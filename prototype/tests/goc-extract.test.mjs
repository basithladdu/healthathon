import test from 'node:test';
import assert from 'node:assert/strict';
import { applyGocDecision, applyGocPerson, applyGocQuote, checkedAiSuggestions, extractGoc, gocCarryForward, gocCurrentValue, gocFieldLock, gocLinesFromText, gocMarksFor, gocTextFromLines, withoutSoundCaptions, GOC_TOPIC_ORDER } from '../app/goc-extract.ts';
import { emptyEctpr } from '../app/ectpr-rules.ts';
import { quickViewSnapshot, withoutGocTranscript } from '../app/ed-card-snapshot.ts';
import { buildDataExport } from '../app/dpdp.ts';
import { buildGocNote, gocNoteKind, gocTopicRows } from '../app/goc-note.ts';
import { gocDefaultModel, planChunks, GOC_MODEL_DOWNLOAD_BYTES, GOC_LONGEST_SECONDS } from '../app/goc-transcribe-model.ts';
import { createGocExtractHandler } from '../app/goc-extract-server.ts';

const NOW = new Date('2026-11-01T10:00:00');
const draft = (patch = () => {}) => { const r = emptyEctpr('P1', { name: 'Asha Test', uhid: 'UH-1' }, 'Dr Test', NOW); r.prognosticUnderstanding.confirmed = true; patch(r); return r; };
const pick = (text, field) => extractGoc(text).suggestions.filter((s) => s.field === field);
const vals = (text, field) => pick(text, field).flatMap((s) => s.values);

function quotesAreExact(text) {
  const result = extractGoc(text);
  const source = text.normalize('NFC');
  for (const s of [...result.suggestions, ...result.ownWords, ...result.unacceptableOutcomes, ...result.homeCare, ...result.decisionMakers]) {
    assert.ok(source.includes(s.quote), `quote not word for word: ${s.quote}`);
  }
}

// Each row: language, words, field, expected option. Negation and wants in all five languages.
const CASES = [
  ['en', 'She does not want dialysis.', 'dialysis', 'NO'],
  ['en', 'No ICU for him.', 'icu', 'NO'],
  ['en', 'We do not want CPR.', 'cpr', 'DNACPR'],
  ['en', 'Yes to CPR, please try.', 'cpr', 'ATTEMPT'],
  ['en', 'He agrees to a trial of ICU for a few days.', 'icu', 'TRIAL'],
  ['en', 'We want dialysis.', 'dialysis', 'YES'],
  ['en', 'No feeding tube.', 'ng', 'NO'],
  ['en', 'No feeding tube.', 'peg', 'NO'],
  ['en', 'Do not take her to hospital.', 'hospitalTransfer', 'NO'],
  ['en', 'Keep her comfortable, comfort care only.', 'goalOfCare', 'C_COMFORT'],
  ['en', 'Please do everything possible.', 'goalOfCare', 'A_LIFE'],
  ['en', 'Take her to hospital only for pain or bleeding.', 'hospitalTransfer', 'ONLY_FOR'],
  ['hi', 'डायलिसिस नहीं चाहिए।', 'dialysis', 'NO'],
  ['hi', 'आईसीयू नहीं चाहते।', 'icu', 'NO'],
  ['hi', 'सीपीआर मत करना।', 'cpr', 'DNACPR'],
  ['hi', 'सीपीआर करना है।', 'cpr', 'ATTEMPT'],
  ['hi', 'डायलिसिस चाहिए।', 'dialysis', 'YES'],
  ['hi', 'नाक की नली नहीं चाहिए।', 'ng', 'NO'],
  ['hi', 'अस्पताल नहीं जाना।', 'hospitalTransfer', 'NO'],
  ['hi', 'बस आराम चाहिए।', 'goalOfCare', 'C_COMFORT'],
  ['ur', 'ڈائیلاسز نہیں چاہیے۔', 'dialysis', 'NO'],
  ['ur', 'آئی سی یو نہیں چاہتے۔', 'icu', 'NO'],
  ['ur', 'سی پی آر مت کریں۔', 'cpr', 'DNACPR'],
  ['ur', 'ڈائیلاسز چاہیے۔', 'dialysis', 'YES'],
  ['ur', 'ہسپتال نہیں جانا۔', 'hospitalTransfer', 'NO'],
  ['ur', 'بس آرام چاہیے۔', 'goalOfCare', 'C_COMFORT'],
  ['te', 'డయాలసిస్ వద్దు.', 'dialysis', 'NO'],
  ['te', 'ఐసీయూ వద్దు.', 'icu', 'NO'],
  ['te', 'సీపీఆర్ చేయవద్దు.', 'cpr', 'DNACPR'],
  ['te', 'డయాలసిస్ కావాలి.', 'dialysis', 'YES'],
  ['te', 'ఆసుపత్రికి తీసుకెళ్లొద్దు.', 'hospitalTransfer', 'NO'],
  ['te', 'ఇంట్లోనే ఉండాలి.', 'hospitalTransfer', 'NO'],
  ['kn', 'ಡಯಾಲಿಸಿಸ್ ಬೇಡ.', 'dialysis', 'NO'],
  ['kn', 'ಐಸಿಯು ಬೇಡ.', 'icu', 'NO'],
  ['kn', 'ಸಿಪಿಆರ್ ಮಾಡಬೇಡಿ.', 'cpr', 'DNACPR'],
  ['kn', 'ಡಯಾಲಿಸಿಸ್ ಬೇಕು.', 'dialysis', 'YES'],
  ['kn', 'ಆಸ್ಪತ್ರೆ ಬೇಡ.', 'hospitalTransfer', 'NO'],
  ['kn', 'ಮನೆಯಲ್ಲೇ ಇರಬೇಕು.', 'hospitalTransfer', 'NO'],
];
for (const [lang, text, field, expected] of CASES) {
  test(`${lang}: "${text}" gives ${field} = ${expected}, with its exact words`, () => {
    assert.deepEqual(vals(text, field), [expected]);
    quotesAreExact(text);
  });
}

