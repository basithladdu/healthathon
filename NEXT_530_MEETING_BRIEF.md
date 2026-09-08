# Continuity Loop - 5:30 meeting brief

## Decision after the working session

The primary hackathon use case is now **Patient / Caregiver Facing -> Care Journey Companion**.

The patient and authorised family-facing experience is a calm, read-only view of the latest care-team-reviewed summary, the next planned conversation, and the named people coordinating it. The existing clinician-facing **Patient Follow-up & Continuity of Care** workflow remains the trust and safety backbone: clinicians author, review, version, and release the summary; family members do not edit it; an emergency receiving physician has a separately gated retrieval route.

The product story is therefore:

> A Care Journey Companion for patients and caregivers, powered by a clinician-led, physician-reviewed continuity loop.

Login/OTP, QR or offline patient-held access, hospital integrations, speech transcription, support directories, and maps are future-pilot concepts. None is represented as live in this prototype.

## The meeting in one sentence

We are asking the clinical team to validate a narrow continuity workflow that gives patients and authorised caregivers a clear next step while preserving clinician ownership of the conversation, summary, review, and authorised retrieval.

## What we are building

**Continuity Loop** demonstrates a patient/caregiver Care Journey Companion, backed by a clinician-facing cancer-care workflow registered as:

> Cancer Care -> Clinician focused -> Patient Follow-up & Continuity of Care

It closes one operational loop:

1. A treating clinician decides that a goals-of-care conversation should continue or be reviewed.
2. A named coordinator or care-team owner receives a clinician-set follow-up date.
3. Outreach and rescheduling are recorded without the product judging clinical urgency.
4. The treating physician reviews a source-linked structured draft.
5. The patient or authorised surrogate acknowledgement is recorded as an attestation state.
6. The physician releases a new append-only version.
7. An authorised receiving clinician records a purpose before viewing the latest verified handoff and reconfirms current status.

## What exists today

The current application is a **synthetic, browser-local prototype**. It demonstrates:0

- A clinician-owned conversation checklist.
- A deterministic, source-linked draft with `not stated` and clarification states.
- Physician-only verification and release.
- Patient/surrogate acknowledgement as an attestation, not a legal signature.
- Append-only Version 1 -> Version 2 behaviour.
- Controlled retrieval with access purpose, care relationship, break-glass distinction, acknowledgement, and a simulated audit trail.
- A follow-up worklist with ownership, outreach outcomes, rescheduling, and due states.

The visual refresh in progress is improving the home experience and making the synthetic workflow analytics clearer. Those metrics will represent workflow counts only - not patient outcomes, deterioration, prognosis, or clinical quality.

## What this product is not

State this clearly in the meeting:

- Not a treatment order, DNAR order, advance medical directive, living will, or legal signature platform.
- Not a diagnostic, prognostic, triage, risk-scoring, or treatment-recommendation system.
- Not live AI transcription, hospital SSO, EHR/ABDM integration, encrypted clinical storage, or a real audit system today.
- Not a patient vital-signs tracker, patient chat product, or autonomous care assistant.

The prototype documents, verifies, versions, and retrieves clinician decisions. It never makes them.

## Why the clinical team should care

The pain point is not simply that a note is hard to find. The product is justified only if there is a recurring continuity failure:

- A conversation is planned but never completed.
- A family needs time and no-one owns the next follow-up.
- A discussion is incomplete or out of date.
- Another clinician cannot quickly identify the latest physician-verified summary.

If the real failure is only emergency retrieval of a completed note, the team should discuss whether **Consultation Readiness & Patient Journey Review** is a truer category than forcing the idea into follow-up continuity.

## The clinician decisions we need at 5:30

Leave the meeting with clear answers to these questions:

1. Is there a recurring missed, delayed, incomplete, or unreviewed goals-of-care workflow worth solving?
2. Who decides that a patient enters the workflow today, and who owns the next contact?
3. What exact conversation checklist and wording will clinicians approve?
4. Which information is allowed in the summary, and what must always remain explicitly `not stated` or unresolved?
5. What does acknowledgement mean operationally? Who can provide it, and what must be documented about surrogate authority?
6. Who may release, revise, withdraw, or retrieve a version?
7. What should break-glass access mean in a future pilot, and what review or audit is required afterwards?
8. Can the team provide approximately 20 clinician-authored synthetic scenarios for evaluation?
9. Is there a named pilot site, workflow owner, information-security owner, and buyer after the hackathon?

## Suggested 25-minute flow

### 0-3 minutes - establish the problem

Ask a clinician to walk through one actual de-identified journey: intended conversation, follow-up ownership, missed or delayed step, current note location, and how another care team retrieves it.

### 3-8 minutes - show the product path

Show only this path:

1. Follow-up worklist and named owner.
2. Guided conversation coverage.
3. Source-linked draft.
4. Physician verification and acknowledgement.
5. Append-only release.
6. Receiving-physician retrieval with a documented purpose.

### 8-13 minutes - validate the boundaries

Say explicitly that the system does not recommend treatment, estimate survival, create legal authority, or replace the treating clinician. Confirm that the record is a physician-verified conversation summary.

### 13-18 minutes - validate the clinical protocol

Ask for the approved checklist, protocol wording, unresolved/disagreement states, surrogate-authority documentation, and the review/revision rules.

### 18-22 minutes - define evaluation

Propose a synthetic evaluation set of approximately 20 cases. Measure source grounding, correct `not stated` handling, physician review completion, retrieval time, and usability. Do not claim patient-outcome improvement yet.

### 22-25 minutes - choose the next commitment

Agree on one next action: clinician-protocol review, synthetic-scenario workshop, pilot-workflow mapping, category-change discussion, or a scoped no-go/pivot decision.

