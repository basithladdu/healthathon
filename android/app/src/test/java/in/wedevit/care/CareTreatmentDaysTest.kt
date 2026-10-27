package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CareTreatmentDaysTest {
    private val person = "p1"
    private fun day(s: String) = careEpoch(LocalDate.parse(s))
    private fun item(id: String, category: String, on: String, who: String = person, status: String = "open") =
        CareItem(id, who, "task", category, CareEntryCodec.encode(CareEntry(type = "schedule", category = category, status = status)), day(on))

    @Test fun onlyThisPersonsChemoAndRadiotherapyDaysAreMarkers() {
        val items = listOf(item("a", "Chemotherapy", "2026-10-01"), item("b", "Radiotherapy", "2026-10-05"), item("c", "Visit", "2026-10-03"),
            item("d", "Chemotherapy", "2026-10-02", who = "p2"), item("e", "Chemotherapy", "2026-10-04", status = "cancelled"),
            CareItem("f", person, "task", "Chemotherapy", "Chemotherapy day typed as a plain task", day("2026-10-06")))
        assertEquals(listOf("b", "a"), TreatmentDayRules.days(items, person).map { it.item.id })
    }
    @Test fun markersStayInsideTheChartDatesIncludingBothEnds() {
        val days = TreatmentDayRules.days(listOf(item("a", "Chemotherapy", "2026-09-30"), item("b", "Chemotherapy", "2026-10-01"), item("c", "Radiotherapy", "2026-10-10"), item("d", "Radiotherapy", "2026-10-11")), person)
        assertEquals(listOf("b", "c"), TreatmentDayRules.within(days, day("2026-10-01"), day("2026-10-10")).map { it.item.id })
    }
    @Test fun entryIsAnAllDayCalendarItemThatSaves() {
        val decoded = CareEntryCodec.decode(TreatmentDayRules.entry("Radiotherapy", " session 3 "))!!
        assertEquals("schedule", decoded.type); assertEquals("Radiotherapy", decoded.category); assertFalse(decoded.timed); assertEquals("session 3", decoded.detail)
        assertThrows(IllegalArgumentException::class.java) { TreatmentDayRules.entry("Surgery", "") }
    }
}
