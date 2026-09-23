'use client';

import { useMemo, useState } from 'react';
import { eventFallsOn, type CareCheck, type CareEvent, type CareEventKind } from './care-calendar-state';
import type { Appointment } from './appointments-page';
import type { FamilyTask } from './family-care-state';
import { CareRouteLink } from './care-route-link';
import { IconArrowRight } from './icons';
import './care-today.css';

type TodayItem = {
  id: string;
  date: string;
  time: string;
  label: string;
  kind: 'medicine' | 'appointment' | 'care-event';
  eventKind?: CareEventKind;
  checked: boolean;
};

export type CareTodayProps = {
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
  hindi?: boolean;
};

export function CareToday({ patientId, today, author, role, events, appointments, checks, onMedicineTaken, tasks = [], viewerId, hindi = false }: CareTodayProps) {
  const [collapsed, setCollapsed] = useState(false);
  const t = (en: string, hi: string) => hindi ? hi : en;
  const items = useMemo(() => {
    const atDate = (date: string): TodayItem[] => {
      const careEvents = events.filter((event) => event.patientId === patientId && eventFallsOn(event, date)).map((event): TodayItem => ({
        id: event.id,
        date,
        time: event.time || '23:59',
        label: event.title,
        kind: event.kind === 'Medicine' ? 'medicine' : 'care-event',
        eventKind: event.kind,
        checked: event.kind === 'Medicine' && checks.some((check) => check.patientId === patientId && check.eventId === event.id && check.date === date),
      }));
      const visits = appointments.filter((appointment) => appointment.hospitalId === patientId && appointment.date === date && appointment.status === 'Scheduled'
        && !careEvents.some((event) => event.eventKind === 'Appointment' && event.time === appointment.time
          && [appointment.type, appointment.clinician].some((name) => name.trim().toLowerCase() === event.label.trim().toLowerCase())))
        .map((appointment): TodayItem => ({
        id: appointment.id,
        date,
        time: appointment.time || '23:59',
        label: appointment.type || appointment.clinician,
        kind: 'appointment',
        checked: false,
      }));
      return [...careEvents, ...visits].sort((a, b) => a.time.localeCompare(b.time) || a.label.localeCompare(b.label));
    };

    const todaysItems = atDate(today);
    if (todaysItems.length) return { date: today, items: todaysItems, isToday: true };
    for (let offset = 1; offset <= 180; offset += 1) {
      const date = new Date(`${today}T12:00:00Z`);
      date.setUTCDate(date.getUTCDate() + offset);
      const nextDate = date.toISOString().slice(0, 10);
      const nextItems = atDate(nextDate);
      if (nextItems.length) return { date: nextDate, items: nextItems, isToday: false };
    }
    return { date: today, items: [], isToday: true };
  }, [appointments, checks, events, patientId, today]);

  const dateLabel = items.isToday
    ? t('Today', 'आज')
    : new Date(`${items.date}T12:00:00Z`).toLocaleDateString(hindi ? 'hi-IN' : 'en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
  const count = items.items.length;
  const assignedTasks = tasks.filter((task) => task.patientId === patientId && !task.completedBy
    && ((Boolean(viewerId) && task.assignedToId === viewerId) || task.owner === author));
  const incomingTasks = assignedTasks.slice(0, 2);
  const moreIncomingTasks = assignedTasks.length > incomingTasks.length;

  return <section className={`care-today${collapsed ? ' is-collapsed' : ''}`} data-role={role} aria-labelledby="care-today-title">
    <header className="care-today-heading">
      <div className="care-today-title"><span className="care-today-dot" aria-hidden="true" /><h2 id="care-today-title">{dateLabel}</h2>{count + incomingTasks.length > 0 && <span className="care-today-count">{count + incomingTasks.length}</span>}</div>
      <div className="care-today-actions">
        <a className="care-today-upload" href="/reports?upload=1">{t('Upload report or prescription', 'रिपोर्ट या प्रिस्क्रिप्शन जोड़ें')}</a>
        <button type="button" className="care-today-collapse" aria-expanded={!collapsed} aria-controls="care-today-items" onClick={() => setCollapsed((value) => !value)}>{collapsed ? t('Show', 'दिखाएँ') : t('Hide', 'छिपाएँ')}</button>
      </div>
    </header>
    {!collapsed && <div className="care-today-items" id="care-today-items">
      {items.items.slice(0, 3).map((item) => <article className={`care-today-item is-${item.kind}`} key={`${item.kind}-${item.id}`}>
        <time dateTime={`${item.date}T${item.time}`}>{item.time === '23:59' ? t('Time not set', 'समय तय नहीं') : item.time}</time>
        <span className={`care-today-kind kind-${item.kind}`}>{item.kind === 'medicine' ? t('Medicine', 'दवा') : item.kind === 'appointment' ? t('Appointment', 'मुलाकात') : t(item.eventKind ?? 'Care', item.eventKind ?? 'देखभाल')}</span>
        <strong>{item.label}</strong>
        {item.kind === 'medicine' ? <>
          <button type="button" className={`care-today-check${item.checked ? ' is-checked' : ''}`} disabled={!items.isToday} aria-label={item.checked ? `${author}: undo taken for ${item.label}` : `${author}: record ${item.label} as taken`} onClick={() => onMedicineTaken(item.id, items.date, !item.checked)}>{item.checked ? t('Undo', 'वापस लें') : t('Record taken', 'दवा ली दर्ज करें')}</button>
          <CareRouteLink view="medicines" className="care-today-open">{t('Open', 'खोलें')} <IconArrowRight /></CareRouteLink>
        </> : <CareRouteLink view="calendar" className="care-today-open">{t('Open', 'खोलें')} <IconArrowRight /></CareRouteLink>}
      </article>)}
      {count > 3 && <CareRouteLink view="calendar" className="care-today-all-tasks">{t(`View all ${count} items`, `सभी ${count} चीज़ें देखें`)} <IconArrowRight /></CareRouteLink>}
      {!count && <p className="care-today-empty">{t('Nothing scheduled today.', 'आज कुछ तय नहीं है।')}</p>}
      {!items.isToday && count > 0 && <p className="care-today-upcoming">{t('Next scheduled items', 'आगे तय चीज़ें')}</p>}
      {incomingTasks.map((task) => <article className="care-today-item care-today-assigned-task" key={`task-${task.id}`}>
        <span className="care-today-kind kind-task">{t('For you', 'आपके लिए')}</span>
        <strong>{t(`From ${task.createdBy}: ${task.title}`, `${task.createdBy} की ओर से: ${task.title}`)}</strong>
        <CareRouteLink view="family-tasks" className="care-today-open">{t('Open task', 'काम खोलें')} <IconArrowRight /></CareRouteLink>
      </article>)}
      {moreIncomingTasks && <CareRouteLink view="family-tasks" className="care-today-all-tasks">{t('See all assigned tasks', 'सभी दिए गए काम देखें')} <IconArrowRight /></CareRouteLink>}
    </div>}
  </section>;
}
