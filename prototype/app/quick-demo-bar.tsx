'use client';

import React, { useState } from 'react';
import { IconZap, IconStethoscope, IconShieldAlert, IconUsers, IconSparkles } from './icons';

export type DemoRoleOption =
  | 'dr-sujay'
  | 'dr-isha'
  | 'anitha'
  | 'patient'
  | 'family';

export function QuickDemoBar(props: {
  currentSession: 'care-team' | 'family' | 'patient';
  currentRole: string;
  onSelectRole: (option: DemoRoleOption) => void;
  onGoHome: () => void;
  onFastAction?: (action: 'break-glass' | 'verify-release' | 'patient-consent') => void;
}) {
  const { currentSession, currentRole, onSelectRole, onGoHome, onFastAction } = props;
  const [collapsed, setCollapsed] = useState(false);

  const activeId: DemoRoleOption =
    currentSession === 'patient'
      ? 'patient'
      : currentSession === 'family'
      ? 'family'
      : currentRole.startsWith('Dr Isha')
      ? 'dr-isha'
      : currentRole.startsWith('Anitha')
      ? 'anitha'
      : 'dr-sujay';

  return (
    <aside className={`quick-demo-bar ${collapsed ? 'is-collapsed' : ''}`} aria-label="Quick scenario navigator">
      <div className="quick-demo-bar-inner">
        <div className="quick-demo-branding">
          <span className="quick-demo-pill">
            <IconZap className="w-3.5 h-3.5 text-amber-500 animate-pulse" />
            <span>1-Click Switcher</span>
          </span>
        </div>

        <div className="quick-demo-roles">
          <button
            type="button"
            className={`quick-demo-btn ${activeId === 'dr-sujay' ? 'is-active' : ''}`}
            onClick={() => onSelectRole('dr-sujay')}
            title="Switch to Dr Sujay (Clinical Lead - Consultation & Verification)"
          >
            <span className="demo-avatar doctor">SS</span>
            <span className="demo-label">
              <strong>Dr Sujay</strong>
              <small>Clinical Lead</small>
            </span>
          </button>

          <button
            type="button"
            className={`quick-demo-btn emergency-btn ${activeId === 'dr-isha' ? 'is-active' : ''}`}
            onClick={() => onSelectRole('dr-isha')}
            title="Switch to Dr Isha Menon (Emergency Break-Glass Retrieval)"
          >
            <span className="demo-avatar emergency">IM</span>
            <span className="demo-label">
              <strong>Dr Isha</strong>
              <small className="text-rose-600 font-semibold">Emergency ED 🚨</small>
            </span>
          </button>

          <button
            type="button"
            className={`quick-demo-btn ${activeId === 'anitha' ? 'is-active' : ''}`}
            onClick={() => onSelectRole('anitha')}
            title="Switch to Anitha Rao (Care Coordinator - Outreach & Handoffs)"
          >
            <span className="demo-avatar coordinator">AR</span>
            <span className="demo-label">
              <strong>Anitha Rao</strong>
              <small>Coordinator</small>
            </span>
          </button>

          <div className="demo-divider" aria-hidden="true" />

          <button
            type="button"
            className={`quick-demo-btn patient-btn ${activeId === 'patient' ? 'is-active' : ''}`}
            onClick={() => onSelectRole('patient')}
            title="Switch to Patient Portal (Voice Audio & Plain Language Plan)"
          >
            <span className="demo-avatar patient">PT</span>
            <span className="demo-label">
              <strong>Patient Portal</strong>
              <small>Audio + Plan 🔊</small>
            </span>
          </button>

          <button
            type="button"
            className={`quick-demo-btn ${activeId === 'family' ? 'is-active' : ''}`}
            onClick={() => onSelectRole('family')}
            title="Switch to Family Caregiver Portal"
          >
            <span className="demo-avatar family">FM</span>
            <span className="demo-label">
              <strong>Family Portal</strong>
              <small>Caregiver</small>
            </span>
          </button>
        </div>

        <div className="quick-demo-actions">
          <button
            type="button"
            className="quick-demo-home-btn"
            onClick={onGoHome}
            title="Return to Landing Page"
          >
            Landing
          </button>
          <button
            type="button"
            className="quick-demo-collapse-btn"
            onClick={() => setCollapsed(!collapsed)}
            aria-label={collapsed ? 'Expand fast switcher' : 'Minimize fast switcher'}
          >
            {collapsed ? '⚡ Expand' : '✕'}
          </button>
        </div>
      </div>
    </aside>
  );
}
