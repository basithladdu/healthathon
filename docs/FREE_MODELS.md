# Free models in Sahara

Sahara uses free, server-side AI only. The phone and the web bundle never carry a model or a key, and the
existing on-phone search without AI (`CareDocumentQuestion.kt`, web `care-document-search`) keeps working offline.

Every free provider below was checked on **2026-10-29**. Free tiers change often, so check the linked pages again before a
demo or a launch.

## How it works

`prototype/app/ai-providers.ts` builds a chain from whichever keys are set in Vercel
(project `continuity-loop-healthathon` → Settings → Environment Variables). The first configured provider is tried
first. On a timeout (25 s each, 55 s in total), HTTP 429, 5xx, a bad key, or an answer that fails Sahara's checks, the
next provider is tried.

Order: custom OpenAI-compatible (optional) → **Gemini** → **Groq** → **OpenRouter (free models)** → **Hugging Face** → **Cloudflare Workers AI**.

- Every reply keeps the Android contract: `provider: "openai-compatible"`, and `model` holds the bare model id.
- Every reply also adds `answeredBy`, the name of the service that answered (for example "Groq"). Show it small next to the existing source label.
- `GET /api/sahara-assist` returns `label`, which names every configured service, for example "Google Gemini / Groq". The consent line uses it, because any of those services may receive the words.
- The existing checks still run on every provider's answer:
  - draft picks whole line numbers only
  - ask cites only lines that were sent
  - every number in an answer must appear in the cited lines
  - a translation must keep the same numbers
- When a check fails, the next provider is tried.
- `SAHARA_AI_CHAIN=groq,gemini` reorders the chain or limits it to the services listed.

Routes on the chain:

- `/api/sahara-assist`: draft, ask and translate go through the chat chain. Transcribe tries Gemini audio, then Groq Whisper.
- `/api/translate` (new): Bhashini first, then the chat chain. See "Translation" below.
- `/api/care-assist` (web) is unchanged. It still uses Sarvam behind its data-terms gate (`CARE_AI_DATA_TERMS_APPROVED`). The web client checks for `provider: 'sarvam'`, and its consent text names Sarvam, so moving it to the chain needs a separate data-terms decision.
- `/api/medicine-organizer` is unchanged. It forwards requests to a Supabase function and does not call a model directly.

## Providers

| # | Provider | Vercel env var(s) | Default model (override) | Endpoint | Free tier (from the docs, 2026-10-29) | Sign up |
|---|---|---|---|---|---|---|
| 1 | Google Gemini | `GEMINI_API_KEY` | `gemini-3.5-flash-lite` (`GEMINI_MODEL`, or `SAHARA_AI_MODEL`) | `https://generativelanguage.googleapis.com/v1beta/openai/chat/completions`; audio: `…/v1beta/models/{model}:generateContent` | Every Flash and Flash-Lite model is "Free of charge". Per-model RPM and RPD are shown only in AI Studio and are not on the public page. **Free-tier content is used to improve Google's products.** | https://aistudio.google.com/apikey |
| 2 | Groq | `GROQ_API_KEY` | `llama-3.3-70b-versatile` (`GROQ_MODEL`); speech: `whisper-large-v3-turbo` (`GROQ_TRANSCRIBE_MODEL`) | `https://api.groq.com/openai/v1` (`/chat/completions`, `/audio/transcriptions`) | Published plan limits: gpt-oss-120b, gpt-oss-20b and qwen3.8-27b get 30 RPM, 1K RPD, 8K TPM and 200K TPD. Whisper gets 20 RPM, 2K RPD and 7.2K audio-seconds per hour. llama-3.3-70b is not in the table, so check your own console. JSON object mode works on all models. | https://console.groq.com/keys |
| 3 | OpenRouter (`:free` only) | `OPENROUTER_API_KEY` | `google/gemma-4-31b-it:free`, then `google/gemma-4-26b-a4b-it:free` through the `models` fallback (`OPENROUTER_MODEL` must end in `:free`, or it is ignored) | `https://openrouter.ai/api/v1/chat/completions` | Free models allow 20 RPM. They allow 50 requests a day with less than 10 credits ever purchased, and 1000 a day with at least 10. A fallback to a paid model would be billed, so only `:free` ids are ever sent. Check the free-model logging setting at https://openrouter.ai/settings/privacy. | https://openrouter.ai/keys |
| 4 | Hugging Face Inference Providers | `HF_TOKEN` | `google/gemma-4-31B-it` (`HF_MODEL`) | `https://router.huggingface.co/v1/chat/completions` | **Free accounts get no monthly credits.** PRO and Team accounts get $2 a month, and anything above that is pay-as-you-go. It is kept as an optional backstop. | https://huggingface.co/settings/tokens |
| 5 | Cloudflare Workers AI | `CLOUDFLARE_ACCOUNT_ID` (32 hex) + `CLOUDFLARE_API_TOKEN` | `@cf/google/gemma-4-26b-a4b-it` (`CLOUDFLARE_MODEL`) | `https://api.cloudflare.com/client/v4/accounts/{id}/ai/v1/chat/completions` | 10,000 Neurons a day free, reset at 00:00 UTC, then $0.011 per 1,000 Neurons. This model costs 9,091 Neurons per million input tokens and 27,273 per million output tokens, roughly 100+ Sahara asks a day. | https://dash.cloudflare.com/ → AI → Workers AI |
| — | Bhashini (translation only) | `BHASHINI_USER_ID` + `BHASHINI_API_KEY` (optional `BHASHINI_PIPELINE_ID`) | MeitY pipeline `64392f96daac500b55c543cd`; the service id comes from the config call | config: `https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline`; compute: the `callbackUrl` the config call returns (`https://dhruva-api.bhashini.gov.in/services/inference/pipeline`) | Free for integrators. The docs say production systems, or integrators charging end users, must contact Bhashini for the paid APIs. | https://bhashini.gov.in/ulca → My Profile (userID, ulcaApiKey) |
| — | Custom (optional) | `SAHARA_AI_BASE_URL` + `SAHARA_AI_API_KEY` + `SAHARA_AI_MODEL` (+ `SAHARA_AI_TRANSCRIBE_MODEL`, `SAHARA_AI_PROVIDER_NAME`) | — | any OpenAI-compatible `https://` base | — | — |

