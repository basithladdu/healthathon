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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

/** Shared only by the visible auth entries. Credentials never enter saved state or disk. */
@Stable class CareAuthDraft {
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var creatingAccount by mutableStateOf(false)
    fun clear() { email = ""; password = ""; creatingAccount = false }
}

@Composable fun CareRoot(activity: MainActivity) {
    val root = remember { ViewModelProvider(activity)[CareRootModel::class.java] }
    val cloud = root.cloud
    var demoOpen by rememberSaveable { mutableStateOf(false) }
    var walkthroughPatient by rememberSaveable { mutableStateOf<String?>(null) }
    var patientWalkthroughUser by rememberSaveable { mutableStateOf<String?>(null) }
    var doctorOpen by rememberSaveable { mutableStateOf(false) }
    var doctorEntryPending by rememberSaveable { mutableStateOf(false) }
    var authNotice by remember { mutableStateOf<String?>(null) }
    val welcomeAuthDraft = remember { CareAuthDraft() }
    val session by cloud.session.collectAsStateWithLifecycle()
    LaunchedEffect(session?.userId) { welcomeAuthDraft.clear() }
    DisposableEffect(welcomeAuthDraft) { onDispose { welcomeAuthDraft.clear() } }
    LaunchedEffect(session?.userId, doctorEntryPending) {
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
        val feedback by model.feedback.collectAsStateWithLifecycle()
        val legacyMessage by model.message.collectAsStateWithLifecycle()
        val selectedCare by model.selected.collectAsStateWithLifecycle()
        val snackbars = remember { SnackbarHostState() }
        LaunchedEffect(legacyMessage) { legacyMessage?.let { model.notifyNotice(it); model.message.value = null } }
        LaunchedEffect(session?.userId, authNotice) {
            if (session != null) authNotice?.let { model.notifySaved(it); authNotice = null }
        }
        Box(Modifier.fillMaxSize()) {
        if (patientWalkthroughUser != null) PatientWalkthroughHost(patientWalkthroughUser!!, onExit = { patientWalkthroughUser = null })
        else if (demoOpen) DoctorDemo(onExit = { demoOpen = false; walkthroughPatient = null }, cloud = cloud, initialPatientId = walkthroughPatient, careModel = model, onCareAccount = { accountOpen = true })
        else if (doctorOpen && session != null) ConnectedDoctorWorkspace(cloud, model, onExit = { doctorOpen = false }, onAccount = { accountOpen = true }, onWalkthrough = { walkthroughPatient = it; demoOpen = true })
        else CareApp(model, onAccount = { accountOpen = true }, onDoctorDemo = {
            if (model.recording.value) model.message.value = "Stop the recording before opening the doctor demo."
            else { walkthroughPatient = null; demoOpen = true }
        }, onPatientWalkthrough = { if (model.recording.value) model.message.value = "Stop the recording before opening the walkthrough." else patientWalkthroughUser = it },
            onDoctorWorkspace = { doctorEntryPending = true; if (session != null) { doctorOpen = true; doctorEntryPending = false } else accountOpen = true },
            welcomeAccountContent = { close, onBusy -> AccountContent(cloud, model, close, entryRole = "Patient", showClose = false, onBusyChange = onBusy, onAuthenticated = { authNotice = "Signed in." }, authDraft = welcomeAuthDraft) },
            welcomeDoctorContent = { close, onBusy ->
                if (session == null) AccountContent(cloud, model, close, entryRole = "Doctor", showClose = false, onBusyChange = onBusy, onAuthenticated = { authNotice = "Signed in." }, authDraft = welcomeAuthDraft)
                else Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Heading("Doctor", 26)
                    Text(session!!.email)
                    Text("Doctor verification and patient access are separate.")
                    Button(onClick = { doctorOpen = true; doctorEntryPending = false }, modifier = Modifier.fillMaxWidth()) { Text("Open care team") }
                }
            }, onWelcomePanelChanged = { doctorEntryPending = it == "doctor"; if (it == "account") model.role("Patient") }, feedbackHandledByParent = true)
        if (accountOpen) AccountSheet(cloud, model, onDismiss = { accountOpen = false; if (cloud.session.value == null) doctorEntryPending = false }, entryRole = if (doctorEntryPending) "Doctor" else "Patient", onAuthenticated = { authNotice = "Signed in." })
        CareSnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = if (selectedCare.isNotBlank()) 72.dp else 8.dp))
        CareFeedbackPresenter(feedback, snackbars, model::dismissFeedback)
        }
    }
}

