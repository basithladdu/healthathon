'use client';

import { useEffect, useId, useState } from 'react';
import QRCode from 'qrcode';
import { IconArrowRight } from './icons';
import { CareRouteLink } from './care-route-link';
import { CareArt, type CareArtKind } from './care-art';
import { CARE_ROUTES, type CareView } from './care-routes';
import { CARE_SECTIONS } from './care-section-pages';
import { CareToday, CareTodayGreeting } from './care-today';
import type { CareCheck, CareEvent } from './care-calendar-state';
import type { Appointment } from './appointments-page';
import type { FamilyTask } from './family-care-state';
import { CaregiverWellbeingCard, FamilyDistressCard } from './caregiver-wellbeing';
import './family-care-home.css';
import { careLocale, careText, type CareLang } from './care-i18n';

export type FamilyCareHomeProps = {
  patientName: string;
  lang: CareLang;
  signedCareNote?: { version: number; releasedAt: string; physician: string };
  nextVisit?: { date: string; time: string; clinician: string };
  patientId: string;
  today: string;
  currentTime: string;
  author: string;
  role: 'patient' | 'family';
  events: CareEvent[];
  appointments: Appointment[];
  checks: CareCheck[];
  onMedicineTaken: (eventId: string, date: string, complete: boolean) => void;
  tasks?: FamilyTask[];
  viewerId?: string;
};

export function FamilyCareHome({ patientName, lang, signedCareNote, patientId, today, currentTime, author, role, events, checks, onMedicineTaken }: FamilyCareHomeProps) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const id = useId();
  const [qr, setQr] = useState<{ patientId: string; version: number; dataUrl: string }>();
  const signedVersion = signedCareNote?.version;
  const qrDataUrl = qr?.patientId === patientId && qr.version === signedVersion ? qr.dataUrl : '';
  const name = patientName.trim().split(/\s+/)[0];
  const isFamily = role === 'family';
  const quickAccess: Array<{ view: CareView; en: string; hi: string; art: CareArtKind }> = [
    { view: 'doctor-pack', en: 'Visit pack', hi: 'मुलाक़ात की फ़ाइल', art: 'doctor-pack' },
    isFamily ? { view: 'care-circle', en: 'Care circle', hi: 'देखभाल का घेरा', art: 'home-help' } : { view: 'care-circle', en: 'My care circle', hi: 'मेरे अपने', art: 'home-help' },
    { view: 'family-tasks', en: 'Who can help?', hi: 'कौन मदद करेगा?', art: 'open-questions' },
    isFamily ? { view: 'medicines', en: 'Medicines', hi: 'दवाइयाँ', art: 'cancer-overview' } : { view: 'medicines', en: 'My medicines', hi: 'मेरी दवाइयाँ', art: 'cancer-overview' },
  ];
  // Caregiver tips are practical, non-medical reminders for the family member. One per day.
  const caregiverTips: Array<{ en: string; hi: string }> = [
    { en: 'Share one task with the care circle today.', hi: 'आज एक काम देखभाल के घेरे में किसी को सौंपें।' },
    { en: 'Write questions down as they come up.', hi: 'सवाल जैसे ही आएँ, लिख लें।' },
    { en: 'Take a short break. Rest helps you care.', hi: 'थोड़ा आराम करें। आराम से देखभाल आसान होती है।' },
    { en: 'Keep the visit pack ready before each visit.', hi: 'हर मुलाक़ात से पहले फ़ाइल तैयार रखें।' },
  ];
  const tip = caregiverTips[Number(today.replace(/\D/g, '').slice(-2) || 0) % caregiverTips.length];
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
    <div className="care-home-top">
    <CareTodayGreeting name={patientName} role={role} today={today} lang={lang} />
    <header className="family-care-home-heading">
      {/* The greeting above is the visible title; the h1 stays for screen readers and page structure. */}
      <h1 className="family-care-home-sr-title">{isFamily ? t(`Caring for ${name}`, `${name} की देखभाल`) : t(`${name}’s care`, `${name} की देखभाल`)}</h1>
      {isFamily && <span className="family-care-home-eyebrow">{t('Family view', 'परिवार का दृश्य')}</span>}
      <CareRouteLink view="my-details" className="care-home-profile">{t('My profile', 'मेरी जानकारी')}</CareRouteLink>
    </header>
    </div>
    {isFamily && <p className="family-care-home-tip"><span aria-hidden="true">✦</span><span><strong>{t('Tip', 'सुझाव')}</strong> {careText(lang, tip.en, tip.hi)}</span></p>}
    <section className={`care-home-note-feature${signedCareNote ? '' : ' is-empty'}`} aria-labelledby={`${id}-note-title`}>
      {signedCareNote ? <>
        <div className="care-home-note-copy">
          <span className="care-home-note-status">{t('Latest signed version', 'नवीनतम हस्ताक्षरित संस्करण')} · V{signedCareNote.version}</span>
          <h2 id={`${id}-note-title`}>{t('Care Note', 'देखभाल का नोट')}</h2>
          <p>{t(`Signed by ${signedCareNote.physician} · ${new Date(signedCareNote.releasedAt).toLocaleDateString(careLocale(lang), { day: 'numeric', month: 'short', year: 'numeric' })}`, `${signedCareNote.physician} द्वारा हस्ताक्षरित · ${new Date(signedCareNote.releasedAt).toLocaleDateString('hi-IN', { day: 'numeric', month: 'short', year: 'numeric' })}`)}</p>
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
        <CareArt kind={section.art} /><strong>{careText(lang, section.en, section.hi)}</strong><IconArrowRight />
      </CareRouteLink>)}
    </nav>
    <CareToday patientId={patientId} today={today} currentTime={currentTime} author={author} role={role} events={events} checks={checks} onMedicineTaken={onMedicineTaken} lang={lang} />
    {isFamily && <div className="family-care-home-wellbeing">
      <FamilyDistressCard patientId={patientId} author={author} today={today} lang={lang} />
      <CaregiverWellbeingCard patientId={patientId} personName={patientName} author={author} today={today} lang={lang} />
    </div>}
    <section className="care-home-quick-section" aria-labelledby={`${id}-quick-title`}>
      <h2 id={`${id}-quick-title`}>{t('Quick access', 'जल्दी खोलें')}</h2>
      <div className="care-home-quick-grid">
        {quickAccess.map((item) => <CareRouteLink key={item.view} view={item.view} className={`care-home-quick-card quick-${item.view}`}>
          <CareArt kind={item.art} /><strong>{careText(lang, item.en, item.hi)}</strong><IconArrowRight />
        </CareRouteLink>)}
      </div>
    </section>
  </section>;
}
