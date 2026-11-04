package `in`.wedevit.care

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Basic health details a family keeps about one person. Blank text / null numbers mean "not filled in".
 * [weightKg] is the current weight; [weights] is the dated history behind it (empty for profiles saved before 4 Nov 2026).
 */
data class CarePatientProfile(
    val age: Int? = null, val sex: String = "", val weightKg: Double? = null, val heightCm: Int? = null,
    val bloodGroup: String = "", val illness: String = "", val allergies: String = "", val phone: String = "", val address: String = "",
    val weights: List<CareWeightEntry> = emptyList(),
) {
    // Still "v": 1: "weights" is an extra key that older apps ignore, and a profile without it reads as an empty history.
    fun encode(): String = JSONObject().put("v", 1).put("sex", sex).put("blood", bloodGroup).put("illness", illness)
        .put("allergies", allergies).put("phone", phone).put("address", address)
        .also { j -> age?.let { j.put("age", it) }; weightKg?.let { j.put("weight", it) }; heightCm?.let { j.put("height", it) } }
        .also { j -> if (weights.isNotEmpty()) j.put("weights", JSONArray().apply { weights.forEach { put(JSONObject().put("day", it.day.toString()).put("kg", it.kg)) } }) }
        .toString()
    companion object {
        val SEX = listOf("Female", "Male", "Other")
        val BLOOD = listOf("A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−", "Not known")
        fun decode(json: String): CarePatientProfile = decodeOrNull(json) ?: CarePatientProfile()
        fun decodeOrNull(json: String): CarePatientProfile? = runCatching {
            val j = JSONObject(json)
            CarePatientProfile(if (j.has("age")) j.getInt("age") else null, j.optString("sex"), if (j.has("weight")) j.getDouble("weight") else null,
                if (j.has("height")) j.getInt("height") else null, j.optString("blood"), j.optString("illness"), j.optString("allergies"), j.optString("phone"), j.optString("address"),
                CareWeightHistory.decode(j.optJSONArray("weights")))
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

/** One dated weight. Only the day is kept: weights are not taken more than once a day. */
data class CareWeightEntry(val day: LocalDate, val kg: Double)

enum class CareWeightTrend { UP, DOWN, STEADY }

/** The weight history kept inside the profile, and the small trend the Account card shows. */
object CareWeightHistory {
    /** Enough for years of weekly weights while keeping the profile entry small. */
    const val MAX = 120
    /** A change smaller than this (kg) reads as steady. */
    const val STEADY_KG = 0.05

    /**
     * Adds [kg] for [day]: a second weight on the same day replaces the first; null leaves the history as it is.
     * Oldest first, at most [MAX] entries (the oldest are dropped).
     */
    fun record(history: List<CareWeightEntry>, kg: Double?, day: LocalDate): List<CareWeightEntry> {
        if (kg == null) return sorted(history)
        return (history.filter { it.day != day } + CareWeightEntry(day, kg)).let(::sorted).takeLast(MAX)
    }

    /**
     * The history after a profile save. A weight the person did not change adds nothing, so editing the phone number
     * does not log a new weight; a new or changed weight is logged for [day].
     */
    fun afterSave(before: CarePatientProfile, newKg: Double?, day: LocalDate): List<CareWeightEntry> =
        if (newKg == null || newKg == before.weightKg) sorted(before.weights) else record(before.weights, newKg, day)

    fun sorted(history: List<CareWeightEntry>): List<CareWeightEntry> = history.sortedBy { it.day }

    /**
     * The history the card may draw a trend from: only when its newest weight is the current one. An older app can
     * change the weight without logging it; then the card shows the weight alone rather than a wrong trend.
     */
    fun forCard(profile: CarePatientProfile): List<CareWeightEntry> = sorted(profile.weights).takeIf { profile.weightKg != null && it.lastOrNull()?.kg == profile.weightKg }.orEmpty()

    /** Latest minus the one before, to one decimal; null with fewer than two weights. */
    fun change(history: List<CareWeightEntry>): Double? = sorted(history).takeLast(2).takeIf { it.size == 2 }?.let { (a, b) -> round1(b.kg - a.kg) }

    /** Latest minus the first recorded weight, to one decimal; null with fewer than two weights. */
    fun changeSinceFirst(history: List<CareWeightEntry>): Double? = sorted(history).takeIf { it.size >= 2 }?.let { round1(it.last().kg - it.first().kg) }

    fun trend(history: List<CareWeightEntry>): CareWeightTrend? = change(history)?.let { when { it >= STEADY_KG -> CareWeightTrend.UP; it <= -STEADY_KG -> CareWeightTrend.DOWN; else -> CareWeightTrend.STEADY } }

    /** The last [n] weights, oldest first, for the small line on the card. */
    fun recent(history: List<CareWeightEntry>, n: Int = 8): List<CareWeightEntry> = sorted(history).takeLast(n)

    /** "1.5" for a change, without a sign; "1" not "1.0". */
    fun amountText(kg: Double): String = CarePatientProfileRules.weightText(round1(kotlin.math.abs(kg)))

    internal fun decode(array: JSONArray?): List<CareWeightEntry> {
        if (array == null) return emptyList()
        val out = ArrayList<CareWeightEntry>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val day = runCatching { LocalDate.parse(o.optString("day")) }.getOrNull() ?: continue
            val kg = o.optDouble("kg", Double.NaN).takeIf { !it.isNaN() && it >= 1.0 && it <= 300.0 } ?: continue
            out += CareWeightEntry(day, kg)
        }
        // A broken or doubled day keeps only its last weight.
        return sorted(out.associateBy { it.day }.values.toList()).takeLast(MAX)
    }

    private fun round1(x: Double) = kotlin.math.round(x * 10.0) / 10.0
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
