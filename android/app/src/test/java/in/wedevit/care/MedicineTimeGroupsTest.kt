package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineTimeGroupsTest {
    private fun med(id: String, name: String, vararg times: String, asNeeded: Boolean = false) = MedicineSlotInput(id, name, times.toList(), asNeeded)

    @Test fun slotsByTimeOfDay() {
        assertEquals(MedicineSlot.MORNING, MedicineTimeGroups.slotOfTime("05:00"))
        assertEquals(MedicineSlot.AFTERNOON, MedicineTimeGroups.slotOfTime("13:30"))
        assertEquals(MedicineSlot.EVENING, MedicineTimeGroups.slotOfTime("20:59"))
        assertEquals(MedicineSlot.NIGHT, MedicineTimeGroups.slotOfTime("21:00"))
        assertEquals(MedicineSlot.NIGHT, MedicineTimeGroups.slotOfTime("02:00"))
    }

    @Test fun severalTimesListsTheMedicineOnceUnderItsEarliestTime() {
        val groups = MedicineTimeGroups.group(listOf(med("a", "Paracetamol", "21:00", "08:00", "14:00"), med("b", "Pantoprazole", "08:00")))
        assertEquals(listOf(MedicineSlot.MORNING), groups.map { it.first })
        assertEquals(listOf("b", "a"), groups.single().second.map { it.id })
        assertEquals(2, groups.sumOf { it.second.size })
    }

    @Test fun untimedGoToAsNeededOrOther() {
        val groups = MedicineTimeGroups.group(listOf(med("a", "Morphine", asNeeded = true), med("b", "Denosumab"), med("c", "Senna", "22:00")))
        assertEquals(listOf(MedicineSlot.NIGHT, MedicineSlot.AS_NEEDED, MedicineSlot.OTHER), groups.map { it.first })
    }

    @Test fun searchIgnoresCase() {
        val all = listOf(med("a", "Paracetamol"), med("b", "Pantoprazole"))
        assertEquals(listOf("b"), MedicineTimeGroups.filterByName(all, " panto ").map { it.id })
        assertEquals(2, MedicineTimeGroups.filterByName(all, "").size)
    }
}
