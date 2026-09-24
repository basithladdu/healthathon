'use client';

import { useState } from 'react';
import type { Appointment } from './appointments-page';
import { latestSummaryRelease, type SummaryState } from './summary-state';
import { VisitInstructions } from './visit-instructions';
import { IconUsers, IconFileText, IconClock, IconCheckCircle, IconArrowRight } from './icons';
import type { CareConversationRequest } from './care-conversation-request-state';
import { CareConversationImpact } from './care-conversation-impact.tsx';
import type { CareConversationImpactEpisode } from './care-conversation-impact.ts';

export function DoctorCareHome({ patients, records, appointments, physician, today, hindi, conversationRequests = [], conversationImpactEpisodes = [], conversationImpactLoading = false, onReview, onRecord, onHistory, onInstructions, onStartConversationRequest, onOpenConversationVersion, onTasks }: {
  patients: Array<{ id: string; name: string; diagnosis: string }>; records: Record<string, SummaryState>;
  appointments: Appointment[]; physician: string; today: string; hindi: boolean;
  conversationRequests?: CareConversationRequest[];
  conversationImpactEpisodes?: CareConversationImpactEpisode[];
  conversationImpactLoading?: boolean;
  onReview: (patientId: string) => void;
  onTasks?: (patientId: string) => void;
  onRecord: (patientId?: string) => void; onHistory: (patientId?: string) => void;
  onInstructions: (patientId: string, appointmentId: string, value: Appointment['preparationInstructions']) => void;
  onStartConversationRequest?: (id: string) => void;
  onOpenConversationVersion?: (patientId: string, version: number) => void;
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
  const activeConversationRequests = conversationRequests.filter((request) => request.status === 'requested' || request.status === 'in-progress').sort((a, b) => a.status === b.status ? a.createdAt.localeCompare(b.createdAt) : a.status === 'requested' ? -1 : 1);
  const completedConversationRequests = conversationRequests.filter((request) => request.status === 'completed' && request.signedVersion).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
  const renderConversationRequest = (request: CareConversationRequest) => {
    const patient = patients.find((item) => item.id === request.patientId);
    const completed = request.status === 'completed' && Boolean(request.signedVersion);
    return <li key={request.id}><div className="doctor-conversation-person"><div className="doctor-patient-initial" aria-hidden="true">{patient?.name.split(' ').map((part) => part[0]).slice(0, 2).join('') ?? '·'}</div><div><strong>{patient?.name ?? request.patientId}</strong><span>{request.requestedBy} · {request.role === 'family' ? t('Family', 'परिवार') : t('Patient', 'मरीज़')}</span><small>{request.topics.length ? request.topics.join(' · ') : request.note || t('No topic added', 'विषय नहीं जोड़ा')}</small></div></div>
      <div className="doctor-conversation-status">{completed ? <><span>{t('Recorded', 'दर्ज')}</span><button type="button" disabled={!onOpenConversationVersion} onClick={() => onOpenConversationVersion?.(request.patientId, request.signedVersion!)}>{t('Open signed note', 'हस्ताक्षरित नोट खोलें')} <IconArrowRight /></button></> : <><span>{request.status === 'in-progress' ? t('In progress', 'जारी है') : t('Requested', 'अनुरोध')}</span><button type="button" disabled={!onStartConversationRequest} className="care-text-button" onClick={() => onStartConversationRequest?.(request.id)}>{request.status === 'in-progress' ? t('Continue', 'जारी रखें') : t('Start conversation', 'बातचीत शुरू करें')} <IconArrowRight /></button></>}</div>
    </li>;
  };
  const dateLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short' });
  return <section className="doctor-care-home">
    <header className="doctor-care-heading"><div><h1>{t('Care team', 'देखभाल टीम')}</h1><span>{physician}</span></div><label className="doctor-patient-search"><span className="sr-only">{t('Find a patient', 'मरीज़ ढूँढें')}</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('Find a patient', 'मरीज़ ढूँढें')} /></label></header>
    <div className="doctor-home-actions"><button type="button" className="primary-button" onClick={() => onRecord()}><IconFileText />{t('Record conversation', 'बातचीत दर्ज करें')}</button><button type="button" className="secondary-button" onClick={() => onHistory()}><IconClock />{t('Note history', 'पुराने नोट')}</button></div>
    {(activeConversationRequests.length > 0 || completedConversationRequests.length > 0) && <section className="doctor-home-panel doctor-conversation-queue" aria-labelledby="doctor-conversation-queue-title"><div className="family-section-heading"><h2 id="doctor-conversation-queue-title">{t('Conversation requests', 'बातचीत के अनुरोध')}</h2><span>{activeConversationRequests.length}</span></div>
      {activeConversationRequests.length > 0 ? <ul>{activeConversationRequests.map(renderConversationRequest)}</ul> : <p className="doctor-conversation-empty">{t('No open conversation requests.', 'बातचीत के खुले अनुरोध नहीं हैं।')}</p>}
      {completedConversationRequests.length > 0 && <details className="doctor-conversation-history"><summary>{t('Recorded conversations', 'दर्ज बातचीत')} · {completedConversationRequests.length}</summary><ul>{completedConversationRequests.map(renderConversationRequest)}</ul></details>}
    </section>}
    <CareConversationImpact episodes={conversationImpactEpisodes} requests={conversationRequests} loading={conversationImpactLoading} />
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
          return <li key={patient.id}><div className="doctor-patient-initial" aria-hidden="true">{patient.name.split(' ').map((part) => part[0]).slice(0, 2).join('')}</div><div><strong>{patient.name}</strong><span>{patient.diagnosis}</span><small>{state?.versionPublished ? `${t('Approved copy', 'मंज़ूर कॉपी')} · ${t('Version', 'संस्करण')} ${release?.number ?? ''}` : t('Needs review', 'जाँच बाकी है')}</small>{onTasks && <button type="button" className="care-text-button" onClick={() => onTasks(patient.id)}>{t('Assign follow-up', 'आगे का काम सौंपें')}</button>}</div><button type="button" className="care-text-button" aria-label={`${t('Open care note for', 'देखभाल का नोट खोलें')} ${patient.name}`} onClick={() => onReview(patient.id)}>{t('Open', 'खोलें')} <IconArrowRight /></button></li>;
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
