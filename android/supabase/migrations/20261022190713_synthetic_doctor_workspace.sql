-- Isolated synthetic doctor workspace. No existing patient/Auth/clinical rows change.
-- Real authentication is required; test publication never grants clinical verification.
begin;

create table public.sahara_doctor_datasets (
 id uuid primary key default gen_random_uuid(), owner_id uuid not null default auth.uid() references auth.users(id) on delete cascade,
 synthetic boolean not null default true check(synthetic), label text not null default 'Synthetic care workspace',
 created_at timestamptz not null default now(), unique(owner_id)
);
create table public.sahara_doctor_dataset_members (
 dataset_id uuid not null references public.sahara_doctor_datasets(id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade, can_edit boolean not null default false,
 expires_at timestamptz not null, primary key(dataset_id,user_id)
);
-- No membership mutations are exposed. Future invitations require their own reviewed flow.
alter table public.sahara_doctor_datasets enable row level security;
alter table public.sahara_doctor_dataset_members enable row level security;
revoke all on public.sahara_doctor_datasets,public.sahara_doctor_dataset_members from public,anon,authenticated;
grant select,insert on public.sahara_doctor_datasets to authenticated;
grant select on public.sahara_doctor_dataset_members to authenticated;
create policy doctor_member_self on public.sahara_doctor_dataset_members for select to authenticated
 using(user_id=(select auth.uid()) and expires_at>now() and healthathon_private.require_open_account());
create policy doctor_dataset_read on public.sahara_doctor_datasets for select to authenticated
 using(healthathon_private.require_open_account() and (owner_id=(select auth.uid()) or exists(
 select 1 from public.sahara_doctor_dataset_members m where m.dataset_id=id and m.user_id=(select auth.uid()) and m.expires_at>now())));
create policy doctor_dataset_create on public.sahara_doctor_datasets for insert to authenticated
 with check(owner_id=(select auth.uid()) and synthetic and healthathon_private.require_open_account());

create function healthathon_private.doctor_dataset_access(p_dataset uuid,p_write boolean default false) returns boolean
language sql stable security invoker set search_path='' as $$
 select auth.uid() is not null and exists(select 1 from public.sahara_doctor_datasets d where d.id=p_dataset
 and (d.owner_id=auth.uid() or exists(select 1 from public.sahara_doctor_dataset_members m
 where m.dataset_id=d.id and m.user_id=auth.uid() and m.expires_at>now() and (not p_write or m.can_edit))))
$$;
revoke all on function healthathon_private.doctor_dataset_access(uuid,boolean) from public,anon;
grant execute on function healthathon_private.doctor_dataset_access(uuid,boolean) to authenticated;

create table public.sahara_doctor_patients (
 id uuid primary key default gen_random_uuid(), dataset_id uuid not null references public.sahara_doctor_datasets(id) on delete cascade,
 legacy_id text not null check(legacy_id in ('demo-asha','demo-ravi','demo-leela','demo-imran','demo-maya','demo-joseph')),
 name text not null check(length(name) between 1 and 100), age integer not null check(age between 0 and 130),
 care_context text not null check(length(care_context)<=500), summary text not null check(length(summary)<=4000),
 payload jsonb not null check(jsonb_typeof(payload)='object' and octet_length(payload::text)<=65536 and (payload->'synthetic') is not distinct from 'true'::jsonb),
 synthetic boolean not null default true check(synthetic), created_at timestamptz not null default now(),
 unique(dataset_id,legacy_id),unique(id,dataset_id)
);
create table public.sahara_doctor_notes (
 id uuid primary key default gen_random_uuid(), dataset_id uuid not null, patient_id uuid not null,
 author_id uuid not null default auth.uid() references auth.users(id), revision bigint not null default 1 check(revision>0),
 status text not null default 'draft' check(status in ('draft','test_published')), payload jsonb not null,
 content_hash text, created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 foreign key(patient_id,dataset_id) references public.sahara_doctor_patients(id,dataset_id) on delete cascade,
 check((status='draft' and content_hash is null) or (status='test_published' and content_hash ~ '^[a-f0-9]{64}$'))
);
create table public.sahara_doctor_doses (
 id uuid primary key default gen_random_uuid(), dataset_id uuid not null, patient_id uuid not null,
 medicine_label text not null check(length(btrim(medicine_label)) between 1 and 200), planned_at timestamptz not null,
 reported_taken_at timestamptz, logged_at timestamptz not null default now(), reporter_id uuid not null default auth.uid() references auth.users(id),
 outcome text not null check(outcome in ('taken','skipped')), source text not null default 'Account report · synthetic',
 seed_key text, unique(dataset_id,seed_key),
 foreign key(patient_id,dataset_id) references public.sahara_doctor_patients(id,dataset_id) on delete cascade,
 check((outcome='taken' and reported_taken_at is not null and reported_taken_at<=logged_at) or (outcome='skipped' and reported_taken_at is null))
);
create table public.sahara_doctor_files (
 id uuid primary key default gen_random_uuid(), dataset_id uuid not null, patient_id uuid not null,
 uploader_id uuid not null default auth.uid() references auth.users(id), title text not null check(length(btrim(title)) between 1 and 160),
 kind text not null check(kind in ('audio','report')), mime text not null,
 bytes bigint not null check(bytes between 1 and 4194304), sha256 text not null check(sha256 ~ '^[a-f0-9]{64}$'),
 object_path text not null unique, consent_at timestamptz not null default now(), created_at timestamptz not null default now(),
 upload_state text not null default 'pending' check(upload_state in ('pending','ready')),
 permission_attested boolean not null default false check(permission_attested),
 foreign key(patient_id,dataset_id) references public.sahara_doctor_patients(id,dataset_id) on delete cascade,
 check(object_path=dataset_id::text||'/'||patient_id::text||'/'||id::text),
 check((kind='audio' and mime in ('audio/mp4','audio/mpeg','audio/wav','audio/x-wav','audio/ogg')) or
 (kind='report' and mime in ('application/pdf','image/jpeg','image/png','image/webp')))
);
create index doctor_patients_dataset on public.sahara_doctor_patients(dataset_id);
create index doctor_notes_patient on public.sahara_doctor_notes(patient_id,updated_at desc);
create index doctor_notes_dataset on public.sahara_doctor_notes(dataset_id);
create index doctor_doses_patient on public.sahara_doctor_doses(patient_id,logged_at desc);
create index doctor_doses_dataset on public.sahara_doctor_doses(dataset_id);
create index doctor_files_patient on public.sahara_doctor_files(patient_id);
create index doctor_files_dataset on public.sahara_doctor_files(dataset_id);
create index doctor_members_user on public.sahara_doctor_dataset_members(user_id,dataset_id);

alter table public.sahara_doctor_patients enable row level security;
alter table public.sahara_doctor_notes enable row level security;
alter table public.sahara_doctor_doses enable row level security;
alter table public.sahara_doctor_files enable row level security;
revoke all on public.sahara_doctor_patients,public.sahara_doctor_notes,public.sahara_doctor_doses,public.sahara_doctor_files from public,anon,authenticated;
grant select,insert on public.sahara_doctor_patients,public.sahara_doctor_notes,public.sahara_doctor_doses,public.sahara_doctor_files to authenticated;
-- Storage restrictive policies are planned for anon requests to OTHER buckets too.
-- Lookup privilege avoids breaking those existing requests. No anon SELECT policy
-- exists on this table, so anonymous callers can never see a metadata row.
grant select on public.sahara_doctor_files to anon;
-- No direct UPDATE grant: only the revision-checking RPC may mutate a draft.
create policy doctor_patient_read on public.sahara_doctor_patients for select to authenticated using(healthathon_private.doctor_dataset_access(dataset_id));
create policy doctor_patient_create on public.sahara_doctor_patients for insert to authenticated with check(healthathon_private.doctor_dataset_access(dataset_id,true));
create policy doctor_note_read on public.sahara_doctor_notes for select to authenticated using(healthathon_private.doctor_dataset_access(dataset_id));
create policy doctor_note_create on public.sahara_doctor_notes for insert to authenticated with check(author_id=(select auth.uid()) and healthathon_private.doctor_dataset_access(dataset_id,true));
create policy doctor_note_change on public.sahara_doctor_notes for update to authenticated
 using(author_id=(select auth.uid()) and status='draft' and healthathon_private.doctor_dataset_access(dataset_id,true))
 with check(author_id=(select auth.uid()) and healthathon_private.doctor_dataset_access(dataset_id,true));
create policy doctor_dose_read on public.sahara_doctor_doses for select to authenticated using(healthathon_private.doctor_dataset_access(dataset_id));
create policy doctor_dose_create on public.sahara_doctor_doses for insert to authenticated with check(reporter_id=(select auth.uid()) and healthathon_private.doctor_dataset_access(dataset_id,true));
create policy doctor_file_read on public.sahara_doctor_files for select to authenticated using(healthathon_private.doctor_dataset_access(dataset_id));
create policy doctor_file_create on public.sahara_doctor_files for insert to authenticated with check(uploader_id=(select auth.uid()) and healthathon_private.doctor_dataset_access(dataset_id,true));

create function healthathon_private.doctor_validate_draft(p jsonb) returns boolean
language plpgsql immutable security invoker set search_path='' as $$
declare key text; item jsonb;
begin
 if p is null or jsonb_typeof(p)<>'object' or octet_length(p::text)>262144 or (p->'schemaVersion') is distinct from '1'::jsonb or (p->'synthetic') is distinct from 'true'::jsonb then return false; end if;
 if p - array['schemaVersion','synthetic','priorities','participants','topics','questions','undecided','nextSteps','sourceText','sourceIds','updatedAt','mockGenerated','checklist','transcriptProvider','transcriptModel','transcribedAt','audioClipId','aiProvider','aiModel','aiWarnings','evidence'] <> '{}'::jsonb then return false; end if;
 foreach key in array array['priorities','participants','topics','questions','undecided','nextSteps','sourceText','transcriptProvider','transcriptModel','audioClipId','aiProvider','aiModel'] loop
  if jsonb_typeof(p->key) is distinct from 'string' or length(p->>key)>(case when key='sourceText' then 60000 else 4000 end) then return false; end if;
 end loop;
 foreach key in array array['sourceIds','checklist','aiWarnings','evidence'] loop
  if jsonb_typeof(p->key) is distinct from 'array' or jsonb_array_length(p->key)>120 then return false; end if;
 end loop;
 if jsonb_typeof(p->'mockGenerated') is distinct from 'boolean' or coalesce(p->>'updatedAt','')!~'^[0-9]{1,15}$' or coalesce(p->>'transcribedAt','')!~'^[0-9]{1,15}$' then return false; end if;
 for item in select value from jsonb_array_elements(p->'checklist') loop
  if jsonb_typeof(item)<>'object' or item-array['title','status']<>'{}'::jsonb or jsonb_typeof(item->'title') is distinct from 'string' or length(coalesce(item->>'title','')) not between 1 and 200 or coalesce(item->>'status','') not in ('NOT_DISCUSSED','DISCUSSED','DEFERRED') then return false; end if;
 end loop;
 for item in select value from jsonb_array_elements(p->'evidence') loop
  if jsonb_typeof(item)<>'object' or item-array['section','quote']<>'{}'::jsonb or jsonb_typeof(item->'quote') is distinct from 'string' or coalesce(item->>'section','') not in ('priorities','participants','topics','questions','undecided','nextSteps') or length(coalesce(item->>'quote','')) not between 1 and 4000 then return false; end if;
 end loop;
 for item in select value from jsonb_array_elements(p->'sourceIds') union all select value from jsonb_array_elements(p->'aiWarnings') loop
  if jsonb_typeof(item)<>'string' or length(item #>> '{}')>4000 then return false; end if;
 end loop;
 return true;
end $$;
revoke all on function healthathon_private.doctor_validate_draft(jsonb) from public,anon;
grant execute on function healthathon_private.doctor_validate_draft(jsonb) to authenticated;

create function healthathon_private.doctor_note_guard() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not healthathon_private.doctor_validate_draft(new.payload) then raise exception 'Invalid synthetic draft. Your original was not changed.'; end if;
 if tg_op='INSERT' then
  if new.status<>'draft' or new.revision<>1 or new.author_id<>auth.uid() then raise exception 'New notes must start as your draft.'; end if;
  new.content_hash:=null; new.created_at:=now();
 else
  if old.status<>'draft' or new.id<>old.id or new.patient_id<>old.patient_id or new.dataset_id<>old.dataset_id or new.author_id<>old.author_id or new.revision<>old.revision+1 then raise exception 'Published snapshots are immutable or revision changed.' using errcode='40001'; end if;
  if new.status='test_published' then
   if length(btrim(new.payload->>'sourceText'))=0 then raise exception 'Record the original words before publishing a test note.'; end if;
   new.content_hash:=encode(extensions.digest(new.payload::text,'sha256'),'hex');
  else new.content_hash:=null; end if;
 end if;
 new.updated_at:=now(); return new;
end $$;
create trigger doctor_note_guard before insert or update on public.sahara_doctor_notes for each row execute function healthathon_private.doctor_note_guard();
create function healthathon_private.doctor_dose_guard() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) then raise exception 'Sign in first.'; end if;
 new.logged_at:=now(); new.reporter_id:=auth.uid();
 if new.reported_taken_at>now() then raise exception 'Reported taken time cannot be in the future.'; end if;
 return new;
end $$;
create trigger doctor_dose_guard before insert on public.sahara_doctor_doses for each row execute function healthathon_private.doctor_dose_guard();
create function healthathon_private.doctor_file_guard() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) then raise exception 'Sign in first.'; end if;
 new.uploader_id:=auth.uid(); new.consent_at:=now(); new.created_at:=now(); new.upload_state:='pending'; return new;
