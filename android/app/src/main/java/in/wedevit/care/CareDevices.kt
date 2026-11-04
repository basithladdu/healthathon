package `in`.wedevit.care

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Tubes and lines, v1 (Sujay, WhatsApp 3 Nov 2026: "date of insertion, required date of change, centre where it was
 * inserted, and complication as free text. Then we can say we will build on it."; Shirin: "an option about some redness
 * or signs of infection around the tubes"). Kept on this phone only. No medical advice: any sign ticked says only
 * "Tell your care team today". Device-specific care guides and alerts are planned for v2.0.
 */
enum class CareDeviceType(val en: String, val hi: String) {
    PORT("Chemo port / PICC line", "कीमो पोर्ट / PICC लाइन"),
    FEEDING("Feeding tube (NG / PEG)", "खाने की नली (NG / PEG)"),
    URINARY("Urinary catheter", "पेशाब की नली (कैथेटर)"),
    CHEST("Chest drain", "छाती की नली (ड्रेन)"),
    STOMA("Colostomy / ileostomy bag", "कोलोस्टॉमी / इलियोस्टॉमी थैली"),
    TRACH("Tracheostomy", "ट्रेकियोस्टॉमी"),
    STENT("Stent", "स्टेंट"),
    OTHER("Other tube or line", "दूसरी नली या लाइन"),
}

/** The daily check. Each is a yes/no question about the tube or the skin around it. */
enum class CareDeviceSign(val en: String, val hi: String) {
    REDNESS("Redness", "लालिमा"),
    SWELLING("Swelling", "सूजन"),
    PAIN("Pain", "दर्द"),
    DISCHARGE("Discharge or fluid", "मवाद या पानी निकलना"),
    BLEEDING("Bleeding", "ख़ून आना"),
    FEVER("Fever", "बुख़ार"),
    BLOCKED("Blocked or not working", "बंद है या काम नहीं कर रही"),
    MOVED("Fallen out or moved", "निकल गई या खिसक गई"),
}

/** One day's check: the signs answered "yes". Empty means everything was fine that day. */
data class CareDeviceCheck(val day: LocalDate, val signs: Set<CareDeviceSign>)

data class CareDevice(
    val id: String = UUID.randomUUID().toString(),
    val type: CareDeviceType = CareDeviceType.OTHER,
    val inserted: LocalDate? = null,
    val changeDue: LocalDate? = null,
    val centre: String = "",
    val notes: String = "",
    val checks: List<CareDeviceCheck> = emptyList(),
)

/** When the next change is due, counted in whole days from today. */
enum class CareDeviceDueKind { LATER, TODAY, OVERDUE }
data class CareDeviceDue(val kind: CareDeviceDueKind, val days: Long)

/** A date that does not make sense, with a short sentence in both languages. */
enum class CareDeviceError(val en: String, val hi: String) {
    INSERTED_FUTURE("The date put in cannot be after today.", "लगाने की तारीख़ आज के बाद की नहीं हो सकती।"),
    CHANGE_BEFORE_INSERTED("The change date should be after the date put in.", "बदलने की तारीख़ लगाने की तारीख़ के बाद होनी चाहिए।"),
}

object CareDeviceRules {
    /** About two months of daily checks per device. */
    const val MAX_CHECKS = 60

    fun due(device: CareDevice, today: LocalDate): CareDeviceDue? = device.changeDue?.let { d ->
        val days = ChronoUnit.DAYS.between(today, d)
        when { days > 0 -> CareDeviceDue(CareDeviceDueKind.LATER, days); days == 0L -> CareDeviceDue(CareDeviceDueKind.TODAY, 0); else -> CareDeviceDue(CareDeviceDueKind.OVERDUE, -days) }
    }

    fun error(inserted: LocalDate?, changeDue: LocalDate?, today: LocalDate): CareDeviceError? = when {
        inserted != null && inserted.isAfter(today) -> CareDeviceError.INSERTED_FUTURE
        inserted != null && changeDue != null && !changeDue.isAfter(inserted) -> CareDeviceError.CHANGE_BEFORE_INSERTED
        else -> null
    }

    /** Saves [signs] as [day]'s check: a second check on the same day replaces the first. Oldest first, at most [MAX_CHECKS]. */
    fun record(device: CareDevice, day: LocalDate, signs: Set<CareDeviceSign>): CareDevice =
        device.copy(checks = (device.checks.filter { it.day != day } + CareDeviceCheck(day, signs)).sortedBy { it.day }.takeLast(MAX_CHECKS))

    fun checkOn(device: CareDevice, day: LocalDate): CareDeviceCheck? = device.checks.lastOrNull { it.day == day }

    /** True when today's check has any "yes": the screen then says "Tell your care team today". */
    fun tellCareTeam(device: CareDevice, today: LocalDate): Boolean = checkOn(device, today)?.signs?.isNotEmpty() == true

    fun tellCareTeam(devices: List<CareDevice>, today: LocalDate): Boolean = devices.any { tellCareTeam(it, today) }

    /** Soonest change first; devices without a change date last. */
    fun sorted(devices: List<CareDevice>): List<CareDevice> = devices.sortedWith(compareBy<CareDevice>({ it.changeDue == null }, { it.changeDue?.toEpochDay() ?: 0L }, { it.type.ordinal }))

    fun upsert(devices: List<CareDevice>, device: CareDevice): List<CareDevice> =
        if (devices.any { it.id == device.id }) devices.map { if (it.id == device.id) device else it } else devices + device

    /** The palliative team's 24-hour phone from the newest care plan that has one, or blank. */
    fun palliativePhone(plans: List<EctprRecord>): String =
        plans.filter { it.careTeam.palliativePhone24h.isNotBlank() }.maxByOrNull { it.updatedAt }?.careTeam?.palliativePhone24h?.trim().orEmpty()

    fun encode(devices: List<CareDevice>): String = JSONObject().put("v", 1).put("devices", JSONArray().apply {
        devices.forEach { d ->
            put(JSONObject().put("id", d.id).put("type", d.type.name).put("centre", d.centre).put("notes", d.notes)
                .also { j -> d.inserted?.let { j.put("inserted", it.toString()) }; d.changeDue?.let { j.put("changeDue", it.toString()) } }
                .put("checks", JSONArray().apply { d.checks.forEach { c -> put(JSONObject().put("day", c.day.toString()).put("signs", JSONArray().apply { c.signs.sortedBy { it.ordinal }.forEach { put(it.name) } })) } }))
        }
    }).toString()

    /** Unreadable text gives an empty list; an unreadable device or check is skipped, an unknown type reads as Other. */
    fun decode(text: String?): List<CareDevice> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONObject(text).getJSONArray("devices") }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            runCatching {
                val j = array.getJSONObject(i)
                val checks = j.optJSONArray("checks")?.let { a -> (0 until a.length()).mapNotNull { k -> runCatching {
                    val c = a.getJSONObject(k); val s = c.optJSONArray("signs")
                    CareDeviceCheck(LocalDate.parse(c.getString("day")), (0 until (s?.length() ?: 0)).mapNotNull { n -> CareDeviceSign.values().firstOrNull { it.name == s!!.getString(n) } }.toSet())
                }.getOrNull() } }.orEmpty()
                CareDevice(j.getString("id").also { require(it.isNotBlank()) }, CareDeviceType.values().firstOrNull { it.name == j.optString("type") } ?: CareDeviceType.OTHER,
                    j.optString("inserted").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }, j.optString("changeDue").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) },
                    j.optString("centre"), j.optString("notes"), checks.sortedBy { it.day }.takeLast(MAX_CHECKS))
            }.getOrNull()
        }
    }
}

/** Phone-only storage, SharedPreferences JSON per account and person, like the patient profile and care plan stores. */
internal class CareDevicesStore(context: Context, namespace: String) {
    private val safe = namespace.ifBlank { "guest" }.replace(Regex("[^A-Za-z0-9-]"), "-")
    private val preferences = context.applicationContext.getSharedPreferences("care-devices-v1-$safe", Context.MODE_PRIVATE)
    private fun key(personId: String) = personId.ifBlank { "self" }
    fun load(personId: String): List<CareDevice> = CareDeviceRules.decode(preferences.getString(key(personId), null))
    fun save(personId: String, devices: List<CareDevice>) { check(preferences.edit().putString(key(personId), CareDeviceRules.encode(devices)).commit()) { "The tubes and lines could not be saved on this phone." } }
}
