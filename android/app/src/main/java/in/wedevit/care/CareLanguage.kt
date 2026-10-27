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
class CareLanguageChoice internal constructor(private val preferences: android.content.SharedPreferences) {
    var hindi by mutableStateOf(preferences.getBoolean(KEY, false)); private set
    fun choose(value: Boolean) { hindi = value; preferences.edit().putBoolean(KEY, value).apply() }
    companion object {
        private const val KEY = "hindi"
        private val shared = mutableMapOf<String, CareLanguageChoice>()
        /** The same object for one account everywhere in the app, so switching on one screen switches them all at once. */
        fun of(context: Context, namespace: String): CareLanguageChoice = synchronized(shared) {
            shared.getOrPut(namespace) { CareLanguageChoice(context.applicationContext.getSharedPreferences("sahara-symptom-language-$namespace", 0)) }
        }
    }
}

val LocalCareLanguage = staticCompositionLocalOf<CareLanguageChoice?> { null }

@Composable fun rememberCareLanguage(namespace: String): CareLanguageChoice { val context = LocalContext.current; return remember(namespace) { CareLanguageChoice.of(context, namespace) } }

/** True when the family chose Hindi. Screens shown outside the care app (doctor side) stay English. */
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

/** Check-in moods are saved in English (doctor screens and reports read them); only the label is shown in Hindi. */
fun checkInMoodLabel(title: String, hindi: Boolean): String = if (!hindi) title else when (title) {
    CheckInMood.GOOD -> "अच्छा दिन"; CheckInMood.OKAY -> "ठीक-ठाक दिन"; CheckInMood.HARD -> "मुश्किल दिन"; else -> title
}
