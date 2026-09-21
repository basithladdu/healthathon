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
import './care-directory.css';
import './family-care.css';
import './ectpr.css';
import './patient-portal.css';
import './patient-history.css';
import './care-workspace.css';
import './continuity-ops-hub.css';
import './simple-care.css';
import './care-sign-in.css';
import './care-calendar.css';
import './family-report-comparison.css';
import './family-care-tools.css';
import './family-symptom-diary.css';
import './family-care-story.css';
import './family-handover-pack.css';
import './family-home-help.css';
import './family-support-places.css';
import './family-note-history.css';
import './family-open-questions.css';
import './family-copy-tracker.css';
import './family-comfort-space.css';
import './family-voice-journal.css';
import './family-cost-help.css';
import './doctor-care-home.css';
import './care-colors.css';

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
  title: 'Saanthvana | Family support for cancer care',
  description:
    'Coordinate family tasks and visits, prepare for goals-of-care conversations, and find palliative support with Saanthvana.',
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
