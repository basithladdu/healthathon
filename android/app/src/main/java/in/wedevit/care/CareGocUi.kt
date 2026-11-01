package `in`.wedevit.care

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** This person's plan folder, given by CarePlanScreen, so recordings go when the person's records go. Null means no recording: typing and pasting still work. */
internal val LocalGocFolder = staticCompositionLocalOf<File?> { null }

private const val GOC_NONE = "__none__"

/** The card in the wizard's treatment step (V4 and V5 already passed there). Opens the talk over the wizard, so a long list can't scroll the recorder away. */
@Composable internal fun GocConversationCard(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    var open by rememberSaveable(d.id) { mutableStateOf(false) }
    var afterGrant by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> val run = afterGrant; afterGrant = null; if (granted) run?.invoke() }
    fun withMic(run: () -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) run()
        else { afterGrant = run; permission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    val own = d.gocTranscript?.takeIf { it.planVersion == d.version }
    val previous = GocCarry.previous(d)
    PlanCard {
        Text(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), fontWeight = FontWeight.Bold)
        Text(t("A checklist for the talk, then record or type it. The words stay on this phone. Nothing reaches the plan until you confirm it, one item at a time.", "बातचीत के लिए सूची, फिर रिकॉर्ड करें या लिखें। शब्द इसी फ़ोन पर रहते हैं। जब तक आप एक-एक करके पक्का न करें, योजना में कुछ नहीं जाता।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        if (own != null) Text(GocNote.kindLabel(own.noteKind).pick(hindi) + " · " + t("Saved with this draft", "इस मसौदे के साथ सहेजा गया"), style = MaterialTheme.typography.bodySmall)
        else if (previous != null) Text(t("An earlier version has a note. It opens as a draft for you to confirm or change.", "पिछले संस्करण में नोट है। यह मसौदे की तरह खुलेगा, ताकि आप पक्का करें या बदलें।"), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-open")) { Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(t("Open the conversation", "बातचीत खोलें")) }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize().imePadding()) { GocConversationPage(d, update, LocalGocFolder.current, ::withMic, onClose = { open = false }) }
    }
}

/** Beside the signature step: the signer sees the note the conversation left. The verbatim words stay out of it. */
@Composable internal fun GocNoteForSigning(d: EctprRecord) {
    val t = careText(); val hindi = careHindi()
    val g = d.gocTranscript?.takeIf { it.planVersion == d.version && it.note.isNotBlank() } ?: return
    PlanCard {
        Text(t("Goals-of-care note", "देखभाल के लक्ष्यों का नोट"), fontWeight = FontWeight.Bold)
        Text(GocNote.kindLabel(g.noteKind).pick(hindi), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        Text(g.note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("goc-signing-note"))
        Text(t("The word-for-word transcript is kept separately. It is not on the emergency card.", "शब्द-दर-शब्द लिखाई अलग रखी है। यह आपातकालीन कार्ड पर नहीं जाती।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
    }
}

@Composable private fun GocConversationPage(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit, folder: File?, withMic: (() -> Unit) -> Unit, onClose: () -> Unit) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val scope = rememberCoroutineScope(); val owner = LocalLifecycleOwner.current; val view = LocalView.current
    val recorder = remember(folder, d.patientId) { folder?.let { GocRecorder(context, it, d.patientId, scope) } }
    val engine = remember { GocSpeechEngine(context) }
    val draftFile = remember(folder, d.id) { folder?.let { GocDraftFile(it, d.id) } }
    val restored = remember(d.id) { draftFile?.read() }
    val initial = remember(d.id) { restored?.transcript ?: d.gocTranscript?.takeIf { it.planVersion == d.version } }

    var participants by rememberSaveable(d.id) { mutableStateOf(initial?.participants.orEmpty()) }
    var consentAt by rememberSaveable(d.id) { mutableStateOf(initial?.consentAt ?: 0L) }
    val agreements = remember { mutableStateMapOf<String, Boolean>() }
    var purpose by rememberSaveable { mutableStateOf(false) }; var storage by rememberSaveable { mutableStateOf(false) }; var access by rememberSaveable { mutableStateOf(false) }
    var lines by remember { mutableStateOf(initial?.lines.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note ?: GocCarry.startingNote(d)) }
    var topics by remember { mutableStateOf(initial?.topics.orEmpty().associateBy { it.decision }) }
    var ticked by remember { mutableStateOf(restored?.ticked.orEmpty()) }
    var language by rememberSaveable(d.id) { mutableStateOf(GocSpeechLanguage.entries.firstOrNull { it.code == initial?.language && it.code.isNotEmpty() }?.name ?: GocSpeechLanguage.AUTO.name) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var typed by rememberSaveable { mutableStateOf("") }
    var analysed by remember { mutableStateOf(if (initial?.lines.isNullOrEmpty()) "" else GocLines.text(initial!!.lines)) }
    val pending = remember { mutableStateMapOf<GocDecision, String>() }
    var editing by remember { mutableStateOf(-1) }
    var keepAudio by rememberSaveable { mutableStateOf(false) }; var keepTranscript by rememberSaveable { mutableStateOf(true) }
    var deleting by remember { mutableStateOf<DoctorAudioClip?>(null) }
    var ready by remember { mutableStateOf(false) }; var checking by remember { mutableStateOf(true) }
    var operation by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var task by remember { mutableStateOf<Job?>(null) }
    val progressFlow = remember { MutableStateFlow(0L to 0L) }; val progress by progressFlow.collectAsState()
    var firstTick by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val recorderState = recorder?.state?.collectAsState()?.value ?: GocRecorderState()

    val names = RecordingConsentRules.names(participants)
    val text = GocLines.text(lines)
    val extraction = remember(analysed) { GocExtract.extract(analysed) }
    val stale = text != analysed
    val busy = operation.isNotBlank()
    val next = GocPlan.next(extraction, ticked, topics)

    fun build(now: String = "", kept: Boolean = false) = GocTranscript(
        lines = lines.filter { it.text.isNotBlank() }, language = GocSpeechLanguage.valueOf(language).code,
        source = if (model.isNotBlank()) GocTranscript.SOURCE_RECORDED else GocTranscript.SOURCE_TYPED,
        model = model, participants = names.joinToString("\n"), consentAt = consentAt, planVersion = d.version,
        topics = topics.values.sortedBy { it.decision.ordinal }, note = note, noteKind = GocNote.kind(topics.values), reviewedAt = now, reviewedBy = if (now.isBlank()) "" else d.createdBy, audioKept = kept,
    )
    LaunchedEffect(Unit) { try { ready = engine.models.ready() } catch (c: CancellationException) { throw c } catch (_: Exception) { ready = false } finally { checking = false } }
    // Autosave, so a killed app or a long job loses nothing the doctor already corrected.
    LaunchedEffect(lines, note, topics, ticked, consentAt, participants) {
        if (consentAt > 0 && draftFile != null) { delay(1200); val snapshot = GocDraft(build(), ticked); withContext(Dispatchers.IO) { draftFile.write(snapshot) } }
    }
    DisposableEffect(recorder, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { recorder?.stop(); recorder?.stopPlayback(); task?.cancel() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder?.close(); task?.cancel() }
    }
    val awake = recorderState.recording || busy
    DisposableEffect(awake) { view.keepScreenOn = awake; onDispose { view.keepScreenOn = false } }

    fun run(label: String, action: suspend () -> Unit) {
        if (busy) return
        operation = label; error = ""; progressFlow.value = 0L to 0L; firstTick = null
        task = scope.launch {
            try { action() }
            catch (c: CancellationException) { error = t("Stopped. What was read is kept, and it continues from there.", "रोक दिया। जितना पढ़ा गया वह रखा है, और यहीं से आगे चलेगा।"); throw c }
            catch (e: Exception) { error = e.message ?: t("That did not work. Your recording is safe. Try again, or type the words.", "यह नहीं हुआ। आपकी रिकॉर्डिंग सुरक्षित है। फिर कोशिश करें, या शब्द लिखें।") }
            finally { operation = ""; task = null }
        }
    }
    val readingLabel = t("Reading the recording", "रिकॉर्डिंग पढ़ी जा रही है")
    fun transcribe(clip: DoctorAudioClip) = run(readingLabel) {
        recorder?.stopPlayback()
        engine.transcribe(clip, d.patientId, GocSpeechLanguage.valueOf(language), { done, total -> progressFlow.value = done to total; if (firstTick == null && done > 0) firstTick = SystemClock.elapsedRealtime() to done }) { segment ->
            scope.launch {
                val key = gocSegmentKey(clip.id, segment.startMs)
                if (segment.text.isNotBlank() && lines.none { it.key.startsWith("$key:") }) lines = lines + GocLines.fromText(segment.text, key)
            }
        }
        model = OfflineWhisperModel.id
    }
    fun confirm(decision: GocDecision, choice: String) {
        val id = choice.takeUnless { it == GOC_NONE }
        val quotes = id?.let { extraction.suggested(decision)[it] }.orEmpty().map { it.quote }
        update { GocPlan.apply(it, decision, id, quotes) }
        topics = topics + (decision to GocTopicRecord(decision, GocTopicStatus.CONFIRMED, id, quotes.firstOrNull().orEmpty(), planNow()))
        pending.remove(decision)
    }
    fun finish() {
        val kept = keepAudio && recorderState.clips.isNotEmpty()
        val saved = build(planNow(), kept).let { if (keepTranscript) it else it.copy(lines = emptyList()) }
        update { it.copy(gocTranscript = saved) }
        if (!kept) recorderState.clips.forEach { recorder?.delete(it) }
        draftFile?.delete()
        onClose()
    }

    LazyColumn(Modifier.fillMaxSize().testTag("goc-page"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") { PlanHeader(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), onClose, "goc-header") }
        item(key = "notice") {
            Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(t("Draft. Sahara matches words only: it does not recommend any treatment. A topic not discussed stays as No preference recorded.", "मसौदा। सहारा सिर्फ़ शब्द मिलाता है: यह कोई इलाज नहीं सुझाता। जिस विषय पर बात नहीं हुई वह 'कोई पसंद दर्ज नहीं' ही रहता है।"), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (consentAt <= 0) item(key = "consent") {
            PlanCard {
                Text(t("Before you begin", "शुरू करने से पहले"), fontWeight = FontWeight.Bold)
                Text(t("List everyone present. Each person must agree.", "सभी मौजूद लोगों के नाम लिखें। हर व्यक्ति की सहमति ज़रूरी है।"))
                CareField(participants, { participants = it.take(600) }, t("Names of everyone present", "सभी मौजूद लोगों के नाम"), supporting = t("Separate names with commas or new lines", "नाम कॉमा या नई पंक्ति से अलग करें"))
                names.forEach { name -> CheckLine(agreements[name] == true, { agreements[name] = it }, name + " · " + t("agrees", "सहमत")) }
                CheckLine(purpose, { purpose = it }, t("We understand this is to write down what was discussed, for the doctor to check.", "हम समझते हैं कि यह चर्चा लिखने के लिए है, जिसे डॉक्टर जाँचेंगे।"))
                CheckLine(storage, { storage = it }, t("Audio and words stay on this phone. Nothing is uploaded. The audio is deleted after review unless the doctor keeps it.", "ऑडियो और शब्द इसी फ़ोन पर रहते हैं। कुछ अपलोड नहीं होता। जाँच के बाद ऑडियो मिट जाता है, जब तक डॉक्टर उसे न रखें।"))
                CheckLine(access, { access = it }, t("People with access to this phone may read its records.", "इस फ़ोन तक पहुँच वाले लोग इसके रिकॉर्ड पढ़ सकते हैं।"))
                Button(onClick = { consentAt = System.currentTimeMillis() }, enabled = names.isNotEmpty() && names.all { agreements[it] == true } && purpose && storage && access, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-consent")) { Text(t("Everyone agrees", "सभी सहमत हैं")) }
            }
        }
        if (consentAt > 0) {
            item(key = "checklist") {
                PlanCard {
                    Text(t("Topics to cover", "किन विषयों पर बात करनी है"), fontWeight = FontWeight.Bold)
                    next?.let { n ->
                        Surface(color = Mint, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().testTag("goc-next-topic")) {
                            Column(Modifier.padding(12.dp)) { Text(t("Next", "अगला") + ": " + GocPlan.title(n).pick(hindi), fontWeight = FontWeight.SemiBold); Text(GocPlan.prompt(n).pick(hindi), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    GocPlan.ORDER.forEach { topic ->
                        val status = GocPlan.status(topic, extraction, ticked, topics)
                        CheckLine(status != GocChecklistStatus.NOT_DISCUSSED, { on -> ticked = if (on) ticked + topic else ticked - topic }, GocPlan.title(topic).pick(hindi) + " · " + checklistLabel(status).pick(hindi))
                    }
                    Text(t("Tick a topic as you cover it. The list is also checked against the transcript. You may skip any topic.", "बात होते ही विषय पर निशान लगाएँ। सूची लिखाई से भी मिलाई जाती है। आप कोई भी विषय छोड़ सकते हैं।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                }
            }
            item(key = "capture") {
                PlanCard {
                    Text(t("Record or type", "रिकॉर्ड करें या लिखें"), fontWeight = FontWeight.Bold)
                    if (recorder != null) {
                        Button(onClick = { if (recorderState.recording) recorder.stop() else withMic { recorder.start(participants, names.toSet(), purpose = true, storage = true, access = true) } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-record")) {
                            Icon(if (recorderState.recording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp))
                            Text(if (recorderState.recording) t("Stop and save", "रोकें और सहेजें") + " · " + clock(recorderState.seconds) else t("Start recording", "रिकॉर्डिंग शुरू करें"))
                        }
                        if (recorderState.recording) LinearProgressIndicator(progress = { recorderState.level.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Rust)
                        Text(t("Up to 90 minutes in one recording. Keep this screen open: leaving the app stops and saves the part, then record the next part.", "एक रिकॉर्डिंग में 90 मिनट तक। यह स्क्रीन खुली रखें: ऐप से बाहर जाने पर हिस्सा रुककर सेव हो जाता है, फिर अगला हिस्सा रिकॉर्ड करें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        recorderState.error?.let { Text(it.pick(hindi), color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
                        recorderState.clips.forEachIndexed { index, clip ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { recorder.play(clip) }, enabled = !recorderState.recording && !busy, modifier = Modifier.testTag("goc-play-$index")) { Icon(if (recorderState.playing == clip.id) Icons.Default.Stop else Icons.Default.PlayArrow, t("Play or stop the recording", "रिकॉर्डिंग चलाएँ या रोकें")) }
                                Text(t("Part", "हिस्सा") + " ${index + 1} · ${clip.file.length() / (1024 * 1024)} MB", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { transcribe(clip) }, enabled = ready && !busy && !recorderState.recording, modifier = Modifier.testTag("goc-transcribe-$index")) { Icon(Icons.Default.GraphicEq, null); Spacer(Modifier.width(4.dp)); Text(if (engine.hasProgress(clip)) t("Continue", "आगे पढ़ें") else t("Read", "पढ़ें")) }
                                IconButton(onClick = { deleting = clip }, enabled = !busy && !recorderState.recording) { Icon(Icons.Default.DeleteOutline, t("Delete recording", "रिकॉर्डिंग मिटाएँ")) }
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GocSpeechLanguage.entries.forEach { choice -> FilterChip(selected = language == choice.name, onClick = { language = choice.name }, enabled = !busy, label = { Text(if (choice == GocSpeechLanguage.AUTO) t("Detect language", "भाषा पहचानें") else choice.label) }, modifier = Modifier.testTag("goc-language-${choice.name}")) }
                        }
                        Text(t("Pick the main language if the words come out wrong. Telugu, Urdu and Kannada are less accurate than English: check every line.", "शब्द ग़लत आएँ तो मुख्य भाषा चुनें। तेलुगु, उर्दू और कन्नड़ में अंग्रेज़ी से कम सटीकता है: हर पंक्ति जाँचें।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        if (!ready) {
                            Text(t("Speech pack · 375 MB · for English, Hindi, Telugu, Urdu and Kannada. Download once, on Wi-Fi if you can. Then it works without internet.", "स्पीच पैक · 375 MB · अंग्रेज़ी, हिन्दी, तेलुगु, उर्दू और कन्नड़ के लिए। एक बार डाउनलोड करें, हो सके तो Wi-Fi पर। फिर बिना इंटरनेट चलेगा।"), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { run(t("Downloading the speech pack", "स्पीच पैक डाउनलोड हो रहा है")) { engine.models.download { done, total -> progressFlow.value = done to total }; ready = true } }, enabled = !checking && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-model-download")) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (checking) t("Checking the speech pack…", "स्पीच पैक जाँच रहे हैं…") else t("Download the speech pack", "स्पीच पैक डाउनलोड करें")) }
                        }
                    }
                    if (busy) {
                        val percent = if (progress.second > 0) (progress.first * 100 / progress.second).coerceIn(0, 100) else 0
                        Text(if (progress.second > 0) "$operation · $percent%" else if (operation == readingLabel) t("Preparing the audio…", "ऑडियो तैयार हो रहा है…") else operation)
                        if (progress.second > 0) LinearProgressIndicator(progress = { (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                        val tick = firstTick
                        if (operation == readingLabel && tick != null && progress.first > tick.second) {
                            val n = (SystemClock.elapsedRealtime() - tick.first) * (progress.second - progress.first) / (progress.first - tick.second) / 60_000 + 1
                            Text(t("About $n minutes left. Keep the screen open and the phone charging.", "लगभग $n मिनट बाकी। स्क्रीन खुली और फ़ोन चार्ज पर रखें।"), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { task?.cancel() }, modifier = Modifier.testTag("goc-cancel")) { Text(t("Stop", "रोकें")) }
                    }
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("goc-error").announce())
                    CareField(typed, { typed = it.take(OfflineSpeechModel.maxOriginalChars) }, t("Type or paste the conversation", "बातचीत लिखें या चिपकाएँ"), minLines = 3)
                    OutlinedButton(onClick = { lines = lines + GocLines.fromText(typed); typed = "" }, enabled = typed.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-add-typed")) { Text(t("Add to the transcript", "लिखाई में जोड़ें")) }
                }
            }
            if (lines.isNotEmpty()) {
                item(key = "transcript-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("Transcript · draft", "लिखाई · मसौदा"), fontWeight = FontWeight.Bold)
                        Text(t("Word for word, kept apart from the note. Check it against the audio. Tap a label to set doctor side or patient party. Sahara does not identify speakers.", "शब्द-दर-शब्द, नोट से अलग रखी। ऑडियो से मिलाएँ। लेबल दबाकर डॉक्टर की ओर या मरीज़ की ओर चुनें। सहारा बोलने वालों को नहीं पहचानता।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                    }
                }
                itemsIndexed(lines, key = { index, _ -> "line-$index" }) { index, line ->
                    Column(Modifier.fillMaxWidth().testTag("goc-line-$index")) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = line.party != GocParty.UNSET, onClick = { lines = lines.toMutableList().also { it[index] = line.copy(party = GocLines.nextParty(line.party), guessed = false) } }, label = { Text(GocLines.label(line.party).pick(hindi) + if (line.guessed) " · " + t("guess", "अनुमान") else "") }, modifier = Modifier.testTag("goc-party-$index"))
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { editing = if (editing == index) -1 else index }) { Icon(Icons.Default.Edit, t("Edit this line", "यह पंक्ति बदलें")) }
                        }
                        if (editing == index) CareField(line.text, { v -> lines = lines.toMutableList().also { it[index] = line.copy(text = v) } }, t("Correct this line", "यह पंक्ति सुधारें"))
                        else Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item(key = "find") {
                    Button(onClick = { analysed = text }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-find")) { Text(if (analysed.isBlank()) t("Find what was discussed", "देखें किस पर बात हुई") else if (stale) t("The transcript changed. Find again", "लिखाई बदली। फिर देखें") else t("Find again", "फिर देखें")) }
                }
            }
            if (lines.isNotEmpty() || note.isNotBlank()) {
                item(key = "review-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t("Confirm each item", "हर बिंदु पक्का करें"), fontWeight = FontWeight.Bold)
                        Text(t("Every choice is shown. A mark only means those words were heard. Nothing is selected until you choose it and confirm.", "हर विकल्प दिखाया गया है। निशान का मतलब बस इतना है कि वे शब्द सुने गए। जब तक आप चुनकर पक्का न करें, कुछ नहीं चुना जाता।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                    }
                }
                items(GocPlan.ORDER, key = { "decision-${it.name}" }) { decision ->
                    GocDecisionCard(d, decision, extraction, lines.takeIf { !stale }, topics, pending[decision], { pending[decision] = it }, { confirm(decision, it) }, { on ->
                        topics = if (on) topics + (decision to GocTopicRecord(decision, GocTopicStatus.NOT_DISCUSSED_VERIFIED, at = planNow())) else topics - decision
                    })
                }
                item(key = "note") {
                    PlanCard {
                        Text(t("Note for the doctor to edit", "डॉक्टर के बदलने के लिए नोट"), fontWeight = FontWeight.Bold)
                        Text(GocNote.kindLabel(GocNote.kind(topics.values)).pick(hindi), style = MaterialTheme.typography.bodySmall)
                        if (note.isNotBlank() && note == GocCarry.startingNote(d)) Text(t("This is the earlier version's note, as a draft. Confirm or change it.", "यह पिछले संस्करण का नोट है, मसौदे की तरह। इसे पक्का करें या बदलें।"), color = RustText, style = MaterialTheme.typography.bodySmall)
                        Text(t("It says what was said and decided. Spoken words and feelings stay in the transcript.", "इसमें वही है जो कहा और तय हुआ। बोले गए शब्द और भावनाएँ लिखाई में रहते हैं।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        OutlinedButton(onClick = { note = GocNote.draft(d, topics, names.joinToString("\n"), hindi) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-build-note")) { Text(t("Build the note from the items above", "ऊपर के बिंदुओं से नोट बनाएँ")) }
                        CareField(note, { note = it.take(8000) }, t("Note", "नोट"), minLines = 5)
                    }
                }
                item(key = "finish") {
                    PlanCard {
                        val open = GocPlan.ORDER.count { it !in topics }
                        if (open > 0) { val n = open; Text(t("$n topics are not confirmed or verified yet. The note will say so.", "$n विषय अभी पक्के या जाँचे नहीं गए। नोट में यह लिखा जाएगा।"), style = MaterialTheme.typography.bodySmall, color = RustText) }
                        if (recorderState.clips.isNotEmpty()) CheckLine(keepAudio, { keepAudio = it }, t("Keep the recording on this phone (otherwise it is deleted now)", "रिकॉर्डिंग इस फ़ोन पर रखें (नहीं तो अभी मिट जाएगी)"))
                        CheckLine(keepTranscript, { keepTranscript = it }, t("Save the word-for-word transcript with this draft", "शब्द-दर-शब्द लिखाई इस मसौदे के साथ सहेजें"))
                        Text(t("The transcript is never put on the emergency card or its QR code. Next: review date, then signatures.", "लिखाई कभी आपातकालीन कार्ड या उसके QR कोड पर नहीं जाती। आगे: समीक्षा की तारीख़, फिर हस्ताक्षर।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
                        Button(onClick = ::finish, enabled = !busy && !recorderState.recording && (text.isNotBlank() || note.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-finish")) { Text(t("Finish review", "जाँच पूरी करें")) }
                    }
                }
            }
        }
    }
    deleting?.let { clip -> AlertDialog(onDismissRequest = { deleting = null }, containerColor = Paper, title = { Text(t("Delete this recording?", "यह रिकॉर्डिंग मिटाएँ?")) }, text = { Text(t("The words already in the transcript are kept.", "लिखाई में आ चुके शब्द रहेंगे।")) },
        confirmButton = { TextButton(onClick = { recorder?.delete(clip); deleting = null }) { Text(t("Delete", "मिटाएँ")) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text(t("Keep recording", "रिकॉर्डिंग रखें")) } }) }
}

private fun clock(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun checklistLabel(s: GocChecklistStatus): Bi = when (s) {
    GocChecklistStatus.NOT_DISCUSSED -> Bi("Not discussed", "बात नहीं हुई")
    GocChecklistStatus.MENTIONED -> Bi("Discussed, no clear choice", "बात हुई, साफ़ फ़ैसला नहीं")
    GocChecklistStatus.SUGGESTED -> Bi("Discussed, needs your check", "बात हुई, आपकी जाँच बाकी")
    GocChecklistStatus.CONFIRMED -> Bi("Confirmed", "पक्का")
    GocChecklistStatus.VERIFIED_NOT_DISCUSSED -> Bi("Not discussed, verified", "बात नहीं हुई, जाँचा गया")
}

@Composable private fun GocDecisionCard(d: EctprRecord, decision: GocDecision, extraction: GocExtraction, lines: List<GocLine>?, topics: Map<GocDecision, GocTopicRecord>, picked: String?, onPick: (String) -> Unit, onConfirm: (String) -> Unit, onVerify: (Boolean) -> Unit) {
    val t = careText(); val hindi = careHindi()
    val suggested = extraction.suggested(decision)
    val ruledOut = extraction.ruledOut(decision)
    val topic = topics[decision]
    val options = GocPlan.options(decision)
    val current = GocPlan.current(d, decision)
    val carried = GocCarry.carried(d, decision, topics)
    PlanCard {
        Text(GocPlan.title(decision).pick(hindi), fontWeight = FontWeight.SemiBold, modifier = Modifier.a11yHeading().testTag("goc-decision-${decision.name}"))
        if (decision != GocDecision.VALUES) {
            val now = options.firstOrNull { it.id == current }?.label ?: NO_PREFERENCE
            Text(if (carried) t("From the previous version", "पिछले संस्करण से") + ": " + now.pick(hindi) + ". " + t("Confirm it or change it.", "इसे पक्का करें या बदलें।") else t("Now in the plan", "अभी योजना में") + ": " + now.pick(hindi), style = MaterialTheme.typography.bodySmall, color = if (carried) RustText else DoctorCardSub)
        }
        if (suggested.size > 1) Text(t("Different things were said. Read the words and decide.", "अलग-अलग बातें कही गईं। शब्द पढ़कर तय करें।"), style = MaterialTheme.typography.bodySmall, color = RustText)
        if (extraction.hitsFor(decision).isEmpty()) extraction.mentionsFor(decision).firstOrNull()?.let { m ->
            Text(t("Came up, with no clear choice", "बात आई, पर साफ़ फ़ैसला नहीं"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(m.quote, style = MaterialTheme.typography.bodySmall)
        }
        PlanOption(if (decision == GocDecision.VALUES) t("Leave the What matters section as it is", "'क्या ज़रूरी है' हिस्सा जैसा है वैसा रखें") else NO_PREFERENCE.pick(hindi), null, picked == GOC_NONE, "goc-${decision.name}-none") { onPick(GOC_NONE) }
        options.forEach { option ->
            val heard = suggested[option.id].orEmpty()
            // The Values choices only add words that were heard, so they need a quote to add.
            val usable = decision != GocDecision.VALUES || heard.isNotEmpty()
            PlanOption(option.label.pick(hindi), option.meaning?.pick(hindi), picked == option.id, "goc-${decision.name}-${option.id}") { if (usable) onPick(option.id) }
            if (heard.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 12.dp).testTag("goc-suggested-${decision.name}-${option.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Marked: these words were heard", "निशान: ये शब्द सुने गए"), color = RustText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                heard.forEach { h ->
                    val party = lines?.let { GocLines.partyAt(it, h.start) } ?: GocParty.UNSET
                    Text((if (party == GocParty.UNSET) "" else GocLines.label(party).pick(hindi) + ": ") + "“" + h.quote + "”", style = MaterialTheme.typography.bodySmall)
                }
            }
            val refused = ruledOut[option.id].orEmpty()
            if (refused.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 12.dp).testTag("goc-ruledout-${decision.name}-${option.id}"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t("Marked: they said no to this", "निशान: इसके लिए मना किया"), color = RustText, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                refused.forEach { h ->
                    val party = lines?.let { GocLines.partyAt(it, h.start) } ?: GocParty.UNSET
                    Text((if (party == GocParty.UNSET) "" else GocLines.label(party).pick(hindi) + ": ") + "\u201C" + h.quote + "\u201D", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (carried && option.id == current) Text(t("Previous version's choice", "पिछले संस्करण का फ़ैसला"), color = RustText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
        }
        picked?.let { choice -> Button(onClick = { onConfirm(choice) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("goc-confirm-${decision.name}")) { Text(t("Confirm this item", "यह बिंदु पक्का करें")) } }
        if (topic?.status == GocTopicStatus.CONFIRMED) Text(t("Confirmed by you", "आपने पक्का किया"), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("goc-confirmed-${decision.name}"))
        if (extraction.hitsFor(decision).isEmpty() && topic?.status != GocTopicStatus.CONFIRMED) {
            val stays = if (decision == GocDecision.VALUES) null else options.firstOrNull { it.id == current }?.label ?: NO_PREFERENCE
            CheckLine(topic?.status == GocTopicStatus.NOT_DISCUSSED_VERIFIED, onVerify, t("Not discussed in this talk. I checked this.", "इस बातचीत में बात नहीं हुई। मैंने जाँच लिया।") + (stays?.let { " " + t("It stays", "यह रहेगा") + ": " + it.pick(hindi) } ?: ""))
        }
    }
}
