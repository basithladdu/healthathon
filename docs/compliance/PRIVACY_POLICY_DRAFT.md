# Sahara privacy policy: plain-words draft (DPDP Act 2023)

_Draft, 27 Oct 2026, compliance lane. Not published. The live policy is at `https://sahara.wedevit.in/privacy` (`prototype/app/privacy/page.tsx`) and stays unchanged until the owner approves this draft._

Based on code on `origin/main` (commit `1304b6d`) and the audit [`docs/research/handoff-2026-10-27/sahara-data-flow-audit.md`](../research/handoff-2026-10-27/sahara-data-flow-audit.md). Section numbers for the DPDP Act come from [`docs/research/handoff-2026-10-27/landscape/india-palliative-policy-law.md`](../research/handoff-2026-10-27/landscape/india-palliative-policy-law.md) and should be checked against the official Act text before publishing **[verify]**. This is not legal advice. Have a lawyer review it before real patients use the app.

## Open questions (answer these before publishing)

1. **Data fiduciary.** The live policy names WEDEVIT PRIVATE LIMITED. `HANDOFF.md` (G5, G6) says the owner still has to choose between the company and a new entity. **[owner to confirm: company or new entity]**
2. **Server region.** No config file in the repo states it. `AI_HANDOFF.md:30` records the Saanthvana project as `ap-south-1` (Mumbai). The Play 0.6.0 build still uses an older project whose region is unknown (`AI_HANDOFF.md:31`). **[owner to confirm in the Supabase dashboard, for the project the shipped build uses]**
3. **Grievance contact.** The live policy and the deletion page use a general support email. DPDP asks for a named contact who answers questions. **[owner to confirm: name or role, email, response time]**
4. **Consent from the patient.** Family members enter the patient's data. The law-research note says consent must come from the patient or a lawful guardian (s.9 for a person with a lawful guardian), and suggests asking the patient to name a nominee (s.14) while they still have capacity. The app does not record patient consent today. Only invitees get a `consented_at` time when they join (`20261019151322_shared_care_clinician_review.sql:92`). **[owner to decide]**
5. **Retention period.** The code has no automatic expiry for care records. Say "until you delete" or set a period? **[owner to decide]**
6. **Sahara staff access.** The audit says Sahara's server admins can see everything, and staff roles exist (`20261019151322_...sql:7`). Who has admin access, and is it logged? **[owner to confirm]**
7. **Website host** for `sahara.wedevit.in` (the join, shared-care and deletion pages). **[owner to confirm]**
8. **Effective date** for DPDP duties: the research note says 13 May 2027, possibly earlier **[verify]**. The policy should be ready before any pilot either way.
9. The four in-progress changes (send-later queue, redaction, emergency server link, in-app account deletion) are marked **in progress, verify before submitting** below. Remove or keep each paragraph to match what actually ships.

---

## The policy (draft text)

### Who we are
Sahara is a palliative-care companion app for patients and families. **[owner to confirm: company or new entity]** decides why and how your data is used, so under India's Digital Personal Data Protection Act 2023 we are the "data fiduciary".

Grievance and privacy contact: **[owner to confirm: name, email]**.

### Two ways to use Sahara
- **Without an account.** Everything you enter stays on this phone. Nothing goes to our server. _(Source: `CareModel.kt:22, 98`, `cloud == null`.)_
- **With an account.** You sign in with an email and password. What you enter is saved on our server so you can share it with family and a doctor. _(Source: `CareCloud.kt:45-56`.)_

### What we collect, and why

| What | Why | When it reaches our server |
|---|---|---|
| Your email and password | To sign you in. We keep an encrypted sign-in token on the phone, not your password. | When you create an account or sign in (`CareCloud.kt:45-56`) |
| The name of the person being cared for | To show and share their records | When you add them (`CareCloud.kt:70-71`) |
| Medicines, visits, tasks, lab values, symptom and diary entries | To organise daily care and share it with people you invite | Each time you save (`CareModel.kt:552`; `CareCloud.kt:74-76`) |
| Goals-of-care notes, including the doctor's and participants' names | To keep the agreed note and its versions | When a note is saved (`CareCloud.kt:81`) |
| Family confirmations (name, relationship, time) | To show who confirmed which version | When someone confirms (`CareCloud.kt:82-91`) |
| Conversation drafts (the words and the sections) | So the doctor you invite can review them | Only when you choose to save or send them to the doctor (`CareModel.kt:375-391, 426-449`) |
| Medicine courses, taken/missed marks and prescription groups | To track medicines | Each change (`MedicineHistoryRepo.kt:62, 84`; `DoctorPrescriptionRepo.kt:55`) |
| Care contacts (name, relation, role, phone) | So the care team knows whom to call | When you save a contact (`DoctorCareData.kt:73`) |
| A doctor's name, medical council and registration number | So Sahara staff can check that the doctor is real before giving access | When a doctor applies (`CareSharedCloud.kt:69`) |
| Invitations (who, what they can see, for how long) | To control sharing | When you invite someone (`CareCloud.kt:93-102`) |

