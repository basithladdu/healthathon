package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Healing
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
import kotlinx.coroutines.launch

/** Slider-to-mood mapping for the daily check-in. Titles are a fixed vocabulary parsed by DoctorReportedMood and the progress history. */
internal object CheckInMood {
    const val GOOD = "A good day"; const val OKAY = "An okay day"; const val HARD = "A hard day"
    const val noteLimit = 4000
    fun title(score: Float): String = when { score < 1f / 3f -> HARD; score < 2f / 3f -> OKAY; else -> GOOD }
    /** Symptom notes also use checkin storage; only an ordinary mood entry may be updated here. */
    fun today(items: List<CareItem>, personId: String, dayStart: Long): CareItem? = items.firstOrNull {
        it.kind == "checkin" && it.personId == personId && it.created >= dayStart && !SymptomNoteCodec.isStructured(it.detail) && !DistressCodec.isStructured(it.detail)
    }
}

/** [cloud] sends app feedback to Sahara's server when signed in; without it, app feedback stays on this phone. */
@Composable fun FeedbackScreen(accountScope: String, surface: String, onClose: () -> Unit, careModel: CareModel? = null, personId: String? = null, cloud: CareCloud? = null,
    onDistress: () -> Unit = {}, onSymptom: () -> Unit = {}) {
    if (careModel == null || personId == null) AppFeedbackScreen(accountScope, surface, onClose, cloud)
    else FeedbackExperienceScreen(accountScope, surface, onClose, careModel, personId, onDistress = onDistress, onSymptom = onSymptom)
}

/** Where the latest app feedback is: only "sent" once the server confirmed it. */
internal enum class FeedbackSendState { NONE, LOCAL, SENDING, SENT, QUEUED }

