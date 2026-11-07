# Saanthvana: the problem and the smaller product

Working direction, 20 September 2026. This is a proposal for the team meeting, not a change to Sujay's saved portal answer. See `form.md` for the original wording.

## The problem in one sentence

After a cancer visit, the family needs a clear account of what was discussed, what happens next, who is helping, and where to get support.

A schedule by itself is not a reason to install another app. The product must make something easier than the hospital's existing handout, phone calls or family WhatsApp group. That is the assumption to test, not an established market finding.

## How Sujay's work fits

The family-facing product supports care between visits. Goals-of-care conversations are its distinctive clinical workflow: understanding what matters to the patient, recording what was actually discussed, and carrying a doctor-approved copy into the next encounter. They are not a questionnaire that every family must complete on every visit.

The team's messages support a focused approach:

- 25 August: Sujay explicitly raised the unconventional fit and uncertainty about judging. This was his opinion, not a probability estimate.
- 26 August: he proposed a concise rolling care document that clinicians could expand when needed.
- 31 August: the wider palliative-support list was a set of ideas, with an explicit warning that adding everything might dilute the main purpose. Another teammate asked for one primary feature.
- 12 September: Sujay identified his distilled `Healthathon form.docx` as the guide; Sharada's longer document is the clinical reference.
- 14 September: Sujay suggested starting with palliative centres while verified RMI and NGO data were still missing.
- 18 September: he asked for a stronger goals-of-care focus and location support. Basith asked for colloquial, simple language.
- 20 September: Basith explicitly requested removal of the landing page, fewer clicks and less doctor workload. Shirin questioned the additional value over existing hospital instructions and proposed minimal design, languages and fewer clicks.

## What is in the main app now

| Family need | Main interaction | Boundary |
| --- | --- | --- |
| What happens next? | Next visit visible on opening the app | Sample appointment data |
| What do we need to do? | Tasks with a single completion control; add in place | Session-only; assigning a name sends no message |
| What should we ask? | Add questions on the same screen | Personal checklist, not clinical advice |
| What did the doctor say? | One click to the latest approved care note; save a copy | Earlier versions remain in state; no automatic translation of medical text |
| What matters to the patient? | One open note with optional conversation prompts | Private preparation does not alter the approved note |
| Where is help available? | Public provider search, contact and directions | Twelve selected sourced entries; no verified medicine stock |
| How does the doctor help? | Review an existing note, record permission, approve | No mandatory eight-domain form in the main path; no connected EHR or live transcription |

The landing page and role-demo toolbar have been removed from the main path. A plain view selector replaces the role launcher. The default screen uses a clearly labelled sample case. English/Hindi labels cover the new family home, care note and preparation screens. The directory and clinical review still use English, and patient-entered/clinical text retains its original language. This is partial localisation, not a claim of a fully multilingual product.

The doctor can use an existing visit note without re-entering history, medication or preferences into separate fields. Pasted text is copied verbatim. The app does not claim to extract clinical meaning with AI. Permission is not preselected, and approval requires the doctor to explicitly review the copy. Editing a note requires fresh review. Local UI role switching is not real hospital authentication.

Existing specialist tools remain in the full workspace for continuity, outside the simplified family/doctor path. They are not part of the proposed competition workflow and have not been clinically validated.

## What we are deliberately not adding now

- A second chemotherapy scheduling system that duplicates the hospital's work.
- A form for every conversation, or a requirement that doctors maintain a parallel medical record.
- Automatic comparison of recommended versus actual treatment, legal conclusions or explanations invented by software.
- An anxiety/triage chatbot that decides whether a patient needs treatment.
- Unverified morphine availability, hospital integration, live alerts or consent controls presented as working services.

Shirin's history and treatment-change concerns belong in the clinical source and record history. If a treating clinician documents a change and its reason, the app can preserve it and show its provenance. It should not infer a deviation or a legal explanation from incomplete information.

## Niche, audience and selling

Cancer families are a focused audience. A product does not need to be useful to every person to be useful or sellable. A goals-of-care-only tool may be used infrequently; a caregiver home makes the surrounding support easier to reach. Adding generic task management alone is not differentiation.

The plausible customer to investigate is a cancer/palliative-care service that already handles family follow-up, with access for its families. This is a commercial hypothesis, not verified demand. No willingness-to-pay study, buyer commitment or reduction in workload has been established. Winning cannot be predicted from scope or a polished interface.

High confidence: the simpler workflow maps more clearly to Family & Caregiver Support. Moderate confidence: combining day-to-day coordination with a reviewed conversation record is worth testing. Unknown: adoption, revenue and selection probability.

## What the meeting needs to settle

1. Which recent real situation was not solved by the hospital's schedule or WhatsApp?
2. What note already exists, who writes it, and can it be reused without duplicate typing?
3. Who does the work today: patient, family member, nurse, coordinator or doctor?
4. What must the doctor check, and what can the family handle independently?
5. Would a clinic try this with a small number of consenting families, and what would it need to see to keep using it?

Suggested evaluation: compare the existing process with the new flow for finding the next step, identifying the responsible person and locating the latest agreed note. Count corrections, missed information and staff time; do not use click counts as a substitute for comprehension. No performance target is represented as a result.

## Deck status

An eight-slide draft was exported earlier in this session. It predates the latest simplification and is not a final submission deck. Following the latest feedback, final deck work should follow the team's product decision. Original clinician decks remain untouched. Nothing has been uploaded or submitted.
