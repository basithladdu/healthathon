'use client';

import React, { useEffect, useRef, useState } from 'react';

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
    title: 'Append-only version',
    body: 'A changed preference creates a new version. The previous one is never edited in place.',
  },
  {
    num: '05',
    title: 'Purpose-logged retrieval',
    body: 'A receiving clinician records the clinical reason and care relationship before the record opens. Break-glass access is a distinct, recorded state.',
  },
  {
    num: '06',
    title: 'Audit trail',
    body: 'Every release and every access appears in the trail, with the stated purpose attached.',
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
  'All names, identifiers and events are synthetic. State is browser-local and resets on reload.',
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

  const heroRef = useRef<HTMLElement>(null);
  const navRef = useRef<HTMLElement>(null);
  const [navState, setNavState] = useState<'top' | 'hero' | 'light'>('top');

  useEffect(() => {
    let rafId: number | null = null;

    const compute = () => {
      rafId = null;
      const scrollY = window.scrollY;
      const hero = heroRef.current;
      const navHeight = navRef.current?.offsetHeight ?? 64;

      if (scrollY < 8) {
        setNavState('top');
        return;
      }

      const heroBottom = hero ? hero.offsetTop + hero.offsetHeight : 0;
      if (scrollY + navHeight < heroBottom) {
        setNavState('hero');
      } else {
        setNavState('light');
      }
    };

    const onScroll = () => {
      if (rafId === null) {
        rafId = window.requestAnimationFrame(compute);
      }
    };

    compute();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);

    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      if (rafId !== null) {
        window.cancelAnimationFrame(rafId);
      }
    };
  }, []);

  return (
    <div className="lp-page">
      <nav className="lp-nav" data-state={navState} aria-label="Primary" ref={navRef}>
        <div className="lp-nav-inner">
          <div className="lp-nav-brand">
            <div className="lp-nav-mark" aria-hidden="true">CL</div>
            <div className="lp-nav-titles">
              <strong>Continuity Loop</strong>
              <span>Goals-of-Care Workflow</span>
            </div>
          </div>
          <div className="lp-nav-actions">
            <button
              type="button"
              className="lp-nav-link"
              onClick={goToPatient}
              aria-label="Patients and families"
            >
              <span className="lp-family-full">Patients &amp; families</span>
              <span className="lp-family-short" aria-hidden="true">Patients</span>
            </button>
            <button type="button" className="lp-nav-link" onClick={onSignIn}>
              Sign in
            </button>
            <button type="button" className="lp-nav-cta" onClick={onSignUp}>
              Create account
            </button>
          </div>
        </div>
      </nav>

      <header className="lp-hero" ref={heroRef}>
        <div className="lp-hero-noise" aria-hidden="true" />
        <div className="lp-hero-inner">
          <span className="lp-eyebrow lp-eyebrow-dark">Goals-of-care continuity</span>
          <h1 className="lp-hero-title">The conversation survives the handoff.</h1>
          <p className="lp-hero-lede">
            When care changes at night, the receiving physician should not have to restart a
            difficult conversation. Continuity Loop turns a clinician-led goals-of-care
            discussion into a source-linked, physician-verified summary that the next clinician
            can retrieve in seconds.
          </p>
          <div className="lp-hero-ctas">
            <button type="button" className="lp-btn lp-btn-solid-white lp-btn-lg" onClick={onSignIn}>
              Care team sign in
            </button>
            <button type="button" className="lp-btn lp-btn-ghost-light lp-btn-lg" onClick={goToPatient}>
              Patient &amp; family access
            </button>
          </div>
          <div className="lp-hero-subactions">
            <button type="button" className="lp-hero-textlink" onClick={onSignUp}>
              New to the care team? <span className="lp-hero-textlink-strong">Create account</span>
            </button>
            <span className="lp-hero-subsep" aria-hidden="true">·</span>
            <button type="button" className="lp-explore-link" onClick={onExploreDemo}>
              Skip sign-in and explore the demo →
            </button>
          </div>
          <div className="lp-footnote">
            <span className="lp-dot" aria-hidden="true" />
            Synthetic demonstration environment · No real patient data
          </div>
        </div>
      </header>

      <section className="lp-doors">
        <div className="lp-section-inner">
          <span className="lp-eyebrow">Three ways in</span>
          <h2 className="lp-h2">Choose your door.</h2>
          <p className="lp-limits-lede">The care team, patients and families see different parts of the same record.</p>
          <div className="lp-doors-grid">
            <div className="lp-door-card is-care-team">
              <span className="lp-door-chip is-care-team">Care team</span>
              <h3 className="lp-card-title">Doctors, coordinators and emergency physicians</h3>
              <p className="lp-card-body">
                Guide the conversation, verify the summary, manage follow-up, and retrieve a record
                when care changes hands.
              </p>
              <ul className="lp-door-list">
                <li>Guided conversation and source-linked drafts</li>
                <li>Physician verification before anything is released</li>
                <li>Purpose-logged emergency retrieval</li>
              </ul>
              <div className="lp-door-actions">
                <button type="button" className="lp-btn lp-btn-door-primary" onClick={onSignIn}>
                  Sign in
                </button>
                <button type="button" className="lp-btn lp-btn-door-secondary" onClick={onSignUp}>
                  Create account
                </button>
              </div>
            </div>
            <div className="lp-door-card is-patient">
              <span className="lp-door-chip is-patient">Patient</span>
              <h3 className="lp-card-title">See and carry your care plan</h3>
              <p className="lp-card-body">
                Read the plan your doctor verified, issue your emergency card, find care near you,
                and keep your details up to date.
              </p>
              <ul className="lp-door-list">
                <li>Your verified care plan</li>
                <li>Emergency card with QR code</li>
                <li>Care near you on a map</li>
                <li>Your details and consent</li>
              </ul>
              <div className="lp-door-actions">
                <button type="button" className="lp-btn lp-btn-door-primary" onClick={goToPatient}>
                  Patient sign-in
                </button>
              </div>
            </div>
            <div className="lp-door-card is-family">
              <span className="lp-door-chip is-family">Family</span>
              <h3 className="lp-card-title">Family members and caregivers</h3>
              <p className="lp-card-body">
                See what the care team has confirmed, what happens next, and who to contact.
                Read-only, so nothing can be changed by mistake.
              </p>
              <ul className="lp-door-list">
                <li>The summary your doctor approved, in plain language</li>
                <li>Your next visit and how to prepare</li>
                <li>Who is on the care team</li>
              </ul>
              <div className="lp-door-actions">
                <button type="button" className="lp-btn lp-btn-door-family" onClick={onFamilyAccess}>
                  Family access
                </button>
              </div>
              <p className="lp-door-note">No account needed in this demo.</p>
            </div>
          </div>
        </div>
      </section>

      <section className="lp-steps">
        <div className="lp-section-inner">
          <span className="lp-eyebrow">The loop</span>
          <h2 className="lp-h2">Six steps, each one accountable.</h2>
          <div className="lp-steps-grid">
            {STEPS.map((step) => (
              <div className="lp-step-card" key={step.num}>
                <span className="lp-step-num">{step.num}</span>
                <h3 className="lp-card-title">{step.title}</h3>
                <p className="lp-card-body">{step.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="lp-roles">
        <div className="lp-section-inner">
          <span className="lp-eyebrow">Roles</span>
          <h2 className="lp-h2">Four people, one record.</h2>
          <div className="lp-roles-grid">
            {ROLES.map((role) => (
              <div className="lp-role-card" key={role.title}>
                <span className="lp-role-avatar" aria-hidden="true">{role.initials}</span>
                <h3 className="lp-card-title">{role.title}</h3>
                <p className="lp-card-body">{role.body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="lp-limits">
        <div className="lp-section-inner">
          <span className="lp-eyebrow">Boundaries</span>
          <h2 className="lp-h2">What this prototype does not do.</h2>
          <p className="lp-limits-lede">Being explicit about the limits is part of the design.</p>
          <ul className="lp-limits-list">
            {LIMITS.map((item) => (
              <li className="lp-limits-item" key={item}>{item}</li>
            ))}
          </ul>
        </div>
      </section>

      <footer className="lp-footer">
        <div className="lp-footer-inner">
          <div className="lp-footer-brand">
            <div className="lp-nav-brand">
              <div className="lp-nav-mark" aria-hidden="true">CL</div>
              <div className="lp-nav-titles">
                <strong>Continuity Loop</strong>
                <span>Goals-of-Care Workflow</span>
              </div>
            </div>
            <p className="lp-footer-sub">Health-a-thon 2026 prototype</p>
          </div>
          <div className="lp-footer-links">
            <button type="button" className="lp-footer-link" onClick={onSignIn}>
              Sign in
            </button>
            <button type="button" className="lp-footer-link" onClick={onSignUp}>
              Create account
            </button>
            <button type="button" className="lp-footer-link" onClick={onFamilyAccess}>
              Family access
            </button>
            <button type="button" className="lp-footer-link" onClick={goToPatient}>
              Patient access
            </button>
          </div>
        </div>
      </footer>
    </div>
  );
}
