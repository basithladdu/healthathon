package `in`.wedevit.care

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * Simple Mode's voice: Android's own text-to-speech in the app's language (en-IN, hi-IN; te-IN, kn-IN and ur-IN when the
 * phone's engine has them). When the phone has no voice for the language, it speaks the English words instead and says
 * nothing about it. Works offline with the phone's installed voices; nothing is downloaded or translated here.
 * Each prompt is built per language ([say] takes a builder), so the words always match the voice.
 */
internal class SimpleSpeaker(context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private var closed = false
    private var pending: ((CareLang) -> String)? = null
    private var count = 0
    /** The app's chosen language; the voice falls back to English when the phone lacks it. */
    var language: CareLang = CareLang.EN

    init {
        engine = TextToSpeech(context.applicationContext) { status -> handler.post {
            if (closed) return@post
            ready = status == TextToSpeech.SUCCESS
            val waiting = pending; pending = null
            if (ready && waiting != null) speakNow(waiting)
        } }
    }

    /** Speaks [text] in the voice's language, replacing anything still being said. Before the engine is ready, the latest prompt waits. */
    fun say(text: (CareLang) -> String) {
        if (closed) return
        if (!ready) { pending = text; return }
        speakNow(text)
    }

    private fun locale(lang: CareLang): Locale = Locale(lang.code, "IN")

    private fun available(tts: TextToSpeech, lang: CareLang): Boolean = runCatching { tts.isLanguageAvailable(locale(lang)) >= TextToSpeech.LANG_AVAILABLE }.getOrDefault(false)

    private fun speakNow(text: (CareLang) -> String) {
        val tts = engine ?: return
        val voice = SimpleModeRules.voiceLanguage(language) { available(tts, it) }
        runCatching { tts.setLanguage(locale(voice)) }
        val words = text(voice).trim()
        if (words.isEmpty()) return
        val limit = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1)
        CareDraftAssist.speechChunks(words, limit).forEachIndexed { index, chunk ->
            tts.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "simple-${count++}")
        }
    }

    fun stop() { pending = null; runCatching { engine?.stop() } }
    fun close() { closed = true; pending = null; runCatching { engine?.stop(); engine?.shutdown() }; engine = null }
}

/** One speaker for the Simple Mode screens; it stops when the app goes to the background and is released when Simple Mode closes. */
@Composable internal fun rememberSimpleSpeaker(language: CareLang): SimpleSpeaker {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val speaker = remember { SimpleSpeaker(context) }
    speaker.language = language
    DisposableEffect(speaker, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) speaker.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); speaker.close() }
    }
    return speaker
}

/**
 * Simple Mode on or off, and its sound muted or not, kept for each person on this phone only (patient and family can
 * switch it for the selected patient in More). [revision] changes on every switch so Today and More update together.
 */
internal object SimpleModeStore {
    val revision = MutableStateFlow(0)
    private fun preferences(context: Context, namespace: String) = context.applicationContext.getSharedPreferences("sahara-simple-mode-${namespace.ifBlank { "guest" }}", 0)
    fun isOn(context: Context, namespace: String, personId: String): Boolean = personId.isNotBlank() && preferences(context, namespace).getBoolean("on:$personId", false)
    fun setOn(context: Context, namespace: String, personId: String, on: Boolean) {
        if (personId.isBlank()) return
        preferences(context, namespace).edit().putBoolean("on:$personId", on).apply(); revision.value++
    }
    fun isMuted(context: Context, namespace: String, personId: String): Boolean = personId.isNotBlank() && preferences(context, namespace).getBoolean("mute:$personId", false)
    fun setMuted(context: Context, namespace: String, personId: String, muted: Boolean) {
        if (personId.isBlank()) return
        preferences(context, namespace).edit().putBoolean("mute:$personId", muted).apply(); revision.value++
    }
}

@Composable internal fun rememberSimpleModeOn(namespace: String, personId: String): Boolean {
    val context = LocalContext.current
    val revision by SimpleModeStore.revision.collectAsStateWithLifecycle()
    return remember(namespace, personId, revision) { SimpleModeStore.isOn(context, namespace, personId) }
}

@Composable internal fun rememberSimpleMuted(namespace: String, personId: String): Boolean {
    val context = LocalContext.current
    val revision by SimpleModeStore.revision.collectAsStateWithLifecycle()
    return remember(namespace, personId, revision) { SimpleModeStore.isMuted(context, namespace, personId) }
}
