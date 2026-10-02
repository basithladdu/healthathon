'use client';

import { useCallback, useEffect, useId, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react';
import { CARE_CENTRES, type CareCentre } from './care-directory-data';
import { checkCareBill } from './care-bill-check';
import type { CareBillCheck } from './care-bill-match';
import {
  aggregateCentreReports,
  REPORT_CONSENT_TEXT,
  REPORT_FILE_MAX_BYTES,
  type CentreReportInput,
  type CentreReportRecord,
  type OralMorphineAvailability,
  type ReportOutcome,
  type ReportReporterRole,
  validateReportInput,
} from './care-centre-report-state';
import './care-centre-reports.css';

const REPORT_CENTRES = CARE_CENTRES.filter((centre) => centre.state === 'Karnataka');
const REPORT_DISTRICTS = ['Bagalkote', 'Ballari', 'Belagavi', 'Bengaluru Rural', 'Bengaluru Urban', 'Bengaluru South', 'Bidar', 'Chamarajanagar', 'Chikkaballapur', 'Chikkamagaluru', 'Chitradurga', 'Dakshina Kannada', 'Davanagere', 'Dharwad', 'Gadag', 'Hassan', 'Haveri', 'Kalaburagi', 'Kodagu', 'Kolar', 'Koppal', 'Mandya', 'Mysuru', 'Raichur', 'Ramanagara', 'Shivamogga', 'Tumakuru', 'Udupi', 'Uttara Kannada', 'Vijayapura', 'Vijayanagara', 'Yadgir'];

export type PublicCentreReport = Omit<CentreReportRecord, 'reporterId'> & {
  hospitalName?: string;
  latitude?: number | null;
  longitude?: number | null;
  receiptStatus?: 'attached' | 'not_attached' | string;
};

export type CareCentreReportsProps = {
  initialCentreId?: string;
  onReportsChanged?: (reports: readonly PublicCentreReport[]) => void;
};

type CentreMode = 'existing' | 'new';
type ReportRequestError = Error & { status?: number };
const REPORT_REQUEST_TIMEOUT_MS = 20_000;

type TimedRequest = {
  signal: AbortSignal;
  dispose: () => void;
};

function timedRequest(parent?: AbortSignal): TimedRequest {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REPORT_REQUEST_TIMEOUT_MS);
  const abort = () => controller.abort();
  parent?.addEventListener('abort', abort, { once: true });
  return {
    signal: controller.signal,
    dispose: () => {
      clearTimeout(timeout);
      parent?.removeEventListener('abort', abort);
    },
  };
}

type ReportDraft = {
  newCentreName: string;
  newCentreDistrict: string;
  newCentreAddress: string;
  newCentreLatitude: string;
  newCentreLongitude: string;
  reporterRole: ReportReporterRole;
  visitDate: string;
  outcome: ReportOutcome;
  oralMorphine: OralMorphineAvailability;
  consent: boolean;
};

function emptyDraft(): ReportDraft {
  return {
    newCentreName: '',
    newCentreDistrict: '',
    newCentreAddress: '',
    newCentreLatitude: '',
    newCentreLongitude: '',
    reporterRole: 'patient',
    visitDate: '',
    outcome: 'received',
    oralMorphine: 'unknown',
    consent: false,
  };
}

function todayValue(): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Kolkata',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date());
  const year = parts.find((part) => part.type === 'year')?.value;
  const month = parts.find((part) => part.type === 'month')?.value;
  const day = parts.find((part) => part.type === 'day')?.value;
  return year && month && day ? `${year}-${month}-${day}` : new Date().toISOString().slice(0, 10);
}

