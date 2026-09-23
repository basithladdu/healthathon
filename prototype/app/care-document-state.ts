import type { CareReport } from './care-calendar-state.ts';

export type CareDocumentCategory = 'cancer' | 'monitoring' | 'other';
export type CareDocumentText = {
  reportId: string;
  patientId: string;
  category: CareDocumentCategory;
  text: string;
  recordedBy: string;
  textSource?: 'copied' | 'pdf-text' | 'text-file';
  pagesRead?: number;
  pageCount?: number;
  pagesWithoutText?: number[];
  reviewedAt?: string;
};
export type CareDocumentMatch = {
  report: CareReport;
  recordedBy: string;
  excerpts: Array<{ line: number; text: string; page?: number }>;
};
export type RemovedCareDocument = { report: CareReport; documents: CareDocumentText[] };

export function removeCareDocument(patientId: string, reportId: string, reports: CareReport[], documents: CareDocumentText[]): {
  reports: CareReport[]; documents: CareDocumentText[]; removed: RemovedCareDocument | null;
} {
  const report = reports.find((item) => item.patientId === patientId && item.id === reportId);
  if (!report) return { reports, documents, removed: null };
  return {
    reports: reports.filter((item) => item.patientId !== patientId || item.id !== reportId),
    documents: documents.filter((item) => item.patientId !== patientId || item.reportId !== reportId),
    removed: { report, documents: documents.filter((item) => item.patientId === patientId && item.reportId === reportId) },
  };
}

export function restoreCareDocument(patientId: string, removed: RemovedCareDocument, reports: CareReport[], documents: CareDocumentText[]): {
  reports: CareReport[]; documents: CareDocumentText[]; restored: boolean;
} {
  if (removed.report.patientId !== patientId || removed.documents.some((item) => item.patientId !== patientId || item.reportId !== removed.report.id)
    || reports.some((item) => item.patientId === patientId && item.id === removed.report.id)
    || documents.some((item) => item.patientId === patientId && item.reportId === removed.report.id)) return { reports, documents, restored: false };
  return { reports: [...reports, removed.report], documents: [...documents, ...removed.documents], restored: true };
}

export function careDocumentCategoryFromName(name: string): CareDocumentCategory {
  const words = name.toLocaleLowerCase().replace(/[_-]/g, ' ');
  if (/\b(pet|ct|mri|biopsy|histopathology|histology|chemotherapy|radiotherapy|treatment|discharge|cancer|oncology)\b/.test(words)) return 'cancer';
  if (/\b(cbc|lft|kft|rft|blood|haemogram|hemogram|haemoglobin|hemoglobin|creatinine|bilirubin|platelet|monitoring)\b/.test(words)) return 'monitoring';
  return 'other';
}

export function careReportCategory(report: CareReport, documents: readonly CareDocumentText[] = []): CareDocumentCategory {
  return documents.find((document) => document.patientId === report.patientId && document.reportId === report.id)?.category
    ?? careDocumentCategoryFromName(report.file.name);
}

export function careDocumentFileError(file: Pick<File, 'type' | 'name' | 'size'>): 'type' | 'size' | null {
  const supported = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp', 'text/plain'].includes(file.type)
    || (!file.type && /\.(pdf|jpe?g|png|webp|txt)$/i.test(file.name));
  if (!supported) return 'type';
  return file.size <= 0 || file.size > 10 * 1024 * 1024 ? 'size' : null;
}

const ignoredWords = new Set([
  'a', 'an', 'the', 'my', 'our', 'patient', 'was', 'were', 'is', 'are', 'has', 'have', 'had', 'did', 'do', 'does',
  'when', 'what', 'where', 'which', 'how', 'who', 'why', 'last', 'latest', 'next', 'date', 'of', 'on', 'in', 'at', 'to',
  'for', 'from', 'and', 'or', 'tell', 'me', 'show', 'find', 'about', 'please', 'get', 'got', 'given', 'receive', 'received',
  'happen', 'happened', 'report', 'reports', 'document', 'documents', 'i', 'we', 'you', 'can', 'could', 'should', 'would',
  'need', 'needto', 'am', 'be', 'been', 'it', 'with', 'that', 'this', 'there', 'any', 'said', 'says', 'say', 'recorded', 'care',
  'कब', 'क्या', 'है', 'था', 'की', 'का', 'के', 'में', 'मुझे', 'मेरा', 'मेरे', 'मेरी', 'आखिरी', 'तारीख', 'बताएँ', 'लिए', 'मैं', 'हूँ',
]);
const relatedTerms = [
  ['chemotherapy', 'chemo', 'कीमोथेरेपी', 'कीमो'],
  ['radiotherapy', 'radiation', 'रेडियोथेरेपी', 'रेडिएशन'],
  ['biopsy', 'histopathology', 'histology', 'बायोप्सी'],
  ['diagnosis', 'diagnosed', 'diagnostic', 'निदान'],
  ['appointment', 'appointments', 'visit', 'visits', 'consultation', 'followup', 'मुलाकात', 'अपॉइंटमेंट'],
  ['prepare', 'preparation', 'bring', 'carry', 'तैयारी', 'लाना', 'लाएँ'],
  ['participants', 'participant', 'attendees', 'joined', 'attended', 'present', 'शामिल'],
  ['preference', 'preferences', 'matters', 'important', 'priorities', 'wishes', 'ज़रूरी', 'महत्वपूर्ण'],
  ['blood', 'cbc', 'haemogram', 'hemogram', 'सीबीसी', 'खून'],
  ['haemoglobin', 'hemoglobin', 'hb', 'हीमोग्लोबिन'],
  ['platelet', 'platelets', 'plt', 'प्लेटलेट'],
  ['medicine', 'medicines', 'medication', 'medications', 'prescription', 'prescriptions', 'दवा', 'दवाएँ'],
  ['family', 'caregiver', 'caregivers', 'carer', 'carers', 'परिवार'],
  ['transport', 'travel', 'ride', 'यात्रा'],
  ['conversation', 'discussion', 'discussed', 'बातचीत'],
];

