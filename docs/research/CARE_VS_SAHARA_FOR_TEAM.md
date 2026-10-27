# CARE vs Sahara: plain words for the team (27 Oct 2026)

Short version for the team. The detailed version is [`CARE_FAMILY_SIDE_FIT.md`](CARE_FAMILY_SIDE_FIT.md).

## First, the words

- **CARE**: a free hospital record system for doctors and nurses.
- **OHC**: the Kerala group that builds CARE.
- **Staff side**: screens doctors and nurses use.
- **Family side**: screens the patient's family uses. That is us.
- **FHIR**: a common health-data format. CARE copies its ideas. It does not speak FHIR directly.
- **Consent**: a record that someone said yes.
- **Verification**: a second person checks that yes.
- **Questionnaire**: a form with questions.
- **Plugin**: an add-on that fits into CARE.
- **Encounter**: one visit or hospital stay.
- **Server**: a computer on the internet that stores data. Sahara's is Supabase (project "Saanthvana").
- **Sync**: the phone and the server keep the same copy.
- **Anonymise**: remove or hide who the patient is (name, phone, face) before data leaves the phone.

## Where facts come from

- CARE server code: `ohcnetwork/care` at commit `2bae933`. Written as `care:path:line`.
- CARE website code: `ohcnetwork/care_fe` at commit `81e7515`. Written as `care_fe:path:line`.
- CARE website pages, saved 23 Oct: `output/research/care-website-20261023/text/`. Written as `OHC page`.
- CARE docs, read 27 Oct: docs.ohc.network.
- Our app: `android/app/src/main/java/in/wedevit/care/` at `7abf52e`. Written as `File.kt:line`.

We read code. We did not run CARE. Anything about how CARE behaves when running is **[unverified]**.

**The one big thing.** CARE is for staff. A patient can log in with a phone code (OTP). That login can only register a patient and book or cancel visits (`care:care/emr/api/otp_viewsets/`). A family member cannot add symptoms, medicines or notes. We are the family side, and CARE has no family side.

---

## Table 1: Everything WE have. Does CARE have it?

