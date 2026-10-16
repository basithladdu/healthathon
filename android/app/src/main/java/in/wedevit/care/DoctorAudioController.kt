package `in`.wedevit.care

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class DoctorAudioClip(val id:String, val file:File, val consent:RecordingConsent)
enum class DoctorAudioStorage { LIVE, LOCAL_PREVIEW }
data class DoctorAudioState(val recording:Boolean=false,val seconds:Int=0,val level:Float=0f,val clips:List<DoctorAudioClip> = emptyList(),val playing:String?=null,val error:String?=null,val paused:Boolean=false,val levels:List<Float> = emptyList(),val playbackPositionMs:Int=0,val durationMs:Int=0)

/** Microphone files and consent live in an isolated account/patient directory, never care Room. */
class DoctorAudioController(context:Context,val patientId:String,val accountId:String="guest",private val scope:CoroutineScope,val storageMode:DoctorAudioStorage=DoctorAudioStorage.LIVE) {
    private val app=context.applicationContext
    private val directory=File(app.filesDir,"${if(storageMode==DoctorAudioStorage.LIVE) "doctor-live-audio" else "doctor-preview-audio"}/${safe(accountId)}/${safe(patientId)}")
    val state=MutableStateFlow(DoctorAudioState())
    private var recorder:MediaRecorder?=null
    private var active:DoctorAudioClip?=null
    private var meter:Job?=null
    private var player:MediaPlayer?=null
    private var playbackJob:Job?=null
    private var playbackClipId:String?=null
    private var pendingSeekClipId:String?=null
    private var pendingSeekMs:Int=0
    private var closed=false
    fun isClosed()=closed
    init { refresh() }
    private fun safe(value:String)=value.also { require(it.matches(Regex("[A-Za-z0-9_-]{1,100}"))) }
    fun start(participants:String,agreed:Set<String>,purpose:Boolean,storage:Boolean,access:Boolean,cloud:Boolean) {
        if(closed || recorder!=null) return
        stopPlayback()
        val file=File(directory,"${UUID.randomUUID()}.m4a")
        val consent=try {
            require(storageMode!=DoctorAudioStorage.LOCAL_PREVIEW || !cloud) { "Local preview recordings cannot authorize cloud processing." }
            RecordingConsentRules.create(patientId,file.absolutePath,participants,agreed,purpose,storage,access,cloud)
        } catch(e:IllegalArgumentException) { state.value=state.value.copy(error=e.message);return }
        directory.mkdirs()
        @Suppress("DEPRECATION") val next=if(android.os.Build.VERSION.SDK_INT>=31) MediaRecorder(app) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC);next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioChannels(1);next.setAudioEncodingBitRate(64000);next.setAudioSamplingRate(32000)
            next.setMaxDuration(299_000);next.setMaxFileSize(ConversationTranscriber.MAX_AUDIO_BYTES)
            next.setOnInfoListener { _,what,_ -> if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stop() }
            next.setOutputFile(file.absolutePath);next.prepare();next.start()
            recorder=next;active=DoctorAudioClip(file.nameWithoutExtension,file,consent);state.value=state.value.copy(recording=true,paused=false,seconds=0,levels=emptyList(),error=null)
            val started=SystemClock.elapsedRealtime()
            meter=scope.launch { while(isActive && !closed) { delay(200);val elapsed=SystemClock.elapsedRealtime()-started
                if(elapsed>=299_000) {stop();break}
                val level=if(state.value.paused) 0f else runCatching { next.maxAmplitude/32767f }.getOrDefault(0f).coerceIn(0f,1f)
                state.value=state.value.copy(seconds=(elapsed/1000).toInt(),level=level,levels=if(state.value.paused) emptyList() else (state.value.levels+level).takeLast(36))
            } }
        } catch(_:Exception) { next.release();file.delete();state.value=state.value.copy(recording=false,error="Microphone could not start. Check permission and try again.") }
    }
    fun stop() {
        val current=recorder ?: return
        recorder=null;meter?.cancel();meter=null
        val clip=active;active=null
        try {
            current.stop()
            require(clip!=null && clip.file.length() in 1..ConversationTranscriber.MAX_AUDIO_BYTES)
            val c=clip.consent
            val json=JSONObject().put("id",clip.id).put("patientId",patientId).put("participants",c.participants).put("recordingConsentAt",c.recordingConsentAt).put("transcriptionConsentAt",c.transcriptionConsentAt).put("noticeVersion",c.noticeVersion)
            val atomic=AtomicFile(File(directory,"${clip.id}.json"));val stream=atomic.startWrite()
            try { stream.write(json.toString().toByteArray());atomic.finishWrite(stream) } catch(e:Exception) { atomic.failWrite(stream);throw e }
            state.value=state.value.copy(error=null)
        } catch(_:Exception) { clip?.file?.delete();state.value=state.value.copy(error="Recording was too short or could not be saved. Try again.") }
        finally { current.release();state.value=state.value.copy(recording=false,paused=false,level=0f,levels=emptyList());refresh() }
    }
    fun pauseResumeRecording() {
        val current=recorder ?: return
        if(closed) return
        try {if(state.value.paused) current.resume() else current.pause();state.value=state.value.copy(paused=!state.value.paused,levels=emptyList(),level=0f)}
        catch(_:Exception) {stop();state.value=state.value.copy(error="Recording could not pause or resume. The recording has been stopped.")}
    }
    fun owns(clip:DoctorAudioClip)=clip.file.canonicalFile.parentFile==directory.canonicalFile && clip.consent.personId==patientId && clip.file.absolutePath==clip.consent.audioPath
    fun play(clip:DoctorAudioClip) {
        require(owns(clip));if(closed || state.value.recording)return
        if(state.value.playing==clip.id) { stopPlayback();return }
        val requestedSeek=if(pendingSeekClipId==clip.id) pendingSeekMs else 0
        stopPlayback()
        val next=MediaPlayer()
        try { next.setDataSource(clip.file.absolutePath);next.setOnCompletionListener { stopPlayback() };next.prepare();val position=requestedSeek.coerceIn(0,next.duration.coerceAtLeast(0));next.seekTo(position);next.start();player=next;playbackClipId=clip.id;state.value=state.value.copy(playing=clip.id,error=null,durationMs=next.duration.coerceAtLeast(0),playbackPositionMs=position)
            playbackJob=scope.launch {while(isActive && !closed && player===next) {delay(200);state.value=state.value.copy(playbackPositionMs=runCatching {next.currentPosition}.getOrDefault(0).coerceIn(0,state.value.durationMs))}}
        }
        catch(_:Exception) { next.runCatching { release() };stopPlayback();state.value=state.value.copy(error="This recording could not be played.") }
    }
    fun togglePlayback(clip:DoctorAudioClip) {
        require(owns(clip));if(closed || state.value.recording)return
        val current=player
        if(current!=null && playbackClipId==clip.id) {
            try {if(current.isPlaying) {current.pause();state.value=state.value.copy(playing=null)} else {current.start();state.value=state.value.copy(playing=clip.id)}}
            catch(_:Exception) {stopPlayback();state.value=state.value.copy(error="This recording could not be played.")}
        } else play(clip)
    }
    fun seekPlayback(positionMs:Int) {val current=player ?: return;if(closed)return;runCatching {current.seekTo(positionMs.coerceIn(0,state.value.durationMs));state.value=state.value.copy(playbackPositionMs=positionMs.coerceIn(0,state.value.durationMs))}}
    fun seekPlayback(clip:DoctorAudioClip,positionMs:Int) {
        require(owns(clip));if(closed || state.value.recording)return
        if(player!=null && playbackClipId==clip.id) {seekPlayback(positionMs);return}
        stopPlayback()
        val duration=clipDuration(clip)
        pendingSeekClipId=clip.id;pendingSeekMs=positionMs.coerceIn(0,duration)
        state.value=state.value.copy(durationMs=duration,playbackPositionMs=pendingSeekMs)
    }
    fun stopPlayback() { playbackJob?.cancel();playbackJob=null;playbackClipId=null;pendingSeekClipId=null;pendingSeekMs=0;val current=player;player=null;current?.run { try {runCatching {stop()}}finally {runCatching {release()}} };state.value=state.value.copy(playing=null,playbackPositionMs=0) }
    fun remove(clip:DoctorAudioClip) { require(owns(clip));if(state.value.recording)return;stopPlayback();val removed=clip.file.delete();if(removed) File(directory,"${clip.id}.json").delete();state.value=state.value.copy(error=if(removed) null else "Recording could not be removed.");refresh() }
    fun release() { stop();stopPlayback();meter?.cancel() }
    fun close() { closed=true;release() }
    private fun refresh() {
        val clips=directory.listFiles()?.filter { it.extension=="json" }?.mapNotNull { metadata -> runCatching {
            val j=JSONObject(metadata.readText());val id=j.getString("id");require(id.matches(Regex("[A-Za-z0-9-]+")) && j.getString("patientId")==patientId)
            val file=File(directory,"$id.m4a");require(file.isFile)
            DoctorAudioClip(id,file,RecordingConsent(file.absolutePath,patientId,j.getString("participants"),j.getLong("recordingConsentAt"),j.getLong("transcriptionConsentAt"),j.getInt("noticeVersion")))
        }.getOrNull() }?.sortedByDescending { it.consent.recordingConsentAt } ?: emptyList()
        val duration=clips.firstOrNull()?.let(::clipDuration) ?: 0
        state.value=state.value.copy(clips=clips,durationMs=duration)
    }
    private fun clipDuration(clip:DoctorAudioClip):Int {val metadata=MediaMetadataRetriever();return try {metadata.setDataSource(clip.file.absolutePath);metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()?.coerceAtLeast(0) ?: 0} catch(_:Exception) {0} finally {runCatching {metadata.release()}}}
}
