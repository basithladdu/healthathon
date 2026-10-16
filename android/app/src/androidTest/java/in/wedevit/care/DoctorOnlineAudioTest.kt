package `in`.wedevit.care

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Rule
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.IOException

/** Mock transport tests are not live Gemini tests. No test opens a microphone or records ambient audio. */
class DoctorOnlineAudioTest {
    @get:Rule val ui = createComposeRule()
    private val source = "Patient: Please give me time to ask questions.\nClinician: Arrange another discussion."
    private val quote = "Patient: Please give me time to ask questions."
    private fun response() = JSONObject().put("provider", "google-gemini").put("model", "test-model")
        .put("sections", JSONObject().apply { doctorAiSectionKeys.forEach { put(it, if (it == "questions") quote else "") } })
        .put("evidence", JSONArray().put(JSONObject().put("section", "questions").put("quote", quote)))
        .put("warnings", JSONArray().put("AI draft: check against source before review."))

    @Test fun recordDialogStartsWithSeparateConsentUncheckedAndNeverOpensMicrophone() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val namespace = "doctor-workflow-audio-dialog-${UUID.randomUUID()}"
        val store = DoctorWorkflowStore(context, preferencesName = namespace)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controller = DoctorAudioController(context, "demo-asha", "test_${UUID.randomUUID().toString().replace('-', '_')}", scope)
        val signedIn = mutableStateOf(false) // UI-only injected state: no service or fake token is used.
        try {
            ui.setContent { CareTheme { Column(Modifier.verticalScroll(rememberScrollState())) { DoctorAudioCapture(controller, signedIn.value, false, true, onSignIn = {}, onTranscribe = { error("No transport may run in this test") }) } } }
            ui.onNodeWithTag("doctor-record-audio").performScrollTo().assertIsNotEnabled()
            screenshot("doctor-audio-signed-out")
            ui.runOnIdle { signedIn.value = true }
            ui.onNodeWithTag("doctor-record-audio").performScrollTo().performClick()
            listOf("purpose", "storage", "access", "cloud").forEach { field ->
                ui.onNodeWithTag("doctor-record-$field").performScrollTo().assertIsOff()
            }
            ui.onNodeWithTag("doctor-start-recording").assertIsDisplayed().assertIsNotEnabled()
            screenshot("doctor-audio-consent-unchecked")
            ui.onNodeWithTag("doctor-record-participants").performScrollTo().performTextReplacement("Fictional test voice")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            ui.onNodeWithTag("doctor-start-recording").assertIsDisplayed().assertIsNotEnabled()
            ui.onNodeWithText("Cancel").performClick()
            ui.onNodeWithTag("doctor-start-recording").assertDoesNotExist()
            assertTrue(store.draft("demo-asha").sourceText.isBlank())
            assertTrue(store.versions("demo-asha").isEmpty())
        } finally { controller.close(); scope.cancel(); context.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun parserAcceptsOnlyExactSourceQuotesAndLeavesUnknownSectionsBlank() {
        val parsed = parseDoctorAiDraft(response().toString(), source)
        assertEquals(quote, parsed.sections["questions"])
        assertEquals("", parsed.sections["priorities"])
        assertEquals("", parsed.sections["nextSteps"])
        assertTrue(parsed.evidence.all { source.contains(it.quote) })
        val invented = response().apply { getJSONObject("sections").put("priorities", "Patient agrees to treatment.") }
        assertTrue(runCatching { parseDoctorAiDraft(invented.toString(), source) }.isFailure)
        val falseQuote = response().apply { getJSONArray("evidence").getJSONObject(0).put("quote", "Patient agrees to treatment.") }
        assertTrue(runCatching { parseDoctorAiDraft(falseQuote.toString(), source) }.isFailure)
        assertTrue(runCatching { parseDoctorAiDraft(response().put("provider", "unknown").toString(), source) }.isFailure)
        val clipped = response().apply { getJSONObject("sections").put("questions", "Please give me time to ask questions."); getJSONArray("evidence").getJSONObject(0).put("quote", "Please give me time to ask questions.") }
        assertTrue(runCatching { parseDoctorAiDraft(clipped.toString(), source) }.isFailure)
        val negation = response().apply { getJSONObject("sections").put("questions", "want treatment"); getJSONArray("evidence").getJSONObject(0).put("quote", "want treatment") }
        assertTrue(runCatching { parseDoctorAiDraft(negation.toString(), "Patient: I do not want treatment.") }.isFailure)
        val unknownSpeaker = response().apply { getJSONObject("sections").put("questions", "").put("priorities", "spk_1: I want treatment."); getJSONArray("evidence").getJSONObject(0).put("section", "priorities").put("quote", "spk_1: I want treatment.") }
        assertTrue(runCatching { parseDoctorAiDraft(unknownSpeaker.toString(), "spk_1: I want treatment.") }.isFailure)
    }

    @Test fun cancellingMockBlockedUploadDisconnectsImmediatelyWithoutNetwork() = runBlocking {
        val waiting = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val endpoint = URL(null, "https://mock.invalid/cancel", object : URLStreamHandler() {
            override fun openConnection(url: URL) = object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() { disconnected.countDown() }
                override fun usingProxy() = false
                override fun getOutputStream() = ByteArrayOutputStream()
                override fun getResponseCode(): Int {
                    waiting.countDown()
                    disconnected.await(5, TimeUnit.SECONDS)
                    throw IOException("Mock connection closed")
                }
            }
        })
        val file = syntheticFile()
        try {
            val job = launch(Dispatchers.Default) { DoctorAiClient(ConversationTranscriber(endpoint)).transcribeAndDraft(file, RecordingConsent(file.absolutePath, "demo-asha", "Synthetic test voice", 1, 2, noticeVersion = 2), "fictional-test-token-not-real-auth") }
            assertTrue("Mock upload must reach blocked response", waiting.await(2, TimeUnit.SECONDS))
            job.cancel()
            assertTrue("Cancellation must disconnect blocked I/O promptly", disconnected.await(1, TimeUnit.SECONDS))
            withTimeout(2000) { job.join() }
            assertTrue(job.isCancelled)
        } finally { file.delete() }
    }

