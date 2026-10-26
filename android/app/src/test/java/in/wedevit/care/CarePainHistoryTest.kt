package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CarePainHistoryTest {
    private fun value(date: String = "2026-10-23", rating: Int? = 5) = SymptomNote("Pain", rating, date, "10:00", "now", "patient", "Fictional patient", painLocation = "Left knee",
        painBodySites = listOf("left-leg"), painBodyPoints = listOf(PainBodyPoint3d("left-leg", .2, .3, .4)), painBodyModel = PainBodyMapRules.MODEL)
    private fun row(id: String, note: SymptomNote = value(), person: String = "fixture-person") = CareItem(id = id, personId = person, kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(note))

    @Test fun historyUsesActualObservationDatesForOnlyTheSelectedPersonsPainAndIncludesUnrated() {
        val older = row("older", value("2026-10-21"))
        val newer = row("newer", value("2026-10-23", null)).copy(created = 1)
        val other = row("different-person", person = "other-person")
        val nausea = row("nausea", value().copy(symptom = "Nausea", painLocation = "", painBodySites = emptyList(), painBodyPoints = emptyList(), painBodyModel = ""))
        val history = PainHistoryRules.entries(listOf(older, other, nausea, newer, older.copy(id = "wrong-kind", kind = "journal")), "fixture-person")
        assertEquals(listOf("newer", "older"), history.map { it.first.id })
        assertNull(history.first().second.rating)
    }
    @Test fun exactSavedPointsRatingsAndContextsAreNotAggregatedOrRewritten() {
        val first = row("one")
        val second = row("two", value("2026-10-22", 9).copy(recall = "worst-24h", respondent = "caregiver", author = "Fixture observer", painBodyPoints = listOf(PainBodyPoint3d("left-leg", .4, .3, .4))))
        val originals = listOf(first, second)
        val before = originals.map { it.detail }
        val history = PainHistoryRules.entries(originals, "fixture-person")
        assertEquals(value(), history[0].second)
        assertEquals(.4, history[1].second.painBodyPoints.single().x, .000001)
        assertEquals("caregiver", history[1].second.respondent)
        assertEquals(before, originals.map { it.detail })
    }
    @Test fun historySelectionFallsBackToARealEntryAfterADeletionOrPersonChange() {
        val history = PainHistoryRules.entries(listOf(row("latest"), row("old", value("2026-10-20"))), "fixture-person")
        assertEquals("old", PainHistoryRules.selectedId(history, "old"))
        assertEquals("latest", PainHistoryRules.selectedId(history, "removed"))
        assertNull(PainHistoryRules.selectedId(emptyList(), "old"))
    }
    @Test fun legacyRegionTextAndMissingLocationsNeverInventAnExactPoint() {
        assertEquals("points", PainHistoryRules.locationKind(value()))
        assertEquals("regions", PainHistoryRules.locationKind(value().copy(painBodyPoints = emptyList())))
        assertEquals("text", PainHistoryRules.locationKind(value().copy(painBodyPoints = emptyList(), painBodySites = emptyList(), painBodyModel = "")))
        assertEquals("none", PainHistoryRules.locationKind(value().copy(painBodyPoints = emptyList(), painBodySites = emptyList(), painBodyModel = "", painLocation = "")))
        assertEquals("unsupported-model", PainHistoryRules.locationKind(value().copy(painBodyModel = "future-model")))
    }
}
