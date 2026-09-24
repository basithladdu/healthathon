# Saathi: Round 1 form

## Latest answers for review — 24 September 2026

Use [Round 1 answers](submission/Round_1_Answers_2026-09-24.md) for the revised **Saathi** name, solution and technology answer. The entry remains **Cancer → Patient / Caregiver → Family & Caregiver Support**. The newer team-page capture supplied by Basith shows five members, including **Shirin Ayub**. The four-member capture and the doctors' original answers remain below as historical source material.

**Proposed technology answer:** The working web app uses Next.js, TypeScript and MapLibre, with PDF export and QR access to a Care Note. We will connect **Sarvam AI** for Indian-language speech transcription, translation and read-aloud support. An AI assistant will prepare drafts from consented conversations and find information in the patient's own documents, showing the original for review. Doctors correct and sign notes; patients review the same version and choose family access. Verified accounts, protected shared storage and a record of consent, edits and approvals will connect the three roles across phones. We will measure documentation, retrieval time and effort in a 60–90 day pilot.

This is an updated draft, not a claim that the portal was edited or submitted. The AI and shared-account services still need connecting.

## Earlier saved form — preserved as supplied

Current source: the user's dashboard capture supplied on 20 September 2026. It reports a draft saved on 18 September at 17:21. The saved answers below are preserved as supplied, not independently validated claims or revised portal answers.

This file supersedes `submission.md` for the current selection and saved answers. `submission.md` preserves the older 13 September draft. `Healthathon form.docx` is the separate clinician-authored questionnaire that guides the goals-of-care discussion and summary.

## Confirmed entry

- Team: Zeros and Ones
- Track: Cancer
- Primary user: Patient / Caregiver
- Use case 03 of 06: Family & Caregiver Support
- Solution name: Saanthvana
- Existing work selection: Building a new solution
- Pitch deck: 6–8 slides, PDF or PowerPoint
- Problem field: maximum 150 words and 1,200 characters, confirmed by the user's second paste

## Team, confirmed by the latest supplied team-page capture

| Person | Current position |
| --- | --- |
| Shaik Abdul Basith | Joined; team leader and displayed technical lead |
| Sharada Vinod Kutty | Joined; clinician and displayed doctor partner |
| Sujay Halkur Shankar | Joined; clinician |
| Shaik Muhammad Awaiz | Joined; technologist |
| Shirin Ayub | Prospective fifth member, as stated by Basith; not yet shown as joined; role and ideas not yet supplied |

The portal shows four of five members and a Round 1 draft with 9/10 required answers (90%), with the pitch deck still missing. It displays a 25 September deadline. The role dropdown text and submission warning remain inconsistent with the displayed assigned leads; no role assignments have been changed here. No invitation has been sent to Shirin.

## Selected use case, supplied by the user

Tools for families who coordinate care - with consent, clarity and less stress.

### The current reality

- Family members and caregivers play a vital role in supporting patients with long-term conditions such as cancer, diabetes and maternal health. They often help manage appointments, medications, lifestyle modifications, home care, transportation, finances and emotional well-being, while serving as an important link between patients and healthcare providers.
- Despite their central role, caregivers often have limited visibility into the patient's care journey. They may not fully understand treatment plans, upcoming milestones, warning signs, care instructions or changes in the patient's condition, making it difficult to provide timely and informed support.
- Communication is frequently fragmented across patients, caregivers and healthcare providers. As care becomes more complex, particularly during prolonged treatment or recovery, the lack of coordinated information and communication can increase stress for both patients and caregivers and impact continuity of care.

### What the programme is looking for

- Design solutions that empower family members and caregivers to actively participate in the patient's healthcare journey while respecting patient consent, privacy and existing clinical workflows.
- Enable caregivers to stay informed about the patient's care plan, appointments, medications, treatment milestones, care requirements and other relevant updates to provide timely and coordinated support.
- Strengthen communication, care coordination and shared care planning between patients, caregivers and healthcare providers through personalised education, task management, timely alerts and other collaborative care capabilities.

## Saved problem answer, authored by Sujay

Patients with cancer have high morbidity with significant symptom burden. While oncologists treat malignancy, the patient is rarely treated holistically. This increases patient and caregiver burden and anxiety due to unmet physical, emotional, and practical needs. This is especially true in times of crisis when families are unprepared about patient values and hesitate while consenting for aggressive treatment protocols. Goals-of-care discussions are inconsistent and undocumented largely owing to lack of training in conducting such discussions and time-constraints. Adding to this, the Indian Palliative Care coverage is sparse and unequally distributed; the access to oral morphine is hindered by lack of know-how about availability on both the physician and patient part, along with lack of coherent data on registered medical institutions. Awareness of nearby palliative care centres and available patient support groups is also poor. The patient and family are left to navigate these issues alone during their most vulnerable moments.

## Saved solution answer

Saanthvana - An integrated palliative care companion for cancer patients and their families, centered on a goals-of-care discussion (GOCD) tool: A clinical documentation of patient values to guide clinical care during a crisis. This is a physician-led, AI-guided conversation captured via ambient listening and converted into a signed, version-updatable document, stored both offline and online, for the family to present to emergency physicians during a crisis. Having this conversation early reduces caregiver anxiety and prevents distressing decisions being made under stress, while keeping clinical care aligned with patient values. Supporting this tool are location-based modules: Nearest palliative care centers, institutions registered for oral morphine dispensing, and matching to relevant patient support groups. To our knowledge, no palliative-focused companion currently exists in India; introducing one could help shift cancer treatment from a predominantly medical model toward a more patient-centered approach.

## Saved build answer

Native apps: Swift for iPhone, Kotlin for Android, offline-first for ASHA workers in low-signal areas. Node API on AWS Mumbai so data stays in India; Postgres with an audit trail nobody can edit; encrypted, with hospital sign-in and role-based access. Hindi and regional voice notes via Bhashini and Whisper, doctor-reviewed before saving. ABHA/ABDM and FHIR to plug into hospital systems; WhatsApp and SMS updates for families. Prototype live today: Next.js, TypeScript, MapLibre, QR handoff.

## Scope and observed form status

The supplied portal text excludes diagnosis, treatment recommendations, clinical decision support, clinical risk scoring, interpretation of medical data and autonomous clinical advice. It allows assistive tools that keep a human in control.

The capture shows five of six required items done, pitch deck not started and optional links not started. It also requests named doctor and technical leads. These are observations from the supplied capture, not a claim about later portal changes. No portal values were changed during this context update.

## Working interpretation

Latest user steering: the newly supplied category screen reconfirms Cancer → Patient / Caregiver → Family & Caregiver Support. Keep these selections for the family-facing workflow. The user requested removal of the landing page, ordinary language, fewer clicks and no parallel documentation burden for doctors. See [FAMILY_CAREGIVER_DIRECTION.md](FAMILY_CAREGIVER_DIRECTION.md) for the combined proposal and Shirin's feedback. The saved answers above remain unchanged.

The target user is the patient/family caregiver. The doctor leads the clinical conversation and reviews the document. Saanthvana's focused problem is helping families prepare for that conversation, understand the reviewed account and carry it into later care encounters with the patient's permission. Palliative-care navigation supports this core workflow.

The saved answers need review before submission: several clinical/national-effect claims are broader than the verified sources, the statement that no comparable Indian companion exists has not been established, and the native/cloud/AI/integration stack is planned rather than implemented. See `CONTEXT_RECOVERY_2026-09-20.md` for the source audit and `SAANTHVANA_VERIFICATION_2026-09-20.md` for actual prototype status. This commentary does not alter Sujay's saved wording above.
