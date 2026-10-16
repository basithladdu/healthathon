package `in`.wedevit.care

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Fictional empty schedule only; no appointment writes, authentication or actual patient access. */
class DoctorScheduleCompactTest {
    @get:Rule val ui = createComposeRule()

    @Test fun compactFiveDayStripKeepsDateNavigationAndAccessibleTargets() {
        val initial = LocalDate.of(2026, 10, 16)
        val selected = mutableStateOf(initial)
        var added = false
        ui.setContent { CareTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DemoSchedule(DoctorDemoData.patients, emptyList(), selected.value, { selected.value = it }, {}, { added = true }, {}, {}, {}) } } }
        (-2L..2L).forEach { offset ->
            ui.onNodeWithTag("demo-schedule-list").performScrollToNode(hasTestTag("demo-day-${initial.plusDays(offset)}"))
            ui.onNodeWithTag("demo-day-${initial.plusDays(offset)}").assertHasClickAction().assertHeightIsAtLeast(48.dp)
        }
        ui.onNodeWithTag("demo-schedule-list").performScrollToNode(hasTestTag("demo-add-visit"))
        ui.onNodeWithTag("demo-add-visit").assertHeightIsAtLeast(48.dp)
        screenshot()
        ui.onNodeWithTag("demo-schedule-list").performScrollToNode(hasContentDescription("Previous day"))
        ui.onNodeWithContentDescription("Previous day").performClick()
        ui.runOnIdle { assertEquals(initial.minusDays(1), selected.value) }
        ui.onNodeWithContentDescription("Next day").performClick()
        ui.runOnIdle { assertEquals(initial, selected.value) }
        ui.onNodeWithTag("demo-day-${initial.plusDays(2)}").performClick()
        ui.runOnIdle { assertEquals(initial.plusDays(2), selected.value) }
        ui.onNodeWithTag("demo-schedule-date").assertTextEquals("Sun, 18 Oct 2026")
        ui.onNodeWithTag("demo-schedule-list").performScrollToNode(hasTestTag("demo-add-visit"))
        ui.onNodeWithTag("demo-add-visit").performClick()
        ui.runOnIdle { assertTrue(added); assertEquals(initial.plusDays(2), selected.value) }
    }

    private fun screenshot() {
        ui.mainClock.advanceTimeBy(500)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/doctor-schedule-compact.png")
        target.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