/** Shared face-and-slider presentation; app feedback and patient check-ins retain distinct save destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun FeedbackExperienceScreen(accountScope: String, surface: String, onClose: () -> Unit, careModel: CareModel? = null, personId: String? = null, cloud: CareCloud? = null,
    onDistress: () -> Unit = {}, onSymptom: () -> Unit = {}) { val t = careText();
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
    var discard by remember { mutableStateOf(false) }
    val dirty = score != savedScore || note != savedNote
    val close = { focus.clearFocus(); if (dirty) discard = true else onClose() }
    BackHandler { close() }
    val expression by animateFloatAsState(score, tween(120), label = "feedback expression")
    val wash by animateColorAsState(if (score <= .5f) lerp(Peach, Cream, score * 2f) else lerp(Cream, SaharaSoftBlue, (score - .5f) * 2f), tween(160), label = "feedback warmth")
    val label = if (checkIn) CheckInMood.title(score) else FeedbackRules.label(score)
    val shownLabel = if (checkIn) checkInMoodLabel(label, careHindi()) else when (label) { "Not great" -> t("Not great", "अच्छा नहीं"); "Okay" -> t("Okay", "ठीक"); else -> t("Good", "अच्छा") }
    val limit = if (checkIn) CheckInMood.noteLimit else FeedbackRules.noteLimit
    val onSaved = { saved = true; savedScore = score; savedNote = note }
    val signedIn by (cloud?.session ?: remember { kotlinx.coroutines.flow.MutableStateFlow<CareSession?>(null) }).collectAsStateWithLifecycle()
    val account = signedIn?.userId.takeIf { !checkIn && cloud != null }
    var sendState by remember(store) { mutableStateOf(if (!checkIn && store.isQueued(store.latestId())) FeedbackSendState.QUEUED else FeedbackSendState.NONE) }
    var sendError by remember(store) { mutableStateOf<String?>(null) }
    val sendScope = rememberCoroutineScope()
    fun send(to: String) {
        val service = cloud ?: return
        val latestId = store.latestId(); val wasQueued = store.isQueued(latestId)
        sendState = FeedbackSendState.SENDING; sendError = null
        sendScope.launch {
            val failure = store.flush(to) { service.sendAppFeedback(it) }
            sendError = failure
            sendState = when { store.isQueued(latestId) -> FeedbackSendState.QUEUED; wasQueued -> FeedbackSendState.SENT; else -> if (saved) FeedbackSendState.LOCAL else FeedbackSendState.NONE }
        }
    }
    // Retries anything this account queued earlier (offline, or before the server table existed).
    LaunchedEffect(account) { if (account != null && store.pending().any { it.accountId == account }) send(account) }

    Column(Modifier.fillMaxSize().background(wash).safeDrawingPadding().imePadding().testTag("feedback-screen").verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("feedback-close")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (checkIn) t("Close check-in", "आज का हाल बंद करें") else t("Close feedback", "राय बंद करें"), tint = Ink) }
            Spacer(Modifier.weight(1f))
            CareLanguageMenu(compact = true)
        }
        Spacer(Modifier.height(16.dp))
        Text(if (checkIn) t("How was your day?", "आज का दिन कैसा रहा?") else t("How was Sahara?", "सहारा कैसा रहा?"), color = Ink, fontFamily = SaharaTitleFont, fontSize = 32.sp, lineHeight = 37.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        CareMoodFace(expression, Modifier.sizeIn(maxWidth = 210.dp, maxHeight = 210.dp).fillMaxWidth().aspectRatio(1f).clearAndSetSemantics { })
        Spacer(Modifier.height(8.dp))
        Text(shownLabel, fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.testTag("feedback-state"), fontFamily = SaharaTitleFont)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth()) { // up/down page scroll over the slider must not change the score
            Slider(value = score, onValueChange = { score = it; saved = false; error = null }, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("feedback-slider").semantics { contentDescription = if (checkIn) t("Rate how your day went", "आज का दिन कैसा रहा, बताएँ") else t("Rate your Sahara experience", "सहारा का अनुभव बताएँ"); stateDescription = shownLabel }, thumb = { Box(Modifier.size(30.dp).background(Rust, CircleShape).border(3.dp, Paper, CircleShape)) }, colors = SliderDefaults.colors(thumbColor = Rust, activeTrackColor = Rust, inactiveTrackColor = Ink.copy(alpha = .14f)))
            Box(Modifier.matchParentSize().sliderTouchOverlay(30.dp) { score = it; saved = false; error = null })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (if (checkIn) listOf(CheckInMood.HARD to 0f, CheckInMood.OKAY to .5f, CheckInMood.GOOD to 1f) else listOf("Not great" to 0f, "Okay" to .5f, "Good" to 1f)).forEach { (name, value) ->
                TextButton(onClick = { score = value; saved = false; error = null }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = 48.dp).then(if (checkIn) Modifier.weight(1f).testTag("checkin-mood-$value") else Modifier.weight(1f).testTag("feedback-mood-$value"))) { Text(if (checkIn) checkInMoodLabel(name, careHindi()) else when (name) { "Not great" -> t("Not great", "अच्छा नहीं"); "Okay" -> t("Okay", "ठीक"); else -> t("Good", "अच्छा") }, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp, color = if (label == name) RustText else DoctorCardSub, fontWeight = if (label == name) FontWeight.Bold else FontWeight.Normal) }
            }
        }
        OutlinedTextField(value = note, onValueChange = { if (it.length <= limit) { note = it; saved = false; error = null } }, label = { Text(t("Anything to add? (optional)", "कुछ और बताना है? (चाहें तो)")) }, placeholder = { Text(if (checkIn) t("How are you feeling today?", "आज आप कैसा महसूस कर रहे हैं?") else t("What felt easy, or what could be clearer?", "क्या आसान लगा, या क्या और साफ़ हो सकता है?")) }, supportingText = { Text("${note.length}/$limit", fontSize = 14.sp, color = DoctorCardSub) }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth().testTag("feedback-note"), shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Rust, focusedLabelColor = RustText, cursorColor = Rust, unfocusedBorderColor = DoctorCardFaint.copy(alpha = .5f), focusedContainerColor = Paper, unfocusedContainerColor = Paper))
        Spacer(Modifier.height(12.dp))
        // The check-in also asks about distress and links to a symptom note, as the old Today card did.
        if (checkIn && careModel != null) {
            Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("checkin-extras")) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DistressQuestion(careModel, onYes = onDistress)
                    TextButton(onClick = onSymptom, modifier = Modifier.heightIn(min = 48.dp).testTag("today-symptom-note")) {
                        Icon(Icons.Default.Healing, null, Modifier.size(20.dp), tint = Rust); Spacer(Modifier.width(8.dp)); Text(t("Note a symptom", "तबीयत का नोट लिखें"), color = RustText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        (modelError.takeIf { checkIn } ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }); Spacer(Modifier.height(8.dp)) }
        Button(onClick = { focus.clearFocus(); if (saved) return@Button /* already saved and unchanged: a second tap must not add a second check-in before the list refreshes */; if (checkIn) { val model = careModel!!; val mood = CheckInMood.title(score); if (!canEditCareContext(model, personId!!, model.accountNamespace)) { model.message.value = t("Care access changed. Reopen this person's records before saving.", "देखभाल की अनुमति बदल गई है। सहेजने से पहले इस व्यक्ति के रिकॉर्ड फिर खोलें।"); onClose() } else if (today != null) model.updateItem(today.copy(title = mood, detail = note), onSaved) else model.saveItem("checkin", mood, note, 0, onSaved) } else { val to = account; error = store.save(score, note, to); if (error == null) { onSaved(); if (to != null) send(to) else sendState = FeedbackSendState.LOCAL } } }, enabled = !busy && sendState != FeedbackSendState.SENDING, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("feedback-save"), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Rust, contentColor = Color.White)) {
            if (saved && (checkIn || sendState == FeedbackSendState.SENT || sendState == FeedbackSendState.LOCAL)) { Icon(Icons.Outlined.CheckCircle, null); Spacer(Modifier.width(8.dp)) }
            Text(if (checkIn) (if (saved) t("Check-in saved", "आज का हाल सहेज लिया") else if (today != null) t("Update today's check-in", "आज का हाल बदलें") else t("Save check-in", "आज का हाल सहेजें")) else if (sendState == FeedbackSendState.SENDING) t("Sending…", "भेजा जा रहा है…") else if (saved && sendState == FeedbackSendState.SENT) t("Sent to Sahara", "Sahara को भेजा") else if (saved) t("Saved on this phone", "फ़ोन पर सहेजा") else if (account != null) t("Send", "भेजें") else t("Save", "सहेजें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        if (account != null && sendState == FeedbackSendState.QUEUED) TextButton(onClick = { send(account) }, modifier = Modifier.heightIn(min = 48.dp).testTag("feedback-retry")) { Text(t("Try again", "फिर कोशिश करें"), color = RustText) }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, containerColor = Paper, title = { Text(t("Leave without saving?", "बिना सहेजे जाएँ?")) }, confirmButton = { TextButton(onClick = { discard = false; onClose() }) { Text(t("Leave", "जाएँ"), color = Rust) } }, dismissButton = { TextButton(onClick = { discard = false }) { Text(t("Keep editing", "लिखते रहें"), color = Rust) } })
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
@Composable internal fun AppFeedbackScreen(accountScope: String, surface: String, onClose: () -> Unit, cloud: CareCloud? = null) {
    FeedbackExperienceScreen(accountScope, surface, onClose, cloud = cloud)
}

/** App-feedback wording, English · Hindi. "Sent" appears only after the server confirmed the save. */
internal object FeedbackSendCopy {
    val about: String get() = careBilingual("Signed in, your rating and note are sent to the Sahara team with your account so we can improve the app. Signed out, they stay on this phone. This is separate from patient records; please leave out personal or health details.", "साइन इन होने पर आपकी रेटिंग और नोट आपके खाते के साथ Sahara टीम को भेजे जाते हैं। साइन आउट होने पर ये इसी फ़ोन पर रहते हैं। यह मरीज़ के रिकॉर्ड से अलग है; निजी या स्वास्थ्य जानकारी न लिखें।")
    fun status(state: FeedbackSendState, saved: Boolean, signedIn: Boolean, error: String?): String = when {
        state == FeedbackSendState.SENDING -> careBilingual("Sending to the Sahara team…", "Sahara टीम को भेजा जा रहा है…")
        state == FeedbackSendState.QUEUED -> run { val detail = error?.let { ": $it" } ?: "."; careBilingual("Saved on this phone, not sent yet$detail", "फ़ोन पर सहेजा, अभी भेजा नहीं गया$detail") }
        saved && state == FeedbackSendState.SENT -> careBilingual("Sent to the Sahara team. Thank you.", "Sahara टीम को भेज दिया गया। धन्यवाद।")
        saved -> careBilingual("Saved on this phone only. Sign in to send feedback to the Sahara team.", "सिर्फ़ इस फ़ोन पर सहेजा। Sahara टीम को भेजने के लिए साइन इन करें।")
        state == FeedbackSendState.SENT -> careBilingual("Your earlier feedback was sent to the Sahara team.", "आपका पिछला फ़ीडबैक Sahara टीम को भेज दिया गया।")
        signedIn -> careBilingual("Send shares this with the Sahara team, with your account.", "भेजें दबाने पर यह आपके खाते के साथ Sahara टीम को जाएगा।")
        else -> careBilingual("Saved on this phone only. Sign in to send it to the Sahara team.", "सिर्फ़ इस फ़ोन पर सहेजा जाएगा। भेजने के लिए साइन इन करें।")
    }
}