function clean(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function valueFrom(record: Record<string, unknown>, ...keys: string[]): unknown {
  for (const key of keys) {
    if (record[key] !== undefined && record[key] !== null) return record[key];
  }
  return undefined;
}

function stableNewCentreId(name: string, district: string, address: string): string {
  const source = `${name}|${district}|${address}`.toLocaleLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  return source ? `new-${source}` : 'new-unknown-centre';
}

function finiteCoordinate(value: unknown): number | null {
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value !== 'string' || !value.trim()) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function normalizeReport(value: unknown, fallbackCentreId = ''): PublicCentreReport | null {
  if (!value || typeof value !== 'object') return null;
  const record = value as Record<string, unknown>;
  const hospitalName = clean(valueFrom(record, 'hospitalName', 'centreName', 'name'));
  const district = clean(valueFrom(record, 'district', 'city'));
  const address = clean(valueFrom(record, 'address'));
  const centreId = clean(valueFrom(record, 'centreId', 'hospitalId'))
    || fallbackCentreId
    || stableNewCentreId(hospitalName, district, address);
  const visitDate = clean(valueFrom(record, 'visitDate', 'visit_date'));
  const outcome = clean(valueFrom(record, 'outcome'));
  if (!centreId || !hospitalName || !visitDate || !['received', 'unavailable'].includes(outcome)) return null;
  const reporterRole = clean(valueFrom(record, 'reporterRole', 'reporter_role')) || undefined;
  const doctorStatus = clean(valueFrom(record, 'doctorStatus', 'doctor_status')) || undefined;
  const oralMorphine = clean(valueFrom(record, 'oralMorphine', 'oral_morphine', 'morphine')) || 'unknown';
  const latitude = finiteCoordinate(valueFrom(record, 'latitude', 'lat'));
  const longitude = finiteCoordinate(valueFrom(record, 'longitude', 'lon', 'lng'));
  return {
    id: clean(valueFrom(record, 'id')) || undefined,
    centreId,
    centreName: hospitalName,
    hospitalName,
    district: district || undefined,
    address: address || undefined,
    visitDate,
    reporterRole,
    doctorStatus,
    outcome,
    oralMorphine,
    latitude,
    longitude,
    createdAt: clean(valueFrom(record, 'createdAt', 'created_at')) || undefined,
    receiptStatus: clean(valueFrom(record, 'receiptStatus', 'receipt_status')) || undefined,
  };
}

type PublicReportsPayload = {
  reports: PublicCentreReport[];
  ownedIds: string[];
  truncated: boolean;
};

function reportsFromPayload(payload: unknown, fallbackCentreId = ''): PublicReportsPayload {
  const values = Array.isArray(payload)
    ? payload
    : payload && typeof payload === 'object' && Array.isArray((payload as { reports?: unknown }).reports)
      ? (payload as { reports: unknown[] }).reports
      : [];
  const reports = values.flatMap((value) => {
    const report = normalizeReport(value, fallbackCentreId);
    return report ? [report] : [];
  });
  const ownedIds = payload && typeof payload === 'object' && Array.isArray((payload as { ownedIds?: unknown }).ownedIds)
    ? (payload as { ownedIds: unknown[] }).ownedIds.filter((value): value is string => typeof value === 'string' && Boolean(value.trim()))
    : [];
  const truncated = Boolean(payload && typeof payload === 'object' && (payload as { truncated?: unknown }).truncated);
  return { reports, ownedIds, truncated };
}

function requestError(status: number | undefined, action: 'load' | 'submit' | 'withdraw'): ReportRequestError {
  const error = new Error(
    status === 503
      ? `Reports service unavailable. ${action === 'submit' ? 'Your report was not saved. ' : action === 'withdraw' ? 'Your report was not withdrawn. ' : ''}Try again.`
      : action === 'submit'
        ? 'Your report was not saved. Try again.'
        : action === 'withdraw'
          ? 'This report could not be withdrawn. Try again.'
        : 'Community reports could not load. Try again.',
  ) as ReportRequestError;
  error.status = status;
  return error;
}

function roleLabel(role: string | undefined): string {
  if (role === 'caregiver') return 'Caregiver';
  if (role === 'doctor') return 'Doctor (self-reported)';
  return 'Patient';
}

function dateLabel(value: string | null | undefined): string {
  if (!value) return 'No visit date yet';
  const parsed = new Date(`${value}T12:00:00Z`);
  return Number.isFinite(parsed.getTime()) ? parsed.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }) : value;
}

function outcomeLabel(value: string): string {
  return value === 'received' ? 'Received' : 'Unavailable';
}

function oralMorphineLabel(value: string | undefined): string {
  if (value === 'available') return 'Oral morphine · available';
  if (value === 'unavailable') return 'Oral morphine · unavailable';
  return 'Oral morphine · not reported';
}

