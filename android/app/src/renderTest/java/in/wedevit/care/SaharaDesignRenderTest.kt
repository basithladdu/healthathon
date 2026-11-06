package `in`.wedevit.care

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Shared steps for the design renders: open screens, capture them, and check that no visible word is split across two lines. */
abstract class SaharaDesignRenderBase {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    protected val tagged = { tag: String -> SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag) }
    protected fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    protected fun click(tag: String) { runCatching { compose.onNode(tagged(tag)).performScrollTo() }; compose.onNode(tagged(tag)).performSemanticsAction(SemanticsActions.OnClick); settle() }
    protected fun shot(name: String) { compose.onRoot().captureRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/design", "$name.png").path) }
    protected fun start(language: String) {
        compose.waitForIdle()
        runCatching { compose.onNode(tagged("care-language-menu")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        runCatching { compose.onNode(tagged("care-language-$language")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        settle()
    }
    protected fun today() {
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-rahul")
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }
    protected fun doctorHome() {
        click("welcome-patient-family"); click("welcome-panel-back")
        click("doctor-workspace-entry"); click("doctor-demo-entry")
        runCatching { click("demo-login") }
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("demo-nav-Home")).fetchSemanticsNodes().isNotEmpty() }
        settle()
    }

    private fun wordChar(c: Char) = Character.isLetterOrDigit(c) || Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }

    /** Every wrapped line on screen ends at a space or punctuation, never inside a word ("PATIEN / T"). */
    protected fun assertNoSplitWords(screen: String) {
        val bad = mutableListOf<String>()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
            val results = mutableListOf<TextLayoutResult>()
            runCatching { node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results) }
            results.forEach { layout ->
                val text = layout.layoutInput.text.text
                for (line in 0 until layout.lineCount - 1) {
                    if (layout.isLineEllipsized(line)) continue
                    val end = layout.getLineEnd(line)
                    if (end in 1 until text.length && wordChar(text[end - 1]) && wordChar(text[end])) bad += "\"${text.take(60)}\" breaks after \"${text.substring(maxOf(0, end - 8), end)}\""
                }
            }
        }
        assertTrue("$screen: words split across lines:\n" + bad.distinct().joinToString("\n"), bad.isEmpty())
    }
}

/** Opt-in (-PSAHARA_RENDER_UI=true): Welcome, Today and the doctor home at normal size, in English. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SaharaDesignRenderTest : SaharaDesignRenderBase() {
    @Test fun welcomeAndToday() {
        start("en"); shot("1-welcome"); assertNoSplitWords("Welcome")
        click("welcome-patient-family"); shot("2-welcome-get-started")
        click("welcome-panel-back"); today(); shot("3-today"); assertNoSplitWords("Today")
        runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeUp() }; settle(); shot("4-today-scrolled") }
    }

    @Test fun doctor() { start("en"); doctorHome(); shot("6-doctor-home"); assertNoSplitWords("Doctor home") }
}

/** Opt-in: the same screens in Hindi at 1.3x and 2x system font size; no word may break. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SaharaDesignLargeTextRenderTest : SaharaDesignRenderBase() {
    @Test fun hindiAtDoubleSize() {
        start("hi"); shot("7-welcome-hi-2x"); assertNoSplitWords("Welcome hi 2x")
        today(); shot("8-today-hi-2x"); assertNoSplitWords("Today hi 2x")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi", fontScale = 1.3f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class SaharaDesignMediumTextRenderTest : SaharaDesignRenderBase() {
    @Test fun doctorHomeHindiAt13() { start("hi"); doctorHome(); shot("9-doctor-home-hi-1.3x"); assertNoSplitWords("Doctor home hi 1.3x") }
}
