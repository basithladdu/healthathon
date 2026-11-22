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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Component-only synthetic slots for the first-run flow (CareOnboarding.kt): no CareModel, authentication or network. */
class WelcomeInlinePanelTest {
    @get:Rule val ui = createComposeRule()

    @Test fun roleCardsOpenOnlyTheirStepAndKeepWalkthroughSeparate() {
        val selections = mutableListOf<String>()
        var legacyCallbacks = 0
        var walkthroughs = 0
        ui.setContent { CareTheme {
            Welcome({ legacyCallbacks++ }, { legacyCallbacks++ }, { legacyCallbacks++ }, { walkthroughs++ },
                accountContent = { close, _ -> Button(onClick = close, modifier = Modifier.testTag("fixture-account")) { Text("Synthetic account form") } },
                doctorContent = { close, _ -> Button(onClick = close, modifier = Modifier.testTag("fixture-doctor")) { Text("Synthetic doctor sign-in") } },
                onPanelChanged = { selections += it }, onSaveProfile = { _, _ -> }, showIntro = false)
        } }
        ui.onNodeWithText("Who are you?").assertIsDisplayed()
        listOf("welcome-patient-family", "welcome-family", "doctor-workspace-entry").forEach { ui.onNodeWithTag(it).assertHasClickAction().assertHeightIsAtLeast(48.dp) }
        listOf("welcome-add-person", "welcome-account", "doctor-demo-entry", "onboarding-skip").forEach { ui.onNodeWithTag(it).assertDoesNotExist() }
        ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").assertIsDisplayed()
        ui.onNodeWithTag("welcome-account").assertIsDisplayed()
        ui.onNodeWithTag("patient-walkthrough-entry").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("doctor-demo-entry").assertDoesNotExist()
        ui.onNodeWithTag("welcome-account").performScrollTo().performClick()
        ui.onNodeWithTag("fixture-account").assertIsDisplayed()
        ui.onNodeWithTag("fixture-doctor").assertDoesNotExist()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.onNodeWithTag("welcome-add-person").assertIsDisplayed()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.onNodeWithTag("doctor-workspace-entry").performScrollTo().performClick()
        ui.onNodeWithTag("fixture-doctor").assertIsDisplayed()
        ui.onNodeWithTag("welcome-add-person").assertDoesNotExist()
        ui.onNodeWithTag("doctor-demo-entry").assertIsDisplayed().performClick()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.onNodeWithTag("welcome-patient-family").assertExists()
        ui.runOnIdle {
            assertEquals(listOf("family", "account", "family", "", "doctor", ""), selections)
            assertEquals(0, legacyCallbacks)
            assertEquals(1, walkthroughs)
        }
    }

    @Test fun introPagesLeadToTheRoleCardsAndCanBeSkipped() {
        ui.setContent { CareTheme { Welcome({}, {}, showIntro = true) } }
        ui.onNodeWithTag("welcome-hero-illustration").assertExists()
        ui.onNodeWithText("Welcome to Sahara").assertIsDisplayed()
        ui.onNodeWithTag("welcome-patient-family").assertDoesNotExist()
        ui.onNodeWithTag("onboarding-next").assertHeightIsAtLeast(48.dp).performClick()
        ui.onNodeWithTag("onboarding-next").performClick()
        ui.onNodeWithText("Get started").assertIsDisplayed()
        ui.onNodeWithTag("onboarding-next").performClick()
        ui.onNodeWithTag("welcome-patient-family").assertIsDisplayed()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.onNodeWithTag("onboarding-skip").assertHeightIsAtLeast(48.dp).performClick()
        ui.onNodeWithTag("welcome-family").assertIsDisplayed()
    }

    @Test fun profileStepsTrimTheNameValidateAgeAndAllowSkipping() {
        val saved = mutableListOf<Pair<String, CarePatientProfile?>>()
        ui.setContent { CareTheme { Welcome({}, {}, onSaveProfile = { name, profile -> saved += name to profile }, showIntro = false) } }
        ui.onNodeWithTag("welcome-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").performScrollTo().performClick()
        ui.onNodeWithText("What is the name of the person you care for?").assertIsDisplayed()
        ui.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        ui.onNodeWithTag("onboarding-name").performTextInput("   Synthetic patient   ")
        ui.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()
        ui.onNodeWithTag("onboarding-age").performTextInput("200")
        ui.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        ui.onNodeWithTag("onboarding-age").performTextReplacement("62")
        ui.onNodeWithTag("onboarding-next").assertIsEnabled().performClick()
        // About the illness: skipped here (the care-now steps have their own test).
        ui.onNodeWithText("About the illness").assertIsDisplayed()
        ui.onNodeWithTag("onboarding-skip-step").performClick()
        ui.onNodeWithTag("onboarding-skip-step").performClick()
        ui.onNodeWithTag("onboarding-skip-step").performClick()
        ui.onNodeWithText("You're all set").assertIsDisplayed()
        ui.onNodeWithTag("welcome-save-person").performClick()
        ui.runOnIdle { assertEquals("Synthetic patient", saved.single().first); assertEquals(62, saved.single().second?.age); assertEquals("", saved.single().second?.illness) }
    }

