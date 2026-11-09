package `in`.wedevit.care

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

private fun answer(vararg lines: JSONObject) = JSONObject().put("lines", JSONArray(lines.toList()))
private fun line(start: Any, speaker: String, lang: String, text: String) = JSONObject().put("startSec", start).put("speaker", speaker).put("lang", lang).put("text", text)

class CareGocGeminiTest {

    @Test fun linesAreCheckedMappedAndSorted() {
        val lines = GocGeminiRules.checkedLines(answer(
            line(12.5, "PATIENT_PARTY", "hi", "  मुझे घर   जाना है "),
            line("00:03", "Doctor", "en-IN", "How are you feeling today?"),
            line(20, "nurse?", "Telugu", "ఇంటికి వెళ్ళాలి"),
            line(25, "UNKNOWN", "kn", "   "),
        ), offsetSec = 600.0, overlapSec = 0.0, durationSec = 600.0)
        assertEquals(listOf(
            GocGeminiLine(603.0, "DOCTOR", "en", "How are you feeling today?"),
            GocGeminiLine(612.5, "PATIENT_PARTY", "hi", "मुझे घर जाना है"),
            GocGeminiLine(620.0, "UNKNOWN", "te", "ఇంటికి వెళ్ళాలి"),
        ), lines)
    }

    @Test fun malformedAnswersAreRejected() {
        val bad = listOf<Any?>(null, "text", JSONObject().put("lines", "x"), answer(JSONObject().put("speaker", "DOCTOR").put("text", "hi")),
            answer(JSONObject().put("startSec", 1)), answer(line(-1, "DOCTOR", "en", "a")), JSONObject().put("lines", JSONArray().put(5)))
        for (value in bad) assertThrows(GocOutputException::class.java) { GocGeminiRules.checkedLines(value, 0.0, 0.0, 300.0) }
        assertThrows(GocOutputException::class.java) { GocGeminiRules.checkedLines(answer(line(400, "DOCTOR", "en", "late")), 0.0, 0.0, 300.0) }
        assertTrue(GocGeminiRules.checkedLines(answer(), 0.0, 0.0, 300.0).isEmpty())
    }

    @Test fun overlapLinesAreDroppedAndALoopIsKeptOnce() {
        val lines = GocGeminiRules.checkedLines(answer(line(0.5, "DOCTOR", "en", "tail of the previous part"), line(2.6, "DOCTOR", "en", "new words"), line(4, "DOCTOR", "en", "new words")), 298.0, 2.0, 300.0)
        assertEquals(listOf(GocGeminiLine(300.6, "DOCTOR", "en", "new words")), lines)
    }

    @Test fun speakerLanguageAndTimeHelpers() {
        assertEquals("PATIENT_PARTY", GocGeminiRules.speaker("patient")); assertEquals("PATIENT_PARTY", GocGeminiRules.speaker("Family")); assertEquals("UNKNOWN", GocGeminiRules.speaker(3))
        assertEquals("ur", GocGeminiRules.lang("ur-IN")); assertEquals("kn", GocGeminiRules.lang("Kannada")); assertEquals("und", GocGeminiRules.lang("??"))
        assertEquals(3675.0, GocGeminiRules.seconds("1:01:15")!!, 0.0); assertEquals(75.5, GocGeminiRules.seconds("75.5s")!!, 0.0); assertNull(GocGeminiRules.seconds("soon"))
        assertEquals("Audio is sent to a secure AI service to transcribe.", GocGeminiRules.consentLine(true)); assertEquals("", GocGeminiRules.consentLine(false))
        assertEquals("provider_busy", GocGeminiRules.errorCode("attempt_unavailable")); assertEquals("sign_in_required", GocGeminiRules.errorCode("confirmed_account_required"))
    }

    @Test fun windowsMergeAndAudioHelpers() {
        assertEquals(listOf(0.0 to 300.0, 300.0 to 600.0, 600.0 to 650.0), GocGeminiRules.planWindows(650.0, GocGeminiRules.windowSec))
        val merged = GocGeminiRules.merge(listOf(listOf(GocGeminiLine(298.0, "DOCTOR", "en", "same")), listOf(GocGeminiLine(299.0, "DOCTOR", "en", "same"), GocGeminiLine(290.0, "PATIENT_PARTY", "en", "earlier"))))
        assertEquals(listOf("earlier", "same"), merged.map { it.text })
        val wav = GocGeminiRules.wav(floatArrayOf(0f, 1f, -1f))
        assertEquals("RIFF", String(wav.copyOfRange(0, 4))); assertEquals(50, wav.size)
        // AAC-LC, 32 kHz (index 5), mono: csd-0 0x12 0x88 (the recorder's format).
        assertEquals(Triple(2, 5, 1), GocGeminiRules.audioSpecificConfig(0x12, 0x88))
        assertNull(GocGeminiRules.audioSpecificConfig(0x2B, 0x88)) // HE-AAC cannot be signalled in ADTS
        assertEquals(listOf(0xFF, 0xF1, 0x54, 0x40, 0x0D, 0x7F, 0xFC), GocGeminiRules.adtsHeader(2, 5, 1, 100).map { it.toInt() and 0xFF })
    }

    // ---------- the pipeline with a faked sahara-care-assist ----------
    private class FakeAudio(override val durationSec: Double) : GocGeminiAudio {
        override suspend fun geminiPiece(start: Double, end: Double) = GocAudioPiece(ByteArray(((end - start) * 100).toInt() + 10), "audio/aac", if (start > 0) 2.0 else 0.0)
    }

