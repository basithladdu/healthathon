package `in`.wedevit.care

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

private data class ReaderGoal(val id: String, val title: String, val body: String)

private fun readerFallbackGoals(note: CareVersion): List<ReaderGoal> = listOf(note.priorities, note.participants, note.topics, note.questions, note.nextSteps).mapIndexedNotNull { i, raw ->
    val text = raw.trim(); if (text.isBlank()) return@mapIndexedNotNull null
    val line = text.lines().first().trim(); val firstSentence = Regex("^(.+?[.!?])(\\s+|$)").find(line)?.groupValues?.get(1) ?: line
    val rest = text.removePrefix(line).trim().let { tail -> if (tail.isNotEmpty()) tail else line.removePrefix(firstSentence).trim() }
    ReaderGoal("fallback-$i", firstSentence.trim(), rest)
}

/** Reader body for doctor conversation notes: audio, transcription, then goals-of-care cards. */
@Composable fun DoctorNoteReaderBody(vm: CareModel, note: CareVersion) {
    val context = LocalContext.current
    val goals = remember(vm, note.id, note.personId, note.conversationId, note.priorities, note.participants, note.topics, note.questions, note.nextSteps) {
        val stored = runCatching { DoctorPreparationStore(context, vm.storageNamespace).load(DoctorPreparationStore.scope(vm.accountNamespace, note.personId, note.conversationId)) }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) stored.map { ReaderGoal(it.id, it.text.trim(), it.answer.trim().ifBlank { "Not answered in this conversation" }) } else readerFallbackGoals(note)
    }
    // The audio card is looked up after the first frame; the body grows smoothly instead of pushing the transcript down in one jump.
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        NoteReaderAudio(vm, note)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderHeading("Transcription")
            NoteReaderTranscript(note.source)
        }
        if (goals.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ReaderHeading("Goals of care")
            goals.forEach { NoteReaderGoalCard(it) }
        }
    }
}

@Composable private fun ReaderHeading(text: String) { Text(text, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk) }

@Composable private fun NoteReaderTranscript(source: String) {
    var all by rememberSaveable(source) { mutableStateOf(false) }
    var overflow by remember(source) { mutableStateOf(false) }
    Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("note-reader-transcript")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (source.isBlank()) Text("No transcript was saved with this note.", fontSize = 15.sp, lineHeight = 24.sp, color = DoctorCardSub)
            else {
                SelectionContainer { Text(source, fontSize = 15.sp, lineHeight = 24.sp, color = DoctorCardInk, maxLines = if (all) Int.MAX_VALUE else 12, overflow = TextOverflow.Ellipsis, onTextLayout = { if (!all) overflow = it.hasVisualOverflow }) }
                if (overflow || all) TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 48.dp).testTag("note-reader-transcript-toggle")) { Text(if (all) "Show less" else "Show all", color = DoctorAccent) }
            }
        }
    }
}

@Composable private fun NoteReaderGoalCard(goal: ReaderGoal) {
    var open by rememberSaveable(goal.id) { mutableStateOf(false) }
    val expandable = goal.body.isNotBlank()
    Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("note-reader-goal-${goal.id}")) {
        Column(Modifier.then(if (expandable) Modifier.clickable { open = !open } else Modifier).padding(horizontal = 18.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(goal.title, fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                if (expandable) Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Collapse" else "Expand", tint = DoctorCardSub)
            }
            if (open && expandable) Surface(color = DoctorCardConversation, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                SelectionContainer { Text(goal.body, fontSize = 15.sp, lineHeight = 23.sp, color = DoctorCardInk, modifier = Modifier.padding(14.dp)) }
            }
        }
    }
}

@Composable private fun NoteReaderAudio(vm: CareModel, note: CareVersion) {
    val person by vm.selected.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val sessionId = vm.cloud?.session?.collectAsStateWithLifecycle()?.value?.userId.orEmpty()
    var loaded by remember(note.id) { mutableStateOf(false) }
    var file by remember(note.id, note.contentHash) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(vm, vm.accountNamespace, vm.storageNamespace, sessionId, note.id, note.contentHash, person, versions) { file = vm.doctorAudioFor(note); loaded = true }
    val audio = file
    if (audio == null) { if (loaded) Text("No audio saved on this device for this conversation.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub, modifier = Modifier.testTag("note-reader-audio-none")); return }
    val context = LocalContext.current
    val player = remember(audio.absolutePath) { runCatching { MediaPlayer.create(context, Uri.fromFile(audio)) }.getOrNull() }
    var playing by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) { player?.setOnCompletionListener { playing = false }; onDispose { runCatching { player?.release() } } }
    fun seek(delta: Int) { player?.let { p -> runCatching { p.seekTo((p.currentPosition + delta).coerceIn(0, p.duration.coerceAtLeast(0))) } } }
    val recorded = remember(audio.absolutePath) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(audio.lastModified())) }
    Surface(color = DoctorAccent, contentColor = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("note-reader-audio-1")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Audio 1", fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text("Recorded $recorded", fontSize = 12.sp, lineHeight = 16.sp, color = Color.White)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { seek(-10_000) }, enabled = player != null, modifier = Modifier.size(48.dp).testTag("note-reader-audio-1-back")) { Icon(Icons.Default.Replay10, "Back 10 seconds", tint = Color.White) }
                IconButton(onClick = { if (playing) { runCatching { player?.pause() }; playing = false } else { runCatching { player?.start() }; playing = runCatching { player?.isPlaying == true }.getOrDefault(false) } }, enabled = player != null, modifier = Modifier.size(56.dp).testTag("note-reader-audio-1-play")) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause audio" else "Play audio", tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { seek(10_000) }, enabled = player != null, modifier = Modifier.size(48.dp).testTag("note-reader-audio-1-forward")) { Icon(Icons.Default.Forward10, "Forward 10 seconds", tint = Color.White) }
            }
            if (player == null) Text("This recording could not be opened.", fontSize = 12.sp, lineHeight = 16.sp, color = Color.White)
        }
    }
}
