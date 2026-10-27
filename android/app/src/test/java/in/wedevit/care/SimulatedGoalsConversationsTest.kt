package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Research check (HANDOFF B17): runs the app's real goals-of-care word matching on the 20 synthetic conversations in
 * docs/research/sim-conversations and reports, per question, hit / miss / wrong against the hand-marked answers.
 * It asserts only that the files and the pipeline are consistent, never a score, so a matcher change does not fail the build.
 * The report is printed and written to app/build/reports/sim-conversations.md.
 */
class SimulatedGoalsConversationsTest {
    private class Convo(val id: String, val language: String, val source: String, val truth: Map<Int, Set<Int>>, val lineCount: Int)

    private val speakers = mapOf("D" to "Doctor", "P" to "Patient", "F" to "Family / carer")
    private val tag = Regex("\\s*<<([^>]*)>>\\s*$")

    private fun folder(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) { File(dir, "docs/research/sim-conversations").takeIf { it.isDirectory }?.let { return it }; dir = dir.parentFile }
        error("docs/research/sim-conversations not found above ${System.getProperty("user.dir")}")
    }

    private fun parse(file: File): Convo {
        val header = mutableMapOf<String, String>()
        val out = mutableListOf<String>()
        val truth = mutableMapOf<Int, MutableSet<Int>>()
        file.readLines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { raw ->
            if (raw.startsWith("#")) { raw.removePrefix("#").split(":", limit = 2).takeIf { it.size == 2 }?.let { header[it[0].trim()] = it[1].trim() }; return@forEach }
            val speaker = speakers[raw.substringBefore(":")] ?: error("${file.name}: line without D:/P:/F: speaker: $raw")
            val tags = tag.find(raw)?.groupValues?.get(1)?.trim()?.split(Regex("\\s+")).orEmpty()
            val words = raw.substringAfter(":").replace(tag, "").trim()
            val lineNo = out.size + 1
            tags.forEach { t -> val q = t.removePrefix("Q").toInt(); require(q in 1..8) { "${file.name}: bad tag $t" }; truth.getOrPut(q) { mutableSetOf() } += lineNo }
            val lang = header.getValue("language").substringBefore(" ")
            out += "[$speaker · $lang · 27 Oct 10:${out.size.toString().padStart(2, '0')}] $words"
        }
        return Convo(header.getValue("id"), header.getValue("language"), out.joinToString("\n"), truth, out.size)
    }

    private enum class Cell(val mark: String) { HIT("hit"), MISS("miss"), WRONG("wrong"), FALSE_FILL("wrong*"), LEFT_OPEN("—") }

    @Test fun runRealMatcherOnSyntheticConversations() {
        val files = folder().listFiles { f -> f.name.endsWith(".txt") }!!.sortedBy { it.name }
        assertEquals("expected 20 synthetic conversations", 20, files.size)
        val convos = files.map(::parse)
        // The same question list the doctor screen proposes when there is no earlier note (CareUi -> doctorSuggestQuestions).
        val rows = doctorSuggestQuestions(null, "")
        assertEquals(8, rows.size)

        val report = StringBuilder()
        val cells = mutableMapOf<Pair<String, Int>, Cell>()
        val top3 = mutableMapOf<Pair<String, Int>, Boolean>()
        val details = StringBuilder()
        convos.forEach { c ->
            assertTrue("${c.id}: ground truth must point at lines", c.truth.values.flatten().all { it in 1..c.lineCount })
            val matches = LocalQuestionMatcher.match(c.source, rows.map { CareQuestion(it.id, it.text, it.section) })
            val filled = transcriptGoalsFill(rows, matches)
            rows.forEachIndexed { i, row ->
                val q = i + 1
                val all = matches.forQuestion(row.id)!!.matches
                val top = all.firstOrNull()
                val span = transcriptGoalSpan(matches, row.id)
                // The pre-filled answer and the status chip must agree with the top match; this is the text the doctor sees.
                assertEquals(top?.quote?.trim().orEmpty(), span)
                assertEquals(span.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS), filled[i].answer)
                assertEquals(if (span.isBlank()) TranscriptGoalStatus.NotFound else TranscriptGoalStatus.Found, transcriptGoalStatus(row, matches))
                val want = c.truth[q].orEmpty()
                val cell = when {
                    top == null && want.isEmpty() -> Cell.LEFT_OPEN
                    top == null -> Cell.MISS
                    top.line.number in want -> Cell.HIT
                    want.isEmpty() -> Cell.FALSE_FILL
                    else -> Cell.WRONG
                }
                cells[c.id to q] = cell
                top3[c.id to q] = all.any { it.line.number in want }
                if (cell != Cell.LEFT_OPEN && cell != Cell.HIT) details.append("| ${c.id} | Q$q | ${cell.mark} | ${want.sorted().joinToString(",").ifEmpty { "none" }} | ${top?.let { "L${it.line.number} (score ${it.score}, words: ${it.matchedWords.joinToString(" ")}) ${it.quote.take(90)}" } ?: "nothing found"} |\n")
            }
        }

        report.append("# Simulated goals-of-care conversations: results\n\n")
        report.append("Question key: ").append(rows.mapIndexed { i, r -> "Q${i + 1} = ${r.text}" }.joinToString("; ")).append("\n\n")
        report.append("hit = top suggested line is a right answer; miss = answered but nothing found; wrong = a different line was pre-filled; wrong* = pre-filled although the topic was never answered; — = correctly left open.\n\n")
        report.append("| Conversation | Language | ").append((1..8).joinToString(" | ") { "Q$it" }).append(" |\n|---|---|").append("---|".repeat(8)).append("\n")
        convos.forEach { c -> report.append("| ${c.id} | ${c.language.substringAfter("(").removeSuffix(")")} | ").append((1..8).joinToString(" | ") { cells.getValue(c.id to it).mark }).append(" |\n") }

        fun stats(label: String, keys: List<Pair<String, Int>>) {
            val cs = keys.map { cells.getValue(it) }
            val hit = cs.count { it == Cell.HIT }; val miss = cs.count { it == Cell.MISS }; val wrong = cs.count { it == Cell.WRONG }
            val ff = cs.count { it == Cell.FALSE_FILL }; val open = cs.count { it == Cell.LEFT_OPEN }
            val answered = hit + miss + wrong; val filledN = hit + wrong + ff
            fun pct(a: Int, b: Int) = if (b == 0) "n/a" else "${Math.round(100.0 * a / b)}% ($a/$b)"
            val t3 = keys.count { top3.getValue(it) }
            report.append("| $label | $answered | $hit | $miss | $wrong | $ff | $open | ${pct(hit, filledN)} | ${pct(hit, answered)} | ${pct(t3, answered)} |\n")
        }
        report.append("\n## Precision and recall\n\nPrecision = hits / everything pre-filled. Recall = hits / questions actually answered. Top-3 recall = a right line is anywhere in the matcher's three suggestions (the screen pre-fills only the first).\n\n")
        report.append("| Question | Answered | Hit | Miss | Wrong | Wrong* | Left open | Precision | Recall | Top-3 recall |\n|---|---|---|---|---|---|---|---|---|---|\n")
        (1..8).forEach { q -> stats("Q$q", convos.map { it.id to q }) }
        stats("All", convos.flatMap { c -> (1..8).map { c.id to it } })
        report.append("\n## By language\n\n| Group | Answered | Hit | Miss | Wrong | Wrong* | Left open | Precision | Recall | Top-3 recall |\n|---|---|---|---|---|---|---|---|---|---|\n")
        convos.groupBy { it.language.substringAfter("(").removeSuffix(")") }.forEach { (lang, cs) -> stats(lang, cs.flatMap { c -> (1..8).map { c.id to it } }) }
        report.append("\n## Every miss and wrong cell\n\n| Conversation | Question | Result | Right lines | Top suggestion |\n|---|---|---|---|---|\n").append(details)

        println(report)
        File("build/reports").apply { mkdirs() }.resolve("sim-conversations.md").writeText(report.toString())
    }
}
