/** Offline PostgreSQL WASM contract tests. No project credentials or remote writes.
 * PGLITE_MODULE must point at an installed @electric-sql/pglite dist/index.js.
 * crypto/auth shims model Supabase's existing extension functions only; production
 * uses pgcrypto + real Auth and must also run its rollback suite before rollout.
 */
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
import assert from 'node:assert/strict';
const { PGlite } = await import(pathToFileURL(process.env.PGLITE_MODULE || 'F:/BuildCaches/sahara-sql-check/node_modules/@electric-sql/pglite/dist/index.js').href);
export async function createCareTestDatabase() {
 const db = new PGlite();
 await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
 create schema auth; create schema extensions;
 create table auth.users(id uuid primary key,email text);
 create function auth.uid() returns uuid language sql stable as $$select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid$$;
 create function auth.jwt() returns jsonb language sql stable as $$select coalesce(nullif(current_setting('request.jwt.claims',true),''),'{}')::jsonb$$;
 grant usage on schema auth,extensions to anon,authenticated;
 create function extensions.digest(text,text) returns bytea language sql immutable as $$select sha256(convert_to($1,'UTF8'))$$;
 create function extensions.gen_random_bytes(integer) returns bytea language sql volatile as $$select substring(decode(repeat(replace(gen_random_uuid()::text,'-',''),8),'hex') from 1 for $1)$$;`);
 const dir=path.resolve('android/supabase/migrations');
 for(const name of (await readdir(dir)).filter(n=>n.endsWith('.sql')).sort()) await db.exec(await readFile(path.join(dir,name),'utf8'));
 return db;
}
export async function asCareUser(db,id,fn) {
 await db.exec(`set role authenticated; select set_config('request.jwt.claim.sub','${id}',false);`);
 try{return await fn();}finally{await db.exec('reset role;');}
}
export const careTestUsers = {patient:'11111111-1111-4111-8111-111111111111',family:'22222222-2222-4222-8222-222222222222',clinician:'33333333-3333-4333-8333-333333333333',stranger:'44444444-4444-4444-8444-444444444444',verifier:'55555555-5555-4555-8555-555555555555'};
export async function seedCareUsers(db) {for(const [name,id] of Object.entries(careTestUsers)) await db.query('insert into auth.users(id,email) values($1,$2)',[id,`${name}@example.invalid`]);}
if (process.argv[1] && path.resolve(process.argv[1])===path.resolve(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/,'$1'))) {
 const db=await createCareTestDatabase(); await seedCareUsers(db);
 const u=careTestUsers, person='66666666-6666-4666-8666-666666666666', draft='77777777-7777-4777-8777-777777777777';
 let checks=0;
 const ok=(value,msg)=>{assert.ok(value,msg);checks++;};
 const deny=async(fn,msg)=>{await assert.rejects(fn,undefined,msg);checks++;};
 const scalar=async(sql,args=[])=>Object.values((await db.query(sql,args)).rows[0])[0];
 await asCareUser(db,u.patient,()=>db.query('insert into public.healthathon_people(id,name,created_ms) values($1,$2,1)',[person,'Synthetic person']));
 await asCareUser(db,u.stranger,async()=>{ok(await scalar('select count(*)::integer from public.healthathon_people')===0,'stranger sees no people'); await deny(()=>db.query('select public.healthathon_invite($1,false)',[person]),'stranger cannot invite');});
 await asCareUser(db,u.clinician,async()=>{await db.query('select public.healthathon_request_clinician($1,$2,$3)',['Synthetic clinician','Synthetic council','TEST-ONLY']);await deny(()=>db.query("update public.healthathon_clinician_requests set status='approved' where user_id=$1",[u.clinician]),'cannot self approve');});
 await asCareUser(db,u.patient,async()=>{await deny(()=>db.query('select public.healthathon_review_clinician($1,1,$2,$3,$4)',[u.clinician,'approved','https://example.invalid/test','Synthetic test only']),'ordinary owner is not verifier');});
 const permissions={careNote:true,calendar:true,reports:false,costs:false};
 const invite=await asCareUser(db,u.patient,()=>scalar('select public.healthathon_invite_access($1,$2,true,$3,30)',[person,'clinician',permissions]));
 await asCareUser(db,u.patient,async()=>{await deny(()=>db.query('select public.healthathon_invite_access($1,null,true,$2,30)',[person,permissions]),'null invitation role rejected');await deny(()=>db.query("select public.healthathon_invite_access($1,'family',true,$2,null)",[person,permissions]),'null membership duration rejected');});
 await asCareUser(db,u.clinician,()=>deny(()=>db.query('select public.healthathon_join($1)',[invite]),'pending clinician cannot consume clinical invitation'));
 // Test fixture grants only. The real migration deliberately contains no staff rows.
 await db.query("insert into healthathon_private.staff_roles(user_id,role) values($1,'clinical_verifier')",[u.verifier]);
 await asCareUser(db,u.verifier,()=>db.query('select public.healthathon_review_clinician($1,1,$2,$3,$4)',[u.clinician,'approved','https://example.invalid/test','Synthetic test only']));
 await asCareUser(db,u.verifier,()=>deny(()=>db.query('select public.healthathon_review_clinician($1,1,$2,$3,$4)',[u.clinician,'rejected','https://example.invalid/test','Stale reviewer decision']),'same request revision cannot overwrite another verifier decision'));
 await asCareUser(db,u.clinician,()=>db.query('select public.healthathon_join($1)',[invite]));
 await asCareUser(db,u.clinician,()=>deny(()=>db.query('select public.healthathon_join($1)',[invite]),'invitation is one use'));
 const data={source:'Patient: I want to spend time at home.',priorities:'I want to spend time at home.',participants:'Patient, clinician',topics:'Goals of care',questions:'',nextSteps:'Discuss with the care team.'};
 await asCareUser(db,u.patient,()=>db.query('select public.healthathon_save_draft($1,$2,$3,0,true)',[draft,person,data]));
 await asCareUser(db,u.patient,()=>deny(()=>db.query('select public.healthathon_review_draft($1,1,true)',[draft]),'patient cannot act as clinician'));
 await asCareUser(db,u.clinician,()=>deny(()=>db.query('select public.healthathon_review_draft($1,2,true)',[draft]),'stale review rejected'));
 const note=await asCareUser(db,u.clinician,()=>scalar('select to_jsonb(public.healthathon_review_draft($1,1,true))',[draft]));
 ok(note.data.reviewerVerification==='verified' && note.data.reviewerId===u.clinician,'reviewer identity comes from approved server profile');
 ok(note.data.doctor==='Synthetic clinician' && note.data.version===1,'server assigns name and next version');
 await asCareUser(db,u.patient,async()=>{const receipts=await db.query('select * from public.healthathon_review_receipts()');ok(receipts.rows[0].note_id===note.id,'verified receipt requires actual reviewed source');});
 const familyInvite=await asCareUser(db,u.patient,()=>scalar('select public.healthathon_invite_access($1,$2,false,$3,1)',[person,'family',permissions]));
 await asCareUser(db,u.family,()=>db.query('select public.healthathon_join($1)',[familyInvite]));
 const ack={versionId:note.id,contentHash:note.data.contentHash,name:'Synthetic family',relationship:'Patient',at:1};
 await asCareUser(db,u.family,()=>deny(()=>db.query('select public.healthathon_save_record(gen_random_uuid(),$1,$2,$3,0)',[person,'ack',ack]),'family cannot confirm as patient'));
 ack.relationship='Family / carer';
 await asCareUser(db,u.family,()=>deny(()=>db.query('select public.healthathon_save_record(gen_random_uuid(),$1,$2,$3,0)',[person,'ack',ack]),'family cannot confirm before patient'));
 await asCareUser(db,u.patient,()=>db.query('select public.healthathon_save_record(gen_random_uuid(),$1,$2,$3,0)',[person,'ack',{...ack,name:'Synthetic patient',relationship:'Patient'}]));
 const familyAck=await asCareUser(db,u.family,()=>scalar('select to_jsonb(public.healthathon_save_record(gen_random_uuid(),$1,$2,$3,0))',[person,'ack',ack]));
 ok(familyAck.data.actorId===u.family,'family confirmation bound to authenticated account');
 ok(familyAck.data.at>1,'acknowledgement time assigned by server');
 await asCareUser(db,u.family,async()=>{ok((await db.query('select * from public.healthathon_note_at($1,1,$2)',[person,note.id])).rows.length===1,'exact version retrieval');ok((await db.query('select * from public.healthathon_note_at($1,2,$2)',[person,note.id])).rows.length===0,'no silent fallback');});
 await asCareUser(db,u.patient,()=>db.query('select public.healthathon_save_record(gen_random_uuid(),$1,$2,$3,0)',[person,'entry',{kind:'journal',title:'Synthetic report',detail:'sahara:entry:v1\n'+JSON.stringify({type:'lab',value:'5',unit:'copied unit'}),created:1}]));
 await asCareUser(db,u.family,async()=>{ok(await scalar("select count(*)::integer from public.healthathon_records where kind='entry'")===0,'reports denied even when encoded inside entry');await deny(()=>db.query("select healthathon_private.save_record_legacy(gen_random_uuid(),$1,'note',$2,0)",[person,note.data]),'legacy privileged save not exposed');});
 const taskId='99999999-9999-4999-8999-999999999999';
 await asCareUser(db,u.patient,()=>db.query('select public.healthathon_save_task($1,$2,$3,$4,$5,0,0)',[taskId,person,u.family,'Synthetic task','No patient information']));
 await asCareUser(db,u.patient,()=>deny(()=>db.query("select public.healthathon_transition_task($1,1,'accepted')",[taskId]),'author cannot accept as recipient'));
 await asCareUser(db,u.family,()=>db.query("select public.healthathon_transition_task($1,1,'accepted')",[taskId]));
 await asCareUser(db,u.family,()=>deny(()=>db.query("select public.healthathon_transition_task($1,1,'completed')",[taskId]),'stale task revision rejected'));
 await asCareUser(db,u.family,()=>db.query("select public.healthathon_transition_task($1,2,'completed')",[taskId]));
 await asCareUser(db,u.patient,async()=>{const counts=await scalar('select public.healthathon_care_progress($1)',[person]);ok(counts.reviewedVersions===1&&counts.patientConfirmations===1&&counts.familyConfirmations===1&&counts.completedTasks===1,'metrics count real recorded events only');});
 await db.query("update public.healthathon_members set expires_at=now()-interval '1 minute' where person_id=$1 and user_id=$2",[person,u.family]);
 await asCareUser(db,u.family,async()=>{ok(await scalar('select count(*)::integer from public.healthathon_records')===0,'expiry removes read access');await deny(()=>db.query("select public.healthathon_transition_task($1,3,'open')",[taskId]),'expired account cannot change tasks');});
 await db.query("update public.healthathon_members set expires_at=now()+interval '1 day' where person_id=$1 and user_id=$2",[person,u.family]);
 await asCareUser(db,u.patient,()=>db.query('delete from public.healthathon_members where person_id=$1 and user_id=$2',[person,u.family]));
 await asCareUser(db,u.family,()=>scalar('select count(*)::integer from public.healthathon_records').then(n=>ok(n===0,'revocation removes online access')));
 await asCareUser(db,u.patient,()=>deny(()=>db.query('select public.healthathon_save_record(gen_random_uuid(),$1,$2,$3,0)',[person,'note',{...note.data,version:2}]),'cannot forge verified metadata through old save'));
 await db.query("insert into healthathon_private.closed_accounts(user_id) values($1)",[u.stranger]);
 await asCareUser(db,u.stranger,()=>deny(()=>db.query('select public.healthathon_request_clinician($1,$2,$3)',['Closed user','Council','TEST']),'closed account cannot request verification'));
 await asCareUser(db,u.patient,async()=>{const id='88888888-8888-4888-8888-888888888888';ok(await scalar('select public.sahara_reserve_assist($1,$2)',[id,'draft'])===true,'first AI reservation');ok(await scalar('select public.sahara_reserve_assist($1,$2)',[id,'draft'])===false,'reservation retry does not run provider again');ok(await scalar('select public.sahara_reserve_assist(gen_random_uuid(),$1)',['draft'])===false,'per minute quota');});
 await db.exec('set role anon;'); await deny(()=>db.query('select * from public.healthathon_care_drafts'),'anonymous cannot read drafts'); await db.exec('reset role;');
 await db.exec('set role anon;'); await deny(()=>db.query("select public.healthathon_review_receipts()"),'anonymous cannot request review receipts'); await db.exec('reset role;');
 await asCareUser(db,u.verifier,()=>db.query('select public.healthathon_review_clinician($1,2,$2,$3,$4)',[u.clinician,'suspended','https://example.invalid/test','Synthetic suspension only']));
 await asCareUser(db,u.clinician,async()=>{
  for(const table of ['healthathon_people','healthathon_records','healthathon_care_drafts','healthathon_team_tasks'])ok(await scalar(`select count(*)::integer from public.${table}`)===0,`suspended clinician cannot read ${table}`);
  ok((await db.query('select * from public.healthathon_note_at($1,1,$2)',[person,note.id])).rows.length===0,'suspended clinician QR retrieval denied');
  await deny(()=>db.query('select public.healthathon_review_draft($1,1,true)',[draft]),'suspended clinician cannot publish');
  await deny(()=>db.query("select public.healthathon_save_task(gen_random_uuid(),$1,$2,'Blocked task','',0,0)",[person,u.clinician]),'suspended clinician cannot write task');
 });
 await asCareUser(db,u.patient,()=>scalar('select count(*)::integer from public.healthathon_records').then(n=>ok(n>0,'patient retains access after clinician suspension')));
 // Closure keeps other patients' immutable evidence, while removing active
 // authority, verification-request identity and quota metadata for this account.
 await db.query("insert into healthathon_private.staff_roles(user_id,role) values($1,'directory_reviewer')",[u.clinician]);
 await db.query("insert into healthathon_private.assist_reservations(request_id,user_id,action) values(gen_random_uuid(),$1,'draft')",[u.clinician]);
 await db.query('select healthathon_private.close_account($1)',[u.clinician]);
 for(const table of ['healthathon_private.staff_roles','healthathon_private.assist_reservations','public.healthathon_clinician_requests'])ok(await scalar(`select count(*)::integer from ${table} where user_id=$1`,[u.clinician])===0,`closure removes ${table}`);
 ok(await scalar("select count(*)::integer from public.healthathon_records where id=$1",[note.id])===1,'closure retains other patient-owned reviewed note');
 console.log(JSON.stringify({checks,passed:true,engine:'PostgreSQL WASM',productionApplied:false})); await db.close();
}
