@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// Older names for the Sahara colour system (SaharaPalette.kt); new code reads SaharaColors and SaharaTone.
val Ink = SaharaColors.Ink
val Cream = SaharaColors.Ground
val Paper = SaharaColors.Surface
val Rust = SaharaColors.Primary
val Gold = SaharaTone.Gold.solid
val Sage = SaharaTone.Leaf.wash
val Mint = SaharaTone.Teal.wash
val Peach = SaharaColors.PrimaryWash
/** The welcome panel is the header band: deep oxblood with white text. */
internal val WelcomePanelOrange = SaharaColors.Band
internal val WelcomePanelInk = SaharaColors.OnBand
internal fun welcomePanelGapDp(screenHeightDp: Int, fontScale: Float): Int =
    if (screenHeightDp < 640 || fontScale >= 1.3f) 32 else 64
internal fun welcomeHeroTopPaddingDp(screenHeightDp: Int, fontScale: Float): Int =
    if (screenHeightDp < 640 || fontScale >= 1.3f) 48 else 72

/** Live identity checks for delayed form callbacks; a display role never grants edit access. */
internal fun careEditContextMatches(expectedAccount: String, modelAccount: String, sessionAccount: String, person: String, selected: String, editable: Boolean): Boolean =
    expectedAccount == modelAccount && expectedAccount == sessionAccount && person.isNotBlank() && person == selected && editable

internal fun canEditCareContext(vm: CareModel, person: String, account: String): Boolean =
    careEditContextMatches(account, vm.accountNamespace, vm.cloud?.session?.value?.userId.orEmpty(), person, vm.selected.value, vm.canEdit.value) && vm.people.value.any { it.id == person }

/**
 * One theme for every screen (family side, doctor side, sign-in). Material's container roles are mapped onto
 * Sahara's ground and white surfaces; the outline is strong enough (3:1) that outlined buttons and fields read as controls.
 * Shapes follow the AI-FIR radii: 12 inputs and menus, pills for chips, 14 cards, 16 large cards, 22 dialogs and sheets.
 */
internal val saharaColorScheme = lightColorScheme(primary = SaharaColors.Primary, onPrimary = SaharaColors.OnPrimary,
    primaryContainer = SaharaColors.PrimaryWash, onPrimaryContainer = SaharaColors.PrimaryPressed,
    // secondaryContainer is Material's selected state (chips, tonal buttons, nav indicator): the bright solid rust, white on it at 4.85:1.
    secondary = SaharaTone.Teal.solid, onSecondary = Color.White, secondaryContainer = SaharaColors.Primary, onSecondaryContainer = Color.White,
    tertiary = SaharaTone.Plum.solid, onTertiary = Color.White, tertiaryContainer = SaharaTone.Plum.wash, onTertiaryContainer = SaharaTone.Plum.ink,
    error = SaharaColors.Error, onError = Color.White, errorContainer = SaharaColors.ErrorWash, onErrorContainer = SaharaTone.Error.ink,
    background = SaharaColors.Ground, onBackground = SaharaColors.Ink, surface = SaharaColors.Surface, onSurface = SaharaColors.Ink,
    surfaceVariant = SaharaColors.Well, onSurfaceVariant = SaharaColors.InkSub,
    surfaceTint = SaharaColors.Surface, surfaceBright = SaharaColors.Surface, surfaceDim = SaharaColors.Well,
    surfaceContainerLowest = SaharaColors.Surface, surfaceContainerLow = SaharaColors.Ground, surfaceContainer = SaharaColors.Surface,
    surfaceContainerHigh = SaharaColors.Surface, surfaceContainerHighest = SaharaColors.Well,
    inverseSurface = SaharaColors.Ink, inverseOnSurface = Color.White, inversePrimary = SaharaTone.Marigold.wash,
    outline = SaharaColors.LineStrong, outlineVariant = SaharaColors.Line, scrim = Color(0x99000000))

internal val saharaShapes = Shapes(extraSmall = RoundedCornerShape(SaharaRadius.Sm), small = RoundedCornerShape(SaharaRadius.Pill),
    medium = RoundedCornerShape(SaharaRadius.Md), large = RoundedCornerShape(SaharaRadius.Lg), extraLarge = RoundedCornerShape(SaharaRadius.Hero))

@Composable fun CareTheme(content: @Composable () -> Unit) {
    SaharaMotion.bind(LocalContext.current)
    MaterialTheme(colorScheme = saharaColorScheme, shapes = saharaShapes, typography = saharaTypography(), content = content)
}

