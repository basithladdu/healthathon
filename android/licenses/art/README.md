# Bundled illustrations

Ten flat vector illustrations in `app/src/main/res/drawable/art_*.xml`, shown through `SaharaIllustration` (SaharaArt.kt):

| File | Shown on |
| --- | --- |
| `art_welcome.xml` | Welcome, after choosing Patient or Family |
| `art_empty_medicines.xml` | Medicines, none yet |
| `art_empty_documents.xml` | Documents, none yet |
| `art_empty_symptoms.xml` | Symptom notes, none yet |
| `art_empty_visits.xml` | Visits, none yet |
| `art_empty_community.xml` | Community group with no posts |
| `art_empty_patients.xml` | Doctor's patient list, no patients yet |
| `art_offline.xml` | Community could not load (offline) |
| `art_success_checkin.xml` | Daily check-in saved |
| `art_nearby.xml` | Find care, no centres found |

- **Source:** original artwork drawn for Sahara (November 2026) as plain vector geometry. No third-party illustration set, photo or trace was used, so no outside licence or attribution applies.
- **Licence:** CC0 1.0 (public domain dedication), https://creativecommons.org/publicdomain/zero/1.0/
- **Colours:** Sahara palette tokens from `SaharaPalette.kt` (Rust, Teal, Indigo, Plum, Leaf, Marigold, Gold, Rose, Success), written as hex because vector drawables cannot read Compose colours.
- **Size:** about 40 KB of XML for all ten; no bitmaps.
- Not used on goals-of-care or end-of-life screens.
