package `in`.wedevit.care

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DoctorProgressDataTest {
    private val utc = ZoneId.of("UTC")
    private val patient = "demo-asha"
    private val at = DoctorPatientTracking.asOf
    @Test fun lateMeansTakenAfterPlanNotLoggedAfterPlan() {
        val onTime = SampleMedicineDose("dose", patient, "Sample", 1000, 1000, 5000, SampleDoseOutcome.TAKEN, SampleReportSource.PATIENT)
        assertEquals("Taken reported", DoctorProgressData.historicalDose(onTime, 6000).status)
        assertEquals("Late reported", DoctorProgressData.historicalDose(onTime.copy(reportedTakenAt = 2000), 6000).status)
    }
    @Test fun historicalCutoffHidesLaterLogsAndFutureTakenClaims() {
        val dose = SampleMedicineDose("dose", patient, "Sample", 1000, 2000, 5000, SampleDoseOutcome.TAKEN, SampleReportSource.FAMILY)
        val beforeLog = DoctorProgressData.historicalDose(dose, 3000)
        assertEquals("Not logged", beforeLog.status); assertNull(beforeLog.reportedTakenAt); assertNull(beforeLog.loggedAt); assertNull(beforeLog.source)
        val impossibleFutureIntake = DoctorProgressData.historicalDose(dose.copy(loggedAt = 1500), 1700)
        assertEquals("Not logged", impossibleFutureIntake.status); assertNull(impossibleFutureIntake.reportedTakenAt); assertNull(impossibleFutureIntake.loggedAt)
        assertEquals("Upcoming", DoctorProgressData.historicalDose(dose.copy(scheduledAt = 9000), 3000).status)
    }
    @Test fun onlyThisPatientAndSameLocalDateAreJoined() {
        val report = SamplePatientCheckIn("custom", patient, at, "Exact update", SampleReportSource.PATIENT)
        val same = SampleMedicineDose("same", patient, "Same day", at - 1000)
        val otherPatient = same.copy(id = "foreign", patientId = "demo-ravi")
        val otherDay = same.copy(id = "yesterday", scheduledAt = at - 86_400_000)
        val reports = DoctorProgressData.build(patient, listOf(report, report.copy(id = "foreign", patientId = "demo-ravi")), listOf(same, otherPatient, otherDay), at, utc)
        assertEquals(1, reports.size); assertEquals(listOf("same"), reports.single().medicineActivity.map { it.id })
        assertTrue(DoctorProgressData.build("unknown", listOf(report), listOf(same), at, utc).isEmpty())
    }
    @Test fun localDateUsesInjectedZoneRatherThanUtcDay() {
        val reportAt = Instant.parse("2026-10-15T00:30:00Z").toEpochMilli()
        val doseAt = Instant.parse("2026-10-14T23:45:00Z").toEpochMilli()
        val report = SamplePatientCheckIn("custom", patient, reportAt, "Exact update", SampleReportSource.FAMILY)
        val dose = SampleMedicineDose("same-local-day", patient, "Sample", doseAt)
        assertTrue(DoctorProgressData.build(patient, listOf(report), listOf(dose), reportAt, utc).single().medicineActivity.isEmpty())
        assertEquals(1, DoctorProgressData.build(patient, listOf(report), listOf(dose), reportAt, ZoneId.of("Asia/Kolkata")).single().medicineActivity.size)
    }
    @Test fun fixtureDiscussionLinkIsExplicitAndPatientSpecific() {
        val records = DoctorProgressData.reports(patient, at + 1000, utc)
        val latest = records.first()
        assertTrue(latest.isLatest); assertEquals("$patient-prior", latest.linkedDiscussion?.id)
        assertEquals(DoctorWorkflowData.sources(patient).first().text, latest.linkedDiscussion?.text)
        assertTrue(records.drop(1).all { it.linkedDiscussion == null })
        assertNull(latest.symptoms); assertNull(latest.clinicalAssessment)
        assertEquals(listOf("Would like time to ask questions at the next visit."), records.single { it.id == "$patient-check-1" }.questions)
        assertEquals(listOf("Family is arranging practical help for the next appointment."), records.single { it.id == "$patient-check-2" }.familySupport)
    }
    @Test fun futureReportsAreNotVisibleAndHistoricalDayHasNoUnrelatedDoses() {
        val records = DoctorProgressData.reports(patient, at - 1, utc)
        assertFalse(records.any { it.at > at - 1 })
        val family = records.first(); assertEquals("$patient-check-2", family.id)
        val futureDose = family.medicineActivity.last(); assertEquals("Upcoming", futureDose.status); assertNull(futureDose.reportedTakenAt); assertNull(futureDose.loggedAt)
        assertTrue(records.last().medicineActivity.isEmpty())
    }
}
