'use client';

import { useEffect, useMemo, useRef, useState } from 'react';

export type CareNearMeProps = {
  audience: 'patient' | 'family';
  patientName: string;
};

type City = {
  name: string;
  lat: number;
  lon: number;
};

const CITIES: City[] = [
  { name: 'Hyderabad', lat: 17.385, lon: 78.4867 },
  { name: 'Bengaluru', lat: 12.9716, lon: 77.5946 },
  { name: 'Chennai', lat: 13.0827, lon: 80.2707 },
  { name: 'Mumbai', lat: 19.076, lon: 72.8777 },
  { name: 'Delhi', lat: 28.6139, lon: 77.209 },
  { name: 'Kolkata', lat: 22.5726, lon: 88.3639 },
  { name: 'Thiruvananthapuram', lat: 8.5241, lon: 76.9366 },
];

type Category = 'palliative' | 'hospital' | 'clinic';

type Place = {
  id: string;
  name: string;
  category: Category;
  lat: number;
  lon: number;
  distanceKm: number;
  address: string;
  phone?: string;
  website?: string;
};

type Origin = {
  lat: number;
  lon: number;
  label: string;
};

const CATEGORY_LABELS: Record<Category, string> = {
  palliative: 'Palliative & hospice',
  hospital: 'Hospital',
  clinic: 'Clinic / doctors',
};

const CATEGORY_COLORS: Record<Category, string> = {
  palliative: '#0c5c3f',
  hospital: '#1a5f9e',
  clinic: '#6b5a3a',
};

const RADII = [5000, 15000, 30000];
const MAX_DISTANCE_KM = 30;
const PHOTON_URL = 'https://photon.komoot.io/api/';

// OpenStreetMap's Overpass API answers HTTP 406 to requests sent from web
// browsers, so the search uses Photon, a geocoder over the same OpenStreetMap
// data that accepts browser requests and responds in a couple of seconds.
const PHOTON_QUERIES: Array<{ q: string; osmTag?: string }> = [
  { q: 'palliative care' },
  { q: 'hospice' },
  { q: 'hospital', osmTag: 'amenity:hospital' },
  { q: 'clinic', osmTag: 'amenity:clinic' },
  { q: 'doctor', osmTag: 'amenity:doctors' },
];

// Only facility-like features count; this keeps "Hospice Road" and similar
// street or place names out of the palliative results.
const FACILITY_KEYS = new Set(['amenity', 'healthcare', 'building', 'office', 'social_facility']);

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

type PhotonProperties = {
  osm_type?: string;
  osm_id?: number;
  osm_key?: string;
  osm_value?: string;
  name?: string;
  housenumber?: string;
  street?: string;
  locality?: string;
  district?: string;
  city?: string;
  postcode?: string;
};

type PhotonFeature = {
  geometry?: { coordinates?: [number, number] };
  properties?: PhotonProperties;
};

function classify(key: string | undefined, value: string | undefined, name: string): Category | null {
  if (!key || !FACILITY_KEYS.has(key)) return null;
  if (value === 'hospice' || value === 'palliative' || /palliat|hospice/i.test(name)) return 'palliative';
  if (value === 'hospital') return 'hospital';
  if (value === 'clinic' || value === 'doctors' || value === 'doctor') return 'clinic';
  return null;
}

function buildAddress(p: PhotonProperties): string {
  const line1 = [p.housenumber, p.street].filter(Boolean).join(' ');
  const parts = [line1, p.locality ?? p.district, p.city, p.postcode].filter(
    (part): part is string => Boolean(part),
  );
  const unique = parts.filter((part, i) => parts.indexOf(part) === i);
  return unique.length > 0 ? unique.join(', ') : 'Address not listed on OpenStreetMap';
}

function parseFeatures(features: PhotonFeature[], origin: Origin): Place[] {
  const seen = new Set<string>();
  const places: Place[] = [];

  for (const feature of features) {
    const coords = feature.geometry?.coordinates;
    const props = feature.properties ?? {};
    if (!coords || !props.name) continue;
    const [lon, lat] = coords;

    const category = classify(props.osm_key, props.osm_value, props.name);
    if (!category) continue;

    const distanceKm = haversineKm(origin.lat, origin.lon, lat, lon);
    if (distanceKm > MAX_DISTANCE_KM) continue;

    const dedupeKey = `${props.name.toLowerCase()}|${lat.toFixed(3)}|${lon.toFixed(3)}`;
    if (seen.has(dedupeKey)) continue;
    seen.add(dedupeKey);

    places.push({
      id: `${props.osm_type ?? 'osm'}/${props.osm_id ?? dedupeKey}`,
      name: props.name,
      category,
      lat,
      lon,
      distanceKm,
      address: buildAddress(props),
    });
  }

  places.sort((a, b) => a.distanceKm - b.distanceKm);
  return places.slice(0, 40);
}

