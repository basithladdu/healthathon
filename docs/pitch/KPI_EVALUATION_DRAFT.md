# KPI and evaluation draft (HANDOFF A20, A21)

**Status: DRAFT, 27 Oct 2026. The team must approve it before anything goes on a slide.** Nothing here is a result. Sahara has no pilot data yet. All targets are **illustrative**.

## Where this comes from
- **A20 (mentor, 20 Oct):** measure family stress, money saved and hospital time saved (`docs/research/CHAT_AUDIT_2026-10-27.md`).
- **A21 (Sujay, 24 Sep):** measure patient requests and documented conversations, before against after.
- **Earlier agreement (25 Sep):** three headline measures: discussions started (patient vs doctor, counted separately), written and confirmed notes, and time to find the latest note in an emergency. See `docs/pitch/2026-09-24-impact-plan.md`.
- **Final template, 27 Oct:** slide 8 is "Evaluation and User Validation". It asks for test method, metrics, AI results, user feedback, improvements and limits (`source-materials/healthathon-phase-2/2026-10-27-final-deliverables/template-text.md`).

This draft keeps the 25 Sep measures and adds evidence-based baselines. It proposes **3 KPIs**. The team picks 2 or 3.

## What the app can count today (checked in code on `main`, 27 Oct)
- Signed-in records are saved on Sahara's Supabase server in `public.healthathon_records`:
  - care notes are `kind='note'`, with `version` and `published` time;
  - confirmations are `kind='ack'`, with `relationship` (Patient or Family / carer) and `at` time;
  - daily entries and symptom notes are `kind='entry'`.
  Source: `android/supabase/migrations/20261009162933_android_care_accounts.sql` and `20261016065132_patient_first_version_confirmations.sql`.
- Symptom notes record the score, the date and time seen, and **who answered** (patient, helped, or carer) (`CareSymptomUi.kt`, `SymptomDiaryRules`).
- A family's "Ask for a goals-of-care conversation" is saved as a care task entry (`kind='entry'`, task) (`CareUi.kt`). Note: `public.healthathon_clinician_requests` is for doctor sign-up approval, not patient requests.
- **Not built:** there is no KPI report screen and no export. Counting means a read-only database query that the owner runs. Records of people who are **not signed in** stay on the phone and cannot be counted. So every pilot family must use a signed-in account.
- **Not built:** a "the doctor saw this" receipt. The app cannot show whether a clinician opened a diary entry.

## KPI 1: Goals-of-care notes confirmed by the patient or family

| | |
|---|---|
| **Definition** | Of the patients enrolled in the pilot, the share with at least one published goals-of-care note that the patient **or** a family member confirmed (that exact version) within the 8 weeks. Report the patient and family confirmations separately. Also report as counts: patient requests, doctor-started talks, notes published. Never add requests and talks together. |
| **How the app measures it** | Count `note` records per patient, and `ack` records that point to a note version, from `healthathon_records`. Count requests from task entries titled "Ask for a goals-of-care conversation". The doctor marks whether the talk was patient-asked or doctor-started (today this needs a manual tally; there is no field for it). |
| **Baseline** | **Measure at the pilot site** for 2–3 weeks before the start, from its usual records (25 Sep plan). Published context: 93.6% of Indian physicians agree advance care planning is needed, but only 25% feel they have the time (PMID 42057103, 2026, in `landscape/india-palliative-evidence.md`). In Kerala government sites, psychosocial and spiritual issues were routinely documented at only **4%** (Lijimol, IJPC 2020, PMID 33623312, in `partners/iapc.md` and `partners/kerala-government-grid.md`). One Chennai quality-improvement project went from **0% to 92%** documentation (24/26) with a paper process (PMID 41340644; `submission/phase-2/README.md`). That project is not Sahara. |
| **8-week target (illustrative)** | At least half of enrolled patients have a confirmed note by week 8. |
| **Data source** | Sahara server tables above (read-only query); the site's own records for the baseline; the doctor's tally of who started each talk. |

## KPI 2: Family pain scores between visits

