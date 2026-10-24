package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CarriedForwardDraftTest {
    private val cid = "11111111-1111-4111-8111-111111111111"
    private val prev = CareVersion(personId = "p1", version = 3, personName = "P", source = "old words", priorities = "At home", participants = "Daughter", topics = "Pain plan", questions = "Hospital?", nextSteps = "Call Monday", doctor = "Dr", contentHash = "h")
    @Test fun sectionsAreCopied() {
        val d = CarriedForward.draftFrom("p1", cid, prev)
        assertEquals(NoteRules.values(prev), NoteRules.values(d))
        assertEquals("", d.source)
    }
    @Test fun originMarksCarriedForwardAndNeedsReview() {
        val goals = DoctorConversationContext.goals(CarriedForward.draftFrom("p1", cid, prev))
        assertTrue(goals.all { CarriedForward.version(it.origin) == 3 && it.status == "needs_review" && it.origin != "source" })
        assertTrue(goals.all(CarriedForward::unconfirmed))
    }
    @Test fun evidenceIsNeverCarried() {
        val ctx = org.json.JSONObject(DoctorConversationContext.initial(Conversation("p1", source = "old words", priorities = "At home")))
        ctx.getJSONArray("goals").getJSONObject(0).put("evidence", org.json.JSONArray().put(org.json.JSONObject().put("start", 0).put("end", 3).put("quote", "old")))
        val withEvidence = prev.copy(contextJson = ctx.toString())
        assertTrue(DoctorConversationContext.goals(CarriedForward.draftFrom("p1", cid, withEvidence)).all { it.evidence.isEmpty() })
    }
    @Test fun noPreviousVersionGivesPlainEmptyDraft() {
        val d = CarriedForward.draftFrom("p1", cid, null)
        val plain = Conversation("p1", conversationId = cid).let { it.copy(contextJson = DoctorConversationContext.initial(it)) }
        assertEquals(plain.copy(updated = 0), d.copy(updated = 0))
        assertTrue(DoctorConversationContext.goals(d).none { CarriedForward.isOrigin(it.origin) })
    }
    @Test fun confirmingChangesStateButKeepsCarriedOrigin() {
        val g = DoctorConversationContext.goals(CarriedForward.draftFrom("p1", cid, prev)).first()
        val c = CarriedForward.confirm(g)
        assertEquals("discussed", c.status); assertFalse(CarriedForward.unconfirmed(c)); assertEquals(g.origin, c.origin); assertTrue(c.evidence.isEmpty())
    }
    @Test fun unconfirmedCarriedSectionsCannotPublish() {
        val person = Person("p1", "P")
        val d = CarriedForward.draftFrom("p1", cid, prev).let { it.copy(source = "new words", contextJson = org.json.JSONObject(it.contextJson).put("sourceHash", DoctorConversationContext.sha256("new words".toByteArray())).toString()) }
        val e = assertThrows(IllegalArgumentException::class.java) { NoteRules.publish(person, d, "Dr", true, 4) }
        assertTrue(e.message!!.contains("carried forward"))
        val confirmed = DoctorConversationContext.goals(d).map(CarriedForward::confirm)
        val ctx = org.json.JSONObject(d.contextJson).put("goals", org.json.JSONArray(confirmed.map { DoctorConversationContext.goalJson(it) }))
        assertEquals(4, NoteRules.publish(person, d.copy(contextJson = ctx.toString()), "Dr", true, 4).version)
    }
}
