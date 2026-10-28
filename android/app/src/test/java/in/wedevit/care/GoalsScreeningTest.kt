package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class GoalsScreeningTest {
    private val tool = SurpriseQuestionEcog
    private fun suggest(vararg answers: Pair<String, String>) = tool.suggest(GoalsScreening.clean(tool, answers.toMap()))

    @Test fun nothingUntilTheSurpriseQuestionIsAnswered() {
        assertNull(suggest())
        assertNull(suggest(SurpriseQuestionEcog.ECOG to "4"))
    }

    @Test fun notSurprisedSuggestsOfferingTheConversation() {
        val s = suggest(SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.NOT_SURPRISED)!!
        assertTrue(s.consider)
        assertTrue(s.en.startsWith("Suggestion"))
        assertTrue(suggest(SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.NOT_SURPRISED, SurpriseQuestionEcog.ECOG to "3")!!.en.contains("ECOG 3"))
    }

    @Test fun surprisedNeverPushesTheConversation() {
        (listOf(null) + (0..4).map { "$it" }).forEach { ecog ->
            val answers = listOfNotNull(SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.SURPRISED, ecog?.let { SurpriseQuestionEcog.ECOG to it })
            val s = suggest(*answers.toTypedArray())!!
            assertFalse(s.consider)
            assertEquals(ecog != null && ecog.toInt() >= 3, s.en.contains("SPICT"))
        }
    }

    @Test fun ecogFiveAndUnknownValuesAreIgnored() {
        assertEquals(emptyMap<String, String>(), GoalsScreening.clean(tool, mapOf(SurpriseQuestionEcog.ECOG to "5", SurpriseQuestionEcog.SURPRISE to "maybe", "other" to "1")))
        assertFalse(suggest(SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.SURPRISED, SurpriseQuestionEcog.ECOG to "5")!!.en.contains("ECOG"))
    }

    @Test fun ecogScaleIsComplete0To5WithPublishedWording() {
        val options = tool.questions.first { it.id == SurpriseQuestionEcog.ECOG }.options
        assertEquals((0..5).map { "$it" }, options.map { it.value })
        assertEquals(listOf(true, true, true, true, true, false), options.map { it.selectable })
        assertEquals("0 · Fully active, able to carry on all pre-disease performance without restriction", options[0].en)
        assertEquals("4 · Completely disabled; cannot carry on any selfcare; totally confined to bed or chair", options[4].en)
    }

    @Test fun everyShownTextHasHindiAndSourcesAreCited() {
        tool.questions.forEach { q ->
            assertTrue(q.en, HindiScan.hasHindi(q.hi))
            q.options.forEach { assertTrue(it.en, HindiScan.hasHindi(it.hi)) }
            if (q.noteEn.isNotBlank()) assertTrue(HindiScan.hasHindi(q.noteHi))
        }
        listOf(tool.suggest(mapOf(SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.NOT_SURPRISED, SurpriseQuestionEcog.ECOG to "4")), tool.suggest(mapOf(SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.SURPRISED, SurpriseQuestionEcog.ECOG to "3")), tool.suggest(mapOf(SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.SURPRISED)))
            .forEach { assertTrue(HindiScan.hasHindi(it!!.hi)) }
        assertTrue(tool.sources.any { it.contains("Oken") && it.contains("1982") })
        assertTrue(tool.sources.any { it.contains("Gold Standards Framework") })
    }

    @Test fun toolIsPluggable() {
        val doctorsTool = object : GoalsScreeningTool {
            override val id = "doctors-tool"; override val titleEn = "Clinic tool"; override val titleHi = "क्लिनिक का तरीका"
            override val questions = listOf(ScreeningQuestion("q", "Q", "स", listOf(ScreeningOption("y", "Yes", "हाँ"))))
            override val sources = listOf("Clinic")
            override fun suggest(answers: Map<String, String>) = if (answers["q"] == "y") ScreeningSuggestion("Offer", "पेशकश", true) else null
        }
        assertEquals(mapOf("q" to "y"), GoalsScreening.clean(doctorsTool, mapOf("q" to "y", SurpriseQuestionEcog.SURPRISE to SurpriseQuestionEcog.NOT_SURPRISED)))
        assertTrue(doctorsTool.suggest(mapOf("q" to "y"))!!.consider)
        assertSame(SurpriseQuestionEcog, GoalsScreening.active)
    }
}
