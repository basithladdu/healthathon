import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  AMD_BOARD_PATHWAY, AMD_ED_CAVEAT, ageOn, amdContentHash, amdStatusSummary, currentExecuted, emptyAmdContent, emptyWitness, markExecuted,
  markGenerated, revokeAmd, seedCopies, startAmdVersion, toLast4, updateAbha, updateAmdDraft, updateCopy, validateAmdContent, validateExecution,
} from '../app/amd-rules.ts';

const now = new Date('2026-10-29T10:00:00Z');

function validContent() {
  const content = emptyAmdContent('Lakshmi Devi');
  Object.assign(content.executant, { dob: '1958-03-14', idType: 'AADHAAR', idLast4: '4821', permanentAddress: 'Vijayawada' });
  Object.assign(content.declarations, { capacity: true, consideredCarefully: true, voluntary: true, understandsNotAppliedIfUnclear: true });
  content.conditions = ['TERMINAL'];
  content.treatments.VENTILATION = 'REFUSE';
  content.treatments.TUBE_FEEDING = 'REFUSE';
  content.dhrs = [
    { ...content.dhrs[0], name: 'Ravi Kumar', relation: 'Son', phone: '9848012345', agreed: true },
    { ...content.dhrs[0], id: 'dhr-b', name: 'Padma Rao', relation: 'Daughter', phone: '9989054321', agreed: true },
  ];
  content.custodianBody = 'Vijayawada Municipal Corporation';
  return content;
}

function execution(overrides = {}) {
  return {
    signedOn: '2026-10-29', place: 'Vijayawada', thumbImpression: false,
    witnesses: [{ ...emptyWitness(), name: 'Suresh Babu' }, { ...emptyWitness(), name: 'Meena Iyer' }],
    attestor: { kind: 'NOTARY', name: 'K. Raghavan', designation: '', registrationNo: 'N-123' },
    scanKey: 'scan-1', scanName: 'signed.pdf', scanType: 'application/pdf', scanSha256: 'a'.repeat(64), scanUploadedAt: now.toISOString(),
    ...overrides,
  };
}

const pending = { status: 'PENDING', body: 'Vijayawada Municipal Corporation', officer: '', acknowledgedOn: '' };
const ids = (issues) => issues.map((issue) => issue.id);

function generatedVersion(content = validContent()) {
  const started = startAmdVersion([], 'p1', 'patient', now, 'Lakshmi Devi');
  const saved = updateAmdDraft(started.versions, started.version.id, content, now);
  return markGenerated(saved.versions, saved.version.id, { pdfSha256: 'b'.repeat(64), contentSha256: 'c'.repeat(64), lang: 'en' }, now);
}

test('L1: an AMD needs an adult of sound mind; under 18 blocks', () => {
  const content = validContent();
  assert.deepEqual(ids(validateAmdContent(content, now).filter((i) => i.severity === 'block')), []);
  content.executant.dob = '2010-01-01';
  assert.ok(ids(validateAmdContent(content, now)).includes('L1_ADULT'));
  content.executant.dob = '2008-10-30'; // turns 18 tomorrow
  assert.ok(ids(validateAmdContent(content, now)).includes('L1_ADULT'));
  content.executant.dob = '2008-10-29'; // 18 today
  assert.ok(!ids(validateAmdContent(content, now)).includes('L1_ADULT'));
  content.declarations.capacity = false;
  assert.ok(ids(validateAmdContent(content, now)).includes('L1_CAPACITY'));
  assert.equal(ageOn('2026-02-30', now), null);
});

test('L1: an under-18 draft cannot be printed', () => {
  const content = validContent();
  content.executant.dob = '2012-05-05';
  const result = generatedVersion(content);
  assert.equal(result.ok, false);
  assert.ok(ids(result.issues).includes('L1_ADULT'));
});

test('L2: at least one representative required, warning below two, preference order kept', () => {
  const content = validContent();
  content.dhrs = [content.dhrs[1], content.dhrs[0]];
  assert.equal(seedCopies(content)[0].name, 'Padma Rao');
  content.dhrs = [content.dhrs[0]];
  const one = validateAmdContent(content, now);
  assert.ok(one.some((i) => i.id === 'L2_DHR_TWO' && i.severity === 'warn'));
  content.dhrs = [{ ...content.dhrs[0], name: '', relation: '', phone: '' }];
  assert.ok(validateAmdContent(content, now).some((i) => i.id === 'L2_DHR_REQUIRED' && i.severity === 'block'));
});

