package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** The caregiver's own ZBI-4 wellbeing check: scoring, the 8-point line, sharp rises, trend and what is stored. */
class CaregiverWellbeingRulesTest {
    private val d1 = LocalDate.of(2026, 11, 1)
    private fun e(day: Int, score: Int) = WellbeingEntry(d1.plusDays(day.toLong()), score)

    @Test fun scoreNeedsAllFourAnswersInRange() {
        assertEquals(0, CaregiverWellbeingRules.score(listOf(0, 0, 0, 0)))
        assertEquals(16, CaregiverWellbeingRules.score(listOf(4, 4, 4, 4)))
        assertEquals(7, CaregiverWellbeingRules.score(listOf(1, 2, 3, 1)))
        assertNull(CaregiverWellbeingRules.score(listOf(1, 2, 3, null)))
        assertNull(CaregiverWellbeingRules.score(listOf(1, 2, 3)))
        assertNull(CaregiverWellbeingRules.score(listOf(1, 2, 3, 5)))
        assertNull(CaregiverWellbeingRules.score(listOf(1, 2, 3, -1)))
    }

    @Test fun eightOrMoreIsHighBurden() {
        assertFalse(CaregiverWellbeingRules.isHigh(7))
        assertTrue(CaregiverWellbeingRules.isHigh(8))
        assertTrue(CaregiverWellbeingRules.isHigh(16))
    }

    @Test fun flagWhenLatestIsHighOrRisesSharply() {
        assertFalse(CaregiverWellbeingRules.shouldFlag(emptyList()))
        assertFalse(CaregiverWellbeingRules.shouldFlag(listOf(e(0, 3))))
        assertTrue(CaregiverWellbeingRules.shouldFlag(listOf(e(0, 9))))
        assertFalse(CaregiverWellbeingRules.shouldFlag(listOf(e(0, 2), e(7, 5))))
        assertTrue(CaregiverWellbeingRules.shouldFlag(listOf(e(0, 2), e(7, 6))))
        // high before, lower now: no flag once the latest is under 8 and not rising
        assertFalse(CaregiverWellbeingRules.shouldFlag(listOf(e(0, 10), e(7, 6))))
        // the latest by date counts, whatever the list order
        assertTrue(CaregiverWellbeingRules.shouldFlag(listOf(e(7, 12), e(0, 1))))
    }

    @Test fun trendComparesTheLastTwoChecks() {
        assertEquals(CaregiverWellbeingRules.Trend.None, CaregiverWellbeingRules.trend(listOf(e(0, 5))))
        assertEquals(CaregiverWellbeingRules.Trend.Rising, CaregiverWellbeingRules.trend(listOf(e(0, 3), e(1, 5))))
        assertEquals(CaregiverWellbeingRules.Trend.Falling, CaregiverWellbeingRules.trend(listOf(e(0, 9), e(1, 6))))
        assertEquals(CaregiverWellbeingRules.Trend.Steady, CaregiverWellbeingRules.trend(listOf(e(0, 5), e(1, 6))))
        assertEquals(4, CaregiverWellbeingRules.change(listOf(e(1, 6), e(0, 2))))
    }

    @Test fun oneCheckPerDayAndOnlyTheNewestAreKept() {
        val same = CaregiverWellbeingRules.add(listOf(e(0, 3)), e(0, 9))
        assertEquals(listOf(e(0, 9)), same)
        var h = emptyList<WellbeingEntry>()
        for (i in 0 until 20) h = CaregiverWellbeingRules.add(h, e(i, i % 17))
        assertEquals(CaregiverWellbeingRules.KEEP, h.size)
        assertEquals(e(19, 2), h.last())
        assertEquals(CaregiverWellbeingRules.TREND_SHOWN, CaregiverWellbeingRules.recent(h).size)
    }

    @Test fun historySurvivesStorageAndBadDataIsDropped() {
        val h = listOf(e(0, 4), e(3, 11))
        assertEquals(h, CaregiverWellbeingRules.decodeHistory(CaregiverWellbeingRules.encodeHistory(h)))
        assertEquals(listOf(e(0, 4)), CaregiverWellbeingRules.decodeHistory("2026-11-01|4;junk;2026-11-02|99;2026-13-40|3"))
        assertTrue(CaregiverWellbeingRules.decodeHistory(null).isEmpty())
        assertTrue(CaregiverWellbeingRules.decodeHistory("").isEmpty())
    }

    @Test fun journalIsNewestFirstAndKeepsAnyText() {
        var notes = CaregiverWellbeingRules.addNote(emptyList(), 1000L, "  Tired today.\nBut we laughed; she ate well.  ")
        notes = CaregiverWellbeingRules.addNote(notes, 2000L, "नींद नहीं आई")
        assertEquals(listOf(2000L, 1000L), notes.map { it.at })
        assertEquals("Tired today.\nBut we laughed; she ate well.", notes[1].text)
        assertEquals(notes, CaregiverWellbeingRules.decodeJournal(CaregiverWellbeingRules.encodeJournal(notes)))
        assertEquals(notes, CaregiverWellbeingRules.addNote(notes, 3000L, "   "))
    }

    @Test fun storeKeyIsPerAccountAndPerson() {
        assertNotEquals(CaregiverWellbeingRules.storeKey("a", "p1"), CaregiverWellbeingRules.storeKey("a", "p2"))
        assertNotEquals(CaregiverWellbeingRules.storeKey("a", "p1"), CaregiverWellbeingRules.storeKey("b", "p1"))
    }

    @Test fun helpLinksAreTheOnesTheDoctorsGave() {
        assertEquals("https://swayamfoundation.co.in/support-group/caregiver-support/", CaregiverWellbeingRules.SUPPORT_GROUP_SWAYAM)
        assertEquals("https://www.soulup.in/products/caregiving-support-group", CaregiverWellbeingRules.SUPPORT_GROUP_SOULUP)
        assertEquals("tel:14416", NearbyHelp.dialUri(CaregiverWellbeingRules.TELE_MANAS_NUMBER))
    }
}
