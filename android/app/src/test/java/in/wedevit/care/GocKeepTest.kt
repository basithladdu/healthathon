package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The talk is kept when the plan's own rules (V4) block the save (H1), and confirmed choices the plan lost come back (M9). */
class GocKeepTest {
    private val now = "2026-11-01T10:00:00Z"
    private val today = LocalDate.of(2026, 11, 1)
    private fun stored() = EctprLifecycle.newDraft("p1", "Dr Rao", now, EctprPatient(name = "Asha Rao", uhid = "UH-1"), id = "r1")
    private fun cpr(at: String = "2026-11-01T10:05:00Z") = GocTopicRecord(GocDecision.CPR, GocTopicStatus.CONFIRMED, Cpr.ATTEMPT.name, "We want CPR", at)
    private fun talk(vararg topics: GocTopicRecord) = GocTranscript(lines = GocLines.fromText("We want CPR."), topics = topics.toList(), note = "CPR: attempt.")

    @Test fun aBlockedChoiceStillKeepsTheTalk_notTheChoice() {
        val base = stored()
        val working = GocPlan.apply(base, GocDecision.CPR, Cpr.ATTEMPT.name).copy(gocTranscript = talk(cpr()))
        assertTrue("V4 blocks the working copy", EctprRules.saveBlockers(working, today).any { it.id == "V4" })
        val kept = GocKeep.talkOnly(base, working, today)
        assertEquals(working.gocTranscript, kept.gocTranscript)
        assertNull("the plan's V4 rule is not weakened", kept.choices.cpr)
        assertTrue(EctprRules.saveBlockers(kept, today).isEmpty())
    }

    @Test fun keptTalkCarriesWhatMattersWords() {
        val base = stored()
        val working = GocPlan.apply(GocPlan.apply(base, GocDecision.CPR, Cpr.ATTEMPT.name), GocDecision.VALUES, GOC_VALUES_OWN_WORDS, listOf("Home with family")).copy(gocTranscript = talk(cpr()))
        assertEquals("Home with family", GocKeep.talkOnly(base, working, today).values.ownWords)
    }

    @Test fun aConfirmedChoiceThePlanLostComesBack() {
        val lost = stored()
        assertEquals(Cpr.ATTEMPT, GocKeep.restore(lost, listOf(cpr())).choices.cpr)
    }

    @Test fun aChoiceMadeLaterInTheFullPlanIsNotOverwritten() {
        val later = stored().let { it.copy(choices = it.choices.copy(cpr = Cpr.DNACPR), updatedAt = "2026-11-01T11:00:00Z") }
        assertEquals(Cpr.DNACPR, GocKeep.restore(later, listOf(cpr())).choices.cpr)
    }

    @Test fun aCarriedValueIsReplacedWhenTheTalkConfirmedAfterTheLastSave() {
        val carried = stored().let { it.copy(choices = it.choices.copy(cpr = Cpr.DNACPR)) }
        assertEquals(Cpr.ATTEMPT, GocKeep.restore(carried, listOf(cpr())).choices.cpr)
    }

    @Test fun restoreIsANoOpWhenThePlanHoldsTheChoice_andWordsAreNotDoubled() {
        val words = GocTopicRecord(GocDecision.VALUES, GocTopicStatus.CONFIRMED, GOC_VALUES_OWN_WORDS, "Home with family", "2026-11-01T10:05:00Z")
        val held = GocPlan.apply(GocPlan.apply(stored(), GocDecision.CPR, Cpr.ATTEMPT.name), GocDecision.VALUES, GOC_VALUES_OWN_WORDS, listOf("Home with family"))
        assertEquals(held, GocKeep.restore(held, listOf(cpr(), words)))
        assertEquals("Home with family", GocKeep.restore(stored(), listOf(words)).values.ownWords)
    }

    @Test fun noneAndVerifiedNotDiscussedAreLeftAlone() {
        val base = stored()
        val none = GocTopicRecord(GocDecision.CPR, GocTopicStatus.CONFIRMED, null, at = "2026-11-01T10:05:00Z")
        val verified = GocTopicRecord(GocDecision.ICU, GocTopicStatus.NOT_DISCUSSED_VERIFIED, at = "2026-11-01T10:05:00Z")
        assertEquals(base, GocKeep.restore(base, listOf(none, verified)))
    }
}
