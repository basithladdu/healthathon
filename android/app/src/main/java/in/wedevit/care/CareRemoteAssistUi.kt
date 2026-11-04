package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    LaunchedEffect(identity, client) { capabilities = try { client.capabilities(latestToken()) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null } }
    DisposableEffect(identity, recording.path, source) { onDispose { job?.cancel() } }
    // Nothing is offered until the server says online transcription is set up; on-device transcription stays above.
    val ready = capabilities?.takeIf { it.audioReady } ?: return
    SurfaceCard(Mint) {
        CareAssistTitle(Icons.Default.GraphicEq, t("Turn one person's speech into text", "एक व्यक्ति की बात को लिखाई में बदलें"),
            t("One person at a time. Up to 30 seconds and 2 MB. Listen to the recording and check who spoke.", "एक बार में एक व्यक्ति। 30 सेकंड और 2 MB तक। रिकॉर्डिंग सुनें और जाँचें कि कौन बोला।"))
        run {
            CareLanguageMenu(language, enabled = !busy) { language = it }
            CheckLine(consent, { consent = it }, t("Everyone here separately agrees to send this recording to ${ready.provider} to turn speech into text. Nothing is sent until you tap the button. The words and who spoke will be checked before use.", "यहाँ मौजूद सभी इस रिकॉर्डिंग को ${ready.provider} को भेजने के लिए अलग से राज़ी हैं, ताकि बात लिखाई में बदले। बटन दबाने तक कुछ नहीं भेजा जाता। इस्तेमाल से पहले शब्द और बोलने वाले की जाँच होगी।"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val originalSource = source
                    job = scope.launch {
                        busy = true; message = ""; result = null; checked = false
                        try {
                            val previous = requireNotNull(recordingConsent) { t("This recording has no saved agreement from the people in it. Make a new recording after everyone agrees.", "इस रिकॉर्डिंग पर लोगों की सहमति सेव नहीं है। सबकी सहमति लेकर नई रिकॉर्डिंग बनाएँ।") }
                            require(previous.personId == identity && File(previous.audioPath).absolutePath == recording.absolutePath) { t("Choose this person’s recording again.", "इस व्यक्ति की रिकॉर्डिंग फिर चुनें।") }
                            val duration = withContext(Dispatchers.IO) {
                                val reader = MediaMetadataRetriever()
                                try { reader.setDataSource(recording.absolutePath); reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
                                finally { reader.release() }
                            }
                            require(duration in 1..30000 && recording.length() in 16..2L * 1024 * 1024) { t("Choose a part up to 30 seconds and 2 MB. Longer recordings stay saved so you can check them yourself.", "30 सेकंड और 2 MB तक का हिस्सा चुनें। लंबी रिकॉर्डिंग सेव रहती हैं, ताकि आप ख़ुद जाँच सकें।") }
                            val approved = previous.copy(transcriptionConsentAt = System.currentTimeMillis(), noticeVersion = maxOf(previous.noticeVersion, 3))
                            latestSaveConsent(approved)
                            val next = client.transcribe(recording, approved, duration, language, latestToken())
                            if (originalSource == latestSource) { result = next; words = next.original }
                        } catch (cancel: CancellationException) { message = t("Stopped. Your recording is unchanged.", "रोक दिया। आपकी रिकॉर्डिंग वैसी ही है।"); throw cancel }
                        catch (error: Exception) { message = error.message ?: t("Could not turn the speech into text. Your recording is safe.", "बात लिखाई में नहीं बदल सकी। आपकी रिकॉर्डिंग सुरक्षित है।") }
                        finally { busy = false }
                    }
                }, enabled = consent && recordingConsent != null && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("care-transcribe-turn")) { CareAssistBusy(busy); Text(if (busy) t("Turning speech into text…", "बात लिखाई में बदल रही है…") else t("Turn speech into text", "बात को लिखाई में बदलें")) }
                if (busy) TextButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Stop", "रोकें"), color = RustText) }
            }
        }
        result?.let { transcript ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DoctorHairline)
            CareAssistChip(t("Text from the recording · not checked yet", "रिकॉर्डिंग से बनी लिखाई · अभी जाँची नहीं"))
            SelectionContainerText(transcript.original)
            CareField(words, { words = it; checked = false }, t("Checked words", "जाँचे गए शब्द"), minLines = 3)
            Text(t("Speaker", "बोलने वाले"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Unknown", "Patient", "Family / carer", "Doctor").forEach { role ->
                // The stored value stays English; only the label follows the language.
                FilterChip(selected = speaker == role, onClick = { speaker = role; checked = false }, label = { Text(careSpeakerLabel(role)) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp))
            } }
            CheckLine(checked, { checked = it }, t("I listened and checked these words and who spoke, including any “no” or “not sure”.", "मैंने सुनकर ये शब्द और कौन बोला, जाँच लिया है। ‘नहीं’ और ‘पक्का नहीं’ वाली बातें भी।"))
            Button(onClick = {
                try { latestAppend(CareDraftAssist.appendReviewedTranscript(latestSource, transcript.original, words, speaker, transcript.language, checked)); result = null; checked = false }
                catch (error: IllegalArgumentException) { message = error.message.orEmpty() }
            }, enabled = checked && words.isNotBlank(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-transcript-use")) { Text(t("Add checked text", "जाँची लिखाई जोड़ें"), fontWeight = FontWeight.SemiBold) }
        }
        if (message.isNotBlank()) Text(message, fontSize = 14.sp, color = DoctorCardInk, modifier = Modifier.announce())
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
    var asked by remember(identity) { mutableStateOf(false) }
    LaunchedEffect(identity, client) {
        capabilities = try { client.capabilities(latestToken()) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
        asked = true
    }
    DisposableEffect(identity, source) { onDispose { job?.cancel() } }
    if (!asked) return
    val service = capabilities?.takeIf { it.ready }
    if (service == null) { CareAiOffNotice(); return }
    SurfaceCard(Mint) {
        CareAssistTitle(Icons.Default.AutoAwesome, t("Make a draft note from this talk", "इस बातचीत से नोट का ड्राफ़्ट बनाएँ"),
            t("${service.provider} picks lines from this talk for each section. It cannot add new words. Every line is copied as it was said.", "${service.provider} हर हिस्से के लिए इस बातचीत से पंक्तियाँ चुनता है। यह नए शब्द नहीं जोड़ सकता। हर पंक्ति वैसी ही ली जाती है जैसी कही गई।"))
        run {
            CheckLine(consent, { consent = it }, t("Everyone here agrees to send these words to ${service.provider} to make a draft. Nothing is sent until you tap the button. A person will check it before use.", "यहाँ मौजूद सभी इन शब्दों को ड्राफ़्ट बनाने के लिए ${service.provider} को भेजने पर राज़ी हैं। बटन दबाने तक कुछ नहीं भेजा जाता। इस्तेमाल से पहले कोई व्यक्ति इसे जाँचेगा।"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val snapshot = source
                    job = scope.launch {
                        busy = true; message = ""; result = null; checked = false
                        try {
                            val next = client.draft(snapshot, latestToken(), consent)
                            if (snapshot == latestSource) result = next
                        } catch (cancel: CancellationException) { message = t("Stopped. Your words are safe.", "रोक दिया। आपके शब्द सुरक्षित हैं।"); throw cancel }
                        catch (error: Exception) { message = error.message ?: t("The draft could not be made. Your words are safe.", "ड्राफ़्ट नहीं बन सका। आपके शब्द सुरक्षित हैं।") }
                        finally { busy = false }
                    }
                }, enabled = consent && source.isNotBlank() && source.length <= 12000 && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("care-assist-draft")) { CareAssistBusy(busy); Text(if (busy) t("Preparing draft…", "ड्राफ़्ट बन रहा है…") else t("Make draft", "ड्राफ़्ट बनाएँ")) }
                if (busy) TextButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Stop", "रोकें"), color = RustText) }
            }
        }
        result?.let { draft ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DoctorHairline)
            CareAssistChip(t("Draft · check each line with the talk", "ड्राफ़्ट · हर पंक्ति बातचीत से मिलाएँ"))
            careAssistFieldKeys.forEachIndexed { index, key ->
                val words = draft.fields[key].orEmpty()
                Text(NoteRules.labels[index], style = MaterialTheme.typography.labelLarge, color = DoctorCardSub)
                Text(words.ifBlank { t("Not stated in this conversation.", "इस बातचीत में नहीं कहा गया।") }, fontSize = 16.sp, color = if (words.isBlank()) DoctorCardSub else DoctorCardInk)
                CareSourceLinks(source, words)
                Spacer(Modifier.height(6.dp))
            }
            CheckLine(checked, { checked = it }, t("I checked the chosen words and who said them against the talk, including any “no” or “not sure”.", "मैंने चुने शब्द और किसने कहे, बातचीत से मिला लिए हैं। ‘नहीं’ और ‘पक्का नहीं’ वाली बातें भी।"))
            Button(onClick = {
                if (draft.sourceHash == careAssistSourceHash(latestSource)) { latestUse(draft); result = null; checked = false }
                else message = t("The conversation changed. Prepare a fresh draft.", "बातचीत बदल गई। नया ड्राफ़्ट बनाएँ।")
            }, enabled = checked && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("care-assist-use")) { Text(t("Use checked draft", "जाँचा ड्राफ़्ट इस्तेमाल करें"), fontWeight = FontWeight.SemiBold) }
        }
        if (message.isNotBlank()) Text(message, fontSize = 14.sp, color = DoctorCardInk, modifier = Modifier.announce())
    }
}

