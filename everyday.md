# Sahara: everyday work

Our aim is to build a useful palliative-care companion and make the judges understand exactly what we are trying to do. We want to win through a working product, clear evidence and a presentation we can explain confidently.

## Final package we are working towards — organiser email, 27 October

The latest [email and two original templates](source-materials/healthathon-phase-2/2026-10-27-final-deliverables/README.md) are saved. Our end goal is **all four deliverables**, not only the app or the slides:

- [ ] **Working MVP:** verify the complete evaluator journey and provide working links/access instructions. Share any test credentials privately at submission, not in this repo.
- [ ] **Two-page overview:** fill the new two-page template with the team/doctor, problem, solution, 3–5 features, technology, progress, validation, safety, sustainability and supporting links.
- [ ] **Final presentation:** adapt our useful v6 visuals to the new 12-slide source template and fill every placeholder. The solution and evaluation sections each permit up to two slides. Use current evidence; keep incomplete features and proposed CARE integration clear.
- [ ] **Narrated demo video:** introduce the team, problem, development and doctor/technology collaboration, then show the working solution and saved result. A silent screen recording or screenshots alone do not meet the email's instruction.
- [ ] **Final submission check:** test all MVP/video/code-access links as an evaluator, confirm the submission destination and exact cut-off, submit the four-part package and retain the receipt.

The archive and requirements are saved. On 28 Oct, working drafts were filled in both prescribed templates: [two-page brief](submission/phase-2/final-deliverables-2026-10-28/Sahara-Two-Page-Solution-Brief-working-draft.pptx) and [12-slide final presentation](submission/phase-2/final-deliverables-2026-10-28/Sahara-Healthathon-Final-Presentation-working-draft.pptx). They are content-complete drafts for review, not final submissions: missing names, team code, CARE choice, approved measures/baselines, costs and pilot details are called out rather than invented. The narrated real-phone video and evaluator access are still pending. The latest email gives no deadline, time zone, portal or video duration. **7 November remains only a planning target; confirm its source and cut-off before submission.**

## Blocked: things only Basith, Awaiz or the team can do

Moved to **[blockers.md](blockers.md)**: one list for every repo (Sahara, ITI, WDCW, AI-FIR, Raasta Saathi). Check it every day.

## Work on this every day

- **Slides and two-page brief:** finish or improve one section each day using the latest 27 October templates linked above. Reuse useful material from our earlier deck. Fill what we know now; add team photos, biographies and answers needing discussion later. The answer to sharing solution information on the Health-a-thon website is **Yes**. Private records and credentials are excluded.
- **Survey:** improve and test the hospital survey, review responses and turn useful findings into product decisions. Keep it on the web at `/hospital-survery`.
- **Features:** choose a meaningful patient, caregiver or doctor task, finish the flow and test its actual result. Keep Hindi/English access and fewer taps in mind. Record what works and what still needs verification.
- **Palliative-doctor feedback:** identify suitable doctors, prepare a short walkthrough and specific questions, arrange outreach, then record their feedback and the changes it leads to. Send messages only when that outreach is authorised.
- **Play Store:** get the current verified Android release onto Google Play. Check the actual release/review status, finish the remaining device and listing checks, and distinguish an uploaded build from an available release.
- **CARE:** understand its palliative-care workflows and test whether a focused Sahara plugin or API connection would improve our product and our Healthathon entry. Compare what already exists with what we can add. Do not claim an integration before it works.
- **Mail CARE / OHC (team decision, 27 Oct):** we will email OHC (Open Healthcare Network, who make CARE) at support@ohc.network and post in their Slack (slack.ohc.network). Named contacts: Bodhish Thomas (CEO), Vignesh Hari (CTO). Ask for: a pilot with one CARE palliative unit for a family-side add-on (family entries wait in a queue; a nurse accepts each), permission to reuse their starter kit/docs (no licence file), and their view on loading our 3 forms as CARE Questionnaires. Send only after the team agrees the wording. Draft:
  > Hello OHC team, we are Team Sahara (Health-a-thon 2026). Sahara is a phone app for palliative patients' families: symptom diary, medicines, and the family confirming the exact goals-of-care note a doctor reviewed. CARE is the staff side and, as your docs note, has no family side. Could we pilot a small family-inbox plugin with one CARE palliative unit, where family entries wait for a nurse to accept them? We have drafted our symptom diary, pain areas and goals-of-care questions as CARE Questionnaires and would value your review. Sources and details: [repo link]. Thank you.
- **Video:** prepare and record a short demonstration of the actual patient/family/doctor journey. Show the problem, the important actions and the saved result. Keep the video consistent with the build and slides.

## What is genuinely left (27 Oct → 7 Nov)

Full list with owners: `HANDOFF.md` → "27 Oct 2026 — open items". The short version:

