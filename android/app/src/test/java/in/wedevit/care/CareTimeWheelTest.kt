package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test

class CareTimeWheelTest {
    @Test fun twelveHourWheelsRoundTripEveryHour() {
        assertEquals(12, careHour12(0)); assertEquals(12, careHour12(12)); assertEquals(1, careHour12(13)); assertEquals(11, careHour12(23))
        assertEquals(0, careHour24(12, pm = false)); assertEquals(12, careHour24(12, pm = true)); assertEquals(8, careHour24(8, pm = false)); assertEquals(21, careHour24(9, pm = true))
        (0..23).forEach { hour -> assertEquals(hour, careHour24(careHour12(hour), hour >= 12)) }
        assertEquals("21:05", careClockValue(careHour24(9, true), 5))
    }

    @Test fun percentIsAStrengthUnit() {
        assertEquals(MedicineStrength.Parts("1", "%"), MedicineStrength.parts("1%"))
        assertEquals(MedicineStrength.Parts("0.5", "%"), MedicineStrength.parts("0.5 %"))
        assertEquals("1%", MedicineStrength.text("1", "%"))
        assertEquals("%", MedicineStrength.unitOf("percent"))
    }
}
