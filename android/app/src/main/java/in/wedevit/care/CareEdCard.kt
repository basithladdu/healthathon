package `in`.wedevit.care

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/*
 * The server side of the wallet card, same actions and fields as prototype/app/ed-card-client.ts: the phone issues a random token for the signed
 * plan and withdraws it when the plan is withdrawn or replaced. The card always saves here first, so it never depends on the network.
 */

internal const val SAHARA_ORIGIN = "https://sahara.wedevit.in"
internal const val ED_CARD_PATH = "/api/ed-card"
internal const val REMINDER_PATH = "/api/care-plan-reminder"

/** HTTP status and body. Throws [IOException] when the server could not be reached. No sign-in: the same public routes the web uses. */
fun interface EdCardTransport { suspend fun post(path: String, body: String): Pair<Int, String> }

class EdCardHttpTransport : EdCardTransport {
    override suspend fun post(path: String, body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        require(path == ED_CARD_PATH || path == REMINDER_PATH)
        val connection = URL(SAHARA_ORIGIN + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 12000; connection.readTimeout = 15000; connection.instanceFollowRedirects = false
            connection.setRequestProperty("Content-Type", "application/json"); connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("Cache-Control", "no-store"); connection.setRequestProperty("X-Sahara-Client", "android")
            val bytes = body.toByteArray(Charsets.UTF_8)
            connection.doOutput = true; connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            status to (stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty().take(256 * 1024))
        } finally { connection.disconnect() }
    }
}

/**
 * The JSON the server stores: the record in the web model's shape, with the living will's status when the card was issued (the AMD itself stays here).
 * It carries gender, the family lists, home care and the Board record; the route's quickViewSnapshot leaves gender, the family lists, Board members and the Board decision out of what a scan shows.
 */
object EdCardSnapshot {
    fun summary(amd: AmdStatusSummary?): Any = amd?.let { JSONObject().put("status", it.status.name).put("executedOn", it.executedOn ?: JSONObject.NULL).put("version", it.version).put("attestor", it.attestor ?: JSONObject.NULL) } ?: JSONObject.NULL
    fun json(r: EctprRecord, amd: AmdStatusSummary?): JSONObject = PlanJson.encode(r).put("amdAtIssue", summary(amd)).also { it.remove(GOC_TRANSCRIPT_KEY) }

    /** issuableProblem in prototype/app/ed-card-snapshot.ts (rule V7). */
    fun problem(snapshot: JSONObject?): String? {
        if (snapshot == null) return "No care plan was sent."
        if (snapshot.optString("status") != "ACTIVE") return "Only the Active (signed) care plan can be put on an emergency card."
        val signatures = snapshot.optJSONArray("signatures")
        val consultant = signatures?.let { a -> (0 until a.length()).map(a::getJSONObject).firstOrNull { it.optString("role") == "TREATING_CONSULTANT" } }
        if (consultant == null || consultant.optString("name").isBlank() || consultant.optString("registrationNo").isBlank()) return "The treating consultant has not signed this plan with a registration number."
        if (snapshot.optJSONObject("patient")?.optString("uhid").isNullOrEmpty() || snapshot.optString("reviewDue").isEmpty()) return "The plan is missing the UHID or review date."
        return null
    }
}

object EdCardApi {
    /** Stored in [CardToken.serverMessage] when the server could not be reached; the screen words it in the chosen language. */
    const val NETWORK = "network"
    /** An opaque reference to the person: no name, no account, and not the same value as anything else on the server. */
    fun patientRef(patientId: String): String = "android-" + AmdLifecycle.sha256("sahara-card:$patientId".toByteArray(Charsets.UTF_8)).take(40)

    private fun answer(body: String): JSONObject? = runCatching { JSONObject(body) }.getOrNull()

    /** One step for one card: uploads a live card, tells the server about a withdrawn one. Network and server failures come back as a state, never as an exception. */
    suspend fun push(card: CardToken, transport: EdCardTransport): CardToken {
        if (!card.pending) return card
        return if (card.live) issue(card, transport) else revoke(card, transport)
    }

