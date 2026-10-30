package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject

/** Text of one chosen document, read on this phone. [reviewed] is false while nobody has checked the reading against the original. */
data class DocumentQuestionSource(val reportId: String, val name: String, val pages: List<DocumentPageText>, val reviewed: Boolean, val added: Long = 0L)
/** A line found on this phone. [marks] are the character ranges that matched the question; [next] is the line under it when the value is printed there. */
data class DocumentMatch(val reportId: String, val reportName: String, val page: Int, val line: Int, val text: String, val marks: List<IntRange>, val reviewed: Boolean, val next: String = "")
/** [date] is yyyymmdd, 0 when unknown. */
data class DocumentMatchGroup(val reportId: String, val reportName: String, val reviewed: Boolean, val date: Int, val lines: List<DocumentMatch>)
data class DocumentValue(val match: DocumentMatch, val date: Int)
/** The answer made on this phone with no AI: matching lines only, nothing interpreted, nothing sent. [values] are test result lines, newest document first. */
data class LocalDocumentAnswer(val question: String, val values: List<DocumentValue>, val groups: List<DocumentMatchGroup>) { val found get() = groups.isNotEmpty() }
data class DocumentQuestionLine(val document: String, val page: Int, val line: Int, val text: String)
/** What leaves the phone: the question and numbered text lines under short references (D1, D2…). File names stay on the phone. */
data class DocumentQuestionRequest(val question: String, val sourceHash: String, val sources: Map<String, DocumentQuestionSource>, val lines: List<DocumentQuestionLine>)
data class DocumentCitation(val reportId: String, val reportName: String, val page: Int, val line: Int, val quote: String, val reviewed: Boolean)
data class DocumentAnswer(val found: Boolean, val answer: String, val citations: List<DocumentCitation>, val model: String)

/** Ask your documents: builds the request and checks the answer. Every cited line and its quote come from this phone's own copy. */
object DocumentQuestionRules {
    const val MAX_QUESTION = 300
    const val MAX_DOCUMENTS = 5
    const val MAX_CHARACTERS = 24_000
    const val MAX_ANSWER = 600
    const val MAX_CITATIONS = 6
    /** Search-all mode: how many documents are read, and how much retrieved text leaves the phone. */
    const val MAX_SEARCH_DOCUMENTS = 20
    const val MAX_RETRIEVED_LINES = 80
    const val MAX_RETRIEVED_CHARACTERS = 8_000

    fun build(question: String, documents: List<DocumentQuestionSource>): DocumentQuestionRequest {
        val asked = question.trim()
        require(asked.isNotEmpty() && asked.length <= MAX_QUESTION) { careBilingual("Type a question of up to 300 characters.", "300 अक्षरों तक का सवाल लिखें।") }
        require(documents.size in 1..MAX_DOCUMENTS && documents.map { it.reportId }.distinct().size == documents.size) { careBilingual("Choose one to five documents.", "एक से पाँच कागज़ चुनें।") }
        val sources = documents.mapIndexed { index, document -> "D${index + 1}" to document }.toMap()
        val lines = sources.flatMap { (ref, document) -> document.pages.sortedBy { it.page }.flatMap { page ->
            // Line numbers match the review screen and the report search, so a citation points at the same line there.
            page.reviewedText.split('\n').mapIndexedNotNull { index, text -> text.trim().takeIf { it.isNotEmpty() }?.let { DocumentQuestionLine(ref, page.page, index + 1, it.take(2000)) } }
        } }
        sources.forEach { (ref, document) -> require(lines.any { it.document == ref }) { careBilingual("“${document.name}” has no readable text yet. Open it with “Read and copy facts” first.", "“${document.name}” में अभी पढ़ने लायक लिखाई नहीं है। पहले “पढ़ें और बातें कॉपी करें” से खोलें।") } }
        require(lines.sumOf { it.text.length } <= MAX_CHARACTERS) { careBilingual("These documents have too much text. Choose fewer documents.", "इन कागज़ों में बहुत ज़्यादा लिखाई है। कम कागज़ चुनें।") }
        val canonical = buildString { append(asked).append('\n'); lines.forEach { append(it.document).append('|').append(it.page).append('|').append(it.line).append('|').append(it.text).append('\n') } }
        return DocumentQuestionRequest(asked, careAssistSourceHash(canonical), sources, lines)
    }

