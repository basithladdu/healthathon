package `in`.wedevit.care

import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Offline emergency card: plain text inside a QR code. Any phone camera can read it
 * without internet. Nothing here talks to a server or a hospital system.
 */
enum class EmergencySection(val en: String, val hi: String) {
    NAME("Name", "नाम"),
    GOALS("Goals of care", "देखभाल के लक्ष्य"),
    CONTACTS("Emergency contacts", "आपातकालीन संपर्क"),
    MEDICINES("Current medicines", "अभी की दवाएँ"),
}

data class EmergencyMedicine(val name: String, val detail: String = "")
data class EmergencyContact(val name: String, val relationship: String, val phone: String = "")
data class EmergencyGoals(val version: Int, val confirmedBy: String, val confirmedAt: Long, val whatMatters: String, val nextSteps: String, val newerUnconfirmedVersion: Int? = null)
data class EmergencySource(val name: String, val goals: EmergencyGoals?, val contacts: List<EmergencyContact>, val medicines: List<EmergencyMedicine>)

/** [body] is everything except the card number/id/date line, so a changed record can be spotted. */
data class EmergencyCardText(val text: String, val body: String, val leftOut: List<String>)

object EmergencyCardRules {
    /** UTF-8 bytes. Keeps the code at QR version 25 or lower (ECC M) so a printed card still scans. */
    const val MAX_BYTES = 800
    val ECC: ErrorCorrectionLevel = ErrorCorrectionLevel.M
    const val NOT_IN_APP = "age, conditions, allergies"
    const val OFFLINE_EN = "This card works without internet; it is not linked to any hospital system."
    const val OFFLINE_HI = "यह कार्ड बिना इंटरनेट के काम करता है; यह किसी अस्पताल के सिस्टम से जुड़ा नहीं है।"
    private const val NAME_MAX = 80
    private const val MEDICINE_MAX = 70
    private const val CONTACT_MAX = 70

    fun bytes(text: String) = text.toByteArray(Charsets.UTF_8).size

