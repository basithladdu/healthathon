# Sahara Android: Awaiz's handoff

Last updated 26 Oct 2026 by Shaik Muhammad Awaiz (UI/Android), with Claude Code.

This note lists every change I made to the Android app, what the app is like right now, what was checked, and what is still open. It covers the patient & family app, the doctor walkthrough and the symptom tracker.

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
- **Scrolling:** swiping up/down over the 3D model rotates it instead of scrolling the page, and swiping over the strength bar changes the score. Scroll from the text or tiles. A fix would let vertical swipes scroll the page.
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
