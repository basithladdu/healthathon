package `in`.wedevit.care

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one motion spec for moving between screens in Sahara. Every screen change
 * (tabs, pushes into a detail screen, going back, entering or leaving a walkthrough)
 * uses these so the whole app moves the same way.
 *
 * - [Forward]: the new screen fades in while drifting a short way in from the end edge.
 * - [Back]: the mirror of Forward.
 * - [Switch]: a quiet fade-through, for peer changes such as bottom tabs.
 */
object SaharaMotion {
    const val ENTER_MS = 240
    const val EXIT_MS = 120
    const val FADE_DELAY_MS = 50

    enum class Direction { Forward, Back, Switch }

    private fun fadeEnter() = fadeIn(tween(ENTER_MS - FADE_DELAY_MS, delayMillis = FADE_DELAY_MS, easing = LinearOutSlowInEasing))
    private fun fadeExit() = fadeOut(tween(EXIT_MS, easing = FastOutSlowInEasing))

    /** Screen-sized content that slides; [rtl] flips the drift so it always follows reading direction. */
    fun transform(direction: Direction, rtl: Boolean = false): ContentTransform {
        val sign = if (rtl) -1 else 1
        val slide = tween<androidx.compose.ui.unit.IntOffset>(ENTER_MS, easing = FastOutSlowInEasing)
        val (enter, exit) = when (direction) {
            Direction.Forward -> (fadeEnter() + slideInHorizontally(slide) { sign * it / 12 }) to (fadeExit() + slideOutHorizontally(slide) { -sign * it / 24 })
            Direction.Back -> (fadeEnter() + slideInHorizontally(slide) { -sign * it / 24 }) to (fadeExit() + slideOutHorizontally(slide) { sign * it / 12 })
            Direction.Switch -> fadeEnter() to fadeExit()
        }
        return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
    }

    /** Only the screen that is staying (or arriving) answers system back; a screen still fading out must not catch a quick second press. */
    fun handlesBack(state: EnterExitState): Boolean = state == EnterExitState.Visible

    /** For parts of a screen that appear or disappear in place (banners, panels, loaders). */
    val enter: EnterTransition get() = fadeEnter() + slideInVertically(tween(ENTER_MS, easing = FastOutSlowInEasing)) { it / 16 }
    val exit: ExitTransition get() = fadeExit()
}

/**
 * Hosts one screen at a time and animates between them with [SaharaMotion].
 * [direction] decides Forward / Back / Switch from the previous and next target.
 * Keep [target] small and stable (a route string, a tab number, a pair of ids).
 */
@Composable fun <T> SaharaScreenHost(
    target: T,
    modifier: Modifier = Modifier,
    label: String = "Sahara screen",
    direction: (from: T, to: T) -> SaharaMotion.Direction = { _, _ -> SaharaMotion.Direction.Switch },
    content: @Composable AnimatedContentScope.(T) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val backOwner = LocalOnBackPressedDispatcherOwner.current
    // A leaving screen stays composed for the exit animation; its back handlers move to a dispatcher nothing presses, so back goes to the new screen.
    val inertBack = remember(backOwner) { backOwner?.let { owner -> object : OnBackPressedDispatcherOwner { override val lifecycle get() = owner.lifecycle; override val onBackPressedDispatcher = OnBackPressedDispatcher() } } }
    AnimatedContent(target, modifier, transitionSpec = { SaharaMotion.transform(direction(initialState, targetState), rtl) },
        contentAlignment = Alignment.TopStart, label = label) { shown ->
        if (backOwner == null || inertBack == null) content(shown)
        else CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides if (SaharaMotion.handlesBack(transition.targetState)) backOwner else inertBack) { content(shown) }
    }
}

/** Calm full-screen loading: a small progress ring and one line, shown only if loading lasts long enough to notice. */
@Composable fun SaharaLoading(text: String, modifier: Modifier = Modifier, background: Color = Cream) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(modifier.fillMaxSize().background(background).safeDrawingPadding().testTag("sahara-loading"), contentAlignment = Alignment.Center) {
        AnimatedVisibility(shown, enter = fadeIn(tween(SaharaMotion.ENTER_MS, delayMillis = 150))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(Modifier.size(32.dp), color = Rust, strokeWidth = 3.dp)
                Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Thin top progress bar for background work; it waits a moment before appearing so quick refreshes and saves never flash it, and fades in and out. */
@Composable fun SaharaTopProgress(active: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) { delay(400); shown = true } else shown = false }
    AnimatedVisibility(shown, modifier, enter = fadeIn(tween(SaharaMotion.ENTER_MS)), exit = fadeOut(tween(SaharaMotion.ENTER_MS))) {
        LinearProgressIndicator(Modifier.fillMaxWidth().testTag("sahara-top-progress"), color = Rust)
    }
}

/** Shared empty / dead-end state: what is missing, and the one thing to do next. */
@Composable fun SaharaEmpty(title: String, detail: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, secondaryLabel: String? = null, onSecondary: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).testTag("sahara-empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) Icon(icon, null, Modifier.size(36.dp), tint = Rust)
        Text(title, fontFamily = SaharaTitleFont, fontSize = 20.sp, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        if (secondaryLabel != null && onSecondary != null) TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp)) { Text(secondaryLabel) }
    }
}
