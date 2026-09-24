import test from 'node:test';
import assert from 'node:assert/strict';
import { confirmCareDocumentTreatment } from '../app/care-document-treatment-state.ts';
import { findCareDocumentText } from '../app/care-document-state.ts';

const report = { id: 'report-a', patientId: 'patient-a', date: '2026-09-22', addedBy: 'Family', file: new File(['source document'], 'treatment.pdf', { type: 'application/pdf' }) };
const document = { reportId: report.id, patientId: report.patientId, category: 'cancer', text: '[Page 1]\nTreatment record\nChemotherapy given on 18 September 2026.\nNext appointment: 28 September 2026.', recordedBy: 'Family', textSource: 'pdf-text' };
const excerpt = findCareDocumentText('chemotherapy', report.patientId, [report], [document])[0].excerpts[0];
const input = () => ({ patientId: report.patientId, author: '  Kavya  ', today: '2026-09-24', report, document, excerpt, title: '  Chemotherapy  ', date: '2026-09-18', confirmed: true, reviewedAt: '2026-09-24T10:30:00.000Z' });
const error = (changes) => confirmCareDocumentTreatment({ ...input(), ...changes }).error;

test('confirmed treatment retains the exact current passage, original file identity, reviewer and entered event date', () => {
  const result = confirmCareDocumentTreatment(input());
  assert.equal(result.error, null);
  assert.deepEqual(result.treatment, {
    title: 'Chemotherapy', date: '2026-09-18',
    source: { reportId: report.id, fileName: 'treatment.pdf', line: excerpt.line, page: 1, quote: excerpt.text, reviewedAt: '2026-09-24T10:30:00.000Z', recordedBy: 'Kavya' },
  });
  assert.notEqual(result.treatment.date, report.date);
  assert.equal(document.reviewedAt, undefined);
});

test('wrong patient or mismatched report cannot attach another person or file source', () => {
  assert.equal(error({ patientId: 'patient-b' }), 'patient');
  assert.equal(error({ report: { ...report, patientId: 'patient-b' } }), 'patient');
  assert.equal(error({ document: { ...document, patientId: 'patient-b' } }), 'patient');
  assert.equal(error({ document: { ...document, reportId: 'report-b' } }), 'report');
  assert.equal(error({ report: { ...report, file: new File([], 'empty.pdf') } }), 'report');
});

test('a moved, changed, truncated or invented passage cannot pass source validation', () => {
  assert.equal(error({ document: { ...document, text: document.text.replace('18 September', '19 September') } }), 'source');
  assert.equal(error({ document: { ...document, text: `New line\n${document.text}` } }), 'source');
  assert.equal(error({ excerpt: { ...excerpt, text: `${excerpt.text}\nInvented treatment.` } }), 'source');
  assert.equal(error({ excerpt: { ...excerpt, text: excerpt.text.trimEnd().slice(0, -1) } }), 'source');
  for (const line of [0, -1, 1.5, 99, NaN]) assert.equal(error({ excerpt: { ...excerpt, line } }), 'source');
});

test('source is exact apart from the same CRLF normalization used by retrieval', () => {
  assert.equal(error({ document: { ...document, text: document.text.replaceAll('\n', '\r\n') } }), null);
  assert.equal(error({ excerpt: { ...excerpt, text: ` ${excerpt.text}` } }), 'source');
  assert.equal(error({ excerpt: { ...excerpt, text: excerpt.text.toLowerCase() } }), 'source');
});

test('PDF page citations must match the current marker and cannot cross page boundaries', () => {
  assert.equal(error({ excerpt: { ...excerpt, page: 2 } }), 'source');
  const paged = { ...document, text: '[Page 1]\nTreatment recorded.\n[Page 2]\nChemotherapy given on 18 September 2026.' };
  assert.equal(error({ document: paged, excerpt: { line: 2, page: 1, text: paged.text.split('\n').slice(1).join('\n') } }), 'source');
  const onPageTwo = { line: 4, page: 2, text: 'Chemotherapy given on 18 September 2026.' };
  const result = confirmCareDocumentTreatment({ ...input(), document: paged, excerpt: onPageTwo });
  assert.equal(result.error, null);
  assert.equal(result.treatment.source.page, 2);
  assert.equal(error({ document: { ...document, text: document.text.replace('[Page 1]', '[Page 0]') }, excerpt: { ...excerpt, page: 0 } }), 'source');
});

test('copied, edited or text-file text never inherits a PDF page citation', () => {
  for (const textSource of ['copied', 'text-file', undefined]) {
    const result = confirmCareDocumentTreatment({ ...input(), document: { ...document, textSource } });
    assert.equal(result.error, null);
    assert.equal(Object.hasOwn(result.treatment.source, 'page'), false);
    assert.equal(result.treatment.source.quote, excerpt.text);
  }
});

test('event date is required, calendar-valid and no later than the supplied today', () => {
  for (const date of ['', '18/09/2026', '2026-02-29', '2026-04-31', '0000-01-01']) assert.equal(error({ date }), 'date');
  assert.equal(error({ today: 'invalid' }), 'date');
  assert.equal(error({ date: '2026-09-25' }), 'future');
  assert.equal(error({ date: '2026-09-24' }), null);
  assert.equal(error({ date: '2024-02-29' }), null);
});

test('no occurrence is recorded without explicit confirmation and an identified reviewer', () => {
  assert.equal(error({ confirmed: false }), 'confirmation');
  assert.equal(error({ confirmed: 'true' }), 'confirmation');
  assert.equal(error({ author: '  ' }), 'author');
  assert.equal(error({ reviewedAt: '' }), 'review-time');
  assert.equal(error({ reviewedAt: 'invalid' }), 'review-time');
  for (const title of ['', '  ', 'x'.repeat(161)]) assert.equal(error({ title }), 'title');
});

test('scheduled wording cannot itself create an event and a future date is rejected even when checked', () => {
  const planned = { ...document, text: '[Page 1]\nChemotherapy scheduled for 28 September 2026.' };
  const plannedExcerpt = { line: 2, text: planned.text.split('\n')[1], page: 1 };
  assert.equal(error({ document: planned, excerpt: plannedExcerpt, date: '', confirmed: false }), 'date');
  assert.equal(error({ document: planned, excerpt: plannedExcerpt, date: '2026-09-28' }), 'future');
  assert.equal(error({ document: planned, excerpt: plannedExcerpt, confirmed: false }), 'confirmation');
  // Nearby plans do not invalidate a separately reviewed past treatment in the same exact passage.
  assert.equal(error({}), null);
});

test('overlong quoted passages are rejected without silently shortening evidence', () => {
  const text = 'x'.repeat(4001);
  assert.equal(error({ document: { ...document, text, textSource: 'copied' }, excerpt: { line: 1, text } }), 'source-long');
});
