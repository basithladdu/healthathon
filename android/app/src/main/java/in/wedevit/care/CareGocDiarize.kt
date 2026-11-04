package `in`.wedevit.care

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.Keep
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Who spoke when, for goals-of-care recordings. sherpa-onnx's OfflineSpeakerDiarization (already in the app, runtime 1.13.8):
 * pyannote segmentation 3.0 finds speech and speaker changes, a 3D-Speaker ERes2Net embedding tells voices apart, and
 * clustering groups them. Both models are downloaded on demand into noBackupFilesDir with size and SHA-256 checks, never
 * into the APK or the repo. Audio stays on this phone. Speaker numbers are "Speaker 1, 2..." only: no names, no voice
 * identification, and the doctor checks them against the audio like the words.
 *
 * Long audio: sherpa clusters every 10 s window of the whole input at once and keeps an N x N distance matrix, which for
 * 90 minutes is several hundred MB. So the audio is read in ~10 minute parts; each part is diarized on its own and the
 * parts' speakers are joined by comparing one voice print per speaker per part (gocLinkSpeakers).
 */

object GocDiarizeModel {
    const val id = "speaker-diarization-pyannote3-eres2net-base"
    const val segmentationSource = "https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0"
    const val segmentationRevision = "9403a6902bb58e3d5ae8c7e77c3422de279db2e0"
    const val embeddingSource = "https://huggingface.co/csukuangfj/speaker-embedding-models"
    const val embeddingRevision = "0743f301363dec56491a490f6d6cbc9d67f9a3bf"
    const val license = "pyannote segmentation-3.0: MIT (CNRS). 3D-Speaker ERes2Net: Apache-2.0. sherpa-onnx: Apache-2.0"
    const val sampleRate = 16_000
    /** An estimate from the model sizes and one 12 minute part of audio, not measured on a phone. Adjust after the first device run. */
    const val minimumAvailableMemory = 805_306_368L // 768 MiB
    val segmentation = OfflineModelFile("model.onnx", 5_992_913, "220ad67ca923bef2fa91f2390c786097bf305bceb5e261d4af67b38e938e1079")
    val embedding = OfflineModelFile("3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx", 39_593_761, "1a331345f04805badbb495c775a6ddffcdd1a732567d5ec8b3d5749e3c7a5e4b")
    val files = listOf(segmentation, embedding)
    val downloadBytes: Long get() = files.sumOf { it.bytes }
    fun url(file: OfflineModelFile) =
        if (file.name == segmentation.name) "$segmentationSource/resolve/$segmentationRevision/${file.name}"
        else "$embeddingSource/resolve/$embeddingRevision/${file.name}"
}

/** One stretch of one voice. Speakers count from 0 in order of first speaking; times are seconds from the start of the recording. */
data class GocSpeakerTurn(val startSec: Float, val endSec: Float, val speaker: Int)

/** Thrown by [diarizeGocAudio] when the speaker pack has not been downloaded (or a file failed its check). */
class GocDiarizeModelsMissing : IllegalStateException(careTr(false, "Download the speaker pack first.", "पहले वक्ता पैक डाउनलोड करें।"))

/** The doctor may say how many people spoke; otherwise the number is found from the voices. */
const val GOC_MIN_SPEAKERS = 2
const val GOC_MAX_SPEAKERS = 6

/** Clustering threshold on cosine distance (sherpa-onnx's default). Larger means fewer speakers. Not tuned on Sahara recordings. */
internal const val GOC_DIARIZE_THRESHOLD = 0.5f
/** sherpa-onnx defaults: 10 s analysis windows moved by 1 s, speech shorter than 0.2 s dropped, same-voice gaps under 0.5 s joined. */
internal const val GOC_WINDOW_SHIFT_RATIO = 0.1f
internal const val GOC_MIN_DURATION_ON = 0.2f
internal const val GOC_MIN_DURATION_OFF = 0.5f
/** Parts of 10 minutes; a last part under 2 minutes is added to the one before, so a part is at most 12 minutes (46 MB of samples). */
internal const val GOC_DIARIZE_PART_SAMPLES = 600L * GocDiarizeModel.sampleRate
internal const val GOC_DIARIZE_MIN_TAIL_SAMPLES = 120L * GocDiarizeModel.sampleRate

