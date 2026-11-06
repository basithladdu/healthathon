package `in`.wedevit.care

import java.text.BreakIterator

/**
 * Owner rule (6 Nov): no word may split across lines ("PATIEN" / "T").
 * Pure checks on a laid-out text and the offsets where its lines end, so the opt-in
 * TextFitRenderTest (-PSAHARA_RENDER_UI=true) and a plain unit test share one rule.
 */
object TextFitRules {
    enum class Kind { MID_WORD, GRAPHEME }

    data class Split(val at: Int, val kind: Kind, val excerpt: String)

    /** Letters, digits and combining marks (Devanagari matras, virama, nukta) plus ZWJ/ZWNJ belong to a word. */
    fun isWordChar(c: Char): Boolean {
        if (Character.isLetterOrDigit(c) || c == '‌' || c == '‍') return true
        val type = Character.getType(c)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()
    }

    /**
     * [lineEnds] are the offsets where every line except the last ends (TextLayoutResult.getLineEnd).
     * A break is wrong when it falls inside a grapheme cluster, or between two word characters
     * (no space, hyphen or punctuation at the break). Forced newlines are fine.
     */
    fun splits(text: String, lineEnds: List<Int>): List<Split> {
        if (text.isEmpty()) return emptyList()
        val graphemes = BreakIterator.getCharacterInstance().apply { setText(text) }
        return lineEnds.mapNotNull { at ->
            if (at <= 0 || at >= text.length) return@mapNotNull null
            val before = text[at - 1]
            val after = text[at]
            if (before == '\n') return@mapNotNull null
            val kind = when {
                !graphemes.isBoundary(at) -> Kind.GRAPHEME
                isWordChar(before) && isWordChar(after) -> Kind.MID_WORD
                else -> null
            } ?: return@mapNotNull null
            Split(at, kind, excerpt(text, at))
        }
    }

    /** Up to 30 characters either side of the break, with "|" where the line ends. */
    fun excerpt(text: String, at: Int): String =
        (text.substring(maxOf(0, at - 30), at) + "|" + text.substring(at, minOf(text.length, at + 30))).replace('\n', ' ')
}
