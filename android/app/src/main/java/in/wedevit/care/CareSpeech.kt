package `in`.wedevit.care

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

data class CareSpeechLanguage(val tag: String, val label: String)
val careSpeechLanguages = listOf(
    CareSpeechLanguage("hi-IN", "हिन्दी"), CareSpeechLanguage("en-IN", "English"),
    CareSpeechLanguage("kn-IN", "ಕನ್ನಡ"), CareSpeechLanguage("te-IN", "తెలుగు"),
    CareSpeechLanguage("ta-IN", "தமிழ்"), CareSpeechLanguage("ml-IN", "മലയാളം"),
    CareSpeechLanguage("ur-IN", "اردو"), CareSpeechLanguage("hi-en", "हिन्दी + English")
)

private class CareDictation(private val context: Context, val changed: () -> Unit, val preview: (String) -> Unit, val result: (String) -> Boolean) {
    private var recognizer: SpeechRecognizer? = null
    private var disposed = false
    private var accepting = false
    private var generation = 0
    var listening = false; private set
    var processing = false; private set
    var partial = ""; private set
    var level = 0f; private set
    var status = ""; private set
    var available = Build.VERSION.SDK_INT >= 31 && runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        private set

    private fun accepts(turn: Int) = turn == generation && accepting && !disposed
    private fun recognizerFor(turn: Int): SpeechRecognizer {
        if (Build.VERSION.SDK_INT < 31) throw IllegalStateException("On-device dictation requires Android 12 or later.")
        check(available)
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { next ->
                next.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) { if (accepts(turn)) { status = speechTr("Listening…", "सुन रहे हैं…"); changed() } }
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) { if (accepts(turn)) { level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f); changed() } }
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { if (accepts(turn)) { listening = false; processing = true; level = 0f; status = speechTr("Checking the words…", "शब्द जाँच रहे हैं…"); changed() } }
                    override fun onError(error: Int) {
                        if (!accepts(turn)) return
                        accepting = false; listening = false; processing = false; partial = ""; level = 0f
                        status = when (error) {
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> speechTr("This language isn't installed for dictation. Choose another language or type the words.", "इस भाषा के लिए बोलकर लिखना इंस्टॉल नहीं है। दूसरी भाषा चुनें या शब्द लिखें।")
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> speechTr("Allow microphone access to dictate, or type the words.", "बोलकर लिखने के लिए माइक की अनुमति दें, या शब्द लिखें।")
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> speechTr("No words were recognised. Tap Dictate to try again.", "कोई शब्द पहचाने नहीं गए। फिर कोशिश के लिए Dictate दबाएँ।")
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> speechTr("The microphone is busy. Stop other recording and try again.", "माइक व्यस्त है। दूसरी रिकॉर्डिंग रोककर फिर कोशिश करें।")
                            else -> speechTr("Dictation couldn't finish. Your earlier words are safe. Try again or type.", "बोलकर लिखना पूरा नहीं हो सका। पहले के शब्द सुरक्षित हैं। फिर कोशिश करें या लिखें।")
                        }
                        changed()
                    }
                    override fun onResults(results: Bundle?) {
                        if (!accepts(turn)) return
                        accepting = false; listening = false; processing = false; level = 0f
                        val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        partial = ""; status = when {
                            words.isBlank() -> speechTr("No words were recognised. Try again or type.", "कोई शब्द पहचाने नहीं गए। फिर कोशिश करें या लिखें।")
                            result(words) -> speechTr("Words added. Check them below.", "शब्द जुड़ गए। नीचे जाँचें।")
                            else -> speechTr("These words need attention before they can be added.", "इन शब्दों को जोड़ने से पहले ध्यान चाहिए।")
                        }
                        changed()
                    }
                    override fun onPartialResults(partialResults: Bundle?) { if (accepts(turn)) { partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); if (partial.isNotBlank()) preview(partial); changed() } }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
        }
    }

    fun start(language: String) {
        if (disposed || accepting) return
        if (!available) return
        if (language == "hi-en" && Build.VERSION.SDK_INT < 34) { status = speechTr("For Hindi + English together, transcribe a saved recording below. This phone's dictation supports one language per turn.", "हिन्दी + English साथ में लिखने के लिए नीचे सहेजी रिकॉर्डिंग से लिखाई बनाएँ। इस फ़ोन पर हर बारी एक ही भाषा चलती है।"); changed(); return }
        val turn = ++generation
        accepting = true; listening = true; processing = false; partial = ""; level = 0f; status = speechTr("Starting microphone…", "माइक शुरू हो रहा है…"); changed()
        try {
            runCatching { recognizer?.destroy() }
            val next = recognizerFor(turn).also { recognizer = it }
            next.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == "hi-en") "hi-IN" else language)
                if (language == "hi-en" && Build.VERSION.SDK_INT >= 34) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, arrayListOf("hi-IN", "en-IN"))
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("hi-IN", "en-IN"))
                }
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (_: Exception) { accepting = false; listening = false; processing = false; status = speechTr("Couldn't start dictation. Check microphone access or type the words.", "बोलकर लिखना शुरू नहीं हो सका। माइक की अनुमति जाँचें या शब्द लिखें।"); changed() }
    }

    fun stop() { if (accepting && listening) { listening = false; processing = true; status = speechTr("Finishing this turn…", "यह बारी पूरी हो रही है…"); runCatching { recognizer?.stopListening() }.onFailure { cancel() }; changed() } }
    fun cancel(keepWords: Boolean = true) { generation++; accepting = false; runCatching { recognizer?.cancel(); recognizer?.destroy() }; recognizer = null; listening = false; processing = false; status = if (keepWords && partial.isNotBlank()) speechTr("Listening stopped. Check the unfinished words.", "सुनना रुक गया। अधूरे शब्द जाँचें।") else speechTr("Listening stopped", "सुनना रुक गया"); partial = ""; level = 0f; changed() }
    fun close() { disposed = true; generation++; accepting = false; runCatching { recognizer?.cancel(); recognizer?.destroy() }; recognizer = null }
}

