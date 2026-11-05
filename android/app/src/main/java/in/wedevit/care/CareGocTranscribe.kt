package `in`.wedevit.care

import android.app.ActivityManager
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.AtomicFile
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.util.UUID

/*
 * On-device speech for goals-of-care talks. Qwen3-ASR (the existing pack) lists Hindi and English but not Telugu, Urdu or Kannada, so this flow uses a
 * multilingual Whisper export for sherpa-onnx, the same runtime already in the app. Downloaded on demand with sha256 checks; no weights in the APK or the repo.
 * Audio and words stay on this phone. No HTTP client exists on this path except the fixed model GETs in OfflineSpeechModelStore.
 */

object OfflineWhisperModel {
    const val id = "whisper-small-int8"
    const val revision = "8f3c18b358db4d1f2fc1eae49d75cd20989e4309"
    const val source = "https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small"
    const val license = "OpenAI Whisper code and weights: MIT. sherpa-onnx: Apache-2.0"
    /** An estimate from the file sizes, not measured on a phone. Adjust after the first device run. */
    const val minimumAvailableMemory = 1_288_490_188L // 1.2 GiB
    val encoder = OfflineModelFile("small-encoder.int8.onnx", 112_442_483, "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9")
    val decoder = OfflineModelFile("small-decoder.int8.onnx", 262_226_114, "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee")
    val tokens = OfflineModelFile("small-tokens.txt", 816_730, "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126")
    val files = listOf(encoder, decoder, tokens)
    val downloadBytes: Long get() = files.sumOf { it.bytes }
    fun url(file: OfflineModelFile) = "$source/resolve/$revision/${file.name}"
}

/** Whisper's language codes. AUTO is an empty code, which sherpa-onnx treats as "detect it"; each pick is applied to the whole recording. */
enum class GocSpeechLanguage(val code: String, val label: String) {
    AUTO("", "Auto"), EN("en", "English"), HI("hi", "हिन्दी"), TE("te", "తెలుగు"), UR("ur", "اردو"), KN("kn", "ಕನ್ನಡ"),
}

/** The real limit: 90 minutes for one recording (about 43 MB of audio, about 350 MB of scratch space while reading it). A longer talk is recorded in parts. */
internal const val GOC_MAX_RECORDING_SECONDS = 90 * 60
private const val GOC_MAX_AUDIO_BYTES = 100L * 1024 * 1024
private const val GOC_AUDIO_BITS_PER_SECOND = 64_000
/** Whisper reads up to 30 s at a time and costs nearly the same for a short piece, so longer pieces finish sooner. sherpa-onnx cuts at about 29.5 s. */
private const val GOC_MIN_WINDOW_SAMPLES = 240_000 // 15 s
private const val GOC_MAX_WINDOW_SAMPLES = 448_000 // 28 s

internal fun gocSegmentKey(clipId: String, startMs: Long) = "$clipId:$startMs"

/** Which pieces of one recording are already read, so a stopped job picks up where it left off instead of starting an hour's audio again. */
internal data class GocSpeechProgress(val audioSha256: String, val language: String, val lastEndSample: Long, val segments: List<OfflineTranscriptSegment>) {
    fun encode(): String = JSONObject().put("audioSha256", audioSha256).put("language", language).put("lastEndSample", lastEndSample)
        .put("segments", JSONArray(segments.map { JSONObject().put("startMs", it.startMs).put("endMs", it.endMs).put("text", it.text).put("language", it.detectedLanguage) })).toString()
    companion object {
        fun decode(json: String): GocSpeechProgress? = runCatching {
            val j = JSONObject(json); val rows = j.getJSONArray("segments")
            GocSpeechProgress(j.getString("audioSha256"), j.getString("language"), j.getLong("lastEndSample"), (0 until rows.length()).map { i -> rows.getJSONObject(i).let { OfflineTranscriptSegment(it.getLong("startMs"), it.getLong("endMs"), it.getString("text"), it.optString("language")) } })
        }.getOrNull()
    }
}

