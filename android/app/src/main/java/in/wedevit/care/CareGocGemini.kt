package `in`.wedevit.care

import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Online transcription of a goals-of-care recording with Google Gemini, through the Sahara website route /api/goc-transcribe on Vercel
 * (GOC_ONLINE_ENDPOINT; prototype/app/goc-transcribe-server.ts). The pipeline still speaks the old action names (goc-transcribe-start /
 * -window / -delete / -chunk, as android/supabase/functions/sahara-care-assist/goc-gemini.ts); GocGeminiHttpTransport maps them onto the
 * route's {action: start | transcribe | delete} and ?action=chunk. The Gemini keys stay in Vercel (GEMINI_API_KEY_1 … _5, tried in order).
 * Gemini returns verbatim lines with DOCTOR / PATIENT_PARTY / UNKNOWN speakers. After the last window the transcript is posted once more
 * (action analyze): Gemini maps what was said onto the goals-of-care form, with a word-for-word quote per item, and the route saves the
 * checked result to Supabase (sahara_goc_analyses) as this account. That reading is optional: if it fails, the transcript is returned as before.
 * When Gemini cannot finish, the caller falls back to on-device Whisper (CareGocTranscribe.kt) with the lines already done, then typing.
 *
 * Audio: the recorder writes AAC in an M4A box, which Gemini does not list. Each 5-minute window (Supabase stops a request after 150 s) is
 * remuxed to ADTS AAC (audio/aac) without re-encoding, about 2.4 MB at 64 kbps, and uploaded straight to Google through a one-off upload URL.
 * The rules here mirror prototype/app/goc-online-lines.ts; keep both in step.
 */

/** The Sahara website route that holds the Gemini keys (Vercel). */
internal const val GOC_ONLINE_ENDPOINT = "https://sahara.wedevit.in/api/goc-transcribe"

data class GocGeminiLine(val startSec: Double, val speaker: String, val lang: String, val text: String)
/**
 * Gemini's reading of the finished transcript onto the goals-of-care form. Every hit's quote is word for word in one transcript line;
 * a topic with no hit was not discussed. [GocHit.start] / [GocHit.at] are offsets into the lines' texts joined with "\n".
 * patientKnows / familyAskedNotToTell have no GocDecision yet and stay in [otherFields] as field to option ids.
 * [saved]: the reading was stored in Supabase (sahara_goc_analyses) under [id]. Every item still needs the doctor's confirmation.
 */
data class GocGeminiAnalysis(val extraction: GocExtraction, val notDiscussed: List<String>, val otherFields: Map<String, List<String>>, val model: String, val saved: Boolean, val id: String)
/** engine is "gemini"; model names who transcribed it, for "Transcribed by …". analysis is null when the reading could not be done. */
data class GocGeminiResult(val lines: List<GocGeminiLine>, val model: String, val engine: String, val analysis: GocGeminiAnalysis? = null)
data class GocOnlineEngines(val gemini: Boolean, val consentLine: String)
/** code: not_enabled, sign_in_required, offline, provider_busy, invalid_output, rejected, unavailable, decode, too_long. partial keeps the lines already finished. */
class GocGeminiException(val code: String, message: String, val partial: List<GocGeminiLine> = emptyList()) : IOException(message)
internal class GocOutputException(val code: String, message: String) : IllegalArgumentException(message)

object GocGeminiRules {
    val speakers = listOf("DOCTOR", "PATIENT_PARTY", "UNKNOWN")
    /** Supabase ends a request after 150 s, so one Gemini call covers at most 5 minutes of audio. */
    const val windowSec = 300.0
    const val minWindowSec = 60.0
    const val geminiOverlapSec = 2.0
    const val maxSeconds = 4 * 3600

    fun consentLine(gemini: Boolean): String = if (gemini) "Audio is sent to a secure AI service to transcribe." else ""

    fun speaker(value: Any?): String {
        val key = (value as? String)?.trim()?.uppercase()?.replace(Regex("[\\s/-]+"), "_").orEmpty()
        return when (key) {
            "DOCTOR", "CLINICIAN" -> "DOCTOR"
            "PATIENT_PARTY", "PATIENT", "FAMILY", "CARER", "PATIENT_OR_FAMILY", "PATIENT_FAMILY" -> "PATIENT_PARTY"
            else -> "UNKNOWN"
        }
    }

