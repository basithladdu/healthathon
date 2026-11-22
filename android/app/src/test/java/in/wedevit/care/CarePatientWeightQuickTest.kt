package `in`.wedevit.care

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CarePatientWeightQuickTest {
    @Test fun addWeightLogsTodayAndKeepsEverythingElse() {
        val day1 = LocalDate.of(2026, 11, 1); val today = LocalDate.of(2026, 11, 22)
        val before = CarePatientProfile(age = 60, weightKg = 62.0, illness = "Cancer", phone = "999", weights = listOf(CareWeightEntry(day1, 62.0)))
        val after = CareWeightHistory.addWeight(before, 61.5, today)
        assertEquals(61.5, after.weightKg!!, 0.0)
        assertEquals(listOf(CareWeightEntry(day1, 62.0), CareWeightEntry(today, 61.5)), after.weights)
        assertEquals(before.copy(weightKg = 61.5, weights = after.weights), after)
        // The same weight again today is still logged once (a second weight on a day replaces the first).
        assertEquals(2, CareWeightHistory.addWeight(after, 61.5, today).weights.size)
        assertEquals(60.0, CareWeightHistory.addWeight(after, 60.0, today).weights.last().kg, 0.0)
    }
}
