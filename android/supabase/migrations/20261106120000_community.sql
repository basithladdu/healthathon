-- OWNER APPLIES (Awaiz). Not applied by the app lane (6 Nov 2026).
-- Sahara community: groups, posts, inline replies, gentle reactions, reports and moderation.
-- * Signed-in members with an open Sahara account only. Nothing is readable by anon; no table is granted to
--   authenticated: every read and write goes through the functions below, which check the caller themselves.
-- * Authors are shown by the community name they choose (sahara_community_set_name), never by email or phone.
--   Feeds return "mine" / "by_team" flags, never another member's user id.
-- * Moderation reuses the existing staff role 'clinical_verifier' (or 'admin') from 20261019151322.
-- * Limits: 10 posts, 30 replies and 20 reports per member per 24 hours; post 2-1000 characters, reply 1-500;
--   no links, email addresses or phone numbers in posts, replies or names; no images in v1.
-- * Three different members reporting the same post or reply hides it until the team looks at it.
-- * The group list is data: clinicians confirm it, then edit rows of public.community_groups in the dashboard.
-- Until this is applied the app says the community is not open yet; nothing is sent.
begin;

create table public.community_groups (
  id text primary key check (id ~ '^[a-z0-9-]{2,40}$'),
  name_en text not null check (length(btrim(name_en)) between 2 and 60),
  name_hi text not null check (length(btrim(name_hi)) between 2 and 60),
  about_en text not null default '' check (length(about_en) <= 140),
  about_hi text not null default '' check (length(about_hi) <= 140),
  tone text not null default 'teal' check (tone in ('teal','rose','plum','sky','leaf','marigold','gold','rust')),
  sort integer not null default 100,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create table public.community_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null check (length(btrim(display_name)) between 2 and 40),
  updated_at timestamptz not null default now()
);

create table public.community_posts (
  id uuid primary key,
  group_id text not null references public.community_groups(id) on update cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (length(body) between 2 and 1000),
  created_at timestamptz not null default now(),
  hidden_at timestamptz,
  hidden_by uuid references auth.users(id) on delete set null,
  hidden_reason text check (hidden_reason in ('team','reports'))
);
create index community_posts_feed on public.community_posts(group_id, created_at desc);
create index community_posts_author_time on public.community_posts(author_id, created_at);

create table public.community_replies (
  id uuid primary key,
  post_id uuid not null references public.community_posts(id) on delete cascade,
  author_id uuid not null references auth.users(id) on delete cascade,
  body text not null check (length(body) between 1 and 500),
  created_at timestamptz not null default now(),
  hidden_at timestamptz,
  hidden_by uuid references auth.users(id) on delete set null,
  hidden_reason text check (hidden_reason in ('team','reports'))
);
create index community_replies_post on public.community_replies(post_id, created_at);
create index community_replies_author_time on public.community_replies(author_id, created_at);

