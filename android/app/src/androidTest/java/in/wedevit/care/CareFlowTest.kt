package `in`.wedevit.care

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
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
    private val extraPeople = mutableListOf<String>()
    @Before fun setUp() {
        ui.runOnIdle { vm = ViewModelProvider(ui.activity)[CareModel::class.java]; vm.role(true); vm.addPerson("Test Person") }
        ui.waitUntil(15000) { vm.people.value.any { it.name == "Test Person" } && vm.draft.value.personId == vm.selected.value && vm.selected.value.isNotBlank() }
        personId = vm.selected.value
    }
    @After fun cleanup() = runBlocking {
        val dao = CareDatabase.get(ui.activity).care()
        (extraPeople + personId).filter { it.isNotBlank() }.forEach { id ->
            dao.deleteAcknowledgements(id); dao.deleteVersions(id); dao.deleteItems(id); dao.deleteDraft(id); dao.deletePersonRow(id)
        }
    }
    private fun screenshot(name: String) {
        val file = File(ui.activity.getExternalFilesDir(null), "verification/$name.png"); file.parentFile!!.mkdirs()
        file.outputStream().use { ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun conversationReviewSavesExactVersionAndSurvivesRecreation() {
        ui.onNodeWithText("Make room for\nwhat matters.").assertIsDisplayed()
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
        ui.onNodeWithText("Clear filters").performClick()
        ui.onNodeWithText("Karnataka · 33 centres").assertIsDisplayed()
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
