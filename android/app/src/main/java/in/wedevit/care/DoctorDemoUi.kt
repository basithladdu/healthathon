@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.UUID

fun doctorWorkflowNamespace(userId:String?):String {
    if(userId==null) return "doctor-workflow-v1"
    require(runCatching { UUID.fromString(userId) }.isSuccess) { "Invalid account identifier." }
    return "doctor-workflow-$userId"
}

private val demoZone: ZoneId get() = ZoneId.systemDefault()
private fun demoDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(demoZone).toLocalDate()
private fun demoTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(demoZone).format(DateTimeFormatter.ofPattern("h:mm a"))
private fun demoDay(day: LocalDate) = day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
private fun DoctorDemoPatient.displayName() = name.removeSuffix(" Demo")
// Keep the doctor's workspace within Sahara's original orange and cream palette.
private val DoctorForest = Rust
private val DoctorTeal = Rust
private val DoctorBackground = Cream
private val DoctorInk = Ink
private val DoctorMuted = DoctorCardSub
private val DoctorMist = Peach

@Composable private fun DoctorTitle(text: String, size: Int = 28) { Text(text, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = size.sp, lineHeight = (size + 6).sp, fontWeight = FontWeight.Medium, color = DoctorInk) }
@Composable private fun DoctorSection(text: String, modifier: Modifier = Modifier) { Text(text, modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorInk) }
@Composable private fun DoctorAvatar(name: String, size: Int = 46, tint:Color=DoctorMist) {
    Box(Modifier.size(size.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
        Text(name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), color = DoctorForest, fontWeight = FontWeight.SemiBold, fontSize = (size / 3).sp)
    }
}
@Composable private fun VisitStatus(status: String) {
    val (color, fill) = when (status) { "Completed" -> DoctorStatusGood to DoctorStatusGoodBg; "Cancelled" -> DoctorStatusRest to DoctorStatusRestBg; else -> DoctorStatusWarn to DoctorStatusWarnBg }
    Surface(color = fill, shape = RoundedCornerShape(50)) { Text(careTr(careHindi(), status, when (status) { "Scheduled" -> "तय"; "Completed" -> "पूरी हुई"; "Cancelled" -> "रद्द"; else -> status }), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium) }
}

/** A separate walkthrough workspace: no CareModel, account token, Room or network operations. */
@Composable fun DoctorDemo(onExit: () -> Unit, store: DoctorDemoStore? = null, workflowStore: DoctorWorkflowStore? = null, cloud:CareCloud? = null, enableOnlineAi:Boolean=false, initialPatientId:String?=null, careModel: CareModel? = null, onCareAccount: () -> Unit = {}) {
    val scheme = lightColorScheme(primary = DoctorForest, onPrimary = Color.White, primaryContainer = DoctorMist, onPrimaryContainer = DoctorForest,
        secondary = DoctorTeal, onSecondary = Color.White, secondaryContainer = DoctorMist, onSecondaryContainer = DoctorForest,
        background = DoctorBackground, onBackground = DoctorInk, surface = Paper, onSurface = DoctorInk,
        surfaceVariant = Color(0xFFF1E9DE), onSurfaceVariant = DoctorMuted, outline = Color(0xFF8D7B70), outlineVariant = Color(0xFFD7C8BC))
    var authOpen by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = scheme, typography = saharaTitleTypography()) {
        DoctorWorkspace(onExit, store, workflowStore, if(enableOnlineAi) cloud else null, enableOnlineAi, onSignIn={if(enableOnlineAi) authOpen=true}, initialPatientId=initialPatientId, careModel = careModel, onCareAccount = onCareAccount)
        if(enableOnlineAi && authOpen && cloud!=null) DoctorAiAccountSheet(cloud,onDismiss={authOpen=false})
    }
}

