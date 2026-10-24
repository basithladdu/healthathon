package `in`.wedevit.care

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/** Decorations sit behind the original, unchanged illustration, never over faces or hands. */
@Composable
internal fun WelcomeHero(modifier: Modifier = Modifier) {
    Box(
        modifier.widthIn(max = 320.dp).fillMaxWidth().height(215.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            val unit = size.height / 215f
            val imageWidth = minOf(size.width, size.height)
            val left = (size.width - imageWidth) / 2f
            val orange = Color(0xFFD97757)

            // Asymmetric, quiet colour fields connect the two people without competing with them.
            drawOval(Color(0xFFFFE0CB), Offset(left - 16f * unit, 44f * unit), Size(146f * unit, 133f * unit))
            drawOval(Color(0xFFFFE9AD), Offset(left + 87f * unit, 45f * unit), Size(139f * unit, 131f * unit))
            drawOval(Color(0xFFECE2F2), Offset(left + 13f * unit, 135f * unit), Size(186f * unit, 60f * unit))
            drawCircle(orange.copy(alpha = .45f), 3f * unit, Offset(18f * unit, 102f * unit))
            drawCircle(Color(0xFFE8C26F), 4f * unit, Offset(size.width - 18f * unit, 148f * unit))

            val cloudWidth = minOf(57f * unit, left + 9f * unit).coerceAtLeast(30f * unit)
            thoughtCloud(6f * unit, 2f * unit, cloudWidth, unit, orange, doctor = true)
            thoughtCloud(size.width - cloudWidth - 6f * unit, 3f * unit, cloudWidth, unit, orange, doctor = false)
        }
        Image(
            painterResource(R.drawable.sahara_welcome_conversation),
            "A doctor and patient talking about care",
            Modifier.widthIn(max = 260.dp).fillMaxWidth().height(215.dp).testTag("welcome-hero-illustration"),
            alignment = Alignment.Center,
            contentScale = ContentScale.Fit,
        )
    }
}

private fun DrawScope.thoughtCloud(x: Float, y: Float, width: Float, unit: Float, ink: Color, doctor: Boolean) {
    val height = 34f * unit
    val fill = Color(0xFFFFFDFA)
    val outline = ink.copy(alpha = .34f)
    val cloud = Path().apply {
        moveTo(x + width * .15f, y + height * .92f)
        cubicTo(x - width * .04f, y + height * .84f, x - width * .02f, y + height * .34f, x + width * .17f, y + height * .32f)
        cubicTo(x + width * .15f, y - height * .02f, x + width * .53f, y - height * .1f, x + width * .62f, y + height * .17f)
        cubicTo(x + width * .85f, y - height * .02f, x + width * 1.02f, y + height * .29f, x + width * .94f, y + height * .45f)
        cubicTo(x + width * 1.13f, y + height * .72f, x + width * .91f, y + height * 1.04f, x + width * .72f, y + height * .91f)
        cubicTo(x + width * .54f, y + height * 1.08f, x + width * .33f, y + height * 1.04f, x + width * .15f, y + height * .92f)
        close()
    }
    drawPath(cloud, fill)
    drawPath(cloud, outline, style = Stroke(1f * unit))
    val tailX = if (doctor) x + width * .86f else x + width * .14f
    drawCircle(fill, 3.5f * unit, Offset(tailX, y + height + 5f * unit))
    drawCircle(outline, 3.5f * unit, Offset(tailX, y + height + 5f * unit), style = Stroke(unit))
    drawCircle(ink.copy(alpha = .35f), 1.8f * unit, Offset(tailX + (if (doctor) 5f else -5f) * unit, y + height + 13f * unit))
    val cx = x + width * .5f
    val cy = y + height * .54f
    if (doctor) {
        drawRoundRect(ink, Offset(cx - 8f * unit, cy - 9f * unit), Size(16f * unit, 19f * unit), CornerRadius(3f * unit), style = Stroke(1.5f * unit))
        drawRoundRect(ink, Offset(cx - 4f * unit, cy - 11f * unit), Size(8f * unit, 4f * unit), CornerRadius(2f * unit))
        for (row in 0..1) {
            val lineY = cy + (-2f + row * 6f) * unit
            drawLine(ink, Offset(cx - 4.5f * unit, lineY), Offset(cx - 3f * unit, lineY + 1.5f * unit), 1.4f * unit)
            drawLine(ink, Offset(cx - 3f * unit, lineY + 1.5f * unit), Offset(cx - .5f * unit, lineY - 1f * unit), 1.4f * unit)
            drawLine(ink.copy(alpha = .65f), Offset(cx + 2f * unit, lineY), Offset(cx + 5f * unit, lineY), 1.3f * unit)
        }
    } else {
        val heart = Path().apply {
            moveTo(cx, cy + 8f * unit)
            cubicTo(cx - 17f * unit, cy - 2f * unit, cx - 8f * unit, cy - 15f * unit, cx, cy - 6f * unit)
            cubicTo(cx + 8f * unit, cy - 15f * unit, cx + 17f * unit, cy - 2f * unit, cx, cy + 8f * unit)
            close()
        }
        drawPath(heart, ink)
    }
}
