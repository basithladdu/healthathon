package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareCalendarVisitsTest {
    @Test fun calendarShowsVisitsAndDatedCareButNotDailyMedicines() {
        val medicine = CareItem(id = "m", personId = "p", kind = "medicine", title = "Morphine", due = 1)
        val visit = CareItem(id = "v", personId = "p", kind = "visit", title = "Clinic", due = 1)
        val test = CareItem(id = "t", personId = "p", kind = "task", title = "Blood test", due = 1,
            detail = CareEntryCodec.encode(CareEntry(type = "schedule", category = "Test")))
        assertFalse(calendarShows(medicine))
        assertTrue(calendarShows(visit))
        assertTrue(calendarShows(test))
        assertTrue(calendarShows(medicine, showMedicines = true))
    }
}
