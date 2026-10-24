begin;
create table public.healthathon_prescriptions (
 id uuid primary key,person_id uuid not null references public.healthathon_people(id) on delete cascade,
 title text not null check(length(title) between 1 and 200),prescribed_on date,created_at bigint not null,
 ended_at bigint not null default 0,ended_on date,end_reason text not null default '',replaces_id uuid,
 revision bigint not null default 1 check(revision>0),check(created_at>0 and ended_at>=0)
);
create index prescriptions_person on public.healthathon_prescriptions(person_id,created_at desc,id);
create table public.healthathon_prescription_state (
 person_id uuid primary key references public.healthathon_people(id) on delete cascade,
 current_id uuid not null,revision bigint not null check(revision>0)
);
create table public.healthathon_prescription_medicines (
 prescription_id uuid not null references public.healthathon_prescriptions(id) on delete cascade,
 medicine_id uuid not null,person_id uuid not null references public.healthathon_people(id) on delete cascade,
 name text not null check(length(name) between 1 and 200),instructions text not null check(length(instructions)<=4000),
 position integer not null check(position>=0),added_at bigint not null,removed_at bigint not null default 0,
 primary key(prescription_id,medicine_id),check(added_at>0 and removed_at>=0)
);
create index prescription_medicines_person on public.healthathon_prescription_medicines(person_id,medicine_id);
create table public.healthathon_prescription_mutations (
 request_id uuid primary key,person_id uuid not null references public.healthathon_people(id) on delete cascade,
 prescription_id uuid not null,actor_id uuid not null,operation text not null check(operation in ('edit','replace')),
 payload jsonb not null,recorded_at bigint not null,state_revision bigint not null,group_revision bigint not null
);
create index prescription_mutations_person on public.healthathon_prescription_mutations(person_id);
-- Serialize legacy entry/course edits with group adoption so omitted or concurrently
-- inserted medicines cannot survive a replacement unnoticed. Deadlocks abort safely.
create function healthathon_private.lock_prescription_person() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if TG_TABLE_NAME='healthathon_records' then
  if TG_OP='INSERT' and new.kind='entry' and new.data->>'kind'='medicine' then perform 1 from public.healthathon_people where id=new.person_id for update;
  elsif TG_OP='DELETE' and old.kind='entry' and old.data->>'kind'='medicine' then perform 1 from public.healthathon_people where id=old.person_id for update;
  elsif TG_OP='UPDATE' and ((old.kind='entry' and old.data->>'kind'='medicine') or (new.kind='entry' and new.data->>'kind'='medicine')) then perform 1 from public.healthathon_people where id in(old.person_id,new.person_id) order by id for update; end if;
 else
  if TG_OP='DELETE' then perform 1 from public.healthathon_people where id=old.person_id for update;
  else perform 1 from public.healthathon_people where id=new.person_id for update; end if;
 end if;
 if TG_OP='DELETE' then return old; else return new; end if;
end $$;
revoke all on function healthathon_private.lock_prescription_person() from public,anon,authenticated,service_role;
create trigger prescription_entry_serialization before insert or update or delete on public.healthathon_records for each row execute function healthathon_private.lock_prescription_person();
create trigger prescription_course_serialization before insert or update or delete on public.healthathon_medicine_courses for each row execute function healthathon_private.lock_prescription_person();
alter table public.healthathon_prescriptions enable row level security;
alter table public.healthathon_prescription_state enable row level security;
alter table public.healthathon_prescription_medicines enable row level security;
alter table public.healthathon_prescription_mutations enable row level security;
revoke all on public.healthathon_prescriptions,public.healthathon_prescription_state,public.healthathon_prescription_medicines,public.healthathon_prescription_mutations from public,anon,authenticated,service_role;

create function healthathon_private.prescription_access(p_person uuid,p_write boolean default false) returns boolean
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then return false; end if;
 perform healthathon_private.require_open_account();
 return coalesce(healthathon_private.medicine_access(p_person,p_write),false);
