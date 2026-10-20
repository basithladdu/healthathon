package `in`.wedevit.care

/** Select overlapping script readings, retaining separate physical lines and the original file. */
data class CareOcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val devanagari: Boolean)

object CareOcrLines {
    fun merge(lines: List<CareOcrLine>): String {
        val selected = mutableListOf<CareOcrLine>()
        lines.filter { it.text.isNotBlank() }.sortedWith(compareByDescending<CareOcrLine> {
            if (it.devanagari && it.text.any { c -> c in '\u0900'..'\u097f' }) 2 else if (!it.devanagari) 1 else 0
        }).forEach { candidate ->
            val duplicate = selected.any { other ->
                val width = (minOf(candidate.right, other.right) - maxOf(candidate.left, other.left)).coerceAtLeast(0)
                val height = (minOf(candidate.bottom, other.bottom) - maxOf(candidate.top, other.top)).coerceAtLeast(0)
                val overlap = width.toLong() * height
                val candidateArea = (candidate.right - candidate.left).coerceAtLeast(1).toLong() * (candidate.bottom - candidate.top).coerceAtLeast(1)
                val otherArea = (other.right - other.left).coerceAtLeast(1).toLong() * (other.bottom - other.top).coerceAtLeast(1)
                val union = candidateArea + otherArea - overlap
                val containedWords = other.text.replace(Regex("\\s+"), " ").contains(candidate.text.replace(Regex("\\s+"), " "))
                // A narrow Hindi line must not swallow a wider line containing English names/doses.
                overlap > 0 && (overlap.toDouble() / union > 0.65 || (containedWords && overlap.toDouble() / candidateArea > 0.8))
            }
            if (!duplicate) selected += candidate
        }
        return selected.sortedWith(compareBy<CareOcrLine> { it.top }.thenBy { it.left })
            .joinToString("\n") { it.text.trim() }
    }
}
