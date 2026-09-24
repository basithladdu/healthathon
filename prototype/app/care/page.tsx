import Link from 'next/link';
import type { Metadata } from 'next';
import { CareNearMe } from '../care-near-me';

export const metadata: Metadata = { title: 'Find best supportive care | Saathi' };

export default function CarePage() {
  return (
    <div className="public-care-page">
      <header className="public-care-header">
        <Link href="/" className="public-care-brand">Saathi</Link>
        <Link href="/">Back to home</Link>
      </header>
      <main>
        <CareNearMe audience="patient" patientName="" />
      </main>
    </div>
  );
}
