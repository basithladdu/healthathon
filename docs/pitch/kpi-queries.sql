-- Sahara pilot KPI counts. READ-ONLY: every statement below is a SELECT.
-- DRAFT, 27 Oct 2026. For the 3 KPIs in docs/pitch/KPI_EVALUATION_DRAFT.md
-- (branch sa5-pitchkit). How to run: docs/pitch/KPI_QUERIES_README.md.
--
-- Run ONE query at a time in the Supabase SQL editor (highlight it, then Run).
-- Before each run, edit the `params` block at the top of that query:
--   pilot_start, pilot_end : first and last pilot day (both included), local dates
--   tz                     : time zone used to turn saved times into days
--   salt                   : any private word; makes the pseudonymous patient_ref.
--                            Use the same word for every query in one report.
--                            Do not share it: with it, a patient_ref can be traced back.
--   pilot_accounts         : sign-in account ids (Supabase Authentication > Users > UID)
--                            of the PATIENT record owners in the pilot.
--
-- What the output contains: patient_ref (first 12 hex characters of
-- md5(salt || patient record id)), week numbers, dates and counts.
-- It never shows names, emails, note text, symptom text or any other free text.
--
-- Where the fields come from (checked on main, 27 Oct 2026):
--   public.healthathon_records            android/supabase/migrations/20261009162933_android_care_accounts.sql
--     kind='note' data: version, published (ms)                     CareCloud.kt noteJson
--     kind='ack'  data: versionId, relationship ('Patient' | 'Family / carer'), at (ms)
--                 (older acks may have no versionId: then the ack id is the note id)  CareCloud.kt
--     kind='entry' data: kind, title, detail, due (ms), created (ms)  CareCloud.kt entryJson
--       data.kind 'checkin' = check-in. A symptom note is a check-in whose detail is
--         'sahara:symptom:v1' + newline + JSON {symptom, rating, respondent, painBodyRatings, ...}
--         (CareSymptomData.kt SymptomNoteCodec, CareModel.kt)
--       data.kind 'journal' = journal note (CareDailyUi.kt careAddKinds)
--       data.kind 'visit'   = visit (older "Add visit"). Calendar visits are data.kind 'task'
--         whose detail is 'sahara:entry:v1' + newline + JSON {type:'schedule', category:'Visit'}
--       Conversation requests are data.kind 'task' with that JSON {type:'conversation'}
--         (CareWorkflow.kt CareEntryCodec), or older tasks titled
--         'Ask for a goals-of-care conversation' (CareUi.kt)
--   public.healthathon_people: id, owner_id, created_ms (the name column is never selected)
--
-- Every number or JSON cast sits inside CASE WHEN <check> so one bad row cannot stop a query.
-- Needs Postgres 16 or newer (pg_input_is_valid). Check with: select version();


