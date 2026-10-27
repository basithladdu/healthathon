# Pilot proposal: Pallium India, Karnataka Mission (Jayanagar, Bengaluru)

**DRAFT, not sent.** One page to share with Pallium India after the team agrees the wording. Written 27 Oct 2026. Sources are in `docs/research/handoff-2026-10-27/partners/pallium-india.md` unless another file is named. Anything marked [unverified] must be checked before sending.

**Who to ask:** Dr Arjun Devarajan (Clinical Services In-Charge, Karnataka Mission), ideally through a warm introduction from Dr Athul Joseph Manuel (on Pallium's Advisory Board and CMO of Open Healthcare Network, who make CARE). Pallium's CEO post was advertised on 14 Sep 2026, so ask only for a small, local pilot, not anything organisation-wide.

## 1. The gap, in Pallium's own words and numbers
- **Visits are spaced out.** Home-care patients "generally receiv[e] visits at least twice a month" (Give.do due-diligence report, 2026). Trivandrum figures, FY 2024-25: 13,955 home visits and 3,144 telehealth contacts (Annual Report 2024-25, p.5). Karnataka figures are not published [unverified].
- **Families feel alone between visits.** Two beneficiaries: "Only you people call to ask if I'm okay" and "All I need is for someone to pick up my call" (Annual Report 2024-25, p.35-36).
- **Caregivers are untrained and carry a real burden.** 54.8% of 270 Trivandrum caregivers had moderate burden (BMC Palliat Care 2025, PMID 41239314). "Most stepped into this role without any prior knowledge" (palliumindia.org/2025/12/generating-evidence-in-fostering-palliative-care-delivery).
- **Bengaluru families asked for this.** At the Karnataka listening session (May 2026), families wished they had been told about "symptom management", "training for caregivers" and "expectations during disease progression" (palliumindia.org/2026/05/report-of-community-listening-session-on-home-based-palliative-care-in-karnataka).
- **Their own staff point to it.** Their Karnataka nurse lead: "Tele-support enables ongoing symptom monitoring, timely intervention, and caregiver guidance" (palliumindia.org/2026/06/nursing-excellence-in-home-based-palliative-care).
- **No family app exists.** We found no Pallium app for patients or families.

## 2. What we ask
- **10–15 consenting families** from the Karnataka Mission home-care list, for **8 weeks**.
- One staff contact who can answer questions for about 30 minutes a week.
- Help to explain the pilot to families and take consent (patient, or the lawful guardian if the patient cannot consent).
- Agreement on whether an ethics review is needed before we start [to confirm with Pallium].

## 3. What we measure (only two things)
1. **Symptom-diary completion:** on how many of the 56 days each family logged at least one symptom score (0–10). We count dates only, not what was written. Sahara has no built-in counting report yet, so we would agree how to count before starting.
2. **Caregiver check:** how often the caregiver answers Sahara's daily "How was your day?" check-in. With Pallium's permission, we would also ask for a burden score in week 0 and week 8 using the inventory from their own study (the Achutha Menon Centre Caregiver Burden Inventory, PMID 41239314), collected by staff by phone or on paper. This scale is **not** in the app today.

We will not claim that Sahara lowers burden or calls from a pilot this small.

## 4. What Pallium gets
- Free use for the pilot families. Families keep their own symptom diary, medicine list with a taken/missed log, visit list and documents on their phone, and can show them to the nurse at a visit or on a call.
- A short written report of what worked and what did not, shared with Pallium first.
- An offer, only with Pallium's permission and credit: put the two worksheets from their ACP booklet ("My Values and Health Care Goals", "My Treatment Choices") into Sahara's goals-of-care flow.
- Sahara's centre directory already reuses public listings from Pallium's pages (`TEAM_HANDOFF.md`). We would send our corrections back to Pallium and ask how they want the listings credited.

## 5. Data handling (what is true today)
- Without signing in, nothing leaves the phone.
- If a family signs in, entries go to Sahara's server (Supabase). Only the account owner, family members they invite and doctors approved by Sahara can read them. Sahara's own server admins can see everything (`docs/research/handoff-2026-10-27/sahara-data-flow-audit.md`).
- Voice recordings, report files and photos stay on the phone.
- Nothing is anonymised today; this is planned, not built. The server's region and a data-protection (DPDP) review are still to be confirmed [unverified].
- We will not publish any patient data, and will delete pilot accounts at the end if Pallium or the family asks.

## 6. What we do not do
- **No doses.** Sahara does not suggest, change or check medicine doses, including morphine. It only records what the family says was taken or missed.
- **No staff EMR.** Staff do not enter anything into Sahara, and nothing is entered twice. CARE stays Pallium's record.
- No advice or triage, and no replacement for the helpline or telehealth team.
- No recording of a conversation without consent. No AI drafting (today the app uses word matching).

## 7. The CARE fit
- Pallium went live on CARE on 5 Dec 2025 and moved to full use in Jan 2026 (OHC blog). Whether the Karnataka Mission also records in CARE is [unverified]; ask.
- Sahara sits **next to** CARE, not inside it. CARE has no family app; Sahara is the family's side.
- Our symptom diary, pain areas and goals-of-care questions are already written in CARE's own questionnaire format (`docs/research/CARE_DEEP_DIVE_2026-10-27.md` §5.2). They have not been loaded into any CARE server.
- After the pilot, if Pallium wants it: a small CARE add-on where family entries wait until a nurse accepts each one (estimate 15–20 working days, low confidence). The pilot itself needs no CARE change.

## 8. Before sending
- **Language:** Sahara is English and Hindi (Hindi still being finished). Pallium's Karnataka materials come in Kannada and the families may need it. Ask which languages pilot families use; a Kannada version is not built.
- Agree the symptom list with our clinicians, and ask Pallium which symptom form they use in CARE (not published).
- Confirm the timing after the hackathon submission.
