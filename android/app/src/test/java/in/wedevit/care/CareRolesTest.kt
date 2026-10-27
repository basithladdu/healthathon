package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test

class CareRolesTest {
    @Test fun ownerIsPatientAndInvitedMemberIsFamilyNeverDoctor() {
        assertEquals("Patient", CareRoles.forOwner(true))
        assertEquals("Family", CareRoles.forOwner(false))
    }
    @Test fun staleRoleAndClinicianPreferencesFromOlderInstallsAreDropped() {
        assertEquals(listOf("viewRole", "clinician"), CareRoles.staleKeys(mapOf("viewRole" to "Doctor", "clinician" to true, "person" to "p1")))
        assertEquals(listOf("clinician"), CareRoles.staleKeys(mapOf("clinician" to true)))
        assertEquals(emptyList<String>(), CareRoles.staleKeys(mapOf("person" to "p1")))
    }
}
