# Sahara team handoff

This is the starting point for another developer or Codex chat. Latest native verification: **20 October 2026**. Play status below was last checked **19 October**, not refreshed by this push.

## Start here

- Repository: https://github.com/basithladdu/healthathon (private), branch `main`.
- Primary checkout: `F:/CODE/healthathon`. `android/` is the Kotlin app; `prototype/` is the React/Next.js website. Do not recreate this as React Native or a web wrapper.
- Read this handoff, then `PROJECT_CONTEXT_MAP.md` and `android/release/store-draft.json`. The latter preserves distinct published, pending, rejected and test-build histories.
- Current product: **Sahara - A Palliative Care Companion**. Play title: **Sahara: Palliative Companion**. Release package: `in.wedevit.sahara`; debug package: `in.wedevit.sahara.development`.
- Existing Saanthvana branding/domain in the submitted deck must remain available. Current web: https://sahara.wedevit.in; earlier https://saanthvana.wedevit.in stays active.

## Deliverables

| Artifact | Location / identity |
| --- | --- |
| Latest installable test APK | `output/android/Sahara-0.6.0-20Oct-test.apk`; 0.6.0 (7), 300,504,493 bytes; Android debug signature; installs beside the Play app |
| APK SHA256 | `5DC9D96176407A8E35A34709653D9897FBD4491FA79E9F908459933509BAC9AC` |
| Native source for that APK | `e683e7e`; subsequent handoff/PDF commits do not change its native source |
| One-page mentor update | `output/pdf/Sahara_Mentor_Update_2026-10-20.pdf`; editable `build_sahara_mentor_update.py` alongside it |
| Presentation PDF | `output/pdf/Saanthvana_Submitted_Presentation.pdf`; 8 slides, 41 original links retained |
| Original submitted deck | `submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx`; unchanged |
| Detailed evidence | `output/android/verification/features/mentor-delivery.json`, `mentor-apk-final.log` and the release metadata |

APK files are excluded from Git. The latest APK and mentor files are being attached to a **private draft GitHub release**, separate from Google Play; the verified draft-release URL is recorded in the context map once its upload completes. A normal clone includes source, PDFs and committed evidence, not signing keys, installed model weights or private records.

## What is implemented

- Patient/family daily care: medicines, visits, tasks, check-ins, journal, dated calendar/lab history, recordings with consent and attributed dictation, private documents and durable inexact reminders.
- Source-linked care notes with immutable versions and exact-version confirmations. The shared web/native account workflow includes independently approved clinician roles, patient-first/family confirmations, permissions, expiry/revocation, protected QR retrieval and named-account task transitions. No real clinician approval was fabricated.
- Local report/prescription OCR with retained originals, corrections and page/line-linked facts. Latin and Devanagari readers are bundled. Original report/audio files stay on the phone; sharing note facts does not synchronize those files.
- On-device Hindi/English translation with an optional downloaded ML Kit pack, original text alongside translation, numeric-sequence checks and human review. Google attribution is included. ML Kit is not described as open source.
- Optional **Qwen3-ASR-0.6B int8** local recording transcription through sherpa-onnx 1.13.8. Six model files are pinned by revision, size and SHA256. Download: **987,015,347 bytes**; requires a 64-bit process and at least 2.5 GiB availableRAM. Audio/person/consent binding, original machine words, reviewed corrections and segment offsets are retained. No patient audio is sent to an external ASR provider.
- National directory: **1,631 public listings across all 36 states/UTs**, from 81 Pallium India/IAPC pages. 1,509 have a public telephone. Original 33 Karnataka IDs and 9 source-backed coordinate pairs remain. Unlocated addresses are not assigned invented coordinates; morphine source claims do not establish live stock.
- Visible Android choices/actions replace dropdowns and click-to-expand sections. Existing useful controls and source evidence remain available. Keep these UI rules in future work.
- Website shared care: https://sahara.wedevit.in/shared-care. Hospital intake is **web only** at https://sahara.wedevit.in/hospital-survery; do not port that survey into Android. Reviewed public facilities: `/reviewed-centres`.

