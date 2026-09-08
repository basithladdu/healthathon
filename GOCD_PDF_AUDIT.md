# Audit of Dr. Sujay's GOCD proposal

## Source identity

- File: `GOCD (1).pdf`
- Author metadata: Sujay Halkur Shankar
- Created: 20 August 2026 at 20:57 IST
- Format: one-page tagged A4 PDF exported from Microsoft Word
- Security: not encrypted; no form fields or JavaScript
- Size: 21,661 bytes
- SHA-256: `52BDC39EC91317F7E2444D99404C1E02BFAD0C21165994C5D7D7334E69E9C848`

## Proposal as written

The document describes goals-of-care conversations for people with advanced malignancy. It says prior records may be incomplete, unstructured, outdated or difficult to retrieve when a patient reaches the emergency department unable to communicate. Families may then have to repeat distressing conversations.

The proposed response is a physician-led digital record that converts a goals-of-care conversation into a concise structured summary. The record would be patient-authorized, physician-verified, updateable, version-controlled and accessible in an emergency. The primary creator is the oncologist or palliative-care physician; the emergency physician is a reader; a long-term care provider may update it.

The document proposes retrieval time and documentation of an appropriate treatment plan as primary outcomes. It also mentions caregiver anxiety, repeated discussions, healthcare utilization and alignment of emergency care with preferences.

## Best official category for the PDF as written

**Cancer Care -> Consultation Readiness & Patient Journey Review**

That category is the cleanest fit because the central mechanism is structured documentation plus rapid retrieval for the next consultation or emergency encounter.

## Critical scope defect

Phrases such as informing emergency decision-making, documenting an appropriate treatment plan and aligning emergency care with preferences place the concept close to prohibited clinical decision support. A hackathon-safe product must not:

- Recommend ICU admission, ventilation, feeding, resuscitation or any treatment.
- Infer a treatment choice from general values.
- Select patients using prognosis or risk scoring.
- Present the summary as a DNAR order, Advance Medical Directive or living will.
- Resolve contradictions among clinicians, family members or documents.
- Claim nationwide legal authority or unrestricted emergency access.

The defensible core is documentation, consent, follow-up, versioning, provenance, controlled retrieval and auditability. The treating clinical team retains every medical decision.

## Fit to the actually selected use case

The dashboard is registered under **Patient Follow-up & Continuity of Care**. The proposal can fit that use case only if the doctors confirm that goals-of-care conversations are a recurring longitudinal process and that planned conversations or reviews are missed, delayed, unfinished or not updated.

That version is **Goals-of-Care Continuity Loop**:

1. A clinician enrolls the patient and sets the next conversation or review date.
2. A named coordinator owns the due task and records outreach outcomes.
3. The clinician completes the conversation and verifies the structured summary.
4. The patient or surrogate authorizes the record.
5. Every revision is preserved and the latest authorized version can be retrieved.

If the doctors cannot demonstrate the follow-up failure, do not force this PDF into the selected category. Either build a broader cancer follow-up workflow or ask the organizers whether the category can be changed to Consultation Readiness.

## Evidence required from the clinicians

- One real de-identified patient journey.
- Current form or representative synthetic note.
- Who identifies and enrolls the patient.
- Who owns follow-up and where due dates/statuses are recorded.
- Monthly eligible, planned, missed and completed volumes.
- Why and when a conversation is revisited.
- Who creates, verifies, authorizes, updates, withdraws and reads the record.
- Whether any legal or treatment-order authority is expected.
- A realistic 60-90 day validation or pilot setting.
