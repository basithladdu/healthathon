package `in`.wedevit.care

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Simple Mode (Dr Sharada, 23 Nov 2026): the patient's Today as four large spoken buttons and nothing else: how are you,
 * my medicines, call, read to me. One task per screen, a big Back and a replay button on every screen, no typing, the
 * same local records as the full app (so it works offline and the doctor sees the same entries). The full app is one
 * small link away at the bottom, behind a confirmation or a long press, so nobody leaves by accident.
 */
private enum class SimpleScreen { HOME, FEEL, WHERE, HOW_MUCH, THANKS, MEDICINES, CALL, READ, VOICE }

private val SimpleRadius = 24.dp
private val SimpleMin = 72.dp

@Composable fun CareSimpleMode(vm: CareModel) {
    val lang = careLanguage()
    val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle()
    val namespace = vm.storageNamespace
    val speaker = rememberSimpleSpeaker(lang)
    val muted = rememberSimpleMuted(namespace, person)
    var screen by rememberSaveable(person) { mutableStateOf(SimpleScreen.HOME) }
    // Pain parts tapped on "Where does it hurt?", kept while the person moves on to "How much?".
    var sites by rememberSaveable(person) { mutableStateOf("") }
    val tapped = sites.split('|').filter { it in PainBodyMapRules.sites }
    // True once a voice note was saved in this round, so "Saved" does not offer another one straight after.
    var voiceSaved by rememberSaveable(person) { mutableStateOf(false) }
    // The pain note's id is chosen before it is saved, so the patient's own voice recorded on "Where does it hurt?" is kept with it,
    // and the words heard are added to the note.
    var painId by rememberSaveable(person) { mutableStateOf(newId()) }
    var painWords by rememberSaveable(person) { mutableStateOf("") }
    val dropPainVoice: () -> Unit = { CareVoiceNotes(context, namespace).remove(person, painId); painWords = ""; painId = newId() }
    // Going home without saving the pain note leaves no stray recording behind (after a save the id is already a fresh one).
    val home: () -> Unit = { voiceSaved = false; dropPainVoice(); screen = SimpleScreen.HOME }
    Box(Modifier.fillMaxSize().background(SaharaColors.Ground).testTag("simple-mode")) {
      // One screen at a time, sliding forward into a task and back out of it (and following the system back swipe).
      SaharaScreenHost(screen, Modifier.fillMaxSize(), label = "Simple mode", direction = ::simpleDirection) { shown ->
        // Inside the host, so only the screen on show answers back and the back swipe previews this screen.
        SaharaBackHandler(shown != SimpleScreen.HOME) {
            when (shown) { SimpleScreen.HOW_MUCH -> screen = SimpleScreen.WHERE; SimpleScreen.WHERE -> screen = SimpleScreen.FEEL; else -> home() }
        }
        when (shown) {
            SimpleScreen.HOME -> SimpleHome(speaker, muted, onMute = { SimpleModeStore.setMuted(context, namespace, person, !muted) },
                onOpen = { screen = it }, onFullApp = { SimpleModeStore.setOn(context, namespace, person, false) })
            SimpleScreen.FEEL -> SimpleFeel(vm, speaker, muted, onBack = home) { hard -> sites = ""; screen = if (hard) SimpleScreen.WHERE else SimpleScreen.THANKS }
            SimpleScreen.WHERE -> SimpleWhere(vm, speaker, muted, tapped, painId, onWords = { painWords = CareVoiceRules.append(painWords, it) },
                onChange = { sites = it.joinToString("|") }, onBack = { screen = SimpleScreen.FEEL },
                onNoPain = { sites = ""; dropPainVoice(); screen = SimpleScreen.THANKS }, onDone = { screen = SimpleScreen.HOW_MUCH })
            SimpleScreen.HOW_MUCH -> SimpleHowMuch(vm, speaker, muted, tapped, painId, painWords, onBack = { screen = SimpleScreen.WHERE }) {
                sites = ""; painWords = ""; painId = newId(); screen = SimpleScreen.THANKS }
            SimpleScreen.THANKS -> SimpleThanks(speaker, muted, onHome = home, onVoice = if (voiceSaved) null else ({ screen = SimpleScreen.VOICE }))
            SimpleScreen.MEDICINES -> SimpleMedicines(vm, speaker, muted, onBack = home)
            SimpleScreen.CALL -> SimpleCall(vm, speaker, muted, onBack = home)
            SimpleScreen.READ -> SimpleRead(vm, speaker, muted, onBack = home)
            SimpleScreen.VOICE -> SimpleVoiceNote(vm, speaker, muted, onBack = home) { voiceSaved = true; screen = SimpleScreen.THANKS }
        }
      }
    }
    SimpleAgeNotice(vm, speaker, muted, show = screen == SimpleScreen.HOME)
}

/** Into a task slides forward; back to Home (or a step back in the pain questions) slides back. "Saved" always arrives forward. */
private fun simpleDirection(from: SimpleScreen, to: SimpleScreen): SaharaMotion.Direction = when {
    to == SimpleScreen.THANKS -> SaharaMotion.Direction.Forward
    to == SimpleScreen.HOME || to.ordinal < from.ordinal -> SaharaMotion.Direction.Back
    else -> SaharaMotion.Direction.Forward
}

/**
 * 24 Nov meeting: when Simple Mode came on by itself because the patient is 60 or older (no choice made yet), say so
 * once, gently, with a way back. Shown one time per person on this phone; either button counts as seen.
 */
@Composable private fun SimpleAgeNotice(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, show: Boolean) {
    val t = careText(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle()
    val namespace = vm.storageNamespace
    val revision by SimpleModeStore.revision.collectAsStateWithLifecycle()
    val due = remember(namespace, person, revision) { !SimpleModeStore.hasChoice(context, namespace, person) && !SimpleModeStore.ageNoticeShown(context, namespace, person) }
    if (!show || !due) return
    // Simple Mode has no Settings: the way out is the "Full app" link at the bottom of the home screen (or "Switch back" here).
    val words = { l: CareLang -> careTr(l, "We made Sahara simpler for you. To go back, tap Full app at the bottom of the screen.", "हमने Sahara को आपके लिए आसान बना दिया है। वापस जाने के लिए स्क्रीन के नीचे 'पूरा ऐप' छुएँ।") }
    LaunchedEffect(person) { if (!muted) { delay(900); speaker.say(words) } }
    val seen = { SimpleModeStore.setAgeNoticeShown(context, namespace, person) }
    AlertDialog(onDismissRequest = seen, containerColor = Paper, modifier = Modifier.saharaDialogEnter().testTag("simple-age-notice"),
        title = { Text(t("We made Sahara simpler for you. To go back, tap Full app at the bottom of the screen.", "हमने Sahara को आपके लिए आसान बना दिया है। वापस जाने के लिए स्क्रीन के नीचे 'पूरा ऐप' छुएँ।"), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold) },
        // The replay every Simple screen has, here too (it works with the sound off, like the top bar's).
        text = { SimpleBarButton(t("Say it again", "फिर से सुनाएँ"), Icons.Default.Replay, SaharaTone.Teal, Modifier.fillMaxWidth().testTag("simple-age-notice-replay")) { speaker.say(words) } },
        confirmButton = { TextButton(onClick = seen, modifier = Modifier.heightIn(min = SimpleMin).testTag("simple-age-notice-ok")) { Text(t("OK", "ठीक है"), fontSize = 20.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { seen(); SimpleModeStore.setOn(context, namespace, person, false) }, modifier = Modifier.heightIn(min = SimpleMin).testTag("simple-age-notice-switch-back")) {
            Text(t("Switch back", "वापस बदलें"), fontSize = 18.sp) } })
}

/** Speaks [prompt] once when the screen opens (unless the sound is off) and returns it for the replay button. */
@Composable private fun SpeakOnOpen(speaker: SimpleSpeaker, muted: Boolean, key: Any = Unit, prompt: (CareLang) -> String) {
    val latest by rememberUpdatedState(prompt)
    LaunchedEffect(key) { if (!muted) speaker.say(latest) }
}

/** The bar on every Simple screen: a big Back (or, on the home screen, the sound switch) and the big replay button. */
@Composable private fun SimpleTopBar(onBack: (() -> Unit)?, onReplay: () -> Unit, muted: Boolean = false, onMute: (() -> Unit)? = null) {
    val t = careText()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) SimpleBarButton(t("Back", "वापस"), Icons.AutoMirrored.Filled.ArrowBack, SaharaTone.Neutral, Modifier.weight(1f).testTag("simple-back"), onBack)
        else if (onMute != null) SimpleBarButton(if (muted) t("Sound off", "आवाज़ बंद") else t("Sound on", "आवाज़ चालू"), if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            SaharaTone.Neutral, Modifier.weight(1f).testTag("simple-mute").semantics { stateDescription = if (muted) t("Sound off", "आवाज़ बंद") else t("Sound on", "आवाज़ चालू") }, onMute)
        SimpleBarButton(t("Say it again", "फिर से सुनाएँ"), Icons.Default.Replay, SaharaTone.Teal, Modifier.weight(1f).testTag("simple-replay"), onReplay)
    }
}

