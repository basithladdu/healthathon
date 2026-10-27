# Simulated goals-of-care conversations: how well does the app's word matching find the answers?

27 October 2026 · HANDOFF item **B17** ("Run 20 simulated conversations with different people and note the results").

**Short answer: on these 20 invented conversations, the app found none of the 130 answers. It pre-filled a wrong line 22 times, and 21 of those 22 were the doctor's own question.**

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
