package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The medicine changes agreed by the clinical team on 24 Nov 2026 (late rule, three dose answers, forms, frequency, calendar, phrases). */
class MedicineTeamChangesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 20)
    private val today = LocalDate.of(2026, 11, 24)
    private val person = "00000000-0000-4000-8000-000000000001"
    private val hour = 3_600_000L
    private fun at(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    private fun medicine(detail: String, doneOn: String = "") = CareItem("00000000-0000-4000-8000-0000000000bb", person, "medicine", "Synthetic medicine", detail, doneOn = doneOn)
    private var ids = 0
    private fun id() = "id-${++ids}"

    // 1. Late only from one hour after the dose time.
    @Test fun lateStartsOneHourAfterTheDoseTime() {
        assertEquals(60L, MedicineLateRule.LATE_AFTER_MINUTES)
        val planned = 1_000_000_000L
        assertFalse(MedicineLateRule.isLate(planned, planned + 10 * 60_000L))
        assertFalse(MedicineLateRule.isLate(planned, planned + hour - 1))
        assertTrue(MedicineLateRule.isLate(planned, planned + hour))
        assertFalse(MedicineLateRule.isLate(null, planned + 2 * hour)); assertFalse(MedicineLateRule.isLate(planned, null))
        assertFalse(MedicineLateRule.overdue("08:00", LocalTime.of(8, 59)))
        assertTrue(MedicineLateRule.overdue("08:00", LocalTime.of(9, 0)))
        // A 23:30 dose never turns late in the next morning's minutes.
        assertFalse(MedicineLateRule.overdue("23:30", LocalTime.of(0, 40)))
        assertFalse(MedicineLateRule.overdue("not a time", LocalTime.NOON))
    }

    @Test fun historyAndDoctorViewsUseTheSameLateRule() {
        val planned = at(day, 8)
        fun taken(after: Long) = MedicineIntakeEvent("e", "m", "Synthetic medicine", "taken", planned, planned + after, loggedAt = planned + after)
        assertEquals("Taken reported", MedicineHistoryRules.status(taken(30 * 60_000L), planned + 2 * hour))
        assertEquals("Late reported", MedicineHistoryRules.status(taken(hour), planned + 2 * hour))
        val sample = SampleMedicineDose("d", "demo-asha", "Sample", planned, planned + 59 * 60_000L, planned + 2 * hour, SampleDoseOutcome.TAKEN, SampleReportSource.PATIENT)
        assertEquals("Taken reported", sample.status(planned + 3 * hour))
        assertEquals("Late reported", sample.copy(reportedTakenAt = planned + hour).status(planned + 3 * hour))
        assertEquals("Late reported", doctorMedicinePresentation(sample.copy(reportedTakenAt = planned + 90 * 60_000L), planned + 3 * hour, zone).status)
    }

    // 2. Three answers only: Taken / Not taken / Skipped. Stored outcomes stay "taken" and "skipped".
    @Test fun storedReportsReadAsTheThreeAnswers() {
        fun event(outcome: String, note: String = "") = MedicineIntakeEvent("e", "m", "Synthetic medicine", outcome, loggedAt = 1, note = note)
        assertEquals(MedicineDoseChoice.TAKEN, MedicineDoseStatus.choiceOf(event("taken")))
        assertEquals(MedicineDoseChoice.NOT_TAKEN, MedicineDoseStatus.choiceOf(event("skipped", "08:00 dose reported missed from the daily list.")))
        assertEquals(MedicineDoseChoice.SKIPPED, MedicineDoseStatus.choiceOf(event("skipped", "08:00 dose skipped on purpose.")))
        assertNull(MedicineDoseStatus.choiceOf(event("unmarked"))); assertNull(MedicineDoseStatus.choiceOf(event("planned")))
        assertEquals("Not taken reported", MedicineHistoryRules.status(event("skipped"), 2))
        assertEquals("Skipped reported", MedicineHistoryRules.status(event("skipped", "Dose 1 of 2 skipped on purpose."), 2))
    }

    @Test fun olderStatusWordsMapToTheThreeAnswers() {
        listOf("late", "Late reported", "Taken", "Taken reported", "Taken reported · time not recorded").forEach { assertEquals(it, MedicineDoseChoice.TAKEN, MedicineDoseStatus.choiceOf(it)) }
        listOf("missed", "Missed", "Not taken", "Not taken reported", "not_taken").forEach { assertEquals(it, MedicineDoseChoice.NOT_TAKEN, MedicineDoseStatus.choiceOf(it)) }
        listOf("skipped", "Skipped reported").forEach { assertEquals(it, MedicineDoseChoice.SKIPPED, MedicineDoseStatus.choiceOf(it)) }
        listOf("", "Upcoming", "Not logged", "Check-off corrected").forEach { assertNull(it, MedicineDoseStatus.choiceOf(it)) }
        assertEquals(listOf("Taken", "Not taken", "Skipped"), MedicineDoseChoice.entries.map { it.english })
    }

    @Test fun skippingOnPurposeIsSavedAsSkippedAndCanBeChangedToNotTaken() {
        val item = medicine("Times: 08:00, 20:00"); val doses = MedicineDoses.forItem(item)
        val skip = MedicineDoses.plan(item, doses, emptyList(), doses[0], false, day, zone, at(day, 9), "Patient report", ::id, skipped = true)
        val saved = skip.events.single()
        assertEquals("skipped", saved.outcome); assertTrue(saved.note.contains(MedicineDoseStatus.SKIP_MARK)); assertEquals(at(day, 8), saved.plannedAt)
        MedicineHistoryRules.validate(saved, at(day, 10))
        assertEquals(MedicineDoseState.SKIPPED, MedicineDoses.state(item, doses[0], skip.events, day, zone))
        val notTaken = MedicineDoses.plan(item, doses, skip.events, doses[0], false, day, zone, at(day, 10), "Patient report", ::id)
        assertEquals(1, notTaken.events.size)
        assertEquals(MedicineDoseState.MISSED, MedicineDoses.state(item, doses[0], skip.events + notTaken.events, day, zone))
        // A skipped dose does not tick the whole day.
        assertEquals("", skip.doneOn)
    }

    // 5. Forms: no Oral solution (it is a Syrup), Nebuliser next to Inhaler, one Injection. Old saved values still read.
    @Test fun formsAreTheAgreedListAndOldValuesStillRead() {
        val forms = MedicinePhotoFields.forms.map { it.first }
        assertFalse(forms.contains("Oral solution")); assertTrue(forms.contains("Syrup")); assertEquals(1, forms.count { it.contains("Injection") })
        assertEquals(forms.indexOf("Inhaler") + 1, forms.indexOf("Nebuliser"))
        assertEquals("Syrup", MedicinePhotoFields.canonicalForm("Oral solution")); assertEquals("Syrup", MedicinePhotoFields.canonicalForm("oral solution"))
        listOf("Subcutaneous injection", "SC injection", "IV", "IM", "S/C", "Injection (IV)", "intramuscular injection", "injection").forEach { assertEquals(it, "Injection", MedicinePhotoFields.canonicalForm(it)) }
        assertEquals("Nebuliser", MedicinePhotoFields.canonicalForm("Nebulizer")); assertEquals("Nebuliser", MedicinePhotoFields.canonicalForm("nebuliser"))
        assertEquals("Tablet", MedicinePhotoFields.canonicalForm("tablet")); assertEquals("Lozenge", MedicinePhotoFields.canonicalForm("Lozenge")); assertEquals("", MedicinePhotoFields.canonicalForm(" "))
        assertEquals("Nebuliser", MedicinePhotoFields.formIn("Salbutamol nebuliser solution 2.5 mg"))
        assertEquals("Injection", MedicinePhotoFields.formIn("Morphine solution for injection 10 mg/ml"))
        assertEquals("Syrup", MedicinePhotoFields.formIn("Morphine oral solution 5 mg"))
        assertEquals("Drops", MedicinePhotoFields.formIn("Eye drops solution"))
        assertEquals("Morphine", MedicinePhotoFields.nameIn("Morphine oral solution 5 mg"))
    }

    // 6. Frequency is a choice that fills dose times from the usual times.
    @Test fun frequencyChoicesFillTheUsualTimes() {
        assertEquals(listOf("08:00", "13:00", "18:00", "21:00"), MedicineDoseRules.commonTimes)
        assertEquals(listOf("08:00"), MedicineFrequencyRules.timesFor(MedicineFrequency.ONCE, emptyList()))
        assertEquals(listOf("08:00", "21:00"), MedicineFrequencyRules.timesFor(MedicineFrequency.TWICE, emptyList()))
        assertEquals(listOf("08:00", "13:00", "21:00"), MedicineFrequencyRules.timesFor(MedicineFrequency.THREE, listOf("08:00")))
        assertEquals(listOf("08:00", "13:00", "18:00", "21:00"), MedicineFrequencyRules.timesFor(MedicineFrequency.FOUR, listOf("09:00")))
        assertEquals(emptyList<String>(), MedicineFrequencyRules.timesFor(MedicineFrequency.AS_NEEDED, listOf("08:00")))
        // Times already chosen are kept when their number fits the choice.
        assertEquals(listOf("09:00", "20:00"), MedicineFrequencyRules.timesFor(MedicineFrequency.TWICE, listOf("20:00", "09:00")))
        MedicineFrequency.entries.forEach { f ->
            assertEquals(f.english, f.perDay, MedicineDoses.perDay(f.english))
            assertEquals(f.english, f, MedicineFrequencyRules.fromText(f.english))
            assertEquals(f.english, f.perDay ?: 0, MedicineFrequencyRules.defaultTimes(f).size)
        }
        assertTrue(MedicineDoses.isAsNeeded(MedicineFrequency.AS_NEEDED.english))
        assertEquals(MedicineFrequency.TWICE, MedicineFrequencyRules.fromText("BD")); assertEquals(MedicineFrequency.TWICE, MedicineFrequencyRules.fromText("1-0-1"))
        assertEquals(MedicineFrequency.THREE, MedicineFrequencyRules.fromText("TDS after food")); assertEquals(MedicineFrequency.AS_NEEDED, MedicineFrequencyRules.fromText("SOS"))
        assertNull(MedicineFrequencyRules.fromText("once weekly")); assertNull(MedicineFrequencyRules.fromText("")); assertNull(MedicineFrequencyRules.fromText("every 4 hours"))
        // Saved as before, so the doses and reminders read it unchanged.
        val detail = MedicineEntryText.detail(MedicineEntry(strength = "500 mg", form = "Tablet", howOften = MedicineFrequency.TWICE.english, times = MedicineFrequencyRules.defaultTimes(MedicineFrequency.TWICE)))
        assertEquals("Strength: 500 mg\nForm: Tablet\nHow often: Twice a day\nTimes: 08:00, 21:00", detail)
        assertEquals(2, MedicineDoses.forItem(medicine(detail)).size)
    }

    @Test fun olderFreeTextKeepsItsWordsWhenAFrequencyIsChosen() {
        val old = "Oral. For nausea."
        val chosen = MedicineEntryText.withTimes(MedicineLegacyText.withFrequency(old, MedicineFrequency.TWICE), MedicineFrequencyRules.timesFor(MedicineFrequency.TWICE, emptyList()))
        assertEquals("Oral. For nausea.\nFrequency: Twice a day\nTimes: 08:00, 21:00", chosen)
        assertEquals(MedicineFrequency.TWICE, MedicineLegacyText.frequency(chosen))
        assertEquals(old, MedicineLegacyText.words(chosen))
        assertEquals("Oral. For pain.\nFrequency: Twice a day\nTimes: 08:00, 21:00", MedicineLegacyText.withWords(chosen, "Oral. For pain."))
        assertEquals("Oral. For nausea.\nTimes: 08:00, 21:00", MedicineLegacyText.withFrequency(chosen, null))
        assertEquals(MedicineFrequency.TWICE, MedicineLegacyText.frequency("Oral, twice daily. For nausea."))
        assertNull(MedicineEntryText.parse(chosen)) // still free text, edited as text
    }

    @Test fun strengthIsANumberAndAUnit() {
        assertEquals(listOf("mg", "mcg", "ml", "g", "units", "%", "drops", "puffs"), MedicineStrength.units)
        assertEquals(MedicineStrength.Parts("500", "mg"), MedicineStrength.parts("500 mg"))
        assertEquals(MedicineStrength.Parts("500", "mg"), MedicineStrength.parts("500mg"))
        assertEquals(MedicineStrength.Parts("2.5", "ml"), MedicineStrength.parts("2.5 ML"))
        assertEquals(MedicineStrength.Parts("100", "mcg"), MedicineStrength.parts("100 µg"))
        assertEquals(MedicineStrength.Parts("", ""), MedicineStrength.parts(""))
        assertNull(MedicineStrength.parts("5 mg/5 ml")); assertNull(MedicineStrength.parts("two tablets"))
        assertEquals("500 mg", MedicineStrength.text("500", "mg")); assertEquals("", MedicineStrength.text("", "mg")); assertEquals("2 ml", MedicineStrength.text("2.", "ml"))
        assertEquals("1.23", MedicineStrength.cleanNumber("1.2.3a")); assertEquals("12345678".take(7), MedicineStrength.cleanNumber("12345678"))
        assertEquals("units", MedicineStrength.unitOf("IU")); assertNull(MedicineStrength.unitOf("tablet"))
    }

    // 3. Calendar day colours.
    @Test fun calendarDaysAreColouredOnlyFromReports() {
        val item = medicine("Times: 08:00, 20:00"); val doses = MedicineDoses.forItem(item)
        fun report(dose: Int, taken: Boolean, skipped: Boolean = false, d: LocalDate = day) = MedicineDoses.report(item, doses[dose], taken, d, zone, at(d, 21), "Patient report", id = id(), skipped = skipped)
        fun marks(events: List<MedicineIntakeEvent>, sides: List<Long> = emptyList(), d: LocalDate = day) = MedicineHistoryCalendar.marks(item, doses, events, sides, d, zone, today)
        assertTrue(marks(emptyList()).empty)
        assertEquals(MedicineDayMarks(allTaken = true), marks(listOf(report(0, true), report(1, true))))
        assertEquals(MedicineDayMarks(notTaken = true), marks(listOf(report(0, true), report(1, false))))
        assertTrue(marks(listOf(report(0, true))).empty) // the evening dose was never answered: no colour is guessed
        assertEquals(MedicineDayMarks(allTaken = true), marks(listOf(report(0, true), report(1, false, skipped = true))))
        assertFalse(marks(listOf(report(0, false, skipped = true), report(1, false, skipped = true))).allTaken)
        assertEquals(MedicineDayMarks(notTaken = true, sideEffect = true), marks(listOf(report(0, true), report(1, false)), listOf(at(day, 15))))
        assertEquals(MedicineDayMarks(sideEffect = true), marks(emptyList(), listOf(at(day, 15))))
        assertTrue(marks(listOf(report(0, true)), listOf(at(day.plusDays(1), 15))).empty)
        // A later correction wins: Not taken, then Taken.
        val corrected = listOf(report(1, false), report(1, true).copy(loggedAt = at(day, 22)), report(0, true))
        assertEquals(MedicineDayMarks(allTaken = true), marks(corrected))
        // Days after today get no dose colour.
        assertTrue(marks(listOf(report(0, true, d = today.plusDays(1)), report(1, true, d = today.plusDays(1))), d = today.plusDays(1)).empty)
    }

    @Test fun calendarReadsOlderReportsAfterDoseTimesChanged() {
        val item = medicine("Times: 09:00"); val doses = MedicineDoses.forItem(item)
        val oldTime = MedicineIntakeEvent(id(), item.id, item.title, "taken", plannedAt = at(day, 14), loggedAt = at(day, 15), note = "14:00 dose ticked.")
        assertEquals(MedicineDayMarks(allTaken = true), MedicineHistoryCalendar.marks(item, doses, listOf(oldTime), emptyList(), day, zone, today))
        val checkoff = MedicineIntakeEvent(id(), item.id, item.title, "taken", reportedDate = day.toString(), loggedAt = at(day, 22), source = "daily-checkoff")
        assertEquals(MedicineDayMarks(allTaken = true), MedicineHistoryCalendar.marks(item, doses, listOf(checkoff), emptyList(), day, zone, today))
        val missed = MedicineHistoryRules.missedReport(item, "Family", at(day, 12), id())
        assertEquals(MedicineDayMarks(notTaken = true), MedicineHistoryCalendar.marks(item, doses, listOf(missed), emptyList(), day, zone, today))
        // The whole-day tick on the medicine only means today.
        val ticked = item.copy(doneOn = today.toString())
        assertTrue(MedicineHistoryCalendar.marks(ticked, doses, emptyList(), emptyList(), today, zone, today).allTaken)
        assertTrue(MedicineHistoryCalendar.marks(ticked, doses, emptyList(), emptyList(), day, zone, today).empty)
    }

    @Test fun aDaysReportsComeInDoseOrder() {
        val item = medicine("Times: 08:00, 20:00"); val doses = MedicineDoses.forItem(item)
        val evening = MedicineDoses.report(item, doses[1], true, day, zone, at(day, 20, 30), "Patient report", id = "evening")
        val morning = MedicineDoses.report(item, doses[0], true, day, zone, at(day, 21), "Patient report", id = "morning")
        val otherDay = MedicineDoses.report(item, doses[0], true, day.minusDays(1), zone, at(day, 21), "Patient report", id = "yesterday")
        assertEquals(listOf("morning", "evening"), MedicineHistoryCalendar.eventsOn(item.id, listOf(evening, otherDay, morning), day, zone).map { it.id })
    }

    // 7. Medicine phrases in the reader's language; the saved words never change.
    @Test fun knownMedicinePhrasesAreShownInTheReadersLanguage() {
        assertEquals("For pain.", MedicinePhrases.shown("For pain.", CareLang.EN))
        assertEquals("दर्द के लिए।", MedicinePhrases.shown("For pain.", CareLang.HI))
        assertEquals("मुँह से, दिन में दो बार। मतली के लिए।", MedicinePhrases.shown("Oral, twice daily. For nausea.", CareLang.HI))
        assertEquals("Keep in the fridge.", MedicinePhrases.shown("Keep in the fridge.", CareLang.HI))
        listOf(CareLang.TE, CareLang.KN, CareLang.UR).forEach { lang ->
            val shown = MedicinePhrases.shown("To ease nausea and appetite loss.", lang)
            assertFalse("${lang.code}: $shown", shown.contains("nausea"))
            assertFalse("${lang.code}: $shown", MedicinePhrases.shown("Before food", lang).contains("food"))
        }
        assertEquals("Ease nausea" to "मतली में आराम", MedicinePhrases.known(" ease  nausea. "))
    }
}
