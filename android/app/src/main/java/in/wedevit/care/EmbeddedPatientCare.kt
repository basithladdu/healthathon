package `in`.wedevit.care

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** A source identity check, not a new access grant. Never accepts a test-dataset ID as a real person. */
internal fun embeddedPatientContextMatches(
    expectedAccountId: String,
    sessionAccountId: String,
    modelAccountId: String,
    personId: String,
    selectedId: String,
    draftPersonId: String,
    availablePersonIds: Set<String>,
    recordPersonIds: List<String>,
    allowDeviceLocal: Boolean = false,
): Boolean {
    val accountMatches = if (allowDeviceLocal && expectedAccountId.isBlank())
        sessionAccountId.isBlank() && modelAccountId.isBlank()
    else expectedAccountId.isNotBlank() && expectedAccountId == sessionAccountId && expectedAccountId == modelAccountId
    return accountMatches && personId.isNotBlank() && personId == selectedId && personId == draftPersonId &&
        personId in availablePersonIds && recordPersonIds.all { it == personId }
}

/** Content-only adapter: the dedicated Doctor shell remains the sole header/navigation owner. */
@Composable
fun EmbeddedPatientCare(
    vm: CareModel,
    personId: String,
    section: String,
    onSection: (String) -> Unit,
    onAccount: () -> Unit,
    onExit: () -> Unit,
    onNavigationBlockedChange: (Boolean) -> Unit = {},
    allowDeviceLocal: Boolean = false,
    onHeaderBackChange: ((() -> Unit)?) -> Unit = {},
) {
    val account = vm.accountNamespace
    val session = vm.cloud?.session?.collectAsStateWithLifecycle()?.value
    val selected by vm.selected.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val entries by vm.items.collectAsStateWithLifecycle()
    val versions by vm.versions.collectAsStateWithLifecycle()
    val reports by vm.reports.collectAsStateWithLifecycle()
    val canEdit by vm.canEdit.collectAsStateWithLifecycle()
    val isOwner by vm.isOwner.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val refreshError by vm.refreshError.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val recording by vm.recording.collectAsStateWithLifecycle()
    val transcription by vm.transcribingPath.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.id == personId }
    val ready = embeddedPatientContextMatches(account, session?.userId.orEmpty(), account,
        personId, selected, draft.personId, people.map { it.id }.toSet(),
        entries.map { it.personId } + versions.map { it.personId } + reports.map { it.personId },
        allowDeviceLocal = allowDeviceLocal && vm.cloud == null)
    var tool by rememberSaveable(account, personId) { mutableStateOf("") }
    var dialog by rememberSaveable(account, personId) { mutableStateOf("") }
    var reading by remember(account, personId) { mutableStateOf<CareVersion?>(null) }
    var talkInteraction by remember(account, personId) { mutableStateOf(false) }
    var sharedBusy by remember(account, personId) { mutableStateOf(false) }
    var medicineBusy by remember(account, personId) { mutableStateOf(false) }
    var visitBusy by remember(account, personId) { mutableStateOf(false) }
    var visitBack by remember(account, personId) {mutableStateOf<(() -> Unit)?>(null)}
    var conversationBack by remember(account, personId) { mutableStateOf<(() -> Unit)?>(null) }
    var conversationCapture by remember(account, personId) { mutableStateOf(false) }
    val screens = rememberSaveableStateHolder()
    val snackbar = remember { SnackbarHostState() }
    val navigating = busy || refreshing || transcription != null || talkInteraction || sharedBusy || medicineBusy || visitBusy
    // Viewing a tool does not disable the Doctor tabs. Only an active operation or modal owns navigation.
    val blocked = ready && (navigating || tool in listOf("scanner", "feedback") || dialog.isNotEmpty() || reading != null)
    val currentBlockedCallback by rememberUpdatedState(onNavigationBlockedChange)
    SideEffect { currentBlockedCallback(blocked) }
    DisposableEffect(vm, account, personId) { onDispose { vm.stopRecording(); currentBlockedCallback(false) } }

    // Doctor is only a presentation mode. Publishing still uses the existing edit/verified-review rules.
    if (ready) DisposableEffect(vm, account, personId) {
        val previousRole = vm.viewRole.value
        vm.role("Doctor")
        onDispose { if (vm.viewRole.value == "Doctor") vm.role(previousRole) }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vm.stopRecording()
            if (event == Lifecycle.Event.ON_START) vm.refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); if (vm.message.value == it) vm.message.value = null } }
    LaunchedEffect(ready) { if (!ready) { tool = ""; dialog = ""; reading = null; talkInteraction = false; sharedBusy = false; medicineBusy = false;visitBusy=false;visitBack=null } }
    LaunchedEffect(canEdit, isOwner) {
        if ((!canEdit && dialog != "delete") || (!isOwner && dialog == "delete")) dialog = ""
        if (!canEdit && tool == "scanner") tool = ""
    }
    fun samePerson() = ready && vm.selected.value == personId && vm.draft.value.personId == personId &&
        vm.accountNamespace == account && (if (vm.cloud == null) allowDeviceLocal else vm.cloud.session.value?.userId == account)
    fun leaveTool() { if (!navigating) { vm.stopRecording(); tool = "" } }
    fun openTool(next: String) { if (samePerson() && !navigating) {
        if(vm.demoMode && next in listOf("scanner","nearby","feedback","reminders","shared","resources")) vm.message.value="This online, account or device action is unavailable in the walkthrough. Your own care profiles are unchanged."
        else {vm.stopRecording(); tool = next}
    } }
    fun changeSection(next: String) { if (samePerson() && !navigating) { vm.stopRecording(); tool = ""; onSection(next) } }
    fun add(kind: String) {
        if (samePerson() && vm.canEdit.value && !navigating) dialog = kind
        else vm.message.value = "Ask the person who invited you to allow changes."
    }
    fun read(note: CareVersion) { if (samePerson() && note.personId == personId && !navigating) reading = note }
    val currentHeaderBackChange by rememberUpdatedState(onHeaderBackChange)
    DisposableEffect(ready, tool, reading?.id, dialog, recording, navigating, visitBack, conversationBack) {
        val back: (() -> Unit)? = when {
            !ready || navigating -> null
            reading != null -> { { reading = null } }
            dialog.isNotEmpty() -> { { dialog = "" } }
            tool in listOf("scanner", "feedback") -> null // These own confirmation/cleanup controls.
            tool.isNotEmpty() -> { { leaveTool() } }
            section=="Visits" && visitBack!=null -> visitBack
            section=="Conversations" && conversationBack!=null -> conversationBack
            recording -> { { vm.stopRecording() } }
            else -> null
        }
        currentHeaderBackChange(back)
        onDispose { currentHeaderBackChange(null) }
    }
    BackHandler(tool.isNotEmpty() && tool !in listOf("scanner", "feedback", "reminders") && reading == null && dialog.isEmpty()) { leaveTool() }
    BackHandler(recording && tool.isEmpty() && reading == null && dialog.isEmpty()) { vm.stopRecording() }

    // Check-ins use the full-screen slider so both entry points record the same way; it replaces the content rather than sitting under it.
    if (ready && dialog == "checkin" && canEdit) {
        FeedbackScreen(accountScope = vm.accountNamespace.ifBlank { "guest" }, surface = "patient-checkin", onClose = { dialog = "" }, careModel = vm, personId = personId)
        return
    }
    Column(Modifier.fillMaxSize().testTag("embedded-patient-care")) {
        if (!ready || person == null) {
            Column(Modifier.fillMaxWidth().padding(22.dp).testTag(if (person == null && !refreshing) "embedded-care-unavailable" else "embedded-care-loading"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (person == null && !refreshing) "This care profile is unavailable" else "Opening this person's care…", style = MaterialTheme.typography.titleLarge)
                Text("Only this selected person's records will appear here. Test patients and other care profiles are kept separate.")
                OutlinedButton(onClick = onExit) { Text("Back to patients") }
                refreshError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::refresh) { Text("Retry") } }
            }
        } else {
            if ((section != "Conversations" || tool.isNotEmpty() || !conversationCapture) && (!DoctorTestProfiles.contains(personId) || !canEdit)) Surface(color = if (vm.cloud == null) Peach.copy(alpha = .5f) else Paper) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    if(!DoctorTestProfiles.contains(personId)) Text(if(vm.demoMode) "Walkthrough care record · not synced" else if (vm.cloud == null) "Device-local care profile" else "Shared care profile", style = MaterialTheme.typography.labelLarge, color = Rust, modifier = Modifier.testTag("embedded-care-source"))
                    if(vm.demoMode && !DoctorTestProfiles.contains(personId)) Text("No prescription is issued and no appointment is booked.",style=MaterialTheme.typography.bodySmall)
                    if (!canEdit) Text("Read-only access · changes need the patient's permission", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("embedded-read-only"))
                }
            }
            if (tool.isNotEmpty() && tool !in listOf("scanner", "feedback", "reminders")) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::leaveTool, enabled = !navigating, modifier = Modifier.heightIn(min = 48.dp).testTag("embedded-care-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Back to ${if (section == "Tools") "tools" else "patient care"}") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (refreshing) Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Refreshing this person's access and records…") }
                else key(account, personId, section, tool) { screens.SaveableStateProvider("$account:$personId:$section:$tool") {
                    when (tool) {
                        "scanner" -> if (canEdit) MedicineScanner(vm, onClose = { tool = "" }, onAccount = onAccount) else Text("Editing access is no longer available.", Modifier.padding(20.dp))
                        "daily" -> CareList(vm, ::add, { openTool("scanner") }, { openTool("calendar") }, { openTool("workflows") }, { openTool("labs") }, onReadNote = ::read)
                        "calendar" -> CareCalendar(vm, onWorkflow = { openTool("workflows") }, onAddDoctorVisit = { add("doctor-visit") })
                        "workflows" -> CareWorkflows(vm, onTalk = { changeSection("Conversations") })
                        "labs" -> CareLabs(vm)
                        "nearby" -> Nearby(vm.centres)
                        "documents" -> CareDocuments(vm, onRead = ::read, onTalk = { changeSection("Conversations") })
                        "care-story" -> DoctorCareStory(person, versions, ::read)
                        "checkin-history" -> DoctorCheckInHistory(personId, entries)
                        "care-team" -> DoctorCareTeam(vm, personId)
                        "reminders" -> CareReminderSettings(vm, onClose = { tool = "" })
                        "feedback" -> FeedbackScreen(account, "doctor-patient", onClose = { tool = "" })
                        "shared" -> EmbeddedSharedCare(vm, onAccount, ::leaveTool, onNavigationBlockedChange = { sharedBusy = it })
                        "resources" -> More(vm, person, onAccount, onDelete = { if (samePerson() && vm.isOwner.value) dialog = "delete" }, onCalendar = { openTool("calendar") }, onWorkflow = { openTool("workflows") }, onLabs = { openTool("labs") }, onFeedback = { openTool("feedback") }, onReminders = { openTool("reminders") }, onSharedWorkspace = { openTool("shared") }, onDocuments = { openTool("documents") }, doctorEmbedded = true)
                        else -> when (section) {
                            "Summary" -> DoctorPatientDay(vm, person, onOverview = { openTool("care-story") }, onCheckIns = { openTool("checkin-history") }, onMedicines = { changeSection("Medicines") }, onCalendar = { changeSection("Visits") }, onLabs = { openTool("labs") }, onCareTeam = { openTool("care-team") }, onAddVisit = { add("doctor-visit") }, onReadNote = ::read)
                            "Progress" -> DoctorCheckInHistory(personId, entries)
                            "Medicines" -> DoctorMedicines(vm, personId, onAddManual = { add("medicine") }, onScanPrescription = { openTool("scanner") }, onNavigationBlockedChange = { medicineBusy = it })
                            "Conversations" -> DoctorConversations(vm, canEdit = canEdit, onRead = ::read, onRequestConversation = { openTool("workflows") }, onInteractionActiveChange = { talkInteraction = it }, onHeaderBackChange = { conversationBack = it }, onCaptureHeroChange = { conversationCapture = it })
                            "Visits" -> DoctorVisits(vm,personId,onCalendar={openTool("calendar")},onAddVisit={add("doctor-visit")},onNavigationBlockedChange={visitBusy=it},onHeaderBackChange={visitBack=it})
                            "Reports" -> CareDocuments(vm, onRead = ::read, onTalk = { changeSection("Conversations") })
                            "Shared" -> EmbeddedSharedCare(vm, onAccount, { changeSection("Summary") }, onNavigationBlockedChange = { sharedBusy = it })
                            else -> EmbeddedCareTools(::openTool)
                        }
                    }
                } }
            }
            refreshError?.let { Surface(color = Peach) { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = vm::refresh, enabled = !busy) { Text("Retry") } } } }
        }
        SnackbarHost(snackbar)
    }
    if (ready && dialog in listOf("medicine", "visit", "task", "journal") && canEdit) AddCareItem(dialog, vm, onDismiss = { dialog = "" })
    if (ready && dialog == "doctor-visit" && canEdit) DoctorVisitEditor(vm, personId, onDismiss = { dialog = "" })
    if (ready && dialog == "delete" && isOwner) AlertDialog(onDismissRequest = { if (!busy) dialog = "" }, title = { Text("Remove ${person?.name}'s records?") }, text = { Text("This removes their notes, reports, recordings and care entries. Export anything you want to keep first.") }, confirmButton = { TextButton(onClick = { if (samePerson() && vm.isOwner.value && !vm.busy.value) vm.removePerson { dialog = ""; onExit() } }, enabled = !busy) { Text("Remove records") } }, dismissButton = { TextButton(onClick = { dialog = "" }, enabled = !busy) { Text("Keep records") } })
    if (ready) reading?.takeIf { it.personId == personId }?.let { NoteReader(vm, it, onDismiss = { reading = null }) }
}

