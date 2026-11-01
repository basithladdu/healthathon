package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test

class SymptomSortTest {
    private data class E(val id: String, val score: Int?, val stamp: String)
    private fun sort(vararg e: E) = SymptomSort.severeFirst(e.toList(), { it.score }, { it.stamp }).map { it.id }

    @Test fun highestScoreFirst() = assertEquals(listOf("b", "a"), sort(E("a", 3, "2026-10-27T15:13"), E("b", 7, "2026-10-01T09:00")))
    @Test fun tiesNewestFirst() = assertEquals(listOf("b", "a"), sort(E("a", 5, "2026-10-01T09:00"), E("b", 5, "2026-10-27T15:13")))
    @Test fun unscoredLast() = assertEquals(listOf("b", "a"), sort(E("a", null, "2026-10-27T15:13"), E("b", 0, "2026-10-01T09:00")))
}
