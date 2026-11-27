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
    @Test fun searchAndStatusDropdownKeepCallbacks() {
        var picked="All";var back=0;var typed=""
        ui.setContent { val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)) {CareTheme {Column(Modifier.width(320.dp).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            DoctorRequestQueueHeader {back++}
            DoctorRequestFilters("All", {picked=it}, typed, {typed=it})
        }}} }
        ui.onNodeWithTag("workflow-request-search").assertExists()
        ui.onNodeWithTag("workflow-request-status").performClick()
        ui.onNodeWithTag("workflow-filter-Completed").performClick();assertEquals("Completed",picked)
        ui.onNodeWithTag("workflow-back").performClick();assertEquals(1,back)
    }
}