@Composable fun CareApp(vm: CareModel, onAccount: () -> Unit, onDoctorDemo: () -> Unit = {}, onDoctorWorkspace: (() -> Unit)? = null,
    welcomeAccountContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    welcomeDoctorContent: (@Composable (() -> Unit, (Boolean) -> Unit) -> Unit)? = null,
    onWelcomePanelChanged: (String) -> Unit = {}, onPatientWalkthrough: (String) -> Unit = {},
    walkthrough: PatientWalkthrough.User? = null, onLeaveWalkthrough: () -> Unit = {}, onSwitchWalkthrough: (String) -> Unit = {},
    openToday: String? = null, onOpenTodayHandled: () -> Unit = {}) {
    val allPeople by vm.people.collectAsStateWithLifecycle()
    val people = if (walkthrough != null) PatientWalkthrough.visiblePeople(allPeople) else allPeople
    val selected by vm.selected.collectAsStateWithLifecycle()
    val clinician by vm.clinician.collectAsStateWithLifecycle(); val isOwnerTop by vm.isOwner.collectAsStateWithLifecycle()
    val role by vm.viewRole.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val actionError by vm.actionError.collectAsStateWithLifecycle()
    val activeRecording by vm.recording.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshError by vm.refreshError.collectAsStateWithLifecycle()
    val syncWaiting by vm.syncWaiting.collectAsStateWithLifecycle()
    val syncKept by vm.syncKept.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val language = rememberCareLanguage(vm.accountNamespace)
    val t = { en: String, hi: String -> careTr(language.hindi, en, hi) }
    val askOwner = t("View only", "सिर्फ़ देखने के लिए")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Screens opened from inside a tab (Calendar, Care team, Lab history, Symptoms, Docs from More…) remember where they came from.
    // Each entry may also name an overlay or sheet to reopen on the way back (see CareNavTrail), so Back returns to the page it came from.
    var tabTrail by rememberSaveable { mutableStateOf("") }
    var tabDirection by remember { mutableStateOf(SaharaMotion.Direction.Switch) }
    fun go(next: Int, reopen: String = "") { if (next != tab) { tabTrail = CareNavTrail.push(tabTrail, tab, reopen); tabDirection = SaharaMotion.Direction.Forward; tab = next } }
    // Today's SOS sheet reopens when Back returns from the emergency plan it opened.
    var sosReopen by rememberSaveable { mutableStateOf(false) }
    // Daily care opens at the section a Today "See all" points to; the bottom tab opens it from the top.
    var careFilter by rememberSaveable { mutableStateOf("All") }
    // A visit card opens the Calendar on that visit's day; every other way in opens it on today (0).
    var calendarDay by rememberSaveable { mutableLongStateOf(0L) }
    fun openCalendar(at: Long = 0L) { calendarDay = at; go(6) }
    // Today's "Note a symptom" opens the symptom form straight away; the diary clears this once it has opened the form.
    var symptomAddPending by rememberSaveable { mutableStateOf(false) }
    fun switchTab(next: Int) { careFilter = "All"; tabTrail = ""; tabDirection = SaharaMotion.Direction.Switch; tab = next }
    var dialog by rememberSaveable { mutableStateOf("") }
    // Full-screen overlays opened from another overlay (check-in, then distress) return to it on Back.
    var overlayTrail by rememberSaveable { mutableStateOf("") }
    fun openOverlay(next: String) { if (dialog in setOf("checkin", "distress")) overlayTrail = CareNavTrail.pushOverlay(overlayTrail, dialog); dialog = next }
    fun closeOverlay() { val (previous, rest) = CareNavTrail.popOverlay(overlayTrail); overlayTrail = rest; dialog = previous }
    fun back() {
        tabDirection = SaharaMotion.Direction.Back
        val popped = CareNavTrail.pop(tabTrail)
        if (popped == null) { tab = 0; return }
        val (entry, rest) = popped
        tab = entry.tab; tabTrail = rest
        when (entry.reopen) { "" -> Unit; "sos" -> sosReopen = true; else -> { overlayTrail = ""; dialog = entry.reopen } }
    }
    var inviteFamilyOpen by rememberSaveable { mutableStateOf(false) }
    var medicineScanner by rememberSaveable { mutableStateOf(false) }
    var medicineScannerPerson by rememberSaveable { mutableStateOf("") }
    var reading by remember { mutableStateOf<CareVersion?>(null) }
    val feedbackAccountScope = vm.cloud?.session?.value?.userId ?: "guest-patient"
    var feedbackOpen by rememberSaveable(feedbackAccountScope) { mutableStateOf(false) }
    var remindersOpen by rememberSaveable(feedbackAccountScope) { mutableStateOf(false) }
    var sharedWorkspaceOpen by rememberSaveable(feedbackAccountScope) { mutableStateOf(false) }
    val reminderTapped by CareReminderLaunch.pending.collectAsStateWithLifecycle()
    // A tap that also names a person is handled below (select that person first, then open Reminders).
    LaunchedEffect(reminderTapped, selected, openToday) { if (reminderTapped && openToday == null && selected.isNotBlank() && walkthrough == null) { remindersOpen = true; CareReminderLaunch.pending.value = false } }
    val openDoctorWorkspace = { if (onDoctorWorkspace != null) onDoctorWorkspace() else sharedWorkspaceOpen = true }
    val screens = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val person = people.firstOrNull { it.id == selected }
    // The language name stays beside the icon on the welcome screen up to 1.3x text, so the choice is easy to find before sign-in.
    val compactWelcomeHeader = LocalConfiguration.current.screenWidthDp < 340 || LocalDensity.current.fontScale > 1.3f
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
    // Important results and problems open a small dialog; a plain "Saved" stays a quiet toast (SaharaNotice routes them).
    var noticeDialog by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { message?.let { if (SaharaNotice.route(it) == SaharaNotice.Route.Dialog) noticeDialog = it else snackbar.showSnackbar(SaharaNotice.quietText(it)); vm.message.value = null } }
    val peopleLoaded by vm.peopleLoaded.collectAsStateWithLifecycle()
    // After the last person is removed (from More), the next person added starts on Today, not on a leftover tab.
    LaunchedEffect(peopleLoaded, people.isEmpty()) { if (peopleLoaded && people.isEmpty()) { tab = 0; tabTrail = ""; careFilter = "All" } }
    // A reminder notification selects the person whose saved activity is due, then opens Reminders on top of their Today.
    LaunchedEffect(openToday, peopleLoaded) {
        val target = openToday ?: return@LaunchedEffect
        if (!peopleLoaded) return@LaunchedEffect
        if (target.isNotBlank() && target != vm.selected.value && vm.people.value.any { it.id == target }) vm.select(target)
        reading = null; dialog = ""; overlayTrail = ""; sosReopen = false; inviteFamilyOpen = false; medicineScanner = false; feedbackOpen = false; remindersOpen = false; sharedWorkspaceOpen = false
        switchTab(0)
        if (CareReminderLaunch.pending.value && walkthrough == null && vm.selected.value.isNotBlank()) { remindersOpen = true; CareReminderLaunch.pending.value = false }
        onOpenTodayHandled()
    }
    val overlay = when { !peopleLoaded -> "starting"; dialog == "checkin" -> "checkin"; dialog == "distress" -> "distress"; feedbackOpen -> "feedback"; remindersOpen -> "reminders"; sharedWorkspaceOpen -> "shared"; else -> "" }
    CompositionLocalProvider(LocalCareLanguage provides language) {
    Box(Modifier.fillMaxSize().background(Cream)) {
    SaharaScreenHost(overlay, label = "Care overlay", direction = { from, to -> if (from == "starting") SaharaMotion.Direction.Switch else if (to.isEmpty()) SaharaMotion.Direction.Back else SaharaMotion.Direction.Forward }) { shown -> when (shown) {
    "starting" -> Box(Modifier.fillMaxSize().testTag("care-starting"))
    "checkin" -> FeedbackScreen(accountScope = feedbackAccountScope, surface = "patient-checkin", onClose = { closeOverlay() }, careModel = vm, personId = vm.selected.value,
        onDistress = { openOverlay("distress") }, onSymptom = { dialog = ""; overlayTrail = ""; symptomAddPending = true; go(9, reopen = "checkin") })
    "distress" -> DistressScreen(vm, onClose = { closeOverlay() })
    "feedback" -> FeedbackScreen(accountScope = feedbackAccountScope, surface = "patient", onClose = { feedbackOpen = false }, cloud = vm.cloud)
    "reminders" -> CareReminderSettings(vm, onClose = { remindersOpen = false })
    "shared" -> CareSharedWorkspace(vm, onAccount = onAccount, onClose = { sharedWorkspaceOpen = false })
    else -> {
    BackHandler(careTabBackEnabled(person != null, medicineScanner, tab, activeRecording)) { if (activeRecording) vm.stopRecording() else back() }
    BackHandler(walkthrough != null && !medicineScanner && tab == 0 && !activeRecording) { onLeaveWalkthrough() }
    Scaffold(containerColor = Cream, snackbarHost = { CareToastHost(snackbar) }, topBar = {
        // Inside a person's care the top bar is the dark header band (white text); on the welcome screen it stays on the cream ground.
        if (!medicineScanner) Surface(color = if (person != null) SaharaColors.Band else Color.Transparent, contentColor = if (person != null) SaharaColors.OnBand else Ink) { Column {
        if (person != null) SaharaStatusBarIcons(lightIcons = true)
        Row(Modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(saharaImage(R.drawable.sahara_professional_logo, 44.dp), null, Modifier.size(44.dp).then(if (person != null) Modifier.clip(CircleShape).background(SaharaColors.Surface).padding(3.dp) else Modifier), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 2.dp)) {
                    // At large text with a person chip, the logo alone names the app instead of a cut "Sa…".
                    if (person == null || androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f)
                    Text(androidx.compose.ui.res.stringResource(R.string.app_name), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, color = LocalContentColor.current, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            // The one language choice, on the header of every family-side screen (welcome and sign-in included).
            CareLanguageMenu(compact = compactWelcomeHeader || person != null)
            if (person != null && walkthrough != null) WalkthroughUserMenu(walkthrough, onSwitch = { if (activeRecording) vm.stopRecording(); onSwitchWalkthrough(it) }, onLeave = { if (activeRecording) vm.stopRecording(); onLeaveWalkthrough() })
            // The person's name and role sit in the top bar; switching, adding a person and inviting family are in its menu.
            if (person != null && walkthrough == null) CareTopPersonMenu(person.name, when (role) { "Family" -> t("Family", "परिवार"); "Patient" -> t("Patient", "मरीज़"); else -> "" },
                people.map { it.id to it.name }, selected, enabled = !activeRecording,
                onSelect = { vm.select(it); reading = null }, onAdd = { dialog = "person" }, canAdd = true,
                onInvite = { inviteFamilyOpen = true }, canInvite = !clinician && isOwnerTop,
                inviteLabel = if (vm.cloud == null) t("Add family", "परिवार जोड़ें") else t("Invite family", "परिवार को बुलाएँ"))
        }
        }
        }
    }, bottomBar = {
        if (person != null && !medicineScanner) CareNavigation(CareNavTrail.rootTab(tabTrail, tab), clinician) { next ->
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
                    0 -> Today(vm, clinician, onTalk = { go(1) }, onCare = { careFilter = it; go(2) }, onNearby = { go(3) }, onAdd = { if (canEdit) dialog = it else vm.message.value = askOwner }, onRead = { reading = it }, onCalendar = { openCalendar() }, onWorkflow = { go(7) }, onLabs = { go(8) }, onSharedWorkspace = { go(7) }, onSymptoms = { go(9) }, onInviteFamily = { inviteFamilyOpen = true }, onCalendarAt = ::openCalendar,
                        onCheckInWords = { if (canEdit) dialog = "checkin" else vm.message.value = askOwner }, onDistress = { if (canEdit) dialog = "distress" else vm.message.value = askOwner }, onSymptomNote = { symptomAddPending = true; go(9) }, onCarePlan = { go(10) },
                        onCarePlanFromSos = { go(10, reopen = "sos") }, sosReopen = sosReopen, onSosReopened = { sosReopen = false }, onCommunity = { go(11) })
                    1 -> Talk(vm, clinician && canEdit, onRead = { reading = it }, onRequestConversation = { go(7) })
                    2 -> CareList(vm, initialFilter = careFilter, onReadNote = { reading = it }, onCalendar = { openCalendar() }, onCalendarAt = ::openCalendar, onWorkflow = { go(7) }, onLabs = { go(8) }, onSymptoms = { go(9) }, onCommunity = { go(11) }, onDevices = { go(12) }, onExit = { back() },
                        onDocuments = { go(5) }, onCarePlan = { go(10) }, title = t("All care tools", "देखभाल के सभी साधन"),
                        onAdd = { if (canEdit) dialog = it else vm.message.value = askOwner },
                        onScanPrescription = {
                            if (canEdit) {
                                medicineScannerPerson = selected
                                medicineScanner = true
                            } else vm.message.value = askOwner
                        })
                    3 -> NearbyScreen(vm.centres, vm)
                    5 -> CareDocuments(vm, onRead = { reading = it }, onTalk = { go(1) }, onCalendarAt = ::openCalendar, onLabs = { go(8) }, onCare = { go(2) })
                    6 -> CareCalendar(vm, onWorkflow = { go(7) }, initialDay = calendarDay)
                    7 -> CareWorkflows(vm, onTalk = { go(1) })
                    8 -> CareLabs(vm)
                    9 -> CareSymptomDiary(vm, onBack = { back() }, startAdding = symptomAddPending, onStartAddingHandled = { symptomAddPending = false })
                    10 -> CarePlanScreen(vm, clinician, onBack = { back() })
                    11 -> CareCommunityScreen(vm.cloud, onAccount = onAccount, onBack = { back() })
                    12 -> CareDevicesScreen(onBack = { back() }, namespace = vm.storageNamespace, personId = selected)
                    else -> More(vm, person, onAccount = onAccount, onDelete = { dialog = "delete" }, onCalendar = { openCalendar() }, onWorkflow = { go(7) }, onLabs = { go(8) }, walkthrough = walkthrough != null, onFeedback = { if (activeRecording) vm.stopRecording(); feedbackOpen = true }, onReminders = { if (activeRecording) vm.stopRecording(); remindersOpen = true }, onSharedWorkspace = { if (activeRecording) vm.stopRecording(); openDoctorWorkspace() }, onDocuments = { if (activeRecording) vm.stopRecording(); go(5) }, onCarePlan = { if (activeRecording) vm.stopRecording(); go(10) }, onDevices = { if (activeRecording) vm.stopRecording(); go(12) }, onInviteFamily = if (!clinician && isOwnerTop) ({ if (activeRecording) vm.stopRecording(); inviteFamilyOpen = true }) else null)
                } }
            } }
            refreshError?.let { error -> Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Peach) { Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ErrorOutline, null, Modifier.size(20.dp), tint = RustText); Text(error, Modifier.weight(1f).padding(12.dp).announce(), style = MaterialTheme.typography.bodySmall, color = Ink); TextButton(onClick = vm::refresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Retry", "फिर कोशिश करें"), color = RustText, fontWeight = FontWeight.Bold) } } } }
            if (person != null) CareSyncStatusLine(syncWaiting, syncKept, busy || refreshing, vm::sendPending, Modifier.align(Alignment.BottomCenter))
            SaharaTopProgress(busy || refreshing, Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    if (inviteFamilyOpen) AddFamilySheet(vm, onDismiss = { inviteFamilyOpen = false }, onSignIn = onAccount)
    if (dialog == "person") AddPerson(onDismiss = { dialog = "" }, busy = busy || refreshing, error = actionError, onSave = { vm.addPerson(it) { dialog = "" } })
    if (dialog in listOf("medicine", "visit", "task", "journal")) AddCareItem(dialog, vm, onDismiss = { dialog = "" })
    if (dialog == "delete") AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(t("Remove ${person?.name}'s records?", "${person?.name} के रिकॉर्ड हटाएँ?")) },
        text = { Text(t("This can't be undone.", "इसे वापस नहीं किया जा सकता।")) },
        confirmButton = { TextButton(onClick = { val removedId = person?.id; vm.removePerson { (0..12).forEach { screens.removeState("$removedId:$it") }; dialog = "" } }, enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(t("Remove records", "रिकॉर्ड हटाएँ"), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { dialog = "" }) { Text(t("Keep records", "रिकॉर्ड रखें")) } })
    reading?.let { note -> NoteReader(vm, note, onDismiss = { reading = null }) }
    }
    } }
    if (overlay.isNotEmpty() && overlay != "starting") CareToastHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    noticeDialog?.let { SaharaNoticeDialog(it) { noticeDialog = null } }
    }
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
    // Which role tile opened the shared patient/family panel. It changes only the wording there; both open the same panel.
    var role by rememberSaveable { mutableStateOf("patient") }
    var formBusy by remember { mutableStateOf(false) }
    var walkthroughChooser by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current; val t = careText()
    val panelGap = welcomePanelGapDp(LocalConfiguration.current.screenHeightDp, LocalDensity.current.fontScale)
    val heroTopPadding = welcomeHeroTopPaddingDp(LocalConfiguration.current.screenHeightDp, LocalDensity.current.fontScale)
    // Three tiles side by side on a normal phone; stacked rows on a narrow screen or at large text, so no label is ever split.
    val stackRoles = welcomeRolesStacked(LocalConfiguration.current.screenWidthDp, LocalDensity.current.fontScale)
    fun select(value: String) { if (!formBusy && !entryBusy) { focus.clearFocus(); panel = value; onPanelChanged(value) } }
    val close = { if (!formBusy && !entryBusy) select(if (panel == "person" || panel == "account") "family" else "") }
    BackHandler(panel.isNotEmpty()) { close() }
    if (walkthroughChooser) PatientWalkthroughChooser(onPick = { walkthroughChooser = false; onPatientWalkthrough(it) }, onDismiss = { walkthroughChooser = false })
    // One value drives the whole change: the band panel rises over the picture while the picture fades and drifts up.
    // Nothing is re-measured per frame, so it stays smooth; the panel's own contents cross-fade.
    val open = panel.isNotEmpty()
    val progress by animateFloatAsState(if (open) 1f else 0f, SaharaMotion.tween(SaharaMotion.PANEL_MS, easing = SaharaMotion.Emphasized))
    var closedPanelPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val roles = listOf(
        WelcomeRole("patient", t("Patient", "मरीज़"), Icons.Default.Person, SaharaTone.Teal, "welcome-patient-family"),
        WelcomeRole("family", t("Family", "परिवार"), Icons.Default.People, SaharaTone.Leaf, "welcome-family"),
        WelcomeRole("doctor", t("Doctor", "डॉक्टर"), Icons.Default.MedicalServices, SaharaTone.Indigo, "doctor-workspace-entry"))
    fun pick(key: String) { if (key == "doctor") select("doctor") else if (!formBusy && !entryBusy) { role = key; select("family") } }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding().testTag("welcome-panel-screen")) {
        val fullPx = constraints.maxHeight
        val closedPx = closedPanelPx.coerceIn(0, fullPx)
        val gapPx = with(density) { panelGap.dp.roundToPx() }
        if (!(open && progress >= 1f)) Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(with(density) { (fullPx - closedPx - gapPx).coerceAtLeast(0).toDp() })
            .graphicsLayer { alpha = 1f - progress; translationY = -progress * 48.dp.toPx() }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = heroTopPadding.dp, bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WelcomeHero()
                    Text(t("Palliative care,\ntogether.", "पैलिएटिव देखभाल,\nसाथ मिलकर।"), Modifier.fillMaxWidth(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp, textAlign = TextAlign.Center)
                }
                }
        }
        if (!open) Spacer(Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { closedPx.toDp() }).fillMaxWidth().height(panelGap.dp).testTag("welcome-panel-gap"))
        val panelHeight = if (!open && progress <= 0f) Modifier.wrapContentHeight() else Modifier.height(with(density) { (closedPx + (fullPx - closedPx) * progress).toInt().toDp() })
        Surface(color = WelcomePanelOrange, contentColor = WelcomePanelInk, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().then(panelHeight).onSizeChanged { if (!open && progress <= 0f) closedPanelPx = it.height }.testTag("welcome-orange-panel")) {
            AnimatedContent(panel, transitionSpec = { fadeIn(SaharaMotion.tween(SaharaMotion.ENTER_MS - 60, 80)) togetherWith fadeOut(SaharaMotion.tween(SaharaMotion.EXIT_MS)) using null },
                contentAlignment = Alignment.TopCenter) { shown ->
                Column(if (shown.isEmpty()) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                    if (shown.isEmpty()) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(t("Welcome to Sahara", "सहारा में आपका स्वागत है"), modifier = Modifier.fillMaxWidth().a11yHeading().testTag("welcome-panel-heading"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp, textAlign = TextAlign.Center)
                        Text(t("Who is using Sahara?", "सहारा कौन इस्तेमाल कर रहा है?"), Modifier.fillMaxWidth(), color = SaharaColors.OnBandSub, fontSize = 16.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                        if (stackRoles) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            roles.forEachIndexed { index, item -> WelcomeRoleTile(item, index, stacked = true, enabled = !entryBusy, onClick = { pick(item.key) }, modifier = Modifier.fillMaxWidth()) }
                        } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            roles.forEachIndexed { index, item -> WelcomeRoleTile(item, index, stacked = false, enabled = !entryBusy, onClick = { pick(item.key) }, modifier = Modifier.weight(1f).fillMaxHeight()) }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = close, enabled = !formBusy && !entryBusy, modifier = Modifier.size(48.dp).testTag("welcome-panel-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = WelcomePanelInk) }
                            Spacer(Modifier.width(4.dp))
                            Text(when(shown) { "family" -> if (role == "family") t("Family", "परिवार") else t("Patient", "मरीज़"); "person" -> t("Add a person", "व्यक्ति जोड़ें"); "doctor" -> t("Doctor", "डॉक्टर"); else -> t("Sign in", "साइन इन") },
                                Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(color = Paper, contentColor = Ink, shape = RoundedCornerShape(topStart = SaharaRadius.Hero, topEnd = SaharaRadius.Hero), modifier = Modifier.fillMaxWidth().weight(1f)) {
                            Box(Modifier.fillMaxSize().padding(top = 16.dp)) { when(shown) {
                                "family" -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp).saharaScreenEnter(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.fillMaxWidth()) { SaharaIllustration(SaharaArtKind.Welcome, Modifier.fillMaxWidth()); SaharaLottie(SaharaLottieKind.Wave, Modifier.align(Alignment.TopEnd).size(56.dp)) }
                                    SaharaPrimaryButton(if (role == "family") t("Add the person you care for", "जिनकी देखभाल करते हैं, उन्हें जोड़ें") else t("Start with your name", "अपने नाम से शुरू करें"),
                                        onClick = { if (addPersonContent == null) onStart() else select("person") }, enabled = !entryBusy, icon = Icons.Default.PersonAdd, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("welcome-add-person"))
                                    SaharaSecondaryButton(t("Sign in", "साइन इन"), onClick = { if (accountContent == null) onAccount() else select("account") }, enabled = !entryBusy, icon = Icons.Default.AccountCircle,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("welcome-account"))
                                    Spacer(Modifier.height(4.dp))
                                    SaharaPracticeEntry(t("Practice", "अभ्यास करें"), t("Look around with a ready-made record. No account needed.", "तैयार रिकॉर्ड के साथ ऐप देखें। खाते की ज़रूरत नहीं।"),
                                        enabled = !entryBusy, onClick = { walkthroughChooser = true }, modifier = Modifier.testTag("patient-walkthrough-entry"))
                                }
                                "person" -> addPersonContent?.invoke(close)
                                "doctor" -> Column(Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxWidth().weight(1f)) {
                                        if (doctorContent != null) doctorContent.invoke(close) { formBusy = it }
                                        else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            SaharaPrimaryButton(t("Sign in", "साइन इन"), onClick = onSharedWorkspace, enabled = !formBusy && !entryBusy, modifier = Modifier.fillMaxWidth().testTag("welcome-open-doctor-workspace"))
                                        }
                                    }
                                    // Practice stays in view below the sign-in form, whatever the form's length.
                                    HorizontalDivider(color = SaharaColors.Line)
                                    SaharaPracticeEntry(t("Practice", "अभ्यास करें"), t("Open Dr Meera's practice workspace. No account needed.", "डॉ. मीरा का अभ्यास वर्कस्पेस खोलें। खाते की ज़रूरत नहीं।"),
                                        enabled = !formBusy && !entryBusy, onClick = onDoctorDemo, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("doctor-demo-entry"))
                                }
                                else -> accountContent?.invoke(close) { formBusy = it }
                            } }
                        }
                    }
                }
            }
        }
    }
}

/** Role tiles stack as rows below 360 dp or at 1.3x text and above, so a label never has to break. */
internal fun welcomeRolesStacked(screenWidthDp: Int, fontScale: Float): Boolean = screenWidthDp < 360 || fontScale >= 1.3f