    private val languageWords = mapOf("english" to "en", "hindi" to "hi", "telugu" to "te", "urdu" to "ur", "kannada" to "kn", "mixed" to "mixed")
    fun lang(value: Any?): String {
        val text = (value as? String)?.trim()?.lowercase().orEmpty()
        languageWords[text]?.let { return it }
        return Regex("^([a-z]{2,3})(?:[-_][a-z0-9]{2,4})?$").find(text)?.groupValues?.get(1) ?: "und"
    }

    /** Seconds from a number, "75.5", "01:15" or "1:01:15". */
    fun seconds(value: Any?): Double? {
        if (value is Number) return value.toDouble().takeIf { it.isFinite() }
        val text = (value as? String)?.trim()?.removeSuffix("s") ?: return null
        text.toDoubleOrNull()?.let { if (Regex("^\\d+(?:\\.\\d+)?$").matches(text)) return it }
        val parts = Regex("^(?:(\\d+):)?(\\d{1,2}):(\\d{1,2}(?:\\.\\d+)?)$").find(text) ?: return null
        return (parts.groupValues[1].ifEmpty { "0" }.toDouble()) * 3600 + parts.groupValues[2].toDouble() * 60 + parts.groupValues[3].toDouble()
    }

    private fun clean(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]"), "").replace(Regex("\\s+"), " ").trim()
    private fun round2(value: Double) = (value * 100).roundToLong() / 100.0

    /**
     * Checks an answer ({"lines":[…]} or a bare array) and maps it onto the recording's clock: offsetSec is where the part starts,
     * lines starting inside a leading overlap are dropped. Throws [GocOutputException] for a malformed answer or a time past the audio.
     */
    fun checkedLines(raw: Any?, offsetSec: Double, overlapSec: Double, durationSec: Double, fallbackLang: String = "und"): List<GocGeminiLine> {
        val list = when (raw) { is JSONObject -> raw.optJSONArray("lines"); is JSONArray -> raw; else -> null }
            ?: throw GocOutputException("invalid_output", "The transcript was not in the expected shape.")
        if (list.length() > 4000) throw GocOutputException("invalid_output", "The transcript had too many lines.")
        val out = ArrayList<GocGeminiLine>()
        for (index in 0 until list.length()) {
            val item = list.opt(index) as? JSONObject ?: throw GocOutputException("invalid_output", "A transcript line could not be read.")
            val start = seconds(if (item.has("startSec")) item.opt("startSec") else item.opt("start"))
            val words = item.opt("text") as? String
            if (start == null || start < 0 || words == null) throw GocOutputException("invalid_output", "A transcript line had no time or no words.")
            if (start > durationSec + 5) throw GocOutputException("invalid_output", "A transcript line was timed after the end of the audio.")
            val text = clean(words)
            if (text.isEmpty()) continue
            if (text.length > 5000) throw GocOutputException("invalid_output", "A transcript line was too long.")
            if (overlapSec > 0 && start < overlapSec - 0.5) continue
            val line = GocGeminiLine(round2(offsetSec + min(start, durationSec)), speaker(item.opt("speaker")), if (item.has("lang")) lang(item.opt("lang")) else fallbackLang, text)
            val previous = out.lastOrNull()
            if (previous != null && previous.text == line.text && previous.speaker == line.speaker) continue
            out += line
        }
        return out.sortedBy { it.startSec }
    }

    /** Joins parts in time order and drops a line repeated across a cut (same words within 3 s). */
    fun merge(parts: List<List<GocGeminiLine>>): List<GocGeminiLine> {
        val out = ArrayList<GocGeminiLine>()
        for (line in parts.flatten().sortedBy { it.startSec }) if (out.none { it.text == line.text && abs(it.startSec - line.startSec) < 3 }) out += line
        return out
    }