    /** Cuts by whole characters (never in the middle of a Hindi letter) and marks the cut. */
    fun clip(value: String, max: Int): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.codePointCount(0, clean.length) <= max) return clean
        return clean.substring(0, clean.offsetByCodePoints(0, (max - 1).coerceAtLeast(0))).trimEnd() + "…"
    }

    private fun day(at: Long, zone: ZoneId) = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(at))

    private fun medicineLine(m: EmergencyMedicine): String {
        val first = m.detail.lineSequence().firstOrNull().orEmpty().substringBefore(". ").trim().trimEnd('.')
        return clip(if (first.isBlank()) m.name else "${m.name} - $first", MEDICINE_MAX)
    }
    private fun contactLine(c: EmergencyContact) = clip(listOf(c.name, "(${c.relationship})", c.phone).filter { it.isNotBlank() && it != "()" }.joinToString(" "), CONTACT_MAX)
    private fun goalsText(g: EmergencyGoals) = listOf("What matters: " to g.whatMatters, "Next steps: " to g.nextSteps)
        .filter { it.second.isNotBlank() }.joinToString(" ") { it.first + it.second.replace(Regex("\\s*\\n+\\s*"), "; ").replace(Regex("\\s+"), " ").trim() }

    /**
     * Builds the card text from the sections the family ticked. If it is too long for
     * [maxBytes], medicines go first (from the end of the list), then the goals text is
     * shortened, then contacts are dropped. Everything removed is listed in [EmergencyCardText.leftOut]
     * and named on the card itself, so the reader knows the card is not complete.
     */
    fun build(source: EmergencySource, sections: Set<EmergencySection>, cardNumber: Int, cardId: String, madeAt: Long, zone: ZoneId = ZoneId.systemDefault(), maxBytes: Int = MAX_BYTES): EmergencyCardText {
        require(sections.isNotEmpty()) { "Choose at least one part to put on the card." }
        val header = "SAHARA EMERGENCY CARD\nCard $cardNumber · ID $cardId · made ${day(madeAt, zone)}\nOffline card, not linked to any hospital system. Ask the family if a newer card exists."
        val goals = source.goals.takeIf { EmergencySection.GOALS in sections }
        var meds = if (EmergencySection.MEDICINES in sections) source.medicines.map(::medicineLine) else emptyList()
        var contacts = if (EmergencySection.CONTACTS in sections) source.contacts.map(::contactLine) else emptyList()
        val fullGoals = goals?.let(::goalsText).orEmpty()
        var goalsShown: String? = goals?.let { fullGoals }
        var droppedMeds = 0; var droppedContacts = 0; var goalsCut = false; var goalsDropped = false
        val notChosen = EmergencySection.entries.filter { it !in sections }.map { it.en.substringBefore(" (").lowercase() }
        val missing = buildList {
            if (EmergencySection.GOALS in sections && goals == null) add("no confirmed goals-of-care note")
            if (EmergencySection.CONTACTS in sections && source.contacts.isEmpty()) add("no contacts saved")
            if (EmergencySection.MEDICINES in sections && source.medicines.isEmpty()) add("no current medicines listed")
        }
        fun leftOut() = buildList {
            if (droppedMeds > 0) add("$droppedMeds medicine${if (droppedMeds == 1) "" else "s"} (no room)")
            if (goalsDropped) add("goals-of-care note (no room)") else if (goalsCut) add("rest of the goals-of-care note (no room)")
            if (droppedContacts > 0) add("$droppedContacts contact${if (droppedContacts == 1) "" else "s"} (no room)")
            notChosen.forEach { add("$it (family chose not to include)") }
            missing.forEach(::add)
        }
        fun body() = buildString {
            if (EmergencySection.NAME in sections) append("\nNAME: ").append(clip(source.name, NAME_MAX))
            if (goals != null && goalsShown != null) {
                append("\nGOALS OF CARE (note v${goals.version}, confirmed in app by ${clip(goals.confirmedBy, 40)} on ${day(goals.confirmedAt, zone)}")
                goals.newerUnconfirmedVersion?.let { append("; newer note v$it not yet confirmed") }
                append("):\n").append(goalsShown)
            }
            if (contacts.isNotEmpty()) { append("\nCONTACTS:"); contacts.forEach { append("\n- ").append(it) } }
            if (meds.isNotEmpty()) { append("\nMEDICINES (as listed by family):"); meds.forEach { append("\n- ").append(it) } }
            append("\nNOT RECORDED IN APP: $NOT_IN_APP")
            leftOut().takeIf { it.isNotEmpty() }?.let { append("\nNOT ON THIS CARD: ").append(it.joinToString("; ")) }
        }
        fun fits() = bytes(header + body()) <= maxBytes
        while (!fits() && meds.isNotEmpty()) { meds = meds.dropLast(1); droppedMeds++ }
        if (!fits() && goalsShown != null) {
            goalsCut = true
            val total = fullGoals.codePointCount(0, fullGoals.length)
            var low = 0; var high = total
            while (low < high) { val mid = (low + high + 1) / 2; goalsShown = clip(fullGoals, mid); if (fits()) low = mid else high = mid - 1 }
            if (low < 40) { goalsShown = null; goalsDropped = true } else goalsShown = clip(fullGoals, low)
        }
        while (!fits() && contacts.isNotEmpty()) { contacts = contacts.dropLast(1); droppedContacts++ }
        val body = body()
        check(bytes(header + body) <= maxBytes) { "The card text is too long." }
        return EmergencyCardText(header + body, body.trimStart('\n'), leftOut())
    }

    /** The QR symbol for [text]: UTF-8 so Hindi names survive, ECC M so a scuffed print still scans. */
    fun qr(text: String): QRCode {
        require(bytes(text) <= MAX_BYTES) { "The card text is too long for a QR code." }
        return Encoder.encode(text, ECC, mapOf(EncodeHintType.CHARACTER_SET to "UTF-8"))
    }

    /** Latest note version that the patient confirmed in the app (exact version and content). */
    fun latestConfirmed(versions: List<CareVersion>, acknowledgements: List<Acknowledgement>, trusted: (Acknowledgement) -> Boolean = { true }): EmergencyGoals? {
        val newest = versions.maxByOrNull { it.version }
        val pick = versions.sortedByDescending { it.version }.firstNotNullOfOrNull { v ->
            acknowledgements.filter { it.versionId == v.id && it.contentHash == v.contentHash && it.relationship == "Patient" && trusted(it) }.minByOrNull { it.at }?.let { v to it }
        } ?: return null
        val (v, ack) = pick
        return EmergencyGoals(v.version, "${ack.name} (patient)", ack.at, v.priorities, v.nextSteps, newest?.version?.takeIf { it > v.version })
    }

    fun newCardId(): String = java.util.UUID.randomUUID().toString().replace("-", "").take(8).uppercase(Locale.ROOT)
}

