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

## Android: on-device speech for goals-of-care conversations

Checked **2026-11-01**. Nothing has been run on a phone yet: size, hash and licence are checked, speed and accuracy are not.

The app's existing offline pack, Qwen3-ASR-0.6B, does not cover the five languages. Its model card lists 30 languages, including English and Hindi, but not Telugu, Urdu or Kannada. It stays in the doctor conversation flow. The goals-of-care flow uses Whisper small on the same runtime (sherpa-onnx 1.13.8, already in the app).

| | |
|---|---|
| Model | OpenAI Whisper small, multilingual, int8 ONNX export for sherpa-onnx |
| Source, pinned | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small at revision `8f3c18b358db4d1f2fc1eae49d75cd20989e4309` |
| `small-encoder.int8.onnx` | 112,442,483 bytes, sha256 `4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9` |
| `small-decoder.int8.onnx` | 262,226,114 bytes, sha256 `acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee` |
| `small-tokens.txt` | 816,730 bytes, sha256 `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` |
| Total download | 375,485,327 bytes (about 375 MB), on the phone, on demand. Not in the APK or the repo. |
| Languages | `en`, `hi`, `te`, `ur`, `kn` are all in Whisper's language table (`whisper/tokenizer.py`). Empty means detect. |
| Licence | Whisper code and weights: MIT (https://github.com/openai/whisper, README and LICENSE). The sherpa-onnx export repo has no licence tag of its own. The `openai/whisper-small` Hugging Face card is tagged apache-2.0. Both are permissive, and the notice asset carries the MIT text. |
| Where it is listed | `OfflineWhisperModel` in `CareGocTranscribe.kt`; `assets/offline-speech-NOTICE.txt` |

**How a long talk is handled**

- One recording can run up to 90 minutes: about 43 MB of audio at 64 kbps, and about 350 MB of temporary space while it is read. A longer talk, or one interrupted by leaving the app, becomes several parts.
- `DoctorAudioController` is not used here, because it stops at 299 s for the cloud route.
- Audio is read in pieces of 15 to 28 seconds, cut at pauses. sherpa-onnx truncates a piece at about 29.5 seconds.
- Each finished piece is saved straight away, so a stop or a crash resumes from the last piece.
- Typing or pasting always works without the pack.

**Not measured:** reading speed (a phone may take longer than the talk), peak memory (the 1.2 GiB free-memory check is an estimate from the file sizes), and accuracy in Telugu, Urdu and Kannada. The screen says every line must be checked against the audio.

The web uses a smaller model (`onnx-community/whisper-base`, about 95 MB); see the web section of this file and `prototype/app/goc-transcribe-model.ts`.

URLs: https://huggingface.co/Qwen/Qwen3-ASR-0.6B, https://huggingface.co/api/models/csukuangfj/sherpa-onnx-whisper-small?blobs=true, https://github.com/openai/whisper/blob/main/whisper/tokenizer.py, https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/kotlin-api/OfflineRecognizer.kt

## On-device speech to text (goals-of-care conversation)

This is the one exception to "we do not bundle on-device models", and it keeps to the reason: **nothing is bundled.** The care plan's conversation panel (`prototype/app/goc-transcribe.tsx`) fetches the engine and the model only when the doctor taps **Transcribe on this device**. Normal pages load none of it, and the audio is never sent anywhere. Typing or pasting always works with no download.

Checked **2026-11-01** in the app's browser pane (Chromium), on a Ryzen 5 6600H laptop with 15 GB of memory, one CPU core in use.

### Options compared

| Option | Download | Licence | Languages | Result | Source |
|---|---|---|---|---|---|
| `onnx-community/whisper-tiny` q8 | 41 MB model | OpenAI Whisper: Apache-2.0 on its Hugging Face card, MIT in OpenAI's repo | 99, including en, hi, te, ur, kn | Not tested: smaller than base, which is already weak on Indian languages | https://huggingface.co/onnx-community/whisper-tiny |
| **`onnx-community/whisper-base` q8 (default)** | **94.5 MB measured**, engine included | OpenAI Whisper: Apache-2.0 on its Hugging Face card, MIT in OpenAI's repo | same | English and Urdu usable as a draft. About real time | https://huggingface.co/onnx-community/whisper-base |
| `onnx-community/whisper-base`, full encoder | about 155 MB | Apache-2.0 | same | Tested: no better than q8 (Urdu 0.277, Telugu 0.963, Kannada 1.121, Hindi 1.029), so not worth 59 MB more | same |
| **`onnx-community/whisper-small` q8 (offered, default for Hindi)** | **266.7 MB measured**, engine included | OpenAI Whisper: Apache-2.0 on its Hugging Face card, MIT in OpenAI's repo | same | Hindi becomes usable. About 3 to 4 times slower than real time, worse when it loops | https://huggingface.co/onnx-community/whisper-small |
| `onnx-community/whisper-large-v3-turbo` q8 | about 1.1 GB | MIT per OpenAI's release; check before use | same | Rejected on size. Not tested | https://huggingface.co/onnx-community/whisper-large-v3-turbo |
| `vasista22/whisper-telugu-base`, `whisper-kannada-base` (IIT Madras fine-tunes) | 290 MB PyTorch, no ONNX | Apache-2.0 | One language each | Not usable as they are: no ONNX export, so not tested. **Best next step for Telugu and Kannada**: convert to quantised ONNX, then host and pin them | https://huggingface.co/vasista22/whisper-telugu-base, https://huggingface.co/vasista22/whisper-kannada-base |
| Browser `SpeechRecognition` | none | n/a | Depends on the browser | Rejected: it may send audio to a remote service, as `doctor-conversation.tsx` already notes | n/a |

### Quality, measured

Character error rate (CER): 0 is perfect, and above 1 means more errors than characters. Measured against the reference text on Google FLEURS validation clips, with the language forced (https://huggingface.co/datasets/google/fleurs). There are only 3 to 4 clips per language, of about 10 to 25 seconds each, and it is clean read speech, so treat these as a rough guide. A ward conversation with several people will be worse.

| Language | base q8 CER | base output | small q8 CER | small output | Speed, base / small (audio time multiple) |
|---|---|---|---|---|---|
| English | 0.081 | Good | not run | | 0.94 / not run |
| Urdu | 0.277 | Urdu script, readable | 0.220 | Urdu script, readable | 0.87 / 2.67 |
| Hindi | 1.091 | **Latin letters**, phonetically close, so the score overstates the error | **0.238** | **Devanagari, readable** | 0.73 / 2.89 |
| Telugu | 0.917 | Latin letters, mostly wrong. **Not usable** | 0.937 | Devanagari letters. **Not usable** | 1.11 / 3.83 |
| Kannada | 1.643 | Other scripts, repeats. **Not usable** | 1.755 | Kannada letters in a repeating loop. **Not usable** | 1.65 / 5.55 |

Blocking every token outside the expected script was also tried on base. It did not help (Hindi 1.039, Telugu 1.0, Kannada 1.04), so it is not used.

So the screen says so. A machine transcript is always labelled a draft, Telugu, Urdu and Kannada are flagged for checking against the audio, and choosing Telugu or Kannada warns that typing is more reliable. The model list defaults to small for Hindi and base otherwise, and the doctor can change it. The phrase matcher also reads romanised Hindi, Telugu and Kannada ("nahi chahiye", "vaddu", "beda") because base writes those.

### How it runs

- **Engine:** Transformers.js, `@huggingface/transformers` **4.3.1** (published 2026-10-06, Apache-2.0), file `dist/transformers.min.js`, self-contained at 586 KB. 4.3.2 is excluded: it was only 5 days old on the check date.
- **Runtime:** ONNX Runtime Web `1.31.0-dev.20260914-8d85527a0`, the version that release pins. It uses the plain `ort-wasm-simd-threaded.wasm` (14.3 MB, CPU only) rather than the 26.9 MB WebGPU build, so no WebGPU is needed.
- **No npm dependency, on purpose.** `npm install @huggingface/transformers` also pulls `onnxruntime-node` (301 MB unpacked), `onnxruntime-web` (145 MB) and `sharp`. That is too much for a disk with 1.2 GB free and for every Vercel install. Instead a 5 KB static worker, `public/vendor/goc-whisper/worker.mjs`, loads exact URLs from jsDelivr, and bundlers never see the library.
- **Integrity:** the worker checks the SHA-384 of the library, wasm and loader before running them (`GOC_ENGINE` in `goc-transcribe-model.ts`), and refuses a mismatch. The weights come from Hugging Face at a pinned commit and are cached by the browser, so the download happens once.
- **Headers:** the care plan pages have no script or connect CSP, and the WASM ran under the headers as they are. Sahara does not send COOP/COEP, so WebAssembly threads are off and it runs on one core. If a CSP is added later, it needs `connect-src cdn.jsdelivr.net huggingface.co *.hf.co`, `script-src 'wasm-unsafe-eval' blob:` and `worker-src 'self'`.
- **Mixed languages:** "auto" asks the model which of the five languages each piece is in, because this library otherwise writes English when no language is given. Each piece is detected on its own. Urdu is usually detected as Hindi.
- **Speakers:** no diarisation. The doctor marks each line as doctor side or patient side, or types "Doctor:" and "Family:". Doctor-side lines tick topics but never become suggested choices.

### Long talks

Audio is cut at the quietest moment before each 30 seconds and handled one piece at a time. Silent pieces are skipped, because Whisper invents words for silence. Finished pieces stay if the run stops, and the screen shows pieces done and time left.

Measured with base on "auto", on this laptop, on a one-hour WAV built from the FLEURS clips:

- Decoding the 115 MB file took 1.4 seconds and holds about 230 MB while it runs. No failure.
- 56 pieces, 27 minutes of audio, took 29 minutes at a steady 31 seconds per 29 second piece, with browser memory flat at about 500 MB. **The run was stopped there**, so a full hour was not completed. At that speed an hour takes about 65 minutes.
- Small was not run for long: at its measured speed an hour would take 3 to 4 hours or more, so the screen says so and defaults to it only for Hindi.
- Not measured on a phone or an older computer. Expect them to be much slower, and the screen says typing or pasting may be quicker for a full hour.
- One short clip failed once with the out-of-memory message while two other models were loaded in other tabs, then worked on retry. The screen's message and the kept lines behaved as intended.

### Choices from the transcript

`prototype/app/goc-extract.ts` is a deterministic phrase matcher with no model and no keys, tested in `prototype/tests/goc-extract.test.mjs`, including the sentences the Android matcher pins. A bare mention ticks a topic and suggests nothing, and when unsure it gives no mark. Every suggestion carries the exact quote. The doctor confirms each item, and the record stays a draft until the normal signing step.

`POST /api/goc-extract` is an optional second reading on the existing free chain, after separate consent naming the services. It returns only known options with word-for-word quotes, and it is off with no keys, which is the state today.
