package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class CareSimpleModeRulesTest {
    private val word = SimpleModeRules.PainWord.entries

    @Test fun verbalScaleSavesTheAgreedNumbers() {
        assertEquals(listOf(0, 3, 6, 9), word.map { SimpleModeRules.rating(it) })
        assertEquals(listOf("None", "A little", "A lot", "Worst"), word.map { it.english })
    }

    @Test fun numbersReadBackAsTheNearestWord() {
        assertEquals(SimpleModeRules.PainWord.NONE, SimpleModeRules.fromRating(0))
        (1..4).forEach { assertEquals(SimpleModeRules.PainWord.A_LITTLE, SimpleModeRules.fromRating(it)) }
        (5..7).forEach { assertEquals(SimpleModeRules.PainWord.A_LOT, SimpleModeRules.fromRating(it)) }
        (8..10).forEach { assertEquals(SimpleModeRules.PainWord.WORST, SimpleModeRules.fromRating(it)) }
        word.forEach { assertEquals(it, SimpleModeRules.fromRating(it.rating)) }
        assertEquals(SimpleModeRules.PainWord.WORST, SimpleModeRules.fromRating(14))
    }

    @Test fun painNoteIsAValidDiaryPainNoteWithEveryPartAtTheWordsNumber() {
        val now = LocalDateTime.of(2026, 11, 23, 9, 30)
        val note = SimpleModeRules.painNote(listOf("left-leg", "head", "not-a-part"), SimpleModeRules.PainWord.A_LOT, now, "Asha", familyHelped = false)
        assertNull(SymptomDiaryRules.error(note, now))
        assertEquals("Pain", note.symptom); assertEquals(6, note.rating); assertEquals("patient", note.respondent)
        assertEquals(listOf("head", "left-leg"), note.painBodySites)
        assertEquals(mapOf("head" to 6, "left-leg" to 6), note.painBodyRatings)
        assertTrue(note.note.contains("a lot"))
        val helped = SimpleModeRules.painNote(listOf("chest"), SimpleModeRules.PainWord.NONE, now, "", familyHelped = true)
        assertNull(SymptomDiaryRules.error(helped, now)); assertEquals("assisted-patient", helped.respondent); assertEquals("Patient", helped.author)
        val saved = SymptomNoteCodec.decode(SymptomNoteCodec.encode(note))
        assertEquals(note.painBodyRatings, saved?.painBodyRatings)
    }

    private fun contact(id: String, role: String, phone: String, name: String = id, person: String = "p1") = CareContact(id, person, name, "Son", role, phone)

    @Test fun callShowsOnlyThisPersonsContactsWithANumberCareTeamFirst() {
        val rows = listOf(
            contact("son", "family", "98765 43210", "Ravi"),
            contact("nurse", "clinician", "+91 99887 76655", "Nurse Mary"),
            contact("nophone", "clinician", ""),
            contact("short", "caregiver", "12345"),
            contact("carer", "caregiver", "080-2345 6789", "Lata"),
            contact("other", "clinician", "9000000000", person = "p2"),
            contact("dup", "family", "+91 98765 43210", "Ravi again"),
        )
        assertEquals(listOf("nurse", "carer", "son"), SimpleModeRules.callable(rows, "p1").map { it.id })
    }

    @Test fun callKeepsTheListShort() {
        val rows = (1..9).map { contact("c$it", "family", "9" + "%09d".format(it), "Name $it") }
        assertEquals(SimpleModeRules.MAX_CONTACTS, SimpleModeRules.callable(rows, "p1").size)
    }

    @Test fun initialIsTheFirstLetter() {
        assertEquals("R", SimpleModeRules.initial(" ravi"))
        assertEquals("M", SimpleModeRules.initial("(mary)"))
        assertEquals("स", SimpleModeRules.initial("सुनीता"))
        assertEquals("?", SimpleModeRules.initial("  12 "))
    }

    private fun dose(time: String, index: Int = 1, count: Int = 1) = MedicineDose(if (time.isBlank()) "n:$index" else "t:$time", index, count, time)

    @Test fun doseTimesFallIntoSunAndMoonGroups() {
        assertEquals(SimpleModeRules.Slot.MORNING, SimpleModeRules.slotFor(dose("08:00")))
        assertEquals(SimpleModeRules.Slot.MORNING, SimpleModeRules.slotFor(dose("04:00")))
        assertEquals(SimpleModeRules.Slot.AFTERNOON, SimpleModeRules.slotFor(dose("13:00")))
        assertEquals(SimpleModeRules.Slot.EVENING, SimpleModeRules.slotFor(dose("18:00")))
        assertEquals(SimpleModeRules.Slot.NIGHT, SimpleModeRules.slotFor(dose("21:00")))
        assertEquals(SimpleModeRules.Slot.NIGHT, SimpleModeRules.slotFor(dose("02:00")))
        // A dose with no saved time is never given one.
        assertEquals(SimpleModeRules.Slot.ANY_TIME, SimpleModeRules.slotFor(dose("", 2, 3)))
    }

    @Test fun groupsAreInDayOrderAndSortedInside() {
        val a = CareItem("a", "p1", "medicine", "Zinc"); val b = CareItem("b", "p1", "medicine", "Aspirin")
        val groups = SimpleModeRules.group(listOf(a to dose("21:00"), a to dose(""), b to dose("08:00"), a to dose("08:00"), b to dose("07:30")))
        assertEquals(listOf(SimpleModeRules.Slot.MORNING, SimpleModeRules.Slot.NIGHT, SimpleModeRules.Slot.ANY_TIME), groups.keys.toList())
        assertEquals(listOf("b@07:30", "b@08:00", "a@08:00"), groups.getValue(SimpleModeRules.Slot.MORNING).map { "${it.first.id}@${it.second.time}" })
    }

    @Test fun medicinesOpenOnTheGroupForNowOrTheNextOne() {
        val all = listOf(SimpleModeRules.Slot.MORNING, SimpleModeRules.Slot.NIGHT, SimpleModeRules.Slot.ANY_TIME)
        assertEquals(SimpleModeRules.Slot.MORNING, SimpleModeRules.startSlot(all, SimpleModeRules.slotAt(LocalTime.of(9, 0))))
        assertEquals(SimpleModeRules.Slot.NIGHT, SimpleModeRules.startSlot(all, SimpleModeRules.slotAt(LocalTime.of(14, 0))))
        assertEquals(SimpleModeRules.Slot.ANY_TIME, SimpleModeRules.startSlot(listOf(SimpleModeRules.Slot.MORNING, SimpleModeRules.Slot.ANY_TIME), SimpleModeRules.Slot.NIGHT))
        assertEquals(SimpleModeRules.Slot.MORNING, SimpleModeRules.startSlot(listOf(SimpleModeRules.Slot.MORNING), SimpleModeRules.Slot.EVENING))
        assertNull(SimpleModeRules.startSlot(emptyList(), SimpleModeRules.Slot.MORNING))
    }

    @Test fun tapsNearAThinPartStillCount() {
        assertEquals("head", SimpleModeRules.siteAt(false, 50f, 15f))
        // Just outside the neck's outline, still the neck (or the head next to it), never nothing.
        assertTrue(SimpleModeRules.siteAt(false, 57f, 31f) in setOf("neck", "head", "left-shoulder", "right-shoulder"))
        assertNull(SimpleModeRules.siteAt(false, 2f, 2f))
    }

    @Test fun voiceFallsBackToEnglishWhenThePhoneLacksIt() {
        assertEquals(CareLang.TE, SimpleModeRules.voiceLanguage(CareLang.TE) { true })
        assertEquals(CareLang.EN, SimpleModeRules.voiceLanguage(CareLang.KN) { false })
        assertEquals(CareLang.EN, SimpleModeRules.voiceLanguage(CareLang.EN) { false })
    }

    @Test fun spokenWordsHaveNoPrognosisWording() {
        val text = SimpleModeRules.dayText(mapOf(SimpleModeRules.Slot.MORNING to listOf("Paracetamol")), "Visit with Dr Rao, Tomorrow", CareLang.EN) +
            SimpleModeRules.medicinesPrompt(SimpleModeRules.Slot.NIGHT, listOf("Morphine"), CareLang.EN)
        listOf("prognosis", "stage", "terminal", "dying", "death", "end of life").forEach { assertTrue(it, !text.lowercase().contains(it)) }
        assertTrue(text.contains("Morning: Paracetamol.") && text.contains("tonight: Morphine."))
    }

    @Test fun simpleModeStartsOnByItselfOnlyForAPatientAged60OrMore() {
        assertTrue(SimpleModeRules.autoOn(60, patientOwnView = true))
        assertTrue(SimpleModeRules.autoOn(82, patientOwnView = true))
        assertTrue(!SimpleModeRules.autoOn(59, patientOwnView = true))
        assertTrue(!SimpleModeRules.autoOn(null, patientOwnView = true))
        assertTrue(!SimpleModeRules.autoOn(75, patientOwnView = false))
    }
}
