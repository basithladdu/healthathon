# Saanthvana — native Android care companion

**Working name; the Hindi rebrand is undecided.** Zeros and Ones was shortlisted for Health-a-thon 2026 Phase 2. The primary project is now native Android; the existing website is preserved in `prototype/` and in [Healthathon-Copy](https://github.com/basithladdu/Healthathon-Copy).

## Android

Kotlin, Jetpack Compose, Room and native audio recording. The main flow is **conversation → doctor review → saved care note → patient/family confirmation of that exact version**. Earlier versions remain unchanged and the original words remain readable.

Also included: multiple people, prescribed-medicine lists, visits, tasks, check-ins, a journal, and 33 source-linked Karnataka care centres with search, service filters, calls and directions. Morphine listings do not establish current stock.

This build keeps records on the phone. It has no internet permission, analytics, clinical cloud sync or verified accounts. Doctor/family views and reviewer names are self-entered. Recording needs microphone permission and everyone's consent, and stops when the app leaves the foreground. The text organiser preserves entered words under explicit headings; it is not AI transcription or a clinical recommendation system. Care notes are not treatment orders or legal directives.

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

The application ID `in.wedevit.healthathon.development` is temporary. The release bundle is unsigned. Final name/package, upload signing, Play account eligibility, public privacy policy, Health apps declaration and Data safety answers must be resolved before submission. No Android app has been uploaded to Play yet. See `android/release/store-draft.json` for the prepared listing and remaining dependencies.

## Website and submission

- [Current website](https://saanthvana.wedevit.in/)
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

The web and Android implementations currently have different integration coverage. The website's shared centre-report service is not connected to this native build; its live care-assist readiness endpoint reported `ready: false` on 9 October. AI transcription, authenticated clinician/patient sharing, report uploads and medicine notifications remain Android work. Do not use a build result as evidence that these services work.

## Team

Dr Sharada Vinod Kutty · Dr Sujay Halkur Shankar · Shaik Abdul Basith · Shaik Muhammad Awaiz · Shirin Ayub
