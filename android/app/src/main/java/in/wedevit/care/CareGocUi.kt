package `in`.wedevit.care

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.IconButton
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
/** The page band and speaker chips (6 Nov AI-FIR grammar): a dark Sahara band, and two distinct speaker colours that carry white text above 4.5:1. */
/** Goals of care keeps its section colour (indigo) on the band, as on Today and the doctor's hub. */
private val GocBandColor get() = SaharaSection.GoalsOfCare.band
private val GocBandSub get() = SaharaSection.GoalsOfCare.wash
private val GocBandDone = Color(0xFF9BDDB0)
private val GocDoctorChip get() = SaharaTone.Rust.ink
private val GocPatientChip get() = SaharaTone.Teal.solid

/** The card in the wizard's treatment step (V4 and V5 already passed there). Opens the talk over the wizard. */
@Composable internal fun GocConversationCard(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit) {
    val t = careText(); val hindi = careHindi()
    val mic = rememberSaharaPermission(SaharaPermission.Microphone)
    var open by rememberSaveable(d.id) { mutableStateOf(false) }
    // Consent is ticked here, so one Record tap opens the talk already recording (6 Nov: 8 taps to a locked note became 6).
    var agreedAt by rememberSaveable(d.id) { mutableStateOf(0L) }
    var startNow by rememberSaveable(d.id) { mutableStateOf(false) }
    var gemini by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { gemini = gocOnlineEngines().gemini }
    val own = d.gocTranscript?.takeIf { it.planVersion == d.version }
    val canRecord = own == null && LocalGocFolder.current != null
    PlanCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(if (own?.signOff != null) DoctorStatusGood else Rust, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(if (own?.signOff != null) Icons.Default.Lock else Icons.Default.GraphicEq, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Goals of care", "देखभाल के लक्ष्य"), fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = SaharaTitleFont, color = DoctorCardInk, modifier = Modifier.a11yHeading())
                own?.let { g -> Text(GocNote.kindLabel(g.noteKind).pick(hindi), fontSize = 14.sp, color = DoctorCardSub) }
            }
            own?.let { g -> GocStatusChip(if (g.signOff != null) t("Locked", "लॉक") else t("Saved", "सहेजा गया"), done = g.signOff != null) }
        }
        if (canRecord) Surface(color = if (agreedAt > 0) GocTickSoft else Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(if (agreedAt > 0) 1.5.dp else 1.dp, if (agreedAt > 0) DoctorStatusGood else DoctorCardSub.copy(alpha = .5f)), modifier = Modifier.fillMaxWidth().testTag("goc-card-consent-box")) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Box(Modifier.testTag("goc-card-consent")) { CheckLine(agreedAt > 0, { agreedAt = if (it) System.currentTimeMillis() else 0L }, t("Everyone present agrees to this conversation being recorded and written down", "मौजूद सभी लोग इस बातचीत को रिकॉर्ड करने और लिखने के लिए राज़ी हैं")) }
                if (gemini) Text(t("Audio is sent to Google Gemini to transcribe.", "ऑडियो लिखने के लिए Google Gemini को भेजा जाता है।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 10.dp))
            }
        }
        val press = remember { MutableInteractionSource() }
        Button(onClick = { startNow = canRecord && agreedAt > 0; open = true }, interactionSource = press, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).saharaPress(press).testTag("goc-open")) {
            Icon(if (own != null) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (own != null) t("Open", "खोलें") else t("Record", "रिकॉर्ड करें"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
    if (open) Dialog(onDismissRequest = { open = false }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(color = Cream, modifier = Modifier.fillMaxSize().imePadding()) { GocConversationPage(d, update, LocalGocFolder.current, mic::request, agreedAt = agreedAt, startRecording = startNow, onClose = { open = false; startNow = false }) }
    }
}

/**
 * The doctor's Goals of care tab: the talk as its own page (Record, Review, Sign), titled with the patient's name,
 * not as step 6 of the plan. Leaving saves the draft as the plan wizard does; [onFullPlan] opens the 10-step plan.
 */
@Composable internal fun GocTabFlow(record: EctprRecord, canEdit: Boolean, onSave: (EctprRecord) -> Boolean, onClose: () -> Unit, onFullPlan: (EctprRecord?) -> Unit) {
    val t = careText(); val hindi = careHindi()
    val mic = rememberSaharaPermission(SaharaPermission.Microphone)
    var d by remember(record.id) { mutableStateOf(record) }
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
    val today = LocalDate.now()
    val blockers = EctprRules.saveBlockers(d, today)
    // The plan's rules can block its save (V4, V5, DNACPR), and this page has no control for them. The talk is still kept; its choices wait for the full plan.
    fun keepTalk(): Boolean { val kept = GocKeep.talkOnly(record, d, today); return samePlan(kept, record) || onSave(kept) }
    fun leave(then: () -> Unit) { if (samePlan(d, record)) then() else if (blockers.isEmpty()) { if (onSave(d)) then() } else leaving = then }
    // The full plan opens with this talk's unsaved choices, so the rules that block them can be fixed there.
    fun fullPlan() { if (samePlan(d, record)) onFullPlan(null) else if (blockers.isEmpty()) { if (onSave(d)) onFullPlan(null) } else if (keepTalk()) onFullPlan(d) }
    androidx.activity.compose.BackHandler { leave(onClose) }
    GocConversationPage(d, { change -> d = change(d) }, LocalGocFolder.current, mic::request, onClose = { leave(onClose) }, onFullPlan = if (canEdit) { { fullPlan() } } else null)
    leaving?.let { then -> androidx.compose.material3.AlertDialog(onDismissRequest = { leaving = null }, title = { Text(t("Can't save yet", "अभी सहेजा नहीं जा सकता")) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blockers.forEach { Text("• " + it.text(hindi), style = MaterialTheme.typography.bodySmall) }
            Text(t("The talk is kept. These choices are saved once this is fixed in the full care plan.", "बातचीत सहेजी रहेगी। पूरी देखभाल योजना में यह ठीक होने पर ये फ़ैसले सहेजे जाएँगे।"), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { leaving = null; if (canEdit) fullPlan() }) { Text(if (canEdit) t("Fix in the full care plan", "पूरी देखभाल योजना में ठीक करें") else t("Fix them", "ठीक करें")) } },
        dismissButton = { TextButton(onClick = { leaving = null; if (keepTalk()) then() }) { Text(t("Leave, keep the talk", "बातचीत सहेजकर बाहर जाएँ"), color = RustText) } }) }
}

/** The way from the talk into the 10-step plan, named for what it is. */
@Composable private fun GocFullPlanButton(onClick: () -> Unit) { val t = careText()
    SaharaRegister(Modifier.testTag("goc-full-plan")) {
        SaharaListRow(t("Make the full care plan", "पूरी देखभाल योजना बनाएँ"), detail = t("All 10 parts of the emergency care plan, from patient details to signatures", "आपातकालीन देखभाल योजना के सभी 10 हिस्से, मरीज़ की जानकारी से हस्ताक्षर तक"),
            icon = Icons.Default.Checklist, tone = SaharaTone.Marigold, onClick = onClick)
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
    // Framed: each section of the form is its own card, so the long form reads as clear blocks.
    val card = if (framed) Modifier.background(Paper, RoundedCornerShape(16.dp)).border(1.dp, DoctorHairline, RoundedCornerShape(16.dp)).padding(16.dp) else Modifier
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (framed) 12.dp else 0.dp)) {
        sections.forEachIndexed { index, section ->
            Column(Modifier.fillMaxWidth().then(card).padding(top = if (framed || index == 0) 0.dp else 24.dp, bottom = if (framed) 0.dp else 8.dp).testTag("goc-form-section-${section.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.widthIn(min = 40.dp).heightIn(min = 40.dp).background(Rust, RoundedCornerShape(12.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                        Text(section.id, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = SaharaTitleFont, fontSize = 17.sp, maxLines = 1, softWrap = false)
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

@Composable private fun GocConversationPage(d: EctprRecord, update: ((EctprRecord) -> EctprRecord) -> Unit, folder: File?, withMic: (() -> Unit) -> Unit, agreedAt: Long = 0L, startRecording: Boolean = false, onClose: () -> Unit, onFullPlan: (() -> Unit)? = null) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current; val scope = rememberCoroutineScope(); val owner = LocalLifecycleOwner.current; val view = LocalView.current
    val recorder = remember(folder, d.patientId) { folder?.let { GocRecorder(context, it, d.patientId, scope) } }
    val engine = remember { GocSpeechEngine(context) }
    val draftFile = remember(folder, d.id) { folder?.let { GocDraftFile(it, d.id) } }
    val restored = remember(d.id) { draftFile?.read() }
    val initial = remember(d.id) { restored?.transcript ?: d.gocTranscript?.takeIf { it.planVersion == d.version } }

    val signedInDoctor = LocalSignedInDoctor.current
    var participants by rememberSaveable(d.id) { mutableStateOf(initial?.participants.orEmpty()) }
    // The signed-in doctor is in the room; their name is not asked again (still editable).
    LaunchedEffect(d.id, signedInDoctor?.name) { if (participants.isBlank() && initial == null && signedInDoctor != null) participants = signedInDoctor.name }
    var consentAt by rememberSaveable(d.id) { mutableStateOf(initial?.consentAt?.takeIf { it > 0 } ?: agreedAt) }
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
    // Gemini's reading of the whole talk onto the legal form wins while the transcript is unchanged; otherwise (offline, Gemini failed, or lines edited) the on-phone reading is used.
    var geminiReading by remember(d.patientId) { mutableStateOf<Pair<String, GocGeminiAnalysis>?>(null) }
    val extraction = remember(text, geminiReading) { geminiReading?.takeIf { it.first == text }?.second?.extraction ?: GocExtract.extract(text) }
    val busy = operation.isNotBlank()
    val covered = remember(text) { GocGuide.covered(text) }
    val undiscussed = GocGuide.undiscussed(extraction, covered, guideTicked, topics, consentAt > 0)
    val suggestions = remember(extraction, lines, topics) { GocSuggest.all(extraction, lines).filter { (dd, id, _) -> topics[dd]?.let { it.status == GocTopicStatus.CONFIRMED && (dd != GocDecision.VALUES || it.optionId == id) } != true } }

    fun build(now: String = "") = GocTranscript(
        lines = lines.filter { it.text.isNotBlank() }, language = GocSpeechLanguage.valueOf(language).code,
        source = if (model.isNotBlank()) GocTranscript.SOURCE_RECORDED else GocTranscript.SOURCE_TYPED,
        model = model, participants = names.joinToString("\n"), consentAt = consentAt, planVersion = d.version,
        topics = topics.values.sortedBy { it.decision.ordinal }, note = note, noteKind = GocNote.kind(topics.values), reviewedAt = now, reviewedBy = if (now.isBlank()) "" else signedInDoctor?.name?.takeIf { it.isNotBlank() } ?: d.createdBy, audioKept = false,
        signOff = signOff, transcribedBy = transcribedBy,
    )
    LaunchedEffect(Unit) { gemini = gocOnlineEngines().gemini }
    // Choices this talk confirmed that the plan no longer holds (the app was closed before the plan saved, or the plan's rules blocked it) come back.
    LaunchedEffect(d.id) { initial?.topics?.let { saved -> if (GocKeep.restore(d, saved) != d) update { GocKeep.restore(it, saved) } } }
    // Autosave, so a killed app or a long job loses nothing the doctor already corrected. Typed or pasted words are kept too; they need no recording consent.
    LaunchedEffect(lines, note, topics, consentAt, participants) {
        // A locked note is saved on the plan and its draft is gone; never bring the draft back after the lock.
        if ((consentAt > 0 || lines.isNotEmpty()) && draftFile != null && signOff == null) { delay(1200); if (signOff == null) { val snapshot = GocDraft(build(), emptySet()); withContext(Dispatchers.IO) { draftFile.write(snapshot) } } }
    }
    // Leaving within the autosave's wait still keeps the last confirm or edit.
    DisposableEffect(draftFile) { onDispose { if (draftFile != null && signOff == null && (consentAt > 0 || lines.isNotEmpty())) draftFile.write(GocDraft(build(), emptySet())) } }
    DisposableEffect(recorder, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { recorder?.stop(); recorder?.stopPlayback(); task?.cancel() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder?.close(); task?.cancel() }
    }
    val awake = recorderState.recording || busy
    DisposableEffect(awake) { view.keepScreenOn = awake; onDispose { view.keepScreenOn = false } }
    // Consent was ticked on the card, so its Record tap starts recording here, once.
    var autoStarted by rememberSaveable(d.id) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (startRecording && !autoStarted && recorder != null && consentAt > 0 && stage == GocStage.CAPTURE && !recorderState.recording) {
            autoStarted = true; withMic { recorder.start(consentNames.joinToString("\n"), consentNames.toSet(), purpose = true, storage = true, access = true) }
        }
    }

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
        result.analysis?.let { geminiReading = GocLines.text(lines) to it }
        added.isNotEmpty()
    } catch (c: CancellationException) { throw c } catch (_: Exception) { false }

    /** Picks the engine by itself: Gemini when the server has it and everyone agreed, else Whisper on this phone (downloaded once), then speakers on this phone. */
    fun transcribe(clip: DoctorAudioClip) = run(readingLabel) {
        recorder?.stopPlayback(); lastClip = clip; askLanguage = false
        val before = lines.size
        if (!(gemini && consentAt > 0 && readOnline(clip))) {
            if (!engine.models.ready()) { requireWhisperDownloadAllowed(context); operation = t("Downloading", "डाउनलोड हो रहा है"); engine.models.download { done, total -> progressFlow.value = done to total } }
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
            // Whisper wrote the words on this phone; Gemini may still read that text onto the legal form (text is small, so it often gets through when audio did not).
            if (gemini && consentAt > 0 && lines.size > before) runCatching {
                val heard = lines.mapIndexed { i, l -> GocGeminiLine(i.toDouble(), when (l.party) { GocParty.DOCTOR_SIDE -> "DOCTOR"; GocParty.PATIENT_PARTY -> "PATIENT_PARTY"; else -> "UNKNOWN" }, "", l.text) }
                analyzeGocTextWithGemini(heard, OfflineWhisperModel.id, GocSpeechLanguage.valueOf(language).code.ifBlank { null }, d.patientId, clip.id)?.let { geminiReading = GocLines.text(lines) to it }
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
        note = GocNote.draft(d, topics, names.joinToString("\n"), hindi, GocChecklist.open(GocGuide.undiscussed(extraction, covered, guideTicked, topics, consentAt > 0)).map { it.title })
        update { it.copy(gocTranscript = build(planNow())) }
        stage = GocStage.NOTE
    }
    fun finish(close: Boolean = true) {
        update { it.copy(gocTranscript = build(planNow())) }
        recorderState.clips.forEach { recorder?.delete(it) }
        draftFile?.delete()
        if (close) onClose()
    }

    // Record → Review → Sign: where the talk is now, always on screen in the band, with the patient's name.
    val step = when (stage) { GocStage.CAPTURE -> 0; GocStage.REVIEW -> 1; GocStage.NOTE -> 2 }
    Column(Modifier.fillMaxSize().testTag("goc-page")) {
    GocBand(t("Goals of care", "देखभाल के लक्ष्य"), d.patient.name, step, signOff != null, onClose)
    SaharaStateSwitch(stage, Modifier.weight(1f), label = "goc stage") { shown ->
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        if (shown == GocStage.CAPTURE) item(key = "capture") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Consent: one tick for everyone in the room; the record button stays off until it is ticked. Hidden while recording (it was given).
                if (!recorderState.recording && !busy) Surface(color = if (consentAt > 0) GocTickSoft else Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(if (consentAt > 0) 1.5.dp else 1.dp, if (consentAt > 0) DoctorStatusGood else DoctorCardSub.copy(alpha = .5f)), modifier = Modifier.fillMaxWidth().testTag("goc-consent")) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        CheckLine(consentAt > 0, { consentAt = if (it) System.currentTimeMillis() else 0L }, t("Everyone present agrees to this conversation being recorded and written down", "मौजूद सभी लोग इस बातचीत को रिकॉर्ड करने और लिखने के लिए राज़ी हैं"))
                        if (gemini) Text(t("Audio is sent to Google Gemini to transcribe.", "ऑडियो लिखने के लिए Google Gemini को भेजा जाता है।"), fontSize = 14.sp, lineHeight = 20.sp, color = DoctorCardSub, modifier = Modifier.padding(start = 12.dp, end = 8.dp, bottom = 10.dp).testTag("goc-gemini-line"))
                    }
                }
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
                    if (recorderState.recording) OutlinedButton(onClick = { if (recorderState.paused) recorder.resume() else recorder.pause() }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-pause")) {
                        Icon(if (recorderState.paused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = RustText); Spacer(Modifier.width(8.dp))
                        Text(if (recorderState.paused) t("Resume", "फिर शुरू") else t("Pause", "रोकें"), color = RustText, fontSize = 16.sp)
                    }
                    recorderState.error?.let { Text(it.pick(hindi), color = MaterialTheme.colorScheme.error, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.announce()) }
                }
                if (!recorderState.recording && !busy) CareField(participants, { participants = it.take(600) }, t("Who's present", "कौन मौजूद है"))
                if (!recorderState.recording && !busy) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (recorder != null) OutlinedButton(onClick = { upload.launch(arrayOf("audio/*", "video/webm")) }, enabled = consentAt > 0, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, if (consentAt > 0) RustText else DoctorCardSub.copy(alpha = .4f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = RustText), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("goc-upload")) {
                        Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(6.dp)); Text(t("Upload", "अपलोड करें"), textAlign = TextAlign.Center)
                    }
                    OutlinedButton(onClick = { typing = !typing }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, RustText), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("goc-type")) {
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
                    Button(onClick = { lines = lines + GocLines.fromText(typed); typed = ""; typing = false; stage = GocStage.REVIEW }, enabled = typed.isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-add-typed")) { Text(t("Use", "इस्तेमाल करें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        if (shown == GocStage.REVIEW) {
            item(key = "checklist") {
                PlanCard {
                    val rows = GocChecklist.ROWS
                    val doneCount = rows.count { GocChecklist.done(it, undiscussed) }
                    val allDone = doneCount == rows.size
                    Row(Modifier.semantics(mergeDescendants = true) {}.testTag("goc-guide-title"), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Checklist, null, tint = RustText); Spacer(Modifier.width(8.dp))
                        Text(t("Checklist", "सूची"), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f).a11yHeading())
                        Surface(color = if (allDone) DoctorStatusGoodBg else GocSand, shape = CircleShape) {
                            Text("$doneCount / ${rows.size}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (allDone) DoctorStatusGood else DoctorCardInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                    LinearProgressIndicator(progress = { doneCount.toFloat() / rows.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = DoctorStatusGood, trackColor = GocSand)
                    Column {
                        rows.forEachIndexed { index, item ->
                            val on = GocChecklist.done(item, undiscussed)
                            if (index > 0) HorizontalDivider(color = DoctorHairline)
                            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (on) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (on) DoctorStatusGood else DoctorCardFaint, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(item.title.pick(hindi), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) DoctorCardInk else DoctorCardSub, modifier = Modifier.weight(1f))
                                item.section.let { sec -> Surface(color = GocSand, shape = CircleShape) { Text(sec, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RustText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) } }
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
                            Text(t("Tap a line the doctor said.", "डॉक्टर की कोई पंक्ति दबाएँ।"), fontSize = 15.sp, color = DoctorCardSub)
                        }
                    }
                }
            }
            listOf("II" to t("Medical information and patient values", "चिकित्सा जानकारी और मरीज़ के मूल्य"), "III" to t("Decisive actions", "निर्णायक फ़ैसले")).forEach { (sec, secTitle) ->
                val items = GocPlan.ORDER.filter { dd -> (if (dd == GocDecision.VALUES) "II" else "III") == sec && (extraction.hitsFor(dd).isNotEmpty() || topics[dd]?.status == GocTopicStatus.CONFIRMED) }
                if (items.isNotEmpty()) {
                    item(key = "sec-$sec") {
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.widthIn(min = 32.dp).heightIn(min = 32.dp).background(Rust, RoundedCornerShape(10.dp)).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) { Text(sec, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = SaharaTitleFont, fontSize = 14.sp, maxLines = 1, softWrap = false) }
                            Spacer(Modifier.width(12.dp)); Text(secTitle, color = RustText, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.a11yHeading())
                        }
                    }
                    items.forEach { dd -> item(key = "decision-${dd.name}") { GocSuggestionCard(d, dd, GocLatest.standing(extraction, dd, lines), lines, topics[dd]) { id -> confirm(dd, id) } } }
                }
            }
            item(key = "transcript") {
                GocTranscriptCard(lines, hindi, onAddMore = { stage = GocStage.CAPTURE }, editing = editing,
                    onLine = { index -> val line = lines[index]
                        if (askDoctor) { val who = line.who; lines = lines.map { l -> if (who.isNotBlank() && l.who == who || l === line) l.copy(party = GocParty.DOCTOR_SIDE, guessed = false) else if (who.isNotBlank() && l.who.startsWith("Speaker ") && l.party != GocParty.DOCTOR_SIDE) l.copy(party = GocParty.PATIENT_PARTY, guessed = false) else l }; askDoctor = false }
                        else editing = if (editing == index) -1 else index },
                    onParty = { index -> val line = lines[index]; lines = lines.toMutableList().also { it[index] = line.copy(party = GocLines.nextParty(line.party), guessed = false) } },
                    onEdit = { index, v -> lines = lines.toMutableList().also { it[index] = lines[index].copy(text = v) } })
            }
            item(key = "finish") {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val openRows = GocChecklist.open(undiscussed)
                    if (openRows.isNotEmpty()) Surface(color = DoctorStatusWarnBg, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("goc-not-discussed")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, null, tint = DoctorStatusWarn, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                                Text(t("Not discussed", "बात नहीं हुई"), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DoctorCardInk)
                            }
                            openRows.forEachIndexed { index, item ->
                                if (index > 0) HorizontalDivider(color = DoctorStatusWarn.copy(alpha = .2f))
                                Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.title.pick(hindi), Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardInk)
                                    TextButton(onClick = { guideTicked = guideTicked + item.items }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Discussed", "बात हुई"), color = RustText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                    }
                    // One tap confirms the suggestions shown above (each still has Change) and opens the form to sign.
                    Button(onClick = { confirmAll(); continueToNote() }, enabled = !busy && text.isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(if (suggestions.isNotEmpty()) "goc-confirm-all" else if (undiscussed.isNotEmpty()) "goc-continue-anyway" else "goc-finish")) {
                        if (suggestions.isNotEmpty()) { Icon(Icons.Default.DoneAll, null); Spacer(Modifier.width(8.dp)) }
                        Text(if (suggestions.isNotEmpty()) t("Confirm ${suggestions.size} and continue", "${suggestions.size} पक्के करें, आगे बढ़ें") else if (openRows.isNotEmpty()) t("Continue anyway", "फिर भी आगे बढ़ें") else t("Continue", "आगे बढ़ें"), fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                    }
                }
            }
        }

        if (shown == GocStage.NOTE) {
            // Locked: the result first, with its status pill and who signed; the form follows below.
            if (signOff != null) item(key = "done") {
                Surface(color = GocTickSoft, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.5.dp, DoctorStatusGood), modifier = Modifier.fillMaxWidth().testTag("goc-signed")) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GocSuccessTick()
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).announce(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                GocStatusChip(t("Signed and locked", "साइन होकर बंद"), done = true)
                                Text(signOff?.signers.orEmpty().joinToString(", "), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                            }
                        }
                        Button(onClick = onClose, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("goc-done")) { Text(t("Done", "हो गया"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            item(key = "note-tools") {
                var problem by remember { mutableStateOf("") }
                // The same status line as the doctor-side PDF, so an unsigned draft never reads as a finished form.
                val pdfStatus = signOff?.let { so -> t("Signed and locked", "साइन होकर बंद") + (if (so.signers.isNotEmpty()) ": " + so.signers.joinToString(", ") else "") } ?: t("Not signed yet", "अभी साइन नहीं")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (signOff == null) TextButton(onClick = { stage = GocStage.REVIEW }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("Back", "वापस"), color = RustText) }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { scope.launch {
                        val file = runCatching { withContext(Dispatchers.IO) { File(File(context.cacheDir, "doctor-preview-exports").apply { mkdirs() }, "goals-of-care-${d.id.filter { it.isLetterOrDigit() || it == '-' }.take(48)}.pdf").also {
                            CarePlanPdf.gocNote(it, d, hindi, LocalDate.now(), note = note, status = pdfStatus, topics = topics, signers = signOff?.formSigners().orEmpty()) } } }.getOrNull()
                        if (file == null) problem = t("Couldn't make the PDF. Try again.", "PDF नहीं बना। फिर कोशिश करें।") else runCatching { DoctorGocPdf.share(context, file, t("Share PDF", "PDF साझा करें")) }
                    } }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-pdf")) { Icon(Icons.Default.PictureAsPdf, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text("PDF", color = RustText) }
                }
                if (problem.isNotBlank()) Text(problem, color = MaterialTheme.colorScheme.error, fontSize = 15.sp, modifier = Modifier.announce())
            }
            item(key = "form") { GocFormView(GocForm.sections(d, topics, signOff?.formSigners().orEmpty()), Modifier.testTag("goc-form")) }
            item(key = "sign") {
                PlanCard {
                    if (note.isNotBlank()) GocSignOff(noteId = "${d.id}:v${d.version}:goc", noteText = note, store = signStore, doctorPrefill = signedInDoctor?.name.orEmpty(), signerPrefill = d.patient.name, showNote = false,
                        doctorRegistrationPrefill = signedInDoctor?.registration.orEmpty(), familyPrefill = d.decisionMakers.primary?.takeIf { it.name.isNotBlank() }?.let { it.name to it.relation },
                        onComplete = { record ->
                            signOff = GocSignOffSummary(record.id, record.sealSha256.orEmpty(), record.lockedAt.orEmpty(), record.signatures.map { it.name },
                                record.signatures.map { it.role }, record.signatures.map { it.registrationNo.orEmpty() }, record.signatures.map { it.method }, record.signatures.map { it.signedAt })
                            // Locked is the end: the note is saved and the audio and draft are cleared now, so Done only closes.
                            finish(close = false)
                        },
                        onInvalidated = { signOff = null })
                }
            }
        }
        if (onFullPlan != null && !recorderState.recording && !busy) item(key = "full-plan") { GocFullPlanButton(onFullPlan) }
    }
    }
    }
}

/** The page's dark band: back, title with the patient's name, and the three steps (Record, Review, Sign), each named under its bar. */
@Composable private fun GocBand(title: String, name: String, step: Int, locked: Boolean, onBack: () -> Unit) { val t = careText()
    val labels = listOf(t("Record", "रिकॉर्ड"), t("Review", "समीक्षा"), t("Sign", "हस्ताक्षर"))
    Surface(color = GocBandColor, contentColor = Color.White, modifier = Modifier.fillMaxWidth().testTag("goc-header")) {
        Column(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("goc-header-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(title, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, color = Color.White)
                    if (name.isNotBlank()) Text(name, fontSize = 15.sp, lineHeight = 20.sp, color = GocBandSub, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.testTag("goc-header-name"))
                }
                if (locked) Surface(color = GocBandDone, contentColor = SaharaColors.Ink, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("Locked", "लॉक"), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 12.dp).testTag("goc-steps"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { i, label ->
                    val done = i < step || locked; val now = i == step && !locked
                    val fill by androidx.compose.animation.animateColorAsState(if (done) GocBandDone else if (now) Color.White else Color.White.copy(alpha = .28f), SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "goc-step")
                    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(fill))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (done) { Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = GocBandDone); Spacer(Modifier.width(4.dp)) }
                            Text(label, fontSize = 15.sp, fontWeight = if (now) FontWeight.Bold else FontWeight.Medium, color = if (now || done) Color.White else GocBandSub, maxLines = 1, softWrap = false,
                                modifier = if (now) Modifier.testTag("goc-step-now") else Modifier)
                        }
                    }
                }
            }
        }
    }
}

