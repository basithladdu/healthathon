package `in`.wedevit.care

/**
 * The doctor-driven topic guide (Dr Sujay, 7 Nov 2026): the doctor taps through the 13 topics of the goals-of-care checklist as the talk goes.
 * No AI is involved. Topics are identified by their row index in [GocChecklist.ROWS]; times are seconds from the recording start (-1 when no recording runs).
 */
internal data class GocGuideState(val stamps: Map<Int, Int> = emptyMap(), val now: Int = -1) {
    /** Started and finished: everything with a start time except the topic being discussed right now. */
    val discussed: Set<Int> get() = stamps.keys.filter { it != now }.toSet()
}

internal object GocTopicGuide {
    const val ESSENTIAL_COUNT = 5
    const val UNDERSTANDING_TITLE = "Patient's understanding of disease"
    private val essentialTitles = setOf(UNDERSTANDING_TITLE, "Home care vs hospital transfer", "CPR preference", "ICU care", "Ventilatory support")

    fun isEssential(titleEn: String): Boolean = titleEn in essentialTitles

    /** How many of the five essentials are discussed. [titles] are the English row titles in row order. */
    fun essentialsDone(discussed: Set<Int>, titles: List<String>): Int = discussed.count { it in titles.indices && titles[it] in essentialTitles }

    /** The next topic not yet discussed after [from], wrapping round; null when every topic is discussed. */
    fun next(discussed: Set<Int>, from: Int, count: Int): Int? {
        if (count <= 0) return null
        for (step in 1..count) { val i = ((from + step) % count + count) % count; if (i !in discussed) return i }
        return null
    }

    /** Tapping a tile: it becomes "now discussing" and the previous one is left discussed at its own start time; tapping the current one finishes it. */
    fun tap(s: GocGuideState, idx: Int, sec: Int): GocGuideState =
        if (idx == s.now) GocGuideState(s.stamps, -1) else GocGuideState(s.stamps + (idx to (s.stamps[idx] ?: sec)), idx)

    /** "Done": the topic is discussed, stamped with the time it was marked unless it already had a start time. */
    fun done(s: GocGuideState, idx: Int, sec: Int): GocGuideState =
        GocGuideState(s.stamps + (idx to (s.stamps[idx] ?: sec)), if (s.now == idx) -1 else s.now)

    /** True when a Section III topic was started while the patient's understanding of the disease has not been discussed. */
    fun outOfOrder(started: Int, discussed: Set<Int>, sections: List<String>, titles: List<String>): Boolean {
        val understanding = titles.indexOf(UNDERSTANDING_TITLE)
        if (started !in sections.indices || understanding < 0 || started == understanding) return false
        return sections[started] == "III" && understanding !in discussed
    }

    /** mm:ss from a number of seconds; "--:--" when no recording time is known. */
    fun mmss(seconds: Int): String = if (seconds < 0) "--:--" else "%02d:%02d".format(seconds / 60, seconds % 60)

    fun encode(stamps: Map<Int, Int>): String = stamps.entries.sortedBy { it.key }.joinToString(";") { "${it.key}:${it.value}" }
    fun decode(text: String): Map<Int, Int> = text.split(';').mapNotNull { part ->
        val bits = part.split(':'); val k = bits.getOrNull(0)?.toIntOrNull(); val v = bits.getOrNull(1)?.toIntOrNull()
        if (k != null && v != null) k to v else null
    }.toMap()
}
