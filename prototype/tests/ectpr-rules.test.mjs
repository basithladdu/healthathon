import test from 'node:test';
import assert from 'node:assert/strict';
import {
  ALWAYS_PROVIDED_CARE, DECISION_FIELDS, ROUTINE_REVIEW_WEEKS, TLT_DEFAULT_DAYS,
  blocksActivation, blocksSave, defaultTrialPlan, edInterpretation, emptyEctpr, nextReviewDue, noPreference,
  pageFooter, reviewTriggers, surrogateEntriesLocked, treatmentLocked, unrecordedDecisions, validateEctpr, boardPathwayRequired, capacityFlag,
} from '../app/ectpr-rules.ts';
import {
  ectprFromQuickView, getActiveEctpr, listAudit, listEctprVersions, listEpisodeLogs, listIncidentReports, listOutcomeRecords, logAudit,
  memoryEctprStorage, revokeEctpr, saveEctprDraft, saveEpisodeLog, saveIncidentReport, saveOutcomeRecord, setEctprStorage, signEctpr,
  startReview, subscribeEctpr, episodeConcordant, retrievalMinutes,
} from '../app/ectpr-store.ts';

const NOW = new Date('2026-10-29T10:00:00');
const consultant = { role: 'TREATING_CONSULTANT', name: 'Dr Test', registrationNo: 'REG-1', signedAt: '2026-10-29T10:00:00.000Z' };
const trial = { durationValue: 3, durationUnit: 'DAYS', reviewAt: '2026-11-01T09:00', reviewers: ['Dr Test'] };

/** A complete, valid, unsigned draft. */
function draft(patch = (r) => r) {
  const r = emptyEctpr('P1', { name: 'Test Patient', uhid: 'UH-1', dob: '1950-01-01' }, 'Dr Test', NOW);
  r.capacity.status = 'HAS';
  r.prognosticUnderstanding = { confirmed: true, confirmedBy: 'Dr Test', confirmedOn: '2026-10-29' };
  r.values.ownWords = 'I want to be at home.';
  r.values.financialConcernDiscussed = true;
  r.reviewDue = '2027-01-21';
  return patch(r) ?? r;
}
const ids = (issues) => issues.map((i) => i.id);
const has = (record, id) => ids(validateEctpr(record, NOW)).includes(id);

test('V1: a new record has nothing pre-selected; every field is "No preference recorded"', () => {
  const r = emptyEctpr('P1', { name: 'A' }, 'Dr', NOW);
  assert.equal(r.status, 'DRAFT');
  assert.deepEqual(unrecordedDecisions(r), [...DECISION_FIELDS]);
  for (const v of [r.choices.goalOfCare, r.choices.cpr, r.choices.respCeiling, r.choices.icu, r.choices.hospitalTransfer, r.choices.hydration]) assert.equal(noPreference(v), true);
  assert.equal(noPreference(0), false);
  assert.equal(noPreference(false), false);
  assert.ok(has(r, 'V1'));
});

test('acceptance: all unticked → no preference everywhere and the ED view says standard care', () => {
  const r = draft();
  r.signatures = [consultant];
  r.status = 'ACTIVE';
  const ed = edInterpretation(r, NOW);
  assert.equal(ed.state, 'ACTIVE');
  assert.equal(ed.standardCareApplies, true);
  assert.match(ed.banner, /standard care/i);
  assert.equal(blocksSave(validateEctpr(r, NOW)), false);
});

test('acceptance V2: Goal C + Attempt CPR cannot save without a reason', () => {
  const r = draft((x) => { x.choices.goalOfCare = 'C_COMFORT'; x.choices.cpr = 'ATTEMPT'; });
  const issues = validateEctpr(r, NOW);
  assert.ok(ids(issues).includes('V2'));
  assert.equal(blocksSave(issues), true);
  r.choices.cprConflictReason = 'Patient wishes, discussed at length';
  assert.equal(has(r, 'V2'), false);
  // ceiling below 5 also triggers V2
  assert.ok(has(draft((x) => { x.choices.respCeiling = 4; x.choices.cpr = 'ATTEMPT'; }), 'V2'));
  assert.equal(has(draft((x) => { x.choices.respCeiling = 5; x.choices.cpr = 'ATTEMPT'; }), 'V2'), false);
});