    /** One line in the person's own words, with where it was found. */
    private data class Candidate(val source: Int, val page: Int, val line: Int, val text: String, val order: Int)
    private class Ranking(val all: List<Candidate>, val terms: List<List<Term>>, val scores: List<Double>, val query: Map<String, Double>)

    private fun readable(asked: String, documents: List<DocumentQuestionSource>): List<DocumentQuestionSource> {
        require(asked.isNotEmpty() && asked.length <= MAX_QUESTION) { careBilingual("Type a question of up to 300 characters.", "300 अक्षरों तक का सवाल लिखें।") }
        return documents.distinctBy { it.reportId }.filter { document -> document.pages.any { it.reviewedText.isNotBlank() } }
            .also { require(it.isNotEmpty()) { careBilingual("Your documents have no readable text yet. Open one with “Read and copy facts” first.", "आपके कागज़ों में अभी पढ़ने लायक लिखाई नहीं है। पहले किसी को “पढ़ें और बातें कॉपी करें” से खोलें।") } }
    }

    /**
     * BM25 over every line, on this phone. Words are folded to one key per medical idea (hb, hgb, haemoglobin, हीमोग्लोबिन → hemoglobin),
     * a broader idea counts a little (fbs also counts as glucose), and a question word also finds lines where the phone misread it by a letter or two.
     */
    private fun rank(asked: String, readable: List<DocumentQuestionSource>): Ranking {
        val all = mutableListOf<Candidate>()
        readable.forEachIndexed { index, document -> document.pages.sortedBy { it.page }.forEach { page ->
            page.reviewedText.split('\n').forEachIndexed { number, text -> text.trim().takeIf { it.isNotEmpty() }?.let { all += Candidate(index, page.page, number + 1, it.take(2000), all.size) } }
        } }
        val terms = all.map { terms(it.text) }
        val bags = terms.map { line -> HashMap<String, Int>().also { bag -> line.forEach { term -> term.keys.forEach { bag[it] = (bag[it] ?: 0) + 1 } } } }
        val sizes = terms.map { it.size }
        val average = sizes.sum().toDouble() / sizes.size.coerceAtLeast(1)
        val frequency = HashMap<String, Int>(); bags.forEach { bag -> bag.keys.forEach { frequency[it] = (frequency[it] ?: 0) + 1 } }
        val query = HashMap<String, Double>()
        terms(asked).forEach { term -> term.keys.forEachIndexed { index, key -> val weight = if (index == 0) 1.0 else 0.4; if (weight > (query[key] ?: 0.0)) query[key] = weight } }
        query.filterValues { it == 1.0 }.keys.filter { it.length >= 5 && it.all { c -> c in 'a'..'z' } }.forEach { word ->
            frequency.keys.forEach { seen -> if (seen !in query && seen !in KNOWN && close(word, seen)) query[seen] = 0.7 }
        }
        val scores = bags.indices.map { index -> val bag = bags[index]
            if (sizes[index] == 0) 0.0 else query.entries.sumOf { (key, weight) ->
                val count = bag[key] ?: 0; if (count == 0) return@sumOf 0.0
                val n = frequency[key] ?: 0
                weight * kotlin.math.ln(1 + (all.size - n + 0.5) / (n + 0.5)) * count * 2.2 / (count + 1.2 * (0.25 + 0.75 * sizes[index] / average.coerceAtLeast(1.0)))
            }
        }
        return Ranking(all, terms, scores, query)
    }

