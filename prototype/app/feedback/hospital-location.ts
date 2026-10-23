export type HospitalLocation = { id: string; name: string; address: string; state: string; label: string; latitude: number; longitude: number };
export type HospitalSearchArea = { state?: string; city?: string };
const normalize = (value:string) => value.normalize('NFKD').toLocaleLowerCase().replace(/[\u0300-\u036f]/g,'').replace(/[^\p{L}\p{N}]+/gu,' ').trim();
const stateAliases:Record<string,string> = {'orissa':'odisha','uttaranchal':'uttarakhand','nct of delhi':'delhi','national capital territory of delhi':'delhi','pondicherry':'puducherry','andaman nicobar islands':'andaman and nicobar islands','dadra and nagar haveli':'dadra and nagar haveli and daman and diu','daman and diu':'dadra and nagar haveli and daman and diu'};
const normalizedState = (value:string) => stateAliases[normalize(value)] ?? normalize(value);
export function hospitalSearchQuery(raw:string,area:HospitalSearchArea={}):string {
  // Normalise search-only aliases; never rewrite the respondent's saved name.
  let query=raw.trim().replace(/\s+/g,' ').replace(/\b(?:[A-Z]\s+)+[A-Z]\b/gi,initials=>initials.replace(/\s+/g,''));
  if(normalizedState(area.state??'')==='karnataka'&&/\b(bengaluru|bangalore)\b/.test(normalize(area.city??'')))query=query.replace(/\brr\s+nagar\b/gi,'Rajarajeshwari Nagar');
  return [query,...[area.city?.trim(),area.state?.trim()].filter((part):part is string=>Boolean(part)&&!normalize(query).includes(normalize(part!)))].join(', ');
}
export function supportedHospitalPin(latitude: unknown,longitude: unknown): boolean {
  return typeof latitude === 'number' && Number.isFinite(latitude) && latitude >= 6 && latitude <= 38 && typeof longitude === 'number' && Number.isFinite(longitude) && longitude >= 68 && longitude <= 98;
}
export function hospitalLocationMatches(body: unknown,area:HospitalSearchArea={},query=''): HospitalLocation[] {
  if (!body || typeof body !== 'object' || !Array.isArray((body as {features?:unknown}).features)) return [];
  const results: HospitalLocation[] = []; const seen=new Set<string>();
  for (const feature of (body as {features:unknown[]}).features.slice(0,40)) {
    if (!feature || typeof feature !== 'object') continue;
    const f=feature as {geometry?:{type?:unknown;coordinates?:unknown};properties?:Record<string,unknown>};
    const coords=f.geometry?.coordinates,p=f.properties;
    if (f.geometry?.type !== 'Point' || !Array.isArray(coords) || coords.length !== 2 || !supportedHospitalPin(coords[1],coords[0]) || !p || typeof p.countrycode !== 'string' || p.countrycode.toLowerCase() !== 'in') continue;
    const part=(name:string)=>typeof p[name] === 'string' ? (p[name] as string).trim().slice(0,160) : '';
    const title=part('name') || part('street'); if (!title) continue;
    const state=part('state');
    // A nationwide provider ranking is never allowed to override the respondent's state.
    if(area.state && normalizedState(state)!==normalizedState(area.state))continue;
    const address=[...new Set([[part('housenumber'),part('street')].filter(Boolean).join(' '),part('locality'),part('city') || part('district') || part('county'),state,part('postcode')].filter(Boolean))].join(', ').slice(0,500);
    const label=[...new Set([title,address].filter(Boolean))].join(', ').slice(0,500);
    const id=`${coords[0]},${coords[1]}:${label}`;
    if (seen.has(id)) continue; seen.add(id);
    results.push({id,name:title,address,state,label,latitude:Number(coords[1].toFixed(6)),longitude:Number(coords[0].toFixed(6))});
  }
  const contextWords=new Set(normalize(`${area.city??''} ${area.state??''}`).split(' '));
  const words=normalize(query).split(' ').filter(word=>word.length>1&&!contextWords.has(word));
  const distinctive=words.filter(word=>!['hospital','hospitals','clinic','medical','health','care','road','street','nagar'].includes(word));
  const required=distinctive.length?distinctive:words;
  // Photon may silently ignore an unknown hospital token and return only the
  // appended city. Do not present those unrelated places as hospital matches.
  const relevant=required.length?results.filter(item=>required.some(word=>normalize(item.label).includes(word))):results;
  const city=normalize(area.city??'').replace(/bangalore/g,'bengaluru');
  const score=(item:HospitalLocation)=>{
    const name=normalize(item.name),label=normalize(item.label).replace(/bangalore/g,'bengaluru');
    return words.reduce((total,word)=>total+(name.includes(word)?4:label.includes(word)?1:0),0)+(city&&label.includes(city)?5:0)+(/\b(hospital|hospice|clinic|medical|health)\b/.test(name)?3:0);
  };
  return relevant.sort((a,b)=>score(b)-score(a)).slice(0,8);
}
export function createHospitalLocationSearch(fetcher: typeof fetch=fetch) {
  const cache=new Map<string,{expires:number;matches:HospitalLocation[]}>();let lastRequest=0;
  return async (raw:string,signal:AbortSignal,area:HospitalSearchArea={}):Promise<HospitalLocation[]>=>{
    const query=raw.trim().replace(/\s+/g,' ');
    if (query.length < 3 || query.length > 160) throw new Error('Enter 3–160 characters for the hospital name and address.');
    const state=area.state?.trim()??'',city=area.city?.trim()??'';
    if(state.length>80||city.length>80)throw new Error('Check the selected state and city.');
    signal.throwIfAborted();
    const key=JSON.stringify([query.toLocaleLowerCase(),normalizedState(state),normalize(city)]);const found=cache.get(key);
    if (found && found.expires > Date.now()) return found.matches.map(v=>({...v}));
    if (Date.now()-lastRequest < 1000) throw new Error('Wait a moment before searching again.');
    lastRequest=Date.now();
    // Photon supports partial names; adding context improves ranking while the
    // result filter above enforces state even when the provider ignores context.
    const contextualQuery=hospitalSearchQuery(query,{city,state});
    const params=new URLSearchParams({q:contextualQuery,limit:'20',lang:'en',countrycode:'IN',bbox:'68,6,98,38'});
    const response=await fetcher(`https://photon.komoot.io/api/?${params}`,{signal:AbortSignal.any([signal,AbortSignal.timeout(8_000)]),credentials:'omit',referrerPolicy:'no-referrer',redirect:'error',headers:{Accept:'application/json'}});
    if (!response.ok || !response.body) throw new Error('Place search is unavailable. Try again or select the hospital on the map.');
    const reader=response.body.getReader();const chunks:Uint8Array[]=[];let size=0;
    try { while(true){const part=await reader.read();signal.throwIfAborted();if(part.done)break;size+=part.value.length;if(size>100_000)throw new Error('Place search returned too much data. Try a more specific hospital name.');chunks.push(part.value);} }
    finally { void reader.cancel().catch(()=>{});reader.releaseLock(); }
    const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
    const matches=hospitalLocationMatches(JSON.parse(new TextDecoder().decode(bytes)),{state,city},query);
    signal.throwIfAborted();
    if(cache.size>=20)cache.delete(cache.keys().next().value!);
    cache.set(key,{expires:Date.now()+5*60_000,matches});
    return matches.map(v=>({...v}));
  };
}
export const searchHospitalLocations=createHospitalLocationSearch();
