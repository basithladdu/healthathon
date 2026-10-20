package `in`.wedevit.care

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Bundled OCR: neither file bytes nor recognised text are sent to a server. */
class CareDocumentReader(private val context: Context) {
    suspend fun read(file: File, report: CareReport, progress: (Int, Int) -> Unit = { _, _ -> }): ReviewedDocumentText = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() in 1..ReportFiles.MAX_BYTES) { "The original report is missing or too large. Add it again from Files." }
        val hash = file.inputStream().use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            while (true) { coroutineContext.ensureActive(); val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        val recognizer = CareBilingualOcr()
        suspend fun recognize(bitmap: Bitmap): String = recognizer.read(bitmap).take(200_001)
        try {
            val pages = mutableListOf<DocumentPageText>()
            var total = 0
            var pageCount = 1
            if (report.mime == "application/pdf") {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { pdf ->
                        pageCount = pdf.pageCount
                        require(pageCount > 0) { "This PDF has no pages." }
                        val count = minOf(12, pageCount)
                        progress(0, count)
                        for (index in 0 until count) {
                            coroutineContext.ensureActive()
                            val bitmap = pdf.openPage(index).use { page ->
                                val scale = minOf(1600.0 / page.width.coerceAtLeast(1), 2000.0 / page.height.coerceAtLeast(1))
                                Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1), (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE); page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                            val text = recognize(bitmap); total += text.length
                            require(total <= 200_000) { "This report has too much text. Add fewer pages." }
                            pages += DocumentPageText(index + 1, text); progress(index + 1, count)
                        }
                    }
                }
            } else {
                pageCount = 1; progress(0, 1)
                val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    val scale = minOf(1.0, 1600.0 / info.size.width, 2000.0 / info.size.height)
                    decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                } else {
                    val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.path, it) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a readable photo." }
                    var sample = 1
                    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 2000) sample *= 2
                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().also { it.inSampleSize = sample }) ?: error("The photo could not be opened.")
                }
                pages += DocumentPageText(1, recognize(bitmap)); progress(1, 1)
            }
            ReviewedDocumentText(report.id, report.personId, hash, pages, pageCount).also(DocumentEvidenceRules::validate)
        } catch (error: SecurityException) { throw IllegalArgumentException("This PDF is locked. Unlock it or copy the words manually.", error) }
        finally { recognizer.close() }
    }

    /** SAF image path for prescription pages; the returned candidates retain page identity. */
    suspend fun prescription(uris: List<Uri>, progress: (Int, Int) -> Unit): List<DocumentPageText> = withContext(Dispatchers.IO) {
        require(uris.size in 1..5) { "Choose up to five prescription pages." }
        val recognizer = CareBilingualOcr()
        try { uris.mapIndexed { index, uri ->
            coroutineContext.ensureActive(); progress(index, uris.size)
            require(uri.scheme == "content") { "Choose a photo using Files or the camera." }
            val source = File.createTempFile("prescription-ocr-", ".input", context.cacheDir)
            val bitmap = try {
                context.contentResolver.openInputStream(uri)?.use { input -> source.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024); var total = 0L
                    while (true) { coroutineContext.ensureActive(); val count = input.read(buffer); if (count < 0) break; total += count; require(total <= 25L * 1024 * 1024) { "Choose a photo smaller than 25 MB." }; output.write(buffer, 0, count) }
                    require(total > 0) { "The photo is empty." }
                } } ?: error("The selected photo could not be opened.")
                if (android.os.Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                    require(info.size.width > 0 && info.size.height > 0 && info.size.width.toLong() * info.size.height <= 250_000_000L) { "The selected photo dimensions are not supported." }
                    val scale = minOf(1.0, 1600.0 / info.size.width, 2000.0 / info.size.height)
                    decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1)); decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                } else {
                    val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true; BitmapFactory.decodeFile(source.path, it) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 250_000_000L) { "The selected photo dimensions are not supported." }
                    var sample = 1; while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 2000) sample *= 2
                    val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().also { it.inSampleSize = sample }) ?: error("The photo could not be read.")
                    val orientation = runCatching { android.media.ExifInterface(source.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
                    val matrix = android.graphics.Matrix().apply { when (orientation) { 2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f); 5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f); 7 -> { setRotate(-90f); postScale(-1f, 1f) }; 8 -> setRotate(-90f) } }
                    if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
                }
            } finally { source.delete() }
            val text = recognizer.read(bitmap)
            require(text.length <= 40_000) { "This page has too much text. Use a smaller section." }
            progress(index + 1, uris.size); DocumentPageText(index + 1, text)
        } } finally { recognizer.close() }
    }
}

class CareDocumentReviewStore(context: Context, account: String) {
    private val root = File(context.filesDir, "document-reviews/${account.ifBlank { "guest" }}")
    init { require(account.isBlank() || runCatching { UUID.fromString(account) }.isSuccess) }
    private fun file(person: String, report: String): File {
        require(runCatching { UUID.fromString(person); UUID.fromString(report) }.isSuccess)
        return File(File(root, person), "$report.json")
    }
    @Synchronized fun load(report: CareReport): ReviewedDocumentText? = runCatching {
        val saved = file(report.personId, report.id); require(saved.length() in 1..1_000_000)
        val json = JSONObject(saved.readText())
        ReviewedDocumentText(json.getString("reportId"), json.getString("personId"), json.getString("sha256"), json.getJSONArray("pages").let { array ->
            (0 until array.length()).map { i -> array.getJSONObject(i).let { DocumentPageText(it.getInt("page"), it.getString("original"), it.getString("reviewed")) } }
        }, json.getInt("pageCount"), json.optLong("reviewedAt")).also { value ->
            DocumentEvidenceRules.validate(value); require(value.reportId == report.id && value.personId == report.personId)
        }
    }.getOrNull()
    fun loadVerified(report: CareReport, original: File): ReviewedDocumentText? = load(report)?.takeIf { value ->
        original.isFile && original.length() in 1..ReportFiles.MAX_BYTES && original.inputStream().use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(16 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it) } == value.fileSha256
        }
    }
    @Synchronized fun save(value: ReviewedDocumentText) {
        DocumentEvidenceRules.validate(value)
        val target = file(value.personId, value.reportId); check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs())
        val pages = JSONArray().apply { value.pages.forEach { put(JSONObject().put("page", it.page).put("original", it.originalText).put("reviewed", it.reviewedText)) } }
        val json = JSONObject().put("reportId", value.reportId).put("personId", value.personId).put("sha256", value.fileSha256).put("pages", pages).put("pageCount", value.pageCount).put("reviewedAt", value.reviewedAt)
        val atomic = android.util.AtomicFile(target); val output = atomic.startWrite()
        try { output.write(json.toString().toByteArray()); atomic.finishWrite(output) } catch (error: Exception) { atomic.failWrite(output); throw error }
    }
    @Synchronized fun remove(report: CareReport) { file(report.personId, report.id).delete() }
    @Synchronized fun removePerson(person: String) { require(runCatching { UUID.fromString(person) }.isSuccess); File(root, person).deleteRecursively() }
}
