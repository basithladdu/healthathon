package `in`.wedevit.care

data class CareTranslationLine(val original: String, val translated: String, val sourceLanguage: String, val targetLanguage: String)

object CareTranslationRules {
    fun sourceLanguage(text: String): String {
        val hindi = text.any { it in '\u0900'..'\u097f' }
        val latin = Regex("\\p{IsLatin}").containsMatchIn(text)
        return if (hindi && latin) "mixed" else if (hindi) "hi" else "en"
    }
    fun numbers(text: String): List<String> = Regex("[+\\-−]?[\\p{Nd}]+(?:[.,][\\p{Nd}]+)*").findAll(text).map { match ->
        match.value.map { char -> if (char.isDigit()) Character.getNumericValue(char).toString() else char.toString() }.joinToString("")
            .replace('−', '-')
    }.toList()
    fun validate(source: String, translated: String) {
        require(translated.isNotBlank()) { "No translation was produced. The original is unchanged." }
        require(numbers(source) == numbers(translated)) { "The translation changed numbers. Read the original and ask a bilingual person to check it." }
    }
    fun lines(source: String): List<String> {
        require(source.isNotBlank() && source.length <= 40_000) { "Choose up to 40,000 characters from the original." }
        // Model calls retain individual source lines. Blank separators remain in the displayed original.
        return source.lines().filter(String::isNotBlank).flatMap { line ->
            require(line.length <= 4000) { "Split this long source line before translating it." }
            listOf(line)
        }
    }
}
