package `in`.wedevit.care

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ConnectedPreservedWalkthroughTest {
    @get:Rule val ui = createComposeRule()

    @Test fun explicitLegacyPatientRouteOpensLocalHubWithoutImportOrAuthentication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        val demoName = "doctor-demo-preserved-route-$suffix"
        val workflowName = "doctor-workflow-preserved-route-$suffix"
        val demo = DoctorDemoStore(context, preferencesName = demoName)
        val workflow = DoctorWorkflowStore(context, preferencesName = workflowName)
        val existing = WorkflowDraft("demo-asha", priorities = "Preserve synthetic draft", sourceText = "Synthetic existing original words")
        workflow.saveDraft(existing)
        val before = workflow.draft("demo-asha")
        val visits = demo.appointments()
        try {
            ui.setContent { CareTheme { DoctorDemo({}, demo, workflow, initialPatientId = "demo-asha") } }
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            ui.onNodeWithTag("demo-email").assertDoesNotExist()
            ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-Conversations"))
            ui.onNodeWithTag("demo-record-Conversations").performClick()
            ui.onNodeWithTag("demo-patient-detail").assertExists()
            pressBack()
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            ui.runOnIdle { assertEquals(before, workflow.draft("demo-asha")); assertEquals(visits, demo.appointments()); assertTrue(workflow.versions("demo-asha").isEmpty()) }
        } finally {
            context.getSharedPreferences(demoName, Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(workflowName, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
