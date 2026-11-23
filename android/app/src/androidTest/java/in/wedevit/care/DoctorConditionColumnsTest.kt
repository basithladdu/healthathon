package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** Actual synthetic timestamps/categories only; the condensed check-in list (grouped by month) opens each report's details. */
class DoctorConditionColumnsTest {
    @get:Rule val ui=createComposeRule()
    @Test fun checkInHistoryListsActualReportsByMonthAndOpensTheirDetails() {
        val now=1_792_728_000_000L
        val checks=listOf("A good day","An okay day","A hard day").mapIndexed {i,title->CareItem("column-$i","fixture","checkin",title,"Fictional report $i",created=now-i*86_400_000)}
        ui.setContent {CareTheme {DoctorCheckInHistory("fixture",checks,now,ZoneId.of("UTC"))}}
        val list=ui.onNodeWithTag("doctor-checkin-history")
        list.performScrollToNode(hasTestTag("doctor-checkin-count"))
        ui.onNodeWithTag("doctor-checkin-count").assertTextEquals("Showing all 3 updates")
        list.performScrollToNode(hasTestTag("doctor-checkin-month-2026-10"))
        ui.onNodeWithTag("doctor-checkin-month-2026-10").assertTextEquals("October 2026")
        ui.onNodeWithTag("doctor-chart-report-column-0").assertDoesNotExist()
        list.performScrollToNode(hasTestTag("doctor-checkin-column-0"))
        ui.onNodeWithTag("doctor-checkin-column-0").assertHasClickAction().performClick()
        ui.onAllNodesWithText("Fictional report 0").onLast().assertIsDisplayed()
    }
}
