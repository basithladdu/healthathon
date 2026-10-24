begin;
-- Actual patient medicines. These tables are unrelated to the synthetic doctor dataset.
create table public.healthathon_medicine_courses (
 person_id uuid not null references public.healthathon_people(id) on delete cascade,
 medicine_id uuid not null, medicine_name text not null check(length(medicine_name) between 1 and 200),
 directions text not null default '', past boolean not null default false,
 reason text not null default '' check(length(reason)<=1000), changed_at timestamptz not null default now(),
 actor_id uuid not null, revision bigint not null default 1 check(revision>0), primary key(person_id,medicine_id)
);
create table public.healthathon_medicine_events (
 id uuid primary key default gen_random_uuid(), person_id uuid not null references public.healthathon_people(id) on delete cascade,
 medicine_id uuid not null, medicine_name text not null check(length(medicine_name) between 1 and 200),
 outcome text not null check(outcome in ('taken','skipped','planned','unmarked','course_past','course_current')),
 planned_at timestamptz, reported_taken_at timestamptz, reported_date date,
 logged_at timestamptz not null default now(), actor_id uuid not null,
 reporter_label text not null default '' check(length(reporter_label)<=100), note text not null default '' check(length(note)<=2000),
 source text not null check(source in ('manual','daily-checkoff','course-change')),
 check(reported_taken_at is null or outcome='taken'),
 check(planned_at is null or (isfinite(planned_at) and extract(epoch from planned_at)>0)),
 check(reported_taken_at is null or (isfinite(reported_taken_at) and extract(epoch from reported_taken_at)>0)),
 check(outcome<>'planned' or planned_at is not null)
);
create index medicine_events_person_time on public.healthathon_medicine_events(person_id,logged_at desc,id);
create index medicine_events_medicine on public.healthathon_medicine_events(person_id,medicine_id);
alter table public.healthathon_medicine_courses enable row level security;
alter table public.healthathon_medicine_events enable row level security;

create function healthathon_private.medicine_access(p_person uuid,p_write boolean default false) returns boolean
language plpgsql stable security definer set search_path='' as $$
begin
 if auth.uid() is null then return false; end if;
 return healthathon_private.can_read(p_person)
  and healthathon_private.has_permission(p_person,'calendar')
  and healthathon_private.has_permission(p_person,'reports')
  and (not p_write or healthathon_private.can_edit(p_person));
end $$;
revoke all on function healthathon_private.medicine_access(uuid,boolean) from public,anon;
grant execute on function healthathon_private.medicine_access(uuid,boolean) to authenticated;
create function healthathon_private.medicine_record_access(p_person uuid,p_medicine uuid,p_write boolean default false) returns boolean
language plpgsql stable security definer set search_path='' as $$
declare med public.healthathon_records;
begin
 if not coalesce(healthathon_private.medicine_access(p_person,p_write),false) then return false; end if;
 select * into med from public.healthathon_records where id=p_medicine and person_id=p_person and kind='entry';
 if found then return med.data->>'kind'='medicine' and coalesce(healthathon_private.can_read_record(p_person,'entry',med.data),false); end if;
 -- Deleted-source history is deliberately owner-only: old member permissions cannot be reconstructed safely.
 return not p_write and healthathon_private.is_owner(p_person);
end $$;
revoke all on function healthathon_private.medicine_record_access(uuid,uuid,boolean) from public,anon;
grant execute on function healthathon_private.medicine_record_access(uuid,uuid,boolean) to authenticated;
create policy medicine_courses_read on public.healthathon_medicine_courses for select to authenticated using(healthathon_private.medicine_record_access(person_id,medicine_id));
create policy medicine_events_read on public.healthathon_medicine_events for select to authenticated using(healthathon_private.medicine_record_access(person_id,medicine_id));
revoke all on public.healthathon_medicine_courses,public.healthathon_medicine_events from public,anon,authenticated;
grant select on public.healthathon_medicine_courses,public.healthathon_medicine_events to authenticated;

create function healthathon_private.set_medicine_course(p_person uuid,p_medicine uuid,p_past boolean,p_reason text,p_expected_revision bigint) returns boolean
language plpgsql security definer set search_path='' as $$
declare med public.healthathon_records; previous public.healthathon_medicine_courses;
begin
 if not coalesce(healthathon_private.medicine_record_access(p_person,p_medicine,true),false) then raise exception 'Medicine editing access is required.' using errcode='42501'; end if;
 if p_past is null or p_reason is null or length(p_reason)>1000 then raise exception 'Invalid medicine status.'; end if;
 select * into med from public.healthathon_records where id=p_medicine and person_id=p_person and kind='entry' and data->>'kind'='medicine' for update;
 if not found then raise exception 'This medicine is unavailable. Refresh first.' using errcode='42501'; end if;
 select * into previous from public.healthathon_medicine_courses where person_id=p_person and medicine_id=p_medicine;
 if found and previous.past=p_past and previous.reason=p_reason and previous.actor_id=auth.uid() and previous.revision=p_expected_revision+1 then return true; end if;
 if p_expected_revision is null or coalesce(previous.revision,0)<>p_expected_revision then raise exception 'This medicine changed elsewhere. Refresh before changing its course.' using errcode='40001'; end if;
 insert into public.healthathon_medicine_courses(person_id,medicine_id,medicine_name,directions,past,reason,actor_id)
 values(p_person,p_medicine,med.data->>'title',coalesce(med.data->>'detail',''),p_past,p_reason,auth.uid())
 on conflict(person_id,medicine_id) do update set past=excluded.past,reason=excluded.reason,changed_at=now(),actor_id=auth.uid(),revision=healthathon_medicine_courses.revision+1;
 insert into public.healthathon_medicine_events(person_id,medicine_id,medicine_name,outcome,actor_id,reporter_label,note,source)
 values(p_person,p_medicine,med.data->>'title',case when p_past then 'course_past' else 'course_current' end,auth.uid(),'Signed-in account',p_reason,'course-change');
 return true;
