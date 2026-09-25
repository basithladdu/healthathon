import { isValidAppointmentDate } from './appointment-state.ts';

export type OpenQuestionStatus = 'to-ask' | 'waiting' | 'replied' | 'closed';
export type OpenQuestionReply = { id: string; text: string; saidBy: string; date: string; recordedBy: string };
export type OpenQuestion = {
  id: string;
  patientId: string;
  topic: string;
  owner: string;
  due: string;
  status: OpenQuestionStatus;
  askedOn: string;
  nextStep: string;
  createdBy: string;
  updatedBy: string;
  createdOn: string;
  replies: OpenQuestionReply[];
};
export type OpenQuestionDetails = { topic: string; owner: string; due: string };

function validDetails(details: OpenQuestionDetails): boolean {
  return Boolean(details.topic.trim()) && details.topic.length <= 240 && details.owner.length <= 100
    && (!details.due || isValidAppointmentDate(details.due));
}

function changeQuestion(entries: OpenQuestion[], patientId: string, id: string, author: string, update: (question: OpenQuestion) => OpenQuestion): OpenQuestion[] {
  if (!author.trim()) return entries;
  const index = entries.findIndex((question) => question.patientId === patientId && question.id === id);
  if (index === -1) return entries;
  const updated = update(entries[index]);
  if (updated === entries[index]) return entries;
  const next = [...entries];
  next[index] = { ...updated, updatedBy: author.trim() };
  return next;
}

export function addOpenQuestion(entries: OpenQuestion[], patientId: string, author: string, today: string, id: string, details: OpenQuestionDetails): OpenQuestion[] {
  if (!patientId.trim() || !author.trim() || !id.trim() || !isValidAppointmentDate(today) || !validDetails(details) || entries.some((question) => question.id === id)) return entries;
  return [...entries, { id, patientId, topic: details.topic.trim(), owner: details.owner.trim(), due: details.due, status: 'to-ask', askedOn: '', nextStep: '', createdBy: author.trim(), updatedBy: author.trim(), createdOn: today, replies: [] }];
}

export function editOpenQuestion(entries: OpenQuestion[], patientId: string, id: string, author: string, details: OpenQuestionDetails): OpenQuestion[] {
  if (!validDetails(details)) return entries;
  return changeQuestion(entries, patientId, id, author, (question) => ({ ...question, topic: details.topic.trim(), owner: details.owner.trim(), due: details.due }));
}

export function markOpenQuestionAsked(entries: OpenQuestion[], patientId: string, id: string, author: string, date: string): OpenQuestion[] {
  if (!isValidAppointmentDate(date)) return entries;
  return changeQuestion(entries, patientId, id, author, (question) => question.status === 'to-ask' ? { ...question, status: 'waiting', askedOn: date } : question);
}

export function addOpenQuestionReply(entries: OpenQuestion[], patientId: string, id: string, author: string, reply: Omit<OpenQuestionReply, 'recordedBy'>): OpenQuestion[] {
  if (!reply.id.trim() || !reply.text.trim() || reply.text.length > 4000 || !reply.saidBy.trim() || reply.saidBy.length > 100 || !isValidAppointmentDate(reply.date)) return entries;
  return changeQuestion(entries, patientId, id, author, (question) => question.status === 'waiting' && !question.replies.some((item) => item.id === reply.id)
    ? { ...question, status: 'replied', replies: [...question.replies, { ...reply, saidBy: reply.saidBy.trim(), recordedBy: author.trim() }] }
    : question);
}

export function setOpenQuestionNextStep(entries: OpenQuestion[], patientId: string, id: string, author: string, nextStep: string, owner: string, due: string): OpenQuestion[] {
  if (!nextStep.trim() || nextStep.length > 1000 || owner.length > 100 || (due && !isValidAppointmentDate(due))) return entries;
  return changeQuestion(entries, patientId, id, author, (question) => question.status !== 'closed' ? { ...question, nextStep, owner: owner.trim(), due } : question);
}

export function postponeOpenQuestion(entries: OpenQuestion[], patientId: string, id: string, author: string, due: string, today: string): OpenQuestion[] {
  if (!isValidAppointmentDate(due) || !isValidAppointmentDate(today) || due < today) return entries;
  return changeQuestion(entries, patientId, id, author, (question) => question.status !== 'closed' ? { ...question, due } : question);
}

export function setOpenQuestionClosed(entries: OpenQuestion[], patientId: string, id: string, author: string, closed: boolean): OpenQuestion[] {
  return changeQuestion(entries, patientId, id, author, (question) => closed
    ? question.status === 'closed' ? question : { ...question, status: 'closed' }
    : question.status === 'closed' ? { ...question, status: 'to-ask', askedOn: '' } : question);
}

export function removeOpenQuestion(entries: OpenQuestion[], patientId: string, id: string): OpenQuestion[] {
  return entries.filter((question) => question.patientId !== patientId || question.id !== id);
}

export function restoreOpenQuestion(entries: OpenQuestion[], patientId: string, removed: OpenQuestion): OpenQuestion[] {
  if (removed.patientId !== patientId || entries.some((question) => question.id === removed.id)) return entries;
  return [...entries, removed];
}

export function selectOpenQuestions(entries: OpenQuestion[], patientId: string, closed = false): OpenQuestion[] {
  return entries.filter((question) => question.patientId === patientId && (question.status === 'closed') === closed)
    .sort((a, b) => (a.due || '9999-12-31').localeCompare(b.due || '9999-12-31') || a.createdOn.localeCompare(b.createdOn));
}

export function exportOpenQuestions(entries: OpenQuestion[], patientId: string): string {
  const status = { 'to-ask': 'To ask', waiting: 'Asked — no reply saved yet', replied: 'Reply saved by family', closed: 'Closed' };
  return [
    'SAANTHVANA — QUESTIONS TO FOLLOW UP', `Patient: ${patientId}`,
    'Family-written questions and replies. Names describe who the family says spoke; no message was received or sent by this app.',
    ...[...selectOpenQuestions(entries, patientId), ...selectOpenQuestions(entries, patientId, true)].map((question) => [
      question.topic, `Status: ${status[question.status]}`, `Added by ${question.createdBy} on ${question.createdOn}`,
      question.updatedBy !== question.createdBy ? `Last edited by: ${question.updatedBy}` : '',
      question.owner ? `Who follows up: ${question.owner}` : '', question.due ? `Follow up on: ${question.due}` : '',
      question.askedOn ? `Marked as asked on: ${question.askedOn}` : '',
      ...question.replies.map((reply) => `FAMILY-SAVED REPLY — ${reply.date}\nSaid by: ${reply.saidBy}\nWritten here by: ${reply.recordedBy}\n${reply.text}`),
      question.replies.length === 0 ? 'No reply has been saved.' : '',
      question.nextStep ? `Next step written by family:\n${question.nextStep}` : '',
    ].filter(Boolean).join('\n\n')),
  ].join('\n\n');
}
