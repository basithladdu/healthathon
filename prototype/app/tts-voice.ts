// Pure language handling for read-aloud: which voice to use, and how to split text so a sentence still in English
// (a string not translated yet) is not read by a Telugu voice. The browser's speech engine does the speaking.
import { CARE_LANGS, type CareLang } from './care-i18n.ts';

const SCRIPT: Partial<Record<CareLang, RegExp>> = {
  hi: /[ऀ-ॿ]/,
  te: /[ఀ-౿]/,
  kn: /[ಀ-೿]/,
  ur: /[؀-ۿݐ-ݿ]/,
};

export function speechLocale(lang: CareLang): string {
  return lang === 'en' ? 'en-IN' : CARE_LANGS.find((l) => l.code === lang)?.locale ?? 'en-IN';
}

export type SpeechChunk = { text: string; lang: CareLang };

/** Sentences in order, each tagged with the language to read it in. Neighbours in one language are joined. */
export function speechChunks(text: string, lang: CareLang): SpeechChunk[] {
  const clean = text.replace(/[#*•_`~]/g, '').replace(/\s*\n+\s*/g, '. ').replace(/\s+/g, ' ').replace(/([.।۔:?!])\s*\.(\s|$)/g, '$1$2').trim();
  if (!clean) return [];
  const script = SCRIPT[lang];
  const chunks: SpeechChunk[] = [];
  for (const sentence of clean.split(/(?<=[.!?।۔؟])\s+/)) {
    if (!/[\p{L}\p{N}]/u.test(sentence)) continue;
    const sentenceLang: CareLang = script && !script.test(sentence) && /\p{L}/u.test(sentence) ? 'en' : lang;
    const last = chunks.at(-1);
    // Stay short: some browsers cut off an utterance after about 15 seconds.
    if (last && last.lang === sentenceLang && last.text.length + sentence.length < 200) last.text += ` ${sentence}`;
    else chunks.push({ text: sentence, lang: sentenceLang });
  }
  return chunks;
}

type VoiceLike = { lang: string; name: string };

/** Best voice for the language, or null when this device has none. Only English falls back to whatever voice exists. */
export function pickVoice<V extends VoiceLike>(voices: readonly V[], lang: CareLang): V | null {
  const matching = voices.filter((v) => v.lang.toLowerCase().replace('_', '-').split('-')[0] === lang);
  const clear = (v: V) => /natural|online|google/i.test(v.name);
  return matching.find(clear) ?? matching[0] ?? (lang === 'en' ? voices[0] ?? null : null);
}
