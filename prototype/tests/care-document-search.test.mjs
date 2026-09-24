import test from 'node:test';
import assert from 'node:assert/strict';
import { careDocumentCategoryFromName, careReportCategory, careDocumentFileError, findCareDocumentText, removeCareDocument, restoreCareDocument } from '../app/care-document-state.ts';
import { createMiraDocumentLibrary, CARE_DOCUMENT_LIBRARY_PATIENT_ID } from '../app/care-document-library.ts';

const report = (id, patientId = 'a', date = '2026-09-20', name = `${id}.pdf`) => ({ id, patientId, date, addedBy: 'Family', file: new File(['source'], name, { type: 'application/pdf' }) });
const source = (reportId, text, patientId = 'a') => ({ reportId, patientId, category: 'other', text, recordedBy: 'Family', textSource: 'pdf-text' });

test('natural questions retrieve adjacent source lines with genuine page and line locations', () => {
  const text = '[Page 1]\nVisit checklist\nNext appointment: 25 September.\nPlease bring previous reports and the current prescription.\nFamily transport is arranged.';
  const matches = findCareDocumentText('What should I bring to my next visit?', 'a', [report('visit')], [source('visit', text)]);
  assert.equal(matches.length, 1);
  assert.equal(matches[0].excerpts[0].page, 1);
  assert.ok(matches[0].excerpts[0].text.includes('Please bring previous reports'));
  for (const excerpt of matches[0].excerpts) {
    assert.equal(text.split('\n').slice(excerpt.line - 1, excerpt.line - 1 + excerpt.text.split('\n').length).join('\n'), excerpt.text);
  }
});

test('same report identifiers cannot leak another patient source or become a filename-only answer', () => {
  const reports = [report('same'), report('same', 'b'), report('filename', 'a', '2026-09-20', 'diabetes-diagnosis.pdf')];
  const documents = [source('same', 'No decisions recorded.'), source('same', 'Private diabetes diagnosis.', 'b'), source('filename', 'A visit checklist.')];
  assert.deepEqual(findCareDocumentText('diabetes diagnosis', 'a', reports, documents), []);
  assert.equal(findCareDocumentText('diabetes diagnosis', 'b', reports, documents).length, 1);
});

test('search never joins facts across PDF pages to imply a matching passage', () => {
  const text = '[Page 1]\nBring your prescription.\n[Page 2]\nAppointment time: 11 AM.';
  assert.deepEqual(findCareDocumentText('bring appointment', 'a', [report('pages')], [source('pages', text)]), []);
});

test('latest questions order source dates and synonyms work without medical interpretation', () => {
  const reports = [report('old', 'a', '2026-09-12'), report('new', 'a', '2026-09-20')];
  const docs = [source('old', '[Page 1]\nHaemoglobin: 10.5 g/dL'), source('new', '[Page 1]\nHaemoglobin: 10.8 g/dL')];
  const matches = findCareDocumentText('My latest hemoglobin', 'a', reports, docs);
  assert.deepEqual(matches.map((match) => match.report.id), ['new', 'old']);
  assert.equal(findCareDocumentText('मेरा आखिरी हीमोग्लोबिन', 'a', reports, docs).length, 2);
  assert.deepEqual(findCareDocumentText('Is haemoglobin dangerous?', 'a', reports, docs), []);
  assert.deepEqual(findCareDocumentText('What should I do?', 'a', reports, docs), []);
});

test('treatment-date questions surface exact recorded and planned passages without turning document dates into treatment dates', () => {
  const reports = [report('treatment', 'a', '2026-09-20')];
  const text = '[Page 1]\nChemotherapy given on 12 September 2026.\nRadiotherapy planned for 25 September 2026.';
  const documents = [source('treatment', text)];
  for (const query of ['Treatment dates', 'इलाज की तारीखें']) {
    const matches = findCareDocumentText(query, 'a', reports, documents);
    assert.equal(matches.length, 1);
    assert.equal(matches[0].report.date, '2026-09-20');
    assert.ok(matches[0].excerpts.some((excerpt) => excerpt.text.includes('given on 12 September 2026.')));
    assert.ok(matches[0].excerpts.some((excerpt) => excerpt.text.includes('planned for 25 September 2026.')));
    for (const excerpt of matches[0].excerpts) assert.ok(text.includes(excerpt.text));
  }
});

test('category is editable, patient-scoped and only a filename suggestion until reviewed', () => {
  assert.equal(careDocumentCategoryFromName('cbc-result.png'), 'monitoring');
  assert.equal(careDocumentCategoryFromName('biopsy_report.pdf'), 'cancer');
  assert.equal(careDocumentCategoryFromName('IMG_1234.jpg'), 'other');
  const file = report('one', 'a', '2026-09-20', 'cbc.pdf');
  assert.equal(careReportCategory(file, [{ ...source('one', '', 'b'), category: 'other' }]), 'monitoring');
  assert.equal(careReportCategory(file, [{ ...source('one', ''), category: 'other' }]), 'other');
});

