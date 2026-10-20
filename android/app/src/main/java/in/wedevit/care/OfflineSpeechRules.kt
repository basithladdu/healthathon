package `in`.wedevit.care

import java.io.File
import java.security.MessageDigest
import kotlin.math.abs

enum class OfflineSpeechLanguage(val label: String) {
    MIXED("हिन्दी + English"),
}

data class OfflineModelFile(val name: String, val bytes: Long, val sha256: String)

object OfflineSpeechModel {
    const val id = "qwen3-asr-0.6b-int8"
    const val provider = "on-device-asr"
    const val revision = "68818b2313fe77bd06f6a7c5068ff3ef59d02b8a"
    const val source = "https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"
    const val runtime = "sherpa-onnx 1.13.8"
    const val license = "Qwen3-ASR and sherpa-onnx: Apache-2.0"
    const val sampleRate = 16_000
    const val maxSeconds = 20 * 60
    const val maxOriginalChars = 120_000
    const val maxTotalTokens = 256
    const val maxNewTokens = 128
    const val minimumAvailableMemory = 2_684_354_560L // 2.5 GiB; avoids starting under native RAM pressure.
    val files = listOf(
        OfflineModelFile("conv_frontend.onnx", 44_148_281, "d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e"),
        OfflineModelFile("encoder.int8.onnx", 182_491_662, "60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9"),
        OfflineModelFile("decoder.int8.onnx", 755_914_231, "4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771"),
        OfflineModelFile("tokenizer/merges.txt", 1_671_853, "8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5"),
        OfflineModelFile("tokenizer/vocab.json", 2_776_833, "ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910"),
        OfflineModelFile("tokenizer/tokenizer_config.json", 12_487, "4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c"),
    )
    val downloadBytes: Long get() = files.sumOf { it.bytes }
    fun url(file: OfflineModelFile) = "$source/resolve/$revision/${file.name}"
}

data class OfflineTranscriptSegment(val startMs: Long, val endMs: Long, val text: String, val detectedLanguage: String)
data class OfflineTranscriptReview(
    val audioSha256: String,
    val originalText: String,
    val text: String,
    val language: String,
    val segments: List<OfflineTranscriptSegment>,
    val createdAt: Long,
    val reviewedAt: Long = 0,
    val provider: String = OfflineSpeechModel.provider,
    val model: String = OfflineSpeechModel.id,
) {
    fun reviewed(words: String, checked: Boolean, now: Long = System.currentTimeMillis()): OfflineTranscriptReview {
        require(checked) { "Check the words against the recording first." }
        require(words.isNotBlank() && words.length <= NoteRules.MAX_SOURCE_CHARS) { "Check the transcript length before using it." }
        require(now >= createdAt) { "Check the phone's date and try again." }
        return copy(text = words.trim(), reviewedAt = now)
    }
}

/** Linear resampling keeps its interpolation position across decoder output buffers. */
class OfflinePcmResampler(private val inputRate: Int, private val emit: (Float) -> Unit) {
    init { require(inputRate in 8_000..192_000) }
    private var previous = 0f
    private var inputIndex = -1L
    private var nextPosition = 0.0
    private val step = inputRate.toDouble() / OfflineSpeechModel.sampleRate
    fun accept(sample: Float) {
        require(sample.isFinite()) { "The recording contains invalid audio samples." }
        inputIndex++
        if (inputIndex == 0L) {
            previous = sample
            emit(sample.coerceIn(-1f, 1f))
            nextPosition += step
            return
        }
        while (nextPosition <= inputIndex) {
            val fraction = (nextPosition - (inputIndex - 1)).coerceIn(0.0, 1.0).toFloat()
            emit((previous + (sample - previous) * fraction).coerceIn(-1f, 1f))
            nextPosition += step
        }
        previous = sample
    }
}

object OfflineSpeechRules {
    fun isHindiEnglishText(text: String): Boolean = text.none { character ->
        Character.isLetter(character) && Character.UnicodeScript.of(character.code) !in setOf(Character.UnicodeScript.LATIN, Character.UnicodeScript.DEVANAGARI)
    }
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65_536)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun matches(file: File, spec: OfflineModelFile) = file.isFile && file.length() == spec.bytes && sha256(file) == spec.sha256
    fun isSilent(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return true
        // Skip near-digital silence; this is not a diagnosis or a voice-activity classifier.
        val rms = kotlin.math.sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        return rms < 0.0003 && samples.maxOf { abs(it) } < 0.002
    }
    fun requireRecording(file: File, privateRoot: File, consent: RecordingConsent?, identity: String) {
        val root = privateRoot.canonicalFile.toPath()
        val actual = file.canonicalFile
        require(actual.toPath().startsWith(root) && actual != privateRoot.canonicalFile && actual.isFile && actual.extension == "m4a") { "Open a saved Sahara recording again." }
        require(consent != null && consent.personId == identity && File(consent.audioPath).canonicalFile == actual && consent.recordingConsentAt > 0) { "Recording consent for this exact person and file is required." }
        require(actual.length() in 1..100L * 1024 * 1024) { "This recording is empty or too large. Choose a shorter recording." }
    }
}
