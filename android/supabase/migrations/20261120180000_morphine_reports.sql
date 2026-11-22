-- OWNER APPLIES. "Could you help another family? Where did you get it?" (20-21 Nov 2026).
-- After a family marks a dose of oral morphine as taken, the app asks (at most monthly per medicine) where it came from: a centre
-- from the bundled directory or a place they type. Only the place is stored. The report is NOT linked to the account: the server
-- keeps a one-way code of the account only to limit reports per day, and nobody (staff included) can turn it back into an account.
-- Nothing is public. Under the NDPS Act only recognised medical institutions (RMIs) may dispense oral morphine, so the team
-- shows a centre to other families only after checking it is a recognised institution; typed places are never published as such.
-- Reports arrive through sahara_report_morphine (one call, safe to repeat). Until this is applied, the app keeps each report queued.
begin;

create table public.sahara_morphine_reports (
  id uuid primary key,
  reporter text not null check (reporter ~ '^[0-9a-f]{64}$'),
  centre_id text not null default '' check (length(centre_id) <= 200),
  centre_name text not null default '' check (length(centre_name) <= 200),
  city text not null default '' check (length(city) <= 80),
  state text not null default '' check (length(state) <= 80),
  place text not null default '' check (length(place) <= 160),
  saved_at timestamptz not null,
  status text not null default 'pending' check (status in ('pending','confirmed','rejected')),
  recognised_institution boolean not null default false,
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz,
  review_note text not null default '' check (length(review_note) <= 500),
  created_at timestamptz not null default now(),
  check (length(btrim(centre_id)) > 0 or length(btrim(place)) >= 3)
);
create index sahara_morphine_reports_reporter_time on public.sahara_morphine_reports(reporter, created_at);
create index sahara_morphine_reports_centre on public.sahara_morphine_reports(centre_id, status);

alter table public.sahara_morphine_reports enable row level security;
revoke all on public.sahara_morphine_reports from public, anon, authenticated;
grant select on public.sahara_morphine_reports to authenticated;
-- Only Sahara staff reviewers read reports. Nobody reads their own: there is no account on the row.
create policy morphine_reports_staff_read on public.sahara_morphine_reports
for select to authenticated
using ((select healthathon_private.has_staff_role('directory_reviewer'))
  or (select healthathon_private.has_staff_role('clinical_verifier')));

-- One report. Safe to repeat with the same id. Limit: 10 a day per account, counted on the one-way code.
create function public.sahara_report_morphine(p_report jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid(); code text;
begin
  if caller is null then raise exception 'Sign in first.' using errcode = '42501'; end if;
  perform healthathon_private.require_open_account();
  code := encode(extensions.digest('sahara-morphine-report:' || caller::text, 'sha256'), 'hex');
  if exists (select 1 from public.sahara_morphine_reports where id = (p_report->>'id')::uuid) then return; end if;
  if (select count(*) from public.sahara_morphine_reports where reporter = code and created_at >= now() - interval '24 hours') >= 10 then
    raise exception 'Too many reports today. It stays on this phone; it will be tried again tomorrow.' using errcode = 'P0001';
  end if;
  insert into public.sahara_morphine_reports(id, reporter, centre_id, centre_name, city, state, place, saved_at)
  values ((p_report->>'id')::uuid, code,
    left(coalesce(p_report->>'centre_id', ''), 200), left(coalesce(p_report->>'centre_name', ''), 200),
    left(coalesce(p_report->>'city', ''), 80), left(coalesce(p_report->>'state', ''), 80),
    left(btrim(coalesce(p_report->>'place', '')), 160), coalesce((p_report->>'saved_at')::timestamptz, now()));
end; $$;
revoke all on function public.sahara_report_morphine(jsonb) from public, anon;
grant execute on function public.sahara_report_morphine(jsonb) to authenticated;

commit;
