package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.time.Instant

/** Server-persisted synthetic workspace. These identities are never clinician verification. */
data class ConnectedDoctorPatient(val id: String, val legacyId: String, val name: String, val age: Int, val careContext: String, val summary: String, val payload: JSONObject)
data class ConnectedDoctorNote(val id: String, val patientId: String, val revision: Long, val status: String, val draft: WorkflowDraft, val createdAt: Long, val updatedAt: Long)
data class ConnectedDoctorDose(val id: String, val patientId: String, val medicineLabel: String, val plannedAt: Long, val reportedTakenAt: Long?, val loggedAt: Long, val reporterId: String, val outcome: String)
data class ConnectedDoctorFile(val id: String, val patientId: String, val title: String, val kind: String, val mime: String, val bytes: Long, val sha256: String, val objectPath: String, val createdAt: Long, val ready: Boolean = false)
data class ConnectedDoctorDataset(val id: String, val patients: List<ConnectedDoctorPatient>, val notes: List<ConnectedDoctorNote>, val doses: List<ConnectedDoctorDose>, val files: List<ConnectedDoctorFile>)

internal fun requireConnectedDoctorId(id: String): String {
    require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "Invalid connected record identity." }
    return id
}
internal fun validateConnectedDoctorDraft(draft: WorkflowDraft): JSONObject {
    requireConnectedDoctorId(draft.patientId)
    require(draft.sourceText.length <= 60000 && listOf(draft.priorities, draft.participants, draft.topics, draft.questions, draft.undecided, draft.nextSteps).all { it.length <= 4000 }) { "The source or a section is too long. Nothing was truncated or uploaded." }
    val value = encodeConnectedDoctorDraft(draft)
    require(value.toString().toByteArray(Charsets.UTF_8).size <= 262144) { "The full draft is too large. Nothing was truncated or uploaded." }
    return value
}
internal fun validateConnectedDoctorDose(outcome: String, plannedAt: Long, reportedTakenAt: Long?, now: Long) {
    require(plannedAt > 0 && now > 0 && outcome in setOf("taken", "skipped")) { "Choose a planned time and Taken or Skipped." }
    require(if (outcome == "taken") reportedTakenAt != null && reportedTakenAt > 0 && reportedTakenAt <= now else reportedTakenAt == null) { "Taken needs its actual past time; Skipped must not have a taken time." }
}
internal fun connectedDoctorInstant(value: Long): String = Instant.ofEpochMilli(value).toString()

/** Only an exact saved response for this caller and captured submission is a retry receipt. */
internal fun connectedDoctorDraftRetryMatches(note: ConnectedDoctorNote, authorId: String, callerId: String,
    patientId: String, expectedRevision: Long, submitted: WorkflowDraft): Boolean =
    expectedRevision >= 0 && expectedRevision < Long.MAX_VALUE && authorId == callerId &&
        note.patientId == patientId && submitted.patientId == patientId && note.status == "draft" &&
        note.revision == expectedRevision + 1 && note.draft == submitted

fun encodeConnectedDoctorDraft(draft: WorkflowDraft): JSONObject = JSONObject()
    .put("schemaVersion", 1).put("synthetic", true).put("priorities", draft.priorities)
    .put("participants", draft.participants).put("topics", draft.topics).put("questions", draft.questions)
    .put("undecided", draft.undecided).put("nextSteps", draft.nextSteps).put("sourceText", draft.sourceText)
    .put("sourceIds", JSONArray(draft.sourceIds)).put("updatedAt", draft.updatedAt).put("mockGenerated", draft.mockGenerated)
    .put("checklist", JSONArray().also { rows -> draft.checklist.forEach { rows.put(JSONObject().put("title", it.title).put("status", it.status.name)) } })
    .put("transcriptProvider", draft.transcriptProvider).put("transcriptModel", draft.transcriptModel).put("transcribedAt", draft.transcribedAt)
    .put("audioClipId", draft.audioClipId).put("aiProvider", draft.aiProvider).put("aiModel", draft.aiModel)
    .put("aiWarnings", JSONArray(draft.aiWarnings)).put("evidence", JSONArray().also { rows -> draft.evidence.forEach { rows.put(JSONObject().put("section", it.section).put("quote", it.quote)) } })

fun decodeConnectedDoctorDraft(patientId: String, data: JSONObject): WorkflowDraft {
    require(data.getInt("schemaVersion") == 1 && data.getBoolean("synthetic")) { "Unsupported test-draft format. Original data was not changed." }
    val keys = setOf("schemaVersion", "synthetic", "priorities", "participants", "topics", "questions", "undecided", "nextSteps", "sourceText", "sourceIds", "updatedAt", "mockGenerated", "checklist", "transcriptProvider", "transcriptModel", "transcribedAt", "audioClipId", "aiProvider", "aiModel", "aiWarnings", "evidence")
    require(data.keys().asSequence().all { it in keys }) { "This draft has newer fields. Update the app; original data was not changed." }
    fun strings(key: String) = data.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
    return WorkflowDraft(patientId = patientId, priorities = data.getString("priorities"), participants = data.getString("participants"),
        topics = data.getString("topics"), questions = data.getString("questions"), undecided = data.getString("undecided"), nextSteps = data.getString("nextSteps"),
        sourceText = data.getString("sourceText"), sourceIds = strings("sourceIds"), checklist = data.getJSONArray("checklist").let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { row ->
                require(row.keys().asSequence().all { it in setOf("title", "status") }) { "This checklist has newer fields. Original data was not changed." }
                WorkflowTopic(row.getString("title"), WorkflowTopicStatus.valueOf(row.getString("status")))
            } }
        }, updatedAt = data.getLong("updatedAt"), mockGenerated = data.getBoolean("mockGenerated"), transcriptProvider = data.getString("transcriptProvider"),
        transcriptModel = data.getString("transcriptModel"), transcribedAt = data.getLong("transcribedAt"), audioClipId = data.getString("audioClipId"),
        aiProvider = data.getString("aiProvider"), aiModel = data.getString("aiModel"), aiWarnings = strings("aiWarnings"),
        evidence = data.getJSONArray("evidence").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { row ->
            require(row.keys().asSequence().all { it in setOf("section", "quote") }) { "This evidence has newer fields. Original data was not changed." }
            DoctorAiEvidence(row.getString("section"), row.getString("quote"))
        } } })
}
