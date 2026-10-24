package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.media.MediaMetadataRetriever
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun CareRemoteTranscriptPanel(
    source: String,
    identity: String,
    recording: File,
    recordingConsent: RecordingConsent?,
    token: suspend () -> String,
    onSaveConsent: suspend (RecordingConsent) -> Unit,
    onAppend: (String) -> Unit,
    client: CareRemoteAssist = remember { CareRemoteAssist() },
) {
    if (!CareAndroidAssistPolicy.externalAiEnabled) return
    val scope = rememberCoroutineScope()
    val latestSource by rememberUpdatedState(source)
    val latestToken by rememberUpdatedState(token)
    val latestAppend by rememberUpdatedState(onAppend)
    val latestSaveConsent by rememberUpdatedState(onSaveConsent)
    var capabilities by remember(identity) { mutableStateOf<CareAssistCapabilities?>(null) }
    var language by rememberSaveable(identity, recording.path) { mutableStateOf("en-IN") }
    var speaker by rememberSaveable(identity, recording.path) { mutableStateOf("Unknown") }
    var consent by rememberSaveable(identity, recording.path) { mutableStateOf(false) }
    var checked by remember(identity, recording.path, source) { mutableStateOf(false) }
    var result by remember(identity, recording.path, source) { mutableStateOf<CareAssistedTranscript?>(null) }
    var words by rememberSaveable(identity, recording.path, source) { mutableStateOf("") }
    var message by remember(identity, recording.path) { mutableStateOf("") }
    var busy by remember(identity) { mutableStateOf(false) }
    var job by remember(identity) { mutableStateOf<Job?>(null) }
    LaunchedEffect(identity, client) { capabilities = try { client.capabilities() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null } }
    DisposableEffect(identity, recording.path, source) { onDispose { job?.cancel() } }
    SurfaceCard(Mint) {
        Text("Transcribe a recorded turn", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
        Text("One person at a time, up to 30 seconds and 2 MB. Listen to the original recording and check who spoke.", style = MaterialTheme.typography.bodySmall)
        if (capabilities?.audioReady != true) Text("Online audio processing is unavailable in this release. Your recording and manual conversation remain available.", style = MaterialTheme.typography.bodySmall)
        else {
            CareLanguageMenu(language, enabled = !busy) { language = it }
            CheckLine(consent, { consent = it }, "Everyone separately agrees to sending this recording to Sarvam for transcription. The words and speaker will be checked before use.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val originalSource = source
                    job = scope.launch {
                        busy = true; message = ""; result = null; checked = false
                        try {
                            val previous = requireNotNull(recordingConsent) { "This recording has no saved participant consent. Make a new consented recording." }
                            require(previous.personId == identity && File(previous.audioPath).absolutePath == recording.absolutePath) { "Choose this person’s recording again." }
                            val duration = withContext(Dispatchers.IO) {
                                val reader = MediaMetadataRetriever()
                                try { reader.setDataSource(recording.absolutePath); reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
                                finally { reader.release() }
                            }
                            require(duration in 1..30000 && recording.length() in 16..2L * 1024 * 1024) { "Choose a turn up to 30 seconds and 2 MB. Longer recordings stay available for manual review." }
                            val approved = previous.copy(transcriptionConsentAt = System.currentTimeMillis(), noticeVersion = maxOf(previous.noticeVersion, 3))
                            latestSaveConsent(approved)
                            val next = client.transcribe(recording, approved, duration, language, latestToken())
                            if (originalSource == latestSource) { result = next; words = next.original }
                        } catch (cancel: CancellationException) { message = "Stopped. Your recording is unchanged."; throw cancel }
                        catch (error: Exception) { message = error.message ?: "Transcription could not finish. Your recording is unchanged." }
                        finally { busy = false }
                    }
                }, enabled = consent && recordingConsent != null && !busy, modifier = Modifier.testTag("care-transcribe-turn")) { Text(if (busy) "Transcribing…" else "Transcribe turn") }
                if (busy) TextButton(onClick = { job?.cancel() }) { Text("Stop") }
            }
        }
        result?.let { transcript ->
            Text("Machine transcript · original", style = MaterialTheme.typography.labelLarge)
            SelectionContainerText(transcript.original)
            CareField(words, { words = it; checked = false }, "Checked words", minLines = 3)
            Text("Speaker", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Unknown", "Patient", "Family / carer", "Doctor").forEach { role ->
                FilterChip(selected = speaker == role, onClick = { speaker = role; checked = false }, label = { Text(role) }, modifier = Modifier.heightIn(min = 48.dp))
            } }
            CheckLine(checked, { checked = it }, "I checked the audio, these words and who spoke, including negation and uncertainty.")
            Button(onClick = {
                try { latestAppend(CareDraftAssist.appendReviewedTranscript(latestSource, transcript.original, words, speaker, transcript.language, checked)); result = null; checked = false }
                catch (error: IllegalArgumentException) { message = error.message.orEmpty() }
            }, enabled = checked && words.isNotBlank(), modifier = Modifier.testTag("care-transcript-use")) { Text("Add checked turn") }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

/** Optional online selection never replaces the manual source picker or publishes a note. */
@Composable fun CareRemoteAssistPanel(
    source: String,
    identity: String,
    token: suspend () -> String,
    onUseDraft: (CareAssistedDraft) -> Unit,
    client: CareRemoteAssist = remember { CareRemoteAssist() },
) {
    if (!CareAndroidAssistPolicy.externalAiEnabled) return
    val scope = rememberCoroutineScope()
    val latestSource by rememberUpdatedState(source)
    val latestToken by rememberUpdatedState(token)
    val latestUse by rememberUpdatedState(onUseDraft)
    var capabilities by remember(identity) { mutableStateOf<CareAssistCapabilities?>(null) }
    var consent by rememberSaveable(identity, source) { mutableStateOf(false) }
    var checked by remember(identity, source) { mutableStateOf(false) }
    var result by remember(identity, source) { mutableStateOf<CareAssistedDraft?>(null) }
    var message by remember(identity, source) { mutableStateOf("") }
    var busy by remember(identity) { mutableStateOf(false) }
    var job by remember(identity) { mutableStateOf<Job?>(null) }
    LaunchedEffect(identity, client) {
        capabilities = try { client.capabilities() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
    }
    DisposableEffect(identity, source) { onDispose { job?.cancel() } }
    SurfaceCard(Mint) {
        Text("Prepare a source-linked draft", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
        val ready = capabilities?.ready == true
        if (!ready) Text("Online assistance is unavailable. You can keep building the note from the original words below.", style = MaterialTheme.typography.bodySmall)
        else {
            CheckLine(consent, { consent = it }, "Everyone agrees to sending these words to Sarvam for an AI draft. A person will review it before use.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val snapshot = source
                    job = scope.launch {
                        busy = true; message = ""; result = null; checked = false
                        try {
                            val next = client.draft(snapshot, latestToken(), consent)
                            if (snapshot == latestSource) result = next
                        } catch (cancel: CancellationException) { message = "Stopped. Your original words are unchanged."; throw cancel }
                        catch (error: Exception) { message = error.message ?: "The draft could not finish. Your original words are unchanged." }
                        finally { busy = false }
                    }
                }, enabled = consent && source.isNotBlank() && source.length <= 12000 && !busy, modifier = Modifier.testTag("care-assist-draft")) { Text(if (busy) "Preparing draft…" else "Prepare AI draft") }
                if (busy) TextButton(onClick = { job?.cancel() }) { Text("Stop") }
            }
        }
        result?.let { draft ->
            Spacer(Modifier.height(8.dp))
            Text("AI draft · review the exact source", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            careAssistFieldKeys.forEachIndexed { index, key ->
                val words = draft.fields[key].orEmpty()
                Text(NoteRules.labels[index], style = MaterialTheme.typography.labelLarge)
                Text(words.ifBlank { "Not stated in this conversation." }, style = MaterialTheme.typography.bodyMedium)
                CareSourceLinks(source, words)
                Spacer(Modifier.height(6.dp))
            }
            CheckLine(checked, { checked = it }, "I checked the selected words, speakers, negation and uncertainty against the original conversation.")
            Button(onClick = {
                if (draft.sourceHash == careAssistSourceHash(latestSource)) { latestUse(draft); result = null; checked = false }
                else message = "The conversation changed. Prepare a fresh draft."
            }, enabled = checked && !busy, modifier = Modifier.testTag("care-assist-use")) { Text("Use reviewed draft") }
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

/** A translation stays separate from the original, signed version and its acknowledgement. */
@Composable fun CareTranslationPanel(
    source: String,
    versionId: String,
    token: suspend () -> String,
    client: CareRemoteAssist = remember { CareRemoteAssist() },
) {
    if (!CareAndroidAssistPolicy.externalAiEnabled) return
    val scope = rememberCoroutineScope()
    val latestSource by rememberUpdatedState(source)
    val latestVersion by rememberUpdatedState(versionId)
    val latestToken by rememberUpdatedState(token)
    var capabilities by remember(versionId) { mutableStateOf<CareAssistCapabilities?>(null) }
    var from by rememberSaveable(versionId) { mutableStateOf("en-IN") }
    var to by rememberSaveable(versionId) { mutableStateOf("hi-IN") }
    var consent by rememberSaveable(versionId, source) { mutableStateOf(false) }
    var reviewed by remember(versionId, source, from, to) { mutableStateOf(false) }
    var result by remember(versionId, source, from, to) { mutableStateOf<CareAssistedTranslation?>(null) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember(versionId, source) { mutableStateOf("") }
    LaunchedEffect(versionId, client) { capabilities = try { client.capabilities() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null } }
    DisposableEffect(versionId, source, from, to) { onDispose { job?.cancel() } }
    SurfaceCard(Mint) {
        Text("Read in another language", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
        if (capabilities?.translationReady != true) Text("Translation is unavailable. The original note and downloaded-language read aloud remain available.", style = MaterialTheme.typography.bodySmall)
        else {
            Text("Original language", style = MaterialTheme.typography.labelLarge)
            CareLanguageMenu(from, enabled = !busy) { from = it }
            Text("Translate to", style = MaterialTheme.typography.labelLarge)
            CareLanguageMenu(to, enabled = !busy) { to = it }
            CheckLine(consent, { consent = it }, "I agree to sending this note to Sarvam for translation. The original stays unchanged.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val original = source; val selectedVersion = versionId
                    job = scope.launch {
                        busy = true; message = ""; result = null; reviewed = false
                        try {
                            val translated = client.translate(original, selectedVersion, from, to, latestToken(), consent)
                            if (original == latestSource && selectedVersion == latestVersion) result = translated
                        } catch (cancel: CancellationException) { message = "Stopped. The original note is unchanged."; throw cancel }
                        catch (error: Exception) { message = error.message ?: "Translation could not finish. Use the original note." }
                        finally { busy = false }
                    }
                }, enabled = consent && !busy && from != to && source.isNotBlank(), modifier = Modifier.testTag("care-translate")) { Text(if (busy) "Translating…" else "Translate note") }
                if (busy) TextButton(onClick = { job?.cancel() }) { Text("Stop") }
            }
        }
        result?.let { translated ->
            Text("AI translation · check against the original", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            SelectionContainerText(translated.translation)
            CheckLine(reviewed, { reviewed = it }, "A person who understands both languages checked this translation, including names, numbers, negation and uncertainty.")
            if (reviewed) CareReadAloudControl(translated.translation, "translation-${translated.versionId}-${translated.sourceHash}-${translated.targetLanguage}", translated.targetLanguage)
            Text("This does not replace the signed original or acknowledge a new version.", style = MaterialTheme.typography.bodySmall)
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
}
