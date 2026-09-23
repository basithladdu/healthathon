# 23 September meeting evidence for the pitch

**Sources read:** the complete 779-line transcript `C:/Users/basit/.codex/attachments/322db338-d7fd-440d-a8b1-0cf99da52c19/Pasted text.txt`, `docs/meetings/2026-09-23-chat-follow-up.md`, and the current eight-slide wording in `submission/Saanthvana_Round_1_Slide_Text_2026-09-24.md`. Transcript references below are source line numbers. The transcript's opening “Source guide” (L3–5) is editorial summary text, not a participant statement; it should not be cited as independent evidence.

## What the meeting settled

- **Entry and audience:** cancer, patient/caregiver, family and caregiver support. The group says this fits better than patient follow-up/continuity because the latter reads as document aggregation (transcript L39–59). Keep the Health-a-thon entry as Cancer → Patient / Caregiver → Family & Caregiver Support; the other team's recruitment message is context only (follow-up, “Submission”).
- **Problem first:** judges need to understand the unconventional problem before the product. Participants prioritize the problem statement and a visually clear first two slides; they discuss a 6–8 slide deck and agree to eight (L75–107). They want authentic dashboard/app captures among the first two or three slides (L181–213). The follow-up specifies a revised family story, opening with **best supportive care**, removing the death outcome, and avoiding any implication that one emergency intervention is inherently wrong.
- **Core product:** a physician-led, AI-guided goals-of-care discussion with source-linked notes. Explain patient values, family understanding, clinical context, physician review, version history, and context from previous discussions in plain language (L123–167, L171–177). The patient or family may request a conversation; the doctor can also initiate it (L15–21). The follow-up tightens the release boundary: conversation capture → source-linked draft → doctor corrects and signs → patient chooses family access.
- **Product framing:** present a palliative-care companion *centered on* goals-of-care discussion, with reports, reminders, journals, support groups and location-based resources as supporting functions (L269–277, L689–743; follow-up, “Decisions”). Do not let the secondary features obscure the wedge.
- **Current vs future must be explicit:** the transcript discusses an actual prototype, a simulated-conversation demo, and several unbuilt integrations. The follow-up requires solution/build answers to distinguish what exists from what is planned. Current pitch wording says AI integration and separate-account sharing remain future work (slide 4 and slide 7); retain that distinction.
- **Closing line:** the follow-up supplies “The hardest decisions shouldn't be made without the right information.” as the closing line.

## Numerical and research claims

The meeting transcript supplies no full citations, study titles, dates, denominators or populations for these figures. Treat them as **claims mentioned in discussion, pending source verification**, not established facts. The “Source guide” at transcript L5 repeats the 98.3% claim but is not a citation.

