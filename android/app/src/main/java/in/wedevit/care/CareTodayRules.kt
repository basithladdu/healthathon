package `in`.wedevit.care

import org.json.JSONArray

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
        items.firstOrNull { it.kind == "checkin" && it.created >= dayStart && !SymptomNoteCodec.isStructured(it.detail) }

    /** Empty Today sections say what to do next; read-only family members are not told to tap an add button. */
    fun emptyText(section: String, canEdit: Boolean): String = when (section) {
        "visits" -> if (canEdit) "No upcoming visits. Tap Add visit to plan the next one. · कोई आने वाली मुलाक़ात नहीं। अगली मुलाक़ात जोड़ने के लिए “मुलाक़ात जोड़ें” दबाएँ।"
            else "No upcoming visits yet. · अभी कोई आने वाली मुलाक़ात नहीं।"
        "tasks" -> if (canEdit) "Nothing to do yet. Tap Add task for anything to remember. · अभी कोई काम नहीं। याद रखने वाली बात के लिए “काम जोड़ें” दबाएँ।"
            else "Nothing to do yet. · अभी कोई काम नहीं।"
        else -> if (canEdit) "No notes yet. Tap Write a note to record how the day went. · अभी कोई नोट नहीं। दिन कैसा रहा, लिखने के लिए “नोट लिखें” दबाएँ।"
            else "No notes yet. · अभी कोई नोट नहीं।"
    }

    /** The half of [emptyText] for the chosen app language. */
    fun emptyText(section: String, canEdit: Boolean, hindi: Boolean): String =
        emptyText(section, canEdit).split(" · ").let { if (hindi) it.last() else it.first() }

    /** The person a reminder belongs to, from the scheduler's saved snapshot; blank when it is no longer there. */
    fun personForItem(snapshot: String?, itemId: String): String = runCatching {
        val rows = JSONArray(snapshot ?: "[]")
        (0 until rows.length()).map(rows::getJSONObject).firstOrNull { it.optString("id") == itemId }?.optString("person").orEmpty()
    }.getOrDefault("")
}
