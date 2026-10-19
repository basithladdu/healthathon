package `in`.wedevit.care

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CareRemoteAssistTest {
    private val source = "Patient: I do not want to decide yet.\r\n\r\nDoctor: We can discuss this again."
    private fun draftJson(): JSONObject {
        val lines = CareDraftAssist.lines(source)
        val fields = JSONObject(); val excerpts = JSONObject(); val evidence = JSONArray()
        careAssistFieldKeys.forEach { field -> fields.put(field, ""); excerpts.put(field, "") }
        listOf("priorities" to lines[0], "topics" to lines[1]).forEach { (field, line) ->
            fields.put(field, line.text); excerpts.put(field, line.text)
            evidence.put(JSONObject().put("field", field).put("line", line.number).put("start", line.start).put("end", line.end).put("text", line.text).put("speaker", if (field == "priorities") "Patient" else "Doctor"))
        }
        return JSONObject().put("provider", "sarvam").put("model", "sarvam-105b").put("sourceHash", careAssistSourceHash(source)).put("requiresReview", true).put("fields", fields).put("excerpts", excerpts).put("evidence", evidence)
    }

    @Test fun exactTurnsRetainNegationAndApplyOnlyToTheirSource() {
        val selected = parseCareAssistedDraft(draftJson().toString(), source)
        assertEquals("Patient: I do not want to decide yet.", selected.fields["priorities"])
        val applied = CareDraftAssist.applyAssisted(Conversation("person", source = source), selected)
        assertEquals(source, applied.source); assertEquals(selected.fields["priorities"], applied.priorities)
        assertThrows(IllegalArgumentException::class.java) { CareDraftAssist.applyAssisted(applied.copy(source = source + " Changed."), selected) }
    }

    @Test fun changedHashQuotesAndOffsetsFailClosed() {
        val wrongHash = draftJson().put("sourceHash", "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(wrongHash.toString(), source) }
        val edited = draftJson(); edited.getJSONObject("fields").put("priorities", "Patient: I want to decide now.")
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(edited.toString(), source) }
        val offset = draftJson(); offset.getJSONArray("evidence").getJSONObject(0).put("start", 1)
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(offset.toString(), source) }
    }

    @Test fun doctorOrUnknownSpeakerCannotBecomePatientPriorities() {
        val forged = draftJson(); forged.getJSONArray("evidence").getJSONObject(0).put("speaker", "Doctor")
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(forged.toString(), source) }
        val duplicate = draftJson(); duplicate.getJSONArray("evidence").put(duplicate.getJSONArray("evidence").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(duplicate.toString(), source) }
    }

    @Test fun transcriptIsUnattributedUntilAHumanChecksIt() {
        val text = "मैं अभी निर्णय नहीं लेना चाहता।"
        val raw = JSONObject().put("provider", "sarvam").put("model", "saaras:v3").put("requiresReview", true).put("transcript", text).put("originalTranscript", text)
            .put("sourceHash", careAssistSourceHash(text)).put("speaker", "Unknown").put("speakerConfirmed", false).put("language", "hi-IN")
        assertEquals(text, parseCareAssistedTranscript(raw.toString()).original)
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedTranscript(raw.put("speaker", "Patient").toString()) }
    }

    @Test fun correctionPreservesOriginalAndExplicitReview() {
        val words = CareDraftAssist.appendReviewedTranscript("Earlier turn.", "I want to decide.", "I do not want to decide.", "Patient", "en-IN", true)
        assertTrue(words.startsWith("Earlier turn.\n[Unknown · en-IN · machine original] I want to decide."))
        assertTrue(words.endsWith("[Patient · en-IN · human-reviewed] I do not want to decide."))
        assertThrows(IllegalArgumentException::class.java) { CareDraftAssist.appendReviewedTranscript("", "Words", "Words", "Patient", "en-IN", false) }
    }

    @Test fun translationIsBoundToOriginalVersionAndCompleteCoverage() {
        val original = "Review in 2 days."; val translated = "2 दिनों में जाँच।"
        val raw = JSONObject().put("provider", "sarvam").put("model", "sarvam-translate:v1").put("requiresReview", true).put("original", original).put("translation", translated)
            .put("versionId", "version-2").put("sourceHash", careAssistSourceHash(original)).put("sourceLanguage", "en-IN").put("targetLanguage", "hi-IN")
            .put("segments", JSONArray().put(JSONObject().put("start", 0).put("end", original.length).put("original", original).put("translation", translated)))
        assertEquals(original, parseCareAssistedTranslation(raw.toString(), original, "version-2", "en-IN", "hi-IN").original)
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedTranslation(raw.toString(), original, "version-3", "en-IN", "hi-IN") }
        val changed = JSONObject(raw.toString()).put("translation", "10 दिनों में जाँच।")
        changed.getJSONArray("segments").getJSONObject(0).put("translation", "10 दिनों में जाँच।")
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedTranslation(changed.toString(), original, "version-2", "en-IN", "hi-IN") }
        raw.getJSONArray("segments").getJSONObject(0).put("start", 1)
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedTranslation(raw.toString(), original, "version-2", "en-IN", "hi-IN") }
    }

    @Test fun realClientUsesOneExplicitConsentedAttemptAndNoFixtureFallback() = runBlocking {
        val calls = mutableListOf<JSONObject>()
        val client = CareRemoteAssist(CareAssistTransport { path, body, token ->
            assertEquals("", path); assertEquals("actual-account-token", token)
            calls += JSONObject(body!!); draftJson().toString()
        })
        val result = client.draft(source, "actual-account-token", true)
        assertEquals("sarvam", result.provider); assertEquals(1, calls.size); assertTrue(calls[0].getBoolean("consent")); assertEquals(source, calls[0].getString("source"))
        assertTrue(calls[0].getString("requestId").matches(Regex("[0-9a-f-]{36}")))
        try { client.draft(source, "actual-account-token", false); fail("Expected consent check") } catch (_: IllegalArgumentException) { }
        assertEquals(1, calls.size)
    }

    @Test fun failedProviderKeepsOriginalAndDoesNotReturnAnInventedDraft() = runBlocking {
        var calls = 0
        val client = CareRemoteAssist(CareAssistTransport { _, _, _ -> calls++; throw CareAssistException("unavailable", "Unavailable") })
        try { client.draft(source, "account", true); fail("Expected failure") } catch (error: CareAssistException) { assertEquals("unavailable", error.code) }
        assertEquals(1, calls); assertTrue(source.contains("do not want"))
    }
}
