begin;
create table public.healthathon_care_contacts (
 id uuid primary key, person_id uuid not null references public.healthathon_people(id) on delete cascade,
 display_name text not null check(length(btrim(display_name)) between 1 and 120),
 relationship text not null check(length(btrim(relationship)) between 1 and 80),
 role text not null check(role in ('family','caregiver','clinician')),
 phone text not null default '' check(phone='' or (length(phone)<=40 and phone ~ '^[+0-9 ()-]{7,40}$')),
 revision bigint not null default 1 check(revision>0), updated_at timestamptz not null default now()
);
create index care_contacts_person_idx on public.healthathon_care_contacts(person_id);
alter table public.healthathon_care_contacts enable row level security;
revoke all on public.healthathon_care_contacts from public,anon,authenticated;

create function healthathon_private.can_read_care_team(p_person uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select coalesce(healthathon_private.can_read(p_person),false) and
 (healthathon_private.is_owner(p_person) or healthathon_private.has_permission(p_person,'careTeam'))
$$;
revoke all on function healthathon_private.can_read_care_team(uuid) from public,anon,authenticated;

create function healthathon_private.list_care_contacts(p_person uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.can_read(p_person),false) then raise exception 'Care access is unavailable.' using errcode='42501'; end if;
 if not healthathon_private.can_read_care_team(p_person) then return jsonb_build_object('readable',false,'contacts','[]'::jsonb); end if;
 return jsonb_build_object('readable',true,'contacts',coalesce((select jsonb_agg(to_jsonb(c) order by c.display_name,c.id) from public.healthathon_care_contacts c where c.person_id=p_person),'[]'::jsonb));
end $$;

create function healthathon_private.save_care_contact(p_contact jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare p uuid:=(p_contact->>'person_id')::uuid; contact_id uuid:=(p_contact->>'id')::uuid; expected bigint:=(p_contact->>'revision')::bigint; saved public.healthathon_care_contacts;
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not coalesce(healthathon_private.is_owner(p),false) then raise exception 'Only the owner can manage care contacts.' using errcode='42501'; end if;
 if expected=0 then
  insert into public.healthathon_care_contacts(id,person_id,display_name,relationship,role,phone) values(contact_id,p,btrim(p_contact->>'display_name'),btrim(p_contact->>'relationship'),p_contact->>'role',coalesce(p_contact->>'phone','')) returning * into saved;
 else
  update public.healthathon_care_contacts set display_name=btrim(p_contact->>'display_name'),relationship=btrim(p_contact->>'relationship'),role=p_contact->>'role',phone=coalesce(p_contact->>'phone',''),revision=revision+1,updated_at=now() where id=contact_id and person_id=p and revision=expected returning * into saved;
  if not found then raise exception 'This contact changed. Refresh before saving.' using errcode='40001'; end if;
 end if;
 return to_jsonb(saved);
end $$;

create function healthathon_private.remove_care_contact(p_person uuid,p_id uuid,p_revision bigint) returns void
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not coalesce(healthathon_private.is_owner(p_person),false) then raise exception 'Only the owner can manage care contacts.' using errcode='42501'; end if;
 delete from public.healthathon_care_contacts where person_id=p_person and id=p_id and revision=p_revision;
 if not found then raise exception 'This contact changed. Refresh before removing it.' using errcode='40001'; end if;
end $$;

create function healthathon_private.care_team_share(p_person uuid,p_member uuid,p_enabled boolean) returns void
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not coalesce(healthathon_private.is_owner(p_person),false) or p_enabled is null then raise exception 'Only the owner can change contact sharing.' using errcode='42501'; end if;
 update public.healthathon_members set permissions=jsonb_set(coalesce(permissions,'{}'::jsonb),'{careTeam}',to_jsonb(p_enabled),true) where person_id=p_person and user_id=p_member;
 if not found then raise exception 'Sharing membership no longer exists.'; end if;
end $$;

create function healthathon_private.care_team_permissions(p_person uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not coalesce(healthathon_private.is_owner(p_person),false) then raise exception 'Only the owner can see sharing permissions.' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(jsonb_build_object('user_id',user_id,'care_team',coalesce(permissions->'careTeam'='true'::jsonb,false))) from public.healthathon_members where person_id=p_person),'[]'::jsonb);
end $$;

create function healthathon_private.invite_care_team(p_person uuid,p_edit boolean,p_care_team boolean) returns text
language plpgsql security definer set search_path='' as $$
declare code text;
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not coalesce(healthathon_private.is_owner(p_person),false) or p_care_team is null or p_edit is null then raise exception 'Only the owner can invite someone.' using errcode='42501'; end if;
 code:=healthathon_private.make_invite(p_person,p_edit);
 update healthathon_private.invites set permissions=jsonb_set(coalesce(permissions,'{}'::jsonb),'{careTeam}',to_jsonb(p_care_team),true) where person_id=p_person and code_hash=encode(extensions.digest(code,'sha256'),'hex');
 return code;
end $$;

revoke all on function healthathon_private.list_care_contacts(uuid),healthathon_private.save_care_contact(jsonb),healthathon_private.remove_care_contact(uuid,uuid,bigint),healthathon_private.care_team_share(uuid,uuid,boolean),healthathon_private.care_team_permissions(uuid),healthathon_private.invite_care_team(uuid,boolean,boolean) from public,anon,authenticated;
grant execute on function healthathon_private.list_care_contacts(uuid),healthathon_private.save_care_contact(jsonb),healthathon_private.remove_care_contact(uuid,uuid,bigint),healthathon_private.care_team_share(uuid,uuid,boolean),healthathon_private.care_team_permissions(uuid),healthathon_private.invite_care_team(uuid,boolean,boolean) to authenticated;
create function public.healthathon_list_care_contacts(p_person uuid) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.list_care_contacts(p_person)$$;
create function public.healthathon_save_care_contact(p_contact jsonb) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.save_care_contact(p_contact)$$;
create function public.healthathon_remove_care_contact(p_person uuid,p_id uuid,p_revision bigint) returns void language sql security invoker set search_path='' as $$select healthathon_private.remove_care_contact(p_person,p_id,p_revision)$$;
create function public.healthathon_care_team_share(p_person uuid,p_member uuid,p_enabled boolean) returns void language sql security invoker set search_path='' as $$select healthathon_private.care_team_share(p_person,p_member,p_enabled)$$;
create function public.healthathon_care_team_permissions(p_person uuid) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.care_team_permissions(p_person)$$;
create function public.healthathon_invite_care_team(p_person uuid,p_edit boolean,p_care_team boolean) returns text language sql security invoker set search_path='' as $$select healthathon_private.invite_care_team(p_person,p_edit,p_care_team)$$;
revoke all on function public.healthathon_list_care_contacts(uuid),public.healthathon_save_care_contact(jsonb),public.healthathon_remove_care_contact(uuid,uuid,bigint),public.healthathon_care_team_share(uuid,uuid,boolean),public.healthathon_care_team_permissions(uuid),public.healthathon_invite_care_team(uuid,boolean,boolean) from public,anon;
grant execute on function public.healthathon_list_care_contacts(uuid),public.healthathon_save_care_contact(jsonb),public.healthathon_remove_care_contact(uuid,uuid,bigint),public.healthathon_care_team_share(uuid,uuid,boolean),public.healthathon_care_team_permissions(uuid),public.healthathon_invite_care_team(uuid,boolean,boolean) to authenticated;
commit;
