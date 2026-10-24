package `in`.wedevit.care

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class DoctorMedicineReportUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun reportCardShowsThreeDifferentTimesAndReporterWithoutVerificationClaim() {
        val event=MedicineIntakeEvent("fixture","fixture-medicine","Paracetamol 500 mg","taken",plannedAt=1000,reportedTakenAt=2000,loggedAt=3000,reporter="Family report")
        ui.setContent{CareTheme{Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())){MedicineReportCard(event,listOf(event),4000)}}}
        ui.onNodeWithText("Late reported").assertExists()
        ui.onNodeWithText("Planned:",substring=true).assertExists()
        ui.onNodeWithText("Reported taken:",substring=true).assertExists()
        ui.onNodeWithText("Logged:",substring=true).assertExists()
        ui.onNodeWithText("Reporter: Family report").assertExists()
    }
    @Test fun dateOnlyLegacyReportKeepsUnknownTimeExplicit() {
        val event=MedicineIntakeEvent("fixture","fixture-medicine","Paracetamol 500 mg","taken",reportedDate="2026-10-23",loggedAt=3000,reporter="Reporter not recorded",source="daily-checkoff")
        ui.setContent{CareTheme{Column(Modifier.fillMaxSize().safeDrawingPadding()){MedicineReportCard(event,listOf(event),4000)}}}
        ui.onNodeWithText("Reported taken: 2026-10-23 · exact time not recorded").assertExists()
        ui.onNodeWithText("Planned: Not recorded").assertExists()
    }
    @Test fun tabsWrapAndLinkedPlanNeverShowsNotLoggedAtLargeFont() {
        val plannedAt=Instant.parse("2026-10-23T07:30:00Z").toEpochMilli()
        val plan=MedicineIntakeEvent("fixture-plan","fixture-medicine","Paracetamol 500 mg","planned",plannedAt=plannedAt,loggedAt=plannedAt+3_600_000)
        val taken=plan.copy(id="fixture-report",outcome="taken",reportedTakenAt=plannedAt+1_800_000,loggedAt=plannedAt+2_700_000)
        ui.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {
                CareTheme {Column(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    MedicineHistoryTabs("Intake history",false){}
                    MedicineReportCard(plan,listOf(plan,taken),plannedAt+7_200_000)
                }}
            }
        }
        listOf("Current","Past").forEach{ui.onNodeWithTag("medicine-tab-$it").assertIsDisplayed().assertHasClickAction()}
        ui.onNodeWithTag("medicine-tab-Intake history").assertDoesNotExist()
        ui.onNodeWithText("Plan followed by a report").assertExists()
        ui.onNodeWithText("Not logged").assertDoesNotExist()
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/doctor-medicine-tabs-report-large-text.png")
        file.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }
}