| | |
|---|---|
| **Definition** | Share of **patient-weeks** with at least one pain score (0–10) entered in the symptom tracker, plus the share of clinic or home **visits** where a pain score from the previous 7 days was there for the clinician to see. |
| **How the app measures it** | Symptom notes (`kind='entry'`) have the symptom, score, date seen and who answered. Visits are entries of kind `visit` with a date. Count per patient per week. Report "who answered" (patient, helped, carer) next to the number. |
| **Baseline** | Patient-reported pain was routinely documented at only **21%** of Kerala government palliative sites (65% of NGO sites) (Lijimol, IJPC 2020, PMID 33623312; `partners/iapc.md`, `partners/kerala-government-grid.md`). Kerala PHC nurses visit bed-bound patients about **once a month** (IAPC status report, `partners/iapc.md`). Pallium India visits about twice a month (`partners/pallium-india.md`). Reference for completion: an Indian phone symptom form (SMART-ESAS, AIIMS) was completed by about **51%** (PMID 38386957). A US weekly survey reached 91.5% (PMID 39920394); that is a rich-country figure, not a target for India. |
| **8-week target (illustrative)** | At least 60% of patient-weeks have a pain score, and at least 60% of visits have one from the last 7 days. |
| **Data source** | Sahara server symptom entries (read-only query); visit dates from the app or the site's visit register. |

**Caution:** do not call the scale ESAS-r or IPOS. The clinicians have not yet chosen the scales (HANDOFF E2, E3). Our tracker is 0–10 per symptom.

## KPI 3: Caregiver burden (family stress)

| | |
|---|---|
| **Definition** | Caregiver burden score at week 0 and week 8, using the 3-item **Brief AMC-CBI** ("lost hope", "feel sad", "financial burden"; cut-off 3 or more). Report the share at or above the cut-off at each time, and how many caregivers completed both. |
| **How the app measures it** | **It does not.** There is no burden check in the app. The pilot coordinator asks the 3 questions by phone or on paper at week 0 and week 8. (Adding it to the app is a later build.) |
| **Baseline** | Kerala: **32.1%** of caregivers had moderate-to-severe burden (Ernakulam, n=115, PMID 39537204; `partners/arike.md`). Pallium India: **54.8%** moderate burden (n=270, PMID 41239314; `partners/pallium-india.md`). Tata Memorial geriatric clinic: 76.4% little or no burden (PMID 35750629), so it varies by setting. Brief AMC-CBI: IJPC 2026, PMID 42694816, tested by phone on 60 rural Kollam caregivers (`partners/iapc.md`). |
| **8-week target (illustrative)** | At least 80% of caregivers complete both checks. Report the change as it is. **Set no reduction target**: 8 weeks and a small group cannot show an effect, and the evidence file says not to claim Sahara lowers burden (`landscape/india-palliative-evidence.md`, "Do not claim"). |
| **Data source** | Coordinator's paper or phone form. Permission to use the Brief AMC-CBI is **[unverified]**: ask the authors first. |

**Money and hospital time (rest of A20):** no simple, honest measure fits 8 weeks. If the team wants one, add two questions at week 0 and week 8: "trips to hospital in the last month" and "money spent on travel in the last month". Baseline: median ₹4,240 a month out of pocket, mostly travel and lost wages (Hyderabad, n=400, PMID 41164715). Report it only, with no target.

## Option the team may prefer: emergency retrieval time (agreed 25 Sep)
- **Definition:** seconds from "show me the latest goals-of-care note" to the correct latest version on screen, in a timed practice drill.
- **App:** the emergency QR card on Today (`EmergencyCardUi.kt`) and the note reader.
- **Baseline:** **no published number found.** Time the site's current way (paper file, WhatsApp) in the same drill before the start.
- **Target (illustrative):** correct latest version in under 60 seconds in at least 9 of 10 drills.

## Pilot assumptions (all to confirm)
- No site, partner or ethics approval exists yet (HANDOFF A11, H17, I1, I2, I4). The Kerala grid research suggests one unit with 30–50 families (`partners/kerala-government-grid.md`). That is a suggestion, not a plan.
- Consent to record a talk, consent to use pilot data, and DPDP terms are separate and must be in place first (HANDOFF G5, G6).
- Anonymising before the doctor sees data is not built yet.

## Evaluation slide text (max 60 words; team must approve)

> **How we will measure (8-week pilot, targets illustrative)**
> 1. Goals-of-care notes confirmed by patient or family. Baseline: counted at the site first.
> 2. Weeks with a family pain score before the visit. Published baseline: pain recorded at 21% of Kerala government sites.
> 3. Caregiver burden, Brief AMC-CBI, week 0 and 8.
> No pilot results yet.

(54 words.) Cite in the slide notes: PMID 33623312 (21%), PMID 42694816 (Brief AMC-CBI).

## Team to decide
- [ ] Which 2–3 KPIs (KPI 1–3, or swap one for retrieval time).
- [ ] The targets, or drop targets and say "baseline only".
- [ ] Who runs the read-only counts, and who is the pilot coordinator.
- [ ] Permission for the Brief AMC-CBI.
