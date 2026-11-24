package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * A live read-only web copy of one published card version. The token (32 random bytes, hex) is shown only once by the
 * server, which keeps only its SHA-256; this phone keeps it. Each published card has its own token and [version], so
 * an older printed card's page says "This is not the latest version of this card" (migration 20261124120000).
 * [expiresAt] is when the whole link series stops working; the card itself is valid for 3 months from [publishedAt].
 */
data class EmergencyLink(val token: String, val createdAt: Long, val expiresAt: Long, val version: Int = 1, val publishedAt: Long = createdAt) {
    val url: String get() = EmergencyLinkRules.url(token)
}
data class EmergencyLinkStatus(val live: Boolean, val expiresAt: Long?, val views: Int, val lastViewedAt: Long?, val recentViews: List<EmergencyCardView> = emptyList(), val version: Int? = null)

/** One open of the web link: when, and the hospital or clinic the person typed (blank when they skipped it). */
data class EmergencyCardView(val at: Long, val place: String)

/**
 * Where the web link was opened (Dr Sujay, 23 Nov). Since 24 Nov the printed QR holds only the web link, so every scan
 * that opens the card is counted (cards printed before then may also carry an older offline text code, which is not).
 */
object EmergencyViewRules {
    const val PLACE_MAX = 80
    const val RECENT = 10
    /** How far back a doctor is told about an open they have not seen yet. */
    const val NOTICE_DAYS = 7L
    private const val DAY = 86_400_000L

    /** Same cleaning as the web page and the server: one line, trimmed, at most [PLACE_MAX] characters. */
    fun cleanPlace(raw: String): String = raw.map { if (it.isISOControl() || it.isWhitespace()) ' ' else it }.joinToString("")
        .replace(Regex(" +"), " ").trim().take(PLACE_MAX).trim()

    fun decode(array: JSONArray?): List<EmergencyCardView> = array?.let { a ->
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { o -> EmergencyLinkRules.time(o.optString("at"))?.let { at -> EmergencyCardView(at, cleanPlace(if (o.isNull("place")) "" else o.optString("place"))) } } }
    }.orEmpty().sortedByDescending { it.at }.take(RECENT)

    /** Opens newer than [lastSeen] and from the last [NOTICE_DAYS] days, newest first. */
    fun newSince(views: List<EmergencyCardView>, lastSeen: Long, now: Long): List<EmergencyCardView> =
        views.filter { it.at > lastSeen && it.at >= now - NOTICE_DAYS * DAY && it.at <= now + 5 * 60_000L }.sortedByDescending { it.at }
}

/** Why there is no live link yet. Each one is said plainly on Today. */
enum class EmergencyLinkProblem(val en: String, val hi: String) {
    NOT_SET_UP("Web link not available yet.", "वेब लिंक अभी उपलब्ध नहीं है।"),
    SIGNED_OUT("Sign in to add a web link.", "वेब लिंक के लिए साइन इन करें।"),
    NOT_OWNER("Only the profile owner can make a web link.", "वेब लिंक केवल प्रोफ़ाइल का मालिक बना सकता है।"),
    WALKTHROUGH("Web links are off in Practice.", "अभ्यास में वेब लिंक बंद हैं।"),
    LINK_OFF("This link is off or expired.", "यह लिंक बंद है या समाप्त हो गया है।"),
    OFFLINE("Couldn't connect. Try again.", "कनेक्ट नहीं हो सका। फिर कोशिश करें।"),
}

class EmergencyLinkException(val problem: EmergencyLinkProblem) : IllegalStateException(problem.en)

object EmergencyLinkRules {
    const val PAGE = "https://sahara.wedevit.in/emergency"
    const val MAX_CARD_BYTES = 30_000
    private val TOKEN = Regex("^[a-f0-9]{64}$")

    /** The token rides in the #fragment, so it never reaches web-server logs or a Referer header. */
    fun url(token: String): String { require(TOKEN.matches(token)) { "Not an emergency link token." }; return "$PAGE#t=$token" }

    /** PostgREST answers 404 when a function is missing, i.e. the owner has not applied the migration yet. */
    fun problemFor(status: Int): EmergencyLinkProblem = when (status) {
        404 -> EmergencyLinkProblem.NOT_SET_UP
        401 -> EmergencyLinkProblem.SIGNED_OUT
        403 -> EmergencyLinkProblem.NOT_OWNER
        else -> EmergencyLinkProblem.OFFLINE
    }

