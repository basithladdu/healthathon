package `in`.wedevit.care

import java.util.Locale

/**
 * How often a medicine is taken, chosen from a short list (clinical team, 24 Nov 2026: never free text).
 * [english] is what is saved in "How often: " (or a "Frequency: " line), so MedicineDoses.perDay and the doctor read it as before:
 * "Once a day" reads as 1, "Twice a day" 2, "Three times a day" 3, "Four times a day" 4, and "As needed (SOS)" as as-needed.
 */
enum class MedicineFrequency(val english: String, val hindi: String, val perDay: Int?) {
    ONCE("Once a day", "दिन में एक बार", 1),
    TWICE("Twice a day", "दिन में दो बार", 2),
    THREE("Three times a day", "दिन में तीन बार", 3),
    FOUR("Four times a day", "दिन में चार बार", 4),
    AS_NEEDED("As needed (SOS)", "ज़रूरत पड़ने पर (SOS)", null),
}

object MedicineFrequencyRules {
    /** Dose times a choice starts with, from the usual times (08:00, 13:00, 18:00, 21:00). As needed has none. */
    fun defaultTimes(frequency: MedicineFrequency): List<String> {
        val (morning, afternoon, evening, night) = MedicineDoseRules.commonTimes
        return when (frequency) {
            MedicineFrequency.ONCE -> listOf(morning)
            MedicineFrequency.TWICE -> listOf(morning, night)
            MedicineFrequency.THREE -> listOf(morning, afternoon, night)
            MedicineFrequency.FOUR -> listOf(morning, afternoon, evening, night)
            MedicineFrequency.AS_NEEDED -> emptyList()
        }
    }

    /** The times after choosing [frequency]: times already chosen are kept when their number fits; otherwise the usual times. */
    fun timesFor(frequency: MedicineFrequency, current: List<String>): List<String> {
        val valid = runCatching { CareReminderRules.validTimes(current) }.getOrDefault(emptyList())
        return when {
            frequency == MedicineFrequency.AS_NEEDED -> emptyList()
            valid.size == frequency.perDay -> valid
            else -> defaultTimes(frequency)
        }
    }

    /**
     * The choice a saved "How often" text stands for: a label from the list, or older text with a clear count ("BD", "1-0-1",
     * "twice daily") or "SOS". Null for anything else (weekly, every 4 hours, blank), which is then kept as it was written.
     */
    fun fromText(text: String): MedicineFrequency? {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return null
        MedicineFrequency.entries.firstOrNull { it.english.equals(clean, ignoreCase = true) }?.let { return it }
        if (MedicineDoses.isAsNeeded(clean)) return MedicineFrequency.AS_NEEDED
        if (MedicineDoses.notDaily(clean)) return null
        return when (MedicineDoses.perDay(clean)) { 1 -> MedicineFrequency.ONCE; 2 -> MedicineFrequency.TWICE; 3 -> MedicineFrequency.THREE; 4 -> MedicineFrequency.FOUR; else -> null }
    }
}

/**
 * Strength as a number and a unit chip (24 Nov 2026): "500 mg", "5 ml", "2 puffs". Saved as one text, exactly as before, so older
 * phones and the doctor read it unchanged. A strength saved earlier that does not fit ("5 mg/5 ml") is kept as written.
 */
object MedicineStrength {
    val units = listOf("mg", "mcg", "ml", "g", "units", "drops", "puffs")
    data class Parts(val number: String, val unit: String)
    private val pattern = Regex("^(\\d{1,6}(?:[.,]\\d{1,3})?)\\s*([A-Za-zµ]+)?$")

    /** A unit word as one of [units] ("µg" is mcg, "IU" is units, "puff" is puffs), or null when it is not one of them. */
    fun unitOf(word: String): String? = when (word.trim().lowercase(Locale.ROOT)) {
        "mg" -> "mg"; "mcg", "µg", "ug" -> "mcg"; "ml" -> "ml"; "g", "gm", "gram", "grams" -> "g"
        "unit", "units", "iu" -> "units"; "drop", "drops" -> "drops"; "puff", "puffs" -> "puffs"
        else -> null
    }

    /** "500 mg" as ("500", "mg"); blank as ("", ""); null when the saved text is not a number with one listed unit. */
    fun parts(saved: String): Parts? {
        val text = saved.trim()
        if (text.isEmpty()) return Parts("", "")
        val match = pattern.matchEntire(text) ?: return null
        val number = match.groupValues[1].replace(',', '.')
        val word = match.groupValues[2]
        if (word.isEmpty()) return Parts(number, "")
        return unitOf(word)?.let { Parts(number, it) }
    }

    /** Only digits and one decimal point, at most 7 characters: what the number field keeps from a keypress. */
    fun cleanNumber(input: String): String {
        val digits = input.replace(',', '.').filter { it.isDigit() || it == '.' }
        val point = digits.indexOf('.')
        val one = if (point < 0) digits else digits.substring(0, point + 1) + digits.substring(point + 1).replace(".", "")
        return one.take(7)
    }

    /** The saved text: "500 mg". Blank when there is no number yet (a unit alone says nothing). */
    fun text(number: String, unit: String): String {
        val n = cleanNumber(number).trimEnd('.')
        return if (n.isEmpty()) "" else listOf(n, unit.trim()).filter(String::isNotEmpty).joinToString(" ")
    }
}

/**
 * Older medicines saved as free text (before the form) keep their text; the frequency and dose-time choices only add or replace
 * their own one line ("Frequency: Twice a day", "Times: 08:00, 21:00"), so nothing the person wrote is lost.
 */
object MedicineLegacyText {
    private const val FREQUENCY = "Frequency: "

    /** The choice a free-text medicine stands for: its "Frequency: " line first, else what its text says. */
    fun frequency(detail: String): MedicineFrequency? = detail.lineSequence().firstOrNull { it.startsWith(FREQUENCY) }?.removePrefix(FREQUENCY)?.let(MedicineFrequencyRules::fromText)
        ?: MedicineFrequencyRules.fromText(MedicineDoses.frequencyText(detail))

    /** Puts [frequency] in as one "Frequency: " line (null removes it), keeping every other line as it was. */
    fun withFrequency(detail: String, frequency: MedicineFrequency?): String {
        val kept = detail.lineSequence().filterNot { it.startsWith(FREQUENCY) }.joinToString("\n").trimEnd()
        if (frequency == null) return kept
        return (if (kept.isBlank()) "" else "$kept\n") + FREQUENCY + frequency.english
    }
}

/** The person's own words in an older free-text medicine, apart from the lines the choices write ("Frequency: ", "Times: "). */
fun MedicineLegacyText.words(detail: String): String = detail.lineSequence().filterNot { it.startsWith("Frequency: ") || it.startsWith("Times: ") }.joinToString("\n").trimEnd()

/** [words] in place of the old words, keeping the saved "Frequency: " and "Times: " lines after them. */
fun MedicineLegacyText.withWords(detail: String, words: String): String {
    val kept = detail.lineSequence().filter { it.startsWith("Frequency: ") || it.startsWith("Times: ") }.toList()
    return (listOf(words.trimEnd()).filter(String::isNotBlank) + kept).joinToString("\n")
}
