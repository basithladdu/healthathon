package `in`.wedevit.care

import java.util.Locale

/**
 * Team decision 8 (28 Oct 2026): a photo of a strip, box or prescription may fill ONLY the medicine name,
 * strength and form. How much each time, how often, dose times, food timing, directions and course length are
 * always typed by a person, because handwriting and cut strips cause reading errors.
 */
object MedicinePhotoFields {
    enum class Field { NAME, STRENGTH, FORM, HOW_MUCH, HOW_OFTEN, TIMES, FOOD, DIRECTIONS, DURATION }
    val fromPhoto: Set<Field> = setOf(Field.NAME, Field.STRENGTH, Field.FORM)
    fun mayFill(field: Field): Boolean = field in fromPhoto

    /** Clears every field a photo may not fill. The copied source line stays only as a read-only quote. */
    fun limit(medicine: OrganizedMedicine): OrganizedMedicine = medicine.copy(
        quantity = "", frequency = "", suggestedTimes = emptyList(), mealTiming = MedicineMealTiming.NOT_STATED,
        instructions = "", durationDays = null,
    )

    /** English form names as saved, with the Hindi shown beside them. */
    val forms: List<Pair<String, String>> = listOf(
        "Tablet" to "गोली", "Capsule" to "कैप्सूल", "Syrup" to "सिरप", "Injection" to "इंजेक्शन", "Patch" to "पैच",
        "Drops" to "ड्रॉप्स", "Oral solution" to "पीने का घोल", "Ointment" to "मरहम", "Inhaler" to "इनहेलर",
    )
    private val formWords = listOf(
        "Tablet" to "tab(?:let)?s?", "Capsule" to "caps?(?:ules?)?", "Syrup" to "syrup|syp", "Injection" to "inj(?:ection)?s?",
        "Patch" to "patch(?:es)?", "Drops" to "drops?", "Oral solution" to "oral solution|solution", "Ointment" to "ointment|oint|cream|gel", "Inhaler" to "inhaler",
    ).map { (form, words) -> form to Regex("(?<![A-Za-z])(?:$words)(?![A-Za-z])", RegexOption.IGNORE_CASE) }
    private val strength = Regex("(?<![A-Za-z0-9.])\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|µg|g|ml|iu|units?|%)(?:\\s*/\\s*\\d*(?:\\.\\d+)?\\s*(?:ml|g))?(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val pharmacopoeia = Regex("(?<![A-Za-z])(?:I\\.?P\\.?|B\\.?P\\.?|U\\.?S\\.?P\\.?)(?![A-Za-z])")
    private val notAName = Regex("(?<![A-Za-z])(?:mfg|mfd|manufactured|marketed|batch|b\\.? ?no|exp|expiry|mrp|rs|lic|licen[cs]e|store|schedule|warning|composition|each|contains|dosage|keep|reach|children|protect|light|price|incl|taxes|pharma|ltd|limited|pvt|india|prescription|rx|dr|doctor)(?![A-Za-z])", RegexOption.IGNORE_CASE)

    fun formIn(text: String): String = formWords.firstOrNull { (_, pattern) -> pattern.containsMatchIn(text) }?.first.orEmpty()
    fun strengthIn(text: String): String = strength.find(text)?.value?.replace(Regex("\\s+"), " ")?.let { value ->
        Regex("(\\d)([A-Za-zµ%])").replace(value, "$1 $2").replace(Regex("\\s*/\\s*"), "/")
    }.orEmpty()

    /** The medicine name on one line: numbering, form words, strength and anything after it are removed. Blank when no name is left. */
    fun nameIn(line: String): String {
        var text = line.trim().replace(Regex("^\\s*\\d+\\s*[.)-]\\s*"), "")
        strength.find(text)?.let { text = text.substring(0, it.range.first) }
        Regex("\\d").find(text)?.let { text = text.substring(0, it.range.first) }
        formWords.forEach { (_, pattern) -> text = pattern.replace(text, " ") }
        text = pharmacopoeia.replace(text, " ").replace(Regex("[^\\p{L}\\p{M}\\s+-]"), " ").replace(Regex("\\s+"), " ").trim(' ', '-', '+')
        return text.takeIf { it.count(Char::isLetter) >= 2 }?.take(120).orEmpty()
    }

    data class Reading(val name: String, val strength: String, val form: String)

    /** Reads a strip or box photo's text. A cut strip often has no name side: then the name stays blank and the person types or picks it. */
    fun fromStripText(text: String): Reading {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val form = lines.firstNotNullOfOrNull { formIn(it).ifBlank { null } }.orEmpty()
        val dose = lines.firstNotNullOfOrNull { strengthIn(it).ifBlank { null } }.orEmpty()
        val labelled = lines.filter { (strengthIn(it).isNotBlank() || formIn(it).isNotBlank()) && !notAName.containsMatchIn(it) }
            .firstNotNullOfOrNull { nameIn(it).ifBlank { null } }
        val alone = lines.firstOrNull { line -> line.length in 3..40 && !notAName.containsMatchIn(line) && line.none(Char::isDigit) &&
            line.split(Regex("\\s+")).size <= 3 && line.count(Char::isLetter) >= 3 && formIn(line).isBlank() }
        return Reading((labelled ?: alone?.let(::nameIn)).orEmpty(), dose, form)
    }

    /** A photo only fills blanks: it never overwrites what the person typed, and never touches the hand-entered fields. */
    fun fillBlanks(medicine: OrganizedMedicine, reading: Reading): OrganizedMedicine = medicine.copy(
        medicineName = medicine.medicineName.ifBlank { reading.name },
        strength = medicine.strength.ifBlank { reading.strength },
        form = medicine.form.ifBlank { reading.form },
    )
}

/** A hand-entered medicine, saved as plain labelled lines so family phones and the doctor read the same text. */
data class MedicineEntry(
    val strength: String = "", val form: String = "", val howMuch: String = "", val howOften: String = "",
    val times: List<String> = emptyList(), val food: MedicineMealTiming = MedicineMealTiming.NOT_STATED,
    val days: Int? = null, val directions: String = "",
)

object MedicineEntryText {
    private const val STRENGTH = "Strength: "; private const val FORM = "Form: "; private const val HOW_MUCH = "How much each time: "
    private const val HOW_OFTEN = "How often: "; private const val TIMES = "Times: "; private const val FOOD = "Food: "
    private const val COURSE = "Course: "; private const val DIRECTIONS = "Directions: "
    private val foods = mapOf(MedicineMealTiming.BEFORE_FOOD to "before food", MedicineMealTiming.WITH_FOOD to "with food", MedicineMealTiming.AFTER_FOOD to "after food")

