package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Voice as the main way to fill a text box (owner's point 9) and the older-phone rule (point 4): the microphone shows only
 * where this phone can turn speech into words on the phone itself (Android 12 or later with on-device recognition). Older
 * phones never see a microphone that would fail. Pure rules live here so they are unit-tested.
 */
internal object CareVoiceRules {
    /** On-device recognition arrived in Android 12 (API 31). */
    const val MIN_SDK = 31
    /** Feeding our own recording to the recogniser (so the voice is kept and written down at once) needs Android 13 (API 33). */
    const val SHARED_AUDIO_SDK = 33

    fun micShown(sdk: Int, onDeviceAvailable: Boolean, wanted: Boolean = true): Boolean = wanted && sdk >= MIN_SDK && onDeviceAvailable
    fun writesWordsWhileRecording(sdk: Int, onDeviceAvailable: Boolean): Boolean = sdk >= SHARED_AUDIO_SDK && onDeviceAvailable

    private val sentenceEnd = setOf('.', '!', '?', '।', '\n', '؟')

    /** Adds spoken [words] after what is already typed: one space between, a capital letter only to start a sentence, never past [max]. */
    fun append(existing: String, words: String, max: Int = 4000): String {
        val spoken = words.trim().replace(Regex("\\s+"), " ")
        if (spoken.isEmpty()) return existing
        val base = existing.trimEnd(' ', '\t')
        val startsSentence = base.isEmpty() || base.last() in sentenceEnd
        val piece = if (startsSentence) spoken.replaceFirstChar { it.titlecase() } else spoken
        val joined = when { base.isEmpty() -> piece; base.last() == '\n' -> base + piece; else -> "$base $piece" }
        return joined.take(max)
    }

    /** The recogniser language for the language on screen. */
    fun languageTag(lang: CareLang): String = lang.speechTag

    /** A recording time as m:ss. */
    fun clock(ms: Long): String { val s = (ms.coerceAtLeast(0) / 1000); return "${s / 60}:${(s % 60).toString().padStart(2, '0')}" }

    /** Peak sample below this for a whole recording means the phone gave us silence (another app held the microphone). */
    const val SILENT_PEAK = 200
    fun silent(peak: Int): Boolean = peak < SILENT_PEAK

    /** Folder and file names built from ids: letters, digits, dash and underscore only. */
    fun safeName(value: String): String = value.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(80).ifBlank { "x" }

    /** A 44-byte RIFF/WAVE header for 16-bit PCM. */
    fun wavHeader(dataBytes: Long, sampleRate: Int = 16_000, channels: Int = 1, bits: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        val data = dataBytes.coerceIn(0, Int.MAX_VALUE.toLong() - 36).toInt()
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII)); b.putInt(36 + data); b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII)); b.putInt(16); b.putShort(1); b.putShort(channels.toShort()); b.putInt(sampleRate); b.putInt(byteRate)
        b.putShort(blockAlign.toShort()); b.putShort(bits.toShort())
        b.put("data".toByteArray(Charsets.US_ASCII)); b.putInt(data)
        return b.array()
    }
}

/** Whether this phone can turn speech into words on the phone itself. Checked once per process. */
internal object CareVoiceSupport {
    @Volatile private var cached: Boolean? = null
    fun onDevice(context: Context): Boolean = cached ?: (Build.VERSION.SDK_INT >= 31 &&
        runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context.applicationContext) }.getOrDefault(false)).also { cached = it }
    fun micShown(context: Context): Boolean = CareVoiceRules.micShown(Build.VERSION.SDK_INT, onDevice(context))
    fun writesWordsWhileRecording(context: Context): Boolean = CareVoiceRules.writesWordsWhileRecording(Build.VERSION.SDK_INT, onDevice(context))
}

private fun voiceTr(en: String, hi: String) = careTr(CareLanguageState.shown, en, hi)

/** One spoken turn for one text box: listens once on the phone itself and hands back the words. */
@Stable internal class CareFieldMic(private val context: Context, private val words: (String) -> Unit) {
    var listening by mutableStateOf(false); private set
    var partial by mutableStateOf(""); private set
    var status by mutableStateOf(""); private set
    var level by mutableFloatStateOf(0f); private set
    private var recognizer: SpeechRecognizer? = null
    private var turn = 0