private class WelcomeRole(val key: String, val label: String, val icon: ImageVector, val tone: SaharaTone, val tag: String)

/** One big labelled role choice on the welcome band: white tile, the role's colour as chip and edge, a press settle and a short staggered entrance. */
@Composable private fun WelcomeRoleTile(role: WelcomeRole, index: Int, stacked: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val shown = remember { Animatable(if (SaharaMotion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, SaharaMotion.tween(SaharaMotion.LIST_ITEM_MS, SaharaMotion.staggerDelay(index + 1), LinearOutSlowInEasing)) }
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(SaharaRadius.Lg), color = SaharaColors.Surface, contentColor = SaharaColors.Ink,
        border = BorderStroke(2.dp, role.tone.solid), shadowElevation = 2.dp, interactionSource = interaction,
        modifier = modifier.heightIn(min = if (stacked) 64.dp else 112.dp).graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * 12.dp.toPx() }.saharaPress(interaction).testTag(role.tag)) {
        if (stacked) Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SaharaIconChip(role.icon, role.tone, size = 40)
            Spacer(Modifier.width(12.dp))
            Text(role.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = role.tone.ink)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = role.tone.ink)
        } else Column(Modifier.padding(horizontal = 6.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            SaharaIconChip(role.icon, role.tone, size = 48)
            SaharaFitText(role.label, style = MaterialTheme.typography.titleMedium, color = role.tone.ink, fontWeight = FontWeight.SemiBold, min = 13f)
        }
    }
}

/**
 * Today, the landing page. It points to pages rather than holding everything (3 Nov meeting): greeting and date, SOS with
 * the QR card one tap away, then the day's direct actions (the one-tap check-in, today's medicines), the summary, goals of
 * care, Up next, then page shortcuts.
 * Patient and family differ (see [CareFamilyRules]): family sees "Caring for <name>", the patient's summary first, their
 * own caregiver check, the to-do list and caregiver tips; the patient's own check-in stays with the patient.
 */
@Composable fun Today(vm: CareModel, clinician: Boolean, onTalk: () -> Unit, onCare: (String) -> Unit, onNearby: () -> Unit, onAdd: (String) -> Unit, onRead: (CareVersion) -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, onSharedWorkspace: () -> Unit = {}, onSymptoms: () -> Unit = {}, onInviteFamily: () -> Unit = {}, onCalendarAt: (Long) -> Unit = { onCalendar() }, onCheckInWords: () -> Unit = {}, onSymptomNote: () -> Unit = onSymptoms, onDistress: () -> Unit = {}, onCarePlan: () -> Unit = {},
    onCarePlanFromSos: () -> Unit = onCarePlan, sosReopen: Boolean = false, onSosReopened: () -> Unit = {}, onCommunity: () -> Unit = {}) {
    val todayPeople by vm.people.collectAsStateWithLifecycle(); val role by vm.viewRole.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val selectedPerson by vm.selected.collectAsStateWithLifecycle(); val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    val loaded = loadedFor == selectedPerson; val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val latest = versions.firstOrNull()
    val today = LocalDate.now()
    val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val hindi = careHindi(); val t = careText()
    val family = CareFamilyRules.isFamily(role, clinician)
    val personName = todayPeople.firstOrNull { it.id == selectedPerson }?.name.orEmpty()
    // SOS opens one sheet with the emergency numbers, the emergency QR card and the emergency plan.
    var sosOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(sosReopen) { if (sosReopen) { sosOpen = true; onSosReopened() } }
    val showSummary = !clinician && selectedPerson.isNotBlank()
    if (sosOpen) ModalBottomSheet(onDismissRequest = { sosOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Paper) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
            EmergencyNumbersCard()
            Spacer(Modifier.height(12.dp))
            EmergencyCardOnToday(vm, onRead)
            Spacer(Modifier.height(12.dp))
            CarePlanEntryCard { sosOpen = false; onCarePlanFromSos() }
        }
    }
    val hour = java.time.LocalTime.now().hour
    val medicines = CareDailyFlow.medicinesForToday(items, today)
    val visits = CareTodayRules.upcomingVisits(items, dayStart)
    val tasks = items.filter { it.kind == "task" && !CareEntryCodec.isStructured(it.detail) }.sortedBy { it.doneOn.isNotBlank() }
    // A soft warm wash behind Today so the white and tinted cards stand off the ground.
    LazyColumn(Modifier.fillMaxSize().background(SaharaColors.GroundWarm).saharaScreenEnter().testTag("today-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "today-header") { Row(Modifier.fillMaxWidth().saharaStagger(0), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(CareTodayRules.greetingLine(hour, hindi, personName, patient = !family && !clinician), Modifier.a11yHeading().testTag("today-greeting"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, color = DoctorCardInk)
                Text(today.format(careDateFormat("EEEE, d MMMM", hindi)), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardSub, modifier = Modifier.padding(top = 2.dp).testTag("today-date"))
                // Family only: who they are caring for, as a soft chip under the date.
                if (family && personName.isNotBlank()) Surface(shape = RoundedCornerShape(50), color = SaharaApricot, modifier = Modifier.padding(top = 8.dp)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FavoriteBorder, null, Modifier.size(16.dp), tint = RustText); Spacer(Modifier.width(6.dp))
                        Text(t("Caring for $personName", "$personName की देखभाल"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = RustText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("today-caring-for"))
                    }
                }
            }
            // A labelled button, not a bare arrow icon nobody can name.
            if (vm.cloud != null) TextButton(onClick = vm::refresh, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Icon(Icons.Default.Refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)); Text(t("Refresh", "रिफ़्रेश करें")) }
        } }
        if (!clinician) item(key = "today-sos") { Box(Modifier.saharaStagger(1)) { CareSosBar(onSos = { sosOpen = true }) } }
        // Patient: SOS, the check-in (the one main action), today's medicines, the at-a-glance register, then All care tools.
        // Family: SOS, how the patient is doing, medicines, to-dos, their own check, then All care tools. Everything else is in the Care tab.
        val summary: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            if (showSummary) item(key = "patient-summary") { PatientSummaryCard(vm, onSymptoms = if (family) onSymptomNote else onSymptoms,
                onVisit = { v -> if (v == null || v.due == 0L) onCalendar() else onCalendarAt(v.due) }, onAddVisit = if (canEdit) ({ onAdd("visit") }) else null,
                onGoals = { note -> if (note != null) onRead(note) else onTalk() }, onCarePlan = onCarePlan) }
        }
        if (family) summary() else if (!clinician && canEdit) item(key = "today-checkin") { TodayCheckInBar(vm, onOpen = onCheckInWords, onSymptom = onSymptomNote) }
        val medicineSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            // The doses card under the title carries the one "x of y taken" count.
            item(key = "today-doses") { Box(Modifier.saharaStagger(3)) { TodayDosesCard(vm) } }
            item(key = "medicines-open") { SaharaSecondaryButton(t("Open medicines", "दवाइयाँ खोलें"), { onCare("Medicines") }, Modifier.fillMaxWidth().saharaStagger(4).testTag("today-medicines-title"), icon = careIcon("medicine"), tone = SaharaTone.Plum, navigates = true) }
            if (medicines.isEmpty() && loaded && canEdit) item { SaharaSecondaryButton(t("Add a medicine", "दवा जोड़ें"), { onAdd("medicine") }, Modifier.fillMaxWidth(), icon = Icons.Default.Add, tone = SaharaTone.Plum) }
        }
        if (!clinician) medicineSection()
        if (!clinician && !family) summary()
        if (!clinician) {
            // View-only family see the patient's own check-in for today (they cannot change it).
            if (!canEdit) {
                CareTodayRules.todayCheckIn(items, dayStart)?.let { item(key = "today-checkin-seen") { CareRow(it, vm) } }
                DistressRules.today(items, selectedPerson, dayStart)?.let { item(key = "today-distress-seen") { CareRow(it, vm) } }
            }
            if (family) {
                item(key = "todo-title") { SaharaSectionHeader(t("To do", "करने के काम"), Modifier.testTag("today-todo-title"), tone = SaharaTone.Leaf, actionLabel = t("Open to do", "काम खोलें"), onAction = { onCare("To do") }, icon = careIcon("task")) }
                if (tasks.isEmpty() && loaded) item { SurfaceCard { Text(CareTodayRules.emptyText("tasks", canEdit, hindi), color = DoctorCardSub) } }
                itemsIndexed(tasks.take(3), key = { _, it -> it.id }) { i, it -> Box(Modifier.saharaStagger(5 + i)) { CareRow(it, vm) } }
                if (canEdit) item(key = "todo-add") { SaharaSecondaryButton(t("Add task", "काम जोड़ें"), { onAdd("task") }, Modifier.fillMaxWidth(), icon = Icons.Default.Add, tone = SaharaTone.Leaf) }
                item(key = "family-check") { FamilyCaregiverCheckCard(vm, personName.ifBlank { t("the patient", "मरीज़") }) }
            }
            item(key = "all-care-tools") { Box(Modifier.saharaStagger(6)) { TodayAllCareTools { onCare("All") } } }
            return@LazyColumn
        }
        // Doctor view (clinician) below: unchanged.
        item(key = "goals-of-care") { ActionCard(SaharaTone.Marigold.wash, Modifier.testTag("today-goals-of-care"), onClick = onTalk) {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = SaharaTone.Marigold.solid) { Icon(Icons.Default.Description, null, Modifier.padding(8.dp).size(22.dp), tint = SaharaTone.Marigold.onSolid) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Heading(t("Let's talk", "बात करें"), 22) }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Start talking", "बात शुरू करें"), Modifier.size(22.dp), tint = DoctorCardInk)
            }
        } }
        if (clinician && latest != null) item { ActionCard(Paper, onClick = { onRead(latest) }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t("Latest care note", "सबसे नया देखभाल नोट"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium); Text("${date(latest.published)} · ${latest.doctor}", style = MaterialTheme.typography.bodySmall); Text(ReviewWorkflow.label(latest), style = MaterialTheme.typography.labelSmall, color = RustText) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Read note", "नोट पढ़ें"), Modifier.size(20.dp)) }
        } }
        item { DailyCareShortcuts(onCalendar, onWorkflow, onLabs) }
        item { WorkflowSummary(vm, onWorkflow) }
        val checkin = CareTodayRules.todayCheckIn(items, dayStart)
        if (checkin != null) item { CareRow(checkin, vm) }
        DistressRules.today(items, selectedPerson, dayStart)?.let { item { CareRow(it, vm) } }
        item(key = "up-next-title") { SectionTitle(t("Up next", "आगे क्या है"), if (canEdit && visits.isNotEmpty()) t("Add visit", "मुलाक़ात जोड़ें") else null) { onAdd("visit") } }
        if (visits.isEmpty() && loaded) item(key = "up-next-empty") { ActionCard(SaharaSoftBlue, Modifier.fillMaxWidth(), onClick = { if (canEdit) onAdd("visit") else onCalendar() }) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Paper) { Icon(Icons.Default.Event, null, Modifier.padding(10.dp).size(24.dp), tint = DoctorAccent) }
                Spacer(Modifier.width(12.dp)); Text(CareTodayRules.emptyText("visits", canEdit, hindi), Modifier.weight(1f), fontSize = 16.sp, color = DoctorCardInk)
                Icon(if (canEdit) Icons.Default.Add else Icons.Default.ChevronRight, null, tint = DoctorAccent)
            }
        } }
        itemsIndexed(visits.take(2), key = { _, v -> "up-next-${v.id}" }) { index, v ->
            CareUpNextCard(v, CareTodayRules.whenLabel(v.due, today, hindi), if (index == 0) SaharaSoftBlue else SaharaApricot) { if (v.due == 0L) onCalendar() else onCalendarAt(v.due) }
        }
        medicineSection()
        item(key = "todo-title") { SectionTitle(t("To do", "करने के काम"), if (canEdit) t("Add task", "काम जोड़ें") else null) { onAdd("task") } }
        if (tasks.isEmpty() && loaded) item { SurfaceCard { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Checklist, null, Modifier.size(22.dp), tint = DoctorAccent); Spacer(Modifier.width(12.dp)); Text(CareTodayRules.emptyText("tasks", canEdit, hindi), color = DoctorCardSub) } } }
        items(tasks.take(4), key = { it.id }) { CareRow(it, vm) }
        if (tasks.size > 4) item { TextButton(onClick = { onCare("To do") }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(t("See all", "सब देखें")) } }
        item { SectionTitle(t("Journal", "डायरी"), t("Write a note", "नोट लिखें")) { onAdd("journal") } }
        val notes = items.filter { it.kind == "journal" && !PatientProfileCodec.isStructured(it.detail) && !SideEffectCodec.isStructured(it.detail) }
        if (notes.isEmpty() && loaded) item { SurfaceCard { Text(CareTodayRules.emptyText("journal", canEdit, hindi), color = DoctorCardSub) } }
        items(notes.take(2), key = { it.id }) { CareRow(it, vm) }
    }
}

