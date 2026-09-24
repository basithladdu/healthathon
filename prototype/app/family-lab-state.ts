import { isValidAppointmentDate } from './appointment-state.ts';
import type { CareReport } from './care-calendar-state.ts';

export const LAB_TEST_CATALOG = [
  { name: 'Haemoglobin', label: 'Haemoglobin', short: 'Hb', group: 'blood' },
  { name: 'White cell count', label: 'White cells (WBC)', short: 'WBC', group: 'blood' },
  { name: 'Absolute neutrophil count', label: 'Neutrophils (ANC)', short: 'ANC', group: 'blood' },
  { name: 'Platelet count', label: 'Platelets', short: 'PLT', group: 'blood' },
  { name: 'RBC count', label: 'Red cells (RBC)', short: 'RBC', group: 'blood' },
  { name: 'Creatinine', label: 'Creatinine', short: 'Cr', group: 'kidney-liver' },
  { name: 'Bilirubin', label: 'Bilirubin', short: 'Bili', group: 'kidney-liver' },
  { name: 'ALT', label: 'ALT (SGPT)', short: 'ALT', group: 'kidney-liver' },
  { name: 'AST', label: 'AST (SGOT)', short: 'AST', group: 'kidney-liver' },
  { name: 'Sodium', label: 'Sodium', short: 'Na', group: 'electrolytes' },
  { name: 'Potassium', label: 'Potassium', short: 'K', group: 'electrolytes' },
] as const;
export const LAB_TEST_SUGGESTIONS = LAB_TEST_CATALOG.map((test) => test.name);
export const LAB_REPORT_FLAGS = ['Not stated', 'Low', 'High', 'Within range'] as const;
export type LabReportFlag = (typeof LAB_REPORT_FLAGS)[number];
export type LabResultDraft = {
  testName: string;
  value: string;
  unit: string;
  date: string;
  printedRange: string;
  reportFlag: LabReportFlag;
  reportId: string | null;
  sourceName: string;
};
export type LabResultEntry = LabResultDraft & {
  id: string;
  patientId: string;
  recordedBy: string;
  updatedBy: string;
};
export type LabDraftError = 'testName' | 'value' | 'unit' | 'date' | 'printedRange' | 'reportFlag' | 'source';
export type LabNumericPoint = { id: string; date: string; value: string; numericValue: number; unit: string };
export type LabTreatmentEvent = {
  id: string;
  patientId: string;
  date: string;
  title: string;
  status: 'recorded' | 'planned';
  sourceName: string;
  reportId?: string | null;
};
export type LabTrendRow = { testName: string; unit: string; entries: LabResultEntry[] };

export function labTrendTable(entries: LabResultEntry[], patientId: string, treatmentEvents: readonly LabTreatmentEvent[] = []): {
  dates: string[]; rows: LabTrendRow[]; events: LabTreatmentEvent[];
} {
  const values = selectLabResults(entries, patientId).filter((entry) => isValidAppointmentDate(entry.date));
  const events = treatmentEvents.filter((event) => event.patientId === patientId && isValidAppointmentDate(event.date))
    .slice().sort((a, b) => a.date.localeCompare(b.date));
  const rows: LabTrendRow[] = [];
  for (const entry of values) {
    const row = rows.find((item) => item.testName === entry.testName && item.unit === entry.unit);
    if (row) row.entries.push(entry);
    else rows.push({ testName: entry.testName, unit: entry.unit, entries: [entry] });
  }
  rows.sort((a, b) => a.testName.localeCompare(b.testName) || a.unit.localeCompare(b.unit));
  return { dates: [...new Set([...values.map((entry) => entry.date), ...events.map((event) => event.date)])].sort(), rows, events };
}

export function validateLabResult(draft: LabResultDraft, patientId: string, today: string, reports: CareReport[]): LabDraftError | null {
  if (!draft.testName.trim() || draft.testName.trim().length > 80) return 'testName';
  if (!draft.value.trim() || draft.value.trim().length > 80) return 'value';
  if (!draft.unit.trim() || draft.unit.trim().length > 60) return 'unit';
  if (!isValidAppointmentDate(today) || !isValidAppointmentDate(draft.date) || draft.date > today) return 'date';
  if (draft.printedRange.trim().length > 160) return 'printedRange';
  if (!LAB_REPORT_FLAGS.includes(draft.reportFlag)) return 'reportFlag';
  if (draft.reportId) {
    if (!reports.some((report) => report.patientId === patientId && report.id === draft.reportId)) return 'source';
  } else if (!draft.sourceName.trim() || draft.sourceName.trim().length > 300) return 'source';
  return null;
}

