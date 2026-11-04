package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The goals-of-care note is Rituparna's legal form, word for word, and the short flow's "Confirm all" keeps rule V1. Sample data only. */
class GocLegalFormTest {
    private val r0 = EctprLifecycle.newDraft("p1", "Dr Sample", "2026-11-04T10:00:00Z", EctprPatient(name = "Sample patient"), id = "r1")
    private fun text(r: EctprRecord, signers: List<GocFormSigner> = emptyList()) = GocForm.sections(r, emptyMap(), signers).flatMap { s -> listOf(s.title.en.uppercase()) + s.rows.map { it.en } }.joinToString("\n")

    @Test fun everyHeadingFieldBoxAndTableOfTheFormIsThereInOrder() {
        val r = r0.copy(choices = r0.choices.copy(respCeiling = 1, maxOxygenLpm = 2.0, hospitalTransfer = HospitalTransfer(TransferKind.ONLY_FOR, listOf(TransferReason.PAIN))))
        val note = text(r, listOf(GocFormSigner("DOCTOR", "Dr Sample", "2026-11-04T10:00:00Z", "REG-1"))).lowercase()
        val form = listOf("Section I – General information", "Patient details", "Age, Sex, Gender", "Physician details", "Primary Oncologist name and hospital – and contact",
            "Additional space for the team", "Additional option to add a local physician", "Family/Friend details", "Members present for discussion", "Language preferred",
            "|  | PRIMARY | SECONDARY |", "Phone (must be reachable 24 h)", "Basis for authority", "Named by patient", "Court-appointed",
            "Family members who must be informed before any major decision", "attempted to be contacted",
            "Section II – Preliminary medical information and patient values", "Primary diagnosis and stage", "Functional status – ECOG", "4.1  The patient’s capacity, now",
            "Capacity fluctuates", "4.2  Existing directives", "Attested by Notary", "with two witnesses", "5.3  What the patient knows", "will not be lied to", "6.1  In their own words",
            "The most distressing physical symptom right now is", "What I am most afraid of is: Not discussed", "Unfinished things that matter", "6.2  Outcomes that would be unacceptable",
            "Exhausting the family’s savings", "Section III – Decisive Actions", "Home care (if relevant)", "Should the patient be brought to the hospital?", "☑ ONLY FOR THE FOLLOWING",
            "☑     Pain that cannot be controlled at home", "Caregiver unable to cope — respite admission", "NO — the patient is to be cared for where they are.", "frequency of assessments",
            "non-governmental organisation", "previously admitted", "Should CPR be performed?", "ATTEMPT CPR.", "DO NOT ATTEMPT CPR (DNACPR).", "Reason recorded (clinical futility",
            "Discussed with (patient / surrogate / both)", "| LEVEL | WHAT IT MEANS FOR THE PATIENT | CEILING |", "Low-flow oxygen up to 2 L/min", "Other parameters", "Dialysis or CRRT",
            "Only if the renal failure is judged reversible", "Others", "Requirement of ICU?", "Common Cause v Union of India, 2018 and 2023", "Composition of the Boards",
            "Final decision about withdrawal/withholding", "Protocol for withdrawal/withholding", "Vasopressors / inotropes", "no escalation beyond ____ agents", "12.1  Feeding",
            "☑ Comfort feeding by mouth only", "Nasogastric (Ryle’s) tube", "Gastrostomy (PEG)", "A. PRIORITISE LIFE", "B. BALANCED / CEILING OF CARE", "C. PRIORITISE COMFORT",
            "SECTION 19 — Declarations and signatures", "19.1  Patient", "I have made these choices myself.", "Name in capitals", "19.2  Surrogate / healthcare proxy",
            "so far as I can judge", "19.3  Witnesses", "Address / ID", "19.4  Clinicians", "| ROLE | NAME | REGISTRATION NO. | SIGNATURE | DATE |", "| Treating consultant | Dr Sample | REG-1 | Signed |",
            "Nurse involved in the discussion")
        var at = 0
        form.forEach { words ->
            val found = note.indexOf(words.lowercase(), if (words.startsWith("|") || words.startsWith("☑")) 0 else at)
            assertTrue("Missing from the note: $words", found >= 0)
            if (!words.startsWith("|") && !words.startsWith("☑")) at = found
        }
        assertTrue("The ceiling box is ticked in the oxygen table", Regex("""\| 1 \| [^|]* \| ☑ \|""").containsMatchIn(note))
    }

    @Test fun confirmAllAppliesOnlyWhatWasSaidAndNeverADefault() {
        val words = "Patient: No. I do not want CPR.\nPatient: No dialysis.\nSon: Please do everything.\nPatient: Keep me comfortable."
        val lines = GocLines.fromText(words)
        val all = GocSuggest.all(GocExtract.extract(GocLines.text(lines)), lines)
        val decided = all.map { it.first }.toSet()
        assertTrue(all.toString(), GocDecision.CPR in decided && GocDecision.DIALYSIS in decided)
        assertTrue("Nothing about ICU was said, so nothing is set", GocDecision.ICU !in decided)
        all.filter { it.first != GocDecision.VALUES }.forEach { (d, id, _) ->
            val s = GocLatest.standing(GocExtract.extract(GocLines.text(lines)), d, lines)
            assertTrue("$d: the option was said", id in s.suggested.keys); assertTrue("$d: nobody disagreed", !s.conflict)
        }
        var r = r0
        all.forEach { (d, id, quotes) -> r = GocPlan.apply(r, d, id, quotes) }
        assertNull(r.choices.icu); assertNull(r.choices.hydration); assertNull(r.choices.ambulance)
        assertEquals(Cpr.DNACPR, r.choices.cpr)
    }

    @Test fun theDoctorIsTheSpeakerWhoClearlyAsksTheQuestions() {
        val lines = listOf(GocLine("Would you want CPR?", who = "Speaker 1"), GocLine("No.", who = "Speaker 2"), GocLine("And dialysis?", who = "Speaker 1"), GocLine("No dialysis.", who = "Speaker 2"))
        assertEquals("Speaker 1", gocDoctorSpeaker(lines))
        assertNull("Can't tell: the screen asks once", gocDoctorSpeaker(listOf(GocLine("Hello.", who = "Speaker 1"), GocLine("Hello.", who = "Speaker 2"))))
    }

    @Test fun signersFromASavedSignOffReachSection19() {
        val so = GocSignOffSummary("s", "seal", "2026-11-04T11:00:00Z", listOf("Dr Sample", "Sample patient"), listOf("DOCTOR", "PATIENT"), listOf("REG-1", ""), listOf("DRAWN", "THUMB"), listOf("2026-11-04T10:59:00Z", "2026-11-04T11:00:00Z"))
        val back = GocTranscriptJson.decode(GocTranscriptJson.encode(GocTranscript(planVersion = 1, note = "n", signOff = so)))!!.signOff!!
        assertEquals(so, back)
        val note = text(r0, back.formSigners())
        assertTrue(note, note.contains("|  | Thumb impression | SAMPLE PATIENT | 2026-11-04 |"))
    }
}
