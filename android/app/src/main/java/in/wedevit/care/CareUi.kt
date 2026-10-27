@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter

val Ink = Color(0xFF382E29)
val Cream = Color(0xFFFFF8EE)
val Paper = Color(0xFFFFFDF9)
val Rust = Color(0xFFB9472C)
val Gold = Color(0xFFFFCF5C)
val Sage = Color(0xFFD9EAA8)
val Mint = Color(0xFFDCEBE4)
val Peach = Color(0xFFFFD1B3)
internal val WelcomePanelOrange = Color(0xFFD97757)
internal val WelcomePanelInk = Color(0xFF2B1F19)
internal fun welcomePanelGapDp(screenHeightDp: Int, fontScale: Float): Int =
    if (screenHeightDp < 640 || fontScale >= 1.3f) 32 else 64
internal fun welcomeHeroTopPaddingDp(screenHeightDp: Int, fontScale: Float): Int =
    if (screenHeightDp < 640 || fontScale >= 1.3f) 48 else 72

/** Live identity checks for delayed form callbacks; a display role never grants edit access. */
internal fun careEditContextMatches(expectedAccount: String, modelAccount: String, sessionAccount: String, person: String, selected: String, editable: Boolean): Boolean =
    expectedAccount == modelAccount && expectedAccount == sessionAccount && person.isNotBlank() && person == selected && editable

internal fun canEditCareContext(vm: CareModel, person: String, account: String): Boolean =
    careEditContextMatches(account, vm.accountNamespace, vm.cloud?.session?.value?.userId.orEmpty(), person, vm.selected.value, vm.canEdit.value) && vm.people.value.any { it.id == person }

@Composable fun CareTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Rust, onPrimary = Color.White, secondary = Color(0xFF465E45),
        background = Cream, surface = Paper, onSurface = Ink, onBackground = Ink, surfaceVariant = Color(0xFFF1E9DE),
        onSurfaceVariant = Color(0xFF65574E), secondaryContainer = Mint, onSecondaryContainer = Ink,
        outline = Color(0xFF8D7B70)), typography = saharaTitleTypography(), content = content)
}

@Composable fun CareApp(vm: CareModel, onAccount: () -> Unit, onDoctorDemo: () -> Unit = {}, onDoctorWorkspace: (() -> Unit)? = null,
    welcomeAccountContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    welcomeDoctorContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    onWelcomePanelChanged: (String) -> Unit = {}, onPatientWalkthrough: (String) -> Unit = {},
    walkthrough: PatientWalkthrough.User? = null, onLeaveWalkthrough: () -> Unit = {}, onSwitchWalkthrough: (String) -> Unit = {}) {
    val allPeople by vm.people.collectAsStateWithLifecycle()
    val people = if (walkthrough != null) PatientWalkthrough.visiblePeople(allPeople) else allPeople
    val selected by vm.selected.collectAsStateWithLifecycle()
    val clinician by vm.clinician.collectAsStateWithLifecycle()
    val role by vm.viewRole.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val activeRecording by vm.recording.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshError by vm.refreshError.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Screens opened from inside a tab (Calendar, Care team, Lab history, Symptoms, Docs from More…) remember where they came from.
    var tabTrail by rememberSaveable { mutableStateOf("") }
    var tabDirection by remember { mutableStateOf(SaharaMotion.Direction.Switch) }
    val trail = tabTrail.split(',').mapNotNull { it.toIntOrNull() }
    fun go(next: Int) { if (next != tab) { tabTrail = (trail + tab).takeLast(10).joinToString(","); tabDirection = SaharaMotion.Direction.Forward; tab = next } }
    fun switchTab(next: Int) { tabTrail = ""; tabDirection = SaharaMotion.Direction.Switch; tab = next }
    fun back() { tabDirection = SaharaMotion.Direction.Back; if (trail.isNotEmpty()) { tab = trail.last(); tabTrail = trail.dropLast(1).joinToString(",") } else tab = 0 }
    var dialog by rememberSaveable { mutableStateOf("") }
    var inviteFamilyOpen by rememberSaveable { mutableStateOf(false) }
    var medicineScanner by rememberSaveable { mutableStateOf(false) }
    var medicineScannerPerson by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf<CareVersion?>(null) }
    val feedbackAccountScope = vm.cloud?.session?.value?.userId ?: "guest-patient"
    var feedbackOpen by rememberSaveable(feedbackAccountScope) { mutableStateOf(false) }
    var remindersOpen by rememberSaveable(feedbackAccountScope) { mutableStateOf(false) }
    var sharedWorkspaceOpen by rememberSaveable(feedbackAccountScope) { mutableStateOf(false) }
    val openDoctorWorkspace = { if (onDoctorWorkspace != null) onDoctorWorkspace() else sharedWorkspaceOpen = true }
    val screens = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val person = people.firstOrNull { it.id == selected }
    val compactWelcomeHeader = LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.15f
    LaunchedEffect(selected) {
        reading = null
        if (medicineScanner && medicineScannerPerson != selected) medicineScanner = false
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.stopRecording()
            if (event == Lifecycle.Event.ON_START) vm.refresh()
        }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    val peopleLoaded by vm.peopleLoaded.collectAsStateWithLifecycle()
    val overlay = when { !peopleLoaded -> "starting"; dialog == "checkin" -> "checkin"; feedbackOpen -> "feedback"; remindersOpen -> "reminders"; sharedWorkspaceOpen -> "shared"; else -> "" }
    Box(Modifier.fillMaxSize().background(Cream)) {
    SaharaScreenHost(overlay, label = "Care overlay", direction = { from, to -> if (from == "starting") SaharaMotion.Direction.Switch else if (to.isEmpty()) SaharaMotion.Direction.Back else SaharaMotion.Direction.Forward }) { shown -> when (shown) {
    "starting" -> Box(Modifier.fillMaxSize().testTag("care-starting"))
    "checkin" -> FeedbackScreen(accountScope = feedbackAccountScope, surface = "patient-checkin", onClose = { dialog = "" }, careModel = vm, personId = vm.selected.value)
    "feedback" -> FeedbackScreen(accountScope = feedbackAccountScope, surface = "patient", onClose = { feedbackOpen = false })
    "reminders" -> CareReminderSettings(vm, onClose = { remindersOpen = false })
    "shared" -> CareSharedWorkspace(vm, onAccount = onAccount, onClose = { sharedWorkspaceOpen = false })
    else -> {
    BackHandler(careTabBackEnabled(person != null, medicineScanner, tab, activeRecording)) { if (activeRecording) vm.stopRecording() else back() }
    BackHandler(walkthrough != null && !medicineScanner && tab == 0 && !activeRecording) { onLeaveWalkthrough() }
    Scaffold(containerColor = Cream, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        if (!medicineScanner) Column {
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(saharaImage(R.drawable.sahara_professional_logo, 44.dp), null, Modifier.size(44.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 2.dp)) {
                    Text(androidx.compose.ui.res.stringResource(R.string.app_name), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (person != null && walkthrough != null) WalkthroughUserMenu(walkthrough, onSwitch = { if (activeRecording) vm.stopRecording(); onSwitchWalkthrough(it) }, onLeave = { if (activeRecording) vm.stopRecording(); onLeaveWalkthrough() })
            if (person == null && onDoctorWorkspace == null) OutlinedButton(onClick = { sharedWorkspaceOpen = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-workspace-entry").semantics { contentDescription = "Doctor workspace" }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                if (compactWelcomeHeader) { Icon(Icons.Default.MedicalServices, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                Text(if (compactWelcomeHeader) "Doctor" else "Doctor workspace", fontSize = 14.sp, maxLines = 1)
            }
        }
        if (person != null) {
            CarePersonSelector(person.name, people.map { it.id to it.name }, selected,
                enabled = !activeRecording,
                onSelect = { vm.select(it); reading = null },
                onAdd = { dialog = "person" }, canAdd = walkthrough == null)
            Spacer(Modifier.height(10.dp))
        }
        if (person != null) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .15f))
        }
    }, bottomBar = {
        if (person != null && !medicineScanner) CareNavigation(if (tab in 6..9) trail.lastOrNull { it !in 6..9 } ?: tab else tab, clinician) { next ->
            if (activeRecording && next != 1) vm.stopRecording()
            switchTab(next)
        }
        else if (person == null) Spacer(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(WelcomePanelOrange).testTag("welcome-navigation-backdrop"))
    }) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            if (medicineScanner && person != null) MedicineScanner(vm, onClose = { medicineScanner = false }, onAccount = onAccount)
            else if (person == null) Welcome(onAccount = onAccount, onStart = { dialog = "person" }, onSharedWorkspace = onDoctorWorkspace ?: { sharedWorkspaceOpen = true }, onDoctorDemo = onDoctorDemo, onPatientWalkthrough = onPatientWalkthrough,
                accountContent = welcomeAccountContent, doctorContent = welcomeDoctorContent, onPanelChanged = onWelcomePanelChanged,
                entryBusy = busy || refreshing, addPersonContent = { close -> AddPersonContent(busy = busy || refreshing, error = actionError, onDismiss = close, onSave = { vm.addPerson(it) { close() } }) })
            else SaharaScreenHost(person.id to tab, Modifier.fillMaxSize(), label = "Care tab", direction = { from, _ -> if (from.first == person.id) tabDirection else SaharaMotion.Direction.Switch }) { (shownId, shownTab) -> screens.SaveableStateProvider("$shownId:$shownTab") {
                Box(Modifier.fillMaxSize()) { when (shownTab) {
                    0 -> Today(vm, clinician, onTalk = { go(1) }, onCare = { go(2) }, onNearby = { go(3) }, onAdd = { if (canEdit) dialog = it else vm.message.value = "Ask the person who invited you to allow changes." }, onRead = { reading = it }, onCalendar = { go(6) }, onWorkflow = { go(7) }, onLabs = { go(8) }, onSharedWorkspace = if (walkthrough != null) ({ go(7) }) else openDoctorWorkspace, onSymptoms = { go(9) }, onInviteFamily = { inviteFamilyOpen = true })
                    1 -> Talk(vm, clinician && canEdit, onRead = { reading = it }, onRequestConversation = { go(7) })
                    2 -> CareList(vm, onReadNote = { reading = it }, onCalendar = { go(6) }, onWorkflow = { go(7) }, onLabs = { go(8) }, onSymptoms = { go(9) },
                        onAdd = { if (canEdit) dialog = it else vm.message.value = "Ask the person who invited you to allow changes." },
                        onScanPrescription = {
                            if (canEdit) {
                                medicineScannerPerson = selected
                                medicineScanner = true
                            } else vm.message.value = "Ask the person who invited you to allow changes."
                        })
                    3 -> Nearby(vm.centres)
                    5 -> CareDocuments(vm, onRead = { reading = it }, onTalk = { go(1) })
                    6 -> CareCalendar(vm, onWorkflow = { go(7) })
                    7 -> CareWorkflows(vm, onTalk = { go(1) })
                    8 -> CareLabs(vm)
                    9 -> CareSymptomDiary(vm, onBack = { back() })
                    else -> More(vm, person, onAccount = onAccount, onDelete = { dialog = "delete" }, onCalendar = { go(6) }, onWorkflow = { go(7) }, onLabs = { go(8) }, walkthrough = walkthrough != null, onFeedback = { if (activeRecording) vm.stopRecording(); feedbackOpen = true }, onReminders = { if (activeRecording) vm.stopRecording(); remindersOpen = true }, onSharedWorkspace = { if (activeRecording) vm.stopRecording(); openDoctorWorkspace() }, onDocuments = { if (activeRecording) vm.stopRecording(); go(5) })
                } }
            } }
            refreshError?.let { error -> Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Peach) { Row(verticalAlignment = Alignment.CenterVertically) { Text(error, Modifier.weight(1f).padding(12.dp), style = MaterialTheme.typography.bodySmall); TextButton(onClick = vm::refresh) { Text("Retry") } } } }
            SaharaTopProgress(busy || refreshing, Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (inviteFamilyOpen) AddFamilySheet(vm, onDismiss = { inviteFamilyOpen = false }, onSignIn = onAccount)
    if (dialog == "person") AddPerson(onDismiss = { dialog = "" }, busy = busy || refreshing, error = actionError, onSave = { vm.addPerson(it) { dialog = "" } })
    if (dialog in listOf("medicine", "visit", "task", "journal")) AddCareItem(dialog, vm, onDismiss = { dialog = "" })
    if (dialog == "delete") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Remove ${person?.name}'s records?") },
        text = { Text("This removes their notes, reports, recordings and care entries. Export anything you want to keep first.") },
        confirmButton = { TextButton(onClick = { val removedId = person?.id; vm.removePerson { (0..9).forEach { screens.removeState("$removedId:$it") }; dialog = "" } }, enabled = !busy) { Text("Remove records") } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Keep records") } })
    reading?.let { note -> NoteReader(vm, note, onDismiss = { reading = null }) }
    }
    } }
    if (overlay.isNotEmpty() && overlay != "starting") SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }
}


