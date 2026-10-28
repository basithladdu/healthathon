package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject

/** Text of one chosen document, read on this phone. [reviewed] is false while nobody has checked the reading against the original. */
data class DocumentQuestionSource(val reportId: String, val name: String, val pages: List<DocumentPageText>, val reviewed: Boolean)
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

    fun build(question: String, documents: List<DocumentQuestionSource>): DocumentQuestionRequest {
        val asked = question.trim()
        require(asked.isNotEmpty() && asked.length <= MAX_QUESTION) { "Type a question of up to 300 characters. · 300 अक्षरों तक का सवाल लिखें।" }
        require(documents.size in 1..MAX_DOCUMENTS && documents.map { it.reportId }.distinct().size == documents.size) { "Choose one to five documents. · एक से पाँच कागज़ चुनें।" }
        val sources = documents.mapIndexed { index, document -> "D${index + 1}" to document }.toMap()
        val lines = sources.flatMap { (ref, document) -> document.pages.sortedBy { it.page }.flatMap { page ->
            // Line numbers match the review screen and the report search, so a citation points at the same line there.
            page.reviewedText.split('\n').mapIndexedNotNull { index, text -> text.trim().takeIf { it.isNotEmpty() }?.let { DocumentQuestionLine(ref, page.page, index + 1, it.take(2000)) } }
        } }
        sources.forEach { (ref, document) -> require(lines.any { it.document == ref }) { "“${document.name}” has no readable text yet. Open it with “Read and copy facts” first. · “${document.name}” में अभी पढ़ने लायक लिखाई नहीं है। पहले “पढ़ें और बातें कॉपी करें” से खोलें।" } }
        require(lines.sumOf { it.text.length } <= MAX_CHARACTERS) { "These documents have too much text. Choose fewer documents. · इन कागज़ों में बहुत ज़्यादा लिखाई है। कम कागज़ चुनें।" }
        val canonical = buildString { append(asked).append('\n'); lines.forEach { append(it.document).append('|').append(it.page).append('|').append(it.line).append('|').append(it.text).append('\n') } }
        return DocumentQuestionRequest(asked, careAssistSourceHash(canonical), sources, lines)
    }

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
