# Saanthvana — local implementation status, 21 September 2026

This is the current local-app status for **Cancer → Patient / Caregiver → Family & Caregiver Support**. It supersedes older “session only,” “calendar only” and “no sign-in” descriptions. The [meeting card](MEETING_BRIEF_2026-09-21.md) gives the plain feature walkthrough.

## What was checked

“Chrome checked” below means the coordinating task exercised those actions in real Chrome. This document was prepared from that report and the current source; writing it did not repeat those checks. “Code connected” means implemented and reachable locally, with the named browser check still outstanding.

| Area | Current evidence |
| --- | --- |
| Short sign-in → family Home | **Chrome checked.** Local entry flow; not cloud identity or access control. The marketing landing page is removed from this path. |
| One calendar | **Chrome checked:** earlier calendar interactions and the rendered updated Day/Week controls. Dated medicines, appointments, tests, procedures, follow-ups and family tasks are connected. **Latest code connected:** week agenda and edit/save/cancel, preserving separate occurrence checks and removing incompatible checks after a schedule/content edit. These final actions were not retested after the user asked to stop testing. No real reminder or hospital booking is claimed. |
| Visit instructions | **Chrome checked:** a deliberately labelled sample instruction and source saved in the doctor view appeared unchanged in the family calendar, with provenance. Editing/removal/undo exist in code but were not all exercised. |
| Original reports | **Chrome checked:** two reports added and compared as original files; both small originals loaded after the reported reload/hot-reload checks. Large files and every file format are not established by that observation. |
| Symptom diary | **Chrome checked:** add, edit, remove, undo and persistence after reload. Observations are family-entered; no clinical interpretation or automatic care-team alert. |
| Care story | **Chrome checked:** story edits. Date, source, treatment-change notes, filtering and download exist in code; not every path was re-exercised. |
| Take to the next doctor | **Chrome checked:** text export. Contact editing, inclusion controls and browser-print path are implemented; print output was not confirmed in this pass. The approved note's wording, doctor and version are preserved; family material is separate. |
| Find support / Places I've checked | **Code connected.** Dated public directory, phone/directions/source links, shortlist, dated call notes, next steps, done/reopen, edit/remove/undo and download. Current availability and completed calls are not verified. |
| Help at home | **Code connected.** Helper/contact/date, arrange/done/reopen, edit/remove/undo and download. No service booking is made. |
| Reviewed care note and history | **Code connected.** Reuse/review/release workflow, actual released versions, comparison and download. Earlier checks are recorded in their dated reports; this update does not claim a fresh full review-cycle check. |
| Doctor's worklist | **Chrome checked:** snapshot showed 4 sample people, 4 notes to review, 3 upcoming visits and 0 currently approved notes. Today filter worked; opening Meera entered note review. Counts derive from local records. Search and a complete new approval cycle were not exercised in this pass. |
| Who has the latest copy? | **Code connected.** Record actual version reportedly given, person/place and date; compare against latest release; edit/remove/undo and export history. No remote delivery, read receipt or revocation. |
| Things we still need to ask | **Code connected.** Ask/reply with person and date, follow-up, close/reopen, edit/remove/undo and download. Replies are notes saved by the family. |
| My comfort space | **Chrome checked:** a “Peaceful” check-in saved for the selected person. History, remove/undo, download and diary link exist in code; full workflow check pending. |
| My journal | **Chrome checked:** “Record a thought” opens it. **Code connected:** user-initiated MediaRecorder start/stop/cancel, about 90-second limit, playback, original audio download, text fallback, edit/remove/undo and save-status feedback. **Real microphone capture, playback and recording persistence have not been exercised.** |
| Cancer overview | **Chrome checked:** route opened with the synthetic patient's recorded diagnosis/team, 1 care-story entry, 1 diary entry and 2 report files. Its links to the underlying tools are connected; not every link was exercised. |
| Help with costs | **Code connected.** Expenses and paid/unpaid totals, existing fundraiser links, paperwork, edit/remove/undo and downloads. Browser check pending. No payments are processed or fundraiser created. |
| Per-test lab history | **Code connected.** My test results and the report-section shortcut open dated, manually copied values with units, printed ranges and flags. Same-test/exact-unit numeric plots, original-report links, edit/remove/undo/export are implemented. Browser workflow not exercised after the request to stop tests. No automatic extraction or result interpretation is claimed. |

The final visual pass added 11 self-contained SVG illustrations, stronger terracotta/marigold/sage/lavender/blue cards, and hover/press motion respecting reduced motion. The coordinator viewed the calendar at 375px (document width 375px) and the illustrated cancer overview on mobile and desktop. Temporary browser emulation was reset. Latest screenshots are under `tmp/ui-2026-09-21/`; the Chrome preview remains open. No Lottie package was added.

The warm cream/sage/lavender/terracotta styling and English/Hindi controls are in the app. Complete desktop and 375–390px checks across every new tool, along with language review, remain outstanding.

## What “saved” means here

The app uses `prototype/app/care-local-store.ts`: IndexedDB storage with a localStorage fallback, including restoration of saved original `File` objects. The inspected Chrome profile could not open IndexedDB and is using the fallback. The symptom reload and small-report reload/hot-reload observations above establish those specific paths, not a storage guarantee for every device or recording.

The UI reports saving, saved or unavailable. Reports and the voice journal tell the person to download files if saving fails. In-memory playback/download can remain available even if persistent storage fails. Large audio can exceed localStorage capacity. There is no cloud backup, cross-device sync or real permission enforcement; patient-scoped UI state is not an access-control system. Sample cases remain in use.

## Work not claimed as complete

- No cloud authentication or shared family accounts; no EHR/hospital connection.
- No AI transcription, report interpretation, prescription extraction, clinical scoring or treatment recommendation.
- No real notifications, messages, calls, appointment bookings or payments made by this work.
- No current portal submission, deck upload, push or deployment from this feature update.
- **No build after the user asked to stop builds.** The coordinating task ran one TypeScript `noEmit` check successfully after the doctor/cost/overview integration, before the ongoing lab-history and calendar edit/week additions. This is not a production build or proof of those later additions. The documentation task did not run app checks.
- No family/clinician pilot, outcome evidence or willingness-to-pay evidence yet.

## Source handoff

The [deck audit](FEATURES_FROM_DECKS_2026-09-21.md) covers all 11 PPT text exports, including duplicates and unrelated SANCHAY files, plus Sujay's GOCD PDF and form. It is a dated source-to-feature snapshot; its “remaining additions” wording predates some of the components above.

The group export ends **20 September 2026, 1:43:36 pm**. The later Shirin discussion has its own [private six-attachment inventory](source-materials/private-context/2026-09-20/shirin-latest/README.md). Its two machine transcripts remain approximate. The [user-supplied clarification saved on 21 September](source-materials/private-context/2026-09-21/SHIRIN_CLARIFICATION_USER_SUPPLIED.txt) separately establishes appointment instructions and dated Hb/RBC results with ranges and High/Low display. It is a user clarification, not an independently verified verbatim audio transcript. The adopted lab implementation copies units, ranges and flags from the report. Hospital screenshots remain private references, not product/deck assets. [Eight later design-reference PNGs](source-materials/private-context/2026-09-21/design-references/manifest.json) are saved privately with file hashes.

The **25 August technical-thoughts DOCX** and **full 30 August meeting recording/transcript** remain missing. The [20 September recovery report](CONTEXT_RECOVERY_2026-09-20.md) preserves earlier source boundaries; the [source index](SOURCE_INDEX.md) and [context map](PROJECT_CONTEXT_MAP.md) point here for current implementation status.
