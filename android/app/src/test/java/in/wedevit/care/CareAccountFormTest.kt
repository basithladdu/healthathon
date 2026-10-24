package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareAccountFormTest {
    @Test fun emailDeliveryHoldDoesNotBlockExistingAccountSignIn() {
        assertTrue(careAccountCanSubmit(false, "person@example.test", "existing", false))
        assertFalse(careAccountCanSubmit(true, "person@example.test", "new-password", false))
    }

    @Test fun newAccountsKeepTheRealMinimumPasswordRule() {
        assertFalse(careAccountCanSubmit(true, "person@example.test", "123456789", true))
        assertTrue(careAccountCanSubmit(true, "person@example.test", "1234567890", true))
        assertTrue(careAccountCanSubmit(false, "person@example.test", "short", true))
    }

    @Test fun missingEmailOrPasswordCannotSubmitEitherMode() {
        listOf(false, true).forEach { create ->
            assertFalse(careAccountCanSubmit(create, "", "1234567890", true))
            assertFalse(careAccountCanSubmit(create, "person", "1234567890", true))
            assertFalse(careAccountCanSubmit(create, "person@example.test", "", true))
        }
    }

    @Test fun confirmedAuthenticationCanClearAllEphemeralDraftFields() {
        val draft = CareAuthDraft().apply { email = "person@example.test"; password = "not-a-real-secret"; creatingAccount = true }
        draft.clear()
        assertEquals("", draft.email)
        assertEquals("", draft.password)
        assertFalse(draft.creatingAccount)
    }

    @Test fun memberRemovalRequiresTheCapturedAccountPersonOwnershipAndMember() {
        fun matches(account: String? = "owner", model: String = "owner", person: String = "person-a", owner: Boolean = true, member: Boolean = true) =
            careMemberRemovalContextMatches("owner", "person-a", account, model, person, owner, member)
        assertTrue(matches())
        assertFalse(matches(account = null))
        assertFalse(matches(account = "another-account"))
        assertFalse(matches(model = "another-account"))
        assertFalse(matches(person = "person-b"))
        assertFalse(matches(owner = false))
        assertFalse(matches(member = false))
        assertFalse(careMemberRemovalContextMatches("", "person-a", "", "", "person-a", true, true))
        assertFalse(careMemberRemovalContextMatches("owner", "", "owner", "owner", "", true, true))
    }
}