**Technical data.** Our server provider logs your IP address and request details, from which a city or country can be guessed. Google's ML Kit (used on the phone to read printed text and translate) sends Google technical metrics and an installation ID. It does not send your photos, words or care text. The map sends your IP address and the area you view to OpenFreeMap. Downloading the optional Hindi language pack or speech model sends your IP address to Google or Hugging Face. _(Sources: `build.gradle.kts:81-83`; `CentreMap.kt:75`; `CareOfflineTranslation.kt:16-24`; `OfflineSpeechRules.kt:17`.)_

We do not use advertising, we do not sell your data, and we do not use it to profile you. _(No advertising or analytics SDK in `build.gradle.kts:65-94`.)_

### What stays only on your phone
- **Recordings** of conversations. They are never uploaded. Speech-to-text runs on the phone. _(Sources: `CareModel.kt:625, 710`; cloud transcription is off, `build.gradle.kts:37, 46`.)_
- **Report files and photos**, and the text read from them. They are never uploaded unless you copy a fact into an entry yourself. _(Sources: `CareReports.kt:18`; `CareModel.kt:568-574`; `CareDocumentReader.kt:114`.)_
- **Unsent drafts**, reminders and in-app feedback. _(Sources: `FeedbackStore.kt:7`; audit "What stays only on the phone".)_
- Online AI is switched off in the app. _(Source: `CareRemoteAssist.kt:28-30`.)_

The phone also keeps a copy of your server records so you can read them offline. App data is not included in Android cloud backup or transfer to a new phone. _(Sources: `CareModel.kt:196-205`; `res/xml/data_extraction_rules.xml`.)_

### Where it is stored
On Supabase servers in **[owner to confirm: region; `AI_HANDOFF.md` records ap-south-1, Mumbai]**. Supabase stores the data on our behalf (a "data processor"). All connections use HTTPS. _(Source: `AndroidManifest.xml:15`, `usesCleartextTraffic="false"`.)_

### Who can see it
- **You**, as the owner of a person's records.
- **Family members you invite.** You choose read-only or read-and-change, and how long access lasts. An invitation code works once and expires in 3 days. _(Sources: `CareCloud.kt:93-102`; `CareAccount.kt:240-246`.)_
- **A doctor you invite.** The doctor must first be approved by Sahara staff, and then needs your invitation. You choose what they see (care notes, calendar, reports, costs, care team), whether they can make changes, and for how many days (1, 7, 30 or 90). A doctor sees a conversation draft only after you send it. _(Sources: `CareAccount.kt:240-246`; `20261019151322_shared_care_clinician_review.sql:117-155, 123-129, 222-224`.)_
- **Sahara's server administrators** can technically see all stored data. **[owner to confirm who, and what limits apply]**
- **Anyone you share with yourself**, through Android's share button or the printed emergency QR card. Those copies leave Sahara. The offline emergency card is plain text that anyone who scans it can read. _(Source: `EmergencyCard.kt:12-15`.)_

When you remove a member, they lose access from then on. We cannot recall copies they already saved.

### Your rights and how to use them
- **Withdraw consent.** Sign out to stop sending new data. Remove a family member or doctor in Your account. Withdrawing does not undo past processing. _(DPDP s.6 [verify])_
- **See and correct.** You can see and edit your records in the app, or ask us for a summary. _(s.11, s.12 [verify])_
- **Delete one person's records.** More → Remove this person's records. This deletes them on the server and on the phone. _(Source: `CareCloud.kt:73`.)_
- **Delete your account.** Today: Your account → Delete account opens our deletion page, and you send us an email from your registered address. We check that it is your account, then delete your account details, the records you own, their invitations, and your access to other people's records. Records owned by someone else stay with them. _(Sources: `CareAccount.kt:263`; `prototype/app/delete-account/page.tsx`; `20261010122352_sahara_account_closure.sql`.)_
- **Name a nominee** to use these rights if you die or cannot act. **[owner to decide how; DPDP s.14 — see open question 4]**
- **Complain.** Contact our grievance contact first: **[owner to confirm]**. If you are not satisfied, you can complain to the Data Protection Board of India. _(s.13 [verify])_

