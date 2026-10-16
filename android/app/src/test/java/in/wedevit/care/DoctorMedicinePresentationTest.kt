package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DoctorMedicinePresentationTest {
    private val now=Instant.parse("2026-10-15T12:00:00Z").toEpochMilli()
    private val zone=ZoneId.of("UTC")
    private val dose=SampleMedicineDose("dose","patient","Sample medicine",now-7200000,now-3600000,now-1800000,SampleDoseOutcome.TAKEN,SampleReportSource.FAMILY)
    @Test fun separatesPlannedActualAndEntryTimes() {
        val view=doctorMedicinePresentation(dose,now,zone)
        assertEquals("15 Oct 2026 · 10:00 AM",view.planned)
        assertEquals("15 Oct 2026 · 11:00 AM",view.taken)
        assertEquals("15 Oct 2026 · 11:30 AM",view.logged)
        assertEquals("Late reported",view.status);assertEquals("Family",view.reporter)
    }
    @Test fun lateEntryDoesNotMakeOnTimeReportedIntakeLate() {
        assertEquals("Taken reported",doctorMedicinePresentation(dose.copy(reportedTakenAt=dose.scheduledAt),now,zone).status)
    }
    @Test fun skippedNeverShowsTakenTimestamp() {
        val view=doctorMedicinePresentation(dose.copy(outcome=SampleDoseOutcome.SKIPPED),now,zone)
        assertEquals("Not reported",view.taken);assertEquals("Skipped reported",view.status);assertNotEquals("Not logged",view.logged)
    }
    @Test fun laterEntriesAndActualTimesCannotLeakIntoSnapshot() {
        val futureLog=doctorMedicinePresentation(dose.copy(loggedAt=now+1),now,zone)
        assertEquals("Not reported",futureLog.taken);assertEquals("Not logged",futureLog.logged);assertEquals("Not recorded",futureLog.reporter)
        val futureTaken=doctorMedicinePresentation(dose.copy(reportedTakenAt=now+1),now,zone)
        assertEquals("Not reported",futureTaken.taken);assertEquals("Not logged",futureTaken.logged)
    }
    @Test fun unreportedFutureIsUpcomingAndDueIsNotLogged() {
        assertEquals("Upcoming",doctorMedicinePresentation(dose.copy(scheduledAt=now+1,loggedAt=null),now,zone).status)
        assertEquals("Not logged",doctorMedicinePresentation(dose.copy(loggedAt=null),now,zone).status)
    }
    @Test fun timezoneRetainsFullLocalDateNotJustClock() {
        assertEquals("15 Oct 2026 · 3:30 PM",doctorMedicinePresentation(dose,now,ZoneId.of("Asia/Kolkata")).planned)
    }
    @Test fun takenWithUnknownTimeRetainsReportButIncompleteOutcomeDoesNot() {
        val unknownTime=doctorMedicinePresentation(dose.copy(reportedTakenAt=null),now,zone)
        assertEquals("Taken reported · time not recorded",unknownTime.status)
        assertEquals("Time not recorded",unknownTime.taken);assertNotEquals("Not logged",unknownTime.logged)
        val incomplete=doctorMedicinePresentation(dose.copy(outcome=null),now,zone)
        assertEquals("Not logged",incomplete.logged);assertEquals("Not recorded",incomplete.reporter)
    }
}