/** Existing verification/review controls without another Scaffold or navigation bar. */
@Composable
fun EmbeddedSharedCare(vm: CareModel, onAccount: () -> Unit, onClose: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) {
    CareSharedWorkspace(vm, onAccount, onClose, embedded = true, onBusyChange = onNavigationBlockedChange)
}

@Composable
internal fun EmbeddedCareTools(onOpen: (String) -> Unit) {
    val tools = listOf(
        Triple("daily", "Daily care", "Medicines, tasks, check-ins and journal"),
        Triple("calendar", "Calendar", "Planned care activities and visits"),
        Triple("workflows", "Care team & requests", "Existing shared-care tasks and discussion requests"),
        Triple("labs", "Lab history", "Reported results and earlier entries"),
        Triple("nearby", "Find care", "Nearby services and contact information"),
        Triple("documents", "Documents & care notes", "Local reports, saved versions and sharing"),
        Triple("reminders", "Reminders", "Medicine and dated-care notifications on this device"),
        Triple("shared", "Shared care & clinical review", "Patient access, verification and submitted conversations"),
        Triple("resources", "Privacy, exports & more", "Export care notes and manage this care profile"),
        Triple("feedback", "App feedback", "Tell us about your experience"),
    )
    LazyColumn(Modifier.fillMaxSize().testTag("embedded-care-tools"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Heading("Daily care & tools", 28); Text("Everything for this person, without leaving the doctor workspace.", style = MaterialTheme.typography.bodyMedium) }
        items(tools, key = { it.first }) { (key, title, subtitle) ->
            OutlinedCard(onClick = { onOpen(key) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("embedded-tool-$key")) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Rust)
                }
            }
        }
    }
}
