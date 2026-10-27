-- OWNER APPLIES (Saanthvana project). Not applied by the app or by any agent.
-- Account lane, 27 Oct 2026:
--   1. Each membership remembers which invitation created it, so the owner's
--      "Joined" label comes from the server instead of a count-based guess.
--   2. healthathon_member_details: join date, invitation and access end for the owner.
--   3. healthathon_set_member_edit: the owner changes "can read" / "can add and change".
--   4. healthathon_delete_my_account: the signed-in person deletes their own Sahara
--      data and sign-in from the app (DPDP withdrawal/erasure), reusing close_account.
-- Until this is applied the app keeps working: it says that these need a server update.
begin;

alter table public.healthathon_members add column if not exists invite_id uuid;

-- Same as 20261019151322 except that the membership keeps invitation.id.
create or replace function healthathon_private.use_invite(p_code text) returns uuid
language plpgsql security definer set search_path='' as $$
declare invitation healthathon_private.invites;
begin
 perform healthathon_private.require_open_account();
 if p_code !~ '^[a-f0-9]{48}$' then raise exception 'Invalid invitation'; end if;
 select * into invitation from healthathon_private.invites where code_hash=encode(extensions.digest(p_code,'sha256'),'hex') and expires_at>now() for update;
 if not found then raise exception 'Invitation expired or already used.'; end if;
 if healthathon_private.is_owner(invitation.person_id) then raise exception 'Use a separate account to accept an invitation.'; end if;
 if invitation.role='clinician' and not exists(select 1 from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved') then raise exception 'Clinician verification must be approved before joining the care team.' using errcode='42501'; end if;
 insert into public.healthathon_members(person_id,user_id,can_edit,role,permissions,expires_at,consented_at,invite_id)
 values(invitation.person_id,auth.uid(),invitation.can_edit,invitation.role,invitation.permissions,invitation.member_expires_at,now(),invitation.id)
 on conflict(person_id,user_id) do update set can_edit=excluded.can_edit,role=excluded.role,permissions=excluded.permissions,expires_at=excluded.expires_at,consented_at=excluded.consented_at,invite_id=excluded.invite_id;
 delete from healthathon_private.invites where code_hash=invitation.code_hash;
 return invitation.person_id;
end $$;

create function healthathon_private.member_details(p_person uuid)
returns table(user_id uuid,invite_id uuid,joined_at timestamptz,expires_at timestamptz)
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null or not healthathon_private.is_owner(p_person) then raise exception 'Only the owner can see the sharing list.' using errcode='42501'; end if;
 return query select m.user_id,m.invite_id,m.consented_at,m.expires_at from public.healthathon_members m where m.person_id=p_person;
end $$;
create function public.healthathon_member_details(p_person uuid)
returns table(user_id uuid,invite_id uuid,joined_at timestamptz,expires_at timestamptz)
language sql security invoker set search_path='' as $$ select * from healthathon_private.member_details(p_person) $$;

create function healthathon_private.set_member_edit(p_person uuid,p_user uuid,p_edit boolean) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not healthathon_private.is_owner(p_person) then raise exception 'Only the owner can change access.' using errcode='42501'; end if;
 if p_edit is null then raise exception 'Choose the access.'; end if;
 update public.healthathon_members set can_edit=p_edit where person_id=p_person and user_id=p_user;
 if not found then raise exception 'This person no longer has access. Refresh the list.'; end if;
 return p_edit;
end $$;
create function public.healthathon_set_member_edit(p_person uuid,p_user uuid,p_edit boolean) returns boolean
language sql security invoker set search_path='' as $$ select healthathon_private.set_member_edit(p_person,p_user,p_edit) $$;

-- Removes the caller's Sahara data the same way support's close_account does, plus
-- their clinician request and shared drafts, then tries to remove the sign-in itself.
-- If another table still needs the sign-in (or auth.users cannot be changed from SQL),
-- the closure marker stays, Sahara access stays closed, and login_removed is false.
create function healthathon_private.delete_my_account() returns jsonb
language plpgsql security definer set search_path='' as $$
declare caller uuid := auth.uid(); result jsonb; removed boolean := false;
begin
 if caller is null then raise exception 'Sign in to continue.' using errcode='42501'; end if;
 delete from public.healthathon_care_drafts where author_id=caller;
 delete from public.healthathon_clinician_requests where user_id=caller;
 result := healthathon_private.close_account(caller);
 begin
  delete from auth.users where id=caller;
  removed := true;
 exception when foreign_key_violation or insufficient_privilege then removed := false;
 end;
 return result || jsonb_build_object('login_removed',removed);
end $$;
create function public.healthathon_delete_my_account() returns jsonb
language sql security invoker set search_path='' as $$ select healthathon_private.delete_my_account() $$;

revoke all on function healthathon_private.member_details(uuid),healthathon_private.set_member_edit(uuid,uuid,boolean),healthathon_private.delete_my_account() from public,anon;
grant execute on function healthathon_private.member_details(uuid),healthathon_private.set_member_edit(uuid,uuid,boolean),healthathon_private.delete_my_account() to authenticated;
revoke all on function public.healthathon_member_details(uuid),public.healthathon_set_member_edit(uuid,uuid,boolean),public.healthathon_delete_my_account() from public,anon;
grant execute on function public.healthathon_member_details(uuid),public.healthathon_set_member_edit(uuid,uuid,boolean),public.healthathon_delete_my_account() to authenticated;

commit;