    fun start(languageTag: String) {
        if (Build.VERSION.SDK_INT < 31 || listening) return
        val mine = ++turn
        release()
        partial = ""; level = 0f; status = ""
        try {
            val next = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = next
            next.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (mine == turn) status = voiceTr("Listening… speak now", "सुन रहे हैं… अब बोलिए") }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) { if (mine == turn) level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (mine == turn) level = 0f }
                override fun onError(error: Int) {
                    if (mine != turn) return
                    listening = false; level = 0f; partial = ""
                    status = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> voiceTr("No words heard. Tap the microphone and try again.", "कोई शब्द सुनाई नहीं दिए। माइक दबाकर फिर बोलिए।")
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> voiceTr("This language isn't ready for speaking on this phone yet.", "इस फ़ोन पर इस भाषा में बोलकर लिखना अभी तैयार नहीं है।")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> voiceTr("Allow the microphone to speak instead of typing.", "टाइप करने के बजाय बोलने के लिए माइक की अनुमति दें।")
                        else -> voiceTr("Couldn't hear that. Tap the microphone and try again.", "सुन नहीं पाए। माइक दबाकर फिर बोलिए।")
                    }
                    release()
                }
                override fun onResults(results: Bundle?) {
                    if (mine != turn) return
                    listening = false; level = 0f; partial = ""
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (heard.isBlank()) status = voiceTr("No words heard. Tap the microphone and try again.", "कोई शब्द सुनाई नहीं दिए। माइक दबाकर फिर बोलिए।")
                    else { status = ""; words(heard) }
                    release()
                }
                override fun onPartialResults(partialResults: Bundle?) { if (mine == turn) partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty() }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            listening = true
            next.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            })
        } catch (_: Exception) {
            listening = false; release()
            status = voiceTr("Couldn't start the microphone. Try again.", "माइक शुरू नहीं हो सका। फिर कोशिश करें।")
        }
    }

    /** Ends the turn and keeps what was heard so far. */
    fun finish() { if (listening) runCatching { recognizer?.stopListening() }.onFailure { cancel() } }
    fun cancel() { turn++; listening = false; partial = ""; level = 0f; release() }
    fun clearStatus() { status = "" }
    private fun release() { runCatching { recognizer?.cancel(); recognizer?.destroy() }; recognizer = null }
}

/**
 * A microphone for a text box, or null when this phone cannot write speech down on the phone itself (Android below 12,
 * no on-device recognition) or when no screen can ask for the microphone permission. [onWords] receives each spoken turn.
 */
@Composable internal fun rememberCareFieldMic(onWords: (String) -> Unit): CareFieldMic? {
    val context = LocalContext.current
    val shown = remember(context) { CareVoiceSupport.micShown(context) }
    if (!shown || LocalActivityResultRegistryOwner.current == null) return null
    val latest by rememberUpdatedState(onWords)
    val mic = remember(context) { CareFieldMic(context) { latest(it) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(mic, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) mic.cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); mic.cancel() }
    }
    return mic
}

/** The round microphone at the end of a text box. Tap to speak, tap again to finish; a soft ring breathes with the voice. */
@Composable internal fun CareMicButton(mic: CareFieldMic, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = careText()
    val permission = rememberSaharaPermission(SaharaPermission.Microphone)
    val tag = CareVoiceRules.languageTag(careLanguage())
    val haptic = rememberSaharaHaptic()
    val ring by animateFloatAsState(if (mic.listening) 0.55f + mic.level * 0.45f else 0f, SaharaMotion.tween(SaharaMotion.VALUE_MS), label = "mic-level")
    val pulse = if (mic.listening && SaharaMotion.enabled) careBreath() else 1f
    IconButton(onClick = {
        haptic()
        if (mic.listening) mic.finish() else permission.request { mic.start(tag) }
    }, enabled = enabled, modifier = modifier.size(48.dp).testTag("care-field-mic")) {
        Box(contentAlignment = Alignment.Center) {
            if (mic.listening) Box(Modifier.size(40.dp).graphicsLayer { val s = pulse * (0.8f + ring * 0.2f); scaleX = s; scaleY = s; alpha = 0.25f + ring * 0.35f }.clip(CircleShape).background(SaharaTone.Rose.wash))
            Icon(if (mic.listening) Icons.Default.Stop else Icons.Default.Mic,
                if (mic.listening) t("Stop listening", "सुनना रोकें") else t("Speak instead of typing", "टाइप करने के बजाय बोलिए"),
                Modifier.size(24.dp), tint = if (mic.listening) SaharaTone.Rose.ink else RustText)
        }
    }
}

/** A slow swell between 1 and 1.12 while listening; only composed when motion is on. */
@Composable internal fun careBreath(): Float {
    val breathe = rememberInfiniteTransition(label = "voice-breathe")
    val pulse by breathe.animateFloat(1f, 1.12f, infiniteRepeatable(SaharaMotion.tween(SaharaMotion.BREATHE_MS / 2), RepeatMode.Reverse), label = "voice-pulse")
    return pulse
}
