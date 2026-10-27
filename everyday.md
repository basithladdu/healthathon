# Sahara: everyday work

Our aim is to build a useful palliative-care companion and make the judges understand exactly what we are trying to do. We want to win through a working product, clear evidence and a presentation we can explain confidently.

## Final package we are working towards — organiser email, 27 October

The latest [email and two original templates](source-materials/healthathon-phase-2/2026-10-27-final-deliverables/README.md) are saved. Our end goal is **all four deliverables**, not only the app or the slides:

- [ ] **Working MVP:** verify the complete evaluator journey and provide working links/access instructions. Share any test credentials privately at submission, not in this repo.
- [ ] **Two-page overview:** fill the new two-page template with the team/doctor, problem, solution, 3–5 features, technology, progress, validation, safety, sustainability and supporting links.
- [ ] **Final presentation:** adapt our useful v6 visuals to the new 12-slide source template and fill every placeholder. The solution and evaluation sections each permit up to two slides. Use current evidence; keep incomplete features and proposed CARE integration clear.
- [ ] **Narrated demo video:** introduce the team, problem, development and doctor/technology collaboration, then show the working solution and saved result. A silent screen recording or screenshots alone do not meet the email's instruction.
- [ ] **Final submission check:** test all MVP/video/code-access links as an evaluator, confirm the submission destination and exact cut-off, submit the four-part package and retain the receipt.

The archive and requirements are saved; the filled two-page overview, adaptation to the new final template, final video and submission remain pending. The latest email gives no deadline, time zone, portal or video duration. **7 November remains the existing planning target; confirm its source and cut-off before submission.**

## Blocked — only Basith (or the team) can do these

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
- [ ] A doctor with read-only access can't plan visits, and the greyed-out button gives no reason. Add an explanation, or allow it?

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
- [ ] Do the doctor walkthrough demo and the doctor recording panels need Hindi before 7 Nov? They are still English.
- [ ] Sahara accessibility: on a real phone, try TalkBack on the pain body (3D and fallback), Today, the care list and the emergency card; check the app at the largest font and display size.
- [ ] Sahara map: approximate locations come from GeoNames (CC BY 4.0, credited under the map) because data.gov.in couldn't be reached. Accept that, or download the Government PIN directory yourself so the script can be rerun on it?
- [ ] Sahara map: on a phone, check the cluster numbers show and the hollow (approximate) pins are easy to tell from the solid (exact) ones.
- [ ] Directory data: remove 3 junk header rows (2 Assam, 1 Maharashtra); one Vasai centre is listed under Mumbai district.
- [ ] Goals matcher (after the fix): 110 of 130 answers found, 1 wrong, on our own 20 test conversations. That is optimistic, since the rules were written after reading some of them. Don't quote 85% as real-world accuracy; wording is in the report.
- [ ] A clinician or native speaker reviews the Hindi/Hinglish question wording and the "put-off" list in `GoalsQuestionVocabulary.kt`.
- [ ] Should a patient's own question count as an answer to "anything else to discuss" (Q6)?
- [ ] Test on a phone with real on-device speech-to-text, which may drop question marks and mis-hear Hindi.

## Work on this every day

- **Slides and two-page brief:** finish or improve one section each day using the latest 27 October templates linked above. Reuse useful material from our earlier deck. Fill what we know now; add team photos, biographies and answers needing discussion later. The answer to sharing solution information on the Health-a-thon website is **Yes**. Private records and credentials are excluded.
- **Survey:** improve and test the hospital survey, review responses and turn useful findings into product decisions. Keep it on the web at `/hospital-survery`.
- **Features:** choose a meaningful patient, caregiver or doctor task, finish the flow and test its actual result. Keep Hindi/English access and fewer taps in mind. Record what works and what still needs verification.
- **Palliative-doctor feedback:** identify suitable doctors, prepare a short walkthrough and specific questions, arrange outreach, then record their feedback and the changes it leads to. Send messages only when that outreach is authorised.
- **Play Store:** get the current verified Android release onto Google Play. Check the actual release/review status, finish the remaining device and listing checks, and distinguish an uploaded build from an available release.
- **CARE:** understand its palliative-care workflows and test whether a focused Sahara plugin or API connection would improve our product and our Healthathon entry. Compare what already exists with what we can add. Do not claim an integration before it works.
- **Mail CARE / OHC (team decision, 27 Oct):** we will email OHC (Open Healthcare Network, who make CARE) at support@ohc.network and post in their Slack (slack.ohc.network). Named contacts: Bodhish Thomas (CEO), Vignesh Hari (CTO). Ask for: a pilot with one CARE palliative unit for a family-side add-on (family entries wait in a queue; a nurse accepts each), permission to reuse their starter kit/docs (no licence file), and their view on loading our 3 forms as CARE Questionnaires. Send only after the team agrees the wording. Draft:
  > Hello OHC team, we are Team Sahara (Health-a-thon 2026). Sahara is a phone app for palliative patients' families: symptom diary, medicines, and the family confirming the exact goals-of-care note a doctor reviewed. CARE is the staff side and, as your docs note, has no family side. Could we pilot a small family-inbox plugin with one CARE palliative unit, where family entries wait for a nurse to accept them? We have drafted our symptom diary, pain areas and goals-of-care questions as CARE Questionnaires and would value your review. Sources and details: [repo link]. Thank you.
