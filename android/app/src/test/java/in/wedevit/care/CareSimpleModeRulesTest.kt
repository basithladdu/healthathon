package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class CareSimpleModeRulesTest {
    private val word = SimpleModeRules.PainWord.entries

    @Test fun labelFloorIs18spAtNormalSizeAndKeepsThatLookWithALargerFont() {
        assertEquals(18f, SimpleModeRules.labelFloor(1f), 0.001f)
        assertEquals(18f, SimpleModeRules.labelFloor(0.85f), 0.001f) // a smaller font never lowers the floor below 18sp
        assertEquals(12f, SimpleModeRules.labelFloor(1.5f), 0.001f)
        assertEquals(9f, SimpleModeRules.labelFloor(2f), 0.001f)
        // What is drawn (floor x font scale) never falls below 18sp-at-normal-size.
        listOf(1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f).forEach { assertEquals(18f, SimpleModeRules.labelFloor(it) * it, 0.001f) }
    }

    @Test fun pictureGoesAboveTheWordsOnlyWithALargeFont() {
        assertTrue(!SimpleModeRules.stackIcon(1f)); assertTrue(!SimpleModeRules.stackIcon(1.15f))
        assertTrue(SimpleModeRules.stackIcon(1.3f)); assertTrue(SimpleModeRules.stackIcon(2f))
    }

    @Test fun bodyPictureTakesTheRestButNeverLessThanTheMinimum() {
        assertEquals(400, SimpleModeRules.fillHeight(viewport = 700, used = 300, min = 240))
        assertEquals(240, SimpleModeRules.fillHeight(viewport = 500, used = 450, min = 240))
        assertEquals(240, SimpleModeRules.fillHeight(viewport = 400, used = 900, min = 240))
        assertEquals(240, SimpleModeRules.MIN_BODY_DP)
    }

    @Test fun wherePromptAddsTheVoiceHintOnlyOnceAPartIsMarkedAndRecordingIsAllowed() {
        val ask = "Where does it hurt? Touch the place on the body."
        assertEquals(ask, SimpleModeRules.wherePrompt(false, true, CareLang.EN))
        assertEquals(ask, SimpleModeRules.wherePrompt(true, false, CareLang.EN))
        assertEquals("$ask To say more in your own voice, tap Record your voice.", SimpleModeRules.wherePrompt(true, true, CareLang.EN))
        assertTrue(SimpleModeRules.wherePrompt(true, true, CareLang.HI).startsWith("दर्द कहाँ है?"))
    }

    @Test fun saharaStaysQuietWhileThePatientIsRecorded() {
        assertTrue(SimpleModeRules.speakNow(recording = false))
        assertTrue(!SimpleModeRules.speakNow(recording = true))
    }

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
    @Test fun homeHasExactlyFourButtons() {
        assertEquals(listOf("FEEL", "MEDICINES", "READ", "CALL"), SimpleModeRules.HomeButton.entries.map { it.name })
    }

    @Test fun facesScaleGoesFromSmilingToSadAndFollowsTheClinicianNumber() {
        assertEquals(listOf(1f, .67f, .33f, 0f), word.map { SimpleModeRules.faceScore(it) })
        assertEquals(1f, SimpleModeRules.faceForRating(0))
        assertEquals(.67f, SimpleModeRules.faceForRating(3))
        assertEquals(.33f, SimpleModeRules.faceForRating(7))
        assertEquals(0f, SimpleModeRules.faceForRating(10))
        // Every word's saved number comes back to the same face.
        word.forEach { assertEquals(SimpleModeRules.faceScore(it), SimpleModeRules.faceForRating(SimpleModeRules.rating(it))) }
    }

    private fun messageItem(id: String, from: SimpleModeRules.MessageFrom, name: String, text: String, created: Long, person: String = "p1") =
        CareItem(id, person, "journal", SimpleModeRules.MESSAGE_TITLE, SimpleModeRules.messageDetail(from, name, text), created = created)

    @Test fun messageToReadAloudRoundTripsAsPlainJournalText() {
        val item = messageItem("m1", SimpleModeRules.MessageFrom.NURSE, " Sister\nMary ", "  Take the night medicine after food.\nI will visit on Friday. ", 10)
        assertEquals("From: nurse · Sister Mary\n\nTake the night medicine after food.\nI will visit on Friday.", item.detail)
        val message = SimpleModeRules.message(item)!!
        assertEquals(SimpleModeRules.MessageFrom.NURSE, message.from)
        assertEquals("Sister Mary", message.name)
        assertEquals("Take the night medicine after food.\nI will visit on Friday.", message.text)
        val noName = SimpleModeRules.message(messageItem("m2", SimpleModeRules.MessageFrom.DOCTOR, "", "Hello", 1))!!
        assertEquals("", noName.name); assertEquals(SimpleModeRules.MessageFrom.DOCTOR, noName.from)
        // Ordinary journal notes, other kinds, unknown senders and empty messages are not messages.
        assertNull(SimpleModeRules.message(CareItem("j", "p1", "journal", "Slept well", "From: nurse\n\nhi")))
        assertNull(SimpleModeRules.message(item.copy(kind = "task")))
        assertNull(SimpleModeRules.message(item.copy(detail = "From: priest\n\nhi")))
        assertNull(SimpleModeRules.message(item.copy(detail = "From: doctor\n\n   ")))
    }

    @Test fun readToMeReadsTheNewestMessageForThisPerson() {
        val items = listOf(
            messageItem("a", SimpleModeRules.MessageFrom.DOCTOR, "", "Old", 10),
            messageItem("b", SimpleModeRules.MessageFrom.NURSE, "", "New", 20),
            messageItem("c", SimpleModeRules.MessageFrom.FAMILY, "", "Other person", 30, person = "p2"),
            CareItem("d", "p1", "journal", "Diary", "Newest diary line", created = 40),
        )
        assertEquals("b", SimpleModeRules.latestMessage(items, "p1")?.itemId)
        assertNull(SimpleModeRules.latestMessage(items, "p3"))
        val spoken = SimpleModeRules.readPrompt(SimpleModeRules.latestMessage(items, "p1"), CareLang.EN)
        assertEquals("Here is the message from your nurse.\nNew", spoken)
        assertEquals("Here is the message from your doctor", SimpleModeRules.messageLead(SimpleModeRules.MessageFrom.DOCTOR, CareLang.EN))
        val none = SimpleModeRules.readPrompt(null, CareLang.EN)
        assertEquals("There is no message from your doctor or nurse yet.", none)
        listOf("prognosis", "stage", "terminal", "dying", "death", "end of life").forEach { assertTrue(it, !(spoken + none).lowercase().contains(it)) }
    }

    @Test fun messageMustHaveWordsAndStayShort() {
        assertTrue(SimpleModeRules.messageError("  ", CareLang.EN) != null)
        assertTrue(SimpleModeRules.messageError("x".repeat(SimpleModeRules.MESSAGE_MAX + 1), CareLang.EN) != null)
        assertNull(SimpleModeRules.messageError("Take the night medicine.", CareLang.EN))
    }

    @Test fun voiceNoteTitleAndClock() {
        assertEquals("0:00", SimpleModeRules.clock(-3))
        assertEquals("0:42", SimpleModeRules.clock(42))
        assertEquals("3:00", SimpleModeRules.clock(SimpleModeRules.VOICE_MAX_SECONDS))
        val title = SimpleModeRules.voiceNoteTitle(125)
        assertEquals("Voice note from the patient (2:05)", title)
        assertTrue(SimpleModeRules.isVoiceNote(CareItem("v", "p1", "journal", title, SimpleModeRules.VOICE_NOTE_DETAIL)))
        assertTrue(!SimpleModeRules.isVoiceNote(CareItem("v", "p1", "journal", "Slept well", SimpleModeRules.VOICE_NOTE_DETAIL)))
        assertTrue(!SimpleModeRules.isVoiceNote(CareItem("v", "p1", "checkin", title, SimpleModeRules.VOICE_NOTE_DETAIL)))
    }

    @Test fun onlyPlainIdsBecomeFileNames() {
        assertTrue(SimpleModeRules.safeFileId("3f2a9c1e-77b0-4c1d-9e0f-1a2b3c4d5e6f"))
        assertTrue(SimpleModeRules.safeFileId("contact_12"))
        listOf("", "../x", "a/b", "a\\b","x".repeat(81), "a.jpg").forEach { assertTrue(it, !SimpleModeRules.safeFileId(it)) }
    }
}
