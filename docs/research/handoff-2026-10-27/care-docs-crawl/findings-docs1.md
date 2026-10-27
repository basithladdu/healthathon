# findings-docs1 (coordinator copy; 60 /3.0/ pages, all fetched; raw-docs1/)
Key: these are 3.0 (old) docs — cite 3.1. 3.0 consent page lists Comfort care with no gap note; 3.1 keeps the gap note.
- Custom roles + PatientUser (role FK) → narrow relative access possible without plugin [unverified runtime]; each relative needs full CARE User (unique phone, gender) and uses staff web UI.
- Volunteer role can submit patient-level questionnaires (no encounter) and view non-encounter responses ("can_submit_patient_questionnaire … Volunteer"). Org type role "e.g. Volunteer, ASHA Worker".
- Observation performer related_person (contradicts FIT §4.4); submit format has no performer field → set from code/plugin.
- FormSubmission draft→submitted (blueprint missed it).
- Docs self-contradict on Observations; FHIR Patient claim vs code; care plan + emergency contacts in docs, not in code (only phone_number, emergency_phone_number).
- ServiceRequest categories incl counselling/education; intent "plan … agreed plan of care"; encounter-only, doctors/nurses/admins.
- Tags advance_directive could mark palliative enrolment; volunteers cannot tag.
- Form create: Admin/Facility Admin; edit/delete superuser; valueset superuser.
- Role grant expiry not enforced. Notes org-access TODO.
- MedicationAdministration category community (home/field), staff side.
- Create patient: ABDM/ABHA identifier; "Start an encounter… (flow coming soon)".
- Care team: SNOMED 223366009/224930009, dept members only.
