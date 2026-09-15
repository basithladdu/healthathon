'use client';

import React, { useState } from 'react';
import { IconCheckCircle, IconFileText, IconZap } from './icons';

export type FhirBundle = {
  resourceType: 'Bundle';
  id: string;
  type: 'document';
  timestamp: string;
  entry: Array<{
    fullUrl: string;
    resource: Record<string, unknown>;
  }>;
};

export function generateAbdmFhirBundle(patient: {
  hospitalId: string;
  name: string;
  dob?: string;
  diagnosis?: string;
  versionLabel?: string;
  verifiedBy?: string;
  verifiedOn?: string;
  fields?: Array<{ label: string; value: string }>;
}): FhirBundle {
  const timestamp = new Date().toISOString();
  const patientId = patient.hospitalId;

  return {
    resourceType: 'Bundle',
    id: `abdm-continuity-${patientId}-${Date.now()}`,
    type: 'document',
    timestamp,
    entry: [
      {
        fullUrl: `urn:uuid:patient-${patientId}`,
        resource: {
          resourceType: 'Patient',
          id: patientId,
          identifier: [
            {
              system: 'https://healthid.abdm.gov.in',
              value: `ABHA-${patientId.replace(/[^0-9]/g, '') || '9182736450'}`,
              type: { text: 'ABHA ID' },
            },
            {
              system: 'https://hospital.org/uhid',
              value: patientId,
              type: { text: 'Hospital UHID' },
            },
          ],
          name: [{ text: patient.name }],
          gender: 'female',
          birthDate: patient.dob ? patient.dob.split('/').reverse().join('-') : '1958-06-14',
          active: true,
        },
      },
      {
        fullUrl: `urn:uuid:practitioner-sujay`,
        resource: {
          resourceType: 'Practitioner',
          id: 'dr-sujay',
          identifier: [
            {
              system: 'https://nmc.org.in',
              value: 'NMC-2014-99821',
              type: { text: 'NMC Registration' },
            },
          ],
          name: [{ text: patient.verifiedBy || 'Dr Sujay' }],
          qualification: [{ code: { text: 'MD (Oncology), Palliative Care Fellowship' } }],
        },
      },
      {
        fullUrl: `urn:uuid:careplan-${patientId}`,
        resource: {
          resourceType: 'CarePlan',
          id: `careplan-${patientId}-v1`,
          status: 'active',
          intent: 'plan',
          category: [
            {
              coding: [
                {
                  system: 'http://snomed.info/sct',
                  code: '736353004',
                  display: 'Advance care plan',
                },
              ],
              text: 'Goals-of-Care & Continuity Plan',
            },
          ],
          title: `Verified Goals of Care Plan · ${patient.versionLabel || 'Version 1'}`,
          description: patient.diagnosis ? `Primary diagnosis: ${patient.diagnosis}` : 'Continuity Care Plan',
          subject: { reference: `Patient/${patientId}` },
          author: { reference: 'Practitioner/dr-sujay' },
          created: patient.verifiedOn || timestamp,
          activity: (patient.fields || []).map((f, i) => ({
            detail: {
              kind: 'CommunicationRequest',
              status: 'in-progress',
              description: `${f.label}: ${f.value}`,
            },
          })),
        },
      },
      {
        fullUrl: `urn:uuid:consent-${patientId}`,
        resource: {
          resourceType: 'Consent',
          id: `consent-${patientId}`,
          status: 'active',
          scope: {
            coding: [{ system: 'http://terminology.hl7.org/CodeSystem/consentscope', code: 'patient-privacy' }],
          },
          category: [
            {
              coding: [{ system: 'http://terminology.hl7.org/CodeSystem/consentcategorycodes', code: 'acd' }],
              text: 'Advance Care Directive & Goals-of-Care Preference',
            },
          ],
          patient: { reference: `Patient/${patientId}` },
          dateTime: timestamp,
          policy: [{ uri: 'https://abdm.gov.in/consent-policy/continuity-v1' }],
        },
      },
    ],
  };
}

