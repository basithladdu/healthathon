package `in`.wedevit.care

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Stateless fictional patient cards only: no app account, storage, clinical data or network. */
class DoctorPatientGridTest {
    @get:Rule val ui = createComposeRule()

    @Test fun normalGridKeepsSixPatientsInOrderAndEveryCardOpensItsOwnPatient() {
        val opened = mutableListOf<String>()
        content { DemoPatients(DoctorDemoData.patients) { opened += it } }
        ui.onNodeWithText("6 patient records").assertIsDisplayed()
        val first = card("demo-asha").fetchSemanticsNode().boundsInRoot
        val second = card("demo-ravi").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue("Normal width should use two columns", second.left >= first.right)
        screenshot("doctor-patient-grid-normal")
        DoctorDemoData.patients.forEach { patient ->
            ui.onNodeWithTag("demo-patients-list").performScrollToNode(hasTestTag("demo-patient-${patient.id}"))
            card(patient.id).assertHasClickAction().performClick()
        }
        assertEquals(DoctorDemoData.patients.map { it.id }, opened)
    }

    @Test fun nameAndCareContextSearchUpdateCountAndEmptyResultCanBeCleared() {
        content { DemoPatients(DoctorDemoData.patients) {} }
        ui.onNodeWithTag("demo-patient-search").performTextInput("  rAvI  ")
        ui.onNodeWithText("1 patient records").assertExists()
        card("demo-ravi").assertExists()
        card("demo-asha").assertDoesNotExist()
        ui.onNodeWithTag("demo-patient-search").performTextReplacement("caregiver handover")
        card("demo-maya").assertExists()
        card("demo-ravi").assertDoesNotExist()
        ui.onNodeWithTag("demo-patient-search").performTextReplacement("no such fictional patient")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithText("0 patient records").assertExists()
        ui.onNodeWithTag("demo-patients-list").performScrollToNode(hasText("No matching patients"))
        ui.onNodeWithText("No matching patients").assertIsDisplayed()
        ui.onNodeWithTag("demo-patients-list").performScrollToNode(hasText("Clear search"))
        ui.onNodeWithText("Clear search").performClick()
        ui.onNodeWithText("6 patient records").assertExists()
        card("demo-asha").assertExists()
    }

    @Test fun narrowWidthAndLargeTextUseOneColumnWithoutTruncatingPatientIdentity() {
        val width = mutableStateOf(320.dp)
        val scale = mutableFloatStateOf(1f)
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                CareTheme { Box(Modifier.width(width.value).fillMaxSize().safeDrawingPadding()) { DemoPatients(DoctorDemoData.patients) {} } }
            }
        }
        assertSingleColumn()
        screenshot("doctor-patient-grid-narrow")
        ui.runOnIdle { width.value = 360.dp; scale.floatValue = 1.5f }
        assertSingleColumn()
        DoctorDemoData.patients.forEach { patient ->
            ui.onNodeWithTag("demo-patients-list").performScrollToNode(hasTestTag("demo-patient-${patient.id}"))
            card(patient.id).assertIsDisplayed().assertContentDescriptionEquals("${patient.name.removeSuffix(" Demo")}, ${patient.age} years, ${patient.careContext}")
            val layouts = mutableListOf<TextLayoutResult>()
            ui.onNodeWithText(patient.careContext, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val fits = layouts.isNotEmpty() && layouts.all { layout ->
                val last = layout.getBoundingBox(layout.layoutInput.text.length - 1)
                !layout.didOverflowHeight && !layout.multiParagraph.didExceedMaxLines &&
                    (0 until layout.lineCount).all { line -> layout.getLineLeft(line) >= -1f && layout.getLineRight(line) <= layout.size.width + 1f } &&
                    last.right <= layout.size.width + 1f && last.bottom <= layout.size.height + 1f
            }
            if (!fits) {
                screenshot("doctor-patient-grid-overflow")
                println("Grid text layout ${patient.id}: " + layouts.joinToString { "size=${it.size}, lines=${it.lineCount}, lineRights=${(0 until it.lineCount).map(it::getLineRight)}, last=${it.getBoundingBox(it.layoutInput.text.length - 1)}, heightOverflow=${it.didOverflowHeight}" })
            }
            // TextLayoutResult.didOverflowWidth can flag subpixel intrinsic rounding. Check the
            // actual line/last-glyph geometry with at most one physical pixel of tolerance instead.
            assertTrue("Every care-context line and final glyph must fit the actual text bounds", fits)
        }
        ui.onNodeWithTag("demo-patients-list").performScrollToNode(hasTestTag("demo-patient-demo-asha"))
        screenshot("doctor-patient-grid-large-text")
    }

    private fun assertSingleColumn() {
        ui.onNodeWithTag("demo-patients-list").performScrollToNode(hasTestTag("demo-patient-demo-asha"))
        val first = card("demo-asha").fetchSemanticsNode().boundsInRoot
        ui.onNodeWithTag("demo-patients-list").performScrollToNode(hasTestTag("demo-patient-demo-ravi"))
        val second = card("demo-ravi").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, 1f)
        assertTrue("One-column cards should have full available width", first.width > 250f)
    }
    private fun card(id: String) = ui.onNodeWithTag("demo-patient-$id")
    private fun content(body: @androidx.compose.runtime.Composable () -> Unit) {
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                CareTheme { Box(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding()) { body() } }
            }
        }
    }
    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(500)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
