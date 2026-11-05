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

    @Test fun inviteStatusComesFromTheServerNotACount() {
        val t0 = 1_000_000_000L; val id = "11111111-1111-1111-1111-111111111111"
        val e = CareInviteEntry("p1", "Arjun", "Brother", t0, id)
        val open = listOf(CareOpenInvite(id, "family", false, t0 + CareInviteLog.TTL_MS))
        assertEquals(CareInviteState.OPEN, CareInviteLog.status(e, open, emptyList(), t0 + 10).state)
        assertEquals("Arjun · brother · Invited · expires in 3 days", CareInviteLog.label(e, CareInviteLog.status(e, open, emptyList(), t0 + 1000), t0 + 1000))
        assertEquals("Arjun · brother · Invited · expires today", CareInviteLog.label(e, CareInviteLog.status(e, open, null, t0 + 2 * CareInviteLog.DAY_MS + 1000).copy(state = CareInviteState.OPEN), t0 + 2 * CareInviteLog.DAY_MS + 1000))
        // More members than before is no longer read as "joined": only a membership created by this invitation is.
        val member = CareMemberDetail("u1", id, t0 + 50, 0)
        val joined = CareInviteLog.status(e, emptyList(), listOf(member, CareMemberDetail("u2", "", t0, 0)), t0 + 100)
        assertEquals(CareInviteStatus(CareInviteState.JOINED, "u1", t0 + 50), joined)
        assertTrue(CareInviteLog.label(e, joined, t0 + 100, memberEmail = "a@example.com").startsWith("Arjun · brother · Joined · a@example.com · "))
        assertEquals(CareInviteState.JOINED, CareInviteLog.status(e, emptyList(), listOf(member), t0 + 10 * CareInviteLog.DAY_MS).state)
        assertEquals(CareInviteState.CLOSED, CareInviteLog.status(e, emptyList(), listOf(CareMemberDetail("u2", "", t0, 0)), t0 + 100).state)
        assertEquals(CareInviteState.CLOSED, CareInviteLog.status(e, emptyList(), null, t0 + 100).state)
        assertEquals(CareInviteState.UNKNOWN, CareInviteLog.status(e, null, null, t0 + 100).state)
        assertEquals(CareInviteState.EXPIRED, CareInviteLog.status(e, open, null, t0 + CareInviteLog.TTL_MS).state)
        assertEquals(CareInviteState.UNTRACKED, CareInviteLog.status(e.copy(inviteId = ""), open, listOf(member.copy(inviteId = "")), t0 + 100).state)
    }
    @Test fun labelsInHindiAndForUnnamedDoctorInvites() {
        val e = CareInviteEntry("p1", "", "", 0L, "x", role = "clinician")
        assertEquals("Doctor invitation · Expired", CareInviteLog.label(e, CareInviteStatus(CareInviteState.EXPIRED), 0L))
        val hindi = CareInviteLog.label(CareInviteEntry("p1", "Arjun", "Brother", 0L, "x"), CareInviteStatus(CareInviteState.CLOSED), 0L, hindi = true)
        assertTrue(hindi, hindi.startsWith("Arjun · भाई · ") && HindiScan.hasHindi(hindi))
    }
    @Test fun newInviteIdOnlyWhenCertain() {
        assertEquals("b", CareInviteLog.newInviteId(listOf("a"), listOf("a", "b")))
        assertNull(CareInviteLog.newInviteId(listOf("a"), listOf("a", "b", "c")))
        assertNull(CareInviteLog.newInviteId(null, listOf("b")))
        assertNull(CareInviteLog.newInviteId(listOf("a"), null))
    }
    @Test fun encodeDecodeAndOldGuessedJoinsAreDropped() {
        val e = CareInviteEntry("p1", "A", "Son", 5L, "id-1", 99L, "clinician")
        assertEquals(listOf(e), CareInviteLog.decode(CareInviteLog.encode(listOf(e))))
        val old = listOf("p1", "B", "Son", "7", "1", "1").joinToString("\u001F")
        assertEquals(listOf(CareInviteEntry("p1", "B", "Son", 7L)), CareInviteLog.decode(old))
    }
    @Test fun pruneKeepsRecentInvitesPerPerson() {
        val now = 100 * CareInviteLog.DAY_MS
        val list = (0 until 25).map { CareInviteEntry("p1", "n$it", "", now - it * 1000L) } + CareInviteEntry("p1", "old", "", now - 31 * CareInviteLog.DAY_MS) + CareInviteEntry("p2", "other", "", now)
        val out = CareInviteLog.prune(list, now)
        assertEquals(20, out.count { it.person == "p1" }); assertTrue(out.none { it.name == "old" }); assertTrue(out.any { it.person == "p2" })
    }
    @Test fun joinFailureInHindi() {
        assertTrue(HindiScan.hasHindi(careJoinFailureMessage("Invitation expired or already used.", hindi = true)))
        assertTrue(careJoinFailureMessage("Use a separate account to accept an invitation.").contains("their own account"))
    }
    @Test fun inviteStoreIsPerAccount() {
        val store = CareInviteStore(Memory()); val e = CareInviteEntry("p1", "A", "Son", 1L)
        store.add("acct1", e); assertEquals(listOf(e), store.all("acct1")); assertTrue(store.all("acct2").isEmpty())
    }
}
