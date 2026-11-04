package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The doctor's Goals of care list and the legal-form layout (3 Nov meeting). Sample data only. */
class DoctorGocFormTest {
    private fun plan(id: String, patient: String, version: Int, talk: GocTranscript?, updated: String = "2026-11-01T10:00:00Z") =
        EctprRecord(id = id, patientId = patient, version = version, createdAt = updated, updatedAt = updated, patient = EctprPatient(name = "Sample patient"), gocTranscript = talk)

    @Test fun listsOnlyThisPersonsOwnTalksNewestFirst() {
        val signed = GocSignOffSummary("s1", "abc", "2026-11-03T09:00:00Z", listOf("Dr Sample"))
        val list = DoctorGocDiscussions.list(listOf(
            plan("old", "p1", 1, GocTranscript(planVersion = 1, note = "First talk", reviewedAt = "2026-10-20T08:00:00Z")),
            plan("new", "p1", 2, GocTranscript(planVersion = 2, note = "Second talk", signOff = signed)),
            plan("carried", "p1", 3, GocTranscript(planVersion = 2, note = "Copied from version 2")),
            plan("empty", "p1", 4, GocTranscript(planVersion = 4)),
            plan("other", "p2", 1, GocTranscript(planVersion = 1, note = "Someone else")),
            plan("none", "p1", 5, null),
        ), "p1")
        assertEquals(listOf("new", "old"), list.map { it.id })
        assertTrue(list[0].signed); assertFalse(list[1].signed)
        assertEquals("2026-11-03T09:00:00Z", list[0].at)
        assertEquals("2026-10-20T08:00:00Z", list[1].at)
    }

    @Test fun everyDiscussionUsesTheFormsFourSections() {
        val d = DoctorGocDiscussions.list(listOf(plan("a", "p1", 1, GocTranscript(planVersion = 1, note = "Talk"))), "p1").single()
        assertEquals(listOf("I", "II", "III", "IV"), d.sections().map { it.id })
        assertEquals(GocForm.sections(d.record, d.topics).map { it.rows }, d.sections().map { it.rows })
    }

    @Test fun aConversationNoteFillsOnlyWhatItRecorded() {
        val note = CareVersion(personId = "p1", version = 3, personName = "Sample patient", source = "", priorities = "To be at home",
            participants = "Sample patient, Sample son", topics = "Pain at night", questions = "", nextSteps = "Review in a week", doctor = "Dr Sample", published = 0, contentHash = "x")
        val d = DoctorGocDiscussions.fromNote(note, hindi = false)
        val rows = d.sections().associate { it.id to it.rows.map { r -> r.pick(false) } }
        assertTrue(rows.getValue("I").any { it.contains("Sample patient; Sample son") })
        assertTrue(rows.getValue("II").any { it.contains("To be at home") })
        assertTrue(rows.getValue("I").any { it.endsWith("Not recorded") })
        assertTrue(d.note.contains("Pain at night") && d.note.contains("Review in a week"))
        assertFalse(d.signed)
    }

    @Test fun pdfLinesFollowTheSectionsThenTheNote() {
        val d = DoctorGocDiscussions.list(listOf(plan("a", "p1", 1, GocTranscript(planVersion = 1, note = "Doctor wrote this"))), "p1").single()
        val lines = DoctorGocDiscussions.pdfLines(d, hindi = false, status = "Not signed")
        val headings = lines.filter { it.second == 1 }.map { it.first }
        assertEquals(d.sections().map { it.title.en } + "Doctor's note", headings)
        assertEquals("Doctor wrote this", lines.last().first)
        assertTrue(lines[1].first.contains("Not signed"))
    }
}
