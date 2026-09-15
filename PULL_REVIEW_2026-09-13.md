# Pull review and local fixes ? 13 September 2026

Pulled main by fast-forward from ffaa89f to 2fb5ebb67bc13ccea4f93dccb32eea578c5dd0f3 (Add patient portal, nearby-care map, ECTPR quick view and redesign). Existing presentation files and unrelated dirty work were preserved. The outer repository tracks prototype files directly; prototype also has a separate nested Git history with no remote. No reset, commit, push, deployment or submission was performed.

## Fixed

- Nearby-care map: Chrome reproduced a second search with results but no Leaflet container or tiles. The map now disposes when the results container unmounts and initializes again. Abandoned searches cannot update a newer search. Markers have accessible names, and an empty category no longer claims that all nearby care is absent.
- Nearby results: excluded an observed veterinary hospital from human-care listings. This filter is not directory validation.
- Mobile map placement: the incoming page put the map around 8,780px down, below 40 cards. The map now begins at 467px in the 390px viewport. All 40 results remain in a scrollable region; total page height fell from 9,424px to 1,634px. Show on map brings the map into view.
- Patient consent: Chrome reproduced simulated consent to No released version for Farah Nair. Both the form and parent handler now require the actual released summary to be loaded. Patients with only an unloaded-summary placeholder cannot sign either. Missing releases no longer claim verified by a physician in the plan header.
- Appointment dialogs: mount fresh for each opening; removed effect-driven form resets. Timer cleanup restores focus on closing. Chrome confirmed discarded scheduling notes and rescheduling reasons reset, with the requested reschedule tab retained.
- Emergency card: QR result is bound to its payload, stale results are hidden, hash and QR failures are handled together, and printing waits for the QR. Chrome confirmed issue, QR rendering and enabled print for the synthetic patient. Physical print, scanning and forced QR failure were not exercised.
- Lint blockers: resolved all 11 incoming errors, including typed Leaflet refs and toast timer lifecycle. Five existing unused-code warnings remain.

## Verification

- npm ci --ignore-scripts --no-audit --no-fund: passed.
- npx tsc --noEmit --incremental false: passed.
- npm run lint: passed, zero errors and five unused-code warnings.
- npm run build:next: passed with static /, /_not-found and /icon.svg.
- npm run build: passed. Vinext reported its informational unknown route-classification limitation.
- git diff --check: passed.
- Real Chrome: repeated city search retained one map and 16 loaded tiles; empty-category state correct; no veterinary fixture result; consent rejected for missing release and accepted for loaded Meera Version 1; appointment reset checks passed; desktop and 390px layouts checked for plan, card, map and appointments without horizontal overflow.
- Production Next server at http://127.0.0.1:4817/: home and controlled retrieval opened. Retrieval was disabled until acknowledgement, then revealed ED Quick View and focused the patient heading. Switching to the coordinator closed the record and restored the physician-only gate. Leela Thomas's verified-but-unloaded summary also correctly blocked consent. No app console errors were observed; an unrelated Chrome extension logged its own bridge errors.

## Presentation update

submission/Healthathon_Continuity_Loop_Product_Visuals.pptx is the current eight-slide deck. Slide 6 now shows the reviewed appointment workspace and mobile map. The care illustrations remain. The file is 8,395,758 bytes. Package/layout checks passed, all eight notes remain, new screenshots are embedded unchanged, and the other seven slide renders are pixel-identical to the earlier reviewed deck.

## Remaining boundaries

This is still a synthetic browser-local prototype. Production authentication, durable records, live AI, messaging, hospital integration, clinical validation and native PowerPoint playback were not established. The public site was not updated. The existing demo video still shows the older deck. The local production preview is running on port 4817.
