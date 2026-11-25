import test from 'node:test';
import assert from 'node:assert/strict';
import { inflateSync } from 'node:zlib';
import {
  GOC_SIGNOFF_SCHEME, GocSignOffError, gocAddSignature, gocBytesToDataUrl, gocCrc32, gocEncodeMonoPng, gocExportSize, gocLock, gocNextStep,
  gocNoteSha256, gocReconcile, gocRequiredSteps, gocRestart, gocSealInput, gocSealSha256, gocSha256Hex, gocStartSignOff, gocVerify,
  gocCleanReading, gocDecodeReading, gocReadingProblems, gocReadingSealParts, gocReadingSummary, gocRelationLabel, isGocSignOffRecord,
} from '../app/goc-signoff.ts';
import { loadGocSignOffs, memoryGocSignOffStorage, saveGocSignOff, setGocSignOffStorage } from '../app/goc-signoff-store.ts';

const NOTE = 'Sample note. Asha wants to be cared for at home. No CPR.';
const NOW = new Date('2026-11-04T10:00:00.000Z');
const png = async (seed = 1) => gocBytesToDataUrl(await gocEncodeMonoPng(8, 2, [seed & 1, 0, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 1, 0, 1]));
const doctor = async () => ({ role: 'DOCTOR', name: 'Dr Sample', registrationNo: 'KMC-123', consent: true, method: 'DRAWN', imageDataUrl: await png(1), strokeCount: 2 });
const patient = async (patch = {}) => ({ role: 'PATIENT', name: 'Asha Sample', canRead: true, consent: true, method: 'DRAWN', imageDataUrl: await png(0), strokeCount: 1, ...patch });
const witness = async () => ({ role: 'WITNESS', name: 'Nurse Sample', relation: 'Nurse', consent: true, method: 'DRAWN', imageDataUrl: await png(1), strokeCount: 1 });
const code = (c) => (e) => e instanceof GocSignOffError && e.code === c;

async function signed(patientPatch = {}) {
  let r = await gocStartSignOff('note-1', NOTE, NOW, 'id-1');
  r = await gocAddSignature(r, NOTE, await doctor(), undefined, NOW);
  r = await gocAddSignature(r, NOTE, await patient(patientPatch), undefined, NOW);
  return r;
}

test('SHA-256 matches the known vector and hashes the note exactly', async () => {
  assert.equal(await gocSha256Hex('abc'), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  assert.notEqual(await gocNoteSha256(NOTE), await gocNoteSha256(NOTE + ' '));
  const r = await gocStartSignOff('note-1', NOTE, NOW, 'id-1');
  assert.equal(r.scheme, GOC_SIGNOFF_SCHEME);
  assert.equal(r.noteSha256, await gocNoteSha256(NOTE));
});

test('signing goes in order: doctor, patient or family, then lock', async () => {
  const r0 = await gocStartSignOff('note-1', NOTE, NOW, 'id-1');
  assert.equal(gocNextStep(r0), 'DOCTOR');
  await assert.rejects(gocAddSignature(r0, NOTE, await patient()), code('WRONG_ORDER'));
  await assert.rejects(gocLock(r0, NOTE), code('NOT_READY'));
  const r1 = await gocAddSignature(r0, NOTE, await doctor(), undefined, NOW);
  assert.equal(gocNextStep(r1), 'PATIENT_OR_FAMILY');
  const r2 = await gocAddSignature(r1, NOTE, await patient(), undefined, NOW);
  assert.equal(gocNextStep(r2), 'LOCK');
  assert.equal(r2.signatures[1].relation, 'Self');
  assert.equal(r2.signatures[0].imageSha256.length, 64);
  assert.equal(r2.signatures[1].noteSha256, r0.noteSha256);
});

test('names, registration, relation, consent and reading ability are required', async () => {
  const r0 = await gocStartSignOff('note-1', NOTE, NOW);
  await assert.rejects(gocAddSignature(r0, NOTE, { ...await doctor(), name: '   ' }), code('NAME_BLANK'));
  await assert.rejects(gocAddSignature(r0, NOTE, { ...await doctor(), registrationNo: ' ' }), code('REGISTRATION_BLANK'));
  await assert.rejects(gocAddSignature(r0, NOTE, { ...await doctor(), imageDataUrl: 'data:image/png;base64,' }), code('NO_SIGNATURE'));
  const r1 = await gocAddSignature(r0, NOTE, await doctor());
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ name: '' })), code('NAME_BLANK'));
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ consent: false })), code('CONSENT_MISSING'));
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ canRead: null })), code('CAN_READ_MISSING'));
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ role: 'FAMILY', relation: ' ' })), code('RELATION_BLANK'));
  const family = await gocAddSignature(r1, NOTE, await patient({ role: 'FAMILY', relation: 'Daughter' }));
  assert.equal(family.signatures[1].relation, 'Daughter');
});

