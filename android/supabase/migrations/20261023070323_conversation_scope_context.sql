begin;
-- Exact context strings are hashed as UTF-8, never re-serialized for the v2 preimage.
create table healthathon_private.conversation_scopes (
 conversation_id uuid primary key, person_id uuid not null references public.healthathon_people(id) on delete cascade,
 revision bigint not null default 1 check(revision>0), approved_revision bigint,
 updated_at timestamptz not null default now(), unique(person_id,conversation_id)
);
create table healthathon_private.conversation_audience (
 conversation_id uuid not null references healthathon_private.conversation_scopes(conversation_id) on delete cascade,
 user_id uuid not null references auth.users(id) on delete cascade, primary key(conversation_id,user_id)
);
create index conversation_scopes_person_idx on healthathon_private.conversation_scopes(person_id);
create index conversation_audience_user_idx on healthathon_private.conversation_audience(user_id,conversation_id);
alter table healthathon_private.conversation_scopes enable row level security;
alter table healthathon_private.conversation_audience enable row level security;
revoke all on healthathon_private.conversation_scopes,healthathon_private.conversation_audience from public,anon,authenticated;

create function healthathon_private.valid_conversation_context(p_source text,p_exact text) returns boolean
language plpgsql immutable set search_path='' as $$
declare c jsonb;g jsonb;e jsonb;keys text[]:=array['patient_priorities','understanding_questions','participants','concerns_uncertainty','next_steps'];seen text[]:='{}';raw bytea:=convert_to(p_source,'UTF8');a integer;b integer;
begin
 if p_source is null or p_exact is null or octet_length(p_exact)>65536 then return false; end if;
 c:=p_exact::jsonb;
 if jsonb_typeof(c)<>'object' or c-array['schemaVersion','sourceHash','goals','recorderUid','recorderLabel','audioRef','audioHash','englishDraft','transcription']<>'{}'::jsonb
 or not(c ?& array['schemaVersion','sourceHash','goals','recorderUid','recorderLabel','audioRef','audioHash','englishDraft','transcription'])
 or c->'schemaVersion'<>'1'::jsonb or c->>'sourceHash' is distinct from encode(extensions.digest(p_source,'sha256'),'hex')
 or jsonb_typeof(c->'goals')<>'array' or jsonb_array_length(c->'goals')<>5
 or jsonb_typeof(c->'recorderLabel')<>'string' or length(c->>'recorderLabel')>120 then return false; end if;
 if c->'recorderUid'<>'null'::jsonb then perform (c->>'recorderUid')::uuid; end if;
 if c->'audioRef'<>'null'::jsonb then perform (c->>'audioRef')::uuid; end if;
 if c->'audioHash'<>'null'::jsonb and coalesce(c->>'audioHash','')!~'^[a-f0-9]{64}$' then return false; end if;
 if (c->'audioRef'='null'::jsonb) is distinct from (c->'audioHash'='null'::jsonb) then return false; end if;
 for g in select value from jsonb_array_elements(c->'goals') loop
  if jsonb_typeof(g)<>'object' or g-array['key','status','origin','answer','evidence']<>'{}'::jsonb or not(g ?& array['key','status','origin','answer','evidence'])
  or not(coalesce(g->>'key','')=any(keys)) or g->>'key'=any(seen)
  or coalesce(g->>'status','') not in ('not_recorded','needs_review','discussed','deferred','undecided')
  or coalesce(g->>'origin','') not in ('manual','source','assisted','legacy') or jsonb_typeof(g->'answer')<>'string' or length(g->>'answer')>4000
  or jsonb_typeof(g->'evidence')<>'array' or jsonb_array_length(g->'evidence')>50 then return false; end if;
  seen:=array_append(seen,g->>'key');
  if g->>'status'='not_recorded' and (btrim(g->>'answer')<>'' or jsonb_array_length(g->'evidence')<>0) then return false; end if;
  if g->>'status'='discussed' and btrim(g->>'answer')='' then return false; end if;
  for e in select value from jsonb_array_elements(g->'evidence') loop
   if jsonb_typeof(e)<>'object' or e-array['start','end','quote']<>'{}'::jsonb or not(e ?& array['start','end','quote'])
   or jsonb_typeof(e->'start')<>'number' or jsonb_typeof(e->'end')<>'number' or jsonb_typeof(e->'quote')<>'string'
   or (e->>'start')!~'^[0-9]{1,6}$' or (e->>'end')!~'^[0-9]{1,6}$' then return false; end if;
   a:=(e->>'start')::integer;b:=(e->>'end')::integer;
   if a<0 or b<=a or b>octet_length(raw) or convert_to(e->>'quote','UTF8')<>substring(raw from a+1 for b-a) then return false; end if;
  end loop;
 end loop;
 if c->'englishDraft'<>'null'::jsonb then
  e:=c->'englishDraft';
  if jsonb_typeof(e)<>'object' or e-array['text','sourceHash','origin','reviewed']<>'{}'::jsonb or not(e ?& array['text','sourceHash','origin','reviewed'])
  or jsonb_typeof(e->'text')<>'string' or length(e->>'text')>12000 or e->>'sourceHash' is distinct from c->>'sourceHash'
  or coalesce(e->>'origin','') not in ('manual','source','assisted','legacy') or jsonb_typeof(e->'reviewed')<>'boolean' then return false; end if;
 end if;
 if c->'transcription'<>'null'::jsonb then
  e:=c->'transcription';
  if jsonb_typeof(e)<>'object' or e-array['provider','model','at','sourceHash']<>'{}'::jsonb or not(e ?& array['provider','model','at','sourceHash'])
  or jsonb_typeof(e->'provider')<>'string' or length(e->>'provider')>120 or jsonb_typeof(e->'model')<>'string' or length(e->>'model')>200
  or jsonb_typeof(e->'at')<>'number' or (e->>'at')!~'^[1-9][0-9]{0,14}$'
  or (e->'sourceHash'<>'null'::jsonb and coalesce(e->>'sourceHash','')!~'^[a-f0-9]{64}$') then return false; end if;
 end if;
 return true;
