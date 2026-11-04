package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The languages Sahara shows. SVK's AMD platform spec (section 7) asks for Telugu, Hindi, Urdu (right to left)
 * and English first; Kannada is added for Karnataka families.
 *
 * English and Hindi stay inline in the screens as `t("English", "हिन्दी")`. Telugu, Urdu and Kannada are looked up
 * by the English text in `src/main/resources/i18n/<code>.tsv` (see [CareDictionary]). Adding a new screen string
 * needs nothing new in the code: write `t("English", "हिन्दी")` as before and add the English line to each
 * dictionary (CareLanguageDictionaryTest lists anything missing).
 *
 * Machine-drafted; clinician back-translation required before pilot.
 */
enum class CareLang(val code: String, val label: String, val englishName: String, val rtl: Boolean) {
    EN("en", "English", "English", false),
    HI("hi", "हिन्दी", "Hindi", false),
    TE("te", "తెలుగు", "Telugu", false),
    UR("ur", "اردو", "Urdu", true),
    KN("kn", "ಕನ್ನಡ", "Kannada", false);

    /** True for languages read from a dictionary rather than written inline. */
    val dictionary: Boolean get() = this == TE || this == UR || this == KN
    val locale: Locale get() = if (this == EN) Locale.ENGLISH else Locale(code, "IN")

    companion object {
        fun of(code: String?): CareLang? = entries.firstOrNull { it.code == code }
    }
}

/** The language shown right now, for text built outside composables (rules, notifications). */
object CareLanguageState {
    var current by mutableStateOf(CareLang.EN)
    private val englishDepth = ThreadLocal<Int>()
    private val forced = ThreadLocal<CareLang?>()
    internal val englishOnly: Boolean get() = (englishDepth.get() ?: 0) > 0
    /** The language for non-composable text: English while saving records, otherwise the forced or chosen one. */
    internal val shown: CareLang get() = if (englishOnly) CareLang.EN else forced.get() ?: current
    internal fun <T> inEnglish(block: () -> T): T { englishDepth.set((englishDepth.get() ?: 0) + 1); try { return block() } finally { englishDepth.set((englishDepth.get() ?: 1) - 1) } }
    internal fun <T> inLanguage(lang: CareLang, block: () -> T): T { val before = forced.get(); forced.set(lang); try { return block() } finally { forced.set(before) } }
}

/** Builds text that is saved into a record (titles, summaries): always English, whatever the screen shows. */
fun <T> careInEnglish(block: () -> T): T = CareLanguageState.inEnglish(block)

/** Builds text shown outside the app's screens (notifications) in the phone's last chosen language. */
fun <T> careWithDeviceLanguage(context: Context, block: () -> T): T = CareLanguageState.inLanguage(careDeviceLanguage(context), block)

/**
 * The shared picker for code that passes `hindi: Boolean` down: Hindi when [hindi]; otherwise English, or the
 * dictionary text when Telugu, Urdu or Kannada is shown. Saving code passes `false` inside [careInEnglish].
 */
fun careTr(hindi: Boolean, en: String, hi: String): String {
    if (hindi) return hi
    val lang = CareLanguageState.shown
    return if (lang.dictionary) CareDictionary.lookup(lang, en) else en
}

/** The same choice for an explicit language. */
fun careTr(lang: CareLang, en: String, hi: String): String = when {
    lang == CareLang.HI -> hi
    lang.dictionary -> CareDictionary.lookup(lang, en)
    else -> en
}

/**
 * A message written in English and Hindi together ("Saved · सहेजा गया"), kept that way for English and Hindi readers.
 * Telugu, Urdu and Kannada readers get just their own language.
 */
fun careBilingual(en: String, hi: String): String {
    val lang = CareLanguageState.shown
    if (!lang.dictionary) return "$en · $hi"
    val found = CareDictionary.lookup(lang, en)
    return if (found == en) "$en · $hi" else found
}