    /** [0, duration) in windows of at most size seconds; cutNear may move a cut back to a quiet moment. */
    fun planWindows(durationSec: Double, size: Double, cutNear: (Double) -> Double = { it }): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var start = 0.0
        while (durationSec - start > 0.25) {
            var end = min(durationSec, start + size)
            if (end < durationSec) { val moved = cutNear(end); if (moved > start + size / 2 && moved <= end) end = moved }
            out += round2(start) to round2(end)
            start = end
        }
        return out
    }

    /** AAC AudioSpecificConfig (csd-0) → (objectType, frequencyIndex, channels), or null when ADTS cannot carry it (only AAC Main/LC/SSR/LTP can). */
    fun audioSpecificConfig(b0: Int, b1: Int): Triple<Int, Int, Int>? {
        val objectType = (b0 and 0xFF) shr 3
        val frequency = ((b0 and 0x07) shl 1) or ((b1 and 0xFF) shr 7)
        val channels = ((b1 and 0xFF) shr 3) and 0x0F
        return if (objectType in 1..4 && frequency in 0..12 && channels in 1..7) Triple(objectType, frequency, channels) else null
    }

    /** The 7-byte ADTS header (no CRC) for one AAC frame of `payload` bytes. */
    fun adtsHeader(objectType: Int, frequencyIndex: Int, channels: Int, payload: Int): ByteArray {
        val length = payload + 7
        require(length < 8192)
        return byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(),
            ((((objectType - 1) and 3) shl 6) or ((frequencyIndex and 15) shl 2) or ((channels shr 2) and 1)).toByte(),
            (((channels and 3) shl 6) or ((length shr 11) and 3)).toByte(),
            ((length shr 3) and 0xFF).toByte(),
            (((length and 7) shl 5) or 0x1F).toByte(),
            0xFC.toByte(),
        )
    }

    /** 16-bit PCM mono WAV from samples in [-1, 1]. */
    fun wav(samples: FloatArray, sampleRate: Int = 16_000): ByteArray {
        val buffer = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVE".toByteArray()).put("fmt ".toByteArray())
            .putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(sampleRate).putInt(sampleRate * 2).putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(samples.size * 2)
        for (sample in samples) buffer.putShort((sample.coerceIn(-1f, 1f) * 32767).roundToInt().toShort())
        return buffer.array()
    }

    fun errorCode(code: String): String = when {
        code == "provider_busy" || code == "attempt_unavailable" || code == "quota_unavailable" -> "provider_busy"
        code == "sign_in_required" || code == "confirmed_account_required" -> "sign_in_required"
        code == "invalid_output" -> "invalid_output"
        Regex("rejected|invalid_audio|too_large").containsMatchIn(code) -> "rejected"
        code == "not_enabled" -> "not_enabled"
        else -> "unavailable"
    }
}

/** HTTP the pipeline needs. Every call returns status and body; an unreachable server throws IOException. */
interface GocGeminiTransport {
    /** GET the function: whether a Gemini key is set (gocReady). */
    suspend fun info(): Pair<Int, String>
    /** POST JSON to one action: goc-transcribe-start, goc-transcribe-window, goc-transcribe-delete or goc-analyze. */
    suspend fun post(action: String, body: String): Pair<Int, String>
    /** Relay mode only: POST one raw chunk of under 4 MB to goc-transcribe-chunk. */
    suspend fun chunk(bytes: ByteArray, headers: Map<String, String>): Pair<Int, String>
    /** POST a chunk straight to Google's one-off upload URL. Returns status, x-goog-upload-status and body. */
    suspend fun upload(url: String, bytes: ByteArray, headers: Map<String, String>): Triple<Int, String, String>
}

