/** Generated bounded rollback harness; original tracked test is unchanged. */
/** Offline PGlite: actual baseline access-function bodies, minimal synthetic Auth/table fixtures. */
import {readFile} from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
const {PGlite}=await import(pathToFileURL(process.env.PGLITE_MODULE).href);
const db=new PGlite();let checks=0;await db.exec("begin");
const path=name=>new URL(`file:///F:/CODE/healthathon/android/supabase/migrations/${name}`);
const review=await readFile(path('20261019151322_shared_care_clinician_review.sql'),'utf8');
const closure=await readFile(path('20261010122352_sahara_account_closure.sql'),'utf8');
function actual(sql,name){const found=sql.match(new RegExp(`create(?: or replace)? function healthathon_private\\.${name}\\([\\s\\S]*?\\$\\$;`,'i'));assert.ok(found,`actual ${name}`);return found[0];}
const id=n=>`00000000-0000-0000-0000-${String(n).padStart(12,'0')}`;
const owner=id(1),family=id(2),doctor=id(3),stranger=id(4),calendar=id(5),person=id(100);
await db.exec(`create role anon;create role authenticated;create role service_role;create schema auth;create schema healthathon_private;
alter default privileges in schema public grant all on tables to service_role;
alter default privileges in schema public grant execute on functions to service_role;
alter default privileges in schema healthathon_private grant execute on functions to service_role;
create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
grant usage on schema auth,healthathon_private to authenticated,anon;
create table auth.users(id uuid primary key);
create table healthathon_private.closed_accounts(user_id uuid primary key);
create table public.healthathon_people(id uuid primary key,owner_id uuid,name text);
create table public.healthathon_members(person_id uuid,user_id uuid,can_edit boolean,role text,permissions jsonb,expires_at timestamptz);
create table public.healthathon_clinician_requests(user_id uuid,status text);
create table public.healthathon_records(id uuid,kind text,person_id uuid,data jsonb,revision bigint not null default 1,updated_at timestamptz default now(),primary key(kind,id));
insert into auth.users values('${owner}'),('${family}'),('${doctor}'),('${stranger}'),('${calendar}');
insert into healthathon_people values('${person}','${owner}','Fictional SQL fixture');
insert into healthathon_members values('${person}','${family}',false,'family','{"careNote":true,"calendar":true,"reports":true}',now()+interval '1 day'),('${person}','${doctor}',true,'clinician','{"calendar":true,"reports":true}',now()+interval '1 day'),('${person}','${calendar}',true,'family','{"calendar":true}',now()+interval '1 day');
insert into healthathon_clinician_requests values('${doctor}','approved');`);
for(const [source,name] of [[closure,'require_open_account'],[closure,'is_owner'],[review,'can_read'],[review,'can_edit'],[review,'has_permission'],[review,'can_read_record']])await db.exec(actual(source,name));
await db.exec((await readFile(path('20261023053321_doctor_medicine_history.sql'),'utf8')).replace(/^begin;\s*/,'').replace(/commit;\s*$/,''));
await db.exec((await readFile(path('20261023071214_doctor_prescription_groups.sql'),'utf8')).replace(/^begin;\s*/,'').replace(/commit;\s*$/,''));
async function as(actor,sql){await db.exec(`reset role;select set_config('request.jwt.claim.sub','${actor}',false);set role authenticated;`);return db.query(sql);}
async function denied(actor,sql){await db.exec('savepoint expected_denial');try{await assert.rejects(()=>as(actor,sql));checks++;}finally{await db.exec('rollback to savepoint expected_denial;release savepoint expected_denial')}}
const quote=value=>JSON.stringify(value).replaceAll("'","''");
const row=(n,name)=>({id:id(n),name,instructions:'Synthetic instructions; no clinical dosing'});
const req=(n,group,stateRevision,groupRevision,medicines,extra={})=>({requestId:id(n),person,group:id(group),stateRevision,groupRevision,replace:true,confirmed:true,title:'Fictional prescription',date:'',legacy:[],medicines,...extra});
async function capture(value) {
 if(value.legacyFingerprint!==undefined)return value;
 await db.exec('reset role;');
 const rows=(await db.query(`select r.* from healthathon_records r where r.person_id='${value.person}' and r.kind='entry' and r.data->>'kind'='medicine'
 and not exists(select 1 from healthathon_medicine_courses c where c.person_id=r.person_id and c.medicine_id=r.id and c.past)
 and (not exists(select 1 from healthathon_prescription_medicines m where m.person_id=r.person_id and m.medicine_id=r.id) or exists(select 1 from healthathon_prescription_medicines m join healthathon_prescription_state s on s.person_id=m.person_id and s.current_id=m.prescription_id where m.person_id=r.person_id and m.medicine_id=r.id and m.removed_at=0)) order by r.id`)).rows;
 value.legacy=rows.map(r=>r.id);value.legacyFingerprint=createHash('sha256').update(rows.map(r=>[r.id,String(r.revision),r.data.title??'',r.data.detail??'',r.data.doneOn??'',String(r.data.created??0)].map(v=>`${Buffer.byteLength(v)}:${v}`).join('')).join('')).digest('hex');return value;
}
const save=async(actor,value)=>as(actor,`select healthathon_save_prescription('${quote(await capture(value))}'::jsonb) as result`);
const snapshot=async actor=>(await as(actor,`select healthathon_prescription_snapshot('${person}') as result`)).rows[0].result;
const first=req(1000,200,0,0,[row(300,'Demo A'),row(301,'Demo B')]);
await save(owner,first);checks++;
assert.deepEqual(await snapshot(owner),(await save(owner,first)).rows[0].result);checks++;
await denied(owner,`select healthathon_save_prescription('${quote({...first,title:'Changed retry'})}')`);
await denied(family,`select healthathon_save_prescription('${quote(req(1001,201,1,1,[row(302,'Read only')]))}')`);
await denied(stranger,`select healthathon_prescription_snapshot('${person}')`);
await denied(calendar,`select healthathon_prescription_snapshot('${person}')`);
assert.equal((await snapshot(family)).medicines.length,2);checks++;
const edit=req(1002,200,1,1,[row(300,'Demo A edited')],{replace:false});
await save(doctor,edit);let s=await snapshot(owner);assert.equal(s.medicines.find(m=>m.medicine_id===id(301)).removed_at>0,true);checks++;
await db.exec(`reset role;insert into healthathon_medicine_events(id,person_id,medicine_id,medicine_name,outcome,actor_id,source,reported_date) values('${id(500)}','${person}','${id(300)}','Demo A','taken','${owner}','manual','2026-10-23')`);
const replacement=req(1003,202,2,2,[row(303,'Demo C')],{date:'2026-10-23'});
// Capture a reviewed current group, then change an individual item without changing group revision.
await capture(replacement);await db.exec(`reset role;update healthathon_records set data=jsonb_set(data,'{detail}','"Changed outside group"'),revision=revision+1 where id='${id(300)}'`);
await denied(owner,`select healthathon_save_prescription('${quote(replacement)}')`);delete replacement.legacyFingerprint;
await save(owner,replacement);s=await snapshot(owner);assert.equal(s.groups.find(g=>g.id===id(200)).ended_at>0,true);assert.equal(s.medicines.find(m=>m.prescription_id===id(200)&&m.medicine_id===id(300)).name,'Demo A edited');assert.equal(s.medicines.find(m=>m.prescription_id===id(200)&&m.medicine_id===id(300)).instructions,'Changed outside group');assert.equal(s.medicines.find(m=>m.prescription_id===id(200)&&m.medicine_id===id(301)).instructions,'Synthetic instructions; no clinical dosing');checks++;
await db.exec('reset role;');assert.equal((await db.query('select count(*)::int as n from healthathon_records')).rows[0].n,3);assert.equal((await db.query("select count(*)::int as n from healthathon_medicine_events where source='manual'")).rows[0].n,1);checks++;
// Stable retries return the latest authorized snapshot, not an obsolete current pointer.
const eventsBefore=(await db.query('select count(*)::int n from healthathon_medicine_events')).rows[0].n;
assert.deepEqual((await save(owner,first)).rows[0].result,s);await db.exec('reset role;');assert.equal((await db.query('select count(*)::int n from healthathon_medicine_events')).rows[0].n,eventsBefore);checks++;
await denied(owner,`select healthathon_save_prescription('${quote({...edit,requestId:id(1004)})}')`);
const before=await snapshot(owner);
await db.exec(`reset role;create function public.reject_demo_insert() returns trigger language plpgsql as $$begin if new.data->>'title'='Rollback marker' then raise exception 'Synthetic forced failure';end if;return new;end$$;create trigger fixture_reject before insert on public.healthathon_records for each row execute function reject_demo_insert();`);
await denied(owner,`select healthathon_save_prescription('${quote(req(1005,203,3,1,[row(304,'First partial'),row(305,'Rollback marker')]))}')`);
assert.deepEqual(await snapshot(owner),before);await db.exec('reset role;');assert.equal((await db.query(`select count(*)::int n from healthathon_records where id='${id(304)}'`)).rows[0].n,0);checks++;
await db.exec(`reset role;update healthathon_members set expires_at=now()-interval '1 hour' where user_id='${doctor}'`);
await denied(doctor,`select healthathon_prescription_snapshot('${person}')`);
await db.exec(`reset role;update healthathon_members set expires_at=now()+interval '1 day' where user_id='${doctor}';update healthathon_clinician_requests set status='pending' where user_id='${doctor}'`);
await denied(doctor,`select healthathon_prescription_snapshot('${person}')`);
await db.exec(`reset role;delete from healthathon_members where user_id='${family}'`);await denied(family,`select healthathon_prescription_snapshot('${person}')`);
await db.exec(`reset role;insert into healthathon_private.closed_accounts values('${owner}')`);await denied(owner,`select healthathon_prescription_snapshot('${person}')`);
await db.exec(`reset role;delete from healthathon_private.closed_accounts;update healthathon_clinician_requests set status='approved' where user_id='${doctor}';delete from healthathon_records where id='${id(303)}'`);
assert.equal((await snapshot(owner)).state.current_id,id(202));checks++;await denied(doctor,`select healthathon_prescription_snapshot('${person}')`);
await db.exec('reset role;savepoint anonymous_denial;set role anon;');await assert.rejects(()=>db.query('select healthathon_prescription_capabilities()'));await db.exec('rollback to savepoint anonymous_denial;release savepoint anonymous_denial');checks++;
await db.exec('reset role;');assert.equal((await db.query("select has_function_privilege('anon','public.healthathon_save_prescription(jsonb)','execute') allowed")).rows[0].allowed,false);checks++;
assert.equal((await db.query("select has_function_privilege('service_role','public.healthathon_save_prescription(jsonb)','execute') allowed")).rows[0].allowed,false);assert.equal((await db.query("select has_table_privilege('service_role','public.healthathon_prescriptions','select') allowed")).rows[0].allowed,false);checks++;
await denied(owner,'update healthathon_prescriptions set title=\'Unauthorized direct update\'');
const legacyPerson=id(101),legacyMedicine=id(600);
await db.exec(`reset role;insert into healthathon_people values('${legacyPerson}','${owner}','Fictional legacy fixture');insert into healthathon_records values('${legacyMedicine}','entry','${legacyPerson}','{"kind":"medicine","title":"Legacy demo","detail":"Original","doneOn":"","created":100}',1,now())`);
const legacy=req(1100,210,0,0,[row(601,'New legacy replacement')],{person:legacyPerson});
await denied(owner,`select healthathon_save_prescription('${quote(legacy)}')`);
const fingerprint=(revision,detail)=>createHash('sha256').update([legacyMedicine,String(revision),'Legacy demo',detail,'','100'].map(v=>`${Buffer.byteLength(v)}:${v}`).join('')).digest('hex');
const reviewed={...legacy,legacy:[legacyMedicine],legacyFingerprint:fingerprint(1,'Original')};
await db.exec(`reset role;update healthathon_records set data=jsonb_set(data,'{detail}','"Changed concurrently"'),revision=2 where id='${legacyMedicine}'`);
await denied(owner,`select healthathon_save_prescription('${quote(reviewed)}')`);
const adopted=(await save(owner,{...reviewed,legacyFingerprint:fingerprint(2,'Changed concurrently')})).rows[0].result;
assert.equal(adopted.groups.find(g=>g.id!==id(210)).prescribed_on,null);assert.equal(adopted.medicines.find(m=>m.medicine_id===legacyMedicine).instructions,'Changed concurrently');checks++;
// Actual baseline can_read_record differentiates lab/report permission from care-note permission.
await db.exec(`reset role;insert into healthathon_members values('${legacyPerson}','${doctor}',true,'clinician','{"calendar":true,"reports":true}',now()+interval '1 day')`);
const structured=req(1101,211,1,1,[{...row(602,'Structured lab demo'),instructions:'sahara:entry:v1\n{"type":"lab"}'}],{person:legacyPerson});
await save(owner,structured);assert.ok((await as(doctor,`select healthathon_prescription_snapshot('${legacyPerson}') result`)).rows[0].result);checks++;
await db.exec(`reset role;update healthathon_records set data=jsonb_set(data,'{detail}',to_jsonb(E'sahara:entry:v1\n{"type":"conversation"}'::text)) where id='${id(602)}'`);
await denied(doctor,`select healthathon_prescription_snapshot('${legacyPerson}')`);
await db.exec('reset role;rollback');assert.equal((await db.query("select to_regclass('auth.users') is null as cleaned")).rows[0].cleaned,true);checks++;console.log(`Prescription groups offline SQL checks passed: ${checks}; fictional fixture transaction ROLLBACK verified`);await db.close();
