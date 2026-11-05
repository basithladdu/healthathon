package `in`.wedevit.care

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

/*
 * Goals-of-care (GOC) conversation: a deterministic phrase matcher plus the plain data around it. No model, no network, no Android.
 * The matcher only says "these words were said". It never recommends a treatment and never touches the plan: GocPlan.apply runs only when
 * the doctor confirms one item (rule V1). A topic with no match is "not discussed" and stays "No preference recorded".
 * Rough twin of prototype/app/goc-extract.ts. Every phrase here is machine-written [needs clinician and native-speaker review].
 *
 * Keep each language's phrases in its own list. The dictionary test reads an English literal followed by a Hindi literal as a screen string.
 */

const val GOC_TRANSCRIPT_KEY = "gocTranscript"

/** Topics in the order the checklist prompts them: ICU, ventilator, dialysis, then on through the form [needs clinician sign-off on the order]. */
enum class GocDecision { ICU, BREATHING, DIALYSIS, VASOPRESSORS, FEEDING_NG, FEEDING_PEG, HYDRATION, CPR, TRANSFER, AMBULANCE, GOAL, VALUES }

/**
 * One thing heard: which options it points to, and the words that triggered it. [options] holds more than one id only when the words cannot separate them.
 * [ruledOut]: what was refused when no single option follows ("no ventilator" says nothing about NIV). [start]: where the quote begins.
 * [at]: where the deciding words are (the answer, for "Do you want CPR? No."), which orders statements for "latest statement wins" and finds who said it.
 */
data class GocHit(val decision: GocDecision, val options: List<String>, val quote: String, val start: Int, val ruledOut: List<String> = emptyList(), val at: Int = start)
/** The topic came up, but as a question, undecided, or negated in a way that cannot be read as a choice. No option is marked. */
data class GocMention(val decision: GocDecision, val quote: String, val start: Int)

data class GocExtraction(val hits: List<GocHit> = emptyList(), val mentions: List<GocMention> = emptyList()) {
    fun hitsFor(d: GocDecision) = hits.filter { it.decision == d }
    fun mentionsFor(d: GocDecision) = mentions.filter { it.decision == d }
    fun discussed(d: GocDecision) = hits.any { it.decision == d } || mentions.any { it.decision == d }
    /** Option id to the quotes that point at it. Different options for one topic are all marked: the matcher never chooses between them. */
    fun suggested(d: GocDecision): Map<String, List<GocHit>> = hitsFor(d).flatMap { h -> h.options.map { it to h } }.groupBy({ it.first }, { it.second })
    fun ruledOut(d: GocDecision): Map<String, List<GocHit>> = hitsFor(d).flatMap { h -> h.ruledOut.map { it to h } }.groupBy({ it.first }, { it.second })
}

internal class GocToken(val norm: String, val start: Int, val end: Int)

/** One spelling for many: case, nukta, chandrabindu, Latin accents, Urdu letter variants, joiners and apostrophes ("don't" = "dont"). */
internal fun gocNormalize(raw: String): String {
    val out = StringBuilder(raw.length)
    for (c in Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)) when (c) {
        '़', '‌', '‍', '­', '\'', '’', 'ʼ', 'ٰ' -> Unit
        in '̀'..'ͯ', in 'ً'..'ْ' -> Unit
        'ँ' -> out.append('ं')
        'ي', 'ى' -> out.append('ی')
        'ك' -> out.append('ک')
        'ه', 'ھ' -> out.append('ہ')
        else -> out.append(c)
    }
    return out.toString()
}

private fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '’' || c == 'ʼ' || c == '‌' || c == '‍' ||
    Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt() }

internal fun gocTokens(text: String, from: Int = 0, to: Int = text.length): List<GocToken> {
    val out = mutableListOf<GocToken>(); var i = from
    while (i < to) {
        if (!isTokenChar(text[i])) { i++; continue }
        val start = i
        while (i < to && isTokenChar(text[i])) i++
        val norm = gocNormalize(text.substring(start, i))
        if (norm.isNotEmpty()) out += GocToken(norm, start, i)
    }
    return out
}

/** A phrase is words side by side. "*" at either end of a word allows more letters there; "^" at the start ties the phrase to the start of the clause. */
private class Phrase(raw: String) {
    private class Seg(val text: String, val prefix: Boolean, val suffix: Boolean)
    private val anchored = raw.trimStart().startsWith("^")
    private val segs: List<Seg> = raw.trim().removePrefix("^").split(Regex("\\s+")).flatMap { word ->
        val parts = gocTokens(word.trim('*')).map { it.norm }
        parts.mapIndexed { i, p -> Seg(p, i == parts.lastIndex && word.endsWith("*"), i == 0 && word.startsWith("*")) }
    }
    private fun same(s: Seg, t: String) = when {
        s.prefix && s.suffix -> t.contains(s.text)
        s.prefix -> t.startsWith(s.text)
        s.suffix -> t.endsWith(s.text)
        else -> t == s.text
    }
    /** Word index ranges where this phrase sits, skipping masked words. */
    fun find(tokens: List<String>, masked: BooleanArray?): List<IntRange> {
        if (segs.isEmpty() || tokens.size < segs.size) return emptyList()
        val last = if (anchored) 0 else tokens.size - segs.size
        return (0..last).filter { at -> segs.indices.all { i -> masked?.get(at + i) != true && same(segs[i], tokens[at + i]) } }.map { it until it + segs.size }
    }
}

private class Lex(vararg lists: List<String>) {
    private val phrases = lists.flatMap { it }.distinct().map(::Phrase)
    fun find(tokens: List<String>, masked: BooleanArray? = null): List<IntRange> = phrases.flatMap { it.find(tokens, masked) }
    fun any(tokens: List<String>, masked: BooleanArray? = null) = find(tokens, masked).isNotEmpty()
}

private fun en(vararg p: String) = p.toList()
private fun hi(vararg p: String) = p.toList()
private fun te(vararg p: String) = p.toList()
private fun ur(vararg p: String) = p.toList()
private fun kn(vararg p: String) = p.toList()

// ── Cue words, in all five languages. Romanised Hindi sits with the English list. ──

/** A negating word near the topic: not, never, refuses. Telugu and Kannada negate inside the word, so those patterns are open-ended. */
private val VERBAL_NEG = Lex(
    en("not", "dont", "doesnt", "didnt", "wont", "wouldnt", "never", "refuse*", "decline*", "avoid*", "against", "reject*", "stop*", "nahi", "nahin", "nhi", "mat", "mana", "inkaar", "na kar*", "na de*", "na laga*", "na chah*"),
    hi("नहीं", "नही", "मत", "मना", "इनकार", "इंकार", "न कर*", "न दे*", "न लगा*", "न डाल*", "न चाह*", "ना कर*"),
    te("*వద్దు*", "*వద్దని*", "*లేదు", "కాదు", "*కండి", "*కూడదు", "తిరస్కరి*", "నిరాకరి*"),
    ur("نہیں", "مت", "نہ", "انکار", "منع"),
    kn("*ಬೇಡ*", "ಇಲ್ಲ*", "*ಿಲ್ಲ*", "ನಿರಾಕರಿ*"),
)
/** "no ICU", "without dialysis": counts only right next to the topic, so "we want dialysis, no ICU" keeps dialysis. */
private val DET_NEG = Lex(
    en("no", "without", "bina"),
    hi("बिना"),
    te("లేకుండా"),
    ur("بغیر", "بنا"),
    kn("ಇಲ್ಲದೆ"),
)
private val POSITIVE = Lex(
    en("want*", "wish*", "yes", "agree*", "accept*", "okay", "ok", "fine", "prefer*", "would like", "go ahead", "try", "attempt", "please", "put him on", "put her on", "chahiye", "chahte", "chahti", "chahta", "haan", "karein", "kijiye", "theek hai", "thik hai", "manzoor"),
    hi("चाहिए", "चाहिये", "चाहते", "चाहती", "चाहता", "चाहें", "चाहेंगे", "हाँ", "करें", "कीजिए", "ठीक है", "मंज़ूर"),
    te("*కావాలి*", "అవును", "సరే", "*కోరుకుంట*", "*ఒప్పుకుంట*", "చేయండి", "పెట్టండి"),
    ur("چاہیے", "چاہتے", "چاہتی", "چاہتا", "ہاں", "کریں", "کیجیے", "ٹھیک ہے", "منظور"),
    kn("*ಬೇಕು*", "ಹೌದು", "ಸರಿ", "ಮಾಡಿ", "*ಒಪ್ಪಿಗೆ*"),
)
/** Two negatives that together mean yes. Read first, then taken out of the clause. */
private val DOUBLE_NEG = Lex(
    en("no objection", "not against", "dont mind", "no problem with", "koi aapatti nahi", "aitraz nahi"),
    hi("कोई आपत्ति नहीं", "आपत्ति नहीं", "कोई दिक्कत नहीं", "एतराज़ नहीं"),
    te("అభ్యంతరం లేదు"),
    ur("اعتراض نہیں", "کوئی مسئلہ نہیں"),
    kn("ಅಭ್ಯಂತರ ಇಲ್ಲ", "ಆಕ್ಷೇಪಣೆ ಇಲ್ಲ"),
)
/** Not sure is not no, and talk about other people is no choice at all. "I am not sure about dialysis" must never read as a refusal. */
private val UNCERTAIN = Lex(
    en("not sure", "unsure", "dont know", "undecided", "maybe", "perhaps", "think about", "not decided", "pata nahi", "sochenge", "soch kar", "some patients", "many patients", "other patients", "some people", "most people", "usually", "generally", "for example", "in general"),
    hi("पता नहीं", "मालूम नहीं", "सोचेंगे", "सोचकर", "सोच कर", "तय नहीं", "कुछ मरीज़", "ज़्यादातर", "उदाहरण", "आमतौर पर"),
    te("తెలియదు", "ఆలోచించి*", "ఆలోచిస్తా*", "నిర్ణయించలేదు", "కొందరు రోగులు", "సాధారణంగా", "ఉదాహరణకు"),
    ur("معلوم نہیں", "پتا نہیں", "سوچ کر", "سوچیں گے", "طے نہیں", "کچھ مریض", "عام طور پر", "مثال کے طور پر"),
    kn("ಗೊತ್ತಿಲ್ಲ", "ಯೋಚಿಸಿ*", "ನಿರ್ಧರಿಸಿಲ್ಲ", "ಕೆಲವು ರೋಗಿಗಳು", "ಸಾಮಾನ್ಯವಾಗಿ", "ಉದಾಹರಣೆಗೆ"),
)
private val QUESTION = Lex(
    en("^do you", "^would you", "^does", "^did", "^should", "^are you", "^will", "^can", "^is", "^what about", "^how about", "^have you", "^has", "do you", "would you", "if you want", "whether", "kya"),
    hi("क्या"),
    te("కావాలా", "చేయాలా", "పెట్టాలా", "ఇష్టమా", "*వద్దా"),
    ur("^کیا"),
    kn("*ಬೇಕಾ", "*ಬೇಕೇ", "ಮಾಡಬೇಕಾ"),
)
private val TRIAL = Lex(
    en("trial", "few days", "some days", "short time", "time limited", "for a while", "kuch din", "kuch samay"),
    hi("ट्रायल", "कुछ दिन", "कुछ समय", "थोड़े समय"),
    te("ట్రయల్*", "కొన్ని రోజులు*", "కొంత కాలం*"),
    ur("ٹرائل", "آزمائش*", "کچھ دن", "کچھ وقت"),
    kn("ಟ್ರಯಲ್*", "ಕೆಲವು ದಿನ*", "ಸ್ವಲ್ಪ ಸಮಯ*"),
)
/** Clause breaks inside a sentence. "and" is a break on purpose: a missed topic is safer than a wrong "no". */
private val CONJUNCTION = Lex(
    en("but", "however", "though", "and", "lekin", "magar", "aur"),
    hi("लेकिन", "मगर", "किंतु", "परंतु", "और"),
    te("కానీ", "కాని", "మరియు"),
    ur("لیکن", "مگر", "اور"),
    kn("ಆದರೆ", "ಆದ್ರೆ", "ಮತ್ತು"),
)

/** Courtesy words allowed in a short answer. */
private val ANSWER_FILLER = Lex(
    en("please", "thanks", "thank", "you", "doctor", "sir", "madam", "ji", "ma", "am"),
    hi("जी", "डॉक्टर", "साहब"),
    te("అండి", "డాక్టర్", "సార్"),
    ur("جی", "ڈاکٹر", "صاحب"),
    kn("ಡಾಕ್ಟರ್", "ಸರ್"),
)

private enum class Polarity { POS, NEG, TRIAL }

/**
 * How one topic is recognised. [explicit] phrases decide on their own ("DNR"). [qualified] phrases decide only when a [terms] word is in the same clause
 * ("only if", with "dialysis"). [terms] words need a yes, no or trial cue; [map] turns that into option ids, and an empty answer means no option is marked.
 * The Int on a term is its level (the breathing ladder); other topics use 0.
 */
private class GocRule(val decision: GocDecision, val terms: List<Pair<Int, Lex>>, val explicit: List<Pair<List<String>, Lex>> = emptyList(), val qualified: List<Pair<List<String>, Lex>> = emptyList(), val ruledOut: ((Int) -> List<String>)? = null, val map: (Polarity, Int) -> List<String>)

private val FEEDING_TUBE: List<List<String>> = listOf(
    en("feeding tube", "tube feeding", "food pipe", "khane ki nali"),
    hi("खाने की नली", "नली से खाना"),
    te("ఆహార గొట్టం*"),
    ur("کھانے کی نلکی", "کھانے کی نالی"),
    kn("ಆಹಾರ ನಳಿಕೆ*"),
)