/** Dates and times: the chosen language's names, English otherwise. */
fun careLocale(hindi: Boolean): Locale = if (hindi) CareLang.HI.locale else CareLanguageState.shown.let { if (it.dictionary) it.locale else Locale.ENGLISH }

/**
 * One language choice for the whole app, kept per account (in the preference the symptom tracker first used) and
 * for the phone, so a choice made before signing in carries over. Screens read it as `val t = careText()`.
 */
class CareLanguageChoice internal constructor(private val preferences: android.content.SharedPreferences, private val device: android.content.SharedPreferences? = null) {
    // An account without its own choice starts from the phone's last choice, so picking a language before signing in carries over.
    var lang by mutableStateOf(read(preferences) ?: device?.let(::read) ?: CareLang.EN); private set
    val hindi: Boolean get() = lang == CareLang.HI
    fun choose(value: Boolean) = choose(if (value) CareLang.HI else CareLang.EN)
    fun choose(value: CareLang) {
        lang = value; CareLanguageState.current = value
        listOfNotNull(preferences, device).forEach { it.edit().putString(KEY_LANG, value.code).putBoolean(KEY, value == CareLang.HI).apply() }
    }
    companion object {
        private const val KEY = "hindi"
        private const val KEY_LANG = "language"
        private fun read(p: android.content.SharedPreferences): CareLang? =
            CareLang.of(p.getString(KEY_LANG, null)) ?: if (p.contains(KEY)) (if (p.getBoolean(KEY, false)) CareLang.HI else CareLang.EN) else null
        internal fun deviceLanguage(device: android.content.SharedPreferences): CareLang = read(device) ?: CareLang.EN
        private val shared = mutableMapOf<String, CareLanguageChoice>()
        /** The same object for one account everywhere in the app, so switching on one screen switches them all at once. */
        fun of(context: Context, namespace: String): CareLanguageChoice = synchronized(shared) {
            shared.getOrPut(namespace) { CareLanguageChoice(context.applicationContext.getSharedPreferences("sahara-symptom-language-$namespace", 0), context.applicationContext.getSharedPreferences("sahara-language", 0)) }
        }
    }
}

val LocalCareLanguage = staticCompositionLocalOf<CareLanguageChoice?> { null }

@Composable fun rememberCareLanguage(namespace: String): CareLanguageChoice { val context = LocalContext.current; return remember(namespace) { CareLanguageChoice.of(context, namespace) } }

/** The language on screen. CareRoot provides the choice to every screen, family and doctor side alike. */
@Composable fun careLanguage(): CareLang = LocalCareLanguage.current?.lang ?: CareLanguageState.current

/** True when Hindi is chosen. */
@Composable fun careHindi(): Boolean = careLanguage() == CareLang.HI

/** The inline pair picker used across the app: `t("English", "हिन्दी")`; Telugu, Urdu and Kannada come from the dictionaries. */
@Composable fun careText(): (String, String) -> String { val lang = careLanguage(); return { en, hi -> careTr(lang, en, hi) } }

/** Shows [content] in the chosen language's reading direction: right to left for Urdu. */
@Composable fun CareLanguageDirection(choice: CareLanguageChoice, content: @Composable () -> Unit) {
    val lang = choice.lang
    SideEffect { CareLanguageState.current = lang }
    val direction = if (lang.rtl) LayoutDirection.Rtl else LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}

/**
 * The header language dropdown, on every screen's top bar (sign-in included). Each language is written in its own
 * script so a person who reads only one of them can find it.
 */
