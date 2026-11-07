import test from 'node:test';
import assert from 'node:assert/strict';
import { extractGoc, gocLinesFromText } from '../app/goc-extract.ts';
import { gocTopicRows, gocNotDiscussedTitles } from '../app/goc-note.ts';
import { GOC_CHECKLIST_ROWS, gocChecklist, gocChecklistDone, gocChecklistOpen, gocChecklistTitle } from '../app/goc-checklist.ts';
import {
  GOC_GUIDE_TEXT, GOC_ROW_SECTIONS, GOC_ROW_TITLES, gocEssentialsDone, gocGuideDecode, gocGuideDiscussed, gocGuideDone, gocGuideEncode, gocGuideNext, gocGuideOutOfOrder, gocGuideTap, gocMmss,
} from '../app/goc-guide.ts';
import { gocNoteSuffix, gocStartNewVersion, gocTalkLocked, gocTalkVersion } from '../app/goc-version.ts';

const rowsFor = (text, marks = {}, opts = {}) => gocTopicRows(extractGoc(gocLinesFromText(text)).topics, marks, opts);

test('Dr Sujay’s checklist has the 13 rows in his order', () => {
  assert.deepEqual(GOC_CHECKLIST_ROWS.map((r) => r.en), [
    'Patient details', 'Caregiver details', 'Patient capacity', 'Existing medical directives', "Patient's understanding of disease", 'Patient values', 'Goals of care',
    'Home care vs hospital transfer', 'CPR preference', 'ICU care', 'Ventilatory support', 'Dialysis preference', 'Nutritional support',
  ]);
  assert.deepEqual(GOC_CHECKLIST_ROWS.map((r) => r.section), ['I', 'I', 'I', 'I', 'II', 'II', 'III', 'III', 'III', 'III', 'III', 'III', 'III']);
  assert.equal(GOC_GUIDE_TEXT.length, 13);
  assert.equal(gocChecklistTitle(GOC_CHECKLIST_ROWS[8], 'hi'), 'CPR की पसंद');
});

test('a row is ticked when any of its guide items is covered', () => {
  const list = gocChecklist(rowsFor('Doctor: Do you want a feeding tube? Patient: No. Doctor: What about the ambulance and the hospital?'));
  const done = (id) => list.find((r) => r.id === id).discussed;
  assert.ok(done('nutrition'), 'NG/PEG/feeding tube ticks Nutritional support');
  assert.ok(done('homeVsHospital'), 'hospital or ambulance ticks Home care vs hospital transfer');
  assert.ok(!done('cpr') && !done('dialysis') && !done('capacity'));
  assert.equal(gocChecklistDone(list), 2);
});

test('capacity and directive words tick their rows', () => {
  const list = gocChecklist(rowsFor('Doctor: Are you able to decide for yourself? Do you have a living will or any document? '));
  assert.ok(list.find((r) => r.id === 'capacity').discussed);
  assert.ok(list.find((r) => r.id === 'directives').discussed);
  assert.ok(gocChecklist(rowsFor('He was confused and not alert.')).find((r) => r.id === 'capacity').discussed);
  assert.ok(gocChecklist(rowsFor('उनकी वसीयत है')).find((r) => r.id === 'directives').discussed);
  assert.ok(!gocChecklist(rowsFor('No ICU.')).find((r) => r.id === 'directives').discussed);
});

test('the Not discussed list uses rows, taps tick rows, and a doctor verification removes a row', () => {
  const rows = rowsFor('No ICU.');
  const all = gocChecklistOpen(gocChecklist(rows));
  assert.equal(all.length, 12, 'only ICU care is covered');
  assert.ok(!all.some((r) => r.id === 'icu'));
  const tapped = gocChecklistOpen(gocChecklist(rows, [0, 8]));
  assert.ok(!tapped.some((r) => r.id === 'patientDetails' || r.id === 'cpr'));
  const verified = rowsFor('No ICU.', { cpr: { status: 'NOT_DISCUSSED', verifiedBy: 'Dr', verifiedAt: 'x' } });
  assert.ok(!gocChecklistOpen(gocChecklist(verified)).some((r) => r.id === 'cpr'));
  assert.deepEqual(gocNotDiscussedTitles(rows, 'en', [0]).slice(0, 2), ['Caregiver details', 'Patient capacity']);
  assert.equal(gocNotDiscussedTitles(rows, 'en').length, 12);
});

