package `in`.wedevit.care

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CommunityRulesTest {
    private val hindi = Regex("[\\u0900-\\u097F]")
    private fun assertHindi(copy: CommunityCopy) { assertTrue("No Hindi for: ${copy.en}", hindi.containsMatchIn(copy.hi)); assertTrue(copy.en.isNotBlank()) }

    @Test fun postsNeedWordsAndStayUnderTheLimit() {
        assertNotNull(CommunityRules.postError(" a "))
        assertNull(CommunityRules.postError("Hello"))
        assertNull(CommunityRules.postError("x".repeat(CommunityRules.POST_MAX)))
        assertNotNull(CommunityRules.postError("x".repeat(CommunityRules.POST_MAX + 1)))
        assertNotNull(CommunityRules.replyError("   "))
        assertNull(CommunityRules.replyError("Yes"))
        assertNotNull(CommunityRules.replyError("x".repeat(CommunityRules.REPLY_MAX + 1)))
    }

    @Test fun contactDetailsAndLinksAreKeptOutButDatesAndDosesAreFine() {
        listOf("Call 98765 43210", "my number 9876543210", "+91-98765-43210", "write to me@example.com", "see https://example.com", "www.example.com").forEach {
            assertTrue(it, CommunityRules.hasContact(it)); assertNotNull(it, CommunityRules.postError(it))
        }
        listOf("Scan on 2026-11-06 went fine", "Morphine 10 mg at 8 and 20", "Room 1204, ward 3", "4 days, 2 hours").forEach {
            assertFalse(it, CommunityRules.hasContact(it)); assertNull(it, CommunityRules.postError(it))
        }
    }

    @Test fun namesHideContactsAndCannotPoseAsTheTeam() {
        assertNull(CommunityRules.nameError("Asha's daughter"))
        listOf("A", "x".repeat(41), "me@example.com", "98765 43210", "Sahara team").forEach { assertNotNull(it, CommunityRules.nameError(it)) }
    }

    @Test fun serverRefusalsMapToCalmBilingualMessages() {
        fun server(message: String) = CommunityRules.serverMessage(CareHttpException(400, message, "P0001"))
        val posts = server("Daily post limit reached.")
        assertTrue(posts.en.contains("${CommunityRules.POSTS_PER_DAY} posts today") && posts.en.contains("tomorrow")); assertHindi(posts)
        val replies = server("Daily reply limit reached.")
        assertTrue(replies.en.contains("${CommunityRules.REPLIES_PER_DAY} replies")); assertHindi(replies)
        assertNotEquals(posts, replies)
        listOf("Daily report limit reached.", "Choose a community name first.", "Name not allowed.", "Post length not allowed.", "Reply length not allowed.",
            "Remove contact details and links.", "This post is no longer available.", "Group not found.", "Only the Sahara team can do this.").forEach { message ->
            val copy = server(message); assertHindi(copy)
            assertNotEquals(message, CommunityRules.serverMessage(Exception("unrelated")), copy)
        }
        val offline = CommunityRules.serverMessage(CareSyncOffline("Couldn't reach your care records. Your entry is still here."))
        assertTrue(offline.en.startsWith("You're offline")); assertHindi(offline)
        val notOpen = CommunityRules.serverMessage(CareHttpException(404, "Couldn't save. Your entry is still here.", "PGRST202"))
        assertTrue(notOpen.en.contains("isn't open yet")); assertHindi(notOpen)
        assertTrue(CommunityRules.serverMessage(CareHttpException(403, "You don't have permission to change these records.", "42501")).en.startsWith("Sign in"))
        assertTrue(CommunityRules.serverMessage(CareHttpException(429, "Please wait a moment and try again.")).en.startsWith("Please wait"))
        assertHindi(CommunityRules.serverMessage(Exception("boom")))
        // No alarm words in anything a member reads.
        val all = listOf(posts, replies, offline, notOpen) + CommunityRules.reportReasons.map { it.second }
        all.forEach { assertFalse(it.en, Regex("!|ERROR|FAILED|CRITICAL").containsMatchIn(it.en)); assertHindi(it) }
    }

    @Test fun reportReasonsMatchTheServerCheck() {
        val sql = listOf("supabase/migrations", "android/supabase/migrations", "../supabase/migrations").map(::File).first { it.isDirectory }.resolve("20261106120000_community.sql").readText()
        assertEquals(listOf("unkind", "harmful-advice", "spam", "private", "other"), CommunityRules.reportReasons.map { it.first })
        CommunityRules.reportReasons.forEach { assertTrue(it.first, sql.contains("'${it.first}'")) }
        assertTrue(sql.contains(">= ${CommunityRules.POSTS_PER_DAY} then") && sql.contains(">= ${CommunityRules.REPLIES_PER_DAY} then"))
        assertTrue(sql.contains("between ${CommunityRules.POST_MIN} and ${CommunityRules.POST_MAX}") && sql.contains("between 1 and ${CommunityRules.REPLY_MAX}"))
        assertTrue(sql.contains("between ${CommunityRules.NAME_MIN} and ${CommunityRules.NAME_MAX}"))
    }

    @Test fun relativeTimesReadNaturally() {
        val now = 10_000_000_000L
        assertEquals("Just now", CommunityRules.ago(now, now - 20_000).en)
        assertEquals("5 min ago", CommunityRules.ago(now, now - 5 * 60_000).en)
        assertEquals("3 h ago", CommunityRules.ago(now, now - 3 * 3_600_000).en)
        assertEquals("Yesterday", CommunityRules.ago(now, now - 30 * 3_600_000L).en)
        assertEquals("3 days ago", CommunityRules.ago(now, now - 3 * 86_400_000L).en)
        assertEquals("Just now", CommunityRules.ago(now, now + 60_000).en) // phone clock behind the server
    }

    @Test fun feedDecodesNamesFlagsAndInlineReplies() {
        val json = JSONObject("""{"can_moderate":false,"my_name":null,"posts":[{"id":"p1","group_id":"family-carers","body":"How do you rest?","created_ms":5,"name":"Asha's daughter",
            "mine":false,"by_team":false,"hidden":false,"helpful":2,"hugs":1,"my_helpful":true,"my_hugs":false,"reported":false,"reports":0,
            "replies":[{"id":"r1","body":"Short walks.","created_ms":6,"name":"Nurse Priya","mine":false,"by_team":true,"hidden":false,"reported":false,"reports":0}]}]}""")
        val feed = CommunityJson.feed(json)
        assertNull(feed.myName)
        val post = feed.posts.single()
        assertEquals("Asha's daughter", post.name); assertEquals(2, post.helpful); assertTrue(post.myHelpful)
        assertTrue(post.replies.single().byTeam)
        val home = CommunityJson.home(JSONObject("""{"my_name":"Ravi","can_moderate":true,"groups":[{"id":"grief-loss","name_en":"Grief and loss","name_hi":"दुख और बिछड़ना","about_en":"","about_hi":"","tone":"plum","posts":3}]}"""))
        assertEquals("Ravi", home.myName); assertTrue(home.canModerate); assertEquals(3, home.groups.single().posts)
    }

    private fun item(id: String, account: String = "a", at: Long = 1L) = CommunityPending(id, account, CommunityPending.POST, "family-carers", "Hello all", at)

    @Test fun outboxIsBoundedPerAccountAndSendsOldestFirst() = runBlocking {
        assertEquals(listOf(item("1"), item("2")), CommunityOutbox.decode(CommunityOutbox.encode(listOf(item("1"), item("2")))))
        assertEquals(emptyList<CommunityPending>(), CommunityOutbox.decode(null))
        val full = (1..CommunityOutbox.LIMIT).map { item("id-$it") }
        assertNull(CommunityOutbox.add(full, item("extra")))
        assertEquals(1, CommunityOutbox.add(listOf(item("x")), item("x"))!!.size) // the same id twice stays once
        val queue = listOf(item("late", at = 9), item("other", account = "b"), item("early", at = 2))
        val sent = mutableListOf<String>(); var stored = queue
        assertNull(CommunityOutbox.flush(queue, "a", { sent += it.id }) { stored = it; true })
        assertEquals(listOf("early", "late"), sent)
        assertEquals(listOf("other"), stored.map { it.id })
        val offline = CommunityOutbox.flush(queue, "a", { if (it.id == "late") throw CareSyncOffline("offline") }) { stored = it; true }
        assertTrue(offline is CareSyncOffline)
        assertEquals(setOf("late", "other"), stored.map { it.id }.toSet())
    }

    @Test fun noSampleOrComingSoonWordingIsLeft() {
        val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
        val ui = File(dir, "CareCommunity.kt").readText()
        listOf("Coming soon", "जल्द आ रहा है", "Sample", "नमूना", "CommunitySample", "Mock").forEach { assertFalse(it, ui.contains(it)) }
        val missing = HindiScan.missing(ui, setOf("community pages"))
        assertTrue("English-only text in CareCommunity.kt:\n" + missing.joinToString("\n"), missing.isEmpty())
        // The built-in community reads as the community itself: no sample, mock, demo or coming-soon wording in any of its words.
        val banned = Regex("sample|mock|simulat|demo|dummy|fake|coming soon|placeholder|नमूना|नकली|जल्द आ रहा", RegexOption.IGNORE_CASE)
        localVoices().forEach { assertFalse(it.en, banned.containsMatchIn(it.en) || banned.containsMatchIn(it.hi)) }
    }

    private fun localVoices(): List<CommunityVoice> = CommunityLocal.posts.flatMap { post -> listOf(post.name, post.body) + post.replies.flatMap { listOf(it.name, it.body) } }

    @Test fun builtInCommunityMatchesTheServerGroupsAndIsLively() {
        val sql = listOf("supabase/migrations", "android/supabase/migrations", "../supabase/migrations").map(::File).first { it.isDirectory }.resolve("20261106120000_community.sql").readText()
        assertEquals(listOf("family-carers", "living-with-cancer", "grief-loss", "ask-care-team"), CommunityLocal.baseGroups.map { it.id })
        CommunityLocal.baseGroups.forEach { g -> assertTrue(g.id, sql.contains("('${g.id}', '${g.nameEn}', '${g.nameHi}', '${g.aboutEn}', '${g.aboutHi}', '${g.tone}'")) }
        val home = CommunityLocal.home(CommunityLocalState())
        home.groups.forEach { g ->
            assertTrue("${g.id} needs about five posts", g.posts in 4..6)
            val feed = CommunityLocal.feed(g.id, CommunityLocalState(), 1_000_000_000L) { it.en }
            assertEquals(g.posts, feed.posts.size)
            assertEquals(feed.posts.sortedByDescending { it.createdMs }, feed.posts)
            assertTrue(feed.posts.all { it.replies.isNotEmpty() && (it.helpful + it.hugs) > 0 })
        }
        val ids = CommunityLocal.posts.map { it.id } + CommunityLocal.posts.flatMap { p -> p.replies.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
        // Every question to the care team has a Sahara team answer.
        CommunityLocal.posts.filter { it.groupId == "ask-care-team" }.forEach { post -> assertTrue(post.id, post.replies.any { it.byTeam }) }
        // Replies come after their post.
        CommunityLocal.posts.forEach { post -> post.replies.forEach { assertTrue(it.id, it.minutesAgo < post.minutesAgo) } }
    }

    @Test fun builtInWordsAreSafeAndBilingual() {
        val dose = Regex("\\d\\s*(mg|ml|mcg|g|tab|tablet|drops?|units?)\\b|\\bdose|\\bdosage|खुराक|मिलीग्राम", RegexOption.IGNORE_CASE)
        val drugs = Regex("morphine|paracetamol|tramadol|fentanyl|ondansetron|dexamethasone|ibuprofen|codeine|oxycodone|मॉर्फ़ीन|मॉर्फिन|पैरासिटामोल", RegexOption.IGNORE_CASE)
        localVoices().forEach { v ->
            assertTrue("No Hindi for: ${v.en}", hindi.containsMatchIn(v.hi)); assertTrue(v.en.isNotBlank())
            listOf(v.en, v.hi).forEach { text -> assertFalse(text, dose.containsMatchIn(text) || drugs.containsMatchIn(text) || CommunityRules.hasContact(text)) }
        }
        CommunityLocal.posts.forEach { post ->
            assertNull(post.id, CommunityRules.postError(post.body.en)); assertNull(post.id, CommunityRules.postError(post.body.hi))
            assertNull(post.id, CommunityRules.nameError(post.name.en))
            post.replies.forEach { r ->
                assertNull(r.id, CommunityRules.replyError(r.body.en)); assertNull(r.id, CommunityRules.replyError(r.body.hi))
                if (r.byTeam) assertEquals("Sahara team", r.name.en) else assertNull(r.id, CommunityRules.nameError(r.name.en))
            }
        }
        // The team never gives treatment advice; anything medical goes back to the care team, a doctor or a counsellor.
        CommunityLocal.posts.flatMap { it.replies }.filter { it.byTeam }.forEach { r -> assertTrue(r.id, Regex("care team|doctor|nurse|counsellor|Sahara").containsMatchIn(r.body.en)) }
    }

    @Test fun whatThisPhoneAddsShowsAtOnceAndSurvivesARestart() {
        val anchor = 5_000_000_000L
        var state = CommunityLocalState(name = "Asha's daughter")
        state = CommunityLocal.add(state, CommunityPending("mine-1", "acct-a", CommunityPending.POST, "family-carers", "Hello all", anchor + 1000))
        state = CommunityLocal.add(state, CommunityPending("mine-r", "acct-a", CommunityPending.REPLY, "local-fc-2", "Same here", anchor + 2000))
        state = CommunityLocal.add(state, CommunityPending("mine-r2", "acct-a", CommunityPending.REPLY, "mine-1", "Thank you", anchor + 3000))
        state = CommunityLocal.react(state, "local-fc-2", "hugs")
        state = CommunityLocal.report(state, "local-fc-3")
        val base = CommunityLocal.home(CommunityLocalState()).groups.first { it.id == "family-carers" }.posts
        assertEquals(base + 1, CommunityLocal.home(state).groups.first { it.id == "family-carers" }.posts)
        val feed = CommunityLocal.feed("family-carers", state, anchor) { it.hi }
        val first = feed.posts.first()
        assertEquals("mine-1", first.id); assertTrue(first.mine); assertEquals("Asha's daughter", first.name); assertEquals("Thank you", first.replies.single().body)
        val nights = feed.posts.first { it.id == "local-fc-2" }
        assertTrue(nights.myHugs); assertEquals(CommunityLocal.posts.first { it.id == "local-fc-2" }.hugs + 1, nights.hugs)
        assertTrue(nights.replies.last().mine); assertTrue(hindi.containsMatchIn(nights.body))
        assertTrue(feed.posts.first { it.id == "local-fc-3" }.reported)
        // Posting joins the group; saving lists the post under Saved; both survive a restart.
        assertTrue(CommunityLocal.home(state).groups.first { it.id == "family-carers" }.joined)
        state = CommunityLocal.save(CommunityLocal.join(state, "grief-loss", true), "local-gl-1", true)
        assertEquals(listOf("local-gl-1"), CommunityLocal.feed(CommunityRules.SAVED, state, anchor) { it.en }.posts.map { it.id })
        assertEquals(setOf("family-carers", "grief-loss"), CommunityLocal.home(state).groups.filter { it.joined }.map { it.id }.toSet())
        assertEquals("Asha", CommunityRules.suggestName("asha.verma42@example.com")); assertNull(CommunityRules.suggestName("98765@example.com"))
        assertEquals(state, CommunityLocal.decode(CommunityLocal.encode(state)))
        assertEquals(CommunityLocalState(), CommunityLocal.decode(null))
        // A second tap takes the hug back; deleting a post takes its replies with it; built-in posts stay.
        assertFalse(CommunityLocal.feed("family-carers", CommunityLocal.react(state, "local-fc-2", "hugs"), anchor) { it.en }.posts.first { it.id == "local-fc-2" }.myHugs)
        val removed = CommunityLocal.remove(state, "mine-1")
        assertEquals(listOf("mine-r"), removed.items.map { it.id })
        assertEquals(base, CommunityLocal.home(removed).groups.first { it.id == "family-carers" }.posts)
    }
}
