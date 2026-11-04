package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.time.LocalDate

class CarePatientProfileTest {
    @Test fun roundTripKeepsEveryField() {
        val p = CarePatientProfile(58, "Female", 62.5, 160, "O+", "Carcinoma stomach", "Penicillin", "98765 43210", "Pune")
        assertEquals(p, CarePatientProfile.decode(p.encode()))
    }
    @Test fun emptyAndBrokenDecodeToBlank() {
        assertEquals(CarePatientProfile(), CarePatientProfile.decode(CarePatientProfile().encode()))
        assertEquals(CarePatientProfile(), CarePatientProfile.decode("not json"))
    }
    @Test fun ageRange() {
        assertEquals(null to null, CarePatientProfileRules.age(" "))
        assertEquals(0 to null, CarePatientProfileRules.age("0"))
        assertEquals(120 to null, CarePatientProfileRules.age("120"))
        assertEquals(ProfileError.AGE, CarePatientProfileRules.age("121").second)
        assertEquals(ProfileError.AGE, CarePatientProfileRules.age("abc").second)
    }
    @Test fun weightRange() {
        assertEquals(62.5 to null, CarePatientProfileRules.weight("62,5"))
        assertEquals(ProfileError.WEIGHT, CarePatientProfileRules.weight("0.5").second)
        assertEquals(ProfileError.WEIGHT, CarePatientProfileRules.weight("301").second)
        assertEquals("62", CarePatientProfileRules.weightText(62.0))
    }
    @Test fun heightRange() {
        assertEquals(30 to null, CarePatientProfileRules.height("30"))
        assertEquals(ProfileError.HEIGHT, CarePatientProfileRules.height("251").second)
        assertEquals(ProfileError.HEIGHT, CarePatientProfileRules.height("29").second)
    }
    @Test fun sharedCodecRoundTripsAndPicksNewestPerPerson() {
        val p = CarePatientProfile(58, "Male", 62.0, 170, "B+", "Carcinoma stomach, stage IV", "Penicillin (rash)", "", "Hyderabad")
        assertTrue(PatientProfileCodec.isStructured(PatientProfileCodec.encode(p)))
        assertEquals(p, PatientProfileCodec.decode(PatientProfileCodec.encode(p)))
        assertNull(PatientProfileCodec.decode("plain journal note"))
        assertNull(PatientProfileCodec.decode(PatientProfileCodec.PREFIX + "{not json"))
        val old = PatientProfileCodec.item("a", p, 1, "old"); val new = PatientProfileCodec.item("a", p.copy(age = 59), 2, "new")
        val other = PatientProfileCodec.item("b", p, 3, "other"); val note = CareItem("n", "a", "journal", "x", "hello", created = 9)
        assertEquals("new", PatientProfileCodec.latest(listOf(old, new, other, note), "a")?.id)
        assertNull(PatientProfileCodec.latest(listOf(note), "a"))
    }
    @Test fun uploadRedactsFreeTextButKeepsPhoneAndStructure() {
        val p = CarePatientProfile(58, "Male", 62.0, 170, "B+", "Cancer", "Penicillin, call 98765 43210", "98765 43210", "Hyderabad")
        val safe = CarePrivacy.entryForUpload(PatientProfileCodec.item("a", p, 1, "i"))
        val decoded = PatientProfileCodec.decode(safe.detail)!!
        assertEquals("98765 43210", decoded.phone); assertFalse(decoded.allergies.contains("98765"))
    }

    private fun d(s: String) = LocalDate.parse(s)

    @Test fun weightHistoryRoundTripsAndOldProfilesReadAsEmpty() {
        val p = CarePatientProfile(58, "Male", 61.5, 170, "B+", weights = listOf(CareWeightEntry(d("2026-10-01"), 63.0), CareWeightEntry(d("2026-11-01"), 61.5)))
        assertEquals(p, CarePatientProfile.decode(p.encode()))
        assertEquals(p, PatientProfileCodec.decode(PatientProfileCodec.encode(p)))
        // A profile saved before the history existed has no "weights" key.
        val old = """{"v":1,"sex":"Male","blood":"B+","illness":"","allergies":"","phone":"","address":"","age":58,"weight":62,"height":170}"""
        assertEquals(CarePatientProfile(58, "Male", 62.0, 170, "B+"), CarePatientProfile.decode(old))
        assertTrue(CarePatientProfile.decode(old).weights.isEmpty())
        // An empty history writes no key, so older apps see exactly what they wrote.
        assertFalse(JSONObject(CarePatientProfile(weightKg = 60.0).encode()).has("weights"))
    }

    @Test fun brokenWeightEntriesAreSkipped() {
        val json = """{"v":1,"weight":60,"weights":[{"day":"2026-10-01","kg":61},{"day":"not a day","kg":60},{"day":"2026-10-02","kg":0},{"day":"2026-10-03"},"x",{"day":"2026-10-04","kg":60}]}"""
        assertEquals(listOf(CareWeightEntry(d("2026-10-01"), 61.0), CareWeightEntry(d("2026-10-04"), 60.0)), CarePatientProfile.decode(json).weights)
    }