@Composable private fun DoctorWorkspace(onExit: () -> Unit, store: DoctorDemoStore?, workflowStore: DoctorWorkflowStore?, cloud:CareCloud?,enableOnlineAi:Boolean,onSignIn:()->Unit,initialPatientId:String?=null, careModel: CareModel? = null, onCareAccount: () -> Unit = {}) { val t = careText();
    val context = LocalContext.current
    val focus=LocalFocusManager.current
    DisposableEffect(context) {
        var owner = context
        while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
        val window = (owner as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    val demoStore = store ?: remember { DoctorDemoStore(context.applicationContext) }
    val onlineSession=cloud?.session?.collectAsState()
    val workflowAccountId=onlineSession?.value?.userId
    val workflowNamespace=runCatching { doctorWorkflowNamespace(workflowAccountId) }.getOrDefault("doctor-workflow-invalid-session")
    val workflow = workflowStore ?: remember(workflowNamespace) { DoctorWorkflowStore(context.applicationContext,preferencesName=workflowNamespace) }
    var loggedIn by rememberSaveable { mutableStateOf(initialPatientId != null && demoStore.patients.any { it.id == initialPatientId }) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var feedbackOpen by rememberSaveable(workflowNamespace) { mutableStateOf(false) }
    var patientId by rememberSaveable { mutableStateOf(initialPatientId?.takeIf { requested -> demoStore.patients.any { it.id == requested } }) }
    val careAccount = careModel?.accountNamespace.orEmpty()
    val carePeople = careModel?.people?.collectAsState()?.value.orEmpty()
    var carePersonId by rememberSaveable(careAccount) { mutableStateOf<String?>(null) }
    var addingCarePerson by rememberSaveable(careAccount) { mutableStateOf(false) }
    var pendingCreatedProfileId by rememberSaveable(careAccount) { mutableStateOf<String?>(null) }
    val careBusy = careModel?.busy?.collectAsState()?.value == true || careModel?.refreshing?.collectAsState()?.value == true
    val careError = careModel?.actionError?.collectAsState()?.value
    val careDraftId = careModel?.draft?.collectAsState()?.value?.personId
    val careSelection = carePersonId?.let { DoctorPatientSelection(careAccount, if (careModel?.cloud == null) DoctorPatientSource.DEVICE else DoctorPatientSource.SHARED, it) }
    var careSection by rememberSaveable(careAccount, carePersonId) { mutableStateOf("Hub") }
    var careNavigationBlocked by remember { mutableStateOf(false) }
    var careNestedBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    var sampleCareOpen by rememberSaveable {mutableStateOf(false)}
    var testCarePerson by rememberSaveable {mutableStateOf(DoctorCareDemoData.PERSON_ID)}
    var testInitialSection by rememberSaveable {mutableStateOf("Hub")}
    var earlierWorkspace by rememberSaveable {mutableStateOf(false)}
    var homePending by remember {mutableStateOf<List<HomeCarePending>>(emptyList())}
    var homeVisits by remember {mutableStateOf<List<HomeVisit>>(emptyList())}
    var homeLoaded by remember {mutableStateOf(false)} // until the first device read finishes, the visit and draft tiles show "—" instead of a false 0
    LaunchedEffect(sampleCareOpen) {if(!sampleCareOpen) {homePending=loadHomeCarePending(context); homeVisits=loadHomeVisits(context); homeLoaded=true}}
    var sampleCareBack by remember {mutableStateOf<(() -> Unit)?>(null)}
    val embeddedCareStates = rememberSaveableStateHolder()
    val carePerson = carePeople.firstOrNull { it.id == carePersonId }
    fun navigateCare(action: () -> Unit) { if (!careNavigationBlocked) { if (careModel?.recording?.value == true) careModel.stopRecording(); action() } }
    var recordTab by rememberSaveable { mutableStateOf("Hub") }
    var progressReportId by rememberSaveable(workflowNamespace,patientId) {mutableStateOf<String?>(null)}
    val progressReports=remember(workflowNamespace,patientId) {DoctorProgressData.reports(patientId ?: "",System.currentTimeMillis())}
    val progressListState=key(workflowNamespace,patientId) {rememberLazyListState()}
    LaunchedEffect(progressReportId,progressReports) {if(progressReportId!=null && progressReports.none {it.id==progressReportId}) progressReportId=null}
    var reportId by rememberSaveable(workflowNamespace,patientId) {mutableStateOf<String?>(null)}
    var reportQuery by rememberSaveable(workflowNamespace,patientId) {mutableStateOf("")}
    var reportCategory by rememberSaveable(workflowNamespace,patientId) {mutableStateOf<DoctorReportCategory?>(null)}
    val reports=remember(workflowNamespace,patientId) {DoctorReportsData.reports(patientId ?: "")}
    val reportsListState=key(workflowNamespace,patientId) {rememberLazyListState()}
    LaunchedEffect(reportId,reports) {if(reportId!=null && reports.none {it.id==reportId}) reportId=null}
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var appointments by remember { mutableStateOf(demoStore.appointments()) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var cancellingId by rememberSaveable { mutableStateOf<String?>(null) }
    var workflowRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var schedulingRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    val patient = demoStore.patients.firstOrNull { it.id == patientId }
    fun openCare(id: String) = navigateCare { careModel?.select(id); carePersonId = id; patientId = null; careSection = "Hub"; workflowRoute = null; tab = 1 }
    LaunchedEffect(pendingCreatedProfileId, carePeople, careDraftId) {
        val id = pendingCreatedProfileId
        if (id != null && carePeople.any { it.id == id } && careDraftId == id && careModel?.selected?.value == id) {
            openCare(id)
            pendingCreatedProfileId = null
        }
    }
    fun openPatient(id: String) { carePersonId = null; patientId = id; recordTab = "Hub";progressReportId=null;reportId=null }
    fun backPatient() {
        when {
            workflowRoute != null -> workflowRoute = null
            reportId != null -> reportId = null
            progressReportId != null -> progressReportId = null
            recordTab != "Hub" -> recordTab = "Hub"
            else -> {patientId = null;tab = 1}
        }
    }
    fun refresh(result: String?) { notice = result ?: t("Appointment updated", "अपॉइंटमेंट बदला गया"); appointments = demoStore.appointments() }
    fun cancelVisit(id: String) {
        val error = demoStore.cancel(id)
        if (error != null) { notice=error; return }
        val linked = workflow.requests().filter { it.appointmentId==id && it.status==WorkflowRequestStatus.SCHEDULED }
        val failures = linked.mapNotNull { workflow.transitionRequest(it.id, WorkflowRequestStatus.DEFERRED) }
        appointments=demoStore.appointments()
        notice=if(failures.isEmpty()) { if(linked.isEmpty()) t("Visit cancelled", "विज़िट रद्द हुई") else t("Visit cancelled · linked request deferred", "विज़िट रद्द हुई · जुड़ा अनुरोध टाला गया") } else t("Visit cancelled, but request update failed: ${failures.first()}", "विज़िट रद्द हुई, पर अनुरोध नहीं बदला जा सका: ${failures.first()}")
    }
    if (feedbackOpen) {
        FeedbackScreen(accountScope = workflowAccountId ?: "guest-doctor-preview", surface = "doctor-preview", onClose = { feedbackOpen = false }, cloud = careModel?.cloud)
        return
    }
    BackHandler {
        if(sampleCareOpen) {if(!careNavigationBlocked)sampleCareBack?.invoke() ?: run {sampleCareOpen=false}}
        else if (carePersonId != null) { if (!careNavigationBlocked && careNestedBack != null) careNestedBack?.invoke() else navigateCare { if (careSection == "Hub") carePersonId = null else careSection = "Hub" } }
        else when { editingId != null || creatingFor != null -> { editingId = null; creatingFor = null; schedulingRequestId=null }
            workflowRoute != null -> workflowRoute = null
            patientId != null -> backPatient()
            loggedIn && tab != 0 -> tab = 0
            loggedIn -> loggedIn = false
            else -> onExit() }
    }
    if (!loggedIn) {
        DemoLogin(onContinue = { loggedIn = true }, onExit = onExit)
        return
    }
    Scaffold(containerColor = DoctorBackground, topBar = {
        Column(Modifier.background(Paper).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if(sampleCareOpen) IconButton(onClick={sampleCareBack?.invoke() ?: run {sampleCareOpen=false}},enabled=!careNavigationBlocked){Icon(Icons.AutoMirrored.Filled.ArrowBack,t("Back to patient care", "मरीज़ की देखभाल पर वापस"))}
                else if (carePersonId != null) IconButton(onClick = { if (careNestedBack != null) careNestedBack?.invoke() else navigateCare { if (careSection == "Hub") carePersonId = null else careSection = "Hub" } }, enabled = !careNavigationBlocked) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to patient care", "मरीज़ की देखभाल पर वापस")) }
                else if (patient != null) IconButton(onClick = ::backPatient,modifier=Modifier.testTag("demo-patient-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if(reportId!=null) t("Back to reports", "रिपोर्टों पर वापस") else if(progressReportId!=null) t("Back to progress", "प्रगति पर वापस") else if(recordTab=="Hub") t("Back to patients", "मरीज़ों की सूची पर वापस") else t("Back to patient profile", "मरीज़ की प्रोफ़ाइल पर वापस")) }
                else { Image(saharaImage(R.drawable.sahara_professional_logo, 34.dp), null, Modifier.size(34.dp)); Spacer(Modifier.width(8.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(if(sampleCareOpen) DoctorTestProfiles.profiles.firstOrNull {DoctorTestProfiles.personId(it)==testCarePerson}?.name ?: t("Earlier practice care", "पहले की अभ्यास देखभाल") else if (carePersonId != null) carePerson?.name ?: t("Care profile unavailable", "देखभाल प्रोफ़ाइल उपलब्ध नहीं") else if (patient != null) patient.displayName() else "Sahara", fontFamily = SaharaTitleFont, fontWeight = FontWeight.Medium, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CareLanguageMenu(compact = true)
                Spacer(Modifier.width(10.dp))
                if (!sampleCareOpen && patient == null && carePersonId == null) DoctorAvatar("Meera", 34)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }, bottomBar = {
        NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
            val labels = listOf("Home", "Patients", "Schedule", "Account")
            val icons = listOf(Icons.Default.Home, Icons.Default.PeopleOutline, Icons.Default.CalendarMonth, Icons.Default.PersonOutline)
            labels.forEachIndexed { i, label -> NavigationBarItem(selected = if (sampleCareOpen || patientId != null || carePersonId != null) i == 1 else tab == i, enabled = !careNavigationBlocked, onClick = { navigateCare { sampleCareOpen=false;tab = i; carePersonId = null; patientId = null; progressReportId=null;reportId=null;workflowRoute=null; notice = null } },
                icon = { Icon(icons[i], null) }, label = { Text(when (label) { "Home" -> t("Home", "होम"); "Patients" -> t("Patients", "मरीज़"); "Schedule" -> t("Schedule", "समय-सूची"); else -> t("Account", "खाता") }) }, modifier = Modifier.testTag("demo-nav-$label"),
                colors = NavigationBarItemDefaults.colors(indicatorColor = DoctorMist, selectedIconColor = DoctorForest, selectedTextColor = DoctorForest, unselectedIconColor = DoctorMuted, unselectedTextColor = DoctorMuted)) }
        }
    }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            notice?.let { message -> Row(Modifier.fillMaxWidth().background(DoctorCardVisit).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f).announce(), style = MaterialTheme.typography.bodyMedium, color = DoctorInk)
                IconButton(onClick = { notice = null }) { Icon(Icons.Default.Close, t("Dismiss status", "सूचना हटाएँ")) }
            } }
            if(earlierWorkspace && !sampleCareOpen && patientId==null && carePersonId==null) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text(t("Earlier practice workspace", "पहले का अभ्यास वर्कस्पेस"),Modifier.weight(1f));TextButton(onClick={earlierWorkspace=false;workflowRoute=null;tab=0}){Text(t("Return to current workspace", "मौजूदा वर्कस्पेस पर लौटें"))}}
            if(sampleCareOpen) DoctorCareDemoHost(onExit={sampleCareOpen=false;tab=1;testInitialSection="Hub"},onNavigationBlockedChange={careNavigationBlocked=it},onHeaderBackChange={sampleCareBack=it},initialPersonId=testCarePerson,initialSection=testInitialSection)
            else if (carePersonId != null && careModel != null) {
                if (carePerson == null) Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text(t("This care profile is unavailable", "यह देखभाल प्रोफ़ाइल उपलब्ध नहीं है"), style = MaterialTheme.typography.titleLarge); TextButton(onClick = { carePersonId = null }) { Text(t("Back to patients", "मरीज़ों की सूची पर वापस")) } }
                else DoctorPatientWorkspace(careModel, carePerson.id, careSection, { next -> navigateCare { careSection = next } },
                    hub = { pane, open -> DoctorSharedPatientHub(carePerson, { next -> navigateCare { careSection = next } }, deviceLocal = careModel.cloud == null, items = careModel.items, pane = pane, selected = open) },
                    care = { shown -> key(careSelection!!.stateKey) { embeddedCareStates.SaveableStateProvider("${careSelection.stateKey}:care") { EmbeddedPatientCare(careModel, carePersonId!!, shown, { next -> careSection = next }, onCareAccount, { carePersonId = null }, onNavigationBlockedChange = { careNavigationBlocked = it }, allowDeviceLocal = careModel.cloud == null, onHeaderBackChange = { careNestedBack = it }) } } })
            }
            else if(workflowRoute == "requests") DoctorRequestQueue(workflow,demoStore.patients,onBack={workflowRoute=null},onOpen={ id -> patientId=id;recordTab="Conversations";workflowRoute=id },onSchedule={ request -> schedulingRequestId=request.id;creatingFor=request.patientId },refreshToken=appointments.hashCode())
            else if(workflowRoute != null) {
                val target = demoStore.patients.firstOrNull { it.id == workflowRoute }
                if(target!=null) key(target.id,workflowNamespace) { DoctorConversationWorkspace(target,workflow,onBack={workflowRoute=null},cloud=cloud,onSignIn=onSignIn,enableOnlineAi=enableOnlineAi) }
            } else Crossfade(targetState = patientId to tab, animationSpec = tween(160), label = t("Doctor destination", "डॉक्टर कहाँ जाएँ")) { destination ->
            val shownPatient = demoStore.patients.firstOrNull { it.id == destination.first }
            when {
                shownPatient != null && recordTab=="Hub" -> LazyColumn(Modifier.fillMaxSize().testTag("demo-patient-hub"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    item {Row(verticalAlignment=Alignment.CenterVertically) {DoctorAvatar(shownPatient.displayName(),54);Spacer(Modifier.width(12.dp));Column {DoctorTitle(shownPatient.displayName(),24);Text(t("${shownPatient.age} years", "${shownPatient.age} साल"),color=DoctorMuted)}};Spacer(Modifier.height(8.dp));Text(shownPatient.careContext,color=DoctorMuted,maxLines=2,overflow=TextOverflow.Ellipsis)}
                    item {DoctorPatientHub(shownPatient,onSection={progressReportId=null;reportId=null;recordTab=it})}
                }
                shownPatient!=null && recordTab=="Progress" && progressReportId!=null -> {
                    val report=progressReports.firstOrNull {it.id==progressReportId}
                    if(report!=null) LazyColumn(Modifier.fillMaxSize().testTag("progress-detail-page"),contentPadding=PaddingValues(20.dp)) {item {DoctorProgressReportDetail(report,onOpenVisits={progressReportId=null;recordTab="Visits"})}}
                }
                shownPatient!=null && recordTab=="Reports" && reportId!=null -> {
                    val report=reports.firstOrNull {it.id==reportId}
                    if(report!=null) LazyColumn(Modifier.fillMaxSize().testTag("reports-detail-page"),contentPadding=PaddingValues(20.dp)) {item {DoctorReportDetail(report)}}
                }
                shownPatient != null -> DemoPatientDetail(shownPatient, recordTab, { progressReportId=null;reportId=null;recordTab = it }, appointments.filter { it.patientId == shownPatient.id },
                    onSchedule = { creatingFor = shownPatient.id }, onReschedule = { editingId = it.id },
                    onCancel = { cancellingId = it.id }, onComplete = { refresh(demoStore.complete(it.id)) }, onConversation={progressReportId=null;reportId=null;workflowRoute=shownPatient.id},showNavigation=false,
                    overview=DoctorOverviewData.build(shownPatient.id,workflow.versions(shownPatient.id),appointments,System.currentTimeMillis()),
                    progressReports=progressReports,onOpenReport={progressReportId=it},listState=when(recordTab) {"Progress"->progressListState;"Reports"->reportsListState;else->null},
                    reports=reports,reportQuery=reportQuery,reportCategory=reportCategory,onReportQuery={reportQuery=it},onReportCategory={reportCategory=it},onReportOpen={focus.clearFocus();reportId=it},
                    conversations=DoctorConversationsData.build(shownPatient.id,workflow.versions(shownPatient.id),workflow.draft(shownPatient.id),System.currentTimeMillis()))
                destination.second == 0 && earlierWorkspace -> DemoHome(demoStore.patients, appointments, ::openPatient, onSchedule = { tab = 2 }, onAdd = { creatingFor = demoStore.patients.first().id }, workflow = workflow, onRequests={workflowRoute="requests"}, onDraft={workflowRoute=it;patientId=it;recordTab="Conversations"})
                destination.second == 0 -> DoctorHomeWorkday(DoctorTestProfiles.profiles.map { DoctorTestProfiles.personId(it) }.let { ids -> ids.size + carePeople.count { it.id !in ids } }, demoStore.patients, homePending, homeVisits, homeLoaded, workflow, onPatients={tab=1}, onSchedule={tab=2}, onProfile={p->navigateCare {testCarePerson=DoctorTestProfiles.personId(p);testInitialSection="Hub";sampleCareOpen=true}}, onPending={c->navigateCare {testCarePerson=DoctorTestProfiles.personId(c.profile);testInitialSection=c.section;sampleCareOpen=true}}, onConversation={id->workflowRoute=id}, onRequests={workflowRoute="requests"})
                destination.second == 1 -> { var patientQuery by rememberSaveable { mutableStateOf("") }
                // Search matches the patient's name and the family names shown under it.
                val q = patientQuery.trim().lowercase()
                val shownCare = if (q.isEmpty()) carePeople else carePeople.filter { it.name.lowercase().contains(q) }
                val shownTest = if (q.isEmpty()) DoctorTestProfiles.profiles else DoctorTestProfiles.profiles.filter { it.name.lowercase().contains(q) || it.family.lowercase().contains(q) }
                LazyColumn(Modifier.fillMaxSize().testTag("walkthrough-patients-list"), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { DoctorTitle(t("Patients", "मरीज़")) }
                    item { DoctorPatientSearchField(patientQuery, { patientQuery = it }, careHindi()) }
                    if (q.isNotEmpty() && shownCare.isEmpty() && shownTest.isEmpty()) item { DemoEmpty(t("No matching patient", "कोई मरीज़ नहीं मिला"), null, t("Clear search", "खोज मिटाएँ"), { patientQuery = "" }, Icons.Default.Search) }
                    if (careModel != null) {
                        if (carePeople.isNotEmpty()) item { DoctorSection(t("Care profiles", "देखभाल प्रोफ़ाइल"), Modifier.padding(top = 4.dp)) }
                        if (careModel.cloud == null) item { OutlinedButton(onClick = { addingCarePerson = true }, enabled = !careBusy && pendingCreatedProfileId == null, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("doctor-add-device-profile")) { Icon(Icons.Default.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(t("Add profile", "प्रोफ़ाइल जोड़ें")) } }
                        if (pendingCreatedProfileId != null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        items(shownCare, key = { "care:${careAccount}:${it.id}" }) { person -> DoctorPatientListCard(person.name, "", { openCare(person.id) }) }
                    }
                    if (shownTest.isNotEmpty()) item { DoctorSection(t("People in your care", "आपकी देखभाल में लोग"), Modifier.padding(top = 12.dp)) }
                    items(shownTest,key={it.key}) {profile->DoctorPatientListCard(profile.name,profile.family,{navigateCare {testCarePerson=DoctorTestProfiles.personId(profile);testInitialSection="Hub";sampleCareOpen=true}})}
                } }
                destination.second == 2 -> Column(Modifier.fillMaxSize()) {
                    if (carePeople.isNotEmpty()) Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp)) { Text(t("Calendars", "कैलेंडर"), fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { carePeople.forEach { person -> AssistChip(onClick = { openCare(person.id); careSection = "Visits" }, label = { Text(person.name) }, modifier = Modifier.heightIn(min = 48.dp)) } } }
                    if(earlierWorkspace) Box(Modifier.weight(1f)) { DemoSchedule(demoStore.patients, appointments, LocalDate.parse(selectedDay), { selectedDay = it.toString() }, ::openPatient,
                    onAdd = { creatingFor = demoStore.patients.first().id }, onReschedule = { editingId = it.id },
                    onCancel = { cancellingId = it.id }, onComplete = { refresh(demoStore.complete(it.id)) }) }
                    else DoctorPracticeSchedule(careModel, carePeople, onOpenTest = { profile -> navigateCare { testCarePerson = DoctorTestProfiles.personId(profile); testInitialSection = "Visits"; sampleCareOpen = true } }, onOpenCare = { id -> openCare(id); careSection = "Visits" }, onAllPatients = { tab = 1 }, modifier = Modifier.weight(1f))
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)){DemoAccount(onExit,onFeedback={feedbackOpen=true})}
                }
            }
            }
        }
    }
    val latestCareBusy by rememberUpdatedState(careBusy)
    val addCareSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !latestCareBusy })
    if (addingCarePerson && careModel != null && careModel.cloud == null) ModalBottomSheet(onDismissRequest = { if (!careBusy) addingCarePerson = false }, sheetState = addCareSheetState, containerColor = Cream) {
        AddPersonContent(busy = careBusy, error = careError, onDismiss = { if (!careBusy) addingCarePerson = false }, onSave = { name ->
            if (!careBusy) careModel.addPerson(name) { addingCarePerson = false; pendingCreatedProfileId = careModel.selected.value }
        })
    }
    if (creatingFor != null || editingId != null) {
        val editing = appointments.firstOrNull { it.id == editingId }
        DemoAppointmentEditor(demoStore.patients, creatingFor ?: editing?.patientId ?: demoStore.patients.first().id, editing,
            LocalDate.parse(selectedDay), patientLocked = schedulingRequestId!=null, onDismiss = { creatingFor = null; editingId = null; schedulingRequestId=null }, onSave = { appointment ->
                val linkedRequest=schedulingRequestId?.let { id -> workflow.requests().firstOrNull { it.id==id } }
                val error = if(linkedRequest!=null && linkedRequest.patientId!=appointment.patientId) t("Choose the patient linked to this request.", "इस अनुरोध से जुड़ा मरीज़ चुनें।") else demoStore.save(appointment)
                if (error == null) {
                    appointments = demoStore.appointments(); notice = if (editing == null) t("Visit scheduled", "विज़िट तय हुई") else t("Visit rescheduled", "विज़िट का समय बदला")
                    selectedDay = demoDate(appointment.startMillis).toString(); creatingFor = null; editingId = null
                    schedulingRequestId?.let { request ->
                        notice = workflow.transitionRequest(request, WorkflowRequestStatus.SCHEDULED, appointment.id) { id -> demoStore.appointments().firstOrNull { it.id==id } } ?: t("Visit scheduled · request updated locally", "विज़िट तय हुई · अनुरोध फ़ोन पर बदला गया")
                        schedulingRequestId=null
                    }
                }
                error
            })
    }
    cancellingId?.let { id -> AlertDialog(onDismissRequest = { cancellingId = null }, title = { Text(t("Cancel this visit?", "यह विज़िट रद्द करें?")) },
        confirmButton = { TextButton(onClick = { cancelVisit(id); cancellingId = null }, modifier = Modifier.testTag("demo-confirm-cancel")) { Text(t("Cancel visit", "विज़िट रद्द करें")) } },
        dismissButton = { TextButton(onClick = { cancellingId = null }) { Text(t("Keep visit", "विज़िट रहने दें")) } }) }
}

@Composable private fun DemoLogin(onContinue: () -> Unit, onExit: () -> Unit) { val t = careText();
    var email by rememberSaveable { mutableStateOf("doctor@demo.sahara.test") }
    var password by rememberSaveable { mutableStateOf("SaharaDemo") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(DoctorBackground).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back to Sahara", "सहारा पर वापस")) }
            Spacer(Modifier.weight(1f)); Image(saharaImage(R.drawable.sahara_professional_logo, 30.dp), null, Modifier.size(30.dp)); Spacer(Modifier.width(6.dp)); Text(t("Sahara", "सहारा"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        Image(saharaImage(R.drawable.doctor_login_mascot, 180.dp), t("A caring companion holding a notepad", "नोटपैड लिए एक देखभाल करने वाला साथी"), Modifier.fillMaxWidth().height(180.dp), contentScale=androidx.compose.ui.layout.ContentScale.Fit)
        DoctorTitle(t("Doctor login", "डॉक्टर लॉगिन"), 30)
        OutlinedTextField(email, { email = it; error = null }, singleLine = true, label = { Text(t("Email", "ईमेल")) }, leadingIcon = { Icon(Icons.Default.MailOutline, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("demo-email"))
        OutlinedTextField(password, { password = it; error = null }, singleLine = true, label = { Text(t("Password", "पासवर्ड")) }, leadingIcon = { Icon(Icons.Default.LockOpen, null) }, trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (passwordVisible) t("Hide password", "पासवर्ड छिपाएँ") else t("Show password", "पासवर्ड दिखाएँ")) } }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("demo-password"))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("demo-login-error").announce()) }
        Button(onClick = { if (email.trim() == "doctor@demo.sahara.test" && password == "SaharaDemo") onContinue() else error = t("Wrong email or password", "ग़लत ईमेल या पासवर्ड") }, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("demo-login")) { Text(t("Sign in", "साइन इन करें"), fontSize = 16.sp) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun DemoHome(patients: List<DoctorDemoPatient>, appointments: List<DoctorDemoAppointment>, onPatient: (String) -> Unit, onSchedule: () -> Unit, onAdd: () -> Unit, workflow:DoctorWorkflowStore, onRequests:()->Unit, onDraft:(String)->Unit) { val t = careText();
    val today = LocalDate.now()
    val agenda = appointments.filter { demoDate(it.startMillis) == today }.sortedBy { it.startMillis }
    val next = appointments.firstOrNull { it.status == "Scheduled" && it.startMillis > System.currentTimeMillis() }
    LazyColumn(Modifier.fillMaxSize().testTag("demo-home-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(demoDay(today), color = DoctorMuted, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(4.dp)); DoctorTitle(t("Good day, Dr Meera", "नमस्ते, डॉ. मीरा")) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DemoStat(t("Patients", "मरीज़"), patients.size.toString(), DoctorCardProgress, Modifier.weight(1f).testTag("demo-stat-patients"))
            DemoStat(t("Visits today", "आज की विज़िट"), agenda.size.toString(), DoctorCardVisit, Modifier.weight(1f).testTag("demo-stat-today"))
            DemoStat(t("Requests", "अनुरोध"), workflow.requests().count { it.status == WorkflowRequestStatus.NEW }.toString(), DoctorCardMedicine, Modifier.weight(1f).testTag("demo-stat-requests"))
        } }
        item { Surface(color=Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,DoctorHairline)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Forum,null,tint=Rust);Spacer(Modifier.width(10.dp));Text(t("Conversation requests", "बातचीत के अनुरोध"),fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f), fontFamily = SaharaTitleFont);Text(workflow.requests().count { it.status==WorkflowRequestStatus.NEW }.toString(),color=Rust,fontWeight=FontWeight.SemiBold) }; TextButton(onClick=onRequests,modifier=Modifier.heightIn(min=48.dp).testTag("workflow-open-requests")) { Text(t("Open requests", "अनुरोध खोलें"));Icon(Icons.Default.ChevronRight,null) } } } }
        items(workflow.snapshot().drafts.filter { it.updatedAt>0 }.take(2),key={"draft-${it.patientId}"}) { draft -> OutlinedButton(onClick={onDraft(draft.patientId)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-resume-${draft.patientId}")) { Text(t("Resume ${patients.first { it.id==draft.patientId }.displayName()}'s draft", "${patients.first { it.id==draft.patientId }.displayName()} का ड्राफ़्ट जारी रखें")) } }
        next?.let { visit -> item { Surface(color = DoctorForest, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onPatient(visit.patientId) }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Next visit", "अगली विज़िट"), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge) }
            Text(patients.first { it.id == visit.patientId }.displayName(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("${demoDay(demoDate(visit.startMillis))} • ${demoTime(visit.startMillis)}", color = Color.White, fontSize = 14.sp)
            Text(t("${visit.type} · ${visit.durationMinutes} minutes", "${visit.type} · ${visit.durationMinutes} मिनट"), color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
        } } } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(t("Today's agenda", "आज की सूची"), fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontFamily = SaharaTitleFont); TextButton(onClick = onSchedule) { Text(t("View all", "सब देखें")) } } }
        if (agenda.isEmpty()) item { DemoEmpty(t("Your day is open", "आपका दिन खाली है"), null, t("Schedule visit", "विज़िट तय करें"), onAdd) }
        items(agenda, key = { it.id }) { visit -> DemoAgendaRow(visit, patients.first { it.id == visit.patientId }, { onPatient(visit.patientId) }) }
        item { OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("Schedule visit", "विज़िट तय करें")) } }
        item { DoctorSection(t("Patient records", "मरीज़ों के रिकॉर्ड")) }
        items(patients.take(3), key = { it.id }) { DemoPatientRow(it) { onPatient(it.id) } }
    }
}

