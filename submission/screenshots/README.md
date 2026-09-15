# Reviewed local prototype captures

Captured in real Chrome on 13 September 2026 at http://127.0.0.1:4817/ after pulling commit 2fb5ebb and applying the fixes in ../../PULL_REVIEW_2026-09-13.md. All patient names, identifiers, dates, consent events and card contents are synthetic. Nearby facility names and map tiles are public Photon/OpenStreetMap results, not verified care referrals.

Current files:

- appointments-desktop.png and appointments-mobile.png: clinician appointment workspace.
- patient-plan-desktop.png and patient-plan-mobile.png: loaded Version 1 after simulated consent.
- emergency-card-mobile.png: issued synthetic card and rendered QR. Print output and physical QR scanning were not tested.
- nearby-care-desktop.png and nearby-care-mobile.png: city-based Hyderabad search, using no personal geolocation. The mobile capture shows the map above the scrollable list after repair.

Desktop browser viewport: 1536 x 730. Mobile viewport: 390 x 844. The captured content excludes the scrollbar area. Files are unaltered Chrome captures. The development-tool icon appears in these development-build captures. A subsequent production-build browser smoke test passed.

The current deck embeds appointments-desktop.png and nearby-care-mobile.png on slide 6. Earlier numbered screenshots, if present, remain historical references.
