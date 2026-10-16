package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorRecordingReviewTest {
    @Test fun recordingAloneNeverCreatesTranscriptOrCoverage() { val state = DoctorRecordingReviewState(clipLabel = "Saved clip"); assertEquals("", state.sourceText); assertEquals(DoctorTranscriptKind.NONE, state.sourceKind); assertTrue(state.coverage.all { it.status == DoctorCoverageStatus.NOT_ASSESSED }) }
    @Test fun fullSourceOrManualReviewRequiredForDiscussed() { val item = DoctorCoverageItem("Priority?", DoctorCoverageStatus.REVIEWED_DISCUSSED, "I want"); assertEquals(DoctorCoverageStatus.NEEDS_REVIEW, effectiveDoctorCoverage(item, "Patient: I want no decision today.")); assertEquals(DoctorCoverageStatus.REVIEWED_DISCUSSED, effectiveDoctorCoverage(item.copy(quote = "Patient: I want no decision today."), "Patient: I want no decision today.")); assertEquals(DoctorCoverageStatus.REVIEWED_DISCUSSED, effectiveDoctorCoverage(item.copy(quote = "", manuallyReviewed = true), "")) }
    @Test fun clippedNegationNeverBecomesEvidenceEvenWithManualCheckbox() { val source = "Patient: I am not willing to decide today."; assertFalse(isDoctorSourceQuote(source, "willing")); assertTrue(isDoctorSourceQuote(source, source)); assertEquals(DoctorCoverageStatus.NEEDS_REVIEW, effectiveDoctorCoverage(DoctorCoverageItem("Preference?", DoctorCoverageStatus.REVIEWED_DISCUSSED, "willing", true), source)); assertEquals(DoctorCoverageStatus.NEEDS_REVIEW, effectiveDoctorCoverage(DoctorCoverageItem("Preference?", DoctorCoverageStatus.NOT_FOUND_IN_TRANSCRIPT, manuallyReviewed = true), "")) }
    @Test fun clocksAreSafeAndReadable() { assertEquals("0:00", doctorAudioClock(-1)); assertEquals("1:05", doctorAudioClock(65000)) }
}
