@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CareRootModel(app: Application) : AndroidViewModel(app) {
    val cloud = CareCloud(app)
    private var account: String? = null
    private var careStore = ViewModelStore()
    fun storeFor(id: String): ViewModelStore {
        if (account != id) { careStore.clear(); careStore = ViewModelStore(); account = id }
        return careStore
    }
    override fun onCleared() { careStore.clear(); super.onCleared() }
}

@Composable fun CareRoot(activity: MainActivity) {
    val root = remember { ViewModelProvider(activity)[CareRootModel::class.java] }
    val cloud = root.cloud
    var demoOpen by rememberSaveable { mutableStateOf(false) }
    var walkthroughPatient by rememberSaveable { mutableStateOf<String?>(null) }
    var patientWalkthroughUser by rememberSaveable { mutableStateOf<String?>(null) }
    var doctorOpen by rememberSaveable { mutableStateOf(false) }
    var doctorEntryPending by rememberSaveable { mutableStateOf(false) }
    val session by cloud.session.collectAsStateWithLifecycle()
    LaunchedEffect(session?.userId) {
        if (session != null && doctorEntryPending) { doctorOpen = true; doctorEntryPending = false }
        if (session == null) doctorOpen = false
    }
    key(session?.userId.orEmpty()) {
        val store = remember { root.storeFor(session?.userId.orEmpty()) }
        val owner = remember { object : ViewModelStoreOwner { override val viewModelStore = store } }
        val model = remember { ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = CareModel(activity.application, cloud.takeIf { session != null }) as T
        })[CareModel::class.java] }
        SideEffect { activity.careModel = model }
        var accountOpen by rememberSaveable { mutableStateOf(false) }
        if (patientWalkthroughUser != null) PatientWalkthroughHost(patientWalkthroughUser!!, onExit = { patientWalkthroughUser = null })
        else if (demoOpen) DoctorDemo(onExit = { demoOpen = false; walkthroughPatient = null }, cloud = cloud, initialPatientId = walkthroughPatient, careModel = model, onCareAccount = { accountOpen = true })
        else if (doctorOpen && session != null) ConnectedDoctorWorkspace(cloud, model, onExit = { doctorOpen = false }, onAccount = { accountOpen = true }, onWalkthrough = { walkthroughPatient = it; demoOpen = true })
        else CareApp(model, onAccount = { accountOpen = true }, onDoctorDemo = {
            if (model.recording.value) model.message.value = "Stop the recording before opening the doctor demo."
            else { walkthroughPatient = null; demoOpen = true }
        }, onPatientWalkthrough = { if (model.recording.value) model.message.value = "Stop the recording before opening the walkthrough." else patientWalkthroughUser = it },
            onDoctorWorkspace = { doctorEntryPending = true; if (session != null) { doctorOpen = true; doctorEntryPending = false } else accountOpen = true },
            welcomeAccountContent = { close, onBusy -> AccountContent(cloud, model, close, onBusyChange = onBusy) },
            welcomeDoctorContent = { close, onBusy ->
                if (session == null) AccountContent(cloud, model, close, heading = "Doctor sign in", onBusyChange = onBusy)
                else Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Heading("Your doctor workspace", 26)
                    Text(session!!.email)
                    Text("Use your own account. Professional verification and patient access are separate.")
                    Button(onClick = { doctorOpen = true; doctorEntryPending = false }, modifier = Modifier.fillMaxWidth()) { Text("Open workspace") }
                }
            }, onWelcomePanelChanged = { doctorEntryPending = it == "doctor" })
        if (accountOpen) AccountSheet(cloud, model, onDismiss = { accountOpen = false; if (cloud.session.value == null) doctorEntryPending = false })
    }
}

/** Account authentication only. Never grants clinical verification or loads patient records. */
@Composable fun DoctorAiAccountSheet(cloud:CareCloud,onDismiss:()->Unit) {
    val session by cloud.session.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") };var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest={if(!busy)onDismiss()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=Cream) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Heading("Sahara AI sign-in",26)
            Text("Use your real Sahara account for authenticated online requests. This does not verify you as a doctor or grant access to any patient.")
            if(session!=null) { Text("Account connected",color=Rust);TextButton(onClick=onDismiss) { Text("Return to conversation") } }
            else {
                OutlinedTextField(email,{email=it},singleLine=true,label={Text("Sahara account email")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(password,{password=it},singleLine=true,label={Text("Password")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),modifier=Modifier.fillMaxWidth())
                Button(onClick={if(!busy){busy=true;error=null;scope.launch {try {cloud.signIn(email,password);password="";onDismiss()}catch(e:Exception){if(e is CancellationException)throw e;error="Sign-in did not finish. Check your email, password and confirmation status."}finally{busy=false}}}},enabled=!busy && email.contains('@') && password.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(busy) "Signing in…" else "Sign in") }
                Text("The prefilled doctor preview credentials are not a real account. To create an account, return to Sahara → Your account.",style=MaterialTheme.typography.bodySmall)
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            TextButton(onClick=onDismiss,enabled=!busy) {Text("Close")}
        }
    }
}

