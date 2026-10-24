package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorPatientNavigationTest {
    private val shared = DoctorPatientSelection("account-a", DoctorPatientSource.SHARED, "person-a")

    @Test fun stateKeysSeparateAccountsAndRecordSourcesEvenWhenIdsMatch() {
        val keys = DoctorPatientSource.values().flatMap { source ->
            listOf("account-a", "account-b").map { DoctorPatientSelection(it, source, "same-id").stateKey }
        }
        assertEquals(6, keys.toSet().size)
    }

    @Test fun onlyTheExplicitPermittedSharedPersonCanBecomeReady() {
        assertTrue(doctorSharedPatientReady(shared, "account-a", setOf("person-a"), "person-a", "person-a"))
        assertFalse(doctorSharedPatientReady(null, "account-a", setOf("person-a"), "person-a", "person-a"))
        assertFalse(doctorSharedPatientReady(shared, "account-b", setOf("person-a"), "person-a", "person-a"))
        assertFalse(doctorSharedPatientReady(shared.copy(accountId = ""), "", setOf("person-a"), "person-a", "person-a"))
        assertFalse(doctorSharedPatientReady(shared, "account-a", setOf("person-b"), "person-a", "person-a"))
        assertFalse(doctorSharedPatientReady(shared, "account-a", setOf("person-a"), "person-b", "person-a"))
        assertFalse(doctorSharedPatientReady(shared, "account-a", setOf("person-a"), "person-a", "person-b"))
    }

    @Test fun syntheticAndDeviceIdsNeverPassAsSharedPatientRecords() {
        listOf(DoctorPatientSource.TEST, DoctorPatientSource.DEVICE).forEach { source ->
            assertFalse(doctorSharedPatientReady(shared.copy(source = source), "account-a", setOf("person-a"), "person-a", "person-a"))
        }
    }
}
