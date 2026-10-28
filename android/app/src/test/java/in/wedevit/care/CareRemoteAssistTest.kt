package `in`.wedevit.care

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CareRemoteAssistTest {
    private val source = "Patient: I do not want to decide yet.\r\n\r\nDoctor: We can discuss this again."
    private fun draftJson(priorities: List<Int> = listOf(1), topics: List<Int> = listOf(3)): JSONObject {
        val selections = JSONObject()
        careAssistFieldKeys.forEach { selections.put(it, JSONArray()) }
        selections.put("priorities", JSONArray(priorities)); selections.put("topics", JSONArray(topics))
        return JSONObject().put("provider", CARE_ASSIST_PROVIDER).put("model", "qwen2.5-7b-instruct").put("sourceHash", careAssistSourceHash(source)).put("requiresReview", true).put("selections", selections)
    }

    @Test fun exactTurnsRetainNegationAndApplyOnlyToTheirSource() {
        val selected = parseCareAssistedDraft(draftJson().toString(), source)
        assertEquals("Patient: I do not want to decide yet.", selected.fields["priorities"])
        assertEquals("Doctor: We can discuss this again.", selected.fields["topics"])
        assertEquals("qwen2.5-7b-instruct", selected.model)
        val applied = CareDraftAssist.applyAssisted(Conversation("person", source = source), selected)
        assertEquals(source, applied.source); assertEquals(selected.fields["priorities"], applied.priorities)
        assertThrows(IllegalArgumentException::class.java) { CareDraftAssist.applyAssisted(applied.copy(source = source + " Changed."), selected) }
    }

    @Test fun changedHashAndUnknownLinesFailClosed() {
        val wrongHash = draftJson().put("sourceHash", "0".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(wrongHash.toString(), source) }
        // Line 2 is the blank line and line 9 does not exist: the model cannot point at words that are not there.
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(draftJson(topics = listOf(2)).toString(), source) }
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(draftJson(topics = listOf(9)).toString(), source) }
        val text = draftJson(); text.getJSONObject("selections").put("topics", JSONArray().put("Patient: I want to decide now."))
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(text.toString(), source) }
        assertThrows(IllegalArgumentException::class.java) { parseCareAssistedDraft(draftJson().put("provider", "other").toString(), source) }
    }

    @Test fun doctorLinesNeverBecomePatientPrioritiesAndDuplicatesCollapse() {
        val selected = parseCareAssistedDraft(draftJson(priorities = listOf(3, 1, 1)).toString(), source)
        assertEquals("Patient: I do not want to decide yet.", selected.fields["priorities"])
        assertEquals(1, selected.evidence.count { it.field == "priorities" })
        assertEquals("Patient", careAssistSpeaker("[Patient · hi-IN · human-reviewed] Words")); assertEquals("Unknown", careAssistSpeaker("Words"))
    }

    @Test fun transcriptIsUnattributedUntilAHumanChecksIt() {
        val text = "मैं अभी निर्णय नहीं लेना चाहता।"
        val raw = JSONObject().put("provider", CARE_ASSIST_PROVIDER).put("model", "whisper-large-v3").put("requiresReview", true).put("transcript", text).put("originalTranscript", text)
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
        val raw = JSONObject().put("provider", CARE_ASSIST_PROVIDER).put("model", "qwen2.5-7b-instruct").put("requiresReview", true).put("original", original).put("translation", translated)
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

    @Test fun realClientSendsOnlyNumberedLinesAfterConsentInOneAttempt() = runBlocking {
        val calls = mutableListOf<JSONObject>()
        val client = CareAssistProviderAdapter(CareAssistTransport { path, body, token ->
            assertEquals("", path); assertEquals("actual-account-token", token)
            calls += JSONObject(body!!); draftJson().toString()
        })
        val result = client.draft(source, "actual-account-token", true)
        assertEquals(CARE_ASSIST_PROVIDER, result.provider); assertEquals(1, calls.size); assertTrue(calls[0].getBoolean("consent"))
        val lines = calls[0].getJSONArray("lines")
        assertEquals(2, lines.length()); assertEquals(3, lines.getJSONObject(1).getInt("line")); assertEquals("Doctor", lines.getJSONObject(1).getString("speaker"))
        assertEquals(careAssistSourceHash(source), calls[0].getString("sourceHash")); assertFalse(calls[0].has("source"))
        assertTrue(calls[0].getString("requestId").matches(Regex("[0-9a-f-]{36}")))
        try { client.draft(source, "actual-account-token", false); fail("Expected consent check") } catch (_: IllegalArgumentException) { }
        assertEquals(1, calls.size)
    }

    @Test fun failedProviderKeepsOriginalAndDoesNotReturnAnInventedDraft() = runBlocking {
        var calls = 0
        val client = CareAssistProviderAdapter(CareAssistTransport { _, _, _ -> calls++; throw CareAssistException("unavailable", "Unavailable") })
        try { client.draft(source, "account", true); fail("Expected failure") } catch (error: CareAssistException) { assertEquals("unavailable", error.code) }
        assertEquals(1, calls); assertTrue(source.contains("do not want"))
    }

    @Test fun capabilitiesFollowTheServerConfigurationAndNameTheService() = runBlocking {
        val off = CareRemoteAssist(CareAssistTransport { path, body, token -> assertEquals("", path); assertNull(body); assertEquals("account", token)
            JSONObject().put("provider", CARE_ASSIST_PROVIDER).put("ready", false).put("audioReady", true).put("translationReady", true).put("label", "").put("model", "").toString() })
        val notReady = off.capabilities("account")
        assertFalse(notReady.ready || notReady.audioReady || notReady.translationReady)
        val on = CareRemoteAssist(CareAssistTransport { _, _, _ ->
            JSONObject().put("provider", CARE_ASSIST_PROVIDER).put("ready", true).put("audioReady", false).put("translationReady", true).put("label", "Groq").put("model", "llama-3.3-70b").toString() })
        assertEquals(CareAssistCapabilities(true, false, true, "Groq", "llama-3.3-70b"), on.capabilities("account"))
        val other = CareRemoteAssist(CareAssistTransport { _, _, _ -> JSONObject().put("provider", "sarvam").put("ready", true).toString() })
        try { other.capabilities("account"); fail("Expected provider check") } catch (_: IllegalArgumentException) { }
    }

    @Test fun httpTransportRefusesWithoutASignedInToken() = runBlocking {
        try { CareAssistHttpTransport().request("", null, ""); fail("Expected sign-in check") }
        catch (error: CareAssistException) { assertEquals("sign_in_required", error.code) }
    }
}
