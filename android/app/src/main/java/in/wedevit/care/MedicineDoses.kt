package `in`.wedevit.care

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * One dose of a medicine on one day (3 Nov 2026 meeting: "four times a day" must give four ticks, not one).
 * [time] is the saved dose time ("08:00"), or blank when the medicine has a count per day but no times.
 * [key] is stable for the day: "t:08:00" for a timed dose, "n:2" for the second untimed dose.
 */
data class MedicineDose(val key: String, val index: Int, val count: Int, val time: String) {
    val timed: Boolean get() = time.isNotBlank()
}

enum class MedicineDoseState { PENDING, TAKEN, MISSED }

/** What to save for one dose tap: the reports to append, then the whole-day check-off ([doneOn]) the medicine should end with. */
data class MedicineDosePlan(val events: List<MedicineIntakeEvent>, val doneOn: String)

/**
 * Splits a medicine into its doses for a day and reads each dose's state from the intake reports.
 *
 * Nothing is invented: dose times come only from the times saved with the medicine (or chosen in Reminders on this
 * phone); without times, the number of doses comes only from an explicit count in "How often" ("twice a day", "TDS",
 * "1-0-1"). "As needed", "every 4 hours" and anything unclear stay one tick a day, as before.
 *
 * Storage stays as it was, so old medicines load unchanged:
 *  - each dose tap is an ordinary intake report (taken or missed). A timed dose carries its planned time; an untimed
 *    dose is named in the note ("Dose 2 of 4 ...");
 *  - [CareItem.doneOn] keeps its old meaning, "the whole medicine is ticked for this day", and is set only when every
 *    dose is taken. Old single ticks (doneOn = today, no dose reports) therefore show every dose as taken.
 */
object MedicineDoses {
    const val MAX_PER_DAY = 6
    private val untimedNote = Regex("^Dose (\\d) of (\\d)\\b")
    private val asNeeded = Regex("(?<![A-Za-z])(?:as needed|when needed|if needed|only when|when required|as required|sos|prn|stat)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val notDaily = Regex("(?<![A-Za-z])(?:week|weekly|month|monthly|alternate day|alternate days|every other day)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val pattern = Regex("(?<![\\d.\\-/])([0-3½])\\s*-\\s*([0-3½])\\s*-\\s*([0-3½])(?:\\s*-\\s*([0-3½]))?(?![\\d.\\-/])")
    private val numberWords = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6)
    private val timesADay = Regex("(?<![A-Za-z0-9])(\\d|one|two|three|four|five|six)\\s*(?:times|x)\\s*(?:a|per|each|every|in a)?\\s*(?:day|daily)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val abbreviations = listOf(
        Regex("(?<![A-Za-z])(?:qid|qds)(?![A-Za-z])", RegexOption.IGNORE_CASE) to 4,
        Regex("(?<![A-Za-z])(?:tds|tid|thrice)(?![A-Za-z])", RegexOption.IGNORE_CASE) to 3,
        Regex("(?<![A-Za-z])(?:bd|bid|twice)(?![A-Za-z])", RegexOption.IGNORE_CASE) to 2,
        Regex("(?<![A-Za-z])(?:od|qd|once|daily|each morning|every morning|each night|every night|at night|at bedtime|each evening|every evening)(?![A-Za-z])", RegexOption.IGNORE_CASE) to 1,
    )

