import test from 'node:test';
import assert from 'node:assert/strict';
import { validateLabResult, addLabResult, updateLabResult, removeLabResult, restoreLabResult, selectLabResults, labNumericSeries, labHistoryText } from '../app/family-lab-state.ts';

const today = '2026-09-21';
const reports = [
  { id: 'report-a', patientId: 'a', file: { name: 'cbc-a.pdf' } },
  { id: 'report-b', patientId: 'b', file: { name: 'private-b.pdf' } },
];
const draft = { testName: ' Haemoglobin ', value: ' 10.5 ', unit: ' g/dL ', date: '2026-09-20', printedRange: ' 12–16 ', reportFlag: 'Low', reportId: 'report-a', sourceName: 'ignored when linked' };
const saved = () => addLabResult([], 'a', 'Kavya', 'one', draft, today, reports)[0];

test('required fields, real past dates and patient-scoped report sources are validated without deriving flags', () => {
  assert.equal(validateLabResult(draft, 'a', today, reports), null);
  assert.equal(validateLabResult({ ...draft, testName: ' ' }, 'a', today, reports), 'testName');
  assert.equal(validateLabResult({ ...draft, value: '' }, 'a', today, reports), 'value');
  assert.equal(validateLabResult({ ...draft, unit: '' }, 'a', today, reports), 'unit');
  assert.equal(validateLabResult({ ...draft, date: '2026-02-30' }, 'a', today, reports), 'date');
  assert.equal(validateLabResult({ ...draft, date: '2026-09-22' }, 'a', today, reports), 'date');
  assert.equal(validateLabResult({ ...draft, reportId: 'report-b' }, 'a', today, reports), 'source');
  assert.equal(validateLabResult({ ...draft, reportId: 'missing' }, 'a', today, reports), 'source');
  assert.equal(validateLabResult({ ...draft, reportId: null, sourceName: ' ' }, 'a', today, reports), 'source');
  const manual = addLabResult([], 'a', 'Kavya', 'manual', { ...draft, reportId: null, sourceName: ' Typed lab label ', value: '<5', reportFlag: 'Not stated' }, today, reports)[0];
  assert.equal(manual.value, '<5');
  assert.equal(manual.reportFlag, 'Not stated');
  assert.equal(manual.sourceName, 'Typed lab label');
  assert.equal(saved().sourceName, 'cbc-a.pdf');
});

test('edits cannot change patient, author or id and leave other families untouched', () => {
  const first = saved();
  const other = { ...first, id: 'two', patientId: 'b', sourceName: 'private-b.pdf' };
  const entries = Object.freeze([first, other]);
  assert.strictEqual(updateLabResult(entries, 'b', 'one', 'Other', draft, today, reports), entries);
  const edited = updateLabResult(entries, 'a', 'one', 'Meera', { ...draft, patientId: 'b', id: 'changed', recordedBy: 'changed', value: '11.0', reportFlag: 'Not stated' }, today, reports);
  assert.equal(edited[0].id, 'one');
  assert.equal(edited[0].patientId, 'a');
  assert.equal(edited[0].recordedBy, 'Kavya');
  assert.equal(edited[0].updatedBy, 'Meera');
  assert.equal(edited[0].reportFlag, 'Not stated');
  assert.equal(first.value, '10.5');
  assert.strictEqual(edited[1], other);
});

test('delete and undo preserve source labels after files are removed and prevent duplicate restoration', () => {
  const first = saved();
  const remaining = removeLabResult([first], 'a', 'one');
  assert.deepEqual(remaining, []);
  assert.strictEqual(restoreLabResult(remaining, 'b', first), remaining);
  const restored = restoreLabResult(remaining, 'a', first);
  assert.equal(restored[0].sourceName, 'cbc-a.pdf');
  assert.equal(restored[0].reportId, 'report-a');
  assert.notStrictEqual(restored[0], first);
  assert.strictEqual(restoreLabResult(restored, 'a', first), restored);
  assert.deepEqual(removeLabResult(restored, 'b', 'one'), restored);
});

test('the plot combines only exact test and unit matches, omits nonnumeric text and keeps all raw results in the timeline', () => {
  const first = saved();
  const entries = Object.freeze([
    first,
    { ...first, id: 'same', date: '2026-09-21', value: '11.00' },
    { ...first, id: 'less-than', value: '<5' },
    { ...first, id: 'comma', value: '1,000' },
    { ...first, id: 'bad-number', value: 'Infinity' },
    { ...first, id: 'other-unit', unit: 'g/L', value: '105' },
    { ...first, id: 'case-unit', unit: 'g/dl', value: '10.5' },
    { ...first, id: 'other-test', testName: 'RBC count', value: '4' },
    { ...first, id: 'other-patient', patientId: 'b', value: '99' },
  ]);
  const points = labNumericSeries(entries, 'a', 'Haemoglobin', 'g/dL');
  assert.deepEqual(points.map((point) => point.id), ['one', 'same']);
  assert.deepEqual(points.map((point) => point.numericValue), [10.5, 11]);
  assert.equal(points[1].value, '11.00');
  assert.equal(selectLabResults(entries, 'a', 'Haemoglobin').length, 7);
  assert.equal(entries[0].id, 'one');
});

test('text download is patient-scoped and keeps literal values, units, ranges, flags and source names', () => {
  const first = saved();
  const entries = [first, { ...first, id: 'other', patientId: 'b', value: 'SECRET', sourceName: 'private-b.pdf' }];
  const text = labHistoryText(entries, 'a');
  assert.match(text, /Result: 10\.5 g\/dL/);
  assert.match(text, /Range as printed: 12–16/);
  assert.match(text, /Flag as printed: Low/);
  assert.match(text, /Source: cbc-a\.pdf/);
  assert.doesNotMatch(text, /SECRET|private-b/);
});