test('V3: Goal A with a ceiling below 5 or ICU No needs confirmation', () => {
  const r = draft((x) => { x.choices.goalOfCare = 'A_LIFE'; x.choices.icu = { kind: 'NO' }; });
  const v3 = validateEctpr(r, NOW).find((i) => i.id === 'V3');
  assert.equal(v3.severity, 'confirm');
  assert.equal(blocksSave([v3]), false);
  assert.equal(blocksActivation([v3]), true);
  r.choices.goalAConfirmed = true;
  assert.equal(has(r, 'V3'), false);
  assert.ok(has(draft((x) => { x.choices.goalOfCare = 'A_LIFE'; x.choices.respCeiling = 3; }), 'V3'));
});

test('V4: treatment fields locked until prognostic understanding is recorded', () => {
  const r = draft((x) => { x.prognosticUnderstanding.confirmed = false; });
  assert.equal(treatmentLocked(r), true);
  assert.equal(has(r, 'V4'), false);
  r.choices.cpr = 'DNACPR';
  assert.ok(has(r, 'V4'));
  assert.equal(blocksSave(validateEctpr(r, NOW)), true);
  r.prognosticUnderstanding.confirmed = true;
  assert.equal(treatmentLocked(r), false);
  assert.equal(has(r, 'V4'), false);
});

test('V5: surrogate entries locked until the substituted-judgement declaration', () => {
  const r = draft((x) => { x.surrogateDeclaration.answeringAsSurrogate = true; });
  assert.equal(surrogateEntriesLocked(r), true);
  const v5 = validateEctpr(r, NOW).find((i) => i.id === 'V5');
  assert.equal(v5.stage, 'save'); // values already entered
  r.surrogateDeclaration.confirmed = true;
  assert.equal(surrogateEntriesLocked(r), false);
  assert.equal(has(r, 'V5'), false);
});

test('V6: thumb impression or limited literacy needs two witnesses', () => {
  const r = draft((x) => { x.limitedLiteracy = true; x.signatures = [consultant]; });
  assert.ok(has(r, 'V6'));
  r.signatures.push({ role: 'WITNESS', name: 'W1', signedAt: '' });
  assert.ok(has(r, 'V6'));
  r.signatures.push({ role: 'WITNESS', name: 'W2', signedAt: '' });
  assert.equal(has(r, 'V6'), false);
  assert.ok(has(draft((x) => { x.signatures = [{ role: 'PATIENT', name: 'P', signedAt: '', thumbImpression: true }]; }), 'V6'));
});

test('V7: activation needs the consultant signature and registration number', () => {
  const r = draft();
  assert.ok(has(r, 'V7'));
  r.signatures = [{ ...consultant, registrationNo: '' }];
  assert.ok(has(r, 'V7'));
  r.signatures = [consultant];
  assert.equal(has(r, 'V7'), false);
  assert.equal(blocksActivation(validateEctpr(r, NOW)), false);
});

test('V8: page footer carries name, UHID, version, form version, print date and page x of y', () => {
  const footer = pageFooter(draft(), 1, 2, new Date('2026-10-29T12:00:00'));
  assert.equal(footer, 'Test Patient · UHID UH-1 · Version 1 · Sahara ECTPR v1.0 · Printed 2026-10-29 · Page 1 of 2');
  assert.ok(has(draft((x) => { x.patient.uhid = ''; }), 'V8'));
});

test('V9: always-provided care covers mouth care, hand feeding, symptom relief, hygiene, positioning, family, support, dignity', () => {
  const text = ALWAYS_PROVIDED_CARE.join(' ').toLowerCase();
  for (const word of ['mouth care', 'by hand', 'pain', 'breathlessness', 'hygiene', 'pressure', 'positioning', 'family', 'spiritual', 'dignity']) assert.ok(text.includes(word), word);
  assert.ok(Object.isFrozen(ALWAYS_PROVIDED_CARE) || Array.isArray(ALWAYS_PROVIDED_CARE));
});

