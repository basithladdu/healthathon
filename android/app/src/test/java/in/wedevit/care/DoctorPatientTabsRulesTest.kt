package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DoctorPatientTabsRulesTest {
    private val today = LocalDate.of(2026, 11, 4)
    private fun r(date: String, value: Int, time: String = "09:00") = DatedRating("id-$date-$time", date, time, value, "patient", "now")

    @Test fun twoPanesFromTheExpandedWidth() {
        assertFalse(doctorTwoPane(839f)); assertTrue(doctorTwoPane(840f)); assertTrue(doctorTwoPane(1280f))
    }

    @Test fun hubAndOldOverviewOpenSymptomsAndDistress() {
        assertEquals("Progress", doctorContentSection("Hub"))
        assertEquals("Progress", doctorContentSection("Summary"))
        assertEquals("Labs", doctorContentSection("Labs"))
        assertEquals("Medicines", doctorContentSection("Medicines"))
    }

    @Test fun careTodayHasItsOwnRouteAndTitle() {
        assertEquals("Today", doctorContentSection("Today"))
        assertEquals("Care today", doctorMoreSections.single { it.section == "Today" }.en)
        assertTrue(doctorPatientTabs.none { it.section == "Today" })
    }

    @Test fun unknownRangeFallsBackToAll() {
        assertEquals(DoctorTrendRange.ALL, DoctorTrendRange.of("nonsense"))
        assertEquals(DoctorTrendRange.MONTH_1, DoctorTrendRange.of("MONTH_1"))
    }

    @Test fun dailyKeepsTheHighestNumberEachDayOldestFirstAndSkipsBadDates() {
        val days = DoctorTrendRules.daily(listOf(r("2026-11-03", 4, "08:00"), r("2026-11-01", 2), r("2026-11-03", 7, "20:00"), r("not a date", 9)))
        assertEquals(listOf(LocalDate.of(2026, 11, 1) to 2, LocalDate.of(2026, 11, 3) to 7), days)
    }

    @Test fun rangesCountBackFromTodayAndAllStartsAtTheFirstEntry() {
        val days = listOf(LocalDate.of(2026, 6, 1) to 3, LocalDate.of(2026, 10, 1) to 5, LocalDate.of(2026, 10, 25) to 6, today to 8, today.plusDays(2) to 9)
        assertEquals(listOf(6, 8), DoctorTrendRules.within(days, DoctorTrendRange.WEEKS_2, today).map { it.second })
        assertEquals(listOf(6, 8), DoctorTrendRules.within(days, DoctorTrendRange.MONTH_1, today).map { it.second })
        assertEquals(listOf(5, 6, 8), DoctorTrendRules.within(days, DoctorTrendRange.MONTHS_3, today).map { it.second })
        assertEquals(listOf(3, 5, 6, 8), DoctorTrendRules.within(days, DoctorTrendRange.ALL, today).map { it.second })
        assertEquals(today.minusDays(13), DoctorTrendRules.start(DoctorTrendRange.WEEKS_2, LocalDate.of(2026, 6, 1), today))
        assertEquals(LocalDate.of(2026, 6, 1), DoctorTrendRules.start(DoctorTrendRange.ALL, LocalDate.of(2026, 6, 1), today))
        assertEquals(today, DoctorTrendRules.start(DoctorTrendRange.ALL, null, today))
    }

    @Test fun axisLabelsPutTodayFirstThenWeeksOrMonths() {
        val short = DoctorTrendRules.xLabels(today.minusDays(13), today, false)
        assertTrue(short.first().strong); assertEquals(today.toEpochDay().toDouble(), short.first().x, 0.0)
        assertEquals(listOf(today.minusDays(13), today.minusDays(6)).map { it.toEpochDay().toDouble() }, short.drop(1).map { it.x })
        val long = DoctorTrendRules.xLabels(LocalDate.of(2026, 6, 15), today, false)
        assertEquals((7..11).map { LocalDate.of(2026, it, 1).toEpochDay().toDouble() }, long.drop(1).map { it.x })
        assertEquals(1, DoctorTrendRules.xLabels(today, today, false).size)
    }

    @Test fun mostSevereSymptomComesFirstThenNewest() {
        val pain = DoctorSymptomRow("symptom", "Pain", listOf(r("2026-11-01", 9), r("2026-11-03", 4)))
        val nausea = DoctorSymptomRow("symptom", "Nausea", listOf(r("2026-10-20", 7)))
        val breath = DoctorSymptomRow("symptom", "Breathlessness", listOf(r("2026-11-02", 7)))
        assertEquals(listOf("Breathlessness", "Nausea", "Pain"), DoctorTrendRules.bySeverity(listOf(pain, nausea, breath)).map { it.key })
        assertEquals(9, DoctorTrendRules.highest(pain.series, DoctorTrendRange.ALL, today))
        assertEquals(9, DoctorTrendRules.highest(pain.series, DoctorTrendRange.WEEKS_2, today))
        assertNull(DoctorTrendRules.highest(nausea.series, DoctorTrendRange.WEEKS_2, today))
    }

    @Test fun timestampsBecomeOneValuePerDay() {
        val zone = ZoneId.of("UTC")
        val day = today.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(listOf(today to 6.5), DoctorTrendRules.dailyAt(listOf(day + 1000 to 6.5, day + 5000 to 2.0, 0L to 9.0), zone))
    }

    @Test fun printedLabRangeIsReadNeverGuessed() {
        assertEquals(13.0 to 17.0, doctorLabRange(CareEntry(type = "lab", rangeText = "13.0–17.0 g/dL")))
        assertEquals(null to 5.0, doctorLabRange(CareEntry(type = "lab", rangeHigh = "5")))
        assertEquals(null to null, doctorLabRange(CareEntry(type = "lab", rangeText = "see report")))
        assertEquals("12.5", doctorNumber(12.50)); assertEquals("3", doctorNumber(3.0))
    }
}
