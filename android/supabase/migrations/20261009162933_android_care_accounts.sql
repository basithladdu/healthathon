begin;
create schema if not exists healthathon_private;
revoke all on schema healthathon_private from public, anon;
grant usage on schema healthathon_private to authenticated;

create table public.healthathon_people (
  id uuid primary key,
  owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null check (char_length(btrim(name)) between 1 and 100),
  created_ms bigint not null,
  created_at timestamptz not null default now()
);
create index healthathon_people_owner on public.healthathon_people(owner_id);
create table public.healthathon_members (
  person_id uuid not null references public.healthathon_people(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  can_edit boolean not null default false,
  primary key(person_id,user_id)
);
create index healthathon_members_user on public.healthathon_members(user_id);
create table public.healthathon_records (
  id uuid not null,
  kind text not null check (kind in ('entry','note','ack')),
  person_id uuid not null references public.healthathon_people(id) on delete cascade,
  data jsonb not null check (jsonb_typeof(data)='object' and octet_length(data::text) <= 131072),
  revision bigint not null default 1 check (revision > 0),
  updated_at timestamptz not null default now(),
  primary key(kind,id)
);
create index healthathon_records_person on public.healthathon_records(person_id);
create unique index healthathon_note_versions on public.healthathon_records(person_id,((data->>'version')::integer)) where kind='note';
create table healthathon_private.invites (
  code_hash text primary key,
  person_id uuid not null references public.healthathon_people(id) on delete cascade,
  can_edit boolean not null,
  expires_at timestamptz not null default now() + interval '72 hours'
);
alter table public.healthathon_people enable row level security;
alter table public.healthathon_members enable row level security;
alter table public.healthathon_records enable row level security;
alter table healthathon_private.invites enable row level security;
revoke all on public.healthathon_people, public.healthathon_members, public.healthathon_records from public, anon, authenticated;
grant select,insert,update,delete on public.healthathon_people to authenticated;
grant select,delete on public.healthathon_records to authenticated;
grant select,delete on public.healthathon_members to authenticated;

create function healthathon_private.can_read(p_person uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and (
    exists(select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid()) or
    exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid())
  )
$$;
create function healthathon_private.can_edit(p_person uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and (
    exists(select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid()) or
    exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid() and can_edit)
  )
$$;
create function healthathon_private.is_owner(p_person uuid) returns boolean
language sql stable security definer set search_path='' as $$
  select auth.uid() is not null and exists(select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid())
$$;
revoke all on function healthathon_private.can_read(uuid),healthathon_private.can_edit(uuid),healthathon_private.is_owner(uuid) from public,anon;
grant execute on function healthathon_private.can_read(uuid),healthathon_private.can_edit(uuid),healthathon_private.is_owner(uuid) to authenticated;

create policy people_read on public.healthathon_people for select to authenticated using (healthathon_private.can_read(id));
create policy people_add on public.healthathon_people for insert to authenticated with check (owner_id=(select auth.uid()));
create policy people_change on public.healthathon_people for update to authenticated using (owner_id=(select auth.uid())) with check (owner_id=(select auth.uid()));
create policy people_remove on public.healthathon_people for delete to authenticated using (owner_id=(select auth.uid()));
create policy members_read on public.healthathon_members for select to authenticated using (user_id=(select auth.uid()) or healthathon_private.is_owner(person_id));
create policy members_remove on public.healthathon_members for delete to authenticated using (healthathon_private.is_owner(person_id));
create policy records_read on public.healthathon_records for select to authenticated using (healthathon_private.can_read(person_id));
create policy records_add on public.healthathon_records for insert to authenticated with check (healthathon_private.can_edit(person_id) or (kind='ack' and healthathon_private.can_read(person_id)));
create policy records_change on public.healthathon_records for update to authenticated using (kind='entry' and healthathon_private.can_edit(person_id)) with check (kind='entry' and healthathon_private.can_edit(person_id));
create policy records_remove on public.healthathon_records for delete to authenticated using (kind='entry' and healthathon_private.can_edit(person_id));

create function healthathon_private.save_record(p_id uuid,p_person uuid,p_kind text,p_data jsonb,p_revision bigint)
returns public.healthathon_records language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_records;
begin
  if auth.uid() is null then raise exception 'Sign in to save.' using errcode='42501'; end if;
  if p_kind is null or p_kind not in ('entry','note','ack') or p_data is null or jsonb_typeof(p_data)<>'object' then raise exception 'Invalid record'; end if;
  if not healthathon_private.can_edit(p_person) and not (p_kind='ack' and healthathon_private.can_read(p_person)) then raise exception 'You cannot change these records.' using errcode='42501'; end if;
  perform 1 from public.healthathon_people where id=p_person for update;
  if p_kind='entry' and (coalesce(p_data->>'kind','') not in ('medicine','visit','task','checkin','journal') or length(btrim(coalesce(p_data->>'title','')))=0 or length(p_data->>'title')>200 or length(coalesce(p_data->>'detail',''))>4000) then raise exception 'Invalid care entry'; end if;
  if p_kind='note' and (coalesce((p_data->>'version')::integer,0)<1 or length(btrim(coalesce(p_data->>'source','')))=0 or length(p_data->>'source')>12000 or length(btrim(coalesce(p_data->>'doctor','')))=0 or coalesce(p_data->>'contentHash','') !~ '^[a-f0-9]{64}$') then raise exception 'Review the care note before saving'; end if;
  if p_kind='note' and (length(coalesce(p_data->>'priorities',''))>4000 or length(coalesce(p_data->>'participants',''))>4000 or length(coalesce(p_data->>'topics',''))>4000 or length(coalesce(p_data->>'questions',''))>4000 or length(coalesce(p_data->>'nextSteps',''))>4000) then raise exception 'Please shorten this care note'; end if;
  if p_kind='ack' and (length(btrim(coalesce(p_data->>'name','')))=0 or length(p_data->>'name')>100 or coalesce(p_data->>'relationship','') not in ('Patient','Family / carer')) then raise exception 'Add your name and relationship'; end if;
  if p_kind='ack' and not exists(select 1 from public.healthathon_records n where n.person_id=p_person and n.kind='note' and n.id=p_id and n.data->>'contentHash'=p_data->>'contentHash' and not exists(select 1 from public.healthathon_records newer where newer.person_id=p_person and newer.kind='note' and (newer.data->>'version')::integer>(n.data->>'version')::integer)) then raise exception 'Open the latest note before confirming'; end if;
  select * into saved from public.healthathon_records where id=p_id and kind=p_kind;
  if found and saved.person_id=p_person and saved.data=p_data then return saved; end if;
  if p_kind='note' and (p_data->>'version')::integer <> coalesce((select max((data->>'version')::integer)+1 from public.healthathon_records where person_id=p_person and kind='note'),1) then raise exception 'A newer note is available. Refresh before saving.' using errcode='40001'; end if;
  if p_kind<>'entry' and p_revision<>0 then raise exception 'Saved care notes cannot be changed.' using errcode='42501'; end if;
  if p_revision=0 then
    insert into public.healthathon_records(id,kind,person_id,data) values(p_id,p_kind,p_person,p_data) returning * into saved;
  else
    update public.healthathon_records set data=p_data, revision=revision+1,updated_at=now()
    where id=p_id and kind=p_kind and person_id=p_person and revision=p_revision returning * into saved;
    if not found then raise exception 'This record changed. Refresh and try again.' using errcode='40001'; end if;
  end if;
  return saved;
end $$;
revoke all on function healthathon_private.save_record(uuid,uuid,text,jsonb,bigint) from public,anon;
grant execute on function healthathon_private.save_record(uuid,uuid,text,jsonb,bigint) to authenticated;
create function public.healthathon_save_record(p_id uuid,p_person uuid,p_kind text,p_data jsonb,p_revision bigint)
returns public.healthathon_records language sql security invoker set search_path='' as $$
select healthathon_private.save_record(p_id,p_person,p_kind,p_data,p_revision)
$$;
revoke all on function public.healthathon_save_record(uuid,uuid,text,jsonb,bigint) from public,anon;
grant execute on function public.healthathon_save_record(uuid,uuid,text,jsonb,bigint) to authenticated;

create function healthathon_private.make_invite(p_person uuid,p_edit boolean) returns text
language plpgsql security definer set search_path='' as $$
declare code text;
begin
  if auth.uid() is null or not healthathon_private.is_owner(p_person) then raise exception 'Only the owner can invite someone.' using errcode='42501'; end if;
  delete from healthathon_private.invites where expires_at<now();
  if (select count(*) from healthathon_private.invites where person_id=p_person)>10 then raise exception 'Too many open invitations'; end if;
  code:=encode(extensions.gen_random_bytes(24),'hex');
  insert into healthathon_private.invites(code_hash,person_id,can_edit) values(encode(extensions.digest(code,'sha256'),'hex'),p_person,p_edit);
  return code;
end $$;
create function healthathon_private.use_invite(p_code text) returns uuid
language plpgsql security definer set search_path='' as $$
declare invitation healthathon_private.invites;
begin
  if auth.uid() is null then raise exception 'Sign in first.' using errcode='42501'; end if;
  if p_code !~ '^[a-f0-9]{48}$' then raise exception 'Invalid invitation'; end if;
  delete from healthathon_private.invites where code_hash=encode(extensions.digest(p_code,'sha256'),'hex') and expires_at>now() returning * into invitation;
  if not found then raise exception 'Invitation expired or already used.'; end if;
  insert into public.healthathon_members(person_id,user_id,can_edit) values(invitation.person_id,auth.uid(),invitation.can_edit)
  on conflict(person_id,user_id) do update set can_edit=excluded.can_edit;
  return invitation.person_id;
end $$;
revoke all on function healthathon_private.make_invite(uuid,boolean),healthathon_private.use_invite(text) from public,anon;
grant execute on function healthathon_private.make_invite(uuid,boolean),healthathon_private.use_invite(text) to authenticated;
create function public.healthathon_invite(p_person uuid,p_edit boolean) returns text language sql security invoker set search_path='' as $$ select healthathon_private.make_invite(p_person,p_edit) $$;
create function public.healthathon_join(p_code text) returns uuid language sql security invoker set search_path='' as $$ select healthathon_private.use_invite(p_code) $$;
revoke all on function public.healthathon_invite(uuid,boolean),public.healthathon_join(text) from public,anon;
grant execute on function public.healthathon_invite(uuid,boolean),public.healthathon_join(text) to authenticated;
commit;
