package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject

/** One piece of community wording in English and Hindi; screens show it with t(copy.en, copy.hi). */
internal data class CommunityCopy(val en: String, val hi: String)

/** A group as the server lists it (public.community_groups, editable by the team). [tone] picks the tile colour. */
internal data class CommunityGroup(val id: String, val nameEn: String, val nameHi: String, val aboutEn: String, val aboutHi: String, val tone: String, val posts: Int)
internal data class CommunityHome(val myName: String?, val canModerate: Boolean, val groups: List<CommunityGroup>)

/** Authors appear by their chosen community name only; [mine] and [byTeam] come from the server, never a user id. */
internal data class CommunityReply(val id: String, val body: String, val createdMs: Long, val name: String, val mine: Boolean, val byTeam: Boolean,
    val hidden: Boolean, val reported: Boolean, val reports: Int)
internal data class CommunityPost(val id: String, val groupId: String, val body: String, val createdMs: Long, val name: String, val mine: Boolean, val byTeam: Boolean,
    val hidden: Boolean, val helpful: Int, val hugs: Int, val myHelpful: Boolean, val myHugs: Boolean, val reported: Boolean, val reports: Int, val replies: List<CommunityReply>)
internal data class CommunityFeed(val canModerate: Boolean, val myName: String?, val posts: List<CommunityPost>)

/** A post or reply kept on this phone because it could not be sent. [target] is the group id for a post and the post id for a reply. */
internal data class CommunityPending(val id: String, val accountId: String, val kind: String, val target: String, val body: String, val savedAt: Long) {
    init { require(kind == POST || kind == REPLY) }
    companion object { const val POST = "post"; const val REPLY = "reply" }
}

internal object CommunityRules {
    const val POST_MIN = 2
    const val POST_MAX = 1000
    const val REPLY_MAX = 500
    const val NAME_MIN = 2
    const val NAME_MAX = 40
    const val POSTS_PER_DAY = 10
    const val REPLIES_PER_DAY = 30

    /** Report reasons, in the order the dialog lists them. Keys match the server check. */
    val reportReasons = listOf(
        "unkind" to CommunityCopy("Unkind or hurtful", "रूखा या दुख देने वाला"),
        "harmful-advice" to CommunityCopy("Medical advice that could harm", "ऐसी डॉक्टरी सलाह जो नुकसान कर सकती है"),
        "spam" to CommunityCopy("Selling or spam", "कुछ बेचना या स्पैम"),
        "private" to CommunityCopy("Shares someone's private details", "किसी की निजी जानकारी"),
        "other" to CommunityCopy("Something else", "कुछ और"),
    )

    // Same rule as healthathon_private.community_has_contact on the server.
    private val link = Regex("(https?://|www\\.)", RegexOption.IGNORE_CASE)
    private val email = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
    private val phone = Regex("[0-9]([ -]?[0-9]){9,}")
    fun hasContact(text: String): Boolean = link.containsMatchIn(text) || email.containsMatchIn(text) || phone.containsMatchIn(text)

    private val contact = CommunityCopy("Please leave out phone numbers, email addresses and links. They keep everyone safe.", "कृपया फ़ोन नंबर, ईमेल और लिंक न लिखें। इससे सब सुरक्षित रहते हैं।")

    fun postError(text: String): CommunityCopy? {
        val clean = text.trim()
        return when {
            clean.length < POST_MIN -> CommunityCopy("Write a few words first.", "पहले कुछ शब्द लिखें।")
            clean.length > POST_MAX -> CommunityCopy("Keep your post under $POST_MAX characters.", "पोस्ट $POST_MAX अक्षरों से छोटी रखें।")
            hasContact(clean) -> contact
            else -> null
        }
    }

    fun replyError(text: String): CommunityCopy? {
        val clean = text.trim()
        return when {
            clean.isEmpty() -> CommunityCopy("Write your reply first.", "पहले अपना जवाब लिखें।")
            clean.length > REPLY_MAX -> CommunityCopy("Keep your reply under $REPLY_MAX characters.", "जवाब $REPLY_MAX अक्षरों से छोटा रखें।")
            hasContact(clean) -> contact
            else -> null
        }
    }

