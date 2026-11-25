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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.core.spring
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import kotlinx.coroutines.Job

/**
 * Sahara's one spring vocabulary, taken from Material 3's standard motion scheme (not the expressive one, which overshoots
 * too much for older patients). Spatial springs move things (damping 0.9: no visible overshoot), effects springs change
 * alpha, colour and fills (damping 1: never past the target). [gentle] is the one soft settle allowed for small
 * celebrations. Every token is a snap when motion is off (Remove animations or Battery Saver). Springs keep their speed when
 * a new target arrives mid-move, so a quick second tap retargets smoothly instead of restarting.
 */
object SaharaSprings {
    fun <T> fastSpatial(visibilityThreshold: T? = null, enabled: Boolean = SaharaMotion.enabled): FiniteAnimationSpec<T> =
        if (enabled) spring(0.9f, 1400f, visibilityThreshold) else snap()
    fun <T> spatial(visibilityThreshold: T? = null, enabled: Boolean = SaharaMotion.enabled): FiniteAnimationSpec<T> =
        if (enabled) spring(0.9f, 700f, visibilityThreshold) else snap()
    fun <T> slowSpatial(visibilityThreshold: T? = null, enabled: Boolean = SaharaMotion.enabled): FiniteAnimationSpec<T> =
        if (enabled) spring(0.9f, 300f, visibilityThreshold) else snap()
    fun <T> fastEffects(visibilityThreshold: T? = null, enabled: Boolean = SaharaMotion.enabled): FiniteAnimationSpec<T> =
        if (enabled) spring(1f, 3800f, visibilityThreshold) else snap()
    fun <T> effects(visibilityThreshold: T? = null, enabled: Boolean = SaharaMotion.enabled): FiniteAnimationSpec<T> =
        if (enabled) spring(1f, 1600f, visibilityThreshold) else snap()
    fun <T> slowEffects(visibilityThreshold: T? = null, enabled: Boolean = SaharaMotion.enabled): FiniteAnimationSpec<T> =
        if (enabled) spring(1f, 800f, visibilityThreshold) else snap()
    /** The one soft settle for small celebrations (a hug, a chosen mood): a barely visible give, never a wobble. */
    fun <T> gentle(visibilityThreshold: T? = null, enabled: Boolean = SaharaMotion.enabled): FiniteAnimationSpec<T> =
        if (enabled) spring(0.8f, 380f, visibilityThreshold) else snap()
}

/**
 * The one motion spec for Sahara. Every screen change (tabs, pushes into a detail screen,
 * going back, entering or leaving a walkthrough) and every in-place change (cards opening,
 * lists appearing, counts changing, a saved tick) uses these so the whole app moves the same way.
 *
 * - [Forward]: the new screen fades in while drifting a short way in from the end edge.
 * - [Back]: the mirror of Forward.
 * - [Switch]: a true fade-through (the old screen is gone before the new one fades in), for peer changes such as bottom tabs.
 *
 * Calm by design for elderly users: 90-280 ms tweens on Material's emphasized curves, and only critically damped springs
 * ([SaharaSprings]) with no visible bounce. When the phone's "Remove animations" setting or Battery Saver is on, every
 * duration and delay is 0.
 */
object SaharaMotion {
    const val ENTER_MS = 280
    /** Exits are shorter than enters (Material): the old content gets out of the way before the new one arrives. */
    const val EXIT_MS = 90
    /** The welcome panel rising to show a form, and settling back. */
    const val PANEL_MS = 300
    /** Material's emphasized curve: a quick start that glides to rest, so long moves feel calm rather than abrupt. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Material's emphasized decelerate (things arriving) and accelerate (things leaving) curves. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** The predictive back swipe (Material's standard decelerate): the screen answers the first millimetres of the thumb. */
    val BackEasing: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    /** The incoming screen waits until the outgoing one has faded ([EXIT_MS]), so two pages are never seen on top of each other. */
    const val FADE_DELAY_MS = 90
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
    /** [saharaStagger]: one step between list items, and the deepest position that still waits (later items rise with the last one). */
    const val STAGGER_STEP_MS = 30
    const val STAGGER_CAP = 8
    /** Idle float: one slow bob up and back. Pulse glow: one ring spreading out. Count-up and fill-up from zero. */
    const val FLOAT_MS = 3600
    const val GLOW_MS = 1400
    const val COUNT_MS = 700
    const val FILL_MS = 600

    enum class Direction { Forward, Back, Switch }

    /** A whole screen arriving (the per-screen enter in [saharaScreenEnter]). */
    const val SCREEN_MS = 260

