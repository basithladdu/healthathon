package `in`.wedevit.care

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder

/** Decode the app's AAC/M4A recording to a bounded 16 kHz mono scratch file, without uploads. */
object OfflineAudioDecoder {
    suspend fun decode(source: File, destination: File, maxSeconds: Int = OfflineSpeechModel.maxSeconds): Long {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("This recording has no audio track.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = requireNotNull(format.getString(MediaFormat.KEY_MIME))
            if (format.containsKey(MediaFormat.KEY_DURATION)) require(format.getLong(MediaFormat.KEY_DURATION) <= maxSeconds * 1_000_000L + 1_000_000) { "Choose a recording of ${maxSeconds / 60} minutes or less, or transcribe a shorter conversation." }
            val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
            decoder.configure(format, null, null, 0); decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outputChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var outputEncoding = AudioFormat.ENCODING_PCM_16BIT
            var frames = 0L
            var resampler: OfflinePcmResampler? = null
            var lastProgress = SystemClock.elapsedRealtime()
            DataOutputStream(BufferedOutputStream(destination.outputStream(), 65_536)).use { output ->
                fun setOutput(next: MediaFormat) {
                    val nextRate = next.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val nextChannels = next.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val nextEncoding = if (next.containsKey(MediaFormat.KEY_PCM_ENCODING)) next.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(nextChannels in 1..8 && nextEncoding in setOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) { "This phone's audio decoder returned an unsupported format. Keep the recording and use dictation or type." }
                    require(frames == 0L || (nextRate == outputRate && nextChannels == outputChannels && nextEncoding == outputEncoding)) { "The recording's audio format changed. Use a shorter recording." }
                    outputRate = nextRate; outputChannels = nextChannels; outputEncoding = nextEncoding
                }
                while (!outputEnded) {
                    currentCoroutineContext().ensureActive()
                    require(SystemClock.elapsedRealtime() - lastProgress < 60_000) { "This recording could not be decoded. Try another recording." }
                    if (!inputEnded) {
                        val index = decoder.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = requireNotNull(decoder.getInputBuffer(index))
                            val count = extractor.readSampleData(buffer, 0)
                            if (count < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEnded = true }
                            else { decoder.queueInputBuffer(index, 0, count, extractor.sampleTime, 0); extractor.advance() }
                            lastProgress = SystemClock.elapsedRealtime()
                        }
                    }
                    when (val index = decoder.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> setOutput(decoder.outputFormat)
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (index >= 0) {
                            try {
                                val buffer = requireNotNull(decoder.getOutputBuffer(index)).order(ByteOrder.LITTLE_ENDIAN)
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                if (resampler == null) resampler = OfflinePcmResampler(outputRate) { sample ->
                                    frames++
                                    require(frames <= maxSeconds.toLong() * OfflineSpeechModel.sampleRate) { "Choose a recording of ${maxSeconds / 60} minutes or less." }
                                    output.writeFloat(sample)
                                }
                                val bytesPerSample = if (outputEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                require(buffer.remaining() % (bytesPerSample * outputChannels) == 0) { "The recording contains an incomplete audio frame." }
                                while (buffer.remaining() >= bytesPerSample * outputChannels) {
                                    var mono = 0f
                                    repeat(outputChannels) { mono += if (outputEncoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f }
                                    resampler!!.accept(mono / outputChannels)
                                }
                                outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                                lastProgress = SystemClock.elapsedRealtime()
                            } finally { decoder.releaseOutputBuffer(index, false) }
                        }
                    }
                }
            }
            require(frames >= OfflineSpeechModel.sampleRate / 2) { "The recording is too short to transcribe." }
            return frames
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; extractor.release()
        }
    }
}
