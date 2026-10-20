package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareTranslationRulesTest {
    @Test fun hindiAndEnglishNumbersMustRemainExact() {
        CareTranslationRules.validate("Follow-up २५ October, 10 mg", "अगली मुलाकात 25 अक्टूबर, १० mg")
        try { CareTranslationRules.validate("10 mg", "20 mg"); fail("Changed dose accepted") } catch (_: IllegalArgumentException) { }
        try { CareTranslationRules.validate("10 mg twice at 8", "10 mg"); fail("Lost time accepted") } catch (_: IllegalArgumentException) { }
        try { CareTranslationRules.validate("5 mg at 2 pm", "2 mg at 5 pm"); fail("Swapped numbers accepted") } catch (_: IllegalArgumentException) { }
        try { CareTranslationRules.validate("-5", "5"); fail("Lost sign accepted") } catch (_: IllegalArgumentException) { }
    }
    @Test fun mixedScriptIsRecognizedWithoutRewritingTheSource() {
        val source = "मैं घर जाना चाहता हूँ\nBring report 25 October"
        assertEquals("hi", CareTranslationRules.sourceLanguage(source.lines()[0]))
        assertEquals("en", CareTranslationRules.sourceLanguage(source.lines()[1]))
        assertEquals(source.lines(), CareTranslationRules.lines(source))
        assertEquals("mixed", CareTranslationRules.sourceLanguage("no oxygen, दर्द"))
    }
}
