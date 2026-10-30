// Break-glass SMS to the primary decision-maker (tech spec §7). Server only.
// Provider is picked from environment variables; with none set we say so honestly and send nothing.
//   Twilio: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN, TWILIO_FROM (an SMS-capable number or sender ID)
//   MSG91:  MSG91_AUTH_KEY, MSG91_TEMPLATE_ID (a DLT-approved Flow template with variables ##patient## and ##time##)
import { indianMobileE164 } from '../../ed-card-snapshot.ts';

export type SmsResult = { sent: boolean; provider: 'twilio' | 'msg91' | null; status: string };

export function smsProvider(env: Record<string, string | undefined> = process.env): 'twilio' | 'msg91' | null {
  if (env.TWILIO_ACCOUNT_SID && env.TWILIO_AUTH_TOKEN && env.TWILIO_FROM) return 'twilio';
  if (env.MSG91_AUTH_KEY && env.MSG91_TEMPLATE_ID) return 'msg91';
  return null;
}

export async function sendBreakGlassSms(
  input: { phone: string | null | undefined; patientFirstName: string; at: Date },
  env: Record<string, string | undefined> = process.env,
  fetcher: typeof fetch = fetch,
): Promise<SmsResult> {
  const provider = smsProvider(env);
  if (!provider) return { sent: false, provider: null, status: 'not-configured' };
  const to = indianMobileE164(input.phone);
  if (!to) return { sent: false, provider, status: 'no-valid-number' };
  const time = input.at.toLocaleString('en-IN', { timeZone: 'Asia/Kolkata', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  const text = `Sahara: an emergency doctor opened ${input.patientFirstName}'s full care plan at ${time} (break-glass, reason logged). If this is unexpected, call the palliative care team.`;
  try {
    if (provider === 'twilio') {
      const sid = env.TWILIO_ACCOUNT_SID!;
      const response = await fetcher(`https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(sid)}/Messages.json`, {
        method: 'POST',
        headers: { Authorization: `Basic ${btoa(`${sid}:${env.TWILIO_AUTH_TOKEN}`)}`, 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ To: to, From: env.TWILIO_FROM!, Body: text }).toString(),
        signal: AbortSignal.timeout(10_000),
      });
      return response.ok ? { sent: true, provider, status: 'sent' } : { sent: false, provider, status: `provider-error-${response.status}` };
    }
    const response = await fetcher('https://control.msg91.com/api/v5/flow', {
      method: 'POST',
      headers: { authkey: env.MSG91_AUTH_KEY!, 'Content-Type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({ template_id: env.MSG91_TEMPLATE_ID, short_url: '0', recipients: [{ mobiles: to.replace(/^\+/, ''), patient: input.patientFirstName, time }] }),
      signal: AbortSignal.timeout(10_000),
    });
    const body = await response.json().catch(() => null) as { type?: string } | null;
    return response.ok && body?.type !== 'error' ? { sent: true, provider, status: 'sent' } : { sent: false, provider, status: `provider-error-${response.status}` };
  } catch {
    return { sent: false, provider, status: 'provider-unreachable' };
  }
}
