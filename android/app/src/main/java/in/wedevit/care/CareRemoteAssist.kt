package `in`.wedevit.care

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** What the configured server says it can do. [provider] is the service name the owner set; every consent line names it. */
data class CareAssistCapabilities(val ready: Boolean, val audioReady: Boolean, val translationReady: Boolean, val provider: String = "", val model: String = "", val askReady: Boolean = false)
data class CareAssistEvidence(val field: String, val line: Int, val start: Int, val end: Int, val text: String, val speaker: String)
data class CareAssistedDraft(val sourceHash: String, val fields: Map<String, String>, val evidence: List<CareAssistEvidence>, val provider: String, val model: String)
data class CareAssistedTranscript(val original: String, val sourceHash: String, val language: String, val provider: String, val model: String)
data class CareAssistedTranslation(val original: String, val translation: String, val versionId: String, val sourceHash: String, val sourceLanguage: String, val targetLanguage: String)
class CareAssistException(val code: String, message: String) : IOException(message)

/**
 * Online help goes through the Sahara server function `sahara-care-assist` (android/supabase/functions).
 * That function holds the owner's OpenAI-compatible base URL, key and model; no key is ever in the app.
 * Until the owner sets them, capabilities() reports not ready and the screens keep the word-matching flow.
 */
internal const val CARE_ASSIST_PROVIDER = "openai-compatible"
internal const val CARE_ASSIST_ENDPOINT = "https://sahara.wedevit.in/api/sahara-assist"

internal val careAssistFieldKeys = listOf("priorities", "participants", "topics", "openQuestions", "followUp")
internal val careAssistLanguageCodes = setOf("en-IN", "hi-IN", "kn-IN", "te-IN", "ta-IN", "ml-IN", "mr-IN", "bn-IN", "gu-IN", "pa-IN", "od-IN", "ur-IN")
fun careAssistSourceHash(source: String): String = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** No provider key is shipped in the app. The server checks the actual account and quota. */
fun interface CareAssistTransport {
    suspend fun request(path: String, body: String?, accessToken: String): String
}

class CareAssistHttpTransport(private val base: String = CARE_ASSIST_ENDPOINT) : CareAssistTransport {
    init { require(base == CARE_ASSIST_ENDPOINT) }
    override suspend fun request(path: String, body: String?, accessToken: String): String {
        return suspendCancellableCoroutine { continuation ->
        val active = AtomicReference<HttpURLConnection?>()
        val worker = CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null
            try {
                require(path in setOf("", "/transcribe", "/translate", "/ask"))
                if ((!accessToken.matches(Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) || accessToken.length > 16384)) throw CareAssistException("sign_in_required", "Sign in to Sahara first.")
                connection = URL(base + path).openConnection() as HttpURLConnection
                active.set(connection)
                if (!continuation.isActive) { connection.disconnect(); return@launch }
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.connectTimeout = 15000; connection.readTimeout = 300000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cache-Control", "no-store")
                connection.setRequestProperty("X-Sahara-Client", "android")
                // The Sahara server (sahara.wedevit.in) only answers for a signed-in, confirmed Sahara account.
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { output ->
                        var offset = 0
                        while (offset < bytes.size) { currentCoroutineContext().ensureActive(); val count = minOf(8192, bytes.size - offset); output.write(bytes, offset, count); offset += count }
                    }
                }
                currentCoroutineContext().ensureActive()
                val status = connection.responseCode
                if (!connection.contentType.orEmpty().contains("application/json", ignoreCase = true)) throw CareAssistException("invalid_response", "Something went wrong. Nothing was changed. Try again.")
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive(); val count = input.read(buffer); if (count < 0) break
                        if (output.size() + count > 256 * 1024) throw CareAssistException("invalid_response", "The reply was too long. Nothing was changed.")
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }.orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { JSONObject(text) }.getOrNull()
                    throw CareAssistException(error?.optString("code")?.take(80).orEmpty().ifBlank { "unavailable" }, error?.optString("error")?.take(400).orEmpty().ifBlank { "Online help did not work this time. Nothing was changed." })
                }
                continuation.resumeWith(Result.success(text))
            } catch (error: Throwable) { continuation.resumeWith(Result.failure(error)) }
            finally { connection?.disconnect() }
        }
        continuation.invokeOnCancellation { active.get()?.disconnect(); worker.cancel() }
        }
    }
}

