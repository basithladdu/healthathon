package `in`.wedevit.care

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Strip and box photos for one medicine, kept ONLY on this phone (there is no upload path for them).
 * They sit inside the person's report folder, so removing the person or deleting the account removes them too.
 */
class MedicineStripPhotos(context: Context, storage: String) {
    private val root = File(context.filesDir, "reports/${storage.ifBlank { "guest" }}")
    init { require(storage.none { it == '/' || it == '\\' } && storage != "..") }

    private fun folder(person: String, item: String): File {
        require(runCatching { UUID.fromString(person); UUID.fromString(item) }.isSuccess) { "This medicine could not be identified." }
        return File(File(File(root, person), "medicine-strips"), item)
    }

    fun list(person: String, item: String): List<File> = runCatching {
        folder(person, item).listFiles { file -> file.isFile && file.extension == "jpg" }.orEmpty().sortedBy { it.name }
    }.getOrDefault(emptyList())

    fun first(person: String, item: String): File? = list(person, item).firstOrNull()

    /** Copies a photo in as a bounded JPEG (long side 1280 px), turned the right way up. */
    suspend fun add(context: Context, person: String, item: String, uri: Uri): File = withContext(Dispatchers.IO) {
        require(list(person, item).size < MAX_PHOTOS) { "Use up to $MAX_PHOTOS photos for one medicine." }
        val bitmap = decode(context, uri)
        val directory = folder(person, item).apply { mkdirs() }
        val target = File(directory, "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.jpg")
        try { target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it)) } }
        catch (failure: Exception) { target.delete(); throw failure }
        finally { bitmap.recycle() }
        target
    }

    fun remove(person: String, item: String, file: File) {
        val directory = folder(person, item).canonicalFile
        require(file.canonicalFile.parentFile == directory)
        file.delete()
    }

    /** One picture for the reminder: the first strip photo of each medicine due, side by side (up to four). */
    fun collage(person: String, items: List<String>, height: Int = 360): Bitmap? {
        val parts = items.mapNotNull { first(person, it) }.take(4).mapNotNull { thumbnail(it, height) }
        if (parts.isEmpty()) return null
        val gap = 8
        val scaled = parts.map { part -> if (part.height == height) part else Bitmap.createScaledBitmap(part, (part.width * height.toFloat() / part.height).roundToInt().coerceAtLeast(1), height, true).also { if (it !== part) part.recycle() } }
        val result = Bitmap.createBitmap(scaled.sumOf { it.width } + gap * (scaled.size - 1), height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result); canvas.drawColor(android.graphics.Color.WHITE)
        var x = 0f
        scaled.forEach { canvas.drawBitmap(it, x, 0f, null); x += it.width + gap; it.recycle() }
        return result
    }

    companion object {
        const val MAX_PHOTOS = 6
        private const val LONG_SIDE = 1280

        fun thumbnail(file: File, size: Int): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()

        private fun decode(context: Context, uri: Uri): Bitmap {
            val resolver = context.contentResolver
            if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                require(info.size.width > 0 && info.size.height > 0 && info.size.width.toLong() * info.size.height <= 250_000_000L) { "This photo could not be read." }
                val scale = minOf(1.0, LONG_SIDE.toDouble() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This photo could not be read." }
            var sample = 1; while (maxOf(bounds.outWidth, bounds.outHeight) / sample > LONG_SIDE * 2) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("This photo could not be read.")
            val orientation = runCatching { resolver.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } }.getOrNull() ?: 1
            val matrix = Matrix().apply { when (orientation) { 3 -> setRotate(180f); 6 -> setRotate(90f); 8 -> setRotate(-90f) } }
            val scale = minOf(1f, LONG_SIDE.toFloat() / maxOf(decoded.width, decoded.height)); matrix.postScale(scale, scale)
            return if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { if (it !== decoded) decoded.recycle() }
        }

        /** On-phone text reading of one stored strip photo; only the name, strength and form are taken from it. */
        suspend fun read(file: File): MedicinePhotoFields.Reading = withContext(Dispatchers.IO) {
            val bitmap = BitmapFactory.decodeFile(file.path) ?: error("This photo could not be read.")
            val ocr = CareBilingualOcr()
            try { MedicinePhotoFields.fromStripText(ocr.read(bitmap)) } finally { ocr.close() }
        }
    }
}

