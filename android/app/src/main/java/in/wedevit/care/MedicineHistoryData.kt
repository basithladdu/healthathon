package `in`.wedevit.care

import java.time.Instant
import java.time.LocalDate

data class MedicineCourse(val medicineId: String, val name: String, val directions: String, val past: Boolean, val changedAt: Long, val reason: String = "", val revision: Long = 0)
data class MedicineIntakeEvent(val id: String, val medicineId: String, val name: String, val outcome: String,
    val plannedAt: Long? = null, val reportedTakenAt: Long? = null, val reportedDate: String = "",
    val loggedAt: Long, val reporter: String = "", val note: String = "", val source: String = "manual")
data class MedicineHistorySnapshot(val courses: List<MedicineCourse> = emptyList(), val events: List<MedicineIntakeEvent> = emptyList()) {
    val pastIds: Set<String> get() = courses.filter { it.past }.map { it.medicineId }.toSet()
}

object MedicineHistoryRules {
    val outcomes = setOf("taken", "skipped", "planned", "unmarked", "course_past", "course_current")
    fun validate(event: MedicineIntakeEvent, now: Long) {
        require(event.id.isNotBlank() && event.medicineId.isNotBlank() && event.name.trim().length in 1..200)
        require(event.outcome in outcomes && event.loggedAt > 0 && event.note.length <= 2000 && event.reporter.length <= 100)
        require(event.plannedAt == null || event.plannedAt > 0) { "Choose a valid planned time." }
        require(event.reportedTakenAt == null || (event.outcome == "taken" && event.reportedTakenAt > 0 && event.reportedTakenAt <= now)) { "A taken time must be in the past and belong to a Taken report." }
        require(event.outcome != "planned" || (event.plannedAt != null && event.reportedTakenAt == null)) { "A planned dose needs its scheduled time." }
        require(event.reportedDate.isEmpty() || runCatching { LocalDate.parse(event.reportedDate) }.isSuccess) { "Invalid reported date." }
    }
    fun status(event: MedicineIntakeEvent, now: Long): String = when(event.outcome) {
        // Late only from an hour after the planned time (MedicineLateRule, 24 Nov 2026).
        "taken" -> if (MedicineLateRule.isLate(event.plannedAt, event.reportedTakenAt)) "Late reported" else "Taken reported"
        // Not taken and Skipped share the stored "skipped" outcome; only a skip on purpose says Skipped (MedicineDoseStatus).
        "skipped" -> if (MedicineDoseStatus.deliberateSkip(event)) "Skipped reported" else "Not taken reported"
        "unmarked" -> "Check-off corrected"
        "course_past" -> "Moved to past medicines"
        "course_current" -> "Returned to current medicines"
        else -> if (event.plannedAt != null && event.plannedAt > now) "Upcoming" else "Not logged"
    }
    fun current(items: List<CareItem>, personId: String, snapshot: MedicineHistorySnapshot) = items.filter { it.personId == personId && it.kind == "medicine" && it.id !in snapshot.pastIds }
    fun status(event: MedicineIntakeEvent, all: List<MedicineIntakeEvent>, now: Long): String =
        if (event.outcome == "planned" && all.any { it.medicineId == event.medicineId && it.plannedAt == event.plannedAt && it.outcome in setOf("taken", "skipped") }) "Plan followed by a report" else status(event, now)
    /** Who reported, from the phone's view role: an invited family member's report is stored as "Family report". */
    fun reporterFor(role: String) = when (role) { "Patient" -> "Patient report"; "Family" -> "Family report"; else -> "Not recorded" }
    /** The daily tick box keeps its older "Reporter not recorded" label unless the role says who ticked it. */
    fun checkoffReporter(role: String) = when (role) { "Patient", "Family" -> reporterFor(role); else -> "Reporter not recorded" }
    /** True for a dose report written by family, so the dose and the history can say "Logged by family". */
    fun loggedByFamily(event: MedicineIntakeEvent) = event.reporter.startsWith("Family report")
    /** A "missed" report from the daily list: no planned or taken time is invented, and no date field (the server takes only its own log time). */
    fun missedReport(item: CareItem, role: String, now: Long, id: String = java.util.UUID.randomUUID().toString()) = MedicineIntakeEvent(id, item.id, item.title, "skipped",
        loggedAt = now, reporter = reporterFor(role),
        note = "Reported missed from the daily list on ${Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}.", source = "manual")
    /** Latest taken/missed/corrected report logged on [day] for one medicine, or null. A ticked box for today always means taken. */
    fun todayOutcome(item: CareItem, events: List<MedicineIntakeEvent>, day: LocalDate, zone: java.time.ZoneId): String? {
        if (item.doneOn == day.toString()) return "taken"
        return events.filter { it.medicineId == item.id && it.outcome in setOf("taken", "skipped", "unmarked") &&
            (it.reportedDate == day.toString() || Instant.ofEpochMilli(it.loggedAt).atZone(zone).toLocalDate() == day) }
            .maxByOrNull { it.loggedAt }?.outcome?.takeIf { it != "unmarked" }
    }
    fun legacyDate(item: CareItem): String? = item.doneOn.takeIf { it.isNotBlank() && runCatching { LocalDate.parse(it) }.isSuccess }
}