test('acceptance L1: DOB under 18 blocks', () => {
  const r = draft((x) => { x.patient.dob = '2010-05-01'; });
  assert.ok(has(r, 'L1'));
  assert.equal(blocksSave(validateEctpr(r, NOW)), true);
  assert.equal(has(draft((x) => { x.patient.dob = '2008-10-29'; }), 'L1'), false); // 18 today
  assert.ok(has(draft((x) => { x.patient.dob = '2008-10-30'; }), 'L1')); // 18 tomorrow
});

test('C1: no transfer with ICU or ceiling ≥ 3 → confirm; home NIV reason allowed', () => {
  const r = draft((x) => { x.choices.hospitalTransfer = { kind: 'NO' }; x.choices.respCeiling = 4; });
  assert.equal(validateEctpr(r, NOW).find((i) => i.id === 'C1').severity, 'confirm');
  r.choices.ceilingConditions.homeNivReason = 'Home BiPAP for 2 years';
  assert.equal(has(r, 'C1'), false);
  const icu = draft((x) => { x.choices.hospitalTransfer = { kind: 'NO' }; x.choices.icu = { kind: 'YES' }; x.choices.ceilingConditions.homeNivReason = 'x'; });
  assert.ok(has(icu, 'C1'));
  icu.choices.conflictReasons.C1 = 'Reason';
  assert.equal(has(icu, 'C1'), false);
});

test('C2: Goal C with ICU, vasopressors or dialysis = Yes saves only with a reason', () => {
  for (const set of [(x) => { x.choices.icu = { kind: 'YES' }; }, (x) => { x.choices.vasopressors = { kind: 'YES_NO_LIMIT' }; }, (x) => { x.choices.dialysis = { kind: 'YES' }; }]) {
    const r = draft((x) => { x.choices.goalOfCare = 'C_COMFORT'; set(x); });
    assert.ok(has(r, 'C2'));
    assert.equal(blocksSave(validateEctpr(r, NOW)), true);
    r.choices.conflictReasons.C2 = 'Reason';
    assert.equal(has(r, 'C2'), false);
  }
});

test('C3: ceiling 5 or vasopressors yes with ICU No', () => {
  assert.ok(has(draft((x) => { x.choices.respCeiling = 5; x.choices.icu = { kind: 'NO' }; }), 'C3'));
  assert.ok(has(draft((x) => { x.choices.vasopressors = { kind: 'YES_MAX_AGENTS', maxAgents: 1 }; x.choices.icu = { kind: 'NO' }; }), 'C3'));
  assert.equal(has(draft((x) => { x.choices.respCeiling = 5; x.choices.icu = { kind: 'YES' }; }), 'C3'), false);
});

test('C4: every trial needs duration > 0, review date/time and reviewers', () => {
  const r = draft((x) => { x.choices.icu = { kind: 'TRIAL', trial: { durationValue: 0, durationUnit: 'DAYS', reviewAt: '', reviewers: [] } }; });
  assert.deepEqual(ids(validateEctpr(r, NOW)).filter((id) => id.startsWith('C4')), ['C4-icu-duration', 'C4-icu-review', 'C4-icu-reviewers']);
  assert.equal(blocksSave(validateEctpr(r, NOW)), true);
  r.choices.icu = { kind: 'TRIAL', trial };
  assert.equal(ids(validateEctpr(r, NOW)).some((id) => id.startsWith('C4')), false);
  assert.ok(has(draft((x) => { x.choices.vasopressors = { kind: 'TRIAL', trial: { ...trial, reviewers: [' '] } }; }), 'C4-vasopressors-reviewers'));
  assert.ok(has(draft((x) => { x.choices.dialysis = { kind: 'TRIAL', trial: { ...trial, durationValue: -1 } }; }), 'C4-dialysis-duration'));
  assert.ok(has(draft((x) => { x.choices.ceilingConditions.nivMaxHours = 0; }), 'C4-niv-duration'));
  const def = defaultTrialPlan(NOW);
  assert.equal(def.durationValue, TLT_DEFAULT_DAYS);
  assert.equal(TLT_DEFAULT_DAYS, 3);
});

