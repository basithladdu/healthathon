'use client';

import { useEffect, useState } from 'react';
import type { CareCheck, CareEvent } from './care-calendar-state';
import { MedicinePhoto, StoredMedicinePhoto } from './care-medicine-photo';
import { loadMedicineRegimens, patientMedicineRegimens, type MedicineRegimen } from './care-medicine-regimen-state';
import { formatMedicineTime, nextMedicineTimeGroup } from './care-medicine-schedule';
import { medicinePrescription } from './care-medicines-state';
import { CareRouteLink } from './care-route-link';
import { IconArrowRight } from './icons';
import './care-today.css';

export type CareTodayProps = {
  patientId: string;
  today: string;
  currentTime: string;
  author: string;
  role: 'patient' | 'family';
  events: CareEvent[];
  checks: CareCheck[];
  onMedicineTaken: (eventId: string, date: string, complete: boolean) => void;
  hindi?: boolean;
};

function localClock(): string {
  const now = new Date();
  return `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
}

export function CareToday({ patientId, today, currentTime, author, role, events, checks, onMedicineTaken, hindi = false }: CareTodayProps) {
  const [clock, setClock] = useState(currentTime);
  const [regimens, setRegimens] = useState<MedicineRegimen[]>([]);
  const t = (en: string, hi: string) => hindi ? hi : en;
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
    : next ? new Date(`${next.date}T12:00:00Z`).toLocaleDateString(hindi ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }) : '';

  return <section className="care-today" data-role={role} aria-labelledby="care-today-title">
    <header className="care-today-heading">
      <div className="care-today-title"><span className="care-today-dot" aria-hidden="true" /><div><span>{t('UPCOMING DOSE', 'अगली खुराक')}</span><h2 id="care-today-title">{t('Next medicines', 'अगली दवाएँ')}</h2></div>{next && <span className="care-today-count">{next.medicines.length}</span>}</div>
      <CareRouteLink view="medicines" className="care-today-all-tasks">{t('See all medicines', 'सभी दवाएँ देखें')} <IconArrowRight /></CareRouteLink>
    </header>
    {next ? <div className="care-next-dose">
      <div className="care-next-dose-time"><time dateTime={`${next.date}T${next.time}`}>{formatMedicineTime(next.time)}</time><span>{dateLabel}</span></div>
      <div className="care-next-dose-list" role="list" aria-label={t(`Medicines to take at ${formatMedicineTime(next.time)}`, `${formatMedicineTime(next.time)} पर लेने वाली दवाएँ`)}>
        {next.medicines.map((medicine) => {
          const prescription = medicinePrescription(medicine);
          const linkedRegimen = prescription?.regimenId ? regimenById.get(prescription.regimenId) : undefined;
          const imageId = linkedRegimen?.medicineImageId ?? prescription?.medicineImageId;
          return <article className="care-next-dose-medicine" role="listitem" key={medicine.id}>
            {imageId ? <StoredMedicinePhoto imageId={imageId} alt={medicine.title} hindi={hindi} />
              : prescription?.medicineImage ? <MedicinePhoto file={prescription.medicineImage} alt={medicine.title} hindi={hindi} /> : null}
            <div className="care-next-dose-copy"><strong>{medicine.title}</strong><p>{medicine.instructions}</p></div>
            {next.date === today && <button type="button" className="care-today-check" aria-label={`${author}: ${t(`record ${medicine.title} as taken`, `${medicine.title} ली हुई दर्ज करें`)}`} onClick={() => onMedicineTaken(medicine.id, next.date, true)}>{t('Taken', 'ले ली')}</button>}
          </article>;
        })}
      </div>
    </div> : <p className="care-today-empty">{t('No upcoming medicines are scheduled.', 'आगे कोई दवा तय नहीं है।')}</p>}
  </section>;
}
