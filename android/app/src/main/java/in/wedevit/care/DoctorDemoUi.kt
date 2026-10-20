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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
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
private val DoctorMuted = Color(0xFF75665D)
private val DoctorMist = Peach

@Composable private fun DoctorTitle(text: String, size: Int = 28) { Text(text, fontSize = size.sp, lineHeight = (size + 6).sp, fontWeight = FontWeight.SemiBold, color = DoctorInk) }
@Composable private fun DoctorSection(text: String) { Text(text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = DoctorInk) }
@Composable private fun DoctorAvatar(name: String, size: Int = 46, tint:Color=DoctorMist) {
    Box(Modifier.size(size.dp).background(tint, RoundedCornerShape((size / 3).dp)), contentAlignment = Alignment.Center) {
        Text(name.split(' ').mapNotNull { it.firstOrNull() }.take(2).joinToString(""), color = DoctorForest, fontWeight = FontWeight.SemiBold, fontSize = (size / 3).sp)
    }
}
@Composable private fun PreviewLabel() { Surface(color = DoctorMist, shape = RoundedCornerShape(6.dp)) { Text("Preview", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, color = DoctorForest) } }
@Composable private fun VisitStatus(status: String) {
    val color = if (status == "Completed") Rust else DoctorMuted
    Surface(color = color.copy(alpha = .09f), shape = RoundedCornerShape(6.dp)) { Text(status, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelMedium) }
}

/** A separate fictional workspace: no CareModel, account token, Room or network operations. */
@Composable fun DoctorDemo(onExit: () -> Unit, store: DoctorDemoStore? = null, workflowStore: DoctorWorkflowStore? = null, cloud:CareCloud? = null, enableOnlineAi:Boolean=false) {
    val scheme = lightColorScheme(primary = DoctorForest, onPrimary = Color.White, primaryContainer = DoctorMist, onPrimaryContainer = DoctorForest,
        secondary = DoctorTeal, onSecondary = Color.White, secondaryContainer = DoctorMist, onSecondaryContainer = DoctorForest,
        background = DoctorBackground, onBackground = DoctorInk, surface = Paper, onSurface = DoctorInk,
        surfaceVariant = DoctorMist, onSurfaceVariant = DoctorMuted, outline = Color(0xFF8D7B70), outlineVariant = Color(0xFFD7C8BC))
    var authOpen by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = scheme, typography = Typography()) {
        DoctorWorkspace(onExit, store, workflowStore, if(enableOnlineAi) cloud else null, enableOnlineAi, onSignIn={if(enableOnlineAi) authOpen=true})
        if(enableOnlineAi && authOpen && cloud!=null) DoctorAiAccountSheet(cloud,onDismiss={authOpen=false})
    }
}