@Composable fun Talk(vm: CareModel, clinician: Boolean, onRead: (CareVersion) -> Unit, onRequestConversation: (() -> Unit)? = null, onInteractionActiveChange: (Boolean) -> Unit = {}, doctorPresentation: Boolean = false, onDoctorBack: (() -> Unit)? = null, onDoctorStartNew: (() -> Unit)? = null, onDoctorCaptureChange: (Boolean) -> Unit = {}) { val t = careText();
    val draft by vm.draft.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle(); val seconds by vm.recordingSeconds.collectAsStateWithLifecycle(); val level by vm.amplitude.collectAsStateWithLifecycle()
    val recordingSaving by vm.recordingSaving.collectAsStateWithLifecycle()
    val recordingSaveError by vm.recordingSaveError.collectAsStateWithLifecycle()
    val recordingCanEdit by vm.canEdit.collectAsStateWithLifecycle()
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val recordingConsents by vm.recordingConsents.collectAsStateWithLifecycle()
    val transcribingPath by vm.transcribingPath.collectAsStateWithLifecycle()
    // Stop = transcribe: when the newest recording has Gemini consent, send it at once (no extra tap). The audio stays on the phone if it fails.
    var autoTranscribed by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(recordings.size, recordingConsents.size, recording, recordingSaving) {
        if (recording || recordingSaving || !BuildConfig.CLOUD_AUDIO_ENABLED) return@LaunchedEffect
        val file = recordings.maxByOrNull { it.lastModified() } ?: return@LaunchedEffect
        if (file.absolutePath == autoTranscribed || System.currentTimeMillis() - file.lastModified() > 10 * 60_000L) return@LaunchedEffect
        val consent = recordingConsents.firstOrNull { it.audioPath == file.absolutePath } ?: return@LaunchedEffect
        if (consent.transcriptionConsentAt <= 0) return@LaunchedEffect
        autoTranscribed = file.absolutePath
        vm.transcribeAudio(file.absolutePath, consent)
    }
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
    fun nextStep(value: Int) { if (!recordingSaving && recordingSaveError == null) { if (value >= 1 && step < 1 && !attendeesConsented) { vm.message.value = t("Recording is available once everyone listed has agreed.", "सूची के सभी लोगों की सहमति के बाद रिकॉर्डिंग शुरू हो सकती है।"); return }; focus.clearFocus(); topStep = ConversationPages.top(value); subPage = ConversationPages.sub(value) } }
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
    var transcriptionAccepted by rememberSaveable { mutableStateOf(BuildConfig.CLOUD_AUDIO_ENABLED) }
    // Who is present, and their agreement, is captured once in step 1 ("Who's here"). This dialog confirms it rather than asking again.
    val consentNames = attendees.map { it.name.trim() }.filter { it.isNotBlank() }
    val allParticipantsAgreed = attendeesConsented
    val preparationAccount = vm.accountNamespace
    val preparationStore = remember(context, vm.storageNamespace) { DoctorPreparationStore(context, vm.storageNamespace) }
    val preparationScope = remember(preparationAccount, draft.personId, draft.conversationId) { if (draft.personId.isBlank()) "" else DoctorPreparationStore.scope(preparationAccount, draft.personId, draft.conversationId) }
    var prepRows by remember(preparationScope) { mutableStateOf(if (preparationScope.isBlank()) emptyList<DoctorPreparationQuestion>() else runCatching { preparationStore.load(preparationScope) }.getOrDefault(emptyList())) }
    // Bumped when a goal answer is saved from the transcript page; the question and answer cards reload so they never save an older list over it.
    var prepReload by remember(preparationScope) { mutableIntStateOf(0) }
    val questionMatches = remember(draft.source, prepRows, step in 1..3) { if (step in 1..3 && prepRows.isNotEmpty()) LocalQuestionMatcher.match(draft.source, prepRows.map { CareQuestion(it.id, it.text, it.section) }) else null }
    val previousVersion = versions.firstOrNull { it.personId == draft.personId }
    // Walkthrough only: the seeded transcript is a labelled sample, never shown as transcribed.
    val walkthroughSample = remember(draft.personId, vm.demoMode) { if (vm.demoMode) doctorWalkthroughSample(draft.personId) else null }
    val useLine: (Int, CareSourceLine) -> Unit = { section, line ->
        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId) { t("Choose this person's conversation again.", "इस व्यक्ति की बातचीत फिर चुनें।") }; if (doctorPresentation) assignDoctorSource(vm, draft, section, line) else vm.edit(CareDraftAssist.assign(current, section, line)) }
        catch (e: IllegalArgumentException) { vm.message.value = e.message }
    }
    val addTypedAnswers = {
        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId) { t("Choose this person's conversation again.", "इस व्यक्ति की बातचीत फिर चुनें।") }
            var next = current
            doctorAnsweredQuestions(prepRows).forEach { q -> val existing = NoteRules.values(next)[q.section]; if (!existing.contains(q.answer)) next = CareDraftAssist.withSection(next, q.section, (if (existing.isBlank()) "${q.text} ${q.answer}" else existing + "\n" + "${q.text} ${q.answer}").take(4000)) }
            if (next != current) vm.edit(next) }
        catch (e: IllegalArgumentException) { vm.message.value = e.message }
    }
    val startConsentedRecording = {
        if (vm.canEdit.value && vm.localAudioRecordingAllowed && !vm.recordingSaving.value && vm.recordingSaveError.value == null) vm.startRecording(consentNames.joinToString("\n"), consentNames.toSet(), purposeAccepted, storageAccepted, accessAccepted, transcriptionAccepted, expected = recordingExpected)
        else vm.message.value = t("Recording unavailable", "रिकॉर्डिंग उपलब्ध नहीं")
    }
    val mic = rememberSaharaPermission(SaharaPermission.Microphone)
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
            .onFailure { playingNow = false; vm.message.value = t("That recording could not be played.", "वह रिकॉर्डिंग नहीं चल सकी।") }
    }
    // Stop = done: the new recording is linked and the doctor lands on the text, no "saved audio" page or extra Continue.
    var recordingCount by remember { mutableIntStateOf(recordings.size) }
    LaunchedEffect(recordings.size, recording, recordingSaving) {
        if (doctorPresentation && clinician && step == 1 && !recording && !recordingSaving && recordingSaveError == null && recordings.size > recordingCount) {
            recordings.maxByOrNull { it.lastModified() }?.let { f -> savedAudioPicked = setOf(f.absolutePath); runCatching { vm.attachDoctorRecording(f.absolutePath, doctorConversationIdentity(vm.accountNamespace, draft)) } }
            nextStep(2)
        }
        if (!recording && !recordingSaving) recordingCount = recordings.size
    }
    BackHandler(doctorPresentation && clinician && step > 0 && !recording && !dictating && !recordingSaving && recordingSaveError == null && !permissionDialog && !audienceBusy) { previousPage() }
    // Pages move with the app's shared motion in the direction of travel; each page keeps its own list position while the other fades out.
    AnimatedContent(step, transitionSpec = { SaharaMotion.transform(if (targetState >= initialState) SaharaMotion.Direction.Forward else SaharaMotion.Direction.Back) }, label = "conversation-page") { step ->
    val doctorCapture = doctorPresentation && clinician && step == 1
    val scroll = rememberLazyListState()
    LaunchedEffect(buildSection) { scroll.scrollToItem(0) }
    // Tapping an answer jumps to its transcript line and highlights it.
    var highlightLine by remember { mutableStateOf<Int?>(null) }
    var editingTranscript by rememberSaveable { mutableStateOf(false) }
    val lineRequesters = remember { mutableMapOf<Int, BringIntoViewRequester>() }
    val jumpScope = rememberCoroutineScope()
    // Items above a step's own content: heading, the optional back link, then the stepper.
    val stepContentIndex = 2 + if (doctorPresentation && onDoctorBack != null) 1 else 0
    val showLine: (CareSourceLine) -> Unit = { line -> focus.clearFocus(); editingTranscript = false; highlightLine = line.number
        jumpScope.launch { scroll.animateScrollToItem(stepContentIndex); withFrameNanos { }; lineRequesters[line.number]?.bringIntoView() } }
    fun androidx.compose.foundation.lazy.LazyListScope.captureItem(content: @Composable () -> Unit) {
        item { Box(Modifier.fillMaxWidth().padding(horizontal = if (doctorCapture) 20.dp else 0.dp)) { content() } }
    }
    if (doctorCapture && captureMode == "Record") DoctorRecordStage(mode = captureMode, onMode = { captureMode = it }, recording = recording, saving = recordingSaving, saveError = recordingSaveError != null, onRetry = vm::retrySavingRecording, level = level, seconds = seconds.toLong(),
        recordEnabled = recordingCanEdit && recordingSaveError == null && vm.localAudioRecordingAllowed && !recordingSaving && draft.personId.isNotBlank() && !dictating && (!busy || recording),
        onRecord = { recordingExpected = doctorConversationIdentity(vm.accountNamespace, draft); purposeAccepted = false; storageAccepted = false; accessAccepted = false; transcriptionAccepted = BuildConfig.CLOUD_AUDIO_ENABLED; permissionDialog = true },
        onStop = { vm.stopRecording() }, onBack = onDoctorBack, backEnabled = !recording && !dictating && !permissionDialog && !audienceBusy && !recordingSaving && recordingSaveError == null,
        demo = vm.demoMode, localRecordingAllowed = vm.localAudioRecordingAllowed && recordingCanEdit && !recordingSaving && recordingSaveError == null, testRecord = vm.demoMode && DoctorTestProfiles.contains(draft.personId),
        menuEnabled = !recording && !dictating && !permissionDialog && !audienceBusy,
        onStartNew = if (draft.source.isNotBlank() && onDoctorStartNew != null) ({ if (!recordingSaving && recordingSaveError == null && !busy && !recording && !dictating && !permissionDialog && !audienceBusy) onDoctorStartNew() }) else null,
        onBackToWho = { nextStep(0) }, backToWhoEnabled = !recordingSaving && recordingSaveError == null && !recording && !dictating, audioCount = if (recordingSaving || recordingSaveError != null) 0 else recordings.size,
        checklist = { DoctorGoalsChecklist(prepRows, questionMatches, onDark = true) }) {
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
        if (!doctorCapture) item { Heading(if (clinician) t("Conversation", "बातचीत") else t("Care notes", "देखभाल नोट")) }
        if (doctorPresentation && !doctorCapture && onDoctorBack != null) item { TextButton(onClick = onDoctorBack, enabled = !recordingSaving && recordingSaveError == null && !recording && !dictating && !audienceBusy) { Text(t("Back", "वापस")) } }
        if (clinician) {
            if (!doctorCapture) item { ConversationStepper(topStep) }
            when (step) {
                0 -> {
                    if (attendeeScope.isBlank()) item { Text(t("Choose a person first.", "पहले व्यक्ति चुनें।"), fontSize = 13.sp, lineHeight = 18.sp, color = DoctorCardSub) }
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
                    item { Button(onClick = { nextStep(1) }, enabled = attendeesConsented && !recordingSaving && recordingSaveError == null && !preparationBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Continue", "आगे बढ़ें")); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                }
                1 -> {
                    if (doctorPresentation) item { DoctorCaptureHeader(captureMode, { captureMode = it }, recording, dictating, level, seconds.toLong(), vm.demoMode, onDoctorBack, !recording && !dictating && !permissionDialog && !audienceBusy && !recordingSaving && recordingSaveError == null, localRecordingAllowed = vm.localAudioRecordingAllowed && recordingCanEdit && !recordingSaving && recordingSaveError == null, testRecord = vm.demoMode && DoctorTestProfiles.contains(draft.personId)) }
                    if (recordingSaving || recordingSaveError != null) captureItem { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if(recordingSaving) t("Saving…", "सहेज रहे हैं…") else t("Couldn't save. Try again.", "सेव नहीं हुआ। फिर कोशिश करें।"))
                        if (recordingSaveError != null) OutlinedButton(onClick = vm::retrySavingRecording, enabled = !recordingSaving, modifier = Modifier.heightIn(min = 48.dp).testTag("retry-saving-audio")) { Text(t("Retry saving audio", "ऑडियो फिर से सहेजें")) }
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
                    if (doctorPresentation && prepRows.isNotEmpty()) captureItem { DoctorGoalsChecklist(prepRows, questionMatches) }
                    if (!doctorPresentation || captureMode == "Record" || recording) captureItem { Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(t("Conversation audio", "बातचीत का ऑडियो"), fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        if (recording) {
                            val meter by animateFloatAsState(level.coerceIn(0f, 1f), tween(120), label = "microphone level")
                            Text(t("Recording · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", "रिकॉर्डिंग · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"), fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                            LinearProgressIndicator(progress = { meter }, modifier = Modifier.fillMaxWidth(), color = DoctorAccent)
                        }
                        Button(onClick = { if (recording) vm.stopRecording() else {
                            recordingExpected = if (doctorPresentation) doctorConversationIdentity(vm.accountNamespace,draft) else null
                            purposeAccepted = false; storageAccepted = false; accessAccepted = false; transcriptionAccepted = BuildConfig.CLOUD_AUDIO_ENABLED
                            permissionDialog = true
                        } }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = recordingCanEdit && recordingSaveError == null && vm.localAudioRecordingAllowed && !recordingSaving && draft.personId.isNotBlank() && !dictating && (!busy || recording)) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text(if (recordingSaving) t("Saving…", "सहेज रहे हैं…") else if (!vm.localAudioRecordingAllowed) t("Recording unavailable", "रिकॉर्डिंग उपलब्ध नहीं") else if (recording) t("Stop", "रोकें") else t("Record conversation", "बातचीत रिकॉर्ड करें")) }
                    } } }
                    items(if(recordingSaving || recordingSaveError != null || doctorPresentation) emptyList() else recordings, key = { it.absolutePath }) { file ->
                        Column(Modifier.padding(horizontal = if (doctorCapture) 20.dp else 0.dp)) {
                        val consent = recordingConsents.firstOrNull { it.audioPath == file.absolutePath }
                        AudioPlayback(
                            path = file.absolutePath,
                            consent = consent,
                            transcribing = transcribingPath == file.absolutePath,
                            canTranscribe = BuildConfig.CLOUD_AUDIO_ENABLED && consent?.let { it.transcriptionConsentAt > 0 && it.noticeVersion >= 2 } == true && draft.source.isBlank() && !busy && !dictating && !recording,
                            onTranscribe = { consent?.let { vm.transcribeAudio(file.absolutePath, it) } },
                            onDelete = { vm.deleteAudio(file.absolutePath) },
                        )
                        if(doctorPresentation) OutlinedButton(onClick={vm.attachDoctorRecording(file.absolutePath,doctorConversationIdentity(vm.accountNamespace,draft))},enabled=!busy&&!recording&&!dictating,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(t("Link recording", "यह रिकॉर्डिंग जोड़ें"))}
                        if (!vm.demoMode) OfflineConversationTranscription(file = file, identity = "${vm.accountNamespace}:${draft.personId}", consent = consent,
                            enabled = !busy && !dictating && !recording && draft.source.isBlank(),
                            onUse = { vm.useOfflineTranscript(file.absolutePath, it) })
                        if (!vm.demoMode && vm.cloud != null) CareRemoteTranscriptPanel(
                            source = draft.source, identity = draft.personId, recording = file, recordingConsent = consent,
                            token = { vm.cloud.transcriptionToken() }, onSaveConsent = vm::rememberTranscriptionConsent,
                            onAppend = { source ->
                                val current = vm.draft.value
                                if (current.personId == draft.personId && vm.selected.value == draft.personId && current.source == draft.source && source.length <= NoteRules.MAX_SOURCE_CHARS) vm.edit(current.copy(source = source))
                                else vm.message.value = t("The conversation changed. Check the saved recording and try again.", "बातचीत बदल गई। सेव की गई रिकॉर्डिंग जाँचें और फिर कोशिश करें।")
                            },
                        )
                        }
                    }
                    captureItem { Button(onClick = { nextStep(2) }, enabled = !recordingSaving && recordingSaveError == null && !recording && !dictating, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Continue", "आगे बढ़ें")); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                    if (doctorPresentation && draft.source.isNotBlank() && onDoctorStartNew != null) captureItem { TextButton(onClick = onDoctorStartNew, enabled = !recordingSaving && recordingSaveError == null && !busy && !recording && !dictating && !permissionDialog && !audienceBusy) { Text(t("Start new", "नया शुरू करें")) } }
                
                    captureItem { TextButton(onClick = { nextStep(0) }, enabled = !recordingSaving && recordingSaveError == null && !recording && !dictating, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back", "वापस")) } }
                }
                2 -> {
                    val isSample = doctorTranscriptIsSample(vm.demoMode, draft.source, walkthroughSample)
                    val origin = doctorTranscriptOrigin(isSample, draft.transcribedAt, draft.transcriptModel)
                    item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // The doctor flow's recordings are transcribed here, on this phone, once the language pack is present; otherwise the doctor types.
                        if (doctorPresentation && draft.source.isBlank() && !recordingSaving && recordingSaveError == null) recordings.filter { it.absolutePath in savedAudioPicked }.ifEmpty { listOfNotNull(recordings.maxByOrNull { it.lastModified() }) }.forEach { file -> key(file.absolutePath) {
                            // Gemini first (Whisper on this phone if Gemini fails) — runs on its own when this step opens, so nothing has to be typed.
                            val clipConsent = recordingConsents.firstOrNull { it.audioPath == file.absolutePath }
                            LaunchedEffect(file.absolutePath, clipConsent != null) {
                                if (BuildConfig.CLOUD_AUDIO_ENABLED && clipConsent != null && autoTranscribed != file.absolutePath && vm.draft.value.source.isBlank() && transcribingPath == null) { autoTranscribed = file.absolutePath; vm.transcribeAudio(file.absolutePath, clipConsent) }
                            }
                            if (clipConsent != null) Button(onClick = { vm.transcribeAudio(file.absolutePath, clipConsent) }, enabled = BuildConfig.CLOUD_AUDIO_ENABLED && transcribingPath == null && !busy && !dictating && !recording, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-gemini-transcribe")) {
                                if (transcribingPath == file.absolutePath) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)); Text(t("Writing it down…", "लिख रहे हैं…")) }
                                else { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Write it down", "लिख दें")) }
                            }
                        } }
                        DoctorGoalsChecklist(prepRows, questionMatches)
                        if (dictating) SurfaceCard { Text(t("Conversation", "बातचीत"), fontWeight = FontWeight.Bold); SelectionContainerText(draft.source) }
                        else if (draft.source.isNotBlank() && !editingTranscript) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (isSample) t("Practice text", "अभ्यास लिखाई") else t("Written text", "बातचीत की लिखाई"), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f))
                                TextButton(onClick = { highlightLine = null; editingTranscript = true }, enabled = !busy && !recording, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-edit")) { Text(t("Edit", "बदलें"), color = DoctorAccent) }
                            }
                            Text(origin, fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardFaint, modifier = Modifier.testTag("transcript-origin"))
                            DoctorTranscriptLines(draft.source, highlightLine) { n -> lineRequesters.getOrPut(n) { BringIntoViewRequester() } }
                        } else {
                            CareField(value = draft.source, onValueChange = { if (it.length <= NoteRules.MAX_SOURCE_CHARS) { val current = vm.draft.value; if (current.personId == draft.personId) { editingTranscript = true; vm.edit(current.copy(source = it)) } } }, label = if (isSample) t("Practice text", "अभ्यास लिखाई") else t("Conversation in writing", "बातचीत की लिखाई"), placeholder = t("Type or speak", "लिखें या बोलें"), minLines = 5)
                            if (draft.source.isNotBlank()) TextButton(onClick = { focus.clearFocus(); editingTranscript = false }, modifier = Modifier.heightIn(min = 48.dp).testTag("transcript-done")) { Text(t("Done", "हो गया"), color = DoctorAccent) }
                        }
                    } }
                    item { DoctorTranscriptGoals(prepRows, questionMatches, !busy && !recording && !dictating && !recordingSaving && recordingSaveError == null, onSave = { id, answer -> if (preparationScope.isNotBlank() && !vm.recordingSaving.value && vm.recordingSaveError.value == null && canEditCareContext(vm, draft.personId, preparationAccount) && vm.draft.value.conversationId == draft.conversationId) runCatching { val next = prepRows.map { if (it.id == id) it.copy(answer = answer.take(DOCTOR_PREPARATION_MAX_ANSWER_CHARS)) else it }; preparationStore.save(preparationScope, next); prepRows = next; prepReload++ } }, onBackToWho = { nextStep(0) }, source = draft.source, onShowLine = showLine) }
                    // Suggestions are never saved silently: only answers the doctor accepted, edited or typed move on.
                    captureItem { Button(onClick = { buildSection = 0; buildOpen = false; nextStep(3) }, enabled = !recordingSaving && recordingSaveError == null && draft.source.isNotBlank() && !recording && !dictating, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Continue", "आगे बढ़ें")); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                    item { TextButton(onClick = { nextStep(1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back", "वापस")) } }
                }
                3 -> {
                    if (!buildOpen) {
                    questionMatches?.let { result -> items(result.answered, key = { "match-${it.question.id}" }) { qa -> Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(qa.question.text, fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        qa.matches.forEach { m -> Text(m.quote, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk); TextButton(onClick = { useLine(qa.question.section, m.line) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Use this line", "यह पंक्ति डालें")) } }
                    } } } }
                    if (preparationScope.isNotBlank()) item { DoctorPreparationAnswers(preparationStore, preparationScope, !busy && !recording && !dictating && !recordingSaving && recordingSaveError == null, canSave = { !vm.recordingSaving.value && vm.recordingSaveError.value == null && canEditCareContext(vm, draft.personId, preparationAccount) && vm.draft.value.conversationId == draft.conversationId }, onChange = { prepRows = it }, reload = prepReload) }
                    item { OutlinedButton(onClick = addTypedAnswers, enabled = !busy && doctorAnsweredQuestions(prepRows).isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Add typed answers", "मेरे लिखे जवाब जोड़ें")) } }
                    item { OutlinedButton(onClick = { buildSection = 0; buildOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Edit note", "नोट बदलें")) } }
                    item { Button(onClick = { nextStep(4) }, enabled = draft.source.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Continue", "आगे बढ़ें")); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Filled.ArrowForward, null) } }
                    item { TextButton(onClick = { nextStep(2) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back", "वापस")) } }
                    } else {
                    item { SurfaceCard(Peach) {
                        Text(if (draft.transcribedAt > 0 && draft.transcriptProvider == OfflineSpeechModel.provider) t("Text made on this phone · not checked by a doctor", "फ़ोन पर बनी लिखाई · डॉक्टर ने नहीं जाँची") else if (draft.transcribedAt > 0) t("Text made online · not checked by a doctor", "ऑनलाइन बनी लिखाई · डॉक्टर ने नहीं जाँची") else t("Draft · not checked by a doctor", "ड्राफ़्ट · डॉक्टर ने नहीं जाँचा"), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium)
                    } }
                    item { CareSourcePicker(draft, buildSection) { section, line ->
                        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId) { t("Choose this person's conversation again.", "इस व्यक्ति की बातचीत फिर चुनें।") }; if (doctorPresentation) assignDoctorSource(vm, draft, section, line) else vm.edit(CareDraftAssist.assign(current, section, line)) }
                        catch (e: IllegalArgumentException) { vm.message.value = e.message }
                    } }
                    if (!vm.demoMode && vm.cloud != null) item { CareRemoteAssistPanel(source = draft.source, identity = draft.personId, token = { vm.cloud.transcriptionToken() }, onUseDraft = { assisted ->
                        try { val current = vm.draft.value; require(current.personId == draft.personId && vm.selected.value == draft.personId); vm.edit(CareDraftAssist.applyAssisted(current, assisted)) }
                        catch (e: IllegalArgumentException) { vm.message.value = e.message }
                    }) }
                    if (NoteRules.values(draft).all { it.isBlank() }) item { TextButton(onClick = vm::organise) { Text(t("Organise", "व्यवस्थित करें")) } }
                    item { val update: (String) -> Unit = { value -> if (value.length <= 4000) { val current = vm.draft.value; if (current.personId == draft.personId) vm.edit(CareDraftAssist.withSection(current, buildSection, value)) } }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (doctorPresentation && buildSection == 0) DoctorGoalsReview(draft) { goal -> updateDoctorGoalFromUi(vm,draft,goal) }
                            CareField(value = NoteRules.values(draft)[buildSection], onValueChange = update, label = noteSectionLabel(NoteRules.labels[buildSection], careHindi()), minLines = 2)
                            if (!doctorPresentation) CareSourceLinks(draft.source, NoteRules.values(draft)[buildSection])
                        } }
                    item { CareSectionNav(buildSection, NoteRules.labels.size, { focus.clearFocus(); if (buildSection == 0) buildOpen = false else buildSection -= 1 }, { focus.clearFocus(); if (buildSection >= NoteRules.labels.lastIndex) { buildSection = 0; buildOpen = false; nextStep(4) } else buildSection += 1 }) }
                
                    }
                }
                4 -> {
                    items(NoteRules.labels.withIndex().toList(), key = { "preview-${it.index}" }) { (index, label) -> Surface(shape = RoundedCornerShape(20.dp), color = DoctorCardReport, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(label, fontFamily = SaharaTitleFont, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                        Text(NoteRules.values(draft)[index].ifBlank { t("Not recorded", "दर्ज नहीं") }, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk)
                        TextButton(onClick = { buildSection = index; buildOpen = true; nextStep(3) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Edit $label", "$label बदलें")) }
                    } } }
                    item { InfoDisclosure(t("Exact source text", "मूल लिखाई"), Paper) { SelectionContainerText(draft.source) } }
                    item { Button(onClick = { nextStep(5) }, enabled = draft.source.isNotBlank() && NoteRules.values(draft).any { it.isNotBlank() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Continue", "आगे बढ़ें")) } }
                    item { OutlinedButton(onClick = { buildSection = 0; buildOpen = true; nextStep(3) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Back", "वापस")) } }
                }
                else -> {
                    if (doctorPresentation) {
                        item { DoctorRecorderLabel(vm,draft) }
                        item { DoctorConversationAudience(vm,draft) {audienceBusy=it} }
                    }
                    if (vm.cloud == null) item { CareField(value = doctor, onValueChange = { doctor = it.take(100) }, label = t("Doctor's name", "डॉक्टर का नाम"), singleLine = true) }
                    item { CheckLine(reviewed, { reviewed = it }, t("I checked every section against the conversation. This records what was discussed, without adding treatment advice.", "मैंने हर हिस्सा बातचीत से मिलाया है। इसमें वही दर्ज है जिस पर बात हुई, इलाज की कोई सलाह नहीं जोड़ी गई।")) }
                    item { TextButton(onClick = { nextStep(4) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back", "वापस")) } }
                    item { Button(onClick = { vm.publish(doctor, reviewed) { buildOpen = false; nextStep(0); reviewed = false } }, enabled = !recordingSaving && recordingSaveError == null && reviewed && (vm.cloud != null || doctor.isNotBlank()) && !busy && draft.source.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("conversation-publish")) { Text(if (vm.cloud == null) t("Publish", "प्रकाशित करें") else t("Send to doctor", "डॉक्टर को भेजें")) } }
                }
            }
        } else {
            item { OutlinedButton(onClick = { onRequestConversation?.invoke() ?: vm.saveItem("task", t("Ask for a goals-of-care conversation", "देखभाल के लक्ष्यों पर बातचीत माँगें"), t("Discuss what matters and questions for the care team.", "जो ज़रूरी है और देखभाल टीम से सवाल, उन पर बात करें।")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(if (onRequestConversation == null) t("Add reminder", "रिमाइंडर जोड़ें") else t("Ask to talk", "बात करने के लिए कहें")) } }
        }
        if (!doctorCapture && !clinician) {
        if (clinician) item { SectionTitle(t("Saved notes", "सहेजे नोट")) }
        if (versions.isEmpty()) item { Text(t("No care notes yet", "अभी कोई देखभाल नोट नहीं"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(versions, key = { it.id }) { note -> Card(onClick = { onRead(note) }, colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Rust); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t("Version ${note.version}", "संस्करण ${note.version}") + if (note.id == versions.first().id) t(" · Latest", " · नया") else t(" · Earlier", " · पुराना"), fontWeight = FontWeight.Bold); Text("${date(note.published)} · ${note.doctor}", style = MaterialTheme.typography.bodySmall); Text(ReviewWorkflow.label(note), style = MaterialTheme.typography.labelSmall, color = Rust) }; Icon(Icons.AutoMirrored.Filled.ArrowForward, t("Read note", "नोट पढ़ें")) }
        } }
        }
    }
    }
    val quickConsent = doctorPresentation
    if (permissionDialog && vm.localAudioRecordingAllowed) AlertDialog(onDismissRequest = { permissionDialog = false }, title = { Text(t("Record this talk?", "यह बातचीत रिकॉर्ड करें?")) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (consentNames.isEmpty()) t("Name everyone present first.", "रिकॉर्ड करने से पहले मौजूद सभी के नाम लिखें।") else t("Everyone named in “Who's here” has agreed: ${consentNames.joinToString(", ")}.", "“कौन मौजूद है” में लिखे सभी लोग राज़ी हैं: ${consentNames.joinToString(", ")}।"), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            if (quickConsent) Text(t("The audio stays on this phone.", "ऑडियो इसी फ़ोन पर रहता है।"), style = MaterialTheme.typography.bodySmall) else CheckLine(purposeAccepted, { purposeAccepted = it }, t("We understand this recording is to write and check a note of this talk.", "हम समझते हैं कि यह रिकॉर्डिंग इस बातचीत का नोट लिखने और जाँचने के लिए है।"))
            if (!quickConsent) CheckLine(storageAccepted, { storageAccepted = it }, if (!vm.demoMode && BuildConfig.CLOUD_AUDIO_ENABLED) t("We understand the audio stays private on this phone, unless someone shares it or separately agrees below to send it online to turn speech into text.", "हम समझते हैं कि ऑडियो इसी फ़ोन पर निजी रहता है, जब तक कोई इसे साझा न करे या नीचे इसे लिखाई के लिए ऑनलाइन भेजने पर अलग से राज़ी न हो।") else t("We understand the recording stays private on this phone.", "हम समझते हैं कि रिकॉर्डिंग इसी फ़ोन पर निजी रहती है।"))
            if (!quickConsent) CheckLine(accessAccepted, { accessAccepted = it }, t("We understand people with access to this person's phone or shared records may be able to read the written note.", "हम समझते हैं कि इस व्यक्ति के फ़ोन या साझा रिकॉर्ड तक पहुँच वाले लोग लिखा नोट पढ़ सकते हैं।"))
            if (BuildConfig.CLOUD_AUDIO_ENABLED) {
            if (!quickConsent) { HorizontalDivider(); Text(t("Online transcription", "ऑनलाइन लिखाई"), fontWeight = FontWeight.Bold) }
            CheckLine(transcriptionAccepted, { transcriptionAccepted = it }, t("Use Google Gemini to write it down", "Google Gemini से लिखवाएँ"))
            }
        }
    }, confirmButton = {
        TextButton(onClick = { if (vm.canEdit.value && vm.localAudioRecordingAllowed && !vm.recordingSaving.value && vm.recordingSaveError.value == null) { if (quickConsent) { purposeAccepted = true; storageAccepted = true; accessAccepted = true }; permissionDialog = false; mic.request(startConsentedRecording) } }, enabled = recordingCanEdit && recordingSaveError == null && !recordingSaving && vm.localAudioRecordingAllowed && allParticipantsAgreed && (quickConsent || (purposeAccepted && storageAccepted && accessAccepted)), modifier = Modifier.testTag("start-recording-confirm")) { Text(t("Start recording", "रिकॉर्डिंग शुरू करें")) }
    }, dismissButton = { TextButton(onClick = { permissionDialog = false }) { Text(t("Cancel", "रद्द करें")) } })
}

@Composable fun AudioPlayback(path: String, consent: RecordingConsent?, transcribing: Boolean, canTranscribe: Boolean, onTranscribe: () -> Unit, onDelete: () -> Unit) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }; var playing by remember(path) { mutableStateOf(false) }
    var confirmDelete by remember(path) { mutableStateOf(false) }
    val context = LocalContext.current; val t = careText()
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
            } catch (_: Exception) { player?.release(); player = null; playing = false; android.widget.Toast.makeText(context, t("Couldn't play this recording.", "यह रिकॉर्डिंग नहीं चल सकी।"), android.widget.Toast.LENGTH_LONG).show() }
        }) { Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) t("Stop playback", "रोकें") else t("Play recording", "रिकॉर्डिंग चलाएँ")) }
        Column(Modifier.weight(1f)) {
            Text(t("Conversation · ", "बातचीत · ") + Instant.ofEpochMilli(File(path).lastModified()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, HH:mm")), fontSize = 14.sp)
            Text(if (consent == null) t("Consent details unavailable", "सहमति की जानकारी नहीं मिली") else t("Consent recorded · ${RecordingConsentRules.names(consent.participants).joinToString()}", "सहमति दर्ज · ${RecordingConsentRules.names(consent.participants).joinToString()}"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (BuildConfig.CLOUD_AUDIO_ENABLED && consent != null) Text(when {
                consent.transcriptionConsentAt <= 0 -> t("Not agreed to online speech-to-text", "ऑनलाइन लिखाई की सहमति नहीं")
                consent.noticeVersion < 2 -> t("Old agreement: record a new demo", "पुरानी सहमति: नया डेमो रिकॉर्ड करें")
                else -> t("Agreed to online speech-to-text (demo)", "ऑनलाइन लिखाई की सहमति दी (डेमो)")
            }, style = MaterialTheme.typography.labelSmall, color = if (consent.transcriptionConsentAt > 0 && consent.noticeVersion >= 2) MaterialTheme.colorScheme.secondary else Rust)
        }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.DeleteOutline, t("Delete recording", "रिकॉर्डिंग मिटाएँ")) }
    }
        if (BuildConfig.CLOUD_AUDIO_ENABLED && (consent?.transcriptionConsentAt ?: 0L) > 0L) {
            Spacer(Modifier.height(10.dp))
            Button(onClick = onTranscribe, enabled = canTranscribe && !transcribing, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                if (transcribing) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
                else { Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                Text(if (transcribing) t("Writing it down…", "लिख रहे हैं…") else t("Write it down", "लिख दें"))
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(t("Delete this recording?", "यह रिकॉर्डिंग मिटाएँ?")) },
        confirmButton = { TextButton(onClick = { player?.release(); player = null; playing = false; confirmDelete = false; onDelete() }) { Text(t("Delete recording", "रिकॉर्डिंग मिटाएँ")) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Keep recording", "रिकॉर्डिंग रखें")) } })
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
    val context = LocalContext.current; val t = careText()
    val scope = rememberCoroutineScope()
    var exporting by rememberSaveable { mutableStateOf<String?>(null) }
    val familyOnly = vm.cloud == null && vm.walkthroughRole == "Family"
    val signatureStore = remember(vm.storageNamespace) { NoteSignatureStore.of(context, vm.storageNamespace) }; val signatureDraft = remember(note.id) { SignatureDraft() }
    var signaturesRead by remember { mutableIntStateOf(0) }
    val signatures by produceState(emptyList<NoteSignature>(), note.id, signaturesRead, confirmations) { value = withContext(Dispatchers.IO) { NoteSignatureRules.forVersion(runCatching { signatureStore.list(note.personId) }.getOrDefault(emptyList()), note, confirmations) } }
    LaunchedEffect(patientConfirmation?.id, owner) { relationship = if (familyOnly) "Family / carer" else if (vm.cloud != null) if (owner) "Patient" else "Family / carer" else if (patientConfirmation == null) "Patient" else "Family / carer"; reviewed = false }
    val saveNote = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = exporting; exporting = null
        if (uri != null && text != null) scope.launch {
            try {
                withContext(Dispatchers.IO) { (context.contentResolver.openOutputStream(uri) ?: error("File unavailable")).bufferedWriter().use { it.write(text) } }
                vm.message.value = t("Care note saved", "देखभाल नोट सहेज लिया")
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; vm.message.value = t("Couldn't save the copy. Your care note is still here. Try again.", "कॉपी सहेजी नहीं जा सकी। आपका देखभाल नोट यहीं है। फिर कोशिश करें।") }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        LazyColumn(Modifier.fillMaxWidth().imePadding(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Heading(if (note.hashScheme == DoctorConversationContext.HASH_SCHEME) t("Conversation notes", "बातचीत के नोट") else t("Goals of care", "देखभाल के लक्ष्य")); Text("${note.personName} · " + t("Version ${note.version}", "संस्करण ${note.version}")); Text(t("Reviewer: ${note.doctor} · ${date(note.published)}", "जाँचने वाले: ${note.doctor} · ${date(note.published)}"), style = MaterialTheme.typography.bodySmall); Text(ReviewWorkflow.label(note, note.id in verifiedReviews), style = MaterialTheme.typography.labelMedium, color = Rust) }
            if(note.hashScheme==DoctorConversationContext.HASH_SCHEME) item { DoctorNoteReaderBody(vm, note) }
            if (vm.cloud != null) item { OutlinedButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sharedCareNoteUrl(note)), t("Share exact care-note link", "इसी देखभाल नोट का लिंक भेजें"))) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCode, null); Spacer(Modifier.width(8.dp)); Text(t("Share link", "लिंक भेजें")) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { exporting = NoteRules.text(note, confirmations); saveNote.launch("Goals-of-care-v${note.version}.txt") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Save copy", "कॉपी सहेजें")) }
                FilledTonalButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, NoteRules.text(note, confirmations)), t("Share care note", "देखभाल नोट भेजें"))) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Gold, contentColor = Ink)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Share", "भेजें")) }
            } }
            item { CareReadAloudControl(NoteRules.labels.zip(NoteRules.values(note)).filter { it.second.isNotBlank() }.joinToString("\n\n") { "${it.first}. ${it.second}" }, note.id) }
            if (note.hashScheme != DoctorConversationContext.HASH_SCHEME) items(NoteRules.labels.zip(NoteRules.values(note)), key = { it.first }) { (label, value) -> SurfaceCard(if (label == "What matters") Gold else Paper) { Text(noteSectionLabel(label, careHindi()), fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); SelectionContainerText(value.ifBlank { t("Not recorded", "दर्ज नहीं") }); CareSourceLinks(note.source, value) } }
            if (note.hashScheme != DoctorConversationContext.HASH_SCHEME) item { SurfaceCard { Text(t("Original conversation", "असली बातचीत"), fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); SelectionContainerText(note.source) } }
            if (!vm.demoMode) item { CareOfflineTranslationPanel(source = NoteRules.labels.zip(NoteRules.values(note)).filter { it.second.isNotBlank() }.joinToString("\n\n") { "${it.first}\n${it.second}" }, versionId = note.id) }
            items(confirmations, key = { it.id }) { confirmation -> SurfaceCard(if (confirmation.relationship == "Patient") Sage else Mint) {
                Text(if (confirmation.relationship == "Patient") t("Patient confirmed this version", "मरीज़ ने इस संस्करण की पुष्टि की") else t("Family / carer confirmation", "परिवार / देखभाल करने वाले की पुष्टि"), fontWeight = FontWeight.Bold)
                Text("${confirmation.name} · ${date(confirmation.at)}")
                signatures.filter { it.ackId == confirmation.id }.forEach { NoteSignedBy(it, signatureStore) }
            } }
            if (versions.firstOrNull()?.id == note.id) {
                item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); Heading(if (patientConfirmation == null) t("Patient's review", "मरीज़ की जाँच") else t("Family's review", "परिवार की जाँच"), 26) }
                item { CareField(value = name, onValueChange = { name = it.take(100) }, label = t("Your name", "आपका नाम"), singleLine = true) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Patient", "Family / carer").forEach { role -> FilterChip(selected = relationship == role, onClick = { relationship = role }, enabled = (vm.cloud == null || (role == "Patient") == owner) && !(familyOnly && role == "Patient") && if (role == "Patient") patientConfirmation == null else patientConfirmation != null, label = { Text(if (role == "Patient") t("Patient", "मरीज़") else t("Family / carer", "परिवार / देखभाल करने वाले")) }) } } }
                item { CheckLine(reviewed, { reviewed = it }, t("I have read this version. This is not a legal signature, consent for treatment or a treatment decision. I can ask my doctor to change it later.", "मैंने यही संस्करण पढ़ा है। यह क़ानूनी हस्ताक्षर, इलाज की सहमति या इलाज का फ़ैसला नहीं है। मैं बाद में डॉक्टर से इसे बदलवा सकता/सकती हूँ।")) }
                item { NoteSignatureCapture(signatureDraft, enabled = !busy) { vm.message.value = it } }
                val alreadyConfirmed = confirmations.any { it.relationship == relationship && it.name.equals(name.trim(), ignoreCase = true) && (vm.cloud == null || it.id in authenticatedAcks) }
                item { Button(onClick = {
                    val signed = signatureDraft.image(); var signatureFailed = false
                    if (signed != null) vm.confirm(note, name, relationship, reviewed, onSaved = { ack -> if (!saveNoteSignature(context, vm.storageNamespace, note, ack, signed.first, signed.second)) signatureFailed = true }) {
                        reviewed = false; name = ""; signatureDraft.clear(); signaturesRead++
                        if (signatureFailed) vm.message.value = t("Your confirmation is saved, but the signature could not be saved on this phone.", "आपकी पुष्टि सहेजी गई, पर हस्ताक्षर इस फ़ोन पर सहेजा नहीं जा सका।")
                    }
                }, enabled = reviewed && name.isNotBlank() && signatureDraft.ready && !busy && !alreadyConfirmed && (relationship == "Patient" || patientConfirmation != null), modifier = Modifier.fillMaxWidth()) { Text(if (alreadyConfirmed) t("Confirmation already saved", "पुष्टि पहले से सहेजी है") else t("Sign and confirm", "हस्ताक्षर करके पुष्टि करें")) } }
            }
        }
    }
}

/**
 * The Care tab: every care page in one plain grouped list (icon, name, one short line, chevron). Each row opens its page and Back
 * returns here. A row whose page is not passed in (the doctor's embedded view) is left out. [title] is "All care tools" in the family app.
 */
@Composable fun CareList(vm: CareModel, onAdd: (String) -> Unit, onScanPrescription: () -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, initialFilter: String = "All", onReadNote: (CareVersion) -> Unit = {}, onSymptoms: () -> Unit = {}, onCalendarAt: (Long) -> Unit = { onCalendar() },
    onCommunity: (() -> Unit)? = null, onDevices: (() -> Unit)? = null, onExit: (() -> Unit)? = null, onDocuments: (() -> Unit)? = null, onCarePlan: (() -> Unit)? = null, title: String? = null) {
    val entries by vm.items.collectAsStateWithLifecycle(); val everything by vm.allItems.collectAsStateWithLifecycle(); val versions by vm.versions.collectAsStateWithLifecycle()
    val listState = rememberLazyListState(); val target = CareModules.moduleFor(initialFilter)
    val shown = entries.filter { !CareEntryCodec.isStructured(it.detail) || isDoctorStructuredVisit(it) }
    val (currentMeds, pastMeds) = CareModules.splitMedicines(everything.filter { !CareEntryCodec.isStructured(it.detail) }, shown)
    val now = remember(entries) { System.currentTimeMillis() }; val (upcoming, pastVisits) = CareModules.splitVisits(shown.filter { it.kind == "visit" }, now)
    val tasks = shown.filter { it.kind == "task" }; val checkins = shown.filter { it.kind == "checkin" }; val journal = shown.filter { it.kind == "journal" && !SideEffectCodec.isStructured(it.detail) && !PatientProfileCodec.isStructured(it.detail) }
    val selectedPerson by vm.selected.collectAsStateWithLifecycle(); val loadedFor by vm.itemsLoadedFor.collectAsStateWithLifecycle()
    // A row opens its module on its own screen; Back returns to the list. [initialFilter] can open one directly.
    var openModule by rememberSaveable(target) { mutableStateOf(target) }
    // A module opened straight from another page (Today's "Open medicines") goes back to that page, not to the list.
    fun closeModule() { if (target != null && openModule == target && onExit != null) onExit() else openModule = null }
    val t = careText()
    // [name] stays English: it is the module's key and test tag; only [title] is shown.
    val tiles = listOf(
        CareModuleTile("Conversations", t("Conversations", "बातचीत"), "", SaharaLavender, R.drawable.conversation_recent_art, versions.size) {
            CareModuleList("conversations", versions, t("No saved conversations yet.", "अभी कोई सहेजी हुई बातचीत नहीं।"), keyOf = { it.id }) { CareConversationRow(it, onReadNote) }
        },
        CareModuleTile("Medicines", t("Medicines", "दवाइयाँ"), "", SaharaSoftBlue, R.drawable.care_medicines_art, currentMeds.size + pastMeds.size) {
                if (currentMeds.isEmpty() && pastMeds.isEmpty()) { SaharaLottie(SaharaLottieKind.Pill, Modifier.fillMaxWidth()); QuietLine(t("No medicines yet.", "अभी कोई दवा नहीं।")) } else {
                    CareModuleList("medicines-current", currentMeds, t("No current medicines.", "अभी कोई दवा नहीं चल रही।"), t("Current", "अभी चल रही"), { it.id }) { CareRow(it, vm) }
                    CareModuleList("medicines-past", pastMeds, t("No past medicines yet.", "पहले की कोई दवा नहीं।"), t("Past", "पहले की"), { it.id }) { CareRow(it, vm) }
                }
                MedicineScanAction(onScanPrescription) { onAdd("medicine") }
                SideEffectsToRaiseCard(vm)
        },
        CareModuleTile("Visits", t("Visits", "मुलाक़ातें"), "", SaharaApricot, R.drawable.conversation_history_art, upcoming.size + pastVisits.size) {
                CareVisitsModule(vm, upcoming, pastVisits, onCalendarAt)
        },
        CareModuleTile("To do", t("To do", "करने के काम"), "", SaharaSoftGold, R.drawable.care_notes_illustration, tasks.size) {
            CareTodoModule(vm, tasks)
        },
        CareModuleTile("Check-ins", t("Check-ins", "रोज़ का हाल"), "", Mint, R.drawable.wellbeing_checkin_illustration, checkins.size) {
            DistressHistoryCard(checkins, selectedPerson); CareModuleList("check-ins", checkins, t("No check-ins yet.", "अभी कोई हाल नहीं लिखा।"), keyOf = { it.id }) { CareRow(it, vm) }
        },
        CareModuleTile("Journal", t("Journal", "डायरी"), "", Peach, R.drawable.conversation_record_art, journal.size) {
            CareModuleList("journal", journal, t("No journal notes yet.", "डायरी में अभी कोई नोट नहीं।"), keyOf = { it.id }) { CareRow(it, vm) }
        },
    )
    val opened = tiles.firstOrNull { it.name == openModule }.takeIf { loadedFor == selectedPerson }
    val byName = tiles.associateBy { it.name }
    fun module(name: String): CareToolRow? = byName[name]?.let { tile -> CareToolRow(tile.title, careModuleIcon(name), careModuleTone(name), "care-module-${tile.key}-toggle",
        if (tile.count == 0) t("Nothing yet", "अभी कुछ नहीं") else "${tile.count}") { openModule = name } }
    fun page(label: String, icon: ImageVector, tone: SaharaTone, tag: String, open: (() -> Unit)?): CareToolRow? = open?.let { CareToolRow(label, icon, tone, tag, null, it) }
    val groups = listOf(
        Triple(t("Every day", "हर दिन"), SaharaTone.Teal, listOfNotNull(module("Medicines"), page(t("Symptoms", "लक्षण"), Icons.Default.MonitorHeart, SaharaTone.Rose, "symptom-tracker-entry", onSymptoms), module("Check-ins"), module("To do"), module("Journal"))),
        Triple(t("Visits and records", "मुलाक़ातें और रिकॉर्ड"), SaharaTone.Indigo, listOfNotNull(module("Visits"), page(t("Calendar", "कैलेंडर"), Icons.Default.CalendarMonth, SaharaTone.Indigo, "shortcut-calendar", onCalendar),
            page(t("Documents", "कागज़ात"), Icons.Default.FolderOpen, SaharaSection.Documents, "care-tool-documents", onDocuments), page(t("Lab history", "जाँच रिपोर्टें"), Icons.Default.Science, SaharaSection.Labs, "shortcut-labs", onLabs), module("Conversations"))),
        Triple(t("Plans and support", "योजना और सहारा"), SaharaTone.Rose, listOfNotNull(page(t("Emergency plan", "आपातकालीन योजना"), Icons.Default.LocalHospital, SaharaSection.Emergency, "care-tool-plan", onCarePlan),
            page(t("Tubes and lines", "नलियाँ और लाइनें"), Icons.Default.Healing, SaharaTone.Plum, "shortcut-devices", onDevices), page(t("Community", "समुदाय"), Icons.Default.Groups, SaharaSection.Community, "shortcut-community", onCommunity))),
    ).filter { it.third.isNotEmpty() }
    if (opened != null) CareModuleScreen(opened) { closeModule() } else
    LazyColumn(Modifier.fillMaxSize().saharaScreenEnter().testTag("care-list"), state = listState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // One title and the one main action (Add) on top; then the list.
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Heading(title ?: t("Daily care", "रोज़ की देखभाल"), 26) }
            CareAddMenuButton(onAdd, onScanPrescription)
        } }
        groups.forEachIndexed { g, (group, groupTone, rows) -> item(key = "care-group-$group") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Each group's name sits on a solid pill in its colour; its register takes the matching wash and edge bar.
                Text(group, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = groupTone.onSolid, modifier = Modifier.clip(RoundedCornerShape(SaharaRadius.Pill)).background(groupTone.solid).padding(horizontal = 12.dp, vertical = 4.dp).a11yHeading())
                SaharaRegister(tone = groupTone) { rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = groupTone.solid.copy(alpha = .35f), modifier = Modifier.padding(horizontal = 12.dp))
                    SaharaListRow(row.label, Modifier.saharaStagger(g * 3 + index).testTag(row.tag), detail = row.detail, icon = row.icon, tone = row.tone, onClick = row.onClick)
                } }
            }
        } }
    }
}

