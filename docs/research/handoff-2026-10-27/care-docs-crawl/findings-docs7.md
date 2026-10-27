# findings-docs7 (coordinator copy of agent report; 53/53 /references pages, 3.1, none failed; raw7/, txt7/, log7.tsv)
## NEW or CONTRADICTS
1. CONTRADICTS FIT doc: Observation performer can be related_person — "performer ... Performer { type: PerformerType (related_person|user), id: str }. Use it when the performer differs from the data-entry user" (/references/clinical/observation); questionnaire "collect_performer bool ... Collect a Performer reference" (/references/definitions/questionnaire). id is free string (no RelatedPerson model); copy to Observation [unverified].
2. Docs claim FHIR ("Patient records are exposed through Care's REST API and FHIR Patient resources" /references/clinical/patient; base-model) — code found no FHIR endpoint; keep code finding.
3. "Once a questionnaire is active, edit and delete are off the table — move it to retired instead." Submit rules: dateTime needs timezone; boolean true/false/1/0; text max MAX_QUESTIONNAIRE_TEXT_RESPONSE_SIZE. Unused fields: formula, collect_time, is_observation, read_only, max_length.
4. "FormSubmission runs a draft→submitted lifecycle. Its response_dump is opaque JSON" (/references/clinical/questionnaire-response) — possible stand-in for plugin pending queue; access probably staff [unverified, low confidence].
5. Family access: care-team role must be SNOMED healthcare professional (family can't be care team); custom roles + PatientUser grants a user access to ONE patient → minimal "family caregiver" CARE account possible; "expiry ... Stored but not enforced by the model". MedicationAdministration actor must be existing User + needs an order (family-given doses can't be recorded). MedicationStatement update: only status, effective_period, note.
- patient_instruction on MedicationRequest dosage ("Patient-facing instructions") and ServiceRequest ("Instructions surfaced to the patient").
- MetaArtifact: "attaches a freeform metadata payload to any Care object ... a drawing on a patient" → pain-body points could be stored as JSON [unverified UI].
- Every record has `history` (versions + performer via history API); no content hash / family ack per version.
- Notes: "A TODO in the source flags organization-based access restriction as planned but not yet implemented."
- Consent verified_by: docs say external_id on write; code overwrites with signed-in user (consent.py:55) — keep code.
- Condition body_site "no current spec exposes it"; severity mild/moderate/severe. ObservationDefinition qualified_ranges label 7–10 "severe" but don't reject out-of-range.
- Tags: advance_directive, safety, contact. Location "ho House".