create table public.community_reactions (
  post_id uuid not null references public.community_posts(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  kind text not null check (kind in ('helpful','hugs')),
  created_at timestamptz not null default now(),
  primary key (post_id, user_id, kind)
);

create table public.community_reports (
  id uuid primary key default gen_random_uuid(),
  post_id uuid references public.community_posts(id) on delete cascade,
  reply_id uuid references public.community_replies(id) on delete cascade,
  reporter_id uuid not null references auth.users(id) on delete cascade,
  reason text not null check (reason in ('unkind','harmful-advice','spam','private','other')),
  note text not null default '' check (length(note) <= 200),
  created_at timestamptz not null default now(),
  resolved_at timestamptz,
  resolved_by uuid references auth.users(id) on delete set null,
  check ((post_id is null) <> (reply_id is null))
);
create unique index community_reports_one_post on public.community_reports(reporter_id, post_id) where post_id is not null;
create unique index community_reports_one_reply on public.community_reports(reporter_id, reply_id) where reply_id is not null;
create index community_reports_open on public.community_reports(created_at) where resolved_at is null;

-- Deny by default: RLS on, no policies, no grants. Only the functions below (security definer) touch the rows.
alter table public.community_groups enable row level security;
alter table public.community_profiles enable row level security;
alter table public.community_posts enable row level security;
alter table public.community_replies enable row level security;
alter table public.community_reactions enable row level security;
alter table public.community_reports enable row level security;
revoke all on public.community_groups, public.community_profiles, public.community_posts, public.community_replies,
  public.community_reactions, public.community_reports from public, anon, authenticated;
grant select on public.community_groups, public.community_posts, public.community_replies, public.community_reports to service_role;

-- Neutral starting groups. Clinicians confirm or change this list (blockers.md).
insert into public.community_groups(id, name_en, name_hi, about_en, about_hi, tone, sort) values
 ('family-carers', 'Family carers', 'परिवार के देखभाल करने वाले', 'For family looking after someone at home', 'घर पर किसी की देखभाल करने वाले परिवार के लिए', 'teal', 10),
 ('living-with-cancer', 'Living with cancer', 'कैंसर के साथ जीना', 'For patients, during and after treatment', 'मरीज़ों के लिए, इलाज के दौरान और बाद में', 'rose', 20),
 ('grief-loss', 'Grief and loss', 'दुख और बिछड़ना', 'For families after losing someone close', 'किसी करीबी को खोने के बाद परिवारों के लिए', 'plum', 30),
 ('ask-care-team', 'Ask the care team', 'देखभाल टीम से पूछें', 'General questions. The Sahara team replies', 'आम सवाल। Sahara टीम जवाब देती है', 'sky', 40);

-- Helpers (private schema, no client grants).
create function healthathon_private.community_caller() returns uuid
language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid();
begin
  if caller is null or not coalesce(healthathon_private.require_open_account(), false) then
    raise exception 'Sign in to use the community.' using errcode = '42501';
  end if;
  return caller;
end; $$;

create function healthathon_private.community_is_team(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists(select 1 from healthathon_private.staff_roles where user_id = p_user and revoked_at is null and role in ('clinical_verifier','admin'))
$$;

-- Same rule as CommunityRules.contactOrLink on the phone: links, email addresses, or 10+ digits in a row (spaces or dashes allowed).
create function healthathon_private.community_has_contact(p_text text) returns boolean
language sql immutable set search_path = '' as $$
  select p_text ~* '(https?://|www\.)' or p_text ~ '[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+' or p_text ~ '[0-9]([ -]?[0-9]){9,}'
$$;

create function healthathon_private.community_visible(p_hidden timestamptz, p_author uuid, p_caller uuid, p_team boolean) returns boolean
language sql stable security definer set search_path = '' as $$
  select (p_hidden is null or p_author = p_caller or p_team)
    and not exists(select 1 from healthathon_private.closed_accounts where user_id = p_author)
$$;

revoke all on function healthathon_private.community_caller(), healthathon_private.community_is_team(uuid),
  healthathon_private.community_has_contact(text), healthathon_private.community_visible(timestamptz, uuid, uuid, boolean) from public, anon, authenticated;

-- Groups with their visible post counts, plus the caller's community name and whether they moderate.
create function public.sahara_community_home() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); team boolean := healthathon_private.community_is_team(caller);
begin
  return jsonb_build_object(
    'my_name', (select display_name from public.community_profiles where user_id = caller),
    'can_moderate', team,
    'groups', coalesce((select jsonb_agg(jsonb_build_object('id', g.id, 'name_en', g.name_en, 'name_hi', g.name_hi, 'about_en', g.about_en, 'about_hi', g.about_hi, 'tone', g.tone,
        'posts', (select count(*) from public.community_posts p where p.group_id = g.id and p.hidden_at is null)) order by g.sort, g.id)
      from public.community_groups g where g.active), '[]'::jsonb));
end; $$;

create function public.sahara_community_set_name(p_name text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); clean text := btrim(coalesce(p_name, ''));
begin
  if length(clean) not between 2 and 40 or clean ~ '@' or healthathon_private.community_has_contact(clean) or clean ~* 'sahara' then
    raise exception 'Name not allowed.' using errcode = 'P0001';
  end if;
  insert into public.community_profiles(user_id, display_name) values (caller, clean)
  on conflict (user_id) do update set display_name = excluded.display_name, updated_at = now();
  return jsonb_build_object('my_name', clean);
end; $$;

-- One page of a group's feed, newest first, each post with its replies (oldest first).
create function public.sahara_community_feed(p_group text, p_before_ms bigint default null, p_limit integer default 30) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); team boolean := healthathon_private.community_is_team(caller);
begin
  if not exists(select 1 from public.community_groups where id = p_group and active) then raise exception 'Group not found.' using errcode = 'P0001'; end if;
  return jsonb_build_object('can_moderate', team, 'my_name', (select display_name from public.community_profiles where user_id = caller), 'posts', coalesce((
    select jsonb_agg(item order by created desc) from (
      select p.created_at as created, jsonb_build_object(
        'id', p.id, 'group_id', p.group_id, 'body', p.body, 'created_ms', floor(extract(epoch from p.created_at) * 1000)::bigint,
        'name', coalesce((select display_name from public.community_profiles where user_id = p.author_id), 'Member'),
        'mine', p.author_id = caller, 'by_team', healthathon_private.community_is_team(p.author_id), 'hidden', p.hidden_at is not null,
        'helpful', (select count(*) from public.community_reactions r where r.post_id = p.id and r.kind = 'helpful'),
        'hugs', (select count(*) from public.community_reactions r where r.post_id = p.id and r.kind = 'hugs'),
        'my_helpful', exists(select 1 from public.community_reactions r where r.post_id = p.id and r.user_id = caller and r.kind = 'helpful'),
        'my_hugs', exists(select 1 from public.community_reactions r where r.post_id = p.id and r.user_id = caller and r.kind = 'hugs'),
        'reported', exists(select 1 from public.community_reports x where x.post_id = p.id and x.reporter_id = caller),
        'reports', case when team then (select count(*) from public.community_reports x where x.post_id = p.id and x.resolved_at is null) else 0 end,
        'replies', coalesce((select jsonb_agg(jsonb_build_object(
            'id', c.id, 'body', c.body, 'created_ms', floor(extract(epoch from c.created_at) * 1000)::bigint,
            'name', coalesce((select display_name from public.community_profiles where user_id = c.author_id), 'Member'),
            'mine', c.author_id = caller, 'by_team', healthathon_private.community_is_team(c.author_id), 'hidden', c.hidden_at is not null,
            'reported', exists(select 1 from public.community_reports x where x.reply_id = c.id and x.reporter_id = caller),
            'reports', case when team then (select count(*) from public.community_reports x where x.reply_id = c.id and x.resolved_at is null) else 0 end
          ) order by c.created_at, c.id) from public.community_replies c
          where c.post_id = p.id and healthathon_private.community_visible(c.hidden_at, c.author_id, caller, team)), '[]'::jsonb)
      ) as item
      from public.community_posts p
      where p.group_id = p_group and healthathon_private.community_visible(p.hidden_at, p.author_id, caller, team)
        and (p_before_ms is null or p.created_at < to_timestamp(p_before_ms / 1000.0))
      order by p.created_at desc, p.id
      limit least(greatest(coalesce(p_limit, 30), 1), 50)
    ) page), '[]'::jsonb));