@Composable private fun StripThumb(file: File?, size: Int, modifier: Modifier = Modifier) {
    val image by produceState<android.graphics.Bitmap?>(null, file?.path) { value = file?.let { withContext(Dispatchers.IO) { MedicineStripPhotos.thumbnail(it, size * 2) } } }
    Surface(shape = RoundedCornerShape(12.dp), color = SaharaSoftBlue, modifier = modifier.size(size.dp)) {
        val bitmap = image
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Default.Medication, null, tint = DoctorAccent, modifier = Modifier.size((size / 2).dp)) }
    }
}

/**
 * Strip photos for one medicine: take or choose one or more. [onRead] gets only the name, strength and form read
 * from a new photo, for blank fields; everything else is typed by hand.
 */
@Composable fun StripPhotoRow(storage: String, personId: String, itemId: String, enabled: Boolean = true, onRead: ((MedicinePhotoFields.Reading) -> Unit)? = null) {
    val context = LocalContext.current; val t = careText(); val scope = rememberCoroutineScope()
    val store = remember(storage) { MedicineStripPhotos(context.applicationContext, storage) }
    var revision by remember { mutableIntStateOf(0) }
    val photos = remember(personId, itemId, revision) { store.list(personId, itemId) }
    var note by remember { mutableStateOf("") }
    val latestOnRead by rememberUpdatedState(onRead)
    var pendingPath by rememberSaveable { mutableStateOf("") }
    fun keep(uri: Uri, temporary: File?) = scope.launch {
        try {
            val saved = store.add(context.applicationContext, personId, itemId, uri); revision++
            note = ""
            latestOnRead?.let { read ->
                val reading = runCatching { MedicineStripPhotos.read(saved) }.getOrNull()
                if (reading != null) read(reading)
                note = if (reading?.name.isNullOrBlank()) t("No name read from this photo (a cut strip often has none). Type the name, or pick it from the list as you type.", "इस फ़ोटो से नाम नहीं पढ़ा गया (कटे पत्ते पर अक्सर नाम नहीं होता)। नाम लिखें, या लिखते समय सूची से चुनें।")
                    else t("Name, strength and form were read from the photo where empty. Check them.", "ख़ाली जगहों में नाम, ताक़त और रूप फ़ोटो से भरे गए। इन्हें जाँचें।")
            }
        } catch (failure: Exception) { if (failure is CancellationException) throw failure; note = t("This photo couldn't be kept. Try another photo.", "यह फ़ोटो नहीं रखी जा सकी। दूसरी फ़ोटो लें।") }
        finally { temporary?.delete() }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val file = pendingPath.takeIf(String::isNotBlank)?.let(::File); pendingPath = ""
        if (taken && file != null) keep(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file), file) else file?.delete()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) keep(uri, null) }
    Column(Modifier.fillMaxWidth().testTag("strip-photos-$itemId"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Strip photos (optional)", "पत्ते की फ़ोटो (चाहें तो)"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
        Text(t("Shown with the dose reminder so anyone giving the medicine can match the strip. Kept only on this phone.", "दवा के रिमाइंडर के साथ दिखती है, ताकि दवा देने वाला पत्ता मिला सके। सिर्फ़ इसी फ़ोन पर रहती है।"), fontSize = 12.sp, color = DoctorCardFaint)
        if (photos.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            photos.forEachIndexed { index, file -> Box {
                StripThumb(file, 72)
                if (enabled) IconButton(onClick = { runCatching { store.remove(personId, itemId, file) }; revision++ }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Surface(shape = RoundedCornerShape(50), color = Paper) { Icon(Icons.Default.Close, t("Remove strip photo ${index + 1}", "पत्ते की फ़ोटो ${index + 1} हटाएँ"), Modifier.padding(3.dp).size(16.dp)) }
                }
            } }
        }
        if (enabled && photos.size < MedicineStripPhotos.MAX_PHOTOS) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val directory = File(context.cacheDir, "medicine-scan").apply { mkdirs() }
                val file = File(directory, "strip-${newId()}.jpg")
                runCatching { check(file.createNewFile()); pendingPath = file.absolutePath; camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)) }
                    .onFailure { file.delete(); pendingPath = ""; note = t("No camera app is available. Choose a photo instead.", "कैमरा ऐप नहीं मिला। फ़ोटो चुनें।") }
            }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.PhotoCamera, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("Take photo", "फ़ोटो लें"), fontSize = 13.sp, maxLines = 2) }
            OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(t("Choose photo", "फ़ोटो चुनें"), fontSize = 13.sp, maxLines = 2) }
        }
        if (note.isNotBlank()) Text(note, fontSize = 12.sp, color = RustText, modifier = Modifier.announce())
    }
}

