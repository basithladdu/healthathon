# Features from the decks and Sujay’s documents

Read on 21 September 2026. This is a feature map from the saved source text, checked against the current app code. It does not mark proposed features as shipped or treat old slide claims as current results.

The three best additions are **“Still to discuss”**, **“What changed in the doctor’s note?”**, and **“Places I’ve checked.”** They finish work the existing screens start. Source fit: high confidence. Priority order: moderate confidence, to test with the team.

## Everything covered

All 11 PowerPoint text exports in `tmp/context-recovery/sources` were checked. There are 10 byte-unique exports: eight Healthathon decks and two distinct exports for another project. The table records SHA-256 prefixes of the text exports, not the original PPTX binaries. Full slide text and available speaker notes were read for each unique export. Images without extracted text have not been inspected as part of this audit.

| Saved export | Coverage and source-backed features | Text SHA-256 prefix |
| --- | --- | --- |
| `Continuity_Loop_Show_and_Tell.pptx.txt` | All 7 slides. Quick access to the current note; map and handoff; family copy; Hindi and read-aloud; doctor review. Its “send an ASHA or a 108” wording is a historical slide claim, not proof of a dispatch service. | `60DD1EC1CF53DAC0` |
| `Healthathon_Continuity_Loop_Deck.pptx.txt` | All 8 slides and notes. Named person and next review date, unfinished topics, source-linked draft, physician release, preserved versions, retrieval and reconfirmation. Main source for the open-question and version-history additions below. | `0E9B28B373559AAA` |
| `Healthathon_Continuity_Loop_Deck_with_QR.pptx.txt` | All 8 slides and notes. Same core feature text as the preceding deck, with a “SCAN TO TRY” callout. A QR callout adds no new care feature. | `153A4E878A7C3120` |
| `Healthathon_Continuity_Loop_Deck_Visual.pptx.txt` | All 8 slides and notes. Same review and follow-up idea, with explicit “not discussed / needs clarification” states, named owners, source visibility and planned multilingual evaluation. Empty screenshot slots are presentation placeholders, not app features. | `899BD37472182DDA` |
| `Healthathon_Continuity_Loop_Illustrated.pptx.txt` | All 8 slides and notes. The conversation travels between clinic, home and a new team; unfinished topics come back for review; changed preferences create another version. Art is identified in the notes as an illustration. | `4C4BBA9DFCC61338` |
| `Healthathon_Continuity_Loop_Product_Visuals.pptx.txt` | All 8 slides and notes. Same main text as the illustrated deck; slide 6 changes to appointments, patient view and nearby care with historic screenshot notes. Supports a connected calendar and care navigation, but not an automatic hospital booking. | `8D0503AF112B2D31` |
| `Presentation draft.pptx.txt` | All 3 slides. Sujay’s crisis-without-a-documented-conversation story, gaps in finding palliative support, prescribed medicine access and patient support groups. This supports carrying the reviewed note and finishing care navigation. | `86777783D00B95C92` |
| `Saanthvana_Background_Rationale_1.pptx.txt` | All 8 slides and available notes. Expanded version of the previous rationale: undocumented conversations, family uncertainty, nearby care, medicine access and peer support. Slides 6–7 are the clearest source for the support shortlist. | `591A82D2547B1663` |
| `Slide_deck_content_needed.pptx.txt` | All 11 slides and notes. This is **SANCHAY**, a Linux storage project. It supplies no Healthathon feature requirement. | `266D7FBC6C529E37` |
| `Team13ZerosandOnes_Track_2.pptx.txt` | Exact byte duplicate of `Slide_deck_content_needed.pptx.txt`; same hash. Covered by the matching export, not counted as another clinical source. | `266D7FBC6C529E37` |
| `Slide_deck_content_needed (1).pptx.txt` | All 11 slides and notes. Another SANCHAY export; much of the slide body is the same, with rewritten speaker notes. It also supplies no Healthathon feature requirement. | `19FD59A6BC3A0512` |

Two additional clinician sources were read fully:

- [GOCD (1).pdf.txt](<C:/Users/basit/Downloads/CODE/healthathon/tmp/context-recovery/sources/GOCD (1).pdf.txt>): page 1. Sujay asks for a concise, patient-authorised, physician-verified conversation summary that can be updated, versioned and found by the next treating team. He explicitly distinguishes this from an advance directive or treatment order.
- [Healthathon form.docx.txt](<C:/Users/basit/Downloads/CODE/healthathon/tmp/context-recovery/sources/Healthathon form.docx.txt>): all four sections and extracted tables. Contact people, who joined the discussion, preferred language, patient priorities, worries, unfinished personal matters, clinician-recorded discussion and review details. The export repeats table content; repeated passages were counted once. Its clinical decision fields do not become automatic app decisions.

The five longer Continuity Loop decks repeat a shared older product brief; repetition is not five independent clinician requests. Several use the older Doctor / Care Team category. The current supplied selection remains **Cancer → Patient / Caregiver → Family & Caregiver Support**, as recorded in `form.md`. Statistics and legal text in the presentations were not revalidated for this feature audit and are not used as claims about the product.

## What the family can already do in the current code

| Say it in the meeting | Where it exists | What reaches a real local endpoint |
| --- | --- | --- |
| “We have one calendar for medicines, visits, tests and jobs.” | `prototype/app/care-calendar.tsx` | Add a personal item, copy medicine instructions from an existing prescription, repeat daily, mark today’s item done and undo that check. This does not book with a hospital or pick a dose. |
| “You can keep your reports with the date they belong to.” | `prototype/app/care-calendar.tsx` | Add PDF/image files, reopen and remove them. |
| “You can write down how you felt, then show the doctor.” | `prototype/app/family-symptom-diary.tsx` | Add, edit, remove, undo, filter and download personal symptom notes. The note is not sent to a doctor automatically. |
| “Your past visits and treatment changes become a simple care story.” | `prototype/app/family-care-story.tsx` | Add dated events, write the source and documented reason, filter, edit, remove, undo and save a copy. |
| “You can take one pack to the next doctor.” | `prototype/app/family-handover-pack.tsx` | Choose the reviewed note, checklist, report names and contacts; preview, download and print. Report names in this pack are not attached report files. |
| “You can keep the right phone numbers together.” | `prototype/app/family-handover-pack.tsx` | Add, edit, remove and restore contacts. It does not establish anyone’s authority to decide treatment. |
| “You can get your thoughts together before a difficult conversation.” | `prototype/app/conversation-preparation.tsx` | Write priorities, worries, questions, who should be there and a preferred language; save a copy. |
| “The doctor can reuse a note they already wrote.” | `prototype/app/doctor-note-review.tsx` | Paste the source, check the exact copy, record permission and approve a version. |
| “The family can read the latest doctor’s note.” | `prototype/app/simple-care-shell.tsx` | Open and save the latest released note. The simple family screen does not yet expose the earlier releases. |
| “There’s a map and a list of palliative centres.” | `prototype/app/care-near-me.tsx`, `care-directory.tsx` | Search existing public listings and open phone, directions and source links. The curated list contains 12 selected centres across three states. |
| “Family jobs and questions live on the home screen.” | `prototype/app/family-care-workspace.tsx` | Add a task or question, assign a name/date for tasks, mark done or asked and reopen it. There is no full answer-and-follow-up trail for a question yet. |

These are code-observed capabilities during parallel implementation. Browser verification and persistence checks belong to the main task’s verification report. The medication-calendar visual direction came from Shirin’s input; it is not presented here as a feature found in Sujay’s one-page PDF.

## Three additions worth finishing now

### 1. “Still to discuss”

**Story:** “We asked about home care, but didn’t get an answer. Keep it here, put my brother’s name against it, and remind us what we need to ask next time.”

**Source:** `Healthathon_Continuity_Loop_Deck.pptx.txt`, slides 2 and 4; `Healthathon_Continuity_Loop_Deck_Visual.pptx.txt`, slides 4 and 7; the illustrated/product variants, slides 4 and 7. They explicitly retain deferred or unclear topics, a named owner and a review date.

**Full path:** Add question → choose who will follow up and when → record the reply and who said it, or leave it open → set a next step → close it when the family is satisfied → reopen, edit, remove/undo or save the full question-and-answer list.

**Gap it closes:** “Asked” currently means a tick. It does not mean the family got a clear answer or knows what happens next.

Family-entered replies stay labelled as family notes. They never rewrite the approved care note or impersonate a clinician’s response. A calendar date is the family’s chosen reminder, not a recommended clinical review interval.

