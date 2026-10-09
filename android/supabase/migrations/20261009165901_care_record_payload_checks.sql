create or replace function healthathon_private.save_record(p_id uuid,p_person uuid,p_kind text,p_data jsonb,p_revision bigint)
returns public.healthathon_records language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_records;
begin
  if auth.uid() is null then raise exception 'Sign in to save.' using errcode='42501'; end if;
  if p_kind is null or p_kind not in ('entry','note','ack') or p_data is null or jsonb_typeof(p_data)<>'object' then raise exception 'Invalid record'; end if;
  if not healthathon_private.can_edit(p_person) and not (p_kind='ack' and healthathon_private.can_read(p_person)) then raise exception 'You cannot change these records.' using errcode='42501'; end if;
  perform 1 from public.healthathon_people where id=p_person for update;
  if p_revision is null or p_revision<0 then raise exception 'Invalid revision'; end if;
  if p_kind='entry' and (coalesce(jsonb_typeof(p_data->'title'),'')<>'string' or coalesce(jsonb_typeof(p_data->'created'),'')<>'number' or coalesce(p_data->>'created','') !~ '^[0-9]{1,15}$') then raise exception 'Invalid care entry'; end if;
  if p_kind='entry' and p_data ? 'due' and (jsonb_typeof(p_data->'due')<>'number' or p_data->>'due' !~ '^[0-9]{1,15}$') then raise exception 'Invalid visit date'; end if;
  if p_kind='note' and (coalesce(jsonb_typeof(p_data->'personName'),'')<>'string' or length(btrim(coalesce(p_data->>'personName',''))) not between 1 and 100 or length(coalesce(p_data->>'doctor',''))>100 or coalesce(jsonb_typeof(p_data->'published'),'')<>'number' or coalesce(p_data->>'published','') !~ '^[0-9]{1,15}$') then raise exception 'Invalid care note'; end if;
  if p_kind='ack' and (coalesce(jsonb_typeof(p_data->'at'),'')<>'number' or coalesce(p_data->>'at','') !~ '^[0-9]{1,15}$') then raise exception 'Invalid confirmation'; end if;
  if exists(select 1 from jsonb_each(p_data) x where x.key in ('detail','doneOn','source','doctor','contentHash','priorities','participants','topics','questions','nextSteps','name','relationship') and jsonb_typeof(x.value)<>'string') then raise exception 'Use text for the care note fields'; end if;

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
