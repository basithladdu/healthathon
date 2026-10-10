-- Run as the database admin after installing the migration. All fixtures and
-- closures use this transaction only; ON_ERROR_STOP must be enabled by the runner.
begin;

insert into auth.users(id,email) values
 ('1e31734e-0b79-4c36-9f15-e853f74b8b60','sahara-closure-owner@example.invalid'),
 ('57fbaf3f-8c18-4e10-8ee0-2b4154eac975','sahara-closure-other@example.invalid'),
 ('be7e038b-f31f-44cd-a125-847aed11f03e','sahara-closure-reader@example.invalid');

do $$ begin
  if has_function_privilege('anon','public.healthathon_close_account(uuid)','EXECUTE')
     or has_function_privilege('authenticated','public.healthathon_close_account(uuid)','EXECUTE')
     or has_function_privilege('authenticated','healthathon_private.close_account(uuid)','EXECUTE')
     or has_table_privilege('authenticated','healthathon_private.closed_accounts','SELECT')
     or has_table_privilege('authenticated','healthathon_private.closed_accounts','INSERT')
     or has_table_privilege('authenticated','healthathon_private.closed_accounts','DELETE') then
    raise exception 'Closure controls or marker data are exposed to clients';
  end if;
  if not has_function_privilege('service_role','public.healthathon_close_account(uuid)','EXECUTE') then
    raise exception 'Support cannot close an account';
  end if;
end $$;

set local role authenticated;
select set_config('request.jwt.claim.sub','1e31734e-0b79-4c36-9f15-e853f74b8b60',true);
insert into public.healthathon_people(id,name,created_ms)
values('1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25','Closure fixture',1);
select public.healthathon_save_record('8d674f28-efc5-4bf8-a6b8-ac47e0cbe7d6','1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25','entry','{"kind":"task","title":"Owned task","created":1}',0);
select set_config('test.owned_invite',public.healthathon_invite('1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25',false),true);
select set_config('test.owned_open_invite',public.healthathon_invite('1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25',false),true);

-- The owner cannot call the support-only function even for their own UUID.
do $$ begin
  begin
    perform public.healthathon_close_account('1e31734e-0b79-4c36-9f15-e853f74b8b60');
    raise exception 'Client closed an account';
  exception when insufficient_privilege then null; end;
end $$;

select set_config('request.jwt.claim.sub','be7e038b-f31f-44cd-a125-847aed11f03e',true);
select public.healthathon_join(current_setting('test.owned_invite'));
select set_config('request.jwt.claim.sub','57fbaf3f-8c18-4e10-8ee0-2b4154eac975',true);
insert into public.healthathon_people(id,name,created_ms)
values('51ea4352-74b2-4c70-ab9a-acf6ed65312f','Other owner fixture',1);
select public.healthathon_save_record('9701a57a-d28b-4594-9329-6a8d5f7e196e','51ea4352-74b2-4c70-ab9a-acf6ed65312f','entry','{"kind":"task","title":"Keep task","created":1}',0);
select set_config('test.shared_invite',public.healthathon_invite('51ea4352-74b2-4c70-ab9a-acf6ed65312f',true),true);
select set_config('test.unused_invite',public.healthathon_invite('51ea4352-74b2-4c70-ab9a-acf6ed65312f',true),true);
select set_config('request.jwt.claim.sub','1e31734e-0b79-4c36-9f15-e853f74b8b60',true);
select public.healthathon_join(current_setting('test.shared_invite'));

-- An error after the service operation must roll its marker and cascades back.
set local role service_role;
do $$ begin
  begin
    perform public.healthathon_close_account('1e31734e-0b79-4c36-9f15-e853f74b8b60');
    raise exception 'closure rollback probe' using errcode='ZC001';
  exception when sqlstate 'ZC001' then null; end;
end $$;
reset role;
do $$ begin
  if exists(select 1 from healthathon_private.closed_accounts where user_id='1e31734e-0b79-4c36-9f15-e853f74b8b60')
     or not exists(select 1 from public.healthathon_people where id='1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25')
     or not exists(select 1 from public.healthathon_records where id='8d674f28-efc5-4bf8-a6b8-ac47e0cbe7d6')
     or not exists(select 1 from public.healthathon_members where user_id='1e31734e-0b79-4c36-9f15-e853f74b8b60') then
    raise exception 'Closure was not atomic';
  end if;
end $$;

