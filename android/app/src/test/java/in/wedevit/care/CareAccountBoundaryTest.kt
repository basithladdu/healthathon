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
}
