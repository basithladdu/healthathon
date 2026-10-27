# CARE docs crawl, part 2

_Research agent report, 27 Oct 2026. Unedited except for this header._

## docs2 report (docs-part-2: 60 docs.ohc.network pages)

**I could not write the findings file.** The harness refused the Write to `(scratch path)` ("Subagents should return findings as text"). The full content is below; save it as findings-docs2.md if you need the file.

**Pages read: 60 of 60, all HTTP 200, none failed, no retries.** I fetched them with curl -sL, 1 s apart, and read every page in full. Raw HTML and text are in `(scratch path)` and `NN.txt`, numbered in list order, with a fetch log in `log.txt`. I did not edit the repo. Nothing here was checked against code or a running server.

### Top 5 new findings
1. **Pages 1–35 are frozen 3.0 docs.** Each opens with "This is documentation for Care 3.0 , which is no longer actively maintained. For up-to-date documentation, see the latest version ( 3.1 )." Matching un-versioned 3.1 `/references/...` pages exist, and our docs and deck should cite those instead.
2. **The 3D pain body could be stored whole as a MetaArtifact.** The page says: "A MetaArtifact attaches a freeform metadata payload to any Care object … a drawing on a patient, say". It attaches to a patient or encounter, `object_type` is drawing, and `object_value` is "dict | list". This softens deep-dive point 4 ("our 3D pain body only goes in as area plus score rows"). It would be uncoded and written by staff. Who can write it, and whether the CARE app shows it, is [unverified]. (/3.0/references/platform/meta-artifact)
3. **"Referrals: No" in CARE_VS_SAHARA row 100 is too strong.** ResourceRequest is "an inter-facility request for a resource (transfer, supply, or assistance)". It has an optional `related_patient`. Statuses include `transportation_to_be_arranged` and `transfer_in_progress`. Categories include `comfort_devices`, `medicines` and `patient_care`. It is facility-to-facility and staff-only, so the row should say "Partly". (/3.0/references/platform/resource-request)
4. **CARE keeps a full edit history of every record.** `EMRBaseModel.history` is a "Flattened JSON array of every version of the resource with its performer … served on request through a dedicated history API for audit and point-in-time reconstruction". So audit history on its own does not set Sahara apart. (/3.0/references/foundation/base-model)
5. **CARE has a draft stage for forms.** FormSubmission has a status of draft, submitted or entered_in_error. The 3.1 index calls it "the envelope that carries that payload through a draft-to-finalised lifecycle". Our earlier docs never mention it. It might back the family-inbox "pending until a nurse accepts" state. [unverified; probably needs staff auth] (/3.0/references/definitions/questionnaire; /category/clinical-1)

### 1. NEW or CONTRADICTS earlier docs (most important first)
1. **3.0 vs 3.1 docs** (item 1 above). Pages 36–60 are "Version: 3.1". Where a 3.0 page disagrees with code at `2bae933`, trust the code.
2. **MetaArtifact for drawings** (item 2 above).
3. **Referrals** (item 3 above). `comfort_devices` matches palliative equipment loans.
4. **FormSubmission draft** (item 5 above). Quote: "A submission of a questionnaire against a patient (and optional encounter)", with a `response_dump`.
5. **Edit history** (item 4 above).
6. **Advance-directive tag.** Tag categories include "advance_directive" and "safety", and tags can apply to `patient` (/3.0/references/platform/tag-config). A nurse could use one to flag that a Sahara-reviewed goals-of-care note exists. This adds to the Consent `dnr` and `acd` categories we already found.
7. **Patient instruction fields with no patient screen.**
   - ServiceRequest has `patient_instruction`, "Instructions surfaced to the patient", and its categories include counselling and education (/3.0/references/clinical/service-request).
   - MedicationRequest's DosageInstruction has `patient_instruction`, "Patient-facing instructions", plus `as_needed_boolean` and `as_needed_for` for as-needed doses (/3.0/references/medications/medication-request).
   - No CARE patient view shows these, because the patient login is booking-only. Sahara could show them, for example the as-needed rules for breakthrough pain medicine.
8. **ObservationDefinition ranges.** It has `qualified_ranges` with numeric ranges and an interpretation (display, icon, colour, highlight), and its categories include `survey`. The page says "questionnaires reference it so the same observation … is recorded the same way everywhere".
   - A facility could flag pain 7–10 as severe, but a value of 14 would still not be rejected.
   - Observations are only created "When an encounter is present".
   - How a question links to a definition is [unverified]: the 3.0 Question shape has no such field. (/3.0/references/definitions/observation-definition)
