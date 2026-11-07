import test from 'node:test';
import assert from 'node:assert/strict';
import { emptyEctpr, reviewReminderDue, daysUntilReview, REVIEW_REMINDER_DAYS } from '../app/ectpr-rules.ts';
import {
  deleteEctprDrafts, getEctprDistribution, listAudit, listEctprVersions, memoryEctprStorage, saveEctprDraft, saveEctprScan, setEctprCopy, setEctprStorage, signEctpr,
} from '../app/ectpr-store.ts';
import { episodeProblems, episodeRow, firstEdAccessAfter, hoursToSymptomRx, incidentIsEmpty, outcomeProblems, pilotSummary, placeMatchesPreference, planInForceAt, reachDays, valuesIndicator } from '../app/ectpr-pilot.ts';
import { boardReconsiderationNotice, edAmdLines } from '../app/ectpr-labels.ts';
import { ectprFhirBundle, ectprProvisions } from '../app/ectpr-fhir.ts';
import { pickVoice, speechChunks, speechLocale } from '../app/tts-voice.ts';
import { amdScanKeys, buildDataExport, dataExportFileName, dpdpNotice } from '../app/dpdp.ts';
import { withoutUnsignedAmdVersions, AMD_BOARD_PATHWAY } from '../app/amd-rules.ts';
import { reviewSmsProvider, sendReviewReminderSms, smsProvider } from '../app/api/ed-card/sms.ts';
import { POST as reminderPost } from '../app/api/care-plan-reminder/route.ts';

const NOW = new Date('2026-10-30T10:00:00');
const signature = { role: 'TREATING_CONSULTANT', name: 'Dr Test', registrationNo: 'REG-1', signedAt: '2026-10-20T10:00:00.000Z' };

function signed(patch = () => {}) {
  const r = emptyEctpr('P1', { name: 'Asha Test', uhid: 'UH-1', dob: '1950-01-01', sex: 'F' }, 'Dr Test', NOW);
  r.status = 'ACTIVE';
  r.reviewDue = '2027-01-21';
  r.signatures = [signature];
  patch(r);
  return r;
}
function episode(patch = {}) {
  return { id: 'e1', patientId: 'P1', ectprId: null, edArrivalAt: '2026-10-25T02:00:00.000Z', firstRecordAccessAt: '2026-10-25T02:08:00.000Z', highestSupportDelivered: null, icuAdmitted: null, vasopressorsGiven: null, dialysisGiven: null, cprGiven: null, discordanceReason: '', recordedBy: 'Dr', recordedAt: '2026-10-25T03:00:00.000Z', ...patch };
}

// Pilot indicators (clinical spec §8)

test('reach counts days from eligibility to the consultant signature; unknown until both exist', () => {
  assert.equal(reachDays(signed((r) => { r.clinical.eligibilityDate = '2026-10-10'; })), 10);
  assert.equal(reachDays(signed()), null);
  assert.equal(reachDays(emptyEctpr('P', { name: 'A' }, 'Dr', NOW)), null);
});

test('values indicator compares the values section with treatment fields, and flags limits without values', () => {
  const blank = valuesIndicator(signed());
  assert.equal(blank.valuesFilled, false);
  assert.equal(blank.treatmentFilled, 0);
  assert.equal(blank.limitsWithoutValues, false);
  const limits = valuesIndicator(signed((r) => { r.choices.cpr = 'DNACPR'; }));
  assert.equal(limits.treatmentFilled, 1);
  assert.equal(limits.limitsWithoutValues, true);
  assert.equal(valuesIndicator(signed((r) => { r.choices.cpr = 'DNACPR'; r.values.ownWords = 'At home'; })).limitsWithoutValues, false);
});

test('an episode is compared with the version signed before arrival, not a later one', () => {
  const v1 = signed((r) => { r.id = 'v1'; r.choices.respCeiling = 5; });
  const v2 = signed((r) => { r.id = 'v2'; r.choices.respCeiling = 1; r.signatures = [{ ...signature, signedAt: '2026-10-28T10:00:00.000Z' }]; });
  assert.equal(planInForceAt([v2, v1], '2026-10-25T02:00:00.000Z').id, 'v1');
  assert.equal(planInForceAt([v2, v1], '2026-10-29T02:00:00.000Z').id, 'v2');
  assert.equal(planInForceAt([v1], '2026-10-01T02:00:00.000Z'), null);
  const cancelled = signed((r) => { r.revokedAt = '2026-10-22T00:00:00.000Z'; });
  assert.equal(planInForceAt([cancelled], '2026-10-25T00:00:00.000Z'), null);
  assert.equal(episodeRow(episode({ highestSupportDelivered: 4 }), [v2, v1]).concordant, true);
});

