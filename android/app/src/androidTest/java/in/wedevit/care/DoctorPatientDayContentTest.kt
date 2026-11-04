package `in`.wedevit.care

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.io.File

/** Stateless presentation fixtures; no CareModel, account or live database. */
class DoctorPatientDayContentTest {
    @Test fun toolsAreLabelledAccessibleRowActions() {
        val opened = mutableListOf<String>()
        ui.setContent { CareTheme { DoctorDayTopCards(null, null, ZoneId.of("UTC"), {}, {}, {opened += "calendar"}, {opened += "team"}, {opened += "labs"}) } }
        listOf("Calendar", "Care team", "Lab history").forEach { ui.onNodeWithContentDescription(it).assertHasClickAction().performClick() }
        listOf("Calendar", "Care team", "Lab history").forEach { ui.onNodeWithText(it, useUnmergedTree = true).assertIsDisplayed() }
        assertEquals(listOf("calendar", "team", "labs"), opened)
        val calendar = ui.onNodeWithTag("doctor-shortcut-Calendar").fetchSemanticsNode().boundsInRoot
        val team = ui.onNodeWithTag("doctor-shortcut-Care team").fetchSemanticsNode().boundsInRoot
        val labs = ui.onNodeWithTag("doctor-shortcut-Lab history").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(team.top >= calendar.bottom - 1f && labs.top >= team.bottom - 1f)
    }
    @get:Rule val ui = createComposeRule()
    private val now = Instant.parse("2026-10-23T06:30:00Z").toEpochMilli()