-- =====================================================================
-- KPI 1. Goals-of-care notes confirmed by the patient or family.
-- One row per pilot patient, then one 'ALL' row.
--   notes_published            note versions published in the pilot dates
--   versions_confirmed_patient note versions the patient confirmed in the pilot dates
--   versions_confirmed_family  note versions a family member confirmed in the pilot dates
--   has_confirmed_note         1 if the patient or family confirmed at least one version
--   conversation_requests      "Ask to talk" / goals-of-care requests created in the pilot dates
-- In the ALL row, has_confirmed_note is the number of patients with a confirmed note.
-- Keep patient and family counts apart. Do not add requests and notes together.
-- =====================================================================
with params as (
  select date '2026-11-02' as pilot_start,
         date '2026-12-27' as pilot_end,
         'Asia/Kolkata'::text as tz,
         'replace-with-a-private-word'::text as salt,
         array['00000000-0000-0000-0000-000000000000']::uuid[] as pilot_accounts
), bounds as (
  select *, (extract(epoch from (pilot_start::timestamp at time zone tz)) * 1000)::bigint as from_ms,
            (extract(epoch from ((pilot_end + 1)::timestamp at time zone tz)) * 1000)::bigint as to_ms
  from params
), pilot_people as (
  select p.id as person_id, left(md5(b.salt || p.id::text), 12) as patient_ref
  from public.healthathon_people p cross join bounds b
  where p.owner_id = any(b.pilot_accounts)
), records as (
  select r.person_id, r.id, r.kind, r.data->>'kind' as entry_kind, r.data->>'title' as title,
         r.data->>'relationship' as relationship,
         coalesce(nullif(r.data->>'versionId', ''), r.id::text) as version_id,
         case when r.data->>'published' ~ '^[0-9]{1,15}$' then (r.data->>'published')::bigint end as published_ms,
         case when r.data->>'at' ~ '^[0-9]{1,15}$' then (r.data->>'at')::bigint end as at_ms,
         case when r.data->>'created' ~ '^[0-9]{1,15}$' then (r.data->>'created')::bigint end as created_ms,
         case when left(r.data->>'detail', 16) = E'sahara:entry:v1\n' and pg_input_is_valid(substr(r.data->>'detail', 17), 'jsonb')
              then substr(r.data->>'detail', 17)::jsonb end as workflow
  from public.healthathon_records r join pilot_people pp using (person_id)
), confirmed as (
  select a.person_id,
         count(distinct a.version_id) filter (where a.relationship = 'Patient') as versions_confirmed_patient,
         count(distinct a.version_id) filter (where a.relationship = 'Family / carer') as versions_confirmed_family
  from records a join records n on n.kind = 'note' and n.person_id = a.person_id and n.id::text = a.version_id
  cross join bounds b
  where a.kind = 'ack' and a.at_ms >= b.from_ms and a.at_ms < b.to_ms
  group by a.person_id
), published as (
  select n.person_id, count(*) as notes_published
  from records n cross join bounds b
  where n.kind = 'note' and n.published_ms >= b.from_ms and n.published_ms < b.to_ms
  group by n.person_id
), requests as (
  select r.person_id, count(*) as conversation_requests
  from records r cross join bounds b
  where r.kind = 'entry' and r.entry_kind = 'task'
    and r.created_ms >= b.from_ms and r.created_ms < b.to_ms
    and (r.title = 'Ask for a goals-of-care conversation' or r.workflow->>'type' = 'conversation')
  group by r.person_id
), per_patient as (
  select pp.patient_ref,
         coalesce(pu.notes_published, 0) as notes_published,
         coalesce(c.versions_confirmed_patient, 0) as versions_confirmed_patient,
         coalesce(c.versions_confirmed_family, 0) as versions_confirmed_family,
         case when coalesce(c.versions_confirmed_patient, 0) + coalesce(c.versions_confirmed_family, 0) > 0 then 1 else 0 end as has_confirmed_note,
         coalesce(rq.conversation_requests, 0) as conversation_requests
  from pilot_people pp
  left join published pu using (person_id)
  left join confirmed c using (person_id)
  left join requests rq using (person_id)
)
select patient_ref, notes_published, versions_confirmed_patient, versions_confirmed_family,
       has_confirmed_note, conversation_requests
from (
  select 0 as sort, * from per_patient
  union all
  select 1, 'ALL (' || count(*) || ' patients)', sum(notes_published), sum(versions_confirmed_patient),
         sum(versions_confirmed_family), sum(has_confirmed_note), sum(conversation_requests)
  from per_patient
) t order by sort, patient_ref;


