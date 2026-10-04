# Features from the team conversations

Reviewed on 21 September 2026. This is a feature map for the current family-facing app. “Built” below means a local app flow exists in the source; it does not mean a hospital service is connected. Live UI checks belong to the parent task's verification report.

## The short version for the meeting

- **Our care calendar:** medicines, visits, tests, procedures and follow-ups on one calendar. Mark a day's item taken or done.
- **Our report folder:** add the original report, find it by date and open it again.
- **How are you feeling?:** keep a short note of a symptom, how much it bothers you and when it started. Take the diary to the next visit.
- **Your care story:** a timeline of visits, treatment, changes, reports and past history, with the source and any stated reason kept alongside it.
- **What matters to you?:** write what you want the doctor to know and who you want in the conversation.
- **The doctor's care note:** read the reviewed account of the conversation and save a copy.
- **Take to the next doctor:** choose the reviewed note, family checklist, report names and contacts for a printable or downloadable pack.
- **Find support:** a selected palliative-centre directory with phone numbers, directions and source links.
- **Help at home — new component:** organise practical help from “still need to arrange it” through “someone's helping” to “done,” with edit, reopen, undo and a saved handover list.

The central story is still Sujay's: have the difficult conversation with the doctor, keep an understandable account of it, and make it easy to carry into the next encounter. The calendar, diary, history and home-help board give the family a reason to use the app between those conversations.

## Primary material actually read

| Source | Coverage in this pass | What it supports |
| --- | --- | --- |
| `tmp/context-recovery/sources/Saanthvana v 1.0.docx.txt` | Entire 58,270-byte extract, 883 lines, including sections 1–19 and appendices A–F. Repeated bedside-card text treated as one idea. | Sharada's fuller clinical reference; values, practical needs, home support, named people, review history, readable copies and retrieval. |
| `MEETING_TRANSCRIPT_2026-08-24.txt` | Entire 250-line transcript. Speaker names and some wording are imperfect in the supplied transcription. | Sujay's original conversation → source-linked note → doctor/family review → portable copy → later reviewed version workflow. |
| `tmp/context-recovery/chat.txt` | Entire 378-line group export, from 25 August 2026 to Sujay's 20 September 2026, 1:43:36 pm message. Omitted attachments remain omitted. | Later ideas and scope corrections, especially 26/29/31 August and 12/14/18 September. |
| `tmp/context-recovery/sources/Vidhi_EOLC_Sample-Advance-Medical-Directive.pdf.txt` | Entire eight-page, 260-line extract. | Separate documents, chosen people, original copies, distribution and revisions. |
| `tmp/context-recovery/sources/Vidhi-EOLC-Toolkit-FAQ.pdf.txt` | Entire 37-page, 2,075-line extract. It identifies itself as first published June 2024. | Plain explanations, communication, keeping copies findable, nominated contacts and updating recipients when papers change. |
| Shirin messages pasted by Basith in this task | All supplied text from the 20 September discussion, including her questions about the app's basic value, duplicate doctor work, history, treatment changes, languages, fewer clicks and delaying the final PPT. | Everyday value and lower workload. These messages are distinct from the group export. |
| `source-materials/private-context/2026-09-20/shirin-latest/README.md` | Entire written capture of the 5:43–5:54 pm discussion and six-attachment inventory. | One calendar for medicines/appointments/tests/procedures/follow-ups and an original-report history. |
| `source-materials/private-context/2026-09-20/shirin-latest/machine-transcripts/2026-09-20-175305-voice-note.txt` | Entire machine transcript read after the successful local Whisper retry. The original recording was not independently listened to in this review. | Keep doctor-given preparation instructions with an appointment or test, such as instructions about fasting or bringing earlier reports. These examples are reasons to preserve actual instructions, not default instructions for all visits. |
| `source-materials/private-context/2026-09-20/shirin-latest/machine-transcripts/2026-09-20-174931-explanation.txt` | Entire machine transcript read after the retry. Hinglish wording and test names are garbled; exact wording remains uncertain. | Compare reports from different dates. The transcript also mentions ranges; this review does not endorse automatic result interpretation or generating reference ranges. |
| `form.md` and `FAMILY_CAREGIVER_DIRECTION.md` | Read as the current local selection/working-direction record. | Cancer → Patient / Caregiver → Family & Caregiver Support; existing saved portal wording remains unchanged. |

