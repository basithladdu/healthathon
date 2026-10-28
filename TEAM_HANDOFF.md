# Sahara team handoff

## 28 October: four-part Health-a-thon package advanced

- Filled working drafts in the **two original organiser templates**: [two-page solution brief](submission/phase-2/final-deliverables-2026-10-28/Sahara-Two-Page-Solution-Brief-working-draft.pptx) and [12-slide final presentation](submission/phase-2/final-deliverables-2026-10-28/Sahara-Healthathon-Final-Presentation-working-draft.pptx). Original templates and organizer email remain preserved in [the source archive](source-materials/healthathon-phase-2/2026-10-27-final-deliverables/README.md).
- Both files were opened and rendered with PowerPoint; the slide count is 2 and 12. Visual review found no clipped content after shortening the brief title. The full-page PNG renders and the unresolved-field list are in the output folder.
- Facts were limited to repository/handoff evidence: current features, Kotlin/Next.js/Supabase/Vercel stack, survey deployment and dated clinician review, existing test boundaries, manual word matching, and proposed-not-live CARE connection. No pilot outcome, cost, AI performance or clinician specialty was invented.
- **Still required:** team code; clinician specialty/profile approval; Shirin's title; team choice of CARE scope; 2–3 measures and baselines/targets; named pilot/doctor; cost assumptions; data owner/consent/grievance decisions; private judge access; real-phone narrated video; organizer deadline and submission portal. The organizer email does not establish these.
- Live GET checks on 28 Oct: Sahara home `200`; `/hospital-survey` `200`; canonical `/hospital-survery` `200`; Play listing `200`; `/join` fails (404). The app-link asset still has a release-signing fingerprint placeholder, so no deployment was made to pretend evaluator access works. No new AAB, Play upload, organizer submission, email, or physical-phone check was performed.
- See [everyday.md](everyday.md), [phase-2 README](submission/phase-2/README.md), and [blockers.md](blockers.md) for the next actions.


This is the starting point for another developer or Codex chat. Read the latest dated checkpoint first and the current Android `HANDOFF.md` / `AI_HANDOFF.md`; older verification and release snapshots remain historical.

## 27 October: latest final-deliverables email and both templates archived

- Read the organiser's **Build Sprint Final Deliverables and Submission Guidelines** email dated 27 October, 15:29 IST. Both original PPTX attachments, the email body without its personalised unsubscribe footer, all slide text and SHA256 provenance are saved in [the final-deliverables archive](source-materials/healthathon-phase-2/2026-10-27-final-deliverables/README.md).
- Verified the two-page template is 49,969 bytes / 2 slides; the final-presentation template is 101,812 bytes / 12 slides. Both parse successfully and pass ZIP integrity checks. Originals are unmodified; the prior template and filled v6 deck remain preserved.
- Required package: **functional MVP + prescribed two-page overview + prescribed final presentation + narrated demo walkthrough video**. Include tested evaluator access and links. The video must introduce the team and approach, show doctor/technology collaboration and demonstrate the working solution; silent captures alone are insufficient.
- The new presentation removes the older standalone MVP slide, but MVP access is still required. Solution and evaluation sections each permit two slides, so 12 is the source count, not an explicit absolute limit. Deployment/integration/costs move to source slide 10. The two-page template's `Armman` text is a source placeholder, not Sahara's identity.
- The latest message does not state a deadline, cut-off/time zone, submission portal, video duration or export format. The existing 7 November planning target needs its source and exact cut-off confirmed. [everyday.md](everyday.md) and [the presentation README](submission/phase-2/README.md) now track the full package. Test credentials and private clinical material must stay out of Git.
- **Still pending:** filled two-page brief, adaptation of v6 to the new template/current evidence, final narrated video, evaluator-access check and submission receipt. This checkpoint archives and plans the deliverables; it makes no app, Play, deployment, email-send or submission claim.
- Integrated on top of collaborator commit `aae97cb` in an isolated worktree because the main checkout contains overlapping uncommitted context edits. Those local edits and unrelated files were preserved; do not reset them to this archive checkpoint.

## 27 October: forgiving hospital survey intake deployed

