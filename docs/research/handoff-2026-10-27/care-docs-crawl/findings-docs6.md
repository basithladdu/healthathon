# CARE docs crawl, part 6 (medicines, reports, tags)

_Research agent report, 27 Oct 2026. Unedited except for this header._

## docs6 lane: 60 of 60 pages read, none failed, nothing contradicts the earlier docs

I read all 60 URLs in `docs-part-6`. Each page was fetched with curl, with a 1 s delay between pages and one retry on failure. Every page returned HTTP 200 on the first try and was read in full. Every page is labelled "Version: 3.1".

I could not write `care-crawl/findings-docs6.md`: the harness refuses report files from subagents. The full findings are below. The raw HTML is in `(scratch path)` and the extracted text is in `(scratch path)` (`NN.txt`, where NN is the line number in `docs-part-6`).

Nothing in this part contradicts the four earlier CARE docs. Everything below either adds to them or confirms them in more detail.

### Top 5 new findings
1. **A saved medicine statement can't be edited except for status and note.** CARE's "medicine statement" is its record of what a patient takes. Quote: "You cannot change the medicine, the source, the dosage instructions, the period, or the reason of a saved entry. Care disables these fields." (https://docs.ohc.network/flows/medications/medication-statement/update-medication-statement)
   - So a family-reported change of dose or medicine has to become a new statement. The old one is set to stopped or "entered in error".
   - Sahara's single "changed at" field per medicine course can't be mapped onto CARE as an edit.
2. **Staff record medicine statements through a patient form, and some fields constrain Sahara's data.** The form is on the patient's "Updates" tab and needs an open encounter. (https://docs.ohc.network/flows/medications/medication-statement/record-medication-statement)
   - "Who reported" is set to Patient by default ("Care selects Patient by default"), so staff must switch it to "Related Person" for family reports.
   - "Dosage Instructions … mandatory", so Sahara's directions text can't be empty.
   - "Reason … up to 100 characters".
   - "Medication Statement is a patient form. It is not in the Forms list of the encounter."
   - The list of statements covers the whole patient and has a "Logged by" column (https://docs.ohc.network/flows/medications/medication-statement/view-medication-statements). If a staff member accepts a statement through a plugin, that staff member appears as "Logged by"; the family shows up only as the reporting source.
3. **Family-reported medicines feed the doctor's prescribing screen.** "The picker also shows the patient's medication statements, which record the medicines that the patient reports or used before." (https://docs.ohc.network/flows/medications/medication-request/add-medications-from-past-records) This gives a concrete reason CARE staff would want Sahara's medicine data. The earlier docs don't make this point.
4. **CARE can generate reports from Jinja2 templates as PDF or HTML.** The template types are "Discharge Summary, Patient Summary, or Account Report" (https://docs.ohc.network/flows/platform/report-template/create-a-report-template). No earlier doc mentions this. It could be a no-code way to produce a family handout or goals-of-care summary inside CARE.
   - Caveat: the Reports section on an encounter only offers Discharge Summary templates.
   - Generating a report for a completed encounter needs Facility Admin or Admin. (https://docs.ohc.network/flows/platform/report-template/generate-and-view-a-report)
5. **Tags and organisation types fit palliative work.**
   - Tag categories include "Advance Directive", and Patient is one of the record types a tag can apply to. The record type can't be changed once a tag is created (https://docs.ohc.network/flows/platform/tagging/create-a-tag).
   - A child tag is single-choice under its parent, so it could act as a status such as "Sahara linked: yes/no" (https://docs.ohc.network/flows/platform/tagging/create-a-child-tag).
   - Instance-wide organisations include a "role" type: "a flat set of users, e.g. Volunteer, ASHA Worker". The "govt" type mirrors "State → District → …", and "facilities and patients reference these nodes geographically". Organisations also carry a free-form `metadata` field for deployment-specific data (https://docs.ohc.network/references/access-governance/organization).
   - Organisations "align with the FHIR Organization resource". That is still not evidence of a FHIR endpoint, so the brief's [unverified] stays.

### Other new or confirming points
- **Doses given are staff-only.** The page reads "record a dose … that staff gave". Doses sit on a grid of six-hour slots, medicines carry Active/Stopped/PRN badges, and there is a printable "Drug Chart" (administer-dose and view-medicine-administration pages). This confirms the earlier "CARE logs nurse-given doses only". CARE has no counterpart to Sahara's family dose log.
- **The Medicines tab has four sub-tabs.** One of them is "Medication Statements | The ongoing or self-reported medicine use of the patient" (view-encounter-medications page).
- **Prescriptions are named batches.** Once a medicine is saved, its clinical fields lock, and removing it marks it "Entered in Error". For a deliberate clinical decision to stop a medicine, the page says: "Care has no separate action for that decision on this screen" (update-prescription page). Drugs are picked from a clinical drug list or the facility's inventory, depending on the deployment.
- **Schedules can be public or private.** "Patients can see a public schedule and book against it." The resources that can be booked are a practitioner, a location or a healthcare service, with a set number of tokens per slot (create-schedule-template page). I found nothing about scheduling home visits.
- **Billing.** Dispensing creates a charge for each item, and a deployment can turn on automatic invoicing. A delivery linked to a patient counts as a medicine return and creates a refund invoice (dispense-medication and receive-purchase-delivery pages). This is only marginally relevant.
- **Docs structure.** The docs are in four layers: Concepts, Flows, References and Playbooks. The intro links to HMIS playbooks only (https://docs.ohc.network/intro).
- **Weak sign of a staff desktop tool.** Pages list keyboard shortcuts such as g then m, k, p and Shift+Enter. This doesn't prove there is no mobile use.
- **Not found in this part:** patient or family login, consent, pain scales, FHIR/ABDM endpoints, languages, Kerala deployment data, pricing, contacts or licences.

### Page log (all fetched, HTTP 200)
**Relevant (y):**
- **Medicine administration:** 2 Administer a dose; 3 Update an administration record; 4 View the medication administration record.
- **Prescriptions:** 7 Add medications from past records; 8 Manage a prescription; 9 Prescribe a medication; 10 Print a prescription; 11 Update a prescription; 12 View prescriptions and medications for an encounter.
- **Medicine statements:** 13 Mark a statement as entered in error; 14 Record a medication statement; 15 Update a medication statement; 16 View medication statements.
- **Reports:** 17 Create a report template; 19 Generate and view a report.
- **Tags:** 23 Create a child tag; 24 Create a tag.
- **Schedules:** 30 Create a schedule template; 33 View schedule templates.
- **Docs and organisations:** 59 intro; 60 Organization reference.

**Not relevant (n):**
- 1 View locations.
- Dispensing: 5 and 6.
- Report templates: 18 edit, 20 preview, 21 view.
- Tags: 22 archive a child tag, 25 edit, 26 view.
- Schedule exceptions: 27, 28, 29.
- Schedule templates: 31 delete, 32 edit.
- Queue tokens: 34, 35, 36, 37.
- Stock levels, product catalog and instances: 38 to 42.
- Purchase deliveries: 43 to 46.
- Purchase orders: 47 to 50.
- Supply deliveries: 51 to 54.
- Supply requests: 55 to 58.

**Failed:** none.
