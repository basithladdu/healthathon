# findings-docs2 (coordinator copy; 60/60, pages 1-35 are 3.0, 36-60 3.1; raw-docs2/)
1. Cite 3.1 not /3.0/.
2. MetaArtifact "a drawing on a patient" (object_value dict|list) could hold 3D pain body JSON; staff-written; display [unverified].
3. CARE_VS_SAHARA "Referrals: No" too strong → Partly: ResourceRequest inter-facility (transfer/supply/assistance), related_patient, categories comfort_devices/medicines/patient_care; staff-only.
4. EMRBaseModel.history full version history via history API → audit history alone not unique to Sahara.
5. FormSubmission draft/submitted/entered_in_error ("draft-to-finalised lifecycle").
6. Tag advance_directive/safety on patient.
7. patient_instruction on ServiceRequest + MedicationRequest dosage (+ as_needed_boolean/as_needed_for) — no patient screen shows them; Sahara could.
8. ObservationDefinition qualified_ranges flag, not reject; observations only with encounter.
9. Facility-authored value sets (local terminology) → our 12 body areas/17 symptoms as valueset without SNOMED.
10. Report templates (PDF/HTML), registries via plugins.
11. Files: signed S3 or inline base64, MIME sniffing, random keys (PII), archive-only; no photo category.
12. Kerala local-govt hierarchy (Grama Panchayat … Ward); facility types incl CBO/NGO; no palliative/hospice type.
13. 3.1 Responsibility (cross-facility duty groups) could model district palliative network; system roles list.
14. "A user in Care is the staff account of a person who works in your organization … separate from a patient record" → no family account; giving families Volunteer accounts goes against intent.
16. FlagRegistry per-facility feature flags (admin only) for plugin gating.
18. Schedule is_public; TokenBookingOTPReadSpec; booked_by nullable for self-service.
