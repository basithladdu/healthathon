# CARE website, blog and organiser email links

_Research agent report, 27 Oct 2026. Unedited except for this header._

**I read every page in the `site` lane (75 pages and files; 1 HTML page blocked, 1 README missing), but I couldn't write `findings-site.md`.** The harness refused the write ("Subagents should return findings as text"), so the full findings are below. Copy them into `(scratch path)` if you want the file.

**Coverage**
- **Fetched and read in full (75):**
  - all 32 sitemap pages;
  - 27 more ohc.network pages linked from them but not in the sitemap: 22 blog posts plus `/foundation/documents`, `/foundation/iso`, `/funding.json`, `/solutions/care-janwar`, `/support/how-to-fund`;
  - the Loom page (public metadata and chapters only; the transcript needs a sign-up);
  - the docs plugin page;
  - the 4 requested READMEs (care_scaffold, skills, care_filly, care_ai_vision_fe);
  - 9 more READMEs from repos I found relevant (care_pressure_sore_fe, care_patient_geo_fe, care_people_fe, care_notifications_be, care_io_balance_fe, care_clinic, care_create, care_experience_be, care_mcp).
- **Failed (2):**
  - `github.com/ohcnetwork` HTML and `/orgs/ohcnetwork/repositories` returned 403 from the session proxy, and `gh api orgs/…` is blocked. I got the repo list from GitHub search (`org:ohcnetwork`) instead: 83 public repos.
  - `care-abdm` has no root README (404). I shallow-cloned it read-only (`969a278`, 23 Sep 2026) and read `AGENTS.md`, `docs/00-context.md` and `docs/03-roadmap.md`. No code was run.
- Raw HTML and text are in `(scratch path)` (`html/`, `clean/`, `ext/`, `clone-abdm/`).

**Top 5 new findings**
1. **Pallium India, Niram and Arike already run CARE.** These are palliative NGOs, separate from NHM's government-run Kerala grid, so they are realistic pilot partners for a family plugin. This corrects DEEP_DIVE §8, which describes Pallium only as "helpline and telehealth".
2. **CARE plugins can now add body-map question types.** `care_pressure_sore_fe` (24 Oct) does this with no backend, so a frontend-only "pain body map" plugin is a real option. This qualifies DEEP_DIVE §5.1 limit 2.
3. **A public, MIT-licensed ABDM plug exists, but it is not the production `care_abdm`.** `ohcnetwork/care-abdm` is an experiment built by an AI agent from eka.care's docs. Its ABHA sign-up (M1) is mostly built; hospital record sharing (M2) is code-complete only against mocks; record requesting (M3) isn't done. It makes no WellnessRecord.
4. **CARE has run a citizen-facing self-assessment tool.** It is health.kerala.care, plus KARE at kare.kerala.care. So we shouldn't say "CARE has no patient-facing surface anywhere". Whether it runs on CARE core is unverified.
5. **OHC claims an ISO 82304-2 certificate covering "CARE-based apps… in community and home-care programs".** Also claimed: ISO 27001 and 82304-1. This is the quality bar a family app may be compared against.

---

## 1. NEW or CONTRADICTS earlier docs (most important first)

1. **Palliative organisations already run CARE beyond the Kerala grid. This corrects DEEP_DIVE §8.**
   - "On 5 December 2025, Pallium India officially launched CARE HMIS… CARE supported inpatient, outpatient, and home-care operations across the organization… customized questionnaires to match care protocols" — https://ohc.network/blog/one-million-encounters-paperless-governance
   - "Pallium's operating model included an 18-bed inpatient facility, 17 outpatient departments, 138 full-time staff, and 16 home-care teams." "Niram Palliative Care in Tumkur, Karnataka, a 75-bed NGO-managed palliative care center with outpatient and home-visit services, began onboarding to CARE HMIS… created custom questionnaires" — https://ohc.network/blog/care-dashboards-testing-new-deployments
   - "Arike had been using CARE 2.0, and the team rolled out CARE 3.0" — https://ohc.network/blog/fhir-lab-module-and-care-3-deployments
   - "CARE was deployed for the Indian Association of Palliative Care at care.iapckerala.in" — https://ohc.network/blog/building-care-open-healthcare-foundation
   - What it means: named non-government palliative CARE sites are realistic pilot partners for `care_family`, and much easier than NHM's government-run instance. Their questionnaires aren't public.