@Composable internal fun DemoPatients(patients: List<DoctorDemoPatient>, onPatient: (String) -> Unit) { val t = careText();
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = patients.filter { "${it.name} ${it.careContext}".contains(query.trim(), ignoreCase = true) }
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns=if(maxWidth < 340.dp || fontScale >= 1.3f) 1 else 2
    LazyVerticalGrid(columns=GridCells.Fixed(columns),modifier=Modifier.fillMaxSize().testTag("demo-patients-list"), contentPadding = PaddingValues(20.dp), horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) { Column { DoctorTitle(t("Your patients", "आपके मरीज़")); Spacer(Modifier.height(6.dp)) } }
        item(span={GridItemSpan(maxLineSpan)}) { OutlinedTextField(query, { query = it }, placeholder = { Text(t("Search name or care context", "नाम या देखभाल की जानकारी खोजें")) }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if(query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, t("Clear search", "खोज मिटाएँ")) } }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("demo-patient-search")) }
        if (filtered.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { DemoEmpty(t("No matching patients", "कोई मिलता मरीज़ नहीं"), null, t("Clear search", "खोज मिटाएँ"), { query = "" }, Icons.Default.Search) }
        items(filtered, key = { it.id }) { patient ->
            Surface(color=Paper,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,DoctorHairline),modifier=Modifier.fillMaxWidth().heightIn(min=160.dp)
                .testTag("demo-patient-${patient.id}")
                .clickable(role=Role.Button,onClickLabel=t("Open patient record", "मरीज़ का रिकॉर्ड खोलें")) {onPatient(patient.id)}
                .semantics(mergeDescendants=true) {contentDescription="${patient.displayName()}, ${patient.age} years, ${patient.careContext}"}) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {DoctorAvatar(patient.displayName(),42,tint=SaharaSoftBlue);Spacer(Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null,tint=DoctorForest,modifier=Modifier.size(20.dp))}
                    Text(patient.displayName(),fontWeight=FontWeight.SemiBold,fontSize=19.sp,color=DoctorInk)
                    Text(t("${patient.age} years", "${patient.age} साल"),fontSize=14.sp,color=DoctorMuted)
                    Text(patient.careContext,modifier=Modifier.fillMaxWidth(),fontSize=14.sp,color=DoctorMuted,lineHeight=20.sp,maxLines=3,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
    }
}

