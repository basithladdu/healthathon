package `in`.wedevit.care

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** The diary card's friendly date/time, in English and Hindi (HANDOFF section 7: Hindi format was untested). JVM locale data; a phone can render AM/PM words slightly differently. */
class SymptomFriendlyWhenTest {
    private val today = LocalDate.of(2026, 10, 27)

    @Test fun englishTodayYesterdayAndOlderDates() {
        assertEquals("Today, 4:01 PM", friendlyWhen("2026-10-27", "16:01", false, today))
        assertTrue(friendlyWhen("2026-10-26", "09:05", false, today).startsWith("Yesterday, 9:05"))
        assertTrue(friendlyWhen("2026-10-20", "09:05", false, today).startsWith("20 Oct, 9:05"))
        assertTrue(friendlyWhen("2025-12-31", "23:59", false, today).startsWith("31 Dec 2025, 11:59"))
    }

    @Test fun hindiUsesHindiDayWordsAndNoEnglishMonth() {
        assertTrue(friendlyWhen("2026-10-27", "16:01", true, today).startsWith("आज, 4:01"))
        assertTrue(friendlyWhen("2026-10-26", "16:01", true, today).startsWith("कल, 4:01"))
        val older = friendlyWhen("2026-10-20", "16:01", true, today)
        assertTrue(older, older.startsWith("20 "))
        assertFalse(older, older.contains("Oct"))
    }

    @Test fun badStoredTextFallsBackUnchanged() {
        assertEquals("not-a-date 16:01", friendlyWhen("not-a-date", "16:01", false, today))
        assertEquals("2026-10-27 25:99", friendlyWhen("2026-10-27", "25:99", true, today))
    }
}
