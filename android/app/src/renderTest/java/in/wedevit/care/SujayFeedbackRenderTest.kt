package `in`.wedevit.care

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Opt-in (-PSAHARA_RENDER_UI=true): the screens changed after Dr Sujay's 21 Nov review (Today colours, faces, thermometer, medicines, care plan). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SujayFeedbackRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val tagged = { tag: String -> SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag) }

    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun bring(tag: String) {
        runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(tagged(tag)) }
        runCatching { compose.onAllNodes(tagged(tag)).onFirst().performScrollTo() }
        settle()
    }
    private fun click(tag: String) { bring(tag); compose.onAllNodes(tagged(tag)).onFirst().performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; settle() }
    private fun shot(name: String) { compose.onRoot().captureRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/sujay", "$name.png").path) }
    private fun step(name: String, block: () -> Unit) { runCatching { block(); shot(name) }.onFailure { println("SujayFeedbackRenderTest $name: $it") } }

    @Test fun patientScreens() {
        compose.waitForIdle()
        runCatching { compose.onNode(tagged("care-language-en")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-rahul")
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
        step("s01-today") {}
        step("s02-medicines") { bring("today-doses-any-time") }
        step("s03-checkin") { click("today-checkin-words") }
        back()
        step("s04-thermometer") { click("distress-yes"); click("distress-score-5") }
        step("s05-thermometer-8") { click("distress-score-8") }
        back()
        step("s06-care-plan") { click("nav-Care"); click("care-plan-entry") }
    }
}
