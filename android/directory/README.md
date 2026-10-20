# India directory source record

Retrieved **20 October 2026**. `../app/src/main/assets/india.json` contains **1,631 public directory listings across all 36 Indian states and union territories**. This is a merged directory, not an exhaustive census or independently verified network of Sahara partners.

The import covers all 36 pages linked from [Pallium India's national directory](https://palliumindia.org/clinics), plus 45 published [IAPC directory](https://www.palliativecare.in/palliative-care-directory-of-india/) state/district pages. IAPC's public WordPress published-page index resolved its actual URLs, including historical spelling variants. All 14 Kerala districts are included; the source separately publishes Calicut and Kozhikode. Five discovery pages and 81 directory pages have recorded raw-source hashes. No page in the final source ledger failed, and no directory row remained unparsed because of its column count.

## What is retained

- **1,509 listings have a published telephone; 122 do not.** Missing contact details remain missing.
- The original `karnataka.json` is byte-for-byte unchanged. All **33 original stable IDs**, district labels and **9 exact source-backed coordinate pairs** are retained in the national asset.
- Each listing has its source URL, publisher, retrieval date, state and source-row reference. `india-directory-sources.json` contains source hashes, per-state counts, caveats and the deduplication ledger.
- **151 matching source rows were merged** using the same preserved Karnataka identity, or an exact same-state/name match with a matching address or telephone. Conflicting published details remain in `sourceVariants`.
- No fuzzy provider-name merging is performed. Possible spelling/name/address variants remain; the count is of directory listings rather than guaranteed distinct operating facilities.
- **No additional exact place-coordinate links were available.** Records without coordinates remain searchable by published address; they must not be plotted at guessed city centres.

`verification` is always `directory_listed`, and `independentlyVerified` is always false. A retrieval date does not mean the centre was contacted or operationally checked. `morphine` is always `unknown`; a source's historical availability text is retained separately as `sourceListedMorphine`, never as live medicine stock. Published services and contact details can be stale, and callers should confirm before travelling.

No patient data or private contact list was used. Only public directory contact telephone numbers are shipped; contact-person names/emails and entire website HTML are not copied into app records. Raw public website snapshots are preserved under `output/research/india-directory-20261020/` for provenance, separate from Android assets. Private account/browser records are not part of that archive.

## Reproduce

Python dependencies: `beautifulsoup4`, `requests`. They are tooling dependencies; no Android dependency was added.

```powershell
python android/scripts/scrape-india-directory.py --date 2026-10-20
python android/scripts/scrape-india-directory.py --date 2026-10-20 --offline
python android/scripts/test-india-directory.py
```

The first command reuses matching dated raw snapshots or retrieves missing pages with at most three concurrent requests. The second requires the saved sources and makes no network requests. For a fresh retrieval, choose the actual new date, so the previous snapshots remain intact. Eight source, preservation, parser and data contracts passed for this import. Android UI/device integration is verified separately by the integrating agent; this directory import did not package or publish an app.
