import test from 'node:test';
import assert from 'node:assert/strict';
import { asksForTreatmentAdvice, containsTreatmentAdvice, isCarePlanAdvicePurpose, TREATMENT_ADVICE_CODE } from '../app/ai-treatment-guard.ts';
import { createSaharaAssistHandler } from '../app/sahara-assist-server.ts';

// SVK Saanthvana spec, "Won't: AI treatment advice" — for the emergency care plan (ECTPR), the living will, and everything else.
test('questions asking the AI to decide or recommend care are recognised', () => {
  for (const question of [
    'Should we choose CPR in the emergency care plan?',
    'Should my mother go on a ventilator?',
    'Can I increase the morphine dose?',
    'What medicine should I give for the pain?',
    'Which treatment is best for her?',
    'Do you recommend signing the DNR?',
    'What should I write in the living will about ICU?',
    'How much should I give tonight?',
    'क्या हमें CPR करवाना चाहिए?',
    'कौन सी दवा दें?',
    'kaun si dawa deni hai',
    'कौन सा इलाज बेहतर है',
    'کون سی دوا دینی چاہیے؟',
    'ఈ మందు ఇవ్వాలా?',
    'ಈ ಔಷಧ ಕೊಡಬೇಕಾ?',
  ]) assert.equal(asksForTreatmentAdvice(question), true, question);
});

test('questions about what is already written are not refused', () => {
  for (const question of [
    'When is my review?',
    'When should I take the tablet?',
    'What does the prescription say about morphine?',
    'What dose of morphine was prescribed?',
    'How much was the hospital bill?',
    'What did the doctor write about CPR in the care plan?',
    'रिपोर्ट में हीमोग्लोबिन कितना लिखा है?',
  ]) assert.equal(asksForTreatmentAdvice(question), false, question);
});

test('generated advice is caught unless it is quoted from the cited document', () => {
  assert.equal(containsTreatmentAdvice('You should choose DNR.'), true);
  assert.equal(containsTreatmentAdvice('I would recommend stopping the tablets.'), true);
  assert.equal(containsTreatmentAdvice('It is best to avoid the ICU.'), true);
  assert.equal(containsTreatmentAdvice('आपको दवा बंद करनी चाहिए।'), true);
  assert.equal(containsTreatmentAdvice('Your review is on 12/11/2026.'), false);
  assert.equal(containsTreatmentAdvice('You should take paracetamol after food.', 'Tab paracetamol should be taken after food'), true,
    'only the exact advising phrase from the document passes as a read-back');
  assert.equal(containsTreatmentAdvice('The note says: you should come back on Monday.', 'Plan: you should come back on Monday'), false);
  assert.equal(isCarePlanAdvicePurpose('ECTPR'), true); assert.equal(isCarePlanAdvicePurpose('living-will'), true); assert.equal(isCarePlanAdvicePurpose('bills'), false);
});

const TOKEN = 'Bearer aaa.bbb.ccc';
function transport(model) {
  const calls = [];
  const send = async (url, init = {}) => {
    calls.push(String(url));
    if (String(url).endsWith('/auth/v1/user')) return Response.json({ id: '6f9619ff-8b86-4d01-b42d-00cf4fc964ff', email_confirmed_at: '2026-10-01T00:00:00Z' });
    if (String(url).includes('sahara_reserve_assist')) return Response.json(true);
    if (String(url).endsWith('/chat/completions')) return Response.json({ choices: [{ finish_reason: 'stop', message: { content: model } }] });
    throw new Error(`unexpected ${url} ${init.method}`);
  };
  return { send, calls };
}
const lines = [{ ref: 'D1', lines: [{ page: 1, line: 1, text: 'ECTPR: CPR not discussed yet. Review on 12/11/2026' }] }];
const ask = (question, extra = {}) => new Request('https://sahara.wedevit.in/api/sahara-assist/ask', { method: 'POST', headers: { Authorization: TOKEN, 'Content-Type': 'application/json' },
  body: JSON.stringify({ requestId: crypto.randomUUID(), consent: true, question, sourceHash: 'a'.repeat(64), documents: lines, ...extra }) });
const env = { GEMINI_API_KEY: 'g', GROQ_API_KEY: 'q' };

test('an advice question about the emergency care plan is refused before anything is reserved or sent', async () => {
  const { send, calls } = transport('{"found":false,"answer":"","citations":[]}');
  const response = await createSaharaAssistHandler(send, env)(ask('Should we choose CPR in the emergency care plan?'));
  assert.equal(response.status, 422);
  assert.equal((await response.json()).code, TREATMENT_ADVICE_CODE);
  assert.ok(!calls.some((url) => url.includes('sahara_reserve_assist') || url.endsWith('/chat/completions')));
  const tagged = await createSaharaAssistHandler(send, env)(ask('What does it say?', { purpose: 'living-will' }));
  assert.equal(tagged.status, 422);
});

test('a model answer that recommends a care-plan choice is never shown, and no second model is asked', async () => {
  const { send, calls } = transport('{"found":true,"answer":"You should choose DNR before 12/11/2026.","citations":[{"document":"D1","page":1,"line":1}]}');
  const response = await createSaharaAssistHandler(send, env)(ask('What does my ECTPR say about CPR?'));
  assert.equal(response.status, 422);
  assert.equal((await response.json()).code, TREATMENT_ADVICE_CODE);
  assert.equal(calls.filter((url) => url.endsWith('/chat/completions')).length, 1);
});

test('a cited read-back of the care plan is still answered', async () => {
  const { send } = transport('{"found":true,"answer":"The ECTPR says CPR was not discussed yet; review on 12/11/2026.","citations":[{"document":"D1","page":1,"line":1}]}');
  const response = await createSaharaAssistHandler(send, env)(ask('What does my ECTPR say about CPR?'));
  assert.equal(response.status, 200);
  assert.equal((await response.json()).found, true);
});
