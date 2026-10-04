# Saanthvana: context recovery and implementation

Checked 20 September 2026, Asia/Kolkata. This report supersedes older product-name and team-use-case summaries, while retaining the original source files unchanged.

## Where the conversations end

| Material | Coverage and last message | Completeness |
| --- | --- | --- |
| WhatsApp `Zeros and Ones.zip` | 25 August 2026, 5:02:21 pm through **20 September 2026, 1:43:36 pm**. Last message: Sujay, “Should we discuss today?” | Both `chat.txt` and `chat.md` read. ZIP contains only these two text files; omitted attachments are not recoverable from the ZIP itself. |
| Claude `1bd4b819-4dc5-409d-a3b8-1ce8dc668937.jsonl` | 17 September through 20 September. Last assistant response at 08:15:35 UTC / **1:45:35 pm IST**, giving the transcript path. Last substantive work was commit `58c9ff0`, the simulated-conversation exercise, map-tile change and local show-and-tell deck. | Entire JSONL parsed without errors; direct user/assistant text extracted and reviewed. Tool messages were indexed for context; they are historical observations, not new authorisation. |
| Claude `5b425f88-da06-4b37-a2d7-0d2b83cfb2fa.jsonl` | **30 August through 6 September**, ending in a source-note readability and layout update. | Entire JSONL parsed without errors; direct conversation text recovered. Pasted older exchanges containing “Show more” are incomplete within the original, so they cannot establish a complete August 30 meeting transcript. |
| Claude project memory/sidecars | Memory directory is empty; session sidecar contains `custom-title.json`. | No additional meeting transcript or clinician attachment recovered there. |
| Previous repository WhatsApp capture | `USER_SUPPLIED_CONTEXT_2026-09-13.txt` stopped at 25 August, 7:31 pm. | Historical excerpt, now superseded by the newer export. |
| Meeting and webinar | `MEETING_TRANSCRIPT_2026-08-24.txt`; September Connect webinar embedded in `healthathon-2026-raw.txt`. | The August 24 meeting and existing webinar material are present. The August 30 meeting recording/transcript has not been supplied. |

The two Claude JSONL files, original WhatsApp ZIP and extracted chat files are preserved in **`source-materials/private-context/2026-09-20/`**, with original paths, byte counts and SHA-256 hashes in its `manifest.json`. This folder is ignored by Git and is not a web asset. The originals have not been modified.

## Sources recovered and reviewed

| Source | Review and use |
| --- | --- |
| `Healthathon form.docx` | Text extracted, including tables/text boxes. Sujay's distilled form guides the conversation and final record: participants/language, understanding, capacity and representation, values/worries, clinician-discussed preferences, review and declarations. |
| `source-materials/user-provided/Saanthvana v 1.0.docx` | Full structured record and appendices extracted and reviewed. Sharada's broader clinical reference. Blank is not refusal; a changed preference needs review and a new version. Its clinical protocols are not treated as autonomous software requirements. |
| `GOCD (1).pdf` | One-page concept reviewed. Physician-led conversation, updateable documentation and family access are the central workflow. |
| `Presentation draft.pptx` | All 3 slides read; preview rendered. Clinician's case narrative and background references. |
| `Saanthvana_Background_Rationale_1.pptx` | All 8 slides and available notes read; preview rendered. Expanded presentation of the same rationale. See claim corrections below. |
| `Vidhi-EOLC-Toolkit-FAQ.pdf` | 37-page June 2024 toolkit extracted and reviewed, including the patient/caregiver chapter. It distinguishes conversation/documentation from an executed legal directive. It does not establish that a prototype signature creates a living will. |
| `Vidhi_EOLC_Sample-Advance-Medical-Directive.pdf` | All 8 pages read: identity, treatment wishes, optional personal wishes, designated representatives, declarations, witnesses and notarisation. Kept as a reference; no executable legal directive was generated. |
| Existing `submission/` decks | Six distinct decks reviewed through extracted slide text/notes: base, QR, visual, illustrated, product visuals and show-and-tell. Four corresponding `pitch/` decks are exact duplicates. The QR variant adds “SCAN TO TRY”; older decks retain the historical clinician category and Continuity Loop name. |
| `Downloads/Hackathon_Problem_Statement_3.pdf` | Two-page Medlio concept from another team. Background only; not Saanthvana's requirements. |
| `Downloads/Team13ZerosandOnes_Track_2.pptx` and `Slide_deck_content_needed*.pptx` | SANCHAY/Linux material, unrelated to this project. Excluded from Healthathon decisions. |
| Shared public directories | Pallium India, CanSupport, IAPC and Cancer Support India links recovered from chat. Pallium India's Karnataka, Telangana and Andhra Pradesh tables inspected. Cancer Support India's page was not retrievable during research; retained only as a team-supplied external link. |

