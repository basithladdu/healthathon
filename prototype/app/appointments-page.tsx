'use client';
import React, { useMemo, useRef, useState, useEffect } from 'react';
import {
  appendScheduledAppointmentForPatient,
  canMarkAppointmentAttended,
  nextAppointmentId,
  updateAppointmentForPatient,
  validateAppointmentDateTime,
  type AppointmentDateTimeError,
} from './appointment-state';

export type AppointmentStatus = 'Scheduled' | 'Attended' | 'Rescheduled' | 'Cancelled' | 'Missed';
export type AppointmentMode = 'In person' | 'Telephone' | 'Video';
export type DocumentationStatus = 'Summary verified' | 'Draft in review' | 'Pending' | 'Not required';

export type Appointment = {
  id: string;
  hospitalId: string;
  date: string;
  time: string;
  type: string;
  clinician: string;
  mode: AppointmentMode;
  status: AppointmentStatus;
  outcome?: string;
  note?: string;
  documentation: DocumentationStatus;
};

export type AppointmentPatient = {
  hospitalId: string;
  name: string;
  purpose: string;
  owner: string;
  followUpStatus: string;
  lastActivity: string;
  dob: string;
  sex: string;
  age: number;
  team: string;
  participant: string;
  contactChannel: string;
};

export type AppointmentsPageProps = {
  patients: AppointmentPatient[];
  selectedId: string;
  onSelectPatient: (hospitalId: string) => void;
  appointments: Appointment[];
  onAppointmentsChange: (next: Appointment[]) => void;
  canSchedule: boolean;
  canStartConversation: boolean;
  readOnlyReason: string;
  hasVerifiedRecord: (hospitalId: string) => boolean;
  versionLabel: (hospitalId: string) => string;
  onAudit: (event: string, record: string) => void;
  onNotify: (message: string) => void;
  onOpenProfile: (hospitalId: string) => void;
  onStartConversation: (hospitalId: string) => void;
  onRetrieve: (hospitalId: string) => void;
  onRecordOutreach: (hospitalId: string) => void;
  onOpenAudit: () => void;
  onOpenRecords: () => void;
  onPatientActivity: (hospitalId: string, activity: string, nextDue?: string) => void;
};

export const DEMO_TODAY = '2026-08-28';

export const initialAppointments: Appointment[] = [
  { id: 'APT-1001', hospitalId: 'CANCER-20418', date: '2026-08-14', time: '11:00', type: 'Goals-of-care conversation', clinician: 'Dr Sujay', mode: 'In person', status: 'Attended', outcome: 'Meera asked for time to talk with her family first', documentation: 'Summary verified' },
  { id: 'APT-1004', hospitalId: 'CANCER-20418', date: '2026-08-21', time: '12:00', type: 'Summary review', clinician: 'Dr Sujay', mode: 'In person', status: 'Attended', outcome: 'Version 1 verified and released', documentation: 'Summary verified' },
  { id: 'APT-1007', hospitalId: 'CANCER-20418', date: '2026-08-26', time: '16:00', type: 'Follow-up telephone call', clinician: 'Anitha Rao', mode: 'Telephone', status: 'Attended', outcome: 'Family confirmed the discussion is underway', documentation: 'Not required' },
  { id: 'APT-1010', hospitalId: 'CANCER-20418', date: '2026-08-28', time: '10:30', type: 'Family meeting (goals-of-care, continued)', clinician: 'Dr Sujay', mode: 'In person', status: 'Scheduled', documentation: 'Pending' },
  { id: 'APT-1002', hospitalId: 'CANCER-20431', date: '2026-08-18', time: '09:30', type: 'Goals-of-care conversation', clinician: 'Dr Sujay', mode: 'In person', status: 'Attended', outcome: 'Planned discussion started', documentation: 'Pending' },
  { id: 'APT-1008', hospitalId: 'CANCER-20431', date: '2026-08-25', time: '15:00', type: 'Follow-up telephone call', clinician: 'Anitha Rao', mode: 'Telephone', status: 'Missed', outcome: 'Unable to reach patient', documentation: 'Not required' },
  { id: 'APT-1003', hospitalId: 'CANCER-20392', date: '2026-08-20', time: '10:00', type: 'Goals-of-care conversation', clinician: 'Dr Sujay', mode: 'In person', status: 'Attended', outcome: 'Draft saved for review', documentation: 'Draft in review' },
  { id: 'APT-1011', hospitalId: 'CANCER-20392', date: '2026-08-30', time: '11:30', type: 'Summary review', clinician: 'Dr Sujay', mode: 'Video', status: 'Scheduled', documentation: 'Draft in review' },
  { id: 'APT-1005', hospitalId: 'CANCER-20377', date: '2026-08-12', time: '14:00', type: 'Goals-of-care conversation', clinician: 'Dr Sujay', mode: 'In person', status: 'Attended', outcome: 'Priorities recorded', documentation: 'Summary verified' },
  { id: 'APT-1006', hospitalId: 'CANCER-20377', date: '2026-08-21', time: '10:00', type: 'Summary review', clinician: 'Dr Sujay', mode: 'In person', status: 'Attended', outcome: 'Version 1 verified and released', documentation: 'Summary verified' },
  { id: 'APT-1012', hospitalId: 'CANCER-20377', date: '2026-09-04', time: '12:00', type: 'Review date confirmation call', clinician: 'Anitha Rao', mode: 'Telephone', status: 'Scheduled', documentation: 'Not required' },
];

