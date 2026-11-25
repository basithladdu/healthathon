package `in`.wedevit.care

import androidx.compose.animation.EnterExitState
import org.junit.Assert.assertEquals
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
    @Test fun careTabBackSaysWhetherItStopsARecordingOrChangesTheTab() {
        // Stopping a recording stays on the screen (plain BackHandler); only a tab change previews the leaving tab.
        assertEquals(CareTabBack.STOP_RECORDING, careTabBack(personShown = true, medicineScanner = false, tab = 1, activeRecording = true))
        assertEquals(CareTabBack.STOP_RECORDING, careTabBack(personShown = true, medicineScanner = false, tab = 0, activeRecording = true))
        assertEquals(CareTabBack.PREVIOUS_TAB, careTabBack(personShown = true, medicineScanner = false, tab = 9, activeRecording = false))
        assertEquals(CareTabBack.NONE, careTabBack(personShown = true, medicineScanner = false, tab = 0, activeRecording = false))
        assertEquals(CareTabBack.NONE, careTabBack(personShown = true, medicineScanner = true, tab = 9, activeRecording = true))
        assertEquals(CareTabBack.NONE, careTabBack(personShown = false, medicineScanner = false, tab = 9, activeRecording = true))
    }
}
