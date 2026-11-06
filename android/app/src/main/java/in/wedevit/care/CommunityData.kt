package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject

/** One piece of community wording in English and Hindi; screens show it with t(copy.en, copy.hi). */
internal data class CommunityCopy(val en: String, val hi: String)

/** A group as the server lists it (public.community_groups, editable by the team). [tone] picks the tile colour; [joined]: the signed-in member is in it. */
internal data class CommunityGroup(val id: String, val nameEn: String, val nameHi: String, val aboutEn: String, val aboutHi: String, val tone: String, val posts: Int, val joined: Boolean = false)
internal data class CommunityHome(val myName: String?, val canModerate: Boolean, val groups: List<CommunityGroup>)

/** Authors appear by their chosen community name only; [mine] and [byTeam] come from the server, never a user id. */
internal data class CommunityReply(val id: String, val body: String, val createdMs: Long, val name: String, val mine: Boolean, val byTeam: Boolean,
    val hidden: Boolean, val reported: Boolean, val reports: Int)
internal data class CommunityPost(val id: String, val groupId: String, val body: String, val createdMs: Long, val name: String, val mine: Boolean, val byTeam: Boolean,
    val hidden: Boolean, val helpful: Int, val hugs: Int, val myHelpful: Boolean, val myHugs: Boolean, val reported: Boolean, val reports: Int, val replies: List<CommunityReply>,
    val beenThere: Int = 0, val myBeenThere: Boolean = false, val saved: Boolean = false)
internal data class CommunityFeed(val canModerate: Boolean, val myName: String?, val posts: List<CommunityPost>)

/** A post or reply kept on this phone because it could not be sent. [target] is the group id for a post and the post id for a reply. */
internal data class CommunityPending(val id: String, val accountId: String, val kind: String, val target: String, val body: String, val savedAt: Long) {
    init { require(kind == POST || kind == REPLY) }
    companion object { const val POST = "post"; const val REPLY = "reply" }
}

internal object CommunityRules {
    /** The feed id of the member's saved posts (never a group id: those have no underscore). */
    const val SAVED = "_saved"
    const val HELPFUL = "helpful"
    const val HUGS = "hugs"
    const val BEEN_THERE = "been-there"
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

    /**
     * A first name to offer from the sign-in email ("asha.verma@…" -> "Asha"), so joining takes no typing. The person can change it;
     * only the first word is used, so the email itself is never shown. Null when nothing usable is there.
     */
    fun suggestName(email: String?): String? {
        val word = email.orEmpty().substringBefore('@').split(Regex("[^A-Za-z]+")).firstOrNull { it.length >= NAME_MIN } ?: return null
        val name = word.lowercase().replaceFirstChar { it.uppercase() }.take(NAME_MAX)
        return name.takeIf { nameError(it) == null }
    }

    /** Opening words for the three gentle prompts on the write screen. */
    val prompts = listOf(
        CommunityCopy("Ask a question", "सवाल पूछें") to CommunityCopy("My question:", "मेरा सवाल:"),
        CommunityCopy("Share a good moment", "अच्छा पल बाँटें") to CommunityCopy("A good moment today:", "आज का अच्छा पल:"),
        CommunityCopy("Say thank you", "शुक्रिया कहें") to CommunityCopy("Thank you to", "शुक्रिया,"),
    )

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
            rows.getJSONObject(i).let { CommunityGroup(it.getString("id"), it.getString("name_en"), it.getString("name_hi"), it.optString("about_en"), it.optString("about_hi"), it.optString("tone", "teal"), it.optInt("posts"), it.optBoolean("joined")) }
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
                (0 until replies.length()).map { r -> reply(replies.getJSONObject(r)) }, it.optInt("been_there"), it.optBoolean("my_been_there"), it.optBoolean("saved"))
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

/**
 * A member's words in English and Hindi. Built-in posts are member voices, shown like server posts (Hindi when the app is in
 * Hindi, English otherwise), so they are written with named arguments and kept out of the te/ur/kn screen dictionaries.
 */
internal data class CommunityVoice(val en: String, val hi: String)
internal data class CommunityLocalReply(val id: String, val minutesAgo: Long, val name: CommunityVoice, val body: CommunityVoice, val byTeam: Boolean = false)
internal data class CommunityLocalPost(val id: String, val groupId: String, val minutesAgo: Long, val name: CommunityVoice, val body: CommunityVoice,
    val helpful: Int, val hugs: Int, val replies: List<CommunityLocalReply> = emptyList(), val beenThere: Int = 0)

/**
 * What one signed-in account added to the built-in community: its community name, posts and replies (as [CommunityPending] items),
 * reactions ("postId:kind"), reports, the groups it joined and the posts it saved. Kept per account (CommunityStore.local).
 */
