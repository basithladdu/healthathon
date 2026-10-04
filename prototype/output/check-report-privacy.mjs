import fs from 'node:fs/promises';
const vars=Object.fromEntries((await fs.readFile('.env.local','utf8')).split(/\r?\n/).filter(Boolean).map(line=>{ const i=line.indexOf('='); return [line.slice(0,i),line.slice(i+1)]; }));
const base=vars.SAANTHVANA_SUPABASE_URL;
const key=vars.SAANTHVANA_SUPABASE_ANON_KEY;
const headers={apikey:key,Authorization:`Bearer ${key}`};
for (const table of ['saanthvana_report_settings','saanthvana_centre_reports','saanthvana_report_attempts']) {
 const response=await fetch(`${base}/rest/v1/${table}?select=*`,{headers});
 console.log(table, response.status);
 if(response.ok) throw new Error(`Private table exposed: ${table}`);
}
const direct=await fetch(`${base}/functions/v1/saanthvana-centre-reports`,{headers});
console.log('Direct Edge without proxy secret',direct.status);
if(direct.status!==401)throw new Error('Direct Edge request unexpectedly allowed');
const listing=await fetch(`${base}/storage/v1/object/list/saanthvana-bills`,{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({prefix:'',limit:10})});
const listed=await listing.json();
console.log('Anonymous private bucket list',listing.status,Array.isArray(listed)?listed.length:'denied');
if(Array.isArray(listed)&&listed.length)throw new Error('Private bucket list exposed');