end $$;
create function healthathon_private.prescription_group_access(p_person uuid,p_group uuid) returns boolean
language plpgsql stable security definer set search_path='' as $$
begin
 if not healthathon_private.prescription_access(p_person,false) then return false; end if;
 if not exists(select 1 from public.healthathon_prescriptions where id=p_group and person_id=p_person) then return false; end if;
 return not exists(select 1 from public.healthathon_prescription_medicines m where m.person_id=p_person and m.prescription_id=p_group
 and not coalesce(healthathon_private.medicine_record_access(p_person,m.medicine_id,false),false));
end $$;
create policy prescription_read on public.healthathon_prescriptions for select to authenticated using(healthathon_private.prescription_group_access(person_id,id));
create policy prescription_state_read on public.healthathon_prescription_state for select to authenticated using(healthathon_private.prescription_group_access(person_id,current_id));
create policy prescription_medicine_read on public.healthathon_prescription_medicines for select to authenticated using(healthathon_private.prescription_group_access(person_id,prescription_id));
-- Receipts intentionally have no client SELECT policy or direct table grants.
create function healthathon_private.prescription_snapshot(p_person uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare state public.healthathon_prescription_state;
begin
 if not healthathon_private.prescription_access(p_person,false) then raise exception 'Prescription access is unavailable.' using errcode='42501'; end if;
 select * into state from public.healthathon_prescription_state where person_id=p_person;
 if found and not healthathon_private.prescription_group_access(p_person,state.current_id) then raise exception 'The current prescription is not accessible.' using errcode='42501'; end if;
 return jsonb_build_object('version',1,'state',case when state.person_id is null then jsonb_build_object('person_id',p_person,'current_id',null,'revision',0) else to_jsonb(state) end,
 'groups',coalesce((select jsonb_agg(to_jsonb(g) order by g.created_at desc,g.id) from public.healthathon_prescriptions g where g.person_id=p_person and healthathon_private.prescription_group_access(p_person,g.id)),'[]'::jsonb),
 'medicines',coalesce((select jsonb_agg(to_jsonb(m) order by m.position,m.medicine_id) from public.healthathon_prescription_medicines m where m.person_id=p_person and healthathon_private.prescription_group_access(p_person,m.prescription_id)),'[]'::jsonb));
end $$;
create function public.healthathon_prescription_snapshot(p_person uuid) returns jsonb language sql stable security invoker set search_path='' as $$select healthathon_private.prescription_snapshot(p_person)$$;
create function public.healthathon_prescription_capabilities() returns jsonb language sql stable security invoker set search_path='' as $$select jsonb_build_object('version',1,'atomicReplacement',true,'batchEdit',true)$$;

create function healthathon_private.save_prescription(p_request jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare person uuid;group_id uuid;v_request uuid;state public.healthathon_prescription_state;current_group public.healthathon_prescriptions;
 receipt public.healthathon_prescription_mutations;med public.healthathon_records;input jsonb;ids uuid[];old_ids uuid[];entry_id uuid;
replacement boolean;expected_state bigint;expected_group bigint;title text;prescribed date;at_ms bigint;group_revision bigint;legacy_group uuid;position integer:=0;
authoritative uuid[];fingerprint_source text:='';fingerprint text;changed integer;
begin
 if auth.uid() is null then raise exception 'Sign in first.' using errcode='42501'; end if;
 perform healthathon_private.require_open_account();
 if jsonb_typeof(p_request)<>'object' or octet_length(p_request::text)>250000
 or p_request-array['requestId','person','group','stateRevision','groupRevision','replace','confirmed','title','date','legacy','legacyFingerprint','medicines']<>'{}'::jsonb
 or jsonb_typeof(p_request->'replace') is distinct from 'boolean' or jsonb_typeof(p_request->'confirmed') is distinct from 'boolean'
 or exists(select 1 from unnest(array['requestId','person','group','title','date']) k where jsonb_typeof(p_request->k) is distinct from 'string')
 or exists(select 1 from unnest(array['stateRevision','groupRevision']) k where jsonb_typeof(p_request->k) is distinct from 'number' or p_request->>k !~ '^[0-9]{1,18}$')
 or (p_request ? 'legacyFingerprint' and (jsonb_typeof(p_request->'legacyFingerprint') is distinct from 'string' or (p_request->>'legacyFingerprint'<>'' and p_request->>'legacyFingerprint' !~ '^[a-f0-9]{64}$')))
 or (coalesce(p_request->>'date','')<>'' and p_request->>'date' !~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$') then raise exception 'Invalid prescription request.'; end if;
 person:=(p_request->>'person')::uuid;group_id:=(p_request->>'group')::uuid;v_request:=(p_request->>'requestId')::uuid;
 expected_state:=(p_request->>'stateRevision')::bigint;expected_group:=(p_request->>'groupRevision')::bigint;
 replacement:=(p_request->>'replace')::boolean;title:=btrim(p_request->>'title');prescribed:=nullif(p_request->>'date','')::date;
 if person is null or group_id is null or v_request is null or expected_state is null or expected_group is null or expected_state<0 or expected_group<0 or replacement is null
 or title is null or length(title) not between 1 and 200 or (replacement and coalesce((p_request->>'confirmed')::boolean,false)<>true)
 or jsonb_typeof(p_request->'medicines') is distinct from 'array' or jsonb_array_length(p_request->'medicines') not between 1 and 50
 or jsonb_typeof(p_request->'legacy') is distinct from 'array' then raise exception 'Review and confirm every prescription field.'; end if;
 if not healthathon_private.prescription_access(person,true) then raise exception 'Prescription editing access is required.' using errcode='42501'; end if;
 perform 1 from public.healthathon_people where id=person for update;
 if not found then raise exception 'This care profile is unavailable.' using errcode='42501'; end if;
 select * into receipt from public.healthathon_prescription_mutations m where m.request_id=v_request;
 if found then
  if receipt.person_id<>person or receipt.actor_id<>auth.uid() or receipt.payload<>p_request then raise exception 'This save identifier belongs to different information.' using errcode='40001'; end if;
  return healthathon_private.prescription_snapshot(person);
 end if;
 select * into state from public.healthathon_prescription_state where person_id=person;
 select * into current_group from public.healthathon_prescriptions where id=state.current_id and person_id=person;
 if coalesce(state.revision,0)<>expected_state or coalesce(current_group.revision,0)<>expected_group then raise exception 'The prescription changed. Refresh before saving.' using errcode='40001'; end if;
 if current_group.id is not null and not healthathon_private.prescription_group_access(person,current_group.id) then raise exception 'The original prescription is not accessible.' using errcode='42501'; end if;
 if (not replacement and current_group.id is not null and current_group.id<>group_id)
 or ((replacement or current_group.id is null) and exists(select 1 from public.healthathon_prescriptions where id=group_id)) then raise exception 'Invalid current prescription.' using errcode='40001'; end if;
 select array_agg((value->>'id')::uuid) into ids from jsonb_array_elements(p_request->'medicines');
 if cardinality(ids)<>(select count(distinct id) from unnest(ids) id) or array_position(ids,null) is not null then raise exception 'Each medicine needs a unique identifier.'; end if;
 select coalesce(array_agg(value::uuid order by value::uuid),'{}'::uuid[]) into old_ids from jsonb_array_elements_text(p_request->'legacy');
 select coalesce(array_agg(r.id order by r.id),'{}'::uuid[]) into authoritative from public.healthathon_records r where r.person_id=person and r.kind='entry' and r.data->>'kind'='medicine'
 and not exists(select 1 from public.healthathon_medicine_courses c where c.person_id=person and c.medicine_id=r.id and c.past)
 and (not exists(select 1 from public.healthathon_prescription_medicines m where m.person_id=person and m.medicine_id=r.id)
 or exists(select 1 from public.healthathon_prescription_medicines m join public.healthathon_prescription_state s on s.person_id=m.person_id and s.current_id=m.prescription_id where m.person_id=person and m.medicine_id=r.id and m.removed_at=0));
 if old_ids is distinct from authoritative then raise exception 'The current medicine list changed. Review the complete list.' using errcode='40001'; end if;
 for med in select * from public.healthathon_records r where r.person_id=person and r.kind='entry' and r.id=any(authoritative) order by r.id loop
  select fingerprint_source||string_agg(octet_length(v.value)::text||':'||v.value,'' order by v.ord) into fingerprint_source from (values(1,med.id::text),(2,med.revision::text),(3,coalesce(med.data->>'title','')),(4,coalesce(med.data->>'detail','')),(5,coalesce(med.data->>'doneOn','')),(6,coalesce(med.data->>'created','0'))) v(ord,value);
 end loop;
 fingerprint:=encode(sha256(convert_to(fingerprint_source,'UTF8')),'hex');
 if not(cardinality(authoritative)=0 and coalesce(p_request->>'legacyFingerprint','')='') and coalesce(p_request->>'legacyFingerprint','')<>fingerprint then raise exception 'The current medicine details changed. Reopen the reviewed list.' using errcode='40001'; end if;
 foreach entry_id in array old_ids loop
  if not healthathon_private.medicine_record_access(person,entry_id,true) then raise exception 'An original medicine is not editable.' using errcode='42501'; end if;
 end loop;
 for input in select value from jsonb_array_elements(p_request->'medicines') loop
  if jsonb_typeof(input->'name') is distinct from 'string' or length(btrim(input->>'name')) not between 1 and 200
  or jsonb_typeof(input->'instructions') is distinct from 'string' or length(input->>'instructions')>4000 then raise exception 'Review the medicine name and instructions.'; end if;
  entry_id:=(input->>'id')::uuid;
  select * into med from public.healthathon_records where id=entry_id and kind='entry' for update;
  if found and (med.person_id<>person or med.kind<>'entry' or med.data->>'kind' is distinct from 'medicine' or replacement or not(entry_id=any(old_ids))
   or not healthathon_private.medicine_record_access(person,entry_id,true)) then raise exception 'This medicine is not in the editable current prescription.' using errcode='42501'; end if;
  if not coalesce(healthathon_private.can_read_record(person,'entry',jsonb_build_object('kind','medicine','title',input->>'name','detail',input->>'instructions')),false) then raise exception 'Medicine instructions require unavailable permissions.' using errcode='42501'; end if;
 end loop;
 at_ms:=floor(extract(epoch from clock_timestamp())*1000)::bigint;
 if replacement and current_group.id is not null then
  update public.healthathon_prescription_medicines m set name=r.data->>'title',instructions=coalesce(r.data->>'detail','') from public.healthathon_records r
  where m.prescription_id=current_group.id and m.person_id=person and m.removed_at=0 and m.medicine_id=any(authoritative)
  and r.person_id=person and r.kind='entry' and r.id=m.medicine_id;
  update public.healthathon_prescriptions set ended_at=at_ms,end_reason='Reviewed last-active list saved at replacement',revision=revision+1 where id=current_group.id;
 end if;
 group_revision:=case when not replacement and current_group.id is not null then current_group.revision+1 else 1 end;
 insert into public.healthathon_prescriptions(id,person_id,title,prescribed_on,created_at,replaces_id,revision)
 values(group_id,person,title,prescribed,at_ms,case when replacement then current_group.id else null end,group_revision)
 on conflict(id) do update set title=excluded.title,prescribed_on=excluded.prescribed_on,revision=excluded.revision;
 if not replacement then
  update public.healthathon_prescription_medicines set removed_at=at_ms where prescription_id=group_id and removed_at=0 and not(medicine_id=any(ids));
 end if;
 if cardinality(old_ids)>0 and (not replacement or exists(select 1 from unnest(old_ids) old_id where not exists(select 1 from public.healthathon_prescription_medicines m where m.person_id=person and m.medicine_id=old_id))) then
  if replacement then
   legacy_group:=gen_random_uuid();
   insert into public.healthathon_prescriptions(id,person_id,title,created_at,ended_at,end_reason) values(legacy_group,person,'Earlier ungrouped medicines',at_ms,at_ms,'Replaced after confirmation; original prescription date not recorded');
  else legacy_group:=group_id; end if;
  insert into public.healthathon_prescription_medicines(prescription_id,medicine_id,person_id,name,instructions,position,added_at,removed_at)
  select legacy_group,r.id,person,r.data->>'title',coalesce(r.data->>'detail',''),row_number() over(order by r.id)-1,at_ms,case when replacement then 0 else at_ms end
  from public.healthathon_records r where r.kind='entry' and r.person_id=person and r.id=any(old_ids) and (replacement or not(r.id=any(ids)))
  and not exists(select 1 from public.healthathon_prescription_medicines m where m.person_id=person and m.medicine_id=r.id);
 end if;
 for input in select value from jsonb_array_elements(p_request->'medicines') loop
  entry_id:=(input->>'id')::uuid;
  insert into public.healthathon_records(id,person_id,kind,data) values(entry_id,person,'entry',jsonb_build_object('kind','medicine','title',btrim(input->>'name'),'detail',input->>'instructions','due',0,'doneOn','','created',at_ms))
  on conflict(kind,id) do update set data=healthathon_records.data||jsonb_build_object('title',btrim(input->>'name'),'detail',input->>'instructions'),revision=healthathon_records.revision+1,updated_at=now();
  insert into public.healthathon_prescription_medicines(prescription_id,medicine_id,person_id,name,instructions,position,added_at)
  values(group_id,entry_id,person,btrim(input->>'name'),input->>'instructions',position,at_ms)
  on conflict(prescription_id,medicine_id) do update set name=excluded.name,instructions=excluded.instructions,position=excluded.position,removed_at=0;
  position:=position+1;
 end loop;
 -- Compatibility course flags are written in this same transaction: Patient/Family
 -- lists and reminders never see an ended prescription as a still-active regimen.
 foreach entry_id in array (old_ids||ids) loop
  select * into med from public.healthathon_records r where r.kind='entry' and r.id=entry_id and r.person_id=person;
  insert into public.healthathon_medicine_courses(person_id,medicine_id,medicine_name,directions,past,reason,actor_id)
  values(person,entry_id,med.data->>'title',coalesce(med.data->>'detail',''),not(entry_id=any(ids)),'Prescription list changed',auth.uid())
  on conflict(person_id,medicine_id) do update set past=excluded.past,reason=excluded.reason,changed_at=now(),actor_id=auth.uid(),revision=healthathon_medicine_courses.revision+1
  where healthathon_medicine_courses.past is distinct from excluded.past;
  get diagnostics changed=row_count;
  if changed>0 then insert into public.healthathon_medicine_events(person_id,medicine_id,medicine_name,outcome,actor_id,reporter_label,note,source)
  values(person,entry_id,med.data->>'title',case when entry_id=any(ids) then 'course_current' else 'course_past' end,auth.uid(),'Signed-in account','Prescription list changed','course-change'); end if;
 end loop;
 insert into public.healthathon_prescription_state(person_id,current_id,revision) values(person,group_id,expected_state+1)
 on conflict(person_id) do update set current_id=excluded.current_id,revision=excluded.revision;
 insert into public.healthathon_prescription_mutations(request_id,person_id,prescription_id,actor_id,operation,payload,recorded_at,state_revision,group_revision)
 values(v_request,person,group_id,auth.uid(),case when replacement then 'replace' else 'edit' end,p_request,at_ms,expected_state+1,group_revision);
 return healthathon_private.prescription_snapshot(person);
end $$;
create function public.healthathon_save_prescription(p_request jsonb) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.save_prescription(p_request)$$;
revoke all on function healthathon_private.prescription_access(uuid,boolean),healthathon_private.prescription_group_access(uuid,uuid),healthathon_private.prescription_snapshot(uuid),healthathon_private.save_prescription(jsonb),public.healthathon_prescription_snapshot(uuid),public.healthathon_prescription_capabilities(),public.healthathon_save_prescription(jsonb) from public,anon,service_role;
grant execute on function healthathon_private.prescription_access(uuid,boolean),healthathon_private.prescription_group_access(uuid,uuid),healthathon_private.prescription_snapshot(uuid),healthathon_private.save_prescription(jsonb),public.healthathon_prescription_snapshot(uuid),public.healthathon_prescription_capabilities(),public.healthathon_save_prescription(jsonb) to authenticated;
commit;
