# Can Sahara be CARE's family side? (27 Oct 2026)

Answers the owner's question in `CLAUDE_TRANSCRIPT.md` (line 403): *"if CARE is staff side for palliative then can we do family side exactly how they did it so that we can fit in the puzzle and make it whole with our additional features"*.

No app code was changed for this. Sources were read, not run.

**Sources and versions**
- CARE backend: `ohcnetwork/care` at `2bae933` (2026-10-19). Cited as `care@2bae933:path:line`.
- CARE frontend: `ohcnetwork/care_fe` at `81e7515` (2026-10-26). Cited as `care_fe@81e7515:path:line`.
- Sahara Android: this repo at `7abf52e`. Paths are under `android/app/src/main/java/in/wedevit/care/` and cited as `sahara@7abf52e:File.kt:line`.
- CARE docs: docs.ohc.network, read live on 27 Oct. The 23 Oct archive is in `output/research/care-website-20261023/text/`.
- **[unverified]** marks anything we did not run against a real CARE server. That covers everything about runtime behaviour.

---

## 1. Short answer

**Strongest counterargument first.** CARE has no family side for Sahara to slot into, and no door for one. A CARE patient login is a one-hour token tied to a phone number. That token can only do four things: list or register patients with that same phone number, book or cancel public appointments, and list geographic organisations. It cannot read or write consents, symptoms, questionnaires, medicines, notes or files (section 3.1).
- Consent verification records the *signed-in staff user* as `verified_by` (section 3.2). A family member can't be that user unless the hospital gives them a staff account.
- CARE at `2bae933` has **no CarePlan resource** and **no related-person/caregiver resource**, even though the website describes care plans and caregiver registries (section 3.3).

So "exactly how they did it" can't mean "plug into CARE's family API". That API doesn't exist.

**What it can honestly mean:** Sahara uses CARE's own words and record shapes:
- Patient;
- an Encounter of class `hh` (home health);
- Consent, with `verification_type: family | validation`;
- QuestionnaireResponse with LOINC-coded observations;
- MedicationStatement with `information_source: related_person`;
- FileUpload.

It exports a person's records as CARE-shaped JSON a hospital's CARE team could import. Later, a CARE *backend plugin* could receive family submissions into a staff review queue. A plugin can add its own API under `/api/<plugin>/` with its own sign-in (section 3.4). No change to CARE core is needed for that.

**Yes, as a shared data model and export now, and as a plugin-backed review queue later. No, as a live connection before 7 Nov.**

**Confidence:** high on what the code contains, moderate on the plan, low on effort numbers beyond P1 (no CARE server was run).

---

## 2. What "family side exactly like CARE" can concretely mean

| Level | What it is | When |
|---|---|---|
| **1. Same words and shapes** | Sahara's export (and, where cheap, its labels) uses CARE's resource names, enums and field names: `Consent.category` (`dnr`, `acd`, `adr`, `treatment`…), `verification_type` (`family`/`validation`), `Encounter.encounter_class = hh`, `MedicationStatement.information_source = related_person`, Condition `severity` (`mild`/`moderate`/`severe`). | P1 |
| **2. Export file** | One JSON file per person that mirrors CARE's REST request bodies, plus the reviewed goals-of-care note as a PDF attachment. A staff user (or a script using a staff or service account) re-enters or imports it into CARE. Nothing is sent automatically. | P1 |
| **3. Live, with staff review** | A CARE backend plugin (Django app) gives Sahara its own endpoint. Family submissions land as *pending* items. A CARE doctor or nurse accepts each one. Only then does it become a CARE Consent, QuestionnaireResponse or MedicationStatement, written by that staff user under CARE's normal permissions. | P2 |
| **4. Pilot** | One palliative unit already running CARE, with synthetic data first, then real patients under that unit's consent process. | P3 |

Level 3 is how the gap between the two systems is closed honestly. CARE keeps its rule that a clinician writes clinical data, and Sahara supplies what the family reported.

---

## 3. What CARE actually has (from code)

### 3.1 Patient / family-facing access

- **OTP login exists.** `POST /api/v1/otp/send/` and `POST /api/v1/otp/login/` take no authentication (`care@2bae933:care/emr/api/otp_viewsets/login.py:122-166`). They are routed at `care@2bae933:config/api_router.py:125`, under `/api/v1/` (`care@2bae933:config/urls.py:65`).
  - A correct OTP returns a `PatientToken` holding only `phone_number` (`login.py:164-166`).
  - The token lasts 1 hour and has type `patient_login` (`care@2bae933:config/patient_otp_token.py:6-8`). There is no refresh endpoint for it.
  - Outside production the OTP is always `45612` (`login.py:97-99`). The OTP length is 5 (`care@2bae933:config/settings/base.py:505`).
- **What the token can reach.** Only views that list `JWTTokenPatientAuthentication` accept it (`care@2bae933:config/patient_otp_authentication.py:64-81`). By default, views use staff JWT and basic auth (`config/settings/base.py:367-379`). A search for that class finds exactly three users:
  1. `otp/patient`: list and create patients **whose `phone_number` equals the token's phone** (`care@2bae933:care/emr/api/otp_viewsets/patient.py:13-25`; route `api_router.py:130`).
  2. `otp/slots`: public schedule slots, `get_slots_for_day`, `create_appointment` (only for a patient with the same phone), `cancel_appointment` and `get_appointments` (`care@2bae933:care/emr/api/otp_viewsets/slot.py:37-104`; route `api_router.py:132`).
  3. `organization` list only. Every other action is denied in OTP mode (`care@2bae933:care/emr/api/viewsets/organization.py:88-100`).