    @Test fun doctorOverviewKeepsFocusedSectionsAndDispatchesToolsAtLargeText() {
        val opened = mutableListOf<String>()
        ui.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                CareTheme { Box(Modifier.width(360.dp).fillMaxSize().safeDrawingPadding()) {
                    DoctorPatientDayContent(
                        person = Person("qa-person", "QA person"), entries = emptyList(), versions = emptyList(),
                        canEdit = true, now = now, zone = ZoneId.of("Asia/Kolkata"),
                        onOverview = { opened += "overview" }, onCheckIns = { opened += "checkins" },
                        onMedicines = { opened += "medicines" }, onCalendar = { opened += "calendar" },
                        onLabs = { opened += "labs" }, onCareTeam = { opened += "team" },
                        onAddVisit = { opened += "visit" }, onReadNote = { opened += "note" },
                    )
                } }
            }
        }
        listOf("Find care", "To do", "Journal", "Let's talk").forEach { ui.onNodeWithText(it).assertDoesNotExist() }
        screenshot("doctor-only-overview-large-text")
        ui.onNodeWithTag("doctor-medicine-summary").assertDoesNotExist()
        ui.onNodeWithText("Medicines today").assertDoesNotExist()
        listOf("doctor-care-overview" to "overview", "doctor-condition-today" to "checkins", "doctor-add-visit" to "visit").forEach { (tag, _) ->
            ui.onNodeWithTag("doctor-patient-day").performScrollToNode(hasTestTag(tag))
            ui.onNodeWithTag(tag).assertIsDisplayed().assertHasClickAction().performClick()
        }
        assertEquals(listOf("overview", "checkins", "visit"), opened)
        screenshot("doctor-only-overview-lower")
    }

    @Test fun conditionHistoryKeepsDatedReportsWithoutChartCard() {
        val entries = listOf(
            CareItem("good", "qa-person", "checkin", "A good day", "Walked to the shop and slept well", created = now - 86_400_000),
            CareItem("okay", "qa-person", "checkin", "An okay day", "Tired in the afternoon", created = now - 3_600_000),
            CareItem("hard", "qa-person", "checkin", "A hard day", "Pain kept returning today", created = now),
            CareItem("other", "other-person", "checkin", "A good day", "Other person's report", created = now),
        )
        ui.setContent { CareTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            DoctorCheckInHistory("qa-person", entries, now, ZoneId.of("Asia/Kolkata"))
        } } }
        ui.onNodeWithTag("doctor-condition-chart").assertDoesNotExist()
        ui.onNodeWithText("October 2026").assertExists()
        ui.onNodeWithTag("doctor-progress-info").assertExists()
        ui.onNodeWithTag("doctor-checkin-summary").assertExists()
        screenshot("doctor-condition-history")
        listOf("good", "okay", "hard").forEach {
            ui.onNodeWithTag("doctor-checkin-history").performScrollToNode(hasTestTag("doctor-checkin-$it"))
            ui.onNodeWithTag("doctor-checkin-$it").assertIsDisplayed()
        }
        ui.onNodeWithText("Other person's report").assertDoesNotExist()
        ui.onNodeWithTag("doctor-checkin-history").performScrollToNode(hasTestTag("doctor-checkin-search"))
        ui.onNodeWithTag("doctor-checkin-search").performTextInput("shop")
        ui.onNodeWithTag("doctor-checkin-count").assertTextContains("Showing 1 of 3 updates")
        ui.onNodeWithTag("doctor-checkin-okay").assertDoesNotExist()
        ui.onNodeWithTag("doctor-checkin-search").performTextReplacement("zzz")
        ui.onNodeWithTag("doctor-checkin-empty").assertExists()
        ui.onNodeWithTag("doctor-checkin-history").performScrollToNode(hasTestTag("doctor-checkin-clear-filters"))
        ui.onNodeWithTag("doctor-checkin-clear-filters").performClick()
        ui.onNodeWithTag("doctor-checkin-history").performScrollToNode(hasTestTag("doctor-checkin-filter-hard"))
        ui.onNodeWithTag("doctor-checkin-filter-hard").performClick()
        ui.onNodeWithTag("doctor-checkin-count").assertTextContains("Showing 1 of 3 updates")
        ui.onNodeWithTag("doctor-checkin-good").assertDoesNotExist()
        ui.onNodeWithTag("doctor-checkin-filter-hard").performClick()
        ui.onNodeWithTag("doctor-checkin-history").performScrollToNode(hasTestTag("doctor-checkin-good"))
        ui.onNodeWithTag("doctor-checkin-good").performClick()
        ui.onNodeWithText("Self-reported update; clinical assessment and reporter identity are not independently verified.").assertExists()
        ui.onNodeWithText("Close").performClick()
    }

    @Test fun glanceTilesShowRecordedNextVisitMedicinesAndQuestions() {
        val entries = listOf(CareItem("m1", "qa-person", "medicine", "Morning tablet", doneOn = "2026-10-23"), CareItem("m2", "qa-person", "medicine", "Evening tablet"))
        val note = CareVersion("n1", "qa-person", 1, "QA person", "Recorded source", "Priority", "", "Topic", "Is the dose right?\nWhen is the next scan?", "Step", "QA reviewer", now - 1000, "hash")
        ui.setContent { CareTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) { DoctorPatientDayContent(Person("qa-person", "QA person"), entries, listOf(note), true, now, ZoneId.of("UTC"), {}, {}, {}, {}, {}, {}, {}, {}) } } }
        ui.onNodeWithTag("doctor-glance-visit").assertExists()
        ui.onNodeWithText("None recorded", useUnmergedTree = true).assertExists()
        ui.onNodeWithText("1 of 2", useUnmergedTree = true).assertExists()
        ui.onNodeWithText("2", useUnmergedTree = true).assertExists()
        ui.onNodeWithTag("doctor-patient-day").performScrollToNode(hasTestTag("doctor-latest-note"))
        ui.onNodeWithTag("doctor-latest-note").assertIsDisplayed()
    }

    @Test fun readOnlyOverviewDoesNotOfferAnEnabledVisitWrite() {
        ui.setContent { CareTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            DoctorPatientDayContent(Person("qa-person", "QA person"), emptyList(), emptyList(), false, now, ZoneId.of("Asia/Kolkata"),
                {}, {}, {}, {}, {}, {}, {}, {})
        } } }
        ui.onNodeWithTag("doctor-patient-day").performScrollToNode(hasTestTag("doctor-add-visit"))
        ui.onNodeWithTag("doctor-add-visit").assertIsNotEnabled()
        ui.onNodeWithText("Read-only access. A permitted editor must add a visit.").assertExists()
        ui.onNodeWithTag("doctor-medicine-summary").assertDoesNotExist()
    }

    @Test fun careOverviewNeverSubstitutesAnotherPersonsNote() {
        fun note(id: String, person: String, topic: String) = CareVersion(id, person, 1, "QA person", "Recorded source", "Recorded priority", "", topic, "", "Recorded next step", "QA reviewer", now, "fixture-hash")
        val own = note("own", "qa-person", "This person's recorded discussion")
        val other = note("other", "other-person", "Other person's private discussion")
        val opened = mutableListOf<String>()
        ui.setContent { CareTheme { Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            DoctorCareStory(Person("qa-person", "QA person"), listOf(other, own)) { opened += it.id }
        } } }
        ui.onNodeWithTag("doctor-care-story").performScrollToNode(hasText("This person's recorded discussion"))
        ui.onNodeWithText("This person's recorded discussion").assertExists()
        ui.onNodeWithText("Other person's private discussion").assertDoesNotExist()
        ui.onNodeWithTag("doctor-care-story").performScrollToNode(hasText("Read exact note and source"))
        ui.onNodeWithText("Read exact note and source").performClick()
        assertEquals(listOf("own"), opened)
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