exception when others then return false;
end $$;

create function healthathon_private.conversation_hash(p_person uuid,p_note jsonb) returns text
language plpgsql immutable set search_path='' as $$
declare value text; framed text:=E'sahara-care-note-v2\n';fields text[];
begin
 fields:=array[p_note->>'conversationId',p_person::text,p_note->>'personName',p_note->>'version',p_note->>'source',p_note->>'priorities',p_note->>'participants',p_note->>'topics',p_note->>'questions',p_note->>'nextSteps',p_note->>'doctor',p_note->>'published',p_note->>'draftOrigin',p_note->>'reviewStatus',p_note->>'reviewerVerification',p_note->>'transcriptionModel',p_note->>'transcribedAt',p_note->>'contextJson'];
 foreach value in array fields loop
  if value is null then return null; end if;
  framed:=framed||octet_length(convert_to(value,'UTF8'))::text||':'||value;
 end loop;
 return encode(extensions.digest(framed,'sha256'),'hex');
end $$;

create function healthathon_private.conversation_allowed(p_person uuid,p_conversation uuid) returns boolean
language plpgsql stable security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 if auth.uid() is null or not coalesce(healthathon_private.can_read(p_person),false) or not coalesce(healthathon_private.has_permission(p_person,'careNote'),false) then return false; end if;
 if not exists(select 1 from healthathon_private.conversation_scopes where conversation_id=p_conversation and person_id=p_person) then return false; end if;
 if healthathon_private.is_owner(p_person) then return true; end if;
 return exists(select 1 from public.healthathon_members m where m.person_id=p_person and m.user_id=auth.uid() and (m.expires_at is null or m.expires_at>now()) and
 ((m.role='clinician' and exists(select 1 from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved')) or
 (m.role='family' and exists(select 1 from healthathon_private.conversation_audience a join healthathon_private.conversation_scopes s using(conversation_id) where a.conversation_id=p_conversation and a.user_id=auth.uid() and s.approved_revision=s.revision))));
end $$;

create function healthathon_private.conversation_scope(p_person uuid,p_conversation uuid,p_revision bigint,p_members uuid[],p_approve boolean) returns jsonb
language plpgsql security definer set search_path='' as $$
declare scope healthathon_private.conversation_scopes;member uuid;
begin
 perform healthathon_private.require_open_account();
 if auth.uid() is null or not coalesce(healthathon_private.is_owner(p_person),false) then raise exception 'Only the patient record owner can choose this conversation audience.' using errcode='42501'; end if;
 if p_revision is null or p_members is null or p_approve is null or cardinality(p_members)>50 then raise exception 'Choose a valid conversation audience.'; end if;
 select * into scope from healthathon_private.conversation_scopes where person_id=p_person and conversation_id=p_conversation for update;
 if not found or scope.revision<>p_revision then raise exception 'Conversation sharing changed. Refresh before saving.' using errcode='40001'; end if;
 foreach member in array p_members loop
  if member is null or not exists(select 1 from public.healthathon_members where person_id=p_person and user_id=member and role='family' and (expires_at is null or expires_at>now()) and permissions->>'careNote'='true') then raise exception 'Choose only currently invited family accounts with care-note access.' using errcode='42501'; end if;
 end loop;
 delete from healthathon_private.conversation_audience where conversation_id=p_conversation;
 insert into healthathon_private.conversation_audience(conversation_id,user_id) select p_conversation,unnest(p_members) on conflict do nothing;
 update healthathon_private.conversation_scopes set revision=revision+1,approved_revision=case when p_approve then revision+1 else null end,updated_at=now() where conversation_id=p_conversation returning * into scope;
 return jsonb_build_object('conversationId',p_conversation,'revision',scope.revision,'approved',scope.approved_revision=scope.revision,'memberIds',to_jsonb(p_members));
end $$;

create function healthathon_private.conversation_scope_at(p_person uuid,p_conversation uuid) returns jsonb
language plpgsql stable security definer set search_path='' as $$
declare scope healthathon_private.conversation_scopes;
begin
 perform healthathon_private.require_open_account();
 if auth.uid() is null or not coalesce(healthathon_private.is_owner(p_person),false) then raise exception 'Only the owner can manage conversation sharing.' using errcode='42501'; end if;
 select * into scope from healthathon_private.conversation_scopes where person_id=p_person and conversation_id=p_conversation;
 if not found then return null; end if;
 return jsonb_build_object('conversationId',p_conversation,'revision',scope.revision,'approved',coalesce(scope.approved_revision=scope.revision,false),'memberIds',coalesce((select jsonb_agg(user_id order by user_id) from healthathon_private.conversation_audience where conversation_id=p_conversation),'[]'::jsonb));
end $$;

alter function healthathon_private.valid_draft(jsonb) rename to valid_draft_before_context;
create function healthathon_private.valid_draft(p_data jsonb) returns boolean
language plpgsql immutable set search_path='' as $$
begin
 if not(p_data ? 'contextVersion') then return healthathon_private.valid_draft_before_context(p_data); end if;
 if p_data->'contextVersion'<>'1'::jsonb or jsonb_typeof(p_data->'contextJson')<>'string' or jsonb_typeof(p_data->'conversationId')<>'string' then return false; end if;
 perform (p_data->>'conversationId')::uuid;
 return healthathon_private.valid_draft_before_context(p_data-array['conversationId','contextVersion','contextJson']) and healthathon_private.valid_conversation_context(p_data->>'source',p_data->>'contextJson');
exception when others then return false;
end $$;
alter function healthathon_private.save_draft(uuid,uuid,jsonb,bigint,boolean) rename to save_draft_before_context;
create function healthathon_private.save_draft(p_id uuid,p_person uuid,p_data jsonb,p_revision bigint,p_submit boolean) returns public.healthathon_care_drafts
language plpgsql security definer set search_path='' as $$
declare saved public.healthathon_care_drafts;previous public.healthathon_care_drafts;conversation uuid;
begin
 perform healthathon_private.require_open_account();
 if auth.uid() is null or not coalesce(healthathon_private.can_edit(p_person),false) or not coalesce(healthathon_private.has_permission(p_person,'careNote'),false) then raise exception 'You cannot change this conversation.' using errcode='42501'; end if;
 if p_data ? 'contextVersion' then
  if not coalesce(healthathon_private.valid_draft(p_data),false) then raise exception 'Review the original words, context and evidence.'; end if;
  conversation:=(p_data->>'conversationId')::uuid;
  select * into previous from public.healthathon_care_drafts where id=p_id and person_id=p_person and author_id=auth.uid() for update;
  if found and previous.data ? 'conversationId' and previous.data->>'conversationId'<>conversation::text then raise exception 'A saved draft cannot switch conversations.'; end if;
  if exists(select 1 from healthathon_private.conversation_scopes where conversation_id=conversation and person_id<>p_person) then raise exception 'This conversation belongs to a different care profile.' using errcode='42501'; end if;
 end if;
 saved:=healthathon_private.save_draft_before_context(p_id,p_person,p_data,p_revision,p_submit);
 if conversation is not null then
  insert into healthathon_private.conversation_scopes(conversation_id,person_id) values(conversation,p_person) on conflict(conversation_id) do nothing;
  if not exists(select 1 from healthathon_private.conversation_scopes where conversation_id=conversation and person_id=p_person) then raise exception 'This conversation belongs to a different profile.' using errcode='42501'; end if;
 end if;
 return saved;
end $$;

-- The existing legacy note/ack policy remains necessary; the new scope is an additional AND.
alter function healthathon_private.can_read_record(uuid,text,jsonb) rename to can_read_record_before_context;
create function healthathon_private.can_read_record(p_person uuid,p_kind text,p_data jsonb) returns boolean
language plpgsql security definer set search_path='' as $$
declare note jsonb;target uuid;
begin
 perform healthathon_private.require_open_account();
 if not coalesce(healthathon_private.can_read_record_before_context(p_person,p_kind,p_data),false) then return false; end if;
 if p_kind='ack' then
  begin target:=(p_data->>'versionId')::uuid; exception when others then return false; end;
  select data into note from public.healthathon_records where kind='note' and person_id=p_person and id=target;
  if not found then return false; end if;
 elsif p_kind='note' then note:=p_data; else return true; end if;
 if not(note ? 'contextVersion' or note ? 'conversationId' or note ? 'contextJson' or coalesce(note->>'hashScheme'='sha256-framed-v2',false)) then return true; end if;
 if note->'contextVersion' is distinct from '1'::jsonb or note->>'hashScheme' is distinct from 'sha256-framed-v2' or not coalesce(healthathon_private.valid_conversation_context(note->>'source',note->>'contextJson'),false)
 or note->>'contentHash' is distinct from healthathon_private.conversation_hash(p_person,note) then return false; end if;
 return healthathon_private.conversation_allowed(p_person,(note->>'conversationId')::uuid);
exception when invalid_text_representation then return false;
end $$;
create policy records_conversation_scope on public.healthathon_records as restrictive for select to authenticated using(healthathon_private.can_read_record(person_id,kind,data));
alter policy records_read on public.healthathon_records using(healthathon_private.can_read_record(person_id,kind,data));
alter policy records_remove on public.healthathon_records using(kind='entry' and healthathon_private.can_edit(person_id) and healthathon_private.can_read_record(person_id,kind,data));

-- Keep legacy acknowledgements unchanged; v2 confirmations are latest-within-conversation.
alter function healthathon_private.save_record(uuid,uuid,text,jsonb,bigint) rename to save_record_before_context;
create function healthathon_private.save_record(p_id uuid,p_person uuid,p_kind text,p_data jsonb,p_revision bigint) returns public.healthathon_records
language plpgsql security definer set search_path='' as $$
declare n public.healthathon_records;saved public.healthathon_records;target uuid;actor_data jsonb;
begin
 perform healthathon_private.require_open_account();
 if p_kind<>'ack' then return healthathon_private.save_record_before_context(p_id,p_person,p_kind,p_data,p_revision); end if;
 begin target:=(p_data->>'versionId')::uuid; exception when others then raise exception 'Open the exact care-note version.'; end;
 select * into n from public.healthathon_records where id=target and person_id=p_person and kind='note';
 if not found then raise exception 'Open an accessible care-note version.' using errcode='42501'; end if;
 if not(n.data ? 'contextVersion' or n.data ? 'conversationId' or n.data ? 'contextJson' or coalesce(n.data->>'hashScheme'='sha256-framed-v2',false)) then return healthathon_private.save_record_before_context(p_id,p_person,p_kind,p_data,p_revision); end if;
 if auth.uid() is null or not coalesce(healthathon_private.can_read_record(p_person,'note',n.data),false) then raise exception 'You cannot confirm this conversation.' using errcode='42501'; end if;
 if p_revision is distinct from 0 or jsonb_typeof(p_data)<>'object' or length(btrim(coalesce(p_data->>'name',''))) not between 1 and 100 or p_data->>'contentHash' is distinct from n.data->>'contentHash' then raise exception 'Read the exact note and add your name.'; end if;
 perform 1 from public.healthathon_people where id=p_person for update;
 if exists(select 1 from public.healthathon_records where person_id=p_person and kind='note' and data->>'conversationId'=n.data->>'conversationId' and (data->>'version')::integer>(n.data->>'version')::integer) then raise exception 'Open the newest version of this conversation.' using errcode='40001'; end if;
 if p_data->>'relationship'='Patient' then
  if not healthathon_private.is_owner(p_person) then raise exception 'The record owner must confirm as patient.' using errcode='42501'; end if;
 elsif p_data->>'relationship'='Family / carer' then
  if healthathon_private.is_owner(p_person) or not exists(select 1 from public.healthathon_members where person_id=p_person and user_id=auth.uid() and role='family' and (expires_at is null or expires_at>now())) then raise exception 'Use an invited family account.' using errcode='42501'; end if;
  if not exists(select 1 from public.healthathon_records a join public.healthathon_people p on p.id=a.person_id where a.kind='ack' and a.person_id=p_person and a.data->>'versionId'=target::text and a.data->>'contentHash'=n.data->>'contentHash' and a.data->>'relationship'='Patient' and a.data->>'actorId'=p.owner_id::text) then raise exception 'The owner must confirm this exact version first.'; end if;
 else raise exception 'Choose Patient or Family / carer.'; end if;
 select * into saved from public.healthathon_records where kind='ack' and person_id=p_person and data->>'versionId'=target::text and data->>'actorId'=auth.uid()::text and data->>'contentHash'=n.data->>'contentHash';
 if found then return saved; end if;
 actor_data:=jsonb_build_object('versionId',target,'contentHash',n.data->>'contentHash','name',btrim(p_data->>'name'),'relationship',p_data->>'relationship','at',floor(extract(epoch from clock_timestamp())*1000)::bigint,'actorId',auth.uid());
 insert into public.healthathon_records(id,person_id,kind,data) values(p_id,p_person,'ack',actor_data) returning * into saved;
 return saved;
end $$;

create function healthathon_private.require_new_note_context() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.kind='note' then
  perform healthathon_private.require_open_account();
  if new.data->>'hashScheme' is distinct from 'sha256-framed-v2' or not coalesce(healthathon_private.can_read_record(new.person_id,'note',new.data),false) or not exists(select 1 from healthathon_private.conversation_scopes where conversation_id=(new.data->>'conversationId')::uuid and person_id=new.person_id and approved_revision=revision) then raise exception 'Requires updated conversation format and owner-approved audience before publishing.' using errcode='42501'; end if;
 end if;
 return new;
end $$;
create trigger require_new_note_context before insert on public.healthathon_records for each row execute function healthathon_private.require_new_note_context();

alter function healthathon_private.review_draft(uuid,bigint,boolean) rename to review_draft_before_context;
create function healthathon_private.review_draft(p_id uuid,p_revision bigint,p_confirm boolean) returns public.healthathon_records
language plpgsql security definer set search_path='' as $$
declare draft public.healthathon_care_drafts;profile public.healthathon_clinician_requests;note jsonb;saved public.healthathon_records;note_id uuid:=gen_random_uuid();version integer;context jsonb;
begin
 perform healthathon_private.require_open_account();
 select * into draft from public.healthathon_care_drafts where id=p_id for update;
 if not found or not coalesce(healthathon_private.is_care_clinician(draft.person_id),false) then raise exception 'A verified clinician invited by this patient must review this conversation.' using errcode='42501'; end if;
 if not coalesce(p_confirm,false) then raise exception 'Read the source and every goal before publishing.'; end if;
 if draft.status='reviewed' then select * into saved from public.healthathon_records where id=draft.reviewed_note_id and kind='note'; if not coalesce(healthathon_private.can_read_record(saved.person_id,'note',saved.data),false) then raise exception 'Conversation access changed.' using errcode='42501'; end if; return saved; end if;
 if draft.revision is distinct from p_revision or draft.status<>'submitted' then raise exception 'This conversation changed. Refresh before reviewing.' using errcode='40001'; end if;
 if draft.data->'contextVersion' is distinct from '1'::jsonb or not coalesce(healthathon_private.valid_draft(draft.data),false) then raise exception 'This draft needs a conversation-context upgrade before publishing.'; end if;
 perform 1 from healthathon_private.conversation_scopes where person_id=draft.person_id and conversation_id=(draft.data->>'conversationId')::uuid and approved_revision=revision for share;
 if not found then raise exception 'The owner must approve this conversation audience first.' using errcode='42501'; end if;
 select * into profile from public.healthathon_clinician_requests where user_id=auth.uid() and status='approved';
 context:=(draft.data->>'contextJson')::jsonb;
 perform 1 from public.healthathon_people where id=draft.person_id for update;
 select coalesce(max((data->>'version')::integer),0)+1 into version from public.healthathon_records where person_id=draft.person_id and kind='note';
 note:=jsonb_build_object('version',version,'personName',(select name from public.healthathon_people where id=draft.person_id),'source',draft.data->>'source','priorities',coalesce(draft.data->>'priorities',''),'participants',coalesce(draft.data->>'participants',''),'topics',coalesce(draft.data->>'topics',''),'questions',coalesce(draft.data->>'questions',''),'nextSteps',coalesce(draft.data->>'nextSteps',''),'doctor',profile.name,'published',floor(extract(epoch from clock_timestamp())*1000)::bigint,'draftOrigin','shared_source','reviewStatus','clinician_reviewed','reviewerVerification','verified','reviewerId',auth.uid(),'draftId',draft.id,'draftRevision',draft.revision,'conversationId',draft.data->>'conversationId','contextVersion',1,'contextJson',draft.data->>'contextJson','hashScheme','sha256-framed-v2','transcriptionModel','','transcribedAt',0);
 note:=note||jsonb_build_object('transcriptionModel',coalesce(context#>>'{transcription,model}',''),'transcribedAt',coalesce(context#>>'{transcription,at}','0')::bigint);
 note:=note||jsonb_build_object('contentHash',healthathon_private.conversation_hash(draft.person_id,note));
 saved:=healthathon_private.save_record_legacy(note_id,draft.person_id,'note',note,0);
 update public.healthathon_care_drafts set status='reviewed',reviewed_note_id=saved.id,updated_at=now() where id=draft.id;
 return saved;
end $$;

alter function healthathon_private.review_receipts() rename to review_receipts_before_context;
create function healthathon_private.review_receipts() returns table(note_id uuid,reviewer_id uuid)
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 return query select r.note_id,r.reviewer_id from healthathon_private.review_receipts_before_context() r join public.healthathon_records n on n.id=r.note_id and n.kind='note' where n.data->>'hashScheme'='sha256-json-v1' and healthathon_private.can_read_record(n.person_id,n.kind,n.data);
 return query select n.id,(n.data->>'reviewerId')::uuid from public.healthathon_records n join public.healthathon_care_drafts d on d.reviewed_note_id=n.id and d.person_id=n.person_id and d.status='reviewed'
 where n.kind='note' and n.data->>'reviewerVerification'='verified' and n.data->>'reviewStatus'='clinician_reviewed' and n.data->>'hashScheme'='sha256-framed-v2' and n.data->>'draftId'=d.id::text
 and n.data->>'draftRevision'=d.revision::text and n.data->>'conversationId'=d.data->>'conversationId' and n.data->>'contextJson'=d.data->>'contextJson'
 and not exists(select 1 from unnest(array['source','priorities','participants','topics','questions','nextSteps']) k where coalesce(n.data->>k,'') is distinct from coalesce(d.data->>k,''))
 and n.data->>'contentHash'=healthathon_private.conversation_hash(n.person_id,n.data) and healthathon_private.can_read_record(n.person_id,n.kind,n.data);
end $$;

create function public.healthathon_conversation_scope(p_person uuid,p_conversation uuid,p_revision bigint,p_members uuid[],p_approve boolean) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.conversation_scope(p_person,p_conversation,p_revision,p_members,p_approve)$$;
create function healthathon_private.conversation_candidates(p_person uuid) returns jsonb language plpgsql stable security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 if not coalesce(healthathon_private.is_owner(p_person),false) then raise exception 'Only the owner can choose family accounts.' using errcode='42501'; end if;
 return coalesce((select jsonb_agg(jsonb_build_object('userId',m.user_id,'label',u.email) order by m.user_id) from public.healthathon_members m join auth.users u on u.id=m.user_id where m.person_id=p_person and m.role='family' and (m.expires_at is null or m.expires_at>now()) and m.permissions->>'careNote'='true'),'[]'::jsonb);
end $$;
create function public.healthathon_conversation_candidates(p_person uuid) returns jsonb language sql stable security invoker set search_path='' as $$select healthathon_private.conversation_candidates(p_person)$$;
revoke all on function healthathon_private.conversation_candidates(uuid),public.healthathon_conversation_candidates(uuid) from public,anon,service_role;
grant execute on function healthathon_private.conversation_candidates(uuid),public.healthathon_conversation_candidates(uuid) to authenticated;
create function public.healthathon_conversation_scope_at(p_person uuid,p_conversation uuid) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.conversation_scope_at(p_person,p_conversation)$$;
create function public.healthathon_conversation_capabilities() returns jsonb language sql stable security invoker set search_path='' as $$select jsonb_build_object('contextVersion',1,'audience','per-conversation','audioLocalOnly',true,'hashScheme','sha256-framed-v2')$$;
create or replace function public.healthathon_save_draft(p_id uuid,p_person uuid,p_data jsonb,p_revision bigint,p_submit boolean) returns public.healthathon_care_drafts language sql security invoker set search_path='' as $$select healthathon_private.save_draft(p_id,p_person,p_data,p_revision,p_submit)$$;
create or replace function public.healthathon_review_draft(p_id uuid,p_revision bigint,p_confirm boolean) returns public.healthathon_records language sql security invoker set search_path='' as $$select healthathon_private.review_draft(p_id,p_revision,p_confirm)$$;
create or replace function public.healthathon_save_record(p_id uuid,p_person uuid,p_kind text,p_data jsonb,p_revision bigint) returns public.healthathon_records language sql security invoker set search_path='' as $$select healthathon_private.save_record(p_id,p_person,p_kind,p_data,p_revision)$$;
create or replace function public.healthathon_review_receipts() returns table(note_id uuid,reviewer_id uuid) language sql security invoker set search_path='' as $$select * from healthathon_private.review_receipts()$$;
revoke all on function healthathon_private.review_receipts_before_context() from public,anon,authenticated,service_role;
revoke all on function healthathon_private.review_receipts(),healthathon_private.save_record(uuid,uuid,text,jsonb,bigint) from public,anon,service_role;
grant execute on function healthathon_private.review_receipts() to authenticated;
revoke all on function healthathon_private.save_record(uuid,uuid,text,jsonb,bigint) from public,anon;
grant execute on function healthathon_private.save_record(uuid,uuid,text,jsonb,bigint) to authenticated;
revoke all on function healthathon_private.valid_draft_before_context(jsonb),healthathon_private.save_draft_before_context(uuid,uuid,jsonb,bigint,boolean),healthathon_private.can_read_record_before_context(uuid,text,jsonb),healthathon_private.review_draft_before_context(uuid,bigint,boolean),healthathon_private.save_record_before_context(uuid,uuid,text,jsonb,bigint),healthathon_private.valid_conversation_context(text,text),healthathon_private.conversation_hash(uuid,jsonb),healthathon_private.require_new_note_context() from public,anon,authenticated;
revoke all on function healthathon_private.conversation_allowed(uuid,uuid),healthathon_private.conversation_scope(uuid,uuid,bigint,uuid[],boolean),healthathon_private.conversation_scope_at(uuid,uuid),healthathon_private.valid_draft(jsonb),healthathon_private.save_draft(uuid,uuid,jsonb,bigint,boolean),healthathon_private.can_read_record(uuid,text,jsonb),healthathon_private.review_draft(uuid,bigint,boolean),public.healthathon_conversation_scope(uuid,uuid,bigint,uuid[],boolean),public.healthathon_conversation_scope_at(uuid,uuid),public.healthathon_conversation_capabilities() from public,anon;
grant execute on function healthathon_private.conversation_allowed(uuid,uuid),healthathon_private.conversation_scope(uuid,uuid,bigint,uuid[],boolean),healthathon_private.conversation_scope_at(uuid,uuid),healthathon_private.valid_draft(jsonb),healthathon_private.save_draft(uuid,uuid,jsonb,bigint,boolean),healthathon_private.can_read_record(uuid,text,jsonb),healthathon_private.review_draft(uuid,bigint,boolean),public.healthathon_conversation_scope(uuid,uuid,bigint,uuid[],boolean),public.healthathon_conversation_scope_at(uuid,uuid),public.healthathon_conversation_capabilities() to authenticated;
-- Default project ACLs may explicitly grant service_role; these are user-session APIs only.
do $$declare f regprocedure;begin
 for f in select p.oid::regprocedure from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='healthathon_private' and p.proname in ('conversation_allowed','conversation_scope','conversation_scope_at','valid_conversation_context','conversation_hash','require_new_note_context','valid_draft_before_context','save_draft_before_context','can_read_record_before_context','review_draft_before_context','save_record_before_context','review_receipts_before_context','valid_draft','save_draft','can_read_record','review_draft','save_record','review_receipts') loop execute format('revoke all on function %s from service_role',f);end loop;
end $$;
commit;
