package `in`.wedevit.care

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Actual mock routes, unique fictional preferences; no CareRoot or document transfers. */
class DoctorReportsUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun filteredReportsOpenExactSummaryAndBackResetsAcrossPatientsAtLargeText() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val suffix=UUID.randomUUID().toString()
        val demoName="doctor-demo-reports-$suffix"
        val workflowName="doctor-workflow-reports-$suffix"
        val demo=DoctorDemoStore(context,preferencesName=demoName)
        val workflow=DoctorWorkflowStore(context,preferencesName=workflowName)
        try {
            ui.setContent {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {CareTheme {DoctorDemo({},demo,workflow)}}
            }
            ui.onNodeWithTag("demo-login").performScrollTo().performClick()
            ui.onNodeWithTag("demo-nav-Patients").performClick()
            patient("Leela","demo-leela")
            reports()
            ui.onNodeWithTag("reports-search").performScrollTo().performTextReplacement("no-such-summary")
            Espresso.closeSoftKeyboard()
            ui.onNodeWithTag("reports-empty").assertExists()
            ui.onNodeWithContentDescription("Clear search").performScrollTo().performClick()
            ui.onNodeWithTag("reports-category-SCANS").performScrollTo().performClick()
            ui.onNodeWithTag("reports-empty").assertExists()
            ui.onNodeWithText("Show all summaries").performScrollTo().performClick()
            ui.onNodeWithTag("reports-search").performScrollTo().performTextReplacement("laboratory")
            Espresso.closeSoftKeyboard()
            val report=DoctorReportsData.reports("demo-leela").single()
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("report-open-${report.id}"))
            screenshot("doctor-reports-browser-large")
            ui.onNodeWithTag("report-open-${report.id}").performClick()
            ui.onNodeWithTag("reports-detail-page").assertExists()
            ui.onNodeWithTag("report-exact-summary").assertTextEquals(report.summary)
            ui.onNode(hasText("Download",substring=false) and hasClickAction()).assertDoesNotExist()
            screenshot("doctor-reports-detail-large")
            Espresso.pressBack()
            ui.onNodeWithTag("reports-browser").assertExists()
            ui.onNodeWithTag("reports-search").assertTextContains("laboratory")
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("report-open-${report.id}"))
            ui.onNodeWithTag("report-open-${report.id}").performClick()
            ui.onNodeWithTag("demo-patient-back").performClick()
            ui.onNodeWithTag("reports-browser").assertExists()
            Espresso.pressBack()
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            Espresso.pressBack()
            patient("Ravi","demo-ravi")
            reports()
            ui.onNodeWithTag("reports-search").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString("")))
            ui.onNodeWithTag("reports-detail-page").assertDoesNotExist()
            ui.onNodeWithTag("report-open-${report.id}").assertDoesNotExist()
            val ravi=DoctorReportsData.reports("demo-ravi").single()
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("report-open-${ravi.id}"))
            ui.onNodeWithTag("report-open-${ravi.id}").performClick()
            ui.onNodeWithTag("report-exact-summary").assertTextEquals(ravi.summary)
        } finally {
            context.getSharedPreferences(demoName,Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(workflowName,Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun patient(name:String,id:String) {
        ui.onNodeWithTag("demo-patient-search").performTextReplacement(name);Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("demo-patient-$id").performClick()
    }
    private fun reports() {
        ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-Reports"))
        ui.onNodeWithTag("demo-record-Reports").performClick()
    }
    private fun screenshot(name:String) {
        ui.mainClock.advanceTimeBy(600);ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/$name.png")
        target.parentFile!!.mkdirs();val bitmap=instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