test('a thumb impression or a signer who cannot read needs a witness', async () => {
  const thumb = await signed({ method: 'THUMB' });
  assert.deepEqual(gocRequiredSteps(thumb), ['DOCTOR', 'PATIENT_OR_FAMILY', 'WITNESS']);
  assert.equal(gocNextStep(thumb), 'WITNESS');
  await assert.rejects(gocLock(thumb, NOTE), code('NOT_READY'));
  await assert.rejects(gocAddSignature(thumb, NOTE, { ...await witness(), consent: false }), code('CONSENT_MISSING'));
  const withWitness = await gocAddSignature(thumb, NOTE, await witness());
  assert.equal(gocNextStep(withWitness), 'LOCK');
  assert.equal(gocNextStep(await signed({ canRead: false })), 'WITNESS');
  assert.equal(gocNextStep(await signed()), 'LOCK');
  assert.deepEqual(gocRequiredSteps({ signatures: [] }, ['DOCTOR', 'PATIENT_OR_FAMILY', 'WITNESS']), ['DOCTOR', 'PATIENT_OR_FAMILY', 'WITNESS']);
});

test('lock seals the note and every PNG; nothing can be added after', async () => {
  const locked = await gocLock(await signed(), NOTE, undefined, NOW);
  assert.equal(locked.status, 'LOCKED');
  assert.equal(locked.lockedAt, NOW.toISOString());
  assert.equal(locked.sealSha256, await gocSealSha256(locked));
  assert.equal(gocNextStep(locked), null);
  await assert.rejects(gocAddSignature(locked, NOTE, await witness()), code('LOCKED'));
  await assert.rejects(gocLock(locked, NOTE), code('LOCKED'));
  await assert.rejects(gocRestart(locked, NOTE), code('LOCKED'));
  assert.deepEqual(await gocVerify(locked, NOTE), { ok: true, problems: [] });
});

test('verify catches a changed note, a changed image and a changed name', async () => {
  const locked = await gocLock(await signed(), NOTE);
  assert.deepEqual((await gocVerify(locked, NOTE + '!')).problems, ['NOTE_CHANGED']);
  const image = structuredClone(locked); image.signatures[0].imageDataUrl = await png(0);
  assert.ok((await gocVerify(image, NOTE)).problems.includes('IMAGE_CHANGED'));
  const name = structuredClone(locked); name.signatures[1].name = 'Someone else';
  assert.deepEqual((await gocVerify(name, NOTE)).problems, ['SEAL_CHANGED']);
  const open = await signed();
  assert.deepEqual(await gocVerify(open, NOTE), { ok: false, problems: ['SEAL_MISSING'] });
});

test('the seal input is length-prefixed so fields cannot run together', async () => {
  const r = await signed();
  const input = gocSealInput(r);
  assert.ok(input.startsWith(`${GOC_SIGNOFF_SCHEME.length}:${GOC_SIGNOFF_SCHEME};6:note-1;64:`));
  const a = structuredClone(r); a.signatures[0].name = 'Dr A'; a.signatures[0].registrationNo = 'B';
  const b = structuredClone(r); b.signatures[0].name = 'Dr AB'; b.signatures[0].registrationNo = '';
  assert.notEqual(gocSealInput(a), gocSealInput(b));
});

