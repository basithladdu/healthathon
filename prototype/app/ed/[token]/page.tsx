import type { Metadata } from 'next';
import { EdCardPage } from './ed-card-page';

// Public emergency page opened from the wallet card's QR code. No sign-in: the random token in the URL is the
// only key, and it holds no personal data. No record is rendered on the server: the client asks /api/ed-card
// (which logs the QR_VIEW), and falls back to this device's cached copy or the card's offline QR code.
export const metadata: Metadata = {
  title: 'Emergency care plan · Sahara',
  description: 'Emergency care plan opened from a Sahara wallet card.',
  robots: { index: false, follow: false },
  referrer: 'no-referrer',
};

export default async function EdTokenPage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <EdCardPage token={token} />;
}
