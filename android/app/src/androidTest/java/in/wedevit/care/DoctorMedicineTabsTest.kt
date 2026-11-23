package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Stateless navigation only: no account, prescription or intake writes. */
class DoctorMedicineTabsTest {
    @get:Rule val ui=createComposeRule()
    @Test fun currentPastAndIntakeHistoryTabsEachSelectTheirView() {
        var selected=""
        ui.setContent {CareTheme {MedicineHistoryTabs("Intake history",false){selected=it}}}
        ui.onNodeWithTag("medicine-tab-Current").assertIsDisplayed().performClick()
        assertEquals("Current",selected)
        ui.onNodeWithTag("medicine-tab-Past").assertIsDisplayed().performClick()
        assertEquals("Past",selected)
        // The doctor's intake tab (taken / missed doses) sits beside Current and Past.
        ui.onNodeWithTag("medicine-tab-Intake history").assertIsDisplayed().performClick()
        assertEquals("Intake history",selected)
    }
}
