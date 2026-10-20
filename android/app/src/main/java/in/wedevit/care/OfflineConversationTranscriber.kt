package `in`.wedevit.care

import android.content.Context
import android.app.ActivityManager
import android.os.Process
import android.util.AtomicFile
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File

/** No HTTP client, provider token or account dependency exists on the transcription path. */
class OfflineConversationTranscriber(context: Context) {
    private val app = context.applicationContext
    private val models = OfflineSpeechModelStore(app)

    suspend fun transcribe(file: File, consent: RecordingConsent?, personId: String, language: OfflineSpeechLanguage,
        progress: (Long, Long) -> Unit): OfflineTranscriptReview = withContext(Dispatchers.IO) {
        inferenceLock.withLock {
            OfflineSpeechRules.requireRecording(file, app.filesDir, consent, personId)
            require(models.ready()) { "Download the Hindi / English language pack first." }
            require(Process.is64Bit()) { "This language pack needs a 64-bit phone. Your recording is safe; dictate a turn or type instead." }
            val memory = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
            require(!memory.lowMemory && memory.availMem >= OfflineSpeechModel.minimumAvailableMemory) { "Free at least 2.5 GB of memory before transcribing. Close other apps and retry; your recording is safe." }
            currentCoroutineContext().ensureActive()
            val originalHash = OfflineSpeechRules.sha256(file)
            val pcm = File.createTempFile("sahara-speech-", ".pcm", app.cacheDir)
            try {
                progress(0, 0)
                val frames = OfflineAudioDecoder.decode(file, pcm)
                currentCoroutineContext().ensureActive()
                val modelConfig = OfflineModelConfig(
                    qwen3Asr = OfflineQwen3AsrModelConfig(
                        convFrontend = File(models.directory, "conv_frontend.onnx").absolutePath,
                        encoder = File(models.directory, "encoder.int8.onnx").absolutePath,
                        decoder = File(models.directory, "decoder.int8.onnx").absolutePath,
                        tokenizer = File(models.directory, "tokenizer").absolutePath,
                        maxTotalLen = OfflineSpeechModel.maxTotalTokens,
                        maxNewTokens = OfflineSpeechModel.maxNewTokens,
                    ),
                    modelType = "qwen3_asr", numThreads = 1, provider = "cpu", debug = false,
                )
                val recognizer = try { OfflineRecognizer(config = OfflineRecognizerConfig(modelConfig = modelConfig)) }
                catch (error: UnsatisfiedLinkError) { throw IllegalStateException("Offline transcription is unavailable on this phone. Your recording is safe; use dictation or type.", error) }
                val segments = mutableListOf<OfflineTranscriptSegment>()
                try {
                    var consumed = 0L
                    val segmenter = OfflineSpeechSegmenter()
                    suspend fun decodeWindow(window: OfflineSpeechWindow) {
                        currentCoroutineContext().ensureActive()
                        val start = window.startSample * 1000 / OfflineSpeechModel.sampleRate
                        val end = window.endSample * 1000 / OfflineSpeechModel.sampleRate
                        if (OfflineSpeechRules.isSilent(window.samples)) {
                            segments += OfflineTranscriptSegment(start, end, "", "")
                            return
                        }
                        val stream = recognizer.createStream()
                        try {
                            stream.acceptWaveform(window.samples, OfflineSpeechModel.sampleRate)
                            recognizer.decode(stream)
                            currentCoroutineContext().ensureActive()
                            val result = recognizer.getResult(stream)
                            val text = result.text.trim()
                            require(text.isNotBlank()) { "A speech segment could not be read. Keep the original and try a shorter recording or type the words." }
                            require(result.tokens.size < OfflineSpeechModel.maxNewTokens - 4) { "A speech turn reached the reading limit. Use shorter turns and check the original recording." }
                            require(OfflineSpeechRules.isHindiEnglishText(text)) { "A segment was read in an unexpected script. Keep the recording and dictate a shorter Hindi/English turn, or type the words." }
                            segments += OfflineTranscriptSegment(start, end, text, result.lang)
                        } finally { stream.release() }
                    }
                    DataInputStream(BufferedInputStream(pcm.inputStream(), 65_536)).use { input ->
                        // Pause boundaries retain every sample, with overlap at forced boundaries.
                        while (consumed < frames) {
                            currentCoroutineContext().ensureActive()
                            val length = minOf(OfflineSpeechSegmenter.FRAME_SAMPLES.toLong(), frames - consumed).toInt()
                            val samples = FloatArray(length) { input.readFloat() }
                            consumed += length
                            segmenter.accept(samples)?.let { decodeWindow(it); progress(consumed, frames) }
                        }
                        segmenter.finish()?.let { decodeWindow(it) }
                        progress(frames, frames)
                    }
                } finally { recognizer.release() }
                currentCoroutineContext().ensureActive()
                OfflineSpeechRules.requireRecording(file, app.filesDir, consent, personId)
                require(OfflineSpeechRules.sha256(file) == originalHash) { "The recording changed while processing. Open it again." }
                val original = segments.map { it.text }.filter(String::isNotBlank).joinToString("\n")
                require(original.isNotBlank()) { "No words were recognised. Keep the recording and dictate a shorter Hindi/English turn, or type the words." }
                require(original.length <= OfflineSpeechModel.maxOriginalChars) { "This transcript is too long to retain safely. Use a shorter recording." }
                OfflineTranscriptReview(originalHash, original, original, language.name, segments, System.currentTimeMillis()).also { persist(file, personId, it) }
            } catch (error: OutOfMemoryError) {
                throw IllegalStateException("This phone has too little free memory for the language pack. Close other apps, then retry; your recording is safe.", error)
            } finally { pcm.delete() }
        }
    }

