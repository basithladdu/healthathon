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

export function PalliativeMap({ points, origin, selectedId, route, searchCentre, onSelect }: {
  points: CareMapPoint[]; origin: CareMapOrigin | null; selectedId: string | null;
  route: CareRoadRoute | null; searchCentre: CareMapOrigin | null; onSelect: (id: string) => void;
}) {
  const container = useRef<HTMLDivElement>(null);
  const instance = useRef<maplibregl.Map | null>(null);
  const markers = useRef<maplibregl.Marker[]>([]);
  const select = useRef(onSelect);
  const refit = useRef<() => void>(() => {});
  const [ready, setReady] = useState(false);
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
      map.on('load', () => setReady(true));
      map.on('error', () => setUnavailable(true));
      map.on('idle', () => { if (map.areTilesLoaded()) setUnavailable(false); });
    } catch { setUnavailable(true); return; }
    const observer = new ResizeObserver(() => {
      if (!container.current?.clientWidth || !container.current.clientHeight) return;
      map.resize();
      refit.current();
    });
    observer.observe(container.current);
    return () => { observer.disconnect(); refit.current = () => {}; markers.current.forEach((marker) => marker.remove()); markers.current = []; map.remove(); instance.current = null; };
  }, []);

  useEffect(() => {
    const map = instance.current;
    if (!map || !ready) return;
    markers.current.forEach((marker) => marker.remove());
    markers.current = [];
    for (const point of points) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `palliative-map-pin ${point.category}${selectedId === point.id ? ' is-selected' : ''}`;
      button.setAttribute('aria-label', `Show ${point.name}`);
      button.setAttribute('aria-pressed', String(selectedId === point.id));
      button.title = point.name;
      button.appendChild(document.createElement('span'));
      button.addEventListener('click', () => select.current(point.id));
      markers.current.push(new maplibregl.Marker({ element: button, anchor: 'center' }).setLngLat(point.coordinates).addTo(map));
    }
    if (origin) {
      const marker = document.createElement('div');
      marker.className = 'palliative-map-origin';
      marker.setAttribute('aria-label', `Starting point: ${origin.label}`);
      marker.title = origin.label;
      markers.current.push(new maplibregl.Marker({ element: marker }).setLngLat(origin.coordinates).addTo(map));
    }
  }, [points, origin, selectedId, ready]);

  useEffect(() => {
    const map = instance.current;
    if (!map || !ready) return;
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
    refit.current = () => {
      if (!container.current?.clientWidth || !container.current.clientHeight) return;
      const selected = points.find((point) => point.id === selectedId);
      const coordinates = route?.coordinates ?? (selected ? [selected.coordinates] : searchCentre ? [searchCentre.coordinates] : points.map((point) => point.coordinates));
      if (!coordinates.length) return;
      if (coordinates.length === 1) { map.easeTo({ center: coordinates[0], zoom: selected ? 15 : 12, duration: 0 }); return; }
      const bounds = coordinates.reduce((current, point) => current.extend(point), new maplibregl.LngLatBounds(coordinates[0], coordinates[0]));
      map.fitBounds(bounds, { padding: 50, maxZoom: 15, duration: 0 });
    };
    refit.current();
  }, [points, origin, selectedId, route, searchCentre, ready]);

  return <div className="palliative-map-shell">
    <div ref={container} className="palliative-map-canvas" role="region" aria-label="Care centres and road route" />
    {unavailable && <p className="palliative-map-error" role="status">The map could not load. Centre details and directions are still available.</p>}
  </div>;
}
