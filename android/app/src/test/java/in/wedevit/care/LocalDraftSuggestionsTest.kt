package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class LocalDraftSuggestionsTest {
    private val english = listOf(
        "[Patient · English · 16 Oct 14:05] I do not want to be in hospital, I want to be at home.",
        "[Family / carer · English · 16 Oct 14:06] This is my daughter Meera, she came with me.",
        "[Doctor · English · 16 Oct 14:07] We discussed the scan and the dialysis treatment.",
        "[Patient · English · 16 Oct 14:08] Can you explain what will happen next?",
        "[Patient · English · 16 Oct 14:09] I am not sure and still thinking about the operation.",
        "[Doctor · English · 16 Oct 14:10] We will call you before the next visit.",
    ).joinToString("\n")
    private val mixed = "[Patient · हिन्दी · 16 Oct 14:05] मैं चाहती हूँ कि मैं घर पर रहूँ।\n[Family / carer · हिन्दी · 16 Oct 14:06] मेरी बेटी सुनीता मेरे साथ आई है।\n[Patient · हिन्दी · 16 Oct 14:07] मुझे पता नहीं, अभी तय नहीं है।\n[Doctor · हिन्दी · 16 Oct 14:08] अगली मुलाकात से पहले हम फोन करेंगे।"

    private fun all(source: String) = LocalDraftSuggestions.suggest(source).bySection.flatten()
    private fun lineNo(source: String, section: Int) = LocalDraftSuggestions.suggest(source).bySection[section].map { it.line.number }

    @Test fun everySuggestionIsVerbatimAndNeedsReview() {
        for (source in listOf(english, mixed)) all(source).forEach {
            assertEquals(it.quote, source.substring(it.charStart, it.charEnd))
            assertTrue(it.needsHumanReview)
            assertTrue(it.line.text.endsWith(it.quote))
        }
    }

    @Test fun byteOffsetsMatchUtf8ForDevanagariAndMixedScript() {
        val bytes = mixed.toByteArray(Charsets.UTF_8)
        val found = all(mixed); assertTrue(found.isNotEmpty())
        found.forEach {
            assertEquals(it.quote, String(bytes, it.start, it.end - it.start, Charsets.UTF_8))
            assertEquals(it.start + it.quote.toByteArray(Charsets.UTF_8).size, it.end)
        }
        assertTrue(found.any { it.start != it.charStart })
    }

    @Test fun eachSectionMatchesExpectedLines() {
        assertEquals(listOf(1), lineNo(english, 0)); assertEquals(listOf(2), lineNo(english, 1))
        assertEquals(listOf(3), lineNo(english, 2)); assertTrue(lineNo(english, 3).containsAll(listOf(4, 5)))
        assertTrue(6 in lineNo(english, 4))
        assertEquals(listOf(1), lineNo(mixed, 0)); assertEquals(listOf(2), lineNo(mixed, 1))
        assertEquals(listOf(3), lineNo(mixed, 3)); assertEquals(listOf(4), lineNo(mixed, 4))
    }

    @Test fun emptyOrUnrelatedTextGivesNothing() {
        assertTrue(LocalDraftSuggestions.suggest("").isEmpty)
        assertTrue(LocalDraftSuggestions.suggest("   \n\n").isEmpty)
        assertTrue(LocalDraftSuggestions.suggest("Hello.\nThe weather is warm today.\nOkay.").isEmpty)
        assertEquals(5, LocalDraftSuggestions.suggest("").bySection.size)
    }

    @Test fun ranksAreOrderedLimitedAndDeterministic() {
        assertTrue(LocalDraftSuggestions.suggest(english, 1).bySection.all { it.size <= 1 })
        val a = LocalDraftSuggestions.suggest(english); assertEquals(a, LocalDraftSuggestions.suggest(english))
        a.bySection.forEach { s -> assertEquals((1..s.size).toList(), s.map { it.rank }); assertEquals(s.map { it.score }.sortedDescending(), s.map { it.score }) }
    }

    @Test fun acceptUsesExistingLinkedAssignAndRejectsChangedSource() {
        val s = LocalDraftSuggestions.suggest(english).bySection[1].first()
        val updated = LocalDraftSuggestions.accept(Conversation("p", source = english), s)
        assertEquals(s.quote, updated.participants)
        assertTrue(CareDraftAssist.unlinked(english, updated.participants).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { LocalDraftSuggestions.accept(Conversation("p", source = "changed"), s) }
    }

    private val qa = listOf(
        "[Doctor · English · 16 Oct 14:05] Does he want to stay at home if he becomes very ill?",
        "[Family / carer · English · 16 Oct 14:06] Yes, he wants to stay at home with us.",
        "[Doctor · English · 16 Oct 14:07] Who holds the power of attorney if he cannot decide?",
        "[Family / carer · English · 16 Oct 14:08] His daughter Meera holds the power of attorney.",
        "[Doctor · English · 16 Oct 14:09] The weather is warm today.",
    ).joinToString("\n")
    private val qHome = CareQuestion("q1", "Does he want to stay at home?", 0)
    private val qDecider = CareQuestion("q2", "Who holds the power of attorney if he cannot decide?", 1)
    private val qTube = CareQuestion("q3", "Does he want a feeding tube?", 2)
    private fun ask(source: String, vararg q: CareQuestion) = LocalQuestionMatcher.match(source, q.toList())

    @Test fun questionMatchesTheLineThatAnswersIt() {
        val r = ask(qa, qHome, qDecider)
        assertEquals(2, r.forQuestion("q1")!!.matches.first().line.number)
        assertEquals(4, r.forQuestion("q2")!!.matches.first().line.number)
        r.answered.flatMap { it.matches }.forEach { assertTrue(it.needsHumanReview); assertEquals(it.quote, qa.substring(it.charStart, it.charEnd)); assertTrue(it.line.text.endsWith(it.quote)) }
        assertEquals((1..r.forQuestion("q1")!!.matches.size).toList(), r.forQuestion("q1")!!.matches.map { it.rank })
    }

    @Test fun unansweredQuestionIsReportedNotMatchedToNoise() {
        val r = ask(qa, qHome, qTube)
        assertTrue(r.forQuestion("q3")!!.isUnanswered)
        assertEquals(listOf(qTube), r.unanswered); assertEquals(listOf(qTube), LocalQuestionMatcher.unanswered(qa, listOf(qHome, qTube)))
        assertEquals(listOf(qHome.id), r.answered.map { it.question.id })
        assertTrue(ask("", qHome).unanswered.contains(qHome))
    }

    @Test fun stopwordsAloneNeverMatch() {
        val source = "[Patient · English · t] What is the of and to it, yes okay.\n[Patient · हिन्दी · t] क्या है और में से यह नहीं।"
        val r = ask(source, CareQuestion("a", "What is the of and to?", 0), CareQuestion("b", "क्या है और में?", 0), CareQuestion("c", "", 0))
        assertEquals(3, r.unanswered.size)
    }

    @Test fun lineAfterAQuestionIsBoosted() {
        val q = CareQuestion("p", "pharmacy hours", 4)
        val plain = "[Doctor · English · t] The pharmacy is closed on Sundays."
        val boosted = "[Doctor · English · t] Any questions about the pharmacy?\n[Doctor · English · t] The pharmacy is closed on Sundays."
        assertTrue(ask(plain, q).forQuestion("p")!!.isUnanswered)
        val m = ask(boosted, q).forQuestion("p")!!.matches
        assertEquals(listOf(2), m.map { it.line.number }); assertTrue(m.first().followsQuestion)
        assertEquals(LocalQuestionMatcher.LONG_WORD_WEIGHT + LocalQuestionMatcher.FOLLOWS_QUESTION_BOOST, m.first().score)
        // the boost alone, without shared words, never matches
        assertTrue(ask("[Doctor · English · t] Any questions?\n[Patient · English · t] Nothing today.", q).forQuestion("p")!!.isUnanswered)
    }

    @Test fun questionByteOffsetsAreCorrectForDevanagariAndTelugu() {
        val source = "[Doctor · हिन्दी · 16 Oct 14:05] क्या वे अस्पताल में रहना चाहते हैं?\n[Family / carer · हिन्दी · 16 Oct 14:06] नहीं, वे घर पर रहना चाहते हैं, अस्पताल नहीं।\n[Patient · తెలుగు · 16 Oct 14:07] నేను ఇంట్లో ఉండాలనుకుంటున్నాను"
        val r = ask(source, CareQuestion("h", "वे घर पर रहना चाहते हैं या अस्पताल में?", 0), CareQuestion("t", "ఇంట్లో ఉండాలనుకుంటున్నాను", 0))
        val bytes = source.toByteArray(Charsets.UTF_8)
        val hindi = r.forQuestion("h")!!.matches; val telugu = r.forQuestion("t")!!.matches
        assertEquals(listOf(2), hindi.map { it.line.number }); assertEquals(listOf(3), telugu.map { it.line.number })
        (hindi + telugu).forEach {
            assertEquals(it.quote, String(bytes, it.start, it.end - it.start, Charsets.UTF_8))
            assertEquals(it.start + it.quote.toByteArray(Charsets.UTF_8).size, it.end)
            assertTrue(it.start != it.charStart)
        }
    }

    @Test fun questionMatchingIsDeterministicAndTiesKeepSourceOrder() {
        val a = ask(qa, qHome, qDecider, qTube); assertEquals(a, ask(qa, qHome, qDecider, qTube))
        val tie = "[Patient · English · t] The hospital visit was long.\n[Patient · English · t] The hospital visit was long."
        val m = ask(tie, CareQuestion("t", "hospital visit", 2)).answers.first().matches
        assertEquals(listOf(1, 2), m.map { it.line.number }); assertEquals(listOf(1, 2), m.map { it.rank })
        assertThrows(IllegalArgumentException::class.java) { LocalQuestionMatcher.match(tie, listOf(qHome), 0) }
    }
}