test('retrieval minutes and concordance appear on a row; the summary counts only what could be compared', () => {
  const plan = signed((r) => { r.choices.respCeiling = 2; r.choices.cpr = 'DNACPR'; });
  const ok = episodeRow(episode({ highestSupportDelivered: 2, cprGiven: false }), [plan]);
  assert.equal(ok.minutes, 8);
  assert.equal(ok.withinTarget, true);
  assert.equal(ok.concordant, true);
  const late = episodeRow(episode({ firstRecordAccessAt: '2026-10-25T02:30:00.000Z', highestSupportDelivered: 3 }), [plan]);
  assert.equal(late.withinTarget, false);
  assert.equal(late.concordant, false);
  const noPlan = episodeRow(episode({ firstRecordAccessAt: null }), []);
  assert.equal(noPlan.minutes, null);
  assert.equal(noPlan.concordant, null);
  assert.deepEqual(pilotSummary([ok, late, noPlan]), { episodes: 3, retrieved: 2, withinTarget: 1, compared: 2, concordant: 1 });
});

test('an episode needs an arrival, a sensible access time, and a reason when care differed from the plan', () => {
  const plan = signed((r) => { r.choices.cpr = 'DNACPR'; });
  assert.deepEqual(episodeProblems(episode({ edArrivalAt: '' }), plan), ['NO_ARRIVAL']);
  assert.deepEqual(episodeProblems(episode({ firstRecordAccessAt: '2026-10-25T01:00:00.000Z' }), plan), ['ACCESS_BEFORE_ARRIVAL']);
  assert.deepEqual(episodeProblems(episode({ cprGiven: true }), plan), ['REASON_NEEDED']);
  assert.deepEqual(episodeProblems(episode({ cprGiven: true, discordanceReason: 'Family asked at the bedside; reversible cause' }), plan), []);
  assert.deepEqual(episodeProblems(episode({ cprGiven: true }), null), []);
});

test('first ED access comes from the access log: clinic views do not count, and nothing before arrival does', () => {
  const log = [
    { at: '2026-10-25T01:00:00.000Z', action: 'VIEW', actor: 'ED (QR scan)' },
    { at: '2026-10-25T02:01:00.000Z', action: 'VIEW', actor: 'Dr Sujay' },
    { at: '2026-10-25T02:09:00.000Z', action: 'BREAK_GLASS', actor: 'ED (break-glass)' },
    { at: '2026-10-25T02:05:00.000Z', action: 'VIEW', actor: 'ED (QR scan)' },
  ];
  assert.equal(firstEdAccessAfter(log, '2026-10-25T02:00:00.000Z'), '2026-10-25T02:05:00.000Z');
  assert.equal(firstEdAccessAfter(log, 'not a date'), null);
});

test('outcome indicators and checks', () => {
  assert.equal(placeMatchesPreference({ placeOfDeath: 'HOME', preferredPlaceOfDeath: 'HOME' }), true);
  assert.equal(placeMatchesPreference({ placeOfDeath: 'ICU', preferredPlaceOfDeath: 'HOME' }), false);
  assert.equal(placeMatchesPreference({ placeOfDeath: null, preferredPlaceOfDeath: 'HOME' }), null);
  assert.equal(hoursToSymptomRx({ lastAdmissionAt: '2026-10-25T02:00:00.000Z', firstSymptomControlRxAt: '2026-10-25T03:30:00.000Z' }), 1.5);
  assert.equal(hoursToSymptomRx({ lastAdmissionAt: null, firstSymptomControlRxAt: '2026-10-25T03:30:00.000Z' }), null);
  const blank = { placeOfDeath: null, preferredPlaceOfDeath: null, icuDaysLast30: null, ventilatorDaysLast30: null, lastAdmissionAt: null, firstSymptomControlRxAt: null, outOfPocketLastAdmissionInr: null };
  assert.deepEqual(outcomeProblems(blank), ['NOTHING_ENTERED']);
  assert.deepEqual(outcomeProblems({ ...blank, icuDaysLast30: 31 }), ['DAYS_OUT_OF_RANGE']);
  assert.deepEqual(outcomeProblems({ ...blank, icuDaysLast30: 0 }), []);
  assert.deepEqual(outcomeProblems({ ...blank, lastAdmissionAt: '2026-10-25T03:00:00.000Z', firstSymptomControlRxAt: '2026-10-25T02:00:00.000Z' }), ['RX_BEFORE_ADMISSION']);
  assert.deepEqual(outcomeProblems({ ...blank, outOfPocketLastAdmissionInr: -1 }), ['NEGATIVE_SPEND']);
  assert.equal(incidentIsEmpty({ appliedToReversibleIllness: false, familyFeltPushed: false, details: '  ' }), true);
  assert.equal(incidentIsEmpty({ appliedToReversibleIllness: false, familyFeltPushed: true, details: '' }), false);
});