/** The Sahara website route /api/goc-transcribe (Vercel), with the same headers as CareAssistHttpTransport. The Gemini keys stay on the server. */
class GocGeminiHttpTransport(private val token: suspend () -> String) : GocGeminiTransport {
    private suspend fun call(url: String, method: String, bytes: ByteArray?, headers: Map<String, String>, readMs: Int): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        var attempt = 0
        var result: Triple<Int, String, String>? = null
        while (result == null) {
            attempt++
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method; connection.connectTimeout = 15_000; connection.readTimeout = readMs; connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("Cache-Control", "no-store")
                headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                if (bytes != null) { connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) } }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                result = Triple(status, connection.getHeaderField("x-goog-upload-status").orEmpty(), stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty().take(4 * 1024 * 1024))
            } catch (error: IOException) {
                // A kept-alive socket the server already closed fails before anything is sent (as in CareAssistHttpTransport): one fresh try.
                if (attempt > 1 || !(error is java.net.SocketException || error is javax.net.ssl.SSLException || error is java.io.EOFException)) throw error
            } finally { connection.disconnect() }
        }
        result!!
    }
    private suspend fun app(): Map<String, String> {
        // Signed in: send the account token (results can be saved). Signed out or demo: the route allows the app by name, rate limited per phone.
        val access = try { token() } catch (error: CancellationException) { throw error } catch (_: Exception) { "" }
        val signedIn = access.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) && access.length <= 16384
        return if (signedIn) mapOf("X-Sahara-Client" to "android", "Authorization" to "Bearer $access", "apikey" to CareCloud.PUBLIC_API_KEY)
        else mapOf("X-Sahara-Client" to "android", "apikey" to CareCloud.PUBLIC_API_KEY)
    }
    /** The route answers {gemini: true, …}; the pipeline reads gocReady, as from the old function. */
    override suspend fun info(): Pair<Int, String> = call(GOC_ONLINE_ENDPOINT, "GET", null, app(), 15_000).let { (status, _, body) ->
        status to (runCatching { JSONObject(body).let { it.put("gocReady", status == 200 && it.optBoolean("gemini")) }.toString() }.getOrNull() ?: body)
    }
    override suspend fun post(action: String, body: String): Pair<Int, String> {
        val route = routeActions[action] ?: throw IllegalArgumentException("Unknown action")
        val json = JSONObject(body).put("action", route)
        // One window or the whole reading can take a few minutes: the route's own limit is 300 s.
        val readMs = if (route == "transcribe" || route == "analyze") 300_000 else 60_000
        return call(GOC_ONLINE_ENDPOINT, "POST", json.toString().toByteArray(Charsets.UTF_8), app() + ("Content-Type" to "application/json"), readMs).let { it.first to it.third }
    }
    override suspend fun chunk(bytes: ByteArray, headers: Map<String, String>): Pair<Int, String> =
        call("$GOC_ONLINE_ENDPOINT?action=chunk", "POST", bytes, app() + headers + mapOf("Content-Type" to "application/octet-stream", "X-Sahara-Consent" to "online-transcription"), 90_000).let { it.first to it.third }
    private val routeActions = mapOf("goc-transcribe-start" to "start", "goc-transcribe-window" to "transcribe", "goc-transcribe-delete" to "delete", "goc-analyze" to "analyze")
    override suspend fun upload(url: String, bytes: ByteArray, headers: Map<String, String>): Triple<Int, String, String> {
        require(url.startsWith("https://generativelanguage.googleapis.com/upload/"))
        return call(url, "POST", bytes, headers, 120_000)
    }
}

data class GocAudioPiece(val bytes: ByteArray, val mime: String, val overlapSec: Double)

/** Audio the pipeline cuts into windows. */
interface GocGeminiAudio {
    val durationSec: Double
    /** One window, starting `overlapSec` early so a word cut at the boundary is heard whole. */
    suspend fun geminiPiece(start: Double, end: Double): GocAudioPiece
    fun close() {}
}

/**
 * The recorder's M4A file. Each window is its AAC frames remuxed to ADTS (no re-encoding). If the AAC cannot be carried in ADTS (HE-AAC),
 * the recording is decoded once with OfflineAudioDecoder to a 16 kHz scratch file next to it (deleted on close) and windows are sent as WAV.
 */
