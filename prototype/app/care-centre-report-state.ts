import { isValidAppointmentDate } from './appointment-state.ts';

export const REPORT_CONSENT_TEXT = 'Share the hospital, visit date and outcome. My bill stays private.';
export const REPORT_RECENT_WINDOW_DAYS = 90;
export const REPORT_FILE_MAX_BYTES = 4 * 1024 * 1024;
export const REPORT_FILE_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'] as const;

export type ReportReporterRole = 'patient' | 'caregiver' | 'doctor';
export type ReportDoctorStatus = 'self-reported' | 'never-verified';
export type ReportOutcome = 'received' | 'unavailable';
export type OralMorphineAvailability = 'available' | 'unavailable' | 'unknown';

/**
 * This is the client-side shape used by the form and by the public API adapter.
 * A new centre has no id yet, so the same name/address fields are also used for
 * an existing centre's display data. The API decides whether a new centre is
 * created; the client never invents a centre id or coordinates.
 */
export type CentreReportInput = {
  centreId?: string | null;
  centreName?: string;
  district?: string;
  address?: string;
  phone?: string;
  latitude?: string | number | null;
  longitude?: string | number | null;
  isNewCentre?: boolean;
  state?: 'Karnataka' | string;
  newCentreName?: string;
  newCentreDistrict?: string;
  newCentreAddress?: string;
  newCentrePhone?: string;
  newCentreLatitude?: string | number | null;
  newCentreLongitude?: string | number | null;
  reporterRole: ReportReporterRole | string;
  doctorStatus?: ReportDoctorStatus | string;
  visitDate: string;
  outcome: ReportOutcome | string;
  oralMorphine: OralMorphineAvailability | string;
  consent: boolean;
  bill?: Pick<File, 'type' | 'size'> | null;
};

export type CentreReportValidationErrors = Partial<Record<
  | 'centre'
  | 'name'
  | 'district'
  | 'address'
  | 'coordinates'
  | 'reporterRole'
  | 'doctorStatus'
  | 'visitDate'
  | 'outcome'
  | 'oralMorphine'
  | 'consent'
  | 'bill',
  string
>>;

export type CentreReportValidation = {
  valid: boolean;
  errors: CentreReportValidationErrors;
};

export type CentreReportRecord = {
  id?: string;
  centreId: string;
  centreName?: string;
  district?: string;
  address?: string;
  latitude?: number | null;
  longitude?: number | null;
  visitDate: string;
  reporterRole?: ReportReporterRole | string;
  doctorStatus?: ReportDoctorStatus | string;
  outcome: ReportOutcome | string;
  oralMorphine?: OralMorphineAvailability | string;
  createdAt?: string;
  /** Private server-side field. Public API rows must omit this. */
  reporterId?: string | null;
};

export type ReportStatus = 'available' | 'negative' | 'mixed' | 'unknown' | 'outdated' | 'empty';

export type CentreReportSummary = {
  totalCount: number;
  reportCount: number;
  recentCount: number;
  recentReportCount: number;
  distinctRecentReports: number;
  lastDate: string | null;
  latestVisitDate: string | null;
  status: ReportStatus;
  morphineStatus: ReportStatus;
  outcomeStatus: ReportStatus;
  hasConflictingReports: boolean;
  outcomes: { received: number; unavailable: number };
  oralMorphine: { available: number; unavailable: number; unknown: number };
};

type NormalizedCentre = {
  isNew: boolean;
  id: string;
  name: string;
  district: string;
  address: string;
  phone: string;
  latitude: string;
  longitude: string;
};

