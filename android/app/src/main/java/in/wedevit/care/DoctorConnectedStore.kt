package `in`.wedevit.care

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

private fun JSONObject.epoch(key: String): Long = Instant.parse(getString(key)).toEpochMilli()
private fun JSONArray.rows(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private suspend fun CareCloud.doctorRows(table: String, filter: String): List<JSONObject> {
    require(table in setOf("datasets", "patients", "notes", "doses", "files"))
    val all = mutableListOf<JSONObject>(); var offset = 0
    do {
        val rows = JSONArray(request("GET", "/rest/v1/sahara_doctor_$table?$filter&limit=500&offset=$offset")).rows()
        all += rows; offset += rows.size
        require(offset <= 10000) { "Too many records to load." }
    } while (rows.size == 500)
    return all
}
private fun decodeDoctorPatient(row: JSONObject): ConnectedDoctorPatient {
    require(row.getBoolean("synthetic")) { "This is not a synthetic workspace record." }
    return ConnectedDoctorPatient(requireConnectedDoctorId(row.getString("id")), row.getString("legacy_id"), row.getString("name"), row.getInt("age"),
        row.getString("care_context"), row.getString("summary"), row.getJSONObject("payload"))
}
private fun decodeDoctorNote(row: JSONObject): ConnectedDoctorNote {
    val patient = requireConnectedDoctorId(row.getString("patient_id")); val status = row.getString("status")
    require(status in setOf("draft", "test_published") && row.getLong("revision") > 0)
    if (status == "test_published") require(row.getString("content_hash").matches(Regex("[a-f0-9]{64}")))
    return ConnectedDoctorNote(requireConnectedDoctorId(row.getString("id")), patient, row.getLong("revision"), status,
        decodeConnectedDoctorDraft(patient, row.getJSONObject("payload")), row.epoch("created_at"), row.epoch("updated_at"))
}
private fun decodeDoctorDose(row: JSONObject): ConnectedDoctorDose {
    val planned = row.epoch("planned_at"); val logged = row.epoch("logged_at")
    val taken = if (row.isNull("reported_taken_at")) null else row.epoch("reported_taken_at")
    validateConnectedDoctorDose(row.getString("outcome"), planned, taken, logged)
    return ConnectedDoctorDose(requireConnectedDoctorId(row.getString("id")), requireConnectedDoctorId(row.getString("patient_id")), row.getString("medicine_label"),
        planned, taken, logged, requireConnectedDoctorId(row.getString("reporter_id")), row.getString("outcome"))
}
private fun decodeDoctorFile(row: JSONObject) = ConnectedDoctorFile(requireConnectedDoctorId(row.getString("id")), requireConnectedDoctorId(row.getString("patient_id")),
    row.getString("title"), row.getString("kind"), row.getString("mime"), row.getLong("bytes"), row.getString("sha256"), row.getString("object_path"), row.epoch("created_at"), row.getString("upload_state") == "ready")

/** No automatic fixture seeding, import, upload or fallback to the local store. */
suspend fun CareCloud.loadDoctorDataset(): ConnectedDoctorDataset? {
    val user = requireConnectedDoctorId(session.value?.userId ?: error("Sign in first."))
    val dataset = doctorRows("datasets", "owner_id=eq.$user&order=created_at.asc").firstOrNull() ?: return null
    val id = requireConnectedDoctorId(dataset.getString("id"))
    require(dataset.getBoolean("synthetic"))
    return ConnectedDoctorDataset(id,
        doctorRows("patients", "dataset_id=eq.$id&order=created_at.asc,id.asc").map(::decodeDoctorPatient),
        doctorRows("notes", "dataset_id=eq.$id&order=updated_at.desc,id.asc").map(::decodeDoctorNote),
        doctorRows("doses", "dataset_id=eq.$id&order=logged_at.desc,id.asc").map(::decodeDoctorDose),
        doctorRows("files", "dataset_id=eq.$id&order=created_at.desc,id.asc").map(::decodeDoctorFile))
}

/** Call only after the user explicitly chooses Create my synthetic test workspace. */
suspend fun CareCloud.seedDoctorDataset(): ConnectedDoctorDataset {
    require(session.value != null) { "Sign in first." }
    val patients = JSONArray()
    DoctorDemoData.patients.forEach { patient ->
        val now = System.currentTimeMillis()
        val overview = DoctorOverviewData.build(patient.id, emptyList(), emptyList(), now)
        val professional = overview?.previousProfessional
        val discussion = overview?.lastDiscussion
        val payload = JSONObject().put("synthetic", true).put("schemaVersion", 1).put("careNotes", JSONArray(patient.careNotes))
            .put("reportSummaries", JSONArray(patient.reportSummaries)).put("observations", JSONArray(patient.observations)).put("medicines", JSONArray(patient.medicines))
            .put("previousDoctor", professional?.name.orEmpty())
            .put("previousProfessional", professional?.let { JSONObject().put("name", it.name).put("role", it.role).put("sourceId", it.sourceId).put("disclosure", it.disclosure) } ?: JSONObject.NULL)
            .put("reportMetadata", JSONArray().also { rows -> DoctorReportsData.reports(patient.id).forEach { report -> rows.put(JSONObject().put("id", report.id).put("title", report.title)
                .put("category", report.category.name).put("summary", report.summary).put("sourceLabel", report.sourceLabel).put("hasAttachedFile", false)
                .put("author", report.author ?: JSONObject.NULL).put("createdAt", report.createdAt ?: JSONObject.NULL)) } })
            .put("clinicalAssessment", overview?.clinicalAssessment ?: "Not recorded")
            .put("latestReportedCondition", overview?.latestReport?.text ?: "Not recorded")
            .put("lastDiscussion", discussion?.let { JSONObject().put("id", it.id).put("sourceId", it.sourceId).put("sourceLabel", it.sourceLabel)
                .put("sourceText", it.sourceText).put("priorities", it.priorities).put("participants", it.participants).put("topics", it.topics)
                .put("questions", it.questions).put("undecided", it.undecided).put("nextSteps", it.nextSteps).put("isSample", true) } ?: JSONObject.NULL)
            .put("checkIns", JSONArray().also { rows -> DoctorPatientTracking.checkIns(patient.id).forEach { rows.put(JSONObject().put("id", it.id).put("at", it.at).put("text", it.text).put("source", it.source.name)) } })
            .put("progressReports", JSONArray().also { rows -> DoctorProgressData.reports(patient.id, now).forEach { report ->
                rows.put(JSONObject().put("id", report.id).put("at", report.at).put("source", report.source.name).put("text", report.text)
                    .put("date", report.date.toString()).put("zoneId", report.zoneId).put("isLatest", report.isLatest).put("questions", JSONArray(report.questions))
                    .put("familySupport", JSONArray(report.familySupport)).put("symptoms", report.symptoms ?: JSONObject.NULL).put("clinicalAssessment", report.clinicalAssessment ?: JSONObject.NULL)
                    .put("linkedDiscussion", report.linkedDiscussion?.let { JSONObject().put("id", it.id).put("label", it.label).put("text", it.text) } ?: JSONObject.NULL)
                    .put("medicineActivity", JSONArray().also { doses -> report.medicineActivity.forEach { dose -> doses.put(JSONObject().put("id", dose.id).put("medicineLabel", dose.medicineLabel)
                        .put("plannedAt", dose.scheduledAt).put("reportedTakenAt", dose.reportedTakenAt ?: JSONObject.NULL).put("loggedAt", dose.loggedAt ?: JSONObject.NULL)
                        .put("source", dose.source?.name ?: JSONObject.NULL).put("status", dose.status)) } }))
            } })
        val doses = JSONArray()
        DoctorPatientTracking.doses(patient.id).filter { it.outcome != null }.forEach { dose -> doses.put(JSONObject().put("id", dose.id).put("medicineLabel", dose.medicineLabel)
            .put("plannedAt", dose.scheduledAt).put("reportedTakenAt", dose.reportedTakenAt ?: JSONObject.NULL)
            .put("outcome", if (dose.outcome == SampleDoseOutcome.SKIPPED) "skipped" else "taken")) }
        patients.put(JSONObject().put("legacyId", patient.id).put("name", patient.name.removeSuffix(" Demo")).put("age", patient.age).put("careContext", patient.careContext)
            .put("summary", patient.summary).put("payload", payload).put("draft", encodeConnectedDoctorDraft(DoctorWorkflowData.mockDraft(patient.id, System.currentTimeMillis())))
            .put("doses", doses))
    }
    request("POST", "/rest/v1/rpc/sahara_doctor_seed", JSONObject().put("p_patients", patients))
    return loadDoctorDataset() ?: error("Couldn't load. Refresh and try again.")
}

suspend fun CareCloud.saveDoctorDraft(patientId: String, draft: WorkflowDraft, expectedRevision: Long, noteId: String? = null): ConnectedDoctorNote {
    requireConnectedDoctorId(patientId); require(draft.patientId == patientId && expectedRevision >= 0)
    val payload = validateConnectedDoctorDraft(draft)
    val id = noteId?.let(::requireConnectedDoctorId) ?: if (expectedRevision == 0L) UUID.randomUUID().toString() else {
        val author = requireConnectedDoctorId(session.value?.userId ?: error("Sign in first."))
        doctorRows("notes", "patient_id=eq.$patientId&author_id=eq.$author&status=eq.draft&revision=eq.$expectedRevision&order=updated_at.desc,id.asc").singleOrNull()?.getString("id")
            ?: error("Draft not found. Refresh and try again.")
    }
    val caller = requireConnectedDoctorId(session.value?.userId ?: error("Sign in first."))
    doctorRows("notes", "id=eq.$id").singleOrNull()?.let { row ->
        val existing = decodeDoctorNote(row)
        require(existing.patientId == patientId && row.getString("author_id") == caller) { "This draft belongs to someone else." }
        require(existing.status == "draft") { "Already published. Save as copy." }
        if (connectedDoctorDraftRetryMatches(existing, row.getString("author_id"), caller, patientId, expectedRevision, draft)) return existing
        require(expectedRevision != 0L) { "Draft conflict. Refresh and try again." }
    }
    val saved = decodeDoctorNote(JSONObject(request("POST", "/rest/v1/rpc/sahara_doctor_save_note", JSONObject().put("p_patient", patientId).put("p_id", id)
        .put("p_revision", expectedRevision).put("p_payload", payload).put("p_publish", false))))
    require(saved.id == id && saved.patientId == patientId && saved.revision == expectedRevision + 1 && saved.status == "draft")
    return saved
}
suspend fun CareCloud.publishDoctorNote(noteId: String, expectedRevision: Long): ConnectedDoctorNote {
    requireConnectedDoctorId(noteId); require(expectedRevision > 0)
    val current = doctorRows("notes", "id=eq.$noteId").singleOrNull()?.let(::decodeDoctorNote) ?: error("This note is not available to your account.")
    require(current.status == "draft" && current.revision == expectedRevision) { "Draft changed. Refresh first." }
    val saved = decodeDoctorNote(JSONObject(request("POST", "/rest/v1/rpc/sahara_doctor_save_note", JSONObject().put("p_patient", current.patientId).put("p_id", noteId)
        .put("p_revision", expectedRevision).put("p_payload", validateConnectedDoctorDraft(current.draft)).put("p_publish", true))))
    require(saved.id == noteId && saved.patientId == current.patientId && saved.revision == expectedRevision + 1 && saved.status == "test_published")
    return saved
}
private suspend fun CareCloud.doctorPatientDataset(patientId: String): String {
    requireConnectedDoctorId(patientId)
    val patient = doctorRows("patients", "id=eq.$patientId").singleOrNull() ?: error("This synthetic patient is not available to your account.")
    require(patient.getBoolean("synthetic"))
    return requireConnectedDoctorId(patient.getString("dataset_id"))
}
suspend fun CareCloud.addDoctorDose(patientId: String, medicineLabel: String, plannedAt: Long, reportedTakenAt: Long?, outcome: String, eventId: String = UUID.randomUUID().toString()): ConnectedDoctorDose {
    validateConnectedDoctorDose(outcome, plannedAt, reportedTakenAt, System.currentTimeMillis())
    requireConnectedDoctorId(eventId); require(medicineLabel.trim().length in 1..200)
    val dataset = doctorPatientDataset(patientId)
    doctorRows("doses", "id=eq.$eventId").singleOrNull()?.let { existing ->
        val dose = decodeDoctorDose(existing)
        require(dose.patientId == patientId && dose.medicineLabel == medicineLabel.trim() && dose.plannedAt == plannedAt && dose.reportedTakenAt == reportedTakenAt && dose.outcome == outcome && dose.reporterId == session.value?.userId) { "Dose already saved. Refresh first." }
        return dose
    }
    val row = JSONObject().put("id", eventId).put("dataset_id", dataset).put("patient_id", patientId).put("medicine_label", medicineLabel.trim())
        .put("planned_at", connectedDoctorInstant(plannedAt)).put("reported_taken_at", reportedTakenAt?.let(::connectedDoctorInstant) ?: JSONObject.NULL).put("outcome", outcome)
    return JSONArray(request("POST", "/rest/v1/sahara_doctor_doses", row)).getJSONObject(0).let(::decodeDoctorDose)
}

suspend fun CareCloud.uploadDoctorFile(context: Context, uri: Uri, patientId: String, title: String, kind: String, consent: Boolean): ConnectedDoctorFile {
    require(consent) { "Tick the permission box first." }; require(title.trim().length in 1..160 && kind in setOf("audio", "report"))
    val dataset = doctorPatientDataset(patientId)
    val (mime, bytes) = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri)?.lowercase() ?: if (uri.path?.endsWith(".m4a", true) == true) "audio/mp4" else "application/octet-stream"
        val allowed = if (kind == "audio") setOf("audio/mp4", "audio/mpeg", "audio/wav", "audio/x-wav", "audio/ogg") else setOf("application/pdf", "image/jpeg", "image/png", "image/webp")
        require(mime in allowed) { "Choose audio, PDF or an image." }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val size = input.read(buffer); if (size < 0) break; require(output.size() + size <= 4_194_304) { "File must be under 4 MB." }; output.write(buffer, 0, size) }
            output.toByteArray()
        } ?: error("This file could not be opened.")
        require(bytes.isNotEmpty()); mime to bytes
    }
    val id = UUID.randomUUID().toString(); val path = "$dataset/$patientId/$id"
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val row = JSONObject().put("id", id).put("dataset_id", dataset).put("patient_id", patientId).put("title", title.trim()).put("kind", kind)
        .put("mime", mime).put("bytes", bytes.size).put("sha256", hash).put("object_path", path).put("permission_attested", true)
    JSONArray(request("POST", "/rest/v1/sahara_doctor_files", row)).getJSONObject(0).let(::decodeDoctorFile)
    doctorFileRequest("POST", path, bytes, mime)
    return JSONObject(request("POST", "/rest/v1/rpc/sahara_doctor_complete_file", JSONObject().put("p_id", id))).let(::decodeDoctorFile)
}
suspend fun CareCloud.downloadDoctorFile(context: Context, file: ConnectedDoctorFile): File {
    requireConnectedDoctorId(file.id); requireConnectedDoctorId(file.patientId)
    val fresh = doctorRows("files", "id=eq.${file.id}").singleOrNull()?.let(::decodeDoctorFile) ?: error("File not available.")
    require(fresh == file) { "File changed. Refresh first." }
    require(fresh.ready) { "Upload incomplete." }
    val bytes = doctorFileRequest("GET", fresh.objectPath)
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(bytes.size.toLong() == fresh.bytes && hash == fresh.sha256) { "File check failed. Not opened." }
    return withContext(Dispatchers.IO) {
        val extension = when (file.mime) { "application/pdf" -> "pdf"; "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "audio/mp4" -> "m4a"; "audio/mpeg" -> "mp3"; "audio/ogg" -> "ogg"; else -> "wav" }
        val folder = File(context.cacheDir, "doctor-preview-exports").also { check(it.exists() || it.mkdir()) }
        File(folder, "protected-${file.id}.$extension").also { it.writeBytes(bytes) }
    }
}
