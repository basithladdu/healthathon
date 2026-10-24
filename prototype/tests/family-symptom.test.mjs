import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addSymptomEntry, updateSymptomEntry, removeSymptomEntry, restoreSymptomEntry,
  selectSymptomEntries, symptomDiaryText, validateSymptomInput,
  SYMPTOM_SCALE, SYMPTOM_OPTIONS, symptomName, symptomScaleAnchors, symptomComparison, sameSymptomContext,
} from '../app/family-symptom-state.ts';

const today = '2026-09-21';
const entry = { id: 'one', patientId: 'patient-a', author: 'Kavya', symptom: ' Pain ', severity: 'Mild', date: today, startedOn: '2026-09-19', note: ' At night ' };
const other = { ...entry, id: 'two', patientId: 'patient-b', author: 'Other family', symptom: 'Tiredness', note: 'Private note from another family' };

test('dates are real, cannot be future observations and onset cannot follow the note', () => {
  assert.equal(validateSymptomInput(entry, today), null);
  assert.equal(validateSymptomInput({ ...entry, date: '2026-02-30' }, today), 'date');
  assert.equal(validateSymptomInput({ ...entry, date: '2026-09-22' }, today), 'date');
  assert.equal(validateSymptomInput({ ...entry, startedOn: '2026-09-22' }, today), 'startedOn');
  assert.equal(validateSymptomInput({ ...entry, startedOn: '2026-02-30' }, today), 'startedOn');
  assert.equal(validateSymptomInput({ ...entry, date: '2024-02-29', startedOn: '' }, today), null);
  assert.equal(validateSymptomInput({ ...entry, severity: '' }, today), 'severity');
  assert.equal(validateSymptomInput({ ...entry, symptom: ' ' }, today), 'symptom');
  assert.equal(validateSymptomInput({ ...entry, note: 'a'.repeat(501) }, today), 'note');
});

const observed = (id, date, severity = 4, extra = {}) => ({
  id, patientId: 'patient-a', author: 'Patient A', symptom: 'Pain', severity,
  date, time: '18:00', startedOn: '', note: '', scale: SYMPTOM_SCALE,
  recall: 'now', respondent: 'patient', recordedAt: `${date}T12:30:00Z`, ...extra,
});

test('missing ratings remain missing, explicit unable-to-rate is not zero and new context must be complete', () => {
  const candidate = observed('one', today);
  assert.equal(validateSymptomInput({ ...candidate, severity: undefined }, today), 'severity');
  assert.equal(validateSymptomInput({ ...candidate, severity: null }, today), null);
  assert.equal(validateSymptomInput({ ...candidate, severity: 0 }, today), null);
  for (const change of [{ time: '25:00' }, { time: '' }, { respondent: undefined }, { recall: 'last-week' }, { scale: 'ESAS-r' }]) {
    assert.equal(validateSymptomInput({ ...candidate, ...change }, today), 'context');
  }
  assert.equal(validateSymptomInput({ ...candidate, painLocation: 'a'.repeat(101) }, today), 'pain');
  assert.equal(validateSymptomInput({ ...candidate, painTriggers: ['jumping'] }, today), 'pain');
  assert.equal(validateSymptomInput({ ...candidate, symptom: 'Nausea', painTriggers: ['walking'] }, today), 'pain');
});

test('baseline and previous are real dated entries; first, skipped and legacy entries have no fabricated comparison', () => {
  const baseline = observed('baseline', '2026-09-18', 6);
  const previous = observed('previous', '2026-09-20', 3);
  const current = observed('current', today, 5);
  assert.equal(symptomComparison([], current), null);
  assert.equal(symptomComparison([baseline], baseline), null);
  assert.equal(symptomComparison([baseline], { ...current, severity: null }), null);
  assert.equal(symptomComparison([{ ...entry, severity: 7 }], current), null);
  const comparison = symptomComparison([previous, baseline, observed('future', '2026-09-22', 0)], current);
  assert.equal(comparison.baseline.id, 'baseline');
  assert.equal(comparison.previous.id, 'previous');
  assert.equal(comparison.difference, 2);
  assert.equal(symptomComparison([observed('zero', '2026-09-19', 0)], current).previous.severity, 0);
});

