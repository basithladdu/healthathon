# Healthathon and TANUH project context map

Prepared 13 September 2026, Asia/Calcutta

This is the working map for the Health-a-thon project, the newer TANUH - C-CAMP opportunity, the clinician material, the team history, and the current local workspace. It separates direct source material from derived decisions and open questions.

## Current truth

### Two programmes are in scope

#### Health-a-thon 2026

- Programme source: `healthathon-2026-raw.txt`, now extended with the 6 September Connect webinar transcript.
- Organisers named in the saved material: Koita Foundation, Koita Centre for Digital Health at IIT Bombay, Reskilll, FOGSI, National Cancer Grid and RSSDI.
- Current account selection recorded in `LIVE_DASHBOARD_AUDIT.md`: `Cancer Care -> Clinician focused -> Patient Follow-up & Continuity of Care`.
- Working product: `Continuity Loop`, a clinician-controlled goals-of-care follow-up and verified clinical-handoff workflow.
- The 6 September webinar says idea submission is 12-25 September 2026, the Grand Finale is 28 November 2026 at IIT Bombay, and the Top 30 are ten teams per clinical track. This is transcript evidence, not a fresh live-dashboard check.
- The programme permits teams to bring their own practical problem, combine clinician-facing and patient/caregiver-facing use cases, and use supporting PPTs or demo videos. The saved webinar describes evaluation through healthcare relevance/impact and solution/technical feasibility.
- The programme's hard boundary is assistive, operational software. Diagnosis, treatment recommendations, clinical decision support, clinical risk scoring, interpretation that produces clinical guidance and autonomous clinical advice are out of scope.

#### TANUH - C-CAMP Innovations Program

- Source: the LinkedIn text and screenshot supplied in the current conversation. No official TANUH guide or completed application export is saved locally yet.
- Hosts named in the supplied text: the AI Centre of Excellence in Healthcare at IISc and C-CAMP.
- Stated purpose: identify and support Indian-developed AI innovations for priority non-communicable disease areas.
- Stated readiness stages: idea to proof of concept, proof of concept to minimum viable product, and deployment ready.
- Stated focus areas include cardiovascular and cerebrovascular disease; oral, breast, cervical and lung cancer; chronic respiratory disorders; musculoskeletal disorders; and metabolic liver disease, with the list described as non-exhaustive.
- Stated grant pool: more than INR 2.25 crore for at least eight startups, with support varying by stage.
- Stated deadline in the supplied post: 28 September 2026.
- The supplied screenshot shows these application sections: Track, user and use case; the problem; solution and name; how it will be built; existing work; pitch deck; and links/supporting files.
- All TANUH facts above are user-provided source material and have not been independently verified in this turn. Do not treat the shortened LinkedIn link as a substitute for the official application instructions.

### The product

`Continuity Loop` is the current software identity in the prototype, deck and deployment handoff. Its narrow workflow is:

1. A clinician selects a patient and starts or continues a goals-of-care conversation.
2. A clinician-owned checklist marks each domain as discussed, not discussed or needing clarification.
3. A synthetic source note is converted into a structured draft with field-level source links.
4. A physician reviews and corrects the draft.
5. Patient or surrogate acknowledgement is recorded as an attestation state, not a legal signature.
6. Release creates a new append-only version rather than overwriting the previous one.
7. A receiving physician records a care relationship and access purpose before retrieving the latest verified summary.
8. A coordinator worklist tracks clinician-set follow-up dates, outreach outcomes and rescheduling.

The current prototype is synthetic and browser-local. It does not use real patient data, real authentication, a durable clinical database, a hospital EHR, ABDM, live messaging, an external AI model, real signatures or a cryptographically immutable audit system. It is not a living will, Advance Medical Directive, DNAR order, treatment order, prognostic model or treatment recommender.

### The clinical material

The two new Word files are clinical source material, not software requirements that have been approved and implemented:

- `C:\Users\basit\Downloads\Healthathon form.docx`: a shorter working form with Sections I-IV. The extracted content contains repeated blocks for capacity, directives, patient knowledge, values, hospital transfer, CPR, ventilatory support and treatment parameters.
- `C:\Users\basit\Downloads\Saanthvana v 1.0.docx`: a substantially fuller `Emergency Care & Treatment Preference Record (ECTPR)`. The document itself calls the print artifact `ECTPR Beta v0.1` in its version-control appendix, so the filename `v 1.0` does not prove clinical finality.

