-- Deliberately public Edge intake; all database access remains service-only.
-- No existing care/auth/report object or privilege is changed.
begin;

create function public.sahara_feedback_valid(p jsonb)
returns boolean language plpgsql immutable security invoker set search_path = '' as $$
declare k text; v text; allowed text[]; extra text; selected text;
begin
  if p is null or jsonb_typeof(p) <> 'object' then return false; end if;
  if p->>'kind' = 'app' then
    allowed := array['kind','submissionId','website','rating','role','category','message','consent'];
  elsif p->>'kind' = 'hospital' then
    allowed := array['kind','submissionId','website','role','roleOther','fullName','council','councilOther','registrationState','registrationNumber','hospitalName','institutionType','institutionOther','connection','connectionOther','state','city','latitude','longitude','services','morphine','supply','familyNotes','accuracy','consent'];
  else return false; end if;
  if (select count(*) from jsonb_object_keys(p)) <> cardinality(allowed) or exists(select 1 from jsonb_object_keys(p) t(key) where not (key = any(allowed))) then return false; end if;
  if jsonb_typeof(p->'submissionId') <> 'string' or (p->>'submissionId') !~ '^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$' or p->'website' <> '""'::jsonb or p->'consent' <> 'true'::jsonb then return false; end if;
  -- Every nonnumeric/nonboolean/nonarray input is normalized, bounded text.
  foreach k in array allowed loop
    if k = any(array['rating','latitude','longitude','services','consent','accuracy']) then continue; end if;
    if jsonb_typeof(p->k) <> 'string' then return false; end if;
    v := p->>k;
    if v <> btrim(v) or length(v) > 1000 or translate(v,E'\n\r\t','') ~ '[[:cntrl:]]' then return false; end if;
  end loop;
  if p->>'kind' = 'app' then
    return jsonb_typeof(p->'rating') = 'number' and (p->>'rating') ~ '^[1-5]$'
      and p->>'role' = any(array['patient','caregiver','clinician','other'])
      and p->>'category' = any(array['navigation','design','bug','idea','other'])
      and length(p->>'message') <= 1000;
  end if;
  if not (p->>'role' = any(array['oncologist','palliative_physician','physician','resident','nurse','pharmacist','other'])) or not (p->>'council' = any(array['nmc','state_medical','state_nursing','state_pharmacy','other'])) or not (p->>'institutionType' = any(array['government_hospital','government_medical_college','private_hospital','private_medical_college','private_clinic','ngo_hospice','other'])) or not (p->>'connection' = any(array['works_here','refers_here','other'])) then return false; end if;
  foreach selected in array array['role','council','institutionType','connection'] loop
    extra := case selected when 'role' then 'roleOther' when 'council' then 'councilOther' when 'institutionType' then 'institutionOther' else 'connectionOther' end;
    if (p->>selected = 'other' and length(p->>extra) not between 2 and 120) or (p->>selected <> 'other' and p->>extra <> '') then return false; end if;
  end loop;
  if length(p->>'fullName') not between 2 and 120 or (p->>'registrationNumber') !~ '^[A-Za-z0-9/-]{3,20}$' or length(p->>'hospitalName') not between 2 and 160 or length(p->>'city') not between 2 and 80 or p->'accuracy' <> 'true'::jsonb then return false; end if;
  allowed := array['Andaman and Nicobar Islands','Andhra Pradesh','Arunachal Pradesh','Assam','Bihar','Chandigarh','Chhattisgarh','Dadra and Nagar Haveli and Daman and Diu','Delhi','Goa','Gujarat','Haryana','Himachal Pradesh','Jammu and Kashmir','Jharkhand','Karnataka','Kerala','Ladakh','Lakshadweep','Madhya Pradesh','Maharashtra','Manipur','Meghalaya','Mizoram','Nagaland','Odisha','Puducherry','Punjab','Rajasthan','Sikkim','Tamil Nadu','Telangana','Tripura','Uttar Pradesh','Uttarakhand','West Bengal'];
  if not (p->>'state' = any(allowed)) or (p->>'council' = 'nmc' and p->>'registrationState' <> '') or (p->>'council' <> 'nmc' and not (p->>'registrationState' = any(allowed))) then return false; end if;
  if jsonb_typeof(p->'latitude') <> 'number' or jsonb_typeof(p->'longitude') <> 'number' or (p->>'latitude')::numeric not between 6 and 38 or (p->>'longitude')::numeric not between 68 and 98 then return false; end if;
  if jsonb_typeof(p->'services') <> 'array' then return false; end if;
  if jsonb_array_length(p->'services') not between 1 and 3 or exists(select 1 from jsonb_array_elements(p->'services') t(item) where jsonb_typeof(item) <> 'string' or not (item #>> '{}' = any(array['opd','ipd','home','none','unknown']))) or (select count(distinct item) from jsonb_array_elements(p->'services') t(item)) <> jsonb_array_length(p->'services') or ((p->'services' ?| array['none','unknown']) and jsonb_array_length(p->'services') <> 1) then return false; end if;
  return p->>'morphine' = any(array['yes','no','unknown']) and
    ((p->>'morphine' = 'yes' and p->>'supply' = any(array['','regular','intermittent','unknown'])) or (p->>'morphine' <> 'yes' and p->>'supply' = '')) and length(p->>'familyNotes') <= 1000;
exception when others then return false;
end;
$$;
revoke all on function public.sahara_feedback_valid(jsonb) from public, anon, authenticated;
grant execute on function public.sahara_feedback_valid(jsonb) to service_role;

create table public.sahara_feedback_receipts (
  id uuid primary key,
  kind text not null check (kind in ('app','hospital')),
  content_hash text not null check (content_hash ~ '^[a-f0-9]{64}$'),
  client_hash text not null check (client_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz not null default now()
);
create index sahara_feedback_receipt_time on public.sahara_feedback_receipts(created_at);
create index sahara_feedback_client_time on public.sahara_feedback_receipts(client_hash,created_at);
create table public.sahara_app_feedback (
  id uuid primary key references public.sahara_feedback_receipts(id),
  form_version text not null default 'feedback-v1' check (form_version = 'feedback-v1'),
  consent_version text not null default 'feedback-consent-v1' check (consent_version = 'feedback-consent-v1'),
  rating smallint not null check (rating between 1 and 5),
  respondent_role text not null check (respondent_role in ('patient','caregiver','clinician','other')),
  category text not null check (category in ('navigation','design','bug','idea','other')),
  message text not null check (length(message) <= 1000),
  consent boolean not null check (consent),
  created_at timestamptz not null default now()
);
create table public.sahara_hospital_submissions (
  id uuid primary key references public.sahara_feedback_receipts(id),
  form_version text not null default 'hospital-survey-v1' check (form_version = 'hospital-survey-v1'),
  consent_version text not null default 'hospital-survey-consent-v1' check (consent_version = 'hospital-survey-consent-v1'),
  facility jsonb not null check (jsonb_typeof(facility) = 'object' and not (facility ?| array['fullName','council','councilOther','registrationState','registrationNumber','role','roleOther'])),
  status text not null default 'pending_review' check (status = 'pending_review'),
  created_at timestamptz not null default now()
);
create table public.sahara_hospital_contributors (
  submission_id uuid primary key references public.sahara_hospital_submissions(id),
  full_name text not null check (length(full_name) between 2 and 120),
  respondent_role text not null check (respondent_role in ('oncologist','palliative_physician','physician','resident','nurse','pharmacist','other')),
  role_other text not null check ((respondent_role = 'other' and length(role_other) between 2 and 120) or (respondent_role <> 'other' and role_other = '')),
  council text not null check (council in ('nmc','state_medical','state_nursing','state_pharmacy','other')),
  council_other text not null check ((council = 'other' and length(council_other) between 2 and 120) or (council <> 'other' and council_other = '')),
  registration_state text not null,
  registration_number text not null check (registration_number ~ '^[A-Za-z0-9/-]{3,20}$')
);
alter table public.sahara_feedback_receipts enable row level security;
alter table public.sahara_app_feedback enable row level security;
alter table public.sahara_hospital_submissions enable row level security;
alter table public.sahara_hospital_contributors enable row level security;
revoke all on public.sahara_feedback_receipts,public.sahara_app_feedback,public.sahara_hospital_submissions,public.sahara_hospital_contributors from public,anon,authenticated,service_role;
grant select,insert on public.sahara_feedback_receipts,public.sahara_app_feedback,public.sahara_hospital_submissions,public.sahara_hospital_contributors to service_role;

create function public.sahara_submit_feedback(p_payload jsonb,p_client_hash text)
returns jsonb language plpgsql security invoker set search_path = '' as $$
declare v_id uuid; v_hash text; v_previous public.sahara_feedback_receipts%rowtype; v_now timestamptz := clock_timestamp(); v_status text;
begin
  if public.sahara_feedback_valid(p_payload) is distinct from true or p_client_hash is null or p_client_hash !~ '^[a-f0-9]{64}$' then raise exception 'FEEDBACK_INVALID' using errcode = '22023'; end if;
  v_id := (p_payload->>'submissionId')::uuid;
  v_hash := encode(sha256(convert_to(p_payload::text,'UTF8')),'hex');
  -- Both modes share one atomic quota and idempotency namespace, across replicas.
  perform pg_advisory_xact_lock(1837410292);
  select * into v_previous from public.sahara_feedback_receipts where id = v_id;
  if found then
    if v_previous.content_hash <> v_hash then raise exception 'FEEDBACK_ID_CONFLICT' using errcode = 'P0001'; end if;
    return jsonb_build_object('id',v_id,'status',case v_previous.kind when 'app' then 'received' else 'pending_review' end);
  end if;
  if (select count(*) from public.sahara_feedback_receipts where created_at >= v_now - interval '24 hours') >= 100
     or (select count(*) from public.sahara_feedback_receipts where created_at >= v_now - interval '24 hours' and client_hash = p_client_hash) >= 5
     or exists(select 1 from public.sahara_feedback_receipts where created_at >= v_now - interval '60 seconds' and client_hash = p_client_hash)
  then raise exception 'FEEDBACK_RATE_LIMIT' using errcode = 'P0001'; end if;
  insert into public.sahara_feedback_receipts(id,kind,content_hash,client_hash,created_at) values(v_id,p_payload->>'kind',v_hash,p_client_hash,v_now);
  if p_payload->>'kind' = 'app' then
    insert into public.sahara_app_feedback(id,rating,respondent_role,category,message,consent,created_at) values(v_id,(p_payload->>'rating')::smallint,p_payload->>'role',p_payload->>'category',p_payload->>'message',true,v_now);
    v_status := 'received';
  else
    insert into public.sahara_hospital_submissions(id,facility,created_at) values(v_id,p_payload - array['fullName','council','councilOther','registrationState','registrationNumber','role','roleOther'],v_now);
    insert into public.sahara_hospital_contributors(submission_id,full_name,respondent_role,role_other,council,council_other,registration_state,registration_number) values(v_id,p_payload->>'fullName',p_payload->>'role',p_payload->>'roleOther',p_payload->>'council',p_payload->>'councilOther',p_payload->>'registrationState',p_payload->>'registrationNumber');
    v_status := 'pending_review';
  end if;
  return jsonb_build_object('id',v_id,'status',v_status);
end;
$$;
revoke all on function public.sahara_submit_feedback(jsonb,text) from public,anon,authenticated;
grant execute on function public.sahara_submit_feedback(jsonb,text) to service_role;
commit;
