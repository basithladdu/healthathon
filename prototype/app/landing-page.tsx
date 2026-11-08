'use client';

import React from 'react';
import Image from 'next/image';
import Link from 'next/link';
import QRCode from 'qrcode';
import { IconArrowRight } from './icons';
import { SaharaLogo } from './sahara-logo';

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

const PLAY_URL = 'https://play.google.com/store/apps/details?id=in.wedevit.sahara';
const PROMPT_KEY = 'sahara.appPrompt.dismissed';
const PROMPT_DELAY_MS = 0;

export function PlayLink({ className = '' }: { className?: string }): React.JSX.Element {
  return (
    <a className={`lp-play-badge ${className}`.trim()} href={PLAY_URL} target="_blank" rel="noopener" aria-label="Get it on Google Play">
      <svg className="lp-play-icon" width="28" height="30" viewBox="0 0 24 26" aria-hidden="true" focusable="false">
        <path d="M2 1.2v23.6L13.2 13z" fill="#00d3ff" />
        <path d="M13.2 13 17 9.2 3.6.6C3 .3 2.4.5 2 1.2z" fill="#00f076" />
        <path d="M13.2 13 2 24.8c.4.7 1 .9 1.6.6L17 16.8z" fill="#ff3a44" />
        <path d="M17 9.2 13.2 13 17 16.8l4.4-2.5c1.2-.7 1.2-2 0-2.6z" fill="#ffd400" />
      </svg>
      <span className="lp-play-text" aria-hidden="true"><small>GET IT ON</small><strong>Google Play</strong></span>
    </a>
  );
}

export function AppDownloadDialog(): React.JSX.Element | null {
  const [open, setOpen] = React.useState(false);
  const [qr, setQr] = React.useState('');
  const dialogRef = React.useRef<HTMLDivElement>(null);
  const returnFocusRef = React.useRef<HTMLElement | null>(null);

  React.useEffect(() => {
    try {
      if (new URLSearchParams(window.location.search).get('app') === '1') return;
      if (/;\s*wv\)|Sahara-Android/i.test(navigator.userAgent)) return;
    } catch { /* ignore */ }
    try {
      if (window.localStorage.getItem(PROMPT_KEY)) return;
    } catch { /* storage unavailable: still show once per page view */ }
    const timer = setTimeout(() => {
      returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      setOpen(true);
    }, PROMPT_DELAY_MS);
    return () => clearTimeout(timer);
  }, []);

  const close = React.useCallback(() => {
    try { window.localStorage.setItem(PROMPT_KEY, String(Date.now())); } catch { /* ignore */ }
    setOpen(false);
    returnFocusRef.current?.focus?.();
  }, []);

  React.useEffect(() => {
    if (!open) return;
    let cancelled = false;
    if (window.matchMedia?.('(min-width: 721px)').matches) {
      QRCode.toDataURL(PLAY_URL, { errorCorrectionLevel: 'M', margin: 1, width: 160 })
        .then((url) => { if (!cancelled) setQr(url); })
        .catch(() => { /* QR is optional */ });
    }
    const node = dialogRef.current;
    const focusables = () => Array.from(node?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])') ?? []);
    focusables()[0]?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); close(); return; }
      if (event.key !== 'Tab') return;
      const items = focusables();
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      else if (!node?.contains(document.activeElement)) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { cancelled = true; document.removeEventListener('keydown', onKey); };
  }, [open, close]);

  if (!open) return null;
  return (
    <div className="lp-app-overlay" onMouseDown={(event) => { if (event.target === event.currentTarget) close(); }}>
      <div className="lp-app-dialog" role="dialog" aria-modal="true" aria-labelledby="lp-app-title" aria-describedby="lp-app-desc" ref={dialogRef}>
        <button type="button" className="lp-app-close" onClick={close} aria-label="Close">
          <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M5 5l14 14M19 5L5 19" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" fill="none" /></svg>
        </button>
        <SaharaLogo className="lp-app-logo" />
        <h2 id="lp-app-title">Sahara</h2>
        <p id="lp-app-desc">Sahara works best as the Android app: symptoms, medicines, goals-of-care recording and the emergency card.</p>
        <div className="lp-app-actions">
          <PlayLink />
          <button type="button" className="lp-account-action" onClick={close}>Continue on the web</button>
        </div>
        {qr ? (
          <div className="lp-app-qr">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={qr} width={120} height={120} alt="QR code for the Sahara app on Google Play" />
            <span>Scan with your phone</span>
          </div>
        ) : null}
      </div>
    </div>
  );
}

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
          <SaharaLogo className="lp-brand-mark" priority />
          <span className="lp-brand-copy"><strong>Sahara</strong><small>- A Palliative Care Companion</small></span>
        </div>
        <nav className="lp-nav-actions" aria-label="Account">
          <a className="lp-nav-btn lp-nav-app" href={PLAY_URL} target="_blank" rel="noopener">Get the app</a>
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
            <div className="lp-hero-app"><PlayLink /></div>
            <p className="lp-demo-note">Hospital sign-in is not connected.</p>
          </div>
          <figure className="lp-hero-media">
            <Image src="/care-conversation.png" alt="Illustration of a doctor listening to a patient and family." fill priority sizes="(max-width: 700px) 100vw, 50vw" className="lp-hero-img" />
          </figure>
        </section>

        <section className="lp-app-band" aria-labelledby="lp-app-band-title">
          <div>
            <h2 id="lp-app-band-title">Get the Android app</h2>
            <p>Symptoms, medicines, goals-of-care recording and the emergency card, all in one place on your phone.</p>
          </div>
          <PlayLink />
        </section>

        <div className="lp-care-links">
          <Link href="/care"><span>Find palliative care</span><span aria-hidden="true">↗</span></Link>
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
              <li>A conversation summary does not create a DNAR order or advance care planning. Clinical decisions remain with the treating team.</li>
              <li>Missing preferences stay unrecorded. A family note does not change a doctor-reviewed summary.</li>
              <li>The clinical tools have not been clinically validated.</li>
            </ul>
          </details>
        </div>
      </main>
      <footer className="lp-footer"><p>Sahara - A Palliative Care Companion</p></footer>
    </div>
  );
}