    /**
     * Retrieval step (RAG) over all of the person's documents, entirely on this phone: every line is ranked against the question,
     * the best lines and their neighbours are kept, and only those leave the phone.
     * Line numbers stay those of the original page, so citations still point at the same line in the review screen.
     * When nothing matches (for example a Hindi question about English reports) the start of each document is sent instead.
     */
    fun retrieve(question: String, documents: List<DocumentQuestionSource>, maxLines: Int = MAX_RETRIEVED_LINES, maxCharacters: Int = MAX_RETRIEVED_CHARACTERS): DocumentQuestionRequest {
        val asked = question.trim()
        val readable = readable(asked, documents)
        val ranking = rank(asked, readable); val all = ranking.all; val scores = ranking.scores
        val keep = LinkedHashSet<Int>()
        val ranked = scores.indices.filter { scores[it] > 0.0 }.sortedByDescending { scores[it] }
        var used = 0
        fun take(index: Int): Boolean { if (index !in all.indices || index in keep) return true
            val size = all[index].text.length; if (keep.size >= maxLines || used + size > maxCharacters) return false
            keep += index; used += size; return true }
        if (ranked.isNotEmpty()) {
            // A hit and the line on each side, so a value printed under its label still comes along.
            for (index in ranked) { if (!take(index)) break; val hit = all[index]
                listOf(index - 1, index + 1).filter { it in all.indices && all[it].source == hit.source && all[it].page == hit.page }.forEach { take(it) } }
        } else {
            val starts = readable.indices.map { source -> all.filter { it.source == source }.map { it.order } }
            var row = 0; var added = true
            while (added) { added = false; for (lines in starts) if (row < lines.size && take(lines[row])) added = true; row++; if (keep.size >= maxLines) break }
        }
        val chosen = keep.map { all[it] }.sortedBy { it.order }
        val usedSources = chosen.map { it.source }.distinct().take(MAX_SEARCH_DOCUMENTS)
        val refs = usedSources.mapIndexed { index, source -> source to "D${index + 1}" }.toMap()
        val sources = usedSources.associate { refs.getValue(it) to readable[it] }
        val lines = chosen.filter { it.source in refs }.map { DocumentQuestionLine(refs.getValue(it.source), it.page, it.line, it.text) }
        val canonical = buildString { append(asked).append('\n'); lines.forEach { append(it.document).append('|').append(it.page).append('|').append(it.line).append('|').append(it.text).append('\n') } }
        return DocumentQuestionRequest(asked, careAssistSourceHash(canonical), sources, lines)
    }

    /**
     * The answer built on this phone, with no AI and nothing sent: the best matching lines, grouped by document, best first.
     * For a test result question (“latest creatinine”) the line with the value is pulled out too, newest document first.
     * It only shows lines; it does not interpret them.
     */
    fun matchLines(question: String, documents: List<DocumentQuestionSource>, maxLines: Int = 12): LocalDocumentAnswer {
        val asked = question.trim()
        val readable = readable(asked, documents)
        val ranking = rank(asked, readable); val all = ranking.all; val scores = ranking.scores
        val strong = ranking.query.filterValues { it >= 0.7 }.keys
        val own = ranking.query.filterValues { it == 1.0 }.keys
        fun match(index: Int, next: String = ""): DocumentMatch { val hit = all[index]; val source = readable[hit.source]
            val marks = ranking.terms[index].filter { term -> term.keys.first() in strong || term.keys.any { it in own } }.map { it.start until it.end }
            return DocumentMatch(source.reportId, source.name, hit.page, hit.line, hit.text, marks, source.reviewed, next) }
        val dates = readable.map { documentDate(it) }
        val ranked = scores.indices.filter { scores[it] > 0.0 }.sortedByDescending { scores[it] }
        val groups = ranked.take(maxLines).groupBy { all[it].source }.map { (source, lines) ->
            DocumentMatchGroup(readable[source].reportId, readable[source].name, readable[source].reviewed, dates[source], lines.map { match(it) }) }
        val tests = own.filter { it in TESTS }.toSet()
        val values = if (tests.isEmpty()) emptyList() else ranked.mapNotNull { index ->
            val hit = all[index]; if (ranking.terms[index].none { term -> term.keys.first() in tests || term.keys.first() in strong && term.keys.first() !in own }) return@mapNotNull null
            if (hasValue(hit.text)) index to "" else all.getOrNull(index + 1)?.takeIf { it.source == hit.source && it.page == hit.page && hasValue(it.text) }?.let { index to it.text }
        }.distinctBy { all[it.first].source }.sortedWith(compareByDescending<Pair<Int, String>> { dates[all[it.first].source] }.thenByDescending { scores[it.first] })
            .take(3).map { (index, next) -> DocumentValue(match(index, next), dates[all[index].source]) }
        return LocalDocumentAnswer(asked, values, groups)
    }

