package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareSymptomSummaryTest {
    private fun row(id: String, symptom: String, rating: Int?, date: String, time: String = "10:00", person: String = "p") =
        CareItem(id = id, personId = person, kind = "checkin", title = symptom,
            detail = SymptomNoteCodec.encode(SymptomNote(symptom, rating, date, time, "now", "patient", "Person")))

    @Test fun eachSymptomShowsOnceWithItsLatestNumberAndArrow() {
        val entries = listOf(
            row("a", "Pain", 6, "2026-10-20"), row("b", "Pain", 4, "2026-10-22"), row("c", "Pain", 7, "2026-10-18"),
            row("d", "Nausea", 3, "2026-10-21"), row("e", "Pain", 9, "2026-10-22", person = "other"),
        )
        val s = SymptomSummaryRules.summaries(entries, "p")
        assertEquals(listOf("Pain", "Nausea"), s.map { it.symptom })
        val pain = s.first()
        assertEquals("b", pain.latest.id)
        assertEquals(4, pain.latestScore); assertEquals(6, pain.previousScore); assertEquals(-1, pain.trend)
        assertEquals(listOf("b", "a", "c"), pain.notes.map { it.id })
        assertNull(s[1].trend)
    }

    @Test fun checkInsCondenseToOneRowPerDayNewestFirst() {
        val utc = java.time.ZoneId.of("UTC")
        val oct21 = java.time.LocalDate.of(2026, 10, 21).atStartOfDay(utc).toInstant().toEpochMilli() + 3_600_000
        val mood = CareItem(id = "m", personId = "p", kind = "checkin", title = CheckInMood.GOOD, created = oct21)
        val days = CheckInDays.group(listOf(mood, row("a", "Pain", 6, "2026-10-20"), row("b", "Nausea", 3, "2026-10-21"), row("c", "Pain", 2, "2026-10-21")), utc)
        assertEquals(listOf("2026-10-21", "2026-10-20"), days.map { it.day.toString() })
        assertEquals(CheckInMood.GOOD, days[0].mood); assertEquals(2, days[0].symptomNotes); assertEquals(3, days[0].items.size)
        assertNull(days[1].mood); assertEquals(1, days[1].symptomNotes); assertFalse(days[1].distressAnswered)
    }

    @Test fun spellingAndSpacesDoNotSplitASymptom() {
        assertEquals("Pain", SymptomSummaryRules.key(" pain "))
        assertEquals("Hiccups", SymptomSummaryRules.key("Hiccups "))
    }
}