private fun gocDiarizeStore(context: Context) =
    OfflineSpeechModelStore(context.applicationContext, GocDiarizeModel.id, GocDiarizeModel.files, GocDiarizeModel::url)

/** True when both speaker files are on the phone and pass their size and SHA-256 checks. Reads about 45 MB; call off the main thread's hot path. */
suspend fun gocDiarizeModelsReady(context: Context): Boolean = gocDiarizeStore(context).ready()

/** Downloads the missing speaker files (about 45.6 MB in all). [onProgress] gets (bytes done, bytes total). Throws with a plain message on failure. */
suspend fun downloadGocDiarizeModels(context: Context, onProgress: (Long, Long) -> Unit) = gocDiarizeStore(context).download(onProgress)

/** Deletes the speaker files only. */
suspend fun removeGocDiarizeModels(context: Context) = gocDiarizeStore(context).remove()

/** Total download size in bytes, for the "Download (46 MB)" label. */
val GOC_DIARIZE_DOWNLOAD_BYTES: Long get() = GocDiarizeModel.downloadBytes

/** Who spoke when, with the number of speakers found from the voices. See the four-argument form. */
suspend fun diarizeGocAudio(context: Context, wav: File, onProgress: (Float) -> Unit): List<GocSpeakerTurn> =
    diarizeGocAudio(context, wav, null, onProgress)

/**
 * Who spoke when in [wav] (any format Android's MediaExtractor reads: the app's AAC/M4A recordings or WAV), up to
 * [GOC_MAX_RECORDING_SECONDS]. [numSpeakers] is null to find the number, or 2..6 when the doctor gives it.
 * [onProgress] gets 0..1 and is called on a background thread (also from inside the native call), so post it to the UI.
 * Throws [GocDiarizeModelsMissing] when the pack is missing, and IllegalStateException / IllegalArgumentException with
 * a plain message for memory, storage, phone support or an unreadable recording. Cancelling stops between steps.
 */
