package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Stateless card only; no model, account or stored patient records. */
class DoctorCheckInHeaderTest {
    @get:Rule val ui=createComposeRule()
    @Test fun dateAndMoodChipRowKeepsFullTextAndOpensAtLargeFont() {
        val at=Instant.parse("2026-10-23T10:30:00Z").toEpochMilli();var opened=0
        val report=CareItem("strip-fixture","person-1","checkin","An okay day","Complete source text, unchanged.",created=at)
        ui.setContent {val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,1.5f)){CareTheme {Box(Modifier.width(320.dp).safeDrawingPadding()){DoctorCheckInCard(report,ZoneId.of("UTC"),at+3_600_000){opened++}}}}}
        ui.onNodeWithText("Today · 10:30 AM",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithTag("doctor-checkin-date-strip-fixture",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithTag("doctor-checkin-mood-strip-fixture",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithText("An okay day",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithText("Complete source text, unchanged.",useUnmergedTree=true).assertIsDisplayed()
        val card=ui.onNodeWithTag("doctor-checkin-strip-fixture").fetchSemanticsNode().boundsInRoot
        assertTrue(card.height>=80f)
        ui.onNodeWithTag("doctor-checkin-strip-fixture").performClick();assertEquals(1,opened)
    }
}
