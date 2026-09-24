# Round 1 answers — 24 September 2026

Entry: **Cancer → Patient / Caregiver → Family & Caregiver Support**.

These revisions follow both meetings this week, the later WhatsApp discussion and the two clinician-authored Word documents. The problem statement remains the team's existing statement. These answers are ready for team review; they have not been pasted into or submitted through the portal.

## Solution name

Saathi (साथी) — Companion

## Your solution

Saathi helps cancer patients and their families keep care conversations, reports and everyday plans together. Its core is a doctor-led conversation about what matters to the patient and what was agreed. AI helps prepare a draft from the recorded conversation. The doctor checks, corrects and signs the Care Note. The patient reviews and signs the same version, chooses family access and can find the latest note quickly through Home or its QR code.

Families can organise reports, prescribed medicines, appointments and symptoms, and give a named person a task. Nearby care contacts help them ask about home support or prescribed morphine. Travel arrangements, bills, assistance paperwork and support groups stay connected to the care journey. AI helps find information in their own documents and present it in their language, with the original available for checking. Clinical decisions remain with the care team.

## How you will build it

The working web app uses Next.js, TypeScript and MapLibre, with PDF export and QR access to a Care Note. We will connect Sarvam AI for Indian-language speech transcription, translation and read-aloud support. An AI assistant will organise consented conversations into drafts and find answers in the patient's own documents, showing the source. Report and prescription details will go through a review screen before they enter a calendar or record. Doctors will correct and sign notes; patients will review the same version and choose family access. Verified accounts, protected shared storage and a record of consent, edits and approvals will support sharing between phones. We will measure documentation, retrieval time and effort during a 60–90 day pilot.

## Links

- Website: https://saathi.wedevit.in/
- Public source code: https://github.com/basithladdu/healthathon
- Questions: https://wa.me/919553321211

## Implementation status for the team

The solution answer describes the proposed product. The current app contains the role journeys, document review, calendar, care-note review and typed-name signing, plus local coordination tools. Sarvam AI, a connected language model, verified accounts, delivered notifications and sharing between separate phones are not connected. A typed name is not a verified digital signature or a legal advance directive.

Sarvam's official documentation supports the proposed [Indian-language speech and language tools](https://docs.sarvam.ai/api/getting-started/building-for-india). Choosing the provider does not imply a partnership with our team or guaranteed credits. See the [family-support research and source map](../docs/pitch/2026-09-24-family-support-research.md) for the reasons behind the supporting features.

## Changes from the earlier answers

- AI-assisted conversation documentation leads; daily care tools support it.
- Added reports, recorded lab trends, medicines, appointments, symptoms, mood and named tasks.
- Connected home support, travel arrangements, costs and paperwork to the family's care journey.
- Added Sarvam AI to the proposed stack and made the doctor-to-patient signing sequence explicit.
- Removed claims of native apps already built, hospital sign-in, guaranteed India hosting, an immutable production audit trail and connected AI.
- Removed the unsupported claim that no comparable Indian product exists.
- Kept dispensing-centre availability separate from the care-centre map.
- Kept the current team and selected use case. The recruitment message shared in the group describes another team.

## Opening story for the deck

A 62-year-old woman with advanced breast cancer starts **best supportive care**. Her doctor discusses future complications, but only her husband attends.

Two months later she becomes unconscious and is taken to the Emergency Department. The doctor asks what she would want if her heart stopped. Her husband and daughter remember things differently. Her son is calling from another city. No one can find what she discussed with her doctor.

A shared, doctor-reviewed record could give everyone the same information before a crisis. It records the patient's wishes and the discussion; it does not make the clinical decision.

**The hardest decisions shouldn't be made without the right information.**

This is an illustrative scenario, not a claimed patient case or a proven outcome. No death outcome is included.