    private var power: PowerManager? = null
    private var bound = false
    // Cached as snapshot state: reading them costs nothing (no system call per read), and a change recomposes what reads them.
    private val animatorsOn = mutableStateOf(runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true))
    private val saverOn = mutableStateOf(false)

    /**
     * Called once by the theme so Battery Saver can switch motion off as well. Listens for Battery Saver changes and (Android 13+)
     * for the "Remove animations" scale changing, so either applies at once; [refresh] covers older phones on resume.
     */
    fun bind(context: Context) {
        if (bound) return
        bound = true
        val app = context.applicationContext
        power = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        refresh()
        runCatching {
            // A protected system broadcast, so no export flag is needed.
            app.registerReceiver(object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) { refresh() } },
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }
        if (Build.VERSION.SDK_INT >= 33) runCatching { ValueAnimator.registerDurationScaleChangeListener { scale -> animatorsOn.value = scale != 0f } }
    }

    /** Re-reads "Remove animations" and Battery Saver. Call from the activity's onResume (the scale listener needs Android 13). */
    fun refresh() {
        val animators = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        val saver = runCatching { power?.isPowerSaveMode == true }.getOrDefault(false)
        if (animatorsOn.value != animators) animatorsOn.value = animators
        if (saverOn.value != saver) saverOn.value = saver
    }

    /** True while Battery Saver is on: Sahara then moves nothing, as with "Remove animations". */
    val powerSave: Boolean get() = saverOn.value

    /** False while the system "Remove animations" setting or Battery Saver is on. Observable state, so changing either applies at once. */
    val enabled: Boolean get() = animatorsOn.value && !saverOn.value

    /** A duration or delay, collapsed to 0 when animations are off. */
    fun ms(base: Int, enabled: Boolean = this.enabled): Int = if (enabled) base else 0

    fun <T> tween(durationMillis: Int, delayMillis: Int = 0, easing: Easing = Emphasized, enabled: Boolean = this.enabled): TweenSpec<T> =
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

    private fun fadeEnter(enabled: Boolean) = fadeIn(tween(ENTER_MS - FADE_DELAY_MS, FADE_DELAY_MS, EmphasizedDecelerate, enabled))
    private fun fadeExit(enabled: Boolean) = fadeOut(tween(EXIT_MS, easing = EmphasizedAccelerate, enabled = enabled))

    /** Screen-sized content that slides; [rtl] flips the drift so it always follows reading direction. */
    fun transform(direction: Direction, rtl: Boolean = false, enabled: Boolean = this.enabled): ContentTransform {
        val sign = if (rtl) -1 else 1
        val slide = tween<IntOffset>(ENTER_MS, easing = Emphasized, enabled = enabled)
        val (enter, exit) = when (direction) {
            Direction.Forward -> (fadeEnter(enabled) + slideInHorizontally(slide) { sign * it / 12 }) to (fadeExit(enabled) + slideOutHorizontally(slide) { -sign * it / 24 })
            Direction.Back -> (fadeEnter(enabled) + slideInHorizontally(slide) { -sign * it / 24 }) to (fadeExit(enabled) + slideOutHorizontally(slide) { sign * it / 12 })
            // Fade-through: the old tab fades out first, then the new one fades in while settling from 96% (quieter than Material's 92%).
            Direction.Switch -> (fadeEnter(enabled) + scaleIn(tween(ENTER_MS - FADE_DELAY_MS, FADE_DELAY_MS, EmphasizedDecelerate, enabled), initialScale = .96f)) to fadeExit(enabled)
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
    val collapse: ExitTransition get() = shrinkVertically(tween(EXPAND_MS - 50, easing = EmphasizedAccelerate)) + fadeOut(tween(EXIT_MS, easing = EmphasizedAccelerate))

    /** Back-commit: the swiped-away card fades from where the thumb left it (no further slide); the screen below fades in. */
    fun backCommit(enabled: Boolean = this.enabled): ContentTransform = ContentTransform(fadeEnter(enabled), fadeExit(enabled), sizeTransform = null)
}

/** Hands out first-load stagger delays to the cards of one screen. */
class SaharaStagger(private val enabled: Boolean = SaharaMotion.enabled, private val now: () -> Long = { SystemClock.uptimeMillis() }) {
    private val opened = now()
    private var next = 0
    /** The delay for the next card, or null when it should simply appear (animations off, or the first-load window has passed). */
    fun claim(): Int? = if (!enabled || now() - opened > SaharaMotion.STAGGER_WINDOW_MS) null else SaharaMotion.staggerDelay(next++, enabled)
    /** True while the screen is still in its first-load window (so items composed later by scrolling just appear). */
    val fresh: Boolean get() = enabled && now() - opened <= SaharaMotion.STAGGER_WINDOW_MS
}

/** Each hosted screen gets a fresh stagger, so its cards rise in gently once, when it first opens. */
val LocalSaharaStagger = staticCompositionLocalOf<SaharaStagger?> { null }

/**
 * Gentle first-composition appearance: fade in and rise 8 dp, once per item. Staggered by position while the screen is still
 * opening (first [SaharaMotion.STAGGER_WINDOW_MS]); items composed later inside a host (scrolled in, prefetched) simply
 * appear, while outside a host they still fade in without delay. The flag survives rotation and tab switches, so a card never replays. Draw-only; nothing when motion is off.
 */
@Composable fun Modifier.saharaAppear(): Modifier {
    if (!SaharaMotion.enabled) return this
    val stagger = LocalSaharaStagger.current
    var played by rememberSaveable { mutableStateOf(false) }
    if (played) return this
    // Rows composed after the first-load window (scrolled in, or prefetched just before) simply appear, never half-faded mid-fling.
    val run = remember { stagger?.fresh ?: true }
    if (!run) return this
    val delay = remember { stagger?.claim() ?: 0 }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, SaharaMotion.tween(SaharaMotion.LIST_ITEM_MS, delay, SaharaMotion.EmphasizedDecelerate)); played = true }
    // ModulateAlpha: no offscreen buffer per card, and the card's shadow is not clipped while it fades.
    return graphicsLayer { val p = progress.value; alpha = p; translationY = (1f - p) * 8.dp.toPx(); compositingStrategy = CompositingStrategy.ModulateAlpha }
}

