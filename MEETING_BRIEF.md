# Health-a-thon 2026 meeting brief

## Decision to take into the meeting

The live Health-a-thon dashboard was inspected read-only on 24 August 2026. The registered selection is:

**Cancer Care -> Clinician focused -> Patient Follow-up & Continuity of Care**

Do not try to change it before the meeting. It is already the strongest overall execution choice in the independent comparison. The problem is not the selected use case; the problem is avoiding a generic reminder app.

Recommended concept, subject to clinical validation: **Goals-of-Care Continuity Loop**

A clinician enrolls an appropriate patient, records a clinician-set next conversation or review date, and assigns the follow-up. The system surfaces due and overdue work, supports coordinator outreach, records attempts and outcomes, and preserves the latest physician-verified goals-of-care summary. AI may structure a draft from clinician-entered source text or dictation, but it never decides which patients need the conversation, when a clinical change requires action, or what treatment should be given.

This is an honest fit to the locked use case only if the doctors confirm a recurring continuity failure: planned goals-of-care conversations are missed, delayed, left incomplete, or not revisited and updated across months of cancer care. If the only real problem is retrieving an already-completed note in the emergency department, the PDF belongs under **Consultation Readiness & Patient Journey Review**, and the team should ask the organizers to change the category rather than mislabel the solution.

## The problem we are solving

Goals-of-care conversations for people with advanced cancer are not necessarily one-time events. They may need more than one visit and may need to be revisited as circumstances change. In practice, an intended conversation may never be scheduled, may remain incomplete, or may not be reviewed again. The resulting note can also be incomplete, unstructured, outdated or difficult to find when another team needs it.

The operational failure is a broken continuity loop: no visible cohort, no accountable owner, no reliable due date or status, no recorded outreach outcome, and no assured current verified summary. The system does not select treatment or infer clinical eligibility.

## Simple example for the meeting

A patient with advanced lung cancer starts a goals-of-care discussion with her oncologist and daughter. They need time to discuss it at home, so the clinician plans to continue it at the next visit. The plan is written in free text, nobody owns the follow-up, and the next visit is missed. Months later, an old incomplete note is the only record another care team can find.

With Goals-of-Care Continuity Loop, the clinician enrolls the patient and sets the next review date. A navigator sees the due task, contacts the patient through an approved channel, records the outcome, and reschedules it when necessary. After the conversation, the clinician dictates or completes a guided summary. AI structures only what was stated; the clinician verifies it, the patient or surrogate authorizes it, and the signed version becomes immutable. The next authorized clinician can retrieve the latest verified version. The system offers no treatment recommendation.

## Users and responsibilities

- Primary creator: oncologist or palliative-care physician.
- Follow-up owner: nurse navigator, care coordinator or named clinic team member.
- Authorized updater: designated treating clinician; every update creates a new version.
- Reader: emergency, ICU, oncology or palliative-care clinician with authorized access.
- Record controller: patient or authorized surrogate.
- Operational owner: participating hospital or cancer centre.

## What AI does

- Transcribes a consented clinician dictation or conversation.
- Converts stated information into a predefined structured draft.
- Flags missing required fields and ambiguous transcription for review.
- Produces a multilingual patient-facing copy after clinician approval.
- Summarizes changes between versions without deciding which version is clinically appropriate.

## What AI must never do

- Diagnose, estimate prognosis or decide who needs a goals-of-care discussion.
- Recommend ICU admission, ventilation, feeding, resuscitation or any treatment.
- Convert patient values into a clinical order.
- Present the summary as a DNAR order, advance medical directive or living will.
- Resolve conflicts between family members, documents or clinicians.
- Alter a signed record.

## Five-week prototype

1. Clinician and coordinator login with role-based access.
2. Clinician-controlled patient enrolment; no AI eligibility or risk score.
3. Follow-up status, owner and clinician-set due date.
4. Worklist for due, overdue, attempted, rescheduled and completed tasks.
5. Approved multilingual outreach template and outcome capture.
6. Guided goals-of-care note or clinician dictation.
7. AI-generated structured draft with source-linked field confirmation.
8. Physician attestation, patient/surrogate authorization and immutable versions.
9. Controlled retrieval, audit trail and operational metrics.

