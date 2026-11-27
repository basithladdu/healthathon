package `in`.wedevit.care

import androidx.compose.ui.graphics.compositeOver

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Sahara's shared components in the AI-FIR grammar: a solid header band, bento tiles with coloured icon chips,
 * compact register rows, status pills, real-looking buttons, section headers and the empty / loading / error states.
 * Colours come only from SaharaColors and SaharaTone; page code should not hard-code colours.
 */

/** True when a wrapped line of [layout] ends inside a word (a letter or mark on both sides of the break). */
internal fun splitsWord(text: String, lineEnds: List<Int>): Boolean {
    fun word(c: Char) = Character.isLetterOrDigit(c) || Character.getType(c).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() }
    return lineEnds.dropLast(1).any { end -> end in 1 until text.length && word(text[end - 1]) && word(text[end]) }
}

/**
 * Text for fixed-width slots (tabs, chips, tile labels, quick actions). It may wrap to [maxLines] lines at spaces,
 * but if a word would split ("PATIEN / T") or the text would not fit, it steps the size down (to [min] sp at least)
 * until it fits whole. The size is chosen inside the text's own layout pass (Compose's autoSize), so the label is drawn
 * once at its final size: no hidden steps, no pop-in, and a tab turning bold re-fits in the same frame.
 */
@Composable fun SaharaFitText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelLarge, color: Color = Color.Unspecified,
    maxLines: Int = 2, min: Float = 11f, fontWeight: FontWeight? = null, textAlign: androidx.compose.ui.text.style.TextAlign? = androidx.compose.ui.text.style.TextAlign.Center) {
    val base = if (style.fontSize.isSp) style.fontSize.value else 15f
    val fit = remember(base, min) { SaharaFitSize(base, min) }
    Text(text, modifier, color = color, autoSize = fit, fontWeight = fontWeight, maxLines = maxLines, softWrap = maxLines > 1,
        overflow = TextOverflow.Clip, textAlign = textAlign,
        // em line height follows whichever size is chosen (1.3x, as before).
        style = style.copy(lineHeight = 1.3.em, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading, hyphens = androidx.compose.ui.text.style.Hyphens.None))
}

/** [SaharaFitText]'s fitting rule: the largest size from [base] down to [min] (1 sp steps) that neither overflows nor splits a word. */
private class SaharaFitSize(private val base: Float, private val min: Float) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var s = base
        while (s > min) {
            val layout = performLayout(constraints, text, s.sp)
            if (!layout.hasVisualOverflow && !splitsWord(text.text, (0 until layout.lineCount).map { layout.getLineEnd(it) })) return s.sp
            s = maxOf(min, s - 1f)
        }
        return s.sp
    }
    override fun equals(other: Any?): Boolean = other is SaharaFitSize && other.base == base && other.min == min
    override fun hashCode(): Int = 31 * base.hashCode() + min.hashCode()
}