/** One row of All care tools: a module of this tab or another page. [detail] is the module's count. */
private class CareToolRow(val label: String, val icon: ImageVector, val tone: SaharaTone, val tag: String, val detail: String?, val onClick: () -> Unit)

/** The emergency numbers (tap to open the dialler) and a hospitals map link. Shown in Nearby and in Today's SOS sheet. */
@Composable fun EmergencyNumbersCard() {
    val t = careText(); val context = LocalContext.current
    // Emergency red wash; each number is a white button with a solid red call chip.
    SurfaceCard(SaharaSection.Emergency.wash) {
        Text(t("Emergency", "आपातकाल"), fontWeight = FontWeight.Bold, fontSize = 19.sp, color = SaharaSection.Emergency.ink, modifier = Modifier.testTag("nearby-emergency"))
        NearbyHelp.emergency.forEach { e ->
            Spacer(Modifier.height(8.dp))
            Button(onClick = { open(context, Intent(Intent.ACTION_DIAL, Uri.parse(NearbyHelp.dialUri(e.number)))) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
                border = BorderStroke(1.5.dp, SaharaSection.Emergency.solid), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("emergency-${e.number}")) {
                SaharaIconChip(Icons.Default.Call, SaharaSection.Emergency, size = 32); Spacer(Modifier.width(10.dp))
                Text(e.number, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.widthIn(min = 64.dp)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(t(e.label, e.labelHindi), fontWeight = FontWeight.SemiBold)
                    if (e.note.isNotBlank()) Text(t(e.note, e.noteHindi), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // The maps app searches near the phone using its own location setting; this app does not read location.
        OutlinedButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("hospital")))) }, shape = RoundedCornerShape(14.dp), border = BorderStroke(2.dp, SaharaSection.Emergency.solid), colors = ButtonDefaults.outlinedButtonColors(containerColor = Paper, contentColor = SaharaSection.Emergency.ink), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("nearby-hospitals-map")) {
            Icon(Icons.Default.LocalHospital, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Hospitals near you on the map", "नक्शे पर पास के अस्पताल"), fontWeight = FontWeight.Bold)
        }
    }
}

/** [compact] (Today): no Missed today / Side effect? / History capsules; those stay on the Medicines page. */
@Composable fun CareRow(item: CareItem, vm: CareModel, compact: Boolean = false) {
    val hindi = careHindi(); val t = careText()
    if (SymptomNoteCodec.isStructured(item.detail)) { SymptomNoteCard(item, vm, hindi); return }
    if (DistressCodec.isStructured(item.detail)) { DistressEntryCard(item); return }
    if (SideEffectCodec.isStructured(item.detail)) { SideEffectEntryCard(item, vm); return }
    if (item.kind == "medicine") { MedicineCareCard(item, vm, compact); return }
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var delete by rememberSaveable(item.id) { mutableStateOf(false) }
    var editing by rememberSaveable(item.id) { mutableStateOf(false) }
    val capturedAccount = remember(item.id) { vm.accountNamespace }
    val done = if (item.kind == "medicine") item.doneOn == LocalDate.now().toString() else item.doneOn.isNotBlank()
    val color by animateColorAsState(if (done) Color(0xFFE7EED6) else Paper, tween(180), label = "care completed")
    var menu by remember { mutableStateOf(false) }
    SurfaceCard(color) { Row(verticalAlignment = Alignment.Top) {
        if (item.kind in listOf("medicine", "task")) Checkbox(checked = done, onCheckedChange = { if (!busy && canEditCareContext(vm, item.personId, capturedAccount)) vm.toggle(item) }, enabled = canEdit, modifier = Modifier.semantics { contentDescription = "${if (item.kind == "medicine") t("Taken today", "आज ली") else t("Done", "हो गया")}: ${item.title}" })
        else SaharaIconChip(careIcon(item.kind), when (item.kind) { "visit" -> SaharaSection.Visits; "checkin" -> SaharaSection.CheckIns; "journal" -> SaharaTone.Rose; else -> SaharaTone.Leaf }, Modifier.padding(end = 12.dp), size = 42)
        Column(Modifier.weight(1f)) { Text(if (item.kind == "checkin") checkInMoodLabel(item.title, hindi) else item.title, fontWeight = FontWeight.SemiBold); if (item.due > 0) Text(date(item.due), color = if (done) RustText else Rust, fontSize = 13.sp); if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.bodyMedium); if (item.kind in listOf("checkin", "journal")) Text(date(item.created), style = MaterialTheme.typography.bodySmall) }
        // Edit and Remove live behind a three-dot menu at the card's top-right.
        if (canEdit) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("care-menu-${item.id}")) { Icon(Icons.Default.MoreVert, t("More actions", "और विकल्प"), tint = DoctorCardInk) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(t("Edit", "बदलें"), color = DoctorCardInk) }, leadingIcon = { Icon(Icons.Default.Edit, null, tint = DoctorCardInk) }, onClick = { menu = false; editing = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-edit-${item.id}"))
                DropdownMenuItem(text = { Text(t("Remove", "हटाएँ"), color = RustText) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = RustText) }, onClick = { menu = false; delete = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-remove-${item.id}"))
            }
        }
    }
        if (item.kind == "medicine" && !compact) MedicineDoseActions(item, vm)
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(t("Remove this entry?", "यह जानकारी हटाएँ?")) }, text = { Text(item.title) }, confirmButton = { TextButton(onClick = { if (canEditCareContext(vm, item.personId, capturedAccount)) vm.remove(item); delete = false }, enabled = !busy && canEdit) { Text(t("Remove", "हटाएँ")) } }, dismissButton = { TextButton(onClick = { delete = false }) { Text(t("Keep", "रखें")) } })
    if (editing) AddCareItem(item.kind, vm, onDismiss = { editing = false }, initial = item)
}

