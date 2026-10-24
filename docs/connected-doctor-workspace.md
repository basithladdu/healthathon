# Connected doctor test workspace

## Scope and boundaries

The new native doctor shell uses real Sahara authentication and real database persistence for an **isolated synthetic dataset**. Its six fixture patients are not real patients; creating the dataset does not verify a clinician, invite a family member, or grant access to actual clinical records. Real shared-clinician review remains a separate route using its existing permissions and verification.

The canonical account backend remains `cghlkqeqiokjqdionrkc`. No data or authentication was moved to the legacy Saanthvana project `kjnhtakqzboayqorrlue`.

Existing `doctor-demo-v1`, `doctor-workflow-v1`, account-scoped Room databases, local recordings, documents, draft histories and old shared-care contracts are unchanged. There is no automatic import, name matching, fixture upload on startup, or fallback that shows fictional content as a real patient's data.

## New database objects

- `sahara_doctor_datasets`: UUID, authenticated owner UUID, mandatory synthetic marker, label, creation timestamp; one dataset per account.
- `sahara_doctor_dataset_members`: dataset/user UUIDs, edit permission and expiry. No client membership mutation grants exist; this is infrastructure for a separately reviewed invitation flow, not a working family-sharing feature.
- `sahara_doctor_patients`: stable server UUID, dataset UUID, original fixture ID, name, age, care context, summary, rich JSON payload and synthetic marker. Dataset/fixture ID uniqueness makes seeding repeatable without replacing edits.
- `sahara_doctor_notes`: UUID, dataset/patient UUIDs, author, revision, draft or `test_published` status, full JSON, publication fingerprint and server timestamps. Published test snapshots are immutable and unverified.
- `sahara_doctor_doses`: append-only UUID, dataset/patient, medicine label, planned time, nullable reported-taken time, server logged time, actual authenticated reporter, reported outcome and sample provenance. Seeded entries are fictional examples, not verified ingestion or prescriptions.
- `sahara_doctor_files`: UUID, dataset/patient, authenticated uploader, title, kind, MIME, byte size, client SHA-256 fingerprint, protected object path, explicit permission attestation, server timestamp and pending/ready upload state.

Every new table has RLS. Anonymous metadata lookup privilege on the file table is required for cross-table Storage policy planning, but there is **no anonymous row policy**: anonymous metadata queries return zero rows. Anonymous writes and RPC execution are denied.

The private bucket `sahara-doctor-test-files` limits objects to 4 MiB and approved audio/PDF/image MIME types. Paths must be `datasetUUID/patientUUID/fileUUID`. Restrictive Storage policies protect this bucket even if another application's permissive policy is later added. Existing bucket policies were not changed.

## Care Note JSON: schemaVersion 1

Preserved fields: `synthetic`, `priorities`, `participants`, `topics`, `questions`, `undecided`, `nextSteps`, `sourceText`, `sourceIds`, `updatedAt`, `mockGenerated`, `checklist` (title/status), `transcriptProvider`, `transcriptModel`, `transcribedAt`, `audioClipId`, `aiProvider`, `aiModel`, `aiWarnings`, and `evidence` (section/quote).

Unknown schema versions, unknown root/nested keys, invalid types and excessive sizes fail closed rather than silently discarding or truncating data. Source text is at most 60,000 characters; individual sections at most 4,000; complete JSON at most 256 KiB. Source IDs carried from fixtures are explicitly synthetic references, not authenticated original report attachments.

Draft writes use exact revision compare-and-swap. Direct client UPDATE is not granted. The public RPC is security-invoker; its internal fixed-search-path mutation function checks caller, open-account state, dataset write access, original author, patient and revision. Publication creates an immutable **unverified test snapshot**, never a verified clinical receipt.

## Explicit file flow

1. User chooses one file and explicitly attests permission to upload it.
2. Client checks allowed type and 4 MiB bound, computes a fingerprint and creates pending metadata.
3. Authenticated upload writes a new private object without upsert.
4. Server finalisation checks object existence, byte size and MIME against metadata before marking ready.
5. Opening a ready file makes a fresh authenticated request, rechecks current metadata/access and verifies downloaded size and fingerprint before private-cache/FileProvider opening.

The fingerprint is computed by the uploading client; server finalisation does not claim independently verified clinical authenticity or server-side content hashing. Permission attestation is not proof of every participant's consent or representative authority. No public URLs, automatic uploads, file replacement or deletion controls are exposed. Interrupted uploads can leave pending metadata; these are not shown as ready originals. Retention/deletion administration is a remaining task.

## Local mock-only features retained

Visits and discussion-request scheduling remain explicitly local mock workflows for now. They do not notify other accounts, reserve a hospital slot or initiate calls. Dose history in the new dataset is genuinely database-persisted but remains synthetic and is not yet a live patient/family tracker.

## Deployment and verification

Applied additive migrations:

- `20261022190713_synthetic_doctor_workspace.sql`
- `20261022190911_synthetic_doctor_indexes.sql`

CLI-created filenames were reconciled with timestamps assigned by the remote migration API. Six covering foreign-key indexes were added after advisor review. No existing records, Auth users, old tables/functions or bucket policies were modified.

Catalog/count and advisor snapshots are outside the repository at `E:/Healthathon/backups/sahara-baseline-20261023-002520/`. This is a schema/count snapshot, **not a complete production database backup**. The separate local source/app-data baseline is also retained there.

Offline PostgreSQL tests (`android/scripts/test-doctor-connected-sql.mjs`) cover 51 auth/RLS, full-field persistence, strict schema validation, revision, immutability, timestamps, cross-patient, idempotency and Storage-policy cases. They use local Auth/Storage shims and do not prove actual Auth JWT verification or upload service behaviour.

Actual anonymous REST checks: file metadata `200 []`; seed RPC `401`; private bucket listing `200 []`; guessed private object returns no file (`400`). A successful signed-in UI/seed/save/upload end-to-end test still requires the user to create and sign into the new real test account. No test credentials are hardcoded or provisioned through raw Auth-table SQL.
