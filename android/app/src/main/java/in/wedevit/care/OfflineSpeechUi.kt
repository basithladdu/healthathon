package `in`.wedevit.care

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

@Composable fun OfflineConversationTranscription(
    file: File,
    identity: String,
    consent: RecordingConsent?,
    enabled: Boolean = true,
    onUse: (OfflineTranscriptReview) -> Boolean,
) {
    if (consent == null || consent.recordingConsentAt <= 0) return
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val models = remember { OfflineSpeechModelStore(context) }
    val engine = remember { OfflineConversationTranscriber(context) }
    var ready by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(true) }
    var language by rememberSaveable(identity, file.absolutePath) { mutableStateOf(OfflineSpeechLanguage.MIXED.name) }
    var result by remember(identity, file.absolutePath) { mutableStateOf<OfflineTranscriptReview?>(null) }
    var words by rememberSaveable(identity, file.absolutePath) { mutableStateOf("") }
    var checked by rememberSaveable(identity, file.absolutePath) { mutableStateOf(false) }
    var error by remember(identity, file.absolutePath) { mutableStateOf("") }
    var task by remember(identity, file.absolutePath) { mutableStateOf<Job?>(null) }
    var operation by remember(identity, file.absolutePath) { mutableStateOf("") }
    val progressFlow = remember(identity, file.absolutePath) { MutableStateFlow(0L to 0L) }
    val progress by progressFlow.collectAsState()
    val latestUse by rememberUpdatedState(onUse)
    val busy = operation.isNotBlank()
    LaunchedEffect(identity, file.absolutePath) {
        checking = true
        try {
            ready = models.ready()
            engine.saved(file, consent, consent.personId)?.let { saved ->
                result = saved
                if (words.isBlank()) words = saved.text
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "The saved transcript could not be opened. Your recording is safe." }
        finally { checking = false }
    }
    DisposableEffect(identity, file.absolutePath, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) task?.cancel() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); task?.cancel() }
    }
    fun runOperation(label: String, action: suspend () -> Unit) {
        if (busy || checking || !enabled) return
        operation = label; error = ""; progressFlow.value = 0L to 0L
        task = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { error = "Stopped. Your recording and earlier words are safe."; throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Transcription could not finish. Your recording is safe. Try again." }
            finally { operation = ""; task = null }
        }
    }
    SurfaceCard(Mint) {
        Text("Recording → words", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfflineSpeechLanguage.entries.forEach { choice -> FilterChip(selected = language == choice.name, onClick = { language = choice.name }, enabled = !busy, label = { Text(choice.label) }, modifier = Modifier.testTag("offline-language-${choice.name}")) }
        }
        if (!ready) {
            Text("Hindi / English language pack · 987 MB", style = MaterialTheme.typography.bodyMedium)
            Text("Download once, then transcribe without a connection.", style = MaterialTheme.typography.bodySmall)
            Text("Needs a 64-bit phone and at least 2.5 GB free memory. Check the words against the recording.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { runOperation("Downloading language pack") { models.download { done, total -> progressFlow.value = done to total }; ready = true } }, enabled = enabled && !checking && !busy, modifier = Modifier.fillMaxWidth().testTag("offline-model-download")) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (checking) "Checking language pack…" else "Download language pack") }
        } else {
            Button(onClick = { runOperation("Transcribing recording") {
                val next = engine.transcribe(file, consent, consent.personId, OfflineSpeechLanguage.valueOf(language)) { done, total -> progressFlow.value = done to total }
                result = next; words = next.originalText; checked = false
            } }, enabled = enabled && !busy && !checking, modifier = Modifier.fillMaxWidth().testTag("offline-transcribe")) { Icon(Icons.Default.GraphicEq, null); Spacer(Modifier.width(8.dp)); Text(if (result == null) "Transcribe recording" else "Transcribe again") }
        }
        if (busy) {
            Text(if (progress.second > 0) "$operation · ${(progress.first * 100 / progress.second).coerceIn(0, 100)}%" else if (operation == "Transcribing recording") "Preparing audio…" else operation)
            if (progress.second > 0) LinearProgressIndicator(progress = { (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            TextButton(onClick = { task?.cancel() }, modifier = Modifier.testTag("offline-cancel")) { Text("Stop") }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("offline-error").announce())
        result?.let { original ->
            Spacer(Modifier.height(8.dp))
            Text("Original recognised words", fontWeight = FontWeight.SemiBold)
            SelectionContainer { Text(original.originalText, style = MaterialTheme.typography.bodyMedium) }
            Text("Check names, medicine names, numbers and speakers against the audio. Mixed-language words may be missed.", style = MaterialTheme.typography.bodySmall)
            CareField(value = words, onValueChange = { if (it.length <= OfflineSpeechModel.maxOriginalChars) { words = it; checked = false } }, label = "Corrected transcript", minLines = 4)
            if (words.length > NoteRules.MAX_SOURCE_CHARS) Text("The full original is retained. Select up to ${NoteRules.MAX_SOURCE_CHARS} characters for this care note; ${words.length} are selected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            CheckLine(checked, { checked = it }, "I checked these words against the recording.")
            Button(onClick = { runOperation("Saving reviewed words") {
                val reviewed = original.reviewed(words, checked)
                engine.saveReview(file, consent, consent.personId, reviewed)
                if (latestUse(reviewed)) result = reviewed else error = "The conversation changed. Your reviewed transcript is retained here; open this person's conversation again."
            } }, enabled = enabled && checked && words.isNotBlank() && words.length <= NoteRules.MAX_SOURCE_CHARS && !busy, modifier = Modifier.fillMaxWidth().testTag("offline-use-reviewed")) { Text("Use reviewed words") }
        }
    }
}
