@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.shape.RoundedCornerShape
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private fun workflowDate(time: Long) = if (time == 0L) "Date not recorded" else DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
private fun ackWords(status: WorkflowAckStatus) = when(status) { WorkflowAckStatus.READ->"they said they read it"; WorkflowAckStatus.CONCERN->"they raised a concern"; else->"they said they are not ready" }
private fun requestLabel(status: WorkflowRequestStatus) = status.name.lowercase().replaceFirstChar { it.uppercase() }
@Composable private fun WorkflowTitle(text: String) { Text(text, Modifier.a11yHeading(), fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Medium, color = Ink, fontFamily = SaharaTitleFont) }
@Composable private fun WorkflowSection(text: String) { Text(text, Modifier.padding(top = 8.dp).a11yHeading(), fontSize = 18.sp, fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont, color = DoctorCardInk) }
@Composable private fun WorkflowChip(text: String, fg: androidx.compose.ui.graphics.Color = DoctorStatusRest, bg: androidx.compose.ui.graphics.Color = DoctorStatusRestBg) { Surface(color = bg, shape = RoundedCornerShape(50)) { Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg) } }
@Composable private fun WorkflowStatus(text: String) { Surface(color = Peach.copy(alpha = .45f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Info, null, Modifier.size(20.dp), tint = RustText); Text(text, Modifier.weight(1f).testTag("workflow-status"), fontSize = 15.sp, color = DoctorCardInk) } } }
@Composable private fun WorkflowCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().a11yCard()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable fun DoctorRequestQueue(store: DoctorWorkflowStore, patients: List<DoctorDemoPatient>, onBack: () -> Unit, onOpen: (String) -> Unit, onSchedule: (WorkflowRequest) -> Unit, refreshToken: Int = 0) { val t = careText();
    var revision by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var message by remember { mutableStateOf<String?>(null) }
    val requests = remember(store, revision, refreshToken) { store.requests() }
    LazyColumn(Modifier.fillMaxSize().testTag("workflow-request-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DoctorRequestQueueHeader(onBack) }
        item { DoctorRequestFilters(filter) { filter = it } }
        message?.let { item { WorkflowStatus(it) } }
        val shown = requests.filter { filter == "All" || requestLabel(it.status) == filter }
        if (shown.isEmpty()) item { WorkflowCard { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Default.Inbox, null, tint = DoctorAccent, modifier = Modifier.size(24.dp)); Text(t("No requests in this group.", "इस समूह में कोई अनुरोध नहीं।"), fontSize = 16.sp, modifier = Modifier.weight(1f)) }; if (filter != "All") TextButton(onClick = { filter = "All" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Show all", "सभी दिखाएँ"), color = DoctorAccent) } } }
        items(shown, key = { it.id }) { request ->
            val patient = patients.firstOrNull { it.id == request.patientId }
            WorkflowCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(patient?.name?.removeSuffix(" Demo") ?: "Patient", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Spacer(Modifier.width(8.dp)); when (request.status) { WorkflowRequestStatus.COMPLETED -> WorkflowChip(requestLabel(request.status), DoctorStatusGood, DoctorStatusGoodBg); WorkflowRequestStatus.NEW -> WorkflowChip(requestLabel(request.status), DoctorStatusWarn, DoctorStatusWarnBg); else -> WorkflowChip(requestLabel(request.status)) } }
                Text(t("Listed under ${request.requestedBy.lowercase()} · ${workflowDate(request.createdAt)}", "${request.requestedBy.lowercase()} में दर्ज · ${workflowDate(request.createdAt)}"), fontSize = 14.sp, color = DoctorCardSub)
                Text(request.reason, fontSize = 16.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (request.status in listOf(WorkflowRequestStatus.NEW, WorkflowRequestStatus.DEFERRED)) Button(onClick = { message = store.transitionRequest(request.id, WorkflowRequestStatus.ACCEPTED) ?: t("Marked as planned on this device", "इस फ़ोन पर तय के रूप में दर्ज"); revision++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-accept-${request.id}")) { Text(t("Plan this", "इसे तय करें")) }
                    if (request.status == WorkflowRequestStatus.ACCEPTED) Button(onClick = { onSchedule(request) }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-schedule-${request.id}")) { Text(t("Schedule visit", "विज़िट तय करें")) }
                    if (request.status in listOf(WorkflowRequestStatus.NEW, WorkflowRequestStatus.ACCEPTED, WorkflowRequestStatus.SCHEDULED)) TextButton(onClick = { message = store.transitionRequest(request.id, WorkflowRequestStatus.DEFERRED) ?: t("Deferred on this device", "इस फ़ोन पर टाला गया"); revision++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-defer-${request.id}")) { Text(t("Defer", "टालें")) }
                    if (request.status == WorkflowRequestStatus.SCHEDULED) TextButton(onClick = { message = store.transitionRequest(request.id, WorkflowRequestStatus.COMPLETED) ?: t("Marked complete on this device", "इस फ़ोन पर पूरा दर्ज"); revision++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-complete-${request.id}")) { Text(t("Complete discussion", "चर्चा पूरी करें")) }
                }
                OutlinedButton(onClick = { onOpen(request.patientId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("workflow-request-${request.id}")) { Text(t("Open conversation", "बातचीत खोलें")); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null) }
            }
        }
    }
}

@Composable internal fun DoctorRequestQueueHeader(onBack: () -> Unit) { val t = careText();
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        TextButton(onClick=onBack,modifier=Modifier.heightIn(min=48.dp).testTag("workflow-back")) { Icon(Icons.Default.ChevronLeft,null);Text(t("Workspace", "कार्यक्षेत्र")) }
        WorkflowTitle(t("Conversations to plan", "तय करने वाली बातचीत"))
    }
}

@Composable internal fun DoctorRequestFilters(filter: String, onFilter: (String) -> Unit) {
    FlowRow(Modifier.fillMaxWidth().testTag("workflow-request-filters"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf("All","New","Accepted","Scheduled","Deferred","Completed").forEach { value ->
            FilterChip(filter==value,onClick={onFilter(value)},label={Text(value,fontSize=15.sp)},shape=RoundedCornerShape(50),modifier=Modifier.heightIn(min=48.dp).testTag("workflow-filter-$value"))
        }
    }
}

@Composable fun DoctorConversationWorkspace(patient: DoctorDemoPatient, store: DoctorWorkflowStore, onBack: () -> Unit, cloud:CareCloud?=null, onSignIn:()->Unit={}, enableOnlineAi:Boolean=false) { val t = careText();
    var draft by remember(patient.id) { mutableStateOf(store.draft(patient.id)) }
    var step by rememberSaveable(patient.id) { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var historySection by rememberSaveable(patient.id) { mutableStateOf("Records") }
    var selectedVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var participant by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf(t("Document the care discussion", "देखभाल चर्चा दर्ज करें")) }
    var recordingAgreed by rememberSaveable { mutableStateOf(false) }
    var cloudAgreed by rememberSaveable { mutableStateOf(false) }
    var reviewer by rememberSaveable { mutableStateOf("Dr Meera") }
    var checked by rememberSaveable { mutableStateOf(false) }
    var sourceConfirmed by rememberSaveable { mutableStateOf(false) }
    val sessionState=cloud?.session?.collectAsState()
    val accountId=sessionState?.value?.userId ?: "guest"
    val audio=rememberDoctorAudioController(patient.id,if(enableOnlineAi) accountId else "local-preview",if(enableOnlineAi) DoctorAudioStorage.LIVE else DoctorAudioStorage.LOCAL_PREVIEW)
    val audioState by audio.state.collectAsState()
    val onlineScope=rememberCoroutineScope()
    var onlineJob by remember { mutableStateOf<Job?>(null) }
    var onlineBusy by remember { mutableStateOf(false) }
    var returnedTranscript by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(patient.id,accountId,lifecycleOwner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) {onlineJob?.cancel();audio.release()} }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer);onlineJob?.cancel();audio.release() }
    }
    fun transcribeAndDraft(clip:DoctorAudioClip) {
        val service=cloud ?: return
        if(onlineBusy || service.session.value?.userId!=accountId || !audio.owns(clip)) return
        val expected=store.draft(patient.id).sourceText
        if(expected.isNotBlank()) { message=t("Your existing source was kept. Use an empty draft for a new transcript.", "आपका मौजूदा स्रोत रखा गया। नई लिखाई के लिए ख़ाली ड्राफ़्ट लें।");return }
        onlineBusy=true;message=null
        onlineJob=onlineScope.launch {
            try {
                val result=DoctorAiClient().transcribeAndDraft(clip.file,clip.consent,service.transcriptionToken())
                if(service.session.value?.userId!=accountId || !audio.owns(clip)) { message=t("The account or recording changed. No result was inserted.", "खाता या रिकॉर्डिंग बदल गई। कोई नतीजा नहीं डाला गया।");return@launch }
                returnedTranscript=result.transcript.text
                val error=store.importTranscript(patient.id,result.transcript.text,result.transcript.provider,result.transcript.model,clip.id,clip.consent.transcriptionConsentAt,expected)
                if(error!=null) { message=error+t(" The returned transcript is preserved below for review.", " लौटी लिखाई जाँच के लिए नीचे रखी है।");return@launch }
                val draftingError=result.draft?.let { store.applyAiDraft(patient.id,it,result.transcript.text) }
                draft=store.draft(patient.id);revision++;checked=false;sourceConfirmed=false
                message=when { result.draftError!=null -> t("Transcript saved. AI drafting did not finish: ${result.draftError}", "लिखाई सहेजी गई। AI ड्राफ़्ट पूरा नहीं हुआ: ${result.draftError}");draftingError!=null -> t("Transcript saved; existing note fields kept. $draftingError", "लिखाई सहेजी गई; नोट के मौजूदा हिस्से रखे गए। $draftingError");result.draft==null -> t("Transcript saved. No AI draft was returned.", "लिखाई सहेजी गई। कोई AI ड्राफ़्ट नहीं लौटा।");else -> t("Transcript and AI draft ready. Check every word against the recording.", "लिखाई और AI ड्राफ़्ट तैयार। हर शब्द रिकॉर्डिंग से मिलाएँ।") }
                if(draftingError==null && result.draft!=null) returnedTranscript=null
            } catch(e:Exception) { if(e is CancellationException) throw e;message=e.message ?: t("Online processing could not finish. The local audio is kept.", "ऑनलाइन प्रोसेसिंग पूरी नहीं हुई। फ़ोन का ऑडियो रखा गया है।") }
            finally { onlineBusy=false }
        }
    }
    val versions = remember(revision) { store.versions(patient.id) }
    val sources = remember(patient.id) { DoctorWorkflowData.sources(patient.id) }
    fun update(value: WorkflowDraft) { val error = store.saveDraft(value); message = error; if(error == null) { draft = store.draft(patient.id); checked = false; sourceConfirmed = false; revision++ } }
    fun moveStep(value:Int) { if(step!=value) {audio.release();onlineJob?.cancel()};step=value }
    LaunchedEffect(selectedVersion) { if(selectedVersion!=null) {audio.release();onlineJob?.cancel()} }
    val version = versions.firstOrNull { it.id == selectedVersion }
    BackHandler(selectedVersion != null) { selectedVersion = null; revision++ }
    if (version != null) {
        DoctorVersionReader(patient, version, store, onBack = { selectedVersion = null; revision++ })
        return
    }
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("workflow-conversation"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = { message = store.saveDraft(draft); if (message == null) onBack() }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-back")) { Icon(Icons.Default.ChevronLeft, null); Text(t("Patient record", "मरीज़ का रिकॉर्ड")) }; WorkflowTitle(t("Care conversation", "देखभाल पर बातचीत")); Text(patient.name.removeSuffix(" Demo"), color = RustText, fontSize = 16.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Prepare", "Document", "Review").forEachIndexed { index, title -> FilterChip(step == index, onClick = { moveStep(index) }, label = { Text("${index + 1} $title", fontSize = 15.sp) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-step-$title")) } }
        }
        message?.let { item { WorkflowStatus(it) } }
        when (step) {
            0 -> {
                item { WorkflowSection(t("Previous discussion", "पिछली चर्चा")) }
                val previous = versions.firstOrNull()
                if (previous != null) item { WorkflowCard { Text(t("Practice care note v${previous.number}", "अभ्यास केयर नोट v${previous.number}"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text(previous.draft.nextSteps.ifBlank { t("No next steps recorded.", "कोई अगला क़दम दर्ज नहीं।") }); TextButton(onClick = { selectedVersion = previous.id }, modifier = Modifier.testTag("workflow-version-${previous.id}")) { Text(t("Read exact version", "मूल संस्करण पढ़ें")) } } }
                else item { WorkflowCard { Text(sources.firstOrNull()?.label ?: t("No previous discussion", "कोई पिछली चर्चा नहीं"), fontWeight = FontWeight.SemiBold); Text(sources.firstOrNull()?.text ?: t("Start a conversation below.", "नीचे बातचीत शुरू करें।")) } }
                item { WorkflowSection(t("Guided discussion checklist", "चर्चा की सूची")) }
                items(draft.checklist.withIndex().toList(), key = { it.index }) { (index, topic) -> WorkflowCard {
                    Text(topic.title, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { WorkflowTopicStatus.entries.forEach { status -> FilterChip(topic.status == status, onClick = { update(draft.copy(checklist = draft.checklist.mapIndexed { i, row -> if(i == index) row.copy(status = status) else row })) }, label = { Text(when(status) { WorkflowTopicStatus.DISCUSSED -> t("Discussed", "बात हुई"); WorkflowTopicStatus.DEFERRED -> "Defer"; else -> t("Not discussed", "बात नहीं हुई") }, fontSize = 14.sp) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min=48.dp).testTag("workflow-topic-$index-${status.name}")) } }
                } }
                item { WorkflowSection(t("Participant permissions", "भागीदारों की अनुमति")) }
                item { OutlinedTextField(participant, { participant = it }, label = { Text(t("Participant name", "भागीदार का नाम")) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("workflow-consent-participant"), singleLine=true) }
                item { OutlinedTextField(purpose, { purpose = it }, label = { Text(t("Purpose", "उद्देश्य")) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag("workflow-consent-purpose")) }
                item { WorkflowCheck(recordingAgreed, { recordingAgreed = it }, t("Agrees to recording", "रिकॉर्डिंग के लिए राज़ी"), "workflow-consent-recording"); WorkflowCheck(cloudAgreed, { cloudAgreed = it }, t("Separately agrees to cloud processing", "क्लाउड प्रोसेसिंग के लिए अलग से राज़ी"), "workflow-consent-cloud") }
                item { OutlinedButton(onClick = { val error = store.recordConsent(patient.id, participant, purpose, recordingAgreed, cloudAgreed); message = error ?: t("Participant selections saved locally", "भागीदारों के चुनाव इसी फ़ोन पर सहेजे गए"); if(error == null) { participant=""; recordingAgreed=false; cloudAgreed=false; revision++ } }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-save-consent")) { Text(t("Save participant selections", "भागीदारों के चुनाव सहेजें")) } }
                items(store.snapshot().consents.filter { it.patientId == patient.id }, key={it.id}) { consent -> Text("${consent.participant}: recording ${if(consent.recordingAgreed) "agreed" else "not agreed"}; cloud ${if(consent.cloudProcessingAgreed) "agreed" else "not agreed"}\n${workflowDate(consent.at)}", fontSize=14.sp, color=DoctorCardSub) }
                item { Button(onClick = { moveStep(1) }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(t("Continue to document", "दर्ज करने पर आगे बढ़ें")) } }
            }
            1 -> {
                if(enableOnlineAi) {
                    item { WorkflowSection("Audio → transcript → draft") }
                    item { DoctorAudioCapture(audio,sessionState?.value!=null,onlineBusy,draft.sourceText.isBlank(),onSignIn,::transcribeAndDraft) }
                } else item { DoctorLocalPreviewCapture(audio,draft.sourceText,when {draft.sourceText.isBlank()->DoctorTranscriptKind.NONE;draft.mockGenerated->DoctorTranscriptKind.FICTIONAL_SAMPLE;else->DoctorTranscriptKind.MANUAL},onSourceEdit={update(draft.copy(sourceText=it.take(12000)))}) }
                items(audioState.clips, key={"offline-audio-${it.id}"}) { clip ->
                    OfflineConversationTranscription(file=clip.file,identity="$accountId:${patient.id}",consent=clip.consent,
                        enabled=!audioState.recording && !onlineBusy && draft.sourceText.isBlank(),
                        onUse={review ->
                            if(audio.isClosed() || !audio.owns(clip) || audio.state.value.recording || audio.state.value.clips.none {it.id==clip.id}) false
                            else try {
                                val failure=store.importOfflineTranscript(patient.id,clip.id,review)
                                if(failure!=null) {message=failure;false}
                                else {draft=store.draft(patient.id);revision++;checked=false;sourceConfirmed=false;message=t("Checked transcript added. Review the original words before publishing.", "जाँची लिखाई जोड़ी गई। प्रकाशित करने से पहले मूल शब्द देखें।");true}
                            }
                            catch(error:Exception) {message=error.message ?: t("Couldn't add this transcript. Your original recording is unchanged.", "यह लिखाई नहीं जुड़ सकी। आपकी मूल रिकॉर्डिंग वैसी ही है।");false}
                        })
                }
                if(draft.transcribedAt>0) item { Text(t("Transcript: ${draft.transcriptProvider} / ${draft.transcriptModel} · ${workflowDate(draft.transcribedAt)}", "लिखाई: ${draft.transcriptProvider} / ${draft.transcriptModel} · ${workflowDate(draft.transcribedAt)}"),color=Rust,style=MaterialTheme.typography.labelMedium) }
                if(draft.aiModel.isNotBlank()) item { Text(t("Online AI draft: ${draft.aiProvider} / ${draft.aiModel} · not clinically reviewed", "ऑनलाइन AI ड्राफ़्ट: ${draft.aiProvider} / ${draft.aiModel} · डॉक्टर ने नहीं जाँचा"),color=Rust,style=MaterialTheme.typography.labelMedium) }
                returnedTranscript?.let { text -> item { WorkflowCard { Text(t("Returned transcript", "लौटी लिखाई"),fontWeight=FontWeight.SemiBold);Text(text);Text(t("Your existing work was not silently replaced.", "आपका मौजूदा काम चुपचाप नहीं बदला गया।"),style=MaterialTheme.typography.bodySmall) } } }
                item { OutlinedButton(onClick = { val error=store.loadMockDraft(patient.id); message=error ?: t("Practice draft loaded (written in advance, not AI)", "अभ्यास ड्राफ़्ट लोड हुआ (पहले से लिखा, AI नहीं)"); if(error==null) { draft=store.draft(patient.id); revision++ } }, enabled = listOf(draft.sourceText,draft.priorities,draft.participants,draft.topics,draft.questions,draft.undecided,draft.nextSteps).all { it.isBlank() }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-load-mock")) { Icon(Icons.Default.Description,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Load practice draft (written in advance, not AI)", "अभ्यास ड्राफ़्ट लोड करें (पहले से लिखा, AI नहीं)")) }; if(draft.mockGenerated) Text(t("Practice draft, written in advance · not AI · not clinically reviewed", "अभ्यास ड्राफ़्ट, पहले से लिखा · AI नहीं · डॉक्टर ने नहीं जाँचा"), color=Rust, style=MaterialTheme.typography.labelMedium) }
                if(enableOnlineAi) item { OutlinedTextField(draft.sourceText, { update(draft.copy(sourceText=it.take(12000))) }, label={Text(t("Original source text", "मूल स्रोत"))}, minLines=5, modifier=Modifier.fillMaxWidth().testTag("workflow-source-text")) }
                item { WorkflowSection(t("Source references", "स्रोत के हवाले")) }
                if(draft.transcribedAt>0) item { WorkflowCard { Text(t("Consented conversation recording", "सहमति वाली बातचीत की रिकॉर्डिंग"),fontWeight=FontWeight.SemiBold);Text(t("Transcript created ${workflowDate(draft.transcribedAt)} · ${draft.transcriptModel}", "लिखाई बनी ${workflowDate(draft.transcribedAt)} · ${draft.transcriptModel}"),style=MaterialTheme.typography.bodySmall);Text(t("Check the transcript against its original audio before review.", "जाँच से पहले लिखाई को मूल ऑडियो से मिलाएँ।"),style=MaterialTheme.typography.bodySmall) } }
                else items(sources, key={it.id}) { source -> WorkflowCard { Text(source.label,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("workflow-source-${source.id}")); Text(source.text, modifier=Modifier.testTag("workflow-source-text-${source.id}")) } }
                item { WorkflowCard { Text(t("Uncertainty stays visible", "अनिश्चितता दिखती रहती है"), fontWeight=FontWeight.SemiBold); Text(if(draft.aiModel.isNotBlank()) t("AI may miss or misattribute words. Verify source evidence, leave unsupported fields blank and keep unresolved questions visible.", "AI शब्द छोड़ सकता है या ग़लत व्यक्ति के नाम कर सकता है। स्रोत जाँचें, बिना आधार वाले हिस्से ख़ाली छोड़ें और अनसुलझे सवाल दिखते रहने दें।") else t("The source does not state patient priorities or a treatment choice. Do not infer agreement.", "स्रोत में मरीज़ की प्राथमिकताएँ या इलाज का चुनाव नहीं लिखा। सहमति का अंदाज़ा न लगाएँ।"));draft.aiWarnings.forEach { Text(it,color=Rust,style=MaterialTheme.typography.bodySmall) } } }
                items(draft.evidence.withIndex().toList(),key={"evidence-${it.index}"}) { (_,evidence) -> WorkflowCard { Text(t("Source evidence · ${evidence.section}", "स्रोत का सबूत · ${evidence.section}"),fontWeight=FontWeight.Medium);Text(evidence.quote) } }
                val fields = listOf("priorities" to t("What matters most", "सबसे ज़रूरी क्या है"), "participants" to t("Who participated", "कौन शामिल था"), "topics" to t("What was discussed", "क्या चर्चा हुई"), "questions" to t("Questions & concerns", "सवाल और चिंताएँ"), "undecided" to t("Still undecided", "अभी तय नहीं"), "nextSteps" to t("Next steps", "अगले क़दम"))
                items(fields, key={it.first}) { (id,label) ->
                    val value = when(id) { "priorities"->draft.priorities; "participants"->draft.participants; "topics"->draft.topics; "questions"->draft.questions; "undecided"->draft.undecided; else->draft.nextSteps }
                    OutlinedTextField(value,{ valueText -> val text=valueText.take(4000); update(when(id) { "priorities"->draft.copy(priorities=text); "participants"->draft.copy(participants=text); "topics"->draft.copy(topics=text); "questions"->draft.copy(questions=text); "undecided"->draft.copy(undecided=text); else->draft.copy(nextSteps=text) }) },label={Text(label)},minLines=2,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().testTag("workflow-field-$id"))
                }
                item { Button(onClick={moveStep(2)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(t("Continue to review", "जाँच पर आगे बढ़ें")) } }
            }
            else -> {
                item { WorkflowSection(t("Check the exact draft", "मूल ड्राफ़्ट जाँचें")) }
                item { WorkflowCard { Text(t("Practice walkthrough · unverified", "अभ्यास वॉकथ्रू · अपुष्ट"), fontWeight=FontWeight.Medium, color=Rust, fontFamily = SaharaTitleFont) } }
                item { WorkflowDraftSummary(draft) }
                item { OutlinedTextField(reviewer,{reviewer=it; checked=false},label={Text(t("Reviewer name (self-entered)", "समीक्षक का नाम (ख़ुद लिखा)"))},singleLine=true,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().testTag("workflow-reviewer")) }
                item { WorkflowCheck(sourceConfirmed,{sourceConfirmed=it},t("I checked the original source text", "मैंने मूल स्रोत जाँचा"), "workflow-source-confirmed"); WorkflowCheck(checked,{checked=it},t("I reviewed every section and unresolved point", "मैंने हर हिस्सा और अनसुलझी बात जाँची"), "workflow-review-checked") }
                item { Button(onClick={ val saved=store.saveDraft(draft); val error=saved ?: store.publish(patient.id,reviewer,checked,sourceConfirmed); message=error ?: t("Practice version saved on this phone", "अभ्यास संस्करण इस फ़ोन पर सहेजा"); if(error==null) { revision++; selectedVersion=store.versions(patient.id).first().id; checked=false; sourceConfirmed=false } }, modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("workflow-publish")) { Text(t("Save practice version", "अभ्यास संस्करण सहेजें")) } }
            }
        }
        item { OutlinedButton(onClick={message=store.saveDraft(draft); if(message==null) onBack()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-finish-later")) { Text(t("Save & finish later", "सहेजें और बाद में पूरा करें")) }; Text(t("Draft changes are saved on this device.", "ड्राफ़्ट के बदलाव इसी फ़ोन पर सहेजे जाते हैं।"), Modifier.fillMaxWidth().padding(top = 4.dp), fontSize=14.sp, color=DoctorCardSub, textAlign=androidx.compose.ui.text.style.TextAlign.Center) }
        if(versions.isNotEmpty()) item { WorkflowSection(t("Version history", "संस्करणों का इतिहास")) }
        items(versions,key={it.id}) { item -> OutlinedButton(onClick={selectedVersion=item.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-version-${item.id}")) { Text(t("Version ${item.number} · ${workflowDate(item.publishedAt)}", "संस्करण ${item.number} · ${workflowDate(item.publishedAt)}")) } }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Records", "Activity").forEach { section -> FilterChip(selected=historySection==section,onClick={historySection=section},label={Text(if(section=="Records") "Records & timeline" else "Activity & updates",fontSize=15.sp)},shape=RoundedCornerShape(50),modifier=Modifier.heightIn(min=48.dp).testTag(if(section=="Records") "workflow-open-records" else "workflow-open-activity")) }
        } }
        if(historySection=="Records") {
            items(store.attachments(patient.id),key={it.id}) { attachment -> WorkflowCard { Text(attachment.title,fontWeight=FontWeight.Medium); Text(attachment.text); Text(t("Text source; no uploaded original file.", "लिखा हुआ स्रोत; कोई मूल फ़ाइल अपलोड नहीं।"),fontSize=14.sp,color=DoctorCardSub) } }
            items(store.timeline(patient.id),key={"timeline-${it.id}"}) { entry -> Column(verticalArrangement=Arrangement.spacedBy(2.dp)) { HorizontalDivider(color=DoctorHairline); Spacer(Modifier.height(8.dp)); Text(entry.title,fontWeight=FontWeight.Medium); Text(entry.detail); Text(workflowDate(entry.at),fontSize=14.sp,color=DoctorCardSub) } }
        }
        if(historySection=="Activity") { items(store.activity(patient.id).take(30),key={it.id}) { entry -> Text("${workflowDate(entry.at)}\n${entry.action}",fontSize=14.sp,color=DoctorCardInk) } }
    }
}

@Composable private fun WorkflowCheck(value: Boolean,onChange:(Boolean)->Unit,text:String,tag:String) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Checkbox(value,onChange,modifier=Modifier.testTag(tag)); Text(text,Modifier.weight(1f)) } }
@Composable private fun WorkflowDraftSummary(draft: WorkflowDraft) { val t = careText(); WorkflowCard { listOf(t("What matters", "क्या ज़रूरी है") to draft.priorities,t("Participants", "भागीदार") to draft.participants,t("Discussion", "चर्चा") to draft.topics,t("Questions", "सवाल") to draft.questions,t("Undecided", "तय नहीं") to draft.undecided,t("Next steps", "अगले क़दम") to draft.nextSteps).forEach { (label,text) -> Text(label,fontWeight=FontWeight.SemiBold); Text(text.ifBlank { t("Not recorded", "दर्ज नहीं") }) } } }

@Composable private fun DoctorVersionReader(patient:DoctorDemoPatient,version:WorkflowVersion,store:DoctorWorkflowStore,onBack:()->Unit) { val t = careText();
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var exported by remember(version.id) { mutableStateOf<File?>(null) }
    var pdfPassword by remember(version.id) { mutableStateOf("") }
    var showPassword by remember(version.id) { mutableStateOf(false) }
    val context=LocalContext.current
    val acknowledgements=remember(revision) { store.acknowledgements(version.id) }
    LazyColumn(Modifier.fillMaxSize().testTag("workflow-version-reader"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack,modifier=Modifier.heightIn(min=48.dp).testTag("workflow-version-back")) { Icon(Icons.Default.ChevronLeft,null); Text(t("Conversation", "बातचीत")) }; WorkflowTitle(t("Care Note · Version ${version.number}", "केयर नोट · संस्करण ${version.number}")); Text("${patient.name.removeSuffix(" Demo")} · ${workflowDate(version.publishedAt)}",color=RustText,fontSize=16.sp,fontWeight=FontWeight.Medium); Spacer(Modifier.height(4.dp)); FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { WorkflowChip(version.reviewLabel); WorkflowChip(t("Reviewer: ${version.reviewerName} (self-entered)", "समीक्षक: ${version.reviewerName} (ख़ुद लिखा)"),DoctorCardSub,Paper) } }
        item { WorkflowDraftSummary(version.draft) }
        item { WorkflowCard { Text(t("Exact source", "मूल स्रोत"),fontWeight=FontWeight.SemiBold); Text(version.draft.sourceText); Text(if(version.draft.transcribedAt>0) t("Recorded conversation · ${workflowDate(version.draft.transcribedAt)} · ${version.draft.transcriptModel}", "रिकॉर्ड की गई बातचीत · ${workflowDate(version.draft.transcribedAt)} · ${version.draft.transcriptModel}") else DoctorWorkflowData.sources(patient.id).filter { it.id in version.draft.sourceIds }.joinToString("\n") { it.label }.ifBlank { t("Manually entered source", "हाथ से लिखा स्रोत") },style=MaterialTheme.typography.bodySmall);if(version.draft.aiModel.isNotBlank())Text(t("AI draft: ${version.draft.aiProvider} / ${version.draft.aiModel}", "AI ड्राफ़्ट: ${version.draft.aiProvider} / ${version.draft.aiModel}"),style=MaterialTheme.typography.bodySmall);version.draft.aiWarnings.forEach {Text(it,style=MaterialTheme.typography.bodySmall,color=Rust)} } }
        item { Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.weight(1f)) { WorkflowSection(t("Record what they told you", "उन्होंने जो बताया वह दर्ज करें")) }; DoctorInfoIcon(t("Record what they told you", "उन्होंने जो बताया वह दर्ज करें"), t("This version has not been shared with anyone, so no one has responded in the app. If the patient or a family member told you something about it in person, note it here as your own record. It is not their acknowledgement, consent or agreement.", "यह संस्करण किसी के साथ साझा नहीं हुआ, इसलिए ऐप में किसी ने जवाब नहीं दिया। अगर मरीज़ या परिवार ने आपको आमने-सामने कुछ बताया, तो उसे अपने रिकॉर्ड के रूप में यहाँ लिखें। यह उनकी पुष्टि, सहमति या रज़ामंदी नहीं है।"),"workflow-ack-info") } }
        items(DoctorWorkflowData.members(patient.id),key={it.id}) { member -> WorkflowCard {
            Text(member.name,fontWeight=FontWeight.SemiBold)
            val ack=acknowledgements.firstOrNull { it.memberId==member.id }
            Text(if(ack==null) t("Nothing recorded for this version", "इस संस्करण के लिए कुछ दर्ज नहीं") else t("You noted: ", "आपने लिखा: ") + "${ackWords(ack.status)} · ${workflowDate(ack.at)}",color=Rust,modifier=Modifier.testTag("workflow-ack-state-${member.id}"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { WorkflowAckStatus.entries.forEach { status -> OutlinedButton(onClick={message=store.recordToldToClinician(version.id,member.id,status);revision++},shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp).testTag("workflow-ack-${member.id}-${status.name}")) { Text(when(status) { WorkflowAckStatus.READ->t("Told me they read it", "बताया कि पढ़ लिया"); WorkflowAckStatus.CONCERN->t("Told me a concern", "कोई चिंता बताई"); else->t("Told me not ready", "बताया कि अभी तैयार नहीं") }) } } }
        } }
        message?.let { item { WorkflowStatus(it) } }
        item { WorkflowCard {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(t("Password (optional)", "पासवर्ड (चाहें तो)"),Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
            DoctorInfoIcon(t("Password (optional)", "पासवर्ड (चाहें तो)"),t("With a password, the PDF itself is locked (AES-256): any PDF reader asks for the password before showing the note. Tell the other person the password by phone, not in the same message.", "पासवर्ड देने पर PDF खुद लॉक हो जाती है (AES-256): कोई भी PDF ऐप नोट दिखाने से पहले पासवर्ड माँगेगा। पासवर्ड फ़ोन पर बताएँ, उसी मैसेज में नहीं।"),"workflow-pdf-password-info") }
            OutlinedTextField(pdfPassword,{ pdfPassword=it.take(64) },label={Text(t("Leave empty for a normal PDF", "खाली छोड़ें तो सामान्य PDF"))},singleLine=true,shape=RoundedCornerShape(14.dp),visualTransformation=if(showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon={ IconButton(onClick={showPassword=!showPassword}) { Icon(if(showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,if(showPassword) t("Hide password", "पासवर्ड छिपाएँ") else t("Show password", "पासवर्ड दिखाएँ")) } },
                supportingText=pdfPassword.takeIf { it.isNotEmpty() }?.let(PdfPasswordLock::passwordError)?.let { { Text(it) } },modifier=Modifier.fillMaxWidth().testTag("workflow-pdf-password"))
        } }
        item { Button(onClick={runCatching { if(pdfPassword.isEmpty()) DoctorWorkflowPdf.create(context,version,patient.name.removeSuffix(" Demo")) else DoctorWorkflowPdf.createLocked(context,version,patient.name.removeSuffix(" Demo"),pdfPassword) }.onSuccess { exported=it;message=if(pdfPassword.isEmpty()) t("Practice PDF created on this device", "अभ्यास PDF इसी फ़ोन पर बनी") else t("Locked PDF created on this device", "लॉक PDF इसी फ़ोन पर बनी") }.onFailure { message=(it as? IllegalArgumentException)?.message ?: t("Could not create the practice PDF. Try again.", "अभ्यास PDF नहीं बन सकी। फिर कोशिश करें।") }},enabled=pdfPassword.isEmpty()||PdfPasswordLock.passwordError(pdfPassword)==null,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-export-pdf")) { Icon(if(pdfPassword.isEmpty()) Icons.Default.PictureAsPdf else Icons.Default.Lock,null);Spacer(Modifier.width(8.dp));Text(if(pdfPassword.isEmpty()) t("Create practice PDF", "अभ्यास PDF बनाएँ") else t("Create locked PDF", "लॉक PDF बनाएँ")) } }
        exported?.let { file -> item { Text("${file.name} · ${file.length()/1024} KB",fontSize=14.sp,color=DoctorCardSub,modifier=Modifier.testTag("workflow-pdf-ready")); OutlinedButton(onClick={runCatching { DoctorWorkflowPdf.share(context,file) }.onFailure { message=t("No sharing app is available.", "भेजने के लिए कोई ऐप नहीं है।") }},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-share-pdf")) { Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text(if(file.name.endsWith("-locked.pdf")) t("Share locked PDF", "लॉक PDF भेजें") else t("Share practice PDF", "अभ्यास PDF भेजें")) } } }
    }
}
