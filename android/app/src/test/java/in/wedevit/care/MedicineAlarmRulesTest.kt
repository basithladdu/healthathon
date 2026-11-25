package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineAlarmRulesTest {
    @Test fun morphineAndOtherOpioidsRingByDefaultOthersDoNot() {
        listOf("Morphine 10 mg", "Oramorph", "Tab Morcontin 30", "Fentanyl patch", "Tramadol", "Tapentadol 50 mg", "Oxycodone", "Buprenorphine patch", "Methadone", "मॉर्फिन")
            .forEach { assertTrue(it, MedicineAlarmRules.isOpioid(it)) }
        listOf("Paracetamol", "Ondansetron", "Dexamethasone", "Senna", "Metformin").forEach { assertFalse(it, MedicineAlarmRules.isOpioid(it)) }
        assertTrue(MedicineAlarmRules.isImportant("Morphine 10 mg", ""))
        assertFalse(MedicineAlarmRules.isImportant("Paracetamol", ""))
    }

    @Test fun aSavedChoiceWinsOverTheDefaultAndOnlyADifferenceIsWritten() {
        assertEquals("Times: 21:00", MedicineAlarmRules.withAlarm("Times: 21:00", "Morphine", true))
        assertEquals("Times: 21:00\nAlarm: off", MedicineAlarmRules.withAlarm("Times: 21:00", "Morphine", false))
        assertFalse(MedicineAlarmRules.isImportant("Morphine", "Times: 21:00\nAlarm: off"))
        assertEquals("Times: 08:00\nAlarm: on", MedicineAlarmRules.withAlarm("Times: 08:00", "Paracetamol", true))
        assertTrue(MedicineAlarmRules.isImportant("Paracetamol", "Times: 08:00\nAlarm: on"))
        // Switching back to the default removes the line, and nothing else changes.
        assertEquals("Times: 08:00", MedicineAlarmRules.withAlarm("Times: 08:00\nAlarm: on", "Paracetamol", false))
        assertEquals("Alarm: on", MedicineAlarmRules.withAlarm("", "Senna", true))
        assertNull(MedicineAlarmRules.saved("For pain."))
        assertNull(MedicineAlarmRules.entryValue("Morphine", true)); assertEquals(false, MedicineAlarmRules.entryValue("Morphine", false))
    }

    @Test fun theFormKeepsTheAlarmLineAndItNeverReadsAsDoseText() {
        val entry = MedicineEntry(strength = "10 mg", form = "Syrup", howOften = "Twice a day", times = listOf("08:00", "20:00"), alarm = true)
        val detail = MedicineEntryText.detail(entry)
        assertTrue(detail.endsWith("Alarm: on"))
        assertEquals(entry, MedicineEntryText.parse(detail))
        assertEquals(MedicineEntry(times = listOf("08:00")), MedicineEntryText.parse(MedicineEntryText.detail(MedicineEntry(times = listOf("08:00")))))
        // The line is not "How often" text, not a purpose sentence, and not the person's own words.
        assertFalse(MedicineDoses.frequencyText("For pain.\nAlarm: on").contains("Alarm"))
        assertFalse(MedicineCardText.rest("For pain. Take with food.\nAlarm: on").contains("Alarm"))
        assertEquals("Take at night.", MedicineLegacyText.words("Take at night.\nAlarm: off"))
        assertEquals("New words\nAlarm: off", MedicineLegacyText.withWords("Old\nAlarm: off", "New words"))
    }

    @Test fun theSpokenPromptFollowsTheTimeOfDay() {
        assertEquals("Time for your morning medicine", MedicineAlarmRules.promptEnglish(MedicineDoseRules.slot("08:00")))
        assertEquals("Time for your afternoon medicine", MedicineAlarmRules.promptEnglish(MedicineDoseRules.slot("13:00")))
        assertEquals("Time for your evening medicine", MedicineAlarmRules.promptEnglish(MedicineDoseRules.slot("18:00")))
        assertEquals("Time for your night medicine", MedicineAlarmRules.promptEnglish(MedicineDoseRules.slot("21:00")))
        assertEquals("Time for your night medicine", MedicineAlarmRules.promptEnglish(MedicineDoseRules.slot("02:00")))
        assertEquals("Time for your night medicine, Morphine.", MedicineAlarmRules.spokenEnglish(DoseSlot.NIGHT, listOf("Morphine")))
        assertEquals("Time for your morning medicine, Morphine and Senna.", MedicineAlarmRules.spokenEnglish(DoseSlot.MORNING, listOf("Morphine", " ", "Senna")))
        assertEquals("A, B and C", MedicineAlarmRules.joinNames(listOf("A", "B", "C", "A"), "and"))
        assertTrue(MedicineAlarmRules.promptHindi(DoseSlot.NIGHT).startsWith("रात"))
        // No prompt speaks about illness, stage or prognosis: only the time of day and the medicine.
        DoseSlot.entries.forEach { assertFalse(MedicineAlarmRules.promptEnglish(it).contains(Regex("(?i)stage|end|last|dying"))) }
    }

    @Test fun anAlarmRingsOnlyWithAnImportantMedicineStillDue() {
        val group = DoseGroup("p", "21:00", DoseSlot.NIGHT, listOf(DoseMedicine("a", "p", "Paracetamol", listOf("21:00")), DoseMedicine("m", "p", "Morphine", listOf("21:00"))))
        assertEquals(listOf("m", "a"), MedicineAlarmRules.ringing(group, setOf("m"), emptySet()).map { it.itemId })
        assertTrue(MedicineAlarmRules.ringing(group, setOf("m"), setOf("m")).isEmpty())
        assertTrue(MedicineAlarmRules.ringing(group, emptySet(), emptySet()).isEmpty())
        assertEquals(listOf("m"), MedicineAlarmRules.ringing(group, setOf("m"), setOf("a")).map { it.itemId })
    }

    @Test fun anAlarmTravelsInItsIntentAndSnoozesForTenMinutes() {
        val alarm = MedicineAlarmRules.Alarm("acc", "person", 1_000L, "21:00", listOf("m" to "Morphine", "s" to "Senna"))
        assertEquals(alarm, MedicineAlarmRules.decode(MedicineAlarmRules.encode(alarm)))
        assertEquals(DoseSlot.NIGHT, alarm.slot)
        assertNull(MedicineAlarmRules.decode("{}")); assertNull(MedicineAlarmRules.decode(null))
        assertNull(MedicineAlarmRules.decode(MedicineAlarmRules.encode(alarm.copy(time = "9pm"))))
        assertEquals(10 * 60_000L, MedicineAlarmRules.snoozeAt(0L))
        assertEquals(60 * 60_000L, MedicineAlarmRules.snoozeAt(0L, 500))
    }

    @Test fun anUnansweredAlarmSnoozesItselfOnlyAFewTimes() {
        assertTrue(MedicineAlarmRules.autoSnooze(0))
        assertTrue(MedicineAlarmRules.autoSnooze(MedicineAlarmRules.MAX_AUTO_SNOOZES - 1))
        assertFalse(MedicineAlarmRules.autoSnooze(MedicineAlarmRules.MAX_AUTO_SNOOZES))
    }

    @Test fun aRecreatedAlarmScreenRingsOnlyForWhatIsLeft() {
        assertEquals(MedicineAlarmRules.RING_FOR_MS, MedicineAlarmRules.ringLeft(1_000L, 1_000L))
        assertEquals(MedicineAlarmRules.RING_FOR_MS - 60_000L, MedicineAlarmRules.ringLeft(0L, 60_000L))
        assertEquals(0L, MedicineAlarmRules.ringLeft(0L, MedicineAlarmRules.RING_FOR_MS + 5L))
        // A clock set back never makes the ring longer than one full ring.
        assertEquals(MedicineAlarmRules.RING_FOR_MS, MedicineAlarmRules.ringLeft(10_000L, 0L))
    }

    @Test fun aSnoozeIsSetAgainAfterARestartAtItsOwnTimeOrAMinuteFromNow() {
        assertEquals(500_000L, MedicineAlarmRules.restoreAt(500_000L, 100_000L))
        assertEquals(160_000L, MedicineAlarmRules.restoreAt(120_000L, 100_000L))
        assertEquals(160_000L, MedicineAlarmRules.restoreAt(0L, 100_000L))
    }

    @Test fun theAlarmToneFallsThroughToTheNextThatPlays() {
        val tried = mutableListOf<String>()
        assertEquals("settings", MedicineAlarmRules.firstWorking(listOf(null, "settings", "actual")) { tried += it; true })
        tried.clear()
        assertEquals("ringtone", MedicineAlarmRules.firstWorking(listOf("settings", "actual", "settings", "ringtone")) { tried += it; it == "ringtone" })
        assertEquals(listOf("settings", "actual", "ringtone"), tried)
        assertNull(MedicineAlarmRules.firstWorking(listOf<String?>(null, "a")) { false })
    }

    @Test fun theAlarmLineCanBeLeftOutWhereTheTextIsShownAsItIs() {
        assertEquals("Times: 08:00\nFor pain.", MedicineAlarmRules.withoutLine("Times: 08:00\nAlarm: on\nFor pain."))
        assertEquals("", MedicineAlarmRules.withoutLine("Alarm: off"))
    }

    @Test fun eachPartOfTheDayHasItsOwnPicture() {
        assertEquals(4, DoseSlot.entries.map(MedicineDoseRules::symbol).distinct().size)
    }
}
