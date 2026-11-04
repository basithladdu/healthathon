package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CareVisitRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: String, hour: Int = 10) = LocalDate.parse(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun specialityComesOnlyFromWholeWordsAlreadyTyped() {
        assertEquals(CareVisitSpeciality.ONCOLOGY, CareVisitRules.speciality("Dr Meera, Oncology OPD"))
        assertEquals(CareVisitSpeciality.PALLIATIVE, CareVisitRules.speciality("Pain and palliative clinic"))
        assertEquals(CareVisitSpeciality.RADIOTHERAPY, CareVisitRules.speciality("Radiation oncology review"))
        assertEquals(CareVisitSpeciality.HOME_CARE, CareVisitRules.speciality("Home visit by nurse"))
        assertEquals(CareVisitSpeciality.ONCOLOGY, CareVisitRules.speciality("कैंसर अस्पताल"))
        assertNull(CareVisitRules.speciality("Dr Rao", "City hospital"))
        // "gp" inside another word is not a match.
        assertNull(CareVisitRules.speciality("Gpointe clinic"))
    }

    @Test fun familyVisitUsesTitleAsDoctorAndDoctorVisitUsesSender() {
        val family = CareItem("v1", "p", "visit", "Dr Meera (palliative)", "Ask about sleep", due = at("2026-11-04"))
        val f = CareVisitRules.info(family)
        assertEquals("Dr Meera (palliative)", f.doctor); assertEquals(CareVisitSpeciality.PALLIATIVE, f.speciality); assertEquals("Ask about sleep", f.note)
        assertFalse(f.structured); assertEquals("", f.status)
        assertEquals("completed", CareVisitRules.info(family.copy(doneOn = "2026-11-04")).status)

        val planned = CareItem("v2", "p", "visit", "Review", CareEntryCodec.encode(CareEntry("schedule", category = "Visit", sender = "Dr Sujay", location = "Oncology block", status = "cancelled")), due = at("2026-11-10"))
        val d = CareVisitRules.info(planned)
        assertEquals("Dr Sujay", d.doctor); assertEquals("Oncology block", d.place); assertEquals(CareVisitSpeciality.ONCOLOGY, d.speciality)
        assertTrue(d.structured); assertEquals("cancelled", d.status)
        assertEquals("Review", CareVisitRules.info(planned.copy(detail = CareEntryCodec.encode(CareEntry("schedule", category = "Visit")))).doctor)
    }

    @Test fun dayPrescriptionMatchesTheVisitDayAndPerson() {
        val visit = CareItem("v", "p", "visit", "Dr Meera", due = at("2026-11-04", 9))
        val sameDay = CareItem("m1", "p", "medicine", "Morphine", "5 mg as prescribed", created = at("2026-11-04", 18))
        val dayBefore = CareItem("m2", "p", "medicine", "Paracetamol", created = at("2026-11-03"))
        val other = CareItem("m3", "q", "medicine", "Ondansetron", created = at("2026-11-04"))
        val task = CareItem("t", "p", "task", "Buy medicines", created = at("2026-11-04"))
        val rx = CareReport("r1", "p", "Prescription.jpg", "image/jpeg", "a.jpg", 1, created = at("2026-11-04", 11))
        val bill = CareReport("r2", "p", "Bill.pdf", "application/pdf", "b.pdf", 1, created = at("2026-11-04", 12))
        val folder = { r: CareReport -> if (r.id == "r1") DocumentFolderRules.PRESCRIPTIONS else DocumentFolderRules.BILLS }
        val result = CareVisitRules.dayPrescription(visit, listOf(sameDay, dayBefore, other, task), listOf(rx, bill, rx), folder, zone)
        assertEquals(listOf("m1"), result.medicines.map { it.id })
        assertEquals(listOf("r1"), result.documents.map { it.id })
        assertFalse(result.isEmpty)
        assertTrue(CareVisitRules.dayPrescription(visit.copy(due = 0), listOf(sameDay), listOf(rx), folder, zone).isEmpty)
    }

    @Test fun todoPutsOpenTasksFirstBySoonestDue() {
        val a = CareItem("a", "p", "task", "Later", due = at("2026-11-20"))
        val b = CareItem("b", "p", "task", "Soon", due = at("2026-11-05"))
        val c = CareItem("c", "p", "task", "No date")
        val d1 = CareItem("d1", "p", "task", "Done earlier", doneOn = "2026-11-01")
        val d2 = CareItem("d2", "p", "task", "Done today", doneOn = "2026-11-04")
        val (open, done) = CareTodoRules.split(listOf(a, d1, c, b, d2))
        assertEquals(listOf("b", "a", "c"), open.map { it.id })
        assertEquals(listOf("d2", "d1"), done.map { it.id })
        assertTrue(CareTodoRules.overdue(b, LocalDate.parse("2026-11-06"), zone))
        assertFalse(CareTodoRules.overdue(b, LocalDate.parse("2026-11-05"), zone))
        assertFalse(CareTodoRules.overdue(d1.copy(due = at("2026-10-01")), LocalDate.parse("2026-11-06"), zone))
        assertFalse(CareTodoRules.overdue(c, LocalDate.parse("2026-11-06"), zone))
    }
}