@Composable fun CareLanguageMenu(modifier: Modifier = Modifier, contentColor: Color = Color.Unspecified, compact: Boolean = false) {
    val language = LocalCareLanguage.current ?: return
    var open by remember { mutableStateOf(false) }
    val t = careText()
    val color = if (contentColor == Color.Unspecified) LocalContentColor.current else contentColor
    Box(modifier) {
        TextButton(onClick = { open = true }, colors = ButtonDefaults.textButtonColors(contentColor = color), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.heightIn(min = 48.dp).testTag("care-language-menu").semantics { contentDescription = t("Language", "भाषा") + ": " + language.lang.label }) {
            Icon(Icons.Default.Translate, null, Modifier.size(20.dp))
            if (!compact) { Spacer(Modifier.width(4.dp)); Text(language.lang.label, fontSize = 14.sp, maxLines = 1) }
            Icon(Icons.Default.ArrowDropDown, null, Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(Paper).testTag("care-language-options")) {
            CareLang.entries.forEach { option ->
                val chosen = option == language.lang
                DropdownMenuItem(
                    text = { Row(verticalAlignment = Alignment.CenterVertically) { Text(option.label, fontSize = 16.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal, color = if (chosen) RustText else DoctorCardInk); if (option != CareLang.EN) { Spacer(Modifier.width(8.dp)); Text(option.englishName, fontSize = 14.sp, color = DoctorCardSub) } } },
                    onClick = { language.choose(option); open = false },
                    trailingIcon = { if (chosen) Icon(Icons.Default.Check, null, tint = Rust) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("care-language-${option.code}"))
            }
        }
    }
}

/** Kept for older call sites: the same dropdown as the header. */
@Composable fun CareLanguageSwitch(modifier: Modifier = Modifier) = CareLanguageMenu(modifier)

/**
 * Telugu, Urdu and Kannada text, keyed by the English source text exactly as written in the Kotlin code
 * (escapes such as `\n` kept, and `$name` / `${expr}` placeholders kept). Lines are `english<TAB>translation`;
 * lines starting with `#` are comments. A key with placeholders matches any English text of that shape, and the
 * captured values are put into the translation's placeholders of the same name.
 *
 * Machine-drafted; clinician back-translation required before pilot.
 */
object CareDictionary {
    private class Template(val regex: Regex, val names: List<String>, val value: List<Any>) // value parts: String text or Placeholder
    private data class Placeholder(val name: String)
    private class Table(val exact: Map<String, String>, val templates: List<Template>)
    private val tables = ConcurrentHashMap<CareLang, Table>()
    private val cache = ConcurrentHashMap<CareLang, ConcurrentHashMap<String, String>>()

    fun lookup(lang: CareLang, english: String): String {
        if (!lang.dictionary || english.isEmpty()) return english
        val memo = cache.getOrPut(lang) { ConcurrentHashMap() }
        memo[english]?.let { return it }
        val table = tables.getOrPut(lang) { load(lang) }
        val found = table.exact[english] ?: table.templates.firstNotNullOfOrNull { fill(it, english) } ?: english
        if (memo.size < 5000) memo[english] = found
        return found
    }

    private fun fill(template: Template, english: String): String? {
        val match = template.regex.matchEntire(english) ?: return null
        val values = template.names.mapIndexed { i, name -> name to match.groupValues[i + 1] }.distinctBy { it.first }.toMap()
        return template.value.joinToString("") { if (it is Placeholder) values[it.name] ?: "" else it as String }
    }

    /** Raw dictionary lines for [lang] (key to value, both as written in source), for tests and loading. */
    fun raw(lang: CareLang): Map<String, String> {
        val stream = CareDictionary::class.java.getResourceAsStream("/i18n/${lang.code}.tsv") ?: return emptyMap()
        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line -> line.indexOf('\t').takeIf { it > 0 }?.let { line.substring(0, it) to line.substring(it + 1).trimEnd('\r') } }.toMap()
        }
    }

    private fun load(lang: CareLang): Table {
        val exact = HashMap<String, String>(); val templates = mutableListOf<Template>()
        raw(lang).forEach { (key, value) ->
            val keyParts = parse(key); val valueParts = parse(value)
            if (keyParts.none { it is Placeholder }) exact[keyParts.joinToString("") { it as String }] = valueParts.joinToString("") { (it as? String) ?: "\$${(it as Placeholder).name}" }
            else {
                val names = keyParts.filterIsInstance<Placeholder>().map { it.name }
                val pattern = keyParts.joinToString("") { if (it is Placeholder) "(.*?)" else Regex.escape(it as String) }
                templates += Template(Regex(pattern, RegexOption.DOT_MATCHES_ALL), names, valueParts)
            }
        }
        // Longer, more specific shapes first, so "$n of $total done" wins over "$a $b".
        return Table(exact, templates.sortedByDescending { t -> t.value.size + t.regex.pattern.length })
    }

    /** Splits Kotlin string-literal source text into plain text (unescaped) and placeholders. */
    internal fun parse(source: String): List<Any> {
        val parts = mutableListOf<Any>(); val text = StringBuilder(); var i = 0
        fun flush() { if (text.isNotEmpty()) { parts += text.toString(); text.clear() } }
        while (i < source.length) {
            val c = source[i]
            when {
                c == '\\' && i + 1 < source.length -> {
                    val n = source[i + 1]
                    when (n) {
                        'n' -> text.append('\n'); 't' -> text.append('\t'); 'r' -> text.append('\r'); 'b' -> text.append('\b')
                        'u' -> if (i + 5 < source.length) { text.append(source.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                        else -> text.append(n)
                    }
                    i += 2
                }
                c == '$' && i + 1 < source.length && source[i + 1] == '{' -> {
                    var depth = 1; var j = i + 2
                    while (j < source.length && depth > 0) { when (source[j]) { '{' -> depth++; '}' -> depth-- }; if (depth > 0) j++ }
                    flush(); parts += Placeholder(source.substring(i + 2, minOf(j, source.length)).trim()); i = j + 1
                }
                c == '$' && i + 1 < source.length && (source[i + 1].isLetter() || source[i + 1] == '_') -> {
                    var j = i + 1; while (j < source.length && (source[j].isLetterOrDigit() || source[j] == '_')) j++
                    flush(); parts += Placeholder(source.substring(i + 1, j)); i = j
                }
                else -> { text.append(c); i++ }
            }
        }
        flush(); return parts
    }

    /** Placeholder names in a source text, for checks. */
    fun placeholders(source: String): List<String> = parse(source).filterIsInstance<Placeholder>().map { it.name }
}

/** Dates on the family screens: the chosen language's month and day names, otherwise the phone's own format. */
fun careDateFormat(pattern: String, hindi: Boolean): java.time.format.DateTimeFormatter = when {
    hindi -> java.time.format.DateTimeFormatter.ofPattern(pattern, Locale("hi"))
    CareLanguageState.shown.dictionary -> java.time.format.DateTimeFormatter.ofPattern(pattern, CareLanguageState.shown.locale)
    else -> java.time.format.DateTimeFormatter.ofPattern(pattern)
}

/** Calendar categories are saved in English; only the label is translated. */
fun careCategoryLabel(category: String, hindi: Boolean): String = careTr(hindi, category, when (category) {
    "All" -> "सभी"; "Medicine" -> "दवा"; "Visit" -> "मुलाक़ात"; "Test" -> "जाँच"; "Follow-up" -> "अगली मुलाक़ात"; "Procedure" -> "प्रक्रिया"; "Task" -> "काम"; "Conversation" -> "बातचीत"; else -> category
})

/** Check-in moods are saved in English (doctor screens and reports read them); only the label is translated. */
fun checkInMoodLabel(title: String, hindi: Boolean): String = careTr(hindi, title, when (title) {
    CheckInMood.GOOD -> "अच्छा दिन"; CheckInMood.OKAY -> "ठीक-ठाक दिन"; CheckInMood.HARD -> "मुश्किल दिन"; else -> title
})

/** Care-note section headings are saved and hashed in English (NoteRules.labels); only the shown heading is translated. */
fun noteSectionLabel(label: String, hindi: Boolean): String = careTr(hindi, label, when (label) {
    "What matters" -> "क्या ज़रूरी है"; "Who was there" -> "कौन मौजूद था"; "What we discussed" -> "किस पर बात हुई"; "Still to discuss" -> "अभी बात करनी है"; "Next steps" -> "आगे के कदम"; else -> label
})

/** Hindi for reminder checks and alerts (the English stays in CareReminderRules and CareReminders, which tests rely on). */
internal val ReminderTextHindi = mapOf(
    "Use up to six reminder times." to "ज़्यादा से ज़्यादा छह रिमाइंडर समय रखें।",
    "Use HH:mm, such as 08:30." to "HH:mm लिखें, जैसे 08:30।",
    "Use a start date as YYYY-MM-DD." to "शुरू की तारीख़ YYYY-MM-DD में लिखें।",
    "Use an end date as YYYY-MM-DD." to "आख़िरी तारीख़ YYYY-MM-DD में लिखें।",
    "The end date must follow the start date." to "आख़िरी तारीख़ शुरू की तारीख़ के बाद की होनी चाहिए।",
    "Sahara reminder" to "सहारा रिमाइंडर",
    "A saved care activity is due. Open Sahara to check the record." to "सहेजा हुआ देखभाल का काम अब है। रिकॉर्ड देखने के लिए सहारा खोलें।",
    "Care reminders" to "देखभाल रिमाइंडर",
    "Reminders for your chosen medicine times and dated care activities. Android may delay alerts." to "चुने हुए दवा के समय और तारीख़ वाले देखभाल के कामों के रिमाइंडर। Android सूचनाएँ देर से भेज सकता है।",
)

/** The phone's last language choice, for text shown outside the app's screens (notifications). */
fun careDeviceLanguage(context: Context): CareLang = CareLanguageChoice.deviceLanguage(context.applicationContext.getSharedPreferences("sahara-language", 0))
fun careDeviceHindi(context: Context): Boolean = careDeviceLanguage(context) == CareLang.HI

/** Hindi for the symptom diary's save checks (SymptomDiaryRules.error returns the English text, which tests rely on). */
internal val SymptomErrorsHindi = mapOf(
    "Choose a symptom or add a short name." to "लक्षण चुनें या छोटा नाम लिखें।",
    "Choose a number from 0 to 10, or Not rated." to "0 से 10 तक कोई अंक चुनें, या अंक नहीं बताया चुनें।",
    "Choose a valid date and time." to "सही तारीख़ और समय चुनें।",
    "The observation cannot be in the future." to "देखने का समय आगे का नहीं हो सकता।",
    "The start date must be on or before this observation." to "शुरू होने की तारीख़ इस दिन या इससे पहले की होनी चाहिए।",
    "Choose who reported this and the time frame." to "चुनें कि किसने बताया और कितने समय की बात है।",
    "Add who wrote or observed this note." to "लिखें कि यह नोट किसने लिखा या देखा।",
    "Shorten the note or review the pain details." to "नोट छोटा करें या दर्द की जानकारी देखें।",
    "Choose a listed pain area." to "सूची से दर्द की जगह चुनें।",
    "Choose a valid point on a selected pain area." to "चुनी हुई दर्द की जगह पर सही बिंदु चुनें।",
    "Choose a number from 0 to 10 for a selected pain area." to "चुनी हुई दर्द की जगह के लिए 0 से 10 तक अंक चुनें।",
    "This body map version cannot be edited here." to "शरीर के नक्शे का यह संस्करण यहाँ नहीं बदला जा सकता।",
    "Pain details belong to a pain entry." to "दर्द की जानकारी सिर्फ़ दर्द वाले नोट में होती है।",
    "Choose a comparison." to "तुलना चुनें।",
    "Choose the previous entry for this comparison." to "इस तुलना के लिए पिछला नोट चुनें।",
    "This note changed or was removed. Your answers are still here. Close this form and reopen the current note before saving." to "यह नोट बदल गया है या हटा दिया गया है। आपके जवाब यहीं हैं। सहेजने से पहले यह फ़ॉर्म बंद करके मौजूदा नोट फिर खोलें।",
)
