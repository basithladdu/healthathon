package `in`.wedevit.care

import android.animation.ValueAnimator
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.Image
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one motion spec for Sahara. Every screen change (tabs, pushes into a detail screen,
 * going back, entering or leaving a walkthrough) and every in-place change (cards opening,
 * lists appearing, counts changing, a saved tick) uses these so the whole app moves the same way.
 *
 * - [Forward]: the new screen fades in while drifting a short way in from the end edge.
 * - [Back]: the mirror of Forward.
 * - [Switch]: a quiet fade-through, for peer changes such as bottom tabs.
 *
 * Calm by design for elderly users: 110-240 ms, no springs or bounce. When the phone's
 * "Remove animations" setting is on (animator duration scale 0) every duration and delay is 0.
 */
object SaharaMotion {
    const val ENTER_MS = 280
    const val EXIT_MS = 140
    /** The welcome panel rising to show a form, and settling back. */
    const val PANEL_MS = 300
    /** Material's emphasized curve: a quick start that glides to rest, so long moves feel calm rather than abrupt. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val FADE_DELAY_MS = 50
    /** Card expand/collapse and content-size changes. */
    const val EXPAND_MS = 200
    /** One list item appearing on first load; [STAGGER_MS] between items, at most [STAGGER_ITEMS] deep. */
    const val LIST_ITEM_MS = 200
    const val STAGGER_MS = 35
    const val STAGGER_ITEMS = 6
    /** Only items composed this soon after a screen opens are staggered; later ones (scrolling, refreshes) just appear. */
    const val STAGGER_WINDOW_MS = 400L
    /** Press feedback on tappable cards. */
    const val PRESS_MS = 110
    /** Selection colour (tabs, chips, choice rows) and small value changes (counts, chips). */
    const val SELECT_MS = 160
    const val VALUE_MS = 180
    /** The saved tick. */
    const val SAVED_MS = 180
    /** Idle motion: one slow breath in (and the same out), and one shimmer sweep. Both rest after a few cycles. */
    const val BREATHE_MS = 1600
    const val SHIMMER_MS = 1100

    enum class Direction { Forward, Back, Switch }

    /** A whole screen arriving (the per-screen enter in [saharaScreenEnter]). */
    const val SCREEN_MS = 260

    private var power: PowerManager? = null
    /** Called once by the theme so Battery Saver can switch motion off as well. */
    fun bind(context: Context) { if (power == null) power = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager }

    /** True while Battery Saver is on: Sahara then moves nothing, as with "Remove animations". */
    val powerSave: Boolean get() = runCatching { power?.isPowerSaveMode == true }.getOrDefault(false)

