package `in`.wedevit.care

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The patient's own voice, kept as audio beside the written note (owner's point 9), so a nurse or family member can listen
 * to exactly what was said. One WAV file per note, on this phone, next to the strip photos: filesDir/voice-notes/<account>/<person>/<entry>.wav.
 */
internal class CareVoiceNotes(context: Context, storage: String) {
    private val root = File(context.applicationContext.filesDir, "voice-notes/${CareVoiceRules.safeName(storage.ifBlank { "guest" })}")
    fun file(person: String, item: String): File = File(File(root, CareVoiceRules.safeName(person)), CareVoiceRules.safeName(item) + ".wav")
    fun has(person: String, item: String): Boolean = runCatching { file(person, item).let { it.isFile && it.length() > 44 } }.getOrDefault(false)
    fun remove(person: String, item: String) { runCatching { file(person, item).delete() }; changed() }
    /** Deletes recordings for entries that no longer exist (removed notes, or a form closed without saving), once a day old. */
    fun prune(person: String, liveIds: Set<String>, now: Long = System.currentTimeMillis()) {
        val keep = liveIds.map(CareVoiceRules::safeName).toSet()
        runCatching { File(root, CareVoiceRules.safeName(person)).listFiles().orEmpty().forEach { f ->
            if (f.name.removeSuffix(".wav").removeSuffix(".part") !in keep && now - f.lastModified() > CareInviteLog.DAY_MS) f.delete()
        } }
    }
    companion object {
        /** Bumped whenever a recording is saved or removed, so play buttons elsewhere redraw. */
        val revision = mutableIntStateOf(0)
        fun changed() { revision.intValue++ }
        /** Everything recorded under one account, for account deletion. */
        fun forgetAll(context: Context, storage: String) { runCatching { File(context.applicationContext.filesDir, "voice-notes/${CareVoiceRules.safeName(storage.ifBlank { "guest" })}").deleteRecursively() }; changed() }
        const val MAX_MS = 3 * 60_000L
        const val RATE = 16_000
    }
}

private fun noteTr(en: String, hi: String) = careTr(CareLanguageState.shown, en, hi)

/**
 * Records 16 kHz speech into a WAV file. On Android 13+ with on-device recognition the same sound is also fed to the
 * recogniser, so the words are written into the note while the voice itself is kept. Elsewhere only the voice is kept.
 */