function text(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizedCentre(input: CentreReportInput): NormalizedCentre {
  const isNew = input.isNewCentre ?? !text(input.centreId);
  return {
    isNew,
    id: text(input.centreId),
    name: text(isNew ? input.newCentreName ?? input.centreName : input.centreName),
    district: text(isNew ? input.newCentreDistrict ?? input.district : input.district),
    address: text(isNew ? input.newCentreAddress ?? input.address : input.address),
    phone: text(isNew ? input.newCentrePhone ?? input.phone : input.phone),
    latitude: String(isNew ? input.newCentreLatitude ?? input.latitude ?? '' : input.latitude ?? '').trim(),
    longitude: String(isNew ? input.newCentreLongitude ?? input.longitude ?? '' : input.longitude ?? '').trim(),
  };
}

function dateOnly(value: Date | string): string {
  if (value instanceof Date) {
    if (!Number.isFinite(value.getTime())) return '';
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Asia/Kolkata',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(value);
    const year = parts.find((part) => part.type === 'year')?.value;
    const month = parts.find((part) => part.type === 'month')?.value;
    const day = parts.find((part) => part.type === 'day')?.value;
    return year && month && day ? `${year}-${month}-${day}` : '';
  }
  const candidate = text(value);
  const day = candidate.slice(0, 10);
  return isValidAppointmentDate(day) ? day : '';
}

function dateDistanceInDays(later: string, earlier: string): number {
  const laterMs = Date.parse(`${later}T00:00:00Z`);
  const earlierMs = Date.parse(`${earlier}T00:00:00Z`);
  return Math.round((laterMs - earlierMs) / 86_400_000);
}

function numericCoordinate(value: string): number | null {
  if (!value) return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function validCoordinatePair(latitude: string, longitude: string): boolean {
  const hasLatitude = Boolean(latitude);
  const hasLongitude = Boolean(longitude);
  if (!hasLatitude && !hasLongitude) return true;
  if (!hasLatitude || !hasLongitude) return false;
  const lat = numericCoordinate(latitude);
  const lon = numericCoordinate(longitude);
  return lat !== null && lon !== null && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
}

function validBill(file: Pick<File, 'type' | 'size'>): boolean {
  return REPORT_FILE_TYPES.includes(file.type as (typeof REPORT_FILE_TYPES)[number])
    && file.size > 0 && file.size <= REPORT_FILE_MAX_BYTES;
}

export function validateReportInput(input: CentreReportInput, now: Date | string = new Date()): CentreReportValidation {
  const errors: CentreReportValidationErrors = {};
  const centre = normalizedCentre(input);
  const today = dateOnly(now);

  if (centre.isNew) {
    if (text(input.state) && text(input.state) !== 'Karnataka') errors.centre = 'New hospitals must be in Karnataka.';
    if (!centre.name) errors.name = 'Hospital name is required.';
    if (!centre.district) errors.district = 'District is required.';
    if (!centre.address) errors.address = 'Address is required.';
  } else if (!centre.id) {
    errors.centre = 'Choose an existing centre or add a Karnataka hospital.';
  }

  if (!validCoordinatePair(centre.latitude, centre.longitude)) {
    errors.coordinates = 'Enter both coordinates as valid latitude and longitude, or leave both blank.';
  } else if (centre.isNew && centre.latitude && centre.longitude) {
    const latitude = numericCoordinate(centre.latitude);
    const longitude = numericCoordinate(centre.longitude);
    if (latitude === null || longitude === null || latitude < 11 || latitude > 19 || longitude < 74 || longitude > 79) {
      errors.coordinates = 'Use coordinates within Karnataka, or leave both blank.';
    }
  }
  if (!['patient', 'caregiver', 'doctor'].includes(text(input.reporterRole))) {
    errors.reporterRole = 'Choose patient, caregiver or doctor.';
  }
  if (text(input.reporterRole) === 'doctor' && text(input.doctorStatus) !== 'self-reported') {
    errors.doctorStatus = 'Doctor reports are self-reported and are not verified.';
  }
  if (!isValidAppointmentDate(text(input.visitDate))) {
    errors.visitDate = 'Enter a valid visit date.';
  } else if (today && text(input.visitDate) > today) {
    errors.visitDate = 'Visit date cannot be in the future.';
  }
  if (!['received', 'unavailable'].includes(text(input.outcome))) {
    errors.outcome = 'Choose received or unavailable.';
  }
  if (!['available', 'unavailable', 'unknown'].includes(text(input.oralMorphine))) {
    errors.oralMorphine = 'Choose the oral morphine result.';
  }
  if (input.consent !== true) errors.consent = 'Consent is required to share this report.';
  if (input.bill && !validBill(input.bill)) errors.bill = 'Bill must be a PDF or image up to 4 MB.';

  return { valid: Object.keys(errors).length === 0, errors };
}

function privateDedupe(reports: readonly CentreReportRecord[]): CentreReportRecord[] {
  const seenPrivate = new Set<string>();
  return reports.filter((report) => {
    const reporterId = text(report.reporterId);
    if (!reporterId) return true;
    const key = `${text(report.centreId)}|${text(report.visitDate)}|${reporterId}`;
    if (seenPrivate.has(key)) return false;
    seenPrivate.add(key);
    return true;
  });
}

function statusFor(values: string[], empty: ReportStatus = 'unknown'): ReportStatus {
  const known = values.filter((value) => value === 'available' || value === 'unavailable');
  if (!known.length) return empty;
  const unique = new Set(known);
  if (unique.size > 1) return 'mixed';
  return known[0] === 'available' ? 'available' : 'negative';
}

function outcomeStatusFor(values: string[]): ReportStatus {
  const known = values.filter((value) => value === 'received' || value === 'unavailable');
  if (!known.length) return 'unknown';
  const unique = new Set(known);
  if (unique.size > 1) return 'mixed';
  return known[0] === 'received' ? 'available' : 'negative';
}

export function aggregateCentreReports(
  reports: readonly CentreReportRecord[],
  now: Date | string,
  options: { centreId?: string; recentWindowDays?: number } = {},
): CentreReportSummary {
  const today = dateOnly(now);
  const windowDays = Number.isFinite(options.recentWindowDays) && (options.recentWindowDays ?? 0) >= 0
    ? Math.floor(options.recentWindowDays as number)
    : REPORT_RECENT_WINDOW_DAYS;
  const deduped = privateDedupe(reports).filter((report) => !options.centreId || report.centreId === options.centreId);
  const validPast = deduped.filter((report) => {
    const date = text(report.visitDate);
    return isValidAppointmentDate(date) && (!today || date <= today);
  });
  const recent = validPast.filter((report) => !today || dateDistanceInDays(today, report.visitDate) <= windowDays);
  const ordered = [...validPast].sort((a, b) => b.visitDate.localeCompare(a.visitDate));
  const lastDate = ordered[0]?.visitDate ?? null;
  const morphineValues = recent.map((report) => text(report.oralMorphine));
  const outcomeValues = recent.map((report) => text(report.outcome));
  const morphineStatus = recent.length ? statusFor(morphineValues) : (validPast.length ? 'outdated' : 'empty');
  const outcomeStatus = recent.length ? outcomeStatusFor(outcomeValues) : (validPast.length ? 'outdated' : 'empty');
  const usableRecent = recent.filter((report) => text(report.oralMorphine) === 'available' || text(report.oralMorphine) === 'unavailable');
  const hasConflictingReports = new Set(usableRecent.map((report) => report.oralMorphine)).size > 1;

  return {
    totalCount: validPast.length,
    reportCount: validPast.length,
    recentCount: recent.length,
    recentReportCount: recent.length,
    distinctRecentReports: recent.length,
    lastDate,
    latestVisitDate: lastDate,
    status: morphineStatus,
    morphineStatus,
    outcomeStatus,
    hasConflictingReports,
    outcomes: {
      received: recent.filter((report) => report.outcome === 'received').length,
      unavailable: recent.filter((report) => report.outcome === 'unavailable').length,
    },
    oralMorphine: {
      available: recent.filter((report) => report.oralMorphine === 'available').length,
      unavailable: recent.filter((report) => report.oralMorphine === 'unavailable').length,
      unknown: recent.filter((report) => !report.oralMorphine || report.oralMorphine === 'unknown').length,
    },
  };
}