## What was actually verified

- Latest local build: `assembleDebug`, `testDebugUnitTest`, `compileDebugAndroidTestKotlin` succeeded. **147 unit tests / 25 suites; zero failures, errors or skips**. Instrumentation sources compiled; no new instrumentation/device execution is claimed.
- APK signature, package/version, packaged current directory bytes and Qwen notice, and 16 KB ZIP alignment were checked. The Sherpa AAR's eight 64-bit native libraries passed static alignment inspection; this is not a 16 KB runtime test.
- All 8 directory source/parser/preservation checks passed. Raw public directory snapshots and source hashes are preserved under `output/research/india-directory-20261020/`, separately from app assets.
- Previous 19 October web checks: **284 tests**, TypeScript/scoped lint, real sign-in/sign-out/reload and390 px browser flows. Database evidence:47 workflow +18 directory checks and real role-isolation tests within rollback. These are dated previous results, not fresh21October live verification.
- Both final PDFs were rendered and visually inspected. Historical failed model/build/runtime checks remain labelled; do not turn them into a pass count.

## What is still incomplete

1. **Current APK phone testing**: no phone was connected for this build. Exercise installation/update, microphone consent, saved-audio ASR, Hindi/English OCR/translation, app navigation/insets/keyboard, reminders/permissions/reboot, account switching and safe retry/reload. Use fictional/non-patient inputs first.
2. **Clinical language validation**: Qwen had promising narrow desktop Hindi/English/public Hinglish results with errors. Whisper-small performed badly on Hindi/mixed speech and was rejected. None of this establishes clinical accuracy, speaker identification or reliable rapid code switching. Keep original audio/text and mandatory review.
3. **Account operations**: the last recorded check still had incomplete ordinary signup/reset delivery, no custom SMTP and no approved Sahara callback. Existing confirmed-account sign-in works. Recheck actual state before making changes. Zoho remains paused; do not alter callbacks or provider/billing settings without authorisation.
4. **Human governance/pilot**: actual independent verifier assignment and a clinician/family pilot remain pending. No patients have been onboarded by this work. Before identifiable patient use, validate access/deletion/recovery, explain recording/sharing in the participant's language and obtain the clinical institution's permission. If conducting health research, have its ethics committee determine review before recruitment; a small 'pilot' is not automatically exempt. [ICMR handbook](https://www.icmr.gov.in/icmrobject/uploads/Guidelines/1724914217_handbook_on_icmr_ethical_guidelines_2018.pdf).
5. **Generative clinical drafting/Sarvam**: not enabled. The promised sponsor access has not arrived according to the user; an existing workspace/credits are not assumed to be that allocation. No new key, training setting, billing or patient-provider calls were authorised. Free-tier Gemini must not receive patient input; public cloud photo/audio assistance remains off.
6. **Future Play update**: no 0.6 AAB or production-signed release was uploaded/submitted. Native SDK disclosures, privacy/listing/reviewer claims and actual device checks must match the candidate before a future release. A test APK is not Play approval or clinical readiness.

## Play and release boundaries

Last live check 19 October: Production **0.4.1 (5) available in India**; **0.5.0 (6) Production changes in review**. These statuses can change; inspect Console before reporting them as current. The existing Devit account is Organization; do not restart DUNS/identity conversion. Earlier rejections stay in history. All 59 tester invitations were already sent in separate 57 + 2 groups; do not resend or copy recipient lists into Git. This push does not authorise changing Play, account billing, payouts, identity documents or patient enrolment.

## Reproduce the current build

JBR/JDK21, Android SDK 36 / build-tools 36.1.0. On the existing Windows machine:

```powershell
cd F:/CODE/healthathon/android
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'F:/BuildCaches/healthathon-gradle'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin '-Pksp.incremental=false' --no-daemon --max-workers=1 --console=plain '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=96m -Xss512k -Dfile.encoding=UTF-8'
```

The first attempt failed under native memory pressure; a later KSP incremental-cache class error was bypassed by the explicit non-incremental check above. A malformed unquoted PowerShell property also failed; retain the quotes. Do not kill unrelated processes or reuse/rename stale APKs as new builds. The source-check workflow has an optional test-APK input, but GitHub run 39402505540 never started because the account's billing/spending gate blocked runners; no payment setting was changed.

Directory tooling: `python android/scripts/test-india-directory.py`. Python dependencies for refresh: `requests`, `beautifulsoup4`; see `android/directory/README.md`. Speech CPU diagnostics: `android/scripts/verify-offline-speech.py` with explicitly non-patient 16 kHz PCM fixtures and sherpa-onnx 1.13.8; never call diagnostic output a clinical accuracy result. Model files live outside Git and are downloaded and hash-checked in the app.

Website development, with Node 22.13+:

```powershell
cd F:/CODE/healthathon/prototype
npm ci
npm test
npm run dev:next
```

Existing backend migrations are already applied. Do not blindly replay historical migrations whose remote/local timestamps differ. `prototype/AGENTS.md` applies to web source changes. Build success does not establish the current live provider/backend state.

## Preserve and protect

- No global Codex state, browser profiles/storage, private conversations, credentials, signing files, OTPs, tester lists or verification documents belong in Git or the release assets.
- APK/model/build/cache files use artifact storage or local caches, not Git. Unreviewed account screenshots, UI dumps and logcat remain local.
- Four pre-existing deletions of presentation generators under `source-materials/ppt-sources/devit-utility/docs/presentation/` were intentionally left unstaged. Establish the author's intent before committing or restoring them.
- Work narrowly, preserve concurrent edits, fetch before merging, and keep one current handoff/context map. Do not substitute old deck claims for current implementation, or source/unit tests for actual phone/clinical evidence.

---

## Historical preparation snapshot -13 September 2026

The earlier handoff below is preserved as history. Its 'current' decisions describe that date and are superseded by the current handoff above.

# Health-a-thon team handoff

## Read first

1. `PROJECT_CONTEXT_MAP.md` - complete timeline, contacts, source map, conflicts and missing inputs.
2. `MEETING_EVIDENCE_MAP.md` - direct clinician quotes versus later product interpretation.
3. `submission.md` - exact Health-a-thon Round 1 fields and paste-ready answers.
4. `submission/ASSET_MANIFEST.md` - deck, video, QR and screenshot status.
5. `USER_SUPPLIED_CONTEXT_2026-09-13.txt` - raw capture of the latest user-supplied dashboard and programme context.
6. `PRODUCT_NAMING.md` - working name recommendation and visible-web collision screen.

## Current application decision

- Track: Cancer
- Primary user: Doctor / Care Team
- Use case: Patient Follow-up & Continuity of Care
- Product: Continuity Loop
- Existing work: Yes

## Current evidence

- The public MVP URL was checked in Chrome on 13 September 2026 at desktop and 390 px mobile width.
- The local prototype is synthetic, browser-local and deterministic.
- The current visual deck candidate is eight slides and the submission copy is under the 25 MB limit; product-surface screenshot slots are intentionally blank until the teammate's UI is ready.
- The local video is a 24-second silent slide walkthrough; it is not a narrated live screen recording.
- The QR code points to the public MVP URL.
- No form submission, upload, message, commit or push was performed in this preparation pass.

## Do not claim

- Live AI transcription or summarisation.
- Real patient data, clinical outcomes or model accuracy.
- Legal validity, DNAR/treatment-order status or Advance Medical Directive conversion.
- Production authentication, EHR/ABDM integration, durable clinical storage or live messaging.
- Approval of either supplied clinical DOCX.

## Highest-priority asks for the next team meeting

- Confirm the authoritative clinician-approved conversation framework.
- Confirm the final team roster, roles, bios and application contact details.
- Provide or recreate `Technical thoughts and questions.docx`.
- Decide whether the demo video should be replaced with a narrated screen recording.
- Name one pilot setting, workflow owner, baseline and 20-case reference-set plan.
- Decide which public supporting links can be opened without access requests.
- When the UI branch lands, replace only the reserved screenshot slots and rerun the deck render/package checks; do not claim live AI or clinical validation unless separately evidenced.
