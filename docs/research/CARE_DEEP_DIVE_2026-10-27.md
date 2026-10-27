# CARE deep dive (27 Oct 2026)

Owner asked: "do more research, dig in more, and probably clone the CARE thing as well." This file goes past [`CARE_BRIEF_2026-10-27.md`](CARE_BRIEF_2026-10-27.md), [`CARE_FAMILY_SIDE_FIT.md`](CARE_FAMILY_SIDE_FIT.md) and [`CARE_VS_SAHARA_FOR_TEAM.md`](CARE_VS_SAHARA_FOR_TEAM.md). It doesn't repeat what they already cover, and section 7 corrects them where they were wrong.

**How this was done.** Repos were shallow-cloned and **read only**. Nothing was installed, built or run. There was no Docker, no npm/pip install of CARE, and no CARE server. Public web pages were fetched. One check was executed: our own script, which tests the form JSON in section 5 against a copy of CARE's form rules. **[unverified]** marks anything about how CARE behaves at runtime.

## Plain summary for the team (10 points)

1. **The Kerala palliative grid runs ordinary CARE, set up with its own forms.** We found no separate "palliative" plugin or code. Kerala's home-visit forms are questionnaires its staff built inside their own CARE server, and they are not published anywhere we can read.
2. **CARE's code has no care plan and no caregiver record**, even though the website lists both. The palliative docs have one page, and its "programme enrolment" step still says "coming soon".
3. **A hospital can build our forms in CARE today, with no code.** We wrote three in CARE's own format: the 17-symptom diary, pain by body area, and the goals-of-care questions. All three pass a copy of CARE's form rules (section 5).
4. **Three limits on those forms.** CARE won't stop a score of 14 on a 0–10 question. Its form screen has no body-site picker. Answers to patient-level forms aren't turned into chartable observations. So our 3D pain body only goes in as "area plus score" rows.
5. **The patient login is still booking-only**, and every add-on hook in the patient portal is gone. OHC's own plugin guide names two patient-portal hooks; neither exists in today's CARE frontend.
6. **A "family inbox" plugin is realistic, after 7 Nov.** It is a small Django add-on plus a staff screen. The family's entries wait in a queue, and a nurse accepts each one before it enters the record. About 3 weeks for one developer. Section 4 has the full blueprint.
7. **ABDM (India's national health-record network) doesn't let one app push a file into a hospital.** Records move only when the patient consents, and Sahara would have to register as a provider first. We couldn't read CARE's ABDM code, because the repo isn't public to us. So "export to ABDM" is a later option, not a 7 Nov one.
8. **Licences.** CARE and its frontend are MIT (free to reuse and change). The plugin starter kit, OHC's coding-agent skills and the docs have no licence file, so ask OHC before copying from them. Community: Slack (slack.ohc.network) and support@ohc.network.
9. **No other family palliative app in India connects to CARE or ABDM, as far as we found.** PalliKare is a reference app for doctors and nurses. Pallium India runs helplines and telehealth.
10. **The recommendation changes slightly.** Still describe the link on the slide and don't build a plugin before 7 Nov. But show the three CARE form files, not just a bundle. They are cheap, they really match CARE's format, and they back up "a CARE hospital can take our data".

---

## 1. Sources and versions

| Repo | Commit (date) | Cited as |
|---|---|---|
| `ohcnetwork/care` | `2bae933` (19 Oct) | `care@2bae933:path:line` |
| `ohcnetwork/care_fe` | `81e7515` (26 Oct) | `care_fe@81e7515:…` |
| `ohcnetwork/care_scaffold` | `a3ecf5e` (11 Oct) | `scaffold@a3ecf5e:…` |
| `ohcnetwork/care_filly` | `cccab7c` (24 Aug) | `filly@cccab7c:…` |
| `ohcnetwork/care_ai_vision_fe` | `09f0fe5` (2 Sep) | `vision_fe@09f0fe5:…` |
| `ohcnetwork/skills` | `30f437f` (12 Aug) | `skills@30f437f:…` |
| `ohcnetwork/docs` (docs.ohc.network source) | `40dbd25` (27 Aug) | `docs@40dbd25:…` |
| `ohcnetwork/care_apps_registry` | `7b528aa` (2 Jul) | `registry@7b528aa:…` |
| `ohcnetwork/care-plugin-cookiecutter`, `care_hello_fe`, `care_imports_fe`, `care_ai`, `care_dashboard`, `care_tools` | `bef536c`, `ade74c8`, `62fb4ea`, `80bf42e`, `89f4879`, `73bdced` | skimmed only |
| Sahara (this repo) | `d0a278f` | `sahara@d0a278f:File.kt:line` under `android/app/src/main/java/in/wedevit/care/` |

**Could not read:**
- `care_abdm`, `care_abdm_fe`, `care_scribe`, `care_scribe_fe`, `care_hcx`, `care_teleicu_devices_fe`, `care_filly_fe`.
  - Anonymous git asks for credentials, and raw.githubusercontent.com returns nothing.
  - So they are private, renamed, or blocked for this session.
  - The registry still lists `care_abdm_fe` and `care_scribe_fe` (`registry@7b528aa:index.json`), and `care@2bae933:docs/development/pluggable-apps.md:57` still links `care_scribe` as public.
- The GitHub org listing (blocked by the session proxy).
- `care-abdm.ohc.network` (proxy 502).
- nrces.in (the TLS chain fails in this sandbox; we used search snippets instead).

So nothing below about ABDM comes from code.

---

## 2. The Kerala palliative deployment ("Home Care Grid")

**What runs it.** It is CARE 3.0 core configured per instance. We found no palliative plugin anywhere:
- `care` and `care_fe` hold nothing palliative except the encounter class `hh` (`care_fe@81e7515:src/types/emr/encounter/encounter.ts:54`) and a staff type "Palliative care Physician" (`care@2bae933:care/facility/models/facility.py:118`).
- A search of all of `care/` and `care_fe/src/` for `palliative|home care|homecare` finds only that staff type and old migrations.
- The app registry lists exactly five apps: ABDM, Hello, Imports, Scribe and TeleICU devices (`registry@7b528aa:index.json`).

OHC's own blog gives the timeline (archived in `output/research/care-website-20261023/text/`):
- **Jan 2025:** pilots at Ayyampuzha and Calicut. 177 patients and 189 home visits ("palliative-care-grid-field-pilots", lines 17-23).
- **3 Mar 2025:** statewide launch. Nurses register patients and record home visits. Malayalam localisation. Handover to NHM (the state health mission). The ABDM registration of the Kerala instance was "awaiting NHA approval" ("kerala-palliative-care-grid-launch", lines 13-36).
- **Apr 2025:** a new **Home-Based Care module** "allowed field nurses to log vitals, symptoms, diagnoses, medications, and follow-up information". The same month brought "Questionnaire import and export with value-set bindings" ("stabilizing-care-after-statewide-launch", lines 19, 33).
  - We found no module by that name in code. Our reading **[unverified]**: it is encounter class `hh` plus structured questionnaire questions (symptom, diagnosis, medication_statement), which exist in core (section 5.1).
- **Aug–Sep 2025:** "designed new questionnaires for Mid Level Service Providers and Janakeeya Arogya Kendram home care teams. ABDM integration steps were completed" ("token-management-care-hmis-groundwork", line 45).
  - OHC's form-generator skill mentions a production example `mlsp-form-for-category` (`skills@30f437f:care-form-skill/form.knowledge.md:194`). The file itself isn't in the repo.
- **Jul 2025:** "Patient accounts" is **billing**: charges, invoices and payments ("patient-accounts-care-hmis", lines 13-21). It is **not** a patient-facing account. Don't cite it as one.

**Visit forms and questionnaires.** Not public. They live as rows in the Kerala instance's database. **[unverified]** what they ask, and whether they use a 0–10 scale. Ask OHC for an export: the April release added questionnaire export.

**Care plans and caregivers.**
- **Care plans:** none in code.
  - No `CarePlan` model in `care@2bae933:care/emr/models/` (the full list: account … valueset).
  - No `careplan` in `care_fe/src`.
  - The only hit is a test name (`care@2bae933:care/emr/tests/test_device_api.py:187`).
  - The website lists "care plans" as a CARE Core primitive (`ohc.network__product__care-core.txt:5`; `ohc.network__blog__care-core-as-operating-layer.txt:16`). That is a product claim with no code behind it at `2bae933`.
- **Caregivers:** the only caregiver data is what the playbook says to collect: "Record primary caregiver name, phone, and relationship" and "Add caregiver as related contact with callback number" (`docs@40dbd25:deployments/palliative/playbooks/patient-enrollment.mdx:21,55`).
  - Core has no field for this. In Kerala it must sit in a registration questionnaire, a patient extension or a free-text note **[unverified]**.
- **Docs:** the palliative docs are two files: `intro.mdx` and that one playbook (`docs@40dbd25:deployments/palliative/`).

---

## 3. Patient OTP / portal: exactly what a patient token reaches

The earlier fit doc (section 3.1) got this right. This pass confirms it independently and adds:
- **Every place that accepts the token:** `JWTTokenPatientAuthentication` is used in only three viewsets:
  - `care@2bae933:care/emr/api/otp_viewsets/patient.py:14`;
  - `…/otp_viewsets/slot.py:38`;
  - `care/emr/api/viewsets/organization.py:91`.

  No plugin we could read uses it either.
- **Staff endpoints reject it.** Staff APIs use `config.authentication.CustomJWTAuthentication`, which validates a standard access token (`care@2bae933:config/authentication.py:14-31`). The patient token has type `patient_login` (`config/patient_otp_token.py:8`).
- **The patient portal has a dead end.** `PatientRouter` exposes `/patient/:id/:tab` (`care_fe@81e7515:src/Routers/PatientRouter.tsx:33-42`). But `PatientProfile` loads the patient from the **staff** endpoint `/api/v1/patient/{id}/` (`src/components/Patient/PatientProfile.tsx:43-52`; `src/types/emr/patient/patientApi.ts:21`). With only a patient token this should fail and show "patient not found" **[unverified]**. The patient home only lists appointments (`src/pages/Patient/index.tsx:45`).
- **OTP limits:**
  - 10 sends per 60 minutes, 3 verify attempts, 5 failures, then a 60-minute lockout (`care@2bae933:config/settings/base.py:505-520`).
  - Production needs an SMS backend, or it raises "SMS Backend not configured" (`care/emr/api/otp_viewsets/login.py:100-113`).
  - Outside production the code is `45612` (`login.py:97-99`).
- **No patient-portal plugin hooks.** OHC's scaffold names two: `AppointmentCardActions` (in `pages/Patient/index.tsx`) and `AppointmentSlotGroupHeader` (in `PublicAppointments/Schedule.tsx`) (`scaffold@a3ecf5e:skills/care-extension-points/SKILL.md:26-28`; `skills/care-auth-contexts/SKILL.md`, "Which shell am I in?"). **Neither exists in `care_fe@81e7515`**: there is no match in `src/`, and they are missing from `SupportedPluginComponents` (`src/pluginTypes.ts:138-158`). The scaffold itself warns that its catalogue is a snapshot.
- **A stale class in the scaffold.** The scaffold's OTP viewset example uses `MiddlewareAuthentication` (`care-auth-contexts/SKILL.md`, "Backend OTP viewsets"). That class doesn't exist in `care@2bae933`. A plugin must use `config.patient_otp_authentication.JWTTokenPatientAuthentication` with `OTPAuthenticatedPermission` (`config/patient_otp_authentication.py:64-81`).

**Bottom line (unchanged, now firmer):** a patient token reaches only:
- patients with the same phone number (list and create);
- public slots and that phone's own appointments;
- the organisation list.

A plugin can add its own OTP routes on the backend. On the frontend, the patient portal has **no** plugin surface today.

---

## 4. Plugin system in practice, and a `care_family` blueprint

### 4.1 How real plugins register, authenticate and add things

**Backend** (Django app, `filly@cccab7c`; scaffold template `scaffold@a3ecf5e:templates/backend/`):
- **Registration:**
  - CARE's `plug_config.py` is empty at `2bae933` (`care@2bae933:plug_config.py:4`).
  - Deployments add plugins through the `ADDITIONAL_PLUGS` JSON environment variable (`care@2bae933:plugs/manager.py:22-27`), installed with pip (`manager.py:29-32`).
  - URLs mount at `/api/<plugin>/` (`config/urls.py:111-112`).
  - care_filly's README still shows editing `plug_config.py` directly (`filly@cccab7c:README.md:25-41`). Both ways work. The scaffold says to use `ADDITIONAL_PLUGS` and never edit core (`scaffold@a3ecf5e:skills/care-backend-plugin/SKILL.md`, "How core loads plugins").
- **Authentication:** care_filly calls `CustomJWTAuthentication().authenticate(request)` itself. Only signed-in CARE staff get in (`filly@cccab7c:care_filly/api/common.py:15-33`). It also exempts its routes from CSRF, because it uses a bearer token, not cookies (`care_filly/urls.py:17-23`).
- **Permissions:** a plugin declares an enum of `Permission(name, description, PermissionContext.FACILITY, [roles])` (`filly@cccab7c:care_filly/security/permissions.py:13-30`). It registers it with `PermissionController.register_permission_handler` in `AppConfig.ready()` (`apps.py:39-46`). It adds an `AuthorizationHandler` with `AuthorizationController.register_internal_controller` (`security/authorization.py:1-18`). Contexts available: GENERIC, FACILITY, PATIENT, QUESTIONNAIRE, ORGANIZATION, FACILITY_ORGANIZATION, ENCOUNTER (`care@2bae933:care/security/permissions/constants.py:5-12`).
- **Models:** they inherit `care.emr.models.base.EMRBaseModel` and use foreign keys into core, e.g. `"facility.Facility"` (`filly@cccab7c:care_filly/models/medispeak.py`). Core must never point at plugin tables (scaffold backend skill, "Models").

**Frontend** (Vite module-federation remote):
- **Packaging:** it exposes `./manifest` as `remoteEntry.js` and shares react, react-dom, react-i18next, @tanstack/react-query and raviger with the host (`vision_fe@09f0fe5:vite.config.mts:10-23`).
- **Loading:** care_fe merges `GET /api/v1/plug_config/` with the build-time `REACT_ENABLED_APPS`, loads each remote's manifest, and exposes `window.CARE_API_URL` and `window.AuthUserContext` (`care_fe@81e7515:docs/care-apps-architecture-note.md`).
- **Auth:** the plugin sends `Bearer localStorage.care_access_token` (`vision_fe@09f0fe5:src/lib/request.ts:97-127`).
- **Manifest surfaces:** `routes`, `navItems`/`userNavItems`/`adminNavItems`, `organizationTabs`, `components` (slots), `encounterTabs`, `encounterFileTabs`, `devices` and `overrides` (`care_fe@81e7515:src/pluginTypes.ts:210-225`).
  - An encounter tab receives `{encounter, patient}` (`src/pages/Encounters/EncounterShow.tsx:49-52`).
  - Patient-level slots are `PatientInfoCardActions`, `PatientHomeActions` and `PatientDetailsTabDemographyGeneralInfo`. All three are staff side (`src/pages/Patient/PatientHome.tsx:138`, routed at `/facility/:facilityId/patients/home`, `src/Routers/routes/PatientRoutes.tsx:78`).
  - There are no patient-level *tabs* for plugins, only encounter tabs.
- **Example:** `care_ai_vision_fe` adds one route, two component slots and a user-menu link (`vision_fe@09f0fe5:src/manifest.tsx`).

### 4.2 Blueprint: `care_family` (backend) + `care_family_fe` (staff review)

**Goal.** Sahara sends what the family reported. It lands as *pending*. A CARE nurse or doctor reviews it, and **accepting** writes the core record as that staff user. CARE's rule that staff write clinical data still holds.

**Backend package `care_family/`** (layout from `scaffold@a3ecf5e:templates/backend/__PLUGIN_SNAKE__/`):

```
care_family/
  apps.py            # ready(): import signals, security; PermissionController.register_permission_handler(FamilyPermissions)
  settings.py        # PluginSettings: FAMILY_LINK_TTL_DAYS=180, FAMILY_MAX_PAYLOAD_KB=256, FAMILY_ALLOWED_KINDS="symptom,pain,checkin,medicine,goc_ack"
  models/link.py     # FamilyLink
  models/submission.py  # FamilySubmission, FamilyAttachment
  security/permissions.py, security/authorization.py
  auth.py            # FamilyLinkAuthentication (DRF BaseAuthentication)
  viewsets/family.py # family-facing (link token)
  viewsets/staff.py  # staff-facing (CARE JWT)
  converters.py      # accepted submission -> core record
  urls.py, migrations/, tests/
```

**Models** (all `EMRBaseModel`, so `external_id`, timestamps and soft delete come free):

| Model | Fields |
|---|---|
| `FamilyLink` | `patient` FK `emr.Patient`; `facility` FK `facility.Facility`; `display_name`; `relationship` (choice: spouse, son, daughter, parent, sibling, other); `phone_number` (optional); `token_hash` (sha256, unique); `expires_at`; `revoked_at`, `revoked_by`; `created_by` (staff). One row per family member. |
| `FamilySubmission` | `link` FK; `patient` FK (copied from the link, **never from the request**); `encounter` FK `emr.Encounter` (null; staff can attach on accept); `kind` (symptom, pain, checkin, medicine, goc_ack); `payload` JSON (schema checked per kind); `client_id` + `client_created_at` (Sahara's UUID and time, unique per link, so retries don't duplicate); `content_hash`; `status` (pending, accepted, rejected, superseded); `review_note`; `reviewed_by` FK User; `reviewed_at`; `result_type` + `result_external_id` (the core record it became). |
| `FamilyAttachment` | `submission` FK; a file in the plugin's own storage prefix; `mime`; `sha256`. Optional in v1. |

**Family authentication.** Use a plugin-issued **link token**, not CARE's OTP, as the default:
- How it works: staff create a `FamilyLink` from the patient screen. CARE shows a QR code or short code once. Sahara stores the token, and `FamilyLinkAuthentication` resolves it to a `FamilyLink` (unrevoked and unexpired).
- Why not OTP:
  - OTP can't tell the patient from a relative (section 3);
  - it needs the hospital's SMS gateway (`login.py:100-113`);
  - its token lasts one hour with no refresh (`config/patient_otp_token.py:6-8`).
- Optional: also accept `JWTTokenPatientAuthentication` + `OTPAuthenticatedPermission` on a separate `otp/` route set, scoped by `patient__phone_number = request.user.phone_number`. That follows the scaffold's convention, but uses the class that exists (section 3).

**Endpoints** (mounted under `/api/care_family/`):

| Method + path | Auth | Does |
|---|---|---|
| `POST link/exchange/` | none (one-time code, throttled) | Swaps a short code shown in CARE for a link token |
| `GET  family/me/` | link token | Patient first name, facility name, allowed kinds, link expiry |
| `POST family/submissions/` | link token | Creates a pending `FamilySubmission`. Idempotent on `client_id` |
| `GET  family/submissions/` | link token | The family's own submissions and their status (accepted or rejected, plus the note) |
| `GET  family/questionnaires/` | link token | Slugs and questions of the facility's forms flagged for family use (for local mapping) |
| `POST links/` / `GET links/?patient=` / `POST links/{id}/revoke/` | CARE JWT | Staff manage family links |
| `GET  submissions/?patient=&status=pending` | CARE JWT | Review queue |
| `POST submissions/{id}/accept/` | CARE JWT | Converts and writes the core record as `request.user` (below) |
| `POST submissions/{id}/reject/` | CARE JWT | Sets rejected, with a note |
| `GET  config/` | CARE JWT | Client-safe settings (the scaffold convention) |

**What happens on accept** (`converters.py`). Each converter reuses core code and core permission checks:
- **symptom / pain / checkin** → build a `QuestionnaireSubmitRequest` against the facility's form (section 5) and call core `handle_response(questionnaire, request, user)` (`care@2bae933:care/emr/resources/questionnaire/utils.py:538-628`).
  - First run `AuthorizationController.call("can_submit_questionnaire_patient_obj", user, patient)`. Use `can_submit_encounter_questionnaire_obj` if an encounter is attached (`care/emr/api/viewsets/questionnaire.py:121-134`).
- **medicine** → a MedicationStatement with `information_source = related_person` (`care/emr/resources/medication/statement/spec.py:25-28`).
  - It needs an open encounter, which staff choose on accept, and `can_write_encounter_clinical_data`.
- **goc_ack** (the family's READ / CONCERN / NOT_READY on a note version, with its hash) → keep it in the plugin as evidence. Optionally, staff then add a core Consent verification of type `family` themselves (`care/emr/api/viewsets/consent.py:48-71`). The plugin must **not** forge `verified_by`; core overwrites it with the signed-in user anyway (`consent.py:55-56`).

**Permissions** (`FamilyPermissions`, context FACILITY):

| Permission | Roles |
|---|---|
| `can_manage_family_links` | Doctor, Nurse, Facility Admin, Admin |
| `can_review_family_submissions` | Doctor, Nurse, Facility Admin, Admin |
| `can_view_family_submissions` | the above + Staff, Volunteer |

Role constants are in `care@2bae933:care/security/roles/role.py:22-73`. The `AuthorizationHandler` also requires core `can_view_clinical_data(user, patient)` (`care/security/authorization/patient.py:94`), so a plugin permission never widens what core allows.

**Frontend `care_fe`** (`care_family_fe`, cloned from `scaffold@a3ecf5e:templates/frontend/`):
- `routes`: `/facility/:facilityId/family-inbox`, a facility-wide pending queue, plus `/facility/:facilityId/patient/:patientId/family`.
- `navItems`: "Family inbox", with a pending count.
- `components.PatientInfoCardActions`: a "Family (3 pending)" button and "Link a family member" (creates a `FamilyLink` and shows the QR code once).
- `encounterTabs`: `{"family": FamilyTab}`. It shows pending items for the encounter's patient, an accept button (writes into this encounter) and a reject button with a note.
- A review card shows the payload as a readable form preview: the 0–10 values, the body-area rows and the `respondent`. Accepting asks for confirmation.

**What Sahara (Android) adds:** a "Connect to my hospital" QR scanner, a small HTTPS client, and a sync outbox of signed submissions (`client_id`, hash). It shows each item's accepted or rejected status back to the family. Nothing is sent without a family member choosing to share.

**Effort** (one developer new to CARE; confidence **low**, because nothing was built or run):

| Piece | Days | Why |
|---|---|---|
| Local CARE stack with the scaffold (Docker, seed data, a facility, a form) | 2–3 | First-time setup. OHC's scaffold automates most of it, but CARE is large and the docs warn about stale-install traps |
| Backend models, migrations, link auth, family endpoints, tests | 4–5 | 3 models, about 10 endpoints, idempotency and throttling |
| Converters reusing `handle_response` + MedicationStatement + permission wiring, tests | 3–4 | Reuse cuts the work, but encounter-open rules and validation errors need care |
| Frontend: inbox route, patient action, encounter tab, review card | 4–5 | Module federation setup plus 3 screens, on CARE's ShadCN components |
| Android: QR link, outbox, status display | 2–3 | Small, but needs device testing |
| **Total** | **≈15–20 dev-days (3–4 weeks)** | The earlier "2–3 weeks" was a bit optimistic, because the Android part and stack setup weren't counted |

**Strongest counterargument.**
- It only matters if a real CARE hospital installs a third-party plugin. Kerala's instance belongs to NHM (the state health mission) and is government-run (blog, "Handover to NHM").
- A plugin also adds an internet-facing API holding health data. That needs a security review by the deployment. For example, the Kerala instance got CERT certification from Keltron, a Kerala state agency ("preparing-care-statewide-palliative-rollout", line 24).
- So build it only with a named partner unit.

---

## 5. Questionnaire engine: our forms as CARE questionnaires

### 5.1 What the engine accepts (from code)

- **Question types:** group, boolean, decimal, integer, string, text, display, date, dateTime, time, choice, url, quantity, structured. `open_choice`, `attachment` and `reference` are commented out (`care@2bae933:care/emr/resources/questionnaire/spec.py:37-54`).
- **Choice options** must be non-empty strings (`spec.py:98-105`). A choice or quantity question needs `answer_option` or `answer_value_set`. A group needs at least one child (`spec.py:170-182`).
- **`link_id` and `id` must be unique** across the whole tree (`spec.py:232-244`). `id` may be UUID4 or UUID5 (`spec.py:119`).
- **Slug:** 5–50 characters, URL-safe, unique, and must not reuse an internal type such as `symptom` (`care/emr/utils/slug_type.py:15-49`; `spec.py:198-215`).
- **Create needs `organizations`** (min 1, `spec.py:248`). The caller needs `can_write_questionnaire` in each organisation (`care/emr/api/viewsets/questionnaire.py:95-102`).
- **`enable_when.question` is a link_id**, resolved through `get_link_id_map` (`utils.py:134-160`).
- **Codes need a terminology server.** `code` is checked against LOINC through the valueset lookup (`spec.py:122`; `care/emr/registries/care_valueset/care_valueset.py:91-100`; `care/emr/resources/observation/valueset.py:5-14`). We left codes out. OHC's own form skill does too: "Do NOT add LOINC/SNOMED/UCUM `code` fields by default" (`skills@30f437f:care-form-skill/form.knowledge.md:21`).
- **Structured types:** `symptom`, `diagnosis`, `medication_statement`, `medication_request`, `allergy_intolerance`, `service_request`, `appointment`, `files`, `encounter`, `time_of_death`, `charge_item` (`form.knowledge.md:75-108`; registrations at `care/emr/api/viewsets/condition.py:100,153`, `medication_statement.py:53` and others).

**Limits that matter for us (all from code; runtime [unverified]):**
1. **No 0–10 range check.** An integer answer is only checked with `int(value)` (`utils.py:80-81`), and the frontend input has no min or max (`care_fe@81e7515:src/components/Questionnaire/QuestionTypes/NumberQuestion.tsx:46-54`). The other option is a choice question with options "0"…"10". That enforces the range, but the answers are stored as text.
2. **No body-site picker when filling a form.**
   - The form builder has a "collect body site" switch (`QuestionnaireEditor.tsx:2459`), and submit passes `response.body_site` along (`QuestionnaireForm.tsx:908`).
   - But no question component lets staff pick a body site, and the backend never copies `body_site` onto an Observation (no `body_site` in `care/emr/resources/questionnaire/utils.py`).
   - It survives only inside the stored response JSON (`utils.py:611`).
   - So body areas go in as a **choice question** in a repeating group.
3. **Observations need a code and an encounter.** They are created only for questions with a `code` (`utils.py:447`) and only when the response has an encounter (`utils.py:616`). Our patient-level, uncoded forms are stored as responses only. They can be read back, but they won't show in CARE's observation charts.
4. **Version is a string.** It defaults to `"1.0"` and is frozen (`spec.py:186`). OHC's form skill says to send `version` as a number and leave out `organizations` (`form.knowledge.md:65-66`). That conflicts with the spec at `2bae933`: pydantic 2 won't turn a number into a string, and organisations are required on create. The skill probably describes the in-app import path **[unverified]**. The JSON below follows the spec.

### 5.2 The three forms

How they were made:
- Generated by a Python script in the session scratchpad (not committed), from Sahara's own lists:
  - symptoms: `sahara@d0a278f:CareSymptomData.kt:28-36`;
  - body sites: `CarePainBodyData.kt:48-52`;
  - goals-of-care prompts: `DoctorPreparationQuestions.kt:28-33`.
- Checked against a copy of `QuestionnaireSpec` (the DB-backed checks were skipped) and against `validate_data`. Result: **all three pass**. A sample family answer passes too, and a score of 14 also passes (limit 1).
- IDs are deterministic UUID5s.
- `organizations` holds a placeholder: put the hospital's own organisation UUID there.
- Status is `draft`. A form must be `active` before anyone can submit to it (`utils.py:542-545`).

Design choices:
- **Patient-level subject.** Diary entries come between visits, and the playbook says to keep goals of care separate from acute encounter notes (`docs@40dbd25:deployments/palliative/playbooks/patient-enrollment.mdx:46`). The trade-off is no observation charts (limit 3).
- **Integer for each 0–10 score**, with the anchors in `description`. That keeps answers numeric. A hospital that wants the range enforced can switch to a 0–10 choice question.
- **The descriptions say "not ESAS-r or IPOS"**, as our symptom brief requires.
- **No exact 3D points.** Only area plus score, because CARE has no field for 3D points.

#### A. Family symptom diary (`sahara-symptom-diary`)

```json
{
  "slug": "sahara-symptom-diary",
  "version": "1.0",
  "status": "draft",
  "subject_type": "patient",
  "type": "custom",
  "title": "Family symptom diary (Sahara personal 0-10)",
  "description": "Personal 0-10 diary reported by the patient or family between visits. Not ESAS-r or IPOS. 0 = none, 10 = worst imaginable; for overall well-being 0 = best, 10 = worst.",
  "organizations": [
    "00000000-0000-4000-8000-000000000000"
  ],
  "questions": [
    {
      "id": "f3c373c9-9b83-521b-9fc2-78a98b303793",
      "link_id": "observed-at",
      "text": "When was this observed?",
      "type": "dateTime",
      "required": true
    },
    {
      "id": "0f42d4c6-9be1-5921-a54f-fd2f22d6d046",
      "link_id": "respondent",
      "text": "Who is reporting?",
      "type": "choice",
      "required": true,
      "answer_option": [
        {
          "value": "Patient"
        },
        {
          "value": "Patient, with help"
        },
        {
          "value": "Seen by family or carer"
        },
        {
          "value": "Seen by care team"
        }
      ]
    },
    {
      "id": "05802ea1-587b-5a90-9608-f72f860ae9bf",
      "link_id": "recall",
      "text": "Time window",
      "type": "choice",
      "required": true,
      "answer_option": [
        {
          "value": "Right now"
        },
        {
          "value": "24-hour average"
        },
        {
          "value": "Worst in 24 hours"
        }
      ]
    },
    {
      "id": "b6eb60b2-95d3-551b-82bb-d8a217721d33",
      "link_id": "symptoms",
      "text": "Symptoms (0-10, leave blank if not asked)",
      "type": "group",
      "styling_metadata": {
        "containerClasses": "grid grid-cols-2"
      },
      "questions": [
        {
          "id": "77d095c5-ef3c-5bfc-a4da-8c887de9bd9f",
          "link_id": "sym-pain",
          "text": "Pain (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "547fe4b9-cbf1-5fdb-bd89-614086a2d733",
          "link_id": "sym-tiredness",
          "text": "Fatigue / lack of energy (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "23c0cbbd-03d4-52e9-ab85-717c651f4a14",
          "link_id": "sym-sleepiness",
          "text": "Drowsiness / excessive sleepiness (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "9ac00aaa-21dd-5e25-b34f-fc6b2e2beddb",
          "link_id": "sym-poor-appetite",
          "text": "Poor appetite (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "c74f8cc2-28bd-5835-ab1c-e14026f14046",
          "link_id": "sym-dry-mouth",
          "text": "Dry / sore mouth (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "3147481c-8806-5a57-9a22-7fdda17bff8c",
          "link_id": "sym-breathlessness",
          "text": "Breathlessness (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "dc45b495-6419-5a4a-a362-ac36a4f500a3",
          "link_id": "sym-constipation",
          "text": "Constipation (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "d3e13846-a25e-5350-a0cf-084445fe0f6d",
          "link_id": "sym-diarrhea",
          "text": "Diarrhea (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "3882256b-2f76-59b9-bcb7-31b502ccbfa1",
          "link_id": "sym-nausea",
          "text": "Nausea (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "2fbd2e35-052b-5373-917f-0821a921f3b1",
          "link_id": "sym-vomiting",
          "text": "Vomiting (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "2c31b5d3-608d-517a-a5f0-c6893ff2346c",
          "link_id": "sym-difficulty-swallowing",
          "text": "Difficulty swallowing (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "ac047542-9f64-576e-8614-79e4c4b650e2",
          "link_id": "sym-cough",
          "text": "Cough (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "5fe718a1-f592-54c2-aee5-581a6f912fed",
          "link_id": "sym-bowel-bladder-incontinence",
          "text": "Bowel / bladder incontinence (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "dad291a2-e033-5472-ad1e-c79216b3fe71",
          "link_id": "sym-sadness",
          "text": "Sadness (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "9aced75a-ba41-5c46-9d06-722c56459292",
          "link_id": "sym-worry",
          "text": "Worry (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        },
        {
          "id": "db7efb67-94bf-5908-8add-56a600b68b83",
          "link_id": "sym-overall-well-being",
          "text": "Overall well-being (0-10)",
          "type": "integer",
          "description": "0 = best possible well-being, 10 = worst possible well-being"
        },
        {
          "id": "824de528-d212-54f1-8d1f-115805db3aa4",
          "link_id": "sym-other",
          "text": "Other (0-10)",
          "type": "integer",
          "description": "0 = none, 10 = worst imaginable"
        }
      ]
    },
    {
      "id": "b4b00140-7f55-560f-bae0-908c77a2ae5f",
      "link_id": "sym-other-name",
      "text": "Other symptom: what is it?",
      "type": "string",
      "enable_when": [
        {
          "question": "sym-other",
          "operator": "exists",
          "answer": true
        }
      ]
    },
    {
      "id": "b2d4301b-c619-51b6-a642-e6e588ddf911",
      "link_id": "change",
      "text": "Compared with last time",
      "type": "choice",
      "answer_option": [
        {
          "value": "Better"
        },
        {
          "value": "Same"
        },
        {
          "value": "Worse"
        },
        {
          "value": "Not sure"
        }
      ]
    },
    {
      "id": "c2b7da7c-b671-5e84-98e2-dffbfae0cc7f",
      "link_id": "note",
      "text": "Anything else the care team should know?",
      "type": "text"
    }
  ]
}
```

#### B. Pain by body area (`sahara-pain-body-map`)

```json
{
  "slug": "sahara-pain-body-map",
  "version": "1.0",
  "status": "draft",
  "subject_type": "patient",
  "type": "custom",
  "title": "Pain by body area (Sahara)",
  "description": "One row per painful area, each with its own 0-10 strength. The 12 areas match Sahara's body model (sahara-body-v1). Exact 3D points are not carried.",
  "organizations": [
    "00000000-0000-4000-8000-000000000000"
  ],
  "questions": [
    {
      "id": "cd08d20a-de06-5c60-a756-b839f5d3d627",
      "link_id": "pain-observed-at",
      "text": "When was this observed?",
      "type": "dateTime",
      "required": true
    },
    {
      "id": "5782f2fe-11d2-5c80-b612-18f61038c542",
      "link_id": "pain-respondent",
      "text": "Who is reporting?",
      "type": "choice",
      "required": true,
      "answer_option": [
        {
          "value": "Patient"
        },
        {
          "value": "Patient, with help"
        },
        {
          "value": "Seen by family or carer"
        },
        {
          "value": "Seen by care team"
        }
      ]
    },
    {
      "id": "b59bd529-af89-56c4-a5b9-48fc509cfd8c",
      "link_id": "pain-spot",
      "text": "Painful area",
      "type": "group",
      "repeats": true,
      "questions": [
        {
          "id": "a911e09a-407c-53c7-9601-b8a7aa5f1934",
          "link_id": "pain-site",
          "text": "Where?",
          "type": "choice",
          "required": true,
          "answer_option": [
            {
              "value": "Head"
            },
            {
              "value": "Neck"
            },
            {
              "value": "Chest"
            },
            {
              "value": "Abdomen"
            },
            {
              "value": "Upper back"
            },
            {
              "value": "Lower back"
            },
            {
              "value": "Left shoulder"
            },
            {
              "value": "Right shoulder"
            },
            {
              "value": "Left arm / hand"
            },
            {
              "value": "Right arm / hand"
            },
            {
              "value": "Left leg / foot"
            },
            {
              "value": "Right leg / foot"
            }
          ]
        },
        {
          "id": "f9999d6c-20c3-528a-9d82-38881ddfab3f",
          "link_id": "pain-rating",
          "text": "How strong is it here? (0-10)",
          "type": "integer",
          "required": true,
          "description": "0 = none, 10 = worst imaginable"
        }
      ]
    },
    {
      "id": "0ef85598-c171-5273-a461-b1e4d7153129",
      "link_id": "pain-triggers",
      "text": "When is it worse?",
      "type": "choice",
      "repeats": true,
      "answer_option": [
        {
          "value": "At rest"
        },
        {
          "value": "Turning in bed"
        },
        {
          "value": "Standing / walking"
        },
        {
          "value": "Coughing"
        },
        {
          "value": "Other"
        },
        {
          "value": "Not sure"
        }
      ]
    },
    {
      "id": "8ce60b76-dc7b-5661-b2a8-f0020f349789",
      "link_id": "pain-note",
      "text": "Describe the pain in your own words",
      "type": "text"
    }
  ]
}
```

#### C. Goals-of-care conversation (`sahara-goals-of-care-conversation`)

```json
{
  "slug": "sahara-goals-of-care-conversation",
  "version": "1.0",
  "status": "draft",
  "subject_type": "patient",
  "type": "custom",
  "title": "Goals-of-care conversation (Sahara prompts)",
  "description": "Clinician-led prompts; the clinician records the answers. Kept at patient level so it sits apart from acute encounter notes. Prompt wording is Sahara's default set and has no cited clinical source yet.",
  "organizations": [
    "00000000-0000-4000-8000-000000000000"
  ],
  "questions": [
    {
      "id": "72c72c2a-3c56-5b2f-8a10-28a8a0194443",
      "link_id": "goc-date",
      "text": "Date of conversation",
      "type": "date",
      "required": true
    },
    {
      "id": "d4a825d7-e825-5cdd-9076-15c2822b53ef",
      "link_id": "goc-present",
      "text": "People present (name and relationship)",
      "type": "string",
      "repeats": true
    },
    {
      "id": "548568b5-7447-5284-b5c7-e50cd13f486d",
      "link_id": "goc-sec-1",
      "text": "What matters",
      "type": "group",
      "questions": [
        {
          "id": "5f27f133-918e-5c07-96c8-e3b6ac35865f",
          "link_id": "goc-1.1",
          "text": "What matters most to you at the moment?",
          "type": "text"
        },
        {
          "id": "db326638-5578-5ade-9bec-2c3aade13061",
          "link_id": "goc-1.2",
          "text": "What are you hoping for from your care?",
          "type": "text"
        }
      ]
    },
    {
      "id": "3d67976d-f605-50f9-a375-e57f91a5a9c2",
      "link_id": "goc-sec-2",
      "text": "Who was there",
      "type": "group",
      "questions": [
        {
          "id": "592d7bfe-24f2-54b4-9a53-9085e9435071",
          "link_id": "goc-2.1",
          "text": "Who would you like involved in decisions about your care?",
          "type": "text"
        }
      ]
    },
    {
      "id": "e223728f-aed1-5a2d-857f-2622ae6d710a",
      "link_id": "goc-sec-3",
      "text": "What we discussed",
      "type": "group",
      "questions": [
        {
          "id": "201137cf-827a-5778-bffb-afeb4c96b6bb",
          "link_id": "goc-3.1",
          "text": "What is your understanding of your current health?",
          "type": "text"
        },
        {
          "id": "7ef7ecbd-f2e6-5bc4-a8d4-b0dc14a7a1d4",
          "link_id": "goc-3.2",
          "text": "What worries you most about the future?",
          "type": "text"
        }
      ]
    },
    {
      "id": "9e539091-7f9a-5098-ba10-6aecaf365ad2",
      "link_id": "goc-sec-4",
      "text": "Still to discuss",
      "type": "group",
      "questions": [
        {
          "id": "ba118653-f4a2-5a21-812d-7fa5b5da993a",
          "link_id": "goc-4.1",
          "text": "Is there anything you would like to talk about that we have not covered?",
          "type": "text"
        }
      ]
    },
    {
      "id": "0a741129-a813-58c1-bdd1-8779f8a39344",
      "link_id": "goc-sec-5",
      "text": "Next steps",
      "type": "group",
      "questions": [
        {
          "id": "01dea0cd-668f-5a16-bb2d-d639f1c8fe0e",
          "link_id": "goc-5.1",
          "text": "What would you like to happen next?",
          "type": "text"
        },
        {
          "id": "7c4ad18b-529c-5ccd-a6bd-84e4d99c93dd",
          "link_id": "goc-5.2",
          "text": "Who should hear about what we have agreed?",
          "type": "text"
        }
      ]
    },
    {
      "id": "9037aba4-6c64-563c-b55e-0f7355f0cb32",
      "link_id": "goc-family-reviewed",
      "text": "Has the family been shown the written summary?",
      "type": "choice",
      "answer_option": [
        {
          "value": "Not yet"
        },
        {
          "value": "Read and agreed"
        },
        {
          "value": "Read, has concerns"
        },
        {
          "value": "Not ready to read"
        }
      ]
    }
  ]
}
```

**A matching submission** (what the plugin's converter, or a staff user, would POST to `/api/v1/questionnaire/sahara-pain-body-map/submit/`, `care/emr/api/viewsets/questionnaire.py:115-153`). It follows `QuestionnaireSubmitRequest` (`care/emr/resources/questionnaire_response/spec.py:18-41`). A repeating group carries its rows in `sub_results`:

```json
{
  "resource_id": "<patient external_id>",
  "patient": "<patient external_id>",
  "results": [
    {"question_id": "cd08d20a-de06-5c60-a756-b839f5d3d627", "values": [{"value": "2026-10-27T09:30:00+05:30"}]},
    {"question_id": "5782f2fe-11d2-5c80-b612-18f61038c542", "values": [{"value": "Seen by family or carer"}]},
    {"question_id": "b59bd529-af89-56c4-a5b9-48fc509cfd8c", "sub_results": [
      [{"question_id": "a911e09a-407c-53c7-9601-b8a7aa5f1934", "values": [{"value": "Lower back"}]},
       {"question_id": "f9999d6c-20c3-528a-9d82-38881ddfab3f", "values": [{"value": "7"}]}],
      [{"question_id": "a911e09a-407c-53c7-9601-b8a7aa5f1934", "values": [{"value": "Left leg / foot"}]},
       {"question_id": "f9999d6c-20c3-528a-9d82-38881ddfab3f", "values": [{"value": "4"}]}]
    ]}
  ]
}
```

**What can't go in a questionnaire:**
- the 3D points;
- the per-participant recording consent;
- the family's confirmation of an exact note version with its hash.

They stay in Sahara, or in the `care_family` plugin tables (section 4.2).

**Body-site codes, for a later version.** CARE's body-site valueset is SNOMED `is-a 442083009` plus a list of foot and leg landmarks (`care@2bae933:care/emr/resources/observation/valueset.py:17-108`). We didn't map our 12 areas to SNOMED codes, because no SNOMED browser was reachable to check them. Do that before adding any `code`.

---

## 6. FHIR / ABDM

- **What care_abdm does.** We couldn't read the code (section 1). Public claims:
  - "ABDM Integration: M1, M2, M3 style workflows for demographic and biometric authentication through a plugin model" (`ohc.network__product__apps-integrations.txt:50-55`);
  - "ABDM M1–M3" (`ohc.network__developers.txt:27`);
  - "Enables ABHA number linking, consent management, and health information exchange" (`registry@7b528aa:apps/care-abdm.json`, served from `care-abdm.ohc.network`).
  - In ABDM terms, M1 is ABHA (the national health ID) creation and linking, M2 is HIP (the hospital shares records) and M3 is HIU (the hospital requests records). **[unverified]** which of these care_abdm actually implements today.
- **In core:**
  - `care_fe` shows "ABHA number" and "ABHA address" as placeholders, both "-" (`care_fe@81e7515:src/components/Patient/PatientDetailsTab/Demography.tsx:333-334`);
  - old migrations show legacy CARE once had an `abdm` app and a FileUpload type `ABDM_HEALTH_INFORMATION` (`care@2bae933:care/facility/migrations/0432_alter_fileupload_file_type.py:25`). The current FileUpload types don't include it (`care/emr/models/file_upload.py`);
  - the HMIS playbook's "ABDM link" step is "(flow coming soon)" (`docs@40dbd25:deployments/hmis/playbooks/outpatient-registration.mdx:69`).
- **Could Sahara make an ABDM bundle?** Yes, as a file. The ABDM FHIR guide (NRCeS) has a **WellnessRecord** profile for "regular wellness information of patients typically through the Patient Health Record (PHR) application". It has sections such as Vital Signs, Body Measurement and Physical Activity. There is also a **HealthDocumentRecord** profile for scanned documents ([NRCeS IG](https://nrces.in/ndhm/fhir/r4/StructureDefinition-WellnessRecord.html), [HealthDocumentRecord](https://nrces.in/ndhm/fhir/r4/StructureDefinition-HealthDocumentRecord.html); read through search snippets, not fetched). A Sahara diary would map to a WellnessRecord document bundle: a Composition first, then Patient and Observations.
- **Could a CARE+ABDM hospital receive it?** Not by a push, and not this year.
  - In ABDM, records move from an HIP to an HIU through the gateway after the patient approves a consent request. The data is encrypted (ECDH) per request ([Eka Care ABDM M2 skill summary](https://skillsmp.com/creators/eka-care/abdm-utility/skills-abdm-fhir); [ABDM dev forum](https://devforum.abdm.gov.in/t/how-to-display-fhir-bundle-coming-from-other-hips-into-our-hiu/8802)).
  - So Sahara would have to register with NHA (sandbox, then production) as an HIP or PHR app, and build discovery, linking and data-push APIs. The hospital would need care_abdm with HIU (M3) working.
  - Estimate: weeks to months, plus NHA approval. Kerala's own instance waited on NHA approval for months (blog, March and August 2025).
  - **Confidence: moderate** on the flow, **low** on what care_abdm does.
- **The pitch line:** "Sahara's records map to ABDM's WellnessRecord shape; linking to ABDM needs NHA registration and is on the roadmap." Don't say "ABDM-integrated".

---

## 7. Licensing, contribution, community, contacts, roadmap

- **Licences:**
  - `care` and `care_fe`: MIT (`LICENSE` line 1).
  - `care_filly`: MIT (2026, Open Healthcare Network).
  - `care_ai_vision_fe`: MIT in `package.json:19`, with no LICENSE file.
  - **`care_scaffold`, `skills` and `docs` have no LICENSE file.** Without one, reuse rights aren't granted, so ask OHC before copying their text or templates. The scaffold's *generated* backend template declares MIT (`templates/backend/setup.py:25,32`).
  - MIT lets Sahara reuse CARE code in a proprietary or open app, as long as we keep the copyright notice.
- **Contributing:** branch from `develop`, never `main`; frontend branch names follow `issues/{n}/{name}`; comment on an issue and wait to be assigned; Ruff via pre-commit on the backend, ESLint/Prettier on the frontend (`docs@40dbd25:contributing/workflow.md`; `care@2bae933:CONTRIBUTING.md`).
- **Security reports:** by email, not public issues (`care@2bae933:SECURITY.md`).
- **Community and contacts:**
  - Slack: `https://slack.ohc.network/` (in the repo READMEs);
  - support@ohc.network ([contact](https://ohc.network/contact));
  - public foundation roster: Bodhish Thomas (CEO) and Vignesh Hari (CTO) (`ohc.network__about__team__foundation.txt`);
  - registered office in Palarivattom, Kochi; CIN U88100KL2025NPL098818 (blog footer).
- **Roadmap items about patients or families:**
  - CARE AI lists "Caregiver instruction drafts" and "Care plan generation" (`ohc.network__product__care-ai.txt:185,258`), for staff to draft;
  - HMIS lists "patient-facing workflows" (`ohc.network__solutions__hospital-management.txt:209`);
  - the newest code change for plugins added sections on the *staff* user profile (September 2026 update).
  - **We found no published plan for a patient or family app.** We couldn't search GitHub issues (the API is blocked here).

## 8. Peers

- **PalliKare** (Karunashraya, Bangalore Hospice Trust): a symptom-control reference "for doctors and nurses". It isn't a family app, and it has no CARE or ABDM link ([IAPC](https://www.palliativecare.in/free-palliative-care-app-pallikare/), [IAHPC review](https://hospicecare.com/what-we-do/publications/newsletter/2021/02/app-review)).
- **Pallium India:** helpline and telehealth. Its tech post names symptom-tracking apps in general terms ([Pallium India](https://palliumindia.org/2024/02/transforming-palliative-care-with-technology)).
- **Research only, not products:** caregiver-app studies in India, Uganda and Zimbabwe ([UNCST record](https://nru.uncst.go.ug/handle/123456789/11910)), and Indian caregiver-role studies ([PMC9481001](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9481001/)).
- **No family or caregiver palliative app integrating with CARE or ABDM was found** (searched 27 Oct). Confidence moderate: search coverage of Play Store apps is weak.

---

## 9. Corrections to the earlier docs

| Earlier claim | Where | Correction |
|---|---|---|
| "3D pain body: Partly. CARE can attach one body part code to an answer." | `CARE_VS_SAHARA_FOR_TEAM.md` Table 1; `CARE_FAMILY_SIDE_FIT.md` 4.4 | True at the API level only. `care_fe` has no body-site picker when filling a form, and the backend doesn't copy `body_site` to an Observation; it stays in the response JSON (section 5.1, limit 2). Body areas need a choice question. |
| Questionnaire answers line up with "LOINC-coded observations" | `CARE_FAMILY_SIDE_FIT.md` section 1 | Observations exist only for coded questions answered **inside an encounter** (`utils.py:447,616`). Our uncoded, patient-level diary would be stored as responses only. |
| "Hindi / English: CARE has translation files. [unverified] which languages are complete." | `CARE_VS_SAHARA_FOR_TEAM.md` Table 1 | Now checked: `care_fe@81e7515:public/locale/` has en 6,856 keys, ml 2,852, hi 795, kn 796, ta 795, mr 94. **Hindi covers about 12% of staff screens. Malayalam covers about 42%.** |
| "The Kerala palliative features (care plans, caregiver registries) may come from plugins or deployment configuration we did not inspect." | `CARE_FAMILY_SIDE_FIT.md` 3.3 | No palliative plugin exists in the public registry or code. The Kerala grid is core plus questionnaires it built itself; OHC's blog names the MLSP and JAK home-care forms. Care plans aren't in code at all. |
| "A care_fe plugin can add staff screens … but not patient-portal screens" | `CARE_FAMILY_SIDE_FIT.md` 3.4 | Still right for `81e7515`, and stronger: the two patient-portal slots in OHC's own scaffold docs don't exist in care_fe today. The scaffold's `MiddlewareAuthentication` doesn't exist in core either. |
| P2 plugin "estimate 2–3 weeks" | `CARE_FAMILY_SIDE_FIT.md` section 5 | Now 15–20 dev-days, once stack setup and the Android client are counted (section 4.2). |
| Option B "FHIR-style bundle" could feed CARE | `CARE_BRIEF_2026-10-27.md` section 4 | Clarification, not an error: CARE has no FHIR inbound endpoint (already in the fit doc). An ABDM-shaped bundle can't be pushed to a CARE hospital either; ABDM delivery needs NHA registration (section 6). The cheap, real way into CARE is **its own questionnaire format** (section 5). |
| "Patient self-registration + booking" implies a usable patient profile | `CARE_VS_SAHARA_FOR_TEAM.md` Table 2 | The portal's `/patient/:id` page calls a staff-only API and likely fails for a patient token **[unverified]**. The working parts are registration and appointments. |
| OHC "Patient Accounts" (Jul 2025) | (risk, not yet claimed) | That is billing, not a patient login. Never cite it as patient access. |
| Sahara commit `7abf52e` | all three docs | The repo is now at `d0a278f`. The symptom, body-site and question lists used here were re-read at `d0a278f`. |

---

## 10. Updated recommendation

**Strongest counterargument first.**
- Judges may reward a live CARE demo more than files.
- OHC offered plugin help, and our three form files are only "CARE-shaped" until a running CARE server accepts them. We haven't run one.

We still don't recommend a plugin before 7 Nov:
- it is 3–4 weeks of work;
- it needs a running CARE stack;
- it needs a partner hospital willing to install it;
- and there are 11 days left.

**Recommendation:**
1. **Before 7 Nov (about 1 day):**
   - Slide 11 text: "Built to CARE's own record types; ships CARE questionnaire definitions for its symptom diary, pain map and goals-of-care prompts; family entries would enter a CARE hospital through a staff-reviewed plugin."
   - Add the three JSON files in section 5 to the repo or an appendix. Mark them "validated against CARE's form rules by code reading; not yet loaded into a CARE server".
   - Don't claim "integrated", "FHIR" or "ABDM".
2. **If 2–3 days remain:** the export file from `CARE_FAMILY_SIDE_FIT.md` P1. Make the diary export emit `QuestionnaireSubmitRequest` bodies keyed to the UUIDs above, so a staff user or script can POST them.
3. **After 7 Nov, with a named CARE unit:**
   - stand up the scaffold stack and load the three forms (half a day; this checks section 5 at runtime);
   - then build `care_family` (section 4.2);
   - ask OHC on Slack about: the Kerala questionnaires export, licences for scaffold and skills, and whether the patient-portal plugin slots are planned.
4. **ABDM:** later. Only after a pilot, and only through NHA registration.

**Confidence:**
- high on what the code contains;
- moderate on the recommendation;
- low on the effort figures and on care_abdm (unread).
