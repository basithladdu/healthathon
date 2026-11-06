package `in`.wedevit.care

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The three welcome role tiles sit side by side only where every label fits on one line without breaking a word. */
class WelcomeRoleTilesTest {
    @Test fun tilesStackOnNarrowScreensAndAtLargeText() {
        assertFalse(welcomeRolesStacked(360, 1f))
        assertFalse(welcomeRolesStacked(411, 1.15f))
        assertTrue(welcomeRolesStacked(359, 1f))
        assertTrue(welcomeRolesStacked(411, 1.3f))
        assertTrue(welcomeRolesStacked(411, 2f))
    }
}
