package `in`.wedevit.care

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every English string that has Hindi beside it in the code (`t("English", "हिन्दी")`, `"English" to "हिन्दी"`,
 * `careTr(hindi, "English", "हिन्दी")`, `careBilingual("English", "हिन्दी")`) must also be in the Telugu, Urdu and
 * Kannada dictionaries in `src/main/resources/i18n/<code>.tsv`. A new screen string fails here until it is added.
 *
 * Machine-drafted; clinician back-translation required before pilot.
 */
class CareLanguageDictionaryTest {
    private val languages = listOf(CareLang.TE, CareLang.UR, CareLang.KN)

    @After fun reset() { CareLanguageState.current = CareLang.EN }

    private fun sources(): List<File> {
        val dir = listOf("src/main/java/in/wedevit/care", "app/src/main/java/in/wedevit/care", "android/app/src/main/java/in/wedevit/care").map(::File).first { it.isDirectory }
        return dir.listFiles { f -> f.extension == "kt" }!!.sortedBy { it.name }
    }

    private fun allKeys(): Set<String> = sources().flatMapTo(linkedSetOf()) { file -> CareLanguageKeys.keys(file.readText(), file.name in CareLanguageKeys.vocabularyFiles) }

    @Test fun everyStringIsInEveryLanguage() {
        val keys = allKeys()
        assertTrue("The scanner found too few strings; it may be broken: ${keys.size}", keys.size > 2000)
        val problems = languages.flatMap { lang ->
            val table = CareDictionary.raw(lang)
            (keys - table.keys).map { "${lang.code} missing: $it" }
        }
        assertTrue("Add these lines to src/main/resources/i18n/<code>.tsv (english<TAB>translation):\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun translationsKeepTheirPlaceholdersAndAreNotBlank() {
        val problems = languages.flatMap { lang ->
            CareDictionary.raw(lang).flatMap { (key, value) ->
                listOfNotNull(
                    "${lang.code} blank: $key".takeIf { value.isBlank() },
                    "${lang.code} placeholders differ: $key => $value".takeIf { CareDictionary.placeholders(key).sorted() != CareDictionary.placeholders(value).sorted() },
                )
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun translationsAreWrittenInTheirOwnScript() {
        val scripts = mapOf(CareLang.TE to 'ఀ'..'౿', CareLang.UR to '؀'..'ۿ', CareLang.KN to 'ಀ'..'೿')
        scripts.forEach { (lang, range) ->
            val table = CareDictionary.raw(lang)
            val own = table.values.count { value -> value.any { it in range } }
            assertTrue("${lang.code}: only $own of ${table.size} lines use the language's script", own >= table.size * 95 / 100)
        }
    }

    @Test fun dictionariesCarryTheBackTranslationNotice() {
        val dir = listOf("src/main/resources/i18n", "app/src/main/resources/i18n", "android/app/src/main/resources/i18n").map(::File).first { it.isDirectory }
        languages.forEach { lang -> assertTrue(File(dir, "${lang.code}.tsv").readLines().first().contains("Machine-drafted; clinician back-translation required before pilot")) }
    }

    @Test fun lookupFillsPlaceholdersFromTheEnglishText() {
        val key = CareDictionary.raw(CareLang.TE).keys.first { CareDictionary.placeholders(it) == listOf("n") && it.startsWith("\$n changes waiting") }
        val shown = CareDictionary.lookup(CareLang.TE, key.replace("\$n", "7"))
        assertTrue(shown, shown.contains("7") && !shown.contains("\$n") && !shown.contains("changes"))
        assertEquals("No such string", CareDictionary.lookup(CareLang.TE, "No such string"))
    }

    @Test fun recordsStayEnglishWhateverTheScreenShows() {
        CareLanguageState.current = CareLang.UR
        assertTrue(careTr(false, "Today", "आज") != "Today")
        assertEquals("Today", careInEnglish { careTr(false, "Today", "आज") })
    }

    @Test fun englishAndHindiKeepTheirInlineText() {
        CareLanguageState.current = CareLang.EN
        assertEquals("Today", careTr(false, "Today", "आज")); assertEquals("आज", careTr(true, "Today", "आज"))
        assertEquals("Saved · सहेजा", careBilingual("Saved", "सहेजा"))
        assertEquals("Today", careTr(CareLang.EN, "Today", "आज")); assertEquals("आज", careTr(CareLang.HI, "Today", "आज"))
    }

    @Test fun onlyUrduReadsRightToLeft() {
        assertEquals(listOf(CareLang.UR), CareLang.entries.filter { it.rtl })
        assertEquals(listOf("en", "hi", "te", "ur", "kn"), CareLang.entries.map { it.code })
    }

    @Test fun scannerFindsPairsAndIgnoresPlainText() {
        val code = """val a = t("Save it", "सहेजें"); val b = "Son" to "बेटा"; val c = careTr(hindi, "Today ${'$'}n", "आज ${'$'}n"); Text("Alone"); val d = "x" to "y"; val e = careBilingual("Both", "दोनों")"""
        assertEquals(listOf("Save it", "Son", "Today ${'$'}n", "Both"), CareLanguageKeys.keys(code, false))
        assertEquals(listOf("Both"), CareLanguageKeys.keys(code, true))
    }
}

/** Reads Kotlin source: finds string literals (also inside `${...}`), skipping comments, and returns the English of each English/Hindi pair. */
internal object CareLanguageKeys {
    /** Search vocabularies, not screen text: only their careBilingual messages are shown. */
    val vocabularyFiles = setOf("CareDocumentEvidence.kt", "CareDocumentQuestion.kt", "CareDraftAssist.kt", "GoalsQuestionVocabulary.kt")
    private val devanagari = Regex("[\\u0900-\\u097F]")
    private val gap = Regex("\\s*(,|to|->)\\s*")
    private class Literal(val start: Int, val end: Int, val raw: String)

    private fun readString(code: String, open: Int, out: MutableList<Literal>): Int {
        var i = open + 1
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\\' -> i += 2
                c == '"' -> { out += Literal(open, i + 1, code.substring(open + 1, i)); return i + 1 }
                c == '$' && i + 1 < code.length && code[i + 1] == '{' -> i = readBlock(code, i + 2, out)
                else -> i++
            }
        }
        return i
    }

    private fun readBlock(code: String, from: Int, out: MutableList<Literal>): Int {
        var i = from; var depth = 1
        while (i < code.length) {
            val c = code[i]
            if (c == '"') { i = readString(code, i, out); continue }
            if (c == '{') depth++ else if (c == '}') { depth--; if (depth == 0) return i + 1 }
            i++
        }
        return i
    }

    private fun literals(code: String): List<Literal> {
        val out = mutableListOf<Literal>(); var i = 0
        while (i < code.length) {
            when {
                code.startsWith("//", i) -> i = code.indexOf('\n', i).let { if (it < 0) code.length else it }
                code.startsWith("/*", i) -> i = code.indexOf("*/", i) + 2
                code.startsWith("\"\"\"", i) -> i = code.indexOf("\"\"\"", i + 3) + 3
                code[i] == '\'' -> { var j = i + 1; if (j < code.length && code[j] == '\\') j++; i = if (j + 1 < code.length && code[j + 1] == '\'') j + 2 else i + 1 }
                code[i] == '"' -> i = readString(code, i, out)
                else -> i++
            }
        }
        return out.sortedBy { it.start }
    }

    private fun visible(raw: String) = Regex("[A-Za-z]{2}").containsMatchIn(raw.replace(Regex("\\$\\{[^}]*\\}|\\$[A-Za-z_]\\w*|\\\\."), " "))

    fun keys(code: String, vocabulary: Boolean): List<String> {
        val lits = literals(code); val found = mutableListOf<String>()
        lits.forEach { lit ->
            if (devanagari.containsMatchIn(lit.raw) || !visible(lit.raw) || lit.raw == "hi") return@forEach
            if (vocabulary && !code.substring(0, lit.start).endsWith("careBilingual(")) return@forEach
            val next = lits.firstOrNull { it.start >= lit.end } ?: return@forEach
            if (devanagari.containsMatchIn(next.raw) && gap.matches(code.substring(lit.end, next.start))) found += lit.raw
        }
        return found
    }
}
