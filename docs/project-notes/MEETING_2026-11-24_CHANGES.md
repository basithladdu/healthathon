# App changes from the 24 Nov 2026 meeting (20:04 IST) and the owner's UI feedback

Source: Gemini notes of the call (Sujay, Sharada, Shirin, Awaiz, Basith) + Basith's feedback the same evening:
"cards and cards and cards, buttons and type forms", "typing the time", "no back button", "history getting too long on all routes",
"reduce card sizes which keep expanding", "Lottie on everything in onboarding".

Status (24 Nov, 23:00): built into the app; [ ] to do · [x] done · [~] partly / needs a decision

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
- [~] Live only after the database migration `20261124120000_emergency_card_versions.sql` is applied and the website is deployed
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

## Needs a decision (not started)
- [~] "All languages" in settings (00:57): the app has English, Hindi, Telugu, Kannada, Urdu. Each new language means translating ~2,000 strings.
- [~] Online meetings (pitched for the 5 lakh funding, not for the finale build)
- [~] DPDP Act consent for research use (Sujay + Basith research)
