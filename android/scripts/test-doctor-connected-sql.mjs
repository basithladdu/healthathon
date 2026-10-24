/** Offline PostgreSQL WASM checks only. Auth users and Storage below are local shims,
 * never inserted into a real project. Run from repository root with PGLITE_MODULE. */
import { readFile, readdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';
process.on('unhandledRejection', error => { console.error(error.message, error.code || '', error.position || ''); process.exit(1); });
process.on('uncaughtException', error => { console.error(error.message, error.code || '', error.position || '', error.query?.slice(Math.max(0, Number(error.position || 0)-150), Number(error.position || 0)+100) || ''); process.exit(1); });
const { PGlite } = await import(pathToFileURL(process.env.PGLITE_MODULE).href);
const db = new PGlite();
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
alter table storage.objects enable row level security;
grant select,insert,update,delete on storage.objects to anon,authenticated;
-- Reproduce the EXISTING live broad permissive policy so guards are tested with OR access.
create policy existing_other_app_all on storage.objects for all to anon,authenticated using(bucket_id<>'saanthvana-bills') with check(bucket_id<>'saanthvana-bills');`);
for(const file of (await readdir('android/supabase/migrations')).filter(x=>x.endsWith('.sql')).sort()) await db.exec(await readFile(path.join('android/supabase/migrations',file),'utf8'));
const owner='11111111-1111-4111-8111-111111111111', stranger='22222222-2222-4222-8222-222222222222', editor='33333333-3333-4333-8333-333333333333';
await db.query('insert into auth.users values($1,$2),($3,$4),($5,$6)',[owner,'owner@example.invalid',stranger,'stranger@example.invalid',editor,'editor@example.invalid']);
let checks=0;
const ok=(value,msg)=>{assert.ok(value,msg);checks++;};
const denied=async(fn,msg)=>{await assert.rejects(fn,undefined,msg);checks++;};
const scalar=async(sql,args=[])=>Object.values((await db.query(sql,args)).rows[0])[0];
async function as(id,fn,role='authenticated') {await db.exec(`set role ${role}; select set_config('request.jwt.claim.sub','${id||''}',false);`); try{return await fn();}finally{await db.exec('reset role');}}
const draft={schemaVersion:1,synthetic:true,priorities:'Patient: Family time matters.',participants:'Patient, clinician (synthetic)',topics:'Visit preparation',questions:'Who attends?',undecided:'Next visit mode',nextSteps:'Arrange discussion',sourceText:'Patient: Family time matters.\nFamily: We can arrange transport.',sourceIds:['sample-source'],updatedAt:1,mockGenerated:true,checklist:[{title:'Patient priorities',status:'DISCUSSED'}],transcriptProvider:'',transcriptModel:'',transcribedAt:0,audioClipId:'',aiProvider:'',aiModel:'',aiWarnings:['Fictional example'],evidence:[]};
const fixtures=['asha','ravi','leela','imran','maya','joseph'].map((name,i)=>({legacyId:`demo-${name}`,name,age:40+i,careContext:'Synthetic care',summary:'Fictional summary',payload:{synthetic:true,schemaVersion:1,careNotes:['Prior conversation'],checkIns:[]},draft,doses:[{id:`demo-${name}-dose-1`,medicineLabel:'Sample medicine',plannedAt:1720000000000,reportedTakenAt:1720000001000,outcome:'taken'}]}));
await as('',()=>denied(()=>db.query('select public.sahara_doctor_seed($1)',[fixtures]),'anon cannot seed'),'anon');
let dataset,patient,note;
await as(owner,async()=>{
 dataset=await scalar('select public.sahara_doctor_seed($1)',[fixtures]);
 ok(await scalar('select public.sahara_doctor_seed($1)',[fixtures])===dataset,'seed idempotent dataset');
 ok(await scalar('select count(*)::int from public.sahara_doctor_patients')===6,'six stable patient UUIDs');
 ok(await scalar('select count(*)::int from public.sahara_doctor_notes')===6,'repeat seed keeps one initial draft each');
 ok(await scalar('select count(*)::int from public.sahara_doctor_doses')===6,'repeat seed does not duplicate dose history');
 patient=await scalar('select id from public.sahara_doctor_patients order by legacy_id limit 1');
 note=await scalar('select id from public.sahara_doctor_notes where patient_id=$1',[patient]);
 const preserved=await scalar('select payload from public.sahara_doctor_notes where id=$1',[note]);
 ok(JSON.stringify(preserved.undecided)===JSON.stringify(draft.undecided)&&preserved.evidence.length===0&&preserved.sourceIds[0]==='sample-source','rich fields persisted');
 await denied(()=>db.query("update public.sahara_doctor_notes set revision=revision+1 where id=$1",[note]),'no direct update bypassing CAS');
 await denied(()=>db.query("insert into public.sahara_doctor_dataset_members(dataset_id,user_id,expires_at) values($1,$2,now()+interval '1 day')",[dataset,stranger]),'cannot self grant membership');
 await db.query('select public.sahara_doctor_save_note($1,$2,1,$3,false)',[patient,note,{...draft,undecided:'Keep this wording'}]);
 await denied(()=>db.query('select public.sahara_doctor_save_note($1,$2,1,$3,false)',[patient,note,draft]),'stale draft rejected');
 const saved=(await db.query('select * from public.sahara_doctor_save_note($1,$2,2,$3,true)',[patient,note,{...draft,undecided:'Keep this wording'}])).rows[0];
 ok(saved.status==='test_published'&&saved.revision===3&&saved.content_hash.length===64,'unverified test publication hash and revision');
 await denied(()=>db.query('select public.sahara_doctor_save_note($1,$2,3,$3,false)',[patient,note,draft]),'published snapshot immutable');
 await denied(()=>db.query('select public.sahara_doctor_save_note($1,gen_random_uuid(),0,$2,false)',[patient,{...draft,reviewerVerification:'verified'}]),'cannot inject clinical verification claim');
 await denied(()=>db.query('select public.sahara_doctor_save_note($1,gen_random_uuid(),0,$2,false)',[patient,{...draft,sourceText:'a'.repeat(60001)}]),'oversized source not truncated');
 for(const key of ['schemaVersion','synthetic']) { const missing={...draft};delete missing[key];await denied(()=>db.query('select public.sahara_doctor_save_note($1,gen_random_uuid(),0,$2,false)',[patient,missing]),`missing ${key} rejected`); }
 await denied(()=>db.query('select public.sahara_doctor_save_note($1,gen_random_uuid(),0,$2,false)',[patient,{...draft,checklist:[{title:'Priority',status:'DISCUSSED',futureField:'do not silently drop'}]}]),'unknown nested checklist rejected');
 await denied(()=>db.query('select public.sahara_doctor_save_note($1,gen_random_uuid(),0,$2,false)',[patient,{...draft,evidence:[{section:'questions',quote:'Question',futureField:'do not silently drop'}]}]),'unknown nested evidence rejected');
 await denied(()=>db.query('select public.sahara_doctor_save_note(gen_random_uuid(),$1,3,$2,false)',[note,draft]),'wrong patient cannot reuse note ID');
 const expectedHash=await scalar("select encode(extensions.digest(payload::text,'sha256'),'hex') from public.sahara_doctor_notes where id=$1",[note]);
 ok(expectedHash===saved.content_hash,'publication hash equals exact saved payload');
 await denied(()=>db.query("insert into public.sahara_doctor_doses(dataset_id,patient_id,medicine_label,planned_at,outcome) values($1,$2,'Sample',now(),'taken')",[dataset,patient]),'taken requires time');
 await denied(()=>db.query("insert into public.sahara_doctor_doses(dataset_id,patient_id,medicine_label,planned_at,reported_taken_at,outcome) values($1,$2,'Sample',now(),now(),'skipped')",[dataset,patient]),'skipped cannot pretend taken');
 await denied(()=>db.query("insert into public.sahara_doctor_doses(dataset_id,patient_id,medicine_label,planned_at,reported_taken_at,outcome) values($1,$2,'Sample',now(),now()+interval '1 day','taken')",[dataset,patient]),'future reported taken rejected');
 const dose=(await db.query("insert into public.sahara_doctor_doses(dataset_id,patient_id,medicine_label,planned_at,outcome,logged_at) values($1,$2,'Sample',now()-interval '1 hour','skipped','2000-01-01') returning *",[dataset,patient])).rows[0];
 ok(dose.reporter_id===owner&&new Date(dose.logged_at).getUTCFullYear()>2020,'reporter and logged time are server assigned');
 await denied(()=>db.query("update public.sahara_doctor_doses set outcome='taken'"),'dose history append only');
});
await as(stranger,async()=>{
 for(const table of ['datasets','patients','notes','doses','files']) ok(await scalar(`select count(*)::int from public.sahara_doctor_${table}`)===0,`stranger cannot read ${table}`);
 await denied(()=>db.query('select public.sahara_doctor_save_note($1,$2,3,$3,false)',[patient,note,draft]),'stranger cannot edit note');
 const otherDataset=await scalar('select public.sahara_doctor_seed($1)',[fixtures]);
 await denied(()=>db.query("insert into public.sahara_doctor_doses(dataset_id,patient_id,medicine_label,planned_at,outcome) values($1,$2,'Spoof',now(),'skipped')",[otherDataset,patient]),'cross-dataset patient FK prevents reassignment');
});
// Membership is configured only by trusted administration in this offline test.
await db.query("insert into public.sahara_doctor_dataset_members values($1,$2,true,now()+interval '1 hour')",[dataset,editor]);
await as(editor,async()=>{ok(await scalar('select count(*)::int from public.sahara_doctor_patients')===6,'explicit editor can read dataset'); await denied(()=>db.query('select public.sahara_doctor_save_note($1,$2,3,$3,false)',[patient,note,draft]),'editor cannot edit another author snapshot');});
let file,objectPath;
await as(owner,async()=>{
 file=await scalar('select gen_random_uuid()'); objectPath=`${dataset}/${patient}/${file}`;
 await denied(()=>db.query("insert into storage.objects(bucket_id,name,metadata) values('sahara-doctor-test-files','unknown','{}')"),'unknown upload path denied');
 await denied(()=>db.query("insert into public.sahara_doctor_files(id,dataset_id,patient_id,title,kind,mime,bytes,sha256,object_path) values($1,$2,$3,'Sample','report','application/pdf',3,$4,'spoof')",[file,dataset,patient,'a'.repeat(64)]),'metadata path identity enforced');
 await db.query("insert into public.sahara_doctor_files(id,dataset_id,patient_id,title,kind,mime,bytes,sha256,object_path,permission_attested) values($1,$2,$3,'Sample','report','application/pdf',3,$4,$5,true)",[file,dataset,patient,'a'.repeat(64),objectPath]);
 await denied(()=>db.query('select public.sahara_doctor_complete_file($1)',[file]),'metadata without bytes cannot become ready');
 await denied(()=>db.query("update public.sahara_doctor_files set upload_state='ready' where id=$1",[file]),'client cannot mark metadata ready');
 await db.query("insert into storage.objects(bucket_id,name,metadata) values('sahara-doctor-test-files',$1,'{\"size\":3,\"mimetype\":\"application/pdf\"}')",[objectPath]);
 ok(await scalar("select count(*)::int from storage.objects where bucket_id='sahara-doctor-test-files'")===0,'pending object not readable');
 const ready=(await db.query('select * from public.sahara_doctor_complete_file($1)',[file])).rows[0];ok(ready.upload_state==='ready','completed upload ready after server size/mime check');
 ok(await scalar("select count(*)::int from storage.objects where bucket_id='sahara-doctor-test-files'")===1,'owner can read ready protected file');
 ok((await db.query("update storage.objects set name='changed' where name=$1 returning *",[objectPath])).rows.length===0,'existing broad policy cannot allow object replacement');
 ok((await db.query('delete from storage.objects where name=$1 returning *',[objectPath])).rows.length===0,'existing broad policy cannot allow object deletion');
});
await as('',async()=>{
 ok(await scalar("select count(*)::int from storage.objects where bucket_id='sahara-doctor-test-files'")===0,'anon cannot read private file despite old OR policy');
 ok(await scalar('select count(*)::int from public.sahara_doctor_files')===0,'anon metadata lookup privilege exposes zero rows');
 await denied(()=>db.query("insert into storage.objects(bucket_id,name) values('sahara-doctor-test-files',$1)",[objectPath+'-fake']),'anon cannot upload despite old OR policy');
 await db.query("insert into storage.objects(bucket_id,name) values('unrelated-bucket','unchanged')");
 ok(await scalar("select count(*)::int from storage.objects where bucket_id='unrelated-bucket'")===1,'other bucket policy unchanged');
},'anon');
await as(stranger,async()=>{ok(await scalar("select count(*)::int from storage.objects where bucket_id='sahara-doctor-test-files'")===0,'stranger cannot read protected file');});
await as(editor,async()=>{ok(await scalar("select count(*)::int from storage.objects where bucket_id='sahara-doctor-test-files'")===1,'explicit member reads ready file');});
await db.query("update public.sahara_doctor_dataset_members set expires_at=now()-interval '1 second' where user_id=$1",[editor]);
await as(editor,async()=>{ok(await scalar("select count(*)::int from storage.objects where bucket_id='sahara-doctor-test-files'")===0,'expired membership loses file access');ok(await scalar('select count(*)::int from public.sahara_doctor_patients')===0,'expired membership loses dataset access');});
await db.query('insert into healthathon_private.closed_accounts(user_id) values($1)',[owner]);
await as(owner,()=>denied(()=>db.query('select * from public.sahara_doctor_patients'),'closed account denied'));
console.log(`${checks} isolated connected-doctor SQL checks passed. No live project writes.`);
await db.close();
