'use client';

import React from 'react';
import Image from 'next/image';
import {
  IconZap,
  IconVolume2,
  IconShieldAlert,
  IconStethoscope,
  IconCheckCircle,
  IconArrowRight,
  IconHeartPulse,
} from './icons';

const STEPS: { num: string; title: string; body: string }[] = [
  {
    num: '01',
    title: 'Guided conversation',
    body: 'The treating clinician works through eight goals-of-care domains. Each is marked discussed, not discussed, or defer.',
  },
  {
    num: '02',
    title: 'Source-linked draft',
    body: 'Every structured field points back to an exact sentence in the conversation note. Silence is recorded as not discussed, never converted into a preference.',
  },
  {
    num: '03',
    title: 'Physician verification',
    body: 'Four mandatory checks, a patient or surrogate authorisation, and a physician attestation before anything is released.',
  },
  {
    num: '04',
    title: 'Versioned summary',
    body: 'Each release stays available as its own version. Compare versions side by side with audit traceability.',
  },
  {
    num: '05',
    title: 'Open a note & Break-glass',
    body: 'A receiving clinician records the clinical reason and care relationship before the record opens. Emergency viewing is recorded separately.',
  },
  {
    num: '06',
    title: 'Voice TTS & Wallet Card',
    body: 'Patients and caregivers can listen to their plan via open-source audio TTS and print an offline checksummed wallet card.',
  },
];

const ROLES: { initials: string; title: string; roleKey: 'dr-sujay' | 'dr-isha' | 'anitha' | 'patient' | 'family'; badge: string; body: string }[] = [
  {
    initials: 'TC',
    title: 'Treating clinician',
    roleKey: 'dr-sujay',
    badge: 'Dr Sujay · Lead',
    body: 'Guides the conversation, prepares the draft, and is the only role that can release a version.',
  },
  {
    initials: 'EP',
    title: 'Emergency physician',
    roleKey: 'dr-isha',
    badge: 'Dr Isha · Break-Glass',
    body: 'Retrieves the latest verified handoff with break-glass protocol in emergency resuscitation.',
  },
  {
    initials: 'CC',
    title: 'Care coordinator',
    roleKey: 'anitha',
    badge: 'Anitha Rao · Outreach',
    body: 'Owns follow-up, records outreach outcomes, and routes clinical questions back to the named clinician.',
  },
  {
    initials: 'PT',
    title: 'Patient Portal',
    roleKey: 'patient',
    badge: 'Sunita Sharma · Patient',
    body: 'Plain-language view of confirmed care with built-in voice audio TTS for accessibility.',
  },
  {
    initials: 'FM',
    title: 'Family & Caregiver',
    roleKey: 'family',
    badge: 'Kavya Raghavan · Proxy',
    body: 'Transparent read-only view of what the care team has confirmed, released only after physician review.',
  },
];

const LIMITS: string[] = [
  'Does not diagnose, estimate prognosis, or recommend treatment.',
  'Does not create a DNAR order, an Advance Medical Directive, or any legally binding directive.',
  'Does not select patients or infer clinical urgency.',
  'Does not infer a treatment preference from silence.',
  'No live AI model, hospital system, messaging channel, or ABDM connection is active.',
  'Sample names, identifiers and events are used. State is browser-local and resets on reload.',
];

