package `in`.wedevit.care

import android.graphics.Bitmap
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class CareFlowTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: CareModel
    private var personId = ""
    private var previousPerson = ""
    private var previousRole = "Family"
    private val extraPeople = mutableListOf<String>()
    @Before fun setUp() {
        val account = ui.activity.careModel.cloud
        if (account != null) {
            val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            org.junit.Assume.assumeTrue(account.session.value?.email in listOf(args.getString("careTestOwner"), args.getString("careTestReader")))
            runBlocking { account.signOut() }
            ui.activityRule.scenario.recreate(); ui.waitForIdle()
        }
        ui.runOnIdle { vm = ui.activity.careModel; previousPerson = vm.selected.value; previousRole = vm.viewRole.value; vm.role(true); vm.addPerson("Test Person") }
        ui.waitUntil(15000) { !vm.busy.value && vm.selected.value != previousPerson && vm.people.value.any { it.id == vm.selected.value && it.name == "Test Person" } && vm.draft.value.personId == vm.selected.value && vm.selected.value.isNotBlank() }
        personId = vm.selected.value
    }
    @After fun cleanup() = runBlocking {
        val dao = CareDatabase.get(ui.activity).care()
        (extraPeople + personId).filter { it.isNotBlank() }.forEach { id ->
            dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteReports(id); dao.deleteDraft(id); dao.deletePersonRow(id)
            ReportFiles(ui.activity).removePerson(id)
        }
        ui.runOnIdle { vm = ui.activity.careModel; vm.select(previousPerson); vm.role(previousRole) }
    }
    private val attendeeConsent = SemanticsMatcher("doctor attendee consent checkbox") { node ->
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("doctor-attendee-consent-") == true
    }
    private fun clickContinue(label: String) {
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText(label))
        ui.onNodeWithText(label).assertIsEnabled().performClick()
    }
    /** Step 0 "Who's here": tick every attendee's consent box (the gate is satisfied, never bypassed), then continue. */
    private fun consentEveryoneAndContinue() {
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasTestTag("doctor-attendees"))
        ui.waitUntil(15000) { ui.onAllNodes(attendeeConsent).fetchSemanticsNodes().isNotEmpty() }
        val total = ui.onAllNodes(attendeeConsent).fetchSemanticsNodes().size
        // Consent is never pre-ticked, so recording must be unreachable until every person is ticked.
        assertEquals(total, ui.onAllNodes(attendeeConsent and isOff()).fetchSemanticsNodes().size)
        ui.onNodeWithText("Continue to recording").assertIsNotEnabled()
        repeat(total) {
            ui.onAllNodes(attendeeConsent and isOff())[0].performScrollTo().performClick()
            ui.waitForIdle()
        }
        ui.waitUntil(15000) { ui.onAllNodes(attendeeConsent and isOff()).fetchSemanticsNodes().isEmpty() }
        clickContinue("Continue to recording")
    }
    /** Step 0 to Step 3 "Transcript". */
    private fun openTranscriptStep() {
        consentEveryoneAndContinue()
        clickContinue("Continue to written text")
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val file = File(ui.activity.getExternalFilesDir(null), "verification/$name.png"); file.parentFile!!.mkdirs()
        val screen = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screen.recycle()
    }
    @Test fun realUsersHaveNoRoleMenuAndKeepDraftAndComputedRoleAfterRecreation() {
        val words = "Please keep my questions while I change views."
        ui.runOnIdle { vm.edit(vm.draft.value.copy(source = words)) }
        ui.onNodeWithTag("choose-role").assertDoesNotExist()
        ui.onNodeWithTag("walkthrough-user-menu").assertDoesNotExist()
        screenshot("no-role-menu")
        ui.onNodeWithTag("nav-Docs").assertIsDisplayed().performClick()
        ui.onNodeWithTag("documents-list").assertIsDisplayed()
        ui.activityRule.scenario.recreate(); ui.waitForIdle()
        ui.runOnIdle { vm = ui.activity.careModel }
        ui.waitUntil(15000) { vm.draft.value.personId == personId }
        assertEquals(CareRoles.forOwner(vm.isOwner.value), vm.viewRole.value)
        assertEquals(words, vm.draft.value.source)
        ui.runOnIdle { vm.role("Doctor") }
        ui.onNodeWithTag("nav-Talk").performClick()
        openTranscriptStep()
        // A saved transcript opens as numbered lines; Edit shows the text field again.
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasTestTag("transcript-edit"))
        ui.onNodeWithTag("transcript-edit").performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasSetTextAction() and hasText("Conversation in writing"))
        ui.onNode(hasSetTextAction() and hasText("Conversation in writing")).assertTextContains(words)
    }
    @Test fun reportPickerPersistsSearchesSharesAndRemovesOnlyItsCopy() {
        val source = CareReport(personId = personId, name = "Clinic report.pdf", mime = "application/pdf", fileName = "source.pdf", bytes = 0)
        val original = vm.reportFiles.file(source).apply { parentFile!!.mkdirs() }
        val pdf = android.graphics.pdf.PdfDocument()
        try {
            val page = pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(300, 400, 1).create())
            page.canvas.drawText("Synthetic report for UI verification", 16f, 40f, android.graphics.Paint().apply { textSize = 13f })
            pdf.finishPage(page); original.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        val uri = androidx.core.content.FileProvider.getUriForFile(ui.activity, ui.activity.packageName + ".fileprovider", original, source.name)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE); addDataType("*/*") }
        val monitor = instrumentation.addMonitor(filter, android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri)), true)
        try {
            ui.onNodeWithContentDescription("Documents").performClick()
            ui.onNodeWithContentDescription("Add a report").performClick()
            ui.waitUntil(15000) { vm.reports.value.size == 1 }
            assertEquals(1, monitor.hits)
        } finally { instrumentation.removeMonitor(monitor) }
        val report = vm.reports.value.single()
        assertEquals(source.name, report.name)
        assertFalse(original == vm.reportFiles.file(report))
        ui.onNodeWithText("Clinic report.pdf").assertIsDisplayed()
        screenshot("documents")
        ui.onNodeWithTag("document-search").performTextInput("no such document")
        ui.onNodeWithText("No documents found").assertIsDisplayed()
        ui.onNodeWithTag("nav-Today").performClick()
        ui.onNodeWithContentDescription("Documents").performClick()
        ui.onNodeWithTag("document-search").assertTextContains("no such document")
        ui.activityRule.scenario.recreate(); ui.waitForIdle()
        ui.runOnIdle { vm = ui.activity.careModel }
        ui.waitUntil(15000) { vm.reports.value.size == 1 }
        ui.onNodeWithContentDescription("Clear document search").performClick()
        val share = vm.reportFiles.intent(report, true)
        assertEquals(android.content.Intent.ACTION_SEND, share.action)
        assertEquals("application/pdf", share.type)
        assertTrue(share.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, share.flags and android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val sharedBytes = ui.activity.contentResolver.openInputStream(share.clipData!!.getItemAt(0).uri)!!.use { it.readBytes() }
        assertArrayEquals(original.readBytes(), sharedBytes)
        ui.onNodeWithTag("report-share-${report.id}").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("report-remove-${report.id}").performScrollTo().performClick()
        ui.onNodeWithText("Remove report", useUnmergedTree = true).performClick()
        ui.waitUntil(15000) { vm.reports.value.isEmpty() && !vm.busy.value }
        assertFalse(vm.reportFiles.file(report).exists())
        assertTrue(original.exists())
    }
    @Test fun documentsOpenSavedVersionWithActionsAtTheTop() = runBlocking {
        val note = NoteRules.publish(vm.people.value.first { it.id == personId }, Conversation(personId, source = "What matters: More time with family.", priorities = "More time with family."), "Test Doctor", true, 1)
        CareDatabase.get(ui.activity).care().insertVersion(note)
        ui.waitUntil(15000) { vm.versions.value.any { it.id == note.id } }
        ui.onNodeWithContentDescription("Documents").performClick()
        ui.onNodeWithText("Goals of care").performClick()
        ui.onNodeWithText("Save copy").assertIsDisplayed()
        ui.onNodeWithText("Share", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithText("More time with family.").assertIsDisplayed()
        screenshot("note-actions")
        val target = vm.reportFiles.file(CareReport(personId = personId, name = "Care note.txt", mime = "text/plain", fileName = "exported-note.txt", bytes = 0)).apply { parentFile!!.mkdirs(); createNewFile() }
        val uri = androidx.core.content.FileProvider.getUriForFile(ui.activity, ui.activity.packageName + ".fileprovider", target)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(android.content.Intent.CATEGORY_OPENABLE); addDataType("text/plain") }
        val monitor = instrumentation.addMonitor(filter, android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK, android.content.Intent().setData(uri)), true)
        try {
            ui.onNodeWithText("Save copy").performClick()
            ui.waitUntil(15000) { target.length() > 0 }
            assertEquals(NoteRules.text(note), target.readText())
            assertEquals(1, monitor.hits)
        } finally { instrumentation.removeMonitor(monitor) }
    }
    @Test fun conversationReviewSavesExactVersionAndSurvivesRecreation() {
        ui.onNodeWithText("Start talking").assertIsDisplayed()
        screenshot("today")
        ui.onNodeWithText("Talk", useUnmergedTree = true).performClick()
        val original = "What matters: I want to spend time with family.\nStill to discuss: I have not decided about hospital care.\nNext steps: Bring my questions to the next visit."
        openTranscriptStep()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasSetTextAction() and hasText("Conversation in writing"))
        ui.onNode(hasSetTextAction() and hasText("Conversation in writing")).performTextInput(original)
        clickContinue("Continue to answers")
        // The five note sections are off the main path now; open them from the Answers step.
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Edit the five note sections"))
        ui.onNodeWithText("Edit the five note sections").performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Organise using section headings"))
        ui.onNodeWithText("Organise using section headings").performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasSetTextAction() and hasText("What matters"))
        ui.onNode(hasSetTextAction() and hasText("What matters")).assertTextContains("I want to spend time with family.")
        // Build shows one section per screen; step through all five, the last "Next" reads "Review note".
        repeat(NoteRules.labels.size - 1) {
            ui.onNodeWithTag("conversation-list").performScrollToNode(hasTestTag("build-next"))
            ui.onNodeWithTag("build-next").performClick()
        }
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasTestTag("build-next"))
        ui.onNodeWithTag("build-next").assertTextContains("Review note").performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Continue to publish"))
        ui.onNodeWithText("Continue to publish").performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasSetTextAction() and hasText("Doctor's name"))
        ui.onNode(hasSetTextAction() and hasText("Doctor's name")).performTextInput("Test Doctor")
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Publish reviewed version"))
        ui.onNodeWithText("Publish reviewed version").assertIsNotEnabled()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("I checked every section against the conversation.", substring = true))
        ui.onNodeWithText("I checked every section against the conversation.", substring = true).performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Publish reviewed version"))
        ui.onNodeWithText("Publish reviewed version").performClick()
        ui.waitUntil(15000) { vm.versions.value.size == 1 }
        val first = vm.versions.value.single()
        assertEquals(original, first.source)
        assertEquals("I have not decided about hospital care.", first.questions)
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        val saved = runBlocking { CareDatabase.get(ui.activity).care().latest(personId) }
        assertEquals(first.contentHash, saved!!.contentHash)
        assertEquals(first.id, saved.id)
    }
    @Test fun manualSourceAndConsentSurviveAppStopWithoutStartingAMicrophone() {
        val words = "[Patient · English · 16 Oct 14:05] I am not ready to decide.\nManually corrected words remain here."
        val consentAt = 1_792_136_700_000L
        ui.runOnIdle { vm.edit(vm.draft.value.copy(source = words, consentAt = consentAt)) }
        ui.waitUntil(15000) { runBlocking { CareDatabase.get(ui.activity).care().draft(personId)?.source == words } }
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        ui.waitForIdle()
        assertFalse(vm.recording.value)
        assertEquals(words, vm.draft.value.source)
        assertEquals(consentAt, vm.draft.value.consentAt)
        ui.activityRule.scenario.recreate(); ui.waitForIdle()
        ui.runOnIdle { vm = ui.activity.careModel }
        ui.waitUntil(15000) { vm.draft.value.personId == personId }
        assertEquals(words, vm.draft.value.source)
        assertEquals(consentAt, vm.draft.value.consentAt)
    }
    @Test fun staleConfirmationCannotApplyAfterSwitchingPeople() {
        val note = NoteRules.publish(vm.people.value.first { it.id == personId }, Conversation(personId, source = "I want to ask again.", questions = "I want to ask again."), "Test Doctor", true, 1)
        runBlocking { CareDatabase.get(ui.activity).care().insertVersion(note) }
        ui.runOnIdle { vm.addPerson("Other confirmation person") }
        ui.waitUntil(15000) { !vm.busy.value && vm.selected.value != personId && vm.draft.value.personId == vm.selected.value }
        val otherPerson = vm.selected.value
        extraPeople += otherPerson
        ui.runOnIdle { vm.edit(vm.draft.value.copy(source = "Other person's words.")); vm.confirm(note, "Patient", "Patient", true) {} }
        ui.waitUntil(15000) { !vm.busy.value && vm.actionError.value != null }
        assertTrue(vm.actionError.value!!.contains("Open this person's care note"))
        assertTrue(runBlocking { CareDatabase.get(ui.activity).care().confirmations(note.id) }.isEmpty())
        assertEquals(otherPerson, vm.draft.value.personId)
        assertEquals("Other person's words.", vm.draft.value.source)
    }
    @Test fun directoryFiltersRecoverFromNoResults() {
        ui.onNodeWithText("Nearby", useUnmergedTree = true).performClick()
        ui.onNodeWithText("All India · ${vm.centres.size} centres").assertIsDisplayed()
        ui.onNodeWithText("Karnataka", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithText("Karnataka · ${vm.centres.count { it.state == "Karnataka" }} centres").assertIsDisplayed()
        ui.onNodeWithText("All India", useUnmergedTree = true).performScrollTo().performClick()
        screenshot("nearby")
        ui.onNode(hasSetTextAction()).performTextInput("No such hospital xyz")
        ui.onNodeWithText("No centres match that search.").assertIsDisplayed()
        ui.onNodeWithTag("nav-Today").performClick()
        ui.onNodeWithTag("nav-Nearby").performClick()
        ui.onNode(hasSetTextAction()).assertTextContains("No such hospital xyz")
        ui.onNodeWithText("Clear filters").performClick()
        ui.onNodeWithText("All India · ${vm.centres.size} centres").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).performTextInput("Aster")
        ui.onNodeWithTag("nearby-list").performScrollToNode(hasTestTag("centre-map"))
        ui.waitUntil(30000) { ui.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Map ready")).fetchSemanticsNodes().isNotEmpty() }
        screenshot("centre-map-loaded")
        ui.onNodeWithTag("centre-map").performTouchInput { click(center) }
        ui.waitUntil(10000) { ui.onAllNodesWithText("Aster CMI Hospital").fetchSemanticsNodes().size >= 2 }
        screenshot("centre-map")
    }
    @Test fun dailyEntriesAppearOnTodayAndCanBeEditedAndRemoved() {
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("Add task"))
        ui.onNodeWithText("Add task").performClick()
        ui.onNode(hasSetTextAction() and hasText("What needs doing?")).performTextInput("Bring questions")
        ui.onNodeWithText("Save", useUnmergedTree = true).performScrollTo().performClick()
        ui.waitUntil(15000) { vm.items.value.any { it.title == "Bring questions" } }
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("Bring questions"))
        ui.onNodeWithText("Bring questions").assertIsDisplayed()
        val entryId = vm.items.value.single { it.title == "Bring questions" }.id
        ui.onNodeWithTag("today-list").performScrollToNode(hasTestTag("care-edit-$entryId"))
        ui.onNodeWithTag("care-edit-$entryId").performClick()
        ui.onNode(hasSetTextAction() and hasText("What needs doing?")).performTextReplacement("Bring the prescription")
        ui.onNodeWithText("Save", useUnmergedTree = true).performScrollTo().performClick()
        ui.waitUntil(15000) { vm.items.value.any { it.title == "Bring the prescription" } }
        ui.onNodeWithTag("nav-Care").performClick()
        // Daily care shows modules as tiles; the To do tile opens its own screen.
        ui.onNodeWithTag("care-list").performScrollToNode(hasTestTag("care-module-to-do-toggle"))
        ui.onNodeWithTag("care-module-to-do-toggle").performClick()
        ui.onNodeWithTag("care-list").performScrollToNode(hasText("Bring the prescription"))
        ui.onNodeWithText("Bring the prescription").assertIsDisplayed()
        screenshot("care")
        ui.onNodeWithTag("care-list").performScrollToNode(hasTestTag("care-remove-$entryId"))
        ui.onNodeWithTag("care-remove-$entryId").performClick()
        ui.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        ui.waitUntil(15000) { vm.items.value.none { it.title == "Bring the prescription" } }
        ui.onNodeWithText("Nothing added yet").assertIsDisplayed()
    }
    @Test fun prescriptionScannerKeepsManualFallbackAndRequiresReviewBeforeSaving() {
        ui.onNodeWithTag("nav-Care").performClick()
        // The Medicines tile opens its own screen, which holds the scan entry.
        ui.onNodeWithTag("care-list").performScrollToNode(hasTestTag("care-module-medicines-toggle"))
        ui.onNodeWithTag("care-module-medicines-toggle").performClick()
        ui.onNodeWithTag("care-list").performScrollToNode(hasText("Scan a prescription"))
        ui.onNodeWithText("Scan a prescription").assertIsDisplayed()
        ui.onNodeWithText("Scan & organize").performClick()
        ui.onNodeWithTag("medicine-scanner-select").assertIsDisplayed()
        ui.onNodeWithText("Choose pages").assertIsDisplayed()
        ui.onNodeWithText("Choose photos").assertIsDisplayed()
        screenshot("medicine-scanner")
        ui.onNodeWithTag("medicine-scanner-select").performScrollToNode(hasText("Add one manually"))
        ui.onNodeWithText("Add one manually").performClick()
        ui.onNodeWithTag("medicine-scanner-review").assertIsDisplayed()
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("medicine-scanner-select").assertIsDisplayed()
        ui.onNodeWithTag("medicine-scanner-select").performScrollToNode(hasText("Add one manually"))
        ui.onNodeWithText("Add one manually").performClick()
        ui.onNodeWithTag("medicine-scanner-review").assertIsDisplayed()
        ui.onNode(hasSetTextAction() and hasText("Medicine name")).performTextInput("Test medicine")
        ui.onNode(hasSetTextAction() and hasText("Strength")).performTextInput("10 mg")
        screenshot("medicine-review")
        ui.onNodeWithTag("medicine-scanner-review").performScrollToNode(hasText("Save 1 medicine"))
        ui.onNodeWithText("Save 1 medicine").assertIsNotEnabled()
        ui.onNodeWithText("I checked this medicine, dose and directions against the original prescription and packaging.", substring = true).performScrollTo().performClick()
        ui.onNodeWithTag("medicine-scanner-review").performScrollToNode(hasText("Save 1 medicine"))
        ui.onNodeWithText("Save 1 medicine").performClick()
        ui.waitUntil(15000) { vm.items.value.any { it.kind == "medicine" && it.title == "Test medicine" } }
        val saved = vm.items.value.single { it.kind == "medicine" && it.title == "Test medicine" }
        assertTrue(saved.detail.contains("Dose: 10 mg"))
        ui.onNodeWithText("Test medicine").assertIsDisplayed()
    }
    @Test fun homeSymptomTileOpensDiaryWithoutCreatingACheckin() {
        ui.runOnIdle { vm.role(false) }
        val before = vm.items.value.toList()
        ui.onNodeWithTag("symptom-tracker-entry").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-diary").assertIsDisplayed()
        ui.onNodeWithTag("symptom-add").assertExists()
        ui.onNodeWithTag("feedback-screen").assertDoesNotExist()
        ui.runOnIdle { assertEquals(before, vm.items.value) }
        screenshot("home-symptom-diary")
    }

    @Test fun checkinSavesChosenWordsAndKeepsOptionalNote() {
        ui.runOnIdle { vm.role(false) }
        ui.onNodeWithTag("nav-Care").performClick()
        ui.onAllNodesWithTag("care-add-button").onFirst().performClick()
        ui.onNodeWithTag("care-add-checkin").performClick()
        // The check-in is now the slider screen: pick the middle mood, which stores "An okay day".
        ui.onNodeWithTag("checkin-mood-0.5").performScrollTo().performClick()
        screenshot("checkin")
        ui.onNode(hasSetTextAction() and hasText("Anything to add? (optional)")).performScrollTo().performTextInput("Spent time with family")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        ui.onNodeWithTag("feedback-save").performScrollTo().performClick()
        ui.waitUntil(15000) { vm.items.value.any { it.kind == "checkin" } }
        assertEquals("Spent time with family", vm.items.value.single { it.kind == "checkin" }.detail)
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("An okay day"))
        ui.onNodeWithText("An okay day").assertIsDisplayed()
        screenshot("today-saved")
        // A fresh save snackbar overlaps the first visible Privacy header.
        // Wait for its actual dismissal before testing the next touch target.
        ui.waitUntil(15000) {
            ui.onAllNodesWithText("Saved", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        ui.onNodeWithTag("nav-More").performClick()
        val privacyHeader = ui.onNode(hasText("Privacy"))
        privacyHeader.performScrollTo().assertIsDisplayed()
        ui.onRoot(useUnmergedTree = true).printToLog("SaharaPrivacyBefore")
        screenshot("more-privacy-before")
        ui.onRoot(useUnmergedTree = true).printToLog("SaharaPrivacy")
        screenshot("more-privacy-visible")
        ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("You choose which notes to share and with whom.", substring = true))
        ui.onNodeWithText("You choose which notes to share and with whom.", substring = true).assertIsDisplayed()
        screenshot("more")
    }
    @Test fun signInLoadsAccountRecordsOnToday() {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val email = args.getString("careTestOwner"); val password = args.getString("careTestPassword")
        org.junit.Assume.assumeNotNull(email, password)
        val cloud = CareCloud(ui.activity)
        org.junit.Assume.assumeTrue(cloud.session.value == null)
        val shared = Person(name = "Shared Test Person")
        try {
            runBlocking {
                cloud.signIn(email!!, password!!)
                cloud.addPerson(shared)
                cloud.saveEntry(CareItem(personId = shared.id, kind = "task", title = "Bring the care note"))
                cloud.signOut()
            }
            ui.onNodeWithTag("nav-More").performClick()
            ui.onNodeWithText("Sign in to share care").performClick()
            ui.onNode(hasSetTextAction() and hasText("Email")).performTextInput(email!!)
            ui.onNode(hasSetTextAction() and hasText("Password")).performTextInput(password!!)
            ui.onNodeWithText("Forgot password?").performScrollTo().assertIsDisplayed().assertIsEnabled()
            screenshot("private-account-keyboard")
            ui.onNodeWithText("Privacy policy").performScrollTo().assertIsDisplayed()
            ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().assertIsDisplayed().assertIsEnabled()
            screenshot("private-account-actions")
            ui.onNode(hasText("Sign in") and hasClickAction()).performClick()
            ui.waitUntil(20000) { ui.activity.careModel.cloud != null && ui.activity.careModel.people.value.any { it.id == shared.id } }
            if (ui.activity.careModel.selected.value != shared.id) {
                ui.onNodeWithTag("person-${shared.id}").performScrollTo().performClick()
            }
            ui.waitUntil(20000) { ui.activity.careModel.cloud != null && ui.activity.careModel.items.value.any { it.title == "Bring the care note" } }
            ui.onNodeWithTag("today-list").performScrollToNode(hasText("Bring the care note"))
            ui.onNodeWithText("Bring the care note").assertIsDisplayed()
            ui.activityRule.scenario.recreate()
            ui.waitUntil(20000) { ui.activity.careModel.cloud != null && ui.activity.careModel.items.value.any { it.title == "Bring the care note" } }
            ui.onNodeWithTag("today-list").performScrollToNode(hasText("Bring the care note"))
            screenshot("account-today")
        } finally {
            runBlocking {
                runCatching { ui.activity.careModel.cloud?.signOut() }
                runCatching { cloud.signIn(email!!, password!!); cloud.removePerson(shared.id) }
                runCatching { cloud.signOut() }
            }
            ui.activityRule.scenario.recreate()
            ui.waitForIdle()
            vm = ui.activity.careModel
        }
    }
    @Test fun switchingPeopleKeepsLatestDraftAndCompletedTasks() {
        ui.runOnIdle { vm.addPerson("Second Test Person") }
        ui.waitUntil(15000) { vm.selected.value != personId && vm.draft.value.personId == vm.selected.value }
        val secondId = vm.selected.value; extraPeople.add(secondId)
        ui.runOnIdle { vm.select(personId) }
        ui.waitUntil(15000) { vm.draft.value.personId == personId }
        val latestWords = "Please keep my latest words when I switch people."
        ui.runOnIdle { vm.edit(vm.draft.value.copy(source = latestWords)); vm.select(secondId) }
        ui.waitUntil(15000) { vm.draft.value.personId == secondId }
        assertEquals("", vm.draft.value.source)
        ui.runOnIdle { vm.select(personId) }
        ui.waitUntil(15000) { vm.draft.value.personId == personId }
        assertEquals(latestWords, vm.draft.value.source)
        val oldTask = CareItem(personId = personId, kind = "task", title = "An already completed task", doneOn = "2026-01-01")
        runBlocking { CareDatabase.get(ui.activity).care().saveItem(oldTask) }
        ui.waitUntil(15000) { vm.items.value.any { it.id == oldTask.id } }
        ui.runOnIdle { vm.toggle(oldTask) }
        ui.waitUntil(15000) { vm.items.value.firstOrNull { it.id == oldTask.id }?.doneOn == "" }
    }
    @Test fun switchingPeopleClosesOrdinaryEntryWithoutSavingToTheNewPerson() {
        ui.runOnIdle { vm.addPerson("Other entry recipient") }
        ui.waitUntil(15000) { !vm.busy.value && vm.selected.value != personId && vm.selected.value.isNotBlank() }
        val other = vm.selected.value; extraPeople += other
        ui.runOnIdle { vm.select(personId) }
        ui.waitUntil(15000) { vm.selected.value == personId && vm.draft.value.personId == personId }
        ui.onNodeWithTag("nav-Today").performClick()
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("Add task"))
        ui.onNodeWithText("Add task").performClick()
        val title = "Questions belonging to the first person"
        ui.onNode(hasSetTextAction() and hasText("What needs doing?")).performTextInput(title)
        ui.runOnIdle { vm.select(other) }
        ui.waitUntil(15000) {
            vm.selected.value == other && vm.draft.value.personId == other &&
                ui.onAllNodes(hasSetTextAction() and hasText("What needs doing?")).fetchSemanticsNodes().isEmpty()
        }
        ui.onNode(hasSetTextAction() and hasText("What needs doing?")).assertDoesNotExist()
        val dao = CareDatabase.get(ui.activity).care()
        val newPersonItems = runBlocking { dao.items(other).first() }
        val oldPersonItems = runBlocking { dao.items(personId).first() }
        assertTrue(newPersonItems.none { it.title == title })
        assertTrue(oldPersonItems.none { it.title == title })
    }

    @Test fun roomPersistsAndSeparatesPeopleAcrossDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val filename = "verification-${newId()}.db"
        var db = Room.databaseBuilder(context, CareDatabase::class.java, filename).build()
        val one = Person(name = "First synthetic person"); val two = Person(name = "Second synthetic person")
        db.care().addPerson(one); db.care().addPerson(two)
        val item = CareItem(personId = one.id, kind = "task", title = "Bring questions")
        db.care().saveItem(item); db.close()
        db = Room.databaseBuilder(context, CareDatabase::class.java, filename).build()
        assertEquals(item, db.care().items(one.id).first().single())
        assertTrue(db.care().items(two.id).first().isEmpty())
        db.close(); context.deleteDatabase(filename); Unit
    }
}
