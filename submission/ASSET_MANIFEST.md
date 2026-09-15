# Health-a-thon asset manifest

Status as of 13 September 2026. This is a preparation manifest; no asset has been uploaded to the application.

## Current deck revision

Use `Healthathon_Continuity_Loop_Product_Visuals.pptx` for the current local deck review. It has eight slides, three generated editorial care illustrations, two actual Chrome captures on slide 6, editable workflow diagrams and the existing prototype QR. Size: 8,395,758 bytes. SHA-256: `207dfa2877e75f21c59ccdb84a4f63f06897c17a787bd1bed6fea6ecf78bae79`. Matching copy: `../pitch/Continuity_Loop_Product_Visuals.pptx`.

`Product_Workflow_Preview.png` shows the changed slide. `screenshots/README.md` identifies the current app captures. `visual-assets/README.md` records the fictional editorial illustrations. Package/layout validation and rendered-slide review passed. Native PowerPoint playback remains unverified.

The table below describes the earlier revision. The 24-second `demo-video.mp4` has not been regenerated. The public deployment and QR destination have not been refreshed in this pull review.

## Previous revision inventory

| Asset | Status | Next action | Public-safe? |
| --- | --- | --- | --- |
| `../submission.md` | Ready as a draft | Clinicians/team review, then paste manually | Yes, after review |
| `../pitch/Continuity_Loop_Pitch_Deck_Visual.pptx` | Current 8-slide visual candidate | Team content review, then upload the approved copy | Needs review |
| `Healthathon_Continuity_Loop_Deck_Visual.pptx` | Current 8-slide visual candidate; built from the copied Utility/Mulberry presentation sources; 57,353 bytes; SHA-256 `B7F2A701486CFB6CCBB279FD4C032892788503BCC6DAA0DFF2CAF9F211EA631F` | Team content review, then upload this copy | Needs review |
| `../pitch/Continuity_Loop_Pitch_Deck.pptx` | Prior deck version retained for comparison | Do not upload unless the team explicitly reverts | Prior version |
| `Healthathon_Continuity_Loop_Deck.pptx` | Prior submission copy retained for comparison | Do not upload unless the team explicitly reverts | Prior version |
| `Healthathon_Continuity_Loop_Deck.pdf` | Not yet created | Export and inspect final PDF | Only after review |
| `demo-video.mp4` | Current 24-second, silent walkthrough of the visual deck; no app UI is claimed | Replace with a narrated/live-screen recording after the UI build, then host publicly | Needs review |
| `qr-code.png` | Created for `https://continuity-loop-healthathon.vercel.app/` | Re-scan against the final URL before uploading | Yes, after URL confirmation |
| `screenshots/` | Existing five synthetic-data references retained outside the new deck | Add current live captures to the reserved deck slots after the UI build | Yes, after review |
| `../source-materials/ppt-sources/` | Copied Utility/Mulberry PPT JavaScript sources used as visual references | Keep local; do not upload as submission evidence | No, private source |
| `public-links.txt` | Created with the public MVP URL only | Add the public video URL after hosting | Yes, after link checks |
| `../prototype/` | Existing local source | Use for demo/reproducibility, not as a form upload by default | No, private source |
| `../source-materials/user-provided/` | Ingested privately | Keep as team reference; do not upload automatically | No |

## Required final evidence

- Six to eight slides, under 25 MB.
- The current candidate includes reviewed local app captures and has no blank screenshot slots.
- Current public URL returns the intended prototype.
- Video URL opens without authentication.
- QR code resolves to that same final URL.
- Screenshots show synthetic data and the assistive/non-diagnostic boundary.
- Claims in the deck match the current code, not a planned production system.