class CareRemoteAssist(private val transport: CareAssistTransport = CareAssistHttpTransport()) {
    private val adapter = CareAssistProviderAdapter(transport)
    suspend fun capabilities(token: String): CareAssistCapabilities = adapter.capabilities(token)
    suspend fun draft(source: String, token: String, consent: Boolean): CareAssistedDraft = adapter.draft(source, token, consent)
    suspend fun transcribe(file: File, consent: RecordingConsent, durationMs: Long, language: String, token: String): CareAssistedTranscript = adapter.transcribe(file, consent, durationMs, language, token)
    suspend fun translate(source: String, versionId: String, sourceLanguage: String, targetLanguage: String, token: String, consent: Boolean): CareAssistedTranslation =
        adapter.translate(source, versionId, sourceLanguage, targetLanguage, token, consent)
    suspend fun ask(request: DocumentQuestionRequest, token: String, consent: Boolean): DocumentAnswer = adapter.ask(request, token, consent)
}

/** The speaker label already written at the start of a conversation line, or "Unknown". */
internal fun careAssistSpeaker(text: String): String =
    Regex("^(?:\\[(Patient|Family / carer|Doctor) · [^\\]\\r\\n]+\\]|(Patient|Family / carer|Doctor):)").find(text)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } } ?: "Unknown"

/** Each request carries consent and a fresh request ID. Only the numbered lines (draft), one short turn (audio) or one note (translation) leave the phone. */
internal class CareAssistProviderAdapter(private val transport: CareAssistTransport) {
    suspend fun capabilities(token: String): CareAssistCapabilities {
        val value = JSONObject(transport.request("", null, token))
        require(value.getString("provider") == CARE_ASSIST_PROVIDER)
        val ready = value.getBoolean("ready")
        return CareAssistCapabilities(ready, ready && value.optBoolean("audioReady"), ready && value.optBoolean("translationReady"),
            value.optString("label").take(60).ifBlank { "the online service" }, value.optString("model").take(100), ready && value.optBoolean("askReady"))
    }

    suspend fun draft(source: String, token: String, consent: Boolean): CareAssistedDraft {
        require(consent) { "Tick the box to agree first." }; require(source.isNotBlank() && source.length <= 12000)
        val lines = JSONArray()
        CareDraftAssist.lines(source).forEach { lines.put(JSONObject().put("line", it.number).put("text", it.text).put("speaker", careAssistSpeaker(it.text))) }
        val raw = transport.request("", request().put("sourceHash", careAssistSourceHash(source)).put("lines", lines).toString(), token)
        return checkedRemote { parseCareAssistedDraft(raw, source) }
    }

    suspend fun transcribe(file: File, consent: RecordingConsent, durationMs: Long, language: String, token: String): CareAssistedTranscript {
        require(file.isFile && file.absolutePath == File(consent.audioPath).absolutePath && file.extension.lowercase() == "m4a") { "Choose this conversation’s saved recording." }
        require(consent.recordingConsentAt > 0 && consent.transcriptionConsentAt >= consent.recordingConsentAt && consent.transcriptionConsentAt > 0) { "Everyone must agree to the recording, and separately to sending it to turn speech into text." }
        require(durationMs in 1..30000 && file.length() in 16..2L * 1024 * 1024) { "Use one person speaking, up to 30 seconds and 2 MB. For longer recordings, type the checked words yourself." }
        require(language == "unknown" || language in careAssistLanguageCodes)
        val payload = request().put("recordingConsent", true).put("processingConsent", true).put("language", language).put("durationMs", durationMs)
            .put("audio", JSONObject().put("data", Base64.getEncoder().encodeToString(file.readBytes())).put("mimeType", "audio/m4a"))
        val raw = transport.request("/transcribe", payload.toString(), token)
        return checkedRemote { parseCareAssistedTranscript(raw) }
    }

    suspend fun translate(source: String, versionId: String, sourceLanguage: String, targetLanguage: String, token: String, consent: Boolean): CareAssistedTranslation {
        require(consent) { "Tick the box to agree first." }
        require(source.isNotBlank() && source.length <= 12000 && versionId.matches(Regex("[A-Za-z0-9._:-]{1,200}")))
        require(sourceLanguage in careAssistLanguageCodes && targetLanguage in careAssistLanguageCodes && sourceLanguage != targetLanguage)
        val payload = request().put("source", source).put("sourceHash", careAssistSourceHash(source)).put("versionId", versionId).put("sourceLanguage", sourceLanguage).put("targetLanguage", targetLanguage)
        val raw = transport.request("/translate", payload.toString(), token)
        return checkedRemote { parseCareAssistedTranslation(raw, source, versionId, sourceLanguage, targetLanguage) }
    }

