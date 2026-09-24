'use client';

import React from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { IconArrowRight } from './icons';

type DemoRole = 'dr-sujay' | 'dr-isha' | 'anitha' | 'patient' | 'family';
const ROLES: { title: string; roleKey: DemoRole; body: string }[] = [
  { title: 'Treating doctor', roleKey: 'dr-sujay', body: 'Guide the conversation, review the draft and release a summary.' },
  { title: 'Emergency doctor', roleKey: 'dr-isha', body: 'Record the reason for access and read the reviewed conversation.' },
  { title: 'Care coordinator', roleKey: 'anitha', body: 'Arrange follow-up and bring open questions back to the doctor.' },
  { title: 'Patient', roleKey: 'patient', body: 'Read or listen to your reviewed plan and see your care team.' },
  { title: 'Family or caregiver', roleKey: 'family', body: 'Coordinate family tasks and visits, prepare questions and find local support.' },
];
const STEPS = [
  ['Talk together', 'The doctor guides eight conversation topics, including understanding, values, people to involve and questions still to discuss.'],
  ['Write the draft', 'Each field links to the conversation note. Something not discussed stays unrecorded.'],
  ['Review together', 'The treating physician reviews the draft, checks authorisation and attests before releasing it.'],
  ['Keep the latest version', 'A change creates a new version. Earlier released summaries remain available.'],
  ['Share during care', 'The receiving doctor records their care relationship and reason for access before opening the summary.'],
  ['Keep a copy', 'Patients and families can listen to the reviewed plan and print a wallet card.'],
];

export function LandingPage(props: {
  onSignIn: () => void;
  onSignUp: () => void;
  onExploreDemo: () => void;
  onFamilyAccess: () => void;
  onPatientAccess?: () => void;
  onQuickEnterRole?: (role: DemoRole) => void;
  onOpenOpsHub?: (tab: 'map' | 'qr' | 'context' | 'mro') => void;
}): React.JSX.Element {
  const { onSignIn, onSignUp, onExploreDemo, onFamilyAccess, onPatientAccess, onQuickEnterRole, onOpenOpsHub } = props;
  const handleQuickRole = (role: DemoRole) => {
    if (onQuickEnterRole) onQuickEnterRole(role);
    else if (role === 'patient') (onPatientAccess ?? onFamilyAccess)();
    else if (role === 'family') onFamilyAccess();
    else onExploreDemo();
  };

  return (
    <div className="lp-page">
      <header className="lp-nav">
        <div className="lp-brand">
          <span className="lp-brand-mark" aria-hidden="true">S</span>
          <strong>Saathi</strong>
        </div>
        <nav className="lp-nav-actions" aria-label="Account">
          <button type="button" className="lp-nav-btn" onClick={onSignIn}>Sign in</button>
          <button type="button" className="lp-nav-btn" onClick={onSignUp}>Create account</button>
        </nav>
      </header>

      <main className="lp-main">
        <section className="lp-hero" aria-labelledby="lp-hero-title">
          <div className="lp-hero-copy">
            <p className="lp-kicker">Cancer care, with patients and families</p>
            <h1 id="lp-hero-title">Support for families<br /><span>through cancer care.</span></h1>
            <p className="lp-hero-desc">Coordinate family tasks and visits, prepare for goals-of-care discussions, and keep your doctor-reviewed summary close.</p>
            <div className="lp-hero-actions">
              <button type="button" className="lp-demo" onClick={() => handleQuickRole('family')}>
                Open family care <IconArrowRight className="w-4 h-4" />
              </button>
              <button type="button" className="lp-account-action" onClick={() => handleQuickRole('dr-sujay')}>
                Start a conversation
              </button>
            </div>
            <p className="lp-demo-note">Hospital sign-in is not connected.</p>
          </div>
          <figure className="lp-hero-media">
            <Image src="/care-conversation.png" alt="Illustration of a doctor listening to a patient and family." fill priority sizes="(max-width: 700px) 100vw, 50vw" className="lp-hero-img" />
          </figure>
        </section>

        <div className="lp-care-links">
          <Link href="/care"><span>Find best supportive care</span><span aria-hidden="true">↗</span></Link>
          <button type="button" onClick={onPatientAccess ?? onFamilyAccess}><span>Patient sign in</span><span aria-hidden="true">→</span></button>
          <button type="button" onClick={onFamilyAccess}><span>Family sign in</span><span aria-hidden="true">→</span></button>
        </div>

        <div className="lp-details">
          <details>
            <summary>Explore the care roles</summary>
            <div className="lp-doors-grid">
              {ROLES.map((role) => (
                <article className="lp-door-card" key={role.roleKey}>
                  <h2>{role.title}</h2><p>{role.body}</p>
                  <button type="button" onClick={() => handleQuickRole(role.roleKey)}>Open {role.title.toLowerCase()} view <span aria-hidden="true">→</span></button>
                </article>
              ))}
            </div>
          </details>
          <details>
            <summary>From conversation to shared summary</summary>
            <div className="lp-pillars-grid">
              {[
                { image: '/care-conversation.png', title: 'Talk about what matters', text: 'Understanding, worries and care preferences are discussed with the doctor.' },
                { image: '/clinician-handoff.png', title: 'Review the written summary', text: 'The doctor checks the words before releasing a version for the patient and family.' },
                { image: '/home-followup.png', title: 'Revisit as things change', text: 'Bring new questions to the care team and keep the latest reviewed copy.' },
              ].map((item) => (
                <article className="lp-pillar-card" key={item.title}>
                  <div className="lp-pillar-image-wrap"><Image src={item.image} alt="" fill sizes="(max-width: 700px) 100vw, 33vw" className="lp-hero-img" /></div>
                  <h2>{item.title}</h2><p>{item.text}</p>
                </article>
              ))}
            </div>
            <ol className="lp-info-list">
              {STEPS.map(([title, body]) => <li key={title}><strong>{title}</strong><p>{body}</p></li>)}
            </ol>
          </details>
          <details>
            <summary>Handoff and coordination</summary>
            <p className="lp-demo-note">These controls do not contact a health worker or ambulance.</p>
            <div className="lp-os-grid">
              <button type="button" onClick={() => onOpenOpsHub?.('map')}><strong>Coordination map</strong><span>View the saved Bengaluru care routes.</span></button>
              <button type="button" onClick={() => onOpenOpsHub?.('qr')}><strong>QR handoff</strong><span>Try a clinic check-in or emergency record lookup.</span></button>
              <button type="button" onClick={() => onOpenOpsHub?.('context')}><strong>Check the handoff</strong><span>Review the role, patient and access information.</span></button>
              <button type="button" onClick={() => onOpenOpsHub?.('mro')}><strong>Portable care record</strong><span>Inspect a copy and its integrity check.</span></button>
            </div>
          </details>
          <details>
            <summary>What you can do</summary>
            <ul className="lp-info-list">
              <li>A guided goals-of-care conversation, review, version history and retrieval.</li>
              <li>A family checklist for practical tasks, upcoming visits and questions to bring to the care team.</li>
              <li>No live transcription, AI model, hospital system, messaging channel or ABDM connection is active.</li>
              <li>A conversation summary does not create a DNAR order or an advance medical directive. Clinical decisions remain with the treating team.</li>
              <li>Missing preferences stay unrecorded. A family note does not change a doctor-reviewed summary.</li>
              <li>The clinical tools have not been clinically validated.</li>
            </ul>
          </details>
        </div>
      </main>
      <footer className="lp-footer"><p>Saathi · Cancer care, with patients and families.</p></footer>
    </div>
  );
}
