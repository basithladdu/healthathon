# Blockers: only Basith, Awaiz or the team can do these

Agents cannot do these: server migrations, deployments, provider accounts and keys, decisions, Play Console, and tests on a real phone. Everything else is being built. Linked from [everyday.md](everyday.md) and [HANDOFF.md](HANDOFF.md). Tick items as they are done.

**Who:** Basith = owner; Awaiz = Sahara app; team = the clinicians (Sujay, Sharada, Shirin).

Collected on 27 Oct from every agent lane across Sahara, ITI, WDCW and AI-FIR. The web survey and Android feature work have separate handoffs; these are the remaining owner/team actions. Tick an item only after the external step is verified.

### Sahara: status on 2 Nov 2026 (Awaiz)
New since 1 Nov, all on GitHub main (`b3435d7`): patient profile in the shared record, doctor Schedule redesign, compact cards, point graphs, read aloud in the chosen language, about 100 more translations, force-dark off. Details in HANDOFF.md "2 Nov 2026". A development APK was shared for a real-phone test.

Done:
- [x] Basith's 1 Nov Android code (legal comments, goals-of-care transcription) built: compiles, 760 unit tests pass.
- [x] "Ask your documents" with Gemini (`gemini-3.1-flash-lite`) works; the function retries once when Google is busy.