async function fetchPhoton(
  query: { q: string; osmTag?: string },
  origin: Origin,
  signal: AbortSignal,
): Promise<PhotonFeature[]> {
  // Two decimals is roughly 1 km: close enough to find nearby care, without
  // sending the exact location to a third-party service.
  const params = new URLSearchParams({
    q: query.q,
    lat: origin.lat.toFixed(2),
    lon: origin.lon.toFixed(2),
    limit: '50',
    lang: 'en',
  });
  if (query.osmTag) params.append('osm_tag', query.osmTag);

  const response = await fetch(`${PHOTON_URL}?${params.toString()}`, { signal });
  if (!response.ok) throw new Error(`Photon request failed: ${response.status}`);
  const json = (await response.json()) as { features?: PhotonFeature[] };
  return json.features ?? [];
}

type SearchResult = {
  places: Place[];
  radius: number;
};

async function searchNear(origin: Origin, signal: AbortSignal): Promise<SearchResult> {
  const settled = await Promise.allSettled(PHOTON_QUERIES.map((query) => fetchPhoton(query, origin, signal)));
  if (settled.every((result) => result.status === 'rejected')) {
    throw new Error('All place searches failed');
  }
  const features = settled.flatMap((result) => (result.status === 'fulfilled' ? result.value : []));
  const places = parseFeatures(features, origin);
  const furthest = places.reduce((max, place) => Math.max(max, place.distanceKm), 0);
  const radius = RADII.find((r) => furthest <= r / 1000) ?? RADII[RADII.length - 1];
  return { places, radius };
}

type Stage = 'consent' | 'loading' | 'results' | 'error';

type FilterKey = 'all' | Category;