/**
 * Press feedback for tappable cards, tiles and buttons: a slight 3% settle while pressed and a calm spring back on release
 * (no overshoot). A modifier node that watches [interaction] itself, so a press never recomposes the card; the scale is
 * applied in the layer only, so the tap itself is never delayed. Instant when motion is off.
 */
fun Modifier.saharaPress(interaction: InteractionSource, pressedScale: Float = SAHARA_PRESS_SCALE): Modifier = this then SaharaPressElement(interaction, pressedScale)

/** The one press depth used app-wide (cards, tiles, big buttons). */
const val SAHARA_PRESS_SCALE = .97f

private data class SaharaPressElement(val source: InteractionSource, val pressedScale: Float) : ModifierNodeElement<SaharaPressNode>() {
    override fun create() = SaharaPressNode(source, pressedScale)
    override fun update(node: SaharaPressNode) { node.pressedScale = pressedScale; node.watch(source) }
}

private class SaharaPressNode(private var source: InteractionSource, var pressedScale: Float) : Modifier.Node(), LayoutModifierNode {
    private val scale = Animatable(1f)
    private var job: Job? = null
    override fun onAttach() { start() }
    override fun onDetach() { job = null }
    fun watch(next: InteractionSource) {
        if (next == source) return
        source = next
        if (isAttached) { job?.cancel(); coroutineScope.launch { scale.snapTo(1f) }; start() }
    }
    private fun start() {
        job = coroutineScope.launch {
            val presses = mutableListOf<PressInteraction.Press>()
            source.interactions.collect { i ->
                when (i) {
                    is PressInteraction.Press -> presses.add(i)
                    is PressInteraction.Release -> presses.remove(i.press)
                    is PressInteraction.Cancel -> presses.remove(i.press)
                    else -> return@collect
                }
                val down = presses.isNotEmpty()
                // A new target cancels the running one and keeps its speed, so a quick tap never hitches.
                launch { scale.animateTo(if (down) pressedScale else 1f, if (down) SaharaSprings.fastSpatial() else SaharaSprings.spatial()) }
            }
        }
    }
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0) { val s = scale.value; scaleX = s; scaleY = s } }
    }
}

/** Animated content size for cards that grow or shrink (expanding sections, edited text). */
/** Cards no longer animate their size (owner, 24 Nov: cards "keep expanding"); content changes in place. */
@Suppress("UnusedReceiverParameter") fun Modifier.saharaContentSize(): Modifier = this

/** Crossfades between loading, empty and content states keyed by [state]. */
@Composable fun <S> SaharaStateSwitch(state: S, modifier: Modifier = Modifier, label: String = "Sahara state", content: @Composable AnimatedContentScope.(S) -> Unit) =
    AnimatedContent(state, modifier, transitionSpec = { SaharaMotion.crossfade() }, contentAlignment = Alignment.TopStart, label = label, content = content)

/** A count or short chip label that changes in place; numbers rise when they grow. */
@Composable fun SaharaAnimatedText(text: String, modifier: Modifier = Modifier, content: @Composable (String) -> Unit) =
    AnimatedContent(text, modifier, transitionSpec = { SaharaMotion.valueChange((targetState.toIntOrNull() ?: 0) >= (initialState.toIntOrNull() ?: 0)) }, label = "Sahara value") {
        // Tabular figures: a changing number keeps its width, so nothing beside it jitters (ignored where the font lacks them).
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) { content(it) }
    }

