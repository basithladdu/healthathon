package `in`.wedevit.care

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.animation.animateColorAsState
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
 * until it fits whole. Hidden until settled, so nobody sees the steps.
 */
@Composable fun SaharaFitText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.labelLarge, color: Color = Color.Unspecified,
    maxLines: Int = 2, min: Float = 11f, fontWeight: FontWeight? = null, textAlign: androidx.compose.ui.text.style.TextAlign? = androidx.compose.ui.text.style.TextAlign.Center) {
    val base = if (style.fontSize.isSp) style.fontSize.value else 15f
    var size by remember(text, base) { mutableFloatStateOf(base) }
    var settled by remember(text, base) { mutableStateOf(false) }
    Text(text, modifier.drawWithContent { if (settled) drawContent() }, color = color, fontWeight = fontWeight, maxLines = maxLines, softWrap = maxLines > 1,
        overflow = TextOverflow.Clip, textAlign = textAlign,
        style = style.copy(fontSize = size.sp, lineHeight = (size * 1.3f).sp, lineBreak = androidx.compose.ui.text.style.LineBreak.Heading, hyphens = androidx.compose.ui.text.style.Hyphens.None),
        onTextLayout = { layout ->
            val ends = (0 until layout.lineCount).map { layout.getLineEnd(it) }
            val bad = layout.hasVisualOverflow || splitsWord(text, ends)
            if (bad && size > min) size = maxOf(min, size - 1f) else settled = true
        })
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
    SaharaStatusBarIcons(lightIcons = true)
    Surface(color = SaharaColors.Band, contentColor = SaharaColors.OnBand, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.statusBarsPadding().heightIn(min = 60.dp).padding(start = if (navigation == null) 16.dp else 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (navigation != null) { navigation(); Spacer(Modifier.width(4.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, Modifier.a11yHeading(), style = MaterialTheme.typography.titleLarge, fontSize = 21.sp, lineHeight = 26.sp, color = SaharaColors.OnBand, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SaharaColors.OnBandSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions()
        }
    }
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
    val fill by animateColorAsState(if (pressed) tone.ink else tone.solid, SaharaMotion.tween(SaharaMotion.PRESS_MS), label = "tile-press")
    val shape = RoundedCornerShape(SaharaRadius.Lg)
    Column(modifier.saharaAppear().saharaPress(interaction).heightIn(min = 112.dp).shadow(if (pressed) 1.dp else 4.dp, shape).clip(shape).background(fill)
        .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(color = tone.onSolid), role = Role.Button, onClick = onClick).a11yCard().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(tone.onSolid.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = tone.onSolid)
            }
            Spacer(Modifier.weight(1f))
            if (value != null) Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = tone.onSolid, maxLines = 1)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, color = tone.onSolid, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = tone.onSolid, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Two tiles per row; the last one spans the row when the count is odd. */
@Composable fun SaharaBentoGrid(modifier: Modifier = Modifier, gap: Int = 10, tiles: List<@Composable (Modifier) -> Unit>) {
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
    status: Pair<String, SaharaTone>? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.saharaPress(interaction).clip(RoundedCornerShape(SaharaRadius.Md))
        .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick) else Modifier)
        .a11yCard().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { SaharaIconChip(icon, tone, size = 36); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = SaharaColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = SaharaColors.InkSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (status != null) { Spacer(Modifier.width(8.dp)); SaharaStatusPill(status.first, status.second) }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        else if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = tone.ink)
    }
}

/**
 * A register card holding rows separated by hairlines (AI-FIR register). White by default; with a section [tone] it
 * takes that section's wash, a 1.5 dp border in its solid and a solid accent bar down the leading edge.
 */
@Composable fun SaharaRegister(modifier: Modifier = Modifier, tone: SaharaTone? = null, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth().saharaAppear(), shape = RoundedCornerShape(SaharaRadius.Lg), color = tone?.wash ?: SaharaColors.Surface,
        border = BorderStroke(if (tone != null) 1.5.dp else 1.dp, tone?.solid ?: SaharaColors.Edge)) {
        Column(Modifier.then(if (tone != null) Modifier.saharaAccentBar(tone) else Modifier).padding(vertical = 4.dp), content = content)
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
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(SaharaRadius.Lg + 4.dp), color = tone.band, contentColor = tone.onSolid, shadowElevation = 3.dp) {
        Row(Modifier.heightIn(min = 72.dp).padding(start = if (onBack != null) 4.dp else 18.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(48.dp).then(if (backTag != null) Modifier.testTag(backTag) else Modifier)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस"), tint = tone.onSolid)
            }
            Column(Modifier.weight(1f).padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, Modifier.a11yHeading(), fontFamily = SaharaTitleFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, color = tone.onSolid)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = tone.onSolid, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        }
    }
}

/** The one action on a section band: a solid white button with the section's ink label, so it still reads as a button on the colour. */
@Composable fun SaharaBandButton(text: String, onClick: () -> Unit, tone: SaharaTone, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(SaharaRadius.Md),
        colors = ButtonDefaults.buttonColors(containerColor = SaharaColors.Surface, contentColor = tone.ink, disabledContainerColor = SaharaColors.Well, disabledContentColor = SaharaColors.InkFaint),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), modifier = modifier.heightIn(min = 48.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
    }
}

