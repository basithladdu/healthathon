# Connected doctor test workspace

## Two deliberately separate experiences

The welcome screen has an orange expanding panel for **Add a person**, **Your account**, and **Doctor workspace**. Each choice opens its own form within the panel. Back returns to the welcome choices; a request in progress must finish before closing its form.

**Doctor workspace** uses real Sahara account authentication. It is not the fixed local walkthrough login and not a Gemini or Supabase-dashboard login. Selecting a doctor entry does not prove medical registration, approve clinical publication, or grant access to another person's records.

**Explore doctor walkthrough** remains the independent fictional on-device workspace. Existing walkthrough drafts, published previews, requests and appointments are not imported, replaced or relabelled as connected records.

## Patient care stays inside the doctor navigation

The doctor workspace retains **Home, Patients, Schedule and Account**. Opening a person keeps **Patients** selected, with that person's name and source label visible. Individual care tools belong inside that person's record rather than adding the old Today/Talk/Care/Nearby/More navigation bar below it.

Real shared people, database-backed fictional test patients and the preserved local walkthrough remain different record sources. Matching names do not merge identities or copy histories. Missing or revoked access must show an unavailable state, not select another person automatically. Read-only access must remain read-only; choosing a doctor display mode does not add permissions.

Navigation checks use offline fixtures and isolated preferences. They do not prove a successful live doctor sign-in, cross-account invitation or database round trip. Those still require an authorised test account and explicit end-to-end verification.

### Navigation verification — 23 October 2026

- Final Android app/test APK builds and all **173 host tests** passed, including account/source identity and delayed-callback permission checks. Three new isolated device tests passed: large-text patient-hub dispatch, four-tab patient-record navigation with preserved drafts, and all embedded-tool callbacks at 1.5× text size. Ten earlier Welcome/header/preserved-walkthrough component checks also passed during this verification.
- Normal app navigation was checked on `Sahara_Doctor_Demo` only: Doctor walkthrough → Patients → Asha → Overview, with Patients highlighted and the four doctor tabs retained. The guest list correctly showed no device-local care profiles; the pre-install database snapshot already contained zero people and zero care rows.
- The new **Add on-device care profile** form was opened and cancelled without entering or saving information. No test person was created in the user's app. Existing fictional histories remain separately reachable.
- An outside-repository private backup was restored for inspection. After final installation and navigation, 93 of the 94 pre-existing private files were byte-identical, none were missing, and only Android's `files/profileInstalled` installation marker changed.
- The shared-care permission/role restoration and callback guards were source-reviewed and pure-helper tested. Live sign-in, permission revocation across accounts, actual cloud saves/uploads and live patient data were **not** exercised. Component tool tests validate menu dispatch, not external provider, file-sharing or notification delivery.

## Correct backend

Sahara account and shared-care configuration uses project `cghlkqeqiokjqdionrkc`. The older Saanthvana project `kjnhtakqzboayqorrlue` and its six-day health-check workflow are separate. Do not create the test account in the older project by mistake.

An authorised owner must manually create the dedicated test user in the current project's Supabase Authentication user interface. Use a unique synthetic-test email and a strong password; confirm the account using the supported administrative flow. Do not put the password in source code, GitHub, test fixtures, screenshots, logs, or this document. Signing in must use the actual credentials entered by the tester; no shared password is embedded in the app.

## Explicit test dataset

After sign-in, **Load test patients** explicitly requests six fictional database-backed patients in the signed-in account's private test dataset. Opening the workspace alone does not seed records. Repeated loading should return the existing dataset rather than duplicate it. Server patient IDs are separate from local walkthrough IDs.

The patient hub offers Overview, Progress, Medicines, Conversations, Visits and Reports. The test label must remain visible. These records are not genuine patient accounts and must never display a verified-doctor badge merely because the user signed in.

Conversation drafts preserve the original words, all six note headings, checklist states, source references, transcript provenance, timestamps, warnings and evidence. Saving is explicit. Published test versions are distinct from approved clinical records; editing later must not rewrite an already published test version.

