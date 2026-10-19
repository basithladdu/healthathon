'use client';
import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { readReviewedDirectory, type ReviewedListing } from '../care-reviewed-directory';
import { PalliativeMap, type CareMapPoint } from '../palliative-map';
import '../directory-review.css';
const date = (s:string) => new Date(s).toLocaleDateString('en-IN',{day:'numeric',month:'short',year:'numeric'});
export function ReviewedCentres() {
 const [rows,setRows]=useState<ReviewedListing[]>([]),[status,setStatus]=useState('Loading hospital updates…'),[attempt,setAttempt]=useState(0),[search,setSearch]=useState(''),[selected,setSelected]=useState<string|null>(null),[now,setNow]=useState(()=>Date.now());
 useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),30000);return()=>clearInterval(timer);},[]);
 useEffect(()=>{const abort=new AbortController();fetch('/api/reviewed-centres',{cache:'no-store',signal:abort.signal}).then(async r=>{const b=await r.json() as {error?:string;centres?:unknown};if(!r.ok)throw Error(b.error||'Could not load hospitals.');return readReviewedDirectory(b.centres);}).then(r=>{if(!abort.signal.aborted){setRows(r);setStatus(r.length?'':'No reviewed hospital updates yet.');}}).catch(e=>{if(!abort.signal.aborted){setRows([]);setStatus(e instanceof Error?e.message:'Could not load hospitals.');}});return()=>abort.abort();},[attempt]);
 const shown=useMemo(()=>rows.filter(r=>Date.parse(r.expiresAt)>now&&`${r.listing.name} ${r.listing.city} ${r.listing.state} ${r.listing.services.join(' ')}`.toLowerCase().includes(search.trim().toLowerCase())),[rows,search,now]);
 const points:CareMapPoint[]=shown.map(r=>({id:r.id,name:r.listing.name,coordinates:[r.listing.longitude,r.listing.latitude],category:'hospital',source:'community'}));
 return <main className="directory-review"><header><Link href="/care">← Sahara</Link><h1>Reviewed hospital updates</h1><p>Source checks are dated. Call the centre to confirm services, prescriptions and current medicine stock.</p><nav><Link href="/hospital-survery">Share hospital information</Link><Link href="/directory-review">Review submissions</Link></nav></header>
 <label>Search hospitals or places<input type="search" value={search} onChange={e=>setSearch(e.target.value)}/></label>
 {status&&<p role="status">{status} <button type="button" onClick={()=>setAttempt(a=>a+1)}>Refresh</button></p>}
 {!!shown.length&&<><PalliativeMap points={points} origin={null} selectedId={selected} route={null} searchCentre={null} onSelect={setSelected}/><div className="directory-public-list">{shown.map(r=><article key={r.id} className={selected===r.id?'selected':''}><button className="directory-name" type="button" onClick={()=>setSelected(r.id)}>{r.listing.name}</button><p>{r.listing.city} · {r.listing.state}</p><address>{r.listing.address}</address><p>{r.listing.services.map(s=>({opd:'Clinic visits',ipd:'Inpatient care',home:'Home care'}[s])).join(' · ')||'Call for services'}</p><p>Oral morphine: {r.listing.morphine==='reported_yes'?'reported by facility':r.listing.morphine==='reported_no'?'reported unavailable':'not confirmed'}. Call to check current stock.</p>{r.listing.notes&&<p>{r.listing.notes}</p>}<p className="directory-date">Reported {date(r.reportedAt)} · source reviewed {date(r.reviewedAt)} · recheck by {date(r.expiresAt)}</p><nav>{r.listing.phone&&<a href={`tel:${r.listing.phone.replace(/[^+\d]/g,'')}`}>Call centre</a>}<a href={r.listing.sourceUrl} target="_blank" rel="noopener noreferrer">Published source ↗</a><a href={`https://www.google.com/maps/dir/?api=1&destination=${r.listing.latitude},${r.listing.longitude}`} target="_blank" rel="noopener noreferrer">Directions ↗</a></nav></article>)}</div></>}
 {!status&&!shown.length&&<p>No matching hospital. Try another place.</p>}
 </main>;
}
