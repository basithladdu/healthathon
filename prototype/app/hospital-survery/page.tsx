import type { Metadata } from 'next';
import Link from 'next/link';
import HospitalSurvey from '../feedback/HospitalSurvey';
import styles from '../feedback/feedback.module.css';

export const metadata: Metadata = {
  title: 'Hospital survey · Sahara',
  description: 'Share palliative-care service information to help families find care.',
  alternates: { canonical: 'https://sahara.wedevit.in/hospital-survery' },
};

export default function HospitalSurveyPage() {
  return <div className={styles.page}>
    <header className={styles.header}><Link href="/" className={styles.brand}>Sahara<span>A Palliative Care Companion</span></Link><Link href="/" className={styles.back}>Back to Sahara</Link></header>
    <main className={styles.main}><HospitalSurvey /></main>
  </div>;
}
