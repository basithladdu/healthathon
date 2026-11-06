'use client';

import { useEffect, useState } from 'react';
import type { CareCheck, CareEvent } from './care-calendar-state';
import { MedicinePhoto, StoredMedicinePhoto } from './care-medicine-photo';
import { loadMedicineRegimens, patientMedicineRegimens, type MedicineRegimen } from './care-medicine-regimen-state';
import { formatMedicineTime, nextMedicineTimeGroup } from './care-medicine-schedule';
import { medicinePrescription } from './care-medicines-state';
import { CareRouteLink } from './care-route-link';
import { SaharaLottie } from './sahara-lottie';
import { IconArrowRight } from './icons';
import './care-today.css';
import { careLocale, careText, type CareLang } from './care-i18n';

export type CareTodayProps = {
  patientId: string;
  today: string;
  currentTime: string;
  author: string;
  role: 'patient' | 'family';
  events: CareEvent[];
  checks: CareCheck[];
  onMedicineTaken: (eventId: string, date: string, complete: boolean) => void;
  lang?: CareLang;
};

function localClock(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

export function CareToday({ patientId, today, currentTime, author, role, events, checks, onMedicineTaken, lang = 'en' }: CareTodayProps) {
  const [clock, setClock] = useState(currentTime);
  const [regimens, setRegimens] = useState<MedicineRegimen[]>([]);
  const t = (en: string, hi: string) => careText(lang, en, hi);
  useEffect(() => {
    const timer = window.setInterval(() => setClock(localClock()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- versioned browser medicine metadata hydrates after mount.
    setRegimens(patientMedicineRegimens(loadMedicineRegimens(), patientId));
  }, [patientId]);

  const next = nextMedicineTimeGroup(events, checks, patientId, today, clock);
  const regimenById = new Map(regimens.map((item) => [item.id, item]));
  const dateLabel = next?.date === today
    ? t('Today', 'आज')
    : next ? new Date(`${next.date}T12:00:00Z`).toLocaleDateString(careLocale(lang, 'en-IN'), { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }) : '';

  const takeAt = next ? formatMedicineTime(next.time) : '';
  return <section className="care-today" data-role={role} aria-labelledby="care-today-title">
    <header className="care-today-heading">
      <h2 id="care-today-title">{t('Next medicines', 'अगली दवाएँ')}</h2>
      {next && <span className="care-today-count" aria-label={t(`${next.medicines.length} medicines`, `${next.medicines.length} दवाएँ`)}>{next.medicines.length}</span>}
      <CareRouteLink view="medicines" className="care-today-all-tasks">{t('See all medicines', 'सभी दवाएँ देखें')} <IconArrowRight /></CareRouteLink>
    </header>
    {next ? <div className="care-next-dose">
      <div className="care-next-dose-time"><span className="care-next-dose-when">{dateLabel}</span><time dateTime={`${next.date}T${next.time}`}>{takeAt}</time></div>
      <div className="care-next-dose-list" role="list" aria-label={t(`Medicines to take at ${takeAt}`, `${takeAt} पर लेने वाली दवाएँ`)}>
        {next.medicines.map((medicine) => {
          const prescription = medicinePrescription(medicine);
          const linkedRegimen = prescription?.regimenId ? regimenById.get(prescription.regimenId) : undefined;
          const imageId = linkedRegimen?.medicineImageId ?? prescription?.medicineImageId;
          return <article className="care-next-dose-medicine" role="listitem" key={medicine.id}>
            {imageId ? <StoredMedicinePhoto imageId={imageId} alt={medicine.title} lang={lang} />
              : prescription?.medicineImage ? <MedicinePhoto file={prescription.medicineImage} alt={medicine.title} lang={lang} /> : <span className="care-next-dose-pill" aria-hidden="true"><PillIcon /></span>}
            <div className="care-next-dose-copy"><strong>{medicine.title}</strong>{medicine.instructions && <p>{medicine.instructions}</p>}</div>
            {next.date === today && <button type="button" className="care-today-check" aria-label={`${author}: ${t(`record ${medicine.title} as taken`, `${medicine.title} ली हुई दर्ज करें`)}`} onClick={() => onMedicineTaken(medicine.id, next.date, true)}><CheckIcon />{t('Taken', 'ले ली')}</button>}
          </article>;
        })}
      </div>
    </div> : <div className="care-today-empty"><span className="care-next-dose-pill" aria-hidden="true"><PillIcon /></span><p>{t('No upcoming medicines are scheduled.', 'आगे कोई दवा तय नहीं है।')}</p></div>}
  </section>;
}

function PillIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m10.5 20.5 10-10a4.95 4.95 0 1 0-7-7l-10 10a4.95 4.95 0 1 0 7 7Z" /><path d="m8.5 8.5 7 7" /></svg>;
}

function CheckIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M5 12.5 10 17l9-10" /></svg>;
}

/** Greeting by the device hour, like Android Today ("Good morning, Meera"). Family sees the greeting alone. */
export function careGreeting(hour: number, lang: CareLang = 'en'): string {
  if (hour < 12) return careText(lang, 'Good morning', 'सुप्रभात');
  if (hour < 17) return careText(lang, 'Good afternoon', 'शुभ दोपहर');
  return careText(lang, 'Good evening', 'शुभ संध्या');
}

export type CareTodayGreetingProps = { name?: string; role: 'patient' | 'family'; today: string; lang?: CareLang };

/**
 * Optional Today header matching Android: greeting + date, then SOS and the QR (emergency) card one tap away.
 * Not yet placed on the page; family-care-home.tsx can render it above the Care Note card.
 */
export function CareTodayGreeting({ name = '', role, today, lang = 'en' }: CareTodayGreetingProps) {
  const t = (en: string, hi: string) => careText(lang, en, hi);
  const [hour, setHour] = useState(9);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- the device clock is read after mount to avoid a hydration mismatch.
    setHour(new Date().getHours());
  }, []);
  const first = name.trim().split(/\s+/)[0] ?? '';
  const greeting = careGreeting(hour, lang);
  const line = role === 'patient' && first ? `${greeting}, ${first}` : greeting;
  const date = new Date(`${today}T12:00:00Z`).toLocaleDateString(careLocale(lang, 'en-IN'), { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' });
  return <div className="care-today-greeting">
    <div className="care-today-greeting-copy"><p className="care-today-hello">{line} <SaharaLottie name="wave" className="sh-lottie-inline" /></p><p className="care-today-date">{date}</p>{role === 'family' && first && <p className="care-today-caring">{t(`Caring for ${first}`, `${first} की देखभाल`)}</p>}</div>
    <div className="care-today-sos-bar">
      <CareRouteLink view="emergency-card" className="care-today-sos">{t('SOS', 'SOS')}</CareRouteLink>
      <CareRouteLink view="emergency-card" className="care-today-qr"><QrIcon />{t('QR card', 'QR कार्ड')}</CareRouteLink>
    </div>
  </div>;
}

function QrIcon() {
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><path d="M14 14h3v3h-3zM20 14v.01M14 20h.01M17 20h4v-3" /></svg>;
}