For the cheapest working setup, set `GEMINI_API_KEY` and `GROQ_API_KEY`. Add `OPENROUTER_API_KEY` and the two Cloudflare
variables for more headroom. For translation, add `BHASHINI_USER_ID` and `BHASHINI_API_KEY`.

**Health data and free tiers.** Gemini's free tier uses submitted content to improve Google's products. Sahara already:

- sends only the lines a search matched
- asks for consent every time
- names every service in the consent line

Before real patients use it, move Gemini to a paid key (paid tier: content is not used), or drop Gemini from
`SAHARA_AI_CHAIN`.

## Translation: `POST /api/translate`

The route translates free text a person typed, such as "own words", between English, Hindi, Telugu, Kannada and Urdu.

**Request:** `{ text (≤2000 chars), sourceLanguage, targetLanguage, consent: true }`. Language codes are `en`, `hi`, `te`, `kn`, `ur`, or the same codes with `-IN`.

**Order:** Bhashini, then the chat chain. The chain is used when Bhashini is not set up, fails, or changes any number. Devanagari, Telugu, Kannada and Arabic-Indic digits count as the same numbers as 0-9.

**Output:**
- Always `label: "Machine translation"`, plus `answeredBy`, `requiresReview: true`, the unchanged `original`, and a notice.
- If every service changes a number, the route returns 502 `translation_needs_attention`.

**Access:**
- A Sahara Bearer token works. It is checked against the Android Supabase project, then the web one.
- A same-origin browser request also works.
- Limit: 30 translations an hour per user or address.
- The route does not follow a Bhashini `callbackUrl` outside `*.bhashini.gov.in` or `*.ulcacontrib.org`.

The route is **not wired into the ECTPR choices**. Those are enums with fixed, reviewed translations in `care-i18n.ts`.

## Guard against treatment advice

SVK's Saanthvana spec lists "Won't: AI treatment advice". `prototype/app/ai-treatment-guard.ts` enforces it in three places:

1. **Before anything is reserved or sent.** `/ask` refuses a question that asks the AI to decide or recommend care.
   - Examples: CPR, DNR, ventilation, ICU, dialysis, feeding, medicine or dose changes, emergency-care-plan or living-will choices.
   - Languages: English, Hindi (Devanagari and romanised), Urdu, Telugu and Kannada.
   - A request tagged `purpose: ectpr | emergency-care-plan | living-will | advance-directive | treatment-plan` is also refused.
   - The refusal is 422 `treatment_advice_refused`, with a message pointing to the palliative care team.
   - Questions about what is written still pass, for example "When should I take the tablet?" or "What does my ECTPR say about CPR?".
