-- Run only on the explicitly selected project. No public table or bill access.
begin;

create table if not exists public.saanthvana_report_settings (
  id boolean primary key default true check (id),
  proxy_secret_hash text not null check (proxy_secret_hash ~ '^[a-f0-9]{64}$')
);

create table if not exists public.saanthvana_centre_reports (
  id uuid primary key,
  session_hash text not null,
  centre_key text not null,
  centre_id text not null,
  hospital_name text not null check (char_length(hospital_name) between 2 and 160),
  district text not null check (char_length(district) between 2 and 80),
  address text not null check (char_length(address) between 5 and 400),
  latitude double precision,
  longitude double precision,
  visit_date date not null,
  outcome text not null check (outcome in ('received', 'unavailable')),
  oral_morphine text not null check (oral_morphine in ('available', 'unavailable', 'unknown')),
  reporter_role text not null check (reporter_role in ('patient', 'caregiver', 'doctor')),
  receipt_path text,
  receipt_hash text unique,
  published boolean not null default false,
  created_at timestamptz not null default now(),
  unique (session_hash, centre_key, visit_date),
  check ((latitude is null and longitude is null) or (latitude between 11 and 19 and longitude between 74 and 79)),
  check ((receipt_path is null) = (receipt_hash is null))
);
create index if not exists saanthvana_reports_published_date on public.saanthvana_centre_reports (created_at desc) where published;

create table if not exists public.saanthvana_report_attempts (
  id uuid primary key,
  session_hash text not null,
  ip_hash text,
  created_at timestamptz not null default now()
);
create index if not exists saanthvana_report_attempt_date on public.saanthvana_report_attempts (created_at);

alter table public.saanthvana_report_settings enable row level security;
alter table public.saanthvana_centre_reports enable row level security;
alter table public.saanthvana_report_attempts enable row level security;
revoke all on public.saanthvana_report_settings, public.saanthvana_centre_reports, public.saanthvana_report_attempts from public, anon, authenticated;
grant select, insert, update, delete on public.saanthvana_report_settings, public.saanthvana_centre_reports, public.saanthvana_report_attempts to service_role;

-- Invoker preserves the service-role boundary; anon/authenticated cannot execute.
create or replace function public.saanthvana_reserve_report(p_report jsonb, p_ip_hash text)
returns uuid language plpgsql security invoker set search_path = '' as $$
declare
  v_id uuid := (p_report->>'id')::uuid;
  v_session text := p_report->>'session_hash';
  v_day timestamptz := now() - interval '24 hours';
begin
  -- Serializes only this feature's small write quota, across all Edge replicas.
  perform pg_advisory_xact_lock(1837410291);
  delete from public.saanthvana_report_attempts where created_at < now() - interval '2 days';
  if (select count(*) from public.saanthvana_report_attempts where created_at >= v_day) >= 120
     or (select count(*) from public.saanthvana_report_attempts where created_at >= v_day and session_hash = v_session) >= 5
     or (p_ip_hash is not null and (select count(*) from public.saanthvana_report_attempts where created_at >= v_day and ip_hash = p_ip_hash) >= 20)
  then raise exception 'REPORT_RATE_LIMIT' using errcode = 'P0001'; end if;
  insert into public.saanthvana_centre_reports (
    id, session_hash, centre_key, centre_id, hospital_name, district, address, latitude, longitude,
    visit_date, outcome, oral_morphine, reporter_role, receipt_path, receipt_hash
  ) values (
    v_id, v_session, p_report->>'centre_key', p_report->>'centre_id', p_report->>'hospital_name',
    p_report->>'district', p_report->>'address', (p_report->>'latitude')::double precision,
    (p_report->>'longitude')::double precision, (p_report->>'visit_date')::date, p_report->>'outcome',
    p_report->>'oral_morphine', p_report->>'reporter_role', p_report->>'receipt_path', p_report->>'receipt_hash'
  );
  insert into public.saanthvana_report_attempts (id, session_hash, ip_hash) values (v_id, v_session, p_ip_hash);
  return v_id;
end;
$$;
revoke all on function public.saanthvana_reserve_report(jsonb, text) from public, anon, authenticated;
grant execute on function public.saanthvana_reserve_report(jsonb, text) to service_role;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('saanthvana-bills', 'saanthvana-bills', false, 4194304, array['image/jpeg', 'image/png', 'image/webp', 'application/pdf'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

-- Restrictive policies protect this bucket even if the selected project has broad
-- permissive policies for unrelated application buckets.
drop policy if exists saanthvana_bills_private on storage.objects;
create policy saanthvana_bills_private on storage.objects as restrictive for all to anon, authenticated
using (bucket_id <> 'saanthvana-bills') with check (bucket_id <> 'saanthvana-bills');
commit;
