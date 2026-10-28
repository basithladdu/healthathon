package `in`.wedevit.care

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The signature being made in the note reader: drawn strokes (0..1 pad coordinates) or a photo of a paper signature. */
internal class SignatureDraft {
    var strokes by mutableStateOf(listOf<List<Pair<Float, Float>>>())
    var photo by mutableStateOf<ByteArray?>(null)
    val ready: Boolean get() = photo != null || SignatureInk.enough(strokes)
    fun clear() { strokes = emptyList(); photo = null }
    /** (kind, image bytes) to sign with, or null when nothing is ready. */
    fun image(): Pair<String, ByteArray>? = photo?.let { NoteSignatureRules.PHOTO to it } ?: if (SignatureInk.enough(strokes)) NoteSignatureRules.DRAWN to renderSignaturePng(strokes) else null
}

private const val PAD_W = 900
private const val PAD_H = 300

/** Black ink on white, fixed size, so the stored PNG and its hash do not depend on the screen. */
internal fun renderSignaturePng(strokes: List<List<Pair<Float, Float>>>): ByteArray {
    val bitmap = Bitmap.createBitmap(PAD_W, PAD_H, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap); canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK; style = android.graphics.Paint.Style.STROKE; strokeWidth = 6f; isAntiAlias = true; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND }
    strokes.forEach { stroke ->
        val path = android.graphics.Path()
        stroke.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * PAD_W, y * PAD_H) else path.lineTo(x * PAD_W, y * PAD_H) }
        if (stroke.size == 1) path.lineTo(stroke[0].first * PAD_W + 0.5f, stroke[0].second * PAD_H)
        canvas.drawPath(path, paint)
    }
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
}

/** Shrinks a camera photo to at most 1600 px on its long side and re-encodes it as JPEG; these bytes are what is hashed. */
internal fun compressSignaturePhoto(file: File): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it); bitmap.recycle() }.toByteArray().takeIf { it.size <= NoteSignatureRules.MAX_IMAGE_BYTES }
}

/** Saves the signature for [ack] after the confirmation is saved. Runs inside the confirm action. False when it could not be saved. */
internal suspend fun saveNoteSignature(context: android.content.Context, namespace: String, version: CareVersion, ack: Acknowledgement, kind: String, bytes: ByteArray): Boolean = try {
    val signature = NoteSignatureRules.create(version, ack, kind, bytes, System.currentTimeMillis())
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { NoteSignatureStore.of(context, namespace).save(signature, bytes) }
    true
} catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; false }

