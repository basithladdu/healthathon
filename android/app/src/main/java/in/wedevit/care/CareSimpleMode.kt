package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
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
import androidx.compose.material.icons.filled.WbTwilight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.time.format.DateTimeFormatter

/**
 * Simple Mode (Dr Sharada, 23 Nov 2026): the patient's Today as four large spoken buttons and nothing else: how are you,
 * my medicines, call, read to me. One task per screen, a big Back and a replay button on every screen, no typing, the
 * same local records as the full app (so it works offline and the doctor sees the same entries). The full app is one
 * small link away at the bottom, behind a confirmation or a long press, so nobody leaves by accident.
 */
private enum class SimpleScreen { HOME, FEEL, WHERE, HOW_MUCH, THANKS, MEDICINES, CALL, READ }

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
    BackHandler(screen != SimpleScreen.HOME) {
        screen = when (screen) { SimpleScreen.HOW_MUCH -> SimpleScreen.WHERE; SimpleScreen.WHERE -> SimpleScreen.FEEL; else -> SimpleScreen.HOME }
    }
    val home: () -> Unit = { screen = SimpleScreen.HOME }
    Box(Modifier.fillMaxSize().background(SaharaColors.Ground).testTag("simple-mode")) {
        when (screen) {
            SimpleScreen.HOME -> SimpleHome(speaker, muted, onMute = { SimpleModeStore.setMuted(context, namespace, person, !muted) },
                onOpen = { screen = it }, onFullApp = { SimpleModeStore.setOn(context, namespace, person, false) })
            SimpleScreen.FEEL -> SimpleFeel(vm, speaker, muted, onBack = home) { hard -> sites = ""; screen = if (hard) SimpleScreen.WHERE else SimpleScreen.THANKS }
            SimpleScreen.WHERE -> SimpleWhere(speaker, muted, tapped, onChange = { sites = it.joinToString("|") }, onBack = { screen = SimpleScreen.FEEL },
                onNoPain = { sites = ""; screen = SimpleScreen.THANKS }, onDone = { screen = SimpleScreen.HOW_MUCH })
            SimpleScreen.HOW_MUCH -> SimpleHowMuch(vm, speaker, muted, tapped, onBack = { screen = SimpleScreen.WHERE }) { sites = ""; screen = SimpleScreen.THANKS }
            SimpleScreen.THANKS -> SimpleThanks(speaker, muted, onHome = home)
            SimpleScreen.MEDICINES -> SimpleMedicines(vm, speaker, muted, onBack = home)
            SimpleScreen.CALL -> SimpleCall(vm, speaker, muted, onBack = home)
            SimpleScreen.READ -> SimpleRead(vm, speaker, muted, onBack = home)
        }
    }
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
    Surface(onClick = onClick, shape = RoundedCornerShape(SimpleRadius), color = tone.wash, contentColor = tone.ink, border = BorderStroke(2.dp, tone.solid), modifier = modifier.heightIn(min = SimpleMin)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
            SaharaFitText(label, style = LocalTextStyle.current.copy(fontSize = 20.sp, lineHeight = 24.sp), color = tone.ink, maxLines = 2, min = 18f, fontWeight = FontWeight.Bold)
        }
    }
}

/** A large title under the bar: 26sp, high contrast, centred. */
@Composable private fun SimpleTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(horizontal = 16.dp).a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, color = SaharaColors.Ink, textAlign = TextAlign.Center)
}