@Composable private fun DemoPatientRow(patient: DoctorDemoPatient, onOpen: () -> Unit) { val t = careText();
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 78.dp).testTag("demo-patient-${patient.id}").padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoctorAvatar(patient.displayName())
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(patient.displayName(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text(t("${patient.age} years · ${patient.careContext}", "${patient.age} साल · ${patient.careContext}"), color = DoctorMuted, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Icon(Icons.Default.ChevronRight, t("Open ${patient.displayName()}'s record", "${patient.displayName()} का रिकॉर्ड खोलें"), tint = DoctorMuted)
        }
        Spacer(Modifier.height(14.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable private fun DemoAgendaRow(visit: DoctorDemoAppointment, patient: DoctorDemoPatient, onOpen: () -> Unit) { val t = careText();
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 76.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.widthIn(min = 68.dp, max = 90.dp)) { Text(demoTime(visit.startMillis), fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text(t("${visit.durationMinutes} min", "${visit.durationMinutes} मिनट"), fontSize = 14.sp, color = DoctorMuted) }
        Box(Modifier.padding(top = 5.dp).size(7.dp).background(DoctorTeal, CircleShape))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(patient.displayName(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text(visit.type, style = MaterialTheme.typography.bodyMedium, color = DoctorMuted); VisitStatus(visit.status) }
        Icon(Icons.Default.ChevronRight, t("Open ${patient.displayName()}'s record", "${patient.displayName()} का रिकॉर्ड खोलें"), tint = DoctorMuted)
    }
}

@Composable internal fun DemoPatientDetail(patient: DoctorDemoPatient, selected: String, onSection: (String) -> Unit, appointments: List<DoctorDemoAppointment>, onSchedule: () -> Unit, onReschedule: (DoctorDemoAppointment) -> Unit, onCancel: (DoctorDemoAppointment) -> Unit, onComplete: (DoctorDemoAppointment) -> Unit, onConversation:()->Unit,showNavigation:Boolean=true,overview:DoctorPatientOverview?=null,progressReports:List<DoctorProgressReport>?=null,onOpenReport:(String)->Unit={},listState:LazyListState?=null,reports:List<DoctorReportSummary>?=null,reportQuery:String="",reportCategory:DoctorReportCategory?=null,onReportQuery:(String)->Unit={},onReportCategory:(DoctorReportCategory?)->Unit={},onReportOpen:(String)->Unit={},conversations:DoctorConversationsModel?=null) { val t = careText();
    val defaultListState=rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().testTag("demo-patient-detail"),state=listState ?: defaultListState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { DoctorAvatar(patient.displayName(), 58); Spacer(Modifier.width(14.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { DoctorTitle(patient.displayName(), 24); Text(t("${patient.age} years", "${patient.age} साल"), color = DoctorMuted); } }
            Spacer(Modifier.height(8.dp)); Text(patient.careContext, color = DoctorMuted) }
        if(showNavigation) item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Summary" to Icons.Default.GridView, "Progress" to Icons.Default.Insights, "Medicines" to Icons.Default.Medication, "Conversations" to Icons.Default.Forum, "Visits" to Icons.Default.CalendarMonth, "Reports" to Icons.Default.FolderOpen).forEach { (section, icon) ->
                FilterChip(selected == section, onClick = { onSection(section) }, label = { Text(if (section == "Summary") "Overview" else section) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).testTag("demo-record-$section"))
            }
        } }
        if(selected!="Reports") item { DoctorSection(if (selected == "Summary") t("Care overview", "देखभाल का सार") else selected) }
        when (selected) {
            "Summary" -> {
                val summary=overview ?: DoctorOverviewData.build(patient.id,emptyList(),appointments,System.currentTimeMillis())
                if(summary!=null) item {DoctorOverviewContent(summary,onConversation,onSection,onSchedule)}
            }
            "Progress", "Observations" -> {
                item {DoctorProgressTimeline(progressReports ?: DoctorProgressData.reports(patient.id,System.currentTimeMillis()),onOpenReport=onOpenReport)}
            }
            "Conversations", t("Care notes", "देखभाल नोट") -> {
                val history=conversations ?: DoctorConversationsData.build(patient.id,emptyList(),null,System.currentTimeMillis())
                if(history!=null) item {DoctorConversationsContent(history,onConversation)}
            }
            "Medicines" -> {
                item {DoctorMedicineContent(DoctorPatientTracking.doses(patient.id),DoctorPatientTracking.asOf,demoZone)}
            }
            "Visits" -> {
                item { Button(onClick=onSchedule,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("demo-schedule-visit")) { Icon(Icons.Default.Add,null); Spacer(Modifier.width(8.dp)); Text(t("Schedule visit", "विज़िट तय करें")) } }
                if(appointments.isEmpty()) item { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.EventAvailable,null,tint=DoctorAccent); Text(t("No visits yet.", "अभी कोई विज़िट नहीं।"),color=DoctorMuted,fontSize=15.sp) } }
                items(appointments.sortedBy {it.startMillis},key={it.id}) {DemoVisitCard(it,patient,onReschedule,onCancel,onComplete)}
            }
            "Reports" -> {item {DoctorReportsBrowser(patient.displayName(),reports ?: DoctorReportsData.reports(patient.id),reportQuery,reportCategory,onReportQuery,onReportCategory,onReportOpen)}}
            else -> items(patient.observations) { DemoRecordText(it) }
        }
    }
}