### 2. “What changed in the doctor’s note?”

**Story:** “We have two copies. Show me the latest one and let me see exactly what changed.”

**Source:** `GOCD (1).pdf.txt`, page 1, especially lines 16–23; every longer Continuity Loop deck’s version/revisit step. The original requirement is an updateable, reviewed note with preserved versions.

**Full path:** Open approved notes → latest copy is clearly marked → pick an earlier copy → compare exact text by section → show additions/removals without interpreting them → save or print the chosen version with its date and doctor’s name.

**Gap it closes:** The data already keeps releases in `summary-state.ts`, but the simpler family page presents only the latest one. This should be a new compact component over existing releases, not a second version store.

The comparison reports wording changes. It does not decide that treatment became better, worse, safer or legally binding. Older copies are plainly marked older; a draft never appears as approved.

### 3. “Places I’ve checked”

**Story:** “I called two places. One said to call back tomorrow. Don’t make me remember who I spoke to or start the search again.”

**Source:** `Presentation draft.pptx.txt`, slide 3; `Saanthvana_Background_Rationale_1.pptx.txt`, slides 6–7. These explicitly raise nearby palliative care, access questions and poor visibility of support groups. The shortlist-and-call-log workflow is a product extension of that problem, not quoted as Sujay’s exact specification.

**Full path:** Pick a centre from the existing dated directory → save it → open its phone/directions/source → write the contact date, who answered and exactly what they said → choose the next step/date → mark the follow-up done or reopen it → edit, remove/undo and download the notes.

**Gap it closes:** The current directory stops when the family leaves for the phone or maps app. There is nowhere to keep what happened afterwards.

The first complete path can use the existing real listings without inventing new providers. A support-group filter needs actual sourced group entries before it can be advertised. A family’s call note does not become verified service availability, medicine stock or a booking. The app never marks a call completed merely because the phone link was opened.

## More useful additions, after those three

| Feature in ordinary words | Source and what is already covered | Complete endpoint worth building |
| --- | --- | --- |
| “Read this to me.” | Show-and-Tell slide 6; preferred-language field in `Healthathon form.docx`. A speech component exists in the older workspace, while the new family copy is text. | Choose available voice, listen to exactly the displayed text, pause/resume/stop. State when a voice is unavailable; keep the original medical wording. |
| “Call this person first; this person if they don’t answer.” | `Healthathon form.docx`, Section I’s primary/alternate contact structure. The new pack already stores ordinary contacts. | Select two contact people, open their phone links and print a small contact card. Call order does not assign medical or legal authority. |
| “These are the papers to take.” | GOCD’s portable record; product-visual deck’s next-visit handoff. Reports and the name-only pack are already present. | Select actual stored files, open each, download the selected originals alongside the reviewed note. Do not label a list of filenames as a bundle of attached reports. |
| “This matters to me, even if it isn’t a medical question.” | `Healthathon form.docx`, Section II, “Unfinished things that matter.” Preparation already stores priorities; tasks already assign jobs. | Convert a chosen personal note into a family task with a name/date, mark done, undo and keep the original note. No automatic clinical interpretation is needed. |
| “Tell the doctor what changed since the last visit.” | GOCD’s changing context; longer decks’ revisit step. Care story and personal preparation already capture this. | Let the family select existing notes for a short visit sheet, edit the order, preview and save it. Reuse those notes rather than ask the family to type them again. |

## Features these sources do not justify claiming as complete

- Automatic ambulance or ASHA dispatch. The Show-and-Tell slide mentions dispatch; it does not establish a connected service.
- Current morphine stock, a verified dispensing institution or a medicine reservation. The rationale identifies an access problem, not a supplied live dataset.
- A patient-support-group match or referral without real sourced group entries and an actual contact path.
- Automatic treatment recommendations, triage, prognosis, ICU decisions or legal directives. The clinician form contains discussion fields; their presence is not permission to automate them.
- Live hospital sign-in, cross-device sharing, protected QR access, speech transcription or messages to clinicians. The older decks list many of these as future work, and a polished local screen does not supply the service.

The practical direction is to give each button an ending: a saved note, a real file, a completed family follow-up, a call the user chooses to make, or an exact reviewed copy they can carry.
