import { readFeedbackBody, validateFeedback } from '../../../supabase/functions/sahara-feedback/schema.ts';
export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
const EDGE = 'https://cghlkqeqiokjqdionrkc.supabase.co/functions/v1/sahara-feedback';
const COOKIE = 'sahara_feedback_client';
const json = (error: string,status: number) => Response.json({error},{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
function sameOrigin(request: Request) {
  try {
    if (request.headers.get('sec-fetch-site') && request.headers.get('sec-fetch-site') !== 'same-origin') return false;
    const url = new URL(request.url); const host = request.headers.get('host');
    if (!['http:','https:'].includes(url.protocol) || (host && !/^(?:\[[a-fA-F0-9:]+\]|[a-zA-Z0-9.-]+)(?::\d{1,5})?$/.test(host))) return false;
    return request.headers.get('origin') === (host ? new URL(`${url.protocol}//${host}`).origin : url.origin);
  } catch { return false; }
}
export async function POST(request: Request) {
  if (!sameOrigin(request)) return json('Open this action from Sahara’s feedback page.',403);
  let input: unknown;
  try { input = await readFeedbackBody(request); }
  catch (e) { return json('Send a valid form within the size limit.',e instanceof Error && e.message === 'SIZE' ? 413 : e instanceof Error && e.message === 'TYPE' ? 415 : 400); }
  const checked = validateFeedback(input);
  if (!checked.value) return json(checked.error,400);
  const existing = request.headers.get('cookie')?.split(';').map(v=>v.trim()).find(v=>v.startsWith(`${COOKIE}=`))?.slice(COOKIE.length+1);
  const token = existing && /^[a-f0-9]{64}$/.test(existing) ? existing : Array.from(crypto.getRandomValues(new Uint8Array(32)),v=>v.toString(16).padStart(2,'0')).join('');
  try {
    // Public Edge endpoint: no anon/service secret is needed in this proxy or browser.
    const remote = await fetch(EDGE,{method:'POST',headers:{'Content-Type':'application/json','X-Feedback-Client':token},body:JSON.stringify(checked.value),cache:'no-store',redirect:'error',signal:AbortSignal.any([request.signal,AbortSignal.timeout(20_000)])});
    const result = await remote.json().catch(()=>null) as {ok?:boolean;id?:string;status?:string;error?:string} | null;
    let outgoing: Response;
    if (remote.ok && result?.ok === true && result.id === checked.value.submissionId && result.status === (checked.value.kind === 'app' ? 'received' : 'pending_review')) outgoing = Response.json({ok:true,id:result.id,status:result.status},{status:201});
    else outgoing = json(remote.status === 429 ? 'The form has reached its submission limit. Please try later.' : remote.status === 409 ? 'This receipt belongs to a different submission. Reload the form before sending changes.' : 'Feedback could not be saved. Your form has not been cleared.',remote.status === 429 ? 429 : remote.status === 409 ? 409 : 503);
    outgoing.headers.set('Cache-Control','no-store'); outgoing.headers.set('X-Content-Type-Options','nosniff');
    if (outgoing.status === 429) outgoing.headers.set('Retry-After','60');
    // Keep the token even after failure; retries share the same durable quota bucket.
    outgoing.headers.set('Set-Cookie',`${COOKIE}=${token}; HttpOnly; SameSite=Strict; Path=/api/feedback; Max-Age=86400${process.env.NODE_ENV === 'production' ? '; Secure' : ''}`);
    return outgoing;
  } catch { return json('Feedback is temporarily unavailable. Your form has not been cleared.',503); }
}
