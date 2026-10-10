import type { Metadata } from 'next';
import Link from 'next/link';
import styles from '../privacy/policy.module.css';

export const metadata: Metadata = {
  title: 'Delete your account · Sahara',
  description: 'Request deletion of your Sahara account and associated care information without reinstalling the app.',
  alternates: { canonical: 'https://sahara.wedevit.in/delete-account' },
};

const deletionEmail = 'mailto:workwithdevit@gmail.com?subject=Delete%20my%20Sahara%20account&body=Please%20delete%20my%20Sahara%20account%20and%20the%20associated%20care%20information.%0A%0AI%20am%20sending%20this%20from%20my%20registered%20email%20address.';

export default function DeleteAccountPage() {
  return (
    <main className={styles.page}>
      <div className={styles.content}>
        <header className={styles.header}>
          <Link href="/" className={styles.brand}>Sahara</Link>
          <Link href="/privacy">Privacy policy</Link>
        </header>
        <h1>Delete your Sahara account</h1>
        <p>Sahara — A Palliative Care Companion · WEDEVIT PRIVATE LIMITED</p>
        <p>
          Send a request from the email you used to sign in. You do not need to reinstall
          the app. This requests deletion of your account and associated care information.
        </p>

        <div className={styles.request}>
          <a className={styles.button} href={deletionEmail}>Email a deletion request</a>
          <p>
            Or email <a href="mailto:workwithdevit@gmail.com">workwithdevit@gmail.com</a>{' '}
            with the subject <strong>Delete my Sahara account</strong>.
          </p>
          <p>
            Please do not send your password, medical documents or recordings. If you
            cannot use your registered email, contact us for help verifying the account.
          </p>
        </div>

        <section aria-labelledby="next">
          <h2 id="next">What happens next</h2>
          <p>
            We verify that you control the account, explain the expected completion date
            and email you when deletion is complete. Opening an email draft does not
            send the request or delete information. You still need to send the email.
          </p>
        </section>

        <section aria-labelledby="removed">
          <h2 id="removed">What will be removed</h2>
          <ul>
            <li>Your Sahara account details and care records you own, including saved care notes, confirmations and daily entries.</li>
            <li>Invitations and shared access to the care records you own.</li>
            <li>Your membership in care records owned by someone else.</li>
          </ul>
          <p>
            If your sign-in is also used for another service, we handle Sahara data
            separately and explain any shared account details before completing the request.
          </p>
          <p>
            If another service still needs your sign-in, we keep only its account ID and
            the Sahara closure date to keep Sahara access closed. This marker is removed
            when that shared sign-in is deleted.
          </p>
        </section>

        <section aria-labelledby="copies">
          <h2 id="copies">Records and copies to check</h2>
          <p>
            Care records owned by someone else remain with their owner. Deleting your
            account cannot recall files you exported or copies that recipients saved.
            Ask recipients to remove those copies if needed.
          </p>
          <p>
            Audio, unfinished drafts and unsigned-in records stay on your phone. Remove
            them in the app, clear Sahara’s app data in Android Settings, or uninstall
            after exporting anything you need. Uninstalling alone does not delete your
            online account.
          </p>
          <p>
            We keep support correspondence while handling the request. If any information
            must be retained for a legal or security reason, we will explain what, why
            and for how long in our response.
          </p>
        </section>

        <section aria-labelledby="specific">
          <h2 id="specific">Only removing one person’s records?</h2>
          <p>
            If you own those records, choose the person in Sahara, then More → Remove
            this person’s records. This removes their care notes, recordings and daily
            entries without closing your account. You can also email us to request
            deletion of specific information.
          </p>
        </section>
        <footer className={styles.footer}>
          <Link href="/privacy">Privacy policy</Link>
          <Link href="/">Back to Sahara</Link>
        </footer>
      </div>
    </main>
  );
}
