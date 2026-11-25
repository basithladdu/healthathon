package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SymptomScaleRulesTest {

    @Test fun facesSaveTheDocumentedNumbers() {
        assertEquals(0, SymptomScaleRules.rating(SymptomScaleRules.Level.NONE))
        assertEquals(3, SymptomScaleRules.rating(SymptomScaleRules.Level.A_LITTLE))
        assertEquals(6, SymptomScaleRules.rating(SymptomScaleRules.Level.A_LOT))
        assertEquals(9, SymptomScaleRules.rating(SymptomScaleRules.Level.WORST))
    }

    @Test fun everyNumberReadsAsTheNearestFace() {
        assertEquals(SymptomScaleRules.Level.NONE, SymptomScaleRules.fromRating(0))
        (1..4).forEach { assertEquals(SymptomScaleRules.Level.A_LITTLE, SymptomScaleRules.fromRating(it)) }
        (5..7).forEach { assertEquals(SymptomScaleRules.Level.A_LOT, SymptomScaleRules.fromRating(it)) }
        (8..10).forEach { assertEquals(SymptomScaleRules.Level.WORST, SymptomScaleRules.fromRating(it)) }
        assertEquals(SymptomScaleRules.Level.NONE, SymptomScaleRules.fromRating(-3))
        assertEquals(SymptomScaleRules.Level.WORST, SymptomScaleRules.fromRating(14))
    }

    @Test fun faceToNumberToFaceRoundTrips() {
        SymptomScaleRules.Level.entries.forEach { assertEquals(it, SymptomScaleRules.fromRating(SymptomScaleRules.rating(it))) }
    }

    @Test fun sameMappingAsSimpleMode() = assertTrue(SymptomScaleRules.matchesSimpleMode())

    @Test fun facesGoFromSmileToSad() {
        val faces = SymptomScaleRules.Level.entries.map { it.face }
        assertEquals(faces.sortedDescending(), faces)
        assertEquals(1f, SymptomScaleRules.distressFace(0), 0f)
        assertEquals(0f, SymptomScaleRules.distressFace(10), 0f)
    }

    @Test fun onlyPatientsGetPicturesByDefault() {
        assertTrue(SymptomScaleRules.picturesByDefault("Patient"))
        assertFalse(SymptomScaleRules.picturesByDefault("Family"))
        assertFalse(SymptomScaleRules.picturesByDefault("Doctor"))
        assertFalse(SymptomScaleRules.picturesByDefault(""))
    }

    @Test fun shownAsWordForPatientsAndNumberForOthers() {
        assertEquals("A lot", SymptomScaleRules.shown(6, pictures = true, hindi = false))
        assertEquals("6/10", SymptomScaleRules.shown(6, pictures = false, hindi = false))
        assertEquals("Worst", SymptomScaleRules.shown(10, pictures = true, hindi = false))
        assertEquals("Very well", SymptomScaleRules.shown(0, true, false, SymptomDiaryRules.WELLBEING_SCALE))
        assertTrue(HindiScan.hasHindi(SymptomScaleRules.shown(3, true, true)))
    }

    @Test fun partsOfTheDayMatchTheMedicineCutOffs() {
        assertEquals(DoseSlot.MORNING, SymptomScaleRules.partOf(LocalTime.of(7, 30)))
        assertEquals(DoseSlot.AFTERNOON, SymptomScaleRules.partOf(LocalTime.of(12, 0)))
        assertEquals(DoseSlot.EVENING, SymptomScaleRules.partOf(LocalTime.of(19, 59)))
        assertEquals(DoseSlot.NIGHT, SymptomScaleRules.partOf(LocalTime.of(23, 0)))
        assertEquals(DoseSlot.NIGHT, SymptomScaleRules.partOf(LocalTime.of(2, 0)))
        DoseSlot.entries.forEach { assertEquals(it, SymptomScaleRules.partOf(SymptomScaleRules.partTime(it))) }
    }

    @Test fun laterPartsOfTodayCannotBeChosen() {
        val now = LocalDateTime.of(2026, 11, 25, 10, 15)
        val today = now.toLocalDate()
        assertTrue(SymptomScaleRules.partAllowed(DoseSlot.MORNING, today, now))
        assertFalse(SymptomScaleRules.partAllowed(DoseSlot.AFTERNOON, today, now))
        assertFalse(SymptomScaleRules.partAllowed(DoseSlot.NIGHT, today, now))
        DoseSlot.entries.forEach { assertTrue(SymptomScaleRules.partAllowed(it, today.minusDays(1), now)) }
        assertFalse(SymptomScaleRules.partAllowed(DoseSlot.MORNING, today.plusDays(1), now))
        // Just after midnight it is night now, so Night is allowed and saved as now, not 9 PM.
        val lateNight = LocalDateTime.of(2026, 11, 25, 1, 10)
        assertTrue(SymptomScaleRules.partAllowed(DoseSlot.NIGHT, lateNight.toLocalDate(), lateNight))
        assertEquals("01:10", SymptomScaleRules.timeFor(DoseSlot.NIGHT, lateNight.toLocalDate(), lateNight))
    }

    @Test fun tappedPartIsNeverInTheFuture() {
        val now = LocalDateTime.of(2026, 11, 25, 7, 5)
        assertEquals("07:05", SymptomScaleRules.timeFor(DoseSlot.MORNING, now.toLocalDate(), now))
        assertEquals("21:00", SymptomScaleRules.timeFor(DoseSlot.NIGHT, now.toLocalDate().minusDays(1), now))
        assertEquals("13:00", SymptomScaleRules.timeFor(DoseSlot.AFTERNOON, LocalDate.of(2026, 11, 20), now))
        assertEquals("07:05", SymptomScaleRules.clamp(now.toLocalDate(), "18:00", now))
        assertEquals("18:00", SymptomScaleRules.clamp(now.toLocalDate().minusDays(1), "18:00", now))
        assertEquals("06:00", SymptomScaleRules.clamp(now.toLocalDate(), "06:00", now))
    }

    @Test fun startedOnCountsBackFromTheNote() {
        val day = LocalDate.of(2026, 11, 25)
        assertEquals("2026-11-25", SymptomScaleRules.startedOn(SymptomScaleRules.Day.TODAY, day))
        assertEquals("2026-11-24", SymptomScaleRules.startedOn(SymptomScaleRules.Day.YESTERDAY, day))
        assertNull(SymptomScaleRules.startedOn(SymptomScaleRules.Day.EARLIER, day))
        assertEquals(SymptomScaleRules.Day.YESTERDAY, SymptomScaleRules.dayOf(day.minusDays(1), day))
        assertEquals(SymptomScaleRules.Day.EARLIER, SymptomScaleRules.dayOf(day.minusDays(5), day))
    }

    @Test fun promptsFollowTheTimeOfDay() {
        assertEquals("How much pain this morning?", SymptomScaleRules.prompt("Pain", DoseSlot.MORNING, false))
        assertEquals("How much pain tonight?", SymptomScaleRules.prompt("Pain", DoseSlot.NIGHT, false))
        assertEquals("How are you feeling this evening?", SymptomScaleRules.prompt(SymptomDiaryRules.WELLBEING_SYMPTOM, DoseSlot.EVENING, false))
        DoseSlot.entries.forEach { assertTrue(HindiScan.hasHindi(SymptomScaleRules.prompt("Pain", it, true))) }
        // No prognosis or end-of-life words in any prompt.
        DoseSlot.entries.forEach { p -> listOf("die", "dying", "stage", "terminal").forEach { assertFalse(SymptomScaleRules.prompt("Nausea", p, false).lowercase().contains(it)) } }
    }

    @Test fun spokenPromptListsTheFourWords() =
        assertEquals("How much pain? None, A little, A lot, Worst.", SymptomScaleRules.spoken("How much pain?", SymptomDiaryRules.PERSONAL_SCALE, false))

    @Test fun distressPictureGroupsCoverTheFiveNccnGroups() {
        assertEquals(DistressRules.categories.map { it.en }, SymptomScaleRules.DistressGroup.entries.map { it.category })
        assertEquals(SymptomScaleRules.DistressGroup.BODY, SymptomScaleRules.groupOfProblem("Pain"))
        assertEquals(SymptomScaleRules.DistressGroup.MONEY, SymptomScaleRules.groupOfProblem("Finances"))
        assertNull(SymptomScaleRules.groupOfProblem("Not on the list"))
        assertEquals(listOf("body", "faith"), SymptomScaleRules.shownGroups(listOf("faith"), listOf("Pain")))
    }

    @Test fun distressGroupsRoundTripAndShowInTheSummary() {
        val check = DistressCheck(true, 6, listOf("Pain"), groups = listOf("money", "body"))
        val decoded = DistressCodec.decode(DistressCodec.encode(check))!!
        assertEquals(listOf("body", "money"), decoded.groups)
        assertEquals("Pain, Money and practical", DistressRules.summary(decoded, false))
        assertNotNull(DistressRules.error(DistressCheck(true, 4, groups = listOf("unknown"))))
        assertNotNull(DistressRules.error(DistressCheck(false, groups = listOf("body"))))
        // Answers without pictures are stored exactly as before.
        assertFalse(DistressCodec.encode(DistressCheck(true, 3)).contains("groups"))
        assertEquals(emptyList<String>(), DistressCodec.decode(DistressCodec.encode(DistressCheck(true, 3)))!!.groups)
    }
}