- **Video:** prepare and record a short demonstration of the actual patient/family/doctor journey. Show the problem, the important actions and the saved result. Keep the video consistent with the build and slides.

## What is genuinely left (27 Oct → 7 Nov)

Full list with owners: `HANDOFF.md` → "27 Oct 2026 — open items". The short version:

**Must do before submission (planning target: 7 Nov; exact cut-off to confirm)**
1. Decide CARE option (A / A+B / A+C) in the team meeting; write the deployment/integration answer (slide 10 in the unexpanded new template). Brief: `docs/presentations/Sahara_CARE_Team_Briefing_2026-10-27.pdf`.
2. Fill both the new final deck and the two-page overview: team code, doctor partner details, costs, 2–3 measures of success with a baseline.
3. Record the narrated demo video (conversation → reviewed note → family confirms).
4. Test the app on a real phone (checklist: `CLAUDE_TRANSCRIPT.md` §3). Nothing from 26–27 Oct has been tried on a device.
5. Get clinician sign-off (Sujay, Shirin, Sharada): goals-of-care note format, symptom list and scales, and the source of the 8 default goals-of-care questions.
6. Agree and send the OHC email (draft above). Mention the pilot sites and the ccm_uhi privacy issue privately.

**Decide (owner)**
- Anonymising before data reaches the doctor: scope (outside reviewers / AI only, or the treating doctor too).
- Real transcription/AI provider and consent terms, or keep "word matching" and say so.
- Emergency QR: offline card only, or a server link later.
- Hindi across the whole app; re-tap behaviour on the pain body.
- How judges see the code (repo is private).

**Set up (owner)**
- Real doctor account + Supabase email setting; Supabase security warnings.
- Website redeploy with the app-link fingerprint so invite links open the app.
- Next Play upload with a higher versionCode (don't touch the build in review).
- DPDP: consent notice naming the server and doctor, withdraw/delete, named data fiduciary.

**Team / clinicians**
- Send the hospital survey to oncology and palliative groups; name a pilot centre and doctor contact.
- 20 practice conversations (asked twice, none done).
- Support-group, palliative-doctor and morphine-centre lists (Sujay).

## Main thing: how we present it

Make the idea understandable to someone hearing it for the first time—as simply as explaining it to a five-year-old—while being precise enough to show we know the clinical problem and our product.

1. Who is struggling, and what happens to them today?
2. What does Sahara help them do?
3. Show one clear journey, from the conversation to the reviewed record and its next use.
4. Explain what AI does, what the clinician decides and what the patient/family confirms.
5. Explain how we differ from existing tools, including CARE, without pretending their features do not exist.
6. Show what we have built and tested, what feedback changed, and what comes next.

Rehearse aloud. Remove jargon and sentences we cannot explain naturally. Practise likely judge questions, transitions between teammates and recovery if the live demo fails. The slides, video and spoken explanation should tell the same story.

**Visual priority after comparing the decks:** keep the patient story, large app screens, care map and evidence visuals when adapting to the latest 27 October template. The earlier v6 draft follows the older 13-section source and is a useful visual reference, not the completed new final deck. The user rejected the report-like first revision; preserve v6's large product scenes and concise copy. Keep team photos/write-ups for the agreed later discussion.

## End each work session

- Record the slide worked on and the concrete survey/feature progress.
- Save evidence of meaningful checks and feedback; do not invent validation or outcomes.
- Note the next action and its owner once agreed with the team.
- Update this file and the current handoff without scattering decisions across new notes.

| Date | Slide | Survey / feature work | Doctor feedback | CARE / Play / video | Next action |
| --- | --- | --- | --- | --- | --- |
| 27 Oct 2026, final-deliverables email | Both original templates archived; 2-page brief and new 12-slide source read and checked | No app or survey changes in this archive task | Evidence and collaboration must appear in both documents and video | Four-part final package added above; no submission, release or external message made | Complete the two-page brief, adapt v6, record narrated video and verify evaluator access |
| 23 Oct 2026 | Official template downloaded; first 13-slide draft filled, including the team table and Yes to public solution information | Review current evidence before choosing the next task | Outreach and feedback to arrange | CARE website reviewed; integration not built. No new Play release submitted in this session | Review the draft with the team, fill the remaining details and rehearse the opening explanation |
| 23 Oct 2026, follow-up | Product-led v6 revision across13 sections, rendered and checked | Survey changes and manual hospital entry live on Vercel; one actual390px submission/receipt verified and exact synthetic data removed.299 unit checks pass. Personal diary keeps separate ratings, respondent/time frame and real comparisons | Exact WhatsApp feedback saved; S S Sparsh entry received and pending review; clinical symptom report prepared for team discussion | No Android/Play change, patient onboarding or messages sent | Review the visual draft, choose clinical questionnaire with Shirin/Sujay, then rehearse one care-note journey |
| 27 Oct 2026 | Team briefing deck (9 slides, CARE vs Sahara) | Screen fixes running (doctor conversation, emergency QR, Nearby, Docs/Lab, Hindi, strength bar) | — | CARE docs (all 473 pages) and OHC repos read; mail OHC agreed (draft above, not sent) | Agree the OHC email wording, then send it; decide CARE option A/A+B/A+C in the meeting |

Current slide draft and remaining questions: [`submission/phase-2/README.md`](submission/phase-2/README.md).
