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
        <p className={styles.date}>Privacy policy · Updated 27 October 2026</p>
        <p>
          Sahara — A Palliative Care Companion is provided by WEDEVIT PRIVATE LIMITED.
          This policy covers the Sahara Android app, its website workspaces and the
          voluntary feedback and hospital-survey forms. The older browser care
          workspaces keep their own local records. The <Link href="/shared-care">shared
          care workspace</Link> signs into the same Sahara account-backed records as
          Android; it does not automatically import that older browser workspace or
          unsigned-in phone records.
        </p>

        <section aria-labelledby="information">
          <h2 id="information">What we use and why</h2>
          <p>
            The app keeps the names, care conversations, reviewed notes, confirmations,
            prescribed-medicine details, visits, tasks, lab results, conversation requests,
            symptom notes, check-ins and journal entries
            you enter. We use them to show your care records, organise daily care and,
            when you sign in, share records with people you invite.
          </p>
          <p>
            Without signing in, these records stay in the app’s private storage on your
            phone. If you sign in, names, published care notes, confirmations, named
            tasks and daily entries you add in that account are also stored with
            Supabase for syncing and sharing. Conversation drafts and their original
            words or reviewed sections are uploaded when you explicitly save or send
            them to the shared clinician workspace. An unsent phone draft is not
            automatically uploaded. Existing unsigned-in records are not automatically
            imported into the account.
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

        <section aria-labelledby="symptom-notes">
          <h2 id="symptom-notes">Personal symptom notes</h2>
          <p>
            A symptom note can contain the symptom, a number from zero to ten or an
            unanswered rating, observation date and time, reporting period, who answered,
            author name, pain location or triggers, reported change and optional words.
            Comparisons use earlier entries for the same person, symptom and reporting
            context. They do not diagnose a condition, calculate a clinical risk score
            or decide treatment. This personal diary is not a validated clinical questionnaire.
          </p>
          <p>
            In Android, these notes use the existing care-record store. Unsigned-in
            notes stay on the phone; notes saved in a signed-in account also go to
            Supabase and follow that person’s existing sharing permissions. The older
            browser diary remains in its separate browser workspace. Sharing an export
            sends the selected words to the app and recipient you choose.
          </p>
        </section>

        <section aria-labelledby="recordings">
          <h2 id="recordings">Microphone and recordings</h2>
          <p>
            Microphone permission is requested when you choose to record. Ask everyone
            taking part for permission first. You can stop at any time; recording also
            stops when the app leaves the foreground. Standard recordings remain on
            your phone and are not transcribed automatically. The public cloud-audio
            transcription service remains disabled. Android does not send recordings
            to a cloud speech or generative-AI provider. Saving or sending a text draft
            to the shared clinician workspace
            uploads that text to your account; it does not upload its recording.
          </p>
          <p>
            Optional turn-by-turn dictation uses Android’s on-device
            recognizer on supported phones. You choose the speaker and language and check
            the words; speaker identity is not detected or verified. There is no cloud
            speech fallback in this on-device dictation flow. Recognised words remain
            in the draft until you explicitly save or send them to the shared workspace
            or publish a reviewed care-note version. Read aloud uses an installed offline voice and
            does not translate the text.
          </p>
          <p>
            From Android version 0.6.0, you can also choose to transcribe a saved,
            consented recording with Qwen3-ASR running locally through sherpa-onnx.
            This requires a separate approximately 987 MB language-pack download,
            a 64-bit process and sufficient free memory. Download requests go to
            Hugging Face and its content-delivery hosts, which receive network
            information such as your IP address and the model file requested.
            Downloading the model does not send your recording or care text to them.
            After the download, transcription runs on the phone without a cloud fallback.
          </p>
          <p>
            The original recording, recognised words, segment offsets and reviewed
            corrections stay with that recording in private phone storage. Check the
            words against the audio before using them in a care note; the model can
            miss mixed-language words, names or numbers and does not identify speakers.
            Removing the recording through Sahara also removes its saved local transcript.
            If you choose to save recognised words in a signed-in care record or shared
            draft, that text follows the account sharing rules above; the original audio
            is not uploaded with it.
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

        <section aria-labelledby="translation">
          <h2 id="translation">Hindi and English reading</h2>
          <p>
            Android offers optional Hindi–English machine translation using Google
            ML Kit on-device models. An initial language-pack download needs a
            connection to Google; translation of care text then runs on the phone.
            ML Kit does not send the input care text or translated output to Google
            for translation. Technical SDK metrics and configured language settings
            are handled as described below. The original remains visible beside the
            translation. Check the result with a bilingual person before relying on
            it; machine translation is not a clinical interpretation.
          </p>
        </section>

        <section aria-labelledby="shared-clinical-records">
          <h2 id="shared-clinical-records">Shared drafts, clinician review and care-team tasks</h2>
          <p>
            In the shared workspace, the record owner chooses family or clinician
            access, the permitted record categories, whether changes are allowed and
            when membership expires. Invitations are private, single-use and expire
            after three days; the owner can cancel an unused invitation or revoke a
            member. Expired or revoked permission blocks future service requests but
            cannot recall previously saved or exported copies. An exact-version link
            or QR code identifies a note; it does not bypass sign-in or grant public access.
          </p>
          <p>
            A clinician-verification request stores the applicant’s registered name,
            medical council, registration number, request status, revision and review
            timestamps. A separately authorised verifier records the checked source,
            decision and reason. Submitting a request or selecting a doctor view does
            not grant approval or clinical access. The applicant can read their own
            request; authorised verification staff can review it. These details are not
            a public clinician directory. A patient’s separate invitation and current
            permissions are also required for a verified clinician to review a shared
            conversation.
          </p>
          <p>
            Shared drafts keep the original source, sections, author account, revision
            and submission state. A clinician review saves a distinct note version with
            the reviewing account, checked draft revision and source hash. Patient and
            family confirmations are separate, account-attributed events tied to the
            exact note version; family confirmation follows the patient record owner’s
            confirmation. These events record a discussion and acknowledgment, not
            verified understanding, legal consent or a treatment order.
          </p>
          <p>
            Named tasks store the author and recipient accounts, title, details, chosen
            due time, acceptance or completion state and timestamps. Only the assigned
            account can accept or complete the task. Workflow counts reflect these
            saved events; they do not measure patient outcomes or clinical effectiveness.
            Shared changes do not themselves dispatch a call, email or emergency alert.
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
            The voluntary <Link href="/hospital-survery">hospital survey</Link>
            requires only a hospital name and your consent. You may optionally share
            your professional name, role, registering council, registration number and
            state, plus a professional email. Incomplete or imperfectly formatted
            details are retained for review, rather than treated as verified. The current form
            does not request a mobile number. These
            professional and contact details are stored separately from hospital-service
            information and are not for public display. Optional contacts are used to
            follow up about your submission, not for marketing. We do not verify your
            identity, registration, contact ownership or hospital affiliation through
            this form, and submitting does not send an OTP, email or SMS.
          </p>
          <p>
            Hospital-service information includes the hospital name and address, institution type,
            your connection to it, state, city, an optional confirmed hospital map pin, names
            and departments of palliative-care physicians, palliative-care
            services, morphine availability and any note for families. All details
            beyond the hospital name are optional. An unchecked map pin is not saved. The pin
            is a place you select, not your current GPS location. Submissions remain
            pending review, including any named doctors; they do not automatically create a public listing or a
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
            Restricted service administration and authorised directory reviewers can
            read hospital submissions and contributor details; app-feedback records are
            limited to service administration. There is no public submission list.
            An approval publishes a separate edited hospital listing with a dated
            source check and recheck date, without contributor contacts or registration
            identifiers. Withdrawal or expiry removes that listing from the reviewed
            directory. Internal review decisions, evidence references and revision
            history remain private. We retain these records for app
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
            Cloud photo scanning remains off. The Read prescription flow reads printed
            English/Latin and Hindi/Devanagari text on your device: Android uses bundled
            ML Kit text models. The website runs Tesseract in a browser worker using
            engine and language files served by Sahara for English text. The selected
            photos and their recognised words are
            not sent to an AI service, Supabase or another OCR service. Handwriting, small print
            and other scripts may need manual entry. You can always add medicines by hand.
          </p>
          <p>
            Google’s Android ML Kit SDK sends technical diagnostics and usage metrics
            to Google over HTTPS, including app and device information, a per-installation
            identifier, performance, input/output sizes, API settings and feature events
            or errors. Translation also reports configured source and destination
            languages and uses Firebase remote configuration and installation services
            for configuration diagnostics. ML Kit initialises with the Android app;
            Sahara does not offer a setting to turn off these technical metrics. They
            may be sent when Sahara starts, even if you do not use OCR or translation. These
            metrics do not include the selected image, recognised words or care text.
            See <a href="https://developers.google.com/ml-kit/android-data-disclosure" target="_blank" rel="noopener noreferrer">Google’s ML Kit data disclosure</a> and{' '}
            <a href="https://developers.google.com/ml-kit/terms" target="_blank" rel="noopener noreferrer">ML Kit’s privacy terms</a>.
            Website OCR uses Sahara-hosted engine files; ordinary hosting logs can
            record the request for those files and your IP address.
          </p>
          <p>
            The reader supplies possible prescription lines for review. It does not
            identify medicines from packaging, verify a brand match, calculate doses,
            choose treatment or infer a clock time from a frequency such as twice daily.
            Compare the words, name, strength, dose, food instruction, course dates and
            any reminder times against the original before saving. Corrections are
            separate from the original reading. Only records you explicitly confirm are
            added to the selected person’s care list.
          </p>
          <p>
            Website prescription and packaging images you save with the reviewed list
            stay in this browser’s IndexedDB so you can view the original again; clearing
            the site data removes them. Android keeps saved source copies within the
            person’s private report files. Shared prescription originals remain while
            medicines can refer to them; removing the person’s records, clearing app
            data or uninstalling removes those copies. If you are signed in, copied medicine text or
            calendar entries you confirm may sync with your shared care records, while
            the original files remain local. Cloud scanning will not be enabled without
            suitable provider terms, an updated explanation and consent before an upload.
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
          <p>
            If you choose Read and copy facts, Android can render up to the first twelve
            PDF pages or read an image with on-device OCR. The website reads PDF text
            directly and uses local browser OCR for scanned pages and photos. The
            original remains available beside the text. Correct the reading and confirm
            it against the original before it becomes searchable. Android keeps the
            original file hash, page, line and checked excerpt with a copied fact;
            website passages cite the selected report, page when available and text line.
            Search returns matching source passages, not medical advice or an inferred
            diagnosis. No supported passage means no answer. Unreadable or unprocessed
            pages are identified; the reader does not claim to have checked the full file.
          </p>
          <p>
            Reviewed text and corrections are stored with that account’s private report
            review on Android and with the website’s separate local document workspace.
            Removing a report or person’s private records removes its Android review
            text. If you explicitly copy a fact into a signed-in care record, that copy
            follows the care-record sharing and deletion rules above; removing its
            source report does not silently remove previously confirmed care entries.
          </p>
        </section>

        <section aria-labelledby="reminders">
          <h2 id="reminders">Medicine and follow-up reminders</h2>
          <p>
            Android reminders are optional. Sahara asks for notification permission
            when you enable them and keeps your selected times, course boundaries and
            next scheduled alerts in private storage on the phone. Only the active
            account sends alerts. Saved edits, completed or removed activities,
            account changes, phone restarts and clock or time-zone changes reconcile
            the next alerts. Medicine wall-clock times follow the phone’s current time
            zone; dated visits retain their saved instant. No reminder information is
            sent to an advertising or notification provider.
          </p>
          <p>
            Notifications use a general Sahara reminder message without medicine names,
            doses or patient names on the lock screen. Opening one returns to Sahara;
            it does not mark a medicine as taken. Permission denial leaves the care
            record intact, and you can return to notification settings. Android may
            delay or block alerts because of battery restrictions, a disabled channel,
            force-stop or a switched-off phone. These are inexact reminders, not an
            urgent-care or guaranteed medication-administration service. The website’s
            calendar alone cannot send an alert after its page is closed.
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
            Check the contents and recipient before sharing. Names and roles entered in
            the legacy walkthrough are self-entered. In the shared workspace, a clinician
            request remains pending until an authorised independent verifier checks the
            professional register and the applicant’s identity/control. That review does
            not certify treatment decisions, create a legal signature or replace the
            patient’s separate invitation.
          </p>
        </section>

        <section aria-labelledby="maps">
          <h2 id="maps">Maps and links</h2>
          <p>
            Android version 0.6.0 includes 1,631 published directory listings across
            36 Indian states and union territories. This directory snapshot was updated
            on 20 October 2026; individual listings show their source-check dates.
            These public listings do not confirm current services, opening hours
            or medicine stock. Call the centre before travelling. Searching this
            bundled list does not use your GPS location or upload your care records.
          </p>
          <p>
            OpenFreeMap supplies map tiles through MapLibre and receives your IP address
            and the map area requested. Sahara does not request your precise GPS location.
            Calls, directions and source links open your dialler, maps or browser when
            you select them. Those services handle information under their own policies.
            The website’s MapLibre maps use OpenStreetMap tiles; that provider receives
            your IP address and the map areas requested.
          </p>
        </section>

        <section aria-labelledby="security">
          <h2 id="security">How information is protected</h2>
          <p>
            Sahara has no advertising SDK. We do not sell care information
            or use it for advertising. ML Kit’s technical usage and diagnostic metrics
            are described above. Connections to Sahara’s service, Supabase,
            Google model and SDK services, Hugging Face model hosts and OpenFreeMap use
            HTTPS. Sign-in tokens are encrypted using
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
            Closure removes active staff permissions, your clinician-verification request,
            assistance-quota identifiers and unfinished drafts you authored. Open tasks
            assigned to the closed account are cancelled, not marked completed. Reviewed
            source conversations, care notes, confirmations and task history that belong
            to another patient remain part of that patient’s record. Decision audit
            evidence supporting those recorded reviews or professional approvals can
            also remain; closing an account does not rewrite another patient’s history.
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
