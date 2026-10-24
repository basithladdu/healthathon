package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** Actual synthetic timestamps/categories only; the accessible list remains the detail action. */
class DoctorConditionColumnsTest {
    @get:Rule val ui=createComposeRule()
    @Test fun columnsRetainAccessibleReportsWithoutChartFooterOrReportChips() {
        val now=1_792_728_000_000L
        val checks=listOf("A good day","An okay day","A hard day").mapIndexed {i,title->CareItem("column-$i","fixture","checkin",title,"Fictional report $i",created=now-i*86_400_000)}
        ui.setContent {CareTheme {DoctorCheckInHistory("fixture",checks,now,ZoneId.of("UTC"))}}
        ui.onNodeWithText("October 2026").assertExists()
        ui.onNode(hasContentDescription("3 actual reports",substring=true)).assertExists()
        ui.onNodeWithText("Choose a report to read its details").assertDoesNotExist()
        ui.onNodeWithTag("doctor-chart-report-column-0").assertDoesNotExist()
        ui.onNodeWithTag("doctor-checkin-column-0").performScrollTo().assertHasClickAction().performClick()
        ui.onAllNodesWithText("Fictional report 0").onLast().assertIsDisplayed()
    }
}
