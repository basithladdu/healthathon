# findings-docs5 (docs.ohc.network flows, part 5: files, notes, patient, questionnaire-response, service-request, specimen, definitions, department, facility, location)

All 60 URLs fetched (HTTP 200, first try). All pages are "Version: 3.1". Read-only; no login/forms.
Compared against CARE_DEEP_DIVE_2026-10-27.md, CARE_VS_SAHARA_FOR_TEAM.md, CARE_FAMILY_SIDE_FIT.md, CARE_BRIEF_2026-10-27.md.

## 1. NEW or CONTRADICTS earlier docs (most important first)

1. **PARTLY CONTRADICTS deep-dive point 3 ("A hospital can build our forms in CARE today, with no code").** The UI flow says creating/editing questionnaires is a *Care superuser* (instance-level) action, not a hospital/facility-admin one. Deep dive only cites the API permission (can_write_questionnaire per organisation). A Kerala hospital would need the instance operator to create our three forms in the UI (or a user with API rights).
   - "You are a Care superuser. Care shows the Admin Dashboard button only to superusers." — https://docs.ohc.network/flows/definitions/questionnaire/create-questionnaire
   - "If you change a saved questionnaire, you are a Care superuser. Only superusers can change a saved questionnaire." — https://docs.ohc.network/flows/definitions/questionnaire/add-and-edit-questions
   - Same for valuesets (custom coded answer lists, e.g. a body-area list): "None (Care superuser only) Lets you create a new valueset." — https://docs.ohc.network/flows/definitions/valueset/create-valueset
   - Clone is the softer path: copy starts "in Draft status" and needs only "Can Create/Update Questionnaires ... in each organization that you select" — https://docs.ohc.network/flows/definitions/questionnaire/clone-questionnaire
2. **Clinical Notes (staff discussions) are thinner than "staff chat threads" implies** (CARE_VS_SAHARA Table: "Notes / discussions ... Partly").
   - "Care does not support file attachments. Care does not support mentions of other users." — https://docs.ohc.network/flows/clinical/notes/post-a-message
   - "If Care cannot send the message, Care shows no error." / "Care shows no way to delete a message." — same page
   - "Care shows no search box and no filter for discussions." and patient vs encounter lists "never overlap" — https://docs.ohc.network/flows/clinical/notes/find-discussions
   - Suggested thread titles: "Treatment Plan, Medication Notes, Care Coordination, General Notes, Patient History, Referral Notes, Lab Results Discussion"; "Care shows no way to delete a discussion" — https://docs.ohc.network/flows/clinical/notes/start-a-discussion
   - Read access to patient-level discussions includes Staff: "Can view clinical data about patients ... Staff, Doctor, Nurse, Admin, and Facility Admin". No Volunteer role listed. (Implication: "Treatment Plan" thread is the nearest thing to a care plan in the UI = free-text only, consistent with "no care plan".)
3. **Patient profile has a "Volunteer Contact" tab and a "Social Profile" tab** (not named in any earlier doc). Confirms FAMILY_SIDE_FIT ("patient users are staff") — the field supporter is a CARE account, not a family member.
   - "Volunteer Contact The users assigned to support this patient in the field, for example volunteers." Also "Social Profile The patient's social information." and "Insurance Details" — https://docs.ohc.network/flows/clinical/patient/view-patient-profile
   - "Search for a user and assign a role to add them to the patient." — https://docs.ohc.network/flows/clinical/patient/manage-patient-users
4. **File limits and capture modes (new detail).** Fits Sahara audio/photo export.
   - "The file is not larger than 100 MB." / types: "image, a video, an audio file, a text file, an office document, or a PDF" — https://docs.ohc.network/flows/clinical/files/upload-a-file
   - "Upload From Device / Open Camera / Record Audio" ... "Care saves the recording as an mp3 file." ... "Combine Files To PDF" for 2+ images — same page
   - Upload needs "Write access to the patient" (staff); encounter upload blocked if encounter inactive.
   - Preview: ".mov video does not play in a browser other than Safari"; Word/Excel need download — https://docs.ohc.network/flows/clinical/files/view-or-preview-a-file
