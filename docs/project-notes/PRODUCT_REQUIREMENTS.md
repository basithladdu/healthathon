# Goals-of-Care Continuity Loop product requirements

Version: Hackathon MVP, transcript-grounded  
Date: 25 August 2026

## Product statement

A clinician-guided conversation becomes a source-linked, physician-verified, append-only goals-of-care summary that an unfamiliar receiving physician can retrieve and understand quickly.

The software is an assistive documentation and continuity tool. It is not a treatment order, legal directive, diagnostic system, prognostic model, or autonomous clinical decision-maker.

## MVP users

1. Treating oncologist or primary clinician.
2. Emergency or receiving physician.
3. Care coordinator.
4. Patient or authorised surrogate as an acknowledgement participant.

## P0 functional requirements

### Entry and patient selection

- The first screen must make the two principal jobs obvious: start/continue a conversation and retrieve a verified record.
- All demo patients and identifiers must be visibly synthetic.
- Patient selection must bind every draft, verification, version, retrieval state, and audit event to that patient.
- Changing patient must reset any retrieval unlock and prevent state leakage.

### Guided conversation

- Show a clinician-owned checklist covering the domains supplied in the meeting.
- Every domain must support `discussed`, `not discussed`, and `defer/clarify`.
- The checklist is a demonstration protocol until the clinicians provide the approved framework.
- Do not calculate prognosis or automatically select the patient.

### Source capture and draft

- Use a synthetic, preloaded source conversation for the MVP.
- Map each structured field to an exact source excerpt.
- Never infer a treatment preference from silence.
- Allow physician editing and explicit not-stated/clarification states.
- Label deterministic extraction honestly; do not claim an external AI model is active.

### Verification and acknowledgement

- Keep the record in draft until all required review checks pass.
- Only the simulated physician role may release a version.
- Record patient/surrogate acknowledgement as an attestation state, not a legal signature.
- Prevent release if acknowledgement is pending/declined or required fields are unresolved.

### Versioning

- Released versions are append-only in the demonstrated workflow.
- A changed preference creates a new version.
- Show version number, author, date, acknowledgement, and changed fields.
- Do not show a Version 2 comparison before Version 2 exists.

### Emergency retrieval

- Retrieval begins locked for every patient.
- Require access purpose and care relationship.
- Distinguish emergency/break-glass access from routine access.
- Show only the latest verified version by default.
- Show source context, originating team, timestamp, and unresolved/not-discussed items.
- Show a prominent instruction to reconfirm current clinical status.
- Show that the summary is not a treatment order or legal directive.
- Record every retrieval in the audit trail.

### Operational continuity

- Coordinator outreach outcomes must produce appropriate next states.
- `Discussion completed` routes to draft/review, not `Due today`.
- `Escalate to clinician` creates an escalation state.
- More-time/unreachable outcomes preserve a follow-up due date.
- The worklist supports the clinical record; it is not the primary landing experience.

### Demonstration integrity

- No real patient contact details.
- No claims of real authentication, encryption, cryptographic audit, legal signature, hospital integration, or ABDM compliance.
- All state is browser-local and resets on reload unless a later backend is intentionally added.

## P1 requirements after clinician protocol

- Approved goals-of-care checklist and wording.
- Twenty synthetic scenarios with expected domain labels and source mappings.
- Print/PDF handoff output.
- Configurable clinician/coordinator roles.
- Explicit disagreement, refusal, uncertainty, and surrogate-authority states.
- Evaluation dashboard for simulation metrics.
- Audio/transcript retention decision.

## P2 roadmap

- Real speech capture, transcription and speaker diarisation.
- Multilingual model evaluation.
- Hospital SSO and staff verification.
- Durable backend, audit storage and institutional tenancy.
- HIS/EMR integration.
- ABHA/ABDM interoperability.
- Cross-hospital trust and patient matching.
- Expanded oncology navigation and chronic-disease modules.

## Record state model

```text
Selected
  -> Conversation scheduled
  -> Conversation in progress
  -> Source captured
  -> Draft ready
  -> Clinician review
       -> Needs clarification -> Draft ready
       -> Ready for acknowledgement
  -> Acknowledged
  -> Physician verified
  -> Version published
  -> Retrieved
  -> Reconfirmed or superseded by a new discussion/version
```

## Required record fields

- Patient synthetic ID and originating institution.
- Treating team and verifying physician.
- Participants and relationship/authority.
- Patient-stated priorities.
- Care setting preference if explicitly discussed.
- ICU, ventilation, dialysis and time-limited trial disposition if explicitly discussed.
- Topics not discussed.
- Ambiguities or disagreements.
- Follow-up plan, owner and date.
- Source excerpt/provenance for each substantive field.
- Acknowledgement status.
- Version, author, created time and released time.

## Acceptance gates

- Desktop and 375-390px mobile render without page-level horizontal overflow.
- Primary purpose and two main actions are understandable in the first viewport.
- All reachable routes and forms work with keyboard and touch-sized controls.
- Search, filters, enrolment, outreach, draft review, verification, versioning, retrieval and audit flows operate on the correct patient.
- No app-origin console error or framework error overlay.
- Lint and production build pass.
- Production deployment returns a successful HTTP response and the verified UI.