test('no signature can be added to a changed note', async () => {
  const r1 = await gocAddSignature(await gocStartSignOff('note-1', NOTE), NOTE, await doctor());
  await assert.rejects(gocAddSignature(r1, NOTE + ' Edited.', await patient()), code('NOTE_CHANGED'));
  await assert.rejects(gocLock(await signed(), NOTE + ' Edited.'), code('NOTE_CHANGED'));
});

test('changing the note after any signature invalidates all of them', async () => {
  const unsigned = await gocStartSignOff('note-1', NOTE, NOW, 'id-1');
  const moved = await gocReconcile(unsigned, NOTE + ' Edited.');
  assert.equal(moved.invalidated, null);
  assert.equal(moved.record.id, 'id-1');
  assert.equal(moved.record.noteSha256, await gocNoteSha256(NOTE + ' Edited.'));

  const same = await gocReconcile(await signed(), NOTE);
  assert.equal(same.invalidated, null);

  for (const before of [await signed(), await gocLock(await signed(), NOTE)]) {
    const result = await gocReconcile(before, NOTE + ' Edited.', NOW);
    assert.equal(result.invalidated.status, 'INVALIDATED');
    assert.equal(result.invalidated.signatures.length, 2);
    assert.match(result.invalidated.invalidatedReason, /no longer valid/);
    assert.equal(result.invalidated.invalidatedAt, NOW.toISOString());
    assert.equal(result.record.status, 'OPEN');
    assert.equal(result.record.signatures.length, 0);
    assert.notEqual(result.record.id, before.id);
    assert.equal(gocNextStep(result.invalidated), null);
    await assert.rejects(gocAddSignature(result.invalidated, NOTE + ' Edited.', await doctor()), code('INVALIDATED'));
    assert.ok((await gocVerify(result.invalidated, NOTE)).problems.includes('INVALIDATED'));
  }
});

test('restart keeps the earlier open signatures as invalidated', async () => {
  const result = await gocRestart(await signed(), NOTE, NOW);
  assert.equal(result.invalidated.status, 'INVALIDATED');
  assert.equal(result.record.signatures.length, 0);
});

