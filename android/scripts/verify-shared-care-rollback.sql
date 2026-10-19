-- Run with the Supabase SQL tool. Entire synthetic fixture rolls back.
begin;
do $$begin
 if exists(select 1 from auth.users where id in ('11111111-1111-4111-8111-111111111111','22222222-2222-4222-8222-222222222222','33333333-3333-4333-8333-333333333333','44444444-4444-4444-8444-444444444444','55555555-5555-4555-8555-555555555555')) then raise exception 'Fixture collision; no test started.'; end if;
end$$;
insert into auth.users(id,email) values
 ('11111111-1111-4111-8111-111111111111','patient@example.invalid'),('22222222-2222-4222-8222-222222222222','family@example.invalid'),
 ('33333333-3333-4333-8333-333333333333','clinician@example.invalid'),('44444444-4444-4444-8444-444444444444','outsider@example.invalid'),('55555555-5555-4555-8555-555555555555','verifier@example.invalid');
-- Test-only trusted assignment, never committed and never applied to a real user.
insert into healthathon_private.staff_roles(user_id,role) values('55555555-5555-4555-8555-555555555555','clinical_verifier'),('55555555-5555-4555-8555-555555555555','directory_reviewer');
set local role authenticated;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
insert into public.healthathon_people(id,name,created_ms) values('66666666-6666-4666-8666-666666666666','Synthetic rollback person',1);
select set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
select public.healthathon_request_clinician('Synthetic clinician','Synthetic council','TEST-ONLY');
do $$begin
 begin perform public.healthathon_review_clinician(auth.uid(),1,'approved','Synthetic source only','Synthetic test only');raise exception 'Self approval was allowed';exception when insufficient_privilege then null;end;
end$$;
select set_config('request.jwt.claim.sub','55555555-5555-4555-8555-555555555555',true);
select public.healthathon_review_clinician('33333333-3333-4333-8333-333333333333',1,'approved','https://example.invalid/registry','Synthetic verification only');
do $$begin
 begin perform public.healthathon_review_clinician('33333333-3333-4333-8333-333333333333',1,'rejected','Synthetic source only','Synthetic test only');raise exception 'Stale verification was allowed';exception when serialization_failure then null;end;
end$$;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select set_config('sahara.test_invite',public.healthathon_invite_access('66666666-6666-4666-8666-666666666666','clinician',true,'{"careNote":true,"calendar":true,"reports":false,"costs":false}',30),true);
select set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
select public.healthathon_join(current_setting('sahara.test_invite'));
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select public.healthathon_save_draft('77777777-7777-4777-8777-777777777777','66666666-6666-4666-8666-666666666666','{"source":"Patient: Synthetic source only.","priorities":"Synthetic source only.","participants":"Patient, clinician","topics":"Discussion","questions":"","nextSteps":""}',0,true);
do $$begin
 begin perform public.healthathon_review_draft('77777777-7777-4777-8777-777777777777',1,true);raise exception 'Patient acted as clinician';exception when insufficient_privilege then null;end;
end$$;
select set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
select set_config('sahara.test_note',(public.healthathon_review_draft('77777777-7777-4777-8777-777777777777',1,true)).id::text,true);
do $$begin
 assert (select count(*)=1 from public.healthathon_note_at('66666666-6666-4666-8666-666666666666',1,current_setting('sahara.test_note')::uuid)),'Exact version missing';
 assert (select count(*)=0 from public.healthathon_note_at('66666666-6666-4666-8666-666666666666',2,current_setting('sahara.test_note')::uuid)),'Wrong QR version substituted';
 assert (select count(*)=1 from public.healthathon_review_receipts()),'Review receipt missing';
end$$;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select set_config('sahara.test_invite',public.healthathon_invite_access('66666666-6666-4666-8666-666666666666','family',false,'{"careNote":true,"calendar":true,"reports":false,"costs":false}',30),true);
select set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
select public.healthathon_join(current_setting('sahara.test_invite'));
do $$declare h text;begin
 select data->>'contentHash' into h from public.healthathon_records where id=current_setting('sahara.test_note')::uuid;
 begin perform public.healthathon_save_record(gen_random_uuid(),'66666666-6666-4666-8666-666666666666','ack',jsonb_build_object('versionId',current_setting('sahara.test_note'),'contentHash',h,'name','Synthetic family','relationship','Patient','at',1),0);raise exception 'Family acted as patient';exception when insufficient_privilege then null;end;
 begin perform public.healthathon_save_record(gen_random_uuid(),'66666666-6666-4666-8666-666666666666','ack',jsonb_build_object('versionId',current_setting('sahara.test_note'),'contentHash',h,'name','Synthetic family','relationship','Family / carer','at',1),0);raise exception 'Family confirmed before patient';exception when raise_exception then if SQLERRM<>'The patient needs to confirm this exact version before family adds a confirmation.' then raise;end if;end;