Extraction files, source hashes and rendered deck previews are available in `tmp/context-recovery/sources/` and `tmp/context-recovery/render/`. Original supplied decks and PDFs remain unmodified.

## Remaining source gaps

1. **`Technical thoughts and questions.docx`**, shared 25 August at 5:06 pm, was not found among the supplied files.
2. **August 30 meeting recording or full transcript**, if one exists.
3. **September 2, 9:15 pm changed-use-case screenshot**. Lower priority because the current team draft was checked directly in Chrome.

The two September 18 presentation attachments and both Vidhi PDFs have now arrived. They are no longer missing. Text-export attachment placeholders alone do not prove that every historical image or attachment has been supplied.

## Confirmed current decisions

- **Name: Saanthvana.** Accepted in the September 17 team discussion; supersedes the unaccepted Sambal suggestion and historical Continuity Loop branding.
- **Primary purpose: goals-of-care discussion.** Sujay explicitly requested this stronger focus on September 18 at 8:18 pm, rather than a general continuity document.
- **Audience: people with cancer and their families/caregivers**, with the treating physician leading discussion and reviewing the summary.
- **Supporting feature: find palliative care nearby.** The September 14 discussion prioritised palliative centres; a current state-level RMI list was not available. Neither medicine stock nor legal dispensing authorisation should be invented from an undated listing.
- Ambient listening, multilingual transcription, native Swift/Kotlin apps, India-hosted API/database, hospital authentication, messaging and ABDM/FHIR integration are proposals. The current application is a browser-local prototype with sample patients.
- TANUH is a separate opportunity and has not been merged into this cancer-care scope.

## Current portal, checked in signed-in Chrome

- Team: **Zeros and Ones**, 4 of 5 members.
- **Team Round 1 selection: Cancer → Patient / Caregiver → Family & Caregiver Support.** The personal registration/dashboard still contains an older clinician selection; these are different surfaces.
- Saved solution already uses **Saanthvana**, with goals-of-care discussions central and location modules supporting it.
- Draft **90% / 9 of 10 required answers**, **not submitted**. Pitch deck remains missing; the portal requests 6–8 slides. Deadline displayed: **25 September 2026**.
- Team page displays **Sharada Vinod Kutty** as doctor partner and **Shaik Abdul Basith** as technical lead, both marked assigned. The role dropdowns nevertheless show “Not assigned yet”, and Round 1 showed a lead-assignment warning. This is an unresolved portal-state inconsistency, not evidence that either role was changed.
- No form edits, uploads, invitations, messages or submission were performed.

## Research corrections before merging the decks

The original decks have been preserved. These corrections belong in the next merged presentation, not as silent changes to source material.

