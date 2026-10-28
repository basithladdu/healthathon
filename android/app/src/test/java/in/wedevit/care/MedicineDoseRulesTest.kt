package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Team decision 8 (28 Oct 2026): photo-filled fields, dose-time groups and the strip-photo reminder. */
class MedicineDoseRulesTest {
    private fun organized(name: String = "Paracetamol") = OrganizedMedicine(name, "500 mg", "Tablet", "1 tablet", "twice daily", listOf("08:00", "20:00"),
        MedicineMealTiming.AFTER_FOOD, "Finish the course", 5, 1, 0, listOf(0), 0.9, emptyList(), MedicineMatchQuality.MATCHED)

    @Test fun aPhotoMayFillOnlyNameStrengthAndForm() {
        assertEquals(setOf(MedicinePhotoFields.Field.NAME, MedicinePhotoFields.Field.STRENGTH, MedicinePhotoFields.Field.FORM), MedicinePhotoFields.fromPhoto)
        listOf(MedicinePhotoFields.Field.HOW_MUCH, MedicinePhotoFields.Field.HOW_OFTEN, MedicinePhotoFields.Field.TIMES, MedicinePhotoFields.Field.FOOD,
            MedicinePhotoFields.Field.DIRECTIONS, MedicinePhotoFields.Field.DURATION).forEach { assertFalse(it.name, MedicinePhotoFields.mayFill(it)) }
        val limited = MedicinePhotoFields.limit(organized())
        assertEquals("Paracetamol", limited.medicineName); assertEquals("500 mg", limited.strength); assertEquals("Tablet", limited.form)
        assertEquals("", limited.quantity); assertEquals("", limited.frequency); assertEquals("", limited.instructions)
        assertTrue(limited.suggestedTimes.isEmpty()); assertEquals(MedicineMealTiming.NOT_STATED, limited.mealTiming); assertNull(limited.durationDays)
    }

    @Test fun prescriptionLinesNeverFillFrequencyAmountDirectionsOrCourse() {
        val found = copiedPrescriptionMedicines(listOf(DocumentPageText(1, "1. Tab Paracetamol 500 mg 1-0-1 after food x 5 days\nSyp Lactulose 15 ml at night"))).associateBy { it.medicineName }
        val tablet = found.getValue("Paracetamol")
        assertEquals("500 mg", tablet.strength); assertEquals("Tablet", tablet.form)
        assertEquals("", tablet.quantity); assertEquals("", tablet.frequency); assertEquals("", tablet.instructions)
        assertTrue(tablet.suggestedTimes.isEmpty()); assertNull(tablet.durationDays); assertEquals(MedicineMealTiming.NOT_STATED, tablet.mealTiming)
        assertEquals("1. Tab Paracetamol 500 mg 1-0-1 after food x 5 days", tablet.sourceQuote) // kept only as a read-only quote
        assertEquals("Syrup", found.getValue("Lactulose").form); assertEquals("15 ml", found.getValue("Lactulose").strength)
        // The saved text has no frequency, food or course line taken from the photo.
        val detail = medicineCareDetail(tablet)
        assertFalse(detail.contains("Frequency:")); assertFalse(detail.contains("Food:")); assertFalse(detail.contains("Course:")); assertFalse(detail.contains("Times:"))
    }

    @Test fun stripPhotoTextFillsOnlyBlanksAndACutStripLeavesTheNameToThePerson() {
        val reading = MedicinePhotoFields.fromStripText("PARACETAMOL TABLETS IP\n650mg\nMfg. Lic. No. KTK/25/123\nBatch B123 Exp 12/2027")
        assertEquals(MedicinePhotoFields.Reading("PARACETAMOL", "650 mg", "Tablet"), reading)
        val cut = MedicinePhotoFields.fromStripText("Batch No. B123\nExp. 12/2027\nM.R.P. Rs. 30.50")
        assertEquals("", cut.name)
        val typed = organized("Dolo 650").copy(strength = "", form = "")
        val filled = MedicinePhotoFields.fillBlanks(typed, reading)
        assertEquals("Dolo 650", filled.medicineName); assertEquals("650 mg", filled.strength); assertEquals("Tablet", filled.form)
        assertEquals(typed.frequency, filled.frequency); assertEquals(typed.quantity, filled.quantity); assertEquals(typed.instructions, filled.instructions)
    }

    @Test fun handEnteredMedicineTextRoundTripsAndFreeTextStaysText() {
        val entry = MedicineEntry("500 mg", "Tablet", "1½ tablets", "twice a day", listOf("20:00", "08:00"), MedicineMealTiming.AFTER_FOOD, 5, "as written")
        val detail = MedicineEntryText.detail(entry)
        assertEquals("Strength: 500 mg\nForm: Tablet\nHow much each time: 1½ tablets\nHow often: twice a day\nTimes: 08:00, 20:00\nFood: after food\nCourse: 5 days\nDirections: as written", detail)
        assertEquals(entry.copy(times = listOf("08:00", "20:00")), MedicineEntryText.parse(detail))
        assertEquals(listOf("08:00", "20:00"), CareReminderRules.printedTimes(detail))
        assertEquals(MedicineEntry(), MedicineEntryText.parse(""))
        assertNull(MedicineEntryText.parse("Oral, twice daily. For nausea."))
        assertEquals("Oral, twice daily.\nTimes: 09:00", MedicineEntryText.withTimes("Oral, twice daily.\nTimes: 08:00, 20:00", listOf("09:00")))
        assertEquals("Oral, twice daily.", MedicineEntryText.withTimes("Oral, twice daily.\nTimes: 08:00", emptyList()))
    }

