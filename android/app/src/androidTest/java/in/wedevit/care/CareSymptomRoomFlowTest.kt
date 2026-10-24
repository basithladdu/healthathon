package `in`.wedevit.care

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.*
import androidx.room.Room
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real form, model and Room writes, confined to owned RAM storage and UUID-scoped preferences.
 * Nausea deliberately avoids WebView, microphone, alarms, auth and network calls.
 * Reopening here means a new ViewModel over the same RAM database, not disk/process persistence.
 */
class CareSymptomRoomFlowTest {
    @get:Rule val ui = createComposeRule()

    @Test fun acceptedFormWriteSurvivesNewModelAndReopensActualHistoryEditAndTrend() = withOwnedRoom { fixture, screen ->
        enterNausea("Synthetic Room round-trip note")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.waitUntil(10_000) { screen.accepted.get() == 1 && !screen.model.value!!.busy.value }
        ui.waitUntil(10_000) { screen.results.values.any { it.kind == CareFeedbackKind.SAVED } }
        val stored = fixture.onlyNote()
        val note = requireNotNull(SymptomNoteCodec.decode(stored.detail))
        assertEquals(screen.attemptedId.get(), stored.id)
        assertEquals(fixture.person.id, stored.personId)
        assertEquals("checkin", stored.kind)
        assertEquals("Nausea", note.symptom)
        assertEquals(6, note.rating)
        assertEquals("patient", note.respondent)
        assertEquals(fixture.person.name, note.author)
        assertEquals("Synthetic Room round-trip note", note.note)
        assertTrue(screen.results.values.any { it.kind == CareFeedbackKind.SAVED && it.message == "Symptom note saved" })
        assertFalse(screen.results.values.any { it.kind == CareFeedbackKind.ERROR })
        ui.onNodeWithTag("care-feedback-saved").assertIsDisplayed()
        ui.onNodeWithContentDescription("Dismiss message").performClick()
        ui.onNodeWithTag("symptom-save").assertDoesNotExist()

        // Dispose the first real model and its collectors, then create another over the owned Room database.
        val first = screen.model.value!!
        ui.runOnIdle { screen.model.value = null }
        fixture.clearModel(first)
        val reopened = fixture.createModel()
        ui.runOnIdle { screen.model.value = reopened; screen.mode.value = "history" }
        awaitReady(fixture, reopened, stored.id)
        ui.onNodeWithTag("symptom-trend-${stored.id}").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-trend-baseline").assertTextEquals("First matching entry: ${note.observedDate} ${note.observedTime} · 6/10")
        ui.onNodeWithTag("symptom-trend-chart").assertExists()
        ui.onNodeWithText("Close").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-edit-${stored.id}").performScrollTo().performClick()
        assertRetainedForm("Synthetic Room round-trip note")
        ui.onNodeWithText("Cancel").performClick()
        assertEquals(stored, fixture.onlyNote())
        assertEquals(1, screen.accepted.get())
        assertEquals(0, screen.closed.get())
    }

    @Test fun realRoomInsertFailureKeepsFormAndEmitsOnlyErrorUntilSuccessfulRetry() = withOwnedRoom { fixture, screen ->
        // A genuine SQLite rejection after validation; no fake DAO or fabricated callback result.
        fixture.sql("CREATE TRIGGER fixture_reject_symptom BEFORE INSERT ON CareItem BEGIN SELECT RAISE(ABORT, 'isolated_storage_failure'); END")
        enterNausea("Synthetic retained after storage rejection")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.waitUntil(10_000) { !screen.model.value!!.busy.value && screen.model.value!!.feedback.value.any { it.kind == CareFeedbackKind.ERROR } }
        ui.onNodeWithTag("care-feedback-error-dialog").assertIsDisplayed()
        assertEquals(0, screen.accepted.get())
        assertEquals(0, screen.closed.get())
        assertTrue(fixture.notes().isEmpty())
        assertTrue(screen.results.values.any { it.kind == CareFeedbackKind.ERROR })
        assertFalse(screen.results.values.any { it.kind == CareFeedbackKind.SAVED })
        ui.onNodeWithTag("care-feedback-dismiss").performClick()
        assertRetainedForm("Synthetic retained after storage rejection")
        ui.onNodeWithTag("symptom-save-error").assertTextEquals("Couldn't finish this action. Check the current record before trying again.")

        fixture.sql("DROP TRIGGER fixture_reject_symptom")
        val retainedId = screen.attemptedId.get()
        ui.onNodeWithTag("symptom-save").performClick()
        ui.waitUntil(10_000) { screen.accepted.get() == 1 && !screen.model.value!!.busy.value }
        val stored = fixture.onlyNote()
        assertEquals(retainedId, stored.id)
        assertEquals("Synthetic retained after storage rejection", SymptomNoteCodec.decode(stored.detail)!!.note)
        assertEquals(6, SymptomNoteCodec.decode(stored.detail)!!.rating)
        assertNull(screen.model.value!!.actionError.value)
        assertEquals(0, screen.closed.get())
        ui.onNodeWithTag("care-feedback-saved").assertIsDisplayed()
        ui.onNodeWithTag("symptom-save").assertDoesNotExist()
    }