test('uploads permit only bounded PDFs, raster images and text files', () => {
  assert.equal(careDocumentFileError(new File(['hi'], 'note.txt', { type: 'text/plain' })), null);
  assert.equal(careDocumentFileError({ type: '', name: 'report.pdf', size: 20 }), null);
  assert.equal(careDocumentFileError({ type: 'text/html', name: 'report.pdf', size: 20 }), 'type');
  assert.equal(careDocumentFileError({ type: 'image/svg+xml', name: 'report.svg', size: 20 }), 'type');
  assert.equal(careDocumentFileError({ type: 'image/png', name: 'photo.png', size: 0 }), 'size');
  assert.equal(careDocumentFileError({ type: 'application/pdf', name: 'report.pdf', size: 10 * 1024 * 1024 + 1 }), 'size');
});

test('Meera library has real PDFs, stable IDs and directly supported questions for one account only', async () => {
  assert.deepEqual(await createMiraDocumentLibrary('another-patient'), { reports: [], documents: [] });
  const library = await createMiraDocumentLibrary(CARE_DOCUMENT_LIBRARY_PATIENT_ID);
  assert.equal(library.reports.length, 4);
  assert.equal(new Set(library.reports.map((item) => item.id)).size, 4);
  for (const file of library.reports) {
    assert.equal(await file.file.slice(0, 5).text(), '%PDF-');
    const document = library.documents.find((item) => item.reportId === file.id);
    assert.equal(document.patientId, file.patientId);
    assert.match(document.text, /\[Page 1\]/);
    assert.ok(document.text.includes(file.date));
  }
  const search = (question) => findCareDocumentText(question, CARE_DOCUMENT_LIBRARY_PATIENT_ID, library.reports, library.documents);
  assert.ok(search('What should I bring to my next visit?')[0].excerpts.some((excerpt) => excerpt.text.includes('Bring previous reports')));
  assert.ok(search('What matters to me?')[0].excerpts.some((excerpt) => excerpt.text.includes('understand her preferences')));
  assert.ok(search('Who joined the care conversation?')[0].excerpts.some((excerpt) => excerpt.text.includes('Kavya Raghavan')));
  assert.ok(search('My latest haemoglobin')[0].excerpts.some((excerpt) => excerpt.text.includes('10.8 g/dL')));
  assert.deepEqual(search('How much aspirin should I take?'), []);
  assert.deepEqual(findCareDocumentText('haemoglobin', 'another-patient', library.reports, library.documents), []);
});

test('removing a document removes its searchable source and undo preserves the exact original file and text', () => {
  const original = report('shared-id');
  const otherPatient = report('shared-id', 'b');
  const reports = [original, otherPatient, report('keep')];
  const checked = source('shared-id', '[Page 1]\nHaemoglobin: 10.8 g/dL');
  const otherText = source('shared-id', 'Other patient private result.', 'b');
  const documents = [checked, otherText, source('keep', 'Visit checklist.')];
  const removed = removeCareDocument('a', 'shared-id', reports, documents);
  assert.equal(removed.reports.length, 2);
  assert.strictEqual(removed.reports[0], otherPatient);
  assert.strictEqual(removed.documents[0], otherText);
  assert.deepEqual(findCareDocumentText('haemoglobin', 'a', removed.reports, removed.documents), []);
  assert.equal(reports.length, 3);
  assert.equal(documents.length, 3);
  const restored = restoreCareDocument('a', removed.removed, removed.reports, removed.documents);
  assert.equal(restored.restored, true);
  assert.strictEqual(restored.reports.at(-1).file, original.file);
  assert.strictEqual(restored.documents.at(-1), checked);
  assert.equal(findCareDocumentText('haemoglobin', 'a', restored.reports, restored.documents).length, 1);
});

test('invalid deletion and conflicting or cross-patient undo cannot overwrite existing sources', () => {
  const reports = [report('one')];
  const documents = [source('one', 'Original source.')];
  const foreign = removeCareDocument('b', 'one', reports, documents);
  assert.equal(foreign.removed, null);
  assert.strictEqual(foreign.reports, reports);
  assert.strictEqual(foreign.documents, documents);
  const removed = removeCareDocument('a', 'one', reports, documents);
  assert.equal(restoreCareDocument('b', removed.removed, [], []).restored, false);
  const newer = source('one', 'Newer source.');
  const existing = [newer];
  const blocked = restoreCareDocument('a', removed.removed, [], existing);
  assert.equal(blocked.restored, false);
  assert.strictEqual(blocked.documents, existing);
  const once = restoreCareDocument('a', removed.removed, [], []);
  assert.equal(restoreCareDocument('a', removed.removed, once.reports, once.documents).restored, false);
});
