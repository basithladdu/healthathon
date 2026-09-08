# Health-a-thon clinician meeting analysis

Meeting date: 24 August 2026  
Analysis date: 25 August 2026  
Raw transcript: `MEETING_TRANSCRIPT_2026-08-24.txt`  
Raw transcript SHA-256: `E77A2ED24DBEBC52654DDB6F8ABB3AECC86CE555F3834D2F43C0907255656E36`

## Decision

Keep the registered problem statement:

**Cancer Care / Clinician focused / Patient Follow-up & Continuity of Care**

Do not present the product as a generic reminder app or as a legal advance-directive system. The product is a **clinician-controlled goals-of-care continuity and emergency handoff workflow**.

The meeting validates clinical relevance and strong clinician collaboration. It does **not** yet validate hospital procurement, recurring case volume, production data access, or a live-patient pilot.

Confidence: **high** on the problem-to-workflow fit; **moderate** on hackathon differentiation; **low** on commercial demand until a pilot site and buyer are named.

## What the clinicians explicitly described

1. Goals-of-care discussions occur when a treating clinician believes a patient may have limited survival and the patient/family needs to discuss future care preferences.
2. The discussion may cover home versus hospital care, ward versus ICU, ventilation, dialysis, a time-limited treatment trial, comfort, and family involvement.
3. These discussions are uncommon or unstructured in current Indian practice, and when they happen they may not be documented or available to an unfamiliar emergency physician.
4. The treating clinician must lead the conversation. Software may guide coverage, capture the source, and structure a draft; it must not replace the clinician.
5. The clinical output is a goals-of-care note, not a living will, advance medical directive, DNAR order, treatment order, or independent legal instrument.
6. The patient/family cannot independently change a released record. A new clinician-involved conversation produces a new version.
7. A released version should be acknowledged by the clinician and patient or appropriate surrogate.
8. The receiving or emergency physician is the principal downstream user. The oncologist/primary clinician is the initiator, and a coordinator may support capture or follow-up.
9. The current version should be portable enough to show at another hospital, while access and identity controls remain unresolved.
10. The doctors will supply the clinical framework and simulated scenarios. They proposed approximately 20 simulated conversations.

## Source-faithful workflow

```text
Clinician selects an appropriate patient
    -> guided goals-of-care conversation
    -> voice, transcript, or coordinator-assisted source capture
    -> structured draft with explicit not-discussed and unclear states
    -> field-to-source traceability
    -> physician review and correction
    -> patient/surrogate acknowledgement
    -> physician verification
    -> append-only released version
    -> controlled retrieval by an unfamiliar clinician
    -> current clinical status reconfirmed
    -> later discussion creates a new version
```

## Actors and permissions

| Actor | Allowed responsibility | Prohibited shortcut |
| --- | --- | --- |
| Treating oncologist/clinician | Select patient, lead discussion, edit and verify summary, initiate a new version | Automated prognosis or delegated clinical authority |
| Patient or authorised surrogate | Participate and acknowledge the reviewed record | Self-editing a released clinical record |
| Care coordinator | Schedule, record outreach, assist source capture, route draft to clinician | Publishing or changing treatment-related content |
| Emergency/receiving physician | Retrieve the current verified summary, review source context, reconfirm current status | Treating the note as an automatic order |
| Hospital administrator | Verify staff identities and institutional access | Assuming national identity or cross-hospital trust without integration |

## Resolved product choices

- Use **goals-of-care clinical summary/document**, never "will".
- Use append-only versions to reconcile "non-changeable" with "updatable".
- Start with a hospital-level synthetic pilot, not a national registry.
- Let hospitals verify clinicians in a future deployment.
- Treat ABHA/ABDM as later interoperability work; do not use Aadhaar in the MVP.
- Keep the physician in control of every clinical release.
- Use 20 simulated conversations for the first evaluation.

## Unresolved decisions for the clinicians

1. Exact clinical checklist and mandatory versus optional discussion domains.
2. Whether a family-only conversation is valid and how surrogate authority is recorded.
3. Whether audio is captured, retained, deleted after transcription, or avoided.
4. Whether the MVP input is live voice, an uploaded recording, a pasted transcript, or coordinator entry.
5. What acknowledgement means: recorded verbal acknowledgement, checkbox attestation, stylus signature, or printed signature.
6. Patient matching method for the pilot.
7. Data controller, processor, hosting location, retention period, and breach responsibility.
8. Exact emergency retrieval mechanism outside the originating hospital.
9. Named pilot site, workflow owner, information-security owner, and buyer.
10. Monthly eligible-case volume and current documentation/retrieval baseline.

## Hackathon MVP decision

### Build now

- Two obvious entry paths: **Start/continue a conversation** and **Retrieve a verified record**.
- A deterministic clinician-authored checklist placeholder, clearly pending clinician protocol.
- A preloaded synthetic conversation rather than live ambient recording.
- Source-linked structured fields with `stated`, `not discussed`, and `needs clarification` states.
- Physician review gate and acknowledgement state.
- Append-only Version 1 -> Version 2 demonstration.
- Controlled retrieval with purpose, care relationship, audit event, and reconfirmation warning.
- Supporting follow-up worklist and coordinator outreach.
- A print-friendly current-version handoff sheet.
- A small evaluation panel based on simulated cases.

### Do not claim or build now

- Real ambient AI, speaker diarisation, multilingual ASR, or hour-long audio retention.
- Automated prognosis or patient eligibility.
- Treatment recommendations, DNAR orders, or legal directives.
- Real authentication, real signatures, cryptographic immutability, or ABDM compliance.
- Aadhaar/ABHA integration, hospital integration, or cross-hospital identity federation.
- Real-patient deployment or production security.

## Evaluation plan for 20 simulations

The primary evidence should be workflow performance, not invented clinical outcomes.

| Measure | Definition | Proposed target for simulated evaluation |
| --- | --- | --- |
| Required-domain disposition | Required domains marked stated, not discussed, or needs clarification | 100% |
| Unsupported preference rate | Released fields with no source support | 0% |
| Latest-version retrieval accuracy | Receiving user opens the current released version | 100% |
| Median retrieval time | Patient lookup to current summary visible | Under 30 seconds |
| Physician correction burden | Fields edited before release | Report observed median; do not pre-claim |
| Source trace success | Reviewer can open supporting source for every substantive field | 100% |
| Access audit completeness | Retrievals with actor, purpose, relationship, patient and version | 100% |

Patient distress, hospital cost savings, ICU utilisation, and mortality cannot be claimed from simulated conversations.

## Commercial interpretation

The standalone goals-of-care document is clinically meaningful but may be too infrequent and narrow to support a large company by itself. The durable company should be framed as an **oncology continuity and clinical handoff operating system**, with goals-of-care as its first high-trust module.

Potential later modules include care-gap worklists, patient navigation, caregiver coordination, referral closure, barrier capture, longitudinal milestones, and institutional analytics. The moat would be verified workflow data, institutional integration, disease-specific protocols, and measured closure outcomes—not reminders or the use of AI.

