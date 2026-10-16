package `in`.wedevit.care

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Local-only preview binding. It has no token, transcriber, upload callback or cloud consent. */
@Composable fun DoctorLocalPreviewCapture(controller:DoctorAudioController,sourceText:String,sourceKind:DoctorTranscriptKind,onSourceEdit:(String)->Unit) {
    require(controller.storageMode==DoctorAudioStorage.LOCAL_PREVIEW)
    val state by controller.state.collectAsState()
    val clip=state.clips.firstOrNull()
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var consentOpen by remember {mutableStateOf(false)}
    var participants by remember {mutableStateOf("")}
    val agreements=remember {mutableStateMapOf<String,Boolean>()}
    var purpose by remember {mutableStateOf(false)}
    var storage by remember {mutableStateOf(false)}
    var access by remember {mutableStateOf(false)}
    val names=RecordingConsentRules.names(participants)
    var approved by remember {mutableStateOf<PreviewRecordingApproval?>(null)}
    DisposableEffect(controller,owner) {
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) {approved=null;consentOpen=false}}
        owner.lifecycle.addObserver(observer)
        onDispose {approved=null;owner.lifecycle.removeObserver(observer)}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        val request=approved;approved=null
        if(granted && request!=null && request.controller===controller && !controller.isClosed() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) request.start()
    }
    // Review annotations belong to this source/clip, never to another recording or patient.
    var coverage by remember(controller,sourceText,clip?.id,state.recording) {mutableStateOf(defaultDoctorCoverage())}
    var reviewing by remember(controller,sourceText,clip?.id,state.recording) {mutableStateOf<Int?>(null)}
    var quote by remember(reviewing) {mutableStateOf("")}
    Text("Local preview—on this device, not linked to a real patient. Use fictional speech. Recording does not upload audio or create a transcript.",style=MaterialTheme.typography.bodySmall)
    if(clip!=null && sourceText.isNotBlank()) Text("Existing text below was not generated from this recording. It is kept unchanged.",color=Rust,style=MaterialTheme.typography.bodySmall)
    DoctorRecordingReview(
        state=DoctorRecordingReviewState(recording=state.recording,paused=state.paused,seconds=state.seconds,levels=state.levels,
            clipLabel=clip?.let {"Local preview recording"},playing=clip!=null && state.playing==clip.id,
            playbackPositionMs=state.playbackPositionMs,durationMs=state.durationMs,sourceText=sourceText,
            sourceKind=sourceKind,coverage=coverage,canRecord=!controller.isClosed(),canTranscribe=false,error=state.error),
        onRecord={agreements.clear();purpose=false;storage=false;access=false;consentOpen=true},
        onPauseResumeRecording=controller::pauseResumeRecording,onStopRecording=controller::stop,
        onPlayPause={clip?.let(controller::togglePlayback)},onSeek={position->clip?.let {controller.seekPlayback(it,position)}},
        onTranscribe={},onSourceEdit=onSourceEdit,onCoverageReview={reviewing=it},
    )
    Text("Goals of Care review selections apply only to this review session and are reset when the source or recording changes.",style=MaterialTheme.typography.bodySmall)
    if(consentOpen) AlertDialog(containerColor=Paper,onDismissRequest={consentOpen=false},title={Text("Before recording")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Every participant must agree. Fictional, non-confidential test conversation only.")
            OutlinedTextField(participants,{participants=it.take(600)},label={Text("Participant names")},supportingText={Text("Separate names with commas or new lines")},modifier=Modifier.testTag("preview-record-participants"))
            names.forEachIndexed {i,name -> PreviewRecordingCheck(agreements[name]==true,{agreements[name]=it},"$name agrees to recording","preview-record-person-$i")}
            PreviewRecordingCheck(purpose,{purpose=it},"We understand this recording is for a local care-conversation preview.","preview-record-purpose")
            PreviewRecordingCheck(storage,{storage=it},"Audio is saved only in this preview's local device storage; no upload or transcription runs.","preview-record-storage")
            PreviewRecordingCheck(access,{access=it},"Anyone with access to this device may access its local recordings.","preview-record-access")
            Text("Maximum 5 minutes, including pauses, or 3 MB. Leaving the app or this conversation stops and saves recording.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick={
        consentOpen=false
        val request=PreviewRecordingApproval(controller,participants,agreements.filterValues {it}.keys.toSet(),purpose,storage,access)
        if(!controller.isClosed() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) request.start()
            else {approved=request;permission.launch(Manifest.permission.RECORD_AUDIO)}
        }
    },enabled=names.isNotEmpty() && names.all {agreements[it]==true} && purpose && storage && access,modifier=Modifier.testTag("preview-record-start")) {Text("Start recording")}},dismissButton={TextButton(onClick={consentOpen=false}) {Text("Cancel")}})
    reviewing?.let {index -> AlertDialog(containerColor=Paper,onDismissRequest={reviewing=null},title={Text(coverage[index].question)},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Manual review only—not an AI judgement. Missing words do not establish that a topic was not discussed.")
            OutlinedTextField(quote,{quote=it.take(2000)},label={Text("Exact source quote (optional)")},minLines=2)
            if(quote.isNotBlank() && !isDoctorSourceQuote(sourceText,quote)) Text("Use one complete, exact line from the current source text.",color=Rust)
            DoctorCoverageStatus.entries.filter {it!=DoctorCoverageStatus.NOT_ASSESSED}.forEach {status ->
                TextButton(onClick={coverage=coverage.mapIndexed {i,item -> if(i==index) item.copy(status=status,quote=quote,manuallyReviewed=true) else item};reviewing=null},enabled=(quote.isBlank() || isDoctorSourceQuote(sourceText,quote)) && (status!=DoctorCoverageStatus.NOT_FOUND_IN_TRANSCRIPT || sourceText.isNotBlank())) {
                    Text(when(status) {DoctorCoverageStatus.REVIEWED_DISCUSSED->"Discussed · manually reviewed";DoctorCoverageStatus.NOT_FOUND_IN_TRANSCRIPT->"Not found in transcript · manually checked";else->"Needs review"})
                }
            }
        }
    },confirmButton={TextButton(onClick={reviewing=null}) {Text("Close")}}) }
}
@Composable private fun PreviewRecordingCheck(value:Boolean,onChange:(Boolean)->Unit,label:String,tag:String) {Row(Modifier.fillMaxWidth()) {Checkbox(value,onChange,modifier=Modifier.testTag(tag));Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)}}
private data class PreviewRecordingApproval(val controller:DoctorAudioController,val participants:String,val agreed:Set<String>,val purpose:Boolean,val storage:Boolean,val access:Boolean) {
    fun start()=controller.start(participants,agreed,purpose,storage,access,cloud=false)
}