suspend fun diarizeGocAudio(context: Context, wav: File, numSpeakers: Int?, onProgress: (Float) -> Unit): List<GocSpeakerTurn> = withContext(Dispatchers.IO) {
    require(numSpeakers == null || numSpeakers in GOC_MIN_SPEAKERS..GOC_MAX_SPEAKERS) { "numSpeakers must be null or $GOC_MIN_SPEAKERS..$GOC_MAX_SPEAKERS" }
    gocDiarizeLock.withLock {
        val app = context.applicationContext
        val store = gocDiarizeStore(app)
        if (!store.ready()) throw GocDiarizeModelsMissing()
        require(wav.isFile && wav.length() > 0) { "The recording could not be found." }
        val memory = ActivityManager.MemoryInfo().also { (app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }
        check(!memory.lowMemory && memory.availMem >= GocDiarizeModel.minimumAvailableMemory) { careTr(false, "Too little free memory to tell voices apart. Close other apps and retry. Your recording is safe.", "आवाज़ें अलग करने के लिए मेमोरी कम है। दूसरे ऐप बंद करके फिर कोशिश करें। आपकी रिकॉर्डिंग सुरक्षित है।") }
        // 16 kHz floats are 64 000 bytes a second; the app's recording is about 8 000 bytes a second.
        check(app.cacheDir.usableSpace > wav.length() * 8 + 64L * 1024 * 1024) { careTr(false, "Too little free storage to read this recording. Free some space and retry.", "इस रिकॉर्डिंग को पढ़ने के लिए जगह कम है। जगह ख़ाली करके फिर कोशिश करें।") }
        val report = { value: Float -> runCatching { onProgress(value.coerceIn(0f, 1f)) }; Unit }
        currentCoroutineContext().ensureActive()
        val pcm = File.createTempFile("sahara-diarize-", ".pcm", app.cacheDir)
        try {
            report(0f)
            val frames = OfflineAudioDecoder.decode(wav, pcm, GOC_MAX_RECORDING_SECONDS)
            report(DECODE_SHARE)
            currentCoroutineContext().ensureActive()
            val parts = gocDiarizeParts(frames, GOC_DIARIZE_PART_SAMPLES, GOC_DIARIZE_MIN_TAIL_SAMPLES)
            val single = parts.size == 1
            val segmentationPath = File(store.directory, GocDiarizeModel.segmentation.name).absolutePath
            val embeddingPath = File(store.directory, GocDiarizeModel.embedding.name).absolutePath
            val config = OfflineSpeakerDiarizationConfig(
                segmentation = OfflineSpeakerSegmentationModelConfig(
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(model = segmentationPath, windowShiftRatio = GOC_WINDOW_SHIFT_RATIO),
                    numThreads = 2, debug = false, provider = "cpu",
                ),
                embedding = SpeakerEmbeddingExtractorConfig(model = embeddingPath, numThreads = 2, debug = false, provider = "cpu"),
                // With one part the doctor's number goes straight to sherpa; with several, parts use the threshold and gocLinkSpeakers applies the number.
                clustering = FastClusteringConfig(numClusters = if (single && numSpeakers != null) numSpeakers else -1, threshold = GOC_DIARIZE_THRESHOLD),
                minDurationOn = GOC_MIN_DURATION_ON, minDurationOff = GOC_MIN_DURATION_OFF,
            )
            val unsupported = careTr(false, "Telling voices apart is not available on this phone. The transcript is kept without speakers.", "इस फ़ोन पर आवाज़ें अलग करना उपलब्ध नहीं है। लिखाई बिना वक्ताओं के रखी गई है।")
            val diarizer = try { OfflineSpeakerDiarization(config = config) }
            catch (error: UnsatisfiedLinkError) { throw IllegalStateException(unsupported, error) }
            catch (error: IllegalArgumentException) { throw IllegalStateException(unsupported, error) }
            try {
                check(diarizer.sampleRate() == GocDiarizeModel.sampleRate) { unsupported }
                // A second embedding model only for joining parts; a recording of one part never loads it.
                val extractor = if (single) null else try { SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(model = embeddingPath, numThreads = 2, debug = false, provider = "cpu")) }
                    catch (error: UnsatisfiedLinkError) { throw IllegalStateException(unsupported, error) }
                    catch (error: IllegalArgumentException) { throw IllegalStateException(unsupported, error) }
                try {
                    val raw = mutableListOf<GocPartTurn>()
                    val voices = mutableListOf<GocLocalSpeaker>()
                    var doneSamples = 0L
                    RandomAccessFile(pcm, "r").use { input ->
                        parts.forEachIndexed { index, part ->
                            currentCoroutineContext().ensureActive()
                            val samples = readPcm(input, part.start, (part.end - part.start).toInt())
                            val partStart = DECODE_SHARE + (1f - DECODE_SHARE) * doneSamples / frames
                            val partShare = (1f - DECODE_SHARE) * (part.end - part.start) / frames
                            val diarizeShare = if (extractor == null) partShare else partShare * 0.9f
                            val segments = diarizer.processWithCallback(samples, callback = GocDiarizeProgress { done, total ->
                                if (total > 0) report(partStart + diarizeShare * done / total)
                            })
                            currentCoroutineContext().ensureActive()
                            val offset = part.start.toFloat() / GocDiarizeModel.sampleRate
                            val local = segments.filter { it.end > it.start }.map { GocPartTurn(index, it.speaker, it.start, it.end, offset) }
                            raw += local
                            if (extractor != null) local.map { it.local }.distinct().forEach { speaker ->
                                val own = local.filter { it.local == speaker }.map { it.start to it.end }
                                val others = local.filter { it.local != speaker }.map { it.start to it.end }
                                voices += GocLocalSpeaker(index, speaker, voicePrint(extractor, samples, gocCleanSpans(own, others)))
                            }
                            doneSamples += part.end - part.start
                            report(partStart + partShare)
                        }
                    }
                    val global = if (extractor == null) null else gocLinkSpeakers(voices, GOC_DIARIZE_THRESHOLD, numSpeakers)
                    val turns = raw.map { turn ->
                        val speaker = if (global == null) turn.local else global[voices.indexOfFirst { it.part == turn.part && it.local == turn.local }]
                        GocSpeakerTurn(turn.offset + turn.start, turn.offset + turn.end, speaker)
                    }
                    report(1f)
                    gocMergeTurns(gocNumberByFirstTurn(turns), GOC_MIN_DURATION_OFF)
                } finally { extractor?.release() }
            } finally { diarizer.release() }
        } catch (error: OutOfMemoryError) {
            throw IllegalStateException(careTr(false, "Too little free memory to tell voices apart. Close other apps and retry. Your recording is safe.", "आवाज़ें अलग करने के लिए मेमोरी कम है। दूसरे ऐप बंद करके फिर कोशिश करें। आपकी रिकॉर्डिंग सुरक्षित है।"), error)
        } finally { pcm.delete() }
    }
}

