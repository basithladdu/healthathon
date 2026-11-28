package `in`.wedevit.care

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** The shared doctor Home (25 Nov owner override): the ring's numbers, the next visit, the groups and where a stat line scrolls. */
class DoctorHomeScreenTest {
    private val now = Instant.parse("2026-10-27T06:30:00Z").toEpochMilli() // 12:00 in Kolkata
    private val zone = ZoneId.of("Asia/Kolkata")
    private val asha = Person("1a2b3c4d-0000-4000-8000-000000000001", "Asha", 1)
    private val ravi = Person("9f8e7d6c-0000-4000-8000-000000000002", "Ravi", 2)
    private val people = listOf(asha, ravi)
    private fun item(id: String, person: Person, kind: String, title: String = "A good day", created: Long = now, due: Long = 0, detail: String = "") =
        CareItem(id, person.id, kind, title, detail, due, "", created)
    private fun visit(status: String = "open") = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", timed = true, status = status))

    @Test fun ringCountsTodaysVisitsDoneOfAllNotCancelled() {
        val rows = listOf(item("done", asha, "visit", "Morning", due = now - 3 * 3_600_000, detail = visit("completed")),
            item("open", ravi, "visit", "Afternoon", due = now + 3_600_000, detail = visit()),
            item("cancelled", ravi, "visit", "Off", due = now + 2 * 3_600_000, detail = visit("cancelled")),
            item("tomorrow", asha, "visit", "Later", due = now + 86_400_000, detail = visit()),
            item("stranger", Person("x", "Not connected", 3), "visit", due = now, detail = visit()))
        assertEquals(1 to 2, doctorVisitsToday(people, rows, now, zone))
        assertEquals(0 to 0, doctorVisitsToday(emptyList(), rows, now, zone))
    }

    @Test fun nextVisitLeavesTheListsAndStatLinesScrollToTheirGroup() {
        val rows = listOf(item("soon", ravi, "visit", "Review", due = now + 3_600_000, detail = visit()),
            item("today2", asha, "visit", "Dressing", due = now + 4 * 3_600_000, detail = visit()),
            item("later", asha, "visit", "Home visit", due = now + 2 * 86_400_000, detail = visit()),
            item("hard", ravi, "checkin", "A hard day", created = now - 2 * 86_400_000))
        val banner = DoctorHomeBanner("b", "Verify your registration", null, Icons.Default.Info)
        val state = doctorConnectedHomeState(people, rows, now, false, listOf(banner), {}, {}, {}, {}, zone)
        assertEquals("Ravi", state.next?.name)
        val today = state.groups.first { it.key == "today" }
        assertEquals(listOf("home-today-today2"), today.rows.map { it.tag })
        assertEquals(listOf("home-visit-later"), state.groups.first { it.key == "coming" }.rows.map { it.tag })
        val checks = state.groups.first { it.key == "checks" }
        assertTrue(checks.rows.single().alert)
        assertEquals(1, state.stats.first { it.tag == "doctor-home-tile-checkins" }.count)
        assertEquals(0 to 2, state.visitsDone to state.visitsTotal)
        // Banners (1 item) + ring (1) + next (1), then today, coming, checks.
        assertEquals(3, doctorHomeGroupIndex(state, "today"))
        assertEquals(5, doctorHomeGroupIndex(state, "checks"))
        assertEquals(-1, doctorHomeGroupIndex(state, "missing"))
    }

    @Test fun emptyComingUpIsNotDrawnSoItsStatFallsBackToItsAction() {
        var opened = false
        val state = doctorConnectedHomeState(people, emptyList(), now, false, emptyList(), {}, {}, {}, { opened = true }, zone)
        assertNull(state.next)
        assertEquals(listOf("today", "checks"), doctorHomeShownGroups(state).map { it.key })
        assertEquals(-1, doctorHomeGroupIndex(state, "coming"))
        assertEquals(1, doctorHomeGroupIndex(state, "today"))
        assertEquals("Nothing due today", state.groups.first { it.key == "today" }.empty)
        state.stats.first { it.tag == "doctor-home-tile-upcoming" }.onClick()
        assertTrue(opened)
    }

    @Test fun noPatientsShowsOneLineThatLeadsToPatients() {
        var patients = false
        val state = doctorConnectedHomeState(emptyList(), emptyList(), now, false, emptyList(), {}, {}, { patients = true }, {}, zone)
        val only = doctorHomeShownGroups(state).single()
        assertEquals("No patients yet", only.empty)
        only.onEmptyAction?.invoke()
        assertTrue(patients)
    }
}
