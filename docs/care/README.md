# CARE questionnaire files (drafts)

**Status: nothing here is loaded into any CARE server.** No CARE hospital, OHC or anyone else has received these files. We have not run a CARE server, so the files have been checked by reading CARE's code and docs only. They have not been tested in a live CARE system.

## What these files are

CARE is the open-source hospital record system from Open Healthcare Network (OHC). Kerala's palliative home-care teams use it. CARE lets a hospital build its own forms, which CARE calls "questionnaires". These three JSON files are Sahara's own forms written in CARE's questionnaire format. A CARE hospital could load them, and its staff could then record the same things Sahara records:

| File | What it records | Built from (Android app) |
|---|---|---|
| `questionnaires/sahara-symptom-diary.json` | 0–10 scores for Sahara's 17 symptoms, who reported them, the time window, the name of the person writing, and a note | `CareSymptomData.kt` (`SymptomNote`, `SymptomDiaryRules`) |
| `questionnaires/sahara-pain-body-map.json` | One row per painful body area (Sahara's 12 areas), each with an optional 0–10 score, plus when the pain is worse | `CarePainBodyData.kt` (`PainBodyMapRules.sites`), `CareSymptomData.kt` |
| `questionnaires/sahara-goc-conversation.json` | The five goals-of-care sections with Sahara's standard prompts, the people present, and who has confirmed the written summary | `DoctorPreparationQuestions.kt`, `CareData.kt` (`NoteRules`, `acknowledge`), shown in `DoctorTranscriptGoals.kt` |

They come from the drafts in `docs/research/CARE_DEEP_DIVE_2026-10-27.md` section 5.2. These changes were made after checking the app and the CARE docs:

- **Goals-of-care slug renamed** from `sahara-goals-of-care-conversation` (33 characters) to `sahara-goc-conversation` (23). The CARE docs allow up to 25 characters, but the code allows 50. We follow the stricter docs limit (`docs/research/handoff-2026-10-27/care-docs-crawl/findings-docs3.md` item 1).
- **"Who wrote this note" added** to the diary and the pain form, and **"Time window" added** to the pain form. The app requires both for every note.
- **The pain score per area is now optional.** It was required. In the app, a score for each spot is optional (`painBodyRatings`).
- **A "Where exactly" short text field added** to the pain form. This matches the app's `painLocation`, which is up to 100 characters.
- **Length limits (`max_length`) added** to match the app. Author: 120. Other symptom name: 80. Note: 500. Pain location: 100. Goals-of-care answer: 2000. Confirmer name: 100. Whether CARE enforces `max_length` when a form is submitted is [unverified].
- **The family-review question was replaced.** The draft had a 4-option choice ("Not yet", "Read and agreed" and so on), and the app has no such field. The app stores a confirmation as a name plus a relationship, either "Patient" or "Family / carer". The form now has a repeating "Who has confirmed" group with those two fields.

Question IDs from the deep dive are kept, so its sample submission still matches. New questions use UUID5 IDs made from `uuid5(NAMESPACE_URL, "sahara:<slug>:<link_id>")`.

## How to check them

```
python3 docs/care/validate_questionnaires.py
```

The script checks two things. First, CARE's form rules: the slug must be 5–25 URL-safe characters and must not clash with CARE's built-in types, and only allowed question types may be used. It also checks for unique `link_id` and `id` values, choice options that are not empty, groups that have child questions, `enable_when` targets that exist, status `draft`, and at least one organisation UUID. Second, that the forms match the app's real fields. It reads the Kotlin files in `android/` and compares option lists, prompts, required flags and length limits. Last run on 27 Oct 2026: **OK**.

The forms deliberately carry **no `code` fields**: no LOINC, SNOMED or UCUM. CARE checks codes against a terminology server, and we have not mapped our symptoms or body areas to checked codes. The validator fails if a `code` is added. Some checks need a running CARE server, so the script cannot do them: whether the slug is already taken, and whether the organisation exists.

## Loading them (not done)

- **Only a CARE instance superuser can load them.** CARE shows the questionnaire admin screens only to superusers, and only a superuser can change a saved form (`findings-docs3.md` item 2, `findings-docs5.md` item 1). A hospital doctor or nurse cannot do it alone. The person who runs the hospital's CARE server has to.
- **Before loading:** replace the placeholder organisation `00000000-0000-4000-8000-000000000000` with the hospital's own organisation UUID.
- **Forms freeze once active.** "Once a questionnaire is active, edit and delete are off the table — move it to retired instead" (`findings-docs7.md` item 3). So finish the wording (and any Hindi or Malayalam text) before setting a form to `active`. A later change means a new form with a new slug.
- Date-and-time answers must include a timezone when submitted, for example `2026-10-27T09:30:00+05:30` (`findings-docs7.md` item 3).

## Known limits

- **No 0–10 range check.** CARE checks only that an integer answer is a whole number, so a score of 14 is accepted (`CARE_DEEP_DIVE_2026-10-27.md` 5.1, limit 1). The anchors ("0 = none, 10 = worst imaginable") are in the question description. A hospital that needs the range enforced can change each score to a choice question with options 0 to 10. Those answers would then be stored as text.
- **No body-site picker.** CARE has no screen to pick a body site while filling a form (limit 2). So body areas are a plain choice list. Sahara's 3D pain points cannot go in at all. CARE also cannot stop the same area being picked twice, which the app does stop.
- **No charts.** CARE makes chartable observations only from coded questions answered inside an encounter (limit 3). These forms are patient-level and have no codes. Answers are stored as form responses and can be read back, but they will not appear in CARE's observation charts.
- **The app does more than the forms can.** The forms cannot carry:
  - the link between a "Compared with last time" answer and the earlier entry;
  - a separate start date for each symptom;
  - the rule that the patient confirms before family;
  - the tie between a confirmation and the exact note version and its hash.
- **Goals-of-care prompts are fixed.** In the app, a clinician can edit or add up to 20 prompts. A CARE form holds only Sahara's standard set. The wording has no cited clinical source yet.
- The symptom diary is Sahara's own personal 0–10 diary. **It is not ESAS-r or IPOS**, and it must not be described as either.