/** Dose times typed by hand: quick choices plus any other HH:mm time. Saved with the medicine, so family phones get the same reminder times. */
@Composable fun DoseTimesField(times: List<String>, onChange: (List<String>) -> Unit, enabled: Boolean = true) {
    val t = careText(); val hindi = careHindi()
    var other by remember { mutableStateOf(times.filter { it !in MedicineDoseRules.commonTimes }.joinToString(", ")) }
    var error by remember { mutableStateOf(false) }
    Text(t("Dose times (optional)", "दवा का समय (चाहें तो)"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MedicineDoseRules.commonTimes.forEach { time ->
            val slot = MedicineDoseRules.slot(time)
            FilterChip(selected = time in times, enabled = enabled, onClick = { onChange(if (time in times) times - time else (times + time).sorted()) },
                label = { Text("${careTr(hindi, slot.english, slot.hindi)} $time") }, modifier = Modifier.heightIn(min = 48.dp).testTag("dose-time-$time"))
        }
    }
    CareField(other, { value ->
        other = value.take(60)
        val typed = other.split(',').map(String::trim).filter(String::isNotEmpty)
        val valid = runCatching { CareReminderRules.validTimes(typed) }.getOrNull()
        error = valid == null
        if (valid != null) onChange((times.filter { it in MedicineDoseRules.commonTimes } + valid).distinct().sorted())
    }, t("Other times", "दूसरे समय"), singleLine = true, placeholder = t("e.g. 06:00, 14:30", "जैसे 06:00, 14:30"),
        supporting = if (error) t("Use HH:mm, such as 08:30.", "HH:mm लिखें, जैसे 08:30।") else null)
}

/** The hand-entered parts of a medicine, each label with an example. Nothing here is ever filled from a photo except via [MedicinePhotoFields]. */
@Composable fun MedicineEntryForm(name: String, onName: (String) -> Unit, detail: String, onDetail: (String) -> Unit, storage: String, personId: String, itemId: String, busy: Boolean) {
    val t = careText(); val hindi = careHindi()
    val entry = MedicineEntryText.parse(detail) ?: MedicineEntry()
    fun change(next: MedicineEntry) = onDetail(MedicineEntryText.detail(next))
    CareField(name, onName, t("Medicine name", "दवा का नाम"), singleLine = true, placeholder = t("e.g. Paracetamol", "जैसे Paracetamol"),
        supporting = t("Strip cut or name missing? Type it, or pick from the list as you type.", "पत्ता कटा है या नाम नहीं दिखता? नाम लिखें, या लिखते समय सूची से चुनें।"))
    MedicineNameSuggestions(name, enabled = !busy) { onName(it) }
    CareField(entry.strength, { change(entry.copy(strength = it.take(80))) }, t("Strength", "ताक़त"), singleLine = true, placeholder = t("e.g. 500 mg, 5 mg/5 ml", "जैसे 500 mg, 5 mg/5 ml"))
    Text(t("Form", "रूप"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MedicinePhotoFields.forms.forEach { (english, hindiName) ->
            FilterChip(selected = entry.form.equals(english, true), enabled = !busy, onClick = { change(entry.copy(form = if (entry.form.equals(english, true)) "" else english)) },
                label = { Text(careTr(hindi, english, hindiName)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    CareField(entry.howMuch, { change(entry.copy(howMuch = it.take(80))) }, t("How much each time", "हर बार कितनी"), singleLine = true, placeholder = t("e.g. 1 tablet, 1½ tablets, 5 ml", "जैसे 1 गोली, 1½ गोली, 5 ml"))
    CareField(entry.howOften, { change(entry.copy(howOften = it.take(120))) }, t("How often", "कितनी बार"), singleLine = true, placeholder = t("e.g. twice a day, only when needed", "जैसे दिन में दो बार, ज़रूरत होने पर ही"))
    DoseTimesField(entry.times, { change(entry.copy(times = it)) }, enabled = !busy)
    Text(t("Food", "खाना"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = DoctorCardInk)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(MedicineMealTiming.BEFORE_FOOD to ("Before food" to "खाने से पहले"), MedicineMealTiming.WITH_FOOD to ("With food" to "खाने के साथ"), MedicineMealTiming.AFTER_FOOD to ("After food" to "खाने के बाद")).forEach { (timing, label) ->
            FilterChip(selected = entry.food == timing, enabled = !busy, onClick = { change(entry.copy(food = if (entry.food == timing) MedicineMealTiming.NOT_STATED else timing)) }, label = { Text(t(label.first, label.second)) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    CareField(entry.days?.toString().orEmpty(), { value -> if (value.isBlank()) change(entry.copy(days = null)) else value.toIntOrNull()?.takeIf { it in 1..365 }?.let { change(entry.copy(days = it)) } },
        t("For how many days", "कितने दिन"), singleLine = true, placeholder = t("e.g. 5 · leave empty if ongoing", "जैसे 5 · चलती रहे तो ख़ाली छोड़ें"))
    CareField(entry.directions, { change(entry.copy(directions = it.take(500))) }, t("Directions", "तरीक़ा"), placeholder = t("e.g. as written: dissolve in water", "जैसे पर्चे में लिखा: पानी में घोलकर"))
    Text(t("Type these from the prescription. Sahara never reads how much, how often, times or directions from a photo.", "ये पर्चे से देखकर लिखें। सहारा कितनी, कितनी बार, समय या तरीक़ा फ़ोटो से कभी नहीं पढ़ता।"), fontSize = 12.sp, color = DoctorCardFaint)
    if (detail.isBlank()) Text(t("Add at least the strength, form or how much each time.", "कम से कम ताक़त, रूप या हर बार कितनी लिखें।"), fontSize = 12.sp, color = RustText)
    StripPhotoRow(storage, personId, itemId, enabled = !busy) { reading ->
        if (name.isBlank() && reading.name.isNotBlank()) onName(reading.name.take(200))
        val filled = entry.copy(strength = entry.strength.ifBlank { reading.strength }, form = entry.form.ifBlank { reading.form })
        if (filled != entry) change(filled)
    }
}

/** Today's doses grouped by time ("Morning · 08:00 · 3 medicines") with each medicine's strip photo, so a family member can match strips by sight. */
@Composable fun TodayDosesCard(vm: CareModel) {
    val t = careText(); val hindi = careHindi(); val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle(); val person by vm.selected.collectAsStateWithLifecycle()
    val scheduler = remember(vm.accountNamespace) { CareReminderScheduler(context.applicationContext, vm.accountNamespace) }
    val store = remember(vm.storageNamespace) { MedicineStripPhotos(context.applicationContext, vm.storageNamespace) }
    val groups = remember(items, person) {
        MedicineDoseRules.groups(items.filter { it.kind == "medicine" && it.personId == person }.map { DoseMedicine(it.id, it.personId, it.title, MedicineDoseRules.times(it, scheduler.option(it.id))) })
    }
    if (groups.isEmpty()) return
    val today = LocalDate.now().toString(); val taken = items.filter { it.doneOn == today }.map { it.id }.toSet()
    Surface(shape = RoundedCornerShape(20.dp), color = Paper, border = BorderStroke(1.dp, DoctorHairline), modifier = Modifier.fillMaxWidth().testTag("today-doses")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Today's doses", "आज की दवाएँ"), fontFamily = SaharaTitleFont, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = DoctorCardInk, modifier = Modifier.a11yHeading())
            groups.forEach { group ->
                Column(Modifier.testTag("today-doses-${group.time}"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(MedicineDoseRules.title(group, hindi), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = RustText)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        group.medicines.forEach { medicine ->
                            Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                StripThumb(remember(medicine.itemId, items) { store.first(medicine.personId, medicine.itemId) }, 76)
                                Text(medicine.name, fontSize = 12.sp, color = DoctorCardInk, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
                                if (medicine.itemId in taken) Text(t("Ticked today", "आज टिक"), fontSize = 11.sp, color = DoctorCardFaint)
                            }
                        }
                    }
                }
            }
            Text(t("Times come from Reminders or the medicine's saved times. Strip photos stay on the phone they were taken on.", "समय रिमाइंडर या दवा के सहेजे समय से आते हैं। पत्ते की फ़ोटो उसी फ़ोन पर रहती है जिस पर ली गई।"), fontSize = 12.sp, color = DoctorCardFaint)
        }
    }
}
