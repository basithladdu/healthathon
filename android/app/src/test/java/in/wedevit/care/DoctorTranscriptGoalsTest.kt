package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorTranscriptGoalsTest {
    private val rows = listOf(
        DoctorPreparationQuestion("matters", "What matters most to you about family time?", 0),
        DoctorPreparationQuestion("travel", "Who will help with travel?", 4),
    )
    private fun match(source: String) = LocalQuestionMatcher.match(source, rows.map { CareQuestion(it.id, it.text, it.section) })

    @Test fun statusIsAnsweredSuggestedOrOpen() {
        val source = "Doctor: Thank you for coming.\nPatient: Family time matters most to me."
        val m = match(source)
        assertEquals(TranscriptGoalStatus.Suggested, transcriptGoalStatus(rows[0], m))
        assertEquals(TranscriptGoalStatus.Open, transcriptGoalStatus(rows[1], m))
        assertEquals(TranscriptGoalStatus.Answered, transcriptGoalStatus(rows[1].copy(answer = "Anita"), m))
        assertEquals(TranscriptGoalStatus.Open, transcriptGoalStatus(rows[0], null))
    }

    @Test fun suggestionPointsAtTheLineItCameFrom() {
        val source = "Doctor: Thank you for coming.\nPatient: Family time matters most to me."
        val s = transcriptGoalSuggestion(match(source), "matters")
        assertNotNull(s)
        assertEquals(2, s!!.line.number)
        assertEquals("Patient: Family time matters most to me.", s.quote)
        assertEquals(s.quote, source.substring(s.charStart, s.charEnd))
    }

    @Test fun acceptedAnswerLinksToItsLineButEditedWordsDoNot() {
        val source = "Doctor: Thank you for coming.\nPatient: Family time matters most to me."
        val accepted = transcriptGoalSpan(match(source), "matters")
        assertEquals(2, transcriptAnswerLine(source, accepted)?.number)
        assertNull(transcriptAnswerLine(source, "Family time matters, and less pain."))
        assertNull(transcriptAnswerLine(source, ""))
        // The answer loses its link when the transcript line it came from is changed.
        assertNull(transcriptAnswerLine(source.replace("most", "a lot"), accepted))
    }

    @Test fun checklistUpdatesLiveAsTheTranscriptGrows() {
        var source = "Patient: Family time matters most to me."
        assertEquals(1, transcriptChecklist(rows, match(source)).covered)
        source += "\nAnita will help with travel."
        assertEquals(1, transcriptChecklist(rows, match(source)).open)
        source = "Patient: Family time matters most to me.\nDoctor: Who can help with travel?\nAnita will help with travel."
        val list = transcriptChecklist(rows, match(source))
        assertEquals(2, list.covered); assertEquals(0, list.open)
        assertEquals(3, transcriptGoalSuggestion(match(source), "travel")!!.line.number)
        assertTrue(list.summary.startsWith("2 of 2 covered"))
        assertEquals(0, transcriptChecklist(rows, match("")).covered)
    }

    @Test fun labelsSayTranscriptNeverAi() {
        TranscriptGoalStatus.entries.forEach { assertFalse(transcriptChecklistLabel(it).contains("AI")) }
        assertTrue(transcriptChecklistLabel(TranscriptGoalStatus.Suggested).startsWith("Suggested from the transcript"))
        listOf(true, false).forEach { sample -> assertFalse(doctorTranscriptOrigin(sample, 0, "").contains("AI")) }
    }

    @Test fun walkthroughSampleIsLabelledAndOnlyInTheWalkthrough() {
        val profile = DoctorTestProfiles.profiles.first()
        val person = DoctorTestProfiles.personId(profile)
        val sample = doctorWalkthroughSample(person)
        assertNotNull(sample)
        assertEquals(DoctorTestProfiles.fixture(profile, 123L).draft.source, sample)
        assertNotNull(doctorWalkthroughSample(DoctorCareDemoData.PERSON_ID))
        assertNull(doctorWalkthroughSample("someone-real"))
        assertNull(doctorWalkthroughSample(""))
        assertTrue(doctorTranscriptIsSample(true, sample!!, sample))
        assertTrue(doctorTranscriptIsSample(true, "$sample\nDoctor added a line.", sample))
        assertFalse(doctorTranscriptIsSample(false, sample, sample))
        assertFalse(doctorTranscriptIsSample(true, "My own typed words.", sample))
        assertFalse(doctorTranscriptIsSample(true, "", sample))
        assertTrue(doctorTranscriptOrigin(true, 0, "").startsWith("Sample"))
        assertFalse(doctorTranscriptOrigin(false, 0, "").startsWith("Sample"))
    }
}
