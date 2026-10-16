package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Opens and cancels local consent only. Never presses valid Start or opens a microphone. */
class DoctorLocalPreviewConsentTest {
    @get:Rule val ui = createComposeRule()

    @Test fun localPreviewRequiresConsentButNeverAsksForCloudAuthentication() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val suffix = UUID.randomUUID().toString().replace('-', '_')
        val controller = DoctorAudioController(context, "qa_patient_$suffix", "qa_account_$suffix", scope, DoctorAudioStorage.LOCAL_PREVIEW)
        try {
            ui.setContent { CareTheme { Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) { DoctorLocalPreviewCapture(controller, "", DoctorTranscriptKind.NONE, {}) } } }
            ui.onNodeWithTag("record-transcribe").assertDoesNotExist()
            ui.onNodeWithTag("doctor-ai-sign-in").assertDoesNotExist()
            ui.onNodeWithTag("record-start").performScrollTo().performClick()
            listOf("purpose", "storage", "access").forEach { ui.onNodeWithTag("preview-record-$it").performScrollTo().assertIsOff() }
            ui.onNodeWithTag("preview-record-start").assertIsNotEnabled()
            ui.onNodeWithTag("preview-record-participants").performScrollTo().performTextInput("Fictional test participant")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.onNodeWithTag("preview-record-person-0").performScrollTo().assertIsOff()
            ui.onNodeWithTag("preview-record-start").assertIsNotEnabled()
            ui.onNodeWithTag("doctor-record-cloud").assertDoesNotExist()
            ui.onNodeWithText("Cancel").performClick()
            ui.onNodeWithTag("preview-record-start").assertDoesNotExist()
            assertFalse(controller.state.value.recording)
            assertTrue(controller.state.value.clips.isEmpty())
        } finally { controller.close(); scope.cancel() }
    }
}
