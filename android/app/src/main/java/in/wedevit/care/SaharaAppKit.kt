@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package `in`.wedevit.care

import android.content.Intent
import android.net.Uri
import androidx.annotation.RawRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/*
 * Sahara's shared page kit (25 Nov redesign). The owner said the app "looks AI-generated", so everything here is stock Material 3 with
 * its default styling and the theme's (dynamic) colours: no tinted boxes, no coloured icon chips on rows, no gradients, no pills.
 * One page = one [SaharaPage]; one row = one [SaharaRow]; one sheet of choices = one [SaharaActionSheet].
 */

// ───────────────────────────── Shell plumbing (provided by CareApp) ─────────────────────────────

/** The one snackbar of the app. CareApp provides it (shown above the bottom bar); a page outside CareApp shows its own. */
val LocalSaharaSnackbar = staticCompositionLocalOf { SnackbarHostState() }
/** True when somebody above already draws a host for [LocalSaharaSnackbar], so a page must not draw a second one. */
internal val LocalSaharaSnackbarHosted = staticCompositionLocalOf { false }

/**
 * What a page may ask of the app shell without new parameters: switch tab, open a pushed page, go back, and hide the bottom bar
 * (module pages call [SaharaHideBottomBar]). Null outside CareApp (doctor screens, tests), where all of it is a no-op.
 */
@Stable class SaharaShell {
    var switchTab: (Int) -> Unit = {}; internal set
    var open: (Int) -> Unit = {}; internal set
    var back: () -> Unit = {}; internal set
    internal var bottomBarHolds by mutableIntStateOf(0)
    val bottomBarHidden: Boolean get() = bottomBarHolds > 0
}
val LocalSaharaShell = staticCompositionLocalOf<SaharaShell?> { null }

/** While this is composed (and [hidden]), the app's bottom bar is hidden, as on any Android detail screen. */
@Composable fun SaharaHideBottomBar(hidden: Boolean = true) {
    val shell = LocalSaharaShell.current ?: return
    DisposableEffect(shell, hidden) {
        if (hidden) shell.bottomBarHolds++
        onDispose { if (hidden) shell.bottomBarHolds = (shell.bottomBarHolds - 1).coerceAtLeast(0) }
    }
}

// ───────────────────────────── SaharaPage ─────────────────────────────

@Composable private fun rememberSaharaTopBar(large: Boolean, transparentBar: Boolean): TopAppBarScrollBehavior {
    val state = rememberTopAppBarState()
    return if (large && !transparentBar) TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state) else TopAppBarDefaults.pinnedScrollBehavior(state)
}

/** The back arrow every pushed page shows (48dp, tag [tag]). */
@Composable fun SaharaBackButton(onBack: () -> Unit, tag: String = "page-back") {
    val t = careText()
    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag(tag)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back", "वापस")) }
}

@Composable private fun SaharaTopBar(title: String, large: Boolean, medium: Boolean, sb: TopAppBarScrollBehavior, navigationIcon: (@Composable () -> Unit)?, actions: @Composable RowScope.() -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    val twoLines by remember(sb, fontScale) { derivedStateOf { sb.state.collapsedFraction < .5f && fontScale <= 1.3f } }
    val ground = MaterialTheme.colorScheme.background
    val titleContent: @Composable () -> Unit = { Text(title, maxLines = if ((large || medium) && twoLines) 2 else 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.a11yHeading()) }
    val nav: @Composable () -> Unit = { navigationIcon?.invoke() }
    when {
        large -> LargeTopAppBar(title = titleContent, navigationIcon = nav, actions = actions, scrollBehavior = sb,
            colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = ground))
        medium -> MediumTopAppBar(title = titleContent, navigationIcon = nav, actions = actions, scrollBehavior = sb,
            colors = TopAppBarDefaults.mediumTopAppBarColors(containerColor = ground))
        else -> TopAppBar(title = titleContent, navigationIcon = nav, actions = actions, scrollBehavior = sb,
            colors = TopAppBarDefaults.topAppBarColors(containerColor = ground))
    }
}

/**
 * The raw page frame: Scaffold + Material top app bar (title left) + optional back arrow + FAB + pull to refresh. [body] gets the
 * Scaffold padding and must hold the page's one scrolling container (LazyColumn or a verticalScroll Column) so the bar collapses.
 * Use this for pages that already own their LazyColumn; most pages use [SaharaPage] or [SaharaColumnPage].
 *
 * Root tabs (Today, Medicines, Care, More) pass no [onBack] and get a large title. Pushed pages pass onBack: they get the arrow
 * (tag [backTag]), the system back is wired through [SaharaBackHandler], and the bottom bar is hidden while they are open.
 */