internal fun medicineInstant(at: Long) = Instant.ofEpochMilli(at).toString()

/**
 * The dots on one day of a medicine's history calendar: green all taken, red something not taken, blue a side effect noted (two can show).
 * For an as-needed medicine (morphine "SOS"), [asNeededTaken] is how many doses were taken that day: one green dot each (25 Nov).
 */
data class MedicineDayMarks(val allTaken: Boolean = false, val notTaken: Boolean = false, val sideEffect: Boolean = false, val asNeededTaken: Int = 0) {
    val empty: Boolean get() = !allTaken && !notTaken && !sideEffect && asNeededTaken == 0
}

/**
 * Medicine history as a month calendar (Dr Sujay, 24 Nov 2026). Only what was reported is coloured: a day with no reports has no
 * dot (never "missed" by assumption), and a dose left out on purpose (Skipped) is neither red nor counted against green.
 */
object MedicineHistoryCalendar {
    private val answers = setOf("taken", "skipped", "unmarked")

    /** The day a report is about: its dose time's day, else the day it names, else the day it was logged. */
    fun eventDay(event: MedicineIntakeEvent, zone: java.time.ZoneId): LocalDate =
        event.plannedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?: event.reportedDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Instant.ofEpochMilli(event.loggedAt).atZone(zone).toLocalDate()

    /** One medicine's reports about [day], dose order (planned time, then when logged); old daily ticks that repeat dose reports are left out. */
    fun eventsOn(medicineId: String, events: List<MedicineIntakeEvent>, day: LocalDate, zone: java.time.ZoneId): List<MedicineIntakeEvent> {
        val mine = events.filter { it.medicineId == medicineId }
        return mine.filter { eventDay(it, zone) == day && !MedicineDoses.redundantCheckoff(it, mine, zone) }
            .sortedWith(compareBy<MedicineIntakeEvent>({ it.plannedAt ?: it.reportedTakenAt ?: it.loggedAt }, { it.loggedAt }))
    }

    /**
     * The dots for [day]. Each of the medicine's doses that day is read as it is on the Today list ([MedicineDoses.state]); reports for a
     * dose time the medicine no longer has (its times changed later) still count by their newest answer; an older whole-day tick counts
     * when nothing else was reported. Green needs every dose answered, none Not taken, and at least one Taken. Days after [today] have no dose dots.
     */
    fun marks(item: CareItem, doses: List<MedicineDose>, events: List<MedicineIntakeEvent>, sideEffectTimes: List<Long>, day: LocalDate, zone: java.time.ZoneId, today: LocalDate): MedicineDayMarks {
        val side = sideEffectTimes.any { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == day }
        if (day > today) return MedicineDayMarks(sideEffect = side)
        // As needed: no dose is ever "missed"; each dose taken that day is a dot (24 Nov: "morphine as needed" on the calendar too).
        if (MedicineDoses.isAsNeededMedicine(item)) {
            val taken = MedicineDoses.asNeededTimes(item.id, events, day, zone).size
            return MedicineDayMarks(allTaken = taken > 0, sideEffect = side, asNeededTaken = taken)
        }
        val mine = events.filter { it.medicineId == item.id && it.outcome in answers }
        // The whole-day tick on the medicine only ever means today.
        val ticked = if (day == today) item else item.copy(doneOn = "")
        val doseStates = doses.map { MedicineDoses.state(ticked, it, mine, day, zone) }
        val orphanStates = mine.filter { e -> e.plannedAt != null && eventDay(e, zone) == day && doses.none { MedicineDoses.belongsTo(e, it, day, zone) } }
            .groupBy { it.plannedAt }.values.map { list -> stateOf(list.maxBy { it.loggedAt }) }
        // Before a change of dose times, a day's reports are all for the old times: then only they count, not today's unanswered times.
        val answered = doseStates.any { it != MedicineDoseState.PENDING }
        var states = if (answered) doseStates + orphanStates else orphanStates
        if (states.none { it != MedicineDoseState.PENDING }) {
            val wholeDay = mine.filter { it.plannedAt == null && !MedicineDoses.isDoseNote(it.note) && eventDay(it, zone) == day }.maxByOrNull { it.loggedAt }
            states = listOfNotNull(wholeDay?.let(::stateOf))
        }
        val notTaken = states.any { it == MedicineDoseState.MISSED }
        val allTaken = !notTaken && states.isNotEmpty() && states.none { it == MedicineDoseState.PENDING } && states.any { it == MedicineDoseState.TAKEN }
        return MedicineDayMarks(allTaken, notTaken, side)
    }

    private fun stateOf(event: MedicineIntakeEvent): MedicineDoseState = MedicineDoseStatus.choiceOf(event)?.let(MedicineDoseStatus::stateOf) ?: MedicineDoseState.PENDING
}
