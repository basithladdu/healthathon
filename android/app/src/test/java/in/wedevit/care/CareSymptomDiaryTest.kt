package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CareSymptomDiaryTest {
    private val now = LocalDateTime.of(2026, 10, 24, 18, 0)
    private fun note(rating: Int? = 4, date: String = "2026-10-22", time: String = "10:00") = SymptomNote("Pain", rating, date, time, "now", "patient", "Person", painLocation = "Left leg", painTriggers = listOf("rest"))
    private fun row(id: String, value: SymptomNote, person: String = "person-a") = CareItem(id = id, personId = person, kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(value))

    @Test fun zeroAndUnableToRateRoundTripWithoutConfusingMissingWithNone() {
        assertEquals(0, SymptomNoteCodec.decode(SymptomNoteCodec.encode(note(0)))!!.rating)
        assertNull(SymptomNoteCodec.decode(SymptomNoteCodec.encode(note(null)))!!.rating)
        assertEquals("Not rated", SymptomDiaryRules.ratingLabel(null))
        assertEquals("0/10", SymptomDiaryRules.ratingLabel(0))
    }
    @Test fun contextNotesAndIndependentSubjectiveChangePersistExactly() {
        val value = note().copy(respondent = "assisted-patient", author = "Helper", startedOn = "2026-10-20", note = "After turning", painTriggers = listOf("turning"), selfReportedChange = "better", comparedWithId = "earlier")
        assertEquals(value, SymptomNoteCodec.decode(SymptomNoteCodec.encode(value)))
    }
    @Test fun noSeededBaselineOrComparisonFromUnratedOrOldMoodRecords() {
        val current = row("current", note())
        assertNull(SymptomDiaryRules.comparison(emptyList(), current, note()))
        assertNull(SymptomDiaryRules.comparison(listOf(row("unrated", note(null, "2026-10-21")), CareItem(personId = "person-a", kind = "checkin", title = "A hard day")), current, note()))
        assertNull(SymptomDiaryRules.comparison(listOf(row("previous", note(8, "2026-10-21"))), current, note(null)))
    }
    @Test fun firstAndLatestRealMatchingRatingsSupplyDatedBaselineAndDifference() {
        val first = row("baseline", note(7, "2026-10-20"))
        val previous = row("previous", note(6, "2026-10-21"))
        val current = row("current", note(4))
        val comparison = SymptomDiaryRules.comparison(listOf(previous, current, first), current, note())!!
        assertEquals(first.id, comparison.baseline.id)
        assertEquals(previous.id, comparison.previous.id)
        assertEquals(-2, comparison.difference)
    }
    @Test fun patientRecallRespondentPainContextAndTimeMustMatchForComparison() {
        val current = row("current", note())
        val variants = listOf(note(8, "2026-10-21").copy(recall = "average-24h"), note(8, "2026-10-21").copy(respondent = "caregiver"),
            note(8, "2026-10-21").copy(painLocation = "Arm"), note(8, "2026-10-21").copy(painTriggers = listOf("walking")), note(8, "2026-10-23"))
        variants.forEachIndexed { index, value -> assertNull(SymptomDiaryRules.comparison(listOf(row("earlier-$index", value)), current, note())) }
        assertNull(SymptomDiaryRules.comparison(listOf(row("other-person", note(8, "2026-10-21"), "person-b")), current, note()))
    }
    @Test fun independentProxyObserversCannotBeComparedAsTheSamePerson() {
        val currentNote = note().copy(respondent = "caregiver", author = "A")
        val prior = row("earlier", note(8, "2026-10-21").copy(respondent = "caregiver", author = "B"))
        assertNull(SymptomDiaryRules.comparison(listOf(prior), row("current", currentNote), currentNote))
    }
    @Test fun invalidRatingFutureObservationOnsetAndPartialComparisonAreRejected() {
        listOf(note(-1), note(11), note().copy(observedDate = "2026-10-25"), note().copy(observedTime = "25:00"), note().copy(startedOn = "2026-10-23"), note().copy(respondent = ""), note().copy(author = ""), note().copy(selfReportedChange = "better")).forEach {
            assertNotNull(SymptomDiaryRules.error(it, now))
        }
        assertNull(SymptomDiaryRules.error(note(0), now))
        assertNull(SymptomDiaryRules.error(note(null), now))
    }
    @Test fun legacyAndCorruptPayloadsAreRetainedWithoutInventingRatings() {
        assertNull(SymptomNoteCodec.decode("A hard day"))
        assertNull(SymptomNoteCodec.decode(SymptomNoteCodec.PREFIX + "bad json"))
        assertFalse(SymptomNoteCodec.readable(SymptomNoteCodec.PREFIX + "bad json").contains("bad json"))
        assertEquals("A hard day", CareEntryCodec.readable("A hard day"))
        val text = CareEntryCodec.readable(SymptomNoteCodec.encode(note()))
        assertTrue(text.contains("4/10")); assertTrue(text.contains("Left leg")); assertFalse(text.contains("\"rating\""))
    }
    @Test fun symptomDetailsNeverBecomeAnAutomaticClinicalTotalOrSeverityBand() {
        val text = SymptomNoteCodec.readable(SymptomNoteCodec.encode(note(9)))
        assertTrue(text.contains("9/10"))
        assertFalse(text.contains("Severe")); assertFalse(text.contains("distress", true)); assertFalse(text.contains("/90"))
    }
}
