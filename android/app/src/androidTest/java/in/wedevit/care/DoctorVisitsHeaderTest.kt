package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Stateless doctor-only header; no model, account, database or booking writes. */
class DoctorVisitsHeaderTest {
    @get:Rule val ui=createComposeRule()
    @Test fun planVisitRemainsReachableAndCalendarActionIsAbsent() {
        var planned=0;var calendar=0
        ui.setContent {CareTheme {DoctorVisitsContent(emptyList(),true,0,onOpen={},onCalendar={calendar++},onAdd={planned++})}}
        ui.onNodeWithText("Open calendar").assertDoesNotExist()
        ui.onNodeWithText("Plan visit").assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals(1,planned);assertEquals(0,calendar)
    }
}
