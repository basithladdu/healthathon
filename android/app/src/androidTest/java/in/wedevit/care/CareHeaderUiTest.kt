package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CareHeaderUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun compactHeaderKeepsProfileSwitchAddAndWalkthroughUserSelectionIndependent() {
        val selected = mutableStateOf("first")
        val user = mutableStateOf(PatientWalkthrough.users[0]); var left = 0
        var additions = 0
        ui.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                CareTheme { Column(Modifier.width(320.dp)) {
                    CarePersonSelector(if (selected.value == "first") "ff" else "Asha", listOf("first" to "ff", "second" to "Asha"), selected.value,
                        enabled = true, onSelect = { selected.value = it }, onAdd = { additions++ })
                    WalkthroughUserMenu(user.value, { user.value = PatientWalkthrough.user(it)!! }, { left++ })
                } }
            }
        }
        ui.onNodeWithContentDescription("Switch care profile, ff").assertIsDisplayed()
        ui.onNodeWithContentDescription("Add person").assertIsDisplayed()
        ui.onNodeWithTag("selected-care-profile").performClick()
        ui.onNodeWithTag("person-second").performClick()
        ui.onNodeWithText("Asha").assertIsDisplayed()
        ui.onNodeWithTag("add-care-person").performClick()
        ui.onNodeWithTag("walkthrough-user-menu").performClick()
        ui.onNodeWithTag("walkthrough-switch-rahul").assertIsSelected()
        ui.onNodeWithTag("leave-walkthrough").assertIsDisplayed()
        ui.onNodeWithTag("walkthrough-switch-arjun").performClick()
        ui.onNodeWithContentDescription("Walkthrough user, currently Arjun, Family").assertIsDisplayed()
        ui.runOnIdle { assertEquals("second", selected.value); assertEquals(1, additions); assertEquals("arjun", user.value.key); assertEquals(0, left) }
    }

    @Test fun recordingDisabledHeaderDoesNotPermitProfileChanges() {
        ui.setContent { CareTheme { Column {
            CarePersonSelector("Asha", listOf("first" to "Asha"), "first", false, {}, {})
        } } }
        ui.onNodeWithTag("selected-care-profile").assertIsNotEnabled()
        ui.onNodeWithTag("add-care-person").assertIsNotEnabled()
    }

    @Test fun illustratedFeatureCardsKeepTheirIndependentActions() {
        var checkins = 0
        var nearby = 0
        ui.setContent { CareTheme { Column {
            Tile("How are you?", R.drawable.wellbeing_checkin_illustration, Peach, Modifier.width(160.dp)) { checkins++ }
            Tile("Find care", R.drawable.nearby_care_illustration, Sage, Modifier.width(160.dp)) { nearby++ }
        } } }
        ui.onNodeWithText("How are you?").performClick()
        ui.onNodeWithText("Find care").performClick()
        ui.runOnIdle { assertEquals(1, checkins); assertEquals(1, nearby) }
    }

    @Test fun welcomeKeepsEntryActionsWithoutFeatureHintSection() {
        var started = 0
        var accounts = 0
        var workspaces = 0
        var walkthroughs = 0
        ui.setContent { CareTheme { Welcome({ accounts++ }, { started++ }, { workspaces++ }, { walkthroughs++ }) } }
        ui.onNodeWithText("Explore what Sahara can help with").assertDoesNotExist()
        ui.onNodeWithText("Care notes, medicines and visits.").assertIsDisplayed()
        ui.onNodeWithTag("doctor-demo-entry").assertDoesNotExist()
        ui.onNodeWithTag("welcome-patient-family").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-add-person").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-account").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-panel-back").performClick()
        ui.onNodeWithTag("doctor-workspace-entry").performScrollTo().performClick()
        ui.onNodeWithTag("welcome-open-doctor-workspace").performScrollTo().performClick()
        ui.onNodeWithTag("doctor-demo-entry").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, started); assertEquals(1, accounts); assertEquals(1, workspaces); assertEquals(1, walkthroughs) }
    }

    /** Explicitly synthetic component-only render. Never opens CareModel or creates records. */
    @Test fun captureNonPersistentCompactHeaderFixture() {
        ui.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                CareTheme { Column(Modifier.width(320.dp).background(Cream)) {
                    Text("UI test fixture · not a patient record", fontSize = 11.sp)
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.sahara_professional_logo), null, Modifier.size(40.dp))
                        Text("Sahara", modifier = Modifier.weight(1f), fontSize = 22.sp)
                        WalkthroughUserMenu(PatientWalkthrough.users[0], {}, {})
                    }
                    CarePersonSelector("Asha", listOf("fixture" to "Asha"), "fixture", true, {}, {})
                    Row(Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
                        Tile("How are you?", R.drawable.wellbeing_checkin_illustration, Peach, Modifier.weight(1f).fillMaxHeight()) {}
                        Spacer(Modifier.width(12.dp))
                        Tile("Find care", R.drawable.nearby_care_illustration, Sage, Modifier.weight(1f).fillMaxHeight()) {}
                    }
                } }
            }
        }
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val destination = File(instrumentation.targetContext.getExternalFilesDir(null), "header-test-fixtures").apply { mkdirs() }
        fun screenshot(name: String) {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(destination, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        screenshot("compact-closed.png")
        ui.onNodeWithTag("walkthrough-user-menu").performClick()
        ui.onNodeWithTag("walkthrough-switch-rahul").assertIsSelected()
        ui.waitForIdle()
        screenshot("compact-open.png")
    }
}
