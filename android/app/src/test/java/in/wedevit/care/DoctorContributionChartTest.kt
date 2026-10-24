package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import androidx.compose.ui.graphics.toArgb

class DoctorContributionChartTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private fun item(id: String, time: Long, title: String = "A good day") = CareItem(id, "fixture", "checkin", title, created = time)

    @Test fun latestPerDayUsesDeterministicTieButRetainsActualCount() {
        val now = at("2026-11-02T17:00:00Z")
        val result = doctorContributionDays(listOf(item("z", now - 1), item("a", now), item("b", now),item("previous-month",at("2026-10-31T12:00:00Z"))), zone, now)
        assertEquals(3, result.actualCount)
        assertEquals("a", result.days.single { it.date == result.today }.report?.id)
        assertEquals(30, result.days.size)
        assertEquals(LocalDate.of(2026,11,1),result.start)
        assertEquals(6,result.weekCount)
        assertTrue(result.days.all {it.date.monthValue==11&&it.date.year==2026})
    }
    @Test fun currentMonthHandlesDstJanuaryRolloverLeapDaysAndFourToSixWeeks() {
        val now = at("2026-11-01T06:30:00Z")
        val result = doctorContributionDays(listOf(item("before", at("2026-11-01T05:30:00Z")), item("after", now), item("future", now + 1)), zone, now)
        assertEquals(LocalDate.of(2026, 11, 1), result.start)
        assertEquals(2, result.actualCount)
        assertEquals("after", result.days.single { it.date == LocalDate.of(2026, 11, 1) }.report?.id)
        val monday = doctorContributionDays(emptyList(), zone, at("2026-11-02T17:00:00Z"))
        assertEquals(28, monday.days.count { it.future })
        assertTrue(monday.days.filter { it.future }.all { it.report == null })
        val january=doctorContributionDays(listOf(item("december",at("2027-01-01T03:00:00Z")),item("january",at("2027-01-01T12:00:00Z"))),zone,at("2027-01-01T12:00:00Z"))
        assertEquals(LocalDate.of(2027,1,1),january.start);assertEquals(1,january.actualCount);assertEquals(31,january.days.size)
        val leap=doctorContributionDays(emptyList(),zone,at("2028-02-29T15:00:00Z"))
        assertEquals(29,leap.days.size);assertEquals(LocalDate.of(2028,2,29),leap.days.last().date);assertEquals(5,leap.weekCount)
        assertEquals(4,doctorContributionDays(emptyList(),zone,at("2027-02-15T15:00:00Z")).weekCount)
    }
    @Test fun unknownLatestStaysUnknownAndMixedPeopleNeverMerge() {
        val now = at("2027-01-01T12:00:00Z")
        val result = doctorContributionDays(listOf(item("old", now - 1), item("new", now, "Changed")), zone, now)
        assertNull(doctorContributionColor(result.days.single { it.date == result.today }.report!!.title))
        assertEquals(0, doctorContributionDays(listOf(item("one", now), item("two", now).copy(personId = "other")), zone, now).actualCount)
        assertEquals(0xFFE88982.toInt(), doctorContributionColor("A hard day")!!.toArgb())
        assertEquals(0xFFF2B273.toInt(), doctorContributionColor("An okay day")!!.toArgb())
        assertEquals(0xFF8FC4A2.toInt(), doctorContributionColor("A good day")!!.toArgb())
    }
}
