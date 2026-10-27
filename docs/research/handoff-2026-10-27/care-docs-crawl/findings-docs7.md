# CARE docs crawl, part 7 (technical references)

_Research agent report, 27 Oct 2026. Unedited except for this header._

I read all 53 docs.ohc.network/references/* pages in docs-part-7 in full (HTTP 200 first try, none failed). I could not write `findings-docs7.md`: the Write tool refuses report files from a subagent. The full findings and page log are below; paste them into that file if you need it. Raw HTML and the stripped text are in (scratch path) and .../txt7/, with the fetch log in .../log7.tsv. These pages are docs written from the code (Version 3.1), so anything about runtime behaviour is still [unverified].

## Top 5 new findings

1. **Contradicts CARE_FAMILY_SIDE_FIT.md table 4.x**, which says CARE records only the staff user as observation performer.
   - An Observation's performer can be a related person: "performer ... Performer { type: PerformerType (related_person|user), id: str }. Use it when the performer differs from the data-entry user" (/references/clinical/observation).
   - A questionnaire question can collect one: "collect_performer bool ... Collect a Performer reference" (/references/definitions/questionnaire).
   - So the "Who is reporting?" choice question in our three forms could map to CARE's own field.
   - Caveat: the id is a free string, because CARE has no related-person model. Whether submission copies the performer onto the Observation is [unverified].
2. **The docs say FHIR; the code doesn't.** The docs say "Patient records are exposed through Care's REST API and FHIR Patient resources" (/clinical/patient) and "identifier used across Care's REST API and FHIR resources" (/foundation/base-model). The code reading found no FHIR endpoint. Keep the code finding and don't quote these lines as proof of FHIR.
3. **Forms freeze once active, and answers have submit rules.**
   - "Once a questionnaire is active, edit and delete are off the table — move it to retired instead." Finish our three forms' wording before a hospital activates them.
   - Submit rules: "dateTime Must include timezone on submit"; boolean only true/false/1/0; text length capped by `MAX_QUESTIONNAIRE_TEXT_RESPONSE_SIZE`.
   - Question fields we didn't use: `formula`, `collect_time`, `is_observation`, `read_only`, `max_length`.
4. **CARE already has a draft holding area for form entries.** "FormSubmission runs a draft→submitted lifecycle. Its response_dump is opaque JSON" (/clinical/questionnaire-response). It might stand in for part of the plugin's pending-submission table. Who may call it is [unverified] and probably staff only. Confidence low.
5. **Family access limits, which partly answer an open question in the fit doc:**
   - **Care team:** family can't be care-team members. The role must come from SNOMED "Healthcare professional / Healthcare related organization", and the member must be able to view the encounter (/clinical/care-team).
   - **Family accounts:** hospitals can create custom roles, and "PatientUser" grants a user access to one patient with a role. So a minimal "family caregiver" CARE account is possible. Access doesn't expire on its own: "expiry ... Stored but not enforced by the model" (/access-governance/permission-association, /role, /clinical/patient).
   - **Doses at home:** a family-given dose can't be recorded as a MedicationAdministration. Its "actor ... The user who performed the administration ... reference an existing User" is required, and it needs an order.
   - **MedicationStatement:** updates can only change "status, effective_period, and note".

## Other new points (all quotes verbatim from the pages)

- **Instructions meant for patients:** `patient_instruction` exists on MedicationRequest dosage lines ("Patient-facing instructions") and on ServiceRequest ("Instructions surfaced to the patient"). Sahara could show these to the family, through a future plugin or export.
- **Pain-body points:** MetaArtifact "attaches a freeform metadata payload to any Care object ... a drawing on a patient", with object_type `drawing` on a patient or encounter. Staff could store the pain-body points there as JSON, which partly qualifies the deep dive's "3D points can't go in CARE". [unverified UI and permissions]
- **Version history:** every record has a `history`, a "Flattened JSON array of every version of the resource with its performer ... served on request through a dedicated history API". There is still no content hash and no family acknowledgement tied to a version.
- **Notes privacy:** "A TODO in the source flags organization-based access restriction as planned but not yet implemented."
- **Consent `verified_by`:** the docs say it is the "verifying user's external_id" on write. The code found earlier overwrites it with the signed-in user (`consent.py:55`). Keep the code finding.
- **Extensions:** unknown keys are "currently dropped" (payment-reconciliation, product). Putting caregiver data in `Patient.extensions` probably needs a plugin-registered schema. [unverified for Patient]
- **Condition** (shown in the UI as "Symptoms"): "body_site ... no current spec exposes it"; severity is only mild/moderate/severe; onset can't be in the future.
- **ObservationDefinition `qualified_ranges`** can label and colour a range, for example 7–10 pain as "severe". It does not reject out-of-range values, so the deep dive's "no 0–10 check" limit stands.
- **Minor:** tag categories include `advance_directive`, `safety` and `contact`. ResourceRequest has `comfort_devices` (between facilities only). Location form includes "ho House". File upload has an inline base64 route with a size cap, MIME sniffing and PII-free storage keys.
- **Confirmed, not new:** OTP booking read spec; consent categories `comfort_care`/`acd`/`adr`; encounter class `hh`; MedicationStatement `information_source = related_person`. Service accounts were already in the fit doc.

## Page log (all fetched: yes)

**Relevant (y):**
- **access-governance:** permission-association (expiry not enforced); role (custom roles); user (service accounts, MFA, MobileOTP)
- **clinical:** care-team (practitioner-role value set); condition (body_site not exposed); consent (enums; docs vs code); encounter (`hh`, care-team role); notes (org-access TODO); observation (`related_person` performer); patient (PatientUser, extensions, FHIR claim); questionnaire-response (FormSubmission draft); service-request (`patient_instruction`)
- **definitions:** observation-definition (ranges); questionnaire (full spec, freeze rule, `collect_performer`); valueset (live terminology lookups, local sets)
- **foundation:** base-model (history API, FHIR wording)
- **medications:** medication-administration (User performer and order required); medication-request (`patient_instruction`); medication-statement (`related_person`, narrow update)
- **platform:** file-upload; meta-artifact (drawing); tag-config (`advance_directive`)
- **scheduling:** booking (OTP spec, self-service)

**Not relevant (n):**
- **access-governance:** permission
- **billing:** account, charge-item, invoice, payment-reconciliation (extensions drop unknown keys)
- **clinical:** allergy-intolerance, diagnostic-report, specimen
- **definitions:** activity-definition, charge-item-definition, product-knowledge, questionnaire-response-template, specimen-definition
- **facility:** facility root page, device, facility-config, facility-flag, healthcare-service, location
- **medications:** medication-dispense
- **platform:** favorites, report, resource-category, resource-request
- **scheduling:** schedule, token
- **supply:** inventory-item, product, supply-delivery, supply-request