// Sentences that once gave a wrong or reversed answer, and the rest of the set the Android matcher pins (CareGocExtractTest.kt).
const PINNED = [
  ['आईसीयू में मत ले जाइए', 'icu', ['NO']],
  ['आईसीयू में मत ले जाइए।', 'icu', ['NO']],
  ['No objection to dialysis.', 'dialysis', ['YES']],
  ['We do not want to stop dialysis.', 'dialysis', []],
  ['We want to stop dialysis.', 'dialysis', []],
  ['We talked about dialysis and no pain.', 'dialysis', []],
  ['No pain and dialysis was discussed.', 'dialysis', []],
  ['dialysis no pain', 'dialysis', []],
  ['डायलिसिस की बात हुई और दर्द नहीं चाहिए।', 'dialysis', []],
  ['No dialysis unless it is reversible.', 'dialysis', ['ONLY_IF_REVERSIBLE']],
  ['No ICU unless her family agrees.', 'icu', []],
  ['Do you want CPR? Yes please.', 'cpr', ['ATTEMPT']],
  ['Do you want CPR? No.', 'cpr', ['DNACPR']],
  ['Do you want CPR', 'cpr', []],
  ['He has a DNR.', 'cpr', ['DNACPR']],
  ['It is not a DNR.', 'cpr', []],
  ['Do not resuscitate.', 'cpr', ['DNACPR']],
  ['Hospital only for pain.', 'hospitalTransfer', ['ONLY_FOR']],
  ['ఐసీయూలో పెట్టకండి', 'icu', ['NO']],
  ['ಡಯಾಲಿಸಿಸ್ ಬೇಕಾಗಿಲ್ಲ', 'dialysis', ['NO']],
  ['ڈائیلیسس نہیں چاہیے', 'dialysis', ['NO']],
  ['ڈائیلیسس چاہیے', 'dialysis', ['YES']],
  ['ڈائیلیسس نهيں چاہیے', 'dialysis', ['NO']],
  ['کیا آپ ڈائیلیسس چاہتے ہیں؟', 'dialysis', []],
  ['ڈائیلیسس کے بارے میں معلوم نہیں', 'dialysis', []],
  ['We don\'t want dialysis.', 'dialysis', ['NO']],
  ['We don’t want dialysis.', 'dialysis', ['NO']],
  ['He refuses ICU.', 'icu', ['NO']],
  ['We want dialysis, no ICU.', 'dialysis', ['YES']],
  ['We want dialysis, no ICU.', 'icu', ['NO']],
  ['We want dialysis and no ICU.', 'dialysis', ['YES']],
  ['We want dialysis and no ICU.', 'icu', ['NO']],
  ['I am not sure about dialysis.', 'dialysis', []],
  ['We don\'t know about the ventilator yet.', 'respCeiling', []],
  ['Some patients choose dialysis.', 'dialysis', []],
  ['The doctor explained the ventilator.', 'respCeiling', []],
  ['Dialysis was explained.', 'dialysis', []],
  ['She wants to stay at home.', 'hospitalTransfer', ['NO']],
  ['Comfort care only please.', 'goalOfCare', ['C_COMFORT']],
  ['Please do everything.', 'goalOfCare', ['A_LIFE']],
  ['Dialysis for a few days as a trial.', 'dialysis', ['TRIAL']],
  ['We want vasopressors.', 'vasopressors', ['YES_NO_LIMIT', 'YES_MAX_AGENTS']],
  ['No drip.', 'hydration', ['ORAL_SIPS']],
  ['सीपीआर न करें।', 'cpr', ['DNACPR']],
  ['घर पर ही रहना है।', 'hospitalTransfer', ['NO']],
  ['क्या आप डायलिसिस चाहते हैं?', 'dialysis', []],
  ['డయాలసిస్ గురించి తెలియదు', 'dialysis', []],
  ['ఐసీయూ కావాలా?', 'icu', []],
  ['ಡಯಾಲಿಸಿಸ್ ಬಗ್ಗೆ ಗೊತ್ತಿಲ್ಲ', 'dialysis', []],
  ['If the heart stops, do not do CPR.', 'cpr', ['DNACPR']],
  ['I want to be at home with my family.', 'hospitalTransfer', []],
];
for (const [text, field, expected] of PINNED) {
  test(`pinned: "${text}" gives ${field} = ${expected.join(' or ') || 'no mark'}`, () => {
    assert.deepEqual(vals(text, field), expected);
    if (!expected.length) assert.deepEqual(pick(text, field).flatMap((s) => s.ruledOut), []);
    quotesAreExact(text);
  });
}

test('pinned: mixed sentences, ruled-out levels, conflict, and values', () => {
  const mixed = extractGoc('No ventilator but BiPAP is okay.').suggestions.filter((s) => s.field === 'respCeiling');
  assert.deepEqual(mixed.flatMap((s) => s.values), ['4']);
  assert.deepEqual(mixed.flatMap((s) => s.ruledOut), ['5']);
  assert.deepEqual(pick('وینٹی لیٹر نہیں', 'respCeiling')[0].ruledOut, ['5']);
  assert.deepEqual(pick('क्या आप वेंटिलेटर चाहते हैं? नहीं।', 'respCeiling')[0].ruledOut, ['5']);
  assert.equal(gocMarksFor(extractGoc(gocLinesFromText('Patient: I do not want ICU.\nSon: We want ICU.')).suggestions, 'icu').conflict, true, 'two people disagreeing stays a conflict');
  assert.equal(gocMarksFor(extractGoc('I do not want ICU. My son wants ICU.').suggestions, 'icu').changedMind, true, 'unlabelled lines count as one speaker');
  const case_ = pick('We met today. Amma does NOT want Dialysis, please.', 'dialysis');
  assert.equal(case_[0].quote, 'Amma does NOT want Dialysis, please.');
  const values = extractGoc('What matters most is being with family. Being a burden is unacceptable. Is it important to you?');
  assert.ok(values.ownWords.some((q) => q.quote.includes('What matters most')));
  assert.ok(values.unacceptableOutcomes.some((q) => q.quote.includes('burden')));
  assert.equal(extractGoc('It is not important to me.').ownWords.length, 0);
});

