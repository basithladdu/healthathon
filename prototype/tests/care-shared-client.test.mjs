import test from 'node:test';
import assert from 'node:assert/strict';
import { CareSharedClient, sharedNoteUrl, selectSharedNote, emptySharedDraft } from '../app/care-shared-client.ts';
const user='11111111-1111-4111-8111-111111111111',other='22222222-2222-4222-8222-222222222222',person='33333333-3333-4333-8333-333333333333',noteId='44444444-4444-4444-8444-444444444444',draftId='55555555-5555-4555-8555-555555555555';
const session={userId:user,email:'synthetic@example.invalid',access:'opaque-access',refresh:'opaque-refresh',expires:Date.now()/1000+3600};
function storage(value=session){const map=new Map(value?[['sahara-shared-session-v1',JSON.stringify(value)]]:[]);return{getItem:k=>map.get(k)??null,setItem:(k,v)=>map.set(k,v),removeItem:k=>map.delete(k)};}
const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'content-type':'application/json'}});
test('browser transport keeps its global receiver instead of invoking fetch as a client method',async()=>{
 const client=new CareSharedClient(storage(),function(){assert.equal(this,globalThis,'Window.fetch needs its browser receiver');return Promise.resolve(json({id:user,email:'synthetic@example.invalid'}));});
 assert.equal((await client.verifyAccount()).id,user);
});
function note(version=1,id=noteId){return{id,person_id:person,kind:'note',revision:1,updated_at:'2026-10-19T00:00:00Z',data:{version,personName:'Synthetic person',source:'Patient: Home matters.',priorities:'Home matters.',participants:'',topics:'',questions:'',nextSteps:'',doctor:'Synthetic clinician',published:1,contentHash:'a'.repeat(64)}};}
test('exact-version QR retains patient/version/note and never silently substitutes another version',()=>{
 const notes=[note(),note(2,other)];
 const link=new URL(sharedNoteUrl('https://sahara.wedevit.in',notes[0]));
 assert.equal(link.pathname,'/shared-care');assert.equal(link.searchParams.get('person'),person);assert.equal(link.searchParams.get('version'),'1');assert.equal(link.searchParams.get('note'),noteId);
 assert.equal(link.searchParams.has('source'),false);assert.equal(link.searchParams.has('token'),false);
 assert.equal(selectSharedNote(notes,person,3),undefined);assert.equal(selectSharedNote(notes,other,1),undefined);assert.equal(selectSharedNote(notes,person,1,other),undefined);assert.equal(selectSharedNote(notes,person).data.version,2);
});
test('authentication validates the actual account and rejects a mismatched stored identity',async()=>{
 const store=storage();const client=new CareSharedClient(store,async()=>json({id:other,email:'other@example.invalid'}));
 await assert.rejects(client.verifyAccount(),/confirmed Sahara account/);assert.equal(client.currentSession(),null);assert.equal(store.getItem('sahara-shared-session-v1'),null);
});
test('password sign-in is an Auth request and clinical-role metadata never grants authority',async()=>{
 const calls=[];const client=new CareSharedClient(storage(null),async(url,options)=>{calls.push([url,options]);if(url.includes('/token?'))return json({access_token:'actual-access',refresh_token:'actual-refresh',expires_in:3600,user:{id:user,email:'synthetic@example.invalid',user_metadata:{role:'doctor'}}});return json({id:user,email:'synthetic@example.invalid'});});
 await client.signIn(' synthetic@example.invalid ','not-a-real-password');assert.equal(client.currentSession().userId,user);assert.equal(client.currentSession().role,undefined);assert.equal(calls.length,2);assert.equal(calls[0][1].credentials,'omit');assert.equal(calls[1][1].headers.Authorization,'Bearer actual-access');
});
test('concurrent expiry refresh is single flight and uses the refreshed token',async()=>{
 let refreshes=0;let release;const waiting=new Promise(resolve=>release=resolve);
 const client=new CareSharedClient(storage({...session,expires:0}),async()=>{refreshes++;await waiting;return json({access_token:'new-access',refresh_token:'new-refresh',expires_in:3600,user:{id:user,email:'synthetic@example.invalid'}});});
 const a=client.token(),b=client.token();release();assert.deepEqual(await Promise.all([a,b]),['new-access','new-access']);assert.equal(refreshes,1);
});
test('sign-out during a refresh cannot resurrect the account',async()=>{
 let release;const waiting=new Promise(resolve=>release=resolve);const store=storage({...session,expires:0});
 const client=new CareSharedClient(store,async(url)=>{if(url.includes('refresh_token')){await waiting;return json({access_token:'new',refresh_token:'new',expires_in:3600,user:{id:user}});}return json({});});
 const refreshing=client.token(); // Simulate another tab clearing the session via sign-out while the refresh is in flight.
 const signout=client.signOut();release();await Promise.allSettled([refreshing,signout]);assert.equal(client.currentSession(),null);assert.equal(store.getItem('sahara-shared-session-v1'),null);
});
test('offline and conflicting draft writes preserve the original local source for recovery',async()=>{
 const data={...emptySharedDraft(),source:'Patient: I do not want this changed.',priorities:'I do not want this changed.'};const snapshot=JSON.stringify(data);
 const offline=new CareSharedClient(storage(),async()=>{throw new TypeError('offline');});await assert.rejects(offline.saveDraft(data,person,draftId,3,true),/draft is still here/);assert.equal(JSON.stringify(data),snapshot);
 const stale=new CareSharedClient(storage(),async()=>json({code:'40001',message:'server conflict'},409));await assert.rejects(stale.saveDraft(data,person,draftId,3,true),/Refresh and compare/);assert.equal(JSON.stringify(data),snapshot);
});
test('a receipt for the wrong person, author or revision is never claimed as a saved draft',async()=>{
 const data={...emptySharedDraft(),source:'Synthetic words'};
 for(const mismatch of [{person_id:other},{author_id:other},{revision:8},{id:other}]){const client=new CareSharedClient(storage(),async()=>json({id:draftId,person_id:person,author_id:user,revision:2,data,status:'submitted',...mismatch}));await assert.rejects(client.saveDraft(data,person,draftId,1,true),/did not match/);}
});
test('a service rollout gap retains ordinary records without claiming review readiness',async()=>{
 const client=new CareSharedClient(storage(),async(url)=>{
  if(url.endsWith('/auth/v1/user'))return json({id:user});
  if(url.includes('healthathon_care_drafts')||url.includes('healthathon_clinician_requests'))return json({code:'PGRST205'},404);
  if(url.includes('healthathon_people'))return json([{id:person,owner_id:user,name:'Synthetic person',created_ms:1}]);
  return json([]);
 });
 const snapshot=await client.load();assert.equal(snapshot.people[0].id,person);assert.equal(snapshot.workflowReady,false);assert.deepEqual(snapshot.drafts,[]);
});
