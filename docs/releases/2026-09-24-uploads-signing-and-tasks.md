# Uploads, signing and practical tasks

24 September 2026. App commits: `5d4b74d` and `e77c01e`.

Production: https://continuity-loop-healthathon.vercel.app/ — deployment `dpl_24SEqWc9gbEFPepLiSJwh52nZgFQ`, READY. The final build and TypeScript check passed.

## Changed

- Four main home cards: My documents, Find support, My space and Calendar. Five smaller Quick access links follow the daily plan.
- Home shows up to three medicines/appointments or upcoming items, plus up to two incoming tasks. Links open the full calendar or task inbox. Future medicines cannot be marked as taken.
- Upload actions on Home, My documents and Medicines. A saved prescription returns to a medicine editor linked to that original. Report search text is optional and folded away; folder and date remain visible.
- Doctor transcript/PDF upload feeds the conversation source. Images/scans are not silently transcribed.
- Signing uses the clinician's filled name, an explicit discussion/decision choice, review confirmation and actionable reasons when blocked. Signed-copy navigation selects the exact version.
- Editing a field no longer reasserts unchanged carried-forward values. Those sections need explicit source-linked confirmation.
- Named task recipients accept and complete assignments. The sender sees the status. Patient/family task creation is folded away until needed; next-visit preparation uses one optional composer.
- The calendar sends tasks awaiting acceptance to the task inbox. Another person's assigned task cannot be completed through the calendar shortcut.
- Revised Round 1 answers and deck story follow the meeting and the later Shirin/Sujay feedback. Initial wording is best supportive care; the earlier death outcome is removed.

## Observed

- Local browser: uploaded a fictional text conversation, prepared its note, selected Discussion, confirmed review and signed it. The result was Version 2, and Open family copy selected that version in history.
- Local browser: assigned a doctor task to Meera; it appeared on her Home with Dr Sujay as sender. Meera accepted it and marked it Done.
- Home viewed on desktop and at 390px. The mobile document width and scroll width were both 390px.
- Opened the final production URL, signed in and saw the four home cards, upload action, next scheduled visit and five Quick access links.
- The targeted signing regression file passed 4/4. No broad test suite was run. A local type check hit an old generated Next.js route reference; Vercel's fresh production build and TypeScript check passed.

## Boundaries

- These role-to-role interactions use the app's shared browser data. Separate authenticated accounts and cross-device notification delivery are not connected.
- AI transcription, language-model drafting, scanned-image OCR and semantic retrieval remain planned. Current text/PDF reading and source-based preparation use the actual selected input.
- Signatures are typed names; identity verification and legal digital signatures are not integrated.
- The updated answer text is saved in the repo. Chrome disconnected after the existing portal answers were read; no edited portal value or submitted entry is claimed.
- The map's existing care-centre locations do not prove current emergency capacity, ambulance dispatch or oral-morphine stock.