private val RULES: List<GocRule> = listOf(
    GocRule(GocDecision.ICU, listOf(0 to Lex(en("icu", "intensive care"), hi("आईसीयू", "आई सी यू", "इंटेंसिव केयर"), te("ఐసీయూ*", "ఐసియు*", "ఐ సి యు*"), ur("آئی سی یو"), kn("ಐಸಿಯು*", "ಐ ಸಿ ಯು*")))) { p, _ ->
        when (p) { Polarity.POS -> listOf(IcuKind.YES.name); Polarity.NEG -> listOf(IcuKind.NO.name); Polarity.TRIAL -> listOf(IcuKind.TRIAL.name) }
    },
    // Breathing ceiling: "no ventilator" rules that level out and marks no other. It says nothing about NIV, and which level is acceptable is for the doctor to ask.
    GocRule(GocDecision.BREATHING, listOf(
        5 to Lex(en("intubat*", "ventilator*", "breathing machine", "life support", "breathing tube"), hi("वेंटिलेटर", "इंट्यूबेशन", "साँस की मशीन", "गले में नली", "साँस की नली"), te("వెంటిలేటర్*", "ఇంట్యూబేషన్*", "శ్వాస యంత్ర*", "గొంతులో గొట్టం"), ur("وینٹی لیٹر", "وینٹیلیٹر", "انٹیوبیشن", "سانس کی مشین"), kn("ವೆಂಟಿಲೇಟರ್*", "ಇಂಟ್ಯೂಬೇಶನ್*", "ಉಸಿರಾಟದ ಯಂತ್ರ*")),
        4 to Lex(en("niv", "bipap", "bi pap", "cpap", "mask ventilation"), hi("बाइपैप", "बाईपैप", "सीपैप", "मास्क से साँस"), te("బైపాప్*", "సీపాప్*"), ur("بائی پیپ", "سی پیپ"), kn("ಬೈಪ್ಯಾಪ್*", "ಸಿಪ್ಯಾಪ್*")),
        3 to Lex(en("hfnc", "high flow"), hi("हाई फ्लो", "हाई फ़्लो"), te("హై ఫ్లో*"), ur("ہائی فلو"), kn("ಹೈ ಫ್ಲೋ*")),
        2 to Lex(en("non rebreather", "venturi"), hi("वेंचुरी", "नॉन रीब्रीदर"), te("వెంచురి*"), ur("وینچوری"), kn("ವೆಂಚುರಿ*")),
        1 to Lex(en("oxygen", "nasal prongs", "simple mask"), hi("ऑक्सीजन", "आक्सीजन"), te("ఆక్సిజన్*"), ur("آکسیجن"), kn("ಆಕ್ಸಿಜನ್*", "ಆಮ್ಲಜನಕ*")),
    ), explicit = listOf(listOf("0") to Lex(en("comfort measures only", "only comfort measures"), hi("सिर्फ़ आराम के उपाय"), te("సౌకర్య చర్యలు మాత్రమే"), ur("صرف آرام کے اقدامات"), kn("ಆರಾಮದ ಕ್ರಮಗಳು ಮಾತ್ರ"))), ruledOut = { level -> listOf(level.toString()) }) { p, level ->
        when (p) { Polarity.POS -> listOf(level.toString()); Polarity.NEG -> emptyList(); Polarity.TRIAL -> listOf(level.toString()) }
    },
    GocRule(GocDecision.DIALYSIS, listOf(0 to Lex(en("dialysis", "kidney machine"), hi("डायलिसिस", "डायलीसिस"), te("డయాలసిస్*", "డయాలిసిస్*"), ur("ڈائیلیسس", "ڈائلیسس", "ڈائیلاسز", "گردے کی مشین"), kn("ಡಯಾಲಿಸಿಸ್*"))),
        qualified = listOf(listOf(DialysisKind.ONLY_IF_REVERSIBLE.name) to Lex(en("only if", "unless", "reversible", "can recover", "can be reversed", "theek ho"), hi("ठीक हो", "अगर ठीक"), te("కోలుకుంటే", "నయమైతే"), ur("ٹھیک ہو"), kn("ಸರಿಹೋದರೆ", "ಗುಣವಾದರೆ")))) { p, _ ->
        when (p) { Polarity.POS -> listOf(DialysisKind.YES.name); Polarity.NEG -> listOf(DialysisKind.NO.name); Polarity.TRIAL -> listOf(DialysisKind.TRIAL.name) }
    },
    GocRule(GocDecision.VASOPRESSORS, listOf(0 to Lex(en("vasopressor*", "pressor*", "noradrenaline", "norepinephrine", "bp support", "blood pressure support", "blood pressure drugs"), hi("बीपी की दवा", "बीपी बढ़ाने की दवा", "ब्लड प्रेशर की दवा"), te("బీపీ మందు*", "రక్తపోటు మందు*", "వాసోప్రెస*"), ur("بی پی کی دوا", "بلڈ پریشر کی دوا"), kn("ಬಿಪಿ ಔಷಧ*", "ರಕ್ತದೊತ್ತಡ ಔಷಧ*"))),
        qualified = listOf(
            listOf(VasopressorKind.YES_NO_LIMIT.name) to Lex(en("no limit", "without limit"), hi("बिना सीमा"), te("పరిమితి లేకుండా"), ur("بغیر حد"), kn("ಮಿತಿ ಇಲ್ಲದೆ")),
            listOf(VasopressorKind.YES_MAX_AGENTS.name) to Lex(en("one drug", "two drugs", "up to one", "up to two"), hi("एक दवा", "दो दवा*"), te("ఒక మందు", "రెండు మందు*"), ur("ایک دوا", "دو دوا*"), kn("ಒಂದು ಔಷಧ", "ಎರಡು ಔಷಧ*")),
        )) { p, _ ->
        when (p) { Polarity.POS -> listOf(VasopressorKind.YES_NO_LIMIT.name, VasopressorKind.YES_MAX_AGENTS.name); Polarity.NEG -> listOf(VasopressorKind.NO.name); Polarity.TRIAL -> listOf(VasopressorKind.TRIAL.name) }
    },
    GocRule(GocDecision.FEEDING_NG, listOf(0 to Lex(en("ng tube", "ng feeding", "nasogastric", "ryles*", "nose tube", "nasal tube", "naak ki nali") + FEEDING_TUBE[0], hi("नाक की नली", "राइल्स") + FEEDING_TUBE[1], te("ముక్కు గొట్టం*", "ముక్కు ద్వారా గొట్టం*") + FEEDING_TUBE[2], ur("ناک کی نلکی", "ناک کی نالی") + FEEDING_TUBE[3], kn("ಮೂಗಿನ ನಳಿಕೆ*", "ಮೂಗಿನ ಕೊಳವೆ*") + FEEDING_TUBE[4]))) { p, _ ->
        when (p) { Polarity.POS -> listOf(NgKind.YES.name); Polarity.NEG -> listOf(NgKind.NO.name); Polarity.TRIAL -> listOf(NgKind.UP_TO_DAYS.name) }
    },
    GocRule(GocDecision.FEEDING_PEG, listOf(0 to Lex(en("peg", "peg tube", "stomach tube", "gastrostomy", "pet ki nali") + FEEDING_TUBE[0], hi("पेट की नली", "पेग") + FEEDING_TUBE[1], te("కడుపు గొట్టం*", "పెగ్*") + FEEDING_TUBE[2], ur("پیٹ کی نلکی", "پیٹ کی نالی") + FEEDING_TUBE[3], kn("ಹೊಟ್ಟೆ ನಳಿಕೆ*", "ಹೊಟ್ಟೆಯ ಕೊಳವೆ*", "ಪೆಗ್*") + FEEDING_TUBE[4])),
        qualified = listOf(listOf(PegFeeding.ONLY_AFTER_PALLIATIVE_DISCUSSION.name) to Lex(en("after palliative", "palliative team first", "after discussion"), hi("पैलिएटिव से बात के बाद", "बात के बाद"), te("పాలియేటివ్ తర్వాత"), ur("بات کے بعد"), kn("ಪ್ಯಾಲಿಯೇಟಿವ್ ನಂತರ")))) { p, _ ->
        when (p) { Polarity.POS -> listOf(PegFeeding.YES.name); Polarity.NEG -> listOf(PegFeeding.NO.name); Polarity.TRIAL -> emptyList() }
    },
    GocRule(GocDecision.HYDRATION, listOf(0 to Lex(en("drip", "iv fluid*", "saline", "iv hydration"), hi("ड्रिप", "सलाइन"), te("డ్రిప్*", "సెలైన్*"), ur("ڈرپ", "سیلائن"), kn("ಡ್ರಿಪ್*", "ಸಲೈನ್*"))),
        explicit = listOf(
            listOf(Hydration.ORAL_SIPS.name) to Lex(en("sips", "by mouth only", "only by mouth", "sirf munh se"), hi("घूँट", "सिर्फ़ मुँह से"), te("గుటకలు*", "నోటి ద్వారా మాత్రమే"), ur("گھونٹ", "صرف منہ سے"), kn("ಗುಟುಕು*", "ಬಾಯಿಯಿಂದ ಮಾತ್ರ")),
            listOf(Hydration.FULL.name) to Lex(en("full fluids", "full drip", "maintenance fluids", "poori drip"), hi("पूरी ड्रिप"), te("పూర్తి డ్రిప్*"), ur("پوری ڈرپ"), kn("ಪೂರ್ಣ ಡ್ರಿಪ್*")),
            listOf(Hydration.LIMITED.name) to Lex(en("limited fluids", "limited drip", "some fluids", "seemit drip"), hi("सीमित ड्रिप", "थोड़ी ड्रिप"), te("పరిమిత డ్రిప్*"), ur("محدود ڈرپ"), kn("ಸೀಮಿತ ಡ್ರಿಪ್*")),
        )) { p, _ ->
        when (p) { Polarity.POS -> listOf(Hydration.LIMITED.name, Hydration.FULL.name); Polarity.NEG -> listOf(Hydration.ORAL_SIPS.name); Polarity.TRIAL -> listOf(Hydration.LIMITED.name) }
    },
    GocRule(GocDecision.CPR, listOf(0 to Lex(en("cpr", "resuscitat*", "revive", "restart the heart"), hi("सीपीआर", "सी पी आर"), te("సీపీఆర్*", "సిపిఆర్*"), ur("سی پی آر"), kn("ಸಿಪಿಆರ್*"))),
        explicit = listOf(listOf(Cpr.DNACPR.name) to Lex(en("dnr", "dnar", "dnacpr", "do not resuscitate", "do not attempt cpr", "allow natural death", "natural death"), hi("डीएनआर", "स्वाभाविक मृत्यु"), te("డీఎన్ఆర్*", "సహజ మరణం*"), ur("ڈی این آر", "فطری موت"), kn("ಡಿಎನ್ಆರ್*", "ಸಹಜ ಸಾವು*")))) { p, _ ->
        when (p) { Polarity.POS -> listOf(Cpr.ATTEMPT.name); Polarity.NEG -> listOf(Cpr.DNACPR.name); Polarity.TRIAL -> emptyList() }
    },
    GocRule(GocDecision.TRANSFER, listOf(0 to Lex(en("hospital*", "admit*", "aspatal", "aspataal", "bharti"), hi("अस्पताल", "भर्ती", "हॉस्पिटल"), te("ఆసుపత్రి*", "హాస్పిటల్*"), ur("ہسپتال", "اسپتال"), kn("ಆಸ್ಪತ್ರೆ*"))),
        explicit = listOf(listOf(TransferKind.NO.name) to Lex(en("stay at home", "stay home", "die at home", "care at home", "at home only", "ghar par hi", "ghar pe hi"), hi("घर पर ही", "घर में ही"), te("ఇంట్లోనే"), ur("گھر پر ہی", "گھر میں ہی"), kn("ಮನೆಯಲ್ಲೇ", "ಮನೆಯಲ್ಲಿಯೇ"))),
        qualified = listOf(listOf(TransferKind.ONLY_FOR.name) to Lex(en("only for", "only if", "sirf tab"), hi("सिर्फ़", "केवल"), te("మాత్రమే"), ur("صرف"), kn("ಮಾತ್ರ")))) { p, _ ->
        when (p) { Polarity.POS -> listOf(TransferKind.ANY.name); Polarity.NEG -> listOf(TransferKind.NO.name); Polarity.TRIAL -> emptyList() }
    },
    // The ambulance options need the doctor's own reading: the word marks the topic as raised and no option.
    GocRule(GocDecision.AMBULANCE, listOf(0 to Lex(en("ambulance*"), hi("एम्बुलेंस", "एंबुलेंस"), te("అంబులెన్స్*"), ur("ایمبولینس"), kn("ಆಂಬುಲೆನ್ಸ್*")))) { _, _ -> emptyList() },
    GocRule(GocDecision.GOAL, emptyList(), explicit = listOf(
        listOf(GoalOfCare.A_LIFE.name) to Lex(en("prioritise life", "prioritize life", "everything possible", "do everything", "all treatment", "full treatment", "maximum treatment", "sab kuch kar*", "pura ilaj", "poora ilaj"), hi("सब कुछ कर*", "हर संभव", "जीवन को प्राथमिकता", "पूरा इलाज", "जान बचा*"), te("అన్ని చికిత్స*", "పూర్తి చికిత్స*", "ప్రాణం కాపాడ*"), ur("سب کچھ کر*", "ہر ممکن", "پورا علاج", "جان بچا*"), kn("ಎಲ್ಲಾ ಚಿಕಿತ್ಸೆ*", "ಪೂರ್ಣ ಚಿಕಿತ್ಸೆ*", "ಜೀವ ಉಳಿಸ*")),
        listOf(GoalOfCare.B_CEILING.name) to Lex(en("balanced", "within limits", "treat what can be reversed", "treat reversible"), hi("संतुलित", "तय सीमा में", "सीमा में इलाज"), te("సమతుల్య*", "పరిమితుల్లో"), ur("متوازن", "حد کے اندر"), kn("ಸಮತೋಲ*", "ಮಿತಿಯೊಳಗೆ")),
        listOf(GoalOfCare.C_COMFORT.name) to Lex(en("comfort care", "comfort only", "only comfort", "prioritise comfort", "prioritize comfort", "keep comfortable", "keep him comfortable", "keep her comfortable", "sirf aaram", "aaram hi"), hi("सिर्फ़ आराम", "केवल आराम", "बस आराम", "आराम को प्राथमिकता", "आराम ही", "तकलीफ़ न हो"), te("సౌకర్యం మాత్రమే", "సౌకర్యమే", "సౌకర్యానికి ప్రాధాన్యత", "నొప్పి లేకుండా", "ప్రశాంతంగా*"), ur("صرف آرام", "بس آرام", "آرام کو ترجیح", "تکلیف نہ ہو"), kn("ಆರಾಮ ಮಾತ್ರ", "ಆರಾಮಕ್ಕೆ ಆದ್ಯತೆ", "ನೋವಿಲ್ಲದೆ", "ನೆಮ್ಮದಿಯಾಗಿ")),
    )) { _, _ -> emptyList() },
)

private val VALUES_OWN = Lex(
    en("what matters", "matters most", "most important", "important to me", "important to him", "important to her", "with family", "with my family", "sabse zaroori", "sabse zaruri"),
    hi("सबसे ज़रूरी", "मेरे लिए ज़रूरी", "सबसे अहम", "इच्छा", "परिवार के साथ"),
    te("ముఖ్యం*", "కోరిక*", "కుటుంబంతో*"),
    ur("سب سے ضروری", "سب سے اہم", "خواہش", "خاندان کے ساتھ"),
    kn("ಮುಖ್ಯ*", "ಆಸೆ*", "ಕುಟುಂಬದ ಜೊತೆ*"),
)
private val VALUES_NOT_ACCEPTED = Lex(
    en("would not accept", "wouldnt accept", "unacceptable", "not acceptable", "a burden", "on machines", "bedridden", "bojh"),
    hi("बोझ", "मंज़ूर नहीं", "मशीनों पर", "बिस्तर पर पड़े"),
    te("భారం*", "ఒప్పుకోను", "యంత్రాలపై*", "మంచానికే*"),
    ur("بوجھ", "منظور نہیں", "مشینوں پر", "بستر پر پڑ*"),
    kn("ಹೊರೆ*", "ಒಪ್ಪುವುದಿಲ್ಲ", "ಯಂತ್ರಗಳ ಮೇಲೆ*", "ಹಾಸಿಗೆ ಹಿಡಿ*"),
)

const val GOC_VALUES_OWN_WORDS = "OWN_WORDS"
const val GOC_VALUES_NOT_ACCEPTED = "NOT_ACCEPTED"

private const val NEG_WINDOW = 8
private const val SHORT_ANSWER_WORDS = 3
private const val MAX_QUOTE = 280
private const val SENTENCE_END = ".?!;।॥۔؟؛\n"
private const val CLAUSE_END = ",،:"

object GocExtract {
    private class Clause(val tokens: List<GocToken>, val question: Boolean, val sentenceStart: Int, val sentenceEnd: Int) { val words = tokens.map { it.norm } }
    private class Pending(val rule: GocRule, val level: Int, val clause: Int)