9. **Facility-made value sets.** "Author your own for local terminology; the system- slug prefix is reserved". A clause can pin a fixed list of concepts, and members are "queried in real time from terminology servers". A facility could hold Sahara's 12 body areas or 17 symptoms as a value set for choice questions without mapping them to SNOMED first. (/3.0/references/definitions/valueset)
10. **Report templates.** "A Template defines reusable report markup and render options; a ReportUpload is one generated file (PDF/HTML/etc.)". The format is pdf or html, and the default context is "encounter_base". Template types and contexts come from plugin registries (ReportTypeRegistry, DataPointRegistry). So CARE can render a goals-of-care or visit summary PDF itself. (/3.0/references/platform/report)
11. **File uploads.**
    - Files can go up through a signed S3 URL or "Inline base64 (POST .../upload-file/)". The inline path enforces `MAX_FILE_UPLOAD_SIZE` and "sniffs the real MIME type with python-magic".
    - Storage keys are random: "Keeping the user-supplied name out of the storage key limits PII leakage".
    - Files are archived, never hard-deleted.
    - There is no photo, wound or prescription category, so family photos would be `unspecified`. (/3.0/references/platform/file-upload)
12. **Kerala local-government hierarchy and facility types.**
    - Organisation levels: "Country State District Block Panchayat Corporation District Panchayat Grama Panchayat Local Body Municipality Ward" (/concepts/access-governance/organization).
    - Facility types include FHC 802, PHC 800, CHC 803, "Clinical Non Governmental Organization" 3000 and "Community Based Organization" 4000. There is no palliative or hospice type (/3.0/references/facility/).
13. **New 3.1 "Responsibility" concept.** "a group that gives a person a designation or a duty across Care … It does not belong to one facility", with roles Admin, Manager and Member (/concepts/access-governance/responsibility). It could model a district palliative network.
    - System roles are "Volunteer, Doctor, Nurse, Staff, Pharmacist, Administrator, Facility Admin, and Admin", and "You can assign only a role with permissions that you also hold" (/concepts/access-governance/access-control).
14. **Users are staff only.** "A user in Care is the staff account of a person who works in your organization … A user account is separate from a patient record." The page also shows a "Service Account" badge and a two-factor sign-in flow, and says "Care has no option to reactivate an archived account" (/concepts/access-governance/user). This confirms there is no family account. Handing families Volunteer staff accounts would go against the documented intent.
15. **Confirmations of what we already had.**
    - MedicationAdministration needs an encounter and a MedicationRequest, and "each performer.actor must reference an existing User", so families can't log doses given at home.
    - MedicationStatement `information_source` includes "related_person", and an encounter is required.
    - The question type "attachment" is "not implemented".
16. **Plugin and feature-gating hooks.** These are not in the earlier docs, which only cover the scaffold and app registry.
    - Facility and user feature flags through FlagRegistry, "administered through Django admin or registration/management code, not the public API". A family-inbox plugin could be switched on per facility this way (/3.0/references/facility/facility-flag).
    - Device `care_type` plugins through DeviceTypeRegistry.
    - Product and SupplyDelivery `extensions` validated against registered schemas.
    - The report registries in item 10.
17. **3.1 index pages outside this lane.** The index lists "Clinical - Care Team" and "Care team: Technical reference for the encounter care team" (/category/clinical, /category/clinical-1), plus Department, Schedule Exception, Purchase Order/Delivery and Supply Order. Whichever lane has the care-team pages (`/concepts/clinical/care-team`, `/references/clinical/care-team`, `/flows/clinical/care-team/*`) should check CARE_VS_SAHARA row 86, "Care team: Partly. A list of names, not accounts".
18. **Minor.**
    - Location forms include "ho House" and "ve Vehicle".
    - A DeliveryOrder can name a patient: "Patient the order is dispensed to".
    - Schedule `is_public` "Exposes the schedule for public booking". There is a `TokenBookingOTPReadSpec` for the "public/OTP booking flow", and `booked_by` is "nullable for self-service". This matches our booking-only finding.

Nothing on these pages covers pricing, hosting, contacts, licences, languages, ABDM/FHIR, or a roadmap.

