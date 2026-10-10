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
data class CareMember(val userId: String, val canEdit: Boolean, val email: String)
data class CloudRecords(val people: List<Person>, val entries: List<CareItem>, val notes: List<CareVersion>, val confirmations: List<Acknowledgement>, val editable: Set<String>, val owned: Set<String>)
private class CareHttpException(val status: Int, message: String) : IllegalArgumentException(message)

class CareCloud(context: Context) {
    private val preferences = context.getSharedPreferences("care-account", Context.MODE_PRIVATE)
    private val refreshLock = Mutex()
    val session = MutableStateFlow(runCatching { preferences.getString("session", null)?.let { decodeSession(JSONObject(unseal(it))) } }.getOrNull())
    private val base = "https://cghlkqeqiokjqdionrkc.supabase.co"
    private val apiKey = "sb_publishable_43mvHQiHJXVGwS71SX0GVA_GWxXBVOC"
    private val accountRedirect = URLEncoder.encode("https://sahara.wedevit.in/android-account", "UTF-8")

    suspend fun signIn(email: String, password: String) {
        val value = JSONObject(request("POST", "/auth/v1/token?grant_type=password", JSONObject().put("email", email.trim()).put("password", password), authenticated = false))
        rememberSession(value)
    }
    suspend fun signUp(email: String, password: String): Boolean {
        require(password.length >= 10) { "Use at least 10 characters for your password." }
        val result = JSONObject(request("POST", "/auth/v1/signup?redirect_to=$accountRedirect", JSONObject().put("email", email.trim()).put("password", password), authenticated = false))
        if (result.has("access_token")) { rememberSession(result); return true }
        return false
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
        }
    }
    suspend fun addPerson(person: Person) {
        request("POST", "/rest/v1/healthathon_people", JSONObject().put("id", person.id).put("name", person.name).put("created_ms", person.created))
    }
    suspend fun removePerson(id: String) { require(JSONArray(request("DELETE", "/rest/v1/healthathon_people?id=eq.$id")).length() == 1) { "These records changed. Refresh and try again." } }
    suspend fun saveEntry(item: CareItem): CareItem {
        val result = save("entry", item.id, item.personId, entryJson(item), item.revision)
        return item.copy(revision = result.getLong("revision"))
    }
    suspend fun removeEntry(item: CareItem) {
        val deleted = JSONArray(request("DELETE", "/rest/v1/healthathon_records?kind=eq.entry&id=eq.${item.id}&revision=eq.${item.revision}"))
        require(deleted.length() == 1) { "This entry changed. Refresh before removing it." }
    }
    suspend fun saveNote(note: CareVersion) { save("note", note.id, note.personId, noteJson(note), 0) }
    suspend fun confirm(personId: String, ack: Acknowledgement) { save("ack", ack.versionId, personId, JSONObject().put("contentHash", ack.contentHash).put("name", ack.name).put("relationship", ack.relationship).put("at", ack.at), 0) }
    private suspend fun save(kind: String, id: String, person: String, data: JSONObject, revision: Long) = JSONObject(request("POST", "/rest/v1/rpc/healthathon_save_record", JSONObject().put("p_id", id).put("p_person", person).put("p_kind", kind).put("p_data", data).put("p_revision", revision)))
    suspend fun invite(personId: String, edit: Boolean): String = request("POST", "/rest/v1/rpc/healthathon_invite", JSONObject().put("p_person", personId).put("p_edit", edit)).trim().trim('"')
    suspend fun join(code: String) { request("POST", "/rest/v1/rpc/healthathon_join", JSONObject().put("p_code", code.trim())) }
    suspend fun members(personId: String): List<CareMember> {
        val rows = JSONArray(request("POST", "/rest/v1/rpc/healthathon_members_list", JSONObject().put("p_person", personId)))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> CareMember(row.getString("user_id"), row.getBoolean("can_edit"), row.getString("email")) } }
    }
    suspend fun removeMember(personId: String, userId: String) { request("DELETE", "/rest/v1/healthathon_members?person_id=eq.$personId&user_id=eq.$userId") }

    suspend fun load(): CloudRecords {
        val user = session.value?.userId ?: error("Sign in first")
        val personRows = rows("healthathon_people?order=created_ms.asc")
        val membership = rows("healthathon_members?user_id=eq.$user")
        val owned = personRows.filter { it.getString("owner_id") == user }.map { it.getString("id") }.toSet()
        val editable = owned + membership.filter { it.getBoolean("can_edit") }.map { it.getString("person_id") }
        val entries = mutableListOf<CareItem>(); val notes = mutableListOf<CareVersion>(); val acks = mutableListOf<Acknowledgement>()
        rows("healthathon_records?order=updated_at.asc").forEach { row ->
            val d = row.getJSONObject("data"); val id = row.getString("id"); val person = row.getString("person_id")
            when (row.getString("kind")) {
                "entry" -> entries.add(CareItem(id, person, d.getString("kind"), d.getString("title"), d.optString("detail"), d.optLong("due"), d.optString("doneOn"), d.getLong("created"), row.getLong("revision")))
                "note" -> notes.add(CareVersion(id, person, d.getInt("version"), d.getString("personName"), d.getString("source"), d.optString("priorities"), d.optString("participants"), d.optString("topics"), d.optString("questions"), d.optString("nextSteps"), d.getString("doctor"), d.getLong("published"), d.getString("contentHash")))
                "ack" -> acks.add(Acknowledgement(id, d.getString("contentHash"), d.getString("name"), d.getString("relationship"), d.getLong("at")))
            }
        }
        return CloudRecords(personRows.map { Person(it.getString("id"), it.getString("name"), it.getLong("created_ms")) }, entries, notes, acks, editable, owned)
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
    private suspend fun token(): String = refreshLock.withLock {
        val saved = session.value ?: throw IllegalArgumentException("Sign in to continue.")
        if (saved.expires > System.currentTimeMillis() / 1000 + 60) return@withLock saved.access
        val response = JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", saved.refresh), authenticated = false))
        rememberSession(response)
        session.value!!.access
    }
    private suspend fun request(method: String, path: String, body: JSONObject? = null, authenticated: Boolean = true): String {
        val access = if (authenticated) token() else null
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
                        code == "40001" || code == "23505" -> "This record changed. Refresh and try again."
                        status == 401 -> "Sign in again to continue."
                        status == 403 || code == "42501" -> "You don't have permission to change these records."
                        status == 429 -> "Please wait a moment and try again."
                        path.startsWith("/auth/") -> listOf("msg", "error_description", "message").mapNotNull { error?.optString(it)?.takeIf(String::isNotBlank) }.firstOrNull() ?: "Couldn't sign in. Check your email and password."
                        else -> error?.optString("message")?.takeIf { code == "P0001" } ?: "Couldn't save. Your entry is still here."
                    }
                    throw CareHttpException(status, friendly)
                }
                text
            } catch (e: java.io.IOException) { throw IllegalArgumentException("Couldn't reach your care records. Your entry is still here.", e) }
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
}

private fun entryJson(i: CareItem) = JSONObject().put("kind", i.kind).put("title", i.title).put("detail", i.detail).put("due", i.due).put("doneOn", i.doneOn).put("created", i.created)
private fun noteJson(n: CareVersion) = JSONObject().put("version", n.version).put("personName", n.personName).put("source", n.source).put("priorities", n.priorities).put("participants", n.participants).put("topics", n.topics).put("questions", n.questions).put("nextSteps", n.nextSteps).put("doctor", n.doctor).put("published", n.published).put("contentHash", n.contentHash)
