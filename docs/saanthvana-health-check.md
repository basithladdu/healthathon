# Saanthvana scheduled read-only check

This repository contains the scheduled workflow; **database activation is pending**.
It cannot pass until the one-time SQL and matching GitHub secret below are set up.
The current connected Supabase account cannot access the target project, so those
steps have not been completed or tested live. It targets only
the email's Saanthvana project, `kjnhtakqzboayqorrlue`. Sahara's current account,
shared-care and feedback backend uses a different project, `cghlkqeqiokjqdionrkc`.
Do not reuse that project's key here.

## Activate once

1. In the Supabase dashboard, explicitly select **Saanthvana** with project ref
   `kjnhtakqzboayqorrlue`. If already paused, restore it first.
2. Review and run `ops/supabase/saanthvana-health-check.sql` in that project's SQL
   editor. This creates a tableless, read-only function returning `true`; it grants
   anonymous execution of only that function. It does not change patient records,
   table permissions or RLS. This is operator SQL, not an app migration.
   If that function already exists, setup fails without replacing it: inspect the
   existing definition rather than blindly overwriting it.
3. In GitHub repository Settings → Secrets and variables → Actions, add secret
   `SAANTHVANA_SUPABASE_PUBLISHABLE_KEY` using **that project's publishable key**
   (or legacy `anon` key). Never use a secret/service-role key, account token or
   database password. Legacy keys are checked for matching project and anon role;
   publishable keys are opaque, so the operator must select the correct project.
4. Merge the workflow, script and supporting files into the repository's default
   branch, currently `main`. Scheduled workflows only run from the default branch.
5. Open Actions → Saanthvana read-only health check → Run workflow. Check that it
   passes. Missing setup fails visibly; nothing silently provisions the database.

## Cadence and behaviour

GitHub checks the UTC date daily at **04:23 UTC (09:53 India time)**. Only every
sixth elapsed day from **22 October 2026 UTC** makes an HTTP request: 22 October,
28 October, 3 November, 9 November, and so on. This does not reset at month-end.
Manual runs always check immediately and do not move the scheduled anchor.

The request is a fixed-host HTTPS `GET /rest/v1/rpc/sahara_health_check`, using only
the public API key header. No redirects, database writes, dummy submissions, or
repository commits occur. Only a strict `true` result succeeds. Requests have a
15-second timeout and at most three attempts for transient failures. Keys and
response bodies are not logged. Other days skip the network step.

## Important limitations

**This is not a guarantee against automatic pausing.** Supabase's current guidance
discusses a few requests **each day**; this requested six-day check may not satisfy
its definition of sufficient activity. Its supported option to avoid inactivity
pausing is a paid plan. No billing or plan change is made by these files.

GitHub scheduled jobs can be delayed or dropped during high load; this is not an
uptime guarantee. Public repositories also automatically disable scheduled
workflows after 60 days without repository activity. Review Actions failures and
Supabase notices yourself; the workflow cannot restore an already paused project.

References: [Supabase free-project pausing](https://supabase.com/docs/guides/platform/free-project-pausing),
[GitHub scheduled workflows](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).

Offline checks: `python -m unittest discover -s scripts -p test_saanthvana_health_check.py`.
