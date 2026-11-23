package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Caregiving changes (SVK, 23 Nov): the inactivity check-in, pausing for 30/60/90 days, and stopping without deleting anything. */
class CaregivingStatusRulesTest {
    private val today = LocalDate.of(2026, 11, 23)
    private val active = CaregivingStatus()

    @Test fun checkInComesAfterThreeQuietDays() {
        assertFalse(CaregivingStatusRules.checkInDue(active, today.minusDays(1), today.minusDays(2), today))
        assertTrue(CaregivingStatusRules.checkInDue(active, today.minusDays(3), today.minusDays(1), today))
        // Opening the app every day but adding nothing (medicine tracking stopped) counts too.
        assertTrue(CaregivingStatusRules.checkInDue(active, today.minusDays(1), today.minusDays(4), today))
        assertEquals(5L, CaregivingStatusRules.inactiveDays(today.minusDays(5), null, today))
    }

    @Test fun noCheckInWithoutAnyHistory() {
        assertNull(CaregivingStatusRules.inactiveDays(null, null, today))
        assertFalse(CaregivingStatusRules.checkInDue(active, null, null, today))
        // A future date (clock change) never counts as inactive.
        assertFalse(CaregivingStatusRules.checkInDue(active, today.plusDays(2), null, today))
    }

    @Test fun laterAndSteadyQuietTheCheckIn() {
        val later = CaregivingStatusRules.later(active, today)
        assertFalse(CaregivingStatusRules.checkInDue(later, today.minusDays(10), null, today.plusDays(1)))
        assertTrue(CaregivingStatusRules.checkInDue(later, today.minusDays(10), null, today.plusDays(2)))
        val steady = CaregivingStatusRules.steady(active, today)
        assertFalse(CaregivingStatusRules.checkInDue(steady, today.minusDays(10), null, today.plusDays(6)))
        assertTrue(CaregivingStatusRules.checkInDue(steady, today.minusDays(10), null, today.plusDays(7)))
    }

    @Test fun pauseStopsRemindersThenEndsByItself() {
        CaregivingStatusRules.PAUSE_CHOICES.forEach { days ->
            val paused = CaregivingStatusRules.pause(active, today, days)
            assertEquals(CaregivingState.PAUSED, paused.state)
            assertTrue(CaregivingStatusRules.remindersStopped(paused, today))
            assertTrue(CaregivingStatusRules.remindersStopped(paused, today.plusDays(days - 1L)))
            assertFalse(CaregivingStatusRules.remindersStopped(paused, today.plusDays(days.toLong())))
            assertEquals(CaregivingState.ACTIVE, CaregivingStatusRules.effective(paused, today.plusDays(days.toLong())).state)
            // No check-in while paused.
            assertFalse(CaregivingStatusRules.checkInDue(paused, today.minusDays(9), null, today.plusDays(1)))
            assertFalse(CaregivingStatusRules.archived(paused))
        }
        assertThrows(IllegalArgumentException::class.java) { CaregivingStatusRules.pause(active, today, 45) }
    }

    @Test fun stoppingIsImmediateArchivesAndNeverExpires() {
        CaregivingStatusRules.reasons.plus("").forEach { reason ->
            val ended = CaregivingStatusRules.stop(active, today, reason)
            assertEquals(CaregivingState.ENDED, ended.state)
            assertEquals(reason, ended.reason)
            assertTrue(CaregivingStatusRules.remindersStopped(ended, today))
            assertTrue(CaregivingStatusRules.remindersStopped(ended, today.plusYears(1)))
            assertTrue(CaregivingStatusRules.archived(ended))
            assertFalse(CaregivingStatusRules.checkInDue(ended, today.minusDays(30), null, today))
        }
        assertThrows(IllegalArgumentException::class.java) { CaregivingStatusRules.stop(active, today, "deceased") }
    }

    @Test fun resumeTurnsRemindersBackOn() {
        val ended = CaregivingStatusRules.stop(CaregivingStatusRules.pause(active, today, 30), today, CaregivingStatusRules.SOMEONE_ELSE)
        val back = CaregivingStatusRules.resume(ended, today)
        assertEquals(CaregivingState.ACTIVE, back.state)
        assertFalse(CaregivingStatusRules.remindersStopped(back, today))
        assertFalse(CaregivingStatusRules.checkInDue(back, today.minusDays(9), null, today.plusDays(3)))
    }

    @Test fun savedStatusRoundTripsAndBadDataIsActive() {
        val paused = CaregivingStatusRules.pause(active, today, 60).copy(lastOpen = today)
        assertEquals(paused, CaregivingStatusRules.decode(CaregivingStatusRules.encode(paused)))
        val ended = CaregivingStatusRules.stop(active, today, CaregivingStatusRules.PASSED_AWAY)
        assertEquals(ended, CaregivingStatusRules.decode(CaregivingStatusRules.encode(ended)))
        assertEquals(CaregivingStatus(), CaregivingStatusRules.decode(null))
        assertEquals(CaregivingStatus(), CaregivingStatusRules.decode("not json"))
        assertEquals(CaregivingState.ACTIVE, CaregivingStatusRules.decode("""{"state":"PAUSED"}""").state)
    }

    @Test fun caregiverKindUsesTheExistingContactRoles() {
        assertEquals("family", CareCaregiverKind.contactRole(CareCaregiverKind.FAMILY))
        assertEquals("caregiver", CareCaregiverKind.contactRole(CareCaregiverKind.PAID_CARER))
        assertEquals("family", CareCaregiverKind.contactRole("something else"))
        CareCaregiverKind.kinds.forEach { assertTrue(CareCaregiverKind.contactRole(it) in CareContactRules.roles) }
        assertEquals("Daughter", CareCaregiverKind.relationship(CareCaregiverKind.FAMILY, "Daughter"))
        assertEquals("Family", CareCaregiverKind.relationship(CareCaregiverKind.FAMILY, ""))
        assertEquals("Nurse / paid carer", CareCaregiverKind.relationship(CareCaregiverKind.PAID_CARER, "Son"))
    }

    @Test fun manyDosesFoldToTheNextTimeStillDue() {
        assertEquals(1, TodayDoseCompact.nextOpen(listOf(0, 3, 2)))
        assertEquals(0, TodayDoseCompact.nextOpen(listOf(4, 0)))
        assertEquals(-1, TodayDoseCompact.nextOpen(listOf(0, 0)))
    }

    @Test fun youMatterShowsAtMostOnceAWeek() {
        assertTrue(CaregiverWellbeingRules.youMatterDue(null, today))
        assertFalse(CaregiverWellbeingRules.youMatterDue(today, today))
        assertFalse(CaregiverWellbeingRules.youMatterDue(today.minusDays(6), today))
        assertTrue(CaregiverWellbeingRules.youMatterDue(today.minusDays(7), today))
    }
}
