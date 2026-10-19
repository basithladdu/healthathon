import type { Metadata } from 'next';
import Link from 'next/link';
import { permanentRedirect } from 'next/navigation';
import FeedbackForm from './FeedbackForm';
import styles from './feedback.module.css';

export const metadata: Metadata = { title: 'App feedback · Sahara', description: 'Share your experience of using Sahara.', alternates: { canonical: 'https://sahara.wedevit.in/feedback' } };

export default async function FeedbackPage({ searchParams }: { searchParams: Promise<{ [key: string]: string | string[] | undefined }> }) {
  const parameters = await searchParams;
  if (parameters.form === 'hospital') permanentRedirect('/hospital-survery');
  return <div className={styles.page}>
    <header className={styles.header}><Link href="/" className={styles.brand}>Sahara<span>A Palliative Care Companion</span></Link><Link href="/" className={styles.back}>Back to Sahara</Link></header>
    <main className={styles.main}><FeedbackForm /></main>
  </div>;
}