    @Test fun isolatedStorageCannotBeUsedByTheNormalAccountPathOrPracticeNamespace() {
        val fixture = OwnedRoom()
        try {
            val productionAttempt = runCatching { CareModel(fixture.app, demoMode = false, isolatedDatabase = fixture.db, isolatedStorageNamespace = fixture.namespace) }
            assertTrue(productionAttempt.exceptionOrNull() is IllegalArgumentException)
            val practiceAttempt = runCatching { CareModel(fixture.app, demoMode = true, isolatedDatabase = fixture.db, isolatedStorageNamespace = CareModel.DOCTOR_DEMO_STORAGE) }
            assertTrue(practiceAttempt.exceptionOrNull() is IllegalArgumentException)
            assertTrue(fixture.notes().isEmpty())
        } finally { fixture.close() }
    }

    @Test fun actualTodayShowsDirectSymptomAndCareTeamActionsThenSavesToOwnedRoom() = withOwnedRoom { fixture, screen ->
        ui.runOnIdle { screen.mode.value = "today" }
        ui.onNodeWithTag("today-list").assertIsDisplayed()
        ui.onNodeWithTag("symptom-tracker-entry").assertIsDisplayed()
        ui.onNodeWithText("Record a symptom").assertIsDisplayed()
        ui.onNodeWithText("Your care team").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(1, screen.careTeamOpened.get()) }
        screenshot("today-real-room-main")