test('ID numbers: only the last 4 digits are ever kept', () => {
  assert.equal(toLast4('1234 5678 9012'), '9012');
  assert.equal(toLast4('ab12'), '12');
  const content = validContent();
  content.executant.idLast4 = '123456789012';
  assert.ok(ids(validateAmdContent(content, now)).includes('ID_LAST4'));
});

test('No euthanasia: wording asking to end life cannot be printed', () => {
  const content = validContent();
  content.values = 'Please give me a lethal injection.';
  assert.ok(validateAmdContent(content, now).some((i) => i.id === 'NO_EUTHANASIA' && i.severity === 'block'));
  content.values = 'I want to be at home with my family.';
  assert.ok(!ids(validateAmdContent(content, now)).includes('NO_EUTHANASIA'));
});

test('L9: comfort care and oral feeding cannot be switched off', () => {
  const content = validContent();
  content.comfortOralFeedingAlways = false;
  assert.ok(ids(validateAmdContent(content, now)).includes('L9_COMFORT'));
});

test('Lifecycle: DRAFT → GENERATED freezes content; only DRAFT can be edited', () => {
  const generated = generatedVersion();
  assert.equal(generated.ok, true);
  assert.equal(generated.version.status, 'GENERATED');
  const edit = updateAmdDraft(generated.versions, generated.version.id, emptyAmdContent(), now);
  assert.equal(edit.ok, false);
});

test('L3: stays GENERATED until scan, two witnesses and attestor are recorded', () => {
  const generated = generatedVersion();
  for (const bad of [
    execution({ scanSha256: '', scanKey: '' }),
    execution({ witnesses: [{ ...emptyWitness(), name: 'Suresh Babu' }, emptyWitness()] }),
    execution({ attestor: { kind: null, name: '', designation: '', registrationNo: '' } }),
  ]) {
    const result = markExecuted(generated.versions, generated.version.id, bad, pending, now);
    assert.equal(result.ok, false);
    assert.equal(result.versions.find((v) => v.id === generated.version.id).status, 'GENERATED');
  }
  const ok = markExecuted(generated.versions, generated.version.id, execution(), pending, now);
  assert.equal(ok.ok, true);
  assert.equal(ok.version.status, 'EXECUTED');
});

test('L3: a witness who is also a DHR is a warning; the executant cannot witness', () => {
  const generated = generatedVersion();
  const withDhr = execution({ witnesses: [{ ...emptyWitness(), name: 'Ravi Kumar' }, { ...emptyWitness(), name: 'Meena Iyer' }] });
  const issues = validateExecution(generated.version, withDhr, pending, now);
  assert.ok(issues.some((i) => i.id === 'L3_WITNESS_IS_DHR' && i.severity === 'warn'));
  assert.equal(markExecuted(generated.versions, generated.version.id, withDhr, pending, now).ok, true);
  const self = execution({ witnesses: [{ ...emptyWitness(), name: 'Lakshmi Devi' }, { ...emptyWitness(), name: 'Meena Iyer' }] });
  assert.ok(ids(validateExecution(generated.version, self, pending, now)).includes('WITNESS_SELF'));
});

test('L5: custodian acknowledgement or "custodian pending" is required', () => {
  const generated = generatedVersion();
  assert.ok(ids(validateExecution(generated.version, execution(), null, now)).includes('L5_CUSTODIAN'));
  const ack = { status: 'ACKNOWLEDGED', body: 'VMC', officer: '', acknowledgedOn: '' };
  assert.ok(ids(validateExecution(generated.version, execution(), ack, now)).includes('L5_CUSTODIAN_DETAIL'));
  assert.equal(markExecuted(generated.versions, generated.version.id, execution(), pending, now).ok, true);
});

