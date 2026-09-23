# Meeting 21 evidence for the pitch

Source: the first meeting transcript at `source-materials/meetings/2026-09-21-transcript.txt`, cross-checked against the supplied attachment `Pasted text.txt`. The files have identical SHA-256 hashes, so the line references below apply to both. This is a product-design discussion, not a clinical study or a record of patient outcomes.

## Evidence and numbers actually mentioned

| Item | What the meeting says | Evidence status |
|---|---|---|
| Symptom rating | A 0–10 scale was suggested to make symptom tracking more consistent if it is meant to support medical review or medicine adjustment (lines 459–491). | Design suggestion; no scale, threshold, or clinical validation was selected. |
| Report length | A PDF upload example was estimated at about 10–12 pages (lines 279–291). | Example used to discuss upload limits; not a measured user requirement. |
| Source-only question answering | “11 pages” was a hypothetical example for finding information in uploaded files (lines 1025–1033). | Proposed AI behavior; not a test result or evidence of accuracy. |
| Slide count and timing | The group discussed 6–8 slides, accepted eight, and planned to upload on the 24th ahead of a 25th deadline (lines 85–117). | Meeting logistics, not product evidence. |
| Research and studies | A participant referred to researched papers/data for the deck, but no study, sample, result, or statistic is named in this meeting (lines 85–87). | No research claim can be sourced to this meeting. Do not present a statistic or study result as meeting evidence. |

Other concrete clinical-data examples: cancer-related diagnostic documents such as PET scans and biopsy reports; monitoring tests such as CBC, LFT, and KFT; and dates of chemotherapy or radiotherapy (lines 297–303, 1061–1071). The discussion proposed separating diagnostic records from serial monitoring values and viewing trends over time or treatment cycles. These are product ideas, not evidence that Saanthvana extracts or trends them correctly.

## Strongest clinician observations

- **The conversation should be the center of the product.** The doctor may initiate the discussion, but the patient and caregiver are the beneficiaries (lines 53–69). Later, the clinician explicitly recommends presenting the conversation as the centerpiece, with other features around it (lines 1021–1043).
- **Keep a record of who was present and what changed.** Notes should retain the patient’s words, what matters to the family, participants/voices, next steps, and what remains to discuss; later discussions should have new versions while earlier ones remain available (lines 337–395).
- **Separate a discussion from a decision.** The clinician identified a meaningful distinction between a general conversation and a specific decision (the example given was about ventilation) and suggested that any AI-created note should distinguish these types (lines 945–949). This was a proposed classification, not an implemented or validated capability.
- **Do not infer agreement or treat a verbal conversation as a decision.** The displayed choices were confusing; the group asked for clear roles and for the note to distinguish agreement from no conclusion or a decision deferred. The clinician rejected an ambiguous “verbal agreement” label (lines 903–941). The meeting also says the legal status is uncertain/“gray”; it does not establish a legal rule or authorize the app to turn a note into a directive (lines 805–819).
- **A signed version should be preserved.** The clinician proposed locking a version after the patient signs it and carrying forward prior fields only as a starting point for the next discussion (lines 951–959). This is a proposed workflow, not evidence of a legally valid signature or immutable storage.
- **Avoid unsafe medication workflows.** The clinician preferred recording major events such as chemotherapy or radiotherapy over patient-entered daily medication reminders, because incorrect entries could be relied on instead of a prescription (lines 1073–1097). Prescription-reading AI linked to calendar reminders was then discussed as an idea, with typed/printed prescription constraints; it was not agreed as a current capability (lines 1097–1123).
- **Clinical tools need useful structure.** A symptom diary was seen as useful in palliative care; if it is used to inform medicine adjustment, entries should be more objective than free-form symptom lists (lines 459–491). This is a clinician’s design observation, not a measured outcome.
- **Patient and caregiver experience matters.** A clinician said the team had become distant from patients’ lived experience and praised the team’s patient-centered feature thinking (lines 1159–1163). This is feedback, not evidence that the product reduces distress or improves care.