/** Snackbar host whose save confirmations carry the saved tick. Other messages look as before. */
@Composable fun SaharaSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) = SnackbarHost(state, modifier) { data ->
    val saved = SaharaMotion.isSavedMessage(data.visuals.message)
    if (!saved) Snackbar(data, Modifier.saharaSlideIn())
    else Snackbar(Modifier.saharaSlideIn().padding(12.dp)) {
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
    val hostScope = androidx.compose.runtime.rememberCoroutineScope()
    val preview = remember(hostScope) { SaharaBackPreview(hostScope) }
    val backOwner = LocalOnBackPressedDispatcherOwner.current
    // A leaving screen stays composed for the exit animation; its back handlers move to a dispatcher nothing presses, so back goes to the new screen.
    val inertBack = remember(backOwner) { backOwner?.let { owner -> object : OnBackPressedDispatcherOwner { override val lifecycle get() = owner.lifecycle; override val onBackPressedDispatcher = OnBackPressedDispatcher() } } }
    val parentEntering = LocalSaharaHostEntering.current
    // After composition: note the shown target, and on a committed back swipe zero the shared progress for the arriving screen.
    SideEffect { preview.targetChanged(target) }
    AnimatedContent(target, modifier, transitionSpec = {
        if (preview.committed > 0f && initialState == preview.leaving) SaharaMotion.backCommit()
        else SaharaMotion.transform(direction(initialState, targetState), rtl)
    }, contentAlignment = Alignment.TopStart, label = label) { shown ->
        val stagger = remember { SaharaStagger() }
        val live = SaharaMotion.handlesBack(transition.targetState)
        val childTransition = transition
        val entering: () -> Boolean = remember(childTransition, parentEntering) { { parentEntering() || childTransition.currentState != childTransition.targetState } }
        // The screen a back swipe just committed keeps the pose the thumb left it in while it fades out (no snap to full size).
        val frozen = remember(live) { if (!live && shown == preview.leaving) preview.committed else 0f }
        val frozenLeft = remember(live) { preview.committedLeft }
        // Predictive back, in Google's geometry: while the thumb swipes from the edge, the screen shrinks to 90%, shifts
        // (width/20 - 8 dp) towards the swipe and rounds its corners. Read only in the draw layer, so a swipe never recomposes.
        Box(Modifier.graphicsLayer {
            val p = if (live) (if (preview.hold) 0f else preview.progress.value) else frozen
            if (p > 0f) {
                val s = 1f - .10f * p
                scaleX = s; scaleY = s
                val left = if (live) preview.fromLeft else frozenLeft
                translationX = (if (left) 1f else -1f) * ((size.width / 20f) - 8.dp.toPx()).coerceAtLeast(0f) * p
                shape = RoundedCornerShape(SaharaRadius.Hero * p); clip = true
                shadowElevation = 12.dp.toPx() * p
            }
        }, propagateMinConstraints = true) {
            CompositionLocalProvider(LocalSaharaStagger provides stagger, LocalSaharaBackPreview provides preview, LocalSaharaHostEntering provides entering) {
                if (backOwner == null || inertBack == null) content(shown)
                else CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides if (live) backOwner else inertBack) { content(shown) }
            }
        }
    }
}

/** The back-swipe progress of the screen host it belongs to (0 = resting, 1 = swiped fully). */
@androidx.compose.runtime.Stable class SaharaBackPreview(internal val scope: kotlinx.coroutines.CoroutineScope) {
    val progress = Animatable(0f)
    var fromLeft by mutableStateOf(true)
    /** The pose (0..1) a committed swipe left the screen in; only that leaving screen draws it while it fades out. */
    var committed by mutableFloatStateOf(0f); internal set
    var committedLeft by mutableStateOf(true); internal set
    /** The host target that the committed swipe is leaving. */
    var leaving: Any? = null; internal set
    /** True for the moment between a commit and [progress] reaching 0, so the arriving screen draws at full size at once. */
    internal var hold by mutableStateOf(false)
    private var shownTarget: Any? = null
    private var awaitingCommit = false

    /** A cancelled swipe: the screen springs back to full size. */
    internal fun settle() { scope.launch { progress.animateTo(0f, SaharaSprings.spatial()) } }

    /** A committed swipe: freeze the pose for the leaving screen; [targetChanged] then resets the live value. */
    internal fun commit() {
        val p = progress.value
        if (p <= 0f) { scope.launch { progress.snapTo(0f) }; return }
        committed = p; committedLeft = fromLeft; leaving = shownTarget; awaitingCommit = true
        // If this back did not change the host's screen (it closed something inside it), spring back instead.
        scope.launch {
            androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { }
            if (awaitingCommit) { awaitingCommit = false; committed = 0f; leaving = null; progress.animateTo(0f, SaharaSprings.spatial()) }
        }
    }

    /** Called after each host composition with the shown target. */
    internal fun targetChanged(target: Any?) {
        if (target == shownTarget) return
        shownTarget = target
        if (awaitingCommit) {
            awaitingCommit = false
            hold = true
            scope.launch { progress.snapTo(0f); hold = false }
        } else if (committed > 0f) {
            // A later navigation: the earlier commit pose must not apply to it.
            committed = 0f; leaving = null
        }
    }
}

val LocalSaharaBackPreview = staticCompositionLocalOf<SaharaBackPreview?> { null }

/** True while the nearest [SaharaScreenHost] is still animating its screen in, so [saharaScreenEnter] does not fade it a second time. */
val LocalSaharaHostEntering = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * BackHandler with the Android predictive back preview: the nearest [SaharaScreenHost] follows the swipe, then the screen
 * goes back (or springs back if the swipe is cancelled). Same arguments as BackHandler; works the same on older phones.
 */
@Composable fun SaharaBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val preview = LocalSaharaBackPreview.current
    val currentOnBack by androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.activity.compose.PredictiveBackHandler(enabled) { events ->
        try {
            events.collect { e ->
                if (preview != null && SaharaMotion.enabled) {
                    preview.fromLeft = e.swipeEdge == androidx.activity.BackEventCompat.EDGE_LEFT
                    preview.progress.snapTo(SaharaMotion.BackEasing.transform(e.progress.coerceIn(0f, 1f)))
                }
            }
            // Freeze the pose first, so the transition chosen for this back already knows it was a swipe.
            preview?.commit()
            currentOnBack()
        } catch (c: kotlinx.coroutines.CancellationException) {
            preview?.settle()
            throw c
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
    Image(painterResource(R.drawable.ic_care), null, modifier.saharaBreathe(cycles = 40).size(size.dp).saharaFloatShadow(CircleShape).clip(CircleShape).clearAndSetSemantics { })
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
    return preferredFrameRate(60f).graphicsLayer { val s = 1f + amount * p.value; scaleX = s; scaleY = s }
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
    return preferredFrameRate(60f).drawWithContent {
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
        if (icon != null) Box(Modifier.saharaBreathe(cycles = 3).size(72.dp).saharaFloatShadow(CircleShape).clip(CircleShape).background(tone.solid), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(36.dp), tint = tone.onSolid) }
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
    // A screen a [SaharaScreenHost] is already fading in gets no second fade or rise (decided once, when it first composes).
    val hostEntering = LocalSaharaHostEntering.current
    val skip = remember { hostEntering() }
    if (skip) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, SaharaMotion.tween(SaharaMotion.SCREEN_MS, easing = SaharaMotion.Emphasized)) }
    return graphicsLayer { val p = progress.value; alpha = p; translationY = (1f - p) * 12.dp.toPx(); compositingStrategy = CompositingStrategy.ModulateAlpha }
}