internal class GocGeminiM4aAudio(private val file: File) : GocGeminiAudio {
    private val pcm = File(file.parentFile, "${file.nameWithoutExtension}.goc-online.pcm")
    private var decodedSamples = -1L
    override val durationSec: Double = run {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: throw GocGeminiException("decode", "This recording has no audio.")
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0 else throw GocGeminiException("decode", "The recording's length could not be read.")
        } catch (error: GocGeminiException) { throw error } catch (_: Exception) { throw GocGeminiException("decode", "The recording could not be read.") } finally { extractor.release() }
    }

    override suspend fun geminiPiece(start: Double, end: Double): GocAudioPiece = withContext(Dispatchers.IO) {
        val from = max(0.0, start - if (start > 0) GocGeminiRules.geminiOverlapSec else 0.0)
        adts(from, end)?.let { GocAudioPiece(it, "audio/aac", start - from) } ?: wavPiece(start, end)
    }

    private suspend fun adts(start: Double, end: Double): ByteArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_AAC } ?: return null
            val format = extractor.getTrackFormat(track)
            val csd = format.getByteBuffer("csd-0") ?: return null
            if (csd.remaining() < 2) return null
            val (objectType, frequency, channels) = GocGeminiRules.audioSpecificConfig(csd.get(csd.position()).toInt(), csd.get(csd.position() + 1).toInt()) ?: return null
            extractor.selectTrack(track)
            extractor.seekTo((start * 1_000_000).toLong(), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val out = ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val at = extractor.sampleTime / 1_000_000.0
                if (at >= end) break
                if (at >= start - 0.03) {
                    val frame = ByteArray(size); buffer.position(0); buffer.get(frame, 0, size)
                    out.write(GocGeminiRules.adtsHeader(objectType, frequency, channels, size)); out.write(frame)
                }
                extractor.advance()
            }
            return out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (error: CancellationException) { throw error } catch (_: Exception) { return null } finally { extractor.release() }
    }

    private suspend fun decoded(): Long {
        if (decodedSamples < 0) decodedSamples = try { OfflineAudioDecoder.decode(file, pcm, GocGeminiRules.maxSeconds) }
            catch (error: CancellationException) { pcm.delete(); throw error }
            catch (error: Exception) { pcm.delete(); throw GocGeminiException("decode", error.message ?: "The recording could not be decoded.") }
        return decodedSamples
    }
    /** OfflineAudioDecoder writes big-endian floats at 16 kHz. */
    private suspend fun wavPiece(start: Double, end: Double): GocAudioPiece {
        val total = decoded(); val from = min(total, (start * 16_000).toLong()); val to = min(total, (end * 16_000).toLong())
        val samples = RandomAccessFile(pcm, "r").use { input ->
            val bytes = ByteArray(((to - from) * 4).toInt()); input.seek(from * 4); input.readFully(bytes)
            FloatArray((to - from).toInt()).also { ByteBuffer.wrap(bytes).asFloatBuffer().get(it) }
        }
        return GocAudioPiece(GocGeminiRules.wav(samples), "audio/wav", 0.0)
    }
    override fun close() { pcm.delete() }
}

/** restart: the key that holds this upload is out of quota and another key is free, so the window is uploaded again (it lands on that key). */
private class GocEngineFailure(val code: String, message: String, val retryAfterSec: Double? = null, val restart: Boolean = false) : Exception(message)
private fun engineFailure(status: Int, body: String): GocEngineFailure {
    val value = runCatching { JSONObject(body) }.getOrNull()
    return GocEngineFailure(value?.optString("code")?.takeIf { it.isNotEmpty() } ?: if (status == 429 || status == 503) "provider_busy" else "unavailable",
        value?.optString("error")?.takeIf { it.isNotEmpty() } ?: "The online service could not finish.", value?.optDouble("retryAfterSec")?.takeIf { it.isFinite() },
        value?.optBoolean("restart") == true)
}

/** The whole pipeline over any audio and transport; [transcribeGocWithGemini] wires the real ones. Mirrors runGocOnline in goc-gemini-client.ts. */
internal class GocGeminiPipeline(private val transport: GocGeminiTransport, private val pause: suspend (Long) -> Unit = { delay(it) }) {
    private var runTicket: String? = null

    /** Lines from the function are checked again with the same rules, on the whole recording's clock. */
    private fun lines(body: JSONObject, durationSec: Double): List<GocGeminiLine> = try { GocGeminiRules.checkedLines(body, 0.0, 0.0, durationSec) }
        catch (error: GocOutputException) { throw GocEngineFailure("invalid_output", error.message ?: "The transcript could not be read.") }

