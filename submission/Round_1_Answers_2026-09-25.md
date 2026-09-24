# Saathi: saved Round 1 answers

Updated on 25 September 2026 in the team's [Healthathon Round 1 draft](https://healthathon.reskilll.com/round1). The saved answers and links were read back in Chrome. The entry has not been submitted, and no PDF or other file was uploaded.

## Entry

- Team: Zeros and Ones
- Track: Cancer
- Primary user: Patient / Caregiver
- Use case: Family & Caregiver Support
- Existing work: Building a new solution
- Solution name: Saathi
- The existing assistive-scope confirmation remains selected.

## The problem you are solving

Cancer patients and families manage care across hospitals and home, but the patient's wishes and everyday care information are often scattered. A goals-of-care discussion records what matters to the patient and the care they would prefer. When it is undocumented or unavailable in an emergency, relatives may disagree without knowing what the patient said. Clinicians face time constraints and uneven training in conducting and documenting these conversations.

Families also coordinate medicines, appointments, tests, transport, home care and expenses across separate records and messages. They may struggle to find nearby palliative care, centres dispensing prescribed oral morphine, or relevant support groups. These gaps leave caregivers handling practical tasks and difficult conversations with limited support. The problem is continuity: keeping the patient's values visible while helping the family act on the care team's instructions, with consent.

## Your solution

Saathi is a palliative care companion for cancer patients and families. The physician leads a goals-of-care discussion; AI offers clinician-approved prompts and drafts a Care Note from the consented recording. The doctor reviews, edits and signs; the patient reviews and signs the same version and chooses family access. A prominent QR code opens the latest Care Note, with earlier versions retained.

Families can organise reports, prescribed medicines, reminders, appointments, symptoms and expenses, assign practical tasks, and find palliative care, prescribed opioid access, support groups, transport and home-help contacts. AI retrieves information from uploaded records, showing the original passage for checking, without interpreting results or recommending treatment.

A proposed 60-90 day pilot will compare patient- and doctor-initiated discussions, written and signed Care Notes per cancer facility, and emergency retrieval time before and after deployment.

## How you will build it

Next.js, TypeScript and MapLibre power the web prototype, with PDF/QR access. We plan Sarvam AI for Indian-language transcription, translation and read-aloud, and Gemini for Care Note drafts and source-linked document search. Doctors edit drafts before signing. PostgreSQL will store consent, version history and an audit trail behind verified role-based accounts and encrypted storage. Encrypted exports, shared access and delivered reminders are planned integrations.

## Prototype or MVP link

https://saathi.wedevit.in/

## Supporting links

https://github.com/basithladdu/healthathon
https://github.com/basithladdu/healthathon/blob/main/submission/Saanthvana_Research_References_2026-09-24.md
https://github.com/basithladdu/healthathon/blob/main/docs/pitch/2026-09-24-impact-plan.md

## Saved state

The portal shows five of six required items complete. The pitch deck is the remaining required item. Supporting links are complete; the video and supporting-file fields are blank.

| Answer | Words | Characters | Limit |
| --- | ---: | ---: | --- |
| Problem | 134 | 956 | 150 words / 1,200 characters |
| Solution | 133 | 968 | 150 words / 1,200 characters |
| Technology | 63 | 469 | 80 words / 600 characters |

The supporting-links field has a 400-character limit. Its 241 characters link to the public repository, all nine research references, and the proposed impact plan.

## What changed

- Replaced Saanthvana with Saathi.
- Kept patient values and physician-led goals-of-care discussions central, alongside everyday family support.
- Made recording consent, doctor review, patient review and signing, family permission, latest-version access and history explicit.
- Included medicines, reminders, appointments, reports, symptoms, expenses, named tasks and support discovery.
- Included source-linked AI assistance and the proposed Sarvam AI and Gemini integrations.
- Added the proposed 60-90 day measures: initiation source, written and signed notes per cancer facility, and emergency retrieval time.
- Removed unsupported uniqueness and guaranteed clinical-benefit claims. The build answer distinguishes the working web prototype from planned integrations.

The earlier clinician-authored answers remain preserved in [form.md](../form.md). This update changes the portal draft and its local text record; it does not deploy app functionality.