/**
 * The warm, layered shadow for the few floating surfaces (the brand mark, empty-state discs, the success tick): a soft wide
 * shadow plus a tight contact one, in [SaharaColors.Shadow]. Put it before clip/background in the chain. Draw-only.
 */
fun Modifier.saharaFloatShadow(shape: Shape): Modifier = this
    .dropShadow(shape, androidx.compose.ui.graphics.shadow.Shadow(24.dp, SaharaColors.Shadow, 0.dp, DpOffset(0.dp, 8.dp), 0.10f))
    .dropShadow(shape, androidx.compose.ui.graphics.shadow.Shadow(3.dp, SaharaColors.Shadow, 0.dp, DpOffset(0.dp, 1.dp), 0.08f))

/** Success tick: a filled circle that pops in with its check (180 ms, no bounce). Static when motion is off. */
@Composable fun SaharaSuccessTick(modifier: Modifier = Modifier, size: Int = 56, tone: SaharaTone = SaharaTone.Success, description: String? = null) {
    val shown = remember { MutableTransitionState(!SaharaMotion.enabled).apply { targetState = true } }
    Box(modifier.size(size.dp).semantics { if (description != null) contentDescription = description }, contentAlignment = Alignment.Center) {
        AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.SAVED_MS)) + scaleIn(SaharaMotion.tween(SaharaMotion.SAVED_MS + 60, easing = SaharaMotion.Emphasized), initialScale = .6f)) {
            Box(Modifier.size(size.dp).saharaFloatShadow(CircleShape).clip(CircleShape).background(tone.solid).testTag("sahara-success-tick"), contentAlignment = Alignment.Center) {
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
        // Read in the indicator's draw lambda, so the glide never recomposes the bar.
        val shown = saharaFillState(progress)
        LinearProgressIndicator(progress = { shown.value }, modifier = m, color = tone.solid, trackColor = tone.wash, drawStopIndicator = {})
    }
}

/*
 * Round 3 motion set. Every one of these is draw-only (graphicsLayer / draw modifiers), so layout, semantics and tests
 * are unaffected, and every one is still when animations are removed or Battery Saver is on. Idle ones run a few
 * cycles and then rest. Not for the goals-of-care recording or sign-off screens (only their record pulse moves).
 */

/**
 * List item enter: fades in and rises 10 dp, [SaharaMotion.STAGGER_STEP_MS] after the item above it, at most
 * [SaharaMotion.STAGGER_CAP] steps deep. Only on a screen's first load: rows scrolled into view later simply appear.
 */
@Composable fun Modifier.saharaStagger(index: Int): Modifier {
    if (!SaharaMotion.enabled) return this
    val screen = LocalSaharaStagger.current
    val run = remember { screen?.fresh ?: (index <= SaharaMotion.STAGGER_CAP * 2) }
    if (!run) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, SaharaMotion.tween(SaharaMotion.LIST_ITEM_MS, index.coerceIn(0, SaharaMotion.STAGGER_CAP) * SaharaMotion.STAGGER_STEP_MS, SaharaMotion.EmphasizedDecelerate))
    }
    return graphicsLayer { val p = progress.value; alpha = p; translationY = (1f - p) * 10.dp.toPx(); compositingStrategy = CompositingStrategy.ModulateAlpha }
}

/** Gentle idle bob for an icon or illustration: up [amplitude] and back over [SaharaMotion.FLOAT_MS], [cycles] times, then rest. */
@Composable fun Modifier.saharaFloat(amplitude: Dp = 1.5.dp, cycles: Int = 4): Modifier {
    if (!SaharaMotion.enabled) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(cycles) {
        repeat(cycles) {
            if (!SaharaMotion.enabled) { p.snapTo(0f); return@LaunchedEffect }
            p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.FLOAT_MS / 2, easing = FastOutSlowInEasing))
            p.animateTo(0f, androidx.compose.animation.core.tween(SaharaMotion.FLOAT_MS / 2, easing = FastOutSlowInEasing))
        }
    }
    return preferredFrameRate(60f).graphicsLayer { translationY = -p.value * amplitude.toPx() }
}

/**
 * A soft ring that spreads out from the one primary action on a screen and fades, [cycles] times, then rests.
 * [corner] matches the button's shape. Off while [active] is false (e.g. once the action is done).
 */
