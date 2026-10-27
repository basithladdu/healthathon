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
    val saved = CareVersion(id = row.getString("id"), personId = row.getString("person_id"), version = d.getInt("version"),
        personName = d.getString("personName"), source = d.getString("source"), priorities = d.optString("priorities"),
        participants = d.optString("participants"), topics = d.optString("topics"), questions = d.optString("questions"),
        nextSteps = d.optString("nextSteps"), doctor = d.getString("doctor"), published = d.getLong("published"), contentHash = d.getString("contentHash"),
        draftOrigin = d.optString("draftOrigin", ReviewWorkflow.MANUAL_DRAFT), reviewStatus = d.optString("reviewStatus", ReviewWorkflow.REVIEWED_UNVERIFIED),
        reviewerVerification = d.optString("reviewerVerification", ReviewWorkflow.NOT_VERIFIED), transcriptionModel = d.optString("transcriptionModel"), transcribedAt = d.optLong("transcribedAt"),
        conversationId = d.optString("conversationId"), contextJson = d.optString("contextJson"), hashScheme = d.optString("hashScheme", "legacy"))
    if (saved.hashScheme == DoctorConversationContext.HASH_SCHEME) {
        UUID.fromString(saved.conversationId); require(d.getInt("contextVersion") == 1)
        DoctorConversationContext.validateVersion(saved)
    } else require(!d.has("contextVersion") && saved.conversationId.isBlank() && saved.contextJson.isBlank()) { "This conversation format requires an updated app." }
    return saved
}
internal fun decodeSharedCareDraft(row: JSONObject): SharedCareDraft {
    val d = row.getJSONObject("data"); val person = row.getString("person_id")
    if(d.has("conversationId") || d.has("contextJson") || d.has("contextVersion")) {require(d.getInt("contextVersion")==1);UUID.fromString(d.getString("conversationId"));DoctorConversationContext.validate(d.getString("source"),d.getString("contextJson"))}
    return SharedCareDraft(row.getString("id"), person, row.getString("author_id"), row.getLong("revision"), row.getString("status"),
        Conversation(person, source = d.getString("source"), priorities = d.optString("priorities"), participants = d.optString("participants"),
            topics = d.optString("topics"), questions = d.optString("questions"), nextSteps = d.optString("nextSteps"), conversationId = d.optString("conversationId"), contextJson = d.optString("contextJson")))
}
/** Capability mismatch is a hard stop, never a fallback to broad legacy sharing. */
suspend fun CareCloud.requireConversationCapabilities(expectedAccountId: String = session.value?.userId ?: error("Sign in first.")) {
    val account = expectedAccountId
    val value = try { JSONObject(request("POST", "/rest/v1/rpc/healthathon_conversation_capabilities", JSONObject(), expectedAccountId = account)) }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { error("Conversation-specific sharing needs the updated backend. Your local draft is still here.") }
    require(value.optInt("contextVersion") == 1 && value.optString("audience") == "per-conversation" && value.optBoolean("audioLocalOnly") && value.optString("hashScheme") == DoctorConversationContext.HASH_SCHEME) { "This backend cannot safely save this conversation format." }
}
data class ConversationAudience(val revision: Long, val approved: Boolean, val memberIds: Set<String>)
data class ConversationAudienceCandidate(val userId: String, val label: String)
suspend fun CareCloud.conversationCandidates(person: String, expectedAccountId: String = session.value?.userId ?: error("Sign in first.")): List<ConversationAudienceCandidate> {
    requireConversationCapabilities(expectedAccountId); UUID.fromString(person)
    val rows = JSONArray(request("POST", "/rest/v1/rpc/healthathon_conversation_candidates", JSONObject().put("p_person", person), expectedAccountId = expectedAccountId))
    return (0 until rows.length()).map { i -> rows.getJSONObject(i).let { ConversationAudienceCandidate(it.getString("userId"),it.getString("label")) } }
}
suspend fun CareCloud.conversationAudience(person: String, conversation: String, expectedAccountId: String = session.value?.userId ?: error("Sign in first.")): ConversationAudience? {
    requireConversationCapabilities(expectedAccountId); UUID.fromString(person); UUID.fromString(conversation)
    val text = request("POST", "/rest/v1/rpc/healthathon_conversation_scope_at", JSONObject().put("p_person", person).put("p_conversation", conversation), expectedAccountId = expectedAccountId)
    if (text.trim() == "null") return null
    val value = JSONObject(text); val members = value.getJSONArray("memberIds")
    return ConversationAudience(value.getLong("revision"), value.getBoolean("approved"), (0 until members.length()).map { members.getString(it) }.toSet())
}
suspend fun CareCloud.saveConversationAudience(person: String, conversation: String, previous: ConversationAudience, members: Set<String>, approve: Boolean, expectedAccountId: String = session.value?.userId ?: error("Sign in first.")): ConversationAudience {
    val account = expectedAccountId
    requireConversationCapabilities(account); UUID.fromString(person); UUID.fromString(conversation); members.forEach { UUID.fromString(it) }
    val value = JSONObject(request("POST", "/rest/v1/rpc/healthathon_conversation_scope", JSONObject().put("p_person", person).put("p_conversation", conversation).put("p_revision", previous.revision).put("p_members", JSONArray(members.toList())).put("p_approve", approve), expectedAccountId = account))
    require(value.getLong("revision") == previous.revision + 1)
    return ConversationAudience(value.getLong("revision"), value.getBoolean("approved"), members)
}
suspend fun CareCloud.clinicianProfile(): CareClinicianProfile? {
    val user = session.value?.userId ?: error("Sign in first.")
    val rows = JSONArray(request("GET", "/rest/v1/healthathon_clinician_requests?user_id=eq.$user"))
    return rows.optJSONObject(0)?.let { CareClinicianProfile(it.getString("name"), it.getString("council"), it.getString("registration"), it.getString("status"), it.optString("review_note")) }
}
suspend fun CareCloud.requestClinician(name: String, council: String, registration: String) {
    request("POST", "/rest/v1/rpc/healthathon_request_clinician", JSONObject().put("p_name", name.trim()).put("p_council", council.trim()).put("p_registration", registration.trim()))
}
suspend fun CareCloud.sharedDrafts(person: String, expectedAccountId: String = session.value?.userId ?: error("Sign in first.")): List<SharedCareDraft> {
    require(runCatching { UUID.fromString(person) }.isSuccess)
    val result = mutableListOf<SharedCareDraft>(); var offset = 0
    do {
        val rows = JSONArray(request("GET", "/rest/v1/healthathon_care_drafts?person_id=eq.$person&order=updated_at.desc&limit=500&offset=$offset", expectedAccountId = expectedAccountId))
        for (i in 0 until rows.length()) result += decodeSharedCareDraft(rows.getJSONObject(i))
        offset += rows.length()
    } while (rows.length() == 500)
    return result
}
suspend fun CareCloud.saveSharedDraft(value: Conversation, id: String, revision: Long, submit: Boolean, expectedAccountId: String = session.value?.userId ?: error("Sign in first.")): SharedCareDraft {
    val account = expectedAccountId
    requireConversationCapabilities(account)
    UUID.fromString(value.conversationId); DoctorConversationContext.validate(value.source, value.contextJson)
    require(value.source.isNotBlank() && value.source.length <= NoteRules.MAX_SOURCE_CHARS && NoteRules.values(value).all { it.length <= 4000 }) { "Add the original words and review the sections first." }
    val data = JSONObject().put("source", value.source).put("priorities", value.priorities).put("participants", value.participants).put("topics", value.topics).put("questions", value.questions).put("nextSteps", value.nextSteps).put("conversationId", value.conversationId).put("contextVersion", 1).put("contextJson", value.contextJson)
    val saved = decodeSharedCareDraft(JSONObject(request("POST", "/rest/v1/rpc/healthathon_save_draft", JSONObject().put("p_id", id).put("p_person", value.personId).put("p_data", data).put("p_revision", revision).put("p_submit", submit), expectedAccountId = account)))
    require(saved.id == id && saved.personId == value.personId && saved.authorId == session.value?.userId && saved.revision == revision + 1) { "The saved draft didn't match. Refresh before trying again." }
    require(saved.conversation.conversationId == value.conversationId && saved.conversation.contextJson == value.contextJson && saved.conversation.source == value.source) { "The exact conversation context was not preserved." }
    return saved
}
suspend fun CareCloud.reviewSharedDraft(draft: SharedCareDraft): CareVersion {
    val account = session.value?.userId ?: error("Sign in first.")
    requireConversationCapabilities(account)
    val row = JSONObject(request("POST", "/rest/v1/rpc/healthathon_review_draft", JSONObject().put("p_id", draft.id).put("p_revision", draft.revision).put("p_confirm", true), expectedAccountId = account))
    val saved = decodeCareVersion(row)
    require(saved.personId == draft.personId && row.getJSONObject("data").getString("reviewerId") == session.value?.userId && saved.reviewStatus == "clinician_reviewed" && saved.reviewerVerification == "verified") { "The review receipt did not match. Refresh before trying again." }
    require(saved.conversationId == draft.conversation.conversationId && saved.contextJson == draft.conversation.contextJson && saved.source == draft.conversation.source) { "The reviewed conversation does not match this draft." }
    return saved
}
suspend fun CareCloud.verifiedReviewIds(): Set<String> {
    val rows = JSONArray(request("POST", "/rest/v1/rpc/healthathon_review_receipts", JSONObject()))
    return (0 until rows.length()).map { rows.getJSONObject(it).getString("note_id") }.toSet()
}
fun sharedCareNoteUrl(note: CareVersion) = "https://sahara.wedevit.in/shared-care?person=${note.personId}&version=${note.version}&note=${note.id}"