    fun detail(entry: MedicineEntry): String = buildList {
        fun line(label: String, value: String) { value.replace(Regex("\\s+"), " ").trim().takeIf(String::isNotEmpty)?.let { add(label + it) } }
        line(STRENGTH, entry.strength); line(FORM, entry.form); line(HOW_MUCH, entry.howMuch); line(HOW_OFTEN, entry.howOften)
        runCatching { CareReminderRules.validTimes(entry.times) }.getOrDefault(emptyList()).takeIf { it.isNotEmpty() }?.let { add(TIMES + it.joinToString(", ")) }
        foods[entry.food]?.let { add(FOOD + it) }
        entry.days?.takeIf { it in 1..365 }?.let { add(COURSE + "$it ${if (it == 1) "day" else "days"}") }
        line(DIRECTIONS, entry.directions)
    }.joinToString("\n")

    /** Reads back only text this form wrote; any other line (free text, a scanned source quote) returns null so it is edited as text. */
    fun parse(detail: String): MedicineEntry? {
        var entry = MedicineEntry()
        detail.lineSequence().filter(String::isNotBlank).forEach { line ->
            entry = when {
                line.startsWith(STRENGTH) -> entry.copy(strength = line.removePrefix(STRENGTH))
                line.startsWith(FORM) -> entry.copy(form = line.removePrefix(FORM))
                line.startsWith(HOW_MUCH) -> entry.copy(howMuch = line.removePrefix(HOW_MUCH))
                line.startsWith(HOW_OFTEN) -> entry.copy(howOften = line.removePrefix(HOW_OFTEN))
                line.startsWith(TIMES) -> entry.copy(times = CareReminderRules.printedTimes(line).ifEmpty { return null })
                line.startsWith(FOOD) -> entry.copy(food = foods.entries.firstOrNull { it.value == line.removePrefix(FOOD) }?.key ?: return null)
                line.startsWith(COURSE) -> entry.copy(days = Regex("^(\\d{1,3}) days?$").find(line.removePrefix(COURSE))?.groupValues?.get(1)?.toInt() ?: return null)
                line.startsWith(DIRECTIONS) -> entry.copy(directions = line.removePrefix(DIRECTIONS))
                else -> return null
            }
        }
        return entry
    }

