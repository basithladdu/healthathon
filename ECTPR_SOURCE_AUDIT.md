# ECTPR source audit — "Healthathon form" and "Saanthvana v 1.0"

## Files

| File | What it is | Author (document properties) | Dates (document properties) | SHA-256 |
| --- | --- | --- | --- | --- |
| `Healthathon form.docx` | Four-section outline of the record the product should capture | Created and last modified by Sujay Halkur Shankar | Created 10 Sep 2026 · modified 12 Sep 2026 | `453257CC911E10433352910810DFE69E40A7A33F90F6B0C9A7C3505464C28D95` |
| `Saanthvana v 1.0.docx` | Full Emergency Care & Treatment Preference Record (ECTPR): 19 sections and 6 appendices | Creator "Un-named" · last modified by a team member | Created 3 Sep 2026 · modified 13 Sep 2026 | `C2BE86ECBD8FFF63ABDF6BD3EA763699CABFC23764E9F271075385EADB493F0E` |

Both were received on 13 September 2026 and placed in the repository root beside `GOCD (1).pdf`, the earlier one-page clinical concept from the same clinical lead. The originals are unchanged; the hashes above identify them. `Saanthvana v 1.0.docx` is listed in `.gitignore` (see Privacy).

## What the documents are

### `Saanthvana v 1.0.docx` — the full ECTPR

- **Title and audience:** "Emergency Care & Treatment Preference Record — for adults with advanced, life-limiting illness — to be read first in the Emergency Department."
- **Built on (Appendix F):** the National Cancer Grid End-of-Life Care algorithm; the ISCCM–IAPC 2024 position statements (Indian J Crit Care Med 2024;28(3):200–250); the MoHFW draft withdrawal guidelines (2024); the Supreme Court judgments of 2018, 24 January 2023 and 11 March 2026; National POLST (USA); ReSPECT v3 (UK); and the ATS time-limited-trial workshop report (2024).
- **What it is (Section 2.2):** a signed summary of what the patient values and would and would not accept; a prompt for the emergency clinician; patient-held and portable across home, ambulance, ED, ward and ICU; revisable at any time.
- **What it is not (Section 2.2):** not a legally executed Advance Medical Directive; not authority to withdraw treatment already started (that follows the Primary and Secondary Medical Board process); not a way to demand non-beneficial treatment; not a DNACPR form.
- **Rules that govern everything else:**
  - "An unticked box means NO PREFERENCE HAS BEEN RECORDED. It does not mean refusal." (Section 1)
  - "IF IN DOUBT, RESUSCITATE" — full standard care if the record is unsigned, out of date, missing or ambiguous, if the problem is new and reversible, or if a patient with capacity now says something different. (Section 1)
  - "No preference recorded below this line is valid unless this section is completed." (Section 5, understanding and prognosis)
  - "This record is not valid until the clinician section is signed. An unsigned form is a conversation note, not a plan." (Section 19)

