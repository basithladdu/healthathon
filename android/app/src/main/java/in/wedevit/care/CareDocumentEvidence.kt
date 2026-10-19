package `in`.wedevit.care

import java.security.MessageDigest

/** OCR is a transcription candidate, never a diagnosis, dose calculation or treatment recommendation. */
data class DocumentPageText(val page: Int, val originalText: String, val reviewedText: String = originalText)
data class ReviewedDocumentText(
    val reportId: String, val personId: String, val fileSha256: String,
    val pages: List<DocumentPageText>, val pageCount: Int, val reviewedAt: Long = 0,
)
data class DocumentFactSource(
    val reportId: String, val personId: String, val fileSha256: String,
    val page: Int, val line: Int, val quote: String, val reviewedAt: Long,
)
data class DocumentSearchExcerpt(val reportId: String, val page: Int, val line: Int, val quote: String)

object DocumentEvidenceRules {
    /** Extractive retrieval only. Unsupported questions return no answer rather than generated medical advice. */
    fun find(query: String, documents: List<ReviewedDocumentText>): List<DocumentSearchExcerpt> {
        fun words(text: String) = Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(text.lowercase()).map { it.value }.toList()
        val ignored = setOf("a", "an", "the", "my", "our", "what", "when", "where", "who", "which", "how", "is", "are", "was", "were", "to", "of", "in", "on", "at", "for", "and", "or", "tell", "me", "show", "find", "please", "last", "latest", "report", "reports", "document", "documents", "should", "can", "i", "we", "did", "do", "have", "has", "had", "with", "from", "about", "क्या", "है", "कब", "मेरे", "मेरी", "मेरा", "की", "का", "के", "में", "मुझे")
        val groups = listOf(setOf("medicine", "medicines", "medication", "prescription", "दवा"), setOf("visit", "visits", "appointment", "appointments", "followup", "मुलाकात"), setOf("chemotherapy", "chemo", "कीमो"), setOf("hemoglobin", "haemoglobin", "hb", "हीमोग्लोबिन"), setOf("bring", "carry", "prepare", "preparation", "लाना"))
        val terms = words(query).filterNot { it in ignored }.distinct().map { term -> groups.firstOrNull { term in it } ?: setOf(term) }
        if (terms.isEmpty()) return emptyList()
        return documents.filter { it.reviewedAt > 0 }.flatMap { document -> document.pages.flatMap { page ->
            val lines = page.reviewedText.split('\n')
            lines.mapIndexedNotNull { index, line ->
                if (line.isBlank()) return@mapIndexedNotNull null
                val first = (index - 1).coerceAtLeast(0); val last = (index + 1).coerceAtMost(lines.lastIndex)
                val window = lines.subList(first, last + 1).joinToString("\n"); val found = words(window)
                if (window.length <= 2000 && terms.all { group -> group.any { it in found } }) DocumentSearchExcerpt(document.reportId, page.page, first + 1, window) else null
            }.distinctBy { it.page to it.line }.take(4)
        } }.take(20)
    }
    fun validate(value: ReviewedDocumentText) {
        require(value.reportId.isNotBlank() && value.personId.isNotBlank()) { "Choose the original report." }
        require(value.fileSha256.matches(Regex("[a-f0-9]{64}"))) { "The original report could not be verified." }
        require(value.pageCount in 1..2000 && value.pages.isNotEmpty() && value.pages.size <= 12) { "Read up to 12 pages at a time." }
        require(value.pages.map { it.page }.distinct().size == value.pages.size && value.pages.all { it.page in 1..value.pageCount }) { "The page order changed. Read the original again." }
        require(value.pages.sumOf { it.originalText.length + it.reviewedText.length } <= 400_000) { "Use a shorter document." }
    }
    fun confirm(value: ReviewedDocumentText, checked: Boolean, at: Long): ReviewedDocumentText {
        validate(value)
        require(checked && at > 0) { "Compare the text with the original before confirming." }
        require(value.pages.any { it.reviewedText.isNotBlank() }) { "Add the words you can read from the original." }
        return value.copy(reviewedAt = at)
    }
    fun source(value: ReviewedDocumentText, page: Int, firstLine: Int, quote: String): DocumentFactSource {
        validate(value)
        require(value.reviewedAt > 0) { "Review this text before copying a fact." }
        val text = value.pages.singleOrNull { it.page == page }?.reviewedText ?: error("Open a page from this report.")
        val lines = text.split('\n')
        val count = quote.split('\n').size
        require(firstLine >= 1 && quote.isNotBlank() && quote.length <= 2000 && firstLine - 1 + count <= lines.size &&
            lines.subList(firstLine - 1, firstLine - 1 + count).joinToString("\n") == quote) { "This excerpt changed. Select it from the reviewed page again." }
        return DocumentFactSource(value.reportId, value.personId, value.fileSha256, page, firstLine, quote, value.reviewedAt)
    }
    fun copiedDetail(source: DocumentFactSource, reportName: String, directions: String = ""): String = buildString {
        if (directions.isNotBlank()) append(directions.trim()).append("\n\n")
        append("Copied from: ").append(reportName.take(160)).append(" · page ").append(source.page).append(" · line ").append(source.line)
        append("\n").append(source.quote)
        append("\nSource ID: ").append(source.reportId).append("\nOriginal SHA-256: ").append(source.fileSha256)
        append("\nText checked at: ").append(source.reviewedAt)
        append("\nCopied record; this does not confirm treatment occurred or change a prescription.")
    }.also { require(it.length <= 3500) { "Choose a shorter excerpt." } }
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
