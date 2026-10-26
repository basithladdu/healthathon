package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Merge regression coverage without MainActivity, CareModel, care.db, auth, microphone or providers.
 * These are component/callback checks; full CareApp Back/profile routing is separately reviewed.
 */
class NativeMergeComponentsTest {
    @get:Rule val ui = createComposeRule()

    @Test fun newPatientShortcutsRetainTheirIndependentCallbacks() {
        val calls = mutableListOf<String>()
        ui.setContent { CareTheme { Column {
            DailyCareShortcuts(onCalendar = { calls += "calendar" }, onWorkflow = { calls += "care-team" }, onLabs = { calls += "labs" })
        } } }
        ui.onNodeWithTag("shortcut-calendar").performClick()
        ui.onNodeWithTag("shortcut-workflows").performClick()
        ui.onNodeWithTag("shortcut-labs").performClick()
        ui.runOnIdle { assertEquals(listOf("calendar", "care-team", "labs"), calls) }
    }

    @Test fun sourcePickerAndReaderKeepOriginalWordsAndPatientIdentity() {
        val source = "Patient: I do not want a decision today.\nFamily: Please explain the choices."
        val draft = mutableStateOf(Conversation("synthetic-merge-source", source = source))
        ui.setContent { CareTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CareSourcePicker(draft.value) { section, line -> draft.value = CareDraftAssist.assign(draft.value, section, line) }
            CareSourceLinks(draft.value.source, draft.value.priorities)
        } } }
        ui.onNodeWithTag("source-add-1-0").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(source, draft.value.source); assertEquals("synthetic-merge-source", draft.value.personId); assertEquals(source.lineSequence().first(), draft.value.priorities) }
        ui.onNodeWithText("Source line 1").performScrollTo().performClick()
        ui.onNodeWithText("Original words · line 1").assertIsDisplayed()
        ui.onNodeWithText("Close").performClick()
        ui.onNodeWithText("Original words · line 1").assertDoesNotExist()
        ui.runOnIdle { assertTrue(CareDraftAssist.references(source, draft.value.priorities).isNotEmpty()); assertTrue(CareDraftAssist.references(source, "want a decision today.").isEmpty()) }
    }

    @Test fun localDoctorLoginAndFeedbackEntrySurvivePatientWorkflowMerge() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val suffix = UUID.randomUUID().toString()
        val scheduleNamespace = "doctor-demo-merge-test-$suffix"
        val workflowNamespace = "doctor-workflow-merge-test-$suffix"
        val schedule = DoctorDemoStore(context, preferencesName = scheduleNamespace)
        val workflow = DoctorWorkflowStore(context, preferencesName = workflowNamespace)
        var exited = false
        try {
            ui.setContent { CareTheme { DoctorDemo(onExit = { exited = true }, store = schedule, workflowStore = workflow) } }
            ui.onNodeWithText("Doctor login").assertExists()
            ui.onNodeWithTag("demo-login").performScrollTo().performClick()
            ui.onNodeWithTag("demo-nav-Patients").assertIsDisplayed()
            ui.onNodeWithTag("demo-nav-Account").performClick()
            ui.onNodeWithTag("doctor-feedback-entry").performScrollTo().assertIsDisplayed()
            // Do not open the workspace's real guest-feedback preference scope.
            ui.onNodeWithTag("demo-exit").performScrollTo().performClick()
            ui.runOnIdle { assertTrue(exited) }
        } finally {
            context.getSharedPreferences(scheduleNamespace, Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(workflowNamespace, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun feedbackCanCloseWithoutWritingOrTouchingPatientStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = "merge-feedback-test-${UUID.randomUUID()}"
        val store = FeedbackStore(context, scope, "integration-test")
        var closed = false
        ui.setContent { CareTheme { FeedbackScreen(scope, "integration-test", onClose = { closed = true }) } }
        (1..5).forEach { ui.onNodeWithTag("feedback-rating-$it").performScrollTo().assertExists() }
        ui.onNodeWithTag("feedback-save").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("feedback-close").performScrollTo().performClick()
        ui.runOnIdle { assertTrue(closed); assertNull(store.latest()) }
    }
}
