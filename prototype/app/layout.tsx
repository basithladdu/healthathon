import type { Metadata } from 'next';
import { Inter, Sora } from 'next/font/google';
import './globals.css';
import './auth.css';
import './landing.css';
import './patient-profile.css';
import './appointments.css';
import './todays-handoffs.css';
import 'leaflet/dist/leaflet.css';
import './care-near-me.css';
import './ectpr.css';
import './patient-portal.css';
import './patient-history.css';
import './care-workspace.css';
import './continuity-ops-hub.css';

const inter = Inter({
  variable: '--font-inter',
  subsets: ['latin'],
  weight: ['400', '500', '600', '700'],
  display: 'swap',
});

const sora = Sora({
  variable: '--font-sora',
  subsets: ['latin'],
  weight: ['600', '700', '800'],
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'Continuity Loop | Verified Clinical Handoff',
  description:
    'A clinician-led workflow for guided goals-of-care documentation, verification and emergency handoff retrieval.',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} ${sora.variable}`}>
      <body className="antialiased">
        {children}
      </body>
    </html>
  );
}
