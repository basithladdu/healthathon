-- OWNER APPLIES to the Android project (kjnhtakqzboayqorrlue). Not applied by the GOC Gemini lane (5 Nov 2026).
-- Goals-of-care analysis: after a recorded goals-of-care conversation is transcribed, the website route /api/goc-transcribe
-- (action "analyze", prototype/app/goc-analyze-server.ts) has Gemini map what was said onto the ECTPR form fields and saves the
-- checked result here AS THE SIGNED-IN ACCOUNT (its own access token + the publishable key), so row-level security applies.
-- No service-role key is used. Until this is applied the route still returns the reading and says saved: false.
-- Contents are suggestions with word-for-word quotes for a doctor to confirm; nothing here changes a care plan.
-- Rows cascade when the auth user is deleted. Re-sending the same id keeps the first row (insert ... on conflict do nothing).
begin;

create table public.sahara_goc_analyses (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  -- The app's own ids for the patient and the conversation, when it sends them (not foreign keys: they may be local ids).
  patient_ref text check (patient_ref is null or patient_ref ~ '^[A-Za-z0-9._:-]{1,120}$'),
  conversation_ref text check (conversation_ref is null or conversation_ref ~ '^[A-Za-z0-9._:-]{1,120}$'),
  -- [{startSec, speaker: DOCTOR | PATIENT_PARTY | UNKNOWN, lang, text}]
  transcript jsonb not null check (jsonb_typeof(transcript) = 'array' and jsonb_array_length(transcript) between 1 and 6000),
  transcript_sha256 text not null check (transcript_sha256 ~ '^[0-9a-f]{64}$'),
  -- GocSuggestion[] (prototype/app/goc-extract.ts): {field, values, ruledOut, quote, source: 'ai', at, who}
  suggestions jsonb not null default '[]'::jsonb check (jsonb_typeof(suggestions) = 'array' and jsonb_array_length(suggestions) <= 200),
  own_words jsonb not null default '[]'::jsonb check (jsonb_typeof(own_words) = 'array' and jsonb_array_length(own_words) <= 100),
  unacceptable_outcomes jsonb not null default '[]'::jsonb check (jsonb_typeof(unacceptable_outcomes) = 'array' and jsonb_array_length(unacceptable_outcomes) <= 100),
  not_discussed jsonb not null default '[]'::jsonb check (jsonb_typeof(not_discussed) = 'array'),
  model text not null check (length(model) between 1 and 100),
  transcribed_by text not null default '' check (length(transcribed_by) <= 120),
  language text not null default '' check (length(language) <= 12),
  analyzed_at timestamptz not null,
  created_at timestamptz not null default now(),
  check (octet_length(transcript::text) <= 2000000)
);
create index sahara_goc_analyses_user_time on public.sahara_goc_analyses(user_id, created_at desc);
create index sahara_goc_analyses_patient on public.sahara_goc_analyses(user_id, patient_ref);

alter table public.sahara_goc_analyses enable row level security;
revoke all on public.sahara_goc_analyses from public, anon, authenticated;
grant select, insert on public.sahara_goc_analyses to authenticated;

-- Each account adds and reads only its own analyses. Append-only: no update or delete policy.
create policy goc_analyses_read_own on public.sahara_goc_analyses for select to authenticated
  using (user_id = (select auth.uid()));
create policy goc_analyses_add_own on public.sahara_goc_analyses for insert to authenticated
  with check (user_id = (select auth.uid()));

commit;
