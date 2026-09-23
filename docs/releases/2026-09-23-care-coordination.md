# Care coordination update — 23 September 2026

This update keeps the goals-of-care conversation central and adds practical family coordination around it. It follows Basith's later request to expand the earlier meeting scope. The original meeting transcript and minutes are unchanged.

## Delivered in this batch

- Animated, prefilled patient/family ABHA-style entry and doctor registration/council entry. Sign in remains a deliberate click; reduced motion and user edits are respected.
- A care circle with named people, chosen update recipients, read acknowledgement, patient-controlled interface permissions, revocation and an action history.
- Named family tasks: quick suggestions, dates, edit, complete, remove and undo. Completed questions are separate from completed practical tasks.
- A direct next-visit brief groups the recorded appointment, preparation instructions, documents, questions and named help. It reuses the same calendar and task records.
- A dedicated medicines page: select a prescription or paste its text, review the written instructions and time, confirm the source, then use Taken/Undo on the corresponding calendar date.
- Reports & answers: up to ten PDF/image/text files in a review queue, original preview, suggested folders, checked text/date, literal source retrieval with page/line references, missing-answer state, remove and undo.
- Four actual PDFs for the existing fictional Meera account. Their searchable passages use the same text as their originals; they never stand in for another uploaded document. A matching next visit appears on the home/calendar.
- Prepare care note organises the supplied conversation text into the five existing sections without a model connection; editing and doctor review/signing follow it. Uploaded audio does not receive an invented transcript.
- Costs & support is restored with expenses, paid/pending status, edit/remove/undo, export and optional assistance paperwork. Fundraiser controls and payment collection are absent; older stored fundraising records are preserved.
- Public support discovery by chosen patient, caregiver, advanced-cancer or after-treatment audience, plus India/Online and the existing condition filter. External destinations receive no journal, report or patient data through the links.
- Legacy clinical-tool URLs resolve to supported care screens. The old clinical views are blocked by the route gate; old source code remains for a separate cleanup.

## Evidence behind the direction

- [Truly Left](../../Truly%20Left.md) now contains the product call, current gaps, brief mapping and a distinct research-derived requirements section.
- [Patient/caregiver source ledger](../research/2026-09-23-india-caregiver-experiences.md): 15 directly opened discussions, 12 Reddit and three UK-hosted Macmillan; exact access limits and date uncertainty retained.
- [Additional organisation discussion](../research/2026-09-23-organisation-follow-up.md): a separate thread about planners, records and paperwork.

No patients were contacted, no posts were made and no interviews were conducted. These are public accounts that inform design hypotheses, not prevalence estimates or validated clinical evidence.

## Verification

- 51 focused Node checks passed across routes, care-circle permissions/audiences, source retrieval and file removal/undo, prescription review and calendar state, conversation organisation, family tasks and costs.
- Source TypeScript check passed. The full local check encounters an older generated `.next/types/validator.ts` reference to the removed `app/page.js`; a fresh production build is the release gate.
- Focused lint passed on new components/state modules. The main file and conversation audio wrapper retain pre-existing `set-state-in-effect` diagnostics; unrelated lint cleanup was not included.
- Isolated local browser check at 1440×960 and 390×844: prefilled sign-in opens `/home`; Meera's four documents survive route reload; the next-visit question returns the matching PDF passage; turning off family care-note access blocks the direct `/care-note` path; doctor note preparation, explicit review and signing produce a versioned care note.
- Mobile document and access-denial screens measured 390px content width at a 390px viewport. Screenshots are kept under `output/playwright/2026-09-23-*` locally.
- The native Computer Use and in-app browser bridges failed to initialise (`failed to write kernel assets`). The browser evidence above comes from a separate Playwright session, not the user's logged-in Chrome session.
- Basith then asked to stop checks and continue writing code. Further tests and browser checks stopped. The next-visit brief and its home entry were added after that request; they have not had a browser walkthrough. Vercel's required deployment build is separate from additional local checks.

## Boundaries still open

The app stores care data in the browser. Role switching and entered identifiers are not authentication, ABHA verification or medical-register verification. Interface permissions need a secure backend before real multi-user use. No external invitations, message delivery, notification service, real model provider, scanned-image OCR or uploaded-audio transcription service was connected.

Document categories are filename suggestions until reviewed. Source search returns saved words; it does not interpret results, choose treatment or answer from missing content. A medicine retains its reviewed excerpt if its original file is later removed. Image upload preserves the original and allows text entry; it does not claim to read image contents.

Hosted community membership/moderation, cross-device sharing, real-user validation and measured operational impact remain in Truly Left. No clinical-readiness or impact claim follows from these checks.

## Release

Commit, production build and live URL verification will be recorded here after deployment completes.