/** A translation stays separate from the original, signed version and its acknowledgement. */
@Composable fun CareTranslationPanel(
    source: String,
    versionId: String,
    token: suspend () -> String,
    client: CareRemoteAssist = remember { CareRemoteAssist() },
) { val t = careText();
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
    LaunchedEffect(versionId, client) { capabilities = try { client.capabilities(latestToken()) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null } }
    DisposableEffect(versionId, source, from, to) { onDispose { job?.cancel() } }
    val service = capabilities?.takeIf { it.translationReady } ?: return
    SurfaceCard(Mint) {
        CareAssistTitle(Icons.Default.Translate, t("Read in another language", "दूसरी भाषा में पढ़ें"),
            t("This does not replace the signed note. It does not confirm a new version.", "यह हस्ताक्षर वाले नोट की जगह नहीं लेता। यह नए संस्करण की पुष्टि नहीं करता।"))
        run {
            Text(t("Original language", "मूल भाषा"), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub)
            CareLanguageMenu(from, enabled = !busy) { from = it }
            Text(t("Translate to", "किस भाषा में"), style = MaterialTheme.typography.labelLarge, color = DoctorCardSub)
            CareLanguageMenu(to, enabled = !busy) { to = it }
            CheckLine(consent, { consent = it }, t("I agree to send this note to ${service.provider} to translate it. Nothing is sent until you tap the button. The original stays the same.", "मैं यह नोट अनुवाद के लिए ${service.provider} को भेजने पर राज़ी हूँ। बटन दबाने तक कुछ नहीं भेजा जाता। मूल नोट नहीं बदलेगा।"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val original = source; val selectedVersion = versionId
                    job = scope.launch {
                        busy = true; message = ""; result = null; reviewed = false
                        try {
                            val translated = client.translate(original, selectedVersion, from, to, latestToken(), consent)
                            if (original == latestSource && selectedVersion == latestVersion) result = translated
                        } catch (cancel: CancellationException) { message = t("Stopped. The original note is unchanged.", "रोक दिया। मूल नोट वैसा ही है।"); throw cancel }
                        catch (error: Exception) { message = error.message ?: t("Could not translate. Use the original note.", "अनुवाद नहीं हो सका। मूल नोट इस्तेमाल करें।") }
                        finally { busy = false }
                    }
                }, enabled = consent && !busy && from != to && source.isNotBlank(), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("care-translate")) { CareAssistBusy(busy); Text(if (busy) t("Translating…", "अनुवाद हो रहा है…") else t("Translate note", "नोट का अनुवाद करें")) }
                if (busy) TextButton(onClick = { job?.cancel() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Stop", "रोकें"), color = RustText) }
            }
        }
        result?.let { translated ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DoctorHairline)
            CareAssistChip(t("Translation · check it with the original", "अनुवाद · मूल से मिलाएँ"))
            SelectionContainerText(translated.translation)
            CheckLine(reviewed, { reviewed = it }, t("Someone who knows both languages checked this translation, including names, numbers and any “no” or “not sure”.", "दोनों भाषाएँ जानने वाले किसी व्यक्ति ने यह अनुवाद जाँचा है, नाम, अंक, ‘नहीं’ और ‘पक्का नहीं’ वाली बातें भी।"))
            if (reviewed) CareReadAloudControl(translated.translation, "translation-${translated.versionId}-${translated.sourceHash}-${translated.targetLanguage}", translated.targetLanguage)
        }
        if (message.isNotBlank()) Text(message, fontSize = 14.sp, color = DoctorCardInk, modifier = Modifier.announce())
    }
}

