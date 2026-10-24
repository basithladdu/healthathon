package `in`.wedevit.care

import android.media.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** Synthetic silence encoded locally. Never accesses the microphone or any care database. */
class DoctorLocalAudioFileTest {
    @Test fun finalizedSyntheticAacIsPlayableAndWrongOwnerOrInvalidBytesAreRejected() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"audio-validation-${UUID.randomUUID()}").apply {mkdirs()}
        val file=File(root,"synthetic-person/${UUID.randomUUID()}.m4a").apply {parentFile!!.mkdirs()}
        try {
            encodeSilence(file)
            val info=inspectDoctorLocalAudio(file,root,"synthetic-person")
            assertTrue(info.bytes>100);assertTrue(info.durationMicros>0)
            val player=MediaPlayer()
            try {player.setDataSource(file.absolutePath);player.prepare();assertTrue(player.duration>0)} finally {player.release()}
            assertTrue(runCatching {inspectDoctorLocalAudio(file,root,"another-person")}.isFailure)
            val invalid=File(file.parentFile,"invalid.m4a").apply {writeText("not an AAC container")}
            assertTrue(runCatching {inspectDoctorLocalAudio(invalid,root,"synthetic-person")}.isFailure)
            assertTrue(file.exists());assertTrue(invalid.exists()) // Validation never removes originals.
        } finally {root.deleteRecursively()}
    }
    private fun encodeSilence(file: File) {
        val format=MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,44100,1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE,64000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4096)
        }
        val codec=MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer=MediaMuxer(file.absolutePath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted=false;var track=-1;var frame=0;var inputDone=false;var outputDone=false
        try {
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start()
            val info=MediaCodec.BufferInfo()
            val deadline=System.currentTimeMillis()+10_000
            while(!outputDone && System.currentTimeMillis()<deadline) {
                if(!inputDone) {
                    val i=codec.dequeueInputBuffer(10_000)
                    if(i>=0) {
                        val buffer=codec.getInputBuffer(i)!!;buffer.clear()
                        if(frame<30) {repeat(1024){buffer.putShort(0)};codec.queueInputBuffer(i,0,2048,frame*1024L*1_000_000/44100,0);frame++}
                        else {codec.queueInputBuffer(i,0,0,frame*1024L*1_000_000/44100,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                    }
                }
                when(val index=codec.dequeueOutputBuffer(info,10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{track=muxer.addTrack(codec.outputFormat);muxer.start();muxerStarted=true}
                    else->if(index>=0) {
                        val output=codec.getOutputBuffer(index)!!
                        if(info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0 && info.size>0) {check(muxerStarted);output.position(info.offset);output.limit(info.offset+info.size);muxer.writeSampleData(track,output,info)}
                        outputDone=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                        codec.releaseOutputBuffer(index,false)
                    }
                }
            }
            check(outputDone){"Synthetic AAC encoder did not finish."}
        } finally {runCatching {codec.stop()};codec.release();if(muxerStarted)runCatching {muxer.stop()};muxer.release()}
    }
}
