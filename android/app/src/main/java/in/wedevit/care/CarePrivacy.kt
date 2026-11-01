package `in`.wedevit.care

import org.json.JSONObject

/**
 * Strips contact and ID details from free text before it leaves the phone for the shared
 * account (care team and doctor). Pure Kotlin so every pattern is unit tested.
 *
 * Removed: Indian mobile and landline numbers, emails, Aadhaar-like 12-digit numbers, PAN, and
 * addresses that end in a PIN code (when they start with a house/flat number or follow a comma),
 * plus a PIN written after "PIN"/"pincode"/"पिन". Names are NOT changed (owner decision pending).
 */
object CarePrivacy {
    const val PHONE = "[phone]"
    const val EMAIL = "[email]"
    const val AADHAAR = "[aadhaar]"
    const val PAN = "[PAN]"
    const val ADDRESS = "[address]"
    const val PIN = "[pincode]"

    const val SYMPTOM_NOTICE_EN = "Shared with your care team and doctor: this note, with phone numbers, emails, Aadhaar, PAN and addresses removed."
    const val SYMPTOM_NOTICE_HI = "आपकी देखभाल टीम और डॉक्टर को यह नोट भेजा जाता है; फ़ोन नंबर, ईमेल, आधार, पैन और पते हटा दिए जाते हैं।"
    const val CONVERSATION_NOTICE_EN = "Sent for clinician review: the original words and sections, with phone numbers, emails, Aadhaar, PAN and addresses removed."
    const val CONVERSATION_NOTICE_HI = "डॉक्टर की समीक्षा के लिए मूल शब्द और हिस्से भेजे जाते हैं; फ़ोन नंबर, ईमेल, आधार, पैन और पते हटा दिए जाते हैं।"

    internal data class Span(val start: Int, val end: Int, val token: String)

    private const val SEP = "[ \\t.-]?"
    private const val HOUSE = "(?:#\\s*|\\b(?:h\\.?\\s?no|house(?:\\s?no)?|flat(?:\\s?no)?|door(?:\\s?no)?|d\\.?\\s?no|plot(?:\\s?no)?)\\.?\\s*[:#-]?\\s*)\\d+[A-Za-z]?(?:[/-][0-9A-Za-z]+)*" +
        "|\\b\\d+[A-Za-z]?(?:[/-][0-9A-Za-z]+)+"
    private const val PIN_DIGITS = "[1-9]\\d{2} ?\\d{3}(?![\\d])"
    private const val PIN_WORD = "(?:pin\\s?code|pincode|pin|पिन\\s?कोड|पिनकोड|पिन)\\s*(?:no\\.?)?\\s*[:.-]?\\s*"

