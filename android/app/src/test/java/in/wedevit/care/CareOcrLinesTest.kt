package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareOcrLinesTest {
    @Test fun keepsHindiAndEnglishOnTheSamePageWithoutRepeatingOverlappingReadings() {
        val result = CareOcrLines.merge(listOf(
            CareOcrLine("Follow-up 25 October", 0, 0, 300, 30, false),
            CareOcrLine("Follow-up 25 October", 0, 0, 300, 30, true),
            CareOcrLine("अगली मुलाकात 25 अक्टूबर", 0, 50, 300, 80, true),
            CareOcrLine("25", 200, 50, 220, 80, false),
            CareOcrLine("Bring the original report", 0, 100, 300, 130, false)))
        assertEquals("Follow-up 25 October\nअगली मुलाकात 25 अक्टूबर\nBring the original report", result)
    }
    @Test fun preservesRepeatedWordsAtDifferentPhysicalPositions() {
        val line = CareOcrLine("दवा 10 mg", 0, 0, 100, 20, true)
        assertEquals("दवा 10 mg\nदवा 10 mg", CareOcrLines.merge(listOf(line, line.copy(top = 40, bottom = 60))))
    }
    @Test fun aNarrowHindiReadingCannotHideAWiderLineWithEnglishNamesAndDoses() {
        val result = CareOcrLines.merge(listOf(CareOcrLine("दवा", 0, 0, 50, 20, true), CareOcrLine("Example 10 mg दवा", 0, 0, 300, 20, false)))
        assertTrue(result.contains("Example 10 mg")); assertTrue(result.contains("दवा"))
    }
}