/** Light or dark status-bar icons while this screen is shown; the band needs light icons. Restores the previous look on leave. */
@Composable fun SaharaStatusBarIcons(lightIcons: Boolean) {
    val context = LocalContext.current; val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(context, lightIcons) {
        var owner = context
        while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
        val window = (owner as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !lightIcons
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
}

/**
 * The header band: solid deep maroon-brown, white title, optional one-line subtitle, back/navigation slot and actions.
 * It draws behind the status bar and pads itself below it.
 */
@Composable fun SaharaTopBand(title: String, modifier: Modifier = Modifier, subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    SaharaStatusBarIcons(lightIcons = false)
    Surface(color = SaharaColors.Header, contentColor = SaharaColors.OnHeader, modifier = modifier.fillMaxWidth()) { Column {
        Row(Modifier.statusBarsPadding().heightIn(min = 60.dp).padding(start = if (navigation == null) 16.dp else 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (navigation != null) { navigation(); Spacer(Modifier.width(4.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, Modifier.a11yHeading(), style = MaterialTheme.typography.titleLarge, fontSize = 21.sp, lineHeight = 26.sp, color = SaharaColors.OnHeader, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SaharaColors.OnHeaderSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
        HorizontalDivider(color = SaharaColors.HeaderLine)
    } }
}

/** A coloured icon chip: the section's bright solid with its white icon. Decorative; the label beside it carries the meaning. [breathe]: a slow idle swell for the one main card on a screen. */
@Composable fun SaharaIconChip(icon: ImageVector, tone: SaharaTone, modifier: Modifier = Modifier, size: Int = 40, breathe: Boolean = false) {
    Box(modifier.then(if (breathe) Modifier.saharaBreathe() else Modifier).size(size.dp).clip(RoundedCornerShape(SaharaRadius.Sm)).background(tone.solid), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * .55f).dp), tint = tone.onSolid)
    }
}

/**
 * Launchpad tile (AI-FIR bento): a solid bright block in the section's colour with a white icon, a bold white title,
 * an optional big count and one short caption. One idea, one action: the whole tile is one button, with a soft
 * shadow that sinks and a fill that darkens while pressed.
 */
@Composable fun SaharaBentoTile(title: String, icon: ImageVector, tone: SaharaTone, onClick: () -> Unit, modifier: Modifier = Modifier,
    value: String? = null, caption: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Softer tiles (Dr Sujay, 22 Nov): the section's light wash, a thin section-colour edge, the icon on a solid disc, dark ink text.
    // The fill is read only in the draw phase, so its 110 ms glide redraws the tile without recomposing it every frame.
    val fill = animateColorAsState(if (pressed) tone.solid.copy(alpha = .22f).compositeOver(tone.wash) else tone.wash, SaharaMotion.tween(SaharaMotion.PRESS_MS), label = "tile-press")
    val shape = RoundedCornerShape(SaharaRadius.Lg)
    // The darker fill and the slight settle already show the press, so the tile's ripple shows focus and hover only:
    // one calm press signal instead of three. No tap haptic: a tile opens a screen, and plain navigation stays silent.
    CompositionLocalProvider(LocalRippleConfiguration provides SaharaRipple.NoPressWash) {
        Column(modifier.saharaAppear().saharaPress(interaction).heightIn(min = 92.dp).clip(shape).drawBehind { drawRect(fill.value) }.border(1.dp, tone.solid.copy(alpha = .22f), shape)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(color = tone.ink), role = Role.Button, onClick = onClick).a11yCard().padding(SaharaSpacing.Md),
            verticalArrangement = Arrangement.spacedBy(SaharaSpacing.Sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tone.solid), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(24.dp), tint = tone.onSolid)
                }
                Spacer(Modifier.weight(1f))
                // Tabular figures: a count that changes keeps its width, so the row does not jitter.
                if (value != null) Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum", textMotion = TextMotion.Animated), fontWeight = FontWeight.Bold, color = tone.ink, maxLines = 1)
            }
            Text(title, style = MaterialTheme.typography.titleMedium.copy(textMotion = TextMotion.Animated), fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = SaharaColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall.copy(textMotion = TextMotion.Animated), fontWeight = FontWeight.SemiBold, color = tone.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Sahara's one calm ripple: Material's ripple at lower alphas (a soft wash, never a flash), in the content's own colour,
 * which on Sahara surfaces is the section's ink. [Calm] is for the whole app: CareTheme should provide it through
 * [SaharaRippleTheme]. [NoPressWash] keeps focus and hover visible but adds no press wash, for surfaces whose own fill shows the press.
 */
object SaharaRipple {
    val Calm = RippleConfiguration(rippleAlpha = RippleAlpha(draggedAlpha = .08f, focusedAlpha = .12f, hoveredAlpha = .06f, pressedAlpha = .10f))
    val NoPressWash = RippleConfiguration(rippleAlpha = RippleAlpha(draggedAlpha = .08f, focusedAlpha = .12f, hoveredAlpha = .06f, pressedAlpha = 0f))
}

/** Wrap the app's content (in CareTheme) so every Material and Sahara ripple uses [SaharaRipple.Calm]. */
@Composable fun SaharaRippleTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides SaharaRipple.Calm, content = content)
}

/** Two tiles per row; the last one spans the row when the count is odd. */
@Composable fun SaharaBentoGrid(modifier: Modifier = Modifier, gap: Int = 12, tiles: List<@Composable (Modifier) -> Unit>) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                pair.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/**
 * Compact register row: icon chip, title, one detail line, optional status pill and a chevron when it opens something.
 * At least 56 dp tall; the row is one button for TalkBack.
 */
@Composable fun SaharaListRow(title: String, modifier: Modifier = Modifier, detail: String? = null, icon: ImageVector? = null, tone: SaharaTone = SaharaTone.Rust,
    status: Pair<String, SaharaTone>? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, emoji: String? = null, emojiTint: Color = tone.solid) {
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.saharaPress(interaction).clip(RoundedCornerShape(SaharaRadius.Md))
        .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(color = tone.ink), role = Role.Button, onClick = onClick) else Modifier)
        .a11yCard().padding(horizontal = SaharaSpacing.Md, vertical = SaharaSpacing.Md), verticalAlignment = Alignment.CenterVertically) {
        if (emoji != null) { Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(emojiTint.copy(alpha = .16f).compositeOver(Color.White)), contentAlignment = Alignment.Center) { HealthEmoji(emoji, size = 19.sp) }; Spacer(Modifier.width(SaharaSpacing.Md)) }
        else if (icon != null) { SaharaIconChip(icon, tone, size = 36); Spacer(Modifier.width(SaharaSpacing.Md)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = SaharaColors.InkSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) { Spacer(Modifier.width(SaharaSpacing.Sm)); SaharaStatusPill(status.first, status.second) }
        if (trailing != null) { Spacer(Modifier.width(SaharaSpacing.Sm)); trailing() }
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = if (emoji != null) SaharaColors.LineStrong else tone.ink)
    }
}

