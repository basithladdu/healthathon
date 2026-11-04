package `in`.wedevit.care

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * The goals-of-care signature pad: finger, stylus or thumb on this phone. Exports a small 1-bit black and white PNG
 * (at most 600 px wide), the stroke count and the time. Twin of GocSignaturePad in prototype/app/goc-signature-pad.tsx.
 * The record it feeds, and the JSON field names shared with the web, are documented at the top of CareGocSignOff.kt.
 */

/** One finished drawing. [png] is what gets hashed; [capturedAt] is ISO-8601 UTC with milliseconds, like the web. */
internal class GocPadResult(val png: ByteArray, val strokeCount: Int, val capturedAt: String, val method: String, val width: Int, val height: Int)

internal object GocSignatureInk {
    /** Ink width as a share of the pad height. Same numbers as the web. */
    const val DRAWN = 0.02f
    const val THUMB = 0.22f
    const val MAX_WIDTH = 600
    const val ASPECT = 3f
    /** A drawn signature needs this much ink (in pad widths) so a stray tap is not a signature. A thumb press is enough as it is. */
    const val MIN_DRAWN_LENGTH = 0.2

    fun length(strokes: List<List<Pair<Float, Float>>>): Double = strokes.sumOf { s ->
        s.zipWithNext { a, b -> Math.hypot((b.first - a.first).toDouble(), ((b.second - a.second) / ASPECT).toDouble()) }.sum()
    }

    fun ready(strokes: List<List<Pair<Float, Float>>>, thumb: Boolean): Boolean = strokes.isNotEmpty() && (thumb || length(strokes) >= MIN_DRAWN_LENGTH)
}

/** A 1-bit greyscale PNG (colour type 0, bit depth 1). Pure JVM, the same bytes layout as gocEncodeMonoPng on the web. */
internal object GocMonoPng {
    /** Packed rows, each starting with filter byte 0. A set bit is white, a clear bit black. */
    fun rows(width: Int, height: Int, black: BooleanArray): ByteArray {
        val stride = (width + 7) / 8 + 1
        val out = ByteArray(stride * height)
        for (y in 0 until height) for (x in 0 until width) if (!black[y * width + x]) {
            val i = y * stride + 1 + (x shr 3)
            out[i] = (out[i].toInt() or (0x80 ushr (x and 7))).toByte()
        }
        return out
    }

    fun encode(width: Int, height: Int, black: BooleanArray): ByteArray {
        require(width > 0 && height > 0 && black.size >= width * height) { "Bad image size." }
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteArrayOutputStream().also { DataOutputStream(it).apply { writeInt(width); writeInt(height); write(byteArrayOf(1, 0, 0, 0, 0)) } }.toByteArray()
        chunk(out, "IHDR", header)
        chunk(out, "IDAT", zlib(rows(width, height, black)))
        chunk(out, "IEND", ByteArray(0))
        return bytes.toByteArray()
    }

    private fun zlib(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(data); deflater.finish()
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
            return out.toByteArray()
        } finally { deflater.end() }
    }

    private fun chunk(out: DataOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.writeInt(data.size); out.write(typeBytes); out.write(data)
        out.writeInt(CRC32().apply { update(typeBytes); update(data) }.value.toInt())
    }
}

/** Renders the strokes (0..1 pad coordinates) black on white at [width] x [height], keeps only black and white, and encodes the PNG. */
internal fun gocRenderSignaturePng(strokes: List<List<Pair<Float, Float>>>, thumb: Boolean, width: Int = GocSignatureInk.MAX_WIDTH, height: Int = (GocSignatureInk.MAX_WIDTH / GocSignatureInk.ASPECT).toInt()): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = android.graphics.Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE)
        val ink = maxOf(1.5f, height * if (thumb) GocSignatureInk.THUMB else GocSignatureInk.DRAWN)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE; strokeWidth = ink; isAntiAlias = false
            strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val dot = android.graphics.Paint(paint).apply { style = android.graphics.Paint.Style.FILL }
        strokes.forEach { stroke ->
            if (stroke.isEmpty()) return@forEach
            if (stroke.size == 1) { canvas.drawCircle(stroke[0].first * width, stroke[0].second * height, ink / 2, dot); return@forEach }
            val path = android.graphics.Path()
            stroke.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * width, y * height) else path.lineTo(x * width, y * height) }
            canvas.drawPath(path, paint)
        }
        val pixels = IntArray(width * height); bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val black = BooleanArray(pixels.size) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xFF) * 0.299 + (p shr 8 and 0xFF) * 0.587 + (p and 0xFF) * 0.114) < 160
        }
        return GocMonoPng.encode(width, height, black)
    } finally { bitmap.recycle() }
}

