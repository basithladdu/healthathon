package `in`.wedevit.care

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CareContact(val id: String, val personId: String, val name: String, val relationship: String, val role: String, val phone: String = "", val revision: Long = 0)
data class CareContactSnapshot(val readable: Boolean, val contacts: List<CareContact>)

internal fun careContextMatches(account: String, currentAccount: String, personId: String, currentPersonId: String, hasAccess: Boolean): Boolean =
    account == currentAccount && personId.isNotBlank() && personId == currentPersonId && hasAccess

object DoctorVisitRules {
    fun validate(name: String, startsAt: Long, location: String, note: String) {
        require(name.trim().length in 1..120) { "Add the doctor's name, up to 120 characters." }
        require(startsAt > 0) { "Choose a date and exact time." }
        require(location.length <= 160 && note.length <= 1600) { "Shorten the place or note." }
    }
    fun item(id: String, personId: String, doctorName: String, startsAt: Long, location: String, note: String): CareItem {
        validate(doctorName, startsAt, location, note)
        require(id.isNotBlank() && personId.isNotBlank()) { "Choose a care profile." }
        return CareItem(id, personId, "visit", "Visit with ${doctorName.trim()}",
            CareEntryCodec.encode(CareEntry(type = "schedule", category = "Visit", sender = doctorName.trim(), location = location.trim(), detail = note.trim(), timed = true)), startsAt)
    }
}

object CareContactRules {
    val roles = setOf("family", "caregiver", "clinician")
    fun validate(name: String, relationship: String, role: String, phone: String) {
        require(name.trim().length in 1..120 && relationship.trim().length in 1..80) { "Add a name and relationship." }
        require(role in roles) { "Choose family, caregiver or clinician." }
        require(phone.isBlank() || (phone.length <= 40 && phone.matches(Regex("[+0-9 ()-]{7,40}")))) { "Check the optional contact number." }
    }
}

internal fun contactJson(contact: CareContact) = JSONObject().put("id", contact.id).put("person_id", contact.personId)
    .put("display_name", contact.name).put("relationship", contact.relationship).put("role", contact.role).put("phone", contact.phone).put("revision", contact.revision)
internal fun decodeCareContact(row: JSONObject) = CareContact(row.getString("id"), row.getString("person_id"), row.getString("display_name"), row.getString("relationship"), row.getString("role"), row.optString("phone"), row.getLong("revision"))

/** Guest profiles only. This never creates memberships or uploads local contacts. */
internal class LocalCareContacts(context: Context, namespace: String = "") {
    init { require(namespace.isBlank() || runCatching { java.util.UUID.fromString(namespace) }.isSuccess) }
    private val preferences = context.getSharedPreferences("device-local-care-contacts-v1" + if (namespace.isBlank()) "" else "-$namespace", Context.MODE_PRIVATE)
    fun list(personId: String): List<CareContact> {
        val rows = JSONArray(preferences.getString(personId, "[]"))
        return (0 until rows.length()).map { decodeCareContact(rows.getJSONObject(it)) }.filter { it.personId == personId }
    }
    fun save(contact: CareContact): CareContact {
        val rows = list(contact.personId)
        val previous = rows.firstOrNull { it.id == contact.id }
        require((previous?.revision ?: 0) == contact.revision) { "This contact changed. Refresh before saving." }
        val saved = contact.copy(revision = contact.revision + 1)
        store(contact.personId, rows.filterNot { it.id == saved.id } + saved)
        return saved
    }
    fun remove(contact: CareContact) {
        val rows = list(contact.personId)
        require(rows.any { it.id == contact.id && it.revision == contact.revision }) { "This contact changed. Refresh before removing it." }
        store(contact.personId, rows.filterNot { it.id == contact.id })
    }
    fun removePerson(personId: String) { check(preferences.edit().remove(personId).commit()) }
    private fun store(personId: String, rows: List<CareContact>) {
        check(preferences.edit().putString(personId, JSONArray().also { array -> rows.forEach { array.put(contactJson(it)) } }.toString()).commit()) { "Couldn't save this contact. Try again." }
    }
}

suspend fun CareCloud.careContacts(personId: String): CareContactSnapshot {
    val account = session.value?.userId ?: error("Sign in again.")
    val result = JSONObject(request("POST", "/rest/v1/rpc/healthathon_list_care_contacts", JSONObject().put("p_person", personId), expectedAccountId = account))
    val rows = result.getJSONArray("contacts")
    return CareContactSnapshot(result.getBoolean("readable"), (0 until rows.length()).map { decodeCareContact(rows.getJSONObject(it)) }.also { require(it.all { contact -> contact.personId == personId }) })
}
suspend fun CareCloud.saveCareContact(contact: CareContact): CareContact {
    val account = session.value?.userId ?: error("Sign in again.")
    val saved = decodeCareContact(JSONObject(request("POST", "/rest/v1/rpc/healthathon_save_care_contact", JSONObject().put("p_contact", contactJson(contact)), expectedAccountId = account)))
    require(saved.id == contact.id && saved.personId == contact.personId && saved.revision == contact.revision + 1)
    return saved
}
suspend fun CareCloud.removeCareContact(contact: CareContact) { val account = session.value?.userId ?: error("Sign in again."); request("POST", "/rest/v1/rpc/healthathon_remove_care_contact", JSONObject().put("p_person", contact.personId).put("p_id", contact.id).put("p_revision", contact.revision), expectedAccountId = account) }
suspend fun CareCloud.setCareTeamSharing(personId: String, memberId: String, enabled: Boolean) { val account = session.value?.userId ?: error("Sign in again."); request("POST", "/rest/v1/rpc/healthathon_care_team_share", JSONObject().put("p_person", personId).put("p_member", memberId).put("p_enabled", enabled), expectedAccountId = account) }
