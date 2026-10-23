# Phase 2 submission draft

## Current visual draft

- `final/Sahara-Healthathon-Phase-2-visual-draft-v6-ready.pptx`
- `final/Sahara-Healthathon-Phase-2-visual-draft-v6-ready.pdf`

The current **v6** puts the patient story and real product captures ahead of prose while retaining all **13 organiser sections**, the official artwork, theme and order. The cover logo is smaller, headings are shorter and source captions sit clear of the footer. Three unchanged Round 1 concept scenes, a large source-review screen, four actual Android screens, note history, dated product evolution and the current public survey replace most of v5's outlined text diagrams. The five care stages remain editable. The required roster, comparative evaluation and cost structure remain native tables, and the external research chart stays editable. Team photographs and biographies remain deferred.

All 13 slides were rendered and visually inspected in PowerPoint. Package, chart/workbook, slide-layout and native table checks passed. Native PowerPoint measurements found no text-frame or table-cell overflow. The PDF retains the MVP and email links; invisible-border annotations restore links omitted by PowerPoint's export without changing page content. The compact verification/hashes are in `visual-draft-v6-checks.json`. `build-visual-draft.mjs` is the retained authoring source; use a new `SAHARA_DECK_VERSION` for another export because the finalizer preserves existing outputs.

The slide 3 chart comes from an external single-centre quality-improvement project, not Sahara. The study reports baseline documentation **0%** and a QI result **92%**, with **24/26** eligible patients documented. It supplies no separate numeric baseline denominator, so the deck does not claim `0/26`. Primary sources: [publisher full text](https://journals.sagepub.com/doi/10.1177/26892820251392545) and [PubMed](https://pubmed.ncbi.nlm.nih.gov/41340644/). The intervention included a standard procedure, referral changes and a paper record; it does not establish clinical benefits from this app.

Asset hashes and original paths are retained in `assets/source-provenance.json`. The three patient/family scenes are generated concepts from the earlier submission, not real patient photographs. The web review, family home and note history are actual **24 September** captures with fictional records and earlier names. Android images are actual **0.5 emulator captures from 19 October** with fictional fixtures. These do not show the latest 0.6 APK or establish clinical use. The **23 October** survey intake is the actual empty public web page. The map is actual synthetic survey QA with visible OpenStreetMap attribution, not evidence of hospital adoption. Original audio/report files remain phone-private and CARE integration remains proposed. Shirin's symptom-tracker clinical input remains pending.

The first filled draft below remains preserved as a historical review copy.

## Previous first filled draft

The first filled draft uses the organiser's 13-slide Phase 2 template, with its section order, Health-a-thon artwork, theme, slide dimensions and editable text retained. The original emailed template is unchanged at `source-materials/healthathon-phase-2/Health-a-thon-2026-Phase-2-Submission-Template-original.pptx`.

Previous review copies:

- `final/Sahara-Healthathon-Phase-2-first-filled-draft-v2.pptx`
- `final/Sahara-Healthathon-Phase-2-first-filled-draft-v2.pdf`

This is a working draft, not a submitted entry. The user explicitly answered **Yes** to the template's question about publicly sharing solution information. That answer does not authorize publishing private patient material, credentials, raw private conversations or unrestricted access to the private repository.

## Historical visual comparison of v2 - 23 October 2026

The read-only comparison on 23 October examined actual renders of the submitted 3 October deck and the **previous v2 first filled draft**. The earlier eight-slide deck used consultation illustrations, a family decision scenario, large product screens, a care map, icons/process steps, team portraits and a research chart. Nine of v2's 13 slides were mainly bullets; its slide 6 was the only large non-template image composition, with three dated Android screens. The editable team table was useful, and photos/write-ups were deferred as requested, but v2 had lost much of the earlier visual storytelling.

The comparison identified these changes for the subsequent visual revision while retaining all 13 organiser sections:

- Problem/users: show the patient-family-clinician situation and the moment a care preference needs to be retrieved.
- Solution/AI: show the consented conversation, attributable source, clinician review, patient confirmation and later retrieval as one readable journey.
- MVP: use large, clearly dated product evidence at the relevant point in the story, keeping older 0.5 screens distinct from untested 0.6 changes.
- Progress/evaluation: use a real milestone timeline and a compact table/chart from the existing test evidence, with limitations visible.
- Safety/deployment: show who reviews, who can access the record and where the data goes; use sourced cost assumptions when the team supplies them.

That comparison itself changed neither presentation. The user then authorised the visual redesign. V5 added imagery and diagrams, but further feedback found it too close to a theoretical paper. **The current v6 files above replace that treatment with large product scenes and substantially less prose**. The submitted 3 October deck, v2 and v5 remain preserved. Remaining team, costing, video and validation gaps are content/evidence gaps.

## Filled content and sources

The team and prior roles come from the submitted `submission/final/Saanthvana_Healthathon_2026-10-03-final.pptx`. Current implementation and dated verification come from `TEAM_HANDOFF.md`, `PROJECT_CONTEXT_MAP.md`, `android/release/store-draft.json` and `output/android/verification/features/mentor-delivery.json`. Notes on every slide retain the claim-level source references.

In the preserved **v2**, slide 6 contains three actual 0.5 emulator screenshots from `output/android/verification/0.5.0/ci-runtime-38882501784/`, captured during 19 October verification with fictional records. V5 used one queue capture beside the historical web review. **Current v6** gives the large source-review UI its own main-story slide, then shows four native screens across slide 6. None is a screenshot or physical-device validation of the latest 0.6 APK.

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

The editable team table contains the five existing members and sourced roles. Missing details remain visible instead of invented. `build-first-draft.mjs` and `verification.json` retain the previous draft's authoring/structural evidence. The current source is `build-visual-draft.mjs` and its compact verification is `visual-draft-v6-checks.json`. All 13 current slide renders are under `build/visual-v6-ready-renders/`; local renders and intermediate inspection files are not part of the publication whitelist.
