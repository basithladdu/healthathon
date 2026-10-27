package `in`.wedevit.care

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class CareSession(val userId: String, val email: String, val access: String, val refresh: String, val expires: Long)
data class CareMember(val userId: String, val canEdit: Boolean, val email: String, val careTeamRead: Boolean = false, val careTeamPermissionKnown: Boolean = false)
data class CloudRecords(val people: List<Person>, val entries: List<CareItem>, val notes: List<CareVersion>, val confirmations: List<Acknowledgement>, val editable: Set<String>, val owned: Set<String>, val verifiedReviews: Set<String> = emptySet(), val authenticatedConfirmations: Set<String> = emptySet())
internal class CareHttpException(val status: Int, message: String, val code: String = "") : IllegalArgumentException(message)
/** A server feature the app asks for is not installed yet (PostgREST: no such function), so the screen can say so instead of guessing. */
internal fun Throwable.isMissingServerFeature() = this is CareHttpException && (status == 404 || code == "PGRST202" || code == "42883")
/** What a sign-up actually produced on the server. */
enum class CareSignUp { SIGNED_IN, CONFIRMATION_SENT, ALREADY_REGISTERED }

internal fun decodeAcknowledgement(row: JSONObject): Acknowledgement {
    val data = row.getJSONObject("data")
    val id = row.getString("id")
    val versionId = data.optString("versionId").ifBlank { id }
    return Acknowledgement(versionId, data.getString("contentHash"), data.getString("name"), data.getString("relationship"), data.getLong("at"), id)
}

