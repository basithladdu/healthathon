package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorVisitsRulesTest {
    @Test fun linkedSourcesRejectWrongPersonStructuredPayloadAndRemovedAccess() {
        val conditionId = "00000000-0000-4000-8000-000000000001"
        val noteId = "00000000-0000-4000-8000-000000000002"
        val visit = DoctorVisitRules.item("visit", "p", "Doctor", 1, "", DoctorVisitDetails.encode(conditionId, noteId, ""))
        val condition = CareItem(conditionId, "p", "checkin", "An okay day", "Protected symptoms")
        val note = CareVersion(id = noteId, personId = "p", version = 1, personName = "", source = "", priorities = "", participants = "", topics = "Protected discussion", questions = "", nextSteps = "", doctor = "", contentHash = "fixture")
        assertEquals(condition, doctorVisitSources(visit, listOf(condition), listOf(note)).condition)
        assertEquals(note, doctorVisitSources(visit, listOf(condition), listOf(note)).discussion)
        assertNull(doctorVisitSources(visit, listOf(condition.copy(personId = "other")), listOf(note.copy(personId = "other"))).condition)
        assertNull(doctorVisitSources(visit, listOf(condition.copy(detail = CareEntryCodec.encode(CareEntry("schedule", category = "Test")))), emptyList()).condition)
        assertNull(doctorVisitSources(visit, emptyList(), emptyList()).discussion)
    }
    @Test fun untimedTodayIsUpcomingAndFutureOrUnknownDoctorIsNotLastSeen() {
        val now = java.time.Instant.parse("2026-10-23T12:00:00Z").toEpochMilli()
        val zone = java.time.ZoneId.of("UTC")
        val midnight = java.time.Instant.parse("2026-10-23T00:00:00Z").toEpochMilli()
        val today = visit("today", "Dr One", "open", midnight).let { it.copy(detail = CareEntryCodec.encode(CareEntryCodec.decode(it.detail)!!.copy(timed = false))) }
        assertEquals("Upcoming", doctorVisitState(today, now, zone))
        val future = visit("future", "Dr One", "completed", now + 1)
        val unnamed = today.copy(id = "unknown", detail = CareEntryCodec.encode(CareEntry("schedule", category = "Visit", status = "completed")))
        assertTrue(doctorLastCompletedByDoctor(listOf(future, unnamed), now).isEmpty())
        assertEquals(2, doctorVisitsFor(listOf(future, unnamed), "p").size)
    }
    @Test fun actualCompletionTimeTakesPriorityOverPlannedTime() {
        val first = visit("first", "Dr One", "completed", 1).let { it.copy(detail = CareEntryCodec.encode(CareEntryCodec.decode(it.detail)!!.copy(completedAt = 9))) }
        val second = visit("second", "Dr One", "completed", 8)
        assertEquals("first", doctorLastCompletedByDoctor(listOf(first, second), 10).single().id)
        assertEquals(9L, doctorCompletedDate(first, 10))
    }
    private fun visit(id: String, doctor: String, status: String, due: Long) = DoctorVisitRules.item(id, "p", doctor, due, "Clinic", "")
        .let { it.copy(detail = CareEntryCodec.encode(CareEntryCodec.decode(it.detail)!!.copy(status = status))) }
    @Test fun pastDateNeverCountsAsCompletedAndDoctorsRemainSeparate() {
        val rows = listOf(visit("past", "Dr One", "open", 1), visit("one", "Dr One", "completed", 2), visit("two", "Dr Two", "completed", 3), visit("cancelled", "Dr Two", "cancelled", 4))
        assertEquals("Past · completion not recorded", doctorVisitState(rows[0], 10))
        assertFalse(doctorVisitCompleted(rows[0]))
        assertEquals(setOf("one", "two"), doctorLastCompletedByDoctor(rows).map { it.id }.toSet())
        assertEquals("Cancelled", doctorVisitState(rows[3], 10))
    }
    @Test fun summaryStoresReferencesNotClinicalTextAndLegacyNotesStayNotes() {
        val condition = "00000000-0000-4000-8000-000000000001"
        val discussion = "00000000-0000-4000-8000-000000000002"
        val encoded = DoctorVisitDetails.encode(condition, discussion, "Bring records")
        val decoded = DoctorVisitDetails.decode(encoded)
        assertEquals(condition, decoded.conditionRef); assertEquals(discussion, decoded.discussionRef)
        assertEquals("Bring records", DoctorVisitDetails.readable(encoded))
        assertEquals("Old visit note", DoctorVisitDetails.decode("Old visit note").note)
        assertTrue(DoctorVisitDetails.decode("Old visit note").conditionRef.isBlank())
    }
    @Test(expected = IllegalArgumentException::class) fun cannotPutClinicalTextInReferenceField() {
        DoctorVisitDetails.encode("patient symptoms", "", "")
    }
    @Test fun visitsFilterNeverIncludesAnotherPersonOrTests() {
        val own = visit("one", "Dr One", "open", 1)
        val other = own.copy(id = "other", personId = "other")
        val test = own.copy(id = "test", detail = CareEntryCodec.encode(CareEntry("schedule", category = "Test")))
        assertEquals(listOf("one"), doctorVisitsFor(listOf(own, other, test), "p").map { it.id })
    }
}
