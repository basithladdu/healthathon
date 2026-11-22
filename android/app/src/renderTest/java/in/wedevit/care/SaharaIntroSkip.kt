package `in`.wedevit.care

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performSemanticsAction

private val roleTags = setOf("welcome-patient-family", "welcome-family", "doctor-workspace-entry")

/** A fresh MainActivity opens on the first-run intro pages (CareOnboarding.kt); flows that start at a role card skip them first. No-op once skipped. */
internal fun SemanticsNodeInteractionsProvider.skipSaharaIntroFor(tag: String) {
    if (tag !in roleTags) return
    val skip = onAllNodes(hasTestTag("onboarding-skip"))
    if (skip.fetchSemanticsNodes().isNotEmpty()) runCatching { skip.onFirst().performSemanticsAction(SemanticsActions.OnClick) }
}