/** An invitation the server still holds open (unused, not cancelled, not expired). */
data class CareOpenInvite(val id: String, val role: String, val canEdit: Boolean, val expiresAt: Long)
/** Server facts about one person with access: when they joined, which invitation they used, and when access ends. */
data class CareMemberDetail(val userId: String, val inviteId: String, val joinedAt: Long, val expiresAt: Long)
/** The result of deleting the signed-in account on the server. */
data class CareAccountDeletion(val removedPeople: Int, val loginRemoved: Boolean)

private fun careServerTime(value: String): Long = if (value.isBlank() || value == "null") 0L else runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrDefault(0L)

/** Open invitations for a person the caller owns; null when the server cannot say (feature missing or offline), never a guess. */
suspend fun CareCloud.openInvites(person: String): List<CareOpenInvite>? {
    UUID.fromString(person)
    val text = try { request("POST", "/rest/v1/rpc/healthathon_open_invites", JSONObject().put("p_person", person)) }
    catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { return null }
    val rows = JSONArray(text)
    return (0 until rows.length()).map { rows.getJSONObject(it).let { r -> CareOpenInvite(r.getString("id"), r.optString("role", "family"), r.optBoolean("can_edit"), careServerTime(r.optString("expires_at"))) } }
}
suspend fun CareCloud.cancelInvite(id: String) {
    UUID.fromString(id)
    request("POST", "/rest/v1/rpc/healthathon_cancel_invite", JSONObject().put("p_id", id))
}
/** Join details from the server; null until the owner installs 20261027210000_sahara_account_lane.sql (or when offline). */
suspend fun CareCloud.memberDetails(person: String): List<CareMemberDetail>? {
    UUID.fromString(person)
    val text = try { request("POST", "/rest/v1/rpc/healthathon_member_details", JSONObject().put("p_person", person)) }
    catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { return null }
    val rows = JSONArray(text)
    return (0 until rows.length()).map { rows.getJSONObject(it).let { r -> CareMemberDetail(r.getString("user_id"), r.optString("invite_id").takeUnless { it == "null" }.orEmpty(), careServerTime(r.optString("joined_at")), careServerTime(r.optString("expires_at"))) } }
}
/** Owner switches one person between "can read" and "can add and change". */
suspend fun CareCloud.setMemberEdit(person: String, user: String, edit: Boolean) {
    UUID.fromString(person); UUID.fromString(user)
    try { request("POST", "/rest/v1/rpc/healthathon_set_member_edit", JSONObject().put("p_person", person).put("p_user", user).put("p_edit", edit)) }
    catch (e: Exception) { if (e.isMissingServerFeature()) throw CareServerFeatureMissing(e); throw e }
}
/** Deletes the caller's Sahara data and sign-in on the server (DPDP). */
suspend fun CareCloud.deleteMyAccount(): CareAccountDeletion {
    val value = try { JSONObject(request("POST", "/rest/v1/rpc/healthathon_delete_my_account", JSONObject())) }
    catch (e: Exception) { if (e.isMissingServerFeature()) throw CareServerFeatureMissing(e); throw e }
    return CareAccountDeletion(value.optInt("removed_people"), value.optBoolean("login_removed"))
}
class CareServerFeatureMissing(cause: Throwable) : IllegalStateException("This needs a server update that isn't installed yet.", cause)