    /** Doses a day written in [text], or null when it says "as needed", is not daily, or gives no clear count. Never more than [MAX_PER_DAY]. */
    fun perDay(text: String): Int? {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty() || asNeeded.containsMatchIn(clean) || notDaily.containsMatchIn(clean)) return null
        pattern.find(clean)?.let { match ->
            val count = match.groupValues.drop(1).count { it.isNotEmpty() && it != "0" }
            return count.takeIf { it in 1..MAX_PER_DAY }
        }
        timesADay.find(clean)?.let { match ->
            val word = match.groupValues[1].lowercase()
            return (word.toIntOrNull() ?: numberWords[word])?.takeIf { it in 1..MAX_PER_DAY }
        }
        return abbreviations.firstOrNull { (regex, _) -> regex.containsMatchIn(clean) }?.second
    }

    /** Doses from saved times (each time is one dose) or, without times, from a count per day (default one). */
    fun doses(times: List<String>, perDay: Int?): List<MedicineDose> {
        val valid = runCatching { CareReminderRules.validTimes(times) }.getOrDefault(emptyList())
        if (valid.isNotEmpty()) return valid.mapIndexed { i, time -> MedicineDose("t:$time", i + 1, valid.size, time) }
        val count = (perDay ?: 1).coerceIn(1, MAX_PER_DAY)
        return (1..count).map { MedicineDose("n:$it", it, count, "") }
    }

    /** The page list a prescription scan adds ("Page 1: <id> · SHA-256 <hex>"): file names and hashes, never dose text. */
    private val sourceReference = Regex("^Page \\d+: [^ ]+(?: · SHA-256 [0-9a-f]*)?$")

    /**
     * The "How often" text of a medicine: the form's field, or the free text without its "Times:" line and without the
     * scan's file references (a hash such as "…3bd9…" must not read as "BD", twice a day).
     */
    fun frequencyText(detail: String): String = MedicineEntryText.parse(detail)?.howOften
        ?: detail.lineSequence().filterNot { it.startsWith("Times: ") || it.startsWith("Original prescription references:") || sourceReference.matches(it.trim()) }.joinToString(" ")

    /** "Weekly", "twice a week", "every 4 weeks", "every 3 days", "alternate days": a schedule that is not every day. */
    private val weeklyOrLonger = Regex("(?<![A-Za-z])(?:weekly|monthly|(?:once|twice|thrice|\\d+ times?) (?:a|per|each|every) (?:week|month)|every \\d+ (?:days|weeks|months)|every other day|alternate days?)(?![A-Za-z])", RegexOption.IGNORE_CASE)
    private val everyDay = Regex("(?<![A-Za-z])(?:daily|(?:a|per|each|every) day|od|bd|bid|tds|tid|qid|qds)(?![A-Za-z])", RegexOption.IGNORE_CASE)

    /**
     * True when the text gives a schedule that is not every day and no daily count ("once weekly", "every 4 weeks").
     * Such a medicine gets no daily tick and no daily reminder; its directions say when it is due.
     * A course length ("twice a day for 1 week", "BD for a week") stays daily.
     */
    fun notDaily(text: String): Boolean {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return weeklyOrLonger.containsMatchIn(clean) && !everyDay.containsMatchIn(clean) && pattern.find(clean) == null
    }

    /** True for a medicine whose own "How often" (the form's field, a scan's "Frequency:" line, or else its text) is [notDaily]. */
    fun notDaily(item: CareItem): Boolean = item.kind == "medicine" && notDaily(
        detailFrequency(item.detail) ?: frequencyText(item.detail))

    private fun detailFrequency(detail: String): String? = if (MedicineEntryText.parse(detail) != null) null
        else detail.lineSequence().firstOrNull { it.startsWith("Frequency: ") }?.removePrefix("Frequency: ")

    /** A medicine's doses: its saved times win; [reminderTimes] (this phone's Reminders) are used only when none are saved. None when it is [notDaily]. */
    fun forItem(item: CareItem, reminderTimes: List<String>? = null): List<MedicineDose> {
        if (item.kind != "medicine" || notDaily(item)) return emptyList()
        val saved = CareReminderRules.printedTimes(item.detail)
        val times = saved.ifEmpty { reminderTimes?.let { runCatching { CareReminderRules.validTimes(it) }.getOrNull() }.orEmpty() }
        return doses(times, perDay(frequencyText(item.detail)))
    }

    private fun localDay(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    private fun clock(at: Long, zone: ZoneId): String = Instant.ofEpochMilli(at).atZone(zone).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

    /** True when [event] is a report for this exact dose on [day]. */
    fun belongsTo(event: MedicineIntakeEvent, dose: MedicineDose, day: LocalDate, zone: ZoneId): Boolean = when {
        dose.timed -> event.plannedAt != null && localDay(event.plannedAt, zone) == day && clock(event.plannedAt, zone) == dose.time
        else -> event.plannedAt == null && untimedNote.find(event.note)?.groupValues?.get(1)?.toIntOrNull() == dose.index && localDay(event.loggedAt, zone) == day
    }

    /** An old-style report with no dose in it (daily check-off, "Missed today"), for [day]. */
    private fun wholeDay(event: MedicineIntakeEvent, day: LocalDate, zone: ZoneId): Boolean = event.plannedAt == null && untimedNote.find(event.note) == null &&
        (event.reportedDate == day.toString() || (event.reportedDate.isBlank() && localDay(event.loggedAt, zone) == day))

    private val reportOutcomes = setOf("taken", "skipped", "unmarked")
    private fun stateOf(outcome: String): MedicineDoseState = when (outcome) { "taken" -> MedicineDoseState.TAKEN; "skipped" -> MedicineDoseState.MISSED; else -> MedicineDoseState.PENDING }

    /**
     * One dose's state on [day]. The newest report for the dose wins. Without one, the whole-day tick counts as taken.
     * A medicine with a single untimed dose also reads the old whole-day reports, so its history before this change still shows.
     */
    fun state(item: CareItem, dose: MedicineDose, events: List<MedicineIntakeEvent>, day: LocalDate, zone: ZoneId): MedicineDoseState {
        val mine = events.filter { it.medicineId == item.id && it.outcome in reportOutcomes }
        mine.filter { belongsTo(it, dose, day, zone) }.maxByOrNull { it.loggedAt }?.let { return stateOf(it.outcome) }
        if (item.doneOn == day.toString()) return MedicineDoseState.TAKEN
        if (!dose.timed && dose.count == 1) mine.filter { wholeDay(it, day, zone) }.maxByOrNull { it.loggedAt }?.let { return stateOf(it.outcome) }
        return MedicineDoseState.PENDING
    }

    /** True when the report that sets this dose's state today was written by family ("Family report"). */
    fun loggedByFamily(item: CareItem, dose: MedicineDose, events: List<MedicineIntakeEvent>, day: LocalDate, zone: ZoneId): Boolean {
        val mine = events.filter { it.medicineId == item.id && it.outcome in reportOutcomes }
        val newest = mine.filter { belongsTo(it, dose, day, zone) }.maxByOrNull { it.loggedAt }
            ?: if (!dose.timed && dose.count == 1 || item.doneOn == day.toString()) mine.filter { wholeDay(it, day, zone) }.maxByOrNull { it.loggedAt } else null
        return newest != null && newest.outcome != "unmarked" && MedicineHistoryRules.loggedByFamily(newest)
    }

    fun states(item: CareItem, doses: List<MedicineDose>, events: List<MedicineIntakeEvent>, day: LocalDate, zone: ZoneId): Map<String, MedicineDoseState> =
        doses.associate { it.key to state(item, it, events, day, zone) }

    fun takenCount(states: Map<String, MedicineDoseState>): Int = states.values.count { it == MedicineDoseState.TAKEN }
    fun allTaken(states: Map<String, MedicineDoseState>): Boolean = states.isNotEmpty() && states.values.all { it == MedicineDoseState.TAKEN }

    /** The planned moment of a timed dose on [day], from its saved time. Null for an untimed dose. */
    fun plannedAt(dose: MedicineDose, day: LocalDate, zone: ZoneId): Long? =
        if (!dose.timed) null else day.atTime(LocalTime.parse(dose.time)).atZone(zone).toInstant().toEpochMilli()

    /**
     * True when marking [dose] at [now] may mean last night's dose, given late after midnight: an evening-side night dose
     * (DoseSlot.NIGHT, 12:00 or later) marked in the after-midnight part of the night slot, before the dose's time today.
     * The person is then asked which night it was, instead of the tick silently covering tonight's dose.
     */
    fun mayBeLastNight(dose: MedicineDose, now: LocalTime): Boolean {
        if (!dose.timed) return false
        val time = LocalTime.parse(dose.time)
        return MedicineDoseRules.slot(dose.time) == DoseSlot.NIGHT && time.hour >= 12 &&
            MedicineDoseRules.slot("%02d:%02d".format(now.hour, now.minute)) == DoseSlot.NIGHT && now.hour < 12 && now < time
    }

    /** [mayBeLastNight], and last night's dose is not already ticked taken: then ask which night it was. */
    fun askWhichNight(item: CareItem, dose: MedicineDose, events: List<MedicineIntakeEvent>, now: ZonedDateTime): Boolean =
        mayBeLastNight(dose, now.toLocalTime()) && state(item, dose, events, now.toLocalDate().minusDays(1), now.zone) != MedicineDoseState.TAKEN

    fun label(dose: MedicineDose): String = if (dose.timed) "${dose.time} dose" else "Dose ${dose.index} of ${dose.count}"

    /** One report for one dose. No taken time is recorded: a tick says the dose was given, not exactly when. */
    fun report(item: CareItem, dose: MedicineDose, taken: Boolean, day: LocalDate, zone: ZoneId, now: Long, reporter: String, note: String? = null, id: String = UUID.randomUUID().toString()) =
        MedicineIntakeEvent(id, item.id, item.title, if (taken) "taken" else "skipped", plannedAt = plannedAt(dose, day, zone), loggedAt = now, reporter = reporter,
            note = note ?: (label(dose) + if (taken) " ticked. Exact taken time was not recorded." else " reported missed from the daily list."), source = "manual")

    /**
     * What to save when [dose] is marked [taken] or missed on [day]. When the medicine was ticked for the whole day and a dose
     * is now missed, the other doses that only the whole-day tick covered are first written as taken, so they keep showing.
     */
    fun plan(item: CareItem, doses: List<MedicineDose>, events: List<MedicineIntakeEvent>, dose: MedicineDose, taken: Boolean, day: LocalDate, zone: ZoneId, now: Long, reporter: String,
             makeId: () -> String = { UUID.randomUUID().toString() }): MedicineDosePlan {
        require(item.kind == "medicine" && doses.any { it.key == dose.key }) { "This dose could not be found. Refresh and try again." }
        val before = states(item, doses, events, day, zone)
        val after = before + (dose.key to if (taken) MedicineDoseState.TAKEN else MedicineDoseState.MISSED)
        val keepTicked = if (item.doneOn == day.toString() && !MedicineDoses.allTaken(after)) doses.filter { other ->
            other.key != dose.key && before[other.key] == MedicineDoseState.TAKEN && events.none { it.medicineId == item.id && it.outcome in reportOutcomes && belongsTo(it, other, day, zone) }
        }.map { report(item, it, true, day, zone, now - 1, reporter, label(it) + " was covered by the earlier whole-day tick.", makeId()) } else emptyList()
        val target = if (before[dose.key] == after[dose.key] && events.any { it.medicineId == item.id && belongsTo(it, dose, day, zone) }) emptyList() else listOf(report(item, dose, taken, day, zone, now, reporter, id = makeId()))
        val doneOn = if (allTaken(after)) day.toString() else if (item.doneOn == day.toString()) "" else item.doneOn
        return MedicineDosePlan(keepTicked + target, doneOn)
    }

    /** Old daily check-off reports on a day that already has dose reports repeat what the doses say; the history hides them. */
    fun redundantCheckoff(event: MedicineIntakeEvent, all: List<MedicineIntakeEvent>, zone: ZoneId): Boolean {
        if (event.source != "daily-checkoff") return false
        val day = event.reportedDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: localDay(event.loggedAt, zone)
        return all.any { other -> other.medicineId == event.medicineId && other.source != "daily-checkoff" && other.outcome in setOf("taken", "skipped") &&
            (other.plannedAt?.let { localDay(it, zone) == day } ?: (untimedNote.containsMatchIn(other.note) && localDay(other.loggedAt, zone) == day)) }
    }
}

