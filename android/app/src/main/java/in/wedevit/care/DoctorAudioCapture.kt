@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun rememberDoctorAudioController(patientId:String,accountId:String,storageMode:DoctorAudioStorage=DoctorAudioStorage.LIVE):DoctorAudioController {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val owner=LocalLifecycleOwner.current
    val controller=remember(patientId,accountId,storageMode) { DoctorAudioController(context,patientId,accountId,scope,storageMode) }
    DisposableEffect(controller,owner) {
        val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) controller.release() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer);controller.close() }
    }
    return controller
}

@Composable fun DoctorAudioCapture(controller:DoctorAudioController,signedIn:Boolean,busy:Boolean,sourceIsBlank:Boolean,onSignIn:()->Unit,onTranscribe:(DoctorAudioClip)->Unit) {
    val state by controller.state.collectAsState()
    var consentOpen by rememberSaveable { mutableStateOf(false) }
    var participants by rememberSaveable { mutableStateOf("") }
    val agreements=remember { mutableStateMapOf<String,Boolean>() }
    var purpose by rememberSaveable { mutableStateOf(false) };var storage by rememberSaveable { mutableStateOf(false) };var access by rememberSaveable { mutableStateOf(false) };var cloud by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DoctorAudioClip?>(null) }
    val names=RecordingConsentRules.names(participants)
    val owner=LocalLifecycleOwner.current
    var approved by remember { mutableStateOf<ApprovedDoctorRecording?>(null) }
    DisposableEffect(controller) { onDispose { approved=null } }
    val mic=rememberSaharaPermission(SaharaPermission.Microphone)
    Surface(color=Peach.copy(alpha=.45f),shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().testTag("doctor-live-capture")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Mic,null,tint=Rust);Spacer(Modifier.width(10.dp));Text(if(state.recording) "Recording ${state.seconds/60}:${(state.seconds%60).toString().padStart(2,'0')}" else "Record",fontWeight=FontWeight.SemiBold) }
            if(state.recording) LinearProgressIndicator(progress={state.level.coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=Rust)
            Button(onClick={ if(state.recording) controller.stop() else { agreements.clear();purpose=false;storage=false;access=false;cloud=false;consentOpen=true } },enabled=!busy && (signedIn || state.recording),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("doctor-record-audio")) { Icon(if(state.recording) Icons.Default.Stop else Icons.Default.Mic,null);Spacer(Modifier.width(8.dp));Text(if(state.recording) "Stop & save" else "Record conversation") }
            if(!signedIn) OutlinedButton(onClick=onSignIn,enabled=!busy && !state.recording,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("doctor-ai-sign-in")) { Text("Sign in") }
            state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("doctor-audio-error").announce()) }
            if(busy) { SaharaProgressBar(null);Text("Transcribing…",modifier=Modifier.testTag("doctor-ai-busy"),style=MaterialTheme.typography.bodySmall) }
        }
    }
    state.clips.forEach { clip -> Surface(color=Paper,shape=RoundedCornerShape(20.dp),border=androidx.compose.foundation.BorderStroke(1.dp,DoctorHairline),modifier=Modifier.fillMaxWidth().padding(top=12.dp).testTag("doctor-audio-clip-${clip.id}")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Conversation · "+DateTimeFormatter.ofPattern("d MMM, h:mm a").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(clip.consent.recordingConsentAt)),fontWeight=FontWeight.Medium)
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick={controller.play(clip)},enabled=!state.recording && !busy,modifier=Modifier.testTag("doctor-play-${clip.id}")) { Icon(if(state.playing==clip.id) Icons.Default.Stop else Icons.Default.PlayArrow,if(state.playing==clip.id) "Stop playback" else "Play recording") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick={deleting=clip},enabled=!busy && !state.recording) { Icon(Icons.Default.DeleteOutline,"Delete recording",tint=MaterialTheme.colorScheme.error) }
            }
            if(clip.consent.transcriptionConsentAt>0) Button(onClick={controller.stopPlayback();onTranscribe(clip)},enabled=signedIn && !busy && !state.recording && sourceIsBlank,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("doctor-transcribe-${clip.id}")) { Icon(Icons.Default.AutoAwesome,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Transcribe") }
        }
    } }
    if(consentOpen) AlertDialog(containerColor=Paper,onDismissRequest={consentOpen=false},title={Text("Before recording")},text={
        Column(Modifier.heightIn(max=510.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Everyone must agree.")
            OutlinedTextField(participants,{participants=it.take(600)},label={Text("Names")},modifier=Modifier.testTag("doctor-record-participants"))
            names.forEachIndexed { i,name -> AudioConsentCheck(agreements[name]==true,{agreements[name]=it},"$name agrees to recording","doctor-participant-consent-$i") }
            AudioConsentCheck(purpose,{purpose=it},"We understand this is for preparing and checking a care-conversation note.","doctor-record-purpose")
            AudioConsentCheck(storage,{storage=it},"Audio stays on this device unless separately uploaded or shared.","doctor-record-storage")
            AudioConsentCheck(access,{access=it},"People with access to this device may read or share its local records.","doctor-record-access")
            HorizontalDivider()
            AudioConsentCheck(cloud,{cloud=it},"Everyone separately agrees to upload this fictional test audio to a secure AI service for transcription and AI drafting.","doctor-record-cloud")
            Text("Test audio only. Google may review it. Up to 5 minutes.",style=MaterialTheme.typography.bodySmall)
        }
    },confirmButton={TextButton(onClick={
        consentOpen=false
        val request=ApprovedDoctorRecording(controller,participants,agreements.filterValues {it}.keys.toSet(),purpose,storage,access,cloud)
        if(signedIn && !controller.isClosed() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            approved=request
            mic.request { val r=approved;approved=null; if(signedIn && r!=null && r.controller===controller && !controller.isClosed() && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) r.start() }
        }
    },enabled=signedIn && names.isNotEmpty() && names.all { agreements[it]==true } && purpose && storage && access,modifier=Modifier.testTag("doctor-start-recording")) { Text("Start recording") }},dismissButton={TextButton(onClick={consentOpen=false}) {Text("Cancel")}})
    deleting?.let { clip -> AlertDialog(containerColor=Paper,onDismissRequest={deleting=null},title={Text("Delete this recording?")},text={Text("Written transcripts and published versions are kept.")},confirmButton={TextButton(onClick={controller.remove(clip);deleting=null}){Text("Delete",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deleting=null}){Text("Keep")}}) }
}
@Composable private fun AudioConsentCheck(value:Boolean,onChange:(Boolean)->Unit,label:String,tag:String) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Checkbox(value,onChange,modifier=Modifier.testTag(tag));Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium) } }
private data class ApprovedDoctorRecording(val controller:DoctorAudioController,val participants:String,val agreed:Set<String>,val purpose:Boolean,val storage:Boolean,val access:Boolean,val cloud:Boolean) {
    fun start()=controller.start(participants,agreed,purpose,storage,access,cloud)
}
