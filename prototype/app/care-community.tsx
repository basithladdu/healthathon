'use client';

import { useId } from 'react';
import { useCareLocalState } from './care-local-store';
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

const audiences = [
  ['patient', 'Patient', 'मरीज़'],
  ['caregiver', 'Caregiver', 'देखभाल करने वाले'],
  ['advanced', 'Advanced cancer', 'बढ़े हुए कैंसर में'],
  ['after', 'After treatment', 'इलाज के बाद'],
] as const;
const regions = [['india', 'India', 'भारत'], ['online', 'Online', 'ऑनलाइन']] as const;
type Audience = (typeof audiences)[number][0];
type Region = (typeof regions)[number][0];
type Preferences = { audience: Audience; region: Region; condition: Condition };
const initialPreferences: Preferences = { audience: 'patient', region: 'india', condition: 'all' };

// Source and audience checks: docs/research/2026-09-23-india-caregiver-experiences.md.
// These fixed links carry no patient, filter, diary or journal data.
const groups: {
  id: string; name: string; hindiName: string; conditions: Condition[]; general?: boolean;
  audiences: Audience[]; regions: Region[]; community?: boolean; provider?: string;
  audience: string; hindiAudience: string; location: string; hindiLocation: string; url: string; tone: string;
}[] = [
  {
    id: 'hitaishini', name: 'Hitaishini', hindiName: 'हितैषिणी', conditions: ['breast'],
    audiences: ['patient', 'caregiver', 'advanced', 'after'], regions: ['india'],
    audience: 'Breast cancer support network', hindiAudience: 'स्तन कैंसर सहायता समूह',
    location: 'Kolkata', hindiLocation: 'कोलकाता', url: 'https://hitaishini.org/', tone: 'peach',
  },
  {
    id: 'friends-of-max', name: 'Friends of Max', hindiName: 'फ्रेंड्स ऑफ़ मैक्स', conditions: ['cml', 'gist'],
    audiences: ['patient', 'caregiver', 'advanced'], regions: ['india'],
    audience: 'CML and GIST patients and caregivers', hindiAudience: 'CML और GIST के मरीज़ और देखभाल करने वाले',
    location: 'Chapters across India', hindiLocation: 'भारत के कई शहरों में', url: 'https://friendsofmax.info/', tone: 'lavender',
  },
  {
    id: 'ugam', name: 'Ugam · Indian Cancer Society', hindiName: 'उगम · इंडियन कैंसर सोसाइटी', conditions: ['childhood'],
    audiences: ['after'], regions: ['india'],
    audience: 'Support group for childhood cancer survivors', hindiAudience: 'बचपन के कैंसर के बाद जीवन में सहायता समूह',
    location: 'Mumbai', hindiLocation: 'मुंबई', url: 'https://www.indiancancersociety.org/what-do-we-do/ugam', tone: 'honey',
  },
  {
    id: 'v-care', name: 'V Care Foundation', hindiName: 'वी केयर फ़ाउंडेशन', conditions: [], general: true,
    audiences: ['patient', 'caregiver', 'advanced', 'after'], regions: ['india'],
    audience: 'Support for people with cancer and their families', hindiAudience: 'कैंसर के मरीज़ों और उनके परिवारों के लिए सहायता',
    location: 'Mumbai', hindiLocation: 'मुंबई', url: 'https://vcarecancer.org/', tone: 'sage',
  },
  {
    id: 'survivors-india', name: 'Cancer Survivors India', hindiName: 'कैंसर सर्वाइवर्स इंडिया', conditions: [], general: true,
    audiences: ['patient', 'advanced', 'after'], regions: ['india', 'online'], community: true, provider: 'Reddit',
    audience: 'People in India, during and after treatment.', hindiAudience: 'भारत में इलाज के दौरान और उसके बाद का साथ।',
    location: 'India · Online', hindiLocation: 'भारत · ऑनलाइन', url: 'https://www.reddit.com/r/CancerSurvivorsIndia/', tone: 'honey',
  },
  {
    id: 'cancer-caregivers', name: 'Cancer caregivers', hindiName: 'देखभाल करने वालों का साथ', conditions: [], general: true,
    audiences: ['caregiver'], regions: ['online'], community: true, provider: 'Reddit',
    audience: 'A place to read and hear from other caregivers.', hindiAudience: 'दूसरे देखभाल करने वालों के अनुभव पढ़ें।',
    location: 'Online', hindiLocation: 'ऑनलाइन', url: 'https://www.reddit.com/r/CancerCaregivers/', tone: 'peach',
  },
  {
    id: 'cancer-conversations', name: 'Cancer conversations', hindiName: 'कैंसर पर बातचीत', conditions: [], general: true,
    audiences: ['patient', 'advanced', 'after'], regions: ['online'], community: true, provider: 'Reddit',
    audience: 'Everyday experiences from people living with cancer.', hindiAudience: 'कैंसर के साथ जी रहे लोगों के रोज़मर्रा के अनुभव।',
    location: 'Online', hindiLocation: 'ऑनलाइन', url: 'https://www.reddit.com/r/cancer/', tone: 'sage',
  },
  {
    id: 'macmillan-incurable', name: 'Living with incurable cancer', hindiName: 'लाइलाज कैंसर के साथ जीवन', conditions: [], general: true,
    audiences: ['advanced'], regions: ['online'], community: true, provider: 'Macmillan',
    audience: 'For patients with an incurable or terminal diagnosis.', hindiAudience: 'जिन मरीज़ों को डॉक्टर ने लाइलाज या अंतिम अवस्था का कैंसर बताया है।',
    location: 'UK · Online', hindiLocation: 'यूके · ऑनलाइन', url: 'https://community.macmillan.org.uk/cancer_experiences/living-with-incurable-cancer-forum', tone: 'lavender',
  },
  {
    id: 'macmillan-carers', name: 'Carers’ space', hindiName: 'देखभाल करने वालों की जगह', conditions: [], general: true,
    audiences: ['caregiver'], regions: ['online'], community: true, provider: 'Macmillan',
    audience: 'A separate space for family and caregivers.', hindiAudience: 'परिवार और देखभाल करने वालों के लिए अलग जगह।',
    location: 'UK · Online', hindiLocation: 'यूके · ऑनलाइन', url: 'https://community.macmillan.org.uk/cancer_experiences/carers-only-forum', tone: 'lavender',
  },
  {
    id: 'macmillan-after', name: 'Life after treatment', hindiName: 'इलाज के बाद की ज़िंदगी', conditions: [], general: true,
    audiences: ['after'], regions: ['online'], community: true, provider: 'Macmillan',
    audience: 'Life, work and relationships after treatment.', hindiAudience: 'इलाज के बाद की ज़िंदगी, काम और रिश्ते।',
    location: 'UK · Online', hindiLocation: 'यूके · ऑनलाइन', url: 'https://community.macmillan.org.uk/cancer_experiences/life-after-cancer-forum', tone: 'peach',
  },
];