@Composable fun Modifier.saharaPulseGlow(color: Color = SaharaColors.Primary, corner: Dp = SaharaRadius.Lg, active: Boolean = true, cycles: Int = 3): Modifier {
    if (!SaharaMotion.enabled || !active) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(cycles) {
        delay(700)
        repeat(cycles) {
            if (!SaharaMotion.enabled) { p.snapTo(0f); return@LaunchedEffect }
            p.snapTo(0f); p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.GLOW_MS, easing = LinearOutSlowInEasing)); p.snapTo(0f)
            delay(1000)
        }
    }
    return drawBehind {
        val v = p.value
        if (v > 0f && v < 1f) {
            val spread = 7.dp.toPx() * v
            drawRoundRect(color.copy(alpha = .45f * (1f - v)), topLeft = Offset(-spread, -spread), size = Size(size.width + 2 * spread, size.height + 2 * spread),
                cornerRadius = CornerRadius(corner.toPx() + spread), style = Stroke(width = 3.dp.toPx()))
        }
    }
}

/**
 * A small attention nudge: one soft, eased tilt each way that settles upright (about half a second), [delayMs] after it
 * appears and again whenever [trigger] changes. Gentle on purpose: a fast shake reads as an error.
 */
@Composable fun Modifier.saharaWiggleOnce(trigger: Any? = Unit, delayMs: Long = 800): Modifier {
    if (!SaharaMotion.enabled) return this
    val angle = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        delay(delayMs)
        if (!SaharaMotion.enabled) return@LaunchedEffect
        angle.animateTo(0f, keyframes {
            durationMillis = 520
            -3f at 90 using SaharaMotion.Emphasized
            3f at 230 using SaharaMotion.Emphasized
            -1.5f at 360 using SaharaMotion.Emphasized
            0f at 520 using SaharaMotion.Emphasized
        })
    }
    return graphicsLayer { rotationZ = angle.value }
}

/**
 * Press feedback for tiles and big buttons without their own interaction source: settles to the app's one press depth
 * ([SAHARA_PRESS_SCALE]) while a finger is down and springs back calmly (no overshoot).
 * It only watches the touch (never consumes it), so the element's own click, ripple and scrolling work as before;
 * moving past touch slop (a scroll) releases it at once.
 */
@Composable fun Modifier.saharaPressBounce(): Modifier {
    if (!SaharaMotion.enabled) return this
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            scope.launch { scale.animateTo(SAHARA_PRESS_SCALE, SaharaSprings.fastSpatial()) }
            val slop = viewConfiguration.touchSlop
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || (change.position - down.position).getDistance() > slop) break
            }
            scope.launch { scale.animateTo(1f, SaharaSprings.spatial()) }
        }
    }.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** A number that counts up from 0 when first shown and glides to each new value. Returns [target] at once when motion is off. */
@Composable fun saharaCountUp(target: Int): Int {
    val shown = remember { Animatable(if (SaharaMotion.enabled) 0f else target.toFloat()) }
    LaunchedEffect(target) {
        if (!SaharaMotion.enabled) shown.snapTo(target.toFloat())
        // Critically damped: never counts past the target, and a new value mid-count carries on smoothly.
        else shown.animateTo(target.toFloat(), spring(1f, 200f, .5f))
    }
    return shown.value.roundToInt()
}

/** A progress fraction (0..1) that fills up from empty when first shown and glides to each new value. */
@Composable fun saharaFill(target: Float): Float = saharaFillState(target).value

/**
 * The same fill as [saharaFill], as state: read `.value` inside a draw or progress lambda (e.g. LinearProgressIndicator's
 * `progress = { fill.value }`) so the glide redraws without recomposing.
 */
@Composable fun saharaFillState(target: Float): State<Float> {
    val goal = target.coerceIn(0f, 1f)
    val shown = remember { Animatable(if (SaharaMotion.enabled) 0f else goal) }
    LaunchedEffect(goal) {
        if (!SaharaMotion.enabled) shown.snapTo(goal)
        // Critically damped spring: never overfills, and ticking two doses in a row retargets without a hitch.
        else shown.animateTo(goal, spring(1f, 300f, 1f / 1000f))
    }
    return shown.asState()
}

/** Charts and graphs: drawn in from the start edge once, when first shown. */
@Composable fun Modifier.saharaReveal(): Modifier {
    if (!SaharaMotion.enabled) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.FILL_MS + 200, 120, SaharaMotion.Emphasized)) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawWithContent {
        val w = size.width * p.value
        if (rtl) clipRect(left = size.width - w) { this@drawWithContent.drawContent() } else clipRect(right = w) { this@drawWithContent.drawContent() }
    }
}

/** A small celebration on a tap (a hug, a like): settles in from 90% with one soft spring each time [trigger] changes to a value other than false/null (not on first show). */
@Composable fun Modifier.saharaPop(trigger: Any?): Modifier {
    if (!SaharaMotion.enabled) return this
    val scale = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        if (trigger == null || trigger == false) return@LaunchedEffect
        scale.snapTo(.9f); scale.animateTo(1f, SaharaSprings.gentle())
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** Map pins and result markers: drop in from above and settle softly, staggered by [index]. */
@Composable fun Modifier.saharaDropIn(index: Int = 0): Modifier {
    if (!SaharaMotion.enabled) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceIn(0, SaharaMotion.STAGGER_CAP) * 40L)
        p.animateTo(1f, SaharaSprings.gentle())
    }
    return graphicsLayer { val v = p.value; alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * -14.dp.toPx(); compositingStrategy = CompositingStrategy.ModulateAlpha }
}

