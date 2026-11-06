// Isolated PostgreSQL WASM fixtures for 20261106120000_community.sql. Never contacts a Supabase project.
// Run from the repository root: PGLITE_MODULE=/path/to/@electric-sql/pglite/dist/index.js node android/scripts/test-community-sql.mjs
import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
const { PGlite } = await import(pathToFileURL(process.env.PGLITE_MODULE).href);
const db = new PGlite();
// Same shims as test-care-team-sql.mjs: Supabase's auth, extensions and storage modelled minimally.
await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
create schema auth; create schema extensions; create schema storage;
create table auth.users(id uuid primary key,email text);
create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
create function auth.jwt() returns jsonb language sql stable as $$select '{}'::jsonb$$;
grant usage on schema auth,extensions,storage to anon,authenticated;
create function extensions.digest(text,text) returns bytea language sql immutable as $$select sha256(convert_to($1,'UTF8'))$$;
create function extensions.gen_random_bytes(integer) returns bytea language sql volatile as $$select substring(decode(repeat(replace(gen_random_uuid()::text,'-',''),8),'hex') from 1 for $1)$$;
create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text,name text,metadata jsonb,unique(bucket_id,name));
alter table storage.objects enable row level security; grant select,insert,update,delete on storage.objects to anon,authenticated;`);
for (const file of (await readdir('android/supabase/migrations')).filter((f) => f.endsWith('.sql')).sort()) await db.exec(await readFile('android/supabase/migrations/' + file, 'utf8'));
async function asCareUser(database, user, fn) {
  await database.exec(`set role authenticated; select set_config('request.jwt.claim.sub','${user}',false);`);
  try { return await fn(); } finally { await database.exec('reset role;'); }
}
const u = { a: '11111111-1111-4111-8111-111111111111', b: '22222222-2222-4222-8222-222222222222', c: '33333333-3333-4333-8333-333333333333', d: '44444444-4444-4444-8444-444444444444', team: '55555555-5555-4555-8555-555555555555' };
for (const [name, id] of Object.entries(u)) await db.query('insert into auth.users(id,email) values($1,$2)', [id, `${name}@example.invalid`]);
await db.query("insert into healthathon_private.staff_roles(user_id,role) values($1,'clinical_verifier')", [u.team]);
let checks = 0;
const ok = (value, msg) => { assert.ok(value, msg); checks++; };
const deny = async (fn, pattern, msg) => { await assert.rejects(fn, (e) => pattern.test(e.message), msg); checks++; };
const one = async (sql, args = []) => Object.values((await db.query(sql, args)).rows[0])[0];
const as = (id, fn) => asCareUser(db, id, fn);
const id = (n) => `aaaaaaaa-aaaa-4aaa-8aaa-${String(n).padStart(12, '0')}`;
const feed = (user, group = 'family-carers') => as(user, () => one('select public.sahara_community_feed($1)', [group]));

// Nothing for anon, no direct table access for members.
await db.exec('set role anon');
await assert.rejects(() => db.query('select public.sahara_community_home()'), undefined, 'anon cannot read'); checks++;
await db.exec('reset role');
await as(u.a, async () => {
  await deny(() => db.query('select * from public.community_posts'), /permission denied/, 'no direct select');
  await deny(() => db.query("insert into public.community_groups(id,name_en,name_hi) values('x1','Xx','Xx')"), /permission denied/, 'no direct group insert');
});
const home = await as(u.a, () => one('select public.sahara_community_home()'));
ok(home.groups.length === 4 && home.groups[0].id === 'family-carers' && home.my_name === null && home.can_moderate === false, 'seeded groups, no name yet');

// A name is needed, and contact details or "Sahara" are not allowed in it.
await as(u.a, () => deny(() => db.query("select public.sahara_community_post($1,'family-carers','Hello everyone')", [id(1)]), /Choose a community name first/, 'name first'));
for (const bad of ['a', 'me@example.com', '98765 43210', 'Sahara team']) await as(u.a, () => deny(() => db.query('select public.sahara_community_set_name($1)', [bad]), /Name not allowed/, `name ${bad} rejected`));
await as(u.a, () => db.query("select public.sahara_community_set_name('Asha''s daughter')"));
await as(u.b, () => db.query("select public.sahara_community_set_name('Ravi')"));
await as(u.c, () => db.query("select public.sahara_community_set_name('Meena')"));
await as(u.d, () => db.query("select public.sahara_community_set_name('Kiran')"));
await as(u.team, () => db.query("select public.sahara_community_set_name('Nurse Priya')"));

// Posting: length, contact details, idempotent retry, unknown group.
await as(u.a, async () => {
  await deny(() => db.query("select public.sahara_community_post($1,'family-carers',' a ')", [id(2)]), /Post length not allowed/, 'too short');
  await deny(() => db.query('select public.sahara_community_post($1,$2,$3)', [id(2), 'family-carers', 'x'.repeat(1001)]), /Post length not allowed/, 'too long');
  await deny(() => db.query("select public.sahara_community_post($1,'family-carers','Call me on 98765-43210')", [id(2)]), /Remove contact details/, 'phone blocked');
  await deny(() => db.query("select public.sahara_community_post($1,'family-carers','See www.example.com')", [id(2)]), /Remove contact details/, 'link blocked');
  await deny(() => db.query("select public.sahara_community_post($1,'nope','Hello there')", [id(2)]), /Group not found/, 'unknown group');
  await db.query("select public.sahara_community_post($1,'family-carers','Scan on 2026-11-06 went fine. How do you rest?')", [id(1)]);
  await db.query("select public.sahara_community_post($1,'family-carers','Scan on 2026-11-06 went fine. How do you rest?')", [id(1)]);
});
ok(await one('select count(*)::int from public.community_posts') === 1, 'retry with the same id posts once');
await as(u.b, () => deny(() => db.query("select public.sahara_community_post($1,'family-carers','Taking over')", [id(1)]), /no longer available/, 'another member cannot reuse an id'));

// Feed: names, never ids or emails; mine flag; team flag on staff replies.
await as(u.team, () => db.query("select public.sahara_community_reply($1,$2,'Short walks and a neighbour sitting in help many carers.')", [id(50), id(1)]));
await as(u.b, () => db.query("select public.sahara_community_reply($1,$2,'Same here. One hour off helps.')", [id(51), id(1)]));
let f = await feed(u.b);
const post = f.posts[0];
ok(post.name === "Asha's daughter" && post.mine === false && !JSON.stringify(f).includes('example.invalid') && !JSON.stringify(f).includes(u.a), 'feed shows names only');
ok(post.replies.length === 2 && post.replies[0].by_team === true && post.replies[1].mine === true, 'replies inline, team badge from staff role');
ok((await feed(u.a)).posts[0].mine === true, 'author sees mine');

// Reactions toggle, one per member and kind.
await as(u.b, async () => { await db.query("select public.sahara_community_react($1,'hugs',true)", [id(1)]); await db.query("select public.sahara_community_react($1,'hugs',true)", [id(1)]); });
await as(u.c, () => db.query("select public.sahara_community_react($1,'helpful',true)", [id(1)]));
f = await feed(u.b); ok(f.posts[0].hugs === 1 && f.posts[0].my_hugs === true && f.posts[0].helpful === 1 && f.posts[0].my_helpful === false, 'reaction counts');
await as(u.b, () => db.query("select public.sahara_community_react($1,'hugs',false)", [id(1)]));
ok((await feed(u.b)).posts[0].hugs === 0, 'reaction removed');

// Reports: once per member; three members hide the post; team sees counts and can restore.
await as(u.b, async () => { await db.query("select public.sahara_community_report($1,null,'unkind')", [id(1)]); await db.query("select public.sahara_community_report($1,null,'unkind')", [id(1)]); });
ok(await one('select count(*)::int from public.community_reports') === 1, 'one report per member');
ok((await feed(u.b)).posts[0].reported === true && (await feed(u.b)).posts[0].reports === 0, 'member sees own report, not counts');
ok((await feed(u.team)).posts[0].reports === 1 && (await feed(u.team)).can_moderate === true, 'team sees report count');
await as(u.c, () => db.query("select public.sahara_community_report($1,null,'spam')", [id(1)]));
await as(u.d, () => db.query("select public.sahara_community_report($1,null,'private')", [id(1)]));
ok((await feed(u.b)).posts.length === 0, 'three reports hide the post');
ok((await feed(u.a)).posts[0].hidden === true, 'author still sees it, marked hidden');
await as(u.b, () => deny(() => db.query("select public.sahara_community_reply($1,$2,'hi')", [id(52), id(1)]), /no longer available/, 'no replies to hidden posts'));
await as(u.a, () => deny(() => db.query("select public.sahara_community_moderate('post',$1,false)", [id(1)]), /Only the Sahara team/, 'member cannot moderate'));
await as(u.team, () => db.query("select public.sahara_community_moderate('post',$1,false)", [id(1)]));
ok((await feed(u.b)).posts.length === 1 && await one('select count(*)::int from public.community_reports where resolved_at is null') === 0, 'team restores and resolves');
await as(u.team, () => db.query("select public.sahara_community_moderate('reply',$1,true)", [id(51)]));
ok((await feed(u.c)).posts[0].replies.length === 1 && (await feed(u.b)).posts[0].replies.length === 2, 'hidden reply: gone for others, kept for its author');

// Rate limits.
await as(u.c, async () => {
  for (let n = 0; n < 10; n++) await db.query("select public.sahara_community_post($1,'grief-loss','Thinking of everyone today.')", [id(100 + n)]);
  await deny(() => db.query("select public.sahara_community_post($1,'grief-loss','One more')", [id(120)]), /Daily post limit reached/, '10 posts a day');
  for (let n = 0; n < 30; n++) await db.query("select public.sahara_community_reply($1,$2,'Yes.')", [id(200 + n), id(100)]);
  await deny(() => db.query("select public.sahara_community_reply($1,$2,'Again')", [id(240), id(100)]), /Daily reply limit reached/, '30 replies a day');
});

// Remove own content only.
await as(u.b, () => deny(() => db.query("select public.sahara_community_remove_mine('post',$1)", [id(1)]), /no longer available/, 'cannot remove others'));
await as(u.a, () => db.query("select public.sahara_community_remove_mine('post',$1)", [id(1)]));
ok(await one('select count(*)::int from public.community_replies where post_id=$1', [id(1)]) === 0, 'own post removed with its replies');

// Closed accounts: cannot use it, and their posts leave the feed.
await db.query('insert into healthathon_private.closed_accounts(user_id) values($1)', [u.c]);
await as(u.c, () => deny(() => db.query('select public.sahara_community_home()'), /account is closed/, 'closed account blocked'));
ok((await feed(u.b, 'grief-loss')).posts.length === 0, 'closed account posts hidden');

console.log(`Community SQL: ${checks} checks PASS (deny-by-default, names only, idempotent posts, reactions, reports and auto-hide, team moderation, rate limits, removal, closed accounts)`);
