import test from 'node:test';
import assert from 'node:assert/strict';
import { addWeight, removeWeight, validWeight, weightChange, weightTrend } from '../app/care-weight-state.ts';
import { painBandText, PAIN_BANDS_SOURCE_EN } from '../app/family-symptom-state.ts';
import '../app/i18n/te.ts';
import '../app/i18n/ur.ts';
import '../app/i18n/kn.ts';
import { careText } from '../app/care-i18n.ts';
import { GOC_CHECKLIST_ROWS } from '../app/goc-checklist.ts';

const TODAY = '2026-11-08';

test('weights keep one entry per day, oldest first, rounded to 0.1 kg', () => {
  let list = addWeight([], { date: '2026-11-01', kg: 54.26 }, TODAY);
  list = addWeight(list, { date: '2026-10-20', kg: 56 }, TODAY);
  list = addWeight(list, { date: '2026-11-01', kg: 53.9 }, TODAY);
  assert.deepEqual(list, [{ date: '2026-10-20', kg: 56 }, { date: '2026-11-01', kg: 53.9 }]);
  assert.equal(weightChange(list), -2.1);
  assert.equal(weightTrend(list), 'down');
  assert.deepEqual(removeWeight(list, '2026-10-20'), [{ date: '2026-11-01', kg: 53.9 }]);
  assert.equal(weightTrend(removeWeight(list, '2026-10-20')), null);
});

test('a future day or an impossible weight is refused', () => {
  assert.equal(validWeight({ date: '2026-11-09', kg: 50 }, TODAY), false);
  assert.equal(validWeight({ date: TODAY, kg: 0 }, TODAY), false);
  assert.equal(validWeight({ date: TODAY, kg: 400 }, TODAY), false);
  assert.equal(validWeight({ date: 'yesterday', kg: 50 }, TODAY), false);
  assert.deepEqual(addWeight(undefined, { date: TODAY, kg: Number.NaN }, TODAY), []);
});

test('pain bands follow the NCCN cut-offs and name their source', () => {
  assert.deepEqual([0, 1, 3, 4, 6, 7, 10].map((n) => painBandText(n)), ['None', 'Mild', 'Mild', 'Moderate', 'Moderate', 'Severe', 'Severe']);
  assert.equal(painBandText(5, 'hi'), 'मध्यम');
  assert.match(PAIN_BANDS_SOURCE_EN, /NCCN Adult Cancer Pain.*Swarm et al\., JNCCN 2010/);
});

test('the 13 goals-of-care rows and the renumbered headings are translated in Telugu, Urdu and Kannada', () => {
  const keys = [...GOC_CHECKLIST_ROWS.map((row) => row.en), 'The patient’s capacity, now', 'Existing directives', 'In their own words', 'Clinicians', 'Section IV – Signatures and declarations', 'Guide me', 'Free flow'];
  for (const lang of ['te', 'ur', 'kn']) {
    for (const key of keys) assert.notEqual(careText(lang, key, key), key, `${lang}: ${key}`);
    assert.doesNotMatch(careText(lang, 'Clinicians', 'Clinicians'), /^\d/, `${lang}: no old section number`);
  }
});
