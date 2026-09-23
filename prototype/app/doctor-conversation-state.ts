import type { DraftFieldKey } from './summary-state';
import type { CareNoteKind } from './care-note-signing-state';

export type DoctorConversationEntry = {
  id: string;
  patientId: string;
  physician: string;
  note: string;
  recordedAt: string;
  audio?: File;
  recordingAgreed: boolean;
};

export type AssistedNote = { fields: Record<DraftFieldKey, string>; excerpts: Record<DraftFieldKey, string>; suggestedKind?: CareNoteKind };

export const CARE_NOTE_LABELS: Record<DraftFieldKey, string> = {
  priorities: 'What matters', participants: 'Who was there', topics: 'What we discussed',
  openQuestions: 'Still to discuss', followUp: 'Next steps',
};

type SourcePassage = { start: number; end: number };

const SECTION_HEADINGS: Array<[DraftFieldKey, RegExp]> = [
  ['priorities', /^(?:what matters|priorities|patient priorit(?:y|ies)|मरीज़ की प्राथमिकताएँ|प्राथमिकताएँ)\s*[:：]\s*/iu],
  ['participants', /^(?:who was there|participants|attendees|present today|उपस्थित लोग|कौन मौजूद था)\s*[:：]\s*/iu],
  ['topics', /^(?:what we discussed|topics|discussion|बातचीत|चर्चा)\s*[:：]\s*/iu],
  ['openQuestions', /^(?:still to discuss|open questions|unresolved questions|बाकी सवाल|खुले सवाल)\s*[:：]\s*/iu],
  ['followUp', /^(?:next steps|follow[ -]?up|अगले कदम|आगे की योजना)\s*[:：]\s*/iu],
];

function sentencePassages(source: string, start: number, end: number): SourcePassage[] {
  const passages: SourcePassage[] = [];
  let sentenceStart = start;
  const add = (sentenceEnd: number) => {
    while (sentenceStart < sentenceEnd && /\s/u.test(source[sentenceStart])) sentenceStart += 1;
    while (sentenceEnd > sentenceStart && /\s/u.test(source[sentenceEnd - 1])) sentenceEnd -= 1;
    if (sentenceEnd > sentenceStart) passages.push({ start: sentenceStart, end: sentenceEnd });
  };
  for (let index = start; index < end; index += 1) {
    if (!/[.!?।]/u.test(source[index])) continue;
    let boundary = index + 1;
    while (boundary < end && /[.!?।"'”’)]/u.test(source[boundary])) boundary += 1;
    if (boundary < end && !/\s/u.test(source[boundary])) continue;
    const before = source.slice(sentenceStart, index + 1);
    if (source[index] === '.' && /(?:\b(?:Dr|Mr|Mrs|Ms|Prof|Sr|Jr|vs|St)|\b[A-Z]|\b[ap]\.m)\.$/iu.test(before)) continue;
    add(boundary);
    sentenceStart = boundary;
    index = boundary - 1;
  }
  add(end);
  return passages;
}

function passageField(text: string): DraftFieldKey {
  // Only explicit wording selects a section; uncertainty and negation stay intact.
  if (/\b(?:still (?:need|want|have) to discuss|not (?:yet )?discussed|unanswered|unresolved|undecided|not sure|remain(?:s)? unclear|defer(?:red)? (?:the |a )?(?:decision|discussion)|need to (?:clarify|ask))\b/iu.test(text)) return 'openQuestions';
  if (/\b(?:follow[ -]?up|next (?:visit|appointment|discussion)|(?:agreed|plan(?:ned)?|propos(?:ed|e)) to (?:call|contact|meet|return|review|continue|discuss|arrange|bring|send)|will (?:call|contact|return|review|arrange|bring|send))\b/iu.test(text)) return 'followUp';
  if (/\b(?:met|spoke) with\b.+\btoday\b/iu.test(text) || /\b(?:participants|attendees|present today)\s*:/iu.test(text) || /\b(?:is|are|was|were) present (?:today|for this (?:conversation|discussion))\b/iu.test(text)) return 'participants';
  if (/^(?:patient|मरीज़|रोगी)\s*:/iu.test(text) && /\b(?:I want|I prefer|I hope|I wish|I would like|important to me|what matters to me|my priority)\b|मैं चाहता|मैं चाहती|मेरे लिए ज़रूरी/iu.test(text)) return 'priorities';
  if (/\b(?:the )?patient (?:said|says|stated|states|wants|prefers)\b/iu.test(text) && /\b(?:want|wants|prefer|prefers|important|priority|priorities|matters|wish|wishes)\b/iu.test(text)) return 'priorities';
  return 'topics';
}

/** Organise supplied words only. The existing review/signing step remains mandatory. */
export function organiseConversation(source: string): AssistedNote {
  const fields: AssistedNote['fields'] = { priorities: '', participants: '', topics: '', openQuestions: '', followUp: '' };
  const excerpts = { ...fields };
  const passages: Record<DraftFieldKey, SourcePassage[]> = { priorities: [], participants: [], topics: [], openQuestions: [], followUp: [] };
  let section: DraftFieldKey | null = null;

  for (const line of source.matchAll(/[^\r\n]+/gu)) {
    const lineText = line[0];
    const leading = lineText.length - lineText.trimStart().length;
    const text = lineText.trim();
    if (!text) continue;
    const start = line.index + leading;
    const end = start + text.length;
    const heading = SECTION_HEADINGS.find(([, pattern]) => pattern.test(text));
    if (heading) {
      section = heading[0];
      const contentStart = start + heading[1].exec(text)![0].length;
      if (contentStart < end) passages[section].push({ start: contentStart, end });
      continue;
    }
    if (section) {
      passages[section].push({ start, end });
      continue;
    }
    for (const passage of sentencePassages(source, start, end)) {
      passages[passageField(source.slice(passage.start, passage.end))].push(passage);
    }
  }

  for (const key of Object.keys(CARE_NOTE_LABELS) as DraftFieldKey[]) {
    const selected = passages[key];
    if (!selected.length) continue;
    fields[key] = selected.map(({ start, end }) => source.slice(start, end)).join('\n');
    // One continuous source passage, including intervening context, is auditable.
    excerpts[key] = source.slice(selected[0].start, selected[selected.length - 1].end);
  }
  return { fields, excerpts };
}