- The user explicitly asked to accept ordinary mistakes and incomplete responses so people can finish. This supersedes the earlier mandatory registration, Other-detail, address/map, service and accuracy checks. The web survey now requires only a nonblank hospital name and privacy consent. All other details can be skipped; optional Other text boxes remain visible when selected. Registration accepts spacing/punctuation and optional email text is preserved for review without a strict format rejection.
- Added explicit `hospital-survey-v4` / `hospital-survey-consent-v4` contracts through the browser, Next proxy, Edge function and database. Missing coordinates stay null, unconfirmed location and unchecked accuracy stay false, and unanswered service/morphine choices stay empty. The review screen says Not provided instead of inventing No or a confirmed pin. Visible choices and inline validation remain; no native dropdown or browser-validation dialog was reintroduced.
- Applied `prototype/supabase/migrations/20261027162302_sahara_forgiving_hospital_survey.sql` to the existing Sahara database, and deployed `sahara-feedback` Edge version 4. The local migration timestamp matches the recorded remote migration. Private contributor fields remain separate, RLS/permissions and quotas are retained, and legacy v1-v3 contracts/hashes remain compatible. Existing responses were not edited. Publication still requires a separate reviewer-approved complete listing.
- Verification: 48 focused schema, proxy/Edge, UI-validation and hospital-location tests passed; scoped ESLint and the production build passed. All 12 live SQL validation cases matched expectations. A service-role transaction accepted imperfect partial data and reused a single receipt on retry, then was rolled back with zero test rows remaining. Direct anonymous RPC execution remains denied.
- Live browser checks on 390px/375px mobile and 1440px desktop covered blank professional details, empty Other descriptions, skipped map/services, missing-name focus, required consent, honest review values and no horizontal overflow. A simulated failed request preserved answers; its retry reached the real backend with the same submission ID and returned HTTP 201/pending_review. The database confirmed preserved imperfect email/registration, private-field separation, null coordinates and one receipt. Only that labelled synthetic test record was then removed; cleanup counts were zero. No real respondent was changed or published.
- Final Vercel deployment `dpl_5HiT6q4b6GmoA3X4kz4Es8geJGGb` reached READY and was promoted. The survey and updated 27 October privacy policy were checked on the public domain. Share https://sahara.wedevit.in/hospital-survey (redirects to the preserved canonical `/hospital-survery`). Browser evidence is local under `prototype/output/playwright/survey-forgiving-*`. Unsent drafts still reset on reload; no physical-phone test is claimed. This checkpoint changes only the web survey and its intake, not Android, Play, CARE or unrelated research/transcript work.

## 27 October: native survey menus and validation popups removed

- The user's later request explicitly expanded the survey change to remove native dialogs. Replaced every survey select with visible radio choices; both state/union-territory fields now use always-visible searchable lists containing all 36 choices. No dropdown, datalist or browser form-validation popup remains in the hospital survey. The four Other text boxes remain attached to their choices.
- Inline validation now checks each step, focuses the first affected field, and rechecks all steps before sending. It preserves the existing server rules for registration, Other details, map confirmation, services and consent. Optional supply can be cleared with Not specified. Search selection clears a resolved address error; Back/Continue and selection changes preserve answers. Backend schema, survey eligibility and receipt/idempotency rules are unchanged.
- Verification: scoped ESLint, 29 feedback/validation unit tests and the production build passed. Chrome checks on the live survey covered 390px and 375px mobile widths plus 1440px desktop, all choice groups, radio keyboard navigation, all 36 states, search/no-match recovery, selected-state visibility, inline errors/focus, map confirmation, service exclusivity, Other fields, optional supply, consent enforcement and Back/Continue retention. Final choice text was verified at 14px with no horizontal overflow.
- The complete review/payload and failed-send/retry flow passed using intercepted synthetic search/API responses, with a stable retry ID and zero real database writes. This does not claim a new real survey submission or physical-phone test. Unsent drafts still reset on reload.
- Final production deployment `dpl_9okmHtG1XsHkHsukaSuBCr3u9WGJ` reached READY and was promoted. The updated https://sahara.wedevit.in/hospital-survey alias and canonical `/hospital-survery` route were checked after promotion. Local browser check scripts/screenshots use `prototype/output/playwright/survey-no-native-*`. Source and this bounded handoff are the web-only checkpoint; Android, Play, CARE, private records and prior unrelated local context edits remain untouched.

## 27 October: survey Other details clarified and deployed