@Composable fun SaharaPageScaffold(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, large: Boolean = onBack == null,
    transparentBar: Boolean = false, navigationIcon: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null, refreshing: Boolean? = null, onRefresh: () -> Unit = {}, backTag: String = "page-back",
    body: @Composable (PaddingValues) -> Unit) {
    val sb = rememberSaharaTopBar(large, transparentBar)
    val hosted = LocalSaharaSnackbarHosted.current; val snackbar = LocalSaharaSnackbar.current
    if (onBack != null) { SaharaHideBottomBar(); SaharaBackHandler(true) { onBack() } }
    val nav: (@Composable () -> Unit)? = navigationIcon ?: if (onBack != null) ({ SaharaBackButton(onBack, backTag) }) else null
    Scaffold(modifier.fillMaxSize().nestedScroll(sb.nestedScrollConnection), containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        topBar = { SaharaTopBar(title, large = large && !transparentBar, medium = !large && !transparentBar, sb = sb, navigationIcon = nav, actions = actions) },
        floatingActionButton = { fab?.invoke() },
        snackbarHost = { if (!hosted) CareToastHost(snackbar) }) { padding ->
        if (refreshing != null) PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            body(PaddingValues(start = padding.calculateStartPadding(LocalLayoutDirection.current), end = padding.calculateEndPadding(LocalLayoutDirection.current), bottom = padding.calculateBottomPadding()))
        } else body(padding)
    }
}

/**
 * The page frame for list pages: [SaharaPageScaffold] around one LazyColumn ([listModifier] carries the list's test tag). The FAB
 * gets `expanded = true` while the list is at its top (use it for ExtendedFloatingActionButton). [contentPadding] is added to the
 * bar and navigation-bar padding.
 */
@Composable fun SaharaPage(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, large: Boolean = onBack == null,
    transparentBar: Boolean = false, navigationIcon: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable (expanded: Boolean) -> Unit)? = null, refreshing: Boolean? = null, onRefresh: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(), contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
    listModifier: Modifier = Modifier, backTag: String = "page-back", content: LazyListScope.() -> Unit) {
    val atTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val pageFab: (@Composable () -> Unit)? = if (fab != null) ({ fab(atTop) }) else null
    val dir = LocalLayoutDirection.current
    SaharaPageScaffold(title, modifier, onBack, large, transparentBar, navigationIcon, actions, fab = pageFab, refreshing = refreshing,
        onRefresh = onRefresh, backTag = backTag) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(listModifier.fillMaxSize(), state = listState, contentPadding = saharaPadding(padding, contentPadding, dir), content = content)
    }
}

/** The same page frame for a body that is a plain Column (forms, documents): it scrolls in one verticalScroll Column. */
@Composable fun SaharaColumnPage(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, large: Boolean = onBack == null,
    transparentBar: Boolean = false, navigationIcon: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {},
    fab: (@Composable () -> Unit)? = null, refreshing: Boolean? = null, onRefresh: () -> Unit = {}, scrollState: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), backTag: String = "page-back",
    contentColumn: @Composable ColumnScope.() -> Unit) {
    val dir = LocalLayoutDirection.current
    SaharaPageScaffold(title, modifier, onBack, large, transparentBar, navigationIcon, actions, fab, refreshing, onRefresh, backTag) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(saharaPadding(padding, contentPadding, dir)), content = contentColumn)
    }
}

/** Adds two paddings side by side (bar padding plus the page's own). */
internal fun saharaPadding(a: PaddingValues, b: PaddingValues, dir: androidx.compose.ui.unit.LayoutDirection): PaddingValues =
    PaddingValues(start = a.calculateStartPadding(dir) + b.calculateStartPadding(dir), top = a.calculateTopPadding() + b.calculateTopPadding(),
        end = a.calculateEndPadding(dir) + b.calculateEndPadding(dir), bottom = a.calculateBottomPadding() + b.calculateBottomPadding())

// ───────────────────────────── Rows ─────────────────────────────

/**
 * The one list row: a stock ListItem with a plain 24dp icon (no coloured circle), a headline, at most one supporting line and an
 * optional trailing control or chevron. [tone] only matters for [SaharaTone.Error] (destructive rows: red icon). [leading] replaces
 * the icon (a checkbox, a photo, a ring). At least 56dp tall, 72dp with a supporting line.
 */
