package `in`.wedevit.care

/**
 * Extra wording for the 8 standard goals-of-care questions (DOCTOR_STANDARD_QUESTIONS), used only by [LocalQuestionMatcher].
 * No AI and no network: fixed word lists, the same output for the same input.
 *
 * - [triggers] are ways a doctor may ASK the question (English paraphrase, Hindi in Devanagari, Hinglish in Roman script).
 *   They are only used to recognise the question line, never to pick an answer.
 * - [concepts] are words an ANSWER to the question often contains. They only raise confidence in a reply that was already found.
 *
 * A phrase is space-separated words that must appear next to each other in the line; a word ending in '*' also matches longer words
 * ("worr*" matches worry, worries, worried). Matching ignores case, the Devanagari nukta and chandrabindu/anusvara spelling.
 * Every Hindi and Hinglish phrase here is written by the lane, not by a clinician or native-speaker reviewer [needs clinician review].
 * A custom question typed by the doctor gets no extra wording: only its own words are used.
 */
internal data class GoalsQuestionVocabulary(val triggers: List<Pair<String, Int>>, val concepts: List<String>)

private const val STRONG = 3
private const val WEAK = 2
private fun strong(vararg p: String) = p.map { it to STRONG }
private fun weak(vararg p: String) = p.map { it to WEAK }

internal val GOALS_QUESTION_VOCABULARY: Map<String, GoalsQuestionVocabulary> = mapOf(
    // Q1 What matters most to you at the moment?
    DOCTOR_STANDARD_QUESTIONS_WHAT_MATTERS[0] to GoalsQuestionVocabulary(
        strong("matters most", "matter most", "most important", "important to you", "important for you", "what matters", "priority", "priorities", "means the most", "care about most",
            "सबसे ज़रूरी", "सबसे जरूरी", "सबसे अहम", "सबसे महत्वपूर्ण", "सबसे important",
            "sabse zaroori", "sabse zaruri", "sabse jaruri", "sabse jaroori", "sabse important", "sabse aham", "sabse ahem", "sabse mahatvapurn") +
            weak("good day", "अहमियत", "मायने", "ahmiyat", "maayne", "mayne"),
        listOf("important", "priority", "matter*", "family", "grandchild*", "home", "peace*", "faith", "pray*", "ज़रूरी", "अहम", "परिवार", "घर", "शांति", "zaroori", "zaruri", "parivaar", "parivar", "ghar", "shanti")),
    // Q2 What are you hoping for from your care?
    DOCTOR_STANDARD_QUESTIONS_WHAT_MATTERS[1] to GoalsQuestionVocabulary(
        strong("hoping for", "hope for", "hope from", "hope*", "expect*", "want from your treatment", "want from the treatment",
            "उम्मीद", "आशा", "अपेक्षा", "umeed", "ummeed", "umeedein", "umid", "ummid") +
            weak("hoping", "from your treatment", "from the treatment", "from your care", "इलाज से", "treatment से", "ilaaj se", "ilaj se", "treatment se", "asha"),
        listOf("hope*", "wish*", "pain", "comfort*", "cure", "ventilator", "machine", "cpr", "resuscitat*", "icu", "tube", "उम्मीद", "दर्द", "आराम", "मशीन", "umeed", "dard", "aaram", "araam")),
    // Q3 Who would you like involved in decisions about your care?
    DOCTOR_STANDARD_QUESTIONS_WHO_WAS_THERE[0] to GoalsQuestionVocabulary(
        strong("involve*", "decision*", "decide*", "deciding", "speak for you", "speak on your behalf", "on your behalf",
            "फैसल*", "निर्णय*", "faisl*", "faisal*", "nirnay") +
            weak("choices", "शामिल", "तय कर*", "shamil", "tay kar*"),
        listOf("son", "daughter", "wife", "husband", "brother", "sister", "mother", "father", "children", "family", "बेटा", "बेटे", "बेटी", "पत्नी", "पति", "भाई", "बहन", "परिवार", "beta", "bete", "beti", "biwi", "patni", "pati", "bhai", "behen", "bahan")),
    // Q4 What is your understanding of your current health?
    DOCTOR_STANDARD_QUESTIONS_WHAT_WE_DISCUSSED[0] to GoalsQuestionVocabulary(
        strong("understand*", "current health", "your illness", "your condition", "बीमारी के बारे", "तबीयत के बारे", "हालत के बारे", "समझ*",
            "samajh*", "samjh*", "bimari ke baare", "bimaari ke baare", "beemari ke baare", "tabiyat ke baare", "tabiyat ke bare", "health ke baare", "condition ke baare") +
            weak("told you about", "know about", "बताया गया", "बताया है", "bataya gaya", "bataya hai"),
        listOf("cancer", "spread", "illness", "disease", "condition", "cure*", "worse", "कैंसर", "बीमारी", "ठीक", "bimari", "bimaari", "theek", "thik")),
    // Q5 What worries you most about the future?
    DOCTOR_STANDARD_QUESTIONS_WHAT_WE_DISCUSSED[1] to GoalsQuestionVocabulary(
        strong("worr*", "afraid", "fear*", "scare*", "frighten*", "चिंता", "डर", "फिक्र", "chinta", "dar", "darr", "fikar", "fikr", "fikra") +
            weak("concern*", "anxious", "घबराहट", "tension"),
        listOf("worr*", "afraid", "fear*", "scare*", "pain", "suffer*", "alone", "burden", "डर", "चिंता", "दर्द", "अकेल*", "dar", "chinta", "dard", "akel*")),
    // Q6 Is there anything you would like to talk about that we have not covered?
    DOCTOR_STANDARD_QUESTIONS_STILL_TO_DISCUSS[0] to GoalsQuestionVocabulary(
        strong("anything else", "not covered", "haven t covered", "any question*", "other question*", "else on your mind", "anything you would like to ask", "anything you want to ask",
            "कुछ और", "और कुछ", "कोई सवाल", "कोई और सवाल", "kuch aur", "aur kuch", "koi sawal", "koi sawaal", "koi aur sawal", "koi question") +
            weak("like to talk about", "पूछना", "puchna", "poochna"),
        listOf("question*", "ask*", "also", "सवाल", "sawal", "sawaal")),
    // Q7 What would you like to happen next?
    DOCTOR_STANDARD_QUESTIONS_NEXT_STEPS[0] to GoalsQuestionVocabulary(
        strong("happen next", "next step*", "what next", "what now", "plan from here", "आगे क्या", "आगे का प्लान", "अगला कदम", "aage kya", "aage ka plan", "agla kadam", "next kya") +
            weak("from here", "going forward", "आगे कैसे", "aage kaise"),
        listOf("visit*", "nurse", "scan", "review", "meet*", "appointment", "hospice", "home", "hospital", "refer*", "अस्पताल", "घर", "मिलेंगे", "ghar", "hospital", "milenge")),
    // Q8 Who should hear about what we have agreed?
    DOCTOR_STANDARD_QUESTIONS_NEXT_STEPS[1] to GoalsQuestionVocabulary(
        strong("should hear", "should know", "needs to know", "need to know", "should we tell", "should i tell", "share this", "share the plan", "inform*",
            "किसे बता*", "किसको बता*", "किस को बता*", "सूचित", "kise bata*", "kisko bata*", "kis ko bata*") +
            weak("copy", "कॉपी", "जानकारी देनी"),
        listOf("tell", "inform*", "copy", "send", "share", "doctor", "gp", "बता*", "कॉपी", "bata*")),
)