The files do not establish which document the clinicians consider authoritative. The shorter file has the later Word metadata timestamps, while the Saanthvana file has the fuller content and an earlier creation date. This needs an explicit clinician decision.

## What happened from the start

### 24 August 2026 clinician meeting

Primary records:

- `MEETING_TRANSCRIPT_2026-08-24.txt` - saved transcript.
- `MEETING_TRANSCRIPT_ANALYSIS.md` - derived analysis.
- `MEETING_SUMMARY_FOR_TEAM.md` - team-facing summary.
- `GOCD (1).pdf` - Dr. Sujay's original one-page proposal.
- `MEETING_EVIDENCE_MAP.md` - quote-level evidence and the boundary between clinician statements and later product decisions.

The clinicians described a goals-of-care conversation for people with advanced cancer or other serious chronic illness. The conversation can cover home versus hospital care, ward versus ICU, ventilation, dialysis, a time-limited treatment trial, comfort and family involvement.

The central failure described was not lack of clinical judgement. It was that the conversation may not happen in a structured way, may not be documented, may not be portable, may become outdated, or may be unavailable to an unfamiliar emergency physician. The meeting specifically described the 3 a.m. or 4 a.m. handoff where the family might have to repeat a difficult conversation.

The clinicians' intended software role was:

- guide the treating clinician through the conversation;
- capture or structure what was actually said;
- allow a reviewer to trace a field back to its source;
- require clinician and patient/surrogate review;
- preserve later changes as versions; and
- let a receiving physician find the latest record.

The clinicians explicitly corrected the language from “will” or “living will” to a goals-of-care clinical document. They said the patient or family must not update a released record without another clinician being involved. The downstream user is the emergency or receiving physician; the oncologist or primary clinician initiates the record.

The clinicians offered to provide the conversation framework and approximately 20 simulated scenarios. The meeting did not establish a hospital pilot, monthly case volume, buyer, production data access, legal approval, ethics approval or a current operational baseline.

### 25 August 2026 team messages

The supplied chat says:

- Sujay did not have Awaiz's number and asked that Awaiz be added to the group.
- Basith said Awaiz had been added, that there might be one more person, and that he would confirm.
- Basith referred to `Technical thoughts and questions.docx`, said he was reviewing the meeting transcription again, and planned to share a website MVP link.
- Sujay asked for rough deadlines and deliverables from both sides, said Round 1 ended in 17 days, and said the team needed a pitch deck and MVP by then.

The exact technical-notes DOCX is not present in the repository, Downloads tree or the searched user profile. It remains a missing source.

### 26-27 August 2026 prototype and pitch work

The saved handoff says a synthetic Continuity Loop prototype was deployed to Vercel and an eight-slide editable deck was prepared. The deck and handoff deliberately exclude live AI, legal authority, treatment recommendations, real authentication, EHR/ABDM integration and patient data.

The current local prototype later received additional UI and workflow commits through 6 September. Therefore the historical pitch/deployment notes and the current nested Git checkout are not the same source snapshot.

### 29-30 August 2026 caregiver-facing exploration

`NEXT_530_MEETING_BRIEF.md` records an exploration of a patient/caregiver-facing `Care Journey Companion` connected to the clinician-led workflow. It describes a read-only family surface, named next steps and clinician-controlled release.

This is a product-direction exploration, not proof that the Health-a-thon registration changed. The registration record remains clinician-facing Patient Follow-up & Continuity of Care. The current code contains both the clinician/coordinator workflow and a simulated read-only caregiver surface.

### 3 September 2026 Saanthvana document

The file metadata reports `Saanthvana v 1.0.docx` was created and modified on 3 September 2026. Its body is an extensive ECTPR draft, including an emergency quick view, detailed preference sections, legal/procedural notes, pilot requirements, measures, risks, wallet cards and sources.

### 6 September 2026 Health-a-thon Connect webinar

The remote teammate commit added the transcript to `healthathon-2026-raw.txt`. The transcript says:

- teams need at least one qualified doctor and one technology member, with two to five members total;
- the listed use cases are illustrative and teams can bring their own practical workflow;
- clinician-facing and caregiver-facing use cases can be combined;
- a working prototype is not required for the initial idea submission;
- a PPT, demo video or other supporting material may be attached;
- ideas are considered through healthcare relevance/impact and solution/technical feasibility;
- no dataset is supplied by the programme;
- generic existing solutions are insufficient unless the team builds and demonstrates a new use case or meaningful extension; and
- the programme is focused on non-clinical, assistive workflows.

