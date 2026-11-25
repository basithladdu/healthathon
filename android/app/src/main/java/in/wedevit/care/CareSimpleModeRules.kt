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

    // ------------------------------------------------------------ Home

    /** The patient home: exactly these four big buttons, in reading order (two rows of two), and nothing else. */
    enum class HomeButton { FEEL, MEDICINES, READ, CALL }

    // ------------------------------------------------------------ Faces scale

    /**
     * The faces scale next to the verbal words (point 7 of the clinical team's note): a smiling face for none down to a
     * sad face for the worst, drawn by [CareMoodFace] (1 smiling, 0 sad). Clinicians keep the 0-10 number.
     */
    fun faceScore(word: PainWord): Float = when (word) { PainWord.NONE -> 1f; PainWord.A_LITTLE -> .67f; PainWord.A_LOT -> .33f; PainWord.WORST -> 0f }

    /** The face for a 0-10 number written by a clinician or carer: the number goes to its word first. */
    fun faceForRating(rating: Int): Float = faceScore(fromRating(rating))

    // ------------------------------------------------------------ Messages to read aloud

    /** Who a message to read aloud is from. Stored by [key] (English); the patient hears "your doctor", "your nurse"… */
    enum class MessageFrom(val key: String, val english: String, val hindi: String) {
        DOCTOR("doctor", "Doctor", "डॉक्टर"),
        NURSE("nurse", "Nurse", "नर्स"),
        CARE_TEAM("care-team", "Care team", "देखभाल टीम"),
        FAMILY("family", "Family", "परिवार");
        companion object { fun of(key: String): MessageFrom? = entries.firstOrNull { it.key == key.trim().lowercase() } }
    }

    data class ReadAloudMessage(val itemId: String, val from: MessageFrom, val name: String, val text: String, val created: Long)

    /**
     * A message for "Read to me" is an ordinary journal entry (the kinds the server takes are fixed) with this title and a
     * first line "From: nurse · Priya", then the words. It reads as plain text in the full app's journal, so the family and
     * the care team see the same message.
     */
    const val MESSAGE_TITLE = "Message to read aloud"
    const val MESSAGE_MAX = 1500
    private const val FROM = "From: "

    fun messageDetail(from: MessageFrom, name: String, text: String): String {
        val who = name.replace('\n', ' ').trim().take(60)
        return FROM + from.key + (if (who.isEmpty()) "" else " · $who") + "\n\n" + text.trim()
    }

    /** The message in [item], or null when the item is not a readable message to read aloud. */
    fun message(item: CareItem): ReadAloudMessage? {
        if (item.kind != "journal" || item.title != MESSAGE_TITLE || !item.detail.startsWith(FROM)) return null
        val head = item.detail.lineSequence().first().removePrefix(FROM)
        val from = MessageFrom.of(head.substringBefore(" · ")) ?: return null
        val name = if (" · " in head) head.substringAfter(" · ").trim() else ""
        val text = item.detail.substringAfter('\n', "").trim()
        if (text.isEmpty()) return null
        return ReadAloudMessage(item.id, from, name, text, item.created)
    }

    /** The newest message to read aloud for this person (newest saved first; the id breaks a tie). */
    fun latestMessage(items: List<CareItem>, personId: String): ReadAloudMessage? = items.asSequence()
        .filter { it.personId == personId }.mapNotNull(::message)
        .maxWithOrNull(compareBy<ReadAloudMessage> { it.created }.thenBy { it.itemId })

    /** Why a message cannot be saved yet, or null when it can. */
    fun messageError(text: String, lang: CareLang): String? = when {
        text.isBlank() -> careTr(lang, "Write or speak the message first.", "पहले संदेश लिखें या बोलें।")
        text.trim().length > MESSAGE_MAX -> careTr(lang, "Please make the message shorter.", "कृपया संदेश छोटा करें।")
        else -> null
    }

    /** The first words "Read to me" says before the message: who it is from. */
    fun messageLead(from: MessageFrom, lang: CareLang): String = when (from) {
        MessageFrom.DOCTOR -> careTr(lang, "Here is the message from your doctor", "यह आपके डॉक्टर का संदेश है")
        MessageFrom.NURSE -> careTr(lang, "Here is the message from your nurse", "यह आपकी नर्स का संदेश है")
        MessageFrom.CARE_TEAM -> careTr(lang, "Here is the message from your care team", "यह आपकी देखभाल टीम का संदेश है")
        MessageFrom.FAMILY -> careTr(lang, "Here is the message from your family", "यह आपके परिवार का संदेश है")
    }

    /** What "Read to me" says: who the message is from, then the message; or that there is none yet. */
    fun readPrompt(message: ReadAloudMessage?, lang: CareLang): String =
        if (message == null) careTr(lang, "There is no message from your doctor or nurse yet.", "अभी आपके डॉक्टर या नर्स का कोई संदेश नहीं है।")
        else messageLead(message.from, lang) + ".\n" + message.text

    // ------------------------------------------------------------ Voice notes

    /** A voice note is at most this long, so it stays small on the phone and quick for the nurse to hear. */
    const val VOICE_MAX_SECONDS = 180

    /** "0:42", "2:05": minutes and seconds for the recording timer and the saved note's title. */
    fun clock(seconds: Int): String = seconds.coerceAtLeast(0).let { s -> String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60) }

    /** The journal title of a patient's voice note (English on the record, like every saved title). */
    fun voiceNoteTitle(seconds: Int): String = "Voice note from the patient (${clock(seconds)})"

    const val VOICE_NOTE_DETAIL = "Recorded in Simple Mode. The patient's own voice is kept as audio on this phone: open this note in Sahara to listen."

    /** True when [item] is a Simple Mode voice note (its audio file is named by the item's id). */
    fun isVoiceNote(item: CareItem): Boolean = item.kind == "journal" && item.title.startsWith("Voice note from the patient (") && item.detail == VOICE_NOTE_DETAIL

    // ------------------------------------------------------------ Large text and layout

    /**
     * The smallest size (sp) a Simple Mode label steps down to so it fits whole: 18sp at the normal font size. With a
     * larger system font the floor stays at what 18sp looks like normally (18 / fontScale sp), so a long word (Tamil
     * "முன்பக்கம்", Malayalam "വിളിക്കുക") shrinks to fit instead of being cut off. Never above 18.
     */
    fun labelFloor(fontScale: Float): Float = 18f / fontScale.coerceAtLeast(1f)

    /** At this system font scale and above, a Simple button's picture goes above its words, so the words get the full width. */
    fun stackIcon(fontScale: Float): Boolean = fontScale >= 1.3f

    /** The smallest body picture on "Where does it hurt?" (dp): big enough to tap a part; below it the screen scrolls instead. */
    const val MIN_BODY_DP = 240

    /** Height for the body picture: what the other blocks leave of [viewport], never less than [min] (all in the same unit). */
    fun fillHeight(viewport: Int, used: Int, min: Int): Int = maxOf(min, viewport - used)

    /**
     * What "Where does it hurt?" says (and says again). Once a part is marked and the patient may record, it adds how to
     * say more in their own voice, since the recorder appears under the body.
     */
    fun wherePrompt(marked: Boolean, canRecord: Boolean, lang: CareLang): String {
        val ask = careTr(lang, "Where does it hurt? Touch the place on the body.", "दर्द कहाँ है? शरीर पर उस जगह को छुएँ।")
        return if (marked && canRecord) ask + " " + careTr(lang, "To say more in your own voice, tap Record your voice.", "अपनी आवाज़ में और बताने के लिए 'अपनी आवाज़ रिकॉर्ड करें' छुएँ।") else ask
    }

    /** A spoken body-part name or replay is held back while the patient's voice is being recorded, so Sahara's voice is not recorded. */
    fun speakNow(recording: Boolean): Boolean = !recording

    // ------------------------------------------------------------ Call photos

    /** A contact or person id that is safe as a file name (contact photos are files named by it). */
    fun safeFileId(id: String): Boolean = id.length in 1..80 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}
