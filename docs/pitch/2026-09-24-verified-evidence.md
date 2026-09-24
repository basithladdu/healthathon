# Verified evidence for the revised pitch

Reviewed 24 September 2026. This ledger separates the two meetings, published evidence, proposed product behaviour and results the team has not yet measured.

## Meeting coverage

- **21 September:** full transcript at `source-materials/meetings/2026-09-21-transcript.txt`, byte-identical to the supplied attachment. Findings and line references: `2026-09-24-meeting-21-evidence.md`.
- **23 September:** full 779-line supplied transcript at `C:/Users/basit/.codex/attachments/322db338-d7fd-440d-a8b1-0cf99da52c19/Pasted text.txt`. Findings: `2026-09-24-meeting-23-evidence.md`.
- **Later 23 September feedback:** `docs/meetings/2026-09-23-chat-follow-up.md`. This replaces the earlier scenario with a 62-year-old woman, uses “best supportive care” at the start, removes the death outcome and keeps AI central.
- **Clinician slides:** `Saanthvana_Background_Rationale_1.pptx` and `Presentation draft.pptx`, mapped in `2026-09-24-clinician-deck-evidence.md`.
- **24 September feedback:** `docs/meetings/2026-09-24-morning-feedback.md`. The final revision uses Saathi, one Care Note label, prominent signed-version access, a separate doctor review step and Sujay's proposed conversation measures.

## Claims used in the slides

### India cancer burden

**Verified:** IARC's GLOBOCAN 2024 India fact sheet reports 1,562,581 new cases in 2024. Slide rounds this to **15.6 lakh** and retains the year. The fact sheet is the 2024 estimate published in 2026, not a measurement of annual cases for every year.

