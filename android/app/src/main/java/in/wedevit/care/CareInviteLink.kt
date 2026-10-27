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
fun careJoinFailureMessage(raw: String?, hindi: Boolean = false): String = when {
    raw.isNullOrBlank() -> if (hindi) "यह निमंत्रण इस्तेमाल नहीं हो सका। नया निमंत्रण माँगें।" else "This invitation couldn't be used. Ask for a new one."
    raw.startsWith("Clinician verification") -> raw
    raw.startsWith("Couldn't reach") -> if (hindi) "अभी इंटरनेट नहीं है। इंटरनेट जाँचें और लिंक फिर खोलें।" else "No connection right now. Check your internet and open the link again."
    raw.startsWith("Sign in again") -> if (hindi) "कृपया फिर से साइन इन करें, फिर निमंत्रण का लिंक दोबारा खोलें।" else "Please sign in again, then open the invitation link again."
    else -> if (hindi) "यह निमंत्रण पुराना हो गया है या पहले ही इस्तेमाल हो चुका है। नया निमंत्रण माँगें।" else "This invitation has expired or was already used. Ask for a new one."
}

/** Local record of invitations the owner sent, so the list can show name, relationship and status. */
data class CareInviteEntry(val person: String, val name: String, val relation: String, val createdAt: Long, val baseline: Int, val joined: Boolean = false)

object CareInviteLog {
    const val DAY_MS = 24L * 60 * 60 * 1000
    const val TTL_MS = 3 * DAY_MS
    private const val F = '\u001F'
    fun encode(list: List<CareInviteEntry>) = list.joinToString("\n") { listOf(it.person, it.name, it.relation, it.createdAt, it.baseline, if (it.joined) 1 else 0).joinToString(F.toString()) }
    fun decode(text: String?): List<CareInviteEntry> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split(F); if (f.size != 6) return@mapNotNull null
        val at = f[3].toLongOrNull() ?: return@mapNotNull null; val base = f[4].toIntOrNull() ?: return@mapNotNull null
        CareInviteEntry(f[0], f[1], f[2], at, base, f[5] == "1")
    }
    fun expired(e: CareInviteEntry, now: Long) = now - e.createdAt >= TTL_MS
    /** Marks the oldest still-open invites as joined when the person has gained members since they were sent; drops old stale ones. This is a local estimate: the server does not say which invite was used. */
    fun reconcile(all: List<CareInviteEntry>, person: String, membersNow: Int, now: Long): List<CareInviteEntry> {
        val mine = all.filter { it.person == person }; val others = all.filter { it.person != person }
        val open = mine.filter { !it.joined }.sortedWith(compareBy({ expired(it, now) }, { it.createdAt }))
        val budget = if (open.isEmpty()) 0 else (membersNow - open.minOf { it.baseline }).coerceAtLeast(0)
        val joinedNow = open.take(budget).toSet()
        val updated = mine.map { when { it.joined -> it; it in joinedNow -> it.copy(joined = true); else -> it.copy(baseline = membersNow) } }
        val kept = updated.filter { it.joined || now - it.createdAt < TTL_MS + 7 * DAY_MS }.sortedByDescending { it.createdAt }.take(20)
        return others + kept
    }
    fun label(e: CareInviteEntry, now: Long): String {
        val who = listOf(e.name, e.relation.lowercase()).filter { it.isNotBlank() }.joinToString(" · ")
        val status = when {
            e.joined -> "Joined"
            expired(e, now) -> "Expired · create a new invite"
            else -> { val left = e.createdAt + TTL_MS - now; val days = ((left + DAY_MS - 1) / DAY_MS).toInt(); "Invited · " + if (left < DAY_MS) "expires today" else "expires in $days ${if (days == 1) "day" else "days"}" }
        }
        return "$who · $status".trimStart(' ', '·')
    }
}

class CareInviteStore(private val kv: CareKeyValue) {
    private fun key(account: String) = "invites_$account"
    fun all(account: String) = CareInviteLog.decode(kv.get(key(account)))
    fun save(account: String, list: List<CareInviteEntry>) = kv.put(key(account), CareInviteLog.encode(list).ifEmpty { null })
    fun add(account: String, entry: CareInviteEntry) = save(account, all(account) + entry)
}

fun careInviteStore(context: android.content.Context) = CareInviteStore(CarePrefsKeyValue(context.getSharedPreferences("care_invite_log", android.content.Context.MODE_PRIVATE)))
