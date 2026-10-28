package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class CareSymptomDiaryTest {
    private val now = LocalDateTime.of(2026, 10, 24, 18, 0)
    private fun note(rating: Int? = 4, date: String = "2026-10-22", time: String = "10:00") = SymptomNote("Pain", rating, date, time, "now", "patient", "Person", painLocation = "Left leg", painTriggers = listOf("rest"))
    private fun row(id: String, value: SymptomNote, person: String = "person-a") = CareItem(id = id, personId = person, kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(value))

    @Test fun rahulHistoryUsesObservedDetailsWithoutChangingStoredEvidence() {
        val value = note(10, "2026-10-26", "16:01").copy(author = "Rahul", note = "After walking")
        val stored = SymptomNoteCodec.encode(value)
        val item = row("rahul", value).copy(title = "Pain 10/10", created = 1793001720000L)
        val text = CareEntryCodec.readable(item.detail)
        assertTrue(text.contains("Observed: 2026-10-26 16:01"))
        assertTrue(text.contains("10/10")); assertTrue(text.contains("Rahul"))
        assertTrue(text.contains("After walking")); assertTrue(text.contains("Left leg"))
        assertFalse(text.contains(SymptomNoteCodec.PREFIX)); assertFalse(text.contains("\"rating\""))
        assertFalse(text.contains("Started:")); assertFalse(text.contains("Pain areas:"))
        assertEquals(stored, item.detail); assertEquals(value, SymptomNoteCodec.decode(item.detail))
    }

    @Test fun historyFormattingKeepsLegacyTextAndHidesMalformedEncodedData() {
        assertEquals("Pain after walking", CareEntryCodec.readable("Pain after walking"))
        assertEquals("Pain after walking", SymptomNoteCodec.readable("Pain after walking"))
        listOf("bad json", "{\"symptom\":\"Pain\",\"rating\":10}").forEach { payload ->
            val text = CareEntryCodec.readable(SymptomNoteCodec.PREFIX + payload)
            assertTrue(text.contains("original record is retained"))
            assertFalse(text.contains(payload)); assertFalse(text.contains("10/10"))
        }
    }

    @Test fun readableHistoryShowsOnlyRecordedLocationsAndNoInventedScore() {
        val value = note(null).copy(painLocation = "", painTriggers = emptyList(), painBodySites = listOf("left-leg"))
        val text = CareEntryCodec.readable(SymptomNoteCodec.encode(value))
        assertTrue(text.contains("Not rated")); assertTrue(text.contains("Pain areas:"))
        assertFalse(text.contains("0/10")); assertFalse(text.contains("Started:"))
    }

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
    @Test fun symptomDetailsNeverBecomeAnAutomaticClinicalTotalAndOnlyPainShowsItsBand() {
        val text = SymptomNoteCodec.readable(SymptomNoteCodec.encode(note(9)))
        assertTrue(text.contains("9/10 · Severe"))
        assertFalse(text.contains("distress", true)); assertFalse(text.contains("/90"))
        val nausea = SymptomNoteCodec.readable(SymptomNoteCodec.encode(note(9).copy(symptom = "Nausea", painLocation = "", painTriggers = emptyList())))
        assertTrue(nausea.contains("9/10")); assertFalse(nausea.contains("Severe"))
    }
    @Test fun staleEditorCannotOverwriteCloudOrRevisionZeroLocalChanges() {
        val opened = row("edit", note())
        val token = SymptomDiaryRules.editToken(opened)
        assertNull(SymptomDiaryRules.saveConflict(opened, "person-a", token))
        assertNotNull(SymptomDiaryRules.saveConflict(opened.copy(revision = 1), "person-a", token))
        assertNotNull(SymptomDiaryRules.saveConflict(opened.copy(detail = SymptomNoteCodec.encode(note(8))), "person-a", token))
        assertNotNull(SymptomDiaryRules.saveConflict(opened.copy(title = "Updated locally"), "person-a", token))
    }
    @Test fun deletedEditsAndUnexpectedInsertCollisionsCannotRecreateOrReplaceRecords() {
        val opened = row("edit", note())
        val token = SymptomDiaryRules.editToken(opened)
        assertNotNull(SymptomDiaryRules.saveConflict(null, "person-a", token))
        assertNotNull(SymptomDiaryRules.saveConflict(opened, "person-a", null))
        assertNotNull(SymptomDiaryRules.saveConflict(opened, "person-b", token))
        val wrongKind = opened.copy(kind = "journal")
        assertNotNull(SymptomDiaryRules.saveConflict(wrongKind, "person-a", SymptomDiaryRules.editToken(wrongKind)))
        assertNull(SymptomDiaryRules.saveConflict(null, "person-a", null))
    }
    @Test fun clinicalReviewChoicesUseStableStoredIdentifiersAndRetainLegacyRecords() {
        listOf("Pain", "Tiredness", "Poor appetite", "Dry mouth", "Breathlessness", "Constipation", "Nausea", "Vomiting",
            "Difficulty swallowing", "Sleepiness", "Cough", "Bowel / bladder incontinence", "Mood", "Diarrhea", "Overall well-being", "Other").forEach {
            assertTrue(it, it in SymptomDiaryRules.symptoms)
        }
        assertFalse("Sadness" in SymptomDiaryRules.symptoms); assertFalse("Worry" in SymptomDiaryRules.symptoms)
        assertFalse("Sleep difficulty" in SymptomDiaryRules.symptoms)
        assertFalse("Weight loss" in SymptomDiaryRules.symptoms)
        assertFalse("Dizziness" in SymptomDiaryRules.symptoms)
        assertFalse("Irritability / restlessness" in SymptomDiaryRules.symptoms)
        assertEquals("Fatigue / lack of energy", SymptomDiaryRules.symptomLabel("Tiredness", false))
        assertEquals("Dry / sore mouth", SymptomDiaryRules.symptomLabel("Dry mouth", false))
        val legacy = note().copy(symptom = "Sleep difficulty", painLocation = "", painTriggers = emptyList())
        assertEquals(legacy, SymptomNoteCodec.decode(SymptomNoteCodec.encode(legacy)))
        assertEquals("सोने में परेशानी", SymptomDiaryRules.symptomLabel(legacy.symptom, true))
        assertEquals("Other", SymptomDiaryRules.symptoms.keys.last())
    }
    @Test fun overallWellbeingHasExplicitBestWorstAnchorsAndItsOwnNonDiagnosticScale() {
        val value = note(0).copy(symptom = "Overall well-being", painLocation = "", painTriggers = emptyList(), scale = SymptomDiaryRules.scaleForSymptom("Overall well-being"))
        assertEquals(SymptomDiaryRules.WELLBEING_SCALE, value.scale)
        assertNull(SymptomDiaryRules.error(value, now))
        assertEquals(value, SymptomNoteCodec.decode(SymptomNoteCodec.encode(value)))
        val text = SymptomNoteCodec.readable(SymptomNoteCodec.encode(value))
        assertTrue(text.contains("0 = best possible well-being"))
        assertTrue(text.contains("10 = worst possible well-being"))
        assertFalse(text.contains("0 = none"))
        assertTrue(SymptomDiaryRules.ratingAnchors(value.scale, true).contains("सबसे अच्छी तबीयत"))
        assertNotNull(SymptomDiaryRules.error(value.copy(symptom = "Cough"), now))
    }
    @Test fun newWellbeingNeverReinterpretsEarlierCustomSymptomRatingsOrComparesDifferentAnchors() {
        val value = note(4).copy(symptom = "Overall well-being", painLocation = "", painTriggers = emptyList(), scale = SymptomDiaryRules.WELLBEING_SCALE)
        val earlierCustom = value.copy(observedDate = "2026-10-21", rating = 7, scale = SymptomDiaryRules.PERSONAL_SCALE)
        assertEquals(earlierCustom, SymptomNoteCodec.decode(SymptomNoteCodec.encode(earlierCustom)))
        assertNull(SymptomDiaryRules.comparison(listOf(row("earlier", earlierCustom)), row("current", value), value))
        assertEquals(-3, SymptomDiaryRules.comparison(listOf(row("earlier", earlierCustom.copy(scale = SymptomDiaryRules.WELLBEING_SCALE))), row("current", value), value)!!.difference)
        assertEquals("0 = none · 10 = worst imaginable", SymptomDiaryRules.ratingAnchors(earlierCustom.scale, false))
    }
    @Test fun quickFollowupKeepsRealContextButNeverPrefillsANewRatingOrReusesTheOldNarrative() {
        val old = note(9).copy(note = "Yesterday", startedOn = "2026-10-20", selfReportedChange = "worse", comparedWithId = "old",
            painBodySites = listOf("left-leg"), painBodyPoints = listOf(PainBodyPoint3d("left-leg", .2, .3, .4)), painBodyModel = PainBodyMapRules.MODEL)
        val next = SymptomDiaryRules.followUp(old, now)
        assertNull(next.rating); assertEquals("", next.note); assertEquals("", next.selfReportedChange); assertEquals("", next.comparedWithId)
        assertEquals("2026-10-24", next.observedDate); assertEquals("18:00", next.observedTime)
        assertEquals(old.painBodyPoints, next.painBodyPoints); assertEquals(old.painBodySites, next.painBodySites); assertEquals(old.painLocation, next.painLocation)
        assertEquals(old.startedOn, next.startedOn); assertEquals(old.respondent, next.respondent); assertEquals(old.recall, next.recall)
        assertNull(SymptomDiaryRules.error(next, now))
    }
    @Test fun trendOnlyUsesActualChronologicalRatedEntriesFromTheSameContext() {
        val current = row("current", note(4))
        val matching = row("first", note(8, "2026-10-20"))
        val wrongSite = row("other-site", note(7, "2026-10-21").copy(painBodySites = listOf("right-leg")))
        val values = SymptomDiaryRules.series(listOf(current, wrongSite, row("unrated", note(null)), matching, matching.copy(id = "other-person", personId = "other")), current, note(4))
        assertEquals(listOf("first", "current"), values.map { it.first.id })
        assertEquals(listOf(8, 4), values.map { it.second.rating })
    }
}
