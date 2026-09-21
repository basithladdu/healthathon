'use client';

import React, { useState, useEffect } from 'react';
import { IconShieldCheck, IconVolume2, IconHeartPulse, IconLungs, IconHospital, IconZap } from './icons';
import { useSpeechSynthesis } from './tts-speech';
import type { EctprQuickView } from './ectpr';

export function OfflineQrScannerModal(props: {
  record: EctprQuickView;
  checksum: string | null;
  onClose: () => void;
}) {
  const { record, checksum, onClose } = props;
  const [isScanning, setIsScanning] = useState(true);
  const [verified, setVerified] = useState(false);
  const { speak, isPlaying, cancel } = useSpeechSynthesis();

  useEffect(() => {
    const timer = setTimeout(() => {
      setIsScanning(false);
      setVerified(true);
    }, 1200);
    return () => clearTimeout(timer);
  }, []);

  const emergencyVoiceText = `Saved care preferences. Patient: ${record.patientName}. UHID: ${record.hospitalId}. Resuscitation preference: ${record.cpr === 'dnacpr' ? 'Do not attempt CPR' : record.cpr === 'attempt' ? 'Attempt CPR' : 'Not recorded'}. Breathing support preference: ${record.breathingCeiling || 'Not recorded'}. Hospital transfer preference: ${record.hospitalTransfer || 'Not recorded'}. This card is not a signed medical order or legal directive.`;

  const handleVoiceBroadcast = () => {
    if (isPlaying) {
      cancel();
    } else {
      speak(emergencyVoiceText, 1.0);
    }
  };

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <section className="modal-panel offline-scanner-modal" role="dialog" aria-modal="true" aria-labelledby="scanner-modal-title">
        <div className="modal-header">
          <div className="flex items-center gap-2">
            <span className="scanner-badge">Saved care card</span>
            <h2 id="scanner-modal-title">Emergency QR code</h2>
          </div>
          <button type="button" className="close-btn" onClick={onClose} aria-label="Close modal">✕</button>
        </div>

        <div className="scanner-content">
          {isScanning ? (
            <div className="scanner-viewfinder">
              <div className="scanner-laser" />
              <div className="scanner-target-box">
                <span className="corner top-left" />
                <span className="corner top-right" />
                <span className="corner bottom-left" />
                <span className="corner bottom-right" />
              </div>
              <p className="scanner-status-text">Opening saved care card…</p>
            </div>
          ) : (
            <div className="scanner-result-view">
              <div className="scanner-verification-banner">
                <IconShieldCheck className="w-5 h-5 text-emerald-600 flex-shrink-0" />
                <div>
                  <strong>Saved card opened</strong>
                  <p>Record hash: {checksum ? `${checksum.slice(0, 8)}…${checksum.slice(-8)}` : 'Not recorded'} · No identity or signature verification</p>
                </div>
              </div>

              <div className="scanner-triage-grid">
                <div className={`triage-card ${record.cpr === 'dnacpr' ? 'is-dnacpr' : 'is-cpr'}`}>
                  <div className="triage-card-header">
                    <IconHeartPulse className="w-4 h-4" />
                    <span>Recorded CPR preference</span>
                  </div>
                  <div className="triage-card-value">
                    {record.cpr === 'dnacpr' ? 'Do not attempt CPR' : record.cpr === 'attempt' ? 'Attempt CPR' : 'Not recorded'}
                  </div>
                  <small className="triage-card-note">
                    Preference recorded on this card
                  </small>
                </div>

                <div className="triage-card is-breathing">
                  <div className="triage-card-header">
                    <IconLungs className="w-4 h-4" />
                    <span>Recorded breathing support</span>
                  </div>
                  <div className="triage-card-value">
                    {record.breathingCeiling ? record.breathingCeiling.replaceAll('-', ' ') : 'Not recorded'}
                  </div>
                  <small className="triage-card-note">Preference recorded on this card</small>
                </div>

                <div className="triage-card is-transfer">
                  <div className="triage-card-header">
                    <IconHospital className="w-4 h-4" />
                    <span>Recorded hospital transfer</span>
                  </div>
                  <div className="triage-card-value">
                    {record.hospitalTransfer === 'no' ? 'No transfer' : record.hospitalTransfer === 'any-deterioration' ? 'For any deterioration' : record.hospitalTransfer === 'listed-reasons-only' ? 'Only for listed reasons' : 'Not recorded'}
                  </div>
                  <small className="triage-card-note">No transport has been requested</small>
                </div>
              </div>

              <div className="scanner-patient-strip">
                <div><strong>Patient:</strong> {record.patientName} (UHID: {record.hospitalId})</div>
                <div><strong>Diagnosis:</strong> {record.diagnosis}</div>
                <div><strong>Named Proxy:</strong> {record.decisionMaker?.name} ({record.decisionMaker?.relationship})</div>
                <div><strong>Consultant:</strong> {record.treatingConsultant || 'Not recorded'}</div>
              </div>

              <div className="scanner-rule-callout">
                <strong>Recorded preferences.</strong> This card is not a signed medical order or legal directive. Confirm the current plan with the care team.
              </div>

              <div className="scanner-actions">
                <button
                  type="button"
                  className="primary-button flex items-center justify-center gap-2 w-full py-3"
                  onClick={handleVoiceBroadcast}
                >
                  <IconVolume2 className="w-4 h-4" />
                  <span>{isPlaying ? 'Stop reading' : 'Read care card aloud'}</span>
                </button>
              </div>
            </div>
          )}
        </div>

        <div className="scanner-footer">
          <button type="button" className="secondary-button" onClick={() => setIsScanning(true)}>
            Open care card again
          </button>
          <button type="button" className="secondary-button" onClick={onClose}>
            Close
          </button>
        </div>
      </section>
    </div>
  );
}