    private val DATE = Regex("(?<![0-9])([0-3]?[0-9])[/.-]([01]?[0-9])[/.-]((?:19|20)[0-9]{2})(?![0-9])")
    private fun hasValue(text: String) = Regex("[0-9]").containsMatchIn(DATE.replace(text, " "))
    private val DATED = Regex("date|collect|report|sample|receiv|discharg|admi|visit|दिनांक|तारीख", RegexOption.IGNORE_CASE)
    private val NOT_DATED = Regex("birth|dob|next|review|follow|due|repeat|जन्म", RegexOption.IGNORE_CASE)
    /** The document's own date as yyyymmdd: the latest day/month/year on a report, sample or visit line (not a birth or next-visit date), else the day it was added. */
    internal fun documentDate(source: DocumentQuestionSource): Int {
        val written = source.pages.flatMap { it.reviewedText.split('\n') }.filter { DATED.containsMatchIn(it) && !NOT_DATED.containsMatchIn(it) }.flatMap { line ->
            DATE.findAll(line).mapNotNull { m -> val (d, mo, y) = m.destructured.toList().map(String::toInt); if (d in 1..31 && mo in 1..12) y * 10000 + mo * 100 + d else null }.toList() }.maxOrNull()
        if (written != null) return written
        if (source.added <= 0) return 0
        val day = java.util.Calendar.getInstance().apply { timeInMillis = source.added }
        return day.get(java.util.Calendar.YEAR) * 10000 + (day.get(java.util.Calendar.MONTH) + 1) * 100 + day.get(java.util.Calendar.DAY_OF_MONTH)
    }

    /** A phone misreading: up to one wrong letter in a word of 5–7 letters, two in a longer one; 0/1/5 read as o/l/s. */
    private fun close(asked: String, seen: String): Boolean {
        if (!seen.all { it in 'a'..'z' || it in '0'..'9' } || seen.none { it in 'a'..'z' }) return false
        val word = seen.map { when (it) { '0' -> 'o'; '1' -> 'l'; '5' -> 's'; else -> it } }.joinToString("")
        val limit = if (asked.length >= 8) 2 else 1
        if (kotlin.math.abs(word.length - asked.length) > limit) return false
        var previous = IntArray(word.length + 1) { it }
        for (i in 1..asked.length) { val row = IntArray(word.length + 1); row[0] = i
            for (j in 1..word.length) row[j] = minOf(previous[j] + 1, row[j - 1] + 1, previous[j - 1] + if (asked[i - 1] == word[j - 1]) 0 else 1)
            if (row.min() > limit) return false; previous = row }
        return previous[word.length] <= limit
    }

    private val STOP = setOf("the", "a", "an", "of", "and", "or", "to", "in", "on", "for", "is", "are", "was", "my", "me", "i", "what", "when", "which", "how", "do", "does", "did", "it", "this", "that", "with", "at", "by", "be", "from", "as", "your", "you",
        "latest", "last", "recent", "current", "level", "levels", "value", "show", "tell", "give", "please", "much", "have", "has", "had", "its",
        "क्या", "है", "कब", "का", "की", "के", "में", "मेरा", "मेरी", "मेरे", "और", "को", "से", "कितना", "कितनी", "बताओ", "बताइए")

