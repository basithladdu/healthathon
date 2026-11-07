package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareDistressTest {
    private val dayStart = 1_000_000L

    @Test fun scoreAndProblemListRoundTrip() {
        val check = DistressCheck(true, 7, listOf("Finances", "Pain", "Worry or anxiety"), "Worried about the next hospital trip")
        val saved = DistressRules.change(null, check)!!
        assertEquals("checkin", saved.kind)
        assertEquals("Distress 7/10", saved.title)
        val back = DistressCodec.decode(saved.detail)!!
        assertEquals(7, back.score)
        // Saved in the NCCN list's own order, whatever order they were ticked in.
        assertEquals(listOf("Pain", "Worry or anxiety", "Finances"), back.problems)
        assertEquals("Worried about the next hospital trip", back.other)
        assertTrue(saved.detail.contains(DistressRules.VERSION))
    }

    @Test fun patientGetsTheFamilyChecksCareTeamMessageAtTheSameScore() {
        assertTrue(DistressRules.tellCareTeam(DistressCheck(true, CareFamilyRules.TALK_FROM)))
        assertTrue(DistressRules.tellCareTeam(DistressCheck(true, 10)))
        assertTrue(!DistressRules.tellCareTeam(DistressCheck(true, CareFamilyRules.TALK_FROM - 1)))
        assertTrue(!DistressRules.tellCareTeam(DistressCheck(false)))
        assertTrue(!DistressRules.tellCareTeam(null))
    }

    @Test fun noIsOneAnswerWithNothingElse() {
        val saved = DistressRules.change(null, DistressCheck(false))!!
        assertEquals(DistressRules.NO_TITLE, saved.title)
        assertEquals(DistressCheck(false), DistressCodec.decode(saved.detail))
        assertNotNull(DistressRules.error(DistressCheck(false, 3)))
        assertNotNull(DistressRules.error(DistressCheck(false, problems = listOf("Pain"))))
    }

    @Test fun onlyTheScoreIsRequired() {
        assertNull(DistressRules.error(DistressCheck(true, 0)))
        assertNull(DistressRules.error(DistressCheck(true, 10)))
        assertNotNull(DistressRules.error(DistressCheck(true, null)))
        assertNotNull(DistressRules.error(DistressCheck(true, 11)))
        assertNotNull(DistressRules.error(DistressCheck(true, -1)))
        assertNotNull(DistressRules.error(DistressCheck(true, 4, listOf("Not on the list"))))
        assertNotNull(DistressRules.error(DistressCheck(true, 4, other = "x".repeat(DistressRules.OTHER_LIMIT + 1))))
        assertNull(DistressCodec.decode(DistressCodec.PREFIX + "{\"distress\":true,\"score\":12,\"problems\":[],\"other\":\"\"}"))
    }

    @Test fun sameAnswerTwiceSavesNothingAndAChangeUpdatesTodaysEntry() {
        val first = DistressRules.change(null, DistressCheck(false))!!.copy(personId = "p", created = dayStart + 10)
        assertNull(DistressRules.change(first, DistressCheck(false)))
        val updated = DistressRules.change(first, DistressCheck(true, 5, listOf("Sleep")))!!
        assertEquals(first.id, updated.id)
        assertEquals("Distress 5/10", updated.title)
    }

    @Test fun historyIsThisPersonsAnswersNewestFirst() {
        fun entry(person: String, at: Long, check: DistressCheck) = DistressRules.change(null, check)!!.copy(personId = person, created = at)
        val items = listOf(
            entry("p", 100, DistressCheck(true, 6, listOf("Pain"))),
            entry("p", 300, DistressCheck(false)),
            entry("other", 400, DistressCheck(true, 9)),
            CareItem(personId = "p", kind = "checkin", title = CheckInMood.GOOD, created = 350),
            entry("p", 200, DistressCheck(true, 3)),
        )
        val history = DistressRules.history(items, "p")
        assertEquals(listOf<Int?>(null, 3, 6), history.map { it.second.score })
        assertEquals(listOf(300L, 200L, 100L), history.map { it.first.created })
    }

    @Test fun moodCheckInAndDistressStaySeparate() {
        val mood = CareItem(personId = "p", kind = "checkin", title = CheckInMood.OKAY, created = dayStart + 5)
        val distress = DistressRules.change(null, DistressCheck(true, 4))!!.copy(personId = "p", created = dayStart + 9)
        val items = listOf(distress, mood)
        assertEquals(mood, CheckInMood.today(items, "p", dayStart))
        assertEquals(mood, CareTodayRules.todayCheckIn(items, dayStart))
        assertEquals(distress, DistressRules.today(items, "p", dayStart))
        assertEquals(listOf(mood), doctorCheckIns(items, "p", dayStart + 100))
        assertNull(DistressRules.today(items, "p", dayStart + 10))
    }

    @Test fun uploadRedactsOnlyTheTypedConcern() {
        val item = DistressRules.change(null, DistressCheck(true, 8, listOf("Finances"), "Call me on 9876543210"))!!.copy(personId = "p")
        val sent = CarePrivacy.entryForUpload(item)
        val check = DistressCodec.decode(sent.detail)!!
        assertEquals(8, check.score); assertEquals(listOf("Finances"), check.problems)
        assertTrue(!check.other.contains("9876543210"))
        val plain = DistressRules.change(null, DistressCheck(true, 2))!!
        assertTrue(CarePrivacy.entryForUpload(plain) === plain)
    }

    @Test fun problemListMatchesNccnVersion2026AndEveryItemHasHindi() {
        assertEquals(listOf("Physical Concerns", "Emotional Concerns", "Social Concerns", "Practical Concerns", "Spiritual or Religious Concerns"), DistressRules.categories.map { it.en })
        assertEquals(listOf(9, 9, 7, 13, 6), DistressRules.categories.map { it.items.size })
        assertEquals(DistressRules.problems.size, DistressRules.problems.toSet().size)
        DistressRules.categories.forEach { c -> assertTrue(c.en, HindiScan.hasHindi(c.hi)); c.items.forEach { (en, hi) -> assertTrue(en, HindiScan.hasHindi(hi)) } }
        assertTrue(HindiScan.hasHindi(DistressRules.shownTitle(DistressCheck(true, 4), true)))
        assertTrue(HindiScan.hasHindi(DistressRules.shownTitle(DistressCheck(false), true)))
    }
}
