package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DoctorPatientAlertsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    // A day back from the real date, so no note is "in the future" for the symptom validator.
    private val today = LocalDate.now(ZoneOffset.UTC).minusDays(1)
    private val now = today.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private fun symptom(id: String, name: String, rating: Int?, date: LocalDate, person: String = "p1", ratings: Map<String, Int> = emptyMap(), mood: Map<String, Int> = emptyMap()) =
        CareItem(id = id, personId = person, kind = "checkin", title = name, created = now - 30 * day,
            detail = SymptomNoteCodec.encode(SymptomNote(symptom = name, rating = rating, observedDate = date.toString(), observedTime = "10:00", respondent = "patient", author = "Asha", painBodySites = ratings.keys.toList(), painBodyRatings = ratings, moodRatings = mood)))

    private fun distress(id: String, score: Int?, at: Long, person: String = "p1") =
        CareItem(id = id, personId = person, kind = "checkin", title = "d", created = at, detail = DistressCodec.encode(if (score == null) DistressCheck(false) else DistressCheck(true, score)))

    @Test fun severeIsSevenOrMoreOnPainOrDistressInTheLastSevenDaysNewestFirst() {
        val items = listOf(
            symptom("pain8", "Pain", 8, today.minusDays(2)),
            symptom("pain6", "Pain", 6, today.minusDays(1)),
            symptom("painOld", "Pain", 9, today.minusDays(8)),
            distress("dist7", 7, now - day),
            distress("distNo", null, now - 2 * 3_600_000L),
            symptom("nausea9", "Nausea", 9, today),
            symptom("mood9", SymptomDiaryRules.MOOD, null, today, mood = mapOf("anxiety" to 9)),
            symptom("other", "Pain", 10, today, person = "p2"),
        )
        val found = SevereRules.entries(items, "p1", now, zone)
        assertEquals(listOf("dist7", "pain8"), found.map { it.itemId })
        assertEquals(listOf(SevereKind.DISTRESS, SevereKind.PAIN), found.map { it.kind })
        assertEquals(listOf(7, 8), found.map { it.score })
    }

    @Test fun painCountsByItsHighestRatedSpotAndAtMostThreeLinesShow() {
        val spots = symptom("spot", "Pain", 3, today, ratings = mapOf("head" to 7, "neck" to 2))
        assertEquals(7, SevereRules.entries(listOf(spots), "p1", now, zone).single().score)
        val many = (1..5).map { symptom("p$it", "Pain", 8, today.minusDays(it.toLong())) }
        val lines = SevereRules.lines(SevereRules.entries(many, "p1", now, zone))
        assertEquals(listOf("p1", "p2", "p3"), lines.map { it.itemId })
    }

    @Test fun showsOncePerNewSevereEntryAndAgainWhenAScoreChanges() {
        val first = SevereRules.entries(listOf(symptom("a", "Pain", 8, today.minusDays(1))), "p1", now, zone)
        assertTrue(SevereRules.shouldShow(first, emptySet()))
        val acknowledged = first.map { it.key }.toSet()
        assertFalse(SevereRules.shouldShow(first, acknowledged))
        val more = SevereRules.entries(listOf(symptom("a", "Pain", 8, today.minusDays(1)), distress("b", 7, now - 3_600_000L)), "p1", now, zone)
        assertTrue(SevereRules.shouldShow(more, acknowledged))
        val edited = SevereRules.entries(listOf(symptom("a", "Pain", 9, today.minusDays(1))), "p1", now, zone)
        assertTrue(SevereRules.shouldShow(edited, acknowledged))
        assertFalse(SevereRules.shouldShow(emptyList(), emptySet()))
    }

    private fun view(at: Long, place: String?) = JSONObject().put("at", java.time.Instant.ofEpochMilli(at).toString()).put("place", place ?: JSONObject.NULL)

    @Test fun recentViewsDecodeNewestFirstWithCleanPlaces() {
        val status = EmergencyLinkRules.decodeStatus(JSONObject().put("live", true).put("views", 3).put("recent_views", JSONArray()
            .put(view(now - 2 * day, "  City\nHospital  ")).put(view(now - day, null)).put(JSONObject().put("at", "bad"))).toString())
        assertEquals(listOf(now - day, now - 2 * day), status.recentViews.map { it.at })
        assertEquals(listOf("", "City Hospital"), status.recentViews.map { it.place })
        assertEquals(80, EmergencyViewRules.cleanPlace("x".repeat(200)).length)
        // An older server without recent_views still decodes.
        assertTrue(EmergencyLinkRules.decodeStatus("""{"live":true,"views":2}""").recentViews.isEmpty())
    }

    @Test fun onlyViewsNewerThanLastSeenAndFromTheLastWeekAreNew() {
        val views = listOf(EmergencyCardView(now - 3_600_000L, "Apollo"), EmergencyCardView(now - 2 * day, "KIMS"), EmergencyCardView(now - 10 * day, "Old"))
        assertEquals(listOf("Apollo", "KIMS"), EmergencyViewRules.newSince(views, 0L, now).map { it.place })
        assertEquals(listOf("Apollo"), EmergencyViewRules.newSince(views, now - 2 * day, now).map { it.place })
        assertTrue(EmergencyViewRules.newSince(views, now - 3_600_000L, now).isEmpty())
        assertTrue(EmergencyViewRules.newSince(emptyList(), 0L, now).isEmpty())
    }

    @Test fun noticeSaysWhoWhereAndWhen() {
        val text = emergencyViewNotice("Asha", EmergencyCardView(now, "City Hospital"), hindi = false, zone = zone)
        assertTrue(text, text.startsWith("Emergency card for Asha opened at City Hospital, ${today.dayOfMonth} "))
        assertTrue(emergencyViewNotice("Asha", EmergencyCardView(now, ""), hindi = false, zone = zone).contains("place not given"))
    }
}