@Composable private fun DoctorWorkspace(onExit: () -> Unit, store: DoctorDemoStore?, workflowStore: DoctorWorkflowStore?, cloud:CareCloud?,enableOnlineAi:Boolean,onSignIn:()->Unit) {
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
    var loggedIn by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var feedbackOpen by rememberSaveable(workflowNamespace) { mutableStateOf(false) }
    var patientId by rememberSaveable { mutableStateOf<String?>(null) }
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
    fun openPatient(id: String) { patientId = id; recordTab = "Hub";progressReportId=null;reportId=null }
    fun backPatient() {
        when {
            workflowRoute != null -> workflowRoute = null
            reportId != null -> reportId = null
            progressReportId != null -> progressReportId = null
            recordTab != "Hub" -> recordTab = "Hub"
            else -> {patientId = null;tab = 1}
        }
    }
    fun refresh(result: String?) { notice = result ?: "Appointment updated"; appointments = demoStore.appointments() }
    fun cancelVisit(id: String) {
        val error = demoStore.cancel(id)
        if (error != null) { notice=error; return }
        val linked = workflow.requests().filter { it.appointmentId==id && it.status==WorkflowRequestStatus.SCHEDULED }
        val failures = linked.mapNotNull { workflow.transitionRequest(it.id, WorkflowRequestStatus.DEFERRED) }
        appointments=demoStore.appointments()
        notice=if(failures.isEmpty()) { if(linked.isEmpty()) "Visit cancelled" else "Visit cancelled · linked request deferred" } else "Visit cancelled, but request update failed: ${failures.first()}"
    }
    if (feedbackOpen) {
        FeedbackScreen(accountScope = workflowAccountId ?: "guest-doctor-preview", surface = "doctor-preview", onClose = { feedbackOpen = false })
        return
    }
    BackHandler {
        when { editingId != null || creatingFor != null -> { editingId = null; creatingFor = null; schedulingRequestId=null }
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
                if (patient != null) IconButton(onClick = ::backPatient,modifier=Modifier.testTag("demo-patient-back")) { Icon(Icons.AutoMirrored.Filled.ArrowBack, if(reportId!=null) "Back to reports" else if(progressReportId!=null) "Back to progress" else if(recordTab=="Hub") "Back to patients" else "Back to patient profile") }
                else { Image(painterResource(R.drawable.sahara_professional_logo), null, Modifier.size(34.dp)); Spacer(Modifier.width(8.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(if (patient != null) patient.displayName() else "Sahara", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (patient != null) "Patient record" else "Doctor workspace", color = DoctorMuted, fontSize = 12.sp)
                }
                PreviewLabel()
                Spacer(Modifier.width(10.dp))
                if (patient == null) DoctorAvatar("Meera", 34)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }, bottomBar = {
        NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
            val labels = listOf("Home", "Patients", "Schedule", "Account")
            val icons = listOf(Icons.Default.Home, Icons.Default.PeopleOutline, Icons.Default.CalendarMonth, Icons.Default.PersonOutline)
            labels.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i && patientId == null, onClick = { tab = i; patientId = null; progressReportId=null;reportId=null;workflowRoute=null; notice = null },
                icon = { Icon(icons[i], null) }, label = { Text(label) }, modifier = Modifier.testTag("demo-nav-$label"),
                colors = NavigationBarItemDefaults.colors(indicatorColor = DoctorMist, selectedIconColor = DoctorForest, selectedTextColor = DoctorForest, unselectedIconColor = DoctorMuted, unselectedTextColor = DoctorMuted)) }
        }
    }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            notice?.let { message -> Row(Modifier.fillMaxWidth().background(DoctorMist).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { notice = null }) { Icon(Icons.Default.Close, "Dismiss status") }
            } }
            if(workflowRoute == "requests") DoctorRequestQueue(workflow,demoStore.patients,onBack={workflowRoute=null},onOpen={ id -> patientId=id;recordTab="Conversations";workflowRoute=id },onSchedule={ request -> schedulingRequestId=request.id;creatingFor=request.patientId },refreshToken=appointments.hashCode())
            else if(workflowRoute != null) {
                val target = demoStore.patients.firstOrNull { it.id == workflowRoute }
                if(target!=null) key(target.id,workflowNamespace) { DoctorConversationWorkspace(target,workflow,onBack={workflowRoute=null},cloud=cloud,onSignIn=onSignIn,enableOnlineAi=enableOnlineAi) }
            } else Crossfade(targetState = patientId to tab, animationSpec = tween(160), label = "Doctor destination") { destination ->
            val shownPatient = demoStore.patients.firstOrNull { it.id == destination.first }
            when {
                shownPatient != null && recordTab=="Hub" -> LazyColumn(Modifier.fillMaxSize().testTag("demo-patient-hub"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    item {Row(verticalAlignment=Alignment.CenterVertically) {DoctorAvatar(shownPatient.displayName(),54);Spacer(Modifier.width(12.dp));Column {DoctorTitle(shownPatient.displayName(),24);Text("${shownPatient.age} years",color=DoctorMuted)}};Spacer(Modifier.height(10.dp));Text(shownPatient.careContext,color=DoctorMuted);Text("Sample record · not connected to a patient account",style=MaterialTheme.typography.bodySmall,color=DoctorMuted)}
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
                destination.second == 0 -> DemoHome(demoStore.patients, appointments, ::openPatient, onSchedule = { tab = 2 }, onAdd = { creatingFor = demoStore.patients.first().id }, workflow = workflow, onRequests={workflowRoute="requests"}, onDraft={workflowRoute=it;patientId=it;recordTab="Conversations"})
                destination.second == 1 -> DemoPatients(demoStore.patients, ::openPatient)
                destination.second == 2 -> DemoSchedule(demoStore.patients, appointments, LocalDate.parse(selectedDay), { selectedDay = it.toString() }, ::openPatient,
                    onAdd = { creatingFor = demoStore.patients.first().id }, onReschedule = { editingId = it.id },
                    onCancel = { cancellingId = it.id }, onComplete = { refresh(demoStore.complete(it.id)) })
                else -> DemoAccount(onExit,onFeedback={feedbackOpen=true})
            }
            }
        }
    }
    if (creatingFor != null || editingId != null) {
        val editing = appointments.firstOrNull { it.id == editingId }
        DemoAppointmentEditor(demoStore.patients, creatingFor ?: editing?.patientId ?: demoStore.patients.first().id, editing,
            LocalDate.parse(selectedDay), patientLocked = schedulingRequestId!=null, onDismiss = { creatingFor = null; editingId = null; schedulingRequestId=null }, onSave = { appointment ->
                val linkedRequest=schedulingRequestId?.let { id -> workflow.requests().firstOrNull { it.id==id } }
                val error = if(linkedRequest!=null && linkedRequest.patientId!=appointment.patientId) "Choose the patient linked to this request." else demoStore.save(appointment)
                if (error == null) {
                    appointments = demoStore.appointments(); notice = if (editing == null) "Visit scheduled" else "Visit rescheduled"
                    selectedDay = demoDate(appointment.startMillis).toString(); creatingFor = null; editingId = null
                    schedulingRequestId?.let { request ->
                        notice = workflow.transitionRequest(request, WorkflowRequestStatus.SCHEDULED, appointment.id) { id -> demoStore.appointments().firstOrNull { it.id==id } } ?: "Visit scheduled · request updated locally"
                        schedulingRequestId=null
                    }
                }
                error
            })
    }
    cancellingId?.let { id -> AlertDialog(onDismissRequest = { cancellingId = null }, title = { Text("Cancel this visit?") },
        text = { Text("The visit will stay in the schedule with a Cancelled status.") },
        confirmButton = { TextButton(onClick = { cancelVisit(id); cancellingId = null }, modifier = Modifier.testTag("demo-confirm-cancel")) { Text("Cancel visit") } },
        dismissButton = { TextButton(onClick = { cancellingId = null }) { Text("Keep visit") } }) }
}