@Composable fun CareDictationPanel(
    personId: String,
    recording: Boolean,
    enabled: Boolean,
    onWords: (String, String, String) -> Boolean,
    onConsent: () -> Unit,
    onActiveChanged: (Boolean) -> Unit,
) { val t = careText();
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var languageTag by rememberSaveable(personId) { mutableStateOf("hi-IN") }
    var speaker by rememberSaveable(personId) { mutableStateOf("Patient") }
    var consent by rememberSaveable(personId) { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var pendingSpeaker by rememberSaveable(personId) { mutableStateOf(speaker) }
    var pendingLanguageTag by rememberSaveable(personId) { mutableStateOf(languageTag) }
    val pendingLanguage = careSpeechLanguages.first { it.tag == pendingLanguageTag }
    var permissionError by remember { mutableStateOf("") }
    var heldWords by rememberSaveable(personId) { mutableStateOf("") }
    val latestWords by rememberUpdatedState(onWords)
    val latestConsent by rememberUpdatedState(onConsent)
    val dictation = remember(personId) { CareDictation(context, { revision++ }, { words -> heldWords = words }) { words ->
        latestWords(words, pendingSpeaker, careSpeechLanguages.first { it.tag == pendingLanguageTag }.label).also { added -> heldWords = if (added) "" else words }
    } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && consent && enabled && !recording) { latestConsent(); dictation.start(pendingLanguage.tag) }
        else if (!granted) permissionError = t("Microphone access wasn't granted. You can type the conversation.", "माइक की अनुमति नहीं मिली। आप बातचीत लिख सकते हैं।")
    }
    DisposableEffect(dictation, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) dictation.cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); dictation.close() }
    }
    // Read the callback revision so partial results and microphone amplitude redraw this panel.
    @Suppress("UNUSED_VARIABLE") val callbackRevision = revision
    val active = dictation.listening || dictation.processing
    val latestActiveChanged by rememberUpdatedState(onActiveChanged)
    LaunchedEffect(active) { latestActiveChanged(active) }
    DisposableEffect(Unit) { onDispose { latestActiveChanged(false) } }
    SurfaceCard(Mint) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.RecordVoiceOver, null, tint = Ink); Spacer(Modifier.width(10.dp)); Text(t("Speak to type", "फ़ोन पर बोलकर लिखना"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont) }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Patient", "Family / carer", "Doctor").forEach { role -> FilterChip(selected = speaker == role, onClick = { speaker = role }, enabled = !active, label = { Text(role) }) }
        }
        CareLanguageMenu(languageTag, enabled = !active, includeMixed = true) { languageTag = it }
        if (languageTag == "hi-en") Text(if (Build.VERSION.SDK_INT >= 34) t("Both phone language packs must be installed. If the language does not switch, turn a saved recording into text below.", "फ़ोन में दोनों भाषा पैक होने चाहिए। अगर भाषा नहीं बदलती, तो नीचे सहेजी रिकॉर्डिंग से लिखाई बनाएँ।") else t("This phone listens to one language at a time. For Hindi and English together, use a saved recording below.", "यह फ़ोन हर बार एक ही भाषा सुनता है। हिन्दी और अंग्रेज़ी साथ में नीचे सहेजी रिकॉर्डिंग के लिए उपलब्ध है।"), style = MaterialTheme.typography.bodySmall)
        if (dictation.available) {
            if (dictation.processing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Rust)
            else if (dictation.listening) LinearProgressIndicator(progress = { dictation.level }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Rust)
            if (dictation.partial.isNotBlank()) Text(dictation.partial, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (dictation.listening) dictation.stop() else {
                        pendingSpeaker = speaker; pendingLanguageTag = languageTag
                        permissionError = ""; consent = false; showConsent = true
                    }
                }, enabled = enabled && !recording && !dictation.processing && (heldWords.isBlank() || dictation.listening), modifier = Modifier.testTag("dictation-toggle")) {
                    Icon(if (dictation.listening) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (dictation.listening) t("Finish turn", "बोलना पूरा करें") else "Dictate")
                }
                if (active) TextButton(onClick = { dictation.cancel(keepWords = false); heldWords = "" }) { Text(t("Cancel turn", "यह बारी रद्द करें")) }
            }
            if (dictation.status.isNotBlank()) Text(dictation.status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        } else Text(if (Build.VERSION.SDK_INT < 31) t("Speaking to type needs Android 12 or newer. Type the words or keep an audio recording.", "फ़ोन पर बोलकर लिखने के लिए Android 12 या नया चाहिए। शब्द लिखें या ऑडियो रिकॉर्डिंग रखें।") else t("Speaking to type does not work on this phone. Type the words or keep an audio recording.", "इस फ़ोन पर बोलकर लिखना उपलब्ध नहीं है। शब्द लिखें या ऑडियो रिकॉर्डिंग रखें।"), style = MaterialTheme.typography.bodySmall)
        if (permissionError.isNotBlank()) Text(permissionError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.announce())
        if (heldWords.isNotBlank() && !active) {
            Text(t("Unfinished words · $pendingSpeaker · ${pendingLanguage.label}", "अधूरे शब्द · $pendingSpeaker · ${pendingLanguage.label}"), style = MaterialTheme.typography.labelMedium)
            CareField(heldWords, { heldWords = it }, t("Words to keep", "रखने वाले शब्द"), minLines = 2)
            TextButton(onClick = { if (latestWords(heldWords, pendingSpeaker, pendingLanguage.label)) heldWords = "" }) { Text(t("Add these words", "ये शब्द जोड़ें")) }
        }
        if (recording) Text(t("Stop the audio recording before dictating.", "बोलकर लिखने से पहले ऑडियो रिकॉर्डिंग रोकें।"), style = MaterialTheme.typography.bodySmall)
    }
    if (showConsent) AlertDialog(onDismissRequest = { showConsent = false }, title = { Text(t("Before we listen", "सुनने से पहले")) },
        text = { Column { Text(t("Speak one person's turn, then check the words before using them in the care note.", "एक व्यक्ति की बात बोलें, फिर केयर नोट में डालने से पहले शब्द जाँचें।")); Spacer(Modifier.height(12.dp)); CheckLine(consent, { consent = it }, t("Everyone here agrees to the microphone being used for this turn.", "यहाँ मौजूद सभी इस बार माइक इस्तेमाल करने के लिए राज़ी हैं।")) } },
        confirmButton = { TextButton(onClick = {
            showConsent = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { latestConsent(); dictation.start(pendingLanguage.tag) }
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        }, enabled = consent && enabled && !recording) { Text(t("Start listening", "सुनना शुरू करें")) } },
        dismissButton = { TextButton(onClick = { showConsent = false }) { Text(t("Cancel", "रद्द करें")) } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable fun CareLanguageMenu(tag: String, enabled: Boolean = true, includeMixed: Boolean = false, onSelect: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        careSpeechLanguages.filter { includeMixed || it.tag != "hi-en" }.forEach { language ->
            FilterChip(selected = language.tag == tag, onClick = { onSelect(language.tag) }, enabled = enabled,
                label = { Text(language.label) }, modifier = Modifier.testTag("care-language-${language.tag}"))
        }
    }
}

/** Status text for classes that are not composables; follows the header language. */
private fun speechTr(en: String, hi: String) = careTr(CareLanguageState.shown, en, hi)

internal class CareReadAloud(context: Context, val changed: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val translation by lazy { CareOfflineTranslation() }
    private var job: Job? = null
    private var engine: TextToSpeech? = null
    private var closed = false
    private var ready = false
    private var generation = 0
    private var lastUtterance = ""
    var speaking = false; private set
    /** True while a language pack downloads or the note is being translated, before any speech starts. */
    var working = false; private set
    val busy: Boolean get() = speaking || working
    var status = speechTr("Preparing voice…", "आवाज़ तैयार हो रही है…"); private set
    init {
        engine = TextToSpeech(context.applicationContext) { result -> handler.post {
            if (!closed) { ready = result == TextToSpeech.SUCCESS; status = if (ready) "" else speechTr("Read aloud isn't available on this phone.", "इस फ़ोन पर पढ़कर सुनाना उपलब्ध नहीं है।"); changed() }
        } }.also { tts ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { handler.post { if (!closed && utteranceId == lastUtterance) { speaking = false; status = speechTr("Finished reading", "पढ़ना पूरा हुआ"); changed() } } }
                @Deprecated("Required by Android TTS")
                override fun onError(utteranceId: String?) { fail(utteranceId) }
                override fun onError(utteranceId: String?, errorCode: Int) { fail(utteranceId) }
            })
        }
    }
    private fun fail(id: String?) { handler.post { if (!closed && id?.startsWith("care-$generation-") == true) { engine?.stop(); speaking = false; status = speechTr("Couldn't read this note. Try another installed language voice.", "यह नोट पढ़ा नहीं जा सका। किसी दूसरी इंस्टॉल की हुई भाषा की आवाज़ आज़माएँ।"); changed() } } }

    // Each string is a full literal so the dictionary lookup in careTr can match it.
    private fun packDownloadStatus(code: String) = when (code) {
        "hi" -> speechTr("Downloading the Hindi language pack (about 30 MB)…", "हिन्दी भाषा पैक डाउनलोड हो रहा है (लगभग 30 MB)…")
        "te" -> speechTr("Downloading the Telugu language pack (about 30 MB)…", "तेलुगु भाषा पैक डाउनलोड हो रहा है (लगभग 30 MB)…")
        "kn" -> speechTr("Downloading the Kannada language pack (about 30 MB)…", "कन्नड़ भाषा पैक डाउनलोड हो रहा है (लगभग 30 MB)…")
        "ta" -> speechTr("Downloading the Tamil language pack (about 30 MB)…", "तमिल भाषा पैक डाउनलोड हो रहा है (लगभग 30 MB)…")
        "ur" -> speechTr("Downloading the Urdu language pack (about 30 MB)…", "उर्दू भाषा पैक डाउनलोड हो रहा है (लगभग 30 MB)…")
        else -> speechTr("Downloading a language pack (about 30 MB)…", "भाषा पैक डाउनलोड हो रहा है (लगभग 30 MB)…")
    }

    fun read(text: String, languageTag: String) {
        val tts = engine ?: return
        if (closed) return
        if (!ready) { status = speechTr("The voice is still preparing. Try Read aloud again in a moment.", "आवाज़ अभी तैयार हो रही है। थोड़ी देर बाद फिर पढ़कर सुनाएँ दबाएँ।"); changed(); return }
        stop()
        // Hindi + English reads the original words with the Hindi voice; nothing is translated.
        val voiceTag = if (languageTag == "hi-en") "hi-IN" else languageTag
        val language = Locale.forLanguageTag(voiceTag)
        val voices = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val voice = voices.filter { !it.isNetworkConnectionRequired && it.locale.language == language.language && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() }
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.locale.country == language.country }.thenByDescending { it.quality }).firstOrNull()
        if (voice == null) { status = speechTr("No downloaded voice for this language. Choose another language or install its voice in phone settings.", "इस भाषा की डाउनलोड की हुई आवाज़ नहीं है। दूसरी भाषा चुनें या फ़ोन की सेटिंग में उसकी आवाज़ इंस्टॉल करें।"); changed(); return }
        val label = careSpeechLanguages.firstOrNull { it.tag == languageTag }?.label ?: language.displayLanguage
        val lines = text.lines().filter(String::isNotBlank)
        if (lines.isEmpty()) return
        val base = language.language
        val needsTranslation = languageTag != "hi-en" && lines.any { line -> CareOfflineTranslation.scriptLanguage(line).let { it != null && it != base } }
        if (!needsTranslation) { speak(tts, voice, text, label, ""); return }
        val target = CareOfflineTranslation.mlKitCode(base)
        if (target == null) {
            speak(tts, voice, text, label, speechTr("Malayalam translation isn't available on this phone; reading the original words.", "इस फ़ोन पर मलयालम अनुवाद उपलब्ध नहीं है; मूल शब्द पढ़ रहे हैं।"))
            return
        }
        val turn = generation
        working = true; status = speechTr("Translating…", "अनुवाद हो रहा है…"); changed()
        job = scope.launch {
            try {
                val needed = (lines.mapNotNull { CareOfflineTranslation.scriptLanguage(it)?.let(CareOfflineTranslation::mlKitCode) } + target).toSet()
                // No Wi-Fi-only preference exists for read aloud, so say plainly that mobile data may be used.
                for (pack in needed - translation.installedLanguages()) {
                    status = packDownloadStatus(pack) + " " + speechTr("Uses any network, including mobile data.", "किसी भी नेटवर्क, यहाँ तक कि मोबाइल डेटा, का उपयोग होता है।"); changed()
                    translation.downloadPack(pack, wifiOnly = false)
                }
                status = speechTr("Translating…", "अनुवाद हो रहा है…"); changed()
                val result = translation.translateForSpeech(lines, base) { _, _ -> }
                if (closed || turn != generation) return@launch
                working = false
                val note = if (result.keptOriginal > 0) speechTr("Some lines could not be checked, so they are read in the original words.", "कुछ पंक्तियाँ जाँची नहीं जा सकीं, इसलिए उन्हें मूल शब्दों में पढ़ रहे हैं।") else ""
                speak(tts, voice, result.lines.joinToString("\n"), label, note)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (closed || turn != generation) return@launch
                working = false
                status = speechTr("Couldn't translate this note on this phone. Check the internet connection for the language pack and try again.", "इस फ़ोन पर यह नोट अनुवाद नहीं हो सका। भाषा पैक के लिए इंटरनेट जाँचें और फिर कोशिश करें।"); changed()
            }
        }
    }

    private fun speak(tts: TextToSpeech, voice: android.speech.tts.Voice, text: String, label: String, note: String) {
        if (tts.setVoice(voice) != TextToSpeech.SUCCESS) { working = false; status = speechTr("No downloaded voice for this language. Choose another language or install its voice in phone settings.", "इस भाषा की डाउनलोड की हुई आवाज़ नहीं है। दूसरी भाषा चुनें या फ़ोन की सेटिंग में उसकी आवाज़ इंस्टॉल करें।"); changed(); return }
        val chunks = CareDraftAssist.speechChunks(text, TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(1))
        if (chunks.isEmpty()) { working = false; changed(); return }
        generation++; lastUtterance = "care-$generation-${chunks.lastIndex}"
        speaking = true
        status = if (note.isBlank()) "$label · ${speechTr("Reading aloud", "पढ़कर सुना रहे हैं")}" else note
        changed()
        chunks.forEachIndexed { index, chunk ->
            if (tts.speak(chunk, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "care-$generation-$index") != TextToSpeech.SUCCESS) {
                tts.stop(); speaking = false; status = speechTr("Couldn't start reading. Try again.", "पढ़ना शुरू नहीं हो सका। फिर कोशिश करें।"); changed(); return
            }
        }
    }
    fun stop() { generation++; lastUtterance = ""; job?.cancel(); job = null; working = false; engine?.stop(); speaking = false; status = ""; changed() }
    fun close() { closed = true; scope.cancel(); engine?.stop(); engine?.shutdown(); engine = null }
}

