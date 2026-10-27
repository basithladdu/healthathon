# Team chat and meeting audit (27 Oct 2026)

The owner asked: "we have saved WhatsApp transcripts right — did we do everything?" This file answers that. It lists every ask, decision, bug report, feature idea and promise in the saved team chats and meetings, and checks each against `main` as of commit `936eab2` (27 Oct).

**Sources read in full:**
- `source-materials/team-context/2026-10-23/canonical-chat.txt`: 25 Aug–23 Oct, 658 records.
- `source-materials/team-context/2026-10-23/latest-export/_chat.txt`: 619 records. Every message in it is also in the canonical chat except two one-line replies with no ask.
- `source-materials/team-context/2026-10-23/sujay-survey-symptom-followup.txt`: 23 Oct.
- `MEETING_TRANSCRIPT_2026-08-24.txt`: the first team meeting.
- `source-materials/meetings/2026-09-21-transcript.txt`: the prototype review.
- `FEATURES_FROM_CONVERSATIONS_2026-09-21.md`: the earlier extraction, used as a cross-check.

All of these are already tracked in Git and none is ignored. Nothing here is copied word for word. Phone numbers, emails, links and patient details are left out.

**Status key:**
- **DONE:** on `main`, or submitted, with evidence.
- **PARTIAL:** some of it is built, or it is built only on the website or in the walkthrough.
- **NOT DONE:** not built and nobody has dropped it.
- **DROPPED:** a later team message or decision removed it (cited).
- **OWNER-ONLY:** only the owner or the clinical team can do or decide it.

"App" means the Android app in `android/`; paths are under `android/app/src/main/java/in/wedevit/care/`. "Web" means `prototype/` and https://sahara.wedevit.in. The Round 1 deck is `submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx`. The Phase 2 draft is `submission/phase-2/final/Sahara-Healthathon-Phase-2-visual-draft-v6-ready.pptx`.

## Counts

| Status | Items |
|---|---|
| DONE | 49 |
| PARTIAL | 36 |
| NOT DONE | 24 |
| DROPPED | 4 |
| OWNER-ONLY | 13 |
| **Total** | **126** |

Most DONE items are decisions and Round 1 deck edits. The biggest gaps are the emergency QR retrieval (C1–C4), real AI or transcription in the conversation (A10, A19, B2–B4, B11), the access-to-care networks (D4–D7, D11–D12), the Phase 2 KPIs, video and costs (A20, H13, H15), and clinician sign-off on the note format and symptom scales (I3, E2–E3).

## A. Direction and decisions

