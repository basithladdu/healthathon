-- OWNER APPLIES. Not applied by any agent. Project: kjnhtakqzboayqorrlue, the one CareCloud.kt and
-- prototype/app/emergency/emergency-link.ts use. Needs 20261027190000_emergency_card_links.sql and
-- 20261123120000_emergency_card_view_places.sql applied first.
--
-- Emergency card versions (clinical team meeting, 24 Nov 2026).
-- Each card the family publishes is a numbered version with its OWN random link token (32 random bytes,
-- only the SHA-256 is kept). The QR printed on a card holds only https://sahara.wedevit.in/emergency#t=<token>,
-- so the page always reads the card from this server, never from text inside the QR.
--   * Scanning an older version's QR shows that version as it was printed, with "This is not the latest version
--     of this card". The page can then open the latest version through the same token (p_latest = true).
--   * The page shows "more than 3 months old" from published_at (the app also prints a valid-until date).
--   * "Replace link" / "Turn off" still revoke the whole series at once (a leaked card), exactly as before.
-- Backward compatible: every existing link becomes version 1 of its own series with the same token, so cards
-- already printed keep opening. The old create/update/view calls keep working for older apps and pages.
begin;

create table healthathon_private.emergency_link_versions (
 id uuid primary key default gen_random_uuid(),
 link_id uuid not null references healthathon_private.emergency_links(id) on delete cascade,
 version integer not null check(version>=1),
 token_hash text not null unique check(token_hash ~ '^[a-f0-9]{64}$'),
 card jsonb not null check(jsonb_typeof(card)='object' and card->>'kind'='sahara-emergency-card' and octet_length(card::text)<=32768),
 published_at timestamptz not null default now(),
 unique(link_id, version)
);
create index emergency_link_versions_newest on healthathon_private.emergency_link_versions(link_id, version desc);
alter table healthathon_private.emergency_link_versions enable row level security;
-- No policies: only the security-definer functions below read or write it.
revoke all on table healthathon_private.emergency_link_versions from public, anon, authenticated;

-- Cards already printed: their link is version 1 of its series, same token, current content.
insert into healthathon_private.emergency_link_versions(link_id, version, token_hash, card, published_at)
select l.id, 1, l.token_hash, l.card, l.updated_at from healthathon_private.emergency_links l
on conflict do nothing;