/** Account authentication only. Never grants clinical verification or loads patient records. */
@Composable fun DoctorAiAccountSheet(cloud: CareCloud, onDismiss: () -> Unit) {
    val session by cloud.session.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (session == null) {
                AccountAuthForm(cloud, onDismiss, entryRole = "Doctor", onBusyChange = { busy = it }, onAuthenticated = onDismiss)
                Text("Walkthrough sign-in details do not work here. Signing in does not verify a doctor or grant access to any patient.", style = MaterialTheme.typography.bodySmall)
            } else {
                AccountHeader("Account", onDismiss, busy = false, entryRole = "Doctor")
                AccountFeedback("Signed in", session!!.email)
                Text("Doctor verification and patient access are separate.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Return to conversation") }
            }
        }
    }
}

@Composable fun AccountSheet(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, entryRole: String? = null, onAuthenticated: () -> Unit = {}) {
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
        AccountContent(cloud, vm, onDismiss, entryRole = entryRole, onBusyChange = { busy = it }, onAuthenticated = onAuthenticated)
    }
}

@Composable private fun AccountHeader(title: String, onClose: () -> Unit, busy: Boolean, entryRole: String? = null, showClose: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            entryRole?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Heading(title, 28)
        }
        if (showClose) IconButton(onClick = onClose, enabled = !busy, modifier = Modifier.size(48.dp).testTag("account-close")) { Icon(Icons.Default.Close, "Close account") }
    }
}

@Composable private fun AccountFeedback(title: String, message: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.testTag(if (error) "account-error" else "account-notice")) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (error) Icons.Default.ErrorOutline else Icons.Default.CheckCircleOutline, null, Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal fun careAccountCanSubmit(create: Boolean, email: String, password: String, emailReady: Boolean): Boolean =
    email.trim().contains('@') && if (create) emailReady && password.length >= 10 else password.isNotEmpty()

private data class PendingCareMemberRemoval(val personId: String, val accountId: String, val member: CareMember)

internal fun careMemberRemovalContextMatches(expectedAccount: String, expectedPerson: String, currentAccount: String?, modelAccount: String,
    currentPerson: String, owner: Boolean, memberStillPresent: Boolean): Boolean =
    expectedAccount.isNotBlank() && expectedPerson.isNotBlank() && expectedAccount == currentAccount && expectedAccount == modelAccount &&
        expectedPerson == currentPerson && owner && memberStillPresent