    private fun clauses(text: String): List<Clause> {
        val out = mutableListOf<Clause>(); var start = 0; var i = 0
        fun sentence(end: Int) {
            val question = end < text.length && (text[end] == '?' || text[end] == '؟')
            var from = start
            fun clause(to: Int) {
                val tokens = gocTokens(text, from, to); from = to + 1
                if (tokens.isEmpty()) return
                val words = tokens.map { it.norm }
                val cuts = CONJUNCTION.find(words).map { it.first }.distinct().sorted()
                var at = 0
                (cuts + tokens.size).forEach { cut -> if (cut > at) out += Clause(tokens.subList(at, cut), question, start, end); at = cut + 1 }
            }
            for (j in start until end) if (text[j] in CLAUSE_END) clause(j)
            clause(end)
        }
        while (i < text.length) {
            val c = text[i]
            // A decimal or a short form inside a number does not end a sentence.
            val inNumber = c == '.' && i > 0 && i + 1 < text.length && text[i - 1].isDigit() && text[i + 1].isDigit()
            if (c in SENTENCE_END && !inNumber) { sentence(i); start = i + 1 }
            i++
        }
        if (start < text.length) sentence(text.length)
        return out
    }

    private fun quote(text: String, c: Clause, next: Clause? = null): String {
        val raw = text.substring(c.sentenceStart, minOf(c.sentenceEnd + 1, text.length)) + (next?.takeIf { it.sentenceStart != c.sentenceStart }?.let { " " + text.substring(it.sentenceStart, minOf(it.sentenceEnd + 1, text.length)) } ?: "")
        val clean = raw.trim()
        return if (clean.length <= MAX_QUOTE) clean else clean.take(MAX_QUOTE - 1).trimEnd() + "…"
    }

    /** Negations near this phrase, repeated words counted once ("no no", "వద్దు వద్దు"). 0 = none; even above 0 = they cancel and the phrase cannot be read. */
    private fun negations(words: List<String>, masked: BooleanArray, range: IntRange): Int {
        val verbal = VERBAL_NEG.find(words, masked).map { it.first }.distinct().filter { it <= range.last + NEG_WINDOW && it >= range.first - NEG_WINDOW }.filter { it !in range }.sorted()
        val unique = verbal.filterIndexed { i, at -> i == 0 || words[verbal[i - 1]] != words[at] }
        val det = DET_NEG.find(words, masked).any { it.first == range.first - 1 || it.first == range.first - 2 || it.first == range.last + 1 }
        return unique.size + if (det) 1 else 0
    }

    /** The sound captions a speech model writes, such as "[crying]" or "(sobs)". For the note; the verbatim transcript keeps them. */
    internal fun withoutSoundCaptions(text: String): String = text.replace(Regex("\\[[^\\]]{0,40}\\]|\\([^)]{0,30}\\)|\u266A+|\\*[^*]{0,30}\\*"), " ").replace(Regex("\\s{2,}"), " ").trim()

    /** Questions asked as statements, used to guess that a line came from the doctor side. */
    internal fun isQuestion(line: String): Boolean = line.trimEnd().let { it.endsWith("?") || it.endsWith("؟") } || QUESTION.any(gocTokens(line).map { it.norm })

    fun extract(text: String): GocExtraction {
        if (text.isBlank()) return GocExtraction()
        val hits = mutableListOf<GocHit>(); val mentions = mutableListOf<GocMention>()
        val all = clauses(text)
        val pending = mutableListOf<Pending>()
        fun mention(d: GocDecision, c: Clause) { mentions += GocMention(d, quote(text, c), c.tokens.first().start) }
        fun hit(d: GocDecision, options: List<String>, c: Clause, next: Clause? = null) { if (options.isNotEmpty()) hits += GocHit(d, options, quote(text, c, next), c.tokens.first().start, at = (next ?: c).tokens.first().start) else mention(d, c) }
        fun refused(rule: GocRule, level: Int, c: Clause, next: Clause? = null) {
            val out = rule.ruledOut?.invoke(level)
            if (out == null) hit(rule.decision, rule.map(Polarity.NEG, level), c, next) else hits += GocHit(rule.decision, emptyList(), quote(text, c, next), c.tokens.first().start, out, (next ?: c).tokens.first().start)
        }
        // "Doctor:" or "Son:" at the start of a line is a label, not words said: skipped, so a reply still finds the question before it.
        val label = BooleanArray(all.size) { i -> val c = all[i]; c.tokens.size <= 3 && gocLabelAfter(text, c.tokens.last().end) && gocLineStartBefore(text, c.tokens.first().start) }
        fun previousContent(index: Int): Int { var i = index - 1; while (i >= 0 && label[i]) i--; return i }

        all.forEachIndexed { index, c ->
            if (label[index]) return@forEachIndexed
            val words = c.words
            // A short "no" after a question or a bare topic answers it.
            // Every word must be a yes, a no or a courtesy, so "and no pain" answers nothing.
            val covered = (VERBAL_NEG.find(words) + DET_NEG.find(words) + POSITIVE.find(words) + ANSWER_FILLER.find(words)).flatMap { it.toList() }.toSet()
            val shortAnswer = words.size <= SHORT_ANSWER_WORDS && !c.question && words.indices.all { it in covered }
            if (shortAnswer) {
                // Any topic asked in the sentence just before ("If your heart stops, would you want CPR, where we press on the chest?").
                val previous = previousContent(index)
                val before = if (previous < 0 || all[previous].sentenceStart == c.sentenceStart) emptyList() else pending.filter { all[it.clause].sentenceStart == all[previous].sentenceStart }
                if (before.isNotEmpty()) {
                    val no = VERBAL_NEG.any(words) || DET_NEG.any(words); val yes = POSITIVE.any(words)
                    if (no || yes) before.forEach { p -> if (no) refused(p.rule, p.level, all[p.clause], c) else hit(p.rule.decision, p.rule.map(Polarity.POS, p.level), all[p.clause], c) }
                }
            }
            val isQuestion = c.question || QUESTION.any(words)
            val uncertain = UNCERTAIN.any(words)
            val masked = BooleanArray(words.size)
            val doubled = DOUBLE_NEG.find(words)
            doubled.forEach { r -> r.forEach { masked[it] = true } }
            // Whole phrases first, then taken out so their "not" and "no" cannot negate anything else in the clause.
            val explicit = RULES.flatMap { rule -> rule.explicit.flatMap { (options, lex) -> lex.find(words).map { Triple(rule, options, it) } } }
            val qualified = RULES.flatMap { rule -> rule.qualified.map { (options, lex) -> Triple(rule, options, lex.find(words)) } }.filter { it.third.isNotEmpty() }
            explicit.forEach { (_, _, r) -> r.forEach { masked[it] = true } }
            qualified.forEach { (_, _, ranges) -> ranges.forEach { r -> r.forEach { masked[it] = true } } }
            explicit.forEach { (rule, options, range) ->
                when {
                    isQuestion || uncertain -> mention(rule.decision, c)
                    negations(words, masked, range) != 0 -> mention(rule.decision, c)
                    else -> hit(rule.decision, options, c)
                }
            }
            RULES.forEach { rule ->
                rule.terms.forEach { (level, lex) ->
                    lex.find(words, masked).forEach { range ->
                        val negs = negations(words, masked, range)
                        val cue = qualified.firstOrNull { it.first === rule }
                        when {
                            isQuestion -> { mention(rule.decision, c); pending += Pending(rule, level, index) }
                            uncertain -> mention(rule.decision, c)
                            cue != null -> hit(rule.decision, cue.second, c)
                            doubled.isNotEmpty() -> hit(rule.decision, rule.map(Polarity.POS, level), c)
                            negs == 0 && TRIAL.any(words, masked) -> hit(rule.decision, rule.map(Polarity.TRIAL, level), c)
                            negs == 0 && POSITIVE.any(words, masked) -> hit(rule.decision, rule.map(Polarity.POS, level), c)
                            negs == 0 -> { mention(rule.decision, c); pending += Pending(rule, level, index) }
                            negs % 2 == 1 -> refused(rule, level, c)
                            else -> mention(rule.decision, c)
                        }
                    }
                }
            }
            if (!isQuestion) {
                val raw = BooleanArray(words.size)
                if (VALUES_NOT_ACCEPTED.any(words)) hit(GocDecision.VALUES, listOf(GOC_VALUES_NOT_ACCEPTED), c)
                else VALUES_OWN.find(words).firstOrNull { negations(words, raw, it) == 0 }?.let { hit(GocDecision.VALUES, listOf(GOC_VALUES_OWN_WORDS), c) }
            }
        }
        return GocExtraction(hits.distinctBy { listOf(it.decision.name, it.options, it.ruledOut, it.quote) }.sortedBy { it.start }, mentions.distinctBy { it.decision to it.quote }.sortedBy { it.start })
    }
}

/** True when the next character after [from] (skipping spaces) is a colon: the words before it are a speaker label. */
internal fun gocLabelAfter(text: String, from: Int): Boolean { var i = from; while (i < text.length && text[i] == ' ') i++; return i < text.length && (text[i] == ':' || text[i] == '：') }
/** True when only spaces sit between the start of the line and [at]. */
internal fun gocLineStartBefore(text: String, at: Int): Boolean { var i = at - 1; while (i >= 0 && text[i] == ' ') i--; return i < 0 || text[i] == '\n' }

// ── Doctor side or patient party, per line. Not speaker identification. ──

enum class GocParty { DOCTOR_SIDE, PATIENT_PARTY, UNSET }

/**
 * One line of the verbatim transcript. [guessed]: the side came from a simple rule (or a speaker model) and the doctor has not looked at it.
 * [key] ties a line to its audio segment, so resuming adds no repeats. [who]: the person label typed before it ("son", "patient"), lower case, or "Speaker 2"
 * from the on-device speaker model, so "latest statement wins" never lets one person overrule another.
 */
data class GocLine(val text: String, val party: GocParty = GocParty.UNSET, val guessed: Boolean = false, val key: String = "", val who: String = "")

object GocLines {
    private val DOCTOR_LABEL = Lex(en("doctor", "dr", "nurse", "consultant"), hi("डॉक्टर", "डॉ", "नर्स"), te("డాక్టర్", "వైద్యుడు", "నర్సు"), ur("ڈاکٹر", "نرس"), kn("ಡಾಕ್ಟರ್", "ವೈದ್ಯ*", "ನರ್ಸ್"))
    private val PATIENT_LABEL = Lex(en("patient", "family", "son", "daughter", "wife", "husband", "mother", "father", "brother", "sister", "nephew", "niece", "grandson", "granddaughter", "relative", "attender", "caregiver", "friend"),
        hi("मरीज़", "परिवार", "बेटा", "बेटी", "पत्नी", "पति", "माँ", "पिता", "भाई", "बहन"), te("రోగి", "కుటుంబం", "కొడుకు", "కూతురు", "భార్య", "భర్త", "అన్న", "తమ్ముడు", "అక్క", "చెల్లి"),
        ur("مریض", "مریضہ", "خاندان", "بیٹا", "بیٹی", "بیوی", "شوہر", "بھائی", "بہن", "ماں", "والد"), kn("ರೋಗಿ", "ಕುಟುಂಬ", "ಮಗ", "ಮಗಳು", "ಹೆಂಡತಿ", "ಗಂಡ", "ಅಣ್ಣ", "ಅಕ್ಕ", "ತಂಗಿ"))
    private const val SHORT_REPLY_WORDS = 6

    /** Splits words into sentences and labels each. A written "Doctor:" or "Son:" is taken as said; a question is guessed doctor side, and a short reply straight after it patient party. Everything else stays not marked. */
    fun fromText(text: String, keyPrefix: String = ""): List<GocLine> {
        val out = mutableListOf<GocLine>(); var start = 0
        // A label ("Son:") covers every sentence on its line, not only the first.
        var carry: GocLine? = null
        fun add(end: Int) {
            val line = text.substring(start, end).trim()
            if (line.isEmpty() || gocTokens(line).isEmpty()) return
            val key = if (keyPrefix.isBlank()) "" else "$keyPrefix:${out.size}"
            val colon = line.indexOf(':').takeIf { it in 1..24 }
            val label = colon?.let { gocTokens(line.substring(0, it)).map { t -> t.norm } }
            val previous = out.lastOrNull()
            val held = carry
            out += when {
                label != null && DOCTOR_LABEL.any(label) -> GocLine(line, GocParty.DOCTOR_SIDE, false, key).also { carry = it }
                label != null && PATIENT_LABEL.any(label) -> GocLine(line, GocParty.PATIENT_PARTY, false, key, label.joinToString(" ")).also { carry = it }
                held != null -> GocLine(line, held.party, false, key, held.who)
                GocExtract.isQuestion(line) -> GocLine(line, GocParty.DOCTOR_SIDE, true, key)
                previous != null && previous.party == GocParty.DOCTOR_SIDE && previous.guessed && gocTokens(line).size <= SHORT_REPLY_WORDS -> GocLine(line, GocParty.PATIENT_PARTY, true, key)
                else -> GocLine(line, GocParty.UNSET, false, key)
            }
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val inNumber = c == '.' && i > 0 && i + 1 < text.length && text[i - 1].isDigit() && text[i + 1].isDigit()
            if (c in SENTENCE_END && !inNumber) { add(if (c == '\n') i else i + 1); start = i + 1; if (c == '\n') carry = null }
            i++
        }
        add(text.length)
        return out
    }

    fun text(lines: List<GocLine>): String = lines.joinToString("\n") { it.text }

    /** Which line a matcher offset into [text] falls on. */
    fun lineAt(lines: List<GocLine>, offset: Int): GocLine? {
        var at = 0
        lines.forEach { line -> val end = at + line.text.length; if (offset <= end) return line; at = end + 1 }
        return null
    }
    fun partyAt(lines: List<GocLine>, offset: Int): GocParty = lineAt(lines, offset)?.party ?: GocParty.UNSET

    fun nextParty(p: GocParty): GocParty = when (p) { GocParty.UNSET -> GocParty.DOCTOR_SIDE; GocParty.DOCTOR_SIDE -> GocParty.PATIENT_PARTY; GocParty.PATIENT_PARTY -> GocParty.UNSET }
    fun label(p: GocParty): Bi = when (p) { GocParty.DOCTOR_SIDE -> Bi("Doctor side", "डॉक्टर की ओर"); GocParty.PATIENT_PARTY -> Bi("Patient side", "मरीज़ की ओर"); GocParty.UNSET -> Bi("Not marked", "तय नहीं") }
}

// ── The saved record. Optional, and never on the emergency card. ──

enum class GocTopicStatus { CONFIRMED, NOT_DISCUSSED_VERIFIED }
data class GocTopicRecord(val decision: GocDecision, val status: GocTopicStatus, val optionId: String? = null, val quote: String = "", val at: String = "")
enum class GocNoteKind { GENERAL_DISCUSSION, DECISION_TAKEN }

/** The verbatim words ([lines]) and the doctor's note ([note]) are kept apart: the note says what was said and decided, and the words stay as spoken. [planVersion] tells a carried-over copy from this version's own. */
data class GocSignOffSummary(val id: String, val sealSha256: String, val lockedAt: String, val signers: List<String>,
    /** DOCTOR, PATIENT, FAMILY or WITNESS, one per signer; with [registrations] and [methods] so Section 19 of the form shows who signed as what. */
    val roles: List<String> = emptyList(), val registrations: List<String> = emptyList(), val methods: List<String> = emptyList(), val signedAt: List<String> = emptyList()) {
    fun formSigners(): List<GocFormSigner> = signers.mapIndexedNotNull { i, name -> roles.getOrNull(i)?.let { role ->
        GocFormSigner(role, name, signedAt.getOrNull(i) ?: lockedAt, registrations.getOrNull(i).orEmpty(), thumb = methods.getOrNull(i) == "THUMB") } }
}