/**
 * Lines that put the topic off rather than answer it ("let's leave it", "God knows"). A reply like this closes the question: nothing is suggested for it.
 * [needs clinician review] for the Hindi and Hinglish wording.
 */
internal val GOALS_DEFERRAL_PHRASES: List<String> = listOf(
    "leave it", "not now", "not today", "another time", "talk about it later", "come back to that", "rather not", "don t want to talk", "don t want to think",
    "god knows", "no idea", "don t know", "what will be will be", "don t think about",
    // Handing the question back to the doctor is not an answer to it.
    "you know best", "you know better", "you re the doctor", "you are the doctor", "you decide", "whatever you think", "whatever is best", "whatever you say",
    "aap jaano", "aap hi jaane", "aap hi batao", "jo aap theek samjhe*", "jo hona hai",
    "mat poochiye", "mat puchiye", "mat pucho", "mat poocho", "baad mein baat", "abhi nahi", "pata nahi", "pata nahin", "bhagwan jaane", "bhagwan ki marzi", "upar wala jaane", "upar wale ki marzi",
    "मत पूछिए", "मत पूछो", "बाद में बात", "अभी नहीं", "पता नहीं", "भगवान जाने", "भगवान की मर्ज़ी", "ऊपर वाला जाने", "ऊपर वाले की मर्ज़ी",
    "आप ही जानें", "आप ही बताइए", "आप जो ठीक समझें", "जो ठीक समझें", "जो होना है",
)