internal data class CommunityLocalState(val name: String? = null, val items: List<CommunityPending> = emptyList(), val reactions: Set<String> = emptySet(), val reported: Set<String> = emptySet(),
    val joined: Set<String> = emptySet(), val saved: Set<String> = emptySet())

/**
 * The community shown when Sahara's community server can't be used (signed out, not set up, or Practice): the same four groups
 * as 20261106120000_community.sql, built-in posts from families and patients, and whatever the signed-in account adds. Signed
 * out, it can be read but not joined. The server wins whenever it answers. No medical advice: questions about symptoms or
 * medicines go to the care team.
 */
internal object CommunityLocal {

    val baseGroups = listOf(
        CommunityGroup("family-carers", "Family carers", "परिवार के देखभाल करने वाले", "For family looking after someone at home", "घर पर किसी की देखभाल करने वाले परिवार के लिए", "teal", 0),
        CommunityGroup("living-with-cancer", "Living with cancer", "कैंसर के साथ जीना", "For patients, during and after treatment", "मरीज़ों के लिए, इलाज के दौरान और बाद में", "rose", 0),
        CommunityGroup("grief-loss", "Grief and loss", "दुख और बिछड़ना", "For families after losing someone close", "किसी करीबी को खोने के बाद परिवारों के लिए", "plum", 0),
        CommunityGroup("ask-care-team", "Ask the care team", "देखभाल टीम से पूछें", "General questions. The Sahara team replies", "आम सवाल। Sahara टीम जवाब देती है", "sky", 0),
    )

    private val team = CommunityVoice(en = "Sahara team", hi = "Sahara टीम")
    private val lakshmi = CommunityVoice(en = "Lakshmi's daughter", hi = "लक्ष्मी की बेटी")
    private val suresh = CommunityVoice(en = "Suresh (caring for Papa)", hi = "सुरेश (पापा की देखभाल)")
    private val meena = CommunityVoice(en = "Meena, Jaipur", hi = "मीना, जयपुर")
    private val rafiq = CommunityVoice(en = "Rafiq bhai", hi = "रफ़ीक़ भाई")
    private val anilWife = CommunityVoice(en = "Anil's wife", hi = "अनिल की पत्नी")
    private val kavita = CommunityVoice(en = "Kavita", hi = "कविता")
    private val gopal = CommunityVoice(en = "Gopal ji", hi = "गोपाल जी")
    private val nisha = CommunityVoice(en = "Nisha", hi = "निशा")
    private val farida = CommunityVoice(en = "Farida", hi = "फ़रीदा")
    private val ravi = CommunityVoice(en = "Ravi, Hyderabad", hi = "रवि, हैदराबाद")
    private val kamla = CommunityVoice(en = "Kamla Devi", hi = "कमला देवी")
    private val deepa = CommunityVoice(en = "Deepa, Pune", hi = "दीपा, पुणे")
    private val imran = CommunityVoice(en = "Imran", hi = "इमरान")
    private val sunita = CommunityVoice(en = "Sunita", hi = "सुनीता")
    private val ajay = CommunityVoice(en = "Ajay", hi = "अजय")
    private val priyaMother = CommunityVoice(en = "Priya's mother", hi = "प्रिया की माँ")
    private val harpreet = CommunityVoice(en = "Harpreet", hi = "हरप्रीत")
    private val shabnam = CommunityVoice(en = "Shabnam, Lucknow", hi = "शबनम, लखनऊ")
    private val joseph = CommunityVoice(en = "Joseph uncle", hi = "जोसेफ़ अंकल")

    private const val H = 60L
    private const val D = 24 * 60L

