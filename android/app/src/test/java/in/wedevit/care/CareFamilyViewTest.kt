package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Patient and family views differ: what an invited family member (view role "Family") logs is marked, and the living will is the patient's to change. */
class CareFamilyViewTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 4)
    private val noon = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val medicine = CareItem("00000000-0000-4000-8000-0000000000aa", "person-1", "medicine", "Synthetic medicine", "Oral, twice daily.\nTimes: 08:00, 20:00")

    @Test fun familyNotesDefaultToCaregiverAndOthersKeepTheLastReporter() {
        assertEquals("caregiver" to "", SymptomDiaryRules.defaultReporter("Family", null))
        assertEquals("caregiver" to "", SymptomDiaryRules.defaultReporter("Family", "patient" to "Rahul"))
        assertEquals("caregiver" to "Asha", SymptomDiaryRules.defaultReporter("Family", "caregiver" to "Asha"))
        assertEquals("patient" to "Rahul", SymptomDiaryRules.defaultReporter("Patient", "patient" to "Rahul"))
        assertEquals("" to "", SymptomDiaryRules.defaultReporter("Patient", null))
        assertTrue(SymptomDiaryRules.loggedByFamily(SymptomNote("Pain", 4, "2026-11-04", "10:00", respondent = "caregiver", author = "Asha")))
        assertFalse(SymptomDiaryRules.loggedByFamily(SymptomNote("Pain", 4, "2026-11-04", "10:00", respondent = "patient", author = "Rahul")))
    }

    @Test fun aFamilyTickIsStoredAsAFamilyReport() {
        assertEquals("Family report", MedicineHistoryRules.reporterFor("Family"))
        assertEquals("Patient report", MedicineHistoryRules.reporterFor("Patient"))
        assertEquals("Not recorded", MedicineHistoryRules.reporterFor("Doctor"))
        assertEquals("Family report", MedicineHistoryRules.checkoffReporter("Family"))
        assertEquals("Reporter not recorded", MedicineHistoryRules.checkoffReporter(""))
        assertEquals("Family report", MedicineHistoryRules.missedReport(medicine, "Family", noon, "e1").reporter)
    }

    @Test fun onlyTheDoseAFamilyMemberTickedSaysLoggedByFamily() {
        val doses = MedicineDoses.forItem(medicine)
        val (morning, evening) = doses
        val events = listOf(MedicineDoses.report(medicine, morning, true, day, zone, noon, MedicineDoses.reporter("Family"), id = "e1"),
            MedicineDoses.report(medicine, evening, true, day, zone, noon, MedicineDoses.reporter("Patient"), id = "e2"))
        assertTrue(MedicineDoses.loggedByFamily(medicine, morning, events, day, zone))
        assertFalse(MedicineDoses.loggedByFamily(medicine, evening, events, day, zone))
        assertFalse(MedicineDoses.loggedByFamily(medicine, morning, events, day.plusDays(1), zone))
        assertTrue(MedicineHistoryRules.loggedByFamily(events[0]))
        assertFalse(MedicineHistoryRules.loggedByFamily(events[1]))
    }

    @Test fun livingWillIsReadOnlyForFamilyButNotForThePatientOrDoctor() {
        assertFalse(AmdRules.canChange(canEdit = true, clinician = false, actor = "Family"))
        assertTrue(AmdRules.canChange(canEdit = true, clinician = false, actor = "Patient"))
        assertTrue(AmdRules.canChange(canEdit = true, clinician = true, actor = "Doctor"))
        assertFalse(AmdRules.canChange(canEdit = false, clinician = false, actor = "Patient"))
    }
}
