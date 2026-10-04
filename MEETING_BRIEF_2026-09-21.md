# Saanthvana — meeting card, 21 September 2026

**We help a cancer patient's family keep track of the next thing to do, what has already happened, and what the doctor has agreed.**

Keep **Cancer → Patient / Caregiver → Family & Caregiver Support**. Confidence: high, based on the supplied use case and saved team selection. The family is the everyday user. Sujay's goals-of-care work gives them a clear, reviewed note to carry forward. Families should not have to complete a long clinical form before using the calendar or keeping a report.

## The features, in normal words

These are connected in the local app. The separate status sheet records which ones have actually been exercised in Chrome.

| Feature | What the person can finish |
| --- | --- |
| **Open it and get on with the day** | A short sign-in opens family Home. The landing page is gone. Cream, sage, lavender and terracotta replace the colder presentation. This sign-in is local, not a real online account. |
| **One calendar for everything due** | Medicines, appointments, tests, procedures, follow-ups and family tasks appear by Day or Week. Add or edit dates and existing care-team instructions; save or cancel; mark the day's item taken or done; undo it. Daily repeats keep each day's completion separate. |
| **What should we do before the visit?** | Keep the doctor's exact preparation instructions with the appointment. See who gave them and who copied them here. Edit, remove or undo; the same saved instructions appear in the family calendar and doctor's view. |
| **Keep the actual reports** | Add a dated PDF or image, open or download the original, and put two reports beside each other. Remove a file and undo the removal. The app does not interpret the results. |
| **Cancer care at a glance** | See the diagnosis and care team already recorded, recent care-story entries, symptom notes and report counts. Each card opens the actual notes or files behind it. |
| **Your care story** | Keep a dated record of visits, treatment, changes, reports, milestones and past history. Add the source and a change reason as documented, correct an entry, or download the story. These remain family-added notes. |
| **How have you been feeling?** | Note a symptom, how it felt and when it started. Look back by day or week, edit, remove, undo, and download the diary for a conversation. |
| **Find support, then keep track of the call** | Search the palliative-care directory, open its phone, directions or source link, and save a place. Record whom you spoke to, their exact reply and the next follow-up. Mark that follow-up done or reopen it. Listed services and dated family call notes stay separate. |
| **Who is sorting the help at home?** | Keep a list for a ride, home help, equipment, paperwork, company or a caregiver break. Add the helper, phone and date; move it from needs arranging to arranged to done; reopen, edit or download it. |
| **Help with costs** | Add expenses, see what is marked paid or unpaid, keep an existing fundraiser link and tick off paperwork. Edit, remove, undo and download the list. It keeps track of costs; it does not collect money. |
| **What did the doctor agree?** | Read the released care note with its doctor, date and version. The doctor can reuse existing written material and review it before release. Family observations stay separate from approved wording. |
| **The doctor's short worklist** | Find a patient, open notes needing review, see approved notes and switch between today's and upcoming visits. Counts come from the local records. |
| **What changed in the note?** | Open previous released versions, compare their actual wording and save a version or comparison. |
| **Who has the latest copy?** | Download the current note, then separately record who you gave it to and when. See whose last recorded copy is older. Correct or remove an entry, undo, and keep the handover history. This records what the family says happened; it cannot know whether somebody read a copy. |
| **Things we still need to ask** | Add a question, mark it asked, save the reply with who said it and the date, set a next step, close it or bring it back. Download the list before a visit. |
| **Take this to the next doctor** | Preview and download or print a care pack. It starts with just the reviewed note. Choose whether to include the family's checklist, report-name list and contacts. Original report files travel separately. |
| **My comfort space** | Tap how you feel, look back at your check-ins, remove and undo, or open the journal. An “In pain” check-in can open the symptom diary. |
| **Say it instead of typing** | Start recording, stop, listen and save a thought. A title or typed thought is optional when there is audio. Download the original recording, edit the text, remove or undo. Text entry remains available if the microphone is unavailable. Real microphone use still needs checking. |

