package `in`.wedevit.care

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** In-memory fictional records and callback only. Never opens CareRoot, a real database, auth or network. */
class CareSymptomDiaryUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun freshRatingsKeepRecallObserverAndActualSamePersonReference() {
        val day = LocalDate.now().minusDays(1).toString()
        val previous = SymptomNote("Pain", 6, day, "10:00", "average-24h", "caregiver", "Fixture observer", painBodySites = listOf("head"), painBodyRatings = mapOf("head" to 6))
        val row = CareItem(id = "fixture-prior", personId = "fixture-person", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(previous))
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-person", "Fictional patient", listOf(row, row.copy(id = "other-person", personId = "different-person")), false,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().performClick()
        markWholePart("head"); ui.onNodeWithTag("pain-strength-target").performScrollTo().assertTextContains("Head"); setStrength(6f); setStrength(4f)
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-rating-4").assertDoesNotExist() // Pain strength is set per spot; step 3 has no number card
        ui.onNodeWithTag("diary-choice-caregiver").performScrollTo().performClick()
        ui.onNodeWithText("Name of the person who saw it").performScrollTo().performTextInput("Fixture observer")
        closeSoftKeyboard()
        ui.onNodeWithTag("diary-choice-average-24h").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-previous-reference").performScrollTo().assertTextEquals("Previous matching note: $day 10:00 · 6/10")
        ui.onNodeWithTag("diary-choice-better").performScrollTo().performClick()
        screenshot("symptom-comparison")
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals(4, saved!!.rating); assertEquals(mapOf("head" to 4), saved!!.painBodyRatings)
            assertEquals("average-24h", saved!!.recall)
            assertEquals("caregiver", saved!!.respondent)
            assertEquals("Fixture observer", saved!!.author)
            assertEquals("better", saved!!.selfReportedChange)
            assertEquals("fixture-prior", saved!!.comparedWithId)
        }
    }

    @Test fun hindiExplicitUnratedIsNotZeroAndKeepsPatientAttribution() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-hindi", "काल्पनिक मरीज़", emptyList(), true,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().assertTextContains("दर्द").performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-worst-24h").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-unrated").performScrollTo().performClick()
        screenshot("symptom-hindi-unrated")
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().assertTextContains("नोट सहेजें").performClick()
        ui.runOnIdle {
            assertNotNull(saved); assertNull(saved!!.rating)
            assertEquals("patient", saved!!.respondent)
            assertEquals("काल्पनिक मरीज़", saved!!.author)
            assertEquals("worst-24h", saved!!.recall)
            assertEquals("", saved!!.comparedWithId)
        }
    }

    @Test fun personSwitchClosesCapturedDraftAndPreventsSaveToTheNewPerson() {
        val person = mutableStateOf("first-person")
        var closed = false
        var saved = false
        ui.setContent { CareTheme { SymptomNoteForm(person.value, "Fictional patient", emptyList(), false,
            onSave = { _, _ -> saved = true }, onClose = { closed = true }) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-unrated").performScrollTo().performClick() // Pain with no rated spot: Not rated
        ui.runOnIdle { person.value = "second-person" }
        ui.waitForIdle()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().assertIsNotEnabled()
        ui.runOnIdle { assertTrue(closed); assertFalse(saved) }
    }

    @Test fun editKeepsRecordIdentityAndClearsAnIncompatibleComparison() {
        val priorDay = LocalDate.now().minusDays(2).toString()
        val editDay = LocalDate.now().minusDays(1).toString()
        val previous = SymptomNote("Nausea", 7, priorDay, "10:00", "now", "patient", "Fictional patient")
        val priorRow = CareItem(id = "fixture-edit-prior", personId = "fixture-edit", kind = "checkin", title = "Nausea", detail = SymptomNoteCodec.encode(previous))
        val initial = priorRow.copy(id = "fixture-edit-current", detail = SymptomNoteCodec.encode(previous.copy(rating = 5, observedDate = editDay, selfReportedChange = "better", comparedWithId = priorRow.id)))
        var savedId: String? = null
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-edit", "Fictional patient", listOf(priorRow, initial), false, initial = initial,
            onSave = { id, value -> savedId = id; saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-step-indicator").assertTextContains("Step 3 of 3")
        ui.onNodeWithTag("symptom-step-back").performClick()
        ui.onNodeWithTag("symptom-rating-5").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("diary-choice-average-24h").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-rating-3").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals(initial.id, savedId)
            assertEquals(3, saved!!.rating)
            assertEquals(editDay, saved!!.observedDate)
            assertEquals("average-24h", saved!!.recall)
            assertEquals("", saved!!.selfReportedChange)
            assertEquals("", saved!!.comparedWithId)
        }
    }

    @Test fun readOnlyFormCannotSaveAndMissingRatingCanBeCorrectedWithoutLosingAnswers() {
        val editable = mutableStateOf(false)
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-readonly", "Fictional patient", emptyList(), false, canEdit = editable.value,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-step-next").assertIsNotEnabled()
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").assertIsNotEnabled()
        ui.onNodeWithText("Choose a rating or Not rated.").assertExists()
        ui.onNodeWithTag("symptom-rating-0").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().assertIsNotEnabled()
        ui.runOnIdle { assertNull(saved); editable.value = true }
        ui.onNodeWithTag("symptom-save").performScrollTo().assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals("Nausea", saved!!.symptom); assertEquals(0, saved!!.rating); assertEquals("patient", saved!!.respondent) }
    }

    @Test fun overallWellbeingUsesBestWorstAnchorsAndSavesAnAttributedPatientEntry() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-wellbeing", "Fictional patient", emptyList(), false,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Overall well-being").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-rating-anchors").performScrollTo().assertTextContains("0 = best possible well-being · 10 = worst possible well-being")
        ui.onNodeWithTag("symptom-rating-0").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals("Overall well-being", saved!!.symptom)
            assertEquals(SymptomDiaryRules.WELLBEING_SCALE, saved!!.scale)
            assertEquals(0, saved!!.rating)
            assertEquals("patient", saved!!.respondent)
            assertEquals("Fictional patient", saved!!.author)
        }
    }

    @Test fun editingAnOlderSleepDifficultyRecordKeepsItsNameRatingAndScale() {
        val value = SymptomNote("Sleep difficulty", 6, LocalDate.now().minusDays(1).toString(), "10:00", "now", "patient", "Fictional patient")
        val row = CareItem(id = "fixture-legacy-sleep", personId = "fixture-legacy", kind = "checkin", title = "Sleep difficulty", detail = SymptomNoteCodec.encode(value))
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-legacy", "Fictional patient", listOf(row), false, initial = row,
            onSave = { _, next -> saved = next }, onClose = {}) } }
        ui.onNodeWithTag("symptom-step-back").performClick()
        ui.onNodeWithTag("symptom-step-back").performClick()
        ui.onNodeWithTag("symptom-choice-Sleep difficulty").assertDoesNotExist()
        ui.onNodeWithText("Symptom name").performScrollTo().assertTextContains("Sleep difficulty")
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(value, saved) }
    }

    @Test fun quickFollowupRequiresANewRatingAndKeepsTheSavedBodyPoint() {
        val point = PainBodyPoint3d("left-leg", .2, .3, .4)
        val old = SymptomNote("Pain", 8, LocalDate.now().minusDays(1).toString(), "10:00", "now", "patient", "Fictional patient",
            painLocation = "Left knee", painBodySites = listOf("left-leg"), painBodyPoints = listOf(point), painBodyModel = PainBodyMapRules.MODEL)
        val row = CareItem(id = "fixture-followup-old", personId = "fixture-followup", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(old))
        var savedId: String? = null
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-followup", "Fictional patient", listOf(row), false, followup = row,
            onSave = { id, value -> savedId = id; saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-quick-mode").assertIsSelected()
        ui.onNodeWithTag("symptom-rating-8").performScrollTo().assertIsNotSelected()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.onNodeWithText("Choose a rating or Not rated.").assertExists()
        ui.runOnIdle { assertNull(saved) }
        ui.onNodeWithTag("symptom-rating-2").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle {
            assertNotEquals(row.id, savedId)
            assertEquals(2, saved!!.rating)
            assertEquals(listOf(point), saved!!.painBodyPoints)
            assertEquals("Left knee", saved!!.painLocation)
            assertEquals(LocalDate.now().toString(), saved!!.observedDate)
        }
    }

    @Test fun painRegionButtonsRetainLateralityClearSelectionAndSaveWithoutA3dHit() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-body", "Fictional patient", emptyList(), true,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-site-left-leg").performScrollTo().assertTextContains("बायाँ पैर").performClick()
        ui.onNodeWithTag("pain-body-site-left-leg").assertIsSelected() // zoomed only
        ui.onNodeWithTag("pain-body-selected").assertDoesNotExist() // a zoom never marks pain
        ui.onNodeWithTag("pain-body-mark-left-leg").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-selected").performScrollTo().assertTextContains("बायाँ पैर")
        ui.onNodeWithTag("pain-body-back").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-site-left-leg").performScrollTo().assertIsNotSelected() // side change resets the zoom, the mark stays
        ui.onNodeWithTag("pain-body-selected").assertExists()
        ui.onNodeWithTag("pain-body-clear").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-selected").assertDoesNotExist()
        ui.onNodeWithTag("pain-body-site-right-arm").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-mark-right-arm").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-unmark-right-arm").assertExists()
        ui.onNodeWithTag("pain-body-selected").assertExists()
        setStrength(4f) // the marked right arm is the active spot
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals(listOf("right-arm"), saved!!.painBodySites)
            assertTrue(saved!!.painBodyPoints.isEmpty())
            assertEquals(4, saved!!.rating); assertEquals(mapOf("right-arm" to 4), saved!!.painBodyRatings)
        }
    }

    @Test fun eachMarkedSpotKeepsItsOwnRatingAndTheNoteTakesTheHighest() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-spots", "Fictional patient", emptyList(), false, onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().performClick()
        markWholePart("head"); setStrength(9f)
        markWholePart("neck")
        ui.onNodeWithTag("pain-strength-target").performScrollTo().assertTextContains("Neck")
        ui.onNodeWithTag("pain-strength-value").assertTextContains("Slide to set. Not rated yet.") // a new spot starts unrated; the head keeps its 9
        setStrength(3f)
        ui.onNodeWithTag("pain-body-site-head").performScrollTo().performClick() // a marked part tile makes that spot active again
        ui.onNodeWithTag("pain-strength-target").assertTextContains("Head")
        ui.onNodeWithTag("pain-strength-value").assertTextContains("9/10")
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-unrated").assertDoesNotExist(); ui.onNodeWithTag("symptom-rating-9").assertDoesNotExist()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(mapOf("head" to 9, "neck" to 3), saved!!.painBodyRatings); assertEquals(9, saved!!.rating) }
    }

    @Test fun painWithoutAnyRatedSpotOffersBackToRateOrNotRatedAndBlocksNextUntilOneIsChosen() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-unrated", "Fictional patient", emptyList(), false, onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().performClick()
        markWholePart("neck") // marked but not rated
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithText("No pain strength set yet.").assertExists()
        ui.onNodeWithTag("symptom-step-next").assertIsNotEnabled()
        ui.onNodeWithTag("symptom-pain-rate-back").performScrollTo().performClick()
        ui.onNodeWithTag("pain-strength-slider").assertExists()
        setStrength(0f) // 0 is a real rating: no colour, but the step can continue
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-step-next").assertIsEnabled()
        ui.onNodeWithTag("symptom-step-next").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(0, saved!!.rating); assertEquals(mapOf("neck" to 0), saved!!.painBodyRatings) }
    }

    private fun markWholePart(site: String) {
        ui.onNodeWithTag("pain-body-site-$site").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-mark-$site").performScrollTo().performClick()
    }
    private fun setStrength(value: Float) { ui.onNodeWithTag("pain-strength-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value) } }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
