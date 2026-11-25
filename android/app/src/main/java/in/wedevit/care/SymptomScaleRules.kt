package `in`.wedevit.care

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pictorial symptom scales for patients (clinical team, "Oldies_illiterate_thoughts" points 5, 7 and 8).
 *
 * Patients answer with four faces and four plain words; clinicians and family keep the 0-10 number. Every saved record keeps
 * the 0-10 number, so trends, doctor alerts and exports are unchanged. The mapping is the same one Simple Mode uses
 * ([SimpleModeRules.PainWord]):
 *
 *   word → number saved:   None 0 · A little 3 · A lot 6 · Worst 9
 *   number → word shown:   0 None · 1-4 A little · 5-7 A lot · 8-10 Worst (each number goes to the nearest word)
 *
 * 9 rather than 10 for "Worst": the patient said "worst", not "the worst imaginable"; a clinician can still record 10.
 * The time-of-day rules give Morning / Afternoon / Evening / Night with the same clock cut-offs and times as the medicine
 * screens ([MedicineDoseRules.slot] and [MedicineDoseRules.commonTimes]), so a time is never typed.
 */
object SymptomScaleRules {
    /** One face on the patient scale. [face] is the smile for [CareMoodFace]: 1 a smile, 0 a sad face. */
    enum class Level(val key: String, val english: String, val hindi: String, val rating: Int, val face: Float) {
        NONE("none", "None", "बिलकुल नहीं", 0, 1f),
        A_LITTLE("a-little", "A little", "थोड़ा", 3, .68f),
        A_LOT("a-lot", "A lot", "बहुत", 6, .34f),
        WORST("worst", "Worst", "सबसे ज़्यादा", 9, 0f);
    }

    /** The 0-10 number saved for a face. */
    fun rating(level: Level): Int = level.rating

    /** The face shown for a saved 0-10 number (also for numbers a clinician or family member entered). */
    fun fromRating(rating: Int): Level = when (rating.coerceIn(0, 10)) { 0 -> Level.NONE; in 1..4 -> Level.A_LITTLE; in 5..7 -> Level.A_LOT; else -> Level.WORST }

    /** Kept identical to Simple Mode's pain words, so a "a lot" means the same number everywhere in the app. */
    fun matchesSimpleMode(): Boolean = Level.entries.all { l -> SimpleModeRules.PainWord.entries.any { it.key == l.key && it.rating == l.rating && SimpleModeRules.fromRating(l.rating).key == l.key } } &&
        (0..10).all { fromRating(it).key == SimpleModeRules.fromRating(it).key }

    /**
     * The word under a face. Overall well-being is asked the other way round in the diary (0 = best well-being), so its words
     * are about feeling well; every other symptom uses None / A little / A lot / Worst.
     */
    fun word(level: Level, scale: String, hindi: Boolean): String = if (scale == SymptomDiaryRules.WELLBEING_SCALE) when (level) {
        Level.NONE -> careTr(hindi, "Very well", "बहुत अच्छी")
        Level.A_LITTLE -> careTr(hindi, "Fairly well", "ठीक-ठाक")
        Level.A_LOT -> careTr(hindi, "Unwell", "खराब")
        Level.WORST -> careTr(hindi, "Very unwell", "बहुत खराब")
    } else careTr(hindi, level.english, level.hindi)

    /** Patients answer with pictures; family and the care team answer with numbers. */
    fun picturesByDefault(viewRole: String): Boolean = viewRole == "Patient"

    /** What a saved rating reads as: a word for a patient ("A lot"), the number for everyone else ("6/10"). */
    fun shown(rating: Int?, pictures: Boolean, hindi: Boolean, scale: String = SymptomDiaryRules.PERSONAL_SCALE): String = when {
        rating == null -> careTr(hindi, "Not rated", "अंक नहीं बताया")
        pictures -> word(fromRating(rating), scale, hindi)
        else -> "$rating/10"
    }

    // ------------------------------------------------------------------ time of day (never typed)

    /** Morning, Afternoon, Evening or Night for a clock time, with the medicine screens' cut-offs (4-11, 12-16, 17-20, else night). */
    fun partOf(time: LocalTime): DoseSlot = MedicineDoseRules.slot(careClockValue(time.hour, time.minute))

    /** The usual clock time saved for a part of the day: 8:00 AM, 1:00 PM, 6:00 PM, 9:00 PM. */
    fun partTime(part: DoseSlot): LocalTime = LocalTime.parse(MedicineDoseRules.commonTimes[part.ordinal])

    /** A part of today can be chosen once it has begun (or is now); an earlier day allows all four. */
    fun partAllowed(part: DoseSlot, day: LocalDate, now: LocalDateTime): Boolean = when {
        day.isAfter(now.toLocalDate()) -> false
        day.isBefore(now.toLocalDate()) -> true
        else -> partOf(now.toLocalTime()) == part || !partTime(part).isAfter(now.toLocalTime())
    }