const ENROLMENT_DATES: Record<string, string> = {
  'CANCER-20418': '10 Aug 2026',
  'CANCER-20431': '15 Aug 2026',
  'CANCER-20392': '17 Aug 2026',
  'CANCER-20377': '05 Aug 2026',
};

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function parseYMD(s: string): { y: number; m: number; d: number } {
  const [y, m, d] = s.split('-').map((n) => parseInt(n, 10));
  return { y, m, d };
}

function formatDate(s: string): string {
  const { y, m, d } = parseYMD(s);
  return `${String(d).padStart(2, '0')} ${MONTHS[m - 1]} ${y}`;
}

function formatShort(s: string): string {
  const { m, d } = parseYMD(s);
  return `${String(d).padStart(2, '0')} ${MONTHS[m - 1]}`;
}

function daysBetween(a: string, b: string): number {
  const pa = parseYMD(a);
  const pb = parseYMD(b);
  const ua = Date.UTC(pa.y, pa.m - 1, pa.d);
  const ub = Date.UTC(pb.y, pb.m - 1, pb.d);
  return Math.round((ub - ua) / 86400000);
}

function clinicianColor(name: string): string {
  if (name === 'Dr Sujay') return 'var(--accent)';
  if (name === 'Anitha Rao') return '#b98a2f';
  if (name === 'Dr Isha Menon') return 'var(--low-ink)';
  return 'var(--text-3)';
}

function initials(name: string): string {
  const parts = name.replace(/^Dr\s+/i, '').trim().split(/\s+/);
  return parts.slice(0, 2).map((p) => p[0]?.toUpperCase() ?? '').join('');
}

function nextAppointment(list: Appointment[]): Appointment | undefined {
  return list
    .filter((a) => a.status === 'Scheduled' && a.date >= DEMO_TODAY)
    .sort((a, b) => (a.date === b.date ? a.time.localeCompare(b.time) : a.date.localeCompare(b.date)))[0];
}

function lastAttended(list: Appointment[]): Appointment | undefined {
  return list
    .filter((a) => a.status === 'Attended')
    .sort((a, b) => (a.date === b.date ? b.time.localeCompare(a.time) : b.date.localeCompare(a.date)))[0];
}

function appointmentDateTimeMessage(error: AppointmentDateTimeError, date: string, time: string): string {
  if (error === 'date-required') return !date && !time ? 'Choose a date and time.' : 'Choose a date.';
  if (error === 'time-required') return 'Choose a time.';
  if (error === 'date-invalid') return 'Choose a valid date.';
  if (error === 'time-invalid') return 'Choose a valid time.';
  return `Choose a date on or after ${formatDate(DEMO_TODAY)}.`;
}

/* ---------- Icons ---------- */
type IconProps = { size?: number; className?: string };
const Icon = ({ size = 16, children }: IconProps & { children: React.ReactNode }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    {children}
  </svg>
);
const IconCalendarPlus = (p: IconProps) => (
  <Icon {...p}><rect x="3" y="4" width="18" height="17" rx="2" /><line x1="3" y1="9" x2="21" y2="9" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="12" y1="13" x2="12" y2="18" /><line x1="9.5" y1="15.5" x2="14.5" y2="15.5" /></Icon>
);
const IconPhone = (p: IconProps) => (
  <Icon {...p}><path d="M4 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L14 13l5 2v4a2 2 0 0 1-2.2 2A17 17 0 0 1 3 6.2 2 2 0 0 1 4 4Z" /></Icon>
);
const IconChat = (p: IconProps) => (
  <Icon {...p}><path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></Icon>
);
const IconLock = (p: IconProps) => (
  <Icon {...p}><rect x="4" y="11" width="16" height="9" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></Icon>
);
const IconUser = (p: IconProps) => (
  <Icon {...p}><circle cx="12" cy="8" r="3.5" /><path d="M4 20c1.5-4 5-6 8-6s6.5 2 8 6" /></Icon>
);
const IconSearch = (p: IconProps) => (
  <Icon {...p}><circle cx="11" cy="11" r="7" /><line x1="21" y1="21" x2="16.5" y2="16.5" /></Icon>
);
const IconKebab = (p: IconProps) => (
  <Icon {...p}><circle cx="12" cy="5" r="1.4" fill="currentColor" /><circle cx="12" cy="12" r="1.4" fill="currentColor" /><circle cx="12" cy="19" r="1.4" fill="currentColor" /></Icon>
);
const IconArrowRight = (p: IconProps) => (
  <Icon {...p}><line x1="4" y1="12" x2="20" y2="12" /><polyline points="13 5 20 12 13 19" /></Icon>
);
const IconCheck = (p: IconProps) => (
  <Icon {...p}><polyline points="20 6 9 17 4 12" /></Icon>
);
const IconX = (p: IconProps) => (
  <Icon {...p}><line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" /></Icon>
);
const IconFile = (p: IconProps) => (
  <Icon {...p}><path d="M6 2h9l5 5v15H6Z" /><path d="M15 2v5h5" /></Icon>
);
const IconOpenArrow = (p: IconProps) => (
  <Icon {...p}><path d="M7 17 17 7" /><path d="M8 7h9v9" /></Icon>
);
const IconClock = (p: IconProps) => (
  <Icon {...p}><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 3" /></Icon>
);

/* ---------- Small shared bits ---------- */

function StatusPill({ status }: { status: AppointmentStatus }) {
  const map: Record<AppointmentStatus, string> = {
    Scheduled: 'low',
    Attended: 'safe',
    Rescheduled: 'medium',
    Cancelled: 'neutral',
    Missed: 'critical',
  };
  const kind = map[status];
  return <span className={`ap-pill ap-pill-${kind}`}>{status}</span>;
}