/** The tick on a locked note: it pops in once (no motion when animations are off). */
@Composable private fun GocSuccessTick() {
    val shown = remember { androidx.compose.animation.core.MutableTransitionState(!SaharaMotion.enabled).apply { targetState = true } }
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(shown, enter = androidx.compose.animation.scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS + 80), initialScale = .6f) + androidx.compose.animation.fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS))) {
            Box(Modifier.size(44.dp).background(DoctorStatusGood, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
    }
}

/** The words of the talk as compact lines, always shown below the decisions: a speaker chip (tap to change who said it), then the words (tap to edit). */
@Composable private fun GocTranscriptCard(lines: List<GocLine>, hindi: Boolean, editing: Int, onLine: (Int) -> Unit, onParty: (Int) -> Unit, onEdit: (Int, String) -> Unit, onAddMore: () -> Unit) { val t = careText()
    Surface(color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaContentSize().testTag("goc-transcript")) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GraphicEq, null, tint = RustText, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
                Text(t("Transcript", "लिखाई"), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                Text("${lines.size}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DoctorCardSub)
            }
            Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lines.forEachIndexed { index, line ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onLine(index) }.padding(horizontal = 4.dp, vertical = 4.dp).testTag("goc-line-$index"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val (bg, fg) = when (line.party) { GocParty.DOCTOR_SIDE -> GocDoctorChip to Color.White; GocParty.PATIENT_PARTY -> GocPatientChip to Color.White; else -> Paper to DoctorCardSub }
                        Surface(onClick = { onParty(index) }, color = bg, contentColor = fg, shape = CircleShape, border = if (line.party == GocParty.UNSET) BorderStroke(1.dp, DoctorCardSub.copy(alpha = .6f)) else null, modifier = Modifier.testTag("goc-party-$index")) {
                            Text((if (line.who.isNotBlank()) line.who + " · " else "") + GocLines.label(line.party).pick(hindi), fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                        if (editing == index) CareField(line.text, { v -> onEdit(index, v) }, t("Edit", "बदलें"))
                        else Text(line.text, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk, modifier = Modifier.padding(start = 2.dp))
                    }
                }
                OutlinedButton(onClick = onAddMore, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.5.dp, RustText), modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) { Icon(Icons.Default.Mic, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("Add more", "और जोड़ें"), color = RustText) }
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
            SaharaProgressBar(if (progress.second > 0) (progress.first.toFloat() / progress.second).coerceIn(0f, 1f) else null)
            OutlinedButton(onClick = onStop, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, RustText), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-cancel")) {
                Icon(Icons.Default.Stop, null, tint = RustText); Spacer(Modifier.width(6.dp)); Text(t("Stop", "रोकें"), color = RustText)
            }
        }
    }
}

/** The big, calm record button: a soft ring that breathes with the voice level while recording. */
@Composable private fun GocRecordButton(recording: Boolean, paused: Boolean, level: Float, enabled: Boolean, onClick: () -> Unit) { val t = careText()
    val ring by animateFloatAsState(if (recording && !paused) level.coerceIn(0f, 1f) else 0f, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "voice level")
    // A slow breath while recording (about one every 2.4 s), only while the screen records and animations are on.
    val breathing = recording && !paused && SaharaMotion.enabled
    val breath = if (breathing) androidx.compose.animation.core.rememberInfiniteTransition(label = "record-pulse").animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "breath").value else 0f
    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        if (recording) Box(Modifier.size(156.dp + 24.dp * breath + 16.dp * ring).background(Rust.copy(alpha = .08f + .10f * breath + .10f * ring), CircleShape))
        if (recording) Box(Modifier.size(156.dp + 16.dp * ring).background(Rust.copy(alpha = .14f), CircleShape))
        Button(onClick = onClick, enabled = enabled, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = if (recording) DoctorCardInk else Rust, contentColor = Color.White),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp), contentPadding = PaddingValues(0.dp), modifier = Modifier.size(148.dp).testTag("goc-record")) {
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
