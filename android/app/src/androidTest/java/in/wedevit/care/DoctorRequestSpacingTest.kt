package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Stateless request controls only; no workflow store, records or status writes. */
class DoctorRequestSpacingTest {
    @get:Rule val ui=createComposeRule()
    @Test fun narrowFiltersWrapWithVisibleRowGapAndKeepCallbacks() {
        var picked="All";var back=0
        ui.setContent { val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {CareTheme {Column(Modifier.width(320.dp).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            DoctorRequestQueueHeader {back++}
            DoctorRequestFilters("All") {picked=it}
        }}} }
        val all=ui.onNodeWithTag("workflow-filter-All").fetchSemanticsNode().boundsInRoot
        val completed=ui.onNodeWithTag("workflow-filter-Completed").fetchSemanticsNode().boundsInRoot
        assertTrue(completed.top>all.bottom)
        val chips=listOf("All","New","Accepted","Scheduled","Deferred","Completed").map {ui.onNodeWithTag("workflow-filter-$it").fetchSemanticsNode().boundsInRoot}
        chips.zipWithNext().filter {(a,b)->b.top>a.top}.forEach {(a,b)->assertTrue(b.top-a.bottom>=9*ui.density.density)}
        ui.onNodeWithTag("workflow-filter-Completed").performClick();assertEquals("Completed",picked)
        ui.onNodeWithTag("workflow-back").performClick();assertEquals(1,back)
    }
}
