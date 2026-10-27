# CARE docs crawl, part 3 (concepts, access governance)

_Research agent report, 27 Oct 2026. Unedited except for this header._

docs3: I read all 60 pages and none failed. The harness blocked writing `findings-docs3.md`, so the full findings are below instead of in that file.

All 60 URLs in `docs-part-3` returned HTTP 200 on the first try (fetched 27 Oct with curl -sL and a 1 s delay), so no retries were needed. Every page shows the banner "Version: 3.1". The raw HTML and stripped text are in `(scratch path)`. I compared everything against the four earlier CARE docs, which are mostly code readings. Where the docs and the code disagree, the conflict is unresolved: nobody has run a CARE server.

## Top 5 new findings
1. **Questionnaire slug limit: the docs say 25 characters, the deep dive says 50.** The docs say "The slug must have 5 to 25 characters" (https://docs.ohc.network/concepts/definitions/questionnaire). CARE_DEEP_DIVE section 5 says "5–50 characters" from the code (`slug_type.py`). Our draft slug `sahara-goals-of-care-conversation` is 33 characters. It passes the code limit and fails the documented one. Shorten it to 25 or fewer (for example `sahara-goc-conversation`, 23 characters). `sahara-symptom-diary` and `sahara-pain-body-map` are 20 characters each, which is fine.
2. **Only a superuser can load or change forms in the UI.** The docs say "The Admin Dashboard button opens the questionnaire screens. Care shows this button only to superusers… To change a saved questionnaire, you must be a superuser." Also: "Only a superuser can create, change, or retire a valueset" (…/definitions/questionnaire, …/definitions/valueset). Forms can't be deleted, only set to Retired. So "a CARE hospital can take our forms" really means the instance superuser has to import them. The deep dive only mentions the per-organisation `can_write_questionnaire` permission.
3. **The docs claim more than the code supports on Observations.** The docs say "each answer on the form becomes an observation" (…/clinical/observation). Our code reading (deep dive section 8) says only coded answers given inside an encounter become Observations. Keep the code-based claim and don't quote this docs line.
4. **CARE itself says it does not fully export FHIR.** On questionnaire responses: "Care does not export a fully validated FHIR QuestionnaireResponse resource". Files: "no formal FHIR R5 resource mapping". Clinical Notes are not mapped to FHIR. The care team "does not match the standalone FHIR CareTeam resource" (…/clinical/questionnaire-response, /files, /notes, /care-team). This strengthens the advice not to claim "FHIR integration".
5. **A consent can carry attached files, which gives a real place for a confirmed goals-of-care document.** The consent page lists "Supporting Documents… for example a scanned signed consent form". Files can be attached to a Patient, Encounter, Consent, Diagnostic Report or Service Request. The limit is 100 MB per file, audio can be recorded in the browser, and files can only be archived, never deleted (…/clinical/consent, …/clinical/files). Staff could attach Sahara's confirmed-note PDF to an Advance Care Directive consent. The fit doc listed the file upload flow as unknown.

## Other new findings
- **Consent:** each consent belongs to one encounter. Category, decision, given-on date and validity are fixed once recorded; only Status and Note can change. The record and change buttons show only on an active encounter opened from the facility. Recording needs a role of Admin, Doctor, Nurse or Facility Admin. The comfort-care and verification gaps are unchanged.
- **MedicationStatement:** source is Patient, Practitioner or Related Person, defaulting to Patient and fixed after saving. Dosage instructions and start date are mandatory. Reason is limited to 100 characters. After saving, only Status and Note can change, and an open encounter is required. Sahara's medicine export must always include a dosage string.
- **Report templates:** the types are Discharge Summary, Patient Summary and Account Report. The body is written in Jinja2 and output as PDF or HTML. The Volunteer and Staff roles can generate reports. This is CARE's nearest thing to a document handed to a family, but staff produce it. **[unverified]** whether the encounter screen shows only Discharge Summary templates.
- **Patient record:** it has a "Volunteer Contact" field ("users that a facility assigns to the patient for field support"; these are CARE users), Patient Tags and configurable identifiers. The documented record has no caregiver field, which matches the fit doc.
- **Tags:** tags can be attached to Patients and other records, and one category is "Advance Directive". Child tags under one parent are mutually exclusive. A deployment could use this to flag palliative or goals-of-care status.
- **Encounters:**
  - By default a patient can have at most 5 open encounters at a facility.
  - Completed, Cancelled, Discontinued and Entered in error lock an encounter.
  - The default restart window is 0 hours, so a completed encounter can't be reopened.
  - "Hospice" is a discharge disposition.

  Any imported data has to land on an open encounter, for example a Home Health one.
- **Condition:** a symptom needs a SNOMED term and an active encounter. Severity has only Mild, Moderate and Severe, so a 0–10 pain score loses detail if mapped there. Onset date can't be changed after saving.
- **Observation Definition:** it has a "Survey" category and normal/abnormal interpretation rules, and a LOINC code is required. A pain score of 7 or more could in principle be flagged this way. **[unverified]** whether these definitions apply to questionnaire answers.
- **Clinical Notes:** threads at patient level and encounter level, staff only, permanent, with no attachments.
- **Questionnaire responses:** marking one "Entered in Error" is allowed only within a time limit the deployment sets; after that only a system administrator can. Patient-level responses show in the patient's "Updates" tab, which is where a Sahara diary submission would appear.
- **Roles:** only a super administrator can create custom roles. System roles can't be edited, only cloned, and you can't grant permissions you don't hold yourself. A cloned "caregiver" role would still need a full CARE user account.
- **"Responsibilities":** a third access context alongside facilities and government organisations, kept as a flat list of groups and created by a super administrator. Opening one can lead straight to a Patients list. It might model programme teams such as a district palliative team **[unverified]**; the docs never say "palliative".
- **Public listings:** a facility can be marked public so it appears in a public facility directory, and a schedule can be marked public for patient booking. This is relevant to Sahara's centre directory.
- **Billing:** the payer is a Patient or an Insurer, with one open account per patient per facility. This confirms that "Patient Accounts" means billing, not a login.

## Page log (URL | fetched | summary | relevant)
All URLs start with https://docs.ohc.network/. All were fetched (200).

**Billing**
- concepts/billing/account | yes | billing account, one open per patient per facility | n
- concepts/billing/charge-item | yes | billable item | n
- concepts/billing/invoice | yes | invoice | n
- concepts/billing/payment-reconciliation | yes | payments; payer Patient or Insurer | n

**Clinical**
- concepts/clinical/allergy-intolerance | yes | SNOMED allergy, FHIR-mapped | n
- concepts/clinical/care-team | yes | staff list on an encounter; not FHIR CareTeam | y
- concepts/clinical/condition | yes | SNOMED symptoms, 3 severity levels, needs active encounter | y
- concepts/clinical/consent | yes | categories, known gaps, supporting files, fields fixed after creation | y
- concepts/clinical/diagnostic-report | yes | lab report; locks at Final | n
- concepts/clinical/encounter | yes | Home Health class, lock statuses, max 5 open, Advance Directive tags | y
- concepts/clinical/files | yes | attachments, 100 MB, audio, archive-only, no FHIR | y
- concepts/clinical/notes | yes | staff discussion threads, permanent | y
- concepts/clinical/observation | yes | says "each answer becomes an observation" (conflicts with code) | y
- concepts/clinical/patient | yes | Volunteer Contact, tags, identifiers; no caregiver field | y
- concepts/clinical/questionnaire-response | yes | Updates tab, void time limit, not full FHIR | y
- concepts/clinical/service-request | yes | orders | n
- concepts/clinical/specimen | yes | lab specimens | n

**Definitions**
- concepts/definitions/activity-definition | yes | orderable service template | n
- concepts/definitions/charge-item-definition | yes | pricing template | n
- concepts/definitions/observation-definition | yes | Survey category, LOINC required, interpretation rules | y
- concepts/definitions/product-knowledge | yes | drug/consumable catalogue | n
- concepts/definitions/questionnaire | yes | slug 5–25 chars, superuser-only screens | y
- concepts/definitions/questionnaire-response-template | yes | saved medicine and order presets | n
- concepts/definitions/specimen-definition | yes | specimen template | n
- concepts/definitions/valueset | yes | code lists; superuser-only edits | y

**Facility**
- concepts/facility/ | yes | public directory flag | y
- concepts/facility/department | yes | department hierarchy | n
- concepts/facility/device | yes | device register | n
- concepts/facility/healthcare-service | yes | service offerings | n
- concepts/facility/location | yes | location hierarchy (includes House) | n

**Medications**
- concepts/medications/medication-administration | yes | doses given by staff | n
- concepts/medications/medication-dispense | yes | pharmacy dispense | n
- concepts/medications/medication-request | yes | prescriptions; Community category | y
- concepts/medications/medication-statement | yes | Related Person source, mandatory dosage, reason ≤100 chars | y

**Platform**
- concepts/platform/report-template | yes | Jinja2 Patient Summary, PDF or HTML | y
- concepts/platform/tagging | yes | Patient tags, Advance Directive category | y

**Scheduling**
- concepts/scheduling/booking | yes | appointment lifecycle | n
- concepts/scheduling/exception | yes | unavailability periods | n
- concepts/scheduling/schedule | yes | public schedules | y
- concepts/scheduling/token | yes | queue tokens | n

**Supply**
- concepts/supply/inventory-item | yes | computed stock | n
- concepts/supply/product | yes | stocked product | n
- concepts/supply/purchase-delivery | yes | stock from vendor | n
- concepts/supply/purchase-order | yes | order to vendor | n
- concepts/supply/supply-delivery | yes | internal stock move | n
- concepts/supply/supply-request | yes | internal stock request | n

**Access governance flows**
- flows/access-governance/access-control/assign-role-to-user | yes | can't grant more than you hold | y
- flows/access-governance/access-control/change-or-remove-user-role | yes | change or remove a role | n
- flows/access-governance/access-control/create-role | yes | super-admin custom roles | y
- flows/access-governance/access-control/edit-or-clone-role | yes | clone system roles | y
- flows/access-governance/access-control/view-permissions-matrix | yes | read-only matrix | n
- flows/access-governance/organization/create-organization | yes | superuser only | n
- flows/access-governance/organization/delete-organization | yes | superuser only | n
- flows/access-governance/organization/update-organization | yes | superuser only | n
- flows/access-governance/organization/view-organization-hierarchy | yes | browse organisation tree | n
- flows/access-governance/responsibility/create-responsibility | yes | flat responsibility groups | y
- flows/access-governance/responsibility/link-responsibilities | yes | oversight links | n
- flows/access-governance/responsibility/manage-responsibility-users | yes | designations; can create accounts | n
- flows/access-governance/responsibility/view-your-responsibilities | yes | opens Users or Patients list | y
- flows/access-governance/supplier/create-supplier | yes | vendor organisation | n

I did not edit the repo.