    fun nameError(name: String): CommunityCopy? {
        val clean = name.trim()
        return when {
            clean.length < NAME_MIN -> CommunityCopy("Choose a name others will see, like \"Asha's daughter\".", "ऐसा नाम चुनें जो दूसरे देखेंगे, जैसे \"आशा की बेटी\"।")
            clean.length > NAME_MAX -> CommunityCopy("Keep your name under $NAME_MAX characters.", "नाम $NAME_MAX अक्षरों से छोटा रखें।")
            '@' in clean || hasContact(clean) -> CommunityCopy("Don't use a phone number or email as your name.", "नाम में फ़ोन नंबर या ईमेल न लिखें।")
            clean.contains("sahara", ignoreCase = true) -> CommunityCopy("Only the Sahara team can use the name Sahara.", "Sahara नाम सिर्फ़ Sahara टीम इस्तेमाल कर सकती है।")
            else -> null
        }
    }

    /** What to tell the person when a community call fails. Server messages are fixed English sentences (20261106120000_community.sql). */
    fun serverMessage(error: Throwable): CommunityCopy {
        val message = error.message.orEmpty()
        return when {
            error is CareSyncOffline -> CommunityCopy("You're offline. Connect to the internet and try again.", "आप ऑफ़लाइन हैं। इंटरनेट से जुड़कर फिर कोशिश करें।")
            error.isMissingServerFeature() -> CommunityCopy("The community isn't open yet. Please try again later.", "समुदाय अभी खुला नहीं है। कृपया बाद में फिर कोशिश करें।")
            message.startsWith("Daily post limit") -> CommunityCopy("You've shared $POSTS_PER_DAY posts today. You can post again tomorrow.", "आज आपने $POSTS_PER_DAY पोस्ट कर दी हैं। कल फिर पोस्ट कर सकते हैं।")
            message.startsWith("Daily reply limit") -> CommunityCopy("You've sent $REPLIES_PER_DAY replies today. You can reply again tomorrow.", "आज आपने $REPLIES_PER_DAY जवाब भेज दिए हैं। कल फिर जवाब दे सकते हैं।")
            message.startsWith("Daily report limit") -> CommunityCopy("You've sent many reports today. The Sahara team will look at them.", "आज आपने कई रिपोर्ट भेजी हैं। Sahara टीम उन्हें देखेगी।")
            message.startsWith("Choose a community name") -> CommunityCopy("Choose the name others will see first.", "पहले वह नाम चुनें जो दूसरे देखेंगे।")
            message.startsWith("Name not allowed") -> CommunityCopy("Choose a different name, without a phone number, email or the word Sahara.", "कोई और नाम चुनें, बिना फ़ोन नंबर, ईमेल या Sahara शब्द के।")
            message.startsWith("Post length") -> CommunityCopy("Keep your post between $POST_MIN and $POST_MAX characters.", "पोस्ट $POST_MIN से $POST_MAX अक्षरों के बीच रखें।")
            message.startsWith("Reply length") -> CommunityCopy("Keep your reply under $REPLY_MAX characters.", "जवाब $REPLY_MAX अक्षरों से छोटा रखें।")
            message.startsWith("Remove contact details") -> contact
            message.startsWith("This post is no longer available") -> CommunityCopy("This post is no longer here.", "यह पोस्ट अब यहाँ नहीं है।")
            message.startsWith("Group not found") -> CommunityCopy("This group is no longer here.", "यह समूह अब यहाँ नहीं है।")
            message.startsWith("Only the Sahara team") -> CommunityCopy("Only the Sahara team can hide posts.", "पोस्ट सिर्फ़ Sahara टीम छिपा सकती है।")
            message.contains("account is closed") -> CommunityCopy("Your Sahara account is closed. Write to workwithdevit@gmail.com for help.", "आपका Sahara खाता बंद है। मदद के लिए workwithdevit@gmail.com पर लिखें।")
            (error is CareHttpException && (error.status == 401 || error.code == "42501")) || message.startsWith("Sign in") -> CommunityCopy("Sign in again to use the community.", "समुदाय इस्तेमाल करने के लिए फिर से साइन इन करें।")
            error is CareHttpException && error.status == 429 -> CommunityCopy("Please wait a moment and try again.", "थोड़ा रुककर फिर कोशिश करें।")
            else -> CommunityCopy("Couldn't reach the community. Please try again.", "समुदाय से जुड़ नहीं पाए। कृपया फिर कोशिश करें।")
        }
    }