test('"no ventilator" rules out the ventilator but does not invent a level the talk never named', () => {
  for (const text of ['No ventilator.', 'वेंटिलेटर नहीं चाहिए।', 'وینٹیلیٹر نہیں چاہیے۔', 'వెంటిలేటర్ వద్దు.', 'ವೆಂಟಿಲೇಟರ್ ಬೇಡ.']) {
    const [s] = pick(text, 'respCeiling');
    assert.ok(s, text);
    assert.deepEqual(s.values, []);
    assert.deepEqual(s.ruledOut, ['5'], text);
  }
  assert.deepEqual(vals('The ventilator is okay.', 'respCeiling'), ['5']);
  assert.deepEqual(pick('No NIV.', 'respCeiling')[0].ruledOut, ['4', '5']);
});

test('negation is tied to the right treatment in one sentence', () => {
  const text = 'He wants dialysis but no ventilator, and do not want CPR.';
  assert.deepEqual(vals(text, 'dialysis'), ['YES']);
  assert.deepEqual(pick(text, 'respCeiling')[0].ruledOut, ['5']);
  assert.deepEqual(vals(text, 'cpr'), ['DNACPR']);
  assert.deepEqual(vals('सीपीआर नहीं चाहिए लेकिन डायलिसिस चाहिए।', 'cpr'), ['DNACPR']);
  assert.deepEqual(vals('सीपीआर नहीं चाहिए लेकिन डायलिसिस चाहिए।', 'dialysis'), ['YES']);
  assert.deepEqual(vals('ಡಯಾಲಿಸಿಸ್ ಬೇಡ ಆದರೆ ಐಸಿಯು ಬೇಕು.', 'dialysis'), ['NO']);
  assert.deepEqual(vals('ಡಯಾಲಿಸಿಸ್ ಬೇಡ ಆದರೆ ಐಸಿಯು ಬೇಕು.', 'icu'), ['YES']);
  assert.deepEqual(vals('They do not want ICU or dialysis.', 'icu'), ['NO']);
  assert.deepEqual(vals('They do not want ICU or dialysis.', 'dialysis'), ['NO']);
});

test('spoken Hindi written in Latin letters, as the small speech model often does', () => {
  assert.deepEqual(vals('dialysis nahi chahiye', 'dialysis'), ['NO']);
  assert.deepEqual(vals('ventilator nahi chahiye', 'respCeiling'), []);
  assert.deepEqual(pick('ventilator nahi chahiye', 'respCeiling')[0].ruledOut, ['5']);
  assert.deepEqual(vals('ICU chahiye', 'icu'), ['YES']);
  assert.deepEqual(vals('dialysis vaddu', 'dialysis'), ['NO']);
  assert.deepEqual(vals('dialysis beda', 'dialysis'), ['NO']);
});

test('saying yes to something that has several options leaves the doctor to pick', () => {
  assert.deepEqual(vals('He agrees to vasopressors.', 'vasopressors'), ['YES_NO_LIMIT', 'YES_MAX_AGENTS']);
  assert.deepEqual(vals('Fluids are okay.', 'hydration'), ['LIMITED', 'FULL']);
  assert.deepEqual(vals('No drips.', 'hydration'), ['ORAL_SIPS']);
});

test('no false positives: unrelated talk, a bare mention, a question, doubt, other patients, the past', () => {
  const quiet = [
    'The weather was hot and we had tea. My brother lives in Hyderabad and the train was late.',
    'The doctor explained what a ventilator is.',
    'He was on dialysis last year.',
    'Do you want CPR?',
    'Would you like ICU care',
    'क्या आप वेंटिलेटर चाहते हैं?',
    'వెంటిలేటర్ కావాలా?',
    'ಡಯಾಲಿಸಿಸ್ ಬೇಕಾ?',
    'We are not sure about dialysis.',
    'डायलिसिस के बारे में पता नहीं।',
    'Some patients do not want a ventilator.',
    'कुछ मरीज़ वेंटिलेटर नहीं चाहते।',
    'He did not have dialysis.',
    'ICU',
    'The hospital bill was high.',
    'आज मौसम अच्छा है और हम घर से आए हैं।',
    'ఈ రోజు వాతావరణం బాగుంది.',
    'ಇಂದು ಹವಾಮಾನ ಚೆನ್ನಾಗಿದೆ.',
    'آج موسم اچھا ہے۔',
    'No, the shop was closed. Yes, the bus came.',
  ];
  for (const text of quiet) assert.deepEqual(extractGoc(text).suggestions, [], text);
});

test('unrelated talk ticks no topics', () => {
  const { topics } = extractGoc('The weather was hot and we had tea.');
  assert.equal(topics.length, GOC_TOPIC_ORDER.length);
  assert.ok(topics.every((t) => !t.discussed));
});

test('a short answer takes its meaning from the question, and the quote holds both', () => {
  const lines = [{ text: 'Do you want ICU?', speaker: 'DOCTOR' }, { text: 'No.', speaker: 'PATIENT_PARTY' }];
  const [s] = pick(lines, 'icu');
  assert.deepEqual(s.values, ['NO']);
  assert.equal(s.quote, 'Do you want ICU?\nNo.');
  assert.deepEqual(vals([{ text: 'क्या आईसीयू चाहिए?', speaker: 'DOCTOR' }, { text: 'नहीं।', speaker: 'PATIENT_PARTY' }], 'icu'), ['NO']);
  assert.deepEqual(vals([{ text: 'ఐసీయూ కావాలా?', speaker: null }, { text: 'వద్దు.', speaker: null }], 'icu'), ['NO']);
  assert.deepEqual(vals('Do you want ICU and dialysis? No.', 'icu'), [], 'a question about two things is too unclear');
});

test('a line marked as the doctor side ticks the topic but never becomes a suggestion', () => {
  const lines = [{ text: 'We do not want to start dialysis.', speaker: 'DOCTOR' }];
  const result = extractGoc(lines);
  assert.deepEqual(result.suggestions, []);
  assert.equal(result.topics.find((t) => t.topic === 'dialysis').discussed, true);
  assert.equal(extractGoc([{ ...lines[0], speaker: 'PATIENT_PARTY' }]).suggestions.length, 1);
});