test('the PNG is a valid 1-bit black and white image', async () => {
  const bytes = await gocEncodeMonoPng(10, 3, Array.from({ length: 30 }, (_, i) => i % 3 === 0));
  assert.deepEqual([...bytes.subarray(0, 8)], [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  assert.equal(view.getUint32(16), 10); assert.equal(view.getUint32(20), 3);
  assert.equal(bytes[24], 1); assert.equal(bytes[25], 0);
  assert.equal(view.getUint32(29), gocCrc32(bytes.subarray(12, 29)));
  const idatLength = view.getUint32(33);
  const rows = inflateSync(bytes.subarray(41, 41 + idatLength));
  assert.equal(rows.length, 3 * 3); // 3 rows of (1 filter byte + 2 bytes)
  assert.equal(rows[0], 0);
  assert.equal(rows[1], 0b01101101); // pixels 0,3,6 black (0), rest white (1)
  assert.equal(gocCrc32(new TextEncoder().encode('123456789')), 0xcbf43926);
  assert.deepEqual(gocExportSize(1200, 400), { width: 600, height: 200 });
  assert.deepEqual(gocExportSize(300, 100), { width: 300, height: 100 });
});

test('the store keeps every record on the device and refuses to change a locked one', async () => {
  setGocSignOffStorage(memoryGocSignOffStorage());
  const open = await signed();
  await saveGocSignOff(open);
  const locked = await gocLock(open, NOTE);
  await saveGocSignOff(locked);
  await assert.rejects(saveGocSignOff({ ...locked, signatures: [] }), /locked/);
  const { record, invalidated } = await gocReconcile(locked, NOTE + ' Edited.');
  await saveGocSignOff(invalidated);
  await saveGocSignOff(record);
  const all = await loadGocSignOffs('note-1');
  assert.deepEqual(all.map((r) => r.status), ['INVALIDATED', 'OPEN']);
  assert.equal((await loadGocSignOffs('other')).length, 0);
});

test('cross-platform vector: Android CareGocSignOffTest checks the same hashes', async () => {
  const text = 'Sample note. आशा wants to be cared for at home.';
  const img = 'data:image/png;base64,YWJj'; // the bytes "abc"
  let r = await gocStartSignOff('note-1', text, NOW, 'id-1');
  r = await gocAddSignature(r, text, { role: 'DOCTOR', name: 'Dr Sample', registrationNo: 'KMC-123', consent: true, method: 'DRAWN', imageDataUrl: img, strokeCount: 2 }, undefined, NOW);
  r = await gocAddSignature(r, text, { role: 'FAMILY', name: 'Ravi Sample', relation: 'Son', canRead: false, consent: true, method: 'THUMB', imageDataUrl: img, strokeCount: 1 }, undefined, NOW);
  r = await gocAddSignature(r, text, { role: 'WITNESS', name: 'Nurse Sample', relation: 'Nurse', consent: true, method: 'DRAWN', imageDataUrl: img, strokeCount: 3 }, undefined, NOW);
  r = await gocLock(r, text, undefined, NOW);
  assert.equal(r.noteSha256, '7e18e357acfbef1d4d20863c4607fb1854d60171e16541595918ac58bc6f8467');
  assert.equal(r.sealSha256, '230b8a152907bf0d012847342958d40f05e1964e368d0875dd31e999e17f1adc');
});

// ---------------------------------------------------------------------------
// Read aloud and thumb impressions, as the phone (CareGocSignOff.kt + ConsentReadAloud.kt) writes them.

const READ_PERSON = { method: 'PERSON', readerName: 'Ravi Sample', readerRelation: 'Son', language: '', witnessName: '', witnessRelation: '', at: '2026-11-04T09:58:00.000Z' };
const tEn = (en) => en;
const GOC_READ_ALOUD_VECTOR = 'ebb167bad91649a15036ec31d88aa20c7bab662be9cbd496b91a4fefcca43a25';

test('a reading is cleaned exactly like Android ConsentReadingRules.clean', () => {
  const c = gocCleanReading({ method: 'PERSON', readerName: '  Ravi \t  Sample ', readerRelation: ' Son ', language: 'hi', witnessName: ' Mohan ', witnessRelation: 'Neighbour', at: ' 2026-11-04T09:58:00.000Z ' });
  assert.deepEqual(c, { method: 'PERSON', readerName: 'Ravi Sample', readerRelation: 'Son', language: '', witnessName: 'Mohan', witnessRelation: 'Neighbour', at: '2026-11-04T09:58:00.000Z' });
  assert.deepEqual(gocCleanReading({ method: 'SELF', readerName: 'x', witnessName: 'y', language: 'hi' }), { method: 'SELF', readerName: '', readerRelation: '', language: '', witnessName: '', witnessRelation: '', at: '' });
  assert.equal(gocCleanReading({ method: 'PHONE', language: ' te ', readerName: 'x' }).language, 'te');
  assert.equal(gocCleanReading({ method: 'PERSON', readerName: 'a'.repeat(130) }).readerName.length, 120);
  // Kotlin trim() drops a no-break space but keeps U+FEFF; Java \s collapses only ASCII spaces.
  assert.equal(gocCleanReading({ method: 'PERSON', readerName: ' Ravi ' }).readerName, 'Ravi');
  assert.equal(gocCleanReading({ method: 'PERSON', readerName: '﻿Ravi' }).readerName, '﻿Ravi');
  assert.equal(gocCleanReading({ method: 'PERSON', readerName: 'Ravi  Sample' }).readerName, 'Ravi  Sample');
  assert.equal(gocDecodeReading({ method: 'LATER' }), null);
  assert.equal(gocDecodeReading(null), null);
  assert.equal(gocDecodeReading('PERSON'), null);
});

test('the seal adds the reading parts after the signature noteSha256, in the phone order', async () => {
  const plain = await signed();
  const read = structuredClone(plain); read.signatures[1].readAloud = READ_PERSON; read.signatures[1].canRead = false;
  const before = gocSealInput({ ...read, signatures: [read.signatures[0], { ...read.signatures[1], readAloud: null }] });
  assert.equal(gocSealInput(read), before + '9:readAloud;6:PERSON;11:Ravi Sample;3:Son;0:;0:;0:;24:2026-11-04T09:58:00.000Z;');
  assert.deepEqual(gocReadingSealParts(READ_PERSON), ['readAloud', 'PERSON', 'Ravi Sample', 'Son', '', '', '', '2026-11-04T09:58:00.000Z']);
  // No reading (null, missing, or an unknown method the phone would drop) leaves the seal as before.
  const noField = structuredClone(plain); for (const s of noField.signatures) delete s.readAloud;
  const unknown = structuredClone(plain); unknown.signatures[1].readAloud = { method: 'LATER', readerName: 'x' };
  assert.equal(gocSealInput(noField), gocSealInput(plain));
  assert.equal(gocSealInput(unknown), gocSealInput(plain));
});

test('a phone-made seal with a thumb impression and a reading verifies on the website', async () => {
  const text = 'Sample note. आशा wants to be cared for at home.';
  const img = 'data:image/png;base64,YWJj'; // the bytes "abc"
  const sha = await gocNoteSha256(text);
  const sig = (patch) => ({ registrationNo: null, relation: null, canRead: null, imageDataUrl: img, imageSha256: 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad', signedAt: NOW.toISOString(), noteSha256: sha, readAloud: null, ...patch });
  // JSON as GocSignOffJson.encode writes it (readAloud: null when not recorded).
  const phone = {
    scheme: GOC_SIGNOFF_SCHEME, id: 'id-1', noteId: 'note-1', noteSha256: sha, status: 'LOCKED', createdAt: NOW.toISOString(),
    signatures: [
      sig({ role: 'DOCTOR', name: 'Dr Sample', registrationNo: 'KMC-123', consent: true, method: 'DRAWN', strokeCount: 2 }),
      sig({ role: 'FAMILY', name: 'Asha Sample', relation: 'Daughter', consent: true, canRead: false, method: 'THUMB', strokeCount: 1, readAloud: READ_PERSON }),
      sig({ role: 'WITNESS', name: 'Nurse Sample', relation: 'Nurse', consent: true, method: 'DRAWN', strokeCount: 3 }),
    ],
    lockedAt: NOW.toISOString(), sealSha256: null, invalidatedAt: null, invalidatedReason: null,
  };
  phone.sealSha256 = await gocSealSha256(phone);
  const stored = JSON.parse(JSON.stringify(phone));
  assert.ok(isGocSignOffRecord(stored));
  assert.deepEqual(await gocVerify(stored, text), { ok: true, problems: [] });
  // Cross-platform vector: Android CareGocSignOffTest should get the same seal for this record.
  assert.equal(stored.sealSha256, GOC_READ_ALOUD_VECTOR);
  for (const change of [(r) => { r.signatures[1].readAloud.readerName = 'Someone else'; }, (r) => { r.signatures[1].readAloud = null; },
    (r) => { r.signatures[1].readAloud.at = '2026-11-05T09:58:00.000Z'; }, (r) => { r.signatures[1].method = 'DRAWN'; }]) {
    const tampered = structuredClone(stored); change(tampered);
    assert.deepEqual((await gocVerify(tampered, text)).problems, ['SEAL_CHANGED']);
  }
});

test('signing with a reading decides canRead and refuses an incomplete one', async () => {
  const r1 = await gocAddSignature(await gocStartSignOff('note-1', NOTE, NOW, 'id-1'), NOTE, await doctor(), undefined, NOW);
  const heard = await gocAddSignature(r1, NOTE, await patient({ canRead: true, method: 'THUMB', readAloud: { ...READ_PERSON, readerName: ' Ravi  Sample ', language: 'hi' } }), undefined, NOW);
  const s = heard.signatures[1];
  assert.equal(s.canRead, false);
  assert.equal(s.method, 'THUMB');
  assert.deepEqual(s.readAloud, READ_PERSON);
  assert.equal(gocNextStep(heard), 'WITNESS');
  const self = await gocAddSignature(r1, NOTE, await patient({ canRead: null, readAloud: { method: 'SELF', at: NOW.toISOString() } }), undefined, NOW);
  assert.equal(self.signatures[1].canRead, true);
  assert.equal(gocNextStep(self), 'LOCK');
  assert.equal((await gocAddSignature(r1, NOTE, await patient())).signatures[1].readAloud, null);
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ readAloud: { method: 'PERSON', readerName: '', readerRelation: 'Son' } })), code('READ_ALOUD_MISSING'));
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ readAloud: { method: 'PERSON', readerName: 'Ravi', readerRelation: ' ' } })), code('READ_ALOUD_MISSING'));
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ readAloud: { method: 'PERSON', readerName: ' asha  SAMPLE', readerRelation: 'Son' } })), code('READ_ALOUD_MISSING'));
  await assert.rejects(gocAddSignature(r1, NOTE, await patient({ readAloud: { method: 'LATER' } })), code('READ_ALOUD_MISSING'));
  assert.deepEqual(gocReadingProblems({ method: 'PHONE' }, 'Asha', true, false), ['NOT_HEARD', 'WITNESS_NAME', 'WITNESS_RELATION']);
  assert.deepEqual(gocReadingProblems({ method: 'PERSON', readerName: 'Ravi', readerRelation: 'Son', witnessName: 'ravi', witnessRelation: 'Son' }, 'Asha', true), ['WITNESS_IS_READER']);
});

