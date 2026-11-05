package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Synthetic presentation fixtures: no CareModel, sign-in or user-data writes. */
class DoctorVisitAndTeamContentTest {
    @get:Rule val ui = createComposeRule()
    private fun visit(scale: Float, writable: Boolean) {
        var saves = 0
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                CareTheme { Box(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding()) {
                    DoctorVisitForm("QA synthetic clinician", {}, "Clinic", {}, "Synthetic follow-up", {}, "Fri, 23 Oct 2026", "10:30 AM", "Asia/Kolkata", false, false, writable, null, {}, {}, { saves++ }, {})
                } }
            }
        }
        ui.onNodeWithText("Plan a visit").assertIsDisplayed()
        screenshot("doctor-visit-form-$scale")
        ui.onNodeWithTag("doctor-save-real-visit").performScrollTo()
        if (writable) { ui.onNodeWithTag("doctor-save-real-visit").assertIsEnabled().performClick(); assertEquals(1, saves) }
        else { ui.onNodeWithTag("doctor-save-real-visit").assertIsNotEnabled(); assertEquals(0, saves) }
        screenshot("doctor-visit-form-bottom-$scale")
    }
    @Test fun visitNormalText() = visit(1f, true)
    @Test fun visitLargeTextReadOnlyCannotSave() = visit(1.5f, false)
    @Test fun teamEmptyStateIsHonest() {
        ui.setContent { CareTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DoctorCareTeamContent(emptyList(), true, false, null, {}) } } }
        ui.onNodeWithText("No contacts yet").assertIsDisplayed()
        screenshot("doctor-care-team-empty")
    }
    @Test fun sharedTeamLongListRemainsReadableAtLargeText() {
        val contacts=(1..12).map { CareContact("qa-$it", "qa-person", "Synthetic contact $it", "Sibling", "family") }
        ui.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) { CareTheme { Box(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding()) { DoctorCareTeamContent(contacts,true,false,null,{}) } } }
        }
        ui.onNodeWithText("Synthetic contact 1").assertIsDisplayed()
        screenshot("doctor-care-team-shared-large")
        ui.onNodeWithText("Synthetic contact 12").performScrollTo().assertIsDisplayed()
        screenshot("doctor-care-team-shared-bottom")
    }
    @Test fun notSharedStateDoesNotRenderPrivateContacts() {
        ui.setContent { CareTheme { DoctorCareTeamContent(listOf(CareContact("qa", "qa-person", "Hidden synthetic contact", "Sibling", "family")),false,false,null,{}) } }
        ui.onNodeWithText("Hidden synthetic contact").assertDoesNotExist()
        ui.onNodeWithText("Contacts not shared").assertExists()
    }
    private fun screenshot(name:String) {
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
