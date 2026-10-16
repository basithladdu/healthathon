package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorWorkflowTest {
    @Test fun requestsHaveExplicitTransitions() {
        assertTrue(DoctorWorkflowData.transitionAllowed(WorkflowRequestStatus.NEW, WorkflowRequestStatus.ACCEPTED))
        assertFalse(DoctorWorkflowData.transitionAllowed(WorkflowRequestStatus.NEW, WorkflowRequestStatus.COMPLETED))
        assertTrue(DoctorWorkflowData.transitionAllowed(WorkflowRequestStatus.DEFERRED, WorkflowRequestStatus.ACCEPTED))
        assertFalse(DoctorWorkflowData.transitionAllowed(WorkflowRequestStatus.COMPLETED, WorkflowRequestStatus.ACCEPTED))
    }
    @Test fun checklistDoesNotInventDiscussion() {
        assertTrue(DoctorWorkflowData.checklist().all { it.status == WorkflowTopicStatus.NOT_DISCUSSED })
        assertTrue(DoctorWorkflowData.mockDraft("demo-asha", 100).checklist.all { it.status == WorkflowTopicStatus.NOT_DISCUSSED })
    }
    @Test fun fixtureHasTraceableSourceAndNoInventedPriorities() {
        val draft = DoctorWorkflowData.mockDraft("demo-asha", 100)
        assertTrue(draft.mockGenerated)
        assertEquals("", draft.priorities)
        assertTrue(draft.sourceIds.all { id -> DoctorWorkflowData.sources(draft.patientId).any { it.id == id } })
        assertEquals(12, DoctorWorkflowData.sources(draft.patientId).last().timestampSeconds)
    }
    @Test fun patientSourcesAndMembershipsAreIsolated() {
        val asha = DoctorWorkflowData.members("demo-asha")
        val ravi = DoctorWorkflowData.members("demo-ravi")
        assertTrue(asha.none { a -> ravi.any { it.id == a.id } })
        assertTrue(DoctorWorkflowData.sources("missing").isEmpty())
        assertTrue(DoctorWorkflowData.attachments("demo-asha").all { it.patientId == "demo-asha" })
    }
    @Test fun publicationRequiresReviewNameAndExplicitSourceCheck() {
        val draft = DoctorWorkflowData.mockDraft("demo-asha", 100)
        assertNotNull(DoctorWorkflowData.publishError(draft, "", true, true))
        assertNotNull(DoctorWorkflowData.publishError(draft, "Reviewer", false, true))
        assertNotNull(DoctorWorkflowData.publishError(draft, "Reviewer", true, false))
        assertNotNull(DoctorWorkflowData.publishError(draft.copy(sourceText = ""), "Reviewer", true, true))
        assertNull(DoctorWorkflowData.publishError(draft, "Reviewer", true, true))
    }
    @Test fun savedLaterDoesNotAlterPublishedVersion() {
        val draft = DoctorWorkflowData.mockDraft("demo-asha", 100)
        val version = WorkflowVersion("v1", draft.patientId, 1, draft, "Reviewer", 100)
        val later = draft.copy(nextSteps = "Later draft", updatedAt = 200)
        assertNotEquals(later.nextSteps, version.draft.nextSteps)
        assertTrue(version.reviewLabel.contains("Unverified"))
        assertEquals("UNVERIFIED", DoctorWorkflowData.verificationStatus)
    }
    @Test fun acknowledgementsBelongToPersonAndExactVersion() {
        val first = WorkflowAcknowledgement("v1", "demo-asha-patient", WorkflowAckStatus.READ, 100)
        val family = WorkflowAcknowledgement("v1", "demo-asha-family", WorkflowAckStatus.CONCERN, 101)
        val later = WorkflowAcknowledgement("v2", "demo-asha-patient", WorkflowAckStatus.NOT_READY, 102)
        assertEquals(2, listOf(first, family, later).filter { it.versionId == "v1" }.size)
        assertEquals(WorkflowAckStatus.NOT_READY, later.status)
        assertNotEquals(first.memberId, family.memberId)
    }
    @Test fun seedsContainOnlyKnownPatientsAndNoConsent() {
        val state = DoctorWorkflowData.seed(100_000)
        assertTrue(state.requests.all { it.patientId in DoctorWorkflowData.patientIds })
        assertTrue(state.consents.isEmpty())
        assertTrue(state.acknowledgements.isEmpty())
        assertTrue(state.versions.isEmpty())
    }
    @Test fun editedFixturePreservesTextButDropsFixtureClaims() {
        val fixture = DoctorWorkflowData.mockDraft("demo-asha", 100)
        val edited = fixture.copy(sourceText = "Manually entered discussion text")
        val normalized = DoctorWorkflowData.normalizeDraftSource(edited)
        assertEquals(edited.sourceText, normalized.sourceText)
        assertTrue(normalized.sourceIds.isEmpty())
        assertFalse(normalized.mockGenerated)
        assertEquals(fixture, DoctorWorkflowData.normalizeDraftSource(fixture))
    }
    @Test(expected = IllegalArgumentException::class) fun foreignPatientSourceIsRejected() {
        DoctorWorkflowData.normalizeDraftSource(DoctorWorkflowData.mockDraft("demo-asha", 100).copy(sourceIds = listOf("demo-ravi-fixture")))
    }
    @Test fun checklistOnlyDraftIsNotTreatedAsConversationContent() {
        val checklistOnly = WorkflowDraft("demo-asha", updatedAt = 100, checklist = DoctorWorkflowData.checklist().map { it.copy(status = WorkflowTopicStatus.DEFERRED) })
        assertFalse(DoctorWorkflowData.hasDraftContent(checklistOnly))
        assertTrue(DoctorWorkflowData.hasDraftContent(checklistOnly.copy(participants = "Patient entered manually")))
        assertTrue(DoctorWorkflowData.hasDraftContent(checklistOnly.copy(sourceText = "Entered source")))
    }
}
