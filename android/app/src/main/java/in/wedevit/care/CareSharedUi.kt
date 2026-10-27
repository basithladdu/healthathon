@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Real account records only. The separate doctor walkthrough is unchanged. */
@Composable fun CareSharedWorkspace(vm: CareModel, onAccount: () -> Unit, onClose: () -> Unit, embedded: Boolean = false, onBusyChange: (Boolean) -> Unit = {}) {
    val cloud = vm.cloud
    val selected by vm.selected.collectAsStateWithLifecycle()
    val owner by vm.isOwner.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<CareClinicianProfile?>(null) }
    var drafts by remember(selected) { mutableStateOf<List<SharedCareDraft>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    SideEffect { onBusyChange(busy) }
    DisposableEffect(Unit) { onDispose { onBusyChange(false) } }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var council by rememberSaveable { mutableStateOf("") }
    var registration by rememberSaveable { mutableStateOf("") }
    var reading by remember(selected) { mutableStateOf<SharedCareDraft?>(null) }
    val currentPerson by rememberUpdatedState(selected)
    val currentCloud by rememberUpdatedState(cloud)
    var actionJob by remember { mutableStateOf<Job?>(null) }
    var epoch by remember { mutableIntStateOf(0) }
    suspend fun refresh() {
        if (cloud == null) return
        val capturedPerson = selected; val capturedUser = cloud.session.value?.userId; val capturedEpoch = epoch
        val nextProfile = cloud.clinicianProfile()
        val nextDrafts = if (capturedPerson.isNotBlank()) cloud.sharedDrafts(capturedPerson) else emptyList()
        if (currentPerson != capturedPerson || currentCloud !== cloud || cloud.session.value?.userId != capturedUser || capturedEpoch != epoch) return
        profile = nextProfile; drafts = nextDrafts
        if (name.isEmpty()) { name = nextProfile?.name.orEmpty(); council = nextProfile?.council.orEmpty(); registration = nextProfile?.registration.orEmpty() }
    }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""; notice = ""
        val capturedPerson = selected; val capturedUser = cloud?.session?.value?.userId; val capturedEpoch = epoch
        actionJob = scope.launch { try { action() } catch (e: Exception) { if (e is CancellationException) throw e; if (currentPerson == capturedPerson && currentCloud?.session?.value?.userId == capturedUser && capturedEpoch == epoch) error = e.message ?: "Couldn't finish. Your source is unchanged." } finally { if (capturedEpoch == epoch) busy = false } }
    }
    LaunchedEffect(cloud, selected, cloud?.session?.value?.userId) {
        epoch++; actionJob?.cancel(); actionJob = null; drafts = emptyList(); reading = null; busy = false; error = ""; notice = ""
        if (cloud != null) run { refresh() }
    }
    DisposableEffect(cloud) { onDispose { actionJob?.cancel() } }
    BackHandler { if (!busy) onClose() }
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cloud == null) item { SurfaceCard { Heading("Sign in to your records", 25); Text("Use an existing confirmed Sahara account. A selected doctor role does not verify your identity."); Button(onClick = onAccount) { Text("Your account") } } }
            else {
                item { Text(people.firstOrNull { it.id == selected }?.name.orEmpty(), style = MaterialTheme.typography.titleLarge); OutlinedButton(onClick = { run { refresh(); vm.refresh() } }, enabled = !busy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Refresh shared records") } }
                if (error.isNotBlank()) item { SurfaceCard(Peach) { Text(error); TextButton(onClick = { run { refresh() } }, enabled = !busy) { Text("Retry") } } }
                if (notice.isNotBlank()) item { Text(notice, color = Rust) }
                item { SurfaceCard(Mint) {
                    Text("Clinician verification", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(when (profile?.status) { "approved" -> "Registration reviewed. Patient invitation is also required before clinical review."; "pending" -> "Request received. An independent verifier must check your council registration."; "rejected", "suspended" -> "${profile?.status}: ${profile?.note}"; else -> "Your account is not verified as a clinician." })
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CareField(name, { name = it.take(100) }, "Registered name", singleLine = true)
                        CareField(council, { council = it.take(160) }, "Medical council", singleLine = true)
                        CareField(registration, { registration = it.take(80) }, "Registration number", singleLine = true)
                        if (profile?.status == "approved") Text("Changing these details suspends verified status until a new independent review.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { run { cloud.requestClinician(name, council, registration); refresh(); notice = "Request received. Verification is pending." } }, enabled = !busy && name.isNotBlank() && council.length >= 2 && registration.length >= 2) { Text("Submit for verification") }
                    }
                } }
                item { SurfaceCard(Gold) {
                    Text("Patient-controlled access", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(if (owner) "Invite a separately verified clinician or chosen family account, choose access and expiry, and cancel or revoke access online." else "The patient record owner controls your access. A display-role change cannot increase your permissions.")
                    OutlinedButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://sahara.wedevit.in/shared-care?person=$selected"))) }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open shared care, tasks and progress") }
                    Text("Sign in with the same Sahara account on the website. Links never grant public access.", style = MaterialTheme.typography.bodySmall)
                } }
                item { Heading("Shared conversations", 26) }
                if (drafts.isEmpty()) item { SurfaceCard { Text("No shared conversation yet"); Text("Capture the original words in Conversation, review the sections and send them for clinician review.", style = MaterialTheme.typography.bodySmall) } }
                items(drafts, key = { it.id }) { draft -> SurfaceCard {
                    Text(when (draft.status) { "submitted" -> "Waiting for clinician review"; "reviewed" -> "Reviewed conversation"; else -> "Account draft" }, fontWeight = FontWeight.Bold)
                    Text("Revision ${draft.revision}", style = MaterialTheme.typography.bodySmall)
                    Text(draft.conversation.source.take(180) + if (draft.conversation.source.length > 180) "…" else "")
                    TextButton(onClick = { reading = draft }) { Text("Open conversation") }
                } }
            }
            item { Text("Clinical review is a separate step by a verified, invited account. It records a discussion; it does not execute a legal advance directive or authorise treatment.", style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (embedded) content(PaddingValues(0.dp))
    else Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text("Shared clinician workspace") }, navigationIcon = { IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, content = content)
    reading?.let { draft ->
        var confirmed by remember(draft.id, draft.revision) { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { if (!busy) reading = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
            LazyColumn(Modifier.imePadding().fillMaxWidth(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Heading("Review conversation", 26); Text("Revision ${draft.revision} · ${draft.status}", style = MaterialTheme.typography.bodySmall) }
                item { SurfaceCard(Gold) { Text("Original words", fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); SelectionContainerText(draft.conversation.source) } }
                items(NoteRules.labels.zip(NoteRules.values(draft.conversation)), key = { it.first }) { (label, value) -> SurfaceCard { Text(label, fontWeight = FontWeight.Bold); SelectionContainerText(value.ifBlank { "Not recorded" }); CareSourceLinks(draft.conversation.source, value) } }
                if (profile?.status == "approved" && draft.status == "submitted" && !owner && cloud != null) {
                    item { CheckLine(confirmed, { confirmed = it }, "I checked this exact source and every section as the patient's invited clinician.") }
                    item { Button(onClick = { run { cloud.reviewSharedDraft(draft); reading = null; refresh(); vm.refresh(); notice = "Reviewed care-note version published. The patient reviews this exact version next." } }, enabled = confirmed && !busy, modifier = Modifier.fillMaxWidth()) { Text("Publish reviewed care note") } }
                }
                if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error) }
                item { TextButton(onClick = { reading = null }, enabled = !busy) { Text("Close") } }
            }
        }
    }
}
