package `in`.wedevit.care

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * On the very first launch of a fresh install, MainActivity shows the brand moment (CareOnboarding.kt, about 1.8 s) on top of
 * everything, and it takes every touch until it fades. Steps the test clock past it; a no-op once it has been shown.
 * Same idea as the render tests' skipSaharaIntroFor (renderTest/SaharaIntroSkip.kt).
 */
/**
 * Opening a patient in the doctor view can show, once per patient, the severe pain/distress pop-up (DoctorPatientAlerts.kt,
 * pain or distress >= 7/10 in the last 7 days) and then the goals-of-care surprise question. Closes whichever is showing; no-op otherwise.
 */
internal fun ComposeTestRule.closeDoctorPatientPopups() {
    fun present(t: String) = onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty()
    waitForIdle()
    repeat(3) {
        when {
            present("doctor-severe-close") -> onAllNodes(hasTestTag("doctor-severe-close"))[0].performClick()
            present("goc-surprise-close") -> onAllNodes(hasTestTag("goc-surprise-close"))[0].performClick()
            else -> return
        }
        waitForIdle()
    }
}

internal fun ComposeTestRule.skipSaharaBrandMoment() {
    fun showing() = onAllNodes(hasTestTag("sahara-brand-moment")).fetchSemanticsNodes().isNotEmpty()
    repeat(10) { if (showing()) { mainClock.advanceTimeBy(500); waitForIdle() } }
}