test('C5: ICD with DNACPR or Goal C prompts a deactivation decision', () => {
  const r = draft((x) => { x.clinical.implantedDevice = 'ICD'; x.choices.goalOfCare = 'C_COMFORT'; });
  assert.ok(has(r, 'C5'));
  r.clinical.icdDeactivation = { decision: 'DEACTIVATE', date: '2026-10-30' };
  assert.equal(has(r, 'C5'), false);
  assert.equal(has(draft((x) => { x.clinical.implantedDevice = 'PACEMAKER'; x.choices.goalOfCare = 'C_COMFORT'; }), 'C5'), false);
});

test('C6: removing an existing tube flags the board pathway', () => {
  const r = draft((x) => { x.choices.feeding.removeExistingTube = true; });
  assert.equal(boardPathwayRequired(r), true);
  assert.equal(validateEctpr(r, NOW).find((i) => i.id === 'C6').severity, 'warn');
});

test('C7: fluctuating capacity requires a reassessment date and shows on the ED view', () => {
  const r = draft((x) => { x.capacity = { status: 'FLUCTUATES', assessor: 'Dr', assessedOn: '2026-10-29', basis: 'Four abilities', reassessOn: '' }; });
  assert.ok(has(r, 'C7'));
  r.capacity.reassessOn = '2026-11-05';
  assert.equal(has(r, 'C7'), false);
  assert.match(capacityFlag(r), /reassess/);
  r.status = 'ACTIVE'; r.signatures = [consultant];
  assert.ok(edInterpretation(r, NOW).flags.some((f) => /reassess/.test(f)));
});

test('C8: family asked not to tell + patient has capacity → direct-question plan required', () => {
  const r = draft((x) => { x.values.familyAskedNotToTell = true; });
  assert.ok(has(r, 'C8'));
  r.values.directQuestionPlan = 'Answer honestly with family present';
  assert.equal(has(r, 'C8'), false);
  assert.equal(has(draft((x) => { x.values.familyAskedNotToTell = true; x.capacity = { status: 'LACKS', assessor: 'Dr', assessedOn: '2026-10-29', basis: 'b', reassessOn: '' }; }), 'C8'), false);
});

test('ED encounter warning for a new plan started in the ED', () => {
  assert.ok(has(draft((x) => { x.createdInEd = true; }), 'ED-new'));
  assert.equal(has(draft((x) => { x.createdInEd = true; x.supersedes = 'old'; }), 'ED-new'), false);
});

test('edInterpretation: NONE, UNSIGNED, OUT_OF_DATE, REVOKED, ACTIVE', () => {
  assert.deepEqual([edInterpretation(null, NOW).state, edInterpretation(null, NOW).standardCareApplies], ['NONE', true]);
  const unsigned = draft((x) => { x.choices.cpr = 'DNACPR'; });
  assert.equal(edInterpretation(unsigned, NOW).state, 'UNSIGNED');
  assert.equal(edInterpretation(unsigned, NOW).standardCareApplies, true);
  // ACTIVE status but no consultant signature is still unsigned (acceptance: unsigned never ACTIVE in ED)
  assert.equal(edInterpretation({ ...unsigned, status: 'ACTIVE' }, NOW).state, 'UNSIGNED');
  const active = { ...unsigned, status: 'ACTIVE', signatures: [consultant] };
  assert.deepEqual([edInterpretation(active, NOW).state, edInterpretation(active, NOW).standardCareApplies], ['ACTIVE', false]);
  const past = { ...active, reviewDue: '2026-10-28' };
  const out = edInterpretation(past, NOW);
  assert.equal(out.state, 'OUT_OF_DATE');
  assert.equal(out.standardCareApplies, true);
  assert.match(out.banner, /OUT OF DATE.*Standard care applies/);
  assert.equal(edInterpretation({ ...active, reviewDue: '2026-10-29' }, NOW).state, 'ACTIVE'); // due today is still valid
  const revoked = edInterpretation({ ...active, status: 'REVOKED', revokedAt: NOW.toISOString() }, NOW);
  assert.equal(revoked.state, 'REVOKED');
  assert.match(revoked.banner, /CARD WITHDRAWN/);
  assert.equal(edInterpretation({ ...active, status: 'SUPERSEDED' }, NOW).state, 'OUT_OF_DATE');
  assert.ok(edInterpretation(null, NOW).ifInDoubt.length === 4);
});

