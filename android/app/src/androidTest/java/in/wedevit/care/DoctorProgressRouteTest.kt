package `in`.wedevit.care

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real mock routes with isolated preferences; never CareRoot/auth/microphone. */
class DoctorProgressRouteTest {
    @get:Rule val ui=createComposeRule()
    @Test fun reportBackPreservesProgressScrollAndPatientScopeAtLargeText() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val suffix=UUID.randomUUID().toString()
        val demoName="doctor-demo-progress-route-$suffix"
        val workflowName="doctor-workflow-progress-route-$suffix"
        val demo=DoctorDemoStore(context,preferencesName=demoName)
        val workflow=DoctorWorkflowStore(context,preferencesName=workflowName)
        val fontScale=mutableFloatStateOf(1f)
        try {
            ui.setContent {
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale.floatValue)) {CareTheme {DoctorDemo({},demo,workflow)}}
            }
            ui.onNodeWithTag("demo-login").performScrollTo().performClick()
            ui.onNodeWithTag("demo-nav-Patients").performClick()
            openPatient("Leela","demo-leela")
            openProgress()
            val oldest="progress-report-demo-leela-check-1"
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag(oldest))
            val before=ui.onNodeWithTag(oldest).fetchSemanticsNode().boundsInRoot.top
            ui.onNodeWithTag(oldest).performClick()
            ui.onNodeWithTag("progress-report-detail-demo-leela-check-1").assertExists()
            ui.onNodeWithTag("progress-report-detail-demo-ravi-check-1").assertDoesNotExist()
            screenshot("doctor-progress-report-normal")
            Espresso.pressBack()
            ui.onNodeWithTag("doctor-progress-timeline").assertExists()
            ui.onNodeWithTag("progress-detail-page").assertDoesNotExist()
            ui.waitForIdle()
            assertEquals(before,ui.onNodeWithTag(oldest).fetchSemanticsNode().boundsInRoot.top,2f)
            ui.onNodeWithTag(oldest).performClick()
            ui.onNodeWithTag("demo-patient-back").performClick()
            ui.onNodeWithTag("doctor-progress-timeline").assertExists()
            Espresso.pressBack()
            ui.onNodeWithTag("demo-patient-hub").assertExists()
            Espresso.pressBack()
            ui.onNodeWithTag("demo-patients-list").assertExists()
            openPatient("Ravi","demo-ravi")
            openProgress()
            ui.onNodeWithTag("progress-detail-page").assertDoesNotExist()
            ui.onNodeWithTag("progress-report-detail-demo-leela-check-1").assertDoesNotExist()
            ui.runOnIdle {fontScale.floatValue=1.5f}
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("doctor-progress-timeline"))
            screenshot("doctor-progress-timeline-large")
            ui.onNodeWithTag("demo-patient-detail").performScrollToNode(hasTestTag("progress-report-demo-ravi-check-3"))
            ui.onNodeWithTag("progress-report-demo-ravi-check-3").performClick()
            ui.onNodeWithTag("progress-report-detail-demo-ravi-check-3").assertExists()
            screenshot("doctor-progress-report-large")
            assertTrue(workflow.versions("demo-leela").isEmpty())
            assertTrue(workflow.versions("demo-ravi").isEmpty())
        } finally {
            context.getSharedPreferences(demoName,Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences(workflowName,Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun openPatient(name:String,id:String) {
        ui.onNodeWithTag("demo-patient-search").performTextReplacement(name)
        Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("demo-patient-$id").performClick()
    }
    private fun openProgress() {
        ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-Progress"))
        ui.onNodeWithTag("demo-record-Progress").performClick()
    }
    private fun screenshot(name:String) {
        ui.mainClock.advanceTimeBy(600);ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
