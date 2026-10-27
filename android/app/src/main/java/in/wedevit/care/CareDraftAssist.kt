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
    /** Apply only to the exact source the user reviewed; publishing remains a separate action. */
    fun applyAssisted(draft: Conversation, selected: CareAssistedDraft): Conversation {
        require(selected.sourceHash == careAssistSourceHash(draft.source)) { "The conversation changed. Prepare a fresh draft." }
        require(selected.provider == "sarvam" && selected.model == "sarvam-105b")
        return careAssistFieldKeys.foldIndexed(draft) { index, current, key ->
            withSection(current, index, selected.fields.getValue(key))
        }
    }

    fun appendReviewedTranscript(source: String, original: String, reviewed: String, speaker: String, language: String, confirmed: Boolean): String {
        require(confirmed && original.isNotBlank() && reviewed.isNotBlank()) { "Check the words and speaker first." }
        require(speaker in listOf("Patient", "Family / carer", "Doctor", "Unknown") && (language == "unknown" || language in careAssistLanguageCodes))
        fun oneLine(words: String) = words.replace(Regex("[\\r\\n]+"), " ")
        val retained = "[Unknown · $language · machine original] ${oneLine(original)}"
        val checked = "[$speaker · $language · human-reviewed] ${oneLine(reviewed)}"
        return listOf(source.trimEnd(), retained, checked).filter { it.isNotBlank() }.joinToString("\n").also {
            require(it.length <= 12000) { "The conversation is full. Keep these words separately before starting another note." }
        }
    }

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

enum class LocalSuggestionConfidence { LOW, MEDIUM, HIGH }

/** A verbatim span of the source (the words of one line). start/end are UTF-8 byte offsets, as in saved evidence; charStart/charEnd index the Kotlin string. Always needs human review. */
data class LocalDraftSuggestion(
    val section: Int, val sectionLabel: String, val line: CareSourceLine, val quote: String,
    val start: Int, val end: Int, val charStart: Int, val charEnd: Int,
    val rank: Int, val score: Int, val confidence: LocalSuggestionConfidence, val matchedCues: List<String>,
) { val needsHumanReview: Boolean get() = true }

data class LocalDraftResult(val bySection: List<List<LocalDraftSuggestion>>) { val isEmpty: Boolean get() = bySection.all { it.isEmpty() } }

/** Rule-based suggestions only (no AI, model or transcription): lines whose own words match the reviewable cue lists below. It never writes new text and never guesses who spoke. */
object LocalDraftSuggestions {
    /** A cue is a whole-word phrase (case-insensitive; Devanagari ok). STRONG scores 2, SUPPORT scores 1 per distinct cue; a line needs MIN_SCORE. */
    const val MIN_SCORE = 2
    const val SECTION_LABEL_BONUS = 3
    const val DEFAULT_PER_SECTION = 3

