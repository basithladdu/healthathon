# Sahara: current work status

Snapshot: 26 Oct 2026 · Awaiz (UI/Android) · branch `awaiz/at-73c5000`

## At a glance

| Area | Status |
|---|---|
| GitHub `main` | ✅ Your version (commit `f486038`, 26 Oct) with `HANDOFF.md` |
| Recording for the doctor + walkthrough tools | ✅ Done, tested on emulator, **committed locally, not pushed** (`b7a9f82`) |
| Server (Supabase **Saanthvana**) | ✅ Database brought up to date (14 of 14 updates) |
| App pointed at Saanthvana + sign-up shown | 🟡 Changed in code, **not built or committed yet** |
| Add family → WhatsApp link → auto-join | 🟡 **Being built** by a worker (files partly written) |
| Email confirmation for new accounts | ⛔ **Waiting on you** (Supabase dashboard setting) |
| Dr Ravi Kiran doctor account | ⛔ **Waiting on you** (sign up in the app, then I approve it) |
| On-device UI tests | ⚪ Not run (unit tests pass) |

---

## 1. Done

### On GitHub `main` (commit `f486038`)
Everything in `HANDOFF.md`:
- **Doctor walkthrough:** redesign, 3-step conversation flow, Saved Audio sheet, goals-of-care Q&A, note reader.
- **Patient & family walkthrough:** Rahul (patient) and Arjun (family).
- **Symptom tracker:**
  - step-by-step entry;
  - 3D pain body with zoom-to-part;
  - its own strength and colour for each spot;
  - Wong-Baker colour bar;
  - contour heat-map glow on the dark viewer;
  - redesigned diary card.

### Committed locally, not pushed (`b7a9f82`)
- **Recording:** works for **every** patient in the doctor walkthrough. The consent step is one tap: "Everyone listed agreed · Start recording". Tested on the emulator: timer and waveform show, and the new audio appears in Saved Audio.
- **Walkthrough tools switched on:** importing reports, share/export, read-aloud and prescription scanning. Data stays on the phone.

### Server: Supabase project "Saanthvana" (`kjnhtakqzboayqorrlue`)
- The 9 missing database updates are applied (14 of 14), with no errors.
- Existing data is untouched: 1 user, 1 care profile.
- Accounts, sharing, family/doctor invites, doctor approval, care-team contacts, medicine history and prescriptions are now all on this server.

---

## 2. In progress

| Work | State |
|---|---|
| **App now talks to Saanthvana** | `CareCloud.kt` uses the Saanthvana address and public key. **Not built yet.** |
| **Sign-up and password reset shown again** | `CareEmailAvailability.ready = true`. Emails still need the dashboard step below. |
| **Add family (WhatsApp link + auto-join)** | The worker is writing it (`CareAccount.kt`, `MainActivity.kt`, `CareModel.kt`, new `CareInviteLink.kt`). The flow is described below. |

**How "Add family" will work:**
1. The patient taps **Add family** on Today and enters the name, relationship (son, daughter…) and access (**Can view** / **Can view and add**).
2. They tap **Send invite on WhatsApp**. The message contains a link: `https://sahara.wedevit.in/join?c=<code>`, with the code as a backup.
3. The family member taps the link, Sahara opens, and they sign in or create an account. A card asks **"Join Rahul's care?"**, they tap Join, and they're in.
4. The patient sees "Arjun · brother · Joined".

There's also a **Paste invite** button that accepts the whole WhatsApp message.

---

## 3. Waiting on you

1. **Email confirmation (Supabase dashboard → Saanthvana).** Supabase's built-in email only reaches your own team's addresses. Choose one:
   - **Testing:** Authentication → Sign In / Providers → Email → turn **off "Confirm email"**.
   - **Proper:** Authentication → Emails → SMTP → connect a free service (e.g. **Brevo**, 300 emails a day).
2. **Dr Ravi Kiran doctor account.**
   - Sign up in the app as a doctor with an **email you own**. `ravikiran@gmail.com` may be a stranger's Gmail.
   - Use a password of **at least 10 characters**. `Ravi@123` is too short; `Ravi@123456` would work.
   - Then I mark the account as an **approved doctor** in the database.
3. **Website (for links to open the app directly).** On Android 12+, an `https://` invite link opens the app by itself only if `https://sahara.wedevit.in/.well-known/assetlinks.json` is published with the app's signing fingerprints. The file is being added to `prototype/public/.well-known/`, but the **website must be redeployed** (basithladdu's Vercel). Until then, the WhatsApp message's backup code and Paste invite still work.
4. **Security settings flagged by Supabase.** Both existed before my changes:
   - Turn on **leaked-password protection** (Authentication settings).
   - The function `public.rls_auto_enable()` can be called by anyone; it should be locked down.

---

## 4. Decisions made
- **Main:** replaced fully with your version, without a force push. basithladdu's 15 commits are still in the history.
- **Backend:** moved to **Saanthvana**. This is a split: the Play Store app (0.6.0) and basithladdu's work still use the old server (`cghlkqeqiokjqdionrkc`).
- **Sign-in:** **email + password** only. No Google or phone OTP.
- **Family joining:** **WhatsApp link + auto-join**, with the code kept as a backup.
- **Arjun (family):** sees the same as Rahul for now. No extra family limits.

## 5. Not working yet / mock-ups
| Feature | Status |
|---|---|
| Speech-to-text & translation | Online transcription off; walkthrough transcript is pre-written. On-device needs the speech model. |
| AI care note | Switched off (Gemini/Sarvam code exists); shows a sample draft |
| Goals-of-care answers | Word matching, not AI |
| Doctor verification | Manual approval in the database only (no self-service check) |
| Hospital QR / emergency retrieval | Not built; no QR or public link is created |

## 6. Known issues
- **Scrolling:** swiping over the 3D model or the strength bar rotates the model or changes the score instead of scrolling the page.
- **Short recordings:** a very short recording that fails to save can leave an unlisted file on the phone.
- **Version:** main's app version is **0.6.0 (7)**. The next Play upload must be **9 or higher**.
- **Old shortcuts:** Supabase recorded the 9 updates under new version stamps, so `supabase db push` from the repo could try to re-apply them. Don't use it on Saanthvana without checking.
- **New-user confusion:** the patient side is confusing for first-time users. There are three "how do you feel" entry points, the same tools appear in several places, unclear tabs, and clinical wording. Proposed fixes are in the session notes.

## 7. Next steps
1. Finish **Add family**, then build and test it, together with sign-up, on the emulator against Saanthvana.
2. Once you've done the email setting and signed up: **approve Dr Ravi Kiran** as a doctor.
3. Commit and push the latest work: `b7a9f82` plus the Saanthvana switch and Add family. Push to main only when you say.
4. Later: make the patient app simpler (one "How are you feeling?", clearer tabs, plain words), switch on real transcription and AI goals of care, and build the hospital QR.