Not in the first version: full hospital-system replacement, nationwide record exchange, legal execution of advance directives, real-time clinical decision support, automated prognostic eligibility, automatic clinical triggers, or use of identifiable patient data in the hackathon demo.

## Pilot and evidence plan

Primary KPI:

**Percentage of clinician-enrolled goals-of-care follow-up tasks completed by the clinician-set due date.**

Secondary measures:

- Median time from enrolment to completed conversation.
- Contact, reschedule and follow-up completion rates.
- Documentation completion rate among clinician-enrolled patients.
- Successful retrieval rate.
- Median authorized retrieval time, with an under-60-second prototype target.
- Number of repeated goals-of-care conversations caused by an unavailable record.
- Number of attempted accesses to stale or superseded versions.
- Clinician usability and patient/caregiver comprehension.

Do not use alignment between emergency treatment and documented preferences as the initial KPI. It needs longer follow-up, clinical adjudication and governance beyond a short hackathon pilot.

Suggested 60-90 day sequence:

- Weeks 1-2: map the existing workflow, template, access rules and baseline times.
- Weeks 3-4: test the prototype with synthetic cases and clinician walkthroughs.
- Weeks 5-8: limited institution-approved workflow pilot, if available.
- Weeks 9-12: measure retrieval, completion, usability and failure cases.

## Evidence supporting the idea

- Goals-of-care conversations can be iterative and nonlinear as the clinical picture evolves, supporting a continuity workflow rather than treating the conversation as a one-time document: https://pmc.ncbi.nlm.nih.gov/articles/PMC11998719/
- A 2025 Indian cancer-centre quality-improvement project increased goals-of-care documentation from 0% to 92% using a standardized paper form. Its authors identified digital implementation and portability to another hospital as remaining gaps: https://journals.sagepub.com/doi/pdf/10.1177/26892820251392545
- ICMR guidance says relevant discussions must be recorded and completed DNAR forms should be readily accessible and integrated into the EHR where available: https://www.icmr.gov.in/icmrobject/uploads/Guidelines/1724842969_icmr_consensus_guidelines_on__do_not_attempt_ijmr.pdf
- Indian EHR standards call for immutable records, append-only revisions, active/inactive versions, authentication, consent and audit trails: https://www.mohfw.gov.in/sites/default/files/EMR-EHR_Standards_for_India_as_notified_by_MOHFW_2016.pdf
- Health-a-thon explicitly allows documentation and workflow automation but excludes diagnosis, treatment recommendations and clinical decision support: https://healthathon.reskilll.com/guide
- Documentation can omit or misrepresent parts of the underlying goals-of-care conversation, making field-level clinician verification mandatory: https://pmc.ncbi.nlm.nih.gov/articles/PMC11323159/

## Decision gate during the meeting

Proceed with Goals-of-Care Continuity Loop only if all of these are credible:

1. The clinicians can show a recurring missed, delayed or incomplete goals-of-care follow-up workflow, not only a document-retrieval problem.
2. They can identify who enrolls a patient, who owns follow-up, how a due date is set and how completion is recorded today.
3. The clinicians can show the current workflow and a representative de-identified form or note.
4. At least one team member has access to oncology, palliative-care or serious-illness clinicians who can validate and pilot it.
5. The proposed record is a physician-verified conversation summary, not an AI-generated treatment order or substitute for an advance directive.
6. The team can define who creates, follows up, updates, revokes, authorizes and reads each version.
7. The clinicians see enough relevant patients for an operational pilot within 60-90 days.

Use **Consultation Readiness & Patient Journey Review** only if the clinicians prove that rapid retrieval of a completed record is the primary failure and recurring follow-up is not. Pivot to a broader **Cancer Follow-up Recovery Worklist** if goals-of-care case volume or access is too weak. Reject or radically reframe any version that depends on AI deciding treatment, legal enforceability across hospitals, unrestricted emergency access, or unavailable integrations.

## Fallback inside the locked use case

**Cancer Care -> Patient Follow-up & Continuity of Care**

Fallback concept: **Cancer Follow-up Recovery Worklist**