    val MATTERS_STRONG = listOf("what matters", "matters to me", "most important", "important to me", "i want to", "i don't want", "i do not want", "i would like", "i wish", "i am afraid", "i'm afraid", "i worry", "i am worried", "i'm worried", "quality of life", "at home", "no pain", "मेरे लिए ज़रूरी", "मेरे लिए जरूरी", "मुझे चाहिए", "मैं चाहता", "मैं चाहती", "नहीं चाहता", "नहीं चाहती", "घर पर", "डर")
    val MATTERS_SUPPORT = listOf("family", "pain", "comfortable", "peace", "independent", "परिवार", "दर्द", "आराम")
    val MATTERS_EXCLUDE = listOf("want to know", "like to know", "जानना चाहता", "जानना चाहती")
    val PEOPLE_STRONG = listOf("my wife", "my husband", "my daughter", "my son", "my brother", "my sister", "my mother", "my father", "my nephew", "my neighbour", "my neighbor", "caregiver", "carer", "nurse", "social worker", "this is my", "came with", "here with", "with me today", "joined us", "was present", "present today", "मेरी बेटी", "मेरा बेटा", "मेरी पत्नी", "मेरे पति", "मेरा भाई", "मेरी बहन", "मेरी माँ", "मेरे पिता", "साथ आए", "साथ आई", "मौजूद")
    val PEOPLE_SUPPORT = listOf("wife", "husband", "daughter", "son", "brother", "sister", "mother", "father", "family", "बेटी", "बेटा", "पत्नी", "पति", "भाई", "बहन", "माँ", "पिताजी")
    /** Names are only recognised after a title or an introduction; a capitalised word alone is not treated as a person. */
    val PEOPLE_NAME_PATTERNS = listOf(Regex("\\b(?:Dr|Mr|Mrs|Ms)\\.?\\s*\\p{Lu}\\p{L}+"), Regex("(?i:this is|my name is)\\s+\\p{Lu}\\p{L}+"))
    val DISCUSSED_STRONG = listOf("we talked about", "we discussed", "discussed", "explained that", "diagnosis", "the scan shows", "test result", "test results", "जांच", "बीमारी", "इलाज", "बताया")
    val DISCUSSED_SUPPORT = listOf("treatment", "medicine", "surgery", "operation", "hospital", "dialysis", "ventilator", "icu", "symptoms", "scan", "दवा", "ऑपरेशन", "अस्पताल")
    val OPEN_STRONG = listOf("i want to know", "i would like to know", "can you explain", "could you explain", "please explain", "what will happen", "what happens", "how long", "not sure", "have not decided", "haven't decided", "still thinking", "need to think", "undecided", "don't know", "do not know", "need more time", "let me think", "जानना चाहता", "जानना चाहती", "समझाइए", "समझा सकते", "पता नहीं", "तय नहीं", "सोच रहा", "सोच रही", "अभी तय")
    val OPEN_SUPPORT = listOf("क्या", "why", "how", "कैसे", "क्यों")
    const val OPEN_QUESTION_MARK_SCORE = 2
    val NEXT_STRONG = listOf("next steps", "we will", "we'll", "next visit", "before the next visit", "next time", "follow up", "follow-up", "book an appointment", "will call", "will arrange", "will send", "please bring", "come back", "अगली बार", "अगली मुलाकात", "फिर मिलेंगे", "फॉलो", "बुलाएंगे", "करेंगे")
    val NEXT_SUPPORT = listOf("next", "soon", "tomorrow", "next week", "appointment", "after the test", "कल", "अगले")

    private val prefix = Regex("^(?:What matters|Who was there|What we discussed|Still to discuss|Next steps):\\s*", RegexOption.IGNORE_CASE)
    private fun cue(text: String) = Regex("(?<![\\p{L}\\p{M}])" + Regex.escape(text) + "(?![\\p{L}\\p{M}])", RegexOption.IGNORE_CASE)
    private val compiled = HashMap<String, Regex>()
    private fun regex(text: String) = synchronized(compiled) { compiled.getOrPut(text) { cue(text) } }

    internal fun utf8Length(s: String, from: Int, to: Int): Int { var n = 0; var i = from; while (i < to) { val c = s[i]; n += when { c.code < 0x80 -> 1; c.code < 0x800 -> 2; c.isHighSurrogate() -> { i++; 4 }; else -> 3 }; i++ }; return n }

    private class Rule(val strong: List<String>, val support: List<String>, val exclude: List<String> = emptyList(), val patterns: List<Regex> = emptyList(), val questionMark: Int = 0)
    private val rules by lazy { listOf(Rule(MATTERS_STRONG, MATTERS_SUPPORT, MATTERS_EXCLUDE), Rule(PEOPLE_STRONG, PEOPLE_SUPPORT, patterns = PEOPLE_NAME_PATTERNS), Rule(DISCUSSED_STRONG, DISCUSSED_SUPPORT), Rule(OPEN_STRONG, OPEN_SUPPORT, questionMark = OPEN_QUESTION_MARK_SCORE), Rule(NEXT_STRONG, NEXT_SUPPORT)) }

