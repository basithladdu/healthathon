import test from 'node:test';
import assert from 'node:assert/strict';
import { validateFeedback } from '../supabase/functions/sahara-feedback/schema.ts';
import { handleFeedbackRequest } from '../supabase/functions/sahara-feedback/index.ts';
import { POST } from '../app/api/feedback/route.ts';

const id = '00000000-0000-4000-8000-000000000004';
const partial = {
  kind:'hospital',submissionId:id,website:'',role:'',roleOther:'',fullName:'',
  council:'',councilOther:'',registrationState:'',registrationNumber:'',
  hospitalName:'Synthetic Test Hospital',institutionType:'',institutionOther:'',
  connection:'',connectionOther:'',state:'',city:'',latitude:null,longitude:null,
  services:[],morphine:'',supply:'',familyNotes:'',accuracy:false,consent:true,
  mobile:'',email:'',hospitalAddress:'',palliativePhysicians:'',locationConfirmed:false,
  formVersion:'hospital-survey-v4'
};

test('v4 accepts a hospital name and consent without inventing missing details', () => {
  assert.deepEqual(validateFeedback(partial).value,partial);
  const value = validateFeedback({...partial,hospitalName:' H '}).value;
  assert.equal(value.hospitalName,'H');
  assert.equal(value.accuracy,false);
  assert.equal(value.locationConfirmed,false);
  assert.equal(value.latitude,null);
  assert.equal(value.longitude,null);
  assert.deepEqual(value.services,[]);
  for (const field of ['role','council','registrationNumber','email','state','city','morphine']) assert.equal(value[field],'');
});

test('v4 preserves imperfect optional answers for review instead of silently dropping them', () => {
  const value = validateFeedback({...partial,fullName:' X ',registrationNumber:'  KA M.C. / 001 (renewal pending)  ',email:'  name at example  ',city:' B ',hospitalAddress:' x '}).value;
  assert.equal(value.fullName,'X');
  assert.equal(value.registrationNumber,'KA M.C. / 001 (renewal pending)');
  assert.equal(value.email,'name at example');
  assert.equal(value.city,'B');
  assert.equal(value.hospitalAddress,'x');
  assert.deepEqual(validateFeedback(JSON.parse(JSON.stringify(value))).value,value);
});

test('v4 Other descriptions are optional, with no stale details under a different selection', () => {
  for (const [selected,detail] of [['role','roleOther'],['council','councilOther'],['institutionType','institutionOther'],['connection','connectionOther']]) {
    for (const answer of ['', ' ', ' X ', ' Synthetic description ']) {
      const value = validateFeedback({...partial,[selected]:'other',[detail]:answer}).value;
      assert.ok(value,`${detail} accepts a short or empty answer`);
      assert.equal(value[detail],answer.trim());
    }
    assert.ok(validateFeedback({...partial,[detail]:'stale answer'}).error);
  }
});

test('v4 distinguishes an optional map from an explicitly confirmed hospital pin', () => {
  assert.ok(validateFeedback({...partial,latitude:10,longitude:76,locationConfirmed:true}).value);
  for (const change of [
    {latitude:0,longitude:0},{latitude:10,longitude:76},{latitude:null,longitude:76},
    {locationConfirmed:true},{locationConfirmed:'true'},
    {locationConfirmed:true,latitude:99,longitude:76},
    {locationConfirmed:true,latitude:10,longitude:NaN},
    {locationConfirmed:true,latitude:'10',longitude:76}
  ]) assert.ok(validateFeedback({...partial,...change}).error,JSON.stringify(change));
});

test('v4 still enforces consent, exact fields, bounded text, types and anti-spam checks', () => {
  for (const change of [
    {hospitalName:''},{hospitalName:'  '},{hospitalName:'x'.repeat(161)},
    {consent:false},{consent:'true'},{accuracy:'true'},
    {website:'https://spam.invalid'},{submissionId:'bad'},
    {formVersion:'hospital-survey-v5'},{formVersion:' hospital-survey-v4 '},
    {role:'verified_doctor'},{state:'Unrecognized'},{registrationState:null},
    {mobile:'9000000000'},{email:null},{email:'x'.repeat(255)},
    {registrationNumber:'x'.repeat(121)},{familyNotes:'bad\u0000value'},
    {patientId:'hidden'},{role:{value:'nurse'}}
  ]) assert.ok(validateFeedback({...partial,...change}).error,JSON.stringify(change));
  for (const key of Object.keys(partial)) {
    const omitted = {...partial}; delete omitted[key];
    assert.ok(validateFeedback(omitted).error,`${key} must still be represented in the contract`);
  }
});

test('v4 accepts no service answer without allowing contradictory selections', () => {
  for (const services of [[],['none'],['unknown'],['home','opd','ipd']]) assert.ok(validateFeedback({...partial,services}).value);
  for (const services of [['none','opd'],['unknown','home'],['opd','opd'],['unrecognized'],'opd',null]) assert.ok(validateFeedback({...partial,services}).error);
  for (const morphine of ['','yes','no','unknown']) assert.ok(validateFeedback({...partial,morphine}).value);
  assert.ok(validateFeedback({...partial,morphine:'yes',supply:'intermittent'}).value);
  assert.ok(validateFeedback({...partial,morphine:'no',supply:'regular'}).error);
});

test('v4 is explicitly versioned and does not relax legacy hospital forms', () => {
  const legacy = {...partial}; delete legacy.formVersion;
  assert.ok(validateFeedback(legacy).error);
});

test('v4 travels through the Next proxy and Edge intake with the actual fields retained', async t => {
  const oldFetch = globalThis.fetch, oldDeno = globalThis.Deno;
  globalThis.Deno = {env:{get:key=>({SUPABASE_URL:'https://cghlkqeqiokjqdionrkc.supabase.co',SUPABASE_SERVICE_ROLE_KEY:'synthetic-only-service-key'})[key]}};
  t.after(()=>{globalThis.fetch=oldFetch;if(oldDeno===undefined)delete globalThis.Deno;else globalThis.Deno=oldDeno;});
  const expected = {...partial,email:'name at example',registrationNumber:'KA M.C. / 001'};
  let edgeCalls = 0, rpcCalls = 0;
  globalThis.fetch = async (url,init) => {
    if (String(url).endsWith('/functions/v1/sahara-feedback')) {
      edgeCalls++;
      assert.equal(init.headers.Authorization,undefined);
      return handleFeedbackRequest(new Request(url,init));
    }
    assert.match(String(url),/\/rpc\/sahara_submit_feedback$/);
    rpcCalls++;
    assert.deepEqual(JSON.parse(init.body).p_payload,expected);
    return Response.json({id,status:'pending_review'});
  };
  const response = await POST(new Request('https://sahara.example/api/feedback',{
    method:'POST',headers:{Origin:'https://sahara.example','Content-Type':'application/json'},body:JSON.stringify(expected)
  }));
  assert.equal(response.status,201);
  assert.deepEqual(await response.json(),{ok:true,id,status:'pending_review'});
  assert.equal(edgeCalls,1);
  assert.equal(rpcCalls,1);
});
