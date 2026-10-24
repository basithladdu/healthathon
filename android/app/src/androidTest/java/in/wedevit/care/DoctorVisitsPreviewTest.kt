package `in`.wedevit.care

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver

/** Stateless synthetic card/detail fixtures, without account or booking writes. */
class DoctorVisitsPreviewTest {
    @get:Rule val ui=createComposeRule()
    @Test fun highlightedVisitUsesRequestedOrangeAndReadableDarkText() {
        org.junit.Assert.assertEquals(0xFFD97757.toInt(), DoctorVisitHighlight.toArgb())
        fun contrast(foreground: androidx.compose.ui.graphics.Color): Float {
            val light = DoctorVisitHighlight.luminance()
            val dark = foreground.compositeOver(DoctorVisitHighlight).luminance()
            return (light + .05f) / (dark + .05f)
        }
        org.junit.Assert.assertTrue(contrast(DoctorVisitHighlightInk) >= 4.5f)
        org.junit.Assert.assertTrue(contrast(DoctorVisitHighlightInk.copy(alpha = .85f)) >= 4.5f)
    }
    @Test fun timelineShowsRecordedDoctorAndPreservesFullStatusDetails() {
        val item=CareItem("visit-fixture","person-fixture","visit","Synthetic title",CareEntryCodec.encode(CareEntry("schedule",category="Visit",detail="Brief fictional discussion",sender="Fictional clinician",status="cancelled",timed=true)),due=1_792_728_000_000)
        ui.setContent {var open by remember {mutableStateOf(false)};CareTheme {
            if(open)DoctorVisitDetailsContent(item,false,false,onBack={open=false},onEdit={},onStatus={})
            else DoctorVisitsContent(listOf(item),false,0,onOpen={open=true},onCalendar={},onAdd={})
        }}
        ui.onNodeWithText("Brief fictional discussion").assertExists()
        ui.onNodeWithText("Cancelled").assertDoesNotExist()
        ui.onNodeWithText("Fictional clinician").assertExists()
        ui.onNodeWithTag("doctor-visit-card-visit-fixture").performScrollTo().performClick()
        ui.onNodeWithText("Fictional clinician").assertExists()
        ui.onNodeWithText("Cancelled").assertExists()
        ui.onNodeWithTag("doctor-visit-details").performScrollToNode(hasText("Visit notes"))
        ui.onNodeWithText("Brief fictional discussion").assertExists()
    }
    @Test fun largeTextTimelineRemainsReadableAndOpensExactVisit() {
        val first=CareItem("timeline-first","person-fixture","visit","Synthetic title",CareEntryCodec.encode(CareEntry("schedule",category="Visit",detail="A brief fictional care discussion",sender="Fictional clinician A",timed=true)),due=1_792_728_000_000)
        val older=first.copy(id="timeline-older",detail=CareEntryCodec.encode(CareEntry("schedule",category="Visit",detail="An earlier fictional review",sender="Fictional clinician B",timed=true)),due=first.due-86_400_000)
        var opened=""
        ui.setContent { val density=LocalDensity.current; CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) { CareTheme { DoctorVisitsContent(listOf(first,older),false,0,onOpen={opened=it.id},onCalendar={},onAdd={}) } } }
        ui.onNodeWithTag("doctor-visit-card-timeline-first").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Fictional clinician A").assertExists()
        ui.onNodeWithTag("doctor-visit-card-timeline-older").performScrollTo().performClick()
        org.junit.Assert.assertEquals("timeline-older",opened)
        ui.mainClock.advanceTimeBy(600);ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val output=File(instrumentation.targetContext.getExternalFilesDir(null),"verification/doctor-visits-timeline-large.png")
        output.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        output.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
}
