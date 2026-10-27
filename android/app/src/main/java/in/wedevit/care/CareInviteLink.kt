package `in`.wedevit.care

import java.net.URLDecoder
import java.net.URLEncoder

/** A care invitation pulled from a link or pasted text. [name] is only a display hint from the link, never trusted for access. */
data class CareInvite(val code: String, val name: String = "")

/** Builds and reads Sahara invite links. Pure Kotlin so it is unit-testable. */
object CareInviteLink {
    const val WEB = "https://sahara.wedevit.in/join"
    private val LINK = Regex("""(?:sahara://join|https?://sahara\.wedevit\.in/join)/?\?([^\s#]*)""", RegexOption.IGNORE_CASE)
    private val CODE = Regex("[a-fA-F0-9]{48}")
    private val BARE = Regex("(?<![A-Za-z0-9])[a-fA-F0-9]{48}(?![A-Za-z0-9])")

    fun isCode(value: String) = CODE.matches(value)
    fun link(code: String, name: String = ""): String = WEB + "?c=" + code + if (name.isBlank()) "" else "&n=" + URLEncoder.encode(cleanName(name), "UTF-8")
    fun shareText(name: String, code: String): String {
        val who = cleanName(name).ifBlank { "Someone" }
        return "You're invited to see $who's care in Sahara. Tap to join: ${link(code, name)}\n\n(If it doesn't open, install Sahara, sign in, and choose Join someone's care; code: $code)"
    }
    fun cleanName(value: String) = value.filter { !it.isISOControl() && it !in "<>\"&=?#%" }.trim().take(40)

    /** Accepts a full https link, a sahara://join link, a whole shared message, or a bare 48-character code. Returns null for anything else. */
    fun parse(text: String?): CareInvite? {
        val input = text?.trim().orEmpty()
        if (input.isEmpty() || input.length > 4000) return null
        for (match in LINK.findAll(input)) {
            val params = match.groupValues[1].split('&').mapNotNull { part -> part.indexOf('=').takeIf { it > 0 }?.let { part.substring(0, it) to part.substring(it + 1) } }.toMap()
            val code = params["c"]?.trimEnd(')', '.', ',', ';')?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }.orEmpty()
            if (isCode(code)) return CareInvite(code.lowercase(), params["n"]?.let { runCatching { cleanName(URLDecoder.decode(it.trimEnd(')', '.', ',', ';'), "UTF-8")) }.getOrNull() }.orEmpty())
        }
        return BARE.find(input)?.let { CareInvite(it.value.lowercase()) }
    }
}

/** An invite waiting for the person to confirm. */
data class PendingJoin(val code: String, val name: String, val savedAt: Long) {
    val who: String get() = name.ifBlank { "this person" }
    val possessive: String get() = if (name.isBlank()) "this person's" else "$name's"
}

