# Lottie animations

## Library

- **com.airbnb.android:lottie-compose 6.7.1** (pulls in `com.airbnb.android:lottie` 6.7.1), from Maven Central.
- Source: https://github.com/airbnb/lottie-android
- **Licence:** Apache License 2.0, https://www.apache.org/licenses/LICENSE-2.0 (declared in the artifact's POM).

## Animations

Fourteen small animations in `app/src/main/res/raw/lottie_*.json`, played through `SaharaLottie` (SaharaLottie.kt).

| File | Kind | Shown on | Playback |
| --- | --- | --- | --- |
| `lottie_success.json` | Success | Check-in saved, dose marked taken (Undo row), post sent, success dialog | once |
| `lottie_breathe.json` | Breathe | Not shown at present (Today's sun now uses SaharaDaySky, 22 Nov); kept for reuse | loop (4 s) |
| `lottie_heart.json` | Heart | Family: "How are you holding up?" card | loop (2.4 s) |
| `lottie_pill.json` | Pill | Medicines, none yet | loop (2 s) |
| `lottie_document.json` | Document | Documents, none yet | once |
| `lottie_pin.json` | MapPin | Find care (Nearby), no centres found | once |
| `lottie_wave.json` | Wave | Welcome panel after choosing Patient or Family | loop (2.4 s) |
| `lottie_loading.json` | Loading | Full-screen loading view | loop (1.2 s) |
| `lottie_confetti.json` | Confetti | Today's doses, when the last dose of the day is ticked | once |
| `lottie_hug.json` | Hug | Community group with no posts | once |
| `lottie_calendar.json` | Calendar | Visits, none yet | once |
| `lottie_offline.json` | Offline | Community could not load (offline) | loop (3 s) |
| `lottie_bloom.json` | Bloom | Today: a mood check-in has just been saved (never on distress) | once (1.5 s), then hidden |
| `lottie_sprout.json` | Sprout | Calm empty states (no check-ins, no symptom notes, empty module lists) | 2 sways (3 s each), then rests |

`lottie_bloom.json` and `lottie_sprout.json` (22 Nov 2026) are also original, hand-authored for Sahara by
`generate_sahara_lottie_extra.py` in this folder, in the Sahara palette; no outside licence applies.

- **Source:** original animations made for Sahara (November 2026) as plain Lottie (bodymovin 5.7) JSON from simple
  shapes and keyframes by `generate_sahara_lottie.py` in this folder (`python3 generate_sahara_lottie.py <out-dir>`).
  No LottieFiles download, third-party animation or trace was used, so no outside licence or attribution applies.
- **Licence:** CC0 1.0 (public domain dedication), https://creativecommons.org/publicdomain/zero/1.0/
- **Colours:** Sahara palette tokens from `SaharaPalette.kt`, written as RGB fractions because Lottie JSON cannot read Compose colours.
- **Size:** about 64 KB of JSON for all twelve (largest: confetti, about 20 KB); no images, fonts or expressions.
- **Motion:** calm (no springs or bounce beyond a small hop); with "Remove animations" or Battery Saver on, each shows one still frame.
- Not used on goals-of-care or end-of-life screens.