Import the clinic's existing Excel or appointment list, identify explicitly overdue operational tasks using clinician-defined rules, give navigators a prioritized worklist, send multilingual reminders through existing channels, capture why follow-up failed, and maintain an audit trail. It does not infer clinical urgency.

Primary KPI: recall completion rate or no-show rate.

This is safer and easier to pilot, but less differentiated. Indian EMR, clinic-management and WhatsApp products already offer generic reminders and follow-up workflows. Choose it over Goals-of-Care Continuity Loop only if the meeting exposes weak goals-of-care volume, no recurring conversation/review workflow, weak access, legal ambiguity or no realistic pilot.

## Ranking of all official use cases

| Tier | Official use case | Current judgement |
| --- | --- | --- |
| A+ | Patient Follow-up & Continuity of Care | Locked selection and strongest execution choice. Differentiate it through a real clinician-owned continuity loop, not generic reminders. |
| A | Consultation Readiness & Patient Journey Review | The cleanest category for the original PDF's documentation-and-retrieval idea, but changing is justified only if follow-up is not the real failure. |
| B+ | Healthcare Navigation & Access | Strong operational need, but requires a specific institution and journey to avoid becoming a generic chatbot. |
| B | Family & Caregiver Support | Relevant to goals of care, but harder to measure and govern independently. |
| B | Clinic Operations & Patient Flow | Easy to measure, but heavily served by hospital-management systems. |
| B | Financial & Administrative Support | Important, but scheme data, eligibility rules and document processes create maintenance risk. |
| C | Patient Registry & Population Health | Requires integration and can drift into clinical prioritization or risk scoring. |
| C | Long-term Care Engagement | Broad, crowded and difficult to show meaningful change in a short pilot. |
| C | Patient Health Journey Progress | Useful but overlaps generic PHR products and requires data integration. |
| C | Care Journey Companion | Easily becomes broad clinical guidance and is difficult to keep differentiated. |
| C | Patient Education & Digital Engagement | Crowded generative-AI category with accuracy and governance risk. |
| D | Doctor Productivity & Knowledge Assistant | AI scribes and medical content assistants are already mature and crowded. |
| D | Clinic Performance & Practice Growth | Weaker clinical-impact story and commercially crowded. |

## Questions for Dr. Sujay and his colleague

1. Walk us through one real patient from the first intended conversation through every missed, completed or repeated follow-up.
2. Are planned conversations or reviews actually being missed, delayed or left incomplete? How often?
3. Who identifies the patient, who owns the follow-up, and where is the next due date or status recorded today?
4. Where is the note stored, who writes it, and how long does retrieval take?
5. What specialties and hospitals do both clinicians represent, and who can validate or pilot the workflow?
6. Is this record purely a summary, or do you expect it to carry legal or treatment-order authority?
7. Who may update or revoke it, and how should conflicting or outdated versions appear?
8. Can you share the current de-identified template and estimate monthly eligible and missed-follow-up volumes?
9. What 60-90 day pilot access can the clinical team realistically provide?

## Twenty-minute call structure

- Minutes 0-5: clinician explains one real example from intended conversation through follow-up and later use.
- Minutes 5-10: identify follow-up owner, due-date/status source, current failures, record type and legal boundary.
- Minutes 10-14: present Goals-of-Care Continuity Loop and confirm what AI may and may not do.
- Minutes 14-17: test pilot access, monthly volume, baseline and KPI.
- Minutes 17-20: choose one branch: GOC Continuity Loop; broader Cancer Follow-up Recovery Worklist; request category change to Consultation Readiness; or no-go.

## Exact position to communicate

> We are registered under Patient Follow-up & Continuity of Care, which is a strong use case. Dr. Sujay's idea fits it only if there is a recurring failure to complete or revisit these conversations across the cancer journey. In that version, clinicians decide who enters the workflow and when follow-up is due; the product closes the loop, preserves the verified summary and never recommends treatment. If the real problem is only emergency retrieval, we should request Consultation Readiness rather than force the fit.

## Product positioning

Recommended descriptive name: **Goals-of-Care Continuity Loop**

Other usable names:

- GOC Continuity
- Care Conversation Loop
- CareValues Record