data class GocTranscript(
    val lines: List<GocLine> = emptyList(), val language: String = "", val source: String = SOURCE_TYPED, val model: String = "",
    val participants: String = "", val consentAt: Long = 0, val planVersion: Int = 1,
    val topics: List<GocTopicRecord> = emptyList(), val note: String = "", val noteKind: GocNoteKind = GocNoteKind.GENERAL_DISCUSSION,
    val reviewedAt: String = "", val reviewedBy: String = "", val audioKept: Boolean = false,
    /** The locked sign-off of [note] (CareGocSignOff.kt): its id in the sign-off store, its seal, when it locked, and who signed. Blank until signed. */
    val signOff: GocSignOffSummary? = null,
    /** Which engine wrote the machine draft, shown as "Transcribed by …". */
    val transcribedBy: String = "",
) {
    companion object { const val SOURCE_TYPED = "TYPED"; const val SOURCE_RECORDED = "RECORDED" }
}

/** The same shape as GocTranscript in prototype/app/ectpr-model.ts, so a record reads on the web and back. planVersion, participants, noteKind, guessed and key are extras the web ignores. */
object GocTranscriptJson {
    private val FIELDS = mapOf(GocDecision.ICU to "icu", GocDecision.BREATHING to "respCeiling", GocDecision.DIALYSIS to "dialysis", GocDecision.VASOPRESSORS to "vasopressors", GocDecision.FEEDING_NG to "ng", GocDecision.FEEDING_PEG to "peg",
        GocDecision.HYDRATION to "hydration", GocDecision.CPR to "cpr", GocDecision.TRANSFER to "hospitalTransfer", GocDecision.AMBULANCE to "ambulance", GocDecision.GOAL to "goalOfCare", GocDecision.VALUES to "values")
    private fun decision(field: String) = FIELDS.entries.firstOrNull { it.value == field }?.key
    private fun iso(ms: Long): String = if (ms <= 0) "" else Instant.ofEpochMilli(ms).toString()
    private fun millis(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    fun encode(g: GocTranscript): JSONObject {
        val marks = JSONObject()
        g.topics.forEach { t -> FIELDS[t.decision]?.let { field -> marks.put(field, JSONObject().put("status", if (t.status == GocTopicStatus.CONFIRMED) "DISCUSSED" else "NOT_DISCUSSED").put("verifiedBy", g.reviewedBy).put("verifiedAt", t.at)) } }
        return JSONObject()
            .put("lines", JSONArray(g.lines.map { l -> JSONObject().put("text", l.text).put("speaker", when (l.party) { GocParty.DOCTOR_SIDE -> "DOCTOR"; GocParty.PATIENT_PARTY -> "PATIENT_PARTY"; GocParty.UNSET -> JSONObject.NULL }).put("guessed", l.guessed).put("key", l.key).also { o -> if (l.who.isNotBlank()) o.put("who", l.who) } }))
            .put("language", g.language.ifBlank { "auto" }).put("source", g.source).put("machineDraft", g.model.isNotBlank()).put("model", g.model).put("consentAt", iso(g.consentAt))
            .put("reviewedBy", g.reviewedBy).put("reviewedAt", g.reviewedAt).put("audioKept", g.audioKept)
            .put("accepted", JSONArray(g.topics.filter { it.status == GocTopicStatus.CONFIRMED }.map { t -> JSONObject().put("field", FIELDS[t.decision]).put("value", t.optionId ?: JSONObject.NULL).put("quote", t.quote).put("at", t.at) }))
            .put("topics", marks).put("note", g.note)
            .put("planVersion", g.planVersion).put("participants", g.participants).put("noteKind", g.noteKind.name)
            .put("transcribedBy", g.transcribedBy)
            .also { o -> g.signOff?.let { so -> o.put("signOff", JSONObject().put("id", so.id).put("status", "LOCKED").put("sealSha256", so.sealSha256).put("lockedAt", so.lockedAt).put("signers", JSONArray(so.signers.mapIndexed { i, name -> JSONObject().put("name", name).put("role", so.roles.getOrNull(i).orEmpty()).put("registrationNo", so.registrations.getOrNull(i).orEmpty()).put("method", so.methods.getOrNull(i).orEmpty()).put("signedAt", so.signedAt.getOrNull(i).orEmpty()) }))) } }
    }

    /** [recordVersion] is the record's own version: a transcript written on the web carries no planVersion of its own. */
    fun decode(j: JSONObject?, recordVersion: Int = 1): GocTranscript? {
        if (j == null) return null
        val lines = j.optJSONArray("lines")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { o ->
            GocLine(o.optString("text"), if (o.isNull("speaker")) GocParty.UNSET else when (o.optString("speaker")) { "DOCTOR" -> GocParty.DOCTOR_SIDE; "PATIENT_PARTY" -> GocParty.PATIENT_PARTY; else -> GocParty.UNSET }, o.optBoolean("guessed"), o.optString("key"), o.optString("who"))
        } }.orEmpty()
        val accepted = j.optJSONArray("accepted")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { o ->
            decision(o.optString("field"))?.let { d -> GocTopicRecord(d, GocTopicStatus.CONFIRMED, if (o.isNull("value")) null else o.optString("value"), o.optString("quote"), o.optString("at")) }
        } }.orEmpty()
        val marks = j.optJSONObject("topics")
        val verified = if (marks == null) emptyList() else marks.keys().asSequence().toList().mapNotNull { key ->
            val o = marks.optJSONObject(key); val d = decision(key)
            if (o == null || d == null || o.optString("status") != "NOT_DISCUSSED") null else GocTopicRecord(d, GocTopicStatus.NOT_DISCUSSED_VERIFIED, at = o.optString("verifiedAt"))
        }
        return GocTranscript(lines, j.optString("language").takeUnless { it == "auto" }.orEmpty(), j.optString("source").ifBlank { GocTranscript.SOURCE_TYPED }, j.optString("model"), j.optString("participants"), millis(j.optString("consentAt")), j.optInt("planVersion", recordVersion),
            accepted + verified, j.optString("note"), GocNoteKind.entries.firstOrNull { it.name == j.optString("noteKind") } ?: GocNote.kind(accepted), j.optString("reviewedAt"), j.optString("reviewedBy"), j.optBoolean("audioKept"),
            j.optJSONObject("signOff")?.takeIf { it.optString("status") == "LOCKED" && it.optString("id").isNotBlank() }?.let { o ->
                val people = o.optJSONArray("signers")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.takeIf { p -> p.optString("name").isNotBlank() } } }.orEmpty()
                GocSignOffSummary(o.optString("id"), o.optString("sealSha256"), o.optString("lockedAt"), people.map { it.optString("name") },
                    people.map { it.optString("role") }.takeIf { r -> r.all(String::isNotBlank) }.orEmpty(), people.map { it.optString("registrationNo") }, people.map { it.optString("method") }, people.map { it.optString("signedAt") })
            }, j.optString("transcribedBy"))
    }
}

// ── All the choices for each topic, and the one way a confirmed item reaches the plan. ──

data class GocOption(val id: String, val label: Bi, val meaning: Bi? = null)

object GocPlan {
    /** CPR and dialysis first, as the doctors asked on 3 Nov 2026, then the rest of Section III of the form. */
    val ORDER: List<GocDecision> = listOf(GocDecision.CPR, GocDecision.DIALYSIS, GocDecision.BREATHING, GocDecision.ICU, GocDecision.VASOPRESSORS, GocDecision.FEEDING_NG, GocDecision.FEEDING_PEG,
        GocDecision.HYDRATION, GocDecision.TRANSFER, GocDecision.AMBULANCE, GocDecision.GOAL, GocDecision.VALUES)

    fun title(d: GocDecision): Bi = when (d) {
        GocDecision.ICU -> Bi("Intensive care (ICU)", "इंटेंसिव केयर (ICU)")
        GocDecision.BREATHING -> Bi("Highest breathing support", "साँस की सबसे ऊँची मदद")
        GocDecision.DIALYSIS -> Bi("Dialysis", "डायलिसिस")
        GocDecision.VASOPRESSORS -> Bi("Drugs to support blood pressure", "BP सँभालने की दवाएँ")
        GocDecision.FEEDING_NG -> Bi("Nose tube feeding (NG)", "नाक की नली से खाना (NG)")
        GocDecision.FEEDING_PEG -> Bi("Stomach tube (PEG)", "पेट की नली (PEG)")
        GocDecision.HYDRATION -> Bi("Fluids", "तरल (पानी)")
        GocDecision.CPR -> Bi("CPR if the heart stops", "दिल रुकने पर CPR")
        GocDecision.TRANSFER -> Bi("Going to hospital", "अस्पताल जाना")
        GocDecision.AMBULANCE -> Bi("If an ambulance comes", "अगर एम्बुलेंस आए")
        GocDecision.GOAL -> Bi("Goal of care", "देखभाल का लक्ष्य")
        GocDecision.VALUES -> Bi("What matters", "क्या ज़रूरी है")
    }

    /** A neutral prompt for the doctor: what to bring up, never what to choose. */
    fun prompt(d: GocDecision): Bi = when (d) {
        GocDecision.ICU -> Bi("Talk about intensive care: would it be wanted if the illness worsens?", "ICU पर बात करें: बीमारी बढ़ने पर क्या यह चाहेंगे?")
        GocDecision.BREATHING -> Bi("Talk about breathing support, up to the ventilator: how far would be acceptable?", "साँस की मदद और वेंटिलेटर पर बात करें: कहाँ तक मंज़ूर है?")
        GocDecision.DIALYSIS -> Bi("Talk about dialysis if the kidneys fail.", "किडनी फ़ेल होने पर डायलिसिस की बात करें।")
        GocDecision.VASOPRESSORS -> Bi("Talk about drugs that support blood pressure.", "BP सँभालने वाली दवाओं की बात करें।")
        GocDecision.FEEDING_NG -> Bi("Talk about a nose feeding tube.", "नाक की खाने की नली की बात करें।")
        GocDecision.FEEDING_PEG -> Bi("Talk about a stomach feeding tube.", "पेट की खाने की नली की बात करें।")
        GocDecision.HYDRATION -> Bi("Talk about fluids by drip or by mouth.", "ड्रिप या मुँह से तरल की बात करें।")
        GocDecision.CPR -> Bi("Talk about CPR if the heart stops.", "दिल रुकने पर CPR की बात करें।")
        GocDecision.TRANSFER -> Bi("Talk about going to hospital when things get worse.", "हालत बिगड़ने पर अस्पताल जाने की बात करें।")
        GocDecision.AMBULANCE -> Bi("Talk about what an ambulance crew should do.", "एम्बुलेंस टीम क्या करे, इसकी बात करें।")
        GocDecision.GOAL -> Bi("Talk about the overall goal: life, balance or comfort.", "कुल लक्ष्य की बात करें: जीवन, संतुलन या आराम।")
        GocDecision.VALUES -> Bi("Ask what matters most, and what would not be acceptable.", "पूछें कि सबसे ज़रूरी क्या है और क्या मंज़ूर नहीं।")
    }

    fun options(d: GocDecision): List<GocOption> = when (d) {
        GocDecision.ICU -> IcuKind.entries.map { GocOption(it.name, PlanText.icu(it)) }
        GocDecision.BREATHING -> (0..5).map { GocOption(it.toString(), PlanText.ceiling(it), PlanText.ceilingMeaning(it)) }
        GocDecision.DIALYSIS -> DialysisKind.entries.map { GocOption(it.name, PlanText.dialysis(it)) }
        GocDecision.VASOPRESSORS -> VasopressorKind.entries.map { GocOption(it.name, PlanText.vaso(it)) }
        GocDecision.FEEDING_NG -> NgKind.entries.map { GocOption(it.name, PlanText.ng(it)) }
        GocDecision.FEEDING_PEG -> PegFeeding.entries.map { GocOption(it.name, PlanText.peg(it)) }
        GocDecision.HYDRATION -> Hydration.entries.map { GocOption(it.name, PlanText.hydration(it), PlanText.hydrationMeaning(it)) }
        GocDecision.CPR -> Cpr.entries.map { GocOption(it.name, PlanText.cpr(it), PlanText.cprMeaning(it)) }
        GocDecision.TRANSFER -> TransferKind.entries.map { GocOption(it.name, PlanText.transfer(it), PlanText.transferMeaning(it)) }
        GocDecision.AMBULANCE -> AmbulanceOption.entries.map { GocOption(it.name, PlanText.ambulance(it)) }
        GocDecision.GOAL -> GoalOfCare.entries.map { GocOption(it.name, PlanText.goal(it), PlanText.goalMeaning(it)) }
        GocDecision.VALUES -> listOf(GocOption(GOC_VALUES_OWN_WORDS, Bi("Add these words to What matters", "इन शब्दों को 'क्या ज़रूरी है' में जोड़ें")), GocOption(GOC_VALUES_NOT_ACCEPTED, Bi("Add these words to outcomes not accepted", "इन शब्दों को 'जो मंज़ूर नहीं' में जोड़ें")))
    }

    /** What the draft holds now, as an option id. Null is "No preference recorded". */
    fun current(r: EctprRecord, d: GocDecision): String? { val c = r.choices; return when (d) {
        GocDecision.ICU -> c.icu?.kind?.name
        GocDecision.BREATHING -> c.respCeiling?.toString()
        GocDecision.DIALYSIS -> c.dialysis?.kind?.name
        GocDecision.VASOPRESSORS -> c.vasopressors?.kind?.name
        GocDecision.FEEDING_NG -> c.feeding.ng?.kind?.name
        GocDecision.FEEDING_PEG -> c.feeding.peg?.name
        GocDecision.HYDRATION -> c.hydration?.name
        GocDecision.CPR -> c.cpr?.name
        GocDecision.TRANSFER -> c.hospitalTransfer?.kind?.name
        GocDecision.AMBULANCE -> c.ambulance?.name
        GocDecision.GOAL -> c.goalOfCare?.name
        GocDecision.VALUES -> null
    } }

    private inline fun <reified E : Enum<E>> id(value: String): E = enumValues<E>().firstOrNull { it.name == value } ?: throw IllegalArgumentException("Unknown option: $value")

    /**
     * The doctor confirmed one item. This is the only call that reaches the plan, and it changes that one topic only. Same defaults as tapping the choice in the wizard,
     * so the trial, reason and conflict rules (V2, C1 to C4, DNACPR) then ask for what they need. [optionId] null clears the topic back to "No preference recorded".
     */
    fun apply(r: EctprRecord, d: GocDecision, optionId: String?, quotes: List<String> = emptyList()): EctprRecord {
        val c = r.choices
        fun choices(next: PlanChoices) = r.copy(choices = next)
        return when (d) {
            GocDecision.ICU -> choices(c.copy(icu = optionId?.let { id<IcuKind>(it) }?.let { k -> Icu(k, if (k == IcuKind.TRIAL) c.icu?.trial ?: TrialPlan(3, DurationUnit.DAYS) else null) }))
            GocDecision.BREATHING -> { val v = optionId?.toInt()?.also { require(it in 0..5) }; choices(c.copy(respCeiling = v, nivTrial = c.nivTrial.takeIf { (v ?: 0) >= 4 })) }
            GocDecision.DIALYSIS -> choices(c.copy(dialysis = optionId?.let { id<DialysisKind>(it) }?.let { k -> Dialysis(k, if (k == DialysisKind.TRIAL) c.dialysis?.trial ?: TrialPlan(durationUnit = DurationUnit.SESSIONS) else null) }))
            GocDecision.VASOPRESSORS -> choices(c.copy(vasopressors = optionId?.let { id<VasopressorKind>(it) }?.let { k -> Vasopressors(k, if (k == VasopressorKind.TRIAL) c.vasopressors?.trial ?: TrialPlan(durationUnit = DurationUnit.HOURS) else null, if (k == VasopressorKind.YES_MAX_AGENTS) c.vasopressors?.maxAgents ?: 1 else null) }))
            GocDecision.FEEDING_NG -> choices(c.copy(feeding = c.feeding.copy(ng = optionId?.let { id<NgKind>(it) }?.let { k -> NgFeeding(k, if (k == NgKind.UP_TO_DAYS) c.feeding.ng?.days ?: 7 else null) })))
            GocDecision.FEEDING_PEG -> choices(c.copy(feeding = c.feeding.copy(peg = optionId?.let { id<PegFeeding>(it) })))
            GocDecision.HYDRATION -> choices(c.copy(hydration = optionId?.let { id<Hydration>(it) }))
            GocDecision.CPR -> choices(c.copy(cpr = optionId?.let { id<Cpr>(it) }))
            GocDecision.TRANSFER -> choices(c.copy(hospitalTransfer = optionId?.let { id<TransferKind>(it) }?.let { k -> HospitalTransfer(k, if (k == TransferKind.ONLY_FOR) c.hospitalTransfer?.reasons.orEmpty() else emptyList()) }))
            GocDecision.AMBULANCE -> choices(c.copy(ambulance = optionId?.let { id<AmbulanceOption>(it) }))
            GocDecision.GOAL -> choices(c.copy(goalOfCare = optionId?.let { id<GoalOfCare>(it) }))
            GocDecision.VALUES -> when (optionId) {
                null -> r
                GOC_VALUES_OWN_WORDS -> r.copy(values = r.values.copy(ownWords = (r.values.ownWords.lines() + quotes).map(String::trim).filter(String::isNotBlank).distinct().joinToString("\n")))
                GOC_VALUES_NOT_ACCEPTED -> r.copy(values = r.values.copy(unacceptableOutcomes = (r.values.unacceptableOutcomes + quotes).map(String::trim).filter(String::isNotBlank).distinct()))
                else -> throw IllegalArgumentException("Unknown option: $optionId")
            }
        }
    }