-- =====================================================================
-- KPI 2a. Pain scores per patient per pilot week.
-- One row per pilot patient per pilot week (a week with no score shows 0).
-- A pain score = a symptom note for 'Pain' with a 0-10 number
-- (the overall rating, or a strength on the body map). "Not rated" is not counted.
-- The week comes from when the score was entered (data.created), in tz.
-- Week 1 starts on pilot_start.
-- Columns by who answered: patient, patient with help, family or carer, care team.
-- home_scores = patient + patient with help + family or carer (not the care team).
-- =====================================================================
with params as (
  select date '2026-11-02' as pilot_start,
         date '2026-12-27' as pilot_end,
         'Asia/Kolkata'::text as tz,
         'replace-with-a-private-word'::text as salt,
         array['00000000-0000-0000-0000-000000000000']::uuid[] as pilot_accounts
), bounds as (
  select *, (extract(epoch from (pilot_start::timestamp at time zone tz)) * 1000)::bigint as from_ms,
            (extract(epoch from ((pilot_end + 1)::timestamp at time zone tz)) * 1000)::bigint as to_ms,
            ((pilot_end - pilot_start) / 7) + 1 as weeks
  from params
), pilot_people as (
  select p.id as person_id, left(md5(b.salt || p.id::text), 12) as patient_ref
  from public.healthathon_people p cross join bounds b
  where p.owner_id = any(b.pilot_accounts)
), entries as (
  select r.person_id,
         case when r.data->>'created' ~ '^[0-9]{1,15}$' then (r.data->>'created')::bigint end as created_ms,
         case when left(r.data->>'detail', 18) = E'sahara:symptom:v1\n' and pg_input_is_valid(substr(r.data->>'detail', 19), 'jsonb')
              then substr(r.data->>'detail', 19)::jsonb end as symptom
  from public.healthathon_records r join pilot_people pp using (person_id)
  where r.kind = 'entry' and r.data->>'kind' = 'checkin'
), pain as (
  select e.person_id, e.symptom->>'respondent' as respondent,
         (((to_timestamp(e.created_ms / 1000.0) at time zone b.tz)::date - b.pilot_start) / 7) + 1 as pilot_week
  from entries e cross join bounds b
  where e.symptom->>'symptom' = 'Pain'
    and (jsonb_typeof(e.symptom->'rating') = 'number'
         or (jsonb_typeof(e.symptom->'painBodyRatings') = 'object' and e.symptom->'painBodyRatings' <> '{}'::jsonb))
    and e.created_ms >= b.from_ms and e.created_ms < b.to_ms
), weeks as (
  select pp.person_id, pp.patient_ref, w as pilot_week, b.pilot_start + (w - 1) * 7 as week_starts
  from pilot_people pp cross join bounds b cross join generate_series(1, b.weeks) w
)
select w.patient_ref, w.pilot_week, w.week_starts,
       count(p.pilot_week) as pain_scores,
       count(*) filter (where p.respondent = 'patient') as by_patient,
       count(*) filter (where p.respondent = 'assisted-patient') as by_patient_with_help,
       count(*) filter (where p.respondent = 'caregiver') as by_family_or_carer,
       count(*) filter (where p.respondent = 'clinician') as by_care_team,
       count(*) filter (where p.respondent in ('patient', 'assisted-patient', 'caregiver')) as home_scores
from weeks w left join pain p on p.person_id = w.person_id and p.pilot_week = w.pilot_week
group by w.patient_ref, w.pilot_week, w.week_starts
order by w.patient_ref, w.pilot_week;


-- =====================================================================
-- KPI 2b. Summary: share of patient-weeks with at least one home pain score.
-- Same rules as 2a. One row. No patient_ref, so no salt needed.
-- =====================================================================
with params as (
  select date '2026-11-02' as pilot_start,
         date '2026-12-27' as pilot_end,
         'Asia/Kolkata'::text as tz,
         array['00000000-0000-0000-0000-000000000000']::uuid[] as pilot_accounts
), bounds as (
  select *, (extract(epoch from (pilot_start::timestamp at time zone tz)) * 1000)::bigint as from_ms,
            (extract(epoch from ((pilot_end + 1)::timestamp at time zone tz)) * 1000)::bigint as to_ms,
            ((pilot_end - pilot_start) / 7) + 1 as weeks
  from params
), pilot_people as (
  select p.id as person_id from public.healthathon_people p cross join bounds b
  where p.owner_id = any(b.pilot_accounts)
), entries as (
  select r.person_id,
         case when r.data->>'created' ~ '^[0-9]{1,15}$' then (r.data->>'created')::bigint end as created_ms,
         case when left(r.data->>'detail', 18) = E'sahara:symptom:v1\n' and pg_input_is_valid(substr(r.data->>'detail', 19), 'jsonb')
              then substr(r.data->>'detail', 19)::jsonb end as symptom
  from public.healthathon_records r join pilot_people pp using (person_id)
  where r.kind = 'entry' and r.data->>'kind' = 'checkin'
), home_pain_weeks as (
  select distinct e.person_id,
         (((to_timestamp(e.created_ms / 1000.0) at time zone b.tz)::date - b.pilot_start) / 7) + 1 as pilot_week
  from entries e cross join bounds b
  where e.symptom->>'symptom' = 'Pain'
    and e.symptom->>'respondent' in ('patient', 'assisted-patient', 'caregiver')
    and (jsonb_typeof(e.symptom->'rating') = 'number'
         or (jsonb_typeof(e.symptom->'painBodyRatings') = 'object' and e.symptom->'painBodyRatings' <> '{}'::jsonb))
    and e.created_ms >= b.from_ms and e.created_ms < b.to_ms
)
select (select count(*) from pilot_people) as patients,
       (select count(*) from pilot_people) * (select weeks from bounds) as patient_weeks,
       (select count(*) from home_pain_weeks) as patient_weeks_with_home_pain_score,
       round(100.0 * (select count(*) from home_pain_weeks)
             / nullif((select count(*) from pilot_people) * (select weeks from bounds), 0), 1) as percent;