Primary source: [IARC India fact sheet](https://gco.iarc.who.int/media/globocan/factsheets/populations/356-india-fact-sheet.pdf).

### Unmet palliative-care need

**Verified:** Patil S, Sharma P, Arora A, Zadey S. *Unmet need for cancer palliative care in India.* BMJ Supportive & Palliative Care, 2024. DOI: 10.1136/spcare-2024-004978.

The authors report **98.39%** unmet need across ten cancer types. They define unmet need using people with metastatic cancer who did not receive palliative care, drawing on the National Cancer Registry Programme 2020 report. This measures receipt of care in that population, not awareness, and not all people with cancer. The transcript's 98.3% is an imprecise recollection; 98.39% is used as published. Do not multiply it by the IARC new-case count because populations and years differ.

Primary author text: [author-uploaded full paper](https://www.researchgate.net/publication/381176125_Unmet_need_for_cancer_palliative_care_in_India). Bibliographic record: [PubMed](https://pubmed.ncbi.nlm.nih.gov/38834235/). The publisher page returned access denied; the publicly available author text supplied the definition and table. This access route does not establish a reuse license for the article's graphics.

### Documentation in Chennai

**Verified:** Thangasamy DRJ et al. *Initiating and Documenting Goals of Care Discussion in Patients with Advanced Pancreatic and Colorectal Cancers: A Quality Improvement Project in a Low Resource Setting.* 2025. DOI: 10.1177/26892820251392545.

The single-centre Cancer Institute (WIA), Chennai project reports **0% to 92%** documented conversations, with **24 of 26 eligible patients** documented after implementation. The programme used a standard procedure, a colour-coded form and team awareness/referral work. Its eligible population had advanced pancreatic or colorectal cancer. The planned electronic-record implementation had not been completed. The deck's editable chart reproduces the reported percentages. It is evidence for structured documentation, not a Saathi, AI or national outcome.

Primary source: [full paper](https://journals.sagepub.com/doi/10.1177/26892820251392545).

### Doctor training

**Verified:** Jacob JA, Thangadurai P, Rebekah GJJ, Kuruvilla A, Gopalakrishnan R. *Knowledge and attitude towards advance directives for patients with terminal illnesses among doctors working in a tertiary care hospital.* National Medical Journal of India 2026;39:140–146. DOI: 10.25259/NMJI_817_2023.

Table 1 reports **45 of 391 doctors (11.5%)** had prior training in advance directives. This was one tertiary hospital survey, at CMC Vellore. It does **not** mean only 11.5% knew the definition, nor is it a national estimate. Advance directives and the app's care-conversation notes are different instruments. The finding supports investigating an approachable guided workflow; it does not prove the app's effectiveness.

Primary source: [full paper and tables](https://nmji.in/knowledge-and-attitude-towards-advance-directives-for-patients-with-terminal-illnesses-among-doctors-working-in-a-tertiary-care-hospital/).

### Patient choice, with explicit reporting provenance

**Reported finding, original survey not retrieved:** Priya Sharma's original IndiaSpend report (3 June 2019) says **88%** of respondents in HCAH's Living Will Survey wanted to choose their treatment near the end of life. It describes over 2,400 urban respondents across seven cities, with equal numbers of men and women, all hospitalised for more than one day in the previous year. This is not a cancer-only sample or a national estimate. Slide 3 attributes the finding to IndiaSpend reporting and retains the year and urban population. It preserves the patient perspective requested in the meeting while showing the weaker source boundary.

Source: [IndiaSpend's original reporting](https://www.indiaspend.com/73-urban-indians-ignorant-of-legal-right-to-living-will/). The [linked survey](https://documentcloud.adobe.com/link/track?uri=urn%3Aaaid%3Ascds%3AUS%3A5791f42c-446e-4d94-a275-1f9e6a0f4ce9) redirects to Adobe Acrobat and was inaccessible. No original dataset or questionnaire was examined.

## Numbers excluded

- **3.3 million five-year prevalence:** the meeting asked to remove this. The incidence figure is enough context.
- **140 physicians, 15.6% and old morphine-consumption comparisons:** removed from the main pitch following the meeting or because the population/date/measure could mislead.
- **27% knew of living wills, 6% of those aware had created one:** extra figures from the same reported HCAH survey. They are omitted to keep the patient perspective clear without overloading the slide or mixing denominators.
- **Measured time savings, accuracy, patient outcomes, adoption, revenue or partnerships:** none established by either meeting. No invented product results appear in the pitch.

## Product claims and scope

The entry remains **Cancer / Patient and Caregiver / Family & Caregiver Support**. The shared care conversation is the focus. Documents, calendar, journaling, support discovery and named family tasks support it. Two slides explain AI's intended jobs: preparing a Care Note from the conversation, finding answers in the patient's own documents, making a dated treatment history from recorded treatment dates, and preparing medicine reminders from printed prescriptions. Every job keeps the source and includes review and correction. Diagnosis, treatment recommendations, clinical risk scoring and interpretation of test results remain outside scope.

The treatment-history brief was adopted in the 21 September transcript, lines 1135–1147. Printed or typed prescription extraction appears at lines 1099–1123 and in the 23 September transcript, lines 269–277. Neither discussion adopted recognition of handwritten prescriptions. Treatment dates must come from the record; a report date does not establish when treatment occurred. Live AI calls and image reading have not been verified or connected in the deployed flow.

The current browser flow supports uploads, recording, source passages, rule-based note preparation, review, typed names, dated versions, PDFs, calendar and role-switched family tasks. Reliable AI/transcription, verified identity and sharing between phones remain to connect. A typed name is not a verified digital signature, and a care note is not a legal advance directive. A map marker is not verified service availability.

The proposed **60–90 day trial** comes from the supplied Health-a-thon design brief, not either meeting. Sujay's 24 September feedback adds patient requests, doctor-started discussions and documented conversations before and after deployment as primary measures. Keep requested, held, documented and signed events separate. Evaluation also covers note-writing and correction effort, finding the latest plan, task completion and errors. It requires users, a baseline and an agreed protocol before results can be claimed. Browser activity counters do not themselves prove a discussion took place clinically.

Official context: [Health-a-thon guide](https://healthathon.reskilll.com/guide), plus the complete brief pasted by the user. No official weighted judging rubric was retrieved; the review perspective is our assessment, not a published judge score.

## Judge assessment

The focused continuity problem is stronger than pitching a long list of generic health features. The evidence supports the need and a reason to try structured documentation. It does not validate this product. The most material remaining question is whether the proposed AI drafting and shared family access can work reliably while reducing the doctor's work. Confidence is high in the source corrections and brief alignment, and moderate in the product hypothesis until users test it.

## Where both meetings appear in the deck

| Meeting input | Slides |
| --- | --- |
| 21 September: patient and caregiver benefit, with clinician-led discussion | 2, 4 and 6 |
| 21 September: keep discussion, decisions and unanswered questions distinct | 4 |
| 21 September: keep earlier signed versions | 4 and 6 |
| 21 September: AI drafts from the conversation and retrieves only from the person's own records | 4 and 5 |
| 21 September: dated treatment history and reviewed printed-prescription reminders | 5 |
| 21 September: reduce new data entry for the doctor | 4 and 7 |
| 23 September: lead with a clear problem and research evidence | 2 and 3 |
| 23 September: incidence, unmet need and Chennai documentation figures | 3 |
| 23 September: retain both patient and doctor perspectives | 3 |
| 23 September: keep AI central and supporting features around it | 4, 5 and 6 |
| Later 23 September: revised story, best supportive care, no death outcome | 2 |
| Later 23 September: today's plan and named family tasks | 6 |
| Both meetings: distinguish what works from integrations still planned | 7 |
| 24 September: latest signed Care Note and QR on Home | 6 |
| 24 September: distinct doctor review before signing | 4 |
| 24 September: patient requests, doctor-started and documented conversations | 7 |
| Basith: brand, Hindi meaning, large working app link and all five team members | 1 |
| Basith: complete background reference titles and blue clickable links | 8 |

The deck omits uncommitted pricing, platform choices and institution partnerships. None of these became an agreed or verified capability in the meetings.

All nine background references are preserved on slide 8 and in `submission/Saanthvana_Research_References_2026-09-24.md`. The app, GitHub, WhatsApp and reference links are native hyperlinks. The cover keeps three photo spaces empty because only Basith's and Awaiz's portraits were supplied.
