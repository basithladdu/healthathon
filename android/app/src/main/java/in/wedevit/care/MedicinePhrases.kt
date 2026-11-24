package `in`.wedevit.care

import java.util.Locale

/**
 * Common purpose and instruction phrases on medicines ("For pain.", "To ease nausea and appetite loss.", "Take with food.") shown in
 * the reader's language (clinical team, 24 Nov 2026: these stayed in English for Hindi, Telugu, Kannada and Urdu readers).
 *
 * The saved text is never changed: what the doctor or family wrote stays as it is in the record, and only the screen shows a known
 * phrase in the chosen language. Hindi is written here; Telugu, Kannada and Urdu come from the i18n dictionaries by the English.
 * Anything not in this list (a person's own words) is shown exactly as written, never machine-guessed.
 */
object MedicinePhrases {
    /** English as written on the screen, with its Hindi. Matching ignores case, spacing and the end stop. */
    private val phrases: List<Pair<String, String>> = listOf(
        // What a medicine is for.
        "For pain" to "दर्द के लिए",
        "For breakthrough pain" to "अचानक तेज़ दर्द के लिए",
        "For background pain" to "लगातार रहने वाले दर्द के लिए",
        "For breakthrough abdominal pain" to "पेट के अचानक तेज़ दर्द के लिए",
        "For abdominal discomfort" to "पेट की तकलीफ़ के लिए",
        "For nausea" to "मतली के लिए",
        "Ease nausea" to "मतली में आराम",
        "To ease nausea" to "मतली में आराम के लिए",
        "Appetite loss" to "भूख न लगना",
        "For appetite loss" to "भूख न लगने के लिए",
        "For nausea and appetite" to "मतली और भूख के लिए",
        "To ease nausea and appetite loss" to "मतली और भूख न लगने में आराम के लिए",
        "Short course to ease nausea and appetite loss, now finished" to "मतली और भूख न लगने के लिए छोटा कोर्स, अब पूरा हो गया",
        "For vomiting" to "उल्टी के लिए",
        "For constipation" to "कब्ज़ के लिए",
        "To keep bowels regular" to "पेट साफ़ रखने के लिए",
        "To keep bowels regular while taking morphine" to "मॉर्फ़ीन लेते समय पेट साफ़ रखने के लिए",
        "For breathlessness" to "साँस फूलने के लिए",
        "For cough" to "खाँसी के लिए",
        "For fever" to "बुख़ार के लिए",
        "For sleep" to "नींद के लिए",
        "For anxiety" to "घबराहट के लिए",
        "For low mood" to "उदासी के लिए",
        "For acidity" to "एसिडिटी के लिए",
        "For itching" to "खुजली के लिए",
        "For swelling" to "सूजन के लिए",
        "For mouth ulcers" to "मुँह के छालों के लिए",
        // How and when to take it.
        "Before food" to "खाने से पहले",
        "After food" to "खाने के बाद",
        "With food" to "खाने के साथ",
        "Take before food" to "खाने से पहले लें",
        "Take after food" to "खाने के बाद लें",
        "Take with food" to "खाने के साथ लें",
        "Taken with food" to "खाने के साथ ली जाती है",
        "Take 30 minutes before meals" to "खाने से 30 मिनट पहले लें",
        "Take 30 minutes before meals if nausea is worse then" to "अगर तब मतली ज़्यादा हो तो खाने से 30 मिनट पहले लें",
        "On an empty stomach" to "ख़ाली पेट",
        "Oral" to "मुँह से",
        "Once daily" to "दिन में एक बार",
        "Twice daily" to "दिन में दो बार",
        "Three times daily" to "दिन में तीन बार",
        "Four times daily" to "दिन में चार बार",
        "Each morning" to "हर सुबह",
        "At night" to "रात में",
        "At bedtime" to "सोते समय",
        "Every 4 hours as needed" to "ज़रूरत पड़ने पर हर 4 घंटे में",
        "Three times daily before meals if nausea persists" to "मतली बनी रहे तो दिन में तीन बार खाने से पहले",
        "Review if more than 3 doses in 24 hours" to "24 घंटे में 3 से ज़्यादा खुराक लगें तो डॉक्टर को बताएँ",
        "Do not take extra paracetamol from other sources" to "किसी और दवा से अलग से पैरासिटामोल न लें",
        "Do not crush or chew" to "पीसें या चबाएँ नहीं",
        "Dissolve in water" to "पानी में घोलकर लें",
        "Shake well before use" to "इस्तेमाल से पहले अच्छी तरह हिलाएँ",
    )

    private fun key(text: String): String = text.trim().trimEnd('.', '!', ';', ',', '।', '۔').replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    private val byKey: Map<String, Pair<String, String>> = phrases.associateBy { key(it.first) }

    /** The English and Hindi of a known phrase, or null. */
    fun known(text: String): Pair<String, String>? = byKey[key(text)]

    /**
     * [text] as shown in [lang]: every sentence that is a known phrase, or that is made only of known phrases joined by commas
     * ("Oral, twice daily."), is shown in that language; any other sentence stays exactly as written. English returns [text] unchanged.
     */
    fun shown(text: String, lang: CareLang): String {
        if (lang == CareLang.EN || text.isBlank()) return text
        return text.lines().joinToString("\n") { line ->
            line.split(Regex("(?<=[.!?])\\s+")).joinToString(" ") { part -> translated(part, lang) }
        }
    }

    private fun translated(text: String, lang: CareLang): String {
        val stop = if (text.trimEnd().endsWith('.')) when (lang) { CareLang.HI -> "।"; CareLang.UR -> "۔"; else -> "." } else ""
        known(text)?.let { return careTr(lang, it.first, it.second) + stop }
        val parts = text.trim().trimEnd('.').split(Regex(",\\s*")).filter(String::isNotBlank)
        if (parts.size > 1) {
            val found = parts.map { known(it) ?: return text }
            return found.joinToString(if (lang == CareLang.UR) "، " else ", ") { careTr(lang, it.first, it.second) } + stop
        }
        return text
    }
}