@Composable fun AccountSheet(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        AccountContent(cloud, vm, onDismiss, onBusyChange = { busy = it })
    }
}

/** The same genuine account form, reusable inside the welcome panel. */
@Composable fun AccountContent(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, heading: String? = null, onBusyChange: (Boolean) -> Unit = {}) {
    val session by cloud.session.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var create by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf("") }
    var allowChanges by rememberSaveable { mutableStateOf(false) }
    var allowCareTeam by rememberSaveable(selected) { mutableStateOf(false) }
    var invite by rememberSaveable(selected) { mutableStateOf("") }
    var inviteDoctor by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorEdit by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorNotes by rememberSaveable(selected) { mutableStateOf(true) }
    var doctorCalendar by rememberSaveable(selected) { mutableStateOf(true) }
    var doctorReports by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorCosts by rememberSaveable(selected) { mutableStateOf(false) }
    var doctorDays by rememberSaveable(selected) { mutableStateOf(30) }
    var members by remember(selected) { mutableStateOf<List<CareMember>>(emptyList()) }
    var removing by remember { mutableStateOf<CareMember?>(null) }
    var signingOut by remember { mutableStateOf(false) }
    SideEffect { onBusyChange(busy) }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; notice = null
        scope.launch {
            try { action() } catch (e: Exception) { if (e is CancellationException) throw e; error = e.message ?: "Couldn't finish. Please try again." }
            finally { busy = false }
        }
    }
    LaunchedEffect(selected, owner) {
        if (session != null && owner && selected.isNotBlank()) try { members = cloud.members(selected) }
        catch (e: Exception) { if (e is CancellationException) throw e; error = "Couldn't load the people you've invited." }
    }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 22.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Heading(heading ?: if (session == null) if (create) "Create an account" else "Sign in" else "Your account", 28)
            if (session == null) {
                Text("Sign in to share care notes and daily care with people you invite.")
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (create) Text("Use at least 10 characters.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { run {
                    if (create) { if (!cloud.signUp(email, password)) { notice = "Check your email to confirm your account, then sign in."; create = false; password = "" } }
                    else cloud.signIn(email, password)
                } }, enabled = !busy && email.contains('@') && password.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (busy) "Please wait…" else if (create) "Create account" else "Sign in") }
                if (!create) TextButton(onClick = { run {
                    cloud.sendPasswordReset(email)
                    notice = "If that email has an account, you'll receive a reset link."
                } }, enabled = !busy && email.contains('@') && CareEmailAvailability.ready) { Text("Forgot password?") }
                TextButton(onClick = { create = !create; error = null; notice = null }, enabled = !busy && (create || CareEmailAvailability.ready)) { Text(if (create) "Already have an account? Sign in" else "Create an account") }
                if (!CareEmailAvailability.ready) Text("New accounts and password reset are temporarily unavailable. Existing confirmed accounts can sign in.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(session!!.email)
                OutlinedButton(onClick = { vm.refresh(); onDismiss() }, enabled = !busy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Refresh records") }
                DetailPanel("Join someone's care", Icons.Default.GroupAdd) {
                    Text("The person who invited you will see your email.", style = MaterialTheme.typography.bodySmall)
                    CareField(code, { code = it }, "Invitation code", singleLine = true)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { run { cloud.join(code); vm.refresh(); code = ""; notice = "Joined. Their records will appear in your list." } }, enabled = !busy && code.trim().length == 48) { Text("Join") }
                }
                if (owner && selected.isNotBlank()) DetailPanel("Share ${people.firstOrNull { it.id == selected }?.name.orEmpty()}'s care", Icons.Default.PersonAdd) {
                    Text("Who are you inviting?", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !inviteDoctor, onClick = { inviteDoctor = false; invite = "" }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("Family") })
                        FilterChip(selected = inviteDoctor, onClick = { inviteDoctor = true; invite = "" }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("Doctor") })
                    }
                    if (!inviteDoctor) {
                        Text("Invited people can read the care notes and daily entries. Recordings and unfinished conversations aren't shared.")
                        CheckLine(allowChanges, { allowChanges = it; invite = "" }, "Allow them to add and change records")
                        CheckLine(allowCareTeam, { allowCareTeam = it; invite = "" }, "Share family and care-team contacts")
                    } else {
                        Text("This creates a one-person code that you share yourself. Nothing is sent to the doctor, and creating it does not verify them.", style = MaterialTheme.typography.bodySmall)
                        Text("A doctor can only join with this code after their clinician verification has been approved. Until then, joining with it will fail with a verification message.", style = MaterialTheme.typography.bodySmall)
                        Text("What they can open", style = MaterialTheme.typography.bodyMedium)
                        CheckLine(doctorNotes, { doctorNotes = it; invite = "" }, "Care notes and conversations")
                        CheckLine(doctorCalendar, { doctorCalendar = it; invite = "" }, "Calendar, medicines and tasks")
                        CheckLine(doctorReports, { doctorReports = it; invite = "" }, "Report-derived records")
                        CheckLine(doctorCosts, { doctorCosts = it; invite = "" }, "Costs")
                        CheckLine(doctorEdit, { doctorEdit = it; invite = "" }, "Allow them to add and change records (otherwise read-only)")
                        Text("Access lasts", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(1, 7, 30, 90).forEach { d -> FilterChip(selected = doctorDays == d, onClick = { doctorDays = d; invite = "" }, modifier = Modifier.heightIn(min = 48.dp), label = { Text("$d days") }) } }
                    }
                    Button(onClick = { val person = selected; val account = session!!.userId; val doctor = inviteDoctor; run { val code = if (doctor) cloud.inviteAccess(person, "clinician", doctorEdit, mapOf("careNote" to doctorNotes, "calendar" to doctorCalendar, "reports" to doctorReports, "costs" to doctorCosts), doctorDays, expectedAccountId = account) else cloud.invite(person, allowChanges, allowCareTeam); if (vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value) invite = code } }, enabled = !busy && (!inviteDoctor || doctorNotes || doctorCalendar || doctorReports || doctorCosts), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (inviteDoctor) "Create doctor invitation" else "Create invitation") }
                    if (invite.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp)); Text(if (inviteDoctor) "One person · the code expires in 3 days · works only for an approved clinician account" else "One person · expires in 3 days", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_care_invitation, context.getString(R.string.app_name), invite)), "Share invitation")) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Share invitation") }
                        androidx.compose.foundation.text.selection.SelectionContainer { Text(invite, style = MaterialTheme.typography.bodySmall) }
                    }
                    members.forEach { member ->
                        Row(Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f).padding(top = 12.dp)) { Text(member.email); Text(if (member.canEdit) "Can make changes" else "Can read", style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { removing = member }, enabled = !busy) { Text("Remove") }
                        }
                        CheckLine(member.careTeamRead, { enabled -> val person = selected; val account = session!!.userId; run { require(vm.selected.value == person && cloud.session.value?.userId == account && vm.isOwner.value); cloud.setCareTeamSharing(person, member.userId, enabled); if (vm.selected.value == person && cloud.session.value?.userId == account) members = cloud.members(person) } }, "Share care-team contacts with this person")
                        if (!member.careTeamPermissionKnown) Text("Care-team sharing is unavailable until the server update is installed.", style = MaterialTheme.typography.bodySmall)
                    }
                    CareContactManager(vm, selected)
                }
                TextButton(onClick = { signingOut = true }, enabled = !busy) { Text("Sign out") }
                TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.delete_account_url)))) }, enabled = !busy) { Text("Delete account") }
            }
            TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_url)))) }) { Text("Privacy policy") }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    removing?.let { member -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Stop sharing with this person?") }, text = { Text("They won't be able to read or change these records online. Copies they've already saved cannot be recalled.") }, confirmButton = { TextButton(onClick = { removing = null; run { cloud.removeMember(selected, member.userId); members = cloud.members(selected) } }) { Text("Stop sharing") } }, dismissButton = { TextButton(onClick = { removing = null }) { Text("Keep sharing") } }) }
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text("Sign out?") }, confirmButton = { TextButton(onClick = { signingOut = false; run { vm.stopRecording(); cloud.signOut() } }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text("Cancel") } })
}
