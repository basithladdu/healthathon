// Deliberately public, nonmedical intake. Never expose database write privileges.
import { readFeedbackBody, validateFeedback } from './schema.ts';
declare const Deno: { env: { get(name: string): string | undefined }; serve(handler: (request: Request) => Promise<Response>): void };
const PROJECT = 'https://cghlkqeqiokjqdionrkc.supabase.co';
const json = (value: unknown,status = 200) => Response.json(value,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff',...(status === 429 ? {'Retry-After':'60'} : {})}});
export async function handleFeedbackRequest(request: Request): Promise<Response> {
  if (request.method !== 'POST') return json({error:'Use the feedback form.'},405);
  let input: unknown;
  try { input = await readFeedbackBody(request); }
  catch (e) { return json({error:'Send a valid form within the size limit.'},e instanceof Error && e.message === 'SIZE' ? 413 : e instanceof Error && e.message === 'TYPE' ? 415 : 400); }
  const checked = validateFeedback(input);
  if (!checked.value) return json({error:checked.error},400);
  const token = request.headers.get('X-Feedback-Client') || '';
  if (!/^[a-f0-9]{64}$/.test(token)) return json({error:'Reload the form and try again.'},400);
  const base = Deno.env.get('SUPABASE_URL')?.replace(/\/$/,'');
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
  if (base !== PROJECT || !key) return json({error:'Feedback is not configured yet.'},503);
  try {
    // HMAC prevents recovering raw opaque tokens from a database-only leak.
    // Tokens can be rotated by attackers: only the durable GLOBAL quota bounds abuse.
    const hashKey = await crypto.subtle.importKey('raw',new TextEncoder().encode(key),{name:'HMAC',hash:'SHA-256'},false,['sign']);
    const clientHash = Array.from(new Uint8Array(await crypto.subtle.sign('HMAC',hashKey,new TextEncoder().encode(`feedback-client:${token}`))),v=>v.toString(16).padStart(2,'0')).join('');
    const response = await fetch(`${base}/rest/v1/rpc/sahara_submit_feedback`,{
      method:'POST',headers:{'Content-Type':'application/json',apikey:key,Authorization:`Bearer ${key}`},
      body:JSON.stringify({p_payload:checked.value,p_client_hash:clientHash}),redirect:'error',signal:AbortSignal.any([request.signal,AbortSignal.timeout(15_000)]),
    });
    const result = await response.json().catch(()=>null) as {code?:string;message?:string;id?:string;status?:string} | null;
    if (!response.ok) {
      if (result?.message === 'FEEDBACK_RATE_LIMIT') return json({error:'The form has reached its submission limit. Please try later.'},429);
      if (result?.message === 'FEEDBACK_ID_CONFLICT') return json({error:'This receipt belongs to a different submission. Reload the form before sending changes.'},409);
      return json({error:'Feedback could not be saved. Your form has not been cleared.'},503);
    }
    if (result?.id !== checked.value.submissionId || result.status !== (checked.value.kind === 'app' ? 'received' : 'pending_review')) return json({error:'Feedback could not be confirmed. Please retry without changing the form.'},502);
    return json({ok:true,id:result.id,status:result.status},201);
  } catch { return json({error:'Feedback is temporarily unavailable. Your form has not been cleared.'},503); }
}
if (typeof Deno !== 'undefined' && typeof Deno.serve === 'function') Deno.serve(handleFeedbackRequest);
