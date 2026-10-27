-- OWNER APPLIES. Not applied by any agent. Project: Saanthvana (kjnhtakqzboayqorrlue), the one CareCloud.kt uses.
-- Emergency card web links (HANDOFF C1-C5, E16).
-- The family's app sends a copy of the emergency card it chose (name, latest patient-confirmed
-- goals-of-care note, contacts, medicines). The server returns a random link token once and keeps
-- only its SHA-256. Anyone holding the link can read that copy, read-only, until the owner turns it
-- off, replaces it, or it expires after a year. One live link per person: making a new link turns
-- the old one off at once (for a leaked card). The page at https://sahara.wedevit.in/emergency reads it.
begin;
create table healthathon_private.emergency_links (
 id uuid primary key default gen_random_uuid(),
 person_id uuid not null references public.healthathon_people(id) on delete cascade,
 token_hash text not null unique check(token_hash ~ '^[a-f0-9]{64}$'),
 card jsonb not null check(jsonb_typeof(card)='object' and card->>'kind'='sahara-emergency-card' and octet_length(card::text)<=32768),
 created_by uuid not null references auth.users(id) on delete cascade,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 expires_at timestamptz not null default now()+interval '365 days',
 revoked_at timestamptz,
 views integer not null default 0 check(views>=0),
 last_viewed_at timestamptz
);
create unique index emergency_links_one_live on healthathon_private.emergency_links(person_id) where revoked_at is null;
alter table healthathon_private.emergency_links enable row level security;
revoke all on healthathon_private.emergency_links from public,anon,authenticated;

create function healthathon_private.emergency_link_owner(p_person uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or not coalesce(healthathon_private.require_open_account(),false) or not coalesce(healthathon_private.is_owner(p_person),false) then
  raise exception 'Only the person who added this profile can manage its emergency link.' using errcode='42501';
 end if;
end $$;

create function healthathon_private.emergency_link_create(p_person uuid,p_card jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare token text:=encode(extensions.gen_random_bytes(32),'hex'); saved healthathon_private.emergency_links;
begin
 perform healthathon_private.emergency_link_owner(p_person);
 perform pg_catalog.pg_advisory_xact_lock(731044822,pg_catalog.hashtext(p_person::text));
 update healthathon_private.emergency_links set revoked_at=now() where person_id=p_person and revoked_at is null;
 insert into healthathon_private.emergency_links(person_id,token_hash,card,created_by)
 values(p_person,encode(extensions.digest(token,'sha256'),'hex'),p_card,auth.uid()) returning * into saved;
 return jsonb_build_object('token',token,'created_at',saved.created_at,'expires_at',saved.expires_at);
end $$;

create function healthathon_private.emergency_link_update(p_person uuid,p_card jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare saved healthathon_private.emergency_links;
begin
 perform healthathon_private.emergency_link_owner(p_person);
 update healthathon_private.emergency_links set card=p_card,updated_at=now()
 where person_id=p_person and revoked_at is null and expires_at>now() returning * into saved;
 if not found then raise exception 'This emergency link is off or expired. Make a new link.' using errcode='P0002'; end if;
 return jsonb_build_object('updated_at',saved.updated_at,'expires_at',saved.expires_at);
end $$;

create function healthathon_private.emergency_link_revoke(p_person uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
 perform healthathon_private.emergency_link_owner(p_person);
 update healthathon_private.emergency_links set revoked_at=now() where person_id=p_person and revoked_at is null;
end $$;

create function healthathon_private.emergency_link_status(p_person uuid) returns jsonb
language plpgsql security definer set search_path='' as $$
declare live healthathon_private.emergency_links;
begin
 perform healthathon_private.emergency_link_owner(p_person);
 select * into live from healthathon_private.emergency_links where person_id=p_person and revoked_at is null;
 if not found then return jsonb_build_object('live',false); end if;
 return jsonb_build_object('live',live.expires_at>now(),'created_at',live.created_at,'updated_at',live.updated_at,'expires_at',live.expires_at,'views',live.views,'last_viewed_at',live.last_viewed_at);
end $$;

revoke all on function healthathon_private.emergency_link_owner(uuid),healthathon_private.emergency_link_create(uuid,jsonb),healthathon_private.emergency_link_update(uuid,jsonb),healthathon_private.emergency_link_revoke(uuid),healthathon_private.emergency_link_status(uuid) from public,anon,authenticated;
grant execute on function healthathon_private.emergency_link_owner(uuid),healthathon_private.emergency_link_create(uuid,jsonb),healthathon_private.emergency_link_update(uuid,jsonb),healthathon_private.emergency_link_revoke(uuid),healthathon_private.emergency_link_status(uuid) to authenticated;
create function public.healthathon_emergency_link_create(p_person uuid,p_card jsonb) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.emergency_link_create(p_person,p_card)$$;
create function public.healthathon_emergency_link_update(p_person uuid,p_card jsonb) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.emergency_link_update(p_person,p_card)$$;
create function public.healthathon_emergency_link_revoke(p_person uuid) returns void language sql security invoker set search_path='' as $$select healthathon_private.emergency_link_revoke(p_person)$$;
create function public.healthathon_emergency_link_status(p_person uuid) returns jsonb language sql security invoker set search_path='' as $$select healthathon_private.emergency_link_status(p_person)$$;
revoke all on function public.healthathon_emergency_link_create(uuid,jsonb),public.healthathon_emergency_link_update(uuid,jsonb),public.healthathon_emergency_link_revoke(uuid),public.healthathon_emergency_link_status(uuid) from public,anon;
grant execute on function public.healthathon_emergency_link_create(uuid,jsonb),public.healthathon_emergency_link_update(uuid,jsonb),public.healthathon_emergency_link_revoke(uuid),public.healthathon_emergency_link_status(uuid) to authenticated;

-- The only anonymous entry point. It returns nothing without the exact 64-hex token, never says
-- whose link a wrong token might be, and counts opens so the family can see if a card was used.
create function public.healthathon_emergency_view(p_token text) returns jsonb
language plpgsql volatile security definer set search_path='' as $$
declare link healthathon_private.emergency_links;
begin
 if p_token is null or p_token !~ '^[a-f0-9]{64}$' then return jsonb_build_object('status','unknown'); end if;
 select * into link from healthathon_private.emergency_links where token_hash=encode(extensions.digest(p_token,'sha256'),'hex');
 if not found then return jsonb_build_object('status','unknown'); end if;
 if link.revoked_at is not null then return jsonb_build_object('status','off'); end if;
 if link.expires_at<=now() then return jsonb_build_object('status','expired'); end if;
 update healthathon_private.emergency_links set views=views+1,last_viewed_at=now() where id=link.id;
 return jsonb_build_object('status','live','card',link.card,'updated_at',link.updated_at,'expires_at',link.expires_at);
end $$;
revoke all on function public.healthathon_emergency_view(text) from public;
grant execute on function public.healthathon_emergency_view(text) to anon,authenticated;
commit;
