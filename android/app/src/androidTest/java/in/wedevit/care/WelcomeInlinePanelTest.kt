package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Component-only synthetic slots: no CareModel, default preferences, authentication or network. */
class WelcomeInlinePanelTest {
    @get:Rule val ui = createComposeRule()

    @Test fun orangePanelOpensOnlySelectedFormAndKeepsWalkthroughSeparate() {
        val selections = mutableListOf<String>()
        var legacyCallbacks = 0
        var walkthroughs = 0
        ui.setContent { CareTheme {
            Welcome({ legacyCallbacks++ }, { legacyCallbacks++ }, { legacyCallbacks++ }, { walkthroughs++ },
                addPersonContent = { close -> Button(onClick = close, modifier = Modifier.testTag("fixture-person")) { Text("Synthetic person form") } },
                accountContent = { close, _ -> Button(onClick = close, modifier = Modifier.testTag("fixture-account")) { Text("Synthetic account form") } },
                doctorContent = { close, _ -> Button(onClick = close, modifier = Modifier.testTag("fixture-doctor")) { Text("Synthetic doctor sign-in") } },
                onPanelChanged = { selections += it })
        } }
        ui.onNodeWithTag("welcome-patient-family").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("doctor-workspace-entry").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("welcome-add-person").assertDoesNotExist()
        ui.onNodeWithTag("welcome-account").assertDoesNotExist()
        ui.onNodeWithTag("doctor-demo-entry").assertDoesNotExist()
        listOf("Welcome to Sahara").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(label).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> action(layouts) }
            org.junit.Assert.assertTrue("Collapsed welcome copy is centred", layouts.any { it.layoutInput.style.textAlign == TextAlign.Center })
        }
        // Three labelled role tiles: patient, family, doctor.
        ui.onNodeWithTag("welcome-family").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        listOf("Patient", "Family", "Doctor").forEach { ui.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        val screen = ui.onNodeWithTag("welcome-panel-screen").fetchSemanticsNode().boundsInRoot
        val illustration = ui.onNodeWithTag("welcome-hero-illustration").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(kotlin.math.abs(screen.center.x - illustration.center.x) <= 2f)
        val collapsedPanel = ui.onNodeWithTag("welcome-orange-panel").fetchSemanticsNode().boundsInRoot
        val doctorLink = ui.onNodeWithTag("doctor-workspace-entry").fetchSemanticsNode().boundsInRoot
        val sixteenDp = with(ui.density) { 16.dp.toPx() }
        org.junit.Assert.assertTrue("Collapsed orange panel should stay bottom anchored", kotlin.math.abs(screen.bottom - collapsedPanel.bottom) <= 2f)
        org.junit.Assert.assertTrue("No empty orange block below the doctor's touch target", collapsedPanel.bottom - doctorLink.bottom in 0f..(sixteenDp + 2f))
        listOf("welcome-add-person" to "person", "welcome-account" to "account").forEach { (entry, form) ->
            ui.onNodeWithTag("welcome-panel-gap").assertExists()
            val collapsedTop = ui.onNodeWithTag("welcome-orange-panel").fetchSemanticsNode().boundsInRoot.top
            ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
            ui.onNodeWithTag("welcome-add-person").assertIsDisplayed()
            ui.onNodeWithTag("welcome-account").assertIsDisplayed()
            ui.onNodeWithTag("doctor-demo-entry").assertDoesNotExist()
            ui.onNodeWithTag(entry).performScrollTo().performClick()
            ui.onNodeWithTag("welcome-orange-panel").assertExists()
            ui.onNodeWithTag("welcome-panel-gap").assertDoesNotExist()
            org.junit.Assert.assertTrue(ui.onNodeWithTag("welcome-orange-panel").fetchSemanticsNode().boundsInRoot.top < collapsedTop)
            val expanded = ui.onNodeWithTag("welcome-orange-panel").fetchSemanticsNode().boundsInRoot
            org.junit.Assert.assertTrue("Expanded form still fills the content height", kotlin.math.abs(expanded.top - screen.top) <= 2f && kotlin.math.abs(expanded.bottom - screen.bottom) <= 2f)
            ui.onNodeWithTag("fixture-$form").assertIsDisplayed()
            listOf("person", "account", "doctor").filterNot { it == form }.forEach { ui.onNodeWithTag("fixture-$it").assertDoesNotExist() }
            ui.onNodeWithTag("welcome-panel-back").performClick()
            ui.onNodeWithTag("welcome-add-person").assertIsDisplayed()
            ui.onNodeWithTag("welcome-panel-back").performClick()
            ui.onNodeWithTag("welcome-patient-family").assertExists()
        }
        ui.onNodeWithTag("doctor-workspace-entry").performScrollTo().performClick()
        ui.onNodeWithTag("fixture-doctor").assertIsDisplayed()
        ui.onNodeWithTag("welcome-add-person").assertDoesNotExist()
        ui.onNodeWithTag("doctor-demo-entry").assertIsDisplayed().performClick()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.runOnIdle {
            assertEquals(listOf("family", "person", "family", "", "family", "account", "family", "", "doctor", ""), selections)
            assertEquals(0, legacyCallbacks)
            assertEquals(1, walkthroughs)
        }
    }

    @Test fun familyTileOpensTheSamePanelAsPatientWithFamilyWording() {
        val selections = mutableListOf<String>()
        ui.setContent { CareTheme { Welcome({}, {}, addPersonContent = { _ -> Text("Synthetic person form") }, onPanelChanged = { selections += it }) } }
        ui.onNodeWithTag("welcome-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").assertIsDisplayed()
        ui.onNodeWithText("Add the person you care for").assertIsDisplayed()
        ui.onNodeWithTag("patient-walkthrough-entry").assertIsDisplayed()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
        ui.onNodeWithText("Start with your name").assertIsDisplayed()
        ui.runOnIdle { assertEquals(listOf("family", "", "family"), selections) }
    }

    @Test fun doctorFormRemainsReadableAtLargeTextAndCannotCloseWhileBusy() {
        val busy = mutableStateOf(false)
        val selections = mutableListOf<String>()
        ui.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                CareTheme { Column(Modifier.width(320.dp)) {
                    Welcome({}, {}, doctorContent = { _, onBusy ->
                        androidx.compose.runtime.LaunchedEffect(busy.value) { onBusy(busy.value) }
                        Column { Text("Synthetic doctor sign-in", Modifier.testTag("fixture-real-sign-in")); Button(onClick = { busy.value = !busy.value }, modifier = Modifier.testTag("fixture-busy")) { Text("Toggle request") } }
                    }, onPanelChanged = { selections += it })
                } }
            }
        }
        ui.onNodeWithTag("doctor-workspace-entry").performScrollTo().performClick()
        ui.onNodeWithTag("fixture-real-sign-in").assertIsDisplayed()
        ui.onNodeWithTag("doctor-demo-entry").assertIsDisplayed()
        ui.onNodeWithTag("fixture-busy").performClick()
        ui.onNodeWithTag("welcome-panel-back").assertIsNotEnabled()
        ui.onNodeWithTag("doctor-demo-entry").assertIsNotEnabled()
        ui.onNodeWithTag("fixture-busy").performClick()
        ui.onNodeWithTag("welcome-panel-back").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(listOf("doctor", ""), selections) }
    }

    @Test fun addPersonTrimsNameCapsLengthAndRequiresNonBlankInput() {
        val saved = mutableListOf<String>()
        ui.setContent { CareTheme { AddPersonContent({}, false, null) { saved += it } } }
        ui.onNodeWithTag("welcome-save-person").assertIsNotEnabled()
        ui.onNode(hasSetTextAction()).performTextInput("   Synthetic patient   ")
        ui.onNodeWithTag("welcome-save-person").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(listOf("Synthetic patient"), saved) }
        ui.onNode(hasSetTextAction()).performTextReplacement("x".repeat(150))
        ui.onNodeWithTag("welcome-save-person").performClick()
        ui.runOnIdle { assertEquals(100, saved.last().length) }
    }

    @Test fun parentSavingStateLocksAddPersonPanelUntilCompletion() {
        val saving = mutableStateOf(false)
        val selections = mutableListOf<String>()
        ui.setContent { CareTheme {
            Welcome({}, {}, addPersonContent = { _ ->
                Button(onClick = { saving.value = true }, modifier = Modifier.testTag("fixture-save-person")) { Text("Save synthetic profile") }
            }, onPanelChanged = { selections += it }, entryBusy = saving.value)
        } }
        ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").performScrollTo().performClick()
        ui.onNodeWithTag("fixture-save-person").performClick()
        ui.onNodeWithTag("welcome-panel-back").assertIsNotEnabled()
        ui.runOnIdle { saving.value = false }
        ui.onNodeWithTag("welcome-panel-back").assertIsEnabled().performClick()
        ui.onNodeWithTag("welcome-add-person").assertIsDisplayed()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.runOnIdle { assertEquals(listOf("family", "person", "family", ""), selections) }
    }
}