### 9 September 2026 remote update

The parent repository was fast-forwarded from `e833ae6` to `ffaa89f` after fetching `origin/main`. The commit was made by `shaikawaiz2501 <shaikawaiz2501@gmail.com>` and is titled `Add Health-a-thon 2026 Connect webinar transcript to raw text`.

The commit changes only `healthathon-2026-raw.txt` by adding 213 lines. It is not a UI implementation commit.

### 10-12 September 2026 Healthathon form metadata

`Healthathon form.docx` has Word metadata naming Sujay Halkur Shankar as creator, with creation on 10 September and modification on 12 September 2026. It is structurally much shorter than Saanthvana and contains repeated sections. It has no tracked insertions/deletions or Word comments in the package.

### 13 September 2026 dashboard capture supplied by Basith

The current conversation supplies the visible Round 1 dashboard text directly. The exact options and limits are preserved in `USER_SUPPLIED_CONTEXT_2026-09-13.txt` and mapped to paste-ready answers in `submission.md`:

- Track options: `Cancer`, `Diabetes`, `Maternal & Women's Health`.
- Primary-user options: `Doctor / Care Team`, `Patient / Caregiver`, `Others`.
- Use-case options include `Patient Follow-up & Continuity of Care`, `Consultation Readiness & Patient Journey Review`, `Care Journey Companion`, `Long-term Care Engagement`, `Family & Caregiver Support`, and other operational/engagement headings.
- Problem: 150 words / 1,200 characters maximum, 100 characters minimum.
- Solution: 150 words / 1,200 characters maximum, 100 characters minimum; solution name minimum five characters; scope confirmation required.
- Build: 80 words / 600 characters maximum, 30 characters minimum.
- Existing work: yes or no.
- Deck: PDF or PowerPoint, 6-8 slides, maximum 25 MB.
- Links/supporting files: optional, one link per line, and openable without requesting access.

The working selections are Cancer, Doctor / Care Team, and Patient Follow-up & Continuity of Care. A screenshot copy is retained under `source-materials/user-provided/healthathon-dashboard-checklist.png`. The two supplied DOCX files are retained under the same private source-materials directory; they are not automatically public submission attachments.

## Team and contact inventory

This is an inventory of contacts evidenced in the saved material. A missing phone or email means it was not found in the local sources, not that it does not exist.

| Person or organisation | Role in this context | Contact evidence | Status |
| --- | --- | --- | --- |
| Basith | Product and technical lead; current local workspace owner | Parent Git identity: `basithladoo@gmail.com`; prototype Git identity: `workwithdevit@gmail.com` | Confirmed in repository history; these are Git identities, not a preferred outreach channel |
| Dr. Sujay / Sujay Halkur Shankar | Clinical concept originator and clinician lead; IISc context in the team discussion | No phone or email found in the local project sources; PDF metadata names Sujay Halkur Shankar | Confirmed participant; direct contact still needed for the application |
| Dr. Sharda | Clinical collaborator referenced in the meeting transcript | No phone or email found | Confirmed by transcript mention; full name, specialty and institution need confirmation |
| Awaiz / Shaik Awaiz | Technical teammate added to the group; author of the pulled webinar commit | `shaikawaiz2501@gmail.com` appears in the Git commit metadata; no phone found | Confirmed contributor; role and preferred contact should be confirmed |
| Hudaa | Proposed allied-health, simulation and patient/family perspective member | No phone or email found | Proposed fifth member in `HUDAA_ONBOARDING_BRIEF.md`; membership should be confirmed |
| Health-a-thon / Reskilll | Programme contact | `healthathon@reskilll.com` in the saved official-site capture | Confirmed programme contact in the local source |
| TANUH, IISc, C-CAMP | New programme and institutional stakeholders | Only the supplied shortened LinkedIn/application link is available; no direct application email or official guide is local | Needs official contact and application instructions |

The names tagged in the supplied LinkedIn post, including government, C-CAMP, IISc, NITI Aayog, BIRAC, Karnataka and other public figures, are public-post tags rather than confirmed team contacts. No direct relationship or outreach route is established for them.

## Saanthvana and Healthathon form content map

### Healthathon form

The extracted structure is:

