package `in`.wedevit.care

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

enum class MedicineMealTiming { NOT_STATED, BEFORE_FOOD, WITH_FOOD, AFTER_FOOD }

enum class MedicineMatchQuality { MATCHED, POSSIBLE, PRESCRIPTION_ONLY, MANUAL }

data class PreparedMedicineImage(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class PreparedMedicineImages(
    val prescriptionPages: List<PreparedMedicineImage>,
    val medicineImages: List<PreparedMedicineImage>,
) {
    val totalBytes: Long get() = (prescriptionPages + medicineImages).sumOf { it.bytes.size.toLong() }
}

data class OrganizedMedicine(
    val medicineName: String,
    val strength: String,
    val form: String,
    val quantity: String,
    val frequency: String,
    val suggestedTimes: List<String>,
    val mealTiming: MedicineMealTiming,
    val instructions: String,
    val durationDays: Int?,
    val prescriptionItemNumber: Int?,
    val medicineImageIndex: Int,
    val prescriptionPageIndexes: List<Int>,
    val confidence: Double?,
    val uncertainties: List<String>,
    val matchQuality: MedicineMatchQuality,
    val sourceQuote: String = "",
)

data class MedicineOrganization(
    val medicines: List<OrganizedMedicine>,
    val unmatchedMedicineImageIndexes: List<Int>,
    val warnings: List<String>,
)

data class MedicineDraft(
    val id: String,
    val medicine: OrganizedMedicine,
    val confirmed: Boolean = false,
)

data class MedicineOrganizerResult(
    val organization: MedicineOrganization,
    val drafts: List<MedicineDraft>,
)

enum class MedicineScanStage { PREPARING, ORGANIZING }

data class MedicineScanProgress(
    val stage: MedicineScanStage,
    val completed: Int,
    val total: Int,
    val fileName: String = "",
)

enum class MedicineOrganizerError {
    INVALID_IMAGES,
    AUTH_REQUIRED,
    TOO_LARGE,
    INVALID_RESPONSE,
    CONFIGURATION,
    RATE_LIMITED,
    TIMEOUT,
    UNAVAILABLE,
}

class MedicineOrganizerException(
    val code: MedicineOrganizerError,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Native client for Sahara's medicine organizer.
 *
 * The caller supplies a current Supabase user access token. No server capability or provider key is
 * stored in the application. Selected photos are decoded, orientation-corrected and converted to
 * bounded JPEGs before the request is built.
 */
class MedicineOrganizer(
    context: Context,
    private val endpoint: URL = URL(DEFAULT_ENDPOINT),
) {
    private val app = context.applicationContext

    suspend fun prepare(
        prescriptionUris: List<Uri>,
        medicineUris: List<Uri>,
        onProgress: (MedicineScanProgress) -> Unit = {},
    ): PreparedMedicineImages {
        validateImageCounts(prescriptionUris.size, medicineUris.size)
        val all = prescriptionUris.map { ImageSelection(it, true) } + medicineUris.map { ImageSelection(it, false) }
        val prescriptions = ArrayList<PreparedMedicineImage>(prescriptionUris.size)
        val medicines = ArrayList<PreparedMedicineImage>(medicineUris.size)
        var totalBytes = 0L

        all.forEachIndexed { index, selection ->
            currentCoroutineContext().ensureActive()
            val displayName = imageDisplayName(selection.uri, index)
            onProgress(MedicineScanProgress(MedicineScanStage.PREPARING, index, all.size, displayName))
            val prepared = withContext(Dispatchers.IO) { prepareOne(selection.uri, displayName) }
            totalBytes += prepared.bytes.size
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw MedicineOrganizerException(
                    MedicineOrganizerError.TOO_LARGE,
                    "The selected images are too large together. Use fewer or tighter photos.",
                )
            }
            if (selection.prescription) prescriptions += prepared else medicines += prepared
        }
        onProgress(MedicineScanProgress(MedicineScanStage.PREPARING, all.size, all.size))
        return PreparedMedicineImages(prescriptions, medicines)
    }

    suspend fun organize(
        prepared: PreparedMedicineImages,
        bearerToken: String,
        scanId: String,
    ): MedicineOrganizerResult {
        validateImageCounts(prepared.prescriptionPages.size, prepared.medicineImages.size)
        validatePreparedImages(prepared)
        if (scanId.isBlank()) {
            throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "This scan could not be identified. Start it again.")
        }
        val token = normalizedBearerToken(bearerToken)
        val body = requestBody(prepared)
        val response = post(body, token)
        val organization = try {
            sanitizeMedicineOrganization(response, prepared.prescriptionPages.size, prepared.medicineImages.size)
        } catch (error: CancellationException) {
            throw error
        } catch (error: MedicineOrganizerException) {
            throw error
        } catch (error: RuntimeException) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.INVALID_RESPONSE,
                "Sahara AI returned an unreadable medicine list. Try clearer photos.",
                error,
            )
        }
        if (organization.medicines.isEmpty()) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.INVALID_RESPONSE,
                "No medicines could be organized. Try clearer prescription photos.",
            )
        }
        return MedicineOrganizerResult(
            organization = organization,
            drafts = organization.medicines.mapIndexed { index, medicine ->
                MedicineDraft(stableMedicineDraftId(scanId, medicine, index), medicine)
            },
        )
    }

    suspend fun organize(
        prescriptionUris: List<Uri>,
        medicineUris: List<Uri>,
        bearerToken: String,
        scanId: String,
        onProgress: (MedicineScanProgress) -> Unit = {},
    ): MedicineOrganizerResult {
        val prepared = prepare(prescriptionUris, medicineUris, onProgress)
        onProgress(
            MedicineScanProgress(
                stage = MedicineScanStage.ORGANIZING,
                completed = prepared.prescriptionPages.size + prepared.medicineImages.size,
                total = prepared.prescriptionPages.size + prepared.medicineImages.size,
            ),
        )
        return organize(prepared, bearerToken, scanId)
    }

    private fun validatePreparedImages(prepared: PreparedMedicineImages) {
        val all = prepared.prescriptionPages + prepared.medicineImages
        if (all.any { it.bytes.isEmpty() || it.bytes.size > MAX_IMAGE_BYTES || it.mimeType != JPEG_MIME || it.name.length > 240 }) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.INVALID_IMAGES,
                "Use valid medicine photos smaller than 500 KB each.",
            )
        }
        if (prepared.totalBytes > MAX_TOTAL_BYTES) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.TOO_LARGE,
                "The selected images are too large together. Use fewer or tighter photos.",
            )
        }
    }

    private fun imageDisplayName(uri: Uri, index: Int): String {
        val fromProvider = runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull()
        return fromProvider?.trim()?.takeIf(String::isNotEmpty)?.take(180) ?: "medicine-photo-${index + 1}"
    }

    private suspend fun prepareOne(uri: Uri, displayName: String): PreparedMedicineImage {
        val directory = File(app.cacheDir, "medicine-organizer").apply { mkdirs() }
        val source = File.createTempFile("source-", ".image", directory)
        try {
            copyBounded(uri, source)
            val decoded = decodeOriented(source)
            val bytes = try {
                encodeBoundedJpeg(decoded)
            } finally {
                if (!decoded.isRecycled) decoded.recycle()
            }
            return PreparedMedicineImage(safeJpegName(displayName), JPEG_MIME, bytes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: MedicineOrganizerException) {
            throw error
        } catch (error: SecurityException) {
            throw invalidPhoto(displayName, error)
        } catch (error: FileNotFoundException) {
            throw invalidPhoto(displayName, error)
        } catch (error: IOException) {
            throw invalidPhoto(displayName, error)
        } catch (error: RuntimeException) {
            throw invalidPhoto(displayName, error)
        } finally {
            source.delete()
        }
    }

    private suspend fun copyBounded(uri: Uri, destination: File) {
        val input = app.contentResolver.openInputStream(uri)
            ?: throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "The selected photo could not be opened.")
        input.use { source ->
            destination.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    copied += count
                    if (copied > MAX_SOURCE_BYTES) {
                        throw MedicineOrganizerException(
                            MedicineOrganizerError.TOO_LARGE,
                            "Use JPG, PNG, WebP or HEIF photos smaller than 25 MB.",
                        )
                    }
                    output.write(buffer, 0, count)
                }
                if (copied == 0L) {
                    throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "The selected photo is empty.")
                }
            }
        }
    }

    private fun decodeOriented(file: File): Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeWithImageDecoder(file)
    } else {
        decodeLegacy(file)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(file: File): Bitmap {
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                validateDimensions(info.size.width, info.size.height)
                val (width, height) = boundedDimensions(info.size.width, info.size.height)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                decoder.setTargetSize(width, height)
                decoder.setOnPartialImageListener { false }
            }
        } catch (error: ImageDecoder.DecodeException) {
            throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "The selected photo format could not be read.", error)
        }
    }

    @Suppress("DEPRECATION")
    private fun decodeLegacy(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        validateDimensions(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (bounds.outWidth / sample > MAX_IMAGE_WIDTH * 2 || bounds.outHeight / sample > MAX_IMAGE_HEIGHT * 2) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "The selected photo format could not be read.")
        val oriented = applyExifOrientation(decoded, file)
        if (oriented !== decoded && !decoded.isRecycled) decoded.recycle()
        return oriented
    }

    private fun applyExifOrientation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun encodeBoundedJpeg(source: Bitmap): ByteArray {
        var working = scaleToBounds(source)
        if (working !== source && !source.isRecycled) source.recycle()
        try {
            repeat(MAX_SIZE_ATTEMPTS) { attempt ->
                currentCoroutineContext().ensureActive()
                val flattened = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
                try {
                    Canvas(flattened).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(working, 0f, 0f, null)
                    }
                    for (quality in JPEG_QUALITIES) {
                        val output = ByteArrayOutputStream(minOf(MAX_IMAGE_BYTES, 256 * 1024))
                        if (!flattened.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                            throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "The selected photo could not be converted.")
                        }
                        if (output.size() in 1..MAX_IMAGE_BYTES) return output.toByteArray()
                    }
                } finally {
                    flattened.recycle()
                }
                if (attempt < MAX_SIZE_ATTEMPTS - 1) {
                    val width = max(1, (working.width * RESIZE_STEP).roundToInt())
                    val height = max(1, (working.height * RESIZE_STEP).roundToInt())
                    val smaller = Bitmap.createScaledBitmap(working, width, height, true)
                    if (smaller !== working) working.recycle()
                    working = smaller
                }
            }
        } finally {
            if (!working.isRecycled) working.recycle()
        }
        throw MedicineOrganizerException(
            MedicineOrganizerError.TOO_LARGE,
            "A selected photo could not be reduced below 500 KB. Crop it and try again.",
        )
    }

    private fun requestBody(prepared: PreparedMedicineImages): ByteArray {
        fun list(images: List<PreparedMedicineImage>) = JSONArray().apply {
            images.forEach { image ->
                put(
                    JSONObject()
                        .put("name", image.name)
                        .put("mimeType", image.mimeType)
                        .put("data", Base64.encodeToString(image.bytes, Base64.NO_WRAP)),
                )
            }
        }
        val bytes = JSONObject()
            .put("prescriptionPages", list(prepared.prescriptionPages))
            .put("medicineImages", list(prepared.medicineImages))
            .toString()
            .toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_BODY_BYTES) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.TOO_LARGE,
                "The selected images are too large together. Use fewer or tighter photos.",
            )
        }
        return bytes
    }

    private suspend fun post(body: ByteArray, token: String): String = withContext(Dispatchers.IO) {
        if (!CareAndroidAssistPolicy.externalAiEnabled) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.CONFIGURATION,
                "Use on-device prescription reading. Online photo processing is off in this Android app.",
            )
        }
        val connection = try {
            endpoint.openConnection() as? HttpURLConnection
                ?: throw MedicineOrganizerException(MedicineOrganizerError.UNAVAILABLE, "The medicine organizer could not connect.")
        } catch (error: MedicineOrganizerException) {
            throw error
        } catch (error: IOException) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.UNAVAILABLE,
                "The medicine organizer could not connect. Check the connection and try again.",
                error,
            )
        }
        val cancellation = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) connection.disconnect()
        }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.outputStream.use { output -> output.write(body) }
            currentCoroutineContext().ensureActive()

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { readBounded(it, MAX_RESPONSE_BYTES) }.orEmpty()
            currentCoroutineContext().ensureActive()
            if (status !in 200..299) throw httpFailure(status, response)
            val type = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            if (!type.contains("application/json")) {
                throw MedicineOrganizerException(
                    MedicineOrganizerError.INVALID_RESPONSE,
                    "Sahara AI returned an unreadable medicine list. Try again.",
                )
            }
            response
        } catch (error: MedicineOrganizerException) {
            throw error
        } catch (error: SocketTimeoutException) {
            throw MedicineOrganizerException(
                MedicineOrganizerError.TIMEOUT,
                "The medicine organizer took too long. Try fewer photos.",
                error,
            )
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            throw MedicineOrganizerException(
                MedicineOrganizerError.UNAVAILABLE,
                "The medicine organizer could not connect. Check the connection and try again.",
                error,
            )
        } finally {
            cancellation?.dispose()
            connection.disconnect()
        }
    }

    private fun httpFailure(status: Int, body: String): MedicineOrganizerException {
        val payload = runCatching { JSONObject(body) }.getOrNull()
        val serverCode = payload?.strictString("code", 80).orEmpty()
        val serverMessage = payload?.strictString("error", 300).orEmpty()
        val code = when (status) {
            400, 413, 415, 422 -> if (status == 413) MedicineOrganizerError.TOO_LARGE else MedicineOrganizerError.INVALID_IMAGES
            401, 403 -> MedicineOrganizerError.AUTH_REQUIRED
            408, 504 -> MedicineOrganizerError.TIMEOUT
            429 -> MedicineOrganizerError.RATE_LIMITED
            503 -> if (serverCode == "not_configured") MedicineOrganizerError.CONFIGURATION else MedicineOrganizerError.UNAVAILABLE
            else -> MedicineOrganizerError.UNAVAILABLE
        }
        val fallback = when (code) {
            MedicineOrganizerError.INVALID_IMAGES -> "The organizer could not read those images. Try clearer photos."
            MedicineOrganizerError.AUTH_REQUIRED -> "Sign in again before organizing prescription photos."
            MedicineOrganizerError.TOO_LARGE -> "The selected images are too large together. Use fewer or tighter photos."
            MedicineOrganizerError.CONFIGURATION -> "Sahara AI is still being connected. Try again later."
            MedicineOrganizerError.RATE_LIMITED -> "Please wait before organizing another prescription."
            MedicineOrganizerError.TIMEOUT -> "The medicine organizer took too long. Try fewer photos."
            else -> "The medicine organizer is temporarily unavailable. Try again."
        }
        return MedicineOrganizerException(code, serverMessage.ifBlank { fallback })
    }

    private suspend fun readBounded(input: InputStream, maximum: Int): String {
        val output = ByteArrayOutputStream(minOf(maximum, 32 * 1024))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var size = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            if (size > maximum) {
                throw MedicineOrganizerException(
                    MedicineOrganizerError.INVALID_RESPONSE,
                    "Sahara AI returned too much data. Try again.",
                )
            }
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun invalidPhoto(name: String, cause: Throwable) = MedicineOrganizerException(
        MedicineOrganizerError.INVALID_IMAGES,
        "${name.take(120)} is not a supported or readable photo.",
        cause,
    )

    private fun validateDimensions(width: Int, height: Int) {
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_DIMENSION || height > MAX_SOURCE_DIMENSION ||
            width.toLong() * height.toLong() > MAX_SOURCE_PIXELS
        ) {
            throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "The selected photo dimensions are not supported.")
        }
    }

    private fun boundedDimensions(width: Int, height: Int): Pair<Int, Int> {
        val scale = minOf(1.0, MAX_IMAGE_WIDTH.toDouble() / width, MAX_IMAGE_HEIGHT.toDouble() / height)
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    private fun scaleToBounds(source: Bitmap): Bitmap {
        val (width, height) = boundedDimensions(source.width, source.height)
        return if (width == source.width && height == source.height) source
        else Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun safeJpegName(original: String): String {
        val stem = original.substringBeforeLast('.', original).trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
            .take(180)
            .ifBlank { "medicine-photo" }
        return "$stem.jpg"
    }

    private fun normalizedBearerToken(value: String): String {
        val token = value.trim().removePrefix("Bearer ").trim()
        if (token.length !in 16..16_384 || token.any { it == '\r' || it == '\n' }) {
            throw MedicineOrganizerException(MedicineOrganizerError.AUTH_REQUIRED, "Sign in again before organizing prescription photos.")
        }
        return token
    }

    private data class ImageSelection(val uri: Uri, val prescription: Boolean)

    companion object {
        const val DEFAULT_ENDPOINT = "https://sahara.wedevit.in/api/medicine-organizer"
        const val MAX_PRESCRIPTION_PAGES = 5
        const val MAX_MEDICINE_IMAGES = 10
        const val MAX_IMAGE_BYTES = 500 * 1024
        const val MAX_TOTAL_BYTES = 8L * 1024 * 1024
        const val MAX_BODY_BYTES = 12 * 1024 * 1024
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_SOURCE_BYTES = 25L * 1024 * 1024
        const val MAX_IMAGE_WIDTH = 1100
        const val MAX_IMAGE_HEIGHT = 1500
        private const val MAX_SOURCE_DIMENSION = 65_535
        private const val MAX_SOURCE_PIXELS = 250_000_000L
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 145_000
        private const val MAX_SIZE_ATTEMPTS = 5
        private const val RESIZE_STEP = 0.78
        private const val JPEG_MIME = "image/jpeg"
        private val JPEG_QUALITIES = intArrayOf(88, 78, 68, 58, 48, 38, 30)
    }
}

fun sanitizeMedicineOrganization(json: String, prescriptionCount: Int, medicineImageCount: Int): MedicineOrganization {
    if (prescriptionCount !in 1..MedicineOrganizer.MAX_PRESCRIPTION_PAGES ||
        medicineImageCount !in 1..MedicineOrganizer.MAX_MEDICINE_IMAGES
    ) {
        throw MedicineOrganizerException(MedicineOrganizerError.INVALID_IMAGES, "Add valid prescription and medicine photos first.")
    }
    val root = try {
        JSONObject(json)
    } catch (error: JSONException) {
        throw MedicineOrganizerException(
            MedicineOrganizerError.INVALID_RESPONSE,
            "The AI response was not valid. Try the photos again.",
            error,
        )
    }
    val rawMedicines = root.opt("medicines") as? JSONArray
        ?: throw MedicineOrganizerException(
            MedicineOrganizerError.INVALID_RESPONSE,
            "No medicines could be organized from these photos.",
        )
    val medicines = ArrayList<OrganizedMedicine>()
    val seen = LinkedHashSet<String>()
    val claimedImages = LinkedHashSet<Int>()

    for (index in 0 until rawMedicines.length()) {
        val raw = rawMedicines.opt(index) as? JSONObject ?: continue
        val name = raw.strictString("medicineName", 120)
        val strength = raw.strictString("strength", 80)
        if (name.isBlank()) continue
        val duplicateKey = "${name.lowercase(Locale.ROOT)}|${strength.lowercase(Locale.ROOT)}"
        if (!seen.add(duplicateKey)) continue

        val numericImageIndex = raw.finiteTruncatedInt("medicineImageIndex") ?: -1
        var medicineIndex = numericImageIndex.takeIf { it in 0 until medicineImageCount } ?: -1
        var confidence = raw.strictFiniteDouble("confidence")?.coerceIn(0.0, 1.0) ?: 0.0
        val suggestedTimes = raw.strictStringList("suggestedTimes", 6, 20)
            .mapNotNull(::normalizedMedicineTime).distinct()
        val pageIndexes = raw.boundedIndexes("prescriptionPageIndexes", prescriptionCount)
        var uncertainties = raw.strictStringList("uncertainties", 6, 180)
        when (raw.strictString("identityMatch", 40)) {
            "genericEquivalent" -> {
                confidence = minOf(confidence, 0.74)
                uncertainties = uncertainties.withUncertainty(
                    "The strip uses a different brand. Confirm the generic medicine and strength before saving.",
                )
            }
            "uncertain" -> confidence = minOf(confidence, 0.49)
            "none" -> medicineIndex = -1
        }
        if (medicineIndex >= 0 && medicineIndex in claimedImages) {
            uncertainties = uncertainties.withUncertainty(
                "Strip photo ${medicineIndex + 1} was also matched to another medicine. Review this entry manually.",
            )
            medicineIndex = -1
            confidence = minOf(confidence, 0.49)
        }
        if (numericImageIndex >= medicineImageCount) {
            uncertainties = uncertainties.withUncertainty(
                "The AI returned an invalid strip-photo match. Review this entry manually.",
            )
        }
        if (pageIndexes.isEmpty()) {
            uncertainties = uncertainties.withUncertainty(
                "No prescription page was linked to this entry. Check it against the prescription.",
            )
            medicineIndex = -1
            confidence = minOf(confidence, 0.49)
        }
        if (medicineIndex >= 0) claimedImages += medicineIndex

        val matchQuality = when {
            medicineIndex < 0 -> MedicineMatchQuality.PRESCRIPTION_ONLY
            confidence >= 0.75 -> MedicineMatchQuality.MATCHED
            else -> MedicineMatchQuality.POSSIBLE
        }
        medicines += OrganizedMedicine(
            medicineName = name,
            strength = strength,
            form = raw.strictString("form", 60),
            quantity = raw.strictString("quantity", 80),
            frequency = raw.strictString("frequency", 120),
            suggestedTimes = suggestedTimes,
            mealTiming = when (raw.strictString("mealTiming", 40)) {
                "beforeFood" -> MedicineMealTiming.BEFORE_FOOD
                "withFood" -> MedicineMealTiming.WITH_FOOD
                "afterFood" -> MedicineMealTiming.AFTER_FOOD
                else -> MedicineMealTiming.NOT_STATED
            },
            instructions = raw.strictString("instructions", 500),
            durationDays = raw.positiveInteger("durationDays", 365),
            prescriptionItemNumber = raw.positiveInteger("prescriptionItemNumber", 999),
            medicineImageIndex = medicineIndex,
            prescriptionPageIndexes = pageIndexes,
            confidence = confidence,
            uncertainties = uncertainties,
            matchQuality = matchQuality,
        )
    }

    val ordered = medicines.withIndex().sortedWith(
        compareBy<IndexedValue<OrganizedMedicine>> { it.value.prescriptionItemNumber ?: Int.MAX_VALUE }
            .thenBy { it.index },
    ).map { it.value }
    val used = ordered.mapNotNull { it.medicineImageIndex.takeIf { image -> image >= 0 } }.toSet()
    val unmatched = LinkedHashSet(root.boundedIndexes("unmatchedMedicineImageIndexes", medicineImageCount))
    for (index in 0 until medicineImageCount) if (index !in used) unmatched += index
    return MedicineOrganization(
        medicines = ordered,
        unmatchedMedicineImageIndexes = unmatched.sorted(),
        warnings = root.strictStringList("warnings", 8, 180),
    )
}

fun medicineCareDetail(medicine: OrganizedMedicine): String {
    val dose = listOf(medicine.strength, medicine.quantity, medicine.form).filter(String::isNotBlank).joinToString(" · ")
    val lines = buildList {
        if (dose.isNotBlank()) add("Dose: $dose")
        if (medicine.frequency.isNotBlank()) add("Frequency: ${medicine.frequency}")
        if (medicine.suggestedTimes.isNotEmpty()) add("Times: ${medicine.suggestedTimes.joinToString(", ")}")
        when (medicine.mealTiming) {
            MedicineMealTiming.BEFORE_FOOD -> add("Food: before food")
            MedicineMealTiming.WITH_FOOD -> add("Food: with food")
            MedicineMealTiming.AFTER_FOOD -> add("Food: after food")
            MedicineMealTiming.NOT_STATED -> Unit
        }
        medicine.durationDays?.let { add("Course: $it ${if (it == 1) "day" else "days"}") }
        if (medicine.instructions.isNotBlank()) add("Directions: ${medicine.instructions}")
        if (medicine.sourceQuote.isNotBlank()) add("Copied source · prescription ${medicine.prescriptionPageIndexes.joinToString { "page ${it + 1}" }}:\n${medicine.sourceQuote}")
        if (medicine.uncertainties.isNotEmpty()) add("Review: ${medicine.uncertainties.joinToString(" ")}")
    }
    return (lines.ifEmpty { listOf("Check dose and directions against the original prescription.") })
        .joinToString("\n").take(4000)
}

fun stableMedicineDraftId(scanId: String, medicine: OrganizedMedicine, index: Int): String {
    require(scanId.isNotBlank()) { "scanId must not be blank" }
    require(index >= 0) { "index must not be negative" }
    val identity = listOf(
        "sahara-medicine-draft-v1",
        scanId.trim(),
        index.toString(),
        medicine.prescriptionItemNumber?.toString().orEmpty(),
        medicine.medicineName.trim().lowercase(Locale.ROOT),
        medicine.strength.trim().lowercase(Locale.ROOT),
    ).joinToString("\u001f")
    return UUID.nameUUIDFromBytes(identity.toByteArray(StandardCharsets.UTF_8)).toString()
}

private fun validateImageCounts(prescriptionCount: Int, medicineCount: Int) {
    if (prescriptionCount !in 1..MedicineOrganizer.MAX_PRESCRIPTION_PAGES ||
        medicineCount !in 1..MedicineOrganizer.MAX_MEDICINE_IMAGES
    ) {
        throw MedicineOrganizerException(
            MedicineOrganizerError.INVALID_IMAGES,
            "Add at least one prescription page and one medicine photo. Use up to 5 prescription pages and 10 medicine photos.",
        )
    }
}

private fun JSONObject.strictString(key: String, maximum: Int): String {
    val value = opt(key)
    return if (value is String) value.trim().take(maximum) else ""
}

private fun JSONObject.strictFiniteDouble(key: String): Double? {
    val value = opt(key) as? Number ?: return null
    return value.toDouble().takeIf(Double::isFinite)
}

private fun JSONObject.finiteTruncatedInt(key: String): Int? {
    val value = strictFiniteDouble(key) ?: return null
    if (value < Int.MIN_VALUE.toDouble() || value > Int.MAX_VALUE.toDouble()) return null
    return value.toInt()
}

private fun JSONObject.positiveInteger(key: String, maximum: Int): Int? {
    val value = finiteTruncatedInt(key) ?: return null
    return value.takeIf { it in 1..maximum }
}

private fun JSONObject.strictStringList(key: String, maximumItems: Int, maximumLength: Int): List<String> {
    val array = opt(key) as? JSONArray ?: return emptyList()
    val result = LinkedHashSet<String>()
    for (index in 0 until array.length()) {
        val value = array.opt(index)
        if (value is String) {
            val text = value.trim().take(maximumLength)
            if (text.isNotBlank()) result += text
        }
        if (result.size >= maximumItems) break
    }
    return result.toList()
}

private fun JSONObject.boundedIndexes(key: String, count: Int): List<Int> {
    val array = opt(key) as? JSONArray ?: return emptyList()
    val result = LinkedHashSet<Int>()
    for (index in 0 until array.length()) {
        val value = array.opt(index) as? Number ?: continue
        val number = value.toDouble()
        if (!number.isFinite() || number < Int.MIN_VALUE || number > Int.MAX_VALUE) continue
        val integer = number.toInt()
        if (integer in 0 until count) result += integer
    }
    return result.toList()
}

private fun List<String>.withUncertainty(message: String): List<String> =
    if (message in this) this else (this + message).take(6)

private fun normalizedMedicineTime(value: String): String? {
    val text = value.trim()
    val twelveHour = TWELVE_HOUR_TIME.matchEntire(text)
    if (twelveHour != null) {
        var hour = twelveHour.groupValues[1].toInt() % 12
        if (twelveHour.groupValues[3].uppercase(Locale.ROOT) == "PM") hour += 12
        val minute = twelveHour.groupValues[2].ifBlank { "00" }
        return "%02d:%s".format(Locale.ROOT, hour, minute)
    }
    val twentyFourHour = TWENTY_FOUR_HOUR_TIME.matchEntire(text) ?: return null
    return "%02d:%s".format(Locale.ROOT, twentyFourHour.groupValues[1].toInt(), twentyFourHour.groupValues[2])
}

private val TWELVE_HOUR_TIME = Regex("^(1[0-2]|0?[1-9])(?::([0-5]\\d))?\\s*(AM|PM)$", RegexOption.IGNORE_CASE)
private val TWENTY_FOUR_HOUR_TIME = Regex("^(2[0-3]|[01]?\\d):([0-5]\\d)$")
