package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class NoteRulesTest {
    private val person = Person(id = "test-person", name = "Test Person")
    private val draft = Conversation(personId = person.id, source = "What matters: I want to be at home.\nStill to discuss: I am not sure about hospital care.\nNext steps: Ask the doctor on Monday.")
    @Test fun organisingPreservesNegationAndDoesNotInventMissingSections() {
        val result = NoteRules.organise(draft)
        assertEquals("I want to be at home.", result.priorities)
        assertEquals("I am not sure about hospital care.", result.questions)
        assertEquals("", result.participants)
        assertEquals(draft.source, result.source)
    }
    @Test fun unlabelledWordsRemainTogetherWithoutClinicalInference() {
        val source = "I do not want to make this decision yet. The family asked about ventilation."
        val result = NoteRules.organise(draft.copy(source = source))
        assertEquals(source, result.topics)
        assertEquals("", result.priorities)
    }
    @Test fun wrongPatientAndUnreviewedNotesCannotPublish() {
        assertThrows(IllegalArgumentException::class.java) { NoteRules.publish(person, draft.copy(personId = "other"), "Doctor", true, 1) }
        assertThrows(IllegalArgumentException::class.java) { NoteRules.publish(person, NoteRules.organise(draft), "Doctor", false, 1) }
    }
    @Test fun aNewVersionDoesNotAlterAnEarlierReviewedRecord() {
        val original = NoteRules.publish(person, NoteRules.organise(draft), "Doctor", true, 1)
        val revised = NoteRules.publish(person, NoteRules.organise(draft.copy(source = "What matters: I changed my mind.")), "Doctor", true, 2)
        assertEquals("I want to be at home.", original.priorities)
        assertNotEquals(original.contentHash, revised.contentHash)
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(original, revised, "Patient", "Patient", true) }
    }
    @Test fun confirmationBindsExactContentAndRequiresReading() {
        val original = NoteRules.publish(person, NoteRules.organise(draft), "Doctor", true, 1)
        assertThrows(IllegalArgumentException::class.java) { NoteRules.acknowledge(original, original, "Patient", "Patient", false) }
        val ack = NoteRules.acknowledge(original, original, "Patient", "Patient", true)
        assertEquals(original.id, ack.versionId)
        assertEquals(original.contentHash, ack.contentHash)
    }
    @Test fun hashCannotConfuseFieldBoundaries() { assertNotEquals(NoteRules.hash(listOf("ab", "c")), NoteRules.hash(listOf("a", "bc"))) }
}
