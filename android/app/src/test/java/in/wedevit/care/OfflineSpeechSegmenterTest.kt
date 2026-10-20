package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class OfflineSpeechSegmenterTest {
    private fun segment(samples: FloatArray): List<OfflineSpeechWindow> {
        val planner = OfflineSpeechSegmenter()
        val windows = mutableListOf<OfflineSpeechWindow>()
        samples.toList().chunked(OfflineSpeechSegmenter.FRAME_SAMPLES).forEach { frame ->
            planner.accept(frame.toFloatArray())?.let(windows::add)
        }
        planner.finish()?.let(windows::add)
        return windows
    }

    @Test fun realPauseSeparatesPhrasesAndRetainsTheEntireQuietTail() {
        val samples = FloatArray(32_000) { if (it < 16_000 || it >= 20_160) .1f else 0f }
        val windows = segment(samples)
        assertEquals(2, windows.size)
        assertEquals(0L, windows.first().startSample)
        assertEquals(samples.size.toLong(), windows.last().endSample)
        assertEquals(windows.first().endSample, windows.last().startSample)
        assertArrayEquals(samples, windows.flatMap { it.samples.toList() }.toFloatArray(), 0f)
    }

    @Test fun forcedBoundariesRetainEdgeWordsThroughOverlapWithoutLosingSamples() {
        val samples = FloatArray(400_123) { (it % 1000 + 100).toFloat() / 2000 }
        val windows = segment(samples)
        assertEquals(0L, windows.first().startSample)
        assertEquals(samples.size.toLong(), windows.last().endSample)
        windows.forEach { window ->
            assertTrue(window.samples.size <= OfflineSpeechSegmenter.MAX_SAMPLES)
            assertArrayEquals(samples.copyOfRange(window.startSample.toInt(), window.endSample.toInt()), window.samples, 0f)
        }
        windows.zipWithNext().forEach { (a, b) ->
            assertEquals(OfflineSpeechSegmenter.OVERLAP_SAMPLES.toLong(), a.endSample - b.startSample)
        }
    }

    @Test fun anExactFullTurnDoesNotAppendARepeatedOverlapOnlyWindow() {
        val windows = segment(FloatArray(OfflineSpeechSegmenter.MAX_SAMPLES) { .1f })
        assertEquals(1, windows.size)
        assertEquals(OfflineSpeechSegmenter.MAX_SAMPLES.toLong(), windows.single().endSample)
    }

    @Test fun shortQuietWordWithinAPhraseIsNotDiscardedAsAPause() {
        val samples = FloatArray(28_000) { if (it in 17_000..18_000) 0f else .02f }
        assertEquals(1, segment(samples).size)
    }
}
