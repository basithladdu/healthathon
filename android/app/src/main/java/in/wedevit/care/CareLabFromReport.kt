package `in`.wedevit.care

/** A suggestion read from one reviewed report line. The family checks every field before it is saved; nothing is interpreted. */
data class LabLineCandidate(val name: String, val value: String, val unit: String = "", val low: String = "", val high: String = "")

object LabLineParser {
    private const val NUMBER = "(?:[0-9][0-9,]*(?:\\.[0-9]+)?|\\.[0-9]+)"
    private val valueToken = Regex("(?<=^|[\\s:=])($NUMBER)(?=$|[\\s%a-zA-Zµμ])")
    private val range = Regex("($NUMBER)\\s*(?:-|–|—|to)\\s*($NUMBER)")
    private val upTo = Regex("(?:<|≤|<=|up to|upto)\\s*($NUMBER)", RegexOption.IGNORE_CASE)
    private val from = Regex("(?:>|≥|>=)\\s*($NUMBER)")
    private val flags = setOf("h", "l", "high", "low", "*", "(h)", "(l)")
    private val unitShape = Regex("[%a-zA-Zµμ/^0-9.³]*[%a-zA-Zµμ][%a-zA-Zµμ/^0-9.³]*|%")

    private fun clean(number: String) = number.replace(",", "")

    fun parse(line: String): LabLineCandidate? {
        val text = line.replace('\t', ' ').trim()
        if (text.length !in 3..400) return null
        val match = valueToken.findAll(text).firstOrNull { m ->
            val before = text.substring(0, m.range.first)
            before.count(Char::isLetter) >= 2
        } ?: return null
        val name = text.substring(0, match.range.first).trim().trimEnd(':', '=', '-', '–', '.').trim()
        if (name.isEmpty() || name.length > 120) return null
        val value = clean(match.groupValues[1])
        val rest = text.substring(match.range.last + 1)
        val tokens = rest.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        var unit = tokens.firstOrNull { it.lowercase() !in flags }?.takeIf { unitShape.matches(it) }.orEmpty()
        val rangeMatch = range.find(rest)
        var low = ""; var high = ""
        if (rangeMatch != null) { low = clean(rangeMatch.groupValues[1]); high = clean(rangeMatch.groupValues[2]) }
        else upTo.find(rest)?.let { high = clean(it.groupValues[1]) } ?: from.find(rest)?.let { low = clean(it.groupValues[1]) }
        if (unit.isEmpty() && rangeMatch != null) {
            unit = rest.substring(rangeMatch.range.last + 1).trim().split(Regex("\\s+")).firstOrNull { unitShape.matches(it) }.orEmpty()
        }
        return LabLineCandidate(name.take(120), value, unit.take(40), low, high)
    }
}
