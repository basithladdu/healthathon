-- OWNER APPLIES. Not applied by any agent. Project: kjnhtakqzboayqorrlue, the one CareCloud.kt and
-- prototype/app/emergency/emergency-link.ts use. Needs 20261027190000_emergency_card_links.sql (and the
-- shared-care clinician migration 20261019151322) applied first.
--
-- Where the emergency card web link was opened (Dr Sujay, 23 Nov 2026): the page at /emergency asks the
-- person opening it for "Your hospital or clinic (optional)" before it shows the card. Each open is kept
-- with its time and that one line of text (trimmed, at most 80 characters, nothing else: no IP address,
-- no device, no name). The profile owner and the patient's approved doctor can read the last 10.
-- Only the web link can be counted: the offline code printed on the card never calls the server.
begin;

create table healthathon_private.emergency_link_views (
 id bigint generated always as identity primary key,
 link_id uuid not null references healthathon_private.emergency_links(id) on delete cascade,
 viewed_at timestamptz not null default now(),
 place text check(place is null or (char_length(place) between 1 and 80 and place !~ '[[:cntrl:]]'))
);
create index emergency_link_views_recent on healthathon_private.emergency_link_views(link_id, viewed_at desc);
alter table healthathon_private.emergency_link_views enable row level security;
-- No policies: only the security-definer functions below read or write it.
revoke all on table healthathon_private.emergency_link_views from public, anon, authenticated;

-- One plain line: control characters and runs of spaces become one space, trimmed, cut to 80, empty -> null.
create function healthathon_private.emergency_clean_place(p_place text) returns text
language sql immutable set search_path='' as $$
 select nullif(pg_catalog.btrim(pg_catalog.left(pg_catalog.btrim(pg_catalog.regexp_replace(coalesce(p_place,''), '[[:cntrl:][:space:]]+', ' ', 'g')), 80)), '')
$$;

-- The newest 10 opens of one link, newest first: [{ "at": timestamptz, "place": text|null }].
create function healthathon_private.emergency_recent_views(p_link uuid) returns jsonb
language sql stable security definer set search_path='' as $$
 select coalesce(jsonb_agg(jsonb_build_object('at',v.viewed_at,'place',v.place) order by v.viewed_at desc),'[]'::jsonb)
 from (select viewed_at, place from healthathon_private.emergency_link_views where link_id=p_link order by viewed_at desc limit 10) v
$$;
revoke all on function healthathon_private.emergency_clean_place(text), healthathon_private.emergency_recent_views(uuid) from public, anon, authenticated;

-- The anonymous view now takes an optional place. Same answers as before; an old page that sends only
-- p_token still works (p_place defaults to null). The old one-argument function is dropped so PostgREST
-- has exactly one candidate.
drop function public.healthathon_emergency_view(text);
create function public.healthathon_emergency_view(p_token text, p_place text default null) returns jsonb
language plpgsql volatile security definer set search_path='' as $$
declare link healthathon_private.emergency_links;
begin
 if p_token is null or p_token !~ '^[a-f0-9]{64}$' then return jsonb_build_object('status','unknown'); end if;
 select * into link from healthathon_private.emergency_links where token_hash=encode(extensions.digest(p_token,'sha256'),'hex');
 if not found then return jsonb_build_object('status','unknown'); end if;
 if link.revoked_at is not null then return jsonb_build_object('status','off'); end if;
 if link.expires_at<=now() then return jsonb_build_object('status','expired'); end if;
 update healthathon_private.emergency_links set views=views+1,last_viewed_at=now() where id=link.id;
 insert into healthathon_private.emergency_link_views(link_id, place) values(link.id, healthathon_private.emergency_clean_place(p_place));
 -- Keep only the newest 50 opens per link.
 delete from healthathon_private.emergency_link_views where link_id=link.id and id not in
  (select id from healthathon_private.emergency_link_views where link_id=link.id order by viewed_at desc, id desc limit 50);
 return jsonb_build_object('status','live','card',link.card,'updated_at',link.updated_at,'expires_at',link.expires_at);
end $$;
revoke all on function public.healthathon_emergency_view(text, text) from public;
grant execute on function public.healthathon_emergency_view(text, text) to anon, authenticated;

-- The owner's status (what EmergencyCardUi.kt already reads) gains recent_views.
create or replace function healthathon_private.emergency_link_status(p_person uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare live healthathon_private.emergency_links;
begin
 perform healthathon_private.emergency_link_owner(p_person);
 select * into live from healthathon_private.emergency_links where person_id=p_person and revoked_at is null;
 if not found then return jsonb_build_object('live',false); end if;
 return jsonb_build_object('live',live.expires_at>now(),'created_at',live.created_at,'updated_at',live.updated_at,'expires_at',live.expires_at,'views',live.views,'last_viewed_at',live.last_viewed_at,
  'recent_views',healthathon_private.emergency_recent_views(live.id));
end $$;

-- The same counts and recent opens for the owner or the patient's approved doctor (a clinician member whose
-- access has not expired). The card itself is not returned.
create function healthathon_private.emergency_link_views(p_person uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare live healthathon_private.emergency_links;
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not (
   coalesce(healthathon_private.is_owner(p_person),false)
   or (exists(select 1 from public.healthathon_members m where m.person_id=p_person and m.user_id=auth.uid() and m.role='clinician' and (m.expires_at is null or m.expires_at>now()))
       and exists(select 1 from public.healthathon_clinician_requests r where r.user_id=auth.uid() and r.status='approved'))) then
  raise exception 'Only the profile owner or the patient''s doctor can see where the emergency card was opened.' using errcode='42501';
 end if;
 select * into live from healthathon_private.emergency_links where person_id=p_person and revoked_at is null;
 if not found then return jsonb_build_object('live',false,'views',0,'recent_views','[]'::jsonb); end if;
 return jsonb_build_object('live',live.expires_at>now(),'views',live.views,'last_viewed_at',live.last_viewed_at,'recent_views',healthathon_private.emergency_recent_views(live.id));
end $$;
revoke all on function healthathon_private.emergency_link_views(uuid) from public, anon, authenticated;
grant execute on function healthathon_private.emergency_link_views(uuid) to authenticated;
create function public.healthathon_emergency_link_views(p_person uuid) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.emergency_link_views(p_person)$$;
revoke all on function public.healthathon_emergency_link_views(uuid) from public, anon;
grant execute on function public.healthathon_emergency_link_views(uuid) to authenticated;

commit;