// Advance care planning on the ED view (tech spec §1, §6)

const executed = { status: 'EXECUTED', executedOn: '2026-09-01', version: 2, attestor: 'Notary: A. Rao' };

test('an executed AMD shows date, version and attestor, and says the AMD governs', () => {
  const { lines, governs } = edAmdLines({ kind: 'NONE' }, executed, 'en');
  assert.equal(governs, true);
  assert.match(lines[0], /version 2/);
  assert.match(lines[0], /Sept? 2026/);
  assert.match(lines[0], /Notary: A\. Rao/);
  assert.equal(lines.at(-1), 'The AMD governs over this plan.');
  assert.ok(!lines.some((l) => l.includes('No advance care plan')), 'the form saying NONE must not contradict an executed AMD');
});

test('a gazetted officer attestor is labelled, and Hindi is used when asked', () => {
  const { lines } = edAmdLines(null, { ...executed, attestor: 'Gazetted officer: B. Das' }, 'hi');
  assert.match(lines[0], /राजपत्रित अधिकारी: B\. Das/);
});

test('an unsigned or cancelled AMD never counts as governing', () => {
  for (const status of ['DRAFT', 'GENERATED']) {
    const view = edAmdLines({ kind: 'NONE' }, { status, executedOn: null, version: 1, attestor: null }, 'en');
    assert.equal(view.governs, false, status);
    assert.match(view.lines[0], /not valid/);
  }
  const cancelled = edAmdLines({ kind: 'VALID_AMD', date: '2026-01-01', location: 'Home', attestor: 'Notary' }, { status: 'REVOKED', executedOn: '2026-01-01', version: 1, attestor: 'Notary: X' }, 'en');
  assert.equal(cancelled.governs, false);
  assert.match(cancelled.lines[0], /cancelled in writing/);
  assert.match(cancelled.lines[1], /Check with the decision-maker/);
});

test('with no AMD in Sahara, the form decides; a valid paper AMD still governs', () => {
  assert.equal(edAmdLines({ kind: 'NONE' }, null, 'en').governs, false);
  const paper = edAmdLines({ kind: 'VALID_AMD', date: '2026-01-01', location: 'Home', attestor: 'Notary' }, undefined, 'en');
  assert.equal(paper.governs, true);
  assert.equal(paper.lines.at(-1), 'The AMD governs over this plan.');
});

// 30-day reconsideration (Harish Rana 2026 INSC 222 para 322(vi))

test('the board notice carries the 30-day reconsideration period, as the AMD pathway does', () => {
  assert.match(boardReconsiderationNotice('en'), /30 days/);
  assert.match(boardReconsiderationNotice('en'), /para 322\(vi\)/);
  assert.match(boardReconsiderationNotice('hi'), /30 दिन/);
  assert.ok(AMD_BOARD_PATHWAY.some((step) => step.en.includes('30-day')));
});

// Read-aloud language handling

test('speech uses the chosen language and never a wrong-language voice', () => {
  const voices = [{ lang: 'en-US', name: 'Microsoft David' }, { lang: 'hi_IN', name: 'Hindi plain' }, { lang: 'hi-IN', name: 'Google हिन्दी' }];
  assert.equal(pickVoice(voices, 'hi').name, 'Google हिन्दी');
  assert.equal(pickVoice(voices, 'te'), null);
  assert.equal(pickVoice(voices, 'en').name, 'Microsoft David');
  assert.equal(pickVoice([{ lang: 'de-DE', name: 'Other' }], 'en').name, 'Other');
  assert.equal(speechLocale('te'), 'te-IN');
  assert.equal(speechLocale('ur'), 'ur-IN');
});

