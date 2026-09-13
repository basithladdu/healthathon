# Health-a-thon research source index

## Local primary sources

| Source | Role | Integrity |
| --- | --- | --- |
| `GOCD (1).pdf` | Dr. Sujay's one-page concept proposal | SHA-256 `52BDC39EC91317F7E2444D99404C1E02BFAD0C21165994C5D7D7334E69E9C848` |
| `healthathon-2026-raw.txt` | Saved public-site capture containing programme rules, FAQ and all 13 official use cases | SHA-256 `1C6F031512306DA78048BAB526EED9D966F18AED5B3535A33A14E27B81023077` |
| `Healthathon form.docx` | Dr. Sujay's four-section outline of the record the product should capture: general information, medical information and values, decisive actions, signatures | SHA-256 `453257CC911E10433352910810DFE69E40A7A33F90F6B0C9A7C3505464C28D95` |
| `Saanthvana v 1.0.docx` | Full Emergency Care & Treatment Preference Record (ECTPR): ED quick view, 19 sections, conversation guide, Indian legal position, beta plan, wallet card and sources. Contains a filled-in personal name, so it is listed in `.gitignore` and kept local | SHA-256 `C2BE86ECBD8FFF63ABDF6BD3EA763699CABFC23764E9F271075385EADB493F0E` |
| Authenticated dashboard observation | Account-specific selected use case and current schedule, inspected read-only on 24 August 2026 | Recorded in `LIVE_DASHBOARD_AUDIT.md`; no account secret or team code reproduced |
| Raw Codex thread snapshot | Conversation, intermediate reasoning inputs and tool provenance | Copied into the dated local archive at the end of the work session |

## Derived working documents

| File | Purpose |
| --- | --- |
| `TOMORROW_CALL_CARD.md` | Short live call script, questions and four-way decision gate |
| `MEETING_BRIEF.md` | Full product, workflow, safety, pilot, architecture, evidence and meeting pack |
| `PS_DECISION_MATRIX.md` | Weighted comparison of all 13 official use cases and sensitivity analysis |
| `GOCD_PDF_AUDIT.md` | Source-faithful PDF summary, category mapping and scope defects |
| `ECTPR_SOURCE_AUDIT.md` | What the two ECTPR documents are, section-by-section mapping to the prototype, scope boundary, build decisions, privacy note and open questions |
| `RAW_TEXT_AUDIT.md` | Structure, official use cases, quality defects and timeline conflict in the `.txt` capture |
| `LIVE_DASHBOARD_AUDIT.md` | Exact registered choice, editability boundary and account-visible schedule |
| `MEETING_TRANSCRIPT_2026-08-24.txt` | Verbatim saved clinician-team meeting transcript |
| `MEETING_TRANSCRIPT_ANALYSIS.md` | Workflow, actors, decisions, uncertainties and product implications extracted from the meeting |
| `PRODUCT_REQUIREMENTS.md` | P0-P2 requirements, state model and acceptance gates |
| `MEETING_SUMMARY_FOR_TEAM.md` | Compact team-facing meeting summary |
| `HUDAA_ONBOARDING_BRIEF.md` | Plain-language concept, fifth-member role and invitation message |
| `VENTURE_SOURCE_LOG.md` | Current programme, market, clinical, competitive, infrastructure and regulatory evidence |
| `VENTURE_DECISION_MATRIX.md` | Combined 13-statement venture/hackathon ranking, crowding, prize arithmetic and final decision |
| `PITCH_READINESS.md` | Pitch narrative, demo order, release state, clinical blockers and artifact QA |
| `PITCH_SCRIPT_90S.md` | Ready-to-speak 90-second pitch script aligned to the deck |
| `PITCH_EVIDENCE_MAP.md` | Slide-level claim, source, status and boundary ledger |
| `pitch/Continuity_Loop_Pitch_Deck.pptx` | Eight-slide editable pitch deck with source blocks in speaker notes; SHA-256 `D9E5A4CF3D98F726D6A75FDA4B2173DF6023612E50A28E061357A3A11F88AA90` |
| `PROTOTYPE_HANDOFF.md` | Current Vercel URL, workflow scope, implementation boundary and verification record |
| `VERCEL_DEPLOYMENT_HANDOFF.md` | Vercel project, deployment ID, cloud build and production checks |
| `ARCHIVE_VERIFICATION.md` | Hashes and final source/thread/deployment archive checks |
| `prototype-source/` | Rebuildable source snapshot for the clinician/coordinator continuity-loop prototype |
| `deployment-build/goc-continuity-loop.tar.gz` | Packaged Sites build output; SHA-256 `9C4E0EF8AC22C957AB0F9EDFBF512E089712FBF63410341E0DD48858BD30EAAD` |

