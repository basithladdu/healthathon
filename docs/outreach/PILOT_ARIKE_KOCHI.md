# Pilot proposal: Arike (Kochi)

**DRAFT, not sent.** One page to share with Arike after the team agrees the wording. Written 27 Oct 2026. Sources are in `docs/research/handoff-2026-10-27/partners/arike.md` unless another file is named. "AR25" means Arike's Annual Report 2025 (https://john-d03.github.io/arike-annualreport25/, linked from arikecare.org/annual-report). Anything marked [unverified] must be checked before sending.

**Who to ask:** Arike's public address (info@arikecare.org) and Dr Athul Joseph Manuel (Arike's Secretary and co-founder, and CMO of Open Healthcare Network, who make CARE). OHC should be part of the conversation.

**The main risk, said first:** Arike already plans its own "patient apps for reminders, resources, and symptom tracking" through CARE and Care Scribe (AR25, Future Plans). A separate app with its own server could look like a rival. So we offer Sahara as **the family side of Arike's CARE**, built with Arike and OHC, and we ask first what they are already building.

## 1. The gap, in Arike's own words and numbers
- **Most of the need comes between visits and at night.** In 2025 Arike's control room took 33,132 calls (15,865 at night) and gave 11,262 tele-consultations (AR25). "Night-time communication demand remained consistently high, highlighting the importance of round-the-clock responsiveness in managing pain crises, symptom escalation, and caregiver distress" (AR25).
- **Other services stop at 4pm.** "Most services operate only between 10am and 4pm - leaving patients without support during evenings, nights, weekends, and emergencies" (arikecare.org/understand).
- **Caregivers are old and strained.** "Untrained caregivers - often elderly spouses - face burnout" (arikecare.org/understand). In an Ernakulam study co-authored by an Arike trustee, 32.1% of 115 caregivers had moderate-to-severe burden, measured with the Malayalam Zarit Burden Interview (BMJ Support Palliat Care 2026, PMC13422066).
- **Paper records get lost.** The 2021 NHM Ernakulam spec said paper cards "can easily be ruined/lost" and planned a patient view by "QR code/mobile phone authentication" (model.coronasafe.network/centralised-facilities/palliative_homecare).
- **No family app exists today.** It is in Arike's future plans (AR25).

## 2. What we ask
- **10–15 consenting families for 8 weeks.** Families with frequent night calls or near end of life would show the most. (The research note suggested 20–30 families; we would rather start small.)
- KOODE volunteers, who already act as "a bridge to the team", to help families get started (arikecare.org/programmes).
- One staff contact for about 30 minutes a week.
- Help with consent (patient, or the lawful guardian), and agreement on whether an ethics review is needed [to confirm].

## 3. What we measure (only two things)
1. **Symptom-diary completion:** on how many of the 56 days each family logged at least one symptom score (0–10). We count dates only, not content. Sahara has no built-in counting report yet, so we agree how to count first.
2. **Caregiver check:** how often the caregiver answers Sahara's daily "How was your day?" check-in, plus a burden score in week 0 and week 8. The best fit is the 3-item Brief AMC-CBI, made and tested in Malayalam in Kerala (IJPC 2026, PMID 42694816; see `partners/iapc.md`), or the Malayalam Zarit tool from the trustee's study. Either needs the authors' permission, and staff would collect it by phone or on paper. It is **not** in the app today.

We will not claim fewer night calls or lower burden from a pilot this small.

## 4. What Arike gets
- Free use for the pilot families: symptom diary with a 3D pain body, medicine list with a taken/missed log, visit list, documents, and invites so a son or daughter living away can follow along.
- The family can show the diary to the nurse, or read it out on a night call.
- A short written report of what worked and what did not, shared with Arike first, to feed into their own patient-app plans.

## 5. Data handling (what is true today)
- Without signing in, nothing leaves the phone.
- If a family signs in, entries go to Sahara's server (Supabase). Only the account owner, family members they invite and doctors approved by Sahara can read them. Sahara's own server admins can see everything (`docs/research/handoff-2026-10-27/sahara-data-flow-audit.md`).
- Voice recordings, report files and photos stay on the phone.
- Nothing is anonymised today (planned, not built). The server's region and a data-protection (DPDP) review are still to be confirmed [unverified]. Arike may want data on its own CARE server instead; we should ask.
- We will not publish any patient data, and will delete pilot accounts at the end if asked.

## 6. What we do not do
- **No doses.** Sahara does not suggest, change or check doses. It only records what the family says was taken or missed. (We found no public statement on how Arike handles morphine; ask.)
- **No staff EMR.** Nurses and the control room enter nothing into Sahara, and nothing is entered twice. CARE stays Arike's record.
- No advice or triage. No replacement for the control room.
- No AI drafting (today the app uses word matching), and nothing that competes with Care Scribe.

## 7. The CARE fit
- Arike moved from CARE 2.0 to 3.0 in May 2025 and helps build CARE (OHC blog). It also hosts CARE for IAPC Kerala: care.iapckerala.in now forwards to care.arikecare.org.
- CARE has no family app. Our symptom diary, pain areas and goals-of-care questions are already written in CARE's questionnaire format (`docs/research/CARE_DEEP_DIVE_2026-10-27.md` §5.2), not yet loaded into any CARE server.
- After the pilot, if Arike and OHC want it: a small CARE add-on where family entries wait until a nurse or the control room accepts each one (estimate 15–20 working days, low confidence). The pilot itself needs no CARE change.

## 8. Malayalam is needed (hard blocker)
- Arike's families speak Malayalam, and their caregiver research used Malayalam tools. Sahara is English and Hindi only today (Hindi still being finished). **Without a Malayalam version there is no pilot.**
- Before asking Arike: decide who translates and who checks the translation clinically, and translate the symptom names with care (the Malayalam Zarit tool had translation problems, PMID 42694816).

## 9. Questions to ask Arike first
1. Which symptom forms are set up in your CARE 3.0?
2. Is a CARE patient app or Care Scribe family feature already being built?
3. How do families get morphine, and who manages it?
4. Where must family data be stored?
