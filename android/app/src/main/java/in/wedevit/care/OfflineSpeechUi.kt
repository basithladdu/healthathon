package `in`.wedevit.care

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
) { val t = careText();
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
        catch (failure: Exception) { error = failure.message ?: t("The saved text could not be opened. Your recording is safe.", "सहेजी लिखाई नहीं खुल सकी। आपकी रिकॉर्डिंग सुरक्षित है।") }
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
            catch (cancelled: CancellationException) { error = t("Stopped. Your recording and earlier words are safe.", "रोक दिया। आपकी रिकॉर्डिंग और पहले के शब्द सुरक्षित हैं।"); throw cancelled }
            catch (failure: Exception) { error = failure.message ?: t("Could not turn the speech into text. Your recording is safe. Try again.", "बात लिखाई में नहीं बदल सकी। आपकी रिकॉर्डिंग सुरक्षित है। फिर कोशिश करें।") }
            finally { operation = ""; task = null }
        }
    }
    SurfaceCard(Mint) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GraphicEq, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(8.dp))
            Text(t("Recording → words", "रिकॉर्डिंग → शब्द"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, fontSize = 17.sp, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfflineSpeechLanguage.entries.forEach { choice -> FilterChip(selected = language == choice.name, onClick = { language = choice.name }, enabled = !busy, label = { Text(choice.label) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).testTag("offline-language-${choice.name}")) }
        }
        if (!ready) {
            Text(t("Hindi / English language pack · 987 MB", "हिन्दी / अंग्रेज़ी भाषा पैक · 987 MB"), fontSize = 14.sp, color = DoctorCardSub, modifier = Modifier.padding(vertical = 4.dp))
            Button(onClick = { runOperation(t("Downloading language pack", "भाषा पैक डाउनलोड हो रहा है")) { models.download { done, total -> progressFlow.value = done to total }; ready = true } }, enabled = enabled && !checking && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline-model-download")) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (checking) t("Checking language pack…", "भाषा पैक जाँच रहे हैं…") else t("Download language pack", "भाषा पैक डाउनलोड करें")) }
        } else {
            Button(onClick = { runOperation(t("Turning the recording into text", "रिकॉर्डिंग लिखाई में बदल रही है")) {
                val next = engine.transcribe(file, consent, consent.personId, OfflineSpeechLanguage.valueOf(language)) { done, total -> progressFlow.value = done to total }
                result = next; words = next.originalText; checked = false
            } }, enabled = enabled && !busy && !checking, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline-transcribe")) { Icon(Icons.Default.GraphicEq, null); Spacer(Modifier.width(8.dp)); Text(if (result == null) t("Turn recording into text", "रिकॉर्डिंग को लिखाई में बदलें") else t("Turn into text again", "फिर से लिखाई में बदलें")) }
        }
        if (busy) {
            Text(color = DoctorCardInk, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), text = if (progress.second > 0) "$operation · ${(progress.first * 100 / progress.second).coerceIn(0, 100)}%" else if (operation == t("Turning the recording into text", "रिकॉर्डिंग लिखाई में बदल रही है")) t("Preparing audio…", "ऑडियो तैयार हो रहा है…") else operation)
            if (progress.second > 0) LinearProgressIndicator(progress = { (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Rust)
            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = Rust)
            TextButton(onClick = { task?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag("offline-cancel")) { Text(t("Stop", "रोकें"), color = RustText) }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp).testTag("offline-error").announce())
        result?.let { original ->
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = DoctorHairline)
            Text(t("Words the phone heard", "फ़ोन ने जो शब्द सुने"), fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
            SelectionContainer { Text(original.originalText, fontSize = 16.sp, color = DoctorCardInk) }
            CareField(value = words, onValueChange = { if (it.length <= OfflineSpeechModel.maxOriginalChars) { words = it; checked = false } }, label = t("Corrected text", "सुधारी गई लिखाई"), minLines = 4)
            if (words.length > NoteRules.MAX_SOURCE_CHARS) Text(t("Too long: ${words.length} / ${NoteRules.MAX_SOURCE_CHARS}", "बहुत लंबा: ${words.length} / ${NoteRules.MAX_SOURCE_CHARS}"), fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            CheckLine(checked, { checked = it }, t("I checked these words against the recording.", "मैंने ये शब्द रिकॉर्डिंग से मिला लिए हैं।"))
            Button(onClick = { runOperation(t("Saving checked words", "जाँचे गए शब्द सेव कर रहे हैं")) {
                val reviewed = original.reviewed(words, checked)
                engine.saveReview(file, consent, consent.personId, reviewed)
                if (latestUse(reviewed)) result = reviewed else error = t("The conversation changed. Your checked text is kept here. Open this person's conversation again.", "बातचीत बदल गई। आपकी जाँची लिखाई यहीं रखी है। इस व्यक्ति की बातचीत फिर खोलें।")
            } }, enabled = enabled && checked && words.isNotBlank() && words.length <= NoteRules.MAX_SOURCE_CHARS && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("offline-use-reviewed")) { Text(t("Use checked words", "जाँचे गए शब्द इस्तेमाल करें"), fontWeight = FontWeight.SemiBold) }
        }
    }
}
