'use client';

import React from 'react';
import Image from 'next/image';

// Image source: Vitaly Gariev, Unsplash, https://unsplash.com/es/fotos/medico-consultando-con-un-paciente-en-un-consultorio-MJmsLpeHPfg (Unsplash License).

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
    body: 'Each release stays available as its own version. Compare versions side by side.',
  },
  {
    num: '05',
    title: 'Open a note',
    body: 'A receiving clinician records the clinical reason and care relationship before the record opens. Emergency viewing is recorded separately.',
  },
  {
    num: '06',
    title: 'History',
    body: 'History shows when summaries were saved and opened, and why.',
  },
];

const ROLES: { initials: string; title: string; body: string }[] = [
  {
    initials: 'TC',
    title: 'Treating clinician',
    body: 'Guides the conversation, prepares the draft, and is the only role that can release a version.',
  },
  {
    initials: 'CC',
    title: 'Care coordinator',
    body: 'Owns follow-up, records outreach outcomes, and routes clinical questions back to the named clinician.',
  },
  {
    initials: 'EP',
    title: 'Emergency physician',
    body: 'Retrieves the latest verified handoff after recording why they need it.',
  },
  {
    initials: 'PF',
    title: 'Patient and family',
    body: 'See a plain-language view of what the care team has confirmed, released only after physician review.',
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
}): React.JSX.Element {
  const { onSignIn, onSignUp, onExploreDemo, onFamilyAccess, onPatientAccess } = props;
  const goToPatient = onPatientAccess ?? onFamilyAccess;

  return (
    <div className="lp-page">
      <header className="lp-nav">
        <div className="lp-brand">
          <span className="lp-brand-mark" aria-hidden="true">CL</span>
          <strong>Continuity Loop</strong>
        </div>
      </header>

      <main className="lp-main">
        <section className="lp-hero" aria-labelledby="lp-hero-title">
          <div className="lp-hero-copy">
            <p className="lp-kicker">Clinician-led goals of care</p>
            <h1 id="lp-hero-title">One care plan.<br />Every handoff.</h1>
            <div className="lp-hero-actions">
              <button type="button" className="lp-demo" onClick={onExploreDemo}>Get started</button>
              <button type="button" className="lp-account-action" onClick={onSignUp}>Create profile</button>
            </div>
          </div>
          <figure className="lp-hero-media">
            <Image
              src="/care-consultation.jpg"
              alt="Illustrative care consultation between a clinician and a patient."
              fill
              priority
              sizes="(max-width: 650px) 100vw, 56vw"
            />
          </figure>
        </section>

        <section className="lp-workspace-section" aria-labelledby="lp-workspace-title">
          <div className="lp-section-heading">
            <h2 id="lp-workspace-title">Continue as</h2>
          </div>
          <div className="lp-workspaces">
            <button type="button" className="lp-workspace lp-workspace-care" onClick={onSignIn}>Care team <span aria-hidden="true">↗</span></button>
            <button type="button" className="lp-workspace lp-workspace-patient" onClick={goToPatient}>Patient <span aria-hidden="true">↗</span></button>
            <button type="button" className="lp-workspace lp-workspace-family" onClick={onFamilyAccess}>Family <span aria-hidden="true">↗</span></button>
          </div>
        </section>

        <div className="lp-details">
          <details>
            <summary>How it works</summary>
            <div className="lp-details-grid">
              <section>
                <h3>Workflow</h3>
                <ol className="lp-info-list">
                  {STEPS.map((step) => <li key={step.num}><strong>{step.title}</strong><p>{step.body}</p></li>)}
                </ol>
              </section>
              <section>
                <h3>Roles</h3>
                <ul className="lp-info-list">
                  {ROLES.map((role) => <li key={role.title}><strong>{role.title}</strong><p>{role.body}</p></li>)}
                </ul>
              </section>
            </div>
          </details>
          <details>
            <summary>About</summary>
            <ul className="lp-info-list">{LIMITS.map((item) => <li key={item}>{item}</li>)}</ul>
          </details>
        </div>
      </main>

      <footer className="lp-footer">Sample patient data. Changes last for this session.</footer>
    </div>
  );
}
