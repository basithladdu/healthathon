package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CareReminderRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun epoch(text: String) = Instant.parse(text).toEpochMilli()
    private fun medicine(times: List<String> = listOf("08:00", "20:00")) = CareReminderPlan("medicine-1", "person-1", "medicine", 0, times, "", false)

    @Test fun nextSlotRetainsFutureTodayAndMovesElapsedSlotToTomorrow() {
        val next = CareReminderRules.next(medicine(), epoch("2026-10-19T04:30:00Z"), zone)
        assertEquals(listOf("2026-10-20", "2026-10-19"), next.map { it.localDate })
        assertEquals(listOf(epoch("2026-10-20T02:30:00Z"), epoch("2026-10-19T14:30:00Z")), next.map { it.at })
    }
    @Test fun dailyCompletionDoesNotInventPerDoseReports() {
        val next = CareReminderRules.next(medicine().copy(doneOn = "2026-10-19"), epoch("2026-10-19T01:00:00Z"), zone)
        assertTrue(next.all { it.localDate == "2026-10-20" })
    }
    @Test fun explicitCourseBoundsCancelAfterLastDayAndHoldUntilStart() {
        val plan = medicine().copy(starts = "2026-10-21", ends = "2026-10-21")
        assertTrue(CareReminderRules.next(plan, epoch("2026-10-22T00:00:00Z"), zone).isEmpty())
        assertTrue(CareReminderRules.next(plan, epoch("2026-10-19T00:00:00Z"), zone).all { it.localDate == "2026-10-21" })
    }
    @Test fun completedOrCancelledVisitNeverSchedules() {
        val plan = CareReminderPlan("visit", "person", "visit", epoch("2026-10-20T04:30:00Z"), emptyList(), "", true)
        assertTrue(CareReminderRules.next(plan, epoch("2026-10-19T00:00:00Z"), zone).isEmpty())
        assertTrue(CareReminderRules.next(plan.copy(finished = false, doneOn = "2026-10-19"), epoch("2026-10-19T00:00:00Z"), zone).isEmpty())
    }
    @Test fun changingZoneRecalculatesDailyWallTimeButKeepsVisitInstant() {
        val now = epoch("2026-10-19T00:00:00Z")
        assertNotEquals(CareReminderRules.next(medicine(listOf("20:00")), now, zone).single().at, CareReminderRules.next(medicine(listOf("20:00")), now, ZoneId.of("Europe/London")).single().at)
        val visit = CareReminderPlan("v", "p", "visit", now + 1000, emptyList(), "", false)
        assertEquals(CareReminderRules.next(visit, now, zone).single().at, CareReminderRules.next(visit, now, ZoneId.of("Europe/London")).single().at)
    }
    @Test fun dstGapAdvancesToRealClockTimeWithoutDuplicateOverlapDose() {
        val next = CareReminderRules.next(medicine(listOf("02:30")), epoch("2026-03-08T05:00:00Z"), ZoneId.of("America/New_York")).single()
        assertEquals(epoch("2026-03-08T07:30:00Z"), next.at)
    }
    @Test fun staleDuplicateFutureAndDeniedBroadcastsDoNotNotify() {
        val expected = CareReminderOccurrence("m:08:00", "m", 10000, "2026-10-19", "08:00")
        assertTrue(CareReminderRules.shouldDeliver(expected, 10000, 10001, 0, true))
        assertFalse(CareReminderRules.shouldDeliver(expected, 9999, 10001, 0, true))
        assertFalse(CareReminderRules.shouldDeliver(expected, 10000, 10001, 10000, true))
        assertFalse(CareReminderRules.shouldDeliver(expected, 10000, 9999, 0, true))
        assertFalse(CareReminderRules.shouldDeliver(expected, 10000, 10001, 0, false))
        assertFalse(CareReminderRules.shouldDeliver(expected, 10000, 10000 + 7 * 3600000, 0, true))
    }
    @Test fun unprintedTimesAreNeverInferredFromFrequencyWords() {
        assertTrue(CareReminderRules.printedTimes("twice daily after food").isEmpty())
        assertTrue(CareReminderRules.printedTimes("Times: 1-0-1").isEmpty())
        assertEquals(listOf("08:00", "20:00"), CareReminderRules.printedTimes("Dose: from prescription\nTimes: 20:00, 08:00, 08:00"))
    }

    @Test fun dateOnlyEntryNeverAlertsAtMidnightAndUsesChosenTimeOnThatDay() {
        val day = java.time.LocalDate.parse("2026-10-21").atStartOfDay(zone).toInstant().toEpochMilli()
        val plan = CareReminderPlan("visit", "person", "visit", day, emptyList(), "", false, dateOnly = true)
        val before = epoch("2026-10-19T00:00:00Z")
        assertTrue(CareReminderRules.next(plan, before, zone).isEmpty())
        val chosen = CareReminderRules.next(plan.copy(times = listOf("09:30")), before, zone).single()
        assertEquals(epoch("2026-10-21T04:00:00Z"), chosen.at)
        assertEquals("visit:09:30", chosen.key)
        assertTrue(CareReminderRules.next(plan.copy(times = listOf("09:30")), chosen.at, zone).isEmpty())
        assertTrue(CareReminderRules.next(plan.copy(times = listOf("09:30"), doneOn = "2026-10-20"), before, zone).isEmpty())
    }
    @Test fun onlyMedicinesAndTimedCalendarEntriesCarryAClockTime() {
        fun item(kind: String, detail: String = "") = CareItem(id = "i", personId = "p", kind = kind, title = "t", detail = detail, due = 1)
        assertFalse(CareReminderRules.dateOnly(item("medicine")))
        assertTrue(CareReminderRules.dateOnly(item("visit", "Clinic")))
        assertTrue(CareReminderRules.dateOnly(item("task", CareEntryCodec.encode(CareEntry("task", assignee = "Asha")))))
        assertTrue(CareReminderRules.dateOnly(item("task", CareEntryCodec.encode(CareEntry("schedule", category = "Test")))))
        assertFalse(CareReminderRules.dateOnly(item("visit", CareEntryCodec.encode(CareEntry("schedule", category = "Visit", timed = true)))))
    }
    @Test fun remindersListHidesPastDatesAndLabResults() {
        val today = java.time.LocalDate.parse("2026-10-21")
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        fun item(kind: String, due: Long, detail: String = "") = CareItem(id = "i", personId = "p", kind = kind, title = "t", detail = detail, due = due)
        assertTrue(CareReminderRules.listed(item("medicine", 0), today, zone))
        assertTrue(CareReminderRules.listed(item("visit", start), today, zone))
        assertFalse(CareReminderRules.listed(item("visit", start - 1), today, zone))
        assertFalse(CareReminderRules.listed(item("task", 0), today, zone))
        assertFalse(CareReminderRules.listed(item("task", start + 1, CareEntryCodec.encode(CareEntry("lab", value = "1", unit = "g"))), today, zone))
    }
}