/**
 * A register card holding rows separated by hairlines (AI-FIR register). White by default; with a section [tone] it
 * takes that section's wash, a 1.5 dp border in its solid and a solid accent bar down the leading edge.
 */
@Composable fun SaharaRegister(modifier: Modifier = Modifier, tone: SaharaTone? = null, content: @Composable ColumnScope.() -> Unit) {
    // Premium pass (24 Nov): one quiet white group with a hairline; the row's icon chip carries the section colour.
    Surface(modifier.fillMaxWidth().saharaAppear(), shape = RoundedCornerShape(SaharaRadius.Xl), color = SaharaColors.Surface, shadowElevation = 1.5.dp) {
        Column(Modifier.padding(vertical = 2.dp), content = content)
    }
}

/** A solid 6 dp bar in the tone's colour down the leading edge, with the content padded clear of it. */
fun Modifier.saharaAccentBar(tone: SaharaTone): Modifier = drawWithContent {
    val w = 6.dp.toPx()
    drawRect(tone.solid, topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - w else 0f, 0f), size = Size(w, size.height))
    drawContent()
}.padding(start = 6.dp)

/**
 * Round 3 section header band (AI-FIR hero band): a rounded solid block in the section's colour with a white back arrow,
 * a bold white title, an optional one-line subtitle and a trailing slot (art or one action). Every screen of a section
 * opts in with that section's tone (see [SaharaSection]), so the colour says where you are.
 */
