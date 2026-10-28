package `in`.wedevit.care

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * One English/Hindi choice for the family-side screens, kept per account in the preference the symptom
 * tracker already used, so a choice made there carries over. Screens read it as `val t = careText()` and
 * write every visible string as an inline pair: `t("English", "हिन्दी")`.
 */
class CareLanguageChoice internal constructor(private val preferences: android.content.SharedPreferences, private val device: android.content.SharedPreferences? = null) {
    // An account without its own choice starts from the phone's last choice, so picking Hindi before signing in carries over.
    var hindi by mutableStateOf(preferences.getBoolean(KEY, device?.getBoolean(KEY, false) ?: false)); private set
    fun choose(value: Boolean) { hindi = value; preferences.edit().putBoolean(KEY, value).apply(); device?.edit()?.putBoolean(KEY, value)?.apply() }
    companion object {
        private const val KEY = "hindi"
        private val shared = mutableMapOf<String, CareLanguageChoice>()
        /** The same object for one account everywhere in the app, so switching on one screen switches them all at once. */
        fun of(context: Context, namespace: String): CareLanguageChoice = synchronized(shared) {
            shared.getOrPut(namespace) { CareLanguageChoice(context.applicationContext.getSharedPreferences("sahara-symptom-language-$namespace", 0), context.applicationContext.getSharedPreferences("sahara-language", 0)) }
        }
    }
}

val LocalCareLanguage = staticCompositionLocalOf<CareLanguageChoice?> { null }

@Composable fun rememberCareLanguage(namespace: String): CareLanguageChoice { val context = LocalContext.current; return remember(namespace) { CareLanguageChoice.of(context, namespace) } }

/** True when Hindi is chosen. CareRoot provides the choice to every screen, family and doctor side alike. */
@Composable fun careHindi(): Boolean = LocalCareLanguage.current?.hindi ?: false

/** The inline pair picker used across the app: `t("English", "हिन्दी")`. */
@Composable fun careText(): (String, String) -> String { val hindi = careHindi(); return { en, hi -> if (hindi) hi else en } }

/** English | हिन्दी chips; each label is written in its own script so a person who reads only one of them can find it. */
@Composable fun CareLanguageSwitch(modifier: Modifier = Modifier) {
    val language = LocalCareLanguage.current ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(false to "English", true to "हिन्दी").forEach { (value, label) ->
            FilterChip(language.hindi == value, { language.choose(value) }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-language-${if (value) "hi" else "en"}"))
        }
    }
}

/** Dates on the family screens: Hindi month and day names when Hindi is chosen, otherwise the phone's own format. */
fun careDateFormat(pattern: String, hindi: Boolean): java.time.format.DateTimeFormatter =
    if (hindi) java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale("hi")) else java.time.format.DateTimeFormatter.ofPattern(pattern)

/** Calendar categories are saved in English; only the label is shown in Hindi. */
fun careCategoryLabel(category: String, hindi: Boolean): String = if (!hindi) category else when (category) {
    "All" -> "सभी"; "Medicine" -> "दवा"; "Visit" -> "मुलाक़ात"; "Test" -> "जाँच"; "Follow-up" -> "अगली मुलाक़ात"; "Procedure" -> "प्रक्रिया"; "Task" -> "काम"; "Conversation" -> "बातचीत"; else -> category
}

/** Check-in moods are saved in English (doctor screens and reports read them); only the label is shown in Hindi. */
fun checkInMoodLabel(title: String, hindi: Boolean): String = if (!hindi) title else when (title) {
    CheckInMood.GOOD -> "अच्छा दिन"; CheckInMood.OKAY -> "ठीक-ठाक दिन"; CheckInMood.HARD -> "मुश्किल दिन"; else -> title
}

/** Care-note section headings are saved and hashed in English (NoteRules.labels); only the shown heading is Hindi. */
fun noteSectionLabel(label: String, hindi: Boolean): String = if (!hindi) label else when (label) {
    "What matters" -> "क्या ज़रूरी है"; "Who was there" -> "कौन मौजूद था"; "What we discussed" -> "किस पर बात हुई"; "Still to discuss" -> "अभी बात करनी है"; "Next steps" -> "आगे के कदम"; else -> label
}

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
fun careDeviceHindi(context: Context): Boolean = context.applicationContext.getSharedPreferences("sahara-language", 0).getBoolean("hindi", false)

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
)