Avoid names containing "decision engine," "emergency directive," "living will," "code-status AI," or "treatment navigator." They imply authority the product must not claim.

One-line submission description:

> Goals-of-Care Continuity Loop helps cancer-care teams complete and revisit clinician-planned goals-of-care conversations, preserve each physician-verified summary and keep the latest authorized record available across the care journey.

The product is not a new hospital information system, generic reminder engine, generic AI scribe, legal registry, advance directive, DNAR order, or treatment recommender. Its narrow wedge is closing the loop between an intended conversation, accountable follow-up, a completed verified record and the next care-team handoff.

## Product hypotheses to validate

1. Goals-of-care conversations are planned, continued or revisited across more than one encounter for a meaningful number of patients.
2. Some planned conversations or reviews are missed, delayed, incomplete or not assigned to an accountable owner.
3. The clinic can record a clinician-set next action or due date without AI deciding clinical eligibility or timing.
4. The next clinician often cannot identify or retrieve the latest relevant note quickly.
5. The creator and reader need a short structured view plus access to the full signed note.
6. Patients or surrogates want control over who may access the summary and want a portable copy.
7. Clinicians will accept AI drafting only when the source and every critical field remain reviewable.
8. The hospital can treat the output as a clinical note while keeping legal directives and treatment orders in their existing governed workflows.

If the meeting disproves hypotheses 1, 2, 3 or 8, Goals-of-Care Continuity Loop should not be selected under the locked use case.

## Minimum record schema

The clinical team must approve the schema. The technical team should not invent medical content. A safe starting structure is:

- Patient and facility identifiers.
- Enrolling clinician, follow-up owner and enrolment timestamp.
- Clinician-set next action, due date and reason for follow-up.
- Follow-up status: planned, due, attempted, rescheduled, completed, declined, unreachable or cancelled.
- Outreach attempts, channel, outcome and next action, without storing unnecessary message content.
- Conversation date, time and location.
- Participants, their roles and relationship to the patient.
- Authoring clinician and verifying clinician.
- Patient decision-making capacity as explicitly recorded by the clinician, never inferred by AI.
- Patient-stated values, priorities, fears and acceptable outcomes in the patient's own words where possible.
- Designated surrogate or decision-maker as stated and verified.
- Topics discussed and explicit preferences as documented by the clinician.
- Questions or issues that remain unresolved.
- Related legal or clinical documents, stored as references rather than treated as equivalent records.
- Patient/surrogate authorization status and timestamp.
- Physician attestation and timestamp.
- Record status: draft, verified, superseded, withdrawn or entered-in-error.
- Review date or reason for revision.
- Provenance linking every structured field to its source text.

Audio should not be retained by default. Whether a transcript is retained must be an institution-level decision with explicit consent and a defined retention period.

## Safe AI contract

Input:

- A consented transcript, post-conversation clinician dictation or clinician-entered text.
- The clinician-approved record schema.

Output:

- Strict structured data containing only statements grounded in the input.
- A source span for every populated critical field.
- An explicit `not_stated` value instead of guessing.
- Confidence or uncertainty flags used only to request human review, never to make a clinical decision.

Release rules:

- AI output always begins as a draft.
- No critical field is signed without human confirmation.
- Unsupported content is a blocking defect.
- The original input remains available during review.
- Translation is shown alongside the approved source language.
- The model is never allowed to generate a treatment recommendation or infer a patient's preferred intervention from general values.

## AI evaluation before any pilot

Create clinician-authored synthetic conversations representing straightforward, incomplete, ambiguous, conflicting and multilingual cases. The clinical team creates the reference records.

Measure:

- Unsupported-statement rate.
- Omission rate for required fields.
- Correct `not_stated` handling.
- Critical-field agreement with clinician reference records.
- Time the clinician spends reviewing and correcting the draft.
- Translation corrections.
- Whether the system preserves negation, uncertainty, speaker identity and changes of mind.

The prototype must fall back to a manual structured form if the AI cannot reliably preserve the patient's exact meaning. AI is useful only if it reduces documentation burden without becoming the source of record.

## Technical architecture

Keep the first implementation small:

- Responsive web application or PWA for clinicians.
- Role-based authentication for clinician, coordinator, verifier, reader and administrator roles.
- Relational database for patients, follow-up tasks, outreach outcomes, records, versions, authorization and audit events.
- Clinician-enrolled due/overdue worklist with no automated clinical prioritization.
- Adapter for a bounded Excel/CSV import so the pilot does not depend on an EHR integration.
- Approved outreach templates; outbound messaging is simulated until the institution approves a channel and consent process.
- Append-only record versions; signed content is never updated in place.
- Structured AI extraction service behind a schema-validated API.
- Secure document export for a human-readable copy.
- FHIR-compatible representation using resources such as DocumentReference, Composition, Consent and Provenance where appropriate.
- Separate synthetic demo environment with no real patient data.

Do not add blockchain. Immutability, provenance and non-repudiation can be demonstrated with append-only versions, hashes, signatures and audit events without introducing a technology that does not solve the workflow problem.

Full ABDM integration should be a roadmap item, not a five-week dependency. ABDM provides consent-based record exchange and should eventually be an integration target rather than something the prototype pretends to have completed: https://abdm.gov.in/faqs

The National Cancer Grid is already developing EMR modules including palliative-care workflows and a FHIR-aligned interoperability blueprint. The product should be positioned as compatible with that direction, not as a replacement for it: https://pmc.ncbi.nlm.nih.gov/articles/PMC12057217/

## Access and security model

- A QR code contains only an opaque locator, never patient details or the record itself.
- Possessing a QR code alone must not reveal the note.
- Normal access requires authenticated clinician identity and authorization.
- Patient or surrogate consent and the purpose of access are recorded.
- Any future emergency "break-glass" access requires institution-approved policy, an access reason and immediate audit visibility.
- All access, failed access, export, attestation, withdrawal and revision events are logged.
- Signed records are encrypted in transit and at rest.
- Access is time-limited and revocable where the workflow permits.
- Models and vendors must not train on patient data.
- Demo and judging environments use only fake or fully anonymized records.

## Indian legal and clinical distinctions

These artefacts must remain visibly separate in the product and pitch:

- A goals-of-care summary records a conversation, values and preferences.
- A DNAR decision concerns cardiopulmonary resuscitation and follows a physician-led governed process.
- An Advance Medical Directive is a legally significant document created for future incapacity.
- Withholding or withdrawing life-sustaining treatment follows formal medical and legal processes.

ICMR states that DNAR is distinct from withdrawal or withholding of other life-supporting treatments and from advance directives. It also places final DNAR responsibility with the treating physician after consultation: https://www.icmr.gov.in/icmrobject/uploads/Guidelines/1724842969_icmr_consensus_guidelines_on__do_not_attempt_ijmr.pdf

Ministry guidance describes medical-board and oversight procedures for withholding or withdrawing life support. The application cannot collapse those procedures into a digital checkbox: https://www.mohfw.gov.in/sites/default/files/Guidelines%20for%20withdrawal%20of%20Life%20Support.pdf

The Supreme Court's March 2026 Harish Rana judgment again highlighted the legal complexity and the need for comprehensive legislation. Do not claim nationwide legal enforceability for the product: https://www.sci.gov.in/landmark-judgment-summaries/

## Main failure modes and controls

