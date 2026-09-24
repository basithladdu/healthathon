# How Saathi supports the whole family

24 September 2026. Entry: **Cancer / Patient or Caregiver / Family & Caregiver Support**.

The care conversation is the starting point. The family then needs to find the agreed note, understand the next visit, obtain prescribed medicines and know who is handling practical jobs. Those jobs should reuse the same people, documents and calendar. A second set of forms would defeat the purpose.

## What the research adds

**Fereydooni S, Lorenz KA, Ganesh A, Satija A, Spruijt O, Bhatnagar S and colleagues (2022). “Empowering families to take on a palliative caregiver role for patients with cancer in India: Persistent challenges and promising strategies.” PLOS ONE 17(9), e0274770.** [Read the paper](https://doi.org/10.1371/journal.pone.0274770).

The study analysed 44 interviews with organisational leaders and clinical team members across seven Indian settings. It identifies gaps in caregiver knowledge and training, competing work and financial demands, and emotional strain. Providers described family counselling, community support, and help with food, travel, accommodation and medicines. These are provider perspectives, not direct interviews with a representative sample of families. The study does not establish that an app solves these problems.

Our design response is to keep approved instructions beside the relevant task, give each job a named owner, and make assistance contacts and paperwork easy to find. These are product choices to test with families, not results claimed by the study.

**LeBaron V and colleagues (2014). “An Ethnographic Study of Barriers to Cancer Pain Management and Opioid Availability in India.” The Oncologist.** [Read the paper](https://doi.org/10.1634/theoncologist.2013-0435).

This historical study helps explain why access involves more than putting a medicine on a map. It is already included in the deck's original nine references. It is not evidence of a centre's current supply. Keep any historical consumption statistic separate from present-day service information.

**Pallium India, clinic directory, checked 24 September 2026.** [All locations](https://palliumindia.org/clinics) · [Telangana entries](https://palliumindia.org/clinics/telangana).

The Telangana page lists contact details, care settings and whether morphine is available. That is a usable starting source. A listing does not confirm stock today, eligibility, a specific oral formulation or a completed booking. Retain the source and date, and ask the centre to confirm before a family travels.

## What happens after someone taps a button

These are the intended complete journeys. Existing screens and remaining connections are separated in [Truly Left](../../Truly%20Left.md#every-part-of-the-caregiver-brief).

| The family needs | The useful journey | What counts as finished |
| --- | --- | --- |
| The doctor's account of the discussion | Doctor checks and signs a Care Note. Patient reads and signs that same version, then chooses family access. | Each permitted person can open the right version. A revision needs fresh review and keeps the earlier signatures with the earlier note. |
| A ride to the next visit | Open the appointment, choose a family helper, ask them to arrange travel, and record their confirmation and contact. | The helper accepts and records the actual arrangement. A task labelled “Arrange a ride” is not a taxi booking. |
| Home help | Find the appropriate service, call about location, cost and availability, then save the agreed contact and date. | A provider or family helper confirms the arrangement. Directory discovery alone is not booked care. |
| A prescribed medicine | Open the prescription, find a relevant dispensing contact, call to check availability and required paperwork, and name the person collecting it. | The family records a confirmed collection plan. The app neither selects the medicine nor changes the dose. |
| Help with costs | Keep the bill, paid or pending amount, assistance contact and required documents beside the related visit. | The family can see what is missing and the real application status. No automatic eligibility or funding promise. |
| Understand what to do at home | Show the care team's existing instructions in the patient's language, with the source available. | A person can ask a question or correct the translation. The app does not create clinical advice. |
| Less work between visits | Reuse reports, prescriptions and profile details. Put medicines, appointments and accepted jobs into the same daily plan. | People can see the next step without re-entering it. Reminders must actually reach the chosen person. |
| Emotional support and a break | Keep a private journal, find an appropriate support group, or ask a named person to help for a while. | Sharing remains a choice. The app does not infer consent or enrol someone from their journal. |

## How the two Word documents are being used

- [Healthathon form.docx](../../source-materials/user-provided/Healthathon%20form.docx) supplies the clinician's conversation structure: patient and care-team details, family participants, understanding, values, recorded choices, declarations and signatures.
- `Saanthvana v 1.0.docx` adds review history, contact and practical-support details, along with extensive clinical and legal material. This source stays local because it contains a filled-in personal name; the public repository excludes it.
- Both supplied Downloads files match their local copies in `source-materials/user-provided` byte for byte. The original wording remains intact there.
- We can record what the clinician discussed and what the patient said. Automated treatment recommendations, prognosis scores, medicine doses and legal decision rules from the longer document do not belong in the submitted assistive workflow.
- Patient signing follows doctor signing. Family access or acknowledgement does not automatically make a relative the patient's legal representative. The current typed-name record still needs verified identity and shared storage before separate people can rely on it across phones.

## Sarvam AI in the proposed stack

Sarvam's official [India-focused tools](https://docs.sarvam.ai/api/getting-started/building-for-india) and [speech transcription documentation](https://docs.sarvam.ai/api-reference/speech-to-text/transcribe) support the intended language layer. We propose consented Indian-language transcription, translation and read-aloud support. A doctor checks a conversation draft before signing. A person checks report and prescription text against the original before using it.

The provider is not connected to the app yet. This is a technology choice, not a claim that Saathi has a partnership, credits or guaranteed clinical accuracy. The [revised form answers](../../submission/Round_1_Answers_2026-09-24.md) distinguish the current app from the services to connect.

## How we will know it helps

Use the same eligible patient group before and after deployment. Count requested conversations, conversations actually held, documented notes and signed notes separately. Measure the time to find the latest note, the clinician's review time and whether a family member can complete an assigned job without another explanation. Record failed handoffs and corrections as well as completions. The [60–90 day pilot plan](2026-09-24-impact-plan.md) gives the proposed measures; no outcome numbers have been collected yet.