internal class CareVoiceNoteSession(
    private val context: Context,
    private val target: File,
    private val languageTag: String,
    writeWords: Boolean,
    private val onWords: (String) -> Unit,
    private val onFinished: (saved: Boolean) -> Unit,
) {
    var recording by mutableStateOf(false); private set
    var elapsedMs by mutableLongStateOf(0L); private set
    var level by mutableFloatStateOf(0f); private set
    var writing by mutableStateOf(writeWords); private set
    var status by mutableStateOf(""); private set
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    @Volatile private var pipeWrite: ParcelFileDescriptor? = null
    private var pipeRead: ParcelFileDescriptor? = null
    private var recognizer: SpeechRecognizer? = null
    private val chunks = ArrayBlockingQueue<ByteArray>(48)
    private var failures = 0
    private var heardWords = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        val min = AudioRecord.getMinBufferSize(CareVoiceNotes.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (min <= 0) { status = noteTr("This phone can't record here.", "यह फ़ोन यहाँ रिकॉर्ड नहीं कर सकता।"); return false }
        val audio = try { AudioRecord(MediaRecorder.AudioSource.MIC, CareVoiceNotes.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, CareVoiceNotes.RATE)) } catch (_: Exception) { null }
        if (audio == null || audio.state != AudioRecord.STATE_INITIALIZED) { runCatching { audio?.release() }; status = noteTr("The microphone is busy. Try again in a moment.", "माइक व्यस्त है। थोड़ी देर बाद फिर कोशिश करें।"); return false }
        val part = File(target.parentFile, target.name + ".part")
        try { target.parentFile?.mkdirs(); part.delete() } catch (_: Exception) {}
        running = true; recording = true; elapsedMs = 0; level = 0f; status = ""; failures = 0; heardWords = false
        try { audio.startRecording() } catch (_: Exception) { running = false; recording = false; audio.release(); status = noteTr("The microphone is busy. Try again in a moment.", "माइक व्यस्त है। थोड़ी देर बाद फिर कोशिश करें।"); return false }
        if (writing && Build.VERSION.SDK_INT >= 33) { startFeeder(); startTurn() } else writing = false
        Thread({ record(audio, part) }, "sahara-voice-note").start()
        return true
    }

    private fun record(audio: AudioRecord, part: File) {
        var saved = false; var peak = 0; var bytes = 0L
        try {
            RandomAccessFile(part, "rw").use { out ->
                out.setLength(0); out.write(CareVoiceRules.wavHeader(0))
                val buffer = ByteArray(3200) // 100 ms
                var lastPost = 0L
                while (running) {
                    val n = audio.read(buffer, 0, buffer.size)
                    if (n <= 0) { if (n < 0) break else continue }
                    out.write(buffer, 0, n); bytes += n
                    var chunkPeak = 0
                    var i = 0
                    while (i + 1 < n) { val s = kotlin.math.abs(((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toInt()); if (s > chunkPeak) chunkPeak = s; i += 2 }
                    if (chunkPeak > peak) peak = chunkPeak
                    if (writing) chunks.offer(buffer.copyOf(n))
                    val ms = bytes * 1000 / (CareVoiceNotes.RATE * 2)
                    if (ms - lastPost >= 100) { lastPost = ms; val lv = (chunkPeak / 12_000f).coerceIn(0f, 1f); main.post { elapsedMs = ms; level = lv } }
                    if (ms >= CareVoiceNotes.MAX_MS) { main.post { stop() }; break }
                }
                out.seek(0); out.write(CareVoiceRules.wavHeader(bytes))
            }
            saved = bytes > CareVoiceNotes.RATE / 2 && !CareVoiceRules.silent(peak) && part.renameTo(target).let { ok -> ok || (target.delete() && part.renameTo(target)) }
        } catch (_: Exception) { saved = false }
        finally { runCatching { audio.stop() }; runCatching { audio.release() }; if (!saved) runCatching { part.delete() } }
        val silent = !saved && bytes > CareVoiceNotes.RATE / 2 && CareVoiceRules.silent(peak)
        main.post {
            recording = false; level = 0f
            status = when {
                saved -> noteTr("Your voice is saved with this note.", "आपकी आवाज़ इस नोट के साथ सहेज ली गई।")
                silent && heardWords -> noteTr("The voice couldn't be kept on this phone. The words are saved.", "इस फ़ोन पर आवाज़ नहीं रखी जा सकी। शब्द सहेज लिए गए।")
                silent -> noteTr("No sound reached the microphone. Try again.", "माइक तक कोई आवाज़ नहीं पहुँची। फिर कोशिश करें।")
                else -> noteTr("Too short to keep. Tap Record and speak.", "रखने के लिए बहुत छोटा है। रिकॉर्ड दबाकर बोलिए।")
            }
            CareVoiceNotes.changed(); onFinished(saved)
        }
    }

    /** Copies recorded sound into the recogniser's pipe on its own thread, so a slow recogniser never stalls the recording. */
    private fun startFeeder() {
        chunks.clear()
        Thread({
            while (running || chunks.isNotEmpty()) {
                val chunk = chunks.poll(200, TimeUnit.MILLISECONDS) ?: continue
                val out = pipeWrite ?: continue
                try { android.system.Os.write(out.fileDescriptor, chunk, 0, chunk.size) } catch (_: Exception) { /* that turn ended; the next pipe takes over */ }
            }
        }, "sahara-voice-words").start()
    }

    @RequiresApi(33)
    private fun openTurn() {
        val pipe = ParcelFileDescriptor.createPipe()
        closePipe()
        pipeRead = pipe[0]; pipeWrite = pipe[1]
        val rec = recognizer ?: SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also { recognizer = it; it.setRecognitionListener(listener) }
        rec.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, CareVoiceNotes.RATE)
        })
    }

    private fun startTurn() {
        if (!running || !writing || Build.VERSION.SDK_INT < 33) return
        try { openTurn() } catch (_: Exception) { stopWriting() }
    }

    private fun stopWriting() {
        writing = false; chunks.clear(); closePipe()
        runCatching { recognizer?.cancel(); recognizer?.destroy() }; recognizer = null
        if (running && !heardWords) status = noteTr("Your voice is being kept. The words can't be written on this phone, so tap the microphone in the box to add them.", "आपकी आवाज़ रखी जा रही है। इस फ़ोन पर शब्द नहीं लिखे जा सकते, इसलिए उन्हें जोड़ने के लिए बॉक्स का माइक दबाएँ।")
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onResults(results: Bundle?) {
            val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (heard.isNotBlank()) { heardWords = true; failures = 0; onWords(heard) }
            if (running) main.post { startTurn() } else finishWords()
        }
        override fun onError(error: Int) {
            val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (!running) { finishWords(); return }
            if (quiet && failures < 20) { failures++; main.post { startTurn() } }
            else if (!quiet && failures < 2 && error != SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED && error != SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { failures++; main.post { startTurn() } }
            else main.post { stopWriting() }
        }
    }

    private fun closePipe() {
        val w = pipeWrite; val r = pipeRead; pipeWrite = null; pipeRead = null
        // Read end first: a write blocked on a full pipe then fails at once instead of waiting.
        runCatching { r?.close() }; runCatching { w?.close() }
    }
    private fun finishWords() { main.removeCallbacks(giveUp); closePipe(); runCatching { recognizer?.destroy() }; recognizer = null }
    private val giveUp = Runnable { finishWords() }

    /** Stops and keeps the recording; the last spoken words still arrive for a moment. */
    fun stop() {
        if (!running) return
        running = false
        // Ending the pipe tells the recogniser the speech is over, so it hands back the last words.
        val w = pipeWrite; pipeWrite = null; runCatching { w?.close() }
        runCatching { recognizer?.stopListening() }
        main.postDelayed(giveUp, 4000)
    }
    fun close() { stop(); main.removeCallbacks(giveUp); finishWords() }
}

