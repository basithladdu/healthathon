package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareDraftAssistTest {
    @Test fun assigningWordsRetainsNegationAndLinksToOriginalSpeaker() {
        val source = "[Patient · हिन्दी · 16 Oct 14:05] I do not want to decide yet.\n[Doctor · English · 16 Oct 14:06] We can talk again on Monday."
        val draft = Conversation("person", source = source)
        val line = CareDraftAssist.lines(source).first()
        val result = CareDraftAssist.assign(draft, 3, line)
        assertEquals(source, result.source)
        assertEquals("I do not want to decide yet.", result.questions)
        val reference = CareDraftAssist.references(source, result.questions).single()
        assertEquals(1, reference.line.number)
        assertEquals(line.text, source.substring(reference.line.start, reference.line.end))
        assertTrue(reference.line.text.startsWith("[Patient · हिन्दी"))
    }

    @Test fun editedMeaningNeverReceivesFuzzySourceLink() {
        val source = "I do not want hospital care."
        assertTrue(CareDraftAssist.references(source, "I want hospital care.").isEmpty())
        assertTrue(CareDraftAssist.references(source, "want hospital care.").isEmpty())
        assertEquals(listOf("I want hospital care."), CareDraftAssist.unlinked(source, "I want hospital care."))
    }

    @Test fun repeatedWordsExposeBothSourcesWithoutInferringWhoSaidThem() {
        val source = "[Patient · English · 16 Oct 14:05] More time at home.\n[Doctor · English · 16 Oct 14:06] More time at home."
        val draft = Conversation("person", source = source)
        val doctorLine = CareDraftAssist.lines(source)[1]
        val result = CareDraftAssist.assign(draft, 0, doctorLine)
        assertEquals("More time at home.", result.priorities)
        val references = CareDraftAssist.references(source, result.priorities)
        assertEquals(listOf(1, 2), references.map { it.line.number })
        assertTrue(references[0].line.text.startsWith("[Patient ·"))
        assertTrue(references[1].line.text.startsWith("[Doctor ·"))
        assertTrue(CareDraftAssist.unlinked(source, result.priorities).isEmpty())
    }

    @Test fun referencesPreserveLineNumbersAcrossBlankLinesAndCrLf() {
        val source = "First line.\r\n\r\n  Ask on Monday.  \r\n"
        val reference = CareDraftAssist.references(source, "Ask on Monday.").single()
        assertEquals(3, reference.line.number)
        assertEquals("Ask on Monday.", source.substring(reference.line.start, reference.line.end))
    }

    @Test fun staleExcerptAndDuplicateAssignmentCannotCorruptDraft() {
        val draft = Conversation("person", source = "I prefer to stay home.")
        val line = CareDraftAssist.lines(draft.source).single()
        val assigned = CareDraftAssist.assign(draft, 0, line)
        assertEquals(assigned, CareDraftAssist.assign(assigned, 0, line))
        assertThrows(IllegalArgumentException::class.java) { CareDraftAssist.assign(draft.copy(source = "I changed my mind."), 0, line) }
        assertEquals("", assigned.topics)
    }

    @Test fun turnsAppendWithoutOverwritingEarlierSourceAndRequireSpeaker() {
        val result = CareDraftAssist.appendTranscript("Earlier words.", "Not ready\n to decide.", "Patient", "English", 0)
        assertTrue(result.startsWith("Earlier words.\n[Patient · English · "))
        assertTrue(result.endsWith("Not ready  to decide."))
        assertThrows(IllegalArgumentException::class.java) { CareDraftAssist.appendTranscript("", "Words", "Automatically identified", "English", 0) }
        assertThrows(IllegalArgumentException::class.java) { CareDraftAssist.appendTranscript("x".repeat(11999), "Too long", "Patient", "English", 0) }
    }

    @Test fun longReadAloudKeepsEveryWordAndFitsEngineLimit() {
        val words = (1..4000).map { "word$it" }
        val chunks = CareDraftAssist.speechChunks(words.joinToString(" "), 127)
        assertTrue(chunks.all { it.length <= 127 && it.isNotBlank() })
        assertEquals(words, chunks.joinToString(" ").split(" "))
        assertEquals(listOf("abc", "def", "ghi"), CareDraftAssist.speechChunks("abcdefghi", 3))
    }
}
