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
        "taken" -> if (event.plannedAt != null && event.reportedTakenAt != null && event.reportedTakenAt > event.plannedAt) "Late reported" else "Taken reported"
        "skipped" -> "Skipped reported"
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