    private suspend fun window(audio: GocGeminiAudio, start: Double, end: Double, lang: String, progress: (Float) -> Unit): Pair<List<GocGeminiLine>, String> {
        val piece = audio.geminiPiece(start, end)
        // The first part of a recording reserves one attempt with a request id; the run ticket it returns covers the rest.
        val begin = JSONObject().put("consent", true).put("mimeType", piece.mime).put("bytes", piece.bytes.size)
        val current = runTicket
        if (current != null) begin.put("run", current) else begin.put("requestId", UUID.randomUUID().toString())
        val (startStatus, startBody) = transport.post("goc-transcribe-start", begin.toString())
        if (startStatus != 200) throw engineFailure(startStatus, startBody)
        val started = JSONObject(startBody)
        started.optString("run").takeIf { it.isNotEmpty() }?.let { runTicket = it }
        val ticket = started.getString("ticket"); val chunk = max(1, started.optInt("chunkBytes", 8 * 1024 * 1024)); val relay = started.optString("mode") == "relay"
        var file: String? = null; var done = false
        try {
            var offset = 0
            while (offset < piece.bytes.size) {
                currentCoroutineContext().ensureActive()
                val part = piece.bytes.copyOfRange(offset, min(piece.bytes.size, offset + chunk))
                val final = offset + part.size >= piece.bytes.size
                var ok = false
                for (attempt in 0 until 3) {
                    try {
                        if (relay) {
                            val (status, body) = transport.chunk(part, mapOf("X-Goc-Ticket" to ticket, "X-Goc-Offset" to "$offset", "X-Goc-Final" to if (final) "1" else "0"))
                            ok = status == 200
                            if (ok) JSONObject(body).optString("file").takeIf { it.startsWith("files/") }?.let { file = it }
                        } else {
                            val (status, uploadStatus, body) = transport.upload(started.getString("uploadUrl"), part, mapOf("X-Goog-Upload-Command" to if (final) "upload, finalize" else "upload", "X-Goog-Upload-Offset" to "$offset", "Content-Type" to piece.mime))
                            ok = status == 200 && (!final || uploadStatus == "final" || uploadStatus.isEmpty())
                            if (ok && final) runCatching { JSONObject(body).getJSONObject("file").getString("name") }.getOrNull()?.let { file = it }
                        }
                    } catch (error: GocGeminiException) { throw error } catch (_: IOException) { ok = false }
                    if (ok) break
                    pause(1000L shl attempt)
                }
                if (!ok) throw GocEngineFailure("upload_failed", "The audio could not be uploaded to the AI service.")
                offset += part.size
                progress(0.4f * offset / piece.bytes.size)
            }
            var busyWaits = 0
            repeat(90) {
                currentCoroutineContext().ensureActive()
                val request = JSONObject().put("consent", true).put("ticket", ticket).put("file", file ?: JSONObject.NULL).put("lang", lang)
                    .put("offsetSec", start).put("overlapSec", piece.overlapSec).put("durationSec", end - start)
                val (status, body) = transport.post("goc-transcribe-window", request.toString())
                val busy = (status == 503 || status == 429) && engineFailure(status, body).code == "provider_busy"
                when {
                    // Upload this window again at once: the new upload starts on a Gemini key that still has quota. The finally block deletes this one.
                    busy && engineFailure(status, body).restart -> throw engineFailure(status, body)
                    status == 202 -> { JSONObject(body).optString("file").takeIf { it.startsWith("files/") }?.let { file = it }; pause(JSONObject(body).optLong("retryMs", 2000)) }
                    busy && busyWaits++ < 1 -> pause((min(30.0, engineFailure(status, body).retryAfterSec ?: 10.0) * 1000).toLong())
                    status != 200 -> { if (!busy) done = true; throw engineFailure(status, body) }
                    else -> { done = true; progress(1f); val value = JSONObject(body); return lines(value, end + 5) to value.optString("model", "gemini") }
                }
            }
            throw GocEngineFailure("unavailable", "The AI service took too long to prepare the audio.")
        } finally {
            // The function deletes the file after a final answer; otherwise ask it to.
            if (!done) withContext(NonCancellable) { runCatching { transport.post("goc-transcribe-delete", JSONObject().put("ticket", ticket).put("file", file ?: JSONObject.NULL).toString()) } }
        }
    }