-- =====================================================================
-- KPI 2c. Visits with a home pain score in the 7 days before the visit.
-- One row per pilot patient, then one 'ALL' row.
-- A visit = an entry of kind 'visit', or a calendar entry with category 'Visit',
-- whose visit time (data.due) falls in the pilot dates.
-- "Before the visit" = a home pain score entered in the 7 x 24 hours up to the visit time.
-- Visits saved without a time may be stored at the start of that day [check with the team].
-- =====================================================================
with params as (
  select date '2026-11-02' as pilot_start,
         date '2026-12-27' as pilot_end,
         'Asia/Kolkata'::text as tz,
         'replace-with-a-private-word'::text as salt,
         array['00000000-0000-0000-0000-000000000000']::uuid[] as pilot_accounts
), bounds as (
  select *, (extract(epoch from (pilot_start::timestamp at time zone tz)) * 1000)::bigint as from_ms,
            (extract(epoch from ((pilot_end + 1)::timestamp at time zone tz)) * 1000)::bigint as to_ms
  from params
), pilot_people as (
  select p.id as person_id, left(md5(b.salt || p.id::text), 12) as patient_ref
  from public.healthathon_people p cross join bounds b
  where p.owner_id = any(b.pilot_accounts)
), entries as (
  select r.person_id, r.id, r.data->>'kind' as entry_kind,
         case when r.data->>'created' ~ '^[0-9]{1,15}$' then (r.data->>'created')::bigint end as created_ms,
         case when r.data->>'due' ~ '^[0-9]{1,15}$' then (r.data->>'due')::bigint end as due_ms,
         case when left(r.data->>'detail', 18) = E'sahara:symptom:v1\n' and pg_input_is_valid(substr(r.data->>'detail', 19), 'jsonb')
              then substr(r.data->>'detail', 19)::jsonb end as symptom,
         case when left(r.data->>'detail', 16) = E'sahara:entry:v1\n' and pg_input_is_valid(substr(r.data->>'detail', 17), 'jsonb')
              then substr(r.data->>'detail', 17)::jsonb end as workflow
  from public.healthathon_records r join pilot_people pp using (person_id)
  where r.kind = 'entry'
), visits as (
  select e.person_id, e.id as visit_id, e.due_ms
  from entries e cross join bounds b
  where e.due_ms >= b.from_ms and e.due_ms < b.to_ms
    and (e.entry_kind = 'visit'
         or (e.entry_kind = 'task' and e.workflow->>'type' = 'schedule' and e.workflow->>'category' = 'Visit'))
), home_pain as (
  select e.person_id, e.created_ms
  from entries e
  where e.entry_kind = 'checkin' and e.created_ms is not null
    and e.symptom->>'symptom' = 'Pain'
    and e.symptom->>'respondent' in ('patient', 'assisted-patient', 'caregiver')
    and (jsonb_typeof(e.symptom->'rating') = 'number'
         or (jsonb_typeof(e.symptom->'painBodyRatings') = 'object' and e.symptom->'painBodyRatings' <> '{}'::jsonb))
), per_patient as (
  select pp.patient_ref,
         count(v.visit_id) as visits,
         count(v.visit_id) filter (where exists (
           select 1 from home_pain h where h.person_id = v.person_id
             and h.created_ms <= v.due_ms and h.created_ms > v.due_ms - 7 * 24 * 3600 * 1000::bigint)) as visits_with_pain_score_last_7_days
  from pilot_people pp left join visits v using (person_id)
  group by pp.patient_ref
)
select patient_ref, visits, visits_with_pain_score_last_7_days
from (
  select 0 as sort, * from per_patient
  union all
  select 1, 'ALL (' || count(*) || ' patients)', sum(visits), sum(visits_with_pain_score_last_7_days) from per_patient
) t order by sort, patient_ref;