    @Test fun careNowStepsSaveIllnessDoctorAndTreatment() {
        val saved = mutableListOf<Pair<String, CarePatientProfile?>>()
        ui.setContent { CareTheme { Welcome({}, {}, onSaveProfile = { name, profile -> saved += name to profile }, showIntro = false) } }
        ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").performScrollTo().performClick()
        ui.onNodeWithTag("onboarding-name").performTextInput("Synthetic patient")
        ui.onNodeWithTag("onboarding-next").performClick()
        ui.onNodeWithTag("onboarding-skip-step").performClick()
        ui.onNodeWithTag("onboarding-illness").performTextInput("Synthetic illness")
        ui.onNodeWithTag("onboarding-illness-since").performTextInput("Mar 2025")
        ui.onNodeWithTag("onboarding-next").performClick()
        ui.onNodeWithTag("onboarding-next").assertIsNotEnabled()
        ui.onNodeWithTag("onboarding-doctor-name").performTextInput("Dr Synthetic")
        ui.onNodeWithTag("onboarding-doctor-place").performTextInput("Synthetic clinic")
        ui.onNodeWithTag("onboarding-doctor-phone").performTextInput("98765x43210")
        ui.onNodeWithTag("onboarding-next").performClick()
        ui.onNodeWithTag("onboarding-treatment-chemotherapy").assertHeightIsAtLeast(48.dp).performClick()
        ui.onNodeWithTag("onboarding-treatment-surgery").performScrollTo().performClick()
        ui.onNodeWithTag("onboarding-treatment-none_now").performScrollTo().performClick()
        ui.onNodeWithTag("onboarding-treatment-none_now").assertIsOn()
        ui.onNodeWithTag("onboarding-treatment-chemotherapy").assertIsOff().performClick()
        ui.onNodeWithTag("onboarding-next").performClick()
        ui.onNodeWithTag("onboarding-summary-care").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("welcome-save-person").performClick()
        ui.runOnIdle {
            val p = saved.single().second!!
            assertEquals("Synthetic illness", p.illness); assertEquals("Mar 2025", p.illnessSince)
            assertEquals("Dr Synthetic", p.doctorName); assertEquals("Synthetic clinic", p.doctorPlace); assertEquals("9876543210", p.doctorPhone)
            assertEquals(listOf("chemotherapy"), p.treatments)
        }
    }

    @Test fun skippingEveryOptionalStepSavesOnlyTheName() {
        val saved = mutableListOf<Pair<String, CarePatientProfile?>>()
        ui.setContent { CareTheme { Welcome({}, {}, onSaveProfile = { name, profile -> saved += name to profile }, showIntro = false) } }
        ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").performScrollTo().performClick()
        ui.onNodeWithText("What is your name?").assertIsDisplayed()
        ui.onNodeWithTag("onboarding-name").performTextInput("x".repeat(150))
        ui.onNodeWithTag("onboarding-next").performClick()
        repeat(4) { ui.onNodeWithTag("onboarding-skip-step").performClick() }
        ui.onNodeWithTag("welcome-save-person").performClick()
        ui.runOnIdle { assertEquals(100, saved.single().first.length); assertNull(saved.single().second) }
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
                    }, onPanelChanged = { selections += it }, showIntro = false)
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

    @Test fun parentSavingStateLocksTheFinalStepUntilCompletion() {
        val saving = mutableStateOf(false)
        ui.setContent { CareTheme { Welcome({}, {}, onSaveProfile = { _, _ -> saving.value = true }, entryBusy = saving.value, showIntro = false) } }
        ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").performScrollTo().performClick()
        ui.onNodeWithTag("onboarding-name").performTextInput("Synthetic patient")
        ui.onNodeWithTag("onboarding-next").performClick()
        repeat(4) { ui.onNodeWithTag("onboarding-skip-step").performClick() }
        ui.onNodeWithTag("welcome-save-person").performClick()
        ui.onNodeWithTag("welcome-panel-back").assertIsNotEnabled()
        ui.onNodeWithTag("welcome-save-person").assertIsNotEnabled()
        ui.runOnIdle { saving.value = false }
        ui.onNodeWithTag("welcome-panel-back").assertIsEnabled()
    }
}