@Composable private fun SimpleBarButton(label: String, icon: ImageVector, tone: SaharaTone, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(SimpleRadius), color = tone.wash, contentColor = tone.ink, border = BorderStroke(2.dp, tone.solid), modifier = modifier.heightIn(min = SimpleMin).saharaPressBounce()) {
        SimpleIconLabel(label, icon, 32.dp, 20.sp, tone.ink)
    }
}

/** The smallest size a Simple label shrinks to: 18sp, or what 18sp looks like at the normal font size when the phone's font is larger. */
@Composable private fun simpleLabelFloor(): Float = SimpleModeRules.labelFloor(LocalDensity.current.fontScale)

/**
 * A Simple button's picture and words. Side by side normally; with a large system font the picture goes above the
 * words, so the words get the whole width. The words wrap to two lines and shrink to fit whole, never cut off.
 */
@Composable private fun SimpleIconLabel(label: String, icon: ImageVector, iconSize: Dp, fontSize: TextUnit, color: Color) {
    val floor = simpleLabelFloor()
    if (SimpleModeRules.stackIcon(LocalDensity.current.fontScale)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(iconSize)); Spacer(Modifier.height(4.dp))
            SaharaFitText(label, style = LocalTextStyle.current.copy(fontSize = fontSize), color = color, maxLines = 2, min = floor, fontWeight = FontWeight.Bold)
        }
    } else Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(iconSize)); Spacer(Modifier.width(8.dp))
        SaharaFitText(label, Modifier.weight(1f, fill = false), style = LocalTextStyle.current.copy(fontSize = fontSize), color = color, maxLines = 2, min = floor, fontWeight = FontWeight.Bold)
    }
}

/** A large title under the bar: 26sp, high contrast, centred. */
@Composable private fun SimpleTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(horizontal = 16.dp).a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, color = SaharaColors.Ink, textAlign = TextAlign.Center)
}