Still open for Awaiz:
- [ ] Check on the Xiaomi phone that text is no longer faded (force-dark turned off in `res/values-v29/styles.xml`).
- [ ] Read aloud in Telugu, Kannada, Urdu, Tamil or Hindi: install that language's voice in the phone's text-to-speech settings, then test one note.
- [ ] Optional: add `SAHARA_AI_FALLBACK_MODEL` (e.g. `gemini-3.5-flash`) in Supabase secrets, because the free model sometimes says "high demand".
- [ ] Give the doctor-verifier and centre-reviewer roles (needs a team member's email). Until then no real doctor can join a patient's care.
- [ ] Real-phone test checklist (CLAUDE_TRANSCRIPT.md §3) and the narrated walkthrough video.
- [ ] Before real patient data: Google billing on, and clinician back-translation of the Telugu, Urdu and Kannada dictionaries.

Needs Basith:
- [ ] Website deploy for the 1 Nov web work (`vercel deploy --prod` from `prototype/`, see HANDOFF.md).
- [ ] Play App Signing SHA-256 into `assetlinks.json`; next Play upload (versionCode 9 or higher).

Small code follow-ups:
- [ ] "Plan a visit" from the doctor Schedule should open on the day selected in the strip.

### Sahara: status on 1 Nov 2026 (Awaiz, checked on the Saanthvana project and the emulator)
Done and checked:
- [x] All Supabase migrations are applied on Saanthvana (`kjnhtakqzboayqorrlue`): 20 in total, including emergency links, feedback, account lane, "Ask your documents", signature receipts and centre suggestions. `android/supabase/tests/emergency_links.sql` passed.
- [x] AI: the Android app now calls the Supabase function `sahara-care-assist` directly (not the Vercel route). Secrets are set in Supabase → Edge Functions → Secrets (`SAHARA_AI_BASE_URL` = Gemini's OpenAI-compatible URL, `SAHARA_AI_API_KEY`, `SAHARA_AI_MODEL` = `gemini-3.1-flash-lite`, `SAHARA_AI_PROVIDER_NAME` = Google Gemini). Function deployed (version 7): low thinking for Gemini, one retry when Google is busy, and an optional backup model.
- [x] "Ask your documents" answered on the emulator with a cited line (practice discharge PDF, page 1 line 9). Google's free tier sometimes says "high demand" (503); the app then shows the lines found on the phone.
- [x] Sign-up is on in Saanthvana ("Confirm email" was turned off, so new accounts are confirmed at once); Add family by WhatsApp link with auto-join works.

Still open for Awaiz:
- [ ] Optional: add `SAHARA_AI_FALLBACK_MODEL` (e.g. `gemini-3.5-flash`) in Supabase secrets, so a busy model falls back to a second one.
- [ ] Give the doctor-verifier and centre-reviewer roles: needs the team member's email (SQL below under "Doctor approval" and in HANDOFF.md). No staff role exists yet.
- [ ] Test on a real phone (the checklist in CLAUDE_TRANSCRIPT.md §3) and record the narrated walkthrough.
- [ ] Before real patient data: turn on billing for the Google project (free tier may be used to improve Google's products).

Needs Basith:
- [ ] Play App Signing SHA-256 into `prototype/public/.well-known/assetlinks.json`, then redeploy, so invite links open the app.
- [ ] Next Play upload (versionCode 9 or higher) and the Data safety form.
- [ ] Note: the Vercel AI route `/api/sahara-assist` is no longer used by the Android app.

### Sahara: from the 3 Nov meeting and WhatsApp (updated 5 Nov)

Done (5 Nov):
- [x] Android compiles; 877 unit tests pass; phone APK built (`Desktop/Sahara-dev-2026-11-04b-phone.apk` on Basith's laptop).
- [x] Website deployed to sahara.wedevit.in.
- [x] Goals of care follows Rituparna's legal form word for word, in 8 taps; doctor side shows the same layout.

**Awaiz**
- [ ] `supabase functions deploy sahara-care-assist --project-ref kjnhtakqzboayqorrlue` from `android/` (goals-of-care Gemini on Android).
- [ ] Vercel env: `SAHARA_AI_API_KEY` and `SAHARA_AI_MODEL`, then redeploy (goals-of-care Gemini on the website).
- [ ] Real-phone test of the APK: goals-of-care recording, signing and PDF; Today; Nearby location; per-dose ticks; family view; faded text on the Xiaomi.
- [ ] `./gradlew :app:connectedDebugAndroidTest` on a device or emulator.

**Team and doctors**
- [ ] Tagline (meeting: "we'll put that on the group").
- [ ] 6-8 minute video (organisers' email).
- [ ] Mock goals-of-care conversations with Dr. Sharada using the app; the 8 written mocks are in `docs/goc-mock-conversations/`.
- [ ] Legal form lines with no field in the app yet (they print "Not discussed"): the 6.1 prompts, the 5.3 boxes, "with two witnesses", the "Other" transfer reason. Decide: add fields or leave them for hand-writing.
- [ ] "Confirm all" on the review screen also ticks the V4 understanding confirmation. Keep it, or make the doctor tick V4 separately?
- [ ] Fluids and ambulance are not in the note. Should they be?
- [ ] Review the machine-written Telugu, Urdu and Kannada phrase lists and translations.
- [ ] Gemini free tier: Google may use submitted audio to improve its models. A paid key avoids this before real patients.
- [ ] Decide five places where we differ from Saanthvana v1.0: hand feeding is not in §16; Goal B wording; "Family consensus" vs our "next of kin"; "CRT" vs "CRT-D"; where the "unticked means no preference" note sits.
- [ ] Side-effect list: generic, hand-written by the team. Approve it or give a source.
- [ ] Tubes and lines: approve the device list and daily check items (Sujay and Shirin, 3 Nov).

**Basith**
- [ ] This machine cannot resolve vercel.com or supabase.co; fix DNS or use another network to deploy from here.

### Sahara: legal review and transcript check (added 1 Nov)

**Awaiz**
- [ ] Build Android and deploy the website: steps are at the top of `HANDOFF.md`. Neither was done for the 1 Nov work. **2 Nov: Android built (compiles, 760 unit tests pass); the website deploy is still to do.**
- [ ] Test goals-of-care recording on a phone. Sujay's mock conversation week needs this, a doctor and an Android phone; none are recorded yet.

**Basith, now**
- [ ] Change the admin password `0` (`prototype/app/api/admin-survey/route.ts:15`). The survey message promises details "are not shown publicly", and that page shows names and registration details.
- [ ] Confirm the deadline. The organisers' Phase 2 PDF says the Build Sprint ends 7 Nov 2026; ask them for the portal.

**Sujay, Sharada, SVK, Rituparna (legal review questions the code cannot answer)**
- [ ] C1: is this form for non-cancer patients too? The app now accepts any specialist, pending your answer.
- [ ] C7: write the capacity annexure. The guidance in the app is our draft and is marked as one.
- [ ] C8: should "what the patient knows" be checked before or after counselling? The app records which, but someone must decide.
- [ ] C3: approve the wording on live-in and queer partners.
- [ ] Approve the withdrawal or withholding step, including rule W1, and confirm whether Rituparna's review is the Vidhi review Sujay mentioned on 27 Oct.
- [ ] Approve the topic order on the goals-of-care checklist and the phrase lists in all five languages.
- [ ] Decide: Sujay asked on 23 Oct for hospital address and map to be compulsory, but the survey has them optional with no record of agreement.
- [ ] Decide: Telugu and Kannada speech-to-text is not usable with the free models tested, so these two languages use typing for now. Is that acceptable for the pilot?

**Missing:** no recording or transcript of the 28 Oct team meeting is in the repo. Add it if it has requirements not captured here.

### Sahara: API keys, Awaiz to do (added 30 Oct)
**1 Nov:** the Android app no longer uses these. It calls the Supabase function, whose key is in Supabase secrets (see "status on 1 Nov" above). These Vercel keys only matter for the website's own AI pages.
**Owner: Awaiz.** Add these in Vercel → project `continuity-loop-healthathon` → Settings → Environment Variables (Production), then redeploy with `vercel deploy --prod` from `prototype/`. Sign-up links, free limits and optional overrides are in [docs/FREE_MODELS.md](docs/FREE_MODELS.md). The care plan, living will, emergency page and QR do not need any of them.
- [ ] `GEMINI_API_KEY`: AI answers, note drafting, speech-to-text and translation. Free tier is for fake patients only; see the billing note below.
- [ ] `GROQ_API_KEY`: backup when Gemini fails or hits its limit, and backup speech-to-text.
- [ ] Optional more backups: `OPENROUTER_API_KEY`, `HF_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` + `CLOUDFLARE_API_TOKEN`.
- [ ] Optional translation of typed text: `BHASHINI_USER_ID` + `BHASHINI_API_KEY`.
- [ ] SMS for the emergency override, which otherwise says "SMS not configured": see `prototype/app/api/ed-card/sms.ts` for the provider variable names.
- [ ] After adding, check on the live site that an AI answer works and shows which service answered.

### Sahara: SVK spec, team to decide (added 30 Oct)
Details in [docs/SAHARA_SVK_BUILD_HANDOFF.md](docs/SAHARA_SVK_BUILD_HANDOFF.md). The care plan table migration is applied.
- [ ] SVK + medical lead: the sign-off list in clinical spec §9 (temporary defaults: 12-week review, 3-day trial, content shown under the out-of-date banner).
- [ ] SVK + legal: does not starting a treatment also need the medical boards for a patient who cannot decide? And the 30-day reconsideration period missing from the spec. See `docs/SAHARA_ECTPR_AMD_REFERENCES.md`.
- [ ] SVK: check "care that always continues" against Saanthvana v1.0 §16.
- [ ] SVK: should the emergency PDF be English and Hindi only for staff, or also Telugu and Urdu for families?
- [ ] Clinician: back-translate Telugu, Urdu and Kannada before the pilot.
- [ ] Before real patients: real login, ethics committee approval, hospital legal review.
- [ ] Basith: free up disk F: (nearly full; Android builds fail without about 2 GB free).

### Sahara: do these now (29 Oct)
**AI with Gemini (one key, on Vercel).** The Android app now calls `https://sahara.wedevit.in/api/sahara-assist` for note drafting, speech-to-text, translation and "Ask your documents". It replaces the Supabase function, so the Supabase function deploy below is no longer needed.
- [x] Google AI Studio (aistudio.google.com) → Get API key → create a key. **31 Oct: done; the key is in Supabase secrets instead of Vercel.**
- [ ] **Before real patient data:** turn on billing for that Google project (paid tier). On the free tier Google may use what is sent to improve its products and humans may review it; the paid tier does not. Also check the data region. For the demo with fake patients the free tier is fine.
- [ ] Vercel → project `continuity-loop-healthathon` → Settings → Environment Variables → add `GEMINI_API_KEY` (Production) = the key. Then redeploy: run `vercel deploy --prod` in `prototype/`. Nothing else is needed: model defaults to `gemini-3.5-flash-lite` (override with `GEMINI_MODEL`).
- [x] Optional but better: apply migration `android/supabase/migrations/20261019151322_shared_care_clinician_review.sql` (holds `sahara_reserve_assist`, the durable 1-a-minute / 10-a-day limit per account). Until then the server uses a per-instance limit that is only a speed bump. **31 Oct: applied.**
- [x] Optional: apply `20261028120000_assist_document_questions.sql` so the database limit also counts "ask". **31 Oct: applied.**
- [x] Check on a phone after the key is in: sign in with a confirmed account → Docs → "Ask your documents" → "Search all my documents" on → ask "When is my next review?". The answer must show the cited lines. **31 Oct: checked on the emulator; the answer showed the cited line.**

**Speech-to-text.** Gemini hears the recording directly (same key). The app also keeps on-device transcription (sherpa-onnx) for offline. Whisper is not free through OpenAI's API (paid per minute); it is free only if you run it yourself on a GPU server. For Indian languages Sarvam's Saarika/Saaras (paid; the website already has Sarvam code) or AI4Bharat IndicConformer (open source, self-hosted) are stronger than Whisper. Decide after trying Gemini on Hindi and Kannada.
- [ ] Try one 20-second Hindi and one Kannada turn on a phone; if the transcript is poor, decide Sarvam (key into Vercel) or self-hosted.

**Survey admin dashboard** (`https://sahara.wedevit.in/admin`, password `0`, owner's choice). No reviewer account is needed any more; the page reads responses on the server with `SUPABASE_SERVICE_ROLE_KEY`, which is set in Vercel.
- [ ] Note: password `0` is effectively public, and it shows respondents' names, registration details and email. Replace it before sharing the link more widely.
- [ ] Optional: permanently remove the two hidden test rows (SVK, QA TEST) with the SQL editor; the API only hides them.

**Phone and Play**
- [ ] Plug the Samsung back in and unlock it; the waiting command installs and opens Sahara 0.6.2 (9) development build. If that command has ended, run from `android/`: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- [ ] Release bundle: close other apps (R8 needs about 4 GB free) and run `gradlew.bat --no-daemon bundleRelease` from `android/`. Upload to Play only after the current review finishes.

### Sahara (healthathon)
- [x] Apply the Supabase migrations the lanes add (emergency QR link, account delete, feedback, sync), then re-test. **31 Oct: all applied on Saanthvana.**
- [ ] Add the Play App Signing SHA-256 to `prototype/public/.well-known/assetlinks.json` and redeploy, so invite links open the app. 29 Oct: the website itself is deployed to production and `/join` is live (200).
- [ ] Supabase settings: email sign-up confirmation, leaked-password protection, lock `rls_auto_enable()`.
- [ ] Approve doctor accounts: now done in the app (see "Sahara owner keys and steps (no-mock round, 28 Oct)" below).
- [x] AI note drafting: put the AI key on the server (see "Sahara owner keys and steps (no-mock round, 28 Oct)" below). Until then the app keeps word matching and says AI is not set up. **31 Oct: done in Supabase secrets.**
- [ ] Decide the anonymisation scope: patient name → ID on the doctor's screens; blur faces in photos.
- [ ] Re-tapping a marked body part: move the spot or add a new one?
- [ ] Pick the CARE option in the team meeting (A / A+B / A+C).
- [ ] Send the OHC email and the private security disclosures (drafts in docs/outreach/).
- [ ] DPDP: name the data fiduciary (company or new entity); approve the privacy policy and consent text (drafts in docs/compliance/).
- [ ] Play: next upload uses versionCode 9 or higher, once 0.6.0 (7) is out of review; fill in Data safety from the draft. 28 Oct: source is 0.6.2 (9). The debug build (`in.wedevit.sahara.development`) built and is for the phone test. The signed release APK/AAB did **not** build: R8 (`minifyReleaseWithR8`) ran out of native memory three times with under 3 GB free. Close other apps and rerun `gradlew.bat --no-daemon bundleRelease` from `android/`; no v9 AAB exists yet.
- [ ] Test on a real phone: checklist in CLAUDE_TRANSCRIPT.md §3.
- [ ] Final template: team code, doctor details, costs; decide how judges see the code (repo is private).
- [x] Working drafts filled in the 27 Oct two-page and 12-slide organiser templates on 28 Oct; see `submission/phase-2/final-deliverables-2026-10-28/`.
- [ ] Approve the draft's team/doctor descriptions, Shirin's role, CARE scope, candidate measures, costs and pilot wording; confirm team code.
- [ ] Ask organiser for the confirmed submission deadline, portal, video duration and evaluator-access method; the 27 Oct email does not specify them.
- [ ] Prepare evaluator access privately and verify the actual join/install path; `/join` is live since 29 Oct; the app-link signing fingerprint is still a placeholder.
- [ ] Record and review the narrated real-phone walkthrough with Awaiz and an approved doctor account; do not use screenshots or a silent capture as the video deliverable.

### Sahara team meeting 28 Oct: people-only items
- [ ] Doctors: share the exact goals-of-care screening tool on WhatsApp (the app has a pluggable helper using the surprise question + ECOG until then).
- [ ] Doctors + Shirin: finalise the simulated conversation data by the weekend; record mock goals-of-care talks directly in the Sahara app (Android phone needed).
- [ ] Tomorrow evening 9:15: go through the questionnaire format (additions/deletions); others join 9:30.
- [ ] Send the palliative care / morphine survey (tomorrow morning).
- [ ] Legal (tomorrow): are public "morphine available today" votes OK? Diversion/addiction risk. Not built until cleared.
- [ ] Decide: is a government-ID-based signature wanted at all? A clinician is uncomfortable; the app uses a drawn signature on the phone or a photo of a paper signature.
- [ ] NCCN Distress Thermometer: confirm permission to use it in the app (non-commercial clinical use).
- [ ] Doctors: ask medical oncology colleagues for real patient support groups; check docs/research/SUPPORT_GROUPS_INDIA.md before anything goes in the app.
- [ ] Nearby: decide who on the Sahara team reviews "suggested centres" before they are shown.
- [ ] Deliverables: PPT, two-page brief, narrated video. Build sprint done by Sun 1 Nov; submission about 5 days after (confirm the exact cut-off). Present as a tag team: doctors explain the problem and clinical features, tech explains the build.
- [ ] Play Store: upload the new version (versionCode 9+); last update was about 19 Oct.
- [ ] NCCN Distress Thermometer (decision 7, 28 Oct): the NCCN PDF says it "may not be reproduced in any form without the express written permission of NCCN". Ask NCCN for permission for the in-app version (English wording from Version 1.2026), and get the official NCCN Hindi translation (free NCCN login at nccn.org/distress-thermometer) so a clinician can compare it with Sahara's own Hindi labels in `CareDistress.kt`.

### Sahara owner keys and steps (no-mock round, 28 Oct)
**AI note drafting, recorded-turn transcription and note translation** (works with any OpenAI-compatible service: a hosted one, or an open model you run yourself, e.g. vLLM, Ollama or llama.cpp server for text and faster-whisper-server for audio, behind HTTPS).
- [x] Supabase → project Saanthvana (`kjnhtakqzboayqorrlue`) → Edge Functions → Secrets → add: **31 Oct: done (Gemini).**
  - `SAHARA_AI_BASE_URL` = the service's base URL ending in `/v1` (must start with `https://`)
  - `SAHARA_AI_API_KEY` = your key (leave out for a self-hosted server without a key)
  - `SAHARA_AI_MODEL` = the chat model name
  - optional `SAHARA_AI_TRANSCRIBE_MODEL` = the speech-to-text model name (turns on "Transcribe a recorded turn")
  - optional `SAHARA_AI_PROVIDER_NAME` = the name people see in the consent line (default: the URL's host name)
- [x] Deploy the function once, from `android/`: `supabase functions deploy sahara-care-assist --project-ref kjnhtakqzboayqorrlue` (code: `android/supabase/functions/sahara-care-assist/index.ts`). **31 Oct–1 Nov: deployed, now version 7.**
- [x] Check migration `20261019151322_shared_care_clinician_review.sql` is applied (it holds the attempt limit `sahara_reserve_assist`: 1 a minute, 10 a day per account, 100 a day in total). **31 Oct: applied.**
- [x] "Ask your documents" (Docs tab, sa-docs lane, 28 Oct): apply migration `20261028120000_assist_document_questions.sql` (lets the same attempt limit count `ask`), then redeploy `sahara-care-assist`. Until both are done and the AI key is set, the app shows "AI is not set up yet". Only the question and the text read on the phone are sent, never photos or files. **31 Oct: applied and redeployed; works.**
- [ ] Check the service's data terms (retention, training use, region) against the DPDP drafts. The app names the service in every consent line and sends nothing until everyone agrees and someone taps the button.

**Doctor approval** (no migration needed; the server function `healthathon_review_clinician` already exists).
- [ ] Once, in Supabase → SQL editor, make a Sahara team account a verifier:
  `insert into healthathon_private.staff_roles(user_id, role) select id, 'clinical_verifier' from auth.users where email = 'TEAM_EMAIL';`
- [ ] Sign in to the app with that account → Doctor workspace → Account → "Open verification and shared care". The "Approve doctors · Sahara team" card lists waiting doctors. Check the number on the NMC or state council register, fill in where you checked and a reason, then tap Approve. The server records who approved, what was checked and why. You cannot approve your own account, so the doctor test account must be a different account.
- [ ] Only if the app can't be used: `update public.healthathon_clinician_requests set status = 'approved', reviewed_at = now(), review_note = 'Checked on REGISTER', revision = revision + 1 where user_id = (select id from auth.users where email = 'DOCTOR_EMAIL');` (this skips the review log).

### ITI (devit-iti)
- [ ] SMS/WhatsApp provider plus `ITI_AUTO_PARENT_ALERTS`, to turn on automatic absence messages to parents.
- [ ] Decide whether the send log should appear outside the archived Messages screen.
- [ ] Free SMS, recommended: install "SMS Gateway for Android" (sms-gate.app) on an ITI phone with a SIM, turn on Cloud server, then Vercel → Project → Settings → Environment Variables → add `SMSGATE_USERNAME` and `SMSGATE_PASSWORD` → Redeploy. Or use MSG91 (`MSG91_AUTH_KEY`, `MSG91_SMS_TEMPLATE_ID`) or Twilio. WhatsApp is optional (`WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_TEMPLATE_NAME`). Until then staff use "SMS from my phone".
- [ ] Parents only get messages when "Parent mobile" is saved for the student.

### WDCW (devit-wdcw / -android)
- [ ] Deploy the current web main before testing Android evidence recovery.
- [ ] Play signing key and Console setup (`WDCW_UPLOAD_*`).
- [ ] Pick one and add it in Vercel → devit-wdcw → Environment Variables (Production), then Redeploy:
  - MSG91: `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_ID`, `MESSAGING_SEND_ENABLED=true` (the DLT template text is in HANDOFF);
  - your own Android phone: `SMS_PROVIDER=android`, `SMS_GATEWAY_USERNAME`, `SMS_GATEWAY_PASSWORD`, `MESSAGING_SEND_ENABLED=true`;
  - or Twilio.
  Until then staff use "Send from my phone".
- [ ] Decide whether to keep the 51-centre sample mode that judges reach from the demo logins. It still shows "Prototype data" on its sample nutrition page.

### All repos
- [ ] Delete the old `quick/*` and `checkpoint/*` branches on GitHub once happy (agents may not delete them).

### Sahara outreach (from the partners lane, drafts in docs/outreach/)
- [ ] Approve the wording of the Pallium and Arike pilot pages, the OHC email and the 3 security disclosures, then send. Fill in the sender name and reply address.
- [ ] Find OHC's private security address in `SECURITY.md` of ohcnetwork/care.
- [ ] Niram notice: first check the repo is still public; if the files are gone, send nothing.
- [ ] Rename the goals-of-care form to `sahara-goc-conversation` (25-character limit) before attaching it.
- [ ] Pilot timing: after the submission (Pallium: after their new CEO starts). Ask Dr Athul Joseph Manuel for the introduction.
- [ ] Who translates and clinically checks Malayalam (Arike) and possibly Kannada (Pallium Karnataka).
- [ ] Ask the authors' permission for the caregiver burden scale (AMC-CBI / Brief AMC-CBI).
- [ ] Does a pilot need ethics review? Confirm the Supabase region and the DPDP position first.

### Sahara feedback / AI (from the assist lane)
- [ ] Apply migration `android/supabase/migrations/20261027190500_sahara_app_feedback.sql`. Until then, signed-in feedback stays queued and says "not sent yet".
- [ ] Should closing an account also delete that person's feedback rows?

### GitHub (all repos)
- [ ] **Fix GitHub billing** (failed payment or spending limit: Settings → Billing & plans). Since 19 Oct no GitHub Actions job has started; every "failure" is GitHub refusing to run, not broken code. After that, run "Android verified packages" on main by hand, then "Android runtime verification" with that run's ID.
- [ ] Optional: pause the daily Saanthvana health check until billing is fixed (each day adds another failed run).

### Sahara pitch (from the pitchkit lane: docs/pitch/KPI_EVALUATION_DRAFT.md, DEMO_VIDEO_SCRIPT.md)
- [ ] Team: approve 2–3 KPIs and the 54-word evaluation slide text; decide whether to keep the illustrative targets.
- [ ] Team: fill in roles, the team code, Shirin's role and the technology partner in the video script; doctors approve the part about them.
- [ ] Record the demo video on a real phone with Awaiz. Approve the doctor test account first, upload the video and test the link signed out.
- [ ] Name a pilot site and a doctor contact (needed before any KPI can be measured).

### CARE forms (docs/care/: 3 questionnaire files plus a checker, not loaded anywhere)
- [ ] Pick a CARE hospital and its superuser to load the forms; get the hospital's organisation ID.
- [ ] Approve the final wording (plus Hindi/Malayalam) before any form goes active: active forms can't be edited.
- [ ] Decide: should 0–10 scores stay as plain numbers (no range check) or become 0–10 choices (range enforced, stored as text)?
- [ ] Doctors: give a clinical source for the goals-of-care prompts, and the medical codes for the 12 body areas, before any codes are added.

### Privacy / Play / DPDP (drafts in docs/compliance/)
- [ ] Choose the data fiduciary: WEDEVIT PRIVATE LIMITED or a new entity. Name a grievance contact (name or role, email, response time).
- [ ] Confirm the Supabase region for the project the shipped build uses, and say which project the next Play build uses.
- [ ] Play Data safety: add "Phone number" (care contacts) and "Other info" (doctor registration number)? Can a real user reach the doctor test-workspace upload? If yes, declare Audio and Files.
- [ ] Patient consent: today the app records no consent from the patient themselves. Decide how the patient or a guardian consents and names a nominee, when the consent screen shows, and whether consent is saved on the server.
- [ ] Decide the retention period, who has server admin access, and whether that access is logged.
- [ ] Native speaker checks the Hindi consent text; lawyer reviews the policy.
- [ ] After merge and migrations: update Play Console and publish the new policy (the live website policy is missing DPDP rights, the server region, admin access and care contacts).

### ITI State Admin / District (from the state lane)
- [ ] Keep or remove the hidden "Data setup" page (`/iti-dashboard/admin`)? It has seed and delete-all buttons (including "Delete all questions") and appears in no menu.
- [ ] User and ITI management exists only inside the archived "Staff list". Unarchive it, or ask for a new screen.
- [ ] Do you want a monthly entry form for the manual score categories (admissions, results, dropouts, records)? Today the ranking uses only saved data. An unused `ITIMonthlyScoreEntry.jsx` already exists.
- [ ] Should District Officers be able to correct staff attendance (today only the Principal can)?
- [ ] After merge, check the ITI drill-down with a real statewide State Admin account.

### WDCW Android sync (from the sync lane)
- [ ] When two officers edit the same record (a real conflict), should Retry overwrite the server copy? Today it fails again and Discard is the only way out.
- [ ] Native speaker checks the new Telugu strings (QR scanner, Retry, sign-in expired).
- [ ] Test on a real phone: kill the app mid-upload, let the sign-in expire with drafts queued, deny the camera on the QR scanner.
- [ ] WDCW: how many complaint escalation levels in the pilot? The picker offers 1–5, but the server silently stops at level 2 while the complaint's history still shows the higher level.
- [ ] WDCW: test the centre-mode switch, starting and resolving a grievance, and dismissing an alert on a real phone. Native speaker checks the 17 new Telugu labels.

### KPI counting (docs/pitch/kpi-queries.sql, read-only)
- [ ] During the pilot: check the Supabase version is Postgres 16 or newer (`select version();`), collect the pilot account IDs, choose a private salt, and run the queries one at a time in the SQL editor.
- [ ] Team: confirm the counting rules. Days come from the phone's save time; week 1 starts on the pilot start day.
- [ ] Doctors keep a manual tally of who started each talk (the app doesn't save it).

### Sahara reminders (from the daily lane)
- [ ] Keep or drop the new "on-time alerts" permission (`SCHEDULE_EXACT_ALARM`) for the next Play upload. If kept, fill in Play Console's exact-alarm declaration.
- [ ] Entries with a date but no time now get no alert until someone sets a time. Do you want a default instead (e.g. 09:00)?
- [ ] Test on a real phone: a reminder fires at the set time, tapping it opens Reminders, it still fires after a reboot, and the "Allow on-time alerts" button opens settings.

### ITI staff (from the staff lane)
- [ ] Demo data: add sample feedback about the ATO/instructor? Today their feedback register is empty on the demo, because the samples only cover the TO.
- [ ] Weekly "Remind class" is an in-app notice only. SMS/WhatsApp needs the provider (same as the absence alerts).

### Sahara privacy redaction (from the privacy lane)
- [ ] Should journal notes, task text and visit notes also have phone numbers and addresses removed? Today only symptom notes and conversations are covered; visits often hold the clinic's number on purpose.
- [ ] Names typed inside free text are not removed. Do you want that? (Hard to do reliably.)
- [ ] Test on a real phone: signed in, type a phone number in a symptom note, then check the doctor sees `[phone]`.

### ITI students (from the student lane)
- [ ] Should students get a Jobs / OJT applications screen? Today they have none; Internships stays archived.
- [ ] Upload the original timetable and syllabus files so student Downloads work (most show "Not uploaded").
- [ ] Test face and OJT check-in on a real phone.
- [ ] Redeploy the ITI site after merge so the "student has read it" notice change goes live.
- [ ] WDCW: test on a real phone. A worker marks attendance and the supervisor sees it; a parent scans the centre QR in the app without signing in; offline submit, then reconnect, then Pending changes.
- [ ] WDCW: confirm the live server accepts attendance saves from real worker accounts, not only demo ones.
- [ ] WDCW: App Links verification (signing fingerprints in `assetlinks.json`) so QR stickers open the app from the phone camera.
- [ ] Sahara: confirm that "Your care team" on Today should open the Care team screen (tasks and conversation requests); doctors now reach their workspace from More → Doctor workspace. Test the reminder tap and the "See all" buttons on a phone.
- [ ] Sahara animations: on a real phone, check tab and screen changes, cards opening, and the "saved" tick; check that turning on "Remove animations" stops all motion; check the cards rising in on first load feel calm for elderly users. If not, it's one setting to turn off (`STAGGER_WINDOW_MS = 0`).

### AI-FIR (ai-fir)
- [ ] Choose an SMS gateway or department sign-in for OTP and password reset. Today any 6 digits work, and the screen says no password is changed.
- [ ] Put an AI provider key on the server (`GEMINI_API_KEY` or `ANTHROPIC_API_KEY` in Vercel), then re-test live extraction. Without it, the on-device rules are used.
- [ ] Legal/product call: may the rules treat a "rider" in a panchanama as the accused when the paper never says "accused"? (FIR 246/2026 currently shows "No accused recorded".)

### Sahara conversation (from the convo lane)
- [ ] Check the live Supabase has migration `20261023070323_conversation_scope_context.sql`. Without it, "Send for clinician review" stops with "needs the updated backend".
- [ ] Test on a real 64-bit phone (about 2.5 GB free memory, 987 MB download): on-device transcription, the live goals checklist, the line jumps, and the full path (send → owner approves who sees it → verified doctor publishes → family confirms).
- [ ] ITI: "OJT sites" (`ojt/map`) and "OJT syllabus" (`ojt/syllabus`) exist but are in no menu, so nobody can reach them. Add them to menus or remove them?
- [ ] ITI: "low" attendance and test score means below 60% (same as "Quick tests to review"). Keep 60%?
- [ ] ITI: should a State Admin or District Officer viewing one institute be able to make a task for it? If yes, who gets the task (the Principal)?
- [ ] AI-FIR: choose the Supabase organisation so drafts, versions and Chitti threads can be shared across officers and devices. Today they stay in one browser.
- [ ] AI-FIR: supply the approved AP case diary and chargesheet formats.
- [ ] AI-FIR: test mobile.html share and print on a real Android phone.
- [ ] WDCW web: supply this month's curriculum and the escalation contacts as CSV or JSON for the new admin "Imports & Curriculum" screen.
- [ ] WDCW: SMS/WhatsApp provider (`TWILIO_*` or `META_*`, plus `MESSAGING_SEND_ENABLED=true`), or keep it simulated.
- [ ] WDCW: after merge, redeploy the website (Vercel) so the Android fixes and the new web screens go live.

### Sahara accounts (from the account lane)
- [ ] Apply migration `android/supabase/migrations/20261027210000_sahara_account_lane.sql`. It's needed for the real "Joined" status, Change access, and deleting an account in the app.
- [ ] Check whether the database is allowed to delete sign-ins (`auth.users`). If not, a deleted account keeps a closed sign-in, unless we add an Edge Function.
- [ ] Put the Play App Signing SHA-256 into `prototype/public/.well-known/assetlinks.json`, then redeploy the website (this also publishes the new `/join` page).
- [ ] Supabase email: turn off "Confirm email" or set up your own SMTP. Today confirmation mail only reaches team addresses.
- [ ] Should family access really end after 30 days? The app now shows the end date.
- [ ] Someone who used the app without an account and then signs up does not get their earlier patient moved into the new account. Build an import?
- [ ] Update the website's `/delete-account` page once in-app deletion is live.
- [ ] AI-FIR: the demo's planned entries for example-case-018 (17 and 24 Oct) are now in the past, so the "Planned" badge no longer shows. Move those dates later?

### Sahara doctor side (from the doctor lane)
- [ ] Create a real doctor account and approve it by hand in the database, then test on a phone: Home, Schedule, patient search by ID (`SH-…`), and joining with a family's code.
- [ ] Should the doctor workspace follow the family's language choice, or the phone's language as it does today?
- [ ] A doctor with read-only access can't plan visits. The greyed-out button now says why (sahara-complete lane); still decide: should read-only doctors be allowed to plan visits?

### Sahara Nearby (from the nearby lane)
- [ ] "Distance from me" needs a location permission (the manifest removes it today). Add it?
- [ ] Show the Saath Saath palliative helpline 1800-202-7777? It is run by palliative organisations, not the government.
- [ ] Choose sources for oxygen, blood bank, pharmacy, imaging and ambulance listings (none in the current data).
- [ ] Test Nearby on a phone: the dialler, maps, Share and the animations.

### Sahara medicines (from the meds lane)
- [ ] Confirm migrations `20261023053321_doctor_medicine_history.sql` and `20261023071214_doctor_prescription_groups.sql` are applied on the Sahara Supabase. Without them, missed doses, medicine history and doctor prescriptions fail to save (they show an error; nothing falsely says "Saved").
- [ ] The medicine-name list (OHC cdci, CC BY 4.0) may only be redistributed within India. Keep the Play listing India-only, or check the licence first.
- [ ] Clinicians: should a family's "missed dose" report be shown to the doctor? (Linked to E8/E9.)
- [ ] Test on a phone: Missed today, History, the doctor's Intake history tab, and the name suggestions.

### Goals-of-care answer finding (from the simulated-conversations test)
- [ ] Pitch and demo: don't claim the app finds the answers. In 20 test conversations, word matching found 0 of 130 answers and often suggested the doctor's own question. Suggested wording is in `docs/research/SIMULATED_CONVERSATIONS_2026-10-27.md`.
- [ ] Ask a clinician (Sujay, Sharada or Shirin) to check the 20 test conversations, or to send real de-identified examples.
- [ ] Sahara Docs: test on a phone: camera scan, rename, the cancer-documents filter, copying a lab result from a scan, chemo/radiotherapy lines on the chart, and opening the password-locked note (an AES ZIP) in 7-Zip or WinRAR.

### Sahara emergency QR (from the qr lane)
- [ ] Apply migration `android/supabase/migrations/20261027190000_emergency_card_links.sql` to the Saanthvana project, then run `android/supabase/tests/emergency_links.sql`.
- [ ] Deploy the website with the new `prototype/app/emergency` page and the `/emergency` headers in `next.config.ts`.
- [ ] The QR link can be revoked and expires after 1 year; it is not single-use, because a printed card must scan more than once. OK?
- [ ] The website's account page still points at the old server (the older Supabase project); the app uses Saanthvana. Switch the website over?
- [ ] Test on a phone: making the card, full-screen brightness, Print, Save, opening the locked PDF in Drive or Adobe, and scanning both QR codes.

### Sahara send-later (from the queue lane)
- [ ] Test on a phone: airplane mode on → save, check-in, confirm, add a contact → airplane mode off. The waiting count should drop to 0 with no duplicates on the server.
- [ ] Should unsent changes also be sent while the app is closed? (Needs WorkManager; today they send when the app is open.)

### Sahara Hindi (from the Hindi lane)
- [ ] A native Hindi speaker (ideally a palliative clinician) reviews the wording. None of it has been reviewed yet.
- [ ] Test on a phone: Hindi fonts, whether text fits in buttons and the bottom bar, and Hindi dates.
- [ ] Doctor walkthrough and recording panels now have Hindi (sahara-complete lane), except the online-audio and local-preview panels the real-sahara lane is replacing. A native speaker still checks it.
- [ ] Sahara accessibility: on a real phone, try TalkBack on the pain body (3D and fallback), Today, the care list and the emergency card; check the app at the largest font and display size.
- [ ] Sahara map: approximate locations come from GeoNames (CC BY 4.0, credited under the map) because data.gov.in couldn't be reached. Accept that, or download the Government PIN directory yourself so the script can be rerun on it?
- [ ] Sahara map: on a phone, check the cluster numbers show and the hollow (approximate) pins are easy to tell from the solid (exact) ones.
- [x] Directory data: remove 3 junk header rows (2 Assam, 1 Maharashtra); one Vasai centre is listed under Mumbai district. Done (sahara-complete lane): 1,628 listings; both Vasai listings are in Palghar.
- [ ] Goals matcher (after the fix): 110 of 130 answers found, 1 wrong, on our own 20 test conversations. That is optimistic, since the rules were written after reading some of them. Don't quote 85% as real-world accuracy; wording is in the report.
- [ ] A clinician or native speaker reviews the Hindi/Hinglish question wording and the "put-off" list in `GoalsQuestionVocabulary.kt`.
- [ ] Should a patient's own question count as an answer to "anything else to discuss" (Q6)?
- [ ] Test on a phone with real on-device speech-to-text, which may drop question marks and mis-hear Hindi.

### Raasta Saathi (devit-womens-safety): women's safety app

**Plan (28 Oct):** Basith has a Play organisation account and will buy Cloudflare later (free tier for now). When ready, Basith will hand over the accounts and Claude will do the uploads and console setup through computer use. Until then these stay here.

- [ ] **SMS provider account** for SOS dispatch: choose a provider, give the server its credentials, then run an authorized test with consenting recipients (delivery, no reply, refusal, backup escalation). The app code is ready; dispatch only works once a provider is set up.
- [ ] **Deploy the SOS service and trip-link service** (Cloudflare / chosen host) with their own secrets (encryption, capability, operator, webhook keys). Do not regenerate keys against the existing pilot.
- [ ] **Play Store:** create the Play Console app, upload signing key, store listing, Data safety, privacy policy URL, content rating, then the internal-testing release.
- [ ] **Open-source release:** decide the public licence review: the AI-FIR UI ports (yours to grant), Zip4j's LGPL provenance, and keeping the research folder and X-thread paraphrases private. Then run the public source export into a new public repo.
- [ ] **Crowdfunding:** pick a platform, set the goal from real costs (SMS, hosting, Play fee), write the campaign, record the videos.
- [ ] **Real phones:** camera/mic, Bluetooth handoff between two phones, battery/Doze, TalkBack, screen-off SOS, dual SIM.

### Raasta Saathi: Play Store and open source (from the release lane; details in android/store/release-checklist.md)
- [ ] Play developer account (US$25) and app record; turn on Play App Signing. You have an organisation account, so the 12-tester closed-test rule for new personal accounts does not apply.
- [ ] Create the upload keystore outside the repo (keep backups); build with the four `SAATHI_UPLOAD_*` environment variables. Next version: 10 / 0.10.0.
- [ ] SMS: a funded MSG91 account, DLT registration and approved templates, the webhook secret, a test with consenting recipients, then turn on `DISPATCH_ENABLED` and `MSG91_TEMPLATES_APPROVED`. Until then the app uses the phone's own SMS and call.
- [ ] Deploy the SOS service on Cloudflare (free tier is fine to start; you will buy a plan later) and set `SOS_API_BASE_URL` in the Play build. Deploy the trip/report server, or don't advertise trip links.
- [ ] Privacy policy: fill the `[OWNER: …]` fields in `android/store/privacy-policy.html` (legal name, address, support email, retention, providers), host it, and give us the URL for the in-app link.
- [ ] Play Console forms: Data safety, content rating, target audience (18+ recommended), foreground-service declaration with a demo video, health question (medical card).
- [ ] Capture the store screenshots from the merged build (list in `android/store/screenshots.md`); make the feature graphic.
- [ ] A native speaker reviews the Hindi listing and policy.
- [ ] Licensing: a written grant for the AI-FIR UI ports (you own AI-FIR; it has no LICENSE file); choose a Zip4j LGPL option; trademark search for "Raasta Saathi"; decide on a DCO / Code of Conduct.
- [ ] Publish a FRESH public repo from `node scripts/public-source.mjs export`. Never make this repo public.
- [ ] Crowdfunding: choose the platform; re-check prices in your own accounts; film only the real build with fictional contacts; never call 112 on camera; get written releases from everyone on camera.
- [ ] Recipient web pages: open an alert link and a trip link on real Android Chrome and iOS Safari (light, dark, large text, Hindi), and check the Call 112 button opens the dialler. Should Call 112 show on alert pages? It is new there.
- [ ] Deploy the SOS service and set `SOS_API_BASE_URL` in the Play build. Deploy the trip/report server, or don't advertise trip links.
- [ ] Phone check: the Android 12+ splash, first launch on a Hindi phone, the welcome card at large font, masthead/dock in light/dark/high contrast, TalkBack on the headings.
- [ ] Decide: add Vosk (offline speech that works on every phone; the library adds about 13 MB to the app, and Hindi/English models download on Wi-Fi, 38–45 MB each)? Today speech uses the phone's own recognizer, offline where the phone supports it.
- [ ] Phone checks for speech: Hindi and English offline packs present and missing, airplane mode, whether discreet mode really silences the beep, TalkBack reading live words, and a group SMS to several people in Google Messages and Samsung Messages.
- [ ] Phone checks for SOS reliability: an airplane-mode SOS with the screen locked (the 1-minute and 10-minute notices should appear and vibrate); reboot during a check-in and during a trip (each reminder fires once); Doze (`adb shell dumpsys deviceidle force-idle`).
- [ ] Decide: if storage gets corrupted, an alert the server already accepted can't be cancelled from that phone (rare).
- [ ] Phone checks for motion and sound: how the cues sound on the speaker and that they are silent in silent, vibrate and DND; haptic feel on several brands (especially Android 8–10); the glow, ring and flash in every theme and at large text; everything stops with the screen off and in Battery Saver; the SOS tap goes through instantly while the button glows.
- [ ] Confirm: evidence audio now SAVES the clip (it used to discard it) when you leave the screen, lock the phone or hit the 55-second cap.
- [ ] Phone checks for the private tools: hands-free evidence recording start/stop on lock and playback; sending several files to WhatsApp/Gmail; trim slider with TalkBack; screenshot blocking on safety plan / templates / checklist; the decoy journal looks identical; erase-journal flow; Hindi at large text.
- [ ] Play Console: declare the microphone foreground service (voice phrase trigger) and describe the volume-key media session.
- [ ] Phone checks for triggers: volume pattern and shake with the screen off (Pixel, Samsung, Xiaomi); voice phrase in Hindi/English and its battery use per hour; the SOS tile on a locked phone; the widget at 200% font; behaviour with notifications denied.
- [ ] SOS service deploy (exact steps in product/alerts/README.md "Owner steps"): `npx wrangler secret put` for OPERATOR_TOKEN, DATA_KEY, CAPABILITY_KEY; set PUBLIC_ORIGIN; `npm run check && npm run deploy`. SMS through MSG91 (MSG91_AUTHKEY, MSG91_WEBHOOK_TOKEN, DLT template IDs, then MSG91_TEMPLATES_APPROVED=true), or through SMSGate (an Android phone; SMS_PROVIDER=smsgate plus 3 secrets). Then run the authorized test script against your own number before setting DISPATCH_ENABLED=true for real users. Optional: SELF_ENROLL_ENABLED=true for public sign-up.
- [ ] Decide the Quick access tile default: "Start SOS at once" (current) or "Open Help only". In online mode one deliberate tap queues a real alert; cancel is one tap on Home.
- [ ] Phone checks for Home: the SMS app opening straight after the SOS tap, the tile on a locked phone, TalkBack on the new rows, 200% font, dark and high-contrast on the active SOS card.
- [ ] Decide: should "End ride" also cancel a running check-in timer? It does today. Should ride details be filled from a photo of the ride app? That needs ML Kit, a new dependency.
- [ ] Approve the change: a missed check-in will now message your chosen contacts (before, nobody was told). Choose the grace period (default 10 minutes). Set `ALERTS_BASE_URL` (https) and `ALERTS_SERVICE_TOKEN` (32+ characters, same value on the SOS service) on the trip server. Register a DLT template for "missed check-in". Use full-disk encryption on the trip server host.
- [ ] After hosting the privacy policy: put its https URL in `PRIVACY_POLICY_HOSTED_URL` (PrivacyPolicyUi.kt / build config). Once the public repo exists, give us its URL for the About page.
- [ ] Automatic SMS from the phone itself (no internet, no server): built but switched off in Play builds. Google Play only allows it after you file a special "SEND_SMS" declaration with a demo video, and approval takes weeks. Decide: file it, or ship automatic SMS only in a direct-download/F-Droid APK (build flag `SAATHI_CELLULAR_SMS=true`).
- [ ] Decide: if the phone is switched off above 15% battery (could be snatched), should it text your people? Today it only updates the trip link, to avoid a text every normal power-off.
- [ ] Telegram (free extra channel, optional): create a bot with @BotFather, then `wrangler secret put TELEGRAM_BOT_TOKEN` and `TELEGRAM_WEBHOOK_SECRET`, set TELEGRAM_BOT_USERNAME, and register the webhook (the curl command is in product/alerts/README.md §2c).
- [ ] Missed check-in: DLT template ID as `MSG91_CHECKIN_TEMPLATE_ID`; the same `ALERTS_SERVICE_TOKEN` on both servers.
- [ ] Phone checks for tools: siren loudness and that the volume is restored afterwards; torch strength; pretend-call ringtone in each ringer mode; dialler hand-offs (use a dummy number). The NCW helpline 7827170170 is not on the NCW pages we could read, so it was not added; confirm it if you know an official source.
- [ ] An Indian lawyer reviews the two new guides: "Harassment at work" (POSH timelines, committees) and "Photos or videos misused" (IT Rules, legal sections). Open shebox.wcd.gov.in on an Indian connection and confirm the link (it was unreachable from here).
- [ ] Optional: let the app read the SMS code automatically. It needs Google's `play-services-auth-api-phone` (a new dependency, no new permission). Without it, people tap the keyboard's code suggestion or paste the code.
- [ ] Play Console: fill in the SCHEDULE_EXACT_ALARM permission declaration (Codex at upload). Recheck Google's exact-alarm policy page before release.
- [ ] Phone test on a spare phone: Doze reminders, reboot re-arm, notifications denied banner (see CLOUD_HANDOFF "UI round 28 Oct").
- [ ] Self-setup by phone number: to switch on, set SELF_ENROLL_ENABLED=true on the SOS server and add an SMS provider (MSG91 approved OTP template + MSG91_OTP_TEMPLATE_ID, or SMSGate). Then do one test with your own number.
- [ ] (Decision, default = no) Auto-read the OTP SMS needs Google Play services library; left out to keep the app fully open-source/F-Droid friendly. Keyboard suggestion + paste already works.
- [ ] Maps (OpenFreeMap, free): before launch, email/sponsor openfreemap.org (no service guarantee). App size grows ~5 MB download per phone.
- [ ] Before going public: one licence read of MapLibre bundled notices (RapidJSON "Good not Evil" clause) and OSM ODbL share-alike (script is included).
- [ ] Re-run scripts/osm-help-places/extract.py before each release (police/hospital list refresh).
- [ ] Buy 2–3 NTAG213 NFC stickers (~Rs 30 each) to test the "tap sticker for SOS" feature on a Pixel and a Samsung.
- [ ] A native Hindi speaker reads values-hi/phone_powers.xml once.
- [ ] Grievance officer (Indian IT Rules): pick a name + email + phone; put it in android/store/contact-details.md and the privacy policy. Not published anywhere yet.
- [ ] Privacy policy [OWNER] blanks: effective date, hosting providers, log retention, SMS provider, response time, target age, public repo URL.
- [ ] Confirm support email workwithdevit@gmail.com and phone +91 95533 21211 go public on Play listing.
- [ ] Missed check-in texts (built, opt-in per trip): set `ALERTS_BASE_URL` + `ALERTS_SERVICE_TOKEN` on the trip server and the same `ALERTS_SERVICE_TOKEN` + `MSG91_CHECKIN_TEMPLATE_ID` on the SOS service; then one test with your own number. Until then the app says "nobody is alerted automatically".
- [ ] Decide: may the devit. logo (company trademark) be in the public open-source repo? It is not covered by the code licence.
- [ ] Phone check for disguise: long-press the Calculator icon on Pixel/Samsung/Xiaomi (no SOS shortcuts should show); widget and tile words after switching.
