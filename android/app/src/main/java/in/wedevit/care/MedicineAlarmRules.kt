package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject

/**
 * Spoken alarms for key medicines (clinical team, "Oldies_illiterate_thoughts" point 10): a medicine marked "Important: ring like an
 * alarm" rings at its dose time with the alarm sound, speaks "Time for your night medicine, Morphine" and shows its strip photo
 * full screen with a big Taken tick and Snooze.
 *
 * The choice is saved with the medicine as one "Alarm: on" or "Alarm: off" line, so every phone that shares this care rings the
 * same way. Without a line the default decides: on for morphine and the other strong pain medicines (opioids) by name, off for
 * everything else. A line is written only when the choice differs from that default, so most medicines carry none.
 */
object MedicineAlarmRules {
    const val LINE = "Alarm: "
    /** Minutes a Snooze waits before ringing again. */
    const val SNOOZE_MINUTES = 10
    /** The ring stops by itself after this long (the alarm notification stays until Taken). */
    const val RING_FOR_MS = 5 * 60_000L
    /** The spoken words repeat this often while the alarm rings. */
    const val SPEAK_EVERY_MS = 20_000L

    /** Morphine and the other opioids a palliative team uses, by generic or common Indian brand name (and Hindi spellings of morphine). */
    private val opioid = Regex(
        "morph|morcontin|oramorph|sevredol|rapidus|fentanyl|durogesic|fentadur|tramadol|ultracet|contramal|tapentadol|tapal|tydol|" +
            "oxycodone|oxycontin|oxynorm|hydromorphone|methadone|buprenorphine|norphin|codeine|pethidine|meperidine|मॉर्फ|मार्फ|मॉर्फ़|ट्रामाडोल|फेंटानिल",
        RegexOption.IGNORE_CASE)

    /** True for morphine and other opioids by name: these ring like an alarm unless someone turned it off. */
    fun isOpioid(name: String): Boolean = opioid.containsMatchIn(name)

    /** The saved choice: true for "Alarm: on", false for "Alarm: off", null when the medicine has no line (the default decides). */
    fun saved(detail: String): Boolean? = detail.lineSequence().map(String::trim).firstOrNull { it.startsWith(LINE.trim()) }
        ?.removePrefix(LINE.trim())?.trim()?.lowercase()?.let { when (it) { "on" -> true; "off" -> false; else -> null } }

    /** True when this medicine rings like an alarm at its dose times. */
    fun isImportant(title: String, detail: String): Boolean = saved(detail) ?: isOpioid(title)
    fun isImportant(item: CareItem): Boolean = item.kind == "medicine" && isImportant(item.title, item.detail)

    /**
     * [detail] with the choice [on] for a medicine called [title]: the "Alarm: " line is removed when [on] matches the default,
     * and written ("Alarm: on" / "Alarm: off") when it differs. Every other line stays as it was.
     */
    fun withAlarm(detail: String, title: String, on: Boolean): String {
        val kept = withoutLine(detail)
        if (on == isOpioid(title)) return kept
        return (if (kept.isBlank()) "" else "$kept\n") + LINE + if (on) "on" else "off"
    }

    /** The value [MedicineEntry.alarm] keeps for a choice: null when it matches the default, so no line is written. */
    fun entryValue(title: String, on: Boolean): Boolean? = if (on == isOpioid(title)) null else on

    /** The sentence spoken at the dose time, in English: "Time for your night medicine, Morphine and Paracetamol." */
    fun spokenEnglish(slot: DoseSlot, names: List<String>): String = "${promptEnglish(slot)}, ${joinNames(names, "and")}."

    /** "Time for your morning medicine" and so on: the English key of the prompt for one part of the day. */
    fun promptEnglish(slot: DoseSlot): String = when (slot) {
        DoseSlot.MORNING -> "Time for your morning medicine"
        DoseSlot.AFTERNOON -> "Time for your afternoon medicine"
        DoseSlot.EVENING -> "Time for your evening medicine"
        DoseSlot.NIGHT -> "Time for your night medicine"
    }
    fun promptHindi(slot: DoseSlot): String = when (slot) {
        DoseSlot.MORNING -> "सुबह की दवा का समय हो गया है"
        DoseSlot.AFTERNOON -> "दोपहर की दवा का समय हो गया है"
        DoseSlot.EVENING -> "शाम की दवा का समय हो गया है"
        DoseSlot.NIGHT -> "रात की दवा का समय हो गया है"
    }