| Our thing | What it is | CARE has it? | CARE source | Our source (code + what we based it on) |
|---|---|---|---|---|
| Add a person | Family adds the sick person by name. | Partly. CARE adds a patient, but staff do it, with much more data. | `care:care/emr/models/patient.py:16-52` | `CareData.kt:11` |
| Family account sign-in | Family signs in with email. | Partly. CARE has a phone code login for registration and booking only. | `care:care/emr/api/otp_viewsets/login.py:122-166` | `CareAccount.kt`; Supabase (`CareCloud.kt`) |
| Invite family by WhatsApp | Send a link, relative joins. | No. | none found in `care:` | `CareInviteLink.kt:7,71`; `CareAddFamily.kt:22-31` |
| Who can edit vs read | Each relative can edit or only read. | No (for family). CARE gives roles to staff only. | `care:care/security/permissions/encounter.py:24-29` | `CareAccount.kt:251` |
| Care contacts list | Names, relation, role, phone. | Partly. CARE has one emergency phone, and a care team of staff accounts. | `care:care/emr/models/patient.py:24`; `care:care/emr/resources/encounter/spec.py:133-145` | `DoctorCareData.kt:7` |
| Today screen | Today's medicines, visits, tasks. | Partly. CARE has staff visit lists. | OHC page `solutions/palliative-care` line 52 | `CareUi.kt:317` |
| Medicines list | Medicine name, dose, how often, past or current. | Yes. CARE's "medicine statement" even allows "told by a family member". | `care:care/emr/resources/medication/statement/spec.py:14-50` | `MedicineHistoryData.kt:6`; `MedicineOrganizer.kt:723` |
| Medicine taken log | Family ticks "taken" or "missed". | No (for family). CARE logs nurse-given doses only. | `care:config/api_router.py:476` (administration) | `MedicineHistoryData.kt:7-9` |
| Prescription copy | Family keeps the doctor's list, with history. | Partly. In CARE, doctors write prescriptions. | `care:care/emr/resources/medication/request/spec.py` | `DoctorPrescriptionData.kt:8-30` |
| Medicine scan / OCR | Photo of prescription becomes text, on the phone. | No. | none found | `MedicineScanner.kt`; `CareDocumentReader.kt:21`; `CareBilingualOcr.kt:13` |
| Medicine reminders | Phone alarm for each dose. | No. | none found | `CareReminders.kt:22` |
| Visits | Family notes the next doctor or home visit. | Yes. CARE has visits, including the "home health" type. | `care:care/emr/resources/encounter/constants.py:28-34` | `CareData.kt:40` (kind "visit"); `CareSchedule.kt:61` |
| Tasks | Family to-do list. | Partly. The OHC page says "Tasks". We found no task record in the code. **[unverified]** | OHC page line 10 | `CareDailyUi.kt:32` |
| Journal | Free-text notes. | Partly. CARE has staff note threads. | `care:care/emr/models/notes.py:6-18` | `CareData.kt:40` (kind "journal") |
| Daily check-in | "Good, okay or hard day" plus a note. | Partly. CARE can build such a form, but has none built in. | `care:care/emr/resources/questionnaire/spec.py:37-54` | `FeedbackUi.kt:49`. Based on: Saanthvana "My comfort space" (`SAANTHVANA_IMPLEMENTATION_2026-09-21.md:24`) |
| Symptom diary | 17 symptoms, each scored 0 to 10, with time, who reported it, now / 24-hour average / 24-hour worst. | Partly. CARE has symptom records (mild, moderate, severe) and custom forms. No fixed palliative scale in the code. | `care:care/emr/resources/condition/spec.py:42-84` | `CareSymptomData.kt:10-37`. Based on: `docs/research/symptom-tracker-clinical-brief-2026-10-23.md`. **We picked a personal 0–10 diary, not ESAS-r or IPOS (two official symptom scales), because the brief says don't claim a scale without clinician choice and permission. CARE uses mild / moderate / severe on symptoms, or whatever form the hospital builds.** |
| 3D pain body | Tap where it hurts on a 3D body. 12 parts, each with its own 0–10. | Partly. CARE can attach one body part code to an answer. No body picture. | `care:care/emr/resources/observation/spec.py:84`; `care:care/emr/resources/medication/valueset/body_site.py:5-17` | `CarePainBodyData.kt:11,47-52`; `CarePainBodyMap.kt`. Based on: symptom brief, step 4 (pain location) |
| Symptom trend / compare | "4 now, 6 yesterday". | No (built in). Staff can see past answers. | `care:care/emr/api/viewsets/observation.py:46` | `CareSymptomTrend.kt`; `CareSymptomData.kt:22`. Based on: symptom brief, "Baseline, yesterday and change" |
| Lab results | Family copies test values by hand. | Yes. CARE has lab reports and results (staff entered). | `care:config/api_router.py:442` (diagnostic report) | `CareLabs.kt:34`. Based on: `SAANTHVANA_IMPLEMENTATION_2026-09-21.md:28` |
| Documents / reports | Keep report files on the phone, read them. | Yes, for files. CARE stores files on its server. | `care:care/emr/models/file_upload.py:13-31` | `CareData.kt:41-42`; `CareDocuments.kt` |
| Goals-of-care questions | Doctor's list of questions: what matters, who decides, worries, next steps. | No. No goals-of-care form in CARE core. The docs say "do it separately" and mark it "coming soon". | [patient-enrollment](https://docs.ohc.network/deployments/palliative/playbooks/patient-enrollment.md) | `DoctorPreparationQuestions.kt:29-34`. Based on: `GOCD (1).pdf` (audited in `GOCD_PDF_AUDIT.md`), `PRODUCT_REQUIREMENTS.md` ("Guided conversation"), `ECTPR_SOURCE_AUDIT.md` Appendix A. **The exact wording of the 8 default questions has no cited source in the repo. [unverified]** |
| Record the talk (with consent) | Each person in the room says yes before recording. | No. CARE consent is for the patient, not for each person in a recording. | `care:care/emr/resources/consent/spec.py:39-46` | `CareData.kt:43-50`; `DoctorWorkflowData.kt:13-16`. Based on: `PRODUCT_REQUIREMENTS.md` "Source capture" |
| Transcript + answers from it | Words from the talk; answers linked to the exact sentence. | No. CARE plans voice notes ("AI drafts, humans decide"). | OHC page lines 109-119 | `DoctorTranscriptGoals.kt`; `DoctorConversationData.kt:37-40`. **Today the transcript is pre-written and the answers come from word matching** (`CLAUDE_TRANSCRIPT.md` section 4) |
| Doctor-reviewed note, with versions | Note v1, v2…, each locked with a fingerprint (hash). | No. CARE notes are free text with edit history. | `care:care/emr/models/notes.py:15-18` | `CareData.kt:23-37`. Based on: `GOCD_PDF_AUDIT.md` ("version-controlled"), `PRODUCT_REQUIREMENTS.md` "Versioning" |
| Family confirms the exact note | Relative says "I read version 3", "I'm worried" or "not ready". | No. The closest CARE thing is staff marking a **consent** "checked with family". | `care:care/emr/api/viewsets/consent.py:48-71` | `CareData.kt:38-39`; `DoctorWorkflowData.kt:5,12`. Based on: `GOCD_PDF_AUDIT.md` step 4 ("patient or surrogate authorizes"), `PRODUCT_REQUIREMENTS.md` "Verification and acknowledgement" |
| Ask for a goals talk | Family asks the doctor for a conversation. | No. | none found | `DoctorWorkflowData.kt:6` |
| Doctor walkthrough / doctor screens | The doctor sees the family's records on the phone. | Yes, CARE is all staff screens. | care_fe overall | `DoctorPatientHubUi.kt`; `DoctorOverviewUi.kt` |
| Find a palliative centre | 1,631 public listings, map, call. | Partly. CARE lists facilities in its own system ("nearby facilities"). | `care_fe:src/Routers/PatientRouter.tsx:26` | `CareCentreDirectory.kt:5-24`; `android/app/src/main/assets/india-directory-sources.json` |
| Hospital survey (web) | Doctors tell us which hospitals offer palliative care. | No. | none found | `prototype/app/feedback/HospitalSurvey.tsx`. Based on: Sujay's 23 Oct notes (`source-materials/team-context/2026-10-23/sujay-survey-symptom-followup.txt`) |
| Hindi / English | Labels in both languages, offline translation. | Partly. CARE has translation files. **[unverified]** which languages are complete. | `care:crowdin.yml` | `CareTranslationRules.kt`; `CareOfflineTranslation.kt:16` |
| Works without internet, phone keeps a copy | Without an account, everything stays on the phone. Signed in, records are saved on Sahara's server so family and the invited doctor can see them; the phone keeps a copy to read offline. Recordings and report files never leave the phone. Not offline-only. | No. CARE is a server system. | `care:care/emr/models/file_upload.py:33` (S3 storage) | `CareModel.kt:552` (signed-in save goes to the server); `CareModel.kt:568-574`, `CareReports.kt:18` (report files local); `CareModel.kt:709` (recordings local). See "Where Sahara's data lives" below |
| Advance directive (paper) | We explain it. We don't make one. | Partly. CARE stores "advance directive" and "DNR" consents (staff only). | `care:care/emr/resources/consent/spec.py:43-46` | Based on: `Vidhi_EOLC_Sample-Advance-Medical-Directive.pdf` and `Vidhi-EOLC-Toolkit-FAQ.pdf` (used for copies and plain explanations: `FEATURES_FROM_CONVERSATIONS_2026-09-21.md:26-27,53`). **We chose not to create a legal advance directive (`GOCD_PDF_AUDIT.md` "Critical scope defect"; `ECTPR_SOURCE_AUDIT.md` 2.2 "not a legally executed AMD").** |
| Emergency card / hospital QR | Card for the emergency doctor. | No. | none found | Placeholder only (`CLAUDE_TRANSCRIPT.md` section 4). Based on: `ECTPR_SOURCE_AUDIT.md` Appendix E, `GOCD_PDF_AUDIT.md` |
| Feedback form | App feedback. | No. | none found | `prototype/app/feedback/page.tsx` |

