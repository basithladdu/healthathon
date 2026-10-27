package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareAccountBoundaryTest {
    @Test fun cloudRefreshRequiresTheSameNonblankAccount() {
        assertFalse(careCloudAccountMatches("", null))
        assertFalse(careCloudAccountMatches("", ""))
        assertFalse(careCloudAccountMatches(" ", " "))
        assertFalse(careCloudAccountMatches("owner-a", null))
        assertFalse(careCloudAccountMatches("owner-a", "owner-b"))
        assertTrue(careCloudAccountMatches("owner-a", "owner-a"))
    }

    @Test fun signUpOutcomeReadsTheServerReply() {
        assertEquals(CareSignUp.CONFIRMATION_SENT, careSignUpOutcome(org.json.JSONObject("""{"id":"u","email":"a@example.com","identities":[{"id":"i"}]}""")))
        assertEquals(CareSignUp.ALREADY_REGISTERED, careSignUpOutcome(org.json.JSONObject("""{"id":"u","email":"a@example.com","identities":[]}""")))
        assertEquals(CareSignUp.CONFIRMATION_SENT, careSignUpOutcome(org.json.JSONObject("""{"user":{"id":"u","identities":[{"id":"i"}]}}""")))
    }
    @Test fun authMessagesArePlainAndBilingual() {
        assertEquals(CareAuthProblem.WRONG_PASSWORD, careAuthProblem("Invalid login credentials"))
        assertEquals(CareAuthProblem.NOT_CONFIRMED, careAuthProblem("Email not confirmed"))
        assertEquals(CareAuthProblem.ALREADY_REGISTERED, careAuthProblem("User already registered"))
        assertEquals(CareAuthProblem.MAIL_NOT_SENT, careAuthProblem("Error sending confirmation email"))
        assertEquals(CareAuthProblem.MAIL_NOT_SENT, careAuthProblem("Email address \"x@example.com\" not authorized"))
        assertEquals(CareAuthProblem.EMAIL_LIMIT, careAuthProblem("email rate limit exceeded"))
        assertEquals(CareAuthProblem.WAIT, careAuthProblem("For security purposes, you can only request this after 52 seconds."))
        assertEquals(CareAuthProblem.NO_CONNECTION, careAuthProblem("Couldn't reach your care records. Your entry is still here."))
        CareAuthProblem.values().filter { it != CareAuthProblem.OTHER }.forEach { p ->
            val raw = mapOf(CareAuthProblem.WRONG_PASSWORD to "Invalid login credentials", CareAuthProblem.NOT_CONFIRMED to "Email not confirmed", CareAuthProblem.ALREADY_REGISTERED to "already registered",
                CareAuthProblem.WEAK_PASSWORD to "Password should be at least 6 characters", CareAuthProblem.MAIL_NOT_SENT to "Error sending", CareAuthProblem.EMAIL_LIMIT to "email rate limit exceeded",
                CareAuthProblem.WAIT to "wait a moment", CareAuthProblem.NO_CONNECTION to "Couldn't reach")[p]
            assertEquals(p, careAuthProblem(raw)); assertTrue(p.name, HindiScan.hasHindi(careAuthMessage(raw, true))); assertFalse(p.name, HindiScan.hasHindi(careAuthMessage(raw, false)))
        }
        assertEquals("Something odd", careAuthMessage("Something odd", false)); assertTrue(HindiScan.hasHindi(careAuthMessage("Something odd", true)))
    }
}
