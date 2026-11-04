package `in`.wedevit.care

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareGocDiarizeTest {
    private fun turn(start: Float, end: Float, speaker: Int) = GocSpeakerTurn(start, end, speaker)
    private fun voice(part: Int, local: Int, vararg v: Float) = GocLocalSpeaker(part, local, v)

    @Test fun parts_splitLongAudioAndFoldAShortTail() {
        assertEquals(listOf(GocDiarizePart(0, 50)), gocDiarizeParts(50, 100, 20))
        assertEquals(listOf(GocDiarizePart(0, 100), GocDiarizePart(100, 200)), gocDiarizeParts(200, 100, 20))
        // 210 leaves a 10-sample tail, shorter than 20, so it joins the last part.
        assertEquals(listOf(GocDiarizePart(0, 100), GocDiarizePart(100, 210)), gocDiarizeParts(210, 100, 20))
        assertEquals(listOf(GocDiarizePart(0, 100), GocDiarizePart(100, 200), GocDiarizePart(200, 230)), gocDiarizeParts(230, 100, 20))
        assertTrue(gocDiarizeParts(0, 100, 20).isEmpty())
    }

    @Test fun parts_ninetyMinutesStaysWithinTwelveMinuteParts() {
        val total = 90L * 60 * GocDiarizeModel.sampleRate
        val parts = gocDiarizeParts(total, GOC_DIARIZE_PART_SAMPLES, GOC_DIARIZE_MIN_TAIL_SAMPLES)
        assertEquals(9, parts.size)
        assertEquals(total, parts.last().end)
        assertTrue(parts.all { it.end - it.start <= GOC_DIARIZE_PART_SAMPLES + GOC_DIARIZE_MIN_TAIL_SAMPLES })
        parts.zipWithNext().forEach { (a, b) -> assertEquals(a.end, b.start) }
    }

    @Test fun cleanSpans_dropOverlappedSpeechAndKeepLongestFirst() {
        val own = listOf(0f to 5f, 10f to 30f)
        val others = listOf(3f to 4f, 12f to 13f)
        assertEquals(listOf(13f to 23f, 0f to 3f, 10f to 12f, 4f to 5f), gocCleanSpans(own, others, minSec = 1f, maxEachSec = 10f, maxTotalSec = 30f))
        // The total is capped.
        assertEquals(listOf(13f to 23f, 0f to 2f), gocCleanSpans(own, others, minSec = 1f, maxEachSec = 10f, maxTotalSec = 12f))
    }

    @Test fun cleanSpans_fallBackToLongestTurnWhenAllOverlaps() {
        assertEquals(listOf(0f to 2f), gocCleanSpans(listOf(0f to 2f, 5f to 5.5f), listOf(0f to 6f)))
        assertTrue(gocCleanSpans(listOf(0f to 0.5f), listOf(0f to 1f)).isEmpty())
    }

    @Test fun cosineDistance_matchesSherpaDefinition() {
        assertEquals(0f, gocCosineDistance(floatArrayOf(1f, 0f), floatArrayOf(2f, 0f)), 1e-6f)
        assertEquals(1f, gocCosineDistance(floatArrayOf(1f, 0f), floatArrayOf(0f, 3f)), 1e-6f)
        assertEquals(2f, gocCosineDistance(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 1e-6f)
        assertEquals(2f, gocCosineDistance(null, floatArrayOf(1f)), 0f)
        assertNull(gocUnit(floatArrayOf(0f, 0f)))
        assertNull(gocUnit(floatArrayOf(Float.NaN, 1f)))
    }

    @Test fun link_joinsTheSameVoiceAcrossParts() {
        // Part 0: A then B. Part 1: B' then A'. A ~ (1,0), B ~ (0,1).
        val voices = listOf(voice(0, 0, 1f, 0.05f), voice(0, 1, 0.05f, 1f), voice(1, 0, 0.1f, 1f), voice(1, 1, 1f, 0.1f))
        assertArrayEquals(intArrayOf(0, 1, 1, 0), gocLinkSpeakers(voices, 0.5f, null))
    }

    @Test fun link_neverJoinsTwoSpeakersOfOnePartByThreshold() {
        // Within one part the two were told apart already, even if their prints look alike.
        val voices = listOf(voice(0, 0, 1f, 0f), voice(0, 1, 0.99f, 0.1f))
        assertArrayEquals(intArrayOf(0, 1), gocLinkSpeakers(voices, 0.5f, null))
    }

    @Test fun link_aNewVoiceStaysNew() {
        val voices = listOf(voice(0, 0, 1f, 0f, 0f), voice(0, 1, 0f, 1f, 0f), voice(1, 0, 0f, 0f, 1f), voice(1, 1, 1f, 0f, 0.05f))
        assertArrayEquals(intArrayOf(0, 1, 2, 0), gocLinkSpeakers(voices, 0.5f, null))
    }

    @Test fun link_doctorsNumberCapsTheSpeakers() {
        val voices = listOf(voice(0, 0, 1f, 0f, 0f), voice(0, 1, 0f, 1f, 0f), voice(1, 0, 0f, 0f, 1f), voice(1, 1, 0.6f, 0.2f, 0.7f))
        val free = gocLinkSpeakers(voices, 0.3f, null)
        assertEquals(4, free.distinct().size)
        val two = gocLinkSpeakers(voices, 0.3f, 2)
        assertEquals(2, two.distinct().size)
        // Even a part found with too many speakers gets down to the number given.
        val crowded = listOf(voice(0, 0, 1f, 0f), voice(0, 1, 0f, 1f), voice(0, 2, 0.7f, 0.7f))
        assertEquals(2, gocLinkSpeakers(crowded, 0.5f, 2).distinct().size)
    }

    @Test fun link_missingPrintsStaySeparateUnlessForced() {
        val voices = listOf(voice(0, 0, 1f, 0f), GocLocalSpeaker(1, 0, null))
        assertArrayEquals(intArrayOf(0, 1), gocLinkSpeakers(voices, 0.5f, null))
        assertArrayEquals(intArrayOf(0, 0), gocLinkSpeakers(voices, 0.5f, 1))
        assertEquals(0, gocLinkSpeakers(emptyList(), 0.5f, null).size)
    }

    @Test fun numbering_followsWhoSpeaksFirst() {
        val turns = listOf(turn(5f, 6f, 3), turn(0f, 2f, 7), turn(3f, 4f, 3), turn(7f, 8f, 1))
        assertEquals(listOf(turn(0f, 2f, 0), turn(3f, 4f, 1), turn(5f, 6f, 1), turn(7f, 8f, 2)), gocNumberByFirstTurn(turns))
    }

    @Test fun merge_joinsSameSpeakerAcrossSmallGapsAndPartEdges() {
        val turns = listOf(turn(0f, 2f, 0), turn(2.3f, 4f, 0), turn(4.1f, 6f, 1), turn(7f, 8f, 1), turn(8f, 8f, 0), turn(599.5f, 600f, 1), turn(600f, 602f, 1))
        assertEquals(listOf(turn(0f, 4f, 0), turn(4.1f, 6f, 1), turn(7f, 8f, 1), turn(599.5f, 602f, 1)), gocMergeTurns(turns, 0.5f))
    }

    @Test fun merge_keepsOverlappingSpeech() {
        val turns = listOf(turn(0f, 5f, 0), turn(4f, 6f, 1), turn(5.5f, 7f, 0))
        assertEquals(turns, gocMergeTurns(turns, 0.5f))
    }

    @Test fun span_takesTheSpeakerWithMostOverlap() {
        val turns = listOf(turn(0f, 4f, 0), turn(4f, 10f, 1), turn(12f, 13f, 0), turn(12.5f, 15f, 1))
        assertEquals(0, gocSpeakerForSpan(0f, 3f, turns))
        assertEquals(1, gocSpeakerForSpan(3f, 9f, turns))
        assertEquals(1, gocSpeakerForSpan(11f, 16f, turns))
        assertNull(gocSpeakerForSpan(10.2f, 11.8f, turns))
        // A tie goes to who started first in the span.
        assertEquals(0, gocSpeakerForSpan(2f, 6f, turns))
        // A moment takes the turn that contains it.
        assertEquals(1, gocSpeakerForSpan(5f, 5f, turns))
        assertNull(gocSpeakerForSpan(11f, 11f, turns))
    }

    @Test fun segments_mapTranscriptLinesToSpeakers() {
        val turns = listOf(turn(0f, 30f, 0), turn(30f, 45f, 1))
        val segments = listOf(
            OfflineTranscriptSegment(0, 20_000, "How is he sleeping?", "en"),
            OfflineTranscriptSegment(28_000, 44_000, "Not well at night.", "en"),
            OfflineTranscriptSegment(50_000, 55_000, "", ""),
        )
        assertEquals(listOf(0, 1, null), gocSpeakersForSegments(segments, turns))
    }

    @Test fun modelFiles_arePinnedAndChecked() {
        assertEquals(2, GocDiarizeModel.files.size)
        assertTrue(GocDiarizeModel.files.all { it.sha256.matches(Regex("[0-9a-f]{64}")) && it.bytes > 1_000_000 })
        assertEquals("https://huggingface.co/csukuangfj/sherpa-onnx-pyannote-segmentation-3-0/resolve/9403a6902bb58e3d5ae8c7e77c3422de279db2e0/model.onnx", GocDiarizeModel.url(GocDiarizeModel.segmentation))
        assertTrue(GocDiarizeModel.url(GocDiarizeModel.embedding).startsWith("https://huggingface.co/csukuangfj/speaker-embedding-models/resolve/0743f301363dec56491a490f6d6cbc9d67f9a3bf/"))
        assertEquals(45_586_674L, GOC_DIARIZE_DOWNLOAD_BYTES)
    }
}
