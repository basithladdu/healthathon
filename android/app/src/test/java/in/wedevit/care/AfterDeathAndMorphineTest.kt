package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AfterDeathAndMorphineTest {
    @Test fun guideHasEveryStepInEnglishAndHindi() {
        val ids = AfterDeathGuide.steps.map { it.id }
        assertEquals(listOf("check", "doctor", "first-day", "medicines", "register", "weeks", "you"), ids)
        AfterDeathGuide.steps.forEach { step ->
            assertTrue(step.title.isNotBlank() && step.titleHi.isNotBlank())
            assertTrue(step.points.isNotEmpty())
            step.points.forEach { (en, hi) -> assertTrue(en.isNotBlank()); assertTrue("Hindi missing for: $en", hi.any { it in 'ऀ'..'ॿ' }) }
        }
        val text = AfterDeathGuide.steps.flatMap { it.points.map { p -> p.first } }.joinToString(" ")
        assertTrue(text.contains("21 days")); assertTrue(text.contains("112")); assertTrue(text.contains("14416")); assertTrue(text.contains("Form 4A"))
        assertTrue(AfterDeathGuide.sources.size >= 5)
    }

    @Test fun morphineNamesAreRecognised() {
        listOf("Morphine 10 mg", "oral morphine solution", "Morcontin 30", "मॉर्फ़ीन", "Oramorph").forEach { assertTrue(it, MorphineSourceRules.isMorphine(it)) }
        listOf("Paracetamol", "Ondansetron", "Tramadol").forEach { assertFalse(it, MorphineSourceRules.isMorphine(it)) }
    }

    @Test fun aSourceNeedsACentreOrAPlace() {
        assertNotNull(MorphineSourceRules.error(MorphineSourceDraft("", "", "", "", "")))
        assertNull(MorphineSourceRules.error(MorphineSourceDraft("c1", "Pallium India", "Thiruvananthapuram", "Kerala")))
        assertNull(MorphineSourceRules.error(MorphineSourceDraft("", "", "", "", "Apollo Pharmacy, Guntur")))
        assertNotNull(MorphineSourceRules.error(MorphineSourceDraft("", "", "", "", "x".repeat(200))))
    }

    @Test fun searchFindsByNameOrPlace() {
        val centres = listOf(
            Centre("Pallium India", "Thiruvananthapuram", "Thiruvananthapuram", "", "", "", "unknown", "", id = "a", state = "Kerala"),
            Centre("AIIMS Mangalagiri Palliative", "Mangalagiri", "Guntur", "", "", "", "unknown", "", id = "b", state = "Andhra Pradesh"),
        )
        assertEquals(listOf("b"), MorphineSourceRules.search(centres, "guntur").map { it.id })
        assertEquals(listOf("a"), MorphineSourceRules.search(centres, "pallium").map { it.id })
        assertTrue(MorphineSourceRules.search(centres, "k").isEmpty())
    }

    @Test fun notSurprisedSuggestsTheConversation() {
        val tool = GoalsScreening.active
        val surprise = tool.questions.first()
        val notSurprised = surprise.options.first { it.selectable && tool.suggest(mapOf(surprise.id to it.value))?.consider == true }
        assertTrue(tool.suggest(mapOf(surprise.id to notSurprised.value))!!.en.contains("goals-of-care"))
    }
}
