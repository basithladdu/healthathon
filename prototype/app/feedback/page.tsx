import type { Metadata } from 'next';
import Link from 'next/link';
import FeedbackForm from './FeedbackForm';
import styles from './feedback.module.css';

export const metadata: Metadata = { title: 'Feedback and surveys · Sahara', description: 'Share your Sahara app experience or hospital-service information.' };

export default async function FeedbackPage({ searchParams }: { searchParams: Promise<{ [key: string]: string | string[] | undefined }> }) {
  const parameters = await searchParams;
  const initialMode = parameters.form === 'hospital' ? 'hospital' : 'app';
  return <div className={styles.page}>
    <header className={styles.header}><Link href="/" className={styles.brand}>Sahara<span>A Palliative Care Companion</span></Link><Link href="/" className={styles.back}>Back to Sahara</Link></header>
    <main className={styles.main}><FeedbackForm initialMode={initialMode} /></main>
  </div>;
}
