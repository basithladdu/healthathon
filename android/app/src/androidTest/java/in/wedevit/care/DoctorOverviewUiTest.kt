package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Read-only synthetic overview models only: no actual patient store, authentication or provider. */
class DoctorOverviewUiTest {
    @get:Rule val ui = createComposeRule()
    private val now = DoctorPatientTracking.asOf + 86_400_000L
    private fun version(patient: String, id: String, number: Int) = WorkflowVersion(id, patient, number,
        WorkflowDraft(patient, sourceText = "Exact fictional source for $id.", questions = "Questions recorded for $id."), "Self-entered sample reviewer $id", now - number * 1000)
    private fun overview(patient: String = "demo-asha") = DoctorOverviewData.build(patient,
        listOf(version(patient, "$patient-older", 1), version(patient, "$patient-latest", 2)), emptyList(), now)!!

    @Test fun timelineReadsExactSelectedSourceAndRoutesAllOverviewActions() {
        val model = mutableStateOf(overview())
        val actions = mutableListOf<String>()
        render(model, 1f, actions)
        ui.onNodeWithTag("overview-clinical-assessment").assertTextEquals("Clinical assessment: Not recorded")
        ui.onNodeWithTag("overview-previous-professional").assertIsDisplayed()
        screenshot("doctor-overview-normal")
        click("overview-source-demo-asha-latest")
        ui.onNodeWithTag("overview-source-text").assertTextEquals("Exact fictional source for demo-asha-latest.")
        ui.onNodeWithText("Old preference must not be invented").assertDoesNotExist()
        screenshot("doctor-overview-latest-source")
        click("overview-history-toggle")
        ui.onNodeWithTag("overview-history-demo-asha-latest").assertDoesNotExist()
        click("overview-history-demo-asha-older")
        ui.onNodeWithTag("overview-source-text").assertTextEquals("Exact fictional source for demo-asha-older.")
        listOf("overview-progress", "overview-reports", "demo-schedule-visit", "overview-conversations").forEach(::click)
        assertEquals(listOf("Progress", "Reports", "Schedule", "Conversations"), actions)
    }

    @Test fun largeTextOverviewKeepsUnknownFieldsAndResetsExpandedSourcesWhenPatientChanges() {
        val model = mutableStateOf(overview())
        render(model, 1.5f, mutableListOf())
        ui.onNodeWithTag("overview-last-discussion").performScrollTo().assertExists()
        click("overview-discussion-details-toggle")
        ui.onNode(hasText("What matters to the patient")).assertExists()
        ui.onAllNodes(hasText("Not recorded") and hasAnyAncestor(hasTestTag("overview-last-discussion"))).assertCountEquals(4)
        click("overview-source-demo-asha-latest")
        click("overview-history-toggle")
        ui.runOnIdle { model.value = overview("demo-ravi") }
        ui.onNodeWithTag("overview-source-text").assertDoesNotExist()
        ui.onNodeWithTag("overview-history-demo-ravi-older").assertDoesNotExist()
        click("overview-source-demo-ravi-latest")
        ui.onNodeWithTag("overview-source-text").assertTextEquals("Exact fictional source for demo-ravi-latest.")
        ui.onNodeWithText("Exact fictional source for demo-asha-latest.").assertDoesNotExist()
        ui.onNodeWithTag("overview-source-demo-ravi-latest").assertHeightIsAtLeast(48.dp)
        ui.onNodeWithTag("overview-previous-professional").performScrollTo()
        screenshot("doctor-overview-large-text-handover")
        ui.runOnIdle { model.value = DoctorOverviewData.build("demo-ravi", emptyList(), emptyList(), now)!! }
        ui.onNodeWithTag("overview-last-discussion").performScrollTo()
        ui.onNodeWithText("Discussion author: Not recorded").assertExists()
        ui.onNode(hasText("Date not recorded") and hasAnyAncestor(hasTestTag("overview-last-discussion"))).assertExists()
        val shortWrappingText="A fictional discussion needs a comfortable explanation. ".repeat(3).trim()
        assertTrue(shortWrappingText.length<240)
        ui.runOnIdle {
            val wrapped=version("demo-ravi","short-wrapped",3)
            model.value=DoctorOverviewData.build("demo-ravi",listOf(wrapped.copy(draft=wrapped.draft.copy(topics=shortWrappingText))),emptyList(),now)!!
        }
        ui.onNodeWithText("Read full field").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        ui.onNodeWithText(shortWrappingText).performScrollTo().performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
        assertTrue(layouts.single().lineCount>4)
        assertFalse(layouts.single().multiParagraph.didExceedMaxLines)
    }

    private fun render(model: MutableState<DoctorPatientOverview>, scale: Float, actions: MutableList<String>) {
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                CareTheme { Column(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    DoctorOverviewContent(model.value, { actions += "Conversations" }, { actions += it }, { actions += "Schedule" })
                } }
            }
        }
    }
    private fun click(tag: String) { ui.onNodeWithTag(tag).performScrollTo().assertHeightIsAtLeast(48.dp).performClick() }
    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(500); ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        target.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