@Composable fun SaharaSectionBand(title: String, tone: SaharaTone, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, subtitle: String? = null,
    backTag: String? = null, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val t = careText()
    // Inside the doctor's patient sections the screen's one header already names the page and the patient, so the band keeps only its action.
    if (LocalHidePageBand.current) { if (trailing != null) Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { trailing() }; return }
    // Softer (Dr Sujay's video, 22 Nov: solid bands felt dark and scary): the section's light wash with its dark ink, a thin edge in the section colour.
    // Premium pass (24 Nov, owner: "cards and cards"): a flat page header on the ground, not another box. Back sits first.
    Surface(modifier.fillMaxWidth().saharaAppear(), color = Color.Transparent, contentColor = tone.ink) {
        Row(Modifier.heightIn(min = 56.dp).padding(start = if (onBack != null) 0.dp else 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // 56 dp, like Sahara's own buttons: the back arrow is the control older patients reach for most.
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(56.dp).then(if (backTag != null) Modifier.testTag(backTag) else Modifier)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = tone.ink)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, color = SaharaColors.Ink)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 14.sp, lineHeight = 19.sp, color = SaharaColors.InkSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) { Spacer(Modifier.width(SaharaSpacing.Sm)); trailing() }
        }
    }
}

/** True inside the doctor's patient sections: page bands (big titles) are left out because the host header is the one header. */
val LocalHidePageBand = androidx.compose.runtime.compositionLocalOf { false }

/** The one action on a section band: a solid white button with the section's ink label, so it still reads as a button on the colour. */
@Composable fun SaharaBandButton(text: String, onClick: () -> Unit, tone: SaharaTone, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(SaharaRadius.Md),
        colors = ButtonDefaults.buttonColors(containerColor = SaharaColors.Surface, contentColor = tone.ink, disabledContainerColor = SaharaColors.Well, disabledContentColor = SaharaColors.InkFaint),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), contentPadding = PaddingValues(horizontal = SaharaSpacing.Lg, vertical = SaharaSpacing.Sm), modifier = modifier.heightIn(min = 56.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(SaharaSpacing.Sm)) }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** Section art on a band: the illustration sits on a white disc so it never melts into the colour. Decorative. */
@Composable fun SaharaBandArt(art: Int, size: Int = 52, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(SaharaColors.Surface), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(saharaImage(art, (size - 8).dp), null, Modifier.size((size - 8).dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    }
}

/** Status pill: the tone's bright solid with bold white text, content-sized, never breaks a word. [shimmer]: a soft idle sweep (the "next dose" pill). */
@Composable fun SaharaStatusPill(text: String, tone: SaharaTone, modifier: Modifier = Modifier, icon: ImageVector? = null, shimmer: Boolean = false) {
    Row(modifier.clip(RoundedCornerShape(SaharaRadius.Pill)).background(tone.solid).then(if (shimmer) Modifier.saharaShimmer() else Modifier)
        .padding(horizontal = SaharaSpacing.Md, vertical = SaharaSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = tone.onSolid); Spacer(Modifier.width(SaharaSpacing.Xs)) }
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tone.onSolid, maxLines = 1, softWrap = false)
    }
}

/** Trailing arrow for a button that opens another screen: it says where it goes and shows the way. */
@Composable private fun GoesArrow() { Spacer(Modifier.width(SaharaSpacing.Sm)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(22.dp)) }

/** How long a button stays busy before its spinner shows: a quick local save finishes first, so no spinner flashes. */
internal const val SAHARA_SPINNER_DELAY_MS = 300L

/** Button labels scale slightly while pressed; animated text motion keeps the glyphs from shimmering as they do. */
@Composable private fun saharaButtonLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(textMotion = TextMotion.Animated)

/**
 * The one main action: a solid bright fill (rust, or [tone]), bold white label, a soft shadow that sinks and a darker
 * fill while pressed, at least 56 dp. [navigates]: it opens another screen, so a chevron follows the label (and the tap
 * stays silent, like any navigation). [busy]: the leading icon crossfades into a spinner, but only once the work has
 * taken longer than [SAHARA_SPINNER_DELAY_MS]; icon and spinner share one 22 dp slot, so the label never moves.
 */
