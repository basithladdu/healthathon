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
) { val t = careText();
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
        Text(t("Transcribe a recorded turn", "रिकॉर्ड की गई बारी की लिखाई बनाएँ"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
        Text(t("One person at a time, up to 30 seconds and 2 MB. Listen to the original recording and check who spoke.", "एक बार में एक व्यक्ति, 30 सेकंड और 2 MB तक। मूल रिकॉर्डिंग सुनें और जाँचें कि कौन बोला।"), style = MaterialTheme.typography.bodySmall)
        if (capabilities?.audioReady != true) Text(t("Online audio processing is unavailable in this release. Your recording and manual conversation remain available.", "इस संस्करण में ऑनलाइन ऑडियो प्रोसेसिंग उपलब्ध नहीं है। आपकी रिकॉर्डिंग और लिखी बातचीत बनी रहेगी।"), style = MaterialTheme.typography.bodySmall)
        else {
            CareLanguageMenu(language, enabled = !busy) { language = it }
            CheckLine(consent, { consent = it }, t("Everyone separately agrees to sending this recording to Sarvam for transcription. The words and speaker will be checked before use.", "सभी अलग-अलग इस रिकॉर्डिंग को लिखाई के लिए Sarvam को भेजने पर राज़ी हैं। इस्तेमाल से पहले शब्द और बोलने वाले की जाँच होगी।"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val originalSource = source
                    job = scope.launch {
                        busy = true; message = ""; result = null; checked = false
                        try {
                            val previous = requireNotNull(recordingConsent) { t("This recording has no saved participant consent. Make a new consented recording.", "इस रिकॉर्डिंग पर भागीदारों की सहमति सहेजी नहीं है। सहमति के साथ नई रिकॉर्डिंग बनाएँ।") }
                            require(previous.personId == identity && File(previous.audioPath).absolutePath == recording.absolutePath) { t("Choose this person’s recording again.", "इस व्यक्ति की रिकॉर्डिंग फिर चुनें।") }
                            val duration = withContext(Dispatchers.IO) {
                                val reader = MediaMetadataRetriever()
                                try { reader.setDataSource(recording.absolutePath); reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
                                finally { reader.release() }
                            }
                            require(duration in 1..30000 && recording.length() in 16..2L * 1024 * 1024) { t("Choose a turn up to 30 seconds and 2 MB. Longer recordings stay available for manual review.", "30 सेकंड और 2 MB तक की बारी चुनें। लंबी रिकॉर्डिंग ख़ुद जाँचने के लिए बनी रहती हैं।") }
                            val approved = previous.copy(transcriptionConsentAt = System.currentTimeMillis(), noticeVersion = maxOf(previous.noticeVersion, 3))
                            latestSaveConsent(approved)
                            val next = client.transcribe(recording, approved, duration, language, latestToken())
                            if (originalSource == latestSource) { result = next; words = next.original }
                        } catch (cancel: CancellationException) { message = t("Stopped. Your recording is unchanged.", "रोक दिया। आपकी रिकॉर्डिंग वैसी ही है।"); throw cancel }
                        catch (error: Exception) { message = error.message ?: t("Transcription could not finish. Your recording is unchanged.", "लिखाई पूरी नहीं हो सकी। आपकी रिकॉर्डिंग वैसी ही है।") }
                        finally { busy = false }
                    }
                }, enabled = consent && recordingConsent != null && !busy, modifier = Modifier.testTag("care-transcribe-turn")) { Text(if (busy) "Transcribing…" else t("Transcribe turn", "बारी की लिखाई बनाएँ")) }
                if (busy) TextButton(onClick = { job?.cancel() }) { Text(t("Stop", "रोकें")) }
            }
        }
        result?.let { transcript ->
            Text(t("Machine transcript · original", "मशीन की लिखाई · मूल"), style = MaterialTheme.typography.labelLarge)
            SelectionContainerText(transcript.original)
            CareField(words, { words = it; checked = false }, t("Checked words", "जाँचे गए शब्द"), minLines = 3)
            Text(t("Speaker", "बोलने वाले"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Unknown", "Patient", "Family / carer", "Doctor").forEach { role ->
                FilterChip(selected = speaker == role, onClick = { speaker = role; checked = false }, label = { Text(role) }, modifier = Modifier.heightIn(min = 48.dp))
            } }
            CheckLine(checked, { checked = it }, t("I checked the audio, these words and who spoke, including negation and uncertainty.", "मैंने ऑडियो, ये शब्द और कौन बोला, यह जाँच लिया है, ‘नहीं’ और अनिश्चितता वाली बातें भी।"))
            Button(onClick = {
                try { latestAppend(CareDraftAssist.appendReviewedTranscript(latestSource, transcript.original, words, speaker, transcript.language, checked)); result = null; checked = false }
                catch (error: IllegalArgumentException) { message = error.message.orEmpty() }
            }, enabled = checked && words.isNotBlank(), modifier = Modifier.testTag("care-transcript-use")) { Text(t("Add checked turn", "जाँची बारी जोड़ें")) }
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
) { val t = careText();
    if (!CareAndroidAssistPolicy.externalAiEnabled) { CareAiOffNotice(); return }
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
        Text(t("Prepare a source-linked draft", "स्रोत से जुड़ा ड्राफ़्ट बनाएँ"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
        val ready = capabilities?.ready == true
        if (!ready) Text(t("Online assistance is unavailable. You can keep building the note from the original words below.", "ऑनलाइन मदद उपलब्ध नहीं है। आप नीचे मूल शब्दों से नोट बनाते रह सकते हैं।"), style = MaterialTheme.typography.bodySmall)
        else {
            CheckLine(consent, { consent = it }, t("Everyone agrees to sending these words to Sarvam for an AI draft. A person will review it before use.", "सभी इन शब्दों को AI ड्राफ़्ट के लिए Sarvam को भेजने पर राज़ी हैं। इस्तेमाल से पहले कोई व्यक्ति इसे जाँचेगा।"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val snapshot = source
                    job = scope.launch {
                        busy = true; message = ""; result = null; checked = false
                        try {
                            val next = client.draft(snapshot, latestToken(), consent)
                            if (snapshot == latestSource) result = next
                        } catch (cancel: CancellationException) { message = t("Stopped. Your original words are unchanged.", "रोक दिया। आपके मूल शब्द वैसे ही हैं।"); throw cancel }
                        catch (error: Exception) { message = error.message ?: t("The draft could not finish. Your original words are unchanged.", "ड्राफ़्ट पूरा नहीं हो सका। आपके मूल शब्द वैसे ही हैं।") }
                        finally { busy = false }
                    }
                }, enabled = consent && source.isNotBlank() && source.length <= 12000 && !busy, modifier = Modifier.testTag("care-assist-draft")) { Text(if (busy) t("Preparing draft…", "ड्राफ़्ट बन रहा है…") else t("Prepare AI draft", "AI ड्राफ़्ट बनाएँ")) }
                if (busy) TextButton(onClick = { job?.cancel() }) { Text(t("Stop", "रोकें")) }
            }
        }
        result?.let { draft ->
            Spacer(Modifier.height(8.dp))
            Text(t("AI draft · review the exact source", "AI ड्राफ़्ट · मूल स्रोत से जाँचें"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            careAssistFieldKeys.forEachIndexed { index, key ->
                val words = draft.fields[key].orEmpty()
                Text(NoteRules.labels[index], style = MaterialTheme.typography.labelLarge)
                Text(words.ifBlank { t("Not stated in this conversation.", "इस बातचीत में नहीं कहा गया।") }, style = MaterialTheme.typography.bodyMedium)
                CareSourceLinks(source, words)
                Spacer(Modifier.height(6.dp))
            }
            CheckLine(checked, { checked = it }, t("I checked the selected words, speakers, negation and uncertainty against the original conversation.", "मैंने चुने शब्द, बोलने वाले, ‘नहीं’ और अनिश्चितता वाली बातें मूल बातचीत से मिला ली हैं।"))
            Button(onClick = {
                if (draft.sourceHash == careAssistSourceHash(latestSource)) { latestUse(draft); result = null; checked = false }
                else message = t("The conversation changed. Prepare a fresh draft.", "बातचीत बदल गई। नया ड्राफ़्ट बनाएँ।")
            }, enabled = checked && !busy, modifier = Modifier.testTag("care-assist-use")) { Text(t("Use reviewed draft", "जाँचा ड्राफ़्ट इस्तेमाल करें")) }
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
) { val t = careText();
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
        Text(t("Read in another language", "दूसरी भाषा में पढ़ें"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
        if (capabilities?.translationReady != true) Text(t("Translation is unavailable. The original note and downloaded-language read aloud remain available.", "अनुवाद उपलब्ध नहीं है। मूल नोट और डाउनलोड की गई भाषा में पढ़कर सुनाना बना रहेगा।"), style = MaterialTheme.typography.bodySmall)
        else {
            Text(t("Original language", "मूल भाषा"), style = MaterialTheme.typography.labelLarge)
            CareLanguageMenu(from, enabled = !busy) { from = it }
            Text(t("Translate to", "किस भाषा में"), style = MaterialTheme.typography.labelLarge)
            CareLanguageMenu(to, enabled = !busy) { to = it }
            CheckLine(consent, { consent = it }, t("I agree to sending this note to Sarvam for translation. The original stays unchanged.", "मैं यह नोट अनुवाद के लिए Sarvam को भेजने पर राज़ी हूँ। मूल नहीं बदलेगा।"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val original = source; val selectedVersion = versionId
                    job = scope.launch {
                        busy = true; message = ""; result = null; reviewed = false
                        try {
                            val translated = client.translate(original, selectedVersion, from, to, latestToken(), consent)
                            if (original == latestSource && selectedVersion == latestVersion) result = translated
                        } catch (cancel: CancellationException) { message = t("Stopped. The original note is unchanged.", "रोक दिया। मूल नोट वैसा ही है।"); throw cancel }
                        catch (error: Exception) { message = error.message ?: t("Translation could not finish. Use the original note.", "अनुवाद पूरा नहीं हो सका। मूल नोट इस्तेमाल करें।") }
                        finally { busy = false }
                    }
                }, enabled = consent && !busy && from != to && source.isNotBlank(), modifier = Modifier.testTag("care-translate")) { Text(if (busy) "Translating…" else t("Translate note", "नोट का अनुवाद करें")) }
                if (busy) TextButton(onClick = { job?.cancel() }) { Text(t("Stop", "रोकें")) }
            }
        }
        result?.let { translated ->
            Text(t("AI translation · check against the original", "AI अनुवाद · मूल से मिलाएँ"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
            SelectionContainerText(translated.translation)
            CheckLine(reviewed, { reviewed = it }, t("A person who understands both languages checked this translation, including names, numbers, negation and uncertainty.", "दोनों भाषाएँ समझने वाले व्यक्ति ने यह अनुवाद जाँचा है, नाम, अंक, ‘नहीं’ और अनिश्चितता वाली बातें भी।"))
            if (reviewed) CareReadAloudControl(translated.translation, "translation-${translated.versionId}-${translated.sourceHash}-${translated.targetLanguage}", translated.targetLanguage)
            Text(t("This does not replace the signed original or acknowledge a new version.", "यह हस्ताक्षर वाले मूल की जगह नहीं लेता और नए संस्करण की पुष्टि नहीं करता।"), style = MaterialTheme.typography.bodySmall)
        }
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

/** Shown where an AI draft would be while no AI provider is switched on, so nothing looks automatic. */
internal object CareAiOffCopy {
    const val title = "AI drafting is off · AI ड्राफ़्ट बंद है"
    const val body = "No AI service is switched on in Sahara yet, so these words are not sent anywhere for a draft. Choose lines from the conversation or type each section yourself. · Sahara में अभी कोई AI सेवा चालू नहीं है, इसलिए ये शब्द ड्राफ़्ट के लिए कहीं नहीं भेजे जाते। बातचीत से पंक्तियाँ चुनें या हर हिस्सा खुद लिखें।"
}

@Composable internal fun CareAiOffNotice() {
    SurfaceCard(Mint) {
        Text(CareAiOffCopy.title, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, modifier = Modifier.testTag("care-ai-off"))
        Text(CareAiOffCopy.body, style = MaterialTheme.typography.bodySmall)
    }
}