end $$;
create trigger doctor_file_guard before insert on public.sahara_doctor_files for each row execute function healthathon_private.doctor_file_guard();

-- Narrow privileged mutation is internal (not an exposed SECURITY DEFINER).
-- It rechecks caller, open account, dataset write access, author and exact revision.
create function healthathon_private.doctor_save_note(p_patient uuid,p_id uuid,p_revision bigint,p_payload jsonb,p_publish boolean default false) returns public.sahara_doctor_notes
language plpgsql security definer set search_path='' as $$
declare saved public.sahara_doctor_notes; dataset uuid;
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) then raise exception 'Sign in first.' using errcode='42501'; end if;
 if p_revision is null or p_revision<0 or p_publish is null then raise exception 'Invalid draft revision.'; end if;
 select dataset_id into dataset from public.sahara_doctor_patients where id=p_patient;
 if dataset is null or not healthathon_private.doctor_dataset_access(dataset,true) then raise exception 'No access to this synthetic patient.' using errcode='42501'; end if;
 if p_revision=0 then
  if p_publish then raise exception 'Save a draft first.'; end if;
  insert into public.sahara_doctor_notes(id,dataset_id,patient_id,payload) values(p_id,dataset,p_patient,p_payload) returning * into saved;
 else
  update public.sahara_doctor_notes set payload=p_payload,status=case when p_publish then 'test_published' else 'draft' end,revision=revision+1
  where id=p_id and patient_id=p_patient and author_id=auth.uid() and revision=p_revision and status='draft' returning * into saved;
  if not found then raise exception 'This draft changed. Refresh; your saved version is unchanged.' using errcode='40001'; end if;
 end if;
 return saved;