    /** Puts chosen dose times into a saved medicine's text (one "Times:" line), so every phone sharing this care reads the same times. */
    fun withTimes(detail: String, times: List<String>): String {
        val valid = CareReminderRules.validTimes(times)
        val kept = detail.lineSequence().filterNot { it.startsWith(TIMES) }.joinToString("\n").trimEnd()
        return if (valid.isEmpty()) kept else (if (kept.isBlank()) "" else "$kept\n") + TIMES + valid.joinToString(", ")
    }
}

enum class DoseSlot(val english: String, val hindi: String) { MORNING("Morning", "सुबह"), AFTERNOON("Afternoon", "दोपहर"), EVENING("Evening", "शाम"), NIGHT("Night", "रात") }

data class DoseMedicine(val itemId: String, val personId: String, val name: String, val times: List<String>)
data class DoseGroup(val personId: String, val time: String, val slot: DoseSlot, val medicines: List<DoseMedicine>)
data class DoseReminder(val title: String, val text: String, val publicTitle: String, val publicText: String, val itemIds: List<String>)

/** Groups medicines by dose time ("Morning · 08:00 · 3 medicines") for the Today card and the reminder. */
object MedicineDoseRules {
    /** Quick choices for typing dose times by hand; the person can still type any other time. */
    val commonTimes = listOf("08:00", "13:00", "18:00", "21:00")

    fun slot(time: String): DoseSlot {
        val hour = time.substringBefore(':').toIntOrNull() ?: return DoseSlot.NIGHT
        return when (hour) { in 4..11 -> DoseSlot.MORNING; in 12..16 -> DoseSlot.AFTERNOON; in 17..20 -> DoseSlot.EVENING; else -> DoseSlot.NIGHT }
    }

    /** A medicine's dose times on this phone: times chosen in Reminders win; otherwise the times saved with the medicine. A paused reminder has none. */
    fun times(item: CareItem, option: ReminderOption?): List<String> = when {
        item.kind != "medicine" || option?.muted == true -> emptyList()
        option != null -> runCatching { CareReminderRules.validTimes(option.times) }.getOrDefault(emptyList())
        else -> CareReminderRules.printedTimes(item.detail)
    }

    fun groups(medicines: List<DoseMedicine>): List<DoseGroup> = medicines
        .flatMap { medicine -> medicine.times.distinct().map { time -> (medicine.personId to time) to medicine } }
        .groupBy({ it.first }, { it.second })
        .map { (key, list) -> DoseGroup(key.first, key.second, slot(key.second), list.distinctBy { it.itemId }.sortedBy { it.name.lowercase(Locale.ROOT) }) }
        .sortedWith(compareBy({ it.time }, { it.personId }))

    fun title(group: DoseGroup, hindi: Boolean): String {
        val count = group.medicines.size
        return if (hindi) "${group.slot.hindi} · ${group.time} · $count ${if (count == 1) "दवा" else "दवाएँ"}"
        else "${group.slot.english} · ${group.time} · $count ${if (count == 1) "medicine" else "medicines"}"
    }

    /** Names and strip photos appear only on an unlocked phone; the lock screen gets the generic public text. */
    fun reminder(group: DoseGroup, hindi: Boolean): DoseReminder = DoseReminder(
        title = title(group, hindi),
        text = group.medicines.joinToString(", ") { it.name } + if (hindi) "\nपत्ते को फ़ोटो से मिलाकर दें।" else "\nMatch each strip with its photo.",
        publicTitle = if (hindi) "दवा का समय" else "Medicine time",
        publicText = if (hindi) "देखने के लिए सहारा खोलें।" else "Open Sahara to see which medicines.",
        itemIds = group.medicines.map { it.itemId },
    )
}

/** One scheduled reminder slot as the scheduler saved it. */
data class DoseOccurrence(val itemId: String, val at: Long, val time: String)

/** Every medicine due at the same moment as the alert that fired, one group per person, so one reminder lists them all. */
fun MedicineDoseRules.dueTogether(occurrences: List<DoseOccurrence>, medicines: List<DoseMedicine>, at: Long): List<DoseGroup> {
    val byId = medicines.associateBy { it.itemId }
    return groups(occurrences.filter { it.at == at && it.time.isNotBlank() }.mapNotNull { occurrence -> byId[occurrence.itemId]?.copy(times = listOf(occurrence.time)) })
}