    val posts: List<CommunityLocalPost> = listOf(
        // Family carers
        CommunityLocalPost("local-fc-1", "family-carers", 2 * H, lakshmi,
            CommunityVoice(en = "Amma slept through the night for the first time this week. Small thing, but I wanted to tell people who understand.",
                hi = "इस हफ़्ते पहली बार अम्मा पूरी रात सोईं। छोटी बात है, पर उन लोगों को बताना चाहती थी जो समझते हैं।"), helpful = 4, hugs = 18, replies = listOf(
            CommunityLocalReply("local-fc-1-r1", 90, rafiq, CommunityVoice(en = "That is not small at all. Get some rest yourself today too.", hi = "यह बिल्कुल छोटी बात नहीं है। आज आप भी थोड़ा आराम कर लीजिए।")),
            CommunityLocalReply("local-fc-1-r2", 40, meena, CommunityVoice(en = "So happy for you both. These nights matter.", hi = "आप दोनों के लिए बहुत ख़ुशी हुई। ऐसी रातें बहुत मायने रखती हैं।")),
        )),
        CommunityLocalPost("local-fc-2", "family-carers", 5 * H, suresh,
            CommunityVoice(en = "How do you all manage the nights? My brother and I now take turns. He sits with Papa till two, then I take over. It has helped us both.",
                hi = "आप सब रातें कैसे संभालते हैं? मैं और मेरा भाई अब बारी-बारी से जागते हैं। दो बजे तक वह पापा के पास बैठता है, फिर मैं। इससे हम दोनों को राहत मिली है।"), helpful = 12, hugs = 5, beenThere = 9, replies = listOf(
            CommunityLocalReply("local-fc-2-r1", 4 * H, kavita, CommunityVoice(en = "We do the same with my sister. A small bell by the bed helped Ma call us without raising her voice.", hi = "हम भी बहन के साथ ऐसा ही करते हैं। बिस्तर के पास एक छोटी घंटी रखी, तो माँ बिना ज़ोर से बोले हमें बुला लेती हैं।")),
            CommunityLocalReply("local-fc-2-r2", 3 * H, anilWife, CommunityVoice(en = "Thank you for this. I have been doing every night alone. I will ask my son to share.", hi = "इसके लिए शुक्रिया। मैं हर रात अकेले जाग रही थी। अब बेटे से बात करूँगी।")),
        )),
        CommunityLocalPost("local-fc-3", "family-carers", 26 * H, meena,
            CommunityVoice(en = "What do you say when relatives keep asking \"kitna time hai\"? I don't know how to answer.",
                hi = "जब रिश्तेदार बार-बार पूछते हैं \"कितना समय है\", तो आप क्या कहते हैं? मुझे समझ नहीं आता क्या जवाब दूँ।"), helpful = 9, hugs = 11, replies = listOf(
            CommunityLocalReply("local-fc-3-r1", 24 * H, rafiq, CommunityVoice(en = "I tell them we are taking one day at a time. Most people stop asking after that.", hi = "मैं कहता हूँ कि हम एक-एक दिन करके चल रहे हैं। उसके बाद ज़्यादातर लोग पूछना छोड़ देते हैं।")),
            CommunityLocalReply("local-fc-3-r2", 20 * H, team, CommunityVoice(en = "This is a hard question. If you'd like, your care team can talk with the family about what to expect. You can ask them at the next visit.", hi = "यह मुश्किल सवाल है। आप चाहें तो आपकी देखभाल टीम परिवार से बात कर सकती है कि आगे क्या हो सकता है। अगली मुलाक़ात में उनसे पूछ लीजिए।"), byTeam = true),
        )),
        CommunityLocalPost("local-fc-4", "family-carers", 3 * D, rafiq,
            CommunityVoice(en = "I keep a small notebook by Abbu's bed: when he ate, how he slept, what he asked about. At the clinic I just hand it over. The doctor said it helps a lot.",
                hi = "मैं अब्बू के बिस्तर के पास एक छोटी कॉपी रखता हूँ: कब खाया, कैसे सोए, क्या पूछा। क्लिनिक में बस वही दे देता हूँ। डॉक्टर ने कहा इससे बहुत मदद मिलती है।"), helpful = 21, hugs = 3, replies = listOf(
            CommunityLocalReply("local-fc-4-r1", 2 * D, suresh, CommunityVoice(en = "Starting this today. Thank you, bhai.", hi = "आज से ही शुरू करता हूँ। शुक्रिया, भाई।")),
        )),
        CommunityLocalPost("local-fc-5", "family-carers", 5 * D, anilWife,
            CommunityVoice(en = "Some days I feel angry, and then guilty for feeling angry. Is this normal?",
                hi = "कुछ दिन मुझे ग़ुस्सा आता है, और फिर ग़ुस्सा आने पर ही बुरा लगता है। क्या ऐसा होना आम है?"), helpful = 6, hugs = 24, beenThere = 15, replies = listOf(
            CommunityLocalReply("local-fc-5-r1", 5 * D - 3 * H, lakshmi, CommunityVoice(en = "Yes. I feel it too. It doesn't mean you love him any less.", hi = "हाँ। मुझे भी ऐसा लगता है। इसका मतलब यह नहीं कि आप उनसे कम प्यार करती हैं।")),
            CommunityLocalReply("local-fc-5-r2", 4 * D, team, CommunityVoice(en = "Many carers feel this. Please look after yourself too. If it feels like too much, tell your care team. They are there for you as well, not only for the patient.", hi = "बहुत से देखभाल करने वाले ऐसा महसूस करते हैं। अपना भी ध्यान रखिए। अगर बोझ ज़्यादा लगे, तो अपनी देखभाल टीम को बताइए। वे सिर्फ़ मरीज़ के लिए नहीं, आपके लिए भी हैं।"), byTeam = true),
        )),

        CommunityLocalPost("local-fc-6", "family-carers", 7 * D, harpreet,
            CommunityVoice(en = "Thank you to whoever here said to ask for help. Today our neighbour sat with Maa for an hour and I went for a walk. First time in weeks.",
                hi = "जिसने भी यहाँ मदद माँगने को कहा था, उसका शुक्रिया। आज पड़ोसन एक घंटा माँ के पास बैठीं और मैं टहलने गई। हफ़्तों में पहली बार।"), helpful = 5, hugs = 14, beenThere = 7, replies = listOf(
            CommunityLocalReply("local-fc-6-r1", 6 * D, suresh, CommunityVoice(en = "Good. That hour is for both of you.", hi = "अच्छा किया। वह एक घंटा आप दोनों के लिए है।")),
        )),

        // Living with cancer
        CommunityLocalPost("local-lc-1", "living-with-cancer", 40, gopal,
            CommunityVoice(en = "Good day today. Sat on the balcony in the sun for an hour and listened to old Rafi songs. Grateful.",
                hi = "आज अच्छा दिन रहा। एक घंटा बालकनी में धूप में बैठा और पुराने रफ़ी के गाने सुने। शुक्रगुज़ार हूँ।"), helpful = 3, hugs = 15, replies = listOf(
            CommunityLocalReply("local-lc-1-r1", 25, nisha, CommunityVoice(en = "Rafi songs on a sunny day. Nothing better, Gopal ji.", hi = "धूप और रफ़ी के गाने। इससे अच्छा क्या, गोपाल जी।")),
        )),
        CommunityLocalPost("local-lc-2", "living-with-cancer", 4 * H, nisha,
            CommunityVoice(en = "Bad day. Tired of being tired. Not looking for advice, just wanted to say it somewhere.",
                hi = "आज बुरा दिन है। थक-थक कर थक गई हूँ। सलाह नहीं चाहिए, बस कहीं कहना था।"), helpful = 2, hugs = 31, beenThere = 11, replies = listOf(
            CommunityLocalReply("local-lc-2-r1", 3 * H, gopal, CommunityVoice(en = "Said and heard, beta. Tomorrow may be lighter.", hi = "कह दिया और हमने सुन लिया, बेटा। कल शायद हल्का हो।")),
            CommunityLocalReply("local-lc-2-r2", 2 * H, farida, CommunityVoice(en = "Sending you a hug. Some days are just like this.", hi = "आपको झप्पी भेज रही हूँ। कुछ दिन बस ऐसे ही होते हैं।")),
        )),
        CommunityLocalPost("local-lc-3", "living-with-cancer", 28 * H, farida,
            CommunityVoice(en = "My treatment is done for now. My grandchildren made me a card. Sharing it with you because you all held me up on the hard days.",
                hi = "अभी के लिए मेरा इलाज पूरा हुआ। पोते-पोतियों ने मेरे लिए एक कार्ड बनाया। आप सबसे बाँट रही हूँ, क्योंकि मुश्किल दिनों में आप सबने मुझे संभाला।"), helpful = 5, hugs = 27, replies = listOf(
            CommunityLocalReply("local-lc-3-r1", 22 * H, nisha, CommunityVoice(en = "This made me smile today. Thank you, Farida aunty.", hi = "इसे पढ़कर आज मुस्कान आ गई। शुक्रिया, फ़रीदा आंटी।")),
        )),
        CommunityLocalPost("local-lc-4", "living-with-cancer", 2 * D, ravi,
            CommunityVoice(en = "Food tastes different these days. I told my care team and they gave me ideas that suit me. Eating at the table with family also makes it easier.",
                hi = "आजकल खाने का स्वाद अलग लगता है। मैंने अपनी देखभाल टीम को बताया, उन्होंने मेरे हिसाब से कुछ सुझाव दिए। परिवार के साथ बैठकर खाने से भी आसानी होती है।"), helpful = 14, hugs = 4, replies = listOf(
            CommunityLocalReply("local-lc-4-r1", 2 * D - 5 * H, team, CommunityVoice(en = "Thank you for sharing, Ravi. Taste often changes. Your care team is the right place to ask what suits you.", hi = "बताने के लिए शुक्रिया, रवि। स्वाद अक्सर बदल जाता है। आपके लिए क्या ठीक है, यह अपनी देखभाल टीम से ही पूछिए।"), byTeam = true),
        )),
        CommunityLocalPost("local-lc-5", "living-with-cancer", 6 * D, kamla,
            CommunityVoice(en = "My daughter reads the newspaper to me every morning now. I pretend I can't find my glasses so she keeps doing it.",
                hi = "अब मेरी बेटी हर सुबह मुझे अख़बार पढ़कर सुनाती है। मैं ऐसे जताती हूँ कि चश्मा नहीं मिल रहा, ताकि वह पढ़ती रहे।"), helpful = 8, hugs = 22, replies = listOf(
            CommunityLocalReply("local-lc-5-r1", 6 * D - 2 * H, gopal, CommunityVoice(en = "Very wise, Kamla ji.", hi = "बहुत समझदारी की बात, कमला जी।")),
        )),

        CommunityLocalPost("local-lc-6", "living-with-cancer", 8 * D, shabnam,
            CommunityVoice(en = "My question: how did you tell young children? My son is eight and he knows something is wrong.",
                hi = "मेरा सवाल: आपने छोटे बच्चों को कैसे बताया? मेरा बेटा आठ साल का है और समझता है कि कुछ ठीक नहीं है।"), helpful = 11, hugs = 13, beenThere = 9, replies = listOf(
            CommunityLocalReply("local-lc-6-r1", 7 * D, farida, CommunityVoice(en = "We told our grandchildren in simple words, a little at a time. They handled it better than we feared.", hi = "हमने पोते-पोतियों को आसान शब्दों में, थोड़ा-थोड़ा करके बताया। वे हमारे डर से कहीं बेहतर संभल गए।")),
            CommunityLocalReply("local-lc-6-r2", 6 * D, team, CommunityVoice(en = "Many families find this hard. Your care team or a counsellor can help you find words that suit his age.", hi = "बहुत से परिवारों को यह मुश्किल लगता है। आपकी देखभाल टीम या कोई काउंसलर उसकी उम्र के हिसाब से शब्द ढूँढने में मदद कर सकते हैं।"), byTeam = true),
        )),

        // Grief and loss
        CommunityLocalPost("local-gl-1", "grief-loss", 3 * H, deepa,
            CommunityVoice(en = "Three months since Papa left. I still make two cups of chai in the morning.",
                hi = "पापा को गए तीन महीने हो गए। सुबह अब भी दो कप चाय बना लेती हूँ।"), helpful = 2, hugs = 34, beenThere = 13, replies = listOf(
            CommunityLocalReply("local-gl-1-r1", 2 * H, imran, CommunityVoice(en = "I set a plate for Ammi for a long time. Be gentle with yourself.", hi = "मैं बहुत समय तक अम्मी की थाली लगाता रहा। ख़ुद के साथ नरमी रखिए।")),
            CommunityLocalReply("local-gl-1-r2", 1 * H, team, CommunityVoice(en = "We're so sorry, Deepa. If the sadness feels too heavy, please talk to your doctor or a counsellor. You don't have to carry it alone.", hi = "हमें बहुत दुख है, दीपा। अगर दुख बहुत भारी लगे, तो अपने डॉक्टर या किसी काउंसलर से बात कीजिए। यह बोझ अकेले मत उठाइए।"), byTeam = true),
        )),
        CommunityLocalPost("local-gl-2", "grief-loss", 30 * H, imran,
            CommunityVoice(en = "What helped me most was talking about Ammi, not around her. Tell me about the people you miss, if you want to.",
                hi = "मुझे सबसे ज़्यादा मदद अम्मी के बारे में खुलकर बात करने से मिली। आप चाहें तो उन लोगों के बारे में बताइए जिन्हें आप याद करते हैं।"), helpful = 16, hugs = 12, replies = listOf(
            CommunityLocalReply("local-gl-2-r1", 27 * H, sunita, CommunityVoice(en = "My husband loved gardening. His tulsi is still growing on our balcony.", hi = "मेरे पति को बाग़बानी बहुत पसंद थी। उनकी तुलसी अब भी हमारी बालकनी में बढ़ रही है।")),
            CommunityLocalReply("local-gl-2-r2", 20 * H, deepa, CommunityVoice(en = "Papa told the worst jokes. I miss them so much.", hi = "पापा सबसे बेकार चुटकुले सुनाते थे। मुझे उनकी बहुत याद आती है।")),
        )),
        CommunityLocalPost("local-gl-3", "grief-loss", 4 * D, sunita,
            CommunityVoice(en = "The first Diwali without him is coming. Did anyone do something different that year?",
                hi = "उनके बिना पहली दिवाली आने वाली है। क्या किसी ने उस साल कुछ अलग किया था?"), helpful = 7, hugs = 19, replies = listOf(
            CommunityLocalReply("local-gl-3-r1", 4 * D - 6 * H, imran, CommunityVoice(en = "We lit a small diya for Ammi and everyone shared one memory. It hurt, and it also helped.", hi = "हमने अम्मी के लिए एक छोटा दीया जलाया और सबने एक याद बाँटी। दर्द भी हुआ, और सुकून भी मिला।")),
        )),
        CommunityLocalPost("local-gl-4", "grief-loss", 5 * D, priyaMother,
            CommunityVoice(en = "Sorted her clothes today. I could only do one drawer. That is enough for today.",
                hi = "आज उसके कपड़े समेटे। बस एक दराज़ कर पाई। आज के लिए इतना काफ़ी है।"), helpful = 3, hugs = 26, replies = listOf(
            CommunityLocalReply("local-gl-4-r1", 5 * D - 4 * H, deepa, CommunityVoice(en = "One drawer is plenty. Go slow.", hi = "एक दराज़ बहुत है। धीरे-धीरे कीजिए।")),
        )),
        CommunityLocalPost("local-gl-5", "grief-loss", 8 * D, ajay,
            CommunityVoice(en = "Grief comes in waves. Some days feel almost normal, then a song on the radio and I'm back at the start.",
                hi = "दुख लहरों में आता है। कुछ दिन लगभग सामान्य लगते हैं, फिर रेडियो पर एक गाना और मैं फिर वहीं पहुँच जाता हूँ।"), helpful = 10, hugs = 21, beenThere = 10, replies = listOf(
            CommunityLocalReply("local-gl-5-r1", 7 * D, sunita, CommunityVoice(en = "Waves is the right word. You are not back at the start, even if it feels that way.", hi = "लहरें, सही शब्द है। भले ऐसा लगे, पर आप फिर शुरुआत पर नहीं हैं।")),
        )),

        CommunityLocalPost("local-gl-6", "grief-loss", 10 * D, joseph,
            CommunityVoice(en = "A good moment today: my granddaughter laughed exactly like her grandmother. It hurt and it was beautiful.",
                hi = "आज का अच्छा पल: मेरी पोती बिल्कुल अपनी दादी की तरह हँसी। दर्द भी हुआ और बहुत सुंदर भी लगा।"), helpful = 4, hugs = 29, beenThere = 12, replies = listOf(
            CommunityLocalReply("local-gl-6-r1", 9 * D, deepa, CommunityVoice(en = "Both at once. I know that feeling.", hi = "दोनों एक साथ। यह एहसास मैं जानती हूँ।")),
        )),

        // Ask the care team
        CommunityLocalPost("local-ac-1", "ask-care-team", 1 * H, meena,
            CommunityVoice(en = "Can I ask the doctor to explain the report in Hindi? I feel shy asking again.",
                hi = "क्या मैं डॉक्टर से रिपोर्ट हिंदी में समझाने को कह सकती हूँ? दोबारा पूछने में झिझक होती है।"), helpful = 11, hugs = 2, replies = listOf(
            CommunityLocalReply("local-ac-1-r1", 30, team, CommunityVoice(en = "Yes, always. Ask the doctor or nurse to explain in the language you're most comfortable with, as many times as you need. Writing your questions down before the visit helps.", hi = "हाँ, बिल्कुल। डॉक्टर या नर्स से उस भाषा में समझाने को कहिए जिसमें आप सहज हैं, जितनी बार ज़रूरत हो। मुलाक़ात से पहले सवाल लिख लेने से मदद मिलती है।"), byTeam = true),
        )),
        CommunityLocalPost("local-ac-2", "ask-care-team", 6 * H, suresh,
            CommunityVoice(en = "Papa's pain seems worse in the evenings. Should we change when he takes his medicines?",
                hi = "पापा का दर्द शाम को ज़्यादा लगता है। क्या हम दवाओं का समय बदल दें?"), helpful = 6, hugs = 4, replies = listOf(
            CommunityLocalReply("local-ac-2-r1", 5 * H, team, CommunityVoice(en = "Please don't change medicines on your own. Tell your care team today what you've noticed, including when it starts. Pain can usually be eased, and they will check what suits him.", hi = "कृपया दवाएँ अपने आप न बदलें। आज ही अपनी देखभाल टीम को बताइए कि आपने क्या देखा, और दर्द कब शुरू होता है। दर्द को अक्सर कम किया जा सकता है, वे देखेंगे कि उनके लिए क्या ठीक है।"), byTeam = true),
        )),
        CommunityLocalPost("local-ac-3", "ask-care-team", 2 * D, kavita,
            CommunityVoice(en = "What should we keep ready when the palliative team visits at home?",
                hi = "जब पैलिएटिव टीम घर आए, तो हमें क्या तैयार रखना चाहिए?"), helpful = 15, hugs = 1, replies = listOf(
            CommunityLocalReply("local-ac-3-r1", 2 * D - 2 * H, team, CommunityVoice(en = "Keep the medicine boxes or list, recent reports and your questions in one place. Sahara's Care tab can hold these too.", hi = "दवाओं के डिब्बे या सूची, हाल की रिपोर्टें और अपने सवाल एक जगह रखिए। Sahara के देखभाल टैब में भी ये रख सकते हैं।"), byTeam = true),
            CommunityLocalReply("local-ac-3-r2", D, rafiq, CommunityVoice(en = "A glass of water and a chair near the bed. Small things, but the nurse thanked us.", hi = "एक गिलास पानी और बिस्तर के पास एक कुर्सी। छोटी बातें हैं, पर नर्स ने शुक्रिया कहा।")),
        )),
        CommunityLocalPost("local-ac-4", "ask-care-team", 3 * D, anilWife,
            CommunityVoice(en = "Is it okay to feel relief on some days, when he is calm and sleeping?",
                hi = "क्या कुछ दिनों में राहत महसूस करना ठीक है, जब वे शांत होकर सो रहे होते हैं?"), helpful = 4, hugs = 9, replies = listOf(
            CommunityLocalReply("local-ac-4-r1", 3 * D - 4 * H, team, CommunityVoice(en = "Yes. Feeling relief doesn't mean you care less. If you want to talk it through, your care team can put you in touch with a counsellor.", hi = "हाँ। राहत महसूस करने का मतलब यह नहीं कि आपको कम फ़िक्र है। अगर बात करना चाहें, तो आपकी देखभाल टीम किसी काउंसलर से मिलवा सकती है।"), byTeam = true),
        )),
        CommunityLocalPost("local-ac-5", "ask-care-team", 6 * D, farida,
            CommunityVoice(en = "Who should we call at night if something worries us?",
                hi = "रात में कोई बात परेशान करे तो हमें किसे फ़ोन करना चाहिए?"), helpful = 13, hugs = 2, replies = listOf(
            CommunityLocalReply("local-ac-5-r1", 6 * D - 3 * H, team, CommunityVoice(en = "Ask your care team now, on a calm day, whom to call at night, and write it on the first page of your file. Sahara's Care plan can keep it too.", hi = "किसी शांत दिन अभी अपनी देखभाल टीम से पूछ लीजिए कि रात में किसे फ़ोन करें, और उसे अपनी फ़ाइल के पहले पन्ने पर लिख लीजिए। Sahara की देखभाल योजना में भी इसे रख सकते हैं।"), byTeam = true),
        )),
        CommunityLocalPost("local-ac-6", "ask-care-team", 9 * D, harpreet,
            CommunityVoice(en = "Is it okay to take a day off from the hospital visits? I feel guilty even asking.",
                hi = "क्या अस्पताल जाने से एक दिन की छुट्टी लेना ठीक है? पूछते हुए भी बुरा लग रहा है।"), helpful = 10, hugs = 8, beenThere = 6, replies = listOf(
            CommunityLocalReply("local-ac-6-r1", 9 * D - 3 * H, team, CommunityVoice(en = "Yes. Rest helps you keep going. Let the nurse or care team know which days you'll be there, so they can reach you.", hi = "हाँ। आराम से ही आप आगे चल पाएँगे। नर्स या देखभाल टीम को बता दीजिए कि आप किन दिनों में आएँगे, ताकि वे आपसे बात कर सकें।"), byTeam = true),
            CommunityLocalReply("local-ac-6-r2", 8 * D, rafiq, CommunityVoice(en = "I take Sundays off. Abbu insisted.", hi = "मैं रविवार की छुट्टी लेता हूँ। अब्बू ने ही ज़िद की थी।")),
        )),
    )

