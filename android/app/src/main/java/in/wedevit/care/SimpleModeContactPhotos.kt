package `in`.wedevit.care

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Photos of people for Simple Mode's Call screen (the nurse, a son, the palliative team): the family sets one per care
 * contact from the gallery or the camera, and the patient taps the face instead of reading a name or a number. Kept ONLY
 * on this phone, inside the person's report folder, so removing the person or the account removes them too.
 */
internal class SimpleContactPhotos(context: Context, storage: String) {
    private val root = File(context.filesDir, "reports/${storage.ifBlank { "guest" }}")
    init { require(storage.none { it == '/' || it == '\\' } && storage != "..") }

    private fun target(person: String, contact: String): File {
        require(SimpleModeRules.safeFileId(person) && SimpleModeRules.safeFileId(contact)) { "This contact could not be identified." }
        return File(File(File(root, person), "simple-contact-photos"), "$contact.jpg")
    }

    /** The contact's photo, or null when none is set (or the ids are not usable). */
    fun file(person: String, contact: String): File? = runCatching { target(person, contact).takeIf { it.isFile && it.length() > 0 } }.getOrNull()

    /** Keeps a square, right-way-up JPEG ([SIDE] px) of the photo at [uri] as this contact's call picture. */
    suspend fun set(context: Context, person: String, contact: String, uri: Uri) = withContext(Dispatchers.IO) {
        val square = square(decode(context, uri))
        val file = target(person, contact).apply { parentFile?.mkdirs() }
        val temp = File(file.parentFile, "${file.name}.part")
        try {
            temp.outputStream().use { check(square.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
            if (!temp.renameTo(file)) { file.delete(); check(temp.renameTo(file)) }
        } catch (failure: Exception) { temp.delete(); throw failure } finally { square.recycle() }
        revision.value++
    }

    fun remove(person: String, contact: String) { runCatching { target(person, contact).delete() }; revision.value++ }

    companion object {
        /** Bumped on every photo set or removed, so the Call screen and the family panel show it at once. */
        val revision = MutableStateFlow(0)
        private const val SIDE = 480

        private fun square(bitmap: Bitmap): Bitmap {
            val side = minOf(bitmap.width, bitmap.height)
            val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
            val scaled = if (side > SIDE) Bitmap.createScaledBitmap(cropped, SIDE, SIDE, true) else cropped
            if (cropped !== bitmap && cropped !== scaled) cropped.recycle()
            if (bitmap !== cropped && bitmap !== scaled) bitmap.recycle()
            return scaled
        }

        private fun decode(context: Context, uri: Uri): Bitmap {
            val resolver = context.contentResolver
            val longSide = SIDE * 2
            if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                require(info.size.width > 0 && info.size.height > 0 && info.size.width.toLong() * info.size.height <= 250_000_000L) { "This photo could not be read." }
                val scale = minOf(1.0, longSide.toDouble() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo could not be read." }
            var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > longSide * 2) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("This photo could not be read.")
            val orientation = runCatching { resolver.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } }.getOrNull() ?: 1
            val matrix = Matrix().apply { when (orientation) { 3 -> setRotate(180f); 6 -> setRotate(90f); 8 -> setRotate(-90f) } }
            return if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        }
    }
}

/** This contact's photo file (it follows [SimpleContactPhotos.revision], so a new photo shows at once). */
@Composable internal fun rememberSimpleContactPhoto(storage: String, person: String, contact: String): File? {
    val context = LocalContext.current
    val store = remember(storage) { SimpleContactPhotos(context.applicationContext, storage) }
    val revision by SimpleContactPhotos.revision.collectAsStateWithLifecycle()
    return remember(store, person, contact, revision) { store.file(person, contact) }
}

/** A round face for a call button: the family's photo of the person, or else a big initial on the tone's colour. */
@Composable internal fun SimpleContactAvatar(photo: File?, initial: String, tone: SaharaTone, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val motion = SaharaMotion.enabled
    Surface(shape = CircleShape, color = tone.solid, contentColor = tone.onSolid, border = BorderStroke(3.dp, tone.ink), modifier = modifier.size(size)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(initial, fontSize = (size.value * .5f).sp, fontWeight = FontWeight.Bold, color = tone.onSolid, maxLines = 1)
            // Coil decodes at the drawn size off the main thread and fades the face in over the letter.
            if (photo != null) {
                val request = remember(photo.path, photo.lastModified(), pixels, motion) { ImageRequest.Builder(context).data(photo).size(pixels).crossfade(motion).build() }
                AsyncImage(request, null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
            }
        }
    }
}

/**
 * For the family: set, change or remove the photo on one person's call button, from the gallery or the camera. Shown
 * from the family's photo list in More and from a long press on a call button in Simple Mode.
 */
@Composable internal fun SimpleContactPhotoDialog(storage: String, person: String, contact: CareContact, onDismiss: () -> Unit) {
    val t = careText(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    val store = remember(storage) { SimpleContactPhotos(context.applicationContext, storage) }
    val photo = rememberSimpleContactPhoto(storage, person, contact.id)
    var note by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf("") }
    val haptic = rememberSaharaHaptic()
    fun keep(uri: Uri, temporary: File?) = scope.launch {
        working = true; note = ""
        try { store.set(context.applicationContext, person, contact.id, uri); haptic() }
        catch (failure: Exception) { if (failure is CancellationException) throw failure; note = t("Couldn't save photo.", "फ़ोटो नहीं सहेजी गई।") }
        finally { temporary?.delete(); working = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) keep(uri, null) }
    // The camera writes into the same cache folder the strip photos use (it is shared through the app's file provider).
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPath.takeIf(String::isNotBlank)?.let(::File); pendingPath = ""
        val wrote = file != null && runCatching { file.length() > 0 }.getOrDefault(false)
        if (file != null && wrote) keep(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), file)
        else { file?.delete(); if (taken) note = t("No photo came back from the camera. Try again.", "कैमरे से फ़ोटो नहीं आई। फिर कोशिश करें।") }
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Paper, modifier = Modifier.saharaDialogEnter().testTag("simple-contact-photo-dialog"),
        title = { Column { Text(t("Photo on the call button", "फ़ोन बटन की फ़ोटो"), fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold); Text(contact.name, fontSize = 18.sp, color = SaharaColors.InkSub) } },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleContactAvatar(photo, SimpleModeRules.initial(contact.name), SaharaTone.Leaf, 112.dp)
                Text(t("The patient taps this face to call. Use a clear photo of the face.", "मरीज़ फ़ोन करने के लिए यह चेहरा छूते हैं। चेहरे की साफ़ फ़ोटो लगाएँ।"), fontSize = 16.sp, lineHeight = 22.sp, color = SaharaColors.InkSub)
                OutlinedButton(onClick = {
                    val directory = File(context.cacheDir, "medicine-scan").apply { mkdirs() }
                    val file = File(directory, "contact-${newId()}.jpg")
                    runCatching { check(file.createNewFile()); pendingPath = file.absolutePath; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) }
                        .onFailure { file.delete(); pendingPath = ""; note = t("No camera app", "कैमरा ऐप नहीं मिला") }
                }, enabled = !working, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("simple-contact-photo-camera")) {
                    Icon(Icons.Default.PhotoCamera, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text(t("Take photo", "फ़ोटो लें"), fontSize = 18.sp)
                }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !working,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("simple-contact-photo-gallery")) {
                    Icon(Icons.Default.PhotoLibrary, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text(t("Choose photo", "फ़ोटो चुनें"), fontSize = 18.sp)
                }
                if (photo != null) TextButton(onClick = { store.remove(person, contact.id) }, enabled = !working, modifier = Modifier.heightIn(min = 48.dp).testTag("simple-contact-photo-remove")) {
                    Icon(Icons.Default.DeleteOutline, null, tint = SaharaTone.Error.ink); Spacer(Modifier.width(6.dp)); Text(t("Remove photo", "फ़ोटो हटाएँ"), fontSize = 16.sp, color = SaharaTone.Error.ink)
                }
                if (note.isNotBlank()) Text(note, fontSize = 16.sp, color = SaharaTone.Error.ink, modifier = Modifier.announce())
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("simple-contact-photo-done")) { Text(t("Done", "हो गया"), fontSize = 18.sp, fontWeight = FontWeight.Bold) } })
}

