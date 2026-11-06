package `in`.wedevit.care

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import java.nio.file.Files
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Opt-in (-PSAHARA_RENDER_UI=true): the doctor's goals-of-care talk on the legal form, from the card to the note and a locked note. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class GocFlowRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val tagged = { tag: String -> SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag) }
    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun bring(tag: String) {
        runCatching { compose.onAllNodes(hasScrollToIndexAction(), useUnmergedTree = true).onLast().performScrollToNode(tagged(tag)) }
        runCatching { compose.onNode(tagged(tag)).performScrollTo() }
        settle()
    }
    private fun click(tag: String) { bring(tag); compose.onNode(tagged(tag)).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun shot(name: String) = captureScreenRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/goc", "$name.png").path)
    private val talk = listOf(
        "Doctor: What matters most to you now?",
        "Patient: Being at home with my family and free of pain.",
        "Doctor: If your heart stops, do you want CPR?",
        "Patient: No CPR. No ventilator.",
        "Daughter: We do not want dialysis. No ICU.",
    ).joinToString("\n")

    private fun show(record: EctprRecord) {
        val folder = Files.createTempDirectory("goc").toFile()
        compose.setContent { CareTheme {
            var d by remember { mutableStateOf(record) }
            CompositionLocalProvider(LocalGocFolder provides folder) { Column(Modifier.fillMaxSize()) { GocConversationCard(d) { f -> d = f(d) } } }
        } }
        settle()
    }

    @Test fun recordToNote() {
        CareLanguageState.current = CareLang.EN
        show(EctprLifecycle.newDraft("p1", "Dr Rao", "2026-11-06T10:00:00Z", EctprPatient(name = "Asha Rao", uhid = "UH-1"), id = "r1"))
        shot("0-card")
        runCatching { click("goc-card-consent") }
        click("goc-open"); runCatching { click("goc-consent") }
        shot("1-capture")
        click("goc-type")
        compose.onNode(hasSetTextAction() and hasText("Type or paste", substring = true)).performTextInput(talk); settle()
        click("goc-add-typed"); shot("2-review")
        runCatching { bring("goc-transcript"); shot("2b-transcript") }
        runCatching { click("goc-confirm-all") }
        runCatching { click("goc-finish") }; runCatching { click("goc-continue-anyway") }
        shot("3-form")
        bring("goc-signoff"); shot("4-sign")
    }

    @Test fun lockedNote() {
        CareLanguageState.current = CareLang.EN
        val base = EctprLifecycle.newDraft("p2", "Dr Rao", "2026-11-06T10:00:00Z", EctprPatient(name = "Asha Rao", uhid = "UH-2"), id = "r2")
        val g = GocTranscript(lines = GocLines.fromText(talk), consentAt = 1L, planVersion = base.version, note = "Goals-of-care discussion with Asha Rao.",
            signOff = GocSignOffSummary("s1", "seal", "2026-11-06T10:30:00Z", listOf("Dr Rao", "Asha Rao"), listOf("DOCTOR", "PATIENT"), listOf("KMC-1", ""), listOf("DRAW", "DRAW"), listOf("2026-11-06T10:25:00Z", "2026-11-06T10:28:00Z")))
        show(base.copy(gocTranscript = g))
        shot("5-card-locked")
        click("goc-open"); shot("6-locked")
    }
}