- The user clarified that respondents need to specify what they mean after choosing Other; the earlier suggestion to replace dropdowns was superseded. Existing Other fields were present in production. The web fix makes all four branches explicit with "Other (please specify)", a directly attached detail box, automatic focus and contextual placeholders.
- Role, registering council, institution type and hospital connection retain typed answers across selection changes and Back/Continue. Blank, whitespace-only or one-character descriptions are caught before advancing, matching the existing server requirement of 2-120 trimmed characters. Registration eligibility, backend schema and stored survey records are unchanged.
- Scoped ESLint and all 20 feedback API tests passed. Live Chrome checks at 390x844 and 1440x1000 verified all four fields, focus, invalid input recovery, selection/step draft retention and no horizontal overflow. Review and trimmed payload coverage, failed-send recovery and stable retry IDs passed with intercepted synthetic responses; no real database write occurred. Unsent survey drafts are still not persisted after reload.
- Vercel production deployment `dpl_3ZjtZF3LD5ZijCkN23yGLJ6dzk3E` reached READY and was promoted to the existing project. The new controls were verified on https://sahara.wedevit.in/hospital-survery. The `/hospital-survey` alias remains available. Browser captures and three check scripts are under `prototype/output/playwright/survey-other-*` locally.
- This release is web-only. No Android/Play release, CARE connection, external message or clinical-data processing changed. Prior research/transcript edits and unrelated deleted files remain separate from the bounded source checkpoint.

## 24 October: native symptom diary and release preparation

- Live Play Console confirmed Production **0.5.0 (6), release 2, Available on Google Play**, with 100% rollout in India and no policy issues. The authorised new Production draft is **release 3 for 0.6.0 (7)**. Version code 7 was absent from the bundle library. The new bundle is **not uploaded or submitted**. Signed packaging is running; do not treat an old artifact as this release.
- The native symptom diary opens from Today and Daily care. It keeps separate 0–10 or explicitly unrated entries, Hindi/English controls, observation time/period, respondent/author, optional pain context, and dated comparisons only against matching real earlier entries. Existing per-person storage, permissions and mood history are retained. This is a personal diary, not a validated clinical instrument or triage/treatment recommendation.
- Fresh **156 JVM tests across 26 suites passed**, including all 9 new diary rules checks; zero failures, errors or skips. The matching-source debug APK was built, copied to [`Sahara-0.6.0-24Oct-test.apk`](output/android/Sahara-0.6.0-24Oct-test.apk) and installed beside the Play app on Samsung A34 without clearing data. SHA256: `458C67FBAEE29042253BC93402B6EE2023A0F19BFEEE9298555234E1FBCAF2A0`. Instrumentation Kotlin compiled, including 5 diary cases, but no current UI execution or final test-APK packaging receipt is established. The phone remains locked; unlock was requested without bypassing authentication. The earlier 20 October test APK is preserved.
- Play Data safety changes were saved: ML Kit App interactions/Analytics added; Diagnostics and Device IDs conservatively required because SDK initialisation is automatic and no app opt-out exists; Analytics added to IDs. Existing health/account/calendar/UGC/provider metadata disclosures remain. Local audio/image processing does not itself upload those contents. The new full description and reviewer instructions are prepared in the [release audit](output/android/verification/0.6.0/release-disclosure-audit.json), but **not saved in Play**. Review the additional Diseases and Conditions Management health feature for the symptom diary; retain all accurate medical declarations.
- The privacy update is deployed in production as `dpl_6cieWvkFSK2SkZsyQD1ARnTeVevq`, verified READY with Sahara and Saanthvana aliases. Both public policy routes return HTTP 200 with the new symptom, Qwen, Devanagari, directory and date text. Scoped ESLint and remote Next build passed. There was no fresh rendered browser review after recovery.
- **Browser blocker:** after the Codex interruption, the browser-control tool was unavailable. The official Windows Computer Use fallback then stopped because it could not verify the current browser URL confidently enough to enforce policy. No further UI actions were taken. Resume the existing draft after browser control is restored; do not create a duplicate draft or claim review submission. The release metadata/private follow-up preserve the current state.
- All 59 earlier tester invitations remain complete; do not resend. No patient enrolment, paid provider, CARE connection, billing, callback change or clinician-role approval is included. The hospital survey stays web-only.

**Team message draft — prepared only, not sent:**

> We reviewed CARE, the open-source platform Healthathon shared. It already supports palliative-care registries, home visits, care teams, referrals and care plans. They offered it as an optional base so we can reuse that infrastructure; we do not have to replace Sahara. Our possible contribution is a focused goals-of-care workflow: keep the original conversation, let a clinician review the exact note, then let the patient/family confirm and retrieve it later. We can keep the Android app and explore a CARE plugin/API connection for the clinical side. That connection is not built yet, and we still need to compare the workflows and validate it with the clinical team. CARE overview: https://ohc.network/solutions/palliative-care

