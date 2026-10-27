# Sahara Android: Awaiz's handoff

Last updated 26 Oct 2026 by Shaik Muhammad Awaiz (UI/Android), with Claude Code.

This note lists every change I made to the Android app, what the app is like right now, what was checked, and what is still open. It covers the patient & family app, the doctor walkthrough and the symptom tracker.

**27 October final-deliverables update:** the organiser's new [two original templates and email](source-materials/healthathon-phase-2/2026-10-27-final-deliverables/README.md) are archived. Final submission requires a working MVP, a separate two-page overview, the new final presentation and a narrated demo video. Use [everyday.md](everyday.md) for the final checklist and [TEAM_HANDOFF.md](TEAM_HANDOFF.md) for this archive checkpoint. The new email does not confirm the existing 7 November planning target or specify a cut-off/portal; verify those before submitting. Previous Android evidence below is unchanged by this archive task.

---

## 1. Where the code is

| What | Value |
|---|---|
| Repository | `github.com/basithladdu/healthathon` |
| My branch | `awaiz/at-73c5000` |
| Starting point | my commit `73c5000` "Redesign the symptom entry form" (24 Oct) |
| App version on this branch | `0.6.0` (versionCode 7), package `in.wedevit.sahara`, debug package `in.wedevit.sahara.development` |
| Built with | Kotlin 2.2.20, Jetpack Compose, Room v7, AGP 8.13, compile/target SDK 36, min SDK 26 |

**About main.** After `73c5000`, basithladdu pushed 15 commits to main (24–25 Oct). I rolled my version back to `73c5000` and then brought in **only** these parts of his work: the 3D pain body, pain history, symptom trends, the new symptom list and two fixes (see section 4). His other app changes are **not** in this version (see section 5).

**On 26 Oct this version became main.** Main's files are now exactly this branch. It was done with a merge commit, not a force push, so basithladdu's 15 commits are still in main's history and anything from them can be brought back, for example with `git checkout 6ab303e -- <path>`. Files from those commits that are no longer on main include his app redesign, the website's 3D pain and privacy pages, `android/release/store-draft.json`, the Play Store and backend verification records, and the CARE guide notes. The app version on main is back to 0.6.0 (7). **The next Play Store upload must use versionCode 9 or higher**: 0.6.0 (7) is in Google's review, and 0.6.1 (8) was built locally (not uploaded) from the version this replaces.

---

## 2. What the app is like now