## Agreed direction versus exploration

**Clear direction in the meeting:** reduce clutter and feature breadth; group patient-facing functions coherently; place clinical documents and the goals-of-care discussion prominently; use a dated/versioned note; keep questions for the next doctor discussion; and focus the presentation on the conversation and its surrounding support (lines 293–305, 727–731, 987–1017, 1021–1059). The clinician proposed three named areas—clinical documents, access to care, and “My space”—plus quick QR access; the conversation does not clearly name a fourth category (lines 995–1017).

**Exploratory, unvalidated ideas:** verify a clinician’s registration number against state medical councils (lines 133–223); upload report photos/PDFs and extract results or show trends (lines 251–303, 1061–1073); add facilities such as inpatient/outpatient care, imaging, and ambulance availability to a care-center directory (lines 341–381); create patient communities or connect to disease-specific support groups (lines 501–545); add transport, home help, nursing, or medication delivery (lines 661–725); make QR or single-use PDF sharing work for emergency clinicians outside the app (lines 735–819); use AI to extract prescription schedules or summarize dated treatment history from uploaded records (lines 1097–1155). The transcript does not establish that any of these ideas were built, tested, or adopted as committed scope.

The team discussed privacy concerns for voice and clinical data, including third-party use and targeted advertising. Compliance with India’s DPDP Act was promised for later, but no implementation, assessment, or verification appears in this meeting (lines 553–579). Do not turn that promise into a current privacy/compliance claim.

## What the current eight-slide text leaves out

The 24 September slide text tells a coherent, appropriately bounded story about the care conversation, clinician-reviewed notes, family support, current versus planned capability, and a future evaluation. Compared with this meeting, it omits several useful design specifics:

1. It does not explain that the note should distinguish a general discussion from a specific decision, or explicitly say that “not decided” and “not discussed” remain separate outcomes (meeting lines 911–949). This is the most important omission for understanding what the note records.
2. It says earlier notes remain available, but does not explain the proposed version boundary: a reviewed/signed version stays fixed while a later conversation starts a new version (lines 951–959). State this only if the current implementation actually enforces it.
3. Its support-center promise is broad. The meeting raised the need to know what facilities a center offers and whether listings are vetted; that source and facility data were uncertain (lines 307–381). “Call to confirm” helps, but the deck should not imply comprehensive or verified coverage.
4. “Support groups” may read as an in-app peer community. The meeting explored a community but raised privacy concerns about grouping people by diagnosis; no design or safeguards were settled (lines 501–579). Clarify whether the feature is a directory of external groups or an in-app community.
5. The deck does not mention symptom journaling, but that is a reasonable scope choice: the meeting supported it while also warning against implying that subjective entries safely guide medication changes (lines 459–491).

The meeting’s feature discussions do not justify adding medication reminders, automatic report interpretation, care-center claims, or peer-community claims to the deck as current capabilities. The meeting repeatedly asks to reduce scope; omitted exploratory ideas should remain omitted unless independently verified.

## Implications for a judge

The strongest case is a focused continuity problem: a clinician-led conversation is documented in patient-centered language, reviewed by the clinician, retained by version, and made findable by the patient/family. The central differentiator should be the human-reviewed record and its continuity, not a list of loosely connected AI features.

The judge will likely ask what AI does today, what source it uses, how errors are corrected, and whether it can mistake a discussion for an agreed decision. The meeting supports describing source-linked drafting and document retrieval only as proposals. It contains no measured time savings, patient outcome, accuracy rate, adoption figure, named study, or verified privacy safeguard. The proposed evaluation questions in the deck should therefore remain questions, not results.

Avoid legal-sounding claims about consensus, signatures, or treatment instructions. The clinicians explicitly described legal status as uncertain and the team’s “agreement” options as confusing. Say that the app records and organizes the discussion for review; do not claim that a note itself authorizes or directs treatment.
