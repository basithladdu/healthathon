package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Stateless navigation only: no account, prescription or intake writes. */
class DoctorMedicineTabsTest {
    @get:Rule val ui=createComposeRule()
    @Test fun onlyCurrentAndPastTabsRemainAndCanReturnFromIndividualHistory() {
        var selected=""
        ui.setContent {CareTheme {MedicineHistoryTabs("Intake history",false){selected=it}}}
        ui.onNodeWithTag("medicine-tab-Intake history").assertDoesNotExist()
        ui.onNodeWithTag("medicine-tab-Current").assertIsDisplayed().performClick()
        assertEquals("Current",selected)
        ui.onNodeWithTag("medicine-tab-Past").assertIsDisplayed().performClick()
        assertEquals("Past",selected)
    }
}
