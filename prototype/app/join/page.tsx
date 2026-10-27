import type { Metadata } from 'next';
import Link from 'next/link';
import styles from '../privacy/policy.module.css';

// Fallback for Sahara family invite links (https://sahara.wedevit.in/join?c=<code>&n=<name>).
// Once /.well-known/assetlinks.json is live with the release fingerprint, Android opens the
// app directly and this page is only seen without the app. Nothing here uses or stores the code.
export const metadata: Metadata = {
  title: 'Join care in Sahara',
  description: 'Open a Sahara family invitation in the app.',
  robots: { index: false, follow: false },
  referrer: 'no-referrer',
};

const playUrl = 'https://play.google.com/store/apps/details?id=in.wedevit.sahara';

export default async function JoinPage({ searchParams }: { searchParams: Promise<{ c?: string; n?: string }> }) {
  const { c, n } = await searchParams;
  const code = typeof c === 'string' && /^[a-fA-F0-9]{48}$/.test(c) ? c.toLowerCase() : '';
  const name = typeof n === 'string' ? n.replace(/[<>"&=?#%\u0000-\u001f]/g, '').trim().slice(0, 40) : '';
  const who = name || 'someone';
  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <Link href="/" className={styles.brand}>Sahara</Link>
        </header>
        <h1>You’re invited to see {who}’s care</h1>
        <p lang="hi">आपको {name || 'किसी'} की देखभाल देखने के लिए बुलाया गया है</p>
        {code ? (
          <div className={styles.request}>
            <a className={styles.button} href={`sahara://join?c=${code}${name ? `&n=${encodeURIComponent(name)}` : ''}`}>Open in Sahara · Sahara में खोलें</a>
            <p>If the button doesn’t open the app · अगर ऐप न खुले:</p>
            <ol>
              <li>Install Sahara from <a href={playUrl}>Google Play</a> · Google Play से Sahara इंस्टॉल करें.</li>
              <li>Copy the whole WhatsApp message (or this code) · पूरा WhatsApp संदेश (या यह कोड) कॉपी करें:<br /><code>{code}</code></li>
              <li>In Sahara: More → Your account → “Got an invitation? Paste it here” (or, once signed in, “Join someone’s care” → Paste invite) · Sahara में: और → आपका खाता → “निमंत्रण मिला है? यहाँ चिपकाएँ”.</li>
            </ol>
            <p>The code works once and expires 3 days after it was sent · कोड एक बार चलता है और 3 दिन में बंद हो जाता है.</p>
          </div>
        ) : (
          <p>This invitation link is incomplete. Ask the person who invited you to send a new one · यह लिंक अधूरा है, नया निमंत्रण माँगें.</p>
        )}
      </div>
    </main>
  );
}