test('comparisons never cross person, symptom, recall, observer, scale or pain activity/location context', () => {
  const baseline = observed('baseline', '2026-09-18');
  const current = observed('current', today);
  for (const change of [
    { patientId: 'patient-b' }, { symptom: 'Tiredness' }, { recall: 'average-24h' },
    { respondent: 'caregiver' }, { scale: undefined }, { time: undefined },
    { painLocation: 'Back' }, { painTriggers: ['walking'] },
  ]) assert.equal(symptomComparison([{ ...baseline, ...change }], current), null);
  assert.equal(sameSymptomContext(
    { ...baseline, painLocation: ' Back ', painTriggers: ['walking', 'coughing'] },
    { ...current, painLocation: 'back', painTriggers: ['coughing', 'walking'] },
  ), true);
  assert.equal(sameSymptomContext(
    { ...baseline, respondent: 'caregiver', author: 'Caregiver A' },
    { ...current, respondent: 'caregiver', author: 'Caregiver B' },
  ), false);
});

test('self-reported change attaches only to the actual previous matching observation', () => {
  const baseline = observed('baseline', '2026-09-18', 6);
  const previous = observed('previous', '2026-09-20', 4);
  const current = observed('current', today, 3, { selfReportedChange: 'worse', comparedWithId: 'previous' });
  // The person's own experience can disagree with the numeric difference.
  const result = addSymptomEntry([baseline, previous], current, today);
  assert.equal(result.at(-1).selfReportedChange, 'worse');
  assert.equal(symptomComparison(result, result.at(-1)).difference, -1);
  assert.strictEqual(addSymptomEntry([baseline, previous], { ...current, comparedWithId: 'baseline' }, today).length, 2);
  assert.equal(validateSymptomInput({ ...current, comparedWithId: undefined }, today), 'comparison');
  assert.equal(validateSymptomInput({ ...current, selfReportedChange: 'recovered' }, today), 'comparison');
});

test('stored round trip and exported copy retain context and attribution without leaking another patient', () => {
  const baseline = observed('baseline', '2026-09-18', 6, { painLocation: 'Back', painTriggers: ['walking'] });
  const current = observed('current', today, null, { painLocation: 'Back', painTriggers: ['walking'], respondent: 'assisted-patient' });
  const rows = addSymptomEntry([baseline, other], current, today);
  const restored = JSON.parse(JSON.stringify({ version: 1, value: rows })).value;
  assert.deepEqual(restored.at(-1), rows.at(-1));
  assert.equal(restored.at(-1).severity, null);
  assert.equal(restored.at(-1).recordedAt, `${today}T12:30:00Z`);
  const copy = symptomDiaryText(restored, 'patient-a', today);
  assert.match(copy, /Not rated/);
  assert.match(copy, /18:00/);
  assert.match(copy, /Patient, with help/);
  assert.match(copy, /At this time/);
  assert.match(copy, /Back/);
  assert.match(copy, /Standing or walking/);
  assert.doesNotMatch(copy, /Private note from another family/);
});

test('editing clears a stale comparison and keeps first-author identity and original creation time', () => {
  const baseline = observed('baseline', '2026-09-18', 6);
  const current = observed('current', today, 3, { selfReportedChange: 'better', comparedWithId: 'baseline' });
  const changed = updateSymptomEntry([baseline, current], 'patient-a', 'current', {
    ...current, selfReportedChange: undefined, comparedWithId: undefined, recall: 'worst-24h',
  }, 'Helper', today);
  assert.equal(changed[1].selfReportedChange, undefined);
  assert.equal(changed[1].comparedWithId, undefined);
  assert.equal(changed[1].recordedAt, current.recordedAt);
  assert.equal(changed[1].author, 'Patient A');
  assert.equal(changed[1].editedBy, 'Helper');
  assert.equal(symptomComparison(changed, changed[1]), null);
});

test('add and edit keep identities scoped and never mutate the original note', () => {
  const entries = addSymptomEntry([other], entry, today);
  assert.equal(entries[1].symptom, 'Pain');
  assert.equal(entries[1].note, 'At night');
  assert.equal(entry.note, ' At night ');
  assert.strictEqual(addSymptomEntry(entries, entry, today), entries);
  assert.strictEqual(updateSymptomEntry(entries, 'patient-b', 'one', { ...entry, note: 'Changed' }, 'Other family', today), entries);
  const updated = updateSymptomEntry(entries, 'patient-a', 'one', { ...entry, id: 'hijacked', patientId: 'patient-b', author: 'Fake', note: ' Better now ', severity: 'Moderate' }, 'Meera', today);
  assert.equal(updated[1].note, 'Better now');
  assert.equal(updated[1].id, 'one');
  assert.equal(updated[1].patientId, 'patient-a');
  assert.equal(updated[1].author, 'Kavya');
  assert.equal(updated[1].editedBy, 'Meera');
  assert.equal(entries[1].note, 'At night');
  assert.strictEqual(updated[0], other);
});

