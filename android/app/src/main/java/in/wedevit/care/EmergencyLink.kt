package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime

/** A live read-only web copy of the emergency card. The token is shown only once by the server; this phone keeps it. */
data class EmergencyLink(val token: String, val createdAt: Long, val expiresAt: Long) {
    val url: String get() = EmergencyLinkRules.url(token)
}
data class EmergencyLinkStatus(val live: Boolean, val expiresAt: Long?, val views: Int, val lastViewedAt: Long?)

/** Why there is no live link yet. Each one is said plainly on Today. */
enum class EmergencyLinkProblem(val en: String, val hi: String) {
    NOT_SET_UP("Web link not available yet.", "वेब लिंक अभी उपलब्ध नहीं है।"),
    SIGNED_OUT("Sign in to add a web link.", "वेब लिंक के लिए साइन इन करें।"),
    NOT_OWNER("Only the profile owner can make a web link.", "वेब लिंक केवल प्रोफ़ाइल का मालिक बना सकता है।"),
    WALKTHROUGH("Web links are off in the walkthrough.", "वॉकथ्रू में वेब लिंक बंद है।"),
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
        return EmergencyLink(token, time(j.optString("created_at")) ?: now, time(j.optString("expires_at")) ?: (now + 365L * 86_400_000))
    }

    fun decodeStatus(json: String): EmergencyLinkStatus {
        val j = JSONObject(json)
        return EmergencyLinkStatus(j.optBoolean("live", false), time(j.optString("expires_at")), j.optInt("views", 0), time(j.optString("last_viewed_at")))
    }

    /**
     * What the web page shows: the same sections the family ticked for the card, in full (the web copy is
     * not limited by QR size). Medicines are dropped from the end only if the copy would pass the server limit.
     */
    fun card(source: EmergencySource, record: EmergencyCardRecord): JSONObject {
        val sections = record.sections
        var medicines = if (EmergencySection.MEDICINES in sections) source.medicines.take(60) else emptyList()
        fun build(dropped: Int) = JSONObject().put("kind", "sahara-emergency-card").put("v", 1)
            .put("cardNumber", record.number).put("cardId", record.id).put("madeAt", record.madeAt)
            .put("sections", JSONArray(sections.map { it.name }))
            .also { j ->
                if (EmergencySection.NAME in sections) j.put("name", EmergencyCardRules.clip(source.name, 100))
                if (EmergencySection.GOALS in sections) source.goals?.let { g ->
                    j.put("goals", JSONObject().put("version", g.version).put("confirmedBy", EmergencyCardRules.clip(g.confirmedBy, 80)).put("confirmedAt", g.confirmedAt)
                        .put("whatMatters", g.whatMatters.take(4000)).put("nextSteps", g.nextSteps.take(4000)).also { o -> g.newerUnconfirmedVersion?.let { o.put("newerUnconfirmedVersion", it) } })
                }
                if (EmergencySection.CONTACTS in sections) j.put("contacts", JSONArray(source.contacts.take(20).map { JSONObject().put("name", EmergencyCardRules.clip(it.name, 120)).put("relationship", EmergencyCardRules.clip(it.relationship, 80)).put("phone", it.phone.take(40)) }))
                if (EmergencySection.MEDICINES in sections) j.put("medicines", JSONArray(medicines.map { JSONObject().put("name", EmergencyCardRules.clip(it.name, 160)).put("detail", it.detail.take(400)) }))
                j.put("notRecorded", EmergencyCardRules.NOT_IN_APP)
                j.put("notIncluded", JSONArray(buildList {
                    EmergencySection.entries.filter { it !in sections }.forEach { add(it.en.substringBefore(" (").lowercase() + " (family chose not to include)") }
                    if (EmergencySection.GOALS in sections && source.goals == null) add("no confirmed goals-of-care note")
                    if (EmergencySection.CONTACTS in sections && source.contacts.isEmpty()) add("no contacts saved")
                    if (EmergencySection.MEDICINES in sections && source.medicines.isEmpty()) add("no current medicines listed")
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

    suspend fun create(personId: String, card: JSONObject, now: Long = System.currentTimeMillis()): EmergencyLink =
        EmergencyLinkRules.decodeCreated(call("healthathon_emergency_link_create", JSONObject().put("p_person", personId).put("p_card", card)), now)

    /** Same link, new content: printed cards keep working and show the update. */
    suspend fun update(personId: String, card: JSONObject) { call("healthathon_emergency_link_update", JSONObject().put("p_person", personId).put("p_card", card)) }

    suspend fun revoke(personId: String) { call("healthathon_emergency_link_revoke", JSONObject().put("p_person", personId)) }

    suspend fun status(personId: String): EmergencyLinkStatus = EmergencyLinkRules.decodeStatus(call("healthathon_emergency_link_status", JSONObject().put("p_person", personId)))
}
