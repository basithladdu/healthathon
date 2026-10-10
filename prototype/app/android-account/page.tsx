import type { Metadata } from 'next';
import { publicAuthConfig } from './account-auth';
import { AndroidAccount } from './android-account';

export const dynamic = 'force-dynamic';

export const metadata: Metadata = {
  title: 'Your Android account · Sahara',
  description: 'Confirm your Sahara email or change your account password.',
  robots: { index: false, follow: false },
  referrer: 'no-referrer',
};

export default function AndroidAccountPage() {
  const config = publicAuthConfig(process.env.SAANTHVANA_SUPABASE_URL, process.env.SAANTHVANA_SUPABASE_ANON_KEY);
  return <AndroidAccount config={config} />;
}