export function LandingPage(props: {
  onSignIn: () => void;
  onSignUp: () => void;
  onExploreDemo: () => void;
  onFamilyAccess: () => void;
  onPatientAccess?: () => void;
  onQuickEnterRole?: (role: 'dr-sujay' | 'dr-isha' | 'anitha' | 'patient' | 'family') => void;
}): React.JSX.Element {
  const { onSignIn, onSignUp, onExploreDemo, onFamilyAccess, onPatientAccess, onQuickEnterRole } = props;
  const goToPatient = onPatientAccess ?? onFamilyAccess;

  const handleQuickRole = (role: 'dr-sujay' | 'dr-isha' | 'anitha' | 'patient' | 'family') => {
    if (onQuickEnterRole) {
      onQuickEnterRole(role);
    } else {
      if (role === 'patient') goToPatient();
      else if (role === 'family') onFamilyAccess();
      else onSignIn();
    }
  };

  return (
    <div className="lp-page">
      <header className="lp-nav">
        <div className="lp-brand">
          <span className="lp-brand-mark" aria-hidden="true">CL</span>
          <div>
            <strong>Continuity Loop</strong>
            <span className="lp-brand-sub">Verified Goals of Care</span>
          </div>
        </div>
        <div className="lp-nav-actions">
          <button type="button" className="lp-nav-btn lp-nav-btn-highlight" onClick={() => handleQuickRole('dr-sujay')}>
            <IconZap className="w-3.5 h-3.5 text-emerald-700" />
            <span>1-Click Demo</span>
          </button>
          <button type="button" className="lp-nav-btn" onClick={onSignIn}>
            Sign in
          </button>
        </div>
      </header>

      <main className="lp-main">
        {/* Hero Section */}
        <section className="lp-hero" aria-labelledby="lp-hero-title">
          <div className="lp-hero-copy">
            <div className="lp-pill-banner">
              <span className="lp-pill-dot" />
              <span>Clinician-Verified Emergency &amp; Palliative Handoff</span>
            </div>
            <h1 id="lp-hero-title">
              One care plan.<br />Every handoff.<br />
              <span className="lp-hero-accent">Zero guesswork.</span>
            </h1>
            <p className="lp-hero-desc">
              Bridging oncologists, emergency physicians, coordinators, and families with source-anchored documentation, 1-click break-glass retrieval, and accessible voice narration.
            </p>

            <div className="lp-hero-actions">
              <button type="button" className="lp-demo lp-btn-pulse" onClick={onExploreDemo}>
                <span>Launch Clinical Lead Demo</span>
                <IconArrowRight className="w-4 h-4 ml-1" />
              </button>
              <button type="button" className="lp-account-action" onClick={() => handleQuickRole('dr-isha')}>
                <IconShieldAlert className="w-4 h-4 text-rose-600" />
                <span>Test Emergency Break-Glass</span>
              </button>
            </div>

            <div className="lp-feature-chips">
              <span className="lp-chip">
                <IconVolume2 className="w-3.5 h-3.5 text-emerald-600" />
                <span>Open-Source Audio TTS</span>
              </span>
              <span className="lp-chip">
                <IconZap className="w-3.5 h-3.5 text-amber-500" />
                <span>1-Click Fast Switcher</span>
              </span>
              <span className="lp-chip">
                <IconCheckCircle className="w-3.5 h-3.5 text-teal-600" />
                <span>Physician Verified</span>
              </span>
            </div>
          </div>

          <figure className="lp-hero-media">
            <Image
              src="/care-conversation.png"
              alt="Editorial illustration of clinician listening to patient and family in consultation room."
              fill
              priority
              sizes="(max-width: 650px) 100vw, 56vw"
              className="lp-hero-img"
            />
            <div className="lp-hero-media-caption">
              <span>Goal-of-Care Dialogue: Anchored to authentic patient preferences</span>
            </div>
          </figure>
        </section>

        {/* 1-Click Fast Switcher Hub */}
        <section className="lp-fast-doors-section" aria-labelledby="lp-doors-title">
          <div className="lp-section-heading">
            <div>
              <p className="lp-kicker">1-Click Fast Switcher</p>
              <h2 id="lp-doors-title">Instant Access to Any Workflow</h2>
            </div>
            <p className="lp-section-note">No password or sign-up needed for evaluation</p>
          </div>

          <div className="lp-doors-grid">
            {ROLES.map((role) => (
              <div key={role.roleKey} className={`lp-door-card lp-door--${role.roleKey}`}>
                <div className="lp-door-header">
                  <span className="lp-door-initials">{role.initials}</span>
                  <span className="lp-door-badge">{role.badge}</span>
                </div>
                <h3 className="lp-door-title">{role.title}</h3>
                <p className="lp-door-body">{role.body}</p>
                <button
                  type="button"
                  className="lp-door-btn"
                  onClick={() => handleQuickRole(role.roleKey)}
                >
                  <IconZap className="w-3.5 h-3.5" />
                  <span>Launch 1-Click Door</span>
                </button>
              </div>
            ))}
          </div>
        </section>

        {/* Visual Story Pillars */}
        <section className="lp-visual-pillars" aria-labelledby="lp-pillars-title">
          <div className="lp-section-heading">
            <div>
              <p className="lp-kicker">Clinical Continuity In Practice</p>
              <h2 id="lp-pillars-title">Three Pillars of Care Loop</h2>
            </div>
          </div>

          <div className="lp-pillars-grid">
            <div className="lp-pillar-card">
              <div className="lp-pillar-image-wrap">
                <Image
                  src="/care-conversation.png"
                  alt="Clinician listening to patient and family."
                  fill
                  sizes="(max-width: 768px) 100vw, 33vw"
                  className="lp-pillar-img"
                />
              </div>
              <div className="lp-pillar-content">
                <span className="lp-pillar-tag">Pillar 01 · Conversation</span>
                <h3>Attentive Listening</h3>
                <p>Structured around 8 goals-of-care domains. Silence is preserved as unrecorded, never forced into preferences.</p>
              </div>
            </div>

            <div className="lp-pillar-card">
              <div className="lp-pillar-image-wrap">
                <Image
                  src="/clinician-handoff.png"
                  alt="Clinical handoff between doctors."
                  fill
                  sizes="(max-width: 768px) 100vw, 33vw"
                  className="lp-pillar-img"
                />
              </div>
              <div className="lp-pillar-content">
                <span className="lp-pillar-tag">Pillar 02 · Verification</span>
                <h3>Traceable Clinical Handoff</h3>
                <p>Physician-attested, tamper-evident checksums, and break-glass emergency retrieval with full audit accountability.</p>
              </div>
            </div>

            <div className="lp-pillar-card">
              <div className="lp-pillar-image-wrap">
                <Image
                  src="/home-followup.png"
                  alt="Coordinator follow-up at home."
                  fill
                  sizes="(max-width: 768px) 100vw, 33vw"
                  className="lp-pillar-img"
                />
              </div>
              <div className="lp-pillar-content">
                <span className="lp-pillar-tag">Pillar 03 · Community</span>
                <h3>Continuous Home Support</h3>
                <p>Care coordinators keep families engaged between appointments with audio TTS guides and nearby care mapping.</p>
              </div>
            </div>
          </div>
        </section>

        {/* Details & Limits */}
        <div className="lp-details">
          <details>
            <summary>Workflow Breakdown (6 Steps)</summary>
            <div className="lp-details-grid">
              <section>
                <h3>How It Works</h3>
                <ol className="lp-info-list">
                  {STEPS.map((step) => (
                    <li key={step.num}>
                      <strong>{step.title}</strong>
                      <p>{step.body}</p>
                    </li>
                  ))}
                </ol>
              </section>
            </div>
          </details>

          <details>
            <summary>Clinical Boundaries &amp; Data Disclaimer</summary>
            <ul className="lp-info-list">
              {LIMITS.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </details>
        </div>
      </main>

      <footer className="lp-footer">
        <p>Continuity Loop · Clinician-Verified Goals of Care Platform · Sample Patient Data local to this session.</p>
      </footer>
    </div>
  );
}