export function FhirExportModal(props: {
  patient: {
    hospitalId: string;
    name: string;
    dob?: string;
    diagnosis?: string;
    versionLabel?: string;
    verifiedBy?: string;
    verifiedOn?: string;
    fields?: Array<{ label: string; value: string }>;
  };
  onClose: () => void;
}) {
  const { patient, onClose } = props;
  const [activeTab, setActiveTab] = useState<'preview' | 'json'>('preview');
  const [copied, setCopied] = useState(false);

  const bundle = generateAbdmFhirBundle(patient);
  const jsonString = JSON.stringify(bundle, null, 2);

  const handleCopy = () => {
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      navigator.clipboard.writeText(jsonString);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleDownload = () => {
    const blob = new Blob([jsonString], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ABDM_FHIR_R4_${patient.hospitalId}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal-panel large-modal fhir-modal" role="dialog" aria-modal="true" aria-labelledby="fhir-modal-title">
        <div className="modal-header">
          <div className="flex items-center gap-3">
            <span className="fhir-badge">ABDM · FHIR R4</span>
            <div>
              <h2 id="fhir-modal-title">Interoperable Health Record Export</h2>
              <p className="text-xs text-emerald-800">Ayushman Bharat Digital Mission (ABDM) Compatible Bundle</p>
            </div>
          </div>
          <button type="button" className="close-btn" onClick={onClose} aria-label="Close modal">✕</button>
        </div>

        <div className="fhir-tabs">
          <button
            type="button"
            className={`fhir-tab-btn ${activeTab === 'preview' ? 'is-active' : ''}`}
            onClick={() => setActiveTab('preview')}
          >
            Structured FHIR Resources
          </button>
          <button
            type="button"
            className={`fhir-tab-btn ${activeTab === 'json' ? 'is-active' : ''}`}
            onClick={() => setActiveTab('json')}
          >
            Raw FHIR R4 JSON
          </button>
        </div>

        <div className="fhir-body">
          {activeTab === 'preview' ? (
            <div className="fhir-resource-tree">
              <div className="fhir-resource-card">
                <div className="fhir-card-title">
                  <span className="resource-pill patient">Patient</span>
                  <strong>{patient.name} ({patient.hospitalId})</strong>
                </div>
                <div className="fhir-card-content">
                  <div><strong>ABHA ID:</strong> ABHA-{patient.hospitalId.replace(/[^0-9]/g, '') || '9182736450'}</div>
                  <div><strong>DOB:</strong> {patient.dob || '14/06/1958'} · <strong>Gender:</strong> Female</div>
                  <div><strong>Identifier System:</strong> https://healthid.abdm.gov.in</div>
                </div>
              </div>

              <div className="fhir-resource-card">
                <div className="fhir-card-title">
                  <span className="resource-pill practitioner">Practitioner</span>
                  <strong>{patient.verifiedBy || 'Dr Sujay'}</strong>
                </div>
                <div className="fhir-card-content">
                  <div><strong>Registration:</strong> NMC-2014-99821 (National Medical Commission)</div>
                  <div><strong>Role:</strong> Attending Oncology &amp; Palliative Lead</div>
                </div>
              </div>

              <div className="fhir-resource-card">
                <div className="fhir-card-title">
                  <span className="resource-pill careplan">CarePlan (SNOMED 736353004)</span>
                  <strong>Goals of Care Plan ({patient.versionLabel || 'Version 1'})</strong>
                </div>
                <div className="fhir-card-content">
                  <div><strong>Status:</strong> Active · <strong>Intent:</strong> Plan</div>
                  <div className="mt-2 text-xs font-semibold text-slate-600">Recorded Preferences (FHIR Activities):</div>
                  <ul className="fhir-activity-list">
                    {(patient.fields || []).map((f) => (
                      <li key={f.label}>
                        <strong>{f.label}:</strong> <span>{f.value}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              </div>

              <div className="fhir-resource-card">
                <div className="fhir-card-title">
                  <span className="resource-pill consent">Consent</span>
                  <strong>Patient Verification &amp; Privacy Policy</strong>
                </div>
                <div className="fhir-card-content">
                  <div><strong>Status:</strong> Active · <strong>Scope:</strong> patient-privacy (acd)</div>
                  <div><strong>Policy URI:</strong> https://abdm.gov.in/consent-policy/continuity-v1</div>
                </div>
              </div>
            </div>
          ) : (
            <div className="fhir-json-wrap">
              <pre className="fhir-json-code"><code>{jsonString}</code></pre>
            </div>
          )}
        </div>

        <div className="fhir-footer">
          <div className="flex items-center gap-2">
            <button type="button" className="secondary-button" onClick={handleCopy}>
              {copied ? '✓ Copied to Clipboard' : 'Copy JSON'}
            </button>
            <button type="button" className="primary-button" onClick={handleDownload}>
              Download ABDM Bundle (.json)
            </button>
          </div>
          <button type="button" className="secondary-button" onClick={onClose}>
            Close
          </button>
        </div>
      </section>
    </div>
  );
}
