-- Shared web/native workspace. Existing offline drafts and immutable notes are retained.
-- No staff or clinician is approved by this migration. Approval is a separate human action.
begin;

create table healthathon_private.staff_roles (
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check(role in ('clinical_verifier','directory_reviewer','admin')),
  granted_by uuid references auth.users(id), granted_at timestamptz not null default now(),
  revoked_at timestamptz, primary key(user_id,role)
);
alter table healthathon_private.staff_roles enable row level security;
revoke all on healthathon_private.staff_roles from public,anon,authenticated;
create function healthathon_private.has_staff_role(p_role text) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 return exists(select 1 from healthathon_private.staff_roles
 where user_id=auth.uid() and revoked_at is null and role in (p_role,'admin'));
end
$$;
revoke all on function healthathon_private.has_staff_role(text) from public,anon;
grant execute on function healthathon_private.has_staff_role(text) to authenticated;
create function public.healthathon_staff_role(p_role text) returns boolean
language sql security invoker set search_path='' as $$select healthathon_private.has_staff_role(p_role)$$;
revoke all on function public.healthathon_staff_role(text) from public,anon;
grant execute on function public.healthathon_staff_role(text) to authenticated;

create table public.healthathon_clinician_requests (
 user_id uuid primary key references auth.users(id) on delete cascade,
 name text not null check(length(btrim(name)) between 1 and 100),
 council text not null check(length(btrim(council)) between 2 and 160),
 registration text not null check(length(btrim(registration)) between 2 and 80),
 status text not null default 'pending' check(status in ('pending','approved','rejected','suspended')),
 submitted_at timestamptz not null default now(), reviewed_at timestamptz,
 reviewed_by uuid references auth.users(id), review_note text not null default '',
 revision bigint not null default 1
);
alter table public.healthathon_clinician_requests enable row level security;
revoke all on public.healthathon_clinician_requests from public,anon,authenticated;
grant select on public.healthathon_clinician_requests to authenticated;
create policy clinician_request_read on public.healthathon_clinician_requests for select to authenticated
 using(user_id=(select auth.uid()) or healthathon_private.has_staff_role('clinical_verifier'));
create policy clinician_request_account_open on public.healthathon_clinician_requests as restrictive for all to authenticated
 using((select healthathon_private.require_open_account())) with check((select healthathon_private.require_open_account()));
create table healthathon_private.clinician_reviews (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references auth.users(id) on delete cascade,
 reviewer_id uuid not null references auth.users(id), decision text not null,
 evidence_reference text not null, note text not null, request_revision bigint not null,
 reviewed_at timestamptz not null default now()
);
alter table healthathon_private.clinician_reviews enable row level security;
revoke all on healthathon_private.clinician_reviews from public,anon,authenticated;

create function healthathon_private.request_clinician(p_name text,p_council text,p_registration text) returns public.healthathon_clinician_requests
language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_clinician_requests;
begin
 perform healthathon_private.require_open_account();
 if length(btrim(p_name)) not between 1 and 100 or length(btrim(p_council)) not between 2 and 160
 or length(btrim(p_registration)) not between 2 and 80 then raise exception 'Add your name, council and registration number.'; end if;
 insert into public.healthathon_clinician_requests(user_id,name,council,registration)
 values(auth.uid(),btrim(p_name),btrim(p_council),btrim(p_registration))
 on conflict(user_id) do update set name=excluded.name,council=excluded.council,registration=excluded.registration,
 status='pending',submitted_at=now(),reviewed_at=null,reviewed_by=null,review_note='',revision=healthathon_clinician_requests.revision+1
 returning * into saved;
 return saved;
end $$;
create function public.healthathon_request_clinician(p_name text,p_council text,p_registration text) returns public.healthathon_clinician_requests
language sql security invoker set search_path='' as $$select healthathon_private.request_clinician(p_name,p_council,p_registration)$$;

create function healthathon_private.review_clinician(p_user uuid,p_revision bigint,p_decision text,p_evidence text,p_note text) returns public.healthathon_clinician_requests
language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_clinician_requests;
begin
 if auth.uid() is null or not healthathon_private.has_staff_role('clinical_verifier') or p_user=auth.uid()
 then raise exception 'An authorised independent verifier must review this request.' using errcode='42501'; end if;
 if p_decision not in ('approved','rejected','suspended') or length(btrim(p_evidence)) not between 8 and 1200
 or length(btrim(p_note)) not between 8 and 2000 then raise exception 'Record the checked register/source and review reason.'; end if;
 update public.healthathon_clinician_requests set status=p_decision,reviewed_by=auth.uid(),reviewed_at=now(),review_note=p_note,revision=revision+1
 where user_id=p_user and revision=p_revision returning * into saved;
 if not found then raise exception 'This request changed. Refresh before reviewing.' using errcode='40001'; end if;
 insert into healthathon_private.clinician_reviews(user_id,reviewer_id,decision,evidence_reference,note,request_revision)
 values(p_user,auth.uid(),p_decision,p_evidence,p_note,p_revision);
 return saved;