test('remove and undo affect only the selected patient, preserve the edited note and do not duplicate it', () => {
  const edited = { ...entry, editedBy: 'Meera' };
  const entries = [edited, other];
  assert.deepEqual(removeSymptomEntry(entries, 'patient-b', 'one'), entries);
  const removed = removeSymptomEntry(entries, 'patient-a', 'one');
  assert.deepEqual(removed, [other]);
  assert.equal(entries.length, 2);
  assert.strictEqual(restoreSymptomEntry(removed, 'patient-b', edited, today), removed);
  const restored = restoreSymptomEntry(removed, 'patient-a', edited, today);
  assert.deepEqual(restored[1], edited);
  assert.notStrictEqual(restored[1], edited);
  assert.strictEqual(restoreSymptomEntry(restored, 'patient-a', edited, today), restored);
});

test('filters and export exclude other patients and use an inclusive seven-day window', () => {
  const entries = [entry, other, { ...entry, id: 'older', date: '2026-09-14', startedOn: '' }, { ...entry, id: 'week-start', date: '2026-09-15', startedOn: '' }];
  assert.equal(selectSymptomEntries(entries, 'patient-a', today, 'today').length, 1);
  assert.equal(selectSymptomEntries(entries, 'patient-a', today, 'week').length, 2);
  assert.equal(selectSymptomEntries(entries, 'patient-a', today, 'all').length, 3);
  assert.deepEqual(selectSymptomEntries(entries, 'patient-a', today, 'all', 'Tiredness'), []);
  const copy = symptomDiaryText(entries, 'patient-a', today);
  assert.match(copy, /At night/);
  assert.match(copy, /Started: 2026-09-19/);
  assert.match(copy, /Not sent to the care team/);
  assert.doesNotMatch(copy, /Other family|Private note from another family/);
  assert.equal(entries[0], entry);
});


test('clinical choices offer distinct patient symptoms and keep retired choices out of new entries', () => {
  const names = SYMPTOM_OPTIONS.map((item) => item.value);
  assert.equal(new Set(names).size, names.length);
  for (const name of ['Diarrhoea', 'Dry or sore mouth', 'Overall well-being', 'Pain', 'Constipation',
    'Difficulty swallowing', 'Cough', 'Bowel or bladder leakage', 'Nausea', 'Vomiting', 'Other']) {
    assert.ok(names.includes(name), name);
  }
  for (const name of ['Sleep trouble', 'Overall discomfort', 'Weight loss', 'Dizziness', 'Irritability / restlessness']) {
    assert.ok(!names.includes(name), name);
  }
  assert.ok(SYMPTOM_OPTIONS.every((item) => item.en && item.hi));
  assert.equal(symptomName('Tiredness'), 'Fatigue / lack of energy');
  assert.equal(symptomName('Dry or sore mouth', true), 'मुँह सूखना या दुखना');
});

test('well-being has best-to-worst anchors and never inherits the no-symptom label', () => {
  assert.deepEqual(symptomScaleAnchors('Overall well-being'), {
    zero: 'Best possible well-being', ten: 'Worst possible well-being',
  });
  assert.deepEqual(symptomScaleAnchors('Overall well-being', true), {
    zero: 'सबसे अच्छी तबीयत', ten: 'सबसे खराब तबीयत',
  });
  assert.equal(symptomScaleAnchors('Nausea').zero, 'Not at all');
  const current = observed('well-being', today, 0, { symptom: 'Overall well-being' });
  const copy = symptomDiaryText([current], 'patient-a', today);
  assert.match(copy, /Overall well-being — 0\/10/);
  assert.match(copy, /0 = Best possible well-being; 10 = Worst possible well-being/);
});

test('retired and custom symptoms survive editing, filtering and export without becoming well-being', () => {
  const old = observed('legacy', '2026-09-18', 4, { symptom: 'Overall discomfort' });
  const sleep = observed('sleep', '2026-09-19', 5, { symptom: 'Sleep trouble' });
  const custom = observed('custom', today, null, { symptom: 'Hiccups' });
  const wellBeing = observed('new', today, 3, { symptom: 'Overall well-being' });
  const changed = updateSymptomEntry([old, sleep, custom], 'patient-a', 'legacy', { ...old, note: 'Still bothers me' }, 'Helper', today);
  assert.equal(changed[0].symptom, 'Overall discomfort');
  assert.equal(changed[0].id, 'legacy');
  assert.equal(selectSymptomEntries(changed, 'patient-a', today, 'all', 'Sleep trouble')[0].id, 'sleep');
  assert.equal(symptomComparison(changed, wellBeing), null);
  const copy = symptomDiaryText(changed, 'patient-a', today, true);
  assert.match(copy, /कुल मिलाकर परेशानी/);
  assert.match(copy, /नींद की परेशानी/);
  assert.match(copy, /Hiccups/);
  assert.doesNotMatch(copy, /सबसे अच्छी तबीयत/);
});