test('values, telling the patient, people and home care carry exact quotes', () => {
  const text = 'I want to be at my daughter\'s wedding. I would not want to live on machines. The patient does not know about the cancer. Please do not tell her. My son Ravi will decide for me. The nurse comes every week. We have an NGO helping us. He was admitted before for this.';
  const result = extractGoc(text);
  assert.ok(result.ownWords.some((q) => q.quote.includes('wedding')));
  assert.ok(result.unacceptableOutcomes.some((q) => q.quote.includes('machines')));
  assert.deepEqual(vals(text, 'patientKnows'), ['no']);
  assert.deepEqual(vals(text, 'familyAskedNotToTell'), ['yes']);
  assert.equal(result.decisionMakers[0].relation, 'son');
  assert.equal(result.decisionMakers[0].name, 'Ravi');
  assert.deepEqual(result.homeCare.map((h) => h.field).sort(), ['assessmentFrequency', 'ngoContact', 'previousAdmission']);
  quotesAreExact(text);
});

test('values in the other languages', () => {
  assert.deepEqual(vals('मरीज़ को बीमारी पता है।', 'patientKnows'), ['yes']);
  assert.deepEqual(vals('मरीज़ को बीमारी के बारे में नहीं पता।', 'patientKnows'), ['no']);
  assert.deepEqual(vals('వారికి జబ్బు గురించి తెలియదు.', 'patientKnows'), ['no']);
  assert.deepEqual(vals('ಕಾಯಿಲೆ ಬಗ್ಗೆ ಗೊತ್ತಿಲ್ಲ.', 'patientKnows'), ['no']);
  assert.deepEqual(vals('انہیں بیماری کا معلوم ہے۔', 'patientKnows'), ['yes']);
  assert.deepEqual(vals('उन्हें मत बताना।', 'familyAskedNotToTell'), ['yes']);
  assert.deepEqual(vals('انہیں مت بتائیں۔', 'familyAskedNotToTell'), ['yes']);
  assert.deepEqual(vals('వారికి చెప్పకండి.', 'familyAskedNotToTell'), ['yes']);
  assert.deepEqual(vals('ಅವರಿಗೆ ಹೇಳಬೇಡಿ.', 'familyAskedNotToTell'), ['yes']);
  assert.ok(extractGoc('मैं बोझ नहीं बनना चाहता।').unacceptableOutcomes.length);
  assert.ok(extractGoc('నాకు ఇంట్లో ఉండాలని కోరిక ఉంది.').ownWords.length);
  assert.ok(extractGoc('ನನಗೆ ಮನೆಯಲ್ಲಿ ಇರಲು ಆಸೆ ಇದೆ.').ownWords.length);
  assert.equal(extractGoc('मेरी बेटी फ़ैसला लेगी।').decisionMakers[0].relation, 'बेटी');
  assert.equal(extractGoc('నా కూతురు నిర్ణయం తీసుకుంటుంది.').decisionMakers[0].relation, 'కూతురు');
});

test('Arabic-style Urdu letters still match and the quote stays as typed', () => {
  const text = 'ڈائیلاسز نہيں چاہيے۔';
  assert.deepEqual(vals(text, 'dialysis'), ['NO']);
  assert.ok(text.includes(pick(text, 'dialysis')[0].quote));
});

test('different statements about one decision are all kept; the latest one stands and the earlier is shown as replaced', () => {
  const marks = gocMarksFor(extractGoc('We want dialysis. Then she said no dialysis.').suggestions, 'dialysis');
  assert.equal(marks.conflict, false);
  assert.equal(marks.changedMind, true);
  assert.deepEqual(marks.suggested, ['NO']);
  assert.equal(marks.own.length, 2);
  assert.deepEqual(marks.superseded.map((s) => s.earlier.values), [['YES']]);
});

test('latest statement wins: no, then yes, then no follows the last one, with both earlier quotes kept', () => {
  const lines = gocLinesFromText('Doctor: Would you want CPR if the heart stops?\nPatient: No, no CPR.\nDoctor: Are you sure?\nPatient: Actually yes, I want CPR.\nPatient: No. I have thought again. Do not do CPR.');
  const marks = gocMarksFor(extractGoc(lines).suggestions, 'cpr');
  assert.deepEqual(marks.suggested, ['DNACPR']);
  assert.equal(marks.superseded.length, 2);
  assert.ok(marks.superseded.some((s) => s.earlier.values.includes('ATTEMPT')));
  assert.equal(marks.conflict, false);
  const hindi = gocMarksFor(extractGoc('डायलिसिस नहीं चाहिए। ठीक है, डायलिसिस करवाना है। नहीं, डायलिसिस नहीं करना।').suggestions, 'dialysis');
  assert.deepEqual(hindi.suggested, ['NO']);
  assert.equal(hindi.superseded.length, 2);
});

test('latest statement wins only within one person, and compatible statements both stand', () => {
  const two = gocMarksFor(extractGoc(gocLinesFromText('Patient: I do not want the ventilator.\nSon: Please put him on the ventilator.')).suggestions, 'respCeiling');
  assert.equal(two.superseded.length, 0, 'the son does not overrule the patient');
  const ladder = gocMarksFor(extractGoc('No ventilator. BiPAP is okay.').suggestions, 'respCeiling');
  assert.deepEqual(ladder.suggested, ['4']);
  assert.deepEqual(ladder.ruledOut, ['5']);
  assert.equal(ladder.superseded.length, 0);
  const back = gocMarksFor(extractGoc('No ventilator. Then she changed her mind: the ventilator is okay.').suggestions, 'respCeiling');
  assert.deepEqual(back.suggested, ['5']);
  assert.deepEqual(back.ruledOut, []);
});

test('side labels come only from what was typed; sentences become lines', () => {
  const lines = gocLinesFromText('Doctor: Do you want ICU? Please tell me.\nFamily: No.\nWe talked a lot.');
  assert.deepEqual(lines.map((l) => l.speaker), ['DOCTOR', 'DOCTOR', 'PATIENT_PARTY', null]);
  assert.equal(lines[2].text, 'No.');
  assert.equal(gocTextFromLines(lines).split('\n').length, 4);
  assert.deepEqual(gocLinesFromText('डॉक्टर: आईसीयू? \nपरिवार: नहीं।').map((l) => l.speaker), ['DOCTOR', 'PATIENT_PARTY']);
});