@Composable fun CareReadAloudControl(text: String, identity: String, initialLanguage: String = "en-IN") { val t = careText();
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var language by rememberSaveable(identity) { mutableStateOf(initialLanguage) }
    val reader = remember(identity) { CareReadAloud(context) { revision++ } }
    DisposableEffect(reader, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) reader.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); reader.close() }
    }
    LaunchedEffect(text, language) { reader.stop() }
    @Suppress("UNUSED_VARIABLE") val callbackRevision = revision
    SurfaceCard(Mint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { if (reader.busy) reader.stop() else reader.read(text, language) }, modifier = Modifier.testTag("note-read-aloud"), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Sage, contentColor = Ink)) {
                Icon(if (reader.busy) Icons.Default.Stop else Icons.Default.VolumeUp, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text(if (reader.busy) t("Stop reading", "पढ़ना रोकें") else t("Read aloud", "पढ़कर सुनाएँ"))
            }
        }
        CareLanguageMenu(language) { language = it }
        if (reader.status.isNotBlank()) Text(reader.status, style = MaterialTheme.typography.bodySmall)
        Text(t("The words are translated on this phone for listening only. The saved note is not changed. Check anything important with the care team.", "शब्द सिर्फ़ सुनने के लिए इसी फ़ोन पर अनुवाद होते हैं। सहेजा हुआ नोट नहीं बदलता। कोई भी ज़रूरी बात केयर टीम से जाँच लें।"), style = MaterialTheme.typography.bodySmall)
    }
}
