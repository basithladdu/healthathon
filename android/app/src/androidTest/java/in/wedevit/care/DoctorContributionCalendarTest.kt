package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DoctorContributionCalendarTest {
    @get:Rule val ui = createComposeRule()
    @Test fun calendarShowsWeekdaysWithoutMoodAxisOrReportChips() {
        val now = Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
        val checks = listOf("A good day", "An okay day", "A hard day").mapIndexed { i, title ->
            CareItem("calendar-$i", "fixture", "checkin", title, created = now - i * 86_400_000)
        }
        ui.setContent { CareTheme { DoctorContributionChart(checks, ZoneId.of("UTC"), now) } }
        ui.onNodeWithText("October 2026").assertExists()
        listOf("August","September","Aug","Sep").forEach {ui.onNodeWithText(it,substring=true).assertDoesNotExist()}
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { ui.onNodeWithText(it).assertExists() }
        ui.onNodeWithTag("doctor-condition-chart").assertExists()
        ui.onNode(hasContentDescription("3 actual reports", substring = true)).assertExists()
        ui.onNode(hasContentDescription("Not a clinical score", substring = true)).assertExists()
        listOf("Good day", "Okay day", "Hard day").forEach { ui.onNodeWithText(it).assertDoesNotExist() }
    }
}
