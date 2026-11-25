package `in`.wedevit.care

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The emergency card a patient carries (clinical team, 24 Nov 2026). The printed QR holds ONLY the web link
 * (https://sahara.wedevit.in/emergency#t=<token>, see [EmergencyLinkRules]); the page reads the card from the
 * Sahara server, so nobody can hand a hospital an edited copy from a notes app. The card text built here is the
 * on-phone preview and the "has anything changed" check; [EmergencyLinkRules.card] is what is printed and published.
 */
enum class EmergencySection(val en: String, val hi: String) {
    NAME("Name", "नाम"),
    GOALS("Goals of care", "देखभाल के लक्ष्य"),
    CONTACTS("Emergency contacts", "आपातकालीन संपर्क"),
    MEDICINES("Current medicines", "अभी की दवाएँ"),
}

data class EmergencyMedicine(val name: String, val detail: String = "")
/** One medicine as a table row: name, strength (dose) and when it is taken, read from the saved medicine text. */
data class EmergencyMedicineRow(val name: String, val strength: String, val whenTaken: String)
/** [role] is the care-team role ("family", "caregiver", "clinician"); blank for older callers and counted as family. */
data class EmergencyContact(val name: String, val relationship: String, val phone: String = "", val role: String = "")
/** The treating doctor from the patient profile (or a clinician contact): name, own phone, hospital or clinic, its landline. */
data class EmergencyDoctor(val name: String, val phone: String = "", val place: String = "", val landline: String = "")
data class EmergencyGoals(val version: Int, val confirmedBy: String, val confirmedAt: Long, val whatMatters: String, val nextSteps: String, val newerUnconfirmedVersion: Int? = null)
/** [age], [conditions] and [allergies] come from the patient profile; blank/null means the family has not filled them in. */
data class EmergencySource(val name: String, val goals: EmergencyGoals?, val contacts: List<EmergencyContact>, val medicines: List<EmergencyMedicine>,
    val age: Int? = null, val conditions: String = "", val allergies: String = "", val doctor: EmergencyDoctor? = null)

/** [body] is everything except the card number/id/date line, so a changed record can be spotted. */
data class EmergencyCardText(val text: String, val body: String, val leftOut: List<String>)

object EmergencyCardRules {
    /** UTF-8 bytes of the on-phone preview text (it is no longer put in a QR code, but stays short and readable). */
    const val MAX_BYTES = 800
    private const val ALLERGIES_MAX = 100

    /** Only the profile details that are really missing; a recorded allergy is printed, never called "not recorded". */
    fun notRecorded(source: EmergencySource): String = listOfNotNull("age".takeIf { source.age == null }, "conditions".takeIf { source.conditions.isBlank() }, "allergies".takeIf { source.allergies.isBlank() }).joinToString(", ")
    /** Recorded in the profile but not printed on the card (allergies are printed). */
    fun recordedNotOnCard(source: EmergencySource): List<String> = listOfNotNull("age (recorded in app)".takeIf { source.age != null }, "conditions (recorded in app)".takeIf { source.conditions.isNotBlank() })
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

    internal fun day(at: Long, zone: ZoneId) = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(zone).format(Instant.ofEpochMilli(at))

    private val STRENGTH = Regex("(?i)\\b\\d+(?:[.,]\\d+)?\\s?(?:mg|mcg|µg|g|ml|iu|units?|%)(?:\\s?/\\s?\\d*\\s?(?:ml|h|hr|hours?))?(?![a-z])")
    private val LABELS = listOf("Strength: ", "Dose: ", "How often: ", "Frequency: ", "Times: ", "Food: ", "How much each time: ", "Form: ")
    private fun labelled(detail: String, vararg labels: String): String = detail.lineSequence().map(String::trim)
        .firstNotNullOfOrNull { line -> labels.firstOrNull { line.startsWith(it) }?.let { line.removePrefix(it).trim() } }.orEmpty()

    /**
     * Name, strength and when, from the medicine's saved text. The app saves labelled lines ("Strength: ", "Dose: ",
     * "How often: ", "Frequency: ", "Times: ", "Food: "); older free text gives its first instruction as "when" and a
     * strength found in the name or text ("10 mg"). Nothing is guessed beyond what the family saved.
     */
    fun medicineRow(m: EmergencyMedicine): EmergencyMedicineRow {
        val name = m.name.replace(Regex("\\s+"), " ").trim()
        if (m.detail.lineSequence().any { line -> LABELS.any { line.trim().startsWith(it) } }) {
            val strength = labelled(m.detail, "Strength: ", "Dose: ").ifBlank { STRENGTH.find(name)?.value.orEmpty() }
            val whenTaken = listOf(labelled(m.detail, "How much each time: "), labelled(m.detail, "How often: ", "Frequency: "), labelled(m.detail, "Times: "), labelled(m.detail, "Food: "))
                .filter(String::isNotBlank).joinToString(", ")
            return EmergencyMedicineRow(name, strength, whenTaken)
        }
        val first = m.detail.lineSequence().firstOrNull().orEmpty().substringBefore(". ").trim().trimEnd('.')
        val strength = STRENGTH.find(name)?.value ?: STRENGTH.find(first)?.value.orEmpty()
        return EmergencyMedicineRow(name, strength.trim(), first)
    }

    /** The next of kin: the first contact who is not a clinician (contacts come family first, with a phone first). */
    fun nextOfKin(source: EmergencySource): EmergencyContact? = source.contacts.firstOrNull { it.role != "clinician" && it.name.isNotBlank() }
    /** The treating doctor: the patient profile's doctor, else the first clinician contact. */
    fun doctorOf(source: EmergencySource): EmergencyDoctor? = source.doctor?.takeIf { it.name.isNotBlank() || it.phone.isNotBlank() || it.place.isNotBlank() }
        ?: source.contacts.firstOrNull { it.role == "clinician" && it.name.isNotBlank() }?.let { EmergencyDoctor(it.name, it.phone) }
    /** Contacts other than the next of kin and the doctor already shown from a clinician contact. */
    fun otherContacts(source: EmergencySource): List<EmergencyContact> {
        val kin = nextOfKin(source); val fromContact = source.doctor?.takeIf { it.name.isNotBlank() || it.phone.isNotBlank() || it.place.isNotBlank() } == null
        val doctorContact = if (fromContact) source.contacts.firstOrNull { it.role == "clinician" && it.name.isNotBlank() } else null
        return source.contacts.filter { it !== kin && it !== doctorContact }
    }

    private fun medicineLine(m: EmergencyMedicine): String {
        val row = medicineRow(m)
        val head = if (row.strength.isBlank() || row.name.contains(row.strength, ignoreCase = true)) row.name else "${row.name} ${row.strength}"
        return clip(if (row.whenTaken.isBlank()) head else "$head - ${row.whenTaken}", MEDICINE_MAX)
    }
    private fun contactLine(c: EmergencyContact) = clip(listOf(c.name, "(${c.relationship})", c.phone).filter { it.isNotBlank() && it != "()" }.joinToString(" "), CONTACT_MAX)
    private fun doctorLine(d: EmergencyDoctor) = clip("Doctor: " + listOf(d.name, d.phone, d.place, if (d.landline.isBlank()) "" else "landline ${d.landline}").filter(String::isNotBlank).joinToString(", "), CONTACT_MAX + 30)
    private fun goalsText(g: EmergencyGoals) = listOf("What matters: " to g.whatMatters, "Next steps: " to g.nextSteps)
        .filter { it.second.isNotBlank() }.joinToString(" ") { it.first + it.second.replace(Regex("\\s*\\n+\\s*"), "; ").replace(Regex("\\s+"), " ").trim() }

    /**
     * Builds the card text from the sections the family ticked. If it is too long for
     * [maxBytes], medicines go first (from the end of the list), then the goals text is
     * shortened, then contacts are dropped. Everything removed is listed in [EmergencyCardText.leftOut]
     * and named on the card itself, so the reader knows the card is not complete.
     */
    /** Failsafe for old printed cards (team meeting, 22 Nov): every card states a valid-until date, 90 days (3 months) after it was made; the web page says so too. */
    const val VALID_DAYS = 90L
    /** The "is everything still the same?" check shows from this many days before the valid-until date (clinical team, 24 Nov). */
    const val RENEW_NOTICE_DAYS = 14L
    fun build(source: EmergencySource, sections: Set<EmergencySection>, cardNumber: Int, cardId: String, madeAt: Long, zone: ZoneId = ZoneId.systemDefault(), maxBytes: Int = MAX_BYTES): EmergencyCardText {
        require(sections.isNotEmpty()) { "Choose at least one part to put on the card." }
        val header = "SAHARA EMERGENCY CARD\nCard $cardNumber · ID $cardId · made ${day(madeAt, zone)} · VALID UNTIL ${day(madeAt + VALID_DAYS * 86_400_000L, zone)}\nScan the QR for this card on the Sahara website; it says if a newer card exists. After the valid-until date check with the family or doctor."
        val goals = source.goals.takeIf { EmergencySection.GOALS in sections }
        var meds = if (EmergencySection.MEDICINES in sections) source.medicines.map(::medicineLine) else emptyList()
        // Next of kin first, then the treating doctor, then the other contacts (dropped from the end if there is no room).
        val kin = nextOfKin(source); val doctor = doctorOf(source)
        var contacts = if (EmergencySection.CONTACTS in sections) buildList {
            kin?.let { add("Next of kin: " + contactLine(it)) }
            doctor?.let { add(doctorLine(it)) }
            otherContacts(source).forEach { add(contactLine(it)) }
        } else emptyList()
        val fullGoals = goals?.let(::goalsText).orEmpty()
        var goalsShown: String? = goals?.let { fullGoals }
        var droppedMeds = 0; var droppedContacts = 0; var goalsCut = false; var goalsDropped = false
        val notChosen = EmergencySection.entries.filter { it !in sections }.map { it.en.substringBefore(" (").lowercase() }
        val missing = buildList {
            if (EmergencySection.GOALS in sections && goals == null) add("no confirmed goals-of-care note")
            if (EmergencySection.CONTACTS in sections && kin == null) add("no contacts saved")
            // Shirin, 21 Nov: the emergency team should be able to call the guardian, not only read names.
            if (EmergencySection.CONTACTS in sections && kin != null && kin.phone.isBlank()) add("no phone number saved for the guardian")
            if (EmergencySection.CONTACTS in sections && doctor == null) add("no treating doctor saved")
            if (EmergencySection.MEDICINES in sections && source.medicines.isEmpty()) add("no current medicines listed")
            addAll(recordedNotOnCard(source))
        }
        val notRecorded = notRecorded(source)
        var allergiesShown = clip(source.allergies, ALLERGIES_MAX)
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
            if (allergiesShown.isNotEmpty()) append("\nALLERGIES (as listed by family): ").append(allergiesShown)
            if (notRecorded.isNotEmpty()) append("\nNOT RECORDED IN APP: $notRecorded")
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
        // Allergies are shortened last and never dropped; the "…" shows the reader there is more.
        var allergyChars = allergiesShown.codePointCount(0, allergiesShown.length)
        while (!fits() && allergyChars > 12) { allergyChars--; allergiesShown = clip(source.allergies, allergyChars) }
        val body = body()
        check(bytes(header + body) <= maxBytes) { "The card text is too long." }
        return EmergencyCardText(header + body, body.trimStart('\n'), leftOut())
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
/**
 * One card as saved on this phone. [snapshot] is the exact web copy (JSON from [EmergencyLinkRules.card]) taken when the
 * card was made: the printed tables and the published version are both drawn from it, so paper and website match.
 * Blank for cards saved before 24 Nov 2026 (they are drawn from [text] until the family updates the card).
 */
/** [renewedAt]: when the family last said "Yes, still the same" (24 Nov); the 3 months then count from it. Null for a card never renewed. */
data class EmergencyCardRecord(val personId: String, val id: String, val number: Int, val madeAt: Long, val sections: Set<EmergencySection>, val text: String, val body: String, val leftOut: List<String>, val replaced: List<ReplacedEmergencyCard> = emptyList(), val link: EmergencyLink? = null, val snapshot: String = "", val renewedAt: Long? = null) {
    companion object {
        /**
         * A first card, or a replacement: new id, next number, and the old card listed as outdated. The new card has no
         * link yet: each card is published as its own version with its own token (24 Nov), so an older printed card's QR
         * says "not the latest version" instead of silently showing different content from the paper.
         */
        fun make(personId: String, source: EmergencySource, sections: Set<EmergencySection>, previous: EmergencyCardRecord?, now: Long, zone: ZoneId = ZoneId.systemDefault(), id: String = EmergencyCardRules.newCardId()): EmergencyCardRecord {
            require(previous == null || previous.personId == personId) { "This card belongs to a different person." }
            require(previous == null || previous.id != id) { "A replacement card needs a new ID." }
            val number = (previous?.number ?: 0) + 1
            val card = EmergencyCardRules.build(source, sections, number, id, now, zone)
            val replaced = previous?.let { it.replaced + ReplacedEmergencyCard(it.id, it.number, it.madeAt, now) }.orEmpty()
            val record = EmergencyCardRecord(personId, id, number, now, sections, card.text, card.body, card.leftOut, replaced, null)
            return record.copy(snapshot = EmergencyLinkRules.card(source, record).toString())
        }
        fun decode(json: String): EmergencyCardRecord {
            val j = org.json.JSONObject(json)
            fun strings(name: String) = j.optJSONArray(name)?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
            val replaced = j.optJSONArray("replaced")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { ReplacedEmergencyCard(it.getString("id"), it.getInt("number"), it.getLong("madeAt"), it.getLong("replacedAt")) } }.orEmpty()
            val link = j.optJSONObject("link")?.let { l -> runCatching { val created = l.getLong("createdAt")
                EmergencyLink(l.getString("token").also { EmergencyLinkRules.url(it) }, created, l.getLong("expiresAt"), l.optInt("version", 1).coerceAtLeast(1), l.optLong("publishedAt", created)) }.getOrNull() }
            return EmergencyCardRecord(j.getString("personId"), j.getString("id"), j.getInt("number"), j.getLong("madeAt"), strings("sections").mapNotNull { s -> EmergencySection.entries.firstOrNull { it.name == s } }.toSet(), j.getString("text"), j.getString("body"), strings("leftOut"), replaced, link, j.optString("snapshot", ""), if (j.has("renewedAt")) j.optLong("renewedAt") else null)
        }
    }
    fun encode(): String = org.json.JSONObject().put("personId", personId).put("id", id).put("number", number).put("madeAt", madeAt)
        .put("sections", org.json.JSONArray(sections.map { it.name })).put("text", text).put("body", body).put("leftOut", org.json.JSONArray(leftOut))
        .put("replaced", org.json.JSONArray(replaced.map { org.json.JSONObject().put("id", it.id).put("number", it.number).put("madeAt", it.madeAt).put("replacedAt", it.replacedAt) }))
        .also { j -> link?.let { j.put("link", org.json.JSONObject().put("token", it.token).put("createdAt", it.createdAt).put("expiresAt", it.expiresAt).put("version", it.version).put("publishedAt", it.publishedAt)) } }
        .also { j -> if (snapshot.isNotBlank()) j.put("snapshot", snapshot) }.also { j -> renewedAt?.let { j.put("renewedAt", it) } }.toString()
    /** The web copy to publish: the one taken when the card was made, or (older saved cards) built from today's records. */
    fun webCard(source: EmergencySource): org.json.JSONObject = snapshot.takeIf(String::isNotBlank)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } ?: EmergencyLinkRules.card(source, this)
    /** Valid-until date of this card: [EmergencyCardRules.VALID_DAYS] after it was made, or after it was last renewed. */
    val validUntil: Long get() = (renewedAt ?: madeAt) + EmergencyCardRules.VALID_DAYS * 86_400_000L
    /** True from [EmergencyCardRules.RENEW_NOTICE_DAYS] days before the valid-until date, and after it. */
    fun renewalDue(now: Long): Boolean = now >= validUntil - EmergencyCardRules.RENEW_NOTICE_DAYS * 86_400_000L
    /**
     * "Yes, still the same" (24 Nov): the same card (same ID, number, sections and content), valid for 3 more months from [now].
     * Only the valid-until date changes, in the saved web copy and the preview text. The link is kept here; the screen then
     * publishes this same content as a new version, so the website shows the new date.
     */
    fun renew(now: Long, zone: ZoneId = ZoneId.systemDefault()): EmergencyCardRecord {
        val next = copy(renewedAt = now)
        val oldUntil = "VALID UNTIL " + EmergencyCardRules.day(validUntil, zone)
        val newUntil = "VALID UNTIL " + EmergencyCardRules.day(next.validUntil, zone)
        val web = snapshot.takeIf(String::isNotBlank)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
            ?.put("validUntil", next.validUntil)?.put("renewedAt", now)?.toString() ?: snapshot
        return next.copy(text = text.replaceFirst(oldUntil, newUntil), snapshot = web)
    }
    /** True when today's records would print a different card than the one saved. */
    fun outOfDate(source: EmergencySource, zone: ZoneId = ZoneId.systemDefault()): Boolean = EmergencyCardRules.build(source, sections, number, id, madeAt, zone).body != body
}

/**
 * The two tables on the printed card (24 Nov): who to call, and the current medicines. Read from the card's web copy
 * ([EmergencyCardRecord.snapshot]) so the paper and the page show the same rows. Version 1 copies (before 24 Nov) have
 * no next-of-kin or doctor keys: their first contact is shown as next of kin.
 */
object EmergencyCardTables {
    data class Row(val who: String, val name: String, val phone: String)
    data class MedicineRow(val name: String, val strength: String, val whenTaken: String)
    const val NOT_SAVED = "not saved"

    private fun org.json.JSONObject.text(key: String) = if (isNull(key)) "" else optString(key).replace(Regex("\\s+"), " ").trim()
    private fun org.json.JSONArray?.objects(): List<org.json.JSONObject> = this?.let { a -> (0 until a.length()).mapNotNull(a::optJSONObject) }.orEmpty()

    fun contacts(card: org.json.JSONObject): List<Row> = buildList {
        val all = card.optJSONArray("contacts").objects()
        val kin = card.optJSONObject("nextOfKin") ?: all.firstOrNull()
        kin?.let { k -> add(Row("Next of kin", listOf(k.text("name"), k.text("relationship").takeIf(String::isNotBlank)?.let { "($it)" }.orEmpty()).filter(String::isNotBlank).joinToString(" "), k.text("phone").ifBlank { NOT_SAVED })) }
        card.optJSONObject("doctor")?.let { d ->
            if (d.text("name").isNotBlank() || d.text("phone").isNotBlank()) add(Row("Treating doctor", d.text("name").ifBlank { NOT_SAVED }, d.text("phone").ifBlank { NOT_SAVED }))
            if (d.text("place").isNotBlank() || d.text("landline").isNotBlank()) add(Row("Hospital / clinic", d.text("place").ifBlank { NOT_SAVED }, d.text("landline").ifBlank { NOT_SAVED }))
        }
        val others = if (card.has("otherContacts")) card.optJSONArray("otherContacts").objects() else all.drop(1)
        others.forEach { o -> add(Row(o.text("relationship").ifBlank { "Contact" }, o.text("name"), o.text("phone").ifBlank { NOT_SAVED })) }
    }

    fun medicines(card: org.json.JSONObject): List<MedicineRow> = card.optJSONArray("medicines").objects().map { m ->
        if (m.has("strength") || m.has("when")) MedicineRow(m.text("name"), m.text("strength"), m.text("when"))
        else EmergencyCardRules.medicineRow(EmergencyMedicine(m.text("name"), if (m.isNull("detail")) "" else m.optString("detail"))).let { MedicineRow(it.name, it.strength, it.whenTaken) }
    }

    fun sections(card: org.json.JSONObject): Set<EmergencySection> = card.optJSONArray("sections")?.let { a -> (0 until a.length()).mapNotNull { i -> EmergencySection.entries.firstOrNull { it.name == a.optString(i) } } }.orEmpty().toSet()
}
