package `in`.wedevit.care

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Fictional component fixtures only. No real account, patient tracker, microphone or AI service. */
class DoctorPatientRecordUiTest {
    @get:Rule val ui = createComposeRule()
    private lateinit var selectedSection: MutableState<String>

    @Test fun recordNavigationShowsOnlyChosenSectionAndHonestReportedMedicineStatuses() {
        val section = mutableStateOf("Summary")
        selectedSection = section
        ui.setContent {
            CareTheme {
                DemoPatientDetail(DoctorDemoData.patients.first(), section.value, { section.value = it }, emptyList(), {}, {}, {}, {}, {}, showNavigation = false)
            }
        }
        ui.onNodeWithText(DoctorDemoData.patients.first().careContext).assertExists()
        listOf("Summary", "Progress", "Medicines", "Conversations", "Visits", "Reports").forEach { ui.onNodeWithTag("demo-record-$it").assertDoesNotExist() }
        ui.onNodeWithTag("demo-dose-demo-asha-dose-1").assertDoesNotExist()
        select("Medicines")
        listOf("Taken reported", "Late reported", "Skipped reported", "Not logged", "Upcoming").forEach { status ->
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasText(status))
            ui.onNodeWithText(status).assertIsDisplayed()
        }
        ui.onNodeWithTag("demo-schedule-visit").assertDoesNotExist()
        ui.onNodeWithTag("workflow-open-conversation").assertDoesNotExist()
        select("Progress")
        ui.onNodeWithTag("demo-dose-demo-asha-dose-1").assertDoesNotExist()
        ui.onNodeWithText("A history of patient and family reports, not a clinical assessment.").assertDoesNotExist()
        select("Conversations")
        ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("workflow-open-conversation"))
        ui.onNodeWithTag("workflow-open-conversation").assertIsDisplayed()
        ui.onNodeWithTag("demo-schedule-visit").assertDoesNotExist()
        select("Visits")
        ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("demo-schedule-visit"))
        ui.onNodeWithTag("demo-schedule-visit").assertIsDisplayed()
        ui.onNodeWithTag("workflow-open-conversation").assertDoesNotExist()
        select("Reports")
        ui.onNodeWithText("1 summary · 0 attached files").assertExists()
        ui.onNodeWithTag("demo-dose-demo-asha-dose-1").assertDoesNotExist()
    }

    @Test fun mockConversationDoesNotShowLiveAiOrRequestAdditionalSignIn() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val namespace = "doctor-workflow-mock-record-ui-${UUID.randomUUID()}"
        val account = context.getSharedPreferences("care-account-kjnhtakqzboayqorrlue", Context.MODE_PRIVATE)
        val before = account.all.toMap()
        try {
            val store = DoctorWorkflowStore(context, preferencesName = namespace)
            ui.setContent { CareTheme { DoctorConversationWorkspace(DoctorDemoData.patients.first(), store, {}) } }
            ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag("workflow-step-Document"))
            ui.onNodeWithTag("workflow-step-Document").performClick()
            ui.onNodeWithTag("doctor-live-capture").assertDoesNotExist()
            ui.onNodeWithTag("doctor-ai-sign-in").assertDoesNotExist()
            ui.onNodeWithTag("doctor-record-audio").assertDoesNotExist()
            ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag("workflow-load-mock"))
            ui.onNodeWithTag("workflow-load-mock").assertIsDisplayed()
            assertTrue(store.draft("demo-asha").sourceText.isBlank())
            assertEquals(before, account.all)
        } finally { context.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    private fun select(section: String) {
        ui.runOnIdle { selectedSection.value = section }
    }
}