    private fun mine(state: CommunityLocalState, kind: String) = state.items.filter { it.kind == kind }

    /** [builtIn] false (outside Practice, owner 6 Nov) leaves out the built-in posts: only this phone's own posts show. */
    fun home(state: CommunityLocalState, builtIn: Boolean = true): CommunityHome = CommunityHome(state.name, canModerate = false, groups = baseGroups.map { group ->
        group.copy(posts = (if (builtIn) posts.count { it.groupId == group.id } else 0) + mine(state, CommunityPending.POST).count { it.target == group.id }, joined = group.id in state.joined)
    })

    /**
     * Newest first. Built-in times are counted back from [anchorMs] (when the screen opened), so they read as recent; [pick] chooses
     * English or Hindi. [groupId] [CommunityRules.SAVED] lists the saved posts from every group.
     */
    fun feed(groupId: String, state: CommunityLocalState, anchorMs: Long, builtIn: Boolean = true, pick: (CommunityVoice) -> String): CommunityFeed {
        val wanted = { postGroup: String, postId: String -> if (groupId == CommunityRules.SAVED) postId in state.saved else postGroup == groupId }
        val myName = state.name.orEmpty()
        val replies = mine(state, CommunityPending.REPLY)
        fun counted(id: String, kind: String) = if ("$id:$kind" in state.reactions) 1 else 0
        fun yours(postId: String) = replies.filter { it.target == postId }.map { CommunityReply(it.id, it.body, it.savedAt, myName, mine = true, byTeam = false, hidden = false, reported = false, reports = 0) }
        val built = posts.filter { builtIn && wanted(it.groupId, it.id) }.map { post ->
            CommunityPost(post.id, post.groupId, pick(post.body), anchorMs - post.minutesAgo * 60_000L, pick(post.name), mine = false, byTeam = false, hidden = false,
                helpful = post.helpful + counted(post.id, "helpful"), hugs = post.hugs + counted(post.id, "hugs"), myHelpful = counted(post.id, "helpful") == 1, myHugs = counted(post.id, "hugs") == 1,
                reported = post.id in state.reported, reports = 0,
                replies = post.replies.map { CommunityReply(it.id, pick(it.body), anchorMs - it.minutesAgo * 60_000L, pick(it.name), mine = false, byTeam = it.byTeam, hidden = false, reported = it.id in state.reported, reports = 0) } + yours(post.id),
                beenThere = post.beenThere + counted(post.id, CommunityRules.BEEN_THERE), myBeenThere = counted(post.id, CommunityRules.BEEN_THERE) == 1, saved = post.id in state.saved)
        }
        val written = mine(state, CommunityPending.POST).filter { wanted(it.target, it.id) }.map { post ->
            CommunityPost(post.id, post.target, post.body, post.savedAt, myName, mine = true, byTeam = false, hidden = false,
                helpful = counted(post.id, "helpful"), hugs = counted(post.id, "hugs"), myHelpful = counted(post.id, "helpful") == 1, myHugs = counted(post.id, "hugs") == 1,
                reported = false, reports = 0, replies = yours(post.id),
                beenThere = counted(post.id, CommunityRules.BEEN_THERE), myBeenThere = counted(post.id, CommunityRules.BEEN_THERE) == 1, saved = post.id in state.saved)
        }
        return CommunityFeed(canModerate = false, myName = state.name, posts = (written + built).sortedByDescending { it.createdMs })
    }