/** System back walks the care tabs only while a person's tabs are on screen; on the welcome screen (for example after removing the last person from More) back leaves the app as expected. */
internal fun careTabBackEnabled(personShown: Boolean, medicineScanner: Boolean, tab: Int, activeRecording: Boolean): Boolean = personShown && !medicineScanner && (tab != 0 || activeRecording)

@Composable fun Welcome(onAccount: () -> Unit, onStart: () -> Unit, onSharedWorkspace: () -> Unit = {}, onDoctorDemo: () -> Unit = {}, onPatientWalkthrough: (String) -> Unit = {},
    addPersonContent: (@Composable (() -> Unit) -> Unit)? = null,
    accountContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    doctorContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    onPanelChanged: (String) -> Unit = {}, entryBusy: Boolean = false) {
    var panel by rememberSaveable { mutableStateOf("") }
    var formBusy by remember { mutableStateOf(false) }
    var walkthroughChooser by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val panelGap = welcomePanelGapDp(LocalConfiguration.current.screenHeightDp, LocalDensity.current.fontScale)
    val heroTopPadding = welcomeHeroTopPaddingDp(LocalConfiguration.current.screenHeightDp, LocalDensity.current.fontScale)
    fun select(value: String) { if (!formBusy && !entryBusy) { focus.clearFocus(); panel = value; onPanelChanged(value) } }
    val close = { if (!formBusy && !entryBusy) select(if (panel == "person" || panel == "account") "family" else "") }
    BackHandler(panel.isNotEmpty()) { close() }
    if (walkthroughChooser) PatientWalkthroughChooser(onPick = { walkthroughChooser = false; onPatientWalkthrough(it) }, onDismiss = { walkthroughChooser = false })
    Column(Modifier.fillMaxSize().imePadding().testTag("welcome-panel-screen")) {
        AnimatedVisibility(panel.isEmpty(), modifier = if (panel.isEmpty()) Modifier.weight(1f) else Modifier, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = heroTopPadding.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                WelcomeHero()
                Text("Palliative care,\ntogether.", Modifier.fillMaxWidth(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp)); Text("Care notes, medicines and visits.", Modifier.fillMaxWidth(), fontSize = 16.sp, textAlign = TextAlign.Center)
            }
            }
        }
        AnimatedVisibility(panel.isEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
            Spacer(Modifier.fillMaxWidth().height(panelGap.dp).testTag("welcome-panel-gap"))
        }
        Surface(color = WelcomePanelOrange, contentColor = WelcomePanelInk, shape = RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp), modifier = Modifier.fillMaxWidth().then(if (panel.isEmpty()) Modifier else Modifier.weight(1f)).animateContentSize().testTag("welcome-orange-panel")) {
            Column(if (panel.isEmpty()) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                if (panel.isEmpty()) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Welcome to Sahara", modifier = Modifier.fillMaxWidth().testTag("welcome-panel-heading"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 26.sp, textAlign = TextAlign.Center)
                    Text("Choose where you would like to begin.", modifier = Modifier.fillMaxWidth().testTag("welcome-panel-description"), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Button(onClick = { select("family") }, enabled = !entryBusy, colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Rust), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-patient-family")) { Icon(Icons.Default.People, null); Spacer(Modifier.width(10.dp)); Text("Patient & family") }
                    TextButton(onClick = { select("doctor") }, enabled = !entryBusy, colors = ButtonDefaults.textButtonColors(contentColor = WelcomePanelInk), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-workspace-entry")) { Text("Doctor workspace", textDecoration = TextDecoration.Underline) }
                } else {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = close, enabled = !formBusy && !entryBusy, modifier = Modifier.testTag("welcome-panel-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if (panel == "person" || panel == "account") "Back to patient and family" else "Back to welcome", tint = WelcomePanelInk) }
                        Text(when(panel) { "family" -> "Patient & family"; "person" -> "Add a person"; "doctor" -> "Doctor workspace"; else -> "Your account" }, fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    }
                    Surface(color = Paper, contentColor = Ink, shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Box(Modifier.fillMaxSize().padding(top = 20.dp)) { when(panel) {
                            "family" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Heading("Care, together", 27)
                                Text("Start a care profile, or sign in to the records you already use.", style = MaterialTheme.typography.bodyLarge)
                                Button(onClick = { if (addPersonContent == null) onStart() else select("person") }, enabled = !entryBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-add-person")) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(10.dp)); Text("Add a person") }
                                OutlinedButton(onClick = { if (accountContent == null) onAccount() else select("account") }, enabled = !entryBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-account")) { Icon(Icons.Default.AccountCircle, null); Spacer(Modifier.width(10.dp)); Text("Sign in to your account") }
                                TextButton(onClick = { walkthroughChooser = true }, enabled = !entryBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("patient-walkthrough-entry")) { Text("Try the walkthrough") }
                            }
                            "person" -> addPersonContent?.invoke(close)
                            "doctor" -> Column(Modifier.fillMaxSize()) {
                                Box(Modifier.fillMaxWidth().weight(1f)) {
                                    if (doctorContent != null) doctorContent.invoke(close) { formBusy = it }
                                    else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Heading("Doctor sign in", 27)
                                        Text("Open your account-connected workspace.")
                                        Button(onClick = onSharedWorkspace, enabled = !formBusy && !entryBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-open-doctor-workspace")) { Text("Open doctor workspace") }
                                    }
                                }
                                TextButton(onClick = onDoctorDemo, enabled = !formBusy && !entryBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-demo-entry")) { Text("Try the walkthrough") }
                            }
                            else -> accountContent?.invoke(close) { formBusy = it }
                        } }
                    }
                }
            }
        }
    }
}

@Composable fun Today(vm: CareModel, clinician: Boolean, onTalk: () -> Unit, onCare: () -> Unit, onNearby: () -> Unit, onAdd: (String) -> Unit, onRead: (CareVersion) -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, onSharedWorkspace: () -> Unit = {}, onSymptoms: () -> Unit = {}, onInviteFamily: () -> Unit = {}) {
    val ownerOfRecords by vm.isOwner.collectAsStateWithLifecycle(); val todayPeople by vm.people.collectAsStateWithLifecycle(); val todaySelected by vm.selected.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val selectedPerson by vm.selected.collectAsStateWithLifecycle(); val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    val loaded = loadedFor == selectedPerson
    val latest = versions.firstOrNull()
    val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    LazyColumn(Modifier.fillMaxSize().testTag("today-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Heading("Today", 30); Spacer(Modifier.weight(1f)); Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM")), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            if (vm.cloud != null) IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Refresh records") }
        } }
        if (!clinician) item {
            // Do not infer a current relationship from a note's typed reviewer.
            // The account workspace loads actual patient-controlled membership.
            TextButton(onClick = onSharedWorkspace, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Groups, null); Spacer(Modifier.width(8.dp)); Text("Your care team") }
        }
        if (!clinician && ownerOfRecords) item {
            ActionCard(Mint, onClick = onInviteFamily) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, null, tint = Rust); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(if (vm.cloud == null) "Add family" else "Invite family", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text("Let family see ${todayPeople.firstOrNull { it.id == todaySelected }?.name.orEmpty()}'s care on their own phone", fontSize = 13.sp) }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
                }
            }
        }
        item { ActionCard(Gold, onClick = { if (!clinician && latest != null) onRead(latest) else onTalk() }) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Goals of care", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(5.dp)); Heading(if (clinician) "Let's talk" else "Your care notes", 27)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (clinician) "Start talking" else "Read notes", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Image(saharaImage(R.drawable.care_notes_illustration, 104.dp), null, Modifier.size(104.dp), contentScale = ContentScale.Fit)
            }
        } }
        if (clinician && latest != null) item { ActionCard(Paper, onClick = { onRead(latest) }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Latest care note", fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium); Text("${date(latest.published)} · ${latest.doctor}", style = MaterialTheme.typography.bodySmall); Text(ReviewWorkflow.label(latest), style = MaterialTheme.typography.labelSmall, color = Rust) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, "Read note", Modifier.size(20.dp)) }
        } }
        item { Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Symptom tracker", R.drawable.symptom_tracker_illustration, Peach, Modifier.weight(1f).fillMaxHeight().testTag("symptom-tracker-entry")) { onSymptoms() }
            Tile("Find care", R.drawable.nearby_care_illustration, Sage, Modifier.weight(1f).fillMaxHeight(), onNearby)
        } }
        item { DailyCareShortcuts(onCalendar, onWorkflow, onLabs) }
        item { WorkflowSummary(vm, onWorkflow) }
        val scheduledToday = calendarItems(items, LocalDate.now()).filter { CareEntryCodec.decode(it.detail)?.type == "schedule" }
        if (scheduledToday.isNotEmpty()) item { Card(onClick = onCalendar, colors = CardDefaults.cardColors(containerColor = Mint), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("${scheduledToday.size} planned today", fontWeight = FontWeight.SemiBold); scheduledToday.take(2).forEach { Text("${calendarCategory(it)} · ${it.title}", style = MaterialTheme.typography.bodyMedium) }; Text("Open calendar", color = RustText, style = MaterialTheme.typography.labelLarge) } } }
        val checkin = items.firstOrNull { it.kind == "checkin" && it.created >= dayStart }
        if (checkin != null) item { CareRow(checkin, vm) }
        item { SectionTitle("Up next", "Add visit") { onAdd("visit") } }
        val visits = items.filter { it.kind == "visit" && (!CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it)) && CareEntryCodec.decode(it.detail)?.status !in listOf("cancelled", "completed") && (it.due == 0L || it.due >= LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }.sortedBy { it.due }
        if (visits.isEmpty() && loaded) item { SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Event, null, tint = Rust); Spacer(Modifier.width(12.dp)); Text("No upcoming visits") } } }
        items(visits.take(2), key = { it.id }) { if (isDoctorStructuredVisit(it)) DoctorVisitSummary(it, onOpen = onCalendar) else CareRow(it, vm) }
        item { SectionTitle("Medicines", "See all", onCare) }
        val medicines = items.filter { it.kind == "medicine" }
        if (medicines.isEmpty() && loaded) item { OutlinedButton(onClick = { onAdd("medicine") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Add a medicine") } }
        items(medicines.take(3), key = { it.id }) { CareRow(it, vm) }
        item { SectionTitle("To do", "Add task") { onAdd("task") } }
        val tasks = items.filter { it.kind == "task" && !CareEntryCodec.isStructured(it.detail) }.sortedBy { it.doneOn.isNotBlank() }
        if (tasks.isEmpty() && loaded) item { SurfaceCard { Text("Nothing to do yet", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(tasks.take(4), key = { it.id }) { CareRow(it, vm) }
        if (tasks.size > 4) item { TextButton(onClick = onCare) { Text("See all ${tasks.size} tasks") } }
        item { SectionTitle("Journal", "Write a note") { onAdd("journal") } }
        val notes = items.filter { it.kind == "journal" }
        if (notes.isEmpty() && loaded) item { SurfaceCard { Text("No notes yet", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(notes.take(2), key = { it.id }) { CareRow(it, vm) }
    }
}

@Composable fun Talk(vm: CareModel, clinician: Boolean, onRead: (CareVersion) -> Unit, onRequestConversation: (() -> Unit)? = null, onInteractionActiveChange: (Boolean) -> Unit = {}, doctorPresentation: Boolean = false, onDoctorBack: (() -> Unit)? = null, onDoctorStartNew: (() -> Unit)? = null, onDoctorCaptureChange: (Boolean) -> Unit = {}) {
    val draft by vm.draft.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle(); val seconds by vm.recordingSeconds.collectAsStateWithLifecycle(); val level by vm.amplitude.collectAsStateWithLifecycle()
    val recordingSaving by vm.recordingSaving.collectAsStateWithLifecycle()
    val recordingSaveError by vm.recordingSaveError.collectAsStateWithLifecycle()
    val recordingCanEdit by vm.canEdit.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val recordingConsents by vm.recordingConsents.collectAsStateWithLifecycle()
    val transcribingPath by vm.transcribingPath.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var topStep by rememberSaveable { mutableIntStateOf(0) }
    var subPage by rememberSaveable { mutableIntStateOf(0) }
    val step = ConversationPages.flat(topStep, subPage)
    var buildSection by rememberSaveable { mutableIntStateOf(0) }
    var dictating by remember { mutableStateOf(false) }
    var audienceBusy by remember { mutableStateOf(false) }
    var preparationBusy by remember { mutableStateOf(false) }
    var captureMode by rememberSaveable(draft.personId) { mutableStateOf(if (vm.localAudioRecordingAllowed) "Record" else "Type") }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val session = vm.cloud?.session?.collectAsStateWithLifecycle()?.value
    val peopleList by vm.people.collectAsStateWithLifecycle()
    val attendeeStore = remember(context, vm.storageNamespace) { DoctorAttendeeStore(context, vm.storageNamespace) }
    val attendeeScope = remember(vm.accountNamespace, draft.personId, draft.conversationId) { if (draft.personId.isBlank()) "" else DoctorAttendeeStore.scope(vm.accountNamespace, draft.personId, draft.conversationId) }
    val attendeeKept = remember { mutableStateOf(emptyList<DoctorAttendee>()) }
    var attendees by remember(attendeeScope) { mutableStateOf(if (attendeeScope.isBlank()) emptyList<DoctorAttendee>() else runCatching { attendeeStore.load(attendeeScope) }.getOrDefault(emptyList())) }
    LaunchedEffect(attendeeScope) { if (attendeeScope.isNotBlank() && attendees.isEmpty() && attendeeKept.value.isNotEmpty()) runCatching { attendeeStore.save(attendeeScope, attendeeKept.value); attendees = attendeeKept.value } }
    val attendeesConsented = doctorAttendeesAllConsented(attendees)
    var buildOpen by rememberSaveable { mutableStateOf(false) }
    fun nextStep(value: Int) { if (!recordingSaving && recordingSaveError == null) { if (value >= 1 && step < 1 && !attendeesConsented) { vm.message.value = "Recording is available once everyone listed has agreed."; return }; focus.clearFocus(); topStep = ConversationPages.top(value); subPage = ConversationPages.sub(value) } }
    // System back goes one page back, exactly like each page's own Back button.
    fun previousPage() { when (step) { 1 -> nextStep(0); 2 -> nextStep(1); 3 -> if (buildOpen) { focus.clearFocus(); if (buildSection == 0) buildOpen = false else buildSection -= 1 } else nextStep(2); 4 -> { buildSection = 0; buildOpen = true; nextStep(3) }; 5 -> nextStep(4) } }
    var permissionDialog by remember { mutableStateOf(false) }; var doctor by rememberSaveable { mutableStateOf("") }; var reviewed by rememberSaveable { mutableStateOf(false) }
    var recordingExpected by remember { mutableStateOf<DoctorConversationIdentity?>(null) }
    // Saved audio: one session can hold several recordings, and the clinician ticks the ones this note is built from.
    var savedAudioPicked by rememberSaveable { mutableStateOf(setOf<String>()) }
    var playingPath by remember { mutableStateOf<String?>(null) }
    var playingNow by remember { mutableStateOf(false) }
    // When a recording plays to the end, the player bar goes back to Play instead of staying on Pause.
    val audioPlayer = remember { android.media.MediaPlayer().apply { setOnCompletionListener { playingNow = false } } }
    DisposableEffect(Unit) { onDispose { runCatching { audioPlayer.release() } } }
    SideEffect { onInteractionActiveChange(dictating || permissionDialog || audienceBusy || preparationBusy || recordingSaving || recordingSaveError != null) }
    DisposableEffect(Unit) { onDispose { onInteractionActiveChange(false) } }
    var purposeAccepted by rememberSaveable { mutableStateOf(false) }
    var storageAccepted by rememberSaveable { mutableStateOf(false) }
    var accessAccepted by rememberSaveable { mutableStateOf(false) }
    var transcriptionAccepted by rememberSaveable { mutableStateOf(false) }
    // Who is present, and their agreement, is captured once in step 1 ("Who's here"). This dialog confirms it rather than asking again.
    val consentNames = attendees.map { it.name.trim() }.filter { it.isNotBlank() }
    val allParticipantsAgreed = attendeesConsented
    val preparationAccount = vm.accountNamespace
    val preparationStore = remember(context, vm.storageNamespace) { DoctorPreparationStore(context, vm.storageNamespace) }
    val preparationScope = remember(preparationAccount, draft.personId, draft.conversationId) { if (draft.personId.isBlank()) "" else DoctorPreparationStore.scope(preparationAccount, draft.personId, draft.conversationId) }
    var prepRows by remember(preparationScope) { mutableStateOf(if (preparationScope.isBlank()) emptyList<DoctorPreparationQuestion>() else runCatching { preparationStore.load(preparationScope) }.getOrDefault(emptyList())) }
    // Bumped when a goal answer is saved from the transcript page; the question and answer cards reload so they never save an older list over it.
    var prepReload by remember(preparationScope) { mutableIntStateOf(0) }
    val questionMatches = remember(draft.source, prepRows, step == 2 || step == 3) { if ((step == 2 || step == 3) && prepRows.isNotEmpty()) LocalQuestionMatcher.match(draft.source, prepRows.map { CareQuestion(it.id, it.text, it.section) }) else null }
    val previousVersion = versions.firstOrNull { it.personId == draft.personId }
    val useLine: (Int, CareSourceLine) -> Unit = { section, line ->
        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId) { "Choose this person's conversation again." }; if (doctorPresentation) assignDoctorSource(vm, draft, section, line) else vm.edit(CareDraftAssist.assign(current, section, line)) }
        catch (e: IllegalArgumentException) { vm.message.value = e.message }
    }
    val addTypedAnswers = {
        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId) { "Choose this person's conversation again." }
            var next = current
            doctorAnsweredQuestions(prepRows).forEach { q -> val existing = NoteRules.values(next)[q.section]; if (!existing.contains(q.answer)) next = CareDraftAssist.withSection(next, q.section, (if (existing.isBlank()) "${q.text} ${q.answer}" else existing + "\n" + "${q.text} ${q.answer}").take(4000)) }
            if (next != current) vm.edit(next) }
        catch (e: IllegalArgumentException) { vm.message.value = e.message }
    }
    val startConsentedRecording = {
        if (vm.canEdit.value && vm.localAudioRecordingAllowed && !vm.recordingSaving.value && vm.recordingSaveError.value == null) vm.startRecording(consentNames.joinToString("\n"), consentNames.toSet(), purposeAccepted, storageAccepted, accessAccepted, transcriptionAccepted, expected = recordingExpected)
        else vm.message.value = "Recording is unavailable. Check editing access or finish saving the retained audio."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startConsentedRecording() else vm.message.value = "Microphone permission wasn't granted. You can still type the conversation." }
    LaunchedEffect(draft.source, draft.priorities, draft.participants, draft.topics, draft.questions, draft.nextSteps) { reviewed = false }
    val captureCallback by rememberUpdatedState(onDoctorCaptureChange)
    val capturing = doctorPresentation && clinician && step == 1
    DisposableEffect(capturing) { captureCallback(capturing); onDispose { captureCallback(false) } }
    // Record mode of the doctor walkthrough: terracotta stage + persistent "Saved Audio" peek sheet instead of the list.
    // Oldest first, so "Audio 1" keeps its name when more recordings are added in the same sitting.
    val audioRows = remember(recordings) { recordings.map { SavedAudioRow(it.absolutePath, it.absolutePath, it.lastModified()) }.sortedBy { it.recordedAt } }
    val playAudio: (SavedAudioRow) -> Unit = { row ->
        runCatching { audioPlayer.reset(); audioPlayer.setDataSource(row.path); audioPlayer.prepare(); audioPlayer.start() }
            .onSuccess { playingPath = row.id; playingNow = true }
            .onFailure { playingNow = false; vm.message.value = "That recording could not be played." }
    }
    BackHandler(doctorPresentation && clinician && step > 0 && !recording && !dictating && !recordingSaving && recordingSaveError == null && !permissionDialog && !audienceBusy) { previousPage() }
    // Pages move with the app's shared motion in the direction of travel; each page keeps its own list position while the other fades out.
    AnimatedContent(step, transitionSpec = { SaharaMotion.transform(if (targetState >= initialState) SaharaMotion.Direction.Forward else SaharaMotion.Direction.Back) }, label = "conversation-page") { step ->
    val doctorCapture = doctorPresentation && clinician && step == 1
    val scroll = rememberLazyListState()
    LaunchedEffect(buildSection) { scroll.scrollToItem(0) }
    fun androidx.compose.foundation.lazy.LazyListScope.captureItem(content: @Composable () -> Unit) {
        item { Box(Modifier.fillMaxWidth().padding(horizontal = if (doctorCapture) 20.dp else 0.dp)) { content() } }
    }
    if (doctorCapture && captureMode == "Record") DoctorRecordStage(mode = captureMode, onMode = { captureMode = it }, recording = recording, saving = recordingSaving, saveError = recordingSaveError != null, onRetry = vm::retrySavingRecording, level = level, seconds = seconds.toLong(),
        recordEnabled = recordingCanEdit && recordingSaveError == null && vm.localAudioRecordingAllowed && !recordingSaving && draft.personId.isNotBlank() && !dictating && (!busy || recording),
        onRecord = { recordingExpected = doctorConversationIdentity(vm.accountNamespace, draft); purposeAccepted = false; storageAccepted = false; accessAccepted = false; transcriptionAccepted = false; permissionDialog = true },
        onStop = { vm.stopRecording() }, onBack = onDoctorBack, backEnabled = !recording && !dictating && !permissionDialog && !audienceBusy && !recordingSaving && recordingSaveError == null,
        demo = vm.demoMode, localRecordingAllowed = vm.localAudioRecordingAllowed && recordingCanEdit && !recordingSaving && recordingSaveError == null, testRecord = vm.demoMode && DoctorTestProfiles.contains(draft.personId),
        menuEnabled = !recording && !dictating && !permissionDialog && !audienceBusy,
        onStartNew = if (draft.source.isNotBlank() && onDoctorStartNew != null) ({ if (!recordingSaving && recordingSaveError == null && !busy && !recording && !dictating && !permissionDialog && !audienceBusy) onDoctorStartNew() }) else null,
        onBackToWho = { nextStep(0) }, backToWhoEnabled = !recordingSaving && recordingSaveError == null && !recording && !dictating, audioCount = if (recordingSaving || recordingSaveError != null) 0 else recordings.size) {
        DoctorSavedAudioPage(rows = audioRows, selected = savedAudioPicked, onSelectedChange = { savedAudioPicked = it }, nowPlaying = playingPath, isPlaying = playingNow, onPlay = playAudio,
            onPause = { runCatching { audioPlayer.pause() }; playingNow = false },
            onSeek = { delta -> runCatching { audioPlayer.seekTo((audioPlayer.currentPosition + delta).coerceAtLeast(0L).toInt()) } },
            onContinue = {
                runCatching { audioPlayer.reset() }; playingPath = null; playingNow = false
                // Only the ticked recordings are tied to this conversation; the rest stay on the device untouched.
                savedAudioPicked.forEach { path -> runCatching { vm.attachDoctorRecording(path, doctorConversationIdentity(vm.accountNamespace, draft)) } }
                nextStep(2)
            })
    } else LazyColumn(Modifier.fillMaxSize().background(if (doctorPresentation) Color.White else MaterialTheme.colorScheme.background).imePadding().testTag("conversation-list"), state = scroll, contentPadding = if (doctorCapture) PaddingValues(bottom = 20.dp) else PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!doctorCapture) item { Heading(if (clinician) "Conversation" else "Care notes") }
        if (doctorPresentation && !doctorCapture && onDoctorBack != null) item { TextButton(onClick = onDoctorBack, enabled = !recordingSaving && recordingSaveError == null && !recording && !dictating && !audienceBusy) { Text("Back to conversations") } }
        if (clinician) {
            if (!doctorCapture) item { Column { ConversationStepper(topStep); val subLabel = ConversationPages.subLabel(topStep, subPage); if (subLabel.isNotBlank()) Text(subLabel, fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).testTag("conversation-substep")) } }
            when (step) {
                0 -> {
                    item { Text("Write down everyone in the room, then the questions this conversation should cover. Each person must agree before anything is recorded.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) }
                    if (attendeeScope.isBlank()) item { Text("Choose a person first to list who is here.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub) }
                    else item {
                        val doctorLabel = listOf(session?.email?.substringBefore('@')?.trim().orEmpty(), if (doctorPresentation) DoctorDemoData.doctorName else "", doctor.trim()).firstOrNull { it.isNotBlank() && !it.equals("Doctor", true) } ?: "Clinician"
                        val patientLabel = peopleList.firstOrNull { it.id == draft.personId }?.name?.trim().orEmpty().ifBlank { "Patient" }
                        DoctorAttendeesList(attendeeStore, attendeeScope, remember(attendeeScope, doctorLabel, patientLabel) { doctorAttendeeStartingList(doctorLabel, patientLabel) }, !busy && !recording && !dictating && !recordingSaving && recordingSaveError == null && canEditCareContext(vm, draft.personId, vm.accountNamespace)) { rows ->
                            attendees = rows; attendeeKept.value = rows
                            val who = doctorAttendeesWhoWasThere(rows); val current = vm.draft.value
                            if (rows.isNotEmpty() && current.personId == draft.personId && current.participants != who) try { vm.edit(CareDraftAssist.withSection(current, 1, who)) } catch (_: Exception) { }
                        }
                    }
                    if (preparationScope.isNotBlank()) item { DoctorPreparationQuestions(preparationStore, preparationScope, !busy && !recording && !dictating && !recordingSaving && recordingSaveError == null, canSave = { !vm.recordingSaving.value && vm.recordingSaveError.value == null && canEditCareContext(vm, draft.personId, preparationAccount) && vm.draft.value.conversationId == draft.conversationId }, onInteraction = { preparationBusy = it }, initial = remember(preparationScope, previousVersion?.id) { doctorSuggestQuestions(previousVersion?.let { DoctorPreviousNote(it.priorities, it.participants, it.topics, it.questions, it.nextSteps) }, "") }, onChange = { prepRows = it }, reload = prepReload) }
                    else item { Text("Choose a person first to prepare questions.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub) }
                    item { Button(onClick = { nextStep(1) }, enabled = attendeesConsented && !recordingSaving && recordingSaveError == null && !preparationBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue to recording"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                    if (!attendeesConsented) item { Text(if (attendees.isEmpty()) "Add at least one person before continuing." else "Continue unlocks once everyone listed has agreed to being recorded.", fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint, modifier = Modifier.testTag("attendees-continue-reason")) }
                }
                1 -> {
                    if (doctorPresentation) item { DoctorCaptureHeader(captureMode, { captureMode = it }, recording, dictating, level, seconds.toLong(), vm.demoMode, onDoctorBack, !recording && !dictating && !permissionDialog && !audienceBusy && !recordingSaving && recordingSaveError == null, localRecordingAllowed = vm.localAudioRecordingAllowed && recordingCanEdit && !recordingSaving && recordingSaveError == null, testRecord = vm.demoMode && DoctorTestProfiles.contains(draft.personId)) }
                    if (vm.demoMode && !doctorPresentation) captureItem { DoctorFootnote(if (vm.localAudioRecordingAllowed) "Audio stays on this device and is not included when a written note is shared. It is not automatically transcribed. Dictation, online transcription and model downloads are unavailable." else "Type and edit below; dictation, recording, transcription and model downloads are unavailable.") }
                    if (recordingSaving || recordingSaveError != null) captureItem { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if(recordingSaving) "Saving local audio and its consent record…" else "The audio file was retained, but its recording details could not be saved. Retry before continuing.")
                        if (recordingSaveError != null) OutlinedButton(onClick = vm::retrySavingRecording, enabled = !recordingSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("retry-saving-audio")) { Text("Retry saving audio") }
                    } }
                    if (!vm.demoMode && (!doctorPresentation || captureMode == "Dictate")) captureItem { CareDictationPanel(draft.personId, recording, enabled = !busy && draft.personId.isNotBlank(),
                        onWords = { words, speaker, language ->
                            val current = vm.draft.value
                            if (current.personId == draft.personId && vm.selected.value == draft.personId && vm.canEdit.value) try {
                                val source = CareDraftAssist.appendTranscript(current.source, words, speaker, language, System.currentTimeMillis())
                                vm.edit(current.copy(source = source))
                                vm.draft.value.personId == current.personId && vm.draft.value.source == source
                            } catch (e: IllegalArgumentException) { vm.message.value = e.message; false } else false
                        },
                        onConsent = { val current = vm.draft.value; if (current.personId == draft.personId) vm.edit(current.copy(consentAt = System.currentTimeMillis())) },
                        onActiveChanged = { dictating = it }) }
                    if (!doctorPresentation || captureMode == "Record" || recording) captureItem { Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Conversation audio", fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        if (recording) {
                            val meter by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "microphone level")
                            Text("Recording · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                            LinearProgressIndicator(progress = { meter }, modifier = Modifier.fillMaxWidth(), color = DoctorAccent)
                        } else Text(if (recordingSaving) "Saving…" else "Ready to record", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                        Button(onClick = { if (recording) vm.stopRecording() else {
                            recordingExpected = if (doctorPresentation) doctorConversationIdentity(vm.accountNamespace,draft) else null
                            purposeAccepted = false; storageAccepted = false; accessAccepted = false; transcriptionAccepted = false
                            permissionDialog = true
                        } }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = recordingCanEdit && recordingSaveError == null && vm.localAudioRecordingAllowed && !recordingSaving && draft.personId.isNotBlank() && !dictating && (!busy || recording)) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (recordingSaving) "Saving local recording…" else if (!vm.localAudioRecordingAllowed) "Recording unavailable" else if (recording) "Stop and save" else "Record conversation") }
                    } } }
                    items(if(recordingSaving || recordingSaveError != null || doctorPresentation) emptyList() else recordings, key = { it.absolutePath }) { file ->
                        Column(Modifier.padding(horizontal = if (doctorCapture) 20.dp else 0.dp)) {
                        val consent = recordingConsents.firstOrNull { it.audioPath == file.absolutePath }
                        AudioPlayback(
                            path = file.absolutePath,
                            consent = consent,
                            transcribing = transcribingPath == file.absolutePath,
                            canTranscribe = !vm.demoMode && BuildConfig.CLOUD_AUDIO_ENABLED && consent?.let { it.transcriptionConsentAt > 0 && it.noticeVersion >= 2 } == true && draft.source.isBlank() && !busy && !dictating && !recording,
                            onTranscribe = { consent?.let { vm.transcribeAudio(file.absolutePath, it) } },
                            onDelete = { vm.deleteAudio(file.absolutePath) },
                        )
                        if(doctorPresentation) OutlinedButton(onClick={vm.attachDoctorRecording(file.absolutePath,doctorConversationIdentity(vm.accountNamespace,draft))},enabled=!busy&&!recording&&!dictating,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Link this recording to this conversation")}
                        if (!vm.demoMode) OfflineConversationTranscription(file = file, identity = "${vm.accountNamespace}:${draft.personId}", consent = consent,
                            enabled = !busy && !dictating && !recording && draft.source.isBlank(),
                            onUse = { vm.useOfflineTranscript(file.absolutePath, it) })
                        if (!vm.demoMode && vm.cloud != null && BuildConfig.CLOUD_AUDIO_ENABLED) CareRemoteTranscriptPanel(
                            source = draft.source, identity = draft.personId, recording = file, recordingConsent = consent,
                            token = { vm.cloud.transcriptionToken() }, onSaveConsent = vm::rememberTranscriptionConsent,
                            onAppend = { source ->
                                val current = vm.draft.value
                                if (current.personId == draft.personId && vm.selected.value == draft.personId && current.source == draft.source && source.length <= NoteRules.MAX_SOURCE_CHARS) vm.edit(current.copy(source = source))
                                else vm.message.value = "The conversation changed. Check the retained recording and try again."
                            },
                        )
                        }
                    }
                    captureItem { Button(onClick = { nextStep(2) }, enabled = !recordingSaving && recordingSaveError == null && !recording && !dictating, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue to transcript"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                    if (doctorPresentation && draft.source.isNotBlank() && onDoctorStartNew != null) captureItem { TextButton(onClick = onDoctorStartNew, enabled = !recordingSaving && recordingSaveError == null && !busy && !recording && !dictating && !permissionDialog && !audienceBusy) { Text("Start new · archive this draft") } }
                
                    captureItem { TextButton(onClick = { nextStep(0) }, enabled = !recordingSaving && recordingSaveError == null && !recording && !dictating, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to who is here") } }
                }
                2 -> {
                    captureItem { if (dictating) SurfaceCard { Text("Conversation", fontWeight = FontWeight.Bold); SelectionContainerText(draft.source); Text("Finish this turn to edit the conversation.", style = MaterialTheme.typography.bodySmall) }
                        else CareField(value = draft.source, onValueChange = { if (it.length <= NoteRules.MAX_SOURCE_CHARS) { val current = vm.draft.value; if (current.personId == draft.personId) vm.edit(current.copy(source = it)) } }, label = "Conversation transcript", placeholder = "Speak a turn, transcribe a saved recording, or type in Hindi or English…", minLines = 5, supporting = if (draft.transcribedAt > 0) "Transcript · ${draft.transcriptModel}. Check names, numbers and meaning against the audio." else "") }
                    item { DoctorTranscriptGoals(prepRows, questionMatches, !busy && !recording && !dictating && !recordingSaving && recordingSaveError == null, onSave = { id, answer -> if (preparationScope.isNotBlank() && !vm.recordingSaving.value && vm.recordingSaveError.value == null && canEditCareContext(vm, draft.personId, preparationAccount) && vm.draft.value.conversationId == draft.conversationId) runCatching { val next = prepRows.map { if (it.id == id) it.copy(answer = answer.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS)) else it }; preparationStore.save(preparationScope, next); prepRows = next; prepReload++ } }, onBackToWho = { nextStep(0) }) }
                    captureItem { Button(onClick = { if (preparationScope.isNotBlank() && !vm.recordingSaving.value && vm.recordingSaveError.value == null && canEditCareContext(vm, draft.personId, preparationAccount) && vm.draft.value.conversationId == draft.conversationId) { val filled = transcriptGoalsFill(prepRows, questionMatches); if (filled != prepRows) runCatching { preparationStore.save(preparationScope, filled); prepRows = filled } }; buildSection = 0; buildOpen = false; nextStep(3) }, enabled = !recordingSaving && recordingSaveError == null && draft.source.isNotBlank() && !recording && !dictating, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue to answers"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                    item { TextButton(onClick = { nextStep(1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to recording") } }
                }
                3 -> {
                    if (!buildOpen) {
                    item { Text("Your questions are matched to lines in the conversation. A matched line is only a suggestion: read it and decide. Anything you type is your own note.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) }
                    if (questionMatches != null) item { Text(if (questionMatches.unanswered.isEmpty()) "Every question has at least one suggested line." else "${questionMatches.unanswered.size} of ${prepRows.size} questions have no suggested line.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub, modifier = Modifier.testTag("answers-match-status")) }
                    questionMatches?.let { result -> items(result.answered, key = { "match-${it.question.id}" }) { qa -> Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(qa.question.text, fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        Text("Suggested lines · check each against the conversation", fontSize = 12.sp, color = DoctorCardFaint)
                        qa.matches.forEach { m -> Text(m.quote, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk); TextButton(onClick = { useLine(qa.question.section, m.line) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use this line in ${NoteRules.labels[qa.question.section]}") } }
                    } } } }
                    if (preparationScope.isNotBlank()) item { DoctorPreparationAnswers(preparationStore, preparationScope, !busy && !recording && !dictating && !recordingSaving && recordingSaveError == null, canSave = { !vm.recordingSaving.value && vm.recordingSaveError.value == null && canEditCareContext(vm, draft.personId, preparationAccount) && vm.draft.value.conversationId == draft.conversationId }, onChange = { prepRows = it }, reload = prepReload) }
                    item { OutlinedButton(onClick = addTypedAnswers, enabled = !busy && doctorAnsweredQuestions(prepRows).isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add my typed answers to the note sections") } }
                    item { OutlinedButton(onClick = { buildSection = 0; buildOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Edit the five note sections") } }
                    item { Button(onClick = { nextStep(4) }, enabled = draft.source.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue to review"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                    item { TextButton(onClick = { nextStep(2) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to transcript") } }
                    } else {
                    item { SurfaceCard(Peach) {
                        Text(if (draft.transcribedAt > 0) "AI transcript draft · not clinically reviewed" else "Structured draft · not clinically reviewed", fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp)); Text(if (draft.transcribedAt > 0) "The recording was transcribed by ${draft.transcriptModel}. Speaker labels and words can be wrong; check them against the audio before publishing." else "Choose original words for each section, or organise explicit section headings. This review is not automatic audio transcription.", style = MaterialTheme.typography.bodySmall)
                    } }
                    item { Text("Check every word against the conversation. Leave anything not discussed blank.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { CareSourcePicker(draft, buildSection) { section, line ->
                        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId) { "Choose this person's conversation again." }; if (doctorPresentation) assignDoctorSource(vm, draft, section, line) else vm.edit(CareDraftAssist.assign(current, section, line)) }
                        catch (e: IllegalArgumentException) { vm.message.value = e.message }
                    } }
                    if (!vm.demoMode && vm.cloud != null) item { CareRemoteAssistPanel(source = draft.source, identity = draft.personId, token = { vm.cloud.transcriptionToken() }, onUseDraft = { assisted ->
                        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId); vm.edit(CareDraftAssist.applyAssisted(current, assisted)) }
                        catch (e: IllegalArgumentException) { vm.message.value = e.message }
                    }) }
                    if (NoteRules.values(draft).all { it.isBlank() }) item { TextButton(onClick = vm::organise) { Text("Organise using section headings") } }
                    item { val update: (String) -> Unit = { value -> if (value.length <= 4000) { val current = vm.draft.value; if (current.personId == draft.personId) vm.edit(CareDraftAssist.withSection(current, buildSection, value)) } }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (doctorPresentation && buildSection == 0) DoctorGoalsReview(draft) { goal -> updateDoctorGoalFromUi(vm,draft,goal) }
                            CareField(value = NoteRules.values(draft)[buildSection], onValueChange = update, label = NoteRules.labels[buildSection], minLines = 2)
                            if (!doctorPresentation) CareSourceLinks(draft.source, NoteRules.values(draft)[buildSection])
                        } }
                    item { CareSectionNav(buildSection, NoteRules.labels.size, { focus.clearFocus(); if (buildSection == 0) buildOpen = false else buildSection -= 1 }, { focus.clearFocus(); if (buildSection >= NoteRules.labels.lastIndex) { buildSection = 0; buildOpen = false; nextStep(4) } else buildSection += 1 }) }
                
                    }
                }
                4 -> {
                    item { Text("Read the whole note before you publish. Nothing here can be changed on this screen.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) }
                    items(NoteRules.labels.withIndex().toList(), key = { "preview-${it.index}" }) { (index, label) -> Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        Text(NoteRules.values(draft)[index].ifBlank { "Not recorded" }, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                        TextButton(onClick = { buildSection = index; buildOpen = true; nextStep(3) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit $label") }
                    } } }
                    item { InfoDisclosure("Exact source text", Paper) { SelectionContainerText(draft.source) } }
                    item { Button(onClick = { nextStep(5) }, enabled = draft.source.isNotBlank() && NoteRules.values(draft).any { it.isNotBlank() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Continue to publish") } }
                    item { OutlinedButton(onClick = { buildSection = 0; buildOpen = true; nextStep(3) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Back to build the note") } }
                }
                else -> {
                    if (doctorPresentation) {
                                                item { InfoDisclosure("Check the exact conversation", Peach) { Text("Audio stays on this device. Only a recording explicitly linked above can be played with this version on the capture device.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk); Text("Conversation-only family access requires the updated backend and owner approval. There is no broad-sharing fallback.", fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub) } }
                        item { DoctorRecorderLabel(vm,draft) }
                        item { DoctorConversationAudience(vm,draft) {audienceBusy=it} }
                    }
                    item { InfoDisclosure("Clinical review", Gold) { Text("Publishing creates an exact version. Earlier versions stay available.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) } }
                    if (vm.cloud == null) item { CareField(value = doctor, onValueChange = { doctor = it.take(100) }, label = "Doctor's name", singleLine = true) }
                    item { CheckLine(reviewed, { reviewed = it }, "I checked every section against the conversation. This records what was discussed, without adding treatment advice.") }
                                        item { InfoDisclosure(if (vm.cloud == null) "Identity not verified" else "Separate clinician review", Peach) { Text(if (vm.cloud == null) "This records a self-entered reviewer name. It does not claim a verified clinical review." else "Your account draft is sent to the shared workspace. Only an independently verified clinician invited by this patient can publish a reviewed care-note version.", fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk) } }
                    item { TextButton(onClick = { nextStep(4) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to read the note") } }
                    item { Button(onClick = { vm.publish(doctor, reviewed) { buildOpen = false; nextStep(0); reviewed = false } }, enabled = !recordingSaving && recordingSaveError == null && (!doctorPresentation || vm.cloud == null) && reviewed && (vm.cloud != null || doctor.isNotBlank()) && !busy && draft.source.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (doctorPresentation && vm.cloud != null) "Conversation-specific sharing not connected yet" else if (vm.cloud == null) "Publish reviewed version" else "Send for clinician review") } }
                }
            }
        } else {
            item { SurfaceCard(Gold) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Forum, null, Modifier.size(28.dp)); Spacer(Modifier.width(14.dp)); Text("Read together. Ask your doctor if anything needs changing.", modifier = Modifier.weight(1f)) } } }
            item { OutlinedButton(onClick = { onRequestConversation?.invoke() ?: vm.saveItem("task", "Ask for a goals-of-care conversation", "Discuss what matters and questions for the care team.") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(if (onRequestConversation == null) "Add a reminder to talk" else "Ask for a conversation") } }
        }
        if (!doctorCapture && !clinician) {
        if (clinician) item { SectionTitle("Saved notes") }
        if (versions.isEmpty()) item { Text("Your first care note will appear here after a doctor reviews and saves it.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(versions, key = { it.id }) { note -> Card(onClick = { onRead(note) }, colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Version ${note.version}" + if (note.id == versions.first().id) " · Latest" else " · Earlier", fontWeight = FontWeight.Bold); Text("${date(note.published)} · ${note.doctor}", style = MaterialTheme.typography.bodySmall); Text(ReviewWorkflow.label(note), style = MaterialTheme.typography.labelSmall, color = Rust) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, "Read note") }
        } }
        }
    }
    }
    val quickConsent = doctorPresentation && (vm.demoMode || !BuildConfig.CLOUD_AUDIO_ENABLED)
    if (permissionDialog && vm.localAudioRecordingAllowed) AlertDialog(onDismissRequest = { permissionDialog = false }, title = { Text("Before we record") }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (consentNames.isEmpty()) "Go back to “Who's here” and name everyone present before recording." else "Everyone named in “Who's here” has agreed: ${consentNames.joinToString(", ")}.", style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            if (quickConsent) Text("By starting, everyone listed agrees that: this recording is for preparing and checking a care-conversation note; it stays privately on this device; and people with access to this phone or shared records may be able to read the written note.", style = MaterialTheme.typography.bodySmall) else CheckLine(purposeAccepted, { purposeAccepted = it }, "We understand this recording is for preparing and checking a care-conversation note.")
            if (!quickConsent) CheckLine(storageAccepted, { storageAccepted = it }, if (!vm.demoMode && BuildConfig.CLOUD_AUDIO_ENABLED) "We understand the audio stays privately on this device unless someone shares it or separately agrees to cloud transcription below." else "We understand the recording stays privately on this device.")
            if (!quickConsent) CheckLine(accessAccepted, { accessAccepted = it }, "We understand people with access to this person's phone or shared records may be able to read the written note.")
            if (!vm.demoMode && BuildConfig.CLOUD_AUDIO_ENABLED) {
            HorizontalDivider()
            Text("Optional free-demo transcription", fontWeight = FontWeight.Bold)
            CheckLine(transcriptionAccepted, { transcriptionAccepted = it }, "Everyone agrees to upload this fictional demo recording to Supabase and Google Gemini. It contains no real patient information, personal details or confidential information.")
            Text("Use test conversations only. Google may use free-tier audio and transcripts to improve its products, including human review. Deleting the temporary upload does not undo that processing. Limit: five minutes / 3 MB, up to five attempts per account per day. Check the returned words against the audio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }, confirmButton = {
        TextButton(onClick = { if (vm.canEdit.value && vm.localAudioRecordingAllowed && !vm.recordingSaving.value && vm.recordingSaveError.value == null) { if (quickConsent) { purposeAccepted = true; storageAccepted = true; accessAccepted = true }; permissionDialog = false; if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startConsentedRecording() else permission.launch(Manifest.permission.RECORD_AUDIO) } }, enabled = recordingCanEdit && recordingSaveError == null && !recordingSaving && vm.localAudioRecordingAllowed && allParticipantsAgreed && (quickConsent || (purposeAccepted && storageAccepted && accessAccepted)), modifier = Modifier.testTag("start-recording-confirm")) { Text(if (quickConsent) "Everyone listed agreed · Start recording" else "Start recording") }
    }, dismissButton = { TextButton(onClick = { permissionDialog = false }) { Text("Cancel") } })
}

@Composable fun AudioPlayback(path: String, consent: RecordingConsent?, transcribing: Boolean, canTranscribe: Boolean, onTranscribe: () -> Unit, onDelete: () -> Unit) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }; var playing by remember(path) { mutableStateOf(false) }
    var confirmDelete by remember(path) { mutableStateOf(false) }
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(path, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) { player?.release(); player = null; playing = false } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player?.release(); player = null; playing = false }
    }
    SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (playing) { player?.stop(); player?.release(); player = null; playing = false }
            else try {
                val audio = MediaPlayer.create(context, Uri.fromFile(File(path))) ?: error("Recording unavailable")
                player = audio; audio.setOnCompletionListener { playing = false; it.release(); player = null }; audio.start(); playing = true
            } catch (_: Exception) { player?.release(); player = null; playing = false; android.widget.Toast.makeText(context, "Couldn't play this recording.", android.widget.Toast.LENGTH_LONG).show() }
        }) { Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) "Stop playback" else "Play recording") }
        Column(Modifier.weight(1f)) {
            Text("Conversation · " + Instant.ofEpochMilli(File(path).lastModified()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")), fontSize = 14.sp)
            Text(if (consent == null) "Consent details unavailable" else "Consent recorded · ${RecordingConsentRules.names(consent.participants).joinToString()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (BuildConfig.CLOUD_AUDIO_ENABLED && consent != null) Text(when {
                consent.transcriptionConsentAt <= 0 -> "Cloud transcription not agreed"
                consent.noticeVersion < 2 -> "Older consent: record a new fictional demo"
                else -> "Free-demo transcription agreed"
            }, style = MaterialTheme.typography.labelSmall, color = if (consent.transcriptionConsentAt > 0 && consent.noticeVersion >= 2) MaterialTheme.colorScheme.secondary else Rust)
        }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, "Delete recording") }
    }
        if (BuildConfig.CLOUD_AUDIO_ENABLED && (consent?.transcriptionConsentAt ?: 0L) > 0L) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onTranscribe, enabled = canTranscribe && !transcribing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                if (transcribing) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
                else { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                Text(if (transcribing) "Transcribing…" else "Create transcript")
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this recording?") }, text = { Text("Your written care notes stay available.") },
        confirmButton = { TextButton(onClick = { player?.release(); player = null; playing = false; confirmDelete = false; onDelete() }) { Text("Delete recording") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep recording") } })
}

@Composable fun NoteReader(vm: CareModel, note: CareVersion, onDismiss: () -> Unit) {
    val acks by vm.acknowledgements.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle(); val busy by vm.busy.collectAsStateWithLifecycle()
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val authenticatedAcks by vm.authenticatedConfirmationIds.collectAsStateWithLifecycle()
    val verifiedReviews by vm.verifiedReviewIds.collectAsStateWithLifecycle()
    val confirmations = acks.filter { it.versionId == note.id && it.contentHash == note.contentHash }
        .sortedWith(compareBy<Acknowledgement> { it.relationship != "Patient" }.thenBy { it.at })
    val patientConfirmation = confirmations.firstOrNull { it.relationship == "Patient" && (vm.cloud == null || it.id in authenticatedAcks) }
    var name by rememberSaveable(note.id) { mutableStateOf("") }; var relationship by rememberSaveable(note.id) { mutableStateOf("Patient") }; var reviewed by rememberSaveable(note.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by rememberSaveable { mutableStateOf<String?>(null) }
    val familyOnly = vm.cloud == null && vm.walkthroughRole == "Family"
    LaunchedEffect(patientConfirmation?.id, owner) { relationship = if (familyOnly) "Family / carer" else if (vm.cloud != null) if (owner) "Patient" else "Family / carer" else if (patientConfirmation == null) "Patient" else "Family / carer"; reviewed = false }
    val saveNote = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = exporting; exporting = null
        if (uri != null && text != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).bufferedWriter().use { it.write(text) } }
                vm.message.value = "Care note saved"
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = "Couldn't save the copy. Your care note is still here. Try again." }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Heading(if (note.hashScheme == DoctorConversationContext.HASH_SCHEME) "Transcription notes" else "Goals of care"); Text("${note.personName} · Version ${note.version}"); Text("Reviewer: ${note.doctor} · ${date(note.published)}", style = MaterialTheme.typography.bodySmall); Text(ReviewWorkflow.label(note, note.id in verifiedReviews), style = MaterialTheme.typography.labelMedium, color = Rust) }
            if(note.hashScheme==DoctorConversationContext.HASH_SCHEME) item { DoctorNoteReaderBody(vm, note) }
            if (vm.cloud != null) item { OutlinedButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sharedCareNoteUrl(note)), "Share exact care-note link")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text("Share exact-version link") }; Text("The recipient must sign in with a permitted account. This link grants no public access.", style = MaterialTheme.typography.bodySmall) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { exporting = NoteRules.text(note, confirmations); saveNote.launch("Goals-of-care-v${note.version}.txt") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save copy") }
                FilledTonalButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, NoteRules.text(note, confirmations)), "Share care note")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Gold, contentColor = Ink)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share") }
            } }
            item { CareReadAloudControl(NoteRules.labels.zip(NoteRules.values(note)).filter { it.second.isNotBlank() }.joinToString("\n\n") { "${it.first}. ${it.second}" }, note.id) }
            if (note.hashScheme != DoctorConversationContext.HASH_SCHEME) items(NoteRules.labels.zip(NoteRules.values(note)), key = { it.first }) { (label, value) -> SurfaceCard(if (label == "What matters") Gold else Paper) { Text(label, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); SelectionContainerText(value.ifBlank { "Not recorded" }); CareSourceLinks(note.source, value) } }
            if (note.hashScheme != DoctorConversationContext.HASH_SCHEME) item { SurfaceCard { Text("Original conversation", fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); SelectionContainerText(note.source) } }
            if (!vm.demoMode) item { CareOfflineTranslationPanel(source = NoteRules.labels.zip(NoteRules.values(note)).filter { it.second.isNotBlank() }.joinToString("\n\n") { "${it.first}\n${it.second}" }, versionId = note.id) }
            items(confirmations, key = { it.id }) { confirmation -> SurfaceCard(if (confirmation.relationship == "Patient") Sage else Mint) {
                Text(if (confirmation.relationship == "Patient") "Patient confirmed this version" else "Family / carer confirmation", fontWeight = FontWeight.Bold)
                Text("${confirmation.name} · ${date(confirmation.at)}")
            } }
            if (versions.firstOrNull()?.id == note.id) {
                item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); Heading(if (patientConfirmation == null) "Patient's review" else "Family's review", 26)
                    Text(if (patientConfirmation == null) "The patient confirms first. Family can then add their own confirmation to this same version." else "The patient's confirmation stays saved. Add your own after reading this version.", style = MaterialTheme.typography.bodySmall) }
                item { CareField(value = name, onValueChange = { name = it.take(100) }, label = "Your name", singleLine = true) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Patient", "Family / carer").forEach { role -> FilterChip(selected = relationship == role, onClick = { relationship = role }, enabled = (vm.cloud == null || (role == "Patient") == owner) && !(familyOnly && role == "Patient") && if (role == "Patient") patientConfirmation == null else patientConfirmation != null, label = { Text(role) }) } } }
                item { CheckLine(reviewed, { reviewed = it }, "I have read this exact version. This acknowledgement is not a legal signature, medical consent or treatment decision. I can ask my doctor to update it later.") }
                val alreadyConfirmed = confirmations.any { it.relationship == relationship && it.name.equals(name.trim(), ignoreCase = true) && (vm.cloud == null || it.id in authenticatedAcks) }
                item { Button(onClick = { vm.confirm(note, name, relationship, reviewed) { reviewed = false; name = "" } }, enabled = reviewed && name.isNotBlank() && !busy && !alreadyConfirmed && (relationship == "Patient" || patientConfirmation != null), modifier = Modifier.fillMaxWidth()) { Text(if (alreadyConfirmed) "Confirmation already saved" else "Confirm this version") } }
            }
            item { Text("This records a conversation. It is not a treatment order or legal directive. Names are self-entered unless this version explicitly shows verified clinician review.", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable fun CareList(vm: CareModel, onAdd: (String) -> Unit, onScanPrescription: () -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, initialFilter: String = "All", onReadNote: (CareVersion) -> Unit = {}, onSymptoms: () -> Unit = {}) {
    val entries by vm.items.collectAsStateWithLifecycle(); val everything by vm.allItems.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(); val target = CareModules.moduleFor(initialFilter)
    val shown = entries.filter { !CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it) }
    val (currentMeds, pastMeds) = CareModules.splitMedicines(everything.filter { !CareEntryCodec.isStructured(it.detail) }, shown)
    val now = remember(entries) { System.currentTimeMillis() }; val (upcoming, pastVisits) = CareModules.splitVisits(shown.filter { it.kind == "visit" }, now)
    val tasks = shown.filter { it.kind == "task" }; val checkins = shown.filter { it.kind == "checkin" }; val journal = shown.filter { it.kind == "journal" }
    val empty = shown.isEmpty() && pastMeds.isEmpty() && versions.isEmpty()
    val selectedPerson by vm.selected.collectAsStateWithLifecycle(); val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    LaunchedEffect(target) { val index = CareModules.order.indexOf(target); if (index >= 0) listState.animateScrollToItem(3 + index) }
    @Composable fun module(name: String, icon: ImageVector, tint: Color, count: Int, content: @Composable ColumnScope.() -> Unit) = CareModuleCard(name.lowercase().replace(' ', '-'), name, icon, tint, count, defaultOpen = name in CareModules.order.take(2), forceOpen = name == target, content = content)
    @Composable fun visit(item: CareItem) = if (isDoctorStructuredVisit(item)) DoctorVisitSummary(item, onOpen = onCalendar) else CareRow(item, vm)
    LazyColumn(Modifier.fillMaxSize().testTag("care-list"), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Heading("Daily care", 30); Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontSize = 13.sp, color = DoctorCardSub) }
            CareAddMenuButton(onAdd, onScanPrescription)
        } }
        item { FilledTonalButton(onClick = onSymptoms, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("daily-symptom-tracker-entry"), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Mint)) { Icon(Icons.Default.MonitorHeart, null); Spacer(Modifier.width(8.dp)); Text("Symptom tracker · तबीयत के नोट") } }
        item { DailyCareShortcuts(onCalendar, onWorkflow, onLabs) }
        if (loadedFor != selectedPerson) Unit else if (empty) item { SurfaceCard(Paper) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(shape = CircleShape, color = SaharaApricot) { Icon(careIcon("task"), null, Modifier.padding(14.dp).size(30.dp), tint = DoctorAccent) }
                Spacer(Modifier.height(12.dp)); Text("Nothing added yet", fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                Spacer(Modifier.height(4.dp)); Text("Use Add to record a medicine, visit, task, check-in or note.", fontSize = 15.sp, color = DoctorCardSub, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp)); CareAddMenuButton(onAdd, onScanPrescription)
            }
        } } else {
            item { module("Conversations", Icons.Default.Description, SaharaLavender, versions.size) { CareModuleList("conversations", versions, "No saved conversations yet.", keyOf = { it.id }) { ConversationRow(it, onReadNote) } } }
            item { module("Medicines", careIcon("medicine"), SaharaSoftBlue, currentMeds.size + pastMeds.size) {
                MedicineScanAction(onScanPrescription) { onAdd("medicine") }
                if (currentMeds.isEmpty() && pastMeds.isEmpty()) QuietLine("No medicines yet.") else {
                    CareModuleList("medicines-current", currentMeds, "No current medicines.", "Current", { it.id }) { CareRow(it, vm) }
                    CareModuleList("medicines-past", pastMeds, "No past medicines yet.", "Past", { it.id }) { CareRow(it, vm) }
                }
            } }
            item { module("Visits", careIcon("visit"), SaharaApricot, upcoming.size + pastVisits.size) {
                if (upcoming.isEmpty() && pastVisits.isEmpty()) QuietLine("No visits yet.") else {
                    CareModuleList("visits-upcoming", upcoming, "No upcoming visits.", "Upcoming", { it.id }) { visit(it) }
                    CareModuleList("visits-past", pastVisits, "No past visits yet.", "Past", { it.id }) { visit(it) }
                }
            } }
            item { module("To do", careIcon("task"), SaharaSoftGold, tasks.size) { CareModuleList("to-do", tasks, "No tasks yet.", keyOf = { it.id }) { CareRow(it, vm) } } }
            item { module("Check-ins", careIcon("checkin"), SaharaSoftBlue, checkins.size) { CareModuleList("check-ins", checkins, "No check-ins yet.", keyOf = { it.id }) { CareRow(it, vm) } } }
            item { module("Journal", careIcon("journal"), SaharaLavender, journal.size) { CareModuleList("journal", journal, "No journal notes yet.", keyOf = { it.id }) { CareRow(it, vm) } } }
        }
    }
}

@Composable fun CareRow(item: CareItem, vm: CareModel) {
    if (SymptomNoteCodec.isStructured(item.detail)) { SymptomNoteCard(item, vm); return }
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var delete by rememberSaveable(item.id) { mutableStateOf(false) }
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val done = if (item.kind == "medicine") item.doneOn == LocalDate.now().toString() else item.doneOn.isNotBlank()
    val color by animateColorAsState(if (done) Color(0xFFE7EED6) else Paper, tween(180), label = "care completed")
    SurfaceCard(color) { Row(verticalAlignment = Alignment.CenterVertically) {
        if (item.kind in listOf("medicine", "task")) Checkbox(checked = done, onCheckedChange = { if (!busy && canEditCareContext(vm, item.personId, capturedAccount)) vm.toggle(item) }, enabled = canEdit, modifier = Modifier.semantics { contentDescription = "${if (item.kind == "medicine") "Taken today" else "Done"}: ${item.title}" })
        else Surface(shape = RoundedCornerShape(12.dp), color = if (item.kind == "visit") Mint else Peach, modifier = Modifier.padding(end = 12.dp)) { Icon(careIcon(item.kind), null, tint = Ink, modifier = Modifier.padding(10.dp).size(22.dp)) }
        Column(Modifier.weight(1f)) { Text(item.title, fontWeight = FontWeight.SemiBold); if (item.due > 0) Text(date(item.due), color = if (done) RustText else Rust, fontSize = 13.sp); if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodyMedium); if (item.kind in listOf("checkin", "journal")) Text(date(item.created), style = MaterialTheme.typography.bodySmall) }
    }
        if (item.kind == "medicine") MedicineDoseActions(item, vm)
        if (canEdit) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val actionColors = ButtonDefaults.textButtonColors(contentColor = if (done) RustText else Rust)
            TextButton(onClick = { editing = true }, colors = actionColors, modifier = Modifier.testTag("care-edit-${item.id}")) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Edit") }
            TextButton(onClick = { delete = true }, colors = actionColors, modifier = Modifier.testTag("care-remove-${item.id}")) { Icon(Icons.Default.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove") }
        }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text("Remove this entry?") }, text = { Text(item.title) }, confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.remove(item); delete = false }, enabled = !busy && canEdit) { Text("Remove") } }, dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep") } })
    if (editing) AddCareItem(item.kind, vm, onDismiss = { editing = false }, initial = item)
}

