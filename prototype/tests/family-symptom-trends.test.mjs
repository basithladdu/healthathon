import test from 'node:test';
import assert from 'node:assert/strict';
import { symptomTrendGroups, symptomTrendSegments } from '../app/family-symptom-trend-state.ts';
import { SYMPTOM_SCALE, symptomFollowUpInput, addSymptomEntry, sameSymptomContext, symptomDiaryText } from '../app/family-symptom-state.ts';

const entry = (id, date, severity, extra = {}) => ({
  id, patientId: 'patient-a', author: 'Patient A', symptom: 'Pain', severity, date, time: '18:00', startedOn: '2026-10-20', note: '',
  scale: SYMPTOM_SCALE, recall: 'now', respondent: 'patient', ...extra,
});
const mapped = { painBodySites: ['left-arm'], painBodyModel: 'sahara-body-v1', painBodyPoints: [{ site: 'left-arm', x: 0.7, y: 0.6, z: 0.1 }] };

test('trends group real same-context observations, retain zero, and break the line at unrated notes', () => {
  const rows = [entry('first', '2026-10-21', 4), entry('unrated', '2026-10-22', null), entry('last', '2026-10-23', 0),
    entry('other', '2026-10-23', 8, { patientId: 'patient-b' }), entry('future', '2026-10-25', 8),
    entry('legacy', '2026-10-23', 'Mild', { scale: undefined }), entry('bad', '2026-10-23', 44),
    entry('different', '2026-10-23', 6, { recall: 'worst-24h' })];
  const groups = symptomTrendGroups(rows, 'patient-a', '2026-10-24', 'Pain');
  assert.equal(groups.length, 2);
  const main = groups.find((group) => group.entries.length === 3);
  assert.deepEqual(main.entries.map((row) => row.id), ['first', 'unrated', 'last']);
  assert.deepEqual(symptomTrendSegments(main.entries).map((segment) => segment.map((row) => row.id)), [['first'], ['last']]);
});

test('quick follow-up carries location context but never reuses rating, note, comparison or identity', () => {
  const previous = entry('previous', '2026-10-23', 7, { ...mapped, painLocation: 'Elbow', painTriggers: ['walking'], note: 'Earlier note', selfReportedChange: 'worse', comparedWithId: 'old' });
  const followUp = symptomFollowUpInput(previous, '2026-10-24', '09:30');
  assert.equal(followUp.severity, undefined);
  assert.equal(followUp.date, '2026-10-24'); assert.equal(followUp.time, '09:30');
  assert.equal(followUp.note, ''); assert.equal(followUp.selfReportedChange, undefined);
  assert.equal(followUp.id, undefined); assert.equal(followUp.patientId, undefined);
  assert.deepEqual(followUp.painBodyPoints, previous.painBodyPoints);
  assert.notStrictEqual(followUp.painBodyPoints[0], previous.painBodyPoints[0]);
  const rated = { ...followUp, severity: 5, id: 'new', patientId: previous.patientId, author: previous.author };
  assert.ok(sameSymptomContext(previous, rated));
  assert.equal(addSymptomEntry([previous], rated, '2026-10-24').length, 2);
});

test('point codec round trip preserves chosen spots and does not compare relocated or unknown-model points', () => {
  const previous = entry('first', '2026-10-23', 7, mapped);
  const current = entry('next', '2026-10-24', 4, mapped);
  const stored = addSymptomEntry([], current, '2026-10-24');
  const roundTrip = JSON.parse(JSON.stringify(stored))[0];
  assert.deepEqual(roundTrip.painBodyPoints, current.painBodyPoints);
  assert.equal(roundTrip.painBodyModel, 'sahara-body-v1');
  assert.ok(sameSymptomContext(previous, roundTrip));
  assert.ok(!sameSymptomContext(previous, { ...current, painBodyPoints: [{ ...mapped.painBodyPoints[0], x: 0.8 }] }));
  assert.ok(!sameSymptomContext(previous, { ...current, painBodyModel: 'another-model' }));
  assert.match(symptomDiaryText([current], 'patient-a', '2026-10-24'), /Pain areas: Left arm \/ hand/);
  assert.match(symptomDiaryText([current], 'patient-a', '2026-10-24', true), /दर्द की जगहें: बायाँ हाथ/);
  assert.ok(sameSymptomContext(entry('old-a', '2026-10-23', 4), entry('old-b', '2026-10-24', 2)));
});