| Part | Content |
| --- | --- |
| 1 | ED Quick View: overall goal (A prioritise life · B balanced / ceiling of care · C prioritise comfort); the four answers needed in 20 seconds (hospital transfer, CPR, breathing-support ceiling, ICU); named decision-maker and treating contacts; the "if in doubt" rule |
| 2 | Eligibility (the surprise question plus decline criteria), what the record is and is not, the six-step completion sequence |
| 3 | Patient details: identity, address, language, diagnosis and stage, comorbidities, allergies, ECOG / PPS / Barthel, implanted devices, medicines, treating consultant and palliative team 24 h phones, local doctor, nearest hospital and travel time |
| 4 | Who decides: capacity now, existing directives, primary and alternate surrogate with basis for authority, and a substituted-judgement declaration read aloud |
| 5 | Understanding and prognosis: what was discussed, basis of the prognostic estimate, what the patient knows, handling of non-disclosure requests |
| 6 | What matters: the patient's own words, unacceptable outcomes, psychosocial, spiritual and practical needs |
| 7 | Hospital transfer (yes / only for listed reasons / no) and what the ambulance crew should do |
| 8 | CPR (attempt / DNACPR), reason, who discussed it, clinician signature, and a POLST-style consistency check |
| 9 | Oxygen and ventilation ceiling, levels 0–5, with conditions and invasive-ventilation limits |
| 10 | Time-limited trial: treatments, duration, review date, improvement and non-improvement markers agreed in advance, wishes at review, withdrawal arrangements and the Indian procedural note |
| 11–15 | Other organ support; feeding and fluids; blood products; surgery by category; anti-infectives, disease-modifying treatment, deprescribing, monitoring, anticipatory medicines |
| 16 | What will always be provided, whatever else is limited |
| 17 | Place of care and death, home-death certification, donation, rituals, practical and bereavement arrangements |
| 18 | Changing or cancelling the record, family disagreement, non-beneficial requests, mandatory review triggers, review log |
| 19 | Signatures (patient, surrogate, witnesses, clinicians), copies issued, optional conversion to a legal AMD |
| Appendix A | Conducting the conversation (set up → ask → permission → tell → respond to emotion → ask again → map values → recommend → close the loop), phrases to avoid |
| Appendix B | Recognising the dying phase (NCG algorithm) |
| Appendix C | The Indian legal and procedural position |
| Appendix D | Running the beta: ethics, legal review, translation (Telugu, Hindi, Urdu), Class 8 readability, low-literacy pathway, training, retrieval plan (including a QR code on the wallet card), indicators and known design risks |
| Appendix E | Wallet / bedside card and the version-control box |
| Appendix F | Sources |

### `Healthathon form.docx` — the clinical lead's outline for the product

A four-section skeleton that reuses ECTPR blocks:

1. **General information** — patient details; physician details (primary oncologist, palliative care physician, wider team, and a proposed option to add a local physician who can be briefed); family details, preferred language, and a primary / alternate decision-maker table with basis for authority.
2. **Preliminary medical information and patient values** — diagnosis and stage, ECOG, capacity, existing directives, what the patient knows, the patient's own words and unacceptable outcomes.
3. **Decisive actions** — hospital transfer, CPR, the 0–5 oxygen and ventilation ceiling, dialysis / CRRT and other parameters, ICU (including vasopressors), feeding, and the overall goal A / B / C.
4. **Signatures and declarations** — patient, surrogate, witnesses, clinicians.

The extracted text repeats several blocks (capacity, directives, values, the ventilation ladder). That looks like duplicated text boxes in the Word file rather than intended repetition; confirm with the author.

## Alignment with the current prototype

Principles the documents and Continuity Loop already share:

- Silence is not a preference ("an unticked box means no preference has been recorded" ↔ "silence is recorded as not discussed").
- A record is not valid until a clinician signs it ↔ the physician-controlled release gate.
- Revision without overwriting (review log, cancellation procedure) ↔ append-only versions and the audit log.
- Retrieval is the point of the whole exercise (Appendix D: "a pilot with no retrieval plan measures nothing") ↔ the purpose-logged retrieval gate.

## Section-by-section mapping