The Word and PDF files were read as text extracts in this pass. This is not a new visual audit of their original layouts. The Vidhi material and clinical treatment tables were used to identify assistive document and communication workflows, not to turn their text into medical or legal instructions. Their legal statements have not been refreshed for September 2026 here.

**Audio/video review boundary:** the first transcription attempt failed, but a later local Whisper retry produced both transcripts and both have now been read. The 27-second voice-note text is clear enough to identify appointment preparation instructions. The 56-second video's exact Hinglish wording and test names remain uncertain. Neither original recording has been independently checked against that text in this review. Older afternoon voice-note references in the user's pasted messages still do not supply their actual speech. The hospital screenshots remain private references; their people, results and medicines must not become app sample data.

## What the sources asked for, and what is covered

| In ordinary words | Source | Current coverage | What is still missing |
| --- | --- | --- | --- |
| “Put today's medicines and appointments together.” | Shirin, 20 September, 5:43–5:44 pm. | `care-calendar.tsx`: calendar, agenda, five event types, explicit medicine instructions, recurring days and day-specific completion. | It records the family's plan. It does not book with a provider, send reminders or decide doses. Calendar editing/removal recovery should be checked separately before describing every action as complete. |
| “Keep the reports somewhere I can find again.” | Shirin, 5:46 pm; Sujay, 26 and 29 August. | Calendar report upload/open/remove with dates; current parent code provides browser storage. | No automatic reading of laboratory values or interpretation. The doctor pack currently includes report names, not the file contents. |
| “Put these two reports next to each other.” | Shirin's video machine transcript, 5:49 pm; exact wording uncertain. | Original-file comparison is being added by the parent task after this source review. | Do not describe it as automatic laboratory comparison, reference-range checking or clinical interpretation. |
| “What did the doctor ask us to do before this visit?” | Shirin's 5:53 pm voice-note machine transcript. | Family calendar entries already have an optional instructions field. | Instructions must be copied from what the doctor actually said. A visit preparation checklist with those instructions and the exact reports to bring is a useful remaining workflow; fasting must never be filled as a universal default. |
| “I want to remember what happened between visits.” | Sujay, 29 August adverse-effects-tracker suggestion. | `family-symptom-diary.tsx`: add/edit/remove/undo, onset date, self-rated severity, filters and text export. | No conversation-based symptom collection, blood-result analysis or automatic message to a doctor. |
| “Show the new doctor our story without starting again.” | Sujay, 26 August rolling care document; Shirin's earlier history and treatment-change comments. | `family-care-story.tsx`: dated history, categories, details, stated change reason and source, edit/remove/undo and export. | No automatic extraction from reports; no inferred discrepancy or reason for a treatment change. |
| “Let me say what matters before we talk.” | Meeting, 24 August; Sharada §6 and Appendix A; Vidhi FAQ 25/53. | `conversation-preparation.tsx`: own notes, optional prompts, chosen people/language and saved copy. | The app does not independently decide treatment preferences or assess a person's ability to consent. |
| “Keep what we actually agreed with the doctor.” | Meeting, 24 August; Sujay, 12 and 18 September. | Simple doctor review and current family care note; existing version history remains in the full workspace. | Live ambient listening, automatic source-linked summary extraction, verified clinical sign-in and actual digital signatures are not connected. |
| “Carry a useful copy into the next hospital.” | Meeting, 24 August; Sharada §19.5 and Appendix E. | `family-handover-pack.tsx`: choose sections, maintain family contacts, preview, download and print. | No delivery receipt, no protected hospital share link, no tracking of who still holds an older copy. |
| “Where can we get palliative support?” | Sujay, 31 August and 14 September. | `care-directory.tsx`: selected centres in three states, service filters, call/directions/source links and links to wider directories. | A complete India-wide service map, confirmed provider availability, verified RMI authorisation/stock and populated patient-group matching are not built. Sujay explicitly described the RMI/NGO data gap on 14 September. |
| “We need help getting through the day.” | Sharada §6.3 and §17; Sujay, 31 August home-care/equipment/support-network list; Vidhi FAQ 51. | New `family-home-help.tsx`: category → optional person/contact/date/note → To arrange/Arranged/Done; edit, reopen, remove/undo, filter and download. Four focused tests, lint and focused TypeScript checks passed. | Parent task handles navigation, shared persistence and Chrome verification. No provider booking or outgoing message occurs. |
| “Can everyone who needs it find the latest paper?” | Sharada §18/19.5 and Appendix E; Vidhi sample pages 6–7; FAQ 29/66/74/77. | Current-note version and download exist. | No family-held list of copy locations/recipients, version given and outstanding replacements. |
| “Help us talk about this without drowning us in forms.” | Meeting, 24 August; Sujay, 25 August education idea; Sharada Appendix A and D. | A simpler family path, short labels and partial Hindi UI exist. | Clinician-reviewed learning cards, checked Telugu/Urdu versions, read-aloud support and actual training evaluation remain open. Existing specialist screens should not be presented as a complete family learning flow. |

