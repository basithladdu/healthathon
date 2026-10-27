package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Slider-to-mood mapping for the daily check-in. Titles are a fixed vocabulary parsed by DoctorReportedMood and the progress history. */
internal object CheckInMood {
    const val GOOD = "A good day"; const val OKAY = "An okay day"; const val HARD = "A hard day"
    const val noteLimit = 4000
    fun title(score: Float): String = when { score < 1f / 3f -> HARD; score < 2f / 3f -> OKAY; else -> GOOD }
    /** Symptom notes also use checkin storage; only an ordinary mood entry may be updated here. */
    fun today(items: List<CareItem>, personId: String, dayStart: Long): CareItem? = items.firstOrNull {
        it.kind == "checkin" && it.personId == personId && it.created >= dayStart && !SymptomNoteCodec.isStructured(it.detail)
    }
}

@Composable fun FeedbackScreen(accountScope: String, surface: String, onClose: () -> Unit, careModel: CareModel? = null, personId: String? = null) {
    if (careModel == null || personId == null) AppFeedbackScreen(accountScope, surface, onClose)
    else FeedbackExperienceScreen(accountScope, surface, onClose, careModel, personId)
}

/** Shared face-and-slider presentation; app feedback and patient check-ins retain distinct save destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FeedbackExperienceScreen(accountScope: String, surface: String, onClose: () -> Unit, careModel: CareModel? = null, personId: String? = null) {
    val checkIn = careModel != null && personId != null
    val items by (careModel?.items ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<CareItem>()) }).collectAsStateWithLifecycle()
    val busy by (careModel?.busy ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val modelError by (careModel?.actionError ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }).collectAsStateWithLifecycle()
    val selectedPerson by (careModel?.selected ?: remember { kotlinx.coroutines.flow.MutableStateFlow("") }).collectAsStateWithLifecycle()
    val dayStart = remember { java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val today = if (checkIn) CheckInMood.today(items, personId!!, dayStart) else null
    if (checkIn) {
        LaunchedEffect(Unit) { careModel!!.actionError.value = null }
        LaunchedEffect(selectedPerson) { if (selectedPerson != personId) onClose() }
    }
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val store = remember(accountScope, surface) { FeedbackStore(context, accountScope, surface) }
    val initial = remember(store) { if (checkIn) null else store.latest() }
    val todayScore = when (today?.title) { CheckInMood.GOOD -> 1f; CheckInMood.HARD -> 0f; CheckInMood.OKAY -> .5f; else -> null }
    var score by rememberSaveable(accountScope, surface) { mutableFloatStateOf(initial?.score ?: todayScore ?: .5f) }
    var note by rememberSaveable(accountScope, surface) { mutableStateOf(initial?.note ?: today?.detail.orEmpty()) }
    var savedScore by rememberSaveable(accountScope, surface) { mutableFloatStateOf(initial?.score ?: todayScore ?: .5f) }
    var savedNote by rememberSaveable(accountScope, surface) { mutableStateOf(initial?.note ?: today?.detail.orEmpty()) }
    var saved by rememberSaveable(accountScope, surface) { mutableStateOf(false) }
    var error by remember { mutableStateOf(if (checkIn) null else store.error()) }
    var help by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val dirty = score != savedScore || note != savedNote
    val close = { focus.clearFocus(); if (dirty) discard = true else onClose() }
    BackHandler { close() }
    val expression by animateFloatAsState(score, tween(120), label = "feedback expression")
    val wash by animateColorAsState(if (score <= .5f) lerp(Peach, Cream, score * 2f) else lerp(Cream, SaharaSoftBlue, (score - .5f) * 2f), tween(160), label = "feedback warmth")
    val label = if (checkIn) CheckInMood.title(score) else FeedbackRules.label(score)
    val limit = if (checkIn) CheckInMood.noteLimit else FeedbackRules.noteLimit
    val onSaved = { saved = true; savedScore = score; savedNote = note }

    Column(Modifier.fillMaxSize().background(wash).safeDrawingPadding().imePadding().testTag("feedback-screen").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("feedback-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (checkIn) "Close check-in" else "Close feedback", tint = Ink) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { help = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.Info, "About this", tint = DoctorAccent) }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (checkIn) "How was your day?" else "How was your Sahara experience?", color = Ink, fontFamily = SaharaTitleFont, fontSize = 32.sp, lineHeight = 37.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(if (checkIn) (if (today != null && !saved) "You already checked in today. Saving will update it." else "Slide to say how today is going.") else "Slide to rate your experience with Sahara.", color = Ink.copy(alpha = .78f), fontSize = 15.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        FeedbackFace(expression, Modifier.sizeIn(maxWidth = 210.dp, maxHeight = 210.dp).fillMaxWidth().aspectRatio(1f).clearAndSetSemantics { })
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.testTag("feedback-state"), fontFamily = SaharaTitleFont)
        Spacer(Modifier.height(12.dp))
        Slider(value = score, onValueChange = { score = it; saved = false; error = null }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("feedback-slider").semantics { contentDescription = if (checkIn) "Rate how your day went" else "Rate your Sahara experience"; stateDescription = label }, thumb = { Box(Modifier.size(30.dp).background(Rust, CircleShape).border(3.dp, Paper, CircleShape)) }, colors = SliderDefaults.colors(thumbColor = Rust, activeTrackColor = Rust, inactiveTrackColor = Ink.copy(alpha = .14f)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (if (checkIn) listOf(CheckInMood.HARD to 0f, CheckInMood.OKAY to .5f, CheckInMood.GOOD to 1f) else listOf("Not great" to 0f, "Okay" to .5f, "Good" to 1f)).forEach { (name, value) ->
                TextButton(onClick = { score = value; saved = false; error = null }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp).then(if (checkIn) Modifier.weight(1f).testTag("checkin-mood-$value") else Modifier.weight(1f).testTag("feedback-mood-$value"))) { Text(name, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = if (checkIn) 13.sp else 14.sp, color = if (label == name) Rust else Ink.copy(alpha = .75f), fontWeight = if (label == name) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        OutlinedTextField(value = note, onValueChange = { if (it.length <= limit) { note = it; saved = false; error = null } }, label = { Text("Anything to add? (optional)") }, placeholder = { Text(if (checkIn) "How are you feeling today?" else "What felt easy, or what could be clearer?") }, supportingText = { Text(if (checkIn) "Added to the care record. ${note.length}/$limit" else "Saved on this device. ${note.length}/500", fontSize = 12.sp) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("feedback-note"), shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = Rust, cursorColor = Rust, unfocusedBorderColor = Ink.copy(alpha = .25f), focusedContainerColor = Paper.copy(alpha = .7f), unfocusedContainerColor = Paper.copy(alpha = .55f)))
        Spacer(Modifier.height(8.dp))
        (modelError.takeIf { checkIn } ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }); Spacer(Modifier.height(8.dp)) }
        Button(onClick = { focus.clearFocus(); if (checkIn) { val model = careModel!!; val mood = CheckInMood.title(score); if (!canEditCareContext(model, personId!!, model.accountNamespace)) { model.message.value = "Care access changed. Reopen this person's records before saving."; onClose() } else if (today != null) model.updateItem(today.copy(title = mood, detail = note), onSaved) else model.saveItem("checkin", mood, note, 0, onSaved) } else { error = store.save(score, note); if (error == null) onSaved() } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("feedback-save"), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White)) {
            if (saved) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)) }
            Text(if (checkIn) (if (saved) "Check-in saved" else if (today != null) "Update today's check-in" else "Save check-in") else if (saved) "Saved on this device" else "Save", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (checkIn) (if (saved) "Saved to the care record." else "Saved to this person's care record. It syncs when signed in. It is your own report, not an assessment.") else if (saved) "Saved on this device." else "Saved on this device only.", color = Ink.copy(alpha = .72f), fontSize = 12.sp, lineHeight = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (help) AlertDialog(onDismissRequest = { help = false }, containerColor = Paper, title = { Text("About this") }, text = { Text(if (checkIn) "A daily check-in is your own words about how today is. It is added to this person's care record and syncs when signed in. It is not a medical assessment or a score." else "This stays on this device and is separate from patient records. Please leave out personal or health details.") }, confirmButton = { TextButton(onClick = { help = false }) { Text("Got it", color = Rust) } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, containerColor = Paper, title = { Text("Leave without saving?") }, text = { Text("Your latest changes won't be saved. ${if (checkIn) "What you saved before stays in the care record." else "What you saved before stays on this device."}") }, confirmButton = { TextButton(onClick = { discard = false; onClose() }) { Text("Leave", color = Rust) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing", color = Rust) } })
}

@Composable fun FeedbackFace(score: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val value = score.coerceIn(0f, 1f)
        val eyeH = h * FeedbackRules.eyeHeight(value)
        val eyeW = w * FeedbackRules.eyeWidth(value)
        for (x in listOf(.28f, .72f)) drawRoundRect(Ink, Offset(w * x - eyeW / 2f, h * .35f - eyeH / 2f), Size(eyeW, eyeH), CornerRadius(eyeW / 2f, eyeH / 2f))
        val mouth = Path().apply { moveTo(w * .34f, h * .72f); cubicTo(w * .44f, h * (.72f + FeedbackRules.mouthCurve(value)), w * .56f, h * (.72f + FeedbackRules.mouthCurve(value)), w * .66f, h * .72f) }
        drawPath(mouth, Ink, style = Stroke(width = w * .032f, cap = StrokeCap.Round))
    }
}

/** Device-local app experience feedback uses the shared animated face and slider. */
@Composable internal fun AppFeedbackScreen(accountScope: String, surface: String, onClose: () -> Unit) {
    FeedbackExperienceScreen(accountScope, surface, onClose)
}
