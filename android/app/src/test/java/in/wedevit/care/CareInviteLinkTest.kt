package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareInviteLinkTest {
    private val code = "0123456789abcdef0123456789abcdef0123456789abcdef"
    private class Memory : CareKeyValue {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    }

    @Test fun fullHttpsLink() { assertEquals(CareInvite(code, "Rahul"), CareInviteLink.parse("https://sahara.wedevit.in/join?c=$code&n=Rahul")) }
    @Test fun linkWithoutName() { assertEquals(CareInvite(code, ""), CareInviteLink.parse("https://sahara.wedevit.in/join?c=$code")) }
    @Test fun customScheme() { assertEquals(code, CareInviteLink.parse("sahara://join?c=$code")?.code) }
    @Test fun bareCodeAndUppercase() { assertEquals(code, CareInviteLink.parse("  $code \n")?.code); assertEquals(code, CareInviteLink.parse(code.uppercase())?.code) }
    @Test fun wholeSharedMessageRoundTrips() {
        val text = CareInviteLink.shareText("Rahul Kumar", code)
        assertTrue(text.contains("https://sahara.wedevit.in/join?c=$code&n=Rahul+Kumar"))
        assertTrue(text.contains("code: $code)"))
        assertEquals(CareInvite(code, "Rahul Kumar"), CareInviteLink.parse(text))
    }
    @Test fun brokenLinkFallsBackToBareCodeInMessage() { assertEquals(code, CareInviteLink.parse("https://sahara.wedevit.in/join?c=abc (code: $code)")?.code) }
    @Test fun junkIsRejected() {
        listOf(null, "", "   ", "hello", "https://sahara.wedevit.in/join", "https://sahara.wedevit.in/join?c=abc", "https://sahara.wedevit.in/join?c=${code}0", "sahara://join?c=${code.take(47)}g",
            "https://evil.example/join?c=short", code + "a", "x".repeat(5000)).forEach { assertNull(it, CareInviteLink.parse(it)) }
    }
    @Test fun nameIsCleaned() { assertEquals("Arjun", CareInviteLink.cleanName("<Arjun>&=")); assertEquals(40, CareInviteLink.cleanName("a".repeat(100)).length); assertEquals("", CareInviteLink.parse("sahara://join?c=$code&n=%3C%3E")?.name) }

    @Test fun pendingJoinSurvivesRestartThenExpires() {
        val kv = Memory(); val store = CarePendingJoinStore(kv)
        assertNull(store.load(1_000L))
        store.save(PendingJoin(code, "Rahul", 1_000L))
        assertEquals(PendingJoin(code, "Rahul", 1_000L), CarePendingJoinStore(kv).load(2_000L))
        assertNull(store.load(1_000L + CareInviteLog.TTL_MS + 1))
        assertTrue(kv.map.isEmpty())
    }
    @Test fun pendingJoinClearsAndRejectsCorruptData() {
        val kv = Memory(); val store = CarePendingJoinStore(kv)
        store.save(PendingJoin(code, "", 5L)); store.clear(); assertNull(store.load(6L)); assertTrue(kv.map.isEmpty())
        kv.put("code", "not-a-code"); kv.put("at", "5"); assertNull(store.load(6L)); assertNull(kv.get("code"))
    }
    @Test fun joinFailuresAreReadable() {
        assertTrue(careJoinFailureMessage("Invalid or expired invite").contains("expired or was already used"))
        assertTrue(careJoinFailureMessage("Couldn't reach your care records. Your entry is still here.").contains("connection"))
        assertEquals("Clinician verification is required.", careJoinFailureMessage("Clinician verification is required."))
        assertTrue(careJoinFailureMessage(null).isNotBlank())
    }

    @Test fun inviteLogLabelsAndJoinedEstimate() {
        val t0 = 1_000_000_000L
        val e = CareInviteEntry("p1", "Arjun", "Brother", t0, baseline = 1)
        assertEquals("Arjun · brother · Invited · expires in 3 days", CareInviteLog.label(e, t0 + 1000))
        assertEquals("Arjun · brother · Invited · expires today", CareInviteLog.label(e, t0 + 2 * CareInviteLog.DAY_MS + 1000))
        assertTrue(CareInviteLog.label(e, t0 + CareInviteLog.TTL_MS).startsWith("Arjun · brother · Expired"))
        val same = CareInviteLog.reconcile(listOf(e), "p1", 1, t0 + 10)
        assertFalse(same.single().joined)
        val joined = CareInviteLog.reconcile(listOf(e), "p1", 2, t0 + 10)
        assertEquals("Arjun · brother · Joined", CareInviteLog.label(joined.single(), t0 + 20))
        val back = CareInviteLog.decode(CareInviteLog.encode(joined)); assertEquals(joined, back)
    }
    @Test fun oneNewMemberMarksOnlyOneInviteAndOtherPeopleAreUntouched() {
        val t0 = 5_000L
        val a = CareInviteEntry("p1", "A", "", t0, 0); val b = CareInviteEntry("p1", "B", "Son", t0 + 10, 0); val other = CareInviteEntry("p2", "C", "", t0, 0)
        val out = CareInviteLog.reconcile(listOf(a, b, other), "p1", 1, t0 + 100)
        assertEquals(listOf(true, false), out.filter { it.person == "p1" }.sortedBy { it.createdAt }.map { it.joined })
        assertEquals(other, out.first { it.person == "p2" })
    }
    @Test fun inviteStoreIsPerAccount() {
        val store = CareInviteStore(Memory()); val e = CareInviteEntry("p1", "A", "Son", 1L, 0)
        store.add("acct1", e); assertEquals(listOf(e), store.all("acct1")); assertTrue(store.all("acct2").isEmpty())
    }
}
