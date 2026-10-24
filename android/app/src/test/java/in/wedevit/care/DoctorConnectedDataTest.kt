package `in`.wedevit.care

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DoctorConnectedDataTest {
    @Test fun richDraftRoundTripPreservesEveryExistingField() {
        val patientId = UUID.randomUUID().toString()
        val original = WorkflowDraft(
            patientId = patientId, priorities = "Synthetic priority\nPreserve newlines and हिंदी.",
            participants = "Synthetic patient, relative and clinician", topics = "Synthetic discussion",
            questions = "What remains to ask?", undecided = "No preference inferred", nextSteps = "Review the exact source",
            sourceText = "Patient: \"Keep this exact wording.\"\nFamily: synthetic only.",
            sourceIds = listOf("source-one", "source-two"),
            checklist = listOf(WorkflowTopic("Discussed", WorkflowTopicStatus.DISCUSSED), WorkflowTopic("Deferred", WorkflowTopicStatus.DEFERRED), WorkflowTopic("Open", WorkflowTopicStatus.NOT_DISCUSSED)),
            updatedAt = 1_792_700_001_123L, mockGenerated = true,
            transcriptProvider = "synthetic-offline", transcriptModel = "fixture-model", transcribedAt = 1_792_700_000_999L,
            audioClipId = "synthetic-private-local-clip", aiProvider = "fixture-only", aiModel = "fixture-draft-model",
            aiWarnings = listOf("Do not infer agreement", "Missing participant confirmation"),
            evidence = listOf(DoctorAiEvidence("priorities", "Keep this exact wording."), DoctorAiEvidence("questions", "synthetic only")))
        val payload = encodeConnectedDoctorDraft(original)
        assertEquals(1, payload.getInt("schemaVersion"))
        assertTrue(payload.getBoolean("synthetic"))
        assertEquals(original, decodeConnectedDoctorDraft(patientId, JSONObject(payload.toString())))
    }

    @Test fun emptyDraftRetainsItsDefaultsWithoutManufacturingContent() {
        val original = WorkflowDraft(UUID.randomUUID().toString())
        assertEquals(original, decodeConnectedDoctorDraft(original.patientId, encodeConnectedDoctorDraft(original)))
    }

    @Test fun serverIdentityComesFromSelectedPatientNotUntrustedPayload() {
        val original = WorkflowDraft(UUID.randomUUID().toString(), sourceText = "Synthetic words")
        val selected = UUID.randomUUID().toString()
        val payload = encodeConnectedDoctorDraft(original)
        assertThrows(IllegalArgumentException::class.java) { decodeConnectedDoctorDraft(selected, JSONObject(payload.toString()).put("patientId", "demo-asha")) }
        val decoded = decodeConnectedDoctorDraft(selected, payload)
        assertEquals(selected, decoded.patientId)
        assertNotEquals("demo-asha", decoded.patientId)
        assertEquals(original.sourceText, decoded.sourceText)
    }

    @Test fun unsupportedVersionAndNonSyntheticDraftFailRatherThanSilentlyDiscardingData() {
        val original = WorkflowDraft(UUID.randomUUID().toString(), priorities = "Retain original")
        val payload = encodeConnectedDoctorDraft(original)
        val before = payload.toString()
        assertThrows(IllegalArgumentException::class.java) { decodeConnectedDoctorDraft(original.patientId, JSONObject(before).put("schemaVersion", 99)) }
        assertThrows(IllegalArgumentException::class.java) { decodeConnectedDoctorDraft(original.patientId, JSONObject(before).put("synthetic", false)) }
        assertEquals(before, payload.toString())
        assertEquals(original, decodeConnectedDoctorDraft(original.patientId, payload))
    }

    @Test fun incompletePayloadFailsInsteadOfInventingMissingFields() {
        val original = WorkflowDraft(UUID.randomUUID().toString())
        val payload = encodeConnectedDoctorDraft(original)
        payload.remove("sourceText")
        assertThrows(Exception::class.java) { decodeConnectedDoctorDraft(original.patientId, payload) }
    }

    @Test fun unknownFutureFieldsAreRejectedRatherThanSilentlyDropped() {
        val original = WorkflowDraft(UUID.randomUUID().toString(), sourceText = "Keep this source")
        val payload = encodeConnectedDoctorDraft(original).put("futureConsentEvidence", JSONObject().put("keep", "synthetic evidence"))
        val before = payload.toString()
        assertThrows(IllegalArgumentException::class.java) { decodeConnectedDoctorDraft(original.patientId, payload) }
        assertEquals(before, payload.toString())
        assertEquals("synthetic evidence", payload.getJSONObject("futureConsentEvidence").getString("keep"))
    }

    @Test fun unknownNestedChecklistAndEvidenceFieldsAlsoFailSafely() {
        val original = WorkflowDraft(UUID.randomUUID().toString(), evidence = listOf(DoctorAiEvidence("questions", "Synthetic quote")))
        val checklist = encodeConnectedDoctorDraft(original)
        checklist.getJSONArray("checklist").getJSONObject(0).put("futureField", "retain")
        assertThrows(IllegalArgumentException::class.java) { decodeConnectedDoctorDraft(original.patientId, checklist) }
        assertEquals("retain", checklist.getJSONArray("checklist").getJSONObject(0).getString("futureField"))
        val evidence = encodeConnectedDoctorDraft(original)
        evidence.getJSONArray("evidence").getJSONObject(0).put("futureTimestamp", 123)
        assertThrows(IllegalArgumentException::class.java) { decodeConnectedDoctorDraft(original.patientId, evidence) }
        assertEquals(123, evidence.getJSONArray("evidence").getJSONObject(0).getInt("futureTimestamp"))
    }

    @Test fun doseTimesRemainIndependentAndSkippedDoesNotInventTakenTime() {
        val patientId = UUID.randomUUID().toString()
        val taken = ConnectedDoctorDose(UUID.randomUUID().toString(), patientId, "Synthetic medicine", 1_792_700_000_000L, 1_792_700_060_000L, 1_792_703_000_000L, UUID.randomUUID().toString(), "taken")
        assertNotEquals(taken.plannedAt, taken.reportedTakenAt)
        assertNotEquals(taken.reportedTakenAt, taken.loggedAt)
        val skipped = taken.copy(id = UUID.randomUUID().toString(), reportedTakenAt = null, outcome = "skipped")
        assertNull(skipped.reportedTakenAt)
        assertEquals(taken.plannedAt, skipped.plannedAt)
        assertEquals(taken.loggedAt, skipped.loggedAt)
    }

    @Test fun connectedIdentitiesMustBeCanonicalUuidsNotLocalFixtureOrPaths() {
        val valid = UUID.randomUUID().toString()
        assertEquals(valid, requireConnectedDoctorId(valid))
        listOf("demo-asha", "", "../patient", "1-1-1-1-1", valid.uppercase()).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { requireConnectedDoctorId(invalid) }
        }
    }

    @Test fun draftLimitsRejectOversizedTextWithoutTruncatingOriginal() {
        val original = WorkflowDraft(UUID.randomUUID().toString(), sourceText = "x".repeat(60000), priorities = "y".repeat(4000))
        assertEquals(original.sourceText, validateConnectedDoctorDraft(original).getString("sourceText"))
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDraft(original.copy(sourceText = original.sourceText + "x")) }
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDraft(original.copy(priorities = original.priorities + "y")) }
        assertEquals(60000, original.sourceText.length)
        assertEquals(4000, original.priorities.length)
        val heavy = original.copy(aiWarnings = listOf("क".repeat(100000)))
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDraft(heavy) }
        assertEquals(100000, heavy.aiWarnings.single().length)
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDraft(original.copy(patientId = "demo-asha")) }
    }

    @Test fun doseValidationDoesNotAcceptFutureTakenMissingTakenOrTakenOnSkipped() {
        val now = 1_792_700_000_000L
        validateConnectedDoctorDose("taken", now + 60000, now - 1, now)
        validateConnectedDoctorDose("skipped", now, null, now)
        listOf("late", "unknown", "Taken", "").forEach { outcome -> assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDose(outcome, now, now - 1, now) } }
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDose("taken", now, null, now) }
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDose("taken", now, now + 1, now) }
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDose("taken", now, 0, now) }
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDose("skipped", now, now - 1, now) }
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDose("skipped", 0, null, now) }
        assertThrows(IllegalArgumentException::class.java) { validateConnectedDoctorDose("skipped", now, null, 0) }
    }

    @Test fun databaseTimestampIsUtcAndDoesNotDependOnPhoneTimezone() {
        val instant = java.time.Instant.parse("2026-10-23T03:30:00Z").toEpochMilli()
        assertEquals("2026-10-23T03:30:00Z", connectedDoctorInstant(instant))
        val india = java.time.LocalDateTime.of(2026, 10, 23, 9, 0).atZone(java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertEquals(instant, india)
        assertEquals("2026-10-23T03:30:00.123Z", connectedDoctorInstant(instant + 123))
    }

    @Test fun uncertainSaveReplyMatchesOnlyExactOwnedDraftReceipt() {
        val owner = UUID.randomUUID().toString()
        val patient = UUID.randomUUID().toString()
        val draft = WorkflowDraft(patient, sourceText = "Synthetic exact original", priorities = "Keep source", transcriptModel = "fixture-provenance", updatedAt = 123)
        val note = ConnectedDoctorNote(UUID.randomUUID().toString(), patient, 4, "draft", draft, 100, 200)
        assertTrue(connectedDoctorDraftRetryMatches(note, owner, owner, patient, 3, draft))
        assertFalse(connectedDoctorDraftRetryMatches(note, UUID.randomUUID().toString(), owner, patient, 3, draft))
        assertFalse(connectedDoctorDraftRetryMatches(note, owner, owner, UUID.randomUUID().toString(), 3, draft))
        assertFalse(connectedDoctorDraftRetryMatches(note, owner, owner, patient, 2, draft))
        assertFalse(connectedDoctorDraftRetryMatches(note.copy(status = "test_published"), owner, owner, patient, 3, draft))
        assertFalse(connectedDoctorDraftRetryMatches(note, owner, owner, patient, 3, draft.copy(priorities = "Changed")))
        assertFalse(connectedDoctorDraftRetryMatches(note, owner, owner, patient, 3, draft.copy(transcriptModel = "Changed provenance")))
        assertFalse(connectedDoctorDraftRetryMatches(note, owner, owner, patient, 3, draft.copy(sourceText = "Different original")))
    }
}
