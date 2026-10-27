package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DoctorConnectedHomeTest {
    private val now = Instant.parse("2026-10-27T06:30:00Z").toEpochMilli()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val asha = Person("1a2b3c4d-0000-4000-8000-000000000001", "Asha", 1)
    private val ravi = Person("9f8e7d6c-0000-4000-8000-000000000002", "Ravi", 2)
    private val people = listOf(asha, ravi)
    private fun item(id: String, person: Person, kind: String, title: String = "A good day", created: Long = now, due: Long = 0, detail: String = "") =
        CareItem(id, person.id, kind, title, detail, due, "", created)

    @Test fun patientCodeIsShortStableAndUppercase() {
        assertEquals("SH-1A2B3C4D", DoctorConnectedHome.patientCode(asha.id))
        assertEquals(DoctorConnectedHome.patientCode(asha.id), DoctorConnectedHome.patientCode(asha.id))
    }

    @Test fun searchFindsByNamePatientIdOrFullIdButOnlyAmongConnectedPeople() {
        assertEquals(listOf(asha), DoctorConnectedHome.search(people, "ash"))
        assertEquals(listOf(asha), DoctorConnectedHome.search(people, "SH-1A2B3C4D"))
        assertEquals(listOf(asha), DoctorConnectedHome.search(people, "sh-1a2b"))
        assertEquals(listOf(asha), DoctorConnectedHome.search(people, " 1a2b3c4d "))
        assertEquals(listOf(ravi), DoctorConnectedHome.search(people, ravi.id.uppercase()))
        assertEquals(people, DoctorConnectedHome.search(people, "  "))
        // Too short or not an ID: never a loose match on the record ID.
        assertTrue(DoctorConnectedHome.search(people, "SH-1A").isEmpty())
        assertTrue(DoctorConnectedHome.search(people, "SH-00000000").isEmpty())
        assertTrue(DoctorConnectedHome.search(emptyList(), "SH-1A2B3C4D").isEmpty())
    }

    @Test fun upcomingVisitsSpanEveryPatientSoonestFirstAndSkipClosedOnes() {
        val visit = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", timed = true))
        val cancelled = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", timed = true, status = "cancelled"))
        val rows = listOf(item("later", asha, "visit", "Home visit", due = now + 2 * 86_400_000, detail = visit),
            item("soon", ravi, "visit", "Review", due = now + 3_600_000, detail = visit),
            item("past", ravi, "visit", "Old", due = now - 3_600_000, detail = visit),
            item("cancelled", asha, "visit", "Cancelled", due = now + 3_600_000, detail = cancelled),
            item("stranger", Person("x", "Not connected", 3), "visit", due = now + 60_000, detail = visit))
        assertEquals(listOf("soon", "later"), DoctorConnectedHome.upcomingVisits(people, rows, now, zone).map { it.item.id })
        assertEquals(listOf(ravi, asha), DoctorConnectedHome.upcomingVisits(people, rows, now, zone).map { it.person })
    }

    @Test fun recentCheckInsKeepTheNewestPerPatientWithinAWeek() {
        val rows = listOf(item("asha-old", asha, "checkin", created = now - 2 * 86_400_000), item("asha-new", asha, "checkin", "A hard day", created = now - 3_600_000),
            item("ravi-stale", ravi, "checkin", created = now - 9 * 86_400_000), item("journal", ravi, "journal", created = now))
        assertEquals(listOf("asha-new"), DoctorConnectedHome.recentCheckIns(people, rows, now).map { it.second.id })
    }

    @Test fun verificationStateIsHonestAboutManualApproval() {
        assertEquals(DoctorConnectedHome.Verification.UNKNOWN, DoctorConnectedHome.verification(null, loaded = false))
        assertEquals(DoctorConnectedHome.Verification.NOT_SUBMITTED, DoctorConnectedHome.verification(null, loaded = true))
        fun profile(status: String) = CareClinicianProfile("Dr Fictional", "Council", "R-1", status, "")
        assertEquals(DoctorConnectedHome.Verification.PENDING, DoctorConnectedHome.verification(profile("pending"), true))
        assertEquals(DoctorConnectedHome.Verification.APPROVED, DoctorConnectedHome.verification(profile("approved"), true))
        assertEquals(DoctorConnectedHome.Verification.SUSPENDED, DoctorConnectedHome.verification(profile("suspended"), true))
        val pending = DoctorConnectedHome.verificationBody(DoctorConnectedHome.Verification.PENDING, hindi = false)
        assertTrue(pending.contains("by hand") && pending.contains("Check again"))
        DoctorConnectedHome.Verification.entries.forEach { state ->
            assertNotEquals(DoctorConnectedHome.verificationTitle(state, false), DoctorConnectedHome.verificationTitle(state, true))
            assertNotEquals(DoctorConnectedHome.verificationBody(state, false), DoctorConnectedHome.verificationBody(state, true))
        }
    }
}