/**
 * "Record your voice" for a patient's note: one large button; while recording a breathing red dot, the time and Stop.
 * Afterwards the play button, Record again and Remove. [onWords] receives spoken words to add to the written note.
 * [onActive] is told true just before the microphone opens and false once it closes, so a screen that speaks (Simple
 * Mode) can stay quiet while the patient is recorded. [simple] is Simple Mode's version: 18sp and larger words, 72dp
 * buttons, and no Remove (Record again replaces the voice).
 */
@Composable fun CareVoiceNoteRecorder(storage: String, personId: String, itemId: String, onWords: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, liveIds: Set<String>? = null, micBusy: Boolean = false,
    simple: Boolean = false, onActive: (Boolean) -> Unit = {}, onRecorded: (saved: Boolean) -> Unit = {}) {
    val t = careText(); val context = LocalContext.current
    val notes = remember(storage) { CareVoiceNotes(context, storage) }
    val revision by CareVoiceNotes.revision
    val has = remember(revision, personId, itemId) { notes.has(personId, itemId) }
    val tag = CareVoiceRules.languageTag(careLanguage())
    val latestWords by rememberUpdatedState(onWords)
    val latestRecorded by rememberUpdatedState(onRecorded)
    val latestActive by rememberUpdatedState(onActive)
    var session by remember(personId, itemId) { mutableStateOf<CareVoiceNoteSession?>(null) }
    val permission = rememberSaharaPermission(SaharaPermission.Microphone)
    val haptic = rememberSaharaHaptic()
    var confirmRemove by remember { mutableStateOf(false) }
    LaunchedEffect(liveIds, personId) { liveIds?.let { ids -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { notes.prune(personId, ids + itemId) } } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, personId, itemId) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) session?.stop() }
        owner.lifecycle.addObserver(observer)
        // Closing mid-recording (the recorder hidden, the screen left) also tells [onActive] the microphone is off.
        onDispose { owner.lifecycle.removeObserver(observer); if (session?.recording == true) latestActive(false); session?.close() }
    }
    val active = session?.recording == true
    LaunchedEffect(active) { latestActive(active) }
    fun begin() {
        val next = CareVoiceNoteSession(context, notes.file(personId, itemId), tag, CareVoiceSupport.writesWordsWhileRecording(context), { latestWords(it) }) { latestRecorded(it) }
        session = next
        // Told before the microphone opens, so anything the app is saying stops before it can be recorded.
        latestActive(true)
        if (!next.start()) latestActive(false)
    }
    // Simple Mode's sizes (18sp and up, 72dp targets); the full app keeps its own.
    val small = if (simple) 18.sp else 16.sp
    Surface(shape = RoundedCornerShape(SaharaRadius.Lg), color = if (active) SaharaTone.Rose.wash else Paper, border = BorderStroke(1.dp, if (active) SaharaTone.Rose.solid else DoctorHairline),
        modifier = modifier.fillMaxWidth().saharaAppear().testTag("voice-note-recorder")) {
        Column(Modifier.saharaContentSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedContent(when { active -> 1; has -> 2; else -> 0 }, transitionSpec = { SaharaMotion.crossfade() }, label = "voice-note-state") { state ->
                when (state) {
                    1 -> {
                        val s = session
                        Row(Modifier.fillMaxWidth().heightIn(min = if (simple) 72.dp else 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            val scale by animateFloatAsState(1f + (s?.level ?: 0f) * 0.5f, SaharaMotion.tween(SaharaMotion.VALUE_MS), label = "voice-note-level")
                            val pulse = if (SaharaMotion.enabled) careBreath() else 1f
                            Box(Modifier.size(20.dp).graphicsLayer { scaleX = scale * pulse; scaleY = scale * pulse }.clip(CircleShape).background(SaharaTone.Error.solid))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t("Recording… speak now", "रिकॉर्ड हो रहा है… अब बोलिए"), fontSize = if (simple) 20.sp else 18.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                                Text(CareVoiceRules.clock(s?.elapsedMs ?: 0L) + " / " + CareVoiceRules.clock(CareVoiceNotes.MAX_MS), fontSize = small, color = DoctorCardSub)
                            }
                            FilledTonalButton(onClick = { haptic(); s?.stop() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = if (simple) 72.dp else 56.dp).testTag("voice-note-stop"),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = SaharaTone.Error.solid, contentColor = SaharaTone.Error.onSolid)) {
                                Icon(Icons.Default.Stop, null, Modifier.size(if (simple) 32.dp else 24.dp)); Spacer(Modifier.width(6.dp)); Text(t("Stop", "रोकें"), fontSize = if (simple) 20.sp else 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    2 -> if (simple) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CareVoiceNotePlayer(storage, personId, itemId, framed = false, large = true)
                        // Simple Mode: one big Record again and no Remove (a new recording replaces the old one).
                        FilledTonalButton(onClick = { haptic(); permission.request { begin() } }, enabled = enabled && !micBusy, shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("voice-note-again")) {
                            Icon(Icons.Default.Mic, null, Modifier.size(28.dp), tint = RustText); Spacer(Modifier.width(10.dp))
                            Text(t("Record again", "फिर से रिकॉर्ड करें"), color = RustText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CareVoiceNotePlayer(storage, personId, itemId, framed = false)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { haptic(); permission.request { begin() } }, enabled = enabled && !micBusy, modifier = Modifier.heightIn(min = 48.dp).testTag("voice-note-again")) {
                                Icon(Icons.Default.Mic, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("Record again", "फिर से रिकॉर्ड करें"), color = RustText, fontSize = 16.sp)
                            }
                            TextButton(onClick = { confirmRemove = true }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("voice-note-remove")) {
                                Icon(Icons.Default.DeleteOutline, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(6.dp)); Text(t("Remove voice", "आवाज़ हटाएँ"), color = DoctorCardSub, fontSize = 16.sp)
                            }
                        }
                    }
                    else -> Button(onClick = { haptic(); permission.request { begin() } }, enabled = enabled && !micBusy, shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaharaTone.Rose.solid, contentColor = SaharaTone.Rose.onSolid),
                        modifier = Modifier.fillMaxWidth().heightIn(min = if (simple) 72.dp else 64.dp).testTag("voice-note-record")) {
                        Icon(Icons.Default.Mic, null, Modifier.size(if (simple) 32.dp else 28.dp)); Spacer(Modifier.width(10.dp))
                        Text(t("Record your voice", "अपनी आवाज़ रिकॉर्ड करें"), fontSize = if (simple) 20.sp else 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val note = session?.status.orEmpty().ifBlank {
                when {
                    active && session?.writing == true && !simple -> t("Your voice is kept for your nurse, and the words are written above.", "आपकी आवाज़ नर्स के लिए रखी जाती है, और शब्द ऊपर लिखे जाते हैं।")
                    active -> t("Your voice is kept for your nurse and family.", "आपकी आवाज़ नर्स और परिवार के लिए रखी जाती है।")
                    micBusy -> t("Stop the other recording first.", "पहले दूसरी रिकॉर्डिंग रोकें।")
                    !has -> t("Speak instead of writing. Your nurse can listen to it.", "लिखने के बजाय बोलिए। आपकी नर्स इसे सुन सकती है।")
                    else -> ""
                }
            }
            if (note.isNotBlank()) Text(note, fontSize = small, lineHeight = if (simple) 24.sp else TextUnit.Unspecified, color = DoctorCardSub, modifier = Modifier.announce())
        }
    }
    if (confirmRemove) AlertDialog(onDismissRequest = { confirmRemove = false }, title = { Text(t("Remove this voice recording?", "यह आवाज़ की रिकॉर्डिंग हटाएँ?")) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; notes.remove(personId, itemId) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Remove", "हटाएँ"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Keep", "रखें")) } })
}

/** The play button for a saved voice note; shows nothing when the note has no recording on this phone. */
@Composable fun CareVoiceNotePlayer(storage: String, personId: String, itemId: String, modifier: Modifier = Modifier, framed: Boolean = true, large: Boolean = false) {
    val t = careText(); val context = LocalContext.current
    val notes = remember(storage) { CareVoiceNotes(context, storage) }
    val revision by CareVoiceNotes.revision
    val file = remember(revision, personId, itemId) { notes.file(personId, itemId).takeIf { notes.has(personId, itemId) } } ?: return
    var player by remember(file, revision) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file, revision) { mutableStateOf(false) }
    var position by remember(file, revision) { mutableIntStateOf(0) }
    val length = remember(file, revision) { ((file.length() - 44).coerceAtLeast(0) * 1000 / (CareVoiceNotes.RATE * 2)).toInt() }
    val owner = LocalLifecycleOwner.current
    // Keyed on revision too: a new recording over the same file gets a fresh player instead of the old prepared one.
    DisposableEffect(file, revision, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { runCatching { player?.pause() }; playing = false } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); runCatching { player?.release() }; player = null }
    }
    LaunchedEffect(playing) { while (playing) { position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0); kotlinx.coroutines.delay(200) } }
    fun toggle() {
        if (playing) { runCatching { player?.pause() }; playing = false; return }
        val p = player ?: runCatching { MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); setOnCompletionListener { playing = false; position = 0; runCatching { seekTo(0) } } } }.getOrNull()
        if (p == null) return
        player = p; runCatching { p.start() }.onSuccess { playing = true }
    }
    val progress by animateFloatAsState(if (length > 0) (position.toFloat() / length).coerceIn(0f, 1f) else 0f, SaharaMotion.tween(SaharaMotion.VALUE_MS), label = "voice-note-progress")
    val body: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().heightIn(min = if (large) 72.dp else 56.dp).padding(horizontal = if (framed) 8.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledIconButton(onClick = { toggle() }, modifier = Modifier.size(if (large) 72.dp else 52.dp).testTag("voice-note-play-$itemId"),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = SaharaTone.Rose.solid, contentColor = SaharaTone.Rose.onSolid)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) t("Pause the voice note", "आवाज़ का नोट रोकें") else t("Play the voice note", "आवाज़ का नोट चलाएँ"), Modifier.size(if (large) 40.dp else 30.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("Voice note", "आवाज़ का नोट"), fontSize = if (large) 20.sp else 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = CareVoiceRules.clock(position.toLong()) }, color = SaharaTone.Rose.solid, trackColor = SaharaTone.Rose.wash)
                Text(CareVoiceRules.clock(if (playing || position > 0) position.toLong() else length.toLong()), fontSize = if (large) 18.sp else 14.sp, color = DoctorCardSub)
            }
        }
    }
    if (framed) Surface(shape = RoundedCornerShape(14.dp), color = SaharaTone.Rose.wash, modifier = modifier.fillMaxWidth().padding(top = 8.dp)) { body() } else Box(modifier) { body() }
}
