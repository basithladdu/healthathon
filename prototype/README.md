# Saanthvana

Interactive Health-a-thon prototype for **Cancer / Patient & Caregiver / Family Caregiver Support**.

Saanthvana (सांत्वना) helps patients and families organise documents, visits, daily care and questions for the care team, and find palliative care. A doctor can prepare, review and sign a versioned Care Note, which the patient or family can review separately.

## Main flow

1. Sign in as a patient, family member or doctor and use the Home, documents, calendar and care-circle sections.
2. Request a care conversation as a patient or family member, or start one from the doctor view.
3. Record audio with microphone permission, upload an audio file, type a conversation, or import a text transcript or text-based PDF. Recordings can be played back and saved in this browser. Live captions depend on browser support and may use the browser provider's speech service.
4. Prepare a draft from the source, then review and edit its sections. Unstated information remains unstated.
5. The treating doctor checks the wording and note type, then signs with a typed name. Signing creates a new saved version without overwriting earlier notes.
6. Home shows the latest doctor-signed Care Note. Its QR identifies the patient and version available in this browser; the PDF can be downloaded for manual sharing.
7. The patient or family reviews and signs that exact saved version separately. Older notes remain available in history, including legacy versions without a typed signature.
8. The doctor view counts recorded requests, conversation records, documented notes and signatures as separate stages. A CSV export includes only browser-local activity with a known starting point; older notes without a recorded origin are not counted.
9. Import a text-based PDF and check the extracted words against the original before saving them for search. Answers quote the saved text. From a treatment passage, enter and confirm the date treatment happened to add it to the care story and treatment table, retaining the quote and original file link. Report dates never become treatment dates automatically.

## Current boundaries

- The included profiles and documents use fictional information. Anything entered or uploaded is stored by that browser.
- Care data and saved files persist across reloads in IndexedDB, with a localStorage fallback. They are tied to the browser profile and site origin; clearing site data removes them. There is no shared database or cross-device sync.
- Sign-in and account roles are not connected to identity verification. Typed names record acknowledgements; they are not cryptographic signatures or legally verified consent.
- The default draft organiser is deterministic. An optional AI endpoint exists but is disabled unless explicitly configured.
- QR links do not transfer the note to another device. PDF download and the copy log support manual sharing; the app does not send a note, verify receipt or revoke downloaded copies.
- No hospital system, ABDM service, messaging channel or clinical data source is connected.
- The prototype documents conversations and organises family support. It does not diagnose, infer urgency or prognosis, recommend treatment, create a DNAR order or execute an Advance Medical Directive.

## Stack

- React 19
- TypeScript
- Next.js-compatible App Router through Vinext
- Vite and the OpenAI Sites runtime
- Native Next.js production build for Vercel
- Authored responsive CSS with no UI theme package

## Local use

Use Node.js 22.13 or later. From this `prototype` directory:

```powershell
npm.cmd ci
npm.cmd run dev:next
```

Open `http://localhost:3000/`. The alternative `npm.cmd run dev` starts the Vinext development runtime.

## Validation

```powershell
npm.cmd test
npm.cmd run lint
npx.cmd tsc --noEmit
npm.cmd run build:next
```

`npm.cmd run build` checks the separate Vinext build. Run build commands sequentially because they share generated output. These are available checks, not a claim that every check ran for every release.

## Deployment

Vercel uses the native Next.js build through `vercel.json`. The linked project is `continuity-loop-healthathon`; the public production address is <https://continuity-loop-healthathon.vercel.app/>. The requested address <https://saanthvana.wedevit.in/> still needs verification from the owner of `wedevit.in` and the DNS record Vercel provides. The old `saathi.wedevit.in` address redirects to the public production address.

Confirm the `.vercel/project.json` project and team before a release, deploy from this directory, and check both Vercel's Ready state and the canonical URL. Source checks, browser checks, microphone/device checks and production deployment are separate verification steps.
