# AI handoff: Sahara Android (continue from here)

Written 26 Oct 2026 for the next AI assistant picking up this work. The user is **Shaik Muhammad Awaiz** (UI/Android on team "Zeros and Ones"). Read this whole file before acting. Also read `HANDOFF.md` (what changed in the app) and `WORK_STATUS.md` (status snapshot), both in this folder.

---

## 0. The big picture

**What Sahara is:** a palliative-care companion app for India, built for the Health-a-thon, with three users:
- **Patient:** records symptoms (3D pain body), medicines, visits and check-ins, and reads notes from the doctor.
- **Family:** sees and helps with the patient's care on their own phone.
- **Doctor:** records a goals-of-care conversation, turns it into a reviewed note and shares it.

**Where it stands:** until now the app was a **polished demo that only worked on one phone**. The doctor, patient (Rahul) and family (Arjun) "walkthroughs" all read the same pre-made data stored on one device. **The current goal is to make it work for real people on separate phones,** with real accounts, a real server, and real sharing between patient, family and doctor. That makes the "one clear journey" demo real for the judges and lets palliative doctors actually try it.

---

## 1. Environment (Windows machine)

| Thing | Value |
|---|---|
| Repo | `github.com/basithladdu/healthathon` (owner basithladdu; Awaiz pushes as `shaik2501`) |
| Working copy | `E:\Healthathon\sahara-merged-20261016` (a **git worktree**; don't `cd` to other checkouts) |
| Branch | `awaiz/at-73c5000`. GitHub `main` = this branch as of `f486038`. **Local and not pushed:** `b7a9f82` (recording + walkthrough tools) and `63c5b27` (Saanthvana, sign-up, Add family) |
| Android app | `android/` — Kotlin 2.2.20, Jetpack Compose, Room v7, AGP 8.13, SDK 36/min 26, package `in.wedevit.sahara` (debug: `in.wedevit.sahara.development`), version **0.6.0 (7)** |
| Source | `android/app/src/main/java/in/wedevit/care/` |
| Build + test | from `android/`: `JAVA_HOME=/d/SetUp-files/Android/jbr ANDROID_HOME=/d/SetUp-files/AndroidSdk cmd //c "E:\\Healthathon\\sahara-merged-20261016\\android\\gradlew.bat" assembleDebug testDebugUnitTest compileDebugAndroidTestKotlin -Pksp.incremental=false --no-daemon --max-workers=1 --console=plain` (~3 min). Last green: **315 unit tests / 55 suites, 0 failures** |
| Emulator | `D:\SetUp-files\AndroidSdk\emulator\emulator.exe -avd Sahara_Doctor_Demo -memory 4096 -no-snapshot-save` (needs 4 GB; it sometimes stops responding — kill the `qemu-system-x86_64` process and restart) |
| Install | `D:\SetUp-files\AndroidSdk\platform-tools\adb.exe install -r -d android/app/build/outputs/apk/debug/app-debug.apk` (`-d` is needed because the emulator once had 0.6.1 (8)) |
| Backend | Supabase project **Saanthvana**, id `kjnhtakqzboayqorrlue` (region ap-south-1). Reachable through the user's Supabase MCP connection |
| Old backend (don't use) | `cghlkqeqiokjqdionrkc`: not in the user's Supabase account; the Play Store 0.6.0 build and basithladdu's work still point there |

**User preferences and rules:**
- Plain English and short explanations; the user is not a backend engineer.
- The user's `CLAUDE.md` asks the main model to plan and review, and **delegate implementation to `worker` subagents**.
- **Ask before** any live-server write, push to `main`, force push, or account creation. Never force-push.
- The user likes seeing results **on the emulator** with screenshots.

---

## 2. Work items: purpose, goal, status, how to continue

### A. Connect the app to a real server (Saanthvana)
- **For:** real accounts and records stored on a server, so patient, family and doctor can use separate phones.
- **Goal:** every account, sharing and invite feature runs against Saanthvana.
- **Status:**
  - ✅ **Database:** the 9 missing updates were applied to Saanthvana (now 14 of 14; existing 1 user and 1 profile untouched). Supabase recorded them under new version stamps (`20261026171032`…`20261026171544`), so **don't run `supabase db push`** on it blindly.
  - ✅ **App:** `CareCloud.kt` now uses `base = "https://kjnhtakqzboayqorrlue.supabase.co"` and `PUBLIC_API_KEY = "sb_publishable_bheyh26N5MKpXNEJDQgq8w_oDRhW8Xe"` (the public, client-safe key). **Built and committed locally in `63c5b27`.** Not yet tried against the server on a device.
- **Continue:**
  1. Sign up and sign in on the emulator.
  2. Create a care profile while signed in and confirm a row appears in `public.healthathon_people`.

### B. Turn sign-up back on (email + password)
- **For:** new patients and family must be able to create an account. It was hidden because email delivery wasn't set up.
- **Goal:** "Create account" and "Forgot password?" work for anyone.
- **Status:**
  - ✅ **App:** `CareEmailAvailability.ready = true` (built and committed in `63c5b27`).
  - ⛔ **Server:** **waiting on the user** in the Supabase dashboard. Supabase's built-in email only reaches team addresses, so either turn **off "Confirm email"** (Authentication → Sign In / Providers → Email; for testing) or add **custom SMTP**, e.g. free Brevo (Authentication → Emails → SMTP).
- **Continue:** once the user confirms the setting, test sign-up with a fresh email on the emulator.
- **Decision already made:** the user chose **email + password only**. No Google or phone OTP.

### C. Add family by WhatsApp link with auto-join
- **For:** families found joining hard; they had to copy a 48-character code into Account → Join.
- **Goal:** the patient adds family like a contact. A WhatsApp link opens Sahara, and after sign-in the family member is joined automatically.
- **Status:** 🟡 **code written, builds, unit tests pass, committed locally in `63c5b27`. Not yet tried on a device.**
  - **Files:** new `CareInviteLink.kt` (link/code parsing, pending-join store, invite log) and `CareAddFamily.kt` (`AddFamilySheet`, `PendingJoinDialog`); changes in `CareAccount.kt`, `CareModel.kt` (`joinedRefresh`), `CareUi.kt` ("Invite family" card on Today), `MainActivity.kt` (reads the link) and `AndroidManifest.xml`.
  - **Asset links:** `prototype/public/.well-known/assetlinks.json` lists the debug SHA-256 `01:6D:61:8B:5F:D2:DC:7E:B0:AD:9C:2E:96:46:5F:DF:0E:E0:BA:AB:9C:2B:6A:76:27:93:FB:13:88:85:F8:5B`. The release entry is the placeholder `REPLACE_ME_RELEASE_SIGNING_SHA256_FINGERPRINT_BEFORE_DEPLOY`, which must be replaced or removed before deploying.
  - **Share text:** "You're invited to see Rahul's care in Sahara. Tap to join: https://sahara.wedevit.in/join?c=<code>&n=Rahul", followed by the code as a backup.
  - **Caveats:**
    - The "Joined" label is a guess: the oldest open invite is marked when the member count rises.
    - The `n=` name in the link is display-only and could be spoofed. The confirm card also shows the user's email.
    - `MainActivity` uses the standard launch mode, so a link starts a new instance; the pending join survives through SharedPreferences.
- **Intended design:**
  - An "Add family" button on Today (and in Account) opens a sheet with name, relationship chips and access (**Can view** / **Can view and add**), then **Send invite on WhatsApp**.
  - The share text includes `https://sahara.wedevit.in/join?c=<48-char code>`, with the code as a backup.
  - Intent filters: https `sahara.wedevit.in/join` (autoVerify) and `sahara://join`.
  - A **pending join** is stored until the family member is signed in, then a confirm card "Join Rahul's care?" calls the existing `cloud.join(code)`.
  - A **Paste invite** button accepts the full message.
  - Unit tests for link and code parsing.
- **Known limit:** on Android 12+, https links open the app directly only when `https://sahara.wedevit.in/.well-known/assetlinks.json` is **deployed** with the signing fingerprints. The website is basithladdu's Vercel project and isn't deployed by us. Until then the `sahara://` link and the paste/code backup must work.
- **Continue:**
  1. Finish or repair the worker's code and build.
  2. On the emulator, test with two accounts: owner invites, the second account opens `sahara://join?c=…` (`adb shell am start -d "sahara://join?c=<code>"`), signs in, then joins.

### D. Real doctor account (Dr Ravi Kiran)
- **For:** the user wants to log in as a real, approved doctor, not the walkthrough.
- **Goal:** a doctor account that can accept a patient's doctor invite and review and verify notes.
- **Status:** ⛔ **not started, waiting on the user.**
- **Do not** create the auth user directly with the user's suggested `ravikiran@gmail.com` / `Ravi@123`:
  - that email may be a real stranger's;
  - the app requires **at least 10 characters** in a password.
- **Agreed approach:**
  1. The user signs up in the app as the doctor with an email they own and a 10+ character password.
  2. You mark them approved. Doctor approval is the row in `public.healthathon_clinician_requests` with `status='approved'` (see migration `20261019151322_shared_care_clinician_review.sql`). There's a review function plus `healthathon_private.clinician_reviews`, so prefer the proper review path, and **ask the user** before writing.

### E. Recording for the doctor, and walkthrough tools (done locally)
- **For:** recording was blocked for most walkthrough patients, and import/share/read-aloud/scan were off in the walkthroughs.
- **Status:** ✅ done and tested on the emulator, and **committed locally as `b7a9f82` but not pushed**.
  - Recording works for every walkthrough patient, with one-tap consent.
  - Import, share/export, read-aloud and scanning are on in the walkthroughs.

### F. Symptom tracker and 3D pain body (done, on main)
- **Status:** ✅ on `main`.
  - Step-by-step entry; tapping a body part zooms the model; tapping the model marks pain.
  - Each spot has its own 0–10 strength (stored as `painBodyRatings` site→rating), with a Wong-Baker colour bar (0 = no colour) and a contour heat-map glow on a dark viewer.
  - Redesigned diary card.
- **Viewer source:** `prototype/scripts/pain-body-viewer-entry.mjs`, built with esbuild 0.27.3 + three@0.186.0 into `android/app/src/main/assets/pain-body-viewer/viewer.js`.
- **Known issue:** vertical swipes over the 3D model or the strength bar rotate the model or change the score instead of scrolling.

---

## 3. Waiting on the user (ask them)
1. **Supabase email setting:** turn off "Confirm email", or add SMTP (see B).
2. **Doctor sign-up** with their own email and a 10+ character password (see D).
3. **Website redeploy** of `assetlinks.json` (see C), probably via basithladdu.
4. **Pre-existing Supabase warnings:** turn on leaked-password protection, and lock down `public.rls_auto_enable()`, which `anon` can call.
5. **When to push to `main`.** Only on their say-so.

## 4. Not real yet (mock-ups)
| Feature | State | Plan agreed / discussed |
|---|---|---|
| Speech-to-text, translation | Online off (`CLOUD_AUDIO_ENABLED=false`); walkthrough transcript is pre-written; on-device sherpa-onnx/Qwen3-ASR needs a model | Free options: Whisper small on-device, AI4Bharat IndicConformer + IndicTrans2 on a server |
| AI care note | `CareAndroidAssistPolicy.externalAiEnabled = false`; Gemini/Sarvam client code exists; shows "Sample draft" | Needs patient consent and a no-retention agreement first |
| Goals-of-care answers | Rule-based word matching (`LocalQuestionMatcher`) | Plan: AI with full patient context, suggests questions, quotes evidence, flags missed topics; doctor must approve |
| Hospital QR / emergency retrieval | Not built (placeholder card) | Short-lived, read-only emergency summary via QR, with an audit log |

## 5. Decisions already made (don't re-ask)
- **`main`:** replaced with Awaiz's version through an "ours" merge (`f486038`), without a force push. basithladdu's 15 later commits are still in history but none of their file changes are on `main`.
- **Backend:** now **Saanthvana**. This splits the backend from the Play Store build and basithladdu's server.
- **Sign-in:** email + password only.
- **Family joining:** WhatsApp link + auto-join, with the code as a backup.
- **Family view:** Arjun (family) sees the same as the patient for now.
- **Play Store:** next upload must be versionCode **9 or higher** (0.6.0 (7) is in Google review; 0.6.1 (8) was built by basithladdu).

## 6. Suggested next steps, in order
1. Finish and build **C**, build with **A + B**, then test sign-up and a two-account family invite on the emulator. Commit.
2. After the user's dashboard email step, do **D**: approve the doctor account and test a doctor invite plus note review.
3. Ask the user, then push the branch, and push to `main` if they want.
4. Then make the patient side simpler for new users:
   - one "How are you feeling?" entry instead of three;
   - clearer tabs (each tool in one place);
   - plain words instead of clinical or system jargon;
   - Hindi across the whole app.
5. Later: real transcription, AI goals of care, hospital QR, and fixing scrolling over the 3D model.
