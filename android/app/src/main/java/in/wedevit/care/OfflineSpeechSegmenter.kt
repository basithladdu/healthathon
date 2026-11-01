package `in`.wedevit.care

import kotlin.math.abs
import kotlin.math.sqrt

data class OfflineSpeechWindow(val startSample: Long, val endSample: Long, val samples: FloatArray)

/** Signal-based phrase boundaries. This does not identify a speaker or language. */
class OfflineSpeechSegmenter(private val minSamples: Int = MIN_SAMPLES, private val maxSamples: Int = MAX_SAMPLES) {
    private val buffer = FloatArray(maxSamples)
    private var size = 0
    private var quiet = 0
    private var endSample = 0L
    private var freshSamples = 0

    /** Accept one bounded PCM frame. No input samples are discarded at a boundary. */
    fun accept(frame: FloatArray): OfflineSpeechWindow? {
        require(frame.size in 1..FRAME_SAMPLES && size + frame.size <= maxSamples)
        require(frame.all { it.isFinite() })
        frame.copyInto(buffer, size)
        size += frame.size
        endSample += frame.size
        freshSamples += frame.size
        val rms = sqrt(frame.sumOf { it.toDouble() * it } / frame.size)
        quiet = if (rms < QUIET_RMS && frame.maxOf { abs(it) } < QUIET_PEAK) quiet + frame.size else 0
        val atPause = size >= minSamples && quiet >= PAUSE_SAMPLES
        if (!atPause && size < maxSamples) return null
        return takeWindow(if (atPause) 0 else OVERLAP_SAMPLES)
    }

    fun finish(): OfflineSpeechWindow? = if (freshSamples > 0) takeWindow(0) else null

    private fun takeWindow(overlap: Int): OfflineSpeechWindow {
        val result = OfflineSpeechWindow(endSample - size, endSample, buffer.copyOf(size))
        // A forced boundary repeats 300 ms for review so an edge word is not silently cut away.
        if (overlap > 0) buffer.copyInto(buffer, 0, size - overlap, size)
        size = overlap
        quiet = 0
        freshSamples = 0
        return result
    }

    companion object {
        const val FRAME_SAMPLES = 320 // 20 ms at 16 kHz
        const val MIN_SAMPLES = 16_000
        const val MAX_SAMPLES = 192_000 // bounded 12 s inference turn
        const val PAUSE_SAMPLES = 4_000 // 250 ms
        const val OVERLAP_SAMPLES = 4_800
        const val QUIET_RMS = 0.003
        const val QUIET_PEAK = 0.03
    }
}