@Composable fun Nearby(centres: List<Centre>) {
    var search by rememberSaveable { mutableStateOf("") }; var service by rememberSaveable { mutableStateOf("All") }; var morphine by rememberSaveable { mutableStateOf(false) }
    var view by rememberSaveable { mutableStateOf("Map & list") }
    var state by rememberSaveable { mutableStateOf("All India") }
    var chosen by remember { mutableStateOf<Centre?>(null) }
    val focus = LocalFocusManager.current
    val states = remember(centres) { centres.map { it.state }.filter(String::isNotBlank).distinct().sorted() }
    // Build each centre's search text once, not for all 1,600 centres on every typed letter.
    val searchText = remember(centres) { centres.map { c -> "${c.name} ${c.city} ${c.district} ${c.address} ${c.state}" } }
    val visible = remember(centres, search, service, morphine, state) { centres.filterIndexed { index, c ->
        searchText[index].contains(search.trim(), true) &&
            (state == "All India" || c.state == state) && (service == "All" || c.services.contains(service, ignoreCase = true)) && (!morphine || c.sourceListedMorphine == "available")
    } }
    LazyColumn(Modifier.fillMaxSize().testTag("nearby-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Heading("Find care"); Text("$state · ${visible.size} ${if (visible.size == 1) "centre" else "centres"}", color = Rust) }
        } }
        item { OutlinedTextField(value = search, onValueChange = { search = it }, placeholder = { Text("Place or hospital") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") } }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { focus.clearFocus() })) }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { (listOf("All India") + states).forEach { option -> FilterChip(selected = state == option, onClick = { state = option }, label = { Text(option) }, modifier = Modifier.heightIn(min = 48.dp)) } } }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Home care", "Clinic visits", "Inpatient care").forEach { s -> FilterChip(selected = service == s, onClick = { service = s }, label = { Text(s) }, modifier = Modifier.heightIn(min = 48.dp)) } } }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Map & list", "List").forEach { option -> FilterChip(selected = view == option, onClick = { view = option }, label = { Text(option) }, modifier = Modifier.heightIn(min = 48.dp)) }
            FilterChip(selected = morphine, onClick = { morphine = !morphine }, label = { Text("Morphine listed") }, leadingIcon = { Icon(Icons.Default.Medication, null, Modifier.size(18.dp)) }, modifier = Modifier.heightIn(min = 48.dp))
        }; Text("Directory listings. Call to confirm services, availability and cost.", style = MaterialTheme.typography.bodySmall) }
        if (view == "Map & list" && visible.isNotEmpty()) item {
            val located = visible.count { it.latitude != null && it.longitude != null }
            if (located > 0) Column(Modifier.clip(RoundedCornerShape(20.dp))) { CentreMap(visible) { chosen = it } }
            else Text("Use the published addresses and Directions below. This source has no exact map locations for this filter.", style = MaterialTheme.typography.bodySmall)
            if (located < visible.size) Text("$located mapped · ${visible.size - located} addresses in the list", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
        if (visible.isEmpty()) item { SurfaceCard { Text("No centres match that search."); TextButton(onClick = { search = ""; service = "All"; state = "All India"; morphine = false }) { Text("Clear filters") } } }
        items(visible, key = { it.id }) { c -> CentreCard(c) }
    }
    chosen?.let { centre -> ModalBottomSheet(onDismissRequest = { chosen = null }, containerColor = Cream) {
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) { CentreCard(centre) }
    } }
}