/** One big tile: a picture over a short label, solid section colour with white text. */
@Composable private fun SimpleTile(label: String, tone: SaharaTone, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, picture: @Composable () -> Unit) {
    val tick = rememberSaharaTick()
    Surface(shape = RoundedCornerShape(SimpleRadius), color = tone.solid, contentColor = tone.onSolid,
        modifier = modifier.heightIn(min = SimpleMin).saharaPressBounce().clip(RoundedCornerShape(SimpleRadius))
            .combinedClickable(role = Role.Button, onLongClick = onLongClick) { tick(); onClick() }.semantics { contentDescription = label }) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.weight(1f, fill = false).heightIn(max = 96.dp), contentAlignment = Alignment.Center) { picture() }
            Spacer(Modifier.height(8.dp))
            SaharaFitText(label, style = LocalTextStyle.current.copy(fontSize = 24.sp, lineHeight = 28.sp), color = tone.onSolid, maxLines = 3, min = 18f, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The Call tile: the one emergency-looking button in Simple Mode. Solid red with a thick dark red edge, a red phone in a
 * white circle and large white words, so it never looks like the other three.
 */
@Composable private fun SimpleCallTile(speaker: SimpleSpeaker, modifier: Modifier, onClick: () -> Unit) {
    val t = careText(); val tone = SaharaTone.Error
    val haptic = rememberSaharaHaptic()
    val label = t("Call", "फ़ोन करें")
    val sub = t("Nurse, family or 108", "नर्स, परिवार या 108")
    Surface(shape = RoundedCornerShape(SimpleRadius), color = tone.solid, contentColor = tone.onSolid, border = BorderStroke(4.dp, tone.ink),
        modifier = modifier.heightIn(min = SimpleMin).saharaPressBounce().clip(RoundedCornerShape(SimpleRadius))
            .combinedClickable(role = Role.Button, onLongClick = { speaker.say { l -> careTr(l, "Call", "फ़ोन करें") } }) { haptic(); onClick() }
            .testTag("simple-home-call").semantics { contentDescription = "$label. $sub" }) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = Color.White, contentColor = tone.solid, modifier = Modifier.weight(1f, fill = false).sizeIn(maxWidth = 92.dp, maxHeight = 92.dp).aspectRatio(1f)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, Modifier.fillMaxSize(.6f)) }
            }
            Spacer(Modifier.height(8.dp))
            // Two lines allowed and a floor that follows the font scale, so "Call" in Tamil or Malayalam is never cut off.
            val floor = simpleLabelFloor()
            SaharaFitText(label, style = LocalTextStyle.current.copy(fontSize = 28.sp), color = tone.onSolid, maxLines = 2, min = floor, fontWeight = FontWeight.ExtraBold)
            SaharaFitText(sub, style = LocalTextStyle.current.copy(fontSize = 18.sp), color = tone.onSolid, maxLines = 2, min = floor, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable private fun SimpleHome(speaker: SimpleSpeaker, muted: Boolean, onMute: () -> Unit, onOpen: (SimpleScreen) -> Unit, onFullApp: () -> Unit) {
    val t = careText()
    val prompt = { l: CareLang -> careTr(l, "What would you like to do? Tap a big button.", "आप क्या करना चाहेंगे? किसी बड़े बटन को छुएँ।") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    var leaving by rememberSaveable { mutableStateOf(false) }
    // A long press on a tile says its name, so someone who cannot read can learn the buttons without opening them.
    val sayName: (String, String) -> Unit = { en, hi -> speaker.say { l -> careTr(l, en, hi) } }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(null, { speaker.say(prompt) }, muted, onMute)
        // Exactly four big buttons in two rows of two (SimpleModeRules.HomeButton): no scrolling, no menus, nothing else.
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleTile(t("How are you today?", "आज आप कैसे हैं?"), SaharaSection.CheckIns, Modifier.weight(1f).fillMaxHeight().testTag("simple-home-feel"), { onOpen(SimpleScreen.FEEL) },
                    onLongClick = { sayName("How are you today?", "आज आप कैसे हैं?") }) {
                    CareMoodFace(1f, Modifier.size(80.dp)) }
                SimpleTile(t("My medicines", "मेरी दवाइयाँ"), SaharaSection.Medicines, Modifier.weight(1f).fillMaxHeight().testTag("simple-home-medicines"), { onOpen(SimpleScreen.MEDICINES) },
                    onLongClick = { sayName("My medicines", "मेरी दवाइयाँ") }) {
                    // The sun and the moon either side of the pill: medicines by time of day.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, null, Modifier.size(32.dp)); Icon(Icons.Default.Medication, null, Modifier.size(60.dp)); Icon(Icons.Default.NightsStay, null, Modifier.size(32.dp))
                    }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleTile(t("Read to me", "मुझे पढ़कर सुनाएँ"), SaharaTone.Indigo, Modifier.weight(1f).fillMaxHeight().testTag("simple-home-read"), { onOpen(SimpleScreen.READ) },
                    onLongClick = { sayName("Read to me", "मुझे पढ़कर सुनाएँ") }) {
                    Icon(Icons.Default.VolumeUp, null, Modifier.size(72.dp)) }
                SimpleCallTile(speaker, Modifier.weight(1f).fillMaxHeight()) { onOpen(SimpleScreen.CALL) }
            }
        }
        // The way back to the full app: small, at the very bottom; a tap asks first, a long press leaves at once.
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(t("Full app", "पूरा ऐप"), Modifier.heightIn(min = 48.dp).combinedClickable(role = Role.Button, onClickLabel = t("Leave simple mode", "सरल मोड छोड़ें"), onLongClick = onFullApp) { leaving = true }
                .padding(horizontal = 16.dp, vertical = 12.dp).testTag("simple-full-app"), fontSize = 18.sp, color = SaharaColors.InkSub, fontWeight = FontWeight.SemiBold)
        }
    }
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, containerColor = Paper, modifier = Modifier.testTag("simple-full-app-dialog"),
        title = { Text(t("Leave simple mode?", "सरल मोड छोड़ें?"), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
        text = { Text(t("The full app has more buttons. Simple mode can be switched on again in More.", "पूरे ऐप में ज़्यादा बटन हैं। सरल मोड को 'और' में फिर से चालू कर सकते हैं।"), fontSize = 18.sp, lineHeight = 24.sp) },
        confirmButton = { TextButton(onClick = { leaving = false }, modifier = Modifier.heightIn(min = SimpleMin).testTag("simple-full-app-stay")) { Text(t("Stay in simple mode", "सरल मोड में रहें"), fontSize = 18.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { leaving = false; onFullApp() }, modifier = Modifier.heightIn(min = SimpleMin).testTag("simple-full-app-leave")) { Text(t("Open full app", "पूरा ऐप खोलें"), fontSize = 18.sp) } })
}

// ---------------------------------------------------------------- How are you today?

@Composable private fun SimpleFeel(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, onBack: () -> Unit, onSaved: (hard: Boolean) -> Unit) {
    val t = careText(); val hindi = careHindi()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = remember(items, person, dayStart) { CheckInMood.today(items, person, dayStart) }
    val account = remember(person) { vm.accountNamespace }
    val haptic = rememberSaharaHaptic()
    val prompt = { l: CareLang -> careTr(l, "How are you feeling today?", "आज आप कैसा महसूस कर रहे हैं?") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        SimpleTitle(t("How are you feeling today?", "आज आप कैसा महसूस कर रहे हैं?"))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Good first; the faces are the same drawing as the full check-in (sad 0, middle .5, smiling 1).
            listOf(CheckInMood.GOOD to 1f, CheckInMood.OKAY to .5f, CheckInMood.HARD to 0f).forEachIndexed { index, (mood, face) ->
                val chosen = today?.title == mood
                // The chosen face's colour and edge glide in, and the row swells once when picked.
                val fill by animateColorAsState(if (chosen) SaharaColors.PrimaryWash else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple mood fill")
                val edge by animateColorAsState(if (chosen) SaharaColors.Primary else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple mood edge")
                Surface(onClick = {
                    if (!canEditCareContext(vm, person, account)) { vm.message.value = t("View only", "सिर्फ़ देखने के लिए"); return@Surface }
                    // The same save as the Today check-in: a new check-in, or today's changed to this mood.
                    val change = CareDailyFlow.checkInChange(today, mood)
                    if (change != null) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    haptic()
                    onSaved(mood == CheckInMood.HARD)
                }, enabled = !busy, shape = RoundedCornerShape(SimpleRadius), color = fill,
                    border = BorderStroke(if (chosen) 4.dp else 2.dp, edge),
                    modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = SimpleMin).saharaSelectBounce(chosen, .04f).saharaPressBounce().testTag("simple-mood-$index").semantics { role = Role.RadioButton; if (chosen) stateDescription = t("Chosen", "चुना गया") }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CareMoodFace(face, Modifier.size(72.dp).clearAndSetSemantics { })
                        Spacer(Modifier.width(20.dp))
                        SaharaFitText(checkInMoodLabel(mood, hindi), Modifier.weight(1f), style = LocalTextStyle.current.copy(fontSize = 26.sp), color = SaharaColors.Ink, maxLines = 2, min = 18f, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                        if (chosen) Icon(Icons.Default.Check, null, Modifier.size(40.dp), tint = SaharaTone.Success.ink)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Where does it hurt?

@Composable private fun SimpleWhere(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, sites: List<String>, painId: String, onWords: (String) -> Unit,
    onChange: (List<String>) -> Unit, onBack: () -> Unit, onNoPain: () -> Unit, onDone: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val person by vm.selected.collectAsStateWithLifecycle(); val micBusy by vm.recording.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var back by rememberSaveable { mutableStateOf(false) }
    val canRecord = canEdit && person.isNotBlank()
    // True while the patient's voice is being recorded: Sahara then says nothing, so its own voice is not recorded
    // (and, on Android 13+, not written into the pain note as if the patient had said it).
    var recording by remember { mutableStateOf(false) }
    val prompt = { l: CareLang -> SimpleModeRules.wherePrompt(sites.isNotEmpty(), canRecord, l) }
    SpeakOnOpen(speaker, muted) { l -> SimpleModeRules.wherePrompt(false, canRecord, l) }
    val latestSites by rememberUpdatedState(sites)
    val latestCanRecord by rememberUpdatedState(canRecord)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { if (SimpleModeRules.speakNow(recording)) speaker.say(prompt) })
        // Everything between the bars: the body picture takes what the other blocks leave, but never less than
        // MIN_BODY_DP, and with a very large font (or the recorder open) the middle scrolls instead of squeezing the body away.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewport = maxHeight
            SimpleFillColumn(viewport, SimpleModeRules.MIN_BODY_DP.dp, fillIndex = 2, Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                SimpleTitle(t("Where does it hurt?", "दर्द कहाँ है?"))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SimpleChoice(t("Front", "सामने"), Icons.Default.AccessibilityNew, !back, Modifier.weight(1f).testTag("simple-pain-front")) { back = false }
                    // Its own English key: "Back" alone is the navigation word ("go back") in several languages.
                    SimpleChoice(t("Back of the body", "पीछे"), Icons.Default.RotateRight, back, Modifier.weight(1f).testTag("simple-pain-back")) { back = true }
                }
                // The flat body outline from the full pain map (same parts, same left and right), drawn as large as the screen allows.
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    val described = if (sites.isEmpty()) t("Body picture. Nothing marked yet.", "शरीर की तस्वीर। अभी कुछ नहीं चुना।") else sites.joinToString(", ") { PainBodyMapRules.label(it, hindi) }
                    Canvas(Modifier.fillMaxHeight().aspectRatio(0.5f).testTag("simple-pain-body").semantics { contentDescription = described }.pointerInput(back) {
                        detectTapGestures { p ->
                            SimpleModeRules.siteAt(back, p.x * 100f / size.width, p.y * 200f / size.height)?.let { site ->
                                val first = latestSites.isEmpty()
                                onChange(PainBodyMapRules.toggled(latestSites, site))
                                val part = PainBodyMapRules.sites.getValue(site)
                                // The first part marked also says how to add their own voice (the recorder opens under the body).
                                if (SimpleModeRules.speakNow(recording)) speaker.say { l -> careTr(l, part.first, part.second) +
                                    if (first && latestCanRecord) " " + careTr(l, "To say more in your own voice, tap Record your voice.", "अपनी आवाज़ में और बताने के लिए 'अपनी आवाज़ रिकॉर्ड करें' छुएँ।") else "" }
                            }
                        }
                    }) {
                        PainBodyMapRules.regions(back).forEach { region ->
                            val path = Path().apply { region.outline.forEachIndexed { i, pt -> val x = pt.x * size.width / 100f; val y = pt.y * size.height / 200f; if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }
                            val on = region.id in sites
                            drawPath(path, if (on) SaharaTone.Rose.solid else SaharaColors.Well)
                            drawPath(path, if (on) SaharaTone.Rose.ink else SaharaColors.Edge, style = Stroke(width = (if (on) 3.dp else 2.dp).toPx()))
                        }
                    }
                }
                // The chosen parts' names open in under the body (the last names stay while the line closes).
                val named = remember(sites, hindi) { sites.joinToString(", ") { PainBodyMapRules.label(it, hindi) } }
                val lastNamed = remember { arrayOf("") }
                if (named.isNotEmpty()) lastNamed[0] = named
                androidx.compose.animation.AnimatedVisibility(sites.isNotEmpty(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                    Text(lastNamed[0],Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("simple-pain-selected"),
                        fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = SaharaTone.Rose.ink, textAlign = TextAlign.Center, maxLines = 2)
                }
                // Once a part is marked, the patient can also say it in their own voice; the recording is kept with the pain note.
                androidx.compose.animation.AnimatedVisibility(sites.isNotEmpty() && canRecord, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
                    // Simple Mode's recorder: large words and buttons, no Remove; Sahara stops speaking the moment the microphone opens.
                    CareVoiceNoteRecorder(vm.storageNamespace, person, painId, onWords = onWords, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).testTag("simple-pain-voice"), micBusy = micBusy,
                        simple = true, onActive = { on -> recording = on; if (on) speaker.stop() })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SimpleBarButton(t("No pain", "दर्द नहीं"), Icons.Default.Close, SaharaTone.Neutral, Modifier.weight(1f).testTag("simple-pain-none"), onNoPain)
            // "Touch the body" turns into Done once a part is marked: a crossfade in place, not a pop.
            SaharaStateSwitch(sites.isNotEmpty(), Modifier.weight(1f), label = "simple pain done") { any ->
                if (any) SimpleBarButton(t("Done", "हो गया"), Icons.Default.Check, SaharaTone.Success, Modifier.fillMaxWidth().testTag("simple-pain-done"), onDone)
                else Box(Modifier.fillMaxWidth().heightIn(min = SimpleMin).background(SaharaColors.Well, RoundedCornerShape(SimpleRadius)), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TouchApp, null, Modifier.size(28.dp), tint = SaharaColors.InkSub); Spacer(Modifier.width(6.dp)); Text(t("Touch the body", "शरीर को छुएँ"), fontSize = 18.sp, color = SaharaColors.InkSub, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/**
 * Stacks its children top to bottom at full width. The child at [fillIndex] gets the height the others leave of
 * [viewport], but never less than [minFill]; when that makes the whole taller than [viewport], the caller's scroll takes
 * over. Children that are not shown (a closed AnimatedVisibility) take no room. Use it inside a vertical scroll.
 */
@Composable private fun SimpleFillColumn(viewport: Dp, minFill: Dp, fillIndex: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val loose = Constraints(minWidth = 0, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity)
        val others = measurables.mapIndexedNotNull { i, m -> if (i == fillIndex) null else i to m.measure(loose) }.toMap()
        val fillHeight = SimpleModeRules.fillHeight(viewport.roundToPx(), others.values.sumOf { it.height }, minFill.roundToPx())
        val fill = measurables.getOrNull(fillIndex)?.measure(Constraints(minWidth = 0, maxWidth = width, minHeight = fillHeight, maxHeight = fillHeight))
        val placed = measurables.indices.mapNotNull { if (it == fillIndex) fill else others[it] }
        layout(width, placed.sumOf { it.height }) { var y = 0; placed.forEach { it.placeRelative(0, y); y += it.height } }
    }
}

@Composable private fun SimpleChoice(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = careText(); val tone = SaharaTone.Rose
    val tick = rememberSaharaTick()
    val fill by animateColorAsState(if (selected) tone.solid else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple choice fill")
    val ink by animateColorAsState(if (selected) tone.onSolid else SaharaColors.Ink, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple choice ink")
    val edge by animateColorAsState(if (selected) tone.solid else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple choice edge")
    Surface(onClick = { if (!selected) tick(); onClick() }, shape = RoundedCornerShape(SimpleRadius), color = fill, contentColor = ink,
        border = BorderStroke(2.dp, edge), modifier = modifier.heightIn(min = SimpleMin).saharaSelectBounce(selected, .06f).semantics { role = Role.Tab; if (selected) stateDescription = t("Chosen", "चुना गया") }) {
        SimpleIconLabel(label, icon, 30.dp, 20.sp, ink)
    }
}

// ---------------------------------------------------------------- How much does it hurt?

@Composable private fun SimpleHowMuch(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, sites: List<String>, painId: String, painWords: String, onBack: () -> Unit, onSaved: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val people by vm.people.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val role by vm.viewRole.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val account = remember(person) { vm.accountNamespace }
    val haptic = rememberSaharaHaptic()
    val prompt = { l: CareLang -> careTr(l, "How much does it hurt? None, a little, a lot, or the worst?", "कितना दर्द है? बिलकुल नहीं, थोड़ा, बहुत, या सबसे ज़्यादा?") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        SimpleTitle(t("How much does it hurt?", "कितना दर्द है?"))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SimpleModeRules.PainWord.entries.forEach { word ->
                val fill = Color(PainBodyMapRules.heatColor(word.rating))
                Surface(onClick = {
                    if (!canEditCareContext(vm, person, account)) { vm.message.value = t("View only", "सिर्फ़ देखने के लिए"); return@Surface }
                    val name = people.firstOrNull { it.id == person }?.name.orEmpty()
                    val told = SimpleModeRules.painNote(sites, word, LocalDateTime.now().withSecond(0).withNano(0), name, familyHelped = role == "Family")
                    // The words heard while the patient spoke on "Where does it hurt?" join the note; the recording has the same id.
                    val note = if (painWords.isBlank()) told else told.copy(note = CareVoiceRules.append(told.note, painWords, max = 500))
                    vm.saveSymptomNote(person, painId, note, null) { haptic(); onSaved() }
                }, enabled = !busy && sites.isNotEmpty(), shape = RoundedCornerShape(SimpleRadius), color = Paper, border = BorderStroke(2.dp, SaharaColors.Edge),
                    modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = SimpleMin).saharaPressBounce().testTag("simple-pain-level-${word.key}")) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // The faces scale (smiling for none, sad for the worst) beside a bar that grows with the pain, in the pain map's colours.
                        CareMoodFace(SimpleModeRules.faceScore(word), Modifier.size(64.dp).clearAndSetSemantics { })
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.width(28.dp).fillMaxHeight(.7f), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(.25f + .75f * word.rating / 9f).background(fill, RoundedCornerShape(8.dp)))
                        }
                        Spacer(Modifier.width(16.dp))
                        SaharaFitText(careTr(hindi, word.english, word.hindi), Modifier.weight(1f), style = LocalTextStyle.current.copy(fontSize = 26.sp), color = SaharaColors.Ink, maxLines = 2, min = 18f, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Saved

/**
 * "Saved": a big green tick, then Home. [onVoice] (null after a voice note) offers one more thing: tell the nurse in
 * your own voice. With nothing tapped it goes home by itself after a while.
 */
@Composable private fun SimpleThanks(speaker: SimpleSpeaker, muted: Boolean, onHome: () -> Unit, onVoice: (() -> Unit)?) {
    val t = careText()
    val prompt = { l: CareLang -> careTr(l, "Thank you. It is saved, and your care team can see it.", "धन्यवाद। यह सहेज लिया गया है, और आपकी देखभाल टीम इसे देख सकती है।") +
        if (onVoice != null) " " + careTr(l, "To tell your nurse more, tap the microphone and speak.", "नर्स को और बताने के लिए माइक छुएँ और बोलें।") else "" }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    val latestHome by rememberUpdatedState(onHome)
    LaunchedEffect(Unit) { delay(if (onVoice != null) 12_000 else 6_000); latestHome() }
    Column(Modifier.fillMaxSize().testTag("simple-thanks")) {
        SimpleTopBar(null, { speaker.say(prompt) })
        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = SaharaTone.Success.solid, modifier = Modifier.size(140.dp).saharaDialogEnter()) { Box(contentAlignment = Alignment.Center) { SaharaDrawnCheck(Modifier.size(96.dp), Color.White, 10.dp) } }
            Spacer(Modifier.height(24.dp))
            SimpleTitle(t("Thank you. It is saved.", "धन्यवाद। सहेज लिया गया।"))
        }
        if (onVoice != null) SimpleBarButton(t("Tell your nurse more", "नर्स को और बताएँ"), Icons.Default.Mic, SaharaTone.Marigold, Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp).testTag("simple-thanks-voice"), onVoice)
        SimpleBarButton(t("Home", "मुख्य स्क्रीन"), Icons.Default.Home, SaharaTone.Rust, Modifier.fillMaxWidth().padding(16.dp).testTag("simple-home"), onHome)
    }
}

// ---------------------------------------------------------------- My medicines

/** Simple Mode's part of the day as the app's dose slot (null for "any time"), so the pictures and colours match MedicineDoseVisuals. */
private fun doseSlotOf(slot: SimpleModeRules.Slot): DoseSlot? = when (slot) {
    SimpleModeRules.Slot.MORNING -> DoseSlot.MORNING
    SimpleModeRules.Slot.AFTERNOON -> DoseSlot.AFTERNOON
    SimpleModeRules.Slot.EVENING -> DoseSlot.EVENING
    SimpleModeRules.Slot.NIGHT -> DoseSlot.NIGHT
    SimpleModeRules.Slot.ANY_TIME -> null
}

private fun slotIcon(slot: SimpleModeRules.Slot): ImageVector = doseSlotOf(slot)?.let(::doseSlotIcon) ?: Icons.Default.Schedule

private fun slotTone(slot: SimpleModeRules.Slot): SaharaTone = doseSlotOf(slot)?.let(::doseSlotTone) ?: SaharaTone.Plum

/** Today's doses for this person, the same way the Today card works them out (saved times, or this phone's reminder times). */
@Composable private fun rememberSimpleDoses(vm: CareModel): Pair<List<CareItem>, Map<String, List<MedicineDose>>> {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle()
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    val medicines = remember(items, person) { items.filter { it.kind == "medicine" && it.personId == person } }
    val doses = remember(medicines) { medicines.associate { it.id to MedicineDoses.forItem(it, scheduler.option(it.id)?.times) } }
    return medicines to doses
}

@Composable private fun SimpleMedicines(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, onBack: () -> Unit) {
    val t = careText(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val person by vm.selected.collectAsStateWithLifecycle()
    val saved by vm.medicineEvents.collectAsStateWithLifecycle()
    val held by DoseUndoWindow.app.pending.collectAsStateWithLifecycle()
    val events = remember(saved, held, person) { DoseUndoWindow.shown(saved, held, person) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val haptic = rememberSaharaHaptic(); val tick = rememberSaharaTick()
    val (medicines, doses) = rememberSimpleDoses(vm)
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val photoRevision by MedicineStripPhotos.revision.collectAsStateWithLifecycle()
    val photos = remember(medicines, photoRevision) { medicines.associate { it.id to runCatching { store.first(it.personId, it.id) }.getOrNull() } }
    val groups = remember(medicines, doses) { SimpleModeRules.group(medicines.flatMap { m -> doses[m.id].orEmpty().map { m to it } }) }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    var slot by rememberSaveable(person) { mutableStateOf(SimpleModeRules.startSlot(groups.keys, SimpleModeRules.slotAt(LocalTime.now()))) }
    LaunchedEffect(groups.keys) { if (slot !in groups.keys) slot = SimpleModeRules.startSlot(groups.keys, SimpleModeRules.slotAt(LocalTime.now())) }
    val shown = slot?.let { groups[it] }.orEmpty()
    val prompt = { l: CareLang -> SimpleModeRules.medicinesPrompt(slot, shown.map { it.first.title }, l) }
    SpeakOnOpen(speaker, muted, key = slot ?: "none", prompt = prompt)
    var working by remember { mutableStateOf(false) }
    var whichNight by remember { mutableStateOf<Pair<String, (LocalDate) -> Unit>?>(null) }
    val account = remember(person) { vm.accountNamespace }
    fun take(item: CareItem, dose: MedicineDose) {
        if (!canEditCareContext(vm, item.personId, account)) { vm.message.value = t("View only", "सिर्फ़ देखने के लिए"); return }
        val save: (LocalDate) -> Unit = { day -> working = true; scope.launch {
            // The same save as the Today card's ticks, written at once (no undo wait), so the doctor sees the dose.
            try { markMedicineDose(context, vm, item, doses[item.id].orEmpty(), dose, true, false, if (dose.timed) dose.time else "", day); haptic(); speaker.say { l -> careTr(l, "Marked as taken.", "ली हुई दर्ज की गई।") } }
            catch (e: Exception) { if (e is CancellationException) throw e; vm.message.value = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।") }
            finally { working = false }
        } }
        if (MedicineDoses.askWhichNight(item, dose, events, ZonedDateTime.now(zone))) whichNight = dose.time to save else save(today)
    }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        // The time-of-day pictures: sun, afternoon sun, sunset, moon (and a clock for any time). Only the ones with doses today.
        if (groups.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.keys.forEach { s -> key(s) {
                val on = s == slot; val tone = slotTone(s); val name = SimpleModeRules.slotName(s, careLanguage())
                // The chosen time of day fills with its colour smoothly and gives one small bounce and a key tap.
                val fill by animateColorAsState(if (on) tone.solid else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple slot fill")
                val ink by animateColorAsState(if (on) tone.onSolid else tone.ink, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple slot ink")
                val edge by animateColorAsState(if (on) tone.solid else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple slot edge")
                Surface(onClick = { if (!on) tick(); slot = s }, shape = RoundedCornerShape(SimpleRadius), color = fill, contentColor = ink,
                    border = BorderStroke(2.dp, edge), modifier = Modifier.weight(1f).heightIn(min = SimpleMin).saharaSelectBounce(on, .08f).testTag("simple-slot-${s.name.lowercase()}")
                        .semantics { contentDescription = name; role = Role.Tab; if (on) stateDescription = t("Chosen", "चुना गया") }) {
                    Box(contentAlignment = Alignment.Center) { Icon(slotIcon(s), null, Modifier.size(40.dp)) }
                }
            } }
        }
        // Changing the time of day crossfades the heading and its doses instead of swapping them at once.
        SaharaStateSwitch(slot, Modifier.weight(1f).fillMaxWidth(), label = "simple medicine slot") { current ->
          val list = current?.let { groups[it] }.orEmpty()
          Column(Modifier.fillMaxSize()) {
            current?.let { s -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val dose = doseSlotOf(s)
                if (dose != null) DoseSlotIcon(dose, 44, describe = false) else Icon(slotIcon(s), null, Modifier.size(44.dp), tint = slotTone(s).ink)
                Spacer(Modifier.width(12.dp))
                Text(SimpleModeRules.slotName(s, careLanguage()), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 28.sp, lineHeight = 32.sp, color = SaharaColors.Ink)
            } }
            if (current == null) Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                SimpleTitle(t("There are no medicines for today.", "आज के लिए कोई दवा नहीं है।"))
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { (item, dose) -> "${item.id}-${dose.key}" }, contentType = { "simple-dose" }) { (item, dose) ->
              Box(if (SaharaMotion.enabled) Modifier.animateItem() else Modifier) {
                if (MedicineDoses.isAsNeededMedicine(item)) {
                    val times = MedicineDoses.asNeededTimes(item.id, events, today, zone)
                    SimpleDoseCard(item, photos[item.id], if (times.isEmpty()) "" else t("Taken ${times.size} times today", "आज ${times.size} बार ली"), if (times.isEmpty()) MedicineDoseState.PENDING else MedicineDoseState.TAKEN,
                        enabled = !busy && !working, again = true, tag = "simple-dose-${item.id}-as-needed") {
                        if (!canEditCareContext(vm, item.personId, account)) { vm.message.value = t("View only", "सिर्फ़ देखने के लिए"); return@SimpleDoseCard }
                        scope.launch { working = true; try { recordAsNeededDose(context, vm, item); haptic(); speaker.say { l -> careTr(l, "Marked as taken.", "ली हुई दर्ज की गई।") } } catch (e: Exception) { if (e is CancellationException) throw e; vm.message.value = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।") } finally { working = false } }
                    }
                } else {
                    val state = MedicineDoses.state(item, dose, events, today, zone)
                    SimpleDoseCard(item, photos[item.id], if (dose.timed) dose.time else if (dose.count > 1) t("Dose ${dose.index}", "खुराक ${dose.index}") else "", state,
                        enabled = !busy && !working && state != MedicineDoseState.TAKEN, again = false, tag = "simple-dose-${item.id}-${dose.key}") { take(item, dose) }
                }
              }
            }
            }
          }
        }
    }
    WhichNightDialog(whichNight) { whichNight = null }
}

/** One dose as a big card: the strip photo (or a pill), the name, and a large green tick to tap when taken; a red cross shows a missed dose. */
@Composable private fun SimpleDoseCard(item: CareItem, photo: File?, caption: String, state: MedicineDoseState, enabled: Boolean, again: Boolean, tag: String, onTaken: () -> Unit) {
    val t = careText()
    val taken = state == MedicineDoseState.TAKEN; val missed = state == MedicineDoseState.MISSED
    val word = doseStateWord(state)
    // Once taken, the card turns green gently and the tick pops; nothing changes size.
    val card by animateColorAsState(if (taken) SaharaColors.SuccessWash else Paper, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple dose card")
    val cardEdge by animateColorAsState(if (taken) SaharaColors.Success else SaharaColors.Edge, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple dose edge")
    val tickFill by animateColorAsState(if (taken) SaharaColors.Success else Color.White, SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "simple dose tick")
    Surface(shape = RoundedCornerShape(SimpleRadius), color = card, border = BorderStroke(2.dp, cardEdge),
        modifier = Modifier.fillMaxWidth().testTag(tag)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            StripThumb(photo, 96)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = SaharaColors.Ink)
                if (caption.isNotBlank()) Text(caption, fontSize = 18.sp, lineHeight = 22.sp, color = SaharaColors.InkSub)
                Text(word, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = when { taken -> SaharaTone.Success.ink; missed -> SaharaTone.Error.ink; else -> SaharaColors.InkSub })
            }
            Spacer(Modifier.width(8.dp))
            // The tick: outlined while due, solid green once taken; an as-needed medicine can be ticked again for each dose.
            val ring = when { missed -> SaharaColors.Error; state == MedicineDoseState.SKIPPED -> SaharaTone.Neutral.solid; else -> SaharaColors.Success }
            Surface(onClick = onTaken, enabled = enabled, shape = CircleShape, color = tickFill, border = BorderStroke(4.dp, ring),
                modifier = Modifier.size(84.dp).saharaSelectBounce(taken).semantics { contentDescription = "${item.title}: " + if (taken && !again) word else t("Mark taken", "ली हुई दर्ज करें"); stateDescription = word }) {
                // Taken, Not taken and Skipped are the same tick, cross and dash as the rest of the app (MedicineDoseVisuals).
                Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                    if (state != MedicineDoseState.PENDING) DoseStateMark(state, 76)
                    else Icon(Icons.Default.Check, null, Modifier.size(56.dp), tint = SaharaColors.Success)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Call

/**
 * Call: the faces of the people who look after the patient (the family sets a photo for each, from the gallery or the
 * camera; a big initial until then), and the ambulance 108 in red at the end. One tap calls when the phone allows Sahara
 * to place calls; otherwise one tap opens the dialler with the number filled in.
 */
@Composable private fun SimpleCall(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, onBack: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle()
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    LaunchedEffect(person) { vm.refreshCareContacts() }
    val callable = remember(contacts, person) { SimpleModeRules.callable(contacts, person) }
    var editing by remember { mutableStateOf<CareContact?>(null) }
    val prompt = { l: CareLang -> careTr(l, "Who do you want to call?", "आप किसे फ़ोन करना चाहते हैं?") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    fun dial(number: String) {
        speaker.stop()
        val uri = Uri.parse(NearbyHelp.dialUri(number))
        val oneTap = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        // A refused or blocked direct call falls back to the dialler, so a tap never does nothing.
        if (oneTap) try { context.startActivity(Intent(Intent.ACTION_CALL, uri)); return } catch (_: Exception) { }
        open(context, Intent(Intent.ACTION_DIAL, uri))
    }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        SimpleTitle(t("Who do you want to call?", "आप किसे फ़ोन करना चाहते हैं?"))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(callable, key = { it.id }, contentType = { "simple-call" }) { contact ->
                Box(if (SaharaMotion.enabled) Modifier.animateItem() else Modifier) {
                    val photo = rememberSimpleContactPhoto(vm.storageNamespace, person, contact.id)
                    SimpleCallButton(photo, SimpleModeRules.initial(contact.name), contact.name, contact.relationship, SaharaTone.Leaf, "simple-call-${contact.id}",
                        onLongClick = { editing = contact }) { dial(contact.phone) }
                }
            }
            item(key = "ambulance", contentType = "simple-call-emergency") {
                SimpleCallButton(null, null, t("Ambulance 108", "एम्बुलेंस 108"), t("Emergency", "आपातकाल"), SaharaTone.Error, "simple-call-108", emergency = true) { dial(SimpleModeRules.AMBULANCE) }
            }
            item(key = "hint", contentType = "simple-call-hint") {
                Text(if (callable.isEmpty()) t("Family can add phone numbers for the care team in the full app.", "परिवार पूरे ऐप में देखभाल टीम के फ़ोन नंबर जोड़ सकता है।")
                    else t("Family: press and hold a person to add their photo.", "परिवार: फ़ोटो जोड़ने के लिए किसी व्यक्ति को दबाकर रखें।"),
                    Modifier.fillMaxWidth().animateItem().padding(top = 8.dp), fontSize = 18.sp, lineHeight = 24.sp, color = SaharaColors.InkSub, textAlign = TextAlign.Center)
            }
        }
    }
    editing?.let { contact -> SimpleContactPhotoDialog(vm.storageNamespace, person, contact) { editing = null } }
}

/**
 * A call button: the person's photo (or a big round initial), the name, and who they are. [initial] null is the
 * ambulance, drawn like nothing else here: solid red, a red phone in a white circle, extra-bold white words.
 */
@Composable private fun SimpleCallButton(photo: File?, initial: String?, name: String, who: String, tone: SaharaTone, tag: String, emergency: Boolean = false,
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val t = careText(); val haptic = rememberSaharaHaptic()
    val fill = if (emergency) tone.solid else Paper
    val words = if (emergency) tone.onSolid else SaharaColors.Ink
    val shape = RoundedCornerShape(SimpleRadius)
    Surface(shape = shape, color = fill, border = BorderStroke(if (emergency) 4.dp else 2.dp, if (emergency) tone.ink else tone.solid),
        modifier = Modifier.fillMaxWidth().heightIn(min = if (emergency) 112.dp else 104.dp).saharaPressBounce().clip(shape)
            .combinedClickable(role = Role.Button, onLongClickLabel = onLongClick?.let { t("Add a photo", "फ़ोटो जोड़ें") }, onLongClick = onLongClick) { haptic(); onClick() }
            .testTag(tag).semantics { contentDescription = t("Call $name", "$name को फ़ोन करें") }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (initial == null) Surface(shape = CircleShape, color = Color.White, contentColor = tone.solid, modifier = Modifier.size(84.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, Modifier.size(52.dp)) }
            } else SimpleContactAvatar(photo, initial, tone, 84.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = if (emergency) 26.sp else 22.sp, lineHeight = if (emergency) 30.sp else 26.sp, fontWeight = if (emergency) FontWeight.ExtraBold else FontWeight.Bold, color = words)
                if (who.isNotBlank()) Text(who, fontSize = 18.sp, lineHeight = 22.sp, color = if (emergency) tone.onSolid else SaharaColors.InkSub, fontWeight = if (emergency) FontWeight.SemiBold else null)
            }
            Spacer(Modifier.width(8.dp))
            if (emergency) Icon(Icons.Default.LocalHospital, null, Modifier.size(40.dp), tint = tone.onSolid)
            else Surface(shape = CircleShape, color = SaharaTone.Success.solid, contentColor = Color.White, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Call, null, Modifier.size(32.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------- Read to me

/**
 * Read to me: reads aloud the newest message from the doctor or nurse ([SimpleModeRules.latestMessage], written in the
 * full app as "Message to read aloud"), in the app's language voice. With no message yet it says so, then reads today's
 * medicines by time of day and the next visit, so the button is never silent.
 */
@Composable private fun SimpleRead(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, onBack: () -> Unit) {
    val t = careText(); val lang = careLanguage()
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle()
    val message = remember(items, person) { SimpleModeRules.latestMessage(items, person) }
    val (medicines, doses) = rememberSimpleDoses(vm)
    val groups = remember(medicines, doses) { SimpleModeRules.group(medicines.flatMap { m -> doses[m.id].orEmpty().map { m to it } }).mapValues { (_, list) -> list.map { it.first.title } } }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val visit = remember(items, dayStart) { CareTodayRules.upcomingVisits(items, dayStart).firstOrNull() }
    val visitText = { l: CareLang -> visit?.let { v ->
        CareLanguageState.inLanguage(l) {
            // 12-hour, in the voice's language ("2:30 PM"), like every other time the patient sees; never "14:30".
            val clock = Instant.ofEpochMilli(v.due).atZone(zone).toLocalTime()
            val time = if (v.due == 0L) "" else ", " + careClockLabel(careClockValue(clock.hour, clock.minute), l == CareLang.HI)
            val whenText = CareTodayRules.whenLabel(v.due, today, l == CareLang.HI, zone)
            "${v.title.trim()}, $whenText$time"
        }
    } }
    val day = { l: CareLang -> SimpleModeRules.dayText(groups, visitText(l), l) }
    val prompt = { l: CareLang -> if (message != null) SimpleModeRules.readPrompt(message, l)
        else SimpleModeRules.readPrompt(null, l) + "\n" + careTr(l, "Here are today's medicines and your next visit.", "ये हैं आज की दवाइयाँ और आपकी अगली मुलाक़ात।") + "\n" + day(l) }
    SpeakOnOpen(speaker, muted, key = message?.itemId ?: "none", prompt = prompt)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        // The speaker picture with who the message is from, then the words large.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = SaharaTone.Indigo.solid, contentColor = Color.White, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (message != null) Icons.Default.RecordVoiceOver else Icons.Default.VolumeUp, null, Modifier.size(44.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Text(if (message != null) SimpleModeRules.messageLead(message.from, lang) else t("There is no message from your doctor or nurse yet.", "अभी आपके डॉक्टर या नर्स का कोई संदेश नहीं है।"),
                Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp, color = SaharaColors.Ink)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Surface(shape = RoundedCornerShape(SimpleRadius), color = Paper, border = BorderStroke(2.dp, if (message != null) SaharaTone.Indigo.solid else SaharaColors.Edge), modifier = Modifier.fillMaxWidth().saharaAppear()) {
                Column(Modifier.padding(20.dp)) {
                    if (message != null) {
                        Text(message.text, Modifier.testTag("simple-read-message"), fontSize = 24.sp, lineHeight = 34.sp, color = SaharaColors.Ink)
                        Spacer(Modifier.height(12.dp))
                        // The month in the app's language and a 12-hour time, like the rest of the patient's screens.
                        val sent = Instant.ofEpochMilli(message.created).atZone(zone).format(careDateFormat("d MMM, h:mm a", careHindi()))
                        Text(listOf(message.name.ifBlank { t(message.from.english, message.from.hindi) }, sent).joinToString(" · "), fontSize = 18.sp, color = SaharaColors.InkSub, fontWeight = FontWeight.SemiBold)
                    } else Text(day(lang), Modifier.testTag("simple-read-text"), fontSize = 22.sp, lineHeight = 32.sp, color = SaharaColors.Ink)
                }
            }
        }
        SimpleBarButton(t("Read it again", "फिर से पढ़ें"), Icons.Default.VolumeUp, SaharaTone.Indigo, Modifier.fillMaxWidth().padding(16.dp).testTag("simple-read-again")) { speaker.say(prompt) }
    }
}

// ---------------------------------------------------------------- Tell your nurse (voice note)

private enum class VoiceStage { READY, RECORDING, RECORDED }

/**
 * Tell your nurse: the patient's own voice, kept as audio (no typing, no transcription, nothing to download). One big
 * microphone to start, one big red stop, then Listen / Speak again / Send. Saved as a journal entry on the shared record
 * ([SimpleModeRules.voiceNoteTitle]) with the audio on this phone, where the family and the nurse can play it.
 */
@Composable private fun SimpleVoiceNote(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, onBack: () -> Unit, onSaved: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val account = remember(person) { vm.accountNamespace }
    val notes = remember(vm.storageNamespace) { SimpleVoiceNotes(context, vm.storageNamespace) }
    var noteId by rememberSaveable(person) { mutableStateOf(newId()) }
    var stage by rememberSaveable(person) { mutableStateOf(VoiceStage.READY) }
    var length by rememberSaveable(person) { mutableIntStateOf(0) }
    var problem by remember { mutableStateOf("") }
    val recorder = remember { SimpleVoiceRecorder(context.applicationContext) }
    val player = rememberSimpleVoicePlayer()
    val mic = rememberSaharaPermission(SaharaPermission.Microphone)
    val haptic = rememberSaharaHaptic()
    val owner = LocalLifecycleOwner.current
    val prompt = { l: CareLang -> when (stage) {
        VoiceStage.RECORDED -> careTr(l, "Tap Listen to hear it, or Send to give it to your nurse.", "सुनने के लिए 'सुनें' छुएँ, या नर्स को देने के लिए 'भेजें' छुएँ।")
        else -> careTr(l, "Tap the big microphone and speak. Your nurse will hear your own voice.", "बड़े माइक को छुएँ और बोलें। आपकी नर्स आपकी अपनी आवाज़ सुनेगी।")
    } }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    // A screen rebuilt mid-recording (the phone turned) has lost its recorder; a kept recording without its file starts over.
    LaunchedEffect(Unit) {
        if (stage == VoiceStage.RECORDING) stage = VoiceStage.READY
        if (stage == VoiceStage.RECORDED && runCatching { !notes.draft(noteId).isFile }.getOrDefault(true)) stage = VoiceStage.READY
    }
    fun finish() {
        val seconds = recorder.stop()
        if (seconds >= 1) { length = seconds; stage = VoiceStage.RECORDED; haptic() }
        else { notes.discardDraft(noteId); stage = VoiceStage.READY; problem = t("That was too short. Tap and speak again.", "यह बहुत छोटा था। फिर से छुएँ और बोलें।") }
    }
    fun start() {
        speaker.stop(); player.stop(); problem = ""
        mic.request {
            // The microphone must not hear Sahara's own voice.
            speaker.stop()
            if (recorder.start(notes.draft(noteId)) { finish() }) stage = VoiceStage.RECORDING
            else problem = t("Couldn't start the microphone. Please try again.", "माइक शुरू नहीं हो सका। कृपया फिर कोशिश करें।")
        }
    }
    fun leave() { if (recorder.recording) recorder.stop(); player.stop(); notes.discardDraft(noteId); stage = VoiceStage.READY; onBack() }
    // The system back swipe leaves the same way as the Back button: the microphone stops at once and the draft is cleared.
    SaharaBackHandler { leave() }
    fun send() {
        if (busy) return
        if (!canEditCareContext(vm, person, account)) { vm.message.value = t("View only", "सिर्फ़ देखने के लिए"); return }
        player.stop()
        val id = noteId
        try { notes.keep(person, id) } catch (_: Exception) { problem = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।"); return }
        vm.saveItem("journal", SimpleModeRules.voiceNoteTitle(length), SimpleModeRules.VOICE_NOTE_DETAIL, id = id, done = { haptic(); noteId = newId(); stage = VoiceStage.READY; length = 0; onSaved() })
    }
    // Going to the background while recording keeps what was said so far.
    DisposableEffect(recorder, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && recorder.recording) finish() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); recorder.release() }
    }
    var seconds by remember { mutableIntStateOf(0) }
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(recorder.recording) { seconds = 0; while (recorder.recording) { seconds = recorder.seconds(); level = recorder.level(); delay(150) }; level = 0f }
    val ring by animateFloatAsState(1f + level * .25f, SaharaMotion.tween(150), label = "simple voice level")
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar({ leave() }, { if (!recorder.recording) speaker.say(prompt) })
        SimpleTitle(t("Tell your nurse", "नर्स को बताएँ"))
        Spacer(Modifier.height(12.dp))
        SaharaStateSwitch(stage, Modifier.weight(1f).fillMaxWidth(), label = "simple voice note") { current ->
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (current) {
                    VoiceStage.READY -> SimpleTile(t("Tap and speak", "छुएँ और बोलें"), SaharaTone.Marigold, Modifier.weight(1f).fillMaxWidth().testTag("simple-voice-start"), { start() }) {
                        Icon(Icons.Default.Mic, null, Modifier.size(96.dp)) }
                    VoiceStage.RECORDING -> {
                        val tone = SaharaTone.Error
                        Surface(onClick = { finish() }, shape = RoundedCornerShape(SimpleRadius), color = tone.solid, contentColor = tone.onSolid, border = BorderStroke(4.dp, tone.ink),
                            modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = SimpleMin).saharaPressBounce().testTag("simple-voice-stop")
                                .semantics { contentDescription = t("Stop recording", "रिकॉर्डिंग रोकें") }) {
                            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                // The white circle swells softly with the voice, so the patient sees they are heard.
                                Surface(shape = CircleShape, color = Color.White, contentColor = tone.solid, modifier = Modifier.size(104.dp).graphicsLayer { scaleX = ring; scaleY = ring }) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Stop, null, Modifier.size(64.dp)) }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(SimpleModeRules.clock(seconds), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = tone.onSolid)
                                SaharaFitText(t("Listening. Tap to stop.", "सुन रहे हैं। रोकने के लिए छुएँ।"), style = LocalTextStyle.current.copy(fontSize = 22.sp), color = tone.onSolid, maxLines = 2, min = 18f, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    VoiceStage.RECORDED -> {
                        Surface(shape = RoundedCornerShape(SimpleRadius), color = Paper, border = BorderStroke(2.dp, SaharaTone.Marigold.solid), modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Mic, null, Modifier.size(72.dp), tint = SaharaTone.Marigold.ink)
                                Text(SimpleModeRules.clock(length), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = SaharaColors.Ink)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            SimpleBarButton(if (player.playing) t("Stop", "रोकें") else t("Listen", "सुनें"), if (player.playing) Icons.Default.Stop else Icons.Default.PlayArrow, SaharaTone.Indigo,
                                Modifier.weight(1f).testTag("simple-voice-listen")) {
                                if (player.playing) player.stop() else { speaker.stop(); if (!player.play(notes.draft(noteId))) problem = t("Couldn't play it.", "चलाया नहीं जा सका।") }
                            }
                            SimpleBarButton(t("Speak again", "फिर से बोलें"), Icons.Default.Mic, SaharaTone.Marigold, Modifier.weight(1f).testTag("simple-voice-again")) {
                                player.stop(); notes.discardDraft(noteId); stage = VoiceStage.READY; start()
                            }
                        }
                        SimpleBarButton(t("Send to nurse", "नर्स को भेजें"), Icons.Default.Check, SaharaTone.Success, Modifier.fillMaxWidth().testTag("simple-voice-send")) { send() }
                    }
                }
            }
        }
        // The problem line opens under the buttons and keeps its words while it closes.
        val lastProblem = remember { arrayOf("") }
        if (problem.isNotBlank()) lastProblem[0] = problem
        AnimatedVisibility(problem.isNotBlank(), enter = SaharaMotion.expand, exit = SaharaMotion.collapse) {
            Text(lastProblem[0], Modifier.fillMaxWidth().padding(horizontal = 16.dp).announce(), fontSize = 18.sp, lineHeight = 24.sp, color = SaharaTone.Error.ink, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- The switch in More

/**
 * The "Simple mode" row in More's settings: patient and family switch it for the selected patient, on this phone only.
 * Under it, for the family: the photos on the Call buttons, the message "Read to me" reads, and one-tap calling.
 */
@Composable fun SimpleModeSwitchRow(vm: CareModel, person: Person) {
    val t = careText(); val context = LocalContext.current
    val role by vm.viewRole.collectAsStateWithLifecycle(); val items by vm.items.collectAsStateWithLifecycle()
    val age = remember(items, person.id) { PatientProfileCodec.latest(items, person.id)?.let { PatientProfileCodec.decode(it.detail)?.age } }
    val on = rememberSimpleModeOn(vm.storageNamespace, person.id, SimpleModeRules.autoOn(age, patientOwnView = !CareFamilyRules.isFamily(role, false)))
    val haptic = rememberSaharaHaptic()
    val change: (Boolean) -> Unit = { value ->
        SimpleModeStore.setOn(context, vm.storageNamespace, person.id, value); haptic()
        if (value) vm.message.value = t("Simple mode is on. Open Today to see it.", "सरल मोड चालू है। देखने के लिए आज खोलें।")
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp).testTag("more-simple-mode"), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = SaharaTone.Teal.solid, contentColor = Color.White, modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.TouchApp, null, Modifier.size(20.dp)) } }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Simple mode", "सरल मोड"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                Text(t("Four big buttons that speak, for ${person.name}", "${person.name} के लिए बोलने वाले चार बड़े बटन"), fontSize = 14.sp, color = DoctorCardSub)
            }
            Switch(checked = on, onCheckedChange = change, modifier = Modifier.testTag("more-simple-mode-switch").semantics { contentDescription = t("Simple mode", "सरल मोड") },
                colors = SwitchDefaults.colors(checkedTrackColor = SaharaColors.Primary))
        }
        // The family's set-up for Simple Mode opens under the switch while it is on.
        AnimatedVisibility(on, enter = SaharaMotion.expand, exit = SaharaMotion.collapse, label = "more-simple-mode-setup") {
            Column(Modifier.fillMaxWidth()) {
                SimpleReadAloudMessageRow(vm, person)
                SimpleCallPhotosPanel(vm, person)
                Text(t("Photos stay on this phone. Set them on the phone the patient uses.", "फ़ोटो इसी फ़ोन पर रहती हैं। इन्हें मरीज़ वाले फ़ोन पर लगाएँ।"),
                    Modifier.padding(start = 68.dp, end = 12.dp, bottom = 8.dp), fontSize = 14.sp, color = DoctorCardSub)
                SimpleOneTapCallRow()
            }
        }
    }
}

/**
 * "Allow one-tap calling": with the phone's call permission, a tap on a face in Simple Mode calls straight away instead of
 * opening the dialler. Shown only when the app asks for that permission and it is not given yet.
 */
@Composable private fun SimpleOneTapCallRow() {
    val t = careText(); val context = LocalContext.current
    val declared = remember { runCatching {
        @Suppress("DEPRECATION") val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.contains(Manifest.permission.CALL_PHONE) == true
    }.getOrDefault(false) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
    if (!declared || granted) return
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 68.dp, end = 4.dp, bottom = 8.dp).testTag("more-simple-one-tap-call"), verticalAlignment = Alignment.CenterVertically) {
        Text(t("One-tap calling: a tap on a face calls at once", "एक बार छूकर फ़ोन: चेहरा छूते ही फ़ोन लगे"), Modifier.weight(1f), fontSize = 14.sp, color = DoctorCardSub)
        TextButton(onClick = { ask.launch(Manifest.permission.CALL_PHONE) }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(t("Allow", "अनुमति दें"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = SaharaTone.Teal.ink)
        }
    }
}
