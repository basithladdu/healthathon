# Continuity Loop pitch readiness

Prepared: 27 August 2026

## Communication job

By the end of the pitch, Health-a-thon judges and clinical collaborators should understand why Continuity Loop is a focused continuity-of-care workflow, see the verified retrieval moment, and choose the next clinician-owned validation step because the product closes a documented handoff loop without making autonomous clinical or legal claims.

## Deliverables

- `pitch/Continuity_Loop_Pitch_Deck.pptx` — eight-slide editable deck with one authentic production UI capture.
- `PITCH_SCRIPT_90S.md` — short spoken narrative aligned to the slide sequence.
- `PITCH_EVIDENCE_MAP.md` — claim-by-claim source and status ledger.

## Pitch position

**Continuity Loop is a clinician-controlled goals-of-care follow-up and verified clinical-handoff workflow for cancer care.**

The memorable wedge is the receiving physician's moment: find the latest physician-verified summary after recording the access purpose and care relationship. The operational product behind it enrols a clinician-selected patient, assigns an owner and review date, guides the conversation, keeps each field linked to source material, preserves versions and closes unresolved follow-up.

## What the current deck proves

- The registered use case remains Cancer Care → Clinician focused → Patient Follow-up & Continuity of Care.
- The product workflow is implemented with synthetic patients and browser-local state.
- The prototype demonstrates guided documentation, source-linked deterministic extraction, physician review, acknowledgement, append-only versioning, purpose-bound retrieval, audit history and coordinator outreach.
- The retrieval slide uses a production UI capture from `https://continuity-loop-healthathon.vercel.app`; it contains synthetic data only.
- Every slide has speaker notes with a `[Sources]` block. A post-export import check found 8 slide records, 8 notes records and 8 source blocks.

## Claims deliberately excluded

The deck does not claim live AI, transcription quality, patient outcomes, treatment effects, legal validity, a living will or treatment order, real authentication/encryption, cryptographic audit, EHR/ABDM/messaging integration, autonomous triage/prognosis/recommendation or market uniqueness.

The four large figures on the validation slide are targets, design scope or integrity invariants—not observed outcomes:

- 20 scenarios to run;
- 8 current conversation domains;
- a <30-second retrieval target;
- zero unstated preferences filled in.

## Demo order

1. Open the deployed handoff landing page.
2. Start retrieval as the simulated emergency physician; point out patient binding, reason/relationship and the audit-purpose gate.
3. Return to the treating clinician role; open the guided conversation and show discussed/not discussed/defer states.
4. Open the source-linked draft; show exact excerpt mapping and the unresolved-field publication blocker.
5. Resolve, acknowledge and release Version 2; show that Version 1 remains preserved.
6. Return to retrieval and show the latest verified version plus the reconfirm-current-status instruction.
7. Close with the four clinician inputs required for a real simulation/pilot.

## Clinical decisions still required

The prototype is not clinically deployable until the clinical leads define and review:

- the exact conversation domains and wording;
- 20 synthetic or fully anonymised scenarios with clinician-authored reference records;
- acknowledgement meaning, refusal/disagreement/uncertainty states and surrogate authority;
- retrieval roles and permitted access purposes;
- the workflow owner, pilot setting, volume and due-date rule;
- the primary KPI and success threshold;
- audio/transcript capture and retention policy.

## Release state

- Product source: `C:\Users\basit\Downloads\CODE\healthathon\prototype`
- Product HEAD: `54855185e429cd9eebe183915127771825126fe2`
- Vercel production: `https://continuity-loop-healthathon.vercel.app`
- Vercel deployment: `dpl_DnH4qgbkT2brBeVShJA5U1gNj8Ay` (`READY`)
- Owner-only Sites publication: `https://goc-continuity-loop.basithmuqeeth.chatgpt.site` (version 10; sign-in required)
- Sites deployment: `appgdep_6a8ee03ecba4819198c0eccfc34e188e` (`succeeded`)

The parent `healthathon` archive has no configured remote, so these pitch artifacts are local handoff files. The product repository remained clean and was not republished because the product source did not change in this pitch-readiness pass.

## Artifact QA

- PPTX exported with the bundled `@oai/artifact-tool` workflow.
- All 8 slides rendered to PNG and inspected individually at full size.
- Official `slides_test.py` overflow check passed.
- Rendered montage inspected for flow, hierarchy, consistency and accidental wrapping.
- Cover label wrapping and provenance-slide placement defects were corrected before the final export.
- Final deck SHA-256: `D9E5A4CF3D98F726D6A75FDA4B2173DF6023612E50A28E061357A3A11F88AA90`.