    /** Where this topic stands on the checklist. [ticked]: the doctor ticked it during the talk. */
    fun status(d: GocDecision, extraction: GocExtraction, ticked: Set<GocDecision>, topics: Map<GocDecision, GocTopicRecord>): GocChecklistStatus = when {
        topics[d]?.status == GocTopicStatus.CONFIRMED -> GocChecklistStatus.CONFIRMED
        topics[d]?.status == GocTopicStatus.NOT_DISCUSSED_VERIFIED -> GocChecklistStatus.VERIFIED_NOT_DISCUSSED
        extraction.hitsFor(d).isNotEmpty() -> GocChecklistStatus.SUGGESTED
        extraction.discussed(d) || d in ticked -> GocChecklistStatus.MENTIONED
        else -> GocChecklistStatus.NOT_DISCUSSED
    }

    /** The first topic not yet brought up: what the checklist prompts next during the talk. */
    fun next(extraction: GocExtraction, ticked: Set<GocDecision>, topics: Map<GocDecision, GocTopicRecord>): GocDecision? = ORDER.firstOrNull { status(it, extraction, ticked, topics) == GocChecklistStatus.NOT_DISCUSSED }
}

enum class GocChecklistStatus { NOT_DISCUSSED, MENTIONED, SUGGESTED, CONFIRMED, VERIFIED_NOT_DISCUSSED }

// ── The note: what was said and decided, built from fixed wording. Spoken words, and any feeling in them, stay in the transcript. ──

object GocNote {
    fun kind(topics: Collection<GocTopicRecord>): GocNoteKind = if (topics.any { it.status == GocTopicStatus.CONFIRMED && it.optionId != null && it.decision != GocDecision.VALUES }) GocNoteKind.DECISION_TAKEN else GocNoteKind.GENERAL_DISCUSSION
    fun kindLabel(k: GocNoteKind) = when (k) { GocNoteKind.GENERAL_DISCUSSION -> Bi("General discussion", "सामान्य बातचीत"); GocNoteKind.DECISION_TAKEN -> Bi("Decision taken", "फ़ैसला लिया गया") }

    private fun optionLabel(d: GocDecision, id: String?): Bi = id?.let { i -> GocPlan.options(d).firstOrNull { it.id == i }?.label } ?: NO_PREFERENCE

    /** What a decision row says: the confirmed choice, or that it was not discussed (verified or not) and what the plan holds now. */
    internal fun decisionValue(r: EctprRecord, d: GocDecision, topic: GocTopicRecord?): Bi = when (topic?.status) {
        GocTopicStatus.CONFIRMED -> optionLabel(d, topic?.optionId)
        GocTopicStatus.NOT_DISCUSSED_VERIFIED -> Bi.join(" · ", listOf(Bi("Not discussed (doctor verified)", "बात नहीं हुई (डॉक्टर ने जाँचा)"), optionLabel(d, GocPlan.current(r, d))))
        null -> Bi.join(" · ", listOf(Bi("Not discussed, not yet verified", "बात नहीं हुई, अभी जाँचा नहीं"), optionLabel(d, GocPlan.current(r, d))))
    }

    /**
     * The note in the order of the legal Word form (legal review, 31 Oct 2026): Sections I to IV. A topic with nothing recorded says so; only the
     * doctor's tick makes it "verified". Spoken quotes stay in the transcript. The doctor edits the result before it is saved, then it is signed.
     */
    fun draft(r: EctprRecord, topics: Map<GocDecision, GocTopicRecord>, participants: String, hindi: Boolean, notDiscussed: List<Bi> = emptyList()): String = buildList {
        add(Bi("Goals-of-care conversation note", "देखभाल के लक्ष्यों की बातचीत का नोट").pick(hindi))
        add(kindLabel(kind(topics.values)).pick(hindi))
        if (participants.isNotBlank()) add(Bi("Present", "मौजूद").pick(hindi) + ": " + participants.lines().filter(String::isNotBlank).joinToString(", "))
        if (notDiscussed.isNotEmpty()) add(Bi("Not discussed", "बात नहीं हुई").pick(hindi) + ": " + notDiscussed.joinToString("; ") { it.pick(hindi) })
        GocForm.sections(r, topics).forEach { section ->
            add(""); add(section.title.pick(hindi).uppercase())
            section.rows.forEach { add(it.pick(hindi)) }
        }
    }.joinToString("\n")
}

/** One tick box of the paper form. [detail] is what goes in the form's blank (days, hours, L/min, a name). */
data class GocFormTick(val text: Bi, val on: Boolean, val detail: String = "")
/** One line of the form. [value] fills the blank (empty when the boxes carry the answer); [open] means nothing was filled: "Not discussed". */
data class GocFormRow(val label: Bi, val value: Bi, val open: Boolean = false, val ticks: List<GocFormTick> = emptyList())
/** A table of the form: decision-makers, the oxygen LEVEL / WHAT IT MEANS FOR THE PATIENT / CEILING table, Section 19. Cell lines that start with a box are boxes. */
data class GocFormTable(val head: List<Bi>, val rows: List<List<Bi>>)
data class GocFormGroup(val title: Bi, val rows: List<GocFormRow> = emptyList(), val table: GocFormTable? = null)
data class GocFormSection(val id: String, val title: Bi, val groups: List<GocFormGroup>) {
    /** The section as plain lines, the way the signed note and older readers print it. */
    val rows: List<Bi> get() = GocForm.lines(this)
}
/** A signature drawn on the doctor's device for this note (CareGocSignOff.kt), so Section 19 shows it. [role]: DOCTOR, PATIENT, FAMILY or WITNESS. */
data class GocFormSigner(val role: String, val name: String, val signedAt: String, val registrationNo: String = "", val relation: String = "", val thumb: Boolean = false)

/**
 * The goals-of-care note IS the legal form: Healthathon_form_Rituparna.docx (legal review, 31 Oct 2026), with Rituparna's tracked additions.
 * Its own wording, headings, numbering (4.1, 4.2, 5.3, 6.1, 6.2, 12.1, Section 19), order, tick boxes and tables, filled like a completed paper
 * form. The talk screen, the doctor's view, the note text that is signed and the PDF all draw this. Twin of prototype/app/goc-form.ts;
 * line-by-line map in docs/goc-legal-form-coverage.md. Built only from what was confirmed (V1): a blank nobody filled reads "Not discussed".
 */
object GocForm {
    const val TICK_ON = "☑"; const val TICK_OFF = "☐"
    val NOT_DISCUSSED = Bi("Not discussed", "बात नहीं हुई")
    private fun box(on: Boolean) = if (on) TICK_ON else TICK_OFF
    private fun indent(b: Bi) = Bi.join("", listOf(Bi.plain("    "), b))
    private fun field(label: Bi, value: String?): GocFormRow = if (value.isNullOrBlank()) GocFormRow(label, NOT_DISCUSSED, open = true) else GocFormRow(label, Bi.plain(value.trim()))
    private fun boxes(label: Bi, ticks: List<GocFormTick>, unticked: Bi, filled: Bi = Bi.plain("")) =
        if (ticks.any { it.on }) GocFormRow(label, filled, ticks = ticks) else GocFormRow(label, unticked, open = true, ticks = ticks)
    private fun tk(on: Boolean, text: Bi, detail: String? = null) = GocFormTick(text, on, detail?.trim().orEmpty())
    private fun cell(ticks: List<GocFormTick>): Bi = Bi.join("\n", ticks.map { Bi.join("", listOf(Bi.plain(box(it.on) + " "), it.text, Bi.plain(if (it.detail.isBlank()) "" else " " + it.detail))) })
    private fun same(a: String, b: String) = a.trim().lowercase().replace('’', '\'') == b.trim().lowercase().replace('’', '\'')

    /** Form 6.2, in its order. */
    val UNACCEPTABLE = listOf(
        Bi("Being kept alive on a ventilator with no prospect of coming off it", "ऐसे वेंटिलेटर पर ज़िंदा रखा जाना जिससे हटने की कोई उम्मीद न हो"),
        Bi("Being permanently dependent on a feeding tube", "हमेशा के लिए खाने की नली पर निर्भर रहना"),
        Bi("Not being able to recognise or respond to family", "परिवार को पहचान या जवाब न दे पाना"),
        Bi("Surviving with severe, permanent physical dependence", "गंभीर, स्थायी शारीरिक निर्भरता के साथ जीना"),
        Bi("Dying in an ICU, away from family", "परिवार से दूर ICU में मृत्यु"),
        Bi("Uncontrolled pain or breathlessness at any stage", "किसी भी समय बेक़ाबू दर्द या साँस फूलना"),
        Bi("Loss of dignity in bathing, toileting or exposure", "नहाने, शौच या शरीर खुलने में सम्मान खोना"),
        Bi("Being alone at the time of death", "मृत्यु के समय अकेले होना"),
        Bi("Exhausting the family’s savings on treatment that will not change the outcome", "ऐसे इलाज पर परिवार की बचत ख़त्म होना जो नतीजा न बदले"),
    )
    /** The form's own words for each hospital-transfer reason. */
    private fun reason(r: TransferReason): Bi = when (r) {
        TransferReason.PAIN -> Bi("Pain that cannot be controlled at home", "घर पर काबू न होने वाला दर्द")
        TransferReason.BREATHLESSNESS -> Bi("Breathlessness that cannot be controlled at home", "घर पर काबू न होने वाली साँस फूलना")
        TransferReason.BLEEDING -> Bi("Significant bleeding", "ज़्यादा खून बहना")
        TransferReason.SEIZURES -> Bi("Seizures", "दौरे")
        TransferReason.FALL_FRACTURE -> Bi("Fall with suspected fracture", "गिरना और हड्डी टूटने का शक")
        TransferReason.BLOCKED_TUBE -> Bi("Blocked or displaced catheter, feeding tube or stent", "बंद या हिली हुई कैथेटर, खाने की नली या स्टेंट")
        TransferReason.INFECTION_IV -> Bi("Suspected infection needing intravenous treatment", "संक्रमण का शक जिसमें नस से (IV) इलाज चाहिए")
        TransferReason.CONFUSION -> Bi("Acute confusion or agitation not settling", "अचानक भ्रम या बेचैनी जो शांत न हो")
        TransferReason.CAREGIVER_RESPITE -> Bi("Caregiver unable to cope — respite admission", "देखभाल करने वाला संभाल नहीं पा रहा — राहत के लिए भर्ती")
    }
    private fun authority(a: AuthorityBasis): Bi = when (a) {
        AuthorityBasis.NAMED_BY_PATIENT -> Bi("Named by patient", "मरीज़ द्वारा नामित"); AuthorityBasis.NEXT_OF_KIN -> Bi("Family consensus", "परिवार की सहमति")
        AuthorityBasis.NAMED_IN_AMD -> Bi("Named in AMD", "AMD में नामित"); AuthorityBasis.COURT_APPOINTED -> Bi("Court-appointed", "अदालत द्वारा नियुक्त")
    }
    private val AUTHORITY_ORDER = listOf(AuthorityBasis.NAMED_BY_PATIENT, AuthorityBasis.NEXT_OF_KIN, AuthorityBasis.NAMED_IN_AMD, AuthorityBasis.COURT_APPOINTED)
    /** The oxygen table, LEVEL / WHAT IT MEANS FOR THE PATIENT / CEILING, word for word. */
    val OXYGEN_LEVELS = listOf(
        Bi("Comfort measures only", "केवल आराम के उपाय") to Bi("Opioid and anxiolytic for breathlessness, fan, positioning, suction, mouth care. Oxygen used only if it makes the patient feel better — no saturation target is chased.", "साँस फूलने पर ओपिऑइड और घबराहट की दवा, पंखा, सही स्थिति, सक्शन, मुँह की देखभाल। ऑक्सीजन तभी जब उससे आराम मिले — कोई सैचुरेशन लक्ष्य नहीं।"),
        Bi("Nasal prongs / simple face mask", "नाक की नली / साधारण मास्क") to Bi("Low-flow oxygen up to ____ L/min. Comfortable, no interference with talking, eating or being at home.", "कम बहाव की ऑक्सीजन, ____ L/min तक। आरामदायक, बोलने, खाने या घर रहने में बाधा नहीं।"),
        Bi("Non-rebreather / venturi mask", "नॉन-रीब्रीदर / वेंचुरी मास्क") to Bi("Higher-concentration oxygen. Tolerable, but the mask can feel confining and interferes with speech.", "ज़्यादा ऑक्सीजन। सहने लायक़, पर मास्क घुटन भरा लग सकता है और बोलने में बाधा डालता है।"),
        Bi("High-flow nasal cannula (HFNC)", "हाई-फ़्लो नाक ऑक्सीजन (HFNC)") to Bi("Warmed humidified high-flow oxygen. Better tolerated than a mask, usually needs a high-dependency bed, and can be used purely for comfort.", "गर्म, नम, तेज़ बहाव की ऑक्सीजन। मास्क से बेहतर सहन होती है, आम तौर पर HDU बिस्तर चाहिए, और सिर्फ़ आराम के लिए भी दी जा सकती है।"),
        Bi("Non-invasive ventilation (CPAP / BiPAP)", "बिना ट्यूब वेंटिलेशन (CPAP / BiPAP)") to Bi("A tight mask driving air in. Effective for fluid overload and COPD; often uncomfortable, restricts speaking and eating, and can be distressing if used for many hours.", "कसा हुआ मास्क जो हवा भरता है। फ़्लूइड ओवरलोड और COPD में असरदार; अक्सर असहज, बोलना और खाना रोकता है, और कई घंटे चले तो परेशान कर सकता है।"),
        Bi("Intubation and invasive mechanical ventilation", "ट्यूब डालकर वेंटिलेटर") to Bi("A tube in the windpipe, sedation, inability to speak, ICU. Recovery may or may not follow; weaning may not be possible.", "साँस की नली में ट्यूब, बेहोशी की दवा, बोल न पाना, ICU। ठीक हो भी सकते हैं और नहीं भी; मशीन हटाना संभव न हो।"),
    )

