package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFitRulesTest {
    @Test fun ownersExampleIsFlagged() {
        val found = TextFitRules.splits("PATIENT", listOf(6))
        assertEquals(1, found.size)
        assertEquals(TextFitRules.Kind.MID_WORD, found[0].kind)
        assertEquals("PATIEN|T", found[0].excerpt)
    }

    @Test fun breaksBetweenWordsAreFine() {
        // Line ends include the trailing space: "Log a " | "symptom".
        assertTrue(TextFitRules.splits("Log a symptom", listOf(6)).isEmpty())
        assertTrue(TextFitRules.splits("Wong-Baker faces", listOf(5)).isEmpty())
        assertTrue(TextFitRules.splits("Pain (today)", listOf(5)).isEmpty())
        assertTrue(TextFitRules.splits("Line one\nLine two", listOf(9)).isEmpty())
    }

    @Test fun hindiWordSplitIsFlagged() {
        val text = "दवाइयाँ लें"
        // Inside दवाइयाँ (before the vowel sign ा): never allowed.
        assertEquals(1, TextFitRules.splits(text, listOf(2)).size)
        // Inside दवाइयाँ between two full letters: still a broken word.
        assertEquals(1, TextFitRules.splits(text, listOf(1)).size)
        // After the space: fine.
        assertTrue(TextFitRules.splits(text, listOf(text.indexOf(' ') + 1)).isEmpty())
    }

    @Test fun outOfRangeOffsetsAreIgnored() {
        assertTrue(TextFitRules.splits("Today", listOf(0, 5, 9)).isEmpty())
        assertTrue(TextFitRules.splits("", listOf(1)).isEmpty())
    }
}