    fun time(value: String?): Long? = value?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

    fun decodeCreated(json: String, now: Long): EmergencyLink {
        val j = JSONObject(json)
        val token = j.getString("token"); require(TOKEN.matches(token)) { "The server sent an unexpected link." }
        val created = time(j.optString("created_at")) ?: now
        return EmergencyLink(token, created, time(j.optString("expires_at")) ?: (now + 365L * 86_400_000), j.optInt("version", 1).coerceAtLeast(1), time(j.optString("published_at")) ?: created)
    }

    fun decodeStatus(json: String): EmergencyLinkStatus {
        val j = JSONObject(json)
        return EmergencyLinkStatus(j.optBoolean("live", false), time(j.optString("expires_at")), j.optInt("views", 0), time(j.optString("last_viewed_at")), EmergencyViewRules.decode(j.optJSONArray("recent_views")),
            j.optInt("version", 0).takeIf { it > 0 })
    }

    /**
     * What the web page shows and the printed PDF draws (one source for both): the sections the family ticked, in full.
     * "v": 2 (24 Nov) adds [nextOfKin], [doctor] (name, phone, hospital or clinic, landline), [otherContacts], each
     * medicine's "strength" and "when", and "validUntil". "contacts" and each medicine's "detail" stay for pages
     * deployed before then. Medicines are dropped from the end only if the copy would pass the server limit.
     */
    fun card(source: EmergencySource, record: EmergencyCardRecord): JSONObject {
        val sections = record.sections
        var medicines = if (EmergencySection.MEDICINES in sections) source.medicines.take(60) else emptyList()
        val kin = EmergencyCardRules.nextOfKin(source); val doctor = EmergencyCardRules.doctorOf(source)
        fun contact(c: EmergencyContact) = JSONObject().put("name", EmergencyCardRules.clip(c.name, 120)).put("relationship", EmergencyCardRules.clip(c.relationship, 80)).put("phone", c.phone.take(40))
        fun build(dropped: Int) = JSONObject().put("kind", "sahara-emergency-card").put("v", 2)
            .put("cardNumber", record.number).put("cardId", record.id).put("madeAt", record.madeAt).put("validUntil", record.validUntil)
            .put("sections", JSONArray(sections.map { it.name }))
            .also { j ->
                if (EmergencySection.NAME in sections) j.put("name", EmergencyCardRules.clip(source.name, 100))
                if (EmergencySection.GOALS in sections) source.goals?.let { g ->
                    j.put("goals", JSONObject().put("version", g.version).put("confirmedBy", EmergencyCardRules.clip(g.confirmedBy, 80)).put("confirmedAt", g.confirmedAt)
                        .put("whatMatters", g.whatMatters.take(4000)).put("nextSteps", g.nextSteps.take(4000)).also { o -> g.newerUnconfirmedVersion?.let { o.put("newerUnconfirmedVersion", it) } })
                }
                if (EmergencySection.CONTACTS in sections) {
                    j.put("contacts", JSONArray(source.contacts.take(20).map(::contact)))
                    kin?.let { j.put("nextOfKin", contact(it)) }
                    doctor?.let { d -> j.put("doctor", JSONObject().put("name", EmergencyCardRules.clip(d.name, 120)).put("phone", d.phone.take(40)).put("place", EmergencyCardRules.clip(d.place, 160)).put("landline", d.landline.take(40))) }
                    j.put("otherContacts", JSONArray(EmergencyCardRules.otherContacts(source).take(20).map(::contact)))
                }
                if (EmergencySection.MEDICINES in sections) j.put("medicines", JSONArray(medicines.map { m -> val row = EmergencyCardRules.medicineRow(m)
                    JSONObject().put("name", EmergencyCardRules.clip(m.name, 160)).put("strength", EmergencyCardRules.clip(row.strength, 80)).put("when", EmergencyCardRules.clip(row.whenTaken, 200)).put("detail", m.detail.take(400)) }))
                if (source.allergies.isNotBlank()) j.put("allergies", EmergencyCardRules.clip(source.allergies, 400))
                j.put("notRecorded", EmergencyCardRules.notRecorded(source))
                j.put("notIncluded", JSONArray(buildList {
                    EmergencySection.entries.filter { it !in sections }.forEach { add(it.en.substringBefore(" (").lowercase() + " (family chose not to include)") }
                    if (EmergencySection.GOALS in sections && source.goals == null) add("no confirmed goals-of-care note")
                    if (EmergencySection.CONTACTS in sections && kin == null) add("no contacts saved")
                    if (EmergencySection.CONTACTS in sections && kin != null && kin.phone.isBlank()) add("no phone number saved for the guardian")
                    if (EmergencySection.CONTACTS in sections && doctor == null) add("no treating doctor saved")
                    if (EmergencySection.MEDICINES in sections && source.medicines.isEmpty()) add("no current medicines listed")
                    addAll(EmergencyCardRules.recordedNotOnCard(source))
                    val skipped = dropped + (if (EmergencySection.MEDICINES in sections) (source.medicines.size - 60).coerceAtLeast(0) else 0)
                    if (skipped > 0) add("$skipped medicine${if (skipped == 1) "" else "s"} (no room)")
                }))
            }
        var dropped = 0
        var card = build(0)
        while (card.toString().toByteArray(Charsets.UTF_8).size > MAX_CARD_BYTES && medicines.isNotEmpty()) { medicines = medicines.dropLast(1); dropped++; card = build(dropped) }
        check(card.toString().toByteArray(Charsets.UTF_8).size <= MAX_CARD_BYTES) { "The card is too long for the web copy." }
        return card
    }
}

