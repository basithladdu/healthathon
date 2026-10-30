package `in`.wedevit.care

import android.os.Build
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/** Opt-in (-PSAHARA_RENDER_UI=true): walks the real screens from More through the wizard to the ED view, wallet card, living will and withdrawal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarePlanFlowRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val tagged = { tag: String -> SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag) }

    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun bring(tag: String) {
        val lists = compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes()
        if (lists.isNotEmpty()) runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(tagged(tag)) }
        runCatching { compose.onNode(tagged(tag)).performScrollTo() }
        settle()
    }
    private fun click(tag: String) { bring(tag); compose.onNode(tagged(tag)).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun exists(tag: String) { bring(tag); compose.onNode(tagged(tag)).assertExists() }
    private fun next() = click("ectpr-next")
    private fun type(label: String, value: String) {
        val field = hasSetTextAction() and hasText(label, substring = true)
        runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(field) }
        compose.onNode(field).performTextInput(value); settle()
    }
    private fun shot(name: String) { compose.onRoot().captureRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/care-plan", "$name.png").path) }

    @Test fun planFromWizardToWithdrawal() {
        assertTrue(Build.FINGERPRINT.contains("robolectric", ignoreCase = true))
        compose.waitForIdle()
        runCatching { compose.onNode(tagged("care-language-en")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-rahul")
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
        exists("care-plan-entry"); shot("0-today-entry")
        click("nav-More"); click("more-care-plan-entry")
        exists("care-plan-home"); shot("1-home")
        // No plan: the ED view says standard care.
        click("care-plan-ed-entry"); exists("ed-banner-none"); exists("if-in-doubt"); exists("care-continues"); shot("2-ed-none"); click("ed-header-back")

        click("care-plan-start"); exists("ectpr-wizard")
        shot("3-wizard-patient"); next(); next(); next()
        // V4: treatment stays locked until understanding is ticked.
        next(); next(); exists("treatment-locked"); shot("4-treatment-locked")
        compose.onNodeWithText("Back").performSemanticsAction(SemanticsActions.OnClick); settle()
        compose.onNodeWithText("Back").performSemanticsAction(SemanticsActions.OnClick); settle()
        compose.onNodeWithText("The illness has been explained", substring = true).performSemanticsAction(SemanticsActions.OnClick); settle(); shot("5-understanding")
        next(); next()
        // V2: comfort goal with attempt CPR is refused without a reason.
        click("goal-c_comfort"); click("cpr-attempt"); shot("6-treatment"); next()
        exists("plan-issues"); shot("7-v2-blocked"); exists("cpr-none")
        click("cpr-none"); click("ceiling-2"); next()
        // Review date, then V7: the consultant signs with a registration number.
        compose.onNodeWithText("In 12 weeks").performSemanticsAction(SemanticsActions.OnClick); settle(); next()
        type("Consultant's name", "Dr Test Consultant"); type("Medical registration number", "KMC-00000"); click("sign-consultant"); shot("8-signed"); next()
        shot("9-check"); click("ectpr-sign")

        exists("ed-banner-active"); shot("10-ed-active")
        EdView.ORDER.forEach { exists("ed-row-${it.lowercase()}") }
        exists("care-continues"); shot("11-ed-bottom"); click("ed-header-back")
        click("care-plan-card-entry"); exists("wallet-qr"); shot("12-wallet"); click("wallet-header-back")

        click("care-plan-amd-entry"); click("amd-start"); exists("amd-status-draft"); shot("13-amd"); click("amd-header-back")

        click("care-plan-withdraw")
        type("Reason", "Test"); type("Signed by", "Tester"); shot("13b-withdraw-form"); click("care-plan-withdraw-confirm")
        click("care-plan-ed-entry"); exists("ed-banner-revoked"); shot("14-ed-withdrawn")
    }
}
