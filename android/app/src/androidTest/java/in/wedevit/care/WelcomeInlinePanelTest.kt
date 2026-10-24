package `in`.wedevit.care
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
/** Isolated slots: no account, private credentials, care records or network. */
class WelcomeInlinePanelTest {
    @get:Rule val ui = createComposeRule()
    @Test fun directSignInAndVisibleRolesAvoidIntermediateScreens() {
        val selections = mutableListOf<String>()
        ui.setContent { CareTheme { Welcome({}, {},
            accountContent = { _, _ -> Text("Patient sign-in", Modifier.testTag("fixture-account")) },
            doctorContent = { _, _ -> Text("Doctor sign-in", Modifier.testTag("fixture-doctor")) }, onPanelChanged = { selections += it }) } }
        ui.onNodeWithTag("fixture-account").assertIsDisplayed()
        ui.onNodeWithTag("welcome-patient-family").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("doctor-workspace-entry").performClick()
        ui.onNodeWithTag("fixture-doctor").assertIsDisplayed()
        ui.onNodeWithTag("fixture-account").assertDoesNotExist()
        ui.onNodeWithText("Doctor workspace").assertDoesNotExist()
        ui.onNodeWithTag("welcome-patient-family").performClick()
        ui.onNodeWithTag("fixture-account").assertIsDisplayed()
        ui.runOnIdle { assertEquals(listOf("account", "doctor", "account"), selections) }
    }
    @Test fun offlineProfileHasExplicitEntryAndReturnsToSignIn() {
        ui.setContent { CareTheme { Welcome({}, {},
            addPersonContent = { close -> Button(onClick = close, modifier = Modifier.testTag("fixture-person")) { Text("Profile form") } },
            accountContent = { _, _ -> Text("Patient sign-in", Modifier.testTag("fixture-account")) }) } }
        ui.onNodeWithTag("welcome-add-person").performClick()
        ui.onNodeWithTag("fixture-person").assertIsDisplayed()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.onNodeWithTag("fixture-account").assertIsDisplayed()
    }
    @Test fun busyRequestCannotSwitchRolesAtLargeText() {
        val busy = mutableStateOf(false)
        ui.setContent { val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) { CareTheme { Column(Modifier.width(320.dp)) {
                Welcome({}, {}, accountContent = { _, onBusy ->
                    LaunchedEffect(busy.value) { onBusy(busy.value) }
                    Button(onClick = { busy.value = !busy.value }, modifier = Modifier.testTag("fixture-busy")) { Text("Request") }
                })
            } } }
        }
        ui.onNodeWithTag("fixture-busy").performClick()
        ui.onNodeWithTag("welcome-patient-family").assertIsNotEnabled()
        ui.onNodeWithTag("doctor-workspace-entry").assertIsNotEnabled()
        ui.onNodeWithTag("welcome-add-person").assertIsNotEnabled()
        ui.onNodeWithTag("fixture-busy").performClick()
        ui.onNodeWithTag("doctor-workspace-entry").assertIsEnabled()
    }
    @Test fun personNameRemainsRequiredTrimmedAndCapped() {
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
}
