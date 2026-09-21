'use client';

import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { CareDirectory } from './care-directory';
import { CARE_CENTRES, DIRECTORY_CHECKED_ON, filterCareCentres, type CareCentre } from './care-directory-data';
import { PalliativeMap, type CareMapOrigin, type CareMapPoint, type CareRoadRoute } from './palliative-map';
import { DIRECTORY_MAP_POINTS } from './care-map-data';

export type CareNearMeProps = { audience: 'patient' | 'family'; patientName: string; homeAddress?: string };
type Category = 'all' | 'palliative' | 'hospital';
type Place = CareMapPoint & { address: string; sourceUrl: string; distanceKm: number };
type SearchMatch = CareMapOrigin & { id: string; address: string; sourceUrl: string };
type PhotonFeature = {
  geometry?: { coordinates?: [number, number] };
  properties?: { osm_type?: string; osm_id?: number; osm_key?: string; osm_value?: string; name?: string; housenumber?: string; street?: string; locality?: string; district?: string; city?: string; state?: string; country?: string; countrycode?: string; postcode?: string };
};
const PHOTON_URL = 'https://photon.komoot.io/api/';
const FACILITY_KEYS = new Set(['amenity', 'healthcare', 'building', 'office', 'social_facility']);

function validCoordinates(value: unknown): value is [number, number] {
  return Array.isArray(value) && value.length === 2 && value.every(Number.isFinite) && Math.abs(value[0]) <= 180 && Math.abs(value[1]) <= 90;
}
function distanceKm(a: [number, number], b: [number, number]) {
  const radians = Math.PI / 180;
  const value = Math.sin((b[1] - a[1]) * radians / 2) ** 2 + Math.cos(a[1] * radians) * Math.cos(b[1] * radians) * Math.sin((b[0] - a[0]) * radians / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
}
function categoryOf(feature: PhotonFeature): Exclude<Category, 'all'> | null {
  const props = feature.properties;
  if (!props?.osm_key || !FACILITY_KEYS.has(props.osm_key) || /\b(veterinary|animal|pet)\b/i.test(props.name ?? '')) return null;
  if (props.osm_value === 'hospice' || props.osm_value === 'palliative' || /palliat|hospice/i.test(props.name ?? '')) return 'palliative';
  return props.osm_value === 'hospital' ? 'hospital' : null;
}
function matchOf(feature: PhotonFeature): SearchMatch | null {
  const props = feature.properties;
  const coordinates = feature.geometry?.coordinates;
  if (!props?.name || !validCoordinates(coordinates)) return null;
  const address = [...new Set([[props.housenumber, props.street].filter(Boolean).join(' '), props.locality ?? props.district, props.city, props.state, props.postcode].filter(Boolean))].join(', ');
  const kind = ({ N: 'node', W: 'way', R: 'relation', node: 'node', way: 'way', relation: 'relation' } as Record<string, string>)[props.osm_type ?? ''];
  return { id: `${kind ?? 'place'}/${props.osm_id ?? coordinates.join(',')}`, label: props.name, coordinates, address, sourceUrl: kind && props.osm_id ? `https://www.openstreetmap.org/${kind}/${props.osm_id}` : `https://www.openstreetmap.org/?mlat=${coordinates[1]}&mlon=${coordinates[0]}#map=16/${coordinates[1]}/${coordinates[0]}` };
}
async function photon(query: string, signal: AbortSignal, origin?: CareMapOrigin, hospitalOnly = false): Promise<PhotonFeature[]> {
  const params = new URLSearchParams({ q: query, limit: origin ? '35' : '6', lang: 'en' });
  if (origin) { params.set('lat', origin.coordinates[1].toFixed(2)); params.set('lon', origin.coordinates[0].toFixed(2)); }
  if (hospitalOnly) params.set('osm_tag', 'amenity:hospital');
  const response = await fetch(`${PHOTON_URL}?${params}`, { signal });
  if (!response.ok) throw new Error('Place search unavailable');
  const body = await response.json() as { features?: PhotonFeature[] };
  return Array.isArray(body.features) ? body.features : [];
}
function directionsUrl(point: CareMapPoint, origin: CareMapOrigin | null) {
  const params = new URLSearchParams({ api: '1', destination: `${point.coordinates[1]},${point.coordinates[0]}`, travelmode: 'driving' });
  if (origin) params.set('origin', `${origin.coordinates[1]},${origin.coordinates[0]}`);
  return `https://www.google.com/maps/dir/?${params}`;
}

export function CareNearMe({ homeAddress }: CareNearMeProps): React.JSX.Element {
  const [query, setQuery] = useState('');
  const [service, setService] = useState('');
  const [category, setCategory] = useState<Category>('palliative');
  const [mobileView, setMobileView] = useState<'map' | 'centres'>('map');
  const [startText, setStartText] = useState('');
  const [origin, setOrigin] = useState<CareMapOrigin | null>(null);
  const [originMatches, setOriginMatches] = useState<SearchMatch[]>([]);
  const [findingOrigin, setFindingOrigin] = useState(false);
  const [locationMessage, setLocationMessage] = useState('');
  const [places, setPlaces] = useState<Place[]>([]);
  const [nearbyStatus, setNearbyStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [nearbyRetry, setNearbyRetry] = useState(0);
  const [resolved, setResolved] = useState<Record<string, CareMapPoint>>(DIRECTORY_MAP_POINTS);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [locatingId, setLocatingId] = useState<string | null>(null);
  const [centreMatches, setCentreMatches] = useState<{ centre: CareCentre; matches: SearchMatch[] } | null>(null);
  const [centreMessage, setCentreMessage] = useState('');
  const [route, setRoute] = useState<CareRoadRoute | null>(null);
  const [routeStatus, setRouteStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [routeRetry, setRouteRetry] = useState(0);
  const startInput = useRef<HTMLInputElement>(null);
  const originRequest = useRef<AbortController | null>(null);
  const centreRequest = useRef<AbortController | null>(null);
  const locationRequest = useRef(0);

  const centres = useMemo(() => {
    const matches = filterCareCentres(query, '', service).filter((centre) => category !== 'hospital' || /hospital|institute|aiims/i.test(centre.name)).map((centre) => ({ ...centre, coordinates: resolved[centre.id]?.coordinates }));
    return matches.sort((a, b) => origin ? (a.coordinates ? distanceKm(origin.coordinates, a.coordinates) : Infinity) - (b.coordinates ? distanceKm(origin.coordinates, b.coordinates) : Infinity) : Number(Boolean(b.coordinates)) - Number(Boolean(a.coordinates)));
  }, [query, service, category, origin, resolved]);
  const filteredPlaces = useMemo(() => places.filter((place) => (!query.trim() || `${place.name} ${place.address}`.toLowerCase().includes(query.trim().toLowerCase())) && (category === 'all' || place.category === category) && !service), [places, query, category, service]);
  const points = useMemo(() => [...centres.flatMap((centre) => resolved[centre.id] ? [resolved[centre.id]] : []), ...filteredPlaces], [centres, resolved, filteredPlaces]);
  const pinNumbers = useMemo(() => Object.fromEntries(points.map((point, index) => [point.id, index + 1])), [points]);
  const distances = useMemo(() => origin ? Object.fromEntries(points.map((point) => [point.id, distanceKm(origin.coordinates, point.coordinates)])) : {}, [points, origin]);
  const selectedCentre = CARE_CENTRES.find((centre) => centre.id === selectedId);
  const selectedPoint = (selectedId ? resolved[selectedId] : undefined) ?? places.find((place) => place.id === selectedId) ?? null;
  const selectedName = selectedCentre?.name ?? selectedPoint?.name;
  const selectedPlace = places.find((place) => place.id === selectedId);
  const visibleSelectedPoint = points.find((point) => point.id === selectedId) ?? null;

  useEffect(() => () => { originRequest.current?.abort(); centreRequest.current?.abort(); locationRequest.current += 1; }, []);

  useEffect(() => {
    if (!origin) { setPlaces([]); setNearbyStatus('idle'); return; }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 14000);
    setNearbyStatus('loading');
    setPlaces([]);
    Promise.allSettled([photon('palliative care', controller.signal, origin), photon('hospice', controller.signal, origin), photon('hospital', controller.signal, origin, true)]).then((results) => {
      if (controller.signal.aborted) return;
      if (results.every((result) => result.status === 'rejected')) { setNearbyStatus('error'); return; }
      const found = new Map<string, Place>();
      for (const result of results) {
        if (result.status !== 'fulfilled') continue;
        for (const feature of result.value) {
          const match = matchOf(feature); const kind = categoryOf(feature);
          if (!match || !kind) continue;
          const distance = distanceKm(origin.coordinates, match.coordinates);
          if (distance > 30) continue;
          found.set(match.id, { id: match.id, name: match.label, coordinates: match.coordinates, category: kind, address: match.address, sourceUrl: match.sourceUrl, distanceKm: distance });
        }
      }
      setPlaces([...found.values()].sort((a, b) => a.distanceKm - b.distanceKm));
      setNearbyStatus('ready');
    }).finally(() => clearTimeout(timeout));
    const onAbort = () => setNearbyStatus('error');
    controller.signal.addEventListener('abort', onAbort);
    return () => { controller.signal.removeEventListener('abort', onAbort); controller.abort(); clearTimeout(timeout); };
  }, [origin, nearbyRetry]);

  useEffect(() => {
    setRoute(null);
    if (!origin || !visibleSelectedPoint) { setRouteStatus('idle'); return; }
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 14000);
    setRouteStatus('loading');
    const pair = `${origin.coordinates.join(',')};${visibleSelectedPoint.coordinates.join(',')}`;
    fetch(`https://router.project-osrm.org/route/v1/driving/${pair}?overview=full&geometries=geojson&steps=false`, { signal: controller.signal }).then(async (response) => {
      if (!response.ok) throw new Error('Route unavailable');
      const data = await response.json() as { code?: string; routes?: { geometry?: { type?: string; coordinates?: [number, number][] }; distance?: number; duration?: number }[] };
      const found = data.routes?.[0];
      if (data.code !== 'Ok' || found?.geometry?.type !== 'LineString' || !Array.isArray(found.geometry.coordinates) || found.geometry.coordinates.length < 2 || !found.geometry.coordinates.every(validCoordinates) || typeof found.distance !== 'number' || !Number.isFinite(found.distance) || typeof found.duration !== 'number' || !Number.isFinite(found.duration)) throw new Error('No road route');
      if (controller.signal.aborted) return;
      setRoute({ coordinates: found.geometry.coordinates, distance: found.distance, duration: found.duration });
      setRouteStatus('ready');
    }).catch(() => { if (!controller.signal.aborted) setRouteStatus('error'); }).finally(() => clearTimeout(timeout));
    const onAbort = () => setRouteStatus('error');
    controller.signal.addEventListener('abort', onAbort);
    return () => { controller.signal.removeEventListener('abort', onAbort); controller.abort(); clearTimeout(timeout); };
  }, [origin, visibleSelectedPoint, routeRetry]);

  function chooseOrigin(match: CareMapOrigin) {
    originRequest.current?.abort(); locationRequest.current += 1;
    setOrigin(match); setStartText(match.label); setOriginMatches([]); setLocationMessage(''); setFindingOrigin(false);
  }
  async function searchOrigin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!startText.trim()) { startInput.current?.focus(); return; }
    originRequest.current?.abort(); locationRequest.current += 1;
    const controller = new AbortController(); originRequest.current = controller;
    const timeout = setTimeout(() => controller.abort(), 12000);
    setFindingOrigin(true); setOriginMatches([]); setLocationMessage('');
    try {
      const matches = (await photon(startText.trim(), controller.signal)).map(matchOf).filter((match): match is SearchMatch => Boolean(match));
      if (originRequest.current !== controller) return;
      if (matches.length === 1) chooseOrigin(matches[0]);
      else { setOriginMatches(matches); if (!matches.length) setLocationMessage('No matching place. Add a city or a fuller address.'); }
    } catch { if (originRequest.current === controller) setLocationMessage('Location search could not load. Try again.'); }
    finally { clearTimeout(timeout); if (originRequest.current === controller) setFindingOrigin(false); }
  }
  function useLocation() {
    originRequest.current?.abort();
    const request = ++locationRequest.current;
    if (!navigator.geolocation) { setLocationMessage('Location is unavailable on this device. Enter a starting point.'); return; }
    setFindingOrigin(true); setOriginMatches([]); setLocationMessage('');
    navigator.geolocation.getCurrentPosition((position) => {
      if (request !== locationRequest.current) return;
      chooseOrigin({ label: 'My location', coordinates: [position.coords.longitude, position.coords.latitude] });
    }, (error) => {
      if (request !== locationRequest.current) return;
      setFindingOrigin(false); setLocationMessage(error.code === error.PERMISSION_DENIED ? 'Location permission was not given. Enter a starting point instead.' : 'Your location could not be found. Enter a starting point instead.');
    }, { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 });
  }
  async function selectCentre(centre: CareCentre) {
    setMobileView('map');
    centreRequest.current?.abort(); setSelectedId(centre.id); setCentreMatches(null); setCentreMessage('');
    if (resolved[centre.id]) { setLocatingId(null); return; }
    const controller = new AbortController(); centreRequest.current = controller;
    const timeout = setTimeout(() => controller.abort(), 12000);
    setLocatingId(centre.id);
    try {
      const features = await photon(`${centre.name.split('—')[0].trim()} ${centre.city}`, controller.signal);
      if (centreRequest.current !== controller || controller.signal.aborted) return;
      const matches = features.filter((feature) => categoryOf(feature) || /clinic|doctors/.test(feature.properties?.osm_value ?? '')).map(matchOf).filter((match): match is SearchMatch => Boolean(match));
      const exact = matches[0];
      const nameKey = (value: string) => value.toLowerCase().replace(/[^a-z0-9]/g, '');
      const postcode = centre.address.match(/\b\d{6}\b/)?.[0];
      if (matches.length === 1 && nameKey(exact.label) === nameKey(centre.name)
        && (exact.address.toLowerCase().includes(centre.city.toLowerCase()) || Boolean(postcode && exact.address.includes(postcode)))) {
        chooseCentreMatch(centre, exact);
      } else {
        setCentreMatches({ centre, matches });
        if (!matches.length) setCentreMessage('This centre’s map pin could not be found. Its Directions link uses the listed address.');
      }
    } catch { if (centreRequest.current === controller) setCentreMessage('The centre’s map location could not load. Use its Directions link or try the centre again.'); }
    finally { clearTimeout(timeout); if (centreRequest.current === controller) setLocatingId(null); }
  }
  function chooseCentreMatch(centre: CareCentre, match: SearchMatch) {
    setResolved((current) => ({ ...current, [centre.id]: { id: centre.id, name: centre.name, coordinates: match.coordinates, category: 'palliative' } }));
    setCentreMatches(null); setCentreMessage('');
  }
  function selectPoint(id: string) {
    setMobileView('map');
    centreRequest.current?.abort(); setLocatingId(null); setSelectedId(id); setCentreMatches(null); setCentreMessage('');
  }
  function clearOrigin() {
    originRequest.current?.abort(); locationRequest.current += 1;
    setOrigin(null); setStartText(''); setOriginMatches([]); setFindingOrigin(false); setLocationMessage('');
  }
  function clearDestination() {
    centreRequest.current?.abort(); setSelectedId(null); setCentreMatches(null); setCentreMessage(''); setLocatingId(null);
  }

  return <section className="cnm-root" aria-label="Find care">
    <div className="cnm-toolbar">
      <div className="cnm-topline"><h1>Find palliative care</h1><div className="cnm-categories" role="group" aria-label="Type of care">
        {(['palliative', 'hospital', 'all'] as const).map((value) => <button type="button" key={value} aria-pressed={category === value} onClick={() => { clearDestination(); setCategory(value); }}>{value === 'all' ? 'All' : value === 'palliative' ? 'Palliative care' : 'Hospitals'}</button>)}
      </div></div>
      <form className="cnm-start-row" onSubmit={searchOrigin}>
        <label>Starting point<input ref={startInput} value={startText} onChange={(event) => setStartText(event.target.value)} placeholder="City, area or address" autoComplete="off" /></label>
        <button type="submit" className="cnm-btn cnm-btn-secondary" disabled={findingOrigin}>Find place</button>
        <button type="button" className="cnm-btn cnm-btn-primary" onClick={useLocation} disabled={findingOrigin}>Use my location</button>
        {homeAddress?.trim() && <button type="button" className="cnm-btn cnm-btn-secondary" onClick={() => { setStartText(homeAddress.trim()); startInput.current?.focus(); }}>Use home address</button>}
      </form>
      {findingOrigin && <p className="cnm-message" role="status">Finding your starting point…</p>}
      {locationMessage && <p className="cnm-message is-error" role="alert">{locationMessage}</p>}
      {originMatches.length > 0 && <ul className="cnm-location-matches" aria-label="Choose a starting point">{originMatches.map((match) => <li key={match.id}><button type="button" onClick={() => chooseOrigin(match)}><strong>{match.label}</strong><span>{match.address}</span></button></li>)}</ul>}
      {origin && <div className="cnm-origin-line"><span>From {origin.label}</span><button type="button" onClick={clearOrigin}>Clear starting point</button></div>}
      <div className="cnm-search-row">
        <label>Search centres<input type="search" value={query} onChange={(event) => { clearDestination(); setQuery(event.target.value); }} placeholder="City or centre name" /></label>
        <label>Services<select value={service} onChange={(event) => { clearDestination(); setService(event.target.value); }}><option value="">All services</option><option>Clinic visits</option><option>Home care</option><option>Inpatient care</option></select></label>
      </div>
    </div>

    <div className="cnm-view-tabs" role="group" aria-label="Find care view">
      <button type="button" aria-pressed={mobileView === 'map'} onClick={() => setMobileView('map')}>Map</button>
      <button type="button" aria-pressed={mobileView === 'centres'} onClick={() => setMobileView('centres')}>Centres ({centres.length + filteredPlaces.length})</button>
    </div>
    <div className="cnm-results" data-mobile-view={mobileView}>
      <div className="cnm-map-panel">
        {selectedName && <div className="cnm-route-panel" aria-live="polite">
          <div className="cnm-route-heading"><strong>{selectedId && pinNumbers[selectedId] && <span className="care-centre-pin-number">{pinNumbers[selectedId]}</span>}{selectedName}</strong><button type="button" aria-label="Clear destination" onClick={clearDestination}>×</button></div>
          <address className="cnm-selected-address">{selectedCentre?.address ?? selectedPlace?.address}</address>
          {centreMatches && centreMatches.matches.length > 0 && <div className="cnm-centre-matches"><p>Choose the matching map location</p>{centreMatches.matches.map((match) => <button type="button" key={match.id} onClick={() => chooseCentreMatch(centreMatches.centre, match)}><strong>{match.label}</strong><span>{match.address}</span></button>)}</div>}
          {centreMessage && <p className="cnm-message is-error">{centreMessage}</p>}
          {locatingId && <p className="cnm-message">Finding the centre…</p>}
          {selectedPoint && origin && routeStatus === 'loading' && <p className="cnm-message">Finding a road route…</p>}
          {route && routeStatus === 'ready' && <p className="cnm-route-summary"><strong>{(route.distance / 1000).toFixed(1)} km</strong><span>About {Math.max(1, Math.round(route.duration / 60))} min driving · no live traffic</span></p>}
          {routeStatus === 'error' && <p className="cnm-message is-error">A road route could not load. <button type="button" onClick={() => setRouteRetry((value) => value + 1)}>Try again</button></p>}
          <div className="cnm-selected-actions">{selectedCentre?.phone && <a className="cnm-call-link" href={`tel:${selectedCentre.phone}`}>Call centre</a>}{selectedPoint && <a className="cnm-directions-link" href={directionsUrl(selectedPoint, origin)} target="_blank" rel="noopener noreferrer">Directions ↗</a>}</div>
        </div>}
        <PalliativeMap points={points} origin={origin} selectedId={selectedId} route={route} searchCentre={origin} onSelect={selectPoint} />
      </div>
      <div className="cnm-list" role="region" aria-label="Care centres" tabIndex={0}>
        <div className="cnm-list-heading"><span>{centres.length + filteredPlaces.length} centres</span>{(query || service || category !== 'palliative') && <button type="button" onClick={() => { clearDestination(); setQuery(''); setService(''); setCategory('palliative'); }}>Clear filters</button>}</div>
        <CareDirectory centres={centres} selectedId={selectedId} locatingId={locatingId} onSelect={selectCentre} pinNumbers={pinNumbers} distances={distances} />
        {filteredPlaces.length > 0 && <div className="cnm-nearby-list"><h3>Nearby map listings</h3>{filteredPlaces.map((place) => <article key={place.id} className={`cnm-place${selectedId === place.id ? ' is-selected' : ''}`}>
          <button type="button" className="cnm-place-select" aria-pressed={selectedId === place.id} onClick={() => selectPoint(place.id)}><span>{pinNumbers[place.id] && <span className="care-centre-pin-number">{pinNumbers[place.id]}</span>}{place.category === 'palliative' ? 'Palliative care' : 'Hospital'}</span><strong>{place.name}</strong></button>
          <address>{place.address || 'Address not listed'}</address>
          <p>{place.distanceKm.toFixed(1)} km away in a straight line</p>
          <div className="cnm-place-actions"><a href={directionsUrl(place, origin)} target="_blank" rel="noopener noreferrer">Directions ↗</a><a href={place.sourceUrl} target="_blank" rel="noopener noreferrer">Source ↗</a></div>
        </article>)}</div>}
        {nearbyStatus === 'loading' && <p className="cnm-message" role="status">Finding nearby care…</p>}
        {nearbyStatus === 'error' && <p className="cnm-message is-error">Nearby search could not load. <button type="button" onClick={() => setNearbyRetry((value) => value + 1)}>Try again</button></p>}
        {!centres.length && !filteredPlaces.length && nearbyStatus !== 'loading' && <p className="cnm-empty">No matching centres. Try another name, area or service.</p>}
      </div>
    </div>
    <details className="cnm-sources"><summary>Sources and contact details</summary><p>Pallium India listings checked {DIRECTORY_CHECKED_ON}. Call to confirm services before travelling. Map listings do not confirm palliative services or availability.</p><p>Your starting point is not saved. Place searches use Photon; road routes send the two locations to OSRM.</p><a href="https://palliumindia.org/clinics" target="_blank" rel="noopener noreferrer">Pallium India care listings ↗</a></details>
  </section>;
}
