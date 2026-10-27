package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The goals matcher suggests the reply after a recognised question, never the question itself. Synthetic words only. */
class GoalsReplyMatcherTest {
    private val standard = DOCTOR_STANDARD_QUESTIONS.flatten().mapIndexed { i, t -> CareQuestion("q${i + 1}", t, 0) }
    private fun top(source: String, q: Int) = LocalQuestionMatcher.match(source, standard).forQuestion("q$q")!!.matches.firstOrNull()

    @Test fun replyAfterTheQuestionIsSuggestedNotTheQuestion() {
        val m = top("What matters most to you at the moment?\nBeing at home with my dog.", 1)!!
        assertEquals(2, m.line.number); assertEquals(1, m.askedOnLine)
        assertEquals("Being at home with my dog.", m.quote)
        // Every standard question read word for word, with no answer after it, offers nothing.
        standard.forEachIndexed { i, q -> assertNull(q.text, top(q.text, i + 1)) }
    }

    @Test fun shortAnswersNeedNoSharedWords() {
        assertEquals(2, top("Who should hear about what we have agreed?\nMy sister.", 8)!!.line.number)
        assertEquals(2, top("Who would you like involved in decisions about your care?\nRavi.", 3)!!.line.number)
    }

    @Test fun hindiAndHinglishWordingIsRecognised() {
        assertEquals(2, top("इस समय आपके लिए सबसे ज़रूरी क्या है?\nबेटी की शादी देखना।", 1)!!.line.number)
        // the same words without the nukta
        assertEquals(2, top("इस समय आपके लिए सबसे जरूरी क्या है?\nबेटी की शादी देखना।", 1)!!.line.number)
        assertEquals(2, top("Aage ke baare mein sabse zyada kis baat ki chinta hai?\nKi dard badh jaaye.", 5)!!.line.number)
        // plain speech-to-text with no punctuation: "kya" marks the question
        assertEquals(2, top("treatment se aap kya umeed rakhte hain\nbas dard kam rahe", 2)!!.line.number)
    }

    @Test fun speakerLabelsAreUsedWhenPresent() {
        val m = top("D: What matters most to you at the moment?\nD: Take your time.\nP: My garden.", 1)!!
        assertEquals(3, m.line.number)
        assertEquals(3, top("[Doctor · English · t] What worries you most about the future?\n[Doctor · English · t] Take your time.\n[Patient · English · t] Being a burden.", 5)!!.line.number)
    }

    @Test fun puttingTheTopicOffLeavesItOpen() {
        assertNull(top("What worries you most about the future?\nLet's leave it for now.\nThe tea is ready.", 5))
        assertNull(top("What are you hoping for from your care?\nWhatever is best, doctor. You know better.", 2))
        assertNull(top("आगे के बारे में सबसे ज़्यादा किस बात की चिंता है?\nपता नहीं।", 5))
    }

    @Test fun aQuestionBackOrTheNextQuestionEndsTheSearch() {
        assertNull(top("Is there anything else you would like to talk about?\nWhen can I go home?\nOkay.", 6))
        val source = "What matters most to you at the moment?\nWhat are you hoping for from your care?\nTo be comfortable."
        assertNull(top(source, 1))
        assertEquals(3, top(source, 2)!!.line.number)
    }

    @Test fun aRestatedQuestionIsNeverOffered() {
        assertNull(top("What is your understanding of your current health?\nYour understanding of your current health, yes.", 4))
    }

    @Test fun noteSaysWhyTheLineWasSuggested() {
        val m = top("What matters most to you at the moment?\nMy dog.", 1)!!
        assertTrue(transcriptSuggestionNote(m).startsWith("The reply after the question on line 1."))
        assertTrue(transcriptSuggestionNote(m).contains("पंक्ति 1"))
        assertTrue(transcriptSuggestionNote(m.copy(askedOnLine = null)).startsWith("This line shares words with the question."))
        assertTrue(transcriptChecklistLabel(TranscriptGoalStatus.Suggested).contains("found by matching words"))
    }
}
