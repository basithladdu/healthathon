// Pure indicators for the pilot audit (clinical spec §8, Appendix D.3): reach, retrieval, concordance, values section,
// outcomes and safety. Screens stay thin: everything that can be worked out from the records is worked out here.
import type { EctprRecord, EpisodeLog, IncidentReport, OutcomeRecord } from './ectpr-model.ts';
import { DECISION_FIELDS, parseDate, unrecordedDecisions } from './ectpr-rules.ts';
import { episodeConcordant, retrievalMinutes } from './ectpr-store.ts';

/** Clinical spec §8: the record should be retrieved within 10 minutes of ED arrival. */
export const RETRIEVAL_TARGET_MINUTES = 10;

const DAY_MS = 86_400_000;
const signedAt = (record: EctprRecord): string | null => record.signatures.find((s) => s.role === 'TREATING_CONSULTANT')?.signedAt ?? null;

/** Reach: whole days from the date found eligible to the consultant's signature. Null until both exist. */
export function reachDays(record: EctprRecord): number | null {
  const eligible = parseDate(record.clinical.eligibilityDate);
  const signed = parseDate(signedAt(record));
  if (!eligible || !signed) return null;
  const day = (d: Date) => Date.UTC(d.getFullYear(), d.getMonth(), d.getDate());
  return Math.round((day(signed) - day(eligible)) / DAY_MS);
}

export type ValuesIndicator = { valuesFilled: boolean; treatmentFilled: number; treatmentTotal: number; limitsWithoutValues: boolean };

/** Values section completed: §5/§6 (the patient's words, what they would not accept) against treatment fields filled. */
export function valuesIndicator(record: EctprRecord): ValuesIndicator {
  const valuesFilled = Boolean(record.values.ownWords.trim()) || record.values.unacceptableOutcomes.some((v) => v.trim());
  const treatmentTotal = DECISION_FIELDS.length;
  const treatmentFilled = treatmentTotal - unrecordedDecisions(record).length;
  return { valuesFilled, treatmentFilled, treatmentTotal, limitsWithoutValues: treatmentFilled > 0 && !valuesFilled };
}

/** The signed version in force at a moment: newest consultant signature at or before it, unless cancelled by then. */
export function planInForceAt(versions: EctprRecord[], at: string): EctprRecord | null {
  const moment = new Date(at).getTime();
  if (Number.isNaN(moment)) return null;
  const signed = versions
    .filter((r) => { const s = signedAt(r); return s !== null && new Date(s).getTime() <= moment; })
    .sort((a, b) => new Date(signedAt(b)!).getTime() - new Date(signedAt(a)!).getTime());
  const newest = signed[0];
  if (!newest) return null;
  if (newest.revokedAt && new Date(newest.revokedAt).getTime() <= moment) return null;
  return newest;
}

export type EpisodeDraft = Pick<EpisodeLog, 'edArrivalAt' | 'firstRecordAccessAt' | 'highestSupportDelivered' | 'icuAdmitted' | 'vasopressorsGiven' | 'dialysisGiven' | 'cprGiven' | 'discordanceReason'>;
export type EpisodeProblem = 'NO_ARRIVAL' | 'ACCESS_BEFORE_ARRIVAL' | 'REASON_NEEDED';

/** What stops an episode being saved. Returned as codes so the screen can word them in the reader's language. */
export function episodeProblems(draft: EpisodeDraft, plan: EctprRecord | null): EpisodeProblem[] {
  const problems: EpisodeProblem[] = [];
  const arrival = new Date(draft.edArrivalAt).getTime();
  if (Number.isNaN(arrival)) problems.push('NO_ARRIVAL');
  if (draft.firstRecordAccessAt && !Number.isNaN(arrival) && new Date(draft.firstRecordAccessAt).getTime() < arrival) problems.push('ACCESS_BEFORE_ARRIVAL');
  if (plan && episodeConcordant(draft as EpisodeLog, plan) === false && !draft.discordanceReason.trim()) problems.push('REASON_NEEDED');
  return problems;
}

