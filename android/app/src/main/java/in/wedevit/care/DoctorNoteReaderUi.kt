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

private data class ReaderGoal(val id: String, val title: String, val body: String, val answer: String = body)

/** B7: the transcript lines an answer in the note came from, so a reader can see what was said. Word matching only. */
internal object NoteSourceLines {
    private fun words(text: String) = Regex("[\\p{L}\\p{M}\\p{N}]{3,}").findAll(text.lowercase()).map { it.value }.toSet()
    private fun plain(text: String) = text.lowercase().replace(Regex("^\\s*(?:\\d+[.)]\\s*)?(?:[\\p{L} ]{1,24}:\\s*)?"), "").replace(Regex("\\s+"), " ").trim()

    fun find(answer: String, source: String, max: Int = 2): List<String> {
        val wanted = words(answer); val exact = plain(answer)
        if (wanted.size < 2 && exact.length < 12) return emptyList()
        val lines = source.lines().map(String::trim).filter(String::isNotBlank)
        val scored = lines.mapIndexedNotNull { i, line ->
            val said = plain(line)
            val score = when {
                said.length >= 12 && (exact.contains(said) || said.contains(exact)) -> 2.0
                wanted.isEmpty() -> 0.0
                else -> (words(line) intersect wanted).size.toDouble() / wanted.size
            }
            if (score >= 0.6 && (words(line) intersect wanted).size >= 2 || score >= 2.0) Triple(i, line, score) else null
        }
        return scored.sortedByDescending { it.third }.take(max).sortedBy { it.first }.map { it.second }
    }
}

private fun readerFallbackGoals(note: CareVersion): List<ReaderGoal> = listOf(note.priorities, note.participants, note.topics, note.questions, note.nextSteps).mapIndexedNotNull { i, raw ->
    val text = raw.trim(); if (text.isBlank()) return@mapIndexedNotNull null
    val line = text.lines().first().trim(); val firstSentence = Regex("^(.+?[.!?])(\\s+|$)").find(line)?.groupValues?.get(1) ?: line
    val rest = text.removePrefix(line).trim().let { tail -> if (tail.isNotEmpty()) tail else line.removePrefix(firstSentence).trim() }
    ReaderGoal("fallback-$i", firstSentence.trim(), rest)
}

/** Reader body for doctor conversation notes: the goals-of-care note in the legal form's layout (Sections I to IV) with its PDF, then audio and transcription. */
@Composable fun DoctorNoteReaderBody(vm: CareModel, note: CareVersion) { val t = careText(); val hindi = careHindi()
    val context = LocalContext.current
    val form = remember(note.id, note.contentHash, hindi) { DoctorGocDiscussions.fromNote(note, hindi) }
    var problem by remember(note.id) { mutableStateOf<String?>(null) }
    val goals = remember(vm, note.id, note.personId, note.conversationId, note.priorities, note.participants, note.topics, note.questions, note.nextSteps) {
        val stored = runCatching { DoctorPreparationStore(context, vm.storageNamespace).load(DoctorPreparationStore.scope(vm.accountNamespace, note.personId, note.conversationId)) }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) stored.map { ReaderGoal(it.id, it.text.trim(), it.answer.trim().ifBlank { t("Not answered", "जवाब नहीं मिला") }, it.answer.trim()) } else readerFallbackGoals(note)
    }
    // The audio card is looked up after the first frame; the body grows smoothly instead of pushing the transcript down in one jump.
    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ReaderHeading(t("Goals of care", "देखभाल के लक्ष्य")) }
                DoctorGocPdfButton(form) { problem = it }
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.announce()) }
            DoctorGocFormSections(form)
        }
        // The doctor's own questions and answers, then what the note says beyond Sections I and II. The fallback cards repeat the note, so they are left out.
        val asked = goals.filterNot { it.id.startsWith("fallback-") }
        if (asked.isNotEmpty() || form.note.isNotBlank()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderHeading(t("Doctor's note", "डॉक्टर का नोट"))
            asked.forEach { NoteReaderGoalCard(it, note.source) }
            if (form.note.isNotBlank()) Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("note-reader-doctor-note")) {
                SelectionContainer { Text(form.note, fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardInk, modifier = Modifier.padding(16.dp)) }
            }
        }
        NoteReaderAudio(vm, note)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderHeading(t("Transcription", "बातचीत की लिखाई"))
            NoteReaderTranscript(note.source)
        }
    }
}

@Composable private fun ReaderHeading(text: String) { Text(text, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk) }

