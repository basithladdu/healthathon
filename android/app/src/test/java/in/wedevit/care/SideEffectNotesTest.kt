package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SideEffectNotesTest {
    private val now = Instant.parse("2026-10-30T10:00:00Z").toEpochMilli()
    private val hour = 3_600_000L
    private val day = 24 * hour
    private fun note(at: Long = now - hour, effects: List<String> = listOf("constipation"), discussedAt: Long = 0, medicine: String = "m1") =
        SideEffectNote(medicine, "Morphine sulfate 10 mg", effects, "", "moderate", at, false, "Patient", discussedAt)
    private fun entryItem(id: String, n: SideEffectNote, person: String = "p1") = SideEffectRules.item(person, n, n.happenedAt, id)
    private fun doneVisit(id: String, completedAt: Long, status: String = "completed", due: Long = completedAt) =
        CareItem(id, "p1", "visit", "Visit", CareEntryCodec.encode(CareEntry("schedule", category = "Visit", status = status, completedAt = completedAt)), due = due)

    @Test fun codecRoundTripsAndKeepsStructure() {
        val n = note(effects = listOf("other", "nausea")).copy(other = "Itchy palms", stoppedTaking = true, reporter = "Meera (sister)")
        val text = SideEffectCodec.encode(n)
        assertTrue(SideEffectCodec.isStructured(text))
        assertEquals(n.copy(effects = listOf("nausea", "other")), SideEffectCodec.decode(text))
        assertTrue(text.contains("\"kind\":\"side_effect\""))
    }

    @Test fun invalidNotesAreRejectedAndUnreadableTextDecodesToNull() {
        assertNotNull(SideEffectRules.error(note(effects = emptyList()), now))
        assertNull(SideEffectRules.error(note(effects = emptyList()).copy(other = "tingling"), now))
        assertNotNull(SideEffectRules.error(note(effects = listOf("headache")), now))
        assertNotNull(SideEffectRules.error(note().copy(severity = "bad"), now))
        assertNotNull(SideEffectRules.error(note(at = now + day), now))
        assertNotNull(SideEffectRules.error(note().copy(medicineId = ""), now))
        assertNull(SideEffectCodec.decode("plain journal note"))
        assertNull(SideEffectCodec.decode(SideEffectCodec.PREFIX + "{not json"))
        assertNull(SideEffectCodec.decode(SideEffectCodec.PREFIX + "{\"kind\":\"other\"}"))
    }

    @Test fun whenChoicesMapToTimes() {
        val utc = ZoneId.of("UTC")
        assertEquals(now, SideEffectRules.whenAt("now", now, utc))
        assertEquals(now - 3 * hour, SideEffectRules.whenAt("earlier-today", now, utc))
        val early = Instant.parse("2026-10-30T01:00:00Z").toEpochMilli()
        assertEquals(Instant.parse("2026-10-30T00:00:00Z").toEpochMilli(), SideEffectRules.whenAt("earlier-today", early, utc))
        assertEquals(now - day, SideEffectRules.whenAt("yesterday", now, utc))
        assertEquals(now - 2 * day, SideEffectRules.whenAt("two-days", now, utc))
    }

    @Test fun titleAndWordsNeverGiveAdvice() {
        val n = note(effects = listOf("constipation", "other")).copy(other = "hard stools")
        assertEquals("Side effect: Constipation, Other: hard stools · Morphine sulfate 10 mg", SideEffectRules.title(n))
        val item = SideEffectRules.item("p1", n, now, "x")
        assertEquals("journal", item.kind)
        assertTrue(item.title.length <= 200)
    }

    @Test fun entriesAreThisPersonsNewestFirstAndIgnoreOtherJournalNotes() {
        val a = entryItem("a", note(at = now - 5 * hour)); val b = entryItem("b", note(at = now - hour)); val other = entryItem("c", note(), person = "p2")
        val plain = CareItem("d", "p1", "journal", "Slept well", "Slept well")
        val wrongKind = a.copy(id = "e", kind = "checkin")
        assertEquals(listOf("b", "a"), SideEffectLog.entries(listOf(a, plain, b, other, wrongKind), "p1").map { it.item.id })
        val log = SideEffectLog.entries(listOf(a, b), "p1")
        assertEquals(listOf("a", "b").sorted(), SideEffectLog.forMedicine(log, "m1").map { it.item.id }.sorted())
        assertTrue(SideEffectLog.forMedicine(log, "other").isEmpty())
    }

    @Test fun lastVisitIsTheLatestCompletedOrPastVisitOnly() {
        assertEquals(0L, SideEffectLog.lastVisitAt(emptyList(), "p1", now))
        val items = listOf(doneVisit("v1", now - 14 * day), doneVisit("v2", now - 3 * day), doneVisit("cancelled", now - day, status = "cancelled"),
            doneVisit("open", 0, status = "open", due = now + 4 * day), doneVisit("other-person", now - hour).copy(personId = "p2"))
        assertEquals(now - 3 * day, SideEffectLog.lastVisitAt(items, "p1", now))
        val plainPast = CareItem("v3", "p1", "visit", "Clinic", "", due = now - 2 * day)
        val plainFuture = CareItem("v4", "p1", "visit", "Clinic", "", due = now + day)
        assertEquals(now - 2 * day, SideEffectLog.lastVisitAt(items + plainPast + plainFuture, "p1", now))
    }

    @Test fun sinceVisitAndToRaiseSplitOnTheLastVisitAndDiscussed() {
        val last = now - 3 * day
        val before = entryItem("before", note(at = last - day)); val openNew = entryItem("open", note(at = now - hour))
        val doneNew = entryItem("done", note(at = now - 2 * hour, discussedAt = now - hour)); val atVisit = entryItem("same", note(at = last))
        val log = SideEffectLog.entries(listOf(before, openNew, doneNew, atVisit), "p1")
        assertEquals(listOf("open", "done"), SideEffectLog.sinceVisit(log, last).map { it.item.id })
        assertEquals(listOf("open"), SideEffectLog.toRaise(log, last).map { it.item.id })
        assertEquals(4, SideEffectLog.sinceVisit(log, 0L).size)
    }

    @Test fun markDiscussedKeepsEverythingElseAndClearsToRaise() {
        val item = entryItem("o", note())
        val entry = SideEffectLog.entries(listOf(item), "p1").single()
        val changed = SideEffectLog.markDiscussed(entry, "Doctor", now)
        assertEquals(item.id, changed.id); assertEquals(item.title, changed.title); assertEquals(item.revision, changed.revision)
        val decoded = SideEffectCodec.decode(changed.detail)!!
        assertEquals(now, decoded.discussedAt); assertEquals("Doctor", decoded.discussedBy)
        assertEquals(entry.note.copy(discussedAt = now, discussedBy = "Doctor"), decoded)
        assertTrue(SideEffectLog.toRaise(SideEffectLog.entries(listOf(changed), "p1"), 0L).isEmpty())
    }

    @Test fun reporterComesFromTheSignedInRole() {
        assertEquals("Patient", SideEffectRules.reporterFor("Patient")); assertEquals("Family", SideEffectRules.reporterFor("Family")); assertEquals("Care team", SideEffectRules.reporterFor("Doctor"))
    }

    @Test fun uploadRedactsTypedWordsButKeepsStructure() {
        val n = note().copy(other = "Call me on 9876543210 if it gets worse")
        val item = entryItem("p", n)
        val safe = CarePrivacy.entryForUpload(item)
        val decoded = SideEffectCodec.decode(safe.detail)!!
        assertFalse(decoded.other.contains("9876543210"))
        assertFalse(safe.title.contains("9876543210"))
        assertEquals(n.medicineId, decoded.medicineId); assertEquals(n.severity, decoded.severity); assertEquals(n.happenedAt, decoded.happenedAt)
        val clean = entryItem("q", note())
        assertEquals(clean, CarePrivacy.entryForUpload(clean))
    }

    @Test fun rahulsWalkthroughHasOpenAndDiscussedSideEffects() {
        val rahul = DoctorTestProfiles.profiles.first { it.key == "rahul" }; val utc = ZoneId.of("UTC")
        val fixture = DoctorTestProfiles.fixture(rahul, now, utc)
        val log = SideEffectLog.entries(fixture.items, fixture.person.id)
        assertEquals(3, log.size)
        val medicineIds = fixture.items.filter { it.kind == "medicine" }.map { it.id }.toSet()
        assertTrue(log.all { it.note.medicineId in medicineIds })
        assertTrue(log.any { it.note.effects == listOf("constipation") && it.note.medicineName.startsWith("Morphine") })
        val last = SideEffectLog.lastVisitAt(fixture.items, fixture.person.id, now)
        assertEquals(2, SideEffectLog.toRaise(log, last).size)
        assertEquals(1, log.count { it.note.discussedAt > 0 })
        val arjun = DoctorTestProfiles.fixture(DoctorTestProfiles.profiles.first { it.key == "arjun" }, now, utc)
        assertTrue(SideEffectLog.entries(arjun.items, arjun.person.id).isEmpty())
    }
}
