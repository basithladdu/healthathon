# Saanthvana local verification

Checked 20 September 2026. Repository baseline: `58c9ff0`. Changes remain local and uncommitted. This report includes the latest direct-entry simplification; earlier captures of the landing page and tabbed daily-care screen are historical.

## Code checks

- `npm.cmd test`: **23/23 passed**. Coverage includes patient-scoped tasks, real date validation, exact source copying, permission/review gates and immutable prior note versions.
- `npm.cmd run build:next`: **passed**, including TypeScript and static generation for `/` and `/care`.
- Targeted ESLint: **0 errors, 5 existing unused-declaration warnings** in the large prototype file. New components and state helpers have no lint warnings.
- `git diff --check`: **passed** after the source and documentation updates. Windows line-ending conversion notices remain informational.

The new short-review tests initially caught source excerpts being cleared by the existing source-change invalidation logic. The adapter now sets the source first and applies the explicit excerpt/status links afterward. The original summary guards remain intact, and all tests pass.

## Final Chrome workflow checks

The production build ran at `http://127.0.0.1:3042`. Checks used the connected real Chrome profile, not a terminal browser.

| Workflow | Observed result |
| --- | --- |
| Direct entry | Reload opens the family's care screen immediately. No landing page, fake sign-in or role-demo toolbar in the main path. |
| Tasks | Type in the visible task field and press Enter; a task appears without opening a form. One checkbox completes it; completed items can be reopened. Name/date/category are optional extra controls. |
| Questions | Add a question on the home screen. English and Hindi input survived in-session navigation. Questions do not change the approved note. |
| Preparation | One open note is visible; additional prompts are optional. Notes remain keyed by patient, author and role. |
| Doctor review | An existing source and family copy appear on one page. Approve stays disabled when permission is unconfirmed, even after checking review. Explicit permission plus review enables approval. |
| Existing reviewed draft | Approved Meera's sample draft to Version 2. Prior version preservation is also covered by the automated tests. |
| Reuse a note | Switched to Farah, whose source was blank. Pasted a short synthetic note and used it. The family copy kept the exact text and inferred no clinical choices. |
| Correct family after approval | Approved Farah's Version 1 and opened family view. The displayed note belonged to Farah. The role transition retains the selected patient. |
| Downloads | The generated family checklist was read from disk. It includes tasks/questions, owners, dates and completion attribution, and identifies itself as personal coordination notes. Preparation download was also inspected earlier. |
| Support | One navigation action opens the selected provider directory. Combined location/service filters, no-results and clear-filters were checked earlier; the final shell still renders the directory without horizontal overflow. |
| Reload | Reload clears session-only changes and restores the sample case, as described once in the footer. |

No provider was called, no message sent, no location permission requested and no private medical data entered into an external site.

## Responsive and visual review

- Final family home: **1440 × 900 CSS pixels**, document height **900**, no horizontal overflow and no clipped content.
- Family home, care note and directory: **390 × 844 CSS pixels**, no horizontal overflow. Mobile home scrolls naturally; its initial content height was 1172 CSS pixels.
- Visible home buttons, selects and non-checkbox inputs were at least 44 CSS pixels high. Checkbox rows provide larger labelled touch areas.
- Hindi labels wrap without horizontal overflow. Clinical notes and entered text retain their original language. Localisation is partial: the new family home, care note and preparation support Hindi; the directory and doctor review remain English.
- Doctor review was inspected at desktop and exercised at mobile. Approval and exact-note reuse worked with the same source/version guards.
- After the final build, the named care-note heading and task reopening were checked again at 390px. The temporary viewport override was reset, and the direct-entry family home was left open in Chrome.
- Chrome's error log contained extension-origin QuickPro preference errors; no application error appeared in the inspected log.

Final captures in `submission/screenshots/2026-09-20/`:

- `simple-family-home-desktop.png`
- `simple-family-home-mobile.png`
- `simple-family-hindi-mobile.png`
- `simple-doctor-review-desktop.png`

Some background captures timed out earlier; bringing the preview tab to the foreground resolved this. Chrome's full-page image export at the user's zoom cropped a Hindi capture; it was replaced with a normal viewport capture.

## Sources and delivery

Both Claude JSONLs parsed fully. Source JSONLs, the ZIP and WhatsApp text were copied into the Git-ignored private-context folder and hash-checked. The group export ends at 1:43:36 pm on 20 September; the later Shirin messages are separate user-pasted material. Referenced voice-note audio was not supplied.

Original clinician PowerPoints and PDFs remain unchanged. An eight-slide draft was exported earlier and passed package/layout checks. It predates the latest product simplification and has not been finalised for submission. Final deck work is deferred while the team settles the direction; the earlier disk-full problem no longer blocks work.

The competition portal was read only. No portal edits, invites, uploads, submission, commit, push or deployment occurred. The public website remains the earlier build.

## Limits

The working app uses sample patients and session-only state. A view selector is not hospital authentication. There is no live AI transcription, EHR connection, family sync or notification service. Existing specialist experiments remain in the full workspace, outside the simplified default flow; they have not been clinically validated. This verification establishes software behaviour, not patient outcomes, legal validity, reduced staff time or commercial demand.