/** Dialog content arriving: a quick scale-up from 92% with a fade. */
@Composable fun Modifier.saharaDialogEnter(): Modifier {
    if (!SaharaMotion.enabled) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.ENTER_MS - 60, easing = SaharaMotion.Emphasized)) }
    return graphicsLayer { val v = p.value; alpha = v; val s = .92f + .08f * v; scaleX = s; scaleY = s; compositingStrategy = CompositingStrategy.ModulateAlpha }
}

/** Snackbars, banners and inline results: slide up 16 dp (or down, when [fromTop]) and fade in once. */
@Composable fun Modifier.saharaSlideIn(fromTop: Boolean = false): Modifier {
    if (!SaharaMotion.enabled) return this
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.ENTER_MS, easing = SaharaMotion.Emphasized)) }
    return graphicsLayer { val v = p.value; alpha = v; translationY = (1f - v) * (if (fromTop) -16 else 16).dp.toPx(); compositingStrategy = CompositingStrategy.ModulateAlpha }
}

/**
 * The selected-tab marker for a row of [count] equal-width tabs (bottom navigation, segmented tabs): a short rounded bar on
 * the top edge that slides to [index] (hidden when [index] is -1). [gap] is the space between tabs in the row.
 */
@Composable fun Modifier.saharaNavIndicator(index: Int, count: Int, color: Color = SaharaColors.Primary, gap: Dp = 0.dp, width: Dp = 32.dp): Modifier {
    if (count <= 0) return this
    // Springs (no overshoot) so a quick second tab tap retargets the marker mid-slide without restarting from rest.
    // Kept as State and read only in the draw block, so the slide redraws without recomposing the row.
    val x = animateFloatAsState(index.coerceAtLeast(0).toFloat(), SaharaSprings.spatial(), label = "navIndicatorX")
    val shownState = animateFloatAsState(if (index in 0 until count) 1f else 0f, SaharaSprings.effects(), label = "navIndicatorAlpha")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawWithContent {
        drawContent()
        val shown = shownState.value
        if (shown <= 0f) return@drawWithContent
        val g = gap.toPx(); val cell = (size.width + g) / count
        val bar = width.toPx().coerceAtMost(cell - g); val h = 3.dp.toPx()
        val center = cell * x.value + (cell - g) / 2f
        val left = if (rtl) size.width - center - bar / 2f else center - bar / 2f
        drawRoundRect(color.copy(alpha = color.alpha * shown), topLeft = Offset(left, 0f), size = Size(bar, h), cornerRadius = CornerRadius(h / 2f))
    }
}

/*
 * Round 4 (22 Nov): small moments of delight for the patient's daily screens. Same rules as above: draw-only, never in the
 * way of a tap, still when animations are removed or Battery Saver is on, idle ones rest after a few cycles, and none of
 * the celebratory ones are used on distress, goals-of-care or end-of-life screens.
 */

/**
 * A light confirming tap of the phone's vibration for a real save (a dose ticked, a mood chosen). Uses the system haptic
 * constants, so it follows the phone's own "Touch feedback" setting and does nothing where that is off.
 */
/** The phone's light key-tap feel on a button or tile press (follows the phone's "Touch feedback" setting), like system apps. */
@Composable fun rememberSaharaTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { runCatching { haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap) }; Unit } }
}

/** Confirm haptic for a real save; Compose's semantic type picks the right effect (and fallback) for each Android version. */
@Composable fun rememberSaharaHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { runCatching { haptic.performHapticFeedback(HapticFeedbackType.Confirm) }; Unit } }
}

/** Reject haptic for something that could not be done (a failed save, a blocked action). Follows the phone's Touch feedback setting. */
@Composable fun rememberSaharaReject(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { runCatching { haptic.performHapticFeedback(HapticFeedbackType.Reject) }; Unit } }
}

/**
 * A chosen thing answers back: when [selected] turns true (not on first show) it swells about [amount] and settles back
 * with one soft spring. For mood faces and similar one-of-many choices.
 */
@Composable fun Modifier.saharaSelectBounce(selected: Boolean, amount: Float = .08f): Modifier {
    if (!SaharaMotion.enabled) return this
    val scale = remember { Animatable(1f) }
    var last by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected && !last && SaharaMotion.enabled) {
            scale.animateTo(1f + amount, SaharaSprings.fastSpatial())
            scale.animateTo(1f, SaharaSprings.gentle())
        }
        last = selected
    }
    return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * A soft bloom when something is completed: as [done] turns true (not on first show), a ring in [color] spreads out from
 * the element's edge and fades, and the element itself settles from 97% with a gentle spring. [corner] matches its shape.
 */