    private suspend fun issue(card: CardToken, transport: EdCardTransport): CardToken {
        val snapshot = runCatching { JSONObject(card.snapshot) }.getOrNull()
        EdCardSnapshot.problem(snapshot)?.let { return card.copy(server = CardServerState.LOCAL_ONLY, serverMessage = it) }
        val body = JSONObject().put("action", "issue").put("token", card.token).put("manageKey", card.manageKey).put("patientRef", patientRef(card.patientId)).put("snapshot", snapshot).toString()
        val (status, text) = try { transport.post(ED_CARD_PATH, body) } catch (_: IOException) { return card.copy(server = CardServerState.LOCAL_ONLY, serverMessage = NETWORK) }
        return if (status in 200..299) card.copy(server = CardServerState.ISSUED, serverMessage = null)
        else card.copy(server = CardServerState.LOCAL_ONLY, serverMessage = answer(text)?.optString("message")?.takeIf(String::isNotBlank)?.take(300) ?: "The server did not accept the card.")
    }

    private suspend fun revoke(card: CardToken, transport: EdCardTransport): CardToken {
        val body = JSONObject().put("action", "revoke").put("token", card.token).put("manageKey", card.manageKey).put("reason", card.revokedReason.orEmpty()).toString()
        val (status, text) = try { transport.post(ED_CARD_PATH, body) } catch (_: IOException) { return card.copy(server = CardServerState.REVOKE_PENDING, serverMessage = NETWORK) }
        val reply = answer(text)
        // "unknown": the card never reached the server, so nothing can open it.
        val done = status in 200..299 || reply?.optString("status") == "revoked" || reply?.optString("status") == "unknown"
        return if (done) card.copy(server = CardServerState.REVOKED, serverMessage = null)
        else card.copy(server = CardServerState.REVOKE_PENDING, serverMessage = reply?.optString("message")?.takeIf(String::isNotBlank)?.take(300) ?: "The server did not accept the withdrawal.")
    }
}

/** Plain-text summary for the second QR (readable by any phone camera, no internet): offlineCardText in prototype/app/ectpr-pdf.ts. */
object EdCardOffline {
    /** Above this the QR is too dense to scan reliably from a printed card; the text falls back to English, which is about a third of the size. */
    private const val MAX_QR_BYTES = 1100
    private fun cut(value: String, max: Int) = if (value.length <= max) value else value.take(max - 1) + "…"

    fun text(r: EctprRecord, amd: AmdStatusSummary?, hindi: Boolean, today: LocalDate): String {
        fun p(b: Bi) = b.pick(hindi)
        val c = r.choices; val interp = EdView.interpret(r, today); val consultant = r.consultantSignature; val dm = r.decisionMakers.primary; val none = p(NOT_RECORDED)
        val lines = mutableListOf("SAHARA ECTPR v${r.version} · ${r.formVersion}")
        lines += if (interp.kind == EdKind.ACTIVE) p(Bi("Signed emergency care plan", "हस्ताक्षरित आपातकालीन योजना")) else p(interp.banner)
        lines += "${r.patient.name} · UHID ${r.patient.uhid}"
        lines += p(Bi("Diagnosis", "बीमारी")) + ": " + cut(r.clinical.diagnosis, 80).ifBlank { none }
        lines += p(Bi("Transfer", "अस्पताल ले जाना")) + ": " + p(c.hospitalTransfer?.let { PlanText.transfer(it.kind) } ?: NO_PREFERENCE)
        lines += "CPR: " + p(c.cpr?.let(PlanText::cpr) ?: NO_PREFERENCE)
        lines += p(Bi("Breathing ceiling", "साँस की सीमा")) + ": " + p(c.respCeiling?.let(PlanText::ceiling) ?: NO_PREFERENCE)
        lines += "ICU: " + p(c.icu?.let { PlanText.icu(it.kind) } ?: NO_PREFERENCE)
        lines += (p(Bi("Call first", "पहले फ़ोन करें")) + ": " + r.careTeam.palliativePhysician.ifBlank { none } + " " + r.careTeam.palliativePhone24h).trim()
        lines += p(Bi("Decision-maker", "फ़ैसला लेने वाले")) + ": " + (dm?.let { "${it.name} (${it.relation}) ${it.phone24h}".trim() } ?: none)
        lines += p(Bi("Signed", "हस्ताक्षर")) + ": " + (consultant?.let { "${it.name} ${it.signedAt.take(10)}" } ?: p(Bi("NOT SIGNED", "हस्ताक्षर नहीं"))) + " · " + p(Bi("Review due", "समीक्षा")) + " " + r.reviewDue
        if (amd != null) { lines += AmdLifecycle.edLines(r.existingDirective, amd).first.joinToString(" ") { p(it) }; lines += p(AmdText.CAVEAT) }
        lines += p(WalletCard.TREAT_LINE)
        return lines.joinToString("\n")
    }

