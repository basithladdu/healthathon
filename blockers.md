# Blockers: only Basith, Awaiz or the team can do these

Agents cannot do these: server migrations, deployments, provider accounts and keys, decisions, Play Console, and tests on a real phone. Everything else is being built. Linked from [everyday.md](everyday.md) and [HANDOFF.md](HANDOFF.md). Tick items as they are done.

**Who:** Basith = owner; Awaiz = Sahara app; team = the clinicians (Sujay, Sharada, Shirin).

Collected on 27 Oct from every agent lane across Sahara, ITI, WDCW and AI-FIR. Everything else in those lanes is merged to each repo's main. Tick an item when done.

### Sahara (healthathon)
- [ ] Apply the Supabase migrations the lanes add (emergency QR link, account delete, feedback, sync), then re-test.
- [ ] Redeploy the website with the app-link file, so invite links open the app.
- [ ] Supabase settings: email sign-up confirmation, leaked-password protection, lock `rls_auto_enable()`.
- [ ] Approve doctor accounts (manual in the database for now).
- [ ] Pick the AI provider for note drafting, and the consent terms.
- [ ] Decide the anonymisation scope: patient name → ID on the doctor's screens; blur faces in photos.
- [ ] Re-tapping a marked body part: move the spot or add a new one?
- [ ] Pick the CARE option in the team meeting (A / A+B / A+C).
- [ ] Send the OHC email and the private security disclosures (drafts in docs/outreach/).
- [ ] DPDP: name the data fiduciary (company or new entity); approve the privacy policy and consent text (drafts in docs/compliance/).
- [ ] Play: next upload uses versionCode 9 or higher, once 0.6.0 (7) is out of review; fill in Data safety from the draft.
- [ ] Test on a real phone: checklist in CLAUDE_TRANSCRIPT.md §3.
- [ ] Final template: team code, doctor details, costs; decide how judges see the code (repo is private).

### ITI (devit-iti)
- [ ] SMS/WhatsApp provider plus `ITI_AUTO_PARENT_ALERTS`, to turn on automatic absence messages to parents.
- [ ] Decide whether the send log should appear outside the archived Messages screen.

### WDCW (devit-wdcw / -android)
- [ ] Deploy the current web main before testing Android evidence recovery.
- [ ] Play signing key and Console setup (`WDCW_UPLOAD_*`).

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
- [ ] **SMS provider account** for SOS dispatch: choose a provider, give the server its credentials, then run an authorized test with consenting recipients (delivery, no reply, refusal, backup escalation). The app code is ready; dispatch only works once a provider is set up.
- [ ] **Deploy the SOS service and trip-link service** (Cloudflare / chosen host) with their own secrets (encryption, capability, operator, webhook keys). Do not regenerate keys against the existing pilot.
- [ ] **Play Store:** create the Play Console app, upload signing key, store listing, Data safety, privacy policy URL, content rating, then the internal-testing release.
- [ ] **Open-source release:** decide the public licence review: the AI-FIR UI ports (yours to grant), Zip4j's LGPL provenance, and keeping the research folder and X-thread paraphrases private. Then run the public source export into a new public repo.
- [ ] **Crowdfunding:** pick a platform, set the goal from real costs (SMS, hosting, Play fee), write the campaign, record the videos.
- [ ] **Real phones:** camera/mic, Bluetooth handoff between two phones, battery/Doze, TalkBack, screen-off SOS, dual SIM.
