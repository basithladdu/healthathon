package `in`.wedevit.care

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
/** Token aliases (5 Nov polish): the warm canvas for headings and table heads, the soft green for "done". */
private val GocSand get() = Cream
private val GocTickSoft get() = DoctorStatusGoodBg

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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(DoctorCardConversation, CircleShape), contentAlignment = Alignment.Center) { Icon(if (own?.signOff != null) Icons.Default.Lock else Icons.Default.GraphicEq, null, tint = RustText, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.a11yHeading())
                own?.let { g -> Text(GocNote.kindLabel(g.noteKind).pick(hindi), fontSize = 14.sp, color = DoctorCardSub) }
            }
        }
        own?.let { g -> GocStatusChip(if (g.signOff != null) t("Signed and locked", "साइन होकर बंद") else t("Saved", "सहेजा गया"), done = g.signOff != null) }
        Button(onClick = { open = true }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-open")) {
            Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (own != null) t("Open", "खोलें") else t("Record", "रिकॉर्ड करें"))
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
        Text(t("Goals-of-care note", "देखभाल के लक्ष्यों का नोट"), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.a11yHeading())
        Text(GocNote.kindLabel(g.noteKind).pick(hindi), fontSize = 14.sp, color = DoctorCardSub)
        g.signOff?.let { so -> GocStatusChip(t("Signed and locked", "साइन होकर बंद") + ": " + so.signers.joinToString(", ") + " · " + so.lockedAt.take(16).replace('T', ' '), done = true) }
            ?: GocStatusChip(t("Not signed", "हस्ताक्षर नहीं हुए"), done = false)
        GocFormView(GocForm.sections(d, g.topics.associateBy { it.decision }, g.signOff?.formSigners().orEmpty()), Modifier.testTag("goc-signing-note"), framed = false)
    }
}

/** A small rounded status pill: green with a tick when done, warm amber otherwise. */
@Composable private fun GocStatusChip(text: String, done: Boolean, modifier: Modifier = Modifier) {
    Surface(color = if (done) DoctorStatusGoodBg else DoctorStatusWarnBg, shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (done) Icons.Default.Lock else Icons.Default.Info, null, tint = if (done) DoctorStatusGood else DoctorStatusWarn, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (done) DoctorStatusGood else DoctorStatusWarn)
        }
    }
}

/**
 * The legal form on screen, laid out like a clean official document: one paper sheet, each section opening with its
 * roman numeral and the form's heading under a rule, filled blanks in bold on a ruled line, "Not discussed" muted,
 * drawn tick boxes, and the form's tables with signature boxes. The wording is the form's, untouched ([GocForm]).
 * [framed] false drops the sheet's own border when the caller already sits it on a card.
 */
@Composable internal fun GocFormView(sections: List<GocFormSection>, modifier: Modifier = Modifier, framed: Boolean = true) { val hindi = careHindi()
    val sheet = if (framed) Modifier.background(Paper, RoundedCornerShape(20.dp)).border(1.dp, DoctorHairline, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp) else Modifier
    Column(modifier.fillMaxWidth().then(sheet)) {
        sections.forEachIndexed { index, section ->
            Column(Modifier.fillMaxWidth().padding(top = if (index == 0) 8.dp else 24.dp, bottom = 8.dp).testTag("goc-form-section-${section.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).border(1.5.dp, Rust, RoundedCornerShape(10.dp)).background(GocSand, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        Text(section.id, color = RustText, fontWeight = FontWeight.Bold, fontFamily = SaharaTitleFont, fontSize = 17.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(section.title.pick(hindi), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                }
                HorizontalDivider(thickness = 1.5.dp, color = Rust.copy(alpha = .55f))
                section.groups.forEach { g ->
                    if (g.title.en.isNotBlank()) Text(g.title.pick(hindi), color = RustText, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 4.dp))
                    g.rows.forEach { row -> GocFormRowView(row, hindi) }
                    g.table?.let { GocFormTableView(it, hindi) }
                }
            }
        }
    }
}

