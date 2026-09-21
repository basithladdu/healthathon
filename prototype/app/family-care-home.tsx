'use client';

import { type FamilyTool } from './family-care-tools';
import { IconArrowRight } from './icons';
import { CareRouteLink } from './care-route-link';
import { CareArt } from './care-art';
import { CARE_SECTIONS } from './care-section-pages';
import './family-care-home.css';

export type FamilyCareHomeProps = {
  patientName: string; hindi: boolean; onOpen: (tool: FamilyTool) => void;
  onCalendar: () => void; onTasks: () => void; onComfort: () => void; onReports: () => void;
  nextVisit?: { date: string; time: string; clinician: string };
};

export function FamilyCareHome({ patientName, hindi, nextVisit }: FamilyCareHomeProps) {
  const t = (en: string, hi: string) => hindi ? hi : en;
  const name = patientName.trim().split(/\s+/)[0];
  const date = nextVisit ? new Date(`${nextVisit.date}T12:00:00Z`) : null;
  const dateLabel = date && Number.isFinite(date.getTime()) ? date.toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', {day:'numeric',month:'short',timeZone:'UTC'}) : nextVisit?.date;
  return <section className="family-care-home care-home-organised">
    <header className="family-care-home-heading"><h1>{t(`${name}’s care`, `${name} की देखभाल`)}</h1><CareRouteLink view="my-details" className="care-home-profile">{t('My profile', 'मेरी जानकारी')}</CareRouteLink></header>
    <div className="care-home-note-row">
      <CareRouteLink view="my-plan" className="care-home-latest-note"><CareArt kind="care-story" /><strong>{t('My care note', 'मेरा देखभाल नोट')}</strong><IconArrowRight /></CareRouteLink>
      <CareRouteLink view="doctor-pack" className="care-home-summary"><CareArt kind="doctor-pack" /><strong>{t('Care summary', 'देखभाल का सार')}</strong><IconArrowRight /></CareRouteLink>
    </div>
    <div className="care-home-groups">{CARE_SECTIONS.map((section) => <CareRouteLink key={section.view} view={section.view} className={`care-home-group tone-${section.color}`}>
      <CareArt kind={section.art} /><strong>{hindi ? section.hi : section.en}</strong><IconArrowRight />
    </CareRouteLink>)}</div>
    <CareRouteLink view="calendar" className="care-home-next-visit"><span><strong>{t('Treatment calendar', 'इलाज का कैलेंडर')}</strong>{nextVisit && <time dateTime={nextVisit.date}>{dateLabel} · {nextVisit.time} · {nextVisit.clinician}</time>}</span><IconArrowRight /></CareRouteLink>
  </section>;
}
