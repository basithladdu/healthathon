@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One plotted point. [y] null leaves a gap in the line. [color] overrides the line colour for the dot. */
data class ChartPoint(val x: Double, val y: Double?, val color: Color? = null)

/** A label under the x axis. Labels that would overlap an earlier one in the list are skipped. */
data class ChartXLabel(val x: Double, val text: String, val strong: Boolean = false)

/** A soft horizontal band in y units. */
data class ChartBand(val from: Double, val to: Double, val color: Color, val alpha: Float = 0.12f)

/** A dashed vertical marker (for example a treatment day). A non-blank [label] is written small at the top of the line. */
data class ChartMarker(val x: Double, val color: Color, val label: String = "")

/** How far back a time chart reaches (x values in epoch milliseconds). [days] null shows everything from the first entry. */
enum class ChartRange(val days: Long?, val en: String, val hi: String) {
    WEEK(7, "1 week", "1 हफ़्ता"), MONTH(30, "1 month", "1 महीना"), QUARTER(91, "3 months", "3 महीने"), ALL(null, "All", "सब");
    fun label(hindi: Boolean): String = careTr(hindi, en, hi)
}

/** Pure range filtering for time charts, shared by the patient and doctor screens. Days follow the phone's time zone. */
object ChartRangeRules {
    /** First moment shown: the start of the day [ChartRange.days] − 1 days before [now]'s day ("1 week" = today and the 6 days before). Null for ALL. */
    fun start(range: ChartRange, now: Long): Long? = range.days?.let { careEpoch(careDay(now).minusDays(it - 1)) }
    /** Points on or after the range's start; ALL keeps every point. */
    fun filter(points: List<ChartPoint>, range: ChartRange, now: Long): List<ChartPoint> = start(range, now)?.let { s -> points.filter { it.x >= s } } ?: points
    /** The x span drawn: the range's start to the later of now and the last point; null for ALL (the chart then uses the data's own span). */
    fun span(points: List<ChartPoint>, range: ChartRange, now: Long): Pair<Double, Double>? =
        start(range, now)?.let { s -> s.toDouble() to maxOf(now.toDouble(), points.maxOfOrNull { it.x } ?: now.toDouble()) }
    /** Ranges worth offering: a shorter range only when it hides older entries and still shows at least one. ALL is always offered, last. */
    fun choices(points: List<ChartPoint>, now: Long): List<ChartRange> {
        val xs = points.filter { it.y != null }.map { it.x }
        return ChartRange.values().filter { r ->
            val s = start(r, now)
            s == null || (xs.any { it < s } && xs.any { it >= s })
        }
    }
    /** 4.0 -> "4", 4.256 -> "4.26". */
    fun number(v: Double): String = v.toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/**
 * Connected line chart drawn on a Canvas. Points are joined in the order given (so points sharing an x still follow recorded order);
 * null y values break the line. The last non-null point is highlighted and labelled with [latestText].
 */
@Composable fun CareLineChart(
    points: List<ChartPoint>,
    yMin: Double,
    yMax: Double,
    yTicks: List<Double>,
    yText: (Double) -> String,
    xLabels: List<ChartXLabel>,
    description: String,
    latestText: String,
    tag: String,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    lineColor: Color = Rust,
    bands: List<ChartBand> = emptyList(),
    markers: List<ChartMarker> = emptyList(),
    xMin: Double? = null,
    xMax: Double? = null,
    xLabelLines: Int = 1,
    /** When set, every point (not only the latest) carries its own value label. */
    pointText: ((Double) -> String)? = null,
    /** Time charts only (x in epoch ms): ALL draws every point; a shorter range keeps the points, markers and labels from its start to now. */
    range: ChartRange = ChartRange.ALL,
    now: Long = System.currentTimeMillis(),
    /** Above this many points the per-point value labels are left out so the line stays readable (the latest keeps its label). */
    pointTextLimit: Int = 14,
    /** Optional axis names: [yTitle] above the value labels, [xTitle] centred under the dates. */
    yTitle: String? = null,
    xTitle: String? = null,
) {
    val measurer = rememberTextMeasurer()
    val shownPoints = remember(points, range, now / 3_600_000L) { ChartRangeRules.filter(points, range, now) }
    val progress = remember(shownPoints) { Animatable(0f) }
    LaunchedEffect(shownPoints) { progress.snapTo(0f); progress.animateTo(1f, SaharaMotion.tween(700)) }
    val axisStyle = TextStyle(color = DoctorCardSub, fontSize = 12.sp, lineHeight = 14.sp)
    val xStyle = axisStyle.copy(textAlign = TextAlign.Center)
    val markerStyle = TextStyle(color = DoctorCardInk, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)
    val span = ChartRangeRules.span(shownPoints, range, now)
    val lo = span?.first ?: xMin ?: shownPoints.minOfOrNull { it.x } ?: 0.0
    val hi = span?.second ?: xMax ?: shownPoints.maxOfOrNull { it.x } ?: 1.0
    val shownMarkers = markers.filter { it.x >= minOf(lo, hi) && it.x <= maxOf(lo, hi) }
    val shownLabels = xLabels.filter { it.x >= minOf(lo, hi) && it.x <= maxOf(lo, hi) }
    val labelEach = shownPoints.count { it.y != null } <= pointTextLimit
    val titleStyle = axisStyle.copy(color = DoctorCardInk, fontWeight = FontWeight.SemiBold)
    Canvas(modifier.fillMaxWidth().height(height).saharaReveal().clearAndSetSemantics { contentDescription = description }.testTag("$tag-chart")) {
        // Room above the plot for treatment-day labels, so they never sit on the top gridline.
        val topPad = ((if (shownMarkers.any { it.label.isNotBlank() }) 22 else 14) + (if (yTitle != null) 16 else 0)).dp.toPx()
        val bottomPad = (10 + 15 * xLabelLines + (if (xTitle != null) 16 else 0)).dp.toPx()
        val rightPad = 16.dp.toPx()
        val yWidth = (yTicks.maxOfOrNull { measurer.measure(yText(it), axisStyle).size.width } ?: 0) + 8.dp.toPx()
        val left = yWidth
        val plotW = (size.width - left - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val ySpan = (yMax - yMin).takeIf { it > 0 } ?: 1.0
        fun py(v: Double) = topPad + ((yMax - v) / ySpan * plotH).toFloat().coerceIn(0f, plotH)
        fun px(v: Double) = if (hi > lo) left + ((v - lo) / (hi - lo) * plotW).toFloat() else left + plotW / 2
        // axis names, written once in ink: the value scale top-left, the time axis centred at the bottom
        yTitle?.let { val layout = measurer.measure(it, titleStyle); drawText(layout, topLeft = Offset(0f, 0f)) }
        xTitle?.let { val layout = measurer.measure(it, titleStyle); drawText(layout, topLeft = Offset(left + (plotW - layout.size.width) / 2f, size.height - layout.size.height)) }
        // soft bands, then gridlines with y labels
        bands.forEach { b ->
            val top = py(b.to.coerceAtMost(yMax)); val bottom = py(b.from.coerceAtLeast(yMin))
            if (bottom > top) drawRect(b.color.copy(alpha = b.alpha), Offset(left, top), Size(plotW, bottom - top))
        }
        // The lowest tick is the axis (solid, a little darker); the others are light dashed gridlines.
        val baseTick = yTicks.minOrNull()
        yTicks.forEach { v ->
            val y = py(v)
            if (v == baseTick) drawLine(DoctorCardSub.copy(alpha = 0.35f), Offset(left, y), Offset(left + plotW, y), 1.dp.toPx())
            else drawLine(DoctorHairline, Offset(left, y), Offset(left + plotW, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            val layout = measurer.measure(yText(v), axisStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }
        // Treatment days: a dashed line and a small label on a soft pill; a label that would overlap the previous one is left out (the line stays).
        val markerSpans = mutableListOf<ClosedFloatingPointRange<Float>>()
        shownMarkers.sortedBy { it.x }.forEach { m ->
            val x = px(m.x)
            drawLine(m.color.copy(alpha = 0.8f), Offset(x, topPad), Offset(x, topPad + plotH), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
            if (m.label.isNotBlank()) {
                val layout = measurer.measure(m.label, markerStyle.copy(color = m.color))
                val padX = 4.dp.toPx(); val w = layout.size.width + padX * 2; val h = layout.size.height.toFloat()
                val lx = (x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                val room = (lx - 2.dp.toPx())..(lx + w + 2.dp.toPx())
                if (markerSpans.none { it.start < room.endInclusive && room.start < it.endInclusive }) {
                    markerSpans += room
                    val ly = (topPad - h - 4.dp.toPx()).coerceAtLeast(0f)
                    drawRoundRect(Paper, Offset(lx, ly), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f))
                    drawRoundRect(m.color.copy(alpha = 0.45f), Offset(lx, ly), Size(w, h), androidx.compose.ui.geometry.CornerRadius(h / 2f), style = Stroke(1.dp.toPx()))
                    drawText(layout, topLeft = Offset(lx + padX, ly))
                }
            }
        }
        // x labels, skipping any that would collide
        val used = mutableListOf<ClosedFloatingPointRange<Float>>()
        shownLabels.forEach { l ->
            val layout = measurer.measure(l.text, if (l.strong) xStyle.copy(color = DoctorCardInk, fontWeight = FontWeight.SemiBold) else xStyle)
            val w = layout.size.width.toFloat()
            val x = (px(l.x) - w / 2).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
            val span = (x - 4.dp.toPx())..(x + w + 4.dp.toPx())
            if (used.none { it.start < span.endInclusive && span.start < it.endInclusive }) { used += span; drawText(layout, topLeft = Offset(x, topPad + plotH + 6.dp.toPx())) }
        }
        // line, drawn in from the left
        val shown = progress.value
        clipRect(left = 0f, top = 0f, right = left + (plotW + rightPad) * shown, bottom = size.height) {
            val path = Path()
            var open = false
            shownPoints.forEach { p ->
                val y = p.y
                if (y == null) { open = false } else {
                    val o = Offset(px(p.x), py(y))
                    if (open) path.lineTo(o.x, o.y) else { path.moveTo(o.x, o.y); open = true }
                }
            }
            drawPath(path, lineColor, style = Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            shownPoints.forEach { p -> p.y?.let { drawCircle(Color.White, 6.dp.toPx(), Offset(px(p.x), py(it))); drawCircle(p.color ?: lineColor, 4.5.dp.toPx(), Offset(px(p.x), py(it))) } }
        }
        val latest = shownPoints.lastOrNull { it.y != null }
        // every other point's value, small and above its dot
        if (pointText != null && labelEach && shown >= 1f) shownPoints.forEach { p -> val y = p.y; if (y != null && p !== latest) {
            val layout = measurer.measure(pointText(y), TextStyle(color = DoctorCardSub, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            val c = Offset(px(p.x), py(y))
            val ty = if (c.y - 8.dp.toPx() - layout.size.height >= 0f) c.y - 8.dp.toPx() - layout.size.height else c.y + 8.dp.toPx()
            drawText(layout, topLeft = Offset((c.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)), ty))
        } }
        // latest point: ring plus value label
        if (latest != null && shown >= 1f) {
            val c = Offset(px(latest.x), py(latest.y!!))
            val dot = latest.color ?: lineColor
            drawCircle(Color.White, 9.dp.toPx(), c)
            drawCircle(dot, 9.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            drawCircle(dot, 5.5.dp.toPx(), c)
            val layout = measurer.measure(latestText, TextStyle(color = DoctorCardInk, fontSize = 13.sp, fontWeight = FontWeight.Bold))
            val above = c.y - 12.dp.toPx() - layout.size.height >= 0f
            val ty = if (above) c.y - 12.dp.toPx() - layout.size.height else c.y + 12.dp.toPx()
            drawText(layout, topLeft = Offset((c.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)), ty))
        }
    }
}

/** "1 week · 1 month · 3 months · All" chips; only the ranges that change what is shown are offered, and nothing is drawn when only "All" would be. */
@Composable fun ChartRangeChips(choices: List<ChartRange>, selected: ChartRange, onSelect: (ChartRange) -> Unit, tag: String, modifier: Modifier = Modifier) {
    if (choices.size < 2) return
    val hindi = careHindi()
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("$tag-range"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { r ->
            FilterChip(selected = r == selected, onClick = { onSelect(r) }, label = { Text(r.label(hindi), fontSize = 14.sp, fontWeight = if (r == selected) FontWeight.SemiBold else FontWeight.Medium) },
                shape = CircleShape, border = FilterChipDefaults.filterChipBorder(enabled = true, selected = r == selected, borderColor = DoctorCardSub.copy(alpha = .45f), selectedBorderColor = Rust, borderWidth = 1.5.dp, selectedBorderWidth = 1.5.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = Paper, labelColor = DoctorCardInk, selectedContainerColor = Rust, selectedLabelColor = Color.White),
                modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-range-${r.name.lowercase()}"))
        }
    }
}

/**
 * A time chart (x = epoch milliseconds) with range chips above it. Every point is joined by one line, newest on the right.
 * Labels under the axis are the points' own dates (newest bold; overlapping ones are dropped). Pass [range] and [onRangeChange]
 * to hold the choice outside (for example to describe only what is shown); otherwise the chart keeps it itself.
 */
@Composable fun CareTimeLineChart(
    points: List<ChartPoint>,
    yMin: Double,
    yMax: Double,
    yTicks: List<Double>,
    yText: (Double) -> String,
    description: String,
    tag: String,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    lineColor: Color = Rust,
    bands: List<ChartBand> = emptyList(),
    markers: List<ChartMarker> = emptyList(),
    pointText: ((Double) -> String)? = null,
    latestText: (Double) -> String = { ChartRangeRules.number(it) },
    xLabelPattern: String = "d MMM",
    range: ChartRange? = null,
    onRangeChange: (ChartRange) -> Unit = {},
    now: Long = System.currentTimeMillis(),
    yTitle: String? = null,
    xTitle: String? = null,
) {
    val hindi = careHindi()
    var own by rememberSaveable(tag) { mutableStateOf(ChartRange.ALL.name) }
    val choices = remember(points, now / 3_600_000L) { ChartRangeRules.choices(points, now) }
    val wanted = range ?: ChartRange.valueOf(own)
    val current = if (wanted in choices) wanted else ChartRange.ALL
    val shown = remember(points, current, now / 3_600_000L) { ChartRangeRules.filter(points, current, now) }
    val format = careDateFormat(xLabelPattern, hindi)
    val labels = remember(shown, xLabelPattern, hindi) {
        val dated = shown.filter { it.y != null }.asReversed()
        dated.mapIndexed { i, p -> ChartXLabel(p.x, careDay(p.x.toLong()).format(format), strong = i == 0) }
    }
    val single = shown.count { it.y != null } == 1 && current == ChartRange.ALL
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChartRangeChips(choices, current, { own = it.name; onRangeChange(it) }, tag)
        CareLineChart(
            points = points, yMin = yMin, yMax = yMax, yTicks = yTicks, yText = yText, xLabels = labels,
            description = if (current == ChartRange.ALL) description else "${current.label(hindi)}. $description",
            latestText = shown.lastOrNull { it.y != null }?.y?.let(latestText).orEmpty(), tag = tag, height = height, lineColor = lineColor,
            bands = bands, markers = markers, pointText = pointText, range = current, now = now, yTitle = yTitle, xTitle = xTitle,
            xMin = if (single) shown.first { it.y != null }.x - 43_200_000.0 else null, xMax = if (single) shown.first { it.y != null }.x + 43_200_000.0 else null,
        )
    }
}

/** A small "i" button that opens a short explanation; used instead of help text under charts and forms. */
@Composable fun CareChartInfoButton(title: String, text: String, tag: String, modifier: Modifier = Modifier) {
    val t = careText()
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier.size(48.dp).testTag("$tag-info")) { Icon(Icons.Outlined.Info, title, tint = DoctorAccent) }
    if (open) AlertDialog(onDismissRequest = { open = false }, containerColor = Paper, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { open = false }) { Text(t("Got it", "ठीक है"), color = RustText) } })
}
