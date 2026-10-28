package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CareCalendarStartTest {
    private val today = LocalDate.of(2026, 10, 28)

    @Test fun visitCardOpensOnTheVisitDay() {
        val visit = careEpoch(LocalDate.of(2026, 11, 3)) + 10 * 60 * 60 * 1000L
        assertEquals(LocalDate.of(2026, 11, 3), calendarStartDay(visit, today))
    }

    @Test fun otherEntriesAndUndatedVisitsOpenOnToday() {
        assertEquals(today, calendarStartDay(0L, today))
    }
}