| Failure | Consequence | Control |
| --- | --- | --- |
| AI or software enrolls the wrong cohort | The system appears to make a clinical eligibility decision | Only a clinician can enrol a patient and set or change the follow-up date. |
| Incorrect due date or owner | A conversation is delayed or assigned to nobody | Explicit clinician confirmation, named owner, overdue escalation and complete audit history. |
| Outreach exposes sensitive context | Patient information is disclosed through SMS or WhatsApp | Minimal approved wording, verified destination, consented channel and no sensitive goals-of-care content in reminders. |
| Wrong patient selected | Another person's preferences are displayed | Multi-factor patient matching and confirmation before signing or viewing. |
| AI invents or reverses a preference | Dangerous false documentation | Source-linked extraction, `not_stated`, field-level review and physician attestation. |
| Old record appears current | Clinician reads stale information | Active/superseded/withdrawn status, visible timestamps and explicit latest-version resolution. |
| Patient changes their mind | Previous preference remains operationally visible | New immutable version, withdrawal workflow and immediate status propagation. |
| Unauthorized QR access | Exposure of highly sensitive information | Opaque locator plus authenticated, authorized access; QR alone reveals nothing. |
| Family members disagree | System appears to resolve a conflict | Mark unresolved conflict and route to clinician-led process; no automated resolution. |
| Record is mistaken for a legal order | Incorrect reliance during emergency care | Persistent banner, document-type labels and separate links to actual orders/directives. |
| Translation changes meaning | Patient authorizes an inaccurate record | Side-by-side source, clinician review and patient confirmation in the understood language. |
| No network during emergency | Record cannot be retrieved | Institution-approved printed or offline copy strategy; do not promise universal online availability. |
| Pilot lacks eligible cases | No credible evidence before judging | Run workflow and usability testing with synthetic cases, while reporting the real-pilot boundary honestly. |

## Three-minute finale demo

1. Show a fragmented mock spreadsheet where an unfinished goals-of-care conversation is buried in free text and has no owner or due date.
2. The clinician enrolls the synthetic patient, assigns a coordinator and sets the next review date.
3. Advance the synthetic clock. The task becomes due; the coordinator records one unsuccessful attempt and a successful reschedule using an approved reminder.
4. At the completed visit, the clinician dictates a synthetic summary.
5. AI structures the draft, marks an ambiguous sentence and leaves an unstated field blank.
6. The clinician corrects the ambiguity, verifies each critical field and signs; the patient or surrogate authorizes it.
7. Show a later review creating version 2 while version 1 remains visibly superseded.
8. The next authorized clinician retrieves version 2, sees its provenance and receives no treatment recommendation.
9. End on the follow-up completion KPI, retrieval time and audit history.

The demo should prove closed-loop follow-up, accountability, retrieval, fidelity, human control and version safety. It should not dramatize a treatment decision.

## Likely judge questions

### Why is AI needed?

Because a sensitive, nuanced conversation currently becomes an incomplete or inconsistent free-text note. AI can reduce the work of structuring, checking completeness and creating an approved multilingual copy. It remains a draft assistant, never the decision-maker.

The worklist, ownership and due-date workflow remains useful even if AI is disabled. That is a strength: AI accelerates documentation but is not used to manufacture a problem or make a clinical decision.

### Why is a paper form not enough?

Paper can improve documentation but does not reliably solve cross-team retrieval, portability, versioning, authorization and auditability. The Indian QI evidence validates the form and explicitly leaves digital implementation and portability unresolved.

### Why not use the hospital EHR?

Some EHRs can support this, but the problem persists across different systems and resource levels. The initial product is a lightweight workflow and interoperable record that can export into existing systems; it is not an EHR replacement.

### How do you prevent hallucinations?

Strict extraction rather than open-ended generation, a source span for every critical field, `not_stated` instead of inference, field-level confirmation and physician attestation.

### Is the record legally binding?

No such claim is made. It is a physician-verified summary of a goals-of-care conversation. Legal directives and treatment orders remain in their governed workflows.

### What happens when preferences change?

A new version is created, the old version is marked superseded, the change is visible, and all events remain auditable.

### What evidence can you produce in 60-90 days?

Due-date completion, contact and reschedule outcomes, documentation completion, time-to-note, authorized retrieval time, stale-version failures, usability and comprehension. Clinical outcomes require a longer governed evaluation.

## Team composition

The programme permits two to five members. A strong four-person core would be:

- Dr. Sujay: clinical lead and pulmonary/serious-illness workflow owner.
- Doctor colleague: ideally oncology, palliative care, emergency medicine or a complementary clinical-validation role.
- Basith: technical/product lead and overall delivery owner.
- Core Devit member: engineering and AI implementation lead.

If a fifth member is added, prioritize a palliative-care, emergency-medicine, hospital quality or health-informatics contributor who closes a real validation gap. Do not add a nominal member merely to fill a seat.

## Competitor and substitution map

