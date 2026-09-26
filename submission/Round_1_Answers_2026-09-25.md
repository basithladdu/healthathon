# Saanthvana: saved Round 1 answers

Updated on 27 September 2026 as the team's local [Healthathon Round 1 draft](https://healthathon.reskilll.com/round1). The earlier portal save was read back in Chrome on 26 September. The entry has not been submitted, and no PDF or other file was uploaded.

## Entry

- Team: Zeros and Ones
- Track: Cancer
- Primary user: Patient / Caregiver
- Use case: Family & Caregiver Support
- Existing work: Building a new solution
- Solution name: Saanthvana
- The existing assistive-scope confirmation remains selected.

## The problem you are solving

Cancer patients and families manage care across hospitals and home, but the patient's wishes and everyday care information are often scattered. A goals-of-care discussion records what matters to the patient and the care they would prefer. When it is undocumented or unavailable in an emergency, relatives may disagree without knowing what the patient said. Clinicians face time constraints and uneven training in conducting and documenting these conversations.

Families also coordinate medicines, appointments, tests, transport, home care and expenses across separate records and messages. They may struggle to find nearby palliative care, centres dispensing prescribed oral morphine, or relevant support groups. These gaps leave caregivers handling practical tasks and difficult conversations with limited support. The problem is continuity: keeping the patient's values visible while helping the family act on the care team's instructions, with consent.

## Your solution

Saanthvana supports cancer patients, families and care teams across visits. The physician leads a goals-of-care discussion. With consent, AI offers clinician-approved prompts and drafts a Care Note from the recording. The doctor reviews, edits and signs it. The patient reviews and signs the same version and chooses which family members can access it. A QR code opens the latest Care Note, with earlier versions in its history. Patients and families keep reports, prescribed medicines, reminders, appointments, tests, symptoms and expenses together. They can assign practical tasks and find palliative-care centres, hospitals that dispense prescribed oral morphine, support groups, transport and home-help contacts. AI searches uploaded records and shows the original passage for checking. It does not interpret results or recommend treatment. In a 60–90 day pilot, we will compare patient- and doctor-initiated discussions, signed Care Notes per centre, and emergency time to retrieve the latest note before and after deployment.

## How you will build it

Next.js and TypeScript power the web app; MapLibre shows palliative-care centres and routes, with PDF and QR access. Sarvam AI will support speech-to-text, translation and read-aloud in Indian languages. Gemini will draft Care Notes from consented conversations and search uploaded reports with links to source passages. Doctors review, edit and sign every note. PostgreSQL will store consent, family access, version history and audit records. Verified roles, encrypted storage, file sharing and reminders connect care workflows.

## Prototype or MVP link

https://saanthvana.wedevit.in/

## Supporting links

https://github.com/basithladdu/healthathon
https://github.com/basithladdu/healthathon/blob/main/submission/Saanthvana_Research_References_2026-09-24.md
https://github.com/basithladdu/healthathon/blob/main/docs/pitch/2026-09-24-impact-plan.md
https://drive.google.com/drive/folders/1WPtw-7iEZoMObEr4pzd9-Gziyo9D3VYV?usp=drive_link

## Saved state

The last confirmed portal view showed five of six required items complete, with the pitch deck remaining. This browser now opens the portal sign-in page, so the updated website and Drive links below are saved here but have not been copied to the portal.

| Answer | Words | Characters | Limit |
| --- | ---: | ---: | --- |
| Problem | 134 | 956 | 150 words / 1,200 characters |
| Solution | 150 | 1,031 | 150 words / 1,200 characters |
| Technology | 75 | 529 | 80 words / 600 characters |

The supporting-links field has a 400-character limit. This local version uses 330 characters to link to the public repository, research references, impact plan and Drive folder. The folder is empty for now; the team plans to upload the video this weekend.

## What changed

- Changed the solution name to Saanthvana.
- Kept patient values and physician-led goals-of-care discussions central, alongside everyday family support.
- Made recording consent, doctor review, patient review and signing, family permission, latest-version access and history explicit.
- Included medicines, reminders, appointments, reports, symptoms, expenses, named tasks and support discovery.
- Included source-linked AI assistance and the proposed Sarvam AI and Gemini integrations.
- Added the proposed 60-90 day measures: initiation source, written and signed notes per cancer facility, and emergency retrieval time.
- Removed unsupported uniqueness and guaranteed clinical-benefit claims. The build answer names Sarvam AI and Gemini as planned integrations.

The earlier clinician-authored answers remain preserved in [form.md](../form.md). The portal still needs its pitch deck before the entry can be submitted. The custom domain `saanthvana.wedevit.in` is live. The Drive folder link is ready, but the video has not been uploaded yet.