private const val DECODE_SHARE = 0.05f
private val gocDiarizeLock = Mutex()

/**
 * The native progress callback. JNI looks up `invoke(IIJ)Ljava/lang/Integer;` by name, which a Kotlin 2 lambda (compiled
 * with invokedynamic) does not have; a named class does. @Keep stops R8 from removing or renaming that method.
 */
@Keep
private class GocDiarizeProgress(private val onChunk: (Int, Int) -> Unit) : (Int, Int, Long) -> Int {
    override fun invoke(numProcessedChunks: Int, numTotalChunks: Int, arg: Long): Int {
        runCatching { onChunk(numProcessedChunks, numTotalChunks) } // never let an exception reach native code
        return 0
    }
}

/** The decoder writes big-endian floats (DataOutputStream); read one part without a per-sample call. */
private fun readPcm(input: RandomAccessFile, startSample: Long, count: Int): FloatArray {
    val out = FloatArray(count)
    val bytes = ByteArray(1 shl 20)
    input.seek(startSample * 4)
    var filled = 0
    while (filled < count) {
        val take = min(bytes.size / 4, count - filled)
        input.readFully(bytes, 0, take * 4)
        ByteBuffer.wrap(bytes, 0, take * 4).asFloatBuffer().get(out, filled, take)
        filled += take
    }
    return out
}

/** One voice print for one speaker in one part: the length-weighted mean of unit embeddings of their clean spans, or null if none was usable. */
private fun voicePrint(extractor: SpeakerEmbeddingExtractor, samples: FloatArray, spans: List<Pair<Float, Float>>): FloatArray? {
    var sum: FloatArray? = null
    spans.forEach { (start, end) ->
        val from = (start * GocDiarizeModel.sampleRate).toInt().coerceIn(0, samples.size)
        val to = (end * GocDiarizeModel.sampleRate).toInt().coerceIn(from, samples.size)
        if (to - from < GocDiarizeModel.sampleRate / 2) return@forEach
        val stream = extractor.createStream()
        try {
            stream.acceptWaveform(samples.copyOfRange(from, to), GocDiarizeModel.sampleRate)
            stream.inputFinished()
            if (!extractor.isReady(stream)) return@forEach
            val vector = gocUnit(extractor.compute(stream)) ?: return@forEach
            val weight = (to - from).toFloat() / GocDiarizeModel.sampleRate
            val acc = sum ?: FloatArray(vector.size).also { sum = it }
            if (acc.size == vector.size) for (i in vector.indices) acc[i] += vector[i] * weight
        } finally { stream.release() }
    }
    return sum?.let(::gocUnit)
}

// ---- Pure logic below: no Android or native calls, unit-tested in CareGocDiarizeTest. ----

internal data class GocDiarizePart(val start: Long, val end: Long)

/** A speaker turn as one part reported it: times are seconds within the part; [offset] is the part's start in the recording. */
internal data class GocPartTurn(val part: Int, val local: Int, val start: Float, val end: Float, val offset: Float)

