# Truly Left

Updated 22 September 2026. Entry: **Cancer > Patient / Caregiver > Family & Caregiver Support**.

## What this is for

Saanthvana helps cancer patients and their families understand what was agreed with the care team, know what needs doing next, and keep everyone working from the same care plan.

The goals-of-care conversation is the centre: what matters to the patient, who was involved, what was discussed, what was agreed and what still needs an answer. Appointments, medicines, reports and practical help make that conversation useful between visits.

We do not need to serve every disease to fit this brief. We need to show how this focused idea helps the patient, family and care team. Adding features alone does not establish demand or improve our chances; a clear, usable care journey and evidence from users do.

## Already there

These ticks mean the feature exists in the app. Shared accounts, service connections and clinical validation are separate work below.

- [x] Patient/family entry, grouped home navigation, separate feature pages and an editable patient profile.
- [x] Care-note drafting, doctor review, patient/family acknowledgement, version history and PDF export. Signatures currently record typed names.
- [x] Reports grouped by purpose, dated test results and a treatment/appointment calendar with preparation notes.
- [x] A separate 0-10 score for each selected symptom, a personal journal and mood entries.
- [x] Palliative-care map, centre markers, calls and directions; home-help categories for nursing, transport and medicines/equipment.
- [x] Typed-PDF text extraction with review and source-linked text search. This is not yet document question answering.
- [x] Doctor audio recording/upload and browser-dependent English/Hindi captions. Reliable uploaded-audio transcription and speaker identification are still open.
- [x] Full meeting transcript preserved; meeting minutes and an editable draft saved.

## First: finish the care conversation

- [ ] **Remove the current scope conflict first.** The older `/workspace/draft` and `/workspace/retrieve` doctor routes still expose prognosis scoring, triage and treatment/dose tools. Remove or isolate those accessible surfaces from the submitted product. The supplied brief explicitly excludes clinical scoring, decision support, medical-data interpretation and treatment advice, including on the doctor side.
- [ ] **Complete the actual care-note form.** Map every agreed field in the team's form, keep participants and agreement specific to each discussion, and have the clinicians review the completed flow. The current five-section note has prompts, not full structured form coverage.
- [ ] **Make the voice button useful to a busy doctor.** Record the conversation with consent, identify speakers, draft only what was said, and let the doctor correct it before signing. Keep source audio/text traceable. Multi-speaker transcription and connected AI are unfinished.
- [ ] **Let the family genuinely receive and review it.** Separate accounts, patient-controlled access, shared storage, exact-version acknowledgement and an audit trail must work across devices. Entering an ABHA or council number does not verify identity.
- [ ] **Finish sharing with the next doctor.** A readable phone view, PDF and QR/link with controlled access, expiry and revocation. Do not promise that a downloaded PDF can be recalled. A typed name is not a verified digital signature or a legal advance directive.

## New action: cover the family needs around it

This follows Basith's 22 September request. The meeting had removed cost-help and family tasks from the main flow; the items below are a new alignment proposal, not a rewrite of that decision. Keep fundraising and payment collection out of this plan.

- [ ] **One shared plan for the week.** Put the existing visits, tests and treatment milestones together with who is helping, what to bring and what is done. Use optional one-tap assignments; do not make the family fill another long form.
- [ ] **Medicines from the prescription.** Read a typed prescription, show the original beside the proposed medicine list and timings, and require review before saving. Add reminders and refill tasks only after the list is confirmed. Do not invent doses or interpret handwriting.
- [ ] **Useful updates and alerts.** Send the right person a confirmed appointment reminder, changed-plan update or agreed task reminder. Include acknowledgement, preferences and quiet hours. Calendar dots and saved entries are not delivered notifications.
- [ ] **Care instructions people can understand.** Bring forward the care team's instructions, warning signs and contact plan in plain language. Allow reviewed translations. Include relevant lifestyle and home-care instructions already given by the clinician; do not generate fresh medical advice.
- [ ] **Practical help without false booking claims.** Verify centre and service details, then make nursing, transport and equipment contacts easy to reach. Track requested/confirmed/completed help only when those states are real.
- [ ] **Keep money support small.** Expenses, bills, assistance contacts and the documents needed for a support application. The costs page is currently outside the main flow. Reintroduce the useful record/assistance part only; no fundraiser marketplace.
- [ ] **Support the caregiver too.** Optional help requests and relevant support resources. Add per-person journal access: entries are currently grouped by patient, so individual privacy is not enforced between people using that patient's records. Sharing must be a choice. Groups should follow the chosen need or condition, not private journal analysis.

