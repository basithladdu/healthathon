package `in`.wedevit.care

/** Read-only conversation catalogue. Drafts are never published-history entries. */
data class DoctorConversationsModel(
    val patientId: String,
    val patientName: String,
    val latestPublished: DoctorOverviewDiscussion?,
    val earlierPublished: List<DoctorOverviewDiscussion>,
    val priorSample: DoctorOverviewDiscussion?,
    val currentDraft: WorkflowDraft?,
    val currentDraftUpdatedAt: Long? = null,
) {
    val draftHasContent: Boolean get() = currentDraft != null
}

object DoctorConversationsData {
    fun build(patientId: String, versions: List<WorkflowVersion>, draft: WorkflowDraft?, nowMillis: Long): DoctorConversationsModel? {
        val overview = DoctorOverviewData.build(patientId, versions, emptyList(), nowMillis) ?: return null
        // Reuse the single validated selector and version ordering used by Overview.
        val published = overview.history.filter { it.isPublishedPreview }
        val sample = overview.history.firstOrNull { !it.isPublishedPreview && it.sourceId == "$patientId-prior" }
        val current = draft?.takeIf { value ->
            value.patientId == patientId && DoctorWorkflowData.hasDraftContent(value) && value.sourceText.length <= 60000 &&
                listOf(value.priorities, value.participants, value.topics, value.questions, value.undecided, value.nextSteps).all { it.length <= 4000 } &&
                value.sourceIds.all { id -> DoctorWorkflowData.sources(patientId).any { it.id == id } }
        }
        return DoctorConversationsModel(patientId, overview.patient.name.removeSuffix(" Demo"), published.firstOrNull(), published.drop(1), sample, current, current?.updatedAt?.takeIf { it > 0 && it <= nowMillis })
    }
}
