// Called only through the same-origin Next proxy. Deploy with verify_jwt=false;
// the function enforces a separate high-entropy server capability.
declare const Deno: { env: { get(name: string): string | undefined }; serve(handler: (request: Request) => Promise<Response>): void };

const MAX_PRESCRIPTION_PAGES = 5;
const MAX_MEDICINE_IMAGES = 10;
const MAX_IMAGE_BYTES = 500 * 1024;
const MAX_TOTAL_BYTES = 8 * 1024 * 1024;
const MAX_BODY_BYTES = 12 * 1024 * 1024;
const MAX_PROVIDER_RESPONSE_BYTES = 1024 * 1024;
const MODEL_NAME = 'gemini-3.8-flash';

const SYSTEM_INSTRUCTION = 'You organize a patient medication plan from prescription pages and medicine packaging. This is transcription and matching only, never medical advice. Prefer marking an item unknown over guessing. Return concise structured data.';
const ORGANIZE_PROMPT = 'Match medicine-strip photos to all medicines and directions found in the prescription pages. A prescription can contain multiple medicines. Return one entry per prescribed medicine. Split slash-separated named products into separate entries unless the prescription clearly names one fixed-dose combination product. Treat the prescription as the source of truth for medicineName, strength, form, quantity per dose, frequency, meal relation, clock times, route, and course duration. Use package images only to match or corroborate identity. Never replace the prescribed name with a package brand. A different package brand is only a genericEquivalent match when visible generic and strength evidence support it; lower confidence and explain the mismatch. Read pages even when they are rotated and use printed item numbering rather than upload order. Keep every dose, clock time, meal instruction, and duration tied to the same prescription line. Do not return monitoring, follow-up, review, or other non-medicine tasks as medicines. Preserve explicit X N DAYS and different doses at different administrations in instructions. Use zero-based image indexes exactly as labelled. If a medicine has no matching strip, use medicineImageIndex -1 and identityMatch none, then explain it in uncertainties. Never invent a dose, timing, meal relation, duration, or medicine name. Leave unknown values empty and place doubts in uncertainties. Suggested times must be 12-hour values such as 8:00 AM and only when an exact clock time is written for that medicine. Use durationDays only for an explicit course length and prescriptionItemNumber only for the printed medicine number; otherwise use 0.';

const MEDICINE_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  properties: {
    medicines: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        properties: {
          medicineName: { type: 'string', description: 'Medicine name exactly as written on the prescription, never replaced by the package brand.' },
          strength: { type: 'string', description: 'Strength exactly as written on the same prescription line, or empty.' },
          form: { type: 'string', description: 'Prescribed form such as tablet, capsule, injection, solution, or ointment, or empty.' },
          quantity: { type: 'string', description: 'Amount per administration from the prescription, such as 1 tablet or 20 units; do not put course duration here.' },
          frequency: { type: 'string', description: 'Frequency exactly supported by the same prescription line.' },
          suggestedTimes: { type: 'array', items: { type: 'string', description: 'Exact same-line clock time normalized as h:mm AM or h:mm PM.' }, maxItems: 6 },
          mealTiming: { type: 'string', enum: ['beforeFood', 'withFood', 'afterFood', 'anytime'] },
          instructions: { type: 'string', description: 'Concise prescription directions including route, explicit duration, and different doses by administration.' },
          durationDays: { type: 'integer', description: 'Explicit X N DAYS course length as a positive day count; use 0 if none is written.' },
          prescriptionItemNumber: { type: 'integer', description: 'Printed medicine item number on the prescription; use 0 if there is no printed number.' },
          identityMatch: { type: 'string', enum: ['exactBrand', 'genericEquivalent', 'uncertain', 'none'], description: 'Relationship between prescription name and matched package.' },
          confidence: { type: 'number', minimum: 0, maximum: 1 },
          uncertainties: { type: 'array', items: { type: 'string' }, maxItems: 6 },
          medicineImageIndex: { type: 'integer', description: 'Zero-based matched strip-photo index, or -1 when no safe match exists.' },
          prescriptionPageIndexes: { type: 'array', items: { type: 'integer' }, maxItems: MAX_PRESCRIPTION_PAGES },
        },
        required: ['medicineName', 'strength', 'form', 'quantity', 'frequency', 'suggestedTimes', 'mealTiming', 'instructions', 'durationDays', 'prescriptionItemNumber', 'identityMatch', 'confidence', 'uncertainties', 'medicineImageIndex', 'prescriptionPageIndexes'],
      },
    },
    unmatchedMedicineImageIndexes: { type: 'array', items: { type: 'integer' }, maxItems: MAX_MEDICINE_IMAGES },
    warnings: { type: 'array', items: { type: 'string' }, maxItems: 8 },
  },
  required: ['medicines', 'unmatchedMedicineImageIndexes', 'warnings'],
} as const;

