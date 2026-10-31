package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/**
 * A side effect the patient or family noticed on one medicine, written down for the doctor's next visit.
 * It only records what happened; it never advises. Stored as a "journal" entry (an allowed server kind) whose detail carries [SideEffectCodec.PREFIX],
 * so it syncs, queues, redacts and works offline exactly like other entries, with no new server table or kind.
 */
data class SideEffectNote(
    val medicineId: String, val medicineName: String, val effects: List<String>, val other: String = "", val severity: String,
    val happenedAt: Long, val stoppedTaking: Boolean = false, val reporter: String = "",
    /** 0 until the patient/family or the doctor marks it discussed. */
    val discussedAt: Long = 0, val discussedBy: String = "",
)

data class SideEffectEntry(val item: CareItem, val note: SideEffectNote)

object SideEffectRules {
    val effects = linkedMapOf("nausea" to ("Nausea" to "मतली"), "drowsiness" to ("Drowsiness" to "बहुत नींद आना"), "constipation" to ("Constipation" to "कब्ज़"), "dizziness" to ("Dizziness" to "चक्कर आना"), "itching" to ("Itching" to "खुजली"), "confusion" to ("Confusion" to "भ्रम / उलझन"), "other" to ("Other" to "अन्य"))
    val severities = linkedMapOf("mild" to ("Mild" to "हल्का"), "moderate" to ("Moderate" to "मध्यम"), "severe" to ("Severe" to "तेज़"))
    val whenOptions = linkedMapOf("now" to ("Just now" to "अभी"), "earlier-today" to ("Earlier today" to "आज पहले"), "yesterday" to ("Yesterday" to "कल"), "two-days" to ("2 days ago or more" to "2 दिन पहले या उससे पहले"))
    const val MAX_OTHER = 300
    fun effectLabel(key: String, hindi: Boolean) = effects[key]?.let { careTr(hindi, it.first, it.second) } ?: key
    fun severityLabel(key: String, hindi: Boolean) = severities[key]?.let { careTr(hindi, it.first, it.second) } ?: key
    /** Chip choice to a time: Just now is [now]; Earlier today is 3 hours ago but never before midnight; the others are 24 and 48 hours ago. */
    fun whenAt(option: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long = when (option) {
        "earlier-today" -> maxOf(now - 3 * HOUR, Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli())
        "yesterday" -> now - 24 * HOUR
        "two-days" -> now - 48 * HOUR
        else -> now
    }
    private const val HOUR = 3_600_000L
    fun error(n: SideEffectNote, now: Long): String? {
        if (n.medicineId.isBlank() || n.medicineName.trim().length !in 1..200) return "Choose the medicine."
        if (n.effects.isEmpty() && n.other.isBlank()) return "Choose what happened or write a few words."
        if (n.effects.any { it !in effects } || n.effects.distinct().size != n.effects.size) return "Choose from the listed side effects."
        if (n.other.length > MAX_OTHER) return "Please shorten the words."
        if (n.severity !in severities) return "Choose how bad it was."
        if (n.happenedAt <= 0 || n.happenedAt > now + 60_000) return "The time cannot be in the future."
        if (n.reporter.length > 100 || n.discussedBy.length > 100 || n.discussedAt < 0) return "Please shorten the name."
        return null
    }
    /** What happened, in words: "Constipation, Other: itchy palms". */
    fun effectsText(n: SideEffectNote, hindi: Boolean): String {
        val keys = n.effects.ifEmpty { listOf("other") }
        return keys.joinToString(", ") { k -> if (k == "other" && n.other.isNotBlank()) effectLabel(k, hindi) + ": " + n.other.trim() else effectLabel(k, hindi) }
    }
    /** Row title (English: it is a stored record title, redacted on upload). */
    fun title(n: SideEffectNote): String = careInEnglish { "Side effect: " + effectsText(n, false) + " · " + n.medicineName.trim() }.take(200)
    fun item(personId: String, n: SideEffectNote, now: Long, id: String = newId()): CareItem = CareItem(id = id, personId = personId, kind = "journal", title = title(n), detail = SideEffectCodec.encode(n), created = now)
    /** Who reported it, from the signed-in role. */
    fun reporterFor(role: String) = when (role) { "Patient" -> "Patient"; "Family" -> "Family"; else -> "Care team" }
}

object SideEffectCodec {
    const val PREFIX = "sahara:sideeffect:v1\n"
    fun isStructured(text: String) = text.startsWith(PREFIX)
    fun encode(n: SideEffectNote): String {
        require(SideEffectRules.error(n, Long.MAX_VALUE / 2) == null) { SideEffectRules.error(n, Long.MAX_VALUE / 2)!! }
        val ordered = SideEffectRules.effects.keys.filter { it in n.effects }
        return PREFIX + JSONObject().put("kind", "side_effect").put("medicineId", n.medicineId).put("medicineName", n.medicineName.trim()).put("effects", JSONArray(ordered))
            .put("other", n.other.trim()).put("severity", n.severity).put("happenedAt", n.happenedAt).put("stoppedTaking", n.stoppedTaking).put("reporter", n.reporter.trim())
            .put("discussedAt", n.discussedAt).put("discussedBy", n.discussedBy.trim()).toString()
    }
    fun decode(text: String): SideEffectNote? {
        if (!isStructured(text)) return null
        return runCatching {
            val j = JSONObject(text.removePrefix(PREFIX)); require(j.optString("kind") == "side_effect"); val list = j.getJSONArray("effects")
            SideEffectNote(j.getString("medicineId"), j.getString("medicineName"), (0 until list.length()).map { list.getString(it) }, j.optString("other"), j.getString("severity"), j.getLong("happenedAt"),
                j.optBoolean("stoppedTaking"), j.optString("reporter"), j.optLong("discussedAt"), j.optString("discussedBy")).also { require(SideEffectRules.error(it, Long.MAX_VALUE / 2) == null) }
        }.getOrNull()
    }
}

/** Pure list rules shared by the family screens and the doctor's view. */
object SideEffectLog {
    /** Every side-effect note of one person, newest first. */
    fun entries(items: List<CareItem>, personId: String): List<SideEffectEntry> = items.filter { it.personId == personId && it.kind == "journal" && SideEffectCodec.isStructured(it.detail) }
        .mapNotNull { item -> SideEffectCodec.decode(item.detail)?.let { SideEffectEntry(item, it) } }.sortedWith(compareByDescending<SideEffectEntry> { it.note.happenedAt }.thenBy { it.item.id })
    fun forMedicine(entries: List<SideEffectEntry>, medicineId: String) = entries.filter { it.note.medicineId == medicineId }
    /**
     * When the last doctor visit happened (0 when there is none yet): the latest completed visit (its completion time, else its date),
     * or a plain visit dated in the past. Cancelled, open and upcoming visits do not count.
     */
    fun lastVisitAt(items: List<CareItem>, personId: String, now: Long): Long = items.filter { it.personId == personId && it.kind == "visit" }.mapNotNull { v ->
        val entry = CareEntryCodec.decode(v.detail)
        when {
            entry != null -> if (entry.status == "completed") (entry.completedAt.takeIf { it in 1..now } ?: v.due.takeIf { it in 1..now }) else null
            CareEntryCodec.isStructured(v.detail) -> null
            else -> v.due.takeIf { it in 1..now }
        }
    }.maxOrNull() ?: 0L
    /** Reported after the last visit (everything when there has been no visit), newest first. */
    fun sinceVisit(entries: List<SideEffectEntry>, lastVisit: Long) = entries.filter { it.note.happenedAt > lastVisit }
    /** Since the last visit and not yet discussed. */
    fun toRaise(entries: List<SideEffectEntry>, lastVisit: Long) = sinceVisit(entries, lastVisit).filter { it.note.discussedAt == 0L }
    fun markDiscussed(entry: SideEffectEntry, by: String, now: Long): CareItem {
        val note = entry.note.copy(discussedAt = now, discussedBy = by.trim().take(100))
        return entry.item.copy(detail = SideEffectCodec.encode(note))
    }
}
