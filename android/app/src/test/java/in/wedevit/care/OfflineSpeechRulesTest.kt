package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin

class OfflineSpeechRulesTest {
    @Test fun resamplingPreservesFrequencyAcrossArbitraryDecoderBuffers() {
        val result = mutableListOf<Float>()
        val resampler = OfflinePcmResampler(44_100) { result += it }
        (0 until 44_100).chunked(713).forEach { chunk -> chunk.forEach { frame -> resampler.accept(sin(2 * PI * 440 * frame / 44_100).toFloat()) } }
        assertEquals(16_000, result.size)
        result.forEachIndexed { index, value -> assertEquals(sin(2 * PI * 440 * index / 16_000).toFloat(), value, 0.002f) }
    }
    @Test fun stereoDecoderDownstreamIsBoundedAndRejectsNonFiniteSamples() {
        val result = mutableListOf<Float>()
        val resampler = OfflinePcmResampler(32_000) { result += it }
        repeat(64_000) { resampler.accept(0.25f) }
        assertEquals(32_000, result.size)
        assertTrue(result.all { it == .25f })
        assertThrows(IllegalArgumentException::class.java) { resampler.accept(Float.NaN) }
    }
    @Test fun silenceIsSkippedWithoutTreatingOrdinaryQuietSpeechAsSilence() {
        assertTrue(OfflineSpeechRules.isSilent(FloatArray(16_000)))
        assertFalse(OfflineSpeechRules.isSilent(FloatArray(16_000) { (.01 * sin(2 * PI * 220 * it / 16_000)).toFloat() }))
    }
    @Test fun hindiEnglishReviewRetainsNegationAndMachineOriginal() {
        val original = "मुझे अस्पताल नहीं जाना है। Please call my doctor."
        val source = OfflineTranscriptReview("a".repeat(64), original, original, OfflineSpeechLanguage.MIXED.name, emptyList(), 100)
        assertThrows(IllegalArgumentException::class.java) { source.reviewed(original, false, 200) }
        val reviewed = source.reviewed("मुझे अस्पताल नहीं जाना है। Please call my doctor today.", true, 200)
        assertEquals(original, reviewed.originalText)
        assertTrue(reviewed.text.contains("नहीं"))
        assertEquals(200, reviewed.reviewedAt)
        assertEquals(OfflineSpeechModel.provider, reviewed.provider)
        assertEquals("हिन्दी + English", OfflineSpeechLanguage.MIXED.label)
        assertTrue(OfflineSpeechRules.isHindiEnglishText(original))
        assertFalse(OfflineSpeechRules.isHindiEnglishText("یہاں please nت"))
    }
    @Test fun recordingCannotCrossThePrivateRootPersonOrExactConsentFile() {
        val directory = Files.createTempDirectory("sahara-offline-test").toFile()
        try {
            val privateRoot = directory.resolve("private").apply { mkdirs() }
            val valid = privateRoot.resolve("patient/one.m4a").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2)) }
            val outside = directory.resolve("outside.m4a").apply { writeBytes(byteArrayOf(1, 2)) }
            val consent = RecordingConsent(valid.absolutePath, "patient", "Patient", 100)
            OfflineSpeechRules.requireRecording(valid, privateRoot, consent, "patient")
            assertThrows(IllegalArgumentException::class.java) { OfflineSpeechRules.requireRecording(valid, privateRoot, consent, "another") }
            assertThrows(IllegalArgumentException::class.java) { OfflineSpeechRules.requireRecording(valid, privateRoot, null, "patient") }
            assertThrows(IllegalArgumentException::class.java) { OfflineSpeechRules.requireRecording(outside, privateRoot, consent.copy(audioPath = outside.absolutePath), "patient") }
            val corrupt = OfflineModelFile(valid.name, 2, OfflineSpeechRules.sha256(valid))
            assertTrue(OfflineSpeechRules.matches(valid, corrupt))
            valid.writeBytes(byteArrayOf(3, 4))
            assertFalse(OfflineSpeechRules.matches(valid, corrupt))
        } finally { directory.walkBottomUp().forEach { it.delete() } }
    }
}