## External evidence trail

Accessed 24-25 August 2026 unless the source itself is part of the saved raw capture. The expanded claim-by-claim trail is in `VENTURE_SOURCE_LOG.md`.

| Source | Claim used | Confidence |
| --- | --- | --- |
| https://healthathon.reskilll.com/ | Official programme, tracks and use cases | High |
| https://healthathon.reskilll.com/guide | Assistive-only scope, team rules, low-integration design bar, KPI and pilot expectations | High |
| https://journals.sagepub.com/doi/pdf/10.1177/26892820251392545 | Indian cancer-centre QI project moved structured GOC documentation from 0% to 92%; digital implementation and cross-hospital portability remained gaps | High |
| https://pmc.ncbi.nlm.nih.gov/articles/PMC11998719/ | Serious-illness and goals-of-care conversations may be iterative and nonlinear as the clinical picture evolves | High |
| https://www.icmr.gov.in/icmrobject/uploads/Guidelines/1724842969_icmr_consensus_guidelines_on__do_not_attempt_ijmr.pdf | DNAR is distinct from advance directives and withdrawal/withholding; discussions and forms require governed physician-led documentation and accessibility | High |
| https://www.mohfw.gov.in/sites/default/files/EMR-EHR_Standards_for_India_as_notified_by_MOHFW_2016.pdf | Indian EHR expectations for authentication, consent, immutable records, revisions and audit trails | High |
| https://www.mohfw.gov.in/sites/default/files/Guidelines%20for%20withdrawal%20of%20Life%20Support.pdf | Withholding/withdrawal of life support follows formal medical and oversight processes that an app cannot replace | High |
| https://www.sci.gov.in/landmark-judgment-summaries/ | Current Indian end-of-life legal context remains complex; no nationwide legal-enforceability claim is defensible | High for the judgment summary; legal application requires expert review |
| https://pmc.ncbi.nlm.nih.gov/articles/PMC12057217/ | National Cancer Grid EMR work includes palliative workflows and a FHIR-aligned interoperability direction | High |
| https://abdm.gov.in/faqs | ABDM supports consent-based health-record exchange; full integration is a roadmap item, not a prototype dependency | High |
| https://pmc.ncbi.nlm.nih.gov/articles/PMC11323159/ | GOC documentation can differ from the source conversation, supporting source-linked AI output and mandatory clinician verification | High |

## Evidence adjudication

- Account-specific dashboard state overrides guesses from the public `.txt` about the selected use case.
- The dashboard and saved public capture conflict on registration and matchmaking deadlines. Both are retained; the conflict is not silently resolved.
- Dr. Sujay's PDF is authoritative for his proposed problem, but not for legal interpretation, clinical authority or the official category.
- The ECTPR documents are authoritative for the clinical record structure the product should capture. They are not legal advice (the document says so itself) and do not widen the hackathon's non-clinical scope.
- Published evidence supports the need for structured, revisitable documentation. It does not prove that this particular team has sufficient case volume, workflow ownership or pilot access; those remain meeting hypotheses.
- Competitor observations are used only to reject generic reminder/scribe positioning. No claim of market uniqueness or being India's first is made.
- No official entrant allocation by problem statement or cash-versus-credit payout table was found. Crowding and equal-payout arithmetic remain explicitly labelled estimates.
