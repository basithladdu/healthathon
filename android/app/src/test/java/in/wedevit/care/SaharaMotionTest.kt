package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaharaMotionTest {
    private val specs = listOf(SaharaMotion.ENTER_MS, SaharaMotion.EXIT_MS, SaharaMotion.EXPAND_MS, SaharaMotion.LIST_ITEM_MS,
        SaharaMotion.PRESS_MS, SaharaMotion.SELECT_MS, SaharaMotion.VALUE_MS, SaharaMotion.SAVED_MS)

    @Test fun everySpecCollapsesToZeroWhenAnimationsAreOff() {
        specs.forEach { ms ->
            assertEquals(0, SaharaMotion.ms(ms, enabled = false))
            val spec = SaharaMotion.tween<Float>(ms, delayMillis = 150, enabled = false)
            assertEquals(0, spec.durationMillis); assertEquals(0, spec.delay)
        }
        (0..10).forEach { assertEquals(0, SaharaMotion.staggerDelay(it, enabled = false)) }
        assertNull(SaharaStagger(enabled = false, now = { 0L }).claim())
    }

    @Test fun specsStayShortAndCalmWhenAnimationsAreOn() {
        specs.forEach { assertTrue("$it ms", it in 100..300); assertEquals(it, SaharaMotion.tween<Float>(it, enabled = true).durationMillis) }
        assertEquals(SaharaMotion.STAGGER_ITEMS * SaharaMotion.STAGGER_MS, SaharaMotion.staggerDelay(50, enabled = true))
        assertTrue(SaharaMotion.staggerDelay(50, enabled = true) + SaharaMotion.LIST_ITEM_MS <= 450)
    }

    @Test fun staggerOnlyRunsOnFirstLoad() {
        var clock = 1_000L
        val stagger = SaharaStagger(enabled = true, now = { clock })
        assertEquals(0, stagger.claim()); assertEquals(SaharaMotion.STAGGER_MS, stagger.claim())
        clock += SaharaMotion.STAGGER_WINDOW_MS + 1
        assertNull(stagger.claim())
    }

    @Test fun savedTickOnlyForRealSaves() {
        listOf("Saved", "Care note saved", "Visit saved on this device", "2 medicines saved", "Your confirmation is saved").forEach { assertTrue(it, SaharaMotion.isSavedMessage(it)) }
        listOf("The recording was too short or could not be saved. Please try again.", "Couldn't save the conversation.",
            "Daily check-off saved, but detailed history could not be recorded.", "Care access changed. Reopen this person's records before saving.").forEach { assertFalse(it, SaharaMotion.isSavedMessage(it)) }
    }
}
