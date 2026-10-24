package `in`.wedevit.care

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
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
        val previous = SymptomNote("Pain", 6, day, "10:00", "average-24h", "caregiver", "Fixture observer")
        val row = CareItem(id = "fixture-prior", personId = "fixture-person", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(previous))
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-person", "Fictional patient", listOf(row, row.copy(id = "other-person", personId = "different-person")), false,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-caregiver").performScrollTo().performClick()
        ui.onNodeWithText("Observer's name").performScrollTo().performTextInput("Fixture observer")
        closeSoftKeyboard()
        ui.onNodeWithTag("diary-choice-average-24h").performScrollTo().performClick()
        severity(6)
        severity(4)
        ui.onNodeWithTag("symptom-previous-reference").performScrollTo().assertTextEquals("Previous matching note: $day 10:00 · 6/10")
        ui.onNodeWithTag("diary-choice-better").performScrollTo().performClick()
        screenshot("symptom-comparison")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle {
            assertEquals(4, saved!!.rating)
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
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-worst-24h").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-unrated").performScrollTo().performClick()
        screenshot("symptom-hindi-unrated")
        ui.onNodeWithTag("symptom-save").assertTextContains("नोट सहेजें").performClick()
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
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        severity(0)
        ui.runOnIdle { person.value = "second-person" }
        ui.waitForIdle()
        ui.onNodeWithTag("symptom-save").assertIsNotEnabled()
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
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(5f, 0f..10f, 9)))
        ui.onNodeWithTag("diary-choice-average-24h").performScrollTo().performClick()
        severity(3)
        ui.onNodeWithTag("symptom-save").performClick()
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
        ui.onNodeWithTag("symptom-save").assertIsNotEnabled()
        ui.runOnIdle { assertNull(saved); editable.value = true }
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performClick()
        ui.onNodeWithText("Choose a rating or Not rated.").assertExists()
        ui.runOnIdle { assertNull(saved) }
        severity(0)
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle { assertEquals("Nausea", saved!!.symptom); assertEquals(0, saved!!.rating); assertEquals("patient", saved!!.respondent) }
    }

    @Test fun overallWellbeingUsesBestWorstAnchorsAndSavesAnAttributedPatientEntry() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-wellbeing", "Fictional patient", emptyList(), false,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Overall well-being").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-rating-anchors").performScrollTo().assertTextContains("0 = best possible well-being · 10 = worst possible well-being")
        severity(0)
        ui.onNodeWithTag("symptom-save").performClick()
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
        ui.setContent { CareTheme { SymptomNoteForm("fixture-legacy", "Renamed patient", listOf(row), false, initial = row,
            onSave = { _, next -> saved = next }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Sleep difficulty").assertDoesNotExist()
        ui.onNodeWithText("Symptom name").performScrollTo().assertTextContains("Sleep difficulty")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle { assertEquals(value, saved) }
    }

    @Test fun quickFollowupRequiresANewRatingAndKeepsTheSavedBodyPoint() {
        val point = PainBodyPoint3d("left-leg", .2, .3, .4)
        val old = SymptomNote("Pain", 8, LocalDate.now().minusDays(1).toString(), "10:00", "now", "patient", "Fictional patient",
            painLocation = "Left knee", painBodySites = listOf("left-leg"), painBodyPoints = listOf(point), painBodyModel = PainBodyMapRules.MODEL)
        val row = CareItem(id = "fixture-followup-old", personId = "fixture-followup", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(old))
        var savedId: String? = null
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-followup", "Renamed patient", listOf(row), false, followup = row,
            onSave = { id, value -> savedId = id; saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-quick-mode").assertIsSelected()
        ui.onNodeWithTag("symptom-rating-state").performScrollTo().assertTextEquals("Choose severity")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.onNodeWithText("Choose a rating or Not rated.").assertExists()
        ui.runOnIdle { assertNull(saved) }
        severity(2)
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle {
            assertNotEquals(row.id, savedId)
            assertEquals(2, saved!!.rating)
            assertEquals(listOf(point), saved!!.painBodyPoints)
            assertEquals("Left knee", saved!!.painLocation)
            assertEquals("Fictional patient", saved!!.author)
            assertEquals("patient", saved!!.respondent)
            assertEquals(LocalDate.now().toString(), saved!!.observedDate)
        }
    }

    @Test fun painRegionButtonsRetainLateralityClearSelectionAndSaveWithoutA3dHit() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-body", "Fictional patient", emptyList(), true,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        severity(4)
        ui.onNodeWithTag("pain-body-site-left-leg").performScrollTo().assertTextContains("बायाँ पैर").performClick()
        ui.onNodeWithTag("pain-body-back").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-site-left-leg").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("pain-body-clear").performScrollTo().performClick()
        ui.onNodeWithTag("pain-body-site-right-arm").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle {
            assertEquals(listOf("right-arm"), saved!!.painBodySites)
            assertTrue(saved!!.painBodyPoints.isEmpty())
            assertEquals(4, saved!!.rating)
        }
    }

    @Test fun freshFormDefaultsPatientButDoesNotSilentlyChooseZero() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-fresh", "Fictional patient", emptyList(), false, defaultPatientReporter = true,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("symptom-reporter-name").assertTextEquals("Fictional patient")
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Choose severity"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0f, 0f..10f, 9)))
        ui.onNodeWithTag("symptom-rating-value").assertTextEquals("—")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.onNodeWithTag("symptom-save-error").assertTextEquals("Choose a rating or Not rated.")
        ui.runOnIdle { assertNull(saved) }
        // An accessibility user can explicitly choose zero even when the unselected thumb is at the start.
        severity(0)
        ui.onNodeWithTag("symptom-rating-slider").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "0 / 10"))
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle {
            assertEquals(0, saved!!.rating)
            assertEquals("patient", saved!!.respondent)
            assertEquals("Fictional patient", saved!!.author)
        }
    }

    @Test fun aRealSliderTapChoosesAnIntegerAndDoesNotResetToZeroAtGestureEnd() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-slider-tap", "Fictional patient", emptyList(), false, defaultPatientReporter = true,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo().performTouchInput { click(center) }
        ui.onNodeWithTag("symptom-rating-value").assertTextEquals("5")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle { assertEquals(5, saved!!.rating) }
    }

    @Test fun freshPatientDefaultDoesNotInventACaregiverName() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-caregiver", "Fictional patient", emptyList(), false, defaultPatientReporter = true,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-caregiver").performScrollTo().performClick()
        ui.onNodeWithText("Observer's name").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
        severity(3)
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle { assertNull(saved) }
        ui.onNodeWithText("Observer's name").performScrollTo().performTextInput("Fictional caregiver")
        closeSoftKeyboard()
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle { assertEquals("caregiver", saved!!.respondent); assertEquals("Fictional caregiver", saved!!.author) }
    }

    @Test fun aNewNonSelfEntryDoesNotAssumeThePatientAnswered() {
        var saved: SymptomNote? = null
        ui.setContent { CareTheme { SymptomNoteForm("fixture-observer", "Fictional patient", emptyList(), false,
            onSave = { _, value -> saved = value }, onClose = {}) } }
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().assertIsNotSelected()
        severity(3)
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle { assertNull(saved) }
        ui.onNodeWithTag("diary-choice-patient").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-save").performClick()
        ui.runOnIdle { assertEquals("patient", saved!!.respondent); assertEquals("Fictional patient", saved!!.author) }
    }

    @Test fun savingLocksDraftAndDismissalThenAFailedAttemptUnlocksTheRetainedAnswersForRetry() {
        val busy = mutableStateOf(false)
        val error = mutableStateOf<String?>(null)
        val attempts = mutableListOf<Pair<String, SymptomNote>>()
        var closed = 0
        val words = "Retained fictional symptom details"
        ui.setContent { CareTheme { SymptomNoteForm("fixture-busy", "Fictional patient", emptyList(), false,
            busy = busy.value, actionError = error.value, defaultPatientReporter = true,
            onSave = { id, value -> attempts += id to value; error.value = null; busy.value = true }, onClose = { closed++ }) } }
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        severity(6)
        ui.onNodeWithText("Anything else? (optional)").performScrollTo().performTextInput(words)
        closeSoftKeyboard()
        ui.onNodeWithTag("symptom-save").performClick()
        ui.onNodeWithTag("symptom-close").assertIsNotEnabled()
        ui.onNodeWithTag("symptom-cancel").assertIsNotEnabled()
        ui.onNodeWithTag("symptom-save").assertIsNotEnabled()
        ui.onNodeWithTag("symptom-choice-Pain").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("diary-choice-caregiver").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("diary-choice-average-24h").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("symptom-unrated").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("symptom-observed-date").performScrollTo().assertIsNotEnabled()
        ui.onNodeWithTag("symptom-observed-time").assertIsNotEnabled()
        ui.onNodeWithText("Anything else? (optional)").performScrollTo().assertIsNotEnabled().assertTextContains(words)
        pressBack()
        ui.waitForIdle()
        ui.onNodeWithTag("symptom-entry-page").assertIsDisplayed()
        ui.runOnIdle {
            assertEquals(0, closed)
            assertEquals(1, attempts.size)
            assertEquals(words, attempts.single().second.note)
            // An explicit failed callback state; no database completion or success is simulated.
            busy.value = false
            error.value = "Synthetic save failed. Your answers are retained."
        }
        ui.onNodeWithTag("symptom-save-error").assertTextEquals("Synthetic save failed. Your answers are retained.")
        ui.onNodeWithTag("symptom-close").assertIsEnabled()
        ui.onNodeWithTag("symptom-cancel").assertIsEnabled()
        ui.onNodeWithText("Anything else? (optional)").performScrollTo().assertIsEnabled().assertTextContains(words)
            .performTextReplacement("$words · reviewed")
        closeSoftKeyboard()
        severity(7)
        ui.onNodeWithTag("symptom-save").assertIsEnabled().performClick()
        ui.runOnIdle {
            assertEquals(2, attempts.size)
            assertEquals(attempts.first().first, attempts.last().first)
            assertEquals("Nausea", attempts.last().second.symptom)
            assertEquals("patient", attempts.last().second.respondent)
            assertEquals("Fictional patient", attempts.last().second.author)
            assertEquals("now", attempts.last().second.recall)
            assertEquals(7, attempts.last().second.rating)
            assertEquals("$words · reviewed", attempts.last().second.note)
            assertEquals(0, closed)
        }
    }

    private fun severity(value: Int) {
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