/** One form for patient/doctor entry. A role label never grants verification or access. */
@Composable private fun AccountAuthForm(cloud: CareCloud, onClose: () -> Unit, entryRole: String?, onBusyChange: (Boolean) -> Unit, onAuthenticated: () -> Unit, showClose: Boolean = true, authDraft: CareAuthDraft? = null) {
    val scope = rememberCoroutineScope()
    val localDraft = remember { CareAuthDraft() }
    val draft = authDraft ?: localDraft
    val email = draft.email
    val password = draft.password
    val create = draft.creatingAccount
    var showPassword by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var lastAction by remember { mutableStateOf("sign-in") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var failure by remember { mutableStateOf<CareFeedback?>(null) }
    val busy = pending != null
    val latestBusyChange by rememberUpdatedState(onBusyChange)
    SideEffect { latestBusyChange(busy) }
    DisposableEffect(Unit) { onDispose { latestBusyChange(false) } }
    fun submit(action: String, block: suspend () -> Unit) {
        if (pending != null) return
        pending = action; lastAction = action; error = null; notice = null; failure = null
        latestBusyChange(true)
        scope.launch {
            try { block() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                val message = e.message ?: "Check your connection and try again. Your email is still here."
                error = message
                failure = CareFeedback(kind = CareFeedbackKind.ERROR, title = accountFailureTitle(action), message = message)
            }
            finally { pending = null; latestBusyChange(false) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AccountHeader(if (create) "Create account" else "Sign in", onClose, busy, entryRole, showClose)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !create, onClick = { draft.creatingAccount = false; error = null; notice = null; failure = null }, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag("account-mode-sign-in"), label = { Text("Sign in") })
            FilterChip(selected = create, onClick = { draft.creatingAccount = true; error = null; notice = null; failure = null }, enabled = !busy && CareEmailAvailability.ready, modifier = Modifier.heightIn(min = 48.dp).testTag("account-mode-create"), label = { Text("Create account") })
        }
        error?.let { AccountFeedback(accountFailureTitle(lastAction), it, error = true) }
        notice?.let { AccountFeedback("Check your email", it) }
        OutlinedTextField(email, { draft.email = it }, enabled = !busy, label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth().testTag("account-email"))
        OutlinedTextField(password, { draft.password = it }, enabled = !busy, label = { Text("Password") }, singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }, enabled = !busy) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showPassword) "Hide password" else "Show password") } },
            supportingText = if (create) ({ Text("At least 10 characters") }) else null,
            modifier = Modifier.fillMaxWidth().testTag("account-password"))
        Button(onClick = { submit(if (create) "create" else "sign-in") {
            if (create) {
                check(CareEmailAvailability.ready) { "New accounts are temporarily unavailable. Existing accounts can sign in." }
                if (!cloud.signUp(email, password)) {
                    notice = "Confirm your account using the link in your email, then sign in."; draft.creatingAccount = false; draft.password = ""
                } else { check(cloud.session.value != null) { "Your account was created, but sign-in was not confirmed. Please sign in." }; draft.clear(); onAuthenticated() }
            } else { cloud.signIn(email, password); check(cloud.session.value != null) { "Sign-in was not confirmed. Please try again." }; draft.clear(); onAuthenticated() }
        } }, enabled = !busy && careAccountCanSubmit(create, email, password, CareEmailAvailability.ready), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("account-submit")) {
            if (busy) { CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)) }
            Text(when (pending) { "reset" -> "Sending reset link…"; "create" -> "Creating account…"; "sign-in" -> "Signing in…"; else -> if (create) "Create account" else "Sign in" })
        }
        if (!create) TextButton(onClick = { submit("reset") {
            check(CareEmailAvailability.ready) { "Password reset is temporarily unavailable." }
            cloud.sendPasswordReset(email); notice = "If this email has an account, you'll receive a password reset link."
        } }, enabled = !busy && email.trim().contains('@') && CareEmailAvailability.ready, modifier = Modifier.heightIn(min = 48.dp).testTag("account-forgot-password")) { Text("Forgot password?") }
        if (!CareEmailAvailability.ready) Text("New accounts and password reset are temporarily unavailable. Existing confirmed accounts can sign in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    failure?.let { CareResultDialog(it) { failure = null } }
}

private fun accountFailureTitle(action: String): String = when (action) {
    "reset" -> "Couldn't send the reset link"
    "create" -> "Couldn't create your account"
    else -> "Couldn't sign in"
}

