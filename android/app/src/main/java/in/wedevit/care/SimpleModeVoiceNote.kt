package `in`.wedevit.care

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * The patient's own voice notes from Simple Mode (point 9 of the clinical team's note): the voice is kept as audio, not
 * only as words, so a nurse can listen to how the patient said it. The audio stays ONLY on this phone, inside the person's
 * report folder (removed with the person or the account); the journal entry on the shared record says a voice note exists.
 * Plain recording, no transcription, so it works offline and on older phones without any download.
 */
internal class SimpleVoiceNotes(context: Context, storage: String) {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "reports/${storage.ifBlank { "guest" }}")
    init { require(storage.none { it == '/' || it == '\\' } && storage != "..") }

    private fun saved(person: String, itemId: String): File {
        require(SimpleModeRules.safeFileId(person) && SimpleModeRules.safeFileId(itemId)) { "This voice note could not be identified." }
        return File(File(File(root, person), "voice-notes"), "$itemId.m4a")
    }

    /** Where a recording waits until it is saved (the cache, so an abandoned one is cleared by the phone in time). */
    fun draft(itemId: String): File {
        require(SimpleModeRules.safeFileId(itemId))
        return File(File(app.cacheDir, "simple-voice"), "$itemId.m4a")
    }

    /** The saved audio of the voice note in journal entry [itemId], or null. */
    fun audio(person: String, itemId: String): File? = runCatching { saved(person, itemId).takeIf { it.isFile && it.length() > 0 } }.getOrNull()

    /** Moves a finished draft into the person's folder under the journal entry's id. */
    fun keep(person: String, itemId: String): File {
        val from = draft(itemId); val to = saved(person, itemId)
        check(from.isFile && from.length() > 0) { "The recording is empty." }
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) { from.copyTo(to, overwrite = true); from.delete() }
        return to
    }

    fun discardDraft(itemId: String) { runCatching { draft(itemId).delete() } }
}

/** One microphone recording at a time, as AAC in an .m4a file; [level] follows the voice for the moving ring. */
internal class SimpleVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L
    var recording by mutableStateOf(false); private set

    /** Starts recording into [file]; false when the microphone could not start. */
    fun start(file: File, onLimit: () -> Unit): Boolean {
        if (recording) return true
        file.parentFile?.mkdirs(); file.delete()
        @Suppress("DEPRECATION") val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC); next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC); next.setAudioChannels(1)
            next.setAudioEncodingBitRate(48_000); next.setAudioSamplingRate(32_000)
            next.setMaxDuration(SimpleModeRules.VOICE_MAX_SECONDS * 1000)
            next.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onLimit() }
            next.setOutputFile(file.absolutePath); next.prepare(); next.start()
            recorder = next; startedAt = System.currentTimeMillis(); recording = true; true
        } catch (_: Exception) { runCatching { next.release() }; file.delete(); false }
    }

    fun seconds(): Int = if (recording) ((System.currentTimeMillis() - startedAt) / 1000).toInt() else 0
    fun level(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f).coerceIn(0f, 1f)

    /** Stops and returns the length in seconds, or -1 when the recording was too short to keep. */
    fun stop(): Int {
        val active = recorder ?: return -1
        val length = seconds(); recorder = null; recording = false
        return try { active.stop(); length } catch (_: Exception) { -1 } finally { runCatching { active.release() } }
    }

    fun release() { if (recorder != null) stop() }
}

/** Plays one audio file at a time; [playing] is true while it plays. */
internal class SimpleVoicePlayer {
    private var player: MediaPlayer? = null
    var playing by mutableStateOf(false); private set

    fun play(file: File): Boolean {
        stop()
        val next = MediaPlayer()
        return try {
            next.setDataSource(file.absolutePath); next.setOnCompletionListener { stop() }; next.prepare(); next.start()
            player = next; playing = true; true
        } catch (_: Exception) { runCatching { next.release() }; false }
    }

    fun stop() { val p = player ?: return; player = null; playing = false; runCatching { p.stop() }; runCatching { p.release() } }
}

/** A player that stops when the app goes to the background and is released when it leaves the screen. */
@Composable internal fun rememberSimpleVoicePlayer(): SimpleVoicePlayer {
    val owner = LocalLifecycleOwner.current
    val player = remember { SimpleVoicePlayer() }
    DisposableEffect(player, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.stop() }
    }
    return player
}

/**
 * "Listen to the voice note": for the family and the care team, beside a Simple Mode voice note in the journal
 * ([SimpleModeRules.isVoiceNote]). Shows nothing when the audio is not on this phone.
 */
@Composable fun SimpleVoiceNotePlayer(storage: String, personId: String, itemId: String, modifier: Modifier = Modifier) {
    val t = careText(); val context = LocalContext.current
    val notes = remember(storage) { SimpleVoiceNotes(context, storage) }
    val audio = remember(notes, personId, itemId) { notes.audio(personId, itemId) } ?: return
    val player = rememberSimpleVoicePlayer()
    val tone = SaharaTone.Marigold
    Surface(onClick = { if (player.playing) player.stop() else player.play(audio) }, shape = RoundedCornerShape(14.dp), color = tone.wash, contentColor = tone.ink,
        border = BorderStroke(1.5.dp, tone.solid), modifier = modifier.heightIn(min = 48.dp).saharaPressBounce().testTag("voice-note-play-$itemId")) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (player.playing) Icons.Default.Stop else Icons.Default.PlayArrow, null, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
            Text(if (player.playing) t("Stop", "रोकें") else t("Listen to the voice note", "आवाज़ वाला नोट सुनें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