- Generic reminder and clinic-CRM systems can send messages and display overdue patients, but usually do not manage the lifecycle, provenance, authorization and version safety of a sensitive goals-of-care conversation.
- Generic AI scribes can transcribe and draft notes but are not centered on goals-of-care provenance, patient authorization, version status and emergency retrieval.
- Hospital EHRs can store notes but may scatter them across sections or fail to expose the latest record across institutions.
- PHR applications let patients carry records but do not necessarily structure or govern a goals-of-care conversation.
- Living-will platforms preserve legal Advance Medical Directives, which are different from a physician-verified goals-of-care summary.
- Paper templates improve completion but do not provide controlled digital retrieval and version history.

Do not claim there are no competitors or that this is India's first digital goals-of-care record. The defensible claim is that the product combines clinician-enrolled follow-up, accountable closure, conversation-to-verified-record workflow, field-level provenance, patient authorization, immutable versions and rapid controlled retrieval.

## Round 1 submission structure

1. One real clinician-observed workflow failure.
2. The affected users and frequency at the proposed pilot site.
3. Direct Indian evidence and the remaining digital/portability gap.
4. Why repeated or unfinished goals-of-care conversations are a continuity-of-care problem.
5. The Goals-of-Care Continuity Loop workflow.
6. The narrow AI role and explicit prohibited functions.
7. Human verification, consent, versions, security and auditability.
8. Five-week prototype scope.
9. One primary operational KPI and the 60-90 day evidence plan.
10. Clinical and technical team access plus honest integration and legal boundaries.

## Required outcomes from tomorrow's call

Do not finish with another vague promise to discuss later. Leave the call with:

- Explicit agreement to stay under Patient Follow-up & Continuity of Care, or a reason to ask the organizers for a change.
- A one-sentence problem statement approved by both doctors.
- One representative real workflow and current failure point.
- Monthly eligible, due, missed and completed follow-up counts, even if initially estimated.
- Named enrolment decision-maker, follow-up owner and due-date/status source.
- The current template or a commitment to provide a de-identified version.
- The role and specialty of the second doctor.
- Named creator, updater, reader and patient/surrogate roles.
- Agreement that the product does not make or execute treatment decisions.
- A realistic validation or pilot setting.
- A primary KPI and an owner for baseline collection.
- A date for the next review and one deliverable per team member.

## Programme timeline and immediate operating window

The saved public-site capture states:

- Doctor and healthcare-professional registration: through 21 August 2026.
- Technology-team registration: 15 August-11 September 2026.
- Matchmaking and team formation: 22 August-18 September 2026.
- Round 1 idea submission: 12-25 September 2026.
- Round 1 evaluation: 26 September-3 October 2026.
- Build Sprint: 5 October-8 November 2026.
- Top 30 announcement: by 14 November 2026.
- Grand finale at IIT Bombay: 28 November 2026.

The authenticated dashboard inspected on 24 August 2026 identifies the actual selection as **Cancer Care / Clinician focused / Patient Follow-up & Continuity of Care**. It exposes no visible self-service edit control. The public registration page only offers account creation, not an edit path. Therefore the current selection should be treated as fixed unless the organizers confirm that they can change it.

The authenticated dashboard also shows registration and matchmaking closing on 25 September, which conflicts with the earlier dates in the saved public capture. Round 1 still shows 12-25 September. Use 25 September only as the current dashboard state and ask the organizers to confirm the authoritative schedule; do not let the later date slow the team's preparation.

The practical deadline is earlier than 25 September. The team should freeze the problem, users, scope and evidence plan during matchmaking, then use the first half of September to prepare the Round 1 concept rather than beginning on 12 September.

## Actions immediately after the call

Within 24 hours:

1. Write the final problem statement using the clinicians' language.
2. Record proceed/pivot/no-go and the evidence behind it.
3. Replace assumptions in this brief with confirmed workflow facts.
4. Obtain a de-identified template or create a clinician-approved synthetic one.
5. Freeze the MVP and no-go list.
6. Draw the current-state and proposed-state workflow.
7. Create the structured record schema with the clinicians.
8. Define the synthetic evaluation set and expected outputs.
9. Assign submission, clinical, product, engineering and evidence owners.
10. Begin the Round 1 concept note; do not wait for the submission window.