@Composable fun CentreCard(c: Centre) {
    val context = LocalContext.current
    SurfaceCard {
            Text(listOf(c.city, c.state).filter(String::isNotBlank).distinct().joinToString(" · "), color = Rust, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(6.dp)); Text(c.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp)); Text(c.services, style = MaterialTheme.typography.bodySmall); Text(c.address, style = MaterialTheme.typography.bodyMedium)
            if (c.sourceListedMorphine == "available") Text("Directory lists morphine · call to confirm", fontSize = 12.sp, color = Color(0xFF465E45), modifier = Modifier.padding(top = 8.dp))
            Text(listOf(c.sourceName, c.checkedOn.takeIf { it.isNotBlank() }?.let { "Source retrieved $it" }).filterNotNull().filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (c.phone.any(Char::isDigit)) TextButton(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.replace(Regex("[^+0-9]"), "")))) }) { Icon(Icons.Default.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Call") }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(c.name + " " + c.address)))) }) { Text("Directions") }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }) { Text("Source") }
            }
    }
}

@Composable fun More(vm: CareModel, person: Person, onAccount: () -> Unit, onDelete: () -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, onFeedback: () -> Unit = {}, onReminders: () -> Unit = {}, onSharedWorkspace: () -> Unit = {}, onDocuments: () -> Unit = {}, doctorEmbedded: Boolean = false, walkthrough: Boolean = false) {
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current; val versions by vm.versions.collectAsStateWithLifecycle(); val acks by vm.acknowledgements.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Heading(if (doctorEmbedded) "Daily care & tools" else "More") }
        item { OutlinedButton(onClick = onDocuments, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("more-documents-entry")) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Documents") } }
        if (!walkthrough) item { OutlinedButton(onClick = onFeedback, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("patient-feedback-entry")) { Icon(Icons.Default.SentimentSatisfiedAlt, null); Spacer(Modifier.width(8.dp)); Text("App feedback") } }
        if (doctorEmbedded) item { OutlinedButton(onClick = onSharedWorkspace, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Groups, null); Spacer(Modifier.width(8.dp)); Text("Shared care and clinical review") } }
        item { DailyCareShortcuts(onCalendar, onWorkflow, onLabs) }
        if (!walkthrough) item { OutlinedButton(onClick = onReminders, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.NotificationsNone, null); Spacer(Modifier.width(8.dp)); Text("Reminders") } }
        if (!doctorEmbedded && !walkthrough) item { OutlinedButton(onClick = onAccount, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.PersonOutline, null); Spacer(Modifier.width(8.dp)); Text(if (vm.cloud == null) "Sign in to share care" else "Your account and sharing") } }
        item { SurfaceCard(Gold) {
            Icon(Icons.Default.Description, null, Modifier.size(28.dp)); Spacer(Modifier.height(12.dp))
            Text("${person.name}'s care notes", fontFamily = SaharaTitleFont, fontSize = 21.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp)); Text("${versions.size} saved", color = Ink)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { val text = versions.joinToString("\n\n──────────\n\n") { v -> NoteRules.text(v, acks.filter { it.versionId == v.id && it.contentHash == v.contentHash }) }; open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Export care notes")) }, enabled = versions.isNotEmpty(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Export notes") }
        } }
        item { DetailPanel("Privacy", Icons.Default.Lock) {
            Text("You choose which notes to share and with whom.")
            Spacer(Modifier.height(10.dp)); Text("You can delete a recording or remove a person's records here.")
            Spacer(Modifier.height(10.dp)); Text("Reports added in Documents stay on this phone. They aren't synced with your care team. Use Share to send a copy, and keep your originals before removing the app.")
            Spacer(Modifier.height(10.dp)); Text("Read prescription photos on this phone, then check the original and every medicine before saving it.")
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }) { Text("Privacy policy") }
        } }
        item { DetailPanel("About", Icons.Outlined.Info) {
            Text(androidx.compose.ui.res.stringResource(R.string.app_full_name))
            Spacer(Modifier.height(10.dp)); Text("Your doctor guides treatment. Selecting a role does not verify identity. Only independently approved, invited accounts can publish a verified review online.")
            Spacer(Modifier.height(10.dp)); Text("Dictate a speaker's turn or transcribe a recording with the downloaded Hindi/English pack, then check the words. Hindi/English reading uses Google Translate on-device models. Read aloud uses a downloaded voice.")
            Spacer(Modifier.height(10.dp)); Text(MedicineNames.ATTRIBUTION + " · दवा के नाम: CDCI (NRCeS / C-DAC), CC BY 4.0", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by/4.0/"))) }) { Text("CC BY 4.0 licence · लाइसेंस") }
            Spacer(Modifier.height(12.dp)); Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        } }
        if (owner && !walkthrough) item { TextButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text("Remove this person's records") } }
    }
}