    /** Only the question and the chosen documents' text lines leave the phone: no images, file names or person details. */
    suspend fun ask(request: DocumentQuestionRequest, token: String, consent: Boolean): DocumentAnswer {
        require(consent) { "Tick the box to agree first." }
        val raw = transport.request("/ask", DocumentQuestionRules.payload(request, request()).toString(), token)
        return try { DocumentQuestionRules.parse(raw, request) } catch (_: RuntimeException) {
            throw CareAssistException("invalid_response", "The answer did not match your documents, so it is not shown.")
        }
    }

    private fun request() = JSONObject().put("requestId", UUID.randomUUID().toString()).put("consent", true)
    private fun <T> checkedRemote(parse: () -> T): T = try { parse() } catch (_: RuntimeException) {
        throw CareAssistException("invalid_response", "The reply did not match this talk. Nothing was changed.")
    }
}

/** The server returns line numbers only. Every word in the draft is copied from this phone's own copy of the conversation. */
fun parseCareAssistedDraft(json: String, source: String): CareAssistedDraft {
    val value = JSONObject(json)
    require(value.get("requiresReview") == true && value.getString("provider") == CARE_ASSIST_PROVIDER)
    val model = value.getString("model"); require(model.length in 1..100)
    val hash = careAssistSourceHash(source); require(value.getString("sourceHash") == hash) { "The conversation changed. Try again." }
    val selections = value.getJSONObject("selections")
    val lines = CareDraftAssist.lines(source).associateBy { it.number }
    val evidence = careAssistFieldKeys.flatMap { field ->
        val raw = selections.optJSONArray(field) ?: JSONArray()
        require(raw.length() <= 12)
        (0 until raw.length()).map { index ->
            val number = raw.get(index); require(number is Int) { "The draft did not match this talk. Nothing was changed." }
            lines[number] ?: throw IllegalArgumentException("The draft used a line that is not in this talk. Nothing was changed.")
        }.distinctBy { it.number }.sortedBy { it.start }
            .map { CareAssistEvidence(field, it.number, it.start, it.end, it.text, careAssistSpeaker(it.text)) }
            // Only the patient's own words can be the patient's priorities.
            .filter { field != "priorities" || it.speaker == "Patient" }
    }
    val fields = careAssistFieldKeys.associateWith { field -> evidence.filter { it.field == field }.joinToString("\n") { it.text } }
    return CareAssistedDraft(hash, fields, evidence, CARE_ASSIST_PROVIDER, model)
}

fun parseCareAssistedTranscript(json: String): CareAssistedTranscript {
    val value = JSONObject(json); val text = value.getString("transcript"); val language = value.getString("language")
    val model = value.getString("model")
    require(value.get("requiresReview") == true && value.getString("provider") == CARE_ASSIST_PROVIDER && model.length in 1..100)
    require(text.isNotBlank() && text.length <= 12000 && value.getString("originalTranscript") == text && value.getString("sourceHash") == careAssistSourceHash(text))
    require(value.getString("speaker") == "Unknown" && value.get("speakerConfirmed") == false) { "A person must check who spoke." }
    require(language == "unknown" || language in careAssistLanguageCodes)
    return CareAssistedTranscript(text, careAssistSourceHash(text), language, CARE_ASSIST_PROVIDER, model)
}

fun parseCareAssistedTranslation(json: String, source: String, versionId: String, from: String, to: String): CareAssistedTranslation {
    val value = JSONObject(json)
    require(value.get("requiresReview") == true && value.getString("provider") == CARE_ASSIST_PROVIDER && value.getString("model").length in 1..100)
    val hash = careAssistSourceHash(source)
    require(value.getString("original") == source && value.getString("sourceHash") == hash && value.getString("versionId") == versionId
        && value.getString("sourceLanguage") == from && value.getString("targetLanguage") == to) { "This translation is for a different version of the note." }
    val segments = value.getJSONArray("segments"); require(segments.length() in 1..12)
    var position = 0
    val translations = (0 until segments.length()).map { index ->
        val segment = segments.getJSONObject(index); val end = segment.getInt("end")
        require(segment.getInt("start") == position && end in (position + 1)..source.length && segment.getString("original") == source.substring(position, end))
        fun numbers(text: String) = Regex("[0-9]+(?:[.,:/-][0-9]+)*").findAll(text).map { it.value }.sorted().toList()
        require(numbers(segment.getString("original")) == numbers(segment.getString("translation"))) { "The translation changed a number, so it is not shown." }
        position = end; segment.getString("translation").also { require(it.length <= 8000) }
    }
    require(position == source.length && value.getString("translation") == translations.joinToString("\n") && value.getString("translation").isNotBlank())
    return CareAssistedTranslation(source, value.getString("translation"), versionId, hash, from, to)
}