test('L6: edits make a new version; latest executed is current; old one superseded on re-execution', () => {
  const generated = generatedVersion();
  const first = markExecuted(generated.versions, generated.version.id, execution(), pending, now);
  const next = startAmdVersion(first.versions, 'p1', 'patient', now);
  assert.equal(next.version.version, 2);
  assert.equal(next.version.status, 'DRAFT');
  assert.equal(currentExecuted(next.versions).version, 1);
  const changed = { ...next.version.content, treatments: { ...next.version.content.treatments, CPR: 'REFUSE' } };
  const saved = updateAmdDraft(next.versions, next.version.id, changed, now);
  const printed = markGenerated(saved.versions, saved.version.id, { pdfSha256: 'd'.repeat(64), contentSha256: 'e'.repeat(64), lang: 'te' }, now);
  assert.equal(currentExecuted(printed.versions).version, 1, 'printed but unsigned v2 does not replace v1');
  const second = markExecuted(printed.versions, printed.version.id, execution(), pending, now);
  assert.equal(currentExecuted(second.versions).version, 2);
  assert.equal(second.versions.find((v) => v.version === 1).status, 'SUPERSEDED');
  assert.deepEqual(amdStatusSummary(second.versions), { status: 'EXECUTED', executedOn: '2026-10-29', version: 2, attestor: 'Notary: K. Raghavan' });
});

test('L6: an unsigned printed version is set aside when a new version starts', () => {
  const generated = generatedVersion();
  const next = startAmdVersion(generated.versions, 'p1', 'patient', now);
  assert.equal(next.versions.find((v) => v.version === 1).status, 'SUPERSEDED');
});

test('L6: revoke needs a signed written cancellation and ends every open version', () => {
  const generated = generatedVersion();
  const executed = markExecuted(generated.versions, generated.version.id, execution(), pending, now);
  assert.equal(revokeAmd(executed.versions, { revokedOn: '2026-10-29', writtenRevocationSigned: false, reason: '' }, now).ok, false);
  const revoked = revokeAmd(executed.versions, { revokedOn: '2026-10-29', writtenRevocationSigned: true, reason: 'Changed my mind' }, now);
  assert.equal(revoked.ok, true);
  assert.equal(currentExecuted(revoked.versions), null);
  assert.equal(amdStatusSummary(revoked.versions).status, 'REVOKED');
  assert.equal(amdStatusSummary([]), null);
});

test('L4 + DPDP: copies per recipient need that recipient’s consent before being marked given', () => {
  const generated = generatedVersion();
  const executed = markExecuted(generated.versions, generated.version.id, execution(), pending, now);
  assert.deepEqual(executed.version.copies.map((c) => c.kind), ['DHR', 'DHR', 'CUSTODIAN']);
  const copy = executed.version.copies[0];
  assert.equal(updateCopy(executed.versions, executed.version.id, copy.id, { given: true }, now).ok, false);
  const consented = updateCopy(executed.versions, executed.version.id, copy.id, { consent: true }, now);
  const given = updateCopy(consented.versions, executed.version.id, copy.id, { given: true, method: 'IN_PERSON' }, now);
  assert.equal(given.ok, true);
  assert.equal(given.version.copies[0].givenOn, '2026-10-29');
  assert.equal(given.version.copies[1].consent, false, 'consent is per recipient');
  const withdrawn = updateCopy(given.versions, executed.version.id, copy.id, { consent: false }, now);
  assert.equal(withdrawn.version.copies[0].given, false);
});

test('L4: optional ABHA link needs consent and a valid address', () => {
  const generated = generatedVersion();
  const executed = markExecuted(generated.versions, generated.version.id, execution(), pending, now);
  assert.equal(updateAbha(executed.versions, executed.version.id, { address: 'lakshmi@abdm', consent: false }, now).ok, false);
  assert.equal(updateAbha(executed.versions, executed.version.id, { address: 'not an abha', consent: true }, now).ok, false);
  assert.equal(updateAbha(executed.versions, executed.version.id, { address: 'lakshmi@abdm', consent: true }, now).version.abha.address, 'lakshmi@abdm');
});

test('L7 and L8 texts: caveat wording; pathway is information only with no withdraw action', async () => {
  assert.equal(AMD_ED_CAVEAT, 'If the situation is ambiguous or not anticipated by the AMD, it is not applied.');
  const pathway = AMD_BOARD_PATHWAY.map((p) => p.en).join(' ');
  for (const phrase of ['Primary Medical Board', 'Secondary Medical Board', '48 hours', 'JMFC', '30-day', '12 months']) assert.ok(pathway.includes(phrase), phrase);
  const screen = await readFile(new URL('../app/amd-screen.tsx', import.meta.url), 'utf8');
  assert.ok(!/>\s*Withdraw|withdrawTreatment|onWithdraw/i.test(screen));
  assert.ok(!/euthanasia/i.test(screen.replace(/\/\/.*$/gm, '')), 'no euthanasia option in the UI');
});

