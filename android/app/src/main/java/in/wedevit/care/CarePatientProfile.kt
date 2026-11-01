package `in`.wedevit.care

import android.content.Context
import org.json.JSONObject

/** Basic health details a family keeps about one person. Blank text / null numbers mean "not filled in". */
data class CarePatientProfile(
    val age: Int? = null, val sex: String = "", val weightKg: Double? = null, val heightCm: Int? = null,
    val bloodGroup: String = "", val illness: String = "", val allergies: String = "", val phone: String = "", val address: String = "",
) {
    fun encode(): String = JSONObject().put("v", 1).put("sex", sex).put("blood", bloodGroup).put("illness", illness)
        .put("allergies", allergies).put("phone", phone).put("address", address)
        .also { j -> age?.let { j.put("age", it) }; weightKg?.let { j.put("weight", it) }; heightCm?.let { j.put("height", it) } }.toString()
    companion object {
        val SEX = listOf("Female", "Male", "Other")
        val BLOOD = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−", "Not known")
        fun decode(json: String): CarePatientProfile = decodeOrNull(json) ?: CarePatientProfile()
        fun decodeOrNull(json: String): CarePatientProfile? = runCatching {
            val j = JSONObject(json)
            CarePatientProfile(if (j.has("age")) j.getInt("age") else null, j.optString("sex"), if (j.has("weight")) j.getDouble("weight") else null,
                if (j.has("height")) j.getInt("height") else null, j.optString("blood"), j.optString("illness"), j.optString("allergies"), j.optString("phone"), j.optString("address"))
        }.getOrNull()
    }
}

/**
 * The profile travels in the shared care record as one "journal" entry (an allowed server kind) whose detail carries [PREFIX], like side-effect notes.
 * Every list that shows journal notes must skip entries where [isStructured] is true.
 */
object PatientProfileCodec {
    const val PREFIX = "sahara:profile:v1\n"
    const val TITLE = "Patient profile"
    fun isStructured(text: String) = text.startsWith(PREFIX)
    fun encode(p: CarePatientProfile) = PREFIX + p.encode()
    fun decode(text: String): CarePatientProfile? = if (!isStructured(text)) null else CarePatientProfile.decodeOrNull(text.removePrefix(PREFIX))
    fun item(personId: String, p: CarePatientProfile, now: Long, id: String = newId()) = CareItem(id = id, personId = personId, kind = "journal", title = TITLE, detail = encode(p), created = now)
    /** One profile per person: the newest readable profile entry wins if a sync ever leaves two. */
    fun latest(items: List<CareItem>, personId: String): CareItem? =
        items.filter { it.personId == personId && it.kind == "journal" && isStructured(it.detail) && decode(it.detail) != null }.maxWithOrNull(compareBy<CareItem> { it.created }.thenBy { it.id })
}

/** Which number was out of range, with a friendly sentence in both languages. */
enum class ProfileError(val en: String, val hi: String) {
    AGE("Age should be between 0 and 120 years.", "उम्र 0 से 120 साल के बीच होनी चाहिए।"),
    WEIGHT("Weight should be between 1 and 300 kg.", "वज़न 1 से 300 किलो के बीच होना चाहिए।"),
    HEIGHT("Height should be between 30 and 250 cm.", "क़द 30 से 250 सेमी के बीच होना चाहिए।"),
}

object CarePatientProfileRules {
    /** Empty text is fine (the field stays blank); text that is not a number in range is an error. */
    fun age(text: String): Pair<Int?, ProfileError?> = text.trim().let { s -> if (s.isEmpty()) null to null else s.toIntOrNull()?.takeIf { it in 0..120 }?.let { it to null } ?: (null to ProfileError.AGE) }
    fun weight(text: String): Pair<Double?, ProfileError?> = text.trim().replace(',', '.').let { s -> if (s.isEmpty()) null to null else s.toDoubleOrNull()?.takeIf { it >= 1.0 && it <= 300.0 }?.let { it to null } ?: (null to ProfileError.WEIGHT) }
    fun height(text: String): Pair<Int?, ProfileError?> = text.trim().let { s -> if (s.isEmpty()) null to null else s.toIntOrNull()?.takeIf { it in 30..250 }?.let { it to null } ?: (null to ProfileError.HEIGHT) }
    /** "62" not "62.0": whole weights print without a decimal. */
    fun weightText(kg: Double?): String = kg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
}

/** Legacy phone-only storage (SharedPreferences JSON per account and person). New saves go to the shared care record; this is only a read fallback. */
internal class CarePatientProfileStore(context: Context, namespace: String) {
    private val safe = namespace.ifBlank { "guest" }.also { require(it.matches(Regex("[A-Za-z0-9-]+"))) }
    private val preferences = context.applicationContext.getSharedPreferences("patient-profile-v1-$safe", Context.MODE_PRIVATE)
    fun load(personId: String): CarePatientProfile = loadOrNull(personId) ?: CarePatientProfile()
    /** Older phone-only copy; read as a fallback until a shared profile exists. */
    fun loadOrNull(personId: String): CarePatientProfile? = preferences.getString(personId, null)?.let { CarePatientProfile.decodeOrNull(it) }
    fun save(personId: String, profile: CarePatientProfile) { check(preferences.edit().putString(personId, profile.encode()).commit()) { "The profile could not be saved on this phone." } }
    fun remove(personId: String) { preferences.edit().remove(personId).commit() }
}
