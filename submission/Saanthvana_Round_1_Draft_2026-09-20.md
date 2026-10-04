# Saanthvana: merged Round 1 slide draft

Eight slides, including cover. Updated 20 September 2026 from the team discussion, two supplied background decks, reviewed clinical forms and local prototype. The PowerPoint was exported, but this draft predates the latest simplification and is not ready for submission. The current proposal is in `FAMILY_CAREGIVER_DIRECTION.md`. Final deck changes are deferred while the team settles that direction; disk space is no longer the blocker.

## 1. Saanthvana

**Goals-of-care conversations**

For people with cancer and their families.

Zeros and Ones, Health-a-thon 2026.

Visual: existing `prototype/public/care-conversation.png` editorial illustration. Do not present it as a pilot photograph.

## 2. The conversation can be missing when care changes

A person with advanced cancer reaches emergency care. The family remembers an earlier discussion, but the receiving team cannot find a written account of the patient's wishes.

A documented conversation gives the next discussion a starting point.

- **1.56 million** estimated new cancer cases in India in 2024.
- **0% to 92%** goals-of-care documentation in one improvement project at Cancer Institute (WIA), Chennai, in advanced pancreatic/colorectal cancer.

The scenario is illustrative. The Chennai finding is not a national documentation rate or a Saanthvana result.

Sources: [IARC India fact sheet](https://gco.iarc.who.int/media/globocan/factsheets/populations/356-india-fact-sheet.pdf), [Thangasamy et al.](https://pubmed.ncbi.nlm.nih.gov/41340644/), `Presentation draft.pptx`.

## 3. A conversation led by the treating doctor

**Understanding and priorities:** what the patient knows, values and wants to discuss.

**People and care preferences:** who should join, which choices were discussed and what remains unclear.

**Review and revisit:** the doctor reviews the draft. Changed wishes create a new version.

Sources: `Healthathon form.docx`, `Saanthvana v 1.0.docx`, current prototype. A conversation summary does not create an advance medical directive or an emergency treatment order.

Visual: existing `prototype/public/clinician-handoff.png` editorial illustration.

## 4. Families prepare before the next conversation

Six prompts cover understanding, priorities, worries, questions, people and language.

Download personal notes to bring to the doctor. The reviewed summary stays unchanged.

Visual: `submission/screenshots/2026-09-20/preparation-desktop.png`, captured in Chrome on 20 September. Sample patient and family details only. Notes are session-only, separate by patient/author/role and are not sent to a care team.

## 5. Palliative care contacts are easier to find

**12 selected centres** across Karnataka, Telangana and Andhra Pradesh.

Search by name or city, filter by service, call the centre or open directions. The public `/care` page needs no sign-in.

Visual: `submission/screenshots/2026-09-20/care-desktop.png` with four Karnataka listings.

Sources: Pallium India's [Karnataka](https://palliumindia.org/clinics/karnataka), [Telangana](https://palliumindia.org/clinics/telangana) and [Andhra Pradesh](https://palliumindia.org/clinics/andhrapradesh) directories. Selected entries, not a comprehensive national directory. Availability is not confirmed. Medicine stock and RMI authorisation remain unverified. The existing OSM/Photon map is a separate nearby-place search.

## 6. AI assistance with a required human review

1. **Capture with consent.** Record or type the clinician-led discussion in the preferred language.
2. **Draft from the conversation.** Transcribe and organise stated information, keeping source passages visible.
3. **Resolve before release.** The doctor checks missing or unclear details and approves the written summary.
4. **Keep a reviewed copy.** The patient and authorised family retrieve the latest released version.

Planned integration. The current prototype uses sample conversation text and no live AI model.

Speaker notes: Whisper/Bhashini, native apps, India-hosted storage, messaging and ABDM/FHIR remain team proposals. They are not implemented integrations. The app demonstrates the documentation workflow, not automated treatment decisions.

## 7. A focused build and clinician review

**Working locally:** guided conversation, physician review and versions, family preparation, care directory and nearby map.

**Next build:** consented transcription, durable access/storage and language evaluation. Restrict the competition demo to the agreed assistive scope.

**Proposed evaluation:** compare drafts with source text; measure clinician corrections and retrieval time; ask families whether the summary is clear.

No clinical outcome improvement or AI accuracy has been established. The repository's simulated conversations are not patient outcomes or model validation. Pre-existing clinical scoring/prescribing experiments require a scope decision before release.

## 8. Zeros and Ones

Clinical collaborators: Sharada Vinod Kutty and Sujay Halkur Shankar.

Product and technology: Shaik Abdul Basith and Shaik Muhammad Awaiz.

Shirin Ayub plans to join as the fifth member. Her role and contributions remain to be confirmed; she is not yet listed among the four joined members in the supplied portal capture.

Existing online prototype: [continuity-loop-healthathon.vercel.app](https://continuity-loop-healthathon.vercel.app/).

This address opens the earlier build. The Saanthvana updates shown in these screenshots are local and await deployment.

Speaker notes: four members were displayed in the signed-in team portal. The portal's lead-assignment warning remains inconsistent with its assigned-role labels. No role change, upload or submission was performed.

## Export plan

Use the website's deep green and warm cream colours, editable Arial text and a 16:9 canvas. Keep the cover minimal, use large product captures and place citations in the corresponding slide notes. Render and inspect all eight slides before delivering a final PPTX. Preserve the original background decks unchanged.
