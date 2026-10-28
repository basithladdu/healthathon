package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSourceLinesTest {
    private val source = """
        Doctor: What matters most to your father now?
        Son: He wants to stay at home, not go back to the ICU.
        Doctor: And if his breathing gets worse?
        Son: No ventilator. He said that clearly last month.
        Doctor: Thank you.
    """.trimIndent()

    @Test fun answerTakenFromALineShowsThatLine() {
        assertEquals(listOf("Son: No ventilator. He said that clearly last month."), NoteSourceLines.find("No ventilator. He said that clearly last month.", source))
    }

    @Test fun rewordedAnswerFindsTheLineSharingMostWords() {
        assertEquals(listOf("Son: He wants to stay at home, not go back to the ICU."), NoteSourceLines.find("Wants to stay at home and avoid the ICU", source))
    }

    @Test fun blankOrUnrelatedAnswerShowsNothing() {
        assertEquals(emptyList<String>(), NoteSourceLines.find("", source))
        assertEquals(emptyList<String>(), NoteSourceLines.find("Daughter will manage the medicines", source))
        assertEquals(emptyList<String>(), NoteSourceLines.find("No ventilator", ""))
    }

    @Test fun neverMoreThanTwoLines() {
        val many = (1..5).joinToString("\n") { "Son: stay at home please, line $it" }
        assertEquals(2, NoteSourceLines.find("stay at home please", many).size)
    }
}
