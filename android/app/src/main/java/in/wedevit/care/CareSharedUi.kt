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
@Composable fun CareSharedWorkspace(vm: CareModel, onAccount: () -> Unit, onClose: () -> Unit, embedded: Boolean = false, onBusyChange: (Boolean) -> Unit = {}) { val t = careText();
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
        actionJob = scope.launch { try { action() } catch (e: Exception) { if (e is CancellationException) throw e; if (currentPerson == capturedPerson && currentCloud?.session?.value?.userId == capturedUser && capturedEpoch == epoch) error = e.message ?: t("Couldn't finish. Your source is unchanged.", "पूरा नहीं हो सका। आपका मूल वैसा ही है।") } finally { if (capturedEpoch == epoch) busy = false } }
    }
    LaunchedEffect(cloud, selected, cloud?.session?.value?.userId) {
        epoch++; actionJob?.cancel(); actionJob = null; drafts = emptyList(); reading = null; busy = false; error = ""; notice = ""
        if (cloud != null) run { refresh() }
    }
    DisposableEffect(cloud) { onDispose { actionJob?.cancel() } }
    // Doctor approval is offered only to accounts the owner made Sahara-team verifiers on the server.
    var verifier by remember { mutableStateOf(false) }
    LaunchedEffect(cloud, cloud?.session?.value?.userId) { verifier = try { cloud?.session?.value != null && cloud.isClinicalVerifier() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { false } }
    BackHandler { if (!busy) onClose() }
    val content: @Composable (PaddingValues) -> Unit = { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cloud == null) item { SurfaceCard { Heading(t("Sign in to your records", "अपने रिकॉर्ड के लिए साइन इन करें"), 25); Text(t("Use an existing confirmed Sahara account. A selected doctor role does not verify your identity.", "पहले से पुष्ट सहारा खाता इस्तेमाल करें। डॉक्टर की भूमिका चुनने से आपकी पहचान पक्की नहीं होती।")); Button(onClick = onAccount) { Text(t("Your account", "आपका खाता")) } } }
            else {
                item { Text(people.firstOrNull { it.id == selected }?.name.orEmpty(), style = MaterialTheme.typography.titleLarge); OutlinedButton(onClick = { run { refresh(); vm.refresh() } }, enabled = !busy) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text(t("Refresh shared records", "साझा रिकॉर्ड ताज़ा करें")) } }
                if (error.isNotBlank()) item { SurfaceCard(Peach) { Text(error, Modifier.announce()); TextButton(onClick = { run { refresh() } }, enabled = !busy) { Text(t("Retry", "फिर कोशिश करें"), color = RustText) } } }
                if (notice.isNotBlank()) item { Text(notice, color = Rust, modifier = Modifier.announce()) }
                item { SurfaceCard(Mint) {
                    Text(t("Clinician verification", "डॉक्टर की पुष्टि"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(when (profile?.status) { "approved" -> t("Registration reviewed. Patient invitation is also required before clinical review.", "रजिस्ट्रेशन जाँचा गया। क्लिनिकल समीक्षा से पहले मरीज़ का निमंत्रण भी ज़रूरी है।"); "pending" -> t("Request received. An independent verifier must check your council registration.", "अनुरोध मिल गया। कोई स्वतंत्र जाँचकर्ता आपका काउंसिल रजिस्ट्रेशन जाँचेगा।"); "rejected", "suspended" -> "${profile?.status}: ${profile?.note}"; else -> t("Your account is not verified as a clinician.", "आपका खाता डॉक्टर के रूप में पुष्ट नहीं है।") })
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CareField(name, { name = it.take(100) }, t("Registered name", "रजिस्टर्ड नाम"), singleLine = true)
                        CareField(council, { council = it.take(160) }, t("Medical council", "मेडिकल काउंसिल"), singleLine = true)
                        CareField(registration, { registration = it.take(80) }, t("Registration number", "रजिस्ट्रेशन नंबर"), singleLine = true)
                        if (profile?.status == "approved") Text(t("Changing these details suspends verified status until a new independent review.", "यह जानकारी बदलने पर नई स्वतंत्र जाँच तक पुष्टि रुक जाती है।"), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { run { cloud.requestClinician(name, council, registration); refresh(); notice = t("Request received. Verification is pending.", "अनुरोध मिल गया। पुष्टि बाक़ी है।") } }, enabled = !busy && name.isNotBlank() && council.length >= 2 && registration.length >= 2) { Text(t("Submit for verification", "पुष्टि के लिए भेजें")) }
                    }
                } }
                if (verifier) item { CareClinicianReviewPanel(cloud) }
                item { SurfaceCard(Gold) {
                    Text(t("Patient-controlled access", "मरीज़ के हाथ में पहुँच"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont)
                    Text(if (owner) t("Invite a separately verified clinician or chosen family account, choose access and expiry, and cancel or revoke access online.", "अलग से पुष्ट डॉक्टर या चुने परिवार वाले खाते को बुलाएँ, पहुँच और समय-सीमा चुनें, और ऑनलाइन पहुँच रद्द या वापस लें।") else t("The patient record owner controls your access. A display-role change cannot increase your permissions.", "मरीज़ के रिकॉर्ड का मालिक आपकी पहुँच तय करता है। भूमिका बदलने से अनुमति नहीं बढ़ती।"))
                    OutlinedButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://sahara.wedevit.in/shared-care?person=$selected"))) }) { Icon(Icons.Default.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text(t("Open shared care, tasks and progress", "साझा देखभाल, काम और प्रगति खोलें")) }
                    Text(t("Sign in with the same Sahara account on the website. Links never grant public access.", "वेबसाइट पर इसी सहारा खाते से साइन इन करें। लिंक से सबको पहुँच नहीं मिलती।"), style = MaterialTheme.typography.bodySmall)
                } }
                item { Heading(t("Shared conversations", "साझा बातचीत"), 26) }
                if (drafts.isEmpty()) item { SurfaceCard { Text(t("No shared conversation yet", "अभी कोई साझा बातचीत नहीं")); Text(t("Capture the original words in Conversation, review the sections and send them for clinician review.", "बातचीत में मूल शब्द लिखें, हिस्से जाँचें और डॉक्टर की समीक्षा के लिए भेजें।"), style = MaterialTheme.typography.bodySmall) } }
                items(drafts, key = { it.id }) { draft -> SurfaceCard {
                    Text(when (draft.status) { "submitted" -> t("Waiting for clinician review", "डॉक्टर की समीक्षा का इंतज़ार"); "reviewed" -> t("Reviewed conversation", "समीक्षा की गई बातचीत"); else -> t("Account draft", "खाते का ड्राफ़्ट") }, fontWeight = FontWeight.Bold)
                    Text(t("Revision ${draft.revision}", "संशोधन ${draft.revision}"), style = MaterialTheme.typography.bodySmall)
                    Text(draft.conversation.source.take(180) + if (draft.conversation.source.length > 180) "…" else "")
                    TextButton(onClick = { reading = draft }) { Text(t("Open conversation", "बातचीत खोलें")) }
                } }
            }
            item { Text(t("Clinical review is a separate step by a verified, invited account. It records a discussion; it does not execute a legal advance directive or authorise treatment.", "क्लिनिकल समीक्षा पुष्ट और बुलाए गए खाते का अलग क़दम है। यह चर्चा दर्ज करती है; यह क़ानूनी एडवांस डायरेक्टिव या इलाज की अनुमति नहीं है।"), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (embedded) content(PaddingValues(0.dp))
    else Scaffold(containerColor = Cream, topBar = { TopAppBar(title = { Text(t("Shared clinician workspace", "साझा डॉक्टर कार्यक्षेत्र")) }, navigationIcon = { IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) } }, actions = { CareLanguageMenu(compact = true) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream)) }, content = content)
    reading?.let { draft ->
        var confirmed by remember(draft.id, draft.revision) { mutableStateOf(false) }
        ModalBottomSheet(onDismissRequest = { if (!busy) reading = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Cream) {
            LazyColumn(Modifier.imePadding().fillMaxWidth(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Heading(t("Review conversation", "बातचीत की समीक्षा"), 26); Text(t("Revision ${draft.revision}", "संशोधन ${draft.revision}") + " · ${draft.status}", style = MaterialTheme.typography.bodySmall) }
                item { SurfaceCard(Gold) { Text(t("Original words", "मूल शब्द"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); SelectionContainerText(draft.conversation.source) } }
                items(NoteRules.labels.zip(NoteRules.values(draft.conversation)), key = { it.first }) { (label, value) -> SurfaceCard { Text(label, fontWeight = FontWeight.Bold); SelectionContainerText(value.ifBlank { t("Not recorded", "दर्ज नहीं") }); CareSourceLinks(draft.conversation.source, value) } }
                if (profile?.status == "approved" && draft.status == "submitted" && !owner && cloud != null) {
                    item { CheckLine(confirmed, { confirmed = it }, t("I checked this exact source and every section as the patient's invited clinician.", "मरीज़ के बुलाए डॉक्टर के रूप में मैंने यही मूल और हर हिस्सा जाँचा है।")) }
                    item { Button(onClick = { run { cloud.reviewSharedDraft(draft); reading = null; refresh(); vm.refresh(); notice = t("Reviewed care-note version published. The patient reviews this exact version next.", "समीक्षा किया केयर नोट प्रकाशित हुआ। अब मरीज़ यही संस्करण देखेंगे।") } }, enabled = confirmed && !busy, modifier = Modifier.fillMaxWidth()) { Text(t("Publish reviewed care note", "समीक्षा किया केयर नोट प्रकाशित करें")) } }
                }
                if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) }
                item { TextButton(onClick = { reading = null }, enabled = !busy) { Text(t("Close", "बंद करें")) } }
            }
        }
    }
}
