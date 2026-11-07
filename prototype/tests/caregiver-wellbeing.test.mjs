import test from 'node:test';
import assert from 'node:assert/strict';
import {
  zbiScore, isHighBurden, wellbeingChange, risingSharply, shouldFlag, wellbeingTrend, addCheck, recentChecks,
  parseHistory, parseJournal, addNote, loadHistory, saveHistory, loadJournal, saveJournal, MAPS_URL, TELE_MANAS_HREF, ZBI_MAX_SCORE,
} from '../app/caregiver-wellbeing-state.ts';

const e = (day, score) => ({ day, score });

test('ZBI-4 scores only a full set of four 0 to 4 answers', () => {
  assert.equal(zbiScore([1, 2, 3, 4]), 10);
  assert.equal(zbiScore([0, 0, 0, 0]), 0);
  assert.equal(zbiScore([4, 4, 4, 4]), ZBI_MAX_SCORE);
  assert.equal(zbiScore([1, 2, 3, null]), null);
  assert.equal(zbiScore([1, 2, 3]), null);
  assert.equal(zbiScore([1, 2, 3, 5]), null);
  assert.equal(zbiScore([1, 2, 3, 1.5]), null);
});

test('high burden is 8 or more', () => {
  assert.equal(isHighBurden(7), false);
  assert.equal(isHighBurden(8), true);
});

test('flag when the latest is high or has risen by 4 or more', () => {
  assert.equal(shouldFlag([]), false);
  assert.equal(shouldFlag([e('2026-10-01', 3)]), false);
  assert.equal(shouldFlag([e('2026-10-01', 8)]), true);
  assert.equal(shouldFlag([e('2026-10-01', 2), e('2026-10-08', 6)]), true);
  assert.equal(risingSharply([e('2026-10-01', 2), e('2026-10-08', 5)]), false);
  assert.equal(shouldFlag([e('2026-10-01', 12), e('2026-10-08', 5)]), false);
});

test('trend compares the newest with the previous check', () => {
  assert.equal(wellbeingTrend([e('2026-10-01', 3)]), 'none');
  assert.equal(wellbeingChange([e('2026-10-08', 7), e('2026-10-01', 3)]), 4);
  assert.equal(wellbeingTrend([e('2026-10-01', 3), e('2026-10-08', 5)]), 'rising');
  assert.equal(wellbeingTrend([e('2026-10-01', 5), e('2026-10-08', 3)]), 'falling');
  assert.equal(wellbeingTrend([e('2026-10-01', 5), e('2026-10-08', 6)]), 'steady');
});

test('one check per day, oldest first, newest 12 kept', () => {
  let history = addCheck([], e('2026-10-01', 4));
  history = addCheck(history, e('2026-10-01', 9));
  assert.deepEqual(history, [e('2026-10-01', 9)]);
  for (let day = 2; day <= 20; day += 1) history = addCheck(history, e(`2026-10-${String(day).padStart(2, '0')}`, 1));
  assert.equal(history.length, 12);
  assert.equal(history[0].day, '2026-10-09');
  assert.equal(recentChecks(history).length, 8);
  assert.equal(addCheck([], e('2026-10-01', 99))[0].score, 16);
});

test('stored data is validated and per person', () => {
  assert.deepEqual(parseHistory([e('2026-10-01', 4), e('bad', 3), e('2026-10-02', 40), { day: '2026-10-03' }]), [e('2026-10-01', 4)]);
  assert.deepEqual(parseJournal([{ at: 1, text: 'a' }, { at: 2, text: '  ' }, { at: 3, text: 'c' }]).map((n) => n.at), [3, 1]);
  const mem = new Map();
  const store = { getItem: (k) => mem.get(k) ?? null, setItem: (k, v) => mem.set(k, v) };
  saveHistory(store, 'ns|Asha', [e('2026-10-01', 5)]);
  assert.deepEqual(loadHistory(store, 'ns|Asha'), [e('2026-10-01', 5)]);
  assert.deepEqual(loadHistory(store, 'ns|Ravi'), []);
  saveJournal(store, 'ns|Asha', addNote([], 5, '  hard day '));
  assert.equal(loadJournal(store, 'ns|Asha')[0].text, 'hard day');
  assert.deepEqual(addNote([], 1, '   '), []);
});

test('help links', () => {
  assert.equal(TELE_MANAS_HREF, 'tel:14416');
  assert.ok(MAPS_URL.startsWith('https://www.google.com/maps/search/'));
  assert.ok(decodeURIComponent(MAPS_URL).includes('psychiatrist OR counsellor near me'));
});