function DocPill({ doc }: { doc: DocumentationStatus }) {
  const kind =
    doc === 'Summary verified' ? 'safe' : doc === 'Draft in review' ? 'low' : doc === 'Pending' ? 'medium' : 'neutral';
  return <span className={`ap-pill ap-pill-${kind}`}>{doc}</span>;
}

function useOutsideClose(onClose: () => void) {
  const ref = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    }
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [onClose]);
  return ref;
}

function Menu({
  open,
  onClose,
  items,
}: {
  open: boolean;
  onClose: () => void;
  items: { label: string; onSelect: () => void; disabled?: boolean }[];
}) {
  const ref = useOutsideClose(onClose);
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="ap-menu" role="menu" ref={ref}>
      {items.map((it) => (
        <button
          key={it.label}
          role="menuitem"
          className="ap-menu-item"
          disabled={it.disabled}
          onClick={() => {
            if (it.disabled) return;
            it.onSelect();
            onClose();
          }}
        >
          {it.label}
        </button>
      ))}
    </div>
  );
}

/* ---------- Dialogs ---------- */

type DialogMode = 'attend' | 'reschedule' | 'cancel' | null;

function useFocusTrapOpen(open: boolean, firstRef: React.RefObject<HTMLElement | null>) {
  const opener = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (!open) return;
    opener.current = document.activeElement as HTMLElement;
    const timer = setTimeout(() => firstRef.current?.focus(), 0);
    return () => {
      clearTimeout(timer);
      opener.current?.focus();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);
}

function Dialog({
  open,
  onClose,
  titleId,
  children,
}: {
  open: boolean;
  onClose: () => void;
  titleId: string;
  children: React.ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="ap-dialog-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="ap-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        {children}
      </div>
    </div>
  );
}

function AppointmentDialog({
  open,
  onClose,
  appt,
  patient,
  canSchedule,
  canStartConversation,
  readOnlyReason,
  onSave,
  onAudit,
  onNotify,
  onStartConversation,
  onPatientActivity,
  appointments,
  defaultMode,
}: {
  open: boolean;
  onClose: () => void;
  appt: Appointment | null;
  patient: AppointmentPatient | undefined;
  canSchedule: boolean;
  canStartConversation: boolean;
  readOnlyReason: string;
  onSave: (next: Appointment, extra?: Appointment) => boolean;
  onAudit: (event: string, record: string) => void;
  onNotify: (message: string) => void;
  onStartConversation: (hospitalId: string) => void;
  onPatientActivity: (hospitalId: string, activity: string, nextDue?: string) => void;
  appointments: Appointment[];
  defaultMode?: DialogMode;
}) {
  const [mode, setMode] = useState<DialogMode>(defaultMode ?? null);
  const [outcome, setOutcome] = useState('Attended as planned');
  const [note, setNote] = useState('');
  const [newDate, setNewDate] = useState('');
  const [newTime, setNewTime] = useState('');
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const firstRef = useRef<HTMLButtonElement | null>(null);
  useFocusTrapOpen(open, firstRef);

  if (!open || !appt || !patient) return null;
  const titleId = `ap-dialog-title-${appt.id}`;
  const isGuided = /Goals-of-care|Family meeting/i.test(appt.type);

  function saveAttend() {
    if (!canSchedule || !appt || !canMarkAppointmentAttended(appt, DEMO_TODAY)) return;
    const next: Appointment = { ...appt, status: 'Attended', outcome, note: note.trim() || appt.note };
    if (!onSave(next)) return;
    onAudit(`Marked appointment ${appt.id} attended · ${outcome}`, patient!.name);
    onNotify('Appointment marked attended.');
    onPatientActivity(patient!.hospitalId, `Appointment attended · ${outcome}`);
    onClose();
  }

  function saveReschedule() {
    if (!canSchedule || !appt) return;
    const dateTimeError = validateAppointmentDateTime(newDate, newTime, DEMO_TODAY);
    if (dateTimeError) {
      setError(appointmentDateTimeMessage(dateTimeError, newDate, newTime));
      return;
    }
    const rescheduleReason = reason.trim();
    const newId = nextAppointmentId(appointments);
    const updated: Appointment = {
      ...appt,
      status: 'Rescheduled',
      outcome: `Rescheduled to ${formatDate(newDate)} ${newTime}${rescheduleReason ? ' — ' + rescheduleReason : ''}`,
    };
    const created: Appointment = {
      id: newId,
      hospitalId: appt.hospitalId,
      date: newDate,
      time: newTime,
      type: appt.type,
      clinician: appt.clinician,
      mode: appt.mode,
      status: 'Scheduled',
      documentation: appt.documentation,
    };
    if (!onSave(updated, created)) return;
    onAudit(`Rescheduled appointment ${appt.id} to ${formatDate(newDate)} ${newTime} as ${newId}`, patient!.name);
    onNotify('Appointment rescheduled.');
    onPatientActivity(patient!.hospitalId, `Appointment rescheduled to ${formatDate(newDate)}`, formatDate(newDate));
    onClose();
  }

  function saveCancel() {
    if (!canSchedule || !appt) return;
    const cancellationReason = reason.trim();
    if (!cancellationReason) {
      setError('Add a reason before cancelling.');
      return;
    }
    const next: Appointment = { ...appt, status: 'Cancelled', outcome: `Cancelled — ${cancellationReason}` };
    if (!onSave(next)) return;
    onAudit(`Cancelled appointment ${appt.id} · ${cancellationReason}`, patient!.name);
    onNotify('Appointment cancelled.');
    onPatientActivity(patient!.hospitalId, 'Appointment cancelled');
    onClose();
  }

  return (
    <Dialog open={open} onClose={onClose} titleId={titleId}>
      <h2 id={titleId} className="ap-dialog-title">{appt.type}</h2>
      <div className="ap-dialog-sub">{patient.name}</div>
      <div className="ap-dialog-sub">{formatDate(appt.date)} · {appt.time}</div>
      <div className="ap-dialog-row">
        <span className="ap-dot" style={{ background: clinicianColor(appt.clinician) }} />
        <span>{appt.clinician} · {appt.mode}</span>
        <StatusPill status={appt.status} />
      </div>
      {appt.outcome && <div className="ap-dialog-note">{appt.outcome}</div>}
      {appt.note && <div className="ap-dialog-note">{appt.note}</div>}

      {appt.status === 'Scheduled' && (
        <div className="ap-dialog-actions">
          {!canSchedule && <div className="ap-notice">{readOnlyReason}</div>}
          <div className="ap-tabbar" role="tablist">
            <button ref={firstRef} role="tab" aria-selected={mode === 'attend'} className={`ap-tab ${mode === 'attend' ? 'active' : ''}`} disabled={!canSchedule || !canMarkAppointmentAttended(appt, DEMO_TODAY)} onClick={() => setMode('attend')}>Mark attended</button>
            <button role="tab" aria-selected={mode === 'reschedule'} className={`ap-tab ${mode === 'reschedule' ? 'active' : ''}`} disabled={!canSchedule} onClick={() => setMode('reschedule')}>Reschedule</button>
            <button role="tab" aria-selected={mode === 'cancel'} className={`ap-tab ${mode === 'cancel' ? 'active' : ''}`} disabled={!canSchedule} onClick={() => setMode('cancel')}>Cancel appointment</button>
          </div>

          {mode === 'attend' && (
            <div className="ap-form">
              <label className="ap-field">
                <span>Outcome</span>
                <select value={outcome} onChange={(e) => setOutcome(e.target.value)}>
                  <option>Attended as planned</option>
                  <option>Family requested more time</option>
                  <option>Escalated to the treating clinician</option>
                  <option>Discussion completed</option>
                </select>
              </label>
              <label className="ap-field">
                <span>Note</span>
                <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
              </label>
              <button className="ap-btn-primary" disabled={!canSchedule || !canMarkAppointmentAttended(appt, DEMO_TODAY)} onClick={saveAttend}>Save</button>
            </div>
          )}

          {mode === 'reschedule' && (
            <div className="ap-form">
              <label className="ap-field">
                <span>New date</span>
                <input type="date" min={DEMO_TODAY} value={newDate} onChange={(e) => setNewDate(e.target.value)} />
              </label>
              <label className="ap-field">
                <span>New time</span>
                <input type="time" value={newTime} onChange={(e) => setNewTime(e.target.value)} />
              </label>
              <label className="ap-field">
                <span>Reason</span>
                <input type="text" value={reason} onChange={(e) => setReason(e.target.value)} />
              </label>
              {error && <div role="alert" className="ap-alert">{error}</div>}
              <button className="ap-btn-primary" disabled={!canSchedule} onClick={saveReschedule}>Save</button>
            </div>
          )}

          {mode === 'cancel' && (
            <div className="ap-form">
              <label className="ap-field">
                <span>Reason for cancelling</span>
                <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={2} />
              </label>
              {error && <div role="alert" className="ap-alert">{error}</div>}
              <button className="ap-btn-primary" disabled={!canSchedule} onClick={saveCancel}>Save</button>
            </div>
          )}

          {isGuided && (
            <button
              className="ap-btn-secondary"
              disabled={!canStartConversation}
              onClick={() => {
                onClose();
                onStartConversation(patient.hospitalId);
              }}
            >
              Open guided conversation
            </button>
          )}
        </div>
      )}

      <div className="ap-dialog-footer">
        <button className="ap-btn-plain" onClick={onClose}>Close</button>
      </div>
    </Dialog>
  );
}

