@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@Composable private fun ConnectedDoctorBody(cloud: CareCloud, vm: CareModel, email: String, userId: String, onExit: () -> Unit, onAccount: () -> Unit, onWalkthrough: (String) -> Unit) {
    val context = LocalContext.current
    val planner = remember(userId) { ConnectedDoctorPlanner(context, userId) }
    val initialPlanner = remember(userId) { runCatching { planner.visits() to planner.requests() } }
    val scope = rememberCoroutineScope()
    val realPeople by vm.people.collectAsStateWithLifecycle()
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
        else error = "The saved local planner could not be read. It has not been replaced."
    }
    fun run(action: suspend () -> Unit) {
        if (!busy) { busy = true; error = null; scope.launch { try { action() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Could not load your records. Try again." } finally { busy = false } } }
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
            catch (e: Exception) { if (e is CancellationException) throw e; clinicianError = if (hindi) "स्थिति नहीं मिल पाई। इंटरनेट देखें और फिर से कोशिश करें।" else "Couldn't check your status. Check your internet and try again." }
            finally { clinicianChecking = false }
        }
    }
    LaunchedEffect(userId) { vm.refresh(); checkClinician() }
    LaunchedEffect(shared) { if (!shared && clinicianLoaded) checkClinician() }
    val verification = DoctorConnectedHome.verification(clinician, clinicianLoaded)
    var patientQuery by rememberSaveable(userId) { mutableStateOf("") }
    var joinCode by rememberSaveable(userId) { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var joinMessage by remember { mutableStateOf<String?>(null) }
    fun joinPatient() {
        val code = joinCode.trim()
        if (joining || code.isEmpty()) return
        joining = true; joinMessage = null
        scope.launch {
            try {
                val before = vm.people.value.map { it.id }.toSet()
                cloud.join(code)
                val added = vm.joinedRefresh(before)
                joinCode = ""
                joinMessage = added?.let { if (hindi) "${it.name} की देखभाल अब आपकी सूची में है।" else "${it.name}'s care is now in your list." }
                    ?: if (hindi) "जुड़ गए। अगर मरीज़ नहीं दिखता, तो रिफ़्रेश करें।" else "Joined. If the patient doesn't appear, tap Refresh."
            } catch (e: Exception) { if (e is CancellationException) throw e; joinMessage = careJoinFailureMessage(e.message); if (e.message?.startsWith("Clinician verification") == true) checkClinician() }
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
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Column { Text(if (isolatedDemo) DoctorTestProfiles.profiles.firstOrNull {DoctorTestProfiles.personId(it)==testCarePerson}?.name ?: "Earlier practice care" else realPatient?.name ?: patient?.name ?: if (selection != null) "Patient record" else if (shared) "Shared care" else "Doctor workspace", fontFamily = SaharaTitleFont, fontSize = 23.sp); Text(when { isolatedDemo -> if (hindi) "वॉकथ्रू · नमूना मरीज़, असली नहीं" else "Walkthrough · sample patient, not real"; realPatientId != null -> "Real shared care"; patientId != null -> "Test patient · separate records"; shared -> "Access and clinical review"; else -> email }, style = MaterialTheme.typography.bodySmall) } }, navigationIcon = { IconButton(onClick = ::back, enabled = !navigationBlocked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = {
        NavigationBar(containerColor = Paper) { listOf("Home" to Icons.Default.Home,"Patients" to Icons.Default.People,"Schedule" to Icons.Default.CalendarMonth,"Account" to Icons.Default.Person).forEachIndexed { index, (label, icon) -> NavigationBarItem(selected = if (selection != null || shared || isolatedDemo) index == 1 else tab == index, enabled = !navigationBlocked, onClick = { navigate { tab = index; selection = null; shared = false; isolatedDemo = false; nestedBack = null } }, icon = { Icon(icon, null) }, label = { Text(label) }) } }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Rust)
            error?.let { message -> Surface(color = Peach) { Row(Modifier.fillMaxWidth().padding(12.dp)) { Text(message, Modifier.weight(1f)); TextButton(onClick = ::refresh, enabled = !busy) { Text("Retry") } } } }
            if (isolatedDemo) DoctorCareDemoHost(onExit = { isolatedDemo = false; nestedBack = null; navigationBlocked = false;testInitialSection="Hub" }, onNavigationBlockedChange = { navigationBlocked = it }, onHeaderBackChange = { nestedBack = it },initialPersonId=testCarePerson,initialSection=testInitialSection)
            else if (shared) EmbeddedSharedCare(vm, onAccount, { shared = false }, onNavigationBlockedChange = { navigationBlocked = it })
            else if (realPatientId != null) {
                if (!doctorSharedPatientReady(selection, userId, realPeople.map { it.id }.toSet(), selectedRealId, realDraft.personId)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (realRefreshing || (realPatient != null && realDraft.personId != realPatientId)) CircularProgressIndicator(color = WelcomePanelOrange)
                        Text(if (realPatient == null && !realRefreshing) "This person's records are unavailable" else "Opening this person's care…", style = MaterialTheme.typography.titleLarge)
                        Text(realRefreshError ?: if (realPatient == null) "Access may have changed. No other patient's information is shown here." else "Waiting for the correct profile and saved conversation.")
                        OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Refresh access") }
                        TextButton(onClick = { navigate { selection = null } }) { Text("Back to patients") }
                    }
                } else if (section == "Hub") {
                    DoctorSharedPatientHub(realPatient!!, onSection = { next -> navigate { section = next } })
                } else key(selection!!.stateKey) {
                    editorStates.SaveableStateProvider("${selection!!.stateKey}:care") {
                        EmbeddedPatientCare(vm, realPatientId, section, { next -> section = next }, onAccount, { selection = null }, onNavigationBlockedChange = { navigationBlocked = it }, onHeaderBackChange = { nestedBack = it })
                    }
                }
            } else if (patientId != null && patient == null) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(if (loaded) "Test record unavailable" else "Loading test records…"); Text("No real patient's record has been substituted."); TextButton(onClick = { selection = null }) { Text("Back to patients") } }
            } else if (patient != null) {
                when (section) {
                    "Conversations" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:notes") { ConnectedNoteEditor(cloud, userId, patient, dataset!!.notes.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Reports" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:files") { ConnectedFiles(cloud, patient, dataset!!.files.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Medicines" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:medicines") { ConnectedMedicines(cloud, patient, dataset!!.doses.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Visits" -> ConnectedVisits(patient, localVisits, onSchedule = { schedulePatient = patient.id }, onUpdate = { changed -> localVisits = localVisits.map { if (it.id == changed.id) changed else it } })
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Text(patient.careContext, style = MaterialTheme.typography.titleMedium); Text("Test data · not a real patient account", color = Rust, style = MaterialTheme.typography.bodySmall) }
                        if (section == "Hub") {
                            item { DoctorPatientHub(patient.card(), onSection = { section = it }) }
                            item { ConnectedCard("Preserved local walkthrough") { Text("Open this patient's original sample history and earlier screens. Walkthrough changes remain local, separate from this database test record."); OutlinedButton(onClick = { onWalkthrough(patient.legacyId) }) { Text("Open preserved walkthrough") } } }
                        }
                        else if (section == "Summary") {
                            item { ConnectedCard("Care overview") { Text(patient.summary, lineHeight = 25.sp) } }
                            item { ConnectedCard("Previous doctor") { Text(patient.payload.optString("previousDoctor").ifBlank { "Not recorded" }) } }
                            item { ConnectedCard("Last conversation") { val note = dataset!!.notes.filter { it.patientId == patient.id }.maxByOrNull { it.updatedAt }; Text(note?.draft?.topics?.ifBlank { "No discussion recorded" } ?: "No discussion recorded"); TextButton(onClick = { section = "Conversations" }) { Text("Open conversations") } } }
                            item { ConnectedCard("Next review") { Text("Read reported updates and medicine history, then prepare questions for the next discussion."); TextButton(onClick = { section = "Progress" }) { Text("View progress") } } }
                        } else {
                            item { Text("Progress", style = MaterialTheme.typography.headlineSmall); Text("Reported test updates, not a clinical assessment.") }
                            val checks = patient.payload.optJSONArray("checkIns")
                            if (checks == null || checks.length() == 0) item { Text("No reported updates saved.") }
                            else items(checks.length()) { index -> val row = checks.getJSONObject(index); ConnectedCard(row.optString("source", "Reported update")) { Text(row.optString("text"), lineHeight = 25.sp); row.optLong("at").takeIf { it > 0 }?.let { Text(connectedTime(it), style = MaterialTheme.typography.bodySmall) } } }
                            item { TextButton(onClick = { section = "Medicines" }) { Text("Open medicine history") } }
                        }
                    }
                }
            } else when(tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().testTag("doctor-home"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(if (hindi) "आज की देखभाल" else "Your day in care", fontFamily = SaharaTitleFont, fontSize = 30.sp); Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", if (hindi) java.util.Locale("hi", "IN") else java.util.Locale.ENGLISH))) }
                    if (earlierWorkspace) {
                        item { ConnectedCard("Earlier practice workspace") { Text(if (hindi) "नमूना रिकॉर्ड · असली मरीज़ नहीं" else "Sample records · not real patients", color = Rust); TextButton(onClick = { earlierWorkspace = false }) { Text("Return to current workspace") } } }
                        if (loaded && dataset == null) item { ConnectedCard("Earlier test dataset") { Text("Existing account test records remain separate."); Text("Creating additional legacy test records is unavailable here.") } }
                        item { ConnectedCard("Earlier visits and discussion requests") { Text("Local testing only; no requests are delivered from real accounts."); TextButton(onClick = { requestsOpen = true }) { Text("Open test discussion requests") }; TextButton(onClick = { tab = 2 }) { Text("Open earlier schedule") } } }
                    } else {
                        if (verification != DoctorConnectedHome.Verification.APPROVED) item { DoctorVerificationCard(verification, clinician?.note.orEmpty(), clinicianCheckedAt, clinicianChecking, clinicianError, hindi, ::checkClinician, ::openShared) }
                        item { OutlinedButton(onClick = { vm.refresh(); checkClinician() }, enabled = !realRefreshing, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-home-refresh")) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (realRefreshing) (if (hindi) "रिफ़्रेश हो रहा है…" else "Refreshing…") else if (hindi) "रिकॉर्ड रिफ़्रेश करें" else "Refresh records") } }
                        realRefreshError?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        doctorConnectedHomeItems(realPeople, allItems, System.currentTimeMillis(), hindi, onVisit = { openReal(it); section = "Visits" }, onProgress = { openReal(it); section = "Progress" }, onPatients = { tab = 1 })
                        item { DoctorWalkthroughCard(hindi) { p -> navigate { testCarePerson = DoctorTestProfiles.personId(p); testInitialSection = "Hub"; isolatedDemo = true } } }
                    }
                }
                1 -> {
                    val shown = DoctorConnectedHome.search(realPeople, patientQuery)
                    val t = { en: String, hi: String -> if (hindi) hi else en }
                    LazyColumn(Modifier.fillMaxSize().testTag("doctor-patients-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item { Text(t("Patients", "मरीज़"), fontFamily = SaharaTitleFont, fontSize = 30.sp); Text(t("Only patients who have shared their care with you appear here.", "यहाँ सिर्फ़ वे मरीज़ दिखते हैं जिन्होंने अपनी देखभाल आपसे साझा की है।"), style = MaterialTheme.typography.bodyLarge) }
                        if (realPeople.isNotEmpty()) item { DoctorPatientSearchField(patientQuery, { patientQuery = it }, hindi) }
                        if (realPeople.isEmpty()) item { ConnectedCard(t("No patients yet", "अभी कोई मरीज़ नहीं")) { Text(t("Signing in alone does not give access. Ask the patient or family to create a doctor invitation in Sahara and share the code with you.", "सिर्फ़ साइन इन करने से पहुँच नहीं मिलती। मरीज़ या परिवार से कहें कि Sahara में डॉक्टर निमंत्रण बनाकर कोड आपको भेजें।")) } }
                        else if (shown.isEmpty()) item { ConnectedCard(t("No matching patient", "कोई मरीज़ नहीं मिला")) { Text(t("Check the name or patient ID. Patients who have not invited you can't be found.", "नाम या मरीज़ ID जाँचें। जिन मरीज़ों ने आपको निमंत्रण नहीं दिया, वे नहीं मिलेंगे।")); TextButton(onClick = { patientQuery = "" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Clear search", "खोज साफ़ करें")) } } }
                        items(shown, key = { "shared:${it.id}" }) { person -> DoctorPatientListCard(person.name, t("Patient ID ", "मरीज़ ID ") + DoctorConnectedHome.patientCode(person.id), { openReal(person.id) }) }
                        item { ConnectedCard(t("Add a patient", "मरीज़ जोड़ें")) {
                            Text(t("Enter the doctor invitation code the patient or family shared with you.", "मरीज़ या परिवार से मिला डॉक्टर निमंत्रण कोड लिखें।"))
                            if (verification != DoctorConnectedHome.Verification.APPROVED) Text(DoctorConnectedHome.verificationTitle(verification, hindi) + t(" · a doctor code works only after approval.", " · डॉक्टर कोड मंज़ूरी के बाद ही काम करता है।"), color = Rust, style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(joinCode, { joinCode = it.take(120); joinMessage = null }, Modifier.fillMaxWidth().testTag("doctor-join-code"), singleLine = true, label = { Text(t("Invitation code", "निमंत्रण कोड")) })
                            Button(onClick = ::joinPatient, enabled = !joining && joinCode.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-join")) { Text(if (joining) t("Joining…", "जुड़ रहे हैं…") else t("Join", "जुड़ें")) }
                            joinMessage?.let { Text(it, color = Rust) }
                        } }
                        item { TextButton(onClick = ::openShared, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Verification and clinical review", "सत्यापन और क्लिनिकल समीक्षा")) } }
                        item { DoctorWalkthroughCard(hindi) { profile -> if (!navigationBlocked && !vm.recording.value) { testCarePerson = DoctorTestProfiles.personId(profile); testInitialSection = "Hub"; isolatedDemo = true; tab = 1; nestedBack = null } } }
                    }
                }
                2 -> {
                    var day by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
                    val patients = dataset?.patients.orEmpty()
                    Column(Modifier.fillMaxSize()) {
                        if(earlierWorkspace) {
                            TextButton(onClick={earlierWorkspace=false}){Text("Return to current schedule")}
                            if (patients.isNotEmpty()) Box(Modifier.weight(1f)) { DemoSchedule(patients.map { it.card() }, localVisits, LocalDate.parse(day), { day = it.toString() }, ::openTest, { editingVisit = null; schedulePatient = patients.first().id }, { editingVisit = it; schedulePatient = it.patientId }, { visit -> localVisits = localVisits.map { if (it.id == visit.id) it.copy(status = "Cancelled") else it } }, { visit -> localVisits = localVisits.map { if (it.id == visit.id) it.copy(status = "Completed") else it } }) } else Text("No earlier planner records.", Modifier.padding(20.dp))
                        } else LazyColumn(Modifier.weight(1f).testTag("doctor-schedule"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            doctorConnectedScheduleItems(realPeople, allItems, System.currentTimeMillis(), hindi, onVisit = { openReal(it); section = "Visits" })
                            item { DoctorWalkthroughCard(hindi) { p -> navigate { testCarePerson = DoctorTestProfiles.personId(p); testInitialSection = "Visits"; isolatedDemo = true } } }
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { DoctorVerificationCard(verification, clinician?.note.orEmpty(), clinicianCheckedAt, clinicianChecking, clinicianError, hindi, ::checkClinician, ::openShared) }
                    item { ConnectedCard(if (hindi) "पुराने अभ्यास रिकॉर्ड · नमूना, असली नहीं" else "Older practice records · sample, not real") { TextButton(onClick={navigate {earlierWorkspace=true;tab=0}}){Text("Earlier practice workspace")};TextButton(onClick={if(!navigationBlocked){testCarePerson=DoctorCareDemoData.PERSON_ID;testInitialSection="Hub";isolatedDemo=true}}){Text("Earlier care workspace")};dataset?.patients.orEmpty().forEach {p->TextButton(onClick={openTest(p.id)}){Text(p.name)}} } }
                    item { ConnectedCard("Your account") { Text(email, style = MaterialTheme.typography.titleLarge); Text("Signed in. Login does not verify professional identity."); Button(onClick = onAccount) { Text("Manage account / sign out") } } }
                    item { ConnectedCard(if (hindi) "सत्यापन और असली मरीज़ों तक पहुँच" else "Verification and real patient access") { Text(if (hindi) "पंजीकरण का विवरण जमा करें या बदलें। मंज़ूरी Sahara टीम खुद देती है; हर मरीज़ अपने निमंत्रण से पहुँच देता है।" else "Submit or change your registration details. Approval is given by hand by the Sahara team; each patient gives access with their own invitation."); OutlinedButton(onClick = ::openShared) { Text(if (hindi) "सत्यापन और साझा देखभाल खोलें" else "Open verification and shared care") } } }
                    item { TextButton(onClick = onExit) { Text("Back to Sahara") } }
                }
            }
        }
    }
    schedulePatient?.let { selected -> val patients = dataset?.patients.orEmpty(); if (patients.isNotEmpty()) DemoAppointmentEditor(patients.map { it.card() }, selected, editingVisit, LocalDate.now(), onDismiss = { schedulePatient = null; editingVisit = null }, onSave = { visit -> localVisits = localVisits.filterNot { it.id == visit.id } + visit; schedulePatient = null; editingVisit = null; null }) }
    if(requestsOpen) ModalBottomSheet(onDismissRequest = {requestsOpen=false},containerColor=Cream) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {Text("Test discussion requests",style=MaterialTheme.typography.headlineSmall);Text("Local simulations only. These requests did not arrive from real accounts.")}
            items(dataset?.patients.orEmpty().take(4),key={it.id}) {p-> val status=org.json.JSONObject(requestStates).optString(p.id,"New");ConnectedCard(p.name) {Text("Prepare questions about ${p.careContext.lowercase()}.");Text(status,color=Rust);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Accepted","Deferred","Completed").forEach {next->TextButton(onClick={requestStates=org.json.JSONObject(requestStates).put(p.id,next).toString()}){Text(next)}}};TextButton(onClick={requestsOpen=false;openTest(p.id);section="Conversations"}){Text("Open discussion")}}}
            item {TextButton(onClick={requestsOpen=false}){Text("Close")}}
        }
    }
}

@Composable private fun ConnectedCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); content() } }
}

@Composable internal fun DoctorPatientListCard(name: String, description: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp)) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Peach, shape = RoundedCornerShape(16.dp)) { Text(name.take(1).uppercase(), Modifier.padding(15.dp), color = WelcomePanelInk, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodyMedium) }
            Icon(Icons.Default.ChevronRight, null, tint = Rust)
        }
    }
}

@Composable internal fun DoctorSharedPatientHub(person: Person, onSection: (String) -> Unit, deviceLocal: Boolean = false) {
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-shared-patient-hub"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("${person.name}'s care", fontFamily = SaharaTitleFont, fontSize = 29.sp,lineHeight=36.sp); if(!DoctorTestProfiles.contains(person.id)) Text(if (deviceLocal) "Device-local care · not synced or shared" else "Real shared records · access controlled by the patient", style = MaterialTheme.typography.bodyMedium); if(!deviceLocal && !DoctorTestProfiles.contains(person.id)) Text((if (doctorUiHindi()) "मरीज़ ID " else "Patient ID ") + DoctorConnectedHome.patientCode(person.id), style = MaterialTheme.typography.labelLarge, color = Rust, modifier = Modifier.testTag("doctor-shared-patient-id")) }
        item { DoctorPatientNavigationTiles(onSection) }
        item { OutlinedButton(onClick = { onSection("Tools") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("doctor-shared-tools")) { Icon(Icons.Default.Apps, null); Spacer(Modifier.width(10.dp)); Text("Daily care & tools") } }
        item { TextButton(onClick = { onSection("Shared") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Sharing, access & clinical review") } }
    }
}

@Composable private fun ConnectedVisits(patient: ConnectedDoctorPatient, visits: List<DoctorDemoAppointment>, onSchedule: () -> Unit, onUpdate: (DoctorDemoAppointment) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Visits", style = MaterialTheme.typography.headlineSmall); Text("Local test planner. No invitations or reminders are sent."); Button(onClick = onSchedule) { Text("Schedule visit") } }
        items(visits.filter { it.patientId == patient.id }, key = { it.id }) { visit -> ConnectedCard(connectedTime(visit.startMillis)) { Text("${visit.type} · ${visit.durationMinutes} minutes"); Text(visit.status); Text(visit.note); TextButton(onClick = { onUpdate(visit.copy(status = "Completed")) }) { Text("Mark complete") }; TextButton(onClick = { onUpdate(visit.copy(status = "Cancelled")) }) { Text("Cancel visit") } } }
    }
}

@Composable private fun ConnectedNoteEditor(cloud: CareCloud, accountId: String, patient: ConnectedDoctorPatient, notes: List<ConnectedDoctorNote>, onSaved: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) {
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
        else error="The local test draft could not be read. It has not been replaced."
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
                error = (if(draftSaved) "Draft saved, but publication did not finish. " else "Your text is unchanged. ") + (e.message ?: "Try again.")
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
        item { Text("Care conversation", fontFamily = SaharaTitleFont, fontSize = 28.sp); Text("Test data · save to your private dataset. Publishing is not verified clinical review.", color = Rust) }
        item { if (!busy) DoctorLocalPreviewCapture(audio, draft.sourceText, if(draft.sourceText.isBlank()) DoctorTranscriptKind.NONE else if(draft.mockGenerated)DoctorTranscriptKind.FICTIONAL_SAMPLE else DoctorTranscriptKind.MANUAL, ::editSource) else Text("Saving the checked draft. Audio and text controls are temporarily paused.") }
        item { Text("Recording does not upload or generate an AI note. Save the draft to keep its associated recording ID in your account; the audio remains local unless you choose upload below.", style = MaterialTheme.typography.bodySmall) }
        audioState.clips.firstOrNull()?.let { clip ->
            item {
                var uploadAgreed by rememberSaveable(clip.id) {mutableStateOf(false)}
                ConnectedCard("Upload this test recording") {
                    Text("Upload only this selected recording (${clip.file.length()/1024} KB) to private Supabase storage for ${patient.name}. This does not transcribe it.")
                    CheckLine(uploadAgreed,{if(!busy)uploadAgreed=it},"Every participant separately permits uploading this fictional test recording. It contains no real patient or confidential information.")
                    Button(onClick={
                        if(busy)return@Button
                        busy=true;error=null;audio.stopPlayback()
                        scope.launch {
                            try {
                                require(audio.owns(clip) && !audio.state.value.recording){"The recording changed. Choose it again."}
                                cloud.uploadDoctorFile(context,Uri.fromFile(clip.file),patient.id,"${patient.name} · test conversation","audio",uploadAgreed)
                                uploadAgreed=false
                                draft=draft.copy(audioClipId=clip.id)
                                onSaved()
                            } catch(e:Exception) {if(e is CancellationException)throw e;error=e.message ?: "Recording was not uploaded. Your local audio is unchanged."}
                            finally {busy=false}
                        }
                    },enabled=uploadAgreed && !busy && !audioState.recording,modifier=Modifier.fillMaxWidth().testTag("connected-upload-recording")) {Text(if(busy)"Working…" else "Upload this recording")}
                }
            }
        }
        item { ConnectedCard("Original words") { CareField(draft.sourceText, ::editSource, "Conversation text") } }
        item {
            ConnectedCard("Organise the Care Note") {
                val fields = listOf("What matters most" to draft.priorities, "Who participated" to draft.participants,
                    "What was discussed" to draft.topics, "Questions and concerns" to draft.questions,
                    "What remains undecided" to draft.undecided, "Agreed next steps" to draft.nextSteps)
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
            ConnectedCard("Discussion checklist") {
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
                                label = { Text(status.name.lowercase().replace('_', ' ')) }
                            )
                        }
                    }
                }
            }
        }
        error?.let { message ->
            item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = {
                    noteId = java.util.UUID.randomUUID().toString()
                    revision = 0
                    saved = false
                    checked = false
                    error = null
                }, enabled = !busy, modifier = Modifier.testTag("connected-note-separate-copy")) {
                    Text("Prepare a separate draft copy")
                }
                Text("Keeps all your current text. Nothing is uploaded until you choose Save draft; existing account notes remain unchanged.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if(saved) item { Text("Saved to your account's test dataset.", color = Rust) }
        item { Button(onClick = { run(false) }, enabled = !busy && !audioState.recording, modifier = Modifier.fillMaxWidth().testTag("connected-save-note")) { Text(if(busy) "Saving…" else "Save draft to account") } }
        item { CheckLine(checked, { if(!busy) checked = it }, "I checked this exact text. This is a test note, not verified clinical documentation.") }
        item { OutlinedButton(onClick = { run(true) }, enabled = !busy && !audioState.recording && checked && draft.sourceText.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("connected-publish-note")) { Text("Publish test version") } }
        items(notes.filter { it.status != "draft" }.sortedByDescending { it.updatedAt }, key = { it.id }) { note -> ConnectedPublishedNote(note) }
    }
}

@Composable private fun ConnectedMedicines(cloud: CareCloud, patient: ConnectedDoctorPatient, doses: List<ConnectedDoctorDose>, onSaved: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) {
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
        item { Text("Medicines", style = MaterialTheme.typography.headlineSmall); Text("Reported test history, not a prescription or proof of ingestion.") }
        items(doses.sortedByDescending { it.plannedAt }, key = { it.id }) { dose -> ConnectedCard(dose.medicineLabel) { Text(if(dose.outcome == "skipped") "Skipped reported" else "Taken reported", color = Rust); Text("Planned: ${connectedTime(dose.plannedAt)}"); Text("Reported taken: ${dose.reportedTakenAt?.let(::connectedTime) ?: "Not reported"}"); Text("Logged: ${connectedTime(dose.loggedAt)}"); Text(if(dose.reporterId == cloud.session.value?.userId) "Reported by you" else "Reported by another authorised account", style = MaterialTheme.typography.bodySmall) } }
        item { ConnectedCard("Add a test dose report") {
            OutlinedTextField(medicine,{medicine=it},label={Text("Medicine label")},enabled=!busy,modifier=Modifier.fillMaxWidth())
            ConnectedDateTime("Planned",planned,!busy){planned=it}
            CheckLine(skipped,{if(!busy)skipped=it},"Skipped")
            if(!skipped) ConnectedDateTime("Reported taken",taken,!busy){taken=it}
            Text("Logged time is recorded by the server when this report is saved.",style=MaterialTheme.typography.bodySmall)
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
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
                    } catch(e:Exception) {if(e is CancellationException)throw e;error=e.message ?: "The report was not saved. Check the times and try again."}
                    finally {busy=false}
                }
            },enabled=!busy && medicine.isNotBlank(),modifier=Modifier.testTag("connected-add-dose")) {Text(if(busy)"Saving…" else "Save reported dose")}
        } }
    }
}