end $$;
create function public.healthathon_review_clinician(p_user uuid,p_revision bigint,p_decision text,p_evidence text,p_note text) returns public.healthathon_clinician_requests
language sql security invoker set search_path='' as $$select healthathon_private.review_clinician(p_user,p_revision,p_decision,p_evidence,p_note)$$;

alter table public.healthathon_members add column role text not null default 'family' check(role in ('family','clinician'));
alter table public.healthathon_members add column permissions jsonb not null default '{"careNote":true,"calendar":true,"reports":true,"costs":false}'::jsonb;
alter table public.healthathon_members add column expires_at timestamptz;
alter table public.healthathon_members add column consented_at timestamptz not null default now();
alter table healthathon_private.invites add column id uuid not null default gen_random_uuid();
alter table healthathon_private.invites add column role text not null default 'family' check(role in ('family','clinician'));
alter table healthathon_private.invites add column permissions jsonb not null default '{"careNote":true,"calendar":true,"reports":true,"costs":false}'::jsonb;
alter table healthathon_private.invites add column member_expires_at timestamptz;
alter table healthathon_private.invites add column created_at timestamptz not null default now();

create or replace function healthathon_private.can_read(p_person uuid) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 return (exists(select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid())
 or exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid()
 and (expires_at is null or expires_at>now()) and (role='family' or exists(select 1 from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved'))));
end
$$;
create or replace function healthathon_private.can_edit(p_person uuid) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 return (exists(select 1 from public.healthathon_people where id=p_person and owner_id=auth.uid())
 or exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid() and can_edit
 and (expires_at is null or expires_at>now()) and (role='family' or exists(select 1 from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved'))));
end
$$;
create function healthathon_private.has_permission(p_person uuid,p_permission text) returns boolean
language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and (healthathon_private.is_owner(p_person) or exists(select 1 from public.healthathon_members
 where person_id=p_person and user_id=auth.uid() and (expires_at is null or expires_at>now()) and permissions->>p_permission='true'
 and (role='family' or exists(select 1 from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved'))))
$$;
create function healthathon_private.is_care_clinician(p_person uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and not healthathon_private.is_owner(p_person)
 and exists(select 1 from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved')
 and exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid() and role='clinician'
 and can_edit and (expires_at is null or expires_at>now()) and permissions->>'careNote'='true')
$$;
create function healthathon_private.can_read_record(p_person uuid,p_kind text,p_data jsonb) returns boolean
language plpgsql security definer set search_path='' as $$
declare detail text:=coalesce(p_data->>'detail',''); structured jsonb;
begin
 if healthathon_private.is_owner(p_person) then return true; end if;
 if not healthathon_private.can_read(p_person) then return false; end if;
 if p_kind in ('note','ack') then return healthathon_private.has_permission(p_person,'careNote'); end if;
 if left(detail,length(E'sahara:entry:v1\n'))=E'sahara:entry:v1\n' then
 begin structured:=substring(detail from length(E'sahara:entry:v1\n')+1)::jsonb; exception when invalid_text_representation then return false; end;
 if structured->>'type'='lab' then return healthathon_private.has_permission(p_person,'reports'); end if;
 if structured->>'type'='conversation' then return healthathon_private.has_permission(p_person,'careNote'); end if;
 if structured->>'type' not in ('schedule','task') or structured->>'type' is null then return false; end if;
 end if;
 if p_data->>'kind'='medicine' then return healthathon_private.has_permission(p_person,'calendar') and healthathon_private.has_permission(p_person,'reports'); end if;
 if p_data->>'kind' in ('visit','task') then return healthathon_private.has_permission(p_person,'calendar'); end if;
 return healthathon_private.has_permission(p_person,'careNote');
end
$$;
-- Family access remains independently revocable. Account membership never means clinician approval.
drop policy records_read on public.healthathon_records;
create policy records_read on public.healthathon_records for select to authenticated
 using(healthathon_private.can_read_record(person_id,kind,data));
drop policy records_remove on public.healthathon_records;
create policy records_remove on public.healthathon_records for delete to authenticated
 using(kind='entry' and healthathon_private.can_edit(person_id) and healthathon_private.can_read_record(person_id,kind,data));

create function healthathon_private.invite_access(p_person uuid,p_role text,p_edit boolean,p_permissions jsonb,p_days integer) returns text
language plpgsql security definer set search_path='' as $$
declare code text;
begin
 if auth.uid() is null or not healthathon_private.is_owner(p_person) then raise exception 'Only the record owner can invite someone.' using errcode='42501'; end if;
 if p_role is null or p_role not in ('family','clinician') or p_days is null or p_days not between 1 and 365 or p_edit is null or p_permissions is null
 or jsonb_typeof(p_permissions)<>'object' or p_permissions - array['careNote','calendar','reports','costs'] <> '{}'::jsonb
 or exists(select 1 from jsonb_each(p_permissions) where jsonb_typeof(value)<>'boolean') then raise exception 'Choose the role, access and expiry.'; end if;
 delete from healthathon_private.invites where person_id=p_person and expires_at<now();
 if (select count(*) from healthathon_private.invites where person_id=p_person)>=10 then raise exception 'Cancel an unused invitation first.'; end if;
 code:=encode(extensions.gen_random_bytes(24),'hex');
 insert into healthathon_private.invites(code_hash,person_id,can_edit,role,permissions,member_expires_at)
 values(encode(extensions.digest(code,'sha256'),'hex'),p_person,p_edit,p_role,p_permissions,now()+make_interval(days=>p_days));
 return code;
end $$;
create function public.healthathon_invite_access(p_person uuid,p_role text,p_edit boolean,p_permissions jsonb,p_days integer) returns text
language sql security invoker set search_path='' as $$select healthathon_private.invite_access(p_person,p_role,p_edit,p_permissions,p_days)$$;
create or replace function healthathon_private.make_invite(p_person uuid,p_edit boolean) returns text
language sql security definer set search_path='' as $$
 select healthathon_private.invite_access(p_person,'family',p_edit,'{"careNote":true,"calendar":true,"reports":true,"costs":false}'::jsonb,30)
$$;
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
 insert into public.healthathon_members(person_id,user_id,can_edit,role,permissions,expires_at,consented_at)
 values(invitation.person_id,auth.uid(),invitation.can_edit,invitation.role,invitation.permissions,invitation.member_expires_at,now())
 on conflict(person_id,user_id) do update set can_edit=excluded.can_edit,role=excluded.role,permissions=excluded.permissions,expires_at=excluded.expires_at,consented_at=excluded.consented_at;
 delete from healthathon_private.invites where code_hash=invitation.code_hash;
 return invitation.person_id;
end $$;
create function healthathon_private.cancel_invite(p_id uuid) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 delete from healthathon_private.invites where id=p_id and healthathon_private.is_owner(person_id);
 return found;
end $$;
create function public.healthathon_cancel_invite(p_id uuid) returns boolean
language sql security invoker set search_path='' as $$select healthathon_private.cancel_invite(p_id)$$;
create function healthathon_private.open_invites(p_person uuid) returns table(id uuid,role text,can_edit boolean,permissions jsonb,expires_at timestamptz,member_expires_at timestamptz)
language plpgsql security definer set search_path='' as $$
begin
 if not healthathon_private.is_owner(p_person) then raise exception 'Only the owner can manage invitations.' using errcode='42501'; end if;
 return query select i.id,i.role,i.can_edit,i.permissions,i.expires_at,i.member_expires_at from healthathon_private.invites i where i.person_id=p_person and i.expires_at>now() order by i.created_at;
end $$;
create function public.healthathon_open_invites(p_person uuid) returns table(id uuid,role text,can_edit boolean,permissions jsonb,expires_at timestamptz,member_expires_at timestamptz)
language sql security invoker set search_path='' as $$select * from healthathon_private.open_invites(p_person)$$;

create table public.healthathon_care_drafts (
 id uuid primary key, person_id uuid not null references public.healthathon_people(id) on delete cascade,
 author_id uuid not null references auth.users(id) on delete cascade,
 data jsonb not null, revision bigint not null default 1,
 status text not null default 'draft' check(status in ('draft','submitted','reviewed')),
 reviewed_note_id uuid, created_at timestamptz not null default now(), submitted_at timestamptz, updated_at timestamptz not null default now()
);
create index healthathon_care_drafts_person on public.healthathon_care_drafts(person_id);
alter table public.healthathon_care_drafts enable row level security;
revoke all on public.healthathon_care_drafts from public,anon,authenticated;
grant select on public.healthathon_care_drafts to authenticated;
create policy drafts_read on public.healthathon_care_drafts for select to authenticated using(
 healthathon_private.has_permission(person_id,'careNote') and (author_id=(select auth.uid())
 or healthathon_private.is_owner(person_id) or (status in ('submitted','reviewed') and healthathon_private.is_care_clinician(person_id))));

create function healthathon_private.valid_draft(p_data jsonb) returns boolean
language sql immutable set search_path='' as $$
 select p_data is not null and jsonb_typeof(p_data)='object' and octet_length(p_data::text)<=65536
 and p_data - array['source','priorities','participants','topics','questions','nextSteps'] = '{}'::jsonb
 and length(btrim(coalesce(p_data->>'source',''))) between 1 and 12000
 and not exists(select 1 from jsonb_each(p_data) where jsonb_typeof(value)<>'string' or (key<>'source' and length(value #>> '{}')>4000))
$$;
create function healthathon_private.save_draft(p_id uuid,p_person uuid,p_data jsonb,p_revision bigint,p_submit boolean) returns public.healthathon_care_drafts
language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_care_drafts;
begin
 if auth.uid() is null or not healthathon_private.can_edit(p_person) or not healthathon_private.has_permission(p_person,'careNote') then raise exception 'You cannot change this conversation.' using errcode='42501'; end if;
 if not healthathon_private.valid_draft(p_data) or p_revision is null or p_revision<0 or p_submit is null then raise exception 'Add the original conversation and review its sections.'; end if;
 if p_revision=0 then
 insert into public.healthathon_care_drafts(id,person_id,author_id,data,status,submitted_at) values(p_id,p_person,auth.uid(),p_data,case when p_submit then 'submitted' else 'draft' end,case when p_submit then now() else null end) returning * into saved;
 else
 update public.healthathon_care_drafts set data=p_data,revision=revision+1,status=case when p_submit then 'submitted' else 'draft' end,submitted_at=case when p_submit then now() else null end,updated_at=now()
 where id=p_id and person_id=p_person and author_id=auth.uid() and revision=p_revision and status<>'reviewed' returning * into saved;
 if not found then raise exception 'This conversation changed. Refresh before saving. Your draft is still here.' using errcode='40001'; end if;
 end if;
 return saved;
end $$;
create function public.healthathon_save_draft(p_id uuid,p_person uuid,p_data jsonb,p_revision bigint,p_submit boolean) returns public.healthathon_care_drafts
language sql security invoker set search_path='' as $$select healthathon_private.save_draft(p_id,p_person,p_data,p_revision,p_submit)$$;

-- Keep the old validator behind a guarded entry point. Client-controlled role strings
-- can never create a verified review, and a family account cannot confirm as the patient.
alter function healthathon_private.save_record(uuid,uuid,text,jsonb,bigint) rename to save_record_legacy;
revoke all on function healthathon_private.save_record_legacy(uuid,uuid,text,jsonb,bigint) from public,anon,authenticated;
create function healthathon_private.save_record(p_id uuid,p_person uuid,p_kind text,p_data jsonb,p_revision bigint)
returns public.healthathon_records language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_records; version_row public.healthathon_records; version_id uuid; actor_data jsonb;
begin
 if auth.uid() is null or not healthathon_private.can_read_record(p_person,p_kind,p_data) then raise exception 'You cannot access these records.' using errcode='42501'; end if;
 if p_kind='note' and (coalesce(p_data->>'reviewerVerification','not_verified')<>'not_verified' or coalesce(p_data->>'reviewStatus','reviewed_unverified')<>'reviewed_unverified') then raise exception 'Only the clinician review workflow can verify a note.' using errcode='42501'; end if;
 if p_kind<>'ack' then
 if exists(select 1 from public.healthathon_records where id=p_id and kind=p_kind
 and not healthathon_private.can_read_record(person_id,kind,data)) then raise exception 'You cannot change this record.' using errcode='42501'; end if;
 return healthathon_private.save_record_legacy(p_id,p_person,p_kind,p_data,p_revision); end if;
 if p_revision is distinct from 0 or jsonb_typeof(p_data)<>'object' or length(btrim(coalesce(p_data->>'name',''))) not between 1 and 100
 or coalesce(p_data->>'contentHash','') !~ '^[a-f0-9]{64}$' or coalesce(p_data->>'at','') !~ '^[0-9]{1,15}$' then raise exception 'Read the exact note and add your name.'; end if;
 perform 1 from public.healthathon_people where id=p_person for update;
 begin version_id:=coalesce(nullif(p_data->>'versionId',''),p_id::text)::uuid; exception when invalid_text_representation then raise exception 'Invalid care-note version'; end;
 select * into version_row from public.healthathon_records where kind='note' and id=version_id and person_id=p_person and data->>'contentHash'=p_data->>'contentHash';
 if not found or exists(select 1 from public.healthathon_records where person_id=p_person and kind='note' and (data->>'version')::integer>(version_row.data->>'version')::integer) then raise exception 'A newer note is available. Open it before confirming.' using errcode='40001'; end if;
 if p_data->>'relationship'='Patient' then
 if not healthathon_private.is_owner(p_person) then raise exception 'The patient record owner must confirm this version first.' using errcode='42501'; end if;
 elsif p_data->>'relationship'='Family / carer' then
 if healthathon_private.is_owner(p_person) or not exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid() and role='family' and (expires_at is null or expires_at>now())) then raise exception 'Use your separately invited family account.' using errcode='42501'; end if;
 if not exists(select 1 from public.healthathon_records a join public.healthathon_people p on p.id=a.person_id where a.kind='ack' and a.person_id=p_person and a.data->>'versionId'=version_id::text and a.data->>'contentHash'=p_data->>'contentHash' and a.data->>'relationship'='Patient' and a.data->>'actorId'=p.owner_id::text) then raise exception 'The patient needs to confirm this exact version before family adds a confirmation.'; end if;
 else raise exception 'Choose Patient or Family / carer.'; end if;
 actor_data:=jsonb_build_object('versionId',version_id,'contentHash',p_data->>'contentHash','name',btrim(p_data->>'name'),'relationship',p_data->>'relationship','at',floor(extract(epoch from clock_timestamp())*1000)::bigint,'actorId',auth.uid());
 select * into saved from public.healthathon_records where kind='ack' and person_id=p_person and data->>'versionId'=version_id::text and data->>'actorId'=auth.uid()::text and data->>'contentHash'=p_data->>'contentHash' limit 1;
 if found then return saved; end if;
 insert into public.healthathon_records(id,kind,person_id,data) values(p_id,'ack',p_person,actor_data) returning * into saved;
 return saved;
end $$;

create function healthathon_private.review_draft(p_id uuid,p_revision bigint,p_confirm boolean) returns public.healthathon_records
language plpgsql security definer set search_path='' as $$
declare draft public.healthathon_care_drafts; profile public.healthathon_clinician_requests; note_data jsonb; saved public.healthathon_records; note_id uuid:=gen_random_uuid(); version integer;
begin
 select * into draft from public.healthathon_care_drafts where id=p_id for update;
 if not found or not healthathon_private.is_care_clinician(draft.person_id) then raise exception 'A verified clinician invited by this patient must review this conversation.' using errcode='42501'; end if;
 if not p_confirm or p_confirm is null then raise exception 'Read the source and every section before publishing.'; end if;
 if draft.status='reviewed' then select * into saved from public.healthathon_records where id=draft.reviewed_note_id and kind='note'; return saved; end if;
 if draft.revision<>p_revision or draft.status<>'submitted' then raise exception 'This conversation changed. Refresh before reviewing.' using errcode='40001'; end if;
 select * into profile from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved';
 perform 1 from public.healthathon_people where id=draft.person_id for update;
 select coalesce(max((data->>'version')::integer),0)+1 into version from public.healthathon_records where person_id=draft.person_id and kind='note';
 note_data:=jsonb_build_object('version',version,'personName',(select name from public.healthathon_people where id=draft.person_id),
 'source',draft.data->>'source','priorities',coalesce(draft.data->>'priorities',''),'participants',coalesce(draft.data->>'participants',''),
 'topics',coalesce(draft.data->>'topics',''),'questions',coalesce(draft.data->>'questions',''),'nextSteps',coalesce(draft.data->>'nextSteps',''),
 'doctor',profile.name,'published',floor(extract(epoch from clock_timestamp())*1000)::bigint,
 'draftOrigin','shared_source','reviewStatus','clinician_reviewed','reviewerVerification','verified','reviewerId',auth.uid(),
 'draftId',draft.id,'draftRevision',draft.revision,'hashScheme','sha256-json-v1');
 note_data:=note_data||jsonb_build_object('contentHash',encode(extensions.digest(note_data::text,'sha256'),'hex'));
 saved:=healthathon_private.save_record_legacy(note_id,draft.person_id,'note',note_data,0);
 update public.healthathon_care_drafts set status='reviewed',reviewed_note_id=saved.id,updated_at=now() where id=draft.id;
 return saved;
end $$;
create function public.healthathon_review_draft(p_id uuid,p_revision bigint,p_confirm boolean) returns public.healthathon_records
language sql security invoker set search_path='' as $$select healthathon_private.review_draft(p_id,p_revision,p_confirm)$$;
create function public.healthathon_note_at(p_person uuid,p_version integer,p_id uuid default null) returns setof public.healthathon_records
language sql stable security invoker set search_path='' as $$
 select * from public.healthathon_records where person_id=p_person and kind='note' and (data->>'version')::integer=p_version and (p_id is null or id=p_id)
$$;
create function healthathon_private.review_receipts() returns table(note_id uuid,reviewer_id uuid)
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 return query select n.id,(n.data->>'reviewerId')::uuid from public.healthathon_records n
 join public.healthathon_care_drafts d on d.reviewed_note_id=n.id and d.person_id=n.person_id and d.status='reviewed'
 where n.kind='note' and n.data->>'reviewerVerification'='verified' and n.data->>'reviewStatus'='clinician_reviewed'
 and n.data->>'hashScheme'='sha256-json-v1' and n.data->>'draftId'=d.id::text
 and healthathon_private.can_read_record(n.person_id,n.kind,n.data);
end $$;
create function public.healthathon_review_receipts() returns table(note_id uuid,reviewer_id uuid)
language sql security invoker set search_path='' as $$select * from healthathon_private.review_receipts()$$;

-- Named tasks bind acceptance/completion to a real invited account, not a typed name.
create table public.healthathon_team_tasks (
 id uuid primary key, person_id uuid not null references public.healthathon_people(id) on delete cascade,
 author_id uuid not null references auth.users(id), recipient_id uuid not null references auth.users(id),
 title text not null check(length(btrim(title)) between 1 and 200), detail text not null default '' check(length(detail)<=1600),
 due_ms bigint not null default 0 check(due_ms>=0), status text not null default 'open' check(status in ('open','accepted','completed','cancelled')),
 revision bigint not null default 1, created_at timestamptz not null default now(), accepted_at timestamptz,completed_at timestamptz,
 updated_at timestamptz not null default now()
);
create index healthathon_team_tasks_person on public.healthathon_team_tasks(person_id);
alter table public.healthathon_team_tasks enable row level security;
revoke all on public.healthathon_team_tasks from public,anon,authenticated;
grant select on public.healthathon_team_tasks to authenticated;
create policy tasks_read on public.healthathon_team_tasks for select to authenticated using(healthathon_private.has_permission(person_id,'calendar'));
create function healthathon_private.task_people(p_person uuid) returns table(user_id uuid,label text)
language plpgsql security definer set search_path='' as $$
begin
 if not healthathon_private.has_permission(p_person,'calendar') then raise exception 'You cannot open this care team.' using errcode='42501'; end if;
 return query select p.owner_id,p.name||' (patient record owner)' from public.healthathon_people p where p.id=p_person
 union all select m.user_id,case when healthathon_private.is_owner(p_person) then u.email else 'Your account' end
 from public.healthathon_members m join auth.users u on u.id=m.user_id where m.person_id=p_person
 and (m.expires_at is null or m.expires_at>now()) and m.permissions->>'calendar'='true'
 and (m.role='family' or exists(select 1 from public.healthathon_clinician_requests c where c.user_id=m.user_id and c.status='approved'))
 and (healthathon_private.is_owner(p_person) or m.user_id=auth.uid());
end $$;
create function public.healthathon_task_people(p_person uuid) returns table(user_id uuid,label text)
language sql security invoker set search_path='' as $$select * from healthathon_private.task_people(p_person)$$;
create function healthathon_private.save_task(p_id uuid,p_person uuid,p_recipient uuid,p_title text,p_detail text,p_due bigint,p_revision bigint) returns public.healthathon_team_tasks
language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_team_tasks;
begin
 if not healthathon_private.can_edit(p_person) or not healthathon_private.has_permission(p_person,'calendar') then raise exception 'You cannot assign this task.' using errcode='42501'; end if;
 perform 1 from public.healthathon_people where id=p_person for update;
 if p_title is null or length(btrim(p_title)) not between 1 and 200 or p_detail is null or length(p_detail)>1600 or p_due is null or p_due<0 or p_revision is null or p_revision<0 then raise exception 'Add the task, recipient and valid date.'; end if;
 if not exists(select 1 from healthathon_private.task_people(p_person) where user_id=p_recipient) then raise exception 'Choose a currently permitted care account.' using errcode='42501'; end if;
 if p_revision=0 then
 insert into public.healthathon_team_tasks(id,person_id,author_id,recipient_id,title,detail,due_ms)
 values(p_id,p_person,auth.uid(),p_recipient,btrim(p_title),btrim(p_detail),p_due) returning * into saved;
 else
 update public.healthathon_team_tasks set recipient_id=p_recipient,title=btrim(p_title),detail=btrim(p_detail),due_ms=p_due,revision=revision+1,updated_at=now()
 where id=p_id and person_id=p_person and revision=p_revision and status='open' and (author_id=auth.uid() or healthathon_private.is_owner(p_person)) returning * into saved;
 if not found then raise exception 'This task changed. Refresh before editing.' using errcode='40001'; end if;
 end if; return saved;
end $$;
create function public.healthathon_save_task(p_id uuid,p_person uuid,p_recipient uuid,p_title text,p_detail text,p_due bigint,p_revision bigint) returns public.healthathon_team_tasks
language sql security invoker set search_path='' as $$select healthathon_private.save_task(p_id,p_person,p_recipient,p_title,p_detail,p_due,p_revision)$$;
create function healthathon_private.transition_task(p_id uuid,p_revision bigint,p_status text) returns public.healthathon_team_tasks
language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_team_tasks;
begin
 select * into saved from public.healthathon_team_tasks where id=p_id for update;
 if not found or not healthathon_private.has_permission(saved.person_id,'calendar') then raise exception 'You cannot open this task.' using errcode='42501'; end if;
 if saved.revision<>p_revision then raise exception 'This task changed. Refresh before continuing.' using errcode='40001'; end if;
 if p_status='accepted' and saved.status='open' and saved.recipient_id=auth.uid() then
 update public.healthathon_team_tasks set status='accepted',accepted_at=now(),revision=revision+1,updated_at=now() where id=p_id returning * into saved;
 elsif p_status='completed' and saved.status='accepted' and saved.recipient_id=auth.uid() then
 update public.healthathon_team_tasks set status='completed',completed_at=now(),revision=revision+1,updated_at=now() where id=p_id returning * into saved;
 elsif p_status='cancelled' and saved.status in ('open','accepted') and (saved.author_id=auth.uid() or healthathon_private.is_owner(saved.person_id)) then
 update public.healthathon_team_tasks set status='cancelled',revision=revision+1,updated_at=now() where id=p_id returning * into saved;
 elsif p_status='open' and saved.status in ('completed','cancelled') and (saved.author_id=auth.uid() or healthathon_private.is_owner(saved.person_id)) then
 update public.healthathon_team_tasks set status='open',accepted_at=null,completed_at=null,revision=revision+1,updated_at=now() where id=p_id returning * into saved;
 else raise exception 'Only the assigned account can accept or complete a task. The author can cancel or reopen it.' using errcode='42501'; end if;
 return saved;
end $$;
create function public.healthathon_transition_task(p_id uuid,p_revision bigint,p_status text) returns public.healthathon_team_tasks
language sql security invoker set search_path='' as $$select healthathon_private.transition_task(p_id,p_revision,p_status)$$;
create function healthathon_private.care_progress(p_person uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
begin
 if not healthathon_private.is_owner(p_person) and not healthathon_private.is_care_clinician(p_person) then raise exception 'Only this patient or their verified clinician can read workflow counts.' using errcode='42501'; end if;
 return jsonb_build_object('drafts',(select count(*) from public.healthathon_care_drafts where person_id=p_person),
 'waitingReview',(select count(*) from public.healthathon_care_drafts where person_id=p_person and status='submitted'),
 'reviewedVersions',(select count(*) from public.healthathon_care_drafts where person_id=p_person and status='reviewed'),
 'patientConfirmations',(select count(*) from public.healthathon_records where person_id=p_person and kind='ack' and data->>'relationship'='Patient' and data ? 'actorId'),
 'familyConfirmations',(select count(*) from public.healthathon_records where person_id=p_person and kind='ack' and data->>'relationship'='Family / carer' and data ? 'actorId'),
 'acceptedTasks',(select count(*) from public.healthathon_team_tasks where person_id=p_person and status='accepted'),
 'completedTasks',(select count(*) from public.healthathon_team_tasks where person_id=p_person and status='completed'),
 'medianReviewMinutes',(select percentile_cont(0.5) within group(order by greatest(0,extract(epoch from (updated_at-submitted_at))/60)) from public.healthathon_care_drafts where person_id=p_person and status='reviewed' and submitted_at is not null),
 'measuredAt',now(),'scope','Saved account workflow events only; no patient outcome or clinical effectiveness measurement');
end $$;
create function public.healthathon_care_progress(p_person uuid) returns jsonb
language sql security invoker set search_path='' as $$select healthathon_private.care_progress(p_person)$$;

create table healthathon_private.assist_reservations (
 request_id uuid primary key, user_id uuid not null references auth.users(id) on delete cascade,
 action text not null check(action in ('draft','transcribe','translate')), reserved_at timestamptz not null default now()
);
create index assist_reservation_date on healthathon_private.assist_reservations(reserved_at);
alter table healthathon_private.assist_reservations enable row level security;
revoke all on healthathon_private.assist_reservations from public,anon,authenticated;
create function healthathon_private.reserve_assist(p_request_id uuid,p_action text) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 if coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise exception 'Sign in with a confirmed account.' using errcode='42501'; end if;
 if p_request_id is null or p_action not in ('draft','transcribe','translate') then raise exception 'Invalid request'; end if;
 perform pg_advisory_xact_lock(19402026);
 if exists(select 1 from healthathon_private.assist_reservations where request_id=p_request_id) then return false; end if;
 if (select count(*) from healthathon_private.assist_reservations where reserved_at>=date_trunc('day',now()))>=100
 or (select count(*) from healthathon_private.assist_reservations where user_id=auth.uid() and reserved_at>=date_trunc('day',now()))>=10
 or exists(select 1 from healthathon_private.assist_reservations where user_id=auth.uid() and reserved_at>now()-interval '1 minute') then return false; end if;
 insert into healthathon_private.assist_reservations(request_id,user_id,action) values(p_request_id,auth.uid(),p_action);
 return true;
end $$;
create function public.sahara_reserve_assist(p_request_id uuid,p_action text) returns boolean
language sql security invoker set search_path='' as $$select healthathon_private.reserve_assist(p_request_id,p_action)$$;

-- The only definer routines live in the non-exposed private schema; public wrappers
-- run as invokers. Every definer path checks auth.uid() and the specific grant.
revoke all on function healthathon_private.has_permission(uuid,text),healthathon_private.is_care_clinician(uuid),healthathon_private.can_read_record(uuid,text,jsonb),healthathon_private.request_clinician(text,text,text),healthathon_private.review_clinician(uuid,bigint,text,text,text),healthathon_private.invite_access(uuid,text,boolean,jsonb,integer),healthathon_private.cancel_invite(uuid),healthathon_private.open_invites(uuid),healthathon_private.valid_draft(jsonb),healthathon_private.save_draft(uuid,uuid,jsonb,bigint,boolean),healthathon_private.save_record(uuid,uuid,text,jsonb,bigint),healthathon_private.review_draft(uuid,bigint,boolean),healthathon_private.reserve_assist(uuid,text) from public,anon;
grant execute on function healthathon_private.has_permission(uuid,text),healthathon_private.is_care_clinician(uuid),healthathon_private.can_read_record(uuid,text,jsonb),healthathon_private.request_clinician(text,text,text),healthathon_private.review_clinician(uuid,bigint,text,text,text),healthathon_private.invite_access(uuid,text,boolean,jsonb,integer),healthathon_private.cancel_invite(uuid),healthathon_private.open_invites(uuid),healthathon_private.save_draft(uuid,uuid,jsonb,bigint,boolean),healthathon_private.save_record(uuid,uuid,text,jsonb,bigint),healthathon_private.review_draft(uuid,bigint,boolean),healthathon_private.reserve_assist(uuid,text) to authenticated;
revoke all on function public.healthathon_request_clinician(text,text,text),public.healthathon_review_clinician(uuid,bigint,text,text,text),public.healthathon_invite_access(uuid,text,boolean,jsonb,integer),public.healthathon_cancel_invite(uuid),public.healthathon_open_invites(uuid),public.healthathon_save_draft(uuid,uuid,jsonb,bigint,boolean),public.healthathon_review_draft(uuid,bigint,boolean),public.healthathon_note_at(uuid,integer,uuid),public.sahara_reserve_assist(uuid,text) from public,anon;
grant execute on function public.healthathon_request_clinician(text,text,text),public.healthathon_review_clinician(uuid,bigint,text,text,text),public.healthathon_invite_access(uuid,text,boolean,jsonb,integer),public.healthathon_cancel_invite(uuid),public.healthathon_open_invites(uuid),public.healthathon_save_draft(uuid,uuid,jsonb,bigint,boolean),public.healthathon_review_draft(uuid,bigint,boolean),public.healthathon_note_at(uuid,integer,uuid),public.sahara_reserve_assist(uuid,text) to authenticated;
revoke all on function healthathon_private.review_receipts(),public.healthathon_review_receipts() from public,anon;
grant execute on function healthathon_private.review_receipts(),public.healthathon_review_receipts() to authenticated;
revoke all on function healthathon_private.task_people(uuid),public.healthathon_task_people(uuid),healthathon_private.save_task(uuid,uuid,uuid,text,text,bigint,bigint),public.healthathon_save_task(uuid,uuid,uuid,text,text,bigint,bigint),healthathon_private.transition_task(uuid,bigint,text),public.healthathon_transition_task(uuid,bigint,text),healthathon_private.care_progress(uuid),public.healthathon_care_progress(uuid) from public,anon;
grant execute on function healthathon_private.task_people(uuid),public.healthathon_task_people(uuid),healthathon_private.save_task(uuid,uuid,uuid,text,text,bigint,bigint),public.healthathon_save_task(uuid,uuid,uuid,text,text,bigint,bigint),healthathon_private.transition_task(uuid,bigint,text),public.healthathon_transition_task(uuid,bigint,text),healthathon_private.care_progress(uuid),public.healthathon_care_progress(uuid) to authenticated;
commit;