test('sound captions are left out of the note text', () => {
  assert.equal(withoutSoundCaptions('I want to go home [crying] (sobs) now ♪'), 'I want to go home now');
});

test('applying: nothing changes until called, V4 and V5 gates hold, and option shapes match the wizard', () => {
  const r = draft();
  extractGoc('No ICU. No CPR. Comfort only.');
  assert.equal(r.choices.icu, null, 'reading a transcript never touches the plan');
  assert.equal(applyGocDecision(r, 'icu', 'TRIAL'), true);
  assert.equal(r.choices.icu.kind, 'TRIAL');
  assert.ok(r.choices.icu.trial.durationValue > 0);
  applyGocDecision(r, 'cpr', 'DNACPR');
  assert.equal(r.choices.dnacprDecidedBy, null, 'who decided is left for the doctor');
  assert.equal(r.status, 'DRAFT');
  assert.deepEqual(r.signatures, []);
  applyGocDecision(r, 'respCeiling', '4');
  assert.equal(r.choices.respCeiling, 4);
  applyGocDecision(r, 'ng', 'UP_TO_DAYS');
  assert.equal(r.choices.feeding.ng.days, 7);
  applyGocDecision(r, 'hospitalTransfer', 'ONLY_FOR', { reasons: ['PAIN'] });
  assert.deepEqual(r.choices.hospitalTransfer, { kind: 'ONLY_FOR', reasons: ['PAIN'] });
  applyGocDecision(r, 'icu', null);
  assert.equal(r.choices.icu, null, 'choosing No preference recorded stores null (V1)');
  assert.equal(gocCurrentValue(r, 'icu'), null);

  const v4 = draft((d) => { d.prognosticUnderstanding.confirmed = false; });
  assert.equal(gocFieldLock(v4, 'icu'), 'V4');
  assert.equal(applyGocDecision(v4, 'icu', 'NO'), false);
  assert.equal(v4.choices.icu, null);
  assert.equal(applyGocDecision(v4, 'patientKnows', 'yes'), true);

  const v5 = draft((d) => { d.surrogateDeclaration.answeringAsSurrogate = true; });
  assert.equal(gocFieldLock(v5, 'patientKnows'), 'V5');
  assert.equal(applyGocDecision(v5, 'cpr', 'DNACPR'), false);
  assert.equal(applyGocQuote(v5, 'ownWords', 'x'), false);

  const signed = draft((d) => { d.status = 'ACTIVE'; });
  assert.equal(applyGocDecision(signed, 'icu', 'NO'), false);
  assert.equal(signed.choices.icu, null);
});

test('accepted quotes add to the plan; existing words and people are kept', () => {
  const r = draft((d) => { d.values.ownWords = 'Earlier words'; d.decisionMakers.primary = { name: 'Existing', relation: 'wife', phone24h: '1', idType: null, idLast4: null, authorityBasis: null }; });
  applyGocQuote(r, 'ownWords', 'I want to go home');
  assert.equal(r.values.ownWords, 'Earlier words\nI want to go home');
  applyGocQuote(r, 'unacceptableOutcomes', 'on machines');
  applyGocQuote(r, 'unacceptableOutcomes', 'on machines');
  assert.deepEqual(r.values.unacceptableOutcomes, ['on machines']);
  applyGocQuote(r, 'ngoContact', 'Asha NGO');
  assert.equal(r.homeCare.ngoContact, 'Asha NGO');
  assert.equal(applyGocPerson(r, { relation: 'son', name: 'Ravi', quote: 'q' }, 'primary'), false);
  assert.equal(r.decisionMakers.primary.name, 'Existing');
  assert.equal(applyGocPerson(r, { relation: 'son', name: 'Ravi', quote: 'q' }, 'alternate'), true);
  assert.equal(r.decisionMakers.alternate.name, 'Ravi');
  applyGocPerson(r, { relation: 'son', name: 'Ravi', quote: 'q' }, 'present');
  assert.deepEqual(r.decisionMakers.presentAtDiscussion, ['Ravi (son)']);
});

test('the checklist is in the order the team asked for, and a topic not talked about stays not discussed and null', () => {
  assert.deepEqual(GOC_TOPIC_ORDER.slice(0, 5), ['consent', 'patientDetails', 'decisionMakers', 'cpr', 'dialysis'], 'consent, patient details, family and emergency contact, then CPR and dialysis');
  const r = draft();
  const rows = gocTopicRows(extractGoc('No ICU.').topics, {});
  assert.equal(rows.find((row) => row.topic === 'icu').status, 'DISCUSSED');
  const dialysis = rows.find((row) => row.topic === 'dialysis');
  assert.equal(dialysis.status, 'NOT_DISCUSSED');
  assert.equal(dialysis.verified, false);
  const mark = { status: 'NOT_DISCUSSED', verifiedBy: 'Dr Test', verifiedAt: NOW.toISOString() };
  assert.equal(gocTopicRows(extractGoc('No ICU.').topics, { dialysis: mark }).find((row) => row.topic === 'dialysis').verified, true);
  assert.equal(r.choices.dialysis, null);
  assert.equal(gocTopicRows(extractGoc('No ICU.').topics, { icu: mark }).find((row) => row.topic === 'icu').status, 'NOT_DISCUSSED', 'the doctor can overrule the tick');
});

