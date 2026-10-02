'use client';

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { CareDirectory } from './care-directory';
import { CARE_CENTRES, DIRECTORY_CHECKED_ON, filterCareCentres, type CareCentre } from './care-directory-data';
import { CareCentreReports, type PublicCentreReport } from './care-centre-reports';
import { aggregateCentreReports } from './care-centre-report-state';
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
type ReportWithCoordinates = PublicCentreReport & {
  coordinates?: unknown;
  latitude?: string | number | null;
  longitude?: string | number | null;
};
type CommunityCentreGroup = {
  id: string;
  name: string;
  district: string;
  address: string;
  reports: PublicCentreReport[];
  summary: ReturnType<typeof aggregateCentreReports>;
  coordinates: [number, number] | null;
  listedCentre?: CareCentre;
};
const PHOTON_URL = 'https://photon.komoot.io/api/';
const FACILITY_KEYS = new Set(['amenity', 'healthcare', 'building', 'office', 'social_facility']);

function validCoordinates(value: unknown): value is [number, number] {
  return Array.isArray(value) && value.length === 2 && value.every(Number.isFinite) && Math.abs(value[0]) <= 180 && Math.abs(value[1]) <= 90;
}
function numericCoordinate(value: unknown): number | null {
  const number = typeof value === 'number' ? value : typeof value === 'string' && value.trim() ? Number(value) : NaN;
  return Number.isFinite(number) ? number : null;
}
function reportCoordinates(report: PublicCentreReport): [number, number] | null {
  const record = report as ReportWithCoordinates;
  if (validCoordinates(record.coordinates)) return record.coordinates;
  const latitude = numericCoordinate(record.latitude);
  const longitude = numericCoordinate(record.longitude);
  return latitude !== null && longitude !== null && validCoordinates([longitude, latitude]) ? [longitude, latitude] : null;
}
function reportName(report: PublicCentreReport): string {
  return report.hospitalName?.trim() || report.centreName?.trim() || report.centreId;
}
function districtKey(value: string | undefined): string {
  const normalized = (value ?? '').trim().toLocaleLowerCase().replace(/[()]/g, ' ').replace(/\s+/g, ' ');
  if (normalized.includes('belagavi') || normalized.includes('belgaum')) return 'belagavi';
  if (normalized === 'tumkur' || normalized === 'tumakuru') return 'tumkur';
  if (normalized === 'bangalore' || normalized === 'bengaluru') return 'bengaluru urban';
  return normalized;
}
function reportMorphineStatus(summary: ReturnType<typeof aggregateCentreReports>): 'available' | 'unavailable' | 'unspecified' {
  if (summary.status === 'available') return 'available';
  if (summary.status === 'negative') return 'unavailable';
  return 'unspecified';
}
function reportStatusLabel(summary: ReturnType<typeof aggregateCentreReports>): string {
  if (summary.status === 'available') return 'Recent reports say oral morphine was available';
  if (summary.status === 'negative') return 'Recent reports say oral morphine was unavailable';
  if (summary.status === 'mixed') return 'Recent reports are mixed';
  if (summary.status === 'outdated') return 'The latest report is older than 90 days';
  if (summary.status === 'unknown') return 'Recent reports did not record oral morphine';
  return 'No community reports yet';
}
function addressDirectionsUrl(name: string, address: string, origin: CareMapOrigin | null): string {
  const params = new URLSearchParams({ api: '1', destination: `${name}, ${address}, India` });
  if (origin) params.set('origin', `${origin.coordinates[1]},${origin.coordinates[0]}`);
  return `https://www.google.com/maps/dir/?${params}`;
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
  const [district, setDistrict] = useState('');
  const [morphine, setMorphine] = useState('');
  const [category, setCategory] = useState<Category>('all');
  const [mobileView, setMobileView] = useState<'map' | 'centres'>('map');
  const [startText, setStartText] = useState('');
  const [origin, setOrigin] = useState<CareMapOrigin | null>(null);
  const [originMatches, setOriginMatches] = useState<SearchMatch[]>([]);
  const [findingOrigin, setFindingOrigin] = useState(false);
  const [locationMessage, setLocationMessage] = useState('');
  const [places, setPlaces] = useState<Place[]>([]);
  const [nearbyStatus, setNearbyStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle');
  const [nearbyRetry, setNearbyRetry] = useState(0);
  const [communityReports, setCommunityReports] = useState<PublicCentreReport[]>([]);
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

  const onReportsChanged = useCallback((next: readonly PublicCentreReport[]) => {
    setCommunityReports([...next]);
  }, []);

  const reportGroups = useMemo<CommunityCentreGroup[]>(() => {
    const groups = new Map<string, { listedCentre?: CareCentre; reports: PublicCentreReport[] }>();
    for (const report of communityReports) {
      const listedCentre = CARE_CENTRES.find((centre) => centre.id === report.centreId);
      if (listedCentre && listedCentre.state !== 'Karnataka') continue;
      const id = listedCentre?.id ?? report.centreId;
      if (!id) continue;
      const group = groups.get(id) ?? { listedCentre, reports: [] };
      group.reports.push(report);
      if (!group.listedCentre && listedCentre) group.listedCentre = listedCentre;
      groups.set(id, group);
    }
    return [...groups.entries()].map(([id, group]) => {
      const listedCentre = group.listedCentre;
      const first = group.reports[0];
      const coordinates = group.reports.map(reportCoordinates).find((value): value is [number, number] => Boolean(value)) ?? null;
      return {
        id,
        name: listedCentre?.name ?? reportName(first!),
        district: listedCentre?.district ?? first?.district?.trim() ?? '',
        address: listedCentre?.address ?? first?.address?.trim() ?? '',
        reports: group.reports,
        summary: aggregateCentreReports(group.reports, new Date()),
        coordinates,
        listedCentre,
      };
    });
  }, [communityReports]);

  const districtOptions = useMemo(() => {
    const labels = new Map<string, string>();
    for (const centre of CARE_CENTRES) {
      if (centre.state !== 'Karnataka' || !centre.district) continue;
      labels.set(districtKey(centre.district), centre.district);
    }
    for (const group of reportGroups) {
      if (group.district && !labels.has(districtKey(group.district))) labels.set(districtKey(group.district), group.district);
    }
    return [...labels.values()].sort((a, b) => a.localeCompare(b));
  }, [reportGroups]);

  const visibleCommunityHospitals = useMemo(() => {
    const terms = query.trim().toLocaleLowerCase().replace(/bangalore/g, 'bengaluru').split(/\s+/).filter(Boolean);
    const morphineFilter = morphine || '';
    return reportGroups.filter((group) => {
      if (group.listedCentre || category === 'palliative' || (service && service !== '')) return false;
      if (district && districtKey(group.district) !== districtKey(district)) return false;
      const searchable = `${group.name} ${group.district} ${group.address}`.toLocaleLowerCase().replace(/bangalore/g, 'bengaluru');
      if (!terms.every((term) => searchable.includes(term))) return false;
      if (morphineFilter && reportMorphineStatus(group.summary) !== morphineFilter) return false;
      return true;
    });
  }, [category, district, morphine, query, reportGroups, service]);

  const resolvedWithReports = useMemo(() => {
    const next = { ...resolved };
    for (const group of reportGroups) {
      if (!group.coordinates || next[group.id]) continue;
      next[group.id] = {
        id: group.id,
        name: group.name,
        coordinates: group.coordinates,
        category: group.listedCentre ? 'palliative' : 'hospital',
        source: group.listedCentre ? 'listed' : 'community',
      };
    }
    return next;
  }, [reportGroups, resolved]);

  const centres = useMemo(() => {
    const matches = filterCareCentres(query, 'Karnataka', service, morphine).filter((centre) => (!district || districtKey(centre.district) === districtKey(district)) && (category !== 'hospital' || /hospital|institute|aiims/i.test(centre.name))).map((centre) => ({ ...centre, coordinates: resolvedWithReports[centre.id]?.coordinates }));
    return matches.sort((a, b) => origin ? (a.coordinates ? distanceKm(origin.coordinates, a.coordinates) : Infinity) - (b.coordinates ? distanceKm(origin.coordinates, b.coordinates) : Infinity) : Number(Boolean(b.coordinates)) - Number(Boolean(a.coordinates)));
  }, [query, service, district, morphine, category, origin, resolvedWithReports]);
  const communityPoints = useMemo<CareMapPoint[]>(() => visibleCommunityHospitals.filter((group) => group.coordinates).map((group) => ({
    id: group.id, name: group.name, coordinates: group.coordinates as [number, number], category: 'hospital', source: 'community',
  })), [visibleCommunityHospitals]);
  const filteredPlaces = useMemo(() => places.filter((place) => (!query.trim() || `${place.name} ${place.address}`.toLowerCase().includes(query.trim().toLowerCase())) && (category === 'all' || place.category === category) && !service && !district && !morphine), [places, query, category, service, district, morphine]);
  const points = useMemo(() => [...centres.flatMap((centre) => resolvedWithReports[centre.id] ? [{ ...resolvedWithReports[centre.id], source: resolvedWithReports[centre.id].source ?? 'listed' }] : []), ...communityPoints, ...filteredPlaces], [centres, resolvedWithReports, communityPoints, filteredPlaces]);
  const pinNumbers = useMemo(() => Object.fromEntries(points.map((point, index) => [point.id, index + 1])), [points]);
  const distances = useMemo(() => origin ? Object.fromEntries(points.map((point) => [point.id, distanceKm(origin.coordinates, point.coordinates)])) : {}, [points, origin]);
  const selectedCentre = CARE_CENTRES.find((centre) => centre.id === selectedId);
  const selectedPoint = (selectedId ? resolvedWithReports[selectedId] : undefined) ?? places.find((place) => place.id === selectedId) ?? null;
  const selectedPlace = places.find((place) => place.id === selectedId);
  const selectedReportGroup = reportGroups.find((group) => group.id === selectedId);
  const selectedName = selectedCentre?.name ?? selectedReportGroup?.name ?? selectedPoint?.name;
  const selectedAddress = selectedCentre?.address ?? selectedReportGroup?.address ?? selectedPlace?.address ?? '';
  const selectedPhone = selectedCentre?.phoneNumbers?.[0] ?? selectedCentre?.phone;
  const visibleSelectedPoint = points.find((point) => point.id === selectedId) ?? null;

  useEffect(() => () => { originRequest.current?.abort(); centreRequest.current?.abort(); locationRequest.current += 1; }, []);

  useEffect(() => {
    const location = new URLSearchParams(window.location.search).get('location')?.trim().slice(0, 160);
    if (!location) return;
    setStartText(location);
    void findOrigin(location);
  }, []);

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
          if (!match || !kind || !/karnataka/i.test(feature.properties?.state ?? match.address)) continue;
          const distance = distanceKm(origin.coordinates, match.coordinates);
          if (distance > 30) continue;
          found.set(match.id, { id: match.id, name: match.label, coordinates: match.coordinates, category: kind, source: 'openstreetmap', address: match.address, sourceUrl: match.sourceUrl, distanceKm: distance });
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
    await findOrigin(startText.trim());
  }
  async function findOrigin(location: string) {
    originRequest.current?.abort(); locationRequest.current += 1;
    const controller = new AbortController(); originRequest.current = controller;
    const timeout = setTimeout(() => controller.abort(), 12000);
    setFindingOrigin(true); setOriginMatches([]); setLocationMessage('');
    try {
      const matches = (await photon(location, controller.signal)).map(matchOf).filter((match): match is SearchMatch => Boolean(match));
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
    if (resolvedWithReports[centre.id]) { setLocatingId(null); return; }
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
    setResolved((current) => ({ ...current, [centre.id]: { id: centre.id, name: centre.name, coordinates: match.coordinates, category: 'palliative', source: 'listed' } }));
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
      <div className="cnm-topline"><h1>Care in Karnataka</h1><div className="cnm-categories" role="group" aria-label="Type of care">
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
      <div className="cnm-search-row cnm-karnataka-filters">
        <label>Search centres<input type="search" value={query} onChange={(event) => { clearDestination(); setQuery(event.target.value); }} placeholder="City or centre name" /></label>
        <label>Services<select value={service} onChange={(event) => { clearDestination(); setService(event.target.value); }}><option value="">All services</option><option>Clinic visits</option><option>Home care</option><option>Inpatient care</option></select></label>
        <label>District<select value={district} onChange={(event) => { clearDestination(); setDistrict(event.target.value); }}><option value="">All districts</option>{districtOptions.map((name) => <option key={name}>{name}</option>)}</select></label>
        <label>Morphine<select value={morphine} onChange={(event) => { clearDestination(); setMorphine(event.target.value); }}><option value="">All listings</option><option value="available">Listed as available</option><option value="unavailable">Listed as unavailable</option><option value="unspecified">Not listed</option></select></label>
      </div>
    </div>

    <div className="cnm-view-tabs" role="group" aria-label="Find care view">
      <button type="button" aria-pressed={mobileView === 'map'} onClick={() => setMobileView('map')}>Map</button>
      <button type="button" aria-pressed={mobileView === 'centres'} onClick={() => setMobileView('centres')}>Centres ({centres.length + visibleCommunityHospitals.length + filteredPlaces.length})</button>
    </div>
    <div className="cnm-results" data-mobile-view={mobileView}>
      <div className="cnm-map-panel">
        {selectedName && <div className="cnm-route-panel" aria-live="polite">
          <div className="cnm-route-heading"><strong>{selectedId && pinNumbers[selectedId] && <span className="care-centre-pin-number">{pinNumbers[selectedId]}</span>}{selectedName}</strong><button type="button" aria-label="Clear destination" onClick={clearDestination}>×</button></div>
          <address className="cnm-selected-address">{selectedAddress || 'Address not listed'}</address>
          {centreMatches && centreMatches.matches.length > 0 && <div className="cnm-centre-matches"><p>Choose the matching map location</p>{centreMatches.matches.map((match) => <button type="button" key={match.id} onClick={() => chooseCentreMatch(centreMatches.centre, match)}><strong>{match.label}</strong><span>{match.address}</span></button>)}</div>}
          {centreMessage && <p className="cnm-message is-error">{centreMessage}</p>}
          {locatingId && <p className="cnm-message">Finding the centre…</p>}
          {selectedReportGroup && <div className="cnm-report-enrichment"><strong>Community reports</strong><span>{selectedReportGroup.summary.recentCount} recent · {reportStatusLabel(selectedReportGroup.summary)}</span></div>}
          {selectedPoint && origin && routeStatus === 'loading' && <p className="cnm-message">Finding a road route…</p>}
          {route && routeStatus === 'ready' && <p className="cnm-route-summary"><strong>{(route.distance / 1000).toFixed(1)} km</strong><span>About {Math.max(1, Math.round(route.duration / 60))} min driving · no live traffic</span></p>}
          {routeStatus === 'error' && <p className="cnm-message is-error">A road route could not load. <button type="button" onClick={() => setRouteRetry((value) => value + 1)}>Try again</button></p>}
          <div className="cnm-selected-actions">{selectedPhone && <a className="cnm-call-link" href={`tel:${selectedPhone.replace(/[^+\d]/g, '')}`}>Call centre</a>}{selectedPoint ? <a className="cnm-directions-link" href={directionsUrl(selectedPoint, origin)} target="_blank" rel="noopener noreferrer">Directions ↗</a> : selectedAddress && <a className="cnm-directions-link" href={addressDirectionsUrl(selectedName ?? '', selectedAddress, origin)} target="_blank" rel="noopener noreferrer">Directions to address ↗</a>}</div>
        </div>}
        <PalliativeMap points={points} origin={origin} selectedId={selectedId} route={route} searchCentre={origin} onSelect={selectPoint} />
      </div>
      <div className="cnm-list" role="region" aria-label="Care centres" tabIndex={0}>
        <div className="cnm-list-heading"><span>{centres.length + visibleCommunityHospitals.length + filteredPlaces.length} centres</span>{(query || service || district || morphine || category !== 'all') && <button type="button" onClick={() => { clearDestination(); setQuery(''); setService(''); setDistrict(''); setMorphine(''); setCategory('all'); }}>Clear filters</button>}</div>
        <CareDirectory centres={centres} selectedId={selectedId} locatingId={locatingId} onSelect={selectCentre} pinNumbers={pinNumbers} distances={distances} />
        {visibleCommunityHospitals.length > 0 && <section className="cnm-community-hospitals" aria-labelledby="cnm-community-hospitals-title">
          <h3 id="cnm-community-hospitals-title">Community hospitals</h3>
          {visibleCommunityHospitals.map((group) => <article key={group.id} className={`cnm-community-hospital${selectedId === group.id ? ' is-selected' : ''}`}>
            <button type="button" className="cnm-community-select" aria-pressed={selectedId === group.id} onClick={() => selectPoint(group.id)}>
              <span>Community report · {group.coordinates ? 'Mapped' : 'Address only'}</span><strong>{group.name}</strong>
            </button>
            <address>{group.address || 'Address not listed'}{group.district && <span>{group.district}</span>}</address>
            <p className="cnm-community-status">{group.summary.recentCount} recent report{group.summary.recentCount === 1 ? '' : 's'} · {reportStatusLabel(group.summary)}</p>
            <div className="cnm-place-actions">
              {group.coordinates ? <a href={directionsUrl({ id: group.id, name: group.name, coordinates: group.coordinates, category: 'hospital', source: 'community' }, origin)} target="_blank" rel="noopener noreferrer">Directions ↗</a> : group.address && <a href={addressDirectionsUrl(group.name, group.address, origin)} target="_blank" rel="noopener noreferrer">Directions to address ↗</a>}
              {group.coordinates && <button type="button" onClick={() => selectPoint(group.id)}>Show on map</button>}
            </div>
          </article>)}
        </section>}
        {filteredPlaces.length > 0 && <div className="cnm-nearby-list"><h3>Nearby map listings</h3>{filteredPlaces.map((place) => <article key={place.id} className={`cnm-place${selectedId === place.id ? ' is-selected' : ''}`}>
          <button type="button" className="cnm-place-select" aria-pressed={selectedId === place.id} onClick={() => selectPoint(place.id)}><span>{pinNumbers[place.id] && <span className="care-centre-pin-number">{pinNumbers[place.id]}</span>}{place.category === 'palliative' ? 'Palliative care' : 'Hospital'}</span><strong>{place.name}</strong></button>
          <address>{place.address || 'Address not listed'}</address>
          <p>{place.distanceKm.toFixed(1)} km away in a straight line</p>
          <div className="cnm-place-actions"><a href={directionsUrl(place, origin)} target="_blank" rel="noopener noreferrer">Directions ↗</a><a href={place.sourceUrl} target="_blank" rel="noopener noreferrer">Source ↗</a></div>
        </article>)}</div>}
        {nearbyStatus === 'loading' && <p className="cnm-message" role="status">Finding nearby care…</p>}
        {nearbyStatus === 'error' && <p className="cnm-message is-error">Nearby search could not load. <button type="button" onClick={() => setNearbyRetry((value) => value + 1)}>Try again</button></p>}
        {!centres.length && !visibleCommunityHospitals.length && !filteredPlaces.length && nearbyStatus !== 'loading' && <p className="cnm-empty">No matching centres. Try another name, area or service.</p>}
      </div>
    </div>
    <section className="cnm-community-reports" aria-label="Community reports">
      <CareCentreReports onReportsChanged={onReportsChanged} />
    </section>
    <details className="cnm-sources"><summary>Sources and contact details</summary><p>Pallium India listings checked {DIRECTORY_CHECKED_ON}. The directory says “morphine”; it does not confirm the oral formulation or today’s stock. Call before travelling.</p><p>Your starting point is not saved. Place searches use Photon; road routes send the two locations to OSRM.</p><a href="https://palliumindia.org/clinics/karnataka" target="_blank" rel="noopener noreferrer">Pallium India Karnataka directory ↗</a></details>
  </section>;
}
