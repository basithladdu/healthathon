-- "Ask your documents" (decision 9, 28 Oct): the sahara-care-assist function reserves its attempts as action 'ask'.
-- Same limit as drafting/transcription/translation (1 a minute, 10 a day per account, 100 a day in total); no new table.
-- assist_reservations keeps RLS on and no grants to app roles, exactly as created in 20261019151322.
alter table healthathon_private.assist_reservations drop constraint if exists assist_reservations_action_check;
alter table healthathon_private.assist_reservations add constraint assist_reservations_action_check check(action in ('draft','transcribe','translate','ask'));
alter table healthathon_private.assist_reservations enable row level security;

create or replace function healthathon_private.reserve_assist(p_request_id uuid,p_action text) returns boolean
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.require_open_account();
 if coalesce((auth.jwt()->>'is_anonymous')::boolean,false) then raise exception 'Sign in with a confirmed account.' using errcode='42501'; end if;
 if p_request_id is null or p_action not in ('draft','transcribe','translate','ask') then raise exception 'Invalid request'; end if;
 perform pg_advisory_xact_lock(19402026);
 if exists(select 1 from healthathon_private.assist_reservations where request_id=p_request_id) then return false; end if;
 if (select count(*) from healthathon_private.assist_reservations where reserved_at>=date_trunc('day',now()))>=100
 or (select count(*) from healthathon_private.assist_reservations where user_id=auth.uid() and reserved_at>=date_trunc('day',now()))>=10
 or exists(select 1 from healthathon_private.assist_reservations where user_id=auth.uid() and reserved_at>now()-interval '1 minute') then return false; end if;
 insert into healthathon_private.assist_reservations(request_id,user_id,action) values(p_request_id,auth.uid(),p_action);
 return true;
end $$;
revoke all on function healthathon_private.reserve_assist(uuid,text) from public,anon;
grant execute on function healthathon_private.reserve_assist(uuid,text) to authenticated;