    /** Adds a post or reply; a post also joins its group, as on the server. */
    fun add(state: CommunityLocalState, item: CommunityPending): CommunityLocalState = state.copy(items = state.items.filterNot { it.id == item.id } + item,
        joined = if (item.kind == CommunityPending.POST) state.joined + item.target else state.joined)
    fun join(state: CommunityLocalState, groupId: String, on: Boolean): CommunityLocalState = state.copy(joined = if (on) state.joined + groupId else state.joined - groupId)
    fun save(state: CommunityLocalState, postId: String, on: Boolean): CommunityLocalState = state.copy(saved = if (on) state.saved + postId else state.saved - postId)
    fun react(state: CommunityLocalState, postId: String, kind: String): CommunityLocalState = "$postId:$kind".let { key -> state.copy(reactions = if (key in state.reactions) state.reactions - key else state.reactions + key) }
    fun report(state: CommunityLocalState, id: String): CommunityLocalState = state.copy(reported = state.reported + id)
    /** Removes this phone's post (with the replies under it) or reply. Built-in posts are never removed. */
    fun remove(state: CommunityLocalState, id: String): CommunityLocalState =
        state.copy(items = state.items.filterNot { it.id == id || (it.kind == CommunityPending.REPLY && it.target == id) }, reactions = state.reactions.filterNot { it.startsWith("$id:") }.toSet(), saved = state.saved - id)

    fun encode(state: CommunityLocalState): String = JSONObject().put("name", state.name ?: JSONObject.NULL).put("items", JSONArray(CommunityOutbox.encode(state.items)))
        .put("reactions", JSONArray(state.reactions.sorted())).put("reported", JSONArray(state.reported.sorted()))
        .put("joined", JSONArray(state.joined.sorted())).put("saved", JSONArray(state.saved.sorted())).toString()
    fun decode(text: String?): CommunityLocalState {
        if (text == null) return CommunityLocalState()
        val json = JSONObject(text)
        fun strings(key: String) = json.optJSONArray(key)?.let { a -> (0 until a.length()).map(a::getString).toSet() }.orEmpty()
        return CommunityLocalState(if (json.isNull("name")) null else json.optString("name").ifBlank { null }, CommunityOutbox.decode(json.optJSONArray("items")?.toString()), strings("reactions"), strings("reported"),
            strings("joined"), strings("saved"))
    }
}