    /** An unticked decision: whether it came up, and V1's "No preference recorded". */
    private fun status(d: GocDecision, topics: Map<GocDecision, GocTopicRecord>): Bi = Bi.join(". ", listOf(
        if (topics[d]?.status == GocTopicStatus.NOT_DISCUSSED_VERIFIED) Bi("Not discussed (doctor verified)", "बात नहीं हुई (डॉक्टर ने जाँचा)") else NOT_DISCUSSED, NO_PREFERENCE)).let { Bi.join("", listOf(it, Bi.plain("."))) }
    private fun label(d: GocDecision, r: EctprRecord): Bi = GocPlan.options(d).firstOrNull { it.id == GocPlan.current(r, d) }?.label ?: NO_PREFERENCE
    private fun trial(t: TrialPlan?): String = t?.let { "${it.durationValue} " + PlanText.unit(it.durationUnit).en + (if (it.reviewAt.isNotBlank()) ", review ${it.reviewAt}" else "") }.orEmpty()

    fun sections(r: EctprRecord, topics: Map<GocDecision, GocTopicRecord>, signers: List<GocFormSigner> = emptyList()): List<GocFormSection> {
        val p = r.patient; val dm = r.decisionMakers; val v = r.values; val c = r.choices; val cap = r.capacity; val h = r.homeCare; val w = r.withdrawal
        fun decision(lbl: Bi, d: GocDecision, ticks: List<GocFormTick>) = if (GocPlan.current(r, d) == null) GocFormRow(lbl, status(d, topics), open = true, ticks = ticks) else GocFormRow(lbl, label(d, r), ticks = ticks)
        val people = listOf(dm.primary, dm.alternate)
        fun dmCell(who: DecisionMaker?, pick: (DecisionMaker) -> String): Bi = who?.let(pick)?.takeIf(String::isNotBlank)?.let(Bi::plain) ?: NOT_DISCUSSED
        val dmTable = GocFormTable(listOf(Bi.plain(""), Bi("PRIMARY", "मुख्य"), Bi("SECONDARY", "द्वितीय")), listOf(
            listOf(Bi("Name", "नाम")) + people.map { dmCell(it) { x -> x.name } },
            listOf(Bi("Relationship to patient", "मरीज़ से रिश्ता")) + people.map { dmCell(it) { x -> x.relation } },
            listOf(Bi("Phone (must be reachable 24 h)", "फ़ोन (24 घंटे मिलना चाहिए)")) + people.map { dmCell(it) { x -> x.phone24h } },
            listOf(Bi("ID type & number", "पहचान पत्र का प्रकार और नंबर")) + people.map { dmCell(it) { x -> x.idType?.let { id -> PlanText.idType(id).en + (x.idLast4?.let { l -> " …$l" } ?: "") }.orEmpty() } },
            listOf(Bi("Basis for authority", "अधिकार का आधार")) + people.map { who -> cell(AUTHORITY_ORDER.map { a -> tk(who?.authorityBasis == a, authority(a)) }) },
        ))
        val capacityTicks = listOf(
            tk(cap.status == Capacity.HAS, Bi("The patient has capacity for these decisions and has completed this record themselves.", "मरीज़ में इन फ़ैसलों की क्षमता है और उन्होंने यह रिकॉर्ड ख़ुद भरा।")),
            tk(cap.status == Capacity.HAS_DELEGATED, Bi("The patient has capacity but has asked family to decide with the team. Record the patient’s own words:", "मरीज़ में क्षमता है, पर उन्होंने परिवार से टीम के साथ फ़ैसला करने को कहा। मरीज़ के अपने शब्द:"), cap.basis.takeIf { cap.status == Capacity.HAS_DELEGATED }),
            tk(cap.status == Capacity.FLUCTUATES, Bi("Capacity fluctuates. Assessed by: ____ on ____. Re-assess before acting on any section.", "क्षमता बदलती रहती है। किसने जाँचा: ____ कब: ____। किसी भी भाग पर अमल से पहले दोबारा जाँचें।"), listOf(cap.assessor, cap.assessedOn).filter(String::isNotBlank).joinToString(" · ").takeIf { cap.status == Capacity.FLUCTUATES }),
            tk(cap.status == Capacity.LACKS, Bi("The patient lacks capacity for these decisions. Basis of assessment (understand / retain / weigh / communicate):", "मरीज़ में इन फ़ैसलों की क्षमता नहीं है। जाँच का आधार (समझना / याद रखना / तौलना / बताना):"), cap.basis.takeIf { cap.status == Capacity.LACKS }),
        )
        val dir = r.existingDirective; val amd = dir?.takeIf { it.kind == DirectiveKind.VALID_AMD }
        val directiveTicks = listOf(
            tk(amd != null, Bi("There is a valid written Advance Medical Directive / living will. Executed on ____. Location of original: ____", "वैध लिखित एडवांस मेडिकल डायरेक्टिव / लिविंग विल है। कब बनी: ____। मूल कहाँ है: ____"), amd?.let { listOf(it.date, it.location).filter(String::isNotBlank).joinToString(" · ") }),
            tk(amd?.attestor?.contains("notary", true) == true, indent(Bi("Attested by Notary", "नोटरी ने सत्यापित किया"))),
            tk(amd?.attestor?.contains("gazett", true) == true, indent(Bi("Attested by Gazetted Officer", "राजपत्रित अधिकारी ने सत्यापित किया")), amd?.attestor?.takeIf { !it.contains("notary", true) && !it.contains("gazett", true) }),
            tk(amd?.twoWitnesses == true, indent(Bi("With two witnesses? Yes. If yes, this ECTPR must be read as subordinate to it — the directive governs; this record operationalises it.", "दो गवाहों के साथ? हाँ। अगर हाँ, तो यह ECTPR उसके अधीन है — डायरेक्टिव ही मान्य है; यह रिकॉर्ड उसे लागू करता है।"))),
            tk(amd?.twoWitnesses == false, indent(Bi("With two witnesses? No.", "दो गवाहों के साथ? नहीं।"))),
            tk(dir?.kind == DirectiveKind.PRIOR_WISHES, Bi("There is no directive, but the patient previously expressed clear wishes. Recorded here, with who heard them and when:", "कोई डायरेक्टिव नहीं, पर मरीज़ ने पहले साफ़ इच्छाएँ बताई थीं। यहाँ दर्ज, किसने और कब सुनीं:"), dir?.note?.takeIf { dir?.kind == DirectiveKind.PRIOR_WISHES }),
            tk(dir?.kind == DirectiveKind.NONE, Bi("No directive and no recorded prior wishes. Decisions will be made in the patient’s best interests through family consensus with the team.", "न डायरेक्टिव, न पहले की दर्ज इच्छाएँ। फ़ैसले मरीज़ के हित में, परिवार और टीम की सहमति से होंगे।")),
        )
        // Each box ticks only from its own recorded field; nothing is guessed.
        val knowsTicks = listOf(
            tk(v.knowsPrognosis, Bi("The patient knows the diagnosis and the prognosis.", "मरीज़ बीमारी और आगे क्या होगा, दोनों जानते हैं।")),
            tk(v.patientKnows == true, Bi("The patient knows the diagnosis but not the prognosis.", "मरीज़ बीमारी जानते हैं, पर आगे क्या होगा यह नहीं।")),
            tk(v.familyAskedNotToTell, Bi("The family has asked that the patient not be told. Discussed with the family that the patient may ask directly and will not be lied to. Family understands: Yes / Not yet resolved", "परिवार ने कहा कि मरीज़ को न बताया जाए। परिवार से बात हुई कि मरीज़ सीधे पूछ सकते हैं और उनसे झूठ नहीं बोला जाएगा। परिवार समझता है: हाँ / अभी तय नहीं"), v.directQuestionPlan.takeIf { v.familyAskedNotToTell }),
            tk(v.doesNotWishToKnow, Bi("The patient has said they do not wish to know details and has delegated:", "मरीज़ ने कहा कि वे विस्तार नहीं जानना चाहते और यह सौंपा:")),
        )
        val outcomes = v.unacceptableOutcomes.map(GocExtract::withoutSoundCaptions).map(String::trim).filter(String::isNotBlank)
        val other = outcomes.filter { o -> UNACCEPTABLE.none { same(o, it.en) || same(o, it.hi) } }
        val outcomeTicks = UNACCEPTABLE.map { u -> tk(outcomes.any { same(it, u.en) || same(it, u.hi) }, u) } + tk(other.isNotEmpty(), Bi("Other:", "अन्य:"), other.joinToString("; "))
        fun firstOn(ticks: List<GocFormTick>) = ticks.firstOrNull { it.on }?.let { Bi.join("", listOf(it.text, Bi.plain(if (it.detail.isBlank()) "" else " " + it.detail))) }
        val tr = c.hospitalTransfer
        val transferTicks = listOf(tk(tr?.kind == TransferKind.ANY, Bi("YES — for any significant deterioration.", "हाँ — किसी भी गंभीर बिगड़ने पर।")),
            tk(tr?.kind == TransferKind.ONLY_FOR, Bi("ONLY FOR THE FOLLOWING (tick each that applies):", "केवल इनके लिए (जो लागू हों उन पर निशान):"))) +
            TransferReason.entries.map { x -> GocFormTick(indent(reason(x)), tr?.kind == TransferKind.ONLY_FOR && x in tr!!.reasons) } +
            listOf(tk(false, indent(Bi("Other:", "अन्य:"))), tk(tr?.kind == TransferKind.NO, Bi("NO — the patient is to be cared for where they are.", "नहीं — मरीज़ की देखभाल वहीं होगी जहाँ वे हैं।")))
        val cprTicks = listOf(tk(c.cpr == Cpr.ATTEMPT, Bi("ATTEMPT CPR.", "CPR करें।")), tk(c.cpr == Cpr.DNACPR, Bi("DO NOT ATTEMPT CPR (DNACPR).", "CPR न करें (DNACPR)।")))
        val reasonText = when (c.dnacprReason) { DnacprReason.FUTILITY -> "clinical futility"; DnacprReason.PATIENT_REFUSAL -> "patient refusal"; DnacprReason.BURDEN_OUTWEIGHS_BENEFIT -> "burden outweighs benefit"; null -> "" }
        val ceiling = c.respCeiling
        val oxygen = GocFormTable(listOf(Bi("LEVEL", "स्तर"), Bi("WHAT IT MEANS FOR THE PATIENT", "मरीज़ के लिए इसका मतलब"), Bi("CEILING", "सबसे ऊँचा स्तर")), OXYGEN_LEVELS.mapIndexed { level, (name, meaning) ->
            val filled = if (level == 1 && c.maxOxygenLpm != null) Bi(meaning.en.replace("____", "%.0f".format(c.maxOxygenLpm)), meaning.hi.replace("____", "%.0f".format(c.maxOxygenLpm)), meaning.pick(false).replace("____", "%.0f".format(c.maxOxygenLpm))) else meaning
            listOf(Bi.plain(level.toString()), Bi.join("\n", listOf(name, filled)), Bi.plain(box(ceiling == level)))
        })
        val vp = c.vasopressors; val dy = c.dialysis; val ng = c.feeding.ng
        val vasoTicks = listOf(tk(vp?.kind == VasopressorKind.NO, Bi("No", "नहीं")), tk(vp?.kind == VasopressorKind.TRIAL, Bi("Short trial only, up to ______ hours", "केवल छोटा ट्रायल, ______ घंटे तक"), trial(vp?.trial).takeIf { vp?.kind == VasopressorKind.TRIAL }),
            tk(vp?.kind == VasopressorKind.YES_NO_LIMIT, Bi("Yes, without a pre-set limit", "हाँ, बिना पहले से तय सीमा")), tk(vp?.kind == VasopressorKind.YES_MAX_AGENTS, Bi("Yes, but no escalation beyond ____ agents", "हाँ, पर ____ दवाओं से आगे नहीं"), vp?.maxAgents?.toString()?.takeIf { vp?.kind == VasopressorKind.YES_MAX_AGENTS }))
        val dialysisTicks = listOf(tk(dy?.kind == DialysisKind.NO, Bi("No", "नहीं")), tk(dy?.kind == DialysisKind.TRIAL, Bi("Trial of ______ sessions / days, then review", "______ सत्र / दिन का ट्रायल, फिर समीक्षा"), trial(dy?.trial).takeIf { dy?.kind == DialysisKind.TRIAL }),
            tk(dy?.kind == DialysisKind.YES, Bi("Yes", "हाँ")), tk(dy?.kind == DialysisKind.ONLY_IF_REVERSIBLE, Bi("Only if the renal failure is judged reversible", "केवल तब जब किडनी फ़ेल्योर ठीक होने वाला माना जाए")))
        val ngTicks = listOf(tk(ng?.kind == NgKind.NO, Bi("No", "नहीं")), tk(ng?.kind == NgKind.UP_TO_DAYS, Bi("Yes, short-term only, up to ______ days, then review", "हाँ, थोड़े समय, ______ दिन तक, फिर समीक्षा"), ng?.days?.let { "$it days" }?.takeIf { ng?.kind == NgKind.UP_TO_DAYS }), tk(ng?.kind == NgKind.YES, Bi("Yes", "हाँ")))
        val pegTicks = listOf(tk(c.feeding.peg == PegFeeding.NO, Bi("No", "नहीं")), tk(c.feeding.peg == PegFeeding.ONLY_AFTER_PALLIATIVE_DISCUSSION, Bi("Only after discussion with the palliative care team", "केवल पैलिएटिव केयर टीम से बात के बाद")), tk(c.feeding.peg == PegFeeding.YES, Bi("Yes", "हाँ")))
        val goalTicks = listOf(
            tk(c.goalOfCare == GoalOfCare.A_LIFE, Bi("A. PRIORITISE LIFE — All medically effective treatment, including ICU and ventilation.", "A. जीवन को प्राथमिकता — ICU और वेंटिलेटर सहित हर असरदार इलाज।")),
            tk(c.goalOfCare == GoalOfCare.B_CEILING, Bi("B. BALANCED / CEILING OF CARE — Treat reversible problems, but with the specific limits and as a time-limited trial.", "B. संतुलित / इलाज की सीमा — ठीक होने वाली समस्याओं का इलाज, तय सीमाओं में और समय-सीमित ट्रायल के रूप में।")),
            tk(c.goalOfCare == GoalOfCare.C_COMFORT, Bi("C. PRIORITISE COMFORT — Symptom control is the goal. Hospital transfer only if comfort cannot be achieved where the patient is.", "C. आराम को प्राथमिकता — लक्षणों पर नियंत्रण ही लक्ष्य। अस्पताल तभी जब वहाँ आराम न मिल सके।")),
        )
        val withdrawn = w?.applicable == true
        fun wField(lbl: Bi, value: String?) = if (withdrawn) field(lbl, value) else GocFormRow(lbl, NOT_DISCUSSED, open = true)
        val finalTicks = listOf(
            tk(withdrawn && w?.finalDecision == WithdrawalFinal.TO_BE_PERFORMED, Bi("withdrawal/withholding to be performed", "इलाज रोका जाएगा / शुरू नहीं होगा")),
            tk(withdrawn && w?.finalDecision == WithdrawalFinal.FAMILY_REFUSED, Bi("Family has refused consent (if patient has lost decision-making capacity)", "परिवार ने सहमति नहीं दी (अगर मरीज़ निर्णय क्षमता खो चुके हों)")),
            tk(withdrawn && w?.finalDecision == WithdrawalFinal.BOARD_REFUSED, Bi("Primary/Secondary Board has refused permission", "प्राथमिक/द्वितीय बोर्ड ने अनुमति नहीं दी")),
            tk(withdrawn && w?.finalDecision == WithdrawalFinal.PHYSICIAN_REVOKED, Bi("Treating physician has revoked their decision to support withdrawal/withholding", "इलाज करने वाले डॉक्टर ने इलाज रोकने/शुरू न करने का समर्थन वापस लिया")),
        )
        // Section 19: the plan's signatures first, then those drawn on this device for this note.
        fun planSig(match: (Signature) -> Boolean) = r.signatures.firstOrNull(match)
        fun dev(role: String) = signers.firstOrNull { it.role == role }
        val signedWord = Bi("Signed", "हस्ताक्षर किए"); val thumbWord = Bi("Thumb impression", "अँगूठे का निशान")
        fun how(thumb: Boolean?) = if (thumb == true) thumbWord else signedWord
        val patientSig = planSig { it.role == SignatureRole.PATIENT }; val patientDev = dev("PATIENT"); val familyDev = dev("FAMILY")
        val primary = planSig { it.role == SignatureRole.SURROGATE && it.surrogateRank != SurrogateRank.SECONDARY }; val secondary = planSig { it.role == SignatureRole.SURROGATE && it.surrogateRank == SurrogateRank.SECONDARY }
        val witnesses = r.signatures.filter { it.role == SignatureRole.WITNESS }; val witnessDev = dev("WITNESS")
        fun clinician(role: SignatureRole, device: GocFormSigner?): List<Bi> { val s = planSig { it.role == role }
            return listOf(Bi.plain(s?.name ?: device?.name.orEmpty()), Bi.plain(s?.registrationNo ?: device?.registrationNo.orEmpty()),
                if (s != null) how(s.thumbImpression) else if (device != null) how(device.thumb) else Bi.plain(""), Bi.plain((s?.signedAt ?: device?.signedAt.orEmpty()).take(10)))
        }
        val blank = Bi.plain("")

        return listOf(
            GocFormSection("I", Bi("Section I – General information", "भाग I – सामान्य जानकारी"), listOf(
                GocFormGroup(Bi("Patient details", "मरीज़ की जानकारी"), listOf(
                    field(Bi("Name", "नाम"), p.name), field(Bi("Age, Sex, Gender", "उम्र, लिंग, जेंडर"), listOf(p.dob, p.sex, p.gender).filter(String::isNotBlank).joinToString(" · ")),
                    field(Bi("Address", "पता"), p.address), field(Bi("Phone number", "फ़ोन नंबर"), p.phone))),
                GocFormGroup(Bi("Physician details", "डॉक्टरों की जानकारी"), listOf(
                    field(Bi("Primary Oncologist name and hospital – and contact", "मुख्य ऑन्कोलॉजिस्ट का नाम और अस्पताल – और संपर्क"), r.careTeam.oncologist),
                    field(Bi("Palliative care physician name and hospital – and contact", "पैलिएटिव केयर डॉक्टर का नाम और अस्पताल – और संपर्क"), listOf(r.careTeam.palliativePhysician, r.careTeam.palliativePhone24h).filter(String::isNotBlank).joinToString(" · ")),
                    field(Bi("Additional space for the team – Surgical oncology, radiation oncology, etc.", "टीम के लिए और जगह – सर्जिकल ऑन्कोलॉजी, रेडिएशन ऑन्कोलॉजी आदि"), r.careTeam.extraTeam.filter(String::isNotBlank).joinToString("; ")),
                    field(Bi("Additional option to add a local physician who can be briefed about the care and part of the team", "स्थानीय डॉक्टर जिन्हें देखभाल के बारे में बताया जा सके और जो टीम में हों"), r.careTeam.localDoctor))),
                GocFormGroup(Bi("Family/Friend details", "परिवार/दोस्तों की जानकारी"), listOf(
                    field(Bi("Members present for discussion and relationship with the patient", "बातचीत में मौजूद सदस्य और मरीज़ से रिश्ता"), dm.presentAtDiscussion.filter(String::isNotBlank).joinToString("; ")),
                    field(Bi("Language preferred", "पसंद की भाषा"), p.languagePref))),
                GocFormGroup(Bi("Primary decision maker and contact details · Secondary decision maker and contact details", "मुख्य फ़ैसला लेने वाले और संपर्क · द्वितीय फ़ैसला लेने वाले और संपर्क"), table = dmTable),
                GocFormGroup(Bi("Family members who must be informed before any major decision (name, relation, phone)", "जिन परिवार वालों को किसी बड़े फ़ैसले से पहले बताना ज़रूरी है (नाम, रिश्ता, फ़ोन)"), listOf(
                    field(Bi("Name, relation, phone", "नाम, रिश्ता, फ़ोन"), dm.familyToInform.filter(String::isNotBlank).joinToString("; ")),
                    field(Bi("Family/friends who were attempted to be contacted, but were unavailable", "परिवार/दोस्त जिनसे संपर्क की कोशिश हुई, पर नहीं मिले"), dm.attemptedContact.filter(String::isNotBlank).joinToString("; ")))),
            )),
            GocFormSection("II", Bi("Section II – Preliminary medical information and patient values", "भाग II – शुरुआती चिकित्सा जानकारी और मरीज़ के मूल्य"), listOf(
                GocFormGroup(Bi.plain(""), listOf(
                    field(Bi("Primary diagnosis and stage", "मुख्य बीमारी और स्टेज"), listOf(r.clinical.diagnosis, r.clinical.stage).filter(String::isNotBlank).joinToString(" · ")),
                    field(Bi("Functional status – ECOG", "काम करने की क्षमता – ECOG"), r.clinical.ecog?.toString()),
                    firstOn(capacityTicks)?.let { GocFormRow(Bi("Patient’s capacity", "मरीज़ की क्षमता"), it) } ?: GocFormRow(Bi("Patient’s capacity", "मरीज़ की क्षमता"), NOT_DISCUSSED, open = true),
                    firstOn(directiveTicks)?.let { GocFormRow(Bi("Existing medical directives", "पहले के चिकित्सा निर्देश"), it) } ?: GocFormRow(Bi("Existing medical directives", "पहले के चिकित्सा निर्देश"), NOT_DISCUSSED, open = true),
                    firstOn(knowsTicks)?.let { GocFormRow(Bi("What the patient knows", "मरीज़ क्या जानते हैं"), it) } ?: GocFormRow(Bi("What the patient knows", "मरीज़ क्या जानते हैं"), NOT_DISCUSSED, open = true),
                )),
                GocFormGroup(Bi("4.1  The patient’s capacity, now", "4.1  मरीज़ की अभी की क्षमता"), listOf(boxes(blank, capacityTicks, NOT_DISCUSSED))),
                GocFormGroup(Bi("4.2  Existing directives", "4.2  पहले के निर्देश"), listOf(boxes(blank, directiveTicks, NOT_DISCUSSED))),
                GocFormGroup(Bi("5.3  What the patient knows", "5.3  मरीज़ क्या जानते हैं"), listOf(boxes(blank, knowsTicks, NOT_DISCUSSED))),
                GocFormGroup(Bi("6.1  In their own words", "6.1  उनके अपने शब्दों में"), listOf(
                    field(Bi("The most distressing physical symptom right now is", "अभी सबसे ज़्यादा तकलीफ़ देने वाला शारीरिक लक्षण है"), null),
                    field(Bi("What matters most to me / to them is", "मेरे / उनके लिए सबसे ज़रूरी है"), v.ownWords.lines().map(GocExtract::withoutSoundCaptions).filter(String::isNotBlank).joinToString("; ")),
                    field(Bi("What I am most afraid of is", "मुझे सबसे ज़्यादा डर है"), null),
                    field(Bi("Unfinished things that matter (people to see, disputes to settle, papers to sign, a place to visit)", "अधूरे ज़रूरी काम (किससे मिलना है, कौन सा झगड़ा सुलझाना है, कौन से काग़ज़ साइन करने हैं, कहाँ जाना है)"), null))),
                GocFormGroup(Bi("6.2  Outcomes that would be unacceptable  (tick any that apply)", "6.2  जो हालत मंज़ूर नहीं  (जो लागू हों उन पर निशान)"), listOf(boxes(blank, outcomeTicks, NOT_DISCUSSED))),
            )),
            GocFormSection("III", Bi("Section III – Decisive Actions", "भाग III – निर्णायक फ़ैसले"), listOf(
                GocFormGroup(Bi("Home care (if relevant)", "घर पर देखभाल (अगर लागू हो)"), listOf(
                    decision(Bi("Should the patient be brought to the hospital? (To inform any doctor that has been called to the home for evaluation)", "क्या मरीज़ को अस्पताल लाया जाए? (घर पर जाँच के लिए बुलाए गए किसी भी डॉक्टर को बताने के लिए)"), GocDecision.TRANSFER, transferTicks),
                    field(Bi("If not, what is the frequency of assessments, and the details of the person coordinating between the patient/family and the relevant hospital/centre", "अगर नहीं, तो जाँच कितनी बार होगी, और मरीज़/परिवार और अस्पताल/केंद्र के बीच तालमेल रखने वाले व्यक्ति की जानकारी"), listOfNotNull(h?.assessmentFrequency, h?.coordinator).filter(String::isNotBlank).joinToString(" · ")),
                    field(Bi("If a non-governmental organisation has been taking care of the patients, contact details of their representative + declaration confirming consultation with the NGO", "अगर कोई ग़ैर-सरकारी संस्था मरीज़ की देखभाल कर रही है, तो उसके प्रतिनिधि का संपर्क + संस्था से सलाह की पुष्टि"), h?.ngoContact?.takeIf(String::isNotBlank)?.let { it + if (h?.ngoConsulted == true) " · $TICK_ON NGO consulted" else "" }),
                    field(Bi("Had they been previously admitted to any hospital for the current condition?", "क्या इसी बीमारी के लिए पहले किसी अस्पताल में भर्ती हुए थे?"), when (h?.previousAdmission) { true -> "Yes" + h?.previousAdmissionDetails.orEmpty().takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty(); false -> "No"; null -> null }))),
                GocFormGroup(Bi("Should CPR be performed?", "क्या CPR किया जाए?"), listOf(
                    decision(Bi.plain("CPR"), GocDecision.CPR, cprTicks),
                    field(Bi("Reason recorded (clinical futility / patient refusal / burden outweighs benefit)", "दर्ज कारण (इलाज बेअसर / मरीज़ ने मना किया / नुकसान फ़ायदे से ज़्यादा)"), reasonText),
                    field(Bi("Discussed with (patient / surrogate / both)", "किससे बात हुई (मरीज़ / प्रतिनिधि / दोनों)"), c.dnacprDiscussedWith))),
                GocFormGroup(Bi("What is the level of oxygen/ventilatory support that should be provided?", "ऑक्सीजन/साँस की कितनी मदद दी जानी चाहिए?"),
                    if (ceiling == null) listOf(GocFormRow(Bi("Ceiling", "सबसे ऊँचा स्तर"), status(GocDecision.BREATHING, topics), open = true)) else emptyList(), oxygen),
                GocFormGroup(Bi("Other parameters", "दूसरी बातें"), listOf(
                    decision(Bi("Dialysis or CRRT", "डायलिसिस या CRRT"), GocDecision.DIALYSIS, dialysisTicks),
                    field(Bi("Others", "अन्य"), null),
                    GocFormRow(Bi("Requirement of ICU?", "ICU की ज़रूरत?"), blank))),
                GocFormGroup(Bi("If withdrawal and withholding of treatment has been decided (as per Common Cause v Union of India, 2018 and 2023)", "अगर इलाज रोकने और शुरू न करने का फ़ैसला हुआ है (कॉमन कॉज़ बनाम भारत संघ, 2018 और 2023 के अनुसार)"), listOf(
                    wField(Bi("Dates of constitution of the Primary and Secondary Boards", "प्राथमिक और द्वितीय बोर्ड बनने की तारीख़ें"), listOfNotNull(w?.primaryBoardDate, w?.secondaryBoardDate).filter(String::isNotBlank).joinToString(" · ")),
                    wField(Bi("Composition of the Boards", "बोर्ड के सदस्य"), listOfNotNull(w?.primaryBoardMembers, w?.secondaryBoardMembers).filter(String::isNotBlank).joinToString(" · ")),
                    wField(Bi("Decision of the Boards", "बोर्ड का फ़ैसला"), w?.boardDecision),
                    boxes(Bi("Final decision about withdrawal/withholding", "इलाज रोकने/शुरू न करने का अंतिम फ़ैसला"), finalTicks, NOT_DISCUSSED),
                    wField(Bi("Protocol for withdrawal/withholding", "इलाज रोकने/शुरू न करने का तरीका"), w?.protocol))),
                GocFormGroup(Bi("Vasopressors / inotropes", "वैसोप्रेसर / इनोट्रोप"), listOf(decision(Bi("Vasopressors / inotropes", "वैसोप्रेसर / इनोट्रोप"), GocDecision.VASOPRESSORS, vasoTicks))),
                GocFormGroup(Bi("12.1  Feeding", "12.1  खाना"), listOf(
                    GocFormRow(blank, blank, ticks = listOf(tk(true, Bi("Comfort feeding by mouth only — careful hand feeding, sips, tastes, favourite foods, no volume target, no forcing. Mouth care given regardless.", "केवल मुँह से आराम के लिए खाना — ध्यान से हाथ से खिलाना, घूँट, स्वाद, पसंद का खाना, कोई मात्रा लक्ष्य नहीं, कोई ज़बरदस्ती नहीं। मुँह की देखभाल हर हाल में।")))),
                    decision(Bi("Nasogastric (Ryle’s) tube", "नाक की नली (राइल्स ट्यूब)"), GocDecision.FEEDING_NG, ngTicks),
                    decision(Bi("Gastrostomy (PEG)", "पेट की नली (PEG)"), GocDecision.FEEDING_PEG, pegTicks))),
                GocFormGroup(Bi("Goal of care", "देखभाल का लक्ष्य"), listOf(decision(Bi("A, B or C", "A, B या C"), GocDecision.GOAL, goalTicks))),
            )),
            GocFormSection("IV", Bi("Section IV – Signatures and declarations (SECTION 19 — Declarations and signatures)", "भाग IV – हस्ताक्षर और घोषणाएँ (भाग 19 — घोषणाएँ और हस्ताक्षर)"), listOf(
                GocFormGroup(Bi("19.1  Patient", "19.1  मरीज़"), table = GocFormTable(listOf(
                    cell(listOf(tk(cap.status == Capacity.HAS, Bi("I have made these choices myself.", "ये फ़ैसले मैंने ख़ुद लिए।")), tk(cap.status == Capacity.HAS_DELEGATED, Bi("I have chosen not to be involved in detail and have delegated to my surrogate.", "मैंने विस्तार में शामिल न होने का फ़ैसला किया और अपने प्रतिनिधि को सौंपा।")))),
                    Bi("Signature / thumb impression", "हस्ताक्षर / अँगूठे का निशान"), Bi("Name in capitals", "बड़े अक्षरों में नाम"), Bi("Date", "तारीख़")),
                    listOf(listOf(blank, if (patientSig != null) how(patientSig.thumbImpression) else if (patientDev != null) how(patientDev.thumb) else blank,
                        Bi.plain((patientSig?.name ?: patientDev?.name.orEmpty()).uppercase()), Bi.plain((patientSig?.signedAt ?: patientDev?.signedAt.orEmpty()).take(10)))))),
                GocFormGroup(Bi("19.2  Surrogate / healthcare proxy (primary and secondary surrogate decision-makers)", "19.2  प्रतिनिधि / हेल्थकेयर प्रॉक्सी (मुख्य और द्वितीय फ़ैसला लेने वाले)"), table = GocFormTable(listOf(
                    cell(listOf(tk(r.surrogateDeclaration.confirmed, Bi("I confirm I am conveying what the patient would have wanted, so far as I can judge.", "मैं पुष्टि करता/करती हूँ कि जितना मैं समझ सकता/सकती हूँ, मैं वही बता रहा/रही हूँ जो मरीज़ चाहते।")))),
                    Bi("Signature", "हस्ताक्षर"), Bi("Name & relationship", "नाम और रिश्ता"), Bi("Date", "तारीख़")), listOf(
                    listOf(Bi("Primary", "मुख्य"), if (primary != null) how(primary.thumbImpression) else if (familyDev != null) how(familyDev.thumb) else blank,
                        Bi.plain(primary?.name ?: familyDev?.let { f -> f.name + f.relation.takeIf(String::isNotBlank)?.let { " ($it)" }.orEmpty() }.orEmpty()), Bi.plain((primary?.signedAt ?: familyDev?.signedAt.orEmpty()).take(10))),
                    listOf(Bi("Secondary", "द्वितीय"), secondary?.let { how(it.thumbImpression) } ?: blank, Bi.plain(secondary?.name.orEmpty()), Bi.plain(secondary?.signedAt.orEmpty().take(10)))))),
                GocFormGroup(Bi("19.3  Witnesses  (required where the patient signs by thumb impression, or where literacy is limited – for both home-based and hospital-based evaluation)", "19.3  गवाह  (जब मरीज़ अँगूठा लगाएँ, या पढ़ना-लिखना कम हो, तब ज़रूरी – घर और अस्पताल दोनों जगह की जाँच में)"), table = GocFormTable(
                    listOf(Bi("Witness — name", "गवाह — नाम"), Bi("Address / ID", "पता / पहचान"), Bi("Signature", "हस्ताक्षर"), Bi("Date", "तारीख़")), (0..1).map { i ->
                        val s = witnesses.getOrNull(i); val d = if (i == 0 && s == null) witnessDev else null
                        listOf(Bi.join("", listOf(Bi("Witness", "गवाह"), Bi.plain(" ${i + 1} — " + (s?.name ?: d?.name.orEmpty())))), Bi.plain(d?.relation.orEmpty()),
                            if (s != null) how(s.thumbImpression) else if (d != null) how(d.thumb) else blank, Bi.plain((s?.signedAt ?: d?.signedAt.orEmpty()).take(10)))
                    })),
                GocFormGroup(Bi("19.4  Clinicians", "19.4  डॉक्टर और नर्स"), table = GocFormTable(
                    listOf(Bi("ROLE", "भूमिका"), Bi("NAME", "नाम"), Bi("REGISTRATION NO.", "रजिस्ट्रेशन नंबर"), Bi("SIGNATURE", "हस्ताक्षर"), Bi("DATE", "तारीख़")), listOf(
                        listOf(Bi("Treating consultant", "इलाज करने वाले कंसल्टेंट")) + clinician(SignatureRole.TREATING_CONSULTANT, dev("DOCTOR")),
                        listOf(Bi("Palliative care clinician", "पैलिएटिव केयर डॉक्टर")) + clinician(SignatureRole.PALLIATIVE_CLINICIAN, null),
                        listOf(Bi("Nurse involved in the discussion", "बातचीत में शामिल नर्स")) + clinician(SignatureRole.NURSE, null)))),
            )),
        )
    }

