'use client';

import { useId, useState } from 'react';
import { IconArrowRight, IconMapPin, IconUsers } from './icons';
import './care-community.css';

export type CareCommunityProps = { hindi: boolean };

const conditions = [
  ['all', 'All groups', 'सभी समूह'],
  ['breast', 'Breast cancer', 'स्तन कैंसर'],
  ['cml', 'CML (chronic myeloid leukaemia)', 'CML (क्रॉनिक मायलॉइड ल्यूकीमिया)'],
  ['gist', 'GIST (gastrointestinal stromal tumour)', 'GIST (गैस्ट्रोइंटेस्टाइनल स्ट्रोमल ट्यूमर)'],
  ['childhood', 'After childhood cancer', 'बचपन के कैंसर के बाद'],
  ['other', 'Another condition / not sure', 'दूसरी बीमारी / पता नहीं'],
] as const;
type Condition = (typeof conditions)[number][0];

// Public descriptions checked against each organisation's own website on 2026-09-21.
// Fixed links carry no patient, condition, diary or journal data.
const groups: {
  id: string; name: string; hindiName: string; conditions: Condition[]; general?: boolean;
  audience: string; hindiAudience: string; location: string; hindiLocation: string; url: string; tone: string;
}[] = [
  {
    id: 'hitaishini', name: 'Hitaishini', hindiName: 'हितैषिणी', conditions: ['breast'],
    audience: 'Breast cancer support network', hindiAudience: 'स्तन कैंसर सहायता समूह',
    location: 'Kolkata', hindiLocation: 'कोलकाता', url: 'https://hitaishini.org/', tone: 'peach',
  },
  {
    id: 'friends-of-max', name: 'Friends of Max', hindiName: 'फ्रेंड्स ऑफ़ मैक्स', conditions: ['cml', 'gist'],
    audience: 'CML and GIST patients and caregivers', hindiAudience: 'CML और GIST के मरीज़ और देखभाल करने वाले',
    location: 'Chapters across India', hindiLocation: 'भारत के कई शहरों में', url: 'https://friendsofmax.info/', tone: 'lavender',
  },
  {
    id: 'ugam', name: 'Ugam · Indian Cancer Society', hindiName: 'उगम · इंडियन कैंसर सोसाइटी', conditions: ['childhood'],
    audience: 'Support group for childhood cancer survivors', hindiAudience: 'बचपन के कैंसर के बाद जीवन में सहायता समूह',
    location: 'Mumbai', hindiLocation: 'मुंबई', url: 'https://www.indiancancersociety.org/what-do-we-do/ugam', tone: 'honey',
  },
  {
    id: 'v-care', name: 'V Care Foundation', hindiName: 'वी केयर फ़ाउंडेशन', conditions: [], general: true,
    audience: 'Support for people with cancer and their families', hindiAudience: 'कैंसर के मरीज़ों और उनके परिवारों के लिए सहायता',
    location: 'Mumbai', hindiLocation: 'मुंबई', url: 'https://vcarecancer.org/', tone: 'sage',
  },
];

export function CareCommunity({ hindi }: CareCommunityProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [condition, setCondition] = useState<Condition>('all');
  const visible = groups.filter((group) => condition === 'all' || group.general || group.conditions.includes(condition));

  return <section className="care-community" aria-labelledby={`${id}-title`}>
    <header className="care-community-heading"><h1 id={`${id}-title`}>{t('Find your people', 'अपना साथ ढूँढें')}</h1></header>
    <div className="care-community-filter">
      <label htmlFor={`${id}-condition`}>{t('Find support for', 'किसके लिए सहायता चाहिए?')}</label>
      <select id={`${id}-condition`} value={condition} onChange={(event) => setCondition(event.target.value as Condition)}>
        {conditions.map(([value, en, hi]) => <option key={value} value={value}>{t(en, hi)}</option>)}
      </select>
    </div>
    <div className="care-community-groups" aria-live="polite">
      {visible.map((group) => <article key={group.id} className={`care-community-card care-community-${group.tone}`}>
        <div className="care-community-card-top"><span className="care-community-icon" aria-hidden="true"><IconUsers /></span><p>{t(group.location, group.hindiLocation)}<span aria-hidden="true"><IconMapPin /></span></p></div>
        <h2>{t(group.name, group.hindiName)}</h2>
        <p className="care-community-audience">{t(group.audience, group.hindiAudience)}</p>
        <a href={group.url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer" aria-label={`${t('Open group website', 'समूह की वेबसाइट खोलें')}: ${t(group.name, group.hindiName)}`}>
          <span>{t('Open group website', 'समूह की वेबसाइट खोलें')}</span><span aria-hidden="true"><IconArrowRight /></span>
        </a>
      </article>)}
    </div>
    <footer className="care-community-footer">
      <p>{t('These groups open on their own websites. Your journal and care records are not shared.', 'ये समूह अपनी वेबसाइट पर खुलते हैं। आपकी डायरी और देखभाल के रिकॉर्ड साझा नहीं होते।')}</p>
      <span>{t('Sources checked', 'स्रोत जाँचे गए')} <time dateTime="2026-09-21">{t('21 Sep 2026', '21 सितंबर 2026')}</time></span>
    </footer>
  </section>;
}
