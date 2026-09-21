'use client';

import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import {
  addHandoverContact, buildHandoverPack, DEFAULT_HANDOVER_SELECTION, handoverFileName,
  removeHandoverContact, restoreHandoverContact, updateHandoverContact,
  type HandoverContact, type HandoverNote, type HandoverSelection,
} from './family-handover-state';

type FamilyHandoverPackProps = {
  patientId: string;
  patientName: string;
  author: string;
  hindi: boolean;
  note: HandoverNote | null;
  calendarText: string;
  reportNames: string[];
  contacts: HandoverContact[];
  onContactsChange: (contacts: HandoverContact[]) => void;
};

export function FamilyHandoverPack(props: FamilyHandoverPackProps) {
  return <PatientHandoverPack key={props.patientId} {...props} />;
}

function PatientHandoverPack(props: FamilyHandoverPackProps) {
  const { patientId, patientName, hindi, note, calendarText, reportNames, contacts, onContactsChange } = props;
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [selection, setSelection] = useState<HandoverSelection>({ ...DEFAULT_HANDOVER_SELECTION });
  const [editing, setEditing] = useState<HandoverContact | null>(null);
  const [name, setName] = useState('');
  const [relationship, setRelationship] = useState('');
  const [phone, setPhone] = useState('');
  const [removed, setRemoved] = useState<{ contact: HandoverContact; index: number } | null>(null);
  const [message, setMessage] = useState('');
  const nameInput = useRef<HTMLInputElement>(null);
  const printFrame = useRef<HTMLIFrameElement | null>(null);
  const patientContacts = contacts.filter((contact) => contact.patientId === patientId);
  const pack = buildHandoverPack({ ...props, selection });
  const fileCount = reportNames.filter((value) => value.trim()).length;

  useEffect(() => () => { printFrame.current?.remove(); }, []);

  function resetContactForm() {
    setEditing(null); setName(''); setRelationship(''); setPhone('');
  }

  function saveContact(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!name.trim()) return;
    const contact = { id: editing?.id ?? crypto.randomUUID(), patientId, name, relationship, phone };
    onContactsChange(editing ? updateHandoverContact(contacts, patientId, contact) : addHandoverContact(contacts, patientId, contact));
    setMessage(t(editing ? 'Contact updated.' : 'Contact added.', editing ? 'संपर्क बदल दिया।' : 'संपर्क जोड़ दिया।'));
    resetContactForm();
  }

  function editContact(contact: HandoverContact) {
    setEditing(contact); setName(contact.name); setRelationship(contact.relationship); setPhone(contact.phone);
    nameInput.current?.focus();
  }

  function removeContact(contact: HandoverContact) {
    setRemoved({ contact, index: contacts.findIndex((item) => item.id === contact.id && item.patientId === patientId) });
    onContactsChange(removeHandoverContact(contacts, patientId, contact.id));
    if (editing?.id === contact.id) resetContactForm();
    setMessage(t(`${contact.name} removed.`, `${contact.name} को हटा दिया।`));
  }

  function download() {
    if (!pack) return;
    const url = URL.createObjectURL(new Blob([pack], { type: 'text/plain;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = handoverFileName(patientId, selection.note && note ? note.version : null);
    document.body.appendChild(link); link.click(); link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    setMessage(t('Your download is ready.', 'आपकी कॉपी डाउनलोड हो रही है।'));
  }

  function print() {
    if (!pack) return;
    printFrame.current?.remove();
    const frame = document.createElement('iframe');
    frame.title = t('Print the care pack', 'देखभाल की कॉपी प्रिंट करें');
    frame.className = 'handover-print-frame';
    frame.setAttribute('aria-hidden', 'true');
    document.body.appendChild(frame);
    printFrame.current = frame;
    const page = frame.contentDocument;
    const printWindow = frame.contentWindow;
    if (!page || !printWindow) {
      frame.remove();
      setMessage(t('Print could not open. You can still save a copy.', 'प्रिंट नहीं खुला। आप कॉपी डाउनलोड कर सकते हैं।'));
      return;
    }
    const title = page.createElement('title'); title.textContent = `${patientName} — care pack`;
    const style = page.createElement('style');
    style.textContent = '@page { margin: 18mm; } body { margin: 0; color: #172c24; } pre { white-space: pre-wrap; overflow-wrap: anywhere; font: 12pt/1.55 Arial, sans-serif; }';
    const content = page.createElement('pre'); content.textContent = pack;
    page.head.appendChild(title); page.head.appendChild(style); page.body.appendChild(content);
    printWindow.addEventListener('afterprint', () => { frame.remove(); printFrame.current = null; }, { once: true });
    printWindow.focus(); printWindow.print();
    setMessage(t('Print opened. Choose your printer or Save as PDF.', 'प्रिंट खुल गया। प्रिंटर चुनें या PDF सेव करें।'));
  }

  function choice(key: keyof HandoverSelection, label: string, available: boolean) {
    return <label className={`handover-choice${available ? '' : ' is-unavailable'}`}>
      <input type="checkbox" checked={available && selection[key]} disabled={!available} onChange={(event) => setSelection((current) => ({ ...current, [key]: event.target.checked }))} />
      <span>{label}</span>
    </label>;
  }

  return <section className="family-handover-pack" aria-labelledby={`${id}-heading`}>
    <div className="handover-heading"><h1 id={`${id}-heading`}>{t('Take to the next doctor', 'अगले डॉक्टर को साथ ले जाएँ')}</h1><span>{patientName}</span></div>
    <div className="handover-grid">
      <div className="handover-options">
        <fieldset className="handover-choices"><legend>{t('What goes in?', 'क्या शामिल करें?')}</legend>
          {choice('note', note ? t(`Doctor’s note · v${note.version}`, `डॉक्टर का नोट · v${note.version}`) : t('Doctor’s note — not available yet', 'डॉक्टर का नोट — अभी नहीं है'), Boolean(note))}
          {choice('checklist', t('My checklist and calendar', 'मेरी सूची और कैलेंडर'), Boolean(calendarText.trim()))}
          {choice('reports', t(`Report names (${fileCount})`, `रिपोर्ट के नाम (${fileCount})`), fileCount > 0)}
          {choice('contacts', t(`People to call (${patientContacts.length})`, `संपर्क (${patientContacts.length})`), patientContacts.length > 0)}
        </fieldset>
        <section className="handover-contacts" aria-labelledby={`${id}-contacts`}>
          <h2 id={`${id}-contacts`}>{t('People to call', 'किसे फ़ोन करें')}</h2>
          {patientContacts.length > 0 && <ul className="handover-contact-list">{patientContacts.map((contact) => <li key={contact.id}>
            <div><strong>{contact.name}</strong>{contact.relationship && <span>{contact.relationship}</span>}{contact.phone && <span>{contact.phone}</span>}</div>
            <div className="handover-contact-actions"><button type="button" onClick={() => editContact(contact)} aria-label={t(`Edit ${contact.name}`, `${contact.name} को बदलें`)}>{t('Edit', 'बदलें')}</button><button type="button" onClick={() => removeContact(contact)} aria-label={t(`Remove ${contact.name}`, `${contact.name} को हटाएँ`)}>{t('Remove', 'हटाएँ')}</button></div>
          </li>)}</ul>}
          <form onSubmit={saveContact} className="handover-contact-form">
            <label htmlFor={`${id}-name`}>{t('Name', 'नाम')}<input ref={nameInput} id={`${id}-name`} autoComplete="off" value={name} required maxLength={80} onChange={(event) => setName(event.target.value)} /></label>
            <div className="handover-contact-fields">
              <label htmlFor={`${id}-relationship`}>{t('Who are they?', 'कौन हैं?')}<input id={`${id}-relationship`} autoComplete="off" value={relationship} maxLength={80} onChange={(event) => setRelationship(event.target.value)} /></label>
              <label htmlFor={`${id}-phone`}>{t('Phone', 'फ़ोन')}<input id={`${id}-phone`} type="tel" autoComplete="off" value={phone} maxLength={60} onChange={(event) => setPhone(event.target.value)} /></label>
            </div>
            <div className="handover-form-actions"><button type="submit" className="secondary-button" disabled={!name.trim()}>{editing ? t('Save contact', 'संपर्क सेव करें') : t('Add contact', 'संपर्क जोड़ें')}</button>{editing && <button type="button" onClick={resetContactForm}>{t('Cancel', 'रद्द करें')}</button>}</div>
          </form>
          {removed && <div className="handover-undo"><span>{t(`${removed.contact.name} removed`, `${removed.contact.name} हटाए गए`)}</span><button type="button" onClick={() => {
            onContactsChange(restoreHandoverContact(contacts, patientId, removed.contact, removed.index)); setRemoved(null); setMessage(t('Contact restored.', 'संपर्क वापस जोड़ दिया।'));
          }}>{t('Undo', 'वापस लाएँ')}</button></div>}
        </section>
      </div>
      <section className="handover-preview" aria-labelledby={`${id}-preview`}>
        <div className="handover-preview-heading"><h2 id={`${id}-preview`}>{t('Your copy', 'आपकी कॉपी')}</h2><div className="handover-export-actions"><button type="button" className="primary-button" disabled={!pack} onClick={download}>{t('Save copy', 'कॉपी डाउनलोड करें')}</button><button type="button" className="secondary-button" disabled={!pack} onClick={print}>{t('Print', 'प्रिंट करें')}</button></div></div>
        {pack ? <pre className="handover-copy">{pack}</pre> : <p className="handover-empty">{t('Choose something to include.', 'कॉपी में क्या रखना है, चुनें।')}</p>}
      </section>
    </div>
    <p className="handover-message" role="status">{message}</p>
  </section>;
}