@Composable fun More(vm: CareModel, person: Person, onAccount: () -> Unit, onDelete: () -> Unit, onCalendar: () -> Unit = {}, onWorkflow: () -> Unit = {}, onLabs: () -> Unit = {}, onFeedback: () -> Unit = {}, onReminders: () -> Unit = {}, onSharedWorkspace: () -> Unit = {}, onDocuments: () -> Unit = {}, doctorEmbedded: Boolean = false, walkthrough: Boolean = false, onCarePlan: (() -> Unit)? = null, onDevices: (() -> Unit)? = null, onInviteFamily: (() -> Unit)? = null) {
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current; val versions by vm.versions.collectAsStateWithLifecycle(); val acks by vm.acknowledgements.collectAsStateWithLifecycle()
    val t = careText()
    val clinicianView by vm.clinician.collectAsStateWithLifecycle()
    val email = vm.cloud?.session?.collectAsStateWithLifecycle()?.value?.email.orEmpty()
    var about by rememberSaveable { mutableStateOf(false) }
    // An Android settings page: the person's profile at the top, then one plain grouped list. Every row is a title, one short line and a chevron.
    LazyColumn(Modifier.fillMaxSize().saharaScreenEnter().testTag("more-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (doctorEmbedded || clinicianView) item { Heading(if (doctorEmbedded) t("Daily care & tools", "रोज़ की देखभाल और साधन") else t("More", "और"), 26) }
        else item { PatientProfileCard(vm, person) }
        if (!doctorEmbedded && !walkthrough) item { MoreGroup(t("Account", "खाता")) {
            MoreRow(Icons.Default.AccountCircle, FamilyAccent.Leaf, if (vm.cloud == null) t("Sign in", "साइन इन") else t("Account and sharing", "खाता और साझा करना"),
                if (vm.cloud == null) t("Share care with family", "परिवार के साथ देखभाल साझा करें") else email, Modifier.saharaStagger(1).testTag("more-account"), onAccount)
            // Inviting family is one tap from here, not hidden in the header's person menu.
            if (onInviteFamily != null && vm.cloud != null && owner) MoreRow(Icons.Default.PersonAdd, FamilyAccent.Leaf, t("Invite family", "परिवार को बुलाएँ"), t("Send a link on WhatsApp", "व्हाट्सऐप पर लिंक भेजें"), Modifier.saharaStagger(2).testTag("more-invite-family"), onInviteFamily)
        } }
        // Patients and family reach Documents, Calendar, Lab history, the emergency plan and tubes and lines from All care tools (the Care tab),
        // so those rows are only here for doctors. More keeps the account, settings and help.
        val shortcutsElsewhere = !doctorEmbedded && !clinicianView
        val exportNotes = {
            val text = versions.joinToString("\n\n──────────\n\n") { v -> NoteRules.text(v, acks.filter { it.versionId == v.id && it.contentHash == v.contentHash }) }
            open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), t("Export care notes", "देखभाल नोट भेजें")))
        }
        if (!shortcutsElsewhere) item { MoreGroup(t("Care", "देखभाल")) {
            MoreRow(Icons.Default.FolderOpen, SaharaSection.Documents.solid, t("Documents", "दस्तावेज़"), "", Modifier.saharaStagger(3).testTag("more-documents-entry"), onDocuments)
            if (onCarePlan != null) MoreRow(Icons.Default.LocalHospital, SaharaSection.Emergency.solid, t("Emergency plan", "आपातकालीन योजना"), t("Plan, pocket emergency card, living will", "योजना, जेब वाला आपातकालीन कार्ड, लिविंग विल"), Modifier.saharaStagger(4).testTag("more-care-plan-entry"), onCarePlan)
            if (onDevices != null && !clinicianView) MoreRow(Icons.Default.Healing, FamilyAccent.Plum, t("Tubes and lines", "नलियाँ और लाइनें"), "", Modifier.saharaStagger(5).testTag("more-devices"), onDevices)
            MoreRow(Icons.Default.CalendarMonth, SaharaSection.Visits.solid, t("Calendar", "कैलेंडर"), "", Modifier.saharaStagger(6).testTag("more-calendar"), onCalendar)
            MoreRow(Icons.Default.Science, SaharaSection.Labs.solid, t("Lab history", "जाँच का इतिहास"), "", Modifier.saharaStagger(7).testTag("more-labs"), onLabs)
            MoreRow(Icons.Default.Share, FamilyAccent.Brick, t("Export care notes", "देखभाल नोट भेजें"), t("${versions.size} saved", "${versions.size} सहेजे गए"), Modifier.saharaStagger(8).testTag("more-export-notes"), enabled = versions.isNotEmpty(), onClick = exportNotes)
        } }
        item { MoreGroup(t("Settings", "सेटिंग")) {
            // Language opens the same menu as the header, from a full-width row.
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                MoreIcon(Icons.Default.Translate, FamilyAccent.Teal); Spacer(Modifier.width(16.dp))
                Text(t("Language", "भाषा"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                CareLanguageMenu(contentColor = RustText)
            }
            if (!walkthrough) MoreRow(Icons.Default.NotificationsNone, FamilyAccent.Marigold, t("Notifications", "सूचनाएँ"), t("Medicine and visit reminders", "दवा और मुलाक़ात के रिमाइंडर"), Modifier.saharaStagger(9).testTag("more-reminders"), onReminders)
            MoreRow(Icons.Default.Lock, FamilyAccent.Plum, t("Privacy and data", "निजता और डेटा"), "", Modifier.saharaStagger(10).testTag("more-privacy"), { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) })
            if (shortcutsElsewhere) MoreRow(Icons.Default.Share, FamilyAccent.Brick, t("Export care notes", "देखभाल नोट भेजें"), t("${versions.size} saved", "${versions.size} सहेजे गए"), Modifier.saharaStagger(11).testTag("more-export-notes"), enabled = versions.isNotEmpty(), onClick = exportNotes)
        } }
        item { MoreGroup(t("Help", "मदद")) {
            if (!walkthrough) MoreRow(Icons.Default.SentimentSatisfiedAlt, FamilyAccent.Leaf, t("Send feedback", "राय भेजें"), "", Modifier.saharaStagger(12).testTag("patient-feedback-entry"), onFeedback)
            if (doctorEmbedded) MoreRow(Icons.Default.Groups, FamilyAccent.Teal, t("Shared care", "साझा देखभाल"), "", Modifier, onSharedWorkspace)
            else if (!walkthrough) MoreRow(Icons.Default.MedicalServices, FamilyAccent.Teal, t("I'm a doctor", "मैं डॉक्टर हूँ"), "", Modifier.saharaStagger(13).testTag("more-doctor-workspace-entry"), onSharedWorkspace)
            MoreRow(Icons.Default.Info, FamilyAccent.Brick, t("About", "ऐप के बारे में"), t("Version ${BuildConfig.VERSION_NAME}", "संस्करण ${BuildConfig.VERSION_NAME}"), Modifier.saharaStagger(14).testTag("more-about"), { about = true })
        } }
        if (owner && !walkthrough) item { TextButton(onClick = onDelete, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(8.dp)); Text(t("Remove this person's records", "इस व्यक्ति के रिकॉर्ड हटाएँ")) } }
    }
    // About is a standard dialog, not an unfolding card at the bottom of the list.
    if (about) AlertDialog(onDismissRequest = { about = false }, containerColor = Paper, modifier = Modifier.saharaDialogEnter().testTag("more-about-dialog"),
        title = { Text(androidx.compose.ui.res.stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(androidx.compose.ui.res.stringResource(R.string.app_full_name))
            Text(t(MedicineNames.ATTRIBUTION, "दवा के नाम: CDCI (NRCeS / C-DAC), CC BY 4.0"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://creativecommons.org/licenses/by/4.0/"))) }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("CC BY 4.0 licence", "CC BY 4.0 लाइसेंस")) }
            Text(t("Version ${BuildConfig.VERSION_NAME}", "संस्करण ${BuildConfig.VERSION_NAME}"), style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { about = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Close", "बंद करें"), fontWeight = FontWeight.Bold) } })
}

/** A titled group of settings rows on one card, rows split by inset dividers. */
@Composable private fun MoreGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.saharaAppear(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RustText, modifier = Modifier.padding(start = 4.dp).a11yHeading())
        Surface(shape = RoundedCornerShape(12.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(vertical = 2.dp), content = content) }
    }
}

@Composable private fun MoreIcon(icon: ImageVector, accent: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = accent, contentColor = Color.White, modifier = Modifier.size(36.dp)) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) } }
}

