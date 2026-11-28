package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 25 Nov redesign: the week strip's days, the bottom bar's tabs and which routes show the bar. */
class SaharaAppKitTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 25)
    private fun at(day: LocalDate, hour: Int = 10) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun weekMarksShowSevenDaysWithTheLatestMoodAndSymptoms() {
        val items = listOf(
            CareItem(personId = "p", kind = "checkin", title = CheckInMood.HARD, created = at(today, 8)),
            CareItem(personId = "p", kind = "checkin", title = CheckInMood.GOOD, created = at(today, 18)),
            CareItem(personId = "p", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.PREFIX + "{}", created = at(today.minusDays(2))),
            CareItem(personId = "p", kind = "checkin", title = CheckInMood.OKAY, created = at(today.minusDays(6))),
            CareItem(personId = "p", kind = "checkin", title = CheckInMood.OKAY, created = at(today.minusDays(7))),
            CareItem(personId = "other", kind = "checkin", title = CheckInMood.HARD, created = at(today.minusDays(1))),
            CareItem(personId = "p", kind = "journal", title = CheckInMood.HARD, created = at(today.minusDays(1))))
        val marks = weekMarks(items, "p", today, zone)
        assertEquals(7, marks.size)
        assertEquals(today.minusDays(6), marks.first().day); assertEquals(today, marks.last().day)
        assertEquals(CheckInMood.GOOD, marks.last().mood)
        assertEquals(CheckInMood.OKAY, marks.first().mood)
        assertNull(marks[5].mood)
        assertTrue(marks[4].symptom); assertNull(marks[4].mood)
        assertFalse(marks.last().symptom)
    }

    @Test fun medicinesHasItsOwnTabAndCarePagesLightCare() {
        assertEquals(13, careNavRoute(13, clinician = false))
        listOf(2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 1).forEach { assertEquals("route $it", 2, careNavRoute(it, clinician = false)) }
        assertEquals(1, careNavRoute(1, clinician = true))
        assertEquals(0, careNavRoute(0, clinician = false)); assertEquals(4, careNavRoute(4, clinician = false))
    }

    @Test fun onlyRootTabsShowTheBottomBar() {
        listOf(0, 2, 4, 13).forEach { assertTrue("tab $it", careBottomBarRoute(it, clinician = false)) }
        listOf(1, 3, 5, 6, 7, 8, 9, 10, 11, 12).forEach { assertFalse("tab $it", careBottomBarRoute(it, clinician = false)) }
        assertTrue(careBottomBarRoute(1, clinician = true))
        // SaharaPage root screens draw their own insets; the clinician Today and Simple Mode are padded by the shell.
        assertTrue(careRouteOwnsInsets(0, clinician = false, simpleOn = false))
        assertFalse(careRouteOwnsInsets(0, clinician = true, simpleOn = false))
        assertFalse(careRouteOwnsInsets(0, clinician = false, simpleOn = true))
        assertFalse(careRouteOwnsInsets(9, clinician = false, simpleOn = false))
    }
}