test('review triggers and next review due', () => {
  assert.equal(ROUTINE_REVIEW_WEEKS, 12);
  assert.deepEqual(reviewTriggers.map((t) => t.id), ['NEW_ADMISSION', 'TRANSFER_BETWEEN_SETTINGS', 'SIGNIFICANT_CHANGE', 'PATIENT_OR_FAMILY_REQUEST', 'NEW_LINE_OF_TREATMENT', 'ROUTINE_INTERVAL']);
  assert.equal(nextReviewDue(NOW), '2027-01-21');
  assert.equal(nextReviewDue(NOW, 'ROUTINE_INTERVAL'), '2027-01-21');
  assert.equal(nextReviewDue(NOW, 'NEW_ADMISSION'), '2026-10-29');
});

// ---------------------------------------------------------------------------
// Store lifecycle (memory backend)

test('store: save, sign (one active), supersede, review, revoke, audit', async () => {
  setEctprStorage(memoryEctprStorage(), null);
  let changes = 0;
  const off = subscribeEctpr(() => { changes++; });

  const bad = draft((x) => { x.choices.goalOfCare = 'C_COMFORT'; x.choices.cpr = 'ATTEMPT'; });
  assert.equal((await saveEctprDraft(bad, NOW)).ok, false);

  const v1 = draft((x) => { x.choices.cpr = 'DNACPR'; x.choices.dnacprReason = 'FUTILITY'; x.choices.dnacprDiscussedWith = 'Son'; x.choices.dnacprDecidedBy = { name: 'Dr Test', registrationNo: 'REG-1', decidedAt: '2026-10-29T09:00' }; });
  assert.equal((await saveEctprDraft(v1, NOW)).ok, true);
  assert.equal(await getActiveEctpr('P1'), null); // unsigned is never active

  assert.equal((await signEctpr(v1.id, { ...consultant, registrationNo: '' }, NOW)).ok, false);
  assert.equal((await signEctpr(v1.id, { role: 'NURSE', name: 'Nurse A', signedAt: '' }, NOW)).ok, true);
  const signed = await signEctpr(v1.id, consultant, NOW);
  assert.equal(signed.ok, true);
  assert.equal(signed.record.status, 'ACTIVE');
  assert.equal(signed.record.signatures.length, 2);

  // signed record is never edited
  assert.equal((await saveEctprDraft({ ...signed.record, status: 'DRAFT' }, NOW)).ok, false);

  const review = await startReview('P1', 'NEW_ADMISSION', 'Dr Test', NOW);
  assert.equal(review.version, 2);
  assert.equal(review.status, 'DRAFT');
  assert.equal(review.supersedes, v1.id);
  assert.equal(review.choices.cpr, 'DNACPR');
  assert.equal((await startReview('P1', 'NEW_ADMISSION', 'Dr Test', NOW)).id, review.id); // no duplicate draft
  assert.equal((await signEctpr(review.id, consultant, NOW)).ok, true);

  const versions = await listEctprVersions('P1');
  assert.deepEqual(versions.map((v) => [v.version, v.status]), [[2, 'ACTIVE'], [1, 'SUPERSEDED']]);
  assert.equal(versions.filter((v) => v.status === 'ACTIVE').length, 1);

  assert.equal((await revokeEctpr(review.id, '', 'Patient', NOW)).ok, false);
  const revoked = await revokeEctpr(review.id, 'Patient changed their mind', 'Patient', NOW);
  assert.equal(revoked.record.status, 'REVOKED');
  assert.equal(await getActiveEctpr('P1'), null);
  assert.equal(edInterpretation(revoked.record, NOW).state, 'REVOKED');

  await logAudit({ actor: 'ED doctor', action: 'VIEW', recordType: 'ECTPR', recordId: review.id, patientId: 'P1' });
  const audit = await listAudit('P1');
  assert.ok(audit.length >= 7);
  assert.deepEqual(audit.slice(-1)[0].action, 'VIEW');
  assert.throws(() => { audit[0].actor = 'changed'; });
  assert.equal((await listAudit('P1'))[0].actor, audit[0].actor);
  assert.ok(changes > 0);
  off();
});