/** Server calls for the link. Only the profile's owner may make, change or turn off its link. */
class EmergencyLinkClient(private val cloud: CareCloud) {
    private suspend fun call(name: String, body: JSONObject): String = try {
        cloud.request("POST", "/rest/v1/rpc/$name", body)
    } catch (e: CareHttpException) {
        throw EmergencyLinkException(EmergencyLinkRules.problemFor(e.status).takeUnless { e.status == 400 && e.message.orEmpty().contains("off or expired") } ?: EmergencyLinkProblem.LINK_OFF)
    } catch (e: IllegalArgumentException) {
        if (e.cause is java.io.IOException) throw EmergencyLinkException(EmergencyLinkProblem.OFFLINE) else throw e
    }

    /** A new link series (first card or "Replace link"): the old series stops working at once. */
    suspend fun create(personId: String, card: JSONObject, now: Long = System.currentTimeMillis()): EmergencyLink =
        EmergencyLinkRules.decodeCreated(call("healthathon_emergency_link_create", JSONObject().put("p_person", personId).put("p_card", card)), now)

    /**
     * Publishes [card] as a new version with its own token (24 Nov). Older printed cards keep opening and say they are not
     * the latest. Before the owner applies 20261124120000_emergency_card_versions.sql the server has no publish function
     * (404): the old behaviour is used then (same link updated in place, or a new link when there is none).
     */
    suspend fun publish(personId: String, card: JSONObject, existing: EmergencyLink?, now: Long = System.currentTimeMillis()): EmergencyLink = try {
        EmergencyLinkRules.decodeCreated(call("healthathon_emergency_link_publish", JSONObject().put("p_person", personId).put("p_card", card)), now)
    } catch (e: EmergencyLinkException) {
        if (e.problem != EmergencyLinkProblem.NOT_SET_UP) throw e
        if (existing == null) create(personId, card, now)
        else {
            try { update(personId, card); existing }
            catch (off: EmergencyLinkException) { if (off.problem == EmergencyLinkProblem.LINK_OFF) create(personId, card, now) else throw off }
        }
    }

    /** Same link, new content (servers and apps from before 24 Nov). */
    suspend fun update(personId: String, card: JSONObject) { call("healthathon_emergency_link_update", JSONObject().put("p_person", personId).put("p_card", card)) }

    suspend fun revoke(personId: String) { call("healthathon_emergency_link_revoke", JSONObject().put("p_person", personId)) }

    suspend fun status(personId: String): EmergencyLinkStatus = EmergencyLinkRules.decodeStatus(call("healthathon_emergency_link_status", JSONObject().put("p_person", personId)))

    /** Counts and the last 10 opens, for the profile owner or the patient's approved doctor (no card content). */
    suspend fun views(personId: String): EmergencyLinkStatus = EmergencyLinkRules.decodeStatus(call("healthathon_emergency_link_views", JSONObject().put("p_person", personId)))
}