- Section I: general information, patient, physician and family details, decision-makers and people to inform.
- Section II: preliminary medical information, capacity, existing directives, patient knowledge, patient words, unacceptable outcomes.
- Section III: decisive actions, hospital transfer, CPR, oxygen/ventilatory ceiling, ICU, vasopressors, dialysis, feeding and overall goals of care.
- Section IV: patient, surrogate, witness and clinician signatures/declarations.

The source contains duplicated content blocks for capacity, directives, patient knowledge, values and decisive-action choices. The duplication is visible in the extracted Word structure; visual page rendering was not completed because the packaged renderer could not start in this environment due to a missing `pdf2image` dependency.

### Saanthvana ECTPR

The fuller document includes:

1. ED quick view for rapid reading.
2. Eligibility, capacity and how to use the record.
3. Patient details and care contacts.
4. Capacity, existing directives, surrogate authority and family disagreement.
5. Prognostic understanding and what the patient knows.
6. Patient values, unacceptable outcomes and psychosocial, spiritual and practical needs.
7. Hospital transfer and ambulance expectations.
8. CPR.
9. Oxygen and ventilatory support ceilings.
10. Time-limited trials and markers of improvement/non-improvement.
11. Other organ support, including dialysis, ECMO, pacing and ICD deactivation.
12. Feeding and fluids.
13. Blood and blood products.
14. Surgery and invasive procedures.
15. Medicines, tests, monitoring and anticipatory medicines.
16. Care that continues regardless of treatment limits.
17. Place of care, place of death and after-death arrangements.
18. Change, cancellation, disagreement and mandatory review triggers.
19. Signatures, copies and optional legal Advance Medical Directive conversion.
20. Appendix A: conversation method and phrases.
21. Appendix B: recognising the dying phase.
22. Appendix C: Indian legal and procedural position.
23. Appendix D: beta prerequisites, measures and known risks.
24. Appendix E: wallet/bedside card.
25. Appendix F: cited sources.

The document is rich clinical input but contains treatment ceilings, CPR/DNACPR language, ventilation decisions, legal claims and emergency instructions. It cannot be copied directly into an autonomous product or treated as final clinical/legal policy. The document itself calls for ethics approval, legal review, translation/back-translation, readability testing, low-literacy support, clinician training, a retrieval plan, version control and safety monitoring before a beta.

## Current repository and Git state

### Parent archive

- Root: `C:\Users\basit\Downloads\CODE\healthathon`.
- Remote: `https://github.com/basithladdu/healthathon.git`.
- Branch: `main` tracking `origin/main`.
- Current HEAD after the pull: `ffaa89f4518109b09cdd483831292cd62bb92a33`.
- Working tree: clean.
- No push was performed in this pass.

### Nested prototype repository

- Root: `C:\Users\basit\Downloads\CODE\healthathon\prototype`.
- Current HEAD: `cea6d42189fad00fae79a39f256685b89b8a37ba` (`Give the app one vertical rhythm and one display tracking`, 6 September 2026).
- No remote is configured for this nested repository.
- Working tree has one pre-existing untracked build artifact: `prototype\tsconfig.tsbuildinfo`.
- The recent nested history contains Basith-authored UI and workflow refinements through 6 September. No remote teammate UI branch or push was available to pull.

### Existing product and pitch artifacts

- Prototype source: `prototype\app\continuity-prototype.tsx` and `prototype\app\globals.css`.
- Existing deck: `pitch\Continuity_Loop_Pitch_Deck.pptx`, eight editable slides with source blocks in speaker notes.
- Current visual deck candidate: `pitch\Continuity_Loop_Pitch_Deck_Visual.pptx`, rebuilt from the locally copied Utility and Mulberry presentation builders. It keeps eight slides but leaves product-surface screenshot slots blank until the teammate's UI is ready.
- Presentation source archive: `source-materials\ppt-sources\README.md` and its copied JavaScript builders. The sibling repositories were not modified.
- Existing screenshots and render QA: `pitch\Continuity_Loop_Pitch_Deck\` and `output\`.
- Historical production URL in the handoff: `https://continuity-loop-healthathon.vercel.app`.
- The public URL was rechecked in Chrome on 13 September 2026: the intended Continuity Loop surface loaded at desktop width and at a 390px viewport; the mobile DOM reported no horizontal overflow and the tab reported no warning/error console entries. This verifies the visible public demo only, not clinical deployment or production integrations.
- The current browser verification showed the intended clinician handoff, follow-up worklist, verified-record retrieval, synthetic/local demonstration label and safety boundary. It did not authenticate, submit the Health-a-thon form, upload files or transmit project material.

