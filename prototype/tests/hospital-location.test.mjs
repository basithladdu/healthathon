import test from 'node:test';
import assert from 'node:assert/strict';
import { supportedHospitalPin,hospitalLocationMatches,createHospitalLocationSearch,hospitalSearchQuery } from '../app/feedback/hospital-location.ts';
const feature={geometry:{type:'Point',coordinates:[76,10]},properties:{name:'Synthetic Test Hospital',city:'Test City',countrycode:'IN',state:'Kerala'}};
test('location results require point geometry, finite supported coordinates and India country code',()=>{assert.equal(supportedHospitalPin(10,76),true);for(const pair of [[NaN,76],[10,Infinity],[0,0],['10',76]])assert.equal(supportedHospitalPin(...pair),false);const variants=[feature,{...feature,geometry:{type:'Point',coordinates:[0,0]}},{...feature,properties:{...feature.properties,countrycode:'US'}},{...feature,geometry:{type:'LineString',coordinates:[76,10]}},{...feature,properties:{countrycode:'in'}},null];const results=hospitalLocationMatches({features:variants});assert.equal(results.length,1);assert.equal(results[0].label,'Synthetic Test Hospital, Test City, Kerala');});
test('location results deduplicate and bound labels/result count without HTML insertion',()=>{assert.equal(hospitalLocationMatches({features:[feature,feature]}).length,1);assert.equal(hospitalLocationMatches({features:Array.from({length:20},(_,i)=>({...feature,geometry:{type:'Point',coordinates:[76+i/100,10]}}))}).length,8);const result=hospitalLocationMatches({features:[{...feature,properties:{...feature.properties,name:'<script>not executed</script>'}}]});assert.equal(result[0].label.startsWith('<script>'),true);});
test('explicit place search uses fixed no-key Photon endpoint, no credentials; cache is bounded clone',async()=>{let calls=0;const search=createHospitalLocationSearch(async(url,init)=>{calls++;const u=new URL(url);assert.equal(u.origin,'https://photon.komoot.io');assert.equal(u.searchParams.get('bbox'),'68,6,98,38');assert.equal(u.searchParams.get('q'),'test hospital');assert.equal(init.credentials,'omit');assert.equal(init.referrerPolicy,'no-referrer');return Response.json({features:[feature]});});const a=await search(' test   hospital ',new AbortController().signal);a[0].label='mutated';const b=await search('TEST HOSPITAL',new AbortController().signal);assert.equal(calls,1);assert.notEqual(b[0].label,'mutated');});
test('search rejects empty/long query, abort and rapid uncached requests',async()=>{let calls=0;const search=createHospitalLocationSearch(async()=>{calls++;return Response.json({features:[]});});await assert.rejects(()=>search('x',new AbortController().signal));await assert.rejects(()=>search('x'.repeat(161),new AbortController().signal));const aborted=new AbortController();aborted.abort();await assert.rejects(()=>search('test hospital',aborted.signal));assert.equal(calls,0);await search('test hospital',new AbortController().signal);await assert.rejects(()=>search('another hospital',new AbortController().signal),/Wait/);assert.equal(calls,1);});
test('provider errors and oversized response do not fabricate a selection',async()=>{const unavailable=createHospitalLocationSearch(async()=>new Response('',{status:503}));await assert.rejects(()=>unavailable('test hospital',new AbortController().signal),/unavailable/);const oversized=createHospitalLocationSearch(async()=>new Response('x'.repeat(100001)));await assert.rejects(()=>oversized('test hospital',new AbortController().signal),/too much data/);});


test('selected state strictly excludes other states and missing provider state',()=>{
  const karnataka={...feature,properties:{...feature.properties,name:'Synthetic Hospital',state:'Karnataka',city:'Bengaluru',street:'Hospital Road',postcode:'560001'}};
  const wrong={...feature,properties:{...feature.properties,name:'Synthetic Hospital',state:'Maharashtra',city:'Pune'}};
  const missing={...feature,properties:{...feature.properties,state:undefined}};
  const matches=hospitalLocationMatches({features:[wrong,missing,karnataka]},{state:'Karnataka',city:'Bengaluru'},'Synthetic');
  assert.equal(matches.length,1);assert.equal(matches[0].state,'Karnataka');assert.equal(matches[0].name,'Synthetic Hospital');assert.equal(matches[0].address,'Hospital Road, Bengaluru, Karnataka, 560001');
});