function words(text: string): string[] {
  return text.toLocaleLowerCase().replace(/follow[ -]up/g, 'followup').match(/[\p{L}\p{M}\p{N}]+/gu) ?? [];
}

/** Extractive retrieval only: every returned word belongs to the patient's saved source text. */
export function findCareDocumentText(query: string, patientId: string, reports: readonly CareReport[], documents: readonly CareDocumentText[]): CareDocumentMatch[] {
  const terms = [...new Set(words(query).filter((term) => !ignoredWords.has(term)))];
  if (!terms.length) return [];
  const needles = [...new Set(terms.map((term) => relatedTerms.find((group) => group.includes(term)) ?? [term]))];
  // "Who joined the conversation?" asks for participants. The document title
  // supplies context, but must never become answer text on its own.
  const asksParticipants = needles.some((group) => group.includes('participants'));
  const requiredNeedles = asksParticipants ? needles.filter((group) => !group.includes('conversation')) : needles;
  const matches: Array<CareDocumentMatch & { score: number }> = [];
  for (const report of reports.filter((item) => item.patientId === patientId)) {
    const document = documents.find((item) => item.patientId === patientId && item.reportId === report.id);
    if (!document?.text.trim()) continue;
    const lines = document.text.split(/\r?\n/);
    let sourcePage: number | undefined;
    const linePages = lines.map((line) => {
      const marker = /^\[Page (\d+)\]$/.exec(line.trim());
      if (document.textSource === 'pdf-text' && marker) sourcePage = Number(marker[1]);
      return sourcePage;
    });
    const candidates: Array<{ first: number; last: number; score: number }> = [];
    for (let index = 0; index < lines.length; index += 1) {
      if (!lines[index].trim() || /^\[Page \d+\]$/.test(lines[index].trim())) continue;
      const previous = index - 1;
      const first = previous >= 0 && linePages[previous] === linePages[index] && !/^\[Page \d+\]$/.test(lines[previous].trim()) ? previous : index;
      const last = index + 1 < lines.length && linePages[index + 1] === linePages[index] && !/^\[Page \d+\]$/.test(lines[index + 1].trim()) ? index + 1 : index;
      const windowWords = words(lines.slice(first, last + 1).join(' '));
      if (!requiredNeedles.every((group) => group.some((term) => windowWords.includes(term)))) continue;
      const lineWords = words(lines[index]);
      const score = needles.filter((group) => group.some((term) => lineWords.includes(term))).length * 2
        + terms.filter((term) => lineWords.includes(term)).length;
      candidates.push({ first, last, score });
    }
    const excerpts: CareDocumentMatch['excerpts'] = [];
    const shown = new Set<number>();
    candidates.sort((a, b) => b.score - a.score || a.first - b.first);
    for (const match of candidates) {
      if (Array.from({ length: match.last - match.first + 1 }, (_, offset) => match.first + offset).some((line) => shown.has(line))) continue;
      for (let index = match.first; index <= match.last; index += 1) shown.add(index);
      excerpts.push({ line: match.first + 1, text: lines.slice(match.first, match.last + 1).join('\n'), page: linePages[match.first] });
    }
    if (excerpts.length) matches.push({ report, recordedBy: document.recordedBy, excerpts, score: candidates[0].score });
  }
  const dateFirst = /\b(last|latest|recent|newest)\b|आखिरी|हालिया/i.test(query);
  return matches.sort((a, b) => (dateFirst ? b.report.date.localeCompare(a.report.date) : b.score - a.score)
    || b.report.date.localeCompare(a.report.date) || a.report.file.name.localeCompare(b.report.file.name))
    .map(({ report, recordedBy, excerpts }) => ({ report, recordedBy, excerpts }));
}
