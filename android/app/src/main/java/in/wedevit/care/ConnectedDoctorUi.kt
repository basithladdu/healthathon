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

@Composable private fun ConnectedDoctorBody(cloud: CareCloud, vm: CareModel, email: String, userId: String, onExit: () -> Unit, onAccount: () -> Unit, onWalkthrough: (String) -> Unit) { val t = careText();
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
        else error = t("The saved local planner could not be read. It has not been replaced.", "सहेजा गया लोकल प्लानर पढ़ा नहीं जा सका। उसे बदला नहीं गया।")
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
    Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Column { Text(if (isolatedDemo) DoctorTestProfiles.profiles.firstOrNull {DoctorTestProfiles.personId(it)==testCarePerson}?.name ?: t("Earlier practice care", "पहले की प्रैक्टिस देखभाल") else realPatient?.name ?: patient?.name ?: if (selection != null) t("Patient record", "मरीज़ का रिकॉर्ड") else if (shared) t("Shared care", "साझा देखभाल") else t("Doctor workspace", "डॉक्टर कार्यक्षेत्र"), fontFamily = SaharaTitleFont, fontSize = 23.sp); Text(when { isolatedDemo -> t("Walkthrough · sample patient, not real", "वॉकथ्रू · नमूना मरीज़, असली नहीं"); realPatientId != null -> t("Real shared care", "असली साझा देखभाल"); patientId != null -> t("Test patient · separate records", "टेस्ट मरीज़ · अलग रिकॉर्ड"); shared -> t("Access and clinical review", "पहुँच और क्लिनिकल समीक्षा"); else -> email }, style = MaterialTheme.typography.bodySmall) } }, navigationIcon = { IconButton(onClick = ::back, enabled = !navigationBlocked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, bottomBar = {
        NavigationBar(containerColor = Paper) { listOf(t("Home", "होम") to Icons.Default.Home,t("Patients", "मरीज़") to Icons.Default.People,t("Schedule", "शेड्यूल") to Icons.Default.CalendarMonth,t("Account", "खाता") to Icons.Default.Person).forEachIndexed { index, (label, icon) -> NavigationBarItem(selected = if (selection != null || shared || isolatedDemo) index == 1 else tab == index, enabled = !navigationBlocked, onClick = { navigate { tab = index; selection = null; shared = false; isolatedDemo = false; nestedBack = null } }, icon = { Icon(icon, null) }, label = { Text(label) }) } }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Rust)
            error?.let { message -> Surface(color = Peach) { Row(Modifier.fillMaxWidth().padding(12.dp)) { Text(message, Modifier.weight(1f)); TextButton(onClick = ::refresh, enabled = !busy) { Text(t("Retry", "फिर कोशिश करें")) } } } }
            if (isolatedDemo) DoctorCareDemoHost(onExit = { isolatedDemo = false; nestedBack = null; navigationBlocked = false;testInitialSection="Hub" }, onNavigationBlockedChange = { navigationBlocked = it }, onHeaderBackChange = { nestedBack = it },initialPersonId=testCarePerson,initialSection=testInitialSection)
            else if (shared) EmbeddedSharedCare(vm, onAccount, { shared = false }, onNavigationBlockedChange = { navigationBlocked = it })
            else if (realPatientId != null) {
                if (!doctorSharedPatientReady(selection, userId, realPeople.map { it.id }.toSet(), selectedRealId, realDraft.personId)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (realRefreshing || (realPatient != null && realDraft.personId != realPatientId)) CircularProgressIndicator(color = WelcomePanelOrange)
                        Text(if (realPatient == null && !realRefreshing) t("This person's records are unavailable", "इस व्यक्ति के रिकॉर्ड उपलब्ध नहीं हैं") else t("Opening this person's care…", "इस व्यक्ति की देखभाल खुल रही है…"), style = MaterialTheme.typography.titleLarge)
                        Text(realRefreshError ?: if (realPatient == null) t("Access may have changed. No other patient's information is shown here.", "पहुँच बदल गई हो सकती है। यहाँ किसी और मरीज़ की जानकारी नहीं दिखाई जाती।") else t("Waiting for the correct profile and saved conversation.", "सही प्रोफ़ाइल और सहेजी बातचीत का इंतज़ार है।"))
                        OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Refresh access", "पहुँच ताज़ा करें")) }
                        TextButton(onClick = { navigate { selection = null } }) { Text(t("Back to patients", "मरीज़ों पर वापस")) }
                    }
                } else if (section == "Hub") {
                    DoctorSharedPatientHub(realPatient!!, onSection = { next -> navigate { section = next } })
                } else key(selection!!.stateKey) {
                    editorStates.SaveableStateProvider("${selection!!.stateKey}:care") {
                        EmbeddedPatientCare(vm, realPatientId, section, { next -> section = next }, onAccount, { selection = null }, onNavigationBlockedChange = { navigationBlocked = it }, onHeaderBackChange = { nestedBack = it })
                    }
                }
            } else if (patientId != null && patient == null) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(if (loaded) t("Test record unavailable", "टेस्ट रिकॉर्ड उपलब्ध नहीं") else t("Loading test records…", "टेस्ट रिकॉर्ड खुल रहे हैं…")); Text(t("No real patient's record has been substituted.", "किसी असली मरीज़ का रिकॉर्ड नहीं दिखाया गया है।")); TextButton(onClick = { selection = null }) { Text(t("Back to patients", "मरीज़ों पर वापस")) } }
            } else if (patient != null) {
                when (section) {
                    "Conversations" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:notes") { ConnectedNoteEditor(cloud, userId, patient, dataset!!.notes.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Reports" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:files") { ConnectedFiles(cloud, patient, dataset!!.files.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Medicines" -> editorStates.SaveableStateProvider("$userId:test:${patient.id}:medicines") { ConnectedMedicines(cloud, patient, dataset!!.doses.filter { it.patientId == patient.id }, onSaved = ::refresh, onNavigationBlockedChange = { navigationBlocked = it }) }
                    "Visits" -> ConnectedVisits(patient, localVisits, onSchedule = { schedulePatient = patient.id }, onUpdate = { changed -> localVisits = localVisits.map { if (it.id == changed.id) changed else it } })
                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item { Text(patient.careContext, style = MaterialTheme.typography.titleMedium); Text(t("Test data · not a real patient account", "टेस्ट डेटा · असली मरीज़ का खाता नहीं"), color = Rust, style = MaterialTheme.typography.bodySmall) }
                        if (section == "Hub") {
                            item { DoctorPatientHub(patient.card(), onSection = { section = it }) }
                            item { ConnectedCard(t("Preserved local walkthrough", "सहेजा हुआ लोकल डेमो")) { Text(t("Open this patient's original sample history and earlier screens. Walkthrough changes remain local, separate from this database test record.", "इस मरीज़ का मूल सैंपल इतिहास और पुरानी स्क्रीनें खोलें। डेमो के बदलाव इसी फ़ोन पर, इस टेस्ट रिकॉर्ड से अलग रहते हैं।")); OutlinedButton(onClick = { onWalkthrough(patient.legacyId) }) { Text(t("Open preserved walkthrough", "सहेजा डेमो खोलें")) } } }
                        }
                        else if (section == "Summary") {
                            item { ConnectedCard(t("Care overview", "देखभाल का सार")) { Text(patient.summary, lineHeight = 25.sp) } }
                            item { ConnectedCard(t("Previous doctor", "पिछले डॉक्टर")) { Text(patient.payload.optString("previousDoctor").ifBlank { t("Not recorded", "दर्ज नहीं") }) } }
                            item { ConnectedCard(t("Last conversation", "पिछली बातचीत")) { val note = dataset!!.notes.filter { it.patientId == patient.id }.maxByOrNull { it.updatedAt }; Text(note?.draft?.topics?.ifBlank { t("No discussion recorded", "कोई चर्चा दर्ज नहीं") } ?: t("No discussion recorded", "कोई चर्चा दर्ज नहीं")); TextButton(onClick = { section = "Conversations" }) { Text(t("Open conversations", "बातचीत खोलें")) } } }
                            item { ConnectedCard(t("Next review", "अगली समीक्षा")) { Text(t("Read reported updates and medicine history, then prepare questions for the next discussion.", "बताए गए अपडेट और दवाओं का इतिहास पढ़ें, फिर अगली चर्चा के सवाल तैयार करें।")); TextButton(onClick = { section = "Progress" }) { Text(t("View progress", "प्रगति देखें")) } } }
                        } else {
                            item { Text(t("Progress", "प्रगति"), style = MaterialTheme.typography.headlineSmall); Text(t("Reported test updates, not a clinical assessment.", "बताए गए टेस्ट अपडेट, क्लिनिकल आकलन नहीं।")) }
                            val checks = patient.payload.optJSONArray("checkIns")
                            if (checks == null || checks.length() == 0) item { Text(t("No reported updates saved.", "कोई अपडेट सहेजा नहीं गया।")) }
                            else items(checks.length()) { index -> val row = checks.getJSONObject(index); ConnectedCard(row.optString("source", t("Reported update", "बताया गया अपडेट"))) { Text(row.optString("text"), lineHeight = 25.sp); row.optLong("at").takeIf { it > 0 }?.let { Text(connectedTime(it), style = MaterialTheme.typography.bodySmall) } } }
                            item { TextButton(onClick = { section = "Medicines" }) { Text(t("Open medicine history", "दवाओं का इतिहास खोलें")) } }
                        }
                    }
                }
            } else when(tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().testTag("doctor-home"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(t("Your day in care", "आज की देखभाल"), fontFamily = SaharaTitleFont, fontSize = 30.sp); Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", if (hindi) java.util.Locale("hi", "IN") else java.util.Locale.ENGLISH))) }
                    if (earlierWorkspace) {
                        item { ConnectedCard(t("Earlier practice workspace", "पहले का प्रैक्टिस कार्यक्षेत्र")) { Text(t("Sample records · not real patients", "नमूना रिकॉर्ड · असली मरीज़ नहीं"), color = Rust); TextButton(onClick = { earlierWorkspace = false }) { Text(t("Return to current workspace", "अभी के कार्यक्षेत्र पर लौटें")) } } }
                        if (loaded && dataset == null) item { ConnectedCard(t("Earlier test dataset", "पहले का टेस्ट डेटा")) { Text(t("Existing account test records remain separate.", "खाते के मौजूदा टेस्ट रिकॉर्ड अलग रहते हैं।")); Text(t("Creating additional legacy test records is unavailable here.", "यहाँ और पुराने टेस्ट रिकॉर्ड नहीं बनाए जा सकते।")) } }
                        item { ConnectedCard(t("Earlier visits and discussion requests", "पहले की विज़िट और चर्चा के अनुरोध")) { Text(t("Local testing only; no requests are delivered from real accounts.", "सिर्फ़ लोकल टेस्ट; असली खातों से कोई अनुरोध नहीं आता।")); TextButton(onClick = { requestsOpen = true }) { Text(t("Open test discussion requests", "टेस्ट चर्चा अनुरोध खोलें")) }; TextButton(onClick = { tab = 2 }) { Text(t("Open earlier schedule", "पुराना शेड्यूल खोलें")) } } }
                    } else {
                        if (verification != DoctorConnectedHome.Verification.APPROVED) item { DoctorVerificationCard(verification, clinician?.note.orEmpty(), clinicianCheckedAt, clinicianChecking, clinicianError, hindi, ::checkClinician, ::openShared) }
                        item { OutlinedButton(onClick = { vm.refresh(); checkClinician() }, enabled = !realRefreshing, modifier = Modifier.heightIn(min = 48.dp).testTag("doctor-home-refresh")) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(if (realRefreshing) t("Refreshing…", "रिफ़्रेश हो रहा है…") else t("Refresh records", "रिकॉर्ड रिफ़्रेश करें")) } }
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
                            TextButton(onClick={earlierWorkspace=false}){Text(t("Return to current schedule", "अभी के शेड्यूल पर लौटें"))}
                            if (patients.isNotEmpty()) Box(Modifier.weight(1f)) { DemoSchedule(patients.map { it.card() }, localVisits, LocalDate.parse(day), { day = it.toString() }, ::openTest, { editingVisit = null; schedulePatient = patients.first().id }, { editingVisit = it; schedulePatient = it.patientId }, { visit -> localVisits = localVisits.map { if (it.id == visit.id) it.copy(status = "Cancelled") else it } }, { visit -> localVisits = localVisits.map { if (it.id == visit.id) it.copy(status = "Completed") else it } }) } else Text(t("No earlier planner records.", "पहले के प्लानर रिकॉर्ड नहीं हैं।"), Modifier.padding(20.dp))
                        } else LazyColumn(Modifier.weight(1f).testTag("doctor-schedule"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            doctorConnectedScheduleItems(realPeople, allItems, System.currentTimeMillis(), hindi, onVisit = { openReal(it); section = "Visits" })
                            item { DoctorWalkthroughCard(hindi) { p -> navigate { testCarePerson = DoctorTestProfiles.personId(p); testInitialSection = "Visits"; isolatedDemo = true } } }
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { DoctorVerificationCard(verification, clinician?.note.orEmpty(), clinicianCheckedAt, clinicianChecking, clinicianError, hindi, ::checkClinician, ::openShared) }
                    item { ConnectedCard(t("Older practice records · sample, not real", "पुराने अभ्यास रिकॉर्ड · नमूना, असली नहीं")) { TextButton(onClick={navigate {earlierWorkspace=true;tab=0}}){Text(t("Earlier practice workspace", "पहले का प्रैक्टिस कार्यक्षेत्र"))};TextButton(onClick={if(!navigationBlocked){testCarePerson=DoctorCareDemoData.PERSON_ID;testInitialSection="Hub";isolatedDemo=true}}){Text(t("Earlier care workspace", "पहले का देखभाल कार्यक्षेत्र"))};dataset?.patients.orEmpty().forEach {p->TextButton(onClick={openTest(p.id)}){Text(p.name)}} } }
                    item { ConnectedCard(t("Your account", "आपका खाता")) { Text(email, style = MaterialTheme.typography.titleLarge); Text("Language · भाषा", fontWeight = FontWeight.Medium); CareLanguageSwitch(); Text(t("Signed in. Login does not verify professional identity.", "साइन इन हो गया। लॉगिन से डॉक्टर होने की पुष्टि नहीं होती।")); Button(onClick = onAccount) { Text(t("Manage account / sign out", "खाता देखें / साइन आउट")) } } }
                    item { ConnectedCard(t("Verification and real patient access", "सत्यापन और असली मरीज़ों तक पहुँच")) { Text(t("Submit or change your registration details. Approval is given by hand by the Sahara team; each patient gives access with their own invitation.", "पंजीकरण का विवरण जमा करें या बदलें। मंज़ूरी Sahara टीम खुद देती है; हर मरीज़ अपने निमंत्रण से पहुँच देता है।")); OutlinedButton(onClick = ::openShared) { Text(t("Open verification and shared care", "सत्यापन और साझा देखभाल खोलें")) } } }
                    item { TextButton(onClick = onExit) { Text(t("Back to Sahara", "सहारा पर वापस")) } }
                }
            }
        }
    }
    schedulePatient?.let { selected -> val patients = dataset?.patients.orEmpty(); if (patients.isNotEmpty()) DemoAppointmentEditor(patients.map { it.card() }, selected, editingVisit, LocalDate.now(), onDismiss = { schedulePatient = null; editingVisit = null }, onSave = { visit -> localVisits = localVisits.filterNot { it.id == visit.id } + visit; schedulePatient = null; editingVisit = null; null }) }
    if(requestsOpen) ModalBottomSheet(onDismissRequest = {requestsOpen=false},containerColor=Cream) {
        LazyColumn(Modifier.fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item {Text(t("Test discussion requests", "टेस्ट चर्चा अनुरोध"),style=MaterialTheme.typography.headlineSmall);Text(t("Local simulations only. These requests did not arrive from real accounts.", "सिर्फ़ लोकल अभ्यास। ये अनुरोध असली खातों से नहीं आए।"))}
            items(dataset?.patients.orEmpty().take(4),key={it.id}) {p-> val status=org.json.JSONObject(requestStates).optString(p.id,"New");ConnectedCard(p.name) {Text(t("Prepare questions about ${p.careContext.lowercase()}.", "${p.careContext.lowercase()} के बारे में सवाल तैयार करें।"));Text(status,color=Rust);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Accepted","Deferred","Completed").forEach {next->TextButton(onClick={requestStates=org.json.JSONObject(requestStates).put(p.id,next).toString()}){Text(next)}}};TextButton(onClick={requestsOpen=false;openTest(p.id);section="Conversations"}){Text(t("Open discussion", "चर्चा खोलें"))}}}
            item {TextButton(onClick={requestsOpen=false}){Text(t("Close", "बंद करें"))}}
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

@Composable internal fun DoctorSharedPatientHub(person: Person, onSection: (String) -> Unit, deviceLocal: Boolean = false) { val t = careText();
    LazyColumn(Modifier.fillMaxSize().testTag("doctor-shared-patient-hub"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(t("${person.name}'s care", "${person.name} की देखभाल"), fontFamily = SaharaTitleFont, fontSize = 29.sp,lineHeight=36.sp); if(!DoctorTestProfiles.contains(person.id)) Text(if (deviceLocal) t("Device-local care · not synced or shared", "सिर्फ़ इस फ़ोन पर · सिंक या साझा नहीं") else t("Real shared records · access controlled by the patient", "असली साझा रिकॉर्ड · पहुँच मरीज़ के हाथ में"), style = MaterialTheme.typography.bodyMedium); if(!deviceLocal && !DoctorTestProfiles.contains(person.id)) Text(t("Patient ID ", "मरीज़ ID ") + DoctorConnectedHome.patientCode(person.id), style = MaterialTheme.typography.labelLarge, color = Rust, modifier = Modifier.testTag("doctor-shared-patient-id")) }
        item { DoctorPatientNavigationTiles(onSection) }
        item { OutlinedButton(onClick = { onSection("Tools") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("doctor-shared-tools")) { Icon(Icons.Default.Apps, null); Spacer(Modifier.width(10.dp)); Text(t("Daily care & tools", "रोज़ की देखभाल और टूल")) } }
        item { TextButton(onClick = { onSection("Shared") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Sharing, access & clinical review", "साझा करना, पहुँच और क्लिनिकल समीक्षा")) } }
    }
}

@Composable private fun ConnectedVisits(patient: ConnectedDoctorPatient, visits: List<DoctorDemoAppointment>, onSchedule: () -> Unit, onUpdate: (DoctorDemoAppointment) -> Unit) { val t = careText();
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(t("Visits", "विज़िट"), style = MaterialTheme.typography.headlineSmall); Text(t("Local test planner. No invitations or reminders are sent.", "लोकल टेस्ट प्लानर। कोई निमंत्रण या रिमाइंडर नहीं भेजा जाता।")); Button(onClick = onSchedule) { Text(t("Schedule visit", "विज़िट तय करें")) } }
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
        item { Text(t("Care conversation", "देखभाल पर बातचीत"), fontFamily = SaharaTitleFont, fontSize = 28.sp); Text(t("Test data · save to your private dataset. Publishing is not verified clinical review.", "टेस्ट डेटा · अपने निजी डेटा में सहेजें। प्रकाशित करना पुष्ट क्लिनिकल समीक्षा नहीं है।"), color = Rust) }
        item { if (!busy) DoctorLocalPreviewCapture(audio, draft.sourceText, if(draft.sourceText.isBlank()) DoctorTranscriptKind.NONE else if(draft.mockGenerated)DoctorTranscriptKind.FICTIONAL_SAMPLE else DoctorTranscriptKind.MANUAL, ::editSource) else Text(t("Saving the checked draft. Audio and text controls are temporarily paused.", "जाँचा ड्राफ़्ट सहेज रहे हैं। ऑडियो और लिखाई के बटन कुछ देर रुके हैं।")) }
        item { Text(t("Recording does not upload or generate an AI note. Save the draft to keep its associated recording ID in your account; the audio remains local unless you choose upload below.", "रिकॉर्डिंग न अपलोड होती है, न उससे AI नोट बनता है। रिकॉर्डिंग की ID खाते में रखने के लिए ड्राफ़्ट सहेजें; ऑडियो इसी फ़ोन पर रहता है, जब तक आप नीचे अपलोड न चुनें।"), style = MaterialTheme.typography.bodySmall) }
        audioState.clips.firstOrNull()?.let { clip ->
            item {
                var uploadAgreed by rememberSaveable(clip.id) {mutableStateOf(false)}
                ConnectedCard(t("Upload this test recording", "यह टेस्ट रिकॉर्डिंग अपलोड करें")) {
                    Text(t("Upload only this selected recording (${clip.file.length()/1024} KB) to private Supabase storage for ${patient.name}. This does not transcribe it.", "सिर्फ़ यह चुनी रिकॉर्डिंग (${clip.file.length()/1024} KB) ${patient.name} के निजी Supabase स्टोरेज में अपलोड करें। इससे लिखित रूप नहीं बनता।"))
                    CheckLine(uploadAgreed,{if(!busy)uploadAgreed=it},t("Every participant separately permits uploading this fictional test recording. It contains no real patient or confidential information.", "हर भागीदार अलग से इस काल्पनिक टेस्ट रिकॉर्डिंग को अपलोड करने की अनुमति देता है। इसमें किसी असली मरीज़ या गोपनीय जानकारी नहीं है।"))
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
                            } catch(e:Exception) {if(e is CancellationException)throw e;error=e.message ?: t("Recording was not uploaded. Your local audio is unchanged.", "रिकॉर्डिंग अपलोड नहीं हुई। आपका लोकल ऑडियो वैसा ही है।")}
                            finally {busy=false}
                        }
                    },enabled=uploadAgreed && !busy && !audioState.recording,modifier=Modifier.fillMaxWidth().testTag("connected-upload-recording")) {Text(if(busy)"Working…" else t("Upload this recording", "यह रिकॉर्डिंग अपलोड करें"))}
                }
            }
        }
        item { ConnectedCard(t("Original words", "मूल शब्द")) { CareField(draft.sourceText, ::editSource, t("Conversation text", "बातचीत की लिखाई")) } }
        item {
            ConnectedCard(t("Organise the Care Note", "केयर नोट व्यवस्थित करें")) {
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
            ConnectedCard(t("Discussion checklist", "चर्चा की सूची")) {
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
                    Text(t("Prepare a separate draft copy", "अलग ड्राफ़्ट कॉपी बनाएँ"))
                }
                Text(t("Keeps all your current text. Nothing is uploaded until you choose Save draft; existing account notes remain unchanged.", "आपका पूरा लिखा रहता है। ड्राफ़्ट सहेजें चुनने तक कुछ अपलोड नहीं होता; खाते के पुराने नोट नहीं बदलते।"), style = MaterialTheme.typography.bodySmall)
            }
        }
        if(saved) item { Text(t("Saved to your account's test dataset.", "आपके खाते के टेस्ट डेटा में सहेजा गया।"), color = Rust) }
        item { Button(onClick = { run(false) }, enabled = !busy && !audioState.recording, modifier = Modifier.fillMaxWidth().testTag("connected-save-note")) { Text(if(busy) "Saving…" else t("Save draft to account", "ड्राफ़्ट खाते में सहेजें")) } }
        item { CheckLine(checked, { if(!busy) checked = it }, t("I checked this exact text. This is a test note, not verified clinical documentation.", "मैंने यही लिखाई जाँची है। यह टेस्ट नोट है, पुष्ट क्लिनिकल दस्तावेज़ नहीं।")) }
        item { OutlinedButton(onClick = { run(true) }, enabled = !busy && !audioState.recording && checked && draft.sourceText.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("connected-publish-note")) { Text(t("Publish test version", "टेस्ट संस्करण प्रकाशित करें")) } }
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
        item { Text(t("Medicines", "दवाइयाँ"), style = MaterialTheme.typography.headlineSmall); Text(t("Reported test history, not a prescription or proof of ingestion.", "बताया गया टेस्ट इतिहास, पर्चा या दवा लेने का सबूत नहीं।")) }
        items(doses.sortedByDescending { it.plannedAt }, key = { it.id }) { dose -> ConnectedCard(dose.medicineLabel) { Text(if(dose.outcome == "skipped") t("Skipped reported", "छोड़ने की सूचना") else t("Taken reported", "लेने की सूचना"), color = Rust); Text(t("Planned: ${connectedTime(dose.plannedAt)}", "तय: ${connectedTime(dose.plannedAt)}")); Text(t("Reported taken: ", "लेने का बताया समय: ") + "${dose.reportedTakenAt?.let(::connectedTime) ?: t("Not reported", "सूचना नहीं")}"); Text(t("Logged: ", "दर्ज: ") + connectedTime(dose.loggedAt)); Text(if(dose.reporterId == cloud.session.value?.userId) t("Reported by you", "आपने बताया") else t("Reported by another authorised account", "किसी और अनुमति वाले खाते ने बताया"), style = MaterialTheme.typography.bodySmall) } }
        item { ConnectedCard(t("Add a test dose report", "टेस्ट ख़ुराक की सूचना जोड़ें")) {
            OutlinedTextField(medicine,{medicine=it},label={Text(t("Medicine label", "दवा का नाम"))},enabled=!busy,modifier=Modifier.fillMaxWidth())
            ConnectedDateTime("Planned",planned,!busy){planned=it}
            CheckLine(skipped,{if(!busy)skipped=it},"Skipped")
            if(!skipped) ConnectedDateTime(t("Reported taken", "लेने का समय"),taken,!busy){taken=it}
            Text(t("Logged time is recorded by the server when this report is saved.", "सूचना सहेजने पर दर्ज समय सर्वर लिखता है।"),style=MaterialTheme.typography.bodySmall)
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
                    } catch(e:Exception) {if(e is CancellationException)throw e;error=e.message ?: t("The report was not saved. Check the times and try again.", "सूचना सहेजी नहीं गई। समय जाँचकर फिर कोशिश करें।")}
                    finally {busy=false}
                }
            },enabled=!busy && medicine.isNotBlank(),modifier=Modifier.testTag("connected-add-dose")) {Text(if(busy)"Saving…" else t("Save reported dose", "ख़ुराक की सूचना सहेजें"))}
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
    ConnectedCard(t("Published test version", "प्रकाशित टेस्ट संस्करण")) {
        Text(connectedTime(note.updatedAt),style=MaterialTheme.typography.bodySmall)
        Text(note.draft.priorities.ifBlank {t("Priorities not recorded", "प्राथमिकताएँ दर्ज नहीं")});Text(note.draft.topics.ifBlank {t("Discussion not recorded", "चर्चा दर्ज नहीं")});Text(t("Unverified test record", "अपुष्ट टेस्ट रिकॉर्ड"),color=Rust)
        TextButton(onClick={expanded=!expanded}) {Text(if(expanded)t("Show less", "कम दिखाएँ") else t("Read full discussion", "पूरी चर्चा पढ़ें"))}
        if(expanded) {
            listOf(t("Original words", "मूल शब्द") to note.draft.sourceText,t("Participants", "भागीदार") to note.draft.participants,t("Questions", "सवाल") to note.draft.questions,t("Undecided", "तय नहीं") to note.draft.undecided,t("Next steps", "अगले क़दम") to note.draft.nextSteps).forEach {(label,value)->Text(label,fontWeight=FontWeight.SemiBold);Text(value.ifBlank {t("Not recorded", "दर्ज नहीं")},lineHeight=25.sp)}
            note.draft.checklist.forEach {Text("${it.title}: ${it.status.name.lowercase().replace('_',' ')}")}
            Text(t("Revision ${note.revision}", "संशोधन ${note.revision}"),style=MaterialTheme.typography.bodySmall)
            if(note.draft.audioClipId.isNotBlank())Text(t("Associated local audio: ", "जुड़ा लोकल ऑडियो: ") + note.draft.audioClipId,style=MaterialTheme.typography.bodySmall)
            if(note.draft.transcriptModel.isNotBlank())Text(t("Transcript source: ", "लिखाई का स्रोत: ") + "${note.draft.transcriptProvider} / ${note.draft.transcriptModel}",style=MaterialTheme.typography.bodySmall)
            if(note.draft.mockGenerated)Text(t("Sample draft, not generated from your recording", "सैंपल ड्राफ़्ट, आपकी रिकॉर्डिंग से नहीं बना"),color=Rust)
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
    fun run(action:suspend()->Unit) {if(!busy){busy=true;message=null;scope.launch {try{action()}catch(e:Exception){if(e is CancellationException)throw e;message=e.message ?: t("The file action did not finish.", "फ़ाइल का काम पूरा नहीं हुआ।")}finally{busy=false}}}}
    LazyColumn(Modifier.fillMaxSize().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Text(t("Reports and recordings", "रिपोर्ट और रिकॉर्डिंग"),fontFamily = SaharaTitleFont,fontSize=27.sp);Text(t("Test files only. Uploads go to private Supabase storage for this account's test patient.", "सिर्फ़ टेस्ट फ़ाइलें। अपलोड इस खाते के टेस्ट मरीज़ के निजी Supabase स्टोरेज में जाते हैं।")) }
        items(files,key={it.id}) {file->ConnectedCard(file.title) {Text("${file.kind} · ${file.bytes/1024} KB");Text(connectedTime(file.createdAt),style=MaterialTheme.typography.bodySmall);if(file.ready) {Text(t("Opening passes this test file to the viewer app you choose.", "खोलने पर यह टेस्ट फ़ाइल आपके चुने ऐप में जाती है।"),style=MaterialTheme.typography.bodySmall);OutlinedButton(onClick={run {val local=cloud.downloadDoctorFile(context,file);val shared=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",local);val intent=Intent(Intent.ACTION_VIEW).setDataAndType(shared,file.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);context.startActivity(Intent.createChooser(intent,t("Open private test file", "निजी टेस्ट फ़ाइल खोलें")))}},enabled=!busy) {Text(t("Download and open", "डाउनलोड करके खोलें"))}} else Text(t("Upload incomplete · choose the file again to retry", "अपलोड अधूरा · फिर से फ़ाइल चुनें"),color=Rust) } }
        item {
            ConnectedCard(t("Attach a test file", "टेस्ट फ़ाइल जोड़ें")) {
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("report","audio").forEach { value ->
                        FilterChip(kind==value,onClick={kind=value;uriText=null;consent=false},enabled=!busy,
                            label={Text(if(value=="audio")"Recording" else "Report")})
                    }
                }
                OutlinedTextField(title,{title=it},label={Text(t("File title", "फ़ाइल का नाम"))},enabled=!busy,modifier=Modifier.fillMaxWidth())
                OutlinedButton(onClick={picker.launch(if(kind=="audio")arrayOf("audio/*") else arrayOf("application/pdf","image/*"))},enabled=!busy) {
                    Text(if(uri==null)t("Choose file", "फ़ाइल चुनें") else t("Change selected file", "चुनी फ़ाइल बदलें"))
                }
                if(uri!=null)Text(t("A file is selected", "एक फ़ाइल चुनी गई है"),style=MaterialTheme.typography.bodySmall)
                CheckLine(consent,{if(!busy)consent=it},t("I have permission to upload this fictional test file to private Supabase storage. It contains no real patient or confidential information.", "मुझे यह काल्पनिक टेस्ट फ़ाइल निजी Supabase स्टोरेज में अपलोड करने की अनुमति है। इसमें किसी असली मरीज़ या गोपनीय जानकारी नहीं है।"))
                Button(onClick={
                    val selected=uri ?: return@Button
                    val selectedTitle=title
                    val selectedKind=kind
                    val agreed=consent
                    run {
                        cloud.uploadDoctorFile(context,selected,patient.id,selectedTitle,selectedKind,agreed)
                        uriText=null;title="";consent=false
                        message=t("File uploaded to your private test dataset.", "फ़ाइल आपके निजी टेस्ट डेटा में अपलोड हो गई।")
                        onSaved()
                    }
                },enabled=!busy && consent && uri!=null && title.isNotBlank(),modifier=Modifier.testTag("connected-upload-file")) {
                    Text(if(busy)"Uploading…" else t("Upload selected test file", "चुनी टेस्ट फ़ाइल अपलोड करें"))
                }
                message?.let {Text(it)}
            }
        }
    }
}
