-- Emergency (ED) card tokens for the Sahara ECTPR wallet card and the public /ed/<token> page.
-- Tech spec §7: random >= 128-bit revocable QR tokens, no personal data in the URL,
-- append-only access log, break-glass logged with a reason.
-- Only the server route /api/ed-card reads or writes these tables, with the secret
-- (service-role) key. RLS is on and there are no anon/authenticated policies, so the
-- publishable key can do nothing here.
begin;

create table if not exists public.sahara_ed_cards (
  token text primary key check (token ~ '^[A-Za-z0-9_-]{22,64}$'),
  -- Opaque reference chosen by the issuing app (not a name, phone or UHID).
  patient_ref text not null check (length(patient_ref) between 1 and 120),
  record_id text not null check (length(record_id) between 1 and 120),
  version integer not null check (version >= 1),
  -- Signed ECTPR as it was when the card was issued (synthetic patients only for the hackathon).
  snapshot jsonb not null check (jsonb_typeof(snapshot) = 'object' and pg_column_size(snapshot) <= 200000),
  -- SHA-256 (hex) of the issuing device's management key. Revoking needs the key itself.
  manage_key_hash text not null check (manage_key_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  revoked_reason text check (revoked_reason is null or length(revoked_reason) <= 200)
);

create index if not exists sahara_ed_cards_patient_ref_idx on public.sahara_ed_cards (patient_ref) where revoked_at is null;

create table if not exists public.sahara_ed_access_log (
  id bigint generated always as identity primary key,
  token text not null references public.sahara_ed_cards (token),
  at timestamptz not null default now(),
  kind text not null check (kind in ('QR_VIEW', 'BREAK_GLASS')),
  reason text check (reason is null or length(reason) <= 500),
  ua text check (ua is null or length(ua) <= 300),
  sms_status text check (sms_status is null or length(sms_status) <= 120)
);

create index if not exists sahara_ed_access_log_token_idx on public.sahara_ed_access_log (token, at desc);

alter table public.sahara_ed_cards enable row level security;
alter table public.sahara_ed_access_log enable row level security;
-- No policies on purpose: anon and authenticated roles get nothing.
revoke all on public.sahara_ed_cards from anon, authenticated;
revoke all on public.sahara_ed_access_log from anon, authenticated;

-- Append-only access log: no updates or deletes, even for the service role.
create or replace function public.sahara_ed_access_log_append_only()
returns trigger language plpgsql security invoker set search_path = '' as $$
begin
  raise exception 'sahara_ed_access_log is append-only';
end;
$$;

drop trigger if exists sahara_ed_access_log_no_change on public.sahara_ed_access_log;
create trigger sahara_ed_access_log_no_change
  before update or delete on public.sahara_ed_access_log
  for each row execute function public.sahara_ed_access_log_append_only();

-- Card rows are never deleted and their content never changes; only revocation may be set, once.
create or replace function public.sahara_ed_cards_revoke_only()
returns trigger language plpgsql security invoker set search_path = '' as $$
begin
  if tg_op = 'DELETE' then raise exception 'sahara_ed_cards rows are never deleted; revoke instead'; end if;
  if new.token is distinct from old.token or new.patient_ref is distinct from old.patient_ref
    or new.record_id is distinct from old.record_id or new.version is distinct from old.version
    or new.snapshot is distinct from old.snapshot or new.manage_key_hash is distinct from old.manage_key_hash
    or new.created_at is distinct from old.created_at then
    raise exception 'sahara_ed_cards content is immutable; issue a new card';
  end if;
  if old.revoked_at is not null and new.revoked_at is distinct from old.revoked_at then
    raise exception 'a revoked card stays revoked';
  end if;
  return new;
end;
$$;

drop trigger if exists sahara_ed_cards_immutable on public.sahara_ed_cards;
create trigger sahara_ed_cards_immutable
  before update or delete on public.sahara_ed_cards
  for each row execute function public.sahara_ed_cards_revoke_only();

commit;
