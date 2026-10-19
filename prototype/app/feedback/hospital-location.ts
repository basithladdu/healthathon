export type HospitalLocation = { id: string; label: string; latitude: number; longitude: number };
export function supportedHospitalPin(latitude: unknown,longitude: unknown): boolean {
  return typeof latitude === 'number' && Number.isFinite(latitude) && latitude >= 6 && latitude <= 38 && typeof longitude === 'number' && Number.isFinite(longitude) && longitude >= 68 && longitude <= 98;
}
export function hospitalLocationMatches(body: unknown): HospitalLocation[] {
  if (!body || typeof body !== 'object' || !Array.isArray((body as {features?:unknown}).features)) return [];
  const results: HospitalLocation[] = []; const seen=new Set<string>();
  for (const feature of (body as {features:unknown[]}).features.slice(0,20)) {
    if (!feature || typeof feature !== 'object') continue;
    const f=feature as {geometry?:{type?:unknown;coordinates?:unknown};properties?:Record<string,unknown>};
    const coords=f.geometry?.coordinates,p=f.properties;
    if (f.geometry?.type !== 'Point' || !Array.isArray(coords) || coords.length !== 2 || !supportedHospitalPin(coords[1],coords[0]) || !p || typeof p.countrycode !== 'string' || p.countrycode.toLowerCase() !== 'in') continue;
    const part=(name:string)=>typeof p[name] === 'string' ? (p[name] as string).trim().slice(0,160) : '';
    const title=part('name') || part('street'); if (!title) continue;
    const label=[...new Set([title,[part('housenumber'),part('street')].filter(Boolean).join(' '),part('locality'),part('city') || part('district'),part('state'),part('postcode')].filter(Boolean))].join(', ').slice(0,500);
    const id=`${coords[0]},${coords[1]}:${label}`;
    if (seen.has(id)) continue; seen.add(id);
    results.push({id,label,latitude:Number(coords[1].toFixed(6)),longitude:Number(coords[0].toFixed(6))});
    if (results.length === 6) break;
  }
  return results;
}
export function createHospitalLocationSearch(fetcher: typeof fetch=fetch) {
  const cache=new Map<string,{expires:number;matches:HospitalLocation[]}>();let lastRequest=0;
  return async (raw:string,signal:AbortSignal):Promise<HospitalLocation[]>=>{
    const query=raw.trim().replace(/\s+/g,' ');
    if (query.length < 3 || query.length > 160) throw new Error('Enter 3–160 characters for the hospital or address.');
    signal.throwIfAborted();
    const key=query.toLocaleLowerCase();const found=cache.get(key);
    if (found && found.expires > Date.now()) return found.matches.map(v=>({...v}));
    if (Date.now()-lastRequest < 1000) throw new Error('Wait a moment before searching again.');
    lastRequest=Date.now();
    const params=new URLSearchParams({q:query,limit:'8',lang:'en',bbox:'68,6,98,38'});
    const response=await fetcher(`https://photon.komoot.io/api/?${params}`,{signal:AbortSignal.any([signal,AbortSignal.timeout(8_000)]),credentials:'omit',referrerPolicy:'no-referrer',redirect:'error',headers:{Accept:'application/json'}});
    if (!response.ok || !response.body) throw new Error('Place search is unavailable. Try again or select the hospital on the map.');
    const reader=response.body.getReader();const chunks:Uint8Array[]=[];let size=0;
    try { while(true){const part=await reader.read();signal.throwIfAborted();if(part.done)break;size+=part.value.length;if(size>100_000)throw new Error('Place search returned too much data. Try a more specific hospital name.');chunks.push(part.value);} }
    finally { void reader.cancel().catch(()=>{});reader.releaseLock(); }
    const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
    const matches=hospitalLocationMatches(JSON.parse(new TextDecoder().decode(bytes)));
    signal.throwIfAborted();
    if(cache.size>=20)cache.delete(cache.keys().next().value!);
    cache.set(key,{expires:Date.now()+5*60_000,matches});
    return matches.map(v=>({...v}));
  };
}
export const searchHospitalLocations=createHospitalLocationSearch();