end; $$;

-- Idempotent on p_id, so a retry from the phone's outbox never posts twice.
create function public.sahara_community_post(p_id uuid, p_group text, p_body text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); clean text := btrim(coalesce(p_body, '')); existing public.community_posts%rowtype;
begin
  if p_id is null then raise exception 'Post length not allowed.' using errcode = 'P0001'; end if;
  select * into existing from public.community_posts where id = p_id;
  if found then
    if existing.author_id <> caller then raise exception 'This post is no longer available.' using errcode = 'P0001'; end if;
    return jsonb_build_object('id', p_id, 'status', 'received');
  end if;
  if not exists(select 1 from public.community_profiles where user_id = caller) then raise exception 'Choose a community name first.' using errcode = 'P0001'; end if;
  if not exists(select 1 from public.community_groups where id = p_group and active) then raise exception 'Group not found.' using errcode = 'P0001'; end if;
  if length(clean) not between 2 and 1000 then raise exception 'Post length not allowed.' using errcode = 'P0001'; end if;
  if healthathon_private.community_has_contact(clean) then raise exception 'Remove contact details and links.' using errcode = 'P0001'; end if;
  perform pg_catalog.pg_advisory_xact_lock(731044822, pg_catalog.hashtext(caller::text));
  if (select count(*) from public.community_posts where author_id = caller and created_at >= now() - interval '24 hours') >= 10 then
    raise exception 'Daily post limit reached.' using errcode = 'P0001';
  end if;
  insert into public.community_posts(id, group_id, author_id, body) values (p_id, p_group, caller, clean);
  return jsonb_build_object('id', p_id, 'status', 'received');