| Claim mentioned | Evidence in transcript | Classification and pitch handling |
|---|---|---|
| **98.3%** of Indians lack access to palliative care | L591–600: speaker calls it an important statistic and says it came from “real studies”; L607–619 discusses using it on the opening slides. No study or definition is named. | Cited-research claim, **unverified here**. Do not present as settled until the actual primary source, year, population and meaning of “access” are checked. |
| Goals-of-care documentation went from **0% to 92%** after an SOP | L617–629: speaker recalls a study in Chennai, attributes it uncertainly (“I don't know which… Swami”), reports 0% at baseline and 92% after an SOP. | Anecdotal recollection of a study, **unverified**. The reported improvement is associated with an SOP, not evidence that this app caused it. Verify the paper, setting, denominator, measure and intervention before including. |
| **11% / 11.5%** of doctors know what advance directives are | L647–683: the participant describes the slide statistic as “11%,” then calls it “11.5”; asks to keep it and its references. The underlying source is absent from this transcript. | Conflicting spoken precision plus an uncaptured reference. Verify the original source and exact measure before use. |
| **15.6%** and **3.3%** | L591–617 and L673–679 discuss removing other figures; ASR is garbled around “3.3” (L613–617). | Do not reproduce. The transcript does not establish what either figure measures. |
| **60–90 days** | Not proposed in this transcript. | This range appears in the current slide-text draft (slide 8), not in the meeting evidence reviewed here; do not attribute it to this meeting. |

**Recommended deck choice:** lead with the agreed human problem and current app evidence. If the statistics are retained, cite the actual primary research on-slide or in speaker notes and state its scope precisely. An uncited 98.3% headline risks a judge asking what “access” means and which population/year was measured.

## Main AI workflow and clinician effort

The meeting describes the core as physician-led and AI-guided, with source-linked notes (L123–155). The intended sequence, clarified by the follow-up, is:

1. Patient or family asks for a goals-of-care conversation; doctor may also initiate it (L15–21).
2. With permission, capture the conversation or use the doctor's existing notes.
3. AI prepares a source-linked draft that preserves what was said, prior discussion context and unresolved questions; it does not decide care (L139–167, L241–261).
4. Doctor reviews, corrects and signs before release (L127–135; follow-up, “Decisions”).
5. Patient chooses whether family can access the signed note; later conversations create a new version while earlier ones remain available (follow-up, “Decisions”; transcript L135–155).

The problem framing includes limited doctor time or knowledge to do the work (L7–11), but the transcript gives **no measured minutes saved, workload baseline, or clinician-effort result**. State the workflow and the review burden honestly; do not claim time savings. Current slide 8 poses doctor time as a question to test and says results do not exist.

The transcript briefly considers recording/transcription and simulated multilingual demonstrations (L427–439, L757–779). This is a demo/testing idea, not evidence of production speech AI, clinical accuracy, language coverage or validated transcription. The follow-up explicitly lists speech/transcript AI and reviewed report extraction as work still requiring real service/verification.

## Patient, family and care-circle roles

- Patients should understand the purpose of a goals-of-care conversation and be able to request one; clinician initiation also remains possible (L15–21).
- The conversation records patient values and what remains to discuss; do not suggest that a family member's preference replaces the patient's (L131–167). Family access is patient-controlled per the follow-up.
- Family distress and difficulty articulating the patient's wishes are proposed problem points for slide 2 (L555–583). These are the team's framing, not measured prevalence.
- For care tasks, name the recipient, let that person accept and complete, and show status to the sender. A ride task does not book an ambulance or taxi (follow-up, “Decisions”). The current slide 6 makes this a named family member workflow and discloses that cross-device updates still need connecting.
- The discussion names family support, documents, medicines/reminders, journals, support groups and care-location discovery as supporting features (L181–213, L269–277, L689–743). A stated plan to list oral-morphine dispensing institutions is not a verified directory or availability promise (L691–715; follow-up, “Still needs a real service or verified data”).

## Technical, platform and pricing ideas versus commitments

The transcript contains a mixture of proposals and corrections. Do not turn this brainstorm into a shipped-capability slide:

- **Native iOS/Android:** Swift and Kotlin are discussed as possible native implementations, not a commitment (L281–303). The speaker then says this section is too technical and considers removing it. The follow-up calls for revising build answers to distinguish what exists from what is planned.
- **Backend/infrastructure:** Node API, AWS, India data residency, Postgres/audit trail, doctor registration, FHIR/HL7 and WhatsApp/SMS are discussed with confusion over what is current versus future (L305–345). In particular, the conversation itself says some messaging and standards items are future plans; it does not establish deployed integrations or hosting/data-residency guarantees.
- **Offline-first:** floated and immediately questioned/reworded as an idea (L285–303); no implementation commitment established here.
- **Pricing:** subscription is suggested, concerns about uptake are raised, one-time purchase is considered, and a government/National Cancer Grid route is imagined (L441–519). No price, payer, business model or institutional adoption is decided. The current follow-up does not make pricing a committed pitch requirement. Omit a definitive pricing promise; if asked, present these as open models.
- **ASHAs:** briefly considered and then set aside in favor of removing the ASHA-specific line (L281–285). Do not claim an ASHA workflow is part of the agreed product scope.
- **Oral morphine access:** the team wants location support, but the list is not centralized and completeness varies by state; finding and collecting it is future work (L691–715). Follow-up says a verified list and current availability still require real data.

## What the current eight-slide wording still leaves out or needs to keep bounded

Compared with the meeting and the follow-up, the current wording in `submission/Saanthvana_Round_1_Slide_Text_2026-09-24.md` does well on the best-supportive-care story, conversation flow, doctor review, patient-controlled access, supporting features and current/future boundary (slides 2–7). Remaining gaps or review points:

- **Source evidence for any headline number:** the text currently omits the discussed 98.3%, 0→92% and 11.5% figures. That omission is appropriate until sources are verified; adding any requires source citation and precise scope.
- **Authentic app evidence early:** meeting asks for real dashboard/product screenshots in the first two or three slides (L181–213). The slide text alone cannot confirm placement or whether the PPT uses authentic captures; verify the rendered deck separately.
- **Problem specificity:** the opening currently says families cannot find the prior conversation (slides 1–2); the meeting asks the judge to grasp why this is an unconventional problem (L87–105). Preserve a clear patient-wishes/earlier-conversation gap and family perspective without framing any emergency choice as inherently wrong.
- **Doctor workload:** no measured effort or baseline is available. Slide 8 correctly asks whether doctors spend less time and states there are no results; keep it as a question, not an outcome.
- **Boundary and consent:** ensure visuals do not imply AI is already connected, that identities are verified, that notes are a legal advance directive, or that cross-device sharing is live. Follow-up expressly marks speech AI, reviewed extraction, verified accounts/consent, cross-device delivery, oral-morphine data and emergency discovery as unresolved.
- **Care-location and transport claims:** “call to confirm” in slide 5 is appropriately cautious. Do not turn care-centre markers into a claim of capacity, availability or emergency dispatch. A task assignment is not a ride booking.
- **Evaluation:** slide 8's 60–90-day plan is in the current draft only, not the meeting transcript reviewed here. Treat it as a proposed evaluation plan and ensure the team has an owner and feasible protocol before presenting it as a commitment.
- **Story provenance:** current slide 2 is explicitly an illustrative scenario per the slide notes, not a reported case. The follow-up says to use Shirin's revised story and omit the prior death outcome; keep names/details fictionalized and avoid publishing private case details.

## Likely judge objections to prepare for

These are anticipated questions based on the meeting and current claims, **not questions quoted from judges**:

1. What precisely is the patient/family problem, and why does a shared goals-of-care note solve it better than ordinary records or a clinician's existing workflow?
2. What does AI do, what source does each statement link to, and how does the doctor catch omissions or transcription errors before anything reaches family?
3. How much clinician time does this require, and what evidence would show the workflow saves time rather than adding documentation work?
4. What exists in the prototype today versus a planned AI, verified identity, separate-account consent, multilingual speech, and cross-device sharing?
5. Who controls family access, what happens when a patient changes that choice, and is the note being represented as a legal advance directive or an automatic instruction? (It must not be.)
6. What supports the 98.3%, 0→92% and 11.5% figures? What exactly is their population, denominator, date and outcome?
7. How current and complete is the oral-morphine institution data? Can a family rely on a listed facility being open or dispensing today? The meeting says the list is not centralized and availability is not verified.
8. Does “Find support” guarantee a service, an ambulance or a bed? The follow-up says markers do not prove capacity, availability or dispatch.
9. Who pays, and is the product patient-paid, one-time, subscription-based or institutionally supported? The meeting leaves this open.
10. How will the team validate this with patients, families and clinicians without presenting simulated conversations as clinical evidence?

## Deck-making direction recorded in the meeting

Keep the pitch to 6–8 slides (the team leans to eight), make the first two slides explain the problem, use real app captures early, and combine complementary content rather than shrinking dense text (transcript L75–107, L181–213, L537–583; follow-up, “Submission”). Remove old/synthetic example material from the live product-story slide where it could be mistaken for evidence (L175–181, L403–435); simulated conversations may be identified only as a planned demo/evaluation input, not real patient outcomes. Preserve the agreed entry and closing line.
