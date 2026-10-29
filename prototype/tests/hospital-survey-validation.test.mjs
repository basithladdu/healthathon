import test from 'node:test';
import assert from 'node:assert/strict';
import { validateHospitalSurveyStep } from '../app/feedback/hospital-survey-validation.ts';

const fields = {
  role:'other',roleOther:'Synthetic contributor',fullName:'Synthetic Test',council:'other',councilOther:'Synthetic council',registrationState:'Karnataka',registrationNumber:'TEST-123',email:'',
  hospitalName:'Synthetic Hospital',institutionType:'other',institutionOther:'Synthetic institution',connection:'other',connectionOther:'Synthetic connection',state:'Karnataka',city:'Bengaluru',hospitalAddress:'Synthetic Road, Bengaluru',latitude:'12.9716',longitude:'77.5946',
  morphine:'yes',supply:'',palliativePhysicians:'',familyNotes:'',
};
const check = (step, changes = {}, services = ['opd'], confirmed = true, accuracy = true, consent = true) => validateHospitalSurveyStep(step, {...fields,...changes}, services, confirmed, accuracy, consent);

test('a name or initials, a hospital name and consent can complete the entire survey', () => {
  for (let step=0;step<4;step++) {
    assert.deepEqual(check(step), {});
    assert.deepEqual(validateHospitalSurveyStep(step,{fullName:'SK',hospitalName:'A'},[],false,false,true), {});
  }
});

test('only the name (initials are fine), hospital name and consent are required', () => {
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(0, {}, [], false, false, false)), ['fullName']);
  assert.ok(validateHospitalSurveyStep(0, {fullName:'S'}, [], false, false, false).fullName);
  assert.deepEqual(validateHospitalSurveyStep(0, {fullName:'S. K.'}, [], false, false, false), {});
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(1, {}, [], false, false, false)), ['hospitalName']);
  assert.deepEqual(validateHospitalSurveyStep(2, {}, [], false, false, false), {});
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(3, {}, [], false, false, false)), ['consent']);
});

test('all four Other details may be blank or a single character and ignore inactive drafts', () => {
  for (const [step, selected, detail, replacement] of [[0,'role','roleOther','nurse'],[0,'council','councilOther','state_medical'],[1,'institutionType','institutionOther','private_hospital'],[1,'connection','connectionOther','works_here']]) {
    for (const valid of ['', '   ', ' x ', 'x'.repeat(120)]) assert.deepEqual(check(step, {[detail]:valid}), {});
    for (const invalid of ['x'.repeat(121), 'AB\u0001']) assert.ok(check(step, {[detail]:invalid})[detail]);
    assert.deepEqual(check(step, {[selected]:replacement,[detail]:'x'.repeat(121)}), {});
  }
});

test('text length and unsafe character bounds remain enforced without formatting restrictions', () => {
  for (const [step,key,max] of [[0,'fullName',120],[0,'registrationNumber',120],[0,'email',254],[1,'hospitalName',160],[1,'city',80],[1,'hospitalAddress',500],[2,'palliativePhysicians',1000],[2,'familyNotes',1000]]) {
    assert.ok(check(step, {[key]:'x'.repeat(max+1)})[key]);
    assert.ok(check(step, {[key]:'Allowed\u007f'})[key]);
    if(key!=='fullName') assert.deepEqual(check(step, {[key]:' x '}), {});
    assert.deepEqual(check(step, {[key]:'x'.repeat(max)}), {});
    if(key!=='hospitalName'&&key!=='fullName') assert.deepEqual(check(step, {[key]:''}), {});
  }
  assert.ok(check(1,{hospitalName:'   '}).hospitalName);
  assert.deepEqual(check(2, {familyNotes:'Line one\nLine two\tallowed'}), {});
});

test('blank choices are allowed while malformed option values are rejected', () => {
  for (const [step,key] of [[0,'role'],[0,'council'],[0,'registrationState'],[1,'institutionType'],[1,'connection'],[1,'state'],[2,'morphine']]) {
    assert.ok(check(step, {[key]:'unsupported'})[key]);
    assert.deepEqual(check(step, {[key]:''}), {});
  }
  assert.deepEqual(check(0, {council:'nmc',councilOther:'stale draft',registrationState:'stale draft'}), {});
});

test('registration spacing and imperfect optional email do not block useful responses', () => {
  for (const registrationNumber of ['','ab','TEST (KMC): 123 45','TEMP. 1']) assert.deepEqual(check(0, {registrationNumber}), {});
  for (const email of ['bad','a..b@example.org','a@-example.org','synthetic at example dot invalid','   ']) assert.deepEqual(check(0, {email}), {});
});

test('location is optional but a claimed confirmed pin must be finite and within India', () => {
  assert.deepEqual(check(1, {latitude:'',longitude:''}, [], false), {});
  assert.deepEqual(check(1, {latitude:'stale preview',longitude:'stale preview'}, [], false), {});
  for (const changes of [{latitude:''},{longitude:' '},{latitude:'NaN'},{longitude:'Infinity'},{latitude:'5.99'},{latitude:'38.01'},{longitude:'67.99'},{longitude:'98.01'}]) assert.ok(check(1, changes).location);
  assert.deepEqual(check(1, {latitude:'6',longitude:'68'}), {});
  assert.deepEqual(check(1, {latitude:'38',longitude:'98'}), {});
});

test('empty services and supply are allowed while conflicting choices remain invalid', () => {
  for (const services of [['opd','none'],['home','unknown'],['opd','opd'],['fake'],['opd','ipd','home','none']]) assert.ok(check(2, {}, services).services);
  for (const services of [[],['opd','ipd','home'],['none'],['unknown']]) assert.deepEqual(check(2, {}, services), {});
  for (const supply of ['','regular','intermittent','unknown']) assert.deepEqual(check(2, {supply}), {});
  assert.ok(check(2, {supply:'fake'}).supply);
  assert.deepEqual(check(2, {morphine:'no',supply:'stale draft'}), {});
});

test('consent is never inferred from accuracy and checking does not change draft answers', () => {
  const draft = {...fields}, before = JSON.stringify(draft), services=['opd'];
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(3,draft,services,true,false,false)), ['consent']);
  assert.deepEqual(Object.keys(validateHospitalSurveyStep(3,draft,services,true,true,false)), ['consent']);
  assert.deepEqual(validateHospitalSurveyStep(3,draft,services,true,false,true), {});
  assert.equal(JSON.stringify(draft),before);
  assert.deepEqual(services,['opd']);
});