@Composable private fun NoteReaderTranscript(source: String) { val t = careText();
    var all by rememberSaveable(source) { mutableStateOf(false) }
    var overflow by remember(source) { mutableStateOf(false) }
    Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("note-reader-transcript")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (source.isBlank()) Text(t("No transcript", "कोई लिखाई नहीं"), fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardSub)
            else {
                SelectionContainer { Text(source, fontSize = 16.sp, lineHeight = 25.sp, color = DoctorCardInk, maxLines = if (all) Int.MAX_VALUE else 12, overflow = TextOverflow.Ellipsis, onTextLayout = { if (!all) overflow = it.hasVisualOverflow }) }
                if (overflow || all) TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 48.dp).testTag("note-reader-transcript-toggle")) { Text(if (all) t("Show less", "कम दिखाएँ") else t("Show all", "सभी दिखाएँ"), color = RustText, fontSize = 16.sp) }
            }
        }
    }
}

@Composable private fun NoteReaderGoalCard(goal: ReaderGoal, source: String) { val t = careText()
    var open by rememberSaveable(goal.id) { mutableStateOf(false) }
    val said = remember(goal.answer, source) { NoteSourceLines.find(goal.answer, source) }
    val expandable = goal.body.isNotBlank()
    Surface(color = DoctorCardReport, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("note-reader-goal-${goal.id}")) {
        Column(Modifier.then(if (expandable) Modifier.clickable { open = !open } else Modifier).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(goal.title, fontFamily = SaharaTitleFont, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                if (expandable) Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) t("Collapse", "बंद करें") else t("Expand", "खोलें"), tint = DoctorCardSub)
            }
            if (open && expandable) Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(color = DoctorHairline)
                SelectionContainer { Text(goal.body, fontSize = 16.sp, lineHeight = 24.sp, color = DoctorCardInk) }
            }
            if (open && said.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("note-reader-said-${goal.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                said.forEach { line -> SelectionContainer { Text("“$line”", fontSize = 16.sp, lineHeight = 24.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = DoctorCardSub) } }
            }
        }
    }
}

@Composable private fun NoteReaderAudio(vm: CareModel, note: CareVersion) { val t = careText();
    val person by vm.selected.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val sessionId = vm.cloud?.session?.collectAsStateWithLifecycle()?.value?.userId.orEmpty()
    var loaded by remember(note.id) { mutableStateOf(false) }
    var file by remember(note.id, note.contentHash) { mutableStateOf<java.io.File?>(null) }
    LaunchedEffect(vm, vm.accountNamespace, vm.storageNamespace, sessionId, note.id, note.contentHash, person, versions) { file = vm.doctorAudioFor(note); loaded = true }
    val audio = file
    if (audio == null) return
    val context = LocalContext.current
    val player = remember(audio.absolutePath) { runCatching { MediaPlayer.create(context, Uri.fromFile(audio)) }.getOrNull() }
    var playing by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) { player?.setOnCompletionListener { playing = false }; onDispose { runCatching { player?.release() } } }
    fun seek(delta: Int) { player?.let { p -> runCatching { p.seekTo((p.currentPosition + delta).coerceIn(0, p.duration.coerceAtLeast(0))) } } }
    val recorded = remember(audio.absolutePath) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(audio.lastModified())) }
    Surface(color = DoctorAccent, contentColor = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("note-reader-audio-1")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Audio 1", "ऑडियो 1"), fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(recorded, fontSize = 14.sp, lineHeight = 20.sp, color = Color.White)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { seek(-10_000) }, enabled = player != null, modifier = Modifier.size(48.dp).testTag("note-reader-audio-1-back")) { Icon(Icons.Default.Replay10, t("Back 10 seconds", "10 सेकंड पीछे"), tint = Color.White) }
                IconButton(onClick = { if (playing) { runCatching { player?.pause() }; playing = false } else { runCatching { player?.start() }; playing = runCatching { player?.isPlaying == true }.getOrDefault(false) } }, enabled = player != null, modifier = Modifier.size(56.dp).testTag("note-reader-audio-1-play")) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) t("Pause audio", "ऑडियो रोकें") else t("Play audio", "ऑडियो चलाएँ"), tint = Color.White, modifier = Modifier.size(36.dp)) }
                IconButton(onClick = { seek(10_000) }, enabled = player != null, modifier = Modifier.size(48.dp).testTag("note-reader-audio-1-forward")) { Icon(Icons.Default.Forward10, t("Forward 10 seconds", "10 सेकंड आगे"), tint = Color.White) }
            }
            if (player == null) Text(t("Can't play this audio", "यह ऑडियो नहीं चल सका"), fontSize = 14.sp, lineHeight = 20.sp, color = Color.White)
        }
    }
}
