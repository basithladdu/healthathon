# What changed in Sahara: a clinician's view (7 Aug to 23 Nov 2026)

For Dr Sujay, Dr Sharada, Dr Shirin and the jury. This lists the changes a doctor, a patient or a family member would actually notice in the app. Refactors, tests, build fixes and internal plumbing are left out.

The app was first called Saanthvana (and, in the earliest prototype, Continuity Loop). It has been **Sahara** since 10 October. It is the same project throughout.

Where a commit message or a team document says who asked for a change, the source is noted in brackets. Commit counts are per calendar week (Monday to Sunday) across all branches, 634 in total, including merges.

---

## The 15 biggest changes clinicians will notice

1. **9 Oct**: Sahara becomes a real Android app, not only a website. It was on the Play Store for closed testing on 14 Oct and in production on 19 Oct.
2. **30 Oct**: Emergency care plan (ECTPR) wizard, an ED Quick View for the emergency doctor, and a pocket emergency card with a QR code that works offline. Built from SVK's clinical specification.
3. **1 Nov**: The emergency care plan now covers more kinds of decision-maker: live-in and queer partners, a secondary decision-maker, people who could not be reached, and both surrogates signing. It also explains capacity. (Legal review by Rituparna, 31 Oct)
4. **4 Nov**: A goals-of-care talk is recorded and turned into the clinical document, word for word in the legal team's layout. The doctor confirms each choice, both sides sign by drawing on the doctor's phone, and the version then locks. (3 Nov meeting with Dr Sujay)
5. **7 Nov**: Dr Sujay's 13-item goals-of-care checklist, in his order, with a "Guide me" or "Free flow" choice while recording. (Dr Sujay's 7 Nov feedback)
6. **22 Nov**: Goals of care has one place to record and sign. Everywhere else shows a read-only history of versions. Patients see the signed note and can ask their doctor for the conversation. (Dr Sujay's video review)
7. **22 Nov**: A surprise-question prompt for doctors. It appears when a patient has no goals-of-care note, and again about 90 days after the last one. (22 Nov team meeting)
8. **28 Oct**: The daily check-in asks about distress, using the NCCN Distress Thermometer (0 to 10) and problem list. Doctors see the score history. (28 Oct team meeting, decision 7)
9. **24 to 26 Oct**: A 3D body map for marking where it hurts, with a strength for each spot and a dated pain history.
10. **28 Oct**: Pain scores show their band (none, mild, moderate, severe, using NCCN cut-offs). Each body part has its own trend over time. Sadness and worry are now two separate scales, low mood and anxiety. (28 Oct team meeting)
11. **4 Nov and 22 Nov**: Every dose time gets its own tick. As-needed (SOS) doses can be logged many times a day and the doctor sees how many were taken. Reminders behave like alarms and stay until the patient taps Taken or Noted. (Dr Sujay; Dr Shirin)
12. **27 to 28 Oct**: "Nearby" lists 1,631 palliative care centres across India, searchable by name, place, state or PIN, with emergency numbers. Centres whose listing mentions morphine are shown as a separate map layer.
13. **27 Oct and 30 Oct**: The whole app in Hindi, then Telugu, Urdu (right to left) and Kannada.
14. **7 Nov**: Caregiver wellbeing for family members: a short burden check (ZBI-4) tracked over time, Tele-MANAS 14416, respite and support-group options, and a private journal. (Dr Sujay's 7 Nov feedback)
15. **23 Nov**: Care plans for patients under 18, where the parent or guardian decides and the child's wishes are asked. Optional first-run questions cover the illness, the current doctor and the current treatment. (Dr Shirin's feedback)

---

## Week by week

### 3 Aug to 13 Sep: starting point (4 commits)
- The organisers' material was saved on 7 Aug. The first web prototype followed on 8 Sep.
- **13 Sep**: A patient portal, a "care near me" map using real places, and the first emergency treatment preference view (ED Quick View: goal of care, hospital transfer, CPR, breathing support, ICU, and "if in doubt, resuscitate"). Also a wallet emergency card with an offline QR code. Dr Sujay's "Healthathon form" outline was mapped into the app.

### 14 to 20 Sep: early clinical prototype (30 commits)
- Doctors could keep a reviewed summary across later revisions of a note. Familiar labels replaced technical ones for notes and profiles.
- An emergency card scanner that works without signal, plus Hindi on the patient side.
- A treatment escalation plan grid, and dictation for clinicians.
- Many clinician reference tools were tried out in the prototype: symptom scoring (ESAS-r), PPS prognosis staging, deprescribing checks, caregiver burden (ZBI-12) and oncology emergency guides. The 23 Sep meeting later decided to keep clinical scoring and advice off the patient and family screens.

### 21 to 27 Sep: shaped by the two September meetings (33 commits)
- **21 Sep**: Separate family pages: symptom diary, voice journal, lab history, calendar, support places and one palliative care map with directions. Sign-in by role (patient, family, doctor). (21 Sep meeting)
- Care conversations, signed notes and a family handover pack were made easier. The doctor signs first, then the patient or family reviews that exact version, and a PDF of the note can be saved. (21 Sep meeting)
- Cancer documents were kept apart from monitoring results, and searching documents was added. (21 Sep meeting)
- **23 to 24 Sep**: A patient or family member can request a goals-of-care conversation, and the doctor sees the request in a queue. The previous signed discussion is carried into the next one. (23 Sep meeting)
- Getting oral morphine became a contact-and-enquiry path, not an unverified list. (23 Sep meeting)
- The pitch was revised after Dr Sujay's review (25 Sep) and Dr Sharada's review (27 Sep).

### 28 Sep to 11 Oct: shortlisted, and the Android app (16 commits)
- **2 Oct**: A Karnataka care directory, and hospital reports that can be shared.
- **9 Oct**: Shortlisted. The same day the native Android care companion was started, with a map of care centres and shared care between family and doctor.
- **10 Oct**: Renamed **Sahara**. A medicine organiser that reads a prescription photo was added, along with account recovery and safe account closure.
- **11 Oct**: The prescription organiser came to Android, together with documents and reports.

### 12 to 18 Oct: Play Store testing and version 0.5 (26 commits)
- **14 Oct**: Version 0.4.1 went out to closed testing on the Play Store with invited testers.
- **16 Oct**: Version 0.5 added care tasks, conversations linked to the lines they came from, lab results, a visit schedule and speech input.
- Care editors open full screen, so typing with the keyboard works properly.

### 19 to 25 Oct: production launch, symptoms and the pain map (45 commits)
- **19 Oct**: Sahara went live on the Play Store. App feedback forms and a hospital survey were added. The doctor's conversation history became easier to read.
- **20 Oct**: A national care directory, plus speech-to-text and translation in Hindi that run on the phone.
- **22 Oct**: A six-day health workflow view that patients and family can read but not edit.
- **24 Oct**: A symptom diary (version 0.6). A rotatable 3D body for marking pain locations, a dated pain history, and symptom trends.
- The doctor walkthrough was redesigned, and a patient and family walkthrough was added.

### 26 Oct to 1 Nov: the biggest week, with the 28 Oct meeting, SVK's spec and the legal review (274 commits)
- **26 Oct**: The pain body shows a heat-map glow, with a strength for each spot and a Wong-Baker style scale. A family member can be invited by a WhatsApp link.
- **27 Oct**: Hindi on every family screen and on the doctor workspace. The emergency card gets a web-link QR, printing and a password-locked PDF. Phone numbers, email, Aadhaar, PAN and addresses are removed from notes before they are uploaded.
- **27 Oct**: The doctor workspace shows real data: today's visits and check-ins, a schedule, search by patient ID, and joining a patient by code. The goals-of-care recording gives a transcript on the phone and a live "covered / still open" checklist. Tapping an answer jumps to the line it came from.
- **27 Oct**: Documents can be filtered to cancer documents (PET-CT, biopsy, staging). Lab values can be read from scanned reports, and chemotherapy and radiotherapy days are marked on the charts.
- **28 Oct**: The changes agreed at the team meeting that day:
  - the NCCN Distress Thermometer in the daily check-in (decision 7);
  - pain bands, a pain trend for each body part, and a Mood tile with separate low-mood and anxiety scales;
  - a prescription photo fills in only the medicine's name, strength and form, and the dose, timing and directions are typed by hand (decision 8);
  - strip photos appear on dose reminders;
  - documents are sorted into folders, and "Ask your documents" answers by citing the lines it used (decision 9);
  - a drawn signature for goals-of-care notes, plus an optional surprise question and ECOG helper (decisions 1 and 2);
  - Nearby shows palliative centres and centres listing morphine as separate map layers, and anyone can suggest a missing centre.
- **28 Oct**: The Sahara team can now approve doctors from inside the app.
- **30 Oct**: From SVK's Saanthvana clinical specification: the emergency care plan wizard, the ED Quick View, the pocket card QR, an advance directive based on the Vidhi text, read-aloud, a 30-day reconsideration period, and Telugu, Urdu and Kannada with a language menu in every header.
- **31 Oct to 1 Nov**: A patient summary, side-effect notes and a care timeline. The emergency care plan was updated with the legal team's comments, covering gender, doctors outside oncology, live-in and queer partners, a secondary decision-maker, capacity guidance, a medical boards step, and both surrogates signing. (Legal review, Rituparna, 31 Oct)

### 2 to 8 Nov: goals of care finished, and Dr Sujay's 7 Nov review (187 commits)
- **4 Nov**: After the 3 Nov meeting with Dr Sujay:
  - the goals-of-care checklist follows the doctors' order (consent, patient, family and emergency contact, CPR, dialysis, then the rest);
  - at the end, the app lists the topics not yet discussed;
  - when one person changes their mind, their latest statement wins; when two people disagree, both are shown;
  - the note comes out in the legal form's layout, word for word, both sides sign on the doctor's phone, and any later edit cancels the signatures.
- **4 Nov**: Patient and family views now differ. Family sees "Caring for <name>" and their own distress check. Each dose time is its own tick. A new "Tubes and lines" section records insertion and change-due dates. Nearby opens on the phone's location. The doctor's patient view shows symptoms first, most severe on top. (3 Nov meeting and team WhatsApp)
- **5 Nov**: The goals-of-care recording turns into text when the doctor taps Stop, and the clinical document is filled in from it.
- **6 Nov**: A real community with groups, posts, replies, reports and moderation. The patient app has four tabs: Today, Care, Help and More. A dose marked Taken can be undone. The doctor's patient page lists its sections with Goals of care first. Forms are pre-filled with what the app already knows.
- **7 Nov**: From Dr Sujay's 7 Nov feedback:
  - calm sage colours, with red only for SOS and danger;
  - "legal form" became "clinical document", and "living will" became "advance care planning";
  - the distress thermometer is back on Today;
  - caregiver wellbeing (ZBI-4, Tele-MANAS, respite care, journal);
  - his 13-item goals-of-care checklist, with Guide me or Free flow.
- **7 Nov**: From the night review by Dr Sujay and Dr Shirin:
  - several symptoms can be logged in one go;
  - medicines are grouped by morning, afternoon, evening and night;
  - after signing, the doctor can start a new version of the plan;
  - the withdrawal and withholding (Common Cause) section was removed from the web form;
  - the full diagnosis shows on the profile, with a weight chart.
- **7 Nov**: Medicine safety fixes. Changing a dose time stops the old reminder. A night dose taken after midnight asks which night it was for. Weekly and monthly medicines no longer get a daily tick. The emergency card prints recorded allergies and shows OUT OF DATE when it has expired. The SOS page dials 112, 108 and 14567 with one tap.
- **8 Nov**: Version 0.7.0 went to the Play Store, and the website caught up with Android.

### 9 to 15 Nov: release 0.7.1 (8 commits)
- **9 Nov**: Version 0.7.1 on the Play Store. The app and the website no longer name any outside technology company. The "stopping or not starting treatment" step was removed from the care plan.
- **10 Nov**: The four-and-a-half-minute Sahara journey film for the jury.

### 16 to 22 Nov: Dr Sujay's photos and videos, and Dr Shirin's notes (4 commits)
- **21 Nov**: Medicine names no longer break mid-word (for example "Dexameth / asone"). The Next visit dialog no longer cuts off fields. (Dr Sujay's photos, 21 Nov)
- **22 Nov**, checkpoint before the video review:
  - as-needed (SOS) doses can be logged many times, with a count for the doctor;
  - reminders behave like alarms until the patient taps Taken or Noted;
  - softer colours and the film's faces on Today;
  - the emergency care plan is read-only for patients and family;
  - the capacity step is explained;
  - several key medicines can be recorded, each with its own dates;
  - the guardian's phone comes first on the QR card;
  - offline cards carry a valid-until date, and the web link can be turned off;
  - two consent ticks, and a role choice at sign-up. (Dr Shirin's and Dr Sujay's 21–22 Nov notes)
- **22 Nov**: A practical guide for family only, reached from "Not breathing or not waking up?" in SOS. Patients never see it. When a patient's morphine is marked taken, the app asks once a month where the family got it. No account is kept of the answer, and it helps other families. (22 Nov team meeting discussion)
- **22 Nov**, after Dr Sujay's video review:
  - one goals-of-care entry, with a read-only history of versions everywhere else;
  - patients can ask their doctor for the conversation;
  - Guide me stays pinned next to the transcript, and Done can always be reached;
  - one "Who's present" card;
  - a fuller add-patient form for doctors, a list of unsigned notes, compact side effects and symptoms, and a month view in the schedule;
  - the surprise-question prompt (22 Nov team meeting).

### 23 Nov: Dr Shirin's details and version 0.8.0 (7 commits)
- Patients and family no longer sign goals-of-care notes. They see a read receipt only. (Dr Sujay's video, frame-by-frame check)
- The Visits screen says how visits get there. "Record another conversation (not goals of care)" is kept separate. Centres that list morphine and also give palliative care appear under both map chips. The calendar shows visits only, not medicines. (Dr Sujay's video)
- The after-death guide was trimmed to practical steps only. (22 Nov team meeting)
- From Dr Shirin's feedback:
  - care plans for under-18s, where the parent or guardian decides and the child's wishes are asked;
  - a date-of-birth picker that jumps to a year or takes a typed date;
  - one line explaining sex versus gender;
  - optional first-run questions on the illness, the current doctor and the current treatment, which the doctor can see.
- Softer look throughout: an animated opening, step-by-step onboarding and a floating bottom bar.
- Version 0.8.0 (build 12) was prepared for the Play Store.

---

## Still open (from the 22 Nov meeting and the review notes)
- The surprise-question algorithm is to be written down by the clinicians. The current 90-day prompt is a placeholder until then.
- How the app should handle the death of the patient, beyond the family-only practical guide.
- Test the new build on real phones with at least five real goals-of-care conversations. Logins for the testers are being opened.
- Invite links expire after 72 hours. Dr Sharada's link stopped working after two days, and the team still has to decide the right length.
- Whether people can create their own community groups. The four starting groups still need the clinicians' confirmation.
