package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test

class DoctorTranscriptGoalsTest {
    @Test fun fillKeepsSavedAnswersAndLeavesUnmatchedBlank() {
        val rows = listOf(DoctorPreparationQuestion("a", "Q?", 0, "mine"), DoctorPreparationQuestion("b", "Q2?", 0, ""))
        assertEquals(rows, transcriptGoalsFill(rows, null))
        assertEquals(TranscriptGoalStatus.Answered, transcriptGoalStatus(rows[0], null))
        assertEquals(TranscriptGoalStatus.NotFound, transcriptGoalStatus(rows[1], null))
    }
}