@Composable fun AddPerson(onDismiss: () -> Unit, busy: Boolean, error: String?, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Who is this care for?") }, text = { Column { CareField(value = name, onValueChange = { name = it.take(100) }, label = "Name", singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && !busy) { Text(if (busy) "Saving…" else "Continue") } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } })
}

@Composable fun AddPersonContent(onDismiss: () -> Unit, busy: Boolean, error: String?, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading("Who is this care for?", 27)
        Text("Create a care profile for yourself or someone you support.")
        CareField(name, { name = it.take(100) }, "Name", singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-save-person")) { Text(if (busy) "Saving…" else "Continue") }
        TextButton(onClick = onDismiss, enabled = !busy) { Text("Back") }
    }
}

@Composable fun AddCareItem(kind: String, vm: CareModel, onDismiss: () -> Unit, initial: CareItem? = null) {
    val entryId = rememberSaveable { initial?.id ?: newId() }
    val selected by vm.selected.collectAsStateWithLifecycle()
    val capturedPerson = rememberSaveable(entryId) { initial?.personId ?: selected }
    val capturedAccount = rememberSaveable(entryId) { vm.accountNamespace }
    val error by vm.actionError.collectAsStateWithLifecycle()
    LaunchedEffect(entryId) { vm.actionError.value = null }
    LaunchedEffect(selected) { if (selected != capturedPerson) onDismiss() }
    val draftKey = if (initial == null) "$capturedAccount:$capturedPerson:$kind" else null
    val draft = remember(draftKey) { draftKey?.let(CareAddDrafts::get) }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: draft?.title ?: "") }; var detail by rememberSaveable { mutableStateOf(initial?.detail ?: draft?.detail ?: "") }; var due by rememberSaveable { mutableLongStateOf(initial?.due ?: draft?.due ?: 0) }
    var saved by remember { mutableStateOf(false) }
    DisposableEffect(draftKey) { onDispose { if (draftKey != null) { if (saved) CareAddDrafts.clear(draftKey) else CareAddDrafts.keep(draftKey, CareAddDrafts.Draft(title, detail, due)) } } }
    // The save can finish after the sheet was swiped away (it waits for a refresh), so it clears the draft itself instead of leaving the saved text to come back.
    val savedAndClose = { saved = true; draftKey?.let(CareAddDrafts::clear); onDismiss() }
    val busy by vm.busy.collectAsStateWithLifecycle(); val context = LocalContext.current
    val headings = mapOf("medicine" to "Add a medicine", "visit" to "Add a visit", "task" to "Add a task", "checkin" to "How is today?", "journal" to "Write a note")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 22.dp, end = 22.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(if (initial != null && kind != "checkin") "Edit ${when (kind) { "medicine" -> "medicine"; "visit" -> "visit"; "task" -> "task"; else -> "note" }}" else headings[kind] ?: "Add an entry", 28)
            if (kind == "checkin") {
                val moods = listOf(Triple("A good day", Icons.Default.SentimentSatisfiedAlt, Sage), Triple("An okay day", Icons.Default.SentimentNeutral, Gold), Triple("A hard day", Icons.Default.SentimentDissatisfied, Peach))
                moods.forEach { (mood, icon, tint) -> ChoiceRow(mood, icon, tint, title == mood) { title = mood } }
                CareField(value = detail, onValueChange = { detail = it.take(4000) }, label = "Anything to add? (optional)", minLines = 2)
            } else if (kind == "journal" && initial == null) {
                CareField(value = detail, onValueChange = { detail = it.take(4000); title = it.lineSequence().firstOrNull { line -> line.isNotBlank() }.orEmpty().take(80) }, label = "Your note", minLines = 4)
            } else {
                CareField(value = title, onValueChange = { title = it.take(200) }, label = when (kind) { "medicine" -> "Medicine name"; "visit" -> "Doctor or hospital"; "task" -> "What needs doing?"; else -> "Title" }, singleLine = kind != "task")
                if (kind == "medicine") MedicineNameSuggestions(title, enabled = !busy) { title = it }
                CareField(value = detail, onValueChange = { detail = it.take(4000) }, label = when (kind) { "medicine" -> "Dose and times, as prescribed"; "visit" -> "Time, place and questions (optional)"; "task" -> "Details (optional)"; else -> "Your note" }, minLines = if (kind == "journal") 4 else 2)
            }
            if (kind == "visit") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Today" to 0L, "Tomorrow" to 1L).forEach { (label, days) -> val day = LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); FilterChip(selected = due == day, onClick = { due = day }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
            if (kind == "visit") OutlinedButton(onClick = { val chosen = if (due > 0) Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> due = LocalDate.of(y, m + 1, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show() }, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(if (due == 0L) "Choose date" else date(due)) }
            if (kind == "medicine") Text("Use your prescription. Choose reminder times in Reminders. Android may delay alerts.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { if (!canEditCareContext(vm, capturedPerson, capturedAccount)) { vm.message.value = "Care access changed. Reopen this person's records before saving."; onDismiss() } else if (initial == null) vm.saveItem(kind, title, detail, due, savedAndClose, entryId) else vm.updateItem(initial.copy(title = title, detail = detail, due = due), onDismiss) }, enabled = selected == capturedPerson && title.isNotBlank() && !busy && (kind != "visit" || due > 0) && (kind != "medicine" || detail.isNotBlank()), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (busy) "Saving…" else "Save", fontSize = 16.sp) }
        }
    }
}

