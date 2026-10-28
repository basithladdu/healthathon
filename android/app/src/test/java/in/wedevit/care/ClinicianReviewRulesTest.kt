package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The in-app doctor approval checks the same limits as the server's review_clinician before sending. */
class ClinicianReviewRulesTest {
    @Test fun decisionNeedsWhereItWasCheckedAndAReason() {
        ClinicianReviewRules.check("approved", "NMC register online", "Name and number match the register.")
        ClinicianReviewRules.check("rejected", "State council register", "Number not found on the register.")
        assertThrows(IllegalArgumentException::class.java) { ClinicianReviewRules.check("suspended", "NMC register online", "Reason given here.") }
        assertThrows(IllegalArgumentException::class.java) { ClinicianReviewRules.check("approved", "  short  ", "Reason given here.") }
        assertThrows(IllegalArgumentException::class.java) { ClinicianReviewRules.check("approved", "NMC register online", "ok") }
        assertThrows(IllegalArgumentException::class.java) { ClinicianReviewRules.check("approved", "x".repeat(1201), "Reason given here.") }
    }

    @Test fun requestRowsDecodeAndRejectBadIds() {
        val row = JSONObject().put("user_id", "123e4567-e89b-42d3-a456-426614174000").put("name", "Dr Test").put("council", "Test Council")
            .put("registration", "TEST-1").put("status", "pending").put("revision", 3).put("submitted_at", "2026-10-28T10:00:00Z")
        val decoded = decodeClinicianRequest(row)
        assertEquals(3L, decoded.revision); assertEquals("pending", decoded.status); assertEquals("Dr Test", decoded.name)
        assertThrows(IllegalArgumentException::class.java) { decodeClinicianRequest(JSONObject(row.toString()).put("user_id", "not-a-user")) }
    }
}