    /** Short relative time for a post: "Just now", "5 min ago", "3 h ago", "2 days ago", then the date. */
    fun ago(nowMs: Long, atMs: Long): CommunityCopy {
        val minutes = ((nowMs - atMs).coerceAtLeast(0L)) / 60_000L
        return when {
            minutes < 1 -> CommunityCopy("Just now", "अभी")
            minutes < 60 -> CommunityCopy("$minutes min ago", "$minutes मिनट पहले")
            minutes < 24 * 60 -> (minutes / 60).let { CommunityCopy("$it h ago", "$it घंटे पहले") }
            minutes < 7 * 24 * 60 -> (minutes / (24 * 60)).let { if (it == 1L) CommunityCopy("Yesterday", "कल") else CommunityCopy("$it days ago", "$it दिन पहले") }
            else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(atMs)).let { CommunityCopy(it, it) }
        }
    }
}

/** Decodes the jsonb replies of the sahara_community_* functions. Bad rows fail loudly rather than showing half a post. */
internal object CommunityJson {
    private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    fun home(json: JSONObject): CommunityHome {
        val rows = json.getJSONArray("groups")
        return CommunityHome(json.nullableString("my_name"), json.optBoolean("can_moderate"), (0 until rows.length()).map { i ->
            rows.getJSONObject(i).let { CommunityGroup(it.getString("id"), it.getString("name_en"), it.getString("name_hi"), it.optString("about_en"), it.optString("about_hi"), it.optString("tone", "teal"), it.optInt("posts")) }
        })
    }
    private fun reply(it: JSONObject) = CommunityReply(it.getString("id"), it.getString("body"), it.getLong("created_ms"), it.getString("name"), it.optBoolean("mine"), it.optBoolean("by_team"),
        it.optBoolean("hidden"), it.optBoolean("reported"), it.optInt("reports"))
    fun feed(json: JSONObject): CommunityFeed {
        val rows = json.getJSONArray("posts")
        return CommunityFeed(json.optBoolean("can_moderate"), json.nullableString("my_name"), (0 until rows.length()).map { i ->
            val it = rows.getJSONObject(i)
            val replies = it.optJSONArray("replies") ?: JSONArray()
            CommunityPost(it.getString("id"), it.getString("group_id"), it.getString("body"), it.getLong("created_ms"), it.getString("name"), it.optBoolean("mine"), it.optBoolean("by_team"),
                it.optBoolean("hidden"), it.optInt("helpful"), it.optInt("hugs"), it.optBoolean("my_helpful"), it.optBoolean("my_hugs"), it.optBoolean("reported"), it.optInt("reports"),
                (0 until replies.length()).map { r -> reply(replies.getJSONObject(r)) })
        })
    }
}

/** Pure outbox logic behind CommunityStore: bounded, per account, oldest first, each item removed only after the server accepted it. */
internal object CommunityOutbox {
    const val LIMIT = 10
    fun decode(text: String?): List<CommunityPending> {
        if (text == null) return emptyList()
        val rows = JSONArray(text)
        return (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            CommunityPending(row.getString("id"), row.getString("accountId"), row.getString("kind"), row.getString("target"), row.getString("body"), row.getLong("savedAt")).also {
                require(it.id.isNotBlank() && it.accountId.isNotBlank() && it.target.isNotBlank() && it.savedAt > 0)
            }
        }
    }
    fun encode(items: List<CommunityPending>): String = JSONArray(items.map {
        JSONObject().put("id", it.id).put("accountId", it.accountId).put("kind", it.kind).put("target", it.target).put("body", it.body).put("savedAt", it.savedAt)
    }).toString()
    /** Null when the outbox is full; nothing older is ever dropped to make room. */
    fun add(items: List<CommunityPending>, item: CommunityPending): List<CommunityPending>? = if (items.size >= LIMIT) null else items.filterNot { it.id == item.id } + item

    /** Sends [accountId]'s items oldest first. The first failure stops the run and is returned; the rest stay queued. */
    suspend fun flush(items: List<CommunityPending>, accountId: String, send: suspend (CommunityPending) -> Unit, persist: (List<CommunityPending>) -> Boolean): Throwable? {
        var remaining = items
        for (item in items.filter { it.accountId == accountId }.sortedBy { it.savedAt }) {
            try { send(item) } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel } catch (error: Exception) { return error }
            remaining = remaining.filterNot { it.id == item.id }
            if (!persist(remaining)) return IllegalStateException("Sent, but this phone couldn't record that.")
        }
        return null
    }
}
