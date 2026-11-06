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

/** Opt-in (-PSAHARA_RENDER_UI=true): the patient and family Today screen, the daily check-in, Care tab, visits, calendar and timeline. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class TodayRenderTest {
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
    private fun shot(name: String) { compose.onRoot().captureRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/today", "$name.png").path) }
    private fun step(name: String, block: () -> Unit) { runCatching { block(); shot(name) }.onFailure { println("TodayRenderTest $name: $it") } }
    private fun enter(user: String) {
        compose.waitForIdle()
        runCatching { compose.onNode(tagged("care-language-en")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-$user")
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    @Test fun patientToday() {
        enter("rahul")
        step("p01-today") {}
        step("p02-today-medicines") { bring("today-doses") }
        step("p03-today-end") { bring("quick-actions") }
        step("p04-checkin-tap") { bring("today-checkin"); runCatching { click("today-checkin-1") } }
        step("p05-checkin-screen") { click("today-checkin-words") }
        back()
        step("p06-care") { click("nav-Care") }
        step("p07-visits") { click("care-module-visits-toggle") }
        back()
        step("p08-calendar") { click("shortcut-calendar") }
        back()
        step("p09-timeline") { click("nav-Docs"); click("care-timeline-open") }
    }

    @Test fun familyToday() {
        enter("arjun")
        step("f01-today") {}
        step("f02-today-check") { bring("family-check") }
        step("f03-today-end") { bring("quick-actions") }
    }
}