2. **Plugins can now add custom question types with body maps. This qualifies DEEP_DIVE §5.1 limit 2 ("No body-site picker") and the 3D-pain-body corrections in §9.**
   - `care_pressure_sore_fe` (created 24 Oct 2026): "An encounter/patient questionnaire group with the original CARE front and back body maps. Select a location, add a sore… Multiple sores in a region remain independent rows… The plugin uses CARE's ordinary questionnaire answers; it adds no backend endpoint." In the builder you "choose Group → Pressure sore chart". The body maps have 21 front and 19 back regions. — https://raw.githubusercontent.com/ohcnetwork/care_pressure_sore_fe/HEAD/README.md
   - The same pattern appears in other repos:
     - `care_dental_fe`: "Dental chart (odontogram) question type… frontend-only CARE plugin";
     - `care_vision_prescription_fe`: "Vision Prescription as a structured question… MFE plug";
     - `care_io_balance_fe`: "Each submitted entry creates a panel Observation… coded component with a UCUM mL unit".
   - The HMIS page claims "Clinical drawings | Capture drawings, body maps, annotations" — https://ohc.network/solutions/hospital-management
   - What it means: core still has no body-site picker. But a frontend-only Sahara "pain body map" question-type plugin, cloned from the pressure-sore plugin, is now a credible, backend-free CARE artefact. Effort is unverified.

3. **A public ABDM plug exists (`ohcnetwork/care-abdm`, MIT, `969a278`). DEEP_DIVE §1 and §6 said nothing about ABDM came from code.**
   - `AGENTS.md` calls it "care-abdm-sbx… ABDM (NHA India) plug for CARE".
   - `docs/00-context.md`: "An experiment, and a real deliverable… can an AI agent, given only that documentation [abdm-docs.dev.eka.care], integrate ABDM into a real healthcare system?" It explicitly forbids opening the existing `care_abdm` / `care_abdm_fe`. **So it is not the production `care_abdm`, which is still unreadable.**
   - `docs/03-roadmap.md`:
     - M1 (ABHA) is mostly built.
     - M2 (the hospital shares records, HIP) is "Code-complete 2026-09-15". It builds OPConsultRecord, PrescriptionRecord and HealthDocumentRecord bundles, and the encrypted push was only proven with the network mocked.
     - Real sandbox runs are still "USER" items, not done.
     - M3 (the hospital requests records, HIU) is not done.
     - I found no WellnessRecord.
   - What it means: this confirms the DEEP_DIVE flow. It gives an MIT-licensed reference for bundle shapes. ABDM is still not a 7 Nov item.

4. **CARE has served citizen-facing self-assessment. This nuances "CARE is a staff tool" (BRIEF §2, FIT §3.1).**
   - "NHM Kerala also requested the CARE team to design and support a Health Risk Assessment Tool for the Government of Kerala's Vibe 4 Wellness Campaign, hosted at health.kerala.care… launched by the Chief Minister on 1 January 2026. It enabled citizens to access self-assessment tools" — https://ohc.network/blog/one-million-encounters-paperless-governance
   - The team also supported "NHM Kerala's KARE program… questionnaire development for risk assessment… at kare.kerala.care" — https://ohc.network/blog/care-hmis-hospital-go-live
   - Unverified: whether this runs on CARE core or the OTP login, or on a separate site.

5. **ISO claims are not in the earlier docs.**
   - "ISO 82304-2:2021 | Health and Wellness Apps… Scope | Applicable to CARE-based apps and wellness-oriented modules deployed in community and home-care programs. | Certified". ISO 27001:2022 and ISO 82304-1:2016 are listed too. — https://ohc.network/foundation/iso
   - Jul 2026 blog: "ISO certification for CARE was completed against ISO/IEC 82304… and ISO/IEC 27001:2022… the CARE trademark application is in progress" — https://ohc.network/blog/prescription-billing-access-improvements-care
   - What it means: Sahara should not imply any equivalent certification.