end; $$;

create function public.sahara_community_reply(p_id uuid, p_post uuid, p_body text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); team boolean := healthathon_private.community_is_team(caller);
  clean text := btrim(coalesce(p_body, '')); existing public.community_replies%rowtype; parent public.community_posts%rowtype;
begin
  if p_id is null then raise exception 'Reply length not allowed.' using errcode = 'P0001'; end if;
  select * into existing from public.community_replies where id = p_id;
  if found then
    if existing.author_id <> caller then raise exception 'This post is no longer available.' using errcode = 'P0001'; end if;
    return jsonb_build_object('id', p_id, 'status', 'received');
  end if;
  if not exists(select 1 from public.community_profiles where user_id = caller) then raise exception 'Choose a community name first.' using errcode = 'P0001'; end if;
  select * into parent from public.community_posts where id = p_post;
  if not found or not healthathon_private.community_visible(parent.hidden_at, parent.author_id, caller, team) or parent.hidden_at is not null then
    raise exception 'This post is no longer available.' using errcode = 'P0001';
  end if;
  if length(clean) not between 1 and 500 then raise exception 'Reply length not allowed.' using errcode = 'P0001'; end if;
  if healthathon_private.community_has_contact(clean) then raise exception 'Remove contact details and links.' using errcode = 'P0001'; end if;
  perform pg_catalog.pg_advisory_xact_lock(731044822, pg_catalog.hashtext(caller::text));
  if (select count(*) from public.community_replies where author_id = caller and created_at >= now() - interval '24 hours') >= 30 then
    raise exception 'Daily reply limit reached.' using errcode = 'P0001';
  end if;
  insert into public.community_replies(id, post_id, author_id, body) values (p_id, p_post, caller, clean);
  return jsonb_build_object('id', p_id, 'status', 'received');
end; $$;

create function public.sahara_community_react(p_post uuid, p_kind text, p_on boolean) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); parent public.community_posts%rowtype;
begin
  if p_kind not in ('helpful','hugs') then raise exception 'Group not found.' using errcode = 'P0001'; end if;
  select * into parent from public.community_posts where id = p_post;
  if not found or parent.hidden_at is not null or not healthathon_private.community_visible(parent.hidden_at, parent.author_id, caller, false) then
    raise exception 'This post is no longer available.' using errcode = 'P0001';
  end if;
  if coalesce(p_on, false) then
    insert into public.community_reactions(post_id, user_id, kind) values (p_post, caller, p_kind) on conflict do nothing;
  else
    delete from public.community_reactions where post_id = p_post and user_id = caller and kind = p_kind;
  end if;
  return jsonb_build_object('count', (select count(*) from public.community_reactions where post_id = p_post and kind = p_kind),
    'mine', exists(select 1 from public.community_reactions where post_id = p_post and user_id = caller and kind = p_kind));
end; $$;

