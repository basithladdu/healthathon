package `in`.wedevit.care

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** References are derived from the exact source frozen into each saved version. */
data class CareSourceLine(val number: Int, val start: Int, val end: Int, val text: String) {
    val id: String get() = "$number:$start:$end:$text"
    val words: String get() = text.replace(Regex("^\\[(Patient|Family / carer|Doctor) · [^\\]]+\\]\\s*"), "")
}

data class CareSourceReference(val line: CareSourceLine, val quote: String)

object CareDraftAssist {
    fun lines(source: String): List<CareSourceLine> {
        var number = 0
        return Regex("[^\\r\\n]*(?:\\r?\\n|$)").findAll(source).mapNotNull { match ->
            if (match.value.isEmpty()) return@mapNotNull null
            number++
            val raw = match.value.trimEnd('\r', '\n')
            val text = raw.trim()
            if (text.isEmpty()) null else {
                val start = match.range.first + raw.indexOf(text)
                CareSourceLine(number, start, start + text.length, text)
            }
        }.toList()
    }

    /** No fuzzy matching: edited or newly written text must not acquire a false citation. */
    fun references(source: String, value: String): List<CareSourceReference> {
        val lines = lines(source)
        return value.lines().map { it.trim() }.filter { it.isNotBlank() }.flatMap { quote ->
            lines.filter { line ->
                line.text == quote || line.words == quote || NoteRules.labels.any { label ->
                    line.text.startsWith("$label:", ignoreCase = true) && line.text.substringAfter(':').trim() == quote
                }
            }.map { CareSourceReference(it, quote) }
        }.distinctBy { "${it.line.id}:${it.quote}" }
    }

    fun unlinked(source: String, value: String): List<String> = value.lines().map { it.trim() }
        .filter { it.isNotBlank() && references(source, it).isEmpty() }

    fun assign(draft: Conversation, section: Int, line: CareSourceLine): Conversation {
        require(section in 0..4) { "Choose a care-note section." }
        require(lines(draft.source).any { it == line }) { "The conversation changed. Choose the words again." }
        val current = NoteRules.values(draft)[section]
        val words = line.words
        if (current.lines().any { it.trim() == words }) return draft
        val value = listOf(current, words).filter { it.isNotBlank() }.joinToString("\n")
        require(value.length <= 4000) { "This section is full. Review its words before adding more." }
        return withSection(draft, section, value)
    }

    fun withSection(draft: Conversation, section: Int, value: String): Conversation = when (section) {
        0 -> draft.copy(priorities = value)
        1 -> draft.copy(participants = value)
        2 -> draft.copy(topics = value)
        3 -> draft.copy(questions = value)
        4 -> draft.copy(nextSteps = value)
        else -> error("Choose a care-note section.")
    }

    fun appendTranscript(source: String, words: String, speaker: String, language: String, at: Long): String {
        require(speaker in listOf("Patient", "Family / carer", "Doctor")) { "Choose who is speaking." }
        require(words.isNotBlank()) { "No words were recognised. Try again or type them." }
        val time = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM HH:mm"))
        val line = "[$speaker · $language · $time] ${words.trim().replace(Regex("[\\r\\n]+"), " ")}"
        return listOf(source.trimEnd(), line).filter { it.isNotBlank() }.joinToString("\n").also {
            require(it.length <= 12000) { "The conversation is full. Copy these words before starting another note." }
        }
    }

    /** Chunking retains all text and stays under TextToSpeech's input limit. */
    fun speechChunks(text: String, limit: Int): List<String> {
        require(limit > 0)
        val chunks = mutableListOf<String>()
        var remaining = text.trim()
        while (remaining.isNotEmpty()) {
            if (remaining.length <= limit) { chunks += remaining; break }
            val boundary = remaining.lastIndexOfAny(charArrayOf('\n', ' '), limit - 1).takeIf { it >= limit / 2 } ?: limit
            chunks += remaining.substring(0, boundary)
            remaining = remaining.substring(boundary).trimStart()
        }
        return chunks
    }
}