set local role service_role;
select set_config('test.closure_result',public.healthathon_close_account('1e31734e-0b79-4c36-9f15-e853f74b8b60')::text,true);
reset role;
do $$ declare result jsonb := current_setting('test.closure_result')::jsonb; begin
  if result->>'removed_people'<>'1' or result->>'removed_memberships'<>'1' or result->>'closed_at' is null then
    raise exception 'Unexpected closure result';
  end if;
  if (select count(*) from healthathon_private.closed_accounts where user_id='1e31734e-0b79-4c36-9f15-e853f74b8b60')<>1
     or exists(select 1 from public.healthathon_people where owner_id='1e31734e-0b79-4c36-9f15-e853f74b8b60')
     or exists(select 1 from public.healthathon_records where person_id='1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25')
     or exists(select 1 from public.healthathon_members where user_id='1e31734e-0b79-4c36-9f15-e853f74b8b60' or person_id='1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25')
     or exists(select 1 from healthathon_private.invites where person_id='1c9a4f42-d1cb-490d-b890-0e2f7a0cfd25') then
    raise exception 'Owned data or invitations survived closure';
  end if;
  if not exists(select 1 from auth.users where id='1e31734e-0b79-4c36-9f15-e853f74b8b60')
     or not exists(select 1 from public.healthathon_people where id='51ea4352-74b2-4c70-ab9a-acf6ed65312f')
     or not exists(select 1 from public.healthathon_records where id='9701a57a-d28b-4594-9329-6a8d5f7e196e') then
    raise exception 'Closure removed an Auth identity or another owner record';
  end if;
end $$;

-- Admin-only canaries prove closure, rather than missing ownership alone, blocks
-- every RLS path. These synthetic rows are removed before idempotency checks.
insert into public.healthathon_people(id,owner_id,name,created_ms)
values('b5b83b04-73e7-49a5-91cf-5970b064c6b0','1e31734e-0b79-4c36-9f15-e853f74b8b60','Closure RLS canary',1);
insert into public.healthathon_records(id,kind,person_id,data)
values('a0431719-70ba-47b6-9f1c-909678d75f5b','entry','b5b83b04-73e7-49a5-91cf-5970b064c6b0','{"kind":"task","title":"Do not alter","created":1}');
insert into public.healthathon_members(person_id,user_id,can_edit) values
 ('b5b83b04-73e7-49a5-91cf-5970b064c6b0','be7e038b-f31f-44cd-a125-847aed11f03e',false),
 ('51ea4352-74b2-4c70-ab9a-acf6ed65312f','1e31734e-0b79-4c36-9f15-e853f74b8b60',true);

-- The same existing JWT subject is still present: closure must not depend on
-- token refresh, sign-out, or client-side visibility checks.
set local role authenticated;
select set_config('request.jwt.claim.sub','1e31734e-0b79-4c36-9f15-e853f74b8b60',true);
do $$ declare command text; touched bigint; begin
  foreach command in array array[
    'select healthathon_private.require_open_account()',
    'insert into public.healthathon_people(id,name,created_ms) values (''6e90381f-fd2e-45b6-abbb-9c3b6622771c'',''Blocked'',1)',
    'select public.healthathon_invite(''51ea4352-74b2-4c70-ab9a-acf6ed65312f'',true)',
    'select public.healthathon_join(current_setting(''test.unused_invite''))',
    'select healthathon_private.use_invite(current_setting(''test.unused_invite''))',
    'select public.healthathon_members_list(''51ea4352-74b2-4c70-ab9a-acf6ed65312f'')',
    'select public.healthathon_save_record(gen_random_uuid(),''51ea4352-74b2-4c70-ab9a-acf6ed65312f'',''entry'',''{"kind":"task","title":"Blocked","created":1}''::jsonb,0)',
    'select healthathon_private.save_record(gen_random_uuid(),''51ea4352-74b2-4c70-ab9a-acf6ed65312f'',''entry'',''{"kind":"task","title":"Blocked","created":1}''::jsonb,0)'
  ] loop
    begin
      execute command;
      raise exception 'Closed account operation succeeded: %',command;
    exception when raise_exception then
      if sqlerrm<>'Your Sahara account is closed. Contact workwithdevit@gmail.com for help.' then raise; end if;
    end;
  end loop;
  -- RLS may return no rows without evaluating a predicate on an empty scan.
  -- Both no rows and the explicit closure error correctly deny reading.
  begin
    if exists(select 1 from public.healthathon_people) then raise exception 'Closed account read people'; end if;
  exception when raise_exception then
    if sqlerrm<>'Your Sahara account is closed. Contact workwithdevit@gmail.com for help.' then raise; end if;
  end;
  begin
    if exists(select 1 from public.healthathon_records) then raise exception 'Closed account read records'; end if;
  exception when raise_exception then
    if sqlerrm<>'Your Sahara account is closed. Contact workwithdevit@gmail.com for help.' then raise; end if;
  end;
  begin
    if exists(select 1 from public.healthathon_members) then raise exception 'Closed account read memberships'; end if;
  exception when raise_exception then
    if sqlerrm<>'Your Sahara account is closed. Contact workwithdevit@gmail.com for help.' then raise; end if;
  end;
  foreach command in array array[
    'update public.healthathon_people set name=''Forbidden'' where id=''b5b83b04-73e7-49a5-91cf-5970b064c6b0''',
    'delete from public.healthathon_people where id=''b5b83b04-73e7-49a5-91cf-5970b064c6b0''',
    'delete from public.healthathon_records where id=''a0431719-70ba-47b6-9f1c-909678d75f5b''',
    'delete from public.healthathon_members where person_id=''b5b83b04-73e7-49a5-91cf-5970b064c6b0'''
  ] loop
    begin
      execute command;
      get diagnostics touched = row_count;
      if touched<>0 then raise exception 'Closed account changed records: %',command; end if;
    exception when raise_exception then
      if sqlerrm<>'Your Sahara account is closed. Contact workwithdevit@gmail.com for help.' then raise; end if;
    end;
  end loop;
