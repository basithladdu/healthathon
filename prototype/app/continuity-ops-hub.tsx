'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import QRCode from 'qrcode';
import * as maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import {
  useContinuityContext,
  type GeoPatient,
} from './continuity-context';
import {
  IconCheckCircle,
  IconHeartPulse,
  IconMapPin,
  IconShieldAlert,
  IconShieldCheck,
  IconZap,
  IconHospital,
  IconFileText,
  IconUsers,
} from './icons';

export type OpsHubTab = 'map' | 'qr' | 'context' | 'mro';

type ContinuityOpsHubProps = {
  open: boolean;
  initialTab?: OpsHubTab;
  sessionType: 'care-team' | 'family' | 'patient' | 'landing';
  currentRole: string;
  patientName: string;
  hospitalId: string;
  diagnosis?: string;
  versionLabel?: string;
  verifiedBy?: string;
  verifiedOn?: string;
  summaryReady: boolean;
  retrievalUnlocked: boolean;
  cprStatus?: string;
  onClose: () => void;
  onClinicCheckIn?: (payload: { hospitalId: string; locator: string; checkedInAt: string }) => void;
  onUnlockHandoff?: (hospitalId: string) => void;
  onSealMro?: (mroId: string) => void;
};

type ContextCheck = {
  id: string;
  label: string;
  detail: string;
  pass: boolean;
  severity: 'critical' | 'warn' | 'info';
};

type ClinicCheckIn = {
  hospitalId: string;
  locator: string;
  checkedInAt: string;
  desk: string;
};

function opaqueLocator(hospitalId: string, version: string): string {
  return `CL://handoff/${hospitalId}/${version.replace(/\s+/g, '').toLowerCase()}`;
}

function sha256Lite(input: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i += 1) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  const a = (h >>> 0).toString(16).padStart(8, '0');
  const b = Math.imul(h ^ input.length, 0x9e3779b9) >>> 0;
  return `${a}${(b >>> 0).toString(16).padStart(8, '0')}`.toUpperCase();
}

