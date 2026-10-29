import type { Metadata } from 'next';
import Link from 'next/link';
import styles from '../feedback/feedback.module.css';
import admin from './admin.module.css';
import { SurveyAdmin } from './survey-admin';

export const metadata: Metadata = {
  title: 'Survey admin · Sahara',
  robots: { index: false, follow: false },
  referrer: 'no-referrer',
};

export default function AdminPage() {
  return <div className={`${styles.page} ${admin.page}`}>
    <header className={`${styles.header} ${admin.header}`}><Link href="/" className={styles.brand}>Sahara<span>A Palliative Care Companion</span></Link><Link href="/" className={styles.back}>Back to Sahara</Link></header>
    <main className={admin.main}><SurveyAdmin /></main>
  </div>;
}