/** One big tile: a picture over a short label, solid section colour with white text. */
@Composable private fun SimpleTile(label: String, tone: SaharaTone, modifier: Modifier, onClick: () -> Unit, picture: @Composable () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(SimpleRadius), color = tone.solid, contentColor = tone.onSolid, modifier = modifier.heightIn(min = SimpleMin)) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.weight(1f, fill = false).heightIn(max = 96.dp), contentAlignment = Alignment.Center) { picture() }
            Spacer(Modifier.height(8.dp))
            SaharaFitText(label, style = LocalTextStyle.current.copy(fontSize = 24.sp, lineHeight = 28.sp), color = tone.onSolid, maxLines = 3, min = 18f, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- Home

@Composable private fun SimpleHome(speaker: SimpleSpeaker, muted: Boolean, onMute: () -> Unit, onOpen: (SimpleScreen) -> Unit, onFullApp: () -> Unit) {
    val t = careText()
    val prompt = { l: CareLang -> careTr(l, "What would you like to do? Tap a big button.", "आप क्या करना चाहेंगे? किसी बड़े बटन को छुएँ।") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    var leaving by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(null, { speaker.say(prompt) }, muted, onMute)
        // Four buttons fill the screen in two rows: no scrolling, no menus.
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleTile(t("How are you today?", "आज आप कैसे हैं?"), SaharaSection.CheckIns, Modifier.weight(1f).fillMaxHeight().testTag("simple-home-feel"), { onOpen(SimpleScreen.FEEL) }) {
                    Icon(Icons.Default.SentimentSatisfiedAlt, null, Modifier.size(72.dp)) }
                SimpleTile(t("My medicines", "मेरी दवाइयाँ"), SaharaSection.Medicines, Modifier.weight(1f).fillMaxHeight().testTag("simple-home-medicines"), { onOpen(SimpleScreen.MEDICINES) }) {
                    Icon(Icons.Default.Medication, null, Modifier.size(72.dp)) }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleTile(t("Call", "फ़ोन करें"), SaharaTone.Leaf, Modifier.weight(1f).fillMaxHeight().testTag("simple-home-call"), { onOpen(SimpleScreen.CALL) }) {
                    Icon(Icons.Default.Call, null, Modifier.size(72.dp)) }
                SimpleTile(t("Read to me", "मुझे पढ़कर सुनाएँ"), SaharaTone.Indigo, Modifier.weight(1f).fillMaxHeight().testTag("simple-home-read"), { onOpen(SimpleScreen.READ) }) {
                    Icon(Icons.Default.RecordVoiceOver, null, Modifier.size(72.dp)) }
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
    val today = CheckInMood.today(items, person, dayStart)
    val account = remember(person) { vm.accountNamespace }
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
                Surface(onClick = {
                    if (!canEditCareContext(vm, person, account)) { vm.message.value = t("View only", "सिर्फ़ देखने के लिए"); return@Surface }
                    // The same save as the Today check-in: a new check-in, or today's changed to this mood.
                    val change = CareDailyFlow.checkInChange(today, mood)
                    if (change != null) { if (today != null) vm.updateItem(change) {} else vm.saveItem("checkin", mood, "") }
                    onSaved(mood == CheckInMood.HARD)
                }, enabled = !busy, shape = RoundedCornerShape(SimpleRadius), color = if (chosen) SaharaColors.PrimaryWash else Paper,
                    border = BorderStroke(if (chosen) 4.dp else 2.dp, if (chosen) SaharaColors.Primary else SaharaColors.Edge),
                    modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = SimpleMin).testTag("simple-mood-$index").semantics { role = Role.RadioButton; if (chosen) stateDescription = t("Chosen", "चुना गया") }) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CareMoodFace(face, Modifier.size(72.dp).clearAndSetSemantics { })
                        Spacer(Modifier.width(20.dp))
                        Text(checkInMoodLabel(mood, hindi), Modifier.weight(1f), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = SaharaColors.Ink)
                        if (chosen) Icon(Icons.Default.Check, null, Modifier.size(40.dp), tint = SaharaTone.Success.ink)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Where does it hurt?

@Composable private fun SimpleWhere(speaker: SimpleSpeaker, muted: Boolean, sites: List<String>, onChange: (List<String>) -> Unit, onBack: () -> Unit, onNoPain: () -> Unit, onDone: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    var back by rememberSaveable { mutableStateOf(false) }
    val prompt = { l: CareLang -> careTr(l, "Where does it hurt? Touch the place on the body.", "दर्द कहाँ है? शरीर पर उस जगह को छुएँ।") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    val latestSites by rememberUpdatedState(sites)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        SimpleTitle(t("Where does it hurt?", "दर्द कहाँ है?"))
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SimpleChoice(t("Front", "सामने"), Icons.Default.AccessibilityNew, !back, Modifier.weight(1f).testTag("simple-pain-front")) { back = false }
            SimpleChoice(t("Back", "पीछे"), Icons.Default.RotateRight, back, Modifier.weight(1f).testTag("simple-pain-back")) { back = true }
        }
        // The flat body outline from the full pain map (same parts, same left and right), drawn as large as the screen allows.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            val described = if (sites.isEmpty()) t("Body picture. Nothing marked yet.", "शरीर की तस्वीर। अभी कुछ नहीं चुना।") else sites.joinToString(", ") { PainBodyMapRules.label(it, hindi) }
            Canvas(Modifier.fillMaxHeight().aspectRatio(0.5f).testTag("simple-pain-body").semantics { contentDescription = described }.pointerInput(back) {
                detectTapGestures { p ->
                    SimpleModeRules.siteAt(back, p.x * 100f / size.width, p.y * 200f / size.height)?.let { site ->
                        onChange(PainBodyMapRules.toggled(latestSites, site))
                        val part = PainBodyMapRules.sites.getValue(site)
                        speaker.say { l -> careTr(l, part.first, part.second) }
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
        if (sites.isNotEmpty()) Text(sites.joinToString(", ") { PainBodyMapRules.label(it, hindi) }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("simple-pain-selected"),
            fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = SaharaTone.Rose.ink, textAlign = TextAlign.Center, maxLines = 2)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SimpleBarButton(t("No pain", "दर्द नहीं"), Icons.Default.Close, SaharaTone.Neutral, Modifier.weight(1f).testTag("simple-pain-none"), onNoPain)
            if (sites.isNotEmpty()) SimpleBarButton(t("Done", "हो गया"), Icons.Default.Check, SaharaTone.Success, Modifier.weight(1f).testTag("simple-pain-done"), onDone)
            else Box(Modifier.weight(1f).heightIn(min = SimpleMin).background(SaharaColors.Well, RoundedCornerShape(SimpleRadius)), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.TouchApp, null, Modifier.size(28.dp), tint = SaharaColors.InkSub); Spacer(Modifier.width(6.dp)); Text(t("Touch the body", "शरीर को छुएँ"), fontSize = 18.sp, color = SaharaColors.InkSub, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable private fun SimpleChoice(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val t = careText(); val tone = SaharaTone.Rose
    Surface(onClick = onClick, shape = RoundedCornerShape(SimpleRadius), color = if (selected) tone.solid else Paper, contentColor = if (selected) tone.onSolid else SaharaColors.Ink,
        border = BorderStroke(2.dp, if (selected) tone.solid else SaharaColors.Edge), modifier = modifier.heightIn(min = SimpleMin).semantics { role = Role.Tab; if (selected) stateDescription = t("Chosen", "चुना गया") }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(30.dp)); Spacer(Modifier.width(8.dp))
            SaharaFitText(label, style = LocalTextStyle.current.copy(fontSize = 20.sp), color = if (selected) tone.onSolid else SaharaColors.Ink, maxLines = 1, min = 18f, fontWeight = FontWeight.Bold)
        }
    }
}

// ---------------------------------------------------------------- How much does it hurt?

@Composable private fun SimpleHowMuch(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, sites: List<String>, onBack: () -> Unit, onSaved: () -> Unit) {
    val t = careText(); val hindi = careHindi()
    val people by vm.people.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle(); val role by vm.viewRole.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val account = remember(person) { vm.accountNamespace }
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
                    val note = SimpleModeRules.painNote(sites, word, LocalDateTime.now().withSecond(0).withNano(0), name, familyHelped = role == "Family")
                    vm.saveSymptomNote(person, newId(), note, null) { onSaved() }
                }, enabled = !busy && sites.isNotEmpty(), shape = RoundedCornerShape(SimpleRadius), color = Paper, border = BorderStroke(2.dp, SaharaColors.Edge),
                    modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = SimpleMin).testTag("simple-pain-level-${word.key}")) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // A bar that grows with the pain, in the pain map's colours (grey for none).
                        Box(Modifier.width(72.dp).fillMaxHeight(.7f), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(.25f + .75f * word.rating / 9f).background(fill, RoundedCornerShape(10.dp)))
                        }
                        Spacer(Modifier.width(20.dp))
                        Text(careTr(hindi, word.english, word.hindi), Modifier.weight(1f), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = SaharaColors.Ink)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Saved

@Composable private fun SimpleThanks(speaker: SimpleSpeaker, muted: Boolean, onHome: () -> Unit) {
    val t = careText()
    val prompt = { l: CareLang -> careTr(l, "Thank you. It is saved, and your care team can see it.", "धन्यवाद। यह सहेज लिया गया है, और आपकी देखभाल टीम इसे देख सकती है।") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    val latestHome by rememberUpdatedState(onHome)
    LaunchedEffect(Unit) { delay(6_000); latestHome() }
    Column(Modifier.fillMaxSize().testTag("simple-thanks")) {
        SimpleTopBar(null, { speaker.say(prompt) })
        Column(Modifier.weight(1f).fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = SaharaTone.Success.solid, modifier = Modifier.size(140.dp)) { Box(contentAlignment = Alignment.Center) { SaharaDrawnCheck(Modifier.size(96.dp), Color.White, 10.dp) } }
            Spacer(Modifier.height(24.dp))
            SimpleTitle(t("Thank you. It is saved.", "धन्यवाद। सहेज लिया गया।"))
        }
        SimpleBarButton(t("Home", "मुख्य स्क्रीन"), Icons.Default.Home, SaharaTone.Rust, Modifier.fillMaxWidth().padding(16.dp).testTag("simple-home"), onHome)
    }
}

// ---------------------------------------------------------------- My medicines

private fun slotIcon(slot: SimpleModeRules.Slot): ImageVector = when (slot) {
    SimpleModeRules.Slot.MORNING -> Icons.Default.WbSunny
    SimpleModeRules.Slot.AFTERNOON -> Icons.Default.LightMode
    SimpleModeRules.Slot.EVENING -> Icons.Default.WbTwilight
    SimpleModeRules.Slot.NIGHT -> Icons.Default.NightsStay
    SimpleModeRules.Slot.ANY_TIME -> Icons.Default.Schedule
}

private fun slotTone(slot: SimpleModeRules.Slot): SaharaTone = when (slot) {
    SimpleModeRules.Slot.MORNING -> SaharaTone.Marigold; SimpleModeRules.Slot.AFTERNOON -> SaharaTone.Warning
    SimpleModeRules.Slot.EVENING -> SaharaTone.Rose; SimpleModeRules.Slot.NIGHT -> SaharaTone.Indigo; SimpleModeRules.Slot.ANY_TIME -> SaharaTone.Plum
}

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
    val events = DoseUndoWindow.shown(saved, held, person)
    val busy by vm.busy.collectAsStateWithLifecycle()
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
            try { markMedicineDose(context, vm, item, doses[item.id].orEmpty(), dose, true, false, if (dose.timed) dose.time else "", day); speaker.say { l -> careTr(l, "Marked as taken.", "ली हुई दर्ज की गई।") } }
            catch (e: Exception) { if (e is CancellationException) throw e; vm.message.value = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।") }
            finally { working = false }
        } }
        if (MedicineDoses.askWhichNight(item, dose, events, ZonedDateTime.now(zone))) whichNight = dose.time to save else save(today)
    }
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        // The time-of-day pictures: sun, afternoon sun, sunset, moon (and a clock for any time). Only the ones with doses today.
        if (groups.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.keys.forEach { s ->
                val on = s == slot; val tone = slotTone(s); val name = SimpleModeRules.slotName(s, careLanguage())
                Surface(onClick = { slot = s }, shape = RoundedCornerShape(SimpleRadius), color = if (on) tone.solid else Paper, contentColor = if (on) tone.onSolid else tone.ink,
                    border = BorderStroke(2.dp, if (on) tone.solid else SaharaColors.Edge), modifier = Modifier.weight(1f).heightIn(min = SimpleMin).testTag("simple-slot-${s.name.lowercase()}")
                        .semantics { contentDescription = name; role = Role.Tab; if (on) stateDescription = t("Chosen", "चुना गया") }) {
                    Box(contentAlignment = Alignment.Center) { Icon(slotIcon(s), null, Modifier.size(40.dp)) }
                }
            }
        }
        slot?.let { s -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(slotIcon(s), null, Modifier.size(44.dp), tint = slotTone(s).ink); Spacer(Modifier.width(12.dp))
            Text(SimpleModeRules.slotName(s, careLanguage()), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 28.sp, lineHeight = 32.sp, color = SaharaColors.Ink)
        } }
        if (slot == null) Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            SimpleTitle(t("There are no medicines for today.", "आज के लिए कोई दवा नहीं है।"))
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { (item, dose) -> "${item.id}-${dose.key}" }) { (item, dose) ->
                if (MedicineDoses.isAsNeededMedicine(item)) {
                    val times = MedicineDoses.asNeededTimes(item.id, events, today, zone)
                    SimpleDoseCard(item, photos[item.id], if (times.isEmpty()) "" else t("Taken ${times.size} times today", "आज ${times.size} बार ली"), if (times.isEmpty()) MedicineDoseState.PENDING else MedicineDoseState.TAKEN,
                        enabled = !busy && !working, again = true, tag = "simple-dose-${item.id}-as-needed") {
                        if (!canEditCareContext(vm, item.personId, account)) { vm.message.value = t("View only", "सिर्फ़ देखने के लिए"); return@SimpleDoseCard }
                        scope.launch { working = true; try { recordAsNeededDose(context, vm, item); speaker.say { l -> careTr(l, "Marked as taken.", "ली हुई दर्ज की गई।") } } catch (e: Exception) { if (e is CancellationException) throw e; vm.message.value = t("Couldn't save. Please try again.", "सहेजा नहीं जा सका। कृपया फिर कोशिश करें।") } finally { working = false } }
                    }
                } else {
                    val state = MedicineDoses.state(item, dose, events, today, zone)
                    SimpleDoseCard(item, photos[item.id], if (dose.timed) dose.time else if (dose.count > 1) t("Dose ${dose.index}", "खुराक ${dose.index}") else "", state,
                        enabled = !busy && !working && state != MedicineDoseState.TAKEN, again = false, tag = "simple-dose-${item.id}-${dose.key}") { take(item, dose) }
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
    val word = when (state) { MedicineDoseState.TAKEN -> t("Taken", "ली"); MedicineDoseState.MISSED -> t("Not taken", "नहीं ली"); else -> t("Not yet", "अभी नहीं") }
    Surface(shape = RoundedCornerShape(SimpleRadius), color = if (taken) SaharaColors.SuccessWash else Paper, border = BorderStroke(2.dp, if (taken) SaharaColors.Success else SaharaColors.Edge),
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
            val ring = if (missed) SaharaColors.Error else SaharaColors.Success
            Surface(onClick = onTaken, enabled = enabled, shape = CircleShape, color = if (taken) SaharaColors.Success else Color.White, border = BorderStroke(4.dp, ring),
                modifier = Modifier.size(84.dp).semantics { contentDescription = "${item.title}: " + if (taken && !again) word else t("Mark taken", "ली हुई दर्ज करें"); stateDescription = word }) {
                Box(contentAlignment = Alignment.Center) {
                    if (missed) Icon(Icons.Default.Close, null, Modifier.size(52.dp), tint = SaharaColors.Error)
                    else Icon(Icons.Default.Check, null, Modifier.size(56.dp), tint = if (taken) Color.White else SaharaColors.Success)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Call

@Composable private fun SimpleCall(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, onBack: () -> Unit) {
    val t = careText(); val context = LocalContext.current
    val person by vm.selected.collectAsStateWithLifecycle()
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    LaunchedEffect(person) { vm.refreshCareContacts() }
    val callable = remember(contacts, person) { SimpleModeRules.callable(contacts, person) }
    val prompt = { l: CareLang -> careTr(l, "Who do you want to call?", "आप किसे फ़ोन करना चाहते हैं?") }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    // One tap opens the phone's dialler with the number filled in; the call itself starts from the dialler.
    fun dial(number: String) = open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(number))))
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        SimpleTitle(t("Who do you want to call?", "आप किसे फ़ोन करना चाहते हैं?"))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(callable, key = { it.id }) { contact ->
                SimpleCallButton(SimpleModeRules.initial(contact.name), contact.name, contact.relationship, SaharaTone.Leaf, "simple-call-${contact.id}") { dial(contact.phone) }
            }
            item(key = "ambulance") { SimpleCallButton(null, t("Ambulance 108", "एम्बुलेंस 108"), t("Emergency", "आपातकाल"), SaharaTone.Error, "simple-call-108") { dial(SimpleModeRules.AMBULANCE) } }
            if (callable.isEmpty()) item(key = "none") { Text(t("Family can add phone numbers for the care team in the full app.", "परिवार पूरे ऐप में देखभाल टीम के फ़ोन नंबर जोड़ सकता है।"),
                Modifier.fillMaxWidth().padding(top = 8.dp), fontSize = 18.sp, lineHeight = 24.sp, color = SaharaColors.InkSub, textAlign = TextAlign.Center) }
        }
    }
}