test('a sentence not translated yet is read in English, not by the language voice', () => {
  assert.deepEqual(speechChunks('నమస్తే. This is not translated. మరొకటి', 'te').map((c) => c.lang), ['te', 'en', 'te']);
  assert.deepEqual(speechChunks('नमस्ते। CPR: कोई पसंद दर्ज नहीं।', 'hi').map((c) => c.lang), ['hi']);
  assert.deepEqual(speechChunks('One.\n\nTwo', 'en'), [{ text: 'One. Two', lang: 'en' }]);
  assert.deepEqual(speechChunks('  # * \n', 'en'), []);
  assert.ok(speechChunks('Sentence one is here. '.repeat(30), 'en').every((c) => c.text.length <= 240), 'long text stays in short pieces');
});

// DPDP notice, download and delete

test('the notice covers what, why, where, rights and how to ask, in Hindi too, for both screens', () => {
  for (const scope of ['care-plan', 'living-will']) {
    const blocks = dpdpNotice(scope);
    assert.deepEqual(blocks.map((b) => b.titleEn), scope === 'care-plan' ? ['What we keep', 'Why', 'Where it is', 'A recorded conversation', 'Your rights', 'How to ask'] : ['What we keep', 'Why', 'Where it is', 'Your rights', 'How to ask']);
    assert.ok(blocks.every((b) => b.hi && b.titleHi && b.en));
    const text = blocks.map((b) => b.en).join(' ');
    for (const word of ['device', 'download', 'Correct', 'Withdraw', 'cannot be deleted']) assert.ok(text.includes(word), `${scope}: ${word}`);
  }
});

test('download bundle holds everything and leaves card secrets out; file name has no spaces', () => {
  const out = buildDataExport({ patientId: 'P 1', ectprVersions: [signed()], distribution: [], audit: [], episodes: [], outcomes: [], incidents: [], amdVersions: [], scans: {}, cards: [{ issuedAt: 'x', version: 1, server: 'issued' }] }, NOW);
  assert.equal(out.ectprVersions.length, 1);
  assert.equal(out.format, 'Sahara personal data export');
  assert.ok(!JSON.stringify(out).includes('manageKey'));
  assert.match(dataExportFileName('CANCER 20418/x', NOW), /^sahara-my-data-CANCER-20418-x-\d{4}-\d{2}-\d{2}\.json$/);
  assert.deepEqual(amdScanKeys([{ execution: { scanKey: 'a' }, revocation: { scanKey: 'b' } }, { execution: { scanKey: 'a' } }]), ['a', 'b']);
});

test('delete removes unsigned drafts only; signed versions, signed drafts and the audit log stay, and the deletion is logged', async () => {
  setEctprStorage(memoryEctprStorage(), null);
  const base = emptyEctpr('P9', { name: 'Asha', uhid: 'UH-9', dob: '1950-01-01' }, 'Dr', NOW);
  base.reviewDue = '2027-01-21'; base.capacity.status = 'HAS';
  assert.equal((await saveEctprDraft(base, NOW)).ok, true);
  const beforeLog = (await listAudit('P9')).length;
  assert.deepEqual(await deleteEctprDrafts('P9', 'Asha', NOW), { deleted: 1, kept: 0 });
  assert.equal((await listEctprVersions('P9')).length, 0);
  const log = await listAudit('P9');
  assert.equal(log.length, beforeLog + 1);
  assert.equal(log.at(-1).action, 'DELETE');
  assert.ok(!log.at(-1).detail.includes('Asha'), 'the log entry holds no deleted content');

  const withSign = { ...emptyEctpr('P9', { name: 'Asha', uhid: 'UH-9', dob: '1950-01-01' }, 'Dr', NOW), id: 'kept-draft', reviewDue: '2027-01-21' };
  await saveEctprDraft(withSign, NOW);
  await signEctpr('kept-draft', { role: 'PATIENT', name: 'Asha', signedAt: NOW.toISOString() }, NOW);
  assert.deepEqual(await deleteEctprDrafts('P9', 'Asha', NOW), { deleted: 0, kept: 1 });
  assert.equal((await listEctprVersions('P9')).length, 1);
  assert.deepEqual(await deleteEctprDrafts('nobody', 'x', NOW), { deleted: 0, kept: 0 });
});

test('only unsigned advance care plan versions can be removed', () => {
  const versions = ['DRAFT', 'GENERATED', 'EXECUTED', 'SUPERSEDED', 'REVOKED'].map((status) => ({ id: status, status }));
  const { kept, removed } = withoutUnsignedAmdVersions(versions);
  assert.deepEqual(removed.map((v) => v.id), ['DRAFT', 'GENERATED']);
  assert.deepEqual(kept.map((v) => v.id), ['EXECUTED', 'SUPERSEDED', 'REVOKED']);
});

