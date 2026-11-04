package `in`.wedevit.care

import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** What the Today screen shows, computed only from the selected person's saved entries. */
internal object CareTodayRules {
    /** Notification extra naming the person whose saved activity is due. */
    const val OPEN_PERSON_EXTRA = "open_person"

    /** Up next: open visits from today on; dated ones soonest first, undated ones after them. */
    fun upcomingVisits(items: List<CareItem>, dayStart: Long): List<CareItem> = items.filter {
        it.kind == "visit" && (!CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it)) &&
            CareEntryCodec.decode(it.detail)?.status !in listOf("cancelled", "completed") && (it.due == 0L || it.due >= dayStart)
    }.sortedBy { if (it.due == 0L) Long.MAX_VALUE else it.due }

    /** Today's daily check-in only; symptom-diary and pain-map notes share the kind but are not the check-in. */
    fun todayCheckIn(items: List<CareItem>, dayStart: Long): CareItem? =
        items.firstOrNull { it.kind == "checkin" && it.created >= dayStart && !SymptomNoteCodec.isStructured(it.detail) && !DistressCodec.isStructured(it.detail) }

    /** Empty Today sections say what to do next; read-only family members are not told to tap an add button. */
    fun emptyText(section: String, canEdit: Boolean): String = when (section) {
        "visits" -> if (canEdit) careBilingual("No upcoming visits. Tap Add visit to plan the next one.", "कोई आने वाली मुलाक़ात नहीं। अगली मुलाक़ात जोड़ने के लिए “मुलाक़ात जोड़ें” दबाएँ।")
            else careBilingual("No upcoming visits yet.", "अभी कोई आने वाली मुलाक़ात नहीं।")
        "tasks" -> if (canEdit) careBilingual("Nothing to do yet. Tap Add task for anything to remember.", "अभी कोई काम नहीं। याद रखने वाली बात के लिए “काम जोड़ें” दबाएँ।")
            else careBilingual("Nothing to do yet.", "अभी कोई काम नहीं।")
        else -> if (canEdit) careBilingual("No notes yet. Tap Write a note to record how the day went.", "अभी कोई नोट नहीं। दिन कैसा रहा, लिखने के लिए “नोट लिखें” दबाएँ।")
            else careBilingual("No notes yet.", "अभी कोई नोट नहीं।")
    }

    /** The half of [emptyText] for the chosen app language. */
    fun emptyText(section: String, canEdit: Boolean, hindi: Boolean): String =
        emptyText(section, canEdit).split(" · ").let { careTr(hindi, it.first(), it.last()) }

    /** "Good morning / afternoon / evening" by the phone's hour (0-23). */
    fun greeting(hour: Int, hindi: Boolean): String = when {
        hour < 12 -> careTr(hindi, "Good morning", "सुप्रभात")
        hour < 17 -> careTr(hindi, "Good afternoon", "शुभ दोपहर")
        else -> careTr(hindi, "Good evening", "शुभ संध्या")
    }

    /** Patient: "Good morning, Rahul". Family and others: the greeting alone (the family's own name is not in the record). */
    fun greetingLine(hour: Int, hindi: Boolean, name: String, patient: Boolean): String {
        val first = name.trim().substringBefore(' ')
        return if (patient && first.isNotBlank()) "${greeting(hour, hindi)}, $first" else greeting(hour, hindi)
    }

    /** "0 of 3": medicines ticked today out of today's medicines. No "not yet" count (3 Nov meeting). */
    fun medicineCount(taken: Int, total: Int, hindi: Boolean): String {
        val all = total.coerceAtLeast(0); val done = taken.coerceIn(0, all)
        return careTr(hindi, "$done of $all", "$all में से $done")
    }

    /** Up next badge: "Today", "Tomorrow", "In 3 days", or "Date to be set" for an undated visit. */
    fun whenLabel(due: Long, today: LocalDate, hindi: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
        if (due == 0L) return careTr(hindi, "Date to be set", "तारीख़ तय होनी है")
        val days = ChronoUnit.DAYS.between(today, Instant.ofEpochMilli(due).atZone(zone).toLocalDate())
        return when {
            days <= 0L -> careTr(hindi, "Today", "आज")
            days == 1L -> careTr(hindi, "Tomorrow", "कल")
            else -> careTr(hindi, "In $days days", "$days दिन में")
        }
    }

    /** The person a reminder belongs to, from the scheduler's saved snapshot; blank when it is no longer there. */
    fun personForItem(snapshot: String?, itemId: String): String = runCatching {
        val rows = JSONArray(snapshot ?: "[]")
        (0 until rows.length()).map(rows::getJSONObject).firstOrNull { it.optString("id") == itemId }?.optString("person").orEmpty()
    }.getOrDefault("")
}