@Composable fun SurfaceCard(color: Color = Paper, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(20.dp), color = color, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), content = content) } }
@Composable fun Tile(title: String, illustration: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    ActionCard(color, modifier, onClick) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Image(saharaImage(illustration, 64.dp), null, Modifier.size(64.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        }
    }
}
@Composable fun Heading(text: String, size: Int = 30) { Text(text, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Normal, fontSize = size.sp, lineHeight = (size + 4).sp, letterSpacing = (-0.7).sp) }
@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 20.sp, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f)); if (action != null) TextButton(onClick = onAction) { Text(action) } } }
@Composable fun CheckLine(checked: Boolean, change: (Boolean) -> Unit, text: String) { Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = change).padding(vertical = 4.dp), verticalAlignment = Alignment.Top) { Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.minimumInteractiveComponentSize()); Text(text, Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium) } }
@Composable fun SelectionContainerText(text: String) { androidx.compose.foundation.text.selection.SelectionContainer { Text(text) } }
fun date(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))
fun open(context: android.content.Context, intent: Intent) { try { context.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(context, "No app available to open this.", android.widget.Toast.LENGTH_LONG).show() } }

/** Three top-level conversation steps, each paging through sub-pages. Flat page index: 0 Who's here and the questions, 1 Record, 2 Transcript, 3 Answers, 4 Review, 5 Publish. */
object ConversationPages {
    val names = listOf("Before", "Conversation", "Note")
    private val subs = listOf(listOf("Who's here"), listOf("Record", "Transcript"), listOf("Answers", "Review", "Publish"))
    val last = subs.sumOf { it.size } - 1
    fun flat(top: Int, sub: Int): Int { val t = top.coerceIn(0, 2); return subs.take(t).sumOf { it.size } + sub.coerceIn(0, subs[t].lastIndex) }
    fun top(flat: Int): Int { var n = flat.coerceIn(0, last); subs.forEachIndexed { i, l -> if (n < l.size) return i else n -= l.size }; return 2 }
    fun sub(flat: Int): Int { var n = flat.coerceIn(0, last); subs.forEach { l -> if (n < l.size) return n else n -= l.size }; return 0 }
    /** Blank for a step that is a single page, where "1 of 1" would say nothing. */
    fun subLabel(top: Int, sub: Int): String { val l = subs[top.coerceIn(0, 2)]; val i = sub.coerceIn(0, l.lastIndex); return if (l.size < 2) "" else "${l[i]} · ${i + 1} of ${l.size}" }
}

