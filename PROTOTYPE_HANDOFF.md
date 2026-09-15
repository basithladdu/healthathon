# Continuity Loop prototype handoff

> Current status, 13 September 2026: this document's deployment identifiers describe the August build. The public URL was reopened in Chrome and still showed the older clinician-only interface. The new patient portal, appointments, compact entry screen and local fixes are not deployed. See [current gap review](GAP_REVIEW_2026-09-13.md) and [pull review](PULL_REVIEW_2026-09-13.md).

## Production

- Canonical public URL: https://continuity-loop-healthathon.vercel.app
- Vercel project: `continuity-loop-healthathon`
- Deployment ID: `dpl_DnH4qgbkT2brBeVShJA5U1gNj8Ay`
- Deployment state: `READY` / production
- Deployed: 26 August 2026 at approximately 18:17 IST
- Local source: `C:\Users\basit\Downloads\CODE\healthathon\prototype`
- Source commit: `54855185e429cd9eebe183915127771825126fe2`
- Deployment URL: https://continuity-loop-healthathon-2wv15d3vo.vercel.app
- Owner-only Sites URL: https://goc-continuity-loop.basithmuqeeth.chatgpt.site (version 10; ChatGPT sign-in required)
- Sites deployment: `appgdep_6a8ee03ecba4819198c0eccfc34e188e` (`succeeded`)

The Vercel URL above is the public handoff target. The Sites URL is the owner-only publication of the same validated source.

## Product position

Registered use case:

**Cancer Care -> Clinician focused -> Patient Follow-up & Continuity of Care**

Product:

**Continuity Loop — a clinician-controlled goals-of-care follow-up and verified clinical-handoff workflow.**

The landing surface prioritises the receiving physician's handoff moment. The continuity worklist is the operational layer behind it, not the primary pitch.

## Demonstrated workflow

1. A treating clinician opens an eight-domain guided conversation checklist.
2. Each domain is explicitly marked discussed, not discussed or deferred/needs clarification.
3. A preloaded synthetic source note is converted into source-linked structured fields.
4. The physician reviews every field and records patient/surrogate acknowledgement.
5. Publication remains blocked until all review checks, physician attestation and structured-field resolution are complete.
6. Publishing creates Version 2 without overwriting Version 1.
7. An emergency physician records an emergency access purpose and care relationship before retrieval.
8. The latest verified handoff can be copied or printed, and the simulated access event appears in the audit trail.
9. The continuity worklist supports enrolment, ownership, outreach outcomes and rescheduling.

## Truthfulness boundary

- All names, identifiers, dates and clinical content are synthetic.
- The guided checklist is a product hypothesis pending clinical-lead approval.
- Extraction is deterministic prototype behaviour; no external AI model is called.
- No audio is recorded or retained.
- No hospital EHR, ABDM, messaging service, identity provider, government ID or clinical database is connected.
- Authentication, authorisation, identity and audit controls are simulated in browser memory.
- State resets on reload.
- The product is a clinical note/workflow demonstration, not a living will, Advance Medical Directive, DNAR order or treatment instruction.
- It does not diagnose, predict prognosis, select eligible patients, score clinical risk, recommend treatment or replace clinician judgement.

## Implementation

- React 19
- TypeScript
- Next.js 16 App Router
- Native Next.js build for Vercel
- Vinext/OpenAI Sites-compatible secondary build
- Authored responsive CSS; no component theme package or generated decorative imagery

Luna 5.6 was used as a read-only workflow and UI critic. Its main finding—make the verified receiving-physician handoff primary and keep the worklist secondary—was incorporated by the main implementation.

## Verification completed

- `npm.cmd run lint` passed with no warnings.
- `npm.cmd run build` passed through all five Vinext stages.
- `npm.cmd run build:next` passed compilation, TypeScript and static-page generation.
- Local desktop viewport: expected title and hero, no error overlay, no horizontal overflow.
- Local 390 x 844 viewport: no horizontal overflow or undersized visible interactive targets.
- Guided conversation, source-linked draft, verification gate, Version 2 publication, retrieval, enrolment and outreach transitions were exercised.
- A clarification-marked structured field was verified to block publication with an actionable field-level message.
- Worklist filters for `Awaiting outreach` and `Escalated` were verified with patient-unavailable, discussion-completed and escalation transitions.
- Per-patient record and retrieval state were checked to prevent one patient's state leaking into another.
- Final Vercel cloud build completed in 14 seconds and deployment reached `READY`.
- Production URL returned HTTP 200 with the expected title and hero.
- Production favicon returned HTTP 200 and the final browser console check returned zero errors.
- The UI refinement pass was rendered at 1440px desktop and 375px/390px mobile widths: navigation, cards, forms, worklist search and enrolment modal remained usable without horizontal page overflow.
- Production desktop and 390 x 844 mobile views were rendered in the browser.
- Production mobile emergency shortcut selected the emergency physician, emergency retrieval reason and emergency receiving relationship.
- Entering the guided conversation from an emergency session was verified to restore the treating-clinician role.
- Production release-gate copy was verified on the public deployment, with publishing disabled before review checks.
- Production verified-record archive now keeps the synthetic Leela entry on Version 1, matching the underlying source fixture.
- Production Leela retrieval remains metadata-only and does not display Meera's source-linked text.
- Production Leela retrieval gate now binds initials to the selected patient (`LT`), rather than reusing Meera's `MR` initials.
- Production non-primary patient summary actions remain disabled and labeled unavailable; they cannot switch into Meera's source-linked workflow.
- Production Leela record is overflow-free at desktop and 390 x 844 mobile widths.
- Production enrolment preserves the selected contact channel and coordination note into the coordinator outreach context.
- Production runtime query for the final deployment returned no error or fatal entries.

## Pitch readiness

- `PITCH_READINESS.md` records the audience, narrative, demo order, clinical blockers and QA state.
- `PITCH_SCRIPT_90S.md` is the aligned spoken script.
- `PITCH_EVIDENCE_MAP.md` maps every deck claim to the local source pack and labels demonstrated, planned and pending boundaries.
- `pitch/Continuity_Loop_Pitch_Deck.pptx` is the eight-slide editable deck; it includes one authentic production UI capture with synthetic data and `[Sources]` blocks in all eight speaker-note sections.

## Local run

```powershell
cd C:\Users\basit\Downloads\CODE\healthathon\prototype
npm.cmd install
npm.cmd run dev
```

For the Vercel-compatible runtime:

```powershell
npm.cmd run dev:next
```