/** One blank of the form: its label, then the answer on a ruled line, or the form's boxes. */
@Composable private fun GocFormRowView(row: GocFormRow, hindi: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (row.label.en.isNotBlank()) Text(row.label.pick(hindi), fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
        if (row.value.en.isNotBlank()) {
            Text(row.value.pick(hindi), fontSize = 16.sp, lineHeight = 22.sp, fontWeight = if (row.open) FontWeight.Normal else FontWeight.SemiBold,
                fontStyle = if (row.open) FontStyle.Italic else FontStyle.Normal, color = if (row.open) DoctorCardSub else DoctorCardInk, modifier = Modifier.padding(start = 2.dp))
            HorizontalDivider(color = DoctorHairline)
        }
        row.ticks.forEach { tick -> GocTickLine(tick.on, tick.text.pick(hindi) + if (tick.detail.isBlank()) "" else " " + tick.detail) }
    }
}

/** A tick box of the form, drawn as a box: filled Rust with a tick when ticked, an empty outlined box when not. Read aloud as the form's own box glyph. */
@Composable private fun GocTickBox(on: Boolean) {
    Box(Modifier.size(20.dp).semantics { contentDescription = if (on) GocForm.TICK_ON else GocForm.TICK_OFF }
        .background(if (on) Rust else Paper, RoundedCornerShape(4.dp)).border(1.5.dp, if (on) Rust else DoctorCardSub, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable private fun GocTickLine(on: Boolean, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp).padding(start = if (text.startsWith("    ")) 20.dp else 0.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 1.dp)) { GocTickBox(on) }
        Spacer(Modifier.width(10.dp))
        Text(text.trimStart(), fontSize = 15.sp, lineHeight = 21.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) DoctorCardInk else DoctorCardSub, modifier = Modifier.weight(1f))
    }
}

