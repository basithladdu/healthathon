package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DoctorCaptureHeroTest {
    @get:Rule val ui = createComposeRule()
    @Test fun fullWidthHeroKeepsBackAndModeAboveWhiteTranscriptBody() {
        var back = false
        var mode = "Type"
        ui.setContent { CareTheme { Surface(color = Color.White) { Column(Modifier.fillMaxWidth().testTag("capture-container")) {
            DoctorCaptureHeader("Type", { mode = it }, false, false, 0f, 0, true, { back = true })
            Text("Fictional practice draft")
            OutlinedTextField("", {}, label = { Text("Conversation transcript") })
        } } } }
        val hero = ui.onNodeWithTag("doctor-capture-hero").fetchSemanticsNode().boundsInRoot
        val container = ui.onNodeWithTag("capture-container").fetchSemanticsNode().boundsInRoot
        assertEquals(container.left, hero.left, .1f)
        assertEquals(container.right, hero.right, .1f)
        ui.onNodeWithText("Conversation").assertExists()
        ui.onNodeWithText("Conversation transcript").assertExists()
        listOf("1 Capture", "2 Review draft", "3 Publish").forEach { ui.onNodeWithText(it).assertDoesNotExist() }
        ui.onNodeWithText("Back to conversations").performClick()
        assertTrue(back)
        ui.onNodeWithTag("doctor-capture-mode").performClick()
        ui.onNodeWithText("Record · unavailable in demo").assertIsNotEnabled()
        ui.onNodeWithText("Type").performClick()
        assertEquals("Type", mode)
    }
    @Test fun recordingBlocksVisibleBackButton() {
        var back = false
        ui.setContent { CareTheme { DoctorCaptureHeader("Record", {}, true, false, .4f, 3, false, { back = true }, backEnabled = false) } }
        ui.onNodeWithTag("doctor-capture-back").assertExists().assertIsNotEnabled()
        assertFalse(back)
    }
    @Test fun isolatedLocalRecordingCanBeChosenWhileDemoDictationStaysDisabled() {
        var mode = "Type"
        ui.setContent { CareTheme { DoctorCaptureHeader("Record", { mode = it }, false, false, 0f, 0, true, localRecordingAllowed = true, testRecord = true) } }
        ui.onNodeWithTag("doctor-capture-mode").performClick()
        ui.onNodeWithText("Dictate · unavailable").assertIsNotEnabled()
        ui.onNodeWithText("Record").assertIsEnabled().performClick()
        assertEquals("Record", mode)
    }
}