    @Test fun recordAddsReplacesSameDayAndKeepsOrder() {
        var h = CareWeightHistory.record(emptyList(), 62.0, d("2026-10-01"))
        h = CareWeightHistory.record(h, 61.0, d("2026-10-15"))
        h = CareWeightHistory.record(h, 60.5, d("2026-10-15"))
        assertEquals(listOf(CareWeightEntry(d("2026-10-01"), 62.0), CareWeightEntry(d("2026-10-15"), 60.5)), h)
        // A back-dated weight lands in date order; null changes nothing.
        h = CareWeightHistory.record(h, 63.0, d("2026-09-20"))
        assertEquals(listOf(d("2026-09-20"), d("2026-10-01"), d("2026-10-15")), h.map { it.day })
        assertEquals(h, CareWeightHistory.record(h, null, d("2026-11-01")))
    }

    @Test fun recordKeepsOnlyTheNewestMax() {
        var h = emptyList<CareWeightEntry>()
        val start = d("2026-01-01")
        repeat(CareWeightHistory.MAX + 5) { h = CareWeightHistory.record(h, 50.0 + it % 7, start.plusDays(it.toLong())) }
        assertEquals(CareWeightHistory.MAX, h.size)
        assertEquals(start.plusDays((CareWeightHistory.MAX + 4).toLong()), h.last().day)
        assertEquals(start.plusDays(5), h.first().day)
    }

    @Test fun saveLogsOnlyANewOrChangedWeight() {
        val day = d("2026-11-04")
        val before = CarePatientProfile(weightKg = 62.0, weights = listOf(CareWeightEntry(d("2026-10-01"), 62.0)))
        assertEquals(before.weights, CareWeightHistory.afterSave(before, 62.0, day))
        assertEquals(before.weights, CareWeightHistory.afterSave(before, null, day))
        assertEquals(listOf(CareWeightEntry(d("2026-10-01"), 62.0), CareWeightEntry(day, 60.0)), CareWeightHistory.afterSave(before, 60.0, day))
        // An old profile with a weight but no history: an unchanged weight is not given today's date.
        assertTrue(CareWeightHistory.afterSave(CarePatientProfile(weightKg = 62.0), 62.0, day).isEmpty())
        assertEquals(listOf(CareWeightEntry(day, 61.0)), CareWeightHistory.afterSave(CarePatientProfile(weightKg = 62.0), 61.0, day))
        assertEquals(listOf(CareWeightEntry(day, 61.0)), CareWeightHistory.afterSave(CarePatientProfile(), 61.0, day))
    }

    @Test fun trendAndChanges() {
        val h = listOf(CareWeightEntry(d("2026-10-15"), 60.5), CareWeightEntry(d("2026-09-01"), 64.0), CareWeightEntry(d("2026-10-01"), 62.0))
        assertEquals(-1.5, CareWeightHistory.change(h)!!, 1e-9)
        assertEquals(-3.5, CareWeightHistory.changeSinceFirst(h)!!, 1e-9)
        assertEquals(CareWeightTrend.DOWN, CareWeightHistory.trend(h))
        assertEquals(CareWeightTrend.UP, CareWeightHistory.trend(h + CareWeightEntry(d("2026-11-01"), 60.8)))
        assertEquals(CareWeightTrend.STEADY, CareWeightHistory.trend(h + CareWeightEntry(d("2026-11-01"), 60.5)))
        assertNull(CareWeightHistory.trend(h.take(1)))
        assertNull(CareWeightHistory.change(emptyList()))
        assertNull(CareWeightHistory.changeSinceFirst(h.take(1)))
        assertEquals("1.5", CareWeightHistory.amountText(-1.5)); assertEquals("2", CareWeightHistory.amountText(2.0)); assertEquals("0.3", CareWeightHistory.amountText(0.1 + 0.2))
        assertEquals(listOf(d("2026-10-01"), d("2026-10-15")), CareWeightHistory.recent(h, 2).map { it.day })
    }

    @Test fun cardDrawsATrendOnlyWhenTheHistoryEndsAtTheCurrentWeight() {
        val h = listOf(CareWeightEntry(d("2026-10-01"), 62.0), CareWeightEntry(d("2026-10-15"), 60.5))
        assertEquals(h, CareWeightHistory.forCard(CarePatientProfile(weightKg = 60.5, weights = h.reversed())))
        // An older app changed the weight without logging it.
        assertTrue(CareWeightHistory.forCard(CarePatientProfile(weightKg = 59.0, weights = h)).isEmpty())
        assertTrue(CareWeightHistory.forCard(CarePatientProfile(weightKg = null, weights = h)).isEmpty())
    }

    @Test fun uploadKeepsTheWeightHistory() {
        val h = listOf(CareWeightEntry(d("2026-10-01"), 62.0))
        val p = CarePatientProfile(58, weightKg = 62.0, illness = "Cancer, call 98765 43210", weights = h)
        val safe = PatientProfileCodec.decode(CarePrivacy.entryForUpload(PatientProfileCodec.item("a", p, 1, "i")).detail)!!
        assertEquals(h, safe.weights)
    }
}