### Patient & family app
- **Look:** warm orange/terracotta palette (Sahara colours), serif titles. Bottom tabs: **Today · Docs · Care · Nearby · More**.
- **Walkthrough:** "Patient & family" → **Try the walkthrough** → **Rahul — Patient** or **Arjun — Family** (Rahul's brother). Both read the same local data as the doctor walkthrough. **Leave walkthrough** returns to the welcome screen. The "Walkthrough · Rahul · Patient" banner has been removed.
- **Daily care (Care tab):** date header, one **+ Add** menu (medicine, visit, task, check-in, note), Calendar / Care team / Lab history tiles, the **Symptom tracker** button, and collapsible sections: Conversations, Medicines (Current / Past), Visits (Upcoming / Past), To do, Check-ins and Journal.
- **Daily check-in:** "How was your day?" mood face + slider, saved into the care record.
- **App feedback:** its own screen ("How is Sahara working for you?", five rating tiles). It no longer reuses the check-in face.

### Symptom tracker ("How are you feeling?")
It's a **step-by-step** form, with a progress bar and Back/Next fixed at the bottom:

1. **What would you like to note?** Tap a symptom tile to move on. The list follows Sujay's feedback: Pain, Fatigue / lack of energy, Drowsiness, Poor appetite, Dry / sore mouth, Breathlessness, Constipation, Diarrhoea, Nausea, Vomiting, Difficulty swallowing, Cough, Bowel / bladder incontinence, Sadness, Worry, Overall well-being and Other. Anxiety and low mood are rated separately.
2. **Where does it hurt?** (Pain only)
   - **3D body** on a dark warm charcoal background, taller than before. Drag to rotate.
   - **"Where is the pain?" + Front / Back toggle**, then a grid of **body-part tiles** (Full body, Head, Neck, Chest, Abdomen, shoulders, arms/hands, legs/feet; the back side has Back of head, Neck (back), Upper back, Lower back, …).
   - **Tapping a tile only zooms** the model to that part. Pain is recorded only when you **tap the model** where it hurts, or press **"Mark whole <part>"** (for anyone who can't use the 3D model).
   - **Each spot gets its own strength** with a slider under the model ("How strong is the pain? · Head"). The bar is **six Wong-Baker colour blocks** from green to red. **0 = None has no colour.**
   - **On the model**, each spot shows as a **contour heat map** (stacked rings, deep pain colour in the centre fading to pale peach) in its own colour. Head can be red while the abdomen is yellow.
   - Tiles show **zoomed** (orange fill) and **marked** (coloured dot + "Marked") states. Each marked part has a **Remove** button, and **Clear** removes everything.
   - Below that: optional location text and "When does it happen?" (at rest, turning in bed, …).
3. **About this note:** who reported it, time frame, and when it happened, all on one page.
   - For **Pain** there is no 0–10 card here. The overall score is the **highest spot**. If no spot has a strength, a short card offers **Back to rate a spot** or **Not rated**.
   - Other symptoms keep the 0–10 number buttons plus "Not rated".
4. **Anything else? (optional):** comparison with the previous note, started-on date, note, then **Save**.

**Diary list cards** show:
- the symptom icon and a **strength badge** in its block colour ("10 · Worst");
- a friendly time ("Today, 4:01 PM"), plus tags for the time frame and reporter;
- per-spot chips for Pain;
- **View on body** (dated, read-only pain history on the 3D body) and **Trend** (dated graphs);
- Update rating / Edit / Remove in a **⋮** menu.

### Doctor walkthrough
- **Home:**
  - a greeting with the doctor's name and today's date;
  - four tappable summary tiles (patients, visits in the next 7 days, unfinished drafts, conversations to plan), using real counts only;
  - quick actions, "Coming up" visits, and "Pick up where you left off".
  - Fixed the "0 care profiles" bug.
- **Patient hub:** Overview, Progress, Medicines, Conversations, Visits and Reports tiles in the matching palette with live counts.
- **Conversation flow, 3 steps:**
  1. **Before:** who is in the room (doctor and patient prefilled; add family) with each person's recording consent, plus the goals-of-care questions (add as many as needed, no section picker).
  2. **Conversation:** a full orange record stage with a waveform and a big mic button. A **Saved Audio** sheet peeks at the bottom; drag it up for Audio 1, 2, … each with date/time, a checkbox and playback. At least one ticked audio is needed to continue.
  3. **Note:** under the transcript, each **goals-of-care question** has a dropdown with its answer (matched to transcript lines on the phone by rules, never AI, and editable; an empty box if nothing was found), then answers, review, publish.
- **Note reader ("Transcription notes"):** audio card, transcript, and goals-of-care Q&A cards.
- **Visits:** "Last completed visit by doctor" was removed.
- **Data:** Rahul and Arjun have realistic records, and all "fictional / sample / mock" wording was removed. Safety statements (not verified, no prescription issued, consent) are kept as small footnotes.

---

## 3. Everything I changed, in order

### Before 24 Oct (merged into main through PR #1 and basithladdu's merge `78cbbc6`)
- **Doctor walkthrough visual overhaul:**
  - a shared palette in `SaharaPalette.kt`;
  - higher-contrast cards;
  - a searchable condition history;
  - clearer Current / Past medicine cards;
  - a readable conversation reader;
  - an outlined orange info icon everywhere.
- **Wording and data:** removed all "fictional / mock / sample data" wording, made Rahul and Arjun read as real patients, and cleaned up `DoctorCareDemoData.kt`.
- **Features:**
  - clinician invite on Android;
  - carry-forward from the previous note;
  - a time-to-retrieve measurement (cold start median 2.67 s, about 6 taps to the latest note);
  - a mock AI note;
  - simulated events removed.
- **Conversation flow rebuilt:**
  - who is in the room, with consent ticked before recording;
  - goals-of-care questions merged onto that page;
  - the Saved Audio sheet with several recordings per sitting;
  - a goals-of-care Q&A under the transcript;
  - "Saved notes" removed from the steps;
  - recording-consent footnotes removed.
- **Screens redesigned:** doctor home, Daily care (+ Add menu, then collapsible sections), app feedback separate from the daily check-in, the "Last completed visit by doctor" section removed, and the note reader.
- **Patient & family walkthrough** added: Rahul (patient) and Arjun (family).
- **Symptom entry form** redesigned with cards, icon tiles and pills.
- **Fixes:**
  - a walkthrough seeding crash (`MedicineHistoryRepo.resetSeededPerson`);
  - the check-in entry points;
  - a duplicate version number when the patient walkthrough seeded over an existing note.

### On `awaiz/at-73c5000` (after rolling back to `73c5000`)
| Commit | Change |
|---|---|
| `4bfdf17` | Took basithladdu's nullable edit-token fix from `e906f8e` |
| `8fb3eab` | Added his 3D pain body, pain history, symptom trends and new symptom list (app parts of `8336f8e`, `879d4f3`, `031492a`, `d0aa4da`; his website, Play Store and backend files from those commits were left out) |
| `5f670fc` | Used his corrected 3D viewer script from `6ab303e`, because the earlier one stayed blank on Android |
| `31c16af` | Step-by-step symptom entry, plus tapping a body part zooms the 3D model, plus Full body |
| `126253d` | Heat-map glow instead of the sphere marker, plus a live pain strength slider |
| `66fe894` | Wong-Baker six-block bar and contour-ring glow |
| `cc8a242` | Where-does-it-hurt redesign (tiles only zoom, dark taller viewer, Front/Back toggle, tile grid) and the walkthrough banner removed |
| `c097bfc` | Per-spot pain strength, 0 = no colour, no 0–10 card for Pain in step 3, redesigned diary card |

The 3D viewer's readable source is now in this branch: `prototype/scripts/pain-body-viewer-entry.mjs` and `build-pain-body-viewer.mjs`. Before, only the bundled file was here. It builds with esbuild 0.27.3 and three@0.186.0 to `android/app/src/main/assets/pain-body-viewer/viewer.js`.

**Saved data change:** symptom notes gained one optional field, `painBodyRatings` (body part → 0–10). Older notes without it load as before.

---

## 4. What I took from basithladdu's later work
- `879d4f3`: rotatable 3D pain body, contextual symptom trends, and the symptom list from Sujay's feedback.
- `031492a`: read-only dated pain history on the 3D body.
- `d0aa4da`: fix for the 3D body sometimes drawing at zero height.
- `e906f8e`: symptom edit-token fix.
- `6ab303e`: **only** the 3D viewer script.

## 5. What is on main but NOT in this version
From basithladdu's `6ab303e` (25 Oct) and the release commits:
- the new start screen ("I am a Patient / Doctor" sign-in, "Use on this phone without signing in", "Try a patient example");
- new tabs (Today · **Symptoms** · Care · Nearby · **Account**) and separate Privacy / About pages;
- his symptom form (0–10 slider, automatic reporter), his app feedback screen, and "no overflow menus / paginated lists";
- the pink/purple colour theme;
- typed saved/error notices above forms;
- the app version bump to **0.6.1 (8)**;
- Play Store records (0.6.0 (7) submitted and in review), backend migration records, CARE guide, website (3D pain, privacy/delete-account pages) and team notes.

---

## 6. Checks done
- `assembleDebug`, all **315 unit tests across 55 suites pass** (0 failures), and the instrumentation tests compile.
- **Seen working on the emulator** (`Sahara_Doctor_Demo`, run with `-memory 4096`):
  - the step-by-step form;
  - zoom to Head / Chest / Abdomen;
  - tiles only zoom;
  - heat-map rings at 10 (red) and 2 (green);
  - per-spot colours (Head red, Abdomen yellow);
  - the dark viewer;
  - the new diary card;
  - the doctor recording stage and Saved Audio sheet;
  - goals Q&A under the transcript;
  - Daily care sections;
  - Rahul and Arjun walkthroughs.
- **Not run:** the instrumentation (on-device UI) tests. They were updated for the new screens but haven't been run on a phone or emulator.

## 7. Known issues and open items
- **Scrolling:** swiping up/down over the 3D model now scrolls the page (27 Oct); sideways drags turn the model and two-finger pinches zoom it. Swiping over the strength bar can still change the score; scroll from the text or tiles there.
- **Not seen on screen yet:** the grey outline for a spot rated 0, the "No pain strength set yet" card, the ⋮ menu, the Back-side zooms, and saving a full pain note.
- **Zoom framing** was set by eye; some parts may sit too close (the right leg may cut off at the feet).
- **Body parts are limited to the 12** the 3D model knows (no buttocks or separate upper/lower arm).
- Re-tapping a part that already has a spot **moves** the spot and keeps its strength.
- The Hindi date and time format is untested on a device.
- **Version 0.6.0 (7) is lower than main's 0.6.1 (8).** Installing over 0.6.1 needs `adb install -r -d` (both use database version 7, so data is kept).
- The patient app's role menu still offers "Doctor" outside the walkthrough; the end-to-end conversation tests rely on it.
- Arjun (family) sees the same screens as Rahul, apart from confirming a note as the patient. No extra family limits were added (team decision, 26 Oct).

## 8. How to build and run
```bash
# from android/ — Windows paths used on Awaiz's machine
JAVA_HOME=/d/SetUp-files/Android/jbr ANDROID_HOME=/d/SetUp-files/AndroidSdk \
  ./gradlew assembleDebug testDebugUnitTest -Pksp.incremental=false --no-daemon --max-workers=1
adb install -r -d app/build/outputs/apk/debug/app-debug.apk
```
Start the emulator with `emulator -avd Sahara_Doctor_Demo -memory 4096 -no-snapshot-save`; the default 2 GB runs out of memory. In the app: **Patient & family → Try the walkthrough → Rahul — Patient → Care → Symptom tracker → How are you feeling? → Pain**.

## 9. Main files
| Area | Files (`android/app/src/main/java/in/wedevit/care/`) |
|---|---|
| Symptom form, steps, diary card | `CareSymptomUi.kt`, `CareSymptomStyle.kt`, `CareSymptomData.kt`, `CareSymptomTrend.kt` |
| 3D pain body, zoom, slider, colours | `CarePainBodyMap.kt`, `CarePainBodyData.kt`, `CarePainHistoryUi.kt`, `CarePainViewerPolicy.kt`, `assets/pain-body-viewer/` |
| Daily care, + Add, sections | `CareUi.kt` (`CareList`), `CareDailyUi.kt`, `CareSchedule.kt` |
| Patient & family walkthrough | `PatientWalkthrough.kt`, `DoctorTestProfiles.kt` |
| Doctor conversation flow | `CareUi.kt` (conversation steps), `DoctorAttendees.kt`, `DoctorPreparationQuestions.kt`, `DoctorRecordStage.kt`, `DoctorSavedAudioSheet.kt`, `DoctorTranscriptGoals.kt`, `DoctorNoteReaderUi.kt` |
| Doctor home and hub | `DoctorDemoUi.kt`, `DoctorPatientHubUi.kt`, `DoctorVisitsUi.kt` |
| Feedback and check-in | `FeedbackUi.kt` |
| Palette | `SaharaPalette.kt` |

---

## 27 Oct 2026 — open items from team chats and this session

Added by the owner's session on 27 Oct; nothing above was changed. The full audit of the team chats and meetings (25 Aug–23 Oct) is in [`docs/research/CHAT_AUDIT_2026-10-27.md`](docs/research/CHAT_AUDIT_2026-10-27.md): 126 items, of which 49 are done, 36 partial, 24 not done, 4 dropped and 13 owner-only. Item numbers below match that file.

Also read:
- [`CLAUDE_TRANSCRIPT.md`](CLAUDE_TRANSCRIPT.md) §3 (owner tasks) and §4 (screens and features that don't work yet);
- the [CARE brief](docs/research/CARE_BRIEF_2026-10-27.md) and [`docs/research/CARE_FAMILY_SIDE_FIT.md`](docs/research/CARE_FAMILY_SIDE_FIT.md).

**Who:**
- **Awaiz:** app work.
- **Owner:** Basith.
- **Team:** the clinicians (Sujay, Sharada, Shirin).

### Do first (before submission; existing planning target 7 Nov, exact cut-off to confirm)
| Item | Next step | Who |
|---|---|---|
| A17, H18 | Pick CARE option A, A+B or A+C (CARE brief §4), then write the integration answer in the deck | Owner |
| H15 | Fill the new two-page brief and adapt v6 to the 27 October final template; complete team code, doctor details, costs and all placeholders; verify all four deliverables and the submission cut-off | Owner + team |
| H13 | Record a narrated walkthrough: introduce the team/problem, explain development and doctor/technology collaboration, then show conversation → note → family confirms in the working app | Owner + Awaiz |
| A20, A21 | Choose 2–3 measurable KPIs and a baseline (documented conversations, retrieval time, family stress/money/time), and put them on the evaluation slide | Team + owner |
| — | Test 0.6 on a real phone: the full checklist is in `CLAUDE_TRANSCRIPT.md` §3 ("Test on a real phone") | Awaiz |
| — | ~~Strength-bar swipe~~ done in the polish lane (see lane notes below); still check on a real phone | Awaiz |
| F5 | Next Play upload must use versionCode 9 or higher; don't touch 0.6.0 (7) while it is in review | Owner |
| I3 | Clinicians send the approved goals-of-care note format and conversation structure | Team |
| E2, E3 | Shirin and Sujay choose the symptom list and a scale per symptom (see the symptom brief) | Team |

### Goals-of-care conversation
| Item | Next step | Who |
|---|---|---|
| A10, A19, B6 | Turn on real AI note drafting once a provider and consent terms are chosen (`CLAUDE_TRANSCRIPT.md` §3 "Decide"); until then, say "word matching" in the pitch | Owner decides, Awaiz builds |
| B2 | Show the goals questions as a live checklist during recording (covered / left open) | Awaiz |
| B3 | Replace the pre-written walkthrough transcript with real on-device transcription on a phone that can run it | Awaiz |
| B4 | Speaker separation (doctor side vs family side): later, needs a model | Awaiz (later) |
| B5 | Typed capture works; add more languages only after Hindi is complete | Awaiz (later) |
| B7 | Tap an answer to jump to the transcript line it came from | Awaiz |
| B9 | Real signatures are out of scope for now; keep the "exact-version confirmation" wording | — |
| B10 | Add one outcome field: "consensus reached among patient and family" yes/no | Awaiz (after team OK) |
| B11 | Mark each note as a "general discussion" or a "decision recorded" (the doctor picks; AI later) | Awaiz (after team OK) |
| B17 | Run 20 simulated conversations with different people and note the results | Team + owner |
| B18 | ~~Check that doctor search by patient ID works with real accounts~~ done, 33ac1f5 (doctor lane; search runs over the doctor's real connected patients; not tried with live accounts on a phone) | Awaiz |
| B1 | Screening tool to flag patients: hold, it borders on clinical judgement | Team decides |
| A22 | Teaching cards on holding the conversation: after the hackathon | Team |

### Getting the note in an emergency
| Item | Next step | Who |
|---|---|---|
| C1–C4 | ~~Decide on the hospital QR card (3–5 days plus server work). If yes: large QR on Today, single-use browser link, printable, saveable offline, can be replaced if leaked~~ done in the QR lane (7a1810a, 217dcd2, a93a520; branch `sa3-qr`): revocable link, not single-use. **Owner applies** `android/supabase/migrations/20261027190000_emergency_card_links.sql` and deploys `prototype/app/emergency` before the link goes live | Owner decides, Awaiz builds |
| C5 | ~~Password-protect the PDF copy~~ done: real PDF open-password (AES-256, `PdfPasswordLock.kt`, a93a520) for the emergency card PDF and, since the integration merge, the care-note PDF too (replaces the sa3-docs locked ZIP) | Awaiz |

### Access to care
| Item | Next step | Who |
|---|---|---|
| D2 | Add imaging, pharmacy and ambulance to the centre services when the source data has them. Checked 27 Oct: the bundled directory lists only clinic visits, home care, inpatient care, physiotherapy and day care, so there is nothing to add yet | Awaiz |
| D3, D11, D12 | Morphine: keep "listed, call to confirm". Showing confidence or patient reports needs a review process first | Team decides |
| D4, D19 | Support-group, palliative-doctor and morphine-institute lists (promised 12 Sep) | Team (Sujay) |
| D5, D6 | ~~Emergency numbers and nearest hospital/ambulance on Nearby; home care~~ done 27 Oct (see Nearby lane note). Home care is a filter chip (561 listings). Still open: oxygen and blood-bank sources (the bundled directory has none) | Awaiz |
| D7, D10 | Help at home (transport, nursing, medicine delivery) and expenses are web only; decide whether the app needs them | Owner |
| D16, D17, D18 | Send the fixed hospital survey to oncology and palliative groups; SVK and Shirin to try it; Shirin to ask her hospital | Team |
| D21 | Patient community: after the hackathon | Team |

### Daily care and reports
| Item | Next step | Who |
|---|---|---|
| A6, A8 | Short patient-side summary with expandable sections; side-effect notes for the doctor at the next visit | Awaiz (later) |
| E5 | ~~Fill Lab history from scanned reports, with the original line kept~~ done, docs lane | Awaiz |
| E6 | ~~Add a "cancer documents" category (PET-CT, biopsy) in Docs~~ done, docs lane | Awaiz |
| E7 | ~~Mark chemo/radiotherapy days on the Lab history chart~~ done, docs lane | Awaiz |
| E8, E9 | Clinicians to re-confirm whether patients may type daily medicines, or only scan printed prescriptions | Team |
| E11 | Timeline summary from documents (last chemo or radiotherapy, with source) | Awaiz (later) |
| E13 | Voice journal themes: no decision; hold | Team |
| E16 | ~~Quick access to the note and QR on Today, once C1 is decided~~ done, a93a520 | Awaiz |

### Language, identity, data
| Item | Next step | Who |
|---|---|---|
| F1 | ~~Hindi across the whole app~~ mostly done on branch `sa3-hindi` (family screens + main doctor screens; see lane notes); Telugu later | Owner decides, Awaiz builds |
| F2 | iPhone users use the website; no iOS app before the final | Owner |
| G1, G2 | Doctor approval stays manual for now; real doctor account and Supabase email setting (`CLAUDE_TRANSCRIPT.md` §3 "Set up / do") | Owner |
| G3 | ABHA link: later | Owner |
| G4 | Identity check for the patient confirmation: later | Owner |
| G5, G6 | DPDP review and who holds the data (company or new entity) before any real patient use | Owner |
| — | ~~**Anonymise before sharing with the doctor (owner's design, 27 Oct; not built).**~~ Text part done on branch `sa4-privacy` (see Lane notes); pseudonymous names and photo blurring still need the owner's decision. Today signed-in records reach Sahara's Supabase server with the real name and the words as typed, and the invited doctor sees them as they are. Build: a pseudonymous patient ID in place of the name on doctor views; strip names, phone numbers and addresses from text before upload; blur faces and ID numbers in any photo before upload. Data map: `docs/research/CARE_VS_SAHARA_FOR_TEAM.md` "Where Sahara's data lives" | Owner decides scope, Awaiz builds |
| — | **DPDP note:** health data on the server is personal data under the DPDP Act 2023, even with the name replaced (a pseudonymous ID can be linked back). Before real patients: a consent notice that names the server and the doctor, a way to withdraw and delete, and a named data fiduciary (ties to G5, G6) | Owner |
| — | Family invite links: add the release (Play App Signing) SHA-256 to `prototype/public/.well-known/assetlinks.json` (placeholder still there), then redeploy the website; the new `prototype/app/join` page is the fallback for people without the app. Until then the in-app paste path works (account lane, see lane notes) | Owner |
| — | Account lane server update: apply `android/supabase/migrations/20261027210000_sahara_account_lane.sql` (real "Joined", change read-only/edit, in-app delete account). The app says "needs a server update" until then | Owner |
| — | Supabase security warnings: leaked-password protection, lock `rls_auto_enable()` (`WORK_STATUS.md` §3) | Owner |
| — | Send the CARE team message in `TEAM_HANDOFF.md` with the corrected line from the CARE brief | Owner |
| — | Re-tapping a marked body part: move the spot or add a new one? About 1 h once decided | Owner decides, Awaiz builds |

### Other open items from the chats
| Item | Next step | Who |
|---|---|---|
| A11, H17, I1, I2, I4 | Pilot: name one centre and a doctor-side contact (Sujay offered on 9 Oct), and check whether ethics review is needed | Team + owner |
| A14 | Agree the final logo (palliative orange/marigold; sunflower idea) | Awaiz + team |
| A16 | Go through each tool with the clinicians, one session per tool | Team |
| H14 | Decide how judges can see the code (the repo is private) | Owner |
| I7 | Write up the 20 Oct mentor-meeting minutes (dummy data, refine the goals algorithm, KPIs) | Owner |
| I9 | TANUH pitch draft: keep or drop | Owner |
| I10 | Find or recreate `Technical thoughts and questions.docx` | Team (Sujay) |

### From the CARE / OHC research (27 Oct)
Sources: `docs/research/CARE_DEEP_DIVE_2026-10-27.md`, `CARE_VS_SAHARA_FOR_TEAM.md`, the team deck `docs/presentations/Sahara_CARE_Team_Briefing_2026-10-27.pdf`; raw crawl notes (all 473 docs pages, 75 site pages, 84 repos) were kept outside the repo.

| Item | Next step | Who |
|---|---|---|
| — | Email OHC (support@ohc.network, Slack slack.ohc.network; Bodhish Thomas CEO, Vignesh Hari CTO) using the draft in `everyday.md`. Ask for: a family-inbox pilot with a palliative CARE site, licence for care_scaffold / docs / care_spec / care_report_templates (no licence file), review of our 3 Questionnaire drafts | Owner (team agrees wording) |
| — | Name pilot candidates in the email: Pallium India (CARE live since 5 Dec 2025, 16 home-care teams), Niram Palliative Care (Tumkur), Arike, IAPC Kerala (care.iapckerala.in). Source: ohc.network blog | Owner |
| — | Tell OHC privately about `ccm_uhi`: its endpoints have no login and a booking ID returns the patient's name, phone, DOB and address (`ccm_uhi/api/appointment.py`). Responsible disclosure, not public | Owner |
| — | ~~Medicine picker: evaluate bundling OHC's `cdci` drug list (NRCeS CDCI/SNOMED, CC BY 4.0, India-only SNOMED terms; includes morphine, fentanyl, tramadol) for offline medicine search with codes. Keep attribution (`DATA_LICENSE.md`, `NOTICE`)~~ done in the meds lane (branch `sa4-meds`) | Awaiz |
| — | Body map: compare our 12-part 3D body with the MIT front/back SVG maps (40 regions) in `care_pressure_sore_fe`; consider them as a 2D fallback / CARE question-type plugin later | Awaiz (later) |
| — | Rename the goals-of-care Questionnaire slug to 25 characters or fewer (docs limit 5–25; e.g. `sahara-goc-conversation`). A CARE instance superuser must load our forms; forms freeze once active, so finalise wording first | Awaiz + team |
| — | Pitch wording: CARE does have citizen-facing tools (health.kerala.care, KARE) and ISO 82304-2 claims; say "CARE has no family app", never "no patient side", and claim no certification | Owner |
| — | Family video visits: `care_zoom_be/fe` (MIT) is a working pattern (patient OTP + guest passcode links) if teleconsults are wanted after the hackathon | Later |
| — | Offline queue: signed-in saves fail without internet ("Couldn't save"); add a send-later queue before real field use | Awaiz |

### Lane notes (27 Oct)
- **Privacy lane (branch `sa4-privacy`):** `CarePrivacy.kt` (pure Kotlin, `CarePrivacyTest`) replaces Indian mobile/landline numbers, emails, Aadhaar-like 12-digit numbers, PAN and PIN-code addresses with `[phone]`, `[email]`, `[aadhaar]`, `[PAN]`, `[address]`, `[pincode]` before upload. Hooks: `CareCloud.saveEntry` (symptom check-ins only; the phone stores the same redacted copy the server holds) and `saveSharedDraft` (conversation words, sections and review context; quotes and source hashes are rebuilt so the context still validates; the local draft keeps the original). Signed-in symptom form and the send-for-review step show a one-line English/Hindi notice. Names, photos, journal/visit/task text and care-contact phones are not changed (owner decision). Not tried on a phone.
- **Polish lane:** an up/down swipe over the pain-strength bar (3D pain body) or the check-in / feedback slider now scrolls the page and never changes the score; only a tap or a deliberate sideways drag sets it (`SaharaSliderTouch.kt`, unit-tested in `SliderTouchPolicyTest`). TalkBack and keyboard still adjust the slider. A second tap on an already-saved check-in no longer adds a second entry, and the "please wait" message now says a change is still saving. In the doctor's embedded Daily care, the "Symptom tracker" button did nothing; it now opens the symptom diary. Not tried on a phone (no device or emulator); the swipe androidTest compiles but has not run.
- **Hindi lane (F1, branch `sa3-hindi`):** one English/हिन्दी choice (`CareLanguage.kt`, inline `t("English", "हिन्दी")`) now covers every screen: switch on Welcome, More and the doctor Account tab; an account with no saved choice inherits the phone's last choice, so Hindi survives sign-in. Fully Hindi: Welcome, Today, Daily care, Calendar, Care team, Lab history, Reminders, check-in, Medicines/prescription scanner, Docs + report review, Nearby, More/Account/Add family, Talk, note reader, read-aloud/translation, symptom-diary save errors; doctor: signed-in workspace, patient day, medicines, visits, overview, progress, conversations, care team, reports, visit/prescription editors. Saved values (moods, categories, statuses, relations, reporters) stay English. Left in English: doctor walkthrough demo (`DoctorDemoUi`), recording/capture panels (`DoctorRecordingReviewUi`, `DoctorAudioCapture`, `DoctorRecordStage`, `DoctorLocalPreviewCapture`, `DoctorPreparationQuestions`, `DoctorConversationsUi`, `DoctorAttendees`), medicine status-chip labels, AI-organizer warnings, Emergency card (already partly bilingual), Google Translate legal notice. `CareHindiCoverageTest` fails on English-only text in the covered screens. Hindi wording not reviewed by a native clinician; not tried on a phone.
- **Account lane:** the invite list's "Joined" was a guess from member counts; it now comes from the server (the invitation's server id is matched against open invitations and, once the owner applies `20261027210000_sahara_account_lane.sql`, against the membership that invitation created), with Cancel for open invites and "used or cancelled" when the server can't say more. The owner can switch a family member between read-only and edit (needs that migration; until then the app says so and suggests remove + re-invite), sees join date and the 30-day access end the server already sets for family, and removal is verified. Sign-up reports what really happened (signed in / confirmation mail requested / email already registered), maps auth errors to plain English/Hindi, offers "Resend confirmation email", and says plainly when the server's mail setup is the cause. Signed-out people can paste an invitation before signing in. "Delete my account and data" now runs in the app (same migration; falls back to the email request page). Not tried on a phone.
- **Assist lane:** app feedback (More → Feedback, doctor walkthrough, doctor's embedded care) is now sent to Sahara's server when signed in, through `sahara_submit_app_feedback` in `android/supabase/migrations/20261027190500_sahara_app_feedback.sql` — **owner applies**; until then each save stays queued on the phone, says "not sent yet", and is retried when Feedback is reopened by the same account. "Sent to Sahara" shows only after the server confirms the id. Signed-out feedback stays on the phone and says so.
- **Assist lane:** AI drafting stays off (no provider chosen, A10/A19/B6). The draft step now shows "AI drafting is off" instead of an empty space, and the line suggestions say "word matching, not AI". Remote assist (Sarvam transcription/draft/translation) stays hidden and makes no network call while off. Offline translation (Google ML Kit on-device, Hindi ⇄ English, pack downloaded once) states plainly when the translator or pack is unavailable and when a download is waiting for Wi-Fi.
- **Assist lane, still open:** the doctor walkthrough's "Load AI draft" button (`DoctorWorkflowUi.kt`) loads a pre-written sample labelled "AI draft"; left for the doctor lane. Feedback rows are not removed by `healthathon_close_account` (they cascade only if the auth user is deleted). Not tried on a phone.
- **Daily lane:** walked Daily care, + Add, check-in, symptom diary/trend, 3D pain body/history and reminders. Add/edit/remove already save to the server first when signed in (Room after), reload on restart, and reach the invited doctor's Progress and embedded Daily care. Fixed in reminders: dated entries with no time (+ Add visits, care-team tasks, untimed calendar entries) used to alert at local midnight; they now alert only at a time chosen for that day in Reminders (the row says "No alert time chosen"). Alerts use exact alarms when Android allows them (new SCHEDULE_EXACT_ALARM permission and an "Allow on-time alerts" button), otherwise the old inexact alarm. Tapping a reminder notification now opens Reminders (the `open_reminders` extra was never read). Reminders no longer lists past dates or lab results. Tests in `CareReminderRulesTest`; not tried on a phone. Reminder times stay on the device (they are alarms on that phone), not on the server.
- **Today lane:** Today's "Your care team" opened the clinician (doctor) workspace for patients and family; it now opens the family's Care team (tasks and conversation requests), as the walkthrough already did, and the doctor workspace has its own entry in More. Medicines "See all" and "See all N tasks" now open Daily care at that section instead of the top. Tapping a reminder notification did nothing special (its `open_reminders` extra was never read); it now opens Today for the person whose saved item is due (`CareTodayRules.personForItem`, `MainActivity.handleReminderIntent`, single-top so the running app is reused). Up next lists dated visits soonest first (undated ones were shown first); today's check-in no longer picks up a symptom-diary or pain-map note; empty Visits / To do / Journal say what to tap next (English · Hindi), without the tap hint for read-only family. After the last person is removed, the next person added starts on Today, not on the leftover More tab. Unit tests: `CareTodayRulesTest`. Not tried on a phone.
- **Motion lane:** `SaharaMotion.kt` is now the one motion set (screen transitions plus expand/collapse, first-load list stagger, card press, selection, value change, loading/empty crossfade), all 110–240 ms, no springs. Every duration and delay goes through `SaharaMotion.ms()`, which is 0 when the phone's "Remove animations" setting is on (`SaharaMotionTest`).
- Applied only through shared pieces: `SurfaceCard`, `ActionCard`, `DetailPanel`, `CareModuleCard`/`CareModuleList`, `InfoDisclosure`, `RawTranscriptPanel`, `ConversationRow`, `CareNavigation`, `ChoiceRow` and the CareApp snackbar (real save confirmations get a small tick). The ActionCard corner is now 20 dp, the same as every other card. Screen logic is unchanged. Bottom sheets keep Material's own motion. Not tried on a phone.
- **Meds lane:** bundled 3,300 medicine *names* from OHC's CDCI copy (NRCeS/C-DAC, CC BY 4.0; 88 KB, names + SNOMED substance code only, no strengths/forms/brands, so it cannot suggest a dose) as offline spelling suggestions under every medicine-name field (Add medicine, scan review, doctor prescription editor). Attribution in `android/app/src/main/assets/medicine-names/` (`NOTICE.txt`, `DATA_LICENSE.md`, `CDCI-License.txt`) and More → About; rebuild with `android/scripts/build-cdci-medicine-names.py`. The CDCI licence allows redistribution only within India.
- Family Daily care: each medicine now has **Missed today** (appends a "skipped" report to the intake history, server RPC `healthathon_report_medicine` when signed in) and **History** (that medicine's taken/missed reports); ticking the box already logged "taken" on the server via the check-off trigger. Doctor's Medicines screen gets an **Intake history** tab next to Current/Past, so family reports are one tap away. Scan review can open unmatched medicine photos; the practice-workspace medicine list is labelled as sample data. Unit tests: `MedicineNamesTest`, `MedicineMissedReportTest`. Not tried on a phone. Owner to confirm migrations `20261023053321_doctor_medicine_history` and `20261023071214_doctor_prescription_groups` are applied on the server.
- **Doctor lane (33ac1f5):** the signed-in doctor Home now shows real upcoming visits and this week's check-ins across the doctor's connected patients (tap opens that patient's Visits or Progress), with a Refresh; Schedule lists every open real visit; Patients has search by name or patient ID (`SH-` + first 8 characters of the care-profile ID, or the full ID; B18) and a Join box for a doctor invitation code. The family's doctor-invite screen shows the patient ID. A verification card shows pending / approved / not approved with "Check again" (approval stays manual in the database, G1). Sample patients appear only in a card labelled "Walkthrough · sample patients"; the old synthetic server workspace no longer loads on entry. Logic unit-tested in `DoctorConnectedHomeTest`. Not tried on a phone.
- **Docs / Lab history (sa3-docs):** E6 "Cancer documents" filter (PET-CT, biopsy, histopathology, staging; file name suggests, family sets or clears; label kept on the phone). E5 "Lab result" in Read and copy facts fills name/value/unit/range from the OCR line; the saved entry keeps the original line, page and file hash. E7 chemo/radiotherapy days (all-day Calendar entries) drawn as dashed lines on Lab history charts.
- Report search takes Hindi query words (हीमोग्लोबिन, खून, जांच, बायोप्सी, पेट-सीटी …) for file names and reviewed text. Reports can now be renamed and scanned with the camera; Docs says report files stay on this phone and are not uploaded.
- C5: optional password on the care-note PDF. sa3-docs first put it in an AES-256 ZIP; at the integration merge it was switched to the QR lane's real PDF password (`PdfPasswordLock`), so any PDF reader asks for the password and no ZIP app is needed. Not tried on a phone.
- **QR lane (emergency card):** Today shows a large QR. When the profile owner is signed in it opens a read-only page at `sahara.wedevit.in/emergency#t=…` with the card the family ticked. The page shows the full latest patient-confirmed goals-of-care note, contacts and medicines. Tapping the QR opens a full-screen white view at maximum brightness; it can switch to the offline text code.
  - Buttons: Update card (same link, so printed cards show the update), Replace the link (old one stops at once), Share, Print (Android print screen), Save PDF, Locked PDF (AES-256 password the reader asks for), and Read the confirmed note. "Done" messages appear only after the write succeeds.
  - Until the owner applies `20261027190000_emergency_card_links.sql` (Saanthvana project) and deploys `prototype/app/emergency`, Today says "The web link becomes live after setup". The offline QR and the PDF already work.
  - The link can be revoked and expires after one year; it is not single-use, because a printed card has to scan more than once.
  - Verified: the SQL test passes on a local Postgres 16, the web unit tests pass, and PDF locking was checked with Poppler and pypdf. Not tried on a phone.
- **Nearby lane (27 Oct, D5/D6/D2):** Nearby now starts with an Emergency card (English + Hindi). Each number opens the phone dialler and never calls by itself: 112 (ERSS, Ministry of Home Affairs, https://112.gov.in/about); 108 ambulance and 102 ambulance for pregnant women and babies, run by each state under NHM (MoHFW https://mohfw.gov.in/sites/default/files/02Chapter.pdf; Lok Sabha answer https://eparlib.nic.in/bitstream/123456789/956165/1/AU5611.pdf); 14567 Elderline, 8 am – 8 pm (Ministry of Social Justice, https://pib.gov.in/PressReleasePage.aspx?PRID=1758880). No palliative/cancer helpline is shown because no government source was found; Saath Saath (1800-202-7777) is run by palliative organisations, so the team decides. "Hospitals near you" opens the maps app's own search. The app does not read location: the manifest removes the location permissions, and only 9 of 1,631 listings have coordinates, so sorting by distance would not mean anything. Instead, "Nearest centres" filters by state, then district. Districts are listed for 7 states; a centre whose address names the district also counts. Logic is in `NearbyHelp.kt`, tests in `NearbyHelpTest`. Not yet tried on a phone.
- **Nearby lane, search and filters (27 Oct):** search covers name, city, district, state, services and PIN (with or without the space), ignores case, matches part-words, needs every word, and accepts Hindi state/city names and a few service words; mixed spellings (Bangalore/Bengaluru, Ernakulam/Ernakulum, Bombay/Mumbai…) match each other. Service chips are those in the data plus Hospice (listing name or address says hospice); "Clear filters" appears whenever a filter is on. Centre Call now dials "91 98…" numbers as +91 (650 listings were undialable); every card has Share (listing, directions link, "call to confirm") and says "No map location" when it has no coordinates. The morphine chip's Hindi now says "listed", not "available". Not in the data, so not shown: opening hours, emergency, imaging, pharmacy, ambulance, oxygen, blood bank. "Distance from me" is not built: the app has no location permission and only 9 of 1,631 listings have coordinates. The map draws all 9 located centres; it has no clustering because there are too few to need it. Unit tests in `NearbyHelpTest`; not tried on a phone.
- **Geo lane (sa5-geo, 27 Oct):** the Nearby map now shows 1,602 of 1,631 centres (was 9). `scripts/geocode_centres.py` builds `assets/india-geo.json` (81 KB, per-centre results only) from GeoNames (CC BY 4.0, see `DATA_LICENSE.md`): exact 9 (unchanged), PIN area 409, town area 1,149, district area 35, list only 29 (no state-level guesses). The PIN's post-office names are looked up in the GeoNames gazetteer because the postal file's own coordinates were a median 15 km off; hiding the 9 known coordinates, the method lands within 0.8–6.4 km of them. Map pins are clustered (MapLibre built-in); approximate pins are hollow; a cluster that is one shared area opens as a list. Cards say "Approximate location (PIN/town/district area) · call to confirm" (Hindi too); Directions still searches the address. The count under the map is per precision, with the GeoNames credit. data.gov.in pincode directory could not be downloaded here (connection reset/503). Tests: `CentreLocationTest`, `scripts/test_geocode_centres.py`. Not tried on a phone.
