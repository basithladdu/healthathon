# Meeting evidence map

Prepared 13 September 2026 from `MEETING_TRANSCRIPT_2026-08-24.txt`, `MEETING_TRANSCRIPT_ANALYSIS.md`, `MEETING_SUMMARY_FOR_TEAM.md` and the related GOCD proposal. The transcript is an imperfect auto-transcription without reliable speaker labels. Speaker names below are attributed from the surrounding exchange and should be checked against the recording before being used as formal minutes.

## Direct clinical problem statements

| Approximate speaker | Short quote from the transcript | What it establishes |
| --- | --- | --- |
| Dr. Sujay | "that is where ... the goals of care discussion actually is" | The proposed workflow is a goals-of-care conversation, not a generic patient document. |
| Dr. Sujay | "these kind of discussions are not really held in India" | The clinicians described a practice gap; this is their observation, not a measured prevalence claim. |
| Dr. Sujay | "if they are held they're not documented properly" | Documentation and portability are central failure modes. |
| Dr. Sujay | "none of it is actually ever documented" | The clinician described the current informal nature of the workflow; do not convert this into a quantified baseline. |
| Dr. Sharda | "the person who is there that time at 3:00 a.m. or 4 a.m. doesn't know exactly how to deal with this" | The receiving/emergency physician is a key user of a retrievable summary. |
| Dr. Sharda | "you have to have that difficult conversation all over again with the family" | The patient/family burden and handoff failure are part of the problem. |

## What the clinicians want the product to do

| Approximate speaker | Short quote from the transcript | Product implication |
| --- | --- | --- |
| Dr. Sujay | "we don't want to say the word will" | Do not call the product a living will or Advance Medical Directive. |
| Dr. Sujay | "we say goals of care document" | Use the clinical-summary framing in the deck and form. |
| Dr. Sujay | "the patient and the family cannot update it without ... another doctor being present" | Released records need a physician-controlled update/release gate. |
| Dr. Sujay | "both the patient and the doctor needs to digitally or whatever sign it" | Model acknowledgement/attestation and physician sign-off; do not claim legally valid digital signatures. |
| Dr. Sujay | "we can probably do it at a smaller level" | Pilot scope should begin at a known centre, not a national registry. |
| Dr. Sharda | "the end user is actually the emergency medicine physician" | Keep receiving-physician retrieval as the strongest product wedge. |
| Dr. Sujay | "we go through ... guidelines that cover what all need to be spoken" | The clinicians own the conversation framework and wording. |
| Dr. Sujay | "we'll give you a structure that the AI should follow" | Any future AI proposes structure from a clinician-approved protocol; it does not invent care decisions. |

## Voice, AI and workflow uncertainties

| Approximate speaker | Short quote from the transcript | Unresolved question |
| --- | --- | --- |
| Dr. Sujay | "the AI can prompt the doctor" | Is the first MVP a checklist/prompting tool, transcription, or both? |
| Dr. Sharda | "different languages different accents different dialects" | Voice capture needs multilingual/accent evaluation before any clinical claim. |
| Dr. Sharda | "have a person on the side like a coordinator" | A coordinator-assisted/manual entry path may be safer than ambient listening for the MVP. |
| Team discussion | "they can go I think even up to an hour sometimes" | Conversation length and storage/retention need an explicit design decision. |
| Basith | "this comes to a ... very advanced ... prototype stage" | Speaker diarisation was discussed as future technical work, not a current MVP requirement. |

## Validation and team commitments

| Approximate speaker | Short quote from the transcript | Status |
| --- | --- | --- |
| Dr. Sujay | "we can have about you know 20 simulated conversations" | Proposed validation set, not completed evidence. |
| Basith | "we need to give an outcome measure ... a KPI" | KPI definition was an open follow-up, later represented as proposed measures only. |
| Dr. Sujay | "we'll ... come with the proper KPI list" | No completed clinician-approved KPI list is present in the current repo. |
| Basith | "MVP is basically like minimum viable product" | The team explicitly needed a plain-language explanation of MVP. |
| Basith | "deploy this and have a few simulated conversations" | The current demo should use synthetic scenarios rather than real patient data. |
| Dr. Sujay | "we'll ... share with you ... the structure" | The clinical framework was promised as a follow-up deliverable; the exact promised document is not fully identified in the repository. |

## Decisions that were not actually made

- No final protocol wording was approved in the meeting.
- No hospital, pilot owner, buyer, case volume, baseline or data-governance arrangement was confirmed.
- No legal status of a signed or exported document was established.
- No decision was made that voice/ambient listening must be in the MVP.
- No decision was made to use Aadhaar, ABHA, a national registry or a production identity system.
- No clinical outcome was measured.

## Product decisions derived later

The current prototype and deck translate the meeting into a narrower, safer workflow: clinician-guided conversation; explicit discussed/not-discussed/clarification state; source-linked draft; physician review; acknowledgement; append-only versions; controlled receiving-physician retrieval; and coordinator follow-up. These are product decisions supported by the meeting, not direct quotations from it.