## Three strongest new components

### 1. Help at home

**The story:** “We need a ride on Tuesday, someone to stay with Mum for an hour, and someone to sort the paperwork. Who is doing each thing?”

Choose a need, add the name of the person helping if known, and a date if needed. Move the card through **To arrange → Arranged → Done**. Open it to add a phone number or note, edit it, reopen it, or remove it with undo. Download the handover list for the next person helping.

Categories: transport, home help/nursing request, equipment request, paperwork, company and a caregiver break. The category is a request already identified by the family or care team; the app does not prescribe nursing, equipment or a care technique.

This is the strongest immediate addition because it takes work off the family's memory and does not create a form for the doctor. It builds on the practical needs in Sharada §6.3/17 and Sujay's 31 August list. It is a proposed product implementation of those needs, not a verbatim team decision about the screen.

**Complete local outcome:** an unassigned need can become an arranged task, be completed/reopened, survive reload, and appear accurately in the downloaded handover. A saved arrangement is explicitly family-entered; nobody receives a booking or message.

### 2. Who has the latest copy?

**The story:** “The note changed. Dad has the new one, but the folder at home still has the old one.”

Choose a person or place where a copy is kept. Record the note version actually given, when it was handed over, and an optional note about where to find it. Compare these family-entered entries with the current reviewed note. Download the current copy, then separately mark **I gave them this copy**. Keep the older handover history and undo mistaken entries.

This finishes the practical job behind the existing **Save a copy** button. Sharada §19.5 and Appendix E explicitly discuss copies and version control; the Vidhi sample pages 6–7 and FAQ 74/77 also emphasise distributing changes and making documents findable.

**Complete local outcome:** a family can see which copies they have recorded as current and which need replacing, download the current note and record their own handover. It must say **recorded by the family**, never **delivered**, **read by the hospital** or **legally valid** without an actual corresponding process. No automatic deletion of someone else's file and no claim that an exported copy can be remotely revoked.

### 3. Support we are trying to arrange

**The story:** “We called three places. Which one can visit home, what did they say, and who are we calling back?”

Save a centre from the existing directory or add a contact supplied by the care team. Record the service being sought, phone/source link and questions. After an actual call, the family can record the response and a follow-up date. Move the card through **To call → Waiting to hear → Arranged / Could not arrange**. Keep alternatives, reopen a card and export the shortlist.

Sujay's 31 August and 14 September messages ask for centre, home-care and support-group networks and acknowledge fragmented information. A call notebook makes the existing directory useful beyond an outbound link without pretending the missing national datasets have been collected.

**Complete local outcome:** the family can compare their own call notes, keep a follow-up date and know the next action. Availability and fees are only what the family records, with a date and source. A phone link opens the dialler; it does not mark a call completed. No morphine-stock claims, automatic group membership, invented provider listings or provider booking confirmation.

## Ideas read but not converted into app decisions

- The cancer-staging model suggested on 27 August and the anxiety/triage chatbot in the 31 August brainstorm are not assistive diary/calendar features. They are not included in these component proposals.
- The long clinical reference includes treatment limits, monitoring, prognosis and other clinician judgments. Those tables do not authorise the app to make recommendations or fill preferences automatically.
- A digital care note, a separate advance medical directive and a hospital's treatment decisions remain different artefacts. Keeping or labelling a file is not the same as executing or validating it.
- More features do not establish adoption, clinical benefit or the chance of winning. These proposals are concrete workflows to demonstrate and discuss with the team.

## Source gaps that still matter

- `Technical thoughts and questions.docx` is named in the 25 August export but its original remains unavailable in the recovered materials.
- The full 30 August meeting recording/transcript is not in the sources read here.
- The exact wording of Shirin's video, including test names, still needs comparison with the original recording. Her earlier afternoon voice notes remain unavailable in the materials reviewed here.
- Sujay's promised complete patient-support-group, palliative-doctor and morphine-institution lists are not present as verified datasets in these conversation materials.
