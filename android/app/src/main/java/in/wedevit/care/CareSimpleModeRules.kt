package `in`.wedevit.care

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.hypot

/**
 * Simple Mode (Dr Sharada's proposal, 23 Nov 2026): a patient home of four large spoken buttons for older and non-literate
 * patients. These are its pure rules; the screens are in CareSimpleMode.kt and the voice in CareSimpleSpeech.kt.
 * Everything is read from and saved to the same records as the full app, so the doctor sees the same check-ins, pain
 * notes and dose ticks. Nothing here speaks of prognosis, stage or end of life.
 */
object SimpleModeRules {
    /** Team decision (24 Nov meeting): Simple Mode starts on by itself for the patient's own phone at 60 or older, until they choose in More. Family and doctors are never switched. */
    const val AUTO_AGE = 60
    fun autoOn(age: Int?, patientOwnView: Boolean): Boolean = patientOwnView && age != null && age >= AUTO_AGE

    const val AMBULANCE = "108"
    /** At most this many care-team people on the Call screen, so it stays one short list (108 is always added after them). */
    const val MAX_CONTACTS = 5

    /**
     * The verbal pain scale for patients (none / a little / a lot / worst). The saved record keeps the 0-10 number the
     * clinicians use: none 0, a little 3, a lot 6, worst 9. [fromRating] turns a 0-10 number back into the words.
     */
    enum class PainWord(val key: String, val english: String, val hindi: String, val rating: Int) {
        NONE("none", "None", "बिलकुल नहीं", 0),
        A_LITTLE("a-little", "A little", "थोड़ा", 3),
        A_LOT("a-lot", "A lot", "बहुत", 6),
        WORST("worst", "Worst", "सबसे ज़्यादा", 9);
    }

    fun rating(word: PainWord): Int = word.rating

    /** 0 none, 1-4 a little, 5-7 a lot, 8-10 worst: each number goes to the word whose saved value is nearest. */
    fun fromRating(rating: Int): PainWord = when (rating.coerceIn(0, 10)) { 0 -> PainWord.NONE; in 1..4 -> PainWord.A_LITTLE; in 5..7 -> PainWord.A_LOT; else -> PainWord.WORST }

    /** The picture groups for dose times: sun, afternoon sun, sunset, moon, and "any time" for doses saved without a time. */
    enum class Slot { MORNING, AFTERNOON, EVENING, NIGHT, ANY_TIME }

    /** A timed dose goes by its clock time (the same cut-offs as the Today card); a dose with no saved time is never given one. */
    fun slotFor(dose: MedicineDose): Slot = if (!dose.timed) Slot.ANY_TIME else slotOf(MedicineDoseRules.slot(dose.time))

    fun slotAt(now: LocalTime): Slot = slotOf(MedicineDoseRules.slot("%02d:%02d".format(now.hour, now.minute)))

    private fun slotOf(slot: DoseSlot): Slot = when (slot) { DoseSlot.MORNING -> Slot.MORNING; DoseSlot.AFTERNOON -> Slot.AFTERNOON; DoseSlot.EVENING -> Slot.EVENING; DoseSlot.NIGHT -> Slot.NIGHT }

    /** Today's doses by picture group, in day order (morning first, any time last); inside a group by time, then name. */
    fun group(doses: List<Pair<CareItem, MedicineDose>>): Map<Slot, List<Pair<CareItem, MedicineDose>>> {
        val bySlot = doses.groupBy { slotFor(it.second) }
        return Slot.entries.filter { it in bySlot }.associateWith { slot ->
            bySlot.getValue(slot).sortedWith(compareBy({ it.second.time }, { it.first.title.lowercase() }, { it.second.index }))
        }
    }

    /**
     * The group the medicines screen opens on: the one for the time now, or else the next one later today, or else the
     * first group (any time, or this morning's if the day is over).
     */
    fun startSlot(slots: Collection<Slot>, now: Slot): Slot? {
        if (slots.isEmpty()) return null
        if (now in slots) return now
        return slots.filter { it != Slot.ANY_TIME && it.ordinal > now.ordinal }.minByOrNull { it.ordinal }
            ?: slots.firstOrNull { it == Slot.ANY_TIME } ?: slots.minBy { it.ordinal }
    }

    private val roleOrder = mapOf("clinician" to 0, "caregiver" to 1, "family" to 2)

    /** Digits of a phone number, keeping a leading +. */
    fun digits(phone: String): String = phone.trim().let { p -> (if (p.startsWith("+")) "+" else "") + p.filter(Char::isDigit) }

    /**
     * The care-team people shown on the Call screen: this person's contacts that have a phone number (at least 7 digits),
     * one button per number, the care team first, then carers, then family, by name; at most [MAX_CONTACTS].
     */
    fun callable(contacts: List<CareContact>, personId: String): List<CareContact> = contacts
        .filter { it.personId == personId && it.name.isNotBlank() && digits(it.phone).count(Char::isDigit) >= 7 }
        .distinctBy { digits(it.phone).removePrefix("+").takeLast(10) }
        .sortedWith(compareBy({ roleOrder[it.role] ?: 3 }, { it.name.trim().lowercase() }))
        .take(MAX_CONTACTS)

