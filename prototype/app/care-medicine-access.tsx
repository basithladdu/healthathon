'use client';

import { useState } from 'react';
import { CareRouteLink } from './care-route-link';
import { IconArrowRight } from './icons';
import { EMPTY_MEDICINE_ACCESS_ENTRY, type MedicineAccessEntry, type MedicineAccessSnapshot, type MedicineAccessStatus } from './care-medicine-access-state';
import './care-medicine-access.css';

export function CareMedicineAccess({ hindi, entry = EMPTY_MEDICINE_ACCESS_ENTRY, onChange, careTeam }: {
  hindi: boolean;
  entry?: MedicineAccessEntry;
  onChange: (entry: MedicineAccessEntry) => void;
  careTeam?: { name?: string; phone?: string };
}) {
  const [copyStatus, setCopyStatus] = useState('');
  const t = (en: string, hi: string) => hindi ? hi : en;
  const snapshot = (value: MedicineAccessEntry): MedicineAccessSnapshot => ({
    location: value.location,
    contactName: value.contactName,
    contactPhone: value.contactPhone,
    enquiry: value.enquiry,
    status: value.status,
    updatedAt: value.updatedAt,
  });
  const change = (patch: Partial<MedicineAccessSnapshot>) => onChange({
    ...entry,
    ...patch,
    previous: snapshot(entry),
    updatedAt: new Date().toISOString(),
  });
  const contactName = entry.contactName || careTeam?.name || '';
  const contactPhone = entry.contactPhone || careTeam?.phone || '';
  const searchUrl = entry.location.trim()
    ? `https://www.google.com/search?q=${encodeURIComponent(`palliative care oral morphine access ${entry.location.trim()}`)}`
    : '';
  const mapUrl = entry.location.trim() ? `/find-support?location=${encodeURIComponent(entry.location.trim())}` : '/find-support';
  const enquiryText = [
    'I would like to ask about access to oral morphine.',
    entry.location.trim() ? `Area: ${entry.location.trim()}` : '',
    entry.enquiry.trim() ? `My question: ${entry.enquiry.trim()}` : '',
  ].filter(Boolean).join('\n');
  const updateStatus = (status: MedicineAccessStatus) => change({ status });
  const undo = () => {
    if (!entry.previous) return;
    onChange({ ...entry.previous, previous: null });
  };

  async function copyEnquiry() {
    try {
      await navigator.clipboard.writeText(enquiryText);
      setCopyStatus(t('Enquiry copied', 'पूछताछ कॉपी हो गई'));
    } catch {
      setCopyStatus(t('Copy is unavailable in this browser', 'इस ब्राउज़र में कॉपी उपलब्ध नहीं है'));
    }
  }

  return <main className="care-medicine-access">
    <header className="cma-heading"><div><p>{t('Find support', 'मदद ढूँढें')}</p><h1>{t('Ask about oral morphine access', 'ओरल मॉर्फ़ीन के बारे में पूछें')}</h1></div><CareRouteLink view="access-care" className="cma-back">{t('Care options', 'देखभाल के विकल्प')} <IconArrowRight /></CareRouteLink></header>

    <section className="cma-search" aria-labelledby="cma-location-title">
      <h2 id="cma-location-title">{t('Search by area', 'इलाके से खोजें')}</h2>
      <div className="cma-search-row"><label className="cma-visually-hidden" htmlFor="cma-location">{t('State, district or city', 'राज्य, ज़िला या शहर')}</label><input id="cma-location" value={entry.location} onChange={(event) => change({ location: event.target.value })} placeholder={t('State, district or city', 'राज्य, ज़िला या शहर')} />
        {searchUrl && <a href={searchUrl} target="_blank" rel="noopener noreferrer">{t('Search online', 'ऑनलाइन खोजें')} ↗</a>}
      </div>
      <div className="cma-directory-links"><a href={mapUrl}>{t('Open nearby best supportive care map', 'पास के सर्वोत्तम सहायक देखभाल केंद्र देखें')} ↗</a><a href="https://palliumindia.org/clinics" target="_blank" rel="noopener noreferrer">{t('Pallium India centre list', 'पैलियम इंडिया की केंद्र सूची')} ↗</a><a href="https://www.palliativecare.in/palliative-care-directory-of-india/" target="_blank" rel="noopener noreferrer">{t('Indian Association of Palliative Care directory', 'इंडियन एसोसिएशन ऑफ़ पैलिएटिव केयर सूची')} ↗</a></div>
      <p className="cma-boundary">{t('Call to confirm availability before travelling.', 'जाने से पहले फ़ोन करके उपलब्धता की पुष्टि करें।')}</p>
    </section>

    <section className="cma-questions" aria-labelledby="cma-questions-title">
      <h2 id="cma-questions-title">{t('Questions to ask', 'पूछने के सवाल')}</h2>
      <ul><li>{t('Do you dispense oral morphine?', 'क्या आप ओरल मॉर्फ़ीन देते हैं?')}</li><li>{t('Which prescription documents should I bring?', 'कौन से प्रिस्क्रिप्शन दस्तावेज़ लाने होंगे?')}</li><li>{t('What are your collection hours?', 'दवा लेने का समय क्या है?')}</li></ul>
    </section>

    <section className="cma-followup" aria-labelledby="cma-followup-title">
      <div className="cma-followup-heading"><h2 id="cma-followup-title">{t('My call follow-up', 'मेरी कॉल का फ़ॉलो-अप')}</h2><label><span className="cma-visually-hidden">{t('Follow-up status', 'फ़ॉलो-अप स्थिति')}</span><select value={entry.status} onChange={(event) => updateStatus(event.target.value as MedicineAccessStatus)}><option value="to-call">{t('To call', 'कॉल करनी है')}</option><option value="contacted">{t('Contacted', 'बात हुई')}</option><option value="call-again">{t('Call again', 'फिर कॉल करें')}</option></select></label></div>
      <div className="cma-contact-grid">
        <label>{t('Care team contact', 'देखभाल टीम का संपर्क')}<input value={contactName} onChange={(event) => change({ contactName: event.target.value })} placeholder={t('Name', 'नाम')} /></label>
        <label>{t('Phone number', 'फ़ोन नंबर')}<input type="tel" value={contactPhone} onChange={(event) => change({ contactPhone: event.target.value })} placeholder={t('Add a number', 'नंबर लिखें')} /></label>
      </div>
      <label className="cma-enquiry-field">{t('Enquiry or reply', 'पूछताछ या जवाब')}<textarea value={entry.enquiry} onChange={(event) => change({ enquiry: event.target.value })} rows={3} placeholder={t('Write what you want to ask or what the care team told you', 'क्या पूछना है या देखभाल टीम ने क्या बताया, लिखें')} /></label>
      <div className="cma-actions">
        {contactPhone.trim() && <a href={`tel:${contactPhone.replace(/[^\d+]/g, '')}`} className="cma-call">{t('Call care team', 'देखभाल टीम को कॉल करें')}</a>}
        <button type="button" onClick={copyEnquiry}>{t('Copy enquiry', 'पूछताछ कॉपी करें')}</button>
        {entry.previous && <button type="button" className="cma-undo" onClick={undo}>{t('Undo last change', 'पिछला बदलाव वापस लें')}</button>}
        {copyStatus && <span role="status">{copyStatus}</span>}
      </div>
      {entry.updatedAt && <p className="cma-saved">{t('Updated', 'अपडेट किया गया')} · {new Date(entry.updatedAt).toLocaleString(hindi ? 'hi-IN' : 'en-IN')}</p>}
    </section>
  </main>;
}
