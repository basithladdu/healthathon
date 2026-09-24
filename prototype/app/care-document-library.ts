import type { CareReport } from './care-calendar-state.ts';
import type { CareDocumentCategory, CareDocumentText } from './care-document-state.ts';

export const CARE_DOCUMENT_LIBRARY_PATIENT_ID = 'CANCER-20418';
export const CARE_DOCUMENT_LIBRARY_VERSION = 1;

// Fictional records for the existing Meera walkthrough account only. These are
// never attached to a new patient or used to answer questions about other files.
export const CARE_DOCUMENT_FIXTURES: ReadonlyArray<{
  key: string; date: string; title: string; category: CareDocumentCategory; lines: readonly string[];
}> = [
  {
    key: 'care-conversation-2026-08-14', date: '2026-08-14', title: 'Care conversation', category: 'cancer',
    lines: [
      'Participants: Meera Raghavan, Kavya Raghavan (daughter), Dr Sujay.',
      'What matters: Meera wants her family to understand her preferences.',
      'She would like time to speak with her family at home.',
      'Discussion: Who Meera wants involved, and questions for the next conversation.',
      'Specific treatment interventions were not discussed.',
      'Follow-up: Continue the conversation on 28 August 2026.',
    ],
  },
  {
    key: 'visit-checklist-2026-09-23', date: '2026-09-23', title: 'Next visit checklist', category: 'other',
    lines: [
      'Next appointment: 25 September 2026, 11:00 AM.',
      'Visit preparation: Bring previous reports and the current prescription.',
      'Questions to ask: Who should we contact between visits?',
      'Family plan: Kavya will keep the documents together.',
      'Transport: Confirm a ride for the clinic visit.',
      'No new treatment decisions are recorded in this checklist.',
    ],
  },
  {
    key: 'cbc-2026-09-12', date: '2026-09-12', title: 'Complete blood count', category: 'monitoring',
    lines: [
      'Test: Complete blood count (CBC)',
      'Collection date: 12 September 2026',
      'Haemoglobin: 10.5 g/dL',
      'White cell count: 5,800 cells/uL',
      'Platelet count: 185,000 cells/uL',
      'RBC count: 3.8 million/uL',
    ],
  },
  {
    key: 'cbc-2026-09-20', date: '2026-09-20', title: 'Complete blood count', category: 'monitoring',
    lines: [
      'Test: Complete blood count (CBC)',
      'Collection date: 20 September 2026',
      'Haemoglobin: 10.8 g/dL',
      'White cell count: 6,100 cells/uL',
      'Platelet count: 192,000 cells/uL',
      'RBC count: 3.9 million/uL',
    ],
  },
];

/** The PDF and searchable record use the same wrapped text, so citations have real originals. */
export async function createMiraDocumentLibrary(patientId: string): Promise<{ reports: CareReport[]; documents: CareDocumentText[] }> {
  if (patientId !== CARE_DOCUMENT_LIBRARY_PATIENT_ID) return { reports: [], documents: [] };
  const { PDFDocument, StandardFonts, rgb } = await import('pdf-lib');
  const reports: CareReport[] = [];
  const documents: CareDocumentText[] = [];
  for (const fixture of CARE_DOCUMENT_FIXTURES) {
    const pdf = await PDFDocument.create();
    const font = await pdf.embedFont(StandardFonts.Helvetica);
    const bold = await pdf.embedFont(StandardFonts.HelveticaBold);
    const page = pdf.addPage([595.28, 841.89]);
    pdf.setTitle(`${fixture.title} - Meera Raghavan`);
    pdf.setSubject('Fictional product walkthrough fixture; not patient data');
    pdf.setCreator('Saathi');
    const header = ['Saathi', fixture.title, 'Meera Raghavan', `Document date: ${fixture.date}`];
    const lines: string[] = [];
    for (const paragraph of fixture.lines) {
      let line = '';
      for (const word of paragraph.split(' ')) {
        const candidate = line ? `${line} ${word}` : word;
        if (font.widthOfTextAtSize(candidate, 11) > 493 && line) { lines.push(line); line = word; }
        else line = candidate;
      }
      if (line) lines.push(line);
    }
    page.drawRectangle({ x: 0, y: 732, width: 595.28, height: 109.89, color: rgb(.985, .935, .867) });
    page.drawText(header[0], { x: 50, y: 801, size: 11, font: bold, color: rgb(.60, .30, .22) });
    page.drawText(header[1], { x: 50, y: 766, size: 24, font: bold, color: rgb(.24, .20, .28) });
    page.drawText(header[2], { x: 50, y: 704, size: 13, font: bold, color: rgb(.24, .20, .28) });
    page.drawText(header[3], { x: 50, y: 682, size: 10, font, color: rgb(.48, .42, .50) });
    let y = 634;
    for (const line of lines) { page.drawText(line, { x: 50, y, size: 11, font, color: rgb(.25, .22, .28) }); y -= 23; }
    const footer = 'Personal care documents';
    page.drawText(footer, { x: 50, y: 46, size: 9, font, color: rgb(.53, .46, .54) });
    const id = `meera-document-v${CARE_DOCUMENT_LIBRARY_VERSION}-${fixture.key}`;
    const bytes = await pdf.save();
    const file = new File([Uint8Array.from(bytes).buffer], `${fixture.key}.pdf`, { type: 'application/pdf', lastModified: Date.parse(`${fixture.date}T12:00:00Z`) });
    reports.push({ id, patientId, date: fixture.date, addedBy: 'Meera Raghavan', file });
    documents.push({
      reportId: id, patientId, category: fixture.category, recordedBy: 'Meera Raghavan',
      text: `[Page 1]\n${[...header, ...lines, footer].join('\n')}`,
      textSource: 'pdf-text', pagesRead: 1, pageCount: 1, pagesWithoutText: [],
    });
  }
  return { reports, documents };
}