-- Any member can report a post or a reply once. Three open reports from different members hide it for the team to check.
create function public.sahara_community_report(p_post uuid, p_reply uuid, p_reason text, p_note text default '') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); open_reports integer;
begin
  if (p_post is null) = (p_reply is null) or p_reason not in ('unkind','harmful-advice','spam','private','other') or length(coalesce(p_note, '')) > 200 then
    raise exception 'This post is no longer available.' using errcode = 'P0001';
  end if;
  if (p_post is not null and not exists(select 1 from public.community_posts where id = p_post))
     or (p_reply is not null and not exists(select 1 from public.community_replies where id = p_reply)) then
    raise exception 'This post is no longer available.' using errcode = 'P0001';
  end if;
  if exists(select 1 from public.community_reports where reporter_id = caller and (post_id = p_post or reply_id = p_reply)) then
    return jsonb_build_object('status', 'received');
  end if;
  perform pg_catalog.pg_advisory_xact_lock(731044822, pg_catalog.hashtext(caller::text));
  if (select count(*) from public.community_reports where reporter_id = caller and created_at >= now() - interval '24 hours') >= 20 then
    raise exception 'Daily report limit reached.' using errcode = 'P0001';
  end if;
  insert into public.community_reports(post_id, reply_id, reporter_id, reason, note) values (p_post, p_reply, caller, p_reason, btrim(coalesce(p_note, '')));
  select count(distinct reporter_id) into open_reports from public.community_reports
   where resolved_at is null and (post_id = p_post or reply_id = p_reply);
  if open_reports >= 3 then
    if p_post is not null then update public.community_posts set hidden_at = now(), hidden_reason = 'reports' where id = p_post and hidden_at is null;
    else update public.community_replies set hidden_at = now(), hidden_reason = 'reports' where id = p_reply and hidden_at is null; end if;
  end if;
  return jsonb_build_object('status', 'received');
end; $$;

-- The author removes their own post (with its replies) or reply. This deletes the row.
create function public.sahara_community_remove_mine(p_kind text, p_id uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); removed integer;
begin
  if p_kind = 'post' then delete from public.community_posts where id = p_id and author_id = caller;
  elsif p_kind = 'reply' then delete from public.community_replies where id = p_id and author_id = caller;
  else removed := 0; end if;
  get diagnostics removed = row_count;
  if removed <> 1 then raise exception 'This post is no longer available.' using errcode = 'P0001'; end if;
  return jsonb_build_object('status', 'removed');
end; $$;

-- Sahara team (clinical_verifier or admin) hides or restores a post or reply; its open reports are resolved.
create function public.sahara_community_moderate(p_kind text, p_id uuid, p_hide boolean) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare caller uuid := healthathon_private.community_caller(); changed integer;
begin
  if not healthathon_private.community_is_team(caller) then raise exception 'Only the Sahara team can do this.' using errcode = 'P0001'; end if;
  if p_kind = 'post' then
    update public.community_posts set hidden_at = case when p_hide then now() end, hidden_by = case when p_hide then caller end, hidden_reason = case when p_hide then 'team' end where id = p_id;
    get diagnostics changed = row_count;
    update public.community_reports set resolved_at = now(), resolved_by = caller where post_id = p_id and resolved_at is null;
  elsif p_kind = 'reply' then
    update public.community_replies set hidden_at = case when p_hide then now() end, hidden_by = case when p_hide then caller end, hidden_reason = case when p_hide then 'team' end where id = p_id;
    get diagnostics changed = row_count;
    update public.community_reports set resolved_at = now(), resolved_by = caller where reply_id = p_id and resolved_at is null;
  else changed := 0; end if;
  if changed <> 1 then raise exception 'This post is no longer available.' using errcode = 'P0001'; end if;
  return jsonb_build_object('status', case when p_hide then 'hidden' else 'shown' end);
end; $$;

revoke all on function public.sahara_community_home(), public.sahara_community_set_name(text), public.sahara_community_feed(text, bigint, integer),
  public.sahara_community_post(uuid, text, text), public.sahara_community_reply(uuid, uuid, text), public.sahara_community_react(uuid, text, boolean),
  public.sahara_community_report(uuid, uuid, text, text), public.sahara_community_remove_mine(text, uuid), public.sahara_community_moderate(text, uuid, boolean) from public, anon;
grant execute on function public.sahara_community_home(), public.sahara_community_set_name(text), public.sahara_community_feed(text, bigint, integer),
  public.sahara_community_post(uuid, text, text), public.sahara_community_reply(uuid, uuid, text), public.sahara_community_react(uuid, text, boolean),
  public.sahara_community_report(uuid, uuid, text, text), public.sahara_community_remove_mine(text, uuid), public.sahara_community_moderate(text, uuid, boolean) to authenticated;

commit;