test('the draft note follows the form, states decisions, keeps no emotion and no verbatim transcript', () => {
  const r = draft();
  applyGocDecision(r, 'icu', 'NO');
  applyGocQuote(r, 'ownWords', 'I want to go home [crying]');
  const lines = [{ text: 'I am so scared and crying [crying], no ICU please', speaker: 'PATIENT_PARTY' }];
  const rows = gocTopicRows(extractGoc(lines).topics, { dialysis: { status: 'NOT_DISCUSSED', verifiedBy: 'Dr Test', verifiedAt: NOW.toISOString() } });
  const note = buildGocNote(r, rows, 'en', { reviewedBy: 'Dr Test', reviewedAt: NOW.toISOString() });
  assert.match(note, /Requirement of ICU\?: No — ward-level/);
  assert.match(note, /Requirement of ICU\?[^\n]*\n {4}☐ Yes\n {4}☐ Time-limited trial\n {4}☑ No/, 'the boxes print like the paper form, the chosen one ticked');
  assert.match(note, /Dialysis or CRRT: Not discussed \(doctor verified\)\. No preference recorded/);
  assert.match(note, /- What I am most afraid of is: Not discussed/, 'a blank nobody filled reads Not discussed');
  assert.match(note, /- CPR: Not discussed\. No preference recorded/);
  assert.match(note, /I want to go home/);
  assert.doesNotMatch(note, /scared|crying/);
  const order = ['SECTION I ', 'SECTION II ', 'SECTION III ', 'SECTION IV '].map((h) => note.indexOf(h));
  assert.ok(order.every((at, i) => at > 0 && (i === 0 || at > order[i - 1])), 'Sections I to IV in the order of the clinical document');
  assert.match(note, /Not discussed: [^\n]*Patient details/, 'the note lists what was not discussed');
  assert.equal(gocNoteKind(r), 'DECISION');
  assert.equal(gocNoteKind(draft()), 'DISCUSSION');
  assert.match(buildGocNote(draft(), [], 'hi', { reviewedBy: 'Dr', reviewedAt: NOW.toISOString() }), /बातचीत/);
});

test('the next conversation shows only what differs, and empty stays empty', () => {
  const before = draft();
  applyGocDecision(before, 'icu', 'NO');
  applyGocDecision(before, 'cpr', 'DNACPR');
  const next = structuredClone(before);
  applyGocDecision(next, 'icu', 'YES');
  const rows = gocCarryForward(before, next);
  assert.deepEqual(rows.filter((row) => row.changed).map((row) => row.field), ['icu']);
  assert.equal(rows.find((row) => row.field === 'dialysis').current, null);
  assert.equal(rows.find((row) => row.field === 'cpr').changed, false);
});

test('the QR card never carries the transcript, in either view, and the data export does', () => {
  const r = draft((d) => { d.status = 'ACTIVE'; d.gocTranscript = { lines: [{ text: 'secret words', speaker: null }], language: 'hi', source: 'TYPED', machineDraft: false, consentAt: NOW.toISOString(), reviewedBy: 'Dr Test', reviewedAt: NOW.toISOString(), audioKept: false, accepted: [], topics: {}, note: 'note text' }; });
  assert.equal(JSON.stringify(quickViewSnapshot(r)).includes('secret words'), false);
  assert.equal('gocTranscript' in withoutGocTranscript(r), false);
  assert.equal(JSON.stringify(withoutGocTranscript(r)).includes('note text'), false);
  assert.equal(r.gocTranscript.note, 'note text', 'the stored record keeps it');
  const exported = buildDataExport({ patientId: 'P1', ectprVersions: [r], distribution: [], audit: [], episodes: [], outcomes: [], incidents: [], amdVersions: [], scans: {}, cards: [] }, NOW);
  assert.equal(JSON.stringify(exported).includes('secret words'), true);
  const old = structuredClone(r); delete old.gocTranscript;
  assert.equal(quickViewSnapshot(old).gocTranscript, undefined, 'older records read as before');
});

test('long audio splits at the quietest point, drops silence and keeps every sample in order', () => {
  const rate = 16000;
  const samples = new Float32Array(rate * 110).fill(0.2);
  samples.fill(0, rate * 27, rate * 27 + rate / 2); // a pause at 27 seconds
  samples.fill(0, rate * 60); // everything after one minute is silent
  const chunks = planChunks(samples);
  assert.equal(chunks[0].start, 0);
  assert.ok(chunks[0].end >= rate * 27 && chunks[0].end <= rate * 27.5, `cut at ${chunks[0].end / rate}`);
  for (let i = 1; i < chunks.length; i++) assert.equal(chunks[i].start, chunks[i - 1].end);
  assert.equal(chunks.at(-1).end, samples.length);
  assert.ok(chunks.every((c) => c.end - c.start <= 30 * rate));
  assert.equal(chunks.at(-1).silent, true);
  assert.equal(chunks[0].silent, false);
  assert.ok(GOC_MODEL_DOWNLOAD_BYTES > 50e6 && GOC_LONGEST_SECONDS >= 3600);
  assert.equal(gocDefaultModel('hi'), 'small');
  assert.equal(gocDefaultModel('auto'), 'base');
});

test('server reading: off without keys, needs consent, and only exact quotes and known options survive', async () => {
  const none = createGocExtractHandler(async () => { throw new Error('no network expected'); }, {});
  const status = await (await none(new Request('http://x/api/goc-extract'))).json();
  assert.equal(status.available, false);
  const refused = await none(new Request('http://x/api/goc-extract', { method: 'POST', headers: { origin: 'http://x' }, body: JSON.stringify({ text: 'No ICU.', consent: true }) }));
  assert.equal(refused.status, 503);

  const reply = { suggestions: [
    { field: 'icu', value: 'NO', quote: 'No ICU' },
    { field: 'dialysis', value: 'YES', quote: 'words that were never said' },
    { field: 'made_up', value: 'NO', quote: 'No ICU' },
    { field: 'cpr', value: 'MAYBE', quote: 'No ICU' },
  ] };
  let sent = '';
  const send = async (_url, init) => { sent = init.body; return new Response(JSON.stringify({ choices: [{ message: { content: JSON.stringify(reply) } }] }), { status: 200, headers: { 'content-type': 'application/json' } }); };
  const on = createGocExtractHandler(send, { GROQ_API_KEY: 'test' });
  assert.equal((await (await on(new Request('http://x/api/goc-extract'))).json()).available, true);
  const noConsent = await on(new Request('http://x/api/goc-extract', { method: 'POST', headers: { origin: 'http://x' }, body: JSON.stringify({ text: 'No ICU.' }) }));
  assert.equal(noConsent.status, 400);
  assert.equal(sent, '');
  const ok = await (await on(new Request('http://x/api/goc-extract', { method: 'POST', headers: { origin: 'http://x' }, body: JSON.stringify({ text: 'No ICU.', consent: true }) }))).json();
  assert.deepEqual(ok.suggestions, [{ field: 'icu', values: ['NO'], ruledOut: [], quote: 'No ICU', source: 'ai', at: 0, who: '' }]);
  assert.match(sent, /Never give treatment advice/);
  assert.equal(checkedAiSuggestions(null, 'x').length, 0);
});

