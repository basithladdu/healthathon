# findings-docs0 (docs.ohc.network, part 0: 60 URLs, fetched 27 Oct 2026)

All 60 URLs returned HTTP 200 on first try; none failed. Text stripped locally (scripts/nav removed). Raw HTML + text: `scratchpad/care-crawl/out0/NN.html|txt`, fetch log `out0/log.txt`.
Compared against CARE_DEEP_DIVE_2026-10-27.md, CARE_VS_SAHARA_FOR_TEAM.md, CARE_FAMILY_SIDE_FIT.md, CARE_BRIEF_2026-10-27.md and the 23 Oct archive (`output/research/care-website-20261023/text/`).

## 1. NEW or CONTRADICTS earlier docs (most important first)

1. **Docs are now versioned; the "3.0" docs are marked unmaintained and a 3.1 exists.** Every /3.0/ page carries: "This is documentation for Care 3.0, which is no longer actively maintained. For up-to-date documentation, see the latest version (3.1)." (e.g. https://docs.ohc.network/3.0/category/clinical). Earlier docs cite unversioned paths (`/concepts/clinical/consent/`, `/flows/clinical/consent/verify-a-consent/`) and code at `care@2bae933` without saying which docs version. Consent-gap and verification quotes should be re-checked against the 3.1 pages before they go on a slide. (Not in our URL list; another lane may hold the 3.1 pages.)

2. **OHC itself names palliative user groups and per-patient grants: the access model can scope a role to a single patient.** "a grant gives a person that role inside one boundary — a facility, an organization, or a single patient" and context table includes "PATIENT | A patient record"; "A grant can also carry an expiry, after which it is meant to lapse" (https://docs.ohc.network/3.0/concepts/access-governance/access-control). And: "A deployment defines the groups it needs — in palliative care these might be Volunteer, ASHA Worker, or MLSP" with role organizations used "to decide things like which users a questionnaire is assigned to" (https://docs.ohc.network/3.0/concepts/access-governance/organization). Relevance: the only staff-side primitive close to "a relative with access to one patient" is a patient-scoped, expiring grant to a *user account* (staff-style login), not a family account. Earlier docs say CARE "gives roles to staff only"; that is still true in practice (standard roles are Doctor/Nurse/Staff/Volunteer/Pharmacist/Admin…), but the per-patient + expiry mechanism is new and is the hook a family-access plugin would reuse.

3. **Docs homepage makes stronger claims than our code reading supports.** "FHIR-native, self-hostable…", "FHIR R5 Standards-native", "Built around FHIR R5, SNOMED CT, LOINC, and open APIs — interoperable by design", "OP, IP, emergency, labs, pharmacy, billing, tasks, referrals, and care plans", "MIT … listed as a Digital Public Good" (https://docs.ohc.network/). Contradicts CARE_VS_SAHARA ("It does not speak FHIR directly"; "CARE code has no care plan record") — the reference pages themselves say "FHIR-aligned", "FHIR-inspired", and "Care implements a minimal subset of the FHIR spec" (ServiceRequest, https://docs.ohc.network/3.0/category/clinical-1). Keep our wording ("FHIR-aligned, no FHIR endpoint, no care-plan model in code"), and expect judges may quote OHC's "FHIR-native". DPG listing is new to our docs.

4. **Concrete hosting cost published: ~₹6,857/month minimum on AWS.** Cost table: ECS Fargate ~₹4000, RDS db.t2.micro ~₹1245, S3 20 GB ~₹38, ALB ~₹1500, 10 GB transfer ~₹74, "Total ~₹6857.00", region ap-south-1, "may vary based on actual usage" (https://docs.ohc.network/deployment/providers/aws/ecs). Gives a real number for "zero licence fee ≠ zero cost" in the brief.

5. **Privacy guidance treats plugins as data processors and cites India's DPDP Act.** "Cloud providers, hosting partners, and any Care plugins that touch patient data are processors — put data-processing agreements in place"; "India — Digital Personal Data Protection (DPDP) Act, 2023"; "Record patient consent explicitly, and make it auditable and revocable"; patient rights incl. "Portability — their data in a usable, machine-readable form" (https://docs.ohc.network/deployment/security/privacy-and-data-protection). Relevance: a Sahara family plugin/export would need a DPA with each hospital; supports framing Sahara's export as portability. None of our four docs mention DPDP or DPAs.

6. **MetaArtifact can hang "a drawing on a patient".** "A MetaArtifact attaches a freeform metadata payload to any Care object without a dedicated foreign key … to hang structured data — a drawing on a patient, say — off an existing record" (https://docs.ohc.network/3.0/category/platform-1). Possible home for the 3D pain-body picture beyond "area plus score" rows (deep dive limit 2). [unverified] how care_fe renders it.

7. **Docs contradict themselves on who answers questionnaires.** Concept: "the set of answers a clinician or staff member records" (https://docs.ohc.network/3.0/category/clinical); reference: "the set of answers a patient gives to a questionnaire; a FormSubmission is the envelope that carries that payload through a draft-to-finalised lifecycle" (https://docs.ohc.network/3.0/category/clinical-1). "FormSubmission" draft→final lifecycle is not named in our docs; it is the natural staging state for staff-reviewed family entries.

8. **Condition = "Symptoms" in UI; MedicationStatement explicitly accepts a relative as source.** "It maps to the FHIR Condition resource and shows up in the Care product UI as Symptoms"; MedicationStatement "as reported by the patient, a relative, or a clinician" (https://docs.ohc.network/3.0/category/clinical-1). Confirms existing claims; adds the UI label.

9. **Reference deployment bundles a SNOMED terminology server, Odoo (via `care_odoo` plugin) and Metabase; restore is not proven.** "Snowstorm | SNOMED CT terminology server"; "Odoo | ERP/back-office integration that Care talks to through the care_odoo plugin"; "Restore is a design goal, not a finished, tested procedure"; minimum "At least 2 Linux hosts" (https://docs.ohc.network/deployment/kubernetes/architecture, …/operations, …/requirements). `care_odoo` is a plugin not in our registry list (deep dive lists only ABDM, Hello, Imports, Scribe, TeleICU).

10. **GKE guide: patient-data bucket "Retention Policy: 7 days"; frontend env flags `REACT_ENABLE_ABDM`, `REACT_ENABLE_HCX`, `REACT_ENABLE_SCRIBE`** (https://docs.ohc.network/deployment/providers/gcp/gke). Flags look legacy (deep dive: frontend apps load via `REACT_ENABLED_APPS`) [unverified]. Odd 7-day retention on patient data — don't reuse as guidance.

11. **Plugins can be toggled from the admin UI.** "API | GET /api/v1/plug_config/ | Editable in the admin UI"; backend routes mount at "/api/<plugin-name>/"; "The default Care image ships with no plugins enabled" (https://docs.ohc.network/contributing/plugins). Deep dive covers REACT_ENABLED_APPS/override; admin-UI editability and the empty default plug list are additions. Page text is otherwise identical to the 23 Oct archive. Build-time plugs also via `ADDITIONAL_PLUGS` secret (AWS ECS guide).

12. **Users support TOTP MFA; service accounts "may sign in without" a password.** (https://docs.ohc.network/3.0/concepts/access-governance/user). Service accounts already known; MFA detail new.

13. Unchanged vs archive (re-confirmed): palliative patient-enrollment playbook ("Palliative program enrollment (coming soon)", "Add caregiver as related contact with callback number", "Document palliative-specific goals of care separately…"); HMIS ABDM link "(flow coming soon)". Docs blog has no posts (only author Vignesh Hari, "0 posts").

## 2. Page log

| # | URL | fetched? | one-line summary | relevant? |
|---|---|---|---|---|
| 1 | https://docs.ohc.network/blog | y | Empty blog shell | n |
| 2 | https://docs.ohc.network/blog/authors | y | One author, Vignesh Hari, 0 posts | n |
| 3 | https://docs.ohc.network/blog/authors/vigneshhari | y | "This author has not written any posts yet." | n |
| 4 | https://docs.ohc.network/markdown-page | y | Docusaurus placeholder page | n |
| 5 | https://docs.ohc.network/contributing/ | y | Contributor intro; care (DRF) + care_fe (React/TS) | n |
| 6 | https://docs.ohc.network/contributing/category/local-development | y | Index: backend, frontend, Nix | n |
| 7 | https://docs.ohc.network/contributing/local-development/backend | y | Docker Compose stack (Postgres, Redis, Celery, MinIO), port 9000 | y (setup for any plugin) |
| 8 | https://docs.ohc.network/contributing/local-development/frontend | y | Node 22, Vite, port 4000, Playwright e2e | y (setup) |
| 9 | https://docs.ohc.network/contributing/local-development/nix | y | Nix dev shell alternative (Py 3.13, PG 15) | n |
| 10 | https://docs.ohc.network/contributing/plugins | y | Plugs + Care Apps; plug_config, /api/<name>/, admin-UI toggle, overrides | y |
| 11 | https://docs.ohc.network/contributing/workflow | y | Branch off develop, issues/{n}/{name}, PR/QA labels, Code of Conduct | n |
| 12 | https://docs.ohc.network/deployments/hmis/ | y | HMIS playbooks intro (concept/flow/playbook layers) | n |
| 13 | https://docs.ohc.network/deployments/hmis/category/playbooks | y | Index: outpatient registration | n |
| 14 | https://docs.ohc.network/deployments/hmis/playbooks/outpatient-registration | y | OP registration; ABHA search, "ABDM link step … (flow coming soon)" | y (ABDM) |
| 15 | https://docs.ohc.network/deployments/palliative/ | y | Palliative playbooks intro | y |
| 16 | https://docs.ohc.network/deployments/palliative/category/playbooks | y | Index: patient enrollment | y |
| 17 | https://docs.ohc.network/deployments/palliative/playbooks/patient-enrollment | y | Enrollment playbook; unchanged vs 23 Oct archive | y |
| 18 | https://docs.ohc.network/deployment/ | y | Self-hosting overview; Odoo + Metabase alongside | y (hosting) |
| 19 | https://docs.ohc.network/deployment/category/kubernetes-reference | y | Index of k8s reference pages | n |
| 20 | https://docs.ohc.network/deployment/category/security--privacy | y | Index: data security, privacy | n |
| 21 | https://docs.ohc.network/deployment/kubernetes/architecture | y | 2-node kubeadm cluster; Snowstorm, care_odoo, Metabase, RustFS | y (hosting) |
| 22 | https://docs.ohc.network/deployment/kubernetes/cluster-setup | y | kubeadm/Cilium/Longhorn bring-up commands | n |
| 23 | https://docs.ohc.network/deployment/kubernetes/deploying-care | y | deployer.py module order; migrations job + terminology still TODO | n |
| 24 | https://docs.ohc.network/deployment/kubernetes/operations | y | Monitoring/backups; restore "not a finished, tested procedure" | y (hosting risk) |
| 25 | https://docs.ohc.network/deployment/kubernetes/requirements | y | ≥2 Linux hosts, tooling, S3 backups, DNS/TLS | y (hosting) |
| 26 | https://docs.ohc.network/deployment/providers/ | y | Generic production infra requirements (HA, SMTP, CI/CD) | n |
| 27 | https://docs.ohc.network/deployment/providers/aws/ | y | AWS = ECS Fargate + RDS + S3 + ALB | n |
| 28 | https://docs.ohc.network/deployment/providers/aws/ecs | y | ECS walkthrough; cost table ~₹6,857/month; ADDITIONAL_PLUGS | y (pricing) |
| 29 | https://docs.ohc.network/deployment/providers/digitalocean | y | App Platform recipe; env vars incl. JWKS_BASE64; Slack link | n |
| 30 | https://docs.ohc.network/deployment/providers/gcp/ | y | GKE vs GCS choice | n |
| 31 | https://docs.ohc.network/deployment/providers/gcp/gcs | y | Static frontend on GCS + CDN | n |
| 32 | https://docs.ohc.network/deployment/providers/gcp/gke | y | GKE recipe; patient bucket 7-day retention; REACT_ENABLE_ABDM/HCX/SCRIBE | y (minor) |
| 33 | https://docs.ohc.network/deployment/security/data-security | y | WAF/TLS/KMS/backup/VPN baseline | n |
| 34 | https://docs.ohc.network/deployment/security/privacy-and-data-protection | y | DPDP, revocable consent, plugins as processors, portability | y |
| 35 | https://docs.ohc.network/ | y | Docs home: "FHIR-native", DPG, care plans claimed | y |
| 36 | https://docs.ohc.network/3.0/category/access--governance | y | Index (concepts); 3.0 unmaintained banner, 3.1 latest | y |
| 37 | https://docs.ohc.network/3.0/category/access--governance-1 | y | Index (reference): Organization, User & Skills, Role, Permission | n |
| 38 | https://docs.ohc.network/3.0/category/billing | y | Billing concepts index | n |
| 39 | https://docs.ohc.network/3.0/category/billing-1 | y | Billing reference index | n |
| 40 | https://docs.ohc.network/3.0/category/clinical | y | Clinical concepts index incl. Consent, Care team, QR "clinician records" | y |
| 41 | https://docs.ohc.network/3.0/category/clinical-1 | y | Clinical reference index; Condition=Symptoms; QR "a patient gives"; FormSubmission | y |
| 42 | https://docs.ohc.network/3.0/category/definitions--terminology | y | Questionnaire, value set, response template concepts | y |
| 43 | https://docs.ohc.network/3.0/category/definitions--terminology-1 | y | Questionnaire "versioned, FHIR-inspired"; ObservationDefinition has body site | y |
| 44 | https://docs.ohc.network/3.0/category/facility | y | Facility/location/service/device concepts | n |
| 45 | https://docs.ohc.network/3.0/category/facility-1 | y | Facility reference index | n |
| 46 | https://docs.ohc.network/3.0/category/foundation | y | Base models: opaque IDs, audit, soft-delete, history | n |
| 47 | https://docs.ohc.network/3.0/category/medications | y | Medication concepts; statement covers "what a patient or relative tells you" | y |
| 48 | https://docs.ohc.network/3.0/category/medications-1 | y | Medication reference index | y |
| 49 | https://docs.ohc.network/3.0/category/platform | y | Tagging concept | n |
| 50 | https://docs.ohc.network/3.0/category/platform-1 | y | FileUpload, MetaArtifact ("a drawing on a patient"), reports | y |
| 51 | https://docs.ohc.network/3.0/category/scheduling | y | Schedule, booking, token concepts | n |
| 52 | https://docs.ohc.network/3.0/category/scheduling-1 | y | Scheduling reference index | n |
| 53 | https://docs.ohc.network/3.0/category/supply--inventory | y | Inventory concepts | n |
| 54 | https://docs.ohc.network/3.0/category/supply--inventory-1 | y | Inventory reference index | n |
| 55 | https://docs.ohc.network/3.0/concepts/access-governance/access-control | y | Roles/grants; PATIENT-scoped grants with expiry; standard role list | y |
| 56 | https://docs.ohc.network/3.0/concepts/access-governance/facility-organization | y | Dept/team tree; encounter access via responsible facility org | n |
| 57 | https://docs.ohc.network/3.0/concepts/access-governance/organization | y | Org tree; palliative groups "Volunteer, ASHA Worker, or MLSP" | y |
| 58 | https://docs.ohc.network/3.0/concepts/access-governance/user | y | User=Practitioner; TOTP MFA; service accounts | y |
| 59 | https://docs.ohc.network/3.0/concepts/billing/account | y | Patient billing account (not a login) | n |
| 60 | https://docs.ohc.network/3.0/concepts/billing/charge-item | y | Charge item pricing/lifecycle | n |