/**
 * The family's list of the people on the Call screen, each with its face and "Add photo" / "Change photo". Shown in More
 * under the Simple mode switch while Simple Mode is on.
 */
@Composable internal fun SimpleCallPhotosPanel(vm: CareModel, person: Person) {
    val t = careText()
    val contacts by vm.careContacts.collectAsStateWithLifecycle()
    LaunchedEffect(person.id) { vm.refreshCareContacts() }
    val callable = remember(contacts, person.id) { SimpleModeRules.callable(contacts, person.id) }
    var editing by remember { mutableStateOf<CareContact?>(null) }
    Column(Modifier.fillMaxWidth().padding(start = 68.dp, end = 12.dp, bottom = 8.dp).testTag("more-simple-call-photos"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Photos for the Call buttons", "फ़ोन बटन के लिए फ़ोटो"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
        if (callable.isEmpty()) Text(t("Add care team phone numbers first; each person then gets a photo button.", "पहले देखभाल टीम के फ़ोन नंबर जोड़ें; फिर हर व्यक्ति का फ़ोटो बटन बनेगा।"), fontSize = 14.sp, color = DoctorCardSub)
        callable.forEach { contact -> key(contact.id) {
            val photo = rememberSimpleContactPhoto(vm.storageNamespace, person.id, contact.id)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                SimpleContactAvatar(photo, SimpleModeRules.initial(contact.name), SaharaTone.Leaf, 44.dp)
                Spacer(Modifier.width(12.dp))
                Text(contact.name, Modifier.weight(1f), fontSize = 16.sp, color = DoctorCardInk, maxLines = 2)
                TextButton(onClick = { editing = contact }, modifier = Modifier.heightIn(min = 48.dp).testTag("more-simple-call-photo-${contact.id}")) {
                    Text(if (photo == null) t("Add photo", "फ़ोटो जोड़ें") else t("Change photo", "फ़ोटो बदलें"), fontSize = 15.sp, color = SaharaTone.Teal.ink, fontWeight = FontWeight.SemiBold)
                }
            }
        } }
    }
    editing?.let { contact -> SimpleContactPhotoDialog(vm.storageNamespace, person.id, contact) { editing = null } }
}
