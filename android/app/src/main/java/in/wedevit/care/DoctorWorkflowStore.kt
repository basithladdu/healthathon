package `in`.wedevit.care

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Local sample workspace only; never calls a provider or touches patient account storage. */
class DoctorWorkflowStore(context: Context, private val now: () -> Long = { System.currentTimeMillis() }, private val newId: () -> String = { UUID.randomUUID().toString() }, preferencesName: String = "doctor-workflow-v1") {
    init { require(preferencesName.startsWith("doctor-workflow-")) }
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private var failure: String? = null
    fun error(): String? = failure
    @Synchronized fun snapshot(): DoctorWorkflowState {
        val raw = preferences.getString("state", null) ?: return DoctorWorkflowData.seed(now())
        return try { decode(JSONObject(raw)).also { failure = null } } catch (_: Exception) { failure = "Saved preview data could not be read. It has not been overwritten."; DoctorWorkflowState(emptyList()) }
    }
    fun requests(patientId: String? = null) = snapshot().requests.filter { patientId == null || it.patientId == patientId }
    fun draft(patientId: String) = snapshot().drafts.firstOrNull { it.patientId == patientId } ?: WorkflowDraft(patientId)
    fun versions(patientId: String) = snapshot().versions.filter { it.patientId == patientId }.sortedByDescending { it.number }
    fun acknowledgements(versionId: String) = snapshot().acknowledgements.filter { it.versionId == versionId }
    fun activity(patientId: String) = snapshot().activity.filter { it.patientId == patientId }.sortedByDescending { it.at }
    fun attachments(patientId: String) = DoctorWorkflowData.attachments(patientId)
    fun timeline(patientId: String): List<WorkflowTimelineEntry> = DoctorWorkflowData.sources(patientId).take(1).map { WorkflowTimelineEntry(it.id, patientId, it.label, it.text, 0) } + versions(patientId).map { WorkflowTimelineEntry(it.id, patientId, "Care Note preview v${it.number}", it.draft.nextSteps, it.publishedAt) }
    @Synchronized fun transitionRequest(id: String, status: WorkflowRequestStatus, appointmentId: String? = null, appointmentLookup: ((String) -> DoctorDemoAppointment?)? = null): String? = change { state ->
        val request = state.requests.firstOrNull { it.id == id } ?: return@change "Request not found." to state
        if (!DoctorWorkflowData.transitionAllowed(request.status, status)) return@change "This request cannot move to that status." to state
        if (status == WorkflowRequestStatus.SCHEDULED && appointmentId.isNullOrBlank()) return@change "Book a visit before marking this request scheduled." to state
        if (status == WorkflowRequestStatus.SCHEDULED) {
            val appointment = appointmentId?.let { appointmentLookup?.invoke(it) }
            if (appointment == null || appointment.patientId != request.patientId || appointment.status != "Scheduled") return@change "Choose a saved scheduled visit for this patient." to state
        }
        null to logged(state.copy(requests = state.requests.map { if (it.id == id) it.copy(status = status, appointmentId = if (status == WorkflowRequestStatus.SCHEDULED) appointmentId else it.appointmentId) else it }), request.patientId, "Request ${status.name.lowercase()} locally; no notification sent")
    }
    @Synchronized fun saveDraft(value: WorkflowDraft): String? = change { state ->
        if (value.patientId !in DoctorWorkflowData.patientIds) return@change "Patient not found." to state
        var normalized = try { DoctorWorkflowData.normalizeDraftSource(value) } catch (_: IllegalArgumentException) { return@change "Source references must belong to this patient." to state }
        val previous = state.drafts.firstOrNull { it.patientId == value.patientId }
        if (previous != null && previous.sourceText != normalized.sourceText && previous.sourceText.isNotBlank()) normalized = normalized.copy(transcriptProvider = "", transcriptModel = "", transcribedAt = 0, audioClipId = "", aiProvider = "", aiModel = "", evidence = emptyList(), aiWarnings = emptyList())
        if (previous?.aiProvider?.isNotBlank() == true && listOf(previous.priorities, previous.participants, previous.topics, previous.questions, previous.undecided, previous.nextSteps) != listOf(normalized.priorities, normalized.participants, normalized.topics, normalized.questions, normalized.undecided, normalized.nextSteps)) normalized = normalized.copy(aiProvider = "", aiModel = "", evidence = emptyList(), aiWarnings = listOf("Manually edited after AI organisation. Review all wording and attribution."))
        if (previous != null && previous.copy(updatedAt = 0) == normalized.copy(updatedAt = 0)) return@change null to state
        null to logged(state.copy(drafts = state.drafts.filterNot { it.patientId == value.patientId } + normalized.copy(updatedAt = now(), checklist = normalized.checklist.toList(), sourceIds = normalized.sourceIds.toList())), value.patientId, "Draft saved for later")
    }
    @Synchronized fun loadMockDraft(patientId: String): String? {
        if (patientId !in DoctorWorkflowData.patientIds) return "Patient not found."
        val previous = draft(patientId)
        if (DoctorWorkflowData.hasDraftContent(previous)) return "Save your current work separately before loading a fictional fixture. Existing work was not replaced."
        return saveDraft(DoctorWorkflowData.mockDraft(patientId, now()).copy(checklist = previous.checklist.toList()))
    }
    @Synchronized fun importTranscript(patientId: String, text: String, provider: String, model: String, clipId: String, consentAt: Long, expectedSourceText: String): String? {
        val current = draft(patientId)
        if (current.sourceText != expectedSourceText || current.sourceText.isNotBlank()) return "The transcript changed. Your text was not replaced."
        if (text.isBlank() || text.length > 60000 || provider != "google-gemini" || model.isBlank() || clipId.isBlank() || consentAt <= 0) return "The transcript response was incomplete."
        return saveDraft(current.copy(sourceText = text, sourceIds = emptyList(), mockGenerated = false, transcriptProvider = provider, transcriptModel = model, transcribedAt = now(), audioClipId = clipId))
    }
    @Synchronized fun importOfflineTranscript(patientId: String, clipId: String, review: OfflineTranscriptReview): String? {
        val current = draft(patientId)
        if (current.sourceText.isNotBlank()) return "The conversation changed. Your text was not replaced."
        if (review.provider != OfflineSpeechModel.provider || review.model != OfflineSpeechModel.id || review.reviewedAt < review.createdAt || review.reviewedAt <= 0 || review.text.isBlank() || review.text.length > NoteRules.MAX_SOURCE_CHARS || clipId.isBlank()) return "Review the transcript against its recording first."
        return saveDraft(current.copy(sourceText = review.text, sourceIds = emptyList(), mockGenerated = false, transcriptProvider = review.provider, transcriptModel = review.model, transcribedAt = review.createdAt, audioClipId = clipId))
    }
    @Synchronized fun applyAiDraft(patientId: String, result: DoctorAiDraft, expectedSourceText: String): String? {
        val current = draft(patientId)
        if (current.sourceText != expectedSourceText || current.sourceText.isBlank()) return "The transcript changed. AI text was not inserted."
        if (listOf(current.priorities, current.participants, current.topics, current.questions, current.undecided, current.nextSteps).any { it.isNotBlank() }) return "Your note contains edited content. It was not replaced."
        if (result.evidence.size > 120 || result.evidence.any { it.quote.length > 4000 } || result.evidence.filter { it.section == "priorities" }.any { !Regex("(?:^|\\]\\s*)patient\\s*:", RegexOption.IGNORE_CASE).containsMatchIn(it.quote) }) return "AI evidence did not establish the patient speaker or exceeded safe limits."
        if (result.provider != "google-gemini" || result.model.isBlank() || result.evidence.any { it.section !in doctorAiSectionKeys || it.quote.isBlank() || current.sourceText.lineSequence().none { line -> line == it.quote } } || doctorAiSectionKeys.any { key -> (result.sections[key]?.length ?: 4001) > 4000 || result.sections[key] != result.evidence.filter { it.section == key }.map { it.quote }.distinct().joinToString("\n") }) return "AI evidence did not match the transcript."
        return saveDraft(current.copy(priorities = result.sections.getValue("priorities"), participants = result.sections.getValue("participants"), topics = result.sections.getValue("topics"), questions = result.sections.getValue("questions"), undecided = result.sections.getValue("undecided"), nextSteps = result.sections.getValue("nextSteps"), aiProvider = result.provider, aiModel = result.model, aiWarnings = result.warnings.toList(), evidence = result.evidence.toList()))
    }
    @Synchronized fun publish(patientId: String, reviewerName: String, checked: Boolean, sourceConfirmed: Boolean): String? = change { state ->
        val draft = state.drafts.firstOrNull { it.patientId == patientId } ?: return@change "Save a draft first." to state
        DoctorWorkflowData.publishError(draft, reviewerName, checked, sourceConfirmed)?.let { return@change it to state }
        val version = WorkflowVersion(newId(), patientId, (state.versions.filter { it.patientId == patientId }.maxOfOrNull { it.number } ?: 0) + 1, draft.copy(checklist = draft.checklist.toList(), sourceIds = draft.sourceIds.toList()), reviewerName.trim(), now())
        null to logged(state.copy(versions = state.versions + version), patientId, "Published unverified local preview v${version.number}; not externally delivered")
    }
    @Synchronized fun acknowledge(versionId: String, memberId: String, status: WorkflowAckStatus): String? = change { state ->
        val version = state.versions.firstOrNull { it.id == versionId } ?: return@change "Version not found." to state
        if (DoctorWorkflowData.members(version.patientId).none { it.id == memberId }) return@change "Choose this patient's fictional patient or family identity." to state
        val value = WorkflowAcknowledgement(versionId, memberId, status, now())
        null to logged(state.copy(acknowledgements = state.acknowledgements.filterNot { it.versionId == versionId && it.memberId == memberId } + value), version.patientId, "Simulated ${status.name.lowercase()} by selected fictional identity; not consent or agreement")
    }
    @Synchronized fun recordConsent(patientId: String, participant: String, purpose: String, recording: Boolean, cloudProcessing: Boolean): String? = change { state ->
        if (patientId !in DoctorWorkflowData.patientIds || participant.isBlank() || purpose.isBlank()) return@change "Enter participant and purpose for this patient." to state
        val consent = WorkflowConsent(newId(), patientId, participant.trim(), purpose.trim(), recording, cloudProcessing, now())
        null to logged(state.copy(consents = state.consents + consent), patientId, "Saved simulated consent selections; no audio recorded or uploaded")
    }
    private fun logged(state: DoctorWorkflowState, patientId: String, action: String): DoctorWorkflowState {
        // Autosave records one event per editing run, not one event per keystroke.
        if (action == "Draft saved for later" && state.activity.lastOrNull()?.let { it.patientId == patientId && it.action == action } == true) return state
        return state.copy(activity = state.activity + WorkflowActivity(newId(), patientId, action, now()))
    }
    private fun change(transform: (DoctorWorkflowState) -> Pair<String?, DoctorWorkflowState>): String? {
        val before = snapshot(); failure?.let { return it }
        val (error, next) = transform(before); if (error != null) return error
        if (next == before) return null
        return if (preferences.edit().putString("state", encode(next).toString()).commit()) null else "Couldn't save preview changes. Try again."
    }
    private fun encode(state: DoctorWorkflowState): JSONObject = JSONObject().put("schema", 1)
        .put("requests", rows(state.requests) { JSONObject().put("id", it.id).put("patientId", it.patientId).put("requestedBy", it.requestedBy).put("reason", it.reason).put("createdAt", it.createdAt).put("status", it.status.name).put("appointmentId", it.appointmentId ?: JSONObject.NULL) })
        .put("drafts", rows(state.drafts, ::draftJson))
        .put("versions", rows(state.versions) { JSONObject().put("id", it.id).put("patientId", it.patientId).put("number", it.number).put("draft", draftJson(it.draft)).put("reviewerName", it.reviewerName).put("publishedAt", it.publishedAt) })
        .put("acknowledgements", rows(state.acknowledgements) { JSONObject().put("versionId", it.versionId).put("memberId", it.memberId).put("status", it.status.name).put("at", it.at) })
        .put("consents", rows(state.consents) { JSONObject().put("id", it.id).put("patientId", it.patientId).put("participant", it.participant).put("purpose", it.purpose).put("recording", it.recordingAgreed).put("cloud", it.cloudProcessingAgreed).put("at", it.at) })
        .put("activity", rows(state.activity) { JSONObject().put("id", it.id).put("patientId", it.patientId).put("action", it.action).put("at", it.at) })
    private fun draftJson(d: WorkflowDraft) = JSONObject().put("patientId", d.patientId).put("priorities", d.priorities).put("participants", d.participants).put("topics", d.topics).put("questions", d.questions).put("undecided", d.undecided).put("nextSteps", d.nextSteps).put("sourceText", d.sourceText).put("sourceIds", JSONArray(d.sourceIds)).put("checklist", rows(d.checklist) { JSONObject().put("title", it.title).put("status", it.status.name) }).put("updatedAt", d.updatedAt).put("mockGenerated", d.mockGenerated).put("transcriptProvider", d.transcriptProvider).put("transcriptModel", d.transcriptModel).put("transcribedAt", d.transcribedAt).put("audioClipId", d.audioClipId).put("aiProvider", d.aiProvider).put("aiModel", d.aiModel).put("aiWarnings", JSONArray(d.aiWarnings)).put("evidence", rows(d.evidence) { JSONObject().put("section", it.section).put("quote", it.quote) })
    private fun readDraft(d: JSONObject) = WorkflowDraft(d.getString("patientId"), d.getString("priorities"), d.getString("participants"), d.getString("topics"), d.getString("questions"), d.getString("undecided"), d.getString("nextSteps"), d.getString("sourceText"), strings(d.getJSONArray("sourceIds")), items(d.getJSONArray("checklist")) { WorkflowTopic(it.getString("title"), WorkflowTopicStatus.valueOf(it.getString("status"))) }, d.getLong("updatedAt"), d.getBoolean("mockGenerated"), d.optString("transcriptProvider"), d.optString("transcriptModel"), d.optLong("transcribedAt"), d.optString("audioClipId"), d.optString("aiProvider"), d.optString("aiModel"), d.optJSONArray("aiWarnings")?.let(::strings) ?: emptyList(), d.optJSONArray("evidence")?.let { items(it) { row -> DoctorAiEvidence(row.getString("section"), row.getString("quote")) } } ?: emptyList())
    private fun decode(j: JSONObject): DoctorWorkflowState {
        require(j.getInt("schema") == 1)
        return DoctorWorkflowState(items(j.getJSONArray("requests")) { WorkflowRequest(it.getString("id"), it.getString("patientId"), it.getString("requestedBy"), it.getString("reason"), it.getLong("createdAt"), WorkflowRequestStatus.valueOf(it.getString("status")), if (it.isNull("appointmentId")) null else it.getString("appointmentId")) }, items(j.getJSONArray("drafts"), ::readDraft), items(j.getJSONArray("versions")) { WorkflowVersion(it.getString("id"), it.getString("patientId"), it.getInt("number"), readDraft(it.getJSONObject("draft")), it.getString("reviewerName"), it.getLong("publishedAt")) }, items(j.getJSONArray("acknowledgements")) { WorkflowAcknowledgement(it.getString("versionId"), it.getString("memberId"), WorkflowAckStatus.valueOf(it.getString("status")), it.getLong("at")) }, items(j.getJSONArray("consents")) { WorkflowConsent(it.getString("id"), it.getString("patientId"), it.getString("participant"), it.getString("purpose"), it.getBoolean("recording"), it.getBoolean("cloud"), it.getLong("at")) }, items(j.getJSONArray("activity")) { WorkflowActivity(it.getString("id"), it.getString("patientId"), it.getString("action"), it.getLong("at")) })
    }
    private fun <T> rows(values: List<T>, encode: (T) -> JSONObject) = JSONArray().also { array -> values.forEach { array.put(encode(it)) } }
    private fun <T> items(array: JSONArray, decode: (JSONObject) -> T) = (0 until array.length()).map { decode(array.getJSONObject(it)) }
    private fun strings(array: JSONArray) = (0 until array.length()).map { array.getString(it) }
}