/** A table of the form. Three narrow columns on a phone stay readable; wider tables scroll sideways like the paper. Signature columns draw a signature box. */
@Composable private fun GocFormTableView(table: GocFormTable, hindi: Boolean) {
    val n = table.head.size
    val weights = when {
        n == 3 && table.head[0].en == "LEVEL" -> listOf(0.6f, 3.6f, 1f)
        n == 3 -> listOf(1.1f, 1f, 1f)
        n == 4 -> listOf(1.8f, 1f, 1f, 0.8f)
        else -> List(n) { 1f }
    }
    val wide = n >= 5
    val signatureColumns = table.head.mapIndexedNotNull { i, h -> i.takeIf { h.en.contains("signature", ignoreCase = true) } }.toSet()
    val shape = RoundedCornerShape(14.dp)
    Column((if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth()).clip(shape).background(Paper).border(1.dp, DoctorHairline, shape)) {
        (listOf(table.head) + table.rows).forEachIndexed { index, cells ->
            if (index > 1) HorizontalDivider(color = DoctorHairline)
            Row((if (wide) Modifier.widthIn(min = 560.dp) else Modifier.fillMaxWidth()).background(if (index == 0) GocSand else Color.Transparent)) {
                cells.forEachIndexed { i, cell ->
                    Column((if (wide) Modifier.width(112.dp) else Modifier.weight(weights.getOrElse(i) { 1f })).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val content: @Composable () -> Unit = {
                            cell.pick(hindi).lines().filter { it.isNotEmpty() || cells.size == 1 }.forEach { line ->
                                when {
                                    line.startsWith(GocForm.TICK_ON) || line.startsWith(GocForm.TICK_OFF) -> GocTickLine(line.startsWith(GocForm.TICK_ON), line.drop(1).trim())
                                    else -> Text(line, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                                        fontStyle = if (line == GocForm.NOT_DISCUSSED.pick(hindi)) FontStyle.Italic else FontStyle.Normal,
                                        color = if (line == GocForm.NOT_DISCUSSED.pick(hindi)) DoctorCardSub else DoctorCardInk)
                                }
                            }
                        }
                        if (index > 0 && i in signatureColumns) Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).border(1.dp, DoctorCardSub.copy(alpha = .5f), RoundedCornerShape(6.dp)).padding(6.dp), contentAlignment = Alignment.BottomStart) { Column { content() } }
                        else content()
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
            catch (c: CancellationException) { error = t("Stopped", "रोक दिया"); throw c }
            catch (e: Exception) { error = e.message ?: t("Didn't work. Try again.", "नहीं हुआ। फिर कोशिश करें।"); typing = true }
            finally { operation = ""; task = null }
        }
    }
    val readingLabel = t("Transcribing", "लिखा जा रहा है")
    suspend fun readOnline(clip: DoctorAudioClip): Boolean = try {
        val result = transcribeGocWithGemini(clip.file, GocSpeechLanguage.valueOf(language).code.ifBlank { null }, patientId = d.patientId, conversationId = clip.id) { share -> progressFlow.value = (share * 1000).toLong() to 1000L }
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
            if (!engine.models.ready()) { operation = t("Downloading", "डाउनलोड हो रहा है"); engine.models.download { done, total -> progressFlow.value = done to total } }
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
                if (!gocDiarizeModelsReady(context)) { operation = t("Downloading", "डाउनलोड हो रहा है"); downloadGocDiarizeModels(context) { done, total -> progressFlow.value = done to total } }
                operation = t("Finding speakers", "बोलने वाले पहचाने जा रहे हैं"); progressFlow.value = 0L to 0L
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
        if (lines.size > before) stage = GocStage.REVIEW else { askLanguage = true; typing = true; error = t("No words heard. Pick a language.", "कोई शब्द नहीं सुने। भाषा चुनें।") }
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

    LazyColumn(Modifier.fillMaxSize().testTag("goc-page"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PlanHeader(t("Goals-of-care conversation", "देखभाल के लक्ष्यों की बातचीत"), onClose, "goc-header")
                GocStepper(listOf(GocStage.CAPTURE to t("Record", "रिकॉर्ड करें"), GocStage.REVIEW to t("Review", "जाँच"), GocStage.NOTE to t("Sign", "हस्ताक्षर")), stage, signOff != null)
            }
        }

        if (stage == GocStage.CAPTURE) item(key = "capture") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Step 1: consent. One tick for everyone in the room; the record button stays off until it is ticked.
                Surface(color = if (consentAt > 0) GocTickSoft else Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(if (consentAt > 0) 1.5.dp else 1.dp, if (consentAt > 0) DoctorStatusGood else DoctorHairline), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        CheckLine(consentAt > 0, { consentAt = if (it) System.currentTimeMillis() else 0L }, t("Everyone present agrees to this conversation being recorded and written down", "मौजूद सभी लोग इस बातचीत को रिकॉर्ड करने और लिखने के लिए राज़ी हैं"))
                        if (gemini) Text(t("Audio is sent to Google Gemini to transcribe.", "ऑडियो लिखने के लिए Google Gemini को भेजा जाता है।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 10.dp).testTag("goc-gemini-line"))
                    }
                }
                if (!recorderState.recording && !busy) CareField(participants, { participants = it.take(600) }, t("Who's present", "कौन मौजूद है"))
                if (busy) GocWorkingCard(operation, progress) { task?.cancel() }
                else if (recorder != null) {
                    // Step 2: record. A big calm button, the timer above it and the voice level as a soft ring around it.
                    if (recorderState.recording) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.announce()) {
                        Text(clock(recorderState.seconds), fontSize = 40.sp, fontWeight = FontWeight.Light, color = DoctorCardInk, fontFamily = SaharaTitleFont)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(if (recorderState.paused) DoctorCardSub else Rust, CircleShape)); Spacer(Modifier.width(8.dp))
                            Text(if (recorderState.paused) t("Paused", "रुका हुआ") else t("Recording", "रिकॉर्ड हो रहा है"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (recorderState.paused) DoctorCardSub else RustText)
                        }
                    }
                    GocRecordButton(recorderState.recording, recorderState.paused, recorderState.level, enabled = consentAt > 0) {
                        if (recorderState.recording) recorder.stopAndGet()?.let { transcribe(it) }
                        else withMic { recorder.start(consentNames.joinToString("\n"), consentNames.toSet(), purpose = true, storage = true, access = true) }
                    }
                    if (recorderState.recording) OutlinedButton(onClick = { if (recorderState.paused) recorder.resume() else recorder.pause() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-pause")) {
                        Icon(if (recorderState.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = RustText); Spacer(Modifier.width(8.dp))
                        Text(if (recorderState.paused) t("Resume", "फिर शुरू") else t("Pause", "रोकें (थोड़ी देर)"), color = RustText, fontSize = 16.sp)
                    }
                    recorderState.error?.let { Text(it.pick(hindi), color = MaterialTheme.colorScheme.error, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.announce()) }
                }
                if (!recorderState.recording && !busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (recorder != null) OutlinedButton(onClick = { upload.launch(arrayOf("audio/*", "video/webm")) }, enabled = consentAt > 0, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("goc-upload")) {
                        Icon(Icons.Default.UploadFile, null, tint = if (consentAt > 0) RustText else DoctorCardFaint); Spacer(Modifier.width(6.dp)); Text(t("Upload", "अपलोड करें"), color = if (consentAt > 0) RustText else DoctorCardFaint, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(onClick = { typing = !typing }, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("goc-type")) {
                        Icon(Icons.Default.Keyboard, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("Type or paste", "टाइप या पेस्ट करें"), color = RustText, textAlign = TextAlign.Center)
                    }
                }
                if (error.isNotBlank()) Surface(color = DoctorStatusRestBg, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(error, color = DoctorStatusRest, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.weight(1f).padding(vertical = 8.dp).testTag("goc-error").announce())
                        val again = lastClip
                        if (again != null && !busy && !askLanguage) TextButton(onClick = { transcribe(again) }, modifier = Modifier.heightIn(min = 48.dp).testTag("goc-retry")) { Text(t("Try again", "फिर कोशिश करें"), color = RustText, fontWeight = FontWeight.SemiBold) }
                    }
                }
                if (askLanguage && lastClip != null) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Language", "भाषा"), fontWeight = FontWeight.Bold)
                    GocSpeechLanguage.entries.filter { it != GocSpeechLanguage.AUTO }.forEach { choice -> FilterChip(selected = language == choice.name, onClick = { language = choice.name; lastClip?.let { transcribe(it) } }, label = { Text(choice.label) }, shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp).testTag("goc-language-${choice.name}")) }
                }
                if (typing) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CareField(typed, { typed = it.take(OfflineSpeechModel.maxOriginalChars) }, t("Type or paste", "टाइप या पेस्ट करें"), minLines = 4)
                    Button(onClick = { lines = lines + GocLines.fromText(typed); typed = ""; typing = false; stage = GocStage.REVIEW }, enabled = typed.isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-add-typed")) { Text(t("Use", "इस्तेमाल करें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (stage == GocStage.REVIEW) {
            item(key = "checklist") {
                PlanCard {
                    val doneCount = GocGuide.ORDER.size - undiscussed.size
                    Row(Modifier.semantics(mergeDescendants = true) {}.testTag("goc-guide-title"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Checklist, null, tint = RustText); Spacer(Modifier.width(8.dp))
                        Text(t("Checklist", "सूची"), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f).a11yHeading())
                        Surface(color = if (undiscussed.isEmpty()) DoctorStatusGoodBg else GocSand, shape = CircleShape) {
                            Text("$doneCount / ${GocGuide.ORDER.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (undiscussed.isEmpty()) DoctorStatusGood else DoctorCardInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    LinearProgressIndicator(progress = { doneCount.toFloat() / GocGuide.ORDER.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = DoctorStatusGood, trackColor = GocSand)
                    Column {
                        GocGuide.ORDER.forEachIndexed { index, item ->
                            val on = item !in undiscussed
                            if (index > 0) HorizontalDivider(color = DoctorHairline)
                            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (on) DoctorStatusGood else DoctorCardFaint, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(GocGuide.title(item).pick(hindi), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) DoctorCardInk else DoctorCardSub, modifier = Modifier.weight(1f))
                                GocGuide.section(item)?.let { sec -> Surface(color = GocSand, shape = CircleShape) { Text(sec, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RustText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) } }
                            }
                        }
                    }
                }
            }
            if (askDoctor) item(key = "who") {
                Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("goc-name-doctor")) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = DoctorStatusWarn); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t("Who’s the doctor?", "डॉक्टर कौन हैं?"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DoctorCardInk)
                            Text(t("Tap their line", "उनकी पंक्ति दबाएँ"), fontSize = 14.sp, color = DoctorCardSub)
                        }
                    }
                }
            }
            item(key = "suggest") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(t("Suggestions", "सुझाव"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                    Button(onClick = ::confirmAll, enabled = suggestions.isNotEmpty(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).testTag("goc-confirm-all")) {
                        Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(8.dp))
                        Text(if (suggestions.isNotEmpty()) t("Confirm all", "सब पक्का करें") + " (${suggestions.size})" else t("All confirmed", "सब पक्के"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            listOf("II" to t("Medical information and patient values", "चिकित्सा जानकारी और मरीज़ के मूल्य"), "III" to t("Decisive actions", "निर्णायक फ़ैसले")).forEach { (sec, secTitle) ->
                val items = GocPlan.ORDER.filter { dd -> (if (dd == GocDecision.VALUES) "II" else "III") == sec && (extraction.hitsFor(dd).isNotEmpty() || topics[dd]?.status == GocTopicStatus.CONFIRMED) }
                if (items.isNotEmpty()) {
                    item(key = "sec-$sec") {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).border(1.5.dp, Rust, RoundedCornerShape(8.dp)).background(GocSand, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Text(sec, color = RustText, fontWeight = FontWeight.Bold, fontFamily = SaharaTitleFont, fontSize = 14.sp) }
                            Spacer(Modifier.width(12.dp)); Text(secTitle, color = RustText, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.a11yHeading())
                        }
                    }
                    items.forEach { dd -> item(key = "decision-${dd.name}") { GocSuggestionCard(d, dd, GocLatest.standing(extraction, dd, lines), lines, topics[dd]) { id -> confirm(dd, id) } } }
                }
            }
            item(key = "transcript") {
                InfoDisclosure(t("Transcript", "लिखाई") + " · " + lines.size) {
                    lines.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = DoctorHairline)
                        Column(Modifier.fillMaxWidth().clickable { if (askDoctor) { val who = line.who; lines = lines.map { l -> if (who.isNotBlank() && l.who == who || l === line) l.copy(party = GocParty.DOCTOR_SIDE, guessed = false) else if (who.isNotBlank() && l.who.startsWith("Speaker ") && l.party != GocParty.DOCTOR_SIDE) l.copy(party = GocParty.PATIENT_PARTY, guessed = false) else l }; askDoctor = false } else editing = if (editing == index) -1 else index }.padding(vertical = 4.dp).testTag("goc-line-$index"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = line.party != GocParty.UNSET, onClick = { lines = lines.toMutableList().also { it[index] = line.copy(party = GocLines.nextParty(line.party), guessed = false) } }, label = { Text((if (line.who.isNotBlank()) line.who + " · " else "") + GocLines.label(line.party).pick(hindi)) }, shape = CircleShape, modifier = Modifier.testTag("goc-party-$index"))
                            if (editing == index) CareField(line.text, { v -> lines = lines.toMutableList().also { it[index] = line.copy(text = v) } }, t("Edit", "बदलें"))
                            else Text(line.text, fontSize = 16.sp, lineHeight = 23.sp, color = DoctorCardInk)
                        }
                    }
                    OutlinedButton(onClick = { stage = GocStage.CAPTURE }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Mic, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("Add more", "और जोड़ें"), color = RustText) }
                }
            }
            item(key = "finish") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (undiscussed.isNotEmpty()) Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("goc-not-discussed")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = DoctorStatusWarn, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                                Text(t("Not discussed", "बात नहीं हुई"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DoctorCardInk)
                            }
                            undiscussed.forEachIndexed { index, item ->
                                if (index > 0) HorizontalDivider(color = DoctorStatusWarn.copy(alpha = .2f))
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(GocGuide.title(item).pick(hindi), Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardInk)
                                    TextButton(onClick = { guideTicked = guideTicked + item }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Discussed", "बात हुई"), color = RustText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                    }
                    Button(onClick = ::continueToNote, enabled = !busy && text.isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(if (undiscussed.isNotEmpty()) "goc-continue-anyway" else "goc-finish")) {
                        Text(if (undiscussed.isNotEmpty()) t("Continue anyway", "फिर भी आगे बढ़ें") else t("Continue", "आगे बढ़ें"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }

        if (stage == GocStage.NOTE) {
            item(key = "note-tools") {
                var problem by remember { mutableStateOf("") }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { stage = GocStage.REVIEW }, enabled = signOff == null, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = if (signOff == null) RustText else DoctorCardFaint); Spacer(Modifier.width(6.dp)); Text(t("Back", "वापस"), color = if (signOff == null) RustText else DoctorCardFaint) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch {
                        val file = runCatching { withContext(Dispatchers.IO) { File(File(context.cacheDir, "doctor-preview-exports").apply { mkdirs() }, "goals-of-care-${d.id.filter { it.isLetterOrDigit() || it == '-' }.take(48)}.pdf").also {
                            CarePlanPdf.gocNote(it, d, hindi, LocalDate.now(), note = note, topics = topics, signers = signOff?.formSigners().orEmpty()) } } }.getOrNull()
                        if (file == null) problem = t("Couldn't make the PDF. Try again.", "PDF नहीं बना। फिर कोशिश करें।") else runCatching { DoctorGocPdf.share(context, file, t("Share PDF", "PDF साझा करें")) }
                    } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-pdf")) { Icon(Icons.Default.PictureAsPdf, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text("PDF", color = RustText) }
                }
                if (problem.isNotBlank()) Text(problem, color = MaterialTheme.colorScheme.error, fontSize = 15.sp, modifier = Modifier.announce())
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
                Surface(color = GocTickSoft, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.5.dp, DoctorStatusGood), modifier = Modifier.fillMaxWidth().testTag("goc-signed")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(DoctorStatusGood, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Text(t("Signed and locked", "साइन होकर बंद") + ": " + signOff?.signers.orEmpty().joinToString(", "), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.weight(1f).announce())
                        }
                        Button(onClick = ::finish, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-done")) { Text(t("Done", "हो गया"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

/** The three stages as numbered dots joined by a line: done ones ticked in green, the current one filled Rust. [locked] ticks the last one too. */
@Composable private fun GocStepper(steps: List<Pair<GocStage, String>>, stage: GocStage, locked: Boolean) {
    Row(Modifier.fillMaxWidth().testTag("goc-steps"), verticalAlignment = Alignment.Top) {
        steps.forEachIndexed { index, (s, label) ->
            val done = s.ordinal < stage.ordinal || (locked && s == stage)
            val now = s == stage && !done
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), thickness = 2.dp, color = if (index == 0) Color.Transparent else if (s.ordinal <= stage.ordinal) DoctorStatusGood else DoctorHairline)
                    Box(Modifier.size(32.dp).background(if (now) Rust else if (done) DoctorStatusGood else Paper, CircleShape).border(1.5.dp, if (now) Rust else if (done) DoctorStatusGood else DoctorCardSub.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) {
                        if (done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        else Text("${index + 1}", color = if (now) Color.White else DoctorCardSub, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    HorizontalDivider(Modifier.weight(1f), thickness = 2.dp, color = if (index == steps.lastIndex) Color.Transparent else if (s.ordinal < stage.ordinal) DoctorStatusGood else DoctorHairline)
                }
                Text(label, color = if (now) RustText else if (done) DoctorStatusGood else DoctorCardSub, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

/** A gentle note on what is happening now (writing the words, getting a pack), with the share done and Stop. */
@Composable private fun GocWorkingCard(operation: String, progress: Pair<Long, Long>, onStop: () -> Unit) { val t = careText()
    Surface(color = DoctorCardConversation, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("goc-working")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val percent = if (progress.second > 0) (progress.first * 100 / progress.second).coerceIn(0, 100) else 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Rust, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text(if (progress.second > 0) "$operation · $percent%" else "$operation…", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.weight(1f).announce())
            }
            if (progress.second > 0) LinearProgressIndicator(progress = { (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Rust, trackColor = Paper)
            else LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Rust, trackColor = Paper)
            OutlinedButton(onClick = onStop, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-cancel")) {
                Icon(Icons.Default.Stop, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("Stop", "रोकें"), color = RustText)
            }
        }
    }
}

/** The big, calm record button: a soft ring that breathes with the voice level while recording. */
@Composable private fun GocRecordButton(recording: Boolean, paused: Boolean, level: Float, enabled: Boolean, onClick: () -> Unit) { val t = careText()
    val ring by animateFloatAsState(if (recording && !paused) level.coerceIn(0f, 1f) else 0f, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "voice level")
    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        if (recording) Box(Modifier.size(156.dp + 40.dp * ring).background(Rust.copy(alpha = .10f + .12f * ring), CircleShape))
        if (recording) Box(Modifier.size(156.dp + 16.dp * ring).background(Rust.copy(alpha = .14f), CircleShape))
        Button(onClick = onClick, enabled = enabled, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = if (recording) DoctorCardInk else Rust),
            contentPadding = PaddingValues(0.dp), modifier = Modifier.size(148.dp).testTag("goc-record")) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null, Modifier.size(44.dp))
                Spacer(Modifier.height(4.dp))
                Text(if (recording) t("Stop", "रोकें") else t("Record", "रिकॉर्ड करें"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun clock(seconds: Int) ="${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

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
    Surface(color = if (confirmed) GocTickSoft else Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (confirmed) DoctorStatusGood.copy(alpha = .4f) else DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("goc-decision-${decision.name}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(GocPlan.title(decision).pick(hindi), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading())
                if (standing.conflict) GocTag(t("People disagree", "लोगों की राय अलग है"), Modifier.testTag("goc-conflict-${decision.name}"))
                else if (standing.changedMind) GocTag(t("Changed during the talk", "बातचीत में बदला"), Modifier.testTag("goc-changed-${decision.name}"))
            }
            if (decision == GocDecision.VALUES) standing.suggested.forEach { (id, hits) ->
                GocChoiceLine(options.firstOrNull { it.id == id }?.label?.pick(hindi).orEmpty(), confirmed)
                hits.forEach { GocQuote(said(it)) }
            } else {
                shown?.let { id -> GocChoiceLine(options.firstOrNull { it.id == id }?.label?.pick(hindi) ?: NO_PREFERENCE.pick(hindi), confirmed, Modifier.testTag("goc-suggested-${decision.name}")) }
                standing.current.forEach { GocQuote(said(it)) }
                standing.superseded.forEach { (earlier, _) -> GocQuote(said(earlier), struck = true) }
                Box {
                    TextButton(onClick = { menu = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("goc-change-${decision.name}")) { Icon(Icons.Default.Edit, null, tint = RustText, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (confirmed || suggested != null) t("Change", "बदलें") else t("Choose", "चुनें"), color = RustText, fontWeight = FontWeight.SemiBold) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        options.forEach { o -> DropdownMenuItem(text = { Text(o.label.pick(hindi) + if (o.id in standing.suggested) " · " + t("said", "कहा गया") else "") }, onClick = { menu = false; onPick(o.id) }) }
                        DropdownMenuItem(text = { Text(NO_PREFERENCE.pick(hindi)) }, onClick = { menu = false; onPick(GOC_NONE) })
                    }
                }
            }
        }
    }
}

/** A small warm tag on a decision card ("People disagree", "Changed during the talk"). */
@Composable private fun GocTag(text: String, modifier: Modifier = Modifier) {
    Surface(color = DoctorStatusWarnBg, shape = CircleShape, modifier = modifier) { Text(text, color = DoctorStatusWarn, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) }
}

/** The option a decision stands at: a green tick once confirmed, Rust while it is only suggested. */
@Composable private fun GocChoiceLine(label: String, confirmed: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        if (confirmed) { Icon(Icons.Default.CheckCircle, null, tint = DoctorStatusGood, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (confirmed) DoctorCardInk else RustText)
    }
}

/** Words that were said, as a quote with a rule on its left; [struck] for an earlier statement the person later changed. */
@Composable private fun GocQuote(text: String, struck: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (struck) DoctorHairline else Rust.copy(alpha = .45f), CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic, color = if (struck) DoctorCardSub else DoctorCardInk,
            textDecoration = if (struck) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f).padding(vertical = 2.dp))
    }
}
