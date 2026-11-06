@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun connectedTime(at: Long) = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))
private fun JSONArray?.strings(): List<String> = this?.let { array -> (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
private fun ConnectedDoctorPatient.card() = DoctorDemoPatient(id, name, age, careContext, summary, payload.optJSONArray("careNotes").strings(), payload.optJSONArray("medicines").strings(), payload.optJSONArray("reportSummaries").strings(), payload.optJSONArray("observations").strings())

/** Authenticated synthetic records. Never imports the independent local walkthrough. */
@Composable fun ConnectedDoctorWorkspace(cloud: CareCloud, vm: CareModel, onExit: () -> Unit, onAccount: () -> Unit, onWalkthrough: (String) -> Unit = {}) {
    val session by cloud.session.collectAsStateWithLifecycle()
    val userId = session?.userId ?: return
    key(userId) { ConnectedDoctorBody(cloud, vm, session!!.email, userId, onExit, onAccount, onWalkthrough) }
}

@Composable private fun ConnectedDoctorBody(cloud: CareCloud, vm: CareModel, email: String, userId: String, onExit: () -> Unit, onAccount: () -> Unit, onWalkthrough: (String) -> Unit) { val t = careText();
    val context = LocalContext.current
    val planner = remember(userId) { ConnectedDoctorPlanner(context, userId) }
    val initialPlanner = remember(userId) { runCatching { planner.visits() to planner.requests() } }
    val scope = rememberCoroutineScope()
    val realPeople by vm.people.collectAsStateWithLifecycle()
    var signingOut by remember { mutableStateOf(false) }
    var dataset by remember { mutableStateOf<ConnectedDoctorDataset?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val selectionSaver = remember { Saver<DoctorPatientSelection?, List<String>>(save = { it?.let { value -> listOf(value.accountId, value.source.name, value.id) } ?: emptyList() }, restore = { values -> if (values.size == 3) runCatching { DoctorPatientSelection(values[0], DoctorPatientSource.valueOf(values[1]), values[2]) }.getOrNull() else null }) }
    var selection by rememberSaveable(userId, stateSaver = selectionSaver) { mutableStateOf<DoctorPatientSelection?>(null) }
    val patientId = selection?.takeIf { it.accountId == userId && it.source == DoctorPatientSource.TEST }?.id
    val realPatientId = selection?.takeIf { it.accountId == userId && it.source == DoctorPatientSource.SHARED }?.id
    var navigationBlocked by remember { mutableStateOf(false) }
    var nestedBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var section by rememberSaveable { mutableStateOf("Hub") }
    var shared by rememberSaveable { mutableStateOf(false) }
    var isolatedDemo by rememberSaveable(userId) { mutableStateOf(false) }
    var testCarePerson by rememberSaveable(userId) { mutableStateOf(DoctorCareDemoData.PERSON_ID) }
    var testInitialSection by rememberSaveable(userId) { mutableStateOf("Hub") }
    var earlierWorkspace by rememberSaveable(userId) { mutableStateOf(false) }
    var schedulePatient by remember { mutableStateOf<String?>(null) }
    var editingVisit by remember { mutableStateOf<DoctorDemoAppointment?>(null) }
    var requestsOpen by rememberSaveable { mutableStateOf(false) }
    var requestStates by rememberSaveable { mutableStateOf(initialPlanner.getOrNull()?.second ?: "{}") }
    val editorStates = rememberSaveableStateHolder()
    var localVisits by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.listSaver(
        save = { rows: List<DoctorDemoAppointment> -> rows.map { org.json.JSONObject().put("id",it.id).put("patient",it.patientId).put("at",it.startMillis).put("duration",it.durationMinutes).put("type",it.type).put("status",it.status).put("note",it.note).toString() } },
        restore = { values -> values.map { org.json.JSONObject(it).let { row -> DoctorDemoAppointment(row.getString("id"),row.getString("patient"),row.getLong("at"),row.getInt("duration"),row.getString("type"),row.getString("status"),row.getString("note")) } } }
    )) { mutableStateOf(initialPlanner.getOrNull()?.first ?: emptyList<DoctorDemoAppointment>()) }
    LaunchedEffect(localVisits, requestStates) {
        if(initialPlanner.isSuccess) runCatching { planner.save(localVisits, requestStates) }.onFailure { error = it.message }
        else error = t("Couldn't load visits", "विज़िट नहीं खुल सकीं")
    }
    fun run(action: suspend () -> Unit) {
        if (!busy) { busy = true; error = null; scope.launch { try { action() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: t("Could not load your records. Try again.", "आपके रिकॉर्ड नहीं खुल सके। फिर कोशिश करें।") } finally { busy = false } } }
    }
    fun refresh() = run { dataset = cloud.loadDoctorDataset(); loaded = true }
    // The old synthetic server workspace loads only when its practice screens are opened, never on entry.
    LaunchedEffect(earlierWorkspace) { if (earlierWorkspace && !loaded) refresh() }
    val hindi = doctorUiHindi()
    val allItems by remember(vm) { vm.allCareItems() }.collectAsStateWithLifecycle(emptyList())
    var clinician by remember { mutableStateOf<CareClinicianProfile?>(null) }
    var clinicianLoaded by remember { mutableStateOf(false) }
    var clinicianChecking by remember { mutableStateOf(false) }
    var clinicianCheckedAt by remember { mutableLongStateOf(0L) }
    var clinicianError by remember { mutableStateOf<String?>(null) }
    fun checkClinician() {
        if (clinicianChecking) return
        clinicianChecking = true; clinicianError = null
        scope.launch {
            try { clinician = cloud.clinicianProfile(); clinicianLoaded = true; clinicianCheckedAt = System.currentTimeMillis() }
            catch (e: Exception) { if (e is CancellationException) throw e; clinicianError = careTr(hindi, "Couldn't check your status. Check your internet and try again.", "स्थिति नहीं मिल पाई। इंटरनेट देखें और फिर से कोशिश करें।") }
            finally { clinicianChecking = false }
        }
    }
    LaunchedEffect(userId) { vm.refresh(); checkClinician() }
    LaunchedEffect(shared) { if (!shared && clinicianLoaded) checkClinician() }
    val verification = DoctorConnectedHome.verification(clinician, clinicianLoaded)
    var patientQuery by rememberSaveable(userId) { mutableStateOf("") }
    val homeList = rememberLazyListState()
    var joinCode by rememberSaveable(userId) { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var joinMessage by remember { mutableStateOf<String?>(null) }
    var joinedId by remember { mutableStateOf<String?>(null) }
    var joinFailed by remember { mutableStateOf(false) }
    fun joinPatient() {
        val code = joinCode.trim()
        if (joining || code.isEmpty()) return
        joining = true; joinMessage = null; joinedId = null; joinFailed = false
        scope.launch {
            try {
                val before = vm.people.value.map { it.id }.toSet()
                cloud.join(code)
                val added = vm.joinedRefresh(before)
                joinCode = ""
                joinedId = added?.id
                joinMessage = added?.let { careTr(hindi, "${it.name} added", "${it.name} जुड़ गए") }
                    ?: careTr(hindi, "Joined", "जुड़ गए")
            } catch (e: Exception) { if (e is CancellationException) throw e; joinFailed = true; joinMessage = careJoinFailureMessage(e.message, hindi); if (e.message?.startsWith("Clinician verification") == true) checkClinician() }
            finally { joining = false }
        }
    }
    val patient = dataset?.patients?.firstOrNull { it.id == patientId }
    val realPatient = realPeople.firstOrNull { it.id == realPatientId }
    val selectedRealId by vm.selected.collectAsStateWithLifecycle()
    val realDraft by vm.draft.collectAsStateWithLifecycle()
    val realRefreshing by vm.refreshing.collectAsStateWithLifecycle()
    val realRefreshError by vm.refreshError.collectAsStateWithLifecycle()
    fun navigate(action: () -> Unit) { if (!navigationBlocked) { if (!isolatedDemo && vm.recording.value) vm.stopRecording(); action() } }
    fun openTest(id: String) = navigate { selection = DoctorPatientSelection(userId, DoctorPatientSource.TEST, id); section = "Hub"; tab = 1; shared = false }
    fun openReal(id: String) = navigate { vm.select(id); selection = DoctorPatientSelection(userId, DoctorPatientSource.SHARED, id); section = "Hub"; tab = 1; shared = false }
    fun openShared() = navigate { selection = null; shared = true; tab = 1 }
    fun back() { if (!navigationBlocked && nestedBack != null) nestedBack?.invoke() else navigate { if (isolatedDemo) isolatedDemo = false else if (shared) shared = false else if (selection != null) { if (section == "Hub") selection = null else section = "Hub" } else if (tab != 0) tab = 0 else onExit() } }
    BackHandler { back() }
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Column { Text(if (isolatedDemo) DoctorTestProfiles.profiles.firstOrNull {DoctorTestProfiles.personId(it)==testCarePerson}?.name ?: t("Earlier practice care", "पहले की प्रैक्टिस देखभाल") else realPatient?.name ?: patient?.name ?: if (selection != null) t("Patient record", "मरीज़ का रिकॉर्ड") else if (shared) t("Shared care", "साझा देखभाल") else t("Sahara", "सहारा"), fontFamily = SaharaTitleFont, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }, navigationIcon = { IconButton(onClick = ::back, enabled = !navigationBlocked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) } }, actions = { CareLanguageMenu(compact = true, contentColor = Color.White) },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = DoctorBand, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)) }, bottomBar = {
        NavigationBar(containerColor = Paper) { listOf(t("Home", "होम") to Icons.Default.Home,t("Patients", "मरीज़") to Icons.Default.People,t("Schedule", "शेड्यूल") to Icons.Default.CalendarMonth,t("Account", "खाता") to Icons.Default.Person).forEachIndexed { index, (label, icon) -> NavigationBarItem(selected = if (selection != null || shared || isolatedDemo) index == 1 else tab == index, enabled = !navigationBlocked, onClick = { navigate { tab = index; selection = null; shared = false; isolatedDemo = false; nestedBack = null } }, icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1, softWrap = false) },
            colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.White, selectedTextColor = DoctorTones.Rust.ink, indicatorColor = DoctorTones.Rust.solid, unselectedIconColor = DoctorCardSub, unselectedTextColor = DoctorCardSub)) } }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) SaharaProgressBar(null)
            error?.let { message -> Surface(color = Peach) { Row(Modifier.fillMaxWidth().padding(12.dp)) { Text(message, Modifier.weight(1f).announce()); TextButton(onClick = ::refresh, enabled = !busy) { Text(t("Retry", "फिर कोशिश करें"), color = RustText) } } } }
            if (isolatedDemo) DoctorCareDemoHost(onExit = { isolatedDemo = false; nestedBack = null; navigationBlocked = false;testInitialSection="Hub" }, onNavigationBlockedChange = { navigationBlocked = it }, onHeaderBackChange = { nestedBack = it },initialPersonId=testCarePerson,initialSection=testInitialSection)
            else if (shared) EmbeddedSharedCare(vm, onAccount, { shared = false }, onNavigationBlockedChange = { navigationBlocked = it })
            else if (realPatientId != null) {
                if (!doctorSharedPatientReady(selection, userId, realPeople.map { it.id }.toSet(), selectedRealId, realDraft.personId)) {
                    // Opening: a skeleton of the hub. Not available: one error line with Try again, and the way back.
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (realPatient == null && !realRefreshing) {
                            SaharaErrorState(t("Not available", "उपलब्ध नहीं"), detail = realRefreshError, actionLabel = t("Retry", "फिर कोशिश करें"), onAction = { vm.refresh() })
                            SaharaSecondaryButton(t("Back to patients", "मरीज़ों पर वापस"), { navigate { selection = null } }, Modifier.fillMaxWidth(), navigates = true)
                        } else {
                            SaharaSkeleton(rows = 4)
                            realRefreshError?.let { SaharaErrorState(it, actionLabel = t("Retry", "फिर कोशिश करें"), onAction = { vm.refresh() }) }
                        }
                    }
                } else DoctorPatientWorkspace(vm, realPatientId, section, { next -> navigate { section = next } },
                    hub = { pane, open -> DoctorSharedPatientHub(realPatient!!, onSection = { next -> navigate { section = next } }, items = vm.items, pane = pane, selected = open) },
                    care = { shown -> key(selection!!.stateKey) {
                        editorStates.SaveableStateProvider("${selection!!.stateKey}:care") {
                            EmbeddedPatientCare(vm, realPatientId, shown, { next -> section = next }, onAccount, { selection = null }, onNavigationBlockedChange = { navigationBlocked = it }, onHeaderBackChange = { nestedBack = it })
                        }
                    } })
            } else if (patientId != null && patient == null) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(if (loaded) t("Test record unavailable", "टेस्ट रिकॉर्ड उपलब्ध नहीं") else t("Loading test records…", "टेस्ट रिकॉर्ड खुल रहे हैं…")); TextButton(onClick = { selection = null }) { Text(t("Back to patients", "मरीज़ों पर वापस")) } }
            } else if (patient != null) {
                when (section) {
                    "Conversations" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:notes") { ConnectedNoteEditor(cloud, userId, patient, dataset!!.notes.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Reports" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:files") { ConnectedFiles(cloud, patient, dataset!!.files.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Medicines" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:medicines") { ConnectedMedicines(cloud, patient, dataset!!.doses.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Visits" -> ConnectedVisits(patient, localVisits, onSchedule = { schedulePatient = patient.id }, onUpdate = { changed -> localVisits = localVisits.map { if (it.id == changed.id) changed else it } })
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Text(patient.careContext, style = MaterialTheme.typography.titleMedium) }
                        if (section == "Hub") {
                            item { DoctorPatientHub(patient.card(), onSection = { section = it }) }
                        }
                        else if (section == "Summary") {
                            item { ConnectedCard(t("Care overview", "देखभाल का सार")) { Text(patient.summary, lineHeight = 25.sp) } }
                            item { ConnectedCard(t("Previous doctor", "पिछले डॉक्टर")) { Text(patient.payload.optString("previousDoctor").ifBlank { t("Not recorded", "दर्ज नहीं") }) } }
                            item { ConnectedCard(t("Last conversation", "पिछली बातचीत")) { val note = dataset!!.notes.filter { it.patientId == patient.id }.maxByOrNull { it.updatedAt }; Text(note?.draft?.topics?.ifBlank { t("Not recorded", "दर्ज नहीं") } ?: t("Not recorded", "दर्ज नहीं")); TextButton(onClick = { section = "Conversations" }) { Text(t("Open", "खोलें")) } } }
                        } else {
                            item { Text(t("Progress", "प्रगति"), style = MaterialTheme.typography.headlineSmall) }
                            val checks = patient.payload.optJSONArray("checkIns")
                            if (checks == null || checks.length() == 0) item { Text(t("No updates yet.", "अभी कोई अपडेट नहीं।")) }
                            else items(checks.length()) { index -> val row = checks.getJSONObject(index); ConnectedCard(row.optString("source", t("Reported update", "बताया गया अपडेट"))) { Text(row.optString("text"), lineHeight = 25.sp); row.optLong("at").takeIf { it > 0 }?.let { Text(connectedTime(it), style = MaterialTheme.typography.bodySmall) } } }
                            item { TextButton(onClick = { section = "Medicines" }) { Text(t("Medicines", "दवाइयाँ")) } }
                        }
                    }
                }
            } else when(tab) {
                0 -> CompositionLocalProvider(LocalSaharaStagger provides remember { SaharaStagger() }) { LazyColumn(Modifier.fillMaxSize().testTag("doctor-home"), state = homeList, contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", careLocale(hindi))), Modifier.weight(1f).a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk)
                        if (!earlierWorkspace) IconButton(onClick = { vm.refresh(); checkClinician() }, enabled = !realRefreshing, modifier = Modifier.size(48.dp).testTag("doctor-home-refresh")) { if (realRefreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = DoctorAccent) else Icon(Icons.Default.Refresh, t("Refresh records", "रिकॉर्ड रिफ़्रेश करें"), tint = DoctorAccent) } } }
                    if (earlierWorkspace) {
                        item { ConnectedCard(t("Practice records", "अभ्यास रिकॉर्ड")) { TextButton(onClick = { requestsOpen = true }) { Text(t("Requests", "अनुरोध")) }; TextButton(onClick = { tab = 2 }) { Text(t("Schedule", "शेड्यूल")) }; TextButton(onClick = { earlierWorkspace = false }) { Text(t("Close", "बंद करें")) } } }
                    } else {
                        val showVerification = verification != DoctorConnectedHome.Verification.APPROVED
                        if (showVerification) item { DoctorVerificationCard(verification, clinician?.note.orEmpty(), clinicianCheckedAt, clinicianChecking, clinicianError, hindi, ::checkClinician, ::openShared) }
                        realRefreshError?.let { item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(it, Modifier.weight(1f).announce(), color = MaterialTheme.colorScheme.error); TextButton(onClick = { vm.refresh(); checkClinician() }, enabled = !realRefreshing, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Retry", "फिर कोशिश करें"), color = RustText) } } } }
                        doctorConnectedHomeItems(realPeople, allItems, System.currentTimeMillis(), hindi, onVisit = { openReal(it); section = "Visits" }, onProgress = { openReal(it); section = "Progress" }, onPatients = { tab = 1 },
                            onScrollTo = { index -> scope.launch { homeList.animateScrollToItem(index) } }, firstIndex = 1 + (if (showVerification) 1 else 0) + (if (realRefreshError != null) 1 else 0))
                    }
                } }
                1 -> {
                    val shown = DoctorConnectedHome.search(realPeople, patientQuery)
                    val t = { en: String, hi: String -> careTr(hindi, en, hi) }
                    val now = System.currentTimeMillis()
                    CompositionLocalProvider(LocalSaharaStagger provides remember { SaharaStagger() }) { LazyColumn(Modifier.fillMaxSize().testTag("doctor-patients-list"), contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(t("Patients", "मरीज़"), Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk); if (realPeople.isNotEmpty()) DoctorPill(if (patientQuery.isBlank()) "${realPeople.size}" else "${shown.size}/${realPeople.size}", DoctorTones.Rust) } }
                        if (realPeople.isNotEmpty()) item { DoctorPatientSearchField(patientQuery, { patientQuery = it }, hindi) }
                        if (realPeople.isEmpty()) item(key = "patients-empty-art") { SaharaIllustration(SaharaArtKind.NoPatients, Modifier.fillMaxWidth()) }
                        if (realPeople.isEmpty()) item { SaharaEmpty(t("No patients yet. Add one with their invitation code.", "अभी कोई मरीज़ नहीं। उनके निमंत्रण कोड से जोड़ें।"), icon = Icons.Default.People, tone = DoctorTones.Rust) }
                        else if (shown.isEmpty()) item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(t("No matching patient", "कोई मरीज़ नहीं मिला"), Modifier.weight(1f), color = DoctorCardSub, fontSize = 16.sp); TextButton(onClick = { patientQuery = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear search", "खोज साफ़ करें"), color = RustText) } } }
                        items(shown, key = { "shared:${it.id}" }) { person -> DoctorPatientListCard(person.name, DoctorConnectedHome.patientCode(person.id), { openReal(person.id) }, pill = doctorPatientStatus(person, allItems, now, hindi)) }
                        item { ConnectedCard(t("Add a patient", "मरीज़ जोड़ें")) {
                            if (verification != DoctorConnectedHome.Verification.APPROVED) DoctorPill(DoctorConnectedHome.verificationTitle(verification, hindi), DoctorTones.Marigold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(joinCode, { joinCode = it.take(120); joinMessage = null }, Modifier.weight(1f).testTag("doctor-join-code"), singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text(t("Invitation code", "निमंत्रण कोड"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { joinPatient() }))
                                Button(onClick = ::joinPatient, enabled = !joining && joinCode.isNotBlank(), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = DoctorTones.Rust.solid, contentColor = Color.White), modifier = Modifier.heightIn(min = 56.dp).testTag("doctor-join")) { if (joining) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text(t("Join", "जुड़ें"), softWrap = false) }
                            }
                        } }
                    } }
                }
                2 -> {
                    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
                    val patients = dataset?.patients.orEmpty()
                    Column(Modifier.fillMaxSize()) {
                        if(earlierWorkspace) {
                            TextButton(onClick={earlierWorkspace=false}){Text(t("Close", "बंद करें"))}
                            if (patients.isNotEmpty()) Box(Modifier.weight(1f)) { DemoSchedule(patients.map { it.card() }, localVisits, LocalDate.parse(day), { day = it.toString() }, ::openTest, { editingVisit = null; schedulePatient = patients.first().id }, { editingVisit = it; schedulePatient = it.patientId }, { visit -> localVisits = localVisits.map { if (it.id == visit.id) it.copy(status = "Cancelled") else it } }, { visit -> localVisits = localVisits.map { if (it.id == visit.id) it.copy(status = "Completed") else it } }) } else Text(t("No visits", "कोई विज़िट नहीं"), Modifier.padding(20.dp))
                        } else DoctorConnectedSchedule(vm, realPeople, allItems, onOpenPatient = { openReal(it); section = "Visits" }, onAllPatients = { tab = 1 }, modifier = Modifier.weight(1f))
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { DoctorVerificationCard(verification, clinician?.note.orEmpty(), clinicianCheckedAt, clinicianChecking, clinicianError, hindi, ::checkClinician, ::openShared) }
                    item { ConnectedCard(t("Your account", "आपका खाता")) { Text(email, style = MaterialTheme.typography.titleLarge); Button(onClick = onAccount) { Text(t("Manage account", "खाता संभालें")) } } }
                    item { OutlinedButton(onClick = ::openShared, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Shared care", "साझा देखभाल")) } }
                    item { TextButton(onClick = onExit) { Text(t("Back to Sahara", "सहारा पर वापस")) } }
                    item { OutlinedButton(onClick = { signingOut = true }, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("account-sign-out")) { Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Sign out", "साइन आउट करें"), fontWeight = FontWeight.SemiBold) } }
                }
            }
        }
    }
    schedulePatient?.let { selected -> val patients = dataset?.patients.orEmpty(); if (patients.isNotEmpty()) DemoAppointmentEditor(patients.map { it.card() }, selected, editingVisit, LocalDate.now(), onDismiss = { schedulePatient = null; editingVisit = null }, onSave = { visit -> localVisits = localVisits.filterNot { it.id == visit.id } + visit; schedulePatient = null; editingVisit = null; null }) }
    // The join result is a small dialog, not a line that is easy to miss; a new patient opens in one tap.
    joinMessage?.let { message -> AlertDialog(onDismissRequest = { joinMessage = null },
        icon = { Icon(if (joinFailed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle, null, tint = if (joinFailed) MaterialTheme.colorScheme.error else DoctorTones.Leaf.ink) },
        title = { Text(message, modifier = Modifier.testTag("doctor-join-result")) },
        confirmButton = { val id = joinedId; if (!joinFailed && id != null) Button(onClick = { joinMessage = null; openReal(id) }, colors = ButtonDefaults.buttonColors(containerColor = DoctorTones.Rust.solid, contentColor = Color.White), modifier = Modifier.testTag("doctor-join-open")) { Text(t("Open", "खोलें")) } else TextButton(onClick = { joinMessage = null }) { Text(t("OK", "ठीक है")) } },
        dismissButton = { if (!joinFailed && joinedId != null) TextButton(onClick = { joinMessage = null }) { Text(t("Close", "बंद करें")) } }) }
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text(t("Sign out?", "साइन आउट करें?")) }, confirmButton = { TextButton(onClick = { signingOut = false; vm.stopRecording(); scope.launch { cloud.signOut() } }, modifier = Modifier.testTag("account-sign-out-confirm")) { Text(t("Sign out", "साइन आउट करें")) } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text(t("Cancel", "रद्द करें")) } })
    if(requestsOpen) ModalBottomSheet(onDismissRequest = {requestsOpen=false},containerColor=Cream) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {Text(t("Requests", "अनुरोध"),style=MaterialTheme.typography.headlineSmall)}
            items(dataset?.patients.orEmpty().take(4),key={it.id}) {p-> val status=org.json.JSONObject(requestStates).optString(p.id,"New");ConnectedCard(p.name) {Text(status,color=Rust);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Accepted","Deferred","Completed").forEach {next->TextButton(onClick={requestStates=org.json.JSONObject(requestStates).put(p.id,next).toString()}){Text(next)}}};TextButton(onClick={requestsOpen=false;openTest(p.id);section="Conversations"}){Text(t("Open", "खोलें"))}}}
            item {TextButton(onClick={requestsOpen=false}){Text(t("Close", "बंद करें"))}}
        }
    }
}

