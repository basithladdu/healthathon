package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorConversationsDataTest {
    private val patient = "demo-asha"
    private fun version(id: String, number: Int, at: Long, person: String = patient) = WorkflowVersion(id, person, number,
        WorkflowDraft(person, questions = "Patient: Please explain the choices.", sourceText = "Patient: Please explain the choices."), "Typed reviewer", at)

    @Test fun validatedPublishedOrderMatchesOverviewAndKeepsDraftSeparate() {
        val versions = listOf(version("old", 1, 900), version("new", 2, 800))
        val draft = WorkflowDraft(patient, priorities = "Unfinished manual words", updatedAt = 999)
        val model = DoctorConversationsData.build(patient, versions, draft, 1000)!!
        assertEquals("new", model.latestPublished!!.id)
        assertEquals(listOf("old"), model.earlierPublished.map { it.id })
        assertEquals(draft, model.currentDraft); assertTrue(model.draftHasContent)
        assertEquals(999L, model.currentDraftUpdatedAt)
        assertEquals(800L, model.latestPublished!!.publishedAt)
        assertEquals(DoctorOverviewData.build(patient, versions, emptyList(), 1000)!!.history.filter { it.isPublishedPreview }, listOf(model.latestPublished) + model.earlierPublished)
    }
    @Test fun wrongPatientFutureAndInvalidVersionsCannotLeakIntoHistory() {
        val invalid = listOf(version("foreign", 9, 900, "demo-ravi"), version("future", 8, 2000), version("zero", 0, 900), version("mismatch", 7, 900).copy(draft = WorkflowDraft("demo-ravi", sourceText = "Foreign", questions = "Foreign")))
        val model = DoctorConversationsData.build(patient, invalid, WorkflowDraft("demo-ravi", sourceText = "Foreign draft"), 1000)!!
        assertNull(model.latestPublished); assertTrue(model.earlierPublished.isEmpty()); assertNull(model.currentDraft)
        assertEquals(patient, model.priorSample!!.patientId)
    }
    @Test fun priorSamplePreservesExactOriginalAndUnknownDateAndAuthor() {
        val model = DoctorConversationsData.build(patient, emptyList(), null, 1000)!!
        val prior = model.priorSample!!
        assertEquals(DoctorWorkflowData.sources(patient).first { it.id == "$patient-prior" }.text, prior.sourceText)
        assertNull(prior.publishedAt); assertNull(prior.versionNumber); assertNull(prior.reviewerName)
        assertFalse(prior.isPublishedPreview); assertNull(model.latestPublished)
        assertEquals("", prior.priorities)
    }
    @Test fun emptyChecklistOnlyDraftIsNotAPublishedOrCurrentConversation() {
        val checklist = WorkflowDraft(patient, updatedAt = 900, checklist = DoctorWorkflowData.checklist().map { it.copy(status = WorkflowTopicStatus.DEFERRED) })
        val model = DoctorConversationsData.build(patient, emptyList(), checklist, 1000)!!
        assertFalse(model.draftHasContent); assertNull(model.currentDraftUpdatedAt); assertTrue(model.earlierPublished.isEmpty())
        assertNull(DoctorConversationsData.build("unknown", emptyList(), null, 1000))
        val futureClockDraft = WorkflowDraft(patient, sourceText = "Keep these words", updatedAt = 2000)
        val future = DoctorConversationsData.build(patient, emptyList(), futureClockDraft, 1000)!!
        assertEquals(futureClockDraft, future.currentDraft); assertNull(future.currentDraftUpdatedAt)
    }
    @Test fun allExistingPatientSourcesRemainScopedAndUnmodified() {
        DoctorDemoData.patients.forEach { person ->
            val model = DoctorConversationsData.build(person.id, emptyList(), WorkflowDraft(person.id, sourceText = "Manually typed source"), 1000)!!
            assertEquals(person.careNotes.joinToString("\n"), model.priorSample!!.sourceText)
            assertEquals("Manually typed source", model.currentDraft!!.sourceText)
            assertNull(model.currentDraftUpdatedAt)
        }
    }
}
