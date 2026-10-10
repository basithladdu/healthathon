begin;

-- Sahara-only closure. No Auth users, other applications or Storage objects are removed.
create table healthathon_private.closed_accounts (
  user_id uuid primary key references auth.users(id) on delete cascade,
  closed_at timestamptz not null default now()
);
alter table healthathon_private.closed_accounts enable row level security;
revoke all on healthathon_private.closed_accounts from public, anon, authenticated, service_role;

-- This function checks only the caller, never a supplied UUID. Its private marker
-- table has no client grants. Shared advisory locks let normal requests proceed
-- together while serializing them against support's exclusive closure lock.
create function healthathon_private.require_open_account() returns boolean
language plpgsql volatile security definer set search_path='' as $$
declare caller uuid := auth.uid();
begin
  if caller is null then raise exception 'Sign in to continue.' using errcode='42501'; end if;
  perform pg_catalog.pg_advisory_xact_lock_shared(731044821, pg_catalog.hashtext(caller::text));
  if exists(select 1 from healthathon_private.closed_accounts where user_id=caller)
     or not exists(select 1 from auth.users where id=caller) then
    raise exception 'Your Sahara account is closed. Contact workwithdevit@gmail.com for help.' using errcode='P0001';
  end if;
  return true;
end; $$;
revoke all on function healthathon_private.require_open_account() from public, anon;
grant execute on function healthathon_private.require_open_account() to authenticated;

-- The restrictive policies also protect owner shortcuts and future permissive
-- policies. Existing read/edit/ownership rules continue to apply unchanged.
create policy sahara_people_account_open on public.healthathon_people
as restrictive for all to authenticated
using ((select healthathon_private.require_open_account()))
with check ((select healthathon_private.require_open_account()));
create policy sahara_members_account_open on public.healthathon_members
as restrictive for all to authenticated
using ((select healthathon_private.require_open_account()))
with check ((select healthathon_private.require_open_account()));
create policy sahara_records_account_open on public.healthathon_records
as restrictive for all to authenticated
using ((select healthathon_private.require_open_account()))
with check ((select healthathon_private.require_open_account()));

-- The existing SECURITY DEFINER save, invite and member-list functions call
-- these permission helpers before touching data, so they cannot bypass closure.
create or replace function healthathon_private.can_read(p_person uuid) returns boolean
language plpgsql volatile security definer set search_path='' as $$
begin
  perform healthathon_private.require_open_account();
  return (
    exists(select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid()) or
    exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid())
  );
end;
$$;
create or replace function healthathon_private.can_edit(p_person uuid) returns boolean
language plpgsql volatile security definer set search_path='' as $$
begin
  perform healthathon_private.require_open_account();
  return (
    exists(select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid()) or
    exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid() and can_edit)
  );
end;
$$;
create or replace function healthathon_private.is_owner(p_person uuid) returns boolean
language plpgsql volatile security definer set search_path='' as $$
begin
  perform healthathon_private.require_open_account();
  return exists(
    select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid()
  );
end;
$$;

-- Joining does not use a person permission helper, so check before consuming
-- the invitation. A rejected closed account must leave that code usable.
create or replace function healthathon_private.use_invite(p_code text) returns uuid
language plpgsql security definer set search_path='' as $$
declare invitation healthathon_private.invites;
begin
  perform healthathon_private.require_open_account();
  if p_code !~ '^[a-f0-9]{48}$' then raise exception 'Invalid invitation'; end if;
  delete from healthathon_private.invites
  where code_hash=encode(extensions.digest(p_code,'sha256'),'hex') and expires_at>now()
  returning * into invitation;
  if not found then raise exception 'Invitation expired or already used.'; end if;
  insert into public.healthathon_members(person_id,user_id,can_edit)
  values(invitation.person_id,auth.uid(),invitation.can_edit)
  on conflict(person_id,user_id) do update set can_edit=excluded.can_edit;
  return invitation.person_id;
end; $$;

-- Support must verify control of the registered email before calling this RPC.
-- Function execution is the only service-role mutation surface for the marker.
create function healthathon_private.close_account(p_user uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare
  removed_people bigint;
  removed_memberships bigint;
  closed timestamptz;
begin
  if p_user is null then raise exception 'Choose the verified Sahara account.' using errcode='22004'; end if;
  perform pg_catalog.pg_advisory_xact_lock(731044821, pg_catalog.hashtext(p_user::text));
  if not exists(select 1 from auth.users where id=p_user) then
    raise exception 'The verified account was not found.' using errcode='P0002';
  end if;
  insert into healthathon_private.closed_accounts(user_id) values(p_user)
  on conflict(user_id) do nothing;
  select closed_at into closed from healthathon_private.closed_accounts where user_id=p_user;
  delete from public.healthathon_members where user_id=p_user;
  get diagnostics removed_memberships = row_count;
  -- Existing foreign keys cascade only this owner's records, memberships and invites.
  delete from public.healthathon_people where owner_id=p_user;
  get diagnostics removed_people = row_count;
  return jsonb_build_object('closed_at',closed,'removed_people',removed_people,'removed_memberships',removed_memberships);
end; $$;
revoke all on function healthathon_private.close_account(uuid) from public, anon, authenticated;
grant usage on schema healthathon_private to service_role;
grant execute on function healthathon_private.close_account(uuid) to service_role;

create function public.healthathon_close_account(p_user uuid) returns jsonb
language sql security invoker set search_path='' as $$
  select healthathon_private.close_account(p_user)
$$;
revoke all on function public.healthathon_close_account(uuid) from public, anon, authenticated;
grant execute on function public.healthathon_close_account(uuid) to service_role;

commit;