/** Discussed / Not discussed / Later, in the chosen language, never the raw stored value. */
private fun connectedTopicLabel(status: WorkflowTopicStatus, t: (String, String) -> String) = when (status) {
    WorkflowTopicStatus.NOT_DISCUSSED -> t("Not discussed", "बात नहीं हुई")
    WorkflowTopicStatus.DISCUSSED -> t("Discussed", "बात हुई")
    WorkflowTopicStatus.DEFERRED -> t("Later", "बाद में")
}

@Composable private fun ConnectedCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, modifier = Modifier.a11yHeading()); content() } }
}

/** One patient in a register: coloured initial, name, one line of detail, optional status pill. */
@Composable internal fun DoctorPatientListCard(name: String, description: String, onClick: () -> Unit, pill: Pair<String, DoctorTone>? = null) =
    DoctorRegisterRow(name, description, onClick, pill = pill)

/**
 * The patient's home on the doctor side, top to bottom: who they are (one header), what a doctor does here (the ordered
 * section list, Goals of care first), a glance at their worst symptoms, then the two less used routes. [pane]: drawn as the left list beside an open section.
 */
@Composable internal fun DoctorSharedPatientHub(person: Person, onSection: (String) -> Unit, deviceLocal: Boolean = false, items: kotlinx.coroutines.flow.StateFlow<List<CareItem>>? = null, pane: Boolean = false, selected: String? = null) { val t = careText();
    val entries = items?.collectAsStateWithLifecycle()?.value
    CompositionLocalProvider(LocalSaharaStagger provides remember(person.id) { SaharaStagger() }) {
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-shared-patient-hub"), contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DoctorPatientHeader(person, entries, showId = !deviceLocal && !DoctorTestProfiles.contains(person.id), pane = pane) }
        item { DoctorPatientNavigationTiles(onSection, selected = selected) }
        if (entries != null && !pane) item { DoctorSymptomSummary(entries, person.id, limit = 3) }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SaharaSectionHeader(t("More for this patient", "इस मरीज़ के लिए और"), Modifier.padding(top = 4.dp), tone = DoctorTones.Indigo, icon = Icons.Default.MoreHoriz)
            Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear()) {
                Column {
                    DoctorPlainRoute(Icons.Default.Apps, t("Daily care", "रोज़ की देखभाल"), { onSection("Tools") }, "doctor-shared-tools")
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = DoctorHairline)
                    DoctorPlainRoute(Icons.Default.Lock, t("Sharing & access", "साझा करना और पहुँच"), { onSection("Shared") }, "doctor-shared-access")
                }
            }
        } }
    }
    }
}

