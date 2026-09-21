'use client';

import { useState } from 'react';
import type { Appointment } from './appointments-page';
import { latestSummaryRelease, type SummaryState } from './summary-state';
import { VisitInstructions } from './visit-instructions';
import { IconUsers, IconFileText, IconClock, IconCheckCircle, IconArrowRight } from './icons';

export function DoctorCareHome({ patients, records, appointments, physician, today, hindi, onReview, onRecord, onHistory, onInstructions }: {
  patients: Array<{ id: string; name: string; diagnosis: string }>; records: Record<string, SummaryState>;
  appointments: Appointment[]; physician: string; today: string; hindi: boolean;
  onReview: (patientId: string) => void;
  onRecord: (patientId?: string) => void; onHistory: (patientId?: string) => void;
  onInstructions: (patientId: string, appointmentId: string, value: Appointment['preparationInstructions']) => void;
}) {
  const [query, setQuery] = useState('');
  const [onlyPending, setOnlyPending] = useState(false);
  const [schedule, setSchedule] = useState<'today' | 'upcoming'>('upcoming');
  const t = (en: string, hi: string) => hindi ? hi : en;
  const patientIds = new Set(patients.map((patient) => patient.id));
  const pending = patients.filter((patient) => !records[patient.id]?.versionPublished);
  const approved = patients.filter((patient) => records[patient.id] && latestSummaryRelease(records[patient.id]));
  const upcoming = appointments.filter((visit) => patientIds.has(visit.hospitalId) && visit.status === 'Scheduled' && visit.date >= today).sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`));
  const visiblePatients = (onlyPending ? pending : patients).filter((patient) => `${patient.name} ${patient.id} ${patient.diagnosis}`.toLowerCase().includes(query.trim().toLowerCase()));
  const visibleVisits = schedule === 'today' ? upcoming.filter((visit) => visit.date === today) : upcoming;
  const dateLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short' });
  return <section className="doctor-care-home">
    <header className="doctor-care-heading"><div><h1>{t('Care team', 'देखभाल टीम')}</h1><span>{physician}</span></div><label className="doctor-patient-search"><span className="sr-only">{t('Find a patient', 'मरीज़ ढूँढें')}</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('Find a patient', 'मरीज़ ढूँढें')} /></label></header>
    <div className="doctor-home-actions"><button type="button" className="primary-button" onClick={() => onRecord()}><IconFileText />{t('Record conversation', 'बातचीत दर्ज करें')}</button><button type="button" className="secondary-button" onClick={() => onHistory()}><IconClock />{t('Note history', 'पुराने नोट')}</button></div>
    <div className="doctor-overview-cards">
      <button type="button" onClick={() => { setOnlyPending(false); document.getElementById('doctor-patient-queue')?.scrollIntoView({ block: 'nearest' }); }}><IconUsers /><strong>{patients.length}</strong><span>{t('People in care', 'देखभाल में लोग')}</span></button>
      <button type="button" onClick={() => { setOnlyPending(true); document.getElementById('doctor-patient-queue')?.scrollIntoView({ block: 'nearest' }); }}><IconFileText /><strong>{pending.length}</strong><span>{t('Notes to review', 'जाँचने के नोट')}</span></button>
      <button type="button" onClick={() => { setSchedule('upcoming'); document.getElementById('doctor-visit-list')?.scrollIntoView({ block: 'nearest' }); }}><IconClock /><strong>{upcoming.length}</strong><span>{t('Upcoming visits', 'आने वाली मुलाकातें')}</span></button>
      <button type="button" onClick={() => onHistory()}><IconCheckCircle /><strong>{approved.length}</strong><span>{t('Approved notes', 'मंज़ूर नोट')}</span></button>
    </div>
    <div className="doctor-home-grid">
      <section id="doctor-patient-queue" className="doctor-home-panel" aria-labelledby="doctor-queue-title"><div className="family-section-heading"><h2 id="doctor-queue-title">{t('Care notes', 'देखभाल के नोट')}</h2><button type="button" className="care-text-button" aria-pressed={onlyPending} onClick={() => setOnlyPending(!onlyPending)}>{onlyPending ? t('Show everyone', 'सभी को देखें') : t('To review', 'जाँचने हैं')}</button></div>
        <ul className="doctor-patient-queue">{visiblePatients.map((patient) => {
          const state = records[patient.id]; const release = state ? latestSummaryRelease(state) : null;
          return <li key={patient.id}><div className="doctor-patient-initial" aria-hidden="true">{patient.name.split(' ').map((part) => part[0]).slice(0, 2).join('')}</div><div><strong>{patient.name}</strong><span>{patient.diagnosis}</span><small>{state?.versionPublished ? `${t('Approved copy', 'मंज़ूर कॉपी')} · ${t('Version', 'संस्करण')} ${release?.number ?? ''}` : t('Needs review', 'जाँच बाकी है')}</small></div><button type="button" className="care-text-button" aria-label={`${t('Open care note for', 'देखभाल का नोट खोलें')} ${patient.name}`} onClick={() => onReview(patient.id)}>{t('Open', 'खोलें')} <IconArrowRight /></button></li>;
        })}</ul>
        {!visiblePatients.length && <p>{t('No matching people here.', 'कोई मेल नहीं मिला।')}</p>}
      </section>
      <section id="doctor-visit-list" className="doctor-home-panel doctor-visit-panel" aria-labelledby="doctor-visits-title"><div className="family-section-heading"><h2 id="doctor-visits-title">{t('Appointments', 'मुलाकातें')}</h2><div className="doctor-schedule-toggle"><button type="button" aria-pressed={schedule === 'today'} onClick={() => setSchedule('today')}>{t('Today', 'आज')}</button><button type="button" aria-pressed={schedule === 'upcoming'} onClick={() => setSchedule('upcoming')}>{t('Upcoming', 'आगे की')}</button></div></div>
        <ol className="doctor-visit-list">{visibleVisits.map((visit) => <li key={visit.id}><div className="doctor-visit-date"><time dateTime={`${visit.date}T${visit.time}`}>{dateLabel(visit.date)}<strong>{visit.time}</strong></time></div><div><strong>{patients.find((patient) => patient.id === visit.hospitalId)?.name}</strong><span>{visit.type}</span><small>{visit.clinician} · {visit.mode}</small></div><VisitInstructions key={`${visit.hospitalId}:${visit.id}`} value={visit.preparationInstructions} author={physician} clinician={visit.clinician} hindi={hindi} onChange={(value) => onInstructions(visit.hospitalId, visit.id, value)} /></li>)}</ol>
        {!visibleVisits.length && <p>{t('No appointments recorded for this view.', 'इस सूची में कोई मुलाकात दर्ज नहीं है।')}</p>}
      </section>
    </div>
  </section>;
}