@Composable private fun ConnectedDateTime(label:String,value:String,enabled:Boolean,onChange:(String)->Unit) {
    val context=LocalContext.current
    val time=java.time.LocalDateTime.parse(value)
    Text(label,fontWeight=FontWeight.SemiBold)
    OutlinedButton(onClick={DatePickerDialog(context,{_,year,month,day-> val selected=java.time.LocalDateTime.of(year,month+1,day,time.hour,time.minute);TimePickerDialog(context,{_,hour,minute->onChange(selected.withHour(hour).withMinute(minute).toString())},time.hour,time.minute,false).show()},time.year,time.monthValue-1,time.dayOfMonth).show()},enabled=enabled,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(time.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")))}
}

@Composable private fun ConnectedPublishedNote(note:ConnectedDoctorNote) {
    var expanded by rememberSaveable(note.id) {mutableStateOf(false)}
    ConnectedCard("Published test version") {
        Text(connectedTime(note.updatedAt),style=MaterialTheme.typography.bodySmall)
        Text(note.draft.priorities.ifBlank {"Priorities not recorded"});Text(note.draft.topics.ifBlank {"Discussion not recorded"});Text("Unverified test record",color=Rust)
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded)"Show less" else "Read full discussion")}
        if(expanded) {
            listOf("Original words" to note.draft.sourceText,"Participants" to note.draft.participants,"Questions" to note.draft.questions,"Undecided" to note.draft.undecided,"Next steps" to note.draft.nextSteps).forEach {(label,value)->Text(label,fontWeight=FontWeight.SemiBold);Text(value.ifBlank {"Not recorded"},lineHeight=25.sp)}
            note.draft.checklist.forEach {Text("${it.title}: ${it.status.name.lowercase().replace('_',' ')}")}
            Text("Revision ${note.revision}",style=MaterialTheme.typography.bodySmall)
            if(note.draft.audioClipId.isNotBlank())Text("Associated local audio: ${note.draft.audioClipId}",style=MaterialTheme.typography.bodySmall)
            if(note.draft.transcriptModel.isNotBlank())Text("Transcript source: ${note.draft.transcriptProvider} / ${note.draft.transcriptModel}",style=MaterialTheme.typography.bodySmall)
            if(note.draft.mockGenerated)Text("Sample draft, not generated from your recording",color=Rust)
            note.draft.aiWarnings.forEach {Text(it,color=Rust)}
            note.draft.evidence.forEach {Text("${it.section}: ${it.quote}",style=MaterialTheme.typography.bodySmall)}
        }
    }
}