function cleanLabDraft(draft: LabResultDraft, patientId: string, reports: CareReport[]): LabResultDraft {
  const report = reports.find((item) => item.patientId === patientId && item.id === draft.reportId);
  return {
    testName: draft.testName.trim(), value: draft.value.trim(), unit: draft.unit.trim(), date: draft.date,
    printedRange: draft.printedRange.trim(), reportFlag: draft.reportFlag,
    reportId: draft.reportId || null, sourceName: report?.file.name ?? draft.sourceName.trim(),
  };
}

export function addLabResult(entries: LabResultEntry[], patientId: string, author: string, id: string, draft: LabResultDraft, today: string, reports: CareReport[]): LabResultEntry[] {
  if (!patientId || !author.trim() || !id || entries.some((entry) => entry.id === id) || validateLabResult(draft, patientId, today, reports)) return entries;
  return [...entries, { ...cleanLabDraft(draft, patientId, reports), id, patientId, recordedBy: author.trim(), updatedBy: author.trim() }];
}

export function updateLabResult(entries: LabResultEntry[], patientId: string, id: string, author: string, draft: LabResultDraft, today: string, reports: CareReport[]): LabResultEntry[] {
  if (!author.trim() || !entries.some((entry) => entry.patientId === patientId && entry.id === id) || validateLabResult(draft, patientId, today, reports)) return entries;
  return entries.map((entry) => entry.patientId === patientId && entry.id === id
    ? { ...entry, ...cleanLabDraft(draft, patientId, reports), updatedBy: author.trim() } : entry);
}

export function removeLabResult(entries: LabResultEntry[], patientId: string, id: string): LabResultEntry[] {
  return entries.filter((entry) => entry.patientId !== patientId || entry.id !== id);
}

export function restoreLabResult(entries: LabResultEntry[], patientId: string, removed: LabResultEntry): LabResultEntry[] {
  if (removed.patientId !== patientId || entries.some((entry) => entry.id === removed.id)) return entries;
  return [...entries, { ...removed }];
}

export function selectLabResults(entries: LabResultEntry[], patientId: string, testName = ''): LabResultEntry[] {
  return entries.filter((entry) => entry.patientId === patientId && (!testName || entry.testName === testName))
    .sort((a, b) => b.date.localeCompare(a.date) || b.id.localeCompare(a.id));
}

export function labNumericSeries(entries: LabResultEntry[], patientId: string, testName: string, unit: string): LabNumericPoint[] {
  const number = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/;
  return entries.filter((entry) => entry.patientId === patientId && entry.testName === testName && entry.unit === unit
    && isValidAppointmentDate(entry.date) && number.test(entry.value.trim()) && Number.isFinite(Number(entry.value)))
    .map((entry) => ({ id: entry.id, date: entry.date, value: entry.value, numericValue: Number(entry.value), unit: entry.unit }))
    .sort((a, b) => a.date.localeCompare(b.date) || a.id.localeCompare(b.id));
}

export function labHistoryText(entries: LabResultEntry[], patientId: string, hindi = false, treatmentEvents: readonly LabTreatmentEvent[] = []): string {
  const t = (en: string, hi: string) => hindi ? hi : en;
  return [
    t('SAATHI — LAB RESULTS', 'साथी — जाँच के नतीजे'),
    t('Values, units, ranges and flags copied from reports. No interpretation is added.', 'नतीजे, इकाइयाँ, रेंज और फ़्लैग रिपोर्ट से लिखे गए हैं। इनकी व्याख्या नहीं की गई है।'),
    ...selectLabResults(entries, patientId).map((entry) => [
      `${entry.date} — ${entry.testName}`,
      `${t('Result', 'नतीजा')}: ${entry.value} ${entry.unit}`,
      `${t('Range as printed', 'रिपोर्ट में लिखी रेंज')}: ${entry.printedRange || t('Not entered', 'दर्ज नहीं है')}`,
      `${t('Flag as printed', 'रिपोर्ट में लिखा फ़्लैग')}: ${entry.reportFlag}`,
      `${t('Source', 'रिपोर्ट')}: ${entry.sourceName}`,
      `${t('Copied by', 'लिखने वाले')}: ${entry.recordedBy}${entry.updatedBy !== entry.recordedBy ? `; ${t('edited by', 'बदला')}: ${entry.updatedBy}` : ''}`,
    ].join('\n')),
    ...labTrendTable([], patientId, treatmentEvents).events.map((event) => [
      `${event.date} — ${t('Treatment event', 'इलाज की घटना')}: ${event.title}`,
      `${t('Status', 'स्थिति')}: ${event.status === 'planned' ? t('Planned', 'तय किया गया') : t('Recorded', 'दर्ज किया गया')}`,
      `${t('Source', 'स्रोत')}: ${event.sourceName}`,
    ].join('\n')),
  ].join('\n\n');
}
