# Handoff pack: CARE, OHC and the palliative landscape (27 Oct 2026)

One place for the team to understand what we researched, how we reasoned, what we decided and what is still open. Written in plain words. Nothing here was run against a live CARE server; anything about how CARE behaves when running is marked [unverified] in the sources.

## Start here (5-minute read)
1. **Team deck (9 slides):** [`../../presentations/Sahara_CARE_Team_Briefing_2026-10-27.pdf`](../../presentations/Sahara_CARE_Team_Briefing_2026-10-27.pdf)
2. **CARE vs Sahara in plain words (two big tables):** [`../CARE_VS_SAHARA_FOR_TEAM.md`](../CARE_VS_SAHARA_FOR_TEAM.md)
3. **What is left to do:** `everyday.md` → "What is genuinely left" and `HANDOFF.md` → "27 Oct 2026 — open items".
4. **The full conversation with Claude:** [`../../../CLAUDE_TRANSCRIPT.md`](../../../CLAUDE_TRANSCRIPT.md)

## What we researched
| What | How much | Where |
|---|---|---|
| CARE website + blog + organiser email links | 75 pages/files | [`care-docs-crawl/findings-site.md`](care-docs-crawl/findings-site.md) |
| CARE documentation (docs.ohc.network) | all 473 pages, none failed | [`care-docs-crawl/findings-docs0..7.md`](care-docs-crawl/) |
| CARE source code (14 repos read for the deep dive) | code read, nothing run | [`../CARE_DEEP_DIVE_2026-10-27.md`](../CARE_DEEP_DIVE_2026-10-27.md) |
| All 84 public OHC GitHub repos | licence, purpose, reuse, API | [`ohc-repos/`](ohc-repos/) and [`ohc-repos/ALL_84_REPOS.tsv`](ohc-repos/ALL_84_REPOS.tsv) |
| Field-by-field data mapping | CARE fields vs Sahara fields | [`../CARE_FAMILY_SIDE_FIT.md`](../CARE_FAMILY_SIDE_FIT.md) |
| Short brief + options A–D | — | [`../CARE_BRIEF_2026-10-27.md`](../CARE_BRIEF_2026-10-27.md) |
| Palliative organisations (Pallium, Niram, Arike, IAPC, Kerala grid) | Niram done; others in progress | [`partners/`](partners/) |
| India palliative landscape (apps, policy, providers, evidence, business models) | 3 of 5 done (global comparators + funding; Indian providers + top 5 pilot partners; research evidence + design requirements) | [`landscape/`](landscape/) |
| Sahara's own data flow (what goes to the server) | code audit | [`sahara-data-flow-audit.md`](sahara-data-flow-audit.md) |

## Raw material (everything is in this repo)
- `care-docs-crawl/raw-page-text.tar.gz`: plain text of every CARE page and file read (docs 473 pages, site 75 pages), plus fetch logs and sitemaps (649 files). Unpack with `tar -xzf`.
- `method/`: the exact instructions the research agents followed (CARE crawl, OHC repo audit, partner research, India landscape).
- Repo source code was read from shallow clones and then deleted; the per-repo notes in `ohc-repos/` cite file paths so anyone can re-check on GitHub.

## How we reasoned (thought process)
1. **Question asked:** CARE is the staff side of palliative care. Can Sahara be the family side and "fit the puzzle"?
2. **First pass (website + docs):** CARE is a free, open-source hospital system for staff (MIT licence, by OHC in Kochi). It covers registries, home visits, care teams, consents. Its own docs list a gap: no screen for family/patient verification of a consent. We first over-stated that gap; the correct version is "staff would record it; the family never confirms anything themselves in CARE".
3. **Code check:** CARE's patient login is a 1-hour phone-code (OTP) token that can only register a patient and book or cancel appointments. Family can't add symptoms, medicines or notes; family can't be on the care team. No FHIR server endpoint. No care-plan or caregiver model in code.
4. **Full read of everything (473 docs pages, 75 site pages, 84 repos):** confirmed the above and added:
   - Real palliative organisations already run CARE: **Pallium India** (live 5 Dec 2025, 16 home-care teams), **Niram** (Tumkur), **Arike**, **IAPC Kerala**, plus Kerala's government grid → realistic pilot partners.
   - A hospital can model a relative with **one-patient access** (custom role + PatientUser), and CARE can mark a reading as reported by a **related person** — but only a CARE superuser can load forms, finished visits lock, and files can never be deleted.
   - Reusable OHC assets: `cdci` (Indian drug + lab codes, incl. morphine/fentanyl), `care_pressure_sore_fe` and `care_dental_fe` (body-map / chart question types), `care_zoom` (family video visits), `care_im_wrapper` (WhatsApp document links).
   - Security issues to report privately to OHC: `ccm_uhi` (no-login patient lookup by booking ID) and committed demo passwords/old tokens in `care_api_backup`.
5. **Can we pull data?** No. Patient data needs a staff login or the patient's own phone code on that hospital's own CARE server. Only static data (code lists, plugin registry, SBOMs) is freely usable.
6. **Build on CARE or not?** **No** (high confidence). CARE has no family side, would limit Sahara to hospitals that run CARE, and doesn't fit our core features. **Connect next to it** instead: our forms in CARE's format now; a small family-inbox add-on (nurse accepts each family entry) with a partner like Pallium after the hackathon.
7. **Correction from the owner:** Sahara is **not** offline-only. Signed-in data goes to Sahara's Supabase server first; there is no offline send-later queue yet; anonymising before the doctor sees data is **planned, not built**.
8. **Owner decision:** everything goes online with a server link (e.g. the emergency QR opens a read-only page). Being built.

## Decisions taken
- Don't rebuild Sahara on CARE; connect next to it.
- Mail OHC (draft in `everyday.md`); name pilot candidates; report the two security issues privately.
- Everything online with server links (emergency QR first).
- CARE option A / A+B / A+C: **to decide in the team meeting** (deck slide 8).

## What not to say to judges
"Integrated with CARE", "FHIR-compliant", "fills CARE's gap", "CARE has a family app", "certified", "AI drafts the note" (it's word matching today), "works fully offline".