**My test results is connected:** tap a test such as Haemoglobin or RBC to see its dated results, exact units and a chart when comparable numeric entries exist. Values, printed ranges and High/Low flags are copied manually from the report. Open the original, edit, remove, undo or download the history. The app does not invent ranges, extract results automatically or interpret them.

**The visual update is in:** 11 colourful illustrations across the feature cards, a stronger sunset palette, illustrated cancer overview, and small hover/press movements. The calendar stays first; sign-in opens directly. These latest changes were not followed by more tests or builds, as requested.

English and Hindi controls are present across the new family tools. Full language quality and every phone screen still need checking.

## A short walkthrough

1. Sign in and show the day's calendar. Add an existing instruction and mark an item done.
2. Open two original reports together. Show the dated care story.
3. Add a symptom, edit it, remove it, undo, then reload to show the saved entry.
4. Open the reviewed note and download **Take to the next doctor**.
5. Tap a feeling in comfort space and open the journal. Show the support shortlist, help-at-home list, costs and unanswered questions as the other connected tools. Their complete click-through checks are still pending.

## What we can honestly say today

Chrome checks cover sign-in, calendar interactions, symptom add/edit/remove/undo and reload, care-story edits, handover export and comparing two original reports after reload/hot reload. A “Peaceful” check-in saved and **Record a thought** opened the journal. The cancer overview shows the selected person's actual saved notes and files. The doctor's worklist, Today filter and route into note review were opened; a saved sample instruction appeared unchanged in the family's calendar, with its source. Costs and several full edit/remove/undo workflows still need Chrome checks.

Changes now save on this device; the old “current session only” description is outdated. The app tries IndexedDB and uses localStorage when needed. This Chrome profile is using the fallback. Storage can fill up, especially with recordings, so failed saves show a download prompt. This is not cloud backup or family sync. The cases remain sample cases.

No cloud authentication, AI transcription, real notifications, bookings or payments are connected. A real microphone is untested. This update has not been submitted, pushed or deployed. No new build was run after the request to stop builds; one code typecheck passed before the ongoing lab/calendar additions. See [the implementation status](SAANTHVANA_IMPLEMENTATION_2026-09-21.md).

## Decisions for the meeting

- **First family to try it with:** choose one cancer-care setting. A focused starting group is useful; usefulness to every condition is not established.
- **Who enters what:** families copy existing dates and instructions. Agree on the smallest doctor action needed to review a changed care note.
- **What to show in the pitch:** one family completing a few real tasks. Agree on that story before finalising the required 6–8 slides. The original form answers and decks remain unchanged.

Category fit: **high confidence**. Day-to-day usefulness: **moderate confidence**, pending family and clinician feedback. Willingness to pay, clinical outcomes and chance of winning: **unknown**.

## What context we actually have

The group export reaches **20 September, 1:43:36 pm**. Shirin's newer **5:43–5:54 pm** discussion and all six attachments are separately saved: calendar image, 56-second video, two hospital screenshots, sticker and 27-second voice note. The machine transcripts remain approximate. The [user's 21 September clarification](source-materials/private-context/2026-09-21/SHIRIN_CLARIFICATION_USER_SUPPLIED.txt) clearly asks for visit instructions and dated Hb/RBC values with ranges and High/Low display. The implementation will copy units, ranges and flags from reports, not interpret them. Eight later visual references are also [saved privately with hashes](source-materials/private-context/2026-09-21/design-references/manifest.json).

The hospital screenshots remain private references. The **25 August “Technical thoughts and questions.docx”** and the **full 30 August meeting recording/transcript** are still missing; the later attachments do not fill those gaps. See [source recovery](CONTEXT_RECOVERY_2026-09-20.md), [deck feature audit](FEATURES_FROM_DECKS_2026-09-21.md), [saved form](form.md) and [source index](SOURCE_INDEX.md).