@Composable fun Modifier.saharaBloom(done: Boolean, color: Color = SaharaColors.Primary, corner: Dp = SaharaRadius.Lg): Modifier {
    if (!SaharaMotion.enabled) return this
    val ring = remember { Animatable(0f) }
    val pop = remember { Animatable(1f) }
    var last by remember { mutableStateOf(done) }
    LaunchedEffect(done) {
        if (done && !last && SaharaMotion.enabled) {
            launch { pop.snapTo(.97f); pop.animateTo(1f, SaharaSprings.gentle()) }
            ring.snapTo(0f); ring.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.GLOW_MS - 400, easing = LinearOutSlowInEasing)); ring.snapTo(0f)
        }
        last = done
    }
    return drawBehind {
        val v = ring.value
        if (v > 0f && v < 1f) {
            val spread = 12.dp.toPx() * v
            drawRoundRect(color.copy(alpha = .22f * (1f - v)), topLeft = Offset(-spread, -spread), size = Size(size.width + 2 * spread, size.height + 2 * spread), cornerRadius = CornerRadius(corner.toPx() + spread))
            drawRoundRect(color.copy(alpha = .5f * (1f - v)), topLeft = Offset(-spread, -spread), size = Size(size.width + 2 * spread, size.height + 2 * spread),
                cornerRadius = CornerRadius(corner.toPx() + spread), style = Stroke(width = 2.dp.toPx()))
        }
    }.graphicsLayer { scaleX = pop.value; scaleY = pop.value }
}

/**
 * A see-through wash in [color] (give it a low alpha) that rises from the bottom over the element while [filled] (a
 * pillbox once its dose is taken), and drains when it is undone. Already-filled elements start full, so nothing replays.
 */
@Composable fun Modifier.saharaFillRise(filled: Boolean, color: Color, corner: Dp = 14.dp): Modifier {
    val level = remember { Animatable(if (filled) 1f else 0f) }
    LaunchedEffect(filled) {
        val goal = if (filled) 1f else 0f
        // Critically damped spring: an undo mid-rise reverses smoothly instead of restarting from rest.
        if (!SaharaMotion.enabled) level.snapTo(goal) else level.animateTo(goal, spring(1f, 300f, 1f / 1000f))
    }
    return drawWithContent {
        drawContent()
        val v = level.value
        if (v > 0f) {
            val h = size.height * v
            clipRect(top = size.height - h) { drawRoundRect(color, cornerRadius = CornerRadius(corner.toPx())) }
        }
    }
}

/** Idle sway for an illustration (the sun or moon on Today): tilts [degrees] each way over [SaharaMotion.FLOAT_MS], [cycles] times, then rests upright. */
@Composable fun Modifier.saharaSway(degrees: Float = 6f, cycles: Int = 3): Modifier {
    if (!SaharaMotion.enabled) return this
    val angle = remember { Animatable(0f) }
    LaunchedEffect(cycles) {
        delay(400)
        repeat(cycles) {
            if (!SaharaMotion.enabled) { angle.snapTo(0f); return@LaunchedEffect }
            angle.animateTo(degrees, androidx.compose.animation.core.tween(SaharaMotion.FLOAT_MS / 2, easing = FastOutSlowInEasing))
            angle.animateTo(-degrees, androidx.compose.animation.core.tween(SaharaMotion.FLOAT_MS, easing = FastOutSlowInEasing))
            angle.animateTo(0f, androidx.compose.animation.core.tween(SaharaMotion.FLOAT_MS / 2, easing = FastOutSlowInEasing))
        }
    }
    return preferredFrameRate(60f).graphicsLayer { rotationZ = angle.value }
}

/**
 * A tick that draws itself, stroke by stroke (about a quarter second), the first time it is shown. Fully drawn at once
 * when motion is off. Decorative: the text or description beside it says what it means.
 */
@Composable fun SaharaDrawnCheck(modifier: Modifier = Modifier, color: Color = Color.White, strokeWidth: Dp = 3.dp) {
    val p = remember { Animatable(if (SaharaMotion.enabled) 0f else 1f) }
    LaunchedEffect(Unit) { if (SaharaMotion.enabled) p.animateTo(1f, androidx.compose.animation.core.tween(SaharaMotion.SAVED_MS + 80, 60, SaharaMotion.Emphasized)) else p.snapTo(1f) }
    androidx.compose.foundation.Canvas(modifier.clearAndSetSemantics { }.testTag("sahara-drawn-check")) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * .2f, size.height * .53f); lineTo(size.width * .42f, size.height * .74f); lineTo(size.width * .8f, size.height * .3f)
        }
        val measure = androidx.compose.ui.graphics.PathMeasure().apply { setPath(path, false) }
        val part = androidx.compose.ui.graphics.Path()
        measure.getSegment(0f, measure.length * p.value, part, true)
        drawPath(part, color, style = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

/** A short-lived overlay: shows [content] each time [trigger] changes to a non-null, non-false value (not on first show) for [showMs], then removes it. Never shown with motion off. */
@Composable fun SaharaMoment(trigger: Any?, showMs: Long = 1600, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) { first = false; return@LaunchedEffect }
        if (trigger == null || trigger == false || !SaharaMotion.enabled) return@LaunchedEffect
        shown = true; delay(showMs); shown = false
    }
    if (shown) content()
}

/**
 * List state for Sahara's long LazyColumns: composes half a screen ahead (and a quarter behind) while scrolling, so tall care
 * cards are ready before they slide into view instead of being built mid-fling.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable fun rememberSaharaListState(initialIndex: Int = 0): androidx.compose.foundation.lazy.LazyListState {
    val window = remember { androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow(0.5f, 0.25f) }
    return androidx.compose.foundation.lazy.rememberLazyListState(window, initialIndex)
}
