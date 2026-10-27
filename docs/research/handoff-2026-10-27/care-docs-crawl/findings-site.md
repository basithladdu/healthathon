# findings-site (coordinator copy; 75 pages/files read; GitHub org HTML 403; care-abdm README 404 → cloned 969a278; raw in site-raw/)
1. Palliative NGOs run CARE: Pallium India launched CARE HMIS 5 Dec 2025 (IP/OP/home-care; 18 beds, 17 OPDs, 138 staff, 16 home-care teams) https://ohc.network/blog/one-million-encounters-paperless-governance , https://ohc.network/blog/care-dashboards-testing-new-deployments ; Niram Palliative Care, Tumkur (75-bed NGO) onboarding; Arike CARE 2.0→3.0 https://ohc.network/blog/fhir-lab-module-and-care-3-deployments ; IAPC Kerala care.iapckerala.in https://ohc.network/blog/building-care-open-healthcare-foundation → realistic pilot partners. Corrects DEEP_DIVE §8.
2. Plugins add question types with body maps: care_pressure_sore_fe (24 Oct 2026, frontend-only, 21 front/19 back regions); care_dental_fe, care_vision_prescription_fe, care_io_balance_fe (coded Observations). HMIS page "Clinical drawings | Capture drawings, body maps, annotations". → frontend-only Sahara pain-body-map question plugin is credible. Qualifies DEEP_DIVE §5.1 limit 2.
3. ohcnetwork/care-abdm (MIT, 969a278) = AI-agent experiment, NOT production care_abdm; M1 ABHA mostly built; M2 HIP code-complete vs mocks only; M3 not done; no WellnessRecord.
4. Citizen-facing: health.kerala.care (Vibe 4 Wellness self-assessment, launched 1 Jan 2026) and kare.kerala.care → don't say CARE has no patient-facing surface anywhere [runs on core unverified].
5. ISO 82304-2 certified "CARE-based apps and wellness-oriented modules deployed in community and home-care programs"; ISO 27001:2022, 82304-1 https://ohc.network/foundation/iso
6. More plugin surfaces (PatientRegistrationForm, user profile sections, files tab, EncounterActions…); Plugs V3 "replace any part" vs docs opt-in register() — unresolved.
7. Patient extensions (care_people_fe primary_facility) → caregiver fields via extension possible.
8. Grid: Volunteer Module + Volunteer Visit Form (Oct 2025); Schedule X (opioid) dashboard; camera consent capture; responsibility state→ward; comfort devices queries; Santhwana Mithram proposal (30,000 women home-care workforce).
9. Questionnaire builder v2 (Aug 2026), FHIRPath computed fields, JSON import/export.
10. Home Care Grid labelled "Reference model" (HMIS "Field-ready").
11. Loom: "How to Build Healthcare Apps on CARE", 13:56, Zoom video visits built by AI.
12. npx @ohcn/care create (care_create) one-command local stack.
13. Metrics inconsistent; quote dated figures only.
14. Offline: CARE Clinic LAN-offline only; patient messaging = booking SMS only.
15. Foundation: Section 8, incorporated 2 Dec 2025; support@ohc.network; Slack; Malayalam docs locale; AI roadmap "Palliative-care risk scores", "Caregiver instruction drafts" (staff-side).
16. 83 public repos via search; none named palliative/family/caregiver/home-care.