| # | Date | Who | Ask, decision or idea | Status | Evidence / note |
|---|---|---|---|---|---|
| A1 | 25 Aug | Sujay | Agree rough deadlines and deliverables on both sides | DONE | Round 1 was submitted on 3 Oct; Phase 2 runs to 7 Nov (`PROJECT_CONTEXT_MAP.md`) |
| A2 | 25 Aug | Basith, Sujay | Is this the best problem statement? Decision: keep goals of care, because it doesn't exist in India | DONE | Decision recorded; the product kept this focus |
| A3 | 25 Aug | Sujay | It must not be a decision-making tool; keep it process-led and assistive | DONE | App footnotes: no prescription, not a treatment order (`DoctorWorkflowPdf.kt`); AI drafting is off |
| A4 | 30 Aug, 2 Sep, 21 Sep | Basith, Sujay | Pick the track and use case. Changed on the portal to the patient/caregiver side; the doctor still starts the conversation | DONE | 21 Sep meeting; `form.md`, `FAMILY_CAREGIVER_DIRECTION.md` |
| A5 | 24 Aug, 10 Oct | Sujay | Call it a goals-of-care clinical note, never a "living will". Everyone should know advance care planning versus a legal advance medical directive | DONE | App text: "does not execute a legal advance directive" (`CareSharedUi.kt`); the PDF says it is not an advance directive |
| A6 | 26 Aug | Sujay | Rolling care document: short for the clinician, expandable to full reports per treatment stage | PARTIAL | Doctor patient hub has Overview, Progress, Medicines, Visits and Reports (`DoctorPatientHubUi.kt`). There is no one expandable patient-side document |
| A7 | 27 Aug | Sujay | AI model that works out cancer staging from reports | DROPPED | Clinical decision support, outside the non-clinical rule (webinar summary, 8 Sep). Not converted (`FEATURES_FROM_CONVERSATIONS_2026-09-21.md`) |
| A8 | 29 Aug | Sujay | Chemo side-effect tracker: patient uploads blood reports, the app asks about side effects and shows the doctor at the next visit | PARTIAL | Symptom diary, Lab history and report OCR exist. No conversation-style collection, no doctor summary |
| A9 | 31 Aug | SVK | An AI chatbot for anxiety is a medicolegal grey area | DROPPED | No chatbot was built. The 21 Sep meeting agreed only on search over the user's own documents (E10) |
| A10 | 23 Sep, 24 Sep | Sujay | The focus is AI in the goals-of-care tool; don't dilute it with other features | PARTIAL | The decks centre on the conversation flow. In the app the answers come from word matching and AI drafting is off (`HANDOFF.md` §2, `WORK_STATUS.md` §5) |
| A11 | 9 Oct | Sujay | Build something deployment-ready: presentation plus live demo for the final | PARTIAL | The app works on the emulator and Play 0.5.0 is live. 0.6 is untested on a phone; no pilot |
| A12 | 9 Oct | Basith | Build an Android app, since India is mostly Android, and publish it on Play | DONE | Play 0.5.0 (6) available; 0.6.0 (7) in review (`TEAM_HANDOFF.md`, `HANDOFF.md` §1) |
| A13 | 25 Sep–10 Oct | All | Rename from Saathi. Final name **Sahara**, Play title "Sahara: Palliative Companion", clearly about palliative care | DONE | Play title and decks (`TEAM_HANDOFF.md` "Start here") |
| A14 | 10 Oct | Awaiz, Shirin, SVK, Sujay | Logo: the first draft looked generic. Use palliative sun colours (orange, marigold); idea of a sunflower in cupped hands | PARTIAL | Warm orange Sahara palette (`SaharaPalette.kt`). No final logo agreement in the chat |
| A15 | 26 Sep | Sujay, SVK | Pick one state (Karnataka) to demo the location features | DONE | 33 Karnataka centres added on 2 Oct; now 1,631 listings nationally |
| A16 | 2 Oct, 23 Oct | Sujay | If shortlisted, work through each feature in detail, one tool per person; Shirin takes the symptom tracker | PARTIAL | Symptom brief done (`docs/research/symptom-tracker-clinical-brief-2026-10-23.md`); other tools not reviewed one by one |
| A17 | 15 Oct | Sujay | We could have built on CARE but built standalone | OWNER-ONLY | Decide option A, A+B or A+C (`docs/research/CARE_BRIEF_2026-10-27.md`, `CLAUDE_TRANSCRIPT.md` §2) |
| A18 | 20 Oct | Mentor (via SVK) | Use dummy data | DONE | Synthetic Rahul/Arjun walkthrough (`PatientWalkthrough.kt`, `DoctorCareDemoData.kt`) |
| A19 | 20 Oct | Mentor | Fine-tune the goals-of-care algorithm | NOT DONE | The answers still come from rules (`DoctorTranscriptGoals.kt`) |
| A20 | 20 Oct | Mentor | KPIs: family stress reduced, money saved, hospital time saved | NOT DONE | Not defined or measured; not in the Phase 2 draft |
| A21 | 24 Sep | Sujay | Measurable impact after deployment: patient requests, doctor-started and documented conversations, before vs after | PARTIAL | Patient requests exist in the app. No baseline or measurement plan; slide 12 says "measure the care flow" only |
| A22 | 25 Aug, 24 Aug | Sujay | Teaching part: how to hold a goals-of-care talk, also for MBBS students (later) | NOT DONE | Sujay himself said "later" |

## B. Goals-of-care conversation (24 Aug meeting, then chat)

