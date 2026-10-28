package `in`.wedevit.care

import java.time.LocalDate

/** Small rules that shorten the family's daily flow: the Today check-in card, medicines to tick first, and who answered last time. */
internal object CareDailyFlow {
    val moods = listOf(CheckInMood.HARD, CheckInMood.OKAY, CheckInMood.GOOD)

    /** Medicines not yet ticked today come first (in their saved order), so the next one to tick is always on Today. */
    fun medicinesForToday(items: List<CareItem>, today: LocalDate): List<CareItem> = items.filter { it.kind == "medicine" }.sortedBy { it.doneOn == today.toString() }

    fun takenToday(items: List<CareItem>, today: LocalDate): Int = items.count { it.kind == "medicine" && it.doneOn == today.toString() }

    /** Who answered this person's latest symptom note; a new note starts with the same answer, shown and changeable. */
    fun lastReporter(entries: List<CareItem>, personId: String): Pair<String, String>? = entries
        .filter { it.personId == personId && SymptomNoteCodec.isStructured(it.detail) }
        .sortedByDescending { it.created }
        .firstNotNullOfOrNull { SymptomNoteCodec.decode(it.detail)?.takeIf { n -> n.respondent in SymptomDiaryRules.respondents } }
        ?.let { it.respondent to it.author }

    /** A tap on a mood: null when nothing needs saving (the same mood is already saved today). */
    fun checkInChange(today: CareItem?, mood: String): CareItem? = when {
        mood !in moods -> null
        today == null -> CareItem(personId = "", kind = "checkin", title = mood)
        today.title == mood -> null
        else -> today.copy(title = mood)
    }
}
