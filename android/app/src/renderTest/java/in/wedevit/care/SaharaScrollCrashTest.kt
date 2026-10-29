package `in`.wedevit.care

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/**
 * Opt-in (-PSAHARA_RENDER_UI=true) crash check: opens the Practice walkthrough as Rahul or Arjun,
 * opens one bottom-nav tab and swipes every vertical scroller on it to the end. Any exception thrown
 * while composing, measuring or drawing the newly visible rows fails the test with its stack trace.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class SaharaScrollCrashTest(private val user: String, private val tab: String) {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun scrollToEnd() {
        assertTrue(Build.FINGERPRINT.contains("robolectric", ignoreCase = true))
        compose.waitForIdle()
        runCatching { click("care-language-en") }
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-$user")
        compose.waitUntil(60_000) { compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, "nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
        click("nav-$tab")
        try {
            val vertical = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            // Lazy lists: step through every row index so each row is composed, then swipe the rest to the end.
            val lazy = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)
            var rows = 0
            compose.onAllNodes(lazy).fetchSemanticsNodes().map { it.id }.forEach { id ->
                var index = 0
                while (true) {
                    try { compose.onNode(SemanticsMatcher("id $id") { it.id == id }).performScrollToIndex(index) }
                    catch (e: IllegalArgumentException) { if (e.message?.contains("out of bounds") == true) break else throw e }
                    compose.waitForIdle(); rows++; index += 2
                }
            }
            var reached = 0
            repeat(60) {
                val nodes = compose.onAllNodes(vertical).fetchSemanticsNodes()
                val open = nodes.filter { n -> n.config[SemanticsProperties.VerticalScrollAxisRange].let { it.value() < it.maxValue() - 0.5f } }
                if (open.isEmpty()) return@repeat
                reached = nodes.size
                open.forEach { n -> compose.onNode(SemanticsMatcher("id ${n.id}") { it.id == n.id }).performTouchInput { swipeUp() }; settle() }
            }
            File(outDir(), "$user-$tab.txt").writeText("ok: composed $rows lazy rows by index, swiped $reached vertical scrollers to the end\n")
        } catch (e: Throwable) {
            File(outDir(), "$user-$tab.txt").writeText(e.stackTraceToString())
            throw e
        }
    }

    private fun click(tag: String) { runCatching { compose.onNodeWithTag(tag).performScrollTo() }; compose.onNodeWithTag(tag).performClick(); settle() }
    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun outDir() = File(System.getProperty("sahara.render.dir") ?: "build/outputs/sahara-scroll-crash").apply { mkdirs() }
}

// One class per user and tab so Gradle (forkEvery = 1) gives each a fresh JVM and walkthrough seed.
class ScrollRahulToday : SaharaScrollCrashTest("rahul", "Today")
class ScrollRahulDocs : SaharaScrollCrashTest("rahul", "Docs")
class ScrollRahulCare : SaharaScrollCrashTest("rahul", "Care")
@Ignore("MapLibre native code cannot load on the JVM (UnsatisfiedLinkError in NativeConnectivityListener); check Nearby on a phone") class ScrollRahulNearby : SaharaScrollCrashTest("rahul", "Nearby")
class ScrollRahulMore : SaharaScrollCrashTest("rahul", "More")
class ScrollArjunToday : SaharaScrollCrashTest("arjun", "Today")
class ScrollArjunDocs : SaharaScrollCrashTest("arjun", "Docs")
class ScrollArjunCare : SaharaScrollCrashTest("arjun", "Care")
@Ignore("MapLibre native code cannot load on the JVM (UnsatisfiedLinkError in NativeConnectivityListener); check Nearby on a phone") class ScrollArjunNearby : SaharaScrollCrashTest("arjun", "Nearby")
class ScrollArjunMore : SaharaScrollCrashTest("arjun", "More")