5. **Questionnaire responses are immutable; correction = "entered in error" within a deployment time limit.** Relevant to how Sahara family submissions/corrections would land.
   - "You mark the response inside the time limit that your deployment sets. After that time, only a system administrator can mark the response." "You cannot change the response. You cannot remove the mark" — https://docs.ohc.network/flows/clinical/questionnaire-response/mark-a-response-as-entered-in-error
   - Patient-level responses live in the patient "Updates" tab: "The Updates tab shows an Add Patient Updates button if you have permission to submit patient-level forms." Clinical History aggregates "responses across all encounters" — https://docs.ohc.network/flows/clinical/questionnaire-response/view-questionnaire-responses
   - Print: "Print all responses ... Every response to the same form" → print view "Questionnaire Response Logs" (a symptom-diary printout path) — https://docs.ohc.network/flows/clinical/questionnaire-response/print-a-questionnaire-response
6. **Patient search/verification by phone + optional year-of-birth check** (new UI detail; relevant to family verification design).
   - "Search for existing patients using their phone number"; "Care shows limited information for a partial match. This protects the patient's privacy."; "Please enter the patient's year of birth to verify their identity." (if configured) — https://docs.ohc.network/flows/clinical/patient/search-patient
   - Duplicate warning: "It appears that there are patient records that contain the same phone number" — same page
7. **Registration required fields (UI):** Name, Phone (required), Gender ("Male, Female, Non-binary, or Transgender"), DOB or Age, Geo Organization (required); address/PIN optional in UI; "Deployment can configure fields which are mandatory and non mandatory." — https://docs.ohc.network/flows/clinical/patient/register-patient. Mild tension with FAMILY_SIDE_FIT line 126 (OTP create requires address, pincode) — that is the OTP API path; the staff UI marks PIN optional.
8. **Recording death (palliative end-of-life):** "Patient is deceased" checkbox + date/time; "must not be in the future"; profile shows "Passed away on {date}, at {time}" — https://docs.ohc.network/flows/clinical/patient/mark-patient-deceased. Odd registration note: "Deceased status should be marked only for patients who are brought dead." — https://docs.ohc.network/flows/clinical/patient/register-patient. No bereavement/family follow-up step mentioned.
9. **Patient identifiers are deployment-configured** (required/unique/format-validated/facility-or-instance scope/auto-generated) — e.g. "A national ID and a facility-specific ID" — https://docs.ohc.network/flows/clinical/patient/manage-patient-identifiers. No ABHA/ABDM named on this page. Supports FAMILY_SIDE_FIT idea of carrying Sahara id as an identifier, but it would need an admin-configured identifier type.
10. **Observation definitions: category "Survey" + qualified ranges keyed on patient tags** (new). Could flag e.g. pain >=7 as "Critical" — but only for observations, which deep dive says patient-level uncoded forms don't produce.
    - Categories: "Social History, Vital Signs, Imaging, Laboratory, Procedure, Survey, Exam, Therapy, or Activity"; "LOINC Code ... Required" — https://docs.ohc.network/flows/definitions/observation-definition/create-observation-definition
    - "Metric ... Patient Age, Patient Gender, Encounter Class, Patient Tags, or Encounter Tags"; ranges "Normal, Abnormal, or Critical" with highlight — https://docs.ohc.network/flows/definitions/observation-definition/set-up-qualified-ranges
11. **Service requests / activity definitions include Counselling and Education** (possible home-care/counselling orders), but need an open encounter and staff roles. "Category ... Laboratory, Imaging, Surgical Procedure, Counselling, or Education" — https://docs.ohc.network/flows/definitions/activity-definition/create-activity-definition; "The encounter is still open. Care hides the create option for a completed or an inactive encounter." — https://docs.ohc.network/flows/clinical/service-request/create-a-service-request. Linked charge items auto-created (billing).
12. **Response templates** (saved medication + service-request bundles, shareable per department; hard delete) — "A template adds its medications and its service requests to the form." — https://docs.ohc.network/flows/definitions/questionnaire-response-template/apply-response-template; "A response template has no retired state" — .../update-or-delete-response-template. Useful for standard palliative regimens (e.g. morphine + laxative) on the staff side only.
13. **Service accounts can be linked into departments** (complements FAMILY_SIDE_FIT line 109): "To link a service account, select the Service accounts tab. Then select Link Service Account." Also "You cannot give a member a role that carries more permissions than your own" — https://docs.ohc.network/flows/facility/department/add-users-to-department
14. **Public facility directory without sign-in** lists bookable users: "Users The users that you can book an appointment with at this facility"; "Make this facility public ... Anyone can browse this directory without a sign-in." — https://docs.ohc.network/flows/facility/facility/view-facility, https://docs.ohc.network/flows/facility/facility/create-facility. Consistent with "patient login is booking-only".
15. **Translation gap evidence:** "The three reason labels in this dialog show in English only. The current build does not translate them" — https://docs.ohc.network/flows/clinical/specimen/discard-a-specimen (minor; shows i18n is incomplete in places).
16. Deployment-level limits: max department nesting depth and max departments per facility — https://docs.ohc.network/flows/facility/department/create-department. Facility delete = Super Admin, permanent — https://docs.ohc.network/flows/facility/facility/delete-facility.

