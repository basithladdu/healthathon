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

/** Query words in English or Hindi (Devanagari). Hindi words are matched to the English words reports usually print; nothing is translated or interpreted. */
object DocumentSearchWords {
    /** Same spelling for चाँद/चांद and for letters typed with or without a nukta. */
    fun normalize(text: String) = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD).replace('\u0901', '\u0902').replace("\u093c", "").replace(Regex("पेट[\\s-]*सीटी"), "pet ct")
    fun words(text: String) = Regex("[\\p{L}\\p{M}\\p{N}]+").findAll(normalize(text)).map { it.value }.toList()
    private val ignored = setOf("a", "an", "the", "my", "our", "what", "when", "where", "who", "which", "how", "is", "are", "was", "were", "to", "of", "in", "on", "at", "for", "and", "or", "tell", "me", "show", "find", "please", "last", "latest", "report", "reports", "document", "documents", "should", "can", "i", "we", "did", "do", "have", "has", "had", "with", "from", "about",
        "क्या", "है", "हैं", "था", "थी", "थे", "कब", "कहां", "कौन", "कितना", "कितनी", "मेरे", "मेरी", "मेरा", "हमारे", "हमारी", "हमारा", "की", "का", "के", "में", "पर", "से", "को", "और", "या", "मुझे", "वाली", "वाला", "वाले", "आखिरी", "पिछली", "पिछला", "रिपोर्ट", "कागज", "दिखाओ", "दिखाइए", "बताओ", "बताइए", "ढूंढो", "ढूंढें")
    private val groups = listOf(
        setOf("medicine", "medicines", "medication", "prescription", "दवा", "दवाई", "दवाइयां", "दवाएं", "पर्चा"),
        setOf("visit", "visits", "appointment", "appointments", "followup", "मुलाकात", "अपॉइंटमेंट"),
        setOf("chemotherapy", "chemo", "कीमो", "कीमोथेरेपी"),
        setOf("radiotherapy", "radiation", "रेडियोथेरेपी", "रेडिएशन", "सिकाई"),
        setOf("hemoglobin", "haemoglobin", "hb", "हीमोग्लोबिन"),
        setOf("blood", "cbc", "खून", "रक्त"),
        setOf("test", "tests", "lab", "जांच"),
        setOf("sugar", "glucose", "शुगर"),
        setOf("platelet", "platelets", "प्लेटलेट", "प्लेटलेट्स"),
        setOf("cancer", "carcinoma", "malignancy", "कैंसर"),
        setOf("biopsy", "fnac", "बायोप्सी"),
        setOf("histopathology", "hpe", "हिस्टोपैथोलॉजी"),
        setOf("staging", "stage", "tnm", "स्टेज", "स्टेजिंग"),
        setOf("pet", "petct", "पीईटी"),
        setOf("ct", "सीटी"),
        setOf("scan", "स्कैन"),
        setOf("urine", "पेशाब", "मूत्र"),
        setOf("kidney", "किडनी", "गुर्दा"),
        setOf("liver", "लिवर", "जिगर"),
        setOf("thyroid", "थायराइड"),
        setOf("pain", "दर्द"),
        setOf("morphine", "मॉर्फिन"),
        setOf("doctor", "dr", "डॉक्टर", "डॉ"),
        setOf("discharge", "डिस्चार्ज"),
        setOf("bring", "carry", "prepare", "preparation", "लाना"),
    )
    /** Each query word becomes the set of words that count as a match for it. */
    fun terms(query: String): List<Set<String>> = words(query).filterNot { it in ignored }.distinct().map { term -> groups.firstOrNull { term in it } ?: setOf(term) }
    /** File names and labels: the plain text match still works; otherwise every query word (or its English/Hindi match) must appear. */
    fun matchesName(query: String, name: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || name.contains(trimmed, ignoreCase = true)) return true
        val terms = terms(trimmed); if (terms.isEmpty()) return false
        val text = normalize(name); val found = words(name)
        return terms.all { group -> group.any { it in found || (it.length >= 4 && text.contains(it)) } }
    }
}

object DocumentEvidenceRules {
    /** Extractive retrieval only. Unsupported questions return no answer rather than generated medical advice. */
    fun find(query: String, documents: List<ReviewedDocumentText>): List<DocumentSearchExcerpt> {
        val terms = DocumentSearchWords.terms(query)
        if (terms.isEmpty()) return emptyList()
        return documents.filter { it.reviewedAt > 0 }.flatMap { document -> document.pages.flatMap { page ->
            val lines = page.reviewedText.split('\n')
            lines.mapIndexedNotNull { index, line ->
                if (line.isBlank()) return@mapIndexedNotNull null
                val first = (index - 1).coerceAtLeast(0); val last = (index + 1).coerceAtMost(lines.lastIndex)
                val window = lines.subList(first, last + 1).joinToString("\n"); val found = DocumentSearchWords.words(window)
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