end $$;
create function public.healthathon_set_medicine_course(p_person uuid,p_medicine uuid,p_past boolean,p_reason text,p_expected_revision bigint) returns boolean
language sql security invoker set search_path='' as $$ select healthathon_private.set_medicine_course(p_person,p_medicine,p_past,p_reason,p_expected_revision) $$;

create function healthathon_private.report_medicine(p_event jsonb) returns uuid
language plpgsql security definer set search_path='' as $$
declare person uuid; medicine uuid; event_id uuid; med public.healthathon_records; old public.healthathon_medicine_events; outcome text; planned timestamptz; taken timestamptz;
begin
 if jsonb_typeof(p_event)<>'object' then raise exception 'Invalid medicine report.'; end if;
 person:=(p_event->>'person_id')::uuid; medicine:=(p_event->>'medicine_id')::uuid; event_id:=(p_event->>'id')::uuid;
 if not coalesce(healthathon_private.medicine_record_access(person,medicine,true),false) then raise exception 'Medicine editing access is required.' using errcode='42501'; end if;
 outcome:=p_event->>'outcome'; planned:=nullif(p_event->>'planned_at','')::timestamptz; taken:=nullif(p_event->>'reported_taken_at','')::timestamptz;
 if outcome is null or outcome not in ('taken','skipped','planned') or (outcome='planned' and planned is null)
  or (taken is not null and (outcome<>'taken' or taken>now()))
  or length(coalesce(p_event->>'reporter_label',''))>100 or length(coalesce(p_event->>'note',''))>2000
  or nullif(p_event->>'reported_date','') is not null then raise exception 'Invalid medicine report fields.'; end if;
 select * into med from public.healthathon_records where id=medicine and person_id=person and kind='entry' and data->>'kind'='medicine' for update;
 if not found then raise exception 'This medicine is unavailable.' using errcode='42501'; end if;
 select * into old from public.healthathon_medicine_events where id=event_id;
 if found then
  if old.person_id=person and old.medicine_id=medicine and old.actor_id=auth.uid() and old.outcome=outcome
   and old.planned_at is not distinct from planned and old.reported_taken_at is not distinct from taken
   and old.reporter_label=coalesce(p_event->>'reporter_label','') and old.note=coalesce(p_event->>'note','') then return event_id; end if;
  raise exception 'This report identifier belongs to different information.' using errcode='23505';
 end if;
 insert into public.healthathon_medicine_events(id,person_id,medicine_id,medicine_name,outcome,planned_at,reported_taken_at,actor_id,reporter_label,note,source)
 values(event_id,person,medicine,med.data->>'title',outcome,planned,taken,auth.uid(),coalesce(p_event->>'reporter_label',''),coalesce(p_event->>'note',''),'manual');
 return event_id;
end $$;
create function public.healthathon_report_medicine(p_event jsonb) returns uuid
language sql security invoker set search_path='' as $$ select healthathon_private.report_medicine(p_event) $$;

-- A new checkbox write and its date-only report are in the SAME database transaction.
-- Existing checkboxes are not backfilled; no ingestion time or reporter identity is invented.
create function healthathon_private.capture_medicine_checkoff() returns trigger
language plpgsql security definer set search_path='' as $$
declare before_date text:=''; after_date text:=coalesce(new.data->>'doneOn',''); reported_day date;
begin
 if new.kind is distinct from 'entry' or new.data->>'kind' is distinct from 'medicine' then return new; end if;
 if tg_op='UPDATE' then before_date:=coalesce(old.data->>'doneOn',''); end if;
 if before_date=after_date or auth.uid() is null then return new; end if;
 if after_date<>'' and after_date !~ '^\d{4}-\d{2}-\d{2}$' then raise exception 'Invalid checkbox date.'; end if;
 if after_date<>'' then reported_day:=after_date::date;
 else begin reported_day:=nullif(before_date,'')::date; exception when invalid_datetime_format or datetime_field_overflow then reported_day:=null; end; end if;
 if exists(select 1 from public.healthathon_medicine_courses where person_id=new.person_id and medicine_id=new.id and past) then raise exception 'This medicine is in past medicines. Refresh before changing it.'; end if;
 insert into public.healthathon_medicine_events(person_id,medicine_id,medicine_name,outcome,reported_date,actor_id,reporter_label,note,source)
 values(new.person_id,new.id,new.data->>'title',case when after_date='' then 'unmarked' else 'taken' end,
  reported_day,auth.uid(),'Signed-in account report',
  case when after_date='' then 'Daily check-off removed; earlier report retained.' else 'Daily check-off. Exact taken time was not recorded.' end,'daily-checkoff');
 return new;
end $$;
create trigger capture_medicine_checkoff after insert or update of data on public.healthathon_records for each row execute function healthathon_private.capture_medicine_checkoff();

revoke all on function healthathon_private.set_medicine_course(uuid,uuid,boolean,text,bigint),healthathon_private.report_medicine(jsonb),healthathon_private.capture_medicine_checkoff() from public,anon,authenticated;
grant execute on function healthathon_private.set_medicine_course(uuid,uuid,boolean,text,bigint),healthathon_private.report_medicine(jsonb) to authenticated;
revoke all on function public.healthathon_set_medicine_course(uuid,uuid,boolean,text,bigint),public.healthathon_report_medicine(jsonb) from public,anon;
grant execute on function public.healthathon_set_medicine_course(uuid,uuid,boolean,text,bigint),public.healthathon_report_medicine(jsonb) to authenticated;
commit;
