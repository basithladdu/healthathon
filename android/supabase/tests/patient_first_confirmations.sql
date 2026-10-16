-- Synthetic fixtures only. Every fixture and change is rolled back.
begin;
insert into auth.users(id,email) values
 ('59f46321-d9e0-4262-8733-fa6a9d73425b','ack-owner@example.invalid'),
 ('f1007061-1351-4cf7-a35b-09da04fba36d','ack-reader@example.invalid'),
 ('f6dd90bd-fbfa-482a-b613-43fd5ca7b1e8','ack-outsider@example.invalid');
set local role authenticated;
select set_config('request.jwt.claim.sub','59f46321-d9e0-4262-8733-fa6a9d73425b',true);
insert into public.healthathon_people(id,name,created_ms) values('0c4ce48e-65a3-4651-ad05-d7c7814fb729','Confirmation fixture',1);
select public.healthathon_save_record('14556689-e3e6-4b09-a283-bf1aa05050bd','0c4ce48e-65a3-4651-ad05-d7c7814fb729','note','{"version":1,"personName":"Confirmation fixture","source":"I am not ready to decide.","doctor":"Synthetic doctor","published":1,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
select set_config('test.ack_invite',public.healthathon_invite('0c4ce48e-65a3-4651-ad05-d7c7814fb729',false),true);
select set_config('request.jwt.claim.sub','f1007061-1351-4cf7-a35b-09da04fba36d',true);
select public.healthathon_join(current_setting('test.ack_invite'));

do $$ begin
 begin
  perform public.healthathon_save_record('d2c54db3-2325-4f6c-8d46-f24dd05aa79b','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Family first","relationship":"Family / carer","at":2,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
  raise exception 'Family confirmation before patient was accepted';
 exception when raise_exception then if sqlerrm<>'The patient needs to confirm this version before family adds a confirmation' then raise; end if; end;
end $$;
-- Legacy patient shape remains valid: row ID doubles as note version ID.
select public.healthathon_save_record('14556689-e3e6-4b09-a283-bf1aa05050bd','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"name":"Patient","relationship":"Patient","at":3,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
select public.healthathon_save_record('d2c54db3-2325-4f6c-8d46-f24dd05aa79b','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Family one","relationship":"Family / carer","at":4,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
select public.healthathon_save_record('03dcd258-e915-4493-9684-4e729adba8b4','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Family two","relationship":"Family / carer","at":5,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
-- Same payload replay and new-ID repeated tap are both idempotent.
select public.healthathon_save_record('d2c54db3-2325-4f6c-8d46-f24dd05aa79b','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Family one","relationship":"Family / carer","at":4,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
select public.healthathon_save_record('be228090-9768-4d9f-ae50-7ee8126c7b77','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":" family ONE ","relationship":"Family / carer","at":9,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
do $$ begin
 if (select count(*) from public.healthathon_records where kind='ack')<>3 then raise exception 'Separate confirmations or idempotency failed'; end if;
 if (select data->>'at' from public.healthathon_records where kind='ack' and id='d2c54db3-2325-4f6c-8d46-f24dd05aa79b')<>'4' then raise exception 'Replay rewrote original confirmation'; end if;
 begin
  perform public.healthathon_save_record('d2c54db3-2325-4f6c-8d46-f24dd05aa79b','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Changed person","relationship":"Family / carer","at":4,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
  raise exception 'Saved confirmation was mutable';
 exception when insufficient_privilege then null; end;
 begin
  perform public.healthathon_save_record('c32e5217-183c-43e3-b9fa-559a1129ed9e','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Different patient","relationship":"Patient","at":6,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
  raise exception 'Second patient confirmation was accepted';
 exception when raise_exception then if sqlerrm<>'The patient confirmation is already saved for this version' then raise; end if; end;
 begin
  perform public.healthathon_save_record('c32e5217-183c-43e3-b9fa-559a1129ed9e','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Wrong hash","relationship":"Family / carer","at":6,"contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}',0);
  raise exception 'Wrong content hash was accepted';
 exception when raise_exception then if sqlerrm<>'Open the latest note before confirming' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','f6dd90bd-fbfa-482a-b613-43fd5ca7b1e8',true);
do $$ begin
 if exists(select 1 from public.healthathon_records) then raise exception 'Outsider can read confirmations'; end if;
 begin
  perform public.healthathon_save_record('c32e5217-183c-43e3-b9fa-559a1129ed9e','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Outsider","relationship":"Family / carer","at":6,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
  raise exception 'Outsider could confirm';
 exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub','59f46321-d9e0-4262-8733-fa6a9d73425b',true);
select public.healthathon_save_record('97bf165b-a179-4dfb-aeb7-99c5286771a3','0c4ce48e-65a3-4651-ad05-d7c7814fb729','note','{"version":2,"personName":"Confirmation fixture","source":"I changed my mind.","doctor":"Synthetic doctor","published":7,"contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}',0);
do $$ begin
 begin
  perform public.healthathon_save_record('c32e5217-183c-43e3-b9fa-559a1129ed9e','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"14556689-e3e6-4b09-a283-bf1aa05050bd","name":"Old version","relationship":"Family / carer","at":8,"contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}',0);
  raise exception 'Earlier version could be confirmed';
 exception when raise_exception then if sqlerrm<>'Open the latest note before confirming' then raise; end if; end;
 begin
  perform public.healthathon_save_record('c32e5217-183c-43e3-b9fa-559a1129ed9e','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"97bf165b-a179-4dfb-aeb7-99c5286771a3","name":"New version family","relationship":"Family / carer","at":8,"contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}',0);
  raise exception 'Old patient confirmation leaked into new version';
 exception when raise_exception then if sqlerrm<>'The patient needs to confirm this version before family adds a confirmation' then raise; end if; end;
 if (select count(*) from public.healthathon_records where kind='ack')<>3 then raise exception 'Rejected requests changed existing confirmations'; end if;
end $$;
-- Model an immutable family-only row written by the old client, before patient-first enforcement.
-- It occupies the note's legacy acknowledgement ID and must never be rewritten into patient consent.
reset role;
insert into public.healthathon_records(id,kind,person_id,data) values(
 '97bf165b-a179-4dfb-aeb7-99c5286771a3','ack','0c4ce48e-65a3-4651-ad05-d7c7814fb729',
 '{"name":"Legacy family","relationship":"Family / carer","at":8,"contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}'
);
set local role authenticated;
select set_config('request.jwt.claim.sub','59f46321-d9e0-4262-8733-fa6a9d73425b',true);
select public.healthathon_save_record('ec01d68e-3a85-4d14-bbb2-266c7fbd2c16','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"97bf165b-a179-4dfb-aeb7-99c5286771a3","name":"Patient version two","relationship":"Patient","at":10,"contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}',0);
select public.healthathon_save_record('7dfcb595-7790-4c9d-b7e4-1292c06c25ed','0c4ce48e-65a3-4651-ad05-d7c7814fb729','ack','{"versionId":"97bf165b-a179-4dfb-aeb7-99c5286771a3","name":"New family after patient","relationship":"Family / carer","at":11,"contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}',0);
do $$ begin
 if not exists(select 1 from public.healthathon_records where kind='ack' and id='97bf165b-a179-4dfb-aeb7-99c5286771a3' and data='{"name":"Legacy family","relationship":"Family / carer","at":8,"contentHash":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}'::jsonb) then raise exception 'Legacy family confirmation was changed or removed'; end if;
 if not exists(select 1 from public.healthathon_records where kind='ack' and id='ec01d68e-3a85-4d14-bbb2-266c7fbd2c16' and data->>'versionId'='97bf165b-a179-4dfb-aeb7-99c5286771a3' and data->>'relationship'='Patient') then raise exception 'New patient confirmation collided with legacy family ID'; end if;
 if (select count(*) from public.healthathon_records where kind='ack' and coalesce(data->>'versionId',id::text)='97bf165b-a179-4dfb-aeb7-99c5286771a3')<>3 then raise exception 'Legacy family, patient and new family confirmations did not coexist'; end if;
 if (select count(*) from public.healthathon_records where kind='ack')<>6 then raise exception 'Legacy regression changed previous version confirmations'; end if;
end $$;
rollback;
select 'PASS: patient-first, separate family rows, legacy shape and family-ID collision, replay, immutability, exact hash, latest version and outsider checks; fixtures rolled back' as result;
