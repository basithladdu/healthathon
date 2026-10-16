# Sahara — A Palliative Care Companion

**Sahara (सहारा)** is the approved name, chosen on 10 October 2026. Zeros and Ones was shortlisted for Health-a-thon 2026 Phase 2. The primary project is now native Android; the website is in `prototype/` and its earlier checkpoint is preserved in [Healthathon-Copy](https://github.com/basithladdu/Healthathon-Copy).

Website: https://sahara.wedevit.in. The earlier https://saanthvana.wedevit.in address remains active for the submitted deck. Android privacy and account-deletion requests are available at `/privacy` and `/delete-account` on either address. `/android-account` handles email confirmation and password recovery; the exact Supabase redirect and ordinary-tester email delivery still need provider setup.

## Android

Kotlin, Jetpack Compose, Room, MapLibre Native and consented audio recording. The main flow is **conversation → doctor review → saved care note → patient/family confirmation of that exact version**. Earlier versions remain unchanged and the original words remain readable.

Also included: multiple people, prescribed-medicine lists, visits, tasks, check-ins, a journal, and 33 source-linked Karnataka care centres with search, service filters, calls and directions. MapLibre with OpenFreeMap tiles shows nine checked hospital locations; the other 24 remain in the address list until coordinates are verified. Source links for each coordinate are retained in the directory asset. Morphine listings do not establish current stock.

The home screen shows saved visits, medicines, tasks, check-ins and journal entries. Forms use optional details, quick date choices and a single journal field. Tab position and filters survive navigation; animations follow system motion settings. Routine screens contain no demo, native-build or storage-explanation copy.

Version 0.4 brings Awaiz's Sahara logo, compact Patient/Family/Doctor menu and highlighted bottom navigation into Android. Documents gathers reviewed care notes and user-selected PDFs or photos, with search, open, share and remove actions. Reports are private copies on the phone, separated by account and person; they do not sync to the care team. Care notes have Save copy and Share at the top. The last physical-phone build was 0.4.0.

The 0.5.0 (6) candidate adds consented, attributed speaker-turn dictation on phones with Android's on-device recognizer, exact source excerpts for care-note review, installed-voice read aloud, patient-first and separate family confirmations, a shared care-team request/task queue, dated calendar and numeric lab history. Room schema 6 merges the two branches with explicit migrations from schemas 3, 4 and 5. The shared confirmation migration and rollback permission suites passed against the restored service on 16 October. Dictation does not transcribe saved recordings, identify speakers or translate. Excerpt assignment is clinician-directed, not a connected generative model.

The latest merged doctor workspace has patient search, scheduled visits, recording playback, review steps and PDF export in an isolated fictional preview. It does not establish clinician verification or connect those sample records to real care accounts. Its disclosures and PDF labels remain visible. Release builds disable cloud audio at the interface, model and transport boundaries; the restored Sahara backend and existing account-token namespace are retained. Published source is capped at 12,000 characters to match that service. The upstream synthetic developer transcription code is preserved separately from public patient use. See [the doctor workspace scope](docs/doctor-demo-workspace.md) for the preview's separate storage and retention limits.

The combined source includes upstream merge `865f624`, the preserving merge `504b09b`, release/account fixes `51c0fec` and keyboard sheet fix `9bd9bda`; build workflow source is `ebffb7b043a5e8725f190ab8f8f3574c768d9058`. Its current build and runtime checks are still pending. Earlier 39-unit / 20-emulator-pass evidence and signed 0.5 artifacts belong to earlier source and do not verify this merged candidate. Current results, artifact identity and release readiness are recorded in `android/release/store-draft.json`.

Version 0.4.0 adds the prescription organiser under **Care → Medicines**. It supports choosing prescription pages and medicine packaging, then reviewing and editing extracted medicines before saving. **Patient scanning remains off:** the existing Gemini key uses the free tier, whose terms exclude sensitive input. On 13 October, the deployed Supabase function passed a live extraction check using fictional images; unauthorized calls returned 401. Its secret is deliberately absent from the public website until suitable provider terms and billing are resolved. No secret is included in the APK. Manual medicine entry works offline.

Optional accounts use Supabase Auth. Signed-in people, daily entries, immutable care-note versions and exact-version confirmations are saved through the API and cached separately per account. Owners can issue one-use, three-day invitations with read or edit permission and remove members. The backend enforces those permissions and rejects conflicting edits. Refresh is available on Today and runs when the app opens. Failed saves keep the form filled. Existing unsigned-in records are not automatically uploaded. Recordings and unfinished conversations are not shared. After verifying a deletion request, support can close Sahara access and remove its owned records through the service-only `healthathon_close_account` function; a shared sign-in and other owners' records remain intact. The closure is enforced for existing tokens. Its rollback regression passed without closing any real account.

Doctor/family views, task recipients and confirmation names are self-entered; identities and credentials are not verified. Recording and dictation need microphone permission and everyone's consent and stop when the app leaves the foreground. Source links require exact words; edited text does not acquire a false citation. Read aloud requires an installed offline voice. Care-team requests are shared records, not dispatched calls or email. Lab graphs show entered values with the printed units/ranges, without clinical interpretation. Care notes are not treatment orders or legal directives.

### Build

Requires JDK 21, Android SDK 36 and the checked-in Gradle wrapper:

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:bundleRelease --no-daemon
.\gradlew.bat :app:lintDebug --no-daemon
```

Use Android Studio to open `android/`, or set `ANDROID_HOME` / an ignored `android/local.properties`. On this computer, build caches and temporary files use F: because C: has little free space. Gradle uses one worker to avoid the observed memory-allocation failure. The manually dispatched Android CI workflow builds unsigned packages without private signing material; a successful CI build still needs local signing, package/signature verification and current-source runtime checks before a store upload.

The permanent application ID is `in.wedevit.sahara`; debug builds use `in.wedevit.sahara.development`. Release builds read the ignored `android/keystore.properties` file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and require an upload key. Never commit signing credentials. The previous development app remains a separate installation; its records are not migrated or removed by installing Sahara.

The Play title is **Sahara: Palliative Companion**. The existing Devit account shows **Organization**. **0.4.1 (5)** is live in **Closed testing — Alpha**, released on 14 October at **17:49 IST**. An eligible account joined and Google Play displayed **Install**. [Join closed testing](https://play.google.com/apps/testing/in.wedevit.sahara) using an account on the selected tester lists. Invitations to all **59** confirmed addresses were verified in Gmail Sent in separate **57 + 2** batches, with recipients kept private. Tester installations and production approval are not confirmed. The original two publisher-account rejections remain in history. Photo scanning stays off; accurate Medical/health declarations are preserved. See `android/release/store-draft.json` for status and remaining scope.

## Website and submission

On 16 October, Play's Publishing overview showed **Production 0.4.1 (5): Changes in review**, for India. Preserve this existing review while preparing the newer candidate; it is not production approval.

- [Current website](https://sahara.wedevit.in/)
- [Website checkpoint](https://github.com/basithladdu/healthathon/commit/735149fc4071555268ddfd053743af48f2266e76) — `SHORTLISTED`
- [Latest saved Round 1 deck](submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx)
- [Current project context](PROJECT_CONTEXT_MAP.md) and [name shortlist](PRODUCT_NAMING.md)
- [Website setup](prototype/README.md), [revised form answers](submission/Round_1_Answers_2026-09-24.md), [family-support research](docs/pitch/2026-09-24-family-support-research.md), [proposed impact plan](docs/pitch/2026-09-24-impact-plan.md)

To run the website with Node.js 22.13 or newer:

```powershell
cd prototype
npm install
npm run dev:next
```

The web and Android implementations currently have different integration coverage. The website's shared centre-report service is not connected to this native build; its live care-assist readiness endpoint reported `ready: false` on 9 October. The public prescription endpoint intentionally reports `ready: false` pending suitable patient-data processing terms. Its private Supabase/Gemini path passed a synthetic-image test on 13 October; this does not establish real-prescription accuracy or the complete phone upload flow. AI transcription, shared hospital-report uploads and medicine notifications remain Android work. A dedicated reviewer account has confirmed email, API access and a successful Samsung sign-in/load/reopen check. Ordinary tester signup still needs transactional SMTP because Supabase's default mailer sends only to project-team addresses; Zoho setup is paused at the user's request. Recovery has 11 focused passing checks and a deployed, mobile-checked callback page, but the redirect allowlist remains unapproved and end-to-end delivery is pending. Unsigned-in care flows remain available. The shared Supabase project also has leaked-password protection disabled: [configuration guidance](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). Build results do not establish live service behavior.

## Team

Dr Sharada Vinod Kutty · Dr Sujay Halkur Shankar · Shaik Abdul Basith · Shaik Muhammad Awaiz · Shirin Ayub

### Backend and map sources

- Reproducible schema and row-permission checks: `android/supabase/migrations/` and `android/supabase/tests/care_permissions.sql`. Only the publishable key is included in the client. No service key is shipped.
- MapLibre Android: https://maplibre.org/maplibre-native/android/examples/getting-started/
- OpenFreeMap style and attribution: https://openfreemap.org/quick_start/
- Directory: https://palliumindia.org/clinics/karnataka
- The private invitation table deliberately has no direct RLS policy; only bounded, authenticated functions can use it. Other existing Supabase tables were left unchanged.
