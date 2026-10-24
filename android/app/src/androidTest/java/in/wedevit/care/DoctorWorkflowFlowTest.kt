package `in`.wedevit.care

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Isolated fictional local work only: no CareRoot, real account, network, audio or patient data. */
class DoctorWorkflowFlowTest {
    @get:Rule val ui = createComposeRule()

    @Test fun doctorCanScheduleContinueReviewAndPublishWithSeparateResponsesAtLargeText() {
        withStore { context, workflow, _ ->
            val demoNamespace = "doctor-demo-workflow-ui-${UUID.randomUUID()}"
            val appointments = DoctorDemoStore(context, preferencesName = demoNamespace)
            appointments.appointments().filter { it.status == "Scheduled" }.forEach { assertNull(appointments.cancel(it.id)) }
            val fontScale = mutableFloatStateOf(1.5f)
            try {
                ui.setContent {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue)) {
                        CareTheme { DoctorDemo(onExit = {}, store = appointments, workflowStore = workflow) }
                    }
                }
                ui.onNodeWithTag("demo-login").performScrollTo().performClick()
                clickTag("workflow-open-requests", "demo-home-list")
                clickTag("workflow-accept-request-demo-asha", "workflow-request-list")
                assertEquals(WorkflowRequestStatus.ACCEPTED, workflow.requests("demo-asha").single().status)
                screenshot("workflow-requests-large")
                clickTag("workflow-schedule-request-demo-asha", "workflow-request-list")
                ui.onNodeWithText("Patient linked to the conversation request").assertExists()
                ui.onNodeWithTag("demo-change-patient").assertDoesNotExist()
                ui.onNodeWithTag("demo-appointment-note").performScrollTo().performTextInput("Fictional requested discussion")
                closeSoftKeyboard()
                ui.onNodeWithTag("demo-save-appointment").performScrollTo().performClick()
                ui.waitForIdle()
                val request = workflow.requests("demo-asha").single()
                assertEquals(WorkflowRequestStatus.SCHEDULED, request.status)
                assertEquals("demo-asha", appointments.appointments().single { it.id == request.appointmentId }.patientId)
                clickTag("workflow-request-request-demo-asha", "workflow-request-list")
                ui.onNodeWithText("Previous discussion on record").assertExists()
                assertTrue(workflow.draft("demo-asha").checklist.all { it.status == WorkflowTopicStatus.NOT_DISCUSSED })
                clickTag("workflow-step-Document")
                fill("record-transcript", "Patient asks for time to prepare questions.")
                fill("workflow-field-questions", "Ask about the next conversation.")
                clickTag("workflow-finish-later")
                ui.onNodeWithTag("workflow-open-conversation").performScrollTo().performClick()
                clickTag("workflow-step-Document")
                ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag("record-transcript"))
                ui.onNodeWithTag("record-transcript").assertTextContains("Patient asks for time to prepare questions.")
                screenshot("workflow-resumed-source-large")
                clickTag("workflow-back")
                ui.onNodeWithTag("demo-nav-Schedule").performClick()
                val cancel = hasText("Cancel visit") and hasAnyAncestor(hasTestTag("demo-appointment-${request.appointmentId}"))
                ui.onNodeWithTag("demo-schedule-list").performScrollToNode(cancel)
                ui.onNode(cancel).performClick()
                ui.onNodeWithTag("demo-confirm-cancel").performClick()
                ui.waitForIdle()
                assertEquals(WorkflowRequestStatus.DEFERRED, workflow.requests("demo-asha").single().status)
                ui.onNodeWithTag("demo-nav-Patients").performClick()
                ui.onNodeWithTag("demo-patient-search").performTextInput("Ravi")
                ui.onNodeWithTag("demo-patient-demo-ravi").performClick()
                ui.onNodeWithTag("demo-patient-hub").performScrollToNode(hasTestTag("demo-record-Conversations"))
                ui.onNodeWithTag("demo-record-Conversations").performClick()
                ui.onNodeWithTag("workflow-open-conversation").performScrollTo().performClick()
                clickTag("workflow-topic-0-DEFERRED")
                clickTag("workflow-step-Document")
                clickTag("workflow-load-mock")
                assertTrue(workflow.draft("demo-ravi").mockGenerated)
                assertEquals(WorkflowTopicStatus.DEFERRED, workflow.draft("demo-ravi").checklist.first().status)
                assertEquals(DoctorWorkflowData.sources("demo-ravi").last().text, workflow.draft("demo-ravi").sourceText)
                ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag("workflow-source-text-demo-ravi-fixture"))
                ui.onNodeWithTag("workflow-source-text-demo-ravi-fixture").assertTextEquals(DoctorWorkflowData.sources("demo-ravi").last().text)
                clickTag("workflow-step-Prepare")
                clickTag("workflow-topic-0-DEFERRED")
                assertEquals(WorkflowTopicStatus.DEFERRED, workflow.draft("demo-ravi").checklist.first().status)
                fill("workflow-consent-participant", "Fictional patient")
                ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag("workflow-consent-recording"))
                ui.onNodeWithTag("workflow-consent-recording").assertIsOff()
                ui.onNodeWithTag("workflow-consent-cloud").assertIsOff()
                clickTag("workflow-save-consent")
                assertFalse(workflow.snapshot().consents.single().recordingAgreed)
                assertFalse(workflow.snapshot().consents.single().cloudProcessingAgreed)
                clickTag("workflow-step-Review")
                clickTag("workflow-publish")
                assertTrue(workflow.versions("demo-ravi").isEmpty())
                fill("workflow-reviewer", "Fictional reviewer")
                clickTag("workflow-source-confirmed")
                clickTag("workflow-publish")
                assertTrue(workflow.versions("demo-ravi").isEmpty())
                clickTag("workflow-review-checked")
                screenshot("workflow-review-large")
                clickTag("workflow-publish")
                val first = workflow.versions("demo-ravi").single()
                clickTag("workflow-ack-demo-ravi-patient-READ", "workflow-version-reader")
                clickTag("workflow-ack-demo-ravi-family-CONCERN", "workflow-version-reader")
                assertEquals(2, workflow.acknowledgements(first.id).size)
                screenshot("workflow-version-responses-large")
                clickTag("workflow-export-pdf", "workflow-version-reader")
                ui.onNodeWithTag("workflow-version-reader").performScrollToNode(hasTestTag("workflow-pdf-ready"))
                ui.onNodeWithTag("workflow-pdf-ready").assertIsDisplayed()
                clickTag("workflow-version-back", "workflow-version-reader")
                clickTag("workflow-step-Document")
                fill("workflow-field-nextSteps", "Arrange another fictional discussion next week.")
                clickTag("workflow-step-Review")
                clickTag("workflow-source-confirmed")
                clickTag("workflow-review-checked")
                clickTag("workflow-publish")
                val second = workflow.versions("demo-ravi").first()
                assertEquals(2, second.number)
                assertEquals(first, workflow.versions("demo-ravi").last())
                assertTrue(workflow.acknowledgements(second.id).isEmpty())
                clickTag("workflow-ack-demo-ravi-family-NOT_READY", "workflow-version-reader")
                assertEquals(WorkflowAckStatus.NOT_READY, workflow.acknowledgements(second.id).single().status)
                assertTrue(workflow.versions("demo-asha").isEmpty())
                assertTrue(workflow.activity("demo-ravi").any { it.action.contains("not externally delivered") })
                ui.runOnIdle { fontScale.floatValue = 1f }
                ui.onNodeWithTag("demo-nav-Home").performClick()
                screenshot("workflow-home-normal")
                clickTag("workflow-open-requests", "demo-home-list")
                screenshot("workflow-requests-normal")
                clickTag("workflow-accept-request-demo-leela", "workflow-request-list")
                clickTag("workflow-defer-request-demo-leela", "workflow-request-list")
                clickTag("workflow-accept-request-demo-leela", "workflow-request-list")
                assertEquals(WorkflowRequestStatus.ACCEPTED, workflow.requests("demo-leela").single().status)
                clickTag("workflow-request-request-demo-ravi", "workflow-request-list")
                screenshot("workflow-conversation-normal")
            } finally {
                context.getSharedPreferences(demoNamespace, Context.MODE_PRIVATE).edit().clear().commit()
            }
        }
    }

    private fun clickTag(tag: String, container: String = "workflow-conversation") {
        ui.onNodeWithTag(container).performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performClick()
    }
    private fun fill(tag: String, text: String) {
        ui.onNodeWithTag("workflow-conversation").performScrollToNode(hasTestTag(tag))
        ui.onNodeWithTag(tag).performTextReplacement(text)
        closeSoftKeyboard()
    }

    @Test fun scheduledRequestsRequireCorrectSavedPatientAndCorruptStorageIsNotOverwritten() {
        withStore { context, store, namespace ->
            val requestId = "request-demo-asha"
            assertNull(store.transitionRequest(requestId, WorkflowRequestStatus.ACCEPTED))
            val wrong = DoctorDemoAppointment("wrong-patient", "demo-ravi", System.currentTimeMillis() + 86_400_000, 30, "Phone")
            assertNotNull(store.transitionRequest(requestId, WorkflowRequestStatus.SCHEDULED, wrong.id) { wrong })
            val correct = wrong.copy(id = "correct-patient", patientId = "demo-asha")
            assertNull(store.transitionRequest(requestId, WorkflowRequestStatus.SCHEDULED, correct.id) { correct })
            assertEquals(correct.id, store.requests("demo-asha").single().appointmentId)
            assertEquals(correct.id, DoctorWorkflowStore(context, preferencesName = namespace).requests("demo-asha").single().appointmentId)
            assertNotNull(store.saveDraft(WorkflowDraft("demo-asha", sourceText = "Fictional source", sourceIds = listOf("demo-ravi-fixture"))))
            val preferences = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
            assertTrue(preferences.edit().putString("state", "not valid JSON").commit())
            val reopened = DoctorWorkflowStore(context, preferencesName = namespace)
            assertTrue(reopened.snapshot().requests.isEmpty())
            assertNotNull(reopened.error())
            assertNotNull(reopened.saveDraft(WorkflowDraft("demo-asha", sourceText = "Do not overwrite damaged saved data")))
            assertEquals("not valid JSON", preferences.getString("state", null))
        }
    }

    @Test fun publishedVersionsKeepDistinctMemberResponsesAndDoNotLeakAcrossPatients() {
        withStore { context, store, namespace ->
            assertNull(store.loadMockDraft("demo-asha"))
            val fixture = store.draft("demo-asha")
            assertEquals(DoctorWorkflowData.sources("demo-asha").last().text, fixture.sourceText)
            assertTrue(fixture.priorities.isBlank()) // The fixture must not invent a preference.
            assertTrue(fixture.checklist.all { it.status == WorkflowTopicStatus.NOT_DISCUSSED })
            assertNull(store.saveDraft(fixture.copy(sourceText = "Edited fictional source; discuss questions only.")))
            assertTrue(store.draft("demo-asha").sourceIds.isEmpty())
            assertFalse(store.draft("demo-asha").mockGenerated)
            assertEquals("Edited fictional source; discuss questions only.", store.draft("demo-asha").sourceText)
            assertNull(store.saveDraft(fixture))
            assertNotNull(store.publish("demo-asha", "", true, true))
            assertNotNull(store.publish("demo-asha", "Fictional reviewer", false, true))
            assertNotNull(store.publish("demo-asha", "Fictional reviewer", true, false))
            assertTrue(store.versions("demo-asha").isEmpty())
            assertNull(store.publish("demo-asha", "Fictional reviewer", true, true))
            val first = store.versions("demo-asha").single()
            assertTrue(first.reviewLabel.contains("Unverified"))
            assertNull(store.recordToldToClinician(first.id, "demo-asha-patient", WorkflowAckStatus.READ))
            assertNull(store.recordToldToClinician(first.id, "demo-asha-family", WorkflowAckStatus.CONCERN))
            assertEquals(2, store.acknowledgements(first.id).size)
            assertNotNull(store.recordToldToClinician(first.id, "demo-ravi-family", WorkflowAckStatus.READ))
            assertNull(store.saveDraft(fixture.copy(nextSteps = "A later fictional discussion.")))
            assertNull(store.publish("demo-asha", "Fictional reviewer", true, true))
            val second = store.versions("demo-asha").first()
            assertEquals(2, second.number)
            assertEquals(first, store.versions("demo-asha").last())
            assertTrue(store.acknowledgements(second.id).isEmpty())
            assertNull(store.recordToldToClinician(second.id, "demo-asha-patient", WorkflowAckStatus.NOT_READY))
            assertEquals(WorkflowAckStatus.NOT_READY, store.acknowledgements(second.id).single().status)
            assertTrue(store.versions("demo-ravi").isEmpty())
            assertTrue(store.draft("demo-ravi").sourceText.isBlank())
            assertTrue(store.activity("demo-asha").any { it.action.contains("not externally delivered") })
            assertEquals(store.snapshot(), DoctorWorkflowStore(context, preferencesName = namespace).snapshot())
        }
    }

    @Test fun longPublishedPreviewExportsToMultipleReadablePages() {
        withStore { context, store, _ ->
            val draft = DoctorWorkflowData.mockDraft("demo-asha", System.currentTimeMillis())
                .copy(sourceText = (1..130).joinToString("\n") { "Fictional source line $it: questions remain open; no treatment decision is recorded." })
            assertNull(store.saveDraft(draft))
            assertNull(store.publish("demo-asha", "Fictional reviewer", true, true))
            val file = DoctorWorkflowPdf.create(context, store.versions("demo-asha").single(), "Asha (fictional)")
            assertTrue(file.exists())
            assertTrue(file.length() > 0)
            val artifact = File(context.getExternalFilesDir(null), "verification/workflow-long-preview.pdf")
            artifact.parentFile!!.mkdirs()
            file.copyTo(artifact, overwrite = true)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertTrue("Long source must paginate rather than clip", renderer.pageCount > 1)
                    listOf(0, renderer.pageCount - 1).forEach { index ->
                        renderer.openPage(index).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            File(artifact.parentFile, "workflow-pdf-page-${index + 1}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    private fun withStore(block: (Context, DoctorWorkflowStore, String) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val namespace = "doctor-workflow-ui-${UUID.randomUUID()}"
        val account = context.getSharedPreferences("care-account-kjnhtakqzboayqorrlue", Context.MODE_PRIVATE)
        val before = account.all.toMap()
        try { block(context, DoctorWorkflowStore(context, preferencesName = namespace), namespace) }
        finally {
            assertEquals(before, account.all)
            context.getSharedPreferences(namespace, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun screenshot(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "verification/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