| Slide claim | Finding and treatment |
| --- | --- |
| 1.56M new cancers; 3.3M five-year prevalence | Verified against the current [IARC India GLOBOCAN 2024 fact sheet](https://gco.iarc.who.int/media/globocan/factsheets/populations/356-india-fact-sheet.pdf): 1,562,581 new cases in 2024 and 3,324,140 five-year prevalent cases. State the estimate year, rather than implying a measured 2026 annual total. |
| 98.3% unmet need among everyone with a life-limiting illness | Overbroad denominator. [Patil et al.](https://pubmed.ncbi.nlm.nih.gov/38834235/) is about cancer. The [author-uploaded paper](https://www.researchgate.net/publication/381176125_Unmet_need_for_cancer_palliative_care_in_India) defines unmet need among metastatic cancer cases and reports 98.39% across the assessed cancer types/registry data. Do not generalise this to all life-limiting conditions. |
| ~0% documentation in dedicated Indian cancer centres | [Thangasamy et al.](https://pubmed.ncbi.nlm.nih.gov/41340644/) is a single-centre QI project at Cancer Institute (WIA), Chennai, in advanced pancreatic/colorectal cancer. Documentation increased from 0% to 92% in that project. It is not a national prevalence estimate or a Saanthvana result. |
| Early discussions “work” and prevent the illustrated outcome | [Aslam and Hayat](https://www.jpsmjournal.com/article/S0885-3924%2824%2900113-1/fulltext) is a survey case-report abstract. Use it as rationale, not proof of Saanthvana's clinical effectiveness. The opening case is a composite illustration; its counterfactual outcome cannot be established. |
| 11.5% formal training; 15.6% discussion timing knowledge | The [NMJI paper](https://nmji.in/knowledge-and-attitude-towards-advance-directives-for-patients-with-terminal-illnesses-among-doctors-working-in-a-tertiary-care-hospital/) concerns 391 doctors at one tertiary centre, CMC Vellore. Retain sample and setting; do not portray it as a nationwide survey. |
| 140-physician ACP preprint | Supplied DOI/link did not yield a verifiable paper during this pass. Keep the four percentages unverified; do not promote them to established findings. |
| 0.4% morphine access | The cited [LeBaron paper](https://academic.oup.com/oncolo/article/19/5/515/6399315) is from 2014. The exact percentage was not independently recovered here. Do not describe it as current access or a verified RMI inventory. |
| 55% lack support; 92.9% barrier | [Forsythe et al.](https://pmc.ncbi.nlm.nih.gov/articles/PMC3661934/) uses the US 2010 NHIS, n=1,777, and measures professional counselling **or** support groups. It is not Indian prevalence. The slide's 92.9% denominator also needs to remain explicit; it is not all survivors. |
| Urban living-will survey | The supplied reference is a 2019 media account of a survey. Its figures were not independently verified against a primary survey report here. |

## Latest direction

The latest user instruction removes the landing page and asks for ordinary language, fewer clicks and less doctor work. The main path now opens directly to family care, with inline tasks/questions, the next visit, an approved care note and support search. The shorter doctor screen reuses an existing note, records permission and requires explicit review before approval. The new family screens have English/Hindi labels; localisation is partial. See [the working product direction](FAMILY_CAREGIVER_DIRECTION.md) and [saved portal answers](form.md).

Shirin's later messages are recorded separately in the ignored private-context directory; they do not extend the group export. Referenced voice recordings were not supplied. The chat suggestions and clinical reference documents are source material, not instructions to implement every idea.

## Earlier implementation, superseded where noted

- Saanthvana branding in the landing page, sign-in, app navigation, metadata, icon title and handoff copy.
- The earlier landing page was subsequently removed from the main app at the user's request. Prior specialist work remains accessible through the full workspace, outside the simplified default path.
- **What matters to you?** is reachable from the family home and care note. One open note is visible; optional prompts cover understanding, worries, questions, people and language. Notes are separate by patient, author and role; they cannot alter released summaries. A text download provides an explicitly unreviewed copy to bring to a doctor. Notes are session-only and are not sent automatically.
- **`/care`**, accessible without sign-in, and the same directory inside patient/family care views. Twelve selected public listings across three states include address, contact, listed service types, a source link and address-based directions. Search, state/service filters, clear filters and empty results are implemented.
- Existing OpenStreetMap/Photon nearby-place map remains available. Directory providers are not given fabricated coordinates. Directory directions and OSM search are distinct.
- Links to the shared support directories and Pallium/Vidhi living-will resources. No claim of verified medicine availability or automatic legal validity.
- Corrected demo role transitions that used the previous session's navigation guard. Selecting the emergency-doctor role now opens the acknowledgement gate instead of silently unlocking the summary.

## Task organisation

The existing tasks **Add Devvit deck visuals** and **Set MVP and pitch deadlines**, plus the saved Healthathon project, are grouped in a **Healthathon** sidebar section. The app tools do not expose project reassignment for an existing task, so this is sidebar grouping and context recovery, not a claim that their project IDs or conversation histories were merged.

## Validation and remaining work

Validation details are in [SAANTHVANA_VERIFICATION_2026-09-20.md](SAANTHVANA_VERIFICATION_2026-09-20.md).

The repository also contains pre-existing clinical scoring, medication and emergency-order tools. These conflict with the non-diagnostic, non-prescriptive scope described in the supplied programme material. This turn has not expanded or clinically validated those tools. They need a separate scope decision before a competition release; their existence means the whole app cannot honestly be described as having no clinical decision-support functions.

No commit, push, production deployment or competition submission was performed. The recovered context and the simplified family/doctor workflow are implemented locally. An eight-slide working deck was exported and passed package/layout checks, but it predates the latest simplification. It remains a draft while the team settles the direction; neither supplied background deck is itself a complete product submission.
