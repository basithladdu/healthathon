package `in`.wedevit.care

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Fictional in-memory entries. No account, database, cloud operation or save callback exists. */
class CarePainHistoryUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun datedHistorySwitchesSavedRecordsWithoutOfferingEditingOrPaintingMissingPoints() {
        val day = LocalDate.now().minusDays(2).toString()
        val point = PainBodyPoint3d("left-leg", .2, .3, .4)
        val exact = SymptomNote("Pain", 7, day, "10:00", "now", "patient", "Fictional patient", painLocation = "Left knee", painBodySites = listOf("left-leg"), painBodyPoints = listOf(point), painBodyModel = PainBodyMapRules.MODEL)
        val old = CareItem(id = "fixture-history-old", personId = "fixture-history", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(exact))
        val recent = old.copy(id = "fixture-history-recent", detail = SymptomNoteCodec.encode(exact.copy(rating = null, observedDate = LocalDate.now().minusDays(1).toString(), recall = "worst-24h", respondent = "caregiver", author = "Fixture observer", painBodySites = emptyList(), painBodyPoints = emptyList(), painBodyModel = "")))
        val records = listOf(old, recent, old.copy(id = "not-this-person", personId = "another-person"))
        val originalDetails = records.map { it.detail }
        ui.setContent { CareTheme { PainBodyHistoryContent(records, "fixture-history", false, initialId = old.id, onClose = {}) } }
        ui.onNodeWithTag("pain-history-observation").assertTextContains("$day 10:00 · 7/10")
        ui.onNodeWithTag("pain-history-entry-not-this-person").assertDoesNotExist()
        ui.onNodeWithTag("pain-body-clear").assertDoesNotExist()
        ui.onNodeWithTag("pain-body-site-left-leg").assertDoesNotExist()
        ui.onNodeWithTag("pain-body-back").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-selected").performScrollTo().assertTextContains("Left leg / foot")
        ui.onNodeWithTag("pain-history-newer").performScrollTo().performClick()
        ui.onNodeWithTag("pain-history-observation").assertTextContains("Not rated")
        ui.onNodeWithTag("pain-history-context").assertTextContains("Worst in 24 hours · Caregiver observation · Fixture observer")
        ui.onNodeWithText("This entry has a written location only; no point is added to the body.").assertExists()
        ui.onNodeWithTag("pain-body-selected").assertDoesNotExist()
        ui.runOnIdle { assertEquals(originalDetails, records.map { it.detail }) }
    }
    @Test fun noHistoryShowsAnHonestEmptyStateAndHindiCloseWorks() {
        var closed = false
        ui.setContent { CareTheme { PainBodyHistoryContent(emptyList(), "fixture-empty", true, onClose = { closed = true }) } }
        ui.onNodeWithText("अभी दर्द का कोई नोट नहीं है").assertExists()
        ui.onNodeWithTag("pain-body-3d").assertDoesNotExist()
        ui.onNodeWithTag("pain-history-close").assertTextContains("बंद करें").performClick()
        ui.runOnIdle { assertTrue(closed) }
    }
}