## Evidence hierarchy

Use this order when preparing a submission or making a claim:

1. Direct clinician words and supplied clinical documents.
2. Authenticated dashboard observations and the pulled programme webinar transcript.
3. Current source code and reproducible local tests.
4. Derived project briefs, pitch notes and decision matrices.
5. Strategic interpretation or planned future work.

Never present a planned pilot target, a simulated metric, a historical deployment check or a document's legal statement as a current clinical outcome or approval.

## Conflicts and unresolved decisions

1. **Health-a-thon use case:** the authenticated record says Patient Follow-up & Continuity of Care; `NEXT_530_MEETING_BRIEF.md` explores Care Journey Companion. The code supports both surfaces. The registration change is not evidenced.
2. **Programme separation:** Health-a-thon and TANUH are different opportunities with different submission contexts. Do not reuse a Health-a-thon deadline, prize statement or use-case label as a TANUH fact.
3. **Product name:** the current application-facing working name is `Continuity Loop`; the new clinical material is branded `Saanthvana` and titled ECTPR. A visible-web naming screen did not surface an exact direct product/company collision for `Continuity Loop`, but this is not formal trademark, company, app-store, social-handle or domain clearance. See `PRODUCT_NAMING.md`.
4. **Clinical source authority:** the shorter Healthathon form and the fuller Saanthvana ECTPR are both present outside the repository. The clinicians must say which is current, whether either is approved, and what is suitable for a software prototype.
5. **AI claim:** the current demo is deterministic and has no external AI model. Any TANUH application must label AI transcription/summarisation as planned unless a real model is implemented and evaluated.
6. **Pilot readiness:** no named pilot institution, workflow owner, baseline, ethics approval, legal review, patient consent process or 20-case reference set is present.
7. **Team contacts:** phone numbers and full application-ready bios for the clinicians and all teammates are missing.
8. **Submission requirements:** the Health-a-thon dashboard capture now supplies the visible fields and limits for Round 1; the official TANUH option set, word limits, eligibility rules, startup/entity fields, budget template and file-size limits are still missing.
9. **Historical paths:** some archive documents refer to `prototype-source`, `prototype-source-vercel` or `deployment-build` paths that are not present at the current repository root. Treat those references as historical, not live files.

## Missing files and context requested from the user

The highest-value missing inputs are:

- `Technical thoughts and questions.docx`.
- The official TANUH application URL or downloaded guide/PDF, including eligibility, scoring, word limits, stage definitions, budget fields and upload limits.
- The full current team roster, each person's role, preferred email/phone and whether Hudaa is joining.
- A clinician-approved version of the conversation framework or an explicit statement that Saanthvana is still a draft.
- The named pilot hospital/clinic, workflow owner, approximate case volume and current baseline.
- The 20 synthetic or anonymised scenarios and clinician-authored reference outputs, when available.
- Any legal, ethics, data-governance, consent or institutional-review documents.
- A current UI build or branch if the teammate is working outside `basithladdu/healthathon`.
- A demo video, final QR target, current production URL confirmation and any approved screenshots.
- Applicant/startup details for TANUH: legal entity, incorporation/startup status, founder bios, IP ownership, grant use and deployment plan.
- The complete raw capture of the current user message is now in `USER_SUPPLIED_CONTEXT_2026-09-13.txt`; the original Health-a-thon raw file contains a labelled pointer to it.

## Immediate application position

For the current Health-a-thon form, the provisional submission is **Cancer -> Doctor / Care Team -> Patient Follow-up & Continuity of Care**, with `Continuity Loop` as the existing product. The strongest separate TANUH framing is also Cancer plus an assistive continuity workflow that helps clinicians complete, review, version and retrieve goals-of-care documentation. The likely TANUH readiness stage is **proof of concept to MVP**, because there is a working synthetic prototype and deck but no validated live AI, clinical pilot, durable backend, institutional integration or deployment evidence. This stage assignment is a working inference and requires confirmation against the TANUH form.

The safest application claim is: **the team has built a functional workflow prototype and is seeking clinical protocol validation, AI evaluation and pilot support.** It is not: “the system is deployment-ready,” “the AI makes treatment decisions,” or “the ECTPR is legally binding.”
