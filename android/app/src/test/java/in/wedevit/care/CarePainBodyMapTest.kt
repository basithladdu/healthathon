package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CarePainBodyMapTest {
    private fun note() = SymptomNote("Pain", 5, "2026-10-23", "10:00", "now", "patient", "Fictional patient", painLocation = "Left knee",
        painBodySites = listOf("left-leg"), painBodyPoints = listOf(PainBodyPoint3d("left-leg", .2, .3, .4)), painBodyModel = PainBodyMapRules.MODEL)

    @Test fun patientLateralityIsMirroredInFrontAndCorrectInBack() {
        assertEquals("left-arm", PainBodyMapRules.hit(false, 75f, 70f))
        assertEquals("right-arm", PainBodyMapRules.hit(false, 25f, 70f))
        assertEquals("right-arm", PainBodyMapRules.hit(true, 75f, 70f))
        assertEquals("left-arm", PainBodyMapRules.hit(true, 25f, 70f))
        assertEquals("chest", PainBodyMapRules.hit(false, 50f, 55f))
        assertEquals("upper-back", PainBodyMapRules.hit(true, 50f, 55f))
        assertEquals("lower-back", PainBodyMapRules.hit(true, 50f, 90f))
        assertNull(PainBodyMapRules.hit(false, 1f, 1f))
        assertEquals("बायाँ कंधा", PainBodyMapRules.label("left-shoulder", true))
    }
    @Test fun selectionIsAStableSetAndUnknownRegionsDoNotEnterIt() {
        val first = PainBodyMapRules.toggled(emptyList(), "left-leg")
        assertEquals(listOf("left-leg"), first)
        assertTrue(PainBodyMapRules.toggled(first, "left-leg").isEmpty())
        assertEquals(first, PainBodyMapRules.toggled(first, "not-an-area"))
    }
    @Test fun clickedPointAndTypedLegacyLocationRoundTripWithoutRewritingTheSource() {
        val value = note()
        assertEquals(value, SymptomNoteCodec.decode(SymptomNoteCodec.encode(value)))
        assertTrue(SymptomNoteCodec.readable(SymptomNoteCodec.encode(value)).contains("Left knee"))
        assertTrue(SymptomNoteCodec.readable(SymptomNoteCodec.encode(value)).contains("Left leg / foot"))
        val json = JSONObject(SymptomNoteCodec.encode(value).removePrefix(SymptomNoteCodec.PREFIX)).apply { remove("painBodySites"); remove("painBodyPoints"); remove("painBodyModel") }
        val legacy = SymptomNoteCodec.decode(SymptomNoteCodec.PREFIX + json.toString())!!
        assertEquals("Left knee", legacy.painLocation); assertTrue(legacy.painBodySites.isEmpty()); assertTrue(legacy.painBodyPoints.isEmpty())
    }
    @Test fun invalidPointsRegionsModelsAndNonPainPayloadsAreRejected() {
        val valid = note()
        val bad = listOf(valid.copy(painBodySites = listOf("unknown")), valid.copy(symptom = "Nausea"), valid.copy(painBodyModel = "future-model"),
            valid.copy(painBodyPoints = listOf(PainBodyPoint3d("left-leg", Double.NaN, .3, .4))),
            valid.copy(painBodyPoints = listOf(PainBodyPoint3d("left-leg", .2, 1.1, .4))),
            valid.copy(painBodyPoints = listOf(PainBodyPoint3d("right-leg", .2, .3, .4))),
            valid.copy(painBodyPoints = valid.painBodyPoints + valid.painBodyPoints))
        bad.forEach { assertNotNull(SymptomDiaryRules.error(it)) }
    }
    @Test fun changingAnActualPainPointOrRegionStartsADifferentComparisonSeries() {
        val value = note()
        val row = CareItem(id = "one", personId = "person", kind = "checkin", title = "Pain", detail = SymptomNoteCodec.encode(value))
        assertTrue(SymptomDiaryRules.sameContext(row, value, row, value))
        assertFalse(SymptomDiaryRules.sameContext(row, value, row, value.copy(painBodyPoints = listOf(PainBodyPoint3d("left-leg", .4, .3, .4)))))
        assertFalse(SymptomDiaryRules.sameContext(row, value, row, value.copy(painBodyPoints = emptyList(), painBodyModel = "")))
        assertFalse(SymptomDiaryRules.sameContext(row, value, row, value.copy(painBodySites = listOf("right-leg"))))
    }
    @Test fun offlineViewerOnlyLoadsItsOwnBundledSubtreeAndAcceptsWhitelistedMessages() {
        assertTrue(PainViewerPolicy.localResource(PainViewerPolicy.PAGE))
        assertTrue(PainViewerPolicy.localResource("${PainViewerPolicy.ORIGIN}/assets/pain-body-viewer/body.glb"))
        listOf("https://example.com/body.glb", "file:///android_asset/body.glb", "${PainViewerPolicy.ORIGIN}/assets/private.json",
            "${PainViewerPolicy.ORIGIN}/assets/pain-body-viewer/../private.json", "${PainViewerPolicy.ORIGIN}/assets/pain-body-viewer/%2e%2e%2fprivate.json").forEach { assertFalse(it, PainViewerPolicy.localResource(it)) }
        assertTrue(PainViewerPolicy.trustedOrigin(PainViewerPolicy.ORIGIN))
        assertFalse(PainViewerPolicy.trustedOrigin("https://evil.example"))
        assertEquals("left-leg", PainViewerPolicy.toggledSite("{\"type\":\"toggle\",\"site\":\"left-leg\"}"))
        assertEquals(PainBodyPoint3d("left-leg", .2, .3, .4), PainViewerPolicy.pickedPoint("{\"type\":\"pick\",\"site\":\"left-leg\",\"point\":{\"x\":0.2,\"y\":0.3,\"z\":0.4}}"))
        assertNull(PainViewerPolicy.pickedPoint("{\"type\":\"pick\",\"site\":\"left-leg\",\"point\":{\"x\":0.2,\"y\":3,\"z\":0.4}}"))
        assertNull(PainViewerPolicy.toggledSite("{\"type\":\"delete-record\",\"site\":\"left-leg\"}"))
        assertNull(PainViewerPolicy.toggledSite("x".repeat(181)))
    }
    @Test fun zoomListIsFilteredBySideAndOnlyUsesKnownSites() {
        val front = PainBodyMapRules.sitesFor(false)
        val back = PainBodyMapRules.sitesFor(true)
        assertTrue(front.containsAll(listOf("head", "neck", "chest", "abdomen", "left-arm", "right-leg")))
        assertTrue(back.containsAll(listOf("head", "neck", "upper-back", "lower-back", "left-arm", "right-leg")))
        assertTrue(front.none { it == "upper-back" || it == "lower-back" })
        assertTrue(back.none { it == "chest" || it == "abdomen" })
        assertEquals(PainBodyMapRules.sites.keys.toSet(), (front + back).toSet())
        assertEquals(PainBodyMapRules.sites.keys.toList().filter { it in front }, front)
        assertFalse(PainBodyMapRules.visibleFrom("not-an-area", false))
    }
    @Test fun backViewHeadAndNeckHaveOwnBilingualFocusLabels() {
        assertEquals("Back of head", PainBodyMapRules.focusLabel("head", true, false))
        assertEquals("Head", PainBodyMapRules.focusLabel("head", false, false))
        assertEquals("Neck (back)", PainBodyMapRules.focusLabel("neck", true, false))
        assertEquals("सिर का पिछला हिस्सा", PainBodyMapRules.focusLabel("head", true, true))
        assertEquals("बायाँ हाथ", PainBodyMapRules.focusLabel("left-arm", true, true))
        assertEquals("Upper back", PainBodyMapRules.focusLabel("upper-back", true, false))
    }
    @Test fun heatBlocksRunGreenToRedAndWordsFollowBands() {
        assertEquals(0xFF4CA64CL, PainBodyMapRules.heatColor(1)); assertEquals(0xFFD9363EL, PainBodyMapRules.heatColor(10))
        assertEquals(0xFF8CC152L, PainBodyMapRules.heatColor(2)); assertEquals(0xFF8CC152L, PainBodyMapRules.heatColor(3)); assertEquals(0xFFF6D743L, PainBodyMapRules.heatColor(4))
        assertEquals(0xFFE8693AL, PainBodyMapRules.heatColor(9)); assertEquals(PainBodyMapRules.heatColor(10), PainBodyMapRules.heatColor(99)); assertEquals(PainBodyMapRules.heatColor(0), PainBodyMapRules.heatColor(-3))
        assertEquals(PainBodyMapRules.NEUTRAL, PainBodyMapRules.heatColor(0)); assertFalse(PainBodyMapRules.heatStops.contains(PainBodyMapRules.heatColor(0))); assertNotEquals(PainBodyMapRules.heatColor(0), PainBodyMapRules.heatColor(1)) // zero carries no heat colour
        assertEquals(7, (0..10).map { PainBodyMapRules.heatColor(it) }.toSet().size)
        assertEquals(listOf("None", "Mild", "Mild", "Moderate", "Severe", "Worst"), listOf(0, 3, 1, 5, 8, 10).map { PainBodyMapRules.strengthWord(it, false) })
        assertEquals("हल्का", PainBodyMapRules.strengthWord(2, true)); assertEquals("सबसे ज़्यादा", PainBodyMapRules.strengthWord(10, true))
    }
    @Test fun perSpotRatingsRoundTripValidateAndOldNotesStillLoad() {
        val spots = note().copy(painBodySites = listOf("head", "left-leg"), painBodyRatings = mapOf("head" to 10, "left-leg" to 0))
        val encoded = SymptomNoteCodec.encode(spots)
        assertEquals(spots, SymptomNoteCodec.decode(encoded))
        assertTrue(SymptomNoteCodec.readable(encoded).contains("Head 10/10, Left leg / foot 0/10"))
        // Old records have no painBodyRatings key at all: they load with an empty map and encode back without one.
        val old = note(); val oldEncoded = SymptomNoteCodec.encode(old)
        assertFalse(JSONObject(oldEncoded.removePrefix(SymptomNoteCodec.PREFIX)).has("painBodyRatings"))
        assertEquals(emptyMap<String, Int>(), SymptomNoteCodec.decode(oldEncoded)!!.painBodyRatings)
        assertNull(SymptomDiaryRules.error(spots.copy(painBodyPoints = emptyList(), painBodyModel = ""), java.time.LocalDateTime.of(2026, 10, 24, 0, 0)))
        val now = java.time.LocalDateTime.of(2026, 10, 24, 0, 0)
        listOf(spots.copy(painBodyRatings = mapOf("head" to 11)), spots.copy(painBodyRatings = mapOf("head" to -1)), spots.copy(painBodyRatings = mapOf("neck" to 3)),
            note().copy(symptom = "Nausea", painLocation = "", painBodySites = emptyList(), painBodyPoints = emptyList(), painBodyModel = "", painBodyRatings = mapOf("head" to 3))).forEach { assertNotNull(SymptomDiaryRules.error(it, now)) }
        // A follow-up is a new observation: spot ratings are cleared while the location stays.
        assertTrue(SymptomDiaryRules.followUp(spots).painBodyRatings.isEmpty()); assertEquals(spots.painBodySites, SymptomDiaryRules.followUp(spots).painBodySites)
        // Ratings are values, not context: two notes at the same places with different spot ratings are the same series.
        val a = CareItem(id = "a", personId = "p", kind = "checkin", title = "Pain"); assertTrue(SymptomDiaryRules.sameContext(a, spots, a, spots.copy(painBodyRatings = mapOf("head" to 2))))
    }
    @Test fun overallIsTheHighestRatedSpotAndNullWhenNoneAreRated() {
        assertEquals(10, PainBodyMapRules.overall(mapOf("head" to 10, "neck" to 5))); assertEquals(0, PainBodyMapRules.overall(mapOf("neck" to 0))); assertNull(PainBodyMapRules.overall(emptyMap()))
        val key = PainBodyMapRules.ratingsKey(mapOf("neck" to 5, "head" to 10)); assertEquals("head:10|neck:5", key)
        assertEquals(mapOf("head" to 10, "neck" to 5), PainBodyMapRules.parseRatingsKey(key)); assertEquals(mapOf("head" to 1), PainBodyMapRules.parseRatingsKey("head:1|x:3|neck:12|neck:abc"))
    }
    @Test fun friendlyDateWordsFollowTheCalendar() {
        val today = java.time.LocalDate.of(2026, 10, 26)
        assertTrue(friendlyWhen("2026-10-26", "16:01", false, today).startsWith("Today, 4:01")); assertTrue(friendlyWhen("2026-10-25", "09:05", false, today).startsWith("Yesterday, 9:05"))
        assertTrue(friendlyWhen("2026-10-20", "16:01", false, today).startsWith("20 Oct, 4:01")); assertTrue(friendlyWhen("2025-10-20", "16:01", false, today).startsWith("20 Oct 2025, 4:01"))
        assertTrue(friendlyWhen("2026-10-26", "16:01", true, today).startsWith("आज, ")); assertEquals("bad 99", friendlyWhen("bad", "99", false, today))
    }
    @Test fun markingAndUnmarkingAreIdempotentAndKeepBodyOrder() {
        val marked = PainBodyMapRules.marked(listOf("left-leg"), "head")
        assertEquals(listOf("head", "left-leg"), marked)
        assertEquals(marked, PainBodyMapRules.marked(marked, "head"))
        assertEquals(marked, PainBodyMapRules.marked(marked, "not-an-area"))
        assertEquals(listOf("left-leg"), PainBodyMapRules.unmarked(marked, "head"))
        assertEquals(listOf("left-leg"), PainBodyMapRules.unmarked(listOf("left-leg"), "head"))
    }
}
