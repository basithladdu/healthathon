package `in`.wedevit.care

import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

/** One saved wellbeing check: the day and the ZBI-4 total (0 to 16). */
data class WellbeingEntry(val day: LocalDate, val score: Int)

/** One private journal note: when it was written (epoch millis) and the free text. */
data class JournalNote(val at: Long, val text: String)

/**
 * The family member's own wellbeing check, kept on this phone only (never added to the patient's record).
 *
 * The check is the 4-item Zarit Burden Interview screen (ZBI-4, Bedard et al. 2001): four questions, each 0 to 4
 * (never, rarely, sometimes, quite frequently, nearly always), total 0 to 16. A total of 8 or more suggests high burden
 * (Bedard 2001; Higginson 2010). It is a screening aid, not a diagnosis.
 * Pure Kotlin so it can be unit tested.
 */
object CaregiverWellbeingRules {
    const val ITEM_COUNT = 4
    const val ITEM_MAX = 4
    const val MAX_SCORE = ITEM_COUNT * ITEM_MAX
    /** ZBI-4: 8 or more is high burden. */
    const val HIGH_FROM = 8
    /** A jump of this many points since the previous check is a sharp rise, even below [HIGH_FROM]. */
    const val SHARP_RISE = 4
    /** Past checks kept and shown. */
    const val KEEP = 12
    const val TREND_SHOWN = 8

    const val SUPPORT_GROUP_SWAYAM = "https://swayamfoundation.co.in/support-group/caregiver-support/"
    const val SUPPORT_GROUP_SOULUP = "https://www.soulup.in/products/caregiving-support-group"
    const val MAPS_QUERY = "psychiatrist OR counsellor near me"
    const val TELE_MANAS_NUMBER = "14416"

    fun validAnswer(v: Int) = v in 0..ITEM_MAX

    /** Total of the four answers, or null unless all four are answered with 0 to 4. */
    fun score(answers: List<Int?>): Int? {
        if (answers.size != ITEM_COUNT || answers.any { it == null || !validAnswer(it) }) return null
        return answers.sumOf { it!! }
    }

    fun isHigh(score: Int) = score >= HIGH_FROM

    /** Newest entry's change from the one before it, or null with fewer than two entries. */
    fun change(history: List<WellbeingEntry>): Int? {
        val sorted = history.sortedBy { it.day }
        if (sorted.size < 2) return null
        return sorted.last().score - sorted[sorted.size - 2].score
    }

    fun risingSharply(history: List<WellbeingEntry>): Boolean = (change(history) ?: 0) >= SHARP_RISE

    /** Show the "You're carrying a lot" card when the latest score is high or has risen sharply. */
    fun shouldFlag(history: List<WellbeingEntry>): Boolean {
        val latest = history.maxByOrNull { it.day } ?: return false
        return isHigh(latest.score) || risingSharply(history)
    }

    enum class Trend { Rising, Falling, Steady, None }
    fun trend(history: List<WellbeingEntry>): Trend {
        val c = change(history) ?: return Trend.None
        return when { c >= 2 -> Trend.Rising; c <= -2 -> Trend.Falling; else -> Trend.Steady }
    }

    /** Add a check: one entry per day (a second check on the same day replaces the first), oldest first, newest [KEEP] kept. */
    fun add(history: List<WellbeingEntry>, entry: WellbeingEntry): List<WellbeingEntry> =
        (history.filter { it.day != entry.day } + entry.copy(score = entry.score.coerceIn(0, MAX_SCORE))).sortedBy { it.day }.takeLast(KEEP)

    /** The last [TREND_SHOWN] scores, oldest first, for the small trend. */
    fun recent(history: List<WellbeingEntry>): List<WellbeingEntry> = history.sortedBy { it.day }.takeLast(TREND_SHOWN)

    fun encodeHistory(history: List<WellbeingEntry>): String = history.joinToString(";") { "${it.day}|${it.score}" }
    fun decodeHistory(raw: String?): List<WellbeingEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = raw.split(';').mapNotNull { part ->
            val bits = part.split('|')
            if (bits.size != 2) return@mapNotNull null
            val day = runCatching { LocalDate.parse(bits[0]) }.getOrNull() ?: return@mapNotNull null
            val score = bits[1].toIntOrNull()?.takeIf { it in 0..MAX_SCORE } ?: return@mapNotNull null
            WellbeingEntry(day, score)
        }
        return out.groupBy { it.day }.map { it.value.last() }.sortedBy { it.day }.takeLast(KEEP)
    }

    /** Journal: one note per line, "<millis><TAB><url-encoded text>", so any text (including new lines) is safe. */
    fun encodeJournal(notes: List<JournalNote>): String = notes.joinToString("\n") { "${it.at}\t${URLEncoder.encode(it.text, "UTF-8")}" }
    fun decodeJournal(raw: String?): List<JournalNote> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val bits = line.split('\t')
            if (bits.size != 2) return@mapNotNull null
            val at = bits[0].toLongOrNull() ?: return@mapNotNull null
            val text = runCatching { URLDecoder.decode(bits[1], "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            JournalNote(at, text)
        }.sortedByDescending { it.at }
    }

    /** Add a note; blank text is ignored. Newest first. */
    fun addNote(notes: List<JournalNote>, at: Long, text: String): List<JournalNote> {
        val clean = text.trim()
        if (clean.isEmpty()) return notes
        return (listOf(JournalNote(at, clean.take(MAX_NOTE_CHARS))) + notes).sortedByDescending { it.at }
    }
    const val MAX_NOTE_CHARS = 2000

    fun storeKey(storageNamespace: String, person: String) = "$storageNamespace|$person"
}
