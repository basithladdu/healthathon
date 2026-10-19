package `in`.wedevit.care

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class CareReminderPlan(
    val itemId: String, val personId: String, val kind: String, val due: Long,
    val times: List<String>, val doneOn: String, val finished: Boolean,
    val starts: String = "", val ends: String = "",
)
data class CareReminderOccurrence(val key: String, val itemId: String, val at: Long, val localDate: String, val time: String)

object CareReminderRules {
    fun validTimes(input: List<String>): List<String> {
        require(input.size <= 6) { "Use up to six reminder times." }
        require(input.all { it.matches(Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")) }) { "Use HH:mm, such as 08:30." }
        return input.distinct().sorted()
    }
    fun validateDates(starts: String, ends: String) {
        if (starts.isNotBlank()) require(runCatching { LocalDate.parse(starts).toString() == starts }.getOrDefault(false)) { "Use a start date as YYYY-MM-DD." }
        if (ends.isNotBlank()) require(runCatching { LocalDate.parse(ends).toString() == ends }.getOrDefault(false)) { "Use an end date as YYYY-MM-DD." }
        require(starts.isBlank() || ends.isBlank() || ends >= starts) { "The end date must follow the start date." }
    }
    fun next(plan: CareReminderPlan, after: Long, zone: ZoneId): List<CareReminderOccurrence> {
        if (plan.finished || plan.itemId.isBlank() || plan.personId.isBlank()) return emptyList()
        if (plan.kind != "medicine") return if (plan.due > after && plan.doneOn.isBlank()) listOf(CareReminderOccurrence("${plan.itemId}:once", plan.itemId, plan.due, Instant.ofEpochMilli(plan.due).atZone(zone).toLocalDate().toString(), "")) else emptyList()
        validateDates(plan.starts, plan.ends)
        val today = Instant.ofEpochMilli(after).atZone(zone).toLocalDate()
        return validTimes(plan.times).mapNotNull { text ->
            var day = today
            if (plan.starts.isNotBlank() && day.toString() < plan.starts) day = LocalDate.parse(plan.starts)
            // A daily completion in the existing care model means the whole medicine entry, not a per-dose proof.
            if (day.toString() == plan.doneOn) day = day.plusDays(1)
            var at = day.atTime(LocalTime.parse(text)).atZone(zone).toInstant().toEpochMilli()
            if (at <= after) { day = day.plusDays(1); at = day.atTime(LocalTime.parse(text)).atZone(zone).toInstant().toEpochMilli() }
            if (plan.ends.isNotBlank() && day.toString() > plan.ends) null
            else CareReminderOccurrence("${plan.itemId}:$text", plan.itemId, at, day.toString(), text)
        }
    }
    fun shouldDeliver(expected: CareReminderOccurrence, suppliedAt: Long, now: Long, lastDeliveredAt: Long, allowed: Boolean): Boolean =
        allowed && suppliedAt == expected.at && lastDeliveredAt != expected.at && now >= expected.at && now - expected.at <= 6 * 60 * 60 * 1000L

    fun printedTimes(detail: String): List<String> {
        val line = detail.lineSequence().firstOrNull { it.startsWith("Times: ") }?.removePrefix("Times: ") ?: return emptyList()
        return runCatching { validTimes(line.split(',').map(String::trim)) }.getOrDefault(emptyList())
    }
}
