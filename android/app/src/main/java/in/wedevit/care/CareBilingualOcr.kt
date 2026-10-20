package `in`.wedevit.care

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Both models are bundled. Images/text have no upload path. */
internal class CareBilingualOcr : AutoCloseable {
    private val english = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val hindi = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

    suspend fun read(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val first = try { english.process(image) } catch (error: Exception) {
            bitmap.recycle(); continuation.resumeWithException(error); return@suspendCancellableCoroutine
        }
        val second = try { hindi.process(image) } catch (error: Exception) {
            first.addOnCompleteListener { if (!bitmap.isRecycled) bitmap.recycle() }
            continuation.resumeWithException(error); return@suspendCancellableCoroutine
        }
        Tasks.whenAllComplete(first, second).addOnCompleteListener {
            try {
                if (continuation.isActive) {
                    if (!first.isSuccessful || !second.isSuccessful) {
                        continuation.resumeWithException(first.exception ?: second.exception ?: IllegalStateException("Hindi/English text could not be read. Try a clearer photo."))
                    } else {
                        val lines = listOf(first.result to false, second.result to true).flatMap { (text, devanagari) ->
                            text.textBlocks.flatMap { block -> block.lines.mapNotNull { line ->
                                line.boundingBox?.let { box -> CareOcrLine(line.text, box.left, box.top, box.right, box.bottom, devanagari) }
                            } }
                        }
                        continuation.resume(CareOcrLines.merge(lines))
                    }
                }
            } finally { if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    override fun close() { english.close(); hindi.close() }
}
