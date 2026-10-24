package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DoctorConversationContextTest {
    private val person="22222222-2222-2222-2222-222222222222"
    @Test fun legacyAnswersNeverImplyDiscussionOrRecorderIdentity() {
        val draft=Conversation(person,source="Patient words",priorities="An existing answer")
        val json=JSONObject(DoctorConversationContext.initial(draft))
        assertEquals("needs_review",json.getJSONArray("goals").getJSONObject(0).getString("status"))
        assertEquals("An existing answer",json.getJSONArray("goals").getJSONObject(0).getString("answer"))
        assertTrue(json.isNull("audioRef"));assertTrue(json.isNull("recorderUid"))
        DoctorConversationContext.validate(draft.source,json.toString())
    }
    @Test fun quotationsUseExactUtf8BytesIncludingNonAsciiSource() {
        val source="नमस्ते 🙂";val d=Conversation(person,source=source)
        val j=JSONObject(DoctorConversationContext.initial(d));val g=j.getJSONArray("goals").getJSONObject(0)
        g.put("status","discussed").put("origin","source").put("answer","🙂")
        val start="नमस्ते ".toByteArray(Charsets.UTF_8).size
        g.put("evidence",org.json.JSONArray().put(JSONObject().put("start",start).put("end",source.toByteArray(Charsets.UTF_8).size).put("quote","🙂")))
        DoctorConversationContext.validate(source,j.toString())
        g.getJSONArray("evidence").getJSONObject(0).put("start",start-1)
        assertThrows(IllegalArgumentException::class.java) { DoctorConversationContext.validate(source,j.toString()) }
        assertThrows(IllegalArgumentException::class.java) { DoctorConversationContext.validate("Changed",DoctorConversationContext.initial(d)) }
    }
    @Test fun v2HashHasCrossLanguageByteFramingAndIncludesExactContext() {
        val v=CareVersion(personId=person,version=2,personName="Sample",source="नमस्ते 🙂",priorities="A",participants="B",topics="C",questions="D",nextSteps="E",doctor="Recorder",published=123,contentHash="",conversationId="11111111-1111-1111-1111-111111111111",contextJson="{}",hashScheme=DoctorConversationContext.HASH_SCHEME)
        assertEquals("f4b8a6d7e0779e221e4c7209780443eaa58fe6361235769b8bac6a79b6dfd50d",DoctorConversationContext.framedHash(v))
        assertNotEquals(DoctorConversationContext.framedHash(v),DoctorConversationContext.framedHash(v.copy(contextJson="{ }")))
    }
    @Test fun legacyPublishDoesNotChangeItsHashSchemeOrRequireContext() {
        val note=NoteRules.publish(Person(person,"Sample",1),Conversation(person,source="Original",priorities="A"),"Doctor",true,1)
        assertEquals("legacy",note.hashScheme);assertEquals("",note.contextJson);assertEquals("",note.conversationId)
    }
    @Test fun sourceEditsKeepAnswersButInvalidateReviewedEvidenceAndArchiveOriginalContext() {
        val old=Conversation(person,source="Original words",priorities="Answer")
        val j=JSONObject(DoctorConversationContext.initial(old))
        j.getJSONArray("goals").getJSONObject(0).put("status","discussed").put("origin","manual")
        val reviewed=old.copy(contextJson=j.toString())
        val (newContext,archive)=DoctorConversationContext.sourceChanged(reviewed,"Changed words")
        assertTrue(archive)
        assertEquals("Original words",JSONObject(DoctorConversationContext.archiveJson(reviewed)).getString("source"))
        val goal=JSONObject(newContext).getJSONArray("goals").getJSONObject(0)
        assertEquals("Answer",goal.getString("answer"));assertEquals("needs_review",goal.getString("status"))
        DoctorConversationContext.validate("Changed words",newContext)
    }
    @Test fun strictContextRejectsUnsupportedShapeTypesAndEmptyDiscussedAnswers() {
        val d=Conversation(person,source="Original")
        val mutations=listOf<(JSONObject)->Unit>(
            { it.put("unknown","not supported") },
            { it.remove("audioRef") },
            { it.put("schemaVersion","1") },
            { it.put("recorderLabel",false) },
            { it.getJSONArray("goals").getJSONObject(0).put("answer",42) },
            { it.put("audioRef","11111111-1111-1111-1111-111111111111") },
            { it.getJSONArray("goals").getJSONObject(0).put("status","discussed") },
            { it.getJSONArray("goals").getJSONObject(0).put("extra",true) },
            { it.put("englishDraft",JSONObject().put("text","Translated").put("sourceHash",DoctorConversationContext.sha256(d.source.toByteArray())).put("origin","manual").put("reviewed","true")) }
        )
        mutations.forEach { change -> val j=JSONObject(DoctorConversationContext.initial(d));change(j);assertThrows(Exception::class.java) { DoctorConversationContext.validate(d.source,j.toString()) } }
    }
    @Test fun transcriptionProvenanceDoesNotInventLegacyOriginalHashAndSurvivesSourceEditing() {
        val d=Conversation(person,source="Original",transcriptProvider="fixture",transcriptModel="fixture-model",transcribedAt=123)
        val j=JSONObject(DoctorConversationContext.initial(d));assertTrue(j.getJSONObject("transcription").isNull("sourceHash"))
        j.getJSONObject("transcription").put("sourceHash",DoctorConversationContext.sha256(d.source.toByteArray()))
        val changed=DoctorConversationContext.sourceChanged(d.copy(contextJson=j.toString()),"Manually edited")
        assertTrue(changed.second)
        val provenance=JSONObject(changed.first).getJSONObject("transcription")
        assertEquals(123,provenance.getLong("at"));assertEquals("fixture-model",provenance.getString("model"))
        assertEquals(j.getJSONObject("transcription").getString("sourceHash"),provenance.getString("sourceHash"))
        DoctorConversationContext.validate("Manually edited",changed.first)
    }
    @Test fun contentDedupIgnoresOnlyImmutableVersionCountersAndPublishTime() {
        val v=CareVersion(personId=person,version=1,personName="Sample",source="Original",priorities="A",participants="",topics="",questions="",nextSteps="",doctor="Doctor",published=10,contentHash="",conversationId="11111111-1111-1111-1111-111111111111",contextJson="{}",hashScheme=DoctorConversationContext.HASH_SCHEME)
        assertTrue(DoctorConversationContext.sameContent(v,v.copy(version=2,published=20)))
        assertFalse(DoctorConversationContext.sameContent(v,v.copy(source="Changed",version=2,published=20)))
    }
    @Test fun expectedIdentityRejectsPersonConversationSourceAccountAndPermissionChanges() {
        val a=DoctorConversationIdentity("account-a",person,"11111111-1111-1111-1111-111111111111",DoctorConversationContext.sha256("Source".toByteArray()))
        assertTrue(doctorConversationIdentityMatches(a,a,true))
        listOf(a.copy(accountId="account-b"),a.copy(personId="other"),a.copy(conversationId="other"),a.copy(sourceHash="changed")).forEach { assertFalse(doctorConversationIdentityMatches(a,it,true)) }
        assertFalse(doctorConversationIdentityMatches(a,a,false))
    }
    @Test fun savedTextIncludesUncertaintyAndReviewStatusesWithoutExposingLocalPaths() {
        val d=Conversation(person,source="Original words",priorities="Travel")
        val j=JSONObject(DoctorConversationContext.initial(d))
        j.getJSONArray("goals").getJSONObject(3).put("status","undecided").put("origin","manual").put("answer","Not sure about travel")
        val note=NoteRules.publish(Person(person,"Sample",1),d.copy(conversationId="11111111-1111-1111-1111-111111111111",contextJson=j.toString()),"Doctor",true,1)
        assertTrue(NoteRules.text(note).contains("Not sure about travel"))
        assertTrue(NoteRules.text(note).contains("undecided"))
        assertFalse(NoteRules.text(note).contains("audioPath"))
    }
    @Test fun readOnlyArchiveRoundTripPreservesOriginalDraftAndRejectsDifferentPerson() {
        val d=Conversation(person,source="Original archive words",priorities="Priority",audioPath="/synthetic/only/local.m4a",updated=123,conversationId="11111111-1111-1111-1111-111111111111")
        val a=DoctorConversationArchive("archive",person,d.conversationId,DoctorConversationContext.archiveJson(d),456)
        assertEquals(d,DoctorConversationContext.decodeArchive(a))
        assertNull(DoctorConversationContext.decodeArchive(a.copy(personId="different")))
        assertNull(DoctorConversationContext.decodeArchive(a.copy(draftJson="unsupported raw archive")))
    }
}
