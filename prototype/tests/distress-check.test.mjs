import test from 'node:test';
import assert from 'node:assert/strict';
import { distressBand, encodeDistress, decodeDistress, loadDistress, saveDistress, DISTRESS_SCORES, distressColour } from '../app/distress-check-state.ts';

test('distress bands follow the Android rules', () => {
  assert.deepEqual([0, 3, 4, 7, 8, 10].map(distressBand), ['none', 'mild', 'moderate', 'moderate', 'severe', 'severe']);
  assert.equal(DISTRESS_SCORES.length, 11);
  assert.equal(distressColour(0), 'rgb(62, 138, 104)');
  assert.equal(distressColour(10), 'rgb(185, 71, 44)');
});

test('only today\'s saved score counts', () => {
  assert.equal(encodeDistress('2026-11-08', 14), '2026-11-08|10');
  assert.equal(decodeDistress('2026-11-08|6', '2026-11-08'), 6);
  assert.equal(decodeDistress('2026-11-07|6', '2026-11-08'), null);
  assert.equal(decodeDistress('2026-11-08|11', '2026-11-08'), null);
  assert.equal(decodeDistress(null, '2026-11-08'), null);
  const mem = new Map();
  const store = { getItem: (k) => mem.get(k) ?? null, setItem: (k, v) => mem.set(k, v) };
  saveDistress(store, 'k', '2026-11-08', 5);
  assert.equal(loadDistress(store, 'k', '2026-11-08'), 5);
});