- **Frontend.** When the token is an OTP token, `care_fe` switches to a separate `PatientRouter` (`care_fe@81e7515:src/App.tsx:46-56`). It has these routes: nearby facilities, patient home, patient profile, and book/reschedule/select/register for appointments (`care_fe@81e7515:src/Routers/PatientRouter.tsx:25-82`). Its API calls are `/api/v1/otp/*` (`care_fe@81e7515:src/types/otp/otpApi.ts:13-32`, `src/types/emr/patient/publicPatientApi.ts:9-15`, `src/types/scheduling/PublicAppointmentApi.ts:11-33`).
- **Can a family member (not the patient) sign in?** Only by phone-number ownership. CARE has no caregiver identity in the token, just a phone number. A caregiver whose phone is the patient's registered `phone_number` can sign in and act for that patient, but only for registration and appointments. Nothing records that a family member, not the patient, signed in.
- **Patient users are staff.** `PatientUser` links an existing CARE **User** to a patient with a role (`care@2bae933:care/emr/models/patient.py:148-155`). Adding one needs patient-update permission (`care@2bae933:care/emr/api/viewsets/patient.py:302-317`). The docs say "The assigned user supports the patient in the field" and "has an account in Care" ([manage patient users](https://docs.ohc.network/flows/clinical/patient/manage-patient-users.md)). A family member could only be one if the facility made them a CARE user account. **[unverified]** We don't know whether any deployment does this.

### 3.2 Data shapes (internal REST, FHIR-aligned, not a FHIR server)

**Not real FHIR.** CARE exposes Django REST at `/api/v1/...` with its own JSON (pydantic specs). It has no FHIR server endpoint, no `/fhir/` route in `config/urls.py` or `config/api_router.py`, and no `application/fhir+json` anywhere in `care/`. The `care/emr/fhir/` package is an *outbound* client that queries a remote FHIR server, used for terminology (`care@2bae933:care/emr/fhir/client.py:3-25`). Codes follow FHIR and terminology standards:
- medications are SNOMED `<< 763158003` (`care@2bae933:care/emr/resources/medication/valueset/medication.py:5-26`);
- conditions and symptoms are SNOMED `404684003` (`care@2bae933:care/emr/resources/condition/valueset.py:5-15`);
- body sites are SNOMED `91723000` (`care@2bae933:care/emr/resources/medication/valueset/body_site.py:5-17`);
- observation codes are LOINC (`care@2bae933:care/emr/resources/observation/valueset.py:5-14`).

| Resource | Endpoint | Shape (key fields) | Who may write |
|---|---|---|---|
| **Patient** | `/api/v1/patient/` (`api_router.py:432`) | `name, gender, phone_number, emergency_phone_number, address, permanent_address, pincode, date_of_birth / year_of_birth, deceased_datetime, blood_group, geo_organization, instance_identifiers, extensions` (`care/emr/models/patient.py:16-52`) | Staff with patient permissions. The OTP route can also create one for its own phone (3.1). |
| **Encounter** | `/api/v1/encounter/` (`api_router.py:196`) | `status` (planned, in_progress, on_hold, discharged, completed, cancelled, discontinued, entered_in_error, unknown), `encounter_class` (imp, amb, obsenc, emer, vr, **hh**), `patient, facility, period, priority, care_team, extensions` (`care/emr/models/encounter.py:8-37`; `care/emr/resources/encounter/constants.py:4-34`) | Staff. Clinical data can't be written to a completed, cancelled, discontinued or entered-in-error encounter (`care/security/authorization/encounter.py:97-108`; `constants.py:17-22`). |
| **Care team** | `POST /api/v1/encounter/{id}/set_care_team_members/` (`care/emr/api/viewsets/encounter.py:421-425`) | `care_team` on the Encounter: a list of `{member: User, role}` (`care/emr/resources/encounter/spec.py:133-145`). Members are **CARE users**. | Staff with encounter update permission. |
| **Consent** | `/api/v1/patient/{id}/consent/` (`api_router.py:448`) | `status` (draft, active, inactive, not_done, entered_in_error), `category` (research, patient_privacy, treatment, dnr, comfort_care, acd, adr), `date, period, encounter` (**required**), `decision` (permit/deny), `note`, `verification_details[]` (`care/emr/models/consent.py:6-16`; `care/emr/resources/consent/spec.py:21-71`). Attachments are FileUploads with `file_type=consent` (`spec.py:109-116`). | Create, update and delete need `can_write_encounter_clinical_data` on that encounter (`care/emr/api/viewsets/encounter_authz_base.py:15-34`). Only **Admin, Doctor, Nurse, Facility Admin** have it (`care/security/permissions/encounter.py:24-29, 51-56`). |
| **Consent verification** | `POST .../consent/{id}/add_verification/` and `remove_verification/` (`care/emr/api/viewsets/consent.py:48-100`) | Body: `verified, verification_type (family / validation), note`, optional `verified_by` and `verification_date` (`spec.py:50-55`). The server **overwrites** `verified_by` with the signed-in user's id and the date with the current time (`consent.py:55-56`). Only one verification per user (`consent.py:58-62`). On read, `verified_by` is looked up as a CARE `User` (`spec.py:119-122`). | Same as Consent: the same four roles, and the encounter must be open. **"Family" means the type of verification a staff user records, not a family login.** |
| **Questionnaire** | `/api/v1/questionnaire/` (`api_router.py:140`) | `title, slug, version, subject_type` (patient / encounter), `status, questions[]` (`care/emr/models/questionnaire.py:10-18`). Question types: group, boolean, decimal, integer, string, text, display, date, dateTime, time, choice, url, quantity, structured (`care/emr/resources/questionnaire/spec.py:37-54`). A question can carry a LOINC `code`, `collect_body_site`, `answer_option`, `unit` (`spec.py:115-152`). | Staff with questionnaire permission in an organisation. |
| **QuestionnaireResponse** | Submit: `POST /api/v1/questionnaire/{slug}/submit/` (`care/emr/api/viewsets/questionnaire.py:115-153`). Read: `/api/v1/patient/{id}/questionnaire_response/` (read and update only, `care/emr/api/viewsets/questionnaire_response.py:30`). | Request: `{resource_id, patient, encounter?, results:[{question_id (UUID), values:[{value, unit, coding}], body_site, method, taken_at, note, sub_results}], form_submission?}` (`care/emr/resources/questionnaire_response/spec.py:18-41`). Stored as `responses`, `structured_responses`, `patient`, `encounter?`, `status` (`models/questionnaire.py:57-72`). | Patient-level forms: `can_submit_questionnaire_patient_obj`. Encounter forms: `can_submit_encounter_questionnaire_obj` (`questionnaire.py:121-134`). Staff only. |
| **Observation** | `/api/v1/patient/{id}/observation/`, **read-only** (`care/emr/api/viewsets/observation.py:46`) | `status, category, main_code (LOINC), value_type, value, body_site (SNOMED), effective_datetime, performer, data_entered_by, questionnaire_response, encounter` (**required**) (`care/emr/models/observation.py:6-52`) | Created only as a side effect of submitting coded questions (`care/emr/resources/questionnaire/utils.py:354-449, 538-626`). |
| **Symptom** (a Condition) | `/api/v1/patient/{id}/symptom/` (`api_router.py:439`) | `code` (SNOMED), `clinical_status, verification_status, severity` (mild / moderate / severe), `onset{onset_datetime, onset_age, onset_string}` (`care/emr/resources/condition/spec.py:42-84`). The model has `body_site` (`care/emr/models/condition.py:12`), but no condition spec exposes it (a search under `care/emr/resources/condition/` finds none). Each save also logs a QuestionnaireResponse with `structured_response_type="symptom"` (`care/emr/api/viewsets/base.py:111-125`; `condition.py:77`). | Staff, encounter-based authorization. |
| **MedicationRequest** | `/api/v1/patient/{id}/medication/request/` (`api_router.py:461`) | A prescription with status, intent and dosage (`care/emr/resources/medication/request/spec.py:39-332`) | Prescribing staff. |
| **MedicationStatement** | `/api/v1/patient/{id}/medication/statement/` (`api_router.py:471`) | `status` (active, on_hold, completed, stopped, unknown, entered_in_error, not_taken, intended), `medication` (SNOMED), `dosage_text, effective_period, encounter` (**required**), `information_source` (**related_person** / practitioner / patient), `reason, note` (`care/emr/resources/medication/statement/spec.py:14-50`) | Staff. |
| **Files** | `/api/v1/files/` (`api_router.py:122`) | `name, associating_id, file_type` (patient, encounter, consent, diagnostic_report, service_request), `file_category` (audio, xray, identity_proof, unspecified, discharge_summary, consent_attachment), archive fields (`care/emr/models/file_upload.py:13-31`; `care/emr/resources/file_upload/spec.py:14-28`). Stored in S3. **No DocumentReference or Composition resource.** | Staff. |
| **Notes** | `/api/v1/patient/{id}/thread/…/note/` (`api_router.py:483-497`) | `NoteThread{title, patient, encounter?}` and `NoteMessage{message, message_history}` (`care/emr/models/notes.py:6-18`) | Staff. |

### 3.3 Palliative / home-care specifics

- **In code:** encounter class `hh` (`constants.py:34`), the consent categories `dnr`, `acd`, `adr` and `comfort_care` (`consent/spec.py:39-46`), and a facility staff type "Palliative care Physician" (`care@2bae933:care/facility/models/facility.py:118`). We found nothing else palliative-specific. A search of `care/` for `CarePlan|care_plan` finds only a test name (`care/emr/tests/test_device_api.py:187`). The model list in `care/emr/models/` has no care plan, goal or related-person model.
- **Frontend gaps:**
  - the consent form's category list leaves out `comfort_care` (`care_fe@81e7515:src/types/consent/consent.ts:4-11`);
  - `add_verification` is defined in the API client (`src/types/consent/consentApi.ts:39`), but no `.tsx` file calls it;
  - new consents are sent with `verification_details: []` (`src/components/Consent/ConsentFormSheet.tsx:279`).

  This matches the docs' "known gap" ([consent concept](https://docs.ohc.network/concepts/clinical/consent/)).
- **Docs:** the palliative enrolment playbook says to "Record primary caregiver name, phone, and relationship" and, for caregiver-only intake, "Add caregiver as related contact with callback number". It marks "Palliative program enrollment" as *(coming soon)*, and says to "Document palliative-specific goals of care separately from acute encounter notes" ([patient-enrollment](https://docs.ohc.network/deployments/palliative/playbooks/patient-enrollment.md)).
  - **[unverified]** The core code has no related-contact model, only `Patient.emergency_phone_number`. It could live in Patient `extensions` (`care/emr/extensions/base.py:10-24`, where `patient` and `encounter` can be extended and `plug` is an owner type) or in a deployment questionnaire. The Kerala palliative features on [ohc.network/solutions/palliative-care](https://ohc.network/solutions/palliative-care) (care plans, caregiver registries) may come from plugins or deployment configuration we did not inspect.

### 3.4 Plugins

- **Backend:** a plugin is a Django app listed in `plug_config.py` (`care@2bae933:plug_config.py:1-6`; `plugs/plug.py:4-9`). It is installed into `INSTALLED_APPS` (`config/settings/base.py:142-149`) and its URLs are mounted at `api/<plugin>/` (`config/urls.py:111-112`; `docs/development/pluggable-apps.md:47-48`).
  - A plugin's views choose their own `authentication_classes`. Core already does this: the OTP views use `JWTTokenPatientAuthentication`. So **a backend plugin can add patient- or family-facing endpoints** with OTP, its own invite token or a service account.
  - Service accounts exist: there is `User.is_service_account` (`care/users/models.py:168`), and only Admin, Facility Admin and Administrator can create one (`care/security/permissions/service_account.py:7-12`).
  - **[unverified]** We didn't build or run a plugin.
- **Frontend:** a plugin manifest can add `routes`, `navItems`, `encounterTabs`, `encounterFileTabs`, `organizationTabs` and component slots (`care_fe@81e7515:src/pluginTypes.ts:210-225`). Plugin routes are loaded only in the staff `AppRouter` (`src/Routers/AppRouter.tsx:101-102`). `usePluginRoutes` throws if used outside it (`src/hooks/useCareApps.tsx:98-108`), and the OTP `PatientRouter` doesn't load plugin routes. So **a care_fe plugin can add staff screens, such as a "Family submissions" encounter tab, but not patient-portal screens**, unless core changes.

---

## 4. Data points side by side: CARE vs Sahara

How to read it:
- **CARE field** is `Resource.field`, cited as `care@2bae933`. **Sahara field** is `KotlinModel.field`, cited as `sahara@7abf52e` under `android/app/src/main/java/in/wedevit/care/`.
- **Fit** is one of:
  - **same**: same meaning, only the name changes;
  - **different shape**: same idea stored differently (the row says how);
  - **CARE-only**: CARE has it, Sahara doesn't collect it;
  - **Sahara-only**: Sahara has it, CARE has no place for it.

### 4.1 Person / patient
CARE sources: `care/emr/models/patient.py:16-52, 148-155` and `care/emr/resources/patient/otp_based_flow.py:41-49` (the OTP create requires name, gender, address, pincode, geo_organization, age or DOB). Sahara sources: `CareData.kt:11`, `DoctorCareData.kt:7`, `CareInviteLink.kt:71`, `CareAddFamily.kt:22`.

| CARE field | Sahara field | Fit |
|---|---|---|
| `Patient.id` (UUID) | `Person.id` (UUID string) | different shape: separate id spaces. Carry Sahara's id in `Patient.instance_identifiers` or `extensions` **[unverified]** |
| `Patient.name` | `Person.name` | same |
| `Patient.gender` (required for OTP create) | (none) | CARE-only |
| `Patient.date_of_birth` / `year_of_birth` | (none) | CARE-only |
| `Patient.phone_number` (also the OTP login key) | (none on Person; the account email is the login, `CareAccount.kt`) | CARE-only. Different shape: CARE's login is a phone OTP, Sahara's is an email account |
| `Patient.emergency_phone_number` | `CareContact.phone` (any contact) | different shape: CARE has one number, Sahara has a list of contacts with roles |
| `Patient.address`, `permanent_address`, `pincode`, `geo_organization` | (none) | CARE-only |
| `Patient.blood_group`, `deceased_datetime` | (none) | CARE-only |
| `Patient.instance_identifiers` / `facility_identifiers` (e.g. MRN) | (none) | CARE-only |
| `Patient.extensions` (`care/emr/extensions/base.py:10-24`) | (n/a) | CARE-only: the slot where Sahara-only patient data could go via a plugin |
| `Patient.created_date` | `Person.created` | same (epoch millis vs datetime) |
| `PatientUser{user, role}` (a staff CARE user given access) | `CareContact{name, relationship, role, phone}` | different shape: CARE links an existing CARE *account*, Sahara stores a *named contact* with a relationship |
| (none) | `CareContact.relationship`; relation chips Son/Daughter/Spouse/… (`CareAddFamily.kt:22`) | Sahara-only (CARE's playbook asks for it, but core has no field) |
| (none) | `CareInviteEntry{person, name, relation, createdAt, joined}`, `CareInvite.code` | Sahara-only (family invites) |
| (none) | member `canEdit` (read / can make changes, `CareAccount.kt:251`) | Sahara-only (family access level) |

### 4.2 Visit / encounter / care team
CARE sources: `care/emr/models/encounter.py:8-37`, `care/emr/resources/encounter/constants.py:4-34`, `care/emr/resources/encounter/spec.py:133-145`. Sahara sources: `CareData.kt:40`, `CareDailyUi.kt:32`, `DoctorWorkflowData.kt:6,11`.

| CARE field | Sahara field | Fit |
|---|---|---|
| `Encounter.id` | `CareItem.id` (kind="visit") | different shape: CARE has a first-class record, Sahara has a generic item with `kind` |
| `Encounter.encounter_class` (`hh` = home health, imp, amb…) | (implicit: every Sahara visit is a reminder) | CARE-only |
| `Encounter.status` (planned … completed, entered_in_error) | `CareItem.doneOn` (blank = not done) | different shape: CARE has 9 states, Sahara has done/not done |
| `Encounter.period{start,end}` | `CareItem.due` (one epoch time) | different shape: a period vs one time |
| `Encounter.facility` (required) | (none; free text in `title`/`detail`) | CARE-only |
| `Encounter.priority`, `hospitalization`, `current_location`, `discharge_summary_advice`, `external_identifier` | (none) | CARE-only |
| `Encounter.care_team[{member: User, role}]` | `WorkflowMember{name, role}` / `CareContact{role}` | different shape: CARE members are CARE user accounts, Sahara's are names |
| (none) | `CareItem.title`, `CareItem.detail` (free text) | different shape: Sahara has free text; CARE puts details in linked resources and notes |
| (none) | `WorkflowRequest{requestedBy, reason, status NEW…COMPLETED, appointmentId}` | Sahara-only (a family asks for a goals-of-care conversation) |

### 4.3 Consent and its verification
CARE sources: `care/emr/models/consent.py:6-16`, `care/emr/resources/consent/spec.py:21-71, 102-124`, `care/emr/api/viewsets/consent.py:48-71`. Sahara sources: `CareData.kt:38-39, 43-50`, `DoctorWorkflowData.kt:5,12-16`.

| CARE field | Sahara field | Fit |
|---|---|---|
| `Consent.category` (research, patient_privacy, treatment, dnr, comfort_care, acd, adr) | `WorkflowConsent.purpose` (free text); `RecordingConsent` is always about recording | different shape: CARE has a coded list, Sahara has free text with a fixed recording purpose. There is no Sahara DNR/ACD record |
| `Consent.decision` (permit / deny) | `WorkflowConsent.recordingAgreed`, `cloudProcessingAgreed` (two booleans) | different shape: CARE has one decision per consent, Sahara has two per participant |
| `Consent.date` | `RecordingConsent.recordingConsentAt`; `WorkflowConsent.at` | same (epoch vs datetime) |
| `Consent.period{start,end}` | (none) | CARE-only |
| `Consent.status` (draft, active, inactive, not_done, entered_in_error) | (none; a consent exists or doesn't) | CARE-only |
| `Consent.encounter` (required) | `RecordingConsent.audioPath` / `personId` | different shape: CARE ties a consent to an encounter, Sahara ties it to a recording |
| `Consent.note` | (none) | CARE-only |
| FileUpload `file_type=consent`, `consent_attachment` (`spec.py:109-116`) | (none) | CARE-only (a scanned signed form) |
| (whole consent is about the patient) | `RecordingConsent.participants`; `WorkflowConsent.participant` | Sahara-only: consent per *participant* in a conversation |
| (none) | `RecordingConsent.transcriptionConsentAt`, `noticeVersion` | Sahara-only |
| `verification_details[].verification_type` (family / validation) | `Acknowledgement.relationship` (who confirmed) | different shape: CARE has a two-value type, Sahara has a free relationship |
| `verification_details[].verified_by` (= the signed-in **staff** User, forced by the server, `consent.py:55`) | `Acknowledgement.name` (the family member's own name) | different shape: CARE records which staff member entered it, Sahara records which family member confirmed |
| `verification_details[].verification_date` (server time) | `Acknowledgement.at` | same |
| `verification_details[].verified` (bool), `note` | `WorkflowAcknowledgement.status` (READ / CONCERN / NOT_READY) | different shape: CARE has yes/no plus a note, Sahara has three outcomes |
| (a verification attaches to a **Consent**) | `Acknowledgement.versionId`, `contentHash` (binds to an exact goals-of-care **note version**) | Sahara-only: no CARE field binds a confirmation to an exact document version or hash |

### 4.4 Symptoms, pain body and check-ins
CARE sources: `care/emr/resources/questionnaire/spec.py:37-54, 115-152`, `care/emr/resources/questionnaire_response/spec.py:18-41`, `care/emr/models/observation.py:6-52`, `care/emr/resources/condition/spec.py:42-84`. Sahara sources: `CareSymptomData.kt:10-37`, `CarePainBodyData.kt:11, 47-52`, `CareModel.kt:521-531`, `FeedbackUi.kt:49`.

| CARE field | Sahara field | Fit |
|---|---|---|
| `Questionnaire.questions[].link_id` / `id` (UUID) | `SymptomDiaryRules.symptoms` keys ("Pain", "Tiredness", … 17 fixed keys) | different shape: CARE builds questions per instance (UUIDs), Sahara has a fixed list of keys. A CARE questionnaire must be created first |
| `Questionnaire.questions[].code` (LOINC) | `SymptomNote.scale` ("sahara-personal-nrs-v1") | different shape: CARE uses a coded concept, Sahara names its own scale (explicitly not ESAS-r/IPOS, `CareSymptomData.kt:9`) |
| `QR.results[].values[].value` (integer) / `Observation.value` | `SymptomNote.rating` (0-10) | same (integer) |
| `QR.results[].taken_at` / `Observation.effective_datetime` | `SymptomNote.observedDate` + `observedTime` (strings) | different shape: one datetime vs two strings |
| `QR.results[].note` / `Observation.note` | `SymptomNote.note` | same |
| `QR.results[].body_site` / `Observation.body_site` (one SNOMED Coding, `91723000` tree) | `SymptomNote.painBodySites` (list of 12 ids), `painBodyRatings` (site → 0-10) | different shape: CARE has one coded site per result, Sahara has many sites each with a strength. Split into one result per site and map ids to SNOMED **[unverified codes]** |
| (none) | `SymptomNote.painBodyPoints` (3D x/y/z), `painBodyModel` | Sahara-only |
| `Observation.performer` / `data_entered_by` (CARE user) | `SymptomNote.respondent` (patient / assisted-patient / caregiver / clinician), `author` | different shape: CARE records which staff user entered it, Sahara records who reported it |
| (none) | `SymptomNote.recall` (now / 24-h average / 24-h worst) | Sahara-only (could be a CARE choice question) |
| `Symptom(Condition).onset.onset_datetime` / `onset_string` | `SymptomNote.startedOn` | same idea (date string vs datetime) |
| `Symptom.severity` (mild / moderate / severe) | `SymptomNote.rating` 0-10 | different shape: 3 bands vs a 0-10 number |
| `Symptom.code` (SNOMED), `clinical_status`, `verification_status` | `SymptomNote.symptom` (key text) | different shape: CARE uses SNOMED codes and statuses, Sahara stores plain keys |
| (none) | `painLocation`, `painTriggers[]`, `selfReportedChange`, `comparedWithId` | Sahara-only (could be text questions in CARE) |
| `QR.encounter` (optional) / `Observation.encounter` (required) | (none) | CARE-only |
| `QR.questionnaire`, `form_submission`, `status` | (none) | CARE-only |
| QR choice answer | Daily check-in `CareItem.title` "A good / okay / hard day" + `detail` | same idea, as a choice question |

### 4.5 Medicines
CARE sources: `care/emr/resources/medication/statement/spec.py:14-50`, `care/emr/resources/medication/request/spec.py:39-332`, `care/emr/resources/medication/valueset/medication.py:5-26`. Sahara sources: `MedicineHistoryData.kt:6-9`, `DoctorPrescriptionData.kt:8-30`, `MedicineOrganizer.kt:723-727`.

| CARE field | Sahara field | Fit |
|---|---|---|
| `MedicationStatement.medication` (SNOMED Coding) | `MedicineCourse.name` (free text) | different shape: coded vs text |
| `MedicationStatement.dosage_text` | `MedicineCourse.directions` / `medicineCareDetail` ("Dose: …, Frequency: …") | same (text) |
| `MedicationStatement.status` (active … stopped, not_taken, intended) | `MedicineCourse.past` (bool) | different shape: 8 states vs current/past |
| `MedicationStatement.effective_period` | `MedicineCourse.changedAt` | different shape: a period vs one change time |
| `MedicationStatement.reason` | `MedicineCourse.reason` | same |
| `MedicationStatement.information_source` (related_person / patient / practitioner) | (implicit: entered by the family) | same idea. Set it to `related_person` |
| `MedicationStatement.encounter` (required) | (none) | CARE-only |
| `MedicationRequest` (intent, dosage instructions, prescriber) | `DoctorPrescription{title, prescribedOn, replacesId, endedAt, endReason}` + `DoctorPrescriptionMedicine{name, instructions, position}` | different shape: CARE has a staff-authored order, Sahara has a family-held copy with version history |
| `MedicationAdministration` (nurse gives a dose) | `MedicineIntakeEvent{outcome, plannedAt, reportedTakenAt, reporter, note, source}` | different shape: CARE's is a staff administration record, Sahara's is a family-reported dose log |

### 4.6 Goals-of-care conversation, notes and documents
CARE sources: `care/emr/models/notes.py:6-18`, `care/emr/models/file_upload.py:13-31`, `care/emr/resources/file_upload/spec.py:14-28`. Sahara sources: `CareData.kt:12-42, 175-187`, `DoctorPreparationQuestions.kt:20`, `DoctorConversationData.kt:11-13, 37-40`, `DoctorWorkflowData.kt:9-10`.

| CARE field | Sahara field | Fit |
|---|---|---|
| (no CarePlan / Goal / Composition / DocumentReference) | `CareVersion{version, priorities, participants, topics, questions, nextSteps, doctor, published}` | Sahara-only as a structured note. In CARE it can only go in as text (`NoteMessage.message`) or a PDF (`FileUpload`) |
| `NoteThread.title`, `NoteMessage.message`, `message_history` | `CareVersion` text fields; `CareItem` kind="journal" `title`/`detail` | different shape: CARE has a free-text thread with edit history, Sahara has named sections and numbered versions |
| (none) | `CareVersion.contentHash`, `hashScheme` | Sahara-only (pins the exact version) |
| (none) | `CareVersion.draftOrigin` (manual / ai / ai_transcript), `reviewStatus`, `reviewerVerification` | Sahara-only (provenance and review state) |
| Questionnaire `text` question + QR answer | `DoctorPreparationQuestion{id, text, section, answer}` | same idea, if a CARE "goals of care" questionnaire exists |
| (none) | `DoctorGoalContext{key, status, origin, answer, evidence[{start,end,quote}]}` | Sahara-only (answers linked to transcript spans) |
| `FileUpload{name, file_type, file_category, associating_id}` | `CareReport{name, mime, fileName, bytes, created}` | same idea. CARE files point to a patient, encounter or consent through `associating_id` + `file_type`. Sahara reports point to a person |
| `FileUpload.is_archived`, `archive_reason`, `archived_by` | (none) | CARE-only |
| `FileUpload` `file_category=audio` | `DoctorLocalAudioRef{localPath, sha256, consentAt, recorderUid}`; `Conversation.audioPath` | different shape: CARE uploads to its server, Sahara keeps audio on the phone with a hash and consent time (audio is never uploaded; only the typed or dictated words go to Sahara's server, when sent) |
| (none) | `Conversation.transcriptProvider`, `transcriptModel`, `transcribedAt` | Sahara-only |

### 4.7 Data CARE has that Sahara doesn't collect
- **Patient identity and address:** gender, date or year of birth, phone number, address, pincode, geographic organisation, blood group, date of death, hospital identifiers (`care/emr/models/patient.py:17-50`).
- **Facility and encounter context:** facility, encounter class, the 9-state status, period, priority, location and discharge advice (`care/emr/models/encounter.py:9-37`).
- **Coded terminology:** SNOMED medication and condition codes, LOINC observation codes, SNOMED body sites, UCUM units (section 3.2 valuesets).
- **Consent context:** a coded category (including DNR, ACD, ADR), status, period, encounter, attached signed forms (`care/emr/resources/consent/spec.py:39-71, 109-116`).
- **Staff authorship:** `created_by`, `data_entered_by`, `performer`, `verified_by` as CARE user accounts (`care/emr/models/observation.py:17-25`; `consent.py:55`).
- **Clinical orders and records:** MedicationRequest, MedicationAdministration, diagnoses, allergies, diagnostic reports, service requests (`config/api_router.py:435-476`).
- **Scheduling:** public slots and token bookings (`care/emr/api/otp_viewsets/slot.py:37-104`).

### 4.8 Data Sahara collects that CARE has no place for
- **Family membership:** relationship, invites and access level (`DoctorCareData.kt:7`; `CareInviteLink.kt:7,71`; `CareAccount.kt:251`).
- **Family confirmation of an exact note version:** `Acknowledgement{versionId, contentHash, name, relationship, at}` and READ / CONCERN / NOT_READY (`CareData.kt:38-39`; `DoctorWorkflowData.kt:5,12`).
- **Per-participant recording and transcription consent** with a notice version (`CareData.kt:43-50`; `DoctorWorkflowData.kt:13-16`).
- **The structured, versioned goals-of-care note:** its sections, hash, draft origin and review state (`CareData.kt:23-37`), and answers linked to transcript evidence (`DoctorConversationData.kt:37-40`).
- **3D pain points and per-site strength** (`CareSymptomData.kt:15-19`; `CarePainBodyData.kt:11`).
- **Diary details:** symptom recall window, respondent type, triggers and self-reported change (`CareSymptomData.kt:10-14, 36-37`).
- **Family-reported dose log:** planned time, taken time, outcome and reporter (`MedicineHistoryData.kt:7-9`).
- **Family requests** for a conversation (`DoctorWorkflowData.kt:6`), family tasks and journal entries (`CareDailyUi.kt:32`).
- **Other on-phone data:** transcription provider and model provenance (`CareData.kt:16-18`), the centre directory (`CareModel.kt:17`).

**Where this data lives (owner correction, 27 Oct).** Sahara is not offline-only. Without an account, all of the above stays on the phone. Signed in, the names, entries, notes, confirmations, medicine log, prescriptions and care contacts are saved on Sahara's Supabase server for the invited family and doctor; recordings and report files stay on the phone. Nothing is anonymised before a doctor sees it today; that is planned. Detail: `CARE_VS_SAHARA_FOR_TEAM.md`, "Where Sahara's data lives".

**Bottom line:** medicines and documents line up. Symptoms and goals-of-care answers line up once a CARE questionnaire exists. CARE needs identity, address, facility and coded fields that Sahara never collects, so an import must ask for them. Sahara's family-side data has no CARE home: family membership, family self-confirmation of an exact note, per-participant consent, the 3D pain body and the dose log. In P2 those would live in a plugin's own tables.

---

## 5. Phased plan

### P1, before 7 Nov: same words, plus an export file
1. **Terms (0.5 day).** Match labels in the export and the pitch:
   - "Encounter (home visit)";
   - Consent "verification: Family / Validation";
   - "MedicationStatement, source: family";
   - "QuestionnaireResponse".

   No UI renames are needed for the demo.
2. **Export (1.5–2 days, estimate).** A "Share for hospital (CARE format)" action writes one JSON file plus a PDF:
   - `patient`: CARE Patient fields Sahara has (`name`), with blanks marked `"required_by_care": [...]`;
   - `consents[]`: CARE `ConsentCreateSpec` bodies, only for advance-directive documents the family attached;
   - `family_confirmations[]`: the Sahara `Acknowledgement` (version, contentHash, name, relationship, time), labelled as a Sahara extension. The suggested CARE action is "staff adds `verification_type: family` after checking";
   - `goals_of_care`: the questions and answers, plus the reviewed note as an attached PDF (target: FileUpload + NoteThread);
   - `symptom_responses[]`: `QuestionnaireSubmitRequest`-shaped bodies with `question_id` set to Sahara's link ids, plus a `question_map` the hospital fills in;
   - `medication_statements[]`: `information_source: "related_person"`.

   Unit tests: round-trip a fixture person and check the enum values against section 3.2.

   **No network, no CARE login.** The web prototype's mock bundle (`prototype/app/fhir-export-modal.tsx`) is FHIR-style, not CARE-shaped, so it won't be reused as-is.
3. **Slide 11 text (1 h):** see section 6.

What it takes: one Android developer, synthetic fixtures, no CARE server. Risk: CARE requires fields Sahara doesn't hold (gender, address, pincode, geo organisation, encounter). The export must say so rather than invent them.

### P2: a CARE plugin for family submissions with staff review (estimate 2–3 weeks, [unverified])
- **Backend plugin (`care_sahara`):**
  - models `FamilySubmission{patient, encounter?, kind, payload, sahara_receipt, status pending|accepted|rejected, reviewed_by}`;
  - a family endpoint at `/api/care_sahara/…` that accepts submissions, using either the existing patient OTP token (phone match) or a facility-issued invite token;
  - staff endpoints that turn an accepted submission into a core QuestionnaireResponse, MedicationStatement or Consent verification, written as the reviewing staff user, so core permissions and audit still apply.
- **Frontend plugin:** an `encounterTabs` entry, "Family submissions", in care_fe with accept/reject, plus a "family confirmation" view for goals-of-care notes.
- **Prerequisites:**
  - a local CARE stack (Docker);
  - a palliative unit's questionnaire definitions;
  - a decision on sign-in: phone OTP (which can't tell the patient from a caregiver) or invites.
- Kotlin code can't run inside CARE. The plugin is Python and React.

### P3: pilot (estimate 1–3 months, [unverified])
- First, one palliative unit already on CARE (Kerala grid or a partner), with synthetic data.
- Then real patients under that unit's own consent, data-sharing agreement and hosting.
- Upstream proposals to OHC:
  - `comfort_care` in the consent form;
  - a verification UI;
  - a related-person / caregiver resource;
  - optionally, plugin routes in `PatientRouter`.

---

## 6. What NOT to claim in the pitch

- **Don't say** "Sahara integrates with CARE", "syncs with CARE" or "works with CARE today". Nothing connects (section 4 of `CARE_BRIEF_2026-10-27.md`).
- **Don't say** "Sahara fills CARE's consent-verification gap". CARE's gap is a *staff* screen for a *consent*. Sahara's feature is *family self-confirmation* of a *goals-of-care note*. They are related, not the same.
- **Don't say** "CARE has a family/caregiver app or login". It has a phone OTP portal limited to registration and appointments.
- **Don't say** "FHIR-compliant export" or "FHIR integration". CARE isn't a FHIR server, and P1 is a CARE-shaped JSON file.
- **Don't say** "Sahara writes CarePlans into CARE". CARE at `2bae933` has no CarePlan resource.
- **Don't say** a family confirmation is a "verified clinical record" or a CARE consent verification. CARE ties `verified_by` to a staff user.
- **Don't claim** the symptom diary is ESAS-r or IPOS, or LOINC-coded, without clinical sign-off.
- **Do say:** "CARE is the staff record for palliative home care. Sahara is the family's phone app, built to CARE's own record types (Patient, home-health Encounter, Consent with Family verification, QuestionnaireResponse, MedicationStatement reported by family). Today it exports a CARE-shaped file. Next comes a CARE plugin where staff review what the family sent. Sahara adds what CARE leaves out: family accounts and invites, a 3D pain diary, and the family's own confirmation of the exact goals-of-care note."

---

## 7. Open questions / unverified

- How the Kerala palliative deployment stores care plans and caregivers (plugins, extensions, questionnaires). We inspected only core.
- Whether any CARE deployment gives caregivers CARE user accounts (`PatientUser`).
- Exact SNOMED codes for Sahara's 12 body sites, and whether any LOINC code is acceptable for the personal 0-10 scale.
- The file upload flow and CARE's required Patient fields at import time. None of this was run against a server.
- The plugin effort figures, since no plugin was built.