    suspend fun run(audio: GocGeminiAudio, lang: String, onProgress: (Float) -> Unit): GocGeminiResult {
        val duration = audio.durationSec
        if (duration > GocGeminiRules.maxSeconds) throw GocGeminiException("too_long", "The recording is longer than 4 hours. Split it into parts.")
        val queue = GocGeminiRules.planWindows(duration, GocGeminiRules.windowSec).toMutableList()
        val parts = ArrayList<List<GocGeminiLine>>(); val models = LinkedHashSet<String>(); var doneSec = 0.0
        onProgress(0f)
        var i = 0; var restarts = 0
        while (i < queue.size) {
            val (start, end) = queue[i]
            val report: (Float) -> Unit = { fraction -> onProgress(min(1.0, (doneSec + fraction * (end - start)) / max(1.0, duration)).toFloat()) }
            try {
                val (lines, model) = window(audio, start, end, lang, report)
                parts += lines; models += model; doneSec += end - start; i++
            } catch (error: GocEngineFailure) {
                // At most one restart per Gemini key (5) in a whole recording, so a run of busy keys still ends.
                if (error.restart && restarts < 5) { restarts++; continue }
                if (error.code == "output_truncated" && end - start > GocGeminiRules.minWindowSec * 2) {
                    val middle = start + (end - start) / 2
                    queue[i] = start to middle; queue.add(i + 1, middle to end); continue
                }
                throw GocGeminiException(GocGeminiRules.errorCode(error.code), error.message ?: "Online transcription could not finish.", GocGeminiRules.merge(parts))
            } catch (error: GocGeminiException) {
                throw GocGeminiException(error.code, error.message ?: "Online transcription could not finish.", GocGeminiRules.merge(parts))
            } catch (_: IOException) {
                throw GocGeminiException("offline", "The AI service could not be reached. Use on-device transcription or type the words.", GocGeminiRules.merge(parts))
            }
        }
        onProgress(1f)
        return GocGeminiResult(GocGeminiRules.merge(parts), models.joinToString(" + ").ifEmpty { "unknown" }, "gemini")
    }
}

/**
 * Where the signed-in account's access token comes from. The app sets it once, e.g. `GocGeminiAuth.token = { cloud.transcriptionToken() }`,
 * because the agreed signatures below take no token. Without it the online path is "not available" and transcribing reports "sign_in_required".
 */
object GocGeminiAuth { @Volatile var token: (suspend () -> String)? = null }

/** Whether the function has a Gemini key, and the consent line to show first. Not available when signed out or offline. */
suspend fun gocOnlineEngines(transport: GocGeminiTransport? = GocGeminiHttpTransport(GocGeminiAuth.token ?: { "" })): GocOnlineEngines = try {
    val (status, body) = transport?.info() ?: (0 to "{}")
    val ready = status == 200 && JSONObject(body).optBoolean("gocReady")
    GocOnlineEngines(ready, GocGeminiRules.consentLine(ready))
} catch (error: CancellationException) { throw error } catch (_: Exception) { GocOnlineEngines(false, "") }

/** True when the function has a Gemini key. False with no key, no sign-in or no network. */
suspend fun geminiGocAvailable(): Boolean = gocOnlineEngines().gemini

/**
 * Transcribes a goals-of-care recording online with Gemini, through the website route /api/goc-transcribe, then reads it onto the form (analysis). Throws [GocGeminiException] (with `partial` lines) when
 * Gemini cannot finish ("offline" with no network); the caller then offers on-device Whisper, then typing. `lang` is en, hi, te, ur, kn, or null for auto.
 * Show [GocOnlineEngines.consentLine] ("Audio is sent to a secure AI service to transcribe.") and get consent before calling this.
 */
suspend fun transcribeGocWithGemini(audio: File, lang: String?, patientId: String? = null, conversationId: String? = null, onProgress: (Float) -> Unit): GocGeminiResult {
    val token = GocGeminiAuth.token ?: { "" }
    return transcribeGocWithGemini(audio, lang, GocGeminiHttpTransport(token), onProgress, patientId, conversationId)
}

/** Gemini reads an on-phone (Whisper) transcript onto the legal form: text only, no audio. Null when offline or Gemini cannot finish; never throws except on cancel. */
suspend fun analyzeGocTextWithGemini(lines: List<GocGeminiLine>, model: String, lang: String?, patientId: String? = null, conversationId: String? = null): GocGeminiAnalysis? {
    if (lines.isEmpty()) return null
    return analyzeGocWithGemini(GocGeminiHttpTransport(GocGeminiAuth.token ?: { "" }), GocGeminiResult(lines, model, "whisper"), lang, patientId, conversationId)
}

internal suspend fun transcribeGocWithGemini(audio: File, lang: String?, transport: GocGeminiTransport, onProgress: (Float) -> Unit,
    patientId: String? = null, conversationId: String? = null): GocGeminiResult {
    val hint = lang?.takeIf { it in setOf("en", "hi", "te", "ur", "kn") } ?: "auto"
    if (!gocOnlineEngines(transport).gemini) throw GocGeminiException("not_enabled", "Online transcription is not set up, or you are offline. Use on-device transcription or type the words.")
    val source = withContext(Dispatchers.IO) { GocGeminiM4aAudio(audio) }
    val result = try { GocGeminiPipeline(transport).run(source, hint, onProgress) } finally { source.close() }
    if (result.lines.isEmpty()) return result
    return result.copy(analysis = analyzeGocWithGemini(transport, result, lang, patientId, conversationId))
}

