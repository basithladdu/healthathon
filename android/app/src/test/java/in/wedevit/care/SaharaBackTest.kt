package `in`.wedevit.care

import androidx.compose.animation.EnterExitState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaharaBackTest {
    @Test fun aScreenStillFadingOutDoesNotCatchSystemBack() {
        assertTrue(SaharaMotion.handlesBack(EnterExitState.Visible))
        assertFalse(SaharaMotion.handlesBack(EnterExitState.PostExit))
        assertFalse(SaharaMotion.handlesBack(EnterExitState.PreEnter))
    }
    @Test fun backOnTheWelcomeScreenIsNotSwallowedByALeftoverCareTab() {
        // Removing the last person from More (tab 4) shows the welcome screen; back must leave the app, not silently reset the tab.
        assertFalse(careTabBackEnabled(personShown = false, medicineScanner = false, tab = 4, activeRecording = false))
        assertTrue(careTabBackEnabled(personShown = true, medicineScanner = false, tab = 4, activeRecording = false))
        assertTrue(careTabBackEnabled(personShown = true, medicineScanner = false, tab = 0, activeRecording = true))
        assertFalse(careTabBackEnabled(personShown = true, medicineScanner = false, tab = 0, activeRecording = false))
        assertFalse(careTabBackEnabled(personShown = true, medicineScanner = true, tab = 4, activeRecording = false))
    }
}