### 2. Page log (all fetched with HTTP 200; y/n = relevant to Sahara)
1. 3.0/references/clinical/service-request: orders, `patient_instruction`, counselling category (y)
2. 3.0/references/clinical/specimen: lab specimens (n)
3. 3.0/references/definitions/activity-definition: templates that become ServiceRequests (n)
4. 3.0/references/definitions/charge-item-definition: pricing (n)
5. 3.0/references/definitions/observation-definition: LOINC definitions, ranges and interpretation (y)
6. 3.0/references/definitions/product-knowledge: product catalogue (n)
7. 3.0/references/definitions/questionnaire: full Questionnaire, QuestionnaireResponse and FormSubmission spec, submit rules (y)
8. 3.0/references/definitions/questionnaire-response-template: clinician prefill templates (n)
9. 3.0/references/definitions/specimen-definition: lab templates (n)
10. 3.0/references/definitions/valueset: custom value sets, live terminology lookup (y)
11. 3.0/references/facility/: facility model, facility types (y)
12. 3.0/references/facility/device: devices, `care_type` plugin registry (n)
13. 3.0/references/facility/facility-config: billing configuration (n)
14. 3.0/references/facility/facility-flag: per-facility flags (y)
15. 3.0/references/facility/healthcare-service: services offered (n)
16. 3.0/references/facility/location: location tree, House and Vehicle forms (n)
17. 3.0/references/foundation/base-model: soft delete, `history` audit API, `meta`, common types (y)
18. 3.0/references/medications/medication-administration: needs encounter, request and a User as performer (y)
19. 3.0/references/medications/medication-dispense: dispensing and inventory (n)
20. 3.0/references/medications/medication-request: prescriptions, `patient_instruction`, as-needed doses (y)
21. 3.0/references/medications/medication-statement: `related_person` source, encounter required (y)
22. 3.0/references/platform/favorites: user favourite lists (n)
23. 3.0/references/platform/file-upload: S3 signed URL or base64 upload, archiving, categories (y)
24. 3.0/references/platform/meta-artifact: free-form drawing JSON on a patient or encounter (y)
25. 3.0/references/platform/report: PDF/HTML report templates (y)
26. 3.0/references/platform/resource-category: category tree (n)
27. 3.0/references/platform/resource-request: inter-facility transfer and comfort-device requests (y)
28. 3.0/references/platform/tag-config: tags, including the advance_directive category (y)
29. 3.0/references/scheduling/booking: bookings, OTP booking read spec (y)
30. 3.0/references/scheduling/schedule: schedules, `is_public` (y)
31. 3.0/references/scheduling/token: walk-in queue tokens (n)
32. 3.0/references/supply/inventory-item: stock levels (n)
33. 3.0/references/supply/product: batches, extensions (n)
34. 3.0/references/supply/supply-delivery: deliveries, patient dispense order (n)
35. 3.0/references/supply/supply-request: supply requests (n)
36. category/access--governance: 3.1 index (n)
37. category/access--governance-1: 3.1 reference index, User & Skills (n)
38. category/billing: 3.1 index (n)
39. category/billing-1: 3.1 index (n)
40. category/clinical: 3.1 index, lists Care Team (y)
41. category/clinical-1: 3.1 index, FormSubmission "draft-to-finalised", care team (y)
42. category/definitions--terminology: 3.1 index (n)
43. category/definitions--terminology-1: 3.1 index (n)
44. category/facility: 3.1 index (n)
45. category/facility-1: 3.1 index (n)
46. category/foundation: 3.1 index (n)
47. category/medications: 3.1 index (n)
48. category/medications-1: 3.1 index (n)
49. category/platform: 3.1 index (n)
50. category/platform-1: 3.1 index (n)
51. category/scheduling: 3.1 index, Schedule Exception (n)
52. category/scheduling-1: 3.1 index (n)
53. category/supply--inventory: 3.1 index, purchase orders (n)
54. category/supply--inventory-1: 3.1 index (n)
55. concepts/access-governance/access-control: roles, system roles including Volunteer (y)
56. concepts/access-governance/facility-organization: departments and teams; encounter access goes through the responsible department (y)
57. concepts/access-governance/organization: Kerala-style local-government hierarchy (y)
58. concepts/access-governance/responsibility: new 3.1 cross-facility duty groups (y)
59. concepts/access-governance/supplier: vendors (n)
60. concepts/access-governance/user: staff-only accounts, service accounts, two-factor sign-in (y)

All URLs above are prefixed with https://docs.ohc.network/.