    /** Pretends to be the function's goc-transcribe actions and Google's upload URL. `gemini` holds per-window results: "ok", a status, or "long". */
    private class FakeFunction(gemini: List<Any> = emptyList(), val ready: Boolean = true, val offline: Boolean = false) : GocGeminiTransport {
        val queue = ArrayDeque(gemini)
        val calls = mutableListOf<String>(); val starts = mutableListOf<JSONObject>(); var deletes = 0
        override suspend fun info(): Pair<Int, String> { if (offline) throw IOException("offline"); return 200 to JSONObject().put("ready", true).put("gocReady", ready).toString() }
        override suspend fun post(action: String, body: String): Pair<Int, String> {
            val request = JSONObject(body); calls += action
            return when (action) {
                "goc-transcribe-start" -> { starts += request; 200 to JSONObject().put("mode", "direct").put("uploadUrl", "https://generativelanguage.googleapis.com/upload/v1beta/files?upload_id=U")
                    .put("chunkBytes", 262_144).put("ticket", "t.s").put("run", "run.s").put("windowSec", 300).toString() }
                "goc-transcribe-delete" -> { deletes++; 200 to "{}" }
                else -> {
                    assertEquals("files/f1", request.getString("file")); assertTrue(request.getBoolean("consent"))
                    val offset = request.getDouble("offsetSec")
                    when (val step = queue.removeFirstOrNull() ?: "ok") {
                        is Int -> step to JSONObject().put("code", if (step == 503) "provider_busy" else "provider_unavailable").put("error", "busy").put("retryAfterSec", 3).toString()
                        "long" -> 422 to JSONObject().put("code", "output_truncated").put("error", "too long").toString()
                        else -> 200 to JSONObject().put("model", "gemini-3.1-flash-lite").put("engine", "gemini").put("lines", JSONArray()
                            .put(line(offset + 1, "DOCTOR", "en", "Doctor at $offset")).put(line(offset + 5, "PATIENT_PARTY", "hi", "मुझे घर पर रहना है"))).toString()
                    }
                }
            }
        }
        override suspend fun chunk(bytes: ByteArray, headers: Map<String, String>): Pair<Int, String> = fail("relay is not used here")
        override suspend fun upload(url: String, bytes: ByteArray, headers: Map<String, String>): Triple<Int, String, String> {
            val final = headers.getValue("X-Goog-Upload-Command").contains("finalize")
            return Triple(200, if (final) "final" else "active", if (final) JSONObject().put("file", JSONObject().put("name", "files/f1")).toString() else "")
        }
        private fun fail(message: String): Nothing = throw AssertionError(message)
    }
    private val waits = mutableListOf<Long>()
    private fun pipeline(function: FakeFunction) = GocGeminiPipeline(function) { waits += it }

    @Test fun geminiTranscribesEveryWindowWithSpeakersAndReservesOnce() = runBlocking {
        val function = FakeFunction(); val progress = mutableListOf<Float>()
        val result = pipeline(function).run(FakeAudio(650.0), lang = "auto") { progress += it }
        assertEquals("gemini", result.engine); assertEquals("gemini-3.1-flash-lite", result.model)
        assertEquals(6, result.lines.size)
        assertEquals(listOf("DOCTOR", "PATIENT_PARTY"), result.lines.take(2).map { it.speaker })
        assertEquals(3, function.starts.size)
        // The first part carries a request id (one reserved attempt); later parts carry the run ticket instead.
        assertTrue(function.starts[0].has("requestId") && !function.starts[0].has("run"))
        assertTrue(function.starts.drop(1).all { it.getString("run") == "run.s" && !it.has("requestId") })
        assertEquals(0, function.deletes)
        assertEquals(1f, progress.last()); assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test fun aTooLongAnswerSplitsTheWindow() = runBlocking {
        val result = pipeline(FakeFunction(gemini = listOf("long"))).run(FakeAudio(300.0), lang = "hi") {}
        assertEquals(4, result.lines.size)
    }

    @Test fun busyGeminiWaitsOnceThenStopsWithTheFinishedLinesForOnDeviceWhisper() = runBlocking {
        val function = FakeFunction(gemini = listOf("ok", 503, 503))
        val error = runCatching { pipeline(function).run(FakeAudio(650.0), lang = "hi") {} }.exceptionOrNull()
        assertTrue(error is GocGeminiException); error as GocGeminiException
        assertEquals("provider_busy", error.code); assertEquals(2, error.partial.size)
        assertEquals(listOf(3000L), waits)
        assertEquals("the busy upload is deleted by the phone", 1, function.deletes)
    }

    @Test fun aFinalFailureStopsAndTheFunctionDeletesTheFile() = runBlocking {
        val function = FakeFunction(gemini = listOf("ok", 502))
        val error = runCatching { pipeline(function).run(FakeAudio(650.0), lang = "auto") {} }.exceptionOrNull() as GocGeminiException
        assertEquals("unavailable", error.code); assertEquals(2, error.partial.size); assertEquals(0, function.deletes)
    }

    @Test fun noKeyNoSignInOrNoNetworkIsNotAvailable() = runBlocking {
        assertFalse(gocOnlineEngines(FakeFunction(offline = true)).gemini)
        assertFalse(gocOnlineEngines(FakeFunction(ready = false)).gemini)
        assertFalse(gocOnlineEngines(null).gemini)
        val on = gocOnlineEngines(FakeFunction())
        assertTrue(on.gemini); assertEquals("Audio is sent to a secure AI service to transcribe.", on.consentLine)
    }
}