type ImageInput = { name: string; mimeType: string; data: string };

const json = (body: unknown, status = 200) => Response.json(body, { status, headers: {
  'Cache-Control': 'private, no-store, max-age=0',
  'Content-Type': 'application/json',
  'X-Content-Type-Options': 'nosniff',
} });
const fail = (code: string, error: string, status: number) => json({ code, error }, status);
const isObject = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value);

async function digest(value: string) {
  return new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)));
}

function sameBytes(left: Uint8Array, right: Uint8Array) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let index = 0; index < left.length; index += 1) difference |= left[index] ^ right[index];
  return difference === 0;
}

function imageList(value: unknown, maximum: number): ImageInput[] | null {
  if (!Array.isArray(value) || value.length < 1 || value.length > maximum) return null;
  const images: ImageInput[] = [];
  for (const item of value) {
    if (!isObject(item) || typeof item.name !== 'string' || typeof item.mimeType !== 'string' || typeof item.data !== 'string') return null;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(item.mimeType) || item.name.length > 240
      || !/^[A-Za-z0-9+/]+={0,2}$/.test(item.data)) return null;
    const bytes = Math.floor(item.data.length * 3 / 4) - (item.data.endsWith('==') ? 2 : item.data.endsWith('=') ? 1 : 0);
    if (bytes < 1 || bytes > MAX_IMAGE_BYTES) return null;
    images.push({ name: item.name, mimeType: item.mimeType, data: item.data });
  }
  return images;
}

async function responseText(response: Response, maximum: number) {
  const reader = response.body?.getReader();
  if (!reader) throw new Error('Missing response body');
  const decoder = new TextDecoder('utf-8', { fatal: true });
  let bytes = 0;
  let text = '';
  while (true) {
    const chunk = await reader.read();
    if (chunk.done) break;
    bytes += chunk.value.byteLength;
    if (bytes > maximum) { void reader.cancel(); throw new Error('Response too large'); }
    text += decoder.decode(chunk.value, { stream: true });
  }
  return text + decoder.decode();
}

function structuredOutput(value: unknown): unknown {
  if (isObject(value) && Array.isArray(value.medicines)) return value;
  if (!isObject(value) || value.status !== 'completed' || !Array.isArray(value.steps)) return null;
  const texts: string[] = [];
  for (const step of value.steps) {
    if (!isObject(step) || step.type !== 'model_output' || !Array.isArray(step.content)) continue;
    for (const content of step.content) {
      if (isObject(content) && content.type === 'text' && typeof content.text === 'string') texts.push(content.text);
    }
  }
  if (!texts.length) return null;
  try { return JSON.parse(texts[texts.length - 1]); }
  catch { return null; }
}

async function providerFailureCode(response: Response) {
  try {
    const payload: unknown = JSON.parse(await responseText(response, 32 * 1024));
    if (!isObject(payload) || !isObject(payload.error)) return '';
    if (typeof payload.error.status === 'string') return payload.error.status.slice(0, 80);
    if (typeof payload.error.code === 'number') return String(payload.error.code);
    if (typeof payload.error.code === 'string') return payload.error.code.slice(0, 80);
  } catch {
    void response.body?.cancel().catch(() => {});
  }
  return '';
}

function providerFailure(responseStatus: number) {
  if (responseStatus === 400 || responseStatus === 422) {
    return fail('provider_rejected', 'Gemini rejected the image request. Check the selected image formats and try again.', 502);
  }
  if (responseStatus === 401 || responseStatus === 403) {
    return fail('provider_auth_failed', 'Saanthvana AI cannot authenticate with Gemini. Check the server Gemini key.', 503);
  }
  if (responseStatus === 404) {
    return fail('provider_model_unavailable', 'The configured Gemini model is unavailable.', 503);
  }
  if (responseStatus === 429) {
    return fail('provider_busy', 'Gemini is busy right now. Wait a moment and try again.', 503);
  }
  return fail('provider_unavailable', 'Gemini could not organize these images. Try again.', 502);
}