/** A call button: a big round letter (the care-team data has no photos), the name, and who they are. [initial] null shows the ambulance cross. */
@Composable private fun SimpleCallButton(initial: String?, name: String, who: String, tone: SaharaTone, tag: String, onClick: () -> Unit) {
    val t = careText()
    Surface(onClick = onClick, shape = RoundedCornerShape(SimpleRadius), color = Paper, border = BorderStroke(2.dp, tone.solid),
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).testTag(tag).semantics { contentDescription = t("Call $name", "$name को फ़ोन करें") }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = tone.solid, contentColor = tone.onSolid, modifier = Modifier.size(80.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (initial == null) Icon(Icons.Default.LocalHospital, null, Modifier.size(48.dp))
                    else Text(initial, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = tone.onSolid, maxLines = 1)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = SaharaColors.Ink, maxLines = 2)
                if (who.isNotBlank()) Text(who, fontSize = 18.sp, lineHeight = 22.sp, color = SaharaColors.InkSub, maxLines = 1)
            }
            Icon(Icons.Default.Call, null, Modifier.size(40.dp), tint = tone.ink)
        }
    }
}

// ---------------------------------------------------------------- Read to me

/**
 * Read to me. Sahara has no direct message from the doctor or nurse to the patient yet (care notes are goals-of-care
 * records and are not read here), so this reads today's medicines by time of day and the next visit.
 */
