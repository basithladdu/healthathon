# Simulated goals-of-care conversations: how well does the app's word matching find the answers?

27 October 2026 · HANDOFF item **B17** ("Run 20 simulated conversations with different people and note the results").

**Short answer: on these 20 invented conversations, the app found none of the 130 answers. It pre-filled a wrong line 22 times, and 21 of those 22 were the doctor's own question.**

**Update, same day: after the matcher fix it suggests a right line for 110 of 130 answers, with 1 wrong suggestion. See [After the matcher fix](#after-the-matcher-fix) at the end. The test set is small and was partly seen while the rules were written, so treat these as optimistic.**

Everything here is synthetic. The people, illnesses and words are invented. No real patient data is used.

## What was tested

- **The real code, unchanged.** The test calls the same functions the doctor's transcript page uses:
  `doctorSuggestQuestions(null, "")` (the 8 standard goals questions) → `LocalQuestionMatcher.match` (`CareDraftAssist.kt`) → `transcriptGoalSpan` / `transcriptGoalStatus` / `transcriptGoalsFill` (`DoctorTranscriptGoals.kt`). This is the path in `CareUi.kt` that fills each goals card and shows "Found in the conversation".
- **The 8 questions** (from `DoctorPreparationQuestions.kt`):
  - Q1 What matters most to you at the moment?
  - Q2 What are you hoping for from your care?
  - Q3 Who would you like involved in decisions about your care?
  - Q4 What is your understanding of your current health?
  - Q5 What worries you most about the future?
  - Q6 Is there anything you would like to talk about that we have not covered?
  - Q7 What would you like to happen next?
  - Q8 Who should hear about what we have agreed?
- **20 conversations** in [`sim-conversations/`](sim-conversations/):
  - 12 in English, 4 in Hinglish (Roman script), 3 in Hindi (Devanagari) and 1 in Hindi with English words mixed in.
  - The illnesses: cancers (ovarian, oral, breast, lung, pancreas, cervix, bowel, mouth, brain, myeloma, childhood leukaemia), COPD, heart failure, liver cirrhosis, kidney failure on dialysis, stroke, motor neurone disease, dementia and frailty after a hip fracture.
  - The people present differ. Some patients are alone; in some talks a child or spouse speaks for the patient, a grandson translates, or two siblings disagree. In one, the family asks the team not to tell the patient.
  - The answers vary too: direct, one-word, split over several lines, evasive ("God knows", "जो भगवान की मर्ज़ी"), deferred ("let's leave it"). In some talks a topic is never raised.
  - Some doctors read the standard questions word for word, as the app shows them. Others paraphrase.
  - The structure follows the goals-of-care record described in `GOCD_PDF_AUDIT.md` and `PRODUCT_REQUIREMENTS.md`: what matters, who is involved, understanding, worries, topics not discussed, follow-up and who is told.
- **Ground truth by hand.** In each file, the lines that answer a question end in `<<Qn>>`. A question with no tagged line was not answered: it was never raised, deflected or deferred. When patient and family give different views, both lines count. When the agreed plan is said by the doctor, that line counts for Q7. The tags are removed before the matcher sees the text.
- **Scoring.** The screen pre-fills only the **first** suggestion, so that is what is scored:
  - **hit:** the first suggestion is a right line.
  - **miss:** the question was answered, but nothing was found.
  - **wrong:** a different line was pre-filled.
  - **wrong\*:** a line was pre-filled although the question was never answered.
  - **—:** correctly left open.
- **Test file:** `android/app/src/test/java/in/wedevit/care/SimulatedGoalsConversationsTest.kt`. It checks only that the files and the pipeline agree; it does not check a score, so a matcher change never breaks the build. It prints the full report and writes it to `android/app/build/reports/sim-conversations.md`.
- **Run:** 27 Oct 2026, one run of `./gradlew --no-daemon testDebugUnitTest --tests in.wedevit.care.SimulatedGoalsConversationsTest` under the gradle lock. It passed (exit 0). The numbers below are copied from that run.

## Results per conversation

| Conversation | Language | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Q8 |
|---|---|---|---|---|---|---|---|---|---|
| C01 | English | miss | miss | wrong | wrong | miss | wrong | miss | miss |
| C02 | English | miss | miss | miss | miss | miss | — | miss | — |
| C03 | Hinglish, Roman script | miss | miss | miss | miss | — | miss | miss | — |
| C04 | Hindi, Devanagari | miss | miss | miss | miss | miss | — | miss | — |
| C05 | English | miss | miss | wrong | miss | miss | miss | miss | miss |
| C06 | English | miss | miss | wrong | wrong | miss | wrong | miss | miss |
| C07 | Hinglish, Roman script | miss | miss | miss | miss | miss | — | miss | — |
| C08 | English | miss | miss | wrong | wrong | miss | wrong | miss | miss |
| C09 | Hindi, Devanagari | miss | miss | miss | miss | — | miss | miss | — |
| C10 | English | miss | miss | wrong | miss | miss | — | miss | miss |
| C11 | Hinglish, Roman script | miss | miss | miss | miss | miss | miss | miss | miss |
| C12 | English | miss | miss | wrong | miss | miss | — | miss | — |
| C13 | English | miss | — | — | wrong* | — | miss | miss | — |
| C14 | Hindi with English words, Devanagari | miss | miss | wrong | miss | miss | — | miss | — |
| C15 | English | miss | miss | wrong | miss | miss | miss | miss | miss |
| C16 | Hinglish, Roman script | miss | miss | wrong | miss | miss | — | miss | miss |
| C17 | English | miss | miss | wrong | wrong | miss | wrong | miss | miss |
| C18 | English | miss | — | — | — | — | — | miss | — |
| C19 | Hindi, Devanagari | miss | miss | — | miss | miss | — | miss | miss |
| C20 | English | miss | miss | wrong | wrong | — | wrong | miss | miss |

## Precision and recall per question

- **Precision:** hits ÷ everything pre-filled.
- **Recall:** hits ÷ questions that were really answered.
- **Top-3 recall:** a right line appears anywhere in the matcher's three suggestions. The screen does not show those three today.

| Question | Answered | Hit | Miss | Wrong | Wrong* | Left open | Precision | Recall | Top-3 recall |
|---|---|---|---|---|---|---|---|---|---|
| Q1 | 20 | 0 | 20 | 0 | 0 | 0 | n/a | 0% (0/20) | 0% (0/20) |
| Q2 | 18 | 0 | 18 | 0 | 0 | 2 | n/a | 0% (0/18) | 0% (0/18) |
| Q3 | 17 | 0 | 6 | 11 | 0 | 3 | 0% (0/11) | 0% (0/17) | 0% (0/17) |
| Q4 | 18 | 0 | 13 | 5 | 1 | 1 | 0% (0/6) | 0% (0/18) | 0% (0/18) |
| Q5 | 15 | 0 | 15 | 0 | 0 | 5 | n/a | 0% (0/15) | 0% (0/15) |
| Q6 | 11 | 0 | 6 | 5 | 0 | 9 | 0% (0/5) | 0% (0/11) | 0% (0/11) |
| Q7 | 20 | 0 | 20 | 0 | 0 | 0 | n/a | 0% (0/20) | 0% (0/20) |
| Q8 | 11 | 0 | 11 | 0 | 0 | 9 | n/a | 0% (0/11) | 0% (0/11) |
| All | 130 | 0 | 109 | 21 | 1 | 29 | 0% (0/22) | 0% (0/130) | 0% (0/130) |

### By language

| Group | Answered | Hit | Miss | Wrong | Wrong* | Left open | Precision | Recall | Top-3 recall |
|---|---|---|---|---|---|---|---|---|---|
| English | 79 | 0 | 60 | 19 | 1 | 16 | 0% (0/20) | 0% (0/79) | 0% (0/79) |
| Hinglish, Roman script | 27 | 0 | 26 | 1 | 0 | 5 | 0% (0/1) | 0% (0/27) | 0% (0/27) |
| Hindi, Devanagari | 18 | 0 | 18 | 0 | 0 | 6 | n/a | 0% (0/18) | 0% (0/18) |
| Hindi with English words, Devanagari | 6 | 0 | 5 | 1 | 0 | 2 | 0% (0/1) | 0% (0/6) | 0% (0/6) |

## Clear failure patterns

1. **The app pre-fills the doctor's question as the answer.** 21 of the 22 wrong fills are the doctor's own question line, for example "Who would you like involved in decisions about your care?" saved as the answer to that same question. This happens for Q3, Q4 and Q6, whose wording has long words such as "involved", "decisions" and "understanding". The question line shares every word with itself. The matcher takes 2 points off a line that looks like a question, but that is less than the overlap, so the question still wins. The card then says **"Found in the conversation"** next to the doctor's own words.
2. **In 20 of those 21 cases, the right answer was the next line.** The matcher finds where the question was asked, then points at the question instead of the reply after it.
3. **Real answers do not repeat the question's words.** "Being at home with my grandchildren" shares no words with "What matters most to you at the moment?". The matcher needs about 3 points of shared words from the question itself, so a natural answer is never found. This gave 109 misses. It is why Q1, Q2, Q5, Q7 and Q8 have no result at all, right or wrong.
4. **Hindi and Hinglish cannot match.** The 8 questions exist only in English. Devanagari and Roman-Hindi answers share no words with them. The only Hindi-side "matches" were English words inside the doctor's own question ("Decisions mein kaun involve ho?").
5. **Short answers can never match.** "My dog.", "No.", "Mrs D'Souza." and "Nothing." carry the answer but share no words with the question.
6. **Leaving an evasive or skipped topic open is not a skill here.** 29 of the 30 unanswered cells were correctly left open, but only because the matcher finds almost nothing anywhere. One deflected question (C13 Q4) was still marked "Found", with the doctor's own question.
7. **Top-3 does not help.** No right line appeared in any of the three suggestions for any question.

## Limits of this test (be fair to the matcher)

- **The conversations are invented** and were written by an AI agent that had read the matcher code first. That is a possible bias, either way. A clinician has not reviewed the conversations or the ground truth [unverified clinically].
- **The text is clean typed text** with the speaker already given. Real speech-to-text on a phone would be noisier and would probably do worse, not better.
- **Only the standard 8 questions were tested.** A doctor who writes their own questions, or a note with earlier "still to discuss" lines, gives different question words. With natural answers, the result is likely similar.
- **20 conversations is a small set.** The direction of the result is clear, but the exact percentages are not precise.
- **This tests the goals-question matcher only.** The other word rules, `LocalDraftSuggestions` (cue words per note section), were not scored here.

## What this means for the pitch (honest wording)

- **Do not say** "the app finds the answers in the conversation" or "AI fills the goals of care". On this test, it found none.
- **Say instead:** "The doctor records the conversation and goes through a checklist of the goals questions. The doctor types or picks each answer. The family then confirms the exact note. Automatic answer-finding is not working yet: a simple word-matching version found none of 130 answers in our 20 test conversations, so we do not rely on it."
- The existing advice in `HANDOFF.md` (item A10/A19/B6) to say "word matching" is not enough. Word matching is there, but it does not find answers, so the pitch should not suggest that it does.
- **Safer for a demo:** the pre-filled text and the "Found in the conversation" label can show the doctor's own question as an answer. In a live demo, either use typed answers or have the convo lane change this first (see below).

## Note for the convo lane (they own the matching code; nothing was changed here)

Ideas only, not tested:
- Never offer a doctor line as an answer. The speaker tag is already in each line (`[Doctor · …]`).
- Look for the answer in the patient or family lines **after** the matched question, instead of in the question itself (pattern 2).
- Add Hindi wording for the 8 questions, or match on the question's position in the talk rather than on its words.
- Until one of these works, think about turning off the pre-fill and the "Found" label, and keeping only the checklist (covered / left open as the doctor marks it).
- Rerun `SimulatedGoalsConversationsTest` after any change. It prints this table again.

## Every miss and wrong cell (from the test run)

| Conversation | Question | Result | Right lines | Top suggestion |
|---|---|---|---|---|
| C01 | Q1 | miss | 4 | nothing found |
| C01 | Q2 | miss | 6 | nothing found |
| C01 | Q3 | wrong | 8 | L7 (score 6, words: like involved decisions care) Who would you like involved in decisions about your care? |
| C01 | Q4 | wrong | 11 | L10 (score 5, words: understanding current health) What is your understanding of your current health? |
| C01 | Q5 | miss | 13 | nothing found |
| C01 | Q6 | wrong | 15 | L14 (score 5, words: anything like talk covered) Is there anything you would like to talk about that we have not covered? |
| C01 | Q7 | miss | 18 | nothing found |
| C01 | Q8 | miss | 20 | nothing found |
| C02 | Q1 | miss | 4 | nothing found |
| C02 | Q2 | miss | 6,8 | nothing found |
| C02 | Q3 | miss | 12 | nothing found |
| C02 | Q4 | miss | 10 | nothing found |
| C02 | Q5 | miss | 14 | nothing found |
| C02 | Q7 | miss | 16,17 | nothing found |
| C03 | Q1 | miss | 4,5 | nothing found |
| C03 | Q2 | miss | 7,8 | nothing found |
| C03 | Q3 | miss | 10 | nothing found |
| C03 | Q4 | miss | 12 | nothing found |
| C03 | Q6 | miss | 17 | nothing found |
| C03 | Q7 | miss | 20 | nothing found |
| C04 | Q1 | miss | 4 | nothing found |
| C04 | Q2 | miss | 6 | nothing found |
| C04 | Q3 | miss | 10,11 | nothing found |
| C04 | Q4 | miss | 8 | nothing found |
| C04 | Q5 | miss | 13 | nothing found |
| C04 | Q7 | miss | 15 | nothing found |
| C05 | Q1 | miss | 4,6 | nothing found |
| C05 | Q2 | miss | 8 | nothing found |
| C05 | Q3 | wrong | 10,12 | L9 (score 5, words: like involved decisions) Who would you like involved in decisions? |
| C05 | Q4 | miss | 14 | nothing found |
| C05 | Q5 | miss | 16 | nothing found |
| C05 | Q6 | miss | 18 | nothing found |
| C05 | Q7 | miss | 20 | nothing found |
| C05 | Q8 | miss | 22 | nothing found |
| C06 | Q1 | miss | 4 | nothing found |
| C06 | Q2 | miss | 7,8 | nothing found |
| C06 | Q3 | wrong | 11 | L10 (score 6, words: like involved decisions care) Who would you like involved in decisions about your care? |
| C06 | Q4 | wrong | 13 | L12 (score 5, words: understanding current health) What is your understanding of your current health? |
| C06 | Q5 | miss | 15,16 | nothing found |
| C06 | Q6 | wrong | 18 | L17 (score 5, words: anything like talk covered) Is there anything you would like to talk about that we have not covered? |
| C06 | Q7 | miss | 21 | nothing found |
| C06 | Q8 | miss | 23 | nothing found |
| C07 | Q1 | miss | 4 | nothing found |
| C07 | Q2 | miss | 6,7 | nothing found |
| C07 | Q3 | miss | 9 | nothing found |
| C07 | Q4 | miss | 11 | nothing found |
| C07 | Q5 | miss | 15 | nothing found |
| C07 | Q7 | miss | 18 | nothing found |
| C08 | Q1 | miss | 4 | nothing found |
| C08 | Q2 | miss | 6,7 | nothing found |
| C08 | Q3 | wrong | 9 | L8 (score 6, words: like involved decisions care) Who would you like involved in decisions about your care? |
| C08 | Q4 | wrong | 12 | L11 (score 5, words: understanding current health) What is your understanding of your current health? |
| C08 | Q5 | miss | 14 | nothing found |
| C08 | Q6 | wrong | 16 | L14 (score 5, words: anything) Being trapped. Awake and unable to say anything. |
| C08 | Q7 | miss | 17,19 | nothing found |
| C08 | Q8 | miss | 21 | nothing found |
| C09 | Q1 | miss | 4 | nothing found |
| C09 | Q2 | miss | 7 | nothing found |
| C09 | Q3 | miss | 11 | nothing found |
| C09 | Q4 | miss | 9 | nothing found |
| C09 | Q6 | miss | 15 | nothing found |
| C09 | Q7 | miss | 16 | nothing found |
| C10 | Q1 | miss | 4 | nothing found |
| C10 | Q2 | miss | 6 | nothing found |
| C10 | Q3 | wrong | 13 | L12 (score 5, words: like involved decisions) Who would you like involved in this decision? |
| C10 | Q4 | miss | 10 | nothing found |
| C10 | Q5 | miss | 15 | nothing found |
| C10 | Q7 | miss | 17,18 | nothing found |
| C10 | Q8 | miss | 20 | nothing found |
| C11 | Q1 | miss | 4 | nothing found |
| C11 | Q2 | miss | 6 | nothing found |
| C11 | Q3 | miss | 8 | nothing found |
| C11 | Q4 | miss | 10 | nothing found |
| C11 | Q5 | miss | 12 | nothing found |
| C11 | Q6 | miss | 14 | nothing found |
| C11 | Q7 | miss | 17 | nothing found |
| C11 | Q8 | miss | 19 | nothing found |
| C12 | Q1 | miss | 4,5 | nothing found |
| C12 | Q2 | miss | 7 | nothing found |
| C12 | Q3 | wrong | 9,10 | L8 (score 5, words: involved decisions care) Who should be involved in decisions about her care? |
| C12 | Q4 | miss | 13 | nothing found |
| C12 | Q5 | miss | 15 | nothing found |
| C12 | Q7 | miss | 17,18 | nothing found |
| C13 | Q1 | miss | 4 | nothing found |
| C13 | Q4 | wrong* | none | L7 (score 5, words: understanding current health) Fair. What is your understanding of your current health? |
| C13 | Q6 | miss | 13 | nothing found |
| C13 | Q7 | miss | 14 | nothing found |
| C14 | Q1 | miss | 2 | nothing found |
| C14 | Q2 | miss | 4 | nothing found |
| C14 | Q3 | wrong | 6 | L5 (score 4, words: involved decisions) Decisions में आप किसको involve करना चाहेंगी? |
| C14 | Q4 | miss | 8 | nothing found |
| C14 | Q5 | miss | 10 | nothing found |
| C14 | Q7 | miss | 13 | nothing found |
| C15 | Q1 | miss | 4,5 | nothing found |
| C15 | Q2 | miss | 7,8 | nothing found |
| C15 | Q3 | wrong | 10 | L9 (score 6, words: like involved decisions care) Who would you like involved in decisions about your care? |
| C15 | Q4 | miss | 12,13 | nothing found |
| C15 | Q5 | miss | 15 | nothing found |
| C15 | Q6 | miss | 18 | nothing found |
| C15 | Q7 | miss | 21 | nothing found |
| C15 | Q8 | miss | 23 | nothing found |
| C16 | Q1 | miss | 2 | nothing found |
| C16 | Q2 | miss | 4 | nothing found |
| C16 | Q3 | wrong | 9 | L8 (score 6, words: involved decisions) Decisions mein kaun involve ho? |
| C16 | Q4 | miss | 6 | nothing found |
| C16 | Q5 | miss | 11 | nothing found |
| C16 | Q7 | miss | 13 | nothing found |
| C16 | Q8 | miss | 15 | nothing found |
| C17 | Q1 | miss | 3 | nothing found |
| C17 | Q2 | miss | 5 | nothing found |
| C17 | Q3 | wrong | 7 | L6 (score 6, words: like involved decisions care) Who would you like involved in decisions about your care? |
| C17 | Q4 | wrong | 9 | L8 (score 5, words: understanding current health) What is your understanding of your current health? |
| C17 | Q5 | miss | 11,12 | nothing found |
| C17 | Q6 | wrong | 14 | L13 (score 5, words: anything like talk covered) Is there anything you would like to talk about that we have not covered? |
| C17 | Q7 | miss | 15,17 | nothing found |
| C17 | Q8 | miss | 19 | nothing found |
| C18 | Q1 | miss | 4 | nothing found |
| C18 | Q7 | miss | 8,9 | nothing found |
| C19 | Q1 | miss | 4 | nothing found |
| C19 | Q2 | miss | 6 | nothing found |
| C19 | Q4 | miss | 8 | nothing found |
| C19 | Q5 | miss | 10 | nothing found |
| C19 | Q7 | miss | 13 | nothing found |
| C19 | Q8 | miss | 15 | nothing found |
| C20 | Q1 | miss | 6 | nothing found |
| C20 | Q2 | miss | 10 | nothing found |
| C20 | Q3 | wrong | 12 | L11 (score 6, words: like involved decisions care) Who would you like involved in decisions about your care? |
| C20 | Q4 | wrong | 14 | L13 (score 5, words: understanding current health) What is your understanding of your current health? |
| C20 | Q6 | wrong | 18 | L17 (score 5, words: anything like talk covered) Is there anything you would like to talk about that we have not covered? |
| C20 | Q7 | miss | 20 | nothing found |
| C20 | Q8 | miss | 22 | nothing found |

## After the matcher fix

27 October 2026, lane `sa5-matcher` (branched from the convo lane `sa3-convo`, with this test merged in). Still no AI, no network: fixed rules and word lists, on the phone, the same output for the same input.

### What changed

The matcher is `LocalQuestionMatcher.match` in `CareDraftAssist.kt`; the word lists are in `GoalsQuestionVocabulary.kt`.

1. **Find where the question was asked.** A line counts as asking when it has `?` or `？`. In plain speech-to-text with no punctuation, it counts when it starts with a question word or has a Hindi/Hinglish question word (क्या, कौन, kya, kaun …). The asked line must fit exactly one question best. "Fit" means the question's own words plus fixed wording for each of the 8 standard questions: English paraphrases, Hindi and Hinglish (for example "matters most", "सबसे ज़रूरी", "sabse zaroori"; "involve*", "फैसल*", "faisle"; "worr*", "चिंता", "chinta", "dar"). If two questions fit equally, the line counts for neither.
2. **Suggest the reply, not the question.** The suggestion is the first line after the question (within 3 lines) that does not itself ask something. The lines straight after it are added as 2nd and 3rd suggestions. Speaker labels are not needed. If they are there (`[Doctor · …]`, `D:`, `Patient:`), a doctor line is not taken as the reply, and the extra lines must come from the same speaker.
3. **Stop rather than guess.** The search for a reply ends when the next goals question is asked, or when the patient or family answers with a question of their own. If the reply puts the topic off, the question stays open and nothing is suggested. "Putting off" means a fixed list: "let's leave it", "not now", "God knows", "I don't know", "you know better", "you're the doctor", "पता नहीं", "बाद में बात", "आप जो ठीक समझें", "jo hona hai" …
4. **Never offer the question.** A line that asks something is never offered. Neither is a line that is mostly the question's own words (70% or more of 3+ words), unless it is labelled as the patient or family.
5. **Old rule only as a last resort.** The old shared-words rule is used only when no reply was found. It never uses a line that is already another question's reply.
6. **Concept words** (for example "home, hospital, घर, अस्पताल" for next steps; "ventilator, CPR, machine, मशीन" for hopes) only raise the confidence shown for a reply that was already found. They never pick a line on their own. That was not tried, to keep wrong suggestions down.
7. **The screen stays honest.** The card says "Suggested from the transcript · found by matching words · line N". Under it, it says why the line was picked ("The reply after the question on line M" or "This line shares words with the question"), in English and Hindi. Nothing is saved until the doctor presses Accept (or edits it).

Every Hindi and Hinglish phrase in `GoalsQuestionVocabulary.kt` was written by the lane, not by a clinician or a native-speaker reviewer **[needs clinician review]**.

### Before and after (same 20 conversations, same scoring, speaker labels as in the files)

| Question | Answered | Before: hit / miss / wrong / wrong\* | After: hit / miss / wrong / wrong\* | After: left open (right) |
|---|---|---|---|---|
| Q1 What matters most | 20 | 0 / 20 / 0 / 0 | 19 / 1 / 0 / 0 | 0 |
| Q2 Hoping for from care | 18 | 0 / 18 / 0 / 0 | 15 / 3 / 0 / 1 | 1 |
| Q3 Who is involved in decisions | 17 | 0 / 6 / 11 / 0 | 17 / 0 / 0 / 0 | 3 |
| Q4 Understanding of health | 18 | 0 / 13 / 5 / 1 | 17 / 1 / 0 / 0 | 2 |
| Q5 Worries about the future | 15 | 0 / 15 / 0 / 0 | 12 / 3 / 0 / 0 | 5 |
| Q6 Anything not covered | 11 | 0 / 6 / 5 / 0 | 3 / 8 / 0 / 0 | 9 |
| Q7 What happens next | 20 | 0 / 20 / 0 / 0 | 16 / 4 / 0 / 0 | 0 |
| Q8 Who should hear | 11 | 0 / 11 / 0 / 0 | 11 / 0 / 0 / 0 | 9 |
| **All** | **130** | **0 / 109 / 21 / 1** | **110 / 20 / 0 / 1** | **29 of 30** |

| Language | Answered | Before: hit / miss / wrong / wrong\* | After: hit / miss / wrong / wrong\* | After: recall |
|---|---|---|---|---|
| English | 79 | 0 / 60 / 19 / 1 | 67 / 12 / 0 / 1 | 85% |
| Hinglish, Roman script | 27 | 0 / 26 / 1 / 0 | 24 / 3 / 0 / 0 | 89% |
| Hindi, Devanagari | 18 | 0 / 18 / 0 / 0 | 13 / 5 / 0 / 0 | 72% |
| Hindi with English words, Devanagari | 6 | 0 / 5 / 1 / 0 | 6 / 0 / 0 / 0 | 100% |

- **Precision** (right ÷ everything suggested): 99% (110/111). **Recall** (right ÷ answered): 85% (110/130). Top-3 recall is also 85%: when the first suggestion is not right, nothing is suggested at all.
- **Without speaker labels:** the test now also strips the `[Doctor · …]` labels and runs again. The numbers are exactly the same: 110 / 20 / 0 / 1. In these files the doctor's follow-up lines rarely fall between a question and its answer. Real transcripts may differ.
- **The one wrong suggestion** (C13 Q2) is sarcasm: "That you stop asking difficult questions!", given as the reply to "What are you hoping for from your care?". The hand-marking counts it as not answered.

### Per conversation, after

| Conversation | Language | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Q8 |
|---|---|---|---|---|---|---|---|---|---|
| C01 | English | hit | hit | hit | hit | hit | hit | hit | hit |
| C02 | English | hit | miss | hit | miss | hit | — | miss | — |
| C03 | Hinglish, Roman script | hit | hit | hit | hit | — | miss | hit | — |
| C04 | Hindi, Devanagari | hit | hit | hit | hit | hit | — | miss | — |
| C05 | English | hit | hit | hit | hit | hit | hit | hit | hit |
| C06 | English | hit | hit | hit | hit | hit | miss | hit | hit |
| C07 | Hinglish, Roman script | hit | hit | hit | hit | miss | — | hit | — |
| C08 | English | hit | hit | hit | hit | hit | miss | hit | hit |
| C09 | Hindi, Devanagari | hit | miss | hit | hit | — | miss | miss | — |
| C10 | English | hit | hit | hit | hit | hit | — | hit | hit |
| C11 | Hinglish, Roman script | hit | hit | hit | hit | hit | miss | hit | hit |
| C12 | English | hit | hit | hit | hit | hit | — | hit | — |
| C13 | English | hit | wrong* | — | — | — | miss | miss | — |
| C14 | Hindi with English words, Devanagari | hit | hit | hit | hit | hit | — | hit | — |
| C15 | English | hit | hit | hit | hit | hit | miss | hit | hit |
| C16 | Hinglish, Roman script | hit | hit | hit | hit | hit | — | hit | hit |
| C17 | English | hit | hit | hit | hit | miss | miss | hit | hit |
| C18 | English | hit | — | — | — | — | — | hit | — |
| C19 | Hindi, Devanagari | hit | hit | — | hit | miss | — | hit | hit |
| C20 | English | miss | miss | hit | hit | — | hit | hit | hit |

### What it still misses, and why (20 misses)

- **Q6, 8 misses: the answer is itself a question.** Examples: "When can I go home?", "Is there any government scheme that covers this drug?". The rule is never to offer a line that asks something, so these are left for the doctor. That is by design. If the clinical team wants a patient's own question offered for Q6 only, it is a small change, but it would also let through clarifying questions such as "Mean what?" [untested].
- **Q7, 4 misses: the plan is said by the doctor, and no "what next" question was asked.** Example: "अगली बार हम दो हफ्ते बाद मिलेंगे". Word matching cannot tell a plan from other doctor talk without guessing.
- **Put off first, answered later (C20 Q1, C09 Q2).** "God knows" or "आप जो ठीक समझें" comes first, and the family then prompts an answer. The rule closes the question at the first put-off. Letting it look further gave +1 hit and +1 wrong, so it was not kept.
- **Paraphrases with no listed wording (C02 Q2, Q4).** Examples: "what would you like it to do for you", "How do you see your lungs". To find these, the list would have to be written around this test.
- **A reply split by a question (C17 Q5).** The family's line "Money. … how do we pay for the medicines?" ends with a question, so the search stops before the patient's "Yes, the costs".

### Be fair: limits of these numbers

- **Not a clean test.** The lane read C01, C03 and C04 before writing the word lists. After the first run, it read the conversations behind the wrong and missed cells and added rules:
  - single-word "hope*" / "expect*" and "next kya";
  - the "you know better / you're the doctor / jo hona hai" put-offs;
  - "a patient's question ends the reply".
- **The first run, before those additions,** gave 106 hit / 21 miss / 3 wrong / 4 wrong\*. That is the less-tuned figure. On new conversations, expect something between the two runs, or worse.
- **Same caveats as above.** The conversations are invented, clean typed text, and the ground truth has not been reviewed by a clinician. 20 conversations is small.
- **Real speech-to-text will be harder.** It may lose question marks; the rule then falls back on question words, which is less reliable. It may also merge or split lines, and mis-hear Hindi.
- **Only the 8 standard questions get the extra wording.** A question the doctor types gets only its own words, and the reply-after-the-question rule.

### Honest pitch wording, updated

- **Say:** "While the doctor talks, Sahara finds where each goals question was asked and suggests the reply that followed. The doctor accepts or edits every answer. It uses fixed word rules on the phone, not AI. In our 20 invented test conversations, it suggested a right line for 110 of 130 answers and a wrong line once. It leaves a question open rather than guess."
- **Do not say** "AI fills the goals of care", or that it understands the conversation. It does not; it matches words and positions.
- **Do not quote 85% as real-world accuracy.** It is from synthetic, partly-seen conversations.

### How this was run

`./gradlew --no-daemon testDebugUnitTest` ran once, under the gradle lock, on 27 Oct 2026. It passed (380 tests, 0 failures), including `SimulatedGoalsConversationsTest` (numbers above), the new `GoalsReplyMatcherTest` and the existing matcher tests.

`SimulatedGoalsConversationsTest` was changed only to compile against the convo lane: the convo lane shows Suggested/Open and needs Accept, with no automatic pre-fill. It also gained the no-labels run. Its scoring and the conversations are unchanged.
