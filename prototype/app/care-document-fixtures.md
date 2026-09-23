# Document journey

`care-document-library.ts` supplies four fictional PDF records for the existing Meera Raghavan account (`CANCER-20418`). Other patient IDs return an empty library. Stable versioned IDs allow the application to seed the collection once without replacing uploaded records or edits.

The PDF bytes and saved searchable text are generated from the same lines. The care-conversation text follows the existing account's conversation. The visit checklist and two CBC reports are authored walkthrough fixtures. They are not clinical records, evidence of clinician review, or extracted from real patient uploads. PDF metadata identifies this provenance. Do not seed them into another account.

## Connected behavior

- Pass `onReportsChange={setCareReports}` to `CareDocumentSearch` for uploads and document-date edits. The callback receives the complete next report array, preserving other patients.
- Seed `createMiraDocumentLibrary('CANCER-20418')` only after report and text storage has hydrated. Merge by both patient ID and report ID. Persist a version flag separately so intentionally removed files do not reappear.
- PDF and plain-text uploads are genuinely read. PDF extraction is limited to the first 12 pages and 200,000 characters; skipped/scanned pages are identified during review.
- Images have a genuine original preview and editable filename-based folder suggestion. A person adds any searchable text. There is no OCR service or fabricated extraction.
- The category and date can be corrected before saving. Multiple files are reviewed in order; cancellation leaves the remaining unreviewed files unsaved.
- Search is extractive keyword/synonym retrieval. Questions return only passages from the selected patient's checked text, with document date, page (when available), line and original file. Unsupported questions produce a not-found state. It is not a generative model or medical-data interpretation.
- Files and text use the application's existing browser storage. This module adds no external data transfer or cross-device sharing.

## Useful walkthrough questions

- What should I bring to my next visit?
- What matters to me?
- Who joined the care conversation?
- My latest haemoglobin
- When was my blood test?
- How much aspirin should I take? — must return no matching source, never a dose.

The original PDF can be opened from every result. Editing checked text changes the searchable record; it does not alter the original file. The result label is consequently “Quoted from the checked text.”