function ScheduleDialog({
  open,
  onClose,
  patient,
  appointments,
  canSchedule,
  readOnlyReason,
  onCreate,
  onAudit,
  onNotify,
  onPatientActivity,
}: {
  open: boolean;
  onClose: () => void;
  appointments: Appointment[];
  patient: AppointmentPatient | undefined;
  canSchedule: boolean;
  readOnlyReason: string;
  onCreate: (a: Appointment) => boolean;
  onAudit: (event: string, record: string) => void;
  onNotify: (message: string) => void;
  onPatientActivity: (hospitalId: string, activity: string, nextDue?: string) => void;
}) {
  const [type, setType] = useState('Goals-of-care conversation');
  const [date, setDate] = useState('');
  const [time, setTime] = useState('');
  const [clinician, setClinician] = useState('Dr Sujay');
  const [mode, setMode] = useState<AppointmentMode>('In person');
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const firstRef = useRef<HTMLInputElement | null>(null);
  useFocusTrapOpen(open, firstRef);

  if (!open || !patient) return null;
  const titleId = 'ap-schedule-title';

  function save() {
    if (!canSchedule) return;
    const dateTimeError = validateAppointmentDateTime(date, time, DEMO_TODAY);
    if (dateTimeError) {
      setError(appointmentDateTimeMessage(dateTimeError, date, time));
      return;
    }
    const newId = nextAppointmentId(appointments);
    const documentation: DocumentationStatus = /Goals-of-care|Summary review|Family meeting/i.test(type) ? 'Pending' : 'Not required';
    const appt: Appointment = {
      id: newId,
      hospitalId: patient!.hospitalId,
      date,
      time,
      type,
      clinician,
      mode,
      status: 'Scheduled',
      documentation,
      note: note || undefined,
    };
    if (!onCreate(appt)) return;
    onAudit(`Scheduled ${type} ${formatDate(date)} ${time} as ${newId}`, patient!.name);
    onNotify('Appointment scheduled.');
    onPatientActivity(patient!.hospitalId, `Appointment scheduled for ${formatDate(date)}`, formatDate(date));
    onClose();
  }

  return (
    <Dialog open={open} onClose={onClose} titleId={titleId}>
      <h2 id={titleId} className="ap-dialog-title">Schedule appointment</h2>
      {!canSchedule && <div className="ap-notice">{readOnlyReason}</div>}
      <div className="ap-form">
        <label className="ap-field">
          <span>Patient</span>
          <input type="text" value={patient.name} readOnly />
        </label>
        <label className="ap-field">
          <span>Appointment type</span>
          <select value={type} onChange={(e) => setType(e.target.value)}>
            <option>Goals-of-care conversation</option>
            <option>Family meeting (goals-of-care, continued)</option>
            <option>Summary review</option>
            <option>Follow-up telephone call</option>
            <option>Review date confirmation call</option>
          </select>
        </label>
        <label className="ap-field">
          <span>Date</span>
          <input ref={firstRef} type="date" min={DEMO_TODAY} value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label className="ap-field">
          <span>Time</span>
          <input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
        </label>
        <label className="ap-field">
          <span>Clinician</span>
          <select value={clinician} onChange={(e) => setClinician(e.target.value)}>
            <option>Dr Sujay</option>
            <option>Anitha Rao</option>
          </select>
        </label>
        <label className="ap-field">
          <span>Mode</span>
          <select value={mode} onChange={(e) => setMode(e.target.value as AppointmentMode)}>
            <option>In person</option>
            <option>Telephone</option>
            <option>Video</option>
          </select>
        </label>
        <label className="ap-field">
          <span>Note</span>
          <textarea value={note} onChange={(e) => setNote(e.target.value)} rows={2} />
        </label>
        <div className="ap-muted-note">No reminder is sent. Share the time with the family through your usual channel.</div>
        {error && <div role="alert" className="ap-alert">{error}</div>}
        <button className="ap-btn-primary" disabled={!canSchedule} onClick={save}>Save</button>
      </div>
      <div className="ap-dialog-footer">
        <button className="ap-btn-plain" onClick={onClose}>Close</button>
      </div>
    </Dialog>
  );
}

