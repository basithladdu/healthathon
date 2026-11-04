# Goals-of-care note: coverage of Rituparna's legal form

Source: `source-materials/team-context/legal-review-2026-10-31/Healthathon_form_Rituparna.docx` (with her tracked additions) and `extracted-text-and-comments.txt`.

The note is built in one place per platform, and every output draws that one document:

- Web: `prototype/app/goc-form.ts` (`buildGocFormDocument`). On screen (goc-transcribe.tsx, Note step), print (`gocFormHtml`), Word (`goc-docx.ts`) and the signed note text (`gocFormText`).
- Android: `GocForm.sections` in `CareGocExtract.kt`. On screen (`GocFormView` in CareGocUi.kt, also the doctor's view in DoctorGocForm.kt), the signed note (`GocNote.draft`) and the PDF (`CarePlanPdf.gocNote`, also used by the plan PDF and `DoctorGocPdf`).

The check is the same on both sides: `prototype/tests/goc-extract.test.mjs` ("every heading, field, box and table…") and `GocLegalFormTest.kt` look for each line below, in the form's order.

"Model" says where the answer comes from. "Blank" means the plan has no field for it yet, so the line is always printed and reads **Not discussed** (or an empty box). It is never guessed.

| Form line | Shown as | Model |
|---|---|---|
| **Section I – General information** | Section card I | |
| Patient details: Name; Age, Sex, Gender; Address; Phone number | Rows | patient |
| Physician details: Primary Oncologist name and hospital – and contact | Row | careTeam.oncologist |
| Palliative care physician name and hospital – and contact | Row | palliativePhysician + 24 h phone |
| Additional space for the team – Surgical oncology, radiation oncology, etc. | Row | careTeam.extraTeam |
| Additional option to add a local physician … | Row | careTeam.localDoctor |
| Family/Friend details: Members present for discussion and relationship with the patient | Row | decisionMakers.presentAtDiscussion (consent screen names add here) |
| Language preferred | Row | patient.languagePref |
| Primary / Secondary decision maker and contact details: table PRIMARY / SECONDARY with Name, Relationship to patient, Phone (must be reachable 24 h), ID type & number, Basis for authority (☐ Named by patient ☐ Family consensus ☐ Named in AMD ☐ Court-appointed) | 3-column table, boxes | decisionMakers.primary / alternate |
| Family members who must be informed before any major decision (name, relation, phone) | Row | familyToInform |
| Comment C6: family/friends attempted to be contacted, but unavailable | Row | attemptedContact |
| **Section II – Preliminary medical information and patient values** | Section card II | |
| Primary diagnosis and stage; Functional status – ECOG | Rows | clinical |
| Patient's capacity; Existing medical directives; What the patient knows | Summary rows (the ticked box) | from 4.1, 4.2, 5.3 |
| 4.1 The patient's capacity, now: 4 boxes with blanks (own words; assessed by / on; basis of assessment) | Boxes, blanks filled | capacity.status, basis, assessor, assessedOn |
| 4.2 Existing directives: valid AMD (executed on, location), attested by Notary / Gazetted Officer, with two witnesses Yes/No + subordinate clause, prior wishes, none | Boxes | existingDirective. **Blank:** "with two witnesses" (no field) |
| 5.3 What the patient knows: 4 boxes, family understands Yes / Not yet resolved | Boxes | values.patientKnows ticks "diagnosis but not prognosis"; familyAskedNotToTell + directQuestionPlan. **Blank:** "diagnosis and prognosis", "does not wish to know … delegated" (no field) |
| 6.1 In their own words: most distressing symptom; what matters most; most afraid of; unfinished things | 4 rows | values.ownWords fills "what matters most". **Blank:** the other three (no field) |
| 6.2 Outcomes that would be unacceptable: 9 boxes + Other | Boxes | values.unacceptableOutcomes; text that matches none goes in Other |
| **Section III – Decisive Actions** | Section card III | |
| Home care (if relevant): Should the patient be brought to the hospital? YES / ONLY FOR (9 reasons + Other) / NO | Boxes | choices.hospitalTransfer. **Blank:** the "Other" reason box |
| If not, frequency of assessments and coordinating person | Row | homeCare.assessmentFrequency, coordinator |
| NGO representative + declaration of consultation | Row (☑ NGO consulted) | homeCare.ngoContact, ngoConsulted |
| Previously admitted for the current condition? | Row | homeCare.previousAdmission (+ details) |
| Should CPR be performed? ATTEMPT CPR / DO NOT ATTEMPT CPR (DNACPR) | Boxes | choices.cpr |
| Reason recorded (clinical futility / patient refusal / burden outweighs benefit) | Row | choices.dnacprReason |
| Discussed with (patient / surrogate / both) | Row | choices.dnacprDiscussedWith |
| Oxygen/ventilatory support: LEVEL / WHAT IT MEANS FOR THE PATIENT / CEILING, levels 0–5 word for word, "up to ____ L/min" | 3-column table, ceiling box | choices.respCeiling, maxOxygenLpm fills the L/min blank |
| Other parameters: Dialysis or CRRT (No / Trial of __ sessions/days / Yes / Only if reversible) | Boxes | choices.dialysis (+ trial) |
| Others | Row | **Blank** (no field) |
| Requirement of ICU? | Boxes (Yes / Time-limited trial / No) | choices.icu |
| Withdrawal and withholding (Common Cause v Union of India, 2018 and 2023): dates of the Primary and Secondary Boards; composition; decision of the Boards; final decision (4 boxes); protocol | Rows + boxes | withdrawal |
| Vasopressors / inotropes: No / Short trial up to __ hours / Yes without a limit / Yes, no escalation beyond __ agents | Boxes | choices.vasopressors |
| 12.1 Feeding: comfort feeding by mouth (always); Nasogastric (Ryle's) tube No / up to __ days / Yes; Gastrostomy (PEG) No / Only after palliative discussion / Yes | Boxes | feeding (comfort is always ticked) |
| A. PRIORITISE LIFE / B. BALANCED / CEILING OF CARE / C. PRIORITISE COMFORT | Boxes | choices.goalOfCare |
| **SECTION 19 — Declarations and signatures** (Section IV) | Section card IV | |
| 19.1 Patient: ☐ made these choices myself ☐ delegated; Signature / thumb impression; Name in capitals; Date | 4-column table | capacity HAS / HAS_DELEGATED; plan signature or the patient's signature drawn on the doctor's device |
| 19.2 Surrogate / healthcare proxy (primary and secondary): ☐ conveying what the patient would have wanted; Signature; Name & relationship; Date | 4-column table | surrogateDeclaration.confirmed; SURROGATE signatures; a family member who signed on the device fills Primary |
| 19.3 Witnesses (thumb impression or limited literacy – home and hospital): Witness 1 and 2, name, Address / ID, Signature, Date | 4-column table | WITNESS signatures, or the witness signed on the device |
| 19.4 Clinicians: ROLE / NAME / REGISTRATION NO. / SIGNATURE / DATE; Treating consultant, Palliative care clinician, Nurse | 5-column table | plan signatures; the doctor who signed on the device fills Treating consultant |

## Not on the form but still in the plan

Fluids (hydration) and "what the ambulance crew should do" are plan fields that the legal form does not have. They stay in the plan and its emergency card, and are no longer printed in the goals-of-care note.

## Open (needs new plan fields, not in the GOC files)

6.1 symptom / fear / unfinished things; 5.3 "knows the prognosis" and "does not wish to know, delegated"; 4.2 "with two witnesses"; "Others" under Other parameters; the "Other" transfer reason. Adding them needs fields in `ectpr-model.ts` / `CarePlanModel.kt` and the wizard; the form already prints their lines.