    /** Names read as a sentence: "A", "A and B", "A, B and C". Blank names are left out; the medicine names are never translated. */
    fun joinNames(names: List<String>, and: String): String {
        val clean = names.map(String::trim).filter(String::isNotEmpty).distinct()
        return when (clean.size) { 0 -> ""; 1 -> clean[0]; else -> clean.dropLast(1).joinToString(", ") + " $and " + clean.last() }
    }

    /** When a snoozed alarm rings again. */
    fun snoozeAt(now: Long, minutes: Int = SNOOZE_MINUTES): Long = now + minutes.coerceIn(1, 60) * 60_000L

    /** An alarm nobody answers snoozes itself this many times (like an alarm clock), then stays a quiet notification until Taken. */
    const val MAX_AUTO_SNOOZES = 3
    /** True when an unanswered alarm that has already snoozed itself [count] times should snooze again. */
    fun autoSnooze(count: Int): Boolean = count < MAX_AUTO_SNOOZES

    /** How long the ring still has to go when the alarm screen comes back (rotated, dark mode) [now], having started at [startedAt]. */
    fun ringLeft(startedAt: Long, now: Long): Long = (RING_FOR_MS - (now - startedAt).coerceAtLeast(0)).coerceAtLeast(0)

    /** After a restart a pending snooze is set again at its own time, never sooner than a minute from [now] ([due] 0 when unknown). */
    fun restoreAt(due: Long, now: Long): Long = maxOf(due, now + 60_000L)

    /** The first of [candidates] (blanks and repeats skipped) for which [works] is true: the alarm tone falls through to the next one. */
    fun <T : Any> firstWorking(candidates: List<T?>, works: (T) -> Boolean): T? = candidates.filterNotNull().distinct().firstOrNull(works)

    /** [detail] without the "Alarm: " line, for any place that shows a medicine's saved text as it is. */
    fun withoutLine(detail: String): String = detail.lineSequence().filterNot { it.trim().startsWith(LINE.trim()) }.joinToString("\n").trimEnd()

    /** One alarm: the medicines due together at [at] ("HH:mm" [time]) for one person, kept in the alert's extras. */
    data class Alarm(val account: String, val personId: String, val at: Long, val time: String, val medicines: List<Pair<String, String>>, val preview: Boolean = false) {
        val slot: DoseSlot get() = MedicineDoseRules.slot(time)
        val key: String get() = "alarm:$personId:$at"
        val itemIds: List<String> get() = medicines.map { it.first }
        val names: List<String> get() = medicines.map { it.second }
    }

    fun encode(alarm: Alarm): String = JSONObject().put("account", alarm.account).put("person", alarm.personId).put("at", alarm.at).put("time", alarm.time).put("preview", alarm.preview)
        .put("medicines", JSONArray().also { array -> alarm.medicines.forEach { (id, name) -> array.put(JSONObject().put("id", id).put("name", name)) } }).toString()

    fun decode(text: String?): Alarm? = if (text.isNullOrBlank()) null else runCatching {
        val j = JSONObject(text)
        val list = j.getJSONArray("medicines").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { m -> m.getString("id") to m.optString("name") } } }
        val time = j.getString("time")
        require(list.isNotEmpty() && time.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")))
        Alarm(j.optString("account"), j.getString("person"), j.getLong("at"), time, list, j.optBoolean("preview"))
    }.getOrNull()

    /**
     * What the alarm shows for one dose group: every medicine due at that time and not already marked taken, the important ones first,
     * but only when at least one important medicine is among them. Empty means the ordinary reminder is enough. [important] and
     * [taken] are item ids.
     */
    fun ringing(group: DoseGroup, important: Set<String>, taken: Set<String>): List<DoseMedicine> {
        val due = group.medicines.filter { it.itemId !in taken }
        if (due.none { it.itemId in important }) return emptyList()
        return due.sortedBy { if (it.itemId in important) 0 else 1 }
    }
}
