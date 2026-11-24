package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** Check-ins (clinical team, 24 Nov 2026): the last 7 days as a short list, then a month calendar with one dot per day. */
class CareCheckInCalendarTest {
    private fun day(d: LocalDate, mood: String? = null, notes: Int = 0) = CheckInDay(d, mood, notes, null, false, emptyList())

    @Test fun lastWeekIsTodayAndTheSixDaysBefore() {
        val today = LocalDate.of(2026, 11, 24)
        val days = listOf(day(today), day(today.minusDays(6)), day(today.minusDays(7)), day(today.plusDays(1)))
        assertEquals(listOf(today, today.minusDays(6)), CheckInDays.lastWeek(days, today).map { it.day })
    }

    @Test fun monthGridStartsOnMondayAndFillsWholeWeeks() {
        // 1 Nov 2026 is a Sunday: six empty cells first, and 30 days end on a Monday.
        val grid = CheckInDays.monthGrid(YearMonth.of(2026, 11))
        assertEquals(6, grid.size)
        grid.forEach { assertEquals(7, it.size) }
        assertEquals(List(6) { null }, grid.first().take(6))
        assertEquals(LocalDate.of(2026, 11, 1), grid.first()[6])
        assertEquals(LocalDate.of(2026, 11, 30), grid.last()[0])
        assertNull(grid.last()[1])
        assertEquals(30, grid.flatten().count { it != null })
    }

    @Test fun theDotFollowsTheMoodOrSaysNotesOnly() {
        assertEquals(CheckInMood.GOOD, CheckInDays.dotKind(day(LocalDate.of(2026, 11, 2), mood = CheckInMood.GOOD)))
        assertEquals(CheckInDays.NOTES_ONLY, CheckInDays.dotKind(day(LocalDate.of(2026, 11, 2), notes = 2)))
    }
}