## The demo narrative

> A treating clinician starts a goals-of-care conversation with a patient and family. The family needs time, so the clinician assigns a follow-up date and named owner. The coordinator records outreach without altering clinical content. The physician checks a source-linked draft and only then releases a new version after acknowledgement. If an unfamiliar receiving physician needs the handoff, they record why they are opening it, see the latest verified summary, and reconfirm the current situation. The product has made the workflow visible and auditable; it has not made a clinical decision.

## Concrete walkthrough for the meeting

Use these **fictional names and synthetic records** so the team can see who clicks what. They are not real people, patient records, or clinical examples. On a deployed build, the same screens would be opened from the agreed deployment URL, but all current data and actions remain seeded and browser-local until real identity, storage, security, and clinical-system integrations are built and validated.

### 1. Dr. Asha Rao starts the handoff

1. Dr. Asha Rao opens the deployed Continuity Loop URL and selects the clinician route.
2. She chooses the seeded record for **Maya Fernandes** from the follow-up worklist.
3. She clicks **Start guided conversation** and works through the checklist with Maya and her fictional family contact, **Daniel Fernandes**.
4. Where the conversation did not establish an answer, she leaves the field as **Not stated** or **Needs clarification**; she does not ask the product to fill a gap.
5. She selects a follow-up date and assigns **Priya Nair, Care Coordinator** as the owner, then saves the workflow state.

### 2. Priya Nair records outreach

1. Priya signs into the same demo build using the coordinator route; this is a role-selection simulation, not real authentication.
2. She opens **Maya Fernandes** and clicks **Record outreach**.
3. She chooses the seeded outcome **Family requested more time**, adds the factual note **Call planned for 14 September**, and saves.
4. She clicks **Reschedule**, chooses the next seeded date, and confirms the change. The screen shows ownership and timing; it does not calculate urgency or recommend care.

### 3. Dr. Asha Rao verifies and releases Version 2

1. Dr. Asha returns to Maya's record and opens **Review draft**.
2. She opens the source-linked fields, checks each statement against the conversation record, and edits only what the clinician-authored workflow supports.
3. She records the fictional acknowledgement from Daniel as an **attestation**, not a legal signature or directive.
4. She clicks **Verify and release**. The interface creates an append-only **Version 2** and shows the reviewer and release time from the seeded session.
5. If the draft is not accurate, she clicks **Return for clarification** instead; no version is released until a physician reviews it.

### 4. Dr. Omar Khan retrieves the latest handoff

1. Dr. Omar Khan, the fictional receiving physician, opens **Retrieve handoff** from the receiving-clinician route.
2. He selects his care relationship, chooses the seeded purpose **Continuity review**, and clicks **Request access**.
3. He reads the access acknowledgement stating that the summary is not an order, legal directive, or substitute for current assessment, then clicks **Acknowledge and view**.
4. He sees the latest physician-verified version, its source status, and the simulated audit entry. He clicks **Reconfirm current status** after his own review.
5. If this were an emergency, the demo's separate **Emergency hand-off retrieval (Break-glass)** path would require the configured physician role and an emergency relationship, then record that exceptional purpose. It still would not make a clinical decision.

### What the patient and family can see in this meeting

Maya and Daniel are represented only as fictional seeded participants in this clinician workflow. The current application does **not** provide a patient login, patient portal, patient chat, live messaging, or a way to submit a legal directive. In a future implementation, the team must separately define identity proofing, consent, surrogate authority, patient-facing language, accessibility, privacy, retention, and support before any patient access is claimed.

### What to say while clicking

> “Asha decides the conversation needs follow-up. Priya owns the next contact and records what happened. Asha checks the source-linked draft and releases Version 2 only after physician review. Omar declares why he needs the handoff, acknowledges its limits, and retrieves the latest verified version. Maya and Daniel are synthetic participants in this demo. Nothing here is a live patient login, EHR connection, clinical recommendation, treatment order, legal directive, or real audit record.”

## Current visual/product direction

We are taking the product from generic dashboard styling toward a calm, clinician-grade working surface:

- Warm cream and sage surfaces, deep charcoal text, and restrained teal/indigo actions.
- A distinct Continuity Loop brand mark, clearer hierarchy, and deliberate iconography.
- Compact synthetic workflow analytics with text labels, keyboard access, and visible browser-local boundaries.
- Desktop and 375-390px mobile checks for readable density, large touch targets, focus states, and no page-level horizontal overflow.

The product should feel serious and focused, not like a generic AI dashboard. Visual polish does not change its clinical or legal boundaries.

## Evidence and source boundaries

- The clinical team described the workflow and agreed to provide a conversation framework and simulated scenarios: [MEETING_SUMMARY_FOR_TEAM.md](./MEETING_SUMMARY_FOR_TEAM.md).
- Product behaviour and acceptance gates are defined in [PRODUCT_REQUIREMENTS.md](./PRODUCT_REQUIREMENTS.md).
- The longer decision analysis, pilot assumptions, and known gaps are in [MEETING_BRIEF.md](./MEETING_BRIEF.md) and [MEETING_TRANSCRIPT_ANALYSIS.md](./MEETING_TRANSCRIPT_ANALYSIS.md).
- The National Cancer Grid's [End of Life Care guideline](https://www.ncgindia.org/assets/palliative-care-guidelines/approach-to-managing-end-of-life-care.pdf) is reference material for clinician review. It is not an instruction to automate prognostication, treatment, medication, or resuscitation choices.

## Final ask to the clinicians

> Confirm the real continuity failure, approve the clinical checklist and authority model, supply synthetic test cases, and name the next pilot-validation owner. We will build the workflow and evidence layer around clinician decisions; we will not manufacture clinical authority through software.