@Composable private fun DemoRecordText(text: String) { Surface(color = DoctorCardReport, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DoctorHairline)) { Text(text, Modifier.fillMaxWidth().padding(18.dp), fontSize = 15.sp, color = DoctorCardInk, lineHeight = 23.sp) } }

@Composable internal fun DemoSchedule(patients: List<DoctorDemoPatient>, appointments: List<DoctorDemoAppointment>, day: LocalDate, onDay: (LocalDate) -> Unit, onPatient: (String) -> Unit, onAdd: () -> Unit, onReschedule: (DoctorDemoAppointment) -> Unit, onCancel: (DoctorDemoAppointment) -> Unit, onComplete: (DoctorDemoAppointment) -> Unit) { val t = careText();
    val context = LocalContext.current
    val visits = appointments.filter { demoDate(it.startMillis) == day }.sortedBy { it.startMillis }
    LazyColumn(Modifier.fillMaxSize().testTag("demo-schedule-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DoctorTitle(t("Your schedule", "आपकी समय-सूची")) }
        item { Column { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDay(day.minusDays(1)) }) { Icon(Icons.Default.ChevronLeft, t("Previous day", "पिछला दिन")) }
            TextButton(onClick = { DatePickerDialog(context, { _, y, m, d -> onDay(LocalDate.of(y, m + 1, d)) }, day.year, day.monthValue - 1, day.dayOfMonth).show() }, modifier = Modifier.weight(1f).heightIn(min=48.dp).testTag("demo-schedule-date")) { Text(demoDay(day),fontSize=13.sp) }
            IconButton(onClick = { onDay(day.plusDays(1)) }) { Icon(Icons.Default.ChevronRight, t("Next day", "अगला दिन")) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { (-2L..2L).forEach { offset ->
            val date = day.plusDays(offset)
            Surface(color = if(offset == 0L) DoctorForest else Paper, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).heightIn(min=48.dp).testTag("demo-day-$date").clickable(role=Role.Button,onClickLabel="Select ${demoDay(date)}") { onDay(date) }) {
                Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) { Text(date.format(DateTimeFormatter.ofPattern("EEE")), color = if(offset == 0L) Color.White else DoctorMuted, fontSize = 12.sp); Spacer(Modifier.height(2.dp)); Text(date.dayOfMonth.toString(), color = if(offset == 0L) Color.White else DoctorInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            }
        } } } }
        item { Button(onClick = onAdd, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("demo-add-visit")) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(t("Schedule visit", "विज़िट तय करें")) } }
        item { DoctorSection(if (visits.size == 1) "1 visit" else "${visits.size} visits") }
        if (visits.isEmpty()) item { DemoEmpty(t("No visits on this day", "इस दिन कोई विज़िट नहीं"), null, t("Go to today", "आज पर जाएँ"), { onDay(LocalDate.now()) }) }
        items(visits, key = { it.id }) { visit ->
            val patient = patients.first { it.id == visit.patientId }
            Column { DemoVisitCard(visit, patient, onReschedule, onCancel, onComplete); TextButton(onClick = { onPatient(patient.id) }, modifier = Modifier.heightIn(min=48.dp)) { Text(t("Open ${patient.displayName()}'s record", "${patient.displayName()} का रिकॉर्ड खोलें")); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp)) } }
        }
    }
}

