# findings-docs3 (coordinator copy; 60/60 concepts + access-governance flows, 3.1, none failed; raw-docs3/)
1. Questionnaire slug: docs "The slug must have 5 to 25 characters" (https://docs.ohc.network/concepts/definitions/questionnaire) vs code 5–50 (deep dive §5). Our `sahara-goals-of-care-conversation` = 33 chars → rename ≤25 (e.g. `sahara-goc-conversation`).
2. Superuser-only form/valueset editing in UI: "Care shows this button only to superusers… To change a saved questionnaire, you must be a superuser." "Only a superuser can create, change, or retire a valueset". Forms retire, never delete. → hospital needs instance superuser to import our forms.
3. Docs: "each answer on the form becomes an observation" (concepts/clinical/observation) conflicts with code (only coded answers in an encounter). Keep code.
4. CARE says it doesn't fully export FHIR: "Care does not export a fully validated FHIR QuestionnaireResponse resource"; files "no formal FHIR R5 resource mapping"; notes not mapped; care team "does not match the standalone FHIR CareTeam resource".
5. Consent "Supporting Documents… for example a scanned signed consent form"; files attach to Patient/Encounter/Consent/DiagnosticReport/ServiceRequest; 100 MB; archive-only → staff could attach Sahara's confirmed-note PDF to an Advance Care Directive consent.
- Consent per encounter; category/decision/dates fixed; only status+note change; record needs Admin/Doctor/Nurse/Facility Admin on active encounter.
- MedicationStatement: source Patient/Practitioner/Related Person (default Patient, fixed); dosage + start date mandatory; reason ≤100.
- Report templates: Discharge Summary / Patient Summary / Account Report (Jinja2→PDF/HTML); Volunteer & Staff can generate.
- Patient: Volunteer Contact (CARE users), tags, identifiers; no caregiver field.
- Encounters: max 5 open/facility; locks; restart window default 0h; "Hospice" disposition.
- Condition: SNOMED + active encounter; Mild/Moderate/Severe.
- ObservationDefinition: Survey category, LOINC required, interpretation rules [unverified for questionnaire answers].
- Roles: only super admin creates custom roles; can't grant beyond own; "Responsibilities" flat groups (might model district palliative team [unverified]).
- Public facility directory flag; public schedules.
