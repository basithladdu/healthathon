# Goals-of-Care Continuity Loop prototype

Interactive Health-a-thon prototype for the registered use case:

**Cancer Care -> Clinician focused -> Patient Follow-up & Continuity of Care**

## Primary demonstration

1. A treating clinician follows a guided goals-of-care conversation checklist.
2. The prototype captures a preloaded synthetic source note; it does not retain audio.
3. A deterministic extractor prepares source-linked fields with `not stated` and clarification controls.
4. The treating physician verifies every field and records patient or surrogate acknowledgement.
5. A new version is released without overwriting the prior version.
6. An unfamiliar emergency physician records an access purpose before viewing the latest verified handoff.
7. Every demonstrated access and version event appears in a browser-local audit trail.

The continuity worklist remains available as the operational layer for enrolment, ownership, outreach and rescheduling, but it is intentionally secondary to the emergency handoff.

## Safety boundary

The prototype coordinates follow-up and documents a conversation. It does not select patients, infer urgency, diagnose, estimate prognosis, recommend treatment, create a DNAR order, execute an Advance Medical Directive or claim legal authority.

## Demonstration boundary

- All names, identifiers and events are synthetic.
- State is stored only in the current browser memory and resets on reload.
- The structured draft is deterministic prototype behavior; no external AI model is called.
- No hospital system, messaging channel, ABDM service or clinical data source is connected.
- Authentication and role labels are simulated for the workflow demonstration.
- The checklist is a product hypothesis pending approval by the clinical leads.

## Stack

- React 19
- TypeScript
- Next.js-compatible App Router through Vinext
- Vite and the OpenAI Sites runtime
- Native Next.js production build for Vercel
- Authored responsive CSS with no UI theme package

## Local use

```powershell
npm.cmd install
npm.cmd run dev
```

Open `http://localhost:3000/`.

## Validation

```powershell
npx.cmd eslint app
npm.cmd run build
npm.cmd run build:next
```

## Local development gotcha: stale CSS

Turbopack's dev cache can keep serving an old build of `app/globals.css` under an
unchanged chunk hash. When that happens, edits to the stylesheet have no visible
effect no matter how many times you reload or restart the dev server, while edits
to `.tsx` files still hot-reload normally.

If a CSS change appears to do nothing, confirm it with:

```bash
curl -s http://localhost:3000/ | grep -o '/_next/static/[^"]*\.css' | head -1
```

then fetch that URL and grep for your new selector. If it is missing, clear the
cache and restart:

```bash
rm -rf .next && npm run dev:next
```