## Finish the assistant and prove the workflow

- [ ] **Connect document answers and visit preparation.** Answer from the person's uploaded records, cite the page/section and say when the answer is missing. A short visit brief should save reading time. Do not interpret test results, infer prognosis or recommend treatment.
- [ ] **Reduce report entry.** Extract printed test names, dates, values, units and report-provided ranges into a review screen. Preserve the source and corrections. Do not turn a range comparison into a clinical judgement. Scanned-report OCR and automatic value extraction remain unfinished.
- [ ] **Carry review and corrections through every AI step.** The person can edit, reject or retry an output. Record the source, changes, reviewer and approved version. A note history on one browser is not the full shared audit system.
- [ ] **Check language and effort with actual users.** Choose the pilot languages, complete the important screens and reviewed care-note translations, then observe a doctor and a patient/caregiver completing the core journey. Measure taps and time; do not just count translated labels.
- [ ] **Run a 60-90 day operational pilot.** Establish a baseline for time spent writing/retrieving a care note, time a family needs to find the next step, and completion/acknowledgement of agreed tasks. Track extra doctor effort and errors too. Record results before claiming impact; no clinical outcome claims from a UI walkthrough.
- [ ] **Finish the Round 1 package.** Keep the portal classification and answers consistent with this purpose. Revise the 6-8 slide deck, show one complete care journey, include the working URL/QR and walkthrough, and upload/submit. The submission is not complete merely because the app is deployed.

## Every part of the caregiver brief

The brief gives examples, not a requirement to build every service. This table keeps each stated need accounted for while keeping the cancer care conversation central.

| The brief asks for | What exists | What is still needed |
| --- | --- | --- |
| Support during long-term cancer care, prolonged treatment and recovery | Cancer-focused notes, reports and treatment calendar | Follow the same family through successive visits and changes; validate usefulness between visits. |
| Appointments | Calendar and preparation notes | Reviewed import, shared access and reminders that actually reach the right person. |
| Medications | Older medication records remain; new manual entry was removed | Confirmed typed-prescription import, medicine list, reminders and refill coordination. |
| Lifestyle modifications | Notes can hold instructions | An easy view of the clinician's existing instructions; reviewed language support. |
| Home care | Nursing and equipment help categories | Verified contacts and genuine follow-through on requests. |
| Transportation | Transport help and directions | Assign a helper and confirm arrangements without claiming a booking was made. |
| Finances | Previous costs UI exists outside the main flow | Small expense/bill record and assistance contacts, if adopted under the new action. |
| Emotional well-being and less stress | Personal journal, moods and support resources | Caregiver-specific support and user feedback on whether coordination reduces effort/stress. |
| Family as the link to providers | Care notes and a doctor summary/PDF | Consented delivery, questions and acknowledgement across separate accounts. |
| Visibility of the care journey | Care story, note versions, reports and calendar | One up-to-date shared view with clear authorship and change history. |
| Understanding the treatment plan | Existing plan can be recorded and carried forward | Clinician-reviewed plain-language summary, with the original available. No new treatment recommendations. |
| Upcoming milestones | Chemotherapy, radiotherapy, test and visit events | Show confirmed upcoming steps and who is helping. |
| Warning signs | Care-note space for team instructions | Display the clinician's warning/contact plan faithfully; no automated risk score or symptom triage. |
| Care instructions and requirements | Note sections and appointment preparation | Turn approved instructions into short, optional actions without repeated entry. |
| Changes in condition and other updates | Symptom entries, dated tests and new note versions | Share only what the patient allows; show changes as recorded, without medical interpretation. |
| Timely and informed support | Information available in separate sections | Reliable update delivery, acknowledgements and a clear next step. |
| Patient consent and privacy | Consent/review controls within the app | Enforced account permissions, revocation, secure storage and shared audit records. |
| Existing clinical workflows | Profile reuse, imported reports and note carry-forward | Finish voice/source import so clinicians review existing information rather than retype it. |
| Less fragmented communication and continuity | Common care note and version history | Actual cross-device handoff and clear responsibility for follow-up. |
| Personalised education | General resource links | Relevant, approved information in the user's language, selected from recorded care needs with human review. |
| Task management | Tasks removed from the main flow | A small optional shared checklist with a named helper, completion and acknowledgement. |
| Timely alerts | In-app dates/statuses | Connected, consented notifications with preferences and delivery status. |
| Communication, shared planning and collaboration | Review screens, questions and care summaries | Separate users can contribute, see the approved version and resolve questions without duplicate work. |

