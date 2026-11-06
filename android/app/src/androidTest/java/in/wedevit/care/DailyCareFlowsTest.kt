package `in`.wedevit.care

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

/** Drives the real screens and checks the saved Room records, using isolated synthetic people. */
class DailyCareFlowsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var vm: CareModel
    private var originalPerson = ""
    private var originalRole = "Family"
    private var person = ""
    private val createdPeople = mutableListOf<String>()

    @Before fun setUp() {
        val cloud = ui.activity.careModel.cloud
        if (cloud != null) {
            val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            Assume.assumeTrue(cloud.session.value?.email in listOf(args.getString("careTestOwner"), args.getString("careTestReader")))
            runBlocking { cloud.signOut() }
            ui.activityRule.scenario.recreate(); ui.waitForIdle()
        }
        ui.runOnIdle {
            vm = ui.activity.careModel
            originalPerson = vm.selected.value; originalRole = vm.viewRole.value
            vm.role("Family"); vm.addPerson("Daily care verification")
        }
        ui.waitUntil(15000) { !vm.busy.value && vm.selected.value.isNotBlank() && vm.selected.value != originalPerson && vm.draft.value.personId == vm.selected.value }
        person = vm.selected.value; createdPeople += person
    }

    @After fun cleanup() = runBlocking {
        if (!::vm.isInitialized) return@runBlocking
        val dao = CareDatabase.get(ui.activity).care()
        createdPeople.forEach { id -> dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteReports(id); dao.deleteDraft(id); dao.deletePersonRow(id); ReportFiles(ui.activity).removePerson(id) }
        ui.runOnIdle { vm = ui.activity.careModel; vm.select(originalPerson); vm.role(originalRole) }
    }

    /** Calendar, Lab history and the other pages are rows in All care tools (the Care tab). */
    private fun openShortcut(tag: String) {
        ui.onNodeWithTag("nav-Care").performClick()
        ui.onNodeWithTag("care-list").performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performClick()
    }
    /** The Care team page left the patient's Today (2 Nov redesign). A family member reaches it from the Goals of care card: Talk, then "Ask for a conversation". */
    private fun openCareTeam() {
        ui.onNodeWithTag("nav-Today").performClick()
        ui.onNodeWithTag("today-list").performScrollToNode(hasTestTag("today-goals-of-care"))
        ui.onNodeWithTag("today-goals-of-care").performClick()
        ui.onAllNodesWithText("Ask to talk").onFirst().performScrollTo().performClick()
        ui.onNodeWithTag("workflow-list").assertIsDisplayed()
    }
    private fun enter(label: String, text: String) {
        val field = ui.onNode(hasSetTextAction() and hasText(label))
        if (label != "Your name") field.performScrollTo()
        field.performTextReplacement(text)
    }
    private fun hideKeyboard() { ui.runOnIdle { ViewCompat.getWindowInsetsController(ui.activity.window.decorView)?.hide(WindowInsetsCompat.Type.ime()) }; ui.waitForIdle() }
    private fun saved(title: String): CareItem {
        ui.waitUntil(15000) { !vm.busy.value && vm.items.value.any { it.personId == person && it.title == title } }
        return vm.items.value.first { it.title == title && it.personId == person }
    }
    private fun recreate() {
        ui.activityRule.scenario.recreate(); ui.waitForIdle()
        ui.runOnIdle { vm = ui.activity.careModel }
        ui.waitUntil(15000) { vm.selected.value == person && vm.draft.value.personId == person }
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val output = File(ui.activity.getExternalFilesDir(null), "verification/$name.png").apply { parentFile!!.mkdirs() }
        val image = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
    }

    @Test fun familyRequestAppearsInDoctorQueueAndCanOpenConversation() {
        openCareTeam()
        ui.onNodeWithText("Ask to talk").performClick()
        enter("What would you like to discuss?", "Comfort at home")
        enter("Doctor or care team", "Dr Test")
        hideKeyboard()
        ui.onNodeWithTag("workflow-save").performScrollTo().performClick()
        val item = saved("Comfort at home")
        assertEquals("conversation", CareEntryCodec.decode(item.detail)!!.type)
        ui.onNode(hasText("Requested") and hasText("For Dr Test", substring = true)).assertIsDisplayed()
        ui.onNodeWithText("Pick up", useUnmergedTree = true).assertDoesNotExist()
        ui.runOnIdle { vm.role("Doctor") }
        ui.onNodeWithText("Care queue").assertIsDisplayed()
        ui.onNodeWithText("Pick up", useUnmergedTree = true).performClick()
        enter("Your name", "Dr Test"); hideKeyboard()
        ui.onNodeWithTag("workflow-confirm-action").performClick()
        ui.waitUntil(15000) { CareEntryCodec.decode(vm.items.value.first { it.id == item.id }.detail)?.status == "accepted" }
        ui.onNode(hasText("Picked up") and hasText("For Dr Test", substring = true)).assertIsDisplayed()
        screenshot("conversation-request-picked-up")
        ui.onNodeWithText("Open conversation").performScrollTo().performClick()
        ui.onNodeWithTag("conversation-list").assertIsDisplayed()
        recreate()
        val persisted = runBlocking { CareDatabase.get(ui.activity).care().item(item.id) }!!
        assertEquals("Dr Test", CareEntryCodec.decode(persisted.detail)!!.acceptedBy)
        assertEquals(person, persisted.personId)
    }

    @Test fun namedTaskAcceptanceCompletionAndSenderStatusSurviveReload() {
        openCareTeam()
        ui.onNodeWithText("Add task", useUnmergedTree = true).performClick()
        enter("What needs doing?", "Collect the clinic report")
        enter("Who is this for?", "Ayesha")
        hideKeyboard()
        ui.onNodeWithTag("workflow-save").performScrollTo().performClick()
        val item = saved("Collect the clinic report")
        ui.onNode(hasText("To do") and hasText("For Ayesha", substring = true)).assertIsDisplayed()
        ui.onNodeWithText("Pick up", useUnmergedTree = true).performClick()
        enter("Your name", "Ayesha"); hideKeyboard(); ui.onNodeWithTag("workflow-confirm-action").performClick()
        ui.waitUntil(15000) { CareEntryCodec.decode(vm.items.value.first { it.id == item.id }.detail)?.status == "accepted" }
        ui.onNodeWithText("Mark done", useUnmergedTree = true).performScrollTo().performClick()
        ui.onNodeWithTag("workflow-confirm-action").performClick()
        ui.waitUntil(15000) { CareEntryCodec.decode(vm.items.value.first { it.id == item.id }.detail)?.status == "completed" }
        ui.onNodeWithText("Include finished").performClick()
        ui.onNode(hasText("Completed") and hasText("For Ayesha", substring = true)).assertIsDisplayed()
        screenshot("care-task-completed")
        recreate(); openCareTeam()
        ui.onNode(hasText("Completed") and hasText("For Ayesha", substring = true)).assertIsDisplayed()
        val result = runBlocking { CareDatabase.get(ui.activity).care().item(item.id) }!!
        val data = CareEntryCodec.decode(result.detail)!!
        assertEquals("Family", data.sender)
        assertEquals("Ayesha", data.assignee)
        assertEquals("Ayesha", data.acceptedBy)
        assertEquals("Ayesha", data.completedBy)
        assertTrue(data.acceptedAt > 0 && data.completedAt >= data.acceptedAt)
        assertTrue(result.doneOn.isNotBlank())
    }

    @Test fun labsKeepPrintedValuesRangesAndSeriesWithoutLeakingToAnotherPerson() {
        openShortcut("shortcut-labs")
        fun addLab(value: String, withRange: Boolean) {
            ui.onNodeWithContentDescription("Add a lab result").performClick()
            enter("Test name on report", "Haemoglobin")
            enter("Value", value); enter("Printed unit", "g/dL"); hideKeyboard()
            if (withRange) {
                enter("Lower", "12"); enter("Upper", "16"); hideKeyboard()
            }
            val oldCount = vm.items.value.count { CareEntryCodec.decode(it.detail)?.type == "lab" }
            ui.onNodeWithText("Save result", useUnmergedTree = true).performScrollTo().performClick()
            ui.waitUntil(15000) { !vm.busy.value && vm.items.value.count { CareEntryCodec.decode(it.detail)?.type == "lab" } == oldCount + 1 }
        }
        addLab("12.4", true); addLab("13.1", false)
        ui.onNodeWithText("2 results", substring = true).assertIsDisplayed()
        ui.onNodeWithTag("lab-history").performScrollToNode(hasText("Printed range: 12–16 g/dL"))
        ui.onNodeWithText("Printed range: 12–16 g/dL").assertIsDisplayed()
        ui.onAllNodesWithText("12.4 g/dL").onFirst().assertExists()
        screenshot("lab-history-series")
        recreate(); openShortcut("shortcut-labs")
        ui.onNodeWithText("2 results", substring = true).assertIsDisplayed()
        val first = vm.items.value.first { CareEntryCodec.decode(it.detail)?.value == "12.4" }
        assertEquals("g/dL", CareEntryCodec.decode(first.detail)!!.unit)
        assertEquals(LocalDate.now(), careDay(first.due))
        ui.runOnIdle { vm.addPerson("Other synthetic person") }
        ui.waitUntil(15000) { !vm.busy.value && vm.selected.value != person && vm.items.value.isEmpty() }
        val other = vm.selected.value; createdPeople += other
        openShortcut("shortcut-labs")
        ui.onNodeWithText("No results yet").assertIsDisplayed()
        ui.onNodeWithText("Haemoglobin").assertDoesNotExist()
        assertEquals(2, runBlocking { CareDatabase.get(ui.activity).care().items(person).first() }.size)
    }

    @Test fun calendarSavesChosenDayAndKeepsUndatedMedicinesOutOfThatSchedule() {
        ui.runOnIdle { vm.saveItem("medicine", "Synthetic prescribed medicine", "As directed by prescriber") }
        saved("Synthetic prescribed medicine")
        openShortcut("shortcut-calendar")
        ui.onNodeWithContentDescription("Next day").performClick()
        ui.onNodeWithContentDescription("Add to calendar").performClick()
        enter("What is planned?", "Clinic follow-up visit"); hideKeyboard()
        ui.onNodeWithText("Save to calendar", useUnmergedTree = true).performScrollTo().performClick()
        val visit = saved("Clinic follow-up visit")
        assertEquals(LocalDate.now().plusDays(1), careDay(visit.due))
        assertEquals("Visit", CareEntryCodec.decode(visit.detail)!!.category)
        ui.onNodeWithText("Clinic follow-up visit").assertIsDisplayed()
        ui.onNodeWithTag("care-calendar").performScrollToNode(hasText("Medicines without a date"))
        ui.onNodeWithText("Medicines without a date").assertIsDisplayed()
        screenshot("calendar-dated-visit")
        recreate(); openShortcut("shortcut-calendar")
        ui.onNodeWithTag("care-calendar").performScrollToNode(hasText("Clinic follow-up visit"))
        ui.onNodeWithText("Clinic follow-up visit").assertIsDisplayed()
        assertEquals(1, calendarItems(vm.items.value, LocalDate.now().plusDays(1)).size)
    }
}
