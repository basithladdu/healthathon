package `in`.wedevit.care

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

data class ConversationTranscript(
    val text: String,
    val provider: String,
    val model: String,
    val languages: List<String>,
)

enum class ConversationTranscriptionError {
    INVALID_AUDIO,
    CONSENT_REQUIRED,
    AUTH_REQUIRED,
    TOO_LARGE,
    INVALID_RESPONSE,
    CONFIGURATION,
    RATE_LIMITED,
    TIMEOUT,
    UNAVAILABLE,
}

class ConversationTranscriptionException(
    val code: ConversationTranscriptionError,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** Sends consented fictional demo audio directly to the authenticated Supabase function. */
class ConversationTranscriber(
    private val endpoint: URL = URL(BuildConfig.CONVERSATION_TRANSCRIPTION_ENDPOINT),
) {
    suspend fun transcribeAndDraft(file: File, consent: RecordingConsent, bearerToken: String): DoctorAudioAiResult {
        requireConsent(file, consent)
        val response = post(file, consent, normalizedBearerToken(bearerToken), includeDraft = true)
        val transcript = sanitizeConversationTranscript(response)
        val payload = JSONObject(response)
        val rawDraft = payload.optJSONObject("draft")
        val draft = rawDraft?.let { runCatching { parseDoctorAiDraft(it.toString(), transcript.text) }.getOrNull() }
        val error = payload.optString("draftError").takeIf { it.isNotBlank() && it != "null" }?.take(400)
            ?: if (draft == null) "Transcript ready, but the server did not return a grounded AI draft. Review manually." else null
        return DoctorAudioAiResult(transcript, draft, error)
    }
    suspend fun transcribe(file: File, consent: RecordingConsent, bearerToken: String): ConversationTranscript {
        requireConsent(file, consent)
        val token = normalizedBearerToken(bearerToken)
        val response = post(file, consent, token)
        return try {
            sanitizeConversationTranscript(response)
        } catch (error: CancellationException) {
            throw error
        } catch (error: ConversationTranscriptionException) {
            throw error
        } catch (error: RuntimeException) {
            throw ConversationTranscriptionException(
                ConversationTranscriptionError.INVALID_RESPONSE,
                "Sahara received an unreadable transcript. Try again.",
                error,
            )
        }
    }

    private fun requireConsent(file: File, consent: RecordingConsent) {
        if (!file.isFile || file.absolutePath != File(consent.audioPath).absolutePath || file.extension.lowercase(Locale.ROOT) != "m4a") {
            throw ConversationTranscriptionException(ConversationTranscriptionError.INVALID_AUDIO, "Choose a saved Sahara conversation recording.")
        }
        if (consent.transcriptionConsentAt <= 0 || consent.transcriptionConsentAt < consent.recordingConsentAt) {
            throw ConversationTranscriptionException(ConversationTranscriptionError.CONSENT_REQUIRED, "Everyone must separately agree to cloud transcription.")
        }
        if (consent.noticeVersion < 2) {
            throw ConversationTranscriptionException(ConversationTranscriptionError.CONSENT_REQUIRED, "This older recording has no free-demo consent. Make a new fictional demo recording.")
        }
        if (file.length() !in 1..MAX_AUDIO_BYTES) {
            throw ConversationTranscriptionException(ConversationTranscriptionError.TOO_LARGE, "Free-demo transcription supports recordings up to 3 MB and five minutes.")
        }
    }

    private suspend fun post(file: File, consent: RecordingConsent, token: String, includeDraft: Boolean = false): String = suspendCancellableCoroutine { continuation ->
        val activeConnection = AtomicReference<HttpURLConnection?>()
        val worker = CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = postConnected(file, consent, token, includeDraft) { connection ->
                    activeConnection.set(connection)
                    if (!continuation.isActive) runCatching { connection.disconnect() }
                }
                continuation.resumeWith(Result.success(result))
            } catch (error: Throwable) {
                continuation.resumeWith(Result.failure(error))
            }
        }
        continuation.invokeOnCancellation {
            runCatching { activeConnection.get()?.disconnect() }
            worker.cancel()
        }
    }

    private suspend fun postConnected(file: File, consent: RecordingConsent, token: String, includeDraft: Boolean, connected: (HttpURLConnection) -> Unit): String = withContext(Dispatchers.IO) {
        val connection = try {
            endpoint.openConnection() as? HttpURLConnection
                ?: throw ConversationTranscriptionException(ConversationTranscriptionError.UNAVAILABLE, "Sahara transcription could not connect.")
        } catch (error: ConversationTranscriptionException) {
            throw error
        } catch (error: IOException) {
            throw ConversationTranscriptionException(ConversationTranscriptionError.UNAVAILABLE, "Sahara transcription could not connect. Check the connection and try again.", error)
        }
        connected(connection)
        try {
            currentCoroutineContext().ensureActive()
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(file.length())
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("apikey", CareCloud.PUBLIC_API_KEY)
            connection.setRequestProperty("X-Sahara-Demo-Audio", "true")
            if (includeDraft) connection.setRequestProperty("X-Sahara-Include-Draft", "true")
            connection.setRequestProperty("Content-Type", "audio/m4a")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.setRequestProperty("X-Sahara-Recording-Consent-At", consent.recordingConsentAt.toString())
            connection.setRequestProperty("X-Sahara-Transcription-Consent-At", consent.transcriptionConsentAt.toString())
            file.inputStream().buffered().use { input ->
                connection.outputStream.buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readBounded(it, MAX_RESPONSE_BYTES) }.orEmpty()
            currentCoroutineContext().ensureActive()
            if (status !in 200..299) throw httpFailure(status, body)
            if (!connection.contentType.orEmpty().lowercase(Locale.ROOT).contains("application/json")) {
                throw ConversationTranscriptionException(ConversationTranscriptionError.INVALID_RESPONSE, "Sahara received an unreadable transcript. Try again.")
            }
            body
        } catch (error: ConversationTranscriptionException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw ConversationTranscriptionException(ConversationTranscriptionError.TIMEOUT, "Transcription took too long. Try a shorter recording.", error)
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw ConversationTranscriptionException(ConversationTranscriptionError.UNAVAILABLE, "Sahara transcription could not connect. Try again.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpFailure(status: Int, body: String): ConversationTranscriptionException {
        val payload = runCatching { JSONObject(body) }.getOrNull()
        val serverCode = payload?.optString("code")?.take(80).orEmpty()
        val serverMessage = payload?.optString("error")?.take(300).orEmpty()
        val code = when (status) {
            400, 415, 422 -> ConversationTranscriptionError.INVALID_AUDIO
            401, 403 -> ConversationTranscriptionError.AUTH_REQUIRED
            408, 504 -> ConversationTranscriptionError.TIMEOUT
            413 -> ConversationTranscriptionError.TOO_LARGE
            429 -> ConversationTranscriptionError.RATE_LIMITED
            404 -> ConversationTranscriptionError.CONFIGURATION
            503 -> if (serverCode == "not_configured") ConversationTranscriptionError.CONFIGURATION else ConversationTranscriptionError.UNAVAILABLE
            else -> ConversationTranscriptionError.UNAVAILABLE
        }
        val fallback = when (code) {
            ConversationTranscriptionError.INVALID_AUDIO -> "The recording could not be transcribed."
            ConversationTranscriptionError.AUTH_REQUIRED -> "Sign in again before transcribing."
            ConversationTranscriptionError.TOO_LARGE -> "Free-demo transcription supports recordings up to 3 MB and five minutes."
            ConversationTranscriptionError.CONFIGURATION -> "Sahara transcription is still being connected."
            ConversationTranscriptionError.RATE_LIMITED -> "Please wait before transcribing another recording."
            ConversationTranscriptionError.TIMEOUT -> "Transcription took too long. Try a shorter recording."
            else -> "Sahara transcription is temporarily unavailable. Try again."
        }
        return ConversationTranscriptionException(code, serverMessage.ifBlank { fallback })
    }

    private suspend fun readBounded(input: java.io.InputStream, maximum: Int): String {
        val output = ByteArrayOutputStream(minOf(maximum, 32 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var size = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            if (size > maximum) throw ConversationTranscriptionException(ConversationTranscriptionError.INVALID_RESPONSE, "Sahara returned too much transcript data.")
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun normalizedBearerToken(value: String): String {
        val token = value.trim().removePrefix("Bearer ").trim()
        if (token.length !in 16..16_384 || token.any { it == '\r' || it == '\n' }) {
            throw ConversationTranscriptionException(ConversationTranscriptionError.AUTH_REQUIRED, "Sign in again before transcribing.")
        }
        return token
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://kjnhtakqzboayqorrlue.supabase.co/functions/v1/saanthvana-conversation-transcription"
        const val MAX_AUDIO_BYTES = 3L * 1024 * 1024
        const val MAX_TRANSCRIPT_CHARS = 60_000
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 140_000
    }
}

fun sanitizeConversationTranscript(json: String): ConversationTranscript {
    val payload = try { JSONObject(json) } catch (error: RuntimeException) {
        throw ConversationTranscriptionException(ConversationTranscriptionError.INVALID_RESPONSE, "Sahara received an unreadable transcript.", error)
    }
    val text = payload.optString("transcript").trim()
    val provider = payload.optString("provider").trim()
    val model = payload.optString("model").trim()
    if (text.isBlank() || text.length > ConversationTranscriber.MAX_TRANSCRIPT_CHARS || provider !in setOf("google-gemini") || model.length !in 1..100) {
        throw ConversationTranscriptionException(ConversationTranscriptionError.INVALID_RESPONSE, "Sahara received an incomplete transcript. Try again.")
    }
    val languages = payload.optJSONArray("languages")?.let { values ->
        (0 until minOf(values.length(), 20)).mapNotNull { index -> values.optString(index).trim().takeIf { it.matches(Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*")) } }
    }.orEmpty().distinct()
    return ConversationTranscript(text, provider, model, languages)
}
