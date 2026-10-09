begin;
insert into auth.users(id,email) values
 ('d486bf27-46b2-4e91-8685-98ad4a8ba521','healthathon-owner-test@example.invalid'),
 ('918059d4-428e-42eb-a1cc-b7294540da10','healthathon-reader-test@example.invalid'),
 ('9e470965-17a3-4cbe-921c-c57c3e90dabd','healthathon-other-test@example.invalid');
set local role authenticated;
select set_config('request.jwt.claim.sub','d486bf27-46b2-4e91-8685-98ad4a8ba521',true);
insert into public.healthathon_people(id,name,created_ms) values('f83366ee-7b34-42a7-8a4e-cfd680801b9e','Permission Test',1);
select public.healthathon_save_record('ec9a1bdf-cd7a-4c55-b687-b053a4983123','f83366ee-7b34-42a7-8a4e-cfd680801b9e','entry','{"kind":"task","title":"Read prescription","detail":"","created":1}',0);
select set_config('test.invite',public.healthathon_invite('f83366ee-7b34-42a7-8a4e-cfd680801b9e',false),true);
select set_config('request.jwt.claim.sub','9e470965-17a3-4cbe-921c-c57c3e90dabd',true);
do $$ begin
 if exists(select 1 from public.healthathon_people) then raise exception 'Unrelated account can read person'; end if;
 if exists(select 1 from public.healthathon_records) then raise exception 'Unrelated account can read entries'; end if;
 begin
  perform public.healthathon_save_record(gen_random_uuid(),'f83366ee-7b34-42a7-8a4e-cfd680801b9e','entry','{"kind":"task","title":"Forbidden","created":1}',0);
  raise exception 'Unrelated account can write';
 exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub','918059d4-428e-42eb-a1cc-b7294540da10',true);
select public.healthathon_join(current_setting('test.invite'));
do $$ begin
 if (select count(*) from public.healthathon_records)<>1 then raise exception 'Invited reader cannot read'; end if;
 begin
  perform public.healthathon_save_record(gen_random_uuid(),'f83366ee-7b34-42a7-8a4e-cfd680801b9e','entry','{"kind":"task","title":"Forbidden","created":1}',0);
  raise exception 'Reader can write';
 exception when insufficient_privilege then null; end;
 begin
  perform public.healthathon_join(current_setting('test.invite'));
  raise exception 'Invitation reuse accepted';
 exception when raise_exception then if sqlerrm<>'Invitation expired or already used.' then raise; end if; end;
end $$;
select set_config('request.jwt.claim.sub','d486bf27-46b2-4e91-8685-98ad4a8ba521',true);
select set_config('test.invite',public.healthathon_invite('f83366ee-7b34-42a7-8a4e-cfd680801b9e',true),true);
select set_config('request.jwt.claim.sub','918059d4-428e-42eb-a1cc-b7294540da10',true);
select public.healthathon_join(current_setting('test.invite'));
select public.healthathon_save_record('ec9a1bdf-cd7a-4c55-b687-b053a4983123','f83366ee-7b34-42a7-8a4e-cfd680801b9e','entry','{"kind":"task","title":"Updated","created":1}',1);
do $$ begin
 if (select revision from public.healthathon_records where id='ec9a1bdf-cd7a-4c55-b687-b053a4983123')<>2 then raise exception 'Revision failed'; end if;
 begin
  perform public.healthathon_save_record('ec9a1bdf-cd7a-4c55-b687-b053a4983123','f83366ee-7b34-42a7-8a4e-cfd680801b9e','entry','{"kind":"task","title":"Stale edit","created":1}',1);
  raise exception 'Stale edit accepted';
 exception when serialization_failure then null; end;
 begin
  update public.healthathon_records set data='{}' where id='ec9a1bdf-cd7a-4c55-b687-b053a4983123';
  raise exception 'Validation bypass allowed';
 exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub','d486bf27-46b2-4e91-8685-98ad4a8ba521',true);
delete from public.healthathon_members where person_id='f83366ee-7b34-42a7-8a4e-cfd680801b9e';
select set_config('request.jwt.claim.sub','918059d4-428e-42eb-a1cc-b7294540da10',true);
do $$ begin if exists(select 1 from public.healthathon_records) then raise exception 'Revoked member can read'; end if; end $$;
rollback;
select 'PASS: owner, outsider, reader, editor, invite reuse, conflict, bypass and revocation checks; all fixtures rolled back' as result;