@Composable fun SaharaRow(headline: String, modifier: Modifier = Modifier, supporting: String? = null, supportingColor: Color = Color.Unspecified,
    headlineColor: Color = Color.Unspecified, icon: ImageVector? = null, tone: SaharaTone = SaharaTone.Neutral, iconSize: Dp = 24.dp,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null, chevron: Boolean = onClick != null && trailing == null, enabled: Boolean = true, supportingMaxLines: Int = 2) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else .38f
    val iconTint = (if (tone == SaharaTone.Error) scheme.error else scheme.onSurfaceVariant).copy(alpha = alpha)
    val clickable = (onClick != null || onLongClick != null)
    val supportingSlot: (@Composable () -> Unit)? = if (!supporting.isNullOrBlank()) ({ Text(supporting, fontSize = 15.sp, lineHeight = 21.sp, maxLines = supportingMaxLines, overflow = TextOverflow.Ellipsis,
        color = (if (supportingColor == Color.Unspecified) scheme.onSurfaceVariant else supportingColor).copy(alpha = alpha)) }) else null
    val leadingSlot: (@Composable () -> Unit)? = leading ?: if (icon != null) ({ Icon(icon, null, Modifier.size(iconSize), tint = iconTint) }) else null
    val trailingSlot: (@Composable () -> Unit)? = trailing ?: if (chevron) ({ Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = scheme.onSurfaceVariant.copy(alpha = alpha)) }) else null
    ListItem(
        headlineContent = { Text(headline, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = (if (headlineColor == Color.Unspecified) scheme.onSurface else headlineColor).copy(alpha = alpha)) },
        supportingContent = supportingSlot,
        leadingContent = leadingSlot,
        trailingContent = trailingSlot,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.fillMaxWidth().heightIn(min = if (supporting.isNullOrBlank()) 56.dp else 72.dp)
            .then(if (clickable) Modifier.combinedClickable(enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = { onClick?.invoke() ?: onLongClick?.invoke() }) else Modifier)
            .semantics(mergeDescendants = true) {})
}

/** A section label above a group of rows (Material's settings style: small, in the accent colour). Optional action on the right. */
@Composable fun SaharaGroupLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).a11yHeading(), style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

/** A row with a Switch at its end; the whole row toggles (role Switch). [tag] goes on the row, "$tag-switch" on the switch. */
@Composable fun SaharaSwitchRow(headline: String, supporting: String?, checked: Boolean, onChecked: (Boolean) -> Unit, icon: ImageVector? = null,
    tone: SaharaTone = SaharaTone.Neutral, tag: String = "", modifier: Modifier = Modifier, enabled: Boolean = true) {
    SaharaRow(headline, modifier.then(if (tag.isNotBlank()) Modifier.testTag(tag) else Modifier).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked),
        supporting = supporting, icon = icon, tone = tone, enabled = enabled, chevron = false,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled, modifier = if (tag.isNotBlank()) Modifier.testTag("$tag-switch") else Modifier) })
}

// ───────────────────────────── Header, quick actions, tool grid ─────────────────────────────

/**
 * The top of a root screen, plain (owner override: no gradient, no centred hero): a left-aligned title, one supporting line and
 * whatever [content] follows (the mood faces on Today). [tone] is kept for older call sites and not drawn.
 */