/**
 * Back stack for the patient app's tabs and full-screen overlays. Each tab entry is "<tab>" or "<tab>><overlay>":
 * the second form reopens that overlay (for example the check-in) when Back returns to the tab, so Back always lands
 * on the page the person came from, not on Today.
 */
internal object CareNavTrail {
    /** Pages opened from inside a tab (Calendar, Labs, Symptoms, Emergency plan, Community, Tubes and lines). */
    val subPages = 6..12
    const val MAX = 10

    data class Entry(val tab: Int, val reopen: String)

    fun entries(trail: String): List<Entry> = trail.split(',').filter { it.isNotBlank() }.mapNotNull { raw ->
        raw.substringBefore('>').toIntOrNull()?.let { Entry(it, raw.substringAfter('>', "")) }
    }

    fun push(trail: String, tab: Int, reopen: String = ""): String =
        (trail.split(',').filter { it.isNotBlank() } + (if (reopen.isBlank()) "$tab" else "$tab>$reopen")).takeLast(MAX).joinToString(",")

    /** The entry Back returns to and the trail left after it; null when the trail is empty (Back goes to Today). */
    fun pop(trail: String): Pair<Entry, String>? {
        val list = trail.split(',').filter { it.isNotBlank() }
        val last = list.lastOrNull() ?: return null
        val entry = entries(last).firstOrNull() ?: return pop(list.dropLast(1).joinToString(","))
        return entry to list.dropLast(1).joinToString(",")
    }

    /** The bottom tab to highlight while a sub-page is open: the nearest main tab it was opened from. */
    fun rootTab(trail: String, tab: Int): Int = if (tab in subPages) entries(trail).lastOrNull { it.tab !in subPages }?.tab ?: tab else tab

    /** Overlays stack the same way (check-in, then distress): Back from distress returns to the check-in. */
    fun pushOverlay(trail: String, overlay: String): String = if (overlay.isBlank()) trail else (trail.split(',').filter { it.isNotBlank() } + overlay).takeLast(MAX).joinToString(",")
    fun popOverlay(trail: String): Pair<String, String> { val list = trail.split(',').filter { it.isNotBlank() }; return list.lastOrNull().orEmpty() to list.dropLast(1).joinToString(",") }
}

/** How a toast looks: a save gets the green tick, a problem the warning colour, everything else is plain information. */
internal enum class CareToastKind { SAVED, PROBLEM, INFO }

internal object CareToastRules {
    private val problem = Regex("""(?i)\b(couldn|can't|cannot|failed|unable|not available|isn't|wasn't|no app|try again|error|could not|not be|choose|reopen|ask the person)\b|n't\b|नहीं|फिर कोशिश""")
    fun kind(message: String): CareToastKind = when {
        SaharaMotion.isSavedMessage(message) -> CareToastKind.SAVED
        problem.containsMatchIn(message) -> CareToastKind.PROBLEM
        else -> CareToastKind.INFO
    }
    /** The text shown: without CareModel's invisible "saved" mark. */
    fun text(message: String): String = message.removePrefix(SaharaMotion.SAVED_MARK).trim()
}
