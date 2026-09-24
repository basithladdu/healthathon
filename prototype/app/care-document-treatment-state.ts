import { isValidAppointmentDate } from './appointment-state.ts';
import type { CareReport } from './care-calendar-state.ts';
import type { CareDocumentMatch, CareDocumentText } from './care-document-state.ts';

export type CareDocumentTreatmentSource = {
  reportId: string; fileName: string; line: number; page?: number; quote: string;
  reviewedAt: string; recordedBy: string;
};
export type ConfirmedDocumentTreatment = { title: string; date: string; source: CareDocumentTreatmentSource };
export type CareDocumentTreatmentExcerpt = CareDocumentMatch['excerpts'][number];
export type CareDocumentTreatmentInput = {
  patientId: string; author: string; today: string; report: CareReport; document: CareDocumentText;
  excerpt: CareDocumentTreatmentExcerpt; title: string; date: string; confirmed: boolean; reviewedAt: string;
};
export type CareDocumentTreatmentError = 'patient' | 'report' | 'source' | 'source-long' | 'title' | 'date' | 'future' | 'confirmation' | 'author' | 'review-time';
export type CareDocumentTreatmentResult =
  | { treatment: ConfirmedDocumentTreatment; error: null }
  | { treatment: null; error: CareDocumentTreatmentError };

/** The reviewer confirms occurrence. This only validates the entered date and exact source, never clinical meaning. */
export function confirmCareDocumentTreatment(input: CareDocumentTreatmentInput): CareDocumentTreatmentResult {
  const { patientId, author, today, report, document, excerpt, title, date, confirmed, reviewedAt } = input;
  const fail = (error: CareDocumentTreatmentError): CareDocumentTreatmentResult => ({ treatment: null, error });
  if (!patientId.trim() || report.patientId !== patientId || document.patientId !== patientId) return fail('patient');
  if (!report.id.trim() || document.reportId !== report.id || !report.file.name.trim() || report.file.size <= 0) return fail('report');
  if (!Number.isInteger(excerpt.line) || excerpt.line < 1 || !excerpt.text.trim()) return fail('source');
  if (excerpt.text.length > 4000) return fail('source-long');
  const lines = document.text.split(/\r?\n/);
  const first = excerpt.line - 1;
  const last = first + excerpt.text.split('\n').length;
  if (last > lines.length || lines.slice(first, last).join('\n') !== excerpt.text) return fail('source');

  let page: number | undefined;
  if (document.textSource === 'pdf-text') {
    for (let index = 0; index < last; index += 1) {
      const marker = /^\[Page (\d+)\]$/.exec(lines[index].trim());
      if (!marker) continue;
      if (index >= first) return fail('source');
      page = Number(marker[1]);
    }
    if ((page !== undefined && (!Number.isSafeInteger(page) || page < 1))
      || (excerpt.page !== undefined && excerpt.page !== page)) return fail('source');
  }
  if (!title.trim() || title.trim().length > 160) return fail('title');
  if (!isValidAppointmentDate(date) || !isValidAppointmentDate(today)) return fail('date');
  if (date > today) return fail('future');
  if (confirmed !== true) return fail('confirmation');
  if (!author.trim()) return fail('author');
  if (!Number.isFinite(Date.parse(reviewedAt))) return fail('review-time');
  return {
    error: null,
    treatment: {
      title: title.trim(), date,
      source: {
        reportId: report.id, fileName: report.file.name, line: excerpt.line,
        ...(page === undefined ? {} : { page }), quote: excerpt.text, reviewedAt, recordedBy: author.trim(),
      },
    },
  };
}
