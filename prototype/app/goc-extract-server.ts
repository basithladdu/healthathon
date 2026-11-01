// Optional: a server model reads the reviewed transcript for more suggestions, after separate consent. Sahara's phrase matcher works without it.
// The answer is checked: only known fields and options, and only quotes that are word for word in the transcript. It finds, it never advises.
import { NO_TREATMENT_ADVICE_RULE } from './ai-treatment-guard.ts';
import { chainLabel, chatProviders, jsonPart, runChatChain } from './ai-providers.ts';
import { checkedAiSuggestions, GOC_FIELD_VALUES } from './goc-extract.ts';

type Environment = NonNullable<Parameters<typeof chatProviders>[0]>;

const MAX_TEXT = 40_000;
const LIMIT_PER_HOUR = 20;
const recent = new Map<string, number[]>();

function reply(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' } });
}

const SYSTEM = `You read a transcript of a goals-of-care conversation. Find only what the patient or family clearly stated about these fields.
Fields and allowed values: ${JSON.stringify(GOC_FIELD_VALUES)}
Reply with {"suggestions":[{"field":"...","value":"...","quote":"..."}]}.
"quote" must be copied character for character from the transcript. Skip anything only asked, explained, or left undecided. Skip a field that was not addressed.
${NO_TREATMENT_ADVICE_RULE}`;

/** Injected transport and environment keep tests synthetic. */
export function createGocExtractHandler(send: typeof fetch = fetch, env: Environment = process.env) {
  return async (request: Request): Promise<Response> => {
    const providers = chatProviders(env);
    if (request.method === 'GET') return reply({ available: providers.length > 0, label: chainLabel(providers) });
    if (request.method !== 'POST') return reply({ error: 'Use POST.' }, 405);
    const origin = request.headers.get('origin'); const site = request.headers.get('sec-fetch-site');
    if (!origin || origin !== new URL(request.url).origin || (site && site !== 'same-origin')) return reply({ error: 'Sign in to Sahara first.' }, 401);
    const body = await request.json().catch(() => null) as { text?: unknown; consent?: unknown } | null;
    if (!body || body.consent !== true) return reply({ error: 'Agree to sending these words first.' }, 400);
    if (!providers.length) return reply({ error: 'AI reading is not set up. The phrase matcher still works.' }, 503);
    const text = typeof body.text === 'string' ? body.text.normalize('NFC') : '';
    if (!text.trim() || text.length > MAX_TEXT) return reply({ error: 'The transcript is empty or too long.' }, 413);
    const who = (request.headers.get('x-forwarded-for') || '').split(',')[0].trim() || 'unknown';
    const now = Date.now();
    const times = (recent.get(who) ?? []).filter((at) => now - at < 3_600_000);
    if (times.length >= LIMIT_PER_HOUR) return reply({ error: 'The hourly limit was reached. The phrase matcher still works.' }, 429);
    recent.set(who, [...times, now]);
    try {
      const result = await runChatChain(providers, { system: SYSTEM, user: text, json: true }, (content) => checkedAiSuggestions(jsonPart(content), text), { send });
      return reply({ suggestions: result.value, answeredBy: result.provider.label });
    } catch {
      return reply({ error: 'The AI service could not finish. The phrase matcher results stay.' }, 502);
    }
  };
}
