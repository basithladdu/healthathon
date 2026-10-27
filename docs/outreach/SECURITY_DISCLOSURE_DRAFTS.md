# Private security disclosure drafts (3)

**DRAFTS, not sent.** Written 27 Oct 2026 from our read-only repo notes. Send nothing until the owner agrees.

## Rules for all three
- **Private channels only.** No GitHub issues, no Slack channels, no social media, no slides, no mention in the hackathon entry.
- **Facts only.** Every statement comes from our research files: `docs/research/handoff-2026-10-27/ohc-repos/repos-r1.md` (care_api_backup), `ohc-repos/repos-r4.md` (ccm_uhi), `partners/niram.md` (Niram).
- **No patient data and no secrets** in these drafts or in any message. We reference file paths only.
- **What we did and did not do.** We read public source code from shallow clones and then deleted them. We did not run any code, log in anywhere, try any password or token, call any live server, or read any patient rows. Keep it that way. Do not "confirm" a finding by testing it on a live system.
- Offer to help, ask them to tell us when it is fixed, and give them time. We will not publish anything about these issues.

---

## 1. OHC `ccm_uhi`: patient details returned without login

**Send to:** the private security address in CARE's `SECURITY.md` (it says security reports go by email, not public issues; `docs/research/CARE_DEEP_DIVE_2026-10-27.md` §7). We have not recorded the address itself [unverified: read `SECURITY.md` in `ohcnetwork/care` before sending]. If unsure, ask support@ohc.network for the right private contact first, without details.

**Subject:** Private security report: ccm_uhi endpoints have no authentication

> Hello OHC security team,
>
> We are Team Sahara, a student team in Health-a-thon 2026. While reading your public repositories, we noticed something in `ohcnetwork/ccm_uhi` that we think you should know about. We are reporting it privately and will not publish it.
>
> **What we saw (from reading the code only):**
> - In `ccm_uhi/api/appointment.py`, `ccm_uhi/api/enquiry.py` and `ccm_uhi/api/emergency_resource_request.py`, authentication and permissions appear to be switched off for all endpoints.
> - As we read it, on any CARE deployment with this plugin installed, anyone could:
>   - list facilities, practitioner names, departments and slots (`GET /appointment/search/`, `POST /service_availability/`);
>   - read a patient's name, phone number, date of birth and address by sending a booking ID (`POST /appointment/status/`, via `map_patient_to_billing`);
>   - create emergency resource requests.
>
> **What we did not do:** we did not run the plugin, call any live server or test any booking ID. We do not know which deployments have it installed. This is based on code reading only and may be wrong.
>
> **Suggestion:** require authentication (or a signed gateway request) on these endpoints, and check whether any live deployment has it installed.
>
> Happy to answer questions. Please let us know when it is resolved.
>
> Team Sahara
> [sender name, email]

**Notes before sending:**
- Confirm the code on the current `HEAD` of `ohcnetwork/ccm_uhi`. Our note is from 27 Oct 2026; the repo was last pushed 2 Jun 2026 (`ohc-repos/ALL_84_REPOS.tsv`).
- Line numbers were not recorded; file paths only.

---

## 2. OHC `care_api_backup`: committed demo passwords and old tokens

**Send to:** same private address as #1. It can go in the same email as #1, as a second item.

**Subject:** Private security report: credentials committed in care_api_backup

> Hello OHC security team,
>
> A second, smaller item from the same reading, in `ohcnetwork/care_api_backup` (the Bruno API collection):
>
> - The committed environment files (`environments/`) contain demo usernames with short passwords, including one for `https://care-api.do.ohc.network`.
> - The committed requests under `OTP/` (`*.bru` files) contain patient login tokens (JWTs) from 2024. They have expired, but they embed a phone number that looks real.
>
> **What we did not do:** we did not try any of these passwords or tokens, and we have not copied them anywhere.
>
> **Suggestions:**
> - If those passwords still work on `care-api.do.ohc.network` or any other server, change them.
> - Check whether the phone number belongs to a real person. If it does, consider removing the tokens from the files and from git history.
> - If these are meant as public demo accounts, a note in the README would make that clear.
>
> Team Sahara
> [sender name, email]

**Notes before sending:**
- Our note does not say whether the phone number is real or a test number [unverified]. Do not repeat the number, usernames or passwords in any message.
- The repo was last pushed 1 Dec 2025 (`ohc-repos/ALL_84_REPOS.tsv`).

---

## 3. Niram: public repo appears to hold a patient roster

**Background (facts from `partners/niram.md`):**
- A public GitHub repo, `rupazb/niram_homecare_routeplanning`, is a home-visit route planner. Its README names "Niram Palliative Care Center's single ambulance". Whether the author works with Niram is [unverified].
- The repo serves `patients_cleaned.csv` and `patients_all.geojson`. The CSV header has columns for registration number, name, gender, age, diagnosis, date of death, address, phone number and latitude/longitude.
- Our researcher read **only the header row**, not the data. Whether the rows are real or synthetic is [unverified].

**Who to tell, privately, and in this order:**
1. **Niram:** connect@nirampalliativecare.org (the public address on their website). They can check whether the data is real and talk to the author.
2. **The repo author:** only through a private route. GitHub has no private messages, so use a public email on the author's profile if there is one, or GitHub's private vulnerability reporting if the repo has it switched on [unverified]. **Never open a public issue**: it would point others to the file.
3. If neither replies within about a week and the file is still public, the owner decides whether to ask GitHub Support privately.

**Subject:** Private note: a public GitHub repo may contain Niram patient details

> Hello Niram team,
>
> We are Team Sahara, a student team in Health-a-thon 2026 working on an app for palliative families. While reading public information about palliative care centres, we came across a public GitHub repository for home-visit route planning that mentions Niram: `github.com/rupazb/niram_homecare_routeplanning`.
>
> It contains two files, `patients_cleaned.csv` and `patients_all.geojson`. The CSV's column headings include patient name, diagnosis, address, phone number and location. We looked only at the column headings, not at any rows, so we do not know if the data is real or made up.
>
> If it is real patient data, you may want to ask the author to make the repository private and remove the files from its history. We have not shared this with anyone else and will not.
>
> We are writing because we would want to be told. No reply is needed.
>
> Team Sahara
> [sender name, email]

**Notes before sending:**
- Keep this separate from any pilot request to Niram. Do not ask Niram for anything in the same message.
- Check the repo is still public before sending. If the files are gone, send nothing.
- Do not download the files again, open the rows or keep any copy.