## Scope and delivery checks

| Official principle | What we must demonstrate |
| --- | --- |
| Real workflow, continuity, engagement and administrative needs | One coherent path from a care discussion to what the patient/family does next. |
| Existing AI capabilities and tools | A working transcription/document assistant. Choose a tool that fits; naming an AI framework is not evidence of an AI feature. |
| Workflow first; light integration | Reuse profile details, prescriptions, reports and conversation records. Minimise new work for doctors and families. |
| Flexible innovation | Keep the goals-of-care focus and connect useful caregiver tasks. Broadening to every disease is unnecessary. |
| Easy, intuitive, low training; busy clinic and limited digital fluency | Direct routes, plain labels, large touch targets, few required fields and observed user completion. |
| Multilingual and caregiver-friendly | Complete the agreed language journeys, including the important care information, with review. |
| Human-centred; patients, caregivers and clinicians | Verify each person's next step and who is responsible for it. |
| Assistive, low risk, oversight and human override | Review/edit/reject before consequential use; no silent changes to approved notes or medication schedules. |
| Auditability for important actions | Record consent, source, author, reviewer, approved version, sharing and corrections. |
| Measurable impact in 60-90 days | Baseline and follow-up measurements of time, coordination and extra workload. No invented improvement percentages. |
| Workflows, operations, continuity, engagement and administration are in scope | Documentation, retrieval, scheduling, approved instructions, reminders and practical support. |
| Diagnosis, treatment recommendations, clinical decision support and risk scoring are out of scope | Do not expose those features through legacy screens or direct URLs. |
| Interpretation of medical data and autonomous clinical advice are out of scope | Preserve and organise recorded information; route clinical questions to the care team. |

## Evidence and status

Status checked against the application code and [the implementation record](docs/meetings/2026-09-21-implementation.md) on 22 September 2026. The production app was deployed from `34df7e014462ecd08edb19cd3d8c2050a7af0284`; these document updates do not add product features.

The current care data is held in the browser. Shared server-side accounts, identity checks, controlled link sharing and external service connections are still outstanding. The implementation record's last live AI readiness check was `ready: false`; connecting and verifying AI remains open. End-to-end use with real documents and separate users is still required.

Source checks behind the open items:

- [Routes](prototype/app/care-routes.ts) and [their rendered screens](prototype/app/continuity-prototype.tsx): removed costs/tasks routes, empty calendar task list and still-accessible legacy clinical tools.
- [Sign-in](prototype/app/care-sign-in.tsx), [storage](prototype/app/care-local-store.ts) and [journal state](prototype/app/family-voice-state.ts): identifier format checks, browser storage and patient-level journal retrieval.
- [Conversation recording](prototype/app/doctor-conversation.tsx), [AI endpoint](prototype/app/api/care-assist/route.ts) and [document search](prototype/app/care-document-search.tsx): existing controls versus unfinished connected transcription and question answering.
- [Care-note signing](prototype/app/care-note-signing-state.ts) and [review](prototype/app/doctor-note-review.tsx): typed signatures and exact-version acknowledgement.
- [Calendar](prototype/app/care-calendar.tsx), [map](prototype/app/care-near-me.tsx) and [home help](prototype/app/family-home-help.tsx): calendar records, map/directions and local helper status, without confirmed notification delivery or service dispatch.

- [Official brief and full scope wording](docs/healthathon/official-brief.md)
- [Meeting minutes](docs/meetings/2026-09-21-minutes-draft.md)
- [Editable draft text](docs/meetings/2026-09-21-draft-text.txt)
- [Preserved meeting transcript](source-materials/meetings/2026-09-21-transcript.txt)

**Confidence:** high for the official wording supplied and the source-checked feature inventory; unproven for clinical adoption, sustained use, impact and competition results.
