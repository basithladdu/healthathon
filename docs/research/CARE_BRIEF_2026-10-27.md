# CARE: short brief for the owner (27 Oct 2026)

Plain summary of what CARE is, how it relates to Sahara, and what to do about it before the 7 Nov submission. Everything below is sourced. **[unverified]** marks things nobody on the team has tested.

Sources used: the organiser email (`source-materials/team-context/2026-10-23/documents/00003571-…Build Sprint Resources….pdf`), the 23 Oct archive of 58 ohc.network pages and 12 docs pages (`output/research/care-website-20261023/`), the earlier audit (`docs/research/care-resources-and-sahara-audit-2026-10-16.json`), the team chat (`source-materials/team-context/2026-10-23/canonical-chat.txt`) and a fresh check of the live sites on 27 Oct.

**Fresh check, 27 Oct:** the palliative page, the Build on Care page, the plugin guide and the consent docs say the same as the 23 Oct archive. Only the menus and footers changed. The site sitemap still lists the same 32 URLs. The newest blog post is still 30 Sep. The CARE backend repo's head is still `2bae933`, the same commit the 23 Oct review inspected. The frontend (`care_fe`) has moved on to `81e7515`, but its consent screen and consent types are byte-identical to the inspected version. So the consent gaps below still apply today.

---

## 1. What CARE is and who runs it