    @Test fun dosesGroupByTimeAndPerson() {
        assertEquals(DoseSlot.MORNING, MedicineDoseRules.slot("08:00")); assertEquals(DoseSlot.AFTERNOON, MedicineDoseRules.slot("13:00"))
        assertEquals(DoseSlot.EVENING, MedicineDoseRules.slot("20:00")); assertEquals(DoseSlot.NIGHT, MedicineDoseRules.slot("21:00")); assertEquals(DoseSlot.NIGHT, MedicineDoseRules.slot("02:00"))
        val groups = MedicineDoseRules.groups(listOf(
            DoseMedicine("a", "p1", "Ondansetron 4 mg", listOf("08:00", "20:00")), DoseMedicine("b", "p1", "dexamethasone 4 mg", listOf("08:00")),
            DoseMedicine("c", "p1", "Paracetamol 1 g", listOf("08:00", "08:00")), DoseMedicine("d", "p2", "Furosemide 40 mg", listOf("08:00")), DoseMedicine("e", "p1", "Morphine", emptyList())))
        assertEquals(listOf("08:00" to "p1", "08:00" to "p2", "20:00" to "p1"), groups.map { it.time to it.personId })
        assertEquals(listOf("dexamethasone 4 mg", "Ondansetron 4 mg", "Paracetamol 1 g"), groups[0].medicines.map { it.name })
        assertEquals("Morning · 08:00 · 3 medicines", MedicineDoseRules.title(groups[0], false))
        assertEquals("Evening · 20:00 · 1 medicine", MedicineDoseRules.title(groups[2], false))
        assertEquals("सुबह · 08:00 · 3 दवाएँ", MedicineDoseRules.title(groups[0], true))
    }

    @Test fun reminderTimesComeFromThisPhoneOrTheSharedRecord() {
        val item = CareItem("m1", "p1", "medicine", "Ondansetron 4 mg", "Oral, twice daily.\nTimes: 08:00, 20:00")
        assertEquals(listOf("08:00", "20:00"), MedicineDoseRules.times(item, null)) // a family phone reads the synced record
        assertEquals(listOf("09:00"), MedicineDoseRules.times(item, ReminderOption(listOf("09:00"))))
        assertTrue(MedicineDoseRules.times(item, ReminderOption(listOf("09:00"), muted = true)).isEmpty())
        assertTrue(MedicineDoseRules.times(item.copy(kind = "task"), null).isEmpty())
    }

    @Test fun oneReminderListsEveryMedicineDueAtThatMomentAndHidesNamesOnTheLockScreen() {
        val at = 1_000L
        val medicines = listOf(DoseMedicine("a", "p1", "Ondansetron 4 mg", emptyList()), DoseMedicine("b", "p1", "Dexamethasone 4 mg", emptyList()), DoseMedicine("c", "p1", "Paracetamol 1 g", emptyList()))
        val due = MedicineDoseRules.dueTogether(listOf(DoseOccurrence("a", at, "08:00"), DoseOccurrence("b", at, "08:00"), DoseOccurrence("c", at, "08:00"),
            DoseOccurrence("a", 2_000L, "20:00"), DoseOccurrence("visit", at, "08:00")), medicines, at)
        assertEquals(1, due.size)
        val reminder = MedicineDoseRules.reminder(due.single(), false)
        assertEquals("Morning · 08:00 · 3 medicines", reminder.title)
        assertEquals("Dexamethasone 4 mg, Ondansetron 4 mg, Paracetamol 1 g\nMatch each strip with its photo.", reminder.text)
        assertEquals(listOf("b", "a", "c"), reminder.itemIds)
        listOf("Ondansetron", "Paracetamol", "Dexamethasone").forEach { assertFalse(reminder.publicTitle.contains(it) || reminder.publicText.contains(it)) }
        assertTrue(MedicineDoseRules.reminder(due.single(), true).title.startsWith("सुबह"))
    }

    @Test fun newMedicineScreensHaveHindi() {
        val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
        val source = File(dir, "MedicineStripPhotos.kt").readText()
        val missing = listOf("StripThumb", "StripPhotoRow", "DoseTimesField", "MedicineEntryForm", "TodayDosesCard").flatMap { name -> HindiScan.missing(HindiScan.body(source, name), emptySet()).map { "$name: $it" } }
        assertTrue(missing.joinToString("\n"), missing.isEmpty())
    }
}
