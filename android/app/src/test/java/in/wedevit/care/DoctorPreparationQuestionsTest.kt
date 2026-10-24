package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class DoctorPreparationQuestionsTest {
    private fun q(id: String, section: Int = 0, answer: String = "", text: String = "Question $id") = DoctorPreparationQuestion(id, text, section, answer)
    private fun store(disk: MutableMap<String, String> = mutableMapOf()) = DoctorPreparationStore({ disk[it] }, { k, v -> disk[k] = v; true })

    @Test fun sectionAndAnswerRoundTrip() {
        val s = store(); val scope = DoctorPreparationStore.scope("account", "person", "discussion")
        val rows = listOf(q("1", 4, "Agreed to call her son."), q("2", 0))
        s.save(scope, rows)
        assertEquals(rows, s.load(scope))
    }
    @Test fun oldRowsWithoutSectionOrAnswerStillLoad() {
        val disk = mutableMapOf("s" to "[{\"id\":\"a\",\"text\":\"Who should join?\"},{\"id\":\"b\",\"text\":\"Odd\",\"section\":9}]")
        val loaded = store(disk).load("s")
        assertEquals(listOf(DoctorPreparationQuestion("a", "Who should join?", 3, ""), DoctorPreparationQuestion("b", "Odd", 3, "")), loaded)
    }
    @Test fun validationLimits() {
        fun bad(rows: List<DoctorPreparationQuestion>) = assertTrue(runCatching { DoctorPreparationStore.validate(rows) }.isFailure)
        DoctorPreparationStore.validate((1..20).map { q("$it") })
        DoctorPreparationStore.validate(listOf(q("1", 4, "a".repeat(2000))))
        bad((1..21).map { q("$it") })
        bad(listOf(q("1"), q("1")))
        bad(listOf(q("1", text = " ")))
        bad(listOf(q("1", text = "x".repeat(1001))))
        bad(listOf(q("1", section = 5)))
        bad(listOf(q("1", section = -1)))
        bad(listOf(q("1", answer = "a".repeat(2001))))
    }
    @Test fun newQuestionsDefaultToStillToDiscuss() {
        assertEquals(3, DOCTOR_PREPARATION_DEFAULT_SECTION)
        assertEquals(DOCTOR_PREPARATION_DEFAULT_SECTION, DoctorPreparationQuestion("n", "New one").section)
        val s = store(); s.save("s", (1..5).map { DoctorPreparationQuestion("$it", "Q$it") })
        assertEquals((1..5).map { "Q$it" }, s.load("s").map { it.text })
        assertTrue(s.load("s").all { it.section == DOCTOR_PREPARATION_DEFAULT_SECTION })
    }
    @Test fun scopeIsUnchanged() {
        assertEquals(DoctorPreparationStore.scope("a", "p", ""), DoctorPreparationStore.scope("a", "p", "legacy-current-draft"))
        assertNotEquals(DoctorPreparationStore.scope("a", "p", "x"), DoctorPreparationStore.scope("a", "p", "y"))
    }
    @Test fun unfinishedBusinessComesFirstAndSuggestionsAreDeterministic() {
        val prev = DoctorPreviousNote(whatMatters = "Being at home", stillToDiscuss = "- Who will care at night\n\n2) Is a hospice visit wanted?\nResuscitation wishes.")
        val first = doctorSuggestQuestions(prev, "Lives with daughter")
        assertEquals(first, doctorSuggestQuestions(prev, "Lives with daughter"))
        assertTrue(first[0].text.contains("Who will care at night"))
        assertEquals("Is a hospice visit wanted?", first[1].text)
        assertTrue(first[2].text.contains("Resuscitation wishes"))
        assertTrue(first.take(3).all { it.section == 3 })
        assertFalse(first.any { it.text in DOCTOR_STANDARD_QUESTIONS.flatten() })
        assertTrue(first.last().text.contains("Lives with daughter"))
        assertTrue(first.all { it.answer.isEmpty() })
        DoctorPreparationStore.validate(first)
    }
    @Test fun standardSetUsedOnlyWhenNothingLeftOpen() {
        val std = doctorSuggestQuestions(DoctorPreviousNote(stillToDiscuss = "  \n "), "")
        assertEquals(DOCTOR_STANDARD_QUESTIONS.flatten(), std.map { it.text })
        assertEquals(DOCTOR_STANDARD_QUESTIONS.flatMapIndexed { i, l -> l.map { i } }, std.map { it.section })
        assertEquals(std, doctorSuggestQuestions(null, ""))
        DoctorPreparationStore.validate(std)
    }
    @Test fun answeredAndUnansweredAreSplit() {
        val rows = listOf(q("1", answer = "Yes"), q("2"), q("3", answer = "   "))
        assertEquals(listOf("1"), doctorAnsweredQuestions(rows).map { it.id })
        assertEquals(listOf("2", "3"), doctorUnansweredQuestions(rows).map { it.id })
    }
}
