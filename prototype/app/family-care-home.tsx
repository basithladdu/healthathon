'use client';

import { useId } from 'react';
import { FamilyToolButtons, type FamilyTool } from './family-care-tools';
import { IconArrowRight, IconCheckCircle, IconClock, IconFileText, IconSmile } from './icons';
import './family-care-home.css';
import { CareRouteLink } from './care-route-link';

export type FamilyCareHomeProps = {
  patientName: string;
  hindi: boolean;
  onOpen: (tool: FamilyTool) => void;
  onCalendar: () => void;
  onTasks: () => void;
  onComfort: () => void;
  onReports: () => void;
  nextVisit?: { date: string; time: string; clinician: string };
};

export function FamilyCareHome({ patientName, hindi, onOpen, onCalendar, onTasks, onComfort, onReports, nextVisit }: FamilyCareHomeProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const firstName = patientName.trim().split(/\s+/)[0];
  const visitDate = nextVisit ? new Date(`${nextVisit.date}T12:00:00Z`) : null;
  const visitDateLabel = visitDate && Number.isFinite(visitDate.getTime())
    ? visitDate.toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', timeZone: 'UTC' })
    : nextVisit?.date;

  return <section className="family-care-home" aria-labelledby={`${id}-heading`}>
    <header className="family-care-home-heading"><h1 id={`${id}-heading`}>{firstName ? t(`${firstName}’s care`, `${firstName} की देखभाल`) : t('Your care', 'आपकी देखभाल')}</h1><div className="care-home-visit-links"><CareRouteLink view="visit-questions">{t('Ask at the next visit', 'अगली मुलाकात में पूछें')}</CareRouteLink><CareRouteLink view="prepare-conversation">{t('What matters to me', 'मेरे लिए क्या ज़रूरी है')}</CareRouteLink></div></header>

    <FamilyToolButtons hindi={hindi} onOpen={onOpen} />

    <div className="family-care-home-shortcuts">
      <CareRouteLink view="calendar" className="care-home-shortcut care-home-shortcut-calendar" onOpen={onCalendar}>
        <span className="care-home-shortcut-icon" aria-hidden="true"><IconClock /></span>
        <span className="care-home-shortcut-copy"><strong>{t('Calendar', 'कैलेंडर')}</strong>
          {nextVisit && <span className="care-home-shortcut-visit"><time dateTime={nextVisit.date}>{t('Next', 'अगली मुलाकात')} · {visitDateLabel}{nextVisit.time ? ` · ${nextVisit.time}` : ''}</time>{nextVisit.clinician && <span>{nextVisit.clinician}</span>}</span>}
        </span>
        <span className="care-home-shortcut-arrow" aria-hidden="true"><IconArrowRight /></span>
      </CareRouteLink>

      <CareRouteLink view="reports" className="care-home-shortcut care-home-shortcut-reports" onOpen={onReports}>
        <span className="care-home-shortcut-icon" aria-hidden="true"><IconFileText /></span>
        <span className="care-home-shortcut-copy"><strong>{t('My reports', 'मेरी रिपोर्ट')}</strong></span>
        <span className="care-home-shortcut-arrow" aria-hidden="true"><IconArrowRight /></span>
      </CareRouteLink>

      <CareRouteLink view="family-tasks" className="care-home-shortcut care-home-shortcut-tasks" onOpen={onTasks}>
        <span className="care-home-shortcut-icon" aria-hidden="true"><IconCheckCircle /></span>
        <span className="care-home-shortcut-copy"><strong>{t('Family tasks', 'परिवार के काम')}</strong></span>
        <span className="care-home-shortcut-arrow" aria-hidden="true"><IconArrowRight /></span>
      </CareRouteLink>

      <CareRouteLink view="comfort" className="care-home-shortcut care-home-shortcut-comfort" onOpen={onComfort}>
        <span className="care-home-shortcut-icon" aria-hidden="true"><IconSmile /></span>
        <span className="care-home-shortcut-copy"><strong>{t('Comfort space', 'सुकून के पल')}</strong></span>
        <span className="care-home-shortcut-arrow" aria-hidden="true"><IconArrowRight /></span>
      </CareRouteLink>
    </div>
    <CareRouteLink className="care-home-resources" view="support-resources">{t('Family support & medicines', 'परिवार की मदद और दवाएँ')} <span aria-hidden="true"><IconArrowRight /></span></CareRouteLink>
  </section>;
}