/** Progress indicator for the three conversation steps. Navigation is by each page's own Back/Continue button. */
@Composable fun ConversationStepper(step: Int) {
    val names = ConversationPages.names
    Column(Modifier.fillMaxWidth().testTag("conversation-stepper")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            names.forEachIndexed { i, name ->
                val done = i < step; val current = i == step
                val fill = if (done) DoctorStatusGoodBg else if (current) DoctorAccent else DoctorStatusRestBg
                val ink = if (done) DoctorStatusGood else if (current) Color.White else DoctorCardSub
                // Progress only: each page is reached by its own Back/Continue button.
                Column(Modifier.weight(1f).heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = name; stateDescription = if (done) "Done" else if (current) "Current step" else "Not started" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(2.dp).background(if (i == 0) Color.Transparent else if (i <= step) DoctorStatusGood else DoctorHairline))
                        Box(Modifier.size(32.dp).background(fill, CircleShape).border(if (current) 3.dp else 1.dp, if (current) DoctorStatusWarnBg else DoctorHairline, CircleShape), contentAlignment = Alignment.Center) { if (done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = ink) else Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ink) }
                        Box(Modifier.weight(1f).height(2.dp).background(if (i == names.lastIndex) Color.Transparent else if (i < step) DoctorStatusGood else DoctorHairline))
                    }
                    Text(name, fontSize = cappedLabelSize(12f), lineHeight = 16.sp, color = if (current) DoctorCardInk else DoctorCardFaint, fontWeight = if (current) FontWeight.Medium else FontWeight.Normal, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Text("Step ${step + 1} of ${names.size} · ${names[step.coerceIn(0, names.lastIndex)]}", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable fun InfoDisclosure(title: String, color: Color = Paper, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(20.dp), color = color, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("info-disclosure")) { Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (open) "Hide details" else "Show details") { open = !open }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f), fontFamily = SaharaTitleFont, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "Collapse" else "Expand", tint = DoctorCardSub)
        }
        if (open) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    } }
}

@Composable fun RawTranscriptPanel(source: String) {
    var open by rememberSaveable { mutableStateOf(true) }
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("raw-transcript")) { Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (open) "Collapse raw transcript" else "Expand raw transcript") { open = !open }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Raw transcript", Modifier.weight(1f), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            Text("${source.trim().split(Regex("\\s+")).count { it.isNotEmpty() }} words", fontSize = 12.sp, color = DoctorCardFaint); Spacer(Modifier.width(8.dp))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = DoctorCardSub)
        }
        if (open) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Read-only. Edit the transcript on the transcript step.", fontSize = 12.sp, color = DoctorCardFaint)
            Box(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) { androidx.compose.foundation.text.selection.SelectionContainer { Text(source.ifBlank { "No transcript captured yet." }, fontSize = 15.sp, lineHeight = 24.sp, color = DoctorCardInk) } }
        }
    } }
}
