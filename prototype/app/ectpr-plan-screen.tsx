'use client';
// Emergency care plan (ECTPR) screen: wizard, rules V1–V9 / C1–C8, versions and signing. Placeholder until built.
import type { CareLang } from './care-i18n';

export function EctprPlanScreen(props: { patientId: string; patientName: string; role: 'doctor' | 'patient' | 'family'; clinician: string; lang: CareLang }) {
  return <section className="care-page"><h1>Emergency care plan</h1><p>{props.patientName}</p></section>;
}