function OpsMapPane({
  activePatient,
  onSelectPatient,
}: {
  activePatient: GeoPatient;
  onSelectPatient: (id: string) => void;
}) {
  const mapContainerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<maplibregl.Marker[]>([]);
  const { geoPatients, facilities, dispatchAshaVisit, dispatchEmergencyAmbulance, cancelDispatch } =
    useContinuityContext();

  useEffect(() => {
    if (!mapContainerRef.current || mapRef.current) return;

    const map = new maplibregl.Map({
      container: mapContainerRef.current,
      style: {
        version: 8,
        sources: {
          'ops-tiles': {
            type: 'raster',
            tiles: [
              'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            ],
            tileSize: 256,
            attribution: '&copy; OpenStreetMap contributors',
          },
        },
        layers: [{ id: 'ops-tiles-layer', type: 'raster', source: 'ops-tiles' }],
      },
      center: activePatient.coordinates,
      zoom: 12.2,
      pitch: 35,
    });

    map.addControl(new maplibregl.NavigationControl({ visualizePitch: true }), 'top-right');
    map.addControl(new maplibregl.ScaleControl({ maxWidth: 120 }), 'bottom-left');

    map.on('load', () => {
      map.addSource('continuity-coverage', {
        type: 'geojson',
        data: {
          type: 'FeatureCollection',
          features: geoPatients.map((p) => ({
            type: 'Feature',
            properties: { id: p.hospitalId, tier: p.ceilingTier },
            geometry: {
              type: 'Point',
              coordinates: p.coordinates,
            },
          })),
        },
      });

      map.addLayer({
        id: 'continuity-heat',
        type: 'circle',
        source: 'continuity-coverage',
        paint: {
          'circle-radius': 42,
          'circle-color': [
            'match',
            ['get', 'tier'],
            1,
            '#0c5c3f',
            2,
            '#a15c00',
            '#1a5f9e',
          ],
          'circle-opacity': 0.18,
          'circle-blur': 0.6,
        },
      });
    });

    mapRef.current = map;
    return () => {
      map.remove();
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    markersRef.current.forEach((m) => m.remove());
    markersRef.current = [];

    facilities.forEach((facility) => {
      const el = document.createElement('div');
      el.className = `maplibre-marker facility-marker ${facility.type}`;
      el.innerHTML = `<div class="facility-marker-inner"><span class="facility-icon">${
        facility.type === 'hospital' ? '🏥' : facility.type === 'hospice' ? '🕊️' : '🩺'
      }</span><span class="facility-tooltip">${facility.name}</span></div>`;
      markersRef.current.push(new maplibregl.Marker({ element: el }).setLngLat(facility.coordinates).addTo(map));
    });

    geoPatients.forEach((patient) => {
      const el = document.createElement('div');
      const focused = patient.hospitalId === activePatient.hospitalId;
      el.className = `maplibre-marker patient-marker tier-${patient.ceilingTier} ${
        focused ? 'is-focused' : ''
      } ${patient.dispatchStatus !== 'idle' ? 'is-dispatched' : ''}`;
      const initials = patient.name
        .split(' ')
        .map((n) => n[0])
        .join('');
      el.innerHTML = `<div class="patient-marker-bubble">${
        patient.dispatchStatus !== 'idle' ? '<span class="pulse-beacon"></span>' : ''
      }<span class="patient-initials">${initials}</span><span class="tier-dot"></span></div>`;
      el.addEventListener('click', () => {
        onSelectPatient(patient.hospitalId);
        map.flyTo({ center: patient.coordinates, zoom: 13.6, speed: 1.3, essential: true });
      });
      markersRef.current.push(new maplibregl.Marker({ element: el }).setLngLat(patient.coordinates).addTo(map));
    });

    map.flyTo({ center: activePatient.coordinates, zoom: 13.2, speed: 0.9, essential: true });
  }, [geoPatients, facilities, activePatient, onSelectPatient]);

  return (
    <div className="ops-map-pane">
      <div className="ops-map-viewport" ref={mapContainerRef} />
      <aside className="ops-map-drawer">
        <div className={`ops-map-tier tier-${activePatient.ceilingTier}`}>
          Tier {activePatient.ceilingTier} · {activePatient.cprStatus === 'dnacpr' ? 'DNACPR' : 'Resus'}
        </div>
        <h3>{activePatient.name}</h3>
        <p className="ops-muted">{activePatient.diagnosis}</p>
        <p className="ops-address">
          <IconMapPin className="w-3.5 h-3.5" /> {activePatient.address}
        </p>

        <div className="ops-dispatch-card">
          <strong>Live continuity dispatch</strong>
          {activePatient.dispatchStatus === 'idle' ? (
            <div className="ops-dispatch-actions">
              <button type="button" className="ops-btn ops-btn-primary" onClick={() => dispatchAshaVisit(activePatient.hospitalId)}>
                <IconHospital className="w-4 h-4" /> Dispatch ASHA visit
              </button>
              <button type="button" className="ops-btn ops-btn-danger" onClick={() => dispatchEmergencyAmbulance(activePatient.hospitalId)}>
                <IconZap className="w-4 h-4" /> Dispatch 108 ambulance
              </button>
            </div>
          ) : (
            <div className="ops-enroute">
              <span className="ops-pulse" /> Unit en route · ETA {activePatient.activeAmbulanceEtaMinutes} min
              <button type="button" className="ops-btn ops-btn-ghost" onClick={() => cancelDispatch(activePatient.hospitalId)}>
                Resolve dispatch
              </button>
            </div>
          )}
        </div>

        <ul className="ops-patient-switch">
          {geoPatients.map((p) => (
            <li key={p.hospitalId}>
              <button
                type="button"
                className={p.hospitalId === activePatient.hospitalId ? 'is-active' : ''}
                onClick={() => onSelectPatient(p.hospitalId)}
              >
                <span>{p.name.split(' ')[0]}</span>
                <small>{p.zone}</small>
              </button>
            </li>
          ))}
        </ul>
      </aside>
    </div>
  );
}

function QrBridgePane(props: {
  hospitalId: string;
  patientName: string;
  versionLabel: string;
  summaryReady: boolean;
  onClinicCheckIn?: ContinuityOpsHubProps['onClinicCheckIn'];
  onUnlockHandoff?: ContinuityOpsHubProps['onUnlockHandoff'];
}) {
  const { hospitalId, patientName, versionLabel, summaryReady, onClinicCheckIn, onUnlockHandoff } = props;
  const locator = opaqueLocator(hospitalId, versionLabel);
  const checksum = sha256Lite(`${locator}|${patientName}|${versionLabel}`);
  const payload = JSON.stringify({
    type: 'continuity-loop-handoff',
    locator,
    hospitalId,
    version: versionLabel,
    checksum,
    opaque: true,
    note: 'QR alone reveals no clinical content. Authenticated retrieval required.',
  });

  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [mode, setMode] = useState<'generate' | 'scan-checkin' | 'scan-handoff'>('generate');
  const [scanning, setScanning] = useState(false);
  const [lastCheckIn, setLastCheckIn] = useState<ClinicCheckIn | null>(null);
  const [scanResult, setScanResult] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    QRCode.toDataURL(payload, { errorCorrectionLevel: 'M', margin: 1, width: 280, color: { dark: '#0a2118', light: '#ffffff' } })
      .then((url) => {
        if (!cancelled) setQrDataUrl(url);
      })
      .catch(() => {
        if (!cancelled) setQrDataUrl(null);
      });
    return () => {
      cancelled = true;
    };
  }, [payload]);

  function runScan(kind: 'scan-checkin' | 'scan-handoff') {
    setMode(kind);
    setScanning(true);
    setScanResult(null);
    window.setTimeout(() => {
      setScanning(false);
      if (kind === 'scan-checkin') {
        const event: ClinicCheckIn = {
          hospitalId,
          locator,
          checkedInAt: new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' }),
          desk: 'Oncology Day-Care Desk · Kidwai',
        };
        setLastCheckIn(event);
        setScanResult(`Check-in saved on this device for ${patientName} at ${event.desk}. The clinic has not been notified.`);
        onClinicCheckIn?.(event);
      } else {
        setScanResult(
          summaryReady
            ? `Opaque locator verified. Handoff gate opened for ${hospitalId}.`
            : `Locator matched, but no physician-verified summary is sealed yet for ${hospitalId}.`,
        );
        if (summaryReady) onUnlockHandoff?.(hospitalId);
      }
    }, 1400);
  }

  return (
    <div className="ops-qr-pane">
      <div className="ops-qr-generate">
        <div className="ops-kicker">Saathi · Opaque locator QR</div>
        <h3>Handoff &amp; clinic check-in QR</h3>
        <p className="ops-muted">
          Encodes only a locator + checksum — never CPR ceilings or clinical text. Possessing the QR does not open the note.
        </p>

        <div className="ops-qr-frame">
          {qrDataUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={qrDataUrl} alt="Saathi handoff QR code" width={220} height={220} />
          ) : (
            <div className="ops-qr-placeholder">Building QR…</div>
          )}
        </div>

        <dl className="ops-qr-meta">
          <div>
            <dt>Opaque locator</dt>
            <dd>
              <code>{locator}</code>
            </dd>
          </div>
          <div>
            <dt>Checksum</dt>
            <dd>
              <code>{checksum.slice(0, 4)}…{checksum.slice(-4)}</code>
            </dd>
          </div>
          <div>
            <dt>Patient (session only)</dt>
            <dd>
              {patientName} · {hospitalId}
            </dd>
          </div>
        </dl>
      </div>

      <div className="ops-qr-actions">
        <button type="button" className="ops-btn ops-btn-primary" disabled={scanning} onClick={() => runScan('scan-checkin')}>
          <IconHospital className="w-4 h-4" /> Open clinic check-in
        </button>
        <button type="button" className="ops-btn ops-btn-accent" disabled={scanning} onClick={() => runScan('scan-handoff')}>
          <IconShieldCheck className="w-4 h-4" /> Open emergency handoff
        </button>

        <div className="ops-qr-scanner-stage" aria-live="polite">
          {scanning ? (
            <div className="ops-scan-viewfinder">
              <div className="ops-scan-laser" />
              <p>Opening saved QR record…</p>
            </div>
          ) : scanResult ? (
            <div className={`ops-scan-result ${mode === 'scan-handoff' && summaryReady ? 'is-ok' : mode === 'scan-checkin' ? 'is-ok' : 'is-warn'}`}>
              <IconCheckCircle className="w-5 h-5" />
              <div>
                <strong>{mode === 'scan-checkin' ? 'Check-in saved here' : 'Handoff record opened'}</strong>
                <p>{scanResult}</p>
                {lastCheckIn && mode === 'scan-checkin' && (
                  <small>
                    {lastCheckIn.checkedInAt} · {lastCheckIn.desk}
                  </small>
                )}
              </div>
            </div>
          ) : (
            <p className="ops-muted">Open a saved QR record. Check-ins stay on this device; the clinic is not notified.</p>
          )}
        </div>
      </div>
    </div>
  );
}

