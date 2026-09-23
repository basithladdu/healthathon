'use client';

import { IconArrowRight } from './icons';
import { CareRouteLink } from './care-route-link';
import { CareArt, type CareArtKind } from './care-art';
import type { CareView } from './care-routes';
import { CARE_SECTIONS } from './care-section-pages';
import './family-care-home.css';

export type FamilyCareHomeProps = {
  patientName: string;
  hindi: boolean;
  nextVisit?: { date: string; time: string; clinician: string };
};

export function FamilyCareHome({ patientName, hindi, nextVisit }: FamilyCareHomeProps) {
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
    { view: 'medicine-access', en: 'Find oral morphine access', hi: 'मॉर्फ़ीन पाने के बारे में पूछें', art: 'support-places' },
    { view: 'calendar', en: 'Treatment calendar', hi: 'इलाज का कैलेंडर', art: 'lab-history' },
    ...(nextVisit ? [{ view: 'next-visit' as const, en: 'Get ready for the next visit', hi: 'अगली मुलाकात की तैयारी', art: 'doctor-pack' as const }] : []),
    { view: 'request-conversation', en: 'Talk about what matters', hi: 'मेरे लिए ज़रूरी बात करें', art: 'voice-journal' },
  ];

  return <section className="family-care-home care-home-organised">
    <header className="family-care-home-heading"><h1>{t(`${name}’s care`, `${name} की देखभाल`)}</h1><CareRouteLink view="my-details" className="care-home-profile">{t('My profile', 'मेरी जानकारी')}</CareRouteLink></header>
    <nav className="care-home-category-nav" aria-label={t('Care sections', 'देखभाल के विभाग')}>
      {CARE_SECTIONS.map((section) => <CareRouteLink key={section.view} view={section.view} className={`care-home-category tone-${section.color}`}>
        <CareArt kind={section.art} /><strong>{hindi ? section.hi : section.en}</strong><IconArrowRight />
      </CareRouteLink>)}
    </nav>
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