    /** False while the system "Remove animations" setting or Battery Saver is on. Read live, so changing either applies at once. */
    val enabled: Boolean get() = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true) && !powerSave

    /** A duration or delay, collapsed to 0 when animations are off. */
    fun ms(base: Int, enabled: Boolean = this.enabled): Int = if (enabled) base else 0

    fun <T> tween(durationMillis: Int, delayMillis: Int = 0, easing: Easing = FastOutSlowInEasing, enabled: Boolean = this.enabled): TweenSpec<T> =
        androidx.compose.animation.core.tween(ms(durationMillis, enabled), ms(delayMillis, enabled), easing)

    /** Delay before list item [index] starts appearing; 0 when animations are off. */
    fun staggerDelay(index: Int, enabled: Boolean = this.enabled): Int = ms(index.coerceIn(0, STAGGER_ITEMS) * STAGGER_MS, enabled)

    /** Saved confirmations get the tick; this is what CareModel and the screens say after a real save. */
    const val SAVED_MARK = "\u200B"

    fun isSavedMessage(text: String): Boolean {
        val t = text.trim()
        if (t.startsWith(SAVED_MARK)) return true // CareModel marks real saves, so the tick survives translation
        if (Regex("""(?i)\b(not|couldn|can't|cannot|failed|unable|but)\b|n't|नहीं""").containsMatchIn(t)) return false
        return t.startsWith("Saved") || t.contains(" saved") || t.contains("सहेज")
    }

    private fun fadeEnter(enabled: Boolean) = fadeIn(tween(ENTER_MS - FADE_DELAY_MS, FADE_DELAY_MS, LinearOutSlowInEasing, enabled))
    private fun fadeExit(enabled: Boolean) = fadeOut(tween(EXIT_MS, enabled = enabled))

    /** Screen-sized content that slides; [rtl] flips the drift so it always follows reading direction. */
    fun transform(direction: Direction, rtl: Boolean = false, enabled: Boolean = this.enabled): ContentTransform {
        val sign = if (rtl) -1 else 1
        val slide = tween<IntOffset>(ENTER_MS, easing = Emphasized, enabled = enabled)
        val (enter, exit) = when (direction) {
            Direction.Forward -> (fadeEnter(enabled) + slideInHorizontally(slide) { sign * it / 12 }) to (fadeExit(enabled) + slideOutHorizontally(slide) { -sign * it / 24 })
            Direction.Back -> (fadeEnter(enabled) + slideInHorizontally(slide) { -sign * it / 24 }) to (fadeExit(enabled) + slideOutHorizontally(slide) { sign * it / 12 })
            Direction.Switch -> fadeEnter(enabled) to fadeExit(enabled)
        }
        // Screens fill the window, so there is no size to animate; skipping it saves a measure pass every frame.
        return ContentTransform(enter, exit, sizeTransform = null)
    }

    /** Loading → empty → content: a plain crossfade, the size following smoothly. */
    fun crossfade(enabled: Boolean = this.enabled): ContentTransform =
        ContentTransform(fadeIn(tween(VALUE_MS, enabled = enabled)), fadeOut(tween(EXIT_MS, enabled = enabled)), sizeTransform = SizeTransform(clip = false) { _, _ -> tween(EXPAND_MS, enabled = enabled) })

    /** A changing number or chip label: the new value rises in a few dp when it grows, drops in when it shrinks. */
    fun valueChange(up: Boolean, enabled: Boolean = this.enabled): ContentTransform {
        val sign = if (up) 1 else -1
        val slide = tween<IntOffset>(VALUE_MS, enabled = enabled)
        return ContentTransform(fadeIn(tween(VALUE_MS, enabled = enabled)) + slideInVertically(slide) { sign * it / 3 },
            fadeOut(tween(EXIT_MS, enabled = enabled)) + slideOutVertically(slide) { -sign * it / 3 }, sizeTransform = SizeTransform(clip = false))
    }

    /** Only the screen that is staying (or arriving) answers system back; a screen still fading out must not catch a quick second press. */
    fun handlesBack(state: EnterExitState): Boolean = state == EnterExitState.Visible

    /** For parts of a screen that appear or disappear in place (banners, panels, loaders). */
    val enter: EnterTransition get() = fadeEnter(enabled) + slideInVertically(tween(ENTER_MS)) { it / 16 }
    val exit: ExitTransition get() = fadeExit(enabled)

    /** A card's hidden section opening and closing. */
    val expand: EnterTransition get() = expandVertically(tween(EXPAND_MS, easing = Emphasized)) + fadeIn(tween(EXPAND_MS))
    val collapse: ExitTransition get() = shrinkVertically(tween(EXPAND_MS - 50, easing = Emphasized)) + fadeOut(tween(EXIT_MS))
}

/** Hands out first-load stagger delays to the cards of one screen. */
class SaharaStagger(private val enabled: Boolean = SaharaMotion.enabled, private val now: () -> Long = { SystemClock.uptimeMillis() }) {
    private val opened = now()
    private var next = 0
    /** The delay for the next card, or null when it should simply appear (animations off, or the first-load window has passed). */
    fun claim(): Int? = if (!enabled || now() - opened > SaharaMotion.STAGGER_WINDOW_MS) null else SaharaMotion.staggerDelay(next++, enabled)
}

/** Each hosted screen gets a fresh stagger, so its cards rise in gently once, when it first opens. */
val LocalSaharaStagger = staticCompositionLocalOf<SaharaStagger?> { null }

/** Gentle first-load appearance: fade in and rise 8 dp, staggered by position. Draw-only, so layout and tests are unaffected. */
@Composable fun Modifier.saharaAppear(): Modifier {
    val stagger = LocalSaharaStagger.current ?: return this
    val delay = remember { stagger.claim() } ?: return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, SaharaMotion.tween(SaharaMotion.LIST_ITEM_MS, delay, LinearOutSlowInEasing)) }
    return graphicsLayer { val p = progress.value; alpha = p; translationY = (1f - p) * 8.dp.toPx() }
}

/** Press feedback for tappable cards: a slight 2% settle while pressed. */
@Composable fun Modifier.saharaPress(interaction: InteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, SaharaMotion.tween(SaharaMotion.PRESS_MS), label = "card press")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Animated content size for cards that grow or shrink (expanding sections, edited text). */
fun Modifier.saharaContentSize(): Modifier = animateContentSize(SaharaMotion.tween(SaharaMotion.EXPAND_MS))

/** Crossfades between loading, empty and content states keyed by [state]. */
@Composable fun <S> SaharaStateSwitch(state: S, modifier: Modifier = Modifier, label: String = "Sahara state", content: @Composable AnimatedContentScope.(S) -> Unit) =
    AnimatedContent(state, modifier, transitionSpec = { SaharaMotion.crossfade() }, contentAlignment = Alignment.TopStart, label = label, content = content)