    private fun tickLine(t: GocFormTick) = Bi.join("", listOf(Bi.plain(box(t.on) + " "), t.text, Bi.plain(if (t.detail.isBlank()) "" else " " + t.detail)))

    /** The section as plain lines: group title, "- label: value", each box indented, and tables as "| a | b |". */
    fun lines(s: GocFormSection): List<Bi> = buildList {
        s.groups.forEach { g ->
            if (g.title.en.isNotBlank()) add(g.title)
            g.rows.forEach { row ->
                if (row.label.en.isNotBlank() || row.value.en.isNotBlank()) add(Bi.join("", listOf(Bi.plain("- "), if (row.label.en.isBlank()) row.value else Bi.labeled(row.label, row.value))))
                row.ticks.forEach { add(Bi.join("", listOf(Bi.plain("    "), tickLine(it)))) }
            }
            g.table?.let { t -> (listOf(t.head) + t.rows).forEach { cells -> add(Bi.join("", listOf(Bi.plain("| "), Bi.join(" | ", cells.map { c -> Bi(c.en.replace("\n", " / "), c.hi.replace("\n", " / "), c.pick(false).replace("\n", " / ")) }), Bi.plain(" |")))) } }
        }
    }
}

/** Carry-forward: a review draft is a copy of the last signed plan, so its transcript belongs to an earlier version until a new talk replaces it. */
object GocCarry {
    /** The earlier version's saved talk, when this draft has not had its own yet. */
    fun previous(r: EctprRecord): GocTranscript? = r.gocTranscript?.takeIf { it.planVersion < r.version }
    /** Whether a choice in the draft came from the earlier version and still waits for this talk's confirmation. */
    fun carried(r: EctprRecord, d: GocDecision, topics: Map<GocDecision, GocTopicRecord>): Boolean = r.supersedes != null && topics[d] == null && GocPlan.current(r, d) != null
    /** What a new talk starts from: the earlier note as an editable draft, no lines and no confirmed topics. */
    fun startingNote(r: EctprRecord): String = previous(r)?.note.orEmpty()
}