@Composable private fun DemoLogin(onContinue: () -> Unit, onExit: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("doctor@demo.sahara.test") }
    var password by rememberSaveable { mutableStateOf("SaharaDemo") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(DoctorBackground).safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Sahara") }
            Spacer(Modifier.weight(1f)); Image(painterResource(R.drawable.sahara_professional_logo), null, Modifier.size(30.dp)); Spacer(Modifier.width(6.dp)); Text("Sahara", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        }
        Image(painterResource(R.drawable.doctor_login_mascot), "A caring companion holding a notepad", Modifier.fillMaxWidth().height(180.dp), contentScale=androidx.compose.ui.layout.ContentScale.Fit)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { DoctorTitle("Welcome back", 30); Text("Manage patient records and appointments.", color = DoctorMuted, fontSize = 16.sp, lineHeight = 23.sp) }
        Text("Doctor login", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        OutlinedTextField(email, { email = it; error = null }, singleLine = true, label = { Text("Email") }, leadingIcon = { Icon(Icons.Default.MailOutline, null) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("demo-email"))
        OutlinedTextField(password, { password = it; error = null }, singleLine = true, label = { Text("Password") }, leadingIcon = { Icon(Icons.Default.LockOpen, null) }, trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (passwordVisible) "Hide password" else "Show password") } }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("demo-password"))
        Text("Preview account prefilled", style = MaterialTheme.typography.bodySmall, color = DoctorMuted)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("demo-login-error")) }
        Button(onClick = { if (email.trim() == "doctor@demo.sahara.test" && password == "SaharaDemo") onContinue() else error = "Use the prefilled preview account: doctor@demo.sahara.test / SaharaDemo." }, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("demo-login")) { Text("Open workspace", fontSize = 16.sp); Spacer(Modifier.width(10.dp)); Icon(Icons.Default.ArrowForward, null, Modifier.size(20.dp)) }
        Text("Sample records · local preview\nNo account is created or authenticated.", color = DoctorMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp, modifier = Modifier.testTag("demo-preview-disclosure"))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable private fun DemoHome(patients: List<DoctorDemoPatient>, appointments: List<DoctorDemoAppointment>, onPatient: (String) -> Unit, onSchedule: () -> Unit, onAdd: () -> Unit, workflow:DoctorWorkflowStore, onRequests:()->Unit, onDraft:(String)->Unit) {
    val today = LocalDate.now()
    val agenda = appointments.filter { demoDate(it.startMillis) == today }.sortedBy { it.startMillis }
    val next = appointments.firstOrNull { it.status == "Scheduled" && it.startMillis > System.currentTimeMillis() }
    LazyColumn(Modifier.fillMaxSize().testTag("demo-home-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text(demoDay(today), color = DoctorMuted, style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(7.dp)); DoctorTitle("Good day, Dr Meera"); Spacer(Modifier.height(6.dp)); Text("A clear view of the care ahead.", color = DoctorMuted) }
        item { Surface(color=Paper,shape=RoundedCornerShape(16.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Forum,null,tint=Rust);Spacer(Modifier.width(10.dp));Text("Conversation requests",fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f));Text(workflow.requests().count { it.status==WorkflowRequestStatus.NEW }.toString(),color=Rust,fontWeight=FontWeight.SemiBold) }; Text("Review requests and pick up unfinished discussions.",style=MaterialTheme.typography.bodySmall,color=DoctorMuted); TextButton(onClick=onRequests,modifier=Modifier.heightIn(min=48.dp).testTag("workflow-open-requests")) { Text("Open requests");Icon(Icons.Default.ChevronRight,null) } } } }
        items(workflow.snapshot().drafts.filter { it.updatedAt>0 }.take(2),key={"draft-${it.patientId}"}) { draft -> OutlinedButton(onClick={onDraft(draft.patientId)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-resume-${draft.patientId}")) { Text("Resume ${patients.first { it.id==draft.patientId }.displayName()}'s draft") } }
        next?.let { visit -> item { Surface(color = DoctorForest, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onPatient(visit.patientId) }) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Schedule, null, tint = Color.White.copy(alpha = .8f), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Next visit", color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelLarge) }
            Text(patients.first { it.id == visit.patientId }.displayName(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("${demoDay(demoDate(visit.startMillis))} • ${demoTime(visit.startMillis)}", color = Color.White, fontSize = 14.sp)
            Text("${visit.type} · ${visit.durationMinutes} minutes", color = Color.White.copy(alpha = .8f), fontSize = 13.sp)
        } } } }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Today's agenda", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); TextButton(onClick = onSchedule) { Text("View all") } } }
        if (agenda.isEmpty()) item { DemoEmpty("Your day is open", "Add a visit to start planning your day.", "Schedule visit", onAdd) }
        items(agenda, key = { it.id }) { visit -> DemoAgendaRow(visit, patients.first { it.id == visit.patientId }, { onPatient(visit.patientId) }) }
        item { OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Schedule visit") } }
        item { DoctorSection("Patient records") }
        items(patients.take(3), key = { it.id }) { DemoPatientRow(it) { onPatient(it.id) } }
    }
}

@Composable internal fun DemoPatients(patients: List<DoctorDemoPatient>, onPatient: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = patients.filter { "${it.name} ${it.careContext}".contains(query.trim(), ignoreCase = true) }
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val columns=if(maxWidth < 340.dp || fontScale >= 1.3f) 1 else 2
    LazyVerticalGrid(columns=GridCells.Fixed(columns),modifier=Modifier.fillMaxSize().testTag("demo-patients-list"), contentPadding = PaddingValues(20.dp), horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span={GridItemSpan(maxLineSpan)}) { Column { DoctorTitle("Your patients"); Spacer(Modifier.height(6.dp)); Text("Find a patient and pick up their care story.", color = DoctorMuted) } }
        item(span={GridItemSpan(maxLineSpan)}) { OutlinedTextField(query, { query = it }, placeholder = { Text("Search name or care context") }, leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if(query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("demo-patient-search")) }
        item(span={GridItemSpan(maxLineSpan)}) { Text("${filtered.size} patient records", style = MaterialTheme.typography.labelLarge, color = DoctorMuted) }
        if (filtered.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { DemoEmpty("No matching patients", "Try a name or care context.", "Clear search", { query = "" }) }
        items(filtered, key = { it.id }) { patient ->
            Surface(color=Paper,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth().heightIn(min=198.dp)
                .testTag("demo-patient-${patient.id}")
                .clickable(role=Role.Button,onClickLabel="Open patient record") {onPatient(patient.id)}
                .semantics(mergeDescendants=true) {contentDescription="${patient.displayName()}, ${patient.age} years, ${patient.careContext}"}) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {DoctorAvatar(patient.displayName(),42,tint=SaharaSoftBlue);Spacer(Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null,tint=DoctorForest,modifier=Modifier.size(20.dp))}
                    Text(patient.displayName(),fontWeight=FontWeight.SemiBold,fontSize=19.sp,color=DoctorInk)
                    Text("${patient.age} years",style=MaterialTheme.typography.bodySmall,color=DoctorMuted)
                    Text(patient.careContext,modifier=Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=DoctorMuted,lineHeight=18.sp)
                }
            }
        }
    }
    }
}

