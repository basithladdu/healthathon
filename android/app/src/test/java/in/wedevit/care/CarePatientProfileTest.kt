package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

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
}