test('store: pilot episode log, outcome record and incident report', async () => {
  setEctprStorage(memoryEctprStorage(), null);
  const ep = await saveEpisodeLog({ patientId: 'P1', ectprId: 'e1', edArrivalAt: '2026-10-29T02:00:00Z', firstRecordAccessAt: '2026-10-29T02:07:00Z', highestSupportDelivered: 3, icuAdmitted: false, vasopressorsGiven: false, dialysisGiven: false, cprGiven: false, discordanceReason: '', recordedBy: 'Dr', recordedAt: NOW.toISOString() });
  assert.equal(retrievalMinutes(ep), 7);
  const plan = draft((x) => { x.choices.respCeiling = 4; x.choices.cpr = 'DNACPR'; });
  assert.equal(episodeConcordant(ep, plan), true);
  assert.equal(episodeConcordant({ ...ep, highestSupportDelivered: 5 }, plan), false);
  await saveOutcomeRecord({ patientId: 'P1', placeOfDeath: 'HOME', preferredPlaceOfDeath: 'HOME', icuDaysLast30: 0, ventilatorDaysLast30: 0, lastAdmissionAt: null, firstSymptomControlRxAt: null, outOfPocketLastAdmissionInr: 12000, recordedBy: 'Dr', recordedAt: NOW.toISOString() });
  await saveIncidentReport({ patientId: 'P2', ectprId: null, appliedToReversibleIllness: true, familyFeltPushed: false, details: 'Choking treated late', reportedBy: 'Nurse', reportedAt: NOW.toISOString() });
  assert.equal((await listEpisodeLogs('P1')).length, 1);
  assert.equal((await listOutcomeRecords('P1'))[0].placeOfDeath, 'HOME');
  assert.equal((await listIncidentReports('P1')).length, 0);
  assert.equal((await listIncidentReports()).length, 1);
});

test('store: seeds the sample quick views as ACTIVE signed records when nothing is stored', async () => {
  const views = {
    'CANCER-20377': { hospitalId: 'CANCER-20377', patientName: 'Leela Thomas', diagnosis: 'Stage IV Ovarian Carcinoma', goal: 'balanced', hospitalTransfer: 'listed-reasons-only', cpr: 'dnacpr', breathingCeiling: 'niv', icu: 'no', icuTrialDays: null, decisionMaker: { name: 'Joel Thomas', relationship: 'Son' }, treatingConsultant: 'Dr Sujay', palliativeContact: 'Palliative care team', signedBy: 'Dr Sujay', signedOn: '21 Aug 2026', reviewDueBy: '04 Sep 2026', formVersion: 'ECTPR Beta v0.1', recordVersion: 'Version 1' },
  };
  setEctprStorage(memoryEctprStorage(), views);
  const active = await getActiveEctpr('CANCER-20377');
  assert.equal(active.status, 'ACTIVE');
  assert.equal(active.choices.respCeiling, 4);
  assert.equal(active.choices.cpr, 'DNACPR');
  assert.equal(active.reviewDue, '2026-09-04');
  assert.equal(edInterpretation(active, new Date('2026-09-01T10:00:00')).state, 'ACTIVE');
  assert.equal(edInterpretation(active, NOW).state, 'OUT_OF_DATE');
  assert.equal(ectprFromQuickView(views['CANCER-20377']).choices.hospitalTransfer.kind, 'ONLY_FOR');
});