/** One settings row: a solid coloured icon, a title, an optional short line and a chevron. */
@Composable private fun MoreRow(icon: ImageVector, accent: Color, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Row(modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(press, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick).saharaPress(press).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MoreIcon(icon, if (enabled) accent else DoctorCardFaint)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = if (enabled) DoctorCardInk else DoctorCardSub)
            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(22.dp), tint = DoctorCardSub)
    }
}

@Composable fun AddPerson(onDismiss: () -> Unit, busy: Boolean, error: String?, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; val t = careText()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(t("Who is this care for?", "यह देखभाल किसके लिए है?")) }, text = { Column { CareField(value = name, onValueChange = { name = it.take(100) }, label = t("Name", "नाम"), singleLine = true); error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) } } },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && !busy) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Continue", "आगे बढ़ें")) } }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(t("Cancel", "रद्द करें")) } })
}

@Composable fun AddPersonContent(onDismiss: () -> Unit, busy: Boolean, error: String?, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; val t = careText()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Heading(t("Who is this care for?", "यह देखभाल किसके लिए है?"), 27)
        CareField(name, { name = it.take(100) }, t("Name", "नाम"), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
        Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && !busy, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("welcome-save-person")) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Continue", "आगे बढ़ें")) }
        TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Back", "वापस")) }
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
    val hindi = careHindi(); val t = careText()
    val headings = mapOf("medicine" to t("Add a medicine", "दवा जोड़ें"), "visit" to t("Add a visit", "मुलाक़ात जोड़ें"), "task" to t("Add a task", "काम जोड़ें"), "checkin" to t("How is today?", "आज कैसा है?"), "journal" to t("Write a note", "नोट लिखें"))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 32.dp).saharaContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading(if (initial != null && kind != "checkin") when (kind) { "medicine" -> t("Edit medicine", "दवा बदलें"); "visit" -> t("Edit visit", "मुलाक़ात बदलें"); "task" -> t("Edit task", "काम बदलें"); else -> t("Edit note", "नोट बदलें") } else headings[kind] ?: t("Add an entry", "जानकारी जोड़ें"), 28)
            if (kind == "checkin") {
                val moods = listOf(Triple(CheckInMood.GOOD, Icons.Default.SentimentSatisfiedAlt, Sage), Triple(CheckInMood.OKAY, Icons.Default.SentimentNeutral, Gold), Triple(CheckInMood.HARD, Icons.Default.SentimentDissatisfied, Peach))
                moods.forEach { (mood, icon, tint) -> ChoiceRow(checkInMoodLabel(mood, hindi), icon, tint, title == mood) { title = mood } }
                CareNoteField(detail, { detail = it.take(4000) }, t("Anything to add? (optional)", "कुछ और बताना है? (ज़रूरी नहीं)"), lines = 2)
            } else if (kind == "medicine" && MedicineEntryText.parse(detail) != null) {
                MedicineEntryForm(title, { title = it.take(200) }, detail, { detail = it.take(4000) }, vm.storageNamespace, capturedPerson, entryId, busy)
            } else if (kind == "journal" && initial == null) {
                CareField(value = detail, onValueChange = { detail = it.take(4000); title = it.lineSequence().firstOrNull { line -> line.isNotBlank() }.orEmpty().take(80) }, label = t("Your note", "आपका नोट"), minLines = 4)
            } else {
                CareField(value = title, onValueChange = { title = it.take(200) }, label = when (kind) { "medicine" -> t("Medicine name", "दवा का नाम"); "visit" -> t("Doctor or hospital", "डॉक्टर या अस्पताल"); "task" -> t("What needs doing?", "क्या करना है?"); else -> t("Title", "शीर्षक") }, singleLine = kind != "task")
                if (kind == "medicine") MedicineNameSuggestions(title, enabled = !busy) { title = it }
                // A task's details are optional, so they start as one "Add a note" line that grows when used.
                if (kind == "task") CareNoteField(detail, { detail = it.take(4000) }, t("Details (optional)", "और जानकारी (ज़रूरी नहीं)"), lines = 2)
                else CareField(value = detail, onValueChange = { detail = it.take(4000) }, label = when (kind) { "medicine" -> t("Dose and times", "खुराक और समय"); "visit" -> t("Time and place", "समय और जगह"); else -> t("Your note", "आपका नोट") }, minLines = if (kind == "journal") 4 else 2)
                if (kind == "medicine") StripPhotoRow(vm.storageNamespace, capturedPerson, entryId, enabled = !busy)
            }
            if (kind == "visit") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(t("Today", "आज") to 0L, t("Tomorrow", "कल") to 1L).forEach { (label, days) -> val day = LocalDate.now().plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(); FilterChip(selected = due == day, onClick = { due = day }, label = { Text(label) }, modifier = Modifier.heightIn(min = 48.dp)) }
            }
            if (kind == "visit") OutlinedButton(onClick = { val chosen = if (due > 0) Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate() else LocalDate.now(); DatePickerDialog(context, { _, y, m, d -> due = LocalDate.of(y, m + 1, d).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }, chosen.year, chosen.monthValue - 1, chosen.dayOfMonth).show() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(if (due == 0L) t("Choose date", "तारीख़ चुनें") else date(due)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = { if (!canEditCareContext(vm, capturedPerson, capturedAccount)) { vm.message.value = t("Care access changed. Reopen this person's records before saving.", "देखभाल की अनुमति बदल गई है। सहेजने से पहले इस व्यक्ति के रिकॉर्ड फिर से खोलें।"); onDismiss() } else if (initial == null) vm.saveItem(kind, title, detail, due, savedAndClose, entryId) else vm.updateItem(initial.copy(title = title, detail = detail, due = due), onDismiss) }, enabled = selected == capturedPerson && title.isNotBlank() && !busy && (kind != "visit" || due > 0) && (kind != "medicine" || detail.isNotBlank()), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (busy) t("Saving…", "सहेज रहे हैं…") else t("Save", "सहेजें"), fontSize = 16.sp) }
        }
    }
}