/** Option ids on the server (prototype/app/goc-extract.ts GOC_FIELD_VALUES) are the same enum names the plan uses here. */
private val gocServerFields = mapOf("goalOfCare" to GocDecision.GOAL, "cpr" to GocDecision.CPR, "hospitalTransfer" to GocDecision.TRANSFER,
    "ambulance" to GocDecision.AMBULANCE, "respCeiling" to GocDecision.BREATHING, "icu" to GocDecision.ICU, "vasopressors" to GocDecision.VASOPRESSORS,
    "dialysis" to GocDecision.DIALYSIS, "ng" to GocDecision.FEEDING_NG, "peg" to GocDecision.FEEDING_PEG, "hydration" to GocDecision.HYDRATION)

/** The route's analyze answer → the app's own GocExtraction. Options not offered for that decision are dropped. Exposed for tests. */
internal fun gocAnalysisFrom(body: JSONObject): GocGeminiAnalysis {
    val hits = ArrayList<GocHit>(); val other = LinkedHashMap<String, MutableList<String>>()
    val suggestions = body.optJSONArray("suggestions") ?: JSONArray()
    for (index in 0 until suggestions.length()) {
        val item = suggestions.optJSONObject(index) ?: continue
        val quote = item.optString("quote").trim(); val at = item.optInt("at", 0)
        val values = item.optJSONArray("values")?.let { list -> (0 until list.length()).map { list.optString(it) }.filter { it.isNotEmpty() } }.orEmpty()
        if (quote.length < 3 || values.isEmpty()) continue
        val field = item.optString("field")
        val decision = gocServerFields[field]
        if (decision == null) { if (field.isNotEmpty()) other.getOrPut(field) { mutableListOf() } += values; continue }
        val allowed = GocPlan.options(decision).map { it.id }.toSet()
        val options = values.filter { it in allowed }
        if (options.isNotEmpty()) hits += GocHit(decision, options, quote, at, at = at)
    }
    fun quotes(name: String, option: String) = (body.optJSONArray(name) ?: JSONArray()).let { list ->
        (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("quote")?.trim()?.takeIf { q -> q.length >= 3 } }.map { GocHit(GocDecision.VALUES, listOf(option), it, 0) }
    }
    hits += quotes("ownWords", GOC_VALUES_OWN_WORDS); hits += quotes("unacceptableOutcomes", GOC_VALUES_NOT_ACCEPTED)
    val notDiscussed = body.optJSONArray("notDiscussed")?.let { list -> (0 until list.length()).map { list.optString(it) } }.orEmpty()
    return GocGeminiAnalysis(GocExtraction(hits = hits), notDiscussed, other, body.optString("model", "gemini"), body.optBoolean("saved"), body.optString("id"))
}

/**
 * One reading of the whole transcript by Gemini (route action analyze), saved to Supabase by the route. Never throws except on cancel:
 * any failure gives null and the transcript is used as before.
 */
private suspend fun analyzeGocWithGemini(transport: GocGeminiTransport, result: GocGeminiResult, lang: String?, patientId: String?, conversationId: String?): GocGeminiAnalysis? = try {
    val lines = JSONArray()
    result.lines.forEach { lines.put(JSONObject().put("startSec", it.startSec).put("speaker", it.speaker).put("lang", it.lang).put("text", it.text)) }
    val request = JSONObject().put("consent", true).put("requestId", UUID.randomUUID().toString()).put("lines", lines)
        .put("transcribedBy", "Online AI service (${result.model})".take(120)).put("language", lang ?: "auto")
    patientId?.takeIf { Regex("[A-Za-z0-9._:-]{1,120}").matches(it) }?.let { request.put("patientId", it) }
    conversationId?.takeIf { Regex("[A-Za-z0-9._:-]{1,120}").matches(it) }?.let { request.put("conversationId", it) }
    val (status, body) = transport.post("goc-analyze", request.toString())
    if (status == 200) gocAnalysisFrom(JSONObject(body)) else null
} catch (error: CancellationException) { throw error } catch (_: Exception) { null }