    fun forQr(r: EctprRecord, amd: AmdStatusSummary?, hindi: Boolean, today: LocalDate): String {
        val shown = text(r, amd, hindi, today)
        return if (shown.toByteArray(Charsets.UTF_8).size <= MAX_QR_BYTES) shown else careInEnglish { text(r, amd, false, today) }
    }
}

/** The "send review reminder" button (tech spec §8): same route and fixed wording as the web, and it says so when SMS is not set up. */
object ReviewReminder {
    const val WINDOW_DAYS = 14L
    fun daysUntilReview(r: EctprRecord, today: LocalDate): Long? = PlanDates.day(r.reviewDue)?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
    fun due(r: EctprRecord, today: LocalDate): Boolean = r.status == EctprStatus.ACTIVE && (daysUntilReview(r, today)?.let { it <= WINDOW_DAYS } ?: false)

    /** indianMobileE164 in prototype/app/ed-card-snapshot.ts. */
    fun mobile(phone: String?): String? {
        val digits = phone.orEmpty().filter { it.isDigit() || it == '+' }
        val plain = digits.removePrefix("+")
        val bare = if (plain.length == 12 && plain.startsWith("91")) plain.drop(2) else if (plain.length == 11 && plain.startsWith("0")) plain.drop(1) else plain
        if (Regex("^[6-9][0-9]{9}$").matches(bare)) return "+91$bare"
        return digits.takeIf { Regex("^\\+[1-9][0-9]{7,14}$").matches(it) }
    }
    /** Who gets it: the main decision-maker if they have a mobile number, otherwise the patient. */
    fun recipient(r: EctprRecord): Pair<String, String>? {
        val dm = r.decisionMakers.primary
        mobile(dm?.phone24h)?.let { return dm!!.name.ifBlank { "the decision-maker" } to it }
        return mobile(r.patient.phone)?.let { r.patient.name.ifBlank { "the patient" } to it }
    }
    /** true or false from the server, null when it could not be reached. */
    suspend fun configured(transport: EdCardTransport): Boolean? = try { JSONObject(transport.post(REMINDER_PATH, JSONObject().put("action", "status").toString()).second).optBoolean("configured") } catch (_: Exception) { null }
    /** "sent", "not-configured", "no-valid-number", "too-many", "network" or another failure status. */
    suspend fun send(r: EctprRecord, phone: String, transport: EdCardTransport): String {
        val first = r.patient.name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
        val body = JSONObject().put("action", "send").put("phone", phone).put("reviewDue", r.reviewDue.take(10)).put("patientFirstName", first).toString()
        return try {
            val reply = JSONObject(transport.post(REMINDER_PATH, body).second)
            if (reply.optBoolean("sent")) "sent" else reply.optString("status").ifBlank { "error" }
        } catch (_: IOException) { "network" } catch (_: org.json.JSONException) { "error" }
    }
}