end $$;
revoke all on function healthathon_private.doctor_save_note(uuid,uuid,bigint,jsonb,boolean) from public,anon;
grant execute on function healthathon_private.doctor_save_note(uuid,uuid,bigint,jsonb,boolean) to authenticated;
create function public.sahara_doctor_save_note(p_patient uuid,p_id uuid,p_revision bigint,p_payload jsonb,p_publish boolean default false) returns public.sahara_doctor_notes
language sql security invoker set search_path='' as $$
 select healthathon_private.doctor_save_note(p_patient,p_id,p_revision,p_payload,p_publish)
$$;
revoke all on function public.sahara_doctor_save_note(uuid,uuid,bigint,jsonb,boolean) from public,anon;
grant execute on function public.sahara_doctor_save_note(uuid,uuid,bigint,jsonb,boolean) to authenticated;

create function public.sahara_doctor_seed(p_patients jsonb) returns uuid
language plpgsql security invoker set search_path='' as $$
declare dataset uuid; row jsonb; patient uuid; dose jsonb;
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) then raise exception 'Sign in first.' using errcode='42501'; end if;
 perform pg_catalog.pg_advisory_xact_lock(70239314,pg_catalog.hashtext(auth.uid()::text));
 if jsonb_typeof(p_patients) is distinct from 'array' or jsonb_array_length(p_patients)<>6 or octet_length(p_patients::text)>524288
 or (select count(distinct x->>'legacyId') from jsonb_array_elements(p_patients) x)<>6 then raise exception 'Provide the six synthetic patient fixtures.'; end if;
 insert into public.sahara_doctor_datasets(owner_id) values(auth.uid()) on conflict(owner_id) do nothing;
 select id into dataset from public.sahara_doctor_datasets where owner_id=auth.uid();
 for row in select value from jsonb_array_elements(p_patients) loop
  insert into public.sahara_doctor_patients(dataset_id,legacy_id,name,age,care_context,summary,payload)
  values(dataset,row->>'legacyId',row->>'name',(row->>'age')::integer,row->>'careContext',row->>'summary',row->'payload')
  on conflict(dataset_id,legacy_id) do nothing;
  select id into patient from public.sahara_doctor_patients where dataset_id=dataset and legacy_id=row->>'legacyId';
  -- First seed only: repeat runs never overwrite edits or add another initial draft.
  if not exists(select 1 from public.sahara_doctor_notes where patient_id=patient) then
   insert into public.sahara_doctor_notes(dataset_id,patient_id,payload) values(dataset,patient,row->'draft');
  end if;
  for dose in select value from jsonb_array_elements(coalesce(row->'doses','[]'::jsonb)) loop
   insert into public.sahara_doctor_doses(dataset_id,patient_id,medicine_label,planned_at,reported_taken_at,outcome,seed_key,source)
   values(dataset,patient,dose->>'medicineLabel',to_timestamp((dose->>'plannedAt')::double precision/1000),
   case when dose->>'reportedTakenAt' is null then null else to_timestamp((dose->>'reportedTakenAt')::double precision/1000) end,
   dose->>'outcome',dose->>'id','Seeded fictional report · not live ingestion') on conflict(dataset_id,seed_key) do nothing;
  end loop;
 end loop;
 return dataset;
