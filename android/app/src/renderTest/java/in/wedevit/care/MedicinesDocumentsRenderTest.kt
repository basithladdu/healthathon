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

/** Opt-in (-PSAHARA_RENDER_UI=true): Today's doses, Care → Medicines, the add-medicine form, Documents, Ask, Reminders and Lab history in the patient walkthrough. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-notnight-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class MedicinesDocumentsRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val tagged = { tag: String -> SemanticsMatcher.expectValue(SemanticsProperties.TestTag, tag) }

    private fun settle() { compose.mainClock.advanceTimeBy(1500); compose.waitForIdle() }
    private fun bring(tag: String) {
        runCatching { compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(tagged(tag)) }
        runCatching { compose.onNode(tagged(tag)).performScrollTo() }
        settle()
    }
    private fun click(tag: String) { bring(tag); compose.onNode(tagged(tag)).performSemanticsAction(SemanticsActions.OnClick); settle() }
    private fun tryClick(tag: String) = runCatching { click(tag) }.isSuccess
    private fun shot(name: String) = runCatching { compose.onRoot().captureRoboImage(File(System.getProperty("sahara.render.dir") ?: "build/outputs/meds", "$name.png").path) }

    @Test fun medicinesAndDocuments() {
        compose.waitForIdle()
        runCatching { compose.onNode(tagged("care-language-en")).performSemanticsAction(SemanticsActions.OnClick); settle() }
        click("welcome-patient-family"); click("patient-walkthrough-entry"); click("patient-walkthrough-rahul")
        compose.waitUntil(60_000) { compose.onAllNodes(tagged("nav-Today")).fetchSemanticsNodes().isNotEmpty() }
        settle()
        if (runCatching { bring("today-doses") }.isSuccess) shot("1-today-doses")
        // One tap on the first dose not marked yet ticks it Taken.
        runCatching {
            val dose = compose.onAllNodes(hasTestTagPrefix("today-dose-")).fetchSemanticsNodes().firstOrNull()
            if (dose != null) { compose.onAllNodes(hasTestTagPrefix("today-dose-")).onFirst().performSemanticsAction(SemanticsActions.OnClick); settle(); shot("2-today-dose-ticked") }
        }
        click("nav-Care")
        if (tryClick("care-module-medicines-toggle")) { shot("3-care-medicines"); tryClick("care-module-back") }
        click("nav-More"); click("more-documents-entry"); shot("4-documents")
        if (tryClick("document-folder-lab")) shot("5-documents-lab-open")
        if (tryClick("document-ask-open")) { shot("6-ask"); compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle() }
        click("nav-More"); if (tryClick("more-reminders")) { shot("7-reminders"); compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }; settle() }
        click("nav-More"); if (tryClick("more-labs")) shot("8-labs")
        click("nav-Care"); if (tryClick("care-module-medicines-toggle")) { compose.onAllNodes(hasText("Add manually")).onFirst().performSemanticsAction(SemanticsActions.OnClick); settle(); shot("9-add-medicine") }
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") { node ->
        node.config.getOrElseNullable(SemanticsProperties.TestTag) { null }?.let { it.startsWith(prefix) && !it.startsWith("today-dose-family") && !it.startsWith("today-doses") } == true
    }
}
