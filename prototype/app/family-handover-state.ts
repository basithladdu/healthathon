export type HandoverContact = {
  id: string;
  patientId: string;
  name: string;
  relationship: string;
  phone: string;
};

export type HandoverNote = {
  version: number;
  physician: string;
  releasedAt: string;
  fields: Array<{ label: string; value: string }>;
};

export type HandoverSelection = {
  note: boolean;
  checklist: boolean;
  reports: boolean;
  contacts: boolean;
};

export const DEFAULT_HANDOVER_SELECTION: HandoverSelection = {
  note: true,
  checklist: false,
  reports: false,
  contacts: false,
};

type HandoverInput = {
  patientId: string;
  patientName: string;
  author: string;
  note: HandoverNote | null;
  calendarText: string;
  reportNames: string[];
  contacts: HandoverContact[];
  selection: HandoverSelection;
};

function cleanContact(contact: HandoverContact): HandoverContact | null {
  const name = contact.name.trim();
  const relationship = contact.relationship.trim();
  const phone = contact.phone.trim();
  if (!contact.id || !contact.patientId || !name || name.length > 80 || relationship.length > 80 || phone.length > 60) return null;
  return { ...contact, name, relationship, phone };
}

export function addHandoverContact(contacts: HandoverContact[], patientId: string, contact: HandoverContact): HandoverContact[] {
  const clean = cleanContact(contact);
  if (!clean || clean.patientId !== patientId || contacts.some((item) => item.id === clean.id)) return contacts;
  return [...contacts, clean];
}

export function updateHandoverContact(contacts: HandoverContact[], patientId: string, contact: HandoverContact): HandoverContact[] {
  const clean = cleanContact(contact);
  if (!clean || clean.patientId !== patientId) return contacts;
  return contacts.map((item) => item.patientId === patientId && item.id === clean.id ? clean : item);
}

export function removeHandoverContact(contacts: HandoverContact[], patientId: string, contactId: string): HandoverContact[] {
  return contacts.filter((item) => item.patientId !== patientId || item.id !== contactId);
}

export function restoreHandoverContact(contacts: HandoverContact[], patientId: string, contact: HandoverContact, index: number): HandoverContact[] {
  const clean = cleanContact(contact);
  if (!clean || clean.patientId !== patientId || contacts.some((item) => item.id === clean.id)) return contacts;
  const restored = [...contacts];
  restored.splice(Math.max(0, Math.min(index, restored.length)), 0, clean);
  return restored;
}

export function buildHandoverPack(input: HandoverInput): string | null {
  const { patientId, patientName, author, note, calendarText, reportNames, contacts, selection } = input;
  if (!patientId || !patientName.trim()) return null;
  const sections: string[] = [];
  if (selection.note && note) {
    sections.push([
      'DOCTOR-REVIEWED CARE NOTE',
      `Version: ${note.version}`,
      `Doctor: ${note.physician}`,
      `Reviewed note saved: ${note.releasedAt}`,
      ...note.fields.map(({ label, value }) => `${label}\n${value}`),
      'This conversation note is not a prescription or a legal directive.',
    ].join('\n\n'));
  }
  const familySections: string[] = [];
  if (selection.checklist && calendarText.trim()) familySections.push(`FAMILY CHECKLIST\n\n${calendarText}`);
  const files = reportNames.filter((name) => name.trim());
  if (selection.reports && files.length) familySections.push(`FILES TO BRING — NAMES ONLY, FILES ARE NOT ATTACHED\n\n${files.map((name) => `• ${name}`).join('\n')}`);
  const patientContacts = contacts.filter((contact) => contact.patientId === patientId);
  if (selection.contacts && patientContacts.length) {
    familySections.push(`CONTACTS ADDED BY THE FAMILY\n\n${patientContacts.map((contact) => [contact.name, contact.relationship, contact.phone].filter(Boolean).join(' · ')).join('\n')}`);
  }
  if (familySections.length) sections.push(`FAMILY-ADDED INFORMATION\nAdded to this pack by ${author}. Not part of the doctor-reviewed note.\n\n${familySections.join('\n\n')}`);
  if (!sections.length) return null;
  return [`SAANTHVANA — CARE TO TAKE WITH YOU`, `Patient: ${patientName}\nPatient ID: ${patientId}`, ...sections].join('\n\n');
}

export function handoverFileName(patientId: string, noteVersion: number | null): string {
  const safeId = patientId.replace(/[^a-zA-Z0-9_-]/g, '-').slice(0, 60) || 'patient';
  return `saanthvana-${safeId}${noteVersion === null ? '' : `-note-v${noteVersion}`}-care-pack.txt`;
}