| # | Date | Who | Ask | Status | Evidence / note |
|---|---|---|---|---|---|
| B1 | 24 Aug | Sujay | Screening tool that flags patients who need the conversation | NOT DONE | Nothing in the app; borders on clinical judgement |
| B2 | 24 Aug | Sujay | AI prompts the doctor through topics (ICU, ventilator, dialysis), ticks them off, and records topics skipped on purpose | PARTIAL | Preparation questions and per-question answers after recording (`DoctorPreparationQuestions.kt`, `DoctorTranscriptGoals.kt`). No live checklist; the web workflow says "Leaving a topic open does not imply agreement" |
| B3 | 24 Aug, 21 Sep | Sujay, Basith | Record the conversation by voice, up to an hour, with several people | PARTIAL | Recording with each person's consent and several clips works (`DoctorRecordStage.kt`, `DoctorSavedAudioSheet.kt`). The walkthrough transcript is pre-written; on-device Qwen speech-to-text is optional |
| B4 | 24 Aug, 21 Sep | Sujay, Basith | Tell the doctor side from the family side (speaker separation) | NOT DONE | Basith said a separate model is needed; none added |
| B5 | 24 Aug | Sharada | Voice may fail on languages and accents; allow a coordinator to type instead | PARTIAL | A Type mode exists beside Record (`CareUi.kt` `captureMode`). Hindi/English only |
| B6 | 24 Aug, 12 Sep, 21 Sep | Sujay, Sharada | Write the note in the clinicians' format (Healthathon form / Saanthvana doc / Vidhi model) | PARTIAL | Goals-of-care Q&A exists. The AI note is off; nobody has checked the format against the clinicians' template |
| B7 | 24 Aug | Sujay | Tapping "ventilator" in the note shows what was said in the conversation | PARTIAL | Answers are matched to transcript lines and the source is shown beside the note. It is not a full tap-to-source link for every field |
| B8 | 24 Sep | Sujay | After the AI drafts, an explicit doctor check-and-edit step | DONE | Review then publish in the conversation flow (`HANDOFF.md` §2); Round 1 slide 7 step 4 |
| B9 | 24 Aug, 21 Sep | Sujay | Doctor signs, then the patient signs from their own login; the version locks; no edits to it | PARTIAL | Fixed versions and exact-version confirmation (`CareSharedUi.kt`). These are not legal signatures, and doctor approval is done by hand |
| B10 | 21 Sep | Sujay, Sharada | One outcome only, "consensus reached among patient and family"; drop "verbal agreement" and "authorised representative" | PARTIAL | Those options are gone. There is no explicit "consensus reached" field |
| B11 | 21 Sep | Sujay | AI labels the note as a general discussion or a decision taken | NOT DONE | — |
| B12 | 21 Sep | Sujay | The next conversation pre-fills from the last one, so only changes are edited | DONE | Carry-forward from the previous note (`HANDOFF.md` §3, `DoctorPreparationQuestions.kt`) |
| B13 | 24 Aug | Sujay | The note can be updated, with every version kept | DONE | Version history v1, v2… (`TEAM_HANDOFF.md` "What is implemented") |
| B14 | 24 Aug | Sujay | Patient and family cannot change the note without a doctor | DONE | Only clinicians create versions; the family confirms or requests |
| B15 | 23 Sep | Sujay | Patient can ask the doctor to start a goals-of-care talk | DONE | Request flow (`EmbeddedPatientCare.kt`, `CareUi.kt` `onRequestConversation`) |
| B16 | 24 Sep | Sujay | Use one name: care document, care summary or doctor's note | DONE | The app uses "care note" throughout. The note reader title is "Transcription notes" (`HANDOFF.md` §2) |
| B17 | 24 Aug | Sujay, Sharada | 20 simulated conversations with different people to test it | NOT DONE | Also asked on 9 Oct. None recorded |
| B18 | 21 Sep | Basith | Doctor finds a patient by ID; only patients who joined appear | PARTIAL | Doctor patient list in the walkthrough; real search depends on accounts and invites |

## C. Getting the note in an emergency

