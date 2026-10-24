@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI callbacks use fictional in-memory results only; no database, account or network operation. */
class CareFeedbackUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun actualErrorDialogAppearsAboveTheSheetAndDismissalKeepsItsDraft() {
        val pending = mutableStateOf(emptyList<CareFeedback>())
        val draft = mutableStateOf("")
        var closed = false
        ui.setContent { CareTheme {
            val snack = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { CareSnackbarHost(snack) }) { padding -> Box(Modifier.padding(padding)) }
            ModalBottomSheet(onDismissRequest = { closed = true }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column {
                    OutlinedTextField(draft.value, { draft.value = it }, Modifier.testTag("feedback-fixture-draft"))
                    Button(onClick = { pending.value = listOf(CareFeedback("failed", CareFeedbackKind.ERROR, "Couldn't save", "The record changed. Your answers are still here.")) }) { Text("Fixture failure") }
                }
            }
            CareFeedbackPresenter(pending.value, snack) { id -> pending.value = CareFeedbackRules.dismiss(pending.value, id) }
        } }
        ui.onNodeWithTag("feedback-fixture-draft").performTextInput("Retained fictional answer")
        closeSoftKeyboard()
        ui.onNodeWithText("Fixture failure").performClick()
        ui.onNodeWithTag("care-feedback-error-dialog").assertIsDisplayed()
        ui.onNodeWithText("The record changed. Your answers are still here.").assertIsDisplayed()
        screenshot("feedback-error-dialog")
        ui.onNodeWithTag("care-feedback-dismiss").performClick()
        ui.onNodeWithTag("feedback-fixture-draft").assertTextContains("Retained fictional answer")
        ui.runOnIdle { assertEquals("Retained fictional answer", draft.value); assertFalse(closed); assertTrue(pending.value.isEmpty()) }
    }

    @Test fun routineSavedFeedbackRemainsVisibleAfterItsSheetCloses() {
        val pending = mutableStateOf(emptyList<CareFeedback>())
        val sheetOpen = mutableStateOf(true)
        ui.setContent { CareTheme {
            val snack = remember { SnackbarHostState() }
            Scaffold(snackbarHost = { CareSnackbarHost(snack) }) { padding -> Text("Care", Modifier.padding(padding)) }
            if (sheetOpen.value) ModalBottomSheet(onDismissRequest = { sheetOpen.value = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Button(onClick = {
                    pending.value = listOf(CareFeedback("accepted", CareFeedbackKind.SAVED, "Saved", "Fictional entry saved"))
                    sheetOpen.value = false
                }) { Text("Accepted fixture callback") }
            }
            CareFeedbackPresenter(pending.value, snack) { id -> pending.value = CareFeedbackRules.dismiss(pending.value, id) }
        } }
        ui.onNodeWithText("Accepted fixture callback").performClick()
        ui.onNodeWithText("Accepted fixture callback").assertDoesNotExist()
        ui.onNodeWithTag("care-feedback-saved").assertIsDisplayed()
        screenshot("feedback-saved-snackbar")
        ui.onNodeWithTag("care-feedback-completed-dialog").assertDoesNotExist()
        ui.onNodeWithContentDescription("Dismiss message").performClick()
        ui.runOnIdle { assertTrue(pending.value.isEmpty()) }
    }

    @Test fun viewSelectionDialogDismissalDoesNotChangeRoleAndDisabledContextClosesIt() {
        val enabled = mutableStateOf(true)
        var chosen: String? = null
        ui.setContent { CareTheme { CareRoleMenu("Patient", enabled.value) { chosen = it } } }
        ui.onNodeWithTag("choose-role").performClick()
        ui.onNodeWithText("Choose your view").assertIsDisplayed()
        ui.onNodeWithTag("role-Patient").assertIsSelected()
        ui.onNodeWithText("Close").performClick()
        ui.runOnIdle { assertNull(chosen) }
        ui.onNodeWithTag("choose-role").performClick()
        ui.runOnIdle { enabled.value = false }
        ui.onNodeWithText("Choose your view").assertDoesNotExist()
        ui.runOnIdle { assertNull(chosen) }
    }

    @Test fun profileSelectionShowsCurrentChoiceAndOnlyExplicitSelectionChangesIt() {
        val selected = mutableStateOf("one")
        val people = listOf("one" to "Fictional first person", "two" to "Fictional second person")
        ui.setContent { CareTheme { CarePersonSelector(people.first { it.first == selected.value }.second, people, selected.value,
            enabled = true, onSelect = { selected.value = it }, onAdd = {}, canAdd = false) } }
        ui.onNodeWithTag("selected-care-profile").performClick()
        ui.onNodeWithText("Choose a care profile").assertIsDisplayed()
        ui.onNodeWithTag("person-one").assertIsSelected()
        ui.onNodeWithText("Close").performClick()
        ui.runOnIdle { assertEquals("one", selected.value) }
        ui.onNodeWithTag("selected-care-profile").performClick()
        ui.onNodeWithTag("person-two").performClick()
        ui.runOnIdle { assertEquals("two", selected.value) }
        ui.onNodeWithText("Choose a care profile").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(150) // Let the native dialog entrance settle before taking evidence.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
