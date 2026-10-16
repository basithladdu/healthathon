package `in`.wedevit.care

enum class WorkflowRequestStatus { NEW, ACCEPTED, SCHEDULED, COMPLETED, DEFERRED }
enum class WorkflowTopicStatus { NOT_DISCUSSED, DISCUSSED, DEFERRED }
enum class WorkflowAckStatus { READ, CONCERN, NOT_READY }
data class WorkflowRequest(val id: String, val patientId: String, val requestedBy: String, val reason: String, val createdAt: Long, val status: WorkflowRequestStatus = WorkflowRequestStatus.NEW, val appointmentId: String? = null)
data class WorkflowTopic(val title: String, val status: WorkflowTopicStatus = WorkflowTopicStatus.NOT_DISCUSSED)
data class WorkflowSource(val id: String, val label: String, val text: String, val timestampSeconds: Int? = null)
data class WorkflowDraft(val patientId: String, val priorities: String = "", val participants: String = "", val topics: String = "", val questions: String = "", val undecided: String = "", val nextSteps: String = "", val sourceText: String = "", val sourceIds: List<String> = emptyList(), val checklist: List<WorkflowTopic> = DoctorWorkflowData.checklist(), val updatedAt: Long = 0, val mockGenerated: Boolean = false, val transcriptProvider: String = "", val transcriptModel: String = "", val transcribedAt: Long = 0, val audioClipId: String = "", val aiProvider: String = "", val aiModel: String = "", val aiWarnings: List<String> = emptyList(), val evidence: List<DoctorAiEvidence> = emptyList())
data class WorkflowVersion(val id: String, val patientId: String, val number: Int, val draft: WorkflowDraft, val reviewerName: String, val publishedAt: Long, val reviewLabel: String = "Unverified sample · local preview · not a clinical record")
data class WorkflowMember(val id: String, val patientId: String, val name: String, val role: String)
data class WorkflowAcknowledgement(val versionId: String, val memberId: String, val status: WorkflowAckStatus, val at: Long)
data class WorkflowConsent(val id: String, val patientId: String, val participant: String, val purpose: String, val recordingAgreed: Boolean, val cloudProcessingAgreed: Boolean, val at: Long) {
    val recordingConsentAt: Long? get() = at.takeIf { recordingAgreed }
    val cloudProcessingConsentAt: Long? get() = at.takeIf { cloudProcessingAgreed }
}
data class WorkflowAttachment(val id: String, val patientId: String, val title: String, val text: String, val at: Long)
data class WorkflowTimelineEntry(val id: String, val patientId: String, val title: String, val detail: String, val at: Long)
data class WorkflowActivity(val id: String, val patientId: String, val action: String, val at: Long)
data class DoctorWorkflowState(val requests: List<WorkflowRequest>, val drafts: List<WorkflowDraft> = emptyList(), val versions: List<WorkflowVersion> = emptyList(), val acknowledgements: List<WorkflowAcknowledgement> = emptyList(), val consents: List<WorkflowConsent> = emptyList(), val activity: List<WorkflowActivity> = emptyList())

/** Fixtures only. Identity verification is deliberately not mutable in this preview. */
object DoctorWorkflowData {
    const val verificationStatus = "UNVERIFIED"
    val patientIds get() = DoctorDemoData.patients.map { it.id }
    fun checklist() = listOf("Patient priorities", "Understanding and questions", "Who should participate", "Concerns and uncertainty", "Next steps and follow-up").map { WorkflowTopic(it) }
    fun seed(now: Long) = DoctorWorkflowState(DoctorDemoData.patients.take(4).mapIndexed { i, p -> WorkflowRequest("request-${p.id}", p.id, if (i % 2 == 0) "Patient (sample)" else "Family (sample)", "Prepare a discussion about ${p.careContext.lowercase()}.", now - (i + 1) * 3_600_000L) })
    fun sources(patientId: String): List<WorkflowSource> = DoctorDemoData.patients.firstOrNull { it.id == patientId }?.let { p -> listOf(WorkflowSource("$patientId-prior", "Previous sample discussion", p.careNotes.joinToString("\n")), WorkflowSource("$patientId-fixture", "Fictional conversation · 00:12", "Patient: I would like time to ask my questions. Family: We can help prepare the questions. Clinician: We will arrange another discussion; no treatment decision is recorded.", 12)) } ?: emptyList()
    fun members(patientId: String) = if (patientId !in patientIds) emptyList() else listOf(WorkflowMember("$patientId-patient", patientId, "Patient (fictional)", "Patient"), WorkflowMember("$patientId-family", patientId, "Family member (fictional)", "Family"))
    fun attachments(patientId: String) = DoctorDemoData.patients.firstOrNull { it.id == patientId }?.reportSummaries?.mapIndexed { i, text -> WorkflowAttachment("$patientId-report-$i", patientId, "Sample report ${i + 1}", text, 0) } ?: emptyList()
    fun mockDraft(patientId: String, now: Long): WorkflowDraft = WorkflowDraft(patientId, participants = "Patient, family and clinician (fictional fixture)", questions = "Patient would like time to ask questions.", undecided = "No treatment decision is recorded.", nextSteps = "Arrange another discussion.", sourceText = sources(patientId).last().text, sourceIds = listOf("$patientId-fixture"), updatedAt = now, mockGenerated = true)
    fun transitionAllowed(from: WorkflowRequestStatus, to: WorkflowRequestStatus): Boolean = to in when (from) {
        WorkflowRequestStatus.NEW -> setOf(WorkflowRequestStatus.ACCEPTED, WorkflowRequestStatus.DEFERRED)
        WorkflowRequestStatus.ACCEPTED -> setOf(WorkflowRequestStatus.SCHEDULED, WorkflowRequestStatus.DEFERRED)
        WorkflowRequestStatus.SCHEDULED -> setOf(WorkflowRequestStatus.COMPLETED, WorkflowRequestStatus.DEFERRED)
        WorkflowRequestStatus.DEFERRED -> setOf(WorkflowRequestStatus.ACCEPTED)
        WorkflowRequestStatus.COMPLETED -> emptySet()
    }
    fun publishError(draft: WorkflowDraft, reviewer: String, checked: Boolean, sourceConfirmed: Boolean): String? = when {
        draft.patientId !in patientIds -> "Patient not found."
        reviewer.isBlank() -> "Enter the preview reviewer's name."
        !checked -> "Review the complete draft before publishing a preview."
        !sourceConfirmed || draft.sourceText.isBlank() -> "Provide and confirm the source text."
        listOf(draft.priorities, draft.topics, draft.questions, draft.undecided, draft.nextSteps).all { it.isBlank() } -> "Add discussion content first."
        else -> null
    }
    fun normalizeDraftSource(draft: WorkflowDraft): WorkflowDraft {
        val sources = sources(draft.patientId)
        require(draft.sourceIds.all { id -> sources.any { it.id == id } }) { "Source references must belong to this patient." }
        val matchesSource = draft.sourceIds.isNotEmpty() && draft.sourceIds.any { id -> sources.any { it.id == id && it.text == draft.sourceText } }
        return if (matchesSource) draft else draft.copy(sourceIds = emptyList(), mockGenerated = false)
    }
    fun hasDraftContent(draft: WorkflowDraft) = listOf(draft.sourceText, draft.priorities, draft.participants, draft.topics, draft.questions, draft.undecided, draft.nextSteps).any { it.isNotBlank() }
}