export function CareNearMe(props: CareNearMeProps): React.JSX.Element {
  const { audience, patientName } = props;
  const firstName = patientName.split(' ')[0];
  const heading = audience === 'patient' ? 'Care near you' : `Care near ${firstName}`;

  const [stage, setStage] = useState<Stage>('consent');
  const [selectedCityIndex, setSelectedCityIndex] = useState(0);
  const [locationError, setLocationError] = useState<string | null>(null);
  const [origin, setOrigin] = useState<Origin | null>(null);
  const [places, setPlaces] = useState<Place[]>([]);
  const [radiusUsed, setRadiusUsed] = useState<number>(RADII[0]);
  const [filter, setFilter] = useState<FilterKey>('all');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [selectedPlaceId, setSelectedPlaceId] = useState<string | null>(null);

  const abortRef = useRef<AbortController | null>(null);
  const mapContainerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<any>(null);
  const leafletRef = useRef<any>(null);
  const markersRef = useRef<any[]>([]);

  const geoSupported = typeof navigator !== 'undefined' && !!navigator.geolocation;

  const runSearch = async (o: Origin) => {
    setOrigin(o);
    setStage('loading');
    setErrorMessage(null);
    setSelectedPlaceId(null);

    const controller = new AbortController();
    abortRef.current = controller;
    const timeoutId = setTimeout(() => controller.abort(), 15000);

    try {
      const { places: found, radius } = await searchNear(o, controller.signal);
      setPlaces(found);
      setRadiusUsed(radius);
      setFilter('all');
      setStage('results');
    } catch {
      setStage('error');
    } finally {
      clearTimeout(timeoutId);
    }
  };

  const handleUseLocation = () => {
    if (!geoSupported) {
      setLocationError('Geolocation is not supported on this device. Choose a city instead.');
      return;
    }
    setLocationError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        runSearch({
          lat: position.coords.latitude,
          lon: position.coords.longitude,
          label: 'your location',
        });
      },
      (error) => {
        if (error.code === error.PERMISSION_DENIED) {
          setLocationError('Location permission was not given. Choose a city instead.');
        } else {
          setLocationError('Your location could not be found. Choose a city instead.');
        }
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 }
    );
  };

  const handleChooseCity = () => {
    const city = CITIES[selectedCityIndex];
    runSearch({ lat: city.lat, lon: city.lon, label: city.name });
  };

  const handleChangeLocation = () => {
    if (abortRef.current) abortRef.current.abort();
    setStage('consent');
    setPlaces([]);
    setOrigin(null);
    setSelectedPlaceId(null);
  };

  const handleTryAgain = () => {
    if (origin) runSearch(origin);
  };

  useEffect(() => {
    return () => {
      if (abortRef.current) abortRef.current.abort();
    };
  }, []);

  const counts = useMemo(() => {
    const c: Record<FilterKey, number> = { all: places.length, palliative: 0, hospital: 0, clinic: 0 };
    for (const p of places) c[p.category]++;
    return c;
  }, [places]);

  const filteredPlaces = useMemo(() => {
    if (filter === 'all') return places;
    return places.filter((p) => p.category === filter);
  }, [places, filter]);

  // Set up map once results are shown.
  useEffect(() => {
    if (stage !== 'results' || !mapContainerRef.current) return;
    let cancelled = false;

    (async () => {
      const L = (await import('leaflet')).default;
      if (cancelled || !mapContainerRef.current) return;
      leafletRef.current = L;

      if (!mapRef.current) {
        const map = L.map(mapContainerRef.current, {
          zoomAnimation: false,
          fadeAnimation: false,
          markerZoomAnimation: false,
          scrollWheelZoom: false,
        });
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution:
            '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
        }).addTo(map);
        mapRef.current = map;
      }

      renderMarkers();
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [stage]);

  useEffect(() => {
    if (stage === 'results' && mapRef.current) {
      renderMarkers();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filteredPlaces]);

  useEffect(() => {
    return () => {
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
    };
  }, []);

  function renderMarkers() {
    const L = leafletRef.current;
    const map = mapRef.current;
    if (!L || !map || !origin) return;

    for (const m of markersRef.current) {
      map.removeLayer(m);
    }
    markersRef.current = [];

    const originIcon = L.divIcon({
      className: '',
      html: `<div class="cnm-marker cnm-marker-origin"></div>`,
      iconSize: [18, 18],
      iconAnchor: [9, 9],
    });
    const originMarker = L.marker([origin.lat, origin.lon], { icon: originIcon }).addTo(map);
    originMarker.bindPopup('You are here / search centre');
    markersRef.current.push(originMarker);

    const bounds: [number, number][] = [[origin.lat, origin.lon]];

    for (const place of filteredPlaces) {
      const color = CATEGORY_COLORS[place.category];
      const icon = L.divIcon({
        className: '',
        html: `<div class="cnm-marker" style="background:${color}"></div>`,
        iconSize: [16, 16],
        iconAnchor: [8, 8],
      });
      const marker = L.marker([place.lat, place.lon], { icon }).addTo(map);
      const dirUrl = `https://www.google.com/maps/dir/?api=1&destination=${place.lat},${place.lon}`;
      marker.bindPopup(
        `<strong>${escapeHtml(place.name)}</strong><br/>${CATEGORY_LABELS[place.category]}<br/>${place.distanceKm.toFixed(1)} km<br/><a href="${dirUrl}" target="_blank" rel="noopener noreferrer">Directions</a>`
      );
      (marker as any)._cnmId = place.id;
      markersRef.current.push(marker);
      bounds.push([place.lat, place.lon]);
    }

    if (bounds.length > 1) {
      map.fitBounds(bounds, { animate: false, padding: [24, 24] });
    } else {
      map.setView([origin.lat, origin.lon], 13, { animate: false });
    }
  }

  function escapeHtml(s: string): string {
    return s
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function handleShowOnMap(place: Place) {
    const map = mapRef.current;
    if (!map) return;
    map.setView([place.lat, place.lon], 16, { animate: false });
    setSelectedPlaceId(place.id);
    const marker = markersRef.current.find((m) => (m as any)._cnmId === place.id);
    if (marker) marker.openPopup();
  }

  const originLabel = origin?.label ?? '';

  return (
    <div className="cnm-root">
      {stage === 'consent' && (
        <div className="cnm-card cnm-consent">
          <h2 className="cnm-heading">{heading}</h2>
          <p className="cnm-text">
            Find palliative care, hospices, hospitals and clinics close by.
          </p>
          <button type="button" className="cnm-btn cnm-btn-primary" onClick={handleUseLocation}>
            Use my location
          </button>
          <div className="cnm-city-row">
            <label htmlFor="cnm-city-select" className="cnm-label">
              Or choose a city
            </label>
            <select
              id="cnm-city-select"
              className="cnm-select"
              value={selectedCityIndex}
              onChange={(e) => setSelectedCityIndex(Number(e.target.value))}
            >
              {CITIES.map((city, idx) => (
                <option key={city.name} value={idx}>
                  {city.name}
                </option>
              ))}
            </select>
            <button type="button" className="cnm-btn cnm-btn-secondary" onClick={handleChooseCity}>
              Search
            </button>
          </div>
          {locationError && (
            <p role="alert" className="cnm-alert">
              {locationError}
            </p>
          )}
          <p className="cnm-privacy">
            Your location stays in this browser. To find places, the map sends an approximate search area (to about 1 km) to Photon, a public OpenStreetMap search service. This demonstration does not store it.
          </p>
        </div>
      )}

      {stage === 'loading' && (
        <div className="cnm-card">
          <p role="status" className="cnm-status">
            Searching OpenStreetMap near {originLabel}…
          </p>
        </div>
      )}

      {stage === 'error' && (
        <div className="cnm-card">
          <p role="alert" className="cnm-alert">
            The place search is not responding. Check your connection and try again.
          </p>
          <button type="button" className="cnm-btn cnm-btn-primary" onClick={handleTryAgain}>
            Try again
          </button>
        </div>
      )}

      {stage === 'results' && (
        <>
          <div className="cnm-card cnm-results-header">
            <p className="cnm-results-summary">
              {places.length} places within {radiusUsed / 1000} km of {originLabel}
            </p>
            <button type="button" className="cnm-btn cnm-btn-secondary" onClick={handleChangeLocation}>
              Change location
            </button>
          </div>

          <div className="cnm-card cnm-filters" role="group" aria-label="Filter places">
            {(
              [
                ['all', `All (${counts.all})`],
                ['palliative', `Palliative & hospice (${counts.palliative})`],
                ['hospital', `Hospitals (${counts.hospital})`],
                ['clinic', `Clinics & doctors (${counts.clinic})`],
              ] as [FilterKey, string][]
            ).map(([key, label]) => (
              <button
                key={key}
                type="button"
                className="cnm-chip"
                aria-pressed={filter === key}
                onClick={() => setFilter(key)}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="cnm-results">
            <div className="cnm-list">
              {filteredPlaces.length === 0 && (
                <p className="cnm-empty">
                  No named places were found within 30 km on OpenStreetMap. Try another city.
                </p>
              )}
              {filteredPlaces.map((place) => (
                <div
                  key={place.id}
                  className={`cnm-list-item${selectedPlaceId === place.id ? ' cnm-list-item-selected' : ''}`}
                >
                  <div className="cnm-list-item-category">{CATEGORY_LABELS[place.category]}</div>
                  <div className="cnm-list-item-name">{place.name}</div>
                  <div className="cnm-list-item-distance">{place.distanceKm.toFixed(1)} km</div>
                  <div className="cnm-list-item-address">{place.address}</div>
                  {place.phone && (
                    <a className="cnm-list-item-phone" href={`tel:${place.phone}`}>
                      {place.phone}
                    </a>
                  )}
                  {place.website && (
                    <a
                      className="cnm-list-item-website"
                      href={place.website}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Website
                    </a>
                  )}
                  <div className="cnm-list-item-actions">
                    <button
                      type="button"
                      className="cnm-btn cnm-btn-secondary"
                      onClick={() => handleShowOnMap(place)}
                    >
                      Show on map
                    </button>
                    <a
                      className="cnm-btn cnm-btn-secondary"
                      href={`https://www.google.com/maps/dir/?api=1&destination=${place.lat},${place.lon}`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Directions
                    </a>
                  </div>
                </div>
              ))}
            </div>
            <div className="cnm-map" ref={mapContainerRef} />
          </div>

          <div className="cnm-card cnm-support">
            <h3 className="cnm-support-heading">Support groups</h3>
            <p className="cnm-text">
              OpenStreetMap does not reliably list patient and family support groups, so none are
              shown here. Ask your palliative care team about groups near you.
            </p>
          </div>

          <p className="cnm-footnote">
            Places come from OpenStreetMap, a public map that anyone can edit, found using the Photon search service. Check details before
            you travel. Listing a place here is not a recommendation.
          </p>
        </>
      )}
    </div>
  );
}
