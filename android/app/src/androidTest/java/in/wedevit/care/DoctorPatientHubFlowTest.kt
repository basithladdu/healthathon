package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated fictional hub and mock navigation only; never opens real CareRoot, auth or network. */
class DoctorPatientHubFlowTest {
    @get:Rule val ui = createComposeRule()
    private val sections = listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports")

    @Test fun bentoTilesDispatchAllSixSectionsAndRemainAccessibleAtLargeText() {
        val opened = mutableListOf<String>()
        val scale = mutableFloatStateOf(1f)
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                CareTheme { Column(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).testTag("hub-test-scroll")) { DoctorPatientHub(DoctorDemoData.patients.first(), { opened += it }) } }
            }
        }
        screenshot("doctor-patient-hub-normal")
        sections.forEach { section -> ui.onNodeWithTag("demo-record-$section").performScrollTo().assertHasClickAction().performClick() }
        assertEquals(sections, opened)
        ui.runOnIdle { scale.floatValue = 1.5f }
        sections.forEach { section -> ui.onNodeWithTag("demo-record-$section").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp) }
        ui.onNodeWithTag("demo-record-Summary").performScrollTo()
        screenshot("doctor-patient-hub-large-text")
    }

    @Test fun actualMockRoutesPreservePatientAndDraftWhileBackReturnsToHubThenList() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val demoName = "doctor-demo-hub-ui-$id"
        val workflowName = "doctor-workflow-hub-ui-$id"
        val demo = DoctorDemoStore(context, preferencesName = demoName)
        val workflow = DoctorWorkflowStore(context, preferencesName = workflowName)
        val account = context.getSharedPreferences("care-account-kjnhtakqzboayqorrlue", Context.MODE_PRIVATE)
        val before = account.all.toMap()
        try {
            ui.setContent { CareTheme { DoctorDemo({}, demo, workflow) } }
            ui.onNodeWithTag("demo-login").performScrollTo().performClick()
            ui.onNodeWithTag("demo-nav-Patients").performClick()
            ui.onNodeWithTag("demo-patient-search").performTextInput("Asha")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.onNodeWithTag("demo-patient-demo-asha").performClick()
            sections.forEach { section ->
                openTile(section)
                ui.onNodeWithTag("demo-patient-detail").assertExists()
                sections.forEach { ui.onNodeWithTag("demo-record-$it").assertDoesNotExist() }
                if (section == "Summary") screenshot("doctor-patient-overview-dedicated")
                pressBack()
                ui.onNodeWithTag("demo-patient-hub").assertExists()
            }
            openTile("Conversations")
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("workflow-open-conversation"))
            ui.onNodeWithTag("workflow-open-conversation").performClick()
            ui.onNodeWithTag("workflow-step-Document").performClick()
            ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag("record-transcript"))
            ui.onNodeWithTag("record-transcript").performTextInput("Fictional Asha draft retained across profile navigation.")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag("workflow-back"))
            ui.onNodeWithTag("workflow-back").performClick()
            ui.onNodeWithTag("demo-patient-detail").assertExists()
            assertEquals("Fictional Asha draft retained across profile navigation.", workflow.draft("demo-asha").sourceText)
            assertTrue(workflow.draft("demo-ravi").sourceText.isBlank())
            pressBack()
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            pressBack()
            ui.onNodeWithTag("demo-patients-list").assertExists()
            assertTrue(workflow.versions("demo-asha").isEmpty())
            assertEquals(before, account.all)
        } finally {
            context.getSharedPreferences(demoName, Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(workflowName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun openTile(section: String) {
        ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-$section"))
        ui.onNodeWithTag("demo-record-$section").performClick()
    }
    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(600)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