@Composable fun SurfaceCard(color: Color = Paper, content: @Composable ColumnScope.() -> Unit) { Surface(shape = RoundedCornerShape(20.dp), color = color, border = if (color == Paper) BorderStroke(1.dp, DoctorHairline) else null, modifier = Modifier.fillMaxWidth().saharaAppear().a11yCard()) { Column(Modifier.saharaContentSize().padding(16.dp), content = content) } }
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
@Composable fun Heading(text: String, size: Int = 30) { Text(text, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = size.sp, lineHeight = (size + 4).sp, letterSpacing = (-0.7).sp) }
@Composable fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 20.sp, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.weight(1f).a11yHeading()); if (action != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = RustText)) { Text(action) } } }
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
    fun subLabel(top: Int, sub: Int, hindi: Boolean = false): String { val l = subs[top.coerceIn(0, 2)]; val i = sub.coerceIn(0, l.lastIndex); return if (l.size < 2) "" else careTr(hindi, "${l[i]} · ${i + 1} of ${l.size}", "${label(l[i], true)} · ${l.size} में से ${i + 1}") }
    /** Step and page names stay English as keys; only the shown name is Hindi. */
    fun label(name: String, hindi: Boolean): String = careTr(hindi, name, when (name) {
        "Before" -> "पहले"; "Conversation" -> "बातचीत"; "Note" -> "नोट"; "Who's here" -> "कौन मौजूद है"; "Record" -> "रिकॉर्ड"; "Transcript" -> "लिखाई"
        "Answers" -> "जवाब"; "Review" -> "जाँच"; "Publish" -> "प्रकाशित करें"; else -> name
    })
}

/** Progress indicator for the three conversation steps. Navigation is by each page's own Back/Continue button. */
@Composable fun ConversationStepper(step: Int) { val t = careText(); val hindi = careHindi()
    val names = ConversationPages.names
    Column(Modifier.fillMaxWidth().testTag("conversation-stepper")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            names.forEachIndexed { i, name ->
                val done = i < step; val current = i == step
                val fill = if (done) DoctorStatusGoodBg else if (current) DoctorAccent else DoctorStatusRestBg
                val ink = if (done) DoctorStatusGood else if (current) Color.White else DoctorCardSub
                // Progress only: each page is reached by its own Back/Continue button.
                Column(Modifier.weight(1f).heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = ConversationPages.label(name, hindi); stateDescription = if (done) t("Done", "हो गया") else if (current) t("Current step", "अभी का चरण") else t("Not started", "शुरू नहीं हुआ") }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(2.dp).background(if (i == 0) Color.Transparent else if (i <= step) DoctorStatusGood else DoctorHairline))
                        Box(Modifier.size(32.dp).background(fill, CircleShape).border(if (current) 3.dp else 1.dp, if (current) DoctorStatusWarnBg else DoctorHairline, CircleShape), contentAlignment = Alignment.Center) { if (done) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = ink) else Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = ink) }
                        Box(Modifier.weight(1f).height(2.dp).background(if (i == names.lastIndex) Color.Transparent else if (i < step) DoctorStatusGood else DoctorHairline))
                    }
                    Text(ConversationPages.label(name, hindi), fontSize = cappedLabelSize(12f), lineHeight = 16.sp, color = if (current) DoctorCardInk else DoctorCardFaint, fontWeight = if (current) FontWeight.Medium else FontWeight.Normal, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable fun InfoDisclosure(title: String, color: Color = Paper, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }; val t = careText()
    Surface(shape = RoundedCornerShape(20.dp), color = color, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("info-disclosure")) { Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (open) t("Hide details", "जानकारी छिपाएँ") else t("Show details", "जानकारी देखें")) { open = !open }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(10.dp))
            Text(title, Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) t("Collapse", "बंद करें") else t("Expand", "खोलें"), tint = DoctorCardSub)
        }
        AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
    } }
}

@Composable fun RawTranscriptPanel(source: String) {
    var open by rememberSaveable { mutableStateOf(true) }; val t = careText()
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("raw-transcript")) { Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = if (open) t("Hide full text", "पूरी लिखाई बंद करें") else t("Show full text", "पूरी लिखाई खोलें")) { open = !open }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("Full written text", "पूरी लिखाई"), Modifier.weight(1f), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
            Text("${source.trim().split(Regex("\\s+")).count { it.isNotEmpty() }} " + t("words", "शब्द"), fontSize = 12.sp, color = DoctorCardFaint); Spacer(Modifier.width(8.dp))
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = DoctorCardSub)
        }
        AnimatedVisibility(open, enter = SaharaMotion.expand, exit = SaharaMotion.collapse) { Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState())) { androidx.compose.foundation.text.selection.SelectionContainer { Text(source.ifBlank { t("No written text yet.", "अभी कोई लिखाई नहीं बनी।") }, fontSize = 15.sp, lineHeight = 24.sp, color = DoctorCardInk) } }
        } }
    } }
}
