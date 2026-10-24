package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Only fictional callbacks and in-memory state; no account, microphone, storage or network. */
class CareVisibleActionsUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun everyDailyAddActionIsVisibleAndManualMedicineDoesNotOpenPhotoScan() {
        val added = mutableListOf<String>()
        var scans = 0
        ui.setContent { CareTheme { CareAddMenuButton({ added += it }, { scans++ }) } }
        listOf("medicine", "visit", "task", "checkin", "journal").forEach { kind ->
            ui.onNodeWithTag("care-add-$kind").assertIsDisplayed().performClick()
        }
        ui.runOnIdle { assertEquals(listOf("medicine", "visit", "task", "checkin", "journal"), added); assertEquals(0, scans) }
        ui.onNodeWithTag("care-add-scan").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, scans) }
        ui.onNodeWithTag("care-add-menu").assertDoesNotExist()
    }

    @Test fun aModuleExposesItsContentWithoutAnExpandableHeading() {
        ui.setContent { CareTheme { CareModuleCard("fixture", "Fixture tasks", Icons.Default.Add, Mint, 1, false, false) { Text("Fictional visible task") } } }
        ui.onNodeWithText("Fictional visible task").assertIsDisplayed()
        ui.onNodeWithTag("care-module-fixture-heading").assert(hasClickAction().not())
        ui.onNodeWithTag("care-module-fixture-toggle").assertDoesNotExist()
    }

    @Test fun modulePagesReachEveryRecordAndReturnWithoutCollapsingTheSection() {
        ui.setContent { CareTheme { Column {
            CareModuleList("fixture", (1..7).toList(), "No fictional rows", keyOf = { it }) { Text("Fixture row $it") }
        } } }
        ui.onNodeWithText("Fixture row 1").assertIsDisplayed()
        ui.onNodeWithText("Fixture row 6").assertDoesNotExist()
        ui.onNodeWithTag("care-module-fixture-page").assertTextEquals("1–5 of 7")
        ui.onNodeWithTag("care-module-fixture-previous").assertIsNotEnabled()
        ui.onNodeWithTag("care-module-fixture-next").performClick()
        ui.onNodeWithText("Fixture row 7").assertIsDisplayed()
        ui.onNodeWithTag("care-module-fixture-page").assertTextEquals("6–7 of 7")
        ui.onNodeWithTag("care-module-fixture-next").assertIsNotEnabled()
        ui.onNodeWithTag("care-module-fixture-previous").performClick()
        ui.onNodeWithText("Fixture row 1").assertIsDisplayed()
    }

    @Test fun recordModesAndSavedAudioAreDirectChoicesAndRecordRequestsCallerConsent() {
        val mode = mutableStateOf("Record")
        var consentRequests = 0
        ui.setContent { CareTheme { StageFixture(mode = mode.value, onMode = { mode.value = it }, onRecord = { consentRequests++ }) } }
        listOf("Record", "Dictate", "Type").forEach { ui.onNodeWithTag("record-stage-mode-$it").assertIsDisplayed() }
        ui.onNodeWithTag("record-stage-mode-Dictate").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-mode-Type").performClick()
        ui.runOnIdle { assertEquals("Type", mode.value) }
        ui.onNodeWithTag("record-stage-mode-Record").performClick()
        ui.onNodeWithTag("record-stage-page-saved").performClick()
        ui.onNodeWithText("Fictional saved audio").assertIsDisplayed()
        ui.onNodeWithTag("record-stage-page-record").performClick()
        ui.onNodeWithTag("record-stage-button").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, consentRequests) }
    }

    @Test fun activeRecordingBlocksModeAndPageChangesButKeepsStopAvailable() {
        var stopped = 0
        var modes = 0
        ui.setContent { CareTheme { StageFixture(recording = true, onMode = { modes++ }, onStop = { stopped++ }) } }
        ui.onNodeWithTag("record-stage-mode-Type").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-mode-Dictate").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-page-saved").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-start-new").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-who").assertIsNotEnabled()
        ui.onNodeWithTag("doctor-capture-back").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-button").performScrollTo().assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(1, stopped); assertEquals(0, modes) }
    }

    @Test fun retainedAudioFailureKeepsRetryVisibleAndPreventsLeavingItsContext() {
        var retried = 0
        ui.setContent { CareTheme { StageFixture(saveError = true, onRetry = { retried++ }) } }
        ui.onNodeWithTag("record-stage-mode-Type").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-page-saved").assertIsNotEnabled()
        ui.onNodeWithTag("record-stage-who").assertIsNotEnabled()
        ui.onNodeWithTag("retry-saving-audio").performScrollTo().assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, retried) }
    }

    @Composable private fun StageFixture(mode: String = "Record", onMode: (String) -> Unit = {}, recording: Boolean = false,
        saveError: Boolean = false, onRecord: () -> Unit = {}, onStop: () -> Unit = {}, onRetry: () -> Unit = {}) {
        DoctorRecordStage(mode, onMode, recording, false, saveError, onRetry, .3f, 3, !saveError, onRecord, onStop,
            onBack = {}, backEnabled = !recording && !saveError, demo = true, localRecordingAllowed = true, testRecord = true,
            menuEnabled = true, onStartNew = {}, onBackToWho = {}, backToWhoEnabled = true, audioCount = 2) { Text("Fictional saved audio") }
    }
}