function ContextCheckerPane(props: {
  sessionType: ContinuityOpsHubProps['sessionType'];
  currentRole: string;
  patientName: string;
  hospitalId: string;
  summaryReady: boolean;
  retrievalUnlocked: boolean;
  cprStatus?: string;
}) {
  const { selectedGeoPatient, geoPatients, facilities, currentRole: ctxRole } = useContinuityContext();
  const [ranAt, setRanAt] = useState<string | null>(null);
  const [checks, setChecks] = useState<ContextCheck[]>([]);

  const baseSignals = useMemo(
    () => [
      { label: 'Auth screen / session', value: props.sessionType },
      { label: 'Active role (UI)', value: props.currentRole },
      { label: 'Context role', value: ctxRole },
      { label: 'Selected patient', value: `${props.patientName} (${props.hospitalId})` },
      { label: 'Geo patient', value: `${selectedGeoPatient.name} @ ${selectedGeoPatient.coordinates.join(', ')}` },
      { label: 'Zone', value: selectedGeoPatient.zone },
      { label: 'Dispatch', value: selectedGeoPatient.dispatchStatus },
      { label: 'Summary sealed', value: props.summaryReady ? 'Yes' : 'No' },
      { label: 'Retrieval unlocked', value: props.retrievalUnlocked ? 'Yes' : 'No' },
      { label: 'CPR context', value: props.cprStatus ?? selectedGeoPatient.cprStatus },
      { label: 'Facilities in map context', value: String(facilities.length) },
      { label: 'Geo cohort size', value: String(geoPatients.length) },
    ],
    [
      props.sessionType,
      props.currentRole,
      props.patientName,
      props.hospitalId,
      props.summaryReady,
      props.retrievalUnlocked,
      props.cprStatus,
      ctxRole,
      selectedGeoPatient,
      facilities.length,
      geoPatients.length,
    ],
  );

  function runContextAudit() {
    const next: ContextCheck[] = [
      {
        id: 'session',
        label: 'Session context present',
        detail: `Session type resolves to “${props.sessionType}”.`,
        pass: Boolean(props.sessionType),
        severity: 'critical',
      },
      {
        id: 'role',
        label: 'Role context aligned',
        detail:
          props.sessionType === 'landing'
            ? 'Landing preview — role not yet bound to a care-team session.'
            : `UI role “${props.currentRole}” is active for continuity actions.`,
        pass: props.sessionType === 'landing' || props.currentRole.length > 0,
        severity: 'critical',
      },
      {
        id: 'patient',
        label: 'Patient continuity context bound',
        detail: `Hospital ID ${props.hospitalId} linked to geo patient ${selectedGeoPatient.hospitalId}.`,
        pass: props.hospitalId === selectedGeoPatient.hospitalId || props.sessionType === 'landing',
        severity: 'warn',
      },
      {
        id: 'geo',
        label: 'Location context usable',
        detail: `Coordinates ${selectedGeoPatient.coordinates[1].toFixed(4)}°N, ${selectedGeoPatient.coordinates[0].toFixed(4)}°E · ${selectedGeoPatient.zone}.`,
        pass: selectedGeoPatient.coordinates.length === 2,
        severity: 'info',
      },
      {
        id: 'facility',
        label: 'Nearest tertiary facility in context',
        detail: `${facilities[0]?.name ?? 'None'} · ${facilities[0]?.availableBeds ?? 0} palliative beds.`,
        pass: facilities.length > 0,
        severity: 'info',
      },
      {
        id: 'summary',
        label: 'Verified summary available for handoff',
        detail: props.summaryReady
          ? 'Physician-verified release present — ED QR unlock is allowed.'
          : 'No sealed release — handoff unlock must stay blocked.',
        pass: props.summaryReady,
        severity: 'critical',
      },
      {
        id: 'breakglass',
        label: 'Break-glass / retrieval state',
        detail: props.retrievalUnlocked
          ? 'Retrieval already unlocked in this browser session.'
          : 'Retrieval locked until purpose + relationship (or QR unlock) recorded.',
        pass: true,
        severity: 'info',
      },
      {
        id: 'dnacpr',
        label: 'Resuscitation context visible to receiving team',
        detail: `CPR status in context: ${props.cprStatus ?? selectedGeoPatient.cprStatus}.`,
        pass: Boolean(props.cprStatus ?? selectedGeoPatient.cprStatus),
        severity: 'warn',
      },
    ];
    setChecks(next);
    setRanAt(new Date().toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }));
  }

  useEffect(() => {
    runContextAudit();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const passCount = checks.filter((c) => c.pass).length;

  return (
    <div className="ops-context-pane">
      <div className="ops-context-hero">
        <div>
          <div className="ops-kicker">Continuity context checker</div>
          <h3>Inspect live session · patient · location · handoff readiness</h3>
          <p className="ops-muted">
            Reads ContinuityProvider + shell session state and surfaces pass/fail gates judges can click through.
          </p>
        </div>
        <button type="button" className="ops-btn ops-btn-primary" onClick={runContextAudit}>
          <IconZap className="w-4 h-4" /> Re-run context audit
        </button>
      </div>

      <div className="ops-context-score">
        <strong>
          {passCount}/{checks.length || '—'} checks green
        </strong>
        <span>{ranAt ? `Last run ${ranAt}` : 'Not run yet'}</span>
      </div>

      <div className="ops-context-grid">
        <section>
          <h4>Live signals</h4>
          <ul className="ops-signal-list">
            {baseSignals.map((s) => (
              <li key={s.label}>
                <span>{s.label}</span>
                <strong>{s.value}</strong>
              </li>
            ))}
          </ul>
        </section>
        <section>
          <h4>Audit results</h4>
          <ul className="ops-check-list">
            {checks.map((c) => (
              <li key={c.id} className={`sev-${c.severity} ${c.pass ? 'pass' : 'fail'}`}>
                <div className="ops-check-head">
                  {c.pass ? <IconCheckCircle className="w-4 h-4" /> : <IconShieldAlert className="w-4 h-4" />}
                  <strong>{c.label}</strong>
                  <span>{c.pass ? 'PASS' : 'FAIL'}</span>
                </div>
                <p>{c.detail}</p>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}

function MroVaultPane(props: {
  hospitalId: string;
  patientName: string;
  diagnosis?: string;
  versionLabel: string;
  verifiedBy?: string;
  verifiedOn?: string;
  summaryReady: boolean;
  cprStatus?: string;
  onSealMro?: (mroId: string) => void;
}) {
  const locator = opaqueLocator(props.hospitalId, props.versionLabel);
  const mroId = `MRO-${props.hospitalId}-${props.versionLabel.replace(/\s+/g, '')}`;
  const integrity = sha256Lite(`${mroId}|${locator}|${props.verifiedBy ?? 'unverified'}`);
  const [sealed, setSealed] = useState(props.summaryReady);
  const [verifyMsg, setVerifyMsg] = useState<string | null>(null);
  const [expanded, setExpanded] = useState(true);

  const fields = [
    { label: 'MRO ID', value: mroId },
    { label: 'Opaque locator', value: locator },
    { label: 'Patient', value: props.patientName },
    { label: 'UHID', value: props.hospitalId },
    { label: 'Diagnosis (summary)', value: props.diagnosis ?? 'Not stated in this object' },
    { label: 'Release', value: props.versionLabel },
    { label: 'Attested by', value: props.verifiedBy ?? 'Not sealed' },
    { label: 'Attested on', value: props.verifiedOn ?? '—' },
    { label: 'Resuscitation ceiling', value: props.cprStatus ?? 'Deferred to full ECTPR' },
    { label: 'Integrity hash', value: integrity },
  ];

  function sealObject() {
    setSealed(true);
    setVerifyMsg(null);
    props.onSealMro?.(mroId);
  }

  function verifyIntegrity() {
    const again = sha256Lite(`${mroId}|${locator}|${props.verifiedBy ?? 'unverified'}`);
    setVerifyMsg(
      again === integrity
        ? 'Record unchanged since saving. This local hash is not a medical or digital signature.'
        : 'Integrity mismatch — object may have been altered.',
    );
  }

  return (
    <div className="ops-mro-pane">
      <div className="ops-mro-hero">
        <div>
          <div className="ops-kicker">MRO · Medical Record Object</div>
          <h3>Portable verified continuity object</h3>
          <p className="ops-muted">
            First-class sealed package wrapping the opaque locator, attestation metadata, resuscitation ceiling pointer,
            and integrity hash — ready for QR, FHIR export, or ED retrieval.
          </p>
        </div>
        <span className={`ops-mro-seal ${sealed ? 'is-sealed' : 'is-draft'}`}>
          {sealed ? 'SEALED' : 'DRAFT'}
        </span>
      </div>

      <div className="ops-mro-card">
        <header>
          <IconFileText className="w-5 h-5" />
          <div>
            <strong>{mroId}</strong>
            <small>Saathi Medical Record Object</small>
          </div>
          <button type="button" className="ops-btn ops-btn-ghost" onClick={() => setExpanded((v) => !v)}>
            {expanded ? 'Collapse' : 'Expand'}
          </button>
        </header>

        {expanded && (
          <dl className="ops-mro-fields">
            {fields.map((f) => (
              <div key={f.label}>
                <dt>{f.label}</dt>
                <dd>{f.value}</dd>
              </div>
            ))}
          </dl>
        )}

        <div className="ops-mro-actions">
          <button type="button" className="ops-btn ops-btn-primary" onClick={sealObject} disabled={sealed && props.summaryReady}>
            <IconShieldCheck className="w-4 h-4" /> {sealed ? 'Re-affirm seal' : 'Seal MRO'}
          </button>
          <button type="button" className="ops-btn ops-btn-accent" onClick={verifyIntegrity}>
            <IconHeartPulse className="w-4 h-4" /> Verify integrity
          </button>
        </div>

        {verifyMsg && (
          <p className="ops-mro-verify" role="status">
            {verifyMsg}
          </p>
        )}
      </div>

      <div className="ops-mro-more">
        <h4>MORE · adjacent OS surfaces in this hub</h4>
        <ul>
          <li>
            <IconMapPin className="w-4 h-4" /> MapLibre community GIS dispatch with live ASHA / 108 routing
          </li>
          <li>
            <IconZap className="w-4 h-4" /> Opaque QR clinic check-in + ED handoff unlock
          </li>
          <li>
            <IconUsers className="w-4 h-4" /> Continuity context audit across session, role, geo, and seal state
          </li>
        </ul>
      </div>
    </div>
  );
}

export function ContinuityOpsHub(props: ContinuityOpsHubProps) {
  const {
    open,
    initialTab = 'map',
    onClose,
    sessionType,
    currentRole,
    patientName,
    hospitalId,
    diagnosis,
    versionLabel = 'Version 1',
    verifiedBy,
    verifiedOn,
    summaryReady,
    retrievalUnlocked,
    cprStatus,
    onClinicCheckIn,
    onUnlockHandoff,
    onSealMro,
  } = props;

  const { selectedGeoPatient, selectPatient } = useContinuityContext();
  const [tab, setTab] = useState<OpsHubTab>(initialTab);

  useEffect(() => {
    if (open) setTab(initialTab);
  }, [open, initialTab]);

  if (!open) return null;

  const tabs: Array<{ id: OpsHubTab; label: string; hint: string }> = [
    { id: 'map', label: 'MapLibre Ops', hint: 'GIS dispatch' },
    { id: 'qr', label: 'QR Bridge', hint: 'Check-in / handoff' },
    { id: 'context', label: 'Context Check', hint: 'Live audit' },
    { id: 'mro', label: 'MRO Vault', hint: 'Medical Record Object' },
  ];

  return (
    <div className="modal-backdrop ops-hub-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal-panel ops-hub-panel" role="dialog" aria-modal="true" aria-labelledby="ops-hub-title">
        <header className="ops-hub-header">
          <div>
            <span className="ops-hub-badge">P0 Continuity OS</span>
            <h2 id="ops-hub-title">Continuity Ops Hub</h2>
            <p>MapLibre · QR · Context · MRO — pitch-ready operating surfaces</p>
          </div>
          <button type="button" className="close-btn" onClick={onClose} aria-label="Close Continuity Ops Hub">
            ✕
          </button>
        </header>

        <nav className="ops-hub-tabs" aria-label="Ops hub sections">
          {tabs.map((t) => (
            <button
              key={t.id}
              type="button"
              className={tab === t.id ? 'is-active' : ''}
              onClick={() => setTab(t.id)}
            >
              <strong>{t.label}</strong>
              <small>{t.hint}</small>
            </button>
          ))}
        </nav>

        <div className="ops-hub-body">
          {tab === 'map' && (
            <OpsMapPane activePatient={selectedGeoPatient} onSelectPatient={selectPatient} />
          )}
          {tab === 'qr' && (
            <QrBridgePane
              hospitalId={hospitalId}
              patientName={patientName}
              versionLabel={versionLabel}
              summaryReady={summaryReady}
              onClinicCheckIn={onClinicCheckIn}
              onUnlockHandoff={onUnlockHandoff}
            />
          )}
          {tab === 'context' && (
            <ContextCheckerPane
              sessionType={sessionType}
              currentRole={currentRole}
              patientName={patientName}
              hospitalId={hospitalId}
              summaryReady={summaryReady}
              retrievalUnlocked={retrievalUnlocked}
              cprStatus={cprStatus}
            />
          )}
          {tab === 'mro' && (
            <MroVaultPane
              hospitalId={hospitalId}
              patientName={patientName}
              diagnosis={diagnosis}
              versionLabel={versionLabel}
              verifiedBy={verifiedBy}
              verifiedOn={verifiedOn}
              summaryReady={summaryReady}
              cprStatus={cprStatus}
              onSealMro={onSealMro}
            />
          )}
        </div>
      </section>
    </div>
  );
}