@Composable fun SaharaHero(title: String, subtitle: String?, modifier: Modifier = Modifier, tone: SaharaTone = SaharaTone.Teal,
    art: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)) {
        art?.invoke()
        Text(title, Modifier.a11yHeading(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (!subtitle.isNullOrBlank()) Text(subtitle, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** One round action: [tone] is kept for older call sites; every circle uses the same stock tonal colour (not a rainbow). */
data class QuickActionSpec(val label: String, val icon: ImageVector, val tone: SaharaTone = SaharaTone.Neutral, val tag: String, val badge: Int = 0, val onClick: () -> Unit)

/** A horizontal row of round quick actions (Today). */
@Composable fun QuickActionRow(actions: List<QuickActionSpec>, modifier: Modifier = Modifier) {
    LazyRow(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        items(actions, key = { it.tag }) { QuickAction(it) }
    }
}

/** A filled-tonal circle ([circle], default 56dp) with a plain 24dp icon and a label (max 2 lines) under it; the whole cell is one button. */
@Composable fun QuickAction(spec: QuickActionSpec, circle: Dp = 56.dp, labelWidth: Dp = 88.dp, modifier: Modifier = Modifier) {
    val t = careText()
    val described = if (spec.badge > 0) "${spec.label}, ${spec.badge} " + t("new", "नए") else spec.label
    Column(modifier.then(if (labelWidth != Dp.Unspecified) Modifier.width(labelWidth) else Modifier).clip(RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClick = spec.onClick).padding(vertical = 8.dp, horizontal = 2.dp).testTag(spec.tag)
        .semantics(mergeDescendants = true) { contentDescription = described }, horizontalAlignment = Alignment.CenterHorizontally) {
        BadgedBox(badge = { if (spec.badge > 0) Badge { Text("${spec.badge}") } }) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(circle)) {
                Box(contentAlignment = Alignment.Center) { Icon(spec.icon, null, Modifier.size(24.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        SaharaFitText(spec.label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2, min = 12f, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/** A grid of round tools (Care tab), built from plain Rows so it can sit inside a LazyColumn item. 3 columns, 2 at very large text. */
@Composable fun ToolGrid(tools: List<QuickActionSpec>, modifier: Modifier = Modifier, columns: Int = if (LocalDensity.current.fontScale >= 1.5f) 2 else 3) {
    Column(modifier.fillMaxWidth()) {
        tools.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                row.forEach { QuickAction(it, circle = 64.dp, labelWidth = Dp.Unspecified, modifier = Modifier.weight(1f).heightIn(min = 120.dp)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ───────────────────────────── Ring and week strip ─────────────────────────────

/** One arc of a [SaharaRing]; [color] Unspecified means the theme's primary, [track] Unspecified the theme's surface variant. */
data class RingArc(val progress: Float, val color: Color = Color.Unspecified, val track: Color = Color.Unspecified)

/** A progress ring (doses taken, visits done). Arc i is inset by i * (stroke + gap). [center] sits in the middle. */
@Composable fun SaharaRing(arcs: List<RingArc>, description: String, modifier: Modifier = Modifier, size: Dp = 128.dp, stroke: Dp = 12.dp,
    gap: Dp = 6.dp, onClick: (() -> Unit)? = null, center: @Composable BoxScope.() -> Unit = {}) {
    val primary = MaterialTheme.colorScheme.primary; val trackDefault = MaterialTheme.colorScheme.surfaceContainerHighest
    val shown = arcs.map { arc -> animateFloatAsState(arc.progress.coerceIn(0f, 1f), SaharaSprings.effects(), label = "ring-arc").value }
    val first = arcs.firstOrNull()?.progress?.coerceIn(0f, 1f) ?: 0f
    Box(modifier.size(size).then(if (onClick != null) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick) else Modifier)
        .semantics(mergeDescendants = true) { contentDescription = description; progressBarRangeInfo = ProgressBarRangeInfo(first, 0f..1f); if (onClick != null) role = Role.Button },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx(); val step = (stroke + gap).toPx()
            arcs.forEachIndexed { i, arc ->
                val inset = w / 2 + i * step
                val side = this.size.minDimension - inset * 2
                if (side <= 0f) return@forEachIndexed
                val topLeft = androidx.compose.ui.geometry.Offset((this.size.width - side) / 2, (this.size.height - side) / 2)
                val box = androidx.compose.ui.geometry.Size(side, side)
                drawArc(if (arc.track == Color.Unspecified) trackDefault else arc.track, -90f, 360f, false, topLeft, box, style = Stroke(w, cap = StrokeCap.Round))
                if (shown[i] > 0f) drawArc(if (arc.color == Color.Unspecified) primary else arc.color, -90f, 360f * shown[i], false, topLeft, box, style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

/** One day of the week strip: [mood] is the check-in title ([CheckInMood.GOOD] / OKAY / HARD) or null; [symptom] when a symptom was noted. */
data class DayMark(val day: LocalDate, val mood: String?, val symptom: Boolean)

/** The last 7 days (oldest first, ending [today]) of [personId]'s check-in moods and symptom notes. Pure, unit-tested. */
internal fun weekMarks(items: List<CareItem>, personId: String, today: LocalDate, zone: ZoneId): List<DayMark> {
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val mine = items.filter { it.personId == personId && it.kind == "checkin" }
    fun dayOf(item: CareItem): LocalDate = Instant.ofEpochMilli(item.created).atZone(zone).toLocalDate()
    return days.map { day ->
        val onDay = mine.filter { dayOf(it) == day }
        val mood = onDay.filter { !SymptomNoteCodec.isStructured(it.detail) && !DistressCodec.isStructured(it.detail) && it.title in setOf(CheckInMood.GOOD, CheckInMood.OKAY, CheckInMood.HARD) }
            .maxByOrNull { it.created }?.title
        DayMark(day, mood, onDay.any { SymptomNoteCodec.isStructured(it.detail) })
    }
}

/** Seven round days (filled with the mood's colour, an empty ring when there was no check-in, a small dot for a symptom) and one line under them. */
@Composable fun SaharaWeekStrip(days: List<DayMark>, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = careText(); val hindi = careHindi()
    val narrow = LocalDensity.current.fontScale >= 1.3f
    val today = LocalDate.now()
    val empty = MaterialTheme.colorScheme.outline; val dot = SaharaTone.Rose.solid
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp).testTag("care-week")) {
        Row(Modifier.fillMaxWidth()) {
            days.forEach { d ->
                val moodWord = when (d.mood) { CheckInMood.GOOD -> t("Good", "अच्छा"); CheckInMood.OKAY -> t("Okay", "ठीक"); CheckInMood.HARD -> t("Hard", "कठिन"); else -> t("No check-in", "कोई हाल नहीं") }
                val spoken = d.day.format(careDateFormat("EEEE", hindi)) + ", " + moodWord + if (d.symptom) ", " + t("symptom noted", "लक्षण लिखा गया") else ""
                val fill = when (d.mood) { CheckInMood.GOOD -> SaharaTone.Success.solid; CheckInMood.OKAY -> SaharaTone.Gold.solid; CheckInMood.HARD -> SaharaTone.Rose.solid; else -> null }
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = spoken }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(40.dp)) {
                        val r = this.size.minDimension / 2
                        if (fill != null) drawCircle(fill, r) else drawCircle(empty, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                        if (d.symptom) drawCircle(dot, 3.dp.toPx(), androidx.compose.ui.geometry.Offset(this.size.width - 4.dp.toPx(), 4.dp.toPx()))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(d.day.format(careDateFormat(if (narrow) "EEEEE" else "EEE", hindi)), fontSize = if (narrow) 13.sp else 12.sp, maxLines = 1,
                        fontWeight = if (d.day == today) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (summary.isNotBlank()) Text(summary, Modifier.padding(top = 8.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ───────────────────────────── Doses ─────────────────────────────

@Composable private fun doseStateText(state: MedicineDoseState): String { val t = careText(); return when (state) {
    MedicineDoseState.TAKEN -> t("Taken", "ली"); MedicineDoseState.MISSED -> t("Not taken", "नहीं ली")
    MedicineDoseState.SKIPPED -> t("Skipped", "छोड़ी"); MedicineDoseState.PENDING -> t("Not marked yet", "अभी दर्ज नहीं")
} }

/**
 * The one dose control: a 48dp round tick. Tap on a dose not yet marked = Taken ([onTake]); tap on a marked one opens the choices
 * ([onOpen]) so nobody un-marks by accident. [family]: a small people badge when family logged it.
 */
@Composable fun DoseToggle(state: MedicineDoseState, medicine: String, enabled: Boolean, family: Boolean = false, onTake: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = careText(); val scheme = MaterialTheme.colorScheme
    val haptic = rememberSaharaHaptic()
    val pop by animateFloatAsState(if (state == MedicineDoseState.TAKEN) 1f else .92f, SaharaSprings.gentle(), label = "dose-toggle")
    val word = doseStateText(state)
    IconToggleButton(checked = state == MedicineDoseState.TAKEN, enabled = enabled,
        onCheckedChange = { if (state == MedicineDoseState.PENDING) { haptic(); onTake() } else onOpen() },
        modifier = modifier.size(48.dp).semantics { stateDescription = word; contentDescription = t("Mark $medicine taken", "$medicine ली गई दर्ज करें") }) {
        Box(Modifier.size(34.dp).scale(pop), contentAlignment = Alignment.Center) {
            when (state) {
                MedicineDoseState.PENDING -> Box(Modifier.fillMaxSize().border(2.5.dp, if (enabled) scheme.primary else scheme.outline, CircleShape))
                MedicineDoseState.TAKEN -> Box(Modifier.fillMaxSize().background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, Modifier.size(22.dp), tint = scheme.onPrimary) }
                MedicineDoseState.MISSED -> Box(Modifier.fillMaxSize().background(scheme.errorContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Close, null, Modifier.size(20.dp), tint = scheme.onErrorContainer) }
                MedicineDoseState.SKIPPED -> Box(Modifier.fillMaxSize().background(scheme.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Remove, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant) }
            }
            if (family && state != MedicineDoseState.PENDING) Icon(Icons.Default.Groups, null, Modifier.size(16.dp).align(Alignment.BottomEnd).background(scheme.surface, CircleShape), tint = scheme.onSurfaceVariant)
        }
    }
}

/** Whether TalkBack (touch exploration) is on: swipe shortcuts are switched off then, and custom actions do the job. */
@Composable internal fun rememberTouchExploration(): Boolean {
    val context = LocalContext.current
    return remember(context) { (context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)?.isTouchExplorationEnabled == true }
}

/**
 * One dose as a row: the strip photo (real content) or a plain pill icon, the medicine name (18sp), one supporting line and the
 * [DoseToggle]. Swipe right past 40% marks it Taken (a shortcut only; off for TalkBack and once marked); the row springs back.
 * Tap or long-press opens the dose's choices ([onOpen]). Use the LazyColumn key "${item.id}:${dose.key}".
 */
@Composable fun DoseRow(item: CareItem, dose: MedicineDose, state: MedicineDoseState, photo: File?, supporting: String, enabled: Boolean, family: Boolean,
    onMark: (choice: MedicineDoseChoice, quick: Boolean) -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val t = careText(); val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val talkBack = rememberTouchExploration()
    val swipe = rememberSwipeToDismissBoxState(initialValue = SwipeToDismissBoxValue.Settled, positionalThreshold = { it * .4f })
    val labels = listOf(MedicineDoseChoice.TAKEN to t("Taken", "ली"), MedicineDoseChoice.NOT_TAKEN to t("Not taken", "नहीं ली"), MedicineDoseChoice.SKIPPED to t("Skipped", "छोड़ी"))
    val doseSupporting: (@Composable () -> Unit)? = if (supporting.isNotBlank()) ({ Text(supporting, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = scheme.onSurfaceVariant) }) else null
    SwipeToDismissBox(swipe, modifier = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = enabled && state == MedicineDoseState.PENDING && !talkBack, enableDismissFromEndToStart = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.StartToEnd) onMark(MedicineDoseChoice.TAKEN, true); scope.launch { swipe.reset() } },
        backgroundContent = {
            Row(Modifier.fillMaxSize().background(scheme.secondaryContainer).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, null, tint = scheme.onSecondaryContainer); Spacer(Modifier.width(8.dp))
                Text(t("Taken", "ली"), color = scheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
        }) {
        ListItem(
            headlineContent = { Text(item.title, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = doseSupporting,
            leadingContent = { if (photo != null) StripThumb(photo, 56) else Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Medication, null, tint = scheme.onSurfaceVariant) } },
            trailingContent = { DoseToggle(state, item.title, enabled, family, onTake = { onMark(MedicineDoseChoice.TAKEN, true) }, onOpen = onOpen) },
            colors = ListItemDefaults.colors(containerColor = scheme.background),
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).combinedClickable(role = Role.Button, onLongClick = onOpen, onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    customActions = (if (enabled) labels.map { (choice, label) -> CustomAccessibilityAction(label) { onMark(choice, false); true } } else emptyList()) +
                        CustomAccessibilityAction(t("Details", "पूरी जानकारी")) { onOpen(); true }
                })
    }
}

/** A row of a bottom sheet (Add, Edit/Remove, SOS extras, a dose's actions). [destructive] rows are red. */
data class SheetAction(val label: String, val icon: ImageVector, val tag: String, val tone: SaharaTone = SaharaTone.Neutral, val supporting: String? = null,
    val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * A dose's choices: the strip photo (160dp), the name and dose line, three full-width 56dp buttons (Taken, Not taken, Skipped; the
 * current one carries a tick), then the [actions] as rows. [doseLabel] defaults to "8:00 AM dose" from [dose]'s time.
 */
@Composable fun DoseSheet(item: CareItem, dose: MedicineDose, state: MedicineDoseState, photo: File?, enabled: Boolean, onChoice: (MedicineDoseChoice) -> Unit,
    actions: List<SheetAction>, onDismiss: () -> Unit, doseLabel: String? = null) {
    val t = careText(); val hindi = careHindi(); val scheme = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true); val scope = rememberCoroutineScope()
    fun close(then: () -> Unit) { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); then() } }
    val clock = if (dose.timed) runCatching { java.time.LocalTime.parse(dose.time).format(careDateFormat("h:mm a", hindi)) }.getOrDefault(dose.time) else ""
    val time = doseLabel ?: if (dose.timed) t("$clock dose", "$clock की खुराक") else t("Any time", "किसी भी समय")
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, modifier = Modifier.testTag("dose-sheet")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            if (photo != null) { StripThumb(photo, 160); Spacer(Modifier.height(16.dp)) }
            Text(item.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.a11yHeading())
            Text(time, Modifier.padding(top = 4.dp), fontSize = 16.sp, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            val mark: @Composable (MedicineDoseChoice, MedicineDoseState, String, String) -> Unit = { choice, matches, label, tag ->
                val current = state == matches
                val content: @Composable RowScope.() -> Unit = { if (current) { Icon(Icons.Default.Check, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }; Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                val m = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 4.dp).testTag(tag)
                when (choice) {
                    MedicineDoseChoice.TAKEN -> Button(onClick = { close { onChoice(choice) } }, enabled = enabled, modifier = m, content = content)
                    MedicineDoseChoice.NOT_TAKEN -> OutlinedButton(onClick = { close { onChoice(choice) } }, enabled = enabled, modifier = m, colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error), content = content)
                    MedicineDoseChoice.SKIPPED -> OutlinedButton(onClick = { close { onChoice(choice) } }, enabled = enabled, modifier = m, content = content)
                }
            }
            mark(MedicineDoseChoice.TAKEN, MedicineDoseState.TAKEN, t("Taken", "ली"), "dose-sheet-taken")
            mark(MedicineDoseChoice.NOT_TAKEN, MedicineDoseState.MISSED, t("Not taken", "नहीं ली"), "dose-sheet-not-taken")
            mark(MedicineDoseChoice.SKIPPED, MedicineDoseState.SKIPPED, t("Skipped", "छोड़ी"), "dose-sheet-skipped")
        }
        if (actions.isNotEmpty()) { HorizontalDivider(Modifier.padding(top = 16.dp)); SheetActionRows(actions) { a -> close(a.onClick) } }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable private fun SheetActionRows(actions: List<SheetAction>, onPick: (SheetAction) -> Unit) {
    actions.forEach { a ->
        SaharaRow(a.label, Modifier.testTag(a.tag), supporting = a.supporting, icon = a.icon, tone = if (a.destructive) SaharaTone.Error else a.tone,
            headlineColor = if (a.destructive) MaterialTheme.colorScheme.error else Color.Unspecified, chevron = false, onClick = { onPick(a) })
    }
}

// ───────────────────────────── Sheets ─────────────────────────────

/**
 * One bottom sheet of big rows: every Add sheet, Edit/Remove, the SOS extras, the dose group. A tap hides the sheet, then calls
 * [onDismiss] and the row's action. System back closes the sheet first (ModalBottomSheet handles it).
 */
@Composable fun SaharaActionSheet(title: String?, actions: List<SheetAction>, onDismiss: () -> Unit, header: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true); val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            title?.let { Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp).a11yHeading(), style = MaterialTheme.typography.titleLarge) }
            header?.invoke(this)
            SheetActionRows(actions) { a -> scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss(); a.onClick() } }
            footer?.invoke(this)
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

/** The language choice as a sheet of big rows: the name in its own script, the English name under it, a radio button. */
@Composable fun SaharaLanguageSheet(onDismiss: () -> Unit) {
    val language = LocalCareLanguage.current ?: return
    val t = careText()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true); val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, modifier = Modifier.testTag("care-language-options")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(t("Language", "भाषा"), Modifier.padding(horizontal = 24.dp, vertical = 8.dp).a11yHeading(), style = MaterialTheme.typography.titleLarge)
            CareLang.entries.forEach { option ->
                val chosen = option == language.lang
                val english: (@Composable () -> Unit)? = if (option != CareLang.EN) ({ Text(option.englishName, fontSize = 14.sp) }) else null
                ListItem(headlineContent = { Text(option.label, fontSize = 20.sp) },
                    supportingContent = english,
                    trailingContent = { RadioButton(selected = chosen, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("care-language-${option.code}")
                        .selectable(chosen, role = Role.RadioButton) { language.choose(option); scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } })
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

// ───────────────────────────── Top-bar pieces ─────────────────────────────

/** SOS in the top bar: always one tap away, red (the one colour that stays). Tag today-sos. */
@Composable fun SosTopButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = careText()
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp).testTag("today-sos"), contentPadding = PaddingValues(horizontal = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = SaharaTone.Error.wash, contentColor = SaharaTone.Error.ink)) {
        Icon(Icons.Default.Call, null, Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
        Text(t("SOS", "SOS मदद"), fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
    }
}

/**
 * Everything the old header held, for the avatar menu: switch person, add a person, invite family, language, Simple Mode and (in
 * the demo walkthrough) the demo-user items. CareApp builds it and provides it through [LocalSaharaPersonMenu].
 */
data class PersonMenu(val people: List<Person>, val selectedId: String, val onSelect: (String) -> Unit, val onAddPerson: (() -> Unit)?, val onInvite: (() -> Unit)?,
    val onLanguage: () -> Unit, val onSimpleMode: (() -> Unit)?, val walkthroughItems: List<SheetAction> = emptyList(), val caption: String? = null,
    val inviteLabel: String? = null, val enabled: Boolean = true)
val LocalSaharaPersonMenu = compositionLocalOf<PersonMenu?> { null }

/** The avatar button for a root screen's top bar, from CareApp's [LocalSaharaPersonMenu]; nothing outside CareApp. */
@Composable fun SaharaPersonAction(modifier: Modifier = Modifier, showCaption: Boolean = false) {
    val menu = LocalSaharaPersonMenu.current ?: return
    val name = menu.people.firstOrNull { it.id == menu.selectedId }?.name.orEmpty()
    PersonAvatarButton(name, if (showCaption) menu.caption else null, menu, modifier)
}

/**
 * The person's initial in a 40dp circle (with an optional short caption beside it). It opens a menu: the people (a tick on the one
 * shown), Add another person, Invite family, Language, Simple mode, and the walkthrough items. Tags: care-person-menu (outer),
 * selected-care-profile (the button; walkthrough-user-menu in the demo), person-<id>, add-care-person, invite-family-top.
 */
@Composable fun PersonAvatarButton(name: String, caption: String?, menu: PersonMenu, modifier: Modifier = Modifier) {
    val t = careText(); val scheme = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(menu.enabled) { if (!menu.enabled) open = false }
    val walkthrough = menu.walkthroughItems.isNotEmpty()
    Box(modifier.testTag("care-person-menu")) {
        Row(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable(enabled = menu.enabled, role = Role.Button) { open = true }
            .testTag(if (walkthrough) "walkthrough-user-menu" else "selected-care-profile")
            .semantics(mergeDescendants = true) { contentDescription = t("Switch care profile, $name", "देखभाल प्रोफ़ाइल बदलें, $name") }
            .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(if (menu.enabled) scheme.primary else scheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = scheme.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (!caption.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Column(Modifier.widthIn(max = 160.dp)) {
                    Text(caption, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        DropdownMenu(expanded = open && menu.enabled, onDismissRequest = { open = false }) {
            menu.people.forEach { p ->
                val chosen = p.id == menu.selectedId
                DropdownMenuItem(text = { Text(p.name, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal) }, modifier = Modifier.heightIn(min = 48.dp).testTag("person-${p.id}"),
                    leadingIcon = { Icon(if (chosen) Icons.Default.CheckCircle else Icons.Default.PersonOutline, null, tint = if (chosen) scheme.primary else scheme.onSurfaceVariant) },
                    onClick = { open = false; menu.onSelect(p.id) })
            }
            if (menu.onAddPerson != null || menu.onInvite != null) HorizontalDivider()
            menu.onInvite?.let { invite -> DropdownMenuItem(text = { Text(menu.inviteLabel ?: t("Invite family", "परिवार को बुलाएँ")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("invite-family-top"),
                leadingIcon = { Icon(Icons.Default.GroupAdd, null) }, onClick = { open = false; invite() }) }
            menu.onAddPerson?.let { add -> DropdownMenuItem(text = { Text(t("Add another person", "एक और व्यक्ति जोड़ें")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("add-care-person"),
                leadingIcon = { Icon(Icons.Default.PersonAdd, null) }, onClick = { open = false; add() }) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text(t("Language", "भाषा")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-person-language"),
                leadingIcon = { Icon(Icons.Default.Translate, null) }, onClick = { open = false; menu.onLanguage() })
            menu.onSimpleMode?.let { simple -> DropdownMenuItem(text = { Text(t("Simple mode", "सरल मोड")) }, modifier = Modifier.heightIn(min = 48.dp).testTag("care-person-simple-mode"),
                leadingIcon = { Icon(Icons.Default.TouchApp, null) }, onClick = { open = false; simple() }) }
            if (walkthrough) {
                HorizontalDivider()
                menu.walkthroughItems.forEach { a -> DropdownMenuItem(text = { Text(a.label, color = if (a.destructive) scheme.error else Color.Unspecified) }, modifier = Modifier.heightIn(min = 48.dp).testTag(a.tag),
                    leadingIcon = { Icon(a.icon, null) }, onClick = { open = false; a.onClick() }) }
            }
        }
    }
}

/**
 * A document shown inside the app (the privacy policy): raw text from res/raw, Hindi when chosen and available. Lines starting
 * with "# " are headings; other lines are body text. Other dictionary languages show the English text with a note. [webUrl]
 * adds a small "Open on the website" link at the end.
 */
@Composable fun InAppDocumentPage(title: String, @RawRes en: Int, @RawRes hi: Int?, onBack: () -> Unit, webUrl: String? = null) {
    val t = careText(); val lang = careLanguage(); val context = LocalContext.current
    val useHindi = lang == CareLang.HI && hi != null
    val text = remember(useHindi, en, hi) { runCatching { context.resources.openRawResource(if (useHindi) hi!! else en).bufferedReader().use { it.readText() } }.getOrDefault("") }
    SaharaColumnPage(title, onBack = onBack, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        if (lang != CareLang.EN && !useHindi) Text(t("This policy is shown in English.", "यह नीति अंग्रेज़ी में दिखाई गई है।"), Modifier.padding(bottom = 8.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer { Column(Modifier.fillMaxWidth()) {
            text.lines().forEach { line ->
                when {
                    line.startsWith("# ") -> Text(line.removePrefix("# ").trim(), Modifier.padding(top = 20.dp, bottom = 4.dp).a11yHeading(), fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold)
                    line.isNotBlank() -> Text(line.trim(), Modifier.padding(vertical = 4.dp), fontSize = 16.sp, lineHeight = 24.sp)
                }
            }
        } }
        if (webUrl != null) TextButton(onClick = { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }, modifier = Modifier.padding(top = 16.dp).heightIn(min = 48.dp).testTag("document-open-website")) {
            Text(t("Open on the website", "वेबसाइट पर खोलें"))
        }
    }
}
