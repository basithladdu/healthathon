package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Component/unique-preference fixtures only. No real account, CareModel or live Room writes. */
class DoctorEmbeddedNavigationTest {
    @get:Rule val ui = createComposeRule()

    @Test fun embeddedToolsKeepExistingDailyCareAndSupportingFeaturesReachable() {
        val opened = mutableListOf<String>()
        val keys = listOf("daily", "calendar", "workflows", "labs", "nearby", "documents", "reminders", "shared", "resources", "feedback")
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CareTheme { Box(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding()) { EmbeddedCareTools { opened += it } } }
            }
        }
        keys.forEach {
            ui.onNodeWithTag("embedded-care-tools").performScrollToNode(hasTestTag("embedded-tool-$it"))
            ui.onNodeWithTag("embedded-tool-$it").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        }
        assertEquals(keys, opened)
    }

    @Test fun sharedHubHasAllPatientSectionsAndToolsAtLargeText() {
        val opened = mutableListOf<String>()
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CareTheme { Box(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding()) { DoctorSharedPatientHub(Person("fixture-person", "Test Person"), { opened += it }) } }
            }
        }
        ui.onNodeWithText("Test Person's care").assertExists()
        listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports").forEach {
            ui.onNodeWithTag("doctor-shared-$it").performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        }
        ui.onNodeWithTag("doctor-shared-patient-hub").performScrollToNode(hasTestTag("doctor-shared-tools"))
        ui.onNodeWithTag("doctor-shared-tools").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { it() }
        ui.onNodeWithTag("doctor-shared-patient-hub").performScrollToNode(hasText("Sharing, access & clinical review"))
        ui.onNodeWithText("Sharing, access & clinical review").performClick()
        assertEquals(listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports", "Tools", "Shared"), opened)
        listOf("Today", "Talk", "Care", "Nearby", "More").forEach { ui.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun walkthroughKeepsFourTabsWithPatientsSelectedAndPreservesStoredDraft() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        val demoName = "doctor-demo-embedded-nav-$suffix"
        val workflowName = "doctor-workflow-embedded-nav-$suffix"
        val demo = DoctorDemoStore(context, preferencesName = demoName)
        val workflow = DoctorWorkflowStore(context, preferencesName = workflowName)
        workflow.saveDraft(WorkflowDraft("demo-asha", sourceText = "Synthetic draft kept unchanged"))
        val before = workflow.draft("demo-asha")
        val visits = demo.appointments()
        try {
            ui.setContent { CareTheme { DoctorDemo({}, demo, workflow, initialPatientId = "demo-asha") } }
            listOf("Home", "Patients", "Schedule", "Account").forEach { ui.onNodeWithTag("demo-nav-$it").assertExists() }
            ui.onNodeWithTag("demo-nav-Patients").assertIsSelected()
            listOf("Today", "Talk", "Care", "Nearby", "More").forEach { ui.onNodeWithText(it).assertDoesNotExist() }
            ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-Medicines"))
            ui.onNodeWithTag("demo-record-Medicines").performClick()
            ui.onNodeWithTag("demo-patient-detail").assertExists()
            ui.onNodeWithTag("demo-nav-Patients").assertIsSelected()
            pressBack()
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            pressBack()
            ui.onNodeWithTag("walkthrough-patients-list").assertExists()
            assertEquals(before, workflow.draft("demo-asha"))
            assertEquals(visits, demo.appointments())
        } finally {
            context.getSharedPreferences(demoName, Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(workflowName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
