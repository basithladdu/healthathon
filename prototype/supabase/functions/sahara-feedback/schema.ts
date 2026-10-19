// Shared by the browser, Next proxy and Edge intake. No credentials or IO.
export const INDIA_STATES = ['Andaman and Nicobar Islands','Andhra Pradesh','Arunachal Pradesh','Assam','Bihar','Chandigarh','Chhattisgarh','Dadra and Nagar Haveli and Daman and Diu','Delhi','Goa','Gujarat','Haryana','Himachal Pradesh','Jammu and Kashmir','Jharkhand','Karnataka','Kerala','Ladakh','Lakshadweep','Madhya Pradesh','Maharashtra','Manipur','Meghalaya','Mizoram','Nagaland','Odisha','Puducherry','Punjab','Rajasthan','Sikkim','Tamil Nadu','Telangana','Tripura','Uttar Pradesh','Uttarakhand','West Bengal'] as const;
export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
export const MAX_BODY = 12_000;
export type FeedbackPayload = Record<string, string | number | boolean | string[]> & { kind: 'app' | 'hospital'; submissionId: string };
export type Validation = { value: FeedbackPayload; error?: never } | { error: string; value?: never };
const appKeys = ['kind','submissionId','website','rating','role','category','message','consent'];
const hospitalKeys = ['kind','submissionId','website','role','roleOther','fullName','council','councilOther','registrationState','registrationNumber','hospitalName','institutionType','institutionOther','connection','connectionOther','state','city','latitude','longitude','services','morphine','supply','familyNotes','accuracy','consent'];
export function normalizeMobile(input: unknown): string | null {
  if (typeof input !== 'string' || input.length > 64) return null;
  if (input.trim() === '') return '';
  let value = input.trim().replace(/[ ()-]/g,'');
  if (value.startsWith('+91')) value = value.slice(3);
  return /^[6-9][0-9]{9}$/.test(value) ? value : null;
}
export function normalizeEmail(input: unknown): string | null {
  if (typeof input !== 'string') return null;
  const value = input.trim();
  if (value === '') return '';
  const parts = value.split('@');
  if (value.length > 254 || parts.length !== 2 || parts[0].length > 64 || !/^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*$/.test(parts[0])) return null;
  const labels = parts[1].split('.');
  return labels.length >= 2 && labels.every(label => /^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$/.test(label)) && /^[A-Za-z]{2,63}$/.test(labels.at(-1) || '') ? value : null;
}
const choice = (value: unknown, values: readonly string[]) => typeof value === 'string' && values.includes(value);
const text = (value: unknown, min: number, max: number) => typeof value === 'string' && value.trim().length >= min && value.trim().length <= max && !/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(value);
export function validateFeedback(input: unknown): Validation {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return { error: 'Send a valid form.' };
  const data = input as Record<string, unknown>;
  const contacts = data.kind === 'hospital' && ('mobile' in data || 'email' in data);
  const keys = data.kind === 'app' ? appKeys : data.kind === 'hospital' ? [...hospitalKeys,...(contacts ? ['mobile','email'] : [])] : [];
  if (!keys.length || Object.keys(data).length !== keys.length || Object.keys(data).some(key => !keys.includes(key))) return { error: 'Send only the form fields shown.' };
  if (typeof data.submissionId !== 'string' || !UUID.test(data.submissionId) || data.website !== '' || data.consent !== true) return { error: 'Check the consent and reload the form if needed.' };
  if (data.kind === 'app') {
    if (!Number.isInteger(data.rating) || Number(data.rating) < 1 || Number(data.rating) > 5 || !choice(data.role, ['patient','caregiver','clinician','other']) || !choice(data.category, ['navigation','design','bug','idea','other']) || !text(data.message, 0, 1000)) return { error: 'Check your rating, role and feedback.' };
  } else {
    if (contacts && (normalizeMobile(data.mobile) === null || normalizeEmail(data.email) === null)) return { error: 'Check your optional mobile number and email, or leave them blank.' };
    if (!choice(data.role,['oncologist','palliative_physician','physician','resident','nurse','pharmacist','other']) || !choice(data.council,['nmc','state_medical','state_nursing','state_pharmacy','other']) || !choice(data.institutionType,['government_hospital','government_medical_college','private_hospital','private_medical_college','private_clinic','ngo_hospice','other']) || !choice(data.connection,['works_here','refers_here','other'])) return { error: 'Choose your role, council and hospital details.' };
    for (const [selected, extra] of [['role','roleOther'],['council','councilOther'],['institutionType','institutionOther'],['connection','connectionOther']]) {
      if (!text(data[extra], data[selected] === 'other' ? 2 : 0, 120) || (data[selected] !== 'other' && data[extra] !== '')) return { error: 'Complete the Other details only where selected.' };
    }
    if (!text(data.fullName,2,120) || typeof data.registrationNumber !== 'string' || !/^[A-Za-z0-9/-]{3,20}$/.test(data.registrationNumber.trim()) || (data.council === 'nmc' ? data.registrationState !== '' : !choice(data.registrationState,INDIA_STATES))) return { error: 'Check your professional registration details.' };
    if (!text(data.hospitalName,2,160) || !choice(data.state,INDIA_STATES) || !text(data.city,2,80) || typeof data.latitude !== 'number' || !Number.isFinite(data.latitude) || data.latitude < 6 || data.latitude > 38 || typeof data.longitude !== 'number' || !Number.isFinite(data.longitude) || data.longitude < 68 || data.longitude > 98) return { error: 'Check the hospital name, state, city and hospital map pin in India.' };
    if (!Array.isArray(data.services) || data.services.length < 1 || data.services.length > 3 || new Set(data.services).size !== data.services.length || data.services.some(v => !choice(v,['opd','ipd','home','none','unknown'])) || (data.services.some(v => v === 'none' || v === 'unknown') && data.services.length !== 1)) return { error: 'Choose services; None or Don’t know must stand alone.' };
    if (!choice(data.morphine,['yes','no','unknown']) || (data.morphine === 'yes' ? !choice(data.supply,['','regular','intermittent','unknown']) : data.supply !== '') || !text(data.familyNotes,0,1000) || data.accuracy !== true) return { error: 'Check service availability and the accuracy declaration.' };
  }
  // Explicit ordered key construction gives deterministic idempotency hashing.
  const value: Record<string, unknown> = {};
  for (const key of keys) value[key] = typeof data[key] === 'string' ? (data[key] as string).trim() : key === 'services' ? [...data.services as string[]].sort() : data[key];
  value.submissionId = (value.submissionId as string).toLowerCase();
  // Do not synthesize new keys for old clients: their receipt hashes must not change.
  if (contacts) { value.mobile = normalizeMobile(data.mobile); value.email = normalizeEmail(data.email); }
  return { value: value as FeedbackPayload };
}
export async function readFeedbackBody(request: Request): Promise<unknown> {
  if (!/^application\/json(?:\s*;\s*charset=utf-8)?$/i.test(request.headers.get('content-type') || '') || (request.headers.get('content-encoding') && request.headers.get('content-encoding') !== 'identity')) throw new Error('TYPE');
  const length = request.headers.get('content-length');
  if (length && (!/^\d+$/.test(length) || Number(length) > MAX_BODY)) throw new Error('SIZE');
  if (!request.body) throw new Error('BODY');
  const reader = request.body.getReader();
  const signal = AbortSignal.any([request.signal,AbortSignal.timeout(8_000)]);
  const cancel = () => { void reader.cancel().catch(() => {}); };
  signal.addEventListener('abort',cancel,{once:true});
  const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) { signal.throwIfAborted(); const part = await reader.read(); signal.throwIfAborted(); if (part.done) break; size += part.value.length; if (size > MAX_BODY) throw new Error('SIZE'); chunks.push(part.value); }
    const all = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { all.set(chunk,offset); offset += chunk.length; }
    return JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(all));
  } finally { signal.removeEventListener('abort',cancel); cancel(); reader.releaseLock(); }
}