end $$;

reset role;
do $$ begin
  if not exists(select 1 from public.healthathon_people where id='b5b83b04-73e7-49a5-91cf-5970b064c6b0' and name='Closure RLS canary')
     or not exists(select 1 from public.healthathon_records where id='a0431719-70ba-47b6-9f1c-909678d75f5b')
     or not exists(select 1 from public.healthathon_members where person_id='b5b83b04-73e7-49a5-91cf-5970b064c6b0') then
    raise exception 'Closed account changed an RLS canary';
  end if;
end $$;
delete from public.healthathon_people where id='b5b83b04-73e7-49a5-91cf-5970b064c6b0';
delete from public.healthathon_members where person_id='51ea4352-74b2-4c70-ab9a-acf6ed65312f' and user_id='1e31734e-0b79-4c36-9f15-e853f74b8b60';
set local role authenticated;

-- Rejected join did not consume the other owner's invitation; an open account
-- can still join and edit. Closure does not weaken ordinary permissions.
select set_config('request.jwt.claim.sub','be7e038b-f31f-44cd-a125-847aed11f03e',true);
select public.healthathon_join(current_setting('test.unused_invite'));
select public.healthathon_save_record('9701a57a-d28b-4594-9329-6a8d5f7e196e','51ea4352-74b2-4c70-ab9a-acf6ed65312f','entry','{"kind":"task","title":"Open editor still works","created":1}',1);

-- Repeated support requests are harmless and preserve the original closure date.
set local role service_role;
do $$ declare result jsonb; begin
  result := public.healthathon_close_account('1e31734e-0b79-4c36-9f15-e853f74b8b60');
  if result->>'removed_people'<>'0' or result->>'removed_memberships'<>'0'
     or result->>'closed_at'<>(current_setting('test.closure_result')::jsonb->>'closed_at') then
    raise exception 'Repeated closure changed the marker or unrelated data';
  end if;
  begin
    perform public.healthathon_close_account(null);
    raise exception 'Null account was accepted';
  exception when null_value_not_allowed then null; end;
  begin
    perform public.healthathon_close_account('46f5c213-721f-4558-8913-b38563308f4d');
    raise exception 'Missing account was accepted';
  exception when no_data_found then null; end;
end $$;
reset role;

-- The marker follows a later Auth deletion, while an old JWT for that missing
-- identity remains blocked. This deletes only a synthetic fixture in rollback.
delete from auth.users where id='1e31734e-0b79-4c36-9f15-e853f74b8b60';
do $$ begin
  if exists(select 1 from healthathon_private.closed_accounts where user_id='1e31734e-0b79-4c36-9f15-e853f74b8b60') then
    raise exception 'Closure marker did not follow Auth deletion';
  end if;
end $$;
set local role authenticated;
select set_config('request.jwt.claim.sub','1e31734e-0b79-4c36-9f15-e853f74b8b60',true);
do $$ begin
  begin
    perform healthathon_private.require_open_account();
    raise exception 'Old JWT for deleted identity was accepted';
  exception when raise_exception then
    if sqlerrm<>'Your Sahara account is closed. Contact workwithdevit@gmail.com for help.' then raise; end if;
  end;
end $$;
reset role;

rollback;
select 'PASS: closure authorization, privacy, atomicity, cascades, shared identity preservation, old JWT guard, invite preservation, open-account editing, idempotency and marker lifecycle; all fixtures rolled back' as result;
