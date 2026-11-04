# Mock goals-of-care conversations (FICTIONAL)

Every conversation here is made up for testing Sahara. No real patient, family or doctor. Nothing here is medical advice.
Hindi, Telugu, Urdu and Kannada lines were machine-written and have not been checked by a native speaker or a clinician.

| File | Language | What it tests |
|---|---|---|
| 01-english.txt | English | A full talk in the guide's order; "maybe" about ICU is doubt, not a choice |
| 02-hindi.txt | Hindi | Refusals and a comfort goal in Devanagari |
| 03-hinglish-change-of-mind.txt | Hinglish | CPR: no, then yes, then no. The last statement wins; earlier ones show as replaced |
| 04-telugu.txt | Telugu | Refusals, staying at home, comfort |
| 05-urdu.txt | Urdu | Refusals, staying at home, comfort |
| 06-kannada.txt | Kannada | Refusals, staying at home, comfort |
| 07-english-family-disagreement.txt | English | Patient and son disagree: both views stay, marked as a disagreement |
| 08-english-topics-not-discussed.txt | English | Most topics never come up: the end-of-talk prompt must list them |

Each `NN-name.expected.json` holds what the word matcher should suggest for the doctor to check (never a decision: rule V1).
`knownGaps.web` and `knownGaps.android` list anything a matcher is known to miss. Both are empty as of 4 Nov 2026.

To try one in the app: open the goals-of-care conversation, tick consent, then paste the lines (the `#` lines can be left out).
The tests read these files: `prototype/tests/goc-extract.test.mjs` and `android/app/src/test/java/in/wedevit/care/GocMockConversationsTest.kt`.