@Composable private fun DemoPatientRow(patient: DoctorDemoPatient, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 78.dp).testTag("demo-patient-${patient.id}").padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DoctorAvatar(patient.displayName())
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(patient.displayName(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold); Text("${patient.age} years · ${patient.careContext}", color = DoctorMuted, style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp) }
            Icon(Icons.Default.ChevronRight, "Open ${patient.displayName()}'s record", tint = DoctorMuted)
        }
        Spacer(Modifier.height(14.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable private fun DemoAgendaRow(visit: DoctorDemoAppointment, patient: DoctorDemoPatient, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 76.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.widthIn(min = 68.dp, max = 90.dp)) { Text(demoTime(visit.startMillis), fontWeight = FontWeight.SemiBold, fontSize = 15.sp); Text("${visit.durationMinutes} min", style = MaterialTheme.typography.bodySmall, color = DoctorMuted) }
        Box(Modifier.padding(top = 5.dp).size(7.dp).background(DoctorTeal, CircleShape))
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(patient.displayName(), fontWeight = FontWeight.SemiBold, fontSize = 17.sp); Text(visit.type, style = MaterialTheme.typography.bodyMedium, color = DoctorMuted); VisitStatus(visit.status) }
        Icon(Icons.Default.ChevronRight, "Open ${patient.displayName()}'s record", tint = DoctorMuted)
    }
}