        ui.onNodeWithText("Record a symptom").performClick()
        enterNausea("Synthetic Today action round-trip")
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo().assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(6f, 0f..10f, 9)))
        screenshot("today-real-room-rated-form")
        ui.onNodeWithTag("symptom-save").performClick()
        ui.waitUntil(10_000) { screen.accepted.get() == 1 && !screen.model.value!!.busy.value }
        ui.waitUntil(10_000) { screen.results.values.any { it.kind == CareFeedbackKind.SAVED } }
        val stored = fixture.onlyNote()
        val note = requireNotNull(SymptomNoteCodec.decode(stored.detail))
        assertEquals(fixture.person.id, stored.personId)
        assertEquals(screen.attemptedId.get(), stored.id)
        assertEquals("Nausea", note.symptom)
        assertEquals(6, note.rating)
        assertEquals("Synthetic Today action round-trip", note.note)
        assertEquals("patient", note.respondent)
        assertFalse(screen.results.values.any { it.kind == CareFeedbackKind.ERROR })
        ui.onNodeWithTag("symptom-save").assertDoesNotExist()
        ui.onNodeWithTag("care-feedback-saved").assertIsDisplayed()
        ui.onNodeWithTag("symptom-edit-${stored.id}").performScrollTo().assertIsDisplayed()
        screenshot("today-real-room-saved-history")
    }

    private fun enterNausea(text: String) {
        ui.onNodeWithTag("symptom-choice-Nausea").performScrollTo().performClick()
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        ui.onNodeWithText("Anything else? (optional)").performScrollTo().performTextInput(text)
        closeSoftKeyboard()
    }

    private fun assertRetainedForm(text: String) {
        ui.onNodeWithTag("symptom-rating-slider").performScrollTo().assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(6f, 0f..10f, 9)))
        ui.onNodeWithText("Anything else? (optional)").performScrollTo().assertTextContains(text)
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(150) // Allow the native window's final frame; no long polling or artificial result.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "room-flow-proof").apply { mkdirs() }
        try {
            File(directory, "$name.png").outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun awaitReady(fixture: OwnedRoom, model: CareModel, itemId: String? = null) {
        ui.waitUntil(10_000) {
            model.people.value.any { it.id == fixture.person.id } && model.selected.value == fixture.person.id &&
                model.draft.value.personId == fixture.person.id && (itemId == null || model.items.value.any { it.id == itemId })
        }
    }

    private fun withOwnedRoom(block: (OwnedRoom, Screen) -> Unit) {
        val fixture = OwnedRoom()
        val screen = Screen()
        var contentStarted = false
        try {
            val model = fixture.createModel()
            screen.model.value = model
            awaitReady(fixture, model)
            ui.setContent { CareTheme { screen.Content(fixture.person) } }
            contentStarted = true
            block(fixture, screen)
        } finally {
            if (contentStarted) ui.runOnIdle { screen.model.value = null }
            fixture.close()
        }
    }

    private class Screen {
        val model = mutableStateOf<CareModel?>(null)
        val mode = mutableStateOf("new")
        val accepted = AtomicInteger()
        val closed = AtomicInteger()
        val careTeamOpened = AtomicInteger()
        val attemptedId = AtomicReference<String>()
        val results = ConcurrentHashMap<String, CareFeedback>()

        @Composable fun Content(person: Person) {
            val vm = model.value ?: return
            val entries by vm.items.collectAsState()
            val busy by vm.busy.collectAsState()
            val error by vm.actionError.collectAsState()
            val feedback by vm.feedback.collectAsState()
            val host = remember(vm) { SnackbarHostState() }
            SideEffect { feedback.forEach { results.putIfAbsent(it.id, it) } }
            Scaffold(snackbarHost = { CareSnackbarHost(host) }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    if (mode.value == "today") Today(vm, clinician = false, onTalk = {}, onCare = {}, onNearby = {},
                        onAdd = {}, onRead = {}, onSharedWorkspace = { careTeamOpened.incrementAndGet() }, onSymptoms = { mode.value = "new" })
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(entries, key = { it.id }) { SymptomNoteCard(it, vm, hindi = false) }
                    }
                }
            }
            if (mode.value == "new") SymptomNoteForm(person.id, person.name, entries, hindi = false,
                busy = busy, actionError = error, defaultPatientReporter = true,
                onSave = { id, note ->
                    attemptedId.set(id)
                    vm.saveSymptomNote(person.id, id, note, expectedToken = null) {
                        accepted.incrementAndGet()
                        mode.value = "history"
                    }
                }, onClose = { closed.incrementAndGet(); mode.value = "history" })
            CareFeedbackPresenter(feedback, host, vm::dismissFeedback)
        }
    }

    private class OwnedRoom {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val namespace = UUID.randomUUID().toString()
        val person = Person(UUID.randomUUID().toString(), "Synthetic Room patient")
        val db = Room.inMemoryDatabaseBuilder(app, CareDatabase::class.java).build()
        private val models = linkedMapOf<CareModel, ViewModelStore>()
        private val medicineHash = MessageDigest.getInstance("SHA-256").digest("$namespace:${person.id}".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private val preferenceNames = listOf("preferences-$namespace", "device-local-care-contacts-v1-$namespace", "medicine-history-$medicineHash")

        init {
            assertNull(db.openHelper.databaseName)
            preferenceNames.forEach { assertFalse("Fixture namespace must be new", File(app.applicationInfo.dataDir, "shared_prefs/$it.xml").exists()) }
            runBlocking { db.care().addPerson(person) }
        }

        fun createModel(): CareModel = runBlocking {
            withContext(Dispatchers.Main) {
                val store = ViewModelStore()
                val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
                val vm = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = CareModel(app, demoMode = true,
                        isolatedDatabase = db, isolatedStorageNamespace = namespace) as T
                })[CareModel::class.java]
                models[vm] = store
                vm
            }
        }

        fun clearModel(vm: CareModel) = runBlocking {
            val job = vm.viewModelScope.coroutineContext[Job]
            withContext(Dispatchers.Main) { models.remove(vm)?.clear() }
            withTimeout(5_000) { job?.join() }
        }

        fun notes(): List<CareItem> = runBlocking { db.care().allItems() }
        fun onlyNote(): CareItem = notes().single()
        fun sql(statement: String) { db.openHelper.writableDatabase.execSQL(statement) }

        fun close() {
            models.keys.toList().forEach(::clearModel)
            db.close()
            // Flush only this fixture's pending apply(), then remove only its generated preferences.
            preferenceNames.forEach { name ->
                app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().commit()
                app.deleteSharedPreferences(name)
            }
            assertFalse(File(app.filesDir, "recordings-$namespace").exists())
            assertFalse(File(app.filesDir, "reports/$namespace").exists())
        }
    }
}