Nothing in this part on: FHIR/ABDM endpoints, consent, caregiver/family accounts, pricing/hosting, licences, contacts, roadmap, Kerala deployments, plugins.

## 2. Page log

| URL | fetched? | summary | relevant? |
|---|---|---|---|
| https://docs.ohc.network/flows/clinical/files/upload-a-file | y | Upload/camera/audio(mp3), 100 MB, combine to PDF | y |
| https://docs.ohc.network/flows/clinical/files/view-or-preview-a-file | y | Files tab, preview per type, archived filter | y |
| https://docs.ohc.network/flows/clinical/notes/find-discussions | y | Patient vs encounter thread lists, no search | y |
| https://docs.ohc.network/flows/clinical/notes/post-a-message | y | Markdown msgs, no attachments/mentions/delete | y |
| https://docs.ohc.network/flows/clinical/notes/start-a-discussion | y | Thread titles incl. Treatment Plan, Care Coordination | y |
| https://docs.ohc.network/flows/clinical/patient/manage-patient-identifiers | y | Admin-configured identifier types | y |
| https://docs.ohc.network/flows/clinical/patient/manage-patient-tags | y | Tags on patient | y |
| https://docs.ohc.network/flows/clinical/patient/manage-patient-users | y | Assign CARE user w/ role to support patient in field | y |
| https://docs.ohc.network/flows/clinical/patient/mark-patient-deceased | y | Deceased checkbox + date/time | y |
| https://docs.ohc.network/flows/clinical/patient/register-patient | y | Registration fields, required set configurable | y |
| https://docs.ohc.network/flows/clinical/patient/search-patient | y | Phone search, partial-match privacy, YOB verify | y |
| https://docs.ohc.network/flows/clinical/patient/update-patient-details | y | Edit fields same as registration | y |
| https://docs.ohc.network/flows/clinical/patient/view-patient-appointments | y | Patient Home, appointments/tokens quick actions | n |
| https://docs.ohc.network/flows/clinical/patient/view-patient-profile | y | Profile tabs incl. Volunteer Contact, Social Profile | y |
| https://docs.ohc.network/flows/clinical/questionnaire-response/mark-a-response-as-entered-in-error | y | Irreversible, deployment time limit | y |
| https://docs.ohc.network/flows/clinical/questionnaire-response/print-a-questionnaire-response | y | Print one/all responses | y |
| https://docs.ohc.network/flows/clinical/questionnaire-response/view-questionnaire-responses | y | Updates tab, encounter Responses, Clinical History | y |
| https://docs.ohc.network/flows/clinical/service-request/cancel-a-service-request | y | Revoke / entered in error, billing abort | n |
| https://docs.ohc.network/flows/clinical/service-request/complete-a-service-request | y | Complete with note | n |
| https://docs.ohc.network/flows/clinical/service-request/create-a-service-request | y | Order from open encounter; priority etc. | y (weak) |
| https://docs.ohc.network/flows/clinical/service-request/view-service-requests | y | List/detail; Volunteer can read | n |
| https://docs.ohc.network/flows/clinical/specimen/collect-a-specimen | y | Lab collection, QR | n |
| https://docs.ohc.network/flows/clinical/specimen/discard-a-specimen | y | Discard reasons, English-only labels | n |
| https://docs.ohc.network/flows/clinical/specimen/process-a-specimen | y | Processing steps | n |
| https://docs.ohc.network/flows/clinical/specimen/view-specimen-history | y | Rejected specimen history | n |
| https://docs.ohc.network/flows/definitions/activity-definition/create-activity-definition | y | Service templates incl. Counselling/Education | y (weak) |
| https://docs.ohc.network/flows/definitions/activity-definition/link-requirements-to-activity-definition | y | Link specimen/observation/charge defs | n |
| https://docs.ohc.network/flows/definitions/activity-definition/update-or-retire-activity-definition | y | Retire, not delete | n |
| https://docs.ohc.network/flows/definitions/activity-definition/use-activity-definition-to-order-a-service | y | Order via template; charge items | n |
| https://docs.ohc.network/flows/definitions/charge-item-definition/create-charge-item-definition | y | Priced templates, taxes, discounts | n |
| https://docs.ohc.network/flows/definitions/charge-item-definition/update-or-retire-charge-item-definition | y | Retire | n |
| https://docs.ohc.network/flows/definitions/observation-definition/create-observation-definition | y | Categories incl. Survey; LOINC required | y |
| https://docs.ohc.network/flows/definitions/observation-definition/set-up-qualified-ranges | y | Interpretation rules by age/gender/tags | y |
| https://docs.ohc.network/flows/definitions/observation-definition/update-or-retire-observation-definition | y | Retire | n |
| https://docs.ohc.network/flows/definitions/questionnaire-response-template/apply-response-template | y | Apply med/SR template to form | y |
| https://docs.ohc.network/flows/definitions/questionnaire-response-template/create-response-template | y | Save template, share with departments | y |
| https://docs.ohc.network/flows/definitions/questionnaire-response-template/update-or-delete-response-template | y | Edit/hard delete | n |
| https://docs.ohc.network/flows/definitions/questionnaire/add-and-edit-questions | y | 14 question types; superuser to edit saved | y |
| https://docs.ohc.network/flows/definitions/questionnaire/clone-questionnaire | y | Clone to orgs, Draft | y |
| https://docs.ohc.network/flows/definitions/questionnaire/create-questionnaire | y | Superuser only; subject Patient/Encounter | y |
| https://docs.ohc.network/flows/definitions/questionnaire/manage-questionnaire-organizations | y | Org visibility of forms | y |
| https://docs.ohc.network/flows/definitions/specimen-definition/create-specimen-definition | y | Lab specimen templates | n |
| https://docs.ohc.network/flows/definitions/specimen-definition/update-or-retire-specimen-definition | y | Retire | n |
| https://docs.ohc.network/flows/definitions/valueset/build-include-and-exclude-rules | y | LOINC/SNOMED/UCUM rules; superuser | y (weak) |
| https://docs.ohc.network/flows/definitions/valueset/create-valueset | y | Superuser only | y |
| https://docs.ohc.network/flows/definitions/valueset/preview-valueset | y | Preview before save | n |
| https://docs.ohc.network/flows/definitions/valueset/update-or-retire-valueset | y | No delete; system-defined sets | n |
| https://docs.ohc.network/flows/facility/department/add-users-to-department | y | Members, roles, service accounts | y (weak) |
| https://docs.ohc.network/flows/facility/department/create-department | y | Dept/Team; deployment limits | n |
| https://docs.ohc.network/flows/facility/department/delete-department | y | Constraints | n |
| https://docs.ohc.network/flows/facility/department/update-department | y | Edit dept | n |
| https://docs.ohc.network/flows/facility/facility/create-facility | y | Facility form; public directory toggle | y (weak) |
| https://docs.ohc.network/flows/facility/facility/delete-facility | y | Super Admin, permanent | n |
| https://docs.ohc.network/flows/facility/facility/update-facility-details | y | Edit facility | n |
| https://docs.ohc.network/flows/facility/facility/view-facility | y | Public directory w/ bookable users | y (weak) |
| https://docs.ohc.network/flows/facility/location/create-location | y | Buildings/wards/beds | n |
| https://docs.ohc.network/flows/facility/location/delete-location | y | Constraints | n |
| https://docs.ohc.network/flows/facility/location/edit-location | y | Edit | n |
| https://docs.ohc.network/flows/facility/location/manage-location-organizations | y | Dept access to location | n |
| https://docs.ohc.network/flows/facility/location/reorder-locations | y | Reorder | n |