@Composable internal fun DemoPatientDetail(patient: DoctorDemoPatient, selected: String, onSection: (String) -> Unit, appointments: List<DoctorDemoAppointment>, onSchedule: () -> Unit, onReschedule: (DoctorDemoAppointment) -> Unit, onCancel: (DoctorDemoAppointment) -> Unit, onComplete: (DoctorDemoAppointment) -> Unit, onConversation:()->Unit,showNavigation:Boolean=true,overview:DoctorPatientOverview?=null,progressReports:List<DoctorProgressReport>?=null,onOpenReport:(String)->Unit={},listState:LazyListState?=null,reports:List<DoctorReportSummary>?=null,reportQuery:String="",reportCategory:DoctorReportCategory?=null,onReportQuery:(String)->Unit={},onReportCategory:(DoctorReportCategory?)->Unit={},onReportOpen:(String)->Unit={},conversations:DoctorConversationsModel?=null) {
    val defaultListState=rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().testTag("demo-patient-detail"),state=listState ?: defaultListState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { DoctorAvatar(patient.displayName(), 58); Spacer(Modifier.width(14.dp)); Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { DoctorTitle(patient.displayName(), 24); Text("${patient.age} years", color = DoctorMuted); } }
            Spacer(Modifier.height(8.dp)); Text(patient.careContext, color = DoctorMuted)
            Text("Sample record · not connected to a patient account",color=DoctorMuted,style=MaterialTheme.typography.bodySmall) }
        if(showNavigation) item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("Summary" to Icons.Default.GridView, "Progress" to Icons.Default.Insights, "Medicines" to Icons.Default.Medication, "Conversations" to Icons.Default.Forum, "Visits" to Icons.Default.CalendarMonth, "Reports" to Icons.Default.FolderOpen).forEach { (section, icon) ->
                FilterChip(selected == section, onClick = { onSection(section) }, label = { Text(if (section == "Summary") "Overview" else section) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("demo-record-$section"))
            }
        } }
        if(selected!="Reports") item { DoctorSection(if (selected == "Summary") "Care overview" else selected) }
        when (selected) {
            "Summary" -> {
                val summary=overview ?: DoctorOverviewData.build(patient.id,emptyList(),appointments,System.currentTimeMillis())
                if(summary!=null) item {DoctorOverviewContent(summary,onConversation,onSection,onSchedule)}
            }
            "Progress", "Observations" -> {
                item {DoctorProgressTimeline(progressReports ?: DoctorProgressData.reports(patient.id,System.currentTimeMillis()),onOpenReport=onOpenReport)}
            }
            "Conversations", "Care notes" -> {
                val history=conversations ?: DoctorConversationsData.build(patient.id,emptyList(),null,System.currentTimeMillis())
                if(history!=null) item {DoctorConversationsContent(history,onConversation)}
            }
            "Medicines" -> {
                item {DoctorMedicineContent(DoctorPatientTracking.doses(patient.id),DoctorPatientTracking.asOf,demoZone)}
            }
            "Visits" -> {
                item { Button(onClick=onSchedule,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("demo-schedule-visit")) { Text("Schedule visit") } }
                if(appointments.isEmpty()) item { Text("No visits yet.",color=DoctorMuted) }
                items(appointments.sortedBy {it.startMillis},key={it.id}) {DemoVisitCard(it,patient,onReschedule,onCancel,onComplete)}
            }
            "Reports" -> {item {DoctorReportsBrowser(patient.displayName(),reports ?: DoctorReportsData.reports(patient.id),reportQuery,reportCategory,onReportQuery,onReportCategory,onReportOpen)}}
            else -> items(patient.observations) { DemoRecordText(it) }
        }
    }
}