// ── The guide for the talk, in the order the doctors asked for on 3 Nov 2026: consent, patient details, family and emergency contact,
// then CPR, dialysis and the other decisions, then the rest of the form. Items tick themselves when the words come up. ──

enum class GocGuideItem(val decision: GocDecision?) {
    CONSENT(null), PATIENT_DETAILS(null), FAMILY_CONTACT(null),
    CPR(GocDecision.CPR), DIALYSIS(GocDecision.DIALYSIS), BREATHING(GocDecision.BREATHING), ICU(GocDecision.ICU), VASOPRESSORS(GocDecision.VASOPRESSORS),
    FEEDING_NG(GocDecision.FEEDING_NG), FEEDING_PEG(GocDecision.FEEDING_PEG), HYDRATION(GocDecision.HYDRATION), TRANSFER(GocDecision.TRANSFER),
    AMBULANCE(GocDecision.AMBULANCE), GOAL(GocDecision.GOAL), UNDERSTANDING(null), VALUES(GocDecision.VALUES), HOME_CARE(null),
}

object GocGuide {
    val ORDER: List<GocGuideItem> = GocGuideItem.entries

    // Machine-written for hi, te, ur, kn [needs native-speaker review]. Only for ticking the guide: never a choice.
    private val TERMS: Map<GocGuideItem, Lex> = mapOf(
        GocGuideItem.CONSENT to Lex(en("consent*", "permission", "record*", "agree to the recording", "sahmati", "ijazat"), hi("सहमति", "सहमत", "इजाज़त", "रिकॉर्ड*"), te("అనుమతి*", "సమ్మతి*", "రికార్డ్*"), ur("اجازت", "رضامندی", "ریکارڈ*"), kn("ಅನುಮತಿ*", "ರೆಕಾರ್ಡ್*")),
        GocGuideItem.PATIENT_DETAILS to Lex(en("your name", "full name", "his name", "her name", "how old", "age", "years old", "date of birth", "address", "where do you live", "naam", "umar", "umr", "saal"), hi("नाम", "उम्र", "आयु", "साल"), te("పేరు*", "వయస్సు*", "వయసు*", "చిరునామా*"), ur("نام", "عمر", "سال"), kn("ಹೆಸರು*", "ವಯಸ್ಸು*", "ವಿಳಾಸ*")),
        GocGuideItem.FAMILY_CONTACT to Lex(en("emergency*", "phone*", "mobile*", "contact*", "next of kin", "decide*", "decision*", "speak for"), hi("इमरजेंसी", "आपातकाल*", "फ़ोन", "नंबर", "फ़ैसला", "निर्णय"), te("అత్యవసర*", "ఎమర్జెన్సీ*", "ఫోన్*", "నిర్ణయ*"), ur("ایمرجنسی", "فون", "فیصلہ"), kn("ತುರ್ತು*", "ಎಮರ್ಜೆನ್ಸಿ*", "ಫೋನ್*", "ನಿರ್ಧಾರ*")),
        GocGuideItem.UNDERSTANDING to Lex(en("cannot be cured", "cant be cured", "not curable", "incurable", "no cure", "prognosis", "illness", "cancer"), hi("लाइलाज", "ठीक नहीं हो", "बीमारी", "कैंसर"), te("నయం*", "జబ్బు*", "వ్యాధి*", "క్యాన్సర్*"), ur("لاعلاج", "بیماری", "کینسر"), kn("ಗುಣ*", "ಕಾಯಿಲೆ*", "ಕ್ಯಾನ್ಸರ್*")),
        GocGuideItem.HOME_CARE to Lex(en("home care", "home nurse", "home visit*", "hospice", "ngo", "coordinator"), hi("घर पर देखभाल", "नर्स", "एनजीओ"), te("ఇంటి వద్ద*", "నర్సు*"), ur("گھر پر دیکھ بھال", "نرس"), kn("ಮನೆ ಆರೈಕೆ*", "ನರ್ಸ್*")),
    )

    /** Which form section the item belongs to, shown as a small tag ("I", "II", "III"). Consent comes before the form. */
    fun section(item: GocGuideItem): String? = when (item) {
        GocGuideItem.CONSENT -> null
        GocGuideItem.PATIENT_DETAILS, GocGuideItem.FAMILY_CONTACT -> "I"
        GocGuideItem.UNDERSTANDING, GocGuideItem.VALUES -> "II"
        else -> "III"
    }

    fun title(item: GocGuideItem): Bi = item.decision?.let { GocPlan.title(it) } ?: when (item) {
        GocGuideItem.CONSENT -> Bi("Consent for this conversation", "इस बातचीत के लिए सहमति")
        GocGuideItem.PATIENT_DETAILS -> Bi("Patient details", "मरीज़ की जानकारी")
        GocGuideItem.FAMILY_CONTACT -> Bi("Family details and emergency contact", "परिवार और इमरजेंसी संपर्क")
        GocGuideItem.UNDERSTANDING -> Bi("Understanding the illness", "बीमारी की समझ")
        else -> Bi("Home care (if relevant)", "घर पर देखभाल (अगर लागू हो)")
    }

    /** The guide items the words touch at all, in any line, either side. */
    fun covered(text: String): Set<GocGuideItem> {
        if (text.isBlank()) return emptySet()
        val words = gocTokens(text).map { it.norm }
        return TERMS.filterValues { it.any(words) }.keys
    }

    /** [ticked]: items the doctor ticked by hand. [consented]: everyone agreed in the app, which covers consent. */
    fun status(item: GocGuideItem, extraction: GocExtraction, covered: Set<GocGuideItem>, ticked: Set<GocGuideItem>, topics: Map<GocDecision, GocTopicRecord>, consented: Boolean): GocChecklistStatus {
        item.decision?.let { d -> return GocPlan.status(d, extraction, if (item in ticked) setOf(d) else emptySet(), topics) }
        return when {
            item == GocGuideItem.CONSENT && consented -> GocChecklistStatus.CONFIRMED
            item in covered || item in ticked -> GocChecklistStatus.MENTIONED
            else -> GocChecklistStatus.NOT_DISCUSSED
        }
    }

    /** What the end-of-talk prompt lists: not covered, not ticked, and not verified by the doctor as not discussed. */
    fun undiscussed(extraction: GocExtraction, covered: Set<GocGuideItem>, ticked: Set<GocGuideItem>, topics: Map<GocDecision, GocTopicRecord>, consented: Boolean): List<GocGuideItem> =
        ORDER.filter { status(it, extraction, covered, ticked, topics, consented) == GocChecklistStatus.NOT_DISCUSSED }

    fun next(extraction: GocExtraction, covered: Set<GocGuideItem>, ticked: Set<GocGuideItem>, topics: Map<GocDecision, GocTopicRecord>, consented: Boolean): GocGuideItem? =
        undiscussed(extraction, covered, ticked, topics, consented).firstOrNull()
}

// ── Latest statement wins, per person. Twin of gocStanding in prototype/app/goc-extract.ts. ──

/** What still stands for one decision, and the earlier statements a later one replaced (shown so the doctor sees the change of mind). */
data class GocStanding(val current: List<GocHit>, val superseded: List<Pair<GocHit, GocHit>>, val additive: Boolean = false) {
    val suggested: Map<String, List<GocHit>> get() = current.flatMap { h -> h.options.map { it to h } }.groupBy({ it.first }, { it.second })
    val ruledOut: Map<String, List<GocHit>> get() = current.flatMap { h -> h.ruledOut.map { it to h } }.groupBy({ it.first }, { it.second })
    val changedMind: Boolean get() = superseded.isNotEmpty()
    /** Still standing on different sides, which only happens between different people. */
    val conflict: Boolean get() = !additive && current.filter { it.options.isNotEmpty() }.map { it.options.toSet() }.toSet().size > 1 ||
        current.any { a -> current.any { b -> a !== b && a.options.any { it in b.ruledOut } } }
}

object GocLatest {
    /**
     * A later statement by the same person replaces an earlier one about the same decision when they disagree: a different choice, or a "no" to an option
     * that was earlier wanted (and the reverse). "No ventilator" then "BiPAP is fine" agree, so both stand. Different people never replace each other.
     * Unlabelled lines count as one unknown speaker. Lines the doctor marked as the doctor side never become a suggestion. Nothing is chosen (V1).
     */
    fun standing(extraction: GocExtraction, d: GocDecision, lines: List<GocLine>? = null): GocStanding {
        val own = extraction.hitsFor(d)
            .filter { h -> lines == null || GocLines.partyAt(lines, h.at) != GocParty.DOCTOR_SIDE }
            .sortedBy { it.at }
        fun who(h: GocHit) = lines?.let { GocLines.lineAt(it, h.at)?.who }.orEmpty()
        val current = mutableListOf<GocHit>(); val superseded = mutableListOf<Pair<GocHit, GocHit>>()
        // Words about what matters add up; they never replace each other.
        if (d == GocDecision.VALUES) return GocStanding(own, emptyList(), additive = true)
        own.forEachIndexed { index, earlier ->
            val by = own.drop(index + 1).lastOrNull { later -> later.at > earlier.at && who(later) == who(earlier) && (
                (later.options.isNotEmpty() && earlier.options.isNotEmpty() && later.options.toSet() != earlier.options.toSet()) ||
                    later.options.any { it in earlier.ruledOut } || later.ruledOut.any { it in earlier.options }) }
            if (by != null) superseded += earlier to by else current += earlier
        }
        return GocStanding(current, superseded)
    }
}

/** "Confirm all suggested" (rule V1): only options the talk stated, one clear option per decision; never a default, nothing undiscussed. */
object GocSuggest {
    /** The one option to set, or null when nothing was said, different people disagree, or the words fit several options. */
    fun single(standing: GocStanding): String? = if (standing.conflict) null else standing.suggested.keys.singleOrNull()
    /** Everything Confirm all applies, in the form's order: decision, option id, the words. Words about what matters add up, so each is listed. */
    fun all(extraction: GocExtraction, lines: List<GocLine>?): List<Triple<GocDecision, String, List<String>>> = GocPlan.ORDER.flatMap { d ->
        val s = GocLatest.standing(extraction, d, lines)
        if (d == GocDecision.VALUES) s.suggested.map { (id, hits) -> Triple(d, id, hits.map { it.quote }) }
        else single(s)?.let { id -> listOf(Triple(d, id, s.suggested[id].orEmpty().map { it.quote })) }.orEmpty()
    }
}
