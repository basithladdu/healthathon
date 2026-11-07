package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MedicineDosesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 4)
    private val person = "00000000-0000-4000-8000-000000000001"
    private fun at(hour: Int, minute: Int = 0) = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun medicine(detail: String, doneOn: String = "") = CareItem("00000000-0000-4000-8000-0000000000aa", person, "medicine", "Synthetic medicine", detail, doneOn = doneOn)
    private var ids = 0
    private fun id() = "id-${++ids}"
    private fun apply(item: CareItem, events: List<MedicineIntakeEvent>, plan: MedicineDosePlan) = item.copy(doneOn = plan.doneOn) to (events + plan.events)

    @Test fun countsComeOnlyFromExplicitFrequencies() {
        assertEquals(4, MedicineDoses.perDay("four times a day"))
        assertEquals(4, MedicineDoses.perDay("4 times daily"))
        assertEquals(3, MedicineDoses.perDay("TDS after food"))
        assertEquals(2, MedicineDoses.perDay("Oral, twice daily. For nausea."))
        assertEquals(2, MedicineDoses.perDay("1-0-1"))
        assertEquals(3, MedicineDoses.perDay("1-1-1 after food"))
        assertEquals(1, MedicineDoses.perDay("Oral, each morning."))
        assertEquals(1, MedicineDoses.perDay("OD"))
        assertNull(MedicineDoses.perDay("Oral, every 4 hours as needed. For breakthrough pain."))
        assertNull(MedicineDoses.perDay("SOS"))
        assertNull(MedicineDoses.perDay("twice a week"))
        assertNull(MedicineDoses.perDay("Take 30 minutes before meals"))
        assertNull(MedicineDoses.perDay("Started on 2026-11-04"))
        assertNull(MedicineDoses.perDay("food"))
        assertNull(MedicineDoses.perDay(""))
    }

    @Test fun savedTimesWinAndEachTimeIsOneDose() {
        val doses = MedicineDoses.forItem(medicine("Oral, four times daily.\nTimes: 20:00, 08:00"))
        assertEquals(listOf("t:08:00", "t:20:00"), doses.map { it.key })
        assertTrue(doses.all { it.timed && it.count == 2 })
        val counted = MedicineDoses.forItem(medicine(MedicineEntryText.detail(MedicineEntry(howOften = "4 times a day"))))
        assertEquals(listOf("n:1", "n:2", "n:3", "n:4"), counted.map { it.key })
        assertEquals(1, MedicineDoses.forItem(medicine("Paracetamol as needed")).size)
        assertEquals(listOf("t:13:00"), MedicineDoses.forItem(medicine("Oral"), reminderTimes = listOf("13:00")).map { it.key })
        assertEquals(emptyList<MedicineDose>(), MedicineDoses.forItem(medicine("x").copy(kind = "task")))
    }

    @Test fun oldSavedMedicinesStillLoadAndAnOldTickCoversEveryDose() {
        // A medicine saved before this change: free text, ticked once today with the old checkbox.
        val item = medicine("Oral, four times a day. For pain.", doneOn = day.toString())
        val doses = MedicineDoses.forItem(item)
        assertEquals(4, doses.size)
        assertTrue(MedicineDoses.allTaken(MedicineDoses.states(item, doses, emptyList(), day, zone)))
        // Yesterday's tick does not count today.
        val yesterday = item.copy(doneOn = day.minusDays(1).toString())
        assertEquals(0, MedicineDoses.takenCount(MedicineDoses.states(yesterday, doses, emptyList(), day, zone)))
    }

    @Test fun fourTimesADayNeedsFourTicksBeforeTheDayIsDone() {
        var item = medicine("How often: four times a day")
        var events = emptyList<MedicineIntakeEvent>()
        val doses = MedicineDoses.forItem(item)
        doses.forEachIndexed { i, dose ->
            val plan = MedicineDoses.plan(item, doses, events, dose, true, day, zone, at(8 + i * 4), "Family report", ::id)
            assertEquals(1, plan.events.size)
            assertNull(plan.events.single().plannedAt); assertNull(plan.events.single().reportedTakenAt)
            assertTrue(plan.events.single().note.startsWith("Dose ${i + 1} of 4"))
            plan.events.forEach { MedicineHistoryRules.validate(it, at(23)) }
            apply(item, events, plan).let { item = it.first; events = it.second }
            assertEquals(i + 1, MedicineDoses.takenCount(MedicineDoses.states(item, doses, events, day, zone)))
            assertEquals(if (i == 3) day.toString() else "", item.doneOn)
        }
        assertEquals(4, events.count { it.outcome == "taken" })
    }

    @Test fun timedDosesCarryTheirPlannedTimeAndShowMissed() {
        var item = medicine("Times: 08:00, 14:00, 20:00")
        var events = emptyList<MedicineIntakeEvent>()
        val doses = MedicineDoses.forItem(item)
        MedicineDoses.plan(item, doses, events, doses[0], true, day, zone, at(9), "Patient report", ::id).let { plan ->
            assertEquals(at(8), plan.events.single().plannedAt)
            assertEquals("taken", plan.events.single().outcome)
            apply(item, events, plan).let { item = it.first; events = it.second }
        }
        MedicineDoses.plan(item, doses, events, doses[1], false, day, zone, at(15), "Patient report", ::id).let { plan ->
            assertEquals("skipped", plan.events.single().outcome); assertEquals(at(14), plan.events.single().plannedAt)
            apply(item, events, plan).let { item = it.first; events = it.second }
        }
        val states = MedicineDoses.states(item, doses, events, day, zone)
        assertEquals(listOf(MedicineDoseState.TAKEN, MedicineDoseState.MISSED, MedicineDoseState.PENDING), doses.map { states[it.key] })
        // The same reports say nothing about tomorrow.
        assertEquals(0, MedicineDoses.takenCount(MedicineDoses.states(item, doses, events, day.plusDays(1), zone)))
        // A later correction wins: the missed dose is reported taken after all.
        MedicineDoses.plan(item, doses, events, doses[1], true, day, zone, at(16), "Patient report", ::id).let { apply(item, events, it).let { r -> item = r.first; events = r.second } }
        assertEquals(MedicineDoseState.TAKEN, MedicineDoses.state(item, doses[1], events, day, zone))
    }

    @Test fun missingOneDoseAfterAWholeDayTickKeepsTheOthersTaken() {
        val item = medicine("Times: 08:00, 20:00", doneOn = day.toString())
        val doses = MedicineDoses.forItem(item)
        val plan = MedicineDoses.plan(item, doses, emptyList(), doses[1], false, day, zone, at(21), "Family report", ::id)
        assertEquals("", plan.doneOn)
        assertEquals(listOf("taken" to at(8), "skipped" to at(20)), plan.events.map { it.outcome to it.plannedAt })
        val (after, events) = apply(item, emptyList(), plan)
        val states = MedicineDoses.states(after, doses, events, day, zone)
        assertEquals(MedicineDoseState.TAKEN, states["t:08:00"]); assertEquals(MedicineDoseState.MISSED, states["t:20:00"])
    }

    @Test fun aSingleDoseStillReadsTheOldDailyReports() {
        val item = medicine("Oral, as needed")
        val dose = MedicineDoses.forItem(item).single()
        val missed = MedicineHistoryRules.missedReport(item, "Family", at(10), "missed")
        assertEquals(MedicineDoseState.MISSED, MedicineDoses.state(item, dose, listOf(missed), day, zone))
        // Old reports never decide a dose of a medicine that now has several.
        val twice = medicine("twice a day")
        assertEquals(MedicineDoseState.PENDING, MedicineDoses.state(twice, MedicineDoses.forItem(twice).first(), listOf(missed.copy(medicineId = twice.id)), day, zone))
    }

    @Test fun theDailyCheckoffNoiseIsHiddenOnlyOnDoseDays() {
        val item = medicine("Times: 08:00")
        val checkoff = MedicineIntakeEvent("c", item.id, item.title, "taken", reportedDate = day.toString(), loggedAt = at(9), source = "daily-checkoff")
        val dose = MedicineDoses.report(item, MedicineDoses.forItem(item).single(), true, day, zone, at(9), "Family report", id = "d")
        assertTrue(MedicineDoses.redundantCheckoff(checkoff, listOf(checkoff, dose), zone))
        assertFalse(MedicineDoses.redundantCheckoff(checkoff, listOf(checkoff), zone))
        assertFalse(MedicineDoses.redundantCheckoff(dose, listOf(checkoff, dose), zone))
    }

    @Test fun cardKeepsWhatTheMedicineDoes() {
        assertEquals("For breakthrough abdominal pain.", MedicineCardText.purpose("Oral, every 4 hours as needed. For breakthrough abdominal pain. Review if more than 3 doses in 24 hours."))
        assertEquals("Short course to ease nausea and appetite loss, now finished.", MedicineCardText.purpose("Oral, each morning. Short course to ease nausea and appetite loss, now finished. Taken with food."))
        assertEquals("Oral, twice daily. Take 30 minutes before meals.", MedicineCardText.rest("Oral, twice daily. For nausea. Take 30 minutes before meals.\nTimes: 08:00, 20:00"))
        assertEquals("", MedicineCardText.purpose("Oral, at night."))
        assertEquals("dissolve in water", MedicineCardText.purpose(MedicineEntryText.detail(MedicineEntry(strength = "5 mg", directions = "dissolve in water"))))
    }
    @Test fun aScanFileHashNeverCountsAsADose() {
        // A scanned medicine with no clear frequency and a page reference whose id and hash contain "3bd9" (read as "BD" before).
        val scanned = medicine("Dose: 5 mg · tablet\nDirections: as advised\nOriginal prescription references:\nPage 1: 1b2c3bd9-0000-4000-8000-000000000001 · SHA-256 03bd9aa1")
        assertEquals(listOf("n:1"), MedicineDoses.forItem(scanned).map { it.key })
        assertEquals(2, MedicineDoses.forItem(medicine("Dose: 5 mg\nFrequency: BD\nOriginal prescription references:\nPage 1: 1b2c3bd9 · SHA-256 03bd9aa1")).size)
    }

    @Test fun weeklyMonthlyAndEveryFewDaysMedicinesGetNoDailyTick() {
        assertTrue(MedicineDoses.notDaily("Methotrexate 15 mg once weekly"))
        assertTrue(MedicineDoses.notDaily("twice a week"))
        assertTrue(MedicineDoses.notDaily("every 4 weeks"))
        assertTrue(MedicineDoses.notDaily("Change the patch every 3 days"))
        assertTrue(MedicineDoses.notDaily("on alternate days"))
        // A course length or a daily count stays daily.
        assertFalse(MedicineDoses.notDaily("1 tablet twice a day for 1 week"))
        assertFalse(MedicineDoses.notDaily("BD for a week"))
        assertFalse(MedicineDoses.notDaily("1-0-1, weekly review"))
        assertFalse(MedicineDoses.notDaily("every 4 hours as needed"))
        assertEquals(emptyList<MedicineDose>(), MedicineDoses.forItem(medicine(MedicineEntryText.detail(MedicineEntry(howOften = "once weekly", times = listOf("08:00"))))))
        assertEquals(emptyList<MedicineDose>(), MedicineDoses.forItem(medicine("Dose: 120 mg\nFrequency: every 4 weeks\nDirections: injection")))
        // A daily form medicine whose directions mention a weekly check keeps its ticks (only "How often" is read).
        assertEquals(2, MedicineDoses.forItem(medicine(MedicineEntryText.detail(MedicineEntry(howOften = "twice a day", directions = "weekly blood test")))).size)
    }

    @Test fun aNightDoseMarkedAfterMidnightAsksWhichNight() {
        val item = medicine("Times: 08:00, 22:00")
        val (morning, night) = MedicineDoses.forItem(item)
        val afterMidnight = day.atTime(0, 20).atZone(zone)
        assertTrue(MedicineDoses.askWhichNight(item, night, emptyList(), afterMidnight))
        assertFalse(MedicineDoses.askWhichNight(item, morning, emptyList(), afterMidnight))
        assertFalse(MedicineDoses.askWhichNight(item, night, emptyList(), day.atTime(21, 30).atZone(zone)))
        assertFalse(MedicineDoses.askWhichNight(item, night, emptyList(), day.atTime(9, 0).atZone(zone)))
        // Last night's dose already ticked: nothing to ask.
        val lastNight = MedicineDoses.plan(item, MedicineDoses.forItem(item), emptyList(), night, true, day.minusDays(1), zone, at(0, 10), "Family report", ::id)
        assertFalse(MedicineDoses.askWhichNight(item, night, lastNight.events, afterMidnight))
        // Saved for last night, the tick does not cover tonight's dose.
        assertEquals(day.minusDays(1).atTime(22, 0).atZone(zone).toInstant().toEpochMilli(), lastNight.events.single().plannedAt)
        assertEquals(MedicineDoseState.PENDING, MedicineDoses.state(item, night, lastNight.events, day, zone))
        assertEquals(MedicineDoseState.TAKEN, MedicineDoses.state(item, night, lastNight.events, day.minusDays(1), zone))
    }
}