export async function handleMedicineOrganizer(request: Request) {
  if (request.method !== 'POST') return fail('method_not_allowed', 'Method not allowed.', 405);
  const expectedSecret = Deno.env.get('SAANTHVANA_MEDICINE_ORGANIZER_SECRET')?.trim() ?? '';
  const suppliedSecret = request.headers.get('X-Saanthvana-Organizer-Secret')?.trim() ?? '';
  if (expectedSecret.length < 32 || suppliedSecret.length < 32
    || !sameBytes(await digest(suppliedSecret), await digest(expectedSecret))) return fail('unauthorized', 'Unauthorized.', 401);
  const geminiKey = Deno.env.get('GEMINI_API_KEY')?.trim() ?? '';
  if (!geminiKey) return fail('not_configured', 'Saanthvana AI is waiting for its Gemini API key.', 503);
  if (request.headers.get('content-type')?.split(';')[0].trim().toLowerCase() !== 'application/json') {
    return fail('unsupported_body', 'Send medicine images as JSON.', 415);
  }
  const length = request.headers.get('content-length');
  if (length && (!/^\d+$/.test(length) || Number(length) > MAX_BODY_BYTES)) return fail('images_too_large', 'The selected images are too large together.', 413);

  try {
    const payload: unknown = await request.json();
    if (!isObject(payload) || Object.keys(payload).some((key) => !['prescriptionPages', 'medicineImages'].includes(key))) {
      return fail('invalid_images', 'Add prescription pages and medicine-strip photos.', 400);
    }
    const prescriptionPages = imageList(payload.prescriptionPages, MAX_PRESCRIPTION_PAGES);
    const medicineImages = imageList(payload.medicineImages, MAX_MEDICINE_IMAGES);
    if (!prescriptionPages || !medicineImages) return fail('invalid_images', 'Use up to 5 valid prescription pages and 10 valid medicine-strip photos.', 400);
    const totalBytes = [...prescriptionPages, ...medicineImages].reduce((sum, item) => sum + Math.floor(item.data.length * 3 / 4), 0);
    if (totalBytes > MAX_TOTAL_BYTES) return fail('images_too_large', 'The selected images are too large together.', 413);

    const input: Array<Record<string, string>> = [{ type: 'text', text: ORGANIZE_PROMPT }];
    prescriptionPages.forEach((image, index) => {
      input.push({ type: 'text', text: `PRESCRIPTION PAGE ${index}` });
      input.push({ type: 'image', data: image.data, mime_type: image.mimeType });
    });
    medicineImages.forEach((image, index) => {
      input.push({ type: 'text', text: `MEDICINE STRIP IMAGE ${index}` });
      input.push({ type: 'image', data: image.data, mime_type: image.mimeType });
    });

    const signal = AbortSignal.timeout(125_000);
    const providerRequest = JSON.stringify({
      model: Deno.env.get('GEMINI_MODEL')?.trim() || MODEL_NAME,
      system_instruction: SYSTEM_INSTRUCTION,
      input,
      store: false,
      generation_config: { max_output_tokens: 12_000, thinking_level: 'low' },
      response_format: { type: 'text', mime_type: 'application/json', schema: MEDICINE_SCHEMA },
    });
    let response: Response | undefined;
    for (let attempt = 1; attempt <= 2; attempt += 1) {
      response = await fetch('https://generativelanguage.googleapis.com/v1beta/interactions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'x-goog-api-key': geminiKey },
        signal,
        redirect: 'error',
        body: providerRequest,
      });
      if (response.ok) break;
      const status = response.status;
      const providerCode = await providerFailureCode(response);
      console.error('Gemini medicine organizer request failed.', { status, providerCode, attempt });
      if (status >= 500 && attempt === 1 && !signal.aborted) {
        await new Promise((resolve) => setTimeout(resolve, 350));
        continue;
      }
      return providerFailure(status);
    }
    if (!response?.ok) return fail('provider_unavailable', 'Gemini could not organize these images. Try again.', 502);
    const raw = JSON.parse(await responseText(response, MAX_PROVIDER_RESPONSE_BYTES));
    const organization = structuredOutput(raw);
    if (!isObject(organization) || !Array.isArray(organization.medicines)) {
      return fail('invalid_response', 'Gemini returned an incomplete medicine list. Try clearer photos.', 502);
    }
    return json(organization);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'TimeoutError') return fail('provider_timeout', 'Gemini took too long. Try fewer photos.', 504);
    return fail('organizer_unavailable', 'The medicine organizer is temporarily unavailable. Try again.', 502);
  }
}

if (typeof Deno !== 'undefined') Deno.serve(handleMedicineOrganizer);