interface CareKeyValue { fun get(key: String): String?; fun put(key: String, value: String?) }
class CarePrefsKeyValue(private val prefs: android.content.SharedPreferences) : CareKeyValue {
    override fun get(key: String) = prefs.getString(key, null)
    override fun put(key: String, value: String?) { prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
}

/** Keeps one pending join so it survives sign-in, app restarts and email confirmation. Expires after the invite itself would. */
class CarePendingJoinStore(private val kv: CareKeyValue, private val ttlMs: Long = CareInviteLog.TTL_MS) {
    fun save(join: PendingJoin) { kv.put("code", join.code); kv.put("name", join.name); kv.put("at", join.savedAt.toString()) }
    fun load(now: Long): PendingJoin? {
        val code = kv.get("code") ?: return null
        val at = kv.get("at")?.toLongOrNull()
        if (!CareInviteLink.isCode(code) || at == null || now < at - 60_000L || now - at > ttlMs) { clear(); return null }
        return PendingJoin(code, kv.get("name").orEmpty(), at)
    }
    fun clear() { kv.put("code", null); kv.put("name", null); kv.put("at", null) }
}

/** Plain messages for a failed join. */
fun careJoinFailureMessage(raw: String?, hindi: Boolean = false): String {
    val t = { en: String, hi: String -> if (hindi) hi else en }
    return when {
        raw.isNullOrBlank() -> t("This invitation couldn't be used. Ask for a new one.", "यह निमंत्रण काम नहीं आया। नया निमंत्रण माँगें।")
        raw.startsWith("Clinician verification") -> raw
        raw.startsWith("Couldn't reach") -> t("No connection right now. Check your internet and open the link again.", "अभी इंटरनेट नहीं मिल रहा। इंटरनेट देखें और लिंक फिर से खोलें।")
        raw.startsWith("Sign in again") -> t("Please sign in again, then open the invitation link again.", "फिर से साइन इन करें, फिर निमंत्रण का लिंक दोबारा खोलें।")
        raw.startsWith("Use a separate account") -> t("This invitation was made from this account. The family member must join from their own account.", "यह निमंत्रण इसी खाते से बना है। परिवार के सदस्य को अपने खाते से जुड़ना होगा।")
        else -> t("This invitation has expired or was already used. Ask for a new one.", "यह निमंत्रण बंद हो चुका है या पहले ही इस्तेमाल हो चुका है। नया निमंत्रण माँगें।")
    }
}

/** Invitations the owner sent from this phone. [inviteId] is the server's id for it; status always comes from the server. */
data class CareInviteEntry(val person: String, val name: String, val relation: String, val createdAt: Long,
    val inviteId: String = "", val expiresAt: Long = createdAt + CareInviteLog.TTL_MS, val role: String = "family")

enum class CareInviteState {
    /** The server still holds it open. */ OPEN,
    /** A member's access was created by this invitation (server record). */ JOINED,
    /** The server no longer holds it, before its expiry: someone used it or it was cancelled. */ CLOSED,
    EXPIRED,
    /** The server could not be asked (offline or not allowed). */ UNKNOWN,
    /** Sent by an older app version that didn't keep the server id, so it can't be matched. */ UNTRACKED,
}
data class CareInviteStatus(val state: CareInviteState, val memberId: String = "", val at: Long = 0L)

object CareInviteLog {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val TTL_MS = 3 * DAY_MS
    private const val F = '\u001F'
    fun encode(list: List<CareInviteEntry>) = list.joinToString("\n") { listOf(it.person, it.name, it.relation, it.createdAt, it.inviteId, it.expiresAt, it.role, "2").joinToString(F.toString()) }
    fun decode(text: String?): List<CareInviteEntry> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split(F); val at = f.getOrNull(3)?.toLongOrNull() ?: return@mapNotNull null
        when (f.size) {
            8 -> CareInviteEntry(f[0], f[1], f[2], at, f[4], f[5].toLongOrNull() ?: (at + TTL_MS), f[6].ifBlank { "family" })
            // Before 27 Oct the app guessed "joined" from member counts; that guess is dropped, never shown as fact.
            6 -> CareInviteEntry(f[0], f[1], f[2], at)
            else -> null
        }
    }
    /** The id of the invitation just created: the one open on the server now that wasn't open before. Null when that isn't certain. */
    fun newInviteId(before: Collection<String>?, after: Collection<String>?): String? =
        if (before == null || after == null) null else (after.toSet() - before.toSet()).singleOrNull()
    fun status(e: CareInviteEntry, open: List<CareOpenInvite>?, members: List<CareMemberDetail>?, now: Long): CareInviteStatus {
        if (e.inviteId.isNotBlank()) members?.firstOrNull { it.inviteId == e.inviteId }?.let { return CareInviteStatus(CareInviteState.JOINED, it.userId, it.joinedAt) }
        if (now >= e.expiresAt) return CareInviteStatus(CareInviteState.EXPIRED)
        if (e.inviteId.isBlank()) return CareInviteStatus(CareInviteState.UNTRACKED)
        if (open == null) return CareInviteStatus(CareInviteState.UNKNOWN)
        return CareInviteStatus(if (open.any { it.id == e.inviteId }) CareInviteState.OPEN else CareInviteState.CLOSED)
    }
    /** Keeps the newest 20 invitations per person from the last 30 days. */
    fun prune(all: List<CareInviteEntry>, now: Long): List<CareInviteEntry> =
        all.filter { now - it.createdAt < 30 * DAY_MS }.groupBy { it.person }.values.flatMap { list -> list.sortedByDescending { it.createdAt }.take(20) }
    fun label(e: CareInviteEntry, status: CareInviteStatus, now: Long, hindi: Boolean = false, memberEmail: String = ""): String {
        val t = { en: String, hi: String -> if (hindi) hi else en }
        val relation = CareRelationsHindi[e.relation]?.takeIf { hindi } ?: e.relation.lowercase()
        val who = listOf(e.name, relation).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { if (e.role == "clinician") t("Doctor invitation", "डॉक्टर का निमंत्रण") else t("Family invitation", "परिवार का निमंत्रण") }
        val left = e.expiresAt - now; val days = ((left + DAY_MS - 1) / DAY_MS).toInt()
        val expiry = if (left < DAY_MS) t("expires today", "आज बंद होगा") else t("expires in $days ${if (days == 1) "day" else "days"}", "$days दिन में बंद होगा")
        val day = if (status.at > 0) java.time.Instant.ofEpochMilli(status.at).atZone(java.time.ZoneId.systemDefault()).format(careDateFormat("d MMM", hindi)) else ""
        val text = when (status.state) {
            CareInviteState.JOINED -> listOf(t("Joined", "जुड़ गए"), memberEmail, day).filter { it.isNotBlank() }.joinToString(" · ")
            CareInviteState.OPEN -> t("Invited · not joined yet · ", "निमंत्रण भेजा · अभी जुड़े नहीं · ") + expiry
            CareInviteState.CLOSED -> t("Link used or cancelled · see who has access below", "लिंक इस्तेमाल हो गया या रद्द हुआ · नीचे देखें किसे पहुँच है")
            CareInviteState.EXPIRED -> t("Expired · create a new invite", "बंद हो गया · नया निमंत्रण बनाएँ")
            CareInviteState.UNKNOWN -> t("Sent · couldn't check with the server just now", "भेजा गया · अभी सर्वर से पता नहीं चल सका")
            CareInviteState.UNTRACKED -> t("Sent · ", "भेजा गया · ") + expiry
        }
        return "$who · $text"
    }
}

