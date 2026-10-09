package `in`.wedevit.care

import android.graphics.Bitmap
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
    private var previousRole = false
    private val extraPeople = mutableListOf<String>()
    @Before fun setUp() {
        val account = ui.activity.careModel.cloud
        if (account != null) {
            val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            org.junit.Assume.assumeTrue(account.session.value?.email in listOf(args.getString("careTestOwner"), args.getString("careTestReader")))
            runBlocking { account.signOut() }
            ui.activityRule.scenario.recreate(); ui.waitForIdle()
        }
        ui.runOnIdle { vm = ui.activity.careModel; previousPerson = vm.selected.value; previousRole = vm.clinician.value; vm.role(true); vm.addPerson("Test Person") }
        ui.waitUntil(15000) { vm.people.value.any { it.name == "Test Person" } && vm.draft.value.personId == vm.selected.value && vm.selected.value.isNotBlank() }
        personId = vm.selected.value
    }
    @After fun cleanup() = runBlocking {
        val dao = CareDatabase.get(ui.activity).care()
        (extraPeople + personId).filter { it.isNotBlank() }.forEach { id ->
            dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteDraft(id); dao.deletePersonRow(id)
        }
        ui.runOnIdle { vm = ui.activity.careModel; vm.select(previousPerson); vm.role(previousRole) }
    }
    private fun screenshot(name: String) {
        ui.waitForIdle()
        val file = File(ui.activity.getExternalFilesDir(null), "verification/$name.png"); file.parentFile!!.mkdirs()
        val screen = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        file.outputStream().use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screen.recycle()
    }
    @Test fun conversationReviewSavesExactVersionAndSurvivesRecreation() {
        ui.onNodeWithText("Start talking").assertIsDisplayed()
        screenshot("today")
        ui.onNodeWithText("Talk", useUnmergedTree = true).performClick()
        val original = "What matters: I want to spend time with family.\nStill to discuss: I have not decided about hospital care.\nNext steps: Bring my questions to the next visit."
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasSetTextAction() and hasText("Conversation"))
        ui.onNode(hasSetTextAction() and hasText("Conversation")).performTextInput(original)
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Organise and review"))
        ui.onNodeWithText("Organise and review").performClick()
        ui.onNode(hasSetTextAction() and hasText("What matters")).assertTextContains("I want to spend time with family.")
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Continue to save"))
        ui.onNodeWithText("Continue to save").performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasSetTextAction() and hasText("Doctor's name"))
        ui.onNode(hasSetTextAction() and hasText("Doctor's name")).performTextInput("Test Doctor")
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Save care note"))
        ui.onNodeWithText("Save care note").assertIsNotEnabled()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("I checked every section against the conversation.", substring = true))
        ui.onNodeWithText("I checked every section against the conversation.", substring = true).performClick()
        ui.onNodeWithTag("conversation-list").performScrollToNode(hasText("Save care note"))
        ui.onNodeWithText("Save care note").performClick()
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
    @Test fun directoryFiltersRecoverFromNoResults() {
        ui.onNodeWithText("Nearby", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Karnataka · 33 centres").assertIsDisplayed()
        screenshot("nearby")
        ui.onNode(hasSetTextAction()).performTextInput("No such hospital xyz")
        ui.onNodeWithText("No centres match that search.").assertIsDisplayed()
        ui.onNodeWithTag("nav-Today").performClick()
        ui.onNodeWithTag("nav-Nearby").performClick()
        ui.onNode(hasSetTextAction()).assertTextContains("No such hospital xyz")
        ui.onNodeWithText("Clear filters").performClick()
        ui.onNodeWithText("Karnataka · 33 centres").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).performTextInput("Aster")
        ui.onNodeWithTag("nearby-list").performScrollToNode(hasTestTag("centre-map"))
        ui.waitUntil(30000) { ui.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Map ready")).fetchSemanticsNodes().isNotEmpty() }
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
        ui.onNodeWithContentDescription("Options for Bring questions").performClick()
        ui.onNodeWithText("Edit", useUnmergedTree = true).performClick()
        ui.onNode(hasSetTextAction() and hasText("What needs doing?")).performTextReplacement("Bring the prescription")
        ui.onNodeWithText("Save", useUnmergedTree = true).performScrollTo().performClick()
        ui.waitUntil(15000) { vm.items.value.any { it.title == "Bring the prescription" } }
        ui.onNodeWithTag("nav-Care").performClick()
        ui.onNodeWithText("Bring the prescription").assertIsDisplayed()
        screenshot("care")
        ui.onNodeWithContentDescription("Options for Bring the prescription").performClick()
        ui.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        ui.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        ui.waitUntil(15000) { vm.items.value.none { it.title == "Bring the prescription" } }
        ui.onNodeWithText("Nothing added yet").assertIsDisplayed()
    }
    @Test fun checkinSavesChosenWordsAndKeepsOptionalNote() {
        ui.runOnIdle { vm.role(false) }
        ui.onNodeWithText("How are you?").performClick()
        ui.onNodeWithText("An okay day").performClick()
        screenshot("checkin")
        ui.onNodeWithText("Add a note").performClick()
        ui.onNode(hasSetTextAction() and hasText("Anything to add?")).performTextInput("Spent time with family")
        ui.onNodeWithText("Hide note").performScrollTo().performClick()
        ui.onNodeWithText("Save", useUnmergedTree = true).performScrollTo().performClick()
        ui.waitUntil(15000) { vm.items.value.any { it.kind == "checkin" } }
        assertEquals("Spent time with family", vm.items.value.single { it.kind == "checkin" }.detail)
        ui.onNodeWithTag("today-list").performScrollToNode(hasText("An okay day"))
        ui.onNodeWithText("An okay day").assertIsDisplayed()
        screenshot("today-saved")
        ui.onNodeWithTag("nav-More").performClick()
        ui.onNodeWithText("Privacy").performClick()
        ui.onNodeWithText("You choose which notes to share and with whom.").assertIsDisplayed()
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
            ui.onNode(hasText("Sign in") and hasClickAction()).performScrollTo().performClick()
            ui.waitUntil(20000) { ui.activity.careModel.cloud != null && ui.activity.careModel.people.value.any { it.id == shared.id } }
            if (ui.activity.careModel.selected.value != shared.id) {
                ui.onNodeWithContentDescription("Choose person").performClick()
                ui.onNodeWithText("Shared Test Person").performClick()
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