// The mock conversations in docs/goc-mock-conversations (all fictional). Each has an expected-outcome file; knownGaps.web lists what the
// web matcher is known to miss, with the reason, so a gap is reported rather than hidden.
import { readFileSync, readdirSync } from 'node:fs';
import { gocUndiscussed } from '../app/goc-extract.ts';
import { buildGocFormDocument, gocFormHtml, gocFormText } from '../app/goc-form.ts';
import { crc32, gocFormDocx } from '../app/goc-docx.ts';
const MOCKS = new URL('../../docs/goc-mock-conversations/', import.meta.url);
const mockText = (file) => readFileSync(new URL(file, MOCKS), 'utf8').split(/\r?\n/).filter((line) => !line.startsWith('#')).join('\n');
for (const file of readdirSync(MOCKS).filter((name) => name.endsWith('.expected.json')).sort()) {
  const expected = JSON.parse(readFileSync(new URL(file, MOCKS), 'utf8'));
  test(`mock conversation ${expected.transcript}: the matcher's suggestions, changes of mind, disagreements and checklist`, () => {
    assert.equal(expected.fictional, true);
    assert.match(readFileSync(new URL(expected.transcript, MOCKS), 'utf8').split('\n')[0], /^# FICTIONAL/);
    const lines = gocLinesFromText(mockText(expected.transcript));
    const result = extractGoc(lines);
    const gaps = expected.knownGaps?.web ?? {};
    for (const [field, want] of Object.entries(expected.decisions)) {
      if (gaps[field]) continue;
      const marks = gocMarksFor(result.suggestions, field);
      assert.deepEqual([...marks.suggested].sort(), [...want.suggested].sort(), `${field} suggested`);
      if (want.ruledOut) assert.deepEqual([...marks.ruledOut].sort(), [...want.ruledOut].sort(), `${field} ruled out`);
      assert.equal(marks.changedMind, expected.changedMind.includes(field), `${field} change of mind`);
      assert.equal(marks.conflict, expected.conflict.includes(field), `${field} disagreement`);
    }
    const rows = gocTopicRows(result.topics, {});
    for (const topic of expected.discussed) if (!gaps[topic]) assert.equal(rows.find((row) => row.topic === topic).status, 'DISCUSSED', `${topic} discussed`);
    const open = gocUndiscussed(rows).map((row) => row.topic);
    for (const topic of expected.notDiscussed) if (!gaps[topic]) assert.ok(open.includes(topic), `${topic} listed as not discussed`);
    quotesAreExact(gocTextFromLines(lines));
  });
}

test('the end-of-talk prompt lists only what was neither discussed nor verified by the doctor', () => {
  const rows = gocTopicRows(extractGoc('No ICU.').topics, { cpr: { status: 'NOT_DISCUSSED', verifiedBy: 'Dr', verifiedAt: NOW.toISOString() } }, { consented: true });
  const open = gocUndiscussed(rows).map((row) => row.topic);
  assert.ok(!open.includes('consent'), 'consent ticks once everyone agreed in the app');
  assert.ok(!open.includes('icu'));
  assert.ok(!open.includes('cpr'), 'the doctor verified it');
  assert.ok(open.includes('dialysis') && open.includes('patientDetails'));
});

test('the note comes out as a printable page and a Word file in the clinical document’s Sections I to IV', () => {
  const r = draft();
  applyGocDecision(r, 'cpr', 'DNACPR');
  const doc = buildGocFormDocument(r, gocTopicRows(extractGoc('Do not do CPR.').topics, {}), 'en', { reviewedBy: 'Dr Test', reviewedAt: NOW.toISOString() });
  assert.deepEqual(doc.sections.map((s) => s.id), ['I', 'II', 'III', 'IV']);
  const html = gocFormHtml(doc);
  assert.match(html, /Section I – General information[\s\S]*Section II[\s\S]*Section III – Decisive Actions[\s\S]*Section IV – Signatures/);
  assert.doesNotMatch(html, /<script/);
  const bytes = gocFormDocx(doc);
  assert.deepEqual([...bytes.slice(0, 4)], [0x50, 0x4b, 0x03, 0x04]);
  const text = new TextDecoder().decode(bytes);
  assert.match(text, /word\/document\.xml/);
  assert.match(text, /Do not attempt CPR \(DNACPR\)/);
  assert.equal(crc32(new TextEncoder().encode('123456789')), 0xcbf43926);
});

test('every heading, field, box and table of Rituparna’s form is in the note, in the form’s order and words', () => {
  const r = draft();
  applyGocDecision(r, 'respCeiling', '1'); r.choices.maxOxygenLpm = 2;
  applyGocDecision(r, 'hospitalTransfer', 'ONLY_FOR', { reasons: ['PAIN'] });
  const doc = buildGocFormDocument(r, [], 'en', { reviewedBy: 'Dr Test', reviewedAt: NOW.toISOString(), deviceSignatures: [{ role: 'DOCTOR', name: 'Dr Test', method: 'DRAWN', signedAt: NOW.toISOString(), registrationNo: 'KMC 1' }] });
  const note = gocFormText(doc);
  const lower = note.toLowerCase();
  const form = [
    'Section I – General information', 'Patient details', 'Age, Sex, Gender', 'Physician details', 'Primary Oncologist name and hospital – and contact',
    'Palliative care physician name and hospital – and contact', 'Additional space for the team – Surgical oncology, radiation oncology, etc.',
    'Additional option to add a local physician', 'Family/Friend details', 'Members present for discussion and relationship with the patient', 'Language preferred',
    'PRIMARY', 'SECONDARY', 'Relationship to patient', 'Phone (must be reachable 24 h)', 'ID type & number', 'Basis for authority', 'Named by patient', 'Family consensus', 'Named in AMD', 'Court-appointed',
    'Family members who must be informed before any major decision (name, relation, phone)', 'attempted to be contacted, but were unavailable',
    'Section II – Preliminary medical information and patient values', 'Primary diagnosis and stage', 'Functional status – ECOG', 'Patient’s capacity', 'Existing medical directives',
    '4.1  The patient’s capacity, now', 'Capacity fluctuates.', 'Basis of assessment (understand / retain / weigh / communicate)', '4.2  Existing directives', 'Attested by Notary', 'Gazetted Officer',
    'with two witnesses', 'No directive and no recorded prior wishes.', '5.3  What the patient knows', 'The patient knows the diagnosis and the prognosis.', 'will not be lied to',
    'do not wish to know details and has delegated', '6.1  In their own words', 'The most distressing physical symptom right now is', 'What matters most to me / to them is',
    'What I am most afraid of is', 'Unfinished things that matter', '6.2  Outcomes that would be unacceptable', 'Exhausting the family’s savings', 'Other:',
    'Section III – Decisive Actions', 'Home care (if relevant)', 'Should the patient be brought to the hospital?', 'YES — for any significant deterioration.', '☑ ONLY FOR THE FOLLOWING', '☑     Pain that cannot be controlled at home',
    'Caregiver unable to cope — respite admission', 'NO — the patient is to be cared for where they are.', 'frequency of assessments', 'non-governmental organisation', 'previously admitted', 'Should CPR be performed?', 'ATTEMPT CPR.', 'DO NOT ATTEMPT CPR (DNACPR).', 'Reason recorded (clinical futility / patient refusal / burden outweighs benefit)',
    'Discussed with (patient / surrogate / both)', 'What is the level of oxygen/ventilatory support', '| LEVEL | WHAT IT MEANS FOR THE PATIENT | CEILING |', 'Low-flow oxygen up to 2 L/min', '| 1 | Nasal prongs / simple face mask',
    'Intubation and invasive mechanical ventilation', 'Other parameters', 'Dialysis or CRRT', 'Only if the renal failure is judged reversible', 'Others', 'Requirement of ICU?', 'Common Cause v Union of India, 2018 and 2023',
    'Dates of constitution of the Primary and Secondary Boards', 'Composition of the Boards', 'Decision of the Boards', 'Final decision about withdrawal/withholding', 'Protocol for withdrawal/withholding',
    'Vasopressors / inotropes', 'Short trial only, up to ______ hours', 'no escalation beyond ____ agents', '12.1  Feeding',
    '☑ Comfort feeding by mouth only', 'Nasogastric (Ryle’s) tube', 'up to ______ days, then review', 'Gastrostomy (PEG)', 'Only after discussion with the palliative care team',
    'A. PRIORITISE LIFE', 'B. BALANCED / CEILING OF CARE', 'C. PRIORITISE COMFORT', 'SECTION 19 — Declarations and signatures', '19.1  Patient', 'I have made these choices myself.',
    'delegated to my surrogate', 'Signature / thumb impression', 'Name in capitals', '19.2  Surrogate / healthcare proxy (primary and secondary surrogate decision-makers)',
    'so far as I can judge', 'Name & relationship', '19.3  Witnesses', 'Address / ID', '19.4  Clinicians', '| ROLE | NAME | REGISTRATION NO. | SIGNATURE | DATE |', '| Treating consultant | Dr Test | KMC 1 | Signed |',
    'Palliative care clinician', 'Nurse involved in the discussion',
  ];
  let at = 0;
  for (const words of form) {
    const found = lower.indexOf(words.toLowerCase(), words.startsWith('|') || words.startsWith('☑') ? 0 : at);
    assert.ok(found >= 0, `missing from the note: ${words}`);
    if (!words.startsWith('|') && !words.startsWith('☑')) at = found;
  }
  assert.match(note, /\| 1 \| [^|]* \| ☑ \|/, 'the ceiling box is ticked in the oxygen table');
});

import { gocNeedsSpeakers, gocSuggestedValue, GOC_DECISION_FIELDS } from '../app/goc-extract.ts';

test('Confirm all (V1): only one clear option the talk stated is applied; disagreements, locks and silence set nothing', () => {
  for (const file of readdirSync(MOCKS).filter((name) => name.endsWith('.txt'))) {
    const lines = gocLinesFromText(mockText(file));
    const found = extractGoc(lines);
    const r = draft();
    for (const field of GOC_DECISION_FIELDS) {
      const value = gocSuggestedValue(found.suggestions, field, gocFieldLock(r, field));
      const info = gocMarksFor(found.suggestions, field);
      if (!info.own.length || info.conflict) assert.equal(value, null, `${file} ${field}: nothing said or people disagree, so nothing is applied`);
      if (value !== null) { assert.ok(info.suggested.includes(value), `${file} ${field}: the value was said`); applyGocDecision(r, field, value); }
      if (!info.own.length) assert.equal(gocCurrentValue(r, field), null, `${file} ${field}: undiscussed stays No preference recorded`);
    }
  }
  const locked = emptyEctpr('P1', { name: 'A', uhid: '' }, 'Dr', NOW);
  assert.equal(gocSuggestedValue(extractGoc('Patient: No. I do not want CPR.').suggestions, 'cpr', gocFieldLock(locked, 'cpr')), null, 'a closed gate applies nothing');
  assert.equal(gocSuggestedValue(extractGoc('Patient: No. I do not want CPR.').suggestions, 'cpr', null), 'DNACPR');
});

test('"Who’s the doctor?" is asked only when no line says who spoke', () => {
  assert.equal(gocNeedsSpeakers([{ text: 'Would you want CPR?', speaker: null }, { text: 'No.', speaker: null }]), true);
  assert.equal(gocNeedsSpeakers(gocLinesFromText('Doctor: Would you want CPR?\nPatient: No.')), false);
  assert.equal(gocNeedsSpeakers([{ text: 'One line', speaker: null }]), false);
});