/** A count or short chip label that changes in place; numbers rise when they grow. */
@Composable fun SaharaAnimatedText(text: String, modifier: Modifier = Modifier, content: @Composable (String) -> Unit) =
    AnimatedContent(text, modifier, transitionSpec = { SaharaMotion.valueChange((targetState.toIntOrNull() ?: 0) >= (initialState.toIntOrNull() ?: 0)) }, label = "Sahara value") { content(it) }

/** Snackbar host whose save confirmations carry the saved tick. Other messages look as before. */
@Composable fun SaharaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) = SnackbarHost(state, modifier) { data ->
    val saved = SaharaMotion.isSavedMessage(data.visuals.message)
    if (!saved) Snackbar(data)
    else Snackbar(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val shown = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS, 80)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS, 80), initialScale = 0.7f)) {
                Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp).testTag("sahara-saved-tick"), tint = Gold)
            }
            Spacer(Modifier.width(10.dp)); Text(data.visuals.message)
        }
    }
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
        val stagger = remember { SaharaStagger() }
        CompositionLocalProvider(LocalSaharaStagger provides stagger) {
            if (backOwner == null || inertBack == null) content(shown)
            else CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides if (SaharaMotion.handlesBack(transition.targetState)) backOwner else inertBack) { content(shown) }
        }
    }
}

/**
 * Calm full-screen loading, branded: the Sahara mark breathing slowly, one plain line ("Opening Rahul's care…") and a
 * bright moving bar. It fades in only if loading lasts long enough to notice; with motion off it is still.
 */
@Composable fun SaharaLoading(text: String, modifier: Modifier = Modifier, background: Color = Cream) {
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Box(modifier.fillMaxSize().background(background).safeDrawingPadding().testTag("sahara-loading"), contentAlignment = Alignment.Center) {
        AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.ENTER_MS, 150))) {
            Column(Modifier.padding(horizontal = 32.dp).a11yCard().announce(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaharaBrandMark(size = 80)
                Text(text, color = Ink, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                SaharaLottie(SaharaLottieKind.Loading)
            }
        }
    }
}

/** The Sahara mark (the app icon) in a circle, breathing gently while something loads. Decorative. */
@Composable fun SaharaBrandMark(modifier: Modifier = Modifier, size: Int = 64) {
    Image(painterResource(R.drawable.ic_care), null, modifier.saharaBreathe(cycles = 40).size(size.dp).shadow(4.dp, CircleShape).clip(CircleShape).clearAndSetSemantics { })
}

/**
 * Skeleton placeholders for a list or a card that is loading: [rows] grey rows (icon, title, detail) in a white card,
 * with a soft shimmer passing over while motion is on. TalkBack hears one "Loading…" progress item.
 */
@Composable fun SaharaSkeleton(modifier: Modifier = Modifier, rows: Int = 3) {
    val busy = careText()("Loading…", "लोड हो रहा है…")
    Surface(modifier.fillMaxWidth().testTag("sahara-skeleton").semantics { contentDescription = busy; progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        shape = RoundedCornerShape(SaharaRadius.Lg), color = SaharaColors.Surface, border = BorderStroke(1.dp, SaharaColors.Line)) {
        Column(Modifier.saharaShimmer(cycles = 60, pauseMs = 300).padding(vertical = 6.dp)) {
            repeat(rows.coerceAtLeast(1)) { index ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(SaharaRadius.Sm)).background(SaharaColors.Well))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth(if (index % 2 == 0) .7f else .55f).height(14.dp).clip(CircleShape).background(SaharaColors.Well))
                        Box(Modifier.fillMaxWidth(.4f).height(10.dp).clip(CircleShape).background(SaharaColors.Well))
                    }
                }
            }
        }
    }
}

/**
 * Idle "breathing" for the one thing that matters on a screen (today's main icon, the loading mark): a slow 4% swell
 * and settle. It rests after [cycles] breaths and never moves when animations or Battery Saver are off.
 */
@Composable fun Modifier.saharaBreathe(cycles: Int = 6, amount: Float = .04f): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(cycles) {
        repeat(cycles) {
            if (!SaharaMotion.enabled) { p.snapTo(0f); return@LaunchedEffect }
            p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.BREATHE_MS, easing = FastOutSlowInEasing))
            p.animateTo(0f, androidx.compose.animation.core.tween(SaharaMotion.BREATHE_MS, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer { val s = 1f + amount * p.value; scaleX = s; scaleY = s }
}

/**
 * A soft light sweep across a pill or card (the "next dose" pill, skeletons): one pass every [pauseMs] plus
 * [SaharaMotion.SHIMMER_MS], [cycles] times, then still. Nothing is drawn when motion is off.
 */
@Composable fun Modifier.saharaShimmer(cycles: Int = 3, pauseMs: Long = 1800): Modifier {
    val p = remember { Animatable(-1f) }
    LaunchedEffect(cycles) {
        repeat(cycles) {
            delay(pauseMs)
            if (!SaharaMotion.enabled) { p.snapTo(-1f); return@LaunchedEffect }
            p.snapTo(0f); p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.SHIMMER_MS, easing = LinearEasing))
        }
        p.snapTo(-1f)
    }
    return drawWithContent {
        drawContent()
        val v = p.value
        if (v in 0f..1f) {
            val band = size.width * .4f; val x = -band + v * (size.width + band)
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .45f), Color.Transparent), start = Offset(x, 0f), end = Offset(x + band, size.height)))
        }
    }
}