test('guide: tap starts a topic, tapping it again finishes it, and the previous one stays discussed', () => {
  let s = { stamps: {}, now: -1 };
  s = gocGuideTap(s, 4, 65);
  assert.equal(s.now, 4);
  assert.deepEqual([...gocGuideDiscussed(s)], [], 'the topic being discussed is not yet discussed');
  s = gocGuideTap(s, 8, 130);
  assert.deepEqual([...gocGuideDiscussed(s)], [4]);
  s = gocGuideTap(s, 8, 200);
  assert.equal(s.now, -1);
  assert.deepEqual([...gocGuideDiscussed(s)].sort(), [4, 8]);
  assert.equal(s.stamps[8], 130, 'the start time is kept');
  s = gocGuideTap(s, 8, 300);
  assert.equal(s.stamps[8], 130, 'tapping again does not restamp');
});

test('guide: Done stamps once, and Next skips discussed topics and wraps', () => {
  let s = gocGuideDone({ stamps: {}, now: 2 }, 2, 50);
  assert.deepEqual(s, { stamps: { 2: 50 }, now: -1 });
  s = gocGuideDone({ stamps: { 2: 10 }, now: -1 }, 2, 50);
  assert.equal(s.stamps[2], 10);
  assert.equal(gocGuideNext(new Set([1, 2]), 0, 13), 3);
  assert.equal(gocGuideNext(new Set([1]), 12, 13), 0, 'wraps round');
  assert.equal(gocGuideNext(new Set([...Array(13).keys()]), 3, 13), null);
  assert.equal(gocGuideNext(new Set(), 0, 0), null);
});

test('guide: essentials count k/5 and the out-of-order warning', () => {
  const titles = GOC_ROW_TITLES;
  const cprIdx = titles.indexOf('CPR preference'); const underIdx = titles.indexOf("Patient's understanding of disease");
  assert.equal(gocEssentialsDone([cprIdx, underIdx, 0], titles), 2);
  assert.equal(gocEssentialsDone([...titles.keys()], titles), 5);
  assert.ok(gocGuideOutOfOrder(cprIdx, new Set(), GOC_ROW_SECTIONS, titles), 'Section III before understanding warns');
  assert.ok(!gocGuideOutOfOrder(cprIdx, new Set([underIdx]), GOC_ROW_SECTIONS, titles));
  assert.ok(!gocGuideOutOfOrder(underIdx, new Set(), GOC_ROW_SECTIONS, titles), 'understanding itself never warns');
  assert.ok(!gocGuideOutOfOrder(0, new Set(), GOC_ROW_SECTIONS, titles), 'Section I does not warn');
});

test('guide: mm:ss stamps and the saved encoding', () => {
  assert.equal(gocMmss(65), '01:05');
  assert.equal(gocMmss(0), '00:00');
  assert.equal(gocMmss(-1), '--:--');
  const stamps = { 8: 130, 2: 5 };
  assert.equal(gocGuideEncode(stamps), '2:5;8:130');
  assert.deepEqual(gocGuideDecode('2:5;8:130;junk;x:y'), { 2: 5, 8: 130 });
});

test('a signed and locked talk starts a new version: the old one stays in history, the new one carries the note', () => {
  const locked = { lines: [{ text: 'No CPR.', speaker: null }], language: 'en', source: 'TYPED', machineDraft: false, consentAt: 't', reviewedBy: 'Dr', reviewedAt: 't', audioKept: false,
    accepted: [{ field: 'cpr', value: 'DNACPR', quote: 'No CPR.', at: 't' }], topics: {}, note: 'Signed note', signOff: { status: 'LOCKED' } };
  assert.ok(gocTalkLocked(locked));
  assert.equal(gocStartNewVersion({ ...locked, signOff: { status: 'OPEN' } }), null, 'refused while not locked');
  assert.equal(gocStartNewVersion(undefined), null);
  const next = gocStartNewVersion(locked);
  assert.equal(gocTalkVersion(next), 2);
  assert.equal(next.history.length, 1);
  assert.equal(next.history[0].version, 1);
  assert.equal(next.history[0].note, 'Signed note');
  assert.equal(next.history[0].signOff.status, 'LOCKED');
  assert.deepEqual(next.lines, []);
  assert.deepEqual(next.accepted, []);
  assert.equal(next.signOff, undefined);
  assert.equal(next.carriedNote, 'Signed note');
  assert.equal(next.note, '', 'the carried note is not mistaken for this talk’s own');
  assert.equal(gocTalkLocked(next), false);
  const third = gocStartNewVersion({ ...next, signOff: { status: 'LOCKED' }, note: 'v2' });
  assert.equal(gocTalkVersion(third), 3);
  assert.deepEqual(third.history.map((h) => h.version), [1, 2]);
  assert.equal(gocNoteSuffix(1), '');
  assert.equal(gocNoteSuffix(3), ':t3');
});
