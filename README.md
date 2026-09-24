# Saathi (साथी)

Saathi is a browser app for cancer patients, families and clinicians to keep goals-of-care conversations and follow-up together. It is entered under **Cancer → Patient / Caregiver → Family & Caregiver Support**.

## What works today

Patients or family members can request a conversation. A clinician can record it, check a Care Note and sign it with a typed name. The patient then reviews and signs that same version; earlier versions remain available. Optional form sections cover patient details, family contacts, values and care choices already discussed. The app also supports report uploads, source-linked text, a calendar, a patient journal, and named family tasks. A person can check uploaded text and add a confirmed treatment date to the care story with its original passage and report attached. Example records shown when the app first opens are fictional. App state is kept in the current browser; there is no shared backend.

## What is planned

Connected AI drafting, reliable transcription, scanned-image reading, verified accounts, and sharing between separate phones are not connected. AI does not diagnose, interpret test results, or recommend treatment. A typed name is not a verified digital signature, and a Care Note is not a legal directive.

## Run locally

Requires Node.js 22.13 or newer.

```powershell
cd prototype
npm install
npm run dev:next
```

Open the local URL printed by Next.js, usually `http://localhost:3000/`.

Run the focused state and workflow tests with:

```powershell
npm test
```

The app uses Next.js, React, TypeScript and MapLibre.

## Project links

- [Open Saathi](https://saathi.wedevit.in/)
- [GitHub](https://github.com/basithladdu/healthathon) · [Questions? Contact us on WhatsApp](https://wa.me/919553321211)
- [Round 1 deck](submission/Saathi_Healthathon_Final.pptx)
- [PDF to share](output/pdf/Saathi_Healthathon_Final.pdf)
- [Revised form answers, including the proposed Sarvam AI stack](submission/Round_1_Answers_2026-09-24.md)
- [Family-support research and complete practical journeys](docs/pitch/2026-09-24-family-support-research.md)
- [Proposed impact plan](docs/pitch/2026-09-24-impact-plan.md)
- [Prototype setup notes](prototype/README.md)

## Team

- Dr Sharada Vinod Kutty
- Dr Sujay Halkur Shankar
- Shaik Abdul Basith
- Shaik Muhammad Awaiz
- Shirin Ayub