class CareInviteStore(private val kv: CareKeyValue) {
    private fun key(account: String) = "invites_$account"
    fun all(account: String) = CareInviteLog.decode(kv.get(key(account)))
    fun save(account: String, list: List<CareInviteEntry>) = kv.put(key(account), CareInviteLog.encode(list).ifEmpty { null })
    fun add(account: String, entry: CareInviteEntry) = save(account, all(account) + entry)
}

fun careInviteStore(context: android.content.Context) = CareInviteStore(CarePrefsKeyValue(context.getSharedPreferences("care_invite_log", android.content.Context.MODE_PRIVATE)))

/** Records a just-created invitation with the server's id for it (open on the server now, not before), so its status can be read back later. */
suspend fun CareInviteStore.logCreated(cloud: CareCloud, account: String, person: String, name: String, relation: String, role: String, before: List<CareOpenInvite>?) {
    val after = cloud.openInvites(person)
    val id = CareInviteLog.newInviteId(before?.map { it.id }, after?.map { it.id }).orEmpty()
    val now = System.currentTimeMillis()
    val expires = after?.firstOrNull { it.id == id }?.expiresAt?.takeIf { it > 0 } ?: (now + CareInviteLog.TTL_MS)
    save(account, CareInviteLog.prune(all(account) + CareInviteEntry(person, name, relation, now, id, expires, role), now))
}