@Composable private fun SimpleRead(vm: CareModel, speaker: SimpleSpeaker, muted: Boolean, onBack: () -> Unit) {
    val t = careText(); val lang = careLanguage()
    val items by vm.items.collectAsStateWithLifecycle()
    val (medicines, doses) = rememberSimpleDoses(vm)
    val groups = remember(medicines, doses) { SimpleModeRules.group(medicines.flatMap { m -> doses[m.id].orEmpty().map { m to it } }).mapValues { (_, list) -> list.map { it.first.title } } }
    val zone = ZoneId.systemDefault(); val today = LocalDate.now(zone)
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val visit = remember(items, dayStart) { CareTodayRules.upcomingVisits(items, dayStart).firstOrNull() }
    val visitText = { l: CareLang -> visit?.let { v ->
        val time = if (v.due == 0L) "" else ", " + Instant.ofEpochMilli(v.due).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val whenText = CareLanguageState.inLanguage(l) { CareTodayRules.whenLabel(v.due, today, l == CareLang.HI, zone) }
        "${v.title.trim()}, $whenText$time"
    } }
    val body = { l: CareLang -> SimpleModeRules.dayText(groups, visitText(l), l) }
    val prompt = { l: CareLang -> careTr(l, "Here are today's medicines and your next visit.", "ये हैं आज की दवाइयाँ और आपकी अगली मुलाक़ात।") + "\n" + body(l) }
    SpeakOnOpen(speaker, muted, prompt = prompt)
    Column(Modifier.fillMaxSize()) {
        SimpleTopBar(onBack, { speaker.say(prompt) })
        SimpleTitle(t("Here are today's medicines and your next visit.", "ये हैं आज की दवाइयाँ और आपकी अगली मुलाक़ात।"))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Surface(shape = RoundedCornerShape(SimpleRadius), color = Paper, border = BorderStroke(2.dp, SaharaColors.Edge), modifier = Modifier.fillMaxWidth()) {
                Text(body(lang), Modifier.padding(20.dp).testTag("simple-read-text"), fontSize = 22.sp, lineHeight = 32.sp, color = SaharaColors.Ink)
            }
        }
        SimpleBarButton(t("Read it again", "फिर से पढ़ें"), Icons.Default.VolumeUp, SaharaTone.Indigo, Modifier.fillMaxWidth().padding(16.dp).testTag("simple-read-again")) { speaker.say(prompt) }
    }
}

// ---------------------------------------------------------------- The switch in More

/** The "Simple mode" row in More's settings: patient and family switch it for the selected patient, on this phone only. */
@Composable fun SimpleModeSwitchRow(vm: CareModel, person: Person) {
    val t = careText(); val context = LocalContext.current
    val on = rememberSimpleModeOn(vm.storageNamespace, person.id)
    val change: (Boolean) -> Unit = { value ->
        SimpleModeStore.setOn(context, vm.storageNamespace, person.id, value)
        if (value) vm.message.value = t("Simple mode is on. Open Today to see it.", "सरल मोड चालू है। देखने के लिए आज खोलें।")
    }
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
}

