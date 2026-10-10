import type { Metadata } from 'next';
import Link from 'next/link';
import styles from './policy.module.css';

export const metadata: Metadata = {
  title: 'Privacy policy · Sahara Android',
  description: 'How the Sahara Android app handles care information, recordings, sharing and account deletion.',
  alternates: { canonical: 'https://sahara.wedevit.in/privacy' },
};

export default function PrivacyPage() {
  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <Link href="/" className={styles.brand}>Sahara</Link>
          <Link href="/delete-account">Delete your account</Link>
        </header>
        <h1>Your care information</h1>
        <p className={styles.date}>Privacy policy · Updated 10 October 2026</p>
        <p>
          Sahara — A Palliative Care Companion is provided by WEDEVIT PRIVATE LIMITED.
          This policy covers the Sahara Android app, including version 0.3.0, rather
          than the separate website.
        </p>

        <section aria-labelledby="information">
          <h2 id="information">What we use and why</h2>
          <p>
            The app keeps the names, care conversations, reviewed notes, confirmations,
            prescribed-medicine details, visits, tasks, check-ins and journal entries
            you enter. We use them to show your care records, organise daily care and,
            when you sign in, share records with people you invite.
          </p>
          <p>
            Without signing in, these records stay in the app’s private storage on your
            phone. If you sign in, names, published care notes, confirmations and daily
            entries you add in that account are also stored with Supabase for syncing
            and sharing. Existing unsigned-in records are not automatically uploaded.
          </p>
          <p>
            Supabase Auth processes your account email and password for sign-in. The app
            stores an encrypted sign-in token, not your password. Our service providers
            also receive network information, such as your IP address, when your phone
            connects to them. Their service logs can include your IP address, an
            approximate city or country inferred from it, and request diagnostics
            used to keep the service working and secure. This is not GPS tracking.
            If you email support, we use your email and message to
            handle your request.
          </p>
        </section>

        <section aria-labelledby="recordings">
          <h2 id="recordings">Microphone and recordings</h2>
          <p>
            Microphone permission is requested when you choose to record. Ask everyone
            taking part for permission first. You can stop at any time; recording also
            stops when the app leaves the foreground. Audio and unfinished conversation
            drafts stay on your phone. This Android version does not upload audio or
            send your care information to an AI service, and it does not transcribe
            recordings automatically.
          </p>
        </section>

        <section aria-labelledby="sharing">
          <h2 id="sharing">Who can see your records</h2>
          <p>
            Signed-in record owners can create invitations with read or edit permission.
            Each invitation can be used once and expires after three days. The owner can
            see an invited member’s email and remove their permission. Removing permission
            stops future online requests; it cannot recall copies already saved.
          </p>
          <p>
            When you share or export care notes, Android lets you choose another app and
            recipient. Those copies leave Sahara and follow that service’s policies.
            Check the contents and recipient before sharing. Names and doctor roles are
            self-entered; Sahara does not verify professional credentials.
          </p>
        </section>

        <section aria-labelledby="maps">
          <h2 id="maps">Maps and links</h2>
          <p>
            OpenFreeMap supplies map tiles through MapLibre and receives your IP address
            and the map area requested. Sahara does not request your precise GPS location.
            Calls, directions and source links open your dialler, maps or browser when
            you select them. Those services handle information under their own policies.
          </p>
        </section>

        <section aria-labelledby="security">
          <h2 id="security">How information is protected</h2>
          <p>
            Sahara has no advertising or analytics SDK. We do not sell care information
            or use it for advertising. Connections to Supabase and OpenFreeMap use HTTPS.
            Sign-in tokens are encrypted using Android Keystore. Account caches are
            separated from each other and from unsigned-in records, and app records are
            excluded from Android backup and transfer.
          </p>
          <p>
            Protect your phone with a screen lock. The app’s records rely on Android’s
            storage protections; its database does not have separate app-level encryption.
            Supabase processes account and shared care information to provide the service.
          </p>
        </section>

        <section aria-labelledby="deletion">
          <h2 id="deletion">Keeping and deleting information</h2>
          <p>
            Unsigned-in records remain until you remove them, clear app data or uninstall.
            Signed-in records remain online after uninstalling. Record owners can choose
            More → Remove this person’s records to delete those records online and clear
            the corresponding app cache. You can also delete individual recordings.
          </p>
          <p>
            To delete your Sahara account and associated information, use Your account →
            Delete account in the app, or <Link href="/delete-account">request deletion
            here</Link>. We verify control of the registered email before processing,
            explain the expected completion date and confirm by email when complete.
            Opening the page or an email draft does not delete anything.
          </p>
          <p>
            Deletion removes your Sahara account details, care records you own, invitations
            for those records and your membership in other people’s care records. Records
            owned by someone else are not removed by closing your account. Exported files
            and copies saved by recipients must be removed separately. If the same sign-in
            is used for another service, we handle Sahara data separately and explain any
            shared account details before completing the request.
          </p>
          <p>
            If another service still needs your sign-in, we keep only its account ID and
            the Sahara closure date to keep Sahara access closed. This marker is removed
            when that shared sign-in is deleted.
          </p>
          <p>
            We keep information while it is needed to provide your account and care records.
            We keep support correspondence while handling your request. If any information
            must be retained for a legal or security reason, we will tell you what is kept,
            why and for how long when handling your deletion request.
          </p>
        </section>

        <section aria-labelledby="care">
          <h2 id="care">About care decisions</h2>
          <p>
            Sahara helps record and organise care. It is not a medical device and does
            not diagnose, prescribe or replace advice from your care team. Saved goals-of-care
            notes record a conversation; they are not treatment orders or legal directives.
          </p>
        </section>

        <section aria-labelledby="contact">
          <h2 id="contact">Questions or changes</h2>
          <p>
            Contact WEDEVIT PRIVATE LIMITED at{' '}
            <a href="mailto:workwithdevit@gmail.com">workwithdevit@gmail.com</a> for privacy
            questions, corrections or deletion requests. We will update this page when
            data handling changes. Any future audio upload or AI processing will require
            an updated explanation and consent before that information is sent.
          </p>
        </section>
        <footer className={styles.footer}>
          <Link href="/delete-account">Delete your account</Link>
          <Link href="/">Back to Sahara</Link>
        </footer>
      </div>
    </main>
  );
}