private fun recordDisplayText(text: String) = text
    .replace("Demo check-in:", "Check-in:")
    .replace("Demo family update:", "Family update:")
    .replace("Demo observation:", "Observation:")
    .replace("Fictional discussion note:", "Discussion note:")

@Composable private fun DemoRecordText(text: String) { Surface(color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(recordDisplayText(text), Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp) } }

@Composable internal fun DemoSchedule(patients: List<DoctorDemoPatient>, appointments: List<DoctorDemoAppointment>, day: LocalDate, onDay: (LocalDate) -> Unit, onPatient: (String) -> Unit, onAdd: () -> Unit, onReschedule: (DoctorDemoAppointment) -> Unit, onCancel: (DoctorDemoAppointment) -> Unit, onComplete: (DoctorDemoAppointment) -> Unit) {
    val context = LocalContext.current
    val visits = appointments.filter { demoDate(it.startMillis) == day }.sortedBy { it.startMillis }
    LazyColumn(Modifier.fillMaxSize().testTag("demo-schedule-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DoctorTitle("Your schedule"); Spacer(Modifier.height(6.dp)); Text("Make room for each conversation.", color = DoctorMuted) }
        item { Column { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDay(day.minusDays(1)) }) { Icon(Icons.Default.ChevronLeft, "Previous day") }
            TextButton(onClick = { DatePickerDialog(context, { _, y, m, d -> onDay(LocalDate.of(y, m + 1, d)) }, day.year, day.monthValue - 1, day.dayOfMonth).show() }, modifier = Modifier.weight(1f).heightIn(min=48.dp).testTag("demo-schedule-date")) { Text(demoDay(day),fontSize=13.sp) }
            IconButton(onClick = { onDay(day.plusDays(1)) }) { Icon(Icons.Default.ChevronRight, "Next day") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) { (-2L..2L).forEach { offset ->
            val date = day.plusDays(offset)
            Surface(color = if(offset == 0L) DoctorForest else Paper, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f).heightIn(min=48.dp).testTag("demo-day-$date").clickable(role=Role.Button,onClickLabel="Select ${demoDay(date)}") { onDay(date) }) {
                Column(Modifier.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) { Text(date.format(DateTimeFormatter.ofPattern("EEE")), color = if(offset == 0L) Color.White.copy(alpha=.8f) else DoctorMuted, fontSize = 10.sp); Spacer(Modifier.height(2.dp)); Text(date.dayOfMonth.toString(), color = if(offset == 0L) Color.White else DoctorInk, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
            }
        } } } }
        item { Button(onClick = onAdd, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("demo-add-visit")) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Schedule visit") } }
        item { DoctorSection(if (visits.size == 1) "1 visit" else "${visits.size} visits") }
        if (visits.isEmpty()) item { DemoEmpty("No visits on this day", "Choose another day or schedule a visit.", "Go to today", { onDay(LocalDate.now()) }) }
        items(visits, key = { it.id }) { visit ->
            val patient = patients.first { it.id == visit.patientId }
            Column { DemoVisitCard(visit, patient, onReschedule, onCancel, onComplete); TextButton(onClick = { onPatient(patient.id) }, modifier = Modifier.heightIn(min=48.dp)) { Text("Open ${patient.displayName()}'s record"); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp)) } }
        }
    }
}

