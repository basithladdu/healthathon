# Doctor workspace preservation baseline

A non-destructive local baseline was captured before the doctor-workspace integration work on 23 October 2026 (India time).

## What is protected

- The current tracked source plus relevant untracked Android source, illustrations and provenance documents.
- The Git revision, branch, status, staged patch and binary working-tree patch.
- Only the Sahara development app on `emulator-5580` / `Sahara_Doctor_Demo`: shared preferences, Room database and WAL files, app files (including recordings), and `no_backup` files.
- No other emulator or Android application was touched. The app was stopped for a coherent snapshot; no data was cleared, overwritten or reset.

The source baseline contains 849 files (243,248,900 bytes). The app-private baseline contains 88 files (4,887,221 bytes). Build outputs, `node_modules`, and generated `output/` directories were excluded.

## Recovery verification

The baseline is outside the repository at `E:\Healthathon\backups\sahara-baseline-20261023-002520`. Access is restricted to the current Windows user and SYSTEM because the app archive may contain session credentials and private records. Do not commit, upload, email or attach that archive.

- Source archive SHA-256: `F4EE47A816D6B04682D19D08144412369C5BC3AA0F5B3EE31A9238EE1804F7A5`
- App-private archive SHA-256: `017B980A2D7EBFEC21692B1E1891B0B728B19506A6E7728742DCB92DE97171E2`
- Git baseline: `b7ccda14b29a27bae74799a895ac4a27719b55e3`, branch `codex/sahara-merged-20261016`.

Source files were restored into a separate local verification folder and compared against every manifest SHA-256. The app archive was independently extracted twice, and every extracted file hash matched. No restore was performed over live project or emulator data.

For recovery, stop and obtain explicit approval first. Extract into a new directory, verify its manifest, and compare only the affected files. Do not use a broad reset, uninstall, clear-data command or blanket overwrite. Restoring Android private data also requires matching package/schema compatibility and a coherent database/WAL snapshot; prefer selective recovery after inspection.

## Integration verification requirements

- Existing local profiles, dose reports, drafts, published versions, acknowledgements, report metadata, recordings and consent records remain unchanged unless the user explicitly edits them.
- Remote synthetic patients must have separate identities; loading them must not replace or relabel local sample patients.
- Real authentication is not professional verification. A test account must not acquire a verified badge by signing in.
- Signing out clears connected views and sessions without deleting local records.
- Local appointments remain local unless an explicit supported sharing action is invoked.
- Recording audio, choosing a report or opening a patient must not implicitly upload files.
- Network tests must be opt-in and use isolated synthetic accounts/data. Never pass real credentials in source, logs, screenshots or committed test fixtures.
- Keep the existing historical migration tests, local-preview navigation tests, and large-text accessibility checks.
