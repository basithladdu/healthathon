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
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.ZoneId

/** Stateless layout fixtures only. No account, model or database writes. */
class DoctorDayBentoTest {
    @get:Rule val ui = createComposeRule()

    @Test fun normalTextUsesWideConditionAndStackedLabelledTools() {
        val opened = mutableListOf<String>()
        ui.setContent { CareTheme { Column(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
            DoctorDayTopCards(null, null, ZoneId.of("Asia/Kolkata"), {opened += "overview"}, {opened += "condition"}, {opened += "calendar"}, {opened += "team"}, {opened += "labs"})
        } } }
        val hero = ui.onNodeWithTag("doctor-care-overview").fetchSemanticsNode().boundsInRoot
        val condition = ui.onNodeWithTag("doctor-condition-today").fetchSemanticsNode().boundsInRoot
        val tools = ui.onNodeWithTag("doctor-care-tools").fetchSemanticsNode().boundsInRoot
        assertTrue(hero.bottom < condition.top)
        assertTrue(condition.right < tools.left)
        assertEquals(condition.top, tools.top, 1f)
        assertEquals(condition.height,tools.height,1f)
        assertEquals(1.5f,condition.width/tools.width,.02f)
        assertEquals(hero.left, condition.left, 1f)
        assertEquals(hero.right, tools.right, 1f)
        ui.onNodeWithText("No update today").assertExists()
        ui.onNodeWithTag("doctor-care-overview").performClick()
        ui.onNodeWithTag("doctor-condition-today").performClick()
        listOf("Calendar", "Care team", "Lab history").forEach {label ->
            ui.onNodeWithContentDescription(label).assertExists()
            ui.onNodeWithText(label).assertExists()
            ui.onNodeWithTag("doctor-shortcut-$label").assertHasClickAction().performClick()
        }
        assertEquals(listOf("overview", "condition", "calendar", "team", "labs"), opened)
        assertStackedTools()
        assertConditionBodyStartsTogether()
        val copy=ui.onNodeWithTag("doctor-condition-copy",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val image=ui.onNodeWithTag("doctor-condition-illustration",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val body=ui.onNodeWithTag("doctor-condition-body",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        // Decorative art is now a large faint background, not a reserved text column.
        assertTrue(image.width > condition.width * .65f)
        assertTrue(body.right > image.left)
        assertTrue(body.left >= copy.left && body.right <= copy.right)
        assertTrue(body.bottom <= condition.bottom)
        screenshot("doctor-bento-normal")
    }

    @Test fun largeTextStacksWholeCardsAndKeepsFullToolLabelsReachable() {
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CareTheme { Column(Modifier.width(320.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    DoctorDayTopCards(null, null, ZoneId.of("Asia/Kolkata"), {}, {}, {}, {}, {})
                } }
            }
        }
        val hero = ui.onNodeWithTag("doctor-care-overview").fetchSemanticsNode().boundsInRoot
        val condition = ui.onNodeWithTag("doctor-condition-today").fetchSemanticsNode().boundsInRoot
        assertTrue(condition.top > hero.top)
        assertEquals(hero.left, condition.left, 1f)
        screenshot("doctor-bento-large-text-top")
        ui.onNodeWithTag("doctor-condition-body",useUnmergedTree=true).performScrollTo()
        assertConditionBodyStartsTogether()
        listOf("Calendar", "Care team", "Lab history").forEach {label ->
            ui.onNodeWithTag("doctor-shortcut-$label").performScrollTo().assertIsDisplayed().assertHasClickAction()
            ui.onNodeWithText(label).assertExists()
        }
        screenshot("doctor-bento-large-text-tools")
        assertStackedTools()
    }

    @Test fun narrow115FontStacksLowerCardsWithReadableLabelsAnd48DpControls() {
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.15f)) {
                CareTheme { Column(Modifier.width(340.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
                    DoctorDayTopCards(null, null, ZoneId.of("Asia/Kolkata"), {}, {}, {}, {}, {})
                } }
            }
        }
        val hero = ui.onNodeWithTag("doctor-care-overview").fetchSemanticsNode().boundsInRoot
        val condition = ui.onNodeWithTag("doctor-condition-today").fetchSemanticsNode().boundsInRoot
        val tools = ui.onNodeWithTag("doctor-care-tools").fetchSemanticsNode().boundsInRoot
        assertTrue(hero.bottom < condition.top)
        assertEquals(hero.left,condition.left,1f)
        assertEquals(condition.width,tools.width,1f)
        ui.onNodeWithText("Reported by the patient or family.",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        val source=ui.onNodeWithText("Reported by the patient or family.",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val visibleCondition=ui.onNodeWithTag("doctor-condition-today").fetchSemanticsNode().boundsInRoot
        assertTrue(source.bottom<=visibleCondition.bottom)
        assertConditionBodyStartsTogether()
        ui.onNodeWithTag("doctor-care-tools").performScrollTo().assertIsDisplayed()
        listOf("Calendar","Care team","Lab history").forEach {ui.onNodeWithText(it).assertIsDisplayed()}
        val visibleTools=ui.onNodeWithTag("doctor-care-tools").fetchSemanticsNode().boundsInRoot
        val buttons = listOf("Calendar", "Care team", "Lab history").map { ui.onNodeWithTag("doctor-shortcut-$it").fetchSemanticsNode().boundsInRoot }
        val touchPx=48*InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(buttons.all {it.height>=touchPx-1&&it.width>=touchPx-1})
        assertTrue(buttons.zipWithNext().all {(a,b)->b.top>=a.bottom-1})
        assertTrue(buttons.all {it.left>=visibleTools.left&&it.right<=visibleTools.right&&it.bottom<=visibleTools.bottom})
        assertStackedTools()
        screenshot("doctor-bento-boundary115")
    }

    @Test fun narrowNormalTextStacksOnlyLowerPairBelowFullWidthHero() {
        ui.setContent { CareTheme { Column(Modifier.width(320.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
            DoctorDayTopCards(null, null, ZoneId.of("Asia/Kolkata"), {}, {}, {}, {}, {})
        } } }
        val hero = ui.onNodeWithTag("doctor-care-overview").fetchSemanticsNode().boundsInRoot
        val condition = ui.onNodeWithTag("doctor-condition-today").fetchSemanticsNode().boundsInRoot
        val tools = ui.onNodeWithTag("doctor-care-tools").fetchSemanticsNode().boundsInRoot
        assertTrue(hero.bottom < condition.top)
        assertTrue(condition.bottom < tools.top)
        assertEquals(hero.width, condition.width, 1f)
        assertEquals(condition.width, tools.width, 1f)
        assertConditionBodyStartsTogether()
        ui.onNodeWithTag("doctor-care-tools").performScrollTo()
        assertStackedTools()
    }

    private fun assertConditionBodyStartsTogether() {
        val body=ui.onNodeWithTag("doctor-condition-body",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        listOf("mood","time","source").forEach {part->
            val text=ui.onNodeWithTag("doctor-condition-$part",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertEquals(body.left,text.left,1f)
        }
    }
    /** Shortcuts are labelled rows in one vertical stack: document order, increasing top, 48dp targets, inside the tools card. */
    private fun assertStackedTools() {
        val tools=ui.onNodeWithTag("doctor-care-tools").fetchSemanticsNode().boundsInRoot
        val labels=listOf("Calendar","Care team","Lab history")
        val rows=labels.map {ui.onNodeWithTag("doctor-shortcut-$it").fetchSemanticsNode().boundsInRoot}
        val touchPx=48*InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        // Each row has a visible text label as well as its content description.
        labels.forEach {ui.onNodeWithText(it).assertExists();ui.onNodeWithContentDescription(it).assertExists()}
        rows.zipWithNext().forEach {(a,b)->assertTrue(b.top>=a.bottom-1f);assertTrue(b.top>a.top)}
        rows.forEach {assertTrue(it.height>=touchPx-1f);assertTrue(it.left>=tools.left&&it.right<=tools.right&&it.top>=tools.top&&it.bottom<=tools.bottom)}
        // Rows are full-width and share one left edge and width, so they read as a single column.
        rows.forEach {assertEquals(rows[0].left,it.left,1f);assertEquals(rows[0].width,it.width,1f)}
    }

    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(600)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
