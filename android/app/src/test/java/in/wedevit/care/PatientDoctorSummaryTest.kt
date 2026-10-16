package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class PatientDoctorSummaryTest {
    @Test fun blankIdentityCannotProduceAnAssignedDoctorRow() {
        assertTrue(runCatching { LinkedDoctorSummary("", "Dr Fictional") }.isFailure)
        assertTrue(runCatching { LinkedDoctorSummary("fictional-id", " \n ") }.isFailure)
        assertTrue(runCatching { LinkedDoctorSummary(" \t ", "Dr Fictional") }.isFailure)
    }

    @Test fun unverifiedIsDefaultAndVerifiedCannotBeClaimedWithoutTrustedSource() {
        val doctor = LinkedDoctorSummary("fictional-id", "Dr Fictional")
        assertEquals(PatientDoctorVerification.NOT_VERIFIED, doctor.verification)
        assertEquals(listOf("NOT_VERIFIED", "PENDING"), PatientDoctorVerification.entries.map { it.name })
        assertNull(doctor.speciality)
        assertNull(doctor.clinic)
    }
}
