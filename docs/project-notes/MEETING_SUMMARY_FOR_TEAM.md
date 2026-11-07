# Clinician meeting summary

Meeting: 24 August 2026  
Purpose: Lock the Health-a-thon team and understand the clinicians' goals-of-care concept.

## One-sentence concept

Build a clinician-controlled tool that helps structure a goals-of-care conversation, creates a source-linked and physician-verified clinical summary, preserves every version, and lets an unfamiliar emergency doctor retrieve the latest record quickly.

## Problem described by the clinicians

For some people with advanced cancer, the treating doctor needs to discuss what the patient and family would want if the condition worsens. Topics may include staying at home versus coming to hospital, ward versus ICU, ventilation, dialysis, time-limited treatment trials, comfort, and family involvement.

In current practice these discussions may not happen, may be informal, or may not be documented in a form another doctor can find. If the patient arrives at an emergency department at night, the receiving doctor and family may have to repeat a difficult conversation under pressure.

## Proposed workflow

1. A clinician selects a patient who should have the discussion.
2. A clinician-owned checklist guides the conversation.
3. The source conversation is captured through a transcript or structured entry; live voice is a later option.
4. The system produces a structured draft and links every important field back to what was actually said.
5. The physician reviews and edits the draft.
6. The patient or appropriate surrogate acknowledges it.
7. The physician verifies and releases a version.
8. Old versions remain preserved when preferences change.
9. An authorised emergency or receiving doctor retrieves the latest verified version and reconfirms the current clinical situation.

## Critical boundary

The output is a **goals-of-care clinical summary**. It is not a living will, advance medical directive, DNAR order, treatment order, or independently binding legal document. The patient/family cannot edit a released record without a clinician-led review.

## What the doctors agreed to provide

- The structure/checklist for the clinical conversation.
- Different simulated clinical scenarios.
- Participation in approximately 20 simulated conversations for evaluation.

## What the technical team agreed to provide

- Product and workflow design.
- A working MVP.
- A KPI/evaluation plan.
- An honest demonstration using synthetic data.

## Decisions still open

- Exact clinical checklist.
- Voice versus transcript versus coordinator entry.
- Meaning of patient/surrogate acknowledgement.
- Audio/transcript retention.
- Patient identity and access model.
- Pilot hospital, owner, volume and buyer.
- Exact KPI and success threshold.

## Current MVP scope

The hackathon version will use synthetic cases and a preloaded conversation. It will demonstrate guided documentation, source traceability, physician review, acknowledgement, append-only versioning, controlled emergency retrieval, audit history, and a supporting follow-up worklist. It will not claim live AI transcription, real hospital integration, real authentication, legal signatures, or ABDM compliance.