test('partial hospital names and selected city rank within the selected state',()=>{
  const item=(name,city,lng)=>({...feature,geometry:{type:'Point',coordinates:[lng,12]},properties:{...feature.properties,name,state:'Karnataka',city}});
  const matches=hospitalLocationMatches({features:[item('Other Hospital','Mysuru',76),item('District General Hospital','Bengaluru',77),item('District General Hospital','Mysuru',78)]},{state:'Karnataka',city:'Bangalore'},'District Gen');
  assert.equal(matches[0].name,'District General Hospital');assert.match(matches[0].address,/Bengaluru/);assert.equal(matches.length,2);
});

test('state aliases are accepted but do not weaken location filtering',()=>{
  assert.equal(hospitalLocationMatches({features:[{...feature,properties:{...feature.properties,state:'Orissa'}}]},{state:'Odisha'}).length,1);
  assert.equal(hospitalLocationMatches({features:[feature]},{state:'Karnataka'}).length,0);
});

test('state and city reach provider with country restriction and are part of cache identity',async()=>{
  const requests=[];
  const search=createHospitalLocationSearch(async(url)=>{const u=new URL(url);requests.push(u);return Response.json({features:[{...feature,properties:{...feature.properties,state:'Karnataka',city:'Bengaluru'}}]});});
  const signal=new AbortController().signal;
  await search('District gen',signal,{state:'Karnataka',city:'Bengaluru'});
  assert.equal(requests[0].searchParams.get('q'),'District gen, Bengaluru, Karnataka');assert.equal(requests[0].searchParams.get('countrycode'),'IN');assert.equal(requests[0].searchParams.get('limit'),'20');
  await search('District gen',signal,{state:'Karnataka',city:'Bengaluru'});assert.equal(requests.length,1);
  await assert.rejects(()=>search('District gen',signal,{state:'Maharashtra',city:'Pune'}),/Wait/);
});


test('search-only spaced initials and Bengaluru RR Nagar alias retrieve the indexed branch without changing saved names',()=>{
  const area={state:'Karnataka',city:'Bengaluru'};
  assert.equal(hospitalSearchQuery('S S Sparsh',area),'SS Sparsh, Bengaluru, Karnataka');
  assert.equal(hospitalSearchQuery('Sparsh RR Nagar',area),'Sparsh Rajarajeshwari Nagar, Bengaluru, Karnataka');
  assert.equal(hospitalSearchQuery('Sparsh RR Nagar',{state:'Maharashtra',city:'Pune'}),'Sparsh RR Nagar, Pune, Maharashtra');
  assert.equal(hospitalSearchQuery('Sparsh RR Nagar',{state:'Karnataka',city:'Mysuru'}),'Sparsh RR Nagar, Mysuru, Karnataka');
  assert.equal(hospitalSearchQuery('Sparsh RR Nagar',{state:'Karnataka',city:'Bangalore'}),'Sparsh Rajarajeshwari Nagar, Bangalore, Karnataka');
});


test('unknown hospital names do not turn provider city-only fallbacks into matches',()=>{
  const city={...feature,properties:{...feature.properties,name:'Bengaluru',city:'Bengaluru',state:'Karnataka'}};
  const unrelated={...feature,properties:{...feature.properties,name:'Karnataka High Court',city:'Bengaluru',state:'Karnataka'}};
  const area={state:'Karnataka',city:'Bengaluru'};
  assert.deepEqual(hospitalLocationMatches({features:[city,unrelated]},area,'SaharaQAUnlisted20261023'),[]);
  assert.deepEqual(hospitalLocationMatches({features:[city,unrelated]},area,'Unknown Hospital Bengaluru'),[]);
  assert.equal(hospitalLocationMatches({features:[city]},area,'Bengaluru').length,1);
});
