# Sahara: SVK spec build handoff

Written 30 Oct 2026. The app is called **Sahara**. "Saanthvana" is the old name and "Sahaara" is a misspelling; both still appear in source documents and in some storage keys.

This covers the build of SVK's two specs: the emergency care plan (ECTPR) and the living will (AMD). Keep it honest: tick only what you have actually checked.

## Source documents

| Document | Path |
|---|---|
| SVK clinical spec | `source-materials/team-context/svk-specs-2026-10-29/Saanthvana_Clinical_Specification.docx` (+ `.txt`) |
| SVK tech spec | `source-materials/team-context/svk-specs-2026-10-29/Advance_Medical_Directive_Platform_Tech_Spec.docx` (+ `.txt`) |
| Vidhi model living will | `Vidhi_EOLC_Sample-Advance-Medical-Directive.pdf` |
| Vidhi FAQ | `Vidhi-EOLC-Toolkit-FAQ.pdf` |
| Legal and clinical sources, with links and what could not be verified | `docs/SAHARA_ECTPR_AMD_REFERENCES.md` |
| Free AI services, limits, key names | `docs/FREE_MODELS.md` |
| What only people can do | `blockers.md` |

**Downloaded:** Noto Sans Telugu font, in `prototype/public/fonts/` with its licence. **No AI models are bundled.** Speech models download on the device only when a doctor transcribes a talk; see `docs/FREE_MODELS.md`.

**1 Nov update:** legal comments and goals-of-care transcription are in. Android is uncompiled and the website is undeployed: see the top of `HANDOFF.md` and `blockers.md`.

## Where the code is

