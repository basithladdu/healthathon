'use client';

import { IconArrowRight } from './icons';
import { CareRouteLink } from './care-route-link';
import { CareArt, type CareArtKind } from './care-art';
import type { CareView } from './care-routes';
import { CARE_SECTIONS } from './care-section-pages';
import { CareToday } from './care-today';
import type { CareCheck, CareEvent } from './care-calendar-state';
import type { Appointment } from './appointments-page';
import type { FamilyTask } from './family-care-state';
import './family-care-home.css';

export type FamilyCareHomeProps = {
  patientName: string;
  hindi: boolean;
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

export function FamilyCareHome({ patientName, hindi, nextVisit, patientId, today, author, role, events, appointments, checks, onMedicineTaken, tasks, viewerId }: FamilyCareHomeProps) {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const name = patientName.trim().split(/\s+/)[0];
  const date = nextVisit ? new Date(`${nextVisit.date}T12:00:00Z`) : null;
  const dateLabel = date && Number.isFinite(date.getTime()) ? date.toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' }) : nextVisit?.date;
  const quickAccess: Array<{ view: CareView; en: string; hi: string; art: CareArtKind }> = [
    { view: 'my-plan', en: 'Care note', hi: 'देखभाल का नोट', art: 'care-story' },
    { view: 'doctor-pack', en: 'Care summary', hi: 'देखभाल का सार', art: 'doctor-pack' },
    { view: 'care-circle', en: 'My care circle', hi: 'मेरे अपने', art: 'home-help' },
    { view: 'family-tasks', en: 'Who can help?', hi: 'कौन मदद करेगा?', art: 'open-questions' },
    { view: 'medicines', en: 'My medicines', hi: 'मेरी दवाइयाँ', art: 'cancer-overview' },
  ];
  const categoryCards: Array<{ view: CareView; en: string; hi: string; art: CareArtKind; color: string }> = [
    ...CARE_SECTIONS.map(({ view, en, hi, art, color }) => ({ view, en, hi, art, color })),
    { view: 'calendar', en: 'Calendar', hi: 'कैलेंडर', art: 'lab-history', color: 'peach' },
  ];

  return <section className="family-care-home care-home-organised">
    <header className="family-care-home-heading"><h1>{t(`${name}’s care`, `${name} की देखभाल`)}</h1><CareRouteLink view="my-details" className="care-home-profile">{t('My profile', 'मेरी जानकारी')}</CareRouteLink></header>
    <nav className="care-home-category-nav" aria-label={t('Care sections', 'देखभाल के विभाग')}>
      {categoryCards.map((section) => <CareRouteLink key={section.view} view={section.view} className={`care-home-category tone-${section.color}`}>
        <CareArt kind={section.art} /><strong>{hindi ? section.hi : section.en}</strong><IconArrowRight />
      </CareRouteLink>)}
    </nav>
    <CareToday patientId={patientId} today={today} author={author} role={role} events={events} appointments={appointments} checks={checks} onMedicineTaken={onMedicineTaken} tasks={tasks} viewerId={viewerId} hindi={hindi} />
    <section className="care-home-quick-section" aria-labelledby="care-home-quick-title">
      <h2 id="care-home-quick-title">{t('Quick access', 'जल्दी खोलें')}</h2>
      <div className="care-home-quick-grid">
        {quickAccess.map((item) => <CareRouteLink key={item.view} view={item.view} className={`care-home-quick-card quick-${item.view}`}>
          <CareArt kind={item.art} /><strong>{hindi ? item.hi : item.en}</strong><IconArrowRight />
          {item.view === 'next-visit' && nextVisit && <time dateTime={nextVisit.date}>{dateLabel} · {nextVisit.time} · {nextVisit.clinician}</time>}
        </CareRouteLink>)}
      </div>
    </section>
  </section>;
}