| # | Date | Who | Ask | Status | Evidence / note |
|---|---|---|---|---|---|
| C1 | 24 Sep, 21 Sep | Sujay | Patient home shows the QR code and latest note large, in very few taps | PARTIAL | Today has a large "Your care notes" card. There is no QR (`WORK_STATUS.md` §5) |
| C2 | 21 Sep | Sujay, Sharada | An emergency doctor without the app opens the note from a QR in the browser, through a single-use link, and can print it for the chart | NOT DONE | App shows "Secure hospital retrieval is not connected" (`DoctorWorkflowUi.kt`). The exact-version link needs a signed-in account (`CareUi.kt`) |
| C3 | 21 Sep | Sujay | Save the QR offline as an image | NOT DONE | Depends on C2 |
| C4 | 21 Sep | Basith | Replace the QR if it leaks | NOT DONE | Depends on C2 |
| C5 | 24 Aug | Sujay | Password-protected PDF copy for the patient | PARTIAL | A PDF exists (`DoctorWorkflowPdf.kt`); it isn't password-protected |
| C6 | 21 Sep | Sujay, Sharada | Rename "Take to the next doctor"; clean consolidated format | DONE | That label isn't in the Android app |

## D. Access to care, morphine and the survey

| # | Date | Who | Ask | Status | Evidence / note |
|---|---|---|---|---|---|
| D1 | 31 Aug, 14 Sep | Sujay | Nearest palliative centres on a map, with call and directions | DONE | Nearby: 1,631 listings across 36 states/UTs (`CareCentreDirectory.kt`, `CentreMap.kt`) |
| D2 | 21 Sep | Sharada | Show services: inpatient, outpatient, home care, plus imaging, pharmacy and ambulance | PARTIAL | Filters for Home care, Clinic visits and Inpatient care (`CareUi.kt`). No imaging, pharmacy or ambulance |
| D3 | 31 Aug | Sujay (colleague's list) | Nearest places that dispense morphine; "may be important" | PARTIAL | "Morphine listed" filter with "call to confirm". No verified stock |
| D4 | 31 Aug, 12 Sep | Sujay | Patient support groups, with a list from NCG promised | NOT DONE | No support-group list in the app; Round 1 slide 4 described it |
| D5 | 31 Aug | Sujay (colleague's list) | Home-care, blood bank and oxygen sources | NOT DONE | — |
| D6 | 31 Aug, 23 Sep | Sujay | Emergency phone numbers and the nearest hospital or ambulance | NOT DONE | No emergency section on the patient side |
| D7 | 21 Sep | Sharada, Sujay | Help at home: transport, home nursing, medicine/oxygen delivery | NOT DONE | Web only (`FEATURES_FROM_CONVERSATIONS_2026-09-21.md`); not in the app |
| D8 | 21 Sep | Sujay, Sharada | Remove "caregiver break", "paperwork" and "company" from help at home | DROPPED | Removed at the meeting; none are in the app |
| D9 | 21 Sep | Sharada, Sujay | Remove the "help with costs" fundraiser link (open to misuse) | DROPPED | Removed at the meeting |
| D10 | 21 Sep | Sharada | Keep an expenses list | NOT DONE | Round 1 slide 5 says "Financial tracking"; the app has no expenses screen |
| D11 | 26 Sep, 10 Oct | Sujay | Patients report morphine at a hospital (with a bill as proof); confidence grows with each report | NOT DONE | — |
| D12 | 10 Oct | Sujay | Colour centres by confidence (one source vs Pallium plus survey) and show how many the survey added | NOT DONE | App marks all as "directory listed"; the web has `/reviewed-centres` |
| D13 | 26 Sep, 10 Oct | Sujay, Basith | Short hospital survey (about 4 questions) on our website for palliative care and morphine | DONE | https://sahara.wedevit.in/hospital-survery (web only, by decision; `TEAM_HANDOFF.md`) |
| D14 | 23 Oct | Sujay | Survey fixes: palliative wording, "hospitals you work with", no mobile field, name **and** address, state-aware search, map pin required, field for palliative doctors in other departments | DONE | Deployed 23 Oct (`TEAM_HANDOFF.md` "23 October follow-up") |
| D15 | 23 Oct | Sujay | Check that his test entry shows correctly | DONE | Stored, pending review (`TEAM_HANDOFF.md`) |
| D16 | 10 Oct, 23 Oct | Sujay, SVK | Send the survey to oncology and palliative groups once fixed | OWNER-ONLY | Not recorded as sent |
| D17 | 23 Oct | Sujay → SVK, Shirin | Try the survey and give feedback | OWNER-ONLY | No reply in the saved chat |
| D18 | 23 Oct | Shirin | Ask her hospital about morphine and fill in the survey | OWNER-ONLY | Promise, not recorded as done |
| D19 | 12 Sep | Sujay | Deliver lists: support groups, palliative doctors, morphine institutes | PARTIAL | The directory came from Pallium/IAPC; the other two lists were not supplied |
| D20 | 14 Sep | Basith | Pull the directory data with an agent or scraper | DONE | 81 Pallium/IAPC pages (`output/research/india-directory-20261020/`) |
| D21 | 21 Sep | Sharada | Community of patients with similar illness, grouped without labelling them | NOT DONE | — |
| D22 | 21 Sep | Sharada | Only vetted centres, not random Google business listings | DONE | Only listings from official directories are shown |

## E. Daily care, diary and reports

| # | Date | Who | Ask | Status | Evidence / note |
|---|---|---|---|---|---|
| E1 | 21 Sep | Sharada, Sujay | Symptom diary for palliative care, semi-objective 0–10 | DONE | Symptom tracker (`CareSymptomUi.kt`) |
| E2 | 23 Oct | Sujay → Shirin | Clinician-led tracker: common palliative symptoms, a proper scale per symptom, better/worse than yesterday and by how much, what makes it worse | PARTIAL | Symptom list follows Sujay's feedback, plus comparison with the last note and "When does it happen?" The clinical scale hasn't been chosen (`docs/research/symptom-tracker-clinical-brief-2026-10-23.md`) |
| E3 | 23 Oct | Shirin | Draft the symptom options and scales | OWNER-ONLY | Pending (Phase 2 slide 12) |
| E4 | 21 Sep | Sharada | One consolidated, trended view of repeated lab results | DONE | Lab history with plots (`CareLabs.kt`) |
| E5 | 21 Sep | Sharada | Photograph or upload a printed report or PDF and pull out each test | PARTIAL | On-device OCR with page- and line-linked facts (`CareDocumentReader.kt`, `CareBilingualOcr.kt`). Lab values aren't filled in automatically |
| E6 | 21 Sep | Sujay | Keep big cancer documents (PET-CT, biopsy) apart from small monitoring tests (CBC/LFT/KFT) | PARTIAL | Docs and Lab history are separate; no category for cancer documents |
| E7 | 21 Sep | Sujay | Mark chemo or radiotherapy days so blood trends can be read per cycle | NOT DONE | — |
| E8 | 21 Sep | Sujay | Calendar for big events, not daily medicines typed by the patient (risk of trusting the app over the prescription) | PARTIAL | The app still has typed medicines and reminders; nobody re-confirmed the decision |
| E9 | 21 Sep | Sharada, Sujay | Scan the printed (not handwritten) prescription; AI makes the reminders | PARTIAL | Prescription scan and reminders exist (`MedicineScanner.kt`, `CareReminders.kt`). No printed-only check |
| E10 | 21 Sep | Basith, Sharada | Search only the user's own uploaded reports and show the source, no internet | DONE | Report search: "Searching your reports…", "Not found in your reports" (`CareDocuments.kt`). English only |
| E11 | 21 Sep | Sharada | AI timeline summary (last chemo, last radiotherapy) citing the documents | NOT DONE | — |
| E12 | 21 Sep | Basith | Journal and daily mood check-in | DONE | Journal and check-in (`CareDailyUi.kt`, `FeedbackUi.kt`) |
| E13 | 21 Sep | Sujay | Voice journal with AI-picked themes | NOT DONE | Basith preferred to leave the journal alone; no decision |
| E14 | 23 Sep | Shirin | Show today's medicines and appointments as soon as the app opens, not only in a calendar | DONE | Today: "planned today", Up next, Medicines (`CareUi.kt`) |
| E15 | 21 Sep | Sujay | Merge the patient and family logins; drop family tasks | DONE | Family sees the same screens (team decision, 26 Oct, `HANDOFF.md` §7) |
| E16 | 21 Sep | Sujay | Less clutter: four groups (clinical documents, access to care, my space, quick QR access) | PARTIAL | Tabs: Today, Docs, Care, Nearby, More. No QR |
| E17 | 21 Sep | Basith | Calendar with goals-of-care visits and appointments | DONE | Calendar (`CareSchedule.kt`) |

## F. Language, platform and release

| # | Date | Who | Ask | Status | Evidence / note |
|---|---|---|---|---|---|
| F1 | 21 Sep | Basith | Hindi, then Telugu and other languages | PARTIAL | Some screens in Hindi; many English only; no Telugu (`CLAUDE_TRANSCRIPT.md` §4) |
| F2 | 21 Sep, 10 Oct | Sujay, Basith | Android now; iOS/web for iPhone users later | PARTIAL | Android is done. iPhone users only have the website |
| F3 | 10 Oct | Basith | Collect testers for Play testing | DONE | 59 tester invitations sent (`TEAM_HANDOFF.md`) |
| F4 | 12 Oct | Basith | Play rejected the app as medical; complete the verification | DONE | 0.5.0 available; 0.6.0 (7) in review |
| F5 | 9 Oct | Basith | Ship updates while the app is live | PARTIAL | The next upload must be versionCode 9 or higher; 0.6.x changes are not uploaded (`HANDOFF.md` §1) |

## G. Identity, data and privacy

| # | Date | Who | Ask | Status | Evidence / note |
|---|---|---|---|---|---|
| G1 | 21 Sep | Sharada | Verify doctors by state medical-council registration number | PARTIAL | Council and registration fields go to manual review (`CareSharedUi.kt`). No council lookup |
| G2 | 24 Aug | Sharada, Sujay | Hospital gives doctors limited access codes; the hospital vets its doctors | PARTIAL | Clinician invite exists; approval is done by hand in the database |
| G3 | 24 Aug, 21 Sep | Sujay, Basith | Link to ABHA later; avoid Aadhaar | NOT DONE | Deferred by the team ("later, not now") |
| G4 | 24 Aug | Sujay | Confirm the patient really took part (identity) | PARTIAL | Account sign-in plus exact-version confirmation; no ID check |
| G5 | 21 Sep | Sharada, Basith | DPDP Act 2023 compliance; no ads, no selling data | PARTIAL | Privacy pages on the web; no formal compliance review |
| G6 | 24 Aug | Sujay, Basith | Who holds the data: a legal entity, or the company, for a real deployment | OWNER-ONLY | Not decided |
| G7 | 21 Sep | Sharada | Paperwork uploads bring legal risk; don't collect bills | DONE | Not built (conflicts with D11's bill-upload idea, so decide both together) |

## H. Pitch, submission, deck and video

| # | Date | Who | Ask | Status | Evidence / note |
|---|---|---|---|---|---|
| H1 | 13 Sep, 21 Sep, 23 Sep | Basith, Sujay | Round 1 deck of 6–8 slides; the tech team supplies prototype slides; QR and link on slide 1 | DONE | 8-slide deck submitted 3 Oct |
| H2 | 18 Sep | Sujay | Make the deck about goals of care, not general continuity; add the location features | DONE | Round 1 slides 4 and 7 |
| H3 | 18 Sep | Sujay | Use the clinicians' slides as the format guide so the decks match | DONE | Merged into the Round 1 deck |
| H4 | 23 Sep | Shirin, Sujay | Change the scenario so it doesn't read as medicine failing; avoid "dying"; say "best supportive care" | DONE | Round 1 slide 2 |
| H5 | 24 Sep | Sujay | Fit everything the organiser form asks for | DONE | Round 1 answers (`submission/Round_1_Answers_2026-09-25.md`) |
| H6 | 24 Sep | Sujay | Make the AI in the goals-of-care step more visible, and show the doctor-review step | DONE | Round 1 slide 7 "Physician leads. AI assists", step 4 |
| H7 | 25 Sep | Sujay | Apply his 29 comments, use slide order 1 2 3 5 7 6 4 8, new slide 1 photo, less lay language for clinical judges | DONE | Basith applied them; SVK moved slides 5 and 7 |
| H8 | 25 Sep | Sujay, Shirin, SVK | New slide 2 picture: OPD setting, 2–3 people | DONE | Shirin's image accepted |
| H9 | 25 Sep | Sujay | Add a features overview before slides 5–7 | DONE | Round 1 slide 5 |
| H10 | 25 Sep | Sujay | Submit a first version now and refine before the extended deadline | DONE | Submitted, then updated by 3 Oct |
| H11 | 2 Oct, 3 Oct | Sujay | Slide 1: "goals of care records" and "palliative care companion" | DONE | Round 1 slide 1 |
| H12 | 2 Oct | Sujay | SVK's photo in the deck | DONE | Round 1 slide 1 team list |
| H13 | 25 Sep | Basith | Upload the walkthrough video "by the weekend" | PARTIAL | `submission/demo-video.mp4` is a short silent slide video; no narrated screen recording |
| H14 | 21 Sep | Basith | Give judges the code repository | OWNER-ONLY | The link is on Round 1 slide 1; the repo is private, so access for judges is still open (`submission/phase-2/README.md`) |
| H15 | 21 Oct | Awaiz, Sujay | Fill the 13-slide Phase 2 template and submit by 7 Nov | PARTIAL | v6 draft is ready. Missing: team code, doctor details, costs, narrated video, validation (`submission/phase-2/README.md`) |
| H16 | 21 Oct | Sujay | Team meeting for the Phase 2 game plan | DONE | Call on 23 Oct |
| H17 | 9 Oct | Sujay | Final at IIT Bombay: live demo for the top 10 per track | OWNER-ONLY | Only if selected; needs a rehearsed demo |
| H18 | 27 Oct (session) | Owner | Answer the CARE integration question in the deck | NOT DONE | Waiting on the CARE choice (A17); slide 7 only says "CARE extension · Proposed" |

## I. Team, pilot and promises

| # | Date | Who | Ask or promise | Status | Evidence / note |
|---|---|---|---|---|---|
| I1 | 24 Aug | Sujay, Sharada | Pilot at a few centres we know, then show the numbers | OWNER-ONLY | No pilot or ethics step yet (`TEAM_HANDOFF.md` "What is still incomplete" 4) |
| I2 | 9 Oct | Sujay | Talk to a hospital or palliative physician about using it on the doctor side | OWNER-ONLY | Not recorded |
| I3 | 24 Aug, 10 Oct | Sujay, Sharada, Shirin | Final goals-of-care note format and conversation structure from the clinicians | OWNER-ONLY | No clinician-approved template recorded |
| I4 | 24 Aug | Sujay | Outreach later through the palliative-medicine community or scale-up programmes | OWNER-ONLY | After the hackathon |
| I5 | 6 Sep | Sujay, Basith | Someone watches the organiser webinar and posts a summary | DONE | Awaiz posted it on 8 Sep |
| I6 | 21 Sep | Sharada | Share the meeting minutes | DONE | `docs/meetings/2026-09-21-minutes-draft.md` |
| I7 | 20 Oct | Basith | Share minutes of the mentor meeting | NOT DONE | None saved (`PROJECT_CONTEXT_MAP.md` line 34) |
| I8 | 19 Oct | Basith, SVK | At the mentor meeting SVK explains the problem and Basith the tech | DONE | The meeting took place on 20 Oct |
| I9 | 8 Sep | Sujay | Separate pitch for the TANUH chronic-respiratory call | PARTIAL | `submission/TANUH_DRAFT.md` exists; no further action |
| I10 | 25 Aug | Sujay | `Technical thoughts and questions.docx`, shared on 25 Aug | OWNER-ONLY | The file was never recovered (`FEATURES_FROM_CONVERSATIONS_2026-09-21.md`, "Source gaps") |
| I11 | 16 Oct | Sujay, Basith | Join the mentor group and rename our group so the names don't clash | DONE | Renamed on 16 Oct |

## Cross-check with the 21 Sep extraction

`FEATURES_FROM_CONVERSATIONS_2026-09-21.md` described **web** components: care calendar, report folder, symptom diary, care story, "what matters to you", handover pack, care directory, help at home, and proposed copy tracking and a call notebook.

On Android:
- **In the app:** the calendar, Docs, symptom diary, care notes and Nearby.
- **Web only:** help at home (D7), the handover pack, copy tracking and the support call notebook.
- **Doctor side only:** the care story (doctor hub).

None of those web-only parts was asked for again in the October chats.

## Not covered here

- `CLAUDE_TRANSCRIPT.md` §3 (owner tasks) and §4 (screens that don't work yet) list this session's open items. They are summarised in `HANDOFF.md` under "27 Oct 2026 — open items" and not repeated as rows here.
- `docs/research/CARE_FAMILY_SIDE_FIT.md` covers the CARE data mapping.