**Web** (`prototype/`, live at https://sahara.wedevit.in, Vercel project `continuity-loop-healthathon`):

| Area | Files |
|---|---|
| Shared data model | `app/ectpr-model.ts` |
| Rules V1–V9, C1–C8, L1, ED status | `app/ectpr-rules.ts` |
| Versions, audit log, pilot records (on this device) | `app/ectpr-store.ts`, `app/ectpr-pilot.ts`, `app/ectpr-pilot-log.tsx` |
| Care plan form and summary | `app/ectpr-plan-screen.tsx`, `app/ectpr-plan-extras.tsx` |
| Emergency view, wallet card, PDF | `app/ectpr-ed-view.tsx`, `app/ectpr.tsx`, `app/ectpr-pdf.ts` |
| QR card | `app/ed/[token]/`, `app/api/ed-card/`, `app/ed-card-client.ts`, `app/ed-card-snapshot.ts` |
| Living will | `app/amd-screen.tsx`, `app/amd-rules.ts`, `app/amd-store.ts`, `app/amd-pdf.ts` |
| Privacy notice, FHIR, review SMS | `app/dpdp.ts`, `app/dpdp-notice.tsx`, `app/ectpr-fhir.ts`, `app/api/care-plan-reminder/` |
| Languages | `app/care-i18n.ts`, `app/care-language-select.tsx`, `app/i18n/{te,ur,kn}.ts`, `scripts/extract-care-strings.mjs` |
| AI backups, translation, advice guard | `app/ai-providers.ts`, `app/ai-treatment-guard.ts`, `app/translate-server.ts`, `app/api/translate/` |
| Tests | `tests/ectpr-rules.test.mjs`, `tests/ectpr-gaps.test.mjs`, `tests/amd-rules.test.mjs`, `tests/ed-card.test.mjs`, `tests/ai-*.test.mjs` |

Pages: `/care-plan`, `/doctor/care-plan`, `/living-will`, `/emergency-card`, and the public `/ed/<token>`.

**Android** (`android/app/src/main/java/in/wedevit/care/`): `CarePlanModel.kt`, `CarePlanStore.kt`, `CarePlanUi.kt`, `CarePlanExtrasUi.kt`, `CarePlanPilot.kt`, `CarePlanPdf.kt`, `CareEdCard.kt` (QR card upload, offline queue), `CareLivingWill.kt`, plus languages in `CareLanguage.kt` and `src/main/resources/i18n/{te,ur,kn}.tsv`. Tests: `src/test/.../CareEdCardTest.kt` checks the snapshot field names against `ectpr-model.ts`; `CarePlanFlowRenderTest` draws the screens when run with `-PSAHARA_RENDER_UI=true`.

**Database:** `prototype/supabase/migrations/20261029230000_sahara_ed_cards.sql`, applied by Basith on 30 Oct. Tables `sahara_ed_cards` and `sahara_ed_access_log`. Rows cannot be deleted by design.

## How to run and deploy

```bash
npm --prefix prototype run dev
```

```bash
npm --prefix prototype test
```

Type check, from `prototype/` (it runs out of memory without the flag; stop the dev server first, and if it reports errors in `.next/types`, run `npx next typegen`):

```bash
NODE_OPTIONS=--max-old-space-size=6144 npx tsc --noEmit -p .
```

Deploy, from `prototype/`:

```bash
vercel deploy --prod --yes
```

Android, from `android/`, one build at a time. Release builds run out of memory, and disk F: is nearly full:

```bash
./gradlew --no-daemon :app:testDebugUnitTest -Dorg.gradle.jvmargs=-Xmx3g
```

New text: every `t("en","hi")` on Android must also go in the three `.tsv` files or `CareLanguageDictionaryTest` fails. On the web, add keys to `app/i18n/*.ts`. The web extractor skips the rule files and `helpEn`, so it will not report those as missing.

## What was checked on the live site (30 Oct)

- Issue a card, open the QR link with no sign-in, emergency override with a typed reason, withdraw: all worked for sample patient Meera. The public view strips date of birth, address, phone and the patient's own words.
- The QR page loaded in about 1.3 s on a desktop connection. **Not measured on a phone or 3G**, which is the spec's target.
- Urdu right-to-left on a phone-sized screen.
- Web tests: 425 of 425, re-run on the final code. Production build passed earlier on 30 Oct.
- A team test left one withdrawn card and two access log rows in Supabase. The Android work's curl test added one more withdrawn synthetic card. They cannot be deleted.

## What was checked on Android (30 Oct)

- Unit tests: 670, 0 failed, 1 skipped (`PdfPasswordLockTest` needs poppler). None touch the network.
- Debug APK builds. `CarePlanFlowRenderTest` passes, and 4 of its screenshots were looked at.
- The server accepted a hand-written issue, view and revoke by curl, in the shape Android sends. That JSON did not come from the Kotlin code.

## Not verified

- Anything on a real phone. No device was connected.
- Android: a real card upload from the app, a Kotlin-made snapshot on the web `/ed/<token>` page, and scanning either QR with a camera.
- Android: the plan and living will PDFs have never been opened. `CarePlanPdfTest` (androidTest) was updated but not compiled or run.
- Android: translated screens were not looked at, including Urdu right-to-left in the PDF.
- Android: `CareLanguageState` crashed in the render test when created during composition, so it now starts in `MainActivity`. Whether the old crash happened on a device is unknown.
- Read-aloud sound: the test browser has no voices. Only the text and language sent to the speech engine were checked.
- A real SMS: no provider is set up.
- A real AI answer: no keys are set; all AI tests use fake services.
- FHIR export against a real validator. It is labelled draft.
- Printing the emergency PDF, and filling in a whole living will by hand in the browser (sample PDFs were generated and page 1 inspected).
- The in-app `EdQuickView` may be dead code; no route was found that reaches it.
- Reading level was written plainly, not measured.

## Known gaps

**Legal and clinical (need SVK and the medical lead):**
1. The spec says only stopping a running treatment needs the medical boards. The Vidhi FAQ and Harish Rana suggest not starting one may too, for a patient who cannot decide. The app shows "legal review pending".
2. Harish Rana adds a 30-day reconsideration period, missing from the spec. It is in the app as information.
3. POLST is stricter than rule V2, which allows CPR with a written reason.
4. Temporary defaults: 12-week review interval, 3-day trial, content shown under the out-of-date banner.
5. The "care that always continues" wording was written from the spec's surrounding statements, because §16's text was not in the files. It needs checking against Saanthvana v1.0.
6. Andhra Pradesh living-will custodians: still unknown.

**Product:**
- **Sign-in is a demo.** Anyone can pick "Doctor" and sign a plan. Real patients need the spec's hospital login and phone OTP.
- Care plans live on the device. Only an issued card's snapshot reaches the server. There is no sync between doctor and patient devices.
- The privacy notice tells people to ask the care team to erase the server copy, but no erase process exists; withdrawing only marks a card revoked.
- `/api/care-plan-reminder` has a same-site check and a per-instance limit but no server sign-in. Tighten it before SMS is turned on. MSG91 also needs `MSG91_REVIEW_TEMPLATE_ID`.
- Pilot entries cannot be edited after saving.
- Translations are machine drafts. A clinician must back-translate before the pilot, starting with ED banners, the board pathway and the "cannot end life" message. Known oddities: "Home" is translated in the navigation sense but also used as a place of care; Kannada "Review" is an imperative used as a label; Telugu "No preference recorded" contains "ఇష్టం".
- Android "Ask your documents" only understands English and Hindi.
- Android emergency PDF: English and Hindi only, by the language agent's choice. SVK should decide, since the spec asks for matching Telugu PDFs.
- Android has no ECTPR "Distribution" (signed-plan scan and copy list); the checklist and SHA-256 exist for the living will only.
- Android: read-aloud status messages in `CareSpeech.kt` are English. No custodian suggestion chips or revocation scan upload.
- Android keeps `nivTrial` in the snapshot. The server stores it, but the web emergency view does not show it, so a card opened by QR does not display an NIV trial. Fix or remove this before the pilot.
- Android old-format living wills come back as drafts to sign again, including ones marked executed. No real ones should exist.
- Native review for Android translations: Sahara in both Latin and local script; Kannada "ಪಾಲಕರು" means both custodian and guardian; transliterated case names; "resuscitation bay" in Telugu.

## Before any real patient

Ethics committee approval, hospital legal review, real login, clinician back-translation, paid AI key or Gemini removed from `SAHARA_AI_CHAIN` (Gemini's free tier may use submitted content), and the DPDP data fiduciary named. See `blockers.md`.

## Restore points

Git tags: `checkpoint-2026-10-29-before-svk-build`, `checkpoint-2026-10-30-web-svk`, `checkpoint-2026-10-30-android-svk`.