@Composable fun SaharaPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
    tone: SaharaTone = SaharaTone.Rust, busy: Boolean = false, navigates: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(if (pressed) tone.ink else tone.solid, SaharaMotion.tween(SaharaMotion.PRESS_MS), label = "button-press")
    val tick = rememberSaharaTick()
    var spin by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) { delay(SAHARA_SPINNER_DELAY_MS); spin = true } else spin = false }
    Button(onClick = { if (!navigates) tick(); onClick() }, enabled = enabled && !busy, interactionSource = interaction, shape = RoundedCornerShape(SaharaRadius.Md),
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = tone.onSolid, disabledContainerColor = SaharaColors.Well, disabledContentColor = SaharaColors.InkFaint),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp, disabledElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = SaharaSpacing.Md), modifier = modifier.heightIn(min = 56.dp).saharaPress(interaction)) {
        if (busy || icon != null) {
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                Crossfade(spin, animationSpec = SaharaMotion.tween(SaharaMotion.SELECT_MS), label = "button-busy") { showSpinner ->
                    // The button is disabled while busy, so the spinner takes the disabled content colour (readable on Well).
                    if (showSpinner) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.5.dp)
                    else if (icon != null) Icon(icon, null, Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(SaharaSpacing.Sm))
        }
        Text(text, style = saharaButtonLabelStyle(), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (navigates) GoesArrow()
    }
}

/** Second action: white fill with a strong 2 dp border in the tone's bright colour, bold ink label, tinted while pressed, at least 56 dp. */
@Composable fun SaharaSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
    tone: SaharaTone = SaharaTone.Rust, navigates: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Premium pass (24 Nov): a soft tonal button (the tone's wash, a faint edge) instead of a heavy 2 dp outline.
    val fill by animateColorAsState(if (pressed) tone.solid.copy(alpha = .18f).compositeOver(tone.wash) else tone.wash, SaharaMotion.tween(SaharaMotion.PRESS_MS), label = "outline-press")
    val tick = rememberSaharaTick()
    OutlinedButton(onClick = { if (!navigates) tick(); onClick() }, enabled = enabled, interactionSource = interaction, shape = RoundedCornerShape(SaharaRadius.Md),
        border = BorderStroke(1.dp, if (enabled) tone.solid.copy(alpha = .28f) else SaharaColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fill, contentColor = tone.ink, disabledContentColor = SaharaColors.InkFaint),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = SaharaSpacing.Md), modifier = modifier.heightIn(min = 56.dp).saharaPress(interaction)) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(SaharaSpacing.Sm)) }
        Text(text, style = saharaButtonLabelStyle(), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (navigates) GoesArrow()
    }
}

/** Section header: the section's icon chip (or a short bright bar), the title, and an optional "See all" style action with a chevron. */
@Composable fun SaharaSectionHeader(title: String, modifier: Modifier = Modifier, tone: SaharaTone = SaharaTone.Rust, actionLabel: String? = null, onAction: (() -> Unit)? = null,
    icon: ImageVector? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) SaharaIconChip(icon, tone, size = 32) 
        if (icon != null) Spacer(Modifier.width(SaharaSpacing.Md))
        Text(title, Modifier.weight(1f).a11yHeading(), style = MaterialTheme.typography.titleMedium, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SaharaColors.Ink)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 56.dp), colors = ButtonDefaults.textButtonColors(contentColor = tone.ink)) {
            Text(actionLabel, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
        }
    }
}

/**
 * Inline loading for part of a screen: a small ring and one line, in place of the content that is coming. It waits
 * [SAHARA_SPINNER_DELAY_MS] before fading in, so a fast local read never flashes a loader; its 56 dp is kept from the
 * start, so nothing below jumps when it appears.
 */
@Composable fun SaharaInlineLoading(text: String, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(SAHARA_SPINNER_DELAY_MS); shown = true }
    Box(modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(shown, enter = fadeIn(SaharaMotion.tween(SaharaMotion.VALUE_MS, easing = SaharaMotion.EmphasizedDecelerate)), exit = ExitTransition.None) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = SaharaSpacing.Lg).announce(), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = SaharaColors.Primary, trackColor = SaharaColors.PrimaryWash, strokeWidth = 3.dp)
                Spacer(Modifier.width(SaharaSpacing.Md)); Text(text, style = MaterialTheme.typography.bodyMedium, color = SaharaColors.InkSub)
            }
        }
    }
}