/** Shown where an AI draft would be while the server has no AI service set up (or can't be reached), so nothing looks automatic. */
internal object CareAiOffCopy {
    val title: String get() = careBilingual("Draft help is off right now", "ड्राफ़्ट मदद अभी बंद है")
    val body: String get() = careBilingual("These words are not sent anywhere. Use the suggestions above, pick lines from the talk, or type each part yourself.", "ये शब्द कहीं नहीं भेजे जाते। ऊपर के सुझाव इस्तेमाल करें, बातचीत से पंक्तियाँ चुनें, या हर हिस्सा ख़ुद लिखें।")
}

@Composable internal fun CareAiOffNotice() {
    SurfaceCard(Mint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(8.dp))
            Text(CareAiOffCopy.title, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk, modifier = Modifier.weight(1f).testTag("care-ai-off"))
        }
        Text(CareAiOffCopy.body, fontSize = 14.sp, color = DoctorCardSub)
    }
}

/** Panel title row: icon, title, and a "?" holding the one-line explanation that used to sit under it. */
@Composable internal fun CareAssistTitle(icon: ImageVector, title: String, help: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f).a11yHeading(), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, fontSize = 17.sp, color = DoctorCardInk)
        CareHelpIcon(title, help)
    }
}

/** A "not checked yet" style status shown as a badge instead of a sentence. */
@Composable internal fun CareAssistChip(text: String) {
    Surface(shape = RoundedCornerShape(50), color = SaharaSoftGold, contentColor = DoctorCardInk) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** A small spinner inside a button while its request runs. */
@Composable internal fun RowScope.CareAssistBusy(busy: Boolean) {
    if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }
}

/** Speaker values are stored in English; this is only the shown label. */
@Composable internal fun careSpeakerLabel(role: String): String { val t = careText()
    return when (role) {
        "Patient" -> t("Patient", "मरीज़"); "Family / carer" -> t("Family / carer", "परिवार / देखभाल करने वाले"); "Doctor" -> t("Doctor", "डॉक्टर")
        "Unknown" -> t("Not sure who", "पता नहीं कौन"); else -> role
    }
}