    /**
     * Small word list, kept as data: each idea's key, then the words that mean it. British and American spellings fold anyway
     * (ae and oe become e). A key after “>” is the broader idea it also counts toward, a little.
     */
    private val IDEAS = listOf(
        "hemoglobin hb hgb haemoglobin हीमोग्लोबिन >cbc", "wbc tlc leucocyte leukocyte >cbc", "platelet plt प्लेटलेट >cbc", "rbc erythrocyte >cbc", "cbc hemogram",
        "creatinine creat creatinin क्रिएटिनिन >kidney", "urea bun >kidney", "uric >kidney", "kidney renal किडनी गुर्दा",
        "glucose sugar शुगर", "fbs >glucose", "ppbs >glucose", "rbs >glucose", "hba1c a1c glycated glycosylated >glucose",
        "bp बीपी", "tsh >thyroid", "t3 >thyroid", "t4 >thyroid", "thyroid थायराइड थायरॉइड",
        "alt sgpt >liver", "ast sgot >liver", "bilirubin >liver", "liver लिवर जिगर",
        "cholesterol chol कोलेस्ट्रॉल >lipid", "ldl >lipid", "hdl >lipid", "triglyceride tg >lipid", "lipid",
        "blood खून रक्त", "urine पेशाब मूत्र", "medicine medication drug tablet tab दवा दवाई गोली", "fever बुखार", "pain दर्द",
        "doctor dr डॉक्टर", "followup review", "pulse", "weight वजन", "esr", "crp", "sodium", "potassium", "vitamin",
    )
    private val PHRASES = listOf(
        "fasting (blood )?(sugar|glucose)|fbg" to "fbs", "(post[ -]?prandial|pp)( blood)? (sugar|glucose)" to "ppbs", "random (blood )?(sugar|glucose)" to "rbs",
        "blood (sugar|glucose)|ब्लड शुगर" to "glucose", "blood pressure|ब्लड प्रेशर" to "bp", "white blood cells?|total leu[ck]ocyte count|total count" to "wbc",
        "red blood cells?" to "rbc", "platelet count" to "platelet", "thyroid stimulating hormone" to "tsh", "follow[ -]*up" to "followup",
        "heart rate|pulse rate" to "pulse", "uric acid" to "uric", "serum creatinine" to "creatinine",
    ).map { (pattern, word) -> Regex("(?<![\\p{L}\\p{N}])(?:$pattern)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) to word }
    private fun fold(word: String): String {
        var w = word.lowercase()
        if (w.all { it in 'a'..'z' || it in '0'..'9' }) { w = w.replace("ae", "e").replace("oe", "e"); if (w.length > 3 && w.endsWith("s") && !w.endsWith("ss") && w.all { it in 'a'..'z' }) w = w.dropLast(1) }
        return w
    }
    private val IDEA_OF: Map<String, List<String>> = buildMap {
        IDEAS.forEach { row -> val parts = row.split(' '); val words = parts.filter { !it.startsWith(">") }
            val keys = listOf(words.first()) + parts.filter { it.startsWith(">") }.map { it.drop(1) }
            words.forEach { put(it.lowercase(), keys); put(fold(it), keys) } }
    }
    private val KNOWN = IDEA_OF.keys + IDEA_OF.values.flatten()
    /** Measurements whose value line is worth pulling out. */
    private val TESTS = setOf("hemoglobin", "wbc", "platelet", "rbc", "creatinine", "urea", "uric", "glucose", "fbs", "ppbs", "rbs", "hba1c", "bp", "tsh", "t3", "t4", "alt", "ast", "bilirubin",
        "cholesterol", "ldl", "hdl", "triglyceride", "pulse", "weight", "esr", "crp", "sodium", "potassium", "vitamin")

    /** A word, or a known phrase such as “blood pressure”, from [start] until [end]: its idea key first, then any broader idea. */
    internal data class Term(val keys: List<String>, val start: Int, val end: Int)
    private val WORD = Regex("[\\p{L}\\p{M}\\p{N}]+")
    internal fun terms(text: String): List<Term> {
        val phrases = mutableListOf<IntRange>(); val found = mutableListOf<Term>()
        PHRASES.flatMap { (regex, word) -> regex.findAll(text).map { it.range to word }.toList() }.sortedWith(compareBy({ it.first.first }, { -it.first.last })).forEach { (range, word) ->
            if (phrases.none { it.first <= range.last && range.first <= it.last }) { phrases += range; found += Term(IDEA_OF.getValue(word), range.first, range.last + 1) } }
        WORD.findAll(text).forEach { match -> if (phrases.any { match.range.first in it }) return@forEach
            val raw = match.value.lowercase(); if (raw in STOP || (raw.length == 1 && !raw[0].isDigit())) return@forEach
            val word = fold(raw); if (word in STOP) return@forEach
            found += Term(IDEA_OF[raw] ?: IDEA_OF[word] ?: listOf(word), match.range.first, match.range.last + 1) }
        return found.sortedBy { it.start }
    }
    /** Lower-case words and numbers in any script, each folded to its idea key; very common words are dropped. */
    internal fun tokens(text: String): List<String> = terms(text).map { it.keys.first() }

    /** [base] carries the request ID and consent flag, as for every other assist request. */
    fun payload(request: DocumentQuestionRequest, base: JSONObject = JSONObject()): JSONObject {
        val documents = JSONArray()
        request.sources.keys.forEach { ref ->
            val lines = JSONArray()
            request.lines.filter { it.document == ref }.forEach { lines.put(JSONObject().put("page", it.page).put("line", it.line).put("text", it.text)) }
            documents.put(JSONObject().put("ref", ref).put("lines", lines))
        }
        return base.put("question", request.question).put("sourceHash", request.sourceHash).put("documents", documents)
    }

    private fun digits(text: String) = Regex("[0-9]+").findAll(text).map { it.value }.toList()

    /** Fails closed: an answer must cite lines that were sent, and every number in it must be written in those lines. */
    fun parse(json: String, request: DocumentQuestionRequest): DocumentAnswer {
        val value = JSONObject(json)
        require(value.get("requiresReview") == true && value.getString("provider") == CARE_ASSIST_PROVIDER)
        val model = value.getString("model"); require(model.length in 1..100)
        require(value.getString("sourceHash") == request.sourceHash) { "The question or documents changed. Ask again." }
        if (value.get("found") == false) return DocumentAnswer(false, "", emptyList(), model)
        require(value.get("found") == true)
        val answer = value.getString("answer").trim(); require(answer.isNotEmpty() && answer.length <= MAX_ANSWER)
        val raw = value.getJSONArray("citations"); require(raw.length() in 1..MAX_CITATIONS)
        val citations = (0 until raw.length()).map { index ->
            val item = raw.getJSONObject(index)
            val document = item.getString("document"); val page = item.get("page"); val line = item.get("line")
            require(page is Int && line is Int)
            val sent = request.lines.firstOrNull { it.document == document && it.page == page && it.line == line } ?: throw IllegalArgumentException("The answer pointed at a line that was not sent.")
            val source = request.sources.getValue(document)
            DocumentCitation(source.reportId, source.name, sent.page, sent.line, sent.text, source.reviewed)
        }.distinctBy { Triple(it.reportId, it.page, it.line) }
        val cited = citations.flatMap { digits(it.quote) }.toSet()
        require(digits(answer).all { it in cited }) { "The answer had a number that is not in the cited lines." }
        return DocumentAnswer(true, answer, citations, model)
    }
}
