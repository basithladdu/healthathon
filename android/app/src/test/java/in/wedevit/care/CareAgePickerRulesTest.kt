package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CareAgePickerRulesTest {
    @Test fun stepMovesOneYearAndStaysInRange() {
        assertEquals("61", CareAgePickerRules.step("60", 1))
        assertEquals("59", CareAgePickerRules.step("60", -1))
        assertEquals("1", CareAgePickerRules.step("1", -1))
        assertEquals("120", CareAgePickerRules.step("120", 1))
    }

    @Test fun blankStaysBlank() {
        assertEquals("", CareAgePickerRules.step("", 1))
    }

    @Test fun everyPickedAgePassesTheProfileCheck() {
        CareAgePickerRules.STARTS.forEach { assertNull(CarePatientProfileRules.age(it.toString()).second) }
        assertNull(CarePatientProfileRules.age(CareAgePickerRules.MAX.toString()).second)
    }
}