@Composable private fun DemoVisitCard(visit: DoctorDemoAppointment, patient: DoctorDemoPatient, onReschedule: (DoctorDemoAppointment) -> Unit, onCancel: (DoctorDemoAppointment) -> Unit, onComplete: (DoctorDemoAppointment) -> Unit) { val t = careText();
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("demo-appointment-${visit.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(demoTime(visit.startMillis), Modifier.weight(1f), fontSize=22.sp, fontWeight = FontWeight.SemiBold); VisitStatus(visit.status) }
            Text(demoDay(demoDate(visit.startMillis)), color = DoctorMuted, fontSize = 14.sp)
            Text(patient.displayName(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(when(visit.type) { "Phone" -> Icons.Default.Call; "Video" -> Icons.Default.Videocam; else -> Icons.Default.MeetingRoom }, null, Modifier.size(18.dp), tint=DoctorMuted); Spacer(Modifier.width(8.dp)); Text(t("${visit.type} · ${visit.durationMinutes} minutes", "${visit.type} · ${visit.durationMinutes} मिनट"), color=DoctorMuted) }
            if (visit.note.isNotBlank()) Text(visit.note, color=DoctorMuted, style=MaterialTheme.typography.bodyMedium)
            if (visit.status == "Scheduled") {
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { onReschedule(visit) }, shape=RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.EditCalendar,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Reschedule", "समय बदलें")) }; TextButton(onClick = { onComplete(visit) }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.CheckCircle,null,Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Mark complete", "पूरा हुआ चिह्नित करें")) }; TextButton(onClick = { onCancel(visit) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel visit", "विज़िट रद्द करें"), color=MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@Composable internal fun DemoAppointmentEditor(patients: List<DoctorDemoPatient>, patientId: String, existing: DoctorDemoAppointment?, preferredDay: LocalDate, patientLocked:Boolean=false, onDismiss: () -> Unit, onSave: (DoctorDemoAppointment) -> String?) { val t = careText();
    val context = LocalContext.current
    val initial = remember {
        existing?.let { Instant.ofEpochMilli(it.startMillis).atZone(demoZone) }
            ?: ZonedDateTime.of(if (preferredDay > LocalDate.now()) preferredDay else LocalDate.now().plusDays(1), LocalTime.of(10, 0), demoZone)
    }
    var selectedPatient by rememberSaveable { mutableStateOf(patientId) }
    var day by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().toString()) }
    var duration by rememberSaveable { mutableIntStateOf(existing?.durationMinutes ?: 30) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: t("In person", "आमने-सामने")) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var patientMenuOpen by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = DoctorBackground) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(48.dp).background(Peach, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.EventAvailable, null, tint = Rust) }
                DoctorTitle(if (existing == null) t("Schedule a visit", "विज़िट तय करें") else t("Reschedule visit", "विज़िट का समय बदलें"), 24)
            }
            Surface(color = Paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().testTag("demo-visit-patient")) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val patient = patients.firstOrNull { it.id == selectedPatient }
                    DoctorAvatar(patient?.displayName().orEmpty())
                    Column(Modifier.weight(1f)) { Text(t("Patient", "मरीज़"), color = DoctorMuted, style = MaterialTheme.typography.labelMedium); Text(patient?.displayName().orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold); patient?.let { Text(t("${it.age} years", "${it.age} साल"), color = DoctorMuted, style = MaterialTheme.typography.bodySmall) } }
                    if (!patientLocked) Box {
                        TextButton(onClick = { patientMenuOpen = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("demo-change-patient")) { Text(t("Change", "बदलें")); Icon(Icons.Default.ExpandMore, null, Modifier.size(18.dp)) }
                        DropdownMenu(expanded = patientMenuOpen, onDismissRequest = { patientMenuOpen = false }) {
                            patients.forEach { person -> DropdownMenuItem(text = { Text(person.displayName()) }, onClick = { selectedPatient = person.id; error = null; patientMenuOpen = false }, leadingIcon = { Icon(if(person.id == selectedPatient) Icons.Default.CheckCircle else Icons.Default.PersonOutline, null) }, modifier = Modifier.heightIn(min = 48.dp).testTag("demo-visit-patient-${person.id}")) }
                        }
                    }
                }
            }
            val date = LocalDate.parse(day); val clock = LocalTime.parse(time)
            Surface(color = Paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(t("Date & time", "तारीख़ और समय"), fontWeight = FontWeight.SemiBold, fontSize = 18.sp); Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy")), color = DoctorMuted, style = MaterialTheme.typography.bodyMedium) }
                        IconButton(onClick = { DatePickerDialog(context, { _, y, m, d -> day = LocalDate.of(y, m + 1, d).toString(); error = null }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.testTag("demo-visit-date")) { Icon(Icons.Default.CalendarMonth, t("Choose another date", "दूसरी तारीख़ चुनें"), tint = Rust) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (-2L..2L).forEach { offset ->
                            val option = date.plusDays(offset)
                            val selected = option == date
                            Surface(onClick = { day = option.toString(); error = null }, color = if(selected) Rust else Cream, contentColor = if(selected) Color.White else Ink, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(min = 48.dp).semantics { this.selected = selected; contentDescription = demoDay(option) }.testTag("demo-visit-day-$offset")) {
                                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(option.format(DateTimeFormatter.ofPattern("EEE")), style = MaterialTheme.typography.labelSmall); Text(option.dayOfMonth.toString(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }
                    Text(demoDay(date), color = DoctorMuted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(onClick = { TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString(); error = null }, clock.hour, clock.minute, false).show() }, color = Cream, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("demo-visit-time")) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Icon(Icons.Default.Schedule, null, tint = Rust); Column(Modifier.weight(1f)) { Text(t("Starts at", "शुरू होने का समय"), color = DoctorMuted, style = MaterialTheme.typography.labelSmall); Text(clock.format(DateTimeFormatter.ofPattern("h:mm a")), fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }; Text(t("Change", "बदलें"), color = Rust, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            Text(t("Visit type", "विज़िट का प्रकार"), fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(t("In person", "आमने-सामने"), "Phone", "Video").forEach { option ->
                Surface(onClick = { type = option }, color = if(type == option) Peach else Paper, contentColor = if(type == option) RustText else Ink, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if(type == option) Rust.copy(alpha = .5f) else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 72.dp).semantics { selected = type == option }.testTag("demo-visit-type-$option")) { Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) { Icon(when(option) { "Phone" -> Icons.Default.Call; "Video" -> Icons.Default.Videocam; else -> Icons.Default.MeetingRoom }, null, Modifier.size(22.dp)); Text(option, style = MaterialTheme.typography.labelLarge) } }
            } }
            Text(t("Duration", "अवधि"), fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (listOf(15, 30, 45, 60) + duration).distinct().sorted().forEach { minutes -> FilterChip(duration == minutes, onClick = { duration = minutes }, label = { Text(t("$minutes min", "$minutes मिनट")) }, modifier = Modifier.heightIn(min = 48.dp)) } }
            OutlinedTextField(note, { note = it.take(500); error = null }, label = { Text(t("Visit note (optional)", "विज़िट नोट (चाहें तो)")) }, modifier = Modifier.fillMaxWidth().testTag("demo-appointment-note"), minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("demo-appointment-error").announce()) }
            Button(onClick = { error = onSave(DoctorDemoAppointment(existing?.id ?: newId(), selectedPatient, ZonedDateTime.of(date, clock, demoZone).toInstant().toEpochMilli(), duration, type, existing?.status ?: "Scheduled", note)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("demo-save-appointment")) { Text(if (existing == null) t("Save visit", "विज़िट सहेजें") else t("Save changes", "बदलाव सहेजें")) }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Close", "बंद करें")) }
        }
    }
}

@Composable private fun DemoAccount(onExit: () -> Unit, onFeedback:()->Unit) { val t = careText(); var signingOut by remember { mutableStateOf(false) };
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DoctorTitle(t("Account", "खाता"))
        DemoDoctorProfileCard()
        OutlinedButton(onClick = onFeedback, shape=RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-feedback-entry")) { Icon(Icons.Default.SentimentSatisfiedAlt,null); Spacer(Modifier.width(8.dp)); Text(t("Feedback", "राय")) }
        Column(Modifier.fillMaxWidth().testTag("account-sign-out")) {
            OutlinedButton(onClick = { signingOut = true }, shape=RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.error), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("demo-exit")) { Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(t("Sign out", "साइन आउट करें"), fontWeight = FontWeight.SemiBold) }
        }
    }
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text(t("Sign out?", "साइन आउट करें?")) }, confirmButton = { TextButton(onClick = { signingOut = false; onExit() }, modifier = Modifier.testTag("account-sign-out-confirm")) { Text(t("Sign out", "साइन आउट करें")) } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text(t("Cancel", "रद्द करें")) } })
}


