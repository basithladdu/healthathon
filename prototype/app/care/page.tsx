import Link from 'next/link';
import type { Metadata } from 'next';
import { CareNearMe } from '../care-near-me';
import { SaharaLogo } from '../sahara-logo';

export const metadata: Metadata = { title: 'Find palliative care | Sahara' };

export default function CarePage() {
  return (
    <div className="public-care-page">
      <header className="public-care-header">
        <Link href="/" className="public-care-brand"><SaharaLogo className="public-care-brand-logo" priority /><span>Sahara - A Palliative Care Companion</span></Link>
        <Link href="/">Back to home</Link>
      </header>
      <main>
        <CareNearMe audience="patient" patientName="" />
      </main>
    </div>
  );
}