/** One speaker of one part, with their voice print (null when none of their speech was long and clean enough). */
internal class GocLocalSpeaker(val part: Int, val local: Int, val embedding: FloatArray?)

/** Splits [total] samples into parts of [partSamples]; a last part shorter than [minTail] joins the part before. */
internal fun gocDiarizeParts(total: Long, partSamples: Long, minTail: Long): List<GocDiarizePart> {
    require(partSamples > 0 && minTail in 0..partSamples)
    if (total <= 0) return emptyList()
    val parts = mutableListOf<GocDiarizePart>()
    var start = 0L
    while (start < total) {
        var end = min(start + partSamples, total)
        if (total - end in 1 until minTail) end = total
        parts += GocDiarizePart(start, end)
        start = end
    }
    return parts
}

/**
 * The parts of a speaker's own turns where nobody else is talking, at least [minSec] long, longest first, each cut to
 * [maxEachSec], until [maxTotalSec]. If none is clean, their single longest turn (if at least [minSec]) is used as is.
 */
internal fun gocCleanSpans(own: List<Pair<Float, Float>>, others: List<Pair<Float, Float>>, minSec: Float = 1f, maxEachSec: Float = 10f, maxTotalSec: Float = 30f): List<Pair<Float, Float>> {
    val clean = mutableListOf<Pair<Float, Float>>()
    own.forEach { (start, end) ->
        var pieces = listOf(start to end)
        others.forEach { (os, oe) ->
            pieces = pieces.flatMap { (s, e) ->
                if (oe <= s || os >= e) listOf(s to e) else listOfNotNull((s to os).takeIf { os > s }, (oe to e).takeIf { oe < e })
            }
        }
        clean += pieces
    }
    val usable = clean.filter { it.second - it.first >= minSec }.sortedByDescending { it.second - it.first }
    val picked = mutableListOf<Pair<Float, Float>>()
    var total = 0f
    for ((s, e) in usable) {
        if (total >= maxTotalSec) break
        val length = minOf(e - s, maxEachSec, maxTotalSec - total)
        if (length < minSec) break
        picked += s to s + length
        total += length
    }
    if (picked.isNotEmpty()) return picked
    val longest = own.maxByOrNull { it.second - it.first } ?: return emptyList()
    return if (longest.second - longest.first >= minSec) listOf(longest.first to min(longest.second, longest.first + maxEachSec)) else emptyList()
}

internal fun gocUnit(vector: FloatArray): FloatArray? {
    var norm = 0.0
    for (v in vector) { if (v.isNaN() || v.isInfinite()) return null; norm += v.toDouble() * v }
    if (norm <= 1e-12) return null
    val scale = (1.0 / sqrt(norm)).toFloat()
    return FloatArray(vector.size) { vector[it] * scale }
}

/** Cosine distance (1 - cosine similarity, at least 0), as sherpa-onnx's clustering uses. A missing print is [UNKNOWN_DISTANCE]. */
internal fun gocCosineDistance(a: FloatArray?, b: FloatArray?): Float {
    if (a == null || b == null || a.size != b.size) return UNKNOWN_DISTANCE
    val ua = gocUnit(a) ?: return UNKNOWN_DISTANCE
    val ub = gocUnit(b) ?: return UNKNOWN_DISTANCE
    var dot = 0f
    for (i in ua.indices) dot += ua[i] * ub[i]
    return max(0f, 1f - dot)
}
private const val UNKNOWN_DISTANCE = 2f

/**
 * Joins the speakers found in each part into speakers of the whole recording. Average-linkage clustering on cosine
 * distance: two groups join while their distance is at most [threshold] and they share no part (one part's speakers
 * were already told apart there). When [target] is given, groups keep joining, nearest first, until at most [target]
 * remain, and then a shared part is allowed. Returns one global id per entry of [speakers], numbered 0.. in input order.
 */