| ECTPR part | Continuity Loop today | Gap |
| --- | --- | --- |
| 1 · ED Quick View | Retrieve shows five narrative fields (priorities, participants, topics, unresolved, follow-up) | No overall goal A/B/C, no structured four answers, no "if in doubt, resuscitate" banner |
| 2 · Eligibility | Treating physician enrols a patient | No surprise-question or decline criteria captured |
| 3 · Patient details | Profile shows DOB, sex, team, diagnosis line, primary contact | No language, functional scores, devices, medicines, local doctor, nearest hospital or travel time |
| 4 · Who decides | "Participants and surrogate" domain; authorisation dropdown at release | No capacity status, directive status, alternate surrogate, basis for authority or substituted-judgement declaration |
| 5 · Understanding | "Understanding and priorities" domain | Not a gate; no record of what the patient knows or how non-disclosure is handled |
| 6 · What matters | Priorities draft field | Unacceptable outcomes and psychosocial needs not captured |
| 7 · Hospital transfer | "Preferred care setting" domain (discussed / not) | No transfer rule or ambulance instruction |
| 8 · CPR | Not captured | Most sensitive section — see boundary below |
| 9 · Ventilation ceiling | "Ventilation" domain (discussed / not) | No 0–5 ceiling or conditions |
| 10 · Time-limited trial | "Time-limited treatment trial" domain | No duration, review date or agreed markers |
| 11–15 · Treatments | "Dialysis" and "Ward and ICU care" domains | No vasopressors, feeding, fluids, blood, surgery or medicines |
| 16 · Always provided | Not shown | Important reassurance for families and patients |
| 17 · Place of care and death | Care-setting domain only | No place of death, certification, rituals or donation |
| 18 · Changes and review | Append-only versions, audit log | No mandatory review triggers or disagreement pathway |
| 19 · Signatures | Authorisation dropdown and physician attestation | No patient / surrogate / witness signatures; no record of copies issued |
| Appendix A · Conversation | Guided conversation page (8 domains) | Page does not follow the conversation steps |
| Appendix D · Beta | Pitch and pilot notes | Indicators (reach, retrieval within 10 minutes, concordance) not tracked |
| Appendix E · Wallet card | Not present | Card and QR retrieval not built |

## Scope and safety boundary

The hackathon admits only assistive, non-clinical tools: "Are you giving clinical guidance? If yes, it's out." Recording what the patient and clinicians agreed is documentation; the app deciding any of it is not. When building from the ECTPR:

- **CPR (Section 8) and the POLST consistency check:** the app may point out that the form asks for a reason when CPR is ticked below an intubation ceiling. It must not recommend DNACPR.
- **Prognosis (Section 5.2):** record which basis the clinician used. Never calculate a prognosis in the app.
- **Dying phase (Appendix B):** a clinical judgement. Never detect or declare it automatically.
- **Legal status (Section 19.6, Appendix C):** never present the record as a legal Advance Medical Directive.
- **Existing landing-page boundaries** ("does not create a DNAR order… or any legally binding directive") remain true. A clinician-signed CPR preference shown in the app needs wording that keeps them true.

## Product features the documents support

- ED Quick View as the first screen of Retrieve (Section 1).
- Wallet / bedside card with a QR code for 2 a.m. retrieval (Appendix E; Appendix D.1 retrieval plan).
- Care team and nearby services: treating consultant and palliative team 24 h phones, local doctor, nearest hospital and travel time (Sections 3 and 7).
- Patient and surrogate declarations before release (Section 19).
- "What will always be provided" for patients and families (Section 16).
- Languages and readability for the patient and family views (Appendix D.1).
- Pilot indicators, with retrieval within 10 minutes as the primary measure (Appendix D.3).

## Build decisions taken on 13 September 2026

- The ED Quick View and the Appendix E wallet card with a QR code are built first. The full Guided conversation, draft and release gate stay as they are until the clinical lead confirms which ECTPR sections are in the hackathon scope.
- Nearby care uses real OpenStreetMap places and never invents individual doctors.
- Patients get their own sign-in with edit rights for their details, the emergency card and consent; family access stays read-only.

## Privacy

- `Saanthvana v 1.0.docx` has a filled-in name and age in the Section 1 header, and its document properties list the same person as last editor. It is listed in `.gitignore` so it stays on the local machine and is never pushed to the GitHub remote.
- `Healthathon form.docx` contains no patient data; its properties name the clinical lead as author.

## Open questions for the clinical lead

1. **Version:** the filename says "v 1.0"; the Appendix E version-control box says "ECTPR Beta v0.1". Which is authoritative?
2. **Name:** is "Saanthvana" the intended product name? It appears only in the filename.
3. **Healthathon form:** are the repeated blocks intentional?
4. **Scope:** is CPR (Section 8) in the hackathon build, or recorded only as discussed / not discussed?
5. **QR code:** should it link to the hospital record (needs a server) or carry an offline summary?
6. **Languages:** confirm the pilot languages (Appendix D lists Telugu, Hindi and Urdu).
