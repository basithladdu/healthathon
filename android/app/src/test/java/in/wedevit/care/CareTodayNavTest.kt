package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 4 Nov: Back returns to the page it came from; Today's greeting, "0 of 3", Up next labels, toasts and the family view. */
class CareTodayNavTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun backReturnsToThePreviousPageAndReopensTheOverlayItCameFrom() {
        var trail = CareNavTrail.push("", 0)            // Today -> Calendar
        trail = CareNavTrail.push(trail, 6)             // Calendar -> Labs
        val (labsBack, rest) = CareNavTrail.pop(trail)!!
        assertEquals(6, labsBack.tab); assertEquals("", labsBack.reopen)
        val (calendarBack, empty) = CareNavTrail.pop(rest)!!
        assertEquals(0, calendarBack.tab); assertEquals("", empty)
        assertNull(CareNavTrail.pop(empty))
        // Check-in -> "Note a symptom" -> Back reopens the check-in on Today.
        val (fromSymptoms, _) = CareNavTrail.pop(CareNavTrail.push("", 0, "checkin"))!!
        assertEquals(0, fromSymptoms.tab); assertEquals("checkin", fromSymptoms.reopen)
        // SOS sheet -> Emergency plan -> Back reopens the SOS sheet.
        assertEquals("sos", CareNavTrail.pop(CareNavTrail.push("2", 0, "sos"))!!.first.reopen)
    }

    @Test fun trailStaysShortAndSkipsBrokenEntries() {
        var trail = ""
        repeat(15) { trail = CareNavTrail.push(trail, it % 3) }
        assertEquals(CareNavTrail.MAX, CareNavTrail.entries(trail).size)
        assertEquals(2, CareNavTrail.pop("2,junk")!!.first.tab)
    }

    @Test fun theBottomBarHighlightsTheTabASubPageWasOpenedFrom() {
        assertEquals(2, CareNavTrail.rootTab(CareNavTrail.push(CareNavTrail.push("", 2), 6), 8))
        assertEquals(5, CareNavTrail.rootTab("", 5))
        assertEquals(11, CareNavTrail.rootTab("", 11))
    }

    @Test fun overlaysStack() {
        val trail = CareNavTrail.pushOverlay("", "checkin")
        assertEquals("checkin" to "", CareNavTrail.popOverlay(trail))
        assertEquals("" to "", CareNavTrail.popOverlay(""))
        assertEquals("", CareNavTrail.pushOverlay("", ""))
    }

    @Test fun greetingFollowsTheHourAndNamesOnlyThePatient() {
        assertEquals("Good morning", CareTodayRules.greeting(7, false))
        assertEquals("Good afternoon", CareTodayRules.greeting(13, false))
        assertEquals("Good evening", CareTodayRules.greeting(20, false))
        assertEquals("Good morning, Rahul", CareTodayRules.greetingLine(7, false, "Rahul Sharma", patient = true))
        assertEquals("Good morning", CareTodayRules.greetingLine(7, false, "Rahul Sharma", patient = false))
    }

    @Test fun medicinesSayZeroOfThreeWithNoNotYetCount() {
        assertEquals("0 of 3", CareTodayRules.medicineCount(0, 3, false))
        assertEquals("3 of 3", CareTodayRules.medicineCount(5, 3, false))
        assertFalse(CareTodayRules.medicineCount(1, 3, false).contains("not yet"))
        assertEquals("0 of 3 today", CarePatientSummaryRules.medicineStatus(3, 0, false))
    }

    @Test fun upNextSaysWhenInWords() {
        val today = LocalDate.of(2026, 11, 4)
        fun at(day: LocalDate) = day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Today", CareTodayRules.whenLabel(at(today), today, false, zone))
        assertEquals("Tomorrow", CareTodayRules.whenLabel(at(today.plusDays(1)), today, false, zone))
        assertEquals("In 5 days", CareTodayRules.whenLabel(at(today.plusDays(5)), today, false, zone))
        assertEquals("Date to be set", CareTodayRules.whenLabel(0L, today, false, zone))
    }

    @Test fun toastsAreColouredByWhatHappened() {
        assertEquals(CareToastKind.SAVED, CareToastRules.kind(SaharaMotion.SAVED_MARK + "Medicine saved"))
        assertEquals(CareToastKind.PROBLEM, CareToastRules.kind("Couldn't save the PDF. Try again."))
        assertEquals(CareToastKind.PROBLEM, CareToastRules.kind("Daily check-off saved, but detailed history could not be recorded."))
        assertEquals(CareToastKind.INFO, CareToastRules.kind("Share screen opened"))
        assertEquals("Medicine saved", CareToastRules.text(SaharaMotion.SAVED_MARK + "Medicine saved"))
    }

    @Test fun familyIsTheInvitedRoleOnlyAndTheirCheckIsForTodayOnly() {
        assertTrue(CareFamilyRules.isFamily("Family", clinician = false))
        assertFalse(CareFamilyRules.isFamily("Patient", clinician = false))
        assertFalse(CareFamilyRules.isFamily("Family", clinician = true))
        val today = LocalDate.of(2026, 11, 4)
        assertEquals(6, CareFamilyRules.decode(CareFamilyRules.encode(today, 6), today))
        assertNull(CareFamilyRules.decode(CareFamilyRules.encode(today.minusDays(1), 6), today))
        assertNull(CareFamilyRules.decode("garbage", today))
        assertEquals(10, CareFamilyRules.decode(CareFamilyRules.encode(today, 14), today))
        assertEquals("No distress", CareFamilyRules.band(0, false)); assertEquals("Moderate", CareFamilyRules.band(CareFamilyRules.TALK_FROM, false))
        assertTrue(CareFamilyRules.tipIndex(today, 3) in CareFamilyRules.tips.indices)
        CareFamilyRules.tips.forEach { (en, hi) -> assertTrue(en, en.isNotBlank() && hi.any { it in 'ऀ'..'ॿ' }) }
    }
}
