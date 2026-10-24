import test from 'node:test';
import assert from 'node:assert/strict';
import { painHistoryEntries, painHistoryMapState } from '../app/family-pain-history-state.ts';
import { PAIN_BODY_MODEL } from '../app/pain-body-map-state.ts';

const saved = (id, date, rating = 7, extra = {}) => ({
  id, patientId: 'synthetic-a', author: 'Synthetic patient', symptom: 'Pain', date, time: '08:30',
  severity: rating, startedOn: '', note: '', painBodySites: ['left-arm'], painBodyModel: PAIN_BODY_MODEL,
  painBodyPoints: [{ site: 'left-arm', x: .794, y: .523, z: -.188 }], ...extra,
});

test('pain history is ordered by actual entry time and never mixes another patient or symptom', () => {
  const rows = [saved('old', '2026-10-22', 8), saved('new', '2026-10-24', 3),
    saved('other-person', '2026-10-24', 10, { patientId: 'synthetic-b' }), saved('nausea', '2026-10-24', 2, { symptom: 'Nausea' })];
  assert.deepEqual(painHistoryEntries(rows, 'synthetic-a', '2026-10-24').map((entry) => entry.id), ['new', 'old']);
  assert.deepEqual(rows.map((entry) => entry.id), ['old', 'new', 'other-person', 'nausea']);
});

test('each historical map restores one exact saved point set and rating without averaging or mutating', () => {
  const first = saved('first', '2026-10-23', 7);
  const next = saved('next', '2026-10-24', 2, { painBodyPoints: [{ site: 'left-arm', x: .65, y: .6, z: .2 }] });
  const firstMap = painHistoryMapState(first), nextMap = painHistoryMapState(next);
  assert.equal(firstMap.status, 'mapped'); assert.equal(firstMap.rating, 7); assert.equal(nextMap.rating, 2);
  assert.deepEqual(firstMap.points, first.painBodyPoints); assert.deepEqual(nextMap.points, next.painBodyPoints);
  firstMap.points[0].x = 0;
  assert.equal(first.painBodyPoints[0].x, .794);
});

test('unrated and zero remain distinct, and written-location records do not invent a 3D map', () => {
  assert.equal(painHistoryMapState(saved('unrated', '2026-10-24', null)).rating, null);
  assert.equal(painHistoryMapState(saved('zero', '2026-10-24', 0)).rating, 0);
  const legacy = saved('legacy', '2026-10-22', 'Mild', { painBodySites: undefined, painBodyPoints: undefined, painBodyModel: undefined, painLocation: 'Back' });
  assert.equal(painHistoryMapState(legacy).status, 'written-location');
  assert.deepEqual(painHistoryMapState(legacy).points, []);
  assert.equal(painHistoryMapState(saved('empty', '2026-10-24', 3, { painBodySites: [], painBodyPoints: [], painBodyModel: undefined })).status, 'missing-location');
});

test('unsupported model versions and invalid point sets remain notes instead of fabricated map positions', () => {
  assert.equal(painHistoryMapState(saved('unknown', '2026-10-24', 5, { painBodyModel: 'another-model' })).status, 'unsupported-map');
  assert.equal(painHistoryMapState(saved('bad', '2026-10-24', 5, { painBodyPoints: [{ site: 'left-arm', x: 2, y: .5, z: 0 }] })).status, 'unsupported-map');
});
