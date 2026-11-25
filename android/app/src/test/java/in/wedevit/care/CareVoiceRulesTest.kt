package `in`.wedevit.care

import org.junit.Assert.*
import org.junit.Test

class CareVoiceRulesTest {
    @Test fun micHiddenBelowAndroid12() {
        assertFalse(CareVoiceRules.micShown(30, onDeviceAvailable = true))
        assertFalse(CareVoiceRules.micShown(26, onDeviceAvailable = true))
    }
    @Test fun micHiddenWithoutOnDeviceRecognition() { assertFalse(CareVoiceRules.micShown(34, onDeviceAvailable = false)) }
    @Test fun micShownOnAndroid12WithOnDevice() { assertTrue(CareVoiceRules.micShown(31, onDeviceAvailable = true)) }
    @Test fun micOffWhenFieldTurnsVoiceOff() { assertFalse(CareVoiceRules.micShown(34, onDeviceAvailable = true, wanted = false)) }

    @Test fun wordsWhileRecordingNeedsAndroid13() {
        assertFalse(CareVoiceRules.writesWordsWhileRecording(32, true))
        assertTrue(CareVoiceRules.writesWordsWhileRecording(33, true))
        assertFalse(CareVoiceRules.writesWordsWhileRecording(35, false))
    }

    @Test fun appendIntoEmptyCapitalises() { assertEquals("Pain in my back", CareVoiceRules.append("", "pain in my back")) }
    @Test fun appendAfterWordsAddsOneSpace() { assertEquals("I slept badly and woke at night", CareVoiceRules.append("I slept badly", " and   woke at night ")) }
    @Test fun appendAfterSentenceStartsNewSentence() { assertEquals("Slept well. Ate lunch", CareVoiceRules.append("Slept well.", "ate lunch")) }
    @Test fun appendAfterNewLineKeepsTheLine() { assertEquals("Morning\nTired", CareVoiceRules.append("Morning\n", "tired")) }
    @Test fun appendBlankWordsChangesNothing() { assertEquals("Hello ", CareVoiceRules.append("Hello ", "   ")) }
    @Test fun appendNeverPassesTheLimit() { assertEquals(10, CareVoiceRules.append("123456789", "abcdef", max = 10).length) }
    @Test fun appendHindiAfterDanda() { assertEquals("दर्द है। नींद नहीं आई", CareVoiceRules.append("दर्द है।", "नींद नहीं आई")) }

    @Test fun languageTagFollowsTheScreen() {
        assertEquals("hi-IN", CareVoiceRules.languageTag(CareLang.HI))
        assertEquals("te-IN", CareVoiceRules.languageTag(CareLang.TE))
        assertEquals("kn-IN", CareVoiceRules.languageTag(CareLang.KN))
        assertEquals("ur-IN", CareVoiceRules.languageTag(CareLang.UR))
        assertEquals("en-IN", CareVoiceRules.languageTag(CareLang.EN))
    }

    @Test fun clockShowsMinutesAndSeconds() {
        assertEquals("0:00", CareVoiceRules.clock(0))
        assertEquals("0:09", CareVoiceRules.clock(9_400))
        assertEquals("1:05", CareVoiceRules.clock(65_000))
        assertEquals("3:00", CareVoiceRules.clock(180_000))
        assertEquals("0:00", CareVoiceRules.clock(-5))
    }

    @Test fun silenceIsDetected() { assertTrue(CareVoiceRules.silent(10)); assertFalse(CareVoiceRules.silent(4000)) }

    @Test fun safeNameDropsPathCharacters() {
        assertEquals("abetcpasswd", CareVoiceRules.safeName("a/b/../etc/passwd").replace("-", ""))
        assertFalse(CareVoiceRules.safeName("../../x").contains('.'))
        assertEquals("x", CareVoiceRules.safeName("///"))
        assertEquals("0b1f-2c_9", CareVoiceRules.safeName("0b1f-2c_9"))
    }

    @Test fun wavHeaderDescribes16kMonoPcm() {
        val h = CareVoiceRules.wavHeader(32_000)
        val b = java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + 32_000, b.getInt(4))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(16_000, b.getInt(24))
        assertEquals(32_000, b.getInt(28)) // byte rate
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))
        assertEquals(32_000, b.getInt(40))
    }
}