/** The walkthrough doctor's profile: name, verified badge, speciality, hospital and a few facts. Sample data only. */
@Composable private fun DemoDoctorProfileCard() { val t = careText()
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("demo-doctor-profile")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = DoctorMist, modifier = Modifier.size(72.dp)) { Box(contentAlignment = Alignment.Center) { Text("M", fontFamily = SaharaTitleFont, fontSize = 32.sp, color = RustText) } }
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = DoctorStatusGoodBg) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null, Modifier.size(16.dp), tint = DoctorStatusGood); Spacer(Modifier.width(4.dp))
                            Text(t("Verified clinician", "सत्यापित डॉक्टर"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = DoctorStatusGood)
                        }
                    }
                    Text(DoctorDemoData.doctorName, fontFamily = SaharaTitleFont, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Text(t("Palliative medicine", "पैलिएटिव मेडिसिन"), fontSize = 14.sp, color = DoctorCardSub)
                }
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Cream, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalHospital, null, Modifier.size(20.dp), tint = DoctorAccent); Spacer(Modifier.width(10.dp))
                    Column { Text(t("Sahara Practice Hospital", "सहारा अभ्यास अस्पताल"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk); Text(t("Palliative care unit · Hyderabad", "पैलिएटिव केयर यूनिट · हैदराबाद"), fontSize = 14.sp, color = DoctorCardSub) }
                }
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DemoDoctorFact(t("Experience", "अनुभव"), t("12 yrs", "12 साल"), Modifier.weight(1f).fillMaxHeight())
                DemoDoctorFact(t("Patients", "मरीज़"), "${DoctorTestProfiles.profiles.size}", Modifier.weight(1f).fillMaxHeight())
                DemoDoctorFact(t("Languages", "भाषाएँ"), "EN · HI · TE", Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable private fun DemoDoctorFact(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = modifier) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 13.sp, color = DoctorCardSub, maxLines = 1)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DoctorCardInk, maxLines = 1)
        }
    }
}

@Composable private fun DemoStat(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(color = tint, shape = RoundedCornerShape(20.dp), modifier = modifier.heightIn(min = 76.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk, fontFamily = SaharaTitleFont)
            Text(label, fontSize = 14.sp, color = DoctorCardSub, lineHeight = 18.sp)
        }
    }
}

@Composable private fun DemoEmpty(title: String, detail: String?, action: String, onAction: () -> Unit, icon: ImageVector = Icons.Default.CalendarToday) {
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(48.dp).background(DoctorMist, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = DoctorAccent, modifier = Modifier.size(24.dp)) }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorInk)
            if (!detail.isNullOrBlank()) Text(detail, color = DoctorMuted, fontSize = 14.sp)
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

internal data class HomeCarePending(val key: String, val profile: DoctorTestProfiles.Profile, val section: String, val title: String, val detail: String)

/** One read of the walkthrough database: an unfinished draft and the next open visit per listed person. Empty when nothing is seeded or the read fails. */
private suspend fun loadHomeCarePending(context: android.content.Context): List<HomeCarePending> = withContext(Dispatchers.IO) {
    runCatching {
        val db = CareDatabase.get(context.applicationContext, CareModel.DOCTOR_DEMO_STORAGE); val now = System.currentTimeMillis(); val people = db.care().allItems()
        DoctorTestProfiles.profiles.flatMap { profile ->
            val id = DoctorTestProfiles.personId(profile)
            val draft = db.care().draft(id)?.takeIf { it.source.isNotBlank() || it.priorities.isNotBlank() || it.questions.isNotBlank() || it.nextSteps.isNotBlank() }
            val visit = people.filter { it.personId == id && it.kind == "visit" && it.due > now && CareEntryCodec.decode(it.detail)?.status == "open" }.minByOrNull { it.due }
            listOfNotNull(
                draft?.let { HomeCarePending("draft-${profile.key}", profile, "Conversations", "Draft", "${demoDay(demoDate(it.updated))}, ${demoTime(it.updated)}") },
                visit?.let { HomeCarePending("visit-${profile.key}", profile, "Visits", CareEntryCodec.decode(it.detail)?.location.orEmpty().ifBlank { "Visit" }, "${demoDay(demoDate(it.due))} · ${demoTime(it.due)}") })
        }
    }.getOrDefault(emptyList())
}