end $$;
revoke all on function public.sahara_doctor_seed(jsonb) from public,anon;
grant execute on function public.sahara_doctor_seed(jsonb) to authenticated;

-- Internal metadata finalisation needs a privileged Storage catalog lookup, not
-- a broad client UPDATE grant. It cannot create/read a file or alter its identity.
create function healthathon_private.doctor_complete_file(p_id uuid) returns public.sahara_doctor_files
language plpgsql security definer set search_path='' as $$
declare saved public.sahara_doctor_files;
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) then raise exception 'Sign in first.' using errcode='42501'; end if;
 select * into saved from public.sahara_doctor_files where id=p_id and uploader_id=auth.uid() for update;
 if not found or not healthathon_private.doctor_dataset_access(saved.dataset_id,true) then raise exception 'No access to this upload.' using errcode='42501'; end if;
 if not exists(select 1 from storage.objects o where o.bucket_id='sahara-doctor-test-files' and o.name=saved.object_path
 and (o.metadata->>'size')::bigint=saved.bytes and o.metadata->>'mimetype'=saved.mime) then raise exception 'Upload incomplete or metadata mismatch. No original is ready yet.'; end if;
 update public.sahara_doctor_files set upload_state='ready' where id=p_id returning * into saved;
 return saved;
end $$;
revoke all on function healthathon_private.doctor_complete_file(uuid) from public,anon;
grant execute on function healthathon_private.doctor_complete_file(uuid) to authenticated;
create function public.sahara_doctor_complete_file(p_id uuid) returns public.sahara_doctor_files
language sql security invoker set search_path='' as $$select healthathon_private.doctor_complete_file(p_id)$$;
revoke all on function public.sahara_doctor_complete_file(uuid) from public,anon;
grant execute on function public.sahara_doctor_complete_file(uuid) to authenticated;

