package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

/** 28 Oct meeting: pain bands beside 0-10, Mood (two scales) replacing Sadness/Worry, and each body part's own pain trend. */
class CareSymptomBandMoodTrendTest {
    private val now = LocalDateTime.of(2026, 10, 28, 18, 0)
    private fun pain(rating: Int?, date: String, time: String = "10:00", sites: List<String> = emptyList(), spots: Map<String, Int> = emptyMap()) =
        SymptomNote("Pain", rating, date, time, "now", "patient", "Rahul", painBodySites = sites, painBodyRatings = spots)
    private fun other(symptom: String, rating: Int?, date: String, time: String = "09:00") = SymptomNote(symptom, rating, date, time, "now", "patient", "Rahul")
    private fun row(id: String, value: SymptomNote, person: String = "rahul") = CareItem(id = id, personId = person, kind = "checkin", title = value.symptom, detail = SymptomNoteCodec.encode(value))
    private fun payload(item: CareItem) = JSONObject(item.detail.removePrefix(SymptomNoteCodec.PREFIX))

    @Test fun painBandsFollowTheAgreedCutOffs() {
        val expected = listOf("none", "mild", "mild", "mild", "moderate", "moderate", "moderate", "severe", "severe", "severe", "severe")
        assertEquals(expected, (0..10).map(PainBands::key))
        assertEquals(listOf("None", "Mild", "Moderate", "Severe"), listOf(0, 3, 4, 10).map { PainBands.label(it, false) })
        assertEquals(listOf("कोई नहीं", "हल्का", "मध्यम", "तेज़"), listOf(0, 1, 6, 7).map { PainBands.label(it, true) })
        (0..10).forEach { assertEquals(PainBands.label(it, false), PainBodyMapRules.strengthWord(it, false)) }
        assertEquals("severe", PainBands.key(12)); assertEquals("none", PainBands.key(-1))
        assertTrue(PainBands.SOURCE_EN.startsWith("Source:")); assertTrue(PainBands.SOURCE_EN.contains("NCCN"))
        assertTrue(HindiScan.hasHindi(PainBands.SOURCE_HI))
    }

    @Test fun painNotesSaveBothNumberAndBandAndStillRoundTrip() {
        val value = pain(7, "2026-10-26", sites = listOf("abdomen", "head"), spots = mapOf("head" to 7, "abdomen" to 3))
        val item = row("p", value)
        assertEquals("severe", payload(item).getString("ratingBand"))
        assertEquals("severe", payload(item).getJSONObject("painBodyBands").getString("head"))
        assertEquals("mild", payload(item).getJSONObject("painBodyBands").getString("abdomen"))
        assertEquals(7, payload(item).getInt("rating"))
        assertEquals(value, SymptomNoteCodec.decode(item.detail))
        assertFalse(payload(row("u", pain(null, "2026-10-26"))).has("ratingBand"))
        assertFalse(payload(row("n", other("Nausea", 8, "2026-10-26"))).has("ratingBand"))
        assertTrue(SymptomNoteCodec.readable(item.detail).contains("7/10 · Severe"))
    }

    @Test fun oldSadnessAndWorryNotesReadAsMoodWithoutRewritingTheRecord() {
        val sad = row("sad", other("Sadness", 6, "2026-10-20"))
        val worry = row("worry", other("Worry", 3, "2026-10-21"))
        val unrated = row("unrated", other("Sadness", null, "2026-10-22"))
        val stored = sad.detail
        val mood = SymptomNoteCodec.decode(sad.detail)!!
        assertEquals("Mood", mood.symptom); assertNull(mood.rating); assertEquals(mapOf("low-mood" to 6), mood.moodRatings)
        assertEquals(mapOf("anxiety" to 3), SymptomNoteCodec.decode(worry.detail)!!.moodRatings)
        assertEquals(emptyMap<String, Int>(), SymptomNoteCodec.decode(unrated.detail)!!.moodRatings)
        assertEquals(stored, sad.detail)
        assertEquals("Sadness", payload(sad).getString("symptom"))
        assertEquals("उदासी", SymptomDiaryRules.symptomLabel("Sadness", true))
        assertTrue(SymptomNoteCodec.readable(sad.detail).contains("Low mood 6/10 · Anxiety Not rated"))
    }

    @Test fun moodKeepsTwoSeparateScalesAndRejectsMixedRatings() {
        val value = other("Mood", null, "2026-10-26").copy(moodRatings = mapOf("low-mood" to 4, "anxiety" to 7))
        assertNull(SymptomDiaryRules.error(value, now))
        assertEquals(value, SymptomNoteCodec.decode(SymptomNoteCodec.encode(value)))
        assertNull(SymptomDiaryRules.error(value.copy(moodRatings = emptyMap()), now))
        assertNotNull(SymptomDiaryRules.error(value.copy(rating = 5), now))
        assertNotNull(SymptomDiaryRules.error(value.copy(moodRatings = mapOf("anger" to 2)), now))
        assertNotNull(SymptomDiaryRules.error(value.copy(moodRatings = mapOf("anxiety" to 11)), now))
        assertNotNull(SymptomDiaryRules.error(other("Nausea", 3, "2026-10-26").copy(moodRatings = mapOf("anxiety" to 2)), now))
        assertTrue(SymptomDiaryRules.symptoms.containsKey("Mood")); assertEquals(listOf("low-mood", "anxiety"), SymptomDiaryRules.moodScales.keys.toList())
        assertTrue(HindiScan.hasHindi(SymptomDiaryRules.symptomLabel("Mood", true)))
        assertEquals(emptyMap<String, Int>(), SymptomDiaryRules.followUp(value).moodRatings)
    }

