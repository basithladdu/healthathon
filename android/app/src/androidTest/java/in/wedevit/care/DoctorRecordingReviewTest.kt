package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Pure presentation fakes only: no microphone, player, file, credential or provider is used. */
class DoctorRecordingReviewTest {
    @get:Rule val ui = createComposeRule()

    @Test fun simulatedRecordingPausesAndStopsIntoCompactPlayerWithoutInventingTranscript() {
        val state = mutableStateOf(DoctorRecordingReviewState())
        var transcribed = 0
        render(state, onTranscribe = { transcribed++ })
        screenshot("record-review-ready-simulated")
        ui.onNodeWithTag("record-start").performScrollTo().performClick()
        ui.mainClock.advanceTimeBy(400)
        ui.onNodeWithTag("record-timer").assertTextEquals("0:17")
        ui.onNodeWithTag("record-transcript").assertDoesNotExist()
        ui.onNodeWithTag("record-coverage-0").assertDoesNotExist()
        val expandedHeight = ui.onNodeWithTag("record-hero").fetchSemanticsNode().boundsInRoot.height
        screenshot("record-review-recording-simulated")
        ui.onNodeWithTag("record-pause").performScrollTo().performClick()
        ui.onNodeWithText("Recording paused").assertExists()
        ui.onNodeWithTag("record-transcript").assertDoesNotExist()
        ui.onNodeWithTag("record-pause").performClick()
        ui.onNodeWithTag("record-stop").performClick()
        ui.mainClock.advanceTimeBy(400)
        ui.onNodeWithTag("record-player").assertExists()
        ui.onNodeWithTag("record-timer").assertDoesNotExist()
        assertTrue(ui.onNodeWithTag("record-hero").fetchSemanticsNode().boundsInRoot.height < expandedHeight)
        ui.onNodeWithTag("record-transcribe").assertDoesNotExist()
        ui.onNodeWithTag("record-transcript-origin").performScrollTo().assertTextEquals("No transcript yet")
        assertEquals("", state.value.sourceText)
        assertEquals(0, transcribed)
        state.value.coverage.indices.forEach { index ->
            ui.onNodeWithTag("record-coverage-$index").performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not assessed"))
        }
        ui.onNodeWithTag("record-play").performScrollTo().performClick()
        assertTrue(state.value.playing)
        ui.onNodeWithTag("record-seek").performSemanticsAction(SemanticsActions.SetProgress) { it(5000f) }
        assertEquals(5000, state.value.playbackPositionMs)
        ui.onNodeWithTag("record-hero").performScrollTo()
        screenshot("record-review-stopped-simulated")
    }

    @Test fun fictionalSourceIsExplicitAndCoverageNeedsExactEvidenceOrHumanReview() {
        val source = "Patient: I do not want treatment."
        val state = mutableStateOf(DoctorRecordingReviewState(clipLabel = "Simulated clip—not a user recording", sourceText = source,
            sourceKind = DoctorTranscriptKind.FICTIONAL_SAMPLE, coverage = listOf(
                DoctorCoverageItem("Clipped evidence", DoctorCoverageStatus.REVIEWED_DISCUSSED, "want treatment", manuallyReviewed = true),
                DoctorCoverageItem("Exact fictional source", DoctorCoverageStatus.REVIEWED_DISCUSSED, source),
                DoctorCoverageItem("Not yet assessed"))))
        var reviewed = -1
        render(state, onCoverage = { reviewed = it })
        ui.onNodeWithTag("record-transcript-origin").performScrollTo().assertTextEquals("Sample transcript")
        ui.onNodeWithTag("record-coverage-0").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Needs review"))
        ui.onNodeWithTag("record-coverage-1").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Discussed"))
        ui.onNodeWithTag("record-coverage-2").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not assessed")).performClick()
        assertEquals(2, reviewed)
        ui.runOnIdle { state.value = state.value.copy(sourceText = "", coverage = listOf(DoctorCoverageItem("No source yet", DoctorCoverageStatus.NOT_FOUND_IN_TRANSCRIPT, manuallyReviewed = true))) }
        ui.onNodeWithTag("record-coverage-0").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Needs review"))
        ui.onNodeWithTag("record-transcript").performScrollTo().performTextReplacement("Manually entered fictional words.")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        assertEquals(DoctorTranscriptKind.MANUAL, state.value.sourceKind)
        assertTrue(state.value.coverage.all { it.status == DoctorCoverageStatus.NOT_ASSESSED })
        ui.onNodeWithTag("record-transcript-origin").assertTextEquals("Typed")
    }

    @Test fun busyAndUnavailableControlsCannotInvokeAnyTransportOrChangeSource() {
        val state = mutableStateOf(DoctorRecordingReviewState(clipLabel = "Simulated fixture", durationMs = 10000,
            sourceText = "Preserved fictional source.", sourceKind = DoctorTranscriptKind.MANUAL, canRecord = false, canTranscribe = true,
            busy = true, error = "Simulated service unavailable; source unchanged."))
        var calls = 0
        render(state, onTranscribe = { calls++ })
        ui.onNodeWithTag("record-start").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("record-play").assertIsNotEnabled()
        ui.onNodeWithTag("record-seek").assertIsNotEnabled()
        ui.onNodeWithTag("record-transcribe").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("record-transcript").performScrollTo().assertIsNotEnabled()
        assertEquals("Preserved fictional source.", state.value.sourceText)
        assertEquals(0, calls)
        ui.runOnIdle { state.value = state.value.copy(busy = false, canTranscribe = false) }
        ui.onNodeWithTag("record-transcribe").assertDoesNotExist()
    }

    private fun render(state: MutableState<DoctorRecordingReviewState>, onTranscribe: () -> Unit = {}, onCoverage: (Int) -> Unit = {}) {
        ui.setContent {
            CareTheme {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    Text("Simulated recorder interface—no microphone running")
                    DoctorRecordingReview(state.value,
                        onRecord = { state.value = state.value.copy(recording = true, seconds = 17, levels = listOf(.2f, .6f, .4f)) },
                        onPauseResumeRecording = { state.value = state.value.copy(paused = !state.value.paused) },
                        onStopRecording = { state.value = state.value.copy(recording = false, paused = false, clipLabel = "Simulated saved fixture", durationMs = 10000) },
                        onPlayPause = { state.value = state.value.copy(playing = !state.value.playing) },
                        onSeek = { state.value = state.value.copy(playbackPositionMs = it) }, onTranscribe = onTranscribe,
                        onSourceEdit = { state.value = state.value.copy(sourceText = it, sourceKind = DoctorTranscriptKind.MANUAL, coverage = defaultDoctorCoverage()) },
                        onCoverageReview = onCoverage)
                }
            }
        }
    }
    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(600); ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        target.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
