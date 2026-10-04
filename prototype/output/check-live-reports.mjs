import fs from 'node:fs/promises';
import assert from 'node:assert/strict';
const base='http://127.0.0.1:3100';
const first=await fetch(`${base}/api/centre-reports`);
assert.equal(first.status,200);
const cookie=first.headers.get('set-cookie').split(';')[0];
const ids=[];
const post=async(date,bill=true)=>{
 const form=new FormData();
 for(const [k,v] of Object.entries({hospitalName:'Saanthvana API Test Hospital',district:'Bengaluru Urban',address:'Synthetic software test address - not a real hospital',visitDate:date,outcome:bill?'received':'unavailable',oralMorphine:bill?'available':'unavailable',reporterRole:bill?'patient':'doctor',consent:'true'}))form.set(k,v);
 if(bill)form.set('bill',new Blob([await fs.readFile('output/synthetic-bill.png')],{type:'image/png'}),'synthetic-bill.png');
 return fetch(`${base}/api/centre-reports`,{method:'POST',headers:{Origin:base,Cookie:cookie},body:form});
};
try {
 const saved=await post('2026-10-01'); const body=await saved.json(); assert.equal(saved.status,201,JSON.stringify(body));ids.push(body.report.id);
 assert.equal(body.report.receiptStatus,'attached');assert(!JSON.stringify(body).includes('receipt_path'));
 const duplicate=await post('2026-10-01');assert.equal(duplicate.status,409);
 const second=await post('2026-09-30',false);const secondBody=await second.json();assert.equal(second.status,201,JSON.stringify(secondBody));ids.push(secondBody.report.id);assert.equal(secondBody.report.doctorStatus,'self-reported');
 const outsiders=await fetch(`${base}/api/centre-reports`);const shared=await outsiders.json();assert(ids.every(id=>shared.reports.some(r=>r.id===id)));assert.deepEqual(shared.ownedIds,[]);
 const forbidden=await fetch(`${base}/api/centre-reports?id=${ids[0]}`,{method:'DELETE',headers:{Origin:base}});assert.equal(forbidden.status,404);
 const privateBill=await fetch(`https://cghlkqeqiokjqdionrkc.supabase.co/storage/v1/object/public/saanthvana-bills/${ids[0]}/receipt.png`);assert(!privateBill.ok);
 console.log('PASS: live upload, shared reads, duplicate prevention, conflicting doctor report, ownership and private bill URL');
} finally {
 for(const id of ids){const removed=await fetch(`${base}/api/centre-reports?id=${id}`,{method:'DELETE',headers:{Origin:base,Cookie:cookie}});assert.equal(removed.status,200,await removed.text());}
 const after=await (await fetch(`${base}/api/centre-reports`)).json();assert(ids.every(id=>!after.reports.some(r=>r.id===id)));console.log('PASS: test reports withdrawn');
}