**Must do before submission (planning target: 7 Nov; exact cut-off to confirm)**
1. Decide CARE option (A / A+B / A+C) in the team meeting; write the deployment/integration answer (slide 10 in the unexpanded new template). Brief: `docs/presentations/Sahara_CARE_Team_Briefing_2026-10-27.pdf`.
2. Fill both the new final deck and the two-page overview: team code, doctor partner details, costs, 2–3 measures of success with a baseline.
3. Record the narrated demo video (conversation → reviewed note → family confirms).
4. Test the app on a real phone (checklist: `CLAUDE_TRANSCRIPT.md` §3). Nothing from 26–27 Oct has been tried on a device.
5. Get clinician sign-off (Sujay, Shirin, Sharada): goals-of-care note format, symptom list and scales, and the source of the 8 default goals-of-care questions.
6. Agree and send the OHC email (draft above). Mention the pilot sites and the ccm_uhi privacy issue privately.

**Decide (owner)**
- Anonymising before data reaches the doctor: scope (outside reviewers / AI only, or the treating doctor too).
- Real transcription/AI provider and consent terms, or keep "word matching" and say so.
- Emergency QR: offline card only, or a server link later.
- Hindi across the whole app; re-tap behaviour on the pain body.
- How judges see the code (repo is private).

**Set up (owner)**
- Real doctor account + Supabase email setting; Supabase security warnings.
- Website redeploy with the app-link fingerprint so invite links open the app.
- Next Play upload with a higher versionCode (don't touch the build in review).
- DPDP: consent notice naming the server and doctor, withdraw/delete, named data fiduciary.

**Team / clinicians**
- Send the hospital survey to oncology and palliative groups; name a pilot centre and doctor contact.
- 20 practice conversations (asked twice, none done).
- Support-group, palliative-doctor and morphine-centre lists (Sujay).

## Main thing: how we present it

Make the idea understandable to someone hearing it for the first time—as simply as explaining it to a five-year-old—while being precise enough to show we know the clinical problem and our product.

1. Who is struggling, and what happens to them today?
2. What does Sahara help them do?
3. Show one clear journey, from the conversation to the reviewed record and its next use.
4. Explain what AI does, what the clinician decides and what the patient/family confirms.
5. Explain how we differ from existing tools, including CARE, without pretending their features do not exist.
6. Show what we have built and tested, what feedback changed, and what comes next.

Rehearse aloud. Remove jargon and sentences we cannot explain naturally. Practise likely judge questions, transitions between teammates and recovery if the live demo fails. The slides, video and spoken explanation should tell the same story.

**Visual priority after comparing the decks:** keep the patient story, large app screens, care map and evidence visuals when adapting to the latest 27 October template. The earlier v6 draft follows the older 13-section source and is a useful visual reference, not the completed new final deck. The user rejected the report-like first revision; preserve v6's large product scenes and concise copy. Keep team photos/write-ups for the agreed later discussion.

## End each work session

- Record the slide worked on and the concrete survey/feature progress.
- Save evidence of meaningful checks and feedback; do not invent validation or outcomes.
- Note the next action and its owner once agreed with the team.
- Update this file and the current handoff without scattering decisions across new notes.

| Date | Slide | Survey / feature work | Doctor feedback | CARE / Play / video | Next action |
| --- | --- | --- | --- | --- | --- |
| 28 Oct 2026 | Drafts filled in the exact two-page and 12-slide organiser templates; renders checked | No code or survey changes in this checkpoint | Names/roles, CARE scope, measures and pilot details remain for team/clinician approval | Live root, survey routes and Play listing checked; `/join` returns 404; no new build, deployment, submission or email | Team approves missing fields; Awaiz/owner restore evaluator access and record the narrated phone demo; confirm deadline/portal |
| 27 Oct 2026, final-deliverables email | Both original templates archived; 2-page brief and new 12-slide source read and checked | No app or survey changes in that archive task | Evidence and collaboration must appear in both documents and video | Four-part final package added above; no submission, release or external message made | Complete the two-page brief, adapt v6, record narrated video and verify evaluator access |
| 23 Oct 2026 | Official template downloaded; first 13-slide draft filled, including the team table and Yes to public solution information | Review current evidence before choosing the next task | Outreach and feedback to arrange | CARE website reviewed; integration not built. No new Play release submitted in this session | Review the draft with the team, fill the remaining details and rehearse the opening explanation |
| 23 Oct 2026, follow-up | Product-led v6 revision across13 sections, rendered and checked | Survey changes and manual hospital entry live on Vercel; one actual390px submission/receipt verified and exact synthetic data removed.299 unit checks pass. Personal diary keeps separate ratings, respondent/time frame and real comparisons | Exact WhatsApp feedback saved; S S Sparsh entry received and pending review; clinical symptom report prepared for team discussion | No Android/Play change, patient onboarding or messages sent | Review the visual draft, choose clinical questionnaire with Shirin/Sujay, then rehearse one care-note journey |
| 27 Oct 2026 | Team briefing deck (9 slides, CARE vs Sahara) | Screen fixes running (doctor conversation, emergency QR, Nearby, Docs/Lab, Hindi, strength bar) | — | CARE docs (all 473 pages) and OHC repos read; mail OHC agreed (draft above, not sent) | Agree the OHC email wording, then send it; decide CARE option A/A+B/A+C in the meeting |

Current slide draft and remaining questions: [`submission/phase-2/README.md`](submission/phase-2/README.md).
