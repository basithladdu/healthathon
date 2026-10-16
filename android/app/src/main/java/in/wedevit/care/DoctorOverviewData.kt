package `in`.wedevit.care

/** Read model for the isolated doctor preview. None of these fields establishes clinical status. */
data class DoctorPatientOverview(
    val patient: DoctorDemoPatient,
    val background: String,
    val latestReport: SamplePatientCheckIn?,
    val lastDiscussion: DoctorOverviewDiscussion?,
    val previousProfessional: DoctorPreviousProfessional?,
    val history: List<DoctorOverviewDiscussion>,
    val nextVisit: DoctorDemoAppointment?,
    val clinicalAssessment: String = "Not recorded",
)

data class DoctorOverviewDiscussion(
    val id: String,
    val patientId: String,
    val versionNumber: Int?,
    val publishedAt: Long?,
    val reviewerName: String?,
    val sourceId: String,
    val sourceLabel: String,
    val sourceText: String,
    val priorities: String,
    val participants: String,
    val topics: String,
    val questions: String,
    val undecided: String,
    val nextSteps: String,
    val isPublishedPreview: Boolean,
    val isSample: Boolean = true,
)

/** Illustrative prior professional, explicitly separate from the source's unknown actual author. */
data class DoctorPreviousProfessional(val name: String, val role: String, val sourceId: String, val disclosure: String)

object DoctorOverviewData {
    private val illustrativeProfessionals = mapOf(
        "demo-asha" to "Dr Ananya Rao",
        "demo-ravi" to "Dr Arjun Menon",
        "demo-leela" to "Dr Nisha Thomas",
        "demo-imran" to "Dr Kabir Shah",
        "demo-maya" to "Dr Priya Nair",
        "demo-joseph" to "Dr Rohan Das",
    )

    fun build(patientId: String, versions: List<WorkflowVersion>, appointments: List<DoctorDemoAppointment>, nowMillis: Long): DoctorPatientOverview? {
        val patient = DoctorDemoData.patients.firstOrNull { it.id == patientId } ?: return null
        val published = versions.filter { usableVersion(it, patientId, nowMillis) }
            .sortedWith(compareByDescending<WorkflowVersion> { it.number }.thenByDescending { it.publishedAt }.thenBy { it.id })
            .map(::discussion)
        val previousSource = DoctorWorkflowData.sources(patientId).firstOrNull { it.id == "$patientId-prior" && it.text.isNotBlank() }
        val previous = previousSource?.let { source ->
            DoctorOverviewDiscussion(
                id = source.id, patientId = patientId, versionNumber = null, publishedAt = null,
                reviewerName = null, sourceId = source.id, sourceLabel = source.label,
                sourceText = source.text,
                // The original sample has no structured headings. Do not manufacture them from background text.
                priorities = "", participants = "", topics = source.text, questions = "", undecided = "", nextSteps = "",
                isPublishedPreview = false,
            )
        }
        val professional = previousSource?.let { source -> illustrativeProfessionals[patientId]?.let { name ->
            DoctorPreviousProfessional(name, "Previous clinician (fictional example)", source.id,
                "Illustrative handover metadata linked to this patient's previous sample discussion. Not a verified note author or an assigned doctor. The original sample does not record its author or date.")
        } }
        val report = DoctorPatientTracking.checkIns(patientId).filter { it.patientId == patientId && it.at > 0 && it.at <= nowMillis && it.text.isNotBlank() }.maxByOrNull { it.at }
        val next = appointments.filter { it.patientId == patientId && it.status == "Scheduled" && it.startMillis > nowMillis && it.startMillis > 0 && it.id.isNotBlank() && it.durationMinutes in 1..480 }
            .minWithOrNull(compareBy<DoctorDemoAppointment> { it.startMillis }.thenBy { it.id })
        return DoctorPatientOverview(patient, patient.summary, report, published.firstOrNull() ?: previous, professional, published + listOfNotNull(previous), next)
    }

    fun usableVersion(version: WorkflowVersion, patientId: String, nowMillis: Long): Boolean {
        val draft = version.draft
        return version.patientId == patientId && draft.patientId == patientId && version.id.isNotBlank() && version.number > 0 &&
            version.publishedAt > 0 && version.publishedAt <= nowMillis && version.reviewerName.isNotBlank() && version.reviewerName.length <= 200 &&
            draft.sourceText.isNotBlank() && draft.sourceText.length <= 60000 &&
            listOf(draft.priorities, draft.participants, draft.topics, draft.questions, draft.undecided, draft.nextSteps).all { it.length <= 4000 } &&
            listOf(draft.priorities, draft.topics, draft.questions, draft.undecided, draft.nextSteps).any { it.isNotBlank() } &&
            draft.sourceIds.all { id -> DoctorWorkflowData.sources(patientId).any { it.id == id } }
    }

    private fun discussion(version: WorkflowVersion): DoctorOverviewDiscussion = version.draft.let { draft ->
        DoctorOverviewDiscussion(version.id, version.patientId, version.number, version.publishedAt, version.reviewerName,
            version.id, "Published local preview v${version.number} · unverified self-entered review", draft.sourceText,
            draft.priorities, draft.participants, draft.topics, draft.questions, draft.undecided, draft.nextSteps,
            isPublishedPreview = true)
    }
}