    /** The large letter on a call button when there is no photo: the first letter of the name, upper case; "?" when none. */
    fun initial(name: String): String {
        val text = name.trim()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (Character.isLetter(cp)) return String(Character.toChars(cp)).uppercase()
            i += Character.charCount(cp)
        }
        return "?"
    }

    /**
     * The body part under a tap on the flat body outline ([PainBodyMapRules.regions], 100 x 200 units). A tap just outside
     * a thin part (an arm, the neck) still counts for the nearest part within [reach] units, so the targets feel big.
     */
    fun siteAt(back: Boolean, x: Float, y: Float, reach: Float = 14f): String? {
        PainBodyMapRules.hit(back, x, y)?.let { return it }
        return PainBodyMapRules.regions(back).map { region ->
            region.id to region.outline.minOf { p -> hypot((p.x - x).toDouble(), (p.y - y).toDouble()) }
        }.filter { it.second <= reach }.minByOrNull { it.second }?.first
    }

    /** The voice to speak with: the app's language when the phone has that voice, otherwise English (said nothing about). */
    fun voiceLanguage(chosen: CareLang, available: (CareLang) -> Boolean): CareLang = if (chosen == CareLang.EN || available(chosen)) chosen else CareLang.EN

    /**
     * The pain note saved after "Where does it hurt?": an ordinary symptom-diary pain note (so the doctor's pain map and
     * trends show it), with every tapped part at the verbal scale's number and the word kept in the note.
     */
    fun painNote(sites: List<String>, word: PainWord, now: LocalDateTime, author: String, familyHelped: Boolean): SymptomNote {
        val parts = PainBodyMapRules.sites.keys.filter { it in sites }
        val rating = rating(word)
        return SymptomNote(
            symptom = "Pain", rating = rating, observedDate = now.toLocalDate().toString(), observedTime = "%02d:%02d".format(now.hour, now.minute),
            recall = "now", respondent = if (familyHelped) "assisted-patient" else "patient", author = author.trim().ifBlank { "Patient" }.take(120),
            note = "Told in Simple Mode on the verbal scale: ${word.english.lowercase()} ($rating/10).", scale = SymptomDiaryRules.PERSONAL_SCALE,
            painBodySites = parts, painBodyRatings = parts.associateWith { rating },
        )
    }

    /** Spoken and shown words for Simple Mode; [lang] is the voice's language (English when the phone has no voice for the app's). */
    fun slotName(slot: Slot, lang: CareLang): String = when (slot) {
        Slot.MORNING -> careTr(lang, "Morning", "सुबह")
        Slot.AFTERNOON -> careTr(lang, "Afternoon", "दोपहर")
        Slot.EVENING -> careTr(lang, "Evening", "शाम")
        Slot.NIGHT -> careTr(lang, "Night", "रात")
        Slot.ANY_TIME -> careTr(lang, "Any time", "कभी भी")
    }

    fun medicinesPrompt(slot: Slot?, names: List<String>, lang: CareLang): String {
        val lead = when (slot) {
            null -> return careTr(lang, "There are no medicines for today.", "आज के लिए कोई दवा नहीं है।")
            Slot.MORNING -> careTr(lang, "These are your medicines for this morning", "ये आपकी आज सुबह की दवाइयाँ हैं")
            Slot.AFTERNOON -> careTr(lang, "These are your medicines for this afternoon", "ये आपकी आज दोपहर की दवाइयाँ हैं")
            Slot.EVENING -> careTr(lang, "These are your medicines for this evening", "ये आपकी आज शाम की दवाइयाँ हैं")
            Slot.NIGHT -> careTr(lang, "These are your medicines for tonight", "ये आपकी आज रात की दवाइयाँ हैं")
            Slot.ANY_TIME -> careTr(lang, "These medicines can be taken at any time of day", "ये दवाइयाँ दिन में कभी भी ली जा सकती हैं")
        }
        return if (names.isEmpty()) "$lead." else "$lead: ${names.distinct().joinToString(", ")}."
    }

    /** "Read to me" when there is no message from the doctor: today's medicines by time of day, then the next visit. */
    fun dayText(groups: Map<Slot, List<String>>, visit: String?, lang: CareLang): String {
        val medicines = if (groups.isEmpty()) careTr(lang, "There are no medicines for today.", "आज के लिए कोई दवा नहीं है।")
            else careTr(lang, "Today's medicines:", "आज की दवाइयाँ:") + "\n" + groups.entries.joinToString("\n") { (slot, names) -> "${slotName(slot, lang)}: ${names.distinct().joinToString(", ")}." }
        val next = if (visit.isNullOrBlank()) careTr(lang, "No visit is planned yet.", "अभी कोई मुलाक़ात तय नहीं है।")
            else careTr(lang, "Your next visit:", "आपकी अगली मुलाक़ात:") + " $visit."
        return "$medicines\n$next"
    }
}