function statusLabel(status: ReturnType<typeof aggregateCentreReports>['status']): string {
  if (status === 'available') return 'Recent reports say oral morphine was available';
  if (status === 'negative') return 'Recent reports say oral morphine was unavailable';
  if (status === 'mixed') return 'Recent reports are mixed';
  if (status === 'outdated') return 'The latest report is older than 90 days';
  if (status === 'unknown') return 'Recent reports did not record oral morphine';
  return 'No community reports yet';
}

function statusTone(status: ReturnType<typeof aggregateCentreReports>['status']): string {
  if (status === 'available') return 'is-available';
  if (status === 'negative') return 'is-negative';
  if (status === 'mixed') return 'is-mixed';
  if (status === 'outdated') return 'is-outdated';
  return 'is-unknown';
}

function visibleError(errors: Record<string, string>, key: string): string | undefined {
  return errors[key];
}

function CentreCommunityCard({
  centreName,
  reports,
  ownedIds,
  onReportHere,
  onWithdraw,
  withdrawId,
  withdrawError,
}: {
  centreName: string;
  reports: PublicCentreReport[];
  ownedIds: ReadonlySet<string>;
  onReportHere: () => void;
  onWithdraw: (id: string) => void;
  withdrawId: string;
  withdrawError: string;
}) {
  const summary = aggregateCentreReports(reports, new Date());
  return <article className="ccr-community-card">
    <div className="ccr-card-heading">
      <div><p className="ccr-card-kicker">Community reports</p><h3>{centreName}</h3></div>
      <button type="button" className="ccr-report-here" onClick={onReportHere}>I also got it here</button>
    </div>
    <div className="ccr-summary-line"><strong>{summary.recentCount}</strong><span>recent reports</span>{summary.lastDate && <span>· Last visit {dateLabel(summary.lastDate)}</span>}</div>
    <p className={`ccr-status ${statusTone(summary.status)}`} role="status">{statusLabel(summary.status)}</p>
    {reports.length > 0 && <ul className="ccr-report-list">
      {[...reports].sort((a, b) => b.visitDate.localeCompare(a.visitDate)).map((report) => <li key={report.id ?? `${report.centreId}-${report.visitDate}-${report.createdAt ?? report.outcome}`}>
        <div><time dateTime={report.visitDate}>{dateLabel(report.visitDate)}</time><span>{roleLabel(report.reporterRole)}</span></div>
        <span>{outcomeLabel(report.outcome)} · {oralMorphineLabel(report.oralMorphine)}{report.id && ownedIds.has(report.id) && <button type="button" className="ccr-withdraw" onClick={() => onWithdraw(report.id!)} disabled={withdrawId === report.id}>{withdrawId === report.id ? 'Withdrawing…' : 'Withdraw'}</button>}</span>
      </li>)}
    </ul>}
    {withdrawError && <p className="ccr-inline-error" role="alert">{withdrawError}</p>}
  </article>;
}