-- A new series (first card, or "Replace link"): turns the old series off, as before, and records version 1.
create or replace function healthathon_private.emergency_link_create(p_person uuid,p_card jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare token text:=encode(extensions.gen_random_bytes(32),'hex'); hash text; saved healthathon_private.emergency_links;
begin
 perform healthathon_private.emergency_link_owner(p_person);
 perform pg_catalog.pg_advisory_xact_lock(731044822,pg_catalog.hashtext(p_person::text));
 hash:=encode(extensions.digest(token,'sha256'),'hex');
 update healthathon_private.emergency_links set revoked_at=now() where person_id=p_person and revoked_at is null;
 insert into healthathon_private.emergency_links(person_id,token_hash,card,created_by)
 values(p_person,hash,p_card,auth.uid()) returning * into saved;
 insert into healthathon_private.emergency_link_versions(link_id,version,token_hash,card,published_at)
 values(saved.id,1,hash,p_card,saved.created_at);
 return jsonb_build_object('token',token,'created_at',saved.created_at,'expires_at',saved.expires_at,'version',1,'published_at',saved.created_at);
end $$;

-- Apps from before 24 Nov: same token, content changed in place (the newest version is edited).
create or replace function healthathon_private.emergency_link_update(p_person uuid,p_card jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare saved healthathon_private.emergency_links;
begin
 perform healthathon_private.emergency_link_owner(p_person);
 perform pg_catalog.pg_advisory_xact_lock(731044822,pg_catalog.hashtext(p_person::text));
 update healthathon_private.emergency_links set card=p_card,updated_at=now()
 where person_id=p_person and revoked_at is null and expires_at>now() returning * into saved;
 if not found then raise exception 'This emergency link is off or expired. Make a new link.'; end if;
 update healthathon_private.emergency_link_versions v set card=p_card
 where v.link_id=saved.id and v.version=(select max(x.version) from healthathon_private.emergency_link_versions x where x.link_id=saved.id);
 return jsonb_build_object('updated_at',saved.updated_at,'expires_at',saved.expires_at);
end $$;

-- Publish a new version: a new token for the new printed card; older versions keep opening with the
-- "not the latest version" banner. With no live series it starts one (same as create).
create function healthathon_private.emergency_link_publish(p_person uuid,p_card jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare token text; hash text; live healthathon_private.emergency_links; next_version integer; now_at timestamptz:=now();
begin
 perform healthathon_private.emergency_link_owner(p_person);
 perform pg_catalog.pg_advisory_xact_lock(731044822,pg_catalog.hashtext(p_person::text));
 select * into live from healthathon_private.emergency_links where person_id=p_person and revoked_at is null and expires_at>now();
 if not found then return healthathon_private.emergency_link_create(p_person,p_card); end if;
 token:=encode(extensions.gen_random_bytes(32),'hex');
 hash:=encode(extensions.digest(token,'sha256'),'hex');
 select coalesce(max(v.version),0)+1 into next_version from healthathon_private.emergency_link_versions v where v.link_id=live.id;
 insert into healthathon_private.emergency_link_versions(link_id,version,token_hash,card,published_at) values(live.id,next_version,hash,p_card,now_at);
 update healthathon_private.emergency_links set card=p_card,updated_at=now_at,expires_at=greatest(expires_at,now_at+interval '365 days')
 where id=live.id returning * into live;
 return jsonb_build_object('token',token,'created_at',live.created_at,'expires_at',live.expires_at,'version',next_version,'published_at',now_at);
end $$;
revoke all on function healthathon_private.emergency_link_publish(uuid,jsonb) from public, anon, authenticated;
grant execute on function healthathon_private.emergency_link_publish(uuid,jsonb) to authenticated;
create function public.healthathon_emergency_link_publish(p_person uuid,p_card jsonb) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.emergency_link_publish(p_person,p_card)$$;
revoke all on function public.healthathon_emergency_link_publish(uuid,jsonb) from public, anon;
grant execute on function public.healthathon_emergency_link_publish(uuid,jsonb) to authenticated;

-- The anonymous view. Same answers as before for a newest-version token (old pages read status/card/updated_at/
-- expires_at as before). New keys: version and published_at of the card shown, scanned_version, latest_version,
-- latest_published_at. p_latest = true opens the newest version of the same live series (not counted again).
-- Nothing about the scanner is kept except the optional typed hospital or clinic line (no location, IP or device).
drop function if exists public.healthathon_emergency_view(text, text);
create function public.healthathon_emergency_view(p_token text, p_place text default null, p_latest boolean default false) returns jsonb
language plpgsql volatile security definer set search_path='' as $$
declare link healthathon_private.emergency_links; scanned healthathon_private.emergency_link_versions; newest healthathon_private.emergency_link_versions;
 hash text; scanned_version integer; latest_version integer; shown_card jsonb; shown_version integer; shown_at timestamptz;
begin
 if p_token is null or p_token !~ '^[a-f0-9]{64}$' then return jsonb_build_object('status','unknown'); end if;
 hash:=encode(extensions.digest(p_token,'sha256'),'hex');
 select * into scanned from healthathon_private.emergency_link_versions where token_hash=hash;
 if found then
  select * into link from healthathon_private.emergency_links where id=scanned.link_id;
 else
  select * into link from healthathon_private.emergency_links where token_hash=hash;
 end if;
 if link.id is null then return jsonb_build_object('status','unknown'); end if;
 if link.revoked_at is not null then return jsonb_build_object('status','off'); end if;
 if link.expires_at<=now() then return jsonb_build_object('status','expired'); end if;
 select * into newest from healthathon_private.emergency_link_versions where link_id=link.id order by version desc limit 1;
 latest_version:=coalesce(newest.version,1);
 scanned_version:=coalesce(scanned.version,latest_version);
 if coalesce(p_latest,false) or scanned.id is null then
  shown_card:=coalesce(newest.card,link.card); shown_version:=latest_version; shown_at:=coalesce(newest.published_at,link.updated_at);
 else
  shown_card:=scanned.card; shown_version:=scanned.version; shown_at:=scanned.published_at;
 end if;
 if not coalesce(p_latest,false) then
  update healthathon_private.emergency_links set views=views+1,last_viewed_at=now() where id=link.id;
  insert into healthathon_private.emergency_link_views(link_id, place) values(link.id, healthathon_private.emergency_clean_place(p_place));
  -- Keep only the newest 50 opens per link.
  delete from healthathon_private.emergency_link_views where link_id=link.id and id not in
   (select id from healthathon_private.emergency_link_views where link_id=link.id order by viewed_at desc, id desc limit 50);
 end if;
 return jsonb_build_object('status','live','card',shown_card,'updated_at',link.updated_at,'expires_at',link.expires_at,
  'version',shown_version,'published_at',shown_at,'scanned_version',scanned_version,'latest_version',latest_version,
  'latest_published_at',coalesce(newest.published_at,link.updated_at));
end $$;
revoke all on function public.healthathon_emergency_view(text, text, boolean) from public;
grant execute on function public.healthathon_emergency_view(text, text, boolean) to anon, authenticated;

-- The owner's status gains the newest version number and when it was published.
create or replace function healthathon_private.emergency_link_status(p_person uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare live healthathon_private.emergency_links; newest healthathon_private.emergency_link_versions;
begin
 perform healthathon_private.emergency_link_owner(p_person);
 select * into live from healthathon_private.emergency_links where person_id=p_person and revoked_at is null;
 if not found then return jsonb_build_object('live',false); end if;
 select * into newest from healthathon_private.emergency_link_versions where link_id=live.id order by version desc limit 1;
 return jsonb_build_object('live',live.expires_at>now(),'created_at',live.created_at,'updated_at',live.updated_at,'expires_at',live.expires_at,'views',live.views,'last_viewed_at',live.last_viewed_at,
  'recent_views',healthathon_private.emergency_recent_views(live.id),'version',coalesce(newest.version,1),'published_at',coalesce(newest.published_at,live.updated_at));
end $$;

commit;
