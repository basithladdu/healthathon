package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class GocTopicGuideTest {
    private val titles = GocChecklist.ROWS.map { it.title.en }
    private val sections = GocChecklist.ROWS.map { it.section }

    @Test fun thereAreThirteenTopicsAndFiveEssentials() {
        assertEquals(13, titles.size)
        assertEquals(5, titles.count { GocTopicGuide.isEssential(it) })
    }

    @Test fun nextSkipsDiscussedAndWraps() {
        assertEquals(1, GocTopicGuide.next(setOf(0), 0, 13))
        assertEquals(3, GocTopicGuide.next(setOf(0, 1, 2), 0, 13))
        assertEquals(0, GocTopicGuide.next(setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), 12, 13))
        assertNull(GocTopicGuide.next((0..12).toSet(), 5, 13))
    }

    @Test fun essentialsCount() {
        assertEquals(0, GocTopicGuide.essentialsDone(emptySet(), titles))
        assertEquals(0, GocTopicGuide.essentialsDone(setOf(0, 1, 2), titles))
        assertEquals(5, GocTopicGuide.essentialsDone((0..12).toSet(), titles))
        assertEquals(2, GocTopicGuide.essentialsDone(setOf(4, 8, 11), titles))
    }

    @Test fun sectionThreeBeforeUnderstandingWarns() {
        val cpr = titles.indexOf("CPR preference"); val understanding = titles.indexOf(GocTopicGuide.UNDERSTANDING_TITLE)
        assertTrue(GocTopicGuide.outOfOrder(cpr, emptySet(), sections, titles))
        assertFalse(GocTopicGuide.outOfOrder(cpr, setOf(understanding), sections, titles))
        assertFalse(GocTopicGuide.outOfOrder(understanding, emptySet(), sections, titles))
        assertFalse(GocTopicGuide.outOfOrder(0, emptySet(), sections, titles))
    }

    @Test fun tappingMovesTheNowDiscussingTopic() {
        var s = GocTopicGuide.tap(GocGuideState(), 2, 10)
        assertEquals(2, s.now); assertTrue(s.discussed.isEmpty())
        s = GocTopicGuide.tap(s, 5, 70)
        assertEquals(5, s.now); assertEquals(setOf(2), s.discussed); assertEquals(10, s.stamps[2])
        s = GocTopicGuide.tap(s, 5, 90)
        assertEquals(-1, s.now); assertEquals(setOf(2, 5), s.discussed)
    }

    @Test fun tappingTheCurrentOneFinishesItAndDoneKeepsTheStartTime() {
        val started = GocTopicGuide.tap(GocGuideState(), 4, 30)
        val finished = GocTopicGuide.done(started, 4, 95)
        assertEquals(-1, finished.now); assertEquals(setOf(4), finished.discussed); assertEquals(30, finished.stamps[4])
        val direct = GocTopicGuide.done(GocGuideState(), 1, 12)
        assertEquals(12, direct.stamps[1]); assertEquals(setOf(1), direct.discussed)
    }

    @Test fun timeFormat() {
        assertEquals("00:00", GocTopicGuide.mmss(0))
        assertEquals("01:05", GocTopicGuide.mmss(65))
        assertEquals("12:00", GocTopicGuide.mmss(720))
        assertEquals("--:--", GocTopicGuide.mmss(-1))
    }

    @Test fun stampsSurviveEncoding() {
        val m = mapOf(3 to 20, 0 to -1, 12 to 301)
        assertEquals(m, GocTopicGuide.decode(GocTopicGuide.encode(m)))
        assertTrue(GocTopicGuide.decode("").isEmpty())
    }
}
