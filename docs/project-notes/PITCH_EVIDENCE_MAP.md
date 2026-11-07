# Continuity Loop pitch evidence map

Prepared: 27 August 2026

Status vocabulary: **demonstrated** means verified in the current prototype; **source-backed** means supported by the local evidence pack; **planned** means a proposed validation or pilot input; **pending** means it requires clinician or external confirmation.

| Slide | Visible claim | Evidence / provenance | Status | Boundary |
| --- | --- | --- | --- | --- |
| 1 | Continuity Loop is a clinician-owned goals-of-care follow-up and verified handoff workflow. | `MEETING_SUMMARY_FOR_TEAM.md`; `VENTURE_DECISION_MATRIX.md`; `PRODUCT_REQUIREMENTS.md` | Source-backed / demonstrated | Registered use case is retained; no category switch is implied. |
| 2 | The failure is continuity across changing care context. | `MEETING_SUMMARY_FOR_TEAM.md` problem description; `VENTURE_DECISION_MATRIX.md` wedge analysis | Source-backed | “At night” is the clinicians' described scenario, not a measured frequency claim. |
| 3 | Receiving physicians use a controlled retrieval gate. | `PRODUCT_REQUIREMENTS.md` emergency retrieval requirements; `PROTOTYPE_HANDOFF.md`; screenshot from `https://continuity-loop-healthathon.vercel.app` | Demonstrated | Production UI is synthetic; access control is simulated in browser-local state. |
| 4 | The workflow links enrolment, conversation, provenance, physician release and retrieval. | `PRODUCT_REQUIREMENTS.md` state model; `PROTOTYPE_HANDOFF.md` demonstrated workflow | Demonstrated | The clinical checklist remains a product hypothesis pending approval. |
| 5 | Each field stays linked to source material and unresolved content blocks release. | `PRODUCT_REQUIREMENTS.md` source capture and verification requirements; `PROTOTYPE_HANDOFF.md` deterministic extraction | Demonstrated | No external AI model is active; the excerpt is synthetic. |
| 6 | The MVP's safe boundary is explicit. | `PRODUCT_REQUIREMENTS.md` demonstration integrity; `MEETING_SUMMARY_FOR_TEAM.md`; `PROTOTYPE_HANDOFF.md` | Demonstrated | No clinical deployment, legal authority, real security or interoperability claim. |
| 7 | A 20-case clinician-reviewed simulation is the next credible proof. | `VENTURE_DECISION_MATRIX.md` 60–90 day pilot hypothesis; `PRODUCT_REQUIREMENTS.md` P1 scenario requirement | Planned | Numbers are targets/scope/invariants, not results. |
| 8 | Clinical ownership is the immediate decision. | `TOMORROW_CALL_CARD.md` decision gate and required inputs; `VENTURE_DECISION_MATRIX.md` remaining decisions | Pending | Checklist, scenarios, pilot owner, authority and retrieval semantics need external clinical agreement. |

## Primary source integrity

- `GOCD (1).pdf` SHA-256: `52BDC39EC91317F7E2444D99404C1E02BFAD0C21165994C5D7D7334E69E9C848`.
- `healthathon-2026-raw.txt` SHA-256: `1C6F031512306DA78048BAB526EED9D966F18AED5B3535A33A14E27B81023077`.
- The account-specific dashboard observation remains recorded in `LIVE_DASHBOARD_AUDIT.md`; it overrides guesses from the public capture when the two conflict.
- The local evidence pack distinguishes source, interpretation, generated copy and unresolved decisions. The deck follows the same distinction.
