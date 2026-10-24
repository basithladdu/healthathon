package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DoctorPatientDayRulesTest {
    private val now = Instant.parse("2026-10-23T06:30:00Z").toEpochMilli()
    private fun entry(id: String, kind: String = "checkin", title: String = "A good day", created: Long = now, person: String = "p", detail: String = "", due: Long = 0, done: String = "") =
        CareItem(id, person, kind, title, detail, due, done, created)

    @Test fun allDayVisitsTodayRemainVisibleAfterMidnightWhileTimedPastVisitsDoNot() {
        val zone = ZoneId.of("Asia/Kolkata")
        val midnight = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val allDay = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", timed = false))
        val timed = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", timed = true))
        val rows = listOf(entry("plain-today", kind = "visit", due = midnight),
            entry("structured-today", kind = "visit", due = midnight, detail = allDay),
            entry("timed-past", kind = "visit", due = now - 1, detail = timed),
            entry("yesterday", kind = "visit", due = midnight - 86_400_000, detail = allDay))
        assertTrue(isDoctorStructuredVisit(rows[1]))
        assertEquals(setOf("plain-today", "structured-today"), doctorUpcomingVisits(rows, "p", now, zone).map { it.id }.toSet())
    }

    @Test fun checkInHistoryKeepsEveryActualTimestampAndOnlyTheSelectedPerson() {
        val rows = listOf(entry("older", created = now - 86_400_000), entry("newest"), entry("same-time"),
            entry("other", person = "other"), entry("future", created = now + 1), entry("invalid", created = 0), entry("journal", kind = "journal"))
        assertEquals(listOf("newest", "same-time", "older"), doctorCheckIns(rows, "p", now).map { it.id })
        assertEquals(3, doctorCheckIns(rows, "p", now).size)
        assertTrue(doctorCheckIns(rows, "missing", now).isEmpty())
    }

    @Test fun categoricalDaysAreNotClinicalScoresAndUnknownReportsRemainText() {
        assertEquals(DoctorReportedMood.GOOD, doctorMood("A good day"))
        assertEquals(DoctorReportedMood.OKAY, doctorMood("An okay day"))
        assertEquals(DoctorReportedMood.HARD, doctorMood("A hard day"))
        assertNull(doctorMood("Symptoms changed"))
        assertNull(doctorMood("a good day"))
        assertEquals("Symptoms changed", doctorCheckIns(listOf(entry("unknown", title = "Symptoms changed")), "p", now).single().title)
    }

    @Test fun medicineSummaryCountsOnlyPlainDailyCheckoffsNotIntakeTimes() {
        val day = LocalDate.of(2026, 10, 23)
        val rows = listOf(entry("marked", kind = "medicine", done = day.toString()), entry("yesterday", kind = "medicine", done = day.minusDays(1).toString()),
            entry("unmarked", kind = "medicine"), entry("other", kind = "medicine", person = "other", done = day.toString()), entry("task", kind = "task", done = day.toString()))
        val result = doctorMedicineCounts(rows, "p", day)
        assertEquals(3, result.total)
        assertEquals(1, result.markedToday)
        assertEquals(2, result.notMarked)
        assertEquals(0, doctorMedicineCounts(emptyList(), "p", day).total)
    }

    @Test fun upcomingVisitsPreservePlainOlderEntriesAndExcludeOtherPeopleAndFinishedVisits() {
        fun structured(status: String = "open", category: String = "Visit") = CareEntryCodec.encode(CareEntry("schedule", category = category, timed = true, status = status))
        val rows = listOf(entry("plain", kind = "visit", due = now + 30), entry("structured", kind = "visit", due = now + 10, detail = structured()),
            entry("unknown-date", kind = "visit"), entry("past", kind = "visit", due = now - 1, detail = structured()), entry("other", kind = "visit", due = now + 1, person = "other"),
            entry("cancelled", kind = "visit", due = now + 1, detail = structured("cancelled")), entry("completed", kind = "visit", due = now + 1, detail = structured("completed")))
        assertEquals(listOf("structured", "plain", "unknown-date"), doctorUpcomingVisits(rows, "p", now).map { it.id })
        val testNotVisit = entry("dated-test", kind = "visit", due = now + 1, detail = structured(category = "Test"))
        assertFalse(isDoctorStructuredVisit(testNotVisit))
        assertTrue(doctorUpcomingVisits(listOf(testNotVisit), "p", now).isEmpty())
    }
}