/** Who tapped a dose, from the signed-in role (same labels as the "Missed today" report). */
fun MedicineDoses.reporter(role: String): String = when (role) { "Patient" -> "Patient report"; "Family" -> "Family report"; else -> "Not recorded" }

/**
 * Short text for a medicine card (3 Nov meeting: cards were too long, but the doctors liked seeing what the medicine does).
 * Only the person's own words are shown; nothing is added.
 */
object MedicineCardText {
    private val sentence = Regex("""(?<=[.!?])\s+|\n""")
    private val purposeStart = Regex("""^(?:for|to|helps?|used for|eases?|relieves?)\b""", RegexOption.IGNORE_CASE)
    private val purposeInside = Regex("""\bto (?:ease|help|treat|prevent|reduce|control|relieve|stop)\b""", RegexOption.IGNORE_CASE)

    private fun sentences(detail: String): List<String> = detail.lineSequence().filterNot { it.startsWith("Times: ") }.joinToString("\n")
        .split(sentence).map(String::trim).filter(String::isNotEmpty)

    /** "What it does" from free text: the first sentence that says what the medicine is for ("For nausea."). Blank when none. */
    fun purpose(detail: String): String {
        if (MedicineEntryText.parse(detail) != null) return MedicineEntryText.parse(detail)?.directions.orEmpty()
        val all = sentences(detail)
        return (all.firstOrNull { purposeStart.containsMatchIn(it) && !it.matches(Regex("""(?i)^for \d+ days?\.?$""")) } ?: all.firstOrNull { purposeInside.containsMatchIn(it) }).orEmpty()
    }

    /** The rest of a free-text detail, shown only when the card is opened. Blank for a form-entered medicine. */
    fun rest(detail: String): String {
        if (MedicineEntryText.parse(detail) != null) return ""
        val purpose = purpose(detail)
        return sentences(detail).filter { it != purpose }.joinToString(" ")
    }
}