    // Order matters: earlier patterns win when matches overlap.
    private val patterns = listOf(
        Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}") to EMAIL,
        // House/flat number, up to six comma-separated parts, then a PIN code.
        Regex("(?i)(?:$HOUSE)\\s*,(?:[^,\\n;]{1,60},){0,6}[^,\\n;\\d]{0,60}?[\\s,-]*(?:$PIN_WORD)?$PIN_DIGITS") to ADDRESS,
        // ", City - 500034" / ", City, PIN 500 034". A bare ", City 500034" is skipped: it also matches lab values.
        Regex("(?i)(?<=,)\\s*[\\p{L}][\\p{L}\\p{M} .]{1,40}?(?:\\s*-\\s*|[\\s,]+$PIN_WORD)$PIN_DIGITS") to ADDRESS,
        Regex("(?<![\\d+])[2-9]\\d{3}[ -]?\\d{4}[ -]?\\d{4}(?![ -]?\\d)") to AADHAAR,
        // Mobile: optional +91 / 0091 / 91 / 0, then 10 digits starting 6-9 with single spaces, dots or dashes.
        Regex("(?<![\\w+])(?:(?:\\+|00)91$SEP|91[ -]|0)?[6-9](?:\\d$SEP){8}\\d(?![\\d])") to PHONE,
        // Landline: +91 or a leading 0, STD code (2-4 digits), separator, then 6-8 digits.
        Regex("(?<![\\w+])(?:(?:\\+|00)91$SEP\\(?|\\(?0)[1-9]\\d{1,3}\\)?[ -]\\d{3,4}[ -]?\\d{3,4}(?![\\d])") to PHONE,
        Regex("(?i)(?<![A-Za-z0-9])[A-Z]{3}[ABCFGHLJPT][A-Z]\\d{4}[A-Z](?![A-Za-z0-9])") to PAN,
        Regex("(?i)(?<![\\p{L}])$PIN_WORD$PIN_DIGITS") to PIN,
    )

    internal fun spans(text: String): List<Span> {
        val chosen = mutableListOf<Span>()
        patterns.forEach { (regex, token) ->
            regex.findAll(text).forEach { m ->
                var start = m.range.first; val end = m.range.last + 1
                while (start < end && text[start].isWhitespace()) start++
                if (token == PHONE && !phoneDigitsOk(text.substring(start, end))) return@forEach
                if (start < end && chosen.none { start < it.end && it.start < end }) chosen += Span(start, end, token)
            }
        }
        return chosen.sortedBy { it.start }
    }

    // A phone match must hold 10-14 digits; this keeps dates, doses and short codes out.
    private fun phoneDigitsOk(match: String) = match.count(Char::isDigit) in 10..14

    fun redact(text: String): String = apply(text, spans(text))

    private fun apply(text: String, spans: List<Span>): String {
        if (spans.isEmpty()) return text
        val out = StringBuilder(); var at = 0
        spans.forEach { out.append(text, at, it.start).append(it.token); at = it.end }
        return out.append(text, at, text.length).toString()
    }

    /** Symptom check-ins keep their structure; only the typed text is redacted. Other entry kinds go unchanged. */
    fun entryForUpload(item: CareItem): CareItem {
        // A medicine side-effect note (a journal entry) keeps its structure; only the typed words and the title are redacted.
        if (item.kind == "journal" && SideEffectCodec.isStructured(item.detail)) {
            val n = SideEffectCodec.decode(item.detail) ?: return item
            val safe = n.copy(other = redact(n.other)); val title = redact(item.title)
            return if (safe == n && title == item.title) item else item.copy(title = title, detail = SideEffectCodec.encode(safe))
        }
        // The patient profile keeps its structure and phone field; only the free-text fields are redacted.
        if (item.kind == "journal" && PatientProfileCodec.isStructured(item.detail)) {
            val p = PatientProfileCodec.decode(item.detail) ?: return item
            val safe = p.copy(illness = redact(p.illness), allergies = redact(p.allergies), address = redact(p.address))
            return if (safe == p) item else item.copy(detail = PatientProfileCodec.encode(safe))
        }
        if (item.kind != "checkin") return item
        if (DistressCodec.isStructured(item.detail)) return DistressCodec.decode(item.detail)?.let { d -> d.copy(other = redact(d.other)).takeIf { it != d }?.let { item.copy(detail = DistressCodec.encode(it)) } } ?: item
        val note = SymptomNoteCodec.decode(item.detail)
        val safeNote = note?.let { it.copy(symptom = redact(it.symptom), note = redact(it.note), painLocation = redact(it.painLocation), author = redact(it.author)) }
        val detail = when { note == null -> if (SymptomNoteCodec.isStructured(item.detail)) item.detail else redact(item.detail); safeNote == note -> item.detail; else -> SymptomNoteCodec.encode(safeNote!!) }
        val title = redact(item.title)
        return if (detail == item.detail && title == item.title) item else item.copy(title = title, detail = detail)
    }

    /**
     * Redacts the original words, every section, and the review context. Source quotations and
     * hashes in the context are rebuilt against the redacted words so they still validate.
     * Returns the same instance when nothing needs removing.
     */
    fun conversationForUpload(value: Conversation): Conversation {
        val sourceSpans = spans(value.source)
        val sections = NoteRules.values(value)
        val redactedSections = sections.map(::redact)
        val context = value.contextJson.takeIf(String::isNotBlank)?.let(::JSONObject)
        val contextChanged = context?.let { contextNeedsRedaction(it) } ?: false
        if (sourceSpans.isEmpty() && redactedSections == sections && !contextChanged) return value
        val source = apply(value.source, sourceSpans)
        val safe = value.copy(source = source, priorities = redactedSections[0], participants = redactedSections[1], topics = redactedSections[2], questions = redactedSections[3], nextSteps = redactedSections[4])
        if (context == null) return safe
        val oldHash = DoctorConversationContext.sha256(value.source.toByteArray(Charsets.UTF_8))
        val newBytes = source.toByteArray(Charsets.UTF_8)
        val newHash = DoctorConversationContext.sha256(newBytes)
        val byteSpans = byteSpans(value.source, sourceSpans)
        if (context.optString("sourceHash") == oldHash) context.put("sourceHash", newHash)
        if (context.has("recorderLabel") && context.get("recorderLabel") is String) context.put("recorderLabel", redact(context.getString("recorderLabel")))
        context.optJSONArray("goals")?.let { goals ->
            for (i in 0 until goals.length()) {
                val goal = goals.getJSONObject(i)
                if (goal.get("answer") is String) goal.put("answer", redact(goal.getString("answer")))
                val evidence = goal.optJSONArray("evidence") ?: continue
                for (k in 0 until evidence.length()) {
                    val e = evidence.getJSONObject(k)
                    val start = mapOffset(e.getInt("start"), byteSpans, end = false); val end = mapOffset(e.getInt("end"), byteSpans, end = true)
                    e.put("start", start).put("end", end).put("quote", String(newBytes.copyOfRange(start, end), Charsets.UTF_8))
                }
            }
        }
        context.optJSONObject("englishDraft")?.let { draft ->
            draft.put("text", redact(draft.getString("text")))
            if (draft.optString("sourceHash") == oldHash) draft.put("sourceHash", newHash)
        }
        context.optJSONObject("transcription")?.let { t -> if (!t.isNull("sourceHash") && t.optString("sourceHash") == oldHash) t.put("sourceHash", newHash) }
        return safe.copy(contextJson = context.toString())
    }

    private fun contextNeedsRedaction(context: JSONObject): Boolean {
        if (context.opt("recorderLabel").let { it is String && spans(it).isNotEmpty() }) return true
        if (context.optJSONObject("englishDraft")?.optString("text")?.let { spans(it).isNotEmpty() } == true) return true
        val goals = context.optJSONArray("goals") ?: return false
        return (0 until goals.length()).any { goals.getJSONObject(it).opt("answer").let { a -> a is String && spans(a).isNotEmpty() } }
    }

    /** Old byte range, new byte length of the replacement token. */
    private data class ByteSpan(val start: Int, val end: Int, val tokenBytes: Int)

    private fun byteSpans(text: String, spans: List<Span>) = spans.map {
        ByteSpan(text.substring(0, it.start).toByteArray(Charsets.UTF_8).size, text.substring(0, it.end).toByteArray(Charsets.UTF_8).size, it.token.toByteArray(Charsets.UTF_8).size)
    }

    /** Maps a byte offset in the original words to the redacted words; an offset inside a removed detail widens to cover its token. */
    private fun mapOffset(offset: Int, spans: List<ByteSpan>, end: Boolean): Int {
        var shift = 0
        for (s in spans) {
            if (offset <= s.start) break
            if (offset < s.end) return s.start + shift + if (end) s.tokenBytes else 0
            shift += s.tokenBytes - (s.end - s.start)
        }
        return offset + shift
    }
}