internal data class HomeVisit(val key: String, val profile: DoctorTestProfiles.Profile, val due: Long, val kind: String)

/** Open visits in the next 7 days for the listed people, soonest first. Empty when nothing is saved or the read fails. */
private suspend fun loadHomeVisits(context: android.content.Context): List<HomeVisit> = withContext(Dispatchers.IO) {
    runCatching {
        val db = CareDatabase.get(context.applicationContext, CareModel.DOCTOR_DEMO_STORAGE); val now = System.currentTimeMillis(); val end = now + 7L * 24 * 3600 * 1000; val people = db.care().allItems()
        DoctorTestProfiles.profiles.flatMap { profile -> val id = DoctorTestProfiles.personId(profile)
            people.filter { it.personId == id && it.kind == "visit" && it.due in now..end && CareEntryCodec.decode(it.detail)?.status == "open" }.map { HomeVisit("${profile.key}-${it.id}", profile, it.due, CareEntryCodec.decode(it.detail)?.location.orEmpty().ifBlank { "Visit" }) } }.sortedBy { it.due }
    }.getOrDefault(emptyList())
}

/** Home for the doctor walkthrough. Conversation and request rows come only from this device's workflow store and visit list, and always open the person they belong to. */
@Composable private fun DoctorHomeWorkday(careCount: Int, patients: List<DoctorDemoPatient>, pending: List<HomeCarePending>, visits: List<HomeVisit>, loaded: Boolean, workflow: DoctorWorkflowStore, onPatients: () -> Unit, onSchedule: () -> Unit, onProfile: (DoctorTestProfiles.Profile) -> Unit, onPending: (HomeCarePending) -> Unit, onConversation: (String) -> Unit, onRequests: () -> Unit) { val t = careText();
    val byId = patients.associateBy { it.id }
    val state = workflow.snapshot()
    val newRequests = state.requests.filter { it.status == WorkflowRequestStatus.NEW && it.patientId in byId }.sortedByDescending { it.createdAt }
    LazyColumn(Modifier.fillMaxSize().testTag("demo-home-list"), contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val now = java.time.LocalDateTime.now(); val greet = when { now.hour < 12 -> t("Good morning", "सुप्रभात"); now.hour < 17 -> t("Good afternoon", "नमस्ते"); else -> t("Good evening", "शुभ संध्या") }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontSize = 14.sp, color = DoctorCardSub)
                DoctorTitle("$greet, ${DoctorDemoData.doctorName}", 26)
            }
        }
        item {
            val drafts = pending.filter { it.section == "Conversations" }
            val tiles = listOf<Triple<String, String, Color>>(
                Triple(t("Patients", "मरीज़"), careCount.toString(), DoctorCardOverview),
                Triple(t("Visits this week", "इस हफ़्ते की विज़िट"), if (loaded) visits.size.toString() else "—", DoctorCardProgress),
                Triple(t("Drafts", "ड्राफ़्ट"), if (loaded) drafts.size.toString() else "—", DoctorCardConversation),
                Triple(t("Requests", "अनुरोध"), newRequests.size.toString(), DoctorCardMedicine))
            val clicks = listOf<() -> Unit>(onPatients, onSchedule, { drafts.firstOrNull()?.let(onPending) ?: onPatients() }, onRequests)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (r in 0..1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { for (c in 0..1) { val i = r * 2 + c; val tile = tiles[i]
                    Surface(onClick = clicks[i], color = tile.third, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.weight(1f).heightIn(min = 88.dp).semantics(mergeDescendants = true) { contentDescription = if (tile.second == "—") t("${tile.first}, loading. Opens it.", "${tile.first}, लोड हो रहा है। इसे खोलता है।") else t("${tile.second} ${tile.first}. Opens it.", "${tile.second} ${tile.first}. इसे खोलता है।") }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { Crossfade(tile.second, animationSpec = tween(SaharaMotion.ENTER_MS), label = "home-tile-count") { value -> Text(value, fontFamily = SaharaTitleFont, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = if (value == "—") DoctorCardSub else DoctorCardInk) }; Text(tile.first, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub) }
                    } } }
            }
        }
        // Visits and drafts are read from the device just after the first frame; they fade in and the rows below glide down instead of jumping.
        if (visits.isNotEmpty()) {
            item(key = "home-coming-up") { Box(Modifier.animateItem()) { DoctorSection(t("Coming up", "आने वाला"), Modifier.padding(top = 12.dp)) } }
            items(visits.take(4), key = { "home-visit-${it.key}" }) { v ->
                Surface(onClick = onSchedule, color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.animateItem().fillMaxWidth().heightIn(min = 72.dp).semantics(mergeDescendants = true) { contentDescription = t("${v.profile.name}, ${v.kind}, ${demoDay(demoDate(v.due))} at ${demoTime(v.due)}. Opens the schedule.", "${v.profile.name}, ${v.kind}, ${demoDay(demoDate(v.due))} को ${demoTime(v.due)}. समय-सूची खोलता है।") }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.width(76.dp)) { Text(demoTime(v.due), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = DoctorAccent); Text(demoDate(v.due).format(DateTimeFormatter.ofPattern("EEE d MMM")), fontSize = 14.sp, color = DoctorCardSub) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(v.profile.name, fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk); Text(v.kind, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        Icon(Icons.Default.ChevronRight, null, tint = DoctorAccent)
                    }
                }
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "home-pick-up") { Box(Modifier.animateItem()) { DoctorSection(t("Continue", "जारी रखें"), Modifier.padding(top = 12.dp)) } }
            items(pending, key = { it.key }) { c ->
                Surface(onClick = { onPending(c) }, color = if (c.section == "Visits") DoctorCardVisit else DoctorCardConversation, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.animateItem().fillMaxWidth().heightIn(min = 72.dp).testTag("home-conversation-${c.key}").semantics(mergeDescendants = true) { contentDescription = "${c.profile.name}: ${c.title}. ${c.detail}. Opens ${c.section.lowercase()}." }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (c.section == "Visits") Icons.Default.CalendarMonth else Icons.Default.Forum, null, tint = DoctorAccent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(c.profile.name, fontFamily = SaharaTitleFont, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk); Text(c.title, fontSize = 15.sp, lineHeight = 21.sp, color = DoctorCardInk); Text(c.detail, fontSize = 14.sp, color = DoctorCardSub) }
                        Icon(Icons.Default.ChevronRight, null, tint = DoctorAccent)
                    }
                }
            }
        }
        item { DoctorSection(t("Requests", "अनुरोध"), Modifier.padding(top = 12.dp)) }
        if (newRequests.isEmpty()) item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp), tint = DoctorStatusGood); Text(t("No requests", "कोई अनुरोध नहीं"), fontSize = 15.sp, color = DoctorCardSub) } }
        items(newRequests.take(3), key = { "home-request-${it.id}" }) { r ->
            val name = byId.getValue(r.patientId).displayName()
            Surface(onClick = { onConversation(r.patientId) }, color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).testTag("home-request-${r.id}").semantics(mergeDescendants = true) { contentDescription = t("Conversation to plan, listed under $name: ${r.reason}. Opens the conversation.", "तय करने वाली बातचीत, $name के नाम: ${r.reason}. बातचीत खोलता है।") }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, fontFamily = SaharaTitleFont, fontSize = 19.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk)
                    Text(r.reason, fontSize = 15.sp, lineHeight = 22.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${demoDay(demoDate(r.createdAt))}, ${demoTime(r.createdAt)}", fontSize = 14.sp, color = DoctorCardSub)
                }
            }
        }
        item { TextButton(onClick = onRequests, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-open-requests")) { Text(t("View all", "सब देखें")); Icon(Icons.Default.ChevronRight, null) } }
    }
}
