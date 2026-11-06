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

/** Opt-in (-PSAHARA_RENDER_UI=true): screenshots of the family-side Today SOS/QR, Nearby, suggest a centre, devices, community and Account screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class FamilyMoreRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val tagged = { tag: String -> SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag) }

    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun click(tag: String) {
        runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(tagged(tag)) }
        runCatching { compose.onNode(tagged(tag)).performScrollTo() }
        compose.onAllNodes(tagged(tag)).onFirst().performSemanticsAction(SemanticsActions.OnClick); settle()
    }
    private fun back() { compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }; settle() }
    private fun shot(name: String) { compose.onRoot().captureRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/family-more", "$name.png").path) }
    /** One screen; a failure is printed and the tour carries on so every other screenshot is still taken. */
    private fun step(name: String, block: () -> Unit) { runCatching { block(); shot(name) }.onFailure { println("FamilyMoreRenderTest $name: $it") } }

    @Test fun familyTour() {
        compose.waitForIdle()
        runCatching { compose.onNode(tagged("care-language-en")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-rahul")
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
        step("01-today") {}
        step("02-emergency-qr") { click("today-qr") }
        back()
        step("03-nearby-location-why") { click("nav-Nearby") }
        step("03b-nearby") { click("permission-not-now") }
        step("03c-nearby-layers") { click("nearby-layer-palliative") }
        step("04-suggest-centre") { click("nearby-suggest-centre") }
        back()
        step("05-devices") { click("nav-Care"); click("shortcut-devices") }
        back()
        step("06-community") { click("shortcut-community") }
        back()
        step("07-account") { click("nav-More") }
        step("07b-about") { click("more-about") }
        back()
        step("08-care-plan") { click("more-care-plan-entry") }
    }
}