    suspend fun saveReview(file: File, consent: RecordingConsent?, personId: String, review: OfflineTranscriptReview) = withContext(Dispatchers.IO) {
        require(review.reviewedAt >= review.createdAt && review.reviewedAt > 0 && review.provider == OfflineSpeechModel.provider && review.model == OfflineSpeechModel.id && review.text.isNotBlank() && review.text.length <= NoteRules.MAX_SOURCE_CHARS) { "Review the transcript first." }
        OfflineSpeechRules.requireRecording(file, app.filesDir, consent, personId)
        require(OfflineSpeechRules.sha256(file) == review.audioSha256) { "The recording changed. Create the transcript again." }
        persist(file, personId, review)
    }

    suspend fun saved(file: File, consent: RecordingConsent?, personId: String): OfflineTranscriptReview? = withContext(Dispatchers.IO) {
        OfflineSpeechRules.requireRecording(file, app.filesDir, consent, personId)
        val sidecar = File(file.parentFile, "${file.nameWithoutExtension}.offline-transcript.json")
        if (!sidecar.isFile) return@withContext null
        require(sidecar.length() in 1..1_000_000) { "The saved transcript could not be read. Your recording is safe." }
        val data = JSONObject(sidecar.readText())
        require(data.getString("personId") == personId && data.getString("model") == OfflineSpeechModel.id && data.getString("modelRevision") == OfflineSpeechModel.revision && data.getString("provider") == OfflineSpeechModel.provider) { "The saved transcript belongs to another source." }
        require(data.getString("audioSha256") == OfflineSpeechRules.sha256(file)) { "The recording changed since transcription. Create a new transcript." }
        val original = data.getString("originalText")
        require(original.isNotBlank() && original.length <= OfflineSpeechModel.maxOriginalChars)
        val rows = data.getJSONArray("segments")
        require(rows.length() <= 2048)
        val segments = (0 until rows.length()).map { index -> rows.getJSONObject(index).let { OfflineTranscriptSegment(it.getLong("startMs"), it.getLong("endMs"), it.getString("text"), it.optString("language")) } }
        val reviewedAt = data.optLong("reviewedAt")
        val text = if (reviewedAt > 0 && !data.isNull("reviewedText")) data.getString("reviewedText") else original
        require(text.isNotBlank() && text.length <= if (reviewedAt > 0) NoteRules.MAX_SOURCE_CHARS else OfflineSpeechModel.maxOriginalChars)
        OfflineTranscriptReview(data.getString("audioSha256"), original, text, data.getString("requestedLanguage"), segments, data.getLong("createdAt"), reviewedAt)
    }

    private fun persist(file: File, personId: String, result: OfflineTranscriptReview) {
        val rows = JSONArray().apply { result.segments.forEach { put(JSONObject().put("startMs", it.startMs).put("endMs", it.endMs).put("text", it.text).put("language", it.detectedLanguage)) } }
        val payload = JSONObject().put("personId", personId).put("audioSha256", result.audioSha256).put("originalText", result.originalText)
            .put("reviewedText", if (result.reviewedAt > 0) result.text else JSONObject.NULL).put("reviewedAt", result.reviewedAt)
            .put("createdAt", result.createdAt).put("provider", result.provider).put("model", result.model)
            .put("modelRevision", OfflineSpeechModel.revision).put("requestedLanguage", result.language).put("segments", rows)
        val atomic = AtomicFile(File(file.parentFile, "${file.nameWithoutExtension}.offline-transcript.json"))
        val output = atomic.startWrite()
        try { output.write(payload.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Throwable) { atomic.failWrite(output); throw error }
    }
    companion object { private val inferenceLock = Mutex() }
}