end$$;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select public.healthathon_save_record(gen_random_uuid(),'66666666-6666-4666-8666-666666666666','ack',jsonb_build_object('versionId',current_setting('sahara.test_note'),'contentHash',(select data->>'contentHash' from public.healthathon_records where id=current_setting('sahara.test_note')::uuid),'name','Synthetic patient','relationship','Patient','at',1),0);
select set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
select public.healthathon_save_record(gen_random_uuid(),'66666666-6666-4666-8666-666666666666','ack',jsonb_build_object('versionId',current_setting('sahara.test_note'),'contentHash',(select data->>'contentHash' from public.healthathon_records where id=current_setting('sahara.test_note')::uuid),'name','Synthetic family','relationship','Family / carer','at',1),0);
do $$begin
 assert (select count(*)=1 from public.healthathon_records where kind='ack' and data->>'actorId'=auth.uid()::text and (data->>'at')::bigint>1),'Actual actor/time missing';
end$$;
select set_config('request.jwt.claim.sub','44444444-4444-4444-8444-444444444444',true);
do $$begin
 assert (select count(*)=0 from public.healthathon_people),'Outsider can read people';
 assert (select count(*)=0 from public.healthathon_records),'Outsider can read records';
 assert (select count(*)=0 from public.healthathon_care_drafts),'Outsider can read drafts';
 begin perform public.sahara_directory_queue();raise exception 'Outsider can read contributors';exception when insufficient_privilege then null;end;
end$$;
reset role;
update public.healthathon_clinician_requests set status='suspended' where user_id='33333333-3333-4333-8333-333333333333';
set local role authenticated;
select set_config('request.jwt.claim.sub','33333333-3333-4333-8333-333333333333',true);
do $$begin
 assert (select count(*)=0 from public.healthathon_records),'Suspended clinician can read records';
 assert (select count(*)=0 from public.healthathon_care_drafts),'Suspended clinician can read drafts';
 assert (select count(*)=0 from public.healthathon_note_at('66666666-6666-4666-8666-666666666666',1,current_setting('sahara.test_note')::uuid)),'Suspended clinician can open QR';
end$$;
reset role;
-- Hospital review and its contributor data are isolated from public output.
insert into healthathon_private.staff_roles(user_id,role) values('33333333-3333-4333-8333-333333333333','directory_reviewer');
insert into healthathon_private.assist_reservations(request_id,user_id,action) values(gen_random_uuid(),'33333333-3333-4333-8333-333333333333','draft');
select healthathon_private.close_account('33333333-3333-4333-8333-333333333333');
do $$begin
 assert not exists(select 1 from healthathon_private.staff_roles where user_id='33333333-3333-4333-8333-333333333333'),'Closed account retained staff access';
 assert not exists(select 1 from healthathon_private.assist_reservations where user_id='33333333-3333-4333-8333-333333333333'),'Closed account retained AI quota identifiers';
 assert not exists(select 1 from public.healthathon_clinician_requests where user_id='33333333-3333-4333-8333-333333333333'),'Closed account retained registration request';
 assert exists(select 1 from public.healthathon_records where id=current_setting('sahara.test_note')::uuid),'Closure removed another patient-owned reviewed note';
end$$;
insert into public.sahara_feedback_receipts(id,kind,content_hash,client_hash) values('99999999-9999-4999-8999-999999999999','hospital',repeat('a',64),repeat('b',64));
insert into public.sahara_hospital_submissions(id,facility) values('99999999-9999-4999-8999-999999999999','{"hospitalName":"Synthetic rollback hospital"}');
insert into public.sahara_hospital_contributors(submission_id,full_name,respondent_role,role_other,council,council_other,registration_state,registration_number) values('99999999-9999-4999-8999-999999999999','PRIVATE_SYNTHETIC_NAME','physician','','nmc','','','TEST-ONLY');
set local role authenticated;
select set_config('request.jwt.claim.sub','55555555-5555-4555-8555-555555555555',true);
select public.sahara_review_directory('99999999-9999-4999-8999-999999999999',0,'approved','{"name":"Synthetic rollback hospital","city":"Synthetic city","state":"Karnataka","address":"Synthetic test address","phone":"","latitude":12,"longitude":77,"services":["opd"],"morphine":"unknown","notes":"Synthetic only","sourceUrl":"https://example.invalid/facility"}','Synthetic registry source','Synthetic test only',now()+interval '1 day');
reset role;
set local role anon;
do $$declare p text;begin
 p:=public.sahara_directory_list()::text;
 assert p like '%Synthetic rollback hospital%','Approved hospital not visible';
 assert p not like '%PRIVATE_SYNTHETIC_NAME%' and p not like '%TEST-ONLY%','Contributor details leaked';
 begin perform public.sahara_directory_queue();raise exception 'Anon can read review queue';exception when insufficient_privilege then null;end;
end$$;
reset role;
rollback;
select 'PASS: real PostgreSQL role, exact-version, consent, suspension and directory projection checks; synthetic transaction rolled back' as result;