/** The same genuine account form, reusable inside the welcome panel. */
@Composable fun AccountContent(cloud: CareCloud, vm: CareModel, onDismiss: () -> Unit, heading: String? = null, onBusyChange: (Boolean) -> Unit = {}, entryRole: String? = null, onAuthenticated: () -> Unit = {}, showClose: Boolean = true, authDraft: CareAuthDraft? = null) {
    val session by cloud.session.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var authBusy by remember { mutableStateOf(false) }
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
    var removing by remember { mutableStateOf<PendingCareMemberRemoval?>(null) }
    var signingOut by remember { mutableStateOf(false) }
    val latestBusyChange by rememberUpdatedState(onBusyChange)
    SideEffect { latestBusyChange(busy || authBusy) }
    DisposableEffect(Unit) { onDispose { latestBusyChange(false) } }
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
            if (session == null) {
                AccountAuthForm(cloud, onDismiss, entryRole, onBusyChange = { authBusy = it; latestBusyChange(busy || it) }, onAuthenticated = onAuthenticated, showClose = showClose, authDraft = authDraft)
            } else {
                AccountHeader(heading ?: "Account", onDismiss, busy, entryRole, showClose)
                AccountFeedback("Signed in", session!!.email)
                error?.let { AccountFeedback("Couldn't finish", it, error = true) }
                notice?.let { AccountFeedback("Done", it) }
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
                            TextButton(onClick = { session?.userId?.let { account -> removing = PendingCareMemberRemoval(selected, account, member) } }, enabled = !busy) { Text("Remove") }
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
        }
    removing?.let { pending ->
        fun sameProfile() = careMemberRemovalContextMatches(pending.accountId, pending.personId, cloud.session.value?.userId,
            vm.accountNamespace, vm.selected.value, vm.isOwner.value, memberStillPresent = true)
        fun sameContext() = sameProfile() && members.any { it.userId == pending.member.userId }
        val contextMatches = careMemberRemovalContextMatches(pending.accountId, pending.personId, session?.userId,
            vm.accountNamespace, selected, owner, members.any { it.userId == pending.member.userId })
        AlertDialog(onDismissRequest = { if (!busy) removing = null }, title = { Text("Stop sharing with ${pending.member.email}?") },
            text = { Text(if (contextMatches) "They won't be able to read or change these records online. Copies they've already saved cannot be recalled." else "The care profile or account changed. Close this dialog and reopen the right person's sharing settings.") },
            confirmButton = { TextButton(onClick = { run {
                try {
                    check(sameContext()) { "The care profile or account changed." }
                    // Existing transport protects the captured account again after token refresh; no server/grant changes.
                    cloud.request("DELETE", "/rest/v1/healthathon_members?person_id=eq.${pending.personId}&user_id=eq.${pending.member.userId}", expectedAccountId = pending.accountId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    removing = null
                    val message = "Couldn't confirm sharing was stopped. Reopen the original care profile's sharing settings and refresh the list before trying again."
                    if (sameProfile()) error = message
                    vm.notifyError(message, "Sharing not confirmed")
                    return@run
                }
                // The server accepted removal. A later list refresh must not turn it into a failed deletion.
                removing = null
                if (sameProfile()) {
                    members = members.filterNot { it.userId == pending.member.userId }
                    notice = "Sharing stopped for this person."
                    vm.notifyCompleted("Sharing stopped", "This person's care is no longer shared with this account. Copies already saved cannot be recalled.")
                    try {
                        val updated = cloud.members(pending.personId)
                        if (sameProfile()) members = updated
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        if (sameProfile()) notice = "Sharing stopped. Couldn't refresh the remaining invitations. Close and reopen Account to refresh them."
                    }
                }
            } }, enabled = !busy && contextMatches) { Text("Stop sharing") } },
            dismissButton = { TextButton(onClick = { removing = null }, enabled = !busy) { Text("Keep sharing") } })
    }
    if (signingOut) AlertDialog(onDismissRequest = { signingOut = false }, title = { Text("Sign out?") }, confirmButton = { TextButton(onClick = { signingOut = false; run { vm.stopRecording(); cloud.signOut() } }) { Text("Sign out") } }, dismissButton = { TextButton(onClick = { signingOut = false }) { Text("Cancel") } })
}
