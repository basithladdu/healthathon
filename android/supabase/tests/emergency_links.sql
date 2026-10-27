-- Run after 20261027190000_emergency_card_links.sql (owner applies). All fixtures roll back.
begin;
insert into auth.users(id,email) values
 ('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0001','emergency-owner-test@example.invalid'),
 ('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0002','emergency-reader-test@example.invalid');
set local role authenticated;
select set_config('request.jwt.claim.sub','5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0001',true);
insert into public.healthathon_people(id,name,created_ms) values('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','Emergency Test',1);
select set_config('test.invite',public.healthathon_invite('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100',true),true);
select set_config('test.first',public.healthathon_emergency_link_create('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','{"kind":"sahara-emergency-card","v":1,"name":"Emergency Test"}')->>'token',true);
do $$ begin
 if current_setting('test.first') !~ '^[a-f0-9]{64}$' then raise exception 'Token is not 64 hex'; end if;
 if (public.healthathon_emergency_link_status('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100')->>'live')::boolean is not true then raise exception 'New link not live'; end if;
 begin
  perform public.healthathon_emergency_link_create('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','{"kind":"other"}');
  raise exception 'Card without kind accepted';
 exception when check_violation then null; end;
end $$;
-- An editor who is not the owner cannot make, change, read status of, or turn off the link.
select set_config('request.jwt.claim.sub','5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0002',true);
select public.healthathon_join(current_setting('test.invite'));
do $$ begin
 begin perform public.healthathon_emergency_link_create('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','{"kind":"sahara-emergency-card"}'); raise exception 'Editor made a link'; exception when insufficient_privilege then null; end;
 begin perform public.healthathon_emergency_link_update('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','{"kind":"sahara-emergency-card"}'); raise exception 'Editor changed a link'; exception when insufficient_privilege then null; end;
 begin perform public.healthathon_emergency_link_revoke('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100'); raise exception 'Editor turned off a link'; exception when insufficient_privilege then null; end;
 begin perform public.healthathon_emergency_link_status('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100'); raise exception 'Editor read link status'; exception when insufficient_privilege then null; end;
 begin perform count(*) from healthathon_private.emergency_links; raise exception 'Table readable'; exception when insufficient_privilege then null; end;
end $$;
-- Anyone with the link (no account) can read it; nothing else is readable.
reset role; set local role anon;
do $$ begin
 if public.healthathon_emergency_view(current_setting('test.first'))->>'status'<>'live' then raise exception 'Live link does not open'; end if;
 if public.healthathon_emergency_view(current_setting('test.first'))->'card'->>'name'<>'Emergency Test' then raise exception 'Card not returned'; end if;
 if public.healthathon_emergency_view(repeat('0',64))->>'status'<>'unknown' then raise exception 'Unknown token opened'; end if;
 if public.healthathon_emergency_view('x')->>'status'<>'unknown' then raise exception 'Bad token accepted'; end if;
 begin perform public.healthathon_emergency_link_status('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100'); raise exception 'Anon read status'; exception when insufficient_privilege then null; end;
end $$;
reset role; set local role authenticated;
select set_config('request.jwt.claim.sub','5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0001',true);
do $$ begin
 if (public.healthathon_emergency_link_status('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100')->>'views')::int<>2 then raise exception 'Opens not counted'; end if;
 perform public.healthathon_emergency_link_update('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','{"kind":"sahara-emergency-card","v":1,"name":"Updated"}');
 if public.healthathon_emergency_view(current_setting('test.first'))->'card'->>'name'<>'Updated' then raise exception 'Same link did not show the update'; end if;
end $$;
-- A new link (leaked card) turns the old one off at once.
select set_config('test.second',public.healthathon_emergency_link_create('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','{"kind":"sahara-emergency-card","v":1}')->>'token',true);
do $$ begin
 if public.healthathon_emergency_view(current_setting('test.first'))->>'status'<>'off' then raise exception 'Old link still opens'; end if;
 if public.healthathon_emergency_view(current_setting('test.second'))->>'status'<>'live' then raise exception 'New link does not open'; end if;
 perform public.healthathon_emergency_link_revoke('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100');
 if public.healthathon_emergency_view(current_setting('test.second'))->>'status'<>'off' then raise exception 'Turned-off link still opens'; end if;
 begin perform public.healthathon_emergency_link_update('5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100','{"kind":"sahara-emergency-card"}'); raise exception 'Updated an off link'; exception when raise_exception then if sqlerrm not like '%off or expired%' then raise; end if; end;
end $$;
-- Removing the person removes every link.
delete from public.healthathon_people where id='5b0b8a1e-6f0e-4c39-9f43-2f7d3c1a0100';
do $$ begin if public.healthathon_emergency_view(current_setting('test.second'))->>'status'<>'unknown' then raise exception 'Link survived person removal'; end if; end $$;
rollback;
select 'PASS: emergency links: owner-only management, anonymous read by token only, update keeps link, replace and turn-off stop old links, cascade on removal' as result;
