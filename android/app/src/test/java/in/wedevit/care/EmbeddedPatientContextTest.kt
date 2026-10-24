package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

/** Fail closed before rendering any record-backed content; no database or account is used. */
class EmbeddedPatientContextTest {
    private fun matches(
        account: String = "account-a", session: String = "account-a", model: String = "account-a",
        person: String = "person-a", selected: String = "person-a", draft: String = "person-a",
        available: Set<String> = setOf("person-a", "person-b"), records: List<String> = listOf("person-a")
    ) = embeddedPatientContextMatches(account, session, model, person, selected, draft, available, records)

    @Test fun matchingAccountAndPersonCanRenderTheirOwnRecords() {
        assertTrue(matches())
        assertTrue(matches(records = emptyList()))
    }

    @Test fun accountChangeOrMissingSessionCannotExposePreviousPerson() {
        assertFalse(matches(session = "account-b"))
        assertFalse(matches(model = "account-b"))
        assertFalse(matches(account = ""))
        assertFalse(matches(session = ""))
    }

    @Test fun missingOrRevokedPersonNeverFallsBackToAnotherPerson() {
        assertFalse(matches(available = setOf("person-b")))
        assertFalse(matches(available = emptySet()))
        assertFalse(matches(person = ""))
        assertFalse(matches(selected = "person-b"))
    }

    @Test fun staleDraftOrMixedRecordEmissionIsRejectedDuringSwitching() {
        assertFalse(matches(draft = "person-b"))
        assertFalse(matches(records = listOf("person-a", "person-b")))
        assertFalse(matches(records = listOf("person-b")))
    }

    @Test fun deviceLocalCareRequiresExplicitOptInAndNoAccountIdentity() {
        assertFalse(embeddedPatientContextMatches("", "", "", "person-a", "person-a", "person-a", setOf("person-a"), emptyList()))
        assertTrue(embeddedPatientContextMatches("", "", "", "person-a", "person-a", "person-a", setOf("person-a"), emptyList(), allowDeviceLocal = true))
        assertFalse(embeddedPatientContextMatches("", "account-a", "", "person-a", "person-a", "person-a", setOf("person-a"), emptyList(), allowDeviceLocal = true))
    }
}
