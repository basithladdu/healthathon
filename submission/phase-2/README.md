# Phase 2 submission draft

The first filled draft uses the organiser's 13-slide Phase 2 template, with its section order, Health-a-thon artwork, theme, slide dimensions and editable text retained. The original emailed template is unchanged at `source-materials/healthathon-phase-2/Health-a-thon-2026-Phase-2-Submission-Template-original.pptx`.

Current review copies:

- `final/Sahara-Healthathon-Phase-2-first-filled-draft-v2.pptx`
- `final/Sahara-Healthathon-Phase-2-first-filled-draft-v2.pdf`

This is a working draft, not a submitted entry. The user explicitly answered **Yes** to the template's question about publicly sharing solution information. That answer does not authorize publishing private patient material, credentials, raw private conversations or unrestricted access to the private repository.

## Visual comparison and next revision

Compared actual renders of the submitted 3 October deck with this draft on 23 October. The earlier eight-slide deck tells the story through consultation photos, a family decision scenario, large product screens, a care map, icons/process steps, team portraits and a research chart. Nine of this draft's13 slides are mainly bullets; slide6 is its only large non-template image composition, with three dated Android screens. The editable team table is useful, and photos/write-ups remain deferred as requested, but the rest of the deck has lost much of the earlier visual storytelling.

The organiser requires the sections and template; its white/blue frame does not require text-only slide bodies. Next visual work should retain all13 sections while carrying forward the earlier approach:

- Problem/users: show the patient-family-clinician situation and the moment a care preference needs to be retrieved.
- Solution/AI: show the consented conversation, attributable source, clinician review, patient confirmation and later retrieval as one readable journey.
- MVP: use large, clearly dated product evidence at the relevant point in the story, keeping old0.5 screens distinct from untested0.6 changes.
- Progress/evaluation: use a real milestone timeline and a compact table/chart from the existing test evidence, with limitations visible.
- Safety/deployment: show who reviews, who can access the record and where the data goes; use sourced cost assumptions when the team supplies them.

This comparison did not change either presentation. The current files remain the first content draft; the visual revision is the next priority, not a completed redesign.

## Filled content and sources

The team and prior roles come from the submitted `submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx`. Current implementation and dated verification come from `TEAM_HANDOFF.md`, `PROJECT_CONTEXT_MAP.md`, `android/release/store-draft.json` and `output/android/verification/features/mentor-delivery.json`. Notes on every slide retain the claim-level source references.

Slide 6 contains three actual 0.5 emulator screenshots from `output/android/verification/0.5.0/ci-runtime-38882501784/`, captured during 19 October verification with fictional records. They illustrate working earlier Android screens. They are not screenshots or physical-device validation of the latest 0.6 APK.

The slide 9 speech timings are narrow **desktop CPU** tests, not clinical accuracy results or handset benchmarks:

| Fixture | Audio | Inference | Exact local evidence |
| --- | --- | --- | --- |
| Synthetic English | 7.15 s | 5.953 s, shown as 5.95 s | `output/android/verification/qwen3-asr-inference.log` |
| Public Hindi | 9.12 s | 22.047 s, shown as 22.05 s | `output/android/verification/qwen3-asr-inference.log` |
| Public Hinglish | 8.275 s, shown as 8.28 s | 22.531 s, shown as 22.53 s | `output/android/verification/qwen3-asr-low-memory-inference.json` |

The English output exactly matches that single fixture. Hindi and Hinglish have errors. No clinical WER/CER benchmark, patient benefit or latest-device result is claimed. Historical allocation failures remain in the source logs. CARE integration and generative clinical drafting remain incomplete.

## Team discussion still needed

- Team code, doctor specialities, experience and institutional affiliations.
- Team photos and individual write-ups, explicitly deferred by the user.
- One-time deployment budget, monthly doctor/facility price and cost assumptions. No amount or payer commitment was invented.
- Current narrated demonstration link and organiser/jury access to the private repository.
- Clinical pilot setting, measured baseline, language reference set and current Android phone validation.

The editable team table contains the five existing members and sourced roles. Missing details remain visible instead of invented. `build-first-draft.mjs` preserves the authoring source; it references the existing Windows workspace and bundled Artifact Tool runtime. Final structural validation is saved in `verification.json`; local renders and intermediate inspection files remain under `build/` and are not part of the publication whitelist.
