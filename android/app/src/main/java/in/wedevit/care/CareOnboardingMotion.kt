package `in`.wedevit.care

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/** Holds the last non-null value, so content that is leaving still shows what it showed while it fades out. */
private class OnboardingKept<T : Any> { var value: T? = null }

/**
 * Shows [content] for [value] while it is not null, and slides it open or closed in place instead of popping (an error line,
 * an extra field). The last value stays drawn while it leaves. Nothing moves when animations or Battery Saver are off.
 */
@Composable internal fun <T : Any> OnboardingShowWhen(value: T?, modifier: Modifier = Modifier,
    enter: EnterTransition = SaharaMotion.expand, exit: ExitTransition = SaharaMotion.collapse, content: @Composable (T) -> Unit) {
    val kept = remember { OnboardingKept<T>() }
    if (value != null) kept.value = value
    AnimatedVisibility(value != null, modifier, enter = enter, exit = exit, label = "onboarding show") { kept.value?.let { content(it) } }
}
