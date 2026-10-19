package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SharedCareDraft(val id: String, val personId: String, val authorId: String, val revision: Long, val status: String, val conversation: Conversation)
data class CareClinicianProfile(val name: String, val council: String, val registration: String, val status: String, val note: String)

internal fun decodeCareVersion(row: JSONObject): CareVersion {
    val d = row.getJSONObject("data")
    require(row.getString("kind") == "note" && runCatching { UUID.fromString(row.getString("id")) }.isSuccess)
    require(d.getString("contentHash").matches(Regex("[a-f0-9]{64}")) && d.getInt("version") > 0)
    return CareVersion(id = row.getString("id"), personId = row.getString("person_id"), version = d.getInt("version"),
        personName = d.getString("personName"), source = d.getString("source"), priorities = d.optString("priorities"),
        participants = d.optString("participants"), topics = d.optString("topics"), questions = d.optString("questions"),
        nextSteps = d.optString("nextSteps"), doctor = d.getString("doctor"), published = d.getLong("published"), contentHash = d.getString("contentHash"),
        draftOrigin = d.optString("draftOrigin", ReviewWorkflow.MANUAL_DRAFT), reviewStatus = d.optString("reviewStatus", ReviewWorkflow.REVIEWED_UNVERIFIED),
        reviewerVerification = d.optString("reviewerVerification", ReviewWorkflow.NOT_VERIFIED), transcriptionModel = d.optString("transcriptionModel"), transcribedAt = d.optLong("transcribedAt"))
}
internal fun decodeSharedCareDraft(row: JSONObject): SharedCareDraft {
    val d = row.getJSONObject("data"); val person = row.getString("person_id")
    return SharedCareDraft(row.getString("id"), person, row.getString("author_id"), row.getLong("revision"), row.getString("status"),
        Conversation(person, source = d.getString("source"), priorities = d.optString("priorities"), participants = d.optString("participants"),
            topics = d.optString("topics"), questions = d.optString("questions"), nextSteps = d.optString("nextSteps")))
}
suspend fun CareCloud.clinicianProfile(): CareClinicianProfile? {
    val user = session.value?.userId ?: error("Sign in first.")
    val rows = JSONArray(request("GET", "/rest/v1/healthathon_clinician_requests?user_id=eq.$user"))
    return rows.optJSONObject(0)?.let { CareClinicianProfile(it.getString("name"), it.getString("council"), it.getString("registration"), it.getString("status"), it.optString("review_note")) }
}
suspend fun CareCloud.requestClinician(name: String, council: String, registration: String) {
    request("POST", "/rest/v1/rpc/healthathon_request_clinician", JSONObject().put("p_name", name.trim()).put("p_council", council.trim()).put("p_registration", registration.trim()))
}
suspend fun CareCloud.sharedDrafts(person: String): List<SharedCareDraft> {
    require(runCatching { UUID.fromString(person) }.isSuccess)
    val result = mutableListOf<SharedCareDraft>(); var offset = 0
    do {
        val rows = JSONArray(request("GET", "/rest/v1/healthathon_care_drafts?person_id=eq.$person&order=updated_at.desc&limit=500&offset=$offset"))
        for (i in 0 until rows.length()) result += decodeSharedCareDraft(rows.getJSONObject(i))
        offset += rows.length()
    } while (rows.length() == 500)
    return result
}
suspend fun CareCloud.saveSharedDraft(value: Conversation, id: String, revision: Long, submit: Boolean): SharedCareDraft {
    require(value.source.isNotBlank() && value.source.length <= NoteRules.MAX_SOURCE_CHARS && NoteRules.values(value).all { it.length <= 4000 }) { "Add the original words and review the sections first." }
    val data = JSONObject().put("source", value.source).put("priorities", value.priorities).put("participants", value.participants).put("topics", value.topics).put("questions", value.questions).put("nextSteps", value.nextSteps)
    val saved = decodeSharedCareDraft(JSONObject(request("POST", "/rest/v1/rpc/healthathon_save_draft", JSONObject().put("p_id", id).put("p_person", value.personId).put("p_data", data).put("p_revision", revision).put("p_submit", submit))))
    require(saved.id == id && saved.personId == value.personId && saved.authorId == session.value?.userId && saved.revision == revision + 1) { "The saved draft didn't match. Refresh before trying again." }
    return saved
}
suspend fun CareCloud.reviewSharedDraft(draft: SharedCareDraft): CareVersion {
    val row = JSONObject(request("POST", "/rest/v1/rpc/healthathon_review_draft", JSONObject().put("p_id", draft.id).put("p_revision", draft.revision).put("p_confirm", true)))
    val saved = decodeCareVersion(row)
    require(saved.personId == draft.personId && row.getJSONObject("data").getString("reviewerId") == session.value?.userId && saved.reviewStatus == "clinician_reviewed" && saved.reviewerVerification == "verified") { "The review receipt did not match. Refresh before trying again." }
    return saved
}
suspend fun CareCloud.verifiedReviewIds(): Set<String> {
    val rows = JSONArray(request("POST", "/rest/v1/rpc/healthathon_review_receipts", JSONObject()))
    return (0 until rows.length()).map { rows.getJSONObject(it).getString("note_id") }.toSet()
}
fun sharedCareNoteUrl(note: CareVersion) = "https://sahara.wedevit.in/shared-care?person=${note.personId}&version=${note.version}&note=${note.id}"
