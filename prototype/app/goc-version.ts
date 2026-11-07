// "Start a new version" (twin of GocNewVersion in CareGocExtract.kt). A signed goals-of-care note is locked for good. To talk again, the
// conversation makes a NEW version: the signed one stays locked in history, and the new draft carries over the earlier note as an editable
// starting point and the earlier choices (which live in the plan itself), to be confirmed again in the new talk. No lines, no confirmed topics.
import type { GocTranscript } from './ectpr-model.ts';

type SignOffLike = { status?: string } | null | undefined;
/** The saved talk with the parts this module adds beside the typed model: the sign-off summary, the version counter and the locked history. */
export type GocTalk = GocTranscript & { signOff?: SignOffLike; version?: number; history?: GocLockedVersion[]; guide?: string; carriedNote?: string };
export type GocLockedVersion = Pick<GocTalk, 'lines' | 'language' | 'source' | 'consentAt' | 'reviewedBy' | 'reviewedAt' | 'accepted' | 'topics' | 'note' | 'signOff' | 'guide'> & { version: number };

export const gocTalkVersion = (talk: GocTalk | undefined | null): number => talk?.version ?? 1;

/** Whether this talk has been signed and locked. */
export const gocTalkLocked = (talk: GocTalk | undefined | null): boolean => talk?.signOff?.status === 'LOCKED';

/** The new version's sign-off id suffix: the first version keeps the id it always had, so signatures already stored still find their note. */
export const gocNoteSuffix = (version: number): string => (version > 1 ? `:t${version}` : '');

/** The locked talk goes into history untouched; the new talk starts from its note. Refused (null) while the talk is not locked. */
export function gocStartNewVersion(talk: GocTalk | undefined | null): GocTalk | null {
  if (!talk || !gocTalkLocked(talk)) return null;
  const version = gocTalkVersion(talk);
  const locked: GocLockedVersion = {
    version, lines: talk.lines, language: talk.language, source: talk.source, consentAt: talk.consentAt, reviewedBy: talk.reviewedBy,
    reviewedAt: talk.reviewedAt, accepted: talk.accepted, topics: talk.topics, note: talk.note, signOff: talk.signOff, guide: talk.guide,
  };
  return {
    lines: [], language: talk.language, source: 'TYPED', machineDraft: false, consentAt: '', reviewedBy: '', reviewedAt: '', audioKept: false,
    // The earlier note is kept beside the new talk, not as its note, so a reload never shows the old signed note as this talk's own.
    accepted: [], topics: {}, note: '', carriedNote: talk.note, version: version + 1, history: [...(talk.history ?? []), locked],
  } as GocTalk;
}