    @Test fun moodSeriesJoinsMigratedAndNewNotesInObservedOrder() {
        val items = listOf(
            row("new", other("Mood", null, "2026-10-26").copy(moodRatings = mapOf("low-mood" to 2, "anxiety" to 5))),
            row("old-sad", other("Sadness", 6, "2026-10-20")), row("old-worry", other("Worry", 8, "2026-10-21")),
            row("other-person", other("Sadness", 9, "2026-10-22"), person = "arjun"))
        assertEquals(listOf(6, 2), SymptomDiaryRules.moodSeries(items, "rahul", "low-mood").map { it.value })
        assertEquals(listOf("old-worry", "new"), SymptomDiaryRules.moodSeries(items, "rahul", "anxiety").map { it.itemId })
        assertEquals(listOf("2026-10-21", "2026-10-26"), SymptomDiaryRules.moodSeries(items, "rahul", "anxiety").map { it.date })
    }

    @Test fun eachBodyPartGetsItsOwnDatedSeries() {
        val items = listOf(
            row("d3", pain(8, "2026-10-26", sites = listOf("head", "abdomen"), spots = mapOf("head" to 8, "abdomen" to 2))),
            row("d1", pain(10, "2026-10-24", sites = listOf("head"), spots = mapOf("head" to 10))),
            row("d2", pain(9, "2026-10-25", "08:00", sites = listOf("head", "chest"), spots = mapOf("head" to 9))),
            row("legacy", pain(6, "2026-10-23", sites = listOf("lower-back"))),
            row("unrated", pain(null, "2026-10-26", "11:00", sites = listOf("head"))),
            row("nausea", other("Nausea", 7, "2026-10-26")),
            row("arjun", pain(3, "2026-10-26", sites = listOf("head"), spots = mapOf("head" to 3)), person = "arjun"))
        val head = SymptomDiaryRules.painPartSeries(items, "rahul", "head")
        assertEquals(listOf(10, 9, 8), head.map { it.value })
        assertEquals(listOf("2026-10-24", "2026-10-25", "2026-10-26"), head.map { it.date })
        assertEquals(listOf("d1", "d2", "d3"), head.map { it.itemId })
        assertEquals(listOf(2), SymptomDiaryRules.painPartSeries(items, "rahul", "abdomen").map { it.value })
        // A marked part without its own strength on a note that has spot ratings is not given another part's number.
        assertEquals(emptyList<DatedRating>(), SymptomDiaryRules.painPartSeries(items, "rahul", "chest"))
        // Older notes without spot ratings coloured every marked part with the note's rating, so the part trend uses it.
        assertEquals(listOf(6), SymptomDiaryRules.painPartSeries(items, "rahul", "lower-back").map { it.value })
        assertEquals(listOf("head", "abdomen", "lower-back"), SymptomDiaryRules.painPartsWithHistory(items, "rahul"))
        assertEquals(listOf(3), SymptomDiaryRules.painPartSeries(items, "arjun", "head").map { it.value })
    }

    @Test fun doctorSummaryReadsOnlyThisPatientAndSplitsMoodAndBodyParts() {
        val items = listOf(
            row("p1", pain(9, "2026-10-25", sites = listOf("head"), spots = mapOf("head" to 9))),
            row("p2", pain(7, "2026-10-26", sites = listOf("head"), spots = mapOf("head" to 7))),
            row("sad", other("Sadness", 5, "2026-10-24")),
            row("arjun", other("Nausea", 8, "2026-10-27"), person = "arjun"),
            CareItem(id = "day", personId = "rahul", kind = "checkin", title = "A good day", detail = "Rested well."))
        val rows = DoctorSymptomSummaryRules.rows(items, "rahul")
        assertEquals(setOf("symptom:Pain", "part:head", "mood:low-mood"), rows.map { "${it.kind}:${it.key}" }.toSet())
        assertEquals("mood:low-mood", rows.last().let { "${it.kind}:${it.key}" })
        assertEquals(listOf(9, 7), rows.first { it.kind == "part" }.series.map { it.value })
        assertTrue(rows.first { it.kind == "part" }.painBands); assertFalse(rows.first { it.kind == "mood" }.painBands)
        assertEquals("Pain · Head", DoctorSymptomSummaryRules.label(rows.first { it.kind == "part" }, false))
        assertTrue(DoctorSymptomSummaryRules.rows(items, "nobody").isEmpty())
    }
}
