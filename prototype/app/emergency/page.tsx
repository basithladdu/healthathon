import type { Metadata } from 'next';
import { EmergencyView } from './emergency-view';

export const metadata: Metadata = {
  title: 'Pocket emergency card · Sahara',
  description: 'Read-only emergency card shared by a Sahara family.',
  robots: { index: false, follow: false },
  referrer: 'no-referrer',
};

export default function EmergencyPage() {
  return <EmergencyView />;
}