Uninstalling the app deletes the phone copy only. It does not delete your online account.

### How long we keep it
Until you delete it, delete the person's records, or close your account **[owner to decide a fixed period, open question 5]**. If the law requires us to keep something, we will tell you what, why and for how long.

### Security
- The sign-in token is encrypted with Android Keystore. _(Source: `CareCloud.kt` `seal`.)_
- Server rules let only the owner and invited members read records. _(Source: `20261009162933_android_care_accounts.sql:47-77`.)_
- The app's local database has no separate encryption, so please use a screen lock. _(Source: live policy, "How information is protected".)_
- If there is a data breach, we will tell you and the Data Protection Board as the law requires. **[owner to set up the process]**

### Not medical advice
Sahara records and organises care. It does not diagnose or prescribe. A goals-of-care note records a conversation; it is not a treatment order or a legal directive.

### Changes in progress (in progress, verify before submitting)
Keep a paragraph only if that change has shipped.
- **Send later.** _[in progress, verify before submitting; `sa4-queue`]_ If you have no internet, your entry is kept on the phone and sent when the connection returns. _(Today a signed-in save without internet fails: `CareModel.kt:552`.)_
- **Removing contact details before upload.** _[in progress, verify before submitting; branch `sa4-privacy`, `086e591`]_ Before symptom notes and conversation drafts are sent, phone numbers, emails, Aadhaar-like numbers, PAN and PIN-code addresses are replaced with labels such as [phone]. Names are not removed. This is not full anonymisation.
- **Emergency web link.** _[in progress, verify before submitting; branch `sa3-qr`, `b2b2456`; server change not applied]_ If you turn it on, a copy of the emergency card (name, latest confirmed goals, emergency contacts, medicines) is kept on our server. Anyone with the link or QR can read it without signing in, for up to one year or until you turn it off or make a new one. We count how many times it is opened.
- **Delete account in the app.** _[in progress, verify before submitting; `sa4-account`; server change not applied]_ You can delete your account and data directly in Your account, without emailing us.

---

## Differences from the existing privacy texts

Compared with the live website policy (`prototype/app/privacy/page.tsx`, "Updated 27 October 2026") and the older Android copy (`android/release/privacy.html`, "Updated 16 October 2026", versions 0.4 and 0.5). No privacy text was found elsewhere in `prototype/` apart from the deletion page.

| Topic | Existing texts | This draft |
|---|---|---|
| Length and style | Website: long, detailed and technical, covering the app, the website, feedback and hospital surveys | Short, plain words, app only. Website forms and surveys are left to the website policy. |
| Data fiduciary | Names WEDEVIT PRIVATE LIMITED as "provider"; never uses the term "data fiduciary" | Names the fiduciary role; the entity is **[owner to confirm]** |
| DPDP rights | Not mentioned: no withdrawal of consent, nominee, Data Protection Board or grievance officer | Adds all four (marked [verify] / [owner to confirm]) |
| Grievance contact | Support email only | Placeholder for a named contact |
| Server region | Says "Supabase" with no region | Region placeholder; `AI_HANDOFF.md` records ap-south-1 |
| Server admins | Website: "Restricted service administration" only for feedback and surveys; nothing about care records | Says plainly that server admins can see stored care data (from the audit) |
| Care contacts with phone numbers | Not mentioned in either text | Listed (`DoctorCareData.kt:73`) |
| Medicine courses and prescription groups | Website: "prescribed-medicine details"; Android 16 Oct copy: not mentioned | Listed |
| Doctor permissions and access length | Website: described; Android 16 Oct copy: only "read or edit" invites | Described, with the 1/7/30/90-day choice from code |
| In-app "Delete account" | Website says "use Your account → Delete account in the app", which reads as if the app deletes it. The button only opens the web page (`CareAccount.kt:263`). | Says the button opens the page and you send an email |
| Offline emergency QR card | Not mentioned | Says anyone who scans it can read it |
| Saving without internet | Website does not say that a signed-in save fails offline | States today's behaviour; the queue paragraph is in progress |
| Redaction, emergency web link, in-app deletion | Not mentioned (not built) | Marked in progress |
| Android 16 Oct copy | Older than the website: no symptom diary, ML Kit, offline speech model, reminders or clinician review | Covers what is on main now. The Android copy should be replaced or removed **[owner to confirm which URL Play links; `strings.xml:4` points to the website]** |