/** Patient header: initial, name, "age · sex · illness", one status line, patient ID, allergies when recorded. The full profile is one tap away. */
@Composable private fun DoctorPatientHeader(person: Person, entries: List<CareItem>?, showId: Boolean, pane: Boolean) { val t = careText(); val hindi = careHindi()
    val profile = remember(entries, person.id) { entries?.let { all -> PatientProfileCodec.latest(all, person.id)?.let { PatientProfileCodec.decode(it.detail) } } }
    var details by remember(person.id) { mutableStateOf(false) }
    val now = remember(entries) { System.currentTimeMillis() }
    val tone = DoctorTones.forName(person.name)
    val facts = listOfNotNull(
        profile?.age?.let { t("$it years", "$it साल") },
        when (profile?.sex) { "Female" -> t("Female", "महिला"); "Male" -> t("Male", "पुरुष"); "Other" -> t("Other", "अन्य"); else -> null },
        profile?.illness?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    // One status line: what needs attention first, otherwise the latest check-in.
    val status: Pair<String, DoctorTone> = entries?.let { all ->
        doctorPatientStatus(person, all, now, hindi) ?: doctorCheckIns(all, person.id, now).firstOrNull()?.let { check ->
            t("Last check-in: ", "आख़िरी हालचाल: ") + checkInMoodLabel(check.title, hindi) + " · " + DateTimeFormatter.ofPattern("d MMM", careLocale(hindi)).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(check.created)) to DoctorTones.Teal
        }
    } ?: (t("No check-ins yet", "अभी कोई हालचाल नहीं") to DoctorTones.Teal)
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().saharaAppear().testTag("doctor-patient-header")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).background(tone.solid, CircleShape), contentAlignment = Alignment.Center) { Text(person.name.trim().take(1).uppercase(), color = tone.onSolid, fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(person.name, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = if (pane) 20.sp else 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, style = DoctorWholeWords)
                    if (facts.isNotBlank()) Text(facts, fontSize = 15.sp, lineHeight = 20.sp, color = DoctorCardSub, style = DoctorWholeWords, modifier = Modifier.testTag("doctor-patient-facts"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(status.second.solid, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(status.first, Modifier.weight(1f).testTag("doctor-patient-status"), fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = status.second.ink, style = DoctorWholeWords)
            }
            profile?.allergies?.takeIf { it.isNotBlank() }?.let { allergies ->
                Text(t("Allergies", "एलर्जी") + ": " + allergies, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = DoctorTones.Attention.ink, style = DoctorWholeWords, modifier = Modifier.testTag("doctor-patient-allergies"))
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                if (showId) DoctorPill(t("Patient ID ", "मरीज़ ID ") + DoctorConnectedHome.patientCode(person.id), DoctorTones.Rust, Modifier.align(Alignment.CenterVertically).testTag("doctor-shared-patient-id"))
                if (entries != null) TextButton(onClick = { details = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-patient-details")) { Text(t("Profile details", "प्रोफ़ाइल की जानकारी"), color = RustText, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
    if (details && entries != null) AlertDialog(onDismissRequest = { details = false }, title = { Text(t("Profile details", "प्रोफ़ाइल की जानकारी")) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { DoctorPatientProfileCard(entries, person) } },
        confirmButton = { TextButton(onClick = { details = false }, modifier = Modifier.testTag("doctor-patient-details-close")) { Text(t("Close", "बंद करें")) } })
}

/** A secondary route in a plain list: icon, name of the place it opens, arrow. */
@Composable private fun DoctorPlainRoute(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, tag: String) {
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Surface(onClick = onClick, interactionSource = press, color = Paper, modifier = Modifier.fillMaxWidth().saharaPress(press).heightIn(min = 56.dp).testTag(tag)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RustText, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
            Text(label, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, style = DoctorWholeWords)
            Icon(Icons.Default.ChevronRight, null, tint = DoctorCardSub, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable private fun ConnectedVisits(patient: ConnectedDoctorPatient, visits: List<DoctorDemoAppointment>, onSchedule: () -> Unit, onUpdate: (DoctorDemoAppointment) -> Unit) { val t = careText();
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(t("Visits", "विज़िट"), style = MaterialTheme.typography.headlineSmall); Button(onClick = onSchedule) { Text(t("Schedule visit", "विज़िट तय करें")) } }
        items(visits.filter { it.patientId == patient.id }, key = { it.id }) { visit -> ConnectedCard(connectedTime(visit.startMillis)) { Text("${visit.type} · ${visit.durationMinutes} minutes"); Text(visit.status); Text(visit.note); TextButton(onClick = { onUpdate(visit.copy(status = "Completed")) }) { Text(t("Mark complete", "पूरी हुई")) }; TextButton(onClick = { onUpdate(visit.copy(status = "Cancelled")) }) { Text(t("Cancel visit", "विज़िट रद्द करें")) } } }
    }
}

@Composable private fun ConnectedNoteEditor(cloud: CareCloud, accountId: String, patient: ConnectedDoctorPatient, notes: List<ConnectedDoctorNote>, onSaved: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) { val t = careText();
    val current = notes.filter { it.status == "draft" }.maxByOrNull { it.updatedAt }
    val context = LocalContext.current
    val localStore = remember(accountId) { ConnectedDoctorPlanner(context, accountId) }
    val recovered = remember(accountId, patient.id) {
        runCatching {
            localStore.draft(patient.id)?.let { text ->
                org.json.JSONObject(text).also {
                    decodeConnectedDoctorDraft(patient.id, it.getJSONObject("draft"))
                    java.util.UUID.fromString(it.getString("noteId"))
                    require(it.getLong("revision") >= 0)
                }
            }
        }
    }
    val initialCache = recovered.getOrNull()
    val scope = rememberCoroutineScope()
    val draftSaver = remember(patient.id) { Saver<WorkflowDraft, String>(save = { encodeConnectedDoctorDraft(it).toString() }, restore = { decodeConnectedDoctorDraft(patient.id, org.json.JSONObject(it)) }) }
    var draft by rememberSaveable(patient.id, stateSaver = draftSaver) { mutableStateOf(initialCache?.let { decodeConnectedDoctorDraft(patient.id,it.getJSONObject("draft")) } ?: current?.draft ?: WorkflowDraft(patient.id)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var noteId by rememberSaveable(patient.id) { mutableStateOf(initialCache?.optString("noteId")?.takeIf { it.isNotBlank() } ?: current?.id ?: java.util.UUID.randomUUID().toString()) }
    var revision by rememberSaveable(patient.id) { mutableLongStateOf(initialCache?.optLong("revision") ?: current?.revision ?: 0L) }
    LaunchedEffect(draft, noteId, revision) {
        if(recovered.isSuccess) runCatching {
            localStore.saveDraft(patient.id,org.json.JSONObject().put("noteId",noteId).put("revision",revision).put("draft",encodeConnectedDoctorDraft(draft)).toString())
        }.onFailure { error=it.message }
        else error=t("The local test draft could not be read. It has not been replaced.", "लोकल टेस्ट ड्राफ़्ट पढ़ा नहीं जा सका। उसे बदला नहीं गया।")
    }
    val audio = rememberDoctorAudioController(patient.id, "connected-test-$accountId", DoctorAudioStorage.LOCAL_PREVIEW)
    val audioState by audio.state.collectAsState()
    val latestNavigationBlockedChange by rememberUpdatedState(onNavigationBlockedChange)
    LaunchedEffect(audioState.recording, busy) { latestNavigationBlockedChange(audioState.recording || busy) }
    DisposableEffect(Unit) { onDispose { latestNavigationBlockedChange(false) } }
    var wasRecording by remember { mutableStateOf(false) }
    LaunchedEffect(audioState.recording, audioState.clips.firstOrNull()?.id) {
        if(wasRecording && !audioState.recording) {
            audioState.clips.firstOrNull()?.let { clip ->
                draft = draft.copy(audioClipId = clip.id)
                saved = false
                checked = false
            }
        }
        wasRecording = audioState.recording
    }
    fun run(publish: Boolean) {
        if (busy) return
        val captured = draft
        busy = true; error = null
        scope.launch {
            var draftSaved = false
            try {
                val note = cloud.saveDoctorDraft(patient.id, captured, revision, noteId)
                noteId = note.id; revision = note.revision; draftSaved = true; saved = true
                if (publish) {
                    cloud.publishDoctorNote(note.id, note.revision)
                    noteId = java.util.UUID.randomUUID().toString(); revision = 0; draft = WorkflowDraft(patient.id)
                }
                onSaved()
            } catch(e:Exception) {
                if(e is CancellationException) throw e
                error = (if(draftSaved) t("Draft saved, but publication did not finish. ", "ड्राफ़्ट सहेजा गया, पर प्रकाशन पूरा नहीं हुआ। ") else t("Your text is unchanged. ", "आपका लिखा वैसा ही है। ")) + (e.message ?: t("Try again.", "फिर कोशिश करें।"))
                if(draftSaved) onSaved()
            } finally {busy=false}
        }
    }
    fun editSource(text: String) {
        if (!busy) {
            draft = draft.copy(sourceText = text, sourceIds = emptyList(), mockGenerated = false,
                transcriptProvider = "", transcriptModel = "", transcribedAt = 0,
                aiProvider = "", aiModel = "", evidence = emptyList(), aiWarnings = emptyList())
            checked = false
            saved = false
        }
    }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(t("Care conversation", "देखभाल पर बातचीत"), fontFamily = SaharaTitleFont, fontSize = 28.sp) }
        item { if (!busy) DoctorLocalPreviewCapture(audio, draft.sourceText, if(draft.sourceText.isBlank()) DoctorTranscriptKind.NONE else if(draft.mockGenerated)DoctorTranscriptKind.FICTIONAL_SAMPLE else DoctorTranscriptKind.MANUAL, ::editSource) else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Rust) }
        audioState.clips.firstOrNull()?.let { clip ->
            item {
                var uploadAgreed by rememberSaveable(clip.id) {mutableStateOf(false)}
                ConnectedCard(t("Upload recording", "रिकॉर्डिंग अपलोड करें")) {
                    CheckLine(uploadAgreed,{if(!busy)uploadAgreed=it},t("Everyone agreed to upload", "सबने अपलोड की अनुमति दी"))
                    Button(onClick={
                        if(busy)return@Button
                        busy=true;error=null;audio.stopPlayback()
                        scope.launch {
                            try {
                                require(audio.owns(clip) && !audio.state.value.recording){t("The recording changed. Choose it again.", "रिकॉर्डिंग बदल गई। उसे फिर चुनें।")}
                                cloud.uploadDoctorFile(context,Uri.fromFile(clip.file),patient.id,"${patient.name} · test conversation","audio",uploadAgreed)
                                uploadAgreed=false
                                draft=draft.copy(audioClipId=clip.id)
                                onSaved()
                            } catch(e:Exception) {if(e is CancellationException)throw e;error=e.message ?: t("Upload failed", "अपलोड नहीं हुआ")}
                            finally {busy=false}
                        }
                    },enabled=uploadAgreed && !busy && !audioState.recording,modifier=Modifier.fillMaxWidth().testTag("connected-upload-recording")) {Text(t("Upload", "अपलोड करें"))}
                }
            }
        }
        item { ConnectedCard(t("Original words", "मूल शब्द")) { CareField(draft.sourceText, ::editSource, t("Conversation text", "बातचीत की लिखाई")) } }
        item {
            ConnectedCard(t("Care note", "केयर नोट")) {
                val fields = listOf(t("What matters most", "सबसे ज़रूरी क्या है") to draft.priorities, t("Who participated", "कौन शामिल था") to draft.participants,
                    t("What was discussed", "क्या चर्चा हुई") to draft.topics, t("Questions and concerns", "सवाल और चिंताएँ") to draft.questions,
                    t("What remains undecided", "क्या तय नहीं हुआ") to draft.undecided, t("Agreed next steps", "तय अगले क़दम") to draft.nextSteps)
                fields.forEachIndexed { index, (label, value) ->
                    OutlinedTextField(value, { text ->
                        draft = when(index) {
                            0 -> draft.copy(priorities=text)
                            1 -> draft.copy(participants=text)
                            2 -> draft.copy(topics=text)
                            3 -> draft.copy(questions=text)
                            4 -> draft.copy(undecided=text)
                            else -> draft.copy(nextSteps=text)
                        }.copy(mockGenerated=false,aiProvider="",aiModel="",evidence=emptyList(),aiWarnings=emptyList())
                        checked=false
                        saved=false
                    },label={Text(label)},enabled=!busy,modifier=Modifier.fillMaxWidth())
                }
            }
        }
        item {
            ConnectedCard(t("Checklist", "सूची")) {
                draft.checklist.forEachIndexed { index, topic ->
                    Text(topic.title, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        WorkflowTopicStatus.entries.forEach { status ->
                            FilterChip(
                                selected = topic.status == status,
                                onClick = {
                                    draft = draft.copy(checklist = draft.checklist.mapIndexed { i, existing ->
                                        if (i == index) existing.copy(status = status) else existing
                                    })
                                    checked = false
                                    saved = false
                                },
                                enabled = !busy,
                                label = { Text(connectedTopicLabel(status, t), softWrap = false) }
                            )
                        }
                    }
                }
            }
        }
        error?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce())
                TextButton(onClick = {
                    noteId = java.util.UUID.randomUUID().toString()
                    revision = 0
                    saved = false
                    checked = false
                    error = null
                }, enabled = !busy, modifier = Modifier.testTag("connected-note-separate-copy")) {
                    Text(t("Save as copy", "कॉपी के रूप में सहेजें"))
                }
            }
        }
        if(saved) item { Text(t("Saved", "सहेजा गया"), color = Rust, modifier = Modifier.announce()) }
        item { Button(onClick = { run(false) }, enabled = !busy && !audioState.recording, modifier = Modifier.fillMaxWidth().testTag("connected-save-note")) { Text(t("Save draft", "ड्राफ़्ट सहेजें")) } }
        item { CheckLine(checked, { if(!busy) checked = it }, t("I checked this text", "मैंने यह लिखाई जाँची")) }
        item { OutlinedButton(onClick = { run(true) }, enabled = !busy && !audioState.recording && checked && draft.sourceText.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("connected-publish-note")) { Text(t("Publish", "प्रकाशित करें")) } }
        items(notes.filter { it.status != "draft" }.sortedByDescending { it.updatedAt }, key = { it.id }) { note -> ConnectedPublishedNote(note) }
    }
}

@Composable private fun ConnectedMedicines(cloud: CareCloud, patient: ConnectedDoctorPatient, doses: List<ConnectedDoctorDose>, onSaved: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) { val t = careText();
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var medicine by rememberSaveable(patient.id) { mutableStateOf("") }
    var planned by rememberSaveable(patient.id) { mutableStateOf(LocalDate.now().atTime(9,0).toString()) }
    var taken by rememberSaveable(patient.id) { mutableStateOf(java.time.LocalDateTime.now().withSecond(0).withNano(0).toString()) }
    var skipped by rememberSaveable(patient.id) { mutableStateOf(false) }
    var eventId by rememberSaveable(patient.id) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val latestNavigationCallback by rememberUpdatedState(onNavigationBlockedChange)
    LaunchedEffect(busy) { latestNavigationCallback(busy) }
    DisposableEffect(Unit) { onDispose { latestNavigationCallback(false) } }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(t("Medicines", "दवाइयाँ"), style = MaterialTheme.typography.headlineSmall) }
        items(doses.sortedByDescending { it.plannedAt }, key = { it.id }) { dose -> ConnectedCard(dose.medicineLabel) { Text(if(dose.outcome == "skipped") t("Skipped reported", "छोड़ने की सूचना") else t("Taken reported", "लेने की सूचना"), color = Rust); Text(t("Planned: ${connectedTime(dose.plannedAt)}", "तय: ${connectedTime(dose.plannedAt)}")); Text(t("Reported taken: ", "लेने का बताया समय: ") + "${dose.reportedTakenAt?.let(::connectedTime) ?: t("Not reported", "सूचना नहीं")}"); Text(t("Logged: ", "दर्ज: ") + connectedTime(dose.loggedAt)); Text(if(dose.reporterId == cloud.session.value?.userId) t("Reported by you", "आपने बताया") else t("Reported by others", "दूसरों ने बताया"), style = MaterialTheme.typography.bodySmall) } }
        item { ConnectedCard(t("Add dose", "ख़ुराक जोड़ें")) {
            OutlinedTextField(medicine,{medicine=it},label={Text(t("Medicine label", "दवा का नाम"))},enabled=!busy,modifier=Modifier.fillMaxWidth())
            ConnectedDateTime("Planned",planned,!busy){planned=it}
            CheckLine(skipped,{if(!busy)skipped=it},"Skipped")
            if(!skipped) ConnectedDateTime(t("Reported taken", "लेने का समय"),taken,!busy){taken=it}
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
            Button(onClick = {
                if(busy)return@Button
                val label=medicine;val plannedValue=planned;val takenValue=taken;val wasSkipped=skipped
                val capturedEventId=eventId
                busy=true;error=null
                scope.launch {
                    try {
                        val plannedAt=java.time.LocalDateTime.parse(plannedValue).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val takenAt=if(wasSkipped)null else java.time.LocalDateTime.parse(takenValue).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        cloud.addDoctorDose(patient.id,label,plannedAt,takenAt,if(wasSkipped)"skipped" else "taken",eventId=capturedEventId)
                        eventId=java.util.UUID.randomUUID().toString()
                        medicine="";onSaved()
                    } catch(e:Exception) {if(e is CancellationException)throw e;error=e.message ?: t("Couldn't save", "सहेजा नहीं जा सका")}
                    finally {busy=false}
                }
            },enabled=!busy && medicine.isNotBlank(),modifier=Modifier.testTag("connected-add-dose")) {Text(t("Save", "सहेजें"))}
        } }
    }
}

@Composable private fun ConnectedDateTime(label:String,value:String,enabled:Boolean,onChange:(String)->Unit) {
    val context=LocalContext.current
    val time=java.time.LocalDateTime.parse(value)
    Text(label,fontWeight=FontWeight.SemiBold)
    OutlinedButton(onClick={DatePickerDialog(context,{_,year,month,day-> val selected=java.time.LocalDateTime.of(year,month+1,day,time.hour,time.minute);TimePickerDialog(context,{_,hour,minute->onChange(selected.withHour(hour).withMinute(minute).toString())},time.hour,time.minute,false).show()},time.year,time.monthValue-1,time.dayOfMonth).show()},enabled=enabled,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(time.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")))}
}

@Composable private fun ConnectedPublishedNote(note:ConnectedDoctorNote) { val t = careText();
    var expanded by rememberSaveable(note.id) {mutableStateOf(false)}
    ConnectedCard(t("Published", "प्रकाशित")) {
        Text(connectedTime(note.updatedAt),style=MaterialTheme.typography.bodySmall)
        Text(note.draft.priorities.ifBlank {t("Priorities not recorded", "प्राथमिकताएँ दर्ज नहीं")});Text(note.draft.topics.ifBlank {t("Discussion not recorded", "चर्चा दर्ज नहीं")})
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded)t("Show less", "कम दिखाएँ") else t("Read full discussion", "पूरी चर्चा पढ़ें"))}
        if(expanded) {
            listOf(t("Original words", "मूल शब्द") to note.draft.sourceText,t("Participants", "भागीदार") to note.draft.participants,t("Questions", "सवाल") to note.draft.questions,t("Undecided", "तय नहीं") to note.draft.undecided,t("Next steps", "अगले क़दम") to note.draft.nextSteps).forEach {(label,value)->Text(label,fontWeight=FontWeight.SemiBold);Text(value.ifBlank {t("Not recorded", "दर्ज नहीं")},lineHeight=25.sp)}
            note.draft.checklist.forEach {Text("${it.title}: ${connectedTopicLabel(it.status, t)}")}
            if(note.draft.audioClipId.isNotBlank())Text(t("Associated local audio: ", "जुड़ा लोकल ऑडियो: ") + note.draft.audioClipId,style=MaterialTheme.typography.bodySmall)
            if(note.draft.transcriptModel.isNotBlank())Text(t("Transcript source: ", "लिखाई का स्रोत: ") + "${note.draft.transcriptProvider} / ${note.draft.transcriptModel}",style=MaterialTheme.typography.bodySmall)
            if(note.draft.mockGenerated)Text(t("Practice draft · not from your recording", "अभ्यास ड्राफ़्ट · आपकी रिकॉर्डिंग से नहीं"),color=Rust)
            note.draft.aiWarnings.forEach {Text(it,color=Rust)}
            note.draft.evidence.forEach {Text("${it.section}: ${it.quote}",style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable private fun ConnectedFiles(cloud: CareCloud, patient: ConnectedDoctorPatient, files: List<ConnectedDoctorFile>, onSaved: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) { val t = careText();
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var uriText by rememberSaveable(patient.id) { mutableStateOf<String?>(null) }
    val uri = uriText?.let(Uri::parse)
    var kind by rememberSaveable(patient.id) {mutableStateOf("report")};var title by rememberSaveable(patient.id) {mutableStateOf("")};var consent by rememberSaveable(patient.id) {mutableStateOf(false)};var busy by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    val latestNavigationCallback by rememberUpdatedState(onNavigationBlockedChange)
    LaunchedEffect(busy) { latestNavigationCallback(busy) }
    DisposableEffect(Unit) { onDispose { latestNavigationCallback(false) } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uriText=it?.toString();consent=false}
    fun run(action:suspend()->Unit) {if(!busy){busy=true;message=null;scope.launch {try{action()}catch(e:Exception){if(e is CancellationException)throw e;message=e.message ?: t("Something went wrong", "कुछ गड़बड़ हुई")}finally{busy=false}}}}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(t("Reports and recordings", "रिपोर्ट और रिकॉर्डिंग"),fontFamily = SaharaTitleFont,fontSize=27.sp) }
        items(files,key={it.id}) {file->ConnectedCard(file.title) {Text("${file.kind} · ${file.bytes/1024} KB");Text(connectedTime(file.createdAt),style=MaterialTheme.typography.bodySmall);if(file.ready) {OutlinedButton(onClick={run {val local=cloud.downloadDoctorFile(context,file);val shared=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",local);val intent=Intent(Intent.ACTION_VIEW).setDataAndType(shared,file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(Intent.createChooser(intent,t("Open file", "फ़ाइल खोलें")))}},enabled=!busy) {Text(t("Open", "खोलें"))}} else Text(t("Upload incomplete", "अपलोड अधूरा"),color=Rust) } }
        item {
            ConnectedCard(t("Add file", "फ़ाइल जोड़ें")) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("report","audio").forEach { value ->
                        FilterChip(kind==value,onClick={kind=value;uriText=null;consent=false},enabled=!busy,
                            label={Text(if(value=="audio")"Recording" else "Report")})
                    }
                }
                OutlinedTextField(title,{title=it},label={Text(t("File title", "फ़ाइल का नाम"))},enabled=!busy,modifier=Modifier.fillMaxWidth())
                OutlinedButton(onClick={picker.launch(if(kind=="audio")arrayOf("audio/*") else arrayOf("application/pdf","image/*"))},enabled=!busy) {
                    Text(if(uri==null)t("Choose file", "फ़ाइल चुनें") else t("Change file", "फ़ाइल बदलें"))
                }
                CheckLine(consent,{if(!busy)consent=it},t("I have permission to upload", "मुझे अपलोड की अनुमति है"))
                Button(onClick={
                    val selected=uri ?: return@Button
                    val selectedTitle=title
                    val selectedKind=kind
                    val agreed=consent
                    run {
                        cloud.uploadDoctorFile(context,selected,patient.id,selectedTitle,selectedKind,agreed)
                        uriText=null;title="";consent=false
                        message=t("Uploaded", "अपलोड हो गया")
                        onSaved()
                    }
                },enabled=!busy && consent && uri!=null && title.isNotBlank(),modifier=Modifier.testTag("connected-upload-file")) {
                    Text(t("Upload", "अपलोड करें"))
                }
                message?.let {Text(it)}
            }
        }
    }
}