## 23 October follow-up: Sujay's survey and visual slides

- Saved the exact supplied WhatsApp continuation in [`source-materials/team-context/2026-10-23/sujay-survey-symptom-followup.txt`](source-materials/team-context/2026-10-23/sujay-survey-symptom-followup.txt). It records the survey feedback and Shirin's symptom-tracker discussion; it is a pasted continuation, not a new complete export. Actual submission identities and contact details remain private.
- Confirmed Sujay's **S S Sparsh Hospital, RR Nagar, Bengaluru** entry is stored, with services, pin and a note about palliative practice within Pulmonary Medicine. It remains **pending review**, not a published or independently verified facility. Do not approve it automatically or treat morphine availability as live stock.
- Survey changes include palliative-specific wording, no mobile field, required hospital name/address, state-aware Photon search and an explicit checked map pin. Missing search results can be entered manually. Optional palliative physician/department names remain subject to directory review. The v3 backend migration and Edge function are deployed; earlier v1/v2 submissions and receipt hashes are preserved. Synthetic SQL checks passed inside rollback.
- The current visual [PPTX](submission/phase-2/final/Sahara-Healthathon-Phase-2-visual-draft-v6-ready.pptx) and [PDF](submission/phase-2/final/Sahara-Healthathon-Phase-2-visual-draft-v6-ready.pdf) use large patient scenes, current survey captures and dated product screens across all13 organiser sections after the user rejected the report-like v5 layout. Earlier draftv2 remains preserved. All slides rendered; no text/table overflow found; MVP/email links checked. Actual dated screens, editable journey/architecture/tables and external research evidence are clearly separated from latest handset or clinical validation. Source/hashes and deferred team/cost/video details: [`submission/phase-2/README.md`](submission/phase-2/README.md).
- Prepared the sourced [clinical symptom brief](docs/research/symptom-tracker-clinical-brief-2026-10-23.md), including the public Hindi IPOS study/form and instrument-use limitations. The personal diary now records separate symptom numbers, reporting period, patient/proxy identity, pain context and comparable earlier entries. Removed seeded scores, invented baselines and an unsupported distress total/clinical classification. It is **not a validated clinical questionnaire or triage protocol**; the clinical team still needs to choose the instrument, wording, timing and workflow. Saved records use the existing diary store.
- **Production verified:** Vercel `dpl_AcVpwonvetw3A7usj23yWMteZNUh` is READY and assigned to Sahara and Saanthvana. The live390px manual-hospital journey returned HTTP201 and a real pending-review receipt. Database checks confirmed the typed address, physician note, confirmed pin and private contact separation; only the exact synthetic fixture was removed, restoring the original two submissions/contributors/receipts. The existing entries were not edited. All299 web unit checks passed; current TypeScript, scoped lint and the remote Next build passed. Desktop/390px search, explicit pin confirmation, address-change invalidation, keyboard map placement, no-results/manual entry and review were exercised. The fictional guest diary saved Pain3/10 and Nausea7/10 together and retained both after reload; the stray-zero rendering issue was fixed and rechecked before the later launch-label-only update; the final production assets were then verified to say Symptom notes. Unsent professional survey data is intentionally not restored after a reload. [Non-sensitive QA](output/qa/survey-followup-2026-10-23.json). No Android build, Play submission, patient onboarding or outreach is included.

## 23 October checkpoint: daily plan, Phase 2 and CARE

