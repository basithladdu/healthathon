package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorCareTeamContextTest {
    @Test fun capturedContextCannotActAfterAccountPersonOrAccessChanges() {
        assertTrue(careContextMatches("account-a", "account-a", "person-a", "person-a", true))
        assertFalse(careContextMatches("account-a", "account-b", "person-a", "person-a", true))
        assertFalse(careContextMatches("account-a", "account-a", "person-a", "person-b", true))
        assertFalse(careContextMatches("account-a", "account-a", "person-a", "person-a", false))
        assertFalse(careContextMatches("account-a", "account-a", "", "", true))
    }

    @Test fun familyCaregiverAndClinicianAreExplicitRolesNotInferredFromProfiles() {
        CareContactRules.validate("Synthetic family member", "Sibling", "family", "")
        CareContactRules.validate("Synthetic caregiver", "Carer", "caregiver", "")
        CareContactRules.validate("Synthetic clinician", "Care professional", "clinician", "")
        assertThrows(IllegalArgumentException::class.java) { CareContactRules.validate("", "Sibling", "family", "") }
        assertThrows(IllegalArgumentException::class.java) { CareContactRules.validate("Synthetic person", "Sibling", "unclassified-account", "") }
    }
}
