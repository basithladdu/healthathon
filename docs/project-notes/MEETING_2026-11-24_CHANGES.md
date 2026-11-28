# App changes from the 24 Nov 2026 meeting (20:04 IST) and the owner's UI feedback

Source: Gemini notes of the call (Sujay, Sharada, Shirin, Awaiz, Basith) + Basith's feedback the same evening:
"cards and cards and cards, buttons and type forms", "typing the time", "no back button", "history getting too long on all routes",
"reduce card sizes which keep expanding", "Lottie on everything in onboarding".

Status (27 Nov): [ ] to do · [x] done · [~] partly / needs a decision. 0.8.2 (on main, e0ecf83) has everything marked [x]; the native redesign (Today, Medicines, Care, More, doctor home) is built in code and being installed.

## UI (owner, 24 Nov evening)
- [x] Soft hairline instead of dark card borders; no bold borders (Sujay 01:09)
- [x] Cards stop animating their size ("keep expanding")
- [x] Flat page headers (back + title), tonal secondary buttons, plain rows in grouped lists
- [x] Typed times replaced by tap chips + clock dial (reminders, medicine doses, plan, document review)
- [x] Remaining typed times/dates: emergency plan decision time, review time, doctor prescription date
- [x] Back button on every screen (Sujay 00:55)
- [x] History lists show the latest few + "Show older" on all routes
- [x] Lottie on every onboarding page; see-through "About this centre" panel fixed; compact centre cards

## Onboarding and profile
- [x] Diagnosis duration as a number + months/years, not free text (00:26)
- [x] Remove the "No treatment now" option (00:29)
- [x] Phone numbers: country code + number; doctor gets landline / hospital number too (00:27)
- [x] Simple Mode switches on automatically at 60+ from the birth date (00:06)

## Emergency QR card
- [x] Next-of-kin number, doctor number and medicines on the card
- [x] Contacts and medicines as a clean one-page table
- [x] QR opens the website copy (not a phone note) so nobody can edit it — the offline text QR is gone
- [~] Website deployed (25 Nov). The versions migration `20261124120000_emergency_card_versions.sql` is with Awaiz (HANDOFF.md, 25 Nov). Until then the QR still opens the web copy, and updating the card keeps the same link so printed cards keep working
- [x] "Not the current version" warning when a newer card exists; valid 3 months
- [x] No browser location tracking for whoever scans it (the page never asked; now says so)

## Care plan and navigation
- [x] Patient side: remove the duplicate advance-care-planning / emergency-plan pages, keep Goals of care (00:40)
- [x] Goals-of-care discussion explained in plain words next to "Ask my doctor" (00:39)
- [x] "Ask my doctor" request reaches the doctor portal
- [x] Remove stents from Tubes and lines (01:12)

## Symptoms, distress, check-ins, labs
- [x] Remove the duplicate symptom history list (graphs already show it) (00:50)
- [x] Distress thermometer and causes on two separate screens (00:52)
- [x] Check-ins: one week as a list + a calendar below (01:03)
- [x] Lab history: one compact card per test that opens on tap (01:12)
- [x] Family Today: distress and wellbeing above medicines, with a heart animation (01:21)

## Medicines
- [x] Late = 1 hour after the dose time (was 10 minutes) (00:59)
- [x] Dose status: Taken / Not taken / Skipped
- [x] Medicine history as a month calendar with dots (green taken, red missed, blue side effect); tap a day for details
- [x] Compact list for patients on 10-20 medicines
- [x] Forms: keep syrup (drop "oral solution"), add nebuliser next to inhaler, one injection type (01:01)
- [x] Frequency from a list: once / twice / three times / four times a day / as needed; strength as a number (01:02)
- [x] Native translations for medicine phrases such as "ease nausea and appetite loss" (00:56)

## Help (map)
- [x] Map opens full screen and zooms like Google Maps; a centre gives directions and its phone number (01:15)

## Also said in the full transcript (added 25 Nov)
- [x] Doctor field reads "Contact number (doctor or hospital)" (00:28)
- [x] Distress thermometer stands out from the symptoms, with "This is your overall distress, not one problem." (00:52-00:53)
- [x] Back goes to the page you came from (00:55)
- [x] "As needed" medicines (morphine) on the medicine calendar (01:01)
- [x] Strength = number + unit buttons; times on a scroll wheel like the iPhone, no letters accepted (01:02)
- [x] Check-ins: smaller cards (01:04)
- [x] Visit icons by type, no location pin for a family meeting (01:05)
- [x] Care tool colours not random (01:07)
- [x] Bold borders gone except where needed (01:09)
- [x] "What we do with your data" kept on the patient's care planning (00:43)
- [x] Emergency card: "still the same?" renew after 3 months (00:35)
- [x] Simple Mode: notice when switched on by age; SOS looks different; journal and symptoms reachable (01:19-01:21)
- [x] Caregiver: distress and wellbeing above medicines, heart beats (01:22)
- [ ] Test the prescription scan and lab/PET reports with real documents (Dr Sharada sends them) (01:14)
- [~] Who opened the QR / location: decided "leave it for now" (00:38)

## Decided after the meeting
- [x] All languages (00:57): Tamil, Malayalam, Marathi, Bengali, Gujarati and Punjabi added next to English, Hindi, Telugu, Kannada, Urdu (machine-drafted; clinician check before pilot)
- [x] DPDP research consent (00:47): opt-in switch in Settings > Privacy and data, off by default, on the patient record
- [~] Online meetings: after the hackathon (pitched for the 5 lakh funding)
