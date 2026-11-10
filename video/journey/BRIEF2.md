# Revision 2: what changed (read after BRIEF.md)

The narration was rewritten and re-voiced (143.3 s). Read `journey/script.json` and `journey/TIMINGS.txt` for every word time. Scene windows are in `src/journey/windows.ts` (A, B, C, D, E, X, F, G, H, I). Never change narration, timings, windows, kit.tsx, team.tsx, cast.ts, Journey.tsx, Captions.tsx, or another agent's files.

## The user's notes, which drive this revision
1. **Real people.** The team is five real people. Use their real names and photos, never generic "a doctor on our team" or "a medical student". Basith and Awaiz (engineers who run a software startup, WeDevIT); Dr Sujay and Dr Sharada (pulmonologists); Shirin (medical student).
   - `src/journey/team.tsx` exports `TEAM`, `Face` (round photo), `NameTag` (photo + name chip; pass `a` from `pop()` for a spring reveal), `PlayBadge` (the Google Play badge, same mark as the Sahara website).
   - Each flat character stands for a real person: `CAST.engA` = Basith, `CAST.engB` = Awaiz, `CAST.teamDoctor` = Dr Sujay, `CAST.sharada` = Dr Sharada (new), `CAST.medStudent` = Shirin. Whenever one of them first appears in your scene, or speaks, pin a `NameTag` next to them.
   - **`CAST.engC` is not a real person. Remove every use of it.**
   - Photos are in `public/team/{basith,awaiz,sujay,sharada,shirin}.jpg` (600x600 headshots).
2. **Accurate facts only.** The team did build those clinical tools: syringe driver calculator, spinal cord compression triage, sedation protocol and about 20 more, in two days (15-16 Sep). Other real tool names you can use on cards: Symptom radar (ESAS-r), Prognosis staging, Cancer emergencies, Caregiver burden, Wound care, Bone metastases, Nutrition and cachexia, Nerve pain, Breathlessness, Delirium, Bowel obstruction, Radiotherapy side effects, High calcium, Just-in-case drug box, Children's palliative care, Deprescribing.
3. **Concrete switches, not "we cut it all".** These are the switches from the narration, in order:
   - Doctor tools became an app for patients and families.
   - Shortlisted on 9 Oct (top 120 of about 450 teams), then Basith and Awaiz rebuilt it as a native Android app.
   - Everything is pulled back to one centre: the goals of care talk.
4. **More concrete features, shown with the real app footage**, including the goals-of-care document (GOCD) being signed by the doctor and then the family, and the lock.
5. **Google Play: use the real badge** (`PlayBadge`) and the real listing image `public/brand/play-listing.png` (2150x520, "Sahara: Palliative Companion, WEDEVIT PRIVATE LIMITED, Install").
6. **Fusion.** Mix the flat illustration with real UI: real phone footage, real photos and the real document, set inside the illustrated world. Phones held by characters, screens glowing in the room, the photo chips.
7. **Captions are burned in** at the bottom: a dark pill about 70 px tall, centred, from y ≈ 930 to 1024. Keep important text and faces above y = 920.
8. Music and sound effects are re-timed by the lead. You only build the picture.

## Rules carried over
BRIEF.md style rules still apply: flat vector, constant motion, beats on their words via `at(id, word)`, `<Rise>` for text, and no full stops.
- **Word tokens.** Use the word tokens exactly as they appear in TIMINGS.txt; numbers stay as words, e.g. `at('s07','Twenty')`.
- **Name tokens.** For the respelled names, words.json keeps the real spelling ("Awaiz", "Shirin"). Check TIMINGS.txt for how a token ends up when punctuation is attached, e.g. "Shirin," may appear as "Shirin".
- **App footage.** `PhoneClip` clips f01-f15 are listed in BRIEF.md.
  - Look at a clip before using it: `ffmpeg -ss 3 -i public/cut/f10.mp4 -frames:v 1 out/<you>/f10.jpg`, then Read the jpg.
  - Add the small "Practice data. All names are fictional." line near any phone that shows the app.

## How to check
- Typecheck: `npx tsc --noEmit -p .`
- Stills: `node tools/jstills.mjs out/<you> <secs...> --sheet`. Render at most 8 stills per call (several agents share this 16 GB PC). Then Read the sheet.
- Check every beat, plus the window edges (from + 0.05, to - 0.05).
- Remove anything a teammate would call AI-looking: empty areas, overlapping text, cropped heads, frozen frames.
