'use client';

import { useEffect, useRef, useState } from 'react';
import type { FeatureCollection } from 'geojson';
import * as maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import './palliative-map.css';

export type CareMapPoint = {
  id: string; name: string; coordinates: [number, number]; category: 'palliative' | 'hospital';
};
export type CareMapOrigin = { label: string; coordinates: [number, number] };
export type CareRoadRoute = { coordinates: [number, number][]; distance: number; duration: number };

const CATEGORY_PATHS = {
  palliative: 'M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z',
  hospital: 'M9 3h6v6h6v6h-6v6H9v-6H3V9h6Z',
};

function CategoryIcon({ category }: { category: CareMapPoint['category'] }) {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d={CATEGORY_PATHS[category]} /></svg>;
}

function nearby(point: [number, number], centre: [number, number]) {
  const radians = Math.PI / 180;
  const latitude = (point[1] - centre[1]) * radians;
  const longitude = (point[0] - centre[0]) * radians;
  const angle = Math.sin(latitude / 2) ** 2 + Math.cos(point[1] * radians) * Math.cos(centre[1] * radians) * Math.sin(longitude / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(angle), Math.sqrt(Math.max(0, 1 - angle))) <= 40;
}

export function PalliativeMap({ points, origin, selectedId, route, searchCentre, onSelect }: {
  points: CareMapPoint[]; origin: CareMapOrigin | null; selectedId: string | null;
  route: CareRoadRoute | null; searchCentre: CareMapOrigin | null; onSelect: (id: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const instance = useRef<maplibregl.Map | null>(null);
  const markers = useRef<maplibregl.Marker[]>([]);
  const popup = useRef<maplibregl.Popup | null>(null);
  const clusterPopup = useRef<maplibregl.Popup | null>(null);
  const select = useRef(onSelect);
  const refit = useRef<() => void>(() => {});
  const fitAll = useRef<() => void>(() => {});
  const userMoved = useRef(false);
  const cameraContext = useRef('');
  const previousRoute = useRef<CareRoadRoute | null>(null);
  const [ready, setReady] = useState(false);
  const [styleReady, setStyleReady] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  useEffect(() => { select.current = onSelect; }, [onSelect]);

  useEffect(() => {
    if (!container.current) return;
    let map: maplibregl.Map;
    try {
      map = new maplibregl.Map({
        container: container.current,
        style: {
          version: 8,
          sources: { streets: { type: 'raster', tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize: 256, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors' } },
          layers: [{ id: 'streets', type: 'raster', source: 'streets' }],
        },
        center: [78.96, 20.59], zoom: 4.2, attributionControl: { compact: true },
      });
      instance.current = map;
      map.addControl(new maplibregl.NavigationControl({ showCompass: false }), 'top-right');
      map.on('style.load', () => setStyleReady(true));
      map.on('movestart', (event) => { if ('originalEvent' in event && event.originalEvent) userMoved.current = true; });
      map.on('error', () => setUnavailable(true));
      map.on('idle', () => { if (map.areTilesLoaded()) setUnavailable(false); });
      setReady(true);
    } catch { setUnavailable(true); return; }
    let width = container.current.clientWidth;
    let height = container.current.clientHeight;
    const observer = new ResizeObserver(() => {
      if (!container.current?.clientWidth || !container.current.clientHeight) { width = 0; height = 0; return; }
      const nextWidth = container.current.clientWidth;
      const nextHeight = container.current.clientHeight;
      const changed = Math.abs(nextWidth - width) > 40 || Math.abs(nextHeight - height) > 60 || !width || !height;
      map.resize();
      if (changed) {
        width = nextWidth; height = nextHeight;
        if (!userMoved.current) refit.current();
      }
    });
    observer.observe(container.current);
    return () => { observer.disconnect(); refit.current = () => {}; fitAll.current = () => {}; popup.current?.remove(); clusterPopup.current?.remove(); markers.current.forEach((marker) => marker.remove()); markers.current = []; map.remove(); instance.current = null; };
  }, []);

  useEffect(() => {
    const map = instance.current;
    if (!map || !ready) return;
    function renderMarkers() {
    markers.current.forEach((marker) => marker.remove());
    markers.current = [];
    popup.current?.remove();
    popup.current = null;
    clusterPopup.current?.remove();
    clusterPopup.current = null;
    const groups: Array<Array<{ point: CareMapPoint; index: number }>> = [];
    for (const [index, point] of points.entries()) {
      const pixel = map!.project(point.coordinates);
      const group = point.id === selectedId ? undefined : groups.find((members) => {
        if (members[0].point.id === selectedId) return false;
        const anchor = map!.project(members[0].point.coordinates);
        return Math.hypot(pixel.x - anchor.x, pixel.y - anchor.y) < 58;
      });
      if (group) group.push({ point, index }); else groups.push([{ point, index }]);
    }
    for (const members of groups) {
      const { index, point } = members[0];
      if (members.length > 1) {
        const button = document.createElement('button');
        button.type = 'button';
        const mixed = members.some((member) => member.point.category !== point.category);
        button.className = `palliative-map-cluster ${mixed ? 'is-mixed' : point.category}`;
        button.setAttribute('aria-label', `${members.length} centres. Zoom in to choose a centre`);
        const count = document.createElement('strong');
        count.textContent = String(members.length);
        const label = document.createElement('span');
        label.textContent = 'centres';
        button.appendChild(count);
        button.appendChild(label);
        button.addEventListener('click', () => {
          userMoved.current = true;
          const bounds = members.reduce((current, member) => current.extend(member.point.coordinates), new maplibregl.LngLatBounds(point.coordinates, point.coordinates));
          const sameLocation = bounds.getNorthEast().distanceTo(bounds.getSouthWest()) < 12;
          if (map!.getZoom() >= 16 || sameLocation) {
            clusterPopup.current?.remove();
            const list = document.createElement('div');
            list.className = 'palliative-map-cluster-list';
            for (const member of members) {
              const choice = document.createElement('button');
              choice.type = 'button';
              const number = document.createElement('span');
              number.textContent = String(member.index + 1);
              const name = document.createElement('strong');
              name.textContent = member.point.name;
              choice.appendChild(number);
              choice.appendChild(name);
              choice.addEventListener('click', () => { userMoved.current = false; select.current(member.point.id); });
              list.appendChild(choice);
            }
            clusterPopup.current = new maplibregl.Popup({ closeButton: true, closeOnClick: true, offset: 30, maxWidth: '265px', className: 'palliative-map-cluster-popup' }).setLngLat(point.coordinates).setDOMContent(list).addTo(map!);
          } else {
            const compact = (container.current?.clientHeight ?? 400) < 240;
            map!.fitBounds(bounds, { padding: compact ? 42 : 65, maxZoom: 17, duration: 0 });
          }
        });
        markers.current.push(new maplibregl.Marker({ element: button, anchor: 'center' }).setLngLat(point.coordinates).addTo(map!));
        continue;
      }
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `palliative-map-pin ${point.category}${selectedId === point.id ? ' is-selected' : ''}`;
      button.setAttribute('aria-label', `${index + 1}. ${point.name}. ${point.category === 'palliative' ? 'Best supportive care' : 'Hospital'}. Show route`);
      button.setAttribute('aria-pressed', String(selectedId === point.id));
      button.title = point.name;
      const head = document.createElement('span');
      head.className = 'palliative-map-pin-head';
      const icon = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      icon.setAttribute('viewBox', '0 0 24 24');
      icon.setAttribute('aria-hidden', 'true');
      const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      path.setAttribute('d', CATEGORY_PATHS[point.category]);
      icon.appendChild(path);
      const number = document.createElement('strong');
      number.textContent = String(index + 1);
      head.appendChild(icon);
      head.appendChild(number);
      button.appendChild(head);
      button.addEventListener('click', () => { userMoved.current = false; if (selectedId === point.id) refit.current(); select.current(point.id); });
      markers.current.push(new maplibregl.Marker({ element: button, anchor: 'bottom' }).setLngLat(point.coordinates).addTo(map!));
      if (selectedId === point.id) {
        const label = document.createElement('div');
        label.className = `palliative-map-place ${point.category}`;
        const category = document.createElement('span');
        category.textContent = point.category === 'palliative' ? 'Best supportive care' : 'Hospital';
        const name = document.createElement('strong');
        name.textContent = point.name;
        label.appendChild(category);
        label.appendChild(name);
        popup.current = new maplibregl.Popup({ closeButton: false, closeOnClick: false, focusAfterOpen: false, offset: 49, maxWidth: '230px', className: 'palliative-map-selected' }).setLngLat(point.coordinates).setDOMContent(label).addTo(map!);
      }
    }
    if (origin) {
      const marker = document.createElement('div');
      marker.className = 'palliative-map-origin';
      marker.setAttribute('aria-label', `Starting point: ${origin.label}`);
      marker.setAttribute('role', 'img');
      marker.title = origin.label;
      const label = document.createElement('span');
      label.textContent = 'Start';
      marker.appendChild(label);
      markers.current.push(new maplibregl.Marker({ element: marker }).setLngLat(origin.coordinates).addTo(map!));
    }
    }
    renderMarkers();
    map.on('moveend', renderMarkers);
    map.on('resize', renderMarkers);
    return () => { map.off('moveend', renderMarkers); map.off('resize', renderMarkers); popup.current?.remove(); clusterPopup.current?.remove(); markers.current.forEach((marker) => marker.remove()); markers.current = []; };
  }, [points, origin, selectedId, ready]);

  useEffect(() => {
    const map = instance.current;
    if (!map || !styleReady) return;
    const data: FeatureCollection = {
      type: 'FeatureCollection',
      features: route ? [{ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: route.coordinates } }] : [],
    };
    const source = map.getSource('care-road-route') as maplibregl.GeoJSONSource | undefined;
    if (source) source.setData(data);
    else {
      map.addSource('care-road-route', { type: 'geojson', data });
      map.addLayer({ id: 'care-route-border', type: 'line', source: 'care-road-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#fff', 'line-width': 9 } });
      map.addLayer({ id: 'care-route-line', type: 'line', source: 'care-road-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#7b3cb5', 'line-width': 5 } });
    }
  }, [route, styleReady]);

  useEffect(() => {
    const map = instance.current;
    if (!map || !ready) return;
    const context = `${selectedId ?? ''}|${origin?.coordinates.join(',') ?? ''}|${searchCentre?.coordinates.join(',') ?? ''}`;
    if (context !== cameraContext.current || (route && previousRoute.current !== route)) userMoved.current = false;
    cameraContext.current = context;
    previousRoute.current = route;
    const selected = points.find((point) => point.id === selectedId);
    const centre = origin ?? searchCentre;
    const allCoordinates = [...points.map((point) => point.coordinates), ...(origin ? [origin.coordinates] : [])];
    const coordinates = route?.coordinates.length ? route.coordinates
      : selected ? [selected.coordinates, ...(origin ? [origin.coordinates] : [])]
        : centre ? [centre.coordinates, ...points.filter((point) => nearby(point.coordinates, centre.coordinates)).map((point) => point.coordinates)]
          : allCoordinates;
    const fit = (positions: [number, number][], maxZoom: number) => {
      if (!container.current?.clientWidth || !container.current.clientHeight) return;
      if (!positions.length) return;
      const bounds = positions.reduce((current, point) => current.extend(point), new maplibregl.LngLatBounds(positions[0], positions[0]));
      const small = container.current.clientWidth < 500;
      const height = container.current.clientHeight;
      const top = Math.min(selected ? 105 : 65, Math.max(35, height * .3));
      map.fitBounds(bounds, { padding: { top, bottom: Math.min(65, height * .24), left: small ? 34 : 55, right: small ? 54 : 65 }, maxZoom, duration: 0 });
    };
    refit.current = () => fit(coordinates, selected ? 15 : centre ? 13 : 11);
    fitAll.current = () => { userMoved.current = true; fit(allCoordinates, 13); };
    if (!userMoved.current) refit.current();
  }, [points, origin, selectedId, route, searchCentre, ready]);

  return <div className="palliative-map-shell">
    <div ref={container} className="palliative-map-canvas" role="region" aria-label="Care centres and road route" />
    <button type="button" className="palliative-map-fit" aria-label="Show all centres" disabled={!ready || (!points.length && !origin)} onClick={() => fitAll.current()}><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5M8 8h8v8H8Z" /></svg>Show all</button>
    <div className="palliative-map-legend" aria-label="Map key"><span className="palliative"><CategoryIcon category="palliative" />Best supportive care</span><span className="hospital"><CategoryIcon category="hospital" />Hospital</span></div>
    {unavailable && <p className="palliative-map-error" role="status">Street map unavailable. You can still choose a centre.</p>}
  </div>;
}
