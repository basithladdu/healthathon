# KPI counts: how to run the read-only queries

**Status: DRAFT, 27 Oct 2026.** No pilot has started, so no real numbers exist yet. The queries were tested only on made-up rows in a local test database, never on Sahara's real Supabase.

File: [`kpi-queries.sql`](kpi-queries.sql). It counts KPI 1 and KPI 2 in [`KPI_EVALUATION_DRAFT.md`](KPI_EVALUATION_DRAFT.md), plus diary days. The draft's KPI 3 (caregiver burden, Brief AMC-CBI) is asked on paper or by phone and is not in the app, so no query counts it. The app has no KPI report screen or export, so the owner runs these by hand.

| Query | What it counts |
|---|---|
| KPI 1 | Per patient: note versions published, versions confirmed by the patient, versions confirmed by family, whether at least one version was confirmed, and "Ask to talk" requests. Patient and family stay separate. |
| KPI 2a | Per patient, per pilot week: pain scores (0–10), split by who answered (patient, patient with help, family or carer, care team). |
| KPI 2b | One line: the share of patient-weeks with at least one pain score from home (not the care team). |
| KPI 2c | Per patient: visits, and visits with a home pain score in the 7 days before. |
| KPI 3 (diary days) | Per patient: diary days (days with at least one check-in, symptom note or journal note) out of days in the pilot. A supporting count, not the draft's caregiver-burden KPI 3. |

## What it shows and what it never shows
- It shows only a `patient_ref` code, week numbers, dates and counts.
- It never shows names, emails, note text, symptom text or anything people typed.
- `patient_ref` is made from the patient record id and a private word (the `salt`). Keep the salt private. Use the same salt for all queries in one report, so the rows match up.
- Every statement is a `SELECT`. Nothing is written or changed.

## How to run it (Supabase SQL editor)
1. Open the Sahara project in Supabase, then **SQL Editor**, then **New query**.
2. Check the database version: run `select version();`. It must be PostgreSQL 16 or newer. If it is older, stop and ask the team (the queries use `pg_input_is_valid`).
3. Find the pilot accounts: **Authentication → Users**. Copy the **UID** of each pilot patient's sign-in account (the account that created the patient record).
4. Copy **one** query from `kpi-queries.sql` (from `with params as (` to the `;` at its end) into the editor.
5. Edit the `params` block at the top of that query:
   - `pilot_start` and `pilot_end`: first and last pilot day (both counted);
   - `tz`: time zone, `Asia/Kolkata` for India;
   - `salt`: your private word;
   - `pilot_accounts`: the UIDs, like `array['uid-1','uid-2']::uuid[]`.
6. Optional extra safety: put `begin read only;` on the first line and `rollback;` on the last line. If the editor then shows no result table, remove those two lines and run again (whether the editor shows the result of a middle statement is [unverified]).
7. Click **Run**. Download the result as CSV if you want to keep it.
8. Do the same for each query. Paste results only into the team's private files, and check them before any number goes on a slide.

## Rules the queries follow
- Only patient records owned by a pilot account are counted. Records of people who are not signed in stay on the phone and cannot be counted.
- Dates come from when the entry was saved on the phone (`created`), turned into days in `tz`. A pain score typed on Tuesday about Monday counts on Tuesday.
- A pain score is a symptom note for **Pain** with a number. "Not rated" is not counted. A body-map strength counts.
- Week 1 starts on `pilot_start`. If the pilot is not a whole number of weeks, the last week is shorter.
- Deleted entries are gone from the server, so they are not counted.
- KPI 1 counts confirmations made in the pilot dates for note versions that still exist.

## Known limits (team to check)
- **"Who started the talk"** (patient-asked or doctor-started) is not saved anywhere. It still needs the doctor's manual tally.
- **Requests:** on `main`, "Ask to talk" saves a task whose hidden type is `conversation` (`CareWorkflow.kt`). Older builds saved a task titled "Ask for a goals-of-care conversation" (`CareUi.kt`). The query counts both.
- **Visits:** counted from "Add visit" entries and calendar entries with type Visit. Visits only in the site's paper register are not in the app.
- **Visit time:** a visit saved without a time may be stored at the start of that day, so a pain score from the same morning would not count as "before" it. [unverified]
- **Phone clocks:** `created` is the phone's time. A wrong phone clock gives a wrong day.
- The scale is Sahara's own 0–10 diary. Do not call it ESAS-r or IPOS.

## How this was checked
On 27 Oct 2026, in a throwaway local PostgreSQL 16.15 database: all 14 files in `android/supabase/migrations/` were applied on top of a small stand-in for Supabase's `auth`, `extensions` and `storage` schemas. Made-up rows were added (3 pilot patients, 1 non-pilot patient, times on both sides of the pilot dates and midnight in India, "Not rated" and body-map scores, a broken symptom note, a legacy confirmation). All 5 queries ran inside a read-only transaction and gave the counts worked out by hand. The non-pilot patient never appeared, and no names or typed text appeared in the output.