---

## Table 2: Everything CARE has (palliative / home care). Do WE have it?

| CARE thing | What it is | Sahara has it? | CARE source | Our source |
|---|---|---|---|---|
| Patient registry | Big list of patients, with address, area, risk. | Partly. We have only the person's name. | OHC page line 50; `care:care/emr/models/patient.py:16-52` | `CareData.kt:11` |
| Patient details | Gender, birth date, phone, address, pincode, blood group, death date, hospital ID. | No. We don't ask any of these. | `care:care/emr/models/patient.py:17-50` | — |
| Caregiver details | Docs say record the caregiver's name, phone and relation. | Yes. We do this better: many relatives, each with access. | [patient-enrollment](https://docs.ohc.network/deployments/palliative/playbooks/patient-enrollment.md) | `DoctorCareData.kt:7`; `CareAddFamily.kt:22` |
| Patient phone-code login | Patient logs in with a phone code. | Partly. We use email accounts instead. | `care:care/emr/api/otp_viewsets/login.py:122-166` | `CareAccount.kt` |
| Patient self-registration + booking | Patient registers and books a slot. | No booking. | `care:care/emr/api/otp_viewsets/slot.py:37-104`; `care_fe:src/Routers/PatientRouter.tsx:45-82` | — |
| Staff given access to a patient | Hospital adds a field worker to a patient. | Partly. We add family members, not staff accounts. | `care:care/emr/models/patient.py:148-155` | `CareInviteLink.kt:71` |
| Home visit | A visit record of type "home health", with status and dates. | Partly. We have a simple visit reminder, done or not done. | `care:care/emr/resources/encounter/constants.py:4-34` | `CareData.kt:40` |
| Home visit form | Vitals, symptoms, nursing needs, medicine needs, caregiver worries, next visit. | Partly. We have symptoms, medicines and notes. No vitals. | OHC page line 52 | `CareSymptomData.kt`; `MedicineHistoryData.kt` |
| Vitals | Blood pressure, pulse and so on, with standard codes (LOINC: lab-test code list). | No. | `care:care/emr/models/observation.py:6-52` | — |
| Care team | Assign nurses, doctors, volunteers. | Partly. A list of names, not accounts. | `care:care/emr/api/viewsets/encounter.py:421-425`; volunteer role `care:care/security/roles/role.py:22` | `DoctorCareData.kt:7`; `DoctorWorkflowData.kt:11` |
| Care plan | Problems, goals, tasks, review dates. | Partly. Our goals-of-care note has "next steps". **CARE code has no care plan record. Only the web page says it.** | OHC page line 63-64; no `CarePlan` in `care:care/emr/models/` | `CareData.kt:23-37` |
| Consent types | Research, privacy, treatment, DNR (do not resuscitate), comfort care, advance care directive, advance directive refusal. | Partly. We only have consent to record and transcribe. | `care:care/emr/resources/consent/spec.py:39-46` | `CareData.kt:43-50` |
| DNR / advance directive record | Staff record "yes" or "no", with a scanned form. | No, by choice. We explain and keep copies, we don't record orders. | `care:care/emr/resources/consent/spec.py:43-46` | `GOCD_PDF_AUDIT.md` "Critical scope defect" |
| Consent verification: Family / Validation | Staff mark a consent checked with family ("family") or with the patient ("validation"). | Partly. Our family confirms a **note** themselves. CARE has **no screen** for this; it exists only on the server. | `care:care/emr/resources/consent/spec.py:29-31,50-55`; `care:care/emr/api/viewsets/consent.py:55`; [consent docs](https://docs.ohc.network/concepts/clinical/consent/) | `CareData.kt:38-39` |
| Comfort care consent | A category in the code. | No. CARE's own form also lacks it. | `care:care/emr/resources/consent/spec.py:44`; missing in `care_fe:src/types/consent/consent.ts:4-11` | — |
| Questionnaires (forms) | The hospital builds its own forms, with any question type. | Partly. Ours are fixed in code (symptom diary, goals questions). | `care:care/emr/resources/questionnaire/spec.py:37-54,115-152` | `CareSymptomData.kt`; `DoctorPreparationQuestions.kt` |
| Symptoms | Coded symptom, mild / moderate / severe, start date. | Yes, our own way (0–10 diary). See Table 1. | `care:care/emr/resources/condition/spec.py:42-84` | `CareSymptomData.kt:10-20` |
| Diagnoses, allergies | Coded disease list, allergies. | No. | `care:config/api_router.py:435-440` | — |
| Medicines: prescription | Doctor orders medicine. | Partly. We keep a copy. | `care:care/emr/resources/medication/request/spec.py` | `DoctorPrescriptionData.kt` |
| Medicines: what the patient takes | List, with "told by family" as the source. | Yes. | `care:care/emr/resources/medication/statement/spec.py:25-28` | `MedicineHistoryData.kt:6` |
| Medicines: dose given by nurse | Nurse records each dose given. | Partly. Our family logs doses. | `care:config/api_router.py:476` | `MedicineHistoryData.kt:7` |
| Files | Upload scans, audio, discharge summaries. | Yes, but they stay on the phone. | `care:care/emr/resources/file_upload/spec.py:14-28` | `CareData.kt:41-42` |
| Notes / discussions | Staff chat threads about a patient. | Partly. Journal plus doctor note. | `care:care/emr/models/notes.py:6-18` | `CareData.kt:40` |
| Referrals | Send the patient to a doctor or hospital. | No. | OHC page line 61-62; `care:care/emr/models/resource_request.py`, `service_request.py` | — |
| Dashboards | Counts: patients, overdue visits, coverage. | No. | OHC page line 57-58 | — |
| Volunteer / NGO coordination | Volunteers, training status, assignments. | No. | OHC page line 59-60 | — |
| Appointments / scheduling | Slots, booking, queue tokens. | No. | `care:care/emr/models/scheduling/` | — |
| Facilities, locations, beds | Hospital setup. | No (only a centre directory). | `care:care/facility/models/facility.py` | `CareCentreDirectory.kt` |
| Roles and permissions | Doctor, nurse, volunteer, admin. | Partly. Owner, can-edit, can-read, and doctor accounts. | `care:care/security/roles/role.py:22-73` | `CareAccount.kt:251` |
| Audit trail | Who changed what. | Partly. Note versions, hashes, dose reporter. | OHC page line 15; `care:care/audit_log/` | `CareData.kt:23-39` |
| AI voice notes (planned) | "AI drafts, humans decide". | Partly. Our recording works. AI is off. | OHC page lines 109-119 | `CLAUDE_TRANSCRIPT.md` section 4 |
| Plugins | Add-ons that extend CARE. | No. A plugin would be our way in later. | `care:docs/development/pluggable-apps.md:6,47-48` | — |
| FHIR-style records | Data shaped like the common health format, with SNOMED / LOINC codes (medical code lists). | No. We use our own simple fields and no codes. | `care:care/emr/resources/medication/valueset/medication.py:5-26` | `CareData.kt` |

---

## Same thing, different form or scale

- **Pain and symptoms.** We picked a personal 0–10 diary with "now / 24-hour average / 24-hour worst", because the symptom brief says pick an official scale only with clinicians and permission (`docs/research/symptom-tracker-clinical-brief-2026-10-23.md`, "The decision to make"). CARE uses mild / moderate / severe on symptoms, plus any form a hospital builds (`care:care/emr/resources/condition/spec.py:42-45`). Kerala's own visit form: **[unverified]**.
- **Goals of care.** We picked doctor-led questions and a versioned note, because of `GOCD (1).pdf`, the ECTPR (`ECTPR_SOURCE_AUDIT.md`) and `PRODUCT_REQUIREMENTS.md`. CARE has no goals-of-care form. Its docs say "do it separately" and mark enrolment "coming soon".
- **Family yes.** We picked "family confirms the exact note version", because `GOCD_PDF_AUDIT.md` step 4 says "patient or surrogate authorizes". CARE uses "staff mark a consent as checked with family" (`care:care/emr/api/viewsets/consent.py:55`).
- **Recording consent.** We picked a yes from each person in the room, because of `PRODUCT_REQUIREMENTS.md` "Source capture". CARE uses one consent per patient, per visit (`care:care/emr/models/consent.py:6-16`).
- **Advance directive.** We picked "explain it and keep copies, never create one", because of Vidhi's papers and `GOCD_PDF_AUDIT.md` "Critical scope defect". CARE uses a staff-recorded consent of type DNR or advance directive.
- **Sign-in.** We picked email accounts with family invites (`CareAccount.kt`). CARE uses a phone code for patients, which lasts 1 hour (`care:config/patient_otp_token.py:6-8`).

---

## Decisions we made that CARE didn't

- Family is the main user, not staff. (`FAMILY_CAREGIVER_DIRECTION.md`)
- Works without internet; recordings and report files stay on the phone. Shared records go to Sahara's server, not a hospital's. (See "Where Sahara's data lives")
- Each person in a recorded talk gives their own yes. (`CareData.kt:43-50`)
- The family confirms the exact note version, locked with a hash. (`CareData.kt:38-39`)
- No official scale is claimed for symptoms. (`CareSymptomData.kt:9`)
- No treatment advice and no DNR orders. (`GOCD_PDF_AUDIT.md` "Critical scope defect")
- Hindi labels in the app. (`CareSymptomData.kt:27-33`)
- A public palliative centre directory. (`CareCentreDirectory.kt:5`)

## Things CARE decided that we didn't

- Staff enter all clinical data. (`care:care/security/permissions/encounter.py:24-29`)
- Everything is coded with SNOMED or LOINC. (`care:care/emr/resources/observation/valueset.py:5-14`)
- Every record hangs on a visit (encounter). (`care:care/emr/models/consent.py:11-13`)
- Hospitals build their own forms. (`care:care/emr/resources/questionnaire/spec.py:115-152`)
- Data lives on a server the hospital runs. (OHC page lines 79-89)
- DNR and advance directive are consent types. (`care:care/emr/resources/consent/spec.py:43-46`)
- Patient login is a phone code, for booking only. (`care:care/emr/api/otp_viewsets/slot.py:37-104`)
- Add-ons come in as plugins. (`care:docs/development/pluggable-apps.md:6`)

---

## Where Sahara's data lives (checked in code, 27 Oct)

**Owner correction (27 Oct):** Sahara is **not** an offline-only app. Some data goes to Sahara's server so the family and the doctor can see it.

In plain words: **Sahara works without internet. Without an account, everything stays on the phone. When you sign in, your care records are saved on Sahara's server (Supabase) so the family members and the doctor you invite can see them. The phone keeps a copy.**

| What | Where | When | Source |
|---|---|---|---|
| Person's name, medicines, visits, tasks, lab values, symptom and diary entries | Server table `healthathon_records` (+ `healthathon_people` for the name) | Each save, when signed in | `CareCloud.kt:70-92`; `CareModel.kt:282,552` |
| Published care notes and family confirmations | Server, `healthathon_records` (kind `note`, `ack`) | When published / confirmed | `CareCloud.kt:82-91`; `CareModel.kt:464` |
| Conversation draft: the typed or dictated words and the reviewed sections | Server, `healthathon_care_drafts` | Only when you press save or send to the doctor | `CareModel.kt:426-449`; `CareSharedCloud.kt:87` |
| Medicine taken / missed log, past or current | Server, `healthathon_medicine_courses`, `healthathon_medicine_events` | Each tick, when signed in | `MedicineHistoryRepo.kt:62,84` |
| Prescription lists | Server, `healthathon_prescriptions` and related tables | Each save, when signed in | `DoctorPrescriptionRepo.kt:55` |
| Care-team contacts (name, relation, phone) | Server, `healthathon_care_contacts` | Each save, when signed in | `DoctorCareData.kt:73` |
| Doctor's verification request (name, council, registration number) | Server, `healthathon_clinician_requests` | When a doctor applies | `CareSharedCloud.kt:69` |
| **Recordings (audio)** | **Phone only** | Never sent. Cloud transcription is switched off | `CareModel.kt:709`; `build.gradle.kts:37,46` |
| **Report files and photos, and their scanned text** | **Phone only** | Never sent, unless you copy a fact into an entry | `CareReports.kt:18`; `CareDocumentReader.kt:114` |
| Speech-to-text, scanning, translation | On the phone | Online AI is off | `CareRemoteAssist.kt:28-30`; `MedicineOrganizer.kt:431-436` |

**Who can read the server copy.** Only the owner, and people the owner invited (server rules: `android/supabase/migrations/20261009162933_android_care_accounts.sql:47-77`, `20261019151322_shared_care_clinician_review.sql:117-129`). Family get read or read-and-edit. A doctor sees only the parts the owner ticked (notes, calendar, reports, costs), for 1–365 days (`CareCloud.kt:94-98`), and only after Sahara staff approve the doctor's registration. Sahara's server admins can see everything.

**Offline, exactly.** Without an account, everything works with no internet. Signed in, the phone keeps a copy you can read offline, and reminders still ring. But saving a shared change needs internet today: there is no "send later" queue (`CareModel.kt:552`).

**Anonymising before the doctor sees it: PLANNED, NOT BUILT.** The owner's design is to blur or anonymise data before it is shared with the doctor. Today nothing does that: the server gets the real name and the words exactly as typed, and the code has no anonymise, redact or blur step. To build it: a pseudonymous patient ID instead of the name on what the doctor sees; strip names, phone numbers and addresses from the text before sending; blur faces and ID numbers in any photo before upload (no photos are uploaded today); and a DPDP review of what still counts as personal data.

The doctor test workspace is separate: fictional patients only (`DoctorConnectedStore.kt:28`), and it uploads a test recording or file only after a tick box (`ConnectedDoctorUi.kt:330-340`).

---

## What to say to judges in 3 lines

1. CARE is the doctors' and nurses' record for palliative home care. Sahara is the family's phone app.
2. We use CARE's record types (patient, home visit, consent checked with family, forms, medicines told by family), so a hospital on CARE can take our export. Nothing is connected live yet.
3. We add what CARE leaves out: family accounts, a 3D pain diary, and the family confirming the exact goals-of-care note themselves.