/* ---------- Main component ---------- */

export function AppointmentsPage(props: AppointmentsPageProps): React.JSX.Element {
  const {
    patients,
    selectedId,
    onSelectPatient,
    appointments,
    onAppointmentsChange,
    canSchedule,
    canStartConversation,
    readOnlyReason,
    hasVerifiedRecord,
    versionLabel,
    onAudit,
    onNotify,
    onOpenProfile,
    onStartConversation,
    onRetrieve,
    onRecordOutreach,
    onOpenAudit,
    onOpenRecords,
    onPatientActivity,
  } = props;

  const [searchOpen, setSearchOpen] = useState(false);
  const [search, setSearch] = useState('');
  const [tab, setTab] = useState<'All' | 'Today' | 'Upcoming'>('All');
  const [openMenuFor, setOpenMenuFor] = useState<string | null>(null);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [apptDialog, setApptDialog] = useState<{ appt: Appointment; mode: DialogMode } | null>(null);
  const [historyMenuFor, setHistoryMenuFor] = useState<string | null>(null);
  const [headerMenuOpen, setHeaderMenuOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    if (searchOpen) setTimeout(() => searchInputRef.current?.focus(), 0);
  }, [searchOpen]);

  const selectedPatient = patients.find((p) => p.hospitalId === selectedId);

  const filteredPatients = useMemo(() => {
    let list = patients;
    if (search.trim()) {
      const q = search.trim().toLowerCase();
      list = list.filter((p) => p.name.toLowerCase().includes(q) || p.hospitalId.toLowerCase().includes(q));
    }
    if (tab === 'Today') {
      list = list.filter((p) => appointments.some((a) => a.hospitalId === p.hospitalId && a.status === 'Scheduled' && a.date === DEMO_TODAY));
    } else if (tab === 'Upcoming') {
      list = list.filter((p) => appointments.some((a) => a.hospitalId === p.hospitalId && a.status === 'Scheduled' && a.date > DEMO_TODAY));
    }
    return list;
  }, [patients, search, tab, appointments]);

  const patientAppts = useMemo(
    () => appointments.filter((a) => a.hospitalId === selectedId),
    [appointments, selectedId]
  );
  const timelineAppts = useMemo(
    () => [...patientAppts].sort((a, b) => (a.date === b.date ? b.time.localeCompare(a.time) : b.date.localeCompare(a.date))),
    [patientAppts]
  );
  const historyAppts = useMemo(
    () => timelineAppts.filter((a) => a.status !== 'Scheduled'),
    [timelineAppts]
  );
  const nextAppt = useMemo(() => nextAppointment(patientAppts), [patientAppts]);
  const lastAtt = useMemo(() => lastAttended(patientAppts), [patientAppts]);
  const daysSinceAttended = lastAtt ? daysBetween(lastAtt.date, DEMO_TODAY) : null;

  function updateAppointment(next: Appointment, extra?: Appointment): boolean {
    if (!canSchedule) return false;
    const result = updateAppointmentForPatient(appointments, selectedId, next, extra);
    if (!result.changed) return false;
    onAppointmentsChange(result.appointments);
    return true;
  }
  function createAppointment(a: Appointment): boolean {
    if (!canSchedule) return false;
    const result = appendScheduledAppointmentForPatient(appointments, selectedId, a);
    if (!result.changed) return false;
    onAppointmentsChange(result.appointments);
    return true;
  }

  const ringRadius = 40;
  const ringCirc = 2 * Math.PI * ringRadius;
  const ringFrac = daysSinceAttended != null ? Math.min(daysSinceAttended, 30) / 30 : 0;

  return (
    <div className="ap-root">
      <div className="ap-breadcrumb">Appointments <span className="ap-crumb-sep">›</span> {selectedPatient?.name ?? ''}</div>

      {/* Patient Queue */}
      <aside className="ap-queue">
        <div className="ap-queue-header">
          <span className="ap-queue-title">Patient Queue</span>
          <button
            className="ap-icon-btn"
            aria-label="Search patients"
            title="Search patients"
            onClick={() => setSearchOpen((s) => !s)}
          >
            <IconSearch />
          </button>
        </div>
        {searchOpen && (
          <input
            ref={searchInputRef}
            className="ap-search-input"
            type="text"
            placeholder="Search name or hospital ID"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') { setSearchOpen(false); setSearch(''); }
            }}
          />
        )}
        <div className="ap-tabbar" role="tablist">
          {(['All', 'Today', 'Upcoming'] as const).map((t) => (
            <button key={t} role="tab" aria-selected={tab === t} className={`ap-tab ${tab === t ? 'active' : ''}`} onClick={() => setTab(t)}>
              {t}
            </button>
          ))}
        </div>
        <div className="ap-queue-list">
          {filteredPatients.length === 0 && <div className="ap-empty">No patients in this view.</div>}
          {filteredPatients.map((p) => {
            const na = nextAppointment(appointments.filter((a) => a.hospitalId === p.hospitalId));
            const isSelected = p.hospitalId === selectedId;
            return (
              <div
                key={p.hospitalId}
                className="ap-queue-card"
                data-selected={isSelected || undefined}
                aria-current={isSelected ? 'true' : undefined}
                onClick={() => onSelectPatient(p.hospitalId)}
              >
                <div className="ap-avatar">{initials(p.name)}</div>
                <div className="ap-queue-card-body">
                  <div className="ap-queue-card-name">{p.name}</div>
                  <div className="ap-queue-card-sub">
                    {na ? `Next: ${formatDate(na.date)} · ${na.time}` : 'No upcoming appointment'}
                  </div>
                </div>
                <div className="ap-queue-card-actions">
                  <button
                    className="ap-icon-btn"
                    aria-haspopup="menu"
                    aria-expanded={openMenuFor === p.hospitalId}
                    aria-label="More actions"
                    title="More actions"
                    onClick={(e) => { e.stopPropagation(); setOpenMenuFor(openMenuFor === p.hospitalId ? null : p.hospitalId); }}
                  >
                    <IconKebab />
                  </button>
                  <Menu
                    open={openMenuFor === p.hospitalId}
                    onClose={() => setOpenMenuFor(null)}
                    items={[
                      { label: 'Open patient profile', onSelect: () => onOpenProfile(p.hospitalId) },
                      { label: 'View in audit log', onSelect: () => onOpenAudit() },
                    ]}
                  />
                  <button
                    className="ap-icon-btn"
                    aria-label={`Select ${p.name}`}
                    title={`Select ${p.name}`}
                    onClick={(e) => { e.stopPropagation(); onSelectPatient(p.hospitalId); }}
                  >
                    <IconArrowRight />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      </aside>

      {/* Main panel */}
      <main className="ap-main">
        {selectedPatient && (
          <>
            <div className="ap-header">
              <div className="ap-header-left">
                <div className="ap-avatar ap-avatar-lg">{initials(selectedPatient.name)}</div>
                <div>
                  <div className="ap-header-name">
                    {selectedPatient.name} <span className="ap-badge">CONTINUITY COHORT</span>
                  </div>
                  <div className="ap-header-sub">Enrolled since: {ENROLMENT_DATES[selectedPatient.hospitalId] ?? 'Not recorded'}</div>
                </div>
              </div>
              <div className="ap-header-actions">
                <button className="ap-icon-btn" title="Schedule appointment" aria-label="Schedule appointment" disabled={!canSchedule} onClick={() => setScheduleOpen(true)}>
                  <IconCalendarPlus />
                </button>
                <button className="ap-icon-btn" title="Log call outcome" aria-label="Log call outcome" onClick={() => onRecordOutreach(selectedPatient.hospitalId)}>
                  <IconPhone />
                </button>
                <button className="ap-icon-btn" title="Start guided conversation" aria-label="Start guided conversation" disabled={!canStartConversation} onClick={() => onStartConversation(selectedPatient.hospitalId)}>
                  <IconChat />
                </button>
                <button className="ap-icon-btn" title="Retrieve verified record" aria-label="Retrieve verified record" disabled={!hasVerifiedRecord(selectedPatient.hospitalId)} onClick={() => onRetrieve(selectedPatient.hospitalId)}>
                  <IconLock />
                </button>
                <button className="ap-icon-btn" title="Open patient profile" aria-label="Open patient profile" onClick={() => onOpenProfile(selectedPatient.hospitalId)}>
                  <IconUser />
                </button>
              </div>
            </div>
            <div className="ap-disclaimer">
              Calls, video visits and reminders are not connected.
            </div>
            {!canSchedule && <div className="ap-notice">{readOnlyReason}</div>}

            <div className="ap-row-a">
              <section className="ap-card">
                <h3 className="ap-card-title">Basic Information</h3>
                <dl className="ap-dl">
                  <div className="ap-dl-row"><IconUser size={14} /><dt>Sex</dt><dd>{selectedPatient.sex}</dd></div>
                  <div className="ap-dl-row"><IconClock size={14} /><dt>Date of birth</dt><dd>{selectedPatient.dob} ({selectedPatient.age} y)</dd></div>
                  <div className="ap-dl-row"><IconLock size={14} /><dt>Contact details</dt><dd>Held by the originating team</dd></div>
                  <div className="ap-dl-row"><IconUser size={14} /><dt>Primary contact</dt><dd>{selectedPatient.participant}</dd></div>
                  <div className="ap-dl-row"><IconPhone size={14} /><dt>Preferred channel</dt><dd><span className="ap-chip">{selectedPatient.contactChannel || 'Telephone call'}</span></dd></div>
                  <div className="ap-dl-row"><IconUser size={14} /><dt>Care team</dt><dd>{selectedPatient.team}</dd></div>
                </dl>
              </section>

              <section className="ap-card">
                <h3 className="ap-card-title">Appointment Schedule</h3>
                {timelineAppts.length === 0 ? (
                  <div className="ap-empty">
                    No appointments recorded for this patient.
                    <div>
                      <button className="ap-btn-secondary" disabled={!canSchedule} onClick={() => setScheduleOpen(true)}>Schedule appointment</button>
                    </div>
                  </div>
                ) : (
                  <div className="ap-timeline">
                    {timelineAppts.map((a) => (
                      <div className="ap-timeline-item" key={a.id}>
                        <div className="ap-timeline-date">{formatDate(a.date)}</div>
                        <button className="ap-timeline-box" onClick={() => setApptDialog({ appt: a, mode: null })}>
                          <div className="ap-timeline-marker" />
                          <div className="ap-timeline-type">{a.type}</div>
                          <div className="ap-timeline-meta">
                            <span className="ap-dot" style={{ background: clinicianColor(a.clinician) }} />
                            {a.clinician} · {a.time} · {a.mode}
                          </div>
                          <StatusPill status={a.status} />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              <div className="ap-right-stack">
                <section className="ap-next-card">
                  {nextAppt ? (
                    <>
                      <div className="ap-next-top">
                        <span className="ap-next-label">Next appointment</span>
                        <button
                          className="ap-icon-btn ap-icon-btn-onDark"
                          aria-haspopup="menu"
                          aria-expanded={headerMenuOpen}
                          aria-label="Appointment actions"
                          title="Appointment actions"
                          onClick={() => setHeaderMenuOpen((v) => !v)}
                        >
                          <IconKebab />
                        </button>
                        <Menu
                          open={headerMenuOpen}
                          onClose={() => setHeaderMenuOpen(false)}
                          items={[
                            { label: 'Reschedule', disabled: !canSchedule, onSelect: () => canSchedule && setApptDialog({ appt: nextAppt, mode: 'reschedule' }) },
                            { label: 'Cancel appointment', disabled: !canSchedule, onSelect: () => canSchedule && setApptDialog({ appt: nextAppt, mode: 'cancel' }) },
                          ]}
                        />
                      </div>
                      <div className="ap-next-big">{formatShort(nextAppt.date)} · {nextAppt.time}</div>
                      <div className="ap-next-type">{nextAppt.type}</div>
                      <div className="ap-next-footer">
                        <div>
                          <div className="ap-next-footer-label">Clinician</div>
                          <div>{nextAppt.clinician}</div>
                        </div>
                        <div>
                          <div className="ap-next-footer-label">Mode</div>
                          <div>{nextAppt.mode}</div>
                        </div>
                      </div>
                    </>
                  ) : (
                    <>
                      <span className="ap-next-label">No upcoming appointment</span>
                      <button className="ap-btn-secondary" disabled={!canSchedule} onClick={() => setScheduleOpen(true)}>Schedule appointment</button>
                    </>
                  )}
                </section>

                <section className="ap-card ap-ring-card">
                  {daysSinceAttended != null ? (
                    <>
                      <svg width="104" height="104" viewBox="0 0 104 104" className="ap-ring">
                        <circle cx="52" cy="52" r={ringRadius} fill="none" stroke="var(--border)" strokeWidth="8" />
                        <circle
                          cx="52" cy="52" r={ringRadius} fill="none" stroke="var(--accent)" strokeWidth="8"
                          strokeDasharray={`${ringCirc} ${ringCirc}`}
                          strokeDashoffset={ringCirc * (1 - ringFrac)}
                          strokeLinecap="round"
                          transform="rotate(-90 52 52)"
                        />
                        <text x="52" y="50" textAnchor="middle" fontSize="20" fontWeight={700} fill="var(--text)">{daysSinceAttended}</text>
                        <text x="52" y="66" textAnchor="middle" fontSize="10" fill="var(--text-3)">Days</text>
                      </svg>
                      <div className="ap-ring-info">
                        <div className="ap-ring-label">Last attended</div>
                        <div>{lastAtt ? formatDate(lastAtt.date) : ''}</div>
                        <button
                          className="ap-btn-secondary"
                          disabled={!canSchedule}
                          onClick={() => {
                            if (nextAppt) setApptDialog({ appt: nextAppt, mode: 'reschedule' });
                            else setScheduleOpen(true);
                          }}
                        >
                          Reschedule ↗
                        </button>
                      </div>
                    </>
                  ) : (
                    <>
                      <svg width="104" height="104" viewBox="0 0 104 104" className="ap-ring">
                        <circle cx="52" cy="52" r={ringRadius} fill="none" stroke="var(--border)" strokeWidth="8" />
                        <text x="52" y="58" textAnchor="middle" fontSize="20" fontWeight={700} fill="var(--text)">—</text>
                      </svg>
                      <div className="ap-ring-info">No attended appointment yet</div>
                    </>
                  )}
                  <div className="ap-ring-caption">Days since the last attended appointment. The ring fills over 30 days.</div>
                </section>
              </div>
            </div>

            <div className="ap-row-b">
              <section className="ap-card">
                <h3 className="ap-card-title">Appointment History</h3>
                {historyAppts.length === 0 ? (
                  <div className="ap-empty">No past appointments yet.</div>
                ) : (
                  <div className="ap-table-wrap">
                    <table className="ap-table">
                      <thead>
                        <tr>
                          <th>ID</th>
                          <th>Appointment</th>
                          <th>Date</th>
                          <th>Outcome</th>
                          <th>Documentation</th>
                          <th className="ap-sr-only">Actions</th>
                        </tr>
                      </thead>
                      <tbody>
                        {historyAppts.map((a) => {
                          const iconColor =
                            a.status === 'Attended' ? 'var(--safe-ink)' :
                            a.status === 'Missed' ? 'var(--critical-ink)' :
                            a.status === 'Cancelled' ? 'var(--text-3)' : 'var(--low-ink)';
                          return (
                            <tr key={a.id}>
                              <td>{a.id}</td>
                              <td>{a.type}</td>
                              <td>{formatDate(a.date)}</td>
                              <td title={a.outcome ?? ''}>
                                <span style={{ color: iconColor, marginRight: 4 }}>
                                  {a.status === 'Attended' ? '✓' : a.status === 'Missed' ? '✕' : a.status === 'Cancelled' ? '–' : '↻'}
                                </span>
                                <span className="ap-truncate">{a.outcome ?? a.status}</span>
                              </td>
                              <td><DocPill doc={a.documentation} /></td>
                              <td className="ap-table-actions">
                                <button
                                  className="ap-icon-btn"
                                  aria-haspopup="menu"
                                  aria-expanded={historyMenuFor === a.id}
                                  aria-label="Row actions"
                                  title="Row actions"
                                  onClick={() => setHistoryMenuFor(historyMenuFor === a.id ? null : a.id)}
                                >
                                  <IconKebab />
                                </button>
                                <Menu
                                  open={historyMenuFor === a.id}
                                  onClose={() => setHistoryMenuFor(null)}
                                  items={[
                                    { label: 'View details', onSelect: () => setApptDialog({ appt: a, mode: null }) },
                                    { label: 'Open patient profile', onSelect: () => onOpenProfile(a.hospitalId) },
                                    { label: 'View in audit log', onSelect: () => onOpenAudit() },
                                  ]}
                                />
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>

              <section className="ap-card">
                <h3 className="ap-card-title">Documents</h3>
                <div className="ap-doc-list">
                  <div className="ap-doc-row">
                    <IconFile />
                    <div className="ap-doc-body">
                      <div className="ap-doc-title">Goals-of-care summary</div>
                      <div className="ap-doc-sub">{versionLabel(selectedPatient.hospitalId)}</div>
                    </div>
                    <button className="ap-icon-btn" aria-label="Open" title="Open" onClick={() => onOpenRecords()}><IconOpenArrow /></button>
                  </div>
                  <div className="ap-doc-row">
                    <IconFile />
                    <div className="ap-doc-body">
                      <div className="ap-doc-title">Handoff print sheet</div>
                      <div className="ap-doc-sub">Open the summary to print it.</div>
                    </div>
                    <button className="ap-icon-btn" aria-label="Open" title="Open" disabled={!hasVerifiedRecord(selectedPatient.hospitalId)} onClick={() => onRetrieve(selectedPatient.hospitalId)}><IconOpenArrow /></button>
                  </div>
                  <div className="ap-doc-row">
                    <IconFile />
                    <div className="ap-doc-body">
                      <div className="ap-doc-title">Patient profile</div>
                      <div className="ap-doc-sub">Timeline, measures and version history</div>
                    </div>
                    <button className="ap-icon-btn" aria-label="Open" title="Open" onClick={() => onOpenProfile(selectedPatient.hospitalId)}><IconOpenArrow /></button>
                  </div>
                  <div className="ap-doc-row">
                    <IconFile />
                    <div className="ap-doc-body">
                      <div className="ap-doc-title">Appointment activity</div>
                      <div className="ap-doc-sub">Recorded in the audit log</div>
                    </div>
                    <button className="ap-icon-btn" aria-label="Open" title="Open" onClick={() => onOpenAudit()}><IconOpenArrow /></button>
                  </div>
                </div>
                <div className="ap-doc-footer-note">Choose a note to open it.</div>
              </section>
            </div>
          </>
        )}
      </main>

      {apptDialog && <AppointmentDialog
        key={`${apptDialog.appt.id}-${apptDialog.mode ?? "view"}`}
        open={!!apptDialog}
        onClose={() => setApptDialog(null)}
        appt={apptDialog?.appt ?? null}
        patient={selectedPatient}
        canSchedule={canSchedule}
        canStartConversation={canStartConversation}
        readOnlyReason={readOnlyReason}
        onSave={updateAppointment}
        onAudit={onAudit}
        onNotify={onNotify}
        onStartConversation={onStartConversation}
        onPatientActivity={onPatientActivity}
        appointments={appointments}
        defaultMode={apptDialog?.mode ?? null}
      />}
      {scheduleOpen && <ScheduleDialog
        open={scheduleOpen}
        onClose={() => setScheduleOpen(false)}
        patient={selectedPatient}
        appointments={appointments}
        canSchedule={canSchedule}
        readOnlyReason={readOnlyReason}
        onCreate={createAppointment}
        onAudit={onAudit}
        onNotify={onNotify}
        onPatientActivity={onPatientActivity}
      />}
    </div>
  );
}