/**
 * Clear and Done buttons and a "Thumb impression" switch. [onDone] gets the PNG; return true to clear the pad,
 * false to keep the drawing (say a name is still missing).
 */
@Composable internal fun GocSignaturePad(onDone: (GocPadResult) -> Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, allowThumb: Boolean = true) { val t = careText()
    var strokes by remember { mutableStateOf(listOf<List<Pair<Float, Float>>>()) }
    var thumb by remember { mutableStateOf(false) }
    val ready = GocSignatureInk.ready(strokes, thumb)
    Column(modifier.fillMaxWidth().testTag("goc-signature-pad"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (thumb) t("Thumb impression here", "यहाँ अंगूठा लगाएँ") else t("Sign here", "यहाँ हस्ताक्षर करें"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (allowThumb) {
                Text(t("Thumb impression", "अंगूठे का निशान"), color = DoctorCardSub)
                Spacer(Modifier.width(8.dp))
                Switch(checked = thumb, onCheckedChange = { thumb = it; strokes = emptyList() }, enabled = enabled, modifier = Modifier.testTag("goc-signature-thumb"))
            }
        }
        val ink = DoctorCardInk
        val padLabel = if (thumb) t("Thumb impression pad", "अंगूठे के निशान की जगह") else t("Signature pad", "हस्ताक्षर की जगह")
        Canvas(Modifier.fillMaxWidth().aspectRatio(GocSignatureInk.ASPECT).clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(2.dp, if (thumb) DoctorAccent else DoctorHairline, RoundedCornerShape(14.dp))
            .semantics { contentDescription = padLabel }.testTag("goc-signature-canvas")
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                fun norm(o: Offset) = (o.x / size.width).coerceIn(0f, 1f) to (o.y / size.height).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    strokes = strokes + listOf(listOf(norm(down.position)))
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        strokes = strokes.dropLast(1) + listOf(strokes.lastOrNull().orEmpty() + norm(change.position))
                    }
                }
            }) {
            val width = size.height * if (thumb) GocSignatureInk.THUMB else GocSignatureInk.DRAWN
            strokes.forEach { stroke ->
                if (stroke.isEmpty()) return@forEach
                if (stroke.size == 1) { drawCircle(ink, width / 2, Offset(stroke[0].first * size.width, stroke[0].second * size.height)); return@forEach }
                val path = Path().apply { stroke.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height) } }
                drawPath(path, ink, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { strokes = emptyList() }, enabled = enabled && strokes.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-signature-clear")) { Text(t("Clear", "मिटाएँ"), color = DoctorAccent) }
            Button(onClick = {
                val png = gocRenderSignaturePng(strokes, thumb)
                val result = GocPadResult(png, strokes.size, GocSignOffRules.isoNow(), if (thumb) GocSignOffRules.THUMB else GocSignOffRules.DRAWN, GocSignatureInk.MAX_WIDTH, (GocSignatureInk.MAX_WIDTH / GocSignatureInk.ASPECT).toInt())
                if (onDone(result)) strokes = emptyList()
            }, enabled = enabled && ready, colors = ButtonDefaults.buttonColors(containerColor = DoctorAccent), modifier = Modifier.heightIn(min = 48.dp).testTag("goc-signature-done")) { Text(t("Done", "हो गया")) }
        }
    }
}