/** First time the ED opened the record at or after arrival, from the access log. Clinic and family views do not count. */
export function firstEdAccessAfter(audit: Array<{ at: string; action: string; actor: string }>, arrivalAt: string): string | null {
  const arrival = new Date(arrivalAt).getTime();
  if (Number.isNaN(arrival)) return null;
  const times = audit
    .filter((e) => e.action === 'BREAK_GLASS' || (e.action === 'VIEW' && e.actor.startsWith('ED')))
    .map((e) => e.at)
    .filter((at) => new Date(at).getTime() >= arrival)
    .sort((a, b) => new Date(a).getTime() - new Date(b).getTime());
  return times[0] ?? null;
}

export type EpisodeRow = { episode: EpisodeLog; minutes: number | null; withinTarget: boolean | null; concordant: boolean | null };

export function episodeRow(episode: EpisodeLog, versions: EctprRecord[]): EpisodeRow {
  const minutes = retrievalMinutes(episode);
  const plan = versions.find((r) => r.id === episode.ectprId) ?? planInForceAt(versions, episode.edArrivalAt);
  return { episode, minutes, withinTarget: minutes === null ? null : minutes <= RETRIEVAL_TARGET_MINUTES, concordant: plan ? episodeConcordant(episode, plan) : null };
}

export type PilotSummary = { episodes: number; retrieved: number; withinTarget: number; compared: number; concordant: number };

export function pilotSummary(rows: EpisodeRow[]): PilotSummary {
  return {
    episodes: rows.length,
    retrieved: rows.filter((r) => r.minutes !== null).length,
    withinTarget: rows.filter((r) => r.withinTarget === true).length,
    compared: rows.filter((r) => r.concordant !== null).length,
    concordant: rows.filter((r) => r.concordant === true).length,
  };
}

/** Died where they preferred? Null when either is unknown. */
export function placeMatchesPreference(outcome: Pick<OutcomeRecord, 'placeOfDeath' | 'preferredPlaceOfDeath'>): boolean | null {
  return outcome.placeOfDeath && outcome.preferredPlaceOfDeath ? outcome.placeOfDeath === outcome.preferredPlaceOfDeath : null;
}

/** Hours from admission to the first symptom-control prescription, to one decimal. Null when unknown or out of order. */
export function hoursToSymptomRx(outcome: Pick<OutcomeRecord, 'lastAdmissionAt' | 'firstSymptomControlRxAt'>): number | null {
  if (!outcome.lastAdmissionAt || !outcome.firstSymptomControlRxAt) return null;
  const ms = new Date(outcome.firstSymptomControlRxAt).getTime() - new Date(outcome.lastAdmissionAt).getTime();
  return Number.isNaN(ms) || ms < 0 ? null : Math.round(ms / 360_000) / 10;
}

export type OutcomeDraft = Omit<OutcomeRecord, 'id' | 'patientId' | 'recordedBy' | 'recordedAt'>;
export type OutcomeProblem = 'NOTHING_ENTERED' | 'DAYS_OUT_OF_RANGE' | 'RX_BEFORE_ADMISSION' | 'NEGATIVE_SPEND';

export function outcomeProblems(draft: OutcomeDraft): OutcomeProblem[] {
  const problems: OutcomeProblem[] = [];
  if (!Object.values(draft).some((v) => v !== null && v !== '')) problems.push('NOTHING_ENTERED');
  if ([draft.icuDaysLast30, draft.ventilatorDaysLast30].some((days) => days !== null && (days < 0 || days > 30))) problems.push('DAYS_OUT_OF_RANGE');
  if (draft.lastAdmissionAt && draft.firstSymptomControlRxAt && new Date(draft.firstSymptomControlRxAt).getTime() < new Date(draft.lastAdmissionAt).getTime()) problems.push('RX_BEFORE_ADMISSION');
  if (draft.outOfPocketLastAdmissionInr !== null && draft.outOfPocketLastAdmissionInr < 0) problems.push('NEGATIVE_SPEND');
  return problems;
}

/** An incident must say what happened: a box ticked or a note written. */
export function incidentIsEmpty(draft: Pick<IncidentReport, 'appliedToReversibleIllness' | 'familyFeltPushed' | 'details'>): boolean {
  return !draft.appliedToReversibleIllness && !draft.familyFeltPushed && !draft.details.trim();
}