@Composable private fun ConnectedFiles(cloud: CareCloud, patient: ConnectedDoctorPatient, files: List<ConnectedDoctorFile>, onSaved: () -> Unit, onNavigationBlockedChange: (Boolean) -> Unit = {}) {
    val context=LocalContext.current;val scope=rememberCoroutineScope()
    var uriText by rememberSaveable(patient.id) { mutableStateOf<String?>(null) }
    val uri = uriText?.let(Uri::parse)
    var kind by rememberSaveable(patient.id) {mutableStateOf("report")};var title by rememberSaveable(patient.id) {mutableStateOf("")};var consent by rememberSaveable(patient.id) {mutableStateOf(false)};var busy by remember {mutableStateOf(false)};var message by remember {mutableStateOf<String?>(null)}
    val latestNavigationCallback by rememberUpdatedState(onNavigationBlockedChange)
    LaunchedEffect(busy) { latestNavigationCallback(busy) }
    DisposableEffect(Unit) { onDispose { latestNavigationCallback(false) } }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uriText=it?.toString();consent=false}
    fun run(action:suspend()->Unit) {if(!busy){busy=true;message=null;scope.launch {try{action()}catch(e:Exception){if(e is CancellationException)throw e;message=e.message ?: "The file action did not finish."}finally{busy=false}}}}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text("Reports and recordings",fontFamily = SaharaTitleFont,fontSize=27.sp);Text("Test files only. Uploads go to private Supabase storage for this account's test patient.") }
        items(files,key={it.id}) {file->ConnectedCard(file.title) {Text("${file.kind} · ${file.bytes/1024} KB");Text(connectedTime(file.createdAt),style=MaterialTheme.typography.bodySmall);if(file.ready) {Text("Opening passes this test file to the viewer app you choose.",style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={run {val local=cloud.downloadDoctorFile(context,file);val shared=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",local);val intent=Intent(Intent.ACTION_VIEW).setDataAndType(shared,file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(Intent.createChooser(intent,"Open private test file"))}},enabled=!busy) {Text("Download and open")}} else Text("Upload incomplete · choose the file again to retry",color=Rust) } }
        item {
            ConnectedCard("Attach a test file") {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("report","audio").forEach { value ->
                        FilterChip(kind==value,onClick={kind=value;uriText=null;consent=false},enabled=!busy,
                            label={Text(if(value=="audio")"Recording" else "Report")})
                    }
                }
                OutlinedTextField(title,{title=it},label={Text("File title")},enabled=!busy,modifier=Modifier.fillMaxWidth())
                OutlinedButton(onClick={picker.launch(if(kind=="audio")arrayOf("audio/*") else arrayOf("application/pdf","image/*"))},enabled=!busy) {
                    Text(if(uri==null)"Choose file" else "Change selected file")
                }
                if(uri!=null)Text("A file is selected",style=MaterialTheme.typography.bodySmall)
                CheckLine(consent,{if(!busy)consent=it},"I have permission to upload this fictional test file to private Supabase storage. It contains no real patient or confidential information.")
                Button(onClick={
                    val selected=uri ?: return@Button
                    val selectedTitle=title
                    val selectedKind=kind
                    val agreed=consent
                    run {
                        cloud.uploadDoctorFile(context,selected,patient.id,selectedTitle,selectedKind,agreed)
                        uriText=null;title="";consent=false
                        message="File uploaded to your private test dataset."
                        onSaved()
                    }
                },enabled=!busy && consent && uri!=null && title.isNotBlank(),modifier=Modifier.testTag("connected-upload-file")) {
                    Text(if(busy)"Uploading…" else "Upload selected test file")
                }
                message?.let {Text(it)}
            }
        }
    }
}
