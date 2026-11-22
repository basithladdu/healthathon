package `in`.wedevit.care

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction

private val roleTags = setOf("welcome-patient-family", "welcome-family", "doctor-workspace-entry")

/**
 * A fresh MainActivity opens on the first-run intro pages (CareOnboarding.kt); flows that start at a role card skip them first.
 * The welcome screen appears only once the care records have loaded, so this first waits (up to 20 s) for either the intro's Skip
 * or the role card itself, then presses Skip if it is there and lets the step change settle. No-op once the intro is gone.
 */
internal fun ComposeTestRule.skipSaharaIntroFor(tag: String) {
    if (tag !in roleTags) return
    fun present(t: String) = onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty()
    runCatching { waitUntil(20_000) { present("onboarding-skip") || present(tag) } }
    if (present("onboarding-skip")) {
        runCatching { onAllNodes(hasTestTag("onboarding-skip")).onFirst().performSemanticsAction(SemanticsActions.OnClick) }
        mainClock.advanceTimeBy(1500); waitForIdle()
    }
}