/** Reads one recording in pieces, offline. Every piece is written out at once, so a stop or a crash loses at most the piece in hand. */
internal class GocSpeechEngine(context: Context) {
    private val app = context.applicationContext
    val models = OfflineSpeechModelStore(app, OfflineWhisperModel.id, OfflineWhisperModel.files, OfflineWhisperModel::url)

    private fun progressFile(clip: DoctorAudioClip) = File(clip.file.parentFile, "${clip.id}.goc-progress.json")
    fun hasProgress(clip: DoctorAudioClip) = progressFile(clip).isFile

    suspend fun transcribe(clip: DoctorAudioClip, personId: String, language: GocSpeechLanguage, progress: (Long, Long) -> Unit, onSegment: (OfflineTranscriptSegment) -> Unit): List<OfflineTranscriptSegment> = withContext(Dispatchers.IO) {
        lock.withLock {
            val file = clip.file
            OfflineSpeechRules.requireRecording(file, app.filesDir, clip.consent, personId)
            require(models.ready()) { careTr(false, "Download the speech pack first.", "पहले स्पीच पैक डाउनलोड करें।") }
            val memory = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            require(!memory.lowMemory && memory.availMem >= OfflineWhisperModel.minimumAvailableMemory) { careTr(false, "Low memory. Close other apps and retry.", "मेमोरी कम है। दूसरे ऐप बंद करके फिर कोशिश करें।") }
            // 16 kHz floats are 64 000 bytes a second; the recording is 8 000 bytes a second.
            require(app.cacheDir.usableSpace > file.length() * 8 + 64L * 1024 * 1024) { careTr(false, "Low storage. Free some space and retry.", "जगह कम है। जगह ख़ाली करके फिर कोशिश करें।") }
            currentCoroutineContext().ensureActive()
            val hash = OfflineSpeechRules.sha256(file)
            val saved = runCatching { progressFile(clip).takeIf { it.isFile }?.readText() }.getOrNull()?.let { GocSpeechProgress.decode(it) }?.takeIf { it.audioSha256 == hash && it.language == language.code }
            val segments = saved?.segments.orEmpty().toMutableList()
            var lastEnd = saved?.lastEndSample ?: 0L
            segments.forEach(onSegment)
            val pcm = File.createTempFile("sahara-goc-", ".pcm", app.cacheDir)
            try {
                progress(0, 0)
                val frames = OfflineAudioDecoder.decode(file, pcm, GOC_MAX_RECORDING_SECONDS)
                currentCoroutineContext().ensureActive()
                val config = OfflineModelConfig(
                    whisper = OfflineWhisperModelConfig(
                        encoder = File(models.directory, OfflineWhisperModel.encoder.name).absolutePath,
                        decoder = File(models.directory, OfflineWhisperModel.decoder.name).absolutePath,
                        language = language.code, task = "transcribe",
                    ),
                    tokens = File(models.directory, OfflineWhisperModel.tokens.name).absolutePath,
                    modelType = "whisper", numThreads = 2, provider = "cpu", debug = false,
                )
                val recognizer = try { OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = config)) }
                catch (error: UnsatisfiedLinkError) { throw IllegalStateException(careTr(false, "Not available on this phone. Type instead.", "इस फ़ोन पर उपलब्ध नहीं। टाइप करें।"), error) }
                try {
                    var consumed = 0L; var chars = segments.sumOf { it.text.length }
                    val segmenter = OfflineSpeechSegmenter(GOC_MIN_WINDOW_SAMPLES, GOC_MAX_WINDOW_SAMPLES)
                    suspend fun read(window: OfflineSpeechWindow) {
                        if (window.endSample <= lastEnd) return
                        currentCoroutineContext().ensureActive()
                        val start = window.startSample * 1000 / OfflineSpeechModel.sampleRate
                        val end = window.endSample * 1000 / OfflineSpeechModel.sampleRate
                        var segment = OfflineTranscriptSegment(start, end, "", "")
                        if (!OfflineSpeechRules.isSilent(window.samples)) {
                            val stream = recognizer.createStream()
                            try {
                                stream.acceptWaveform(window.samples, OfflineSpeechModel.sampleRate)
                                recognizer.decode(stream)
                                val result = recognizer.getResult(stream)
                                segment = OfflineTranscriptSegment(start, end, result.text.trim(), result.lang)
                            } finally { stream.release() }
                        }
                        chars += segment.text.length
                        require(chars <= OfflineSpeechModel.maxOriginalChars) { careTr(false, "This transcript is too long to keep. Use a shorter recording.", "यह लिखाई रखने के लिए बहुत लंबी है। छोटी रिकॉर्डिंग लें।") }
                        segments += segment; lastEnd = window.endSample
                        saveProgress(clip, GocSpeechProgress(hash, language.code, lastEnd, segments))
                        onSegment(segment)
                    }
                    DataInputStream(BufferedInputStream(pcm.inputStream(), 65_536)).use { input ->
                        while (consumed < frames) {
                            currentCoroutineContext().ensureActive()
                            val length = minOf(OfflineSpeechSegmenter.FRAME_SAMPLES.toLong(), frames - consumed).toInt()
                            val samples = FloatArray(length) { input.readFloat() }
                            consumed += length
                            // Pieces already read are skipped without a progress tick, so the time left is worked out from real reading only.
                            segmenter.accept(samples)?.let { if (it.endSample > lastEnd) { read(it); progress(consumed, frames) } }
                        }
                        segmenter.finish()?.let { read(it) }
                        progress(frames, frames)
                    }
                } finally { recognizer.release() }
                require(OfflineSpeechRules.sha256(file) == hash) { careTr(false, "The recording changed while being read. Open it again.", "पढ़ते समय रिकॉर्डिंग बदल गई। इसे फिर खोलें।") }
                segments.toList()
            } catch (error: OutOfMemoryError) {
                throw IllegalStateException(careTr(false, "Low memory. Close other apps and retry.", "मेमोरी कम है। दूसरे ऐप बंद करके फिर कोशिश करें।"), error)
            } finally { pcm.delete() }
        }
    }

    private fun saveProgress(clip: DoctorAudioClip, value: GocSpeechProgress) {
        val atomic = AtomicFile(progressFile(clip)); val output = atomic.startWrite()
        try { output.write(value.encode().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }

    companion object { private val lock = Mutex() }
}

internal data class GocRecorderState(val recording: Boolean = false, val seconds: Int = 0, val level: Float = 0f, val clips: List<DoctorAudioClip> = emptyList(), val playing: String? = null, val error: Bi? = null, val paused: Boolean = false)

/**
 * The microphone for a long talk. DoctorAudioController stops at 299 s and 3 MB because of the cloud route's limits, and this flow has no cloud route, so it records on its own here.
 * Same format, same consent rule and the same sidecar shape. Files sit in the person's plan folder, so "remove this person's records" removes them too.
 * It records only while the app is on screen: leaving the app stops and saves the part, and the doctor can record the next part.
 */
internal class GocRecorder(context: Context, folder: File, private val personId: String, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val directory = File(folder, "goc-audio")
    val state = MutableStateFlow(GocRecorderState())
    private var recorder: MediaRecorder? = null
    private var active: DoctorAudioClip? = null
    private var meter: Job? = null
    private var player: MediaPlayer? = null
    init { refresh() }

    fun start(participants: String, agreed: Set<String>, purpose: Boolean, storage: Boolean, access: Boolean) {
        if (recorder != null) return
        stopPlayback()
        val id = UUID.randomUUID().toString()
        val file = File(directory, "$id.m4a")
        val consent = try { RecordingConsentRules.create(personId, file.absolutePath, participants, agreed, purpose, storage, access, transcriptionAccepted = false) }
        catch (_: IllegalArgumentException) { state.value = state.value.copy(error = Bi("Everyone named must agree before recording.", "रिकॉर्डिंग से पहले सभी नामित लोगों की सहमति ज़रूरी है।")); return }
        directory.mkdirs()
        @Suppress("DEPRECATION") val next = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(app) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC); next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioChannels(1); next.setAudioEncodingBitRate(GOC_AUDIO_BITS_PER_SECOND); next.setAudioSamplingRate(32000)
            next.setMaxDuration(GOC_MAX_RECORDING_SECONDS * 1000); next.setMaxFileSize(GOC_MAX_AUDIO_BYTES)
            next.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stop() }
            next.setOutputFile(file.absolutePath); next.prepare(); next.start()
            recorder = next; active = DoctorAudioClip(id, file, consent)
            state.value = state.value.copy(recording = true, paused = false, seconds = 0, error = null)
            var last = SystemClock.elapsedRealtime(); var spoken = 0L
            meter = scope.launch { while (isActive) { delay(250)
                val now = SystemClock.elapsedRealtime(); if (!state.value.paused) spoken += now - last; last = now
                val level = if (state.value.paused) 0f else runCatching { DoctorLocalAudioRules.level(next.maxAmplitude) }.getOrDefault(0f)
                state.value = state.value.copy(seconds = (spoken / 1000).toInt(), level = level)
            } }
        } catch (_: Exception) {
            next.release(); file.delete()
            state.value = state.value.copy(recording = false, error = Bi("The microphone could not start. Check permission and try again.", "माइक्रोफ़ोन शुरू नहीं हुआ। अनुमति जाँचकर फिर कोशिश करें।"))
        }
    }

    /** Pause and resume one recording (Android 7 and later), so a break in the talk is not recorded. */
    fun pause() { val current = recorder ?: return; if (android.os.Build.VERSION.SDK_INT >= 24 && runCatching { current.pause() }.isSuccess) state.value = state.value.copy(paused = true) }
    fun resume() { val current = recorder ?: return; if (android.os.Build.VERSION.SDK_INT >= 24 && runCatching { current.resume() }.isSuccess) state.value = state.value.copy(paused = false) }

    /** An audio file chosen by the doctor, copied into the plan folder with the same consent sidecar as a recording. Returns the new clip, or null. */
    fun import(uri: android.net.Uri, participants: String, agreed: Set<String>): DoctorAudioClip? {
        if (recorder != null) return null
        val id = UUID.randomUUID().toString(); val file = File(directory, "$id.m4a")
        return try {
            val consent = RecordingConsentRules.create(personId, file.absolutePath, participants, agreed, true, true, true, transcriptionAccepted = false)
            directory.mkdirs()
            val copied = app.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { out -> input.copyTo(out) } } ?: 0L
            require(copied in 1..GOC_MAX_AUDIO_BYTES)
            val json = JSONObject().put("id", id).put("patientId", personId).put("participants", consent.participants).put("recordingConsentAt", consent.recordingConsentAt).put("transcriptionConsentAt", consent.transcriptionConsentAt).put("noticeVersion", consent.noticeVersion)
            val atomic = AtomicFile(File(directory, "$id.json")); val stream = atomic.startWrite()
            try { stream.write(json.toString().toByteArray()); atomic.finishWrite(stream) } catch (e: Exception) { atomic.failWrite(stream); throw e }
            refresh()
            state.value.clips.firstOrNull { it.id == id }
        } catch (_: Exception) {
            file.delete()
            state.value = state.value.copy(error = Bi("This file could not be used. Choose an audio file under 100 MB.", "यह फ़ाइल इस्तेमाल नहीं हो सकी। 100 MB से छोटी ऑडियो फ़ाइल चुनें।"))
            null
        }
    }

    /** Stops and returns the saved clip, so the screen can read it straight away. */
    fun stopAndGet(): DoctorAudioClip? { val id = active?.id ?: return null; stop(); return state.value.clips.firstOrNull { it.id == id } }

    fun stop() {
        val current = recorder ?: return
        recorder = null; meter?.cancel(); meter = null
        val clip = active; active = null
        try {
            current.stop()
            require(clip != null && clip.file.length() in 1..GOC_MAX_AUDIO_BYTES)
            val c = clip.consent
            val json = JSONObject().put("id", clip.id).put("patientId", personId).put("participants", c.participants).put("recordingConsentAt", c.recordingConsentAt).put("transcriptionConsentAt", c.transcriptionConsentAt).put("noticeVersion", c.noticeVersion)
            val atomic = AtomicFile(File(directory, "${clip.id}.json")); val stream = atomic.startWrite()
            try { stream.write(json.toString().toByteArray()); atomic.finishWrite(stream) } catch (e: Exception) { atomic.failWrite(stream); throw e }
            state.value = state.value.copy(error = null)
        } catch (_: Exception) {
            clip?.file?.delete()
            state.value = state.value.copy(error = Bi("The recording was too short or could not be saved. Try again.", "रिकॉर्डिंग बहुत छोटी थी या सेव नहीं हुई। फिर कोशिश करें।"))
        } finally { current.release(); state.value = state.value.copy(recording = false, paused = false, level = 0f); refresh() }
    }

    fun play(clip: DoctorAudioClip) {
        if (state.value.recording) return
        if (state.value.playing == clip.id) { stopPlayback(); return }
        stopPlayback()
        val next = MediaPlayer()
        try {
            next.setDataSource(clip.file.absolutePath); next.setOnCompletionListener { stopPlayback() }; next.prepare(); next.start()
            player = next; state.value = state.value.copy(playing = clip.id, error = null)
        } catch (_: Exception) { runCatching { next.release() }; state.value = state.value.copy(playing = null, error = Bi("This recording could not be played.", "यह रिकॉर्डिंग नहीं चली।")) }
    }
    fun stopPlayback() { val current = player; player = null; current?.run { runCatching { stop() }; runCatching { release() } }; state.value = state.value.copy(playing = null) }

    /** Audio, consent sidecar and reading progress. Named files only, never a recursive delete. */
    fun delete(clip: DoctorAudioClip) {
        if (state.value.recording || clip.file.canonicalFile.parentFile != directory.canonicalFile) return
        stopPlayback()
        clip.file.delete(); File(directory, "${clip.id}.json").delete(); File(directory, "${clip.id}.goc-progress.json").delete()
        refresh()
    }

    fun close() { stop(); stopPlayback() }

    private fun refresh() {
        val clips = directory.listFiles()?.filter { it.extension == "json" && !it.name.endsWith(".goc-progress.json") }?.mapNotNull { metadata -> runCatching {
            val j = JSONObject(metadata.readText()); val id = j.getString("id")
            require(id.matches(Regex("[A-Za-z0-9-]+")) && j.getString("patientId") == personId)
            val file = File(directory, "$id.m4a"); require(file.isFile)
            DoctorAudioClip(id, file, RecordingConsent(file.absolutePath, personId, j.getString("participants"), j.getLong("recordingConsentAt"), j.getLong("transcriptionConsentAt"), j.getInt("noticeVersion")))
        }.getOrNull() }?.sortedBy { it.consent.recordingConsentAt } ?: emptyList()
        state.value = state.value.copy(clips = clips)
    }
}

/** Autosave of an unfinished review, in the same private folder. Survives a killed app; removed when the doctor finishes. */
internal class GocDraftFile(folder: File, private val recordId: String) {
    private val file = File(File(folder, "goc-audio"), "review-${recordId.filter { it.isLetterOrDigit() }}.json")
    fun read(): GocDraft? = runCatching {
        if (!file.isFile) return null
        val j = JSONObject(file.readText())
        if (j.getString("recordId") != recordId) return null
        GocDraft(GocTranscriptJson.decode(j.getJSONObject("draft")) ?: return null, j.optJSONArray("ticked")?.let { a -> (0 until a.length()).mapNotNull { i -> GocDecision.entries.firstOrNull { it.name == a.optString(i) } } }.orEmpty().toSet())
    }.getOrNull()
    fun write(draft: GocDraft) { runCatching {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file); val output = atomic.startWrite()
        try { output.write(JSONObject().put("recordId", recordId).put("draft", GocTranscriptJson.encode(draft.transcript)).put("ticked", JSONArray(draft.ticked.map { it.name })).toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    } }
    fun delete() { file.delete() }
}
internal data class GocDraft(val transcript: GocTranscript, val ticked: Set<GocDecision>)
