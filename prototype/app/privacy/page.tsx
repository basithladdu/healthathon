import type { Metadata } from 'next';
import Link from 'next/link';
import styles from './policy.module.css';

export const metadata: Metadata = {
  title: 'Privacy policy · Sahara',
  description: 'How Sahara handles care information, recordings, voluntary feedback, hospital surveys and deletion.',
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
        <p className={styles.date}>Privacy policy · Updated 19 October 2026</p>
        <p>
          Sahara — A Palliative Care Companion is provided by WEDEVIT PRIVATE LIMITED.
          This policy covers the Sahara Android app and the voluntary feedback and
          hospital-survey forms on this website. The website’s separate care workspace
          is not a shared copy of your Android records.
        </p>

        <section aria-labelledby="information">
          <h2 id="information">What we use and why</h2>
          <p>
            The app keeps the names, care conversations, reviewed notes, confirmations,
            prescribed-medicine details, visits, tasks, lab results, conversation requests,
            check-ins and journal entries
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
            drafts stay on your phone. The Android app does not upload audio and
            does not transcribe recordings automatically.
          </p>
          <p>
            Version 0.5 adds optional turn-by-turn dictation using Android’s on-device
            recognizer on supported phones. You choose the speaker and language and check
            the words; speaker identity is not detected or verified. There is no cloud
            speech fallback. Recognised words remain in your draft until you save a
            reviewed care-note version. Read aloud uses an installed offline voice and
            does not translate the text.
          </p>
          <p>
            The fictional doctor preview keeps its sample records, edited drafts,
            preview recordings and app feedback on this phone, separately from Sahara
            accounts. Changing or deleting a Sahara account does not clear this preview.
            Only the newest preview recording is shown; older recordings are not listed,
            and preview recordings cannot currently be deleted individually in the app.
            Clear Sahara app data in Android settings or uninstall the app to remove the
            local preview data. This also clears other Sahara information stored on that
            phone. Preview PDFs are created in the app cache, which Android may clear.
            Copies you export or share remain with their recipients until they remove them.
          </p>
        </section>

        <section aria-labelledby="feedback">
          <h2 id="feedback">Feedback and hospital surveys</h2>
          <p>
            You can share the <Link href="/feedback">app feedback form</Link> without
            signing in. If you choose to send it, Sahara stores your rating,
            self-described role, topic, optional comments, consent and submission receipt
            in its Supabase database to improve the app. Please do not include patient
            names, phone numbers, ABHA numbers or medical details.
          </p>
          <p>
            The voluntary <Link href="/feedback?form=hospital">hospital survey</Link>
            collects your professional name, role, registering council, registration
            number and state, plus optional professional mobile and email. These
            professional and contact details are stored separately from hospital-service
            information and are not for public display. Optional contacts are used to
            follow up about your submission, not for marketing. We do not verify your
            identity, registration, contact ownership or hospital affiliation through
            this form, and submitting does not send an OTP, email or SMS.
          </p>
          <p>
            Hospital-service information includes the hospital name, institution type,
            your connection to it, state, city, selected hospital map pin, palliative-care
            services, morphine availability and any optional note for families. The pin
            is a place you select, not your current GPS location. Submissions remain
            pending review; they do not automatically create a public listing or a
            verified professional profile. Reviewed hospital-service information may be
            used in the care directory. Do not enter patient or family contact details.
          </p>
          <p>
            On the website, place search sends your query and IP address to Photon.
            Opening the hospital map sends the viewed area and IP address to
            OpenStreetMap. The website uses a strictly necessary, HttpOnly feedback
            cookie lasting 24 hours to limit repeated submissions. Only a keyed hash
            of that random token is stored with the receipt, not the raw token or IP
            address. Hosting and Supabase service logs may contain network diagnostics.
          </p>
          <p>
            Only restricted service administration can read feedback and contributor
            records; there is no public submission list. We retain them for app
            improvement and hospital-directory review. There is currently no automatic
            expiry or self-service deletion for these voluntary submissions. For a
            correction or deletion, email{' '}
            <a href="mailto:workwithdevit@gmail.com">workwithdevit@gmail.com</a> with
            your receipt if available, or enough details to locate the submission.
            We verify the request before changing or deleting it. Closing a Sahara
            account does not automatically remove a separate, unsigned feedback or
            survey submission. Separate on-phone feedback is not sent by these
            website forms.
          </p>
        </section>

        <section aria-labelledby="medicine-ai">
          <h2 id="medicine-ai">Prescription and medicine photo organiser</h2>
          <p>
            Photo scanning is currently off. Selected prescription and medicine photos
            are not uploaded. You can add medicines by hand.
          </p>
          <p>
            When scanning is enabled, the prescription pages and medicine-strip
            or box photos you select are compressed on your phone and sent over HTTPS
            through Sahara’s protected service and a Supabase Edge Function to Google
            Gemini to transcribe and match medicines for your review. The organiser
            does not save the image payloads and asks Gemini not to store the
            interaction. That setting does not remove Google’s separate safety and
            abuse-prevention logging. We will confirm the provider’s data-processing
            terms before enabling patient uploads. Cancelling before Organise sends nothing.
          </p>
          <p>
            AI results can be incomplete or wrong. Unknown details stay blank. Check
            every medicine name, strength, dose, timing, food instruction and course
            length against the original prescription before confirming. Only the text
            you review and save becomes part of the person’s care records; the selected
            photos are not added to those records.
          </p>
        </section>

        <section aria-labelledby="reports">
          <h2 id="reports">Reports you add</h2>
          <p>
            From version 0.4.0, you can choose a PDF or photo using Android’s file
            picker. Sahara saves a private copy for the selected person and account.
            It does not read other files or upload reports to Supabase or an AI service.
            Added reports stay on that phone until you remove them, remove the person’s
            records or uninstall the app. Keep your original files. Removing a report
            in Sahara does not delete the original.
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
            When you share reports or export care notes, Android lets you choose another app and
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
            or use it for advertising. Connections to Sahara’s service, Supabase,
            Google Gemini and OpenFreeMap use HTTPS. Sign-in tokens are encrypted using
            Android Keystore. Account caches are separated from each other and from
            unsigned-in records, and app records are excluded from Android backup and
            transfer.
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
            data handling changes. Any future audio upload or materially different AI
            processing will require an updated explanation and consent before that
            information is sent.
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