class CareCloud(context: Context) {
    private val preferences = context.getSharedPreferences("care-account", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    val session = MutableStateFlow(runCatching { preferences.getString("session", null)?.let { decodeSession(JSONObject(unseal(it))) } }.getOrNull())
    // Saanthvana project (26 Oct 2026): accounts, sharing and invites run here.
    private val base = "https://kjnhtakqzboayqorrlue.supabase.co"
    private val apiKey = PUBLIC_API_KEY
    private val accountRedirect = URLEncoder.encode("https://sahara.wedevit.in/android-account", "UTF-8")

    suspend fun signIn(email: String, password: String) {
        val value = JSONObject(request("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email.trim()).put("password", password), authenticated = false))
        rememberSession(value)
    }
    /** Confirmation off: a session comes back at once. Confirmation on: a user without a session. An email that is
     *  already registered comes back with no identities (Supabase hides it), and no mail is sent for it. */
    suspend fun signUp(email: String, password: String): CareSignUp {
        require(password.length >= 10) { "Use at least 10 characters for your password." }
        val result = JSONObject(request("POST", "/auth/v1/signup?redirect_to=$accountRedirect", JSONObject().put("email", email.trim()).put("password", password), authenticated = false))
        if (result.has("access_token")) { rememberSession(result); return CareSignUp.SIGNED_IN }
        return careSignUpOutcome(result)
    }
    suspend fun resendConfirmation(email: String) {
        request("POST", "/auth/v1/resend?redirect_to=$accountRedirect", JSONObject().put("type", "signup").put("email", email.trim()), authenticated = false)
    }
    /** Forgets this phone's sign-in without calling the server (used after the account itself was deleted). */
    suspend fun forgetSession() {
        withContext(NonCancellable + Dispatchers.IO) {
            check(preferences.edit().remove("session").commit()) { "Couldn't finish signing out. Please try again." }
            session.value = null
        }
        CareAddDrafts.clearAll()
    }
    suspend fun sendPasswordReset(email: String) {
        request("POST", "/auth/v1/recover?redirect_to=$accountRedirect", JSONObject().put("email", email.trim()), authenticated = false)
    }
    suspend fun signOut() {
        try { request("POST", "/auth/v1/logout", JSONObject()) }
        catch (e: CareHttpException) { if (e.status != 401 && e.status != 403) throw e }
        finally {
            // Finish the disk write before the account screen can close or the process exits.
            withContext(NonCancellable + Dispatchers.IO) {
                check(preferences.edit().remove("session").commit()) { "Couldn't finish signing out. Please try again." }
                session.value = null
            }
            CareAddDrafts.clearAll()
        }
    }
    suspend fun addPerson(person: Person) {
        request("POST", "/rest/v1/healthathon_people", JSONObject().put("id", person.id).put("name", person.name).put("created_ms", person.created))
    }
    suspend fun removePerson(id: String) { require(JSONArray(request("DELETE", "/rest/v1/healthathon_people?id=eq.$id")).length() == 1) { "These records changed. Refresh and try again." } }
    suspend fun saveEntry(item: CareItem, expectedAccountId: String? = null): CareItem {
        // The phone keeps what the server holds, so symptom text is redacted for both.
        val shared = CarePrivacy.entryForUpload(item)
        val result = save("entry", shared.id, shared.personId, entryJson(shared), shared.revision, expectedAccountId)
        return shared.copy(revision = result.getLong("revision"))
    }
    suspend fun removeEntry(item: CareItem) {
        val deleted = JSONArray(request("DELETE", "/rest/v1/healthathon_records?kind=eq.entry&id=eq.${item.id}&revision=eq.${item.revision}"))
        require(deleted.length() == 1) { "This entry changed. Refresh before removing it." }
    }
    suspend fun saveNote(note: CareVersion) { save("note", note.id, note.personId, noteJson(note), 0) }
    suspend fun confirm(personId: String, ack: Acknowledgement): Acknowledgement {
        val data = JSONObject().put("contentHash", ack.contentHash).put("name", ack.name).put("relationship", ack.relationship).put("at", ack.at)
        // Retain the legacy patient payload/ID; separate family rows require the patient-first migration.
        if (ack.id != ack.versionId) data.put("versionId", ack.versionId)
        data.put("versionId", ack.versionId)
        val saved = save("ack", ack.id, personId, data, 0)
        require(saved.getJSONObject("data").optString("actorId") == session.value?.userId) { "The account confirmation could not be verified. Refresh before trying again." }
        return decodeAcknowledgement(saved)
    }
    private suspend fun save(kind: String, id: String, person: String, data: JSONObject, revision: Long, expectedAccountId: String? = null) = JSONObject(request("POST", "/rest/v1/rpc/healthathon_save_record", JSONObject().put("p_id", id).put("p_person", person).put("p_kind", kind).put("p_data", data).put("p_revision", revision), expectedAccountId = expectedAccountId))
    suspend fun invite(personId: String, edit: Boolean, careTeam: Boolean = false): String = request("POST", "/rest/v1/rpc/${if (careTeam) "healthathon_invite_care_team" else "healthathon_invite"}", JSONObject().put("p_person", personId).put("p_edit", edit).also { if (careTeam) it.put("p_care_team", true) }).trim().trim('"')
    suspend fun inviteAccess(personId: String, role: String, edit: Boolean, permissions: Map<String, Boolean>, days: Int, expectedAccountId: String? = null): String {
        require(role == "family" || role == "clinician") { "Choose who you are inviting." }
        require(days in 1..365) { "Choose how long access lasts." }
        val body = JSONObject().put("p_person", personId).put("p_role", role).put("p_edit", edit).put("p_permissions", JSONObject().also { json -> permissions.forEach { (key, value) -> json.put(key, value) } }).put("p_days", days)
        val code = request("POST", "/rest/v1/rpc/healthathon_invite_access", body, expectedAccountId = expectedAccountId).trim().trim('"')
        require(Regex("[a-f0-9]{48}").matches(code)) { "The invitation receipt did not match. Refresh before retrying." }
        return code
    }
    suspend fun join(code: String) { request("POST", "/rest/v1/rpc/healthathon_join", JSONObject().put("p_code", code.trim())) }
    suspend fun members(personId: String): List<CareMember> {
        val rows = JSONArray(request("POST", "/rest/v1/rpc/healthathon_members_list", JSONObject().put("p_person", personId)))
        val permissions = runCatching { JSONArray(request("POST", "/rest/v1/rpc/healthathon_care_team_permissions", JSONObject().put("p_person", personId))) }.getOrNull()
        val enabled = permissions?.let { values -> (0 until values.length()).associate { values.getJSONObject(it).let { row -> row.getString("user_id") to row.getBoolean("care_team") } } }
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> CareMember(row.getString("user_id"), row.getBoolean("can_edit"), row.getString("email"), enabled?.get(row.getString("user_id")) == true, enabled != null) } }
    }
    suspend fun removeMember(personId: String, userId: String) { require(JSONArray(request("DELETE", "/rest/v1/healthathon_members?person_id=eq.$personId&user_id=eq.$userId")).length() == 1) { "This person's access had already changed. Refresh the list." } }

    suspend fun load(): CloudRecords {
        val user = session.value?.userId ?: error("Sign in first")
        val personRows = rows("healthathon_people?order=created_ms.asc")
        val membership = rows("healthathon_members?user_id=eq.$user")
        val owned = personRows.filter { it.getString("owner_id") == user }.map { it.getString("id") }.toSet()
        val editable = owned + membership.filter { it.getBoolean("can_edit") }.map { it.getString("person_id") }
        val entries = mutableListOf<CareItem>(); val notes = mutableListOf<CareVersion>(); val acks = mutableListOf<Acknowledgement>(); val authenticatedAcks = mutableSetOf<String>()
        rows("healthathon_records?order=updated_at.asc").forEach { row ->
            val d = row.getJSONObject("data"); val id = row.getString("id"); val person = row.getString("person_id")
            when (row.getString("kind")) {
                "entry" -> entries.add(CareItem(id, person, d.getString("kind"), d.getString("title"), d.optString("detail"), d.optLong("due"), d.optString("doneOn"), d.getLong("created"), row.getLong("revision")))
                "note" -> notes.add(CareVersion(
                    id = id, personId = person, version = d.getInt("version"), personName = d.getString("personName"), source = d.getString("source"),
                    priorities = d.optString("priorities"), participants = d.optString("participants"), topics = d.optString("topics"), questions = d.optString("questions"),
                    nextSteps = d.optString("nextSteps"), doctor = d.getString("doctor"), published = d.getLong("published"), contentHash = d.getString("contentHash"),
                    draftOrigin = d.optString("draftOrigin", ReviewWorkflow.MANUAL_DRAFT), reviewStatus = d.optString("reviewStatus", ReviewWorkflow.REVIEWED_UNVERIFIED),
                    reviewerVerification = d.optString("reviewerVerification", ReviewWorkflow.NOT_VERIFIED),
                    transcriptionModel = d.optString("transcriptionModel"), transcribedAt = d.optLong("transcribedAt"),
                    conversationId=d.optString("conversationId"),contextJson=d.optString("contextJson"),hashScheme=d.optString("hashScheme","legacy"),
                ).also {
                    if(it.hashScheme==DoctorConversationContext.HASH_SCHEME)require(d.getInt("contextVersion")==1)
                    else require(!d.has("contextVersion") && it.conversationId.isBlank() && it.contextJson.isBlank()) { "This conversation format requires an updated app." }
                    DoctorConversationContext.validateVersion(it)
                })
                "ack" -> { acks.add(decodeAcknowledgement(row)); if (runCatching { java.util.UUID.fromString(d.optString("actorId")) }.isSuccess) authenticatedAcks += id }
            }
        }
        // Missing review service leaves ordinary records usable, without granting a badge.
        val receipts = runCatching { verifiedReviewIds() }.getOrDefault(emptySet())
        return CloudRecords(personRows.map { Person(it.getString("id"), it.getString("name"), it.getLong("created_ms")) }, entries, notes, acks, editable, owned, receipts, authenticatedAcks)
    }
    private suspend fun rows(path: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>(); var offset = 0
        do {
            val page = JSONArray(request("GET", "/rest/v1/$path&limit=500&offset=$offset"))
            for (i in 0 until page.length()) result.add(page.getJSONObject(i))
            offset += page.length()
        } while (page.length() == 500)
        return result
    }
    private suspend fun token(forceRefresh: Boolean = false): String = refreshLock.withLock {
        val saved = session.value ?: throw IllegalArgumentException("Sign in to continue.")
        if (!forceRefresh && saved.expires > System.currentTimeMillis() / 1000 + 60) return@withLock saved.access
        val response = JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", saved.refresh), authenticated = false))
        rememberSession(response)
        session.value!!.access
    }
    internal suspend fun medicineOrganizerToken(): String = token()
    internal suspend fun transcriptionToken(forceRefresh: Boolean = false): String = token(forceRefresh)
    /** Binary transport is deliberately restricted to the private synthetic-file bucket. */
    internal suspend fun doctorFileRequest(method: String, objectPath: String, bytes: ByteArray? = null, mime: String = "application/octet-stream"): ByteArray {
        require(method in setOf("POST", "GET") && Regex("[a-f0-9-]{36}/[a-f0-9-]{36}/[a-f0-9-]{36}").matches(objectPath))
        require(bytes == null || bytes.size in 1..4_194_304)
        val access = token()
        return withContext(Dispatchers.IO) {
            val action = if (method == "GET") "authenticated/" else ""
            val connection = URL("$base/storage/v1/object/${action}sahara-doctor-test-files/$objectPath").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method; connection.connectTimeout = 12000; connection.readTimeout = 45000; connection.instanceFollowRedirects = false
                connection.setRequestProperty("apikey", apiKey); connection.setRequestProperty("Authorization", "Bearer $access")
                connection.setRequestProperty("Content-Type", mime); connection.setRequestProperty("x-upsert", "false")
                bytes?.let { connection.doOutput = true; connection.setFixedLengthStreamingMode(it.size); connection.outputStream.use { stream -> stream.write(it) } }
                require(connection.responseCode in 200..299) { "The protected file request failed. Check your account access and try again." }
                connection.inputStream.use { stream ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) { val n = stream.read(buffer); if (n < 0) break; require(output.size() + n <= 4_194_304) { "This file exceeds the protected download limit." }; output.write(buffer, 0, n) }
                    output.toByteArray()
                }
            } finally { connection.disconnect() }
        }
    }
    internal suspend fun request(method: String, path: String, body: JSONObject? = null, authenticated: Boolean = true, expectedAccountId: String? = null): String {
        if (expectedAccountId != null) require(session.value?.userId == expectedAccountId) { "The signed-in account changed. Reopen this care profile." }
        val access = if (authenticated) token() else null
        if (expectedAccountId != null) require(session.value?.userId == expectedAccountId) { "The signed-in account changed. Reopen this care profile." }
        return withContext(Dispatchers.IO) {
            val connection = URL(base + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method; connection.connectTimeout = 12000; connection.readTimeout = 20000; connection.instanceFollowRedirects = false
                connection.setRequestProperty("apikey", apiKey)
                access?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Prefer", "return=representation")
                if (body != null) { connection.doOutput = true; connection.outputStream.use { it.write(body.toString().toByteArray()) } }
                val status = connection.responseCode
                val text = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val error = runCatching { JSONObject(text) }.getOrNull()
                    val code = error?.optString("code").orEmpty()
                    val friendly = when {
                        error?.optString("message").orEmpty().startsWith("Clinician verification") -> error?.optString("message").orEmpty()
                        code == "40001" || code == "23505" -> "This record changed. Refresh and try again."
                        status == 401 -> "Sign in again to continue."
                        status == 403 || code == "42501" -> "You don't have permission to change these records."
                        status == 429 -> "Please wait a moment and try again."
                        path.startsWith("/auth/") -> listOf("msg", "error_description", "message").mapNotNull { error?.optString(it)?.takeIf(String::isNotBlank) }.firstOrNull() ?: "Couldn't sign in. Check your email and password."
                        else -> error?.optString("message")?.takeIf { code == "P0001" } ?: "Couldn't save. Your entry is still here."
                    }
                    throw CareHttpException(status, friendly, code.ifBlank { error?.optString("error_code").orEmpty() })
                }
                text
            } catch (e: java.io.IOException) { throw CareSyncOffline("Couldn't reach your care records. Your entry is still here.", e) }
            finally { connection.disconnect() }
        }
    }
    private suspend fun rememberSession(value: JSONObject) {
        val user = value.getJSONObject("user")
        val data = JSONObject().put("id", user.getString("id")).put("email", user.optString("email")).put("access", value.getString("access_token")).put("refresh", value.getString("refresh_token")).put("expires", value.optLong("expires_at", System.currentTimeMillis() / 1000 + value.getLong("expires_in")))
        withContext(Dispatchers.IO) { check(preferences.edit().putString("session", seal(data.toString())).commit()) { "Couldn't save your sign-in. Please try again." } }
        session.value = decodeSession(data)
    }
    private fun decodeSession(data: JSONObject) = CareSession(data.getString("id"), data.getString("email"), data.getString("access"), data.getString("refresh"), data.getLong("expires"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("healthathon-account", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("healthathon-account", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
    }
    private fun seal(value: String): String { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP) }
    private fun unseal(value: String): String { val bytes = Base64.decode(value, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))); return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size))) }

    companion object {
        const val PUBLIC_API_KEY = "sb_publishable_bheyh26N5MKpXNEJDQgq8w_oDRhW8Xe"
    }
}

