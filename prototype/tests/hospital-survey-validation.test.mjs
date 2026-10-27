import test from 'node:test';
import assert from 'node:assert/strict';
import { validateHospitalSurveyStep } from '../app/feedback/hospital-survey-validation.ts';

const fields = {
  role:'other',roleOther:'Synthetic contributor',fullName:'Synthetic Test',council:'other',councilOther:'Synthetic council',registrationState:'Karnataka',registrationNumber:'TEST-123',email:'',
  hospitalName:'Synthetic Hospital',institutionType:'other',institutionOther:'Synthetic institution',connection:'other',connectionOther:'Synthetic connection',state:'Karnataka',city:'Bengaluru',hospitalAddress:'Synthetic Road, Bengaluru',latitude:'12.9716',longitude:'77.5946',
  morphine:'yes',supply:'',palliativePhysicians:'',familyNotes:'',
};
const check = (step, changes = {}, services = ['opd'], confirmed = true, accuracy = true, consent = true) => validateHospitalSurveyStep(step, {...fields,...changes}, services, confirmed, accuracy, consent);

test('each valid step passes without requiring fields from a different step', () => {
  for (let step=0;step<4;step++) assert.deepEqual(check(step), {});
  assert.deepEqual(validateHospitalSurveyStep(3, {}, [], false, true, true), {});
  assert.deepEqual(check(0, {hospitalName:'',morphine:''}), {});
  assert.deepEqual(check(1, {fullName:'',council:'',morphine:''}), {});
  assert.deepEqual(check(2, {hospitalName:'',role:''}), {});
});

test('empty steps return inline field targets in form order', () => {
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(0, {}, [], false, false, false)), ['role','fullName','council','registrationNumber']);
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(1, {}, [], false, false, false)), ['hospitalName','institutionType','connection','state','city','hospitalAddress','location']);
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(2, {}, [], false, false, false)), ['services','morphine']);
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(3, {}, [], false, false, false)), ['accuracy','consent']);
});

test('all four Other branches require trimmed meaningful text and ignore inactive drafts', () => {
  for (const [step, selected, detail, replacement] of [[0,'role','roleOther','nurse'],[0,'council','councilOther','state_medical'],[1,'institutionType','institutionOther','private_hospital'],[1,'connection','connectionOther','works_here']]) {
    for (const invalid of ['', '   ', ' x ', 'x'.repeat(121), 'AB\u0001']) assert.ok(check(step, {[detail]:invalid})[detail], `${detail}: ${JSON.stringify(invalid)}`);
    for (const valid of [' OK ', 'x'.repeat(120)]) assert.deepEqual(check(step, {[detail]:valid}), {});
    assert.deepEqual(check(step, {[selected]:replacement,[detail]:'x'.repeat(121)}), {});
  }
});

test('text bounds and invalid characters match the intake limits', () => {
  for (const [step,key,min,max] of [[0,'fullName',2,120],[1,'hospitalName',2,160],[1,'city',2,80],[1,'hospitalAddress',5,500]]) {
    assert.ok(check(step, {[key]:'x'.repeat(min-1)})[key]);
    assert.ok(check(step, {[key]:'x'.repeat(max+1)})[key]);
    assert.ok(check(step, {[key]:'Allowed\u007f'})[key]);
    assert.deepEqual(check(step, {[key]:`  ${'x'.repeat(min)}  `}), {});
    assert.deepEqual(check(step, {[key]:'x'.repeat(max)}), {});
  }
  for (const key of ['palliativePhysicians','familyNotes']) {
    assert.deepEqual(check(2, {[key]:'x'.repeat(1000)}), {});
    assert.ok(check(2, {[key]:'x'.repeat(1001)})[key]);
    assert.ok(check(2, {[key]:'Text\u0000'})[key]);
    assert.deepEqual(check(2, {[key]:'Line one\nLine two\tallowed'}), {});
  }
});

test('role, council, institution, connection and state options cannot be free-form values', () => {
  for (const [step,key] of [[0,'role'],[0,'council'],[0,'registrationState'],[1,'institutionType'],[1,'connection'],[1,'state'],[2,'morphine']]) assert.ok(check(step, {[key]:'unsupported'})[key]);
  assert.deepEqual(check(0, {council:'nmc',councilOther:'stale draft',registrationState:'stale draft'}), {});
});

test('registration and optional email validate without browser popups', () => {
  for (const registrationNumber of ['','ab','a'.repeat(21),'TEST @ 1']) assert.ok(check(0, {registrationNumber}).registrationNumber);
  assert.deepEqual(check(0, {registrationNumber:'  AB/123-4  '}), {});
  for (const email of ['bad','a..b@example.org','a@-example.org','a@example.org\nBcc:x']) assert.ok(check(0, {email}).email);
  assert.deepEqual(check(0, {email:'   '}), {});
  assert.deepEqual(check(0, {email:' synthetic+qa@example.invalid '}), {});
});

test('location requires a confirmed finite pin within India', () => {
  assert.ok(check(1, {}, ['opd'], false).location);
  for (const changes of [{latitude:''},{longitude:' '},{latitude:'NaN'},{longitude:'Infinity'},{latitude:'5.99'},{latitude:'38.01'},{longitude:'67.99'},{longitude:'98.01'}]) assert.ok(check(1, changes).location);
  assert.deepEqual(check(1, {latitude:'6',longitude:'68'}), {});
  assert.deepEqual(check(1, {latitude:'38',longitude:'98'}), {});
});

test('service combinations and conditional optional supply preserve existing rules', () => {
  for (const services of [[],['opd','none'],['home','unknown'],['opd','opd'],['fake'],['opd','ipd','home','none']]) assert.ok(check(2, {}, services).services);
  for (const services of [['opd','ipd','home'],['none'],['unknown']]) assert.deepEqual(check(2, {}, services), {});
  for (const supply of ['','regular','intermittent','unknown']) assert.deepEqual(check(2, {supply}), {});
  assert.ok(check(2, {supply:'fake'}).supply);
  assert.deepEqual(check(2, {morphine:'no',supply:'stale draft'}), {});
});

test('validation reports both final acknowledgements without mutating draft answers', () => {
  const draft = {...fields}, before = JSON.stringify(draft), services=['opd'];
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(3,draft,services,true,false,false)), ['accuracy','consent']);
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(3,draft,services,true,true,false)), ['consent']);
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(3,draft,services,true,false,true)), ['accuracy']);
  assert.equal(JSON.stringify(draft),before);
  assert.deepEqual(services,['opd']);
});