/**
 * Error state: a bright icon, what went wrong in one line, and the one thing to do (usually Try again) as a real button.
 * A Reject haptic marks it once when it first appears (not again when a list scrolls it back into view).
 */
@Composable fun SaharaErrorState(title: String, modifier: Modifier = Modifier, detail: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val reject = rememberSaharaReject()
    var felt by rememberSaveable(title) { mutableStateOf(false) }
    LaunchedEffect(title) { if (!felt) { felt = true; reject() } }
    Surface(modifier.fillMaxWidth().saharaAppear().saharaContentSize(), shape = RoundedCornerShape(SaharaRadius.Lg), color = SaharaColors.ErrorWash, border = BorderStroke(1.dp, SaharaColors.Error)) {
        Column(Modifier.padding(SaharaSpacing.Lg), verticalArrangement = Arrangement.spacedBy(SaharaSpacing.Md)) {
            Row(Modifier.announce(), verticalAlignment = Alignment.CenterVertically) {
                SaharaIconChip(Icons.Default.ErrorOutline, SaharaTone.Error, size = 40)
                Spacer(Modifier.width(SaharaSpacing.Md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = SaharaColors.Ink)
                    if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SaharaColors.InkSub)
                }
            }
            if (actionLabel != null && onAction != null) SaharaPrimaryButton(actionLabel, onAction, Modifier.fillMaxWidth(), icon = Icons.Default.Refresh)
        }
    }
}

/** Part of the day, for the Today illustration: a sun in the morning and afternoon, a low sun in the evening, a moon at night. */
internal enum class SaharaDayPart(val icon: ImageVector, val tone: SaharaTone) {
    Morning(Icons.Default.WbSunny, SaharaTone.Gold), Afternoon(Icons.Default.WbSunny, SaharaTone.Gold),
    Evening(Icons.Default.WbTwilight, SaharaTone.Marigold), Night(Icons.Default.Bedtime, SaharaTone.Indigo);
    companion object {
        fun at(hour: Int): SaharaDayPart = when (hour) { in 5..11 -> Morning; in 12..16 -> Afternoon; in 17..19 -> Evening; else -> Night }
        fun now(): SaharaDayPart = at(java.time.LocalTime.now().hour)
    }
}

/**
 * Today's calm idle illustration: the sun (or evening sun, or moon) for the time of day on a soft halo that breathes, the
 * icon floating and swaying slowly a few times and then resting. Decorative; still when motion is off.
 */
@Composable fun SaharaDaySky(modifier: Modifier = Modifier, size: Int = 32) {
    val part = remember { SaharaDayPart.now() }
    Box(modifier.size((size + 14).dp).testTag("sahara-day-${part.name.lowercase()}"), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().saharaBreathe(cycles = 4, amount = .1f).clip(CircleShape).background(part.tone.wash))
        SaharaIconChip(part.icon, part.tone, Modifier.saharaFloat(cycles = 4).saharaSway(if (part == SaharaDayPart.Night) 5f else 8f, cycles = 3).clip(CircleShape), size = size)
    }
}

/**
 * A calm, friendly empty line for a list inside a card ("No check-ins yet."): a small sprout that sways twice and rests,
 * beside the words. Lighter than [SaharaEmpty], for places where the list is one part of a screen.
 */
@Composable fun SaharaCalmEmpty(text: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.fillMaxWidth().saharaAppear().padding(horizontal = 4.dp, vertical = 4.dp).testTag("sahara-calm-empty"), verticalAlignment = Alignment.CenterVertically) {
        SaharaLottie(SaharaLottieKind.Sprout, Modifier.size(56.dp), iterations = 2)
        Spacer(Modifier.width(SaharaSpacing.Md))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = SaharaColors.InkSub)
            if (detail != null) Text(detail, fontSize = 14.sp, lineHeight = 19.sp, color = SaharaColors.InkSub)
        }
    }
}
