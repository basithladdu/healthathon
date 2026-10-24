/** Offline PostgreSQL only. No real accounts, records, credentials or network requests. */
import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
const {PGlite}=await import(pathToFileURL(process.env.PGLITE_MODULE).href);
const db=new PGlite();let checks=0;
const owner='00000000-0000-0000-0000-000000000001',family='00000000-0000-0000-0000-000000000002',doctor='00000000-0000-0000-0000-000000000003',stranger='00000000-0000-0000-0000-000000000004';
const person='10000000-0000-0000-0000-000000000001',med='20000000-0000-0000-0000-000000000001';
await db.exec(`create role anon;create role authenticated;create schema auth;create schema healthathon_private;
create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
grant usage on schema auth,healthathon_private to authenticated,anon;
create table public.healthathon_people(id uuid primary key,owner_id uuid);
create table public.healthathon_records(id uuid primary key,person_id uuid references healthathon_people(id),kind text,data jsonb,revision bigint default 1);
create table public.members(person uuid,actor uuid,editable boolean,calendar boolean,reports boolean,expires timestamptz,approved boolean);
create function healthathon_private.can_read(p uuid) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.healthathon_people where id=p and owner_id=auth.uid()) or exists(select 1 from public.members where person=p and actor=auth.uid() and approved and expires>now())$$;
create function healthathon_private.can_edit(p uuid) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.healthathon_people where id=p and owner_id=auth.uid()) or exists(select 1 from public.members where person=p and actor=auth.uid() and approved and editable and expires>now())$$;
create function healthathon_private.is_owner(p uuid) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.healthathon_people where id=p and owner_id=auth.uid())$$;
create function healthathon_private.can_read_record(p uuid,k text,d jsonb) returns boolean language sql stable security definer set search_path='' as $$select healthathon_private.is_owner(p) or (healthathon_private.can_read(p) and case when d->>'detail' like 'sahara:entry:v1%' then (d->>'detail' like '%"type":"lab"%') else true end)$$;
create function healthathon_private.has_permission(p uuid,k text) returns boolean language sql stable security definer set search_path='' as $$select exists(select 1 from public.healthathon_people where id=p and owner_id=auth.uid()) or exists(select 1 from public.members where person=p and actor=auth.uid() and approved and expires>now() and case k when 'calendar' then calendar when 'reports' then reports else false end)$$;
insert into healthathon_people values('${person}','${owner}');
insert into healthathon_records values('${med}','${person}','entry','{"kind":"medicine","title":"Synthetic medicine","detail":"Original directions","doneOn":""}',1);
insert into members values('${person}','${family}',false,true,true,now()+interval '1 day',true),('${person}','${doctor}',true,true,true,now()+interval '1 day',true);`);
await db.exec(await readFile(new URL('../supabase/migrations/20261023053321_doctor_medicine_history.sql',import.meta.url),'utf8'));
async function as(actor,sql){await db.exec(`reset role;select set_config('request.jwt.claim.sub','${actor}',false);set role authenticated;`);return db.query(sql);}
async function denied(actor,sql){try{await as(actor,sql);assert.fail('Expected denial');}catch(e){assert.notEqual(e.code,'ERR_ASSERTION');checks++;}}
const report=(id,extra={})=>JSON.stringify({id,person_id:person,medicine_id:med,outcome:'taken',planned_at:null,reported_taken_at:null,reported_date:null,reporter_label:'Patient report',note:'Synthetic only',...extra}).replaceAll("'","''");
const id='30000000-0000-0000-0000-000000000001';
await as(owner,`select healthathon_report_medicine('${report(id)}'::jsonb)`);checks++;
let rows=(await as(owner,'select * from healthathon_medicine_events')).rows;assert.equal(rows.length,1);assert.equal(rows[0].reported_taken_at,null);assert.equal(rows[0].actor_id,owner);assert.ok(rows[0].logged_at);checks++;
await as(owner,`select healthathon_report_medicine('${report(id)}'::jsonb)`);assert.equal((await as(owner,'select * from healthathon_medicine_events')).rows.length,1);checks++;
await denied(owner,`select healthathon_report_medicine('${report(id,{note:'different'})}'::jsonb)`);
assert.equal((await as(family,'select * from healthathon_medicine_events')).rows.length,1);checks++;
await denied(family,`select healthathon_report_medicine('${report('30000000-0000-0000-0000-000000000002')}'::jsonb)`);
await denied(stranger,`select healthathon_report_medicine('${report('30000000-0000-0000-0000-000000000003')}'::jsonb)`);
assert.equal((await as(stranger,'select * from healthathon_medicine_events')).rows.length,0);checks++;
await as(doctor,`select healthathon_set_medicine_course('${person}','${med}',true,'Synthetic stopped course',0)`);checks++;
rows=(await as(owner,'select * from healthathon_medicine_courses')).rows;assert.equal(rows[0].past,true);assert.equal(rows[0].directions,'Original directions');checks++;
await as(owner,`select healthathon_set_medicine_course('${person}','${med}',false,'Synthetic restarted course',1)`);assert.equal((await as(owner,'select * from healthathon_medicine_events')).rows.length,3);checks++;
await denied(doctor,`select healthathon_set_medicine_course('${person}','${med}',true,'stale view',0)`);
await as(owner,`select healthathon_set_medicine_course('${person}','${med}',false,'Synthetic restarted course',1)`);assert.equal((await as(owner,'select * from healthathon_medicine_events')).rows.length,3);checks++;
await db.exec(`reset role;select set_config('request.jwt.claim.sub','${owner}',false);update healthathon_records set data=jsonb_set(data,'{doneOn}','"2026-10-23"'),revision=revision+1 where id='${med}';update healthathon_records set data=jsonb_set(data,'{doneOn}','""'),revision=revision+1 where id='${med}';`);
rows=(await as(owner,"select * from healthathon_medicine_events where source='daily-checkoff' order by logged_at,id")).rows;assert.equal(rows.length,2);assert.ok(rows.every(r=>r.reported_taken_at===null&&r.planned_at===null));assert.deepEqual(new Set(rows.map(r=>r.outcome)),new Set(['taken','unmarked']));checks++;
// The fixture's original-record gate models report access but not care-note access.
for(const type of ['conversation','malformed']) {
 await db.exec(`reset role;update healthathon_records set data=jsonb_set(data,'{detail}',to_jsonb('sahara:entry:v1${type==='conversation'?' {"type":"conversation"}':' not-json'}'::text)) where id='${med}'`);
 assert.equal((await as(family,'select * from healthathon_medicine_events')).rows.length,0);checks++;
 await denied(doctor,`select healthathon_set_medicine_course('${person}','${med}',true,'no original-record access',2)`);
}
await db.exec(`reset role;update healthathon_records set data=jsonb_set(data,'{detail}',to_jsonb('sahara:entry:v1 {"type":"lab"}'::text)) where id='${med}'`);
assert.equal((await as(family,'select * from healthathon_medicine_events')).rows.length,5);checks++;
await db.exec(`reset role;update healthathon_records set data=jsonb_set(data,'{detail}','"Original directions"') where id='${med}';insert into healthathon_records values('20000000-0000-0000-0000-000000000099','${person}','entry','{"title":"Not a medicine","doneOn":"2026-10-23"}',1)`);
assert.equal((await as(owner,'select * from healthathon_medicine_events')).rows.length,5);checks++;
await db.exec(`reset role;update members set expires=now()-interval '1 hour' where actor='${doctor}'`);
assert.equal((await as(doctor,'select * from healthathon_medicine_events')).rows.length,0);checks++;
await denied(doctor,`select healthathon_set_medicine_course('${person}','${med}',true,'expired',2)`);
await db.exec(`reset role;update members set reports=false where actor='${family}'`);assert.equal((await as(family,'select * from healthathon_medicine_events')).rows.length,0);checks++;
await db.exec(`reset role;delete from members where actor='${family}'`);assert.equal((await as(family,'select * from healthathon_medicine_events')).rows.length,0);checks++;
await denied(owner,`update healthathon_medicine_events set note='rewrite'`);
await denied(owner,`delete from healthathon_medicine_events`);
await db.exec('reset role;set role anon;');await assert.rejects(()=>db.query('select * from healthathon_medicine_events'));checks++;
await db.exec('reset role;');const grant=(await db.query("select has_function_privilege('anon','public.healthathon_report_medicine(jsonb)','execute') as allowed")).rows[0];assert.equal(grant.allowed,false);checks++;
await db.exec(`reset role;update members set expires=now()+interval '1 day' where actor='${doctor}';delete from healthathon_records where id='${med}'`);
assert.equal((await as(owner,'select * from healthathon_medicine_events')).rows.length,5);checks++;
assert.equal((await as(doctor,'select * from healthathon_medicine_events')).rows.length,0);checks++;
await denied(owner,`select healthathon_set_medicine_course('${person}','${med}',true,'deleted source',2)`);
console.log(`Medicine history offline SQL checks passed: ${checks}`);await db.close();