6. **There are more plugin surfaces than DEEP_DIVE §4.1 lists. One claim conflicts with DEEP_DIVE §3 and is unresolved.**
   - New surfaces:
     - `components.PatientRegistrationForm` receives the live registration form. `care_patient_geo_fe` uses it for "field/home-visit registration" and appends a Maps link to the patient's address (its README).
     - "Plugins can now add their own sections to a user's profile page" (https://ohc.network/blog/september-2026-care-updates).
     - "The encounter and patient files tab can now be extended through plugins" (Jul 2026 blog).
     - care-abdm uses `EncounterActions`, `EncounterOverviewTop` and `PatientSearchActions`.
   - The conflict: Plugs V3 promises "the ability to replace any part of the application with custom plugins" (https://ohc.network/blog/plugs-v3-extensible-care-platform). The docs say components opt in with `register("ComponentName", Component)` (https://docs.ohc.network/contributing/plugins/).
   - Unverified: whether any patient-portal component is registered for override. Check `care_fe/src/lib/override/` and the `register(` call sites before repeating "the patient portal has no plugin surface".

7. **A patient-extension mechanism exists for caregiver fields (relevant to DEEP_DIVE §2 "Caregivers", which was unverified).**
   - `care_people_fe`: "`primary_facility` patient extension… care_fe already renders extension fields on patient create *and* update; the plug only documents the schema the backend must register."
   - Also: "Custom fields can now appear only in relevant contexts" (Plugs V3 blog), and "The patient address field gained support for location links… For home-based care and palliative follow-up" (https://ohc.network/blog/patient-accounts-care-hmis).

8. **Palliative-grid details not in the earlier docs:**
   - "The Volunteer Module was implemented, allowing nurses to assign patients to volunteers and record volunteer home-visit details directly within CARE. A Volunteer Visit Form…" (Oct 2025) — https://ohc.network/blog/refining-care-hmis-across-departments
   - "A Schedule X medicine dashboard provided visibility into controlled medicine handling" (Schedule X covers palliative opioids). "The state dashboard separated community, secondary, and MLSP visits." (Apr 2026) — https://ohc.network/blog/smarter-care-dashboards-queues-forms
   - "Active Patients… encounters in the past 100 days or appointments scheduled in the next 100 days." "Camera-based consent capture allowed staff to upload consent forms directly from a device camera." "The responsibility feature was completed to manage roles and hierarchy from state to ward level." (Mar 2026) — https://ohc.network/blog/analytics-governance-care-operations
   - "Dashboard planning also continued with Kerala CARE and Arike… queries for comfort devices and patient care requests were updated" — Plugs V3 blog
   - "Consent management was moved into patient records" (Apr 2025) — https://ohc.network/blog/stabilizing-care-after-statewide-launch
   - "dispensing medicines or consumables without a formal e-prescription in inpatient and home-visit scenarios" (Aug 2025) — https://ohc.network/blog/token-management-care-hmis-groundwork
   - "'Santhwana Mithram - Your Palliative Care Friend'… a proposed initiative to create a 30,000-member palliative care workforce, primarily women trained through PHCs, to provide optional paid home-care support" (Sep 2025) — https://ohc.network/blog/care-hmis-live-hospital-pilot. Its current status is unknown.

9. **The questionnaire engine has changed.**
   - "version two of CARE's questionnaire builder and forms… live preview… drag and drop" (Aug 2026) — https://ohc.network/blog/august-2026-care-updates
   - "computed extension fields using FHIRPath expressions" (Apr 2026)
   - "JSON import/export" (Jun 2025) — https://ohc.network/blog/pharmacy-module-and-kerala-care-scale
   - DEEP_DIVE validated its three forms against `care@2bae933` (19 Oct), which is later than v2, so they should still hold.

10. **OHC labels Home Care Grid a "Reference model".** HMIS is tagged "Field-ready" and TeleICU "Field-proven": "Some are already field-proven. Others are packaged as implementation-ready blueprints" — https://ohc.network/solutions. This fits DEEP_DIVE's finding of no care-plan model in code.

11. **The Loom walkthrough** (https://www.loom.com/share/524a50d9b57142d7853c3acf35035250):
    - Title "How to Build Healthcare Apps on CARE", 836 s, created 2026-10-09.
    - Chapters: 00:00 Introducing… / 01:46 Core layers / 03:37 Key patient and clinical features / 06:49 Reports dashboards / 08:44 How to get started / 10:31 AI built Zoom video visits / 13:36 Wrap up. The auto-summary mistranscribes CARE as "CAD".
    - The description mentions "specific solutions like HMIS and palliative care" and "a Zoom video SDK based scheduling and video consultation integration using agents without writing code". That matches the `care_zoom_be` / `care_zoom_fe` repos (10 Oct).
    - The scaffold's own worked example is a "LiveKit teleconsultation plugin".

12. **Scaffold and docs details:**
    - The scaffold says to register plugins through "external `ADDITIONAL_PLUGS` configuration… Zero core changes". The docs and the care_filly README still show `plug_config.py`. This is already in DEEP_DIVE.
    - New: `npx @ohcn/care create` (`care_create`) brings up a local CARE stack in one command with a plug picker. It could shorten the DEEP_DIVE §4.2 "2–3 days" stack-setup estimate (unverified).
    - The `ohcnetwork/skills` README lists 6 skills and omits `care-form-skill`, which DEEP_DIVE cites. Re-check before citing it.

13. **Published metrics disagree, so quote only dated figures.**
    - The site says "330,000+ registered patients", "1.3M+ home-care consultations" and "12,000+ daily users".
    - The Apr 2026 blog says "3.26 lakh registered patients and 15.45 lakh recorded encounters".
    - The impact page says TeleICU covers "120+ hospitals | 7 states"; the TeleICU page says "216 units… 10 program states".
    - The press page itself says "Impact numbers should be checked against the metrics register".

14. **Offline use is still Sahara's differentiator.**
    - CARE Clinic works offline on the clinic LAN, but "Staff devices are not independent offline copies" (https://ohc.network/solutions/care-clinics).
    - Patient messaging is booking SMS only (care_notifications_be README). Clinic reminders "need compatible plugins and configured services; they are not included by default."

15. **Foundation and contacts:**
    - Section 8 non-profit, "Date of incorporation | 2 December 2025" (https://ohc.network/foundation/documents; the CIN field is blank there).
    - support@ohc.network and finance@ohc.network; phone +918547673209 (funding.json); Slack at slack.ohc.network.
    - Board includes "Dr. Athul Joseph Manuel | President, IMA Kochi / Chief Medical Officer".
    - docs.ohc.network has versions 3.1 and 3.0 and a Malayalam locale.
    - The CARE AI roadmap lists "Palliative-care risk scores" and "Caregiver instruction drafts", both staff-side.

16. **GitHub org: 83 public repos.**
    - **No repo name contains palliative, family, caregiver or home-care.**
    - Patient-related:
      - `care_patient_geo_fe`;
      - `care_people_fe` / `care_people_be`;
      - `care_pressure_sore_fe`;
      - `care_notifications_be`;
      - `care_io_balance_fe`.
    - Others: `care-abdm`, `care_clinic`, `care_create`, `care_mcp` ("MCP server for CARE, shipped as a Care backend plugin"), `care_eka_scribe_fe`, `care_ask_fe`, `care_ai_widgets_fe`, `care_zoom_be/fe`, `care_onboarding_fe`, `care_excalidraw_fe`, `care_dental_fe`, `care_vision_prescription_fe`, `deployments_registry`.
    - `care_scribe*`, `care_abdm*` (underscore), `care_hcx` and `care_filly_fe` are absent from public search, consistent with DEEP_DIVE finding them private.

**Unchanged:**
- The palliative page matches the BRIEF quotes.
- Build on Care adds a starter prompt: "Use synthetic data only. Do not send patient records, secrets, or credentials to third-party AI tools."
- The 4 requested READMEs match the DEEP_DIVE descriptions.

## 2. Page log (all HTTP 200 unless noted)

| URL | Fetched? | Summary | Relevant? |
|---|---|---|---|
| ohc.network | y | Core v3.0.0/FHIR R5, solutions, DPG/MIT | y |
| /about | y | Mission | n |
| /about/team | y | Board roster incl. Dr Athul J Manuel | y |
| /about/team/foundation | y | CEO and CTO | y |
| /foundation/governance | y | Board, technical steering committee, maintainers | n |
| /about/security-compliance | y | Standards, access control, sovereign hosting, AI as plugins | y |
| /product/care-core | y | Core primitives incl. care-plans claim | y |
| /product/care-ai | y | AI skills; palliative risk scores; caregiver drafts | y |
| /product/apps-integrations | y | ABDM M1–M3, ONVIF, Scribe | y |
| /product/standards-architecture | y | Standards, bring-your-own terminology | y |
| /solutions | y | Home Care Grid "Reference model" | y |
| /solutions/hospital-management | y | HMIS in 5 states; body maps; patient-facing workflows | y |
| /solutions/teleicu | y | 10BedICU | n |
| /solutions/palliative-care | y | Home Care Grid features and metrics | y |
| /solutions/care-clinics | y | CARE Clinic, LAN-offline | y |
| /impact | y | Metrics; DPG evaluated 01.07.2025 | y |
| /network | y | Ecosystem history | n |
| /ecosystem/implementation-partners | y | Partner rules | y |
| /ecosystem/clinicians | y | Clinician roles | n |
| /build-on-care | y | Organiser resource; starter prompt; demo checklist | y |
| /developers | y | Stack, Slack | y |
| /support | y | Funding paths | n |
| /supporters | y | Logos | n |
| /blog | y | Index of 23 posts, Dec 2024 – 30 Sep 2026 | y |
| /blog/why-open-healthcare-needs-a-foundation | y | Lists Pallium, Niram, Arike, KARE, Vibe 4 Wellness | y |
| /blog/ai-ready-not-ai-hyped | y | AI essay | n |
| /blog/care-core-as-operating-layer | y | Primitives; deployments | y |
| /press-media | y | Check the metrics register | n |
| /faq | y | General FAQ | n |
| /system-requirements | y | Browser guide | n |
| /careers | y | 3 roles | n |
| /contact | y | support@ohc.network | y |
| /blog/analytics-governance-care-operations | y | 100-day active rule; camera consent; state→ward roles | y |
| /blog/august-2026-care-updates | y | Questionnaire builder v2 | y |
| /blog/building-care-open-healthcare-foundation | y | care.iapckerala.in; Arike | y |
| /blog/care-dashboards-testing-new-deployments | y | Niram and Pallium onboarding | y |
| /blog/care-hmis-hospital-go-live | y | KARE; Pallium at full scale | y |
| /blog/care-hmis-live-hospital-pilot | y | Santhwana Mithram | y |
| /blog/fhir-lab-module-and-care-3-deployments | y | Arike moves to CARE 3.0 | y |
| /blog/kerala-palliative-care-grid-launch | y | Mar 2025 launch | y |
| /blog/one-million-encounters-paperless-governance | y | Pallium in production; health.kerala.care | y |
| /blog/palliative-care-grid-field-pilots | y | Jan 2025 pilots | y |
| /blog/patient-accounts-care-hmis | y | Billing accounts; address location links | y |
| /blog/pharmacy-module-and-kerala-care-scale | y | Questionnaire JSON import/export | y |
| /blog/plugin-extensions-bed-management-billing-care | y | Versioned valuesets; new extension points | y |
| /blog/plugs-v3-extensible-care-platform | y | "Replace any part" | y |
| /blog/preparing-care-statewide-palliative-rollout | y | Keltron CERT; consent module | y |
| /blog/prescription-billing-access-improvements-care | y | Files tab via plugins; ISO | y |
| /blog/refining-care-hmis-across-departments | y | Volunteer Module | y |
| /blog/september-2026-care-updates | y | Plugin sections on user profile | y |
| /blog/smarter-care-dashboards-queues-forms | y | FHIRPath; Schedule X dashboard | y |
| /blog/stabilizing-care-after-statewide-launch | y | Home-Based Care module; 2FA; consent | y |
| /blog/stable-hospital-operations-care-hmis | y | HMIS stabilisation | n |
| /blog/token-management-care-hmis-groundwork | y | Dispensing without e-prescription on home visits | y |
| /foundation/documents | y | Legal IDs | y |
| /foundation/iso | y | ISO 82304-2 with home-care scope | y |
| /funding.json | y | Funding plans | n |
| /solutions/care-janwar | y | Veterinary blueprint | n |
| /support/how-to-fund | y | Bank details | n |
| Loom share page | y (metadata only) | Title, 13:56, chapters, Zoom demo | y |
| docs.ohc.network/contributing/plugins/ | y | Plugs, Care Apps, overrides | y |
| README care_scaffold | y | ADDITIONAL_PLUGS; zero core changes; LiveKit reference | y |
| README skills | y | 6 skills listed | y |
| README care_filly | y | Medispeak token broker | y |
| README care_ai_vision_fe | y | Registration-form OCR | y |
| github.com/ohcnetwork HTML | **n (403)** | Replaced by search: 83 repos | y |
| README care_pressure_sore_fe | y | Body-map question group | y |
| README care_patient_geo_fe | y | PatientRegistrationForm slot | y |
| README care_people_fe | y | Patient extension | y |
| README care_notifications_be | y | Booking SMS | y |
| README care_io_balance_fe | y | Question-type plugin making coded Observations | y |
| README care_clinic | y | Offline desktop clinic | n |
| README care_create | y | One-command local stack | y |
| README care_experience_be | y | Boilerplate | n |
| README care_mcp | y | One line | n |
| care-abdm README | **404** | Shallow-cloned instead (`969a278`) | y |

No repo was edited and no CARE code was run.
