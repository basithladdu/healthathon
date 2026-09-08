# Vercel production deployment

- URL: https://continuity-loop-healthathon.vercel.app
- Project: `continuity-loop-healthathon`
- Deployment: `dpl_DnH4qgbkT2brBeVShJA5U1gNj8Ay`
- Target: production
- State: `READY`
- Source commit: `54855185e429cd9eebe183915127771825126fe2`
- Deployment URL: https://continuity-loop-healthathon-2wv15d3vo.vercel.app
- Cloud build region: Washington, D.C. (`iad1`)
- Cloud build result: Next.js 16.2.6 compiled, TypeScript passed and four static routes/pages were generated
- Public HTTP check: 200, `text/html; charset=utf-8`
- Favicon HTTP check: 200, `image/svg+xml`
- Browser check: 1440px desktop and 375px/390px mobile passed without horizontal overflow or a framework error overlay
- Final browser console check: zero errors and zero warnings
- Runtime error/fatal query for the final deployment after production requests: no entries returned
- The verified-record archive renders Leela's synthetic entry as Version 1, matching the source fixture.
- Leela's live retrieval gate renders the selected-patient initials `LT`.
- Non-primary patient summary actions are disabled rather than routing into Meera's source-linked workflow.
- Enrolment's selected contact channel and coordination note are preserved in the live outreach context.

## Deployment inputs

Vercel uses `npm run build:next` from `vercel.json`. No environment variables, secrets, databases, hospital systems or AI providers are configured.

The `.vercel/project.json` link is local deployment metadata and is ignored by Git. No deployment credential is stored in the project source.

## Re-deploy

```powershell
cd C:\Users\basit\Downloads\CODE\healthathon\prototype
npm.cmd run lint
npm.cmd run build:next
vercel.cmd deploy --prod --yes --logs
```

Do not describe this deployment as a production-ready clinical system. It is a public synthetic workflow prototype.
