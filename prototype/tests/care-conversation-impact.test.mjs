import assert from 'node:assert/strict';
import test from 'node:test';
import {
  careConversationImpactEpisodes,
  careConversationRequestEpisodeId,
  recordCareConversationImpactEvent,
  summarizeCareConversationImpact,
} from '../app/care-conversation-impact.ts';

test('conversation stages keep one attributed episode and leave unknown history uncounted', () => {
  const id = careConversationRequestEpisodeId('request-1');
  let episodes = recordCareConversationImpactEvent([], {
    id, patientId: 'patient-1', requestId: 'request-1', initiatedBy: 'patient', stage: 'requested', at: '2026-09-24T09:00:00.000Z',
  });
  episodes = recordCareConversationImpactEvent(episodes, { id, patientId: 'patient-1', initiatedBy: 'patient', stage: 'started', at: '2026-09-24T10:00:00.000Z' });
  episodes = recordCareConversationImpactEvent(episodes, { id, patientId: 'patient-1', initiatedBy: 'patient', stage: 'completed', at: '2026-09-24T10:30:00.000Z' });
  episodes = recordCareConversationImpactEvent(episodes, { id, patientId: 'patient-1', initiatedBy: 'patient', stage: 'documented', at: '2026-09-24T10:40:00.000Z' });
  episodes = recordCareConversationImpactEvent(episodes, { id, patientId: 'patient-1', initiatedBy: 'patient', stage: 'signed', at: '2026-09-24T10:45:00.000Z', signedVersion: 2 });
  const signed = episodes;
  episodes = recordCareConversationImpactEvent(episodes, { id, patientId: 'patient-1', initiatedBy: 'patient', stage: 'signed', at: '2026-09-24T11:00:00.000Z', signedVersion: 3 });
  assert.equal(episodes, signed, 'repeated stage events must not duplicate or rewrite the episode');

  const doctorId = 'doctor:episode-1';
  episodes = recordCareConversationImpactEvent(episodes, { id: doctorId, patientId: 'patient-2', initiatedBy: 'doctor', stage: 'started', at: '2026-09-24T11:00:00.000Z' });
  const withUnknown = recordCareConversationImpactEvent(episodes, { id: 'legacy-note-1', patientId: 'patient-3', stage: 'signed', at: '2026-09-23T08:00:00.000Z' });
  assert.equal(withUnknown, episodes, 'a record without a known initiator must remain outside the counts');

  const legacyRequest = {
    id: 'request-1', patientId: 'patient-1', requestedBy: 'Patient', role: 'patient', topics: [], note: '',
    status: 'completed', createdAt: '2026-09-24T09:00:00.000Z', updatedAt: '2026-09-24T10:45:00.000Z', signedVersion: 2,
  };
  const rows = careConversationImpactEpisodes(episodes, [legacyRequest]);
  const counts = summarizeCareConversationImpact(rows);
  assert.equal(rows.length, 2, 'the request and its signed episode are one row');
  assert.deepEqual(counts, { patientRequests: 1, familyRequests: 0, doctorStarted: 1, completed: 1, documented: 1, signed: 1 });
});
