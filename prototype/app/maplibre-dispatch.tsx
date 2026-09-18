'use client';

import React, { useEffect, useRef, useState } from 'react';
import * as maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useContinuityContext, type GeoPatient } from './continuity-context';
import {
  IconHospital,
  IconHeartPulse,
  IconShieldCheck,
  IconShieldAlert,
  IconZap,
  IconCheckCircle,
  IconVolume2,
} from './icons';

export function MapLibreDispatchModal({ onClose }: { onClose: () => void }) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<maplibregl.Marker[]>([]);

  const {
    geoPatients,
    facilities,
    selectedGeoPatient,
    selectPatient,
    dispatchAshaVisit,
    dispatchEmergencyAmbulance,
    cancelDispatch,
    openModal,
  } = useContinuityContext();

  const [activePatient, setActivePatient] = useState<GeoPatient>(selectedGeoPatient);
  const [transitSimActive, setTransitSimActive] = useState(false);

  // Initialize MapLibre GL
  useEffect(() => {
    if (!mapContainerRef.current) return;

    // Keyless OpenStreetMap raster tiles; CARTO's basemaps now require an API key.
    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: {
        version: 8,
        sources: {
          'osm-tiles': {
            type: 'raster',
            tiles: [
              'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            ],
            tileSize: 256,
            attribution: '&copy; OpenStreetMap contributors',
          },
        },
        layers: [
          {
            id: 'osm-tiles-layer',
            type: 'raster',
            source: 'osm-tiles',
            minzoom: 0,
            maxzoom: 19,
          },
        ],
      },
      center: [77.63, 12.945], // Central Bangalore
      zoom: 11.5,
      pitch: 25,
    });

    map.addControl(new maplibregl.NavigationControl({ showCompass: true, visualizePitch: true }), 'top-right');

    mapInstanceRef.current = map;

    return () => {
      map.remove();
    };
  }, []);

  // Update Markers & Layers when patients or active state change
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    // Clear old markers
    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];

    // 1. Add Facility Markers (Hospital & Hospice)
    facilities.forEach((facility) => {
      const el = document.createElement('div');
      el.className = `maplibre-marker facility-marker ${facility.type}`;
      el.innerHTML = `
        <div class="facility-marker-inner">
          <span class="facility-icon">${facility.type === 'hospital' ? '🏥' : facility.type === 'hospice' ? '🕊️' : '🩺'}</span>
          <span class="facility-tooltip">${facility.name}</span>
        </div>
      `;

      const marker = new maplibregl.Marker({ element: el, anchor: 'center' })
        .setLngLat(facility.coordinates)
        .addTo(map);

      markersRef.current.push(marker);
    });

    // 2. Add Patient Markers
    geoPatients.forEach((patient) => {
      const isSelected = patient.hospitalId === activePatient.hospitalId;
      const isDispatched = patient.dispatchStatus !== 'idle';

      const el = document.createElement('div');
      el.className = `maplibre-marker patient-marker tier-${patient.ceilingTier} ${
        isSelected ? 'is-focused' : ''
      } ${isDispatched ? 'is-dispatched' : ''}`;

      const initials = patient.name
        .split(' ')
        .map((n) => n[0])
        .join('');

      el.innerHTML = `
        <div class="patient-marker-bubble">
          ${isDispatched ? '<span class="pulse-beacon"></span>' : ''}
          <span class="patient-initials">${initials}</span>
          <span class="tier-dot"></span>
        </div>
      `;

      el.addEventListener('click', () => {
        setActivePatient(patient);
        selectPatient(patient.hospitalId);
        map.flyTo({
          center: patient.coordinates,
          zoom: 13.5,
          speed: 1.2,
          curve: 1.4,
          essential: true,
        });
      });

      const marker = new maplibregl.Marker({ element: el, anchor: 'center' })
        .setLngLat(patient.coordinates)
        .addTo(map);

      markersRef.current.push(marker);
    });
  }, [geoPatients, facilities, activePatient.hospitalId, selectPatient]);

  // Simulated Transit Route on Map
  useEffect(() => {
    const map = mapInstanceRef.current;
    if (!map) return;

    const sourceId = 'transit-route-source';
    const layerId = 'transit-route-line';

    if (activePatient.dispatchStatus !== 'idle') {
      const facilityCoord = facilities[0].coordinates; // Kidwai
      const patientCoord = activePatient.coordinates;

      // Mid-way point for realistic route curve
      const midCoord: [number, number] = [
        (facilityCoord[0] + patientCoord[0]) / 2 + 0.005,
        (facilityCoord[1] + patientCoord[1]) / 2 - 0.003,
      ];

      const routeGeoJson: any = {
        type: 'Feature',
        geometry: {
          type: 'LineString',
          coordinates: [facilityCoord, midCoord, patientCoord],
        },
      };

      const renderRoute = () => {
        if (map.getSource(sourceId)) {
          (map.getSource(sourceId) as maplibregl.GeoJSONSource).setData(routeGeoJson);
        } else {
          map.addSource(sourceId, {
            type: 'geojson',
            data: routeGeoJson,
          });

          map.addLayer({
            id: layerId,
            type: 'line',
            source: sourceId,
            layout: {
              'line-join': 'round',
              'line-cap': 'round',
            },
            paint: {
              'line-color': activePatient.cprStatus === 'dnacpr' ? '#10b981' : '#ef4444',
              'line-width': 4,
              'line-dasharray': [2, 1],
            },
          });
        }
      };

      if (map.isStyleLoaded()) {
        renderRoute();
      } else {
        map.once('load', renderRoute);
      }
    } else {
      if (map.getLayer(layerId)) map.removeLayer(layerId);
      if (map.getSource(sourceId)) map.removeSource(sourceId);
    }
  }, [activePatient, facilities]);

  // Sector jump presets
  const jumpToSector = (coords: [number, number], zoom = 12.8) => {
    mapInstanceRef.current?.flyTo({
      center: coords,
      zoom,
      speed: 1.4,
      essential: true,
    });
  };

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal-panel maplibre-modal" role="dialog" aria-modal="true" aria-labelledby="map-modal-title">
        {/* Modal Header */}
        <div className="modal-header map-header">
          <div className="flex items-center gap-2">
            <span className="map-badge">🗺️ MapLibre GL · Community GIS</span>
            <div>
              <h2 id="map-modal-title">Palliative Home Care &amp; Emergency Transit Dispatch</h2>
              <p className="text-xs text-slate-500">Real-time geographic coordination of palliative patients, ASHA nurses &amp; emergency resus</p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="offline-pill">
              <span className="dot-live" /> Offline Map Tiles Cached (Bangalore Metropole)
            </span>
            <button type="button" className="close-btn" onClick={onClose} aria-label="Close Map">✕</button>
          </div>
        </div>

        {/* Sector Quick-Jump Strip */}
        <div className="map-sector-bar">
          <span className="sector-kicker">Jump to Sector:</span>
          <button type="button" className="sector-pill" onClick={() => jumpToSector([77.63, 12.945], 11)}>
            🌐 All City Overview
          </button>
          <button type="button" className="sector-pill" onClick={() => jumpToSector([77.5855, 12.926], 13.8)}>
            📍 South Sector (Jayanagar · Sunita)
          </button>
          <button type="button" className="sector-pill" onClick={() => jumpToSector([77.6412, 12.9784], 13.8)}>
            📍 East Sector (Indiranagar · Sana)
          </button>
          <button type="button" className="sector-pill" onClick={() => jumpToSector([77.7499, 12.9698], 13.8)}>
            📍 Whitefield Sector (Vikram)
          </button>
          <button type="button" className="sector-pill" onClick={() => jumpToSector([77.5925, 12.9372], 14.5)}>
            🏥 Tertiary Base (Kidwai Oncology)
          </button>
        </div>

        {/* Main Content Area: Map on Left, Patient Drawer on Right */}
        <div className="maplibre-layout">
          {/* Map Viewport */}
          <div className="maplibre-viewport" ref={mapContainerRef} />

          {/* Active Patient Dispatch Drawer */}
          <div className="maplibre-drawer">
            <div className="drawer-patient-header">
              <div className="flex items-center justify-between">
                <span className={`tep-active-level-chip level-${activePatient.ceilingTier}`}>
                  Tier {activePatient.ceilingTier} · {activePatient.cprStatus === 'dnacpr' ? 'DNACPR Comfort' : 'Full Resus'}
                </span>
                <span className="drawer-zone">{activePatient.zone}</span>
              </div>
              <h3 className="drawer-patient-name">{activePatient.name}</h3>
              <p className="drawer-diagnosis">{activePatient.diagnosis}</p>
              <p className="drawer-address">📍 {activePatient.address}</p>
            </div>

            {/* Critical Resuscitation Directives */}
            <div className={`drawer-triage-card ${activePatient.cprStatus === 'dnacpr' ? 'is-dnacpr' : 'is-full'}`}>
              <div className="flex items-center gap-2">
                <IconHeartPulse className="w-4 h-4" />
                <strong>
                  {activePatient.cprStatus === 'dnacpr'
                    ? 'DNACPR (Do Not Attempt CPR) Registered'
                    : 'Standard Resuscitation Authorized'}
                </strong>
              </div>
              <small>
                {activePatient.cprStatus === 'dnacpr'
                  ? 'Syringe pump comfort analgesia authorized. Do not convey to ED sirens unless requested by family.'
                  : 'Treat acute reversible complications fully.'}
              </small>
            </div>

            {/* Syringe Driver Infusion Status */}
            <div className="drawer-infusion-card">
              <div className="flex items-center justify-between mb-1">
                <span className="text-xs font-bold text-slate-700">Subcutaneous Infusion:</span>
                <span className={`status-pill ${activePatient.syringeDriverActive ? 'pill-active' : 'pill-inactive'}`}>
                  {activePatient.syringeDriverActive ? 'Pump Running' : 'Oral Regimen'}
                </span>
              </div>
              <p className="text-xs text-slate-600">
                {activePatient.syringeDriverActive
                  ? activePatient.syringeDriverDetails
                  : 'Stable on oral baseline analgesia with PRN rescue doses.'}
              </p>
            </div>

            {/* Community Care Team & Proxy Contact */}
            <div className="drawer-contacts-card">
              <div>
                <strong>Assigned ASHA Worker:</strong>
                <p>{activePatient.assignedAshaWorker.name} ({activePatient.assignedAshaWorker.phone})</p>
              </div>
              <div className="mt-2">
                <strong>Primary Family Surrogate:</strong>
                <p>{activePatient.primarySurrogate.name} · {activePatient.primarySurrogate.relation} ({activePatient.primarySurrogate.phone})</p>
              </div>
            </div>

            {/* Active Dispatch Status Banner */}
            {activePatient.dispatchStatus !== 'idle' ? (
              <div className="drawer-transit-active-banner">
                <div className="flex items-center gap-2 text-rose-700 font-bold">
                  <span className="pulse-dot-red" />
                  <span>Unit En Route (ETA {activePatient.activeAmbulanceEtaMinutes} mins)</span>
                </div>
                <p className="text-xs text-rose-600 mt-1">
                  Telemetry linked. Clinician summary &amp; DNACPR instructions sent to vehicle MDT tablet.
                </p>
                <button
                  type="button"
                  className="secondary-button w-full mt-2"
                  onClick={() => cancelDispatch(activePatient.hospitalId)}
                >
                  Cancel / Resolve Dispatch
                </button>
              </div>
            ) : (
              <div className="drawer-dispatch-actions">
                <button
                  type="button"
                  className="primary-button dispatch-btn asha-btn"
                  onClick={() => dispatchAshaVisit(activePatient.hospitalId)}
                >
                  <IconHospital className="w-4 h-4" />
                  <span>Dispatch ASHA Home Palliative Visit</span>
                </button>

                <button
                  type="button"
                  className="secondary-button dispatch-btn ambulance-btn"
                  onClick={() => dispatchEmergencyAmbulance(activePatient.hospitalId)}
                >
                  <IconZap className="w-4 h-4 text-rose-600" />
                  <span>Dispatch 108 Emergency Ambulance</span>
                </button>
              </div>
            )}

            {/* Cross-Tool Clinical Shortcuts */}
            <div className="drawer-tools-row">
              <button
                type="button"
                className="tool-shortcut-btn"
                onClick={() => {
                  onClose();
                  openModal('tep');
                }}
              >
                Open TEP Matrix
              </button>
              <button
                type="button"
                className="tool-shortcut-btn"
                onClick={() => {
                  onClose();
                  openModal('syringe_driver');
                }}
              >
                Syringe Driver Calc
              </button>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
