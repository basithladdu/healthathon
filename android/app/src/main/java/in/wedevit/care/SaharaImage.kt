package `in`.wedevit.care

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/**
 * The bundled illustrations are 1254 px (or density-scaled) PNGs shown at 30–260 dp.
 * painterResource decodes them at full size every time a screen opens. This decodes once at the
 * smallest power-of-two size that still covers [largest] on this screen, and keeps it for reuse.
 */
@Composable fun saharaImage(@DrawableRes id: Int, largest: Dp): Painter {
    val resources = LocalContext.current.resources
    val pixels = with(LocalDensity.current) { largest.roundToPx() }.coerceAtLeast(1)
    val bitmap = remember(id, pixels) { SaharaImageCache.load(resources, id, pixels) }
    // A vector drawable (such as symptom_tracker_illustration.xml) is not a bitmap, so it is drawn as before.
    return if (bitmap == null) painterResource(id) else remember(bitmap) { BitmapPainter(bitmap) }
}

private object SaharaImageCache {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun load(resources: Resources, id: Int, pixels: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeResource(resources, id, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        // Never go below the drawn size, so the picture looks exactly as before.
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= pixels) sample *= 2
        val key = "$id/$sample"
        cache.get(key)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
        return BitmapFactory.decodeResource(resources, id, options)?.asImageBitmap()?.also { cache.put(key, it) }
    }
}