/** Section art on a band: the illustration sits on a white disc so it never melts into the colour. Decorative. */
@Composable fun SaharaBandArt(art: Int, size: Int = 52) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(SaharaColors.Surface), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(saharaImage(art, (size - 8).dp), null, Modifier.size((size - 8).dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
    }
}

/** Status pill: the tone's bright solid with bold white text, content-sized, never breaks a word. [shimmer]: a soft idle sweep (the "next dose" pill). */
@Composable fun SaharaStatusPill(text: String, tone: SaharaTone, modifier: Modifier = Modifier, icon: ImageVector? = null, shimmer: Boolean = false) {
    Row(modifier.clip(RoundedCornerShape(SaharaRadius.Pill)).background(tone.solid).then(if (shimmer) Modifier.saharaShimmer() else Modifier)
        .padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = tone.onSolid); Spacer(Modifier.width(4.dp)) }
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = tone.onSolid, maxLines = 1, softWrap = false)
    }
}

/** Trailing arrow for a button that opens another screen: it says where it goes and shows the way. */
@Composable private fun GoesArrow() { Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(22.dp)) }

/**
 * The one main action: a solid bright fill (rust, or [tone]), bold white label, a soft shadow that sinks and a darker
 * fill while pressed, at least 56 dp. [navigates]: it opens another screen, so a chevron follows the label.
 */
@Composable fun SaharaPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
    tone: SaharaTone = SaharaTone.Rust, busy: Boolean = false, navigates: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(if (pressed) tone.ink else tone.solid, SaharaMotion.tween(SaharaMotion.PRESS_MS), label = "button-press")
    Button(onClick = onClick, enabled = enabled && !busy, interactionSource = interaction, shape = RoundedCornerShape(SaharaRadius.Md),
        colors = ButtonDefaults.buttonColors(containerColor = fill, contentColor = tone.onSolid, disabledContainerColor = SaharaColors.Well, disabledContentColor = SaharaColors.InkFaint),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp, disabledElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), modifier = modifier.heightIn(min = 56.dp).saharaPress(interaction)) {
        if (busy) { CircularProgressIndicator(Modifier.size(20.dp), color = tone.onSolid, strokeWidth = 2.5.dp); Spacer(Modifier.width(10.dp)) }
        else if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (navigates && !busy) GoesArrow()
    }
}

/** Second action: white fill with a strong 2 dp border in the tone's bright colour, bold ink label, tinted while pressed, at least 56 dp. */
@Composable fun SaharaSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true,
    tone: SaharaTone = SaharaTone.Rust, navigates: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(if (pressed) tone.wash else SaharaColors.Surface, SaharaMotion.tween(SaharaMotion.PRESS_MS), label = "outline-press")
    OutlinedButton(onClick = onClick, enabled = enabled, interactionSource = interaction, shape = RoundedCornerShape(SaharaRadius.Md),
        border = BorderStroke(2.dp, if (enabled) tone.solid else SaharaColors.Edge),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = fill, contentColor = tone.ink, disabledContentColor = SaharaColors.InkFaint),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), modifier = modifier.heightIn(min = 56.dp).saharaPress(interaction)) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (navigates) GoesArrow()
    }
}

/** Section header: the section's icon chip (or a short bright bar), the title, and an optional "See all" style action with a chevron. */
@Composable fun SaharaSectionHeader(title: String, modifier: Modifier = Modifier, tone: SaharaTone = SaharaTone.Rust, actionLabel: String? = null, onAction: (() -> Unit)? = null,
    icon: ImageVector? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) SaharaIconChip(icon, tone, size = 32) else Box(Modifier.size(width = 5.dp, height = 22.dp).clip(CircleShape).background(tone.solid))
        Spacer(Modifier.width(10.dp))
        Text(title, Modifier.weight(1f).a11yHeading(), style = MaterialTheme.typography.titleMedium, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = tone.ink)
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = tone.ink)) {
            Text(actionLabel, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(20.dp))
        }
    }
}

/** Inline loading for part of a screen: a small ring and one line, in place of the content that is coming. */
@Composable fun SaharaInlineLoading(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp).announce(), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), color = SaharaColors.Primary, trackColor = SaharaColors.PrimaryWash, strokeWidth = 3.dp)
        Spacer(Modifier.width(12.dp)); Text(text, style = MaterialTheme.typography.bodyMedium, color = SaharaColors.InkSub)
    }
}

/** Error state: a bright icon, what went wrong in one line, and the one thing to do (usually Try again) as a real button. */
@Composable fun SaharaErrorState(title: String, modifier: Modifier = Modifier, detail: String? = null, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Surface(modifier.fillMaxWidth().saharaAppear(), shape = RoundedCornerShape(SaharaRadius.Lg), color = SaharaColors.ErrorWash, border = BorderStroke(1.dp, SaharaColors.Error)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.announce(), verticalAlignment = Alignment.CenterVertically) {
                SaharaIconChip(Icons.Default.ErrorOutline, SaharaTone.Error, size = 40)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = SaharaColors.Ink)
                    if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = SaharaColors.InkSub)
                }
            }
            if (actionLabel != null && onAction != null) SaharaPrimaryButton(actionLabel, onAction, Modifier.fillMaxWidth(), icon = Icons.Default.Refresh)
        }
    }
}