/** Thin top progress bar for background work; it waits a moment before appearing so quick refreshes and saves never flash it, and fades in and out. */
@Composable fun SaharaTopProgress(active: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (active) { delay(400); shown = true } else shown = false }
    AnimatedVisibility(shown, modifier, enter = fadeIn(SaharaMotion.tween(SaharaMotion.ENTER_MS)), exit = fadeOut(SaharaMotion.tween(SaharaMotion.ENTER_MS))) {
        val busy = careText()("Working…", "काम चल रहा है…")
        LinearProgressIndicator(Modifier.fillMaxWidth().testTag("sahara-top-progress").semantics { contentDescription = busy; liveRegion = LiveRegionMode.Polite }, color = Rust)
    }
}

/** Shared empty / dead-end state: a bright icon, what is missing in one line, and the one thing to do next (a solid button). */
@Composable fun SaharaEmpty(title: String, detail: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, secondaryLabel: String? = null, onSecondary: (() -> Unit)? = null, tone: SaharaTone = SaharaTone.Rust) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp).saharaScreenEnter().testTag("sahara-empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (icon != null) Box(Modifier.saharaBreathe(cycles = 3).size(72.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(tone.solid), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(36.dp), tint = tone.onSolid) }
        Text(title, fontFamily = SaharaTitleFont, fontSize = 20.sp, lineHeight = 26.sp, color = Ink, textAlign = TextAlign.Center)
        if (detail != null) Text(detail, fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) SaharaPrimaryButton(actionLabel, onAction, tone = tone)
        if (secondaryLabel != null && onSecondary != null) TextButton(onClick = onSecondary, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = tone.ink)) { Text(secondaryLabel, fontWeight = FontWeight.Bold) }
    }
}

/**
 * Per-screen enter: the screen's content fades in and rises 12 dp once, when the screen first opens.
 * Put it on the outermost content of a page (not on the scaffold). Draw-only, so layout and tests are unaffected;
 * nothing moves when animations are off or Battery Saver is on.
 */
@Composable fun Modifier.saharaScreenEnter(): Modifier {
    if (!SaharaMotion.enabled) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, SaharaMotion.tween(SaharaMotion.SCREEN_MS, easing = SaharaMotion.Emphasized)) }
    return graphicsLayer { val p = progress.value; alpha = p; translationY = (1f - p) * 12.dp.toPx() }
}

/** Success tick: a filled circle that pops in with its check (180 ms, no bounce). Static when motion is off. */
@Composable fun SaharaSuccessTick(modifier: Modifier = Modifier, size: Int = 56, tone: SaharaTone = SaharaTone.Success, description: String? = null) {
    val shown = remember { MutableTransitionState(!SaharaMotion.enabled).apply { targetState = true } }
    Box(modifier.size(size.dp).semantics { if (description != null) contentDescription = description }, contentAlignment = Alignment.Center) {
        AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS + 60, easing = SaharaMotion.Emphasized), initialScale = .6f)) {
            Box(Modifier.size(size.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(tone.solid).testTag("sahara-success-tick"), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, Modifier.size((size * .58f).dp), tint = tone.onSolid)
            }
        }
    }
}

/** Determinate progress that glides to each new value; [progress] is 0..1. Indeterminate when null. */
@Composable fun SaharaProgressBar(progress: Float?, modifier: Modifier = Modifier, tone: SaharaTone = SaharaTone.Rust, label: String? = null) {
    val m = modifier.fillMaxWidth().height(8.dp).clip(CircleShape).semantics { if (label != null) { contentDescription = label; liveRegion = LiveRegionMode.Polite } }
    if (progress == null) LinearProgressIndicator(m, color = tone.solid, trackColor = tone.wash)
    else {
        val shown by animateFloatAsState(progress.coerceIn(0f, 1f), SaharaMotion.tween(SaharaMotion.VALUE_MS + 120), label = "progress-glide")
        LinearProgressIndicator(progress = { shown }, modifier = m, color = tone.solid, trackColor = tone.wash, drawStopIndicator = {})
    }
}
