package `in`.wedevit.care

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.LocalDate

/** This person's plan folder, given by CarePlanScreen, so recordings go when the person's records go. Null means no recording: typing and pasting still work. */
internal val LocalGocFolder = staticCompositionLocalOf<File?> { null }

private const val GOC_NONE = "__none__"
private enum class GocStage { CAPTURE, REVIEW, NOTE }
private val GocSand = Color(0xFFF6ECE1)
private val GocTickSoft = Color(0xFFEEF5DC)

/** The card in the wizard's treatment step (V4 and V5 already passed there). Opens the talk over the wizard. */
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
    PlanCard {
        Text(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), fontWeight = FontWeight.Bold)
        own?.let { g -> Text(GocNote.kindLabel(g.noteKind).pick(hindi) + " · " + (if (g.signOff != null) t("Signed and locked", "साइन होकर बंद") else t("Saved with this draft", "इस मसौदे के साथ सहेजा गया")), style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { open = true }, colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-open")) {
            Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (own != null) t("Open the conversation", "बातचीत खोलें") else t("Record the conversation", "बातचीत रिकॉर्ड करें"))
        }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize().imePadding()) { GocConversationPage(d, update, LocalGocFolder.current, ::withMic, onClose = { open = false }) }
    }
}

/** Beside the signature step: the signer sees the note the conversation left, in the legal form's layout. The verbatim words stay out of it. */
@Composable internal fun GocNoteForSigning(d: EctprRecord) {
    val t = careText(); val hindi = careHindi()
    val g = d.gocTranscript?.takeIf { it.planVersion == d.version && it.note.isNotBlank() } ?: return
    PlanCard {
        Text(t("Goals-of-care note", "देखभाल के लक्ष्यों का नोट"), fontWeight = FontWeight.Bold)
        Text(GocNote.kindLabel(g.noteKind).pick(hindi), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub)
        g.signOff?.let { so -> Text(t("Signed and locked", "साइन होकर बंद") + ": " + so.signers.joinToString(", ") + " · " + so.lockedAt.take(16).replace('T', ' '), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            ?: Text(t("The note is not signed yet.", "नोट पर अभी हस्ताक्षर नहीं हुए।"), style = MaterialTheme.typography.bodySmall, color = RustText)
        GocFormView(GocForm.sections(d, g.topics.associateBy { it.decision }, g.signOff?.formSigners().orEmpty()), Modifier.testTag("goc-signing-note"))
    }
}

/** The legal form on screen: a card per section with its roman numeral, the form's headings, filled blanks in bold, "Not discussed" muted, and the form's boxes and tables. */
@Composable internal fun GocFormView(sections: List<GocFormSection>, modifier: Modifier = Modifier) { val hindi = careHindi()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        sections.forEach { section ->
            Surface(color = Paper, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("goc-form-section-${section.id}")) {
                Column {
                    Row(Modifier.fillMaxWidth().background(GocSand).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(Rust, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(section.id, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                        Spacer(Modifier.width(12.dp))
                        Text(section.title.pick(hindi), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    }
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        section.groups.forEach { g ->
                            if (g.title.en.isNotBlank()) Text(g.title.pick(hindi), color = RustText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                            g.rows.forEach { row -> GocFormRowView(row, hindi) }
                            g.table?.let { GocFormTableView(it, hindi) }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun GocFormRowView(row: GocFormRow, hindi: Boolean) {
    Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (row.label.en.isNotBlank()) Text(row.label.pick(hindi), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub)
        if (row.value.en.isNotBlank()) Text(row.value.pick(hindi), fontSize = 15.sp, fontWeight = if (row.open) FontWeight.Normal else FontWeight.Bold,
            fontStyle = if (row.open) FontStyle.Italic else FontStyle.Normal, color = if (row.open) DoctorCardSub else DoctorCardInk)
        row.ticks.forEach { tick -> GocTickLine(tick.on, tick.text.pick(hindi) + if (tick.detail.isBlank()) "" else " " + tick.detail) }
    }
}

@Composable private fun GocTickLine(on: Boolean, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.Top) {
        Text(if (on) GocForm.TICK_ON else GocForm.TICK_OFF, fontSize = 17.sp, color = if (on) Rust else DoctorCardSub, modifier = Modifier.width(24.dp))
        Text(text.trimStart(), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) DoctorCardInk else DoctorCardSub,
            modifier = Modifier.padding(start = if (text.startsWith("    ")) 16.dp else 0.dp))
    }
}

/** A table of the form. Three narrow columns on a phone stay readable; wider tables scroll sideways like the paper. */
@Composable private fun GocFormTableView(table: GocFormTable, hindi: Boolean) {
    val n = table.head.size
    val weights = when {
        n == 3 && table.head[0].en == "LEVEL" -> listOf(0.6f, 3.6f, 1f)
        n == 3 -> listOf(1.1f, 1f, 1f)
        n == 4 -> listOf(1.8f, 1f, 1f, 0.8f)
        else -> List(n) { 1f }
    }
    val wide = n >= 5
    Column((if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()).background(Color.White, RoundedCornerShape(12.dp)).padding(4.dp)) {
        (listOf(table.head) + table.rows).forEachIndexed { index, cells ->
            Row((if (wide) Modifier.widthIn(min = 560.dp) else Modifier.fillMaxWidth()).background(if (index == 0) GocSand else Color.Transparent)) {
                cells.forEachIndexed { i, cell ->
                    Column((if (wide) Modifier.width(112.dp) else Modifier.weight(weights.getOrElse(i) { 1f })).padding(6.dp)) {
                        cell.pick(hindi).lines().filter { it.isNotEmpty() || cells.size == 1 }.forEach { line ->
                            when {
                                line.startsWith(GocForm.TICK_ON) || line.startsWith(GocForm.TICK_OFF) -> GocTickLine(line.startsWith(GocForm.TICK_ON), line.drop(1).trim())
                                else -> Text(line, fontSize = if (index == 0) 12.sp else 13.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (line == GocForm.NOT_DISCUSSED.pick(hindi)) FontStyle.Italic else FontStyle.Normal,
                                    color = if (line == GocForm.NOT_DISCUSSED.pick(hindi)) DoctorCardSub else DoctorCardInk)
                            }
                        }
                    }
                }
            }
        }
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
    var lines by remember { mutableStateOf(initial?.lines.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var topics by remember { mutableStateOf(initial?.topics.orEmpty().associateBy { it.decision }) }
    var stage by rememberSaveable(d.id) { mutableStateOf(when { initial?.note?.isNotBlank() == true -> GocStage.NOTE; initial?.lines?.isNotEmpty() == true -> GocStage.REVIEW; else -> GocStage.CAPTURE }) }
    var language by rememberSaveable(d.id) { mutableStateOf(GocSpeechLanguage.entries.firstOrNull { it.code == initial?.language && it.code.isNotEmpty() }?.name ?: GocSpeechLanguage.AUTO.name) }
    var askLanguage by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var typing by remember { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf(-1) }
    var operation by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }; var task by remember { mutableStateOf<Job?>(null) }
    val progressFlow = remember { MutableStateFlow(0L to 0L) }; val progress by progressFlow.collectAsState()
    val recorderState = recorder?.state?.collectAsState()?.value ?: GocRecorderState()
    var signOff by remember { mutableStateOf(initial?.signOff) }
    var transcribedBy by remember { mutableStateOf(initial?.transcribedBy.orEmpty()) }
    var lastClip by remember { mutableStateOf<DoctorAudioClip?>(null) }
    var gemini by remember { mutableStateOf(false) }
    var askDoctor by remember { mutableStateOf(false) }
    var guideTicked by remember { mutableStateOf(setOf<GocGuideItem>()) }
    val signStore = remember(d.patientId) { GocSignOffStore(context, "goc-" + d.patientId.filter { it.isLetterOrDigit() }.ifBlank { "guest" }) }

    val names = RecordingConsentRules.names(participants)
    // Everyone in the room agreed on one tick; names are optional, so the consent sidecar records "Everyone present" when none were typed.
    val consentNames = names.ifEmpty { listOf(t("Everyone present", "सभी मौजूद लोग")) }
    val text = GocLines.text(lines)
    val extraction = remember(text) { GocExtract.extract(text) }
    val busy = operation.isNotBlank()
    val covered = remember(text) { GocGuide.covered(text) }
    val undiscussed = GocGuide.undiscussed(extraction, covered, guideTicked, topics, consentAt > 0)
    val suggestions = remember(extraction, lines, topics) { GocSuggest.all(extraction, lines).filter { (dd, id, _) -> topics[dd]?.let { it.status == GocTopicStatus.CONFIRMED && (dd != GocDecision.VALUES || it.optionId == id) } != true } }

    fun build(now: String = "") = GocTranscript(
        lines = lines.filter { it.text.isNotBlank() }, language = GocSpeechLanguage.valueOf(language).code,
        source = if (model.isNotBlank()) GocTranscript.SOURCE_RECORDED else GocTranscript.SOURCE_TYPED,
        model = model, participants = names.joinToString("\n"), consentAt = consentAt, planVersion = d.version,
        topics = topics.values.sortedBy { it.decision.ordinal }, note = note, noteKind = GocNote.kind(topics.values), reviewedAt = now, reviewedBy = if (now.isBlank()) "" else d.createdBy, audioKept = false,
        signOff = signOff, transcribedBy = transcribedBy,
    )
    LaunchedEffect(Unit) { gemini = gocOnlineEngines().gemini }
    // Autosave, so a killed app or a long job loses nothing the doctor already corrected.
    LaunchedEffect(lines, note, topics, consentAt, participants) {
        if (consentAt > 0 && draftFile != null) { delay(1200); val snapshot = GocDraft(build(), emptySet()); withContext(Dispatchers.IO) { draftFile.write(snapshot) } }
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
        operation = label; error = ""; progressFlow.value = 0L to 0L
        task = scope.launch {
            try { action() }
            catch (c: CancellationException) { error = t("Stopped. What was read is kept, and it continues from there.", "रोक दिया। जितना पढ़ा गया वह रखा है, और यहीं से आगे चलेगा।"); throw c }
            catch (e: Exception) { error = e.message ?: t("That did not work. Your recording is safe. Try again, or type the words.", "यह नहीं हुआ। आपकी रिकॉर्डिंग सुरक्षित है। फिर कोशिश करें, या शब्द लिखें।"); typing = true }
            finally { operation = ""; task = null }
        }
    }
    val readingLabel = t("Writing the conversation", "बातचीत लिखी जा रही है")
    suspend fun readOnline(clip: DoctorAudioClip): Boolean = try {
        val result = transcribeGocWithGemini(clip.file, GocSpeechLanguage.valueOf(language).code.ifBlank { null }) { share -> progressFlow.value = (share * 1000).toLong() to 1000L }
        val added = result.lines.flatMap { line ->
            val party = when (line.speaker) { "DOCTOR" -> GocParty.DOCTOR_SIDE; "PATIENT_PARTY" -> GocParty.PATIENT_PARTY; else -> GocParty.UNSET }
            GocLines.fromText(line.text, gocSegmentKey(clip.id, (line.startSec * 1000).toLong())).map { it.copy(party = party, guessed = party != GocParty.UNSET) }
        }
        lines = lines + added.filter { new -> lines.none { it.key == new.key } }
        model = result.model; transcribedBy = "Google Gemini (${result.model})"
        added.isNotEmpty()
    } catch (c: CancellationException) { throw c } catch (_: Exception) { false }

    /** Picks the engine by itself: Gemini when the server has it and everyone agreed, else Whisper on this phone (downloaded once), then speakers on this phone. */
    fun transcribe(clip: DoctorAudioClip) = run(readingLabel) {
        recorder?.stopPlayback(); lastClip = clip; askLanguage = false
        val before = lines.size
        if (!(gemini && consentAt > 0 && readOnline(clip))) {
            if (!engine.models.ready()) { operation = t("Getting the speech pack, first time only", "स्पीच पैक आ रहा है, सिर्फ़ पहली बार"); engine.models.download { done, total -> progressFlow.value = done to total } }
            operation = readingLabel; progressFlow.value = 0L to 0L
            val segments = engine.transcribe(clip, d.patientId, GocSpeechLanguage.valueOf(language), { done, total -> progressFlow.value = done to total }) { segment ->
                scope.launch {
                    val key = gocSegmentKey(clip.id, segment.startMs)
                    if (segment.text.isNotBlank() && lines.none { it.key.startsWith("$key:") }) lines = lines + GocLines.fromText(segment.text, key)
                }
            }
            model = OfflineWhisperModel.id; transcribedBy = "Whisper on this phone (${OfflineWhisperModel.id})"
            // Who spoke when, on the phone, with no prompt. The speaker who clearly asks the questions is taken as the doctor; otherwise the screen asks once.
            if (segments.any { it.text.isNotBlank() }) runCatching {
                if (!gocDiarizeModelsReady(context)) { operation = t("Getting the speaker pack, first time only", "वक्ता पैक आ रहा है, सिर्फ़ पहली बार"); downloadGocDiarizeModels(context) { done, total -> progressFlow.value = done to total } }
                operation = t("Telling the speakers apart", "बोलने वालों को अलग किया जा रहा है"); progressFlow.value = 0L to 0L
                val turns = diarizeGocAudio(context, clip.file) { share -> progressFlow.value = (share * 1000).toLong() to 1000L }
                val speakers = gocSpeakersForSegments(segments, turns)
                val byKey = segments.zip(speakers).mapNotNull { (segment, speaker) -> speaker?.let { gocSegmentKey(clip.id, segment.startMs) + ":" to "Speaker ${it + 1}" } }
                lines = lines.map { line -> byKey.firstOrNull { line.key.startsWith(it.first) }?.let { (_, who) -> if (line.party == GocParty.UNSET || line.guessed) line.copy(who = who, guessed = true) else line.copy(who = who) } ?: line }
                val doctor = gocDoctorSpeaker(lines)
                if (doctor != null) lines = lines.map { if (it.who.startsWith("Speaker ")) it.copy(party = if (it.who == doctor) GocParty.DOCTOR_SIDE else GocParty.PATIENT_PARTY, guessed = true) else it }
                else askDoctor = byKey.map { it.second }.distinct().size > 1
            }.onFailure { if (it is CancellationException) throw it }
        }
        delay(50)
        if (lines.size > before) stage = GocStage.REVIEW else { askLanguage = true; typing = true; error = t("No words were heard. Choose the language, or type the conversation.", "कोई शब्द सुनाई नहीं दिए। भाषा चुनें, या बातचीत टाइप करें।") }
    }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) recorder?.import(uri, consentNames.joinToString("\n"), consentNames.toSet())?.let { transcribe(it) }
    }
    fun confirm(decision: GocDecision, choice: String, quotes: List<String> = emptyList()) {
        val id = choice.takeUnless { it == GOC_NONE }
        val words = quotes.ifEmpty { id?.let { GocLatest.standing(extraction, decision, lines).suggested[it] }.orEmpty().map { it.quote } }
        update { GocPlan.apply(it, decision, id, words) }
        topics = topics + (decision to GocTopicRecord(decision, GocTopicStatus.CONFIRMED, id, words.firstOrNull().orEmpty(), planNow()))
    }
    /** Rule V1: only what the conversation stated, one clear option each; never a default, nothing undiscussed. */
    fun confirmAll() { suggestions.forEach { (dd, id, quotes) -> confirm(dd, id, quotes) } }
    fun continueToNote() {
        note = GocNote.draft(d, topics, names.joinToString("\n"), hindi, undiscussed.map(GocGuide::title))
        update { it.copy(gocTranscript = build(planNow())) }
        stage = GocStage.NOTE
    }
    fun finish() {
        update { it.copy(gocTranscript = build(planNow())) }
        recorderState.clips.forEach { recorder?.delete(it) }
        draftFile?.delete()
        onClose()
    }

    LazyColumn(Modifier.fillMaxSize().testTag("goc-page"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlanHeader(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), onClose, "goc-header")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(GocStage.CAPTURE to t("Consent and record", "सहमति और रिकॉर्ड"), GocStage.REVIEW to t("Review", "जाँच"), GocStage.NOTE to t("Note and sign", "नोट और हस्ताक्षर")).forEach { (s, label) ->
                        val now = s == stage; val done = s.ordinal < stage.ordinal
                        Surface(color = if (now) Rust else if (done) GocTickSoft else GocSand, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).heightIn(min = 40.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text(label, color = if (now) Color.White else DoctorCardInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp)) }
                        }
                    }
                }
            }
        }

        if (stage == GocStage.CAPTURE) item(key = "capture") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(4.dp)) {
                        CheckLine(consentAt > 0, { consentAt = if (it) System.currentTimeMillis() else 0L }, t("Everyone present agrees to this conversation being recorded and written down", "मौजूद सभी लोग इस बातचीत को रिकॉर्ड करने और लिखने के लिए राज़ी हैं"))
                        if (gemini) Text(t("Audio is sent to Google Gemini to transcribe.", "ऑडियो लिखने के लिए Google Gemini को भेजा जाता है।"), style = MaterialTheme.typography.bodySmall, color = DoctorCardSub, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp).testTag("goc-gemini-line"))
                    }
                }
                if (!recorderState.recording && !busy) CareField(participants, { participants = it.take(600) }, t("Who is present (optional)", "कौन मौजूद है (वैकल्पिक)"))
                if (busy) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val percent = if (progress.second > 0) (progress.first * 100 / progress.second).coerceIn(0, 100) else 0
                        Text(if (progress.second > 0) "$operation · $percent%" else "$operation…", fontWeight = FontWeight.Medium, modifier = Modifier.announce())
                        if (progress.second > 0) LinearProgressIndicator(progress = { (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Rust) else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Rust)
                        TextButton(onClick = { task?.cancel() }, modifier = Modifier.heightIn(min = 48.dp).testTag("goc-cancel")) { Text(t("Stop", "रोकें"), color = RustText) }
                    }
                } else if (recorder != null) {
                    if (recorderState.recording) Text((if (recorderState.paused) t("Paused", "रुका हुआ") else t("Recording", "रिकॉर्ड हो रहा है")) + " · " + clock(recorderState.seconds), fontWeight = FontWeight.Bold, modifier = Modifier.announce())
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = {
                            if (recorderState.recording) recorder.stopAndGet()?.let { transcribe(it) }
                            else withMic { recorder.start(consentNames.joinToString("\n"), consentNames.toSet(), purpose = true, storage = true, access = true) }
                        }, enabled = consentAt > 0, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = if (recorderState.recording) DoctorCardInk else Rust),
                            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(148.dp).testTag("goc-record")) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(if (recorderState.recording) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(40.dp))
                                Text(if (recorderState.recording) t("Stop", "रोकें") else t("Record", "रिकॉर्ड करें"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (recorderState.recording) OutlinedButton(onClick = { if (recorderState.paused) recorder.resume() else recorder.pause() }, shape = CircleShape, modifier = Modifier.size(84.dp).testTag("goc-pause")) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(if (recorderState.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null); Text(if (recorderState.paused) t("Resume", "फिर शुरू") else t("Pause", "रोकें (थोड़ी देर)"), fontSize = 12.sp) }
                        }
                    }
                    if (recorderState.recording) LinearProgressIndicator(progress = { recorderState.level.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Rust)
                    recorderState.error?.let { Text(it.pick(hindi), color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
                }
                if (!recorderState.recording && !busy) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (recorder != null) TextButton(onClick = { upload.launch(arrayOf("audio/*", "video/webm")) }, enabled = consentAt > 0, modifier = Modifier.heightIn(min = 48.dp).testTag("goc-upload")) { Text(t("Upload a file", "फ़ाइल अपलोड करें"), color = RustText, textDecoration = TextDecoration.Underline) }
                    Text("·", color = DoctorCardSub)
                    TextButton(onClick = { typing = !typing }, modifier = Modifier.heightIn(min = 48.dp).testTag("goc-type")) { Text(t("Type or paste", "टाइप या पेस्ट करें"), color = RustText, textDecoration = TextDecoration.Underline) }
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("goc-error").announce())
                if (askLanguage && lastClip != null) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Language", "भाषा"), fontWeight = FontWeight.Bold)
                    GocSpeechLanguage.entries.filter { it != GocSpeechLanguage.AUTO }.forEach { choice -> FilterChip(selected = language == choice.name, onClick = { language = choice.name; lastClip?.let { transcribe(it) } }, label = { Text(choice.label) }, modifier = Modifier.testTag("goc-language-${choice.name}")) }
                }
                if (typing) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CareField(typed, { typed = it.take(OfflineSpeechModel.maxOriginalChars) }, t("Type or paste the conversation", "बातचीत लिखें या चिपकाएँ"), minLines = 4)
                    Button(onClick = { lines = lines + GocLines.fromText(typed); typed = ""; typing = false; stage = GocStage.REVIEW }, enabled = typed.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-add-typed")) { Text(t("Use this text", "यह लिखावट इस्तेमाल करें")) }
                }
            }
        }

        if (stage == GocStage.REVIEW) {
            item(key = "checklist") {
                PlanCard {
                    Text(t("Checklist", "सूची") + " · " + (GocGuide.ORDER.size - undiscussed.size) + " / " + GocGuide.ORDER.size, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("goc-guide-title"))
                    GocGuide.ORDER.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { item ->
                                val on = item !in undiscussed
                                Surface(color = if (on) GocTickSoft else GocSand, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).heightIn(min = 40.dp)) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (on) "✓" else "", color = DoctorStatusGood, fontWeight = FontWeight.Bold, modifier = Modifier.width(16.dp))
                                        Text((GocGuide.section(item)?.let { "$it · " } ?: "") + GocGuide.title(item).pick(hindi), fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) DoctorCardInk else DoctorCardSub)
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            if (askDoctor) item(key = "who") {
                Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("goc-name-doctor")) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(t("Who’s the doctor?", "डॉक्टर कौन हैं?"), fontWeight = FontWeight.Bold)
                        Text(t("Tap a line the doctor said.", "डॉक्टर की कही कोई पंक्ति दबाएँ।"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item(key = "suggest") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Suggested from the conversation", "बातचीत से सुझाव"), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    }
                    InfoDisclosure(t("How suggestions work", "सुझाव कैसे बनते हैं")) {
                        Text(t("Each suggestion is the words that were said, not advice. When the same person changed their mind, the latest statement wins and the earlier one is struck through. When different people disagree, both are shown and nothing is suggested. Confirm all applies only what was said.", "हर सुझाव कहे गए शब्द हैं, सलाह नहीं। अगर एक ही व्यक्ति ने राय बदली, तो आख़िरी बात मानी जाती है और पहले वाली कटी दिखती है। अलग-अलग लोग असहमत हों तो दोनों दिखती हैं और कुछ नहीं सुझाया जाता। “सब पक्का करें” सिर्फ़ कही गई बातें लगाता है।"), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = ::confirmAll, enabled = suggestions.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("goc-confirm-all")) {
                        Text(if (suggestions.isNotEmpty()) t("Confirm all suggested", "सभी सुझाव पक्के करें") + " (${suggestions.size})" else t("All suggestions confirmed", "सभी सुझाव पक्के"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            listOf("II" to t("Medical information and patient values", "चिकित्सा जानकारी और मरीज़ के मूल्य"), "III" to t("Decisive actions", "निर्णायक फ़ैसले")).forEach { (sec, secTitle) ->
                val items = GocPlan.ORDER.filter { dd -> (if (dd == GocDecision.VALUES) "II" else "III") == sec && (extraction.hitsFor(dd).isNotEmpty() || topics[dd]?.status == GocTopicStatus.CONFIRMED) }
                if (items.isNotEmpty()) {
                    item(key = "sec-$sec") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).background(Rust, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(sec, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            Spacer(Modifier.width(8.dp)); Text(secTitle, color = RustText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    items.forEach { dd -> item(key = "decision-${dd.name}") { GocSuggestionCard(d, dd, GocLatest.standing(extraction, dd, lines), lines, topics[dd]) { id -> confirm(dd, id) } } }
                }
            }
            item(key = "transcript") {
                InfoDisclosure(t("Transcript", "लिखाई") + " · " + lines.size + (if (transcribedBy.isNotBlank()) " · $transcribedBy" else "")) {
                    lines.forEachIndexed { index, line ->
                        Column(Modifier.fillMaxWidth().clickable { if (askDoctor) { val who = line.who; lines = lines.map { l -> if (who.isNotBlank() && l.who == who || l === line) l.copy(party = GocParty.DOCTOR_SIDE, guessed = false) else if (who.isNotBlank() && l.who.startsWith("Speaker ") && l.party != GocParty.DOCTOR_SIDE) l.copy(party = GocParty.PATIENT_PARTY, guessed = false) else l }; askDoctor = false } else editing = if (editing == index) -1 else index }.testTag("goc-line-$index")) {
                            FilterChip(selected = line.party != GocParty.UNSET, onClick = { lines = lines.toMutableList().also { it[index] = line.copy(party = GocLines.nextParty(line.party), guessed = false) } }, label = { Text((if (line.who.isNotBlank()) line.who + " · " else "") + GocLines.label(line.party).pick(hindi)) }, modifier = Modifier.testTag("goc-party-$index"))
                            if (editing == index) CareField(line.text, { v -> lines = lines.toMutableList().also { it[index] = line.copy(text = v) } }, t("Correct this line", "यह पंक्ति सुधारें"))
                            else Text(line.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    TextButton(onClick = { stage = GocStage.CAPTURE }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Add more", "और जोड़ें"), color = RustText) }
                }
            }
            item(key = "finish") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (undiscussed.isNotEmpty()) Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("goc-not-discussed")) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t("You haven’t spoken about these things", "इन बातों पर बात नहीं हुई"), fontWeight = FontWeight.Bold)
                            undiscussed.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("• " + GocGuide.title(item).pick(hindi), Modifier.weight(1f))
                                TextButton(onClick = { guideTicked = guideTicked + item }, modifier = Modifier.heightIn(min = 44.dp)) { Text(t("It was discussed", "इस पर बात हुई"), color = RustText, fontSize = 13.sp) }
                            } }
                        }
                    }
                    Button(onClick = ::continueToNote, enabled = !busy && text.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(if (undiscussed.isNotEmpty()) "goc-continue-anyway" else "goc-finish")) {
                        Text(if (undiscussed.isNotEmpty()) t("Continue anyway", "फिर भी आगे बढ़ें") else t("Continue", "आगे बढ़ें"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (stage == GocStage.NOTE) {
            item(key = "note-tools") {
                var problem by remember { mutableStateOf("") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { stage = GocStage.REVIEW }, enabled = signOff == null, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back to review", "जाँच पर वापस"), color = RustText) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch {
                        val file = runCatching { withContext(Dispatchers.IO) { File(File(context.cacheDir, "doctor-preview-exports").apply { mkdirs() }, "goals-of-care-${d.id.filter { it.isLetterOrDigit() || it == '-' }.take(48)}.pdf").also {
                            CarePlanPdf.gocNote(it, d, hindi, LocalDate.now(), note = note, topics = topics, signers = signOff?.formSigners().orEmpty()) } } }.getOrNull()
                        if (file == null) problem = t("Couldn't make the PDF. Try again.", "PDF नहीं बना। फिर कोशिश करें।") else runCatching { DoctorGocPdf.share(context, file, t("Share PDF", "PDF साझा करें")) }
                    } }, modifier = Modifier.heightIn(min = 48.dp).testTag("goc-pdf")) { Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF") }
                }
                if (problem.isNotBlank()) Text(problem, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())
            }
            item(key = "form") { GocFormView(GocForm.sections(d, topics, signOff?.formSigners().orEmpty()), Modifier.testTag("goc-form")) }
            item(key = "sign") {
                PlanCard {
                    if (note.isNotBlank()) GocSignOff(noteId = "${d.id}:v${d.version}:goc", noteText = note, store = signStore, doctorPrefill = d.createdBy, signerPrefill = d.patient.name, showNote = false,
                        onComplete = { record ->
                            signOff = GocSignOffSummary(record.id, record.sealSha256.orEmpty(), record.lockedAt.orEmpty(), record.signatures.map { it.name },
                                record.signatures.map { it.role }, record.signatures.map { it.registrationNo.orEmpty() }, record.signatures.map { it.method }, record.signatures.map { it.signedAt })
                            update { it.copy(gocTranscript = build(planNow())) }
                        },
                        onInvalidated = { signOff = null })
                }
            }
            if (signOff != null) item(key = "done") {
                Surface(color = GocTickSoft, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("goc-signed")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✓ " + t("Signed and locked", "साइन होकर बंद") + ": " + signOff?.signers.orEmpty().joinToString(", "), fontWeight = FontWeight.Bold)
                        Button(onClick = ::finish, colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-done")) { Text(t("Done", "हो गया")) }
                    }
                }
            }
        }
    }
}

private fun clock(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** One decision on the review screen: what was said (latest statement wins, earlier words struck through), the suggested option, and Change. */
@Composable private fun GocSuggestionCard(d: EctprRecord, decision: GocDecision, standing: GocStanding, lines: List<GocLine>, topic: GocTopicRecord?, onPick: (String) -> Unit) {
    val t = careText(); val hindi = careHindi()
    var menu by remember { mutableStateOf(false) }
    fun said(h: GocHit): String {
        val line = GocLines.lineAt(lines, h.at)
        val who = line?.who?.takeIf(String::isNotBlank) ?: line?.party?.takeIf { it != GocParty.UNSET }?.let { GocLines.label(it).pick(hindi) }
        return (who?.let { "$it: " } ?: "") + "“" + h.quote + "”"
    }
    val options = GocPlan.options(decision)
    val suggested = GocSuggest.single(standing)
    val confirmed = topic?.status == GocTopicStatus.CONFIRMED
    val shown = if (confirmed) topic?.optionId else suggested
    Surface(color = if (confirmed) GocTickSoft else Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("goc-decision-${decision.name}")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(GocPlan.title(decision).pick(hindi), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).a11yHeading())
                if (standing.conflict) Text(t("People disagree", "लोगों की राय अलग है"), color = RustText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("goc-conflict-${decision.name}"))
                else if (standing.changedMind) Text(t("Changed during the talk", "बातचीत में बदला"), color = RustText, fontSize = 12.sp, modifier = Modifier.testTag("goc-changed-${decision.name}"))
            }
            if (decision == GocDecision.VALUES) standing.suggested.forEach { (id, hits) ->
                Text((if (confirmed) "✓ " else "") + (options.firstOrNull { it.id == id }?.label?.pick(hindi).orEmpty()), fontWeight = FontWeight.Bold)
                hits.forEach { Text(said(it), style = MaterialTheme.typography.bodySmall) }
            } else {
                shown?.let { id -> Text((if (confirmed) "✓ " else "") + (options.firstOrNull { it.id == id }?.label?.pick(hindi) ?: NO_PREFERENCE.pick(hindi)), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (confirmed) DoctorCardInk else RustText, modifier = Modifier.testTag("goc-suggested-${decision.name}")) }
                standing.current.forEach { Text(said(it), style = MaterialTheme.typography.bodySmall) }
                standing.superseded.forEach { (earlier, _) -> Text(said(earlier), style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = DoctorCardSub) }
                Box {
                    TextButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 44.dp).testTag("goc-change-${decision.name}")) { Text(if (confirmed || suggested != null) t("Change", "बदलें") else t("Choose", "चुनें"), color = RustText) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        options.forEach { o -> DropdownMenuItem(text = { Text(o.label.pick(hindi) + if (o.id in standing.suggested) " · " + t("said", "कहा गया") else "") }, onClick = { menu = false; onPick(o.id) }) }
                        DropdownMenuItem(text = { Text(NO_PREFERENCE.pick(hindi)) }, onClick = { menu = false; onPick(GOC_NONE) })
                    }
                }
            }
        }
    }
}
