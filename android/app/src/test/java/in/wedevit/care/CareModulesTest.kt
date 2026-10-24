package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareModulesTest {
    private fun item(id: String, kind: String, due: Long = 0) = CareItem(id = id, personId = "p", kind = kind, title = id, due = due)
    @Test fun medicinesNotShownByTheModelAreHistoryAndOtherKindsAreIgnored() {
        val a = item("a", "medicine"); val b = item("b", "medicine"); val t = item("t", "task")
        val (current, past) = CareModules.splitMedicines(listOf(a, b, t), listOf(a, t))
        assertEquals(listOf("a"), current.map { it.id }); assertEquals(listOf("b"), past.map { it.id })
    }
    @Test fun visitsSplitAtNowWithSoonestUpcomingAndNewestPastFirst() {
        val (up, past) = CareModules.splitVisits(listOf(item("p1", "visit", 100), item("u2", "visit", 3_000), item("u1", "visit", 1_000), item("p2", "visit", 900), item("none", "visit", 0)), 1_000L)
        assertEquals(listOf("u1", "u2", "none"), up.map { it.id }); assertEquals(listOf("p2", "p1"), past.map { it.id })
    }
    @Test fun initialFilterMapsToModuleCaseInsensitively() { assertEquals("To do", CareModules.moduleFor("to do")); assertNull(CareModules.moduleFor("All")) }
}