    /** Up to [perSection] best lines for each of the five sections (NoteRules.labels order); a section with no matching line stays empty. Deterministic: ties keep source order. */
    fun suggest(source: String, perSection: Int = DEFAULT_PER_SECTION): LocalDraftResult {
        require(perSection > 0) { "Ask for at least one suggestion per section." }
        val lines = CareDraftAssist.lines(source)
        val bySection = rules.mapIndexed { section, rule ->
            lines.mapNotNull { line -> score(source, section, rule, line) }
                .sortedWith(compareByDescending<LocalDraftSuggestion> { it.score }.thenBy { it.charStart })
                .take(perSection).mapIndexed { i, s -> s.copy(rank = i + 1) }
        }
        return LocalDraftResult(bySection)
    }

    private fun score(source: String, section: Int, rule: Rule, line: CareSourceLine): LocalDraftSuggestion? {
        val words = line.words
        val labelled = prefix.find(words)
        val labelSection = labelled?.let { m -> NoteRules.labels.indexOfFirst { m.value.startsWith(it, ignoreCase = true) } } ?: -1
        val body = if (labelled != null) words.substring(labelled.value.length) else words
        if (body.isBlank() || (labelled != null && labelSection != section)) return null
        if (rule.exclude.any { regex(it).containsMatchIn(body) }) return null
        val cues = mutableListOf<String>(); var score = 0
        rule.strong.filter { regex(it).containsMatchIn(body) }.forEach { cues += it; score += 2 }
        rule.support.filter { regex(it).containsMatchIn(body) }.forEach { cues += it; score += 1 }
        rule.patterns.forEach { p -> p.find(body)?.let { cues += it.value; score += 2 } }
        if (rule.questionMark > 0 && (body.contains('?') || body.contains('？'))) { cues += "?"; score += rule.questionMark }
        if (labelSection == section) { cues += "label"; score += SECTION_LABEL_BONUS }
        if (score < MIN_SCORE) return null
        // The span is the line's own words, so it is verbatim and still links through CareDraftAssist.references.
        val charStart = line.start + (line.text.length - words.length)
        val charEnd = line.end
        val quote = source.substring(charStart, charEnd)
        val start = utf8Length(source, 0, charStart)
        return LocalDraftSuggestion(section, NoteRules.labels[section], line, quote, start, start + utf8Length(source, charStart, charEnd), charStart, charEnd, 0, score,
            when { score >= 4 -> LocalSuggestionConfidence.HIGH; score == 3 -> LocalSuggestionConfidence.MEDIUM; else -> LocalSuggestionConfidence.LOW }, cues)
    }

    /** Adds the suggestion's verbatim words to a section only if they are still exactly where they were found. Reviewer still publishes separately. */
    fun accept(draft: Conversation, suggestion: LocalDraftSuggestion): Conversation {
        require(suggestion.charEnd <= draft.source.length && draft.source.substring(suggestion.charStart, suggestion.charEnd) == suggestion.quote) { "The conversation changed. Ask for suggestions again." }
        return CareDraftAssist.assign(draft, suggestion.section, suggestion.line)
    }
}

/** A question the clinician prepared before the conversation. [section] is the 0-based note section (NoteRules.labels order) an accepted answer belongs to. */
data class CareQuestion(val id: String, val text: String, val section: Int)

/** A verbatim span of the source (the words of one line) suggested as an answer to a question. start/end are UTF-8 byte offsets as in saved evidence; charStart/charEnd index the Kotlin string. [askedOnLine] is the line where the question was asked when this line is the reply after it, else null. Always needs human review. */
data class QuestionMatch(
    val line: CareSourceLine, val quote: String, val start: Int, val end: Int, val charStart: Int, val charEnd: Int,
    val rank: Int, val score: Int, val confidence: LocalSuggestionConfidence, val matchedWords: List<String>, val followsQuestion: Boolean,
    val askedOnLine: Int? = null,
) { val needsHumanReview: Boolean get() = true }

data class QuestionAnswer(val question: CareQuestion, val matches: List<QuestionMatch>) { val isUnanswered: Boolean get() = matches.isEmpty() }

data class QuestionMatchResult(val answers: List<QuestionAnswer>) {
    val unanswered: List<CareQuestion> get() = answers.filter { it.isUnanswered }.map { it.question }
    val answered: List<QuestionAnswer> get() = answers.filter { !it.isUnanswered }
    fun forQuestion(id: String): QuestionAnswer? = answers.firstOrNull { it.question.id == id }
}