@Composable private fun DemoVisitCard(visit: DoctorDemoAppointment, patient: DoctorDemoPatient, onReschedule: (DoctorDemoAppointment) -> Unit, onCancel: (DoctorDemoAppointment) -> Unit, onComplete: (DoctorDemoAppointment) -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().testTag("demo-appointment-${visit.id}")) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(demoTime(visit.startMillis), Modifier.weight(1f), fontSize=22.sp, fontWeight = FontWeight.SemiBold); VisitStatus(visit.status) }
            Text(demoDay(demoDate(visit.startMillis)), color = DoctorMuted, style = MaterialTheme.typography.bodySmall)
            Text(patient.displayName(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(when(visit.type) { "Phone" -> Icons.Default.Call; "Video" -> Icons.Default.Videocam; else -> Icons.Default.MeetingRoom }, null, Modifier.size(18.dp), tint=DoctorMuted); Spacer(Modifier.width(8.dp)); Text("${visit.type} · ${visit.durationMinutes} minutes", color=DoctorMuted) }
            if (visit.note.isNotBlank()) Text(visit.note, color=DoctorMuted, style=MaterialTheme.typography.bodyMedium)
            if (visit.status == "Scheduled") {
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                OutlinedButton(onClick = { onReschedule(visit) }, shape=RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.EditCalendar,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reschedule") }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { TextButton(onClick = { onComplete(visit) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mark complete") }; TextButton(onClick = { onCancel(visit) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Cancel visit", color=DoctorMuted) } }
            }
        }
    }
}

@Composable private fun DemoAppointmentEditor(patients: List<DoctorDemoPatient>, patientId: String, existing: DoctorDemoAppointment?, preferredDay: LocalDate, patientLocked:Boolean=false, onDismiss: () -> Unit, onSave: (DoctorDemoAppointment) -> String?) {
    val context = LocalContext.current
    val initial = remember {
        existing?.let { Instant.ofEpochMilli(it.startMillis).atZone(demoZone) }
            ?: ZonedDateTime.of(if (preferredDay > LocalDate.now()) preferredDay else LocalDate.now().plusDays(1), LocalTime.of(10, 0), demoZone)
    }
    var selectedPatient by rememberSaveable { mutableStateOf(patientId) }
    var day by rememberSaveable { mutableStateOf(initial.toLocalDate().toString()) }
    var time by rememberSaveable { mutableStateOf(initial.toLocalTime().toString()) }
    var duration by rememberSaveable { mutableIntStateOf(existing?.durationMinutes ?: 30) }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: "In person") }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = DoctorBackground) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DoctorTitle(if (existing == null) "Schedule a visit" else "Reschedule visit", 26)
            Text("Local schedule only. No invitations or reminders are sent.", color=DoctorMuted, style = MaterialTheme.typography.bodySmall)
            Text("Patient", fontWeight=FontWeight.SemiBold)
            FlowRow(Modifier.fillMaxWidth().testTag("demo-visit-patient"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                patients.filter { !patientLocked || it.id == selectedPatient }.forEach { patient ->
                    FilterChip(selected = patient.id == selectedPatient, onClick = { selectedPatient = patient.id; error = null }, enabled = !patientLocked,
                        label = { Text(patient.displayName()) }, modifier = Modifier.heightIn(min = 48.dp).testTag("demo-visit-patient-${patient.id}"))
                }
            }
            if(patientLocked) Text("Patient linked to the conversation request",style=MaterialTheme.typography.bodySmall,color=DoctorMuted)
            Text("Date & time", fontWeight=FontWeight.SemiBold)
            val date = LocalDate.parse(day); val clock = LocalTime.parse(time)
            OutlinedButton(onClick = { DatePickerDialog(context, { _, y, m, d -> day = LocalDate.of(y, m + 1, d).toString(); error = null }, date.year, date.monthValue - 1, date.dayOfMonth).show() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("demo-visit-date")) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(8.dp)); Text(demoDay(date)) }
            OutlinedButton(onClick = { TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m).toString(); error = null }, clock.hour, clock.minute, false).show() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("demo-visit-time")) { Icon(Icons.Default.Schedule, null); Spacer(Modifier.width(8.dp)); Text(clock.format(DateTimeFormatter.ofPattern("h:mm a"))) }
            Text("Visit type", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("In person", "Phone", "Video").forEach { option -> FilterChip(type == option, onClick = { type = option }, label = { Text(option) }, modifier = Modifier.heightIn(min = 48.dp)) } }
            Text("Duration", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { (listOf(15, 30, 45, 60) + duration).distinct().sorted().forEach { minutes -> FilterChip(duration == minutes, onClick = { duration = minutes }, label = { Text("$minutes min") }, modifier = Modifier.heightIn(min = 48.dp)) } }
            OutlinedTextField(note, { note = it.take(500); error = null }, label = { Text("Visit note (optional)") }, modifier = Modifier.fillMaxWidth().testTag("demo-appointment-note"), minLines = 2)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("demo-appointment-error")) }
            Button(onClick = { error = onSave(DoctorDemoAppointment(existing?.id ?: newId(), selectedPatient, ZonedDateTime.of(date, clock, demoZone).toInstant().toEpochMilli(), duration, type, existing?.status ?: "Scheduled", note)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("demo-save-appointment")) { Text(if (existing == null) "Save visit" else "Save changes") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Close") }
        }
    }
}

