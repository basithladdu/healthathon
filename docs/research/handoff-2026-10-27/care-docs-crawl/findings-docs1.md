# CARE docs crawl, part 1 (3.0 docs)

_Research agent report, 27 Oct 2026. Unedited except for this header._

## docs1 (id=docs1): `docs-part-1`, 60 docs.ohc.network pages

**Result:** I read all 60 pages and none failed (all HTTP 200, 1 s delay, retry was never needed). I could not write the findings file the brief asked for (`(scratch path)`): the harness refused it ("Subagents should return findings as text"). The full content is below instead. The raw HTML and extracted text are saved as `(scratch path)` and `NN.txt`, where NN is the line number in the URL list. The fetch log is `raw-docs1/log.txt`. Nothing was edited in the repo. The 4 billing reference pages (47–50) were read in part and searched for relevant terms; all other pages were read in full.

**What matters first: these are older docs.** Every URL in this list is under `/3.0/`. The unversioned docs that the earlier research quoted are now **"Version: 3.1"**. I fetched `https://docs.ohc.network/concepts/clinical/consent/` once to confirm this. So these 60 pages are an older snapshot. Where they differ from 3.1 or from the code, 3.1 and the code win.

### Top 5 new findings
1. **Version difference on the consent gap.** The 3.0 consent page lists "Comfort care — A comfort or palliative care directive" as an ordinary category, with no "known gap" note (https://docs.ohc.network/3.0/concepts/clinical/consent). The 3.1 page still says "the consent form has no option for it… This is a known gap in the product." Always cite the unversioned (3.1) URLs, never `/3.0/`.
2. **A hospital might give relatives narrow CARE access without a plugin.** Unverified at runtime.
   - Custom roles are allowed: "'Doctor', 'Doctor Read Only' and 'Doctor Scheduler' are three unrelated permission sets", created through `RoleCreateSpec` (https://docs.ohc.network/3.0/references/access-governance/role).
   - Patients can have their own users: "PatientUser — Grants a Care user access to a patient record… role → FK RoleModel" (https://docs.ohc.network/3.0/references/clinical/patient).
   - Patient-scoped roles resolve PATIENT/FACILITY-context permissions (permission-association reference).
   - Limits: each relative needs a full CARE User with a phone number that "must not already exist on any user" and a required gender (user reference), and they would use the staff web UI.
3. **The Volunteer role can already submit patient-level forms (no encounter) and read their answers.** These are the same kind of forms as Sahara's three CARE form drafts.
   - "can_submit_patient_questionnaire … Volunteer, Staff, Doctor, Nurse…" (https://docs.ohc.network/3.0/concepts/definitions/questionnaire).
   - "can_view_questionnaire_responses … when the response is not linked to an encounter — Volunteer…" (https://docs.ohc.network/3.0/concepts/clinical/questionnaire-response).
   - The organisation type `role` is described as "e.g. Volunteer, ASHA Worker" (organization reference).
4. **An Observation can record a family member as the person who did it.** This contradicts `CARE_FAMILY_SIDE_FIT.md` §4.4, which says it is always a CARE user.
   - "Performer { type: PerformerType (related_person|user), id: str }. Use it when the performer differs from the data-entry user" (https://docs.ohc.network/3.0/references/clinical/observation).
   - The answer-submission format has no performer field, so it is probably set only from code, such as a plugin.
5. **CARE has a built-in draft stage for form answers.** The earlier `care_family` blueprint missed it and invented its own pending status.
   - "FormSubmission — A submission envelope (draft → submitted) holding a raw response dump"; its `response_dump` is opaque JSON (https://docs.ohc.network/3.0/references/clinical/questionnaire-response).
   - It could hold family entries until staff finalise them. Who may create these drafts is unverified.

### Other new or contradicting items (exact quotes; URLs are https://docs.ohc.network/3.0/… unless noted)
6. **The docs contradict themselves on Observations.** The Observation concept says "each answer on the form becomes an observation" (concepts/clinical/observation). The Questionnaire concept says "a question without a code simply stores its answer as form data" (concepts/definitions/questionnaire). The deep dive's code finding still stands: only coded questions inside an encounter become Observations. Don't cite the Observation concept page.
7. **A FHIR claim that conflicts with the code.** The Patient reference says records are "exposed through Care's REST API and FHIR Patient resources" (references/clinical/patient). The earlier code reading found no FHIR endpoint. Keep the earlier position.
8. **Care plans and multiple emergency contacts appear in the docs again but not in the code.** The Patient concept says "every encounter, observation, order, and care plan links back to a patient record" and "phone, address, and emergency contacts" (concepts/clinical/patient). The Patient reference lists only `phone_number` and `emergency_phone_number`.
9. **Closest built-in substitute for a care plan or a goals-of-care conversation.** Service requests have categories "laboratory, imaging, counselling, surgical_procedure, or education", and their intent can be "a plan is part of an agreed plan of care" (concepts/clinical/service-request). But a service request is always raised inside an encounter, and only doctors, nurses and admins can write one.
10. **Tags could mark palliative enrolment without code.** Tag categories include `advance_directive`, and "A patient can carry instance-wide tags and facility-specific tags" (concepts/platform/tagging). Volunteers cannot apply tags. This could fill the "Palliative program enrollment (coming soon)" gap.
11. **Who can load or change forms.**
    - Only Admin or Facility Admin can create a form. "Editing and deleting a questionnaire are restricted to superusers", and a live form is immutable (concepts/definitions/questionnaire).
    - Value-set writes are "restricted to superusers (is_superuser)" (concepts/definitions/valueset). So a Sahara body-site value set needs the instance superuser.
    - Keep Sahara's forms as drafts until they are reviewed.
12. **Partial answer to the 0–10 range problem.** Observation Definitions carry "reference ranges and interpretations that flag a value as normal, abnormal, or critical", and "Questionnaires point at observation definitions" (concepts/definitions/observation-definition). These only flag values; they don't reject them, and the interpretation value sets "are not enforced" (observation reference).
13. **Time-limited access isn't enforced.** On role grants, "expiry is a stored timestamp only — the model does not enforce it" (references/access-governance/permission-association). This matters for any time-limited family or volunteer access.
14. **Notes have an access gap.** "A TODO in the source flags organization-based access restriction as planned but not yet implemented" (references/clinical/notes). Volunteers cannot write patient-level notes. Notes map to FHIR Communication.
15. **CARE records home and community medicine events (staff side).**
    - MedicationAdministration has the category "community — given outside the facility, such as a home or field setting"; requests and dispenses have the same category.
    - For a MedicationStatement, "only the status, period, and notes can change after creation".
16. **Create-patient flow.**
    - "Required fields depend on your facility's questionnaire configuration"; "Phone — Often required for OTP".
    - "Start an encounter… (flow coming soon)"; "Link national health ID — ABDM…".
    - Identifier types include "National health ID — ABDM, ABHA" (flows/clinical/create-patient; concepts/clinical/patient).
17. **Relatives can't join a care team.** Care-team roles are bound to SNOMED codes 223366009 and 224930009 (healthcare professional or organisation), and members must belong to a facility department (references/clinical/care-team; flows/clinical/manage-encounter-care-team). Each write replaces the whole team list, and a closed encounter rejects it.
18. **Consent verification wording.** The reference describes "family — Verified by a family member" and says `verification_details` is "Read-only on the API". The 3.0 docs don't document `add_verification` at all. This is consistent with the earlier finding that staff enter verifications; no change to the pitch.
19. **Minor details.**
    - Users have web-push fields, TOTP multi-factor login and service accounts.
    - Location types include `house` and `vehicle`.
    - Discharge outcomes include `home` and `exp` (expired).
    - A condition's onset date cannot be in the future.
    - Answer `question_id` accepts UUID5, which confirms the deep dive's choice.

**Not mentioned anywhere in these 60 pages:** palliative care, a caregiver or family app, patient-portal/OTP screens, plugins, languages, pricing or hosting, contacts, licences, roadmap, or Kerala numbers.

### Page log (URL suffix after https://docs.ohc.network/3.0/ | fetched | summary | relevant)
01 concepts/billing/invoice | y | invoice lifecycle, snapshot, refunds | n
02 concepts/billing/payment-reconciliation | y | payments, patient/insurer | n
03 concepts/clinical/allergy-intolerance | y | allergy assertion, SNOMED | n
04 concepts/clinical/care-team | y | care team = Care users with a role | y(17)
05 concepts/clinical/condition | y | "Symptoms"; Volunteer can record via patient-level form | y(3)
06 concepts/clinical/consent | y | 7 categories, no known-gap note (differs from 3.1) | y(1)
07 concepts/clinical/diagnostic-report | y | report fulfils a service request | n
08 concepts/clinical/encounter | y | classes incl. `hh` (home health) | y minor
09 concepts/clinical/notes | y | threads, FHIR Communication | y(14)
10 concepts/clinical/observation | y | "each answer becomes an observation" | y(6)
11 concepts/clinical/patient | y | care-plan and emergency-contacts claims; ABHA identifier | y(8,16)
12 concepts/clinical/questionnaire-response | y | draft envelope; Volunteer can view answers | y(3,5)
13 concepts/clinical/service-request | y | counselling/education categories, plan intent | y(9)
14 concepts/clinical/specimen | y | lab samples | n
15 concepts/definitions/activity-definition | y | order templates, counselling/education | y minor
16 concepts/definitions/charge-item-definition | y | price catalogue | n
17 concepts/definitions/observation-definition | y | reference ranges and interpretation | y(12)
18 concepts/definitions/product-knowledge | y | drug catalogue | n
19 concepts/definitions/questionnaire | y | form types, immutable when live, who can write or submit | y(3,6,11)
20 concepts/definitions/questionnaire-response-template | y | pre-fill templates, Volunteer can write | y minor
21 concepts/definitions/specimen-definition | y | sample templates | n
22 concepts/definitions/valueset | y | superuser-only writes | y(11)
23 concepts/facility/ | y | facility = organisation site | n
24 concepts/facility/device | y | device tracking | n
25 concepts/facility/healthcare-service | y | service catalogue | n
26 concepts/facility/location | y | location tree, house/vehicle | n minor
27 concepts/medications/medication-administration | y | community/home category | y(15)
28 concepts/medications/medication-dispense | y | pharmacy dispensing | n minor
29 concepts/medications/medication-request | y | prescriptions, community category | y minor
30 concepts/medications/medication-statement | y | related-person source; limited edits | y(15)
31 concepts/platform/tagging | y | `advance_directive` tag category | y(10)
32 concepts/scheduling/booking | y | appointment lifecycle | n
33 concepts/scheduling/schedule | y | schedules and slots | n
34 concepts/scheduling/token | y | queue tokens | n
35 concepts/supply/inventory-item | y | stock balance | n
36 concepts/supply/product | y | batches | n
37 concepts/supply/supply-delivery | y | stock movement | n
38 concepts/supply/supply-request | y | stock demand | n
39 flows/clinical/create-patient | y | registration; encounter flow "coming soon" | y(16)
40 flows/clinical/manage-encounter-care-team | y | care-team UI steps | y(17)
41 intro | y | how the docs are organised | n
42 references/access-governance/organization | y | `role` organisation type "Volunteer, ASHA Worker" | y(3)
43 references/access-governance/permission | y | permission registry, contexts | y minor
44 references/access-governance/permission-association | y | expiry not enforced; patient-context permissions | y(2,13)
45 references/access-governance/role | y | custom roles | y(2)
46 references/access-governance/user | y | unique phone, multi-factor login, service accounts | y(2,19)
47 references/billing/account | y | billing account | n
48 references/billing/charge-item | y | charge items | n
49 references/billing/invoice | y | invoice fields | n
50 references/billing/payment-reconciliation | y | payment enums | n
51 references/clinical/allergy-intolerance | y | allergy specs | n
52 references/clinical/care-team | y | SNOMED role value set, full-replacement write | y(17)
53 references/clinical/condition | y | `body_site` not exposed (confirms earlier) | n
54 references/clinical/consent | y | verification read-only; family/validation types | y(18)
55 references/clinical/diagnostic-report | y | report specs | n
56 references/clinical/encounter | y | `hh`, extensions, discharge `home`/`exp` | y minor
57 references/clinical/notes | y | organisation-access TODO | y(14)
58 references/clinical/observation | y | performer can be related_person | y(4)
59 references/clinical/patient | y | PatientUser with role; "FHIR Patient resources" claim | y(2,7,8)
60 references/clinical/questionnaire-response | y | FormSubmission draft→submitted; submit format | y(5)
