package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorPatientTrackingTest {
    private val now = DoctorPatientTracking.asOf
    private fun dose(scheduled: Long = now - 60_000, taken: Long? = null, logged: Long? = null, outcome: SampleDoseOutcome? = null) =
        SampleMedicineDose("sample-test-dose", "demo-asha", "Sample medicine", scheduled, taken, logged, outcome, if (outcome == null) null else SampleReportSource.PATIENT)

    @Test fun noReportNeverBecomesAssumedSkippedOrLate() {
        assertEquals("Not logged", dose().status(now))
        assertEquals("Upcoming", dose(scheduled = now + 60_000).status(now))
        assertEquals("Not logged", dose(scheduled = now).status(now))
    }

    @Test fun lateUsesReportedTakenTimeNotTimeTheEntryWasLogged() {
        val planned = now - 3_600_000
        assertEquals("Taken reported", dose(planned, planned, now, SampleDoseOutcome.TAKEN).status(now))
        assertEquals("Late reported", dose(planned, planned + 1, planned + 1, SampleDoseOutcome.TAKEN).status(now))
        assertEquals("Taken reported", dose(planned, planned - 60_000, now, SampleDoseOutcome.TAKEN).status(now))
    }

    @Test fun explicitSkippedReportIsSeparateFromNotLogged() {
        assertEquals("Skipped reported", dose(logged = now, outcome = SampleDoseOutcome.SKIPPED).status(now))
        assertEquals("Not logged", dose().status(now))
    }

    @Test fun incompleteOrUnattributedReportDoesNotInventTakenStatus() {
        assertEquals("Not logged", dose(outcome = SampleDoseOutcome.TAKEN).status(now))
        assertEquals("Not logged", dose(taken = now, outcome = SampleDoseOutcome.TAKEN).copy(source = null).status(now))
        assertEquals("Upcoming", dose(scheduled = now + 60_000, outcome = SampleDoseOutcome.TAKEN).copy(source = null).status(now))
    }

    @Test fun sampleFixturesArePatientScopedAndDoNotInventUnknownPatientRecords() {
        val known = DoctorDemoData.patients.map { it.id }
        val allDoses = known.flatMap { patientId -> DoctorPatientTracking.doses(patientId).also { rows -> assertTrue(rows.all { it.patientId == patientId }) } }
        val allChecks = known.flatMap { patientId -> DoctorPatientTracking.checkIns(patientId).also { rows -> assertTrue(rows.all { it.patientId == patientId }) } }
        assertTrue(allDoses.isNotEmpty())
        assertTrue(allChecks.isNotEmpty())
        assertEquals(allDoses.size, allDoses.map { it.id }.distinct().size)
        assertEquals(allChecks.size, allChecks.map { it.id }.distinct().size)
        assertTrue(DoctorPatientTracking.doses("unknown-patient").isEmpty())
        assertTrue(DoctorPatientTracking.checkIns("unknown-patient").isEmpty())
        assertTrue(allDoses.all { it.medicineLabel.isNotBlank() })
    }
}
