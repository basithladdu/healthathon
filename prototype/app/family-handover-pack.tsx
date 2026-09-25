'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { downloadCarePdf, type CarePdfSection } from './care-pdf';
import type { SummaryRelease } from './summary-state';
import { matchingCareNoteAcknowledgements, hasChangedSignedCareNote, type CareNoteAcknowledgement } from './care-note-signing-state';
import {
  addHandoverContact, DEFAULT_HANDOVER_SELECTION, handoverFileName, mergeHandoverContacts,
  removeHandoverContact, restoreHandoverContact, updateHandoverContact,
  type HandoverContact, type HandoverNote, type HandoverSelection,
} from './family-handover-state';

type FamilyHandoverPackProps = {
  patientId: string;
  patientName: string;
  author: string;
  hindi: boolean;
  note: HandoverNote | null;
  signedRelease?: SummaryRelease;
  acknowledgements?: readonly CareNoteAcknowledgement[];
  calendarText: string;
  reportNames: string[];
  contacts: HandoverContact[];
  knownContacts?: readonly HandoverContact[];
  onContactsChange: (contacts: HandoverContact[]) => void;
};

export function FamilyHandoverPack(props: FamilyHandoverPackProps) {
  return <PatientHandoverPack key={props.patientId} {...props} />;
}

