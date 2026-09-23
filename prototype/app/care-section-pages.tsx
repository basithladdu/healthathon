'use client';

import { CareArt, type CareArtKind } from './care-art';
import Link from 'next/link';
import { CareRouteLink } from './care-route-link';
import type { CareView } from './care-routes';
import { IconArrowRight } from './icons';
import './care-section-pages.css';

export const CARE_SECTIONS: Array<{ view: CareView; en: string; hi: string; art: CareArtKind; color: string; items: Array<{ view: CareView; en: string; hi: string; art: CareArtKind }> }> = [
  { view: 'clinical-documents', en: 'My documents', hi: 'मेरे दस्तावेज़', art: 'care-story', color: 'peach', items: [
    { view: 'my-plan', en: 'Care note', hi: 'देखभाल का नोट', art: 'care-story' },
    { view: 'reports', en: 'Reports & answers', hi: 'रिपोर्ट और जवाब', art: 'lab-history' },
    { view: 'lab-history', en: 'Test trends', hi: 'जाँच में बदलाव', art: 'lab-history' },
    { view: 'calendar', en: 'Treatment calendar', hi: 'इलाज का कैलेंडर', art: 'cancer-overview' },
    { view: 'next-visit', en: 'Ready for the next visit', hi: 'अगली मुलाकात की तैयारी', art: 'doctor-pack' },
    { view: 'medicines', en: 'My medicines', hi: 'मेरी दवाइयाँ', art: 'cancer-overview' },
    { view: 'care-story', en: 'Care timeline', hi: 'देखभाल का सफ़र', art: 'care-story' },
  ] },
  { view: 'access-care', en: 'Find support', hi: 'मदद ढूँढें', art: 'support-places', color: 'lavender', items: [
    { view: 'care-near-me', en: 'Palliative care centres', hi: 'पैलिएटिव देखभाल केंद्र', art: 'support-places' },
    { view: 'medicine-access', en: 'Find prescribed morphine', hi: 'लिखी हुई मॉर्फ़ीन कहाँ मिलेगी', art: 'cancer-overview' },
    { view: 'home-help', en: 'Help at home', hi: 'घर पर मदद', art: 'home-help' },
    { view: 'care-circle', en: 'My care circle', hi: 'मेरे अपने', art: 'home-help' },
    { view: 'family-tasks', en: 'Who can help?', hi: 'कौन मदद करेगा?', art: 'home-help' },
    { view: 'cost-help', en: 'Costs & support', hi: 'खर्च और सहायता', art: 'cost-help' },
    { view: 'community', en: 'Support groups', hi: 'सहायता समूह', art: 'home-help' },
    { view: 'my-doctors', en: 'My care team', hi: 'मेरी देखभाल टीम', art: 'doctor-pack' },
  ] },
  { view: 'my-space', en: 'My space', hi: 'मेरी जगह', art: 'voice-journal', color: 'mint', items: [
    { view: 'request-conversation', en: 'Talk about what matters', hi: 'ज़रूरी बातों पर बात करें', art: 'care-story' },
    { view: 'symptom-diary', en: 'How I feel', hi: 'मैं कैसा महसूस कर रहा हूँ', art: 'symptom-diary' },
    { view: 'voice-journal', en: 'My journal', hi: 'मेरी डायरी', art: 'voice-journal' },
    { view: 'comfort', en: 'A moment for me', hi: 'मेरे लिए थोड़ा समय', art: 'voice-journal' },
    { view: 'visit-questions', en: 'Questions for my doctor', hi: 'डॉक्टर के लिए सवाल', art: 'open-questions' },
    { view: 'prepare-conversation', en: 'What matters to me', hi: 'मेरे लिए क्या ज़रूरी है', art: 'cancer-overview' },
  ] },
];

export function CareSectionPage({ view, hindi }: { view: CareView; hindi: boolean }) {
  const section = CARE_SECTIONS.find((item) => item.view === view);
  if (!section) return null;
  return <section className={`care-section-page tone-${section.color}`}>
    <header><CareArt kind={section.art} /><h1>{hindi ? section.hi : section.en}</h1>{view === 'clinical-documents' && <Link href="/reports?upload=1" className="primary-button">{hindi ? 'रिपोर्ट अपलोड करें' : 'Upload report'}</Link>}</header>
    <div className="care-section-links">{section.items.map((item) => <CareRouteLink key={item.view} view={item.view}>
      <CareArt kind={item.art} /><strong>{hindi ? item.hi : item.en}</strong><IconArrowRight />
    </CareRouteLink>)}</div>
  </section>;
}

export function careSectionFor(view: string): string {
  return CARE_SECTIONS.find((section) => section.view === view || section.items.some((item) => item.view === view))?.view ?? view;
}