- [`everyday.md`](everyday.md) records the daily priorities: one slide, survey, features, palliative-doctor feedback/outreach preparation, Play release work, CARE feasibility and a demonstration video. The main priority is a simple, rehearsed explanation that shows the actual product and clinical workflow.
- The original13-slide emailed Phase 2 template is preserved unchanged at `source-materials/healthathon-phase-2/Health-a-thon-2026-Phase-2-Submission-Template-original.pptx` (SHA256 `1ABDBA1A2E2FDFF6FEA96544325C7AFB409CD99CD403053FBD79E8DC18735F60`). Current filled [PPTX](submission/phase-2/final/Sahara-Healthathon-Phase-2-first-filled-draft-v2.pptx) and [PDF](submission/phase-2/final/Sahara-Healthathon-Phase-2-first-filled-draft-v2.pdf) contain all13 sections, an editable team table and the user's **Yes** to public solution information. Both were rendered and inspected; these are drafts, not submitted entries.
- The draft still needs team code, doctor details, biographies/photos, cost assumptions, updated demo access and validation evidence. [Sources and gaps](submission/phase-2/README.md) preserve dated0.5 screenshots, actual test results and current0.6 device limitations. PPTX SHA256: `6950B09E2AE73CA62C1B1A5EAFA8E37C2CD5CEF8911A85C8056448C6211BA4A8`; PDF SHA256: `FE63BA9477BD6879710E176387398A856C017832C162DB9F294D90AD2C1BF5D0`.
- **Visual comparison:** the earlier deck uses patient scenes, product screens, a map, process visuals and a chart; the new draft has nine mostly-bullet slides. Restore that visual storytelling within the required sections next. Neither deck was redesigned by this comparison; team photos remain deferred.
- CARE research: re-read the15October email; the offer to shortlisted teams is optional platform/plugin reuse. Archived and text-reviewed58 discoverable main-site pages and12 selected docs, with70 original page hashes verified. Original HTML, derived text, both sitemap inventories and integrity report are in `output/research/care-website-20261023/`; [current findings](docs/research/care-resources-and-sahara-audit-2026-10-16.json) extend the older dated audit. The473-page documentation sitemap was inventoried, not all read. CARE already has substantial palliative-care overlap; a Sahara plugin/API connection remains proposed and unbuilt.
- Integrated upstream `12d0250`: the separately authorised [cleaned team-context package](source-materials/team-context/2026-10-23/README.md), with continued chat/source documents through23October. Preserve its sharing limits: private repository reference only; original raw exports stay excluded. Proposed goals-of-care work and family stress/money/hospital-time KPIs are discussion targets, not completed features or measured outcomes. This integration preserves both agents' context-map sections.
- This checkpoint publishes documentation, the organiser template, filled drafts and public research to the existing private repository. It does not build/submit an Android release, refresh Play status, enable providers, send outreach or onboard patients. Private mail, credentials, recordings and raw local context remain excluded; four pre-existing presentation-generator deletions remain outside this commit.

## Start here

- Daily priorities and presentation practice: [`everyday.md`](everyday.md). Keep the official Phase 2 draft aligned with actual implementation and feedback; CARE integration remains proposed.
- Repository: https://github.com/basithladdu/healthathon (private), branch `main`.
- Primary checkout: `F:/CODE/healthathon`. `android/` is the Kotlin app; `prototype/` is the React/Next.js website. Do not recreate this as React Native or a web wrapper.
- Read this handoff, then `PROJECT_CONTEXT_MAP.md` and `android/release/store-draft.json`. The latter preserves distinct published, pending, rejected and test-build histories.
- Current product: **Sahara - A Palliative Care Companion**. Play title: **Sahara: Palliative Companion**. Release package: `in.wedevit.sahara`; debug package: `in.wedevit.sahara.development`.
- Existing Saanthvana branding/domain in the submitted deck must remain available. Current web: https://sahara.wedevit.in; earlier https://saanthvana.wedevit.in stays active.

## Deliverables

| Artifact | Location / identity |
| --- | --- |
| Phase 2 working slides | `submission/phase-2/final/Sahara-Healthathon-Phase-2-visual-draft-v6-ready.pptx`; all13 organiser sections, editable team table and sourced progress; remaining discussion items in `submission/phase-2/README.md` |
| Latest installable test APK | `output/android/Sahara-0.6.0-20Oct-test.apk`; 0.6.0 (7), 300,504,493 bytes; Android debug signature; installs beside the Play app |
| APK SHA256 | `5DC9D96176407A8E35A34709653D9897FBD4491FA79E9F908459933509BAC9AC` |
| Native source for that APK | `e683e7e`; subsequent handoff/PDF commits do not change its native source |
| One-page mentor update | `output/pdf/Sahara_Mentor_Update_2026-10-20.pdf`; editable `build_sahara_mentor_update.py` alongside it |
| Presentation PDF | `output/pdf/Saanthvana_Submitted_Presentation.pdf`; 8 slides, 41 original links retained |
| Original submitted deck | `submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx`; unchanged |
| Detailed evidence | `output/android/verification/features/mentor-delivery.json`, `mentor-apk-final.log` and the release metadata |

APK files are excluded from Git. The latest APK and mentor files are uploaded to the [private draft GitHub release](https://github.com/basithladdu/healthathon/releases/tag/untagged-c800dc01b40aa7dbfd27), separate from Google Play. All four uploaded asset sizes and server SHA256 digests matched the local files; the handoff asset is refreshed after this link update. A normal clone includes source, PDFs and committed evidence, not signing keys, installed model weights or private records.

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