internal fun careSignUpOutcome(result: JSONObject): CareSignUp {
    val user = result.optJSONObject("user") ?: result
    val identities = user.optJSONArray("identities")
    return if (identities != null && identities.length() == 0) CareSignUp.ALREADY_REGISTERED else CareSignUp.CONFIRMATION_SENT
}

private fun entryJson(i: CareItem) = JSONObject().put("kind", i.kind).put("title", i.title).put("detail", i.detail).put("due", i.due).put("doneOn", i.doneOn).put("created", i.created)
private fun noteJson(n: CareVersion) = JSONObject().put("version", n.version).put("personName", n.personName).put("source", n.source).put("priorities", n.priorities).put("participants", n.participants).put("topics", n.topics).put("questions", n.questions).put("nextSteps", n.nextSteps).put("doctor", n.doctor).put("published", n.published).put("contentHash", n.contentHash).put("draftOrigin", n.draftOrigin).put("reviewStatus", n.reviewStatus).put("reviewerVerification", n.reviewerVerification).put("transcriptionModel", n.transcriptionModel).put("transcribedAt", n.transcribedAt).also { if(n.contextJson.isNotBlank())it.put("conversationId",n.conversationId).put("contextJson",n.contextJson).put("contextVersion",1).put("hashScheme",n.hashScheme) }