-- =====================================================================
-- KPI 3. Diary completion: days with at least one diary entry.
-- One row per pilot patient, then one 'ALL' row.
-- A diary entry = a check-in (this includes symptom notes) or a journal note,
-- counted on the day it was entered (data.created), in tz.
--   days_in_pilot   days from the later of pilot_start and the day the patient
--                   record was added, up to pilot_end
--   diary_days      days with at least one diary entry
--   checkin_days, journal_days   the same, split by type (a day can be in both)
-- =====================================================================
with params as (
  select date '2026-11-02' as pilot_start,
         date '2026-12-27' as pilot_end,
         'Asia/Kolkata'::text as tz,
         'replace-with-a-private-word'::text as salt,
         array['00000000-0000-0000-0000-000000000000']::uuid[] as pilot_accounts
), bounds as (
  select *, (extract(epoch from (pilot_start::timestamp at time zone tz)) * 1000)::bigint as from_ms,
            (extract(epoch from ((pilot_end + 1)::timestamp at time zone tz)) * 1000)::bigint as to_ms
  from params
), pilot_people as (
  select p.id as person_id, left(md5(b.salt || p.id::text), 12) as patient_ref,
         greatest(0, b.pilot_end - greatest(b.pilot_start, (to_timestamp(p.created_ms / 1000.0) at time zone b.tz)::date) + 1) as days_in_pilot
  from public.healthathon_people p cross join bounds b
  where p.owner_id = any(b.pilot_accounts)
), entries as (
  select r.person_id, r.data->>'kind' as entry_kind,
         case when r.data->>'created' ~ '^[0-9]{1,15}$' then (r.data->>'created')::bigint end as created_ms
  from public.healthathon_records r join pilot_people pp using (person_id)
  where r.kind = 'entry' and r.data->>'kind' in ('checkin', 'journal')
), diary as (
  select e.person_id, e.entry_kind, (to_timestamp(e.created_ms / 1000.0) at time zone b.tz)::date as day
  from entries e cross join bounds b
  where e.created_ms >= b.from_ms and e.created_ms < b.to_ms
), per_patient as (
  select pp.patient_ref, pp.days_in_pilot,
         count(distinct d.day) as diary_days,
         count(distinct d.day) filter (where d.entry_kind = 'checkin') as checkin_days,
         count(distinct d.day) filter (where d.entry_kind = 'journal') as journal_days,
         round(100.0 * count(distinct d.day) / nullif(pp.days_in_pilot, 0), 1) as percent_of_days
  from pilot_people pp left join diary d using (person_id)
  group by pp.patient_ref, pp.days_in_pilot
)
select patient_ref, days_in_pilot, diary_days, checkin_days, journal_days, percent_of_days
from (
  select 0 as sort, * from per_patient
  union all
  select 1, 'ALL (' || count(*) || ' patients)', sum(days_in_pilot), sum(diary_days), sum(checkin_days), sum(journal_days),
         round(100.0 * sum(diary_days) / nullif(sum(days_in_pilot), 0), 1)
  from per_patient
) t order by sort, patient_ref;