-- Private object storage: no upsert, anonymous reads or public URLs. Metadata first,
-- explicit consent in the client. Path identity and current membership are rechecked.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values
 ('sahara-doctor-test-files','sahara-doctor-test-files',false,4194304,array['application/pdf','image/jpeg','image/png','image/webp','audio/mp4','audio/mpeg','audio/wav','audio/x-wav','audio/ogg']);
create policy doctor_object_insert on storage.objects for insert to authenticated with check(
 bucket_id='sahara-doctor-test-files' and exists(select 1 from public.sahara_doctor_files f
 where f.object_path=name and f.uploader_id=(select auth.uid()) and healthathon_private.doctor_dataset_access(f.dataset_id,true)));
create policy doctor_object_read on storage.objects for select to authenticated using(
 bucket_id='sahara-doctor-test-files' and exists(select 1 from public.sahara_doctor_files f
 where f.object_path=name and f.upload_state='ready' and healthathon_private.doctor_dataset_access(f.dataset_id)));
-- Existing permissive policies may cover other buckets. Restrictive guards below
-- protect ONLY this bucket even if another application's policy grants broad access.
create policy doctor_object_read_guard on storage.objects as restrictive for select to anon,authenticated using(
 bucket_id<>'sahara-doctor-test-files' or (auth.uid() is not null and exists(select 1 from public.sahara_doctor_files f
 where f.object_path=name and f.upload_state='ready' and healthathon_private.doctor_dataset_access(f.dataset_id))));
create policy doctor_object_insert_guard on storage.objects as restrictive for insert to anon,authenticated with check(
 bucket_id<>'sahara-doctor-test-files' or (auth.uid() is not null and exists(select 1 from public.sahara_doctor_files f
 where f.object_path=name and f.uploader_id=(select auth.uid()) and healthathon_private.doctor_dataset_access(f.dataset_id,true))));
create policy doctor_object_update_guard on storage.objects as restrictive for update to anon,authenticated
 using(bucket_id<>'sahara-doctor-test-files') with check(bucket_id<>'sahara-doctor-test-files');
create policy doctor_object_delete_guard on storage.objects as restrictive for delete to anon,authenticated
 using(bucket_id<>'sahara-doctor-test-files');
-- No UPDATE/DELETE policies: uploaded versions cannot be silently replaced.

revoke all on function healthathon_private.doctor_note_guard(),healthathon_private.doctor_dose_guard(),healthathon_private.doctor_file_guard() from public,anon;
commit;
