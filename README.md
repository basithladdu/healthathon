# Sahara — A Palliative Care Companion

**Sahara (सहारा)** is the approved name, chosen on 10 October 2026. Zeros and Ones was shortlisted for Health-a-thon 2026 Phase 2. The primary project is now native Android; the website is in `prototype/` and its earlier checkpoint is preserved in [Healthathon-Copy](https://github.com/basithladdu/Healthathon-Copy).

Website: https://sahara.wedevit.in. The earlier https://saanthvana.wedevit.in address remains active for the submitted deck. Android privacy and account-deletion requests are available at `/privacy` and `/delete-account` on either address. `/android-account` handles email confirmation and password recovery; the exact Supabase redirect and ordinary-tester email delivery still need provider setup.

## Android

Kotlin, Jetpack Compose, Room, MapLibre Native and consented audio recording. The main flow is **conversation → doctor review → saved care note → patient/family confirmation of that exact version**. Earlier versions remain unchanged and the original words remain readable.

Also included: multiple people, prescribed-medicine lists, visits, tasks, check-ins, a journal, and 33 source-linked Karnataka care centres with search, service filters, calls and directions. MapLibre with OpenFreeMap tiles shows nine checked hospital locations; the other 24 remain in the address list until coordinates are verified. Source links for each coordinate are retained in the directory asset. Morphine listings do not establish current stock.

The home screen shows saved visits, medicines, tasks, check-ins and journal entries. Forms use optional details, quick date choices and a single journal field. Tab position and filters survive navigation; animations follow system motion settings. Routine screens contain no demo, native-build or storage-explanation copy.

Optional accounts use Supabase Auth. Signed-in people, daily entries, immutable care-note versions and exact-version confirmations are saved through the API and cached separately per account. Owners can issue one-use, three-day invitations with read or edit permission and remove members. The backend enforces those permissions and rejects conflicting edits. Refresh is available on Today and runs when the app opens. Failed saves keep the form filled. Existing unsigned-in records are not automatically uploaded. Recordings and unfinished conversations are not shared. After verifying a deletion request, support can close Sahara access and remove its owned records through the service-only `healthathon_close_account` function; a shared sign-in and other owners' records remain intact. The closure is enforced for existing tokens. Its rollback regression passed without closing any real account.

Doctor/family views and reviewer names are self-entered; credentials are not verified. Recording needs microphone permission and everyone's consent and stops when the app leaves the foreground. The text organiser preserves entered words under explicit headings; it is not AI transcription or a clinical recommendation system. Care notes are not treatment orders or legal directives.

### Build

Requires JDK 21, Android SDK 36 and the checked-in Gradle wrapper:

```powershell
cd android
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
.\gradlew.bat :app:bundleRelease --no-daemon
.\gradlew.bat :app:lintDebug --no-daemon
```

Use Android Studio to open `android/`, or set `ANDROID_HOME` / an ignored `android/local.properties`. On this computer, build caches and temporary files use F: because C: has little free space. Gradle uses one worker to avoid the observed memory-allocation failure.

The permanent application ID is `in.wedevit.sahara`; debug builds use `in.wedevit.sahara.development`. Release builds read the ignored `android/keystore.properties` file (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) and require an upload key. Never commit signing credentials. The previous development app remains a separate installation; its records are not migrated or removed by installing Sahara.

The Play title is **Sahara: Palliative Companion**; the full companion name appears in the description and feature graphic. Version 0.3.0 (3) was uploaded and submitted for **Closed testing — Alpha** in India. On 10 October, Google rejected distribution under **Play Console Requirements** because the app's category or declared features require an organisation account. App creation and upload had succeeded in the existing Personal account, but that did not establish publishing eligibility. All forms, the rating, reviewer login, listing and four tester lists (57 unique addresses) remain saved. Closed testing is not live; no invitation has been sent. Account conversion remains stopped at the user's request. See `android/release/store-draft.json` for the decision and current dependencies.

## Website and submission

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

The web and Android implementations currently have different integration coverage. The website's shared centre-report service is not connected to this native build; its live care-assist readiness endpoint reported `ready: false` on 9 October. AI transcription, shared hospital-report uploads and medicine notifications remain Android work. A dedicated reviewer account has confirmed email, API access and a successful Samsung sign-in/load/reopen check. Ordinary tester signup still needs transactional SMTP because Supabase's default mailer sends only to project-team addresses; Zoho setup is paused at the user's request. Recovery has 11 focused passing checks and a deployed, mobile-checked callback page, but the redirect allowlist remains unapproved and end-to-end delivery is pending. Unsigned-in care flows remain available. The shared Supabase project also has leaked-password protection disabled: [configuration guidance](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection). Build results do not establish live service behavior.

## Team

Dr Sharada Vinod Kutty · Dr Sujay Halkur Shankar · Shaik Abdul Basith · Shaik Muhammad Awaiz · Shirin Ayub

### Backend and map sources

- Reproducible schema and row-permission checks: `android/supabase/migrations/` and `android/supabase/tests/care_permissions.sql`. Only the publishable key is included in the client. No service key is shipped.
- MapLibre Android: https://maplibre.org/maplibre-native/android/examples/getting-started/
- OpenFreeMap style and attribution: https://openfreemap.org/quick_start/
- Directory: https://palliumindia.org/clinics/karnataka
- The private invitation table deliberately has no direct RLS policy; only bounded, authenticated functions can use it. Other existing Supabase tables were left unchanged.
