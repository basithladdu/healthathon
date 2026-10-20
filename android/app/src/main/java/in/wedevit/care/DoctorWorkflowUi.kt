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

private fun workflowDate(time: Long) = if (time == 0L) "Sample context" else DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
private fun requestLabel(status: WorkflowRequestStatus) = status.name.lowercase().replaceFirstChar { it.uppercase() }
@Composable private fun WorkflowTitle(text: String) { Text(text, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, color = Ink) }
@Composable private fun WorkflowSection(text: String) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
@Composable private fun WorkflowCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Paper, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable fun DoctorRequestQueue(store: DoctorWorkflowStore, patients: List<DoctorDemoPatient>, onBack: () -> Unit, onOpen: (String) -> Unit, onSchedule: (WorkflowRequest) -> Unit, refreshToken: Int = 0) {
    var revision by remember { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf("All") }
    var message by remember { mutableStateOf<String?>(null) }
    val requests = remember(store, revision, refreshToken) { store.requests() }
    LazyColumn(Modifier.fillMaxSize().testTag("workflow-request-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onBack, modifier = Modifier.testTag("workflow-back")) { Icon(Icons.Default.ChevronLeft, null); Text("Workspace") }; WorkflowTitle("Conversation requests"); Spacer(Modifier.height(6.dp)); Text("Review, organise and continue each discussion.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "New", "Accepted", "Scheduled", "Deferred", "Completed").forEach { value -> FilterChip(filter == value, onClick = { filter = value }, label = { Text(value) }, modifier = Modifier.heightIn(min = 48.dp)) } } }
        message?.let { item { Text(it, color = Rust, modifier = Modifier.testTag("workflow-status")) } }
        val shown = requests.filter { filter == "All" || requestLabel(it.status) == filter }
        if (shown.isEmpty()) item { Text("No requests in this group.") }
        items(shown, key = { it.id }) { request ->
            val patient = patients.firstOrNull { it.id == request.patientId }
            WorkflowCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(patient?.name?.removeSuffix(" Demo") ?: "Patient", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(requestLabel(request.status), color = Rust, style = MaterialTheme.typography.labelLarge) }
                Text("${request.requestedBy} · ${workflowDate(request.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(request.reason)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (request.status in listOf(WorkflowRequestStatus.NEW, WorkflowRequestStatus.DEFERRED)) Button(onClick = { message = store.transitionRequest(request.id, WorkflowRequestStatus.ACCEPTED) ?: "Request accepted locally"; revision++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-accept-${request.id}")) { Text("Accept") }
                    if (request.status == WorkflowRequestStatus.ACCEPTED) Button(onClick = { onSchedule(request) }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-schedule-${request.id}")) { Text("Schedule visit") }
                    if (request.status in listOf(WorkflowRequestStatus.NEW, WorkflowRequestStatus.ACCEPTED, WorkflowRequestStatus.SCHEDULED)) TextButton(onClick = { message = store.transitionRequest(request.id, WorkflowRequestStatus.DEFERRED) ?: "Request deferred locally"; revision++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-defer-${request.id}")) { Text("Defer") }
                    if (request.status == WorkflowRequestStatus.SCHEDULED) TextButton(onClick = { message = store.transitionRequest(request.id, WorkflowRequestStatus.COMPLETED) ?: "Request completed locally"; revision++ }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-complete-${request.id}")) { Text("Complete discussion") }
                }
                OutlinedButton(onClick = { onOpen(request.patientId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("workflow-request-${request.id}")) { Text("Open conversation"); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.ChevronRight, null) }
            }
        }
        item { Text("Local request simulation. Status changes do not notify a patient or reserve a hospital appointment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun DoctorConversationWorkspace(patient: DoctorDemoPatient, store: DoctorWorkflowStore, onBack: () -> Unit, cloud:CareCloud?=null, onSignIn:()->Unit={}, enableOnlineAi:Boolean=false) {
    var draft by remember(patient.id) { mutableStateOf(store.draft(patient.id)) }
    var step by rememberSaveable(patient.id) { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var historySection by rememberSaveable(patient.id) { mutableStateOf("Records") }
    var selectedVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var participant by rememberSaveable { mutableStateOf("") }
    var purpose by rememberSaveable { mutableStateOf("Document the care discussion") }
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
        if(expected.isNotBlank()) { message="Your existing source was kept. Use an empty draft for a new transcript.";return }
        onlineBusy=true;message=null
        onlineJob=onlineScope.launch {
            try {
                val result=DoctorAiClient().transcribeAndDraft(clip.file,clip.consent,service.transcriptionToken())
                if(service.session.value?.userId!=accountId || !audio.owns(clip)) { message="The account or recording changed. No result was inserted.";return@launch }
                returnedTranscript=result.transcript.text
                val error=store.importTranscript(patient.id,result.transcript.text,result.transcript.provider,result.transcript.model,clip.id,clip.consent.transcriptionConsentAt,expected)
                if(error!=null) { message=error+" The returned transcript is preserved below for review.";return@launch }
                val draftingError=result.draft?.let { store.applyAiDraft(patient.id,it,result.transcript.text) }
                draft=store.draft(patient.id);revision++;checked=false;sourceConfirmed=false
                message=when { result.draftError!=null -> "Transcript saved. AI drafting did not finish: ${result.draftError}";draftingError!=null -> "Transcript saved; existing note fields kept. $draftingError";result.draft==null -> "Transcript saved. No AI draft was returned.";else -> "Transcript and AI draft ready. Check every word against the recording." }
                if(draftingError==null && result.draft!=null) returnedTranscript=null
            } catch(e:Exception) { if(e is CancellationException) throw e;message=e.message ?: "Online processing could not finish. The local audio is kept." }
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
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("workflow-conversation"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = { message = store.saveDraft(draft); if (message == null) onBack() }, modifier = Modifier.testTag("workflow-back")) { Icon(Icons.Default.ChevronLeft, null); Text("Patient record") }; WorkflowTitle("Care conversation"); Text(patient.name.removeSuffix(" Demo"), color = Rust, fontWeight = FontWeight.Medium); Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Prepare", "Document", "Review").forEachIndexed { index, title -> FilterChip(step == index, onClick = { moveStep(index) }, label = { Text("${index + 1} $title") }, modifier = Modifier.heightIn(min = 48.dp).testTag("workflow-step-$title")) } }
        }
        message?.let { item { Text(it, color = Rust, modifier = Modifier.testTag("workflow-status")) } }
        when (step) {
            0 -> {
                item { WorkflowSection("Previous discussion") }
                val previous = versions.firstOrNull()
                if (previous != null) item { WorkflowCard { Text("Care Note preview v${previous.number}", fontWeight = FontWeight.SemiBold); Text(previous.draft.nextSteps.ifBlank { "No next steps recorded." }); TextButton(onClick = { selectedVersion = previous.id }, modifier = Modifier.testTag("workflow-version-${previous.id}")) { Text("Read exact version") } } }
                else item { WorkflowCard { Text(sources.firstOrNull()?.label ?: "No previous discussion", fontWeight = FontWeight.SemiBold); Text(sources.firstOrNull()?.text ?: "Start a conversation below."); Text("Sample source, not a verified clinical note.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                item { WorkflowSection("Guided discussion checklist"); Text("Record what was covered. Leaving a topic open does not imply agreement.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(draft.checklist.withIndex().toList(), key = { it.index }) { (index, topic) -> WorkflowCard {
                    Text(topic.title, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { WorkflowTopicStatus.entries.forEach { status -> FilterChip(topic.status == status, onClick = { update(draft.copy(checklist = draft.checklist.mapIndexed { i, row -> if(i == index) row.copy(status = status) else row })) }, label = { Text(when(status) { WorkflowTopicStatus.DISCUSSED -> "Discussed"; WorkflowTopicStatus.DEFERRED -> "Defer"; else -> "Not discussed" }, fontSize = 12.sp) }, modifier = Modifier.heightIn(min=48.dp).testTag("workflow-topic-$index-${status.name}")) } }
                } }
                item { WorkflowSection("Participant permissions"); Text("Preview each person's separate selections. This screen does not record or upload audio.", color=MaterialTheme.colorScheme.onSurfaceVariant) }
                item { OutlinedTextField(participant, { participant = it }, label = { Text("Participant name") }, modifier = Modifier.fillMaxWidth().testTag("workflow-consent-participant"), singleLine=true) }
                item { OutlinedTextField(purpose, { purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth().testTag("workflow-consent-purpose")) }
                item { WorkflowCheck(recordingAgreed, { recordingAgreed = it }, "Agrees to recording", "workflow-consent-recording"); WorkflowCheck(cloudAgreed, { cloudAgreed = it }, "Separately agrees to cloud processing", "workflow-consent-cloud") }
                item { OutlinedButton(onClick = { val error = store.recordConsent(patient.id, participant, purpose, recordingAgreed, cloudAgreed); message = error ?: "Participant selections saved locally"; if(error == null) { participant=""; recordingAgreed=false; cloudAgreed=false; revision++ } }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-save-consent")) { Text("Save participant selections") } }
                items(store.snapshot().consents.filter { it.patientId == patient.id }, key={it.id}) { consent -> Text("${consent.participant}: recording ${if(consent.recordingAgreed) "agreed" else "not agreed"}; cloud ${if(consent.cloudProcessingAgreed) "agreed" else "not agreed"}\n${workflowDate(consent.at)}", style=MaterialTheme.typography.bodySmall) }
                item { Button(onClick = { moveStep(1) }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Continue to document") } }
            }
            1 -> {
                if(enableOnlineAi) {
                    item { WorkflowSection("Audio → transcript → draft") }
                    item { DoctorAudioCapture(audio,sessionState?.value!=null,onlineBusy,draft.sourceText.isBlank(),onSignIn,::transcribeAndDraft) }
                    item { Text("Use fictional test content only. Online processing is explicit; nothing is uploaded while recording. You can also type source text below.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall) }
                } else item { DoctorLocalPreviewCapture(audio,draft.sourceText,when {draft.sourceText.isBlank()->DoctorTranscriptKind.NONE;draft.mockGenerated->DoctorTranscriptKind.FICTIONAL_SAMPLE;else->DoctorTranscriptKind.MANUAL},onSourceEdit={update(draft.copy(sourceText=it.take(12000)))}) }
                items(audioState.clips, key={"offline-audio-${it.id}"}) { clip ->
                    OfflineConversationTranscription(file=clip.file,identity="$accountId:${patient.id}",consent=clip.consent,
                        enabled=!audioState.recording && !onlineBusy && draft.sourceText.isBlank(),
                        onUse={review ->
                            if(audio.isClosed() || !audio.owns(clip) || audio.state.value.recording || audio.state.value.clips.none {it.id==clip.id}) false
                            else try {
                                val failure=store.importOfflineTranscript(patient.id,clip.id,review)
                                if(failure!=null) {message=failure;false}
                                else {draft=store.draft(patient.id);revision++;checked=false;sourceConfirmed=false;message="Checked transcript added. Review the original words before publishing.";true}
                            }
                            catch(error:Exception) {message=error.message ?: "Couldn't add this transcript. Your original recording is unchanged.";false}
                        })
                }
                if(draft.transcribedAt>0) item { Text("Transcript: ${draft.transcriptProvider} / ${draft.transcriptModel} · ${workflowDate(draft.transcribedAt)}",color=Rust,style=MaterialTheme.typography.labelMedium) }
                if(draft.aiModel.isNotBlank()) item { Text("Online AI draft: ${draft.aiProvider} / ${draft.aiModel} · not clinically reviewed",color=Rust,style=MaterialTheme.typography.labelMedium) }
                returnedTranscript?.let { text -> item { WorkflowCard { Text("Returned transcript",fontWeight=FontWeight.SemiBold);Text(text);Text("Your existing work was not silently replaced.",style=MaterialTheme.typography.bodySmall) } } }
                item { OutlinedButton(onClick = { val error=store.loadMockDraft(patient.id); message=error ?: "Sample AI draft loaded from the linked fictional source"; if(error==null) { draft=store.draft(patient.id); revision++ } }, enabled = listOf(draft.sourceText,draft.priorities,draft.participants,draft.topics,draft.questions,draft.undecided,draft.nextSteps).all { it.isBlank() }, modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-load-mock")) { Icon(Icons.Default.AutoAwesome,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Load sample AI draft") }; if(draft.mockGenerated) Text("Sample AI draft · fixture only, not clinically reviewed", color=Rust, style=MaterialTheme.typography.labelMedium) }
                if(enableOnlineAi) item { OutlinedTextField(draft.sourceText, { update(draft.copy(sourceText=it.take(12000))) }, label={Text("Original source text")}, minLines=5, modifier=Modifier.fillMaxWidth().testTag("workflow-source-text")) }
                item { WorkflowSection("Source references") }
                if(draft.transcribedAt>0) item { WorkflowCard { Text("Consented conversation recording",fontWeight=FontWeight.SemiBold);Text("Transcript created ${workflowDate(draft.transcribedAt)} · ${draft.transcriptModel}",style=MaterialTheme.typography.bodySmall);Text("Check the transcript against its original audio before review.",style=MaterialTheme.typography.bodySmall) } }
                else items(sources, key={it.id}) { source -> WorkflowCard { Text(source.label,fontWeight=FontWeight.SemiBold,modifier=Modifier.testTag("workflow-source-${source.id}")); Text(source.text, modifier=Modifier.testTag("workflow-source-text-${source.id}")) } }
                item { WorkflowCard { Text("Uncertainty stays visible", fontWeight=FontWeight.SemiBold); Text(if(draft.aiModel.isNotBlank()) "AI may miss or misattribute words. Verify source evidence, leave unsupported fields blank and keep unresolved questions visible." else "The sample fixture does not state patient priorities or a treatment choice. Do not infer agreement.");draft.aiWarnings.forEach { Text(it,color=Rust,style=MaterialTheme.typography.bodySmall) } } }
                items(draft.evidence.withIndex().toList(),key={"evidence-${it.index}"}) { (_,evidence) -> WorkflowCard { Text("Source evidence · ${evidence.section}",fontWeight=FontWeight.Medium);Text(evidence.quote) } }
                val fields = listOf("priorities" to "What matters most", "participants" to "Who participated", "topics" to "What was discussed", "questions" to "Questions & concerns", "undecided" to "Still undecided", "nextSteps" to "Next steps")
                items(fields, key={it.first}) { (id,label) ->
                    val value = when(id) { "priorities"->draft.priorities; "participants"->draft.participants; "topics"->draft.topics; "questions"->draft.questions; "undecided"->draft.undecided; else->draft.nextSteps }
                    OutlinedTextField(value,{ valueText -> val text=valueText.take(4000); update(when(id) { "priorities"->draft.copy(priorities=text); "participants"->draft.copy(participants=text); "topics"->draft.copy(topics=text); "questions"->draft.copy(questions=text); "undecided"->draft.copy(undecided=text); else->draft.copy(nextSteps=text) }) },label={Text(label)},minLines=2,modifier=Modifier.fillMaxWidth().testTag("workflow-field-$id"))
                }
                item { Button(onClick={moveStep(2)},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text("Continue to review") } }
            }
            else -> {
                item { WorkflowSection("Check the exact draft") }
                item { WorkflowCard { Text("Unverified local preview", fontWeight=FontWeight.SemiBold, color=Rust); Text("Professional verification is not connected. Publishing here creates a sample version, not a verified clinical record.") } }
                item { WorkflowDraftSummary(draft) }
                item { OutlinedTextField(reviewer,{reviewer=it; checked=false},label={Text("Reviewer name (self-entered)")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("workflow-reviewer")) }
                item { WorkflowCheck(sourceConfirmed,{sourceConfirmed=it},"I checked the original source text", "workflow-source-confirmed"); WorkflowCheck(checked,{checked=it},"I reviewed every section and unresolved point", "workflow-review-checked") }
                item { Button(onClick={ val saved=store.saveDraft(draft); val error=saved ?: store.publish(patient.id,reviewer,checked,sourceConfirmed); message=error ?: "Local preview version published"; if(error==null) { revision++; selectedVersion=store.versions(patient.id).first().id; checked=false; sourceConfirmed=false } }, modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("workflow-publish")) { Text("Publish preview version") } }
            }
        }
        item { OutlinedButton(onClick={message=store.saveDraft(draft); if(message==null) onBack()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-finish-later")) { Text("Save & finish later") }; Text("Draft changes are saved on this device.", style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        if(versions.isNotEmpty()) item { WorkflowSection("Version history") }
        items(versions,key={it.id}) { item -> OutlinedButton(onClick={selectedVersion=item.id},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-version-${item.id}")) { Text("Version ${item.number} · ${workflowDate(item.publishedAt)}") } }
        item { FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Records", "Activity").forEach { section -> FilterChip(selected=historySection==section,onClick={historySection=section},label={Text(if(section=="Records") "Records & timeline" else "Activity & updates")},modifier=Modifier.heightIn(min=48.dp).testTag(if(section=="Records") "workflow-open-records" else "workflow-open-activity")) }
        } }
        if(historySection=="Records") {
            items(store.attachments(patient.id),key={it.id}) { attachment -> WorkflowCard { Text(attachment.title,fontWeight=FontWeight.Medium); Text(attachment.text); Text("Sample text source; no uploaded original file.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
            items(store.timeline(patient.id),key={"timeline-${it.id}"}) { entry -> Column { Text(entry.title,fontWeight=FontWeight.Medium); Text(entry.detail); Text(workflowDate(entry.at),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        if(historySection=="Activity") { item { Text("In-app history only; no SMS, push or external delivery.",style=MaterialTheme.typography.bodySmall) }; items(store.activity(patient.id).take(30),key={it.id}) { entry -> Text("${workflowDate(entry.at)}\n${entry.action}",style=MaterialTheme.typography.bodySmall) } }
    }
}

@Composable private fun WorkflowCheck(value: Boolean,onChange:(Boolean)->Unit,text:String,tag:String) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Checkbox(value,onChange,modifier=Modifier.testTag(tag)); Text(text,Modifier.weight(1f)) } }
@Composable private fun WorkflowDraftSummary(draft: WorkflowDraft) { WorkflowCard { listOf("What matters" to draft.priorities,"Participants" to draft.participants,"Discussion" to draft.topics,"Questions" to draft.questions,"Undecided" to draft.undecided,"Next steps" to draft.nextSteps).forEach { (label,text) -> Text(label,fontWeight=FontWeight.SemiBold); Text(text.ifBlank { "Not recorded" }) } } }

@Composable private fun DoctorVersionReader(patient:DoctorDemoPatient,version:WorkflowVersion,store:DoctorWorkflowStore,onBack:()->Unit) {
    var revision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var exported by remember(version.id) { mutableStateOf<File?>(null) }
    val context=LocalContext.current
    val acknowledgements=remember(revision) { store.acknowledgements(version.id) }
    LazyColumn(Modifier.fillMaxSize().testTag("workflow-version-reader"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick=onBack,modifier=Modifier.testTag("workflow-version-back")) { Icon(Icons.Default.ChevronLeft,null); Text("Conversation") }; WorkflowTitle("Care Note · Version ${version.number}"); Text("${patient.name.removeSuffix(" Demo")} · ${workflowDate(version.publishedAt)}",color=Rust); Text(version.reviewLabel,style=MaterialTheme.typography.bodySmall); Text("Reviewer: ${version.reviewerName} (self-entered)",style=MaterialTheme.typography.bodySmall) }
        item { WorkflowDraftSummary(version.draft) }
        item { WorkflowCard { Text("Exact source",fontWeight=FontWeight.SemiBold); Text(version.draft.sourceText); Text(if(version.draft.transcribedAt>0) "Recorded conversation · ${workflowDate(version.draft.transcribedAt)} · ${version.draft.transcriptModel}" else DoctorWorkflowData.sources(patient.id).filter { it.id in version.draft.sourceIds }.joinToString("\n") { it.label }.ifBlank { "Manually entered source" },style=MaterialTheme.typography.bodySmall);if(version.draft.aiModel.isNotBlank())Text("AI draft: ${version.draft.aiProvider} / ${version.draft.aiModel}",style=MaterialTheme.typography.bodySmall);version.draft.aiWarnings.forEach {Text(it,style=MaterialTheme.typography.bodySmall,color=Rust)} } }
        item { WorkflowSection("Preview patient & family responses"); Text("Choose a fictional identity to simulate its own response to this exact version. This is not actual patient consent or external confirmation.",color=MaterialTheme.colorScheme.onSurfaceVariant) }
        items(DoctorWorkflowData.members(patient.id),key={it.id}) { member -> WorkflowCard {
            Text(member.name,fontWeight=FontWeight.SemiBold)
            val ack=acknowledgements.firstOrNull { it.memberId==member.id }
            Text(if(ack==null) "No response to this version" else "${ack.status.name.lowercase().replace('_',' ')} · ${workflowDate(ack.at)}",color=Rust,modifier=Modifier.testTag("workflow-ack-state-${member.id}"))
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { WorkflowAckStatus.entries.forEach { status -> OutlinedButton(onClick={message=store.acknowledge(version.id,member.id,status);revision++},modifier=Modifier.heightIn(min=48.dp).testTag("workflow-ack-${member.id}-${status.name}")) { Text(when(status) { WorkflowAckStatus.READ->"Read"; WorkflowAckStatus.CONCERN->"Concern"; else->"Not ready" }) } } }
        } }
        message?.let { item { Text(it,color=Rust,modifier=Modifier.testTag("workflow-status")) } }
        item { Button(onClick={runCatching { DoctorWorkflowPdf.create(context,version,patient.name.removeSuffix(" Demo")) }.onSuccess { exported=it;message="Preview PDF created on this device" }.onFailure { message="Could not create the preview PDF. Try again." }},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-export-pdf")) { Icon(Icons.Default.PictureAsPdf,null);Spacer(Modifier.width(8.dp));Text("Create preview PDF") } }
        exported?.let { file -> item { Text("${file.name} · ${file.length()/1024} KB",style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("workflow-pdf-ready")); OutlinedButton(onClick={runCatching { DoctorWorkflowPdf.share(context,file) }.onFailure { message="No PDF sharing app is available." }},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("workflow-share-pdf")) { Icon(Icons.Default.Share,null);Spacer(Modifier.width(8.dp));Text("Share preview PDF") } } }
        item { WorkflowCard { Text("Emergency QR retrieval",fontWeight=FontWeight.SemiBold); Text("Secure hospital retrieval is not connected. No QR code or public patient link is created in this local preview.",style=MaterialTheme.typography.bodySmall) } }
    }
}
