import type { CareConversationRequest } from './care-conversation-request-state.ts';

export type CareConversationInitiator = 'patient' | 'family' | 'doctor';
export type CareConversationImpactStage = 'requested' | 'started' | 'completed' | 'documented' | 'signed' | 'cancelled';

export type CareConversationImpactEpisode = {
  id: string;
  patientId: string;
  initiatedBy: CareConversationInitiator;
  requestId?: string;
  requestedAt?: string;
  startedAt?: string;
  completedAt?: string;
  documentedAt?: string;
  signedAt?: string;
  signedVersion?: number;
  cancelledAt?: string;
};

export type CareConversationImpactEvent = {
  id: string;
  patientId: string;
  initiatedBy?: CareConversationInitiator;
  requestId?: string;
  stage: CareConversationImpactStage;
  at: string;
  signedVersion?: number;
};

const STAGE_FIELDS: Record<CareConversationImpactStage, keyof CareConversationImpactEpisode> = {
  requested: 'requestedAt',
  started: 'startedAt',
  completed: 'completedAt',
  documented: 'documentedAt',
  signed: 'signedAt',
  cancelled: 'cancelledAt',
};

export function recordCareConversationImpactEvent(
  episodes: CareConversationImpactEpisode[],
  event: CareConversationImpactEvent,
): CareConversationImpactEpisode[] {
  const existing = episodes.find((episode) => episode.id === event.id);
  if (existing && existing.patientId !== event.patientId) return episodes;
  if (!existing && !event.initiatedBy) return episodes;
  if (existing?.cancelledAt && event.stage !== 'cancelled') return episodes;

  const field = STAGE_FIELDS[event.stage];
  if (existing?.[field]) return episodes;
  const episode: CareConversationImpactEpisode = existing
    ? { ...existing, [field]: event.at }
    : {
        id: event.id,
        patientId: event.patientId,
        initiatedBy: event.initiatedBy!,
        ...(event.requestId ? { requestId: event.requestId } : {}),
        [field]: event.at,
      };

  if (event.stage === 'signed') {
    episode.documentedAt ??= event.at;
    if (event.signedVersion !== undefined) episode.signedVersion = event.signedVersion;
  }
  return existing ? episodes.map((item) => item.id === event.id ? episode : item) : [...episodes, episode];
}

export function careConversationRequestEpisodeId(requestId: string): string {
  return `request:${requestId}`;
}

export function careConversationImpactEpisodes(
  episodes: CareConversationImpactEpisode[],
  requests: CareConversationRequest[] = [],
): CareConversationImpactEpisode[] {
  const byId = new Map(episodes.map((episode) => [episode.id, episode]));
  for (const request of requests) {
    const id = careConversationRequestEpisodeId(request.id);
    if (byId.has(id)) continue;
    const known: CareConversationImpactEpisode = {
      id,
      patientId: request.patientId,
      initiatedBy: request.role,
      requestId: request.id,
      requestedAt: request.createdAt,
      ...(request.status === 'in-progress' ? { startedAt: request.updatedAt } : {}),
      ...(request.status === 'completed' ? {
        completedAt: request.updatedAt,
        ...(request.signedVersion !== undefined ? { documentedAt: request.updatedAt, signedAt: request.updatedAt, signedVersion: request.signedVersion } : {}),
      } : {}),
      ...(request.status === 'cancelled' ? { cancelledAt: request.updatedAt } : {}),
    };
    byId.set(id, known);
  }
  return [...byId.values()].sort((a, b) => (a.requestedAt ?? a.startedAt ?? a.completedAt ?? '').localeCompare(b.requestedAt ?? b.startedAt ?? b.completedAt ?? ''));
}

export function summarizeCareConversationImpact(episodes: CareConversationImpactEpisode[]) {
  return {
    patientRequests: episodes.filter((episode) => episode.initiatedBy === 'patient' && episode.requestedAt).length,
    familyRequests: episodes.filter((episode) => episode.initiatedBy === 'family' && episode.requestedAt).length,
    doctorStarted: episodes.filter((episode) => episode.initiatedBy === 'doctor' && episode.startedAt).length,
    completed: episodes.filter((episode) => episode.completedAt).length,
    documented: episodes.filter((episode) => episode.documentedAt).length,
    signed: episodes.filter((episode) => episode.signedAt).length,
  };
}

function csvCell(value: string | number | undefined): string {
  const text = value === undefined ? '' : String(value);
  return `"${text.replaceAll('"', '""')}"`;
}

export function careConversationImpactCsv(episodes: CareConversationImpactEpisode[]): string {
  const rows = [
    ['Episode ID', 'Patient ID', 'Initiated by', 'Requested at', 'Started at', 'Discussion record saved at', 'Care Note documented at', 'Doctor signed at', 'Signed version'],
    ...episodes.map((episode) => [
      episode.id,
      episode.patientId,
      episode.initiatedBy,
      episode.requestedAt,
      episode.startedAt,
      episode.completedAt,
      episode.documentedAt,
      episode.signedAt,
      episode.signedVersion,
    ]),
  ];
  return rows.map((row) => row.map(csvCell).join(',')).join('\r\n');
}