test('Content hash is stable and changes with content', async () => {
  const a = generatedVersion().version;
  assert.equal(await amdContentHash(a), await amdContentHash(structuredClone(a)));
  const b = { ...a, content: { ...a.content, values: 'x' } };
  assert.notEqual(await amdContentHash(a), await amdContentHash(b));
});

test('Store: CREATE/PRINT/UPLOAD/REVOKE are audited and getCurrentAmdStatus reports the executed version', async () => {
  const store = await import('../app/amd-store.ts');
  const events = [];
  store.setAmdStorage(store.memoryAmdStorage(), async (event) => { events.push(event); });
  assert.equal(await store.getCurrentAmdStatus('p9'), null);
  const started = await store.startAmdDraft('p9', 'patient', 'Lakshmi Devi', now);
  await store.saveAmdDraft('p9', started.version.id, validContent(), 'patient', 'saved', now);
  await store.recordAmdGenerated('p9', started.version.id, { pdfSha256: 'b'.repeat(64), contentSha256: 'c'.repeat(64), lang: 'en' }, 'patient', now);
  const scan = await store.saveAmdScan('p9', new File([new Uint8Array([1, 2, 3])], 'signed.pdf', { type: 'application/pdf' }), 'patient');
  assert.equal(scan.scanSha256, '039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81');
  assert.equal((await store.getCurrentAmdStatus('p9')).status, 'GENERATED');
  const executed = await store.executeAmd('p9', started.version.id, execution({ ...scan }), pending, 'patient', now);
  assert.equal(executed.ok, true);
  assert.deepEqual(await store.getCurrentAmdStatus('p9'), { status: 'EXECUTED', executedOn: '2026-10-29', version: 1, attestor: 'Notary: K. Raghavan' });
  assert.equal(store.getCachedAmdStatus('p9').status, 'EXECUTED');
  await store.revokeAmdRecord('p9', { revokedOn: '2026-10-29', writtenRevocationSigned: true, reason: '' }, 'patient', now);
  assert.deepEqual(events.map((e) => e.action), ['CREATE', 'EDIT', 'PRINT', 'UPLOAD', 'UPLOAD', 'REVOKE']);
  assert.ok(events.every((e) => e.recordType === 'AMD' && e.patientId === 'p9'));
});

test('PDF: Vidhi sections, blank signature, two witnesses, attestation and page x of y', async () => {
  const { buildAmdPdf } = await import('../app/amd-pdf.ts');
  const generated = generatedVersion();
  const devanagari = new Uint8Array(await readFile(new URL('../public/fonts/NotoSansDevanagari.ttf', import.meta.url)));
  const telugu = new Uint8Array(await readFile(new URL('../public/fonts/NotoSansTelugu.ttf', import.meta.url)));
  const { bytes, sha256, pages } = await buildAmdPdf({ version: generated.version, lang: 'te', contentSha256: 'c'.repeat(64), printedAt: now }, { devanagari, telugu });
  assert.match(sha256, /^[0-9a-f]{64}$/);
  assert.ok(pages >= 5);
  const pdfjs = await import('pdfjs-dist/legacy/build/pdf.mjs');
  const doc = await pdfjs.getDocument({ data: bytes.slice(), useSystemFonts: false, disableFontFace: true, verbosity: 0 }).promise;
  let text = '';
  for (let i = 1; i <= doc.numPages; i++) text += (await (await doc.getPage(i)).getTextContent()).items.map((item) => item.str).join(' ') + '\n';
  for (const phrase of ['ADVANCE MEDICAL DIRECTIVE', 'Section 1: Personal details', 'Section 2: Directions relating to life-sustaining treatment', 'Section 4: Authorisation of the Designated Healthcare Representative', 'primary DHR', 'Declaration', 'Validity of directive', 'Signature of the executor', 'Witness 1', 'Witness 2', 'SIGNED BEFORE ME', 'APPROPRIATE AUTHORITY, STAMP', `Page 1 of ${pages}`, `Page ${pages} of ${pages}`, 'MA No. 1699/2019', 'Version 1']) {
    assert.ok(text.includes(phrase), phrase);
  }
  assert.ok(!text.includes('4821 ') || text.includes('ends 4821'), 'only the last 4 digits are printed');
});
