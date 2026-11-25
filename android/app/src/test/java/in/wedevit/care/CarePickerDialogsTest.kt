package `in`.wedevit.care

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarePickerDialogsTest {
    @Test fun weightWheelRunsOneToThreeHundredInHalfKilos() {
        val w = CarePickerScales.WEIGHT_KG
        assertEquals(599, w.count)
        assertEquals(1.0, w.valueAt(0), 0.0)
        assertEquals(300.0, w.valueAt(w.count - 1), 0.0)
        assertEquals(62.5, w.valueAt(w.indexOf(62.5)), 0.0)
        assertEquals(123, w.indexOf(62.5))
        // A child's 14 kg is on the wheel.
        assertEquals(14.0, w.valueAt(w.indexOf(14.0)), 0.0)
        // Off-step values snap to the nearest half kilo; values outside open on the nearest end.
        assertEquals(62.5, w.snap(62.4), 0.0)
        assertEquals(1.0, w.snap(0.2), 0.0)
        assertEquals(300.0, w.snap(320.0), 0.0)
        // Every row reads back to itself without floating drift.
        (0 until w.count).forEach { assertEquals(it, w.indexOf(w.valueAt(it))) }
    }

    @Test fun wheelTextHasNoTrailingZeroAndLatinDigits() {
        val w = CarePickerScales.WEIGHT_KG
        assertEquals("62", w.text(62.0)); assertEquals("62.5", w.text(62.5)); assertEquals("1", w.text(w.valueAt(0)))
        assertEquals("160", CarePickerScales.HEIGHT_CM.text(160.0))
        assertEquals("0.5", CarePickerScales.OXYGEN_LPM.text(0.5))
    }

    @Test fun heightAndOxygenScales() {
        val h = CarePickerScales.HEIGHT_CM
        assertEquals(221, h.count); assertEquals(30.0, h.valueAt(0), 0.0); assertEquals(250.0, h.valueAt(220), 0.0); assertEquals(130, h.indexOf(160.0))
        val o = CarePickerScales.OXYGEN_LPM
        assertEquals(30, o.count); assertEquals(15.0, o.valueAt(29), 0.0); assertEquals(2.0, o.valueAt(o.indexOf(2.0)), 0.0)
        // A plan's whole-number wheel (SpO2 floor 70-100).
        val spo2 = CareNumberScale(70.0, 100.0, 1.0)
        assertEquals(31, spo2.count); assertEquals("92", spo2.text(spo2.valueAt(spo2.indexOf(92.0))))
    }

    @Test fun wheelScalesCoverWhatTheProfileRulesAccept() {
        val w = CarePickerScales.WEIGHT_KG; val h = CarePickerScales.HEIGHT_CM
        assertEquals(CarePatientProfileRules.weight("1").first!!, w.min, 0.0); assertEquals(CarePatientProfileRules.weight("300").first!!, w.max, 0.0)
        assertEquals(ProfileError.WEIGHT, CarePatientProfileRules.weight(w.text(w.max + w.step)).second)
        assertEquals(30, CarePatientProfileRules.height("30").first); assertEquals(30.0, h.min, 0.0); assertEquals(250.0, h.max, 0.0)
        assertEquals(ProfileError.HEIGHT, CarePatientProfileRules.height(h.text(h.max + 1)).second)
    }

    @Test fun anUnturnedWheelKeepsTheSavedValue() {
        val o = CarePickerScales.OXYGEN_LPM; val w = CarePickerScales.WEIGHT_KG
        // Between steps or off the ends: OK without turning keeps it exactly.
        assertEquals(0.25, o.picked(0.25, o.indexOf(0.25)), 0.0)
        assertEquals(20.0, o.picked(20.0, o.indexOf(20.0)), 0.0)
        assertEquals(62.3, w.picked(62.3, w.indexOf(62.3)), 0.0)
        // Once turned, the row's own value is saved.
        assertEquals(1.0, o.picked(0.25, o.indexOf(0.25) + 1), 0.0)
        assertEquals(63.0, w.picked(62.3, w.indexOf(62.3) + 1), 0.0)
        // On-step values are the same either way.
        assertEquals(62.5, w.picked(62.5, w.indexOf(62.5)), 0.0)
    }

    @Test fun startValuesSitOnTheirScales() {
        assertEquals(CarePickerScales.WEIGHT_START, CarePickerScales.WEIGHT_KG.snap(CarePickerScales.WEIGHT_START), 0.0)
        assertEquals(CarePickerScales.HEIGHT_START, CarePickerScales.HEIGHT_CM.snap(CarePickerScales.HEIGHT_START), 0.0)
    }

    @Test fun calendarDayRoundTripsThroughUtcMillis() {
        listOf(LocalDate.of(1940, 1, 1), LocalDate.of(2026, 11, 25), LocalDate.of(2026, 3, 29), LocalDate.of(2056, 12, 31)).forEach { day ->
            assertEquals(day, careUtcDay(careUtcMillis(day)))
        }
        assertEquals(0L, careUtcMillis(LocalDate.of(1970, 1, 1)))
    }

    @Test fun clockKeepsASavedMinuteThatIsNotOnAFive() {
        assertEquals(LocalTime.of(10, 7), careWheelTime(LocalTime.of(10, 7, 42)))
        assertEquals(LocalTime.of(23, 59), careWheelTime(LocalTime.of(23, 59)))
        assertEquals(LocalTime.of(0, 0), careWheelTime(LocalTime.MIDNIGHT))
        // The minute wheel: twelve fives, plus the saved minute in its place when it is off a five.
        assertEquals((0..55 step 5).toList(), careMinuteRows(30))
        assertEquals(12, careMinuteRows(0).size)
        val rows = careMinuteRows(7)
        assertEquals(13, rows.size); assertEquals(listOf(0, 5, 7, 10), rows.take(4))
        assertEquals(59, careMinuteRows(59).last())
    }

    @Test fun previousAdmissionKeepsPlaceAndTappedDayInOneLine() {
        val day = LocalDate.of(2025, 3, 3)
        assertEquals("Apollo · 3 Mar 2025", PlanAdmission.join("Apollo", day))
        assertEquals("Apollo" to day, PlanAdmission.split("Apollo · 3 Mar 2025"))
        assertEquals("" to day, PlanAdmission.split(PlanAdmission.join("", day)))
        assertEquals("Apollo", PlanAdmission.join("Apollo", null))
        // An older free-typed line stays as the place, untouched.
        val (where, none) = PlanAdmission.split("Apollo in March")
        assertEquals("Apollo in March", where); assertNull(none)
        // A place being typed keeps its spaces.
        assertEquals("Apollo Hos · 3 Mar 2025", PlanAdmission.join("Apollo Hos", day))
        assertEquals("Apollo " to null, PlanAdmission.split(PlanAdmission.join("Apollo ", null)))
    }
}
