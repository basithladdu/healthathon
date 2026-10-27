package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class FeedbackTest {
    @Test fun ratingLabelsHaveAccessibleStates() { assertEquals("Not great", FeedbackRules.label(0f)); assertEquals("Okay", FeedbackRules.label(.5f)); assertEquals("Good", FeedbackRules.label(1f)) }
    @Test fun valuesAreValidated() { assertNotNull(FeedbackRules.error(Float.NaN, "")); assertNotNull(FeedbackRules.error(1.1f, "")); assertNotNull(FeedbackRules.error(.5f, "a".repeat(501))); assertNull(FeedbackRules.error(.5f, "a".repeat(500))) }
    @Test fun faceMorphsContinuouslyRatherThanThreeImages() { assertEquals(0f, FeedbackRules.mouthCurve(.5f), .0001f); assertTrue(FeedbackRules.mouthCurve(0f) < 0f); assertTrue(FeedbackRules.mouthCurve(1f) > 0f); assertEquals(.18f, FeedbackRules.eyeHeight(0f), .0001f); assertEquals(.05f, FeedbackRules.eyeHeight(.5f), .0001f); assertEquals(.31f, FeedbackRules.eyeHeight(1f), .0001f); assertEquals(.28f, FeedbackRules.eyeWidth(.5f), .0001f); assertEquals(FeedbackRules.eyeHeight(.4999f), FeedbackRules.eyeHeight(.5001f), .0002f); assertEquals(.0048f, FeedbackRules.mouthCurve(.51f) - FeedbackRules.mouthCurve(.5f), .0001f) }
    @Test fun dailyMoodLookupNeverTargetsSameDayStructuredPain() {
        val dayStart = 1000L
        val pain = CareItem(id = "pain", personId = "person", kind = "checkin", title = "Pain",
            detail = SymptomNoteCodec.encode(SymptomNote("Pain", 7, "2026-10-27", "12:00", "now", "patient", "Person")), created = dayStart + 20)
        // No mood update target means saving a mood creates a separate entry instead of replacing pain.
        assertNull(CheckInMood.today(listOf(pain), "person", dayStart))
        val mood = CareItem(id = "mood", personId = "person", kind = "checkin", title = CheckInMood.OKAY,
            detail = "An ordinary daily note", created = dayStart + 10)
        val yesterday = mood.copy(id = "yesterday", created = dayStart - 1)
        val otherPerson = mood.copy(id = "other", personId = "someone-else")
        assertSame(mood, CheckInMood.today(listOf(pain, yesterday, otherPerson, mood), "person", dayStart))
    }
}