test('the witness cannot be the person who read the note aloud (Android GocSignOffRules.add)', async () => {
  const r = await signed({ canRead: null, readAloud: READ_PERSON });
  assert.equal(gocNextStep(r), 'WITNESS');
  await assert.rejects(gocAddSignature(r, NOTE, { ...(await witness()), name: '  ravi   SAMPLE ' }, undefined, NOW), code('WITNESS_IS_READER'));
  assert.equal(gocNextStep(await gocAddSignature(r, NOTE, await witness(), undefined, NOW)), 'LOCK');
  const phone = await signed({ canRead: null, readAloud: { method: 'PHONE', language: 'hi', at: NOW.toISOString() } });
  assert.equal(gocNextStep(await gocAddSignature(phone, NOTE, { ...(await witness()), name: 'Ravi Sample' }, undefined, NOW)), 'LOCK');
});

test('the reading line reads like the phone one', () => {
  assert.equal(gocReadingSummary({ ...READ_PERSON, witnessName: 'Mohan', witnessRelation: 'Neighbour' }, tEn), 'Read aloud by Ravi Sample (Son) · witnessed by Mohan (Neighbour)');
  assert.equal(gocReadingSummary(READ_PERSON, tEn), 'Read aloud by Ravi Sample (Son)');
  assert.equal(gocReadingSummary({ method: 'PHONE', language: 'te', witnessName: 'Mohan', witnessRelation: '' }, tEn), 'Read aloud by the phone · తెలుగు · witnessed by Mohan');
  assert.equal(gocReadingSummary({ method: 'SELF' }, tEn), 'Read it themselves');
  assert.equal(gocReadingSummary(READ_PERSON, (_en, hi) => hi), 'पढ़कर सुनाया: Ravi Sample (बेटा)');
  assert.equal(gocRelationLabel(tEn, 'Cousin'), 'Cousin');
});
