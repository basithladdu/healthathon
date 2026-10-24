package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareEditContextTest {
    @Test fun delayedCallbacksRequireCurrentAccountSelectedPersonAndEditPermission() {
        assertTrue(careEditContextMatches("a", "a", "a", "p", "p", true))
        assertFalse(careEditContextMatches("a", "b", "a", "p", "p", true))
        assertFalse(careEditContextMatches("a", "a", "b", "p", "p", true))
        assertFalse(careEditContextMatches("a", "a", "a", "p", "other", true))
        assertFalse(careEditContextMatches("a", "a", "a", "p", "p", false))
        assertFalse(careEditContextMatches("a", "a", "a", "", "", true))
    }

    @Test fun localCareMayEditOnlyItsCurrentLocalPerson() {
        assertTrue(careEditContextMatches("", "", "", "p", "p", true))
        assertFalse(careEditContextMatches("", "", "a", "p", "p", true))
    }
}
