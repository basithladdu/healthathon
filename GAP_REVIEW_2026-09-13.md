# Current gaps and fixes - 13 September 2026

Current source is outer-repository main at `2fb5ebb67bc13ccea4f93dccb32eea578c5dd0f3` plus the uncommitted local changes below. No push, deployment or submission was performed. Earlier handoff documents describe the August build.

## Fixed locally

| Gap | Change | Verification |
| --- | --- | --- |
| Family access accepted unknown IDs and showed Meera's content for other patients | Reject unknown IDs; bind the summary and next touchpoint to the selected patient; distinguish unloaded and unreleased summaries | Chrome: unknown ID rejected; normalized Leela ID showed Leela's scheduled call and unloaded-summary notice; Farah showed no released summary; neither showed Meera's content |
| Editing a draft preserved earlier review and acknowledgement | Text and field-status changes clear review checks, acknowledgement and attestation; changed text requires clarification | Reproduced before fix; Chrome confirmed publication changed from enabled to blocked, all five confirmations cleared and acknowledgement reset; regression tests pass |
| Editing after publication silently changed the released plan | Copy approved fields into an immutable release snapshot; block subsequent edits | Reproduced before fix; Chrome confirmed five read-only fields and disabled status actions; patient and mobile family views showed the released Version 2; mutation regression tests pass |
| UI implied later revisions were implemented | State the actual Version 1 to Version 2 limit | Source and rendered release notice checked |
| Invalid birth dates could be saved | Validate actual calendar dates, leap years and future dates | Chrome reproduced acceptance of 31/02/1964 before the fix and rejection afterward; calendar regression tests pass |
| A patient review request could downgrade an escalation | Preserve Escalated status when recording the request | Source guard reviewed; this specific cross-role sequence was not exercised in Chrome |
| Entry screen repeated sign-in actions and long marketing sections | Three workspace choices, one direct clinician-demo button, account creation, expandable workflow/roles/limits | Chrome desktop 1536 x 730: page height 730, no overflow. Mobile 390 x 844: page height 1110, no horizontal overflow, 44px action buttons. All six workflow steps remain accessible |

The earlier map, consent, appointment-dialog and QR fixes are recorded in `PULL_REVIEW_2026-09-13.md`.

## Remaining gaps, in priority order

| Priority | Gap | Next concrete step |
| --- | --- | --- |
| Release | Public demo is older than the current local app | Publish the reviewed build when deployment is requested, then recheck the deck QR target. Chrome opened the public URL on 13 September: it had the older clinician landing page, no appointments navigation, and a notice that an independent patient portal was absent |
| Clinical validation | Only Meera has a complete source-linked narrative; the planned 20-scenario review has not been run | Clinical lead supplies the reference scenarios, approved conversation domains and acknowledgement rules; test against those references |
| Core workflow | Only one new release is supported; no Version 3 or later conversation | Add an explicit new-conversation/revision workflow when extending the prototype, preserving all earlier releases |
| Consistency | Patient-entered demographics are held separately from the clinician's fixture profile | Define the review and reconciliation rule before treating patient edits as clinician-approved data |
| Consistency | Narrative summary and ECTPR quick view have separate fixtures and version labels | Define their relationship with the clinical lead. Do not manufacture an ECTPR signature or treatment preference from a narrative release |
| Demo presentation | Family journey includes the older illustrative Mumbai support map; nearby-care search uses a separate live directory | Choose one clear resource-discovery path in the next design pass; seeded counts remain labelled unverified |
| Submission | The 24-second silent video shows the older deck; no public video URL is staged | Record the compact current workflow and host a reviewed video before adding its link to the form |
| Production scope | Authentication, durable records, live AI, messaging, hospital integration and independently verified care-directory data are absent | Treat these as implementation and validation work; the current app remains a synthetic browser-memory demo |

## Checks and submission assets

- `npm test`: 9 passed, 0 failed.
- ESLint: 0 errors, 5 existing unused-code warnings.
- Next production build, including TypeScript: passed.
- Vinext production build: passed; its existing informational route-classification limitation remains.
- `git diff --check`: passed.
- Current deck: `submission/Healthathon_Continuity_Loop_Product_Visuals.pptx`, 8 slides, 8,395,758 bytes. The care illustrations and actual product screenshots remain.
- Paste-ready answers currently measure 121 words / 870 characters; 145 / 1,053; and 73 / 597. All meet the supplied limits; the build answer has only 3 characters of spare capacity.
- Screenshots: `tmp/gap-review/minimal-entry-desktop.png` and `tmp/gap-review/minimal-entry-mobile.png`.
- The current Next production build is running locally at `http://127.0.0.1:4817/`. Chrome confirmed the direct clinician demo, care-team sign-in and account-registration routes; the entry screen is left open for review.
- Native PowerPoint playback, QR scanning, physical printing, real clinical use and production integration remain unverified.

Confidence is high for reproduced local defects and the observed public/local mismatch. No clinical effectiveness or production readiness is established by these checks.