- **CARE** is an open-source (MIT) EMR/hospital platform. The **Open Healthcare Network (OHC) Foundation** in Kochi, Kerala, maintains it ([about](https://ohc.network/about)).
- It is built with Django REST Framework (backend) and React (frontend) ([developers](https://ohc.network/developers)). OHC describes its records as "FHIR-aligned", with FHIR R5, SNOMED, LOINC and ICD-10 support ([security & compliance](https://ohc.network/about/security-compliance)). **[unverified]** We have not checked whether it exposes a standard FHIR endpoint or only its own REST API.
- It is meant to be self-hosted. "Zero license fees" covers the code only. Hosting, support and training are still real costs.

## 2. What CARE already does for palliative care

The [CARE Home Care Grid page](https://ohc.network/solutions/palliative-care) lists these features:

| Need | What OHC says CARE has |
|---|---|
| Registries | "Patient registry… patients, families, caregivers, addresses, facility linkage, care needs, risk status" |
| Home visits | "Mobile-first workflows for visit lists, vitals, symptoms, nursing needs, medicine needs, caregiver concerns, notes, and next visit dates" |
| Care teams | "Assign field workers, nurses, doctors, volunteers, NGOs, facilities, and follow-up owners" |
| Care plans | "Turn problems, goals, interventions, home-care tasks, follow-up schedules, caregiver instructions, and review cycles into action" |
| Referrals, dashboards | Escalation to doctor or facility review; coverage and overdue-visit dashboards |
| Scale (OHC's own claim, not independently checked) | Kerala: "330,000+ registered patients", "1.3M+ home-care consultations", "12,000+ daily users" |

- **Consent** ([docs](https://docs.ohc.network/concepts/clinical/consent/)): consent categories include Do Not Resuscitate, Advance Directive and Advance Care Directive. Two gaps are documented:
  - "Care also holds a Comfort Care category, but the consent form has no option for it… This is a known gap in the product."
  - CARE can store a Family or Validation (patient) confirmation of a consent, but "The current version of Care has no screen to add, show, or remove a verification" ([verify a consent](https://docs.ohc.network/flows/clinical/consent/verify-a-consent/)).
- **Goals of care:** the [patient-enrollment playbook](https://docs.ohc.network/deployments/palliative/playbooks/patient-enrollment/) says "Document palliative-specific goals of care separately from acute encounter notes". Its "Palliative program enrollment" step is marked **(coming soon)**.
- **AI:** OHC plans voice-native visit notes, patient summaries and follow-up drafts, with the rule "AI drafts, humans decide". The example plugins use Medispeak, not Sarvam (audit, 16 Oct).
- **Patients and families:** CARE is a staff tool. Its patient login (OTP) is a separate, limited scope (audit, care_scaffold auth notes). **[unverified]** We found no patient/family companion app comparable to Sahara.

## 3. Sahara vs CARE

**Where they overlap.** Don't present these as unique to Sahara:
- patient registry and care profiles;
- care team, visits, tasks, medicines and documents;
- consent records;
- symptom recording during visits;
- AI-assisted note drafting (CARE's is planned or in plugins; Sahara's AI is switched off today).

**What Sahara does that CARE doesn't, as far as we found:**
- **A phone app for the patient and family.** It has a symptom diary with a 3D pain body, a daily check-in, medicine and visit lists, family invites by WhatsApp link, Hindi/English text and a centre directory of 1,631 listings. CARE's frontline workflows are for staff.
- **The goals-of-care conversation flow:**
  1. recording with each person's consent;
  2. a transcript;
  3. answers to the goals-of-care questions, linked to the transcript;
  4. doctor review of the exact note version;
  5. **patient/family confirmation of that exact version**.

  CARE has the data fields for family/patient verification of a consent (entered by staff) but no screen for them (see section 2). Sahara's confirmation is done by the family themselves on a goals-of-care note, so it is related to that gap, not the same feature.
- On-phone, offline-first use. Recordings and reports stay on the phone.

**What is not true yet on Sahara's side** (HANDOFF.md, WORK_STATUS.md):
- AI drafting is off.
- Goals-of-care answers come from word matching, not AI.
- Doctor verification is a manual approval in the database.
- The hospital QR is not built.
- Nothing connects to CARE today.

## 4. Integration options

| Option | What it means | Effort (estimate) | What judges would likely value |
|---|---|---|---|
| **A. Describe it only** (recommended now) | On the deck's deployment slide, say Sahara is the patient/family side and could feed a hospital's CARE instance. Give the specific CARE gap it fills (patient/family confirmation of a goals-of-care note). | 1–2 h of slide text | The template asks: "Will solution need to be integrated with other systems? If yes, please outline" (slide 11). This answers it honestly. |
| **B. Export a FHIR-style bundle** | Sahara exports a confirmed care note as a JSON document (Patient + Consent + DocumentReference/Composition). The web prototype already has a mock ABDM/FHIR bundle generator (`prototype/app/fhir-export-modal.tsx`, browser-local). Android has none. | 1–2 days for a file export on Android with unit tests. A real push into CARE needs a running CARE instance, staff authentication and a field mapping: about 1 week or more **[unverified]** | Shows interoperability thinking. A "standards-aligned export" is credible if we say it isn't connected live. |
| **C. Small CARE plugin** | A React frontend app (manifest + module federation), optionally with a Django backend plugin ([plugin guide](https://docs.ohc.network/contributing/plugins/), [care_scaffold](https://github.com/ohcnetwork/care_scaffold)). Example: a "Goals-of-care confirmation" tab on a CARE encounter that shows and records Family/Validation verifications. | Needs Docker, a local CARE stack and React/Django work: about 1–2 weeks for a demo with synthetic data **[unverified]**. Kotlin code can't be reused inside CARE. | OHC offered exactly this. It would show reuse, but it's risky with 11 days left, and it splits effort away from the Android app that already works. |
| **D. Move Sahara onto CARE** | Replace Sahara's backend with CARE | Weeks; a rewrite | No: the organisers didn't ask for it, and it throws away working work. |

**Recommendation: A now. Do B (file export only) if time is left after the demo video. Mention C as the "what next" pilot path.**
**Confidence: moderate.** It rests on the organisers' own wording ("opportunity", "explore") and the documented CARE consent gap. We don't know how much judges reward actual CARE use. No judging rubric mentions CARE; the saved webinar names healthcare relevance/impact and technical feasibility (`PROJECT_CONTEXT_MAP.md`).

## 5. What the organisers actually asked: optional

The email "Health-a-thon 2026 | Build Sprint Resources from Open Healthcare Network" (15 Oct, 2:18 PM; full text in the PDF above):

> "OHC is delighted to extend its support to all shortlisted teams by providing the **opportunity** to build on CARE, its existing open-source EMR platform, and explore its plugins."

> "We **encourage** you to explore these resources and **assess how CARE and its associated tools could support** your solution development."

> "For hackathon development, we recommend using synthetic data and documenting the intended user, workflow, setup instructions and known limitations."

- The email makes no requirement, grant condition, hosting offer or deadline about CARE.
- The Phase 2 updates email (13 Oct) and the 13-slide final submission template (21 Oct) don't mention CARE at all. The template's only integration question is on slide 11 (quoted in section 4).
- Build Sprint ends **7 Nov 2026** (Phase 2 updates email).
- Team view, 15 Oct (Sujay): "our idea could have been integrated but we've built as a standalone thing". No team decision to integrate is recorded since.

Resources linked in the email:
- [Build on Care](https://ohc.network/build-on-care)
- [Loom walkthrough](https://www.loom.com/share/524a50d9b57142d7853c3acf35035250) (full transcript needs a sign-up)
- [plugin docs](https://docs.ohc.network/contributing/plugins/)
- [care_scaffold](https://github.com/ohcnetwork/care_scaffold)
- [OHC skills](https://github.com/ohcnetwork/skills)
- [care_filly](https://github.com/ohcnetwork/care_filly)
- [care_ai_vision_fe](https://github.com/ohcnetwork/care_ai_vision_fe)

## 6. Message for the team

The prepared team message in `TEAM_HANDOFF.md` ("We reviewed CARE…") is still accurate and has not been sent. Send it as is, adding one line: "CARE's own docs list family/patient verification of a consent as a known gap with no screen (staff would record it). Sahara lets the patient or family confirm the exact goals-of-care note themselves." Note: CARE's verification is entered by staff on a consent record; Sahara's confirmation is done by the family on a goals-of-care note. They are related, not the same feature.
