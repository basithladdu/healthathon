package `in`.wedevit.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CheckInMoodTest {
    @Test fun eachThirdMapsToItsExactTitle() {
        listOf(0f, .1f, .33f).forEach { assertEquals("A hard day", CheckInMood.title(it)) }
        listOf(.34f, .5f, .66f).forEach { assertEquals("An okay day", CheckInMood.title(it)) }
        listOf(.67f, .9f, 1f).forEach { assertEquals("A good day", CheckInMood.title(it)) }
    }

    @Test fun boundariesAreStable() {
        assertEquals("A hard day", CheckInMood.title(Math.nextDown(1f / 3f)))
        assertEquals("An okay day", CheckInMood.title(1f / 3f))
        assertEquals("An okay day", CheckInMood.title(Math.nextDown(2f / 3f)))
        assertEquals("A good day", CheckInMood.title(2f / 3f))
    }

    @Test fun sliderPresetsMatchTheirLabels() {
        assertEquals(CheckInMood.HARD, CheckInMood.title(0f))
        assertEquals(CheckInMood.OKAY, CheckInMood.title(.5f))
        assertEquals(CheckInMood.GOOD, CheckInMood.title(1f))
    }

    @Test fun titlesMatchDoctorReportedMoodVocabulary() {
        assertEquals(DoctorReportedMood.GOOD.title, CheckInMood.GOOD)
        assertEquals(DoctorReportedMood.OKAY.title, CheckInMood.OKAY)
        assertEquals(DoctorReportedMood.HARD.title, CheckInMood.HARD)
        listOf(0f, .5f, 1f).forEach { assertNotNull(doctorMood(CheckInMood.title(it))) }
    }
}