@Composable private fun DemoAccount(onExit: () -> Unit, onFeedback:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        DoctorTitle("Account")
        Surface(color=Paper, shape=RoundedCornerShape(20.dp), modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(20.dp), verticalAlignment=Alignment.CenterVertically) { DoctorAvatar("Meera", 60); Spacer(Modifier.width(16.dp)); Column(verticalArrangement=Arrangement.spacedBy(5.dp)) { DoctorTitle("Dr Meera", 22); Text("Doctor workspace", color=DoctorMuted); PreviewLabel() } } }
        DoctorSection("About this workspace")
        Text("Local preview; no identity verification or real patient access.", fontWeight=FontWeight.Medium, modifier=Modifier.testTag("demo-account-disclosure"))
        Text("Meera and the patient records are fictional. Sample appointments are saved on this device, separately from your Sahara account.", color=DoctorMuted, lineHeight=23.sp)
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment=Alignment.Top) { Icon(Icons.Default.EventNote,null,tint=DoctorTeal); Spacer(Modifier.width(12.dp)); Column { Text("Local scheduling", fontWeight=FontWeight.SemiBold); Text("No invitations, calls or reminders are sent.", color=DoctorMuted, style=MaterialTheme.typography.bodyMedium) } }
        Row(verticalAlignment=Alignment.Top) { Icon(Icons.Default.FolderOpen,null,tint=DoctorTeal); Spacer(Modifier.width(12.dp)); Column { Text("Sample records", fontWeight=FontWeight.SemiBold); Text("Patient profiles, progress and medicine reports are fictional. This mock workspace does not upload audio, run online AI or connect to patient accounts. No prescriptions are created.", color=DoctorMuted, style=MaterialTheme.typography.bodyMedium) } }
        OutlinedButton(onClick = onFeedback, shape=RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("doctor-feedback-entry")) { Icon(Icons.Default.SentimentSatisfiedAlt,null); Spacer(Modifier.width(8.dp)); Text("App feedback") }
        OutlinedButton(onClick = onExit, shape=RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("demo-exit")) { Icon(Icons.AutoMirrored.Filled.ArrowBack,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Back to Sahara") }
    }
}

@Composable private fun DemoEmpty(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.CalendarToday, null, tint=DoctorTeal, modifier=Modifier.size(30.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(detail, color=DoctorMuted); OutlinedButton(onClick = onAction, shape=RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}