internal fun gocLinkSpeakers(speakers: List<GocLocalSpeaker>, threshold: Float, target: Int?): IntArray {
    val n = speakers.size
    if (n == 0) return IntArray(0)
    val distance = Array(n) { i -> FloatArray(n) { j -> if (i == j) 0f else gocCosineDistance(speakers[i].embedding, speakers[j].embedding) } }
    val groups = MutableList(n) { mutableListOf(it) }
    fun linkage(a: List<Int>, b: List<Int>): Float = a.sumOf { i -> b.sumOf { j -> distance[i][j].toDouble() } }.toFloat() / (a.size * b.size)
    fun conflict(a: List<Int>, b: List<Int>) = a.any { i -> b.any { j -> speakers[i].part == speakers[j].part } }
    while (groups.size > 1) {
        var best: Triple<Int, Int, Float>? = null
        var bestAny: Triple<Int, Int, Float>? = null
        for (x in groups.indices) for (y in x + 1 until groups.size) {
            val d = linkage(groups[x], groups[y])
            if (bestAny == null || d < bestAny.third) bestAny = Triple(x, y, d)
            if (!conflict(groups[x], groups[y]) && (best == null || d < best.third)) best = Triple(x, y, d)
        }
        val tooMany = target != null && groups.size > target
        val pick = when {
            best != null && (best.third <= threshold || tooMany) -> best
            tooMany -> bestAny
            else -> null
        } ?: break
        groups[pick.first].addAll(groups[pick.second])
        groups.removeAt(pick.second)
    }
    val labels = IntArray(n)
    groups.sortedBy { it.min() }.forEachIndexed { id, members -> members.forEach { labels[it] = id } }
    return labels
}

/** Renumbers speakers 0, 1, 2... in the order they first speak, and sorts the turns by time. */
internal fun gocNumberByFirstTurn(turns: List<GocSpeakerTurn>): List<GocSpeakerTurn> {
    val sorted = turns.sortedWith(compareBy<GocSpeakerTurn> { it.startSec }.thenBy { it.endSec })
    val order = linkedMapOf<Int, Int>()
    sorted.forEach { order.getOrPut(it.speaker) { order.size } }
    return sorted.map { it.copy(speaker = order.getValue(it.speaker)) }
}

/** Sorts by start and joins consecutive turns of the same speaker that touch, overlap or are at most [maxGapSec] apart. Empty turns are dropped. */
internal fun gocMergeTurns(turns: List<GocSpeakerTurn>, maxGapSec: Float): List<GocSpeakerTurn> {
    val out = mutableListOf<GocSpeakerTurn>()
    turns.filter { it.endSec > it.startSec }.sortedWith(compareBy<GocSpeakerTurn> { it.startSec }.thenBy { it.endSec }).forEach { turn ->
        val last = out.lastOrNull()
        if (last != null && last.speaker == turn.speaker && turn.startSec - last.endSec <= maxGapSec) out[out.size - 1] = last.copy(endSec = max(last.endSec, turn.endSec))
        else out += turn
    }
    return out
}

/**
 * The speaker who talks most within [startSec]..[endSec], or null if no turn overlaps it. Ties go to the speaker who
 * started first in the span. A zero-length span takes the turn that contains that moment.
 */
internal fun gocSpeakerForSpan(startSec: Float, endSec: Float, turns: List<GocSpeakerTurn>): Int? {
    if (endSec <= startSec) return turns.firstOrNull { startSec >= it.startSec && startSec < it.endSec }?.speaker
    val overlap = linkedMapOf<Int, Float>()
    turns.sortedBy { it.startSec }.forEach { turn ->
        val shared = min(endSec, turn.endSec) - max(startSec, turn.startSec)
        if (shared > 0f) overlap[turn.speaker] = (overlap[turn.speaker] ?: 0f) + shared
    }
    var best: Int? = null
    var bestValue = 0f
    overlap.forEach { (speaker, value) -> if (value > bestValue) { best = speaker; bestValue = value } }
    return best
}

/** The speaker of each transcript segment by overlap ([gocSpeakerForSpan]); null where nobody was heard. */
internal fun gocSpeakersForSegments(segments: List<OfflineTranscriptSegment>, turns: List<GocSpeakerTurn>): List<Int?> =
    segments.map { gocSpeakerForSpan(it.startMs / 1000f, it.endMs / 1000f, turns) }
