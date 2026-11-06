package `in`.wedevit.care

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Opt-in (-PSAHARA_RENDER_UI=true): Rahul's symptom tracker, logging one symptom, the pain body step, a graph and the pain map. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SymptomFlowRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val tagged = { tag: String -> SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag) }
    private val tagStarts = { prefix: String -> SemanticsMatcher("tag starts with $prefix") { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true } }

    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun bring(m: SemanticsMatcher) {
        runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(m) }
        runCatching { compose.onAllNodes(m).onFirst().performScrollTo() }
        settle()
    }
    private fun has(tag: String) = compose.onAllNodes(tagged(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun click(m: SemanticsMatcher) { bring(m); compose.onAllNodes(m).onFirst().performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun click(tag: String) = click(tagged(tag))
    private fun shot(name: String) { captureScreenRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/symptoms", "$name.png").path) }

    @Test fun logOneSymptomAndReadTheTrends() {
        assertTrue(Build.FINGERPRINT.contains("robolectric", ignoreCase = true))
        compose.waitForIdle()
        runCatching { compose.onNode(tagged("care-language-en")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-rahul")
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
        click("symptom-tracker-entry"); compose.onNode(tagged("symptom-diary")).assertExists(); shot("1-tracker")
        compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(4); settle(); shot("1b-tracker-cards")
        // Log Tiredness: a quick tile on the tracker when there is one, otherwise the form's first step.
        if (has("symptom-quick-Tiredness")) click("symptom-quick-Tiredness") else { click("symptom-add"); shot("2-what"); click("symptom-choice-Tiredness") }
        shot("3-how"); click("symptom-rating-6"); shot("3b-rated")
        // Pain: back to the first step, then the body step (the 2D map stands in for the 3D viewer on the host).
        click("symptom-step-back")
        if (has("symptom-choice-Pain")) { click("symptom-choice-Pain"); shot("4-where") }
        runCatching { click("pain-body-site-chest"); click("pain-body-mark-chest"); shot("4b-where-marked") }
        repeat(4) { if (has("symptom-step-back")) click("symptom-step-back") }
        settle()
        click(tagStarts("symptom-trend-")); shot("5-graph")
        runCatching { compose.onAllNodes(hasText("Close")).onFirst().performSemanticsAction(SemanticsActions.OnClick); settle() }
        if (has("symptom-pain-history")) { click("symptom-pain-history"); shot("6-pain-map") }
    }
}