2. **In every prompt.** The rule never to recommend or suggest care choices is in every generative prompt.
3. **On every answer.** An answer is refused with the same 422 when it contains advising language that is not quoted from the cited document lines.
   - Examples: "you should", "I recommend", "it is best to", "चाहिए".
   - The chain then stops (`FinalOutputError`), so no other model is asked to try again.

Draft only picks line numbers, and translation and transcription only render what was said, so none of them can write
advice. Tests: `prototype/tests/ai-treatment-guard.test.mjs`.

## Why we did not bundle on-device models

- **APK and web size.** Even a small on-device LLM is hundreds of MB to several GB (see the next section), against an app of tens of MB. Families often use low-end Android phones with little storage and expensive data.
- **The spec.** On-device generation would only add the one capability the spec forbids (AI treatment advice). Everything Sahara is allowed to do with AI already works on a server: finding, citing, translating and transcribing.
- **Safety checks need a strong model.** The strict checks (exact citations, the same numbers, no advice) reject weak answers. A 1B-class phone model would fail them most of the time, and the person would get nothing.
- **Offline is covered.** On-phone keyword, synonym and OCR-tolerant search, and ML Kit offline translation (already in `CareOfflineTranslation.kt`), keep working with no network.

## Lightweight on-device options evaluated (future work)

| Option | What it would add | Size | Status |
|---|---|---|---|
| MediaPipe / LiteRT LLM Inference + **Gemma 3n E2B** (int4 `.task`) | Offline generative answers | ≈3.1 GB (`gemma-3n-e2b-it-int4.task`, 3,136,226,711 bytes per a mirror of Google's file; the web `.litertlm` is ≈2.8 GB) | Rejected: size, and it could only generate advice |
| LiteRT + Gemma 3 1B IT (int4) | Small offline generation | ≈0.5 GB (litert-community/Gemma3-1B-IT) | Rejected: too weak for the cited-answer checks |
| **EmbeddingGemma 308M** (QAT-quantized) | Better offline semantic search over a person's own documents (retrieval only, no generation) | runs in under 200 MB RAM (Google) | **Best candidate.** Retrieval cannot give advice. Ship it as an optional download on Wi-Fi only, never in the APK. |
| **ML Kit** on-device translation | Offline translation | ≈30 MB per language, downloaded on demand | Already used |
| ML Kit text recognition (bundled or Play-services) | Offline OCR | a few MB per script | Already used where present |

## URLs used (checked 2026-10-29)

- Gemini: https://ai.google.dev/gemini-api/docs/models, https://ai.google.dev/gemini-api/docs/pricing, https://ai.google.dev/gemini-api/docs/rate-limits, https://ai.google.dev/gemini-api/docs/openai, https://aistudio.google.com/rate-limit
- Groq: https://console.groq.com/docs/models, https://console.groq.com/docs/rate-limits, https://console.groq.com/docs/structured-outputs
- OpenRouter: https://openrouter.ai/docs/api/reference/limits, https://openrouter.ai/docs/guides/routing/model-fallbacks, https://openrouter.ai/api/v1/models (the `:free` list)
- Hugging Face: https://huggingface.co/docs/inference-providers/en/pricing, https://router.huggingface.co/v1/models
- Cloudflare: https://developers.cloudflare.com/workers-ai/platform/pricing/, https://developers.cloudflare.com/workers-ai/configuration/open-ai-compatibility/
- Bhashini: https://bhashini.gitbook.io/bhashini-apis/pipeline-config-call (and `/request-payload.md`, `/response-payload.md`), https://bhashini.gitbook.io/bhashini-apis/pipeline-compute-call (and `/request-payload.md`, `/response-payload.md`), https://bhashini.gitbook.io/bhashini-apis/pipeline-search-call (MeitY pipeline id)
- On-device: https://developers.googleblog.com/introducing-embeddinggemma/, https://huggingface.co/litert-community/Gemma3-1B-IT, https://huggingface.co/google/gemma-3n-E2B-it-litert-lm, https://developers.google.com/ml-kit/language/translation/android