    /** The time saved for a tapped part of the day: its usual time, but never later than now (a note cannot be in the future). */
    fun timeFor(part: DoseSlot, day: LocalDate, now: LocalDateTime): String {
        val usual = partTime(part)
        val time = if (day == now.toLocalDate() && usual.isAfter(now.toLocalTime())) now.toLocalTime() else usual
        return careClockValue(time.hour, time.minute)
    }

    /** After the day changes, a saved time that is now in the future is brought back to now; otherwise it is kept. */
    fun clamp(day: LocalDate, time: String, now: LocalDateTime): String {
        val t = careClock(time) ?: return careClockValue(now.hour, now.minute)
        return if (day == now.toLocalDate() && t.isAfter(now.toLocalTime())) careClockValue(now.hour, now.minute) else time
    }

    enum class Day { TODAY, YESTERDAY, EARLIER }

    fun dayOf(date: LocalDate, today: LocalDate): Day = when (date) { today -> Day.TODAY; today.minusDays(1) -> Day.YESTERDAY; else -> Day.EARLIER }

    /** "When did it start?" by day: today or yesterday, as a date counted back from the note's own day. */
    fun startedOn(day: Day, observed: LocalDate): String? = when (day) { Day.TODAY -> observed.toString(); Day.YESTERDAY -> observed.minusDays(1).toString(); Day.EARLIER -> null }

    /** The time-of-day aware question on the patient scale: "How is your pain this morning?". */
    fun prompt(symptom: String, part: DoseSlot, hindi: Boolean): String {
        val name = SymptomDiaryRules.symptomLabel(symptom, hindi).lowercase()
        if (symptom == SymptomDiaryRules.WELLBEING_SYMPTOM) return when (part) {
            DoseSlot.MORNING -> careTr(hindi, "How are you feeling this morning?", "आज सुबह आप कैसा महसूस कर रहे हैं?")
            DoseSlot.AFTERNOON -> careTr(hindi, "How are you feeling this afternoon?", "आज दोपहर आप कैसा महसूस कर रहे हैं?")
            DoseSlot.EVENING -> careTr(hindi, "How are you feeling this evening?", "आज शाम आप कैसा महसूस कर रहे हैं?")
            DoseSlot.NIGHT -> careTr(hindi, "How are you feeling tonight?", "आज रात आप कैसा महसूस कर रहे हैं?")
        }
        return when (part) {
            DoseSlot.MORNING -> careTr(hindi, "How much $name this morning?", "आज सुबह $name कितना है?")
            DoseSlot.AFTERNOON -> careTr(hindi, "How much $name this afternoon?", "आज दोपहर $name कितना है?")
            DoseSlot.EVENING -> careTr(hindi, "How much $name this evening?", "आज शाम $name कितना है?")
            DoseSlot.NIGHT -> careTr(hindi, "How much $name tonight?", "आज रात $name कितना है?")
        }
    }

    /** What the speaker button says on the patient scale: the question, then the four words in order. */
    fun spoken(question: String, scale: String, hindi: Boolean): String = "$question " + Level.entries.joinToString(", ") { word(it, scale, hindi) } + "."

    // ------------------------------------------------------------------ distress, pictures for patients

    /**
     * The five picture groups patients see on the distress causes step, each one NCCN problem-list group. Keys are stored.
     * [category] is the NCCN group's English name in [DistressRules.categories].
     */
    enum class DistressGroup(val key: String, val english: String, val hindi: String, val category: String) {
        BODY("body", "Body", "शरीर", "Physical Concerns"),
        FEELINGS("feelings", "Feelings", "मन और भावनाएँ", "Emotional Concerns"),
        FAMILY("family", "Family", "परिवार", "Social Concerns"),
        MONEY("money", "Money and practical", "पैसा और रोज़मर्रा के काम", "Practical Concerns"),
        FAITH("faith", "Faith", "आस्था", "Spiritual or Religious Concerns");
    }

    val distressGroupKeys: List<String> = DistressGroup.entries.map { it.key }

    fun distressGroup(key: String): DistressGroup? = DistressGroup.entries.firstOrNull { it.key == key }

    fun distressGroupLabel(key: String, hindi: Boolean): String = distressGroup(key)?.let { careTr(hindi, it.english, it.hindi) } ?: key

    /** The picture group a problem-list item belongs to (so ticking "Pain" also lights the Body picture). */
    fun groupOfProblem(problem: String): DistressGroup? = DistressRules.categories.firstOrNull { c -> c.items.any { it.first == problem } }
        ?.let { c -> DistressGroup.entries.firstOrNull { it.category == c.en } }

    /** Groups shown as chosen: the ones tapped, plus any whose problems were ticked; in picture order. */
    fun shownGroups(groups: Collection<String>, problems: Collection<String>): List<String> {
        val all = groups.toSet() + problems.mapNotNull { groupOfProblem(it)?.key }
        return distressGroupKeys.filter { it in all }
    }

    /** Thermometer picture: the face beside the tube for a 0-10 distress number (0 a smile, 10 a sad face). */
    fun distressFace(score: Int): Float = 1f - score.coerceIn(0, 10) / 10f
}