/** Draw on the screen (primary) or take a photo of a signature on paper. */
@Composable internal fun NoteSignatureCapture(draft: SignatureDraft, enabled: Boolean, onError: (String) -> Unit) { val t = careText()
    val context = LocalContext.current
    var photoPath by remember { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = File(photoPath); photoPath = ""
        if (saved && file.isFile && file.length() > 0) {
            val bytes = runCatching { compressSignaturePhoto(file) }.getOrNull()
            if (bytes != null) { draft.photo = bytes; draft.strokes = emptyList() } else onError(t("The photo could not be read. Try again or draw instead.", "फ़ोटो पढ़ी नहीं जा सकी। फिर कोशिश करें या स्क्रीन पर हस्ताक्षर करें।"))
        }
        file.delete()
    }
    Column(Modifier.fillMaxWidth().testTag("note-signature-capture"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Sign here", "यहाँ हस्ताक्षर करें"), fontWeight = FontWeight.Bold)
        val photo = draft.photo
        if (photo != null) {
            val bitmap = remember(photo) { BitmapFactory.decodeByteArray(photo, 0, photo.size)?.asImageBitmap() }
            if (bitmap != null) Image(bitmap, t("Photo of the signature", "हस्ताक्षर की फ़ोटो"), Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit)
            TextButton(onClick = { draft.photo = null }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Remove photo and draw instead", "फ़ोटो हटाएँ और स्क्रीन पर हस्ताक्षर करें"), color = DoctorAccent) }
        } else {
            val ink = DoctorCardInk
            Canvas(Modifier.fillMaxWidth().aspectRatio(PAD_W / PAD_H.toFloat()).clip(RoundedCornerShape(12.dp)).background(Color.White).semantics { contentDescription = "Signature pad · हस्ताक्षर की जगह" }.testTag("note-signature-pad")
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    fun norm(o: Offset) = (o.x / size.width).coerceIn(0f, 1f) to (o.y / size.height).coerceIn(0f, 1f)
                    detectDragGestures(onDragStart = { draft.strokes = draft.strokes + listOf(listOf(norm(it))) }) { change, _ ->
                        change.consume()
                        draft.strokes = draft.strokes.dropLast(1) + listOf(draft.strokes.lastOrNull().orEmpty() + norm(change.position))
                    }
                }) {
                draft.strokes.forEach { stroke ->
                    if (stroke.isEmpty()) return@forEach
                    val path = Path().apply { stroke.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height) } }
                    drawPath(path, ink, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { draft.strokes = emptyList() }, enabled = enabled && draft.strokes.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp).testTag("note-signature-clear")) { Text(t("Clear", "मिटाएँ"), color = DoctorAccent) }
                TextButton(onClick = {
                    val directory = File(context.cacheDir, "signature-photo").apply { deleteRecursively(); mkdirs() }
                    val file = File(directory, "${newId()}.jpg")
                    runCatching { check(file.createNewFile()); photoPath = file.absolutePath; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) }
                        .onFailure { file.delete(); photoPath = ""; onError(t("No camera app is available. Draw the signature instead.", "कैमरा ऐप नहीं मिला। स्क्रीन पर हस्ताक्षर करें।")) }
                }, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp).testTag("note-signature-photo")) {
                    Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp), tint = DoctorAccent); Spacer(Modifier.width(6.dp)); Text(t("Photo of a paper signature", "काग़ज़ पर किए हस्ताक्षर की फ़ोटो"), color = DoctorAccent)
                }
            }
        }
        Text(t("The signature stays on this phone with a SHA-256 fingerprint tied to this exact version.", "हस्ताक्षर इसी फ़ोन पर रहता है, इस संस्करण से जुड़े SHA-256 फ़िंगरप्रिंट के साथ।"), fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardSub)
    }
}

internal fun signedAtLabel(at: Long): String = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))

/** "Signed by <name> · <role> · <time>" with the image and its fingerprint, under a confirmation. */
@Composable internal fun NoteSignedBy(signature: NoteSignature, store: NoteSignatureStore) { val t = careText()
    val bytes by produceState<ByteArray?>(null, signature.id) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { store.checkedImage(signature) }.getOrNull() } }
    val role = if (signature.relationship == "Patient") t("Patient", "मरीज़") else t("Family / carer", "परिवार / देखभाल करने वाले")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).testTag("note-signed-${signature.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(t("Signed by ", "हस्ताक्षर: ") + "${signature.signerName} · $role · ${signedAtLabel(signature.signedAt)}", fontWeight = FontWeight.Medium)
        val image = bytes
        val bitmap = remember(image) { image?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
        if (bitmap != null) Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.dp, DoctorHairline)) {
            Image(bitmap, t("Signature of ", "हस्ताक्षर: ") + signature.signerName, Modifier.fillMaxWidth().heightIn(max = 120.dp).padding(6.dp), contentScale = ContentScale.Fit)
        } else Text(t("The signature image is not on this phone or has changed.", "हस्ताक्षर की तस्वीर इस फ़ोन पर नहीं है या बदल गई है।"), fontSize = 12.sp, color = Rust)
        Text((if (signature.kind == NoteSignatureRules.PHOTO) t("Photo of a paper signature", "काग़ज़ पर किए हस्ताक्षर की फ़ोटो") else t("Drawn on this phone", "इसी फ़ोन पर किए गए")) +
            " · SHA-256 ${NoteSignatureRules.shortHash(signature.imageSha256)} · " + t("tied to version fingerprint", "संस्करण फ़िंगरप्रिंट से जुड़ा") + " ${NoteSignatureRules.shortHash(signature.contentHash)}", fontSize = 12.sp, lineHeight = 16.sp, color = DoctorCardSub)
    }
}