Medicine reports distinguish:

- **Planned:** scheduled dose time.
- **Reported taken:** the claimed intake time, or absent when no intake is reported.
- **Logged:** when the report entered the database.

A report records what an account says happened; it is not proof of ingestion or a prescription.

## Local versus uploaded information

The connected test planner persists locally in a separate account-scoped preference namespace; it is not a hospital booking service, notification system or database appointment feed. Discussion requests are local simulations, not delivered from real family accounts. The separate walkthrough retains its own existing stored requests and visits.

Audio recording is local by default and does not generate an online AI note. A completed test recording has a separate permission checkbox and **Upload this recording** action. Choosing a report file is not an upload either: Reports offers an explicit selected-file upload with its own permission checkbox for fictional, non-confidential test files only. Private downloads must stay permission-controlled; do not publish public URLs or upload an existing private recording automatically. Files are limited to supported audio, PDF or image formats and 4 MiB; the client checks the downloaded file size and SHA-256 fingerprint before opening it.

The existing shared clinician workflow remains separately accessible for patient-controlled invitations, permissions and professional review. A test-dataset owner is not automatically a verified clinician for real shared care.

An explicit **preserved walkthrough** action can open the matching local sample patient. This is a separate local preview, not a conversion of the connected database record or an upload of its local history.

## Preservation and recovery

See [the preservation baseline](doctor-workspace-preservation-baseline.md). Existing emulator Room data, preferences, files and recordings were archived locally before implementation and restore-verified outside the live app. The private archive must not be committed or uploaded.

Any remote database audit covers schema/catalog and permission evidence only unless a separate authorised export is explicitly performed. It is **not** a full backup/export of the production database or Supabase Storage.

Do not uninstall, wipe the emulator, clear app data, reset the repository or delete existing cloud records to test this feature.

## Verification status

Verification on 23 October 2026:

- Final Android host tests: **160 passed**, no failures, errors or skipped tests. This includes 13 new connected-data tests covering all draft fields, exact source text and Unicode, unknown-format refusal, payload limits without truncation, patient-ID validation, dose time invariants, UTC conversion and exact-owned-draft retry receipts.
- Final debug application and instrumentation APK builds passed. The installed debug APK SHA-256 matches the built APK: `E7508DA6D99EC38275B4209E3C52727CB5E031780CA942088AEB7E48863DB123`.
- On `emulator-5580` only, an earlier integration build passed **13 isolated instrumentation tests**: four welcome-panel routing/busy/large-text/name tests, two account-scoped local-planner tests, and seven historical Room migration fixtures. The planner's two tests passed again on a later build.
- Existing app-private baseline comparison after those tests: all 88 baseline files remained present; preferences, Room/WAL and content files were byte-identical. The only changed baseline file was Android's `files/profileInstalled` runtime marker after APK installation.
- The backend owner reports the synthetic-workspace migration deployed to the current project with six new RLS-protected tables, private file storage and permission-controlled RPCs. The independent SQL suite reports **51 checks passed**. Existing database catalog/count evidence was retained; no full production database export is claimed.
- The final 19-test device run could not complete: Android terminated Sahara with `LOW_MEMORY` before useful test results. An initial preserved-route test fixture used an invalid isolated preference prefix; that test-only prefix was fixed and compiled in the final APK, but its final device execution remains unverified. No live app data was reset to work around the emulator condition.
- Final manual launch attempts were likewise terminated with Android's `LOW_MEMORY` exit reason, not a captured Sahara Java exception. The final installed app therefore cannot be claimed to remain running, and final welcome/doctor-form screenshots were not obtained. An earlier build's actual signed-out welcome navigation was observed, but that is not final-package visual verification.
- A dedicated real test account has not yet been supplied. Successful real login, positive authenticated dataset seeding, private upload/download and cross-account end-to-end UI execution remain **unverified**. Offline/component checks must not be presented as that positive live test.

Next verification requires a healthy emulator or physical test device and the dedicated test account. No other AVD was touched, no existing private files were cleared, and no unrelated applications or services were stopped.
