-- OWNER APPLIES. Not applied by the app lane (28 Oct 2026, meeting decision 10).
-- "Suggest a centre that's missing" on Nearby. A signed-in person adds a pending suggestion; only its author and
-- Sahara staff reviewers (staff_roles 'directory_reviewer' or 'clinical_verifier'; 'admin' is included by
-- has_staff_role) can read it. Nothing here is public: an approved suggestion is added to the bundled directory by
-- the team in a later release. Morphine availability is NOT collected (public morphine reports are on legal hold).
-- Until this is applied, the app keeps each suggestion in its send-later queue and retries it.
begin;

create table public.sahara_centre_suggestions (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null check (length(btrim(name)) between 3 and 120),
  city text not null check (length(btrim(city)) between 2 and 80),
  state text not null check (length(btrim(state)) between 2 and 80),
  address text not null default '' check (length(address) <= 300),
  phone text not null default '' check (phone = '' or phone ~ '^[0-9 +()-]{6,20}$'),
  note text not null default '' check (length(note) <= 500),
  saved_at timestamptz not null,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  reviewed_by uuid references auth.users(id) on delete set null,
  reviewed_at timestamptz,
  review_note text not null default '' check (length(review_note) <= 500),
  created_at timestamptz not null default now()
);
create index sahara_centre_suggestions_user_time on public.sahara_centre_suggestions(user_id, created_at);
create index sahara_centre_suggestions_status on public.sahara_centre_suggestions(status, created_at);
create index sahara_centre_suggestions_reviewer on public.sahara_centre_suggestions(reviewed_by);

alter table public.sahara_centre_suggestions enable row level security;
revoke all on public.sahara_centre_suggestions from public, anon, authenticated;
grant select on public.sahara_centre_suggestions to authenticated;
-- The author can set only what they typed; status and review columns keep their defaults.
grant insert (id, name, city, state, address, phone, note, saved_at) on public.sahara_centre_suggestions to authenticated;

create policy centre_suggestions_account_open on public.sahara_centre_suggestions
as restrictive for all to authenticated
using ((select healthathon_private.require_open_account()))
with check ((select healthathon_private.require_open_account()));
create policy centre_suggestions_insert_own on public.sahara_centre_suggestions
for insert to authenticated
with check (user_id = (select auth.uid()) and status = 'pending' and reviewed_by is null and reviewed_at is null);
create policy centre_suggestions_read_own_or_staff on public.sahara_centre_suggestions
for select to authenticated
using (user_id = (select auth.uid())
  or (select healthathon_private.has_staff_role('directory_reviewer'))
  or (select healthathon_private.has_staff_role('clinical_verifier')));

-- Per-account limit, set server-side so a modified app cannot flood the review list.
create function healthathon_private.limit_centre_suggestions() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select count(*) from public.sahara_centre_suggestions where user_id = new.user_id and created_at >= now() - interval '24 hours') >= 20 then
    raise exception 'Too many suggestions today. It stays on this phone; it will be tried again tomorrow.' using errcode = 'P0001';
  end if;
  new.created_at := now();
  return new;
end; $$;
revoke all on function healthathon_private.limit_centre_suggestions() from public, anon, authenticated;
create trigger sahara_centre_suggestions_limit before insert on public.sahara_centre_suggestions
for each row execute function healthathon_private.limit_centre_suggestions();

-- Review by Sahara staff only. A reviewer cannot review their own suggestion. Recorded with who, when and why.
create function public.sahara_review_centre_suggestion(p_id uuid, p_decision text, p_note text)
returns public.sahara_centre_suggestions language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid(); rec public.sahara_centre_suggestions;
begin
  if caller is null or not (healthathon_private.has_staff_role('directory_reviewer') or healthathon_private.has_staff_role('clinical_verifier')) then
    raise exception 'Only the Sahara team can review suggestions.' using errcode = '42501';
  end if;
  if p_decision not in ('approved','rejected') then raise exception 'Choose approved or rejected.' using errcode = 'P0001'; end if;
  if length(btrim(coalesce(p_note, ''))) not between 3 and 500 then raise exception 'Add what you checked (3 to 500 letters).' using errcode = 'P0001'; end if;
  select * into rec from public.sahara_centre_suggestions where id = p_id for update;
  if not found then raise exception 'Suggestion not found.' using errcode = 'P0001'; end if;
  if rec.user_id = caller then raise exception 'You cannot review your own suggestion.' using errcode = '42501'; end if;
  if rec.status <> 'pending' then raise exception 'This suggestion was already reviewed.' using errcode = 'P0001'; end if;
  update public.sahara_centre_suggestions set status = p_decision, reviewed_by = caller, reviewed_at = now(), review_note = btrim(p_note)
  where id = p_id returning * into rec;
  return rec;
end; $$;
revoke all on function public.sahara_review_centre_suggestion(uuid, text, text) from public, anon;
grant execute on function public.sahara_review_centre_suggestion(uuid, text, text) to authenticated;

commit;
