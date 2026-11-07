package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Time ranges for the symptom, distress and lab charts (3 Nov meeting: "zoom out and see a trend over weeks and months"). */
class CareChartRangeTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val now = careEpoch(today) + 15 * 3_600_000L // 3 pm
    private fun at(day: LocalDate, v: Double) = ChartPoint(careEpoch(day).toDouble() + 43_200_000.0, v)

    @Test fun aWeekIsTodayAndTheSixDaysBefore() {
        assertEquals(careEpoch(today.minusDays(6)), ChartRangeRules.start(ChartRange.WEEK, now))
        assertEquals(careEpoch(today.minusDays(29)), ChartRangeRules.start(ChartRange.MONTH, now))
        assertEquals(careEpoch(today.minusDays(90)), ChartRangeRules.start(ChartRange.QUARTER, now))
        assertNull(ChartRangeRules.start(ChartRange.ALL, now))
    }

    @Test fun filterKeepsOnlyPointsInsideTheRangeAndAllKeepsEverything() {
        val points = listOf(at(today.minusDays(100), 7.0), at(today.minusDays(40), 6.0), at(today.minusDays(10), 5.0), at(today.minusDays(6), 4.0), at(today, 2.0))
        assertEquals(listOf(4.0, 2.0), ChartRangeRules.filter(points, ChartRange.WEEK, now).map { it.y })
        assertEquals(listOf(5.0, 4.0, 2.0), ChartRangeRules.filter(points, ChartRange.MONTH, now).map { it.y })
        assertEquals(4, ChartRangeRules.filter(points, ChartRange.QUARTER, now).size)
        assertEquals(points, ChartRangeRules.filter(points, ChartRange.ALL, now))
    }

    @Test fun onlyRangesThatChangeTheViewAreOffered() {
        // Everything from this week: no chips at all (only "All").
        assertEquals(listOf(ChartRange.ALL), ChartRangeRules.choices(listOf(at(today.minusDays(2), 3.0), at(today, 4.0)), now))
        // Two months of notes: a week and a month narrow it; three months would show the same as All.
        val twoMonths = listOf(at(today.minusDays(60), 3.0), at(today.minusDays(20), 5.0), at(today, 4.0))
        assertEquals(listOf(ChartRange.WEEK, ChartRange.MONTH, ChartRange.ALL), ChartRangeRules.choices(twoMonths, now))
        // A range that would be empty is not offered.
        assertEquals(listOf(ChartRange.ALL), ChartRangeRules.choices(listOf(at(today.minusDays(200), 3.0), at(today.minusDays(150), 5.0)), now))
        assertEquals(listOf(ChartRange.ALL), ChartRangeRules.choices(emptyList(), now))
    }

    @Test fun aRangeSpansFromItsStartToNow() {
        val points = listOf(at(today.minusDays(3), 3.0))
        assertEquals(careEpoch(today.minusDays(6)).toDouble() to now.toDouble(), ChartRangeRules.span(points, ChartRange.WEEK, now))
        assertNull(ChartRangeRules.span(points, ChartRange.ALL, now))
    }

    @Test fun numbersDropTrailingZeros() {
        assertEquals("4", ChartRangeRules.number(4.0)); assertEquals("4.26", ChartRangeRules.number(4.256)); assertEquals("0.5", ChartRangeRules.number(0.5))
    }

    @Test fun rangeLabelsHaveHindi() {
        ChartRange.values().forEach { assertTrue(it.name, HindiScan.hasHindi(it.label(true))) }
        assertEquals("1 week", ChartRange.WEEK.label(false))
    }

    @Test fun dailyHighsTakeEachDaysHighestNumberOldestFirst() {
        val points = listOf(
            DatedRating("a", "2026-11-02", "09:00", 3, "patient", "now"), DatedRating("b", "2026-11-02", "18:00", 7, "patient", "now"),
            DatedRating("c", "2026-10-20", "10:00", 5, "patient", "now"), DatedRating("d", "not a date", "10:00", 9, "patient", "now"),
        )
        assertEquals(listOf(LocalDate.of(2026, 10, 20) to 5, LocalDate.of(2026, 11, 2) to 7), SymptomDiaryRules.dailyHighs(points))
    }

    @Test fun chartSaysWhenItMixesReporters() {
        fun p(who: String) = DatedRating(who, "2026-11-02", "09:00", 5, who, "now")
        assertTrue(!SymptomDiaryRules.mixesReporters(listOf(p("patient"), p("assisted-patient"))))
        assertTrue(!SymptomDiaryRules.mixesReporters(emptyList()))
        assertTrue(SymptomDiaryRules.mixesReporters(listOf(p("patient"), p("caregiver"))))
        assertTrue(SymptomDiaryRules.mixesReporters(listOf(p("caregiver"), p("clinician"))))
    }

    @Test fun painHasGoneIsANewZeroNoteAtTheSameSpotsAndTheTrendEndsAtZero() {
        val old = SymptomNote("Pain", 7, "2026-11-01", "10:00", "now", "patient", "Fictional patient", painBodySites = listOf("head", "neck"), painBodyRatings = mapOf("head" to 7, "neck" to 3))
        val gone = SymptomDiaryRules.painGone(old, LocalDateTime.of(2026, 11, 3, 9, 30))
        assertEquals(0, gone.rating); assertEquals(mapOf("head" to 0, "neck" to 0), gone.painBodyRatings)
        assertEquals("2026-11-03", gone.observedDate); assertEquals("patient", gone.respondent)
        assertNull(SymptomDiaryRules.error(gone, LocalDateTime.of(2026, 11, 3, 12, 0)))
        val items = listOf(old, gone).mapIndexed { i, n -> CareItem(id = "n$i", personId = "p", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(n)) }
        assertEquals(listOf(7, 0), SymptomDiaryRules.painPartSeries(items, "p", "head").map { it.value })
        assertEquals(listOf(3, 0), SymptomDiaryRules.painPartSeries(items, "p", "neck").map { it.value })
    }

    @Test fun distressSeriesRunsOldestFirstWithNoAsZero() {
        fun entry(at: Long, check: DistressCheck) = DistressRules.change(null, check)!!.copy(personId = "p", created = at)
        val history = DistressRules.history(listOf(entry(300, DistressCheck(false)), entry(100, DistressCheck(true, 6)), entry(200, DistressCheck(true, 3))), "p")
        assertEquals(listOf(Triple(100L, 6, true), Triple(200L, 3, true), Triple(300L, 0, false)), DistressRules.series(history))
    }
}
