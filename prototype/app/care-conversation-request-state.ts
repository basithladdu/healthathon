export type CareConversationRequestStatus = 'requested' | 'in-progress' | 'completed' | 'cancelled';

export type CareConversationRequest = {
  id: string;
  patientId: string;
  requestedBy: string;
  role: 'patient' | 'family';
  topics: string[];
  note: string;
  status: CareConversationRequestStatus;
  createdAt: string;
  updatedAt: string;
  signedVersion?: number;
};

export function createCareConversationRequest(input: Pick<CareConversationRequest, 'patientId' | 'requestedBy' | 'role' | 'topics' | 'note'>, now = new Date().toISOString()): CareConversationRequest {
  return { ...input, topics: [...input.topics], id: crypto.randomUUID(), status: 'requested', createdAt: now, updatedAt: now };
}
