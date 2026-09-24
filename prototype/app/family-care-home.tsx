'use client';

import { useEffect, useId, useState } from 'react';
import QRCode from 'qrcode';
import { IconArrowRight } from './icons';
import { CareRouteLink } from './care-route-link';
import { CareArt, type CareArtKind } from './care-art';
import { CARE_ROUTES, type CareView } from './care-routes';
import { CARE_SECTIONS } from './care-section-pages';
import { CareToday } from './care-today';
import type { CareCheck, CareEvent } from './care-calendar-state';
import type { Appointment } from './appointments-page';
import type { FamilyTask } from './family-care-state';
import './family-care-home.css';

export type FamilyCareHomeProps = {
  patientName: string;
  hindi: boolean;
  signedCareNote?: { version: number; releasedAt: string; physician: string };
  nextVisit?: { date: string; time: string; clinician: string };
  patientId: string;
  today: string;
  author: string;
  role: 'patient' | 'family';
  events: CareEvent[];
  appointments: Appointment[];
  checks: CareCheck[];
  onMedicineTaken: (eventId: string, date: string, complete: boolean) => void;
  tasks?: FamilyTask[];
  viewerId?: string;
};

export function FamilyCareHome({ patientName, hindi, signedCareNote, patientId, today, author, role, events, appointments, checks, onMedicineTaken, tasks, viewerId }: FamilyCareHomeProps) {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const id = useId();
  const [qr, setQr] = useState<{ patientId: string; version: number; dataUrl: string }>();
  const signedVersion = signedCareNote?.version;
  const qrDataUrl = qr?.patientId === patientId && qr.version === signedVersion ? qr.dataUrl : '';
  const name = patientName.trim().split(/\s+/)[0];
  const quickAccess: Array<{ view: CareView; en: string; hi: string; art: CareArtKind }> = [
    { view: 'doctor-pack', en: 'Visit pack', hi: 'मुलाक़ात की फ़ाइल', art: 'doctor-pack' },
    { view: 'care-circle', en: 'My care circle', hi: 'मेरे अपने', art: 'home-help' },
    { view: 'family-tasks', en: 'Who can help?', hi: 'कौन मदद करेगा?', art: 'open-questions' },
    { view: 'medicines', en: 'My medicines', hi: 'मेरी दवाइयाँ', art: 'cancer-overview' },
  ];
  const categoryCards: Array<{ view: CareView; en: string; hi: string; art: CareArtKind; color: string }> = [
    ...CARE_SECTIONS.map(({ view, en, hi, art, color }) => ({ view, en, hi, art, color })),
    { view: 'calendar', en: 'Calendar', hi: 'कैलेंडर', art: 'lab-history', color: 'peach' },
  ];

  useEffect(() => {
    let cancelled = false;
    if (signedVersion === undefined) return;
    const targetUrl = new URL(CARE_ROUTES['my-plan'], window.location.origin);
    targetUrl.searchParams.set('patientId', patientId);
    targetUrl.searchParams.set('version', String(signedVersion));
    const target = targetUrl.toString();
    QRCode.toDataURL(target, { errorCorrectionLevel: 'M', margin: 1, width: 240 })
      .then((dataUrl) => { if (!cancelled) setQr({ patientId, version: signedVersion, dataUrl }); })
      .catch(() => { if (!cancelled) setQr(undefined); });
    return () => { cancelled = true; };
  }, [patientId, signedVersion]);

  return <section className="family-care-home care-home-organised">
    <header className="family-care-home-heading"><h1>{t(`${name}’s care`, `${name} की देखभाल`)}</h1><CareRouteLink view="my-details" className="care-home-profile">{t('My profile', 'मेरी जानकारी')}</CareRouteLink></header>
    <section className={`care-home-note-feature${signedCareNote ? '' : ' is-empty'}`} aria-labelledby={`${id}-note-title`}>
      {signedCareNote ? <>
        <div className="care-home-note-copy">
          <span className="care-home-note-status">{t('Latest signed version', 'नवीनतम हस्ताक्षरित संस्करण')} · V{signedCareNote.version}</span>
          <h2 id={`${id}-note-title`}>{t('Care Note', 'देखभाल का नोट')}</h2>
          <p>{t(`Signed by ${signedCareNote.physician} · ${new Date(signedCareNote.releasedAt).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}`, `${signedCareNote.physician} द्वारा हस्ताक्षरित · ${new Date(signedCareNote.releasedAt).toLocaleDateString('hi-IN', { day: 'numeric', month: 'short', year: 'numeric' })}`)}</p>
          <CareRouteLink view="my-plan" className="care-home-note-open">{t('Open Care Note', 'देखभाल का नोट खोलें')} <IconArrowRight /></CareRouteLink>
        </div>
        <figure className="care-home-note-qr">
          {qrDataUrl ? <img src={qrDataUrl} width="168" height="168" alt={t('QR code to open Care Note in this app', 'इस ऐप में देखभाल का नोट खोलने का QR कोड')} /> : <span className="care-home-note-qr-pending" aria-live="polite">{t('Preparing QR code…', 'QR कोड तैयार हो रहा है…')}</span>}
          <figcaption>{t(`Opens V${signedCareNote.version} in this browser. Download the PDF to share it.`, `इस ब्राउज़र में V${signedCareNote.version} खोलता है। साझा करने के लिए PDF डाउनलोड करें।`)}</figcaption>
        </figure>
      </> : <div className="care-home-note-empty">
        <span className="care-home-note-status">{t('Doctor review required', 'डॉक्टर की समीक्षा ज़रूरी है')}</span>
        <h2 id={`${id}-note-title`}>{t('No signed Care Note yet', 'अभी हस्ताक्षरित देखभाल का नोट नहीं है')}</h2>
        <CareRouteLink view="my-plan" className="care-home-note-open">{t('Care Note history', 'देखभाल के पुराने नोट')} <IconArrowRight /></CareRouteLink>
      </div>}
    </section>
    <nav className="care-home-category-nav" aria-label={t('Care sections', 'देखभाल के विभाग')}>
      {categoryCards.map((section) => <CareRouteLink key={section.view} view={section.view} className={`care-home-category tone-${section.color}`}>
        <CareArt kind={section.art} /><strong>{hindi ? section.hi : section.en}</strong><IconArrowRight />
      </CareRouteLink>)}
    </nav>
    <CareToday patientId={patientId} today={today} author={author} role={role} events={events} appointments={appointments} checks={checks} onMedicineTaken={onMedicineTaken} tasks={tasks} viewerId={viewerId} hindi={hindi} />
    <section className="care-home-quick-section" aria-labelledby={`${id}-quick-title`}>
      <h2 id={`${id}-quick-title`}>{t('Quick access', 'जल्दी खोलें')}</h2>
      <div className="care-home-quick-grid">
        {quickAccess.map((item) => <CareRouteLink key={item.view} view={item.view} className={`care-home-quick-card quick-${item.view}`}>
          <CareArt kind={item.art} /><strong>{hindi ? item.hi : item.en}</strong><IconArrowRight />
        </CareRouteLink>)}
      </div>
    </section>
  </section>;
}
