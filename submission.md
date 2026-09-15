# Health-a-thon 2026 - Round 1 submission draft

Prepared 13 September 2026. This is a working answer sheet for the exact dashboard fields supplied in the current conversation. Nothing has been submitted.

## Application identity

- Programme: Health-a-thon 2026 - Round 1, "Zeros and Ones"
- Track: `Cancer`
- Primary user: `Doctor / Care Team`
- Use case: `Patient Follow-up & Continuity of Care`
- Working solution name: `Continuity Loop`
- Existing work: `Yes - we are building on an existing product`
- Current MVP state: synthetic, browser-local proof of concept; no live AI or clinical deployment claimed

## 1. Track, user and use case

Select these exact options:

- Which track are you submitting under? `Cancer`
- Who is the primary user of your solution? `Doctor / Care Team`
- What use case is your team addressing? `Patient Follow-up & Continuity of Care`

The patient/caregiver view is a supporting part of the connected workflow. The primary user remains the doctor/care team because the clinician owns review, release and follow-up decisions.

## 2. The problem you are solving

Form limits: maximum 150 words and 1,200 characters; minimum 100 characters.

### Paste-ready answer

People with advanced cancer may have important care-preference conversations with an oncologist, palliative-care clinician, or family, but what was discussed is often spread across notes, visits, and institutions. The conversation may be incomplete, difficult to retrieve, or out of date when the patient deteriorates. An unfamiliar emergency or receiving clinician may then lack the latest reviewed context, while the patient and family must repeat a distressing discussion under pressure. Clinicians also have no clear follow-up worklist for unresolved topics or changes. This affects patients, caregivers, and the care team across oncology, palliative, emergency, and home settings. The problem is continuity and coordination, not automated clinical decision-making: a human-reviewed record needs to travel with the patient and be revisited when circumstances change.

## 3. Your solution, and its name

Form limits: answer maximum 150 words and 1,200 characters; minimum 100 characters. Solution name minimum 5 characters. The scope confirmation must be checked.

### Solution name

`Continuity Loop`

### Paste-ready answer

Continuity Loop is a clinician-controlled workflow for recording, reviewing, revisiting, and retrieving goals-of-care conversations for people with advanced cancer. A clinician guides the conversation through agreed domains and marks each as discussed, not discussed, or needing clarification. The system creates a structured draft from the source note, links fields to the source, and requires physician review before release. Patient or surrogate acknowledgement is captured as an attestation; a released record is never silently overwritten. A named coordinator can track clinician-set follow-up dates and outreach outcomes. An authorised receiving clinician records the care relationship and access purpose before viewing the latest verified version. The current MVP is synthetic, browser-local, and deterministic. It does not diagnose, interpret medical data for clinical advice, recommend treatment, score risk, or issue a legal or treatment order. Our difference is the closed loop from conversation to reviewed version, retrieval, and follow-up.

### Scope confirmation to check

`I confirm our solution stays outside the out-of-scope list above - it does not diagnose, recommend treatment, provide clinical decision support, or give autonomous clinical advice.`

## 4. How you will build it

Form limits: maximum 80 words and 600 characters; minimum 30 characters.

### Paste-ready answer

React 19, TypeScript, Next.js App Router and CSS power the MVP. Browser-local state models clinician/coordinator and patient-scoped drafts; discussed, not discussed and clarification states; source links; physician review; append-only versions; controlled retrieval; outreach; and audit display. Next, we will test source-grounded transcription/structuring on synthetic or anonymised conversations, with physician review before release. Production would add durable storage, access controls, consent, multilingual evaluation, retention rules and hospital integration. No external AI model is live.

## 5. Existing work

Select:

`Yes - we are building on an existing product`

### Existing work declaration

- Local prototype: `prototype/`
- Existing eight-slide deck: `pitch/Continuity_Loop_Pitch_Deck.pptx`
- Current visual deck candidate: `pitch/Continuity_Loop_Product_Visuals.pptx`
- Historical public demo URL: `https://continuity-loop-healthathon.vercel.app`
- Existing workflow: guided clinician conversation, source-linked draft, physician review, acknowledgement, append-only versioning, controlled retrieval, outreach and audit display.
- The current demo uses synthetic data and browser-local state. It has no live AI model, real patient data, production authentication, EHR/ABDM integration, durable clinical database or live messaging.

## 6. Your pitch deck

Form requirement: PDF or PowerPoint, 6-8 slides, maximum 25 MB.

Current candidate:

`submission/Healthathon_Continuity_Loop_Product_Visuals.pptx`

The final upload must be a reviewed 6-8 slide deck that:

- uses the exact Health-a-thon Cancer / Doctor-Care Team / Patient Follow-up & Continuity of Care framing;
- shows the problem, the clinician-controlled workflow, care illustrations, clearly labelled concept diagrams, technical approach, existing work and next validation step;
- labels synthetic data, browser-local state and deterministic demo behavior honestly;
- keeps the assistive, non-diagnostic boundary visible; and
- includes the existing public demo URL and QR code; recheck the live target before submission and add verified final app captures when the teammate's UI build is ready.

## 7. Links and supporting files

The dashboard says these are optional and must be openable without requesting access. We will still prepare them.

### Intended public support set

- Public MVP URL: `https://continuity-loop-healthathon.vercel.app` - recheck immediately before submission.
- Demo video: local 24-second silent slide walkthrough is staged at `submission/demo-video.mp4`; it still needs team approval and a public, access-free hosting URL before it is entered.
- QR code: to point to the final public MVP URL, not to a private GitHub repository.
- Screenshots: synthetic-data workflow captures for the deck and video thumbnail.
- Papers/guidelines: include only publicly linkable, approved sources that the team wants the jury to open.

Do not paste the private GitHub URL into the form unless the repository is deliberately made public or the organisers confirm that access requests are acceptable.

## Evidence and claim boundaries

| Claim | Current status |
| --- | --- |
| A working workflow exists | Demonstrated in the local prototype; live URL requires current recheck |
| The demo uses synthetic/browser-local state | Confirmed by the prototype documentation and source |
| Live AI transcription or summarisation exists | No - not implemented in the current MVP |
| The goals-of-care continuity problem is clinician-sourced | Yes - supported by the 24 August meeting and GOCD proposal |
| Saanthvana ECTPR is clinically approved | Unknown; do not claim approval |
| The product is deployment-ready | No; integration, identity, governance and pilot gates remain |
| The application has been submitted | No |

## Submission checklist

- [ ] Confirm the final team roster, doctor identity, technologist identities and application contact details.
- [ ] Confirm whether the 24 August clinician framework or Saanthvana ECTPR is the source protocol for the demo.
- [ ] Recheck the public MVP URL in Chrome.
- [ ] Capture final desktop and 375-390px mobile screenshots.
- [ ] Review the staged slide walkthrough, decide whether to replace it with a narrated screen recording, and publish the approved video.
- [ ] Generate and test the QR code against the final public URL.
- [ ] Finalise and visually inspect the 6-8 slide deck under 25 MB.
- [ ] Add only public, approved supporting links/files.
- [ ] Review every clinical and legal claim with the clinicians.
- [ ] Submit manually only after the team approves the completed form.

## Related but separate opportunity: TANUH

The supplied IISc/C-CAMP TANUH announcement is captured in `PROJECT_CONTEXT_MAP.md` and `USER_SUPPLIED_CONTEXT_2026-09-13.txt`. TANUH has a separate application context, readiness-stage language and stated 28 September 2026 deadline. Do not copy this Health-a-thon answer sheet into TANUH without obtaining the official TANUH form and adapting the answers.