// Signed paper and copies for the plan

test('a signed plan keeps a hashed scan and a copy checklist, each logged; a draft cannot take a scan', async () => {
  setEctprStorage(memoryEctprStorage(), null);
  const record = signed((r) => { r.id = 'plan-1'; });
  const file = { name: 'signed.pdf', type: 'application/pdf', arrayBuffer: async () => new TextEncoder().encode('abc').buffer };
  const saved = await saveEctprScan(record, file, 'Dr', NOW);
  assert.equal(saved.scan.scanSha256, 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
  await setEctprCopy(record, 'HOME', true, 'Dr', NOW);
  await setEctprCopy(record, 'FRONT_DOOR', true, 'Dr', NOW);
  await setEctprCopy(record, 'HOME', false, 'Dr', NOW);
  const after = await getEctprDistribution(record);
  assert.equal(after.copies.find((c) => c.kind === 'HOME').given, false);
  assert.equal(after.copies.find((c) => c.kind === 'FRONT_DOOR').given, true);
  assert.equal(after.scan.scanName, 'signed.pdf');
  assert.deepEqual((await listAudit('P1')).map((e) => e.action), ['UPLOAD', 'SHARE', 'SHARE', 'SHARE']);
  await assert.rejects(saveEctprScan({ ...record, status: 'DRAFT' }, file, 'Dr', NOW));
});

// Review reminder

test('the reminder shows inside 14 days and when overdue, for the plan in use only', () => {
  assert.equal(REVIEW_REMINDER_DAYS, 14);
  const on = (due, status = 'ACTIVE') => reviewReminderDue(signed((r) => { r.reviewDue = due; r.status = status; }), NOW);
  assert.equal(on('2026-11-13'), true);
  assert.equal(on('2026-11-14'), false);
  assert.equal(on('2026-10-01'), true);
  assert.equal(on('2026-11-01', 'SUPERSEDED'), false);
  assert.equal(on(''), false);
  assert.equal(daysUntilReview(signed((r) => { r.reviewDue = '2026-10-30'; }), NOW), 0);
});

test('review SMS says it is not configured when no provider is set, and never uses the break-glass template', async () => {
  assert.equal(smsProvider({}), null);
  const none = await sendReviewReminderSms({ phone: '9876543210', patientFirstName: 'Asha', reviewDue: '2026-11-10' }, {});
  assert.deepEqual(none, { sent: false, provider: null, status: 'not-configured' });
  assert.equal(reviewSmsProvider({ MSG91_AUTH_KEY: 'k', MSG91_TEMPLATE_ID: 'break-glass' }), null);
  assert.equal(reviewSmsProvider({ MSG91_AUTH_KEY: 'k', MSG91_REVIEW_TEMPLATE_ID: 'review' }), 'msg91');
  assert.equal(reviewSmsProvider({ TWILIO_ACCOUNT_SID: 'a', TWILIO_AUTH_TOKEN: 'b', TWILIO_FROM: 'c' }), 'twilio');
});

test('review SMS sends one fixed message through the provider', async () => {
  let sent;
  const env = { TWILIO_ACCOUNT_SID: 'a', TWILIO_AUTH_TOKEN: 'b', TWILIO_FROM: '+15550000000' };
  const fetcher = async (url, init) => { sent = { url, body: init.body }; return new Response('{}', { status: 201 }); };
  const result = await sendReviewReminderSms({ phone: '98765 43210', patientFirstName: 'Asha', reviewDue: '2026-11-10' }, env, fetcher);
  assert.deepEqual(result, { sent: true, provider: 'twilio', status: 'sent' });
  const body = new URLSearchParams(sent.body);
  assert.equal(body.get('To'), '+919876543210');
  assert.match(body.get('Body'), /Asha/);
  assert.match(body.get('Body'), /10 Nov 2026/);
  assert.deepEqual(await sendReviewReminderSms({ phone: '123', patientFirstName: 'A', reviewDue: '2026-11-10' }, env, fetcher), { sent: false, provider: 'twilio', status: 'no-valid-number' });
});

const reminder = (body, headers = {}) => reminderPost(new Request('http://localhost/api/care-plan-reminder', { method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: JSON.stringify(body) }));

test('reminder route: reports status, refuses bad input and other sites, is honest without a provider, and limits repeat sends', async () => {
  const saved = { ...process.env };
  const realFetch = globalThis.fetch;
  for (const key of ['TWILIO_ACCOUNT_SID', 'TWILIO_AUTH_TOKEN', 'TWILIO_FROM', 'MSG91_AUTH_KEY', 'MSG91_REVIEW_TEMPLATE_ID']) delete process.env[key];
  try {
    assert.deepEqual(await (await reminder({ action: 'status' })).json(), { status: 'ok', configured: false });
    const good = { action: 'send', phone: '9876543210', patientFirstName: 'Asha', reviewDue: '2026-11-10' };
    assert.equal((await (await reminder(good)).json()).status, 'not-configured');
    assert.equal((await reminder({ ...good, reviewDue: 'soon' })).status, 400);
    assert.equal((await reminder({ ...good, phone: '12' })).status, 400);
    assert.equal((await reminder(good, { Origin: 'https://elsewhere.example' })).status, 403);
    assert.equal((await reminder({ action: 'other' })).status, 400);

    process.env.TWILIO_ACCOUNT_SID = 'a'; process.env.TWILIO_AUTH_TOKEN = 'b'; process.env.TWILIO_FROM = '+15550000000';
    globalThis.fetch = async () => new Response('{}', { status: 201 });
    assert.equal((await (await reminder({ action: 'status' })).json()).configured, true);
    for (let i = 0; i < 3; i++) assert.equal((await (await reminder(good)).json()).status, 'sent');
    assert.equal((await reminder(good)).status, 429);
  } finally {
    globalThis.fetch = realFetch;
    for (const key of ['TWILIO_ACCOUNT_SID', 'TWILIO_AUTH_TOKEN', 'TWILIO_FROM']) delete process.env[key];
    Object.assign(process.env, saved);
  }
});

// FHIR R4 draft export

test('FHIR: a signed plan becomes a Consent with scope adr; a draft exports nothing', () => {
  assert.equal(ectprFhirBundle(emptyEctpr('P', { name: 'A' }, 'Dr', NOW), NOW), null);
  const bundle = ectprFhirBundle(signed((r) => { r.choices.cpr = 'DNACPR'; r.choices.respCeiling = 3; }), NOW);
  assert.equal(bundle.resourceType, 'Bundle');
  assert.match(bundle.meta.tag[0].display, /Not validated against ABDM/);
  const consent = bundle.entry.map((e) => e.resource).find((r) => r.resourceType === 'Consent');
  assert.equal(consent.status, 'active');
  assert.equal(consent.scope.coding[0].code, 'adr');
  assert.equal(consent.scope.coding[0].system, 'http://terminology.hl7.org/CodeSystem/consentscope');
  assert.ok(consent.patient.reference, 'FHIR invariant: scope adr needs a patient');
  assert.ok(consent.policyRule, 'FHIR invariant: policy or policyRule');
  assert.equal(consent.category[0].coding[0].code, 'acd');
  assert.equal(consent.category[1].coding[0].code, 'dnr');
  assert.equal(consent.dateTime, signature.signedAt);
  assert.equal(consent.provision.period.end, '2027-01-21');
  assert.match(consent.policyRule.text, /not an advance care plan/);
  assert.equal(bundle.entry[0].resource.gender, 'female');
  assert.ok(!JSON.stringify(bundle).includes('ABHA'), 'no invented ABHA');
  for (const ref of [consent.patient.reference, consent.performer[0].reference]) assert.ok(bundle.entry.some((e) => e.fullUrl === ref), `${ref} resolves in the Bundle`);
});

test('FHIR: unrecorded choices produce no provision, so a blank is never a refusal (V1)', () => {
  assert.deepEqual(ectprProvisions(signed()), []);
  const provisions = ectprProvisions(signed((r) => { r.choices.cpr = 'ATTEMPT'; r.choices.icu = { kind: 'NO' }; }));
  assert.deepEqual(provisions.map((p) => [p.code[0].coding[0].code, p.type]), [['CPR_ATTEMPT', 'permit'], ['ICU_NO', 'deny']]);
});

test('FHIR: superseded and cancelled versions are inactive', () => {
  for (const status of ['SUPERSEDED', 'REVOKED']) {
    const bundle = ectprFhirBundle(signed((r) => { r.status = status; }), NOW);
    assert.equal(bundle.entry.find((e) => e.resource.resourceType === 'Consent').resource.status, 'inactive');
  }
});