function PatientHandoverPack({ patientId, patientName, author, hindi, note, signedRelease, acknowledgements = [], calendarText, reportNames, contacts, knownContacts = [], onContactsChange }: FamilyHandoverPackProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [selection, setSelection] = useState<HandoverSelection>({ ...DEFAULT_HANDOVER_SELECTION });
  const [contactFormOpen, setContactFormOpen] = useState(false);
  const [editing, setEditing] = useState<HandoverContact | null>(null);
  const [name, setName] = useState('');
  const [relationship, setRelationship] = useState('');
  const [phone, setPhone] = useState('');
  const [omittedContacts, setOmittedContacts] = useState<string[]>([]);
  const [removed, setRemoved] = useState<{ contact: HandoverContact; index: number; wasSaved: boolean } | null>(null);
  const [message, setMessage] = useState('');
  const [downloading, setDownloading] = useState(false);
  const nameInput = useRef<HTMLInputElement>(null);
  const printFrame = useRef<HTMLIFrameElement | null>(null);
  const patientContacts = mergeHandoverContacts(contacts, knownContacts, patientId).filter((contact) => !omittedContacts.includes(contact.id));
  const files = reportNames.filter((value) => value.trim());
  const hasFamilyExtras = (selection.checklist && Boolean(calendarText.trim())) || (selection.reports && files.length > 0) || (selection.contacts && patientContacts.length > 0);
  const hasPack = (selection.note && Boolean(note)) || hasFamilyExtras;
  const reviewedDate = note && Number.isFinite(Date.parse(note.releasedAt)) ? new Date(note.releasedAt).toLocaleDateString(hindi ? 'hi-IN' : 'en-GB', { day: 'numeric', month: 'short', year: 'numeric' }) : note?.releasedAt;

  useEffect(() => () => { printFrame.current?.remove(); }, []);
  useEffect(() => { if (contactFormOpen) nameInput.current?.focus(); }, [contactFormOpen, editing]);

  function resetContactForm() {
    setEditing(null); setName(''); setRelationship(''); setPhone(''); setContactFormOpen(false);
  }

  function saveContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim()) return;
    const contact = { id: editing?.id ?? crypto.randomUUID(), patientId, name, relationship, phone };
    const saved = contacts.some((item) => item.id === contact.id && item.patientId === patientId);
    onContactsChange(saved ? updateHandoverContact(contacts, patientId, contact) : addHandoverContact(contacts, patientId, contact));
    setOmittedContacts((previous) => previous.filter((contactId) => contactId !== contact.id));
    setMessage(t(editing ? 'Contact updated.' : 'Contact added.', editing ? 'संपर्क बदल दिया।' : 'संपर्क जोड़ दिया।'));
    resetContactForm();
  }

  function editContact(contact: HandoverContact) {
    setEditing(contact); setName(contact.name); setRelationship(contact.relationship); setPhone(contact.phone); setContactFormOpen(true);
  }

  function removeContact(contact: HandoverContact) {
    const index = contacts.findIndex((item) => item.id === contact.id && item.patientId === patientId);
    setRemoved({ contact, index, wasSaved: index >= 0 });
    if (index >= 0) onContactsChange(removeHandoverContact(contacts, patientId, contact.id));
    setOmittedContacts((previous) => [...previous, contact.id]);
    if (editing?.id === contact.id) resetContactForm();
    setMessage(t(`${contact.name} left out.`, `${contact.name} को शामिल नहीं किया।`));
  }

  function packetSections(): CarePdfSection[] {
    const sections: CarePdfSection[] = [];
    if (selection.note && note) {
      sections.push({ heading: t('Care Note', 'देखभाल का नोट'), lines: [`${t('Version', 'संस्करण')}: ${note.version}`, `${t('Doctor', 'डॉक्टर')}: ${note.physician}`, `${t('Released', 'जारी किया')}: ${note.releasedAt}`] });
      sections.push(...note.fields.map(({ label, value }) => ({ heading: label, lines: [value] })));
      sections.push({ heading: t('Care conversation', 'देखभाल की बातचीत'), lines: [t('This reviewed conversation note is not a prescription or a legal directive.', 'यह मंज़ूर किया हुआ बातचीत का नोट है; दवा का पर्चा या क़ानूनी निर्देश नहीं।')] });
    }
    if (hasFamilyExtras) sections.push({ heading: t('Family extras', 'परिवार की जानकारी'), lines: [`${t('Included by', 'शामिल किया')}: ${author}`, t('Separate from the doctor-reviewed note.', 'डॉक्टर के मंज़ूर किए हुए नोट से अलग।')] });
    if (selection.checklist && calendarText.trim()) sections.push({ heading: t('My checklist and calendar', 'मेरी सूची और कैलेंडर'), lines: [calendarText] });
    if (selection.reports && files.length) sections.push({ heading: t('Reports to bring', 'साथ लाने वाली रिपोर्ट'), lines: [t('File names only. Bring the original reports.', 'यहाँ केवल फ़ाइल के नाम हैं। मूल रिपोर्ट साथ लाएँ।'), ...files] });
    if (selection.contacts && patientContacts.length) sections.push({ heading: t('People to call', 'किसे फ़ोन करें'), lines: patientContacts.map((contact) => [contact.name, contact.relationship, contact.phone].filter(Boolean).join(' · ')) });
    return sections;
  }

  async function download() {
    if (!hasPack || downloading) return;
    setDownloading(true); setMessage('');
    try {
      await downloadCarePdf({ fileName: handoverFileName(patientId, selection.note && note ? note.version : null), title: t('Visit pack', 'मुलाक़ात की फ़ाइल'), subtitle: `${patientName} · ${patientId}`, sections: packetSections(), ...(selection.note && signedRelease ? { careNote: { release: signedRelease, acknowledgements } } : {}) });
      setMessage(t('Your PDF is downloading.', 'आपकी PDF डाउनलोड हो रही है।'));
    } catch (failure) {
      setMessage(failure instanceof Error ? failure.message : t('The PDF could not download. Try Print.', 'PDF डाउनलोड नहीं हुई। प्रिंट करें।'));
    } finally { setDownloading(false); }
  }

  function print() {
    if (!hasPack) return;
    if (selection.note && signedRelease && hasChangedSignedCareNote(acknowledgements, signedRelease)) { setMessage(t('This note no longer matches its signed version.', 'यह नोट हस्ताक्षर किए हुए संस्करण से मेल नहीं खाता।')); return; }
    printFrame.current?.remove();
    const frame = document.createElement('iframe');
    frame.title = t('Print the care pack', 'देखभाल की कॉपी प्रिंट करें');
    frame.className = 'handover-print-frame'; frame.setAttribute('aria-hidden', 'true');
    document.body.appendChild(frame); printFrame.current = frame;
    const page = frame.contentDocument; const printWindow = frame.contentWindow;
    if (!page || !printWindow) { frame.remove(); setMessage(t('Print could not open. Download the PDF instead.', 'प्रिंट नहीं खुला। PDF डाउनलोड करें।')); return; }
    const title = page.createElement('title'); title.textContent = `${patientName} — care pack`;
    const style = page.createElement('style');
    style.textContent = '@page{margin:16mm}body{margin:0;color:#203d33;font:11pt/1.55 Arial,sans-serif}h1{font-size:22pt;margin:0 0 6pt}h2{font-size:12pt;color:#99503a;break-after:avoid;margin:18pt 0 5pt}p{white-space:pre-wrap;overflow-wrap:anywhere;margin:0 0 6pt}header{border-bottom:1pt solid #cfd9ce;padding-bottom:12pt;margin-bottom:16pt}';
    page.head.appendChild(title); page.head.appendChild(style);
    const header = page.createElement('header'); const heading = page.createElement('h1'); heading.textContent = patientName;
    const identity = page.createElement('p'); identity.textContent = `${t('Visit pack', 'मुलाक़ात की फ़ाइल')} · ${patientId}`;
    header.appendChild(heading); header.appendChild(identity); page.body.appendChild(header);
    const printSections = packetSections();
    if (selection.note && signedRelease?.signature) printSections.push({ heading: t('Recorded signatures', 'दर्ज हस्ताक्षर'), lines: [signedRelease.signature.name, signedRelease.signature.signedAt, ...matchingCareNoteAcknowledgements(acknowledgements, signedRelease).map((entry) => `${entry.signerName} · ${entry.signerRole} · ${entry.signedAt} · V${entry.version}`), t('Typed names; no verified digital signature.', 'टाइप किए हुए नाम; सत्यापित डिजिटल हस्ताक्षर नहीं।')] });
    for (const section of printSections) {
      const heading = page.createElement('h2'); heading.textContent = section.heading; page.body.appendChild(heading);
      for (const line of section.lines) { const paragraph = page.createElement('p'); paragraph.textContent = line; page.body.appendChild(paragraph); }
    }
    printWindow.addEventListener('afterprint', () => { frame.remove(); printFrame.current = null; }, { once: true });
    printWindow.focus(); printWindow.print();
  }

  function choice(key: keyof HandoverSelection, label: string, available: boolean) {
    return <label className={`handover-choice${available && selection[key] ? ' is-selected' : ''}${available ? '' : ' is-unavailable'}`}>
      <input type="checkbox" checked={available && selection[key]} disabled={!available} onChange={(event) => setSelection((current) => ({ ...current, [key]: event.target.checked }))} />
      <span>{label}</span>
    </label>;
  }

  return <section className="family-handover-pack" aria-labelledby={`${id}-heading`}>
    <div className="handover-heading"><h1 id={`${id}-heading`}>{t('Visit pack', 'मुलाक़ात की फ़ाइल')}</h1><div className="handover-export-actions"><button type="button" className="primary-button" disabled={!hasPack || downloading} onClick={download}>{downloading ? t('Making PDF…', 'PDF बन रही है…') : t('Download PDF', 'PDF डाउनलोड करें')}</button><button type="button" disabled={!hasPack} onClick={print}>{t('Print', 'प्रिंट करें')}</button></div></div>
    <fieldset className="handover-choices"><legend>{t('Include', 'शामिल करें')}</legend><div className="handover-choice-grid">
      {choice('note', note ? t(`Care Note · v${note.version}`, `देखभाल का नोट · v${note.version}`) : t('Awaiting Care Note', 'देखभाल के नोट का इंतज़ार'), Boolean(note))}
      {choice('checklist', t('My checklist', 'मेरी सूची'), Boolean(calendarText.trim()))}
      {choice('reports', t(`Reports to bring · ${files.length}`, `साथ लाने वाली रिपोर्ट · ${files.length}`), files.length > 0)}
      {choice('contacts', t(`People to call · ${patientContacts.length}`, `संपर्क · ${patientContacts.length}`), patientContacts.length > 0)}
    </div></fieldset>

    <article className="handover-preview" aria-labelledby={`${id}-preview`}>
      <header className="handover-preview-heading"><div><span className="handover-paper-label">SAANTHVANA</span><h2 id={`${id}-preview`}>{patientName}</h2></div><span className="handover-patient-id">{patientId}</span></header>
      {selection.note && note && <section className="handover-reviewed">
        <div className="handover-note-heading"><h3>{t('Care Note', 'देखभाल का नोट')}</h3><span>V{note.version}</span></div>
        <p className="handover-note-source">{note.physician} · <time dateTime={note.releasedAt}>{reviewedDate}</time></p>
        <dl className="handover-note-fields">{note.fields.map(({ label, value }, index) => <div key={`${index}-${label}`}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
        <p className="handover-clinical-boundary">{t('A care conversation, not a prescription or legal directive.', 'देखभाल की बातचीत; दवा का पर्चा या क़ानूनी निर्देश नहीं।')}</p>
      </section>}
      {hasFamilyExtras && <section className="handover-family-extras"><div className="handover-family-heading"><h3>{t('Family extras', 'परिवार की जानकारी')}</h3><span>{author}</span></div>
        {selection.checklist && calendarText.trim() && <section className="handover-extra-section"><h4>{t('My checklist and calendar', 'मेरी सूची और कैलेंडर')}</h4><p className="handover-calendar-copy">{calendarText}</p></section>}
        {selection.reports && files.length > 0 && <section className="handover-extra-section"><h4>{t('Reports to bring', 'साथ लाने वाली रिपोर्ट')}</h4><ul className="handover-report-list">{files.map((file, index) => <li key={`${index}-${file}`}>{file}</li>)}</ul><p className="handover-clinical-boundary">{t('Bring the original reports.', 'मूल रिपोर्ट साथ लाएँ।')}</p></section>}
        {selection.contacts && patientContacts.length > 0 && <section className="handover-extra-section"><h4>{t('People to call', 'किसे फ़ोन करें')}</h4><dl className="handover-preview-contacts">{patientContacts.map((contact) => <div key={contact.id}><dt>{contact.name}{contact.relationship && <span>{contact.relationship}</span>}</dt><dd>{contact.phone ? <a href={`tel:${contact.phone.replace(/[^\d+*#,;]/g, '')}`} aria-label={t(`Call ${contact.name}`, `${contact.name} को फ़ोन करें`)}>{contact.phone}</a> : t('No number added', 'नंबर नहीं जोड़ा')}</dd></div>)}</dl></section>}
      </section>}
      {!hasPack && <p className="handover-empty">{t('Choose what to take with you.', 'साथ ले जाने वाली जानकारी चुनें।')}</p>}
    </article>

    <details className="handover-contacts"><summary>{t('People to call', 'किसे फ़ोन करें')} <span>{patientContacts.length}</span></summary>
      {patientContacts.length > 0 && <ul className="handover-contact-list">{patientContacts.map((contact) => <li key={contact.id}>
        <div><strong>{contact.name}</strong>{contact.relationship && <span>{contact.relationship}</span>}{contact.phone && <span>{contact.phone}</span>}</div>
        <div className="handover-contact-actions"><button type="button" onClick={() => editContact(contact)} aria-label={t(`Edit ${contact.name}`, `${contact.name} को बदलें`)}>{t('Edit', 'बदलें')}</button><button type="button" onClick={() => removeContact(contact)} aria-label={t(`Leave out ${contact.name}`, `${contact.name} को शामिल न करें`)}>{t('Not needed', 'ज़रूरत नहीं')}</button></div>
      </li>)}</ul>}
      {!contactFormOpen && <button type="button" onClick={() => { resetContactForm(); setContactFormOpen(true); }}>{t('Add someone', 'किसी को जोड़ें')}</button>}
      {contactFormOpen && <form onSubmit={saveContact} className="handover-contact-form">
        <label htmlFor={`${id}-name`}>{t('Name', 'नाम')}<input ref={nameInput} id={`${id}-name`} autoComplete="off" value={name} required maxLength={80} onChange={(event) => setName(event.target.value)} /></label>
        <div className="handover-contact-fields"><label htmlFor={`${id}-relationship`}>{t('Who are they? (optional)', 'कौन हैं? (चाहें तो)')}<input id={`${id}-relationship`} autoComplete="off" value={relationship} maxLength={80} onChange={(event) => setRelationship(event.target.value)} /></label><label htmlFor={`${id}-phone`}>{t('Phone (optional)', 'फ़ोन (चाहें तो)')}<input id={`${id}-phone`} type="tel" autoComplete="off" value={phone} maxLength={60} onChange={(event) => setPhone(event.target.value)} /></label></div>
        <div className="handover-form-actions"><button type="submit" className="secondary-button" disabled={!name.trim()}>{t('Save contact', 'संपर्क सेव करें')}</button><button type="button" onClick={resetContactForm}>{t('Cancel', 'रहने दें')}</button></div>
      </form>}
      {removed && <div className="handover-undo"><span>{t(`${removed.contact.name} left out`, `${removed.contact.name} शामिल नहीं हैं`)}</span><button type="button" onClick={() => { if (removed.wasSaved) onContactsChange(restoreHandoverContact(contacts, patientId, removed.contact, removed.index)); setOmittedContacts((previous) => previous.filter((contactId) => contactId !== removed.contact.id)); setRemoved(null); setMessage(t('Contact restored.', 'संपर्क वापस जोड़ दिया।')); }}>{t('Undo', 'वापस लाएँ')}</button></div>}
    </details>
    <p className="handover-message" role="status">{message}</p>
  </section>;
}
