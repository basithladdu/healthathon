package `in`.wedevit.care

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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

/** A dashed vertical marker (for example a treatment day). */
data class ChartMarker(val x: Double, val color: Color)

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
) {
    val measurer = rememberTextMeasurer()
    val progress = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { progress.snapTo(0f); progress.animateTo(1f, SaharaMotion.tween(700)) }
    val axisStyle = TextStyle(color = DoctorCardSub, fontSize = 11.sp, lineHeight = 13.sp)
    val xStyle = axisStyle.copy(textAlign = TextAlign.Center)
    val lo = xMin ?: points.minOfOrNull { it.x } ?: 0.0
    val hi = xMax ?: points.maxOfOrNull { it.x } ?: 1.0
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { contentDescription = description }.testTag("$tag-chart")) {
        val topPad = 14.dp.toPx()
        val bottomPad = (8 + 14 * xLabelLines).dp.toPx()
        val rightPad = 16.dp.toPx()
        val yWidth = (yTicks.maxOfOrNull { measurer.measure(yText(it), axisStyle).size.width } ?: 0) + 8.dp.toPx()
        val left = yWidth
        val plotW = (size.width - left - rightPad).coerceAtLeast(1f)
        val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val ySpan = (yMax - yMin).takeIf { it > 0 } ?: 1.0
        fun py(v: Double) = topPad + ((yMax - v) / ySpan * plotH).toFloat().coerceIn(0f, plotH)
        fun px(v: Double) = if (hi > lo) left + ((v - lo) / (hi - lo) * plotW).toFloat() else left + plotW / 2
        // soft bands, then gridlines with y labels
        bands.forEach { b ->
            val top = py(b.to.coerceAtMost(yMax)); val bottom = py(b.from.coerceAtLeast(yMin))
            if (bottom > top) drawRect(b.color.copy(alpha = b.alpha), Offset(left, top), Size(plotW, bottom - top))
        }
        yTicks.forEach { v ->
            val y = py(v)
            drawLine(DoctorHairline, Offset(left, y), Offset(left + plotW, y), 1.dp.toPx())
            val layout = measurer.measure(yText(v), axisStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }
        markers.forEach { m ->
            val x = px(m.x)
            drawLine(m.color, Offset(x, topPad), Offset(x, topPad + plotH), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        // x labels, skipping any that would collide
        val used = mutableListOf<ClosedFloatingPointRange<Float>>()
        xLabels.forEach { l ->
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
            points.forEach { p ->
                val y = p.y
                if (y == null) { open = false } else {
                    val o = Offset(px(p.x), py(y))
                    if (open) path.lineTo(o.x, o.y) else { path.moveTo(o.x, o.y); open = true }
                }
            }
            drawPath(path, lineColor, style = Stroke(2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            points.forEach { p -> p.y?.let { drawCircle(p.color ?: lineColor, 4.5.dp.toPx(), Offset(px(p.x), py(it))) } }
        }
        val latest = points.lastOrNull { it.y != null }
        // every other point's value, small and above its dot
        if (pointText != null && shown >= 1f) points.forEach { p -> val y = p.y; if (y != null && p !== latest) {
            val layout = measurer.measure(pointText(y), TextStyle(color = DoctorCardSub, fontSize = 11.sp, fontWeight = FontWeight.Medium))
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