export function CareCommunity({ hindi }: CareCommunityProps) {
  const id = useId();
  const t = (en: string, hi: string) => hindi ? hi : en;
  const [preferences, setPreferences, saveStatus] = useCareLocalState<Preferences>('community-preferences-v1', initialPreferences);
  const { audience, region, condition } = preferences;
  const visible = groups.filter((group) => group.audiences.includes(audience) && group.regions.includes(region)
    && (condition === 'all' || group.general || group.conditions.includes(condition)));

  return <section className="care-community" aria-labelledby={`${id}-title`}>
    <header className="care-community-heading"><h1 id={`${id}-title`}>{t('Find your people', 'अपना साथ ढूँढें')}</h1></header>
    <fieldset className="care-community-choices care-community-audiences" disabled={saveStatus === 'loading'}>
      <legend>{t('Support for', 'किसके लिए साथ चाहिए?')}</legend>
      <div>{audiences.map(([value, en, hi]) => <label key={value}>
        <input type="radio" name={`${id}-audience`} value={value} checked={audience === value} onChange={() => setPreferences((current) => ({ ...current, audience: value }))} />
        <span>{t(en, hi)}</span>
      </label>)}</div>
    </fieldset>
    <div className="care-community-filters">
      <fieldset className="care-community-choices care-community-regions" disabled={saveStatus === 'loading'}>
        <legend>{t('Where', 'कहाँ')}</legend>
        <div>{regions.map(([value, en, hi]) => <label key={value}>
          <input type="radio" name={`${id}-region`} value={value} checked={region === value} onChange={() => setPreferences((current) => ({ ...current, region: value }))} />
          <span>{t(en, hi)}</span>
        </label>)}</div>
      </fieldset>
      <div className="care-community-filter">
        <label htmlFor={`${id}-condition`}>{t('Cancer type', 'कैंसर का प्रकार')}</label>
        <select id={`${id}-condition`} value={condition} disabled={saveStatus === 'loading'} onChange={(event) => setPreferences((current) => ({ ...current, condition: event.target.value as Condition }))}>
          {conditions.map(([value, en, hi]) => <option key={value} value={value}>{t(en, hi)}</option>)}
        </select>
      </div>
    </div>
    <p className="care-community-count" role="status">{visible.length} {t(visible.length === 1 ? 'place to connect' : 'places to connect', 'जगह जहाँ आप जुड़ सकते हैं')}</p>
    <div className="care-community-groups">
      {visible.map((group) => <article key={group.id} className={`care-community-card care-community-${group.tone}`}>
        <div className="care-community-card-top"><span className="care-community-icon" aria-hidden="true"><IconUsers /></span><p>{t(group.location, group.hindiLocation)}<span aria-hidden="true"><IconMapPin /></span></p></div>
        <h2>{t(group.name, group.hindiName)}</h2>
        <p className="care-community-audience">{t(group.audience, group.hindiAudience)}</p>
        <a href={group.url} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer" aria-label={`${t(group.community ? 'Read discussions' : 'Visit website', group.community ? 'बातचीत पढ़ें' : 'वेबसाइट देखें')}: ${t(group.name, group.hindiName)} (${t('opens in a new tab', 'नए टैब में खुलेगा')})`}>
          <span>{group.community ? t('Read on', 'पढ़ें ·') + ' ' + group.provider : t('Visit website', 'वेबसाइट देखें')}</span><span aria-hidden="true"><IconArrowRight /></span>
        </a>
      </article>)}
    </div>
    {visible.length === 0 && <div className="care-community-empty">
      <p>{t('No groups match these choices yet.', 'इन विकल्पों के लिए अभी कोई समूह नहीं है।')}</p>
      <button type="button" onClick={() => setPreferences(initialPreferences)}>{t('See more groups', 'और समूह देखें')}</button>
    </div>}
  </section>;
}