/**
 * Rule-based local matching of transcript lines to the clinician's questions (no AI, model, network or transcription). It only points at verbatim lines; it never writes, paraphrases or summarises, and never pads: no match means unanswered.
 *
 * How a line is chosen, in order:
 * 1. Find where each question was ASKED: a line that asks something (it has '?' or '？', or, in unpunctuated text, starts with or contains a question word)
 *    and whose words fit exactly one question best, by the question's own words plus, for the 8 standard questions, [GOALS_QUESTION_VOCABULARY] wording in English, Hindi and Hinglish.
 * 2. Suggest the REPLY: the first line after it (within [REPLY_WINDOW] lines) that does not itself ask something, plus the lines straight after it (same speaker, when labelled).
 *    When the transcript has speaker labels ("[Doctor · …]", "D:", "Patient:"), a doctor line is not taken as the reply. A line that puts the topic off
 *    ([GOALS_DEFERRAL_PHRASES]) closes the question: nothing is suggested. Another question being asked ends the search.
 * 3. Only for a question that was never recognisably asked: a line that itself shares enough of the question's words (the older rule).
 * A line that asks something, or that is mostly the question's own words (unless labelled as the patient or family), is never offered.
 */
object LocalQuestionMatcher {
    /** A line needs MIN_SCORE in total, of which MIN_OVERLAP_SCORE must come from shared content words, so the follows-a-question boost can never create a match alone. */
    const val MIN_SCORE = 4
    const val MIN_OVERLAP_SCORE = 3
    /** Added when the line directly follows a question-shaped line; taken off a line that is itself question-shaped (it is usually the question, not the answer). */
    const val FOLLOWS_QUESTION_BOOST = 2
    const val QUESTION_LINE_PENALTY = 2
    /** A line must score at least this (own words + standard wording) for one question, and more than for any other, to count as asking it. */
    const val ASK_MIN_SCORE = 3
    /** The reply must start within this many lines after the question. */
    const val REPLY_WINDOW = 3
    /** A line with at least this many content words, this share of them from the question, is a restated question, not an answer. */
    const val ECHO_MIN_WORDS = 3
    const val ECHO_SHARE = 0.7
    /** Word weight by length in characters (code points): longer words are more distinctive. */
    const val LONG_WORD_LENGTH = 8
    const val MEDIUM_WORD_LENGTH = 5
    const val LONG_WORD_WEIGHT = 3
    const val MEDIUM_WORD_WEIGHT = 2
    const val SHORT_WORD_WEIGHT = 1
    const val HIGH_CONFIDENCE_SCORE = 8
    const val MEDIUM_CONFIDENCE_SCORE = 6
    const val DEFAULT_PER_QUESTION = 3