    @Test fun explicitConsentAndRealSessionAreRequiredBeforeAnyTransportConnection() = runBlocking {
        var opened = 0
        val endpoint = URL(null, "https://mock.invalid/draft", object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection { opened++; error("No connection should be opened") }
        })
        val client = DoctorAiClient(ConversationTranscriber(endpoint))
        val file = syntheticFile()
        val consent = RecordingConsent(file.absolutePath, "demo-asha", "Synthetic test voice", 1, 2, noticeVersion = 2)
        try {
            assertTrue(runCatching { client.transcribeAndDraft(file, consent.copy(transcriptionConsentAt = 0), "fictional-test-token-not-real-auth") }.isFailure)
            assertTrue(runCatching { client.transcribeAndDraft(file, consent, "") }.isFailure)
            assertTrue(runCatching { client.transcribeAndDraft(file, consent.copy(noticeVersion = 1), "fictional-test-token-not-real-auth") }.isFailure)
        } finally { file.delete() }
        assertEquals(0, opened)
    }

    @Test fun mockHttpTransportProducesSourceGroundedDraftWithoutPublishingAnything() = runBlocking {
        val output = ByteArrayOutputStream()
        var disconnected = false
        val endpoint = URL(null, "https://mock.invalid/draft", object : URLStreamHandler() {
            override fun openConnection(url: URL) = object : HttpURLConnection(url) {
                override fun connect() {}
                override fun disconnect() { disconnected = true }
                override fun usingProxy() = false
                override fun getOutputStream() = output
                override fun getResponseCode() = 200
                override fun getContentType() = "application/json"
                override fun getInputStream() = ByteArrayInputStream(JSONObject().put("transcript", source).put("provider", "google-gemini").put("model", "test-transcriber").put("languages", JSONArray().put("en")).put("draft", response()).toString().toByteArray())
            }
        })
        val file = syntheticFile()
        val result = try { DoctorAiClient(ConversationTranscriber(endpoint)).transcribeAndDraft(file, RecordingConsent(file.absolutePath, "demo-asha", "Synthetic test voice", 1, 2, noticeVersion = 2), "fictional-test-token-not-real-auth") } finally { file.delete() }
        assertEquals("test-model", result.draft!!.model)
        assertEquals(source, result.transcript.text)
        assertNull(result.draftError)
        assertTrue(output.size() > 0)
        assertTrue(disconnected)
        // No store, CareCloud, sign-in, audio or publication operation is used by this mock test.
    }

    @Test fun importedTranscriptAndAiProvenancePersistButStaleOrEditedTextIsNeverReplaced() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val namespace = "doctor-workflow-ai-test-${UUID.randomUUID()}"
        val store = DoctorWorkflowStore(context, preferencesName = namespace)
        val draft = parseDoctorAiDraft(response().toString(), source)
        val firstAccount = doctorWorkflowNamespace(UUID.randomUUID().toString())
        val secondAccount = doctorWorkflowNamespace(UUID.randomUUID().toString())
        try {
            assertEquals("doctor-workflow-v1", doctorWorkflowNamespace(null))
            assertTrue(runCatching { doctorWorkflowNamespace("not-a-valid-account-id") }.isFailure)
            val firstStore = DoctorWorkflowStore(context, preferencesName = firstAccount)
            val secondStore = DoctorWorkflowStore(context, preferencesName = secondAccount)
            assertNull(firstStore.saveDraft(WorkflowDraft("demo-asha", sourceText = "Account A fictional source")))
            assertTrue(secondStore.draft("demo-asha").sourceText.isBlank())
            assertEquals("Account A fictional source", DoctorWorkflowStore(context, preferencesName = firstAccount).draft("demo-asha").sourceText)
            assertNull(store.importTranscript("demo-asha", source, "google-gemini", "test-transcriber", "fictional-clip", 2, ""))
            assertNotNull(store.importTranscript("demo-asha", "Different source", "google-gemini", "test-transcriber", "other", 2, ""))
            assertNotNull(store.applyAiDraft("demo-asha", draft, "stale source"))
            assertNull(store.applyAiDraft("demo-asha", draft, source))
            assertTrue(store.versions("demo-asha").isEmpty())
            assertEquals("google-gemini", store.draft("demo-asha").aiProvider)
            assertEquals("fictional-clip", store.draft("demo-asha").audioClipId)
            assertEquals(store.draft("demo-asha"), DoctorWorkflowStore(context, preferencesName = namespace).draft("demo-asha"))
            assertNull(store.saveDraft(store.draft("demo-asha").copy(questions = "Manual fictional question.")))
            assertEquals("", store.draft("demo-asha").aiProvider)
            assertTrue(store.draft("demo-asha").evidence.isEmpty())
            assertNotNull(store.applyAiDraft("demo-asha", draft, source))
            assertNull(store.saveDraft(store.draft("demo-asha").copy(sourceText = "Manually corrected source.")))
            assertEquals("", store.draft("demo-asha").transcriptProvider)
            assertEquals("", store.draft("demo-asha").audioClipId)
            assertTrue(store.draft("demo-ravi").sourceText.isBlank())
        } finally { listOf(namespace, firstAccount, secondAccount).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() } }
    }

    private fun syntheticFile(): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return File(instrumentation.targetContext.cacheDir, "doctor-synthetic-${UUID.randomUUID()}.m4a").also { file ->
            instrumentation.context.assets.open("synthetic-transcription-test.m4a").use { input -> file.outputStream().use { input.copyTo(it) } }
        }
    }

    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(500)
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        target.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun recorderRejectsMissingParticipantConsentBeforeOpeningMicrophone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val account = "test_${UUID.randomUUID().toString().replace('-', '_')}"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controller = DoctorAudioController(context, "demo-asha", account, scope)
        try {
            controller.start("Fictional test voice", emptySet(), false, false, false, false)
            assertFalse(controller.state.value.recording)
            assertNotNull(controller.state.value.error)
            assertTrue(controller.state.value.clips.isEmpty())
            controller.release()
            assertFalse(controller.state.value.recording)
            val other = DoctorAudioController(context, "demo-ravi", account, scope)
            try { assertTrue(other.state.value.clips.isEmpty()) } finally { other.release() }
        } finally {
            controller.release(); scope.cancel()
        }
    }
}
