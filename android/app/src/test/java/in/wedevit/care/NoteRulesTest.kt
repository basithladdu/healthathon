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
    @Test fun aPublishedDemoNoteCannotClaimVerifiedClinicalReview() {
        val note = NoteRules.publish(person, NoteRules.organise(draft), "Doctor", true, 1)
        assertEquals(ReviewWorkflow.MANUAL_DRAFT, note.draftOrigin)
        assertEquals(ReviewWorkflow.REVIEWED_UNVERIFIED, note.reviewStatus)
        assertEquals(ReviewWorkflow.NOT_VERIFIED, note.reviewerVerification)
        assertTrue(ReviewWorkflow.label(note).contains("not verified"))
    }
    @Test fun recordingConsentNamesEveryParticipantAndRequiresEachAgreement() {
        val participants = "Patient, Mother\nDr Rao, patient"
        assertEquals(listOf("Patient", "Mother", "Dr Rao"), RecordingConsentRules.names(participants))
        assertThrows(IllegalArgumentException::class.java) {
            RecordingConsentRules.create(person.id, "/recording.m4a", participants, setOf("Patient", "Mother"), true, true, true, now = 123L)
        }
        val consent = RecordingConsentRules.create(person.id, "/recording.m4a", participants, setOf("Patient", "Mother", "Dr Rao"), true, true, true, now = 123L)
        assertEquals("Patient\nMother\nDr Rao", consent.participants)
        assertEquals(123L, consent.recordingConsentAt)
        assertEquals(0L, consent.transcriptionConsentAt)
    }
    @Test fun recordingAndFutureTranscriptionConsentAreSeparate() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordingConsentRules.create(person.id, "/recording.m4a", "Patient", setOf("Patient"), true, false, true)
        }
        val consent = RecordingConsentRules.create(
            person.id, "/recording.m4a", "Patient", setOf("Patient"),
            purposeAccepted = true, storageAccepted = true, accessAccepted = true,
            transcriptionAccepted = true, now = 456L,
        )
        assertEquals(456L, consent.recordingConsentAt)
        assertEquals(456L, consent.transcriptionConsentAt)
    }
    @Test fun transcribedDraftKeepsItsProvenanceWhenPublished() {
        val transcribed = NoteRules.organise(draft.copy(
            transcriptProvider = "google-gemini",
            transcriptModel = "gemini-3.5-transcribe",
            transcribedAt = 456L,
            transcriptAudioPath = "/recording.m4a",
        ))
        val note = NoteRules.publish(person, transcribed, "Doctor", true, 1)
        assertEquals(ReviewWorkflow.TRANSCRIBED_DRAFT, note.draftOrigin)
        assertEquals("gemini-3.5-transcribe", note.transcriptionModel)
        assertEquals(456L, note.transcribedAt)
        assertTrue(NoteRules.text(note).contains("AI-generated"))
    }
    @Test fun transcriptResponseMustCarryTrustedProvenance() {
        val transcript = sanitizeConversationTranscript("""{"transcript":"[00:01] spk_1: Hello","provider":"google-gemini","model":"gemini-3.5-transcribe","languages":["en-IN"]}""")
        assertEquals("[00:01] spk_1: Hello", transcript.text)
        assertEquals(listOf("en-IN"), transcript.languages)
        assertThrows(ConversationTranscriptionException::class.java) {
            sanitizeConversationTranscript("""{"transcript":"Hello","provider":"unknown","model":"model"}""")
        }
    }
    @Test fun hashCannotConfuseFieldBoundaries() { assertNotEquals(NoteRules.hash(listOf("ab", "c")), NoteRules.hash(listOf("a", "bc"))) }
    @Test fun familyConfirmationsRequirePatientAndRemainDistinctAndIdempotent() {
        val note=NoteRules.publish(person,NoteRules.organise(draft),"Reviewer",true,1)
        assertThrows(IllegalArgumentException::class.java) {NoteRules.acknowledge(note,note,"Family one","Family / carer",true)}
        val patient=NoteRules.acknowledge(note,note,"Patient","Patient",true)
        val first=NoteRules.acknowledge(note,note,"Family one","Family / carer",true,listOf(patient))
        val second=NoteRules.acknowledge(note,note,"Family two","Family / carer",true,listOf(patient,first))
        assertEquals(3,setOf(patient.id,first.id,second.id).size)
        assertEquals(first,NoteRules.acknowledge(note,note," family ONE ","Family / carer",true,listOf(patient,first,second)))
        assertThrows(IllegalArgumentException::class.java) {NoteRules.acknowledge(note,note,"Another patient","Patient",true,listOf(patient))}
        assertThrows(IllegalArgumentException::class.java) {NoteRules.acknowledge(note,note,"x".repeat(101),"Family / carer",true,listOf(patient))}
    }
    @Test fun legacyPublishedHashIsOpaqueAndStaleConfirmationsNeverCarryForward() {
        val note=NoteRules.publish(person,NoteRules.organise(draft),"Reviewer",true,1).copy(contentHash="legacy-stored-hash")
        val patient=NoteRules.acknowledge(note,note,"Patient","Patient",true)
        assertEquals("legacy-stored-hash",patient.contentHash)
        val newer=note.copy(id="new-note",version=2,contentHash="new-hash")
        assertThrows(IllegalArgumentException::class.java) {NoteRules.acknowledge(newer,newer,"Family","Family / carer",true,listOf(patient))}
        assertThrows(IllegalArgumentException::class.java) {NoteRules.acknowledge(note,note.copy(contentHash="tampered"),"Patient","Patient",true)}
        assertFalse(NoteRules.text(newer,listOf(patient)).contains("Read and confirmed by"))
        assertEquals("legacy-stored-hash",note.contentHash)
    }
    @Test fun exportsKeepAllExactVersionReadersAndHonestTranscriptionReview() {
        val note=NoteRules.publish(person,NoteRules.organise(draft.copy(transcriptModel="fixture-model",transcribedAt=42)),"Reviewer",true,1)
        val patient=NoteRules.acknowledge(note,note,"Patient","Patient",true)
        val family=NoteRules.acknowledge(note,note,"Family","Family / carer",true,listOf(patient))
        val text=NoteRules.text(note,listOf(family,patient))
        assertTrue(text.indexOf("by Patient")<text.indexOf("by Family"))
        assertTrue(text.contains("identity not verified"));assertTrue(text.contains("AI-generated"))
        assertEquals(ReviewWorkflow.NOT_VERIFIED,note.reviewerVerification)
    }
    @Test fun importedVerificationStringsNeverBecomeTrustedClinicalIdentity() {
        val original=NoteRules.publish(person,NoteRules.organise(draft),"Reviewer",true,1)
        listOf("","unknown","verified","hospital_approved",ReviewWorkflow.NOT_VERIFIED).forEach {claim ->
            val imported=original.copy(reviewStatus=ReviewWorkflow.CLINICIAN_VERIFIED,reviewerVerification=claim)
            assertEquals("Imported review · identity not verified",ReviewWorkflow.label(imported))
            assertFalse(NoteRules.text(imported).contains("Verified clinician review"))
            assertEquals(claim,imported.reviewerVerification)
            assertEquals(original.contentHash,imported.contentHash)
        }
        assertEquals("Review recorded · identity not verified",ReviewWorkflow.label(original))
        assertEquals("Not clinically reviewed",ReviewWorkflow.label(original.copy(reviewStatus="unknown")))
    }
}
