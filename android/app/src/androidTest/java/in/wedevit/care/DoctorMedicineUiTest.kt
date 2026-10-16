package `in`.wedevit.care

import androidx.compose.foundation.layout.*
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
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZoneId

class DoctorMedicineUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun normalCardsKeepThreeDistinctTimestampRowsAndHonestStatuses() = verifyCards(1f,"normal")
    @Test fun largeTextCardsKeepThreeDistinctTimestampRowsAndHonestStatuses() = verifyCards(1.5f,"large")
    private fun verifyCards(scale:Float,suffix:String) {
        val samples=DoctorPatientTracking.doses("demo-asha")
        ui.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                CareTheme {Column(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    DoctorMedicineContent(samples,DoctorPatientTracking.asOf,ZoneId.of("UTC"))
                }}
            }
        }
        samples.forEach {dose ->
            val card=hasAnyAncestor(hasTestTag("doctor-medicine-${dose.id}"))
            listOf("Planned","Reported taken","Logged").forEach {label->ui.onNode(hasText(label) and card).performScrollTo().assertIsDisplayed()}
        }
        val skipped=hasAnyAncestor(hasTestTag("doctor-medicine-demo-asha-dose-3"))
        ui.onNode(hasText("Not reported") and skipped).assertExists()
        ui.onNode(hasText("Skipped reported") and skipped).assertExists()
        ui.onNodeWithTag("doctor-medicine-demo-asha-dose-1").performScrollTo()
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val target=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/doctor-medicine-$suffix.png")
        target.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