export function CareCentreReports({ initialCentreId, onReportsChanged }: CareCentreReportsProps = {}) {
  const id = useId();
  const formRef = useRef<HTMLFormElement>(null);
  const firstCentre = initialCentreId ? REPORT_CENTRES.find((centre) => centre.id === initialCentreId) : undefined;
  const [centreMode, setCentreMode] = useState<CentreMode>('existing');
  const [formOpen, setFormOpen] = useState(Boolean(firstCentre));
  const [selectedCentreId, setSelectedCentreId] = useState(firstCentre?.id ?? '');
  const [communityCentreId, setCommunityCentreId] = useState('');
  const [draft, setDraft] = useState<ReportDraft>(() => emptyDraft());
  const [bill, setBill] = useState<File | null>(null);
  const [billCheck, setBillCheck] = useState<CareBillCheck | null>(null);
  const [billCheckBusy, setBillCheckBusy] = useState(false);
  const [billCheckError, setBillCheckError] = useState('');
  const billCheckController = useRef<AbortController | null>(null);
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const [reports, setReports] = useState<PublicCentreReport[]>([]);
  const [ownedIds, setOwnedIds] = useState<ReadonlySet<string>>(() => new Set());
  const [reportsTruncated, setReportsTruncated] = useState(false);
  const [reportStatus, setReportStatus] = useState<'loading' | 'ready' | 'error'>('loading');
  const [reportError, setReportError] = useState('');
  const [submissionStatus, setSubmissionStatus] = useState<'idle' | 'submitting' | 'saved' | 'error'>('idle');
  const [submissionError, setSubmissionError] = useState('');
  const [withdrawId, setWithdrawId] = useState('');
  const [withdrawError, setWithdrawError] = useState('');
  const [retryKey, setRetryKey] = useState(0);
  const submitController = useRef<AbortController | null>(null);

  const selectedCentre = useMemo<CareCentre | undefined>(() => CARE_CENTRES.find((centre) => centre.id === selectedCentreId), [selectedCentreId]);
  const selectedReports = useMemo(() => selectedCentreId ? reports.filter((report) => report.centreId === selectedCentreId) : reports, [reports, selectedCentreId]);
  const existingGroups = useMemo(() => {
    const groups = new Map<string, { name: string; reports: PublicCentreReport[] }>();
    for (const report of reports) {
      const group = groups.get(report.centreId) ?? { name: report.hospitalName ?? report.centreName ?? report.centreId, reports: [] };
      group.reports.push(report);
      groups.set(report.centreId, group);
    }
    return [...groups.values()];
  }, [reports]);

  const clearBillCheck = () => {
    billCheckController.current?.abort();
    setBillCheckBusy(false);
    setBillCheck(null);
    setBillCheckError('');
  };

  const refreshReports = useCallback(async (signal?: AbortSignal): Promise<PublicCentreReport[]> => {
    const query = selectedCentreId ? `?centreId=${encodeURIComponent(selectedCentreId)}` : '';
    const request = timedRequest(signal);
    let response: Response;
    let payload: unknown;
    try {
      response = await fetch(`/api/centre-reports${query}`, { headers: { Accept: 'application/json' }, signal: request.signal });
      if (signal?.aborted) return [];
      if (!response.ok) throw requestError(response.status, 'load');
      payload = await response.json();
      if (signal?.aborted || request.signal.aborted) return [];
      const nextPayload = reportsFromPayload(payload, selectedCentreId);
      setReports(nextPayload.reports);
      setOwnedIds(new Set(nextPayload.ownedIds));
      setReportsTruncated(nextPayload.truncated);
      setReportStatus('ready');
      setReportError('');
      onReportsChanged?.(nextPayload.reports);
      return nextPayload.reports;
    } catch (error) {
      if (signal?.aborted) return [];
      if ((error as ReportRequestError).status !== undefined) throw error;
      throw requestError(undefined, 'load');
    } finally {
      request.dispose();
    }
  }, [onReportsChanged, selectedCentreId]);

  const withdrawReport = async (reportId: string) => {
    if (!reportId) return;
    setWithdrawId(reportId);
    setWithdrawError('');
    const parent = new AbortController();
    const request = timedRequest(parent.signal);
    try {
      const response = await fetch(`/api/centre-reports?id=${encodeURIComponent(reportId)}`, {
        method: 'DELETE',
        headers: { Accept: 'application/json' },
        signal: request.signal,
      });
      if (parent.signal.aborted || request.signal.aborted) return;
      if (!response.ok) throw requestError(response.status, 'withdraw');
      const next = await refreshReports(parent.signal);
      if (parent.signal.aborted) return;
      if (!next) throw requestError(undefined, 'load');
    } catch (error) {
      if (parent.signal.aborted) return;
      setWithdrawError((error as Error).message || 'This report could not be withdrawn. Try again.');
    } finally {
      request.dispose();
      setWithdrawId('');
    }
  };

  useEffect(() => {
    const controller = new AbortController();
    void Promise.resolve().then(() => refreshReports(controller.signal)).catch((error: ReportRequestError) => {
      if (controller.signal.aborted) return;
      setReportStatus('error');
      setReportError(error.message || 'Community reports could not load. Try again.');
    });
    return () => controller.abort();
  }, [refreshReports, retryKey]);

  useEffect(() => () => {
    submitController.current?.abort();
    billCheckController.current?.abort();
  }, []);

  const inputForValidation = (): CentreReportInput => {
    const isNewCentre = centreMode === 'new';
    return {
      isNewCentre,
      centreId: isNewCentre ? '' : selectedCentreId,
      centreName: selectedCentre?.name,
      district: selectedCentre?.city,
      address: selectedCentre?.address,
      state: isNewCentre ? 'Karnataka' : selectedCentre?.state,
      newCentreName: draft.newCentreName,
      newCentreDistrict: draft.newCentreDistrict,
      newCentreAddress: draft.newCentreAddress,
      newCentreLatitude: draft.newCentreLatitude,
      newCentreLongitude: draft.newCentreLongitude,
      reporterRole: draft.reporterRole,
      doctorStatus: draft.reporterRole === 'doctor' ? 'self-reported' : undefined,
      visitDate: draft.visitDate,
      outcome: draft.outcome,
      oralMorphine: draft.oralMorphine,
      consent: draft.consent,
      bill,
    };
  };

  const updateDraft = <K extends keyof ReportDraft>(key: K, value: ReportDraft[K]) => {
    setDraft((current) => ({ ...current, [key]: value }));
    if (['newCentreName', 'newCentreDistrict', 'newCentreAddress'].includes(key as string)) setCommunityCentreId('');
    if (['newCentreName', 'newCentreDistrict', 'newCentreAddress', 'visitDate'].includes(key as string)) clearBillCheck();
    setValidationErrors((current) => {
      if (!current[key as string]) return current;
      const next = { ...current };
      delete next[key as string];
      return next;
    });
  };

  const openReportForm = (centreId?: string, report?: PublicCentreReport) => {
    setFormOpen(true);
    if (centreId) {
      const directoryCentre = REPORT_CENTRES.find((centre) => centre.id === centreId)
        ?? (report?.hospitalName ? REPORT_CENTRES.find((centre) => centre.name === report.hospitalName) : undefined);
      if (directoryCentre) {
        setCentreMode('existing');
        setSelectedCentreId(directoryCentre.id);
        setCommunityCentreId('');
        clearBillCheck();
      } else if (report) {
        setCentreMode('new');
        setSelectedCentreId('');
        setCommunityCentreId(report.centreId.startsWith('community-') ? report.centreId : '');
        clearBillCheck();
        setDraft((current) => ({
          ...current,
          newCentreName: report.hospitalName ?? report.centreName ?? '',
          newCentreDistrict: report.district ?? '',
          newCentreAddress: report.address ?? '',
        }));
      }
    }
    setSubmissionStatus('idle');
    setSubmissionError('');
    formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const chooseBill = (event: ChangeEvent<HTMLInputElement>) => {
    const next = event.target.files?.[0] ?? null;
    setBill(next);
    setBillCheckBusy(false);
    billCheckController.current?.abort();
    setBillCheck(null);
    setBillCheckError('');
    setValidationErrors((current) => {
      const updated = { ...current };
      delete updated.bill;
      return updated;
    });
    if (next && (next.size <= 0 || next.size > REPORT_FILE_MAX_BYTES || !['application/pdf', 'image/jpeg', 'image/png', 'image/webp'].includes(next.type))) {
      setValidationErrors((current) => ({ ...current, bill: 'Bill must be a PDF or image up to 4 MB.' }));
    }
  };

  const readBill = async () => {
    if (!bill) return;
    const hospitalName = centreMode === 'new' ? draft.newCentreName.trim() : selectedCentre?.name ?? '';
    const controller = new AbortController();
    billCheckController.current?.abort();
    billCheckController.current = controller;
    setBillCheckBusy(true);
    setBillCheckError('');
    try {
      const result = await checkCareBill(bill, hospitalName, draft.visitDate, controller.signal);
      if (!controller.signal.aborted) setBillCheck(result);
    } catch (error) {
      if (!controller.signal.aborted) setBillCheckError((error as Error).message || 'Could not read this bill.');
    } finally {
      if (!controller.signal.aborted) setBillCheckBusy(false);
    }
  };

  const submitReport = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setSubmissionError('');
    const input = inputForValidation();
    const validation = validateReportInput(input, new Date());
    setValidationErrors(validation.errors);
    if (!validation.valid) {
      setSubmissionStatus('error');
      return;
    }

    setSubmissionStatus('submitting');
    submitController.current?.abort();
    const submitParent = new AbortController();
    submitController.current = submitParent;
    const submitRequest = timedRequest(submitParent.signal);
    const isNewCentre = centreMode === 'new';
    const hospitalName = isNewCentre ? draft.newCentreName.trim() : selectedCentre?.name ?? '';
    const district = isNewCentre ? draft.newCentreDistrict.trim() : selectedCentre?.city ?? '';
    const address = isNewCentre ? draft.newCentreAddress.trim() : selectedCentre?.address ?? '';
    const formData = new FormData();
    const resolvedCommunityCentreId = isNewCentre ? communityCentreId : selectedCentreId;
    if (resolvedCommunityCentreId) formData.append('centreId', resolvedCommunityCentreId);
    formData.append('hospitalName', hospitalName);
    formData.append('centreName', hospitalName);
    formData.append('district', district);
    formData.append('address', address);
    const latitude = isNewCentre ? draft.newCentreLatitude.trim() : selectedCentre?.coordinates ? String(selectedCentre.coordinates[1]) : '';
    const longitude = isNewCentre ? draft.newCentreLongitude.trim() : selectedCentre?.coordinates ? String(selectedCentre.coordinates[0]) : '';
    if (latitude) formData.append('latitude', latitude);
    if (longitude) formData.append('longitude', longitude);
    formData.append('visitDate', draft.visitDate);
    formData.append('reporterRole', draft.reporterRole);
    if (draft.reporterRole === 'doctor') {
      formData.append('doctorStatus', 'self-reported');
    }
    formData.append('outcome', draft.outcome);
    formData.append('oralMorphine', draft.oralMorphine);
    formData.append('consent', 'true');
    if (bill) formData.append('bill', bill, bill.name);

    try {
      let response: Response;
      try {
        response = await fetch('/api/centre-reports', { method: 'POST', body: formData, headers: { Accept: 'application/json' }, signal: submitRequest.signal });
      } catch {
        if (submitParent.signal.aborted || submitRequest.signal.aborted) throw requestError(undefined, 'submit');
        throw requestError(undefined, 'submit');
      }
      if (submitParent.signal.aborted || submitRequest.signal.aborted) throw requestError(undefined, 'submit');
      if (!response.ok) throw requestError(response.status, 'submit');
      try {
        await response.json();
      } catch {
        throw requestError(response.status, 'submit');
      }
      if (submitParent.signal.aborted || submitRequest.signal.aborted) throw requestError(undefined, 'submit');

      // Only confirm a save after the server accepts the report.
      try {
        await refreshReports(submitParent.signal);
      } catch (error) {
        if (submitParent.signal.aborted) throw requestError(undefined, 'submit');
        setReportStatus('error');
        setReportError((error as Error).message || 'Community reports could not load. Try again.');
      }
      if (submitParent.signal.aborted || submitRequest.signal.aborted) throw requestError(undefined, 'submit');
      setSubmissionStatus('saved');
      setValidationErrors({});
      setBill(null);
      setBillCheck(null);
      setBillCheckError('');
      setCommunityCentreId('');
      setDraft((current) => ({ ...emptyDraft(), reporterRole: current.reporterRole }));
    } catch (error) {
      if (submitParent.signal.aborted) return;
      setSubmissionStatus('error');
      setSubmissionError((error as Error).message || 'Your report was not saved. Try again.');
    } finally {
      submitRequest.dispose();
      if (submitController.current === submitParent) submitController.current = null;
    }
  };

  const noReports = reportStatus === 'ready' && selectedReports.length === 0;
  const billHospitalName = centreMode === 'new' ? draft.newCentreName.trim() : selectedCentre?.name ?? '';

  return <section className="ccr-root" aria-labelledby={`${id}-title`}>
    <header className="ccr-heading">
      <div><h2 id={`${id}-title`}>Hospital reports</h2></div>
      {selectedCentre && <span className="ccr-centre-chip">{selectedCentre.name}</span>}
    </header>

    <div className="ccr-layout">
      <form className="ccr-form" ref={formRef} onSubmit={submitReport} noValidate>
        <fieldset className="ccr-form-body" disabled={submissionStatus === 'submitting'}>
        {!formOpen ? <button type="button" className="ccr-open-form" onClick={() => openReportForm()}>Share a visit</button> : <>
        <div className="ccr-form-heading"><h2>Share a visit</h2><span>Reports are not independently verified.</span></div>
        <div className="ccr-mode-tabs" role="group" aria-label="Hospital choice">
          <button type="button" aria-pressed={centreMode === 'existing'} onClick={() => { setCentreMode('existing'); setCommunityCentreId(''); clearBillCheck(); setReportError(''); setValidationErrors({}); }}>Existing centre</button>
          <button type="button" aria-pressed={centreMode === 'new'} onClick={() => { setCentreMode('new'); setSelectedCentreId(''); setCommunityCentreId(''); clearBillCheck(); setReportError(''); setValidationErrors({}); }}>New Karnataka hospital</button>
        </div>

        {centreMode === 'existing' ? <label className="ccr-field"><span>Hospital or centre</span><select value={selectedCentreId} onChange={(event) => { setSelectedCentreId(event.target.value); setCommunityCentreId(''); clearBillCheck(); setReportStatus('loading'); setReportError(''); setValidationErrors({}); }} aria-invalid={Boolean(visibleError(validationErrors, 'centre'))}>
          <option value="">Choose an existing centre</option>
          {REPORT_CENTRES.map((centre) => <option key={centre.id} value={centre.id}>{centre.name} · {centre.city}</option>)}
        </select>{visibleError(validationErrors, 'centre') && <em>{validationErrors.centre}</em>}</label> : <div className="ccr-new-centre-fields">
          <label className="ccr-field"><span>Hospital name</span><input value={draft.newCentreName} onChange={(event) => updateDraft('newCentreName', event.target.value)} autoComplete="organization" aria-invalid={Boolean(validationErrors.name)} />{validationErrors.name && <em>{validationErrors.name}</em>}</label>
          <label className="ccr-field"><span>District</span><select value={draft.newCentreDistrict} onChange={(event) => updateDraft('newCentreDistrict', event.target.value)} aria-invalid={Boolean(validationErrors.district)}><option value="">Choose a district</option>{REPORT_DISTRICTS.map((district) => <option key={district}>{district}</option>)}</select>{validationErrors.district && <em>{validationErrors.district}</em>}</label>
          <label className="ccr-field ccr-field-wide"><span>Address</span><textarea value={draft.newCentreAddress} onChange={(event) => updateDraft('newCentreAddress', event.target.value)} rows={2} autoComplete="street-address" aria-invalid={Boolean(validationErrors.address)} />{validationErrors.address && <em>{validationErrors.address}</em>}</label>
          <fieldset className="ccr-coordinates"><legend>Coordinates <small>optional · only if known</small></legend><div><label><span>Latitude</span><input inputMode="decimal" value={draft.newCentreLatitude} onChange={(event) => updateDraft('newCentreLatitude', event.target.value)} /></label><label><span>Longitude</span><input inputMode="decimal" value={draft.newCentreLongitude} onChange={(event) => updateDraft('newCentreLongitude', event.target.value)} /></label></div>{validationErrors.coordinates && <em>{validationErrors.coordinates}</em>}</fieldset>
        </div>}

        <div className="ccr-two-fields">
          <label className="ccr-field"><span>Visit date</span><input type="date" value={draft.visitDate} max={todayValue()} onChange={(event) => updateDraft('visitDate', event.target.value)} aria-invalid={Boolean(validationErrors.visitDate)} />{validationErrors.visitDate && <em>{validationErrors.visitDate}</em>}</label>
          <fieldset className="ccr-choice-field"><legend>Who are you?</legend><div className="ccr-choice-row">{([['patient', 'Patient'], ['caregiver', 'Caregiver'], ['doctor', 'Doctor (self-reported)']] as const).map(([value, label]) => <label key={value}><input type="radio" name={`${id}-role`} value={value} checked={draft.reporterRole === value} onChange={() => updateDraft('reporterRole', value)} /><span>{label}</span></label>)}</div>{validationErrors.reporterRole && <em>{validationErrors.reporterRole}</em>}</fieldset>
        </div>

        <fieldset className="ccr-choice-field"><legend>Visit outcome</legend><div className="ccr-choice-row">{([['received', 'Received care'], ['unavailable', 'Care unavailable']] as const).map(([value, label]) => <label key={value}><input type="radio" name={`${id}-outcome`} value={value} checked={draft.outcome === value} onChange={() => updateDraft('outcome', value)} /><span>{label}</span></label>)}</div>{validationErrors.outcome && <em>{validationErrors.outcome}</em>}</fieldset>
        <label className="ccr-field"><span>Oral morphine</span><select value={draft.oralMorphine} onChange={(event) => updateDraft('oralMorphine', event.target.value as OralMorphineAvailability)} aria-invalid={Boolean(validationErrors.oralMorphine)}><option value="unknown">Did not ask / not sure</option><option value="available">Available</option><option value="unavailable">Unavailable</option></select>{validationErrors.oralMorphine && <em>{validationErrors.oralMorphine}</em>}</label>

        <div className="ccr-bill-field"><label className="ccr-field"><span>Bill image or PDF <small>optional · private · max 4 MB</small></span><input type="file" accept="application/pdf,image/jpeg,image/png,image/webp" onChange={chooseBill} aria-invalid={Boolean(validationErrors.bill)} />{bill && !validationErrors.bill && <small className="ccr-file-name">{bill.name}</small>}{validationErrors.bill && <em>{validationErrors.bill}</em>}</label>{bill && !validationErrors.bill && <div className="ccr-bill-check"><button type="button" onClick={readBill} disabled={billCheckBusy || !draft.visitDate || !billHospitalName}>{billCheckBusy ? 'Reading…' : 'Read bill'}</button>{billCheck && <p className={`ccr-bill-result is-${billCheck.status}`} role="status"><strong>{billCheck.status === 'matched' ? 'Bill text matches' : billCheck.status === 'needs-review' ? 'Needs checking' : 'Could not read'}</strong><span>{billCheck.message}</span></p>}{billCheckError && <p className="ccr-inline-error" role="alert">{billCheckError}</p>}</div>}</div>
        <label className="ccr-consent"><input type="checkbox" checked={draft.consent} onChange={(event) => updateDraft('consent', event.target.checked)} aria-invalid={Boolean(validationErrors.consent)} /><span>{REPORT_CONSENT_TEXT}</span></label>
        {validationErrors.consent && <p className="ccr-inline-error">{validationErrors.consent}</p>}
        <p className="ccr-repeat-note">Repeat submissions for the same hospital and visit date are counted once by the service.</p>
        {submissionStatus === 'error' && submissionError && <p className="ccr-form-error" role="alert">{submissionError}</p>}
        {submissionStatus === 'saved' && <div className="ccr-pending" role="status"><strong>Report shared</strong></div>}
        <button type="submit" className="ccr-submit" disabled={submissionStatus === 'submitting'}>{submissionStatus === 'submitting' ? 'Sending…' : 'Share report'}</button>
        </>}
        </fieldset>
      </form>

      <section className="ccr-community" aria-labelledby={`${id}-community-title`}>
        <div className="ccr-community-heading"><h2 id={`${id}-community-title`}>What people reported</h2>{selectedCentre && <button type="button" className="ccr-small-action" onClick={() => openReportForm(selectedCentre.id)}>Report here</button>}</div>
        {reportStatus === 'loading' && <p className="ccr-message" role="status">Loading community reports…</p>}
        {reportStatus === 'error' && <div className="ccr-message ccr-message-error" role="alert"><p>{reportError}</p><button type="button" onClick={() => { setReportStatus('loading'); setReportError(''); setRetryKey((value) => value + 1); }}>Try again</button></div>}
        {reportStatus === 'ready' && reportsTruncated && <p className="ccr-truncated">Some older community reports are not shown.</p>}
        {reportStatus === 'ready' && selectedCentre && selectedReports.length > 0 && <CentreCommunityCard centreName={selectedCentre.name} reports={selectedReports} ownedIds={ownedIds} onReportHere={() => openReportForm(selectedCentre.id)} onWithdraw={withdrawReport} withdrawId={withdrawId} withdrawError={withdrawError} />}
        {reportStatus === 'ready' && !selectedCentre && noReports && <div className="ccr-empty"><p>No community reports yet.</p><button type="button" onClick={() => openReportForm()}>Share the first report</button></div>}
        {reportStatus === 'ready' && !selectedCentre && !noReports && <div className="ccr-community-list">{existingGroups.map((group) => <CentreCommunityCard key={group.reports[0]?.centreId ?? group.name} centreName={group.name} reports={group.reports} ownedIds={ownedIds} onReportHere={() => openReportForm(group.reports[0]?.centreId, group.reports[0])} onWithdraw={withdrawReport} withdrawId={withdrawId} withdrawError={withdrawError} />)}</div>}
        {reportStatus === 'ready' && selectedCentre && selectedReports.length === 0 && <div className="ccr-empty"><p>No community reports for this centre yet.</p><button type="button" onClick={() => openReportForm(selectedCentre.id)}>Share the first report</button></div>}
      </section>
    </div>
  </section>;
}
