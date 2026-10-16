package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CareAcknowledgementTest {
    private val person = Person(id = "person", name = "Patient")
    private val note = NoteRules.publish(person, Conversation(person.id, source = "I want to be home.", priorities = "I want to be home."), "Doctor", true, 1)

    @Test fun patientMustConfirmBeforeSeparateFamilyConfirmations() {
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(note, note, "Family", "Family / carer", true) }
        val patient = NoteRules.acknowledge(note, note, "Patient", "Patient", true)
        assertEquals(note.id, patient.id)
        val family = NoteRules.acknowledge(note, note, "Family one", "Family / carer", true, listOf(patient))
        val another = NoteRules.acknowledge(note, note, "Family two", "Family / carer", true, listOf(patient, family))
        assertEquals(setOf(note.id), listOf(patient, family, another).map { it.versionId }.toSet())
        assertEquals(3, listOf(patient, family, another).map { it.id }.toSet().size)
        assertEquals(note.contentHash, family.contentHash)
    }

    @Test fun repeatConfirmationReturnsOriginalAndCannotReplacePatient() {
        val patient = NoteRules.acknowledge(note, note, "Patient", "Patient", true)
        assertEquals(patient, NoteRules.acknowledge(note, note, " patient ", "Patient", true, listOf(patient)))
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(note, note, "Someone else", "Patient", true, listOf(patient)) }
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(note, note, "x".repeat(101), "Patient", true) }
    }

    @Test fun changedVersionOrHashDoesNotInheritPreviousPatientConfirmation() {
        val patient = NoteRules.acknowledge(note, note, "Patient", "Patient", true)
        val revised = note.copy(id = newId(), version = 2, contentHash = "b".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(revised, revised, "Family", "Family / carer", true, listOf(patient)) }
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(note.copy(contentHash = "c".repeat(64)), note.copy(contentHash = "c".repeat(64)), "Family", "Family / carer", true, listOf(patient)) }
    }

    @Test fun legacyFamilyRowIsPreservedWithoutPretendingPatientConsent() {
        val legacyFamily = Acknowledgement(note.id, note.contentHash, "Existing family", "Family / carer", 10, note.id)
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(note, note, "Another family", "Family / carer", true, listOf(legacyFamily)) }
        val patient = NoteRules.acknowledge(note, note, "Patient", "Patient", true, listOf(legacyFamily))
        assertNotEquals(legacyFamily.id, patient.id)
        assertEquals(note.id, patient.versionId)
        assertEquals("Family / carer", legacyFamily.relationship)
    }

    @Test fun decodeAcceptsLegacyAndSeparateRowsAndExportIncludesMatchingPeopleOnly() {
        fun row(id: String, name: String, relationship: String, version: String? = null) = JSONObject().put("id", id).put("data", JSONObject().put("contentHash", note.contentHash).put("name", name).put("relationship", relationship).put("at", 10).also { if (version != null) it.put("versionId", version) })
        val patient = decodeAcknowledgement(row(note.id, "Patient", "Patient"))
        val family = decodeAcknowledgement(row("family-id", "Family", "Family / carer", note.id))
        assertEquals(note.id, patient.versionId)
        assertEquals(note.id, patient.id)
        assertEquals(note.id, family.versionId)
        assertEquals("family-id", family.id)
        val exported = NoteRules.text(note, listOf(family, patient, family.copy(id = "other", name = "Wrong version", versionId = "other-note")))
        assertTrue(exported.contains("Patient (Patient)"))
        assertTrue(exported.contains("Family (Family / carer)"))
        assertFalse(exported.contains("Wrong version"))
        assertTrue(exported.indexOf("Patient (Patient)") < exported.indexOf("Family (Family / carer)"))
    }
}