data class ReplacedEmergencyCard(val id: String, val number: Int, val madeAt: Long, val replacedAt: Long)
data class EmergencyCardRecord(val personId: String, val id: String, val number: Int, val madeAt: Long, val sections: Set<EmergencySection>, val text: String, val body: String, val leftOut: List<String>, val replaced: List<ReplacedEmergencyCard> = emptyList(), val link: EmergencyLink? = null) {
    companion object {
        /** A first card, or a replacement: new id, next number, and the old card listed as outdated. */
        fun make(personId: String, source: EmergencySource, sections: Set<EmergencySection>, previous: EmergencyCardRecord?, now: Long, zone: ZoneId = ZoneId.systemDefault(), id: String = EmergencyCardRules.newCardId()): EmergencyCardRecord {
            require(previous == null || previous.personId == personId) { "This card belongs to a different person." }
            require(previous == null || previous.id != id) { "A replacement card needs a new ID." }
            val number = (previous?.number ?: 0) + 1
            val card = EmergencyCardRules.build(source, sections, number, id, now, zone)
            val replaced = previous?.let { it.replaced + ReplacedEmergencyCard(it.id, it.number, it.madeAt, now) }.orEmpty()
            // The web link stays the same across card updates, so printed copies keep opening the latest one.
            return EmergencyCardRecord(personId, id, number, now, sections, card.text, card.body, card.leftOut, replaced, previous?.link)
        }
        fun decode(json: String): EmergencyCardRecord {
            val j = org.json.JSONObject(json)
            fun strings(name: String) = j.optJSONArray(name)?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
            val replaced = j.optJSONArray("replaced")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { ReplacedEmergencyCard(it.getString("id"), it.getInt("number"), it.getLong("madeAt"), it.getLong("replacedAt")) } }.orEmpty()
            val link = j.optJSONObject("link")?.let { l -> runCatching { EmergencyLink(l.getString("token").also { EmergencyLinkRules.url(it) }, l.getLong("createdAt"), l.getLong("expiresAt")) }.getOrNull() }
            return EmergencyCardRecord(j.getString("personId"), j.getString("id"), j.getInt("number"), j.getLong("madeAt"), strings("sections").mapNotNull { s -> EmergencySection.entries.firstOrNull { it.name == s } }.toSet(), j.getString("text"), j.getString("body"), strings("leftOut"), replaced, link)
        }
    }
    fun encode(): String = org.json.JSONObject().put("personId", personId).put("id", id).put("number", number).put("madeAt", madeAt)
        .put("sections", org.json.JSONArray(sections.map { it.name })).put("text", text).put("body", body).put("leftOut", org.json.JSONArray(leftOut))
        .put("replaced", org.json.JSONArray(replaced.map { org.json.JSONObject().put("id", it.id).put("number", it.number).put("madeAt", it.madeAt).put("replacedAt", it.replacedAt) }))
        .also { j -> link?.let { j.put("link", org.json.JSONObject().put("token", it.token).put("createdAt", it.createdAt).put("expiresAt", it.expiresAt)) } }.toString()
    /** True when today's records would print a different card than the one saved. */
    fun outOfDate(source: EmergencySource, zone: ZoneId = ZoneId.systemDefault()): Boolean = EmergencyCardRules.build(source, sections, number, id, madeAt, zone).body != body
}
