package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The doctor-side asks from the team chat review of 23 Nov 2026 (goals of care). Sample data only. */
class GocDoctorReviewTest {
    private val r0 = EctprLifecycle.newDraft("p1", "Dr Sample", "2026-11-04T10:00:00Z", EctprPatient(name = "Sample patient"), id = "r1")
    private fun text(r: EctprRecord, signers: List<GocFormSigner> = emptyList()) = GocForm.sections(r, emptyMap(), signers).flatMap { s -> listOf(s.title.en.uppercase()) + s.rows.map { it.en } }.joinToString("\n")

    // SVK and Dr Sujay, 23 Nov: the patient OR the family agreeing is enough to record.
    @Test fun eitherThePatientOrTheFamilyIsEnoughToRecord() {
        assertTrue(GocRecordingConsent.given(patient = true, family = false))
        assertTrue(GocRecordingConsent.given(patient = false, family = true))
        assertTrue(GocRecordingConsent.given(patient = true, family = true))
        assertFalse(GocRecordingConsent.given(patient = false, family = false))
        assertEquals(1000L, GocRecordingConsent.at(patient = false, family = true, now = 1000L))
        assertEquals(0L, GocRecordingConsent.at(patient = false, family = false, now = 1000L))
    }

    @Test fun whoAgreedIsRecordedAndReadBack() {
        assertEquals("PATIENT", GocRecordingConsent.encode(true, false))
        assertEquals("FAMILY", GocRecordingConsent.encode(false, true))
        assertEquals("PATIENT,FAMILY", GocRecordingConsent.encode(true, true))
        assertEquals("", GocRecordingConsent.encode(false, false))
        assertEquals(false to true, GocRecordingConsent.decode("FAMILY", 1000L))
        assertEquals(true to false, GocRecordingConsent.decode("PATIENT", 1000L))
        // Saved before the rule changed: both ticks were required, so both were given.
        assertEquals(true to true, GocRecordingConsent.decode("", 1000L))
        assertEquals(false to false, GocRecordingConsent.decode("", 0L))
        val back = GocTranscriptJson.decode(GocTranscriptJson.encode(GocTranscript(consentAt = 1_700_000_000_000L, consentBy = "FAMILY")))!!
        assertEquals("FAMILY", back.consentBy)
        assertEquals(false to true, GocRecordingConsent.decode(back.consentBy, back.consentAt))
    }

    // SVK, 23 Nov: "unable to tick this, but I'm able to sign". The Section IV declaration box is ticked by the signature.
    @Test fun signingTicksTheDeclarationBoxes() {
        val unsigned = text(r0)
        assertTrue(unsigned, unsigned.contains("☐ I have made these choices myself."))
        assertTrue(unsigned, unsigned.contains("☐ I confirm I am conveying what the patient would have wanted"))
        val patient = text(r0, listOf(GocFormSigner("PATIENT", "Sample patient", "2026-11-04T11:00:00Z")))
        assertTrue(patient, patient.contains("☑ I have made these choices myself."))
        val family = text(r0, listOf(GocFormSigner("FAMILY", "Sample son", "2026-11-04T11:00:00Z", relation = "Son")))
        assertTrue(family, family.contains("☑ I confirm I am conveying what the patient would have wanted"))
        assertTrue(family, family.contains("☐ I have made these choices myself."))
    }

    // Dr Sujay, 7 Nov: a clinical document, never "legal" or "living will".
    @Test fun theNoteSaysNothingAboutALegalFormOrALivingWill() {
        val note = (text(r0) + "\n" + GocNote.draft(r0, emptyMap(), "", false)).lowercase()
        listOf("living will", "legal").forEach { assertFalse(it, note.contains(it)) }
        assertFalse(VidhiText.S1_INTRO.contains("living will", ignoreCase = true))
    }

    // Dr Sujay, 21 Nov: an old printed note must say which version it is and send the reader to Sahara for a newer one.
    @Test fun printedNoteNamesItsVersionAndPointsToANewerOne() {
        val line = GocPrintVersion.line(r0).en
        assertEquals("Version ${r0.version}, made on 2026-11-04. Check Sahara for a newer version before using a printed copy.", line)
    }
}
