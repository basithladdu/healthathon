import Link from 'next/link';
import type { Metadata } from 'next';
import { CareNearMe } from '../care-near-me';

export const metadata: Metadata = { title: 'Find palliative care | Saanthvana' };

export default function CarePage() {
  return (
    <div className="public-care-page">
      <header className="public-care-header">
        <Link href="/" className="public-care-brand">Saanthvana</Link>
        <Link href="/">Back to home</Link>
      </header>
      <main>
        <CareNearMe audience="patient" patientName="" />
      </main>
    </div>
  );
}