    val ENGLISH_STOPWORDS = setOf("a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "for", "with", "by", "from", "as", "is", "are", "was", "were", "be", "been", "am", "do", "does", "did", "done", "doing", "has", "have", "had", "will", "would", "shall", "should", "can", "could", "may", "might", "must", "cannot", "don", "doesn", "didn", "isn", "aren", "won", "what", "who", "whom", "whose", "which", "when", "where", "why", "how", "he", "she", "it", "they", "we", "you", "i", "me", "my", "mine", "him", "his", "her", "hers", "them", "their", "us", "our", "your", "this", "that", "these", "those", "there", "here", "so", "not", "no", "yes", "any", "some", "about", "into", "than", "then", "also", "very", "just", "all", "up", "out", "too", "ok", "okay")
    val HINDI_STOPWORDS = setOf("क्या", "है", "हैं", "था", "थी", "थे", "हो", "होगा", "होगी", "और", "या", "में", "से", "को", "का", "की", "के", "पर", "ने", "तो", "भी", "ही", "यह", "वह", "ये", "वे", "मैं", "मुझे", "मेरा", "मेरी", "मेरे", "हम", "आप", "आपको", "उन्हें", "उनको", "उसे", "कि", "जो", "कौन", "कब", "कहाँ", "कहां", "कैसे", "क्यों", "कितना", "नहीं", "हाँ", "हां", "चाहता", "चाहती", "चाहते", "कर", "करना", "करते", "रहा", "रही", "रहे", "एक", "बहुत", "अब", "फिर", "जी", "सकता", "सकती", "सकते")
    /** A line whose first word is one of these (or that contains a question mark) is question-shaped. */
    val QUESTION_STARTERS = setOf("what", "who", "whom", "whose", "which", "when", "where", "why", "how", "do", "does", "did", "is", "are", "was", "were", "can", "could", "will", "would", "should", "shall", "may", "क्या", "कौन", "कब", "कहाँ", "कहां", "कैसे", "क्यों", "कितना", "कितनी", "कितने")

    private val wordPattern = Regex("[\\p{L}\\p{M}\\p{N}]+")
    private val englishSuffixes = listOf("ing", "ed", "es", "s", "e")

    private fun length(word: String) = word.codePointCount(0, word.length)
    private fun weight(word: String) = length(word).let { n -> when { n >= LONG_WORD_LENGTH -> LONG_WORD_WEIGHT; n >= MEDIUM_WORD_LENGTH -> MEDIUM_WORD_WEIGHT; else -> SHORT_WORD_WEIGHT } }
    /** Content words: lower-cased, at least two characters, not in either stopword list. */
    private fun contentWords(text: String) = wordPattern.findAll(text).map { it.value.lowercase() }.filter { length(it) >= 2 && it !in ENGLISH_STOPWORDS && it !in HINDI_STOPWORDS }.toList()
    /** Plain-ASCII words only lose one common English ending, so "decides", "decide" and "deciding" meet. Other scripts are compared exactly. */
    private fun stem(word: String): String {
        if (word.length < 5 || word.any { it.code >= 128 }) return word
        val suffix = englishSuffixes.firstOrNull { word.endsWith(it) && word.length - it.length >= 4 } ?: return word
        return word.dropLast(suffix.length)
    }
    private fun questionShaped(body: String) = body.contains('?') || body.contains('？') || wordPattern.find(body)?.value?.lowercase()?.let { it in QUESTION_STARTERS } == true

    /** Words that make an unpunctuated line a question in Hindi or Hinglish wherever they stand ("aage kya karna hai"). */
    val INTERROGATIVES = setOf("kya", "kaun", "kaunsa", "kise", "kisko", "kaise", "kab", "kahan", "kyun", "kyon", "kitna", "क्या", "कौन", "किसे", "किसको", "कैसे", "कब", "कहाँ", "कहां", "क्यों", "कितना")

    private enum class Speaker { DOCTOR, PATIENT_OR_FAMILY, NONE }
    private val bracketLabel = Regex("^\\[(Patient|Family / carer|Doctor|Unknown) · [^\\]]+\\]\\s*")
    private val plainLabel = Regex("^(D|P|F|Dr|Doctor|Patient|Family|Carer|Family / carer|Relative|डॉक्टर|मरीज़|मरीज|परिवार)\\s*[:：]\\s*", RegexOption.IGNORE_CASE)
    private val doctorLabels = setOf("d", "dr", "doctor", "डॉक्टर")
    private fun speaker(text: String): Speaker {
        bracketLabel.find(text)?.let { return when (it.groupValues[1]) { "Doctor" -> Speaker.DOCTOR; "Unknown" -> Speaker.NONE; else -> Speaker.PATIENT_OR_FAMILY } }
        return plainLabel.find(text)?.let { if (it.groupValues[1].lowercase() in doctorLabels) Speaker.DOCTOR else Speaker.PATIENT_OR_FAMILY } ?: Speaker.NONE
    }
    private fun body(text: String) = text.replace(bracketLabel, "").replace(plainLabel, "")
    /** The speaker label exactly as written ("patient", "f", …), or blank. */
    private fun who(text: String) = (bracketLabel.find(text) ?: plainLabel.find(text))?.groupValues?.get(1)?.lowercase().orEmpty()

    /** Lower-case words with the nukta dropped and chandrabindu written as anusvara, so "ज़रूरी"/"जरूरी" and "कहाँ"/"कहां" meet. */
    private fun tokens(text: String) = wordPattern.findAll(text.lowercase().replace("\u093C", "").replace('\u0901', '\u0902')).map { it.value }.toList()
    private fun phrase(p: String) = p.trim().lowercase().replace("\u093C", "").replace('\u0901', '\u0902').split(Regex("[^\\p{L}\\p{M}\\p{N}*]+")).filter { it.isNotEmpty() }
    /** True when [words] appear next to each other in [line]; a word ending in '*' also matches longer words. */
    private fun has(line: List<String>, words: List<String>): Boolean {
        if (words.isEmpty() || words.size > line.size) return false
        return (0..line.size - words.size).any { i -> words.indices.all { k -> val w = words[k]; if (w.endsWith('*')) line[i + k].startsWith(w.dropLast(1)) else line[i + k] == w } }
    }
    private val deferrals by lazy { GOALS_DEFERRAL_PHRASES.map(::phrase) }
    private class Vocab(val triggers: List<Pair<List<String>, Int>>, val concepts: List<Pair<String, List<String>>>)
    private val vocab by lazy { GOALS_QUESTION_VOCABULARY.mapValues { (_, v) -> Vocab(v.triggers.map { phrase(it.first) to it.second }, v.concepts.map { it to phrase(it) }) } }

    /** Asks something: a question mark, or, in a line with no sentence punctuation at all (plain speech-to-text), a question word first or a Hindi/Hinglish question word anywhere. */
    private fun asks(body: String): Boolean {
        if (body.contains('?') || body.contains('？')) return true
        if (body.any { it == '.' || it == '!' || it == '।' }) return false
        val t = tokens(body)
        return t.firstOrNull()?.let { it in QUESTION_STARTERS } == true || t.any { it in INTERROGATIVES }
    }

    /**
     * For each question, up to [perQuestion] lines (replies first, in transcript order, then direct word matches best first). A question nothing answers gets no matches:
     * it is reported unanswered, never matched to noise. Same input gives the same output.
     */
    fun match(source: String, questions: List<CareQuestion>, perQuestion: Int = DEFAULT_PER_QUESTION): QuestionMatchResult {
        require(perQuestion > 0) { "Ask for at least one match per question." }
        val lines = CareDraftAssist.lines(source)
        val speakers = lines.map { speaker(it.text) }
        val names = lines.map { who(it.text) }
        val labelled = speakers.any { it != Speaker.NONE }
        val bodies = lines.map { body(it.text) }
        val lineStems = bodies.map { b -> contentWords(b).map { stem(it) }.toSet() }
        val lineTokens = bodies.map(::tokens)
        val shaped = bodies.map { questionShaped(it) }
        val asking = bodies.map(::asks)
        val deferring = lineTokens.map { t -> deferrals.any { has(t, it) } }
        val wanted = questions.map { q -> contentWords(q.text).groupBy { stem(it) }.mapValues { (_, words) -> words.first() to words.maxOf { weight(it) } } }
        val extra = questions.map { vocab[it.text.trim()] }
        fun overlap(k: Int, i: Int) = wanted[k].filterKeys { it in lineStems[i] }.values.sumOf { it.second }
        fun echo(k: Int, i: Int) = speakers[i] != Speaker.PATIENT_OR_FAMILY && lineStems[i].size >= ECHO_MIN_WORDS && lineStems[i].count { it in wanted[k] } >= ECHO_SHARE * lineStems[i].size

        // 1. Which question each asking line asks: one clear best, never a tie.
        val askedBy = IntArray(lines.size) { -1 }
        val askScore = IntArray(lines.size)
        lines.indices.filter { asking[it] }.forEach { i ->
            val scores = questions.indices.map { k -> overlap(k, i) + (extra[k]?.triggers?.filter { has(lineTokens[i], it.first) }?.sumOf { it.second } ?: 0) }
            val best = scores.maxOrNull() ?: return@forEach
            if (best >= ASK_MIN_SCORE && scores.count { it == best } == 1) { askedBy[i] = scores.indexOf(best); askScore[i] = best }
        }

        // 2. The reply after each asking line.
        val replies = questions.indices.map { mutableListOf<QuestionMatch>() }
        val claimed = mutableSetOf<Int>()
        val closed = BooleanArray(questions.size)
        askedBy.forEachIndexed { i, k ->
            if (k < 0) return@forEachIndexed
            var j = i + 1; var taken = 0; var first = -1
            while (j < lines.size && taken < perQuestion && (taken > 0 || j <= i + REPLY_WINDOW)) {
                val doctor = labelled && speakers[j] == Speaker.DOCTOR
                val skip = asking[j] || doctor || echo(k, j)
                // Another question, or the patient or family answering with a question of their own, ends the reply.
                if (askedBy[j] >= 0 || (skip && taken > 0) || (asking[j] && !doctor)) break
                // Lines after the first reply belong to it only while the same labelled speaker goes on.
                if (taken > 0 && labelled && names[j] != names[first]) break
                if (!skip) {
                    if (deferring[j]) { if (taken == 0) closed[k] = true; break }
                    val concepts = extra[k]?.concepts?.filter { has(lineTokens[j], it.second) }?.map { it.first }.orEmpty()
                    val score = askScore[i] + FOLLOWS_QUESTION_BOOST - taken
                    replies[k] += quoteMatch(source, lines[j], score, if (concepts.isNotEmpty() && taken == 0) LocalSuggestionConfidence.HIGH else LocalSuggestionConfidence.MEDIUM, concepts, i + 1 == j && taken == 0, lines[i].number)
                    if (taken == 0) first = j
                    claimed += j; taken++
                }
                j++
            }
        }

        // 3. Direct word matches, never a line that asks, restates the question, puts it off, or is another question's reply.
        return QuestionMatchResult(questions.mapIndexed { k, q ->
            val direct = if (wanted[k].isEmpty() || closed[k]) emptyList() else lines.indices.filter { i -> !asking[i] && !deferring[i] && i !in claimed && !echo(k, i) }
                .mapNotNull { i -> score(source, lines[i], wanted[k], lineStems[i], i > 0 && shaped[i - 1], shaped[i]) }
                .sortedWith(compareByDescending<QuestionMatch> { it.score }.thenBy { it.charStart })
            QuestionAnswer(q, (replies[k] + direct).distinctBy { it.line.number }.take(perQuestion).mapIndexed { i, m -> m.copy(rank = i + 1) })
        })
    }

    private fun quoteMatch(source: String, line: CareSourceLine, score: Int, confidence: LocalSuggestionConfidence, words: List<String>, follows: Boolean, askedOn: Int): QuestionMatch {
        val charStart = line.start + (line.text.length - line.words.length)
        val start = LocalDraftSuggestions.utf8Length(source, 0, charStart)
        return QuestionMatch(line, source.substring(charStart, line.end), start, start + LocalDraftSuggestions.utf8Length(source, charStart, line.end), charStart, line.end, 0, score, confidence, words, follows, askedOn)
    }

    /** Just the questions nothing answered, in the order given, for the "type an answer" list. */
    fun unanswered(source: String, questions: List<CareQuestion>): List<CareQuestion> = match(source, questions).unanswered

    private fun score(source: String, line: CareSourceLine, wanted: Map<String, Pair<String, Int>>, lineStems: Set<String>, follows: Boolean, isQuestion: Boolean): QuestionMatch? {
        val hits = wanted.filterKeys { it in lineStems }
        val overlap = hits.values.sumOf { it.second }
        if (overlap < MIN_OVERLAP_SCORE) return null
        val total = overlap + (if (follows) FOLLOWS_QUESTION_BOOST else 0) - (if (isQuestion) QUESTION_LINE_PENALTY else 0)
        if (total < MIN_SCORE) return null
        val charStart = line.start + (line.text.length - line.words.length)
        val charEnd = line.end
        val start = LocalDraftSuggestions.utf8Length(source, 0, charStart)
        return QuestionMatch(line, source.substring(charStart, charEnd), start, start + LocalDraftSuggestions.utf8Length(source, charStart, charEnd), charStart, charEnd, 0, total,
            when { total >= HIGH_CONFIDENCE_SCORE -> LocalSuggestionConfidence.HIGH; total >= MEDIUM_CONFIDENCE_SCORE -> LocalSuggestionConfidence.MEDIUM; else -> LocalSuggestionConfidence.LOW }, hits.values.map { it.first }, follows)
    }
}
