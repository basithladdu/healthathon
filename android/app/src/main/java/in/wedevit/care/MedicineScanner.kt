@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package `in`.wedevit.care

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private enum class MedicineScannerPhase { Select, Scanning, Review }

@Composable
fun MedicineScanner(vm: CareModel, onClose: () -> Unit, onAccount: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documentReader = remember(context) { CareDocumentReader(context.applicationContext) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selectedPerson by vm.selected.collectAsStateWithLifecycle()
    val personAtOpen = rememberSaveable { vm.selected.value }
    // A running scan and its AI result intentionally stay in memory only. If Android
    // recreates the activity, return to the retained photo selection instead of
    // restoring a spinner or a review screen whose result no longer exists.
    var phase by remember { mutableStateOf(MedicineScannerPhase.Select) }
    var prescriptionUris by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var medicineUris by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var scanId by rememberSaveable { mutableStateOf(newId()) }
    var progress by remember { mutableStateOf<MedicineScanProgress?>(null) }
    var scanError by rememberSaveable { mutableStateOf<String?>(null) }
    var drafts by remember { mutableStateOf(emptyList<MedicineDraft>()) }
    var savingSources by remember { mutableStateOf(false) }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var unmatched by remember { mutableStateOf(emptyList<Int>()) }
    var unmatchedReviewed by rememberSaveable { mutableStateOf(false) }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    var pendingKind by rememberSaveable { mutableStateOf("") }
    var pendingUri by rememberSaveable { mutableStateOf("") }
    var pendingPath by rememberSaveable { mutableStateOf("") }

    fun releaseReadAccess(value: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(value),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun releaseSelectedPhotos() {
        (prescriptionUris + medicineUris).distinct().forEach(::releaseReadAccess)
    }

    fun addSelected(current: List<String>, selected: List<Uri>, maximum: Int, label: String): List<String> {
        selected.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        val merged = (current + selected.map(Uri::toString)).distinct()
        if (merged.size > maximum) vm.message.value = "Use up to $maximum $label. The extra photos weren't added."
        return merged.take(maximum)
    }

    val prescriptionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        prescriptionUris = addSelected(prescriptionUris, selected, 5, "prescription pages")
    }
    val medicinePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        medicineUris = addSelected(medicineUris, selected, 10, "medicine photos")
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved && pendingUri.isNotBlank()) {
            if (pendingKind == "prescription") prescriptionUris = addSelected(prescriptionUris, listOf(Uri.parse(pendingUri)), 5, "prescription pages")
            else medicineUris = addSelected(medicineUris, listOf(Uri.parse(pendingUri)), 10, "medicine photos")
        } else if (pendingPath.isNotBlank()) File(pendingPath).delete()
        pendingKind = ""; pendingUri = ""; pendingPath = ""
    }

    fun photograph(kind: String) {
        val maximumReached = if (kind == "prescription") prescriptionUris.size >= 5 else medicineUris.size >= 10
        if (maximumReached) {
            vm.message.value = if (kind == "prescription") "Use up to 5 prescription pages." else "Use up to 10 medicine photos."
            return
        }
        val directory = File(context.cacheDir, "medicine-scan").apply { mkdirs() }
        val file = File(directory, "${kind}-${newId()}.jpg")
        runCatching {
            check(file.createNewFile())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingKind = kind; pendingUri = uri.toString(); pendingPath = file.absolutePath
            camera.launch(uri)
        }.onFailure {
            file.delete(); pendingKind = ""; pendingUri = ""; pendingPath = ""
            vm.message.value = "No camera app is available. Choose a photo instead."
        }
    }

    fun cleanAndClose() {
        scanJob?.cancel()
        releaseSelectedPhotos()
        File(context.cacheDir, "medicine-scan").deleteRecursively()
        onClose()
    }

    fun returnToImages() {
        scanJob?.cancel(); scanJob = null; progress = null; scanError = null; scanId = newId(); phase = MedicineScannerPhase.Select
    }
    LaunchedEffect(selectedPerson) { if (selectedPerson != personAtOpen) { scanJob?.cancel(); onClose() } }

    BackHandler {
        if (phase == MedicineScannerPhase.Select) cleanAndClose() else returnToImages()
    }

    when (phase) {
        MedicineScannerPhase.Select -> LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("medicine-scanner-select"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScannerHeader("Scan prescription", "1 / 2") { cleanAndClose() } }
            item { Text("Add a clear prescription. Read and check the words before saving. Medicine packaging is optional for your own comparison.") }
            item {
                PhotoSelectionCard(
                    title = "Prescription pages",
                    subtitle = "Add in page order · up to 5",
                    tint = Mint,
                    uris = prescriptionUris,
                    chooseLabel = "Choose pages",
                    photographLabel = "Take photo",
                    onChoose = { prescriptionPicker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/*")) },
                    onPhotograph = { photograph("prescription") },
                    onRemove = { index ->
                        val removed = prescriptionUris[index]
                        prescriptionUris = prescriptionUris.toMutableList().also { it.removeAt(index) }
                        if (removed !in prescriptionUris && removed !in medicineUris) releaseReadAccess(removed)
                    },
                    onMove = { from, to -> prescriptionUris = prescriptionUris.moved(from, to) },
                )
            }
            item {
                PhotoSelectionCard(
                    title = "Medicine strips or boxes",
                    subtitle = "One clear photo per medicine · up to 10",
                    tint = Color(0xFFFFEDD3),
                    uris = medicineUris,
                    chooseLabel = "Choose photos",
                    photographLabel = "Take photo",
                    onChoose = { medicinePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/*")) },
                    onPhotograph = { photograph("medicine") },
                    onRemove = { index ->
                        val removed = medicineUris[index]
                        medicineUris = medicineUris.toMutableList().also { it.removeAt(index) }
                        if (removed !in prescriptionUris && removed !in medicineUris) releaseReadAccess(removed)
                    },
                    onMove = null,
                )
            }
            scanError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item {
                Text("Reading happens on your phone. Printed Latin text is supported; handwriting may need manual entry. No dose or treatment is inferred.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scanError = null; phase = MedicineScannerPhase.Scanning
                        scanJob = scope.launch {
                            try {
                                val pages = documentReader.prescription(prescriptionUris.map(Uri::parse)) { done, total ->
                                    progress = MedicineScanProgress(MedicineScanStage.PREPARING, done, total, "Prescription page ${done.coerceAtLeast(1)}")
                                }
                                val medicines = copiedPrescriptionMedicines(pages)
                                drafts = medicines.mapIndexed { index, medicine -> MedicineDraft(stableMedicineDraftId(scanId, medicine, index), medicine) }
                                warnings = listOf("Compare every copied line with the original page. The app has not matched brands, chosen doses or confirmed treatment.") + if (medicines.isEmpty()) listOf("No safe medicine lines were readable. Add medicines manually from the original prescription.") else emptyList()
                                unmatched = medicineUris.indices.toList()
                                unmatchedReviewed = unmatched.isEmpty()
                                phase = MedicineScannerPhase.Review
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                scanError = error.message ?: "The medicine organizer couldn't finish. Try again."
                                phase = MedicineScannerPhase.Select
                            } finally { scanJob = null }
                        }
                    },
                    enabled = prescriptionUris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Read prescription") }
                TextButton(onClick = {
                    drafts = listOf(MedicineDraft(newId(), manualMedicine(), false)); warnings = emptyList(); unmatched = emptyList(); unmatchedReviewed = true; phase = MedicineScannerPhase.Review
                }, modifier = Modifier.fillMaxWidth()) { Text("Add one manually") }
                TextButton(onClick = { cleanAndClose() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }

        MedicineScannerPhase.Scanning -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = Rust)
                Heading("Reading prescription", 26)
                val value = progress
                if (value?.stage == MedicineScanStage.PREPARING && value.total > 0) {
                    Text("${value.completed.coerceAtMost(value.total)} of ${value.total}")
                    if (value.fileName.isNotBlank()) Text(value.fileName, style = MaterialTheme.typography.bodySmall)
                } else Text("Reading the selected pages…", style = MaterialTheme.typography.bodyMedium)
                Text("Keep this screen open until review appears. You can cancel without changing saved medicines.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { returnToImages() }) { Text("Cancel") }
            }
        }

        MedicineScannerPhase.Review -> LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("medicine-scanner-review"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ScannerHeader("Review medicine list", "2 / 2") { returnToImages() } }
            item { SurfaceCard(Sage) { Text("Check before saving", fontWeight = FontWeight.Bold); Text("Text reading can be wrong. Confirm each entry against the prescription and packaging. Sahara does not diagnose, prescribe or recommend treatment.") } }
            if (prescriptionUris.isNotEmpty()) item { SurfaceCard {
                Text("Original pages", fontWeight = FontWeight.SemiBold)
                prescriptionUris.forEachIndexed { index, uri -> TextButton(onClick = { runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { scanError = "No image viewer is available. Open this photo in Files to compare it." } }) { Text("View prescription page ${index + 1}") } }
            } }
            warnings.forEach { warning -> item(warning) { SurfaceCard(Peach) { Text(warning) } } }
            items(drafts.size, key = { drafts[it].id }) { index ->
                MedicineReviewCard(
                    draft = drafts[index],
                    onChange = { changed -> drafts = drafts.toMutableList().also { it[index] = changed } },
                    onRemove = { drafts = drafts.toMutableList().also { it.removeAt(index) } },
                )
            }
            if (unmatched.isNotEmpty()) item {
                SurfaceCard(Peach) {
                    Text("${unmatched.size} medicine ${if (unmatched.size == 1) "photo was" else "photos were"} not matched", fontWeight = FontWeight.Bold)
                    Text("Review ${unmatched.joinToString { "photo ${it + 1}" }}. Add any missing medicine manually or leave it out only after checking the original prescription.")
                    Spacer(Modifier.height(8.dp)); CheckLine(unmatchedReviewed, { unmatchedReviewed = it }, "I reviewed the unmatched medicine photos.")
                }
            }
            item {
                OutlinedButton(onClick = { drafts = drafts + MedicineDraft(newId(), manualMedicine(), false) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Add another manually")
                }
            }
            scanError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            item {
                val names = drafts.map { it.medicine.medicineName.trim().lowercase() }.filter(String::isNotEmpty)
                val valid = drafts.isNotEmpty() && unmatchedReviewed && names.size == names.distinct().size && drafts.all {
                    it.confirmed && it.medicine.medicineName.isNotBlank() && (it.medicine.strength.isNotBlank() || it.medicine.quantity.isNotBlank() || it.medicine.form.isNotBlank())
                }
                Button(onClick = {
                    scanError = null
                    savingSources = true
                    scope.launch {
                        try {
                            val sourceStore = MedicineSourceStore(context, vm.accountNamespace)
                            val originals = sourceStore.save(personAtOpen, scanId, prescriptionUris.map(Uri::parse), vm.reportFiles)
                            val references = withContext(Dispatchers.IO) { sourceStore.references(originals, vm.reportFiles) }
                            val linked = drafts.map { draft -> if (references.isBlank()) draft else draft.copy(medicine = draft.medicine.copy(instructions = draft.medicine.instructions + "\nOriginal prescription references:\n" + references)) }
                            require(vm.selected.value == personAtOpen) { "The selected person changed. Open this prescription for the right person." }
                            vm.saveMedicineList(linked) {
                                releaseSelectedPhotos()
                                File(context.cacheDir, "medicine-scan").deleteRecursively()
                                onClose()
                            }
                        } catch (failure: Exception) { if (failure is CancellationException) throw failure; scanError = failure.message ?: "Could not keep the original pages. Your medicine list has not been saved." }
                        finally { savingSources = false }
                    }
                }, enabled = valid && !busy && !savingSources, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (busy || savingSources) "Saving…" else "Save ${drafts.size} ${if (drafts.size == 1) "medicine" else "medicines"}")
                }
                if (!valid) Text("Add a medicine name and dose, resolve unmatched photos, and confirm every entry before saving.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { returnToImages() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Back to photos") }
                TextButton(onClick = { cleanAndClose() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun ScannerHeader(title: String, step: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.7).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            color = Mint,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(min = 58.dp),
        ) {
            Text(
                text = step,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun PhotoSelectionCard(
    title: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    uris: List<String>,
    chooseLabel: String,
    photographLabel: String,
    onChoose: () -> Unit,
    onPhotograph: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: ((Int, Int) -> Unit)?,
) {
    val context = LocalContext.current
    SurfaceCard(tint) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Surface(color = Paper, shape = RoundedCornerShape(12.dp)) { Text(uris.size.toString(), Modifier.padding(horizontal = 11.dp, vertical = 6.dp), fontWeight = FontWeight.Bold) }
        }
        uris.forEachIndexed { index, value ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(imageName(context, Uri.parse(value), index), Modifier.weight(1f), maxLines = 1)
                if (onMove != null && index > 0) IconButton(onClick = { onMove(index, index - 1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowUpward, "Move earlier") }
                if (onMove != null && index < uris.lastIndex) IconButton(onClick = { onMove(index, index + 1) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.ArrowDownward, "Move later") }
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Close, "Remove ${index + 1}") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChoose, modifier = Modifier.weight(1f)) {
                Text(chooseLabel, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
            }
            OutlinedButton(onClick = onPhotograph, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text(photographLabel, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun MedicineReviewCard(draft: MedicineDraft, onChange: (MedicineDraft) -> Unit, onRemove: () -> Unit) {
    val medicine = draft.medicine
    fun change(next: OrganizedMedicine) = onChange(draft.copy(medicine = next, confirmed = false))
    SurfaceCard(Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Medication, null, tint = Rust); Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(if (medicine.medicineName.isBlank()) "Manual medicine" else medicine.medicineName, fontWeight = FontWeight.Bold)
                Text(matchLabel(medicine), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, "Remove medicine") }
        }
        medicine.uncertainties.forEach { Text("• $it", color = Rust, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        CareField(medicine.medicineName, { change(medicine.copy(medicineName = it.take(120))) }, "Medicine name", singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { CareField(medicine.strength, { change(medicine.copy(strength = it.take(80))) }, "Strength", singleLine = true) }
            Box(Modifier.weight(1f)) { CareField(medicine.form, { change(medicine.copy(form = it.take(60))) }, "Form", singleLine = true) }
        }
        CareField(medicine.quantity, { change(medicine.copy(quantity = it.take(80))) }, "Dose per administration", singleLine = true)
        CareField(medicine.frequency, { change(medicine.copy(frequency = it.take(120))) }, "Frequency", singleLine = true)
        CareField(medicine.instructions, { change(medicine.copy(instructions = it.take(500))) }, "Directions from prescription", minLines = 2)
        Text("Food timing", fontWeight = FontWeight.SemiBold)
        if (medicine.sourceQuote.isNotBlank()) {
            Text("Original reading · ${medicine.prescriptionPageIndexes.joinToString { "page ${it + 1}" }}", style = MaterialTheme.typography.labelMedium)
            Text(medicine.sourceQuote, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            MedicineMealTiming.entries.forEach { timing ->
                FilterChip(selected = medicine.mealTiming == timing, onClick = { change(medicine.copy(mealTiming = timing)) }, label = { Text(mealTimingLabel(timing), fontSize = 11.sp) })
            }
        }
        if (medicine.suggestedTimes.isNotEmpty()) {
            Text("Exact times", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                medicine.suggestedTimes.forEach { time -> InputChip(selected = false, onClick = { change(medicine.copy(suggestedTimes = medicine.suggestedTimes - time)) }, label = { Text(time) }, trailingIcon = { Icon(Icons.Default.Close, "Remove $time", Modifier.size(15.dp)) }) }
            }
        }
        CareField(
            medicine.durationDays?.toString().orEmpty(),
            { value -> if (value.isBlank()) change(medicine.copy(durationDays = null)) else value.toIntOrNull()?.takeIf { it in 1..365 }?.let { change(medicine.copy(durationDays = it)) } },
            "Course length in days (if written)", singleLine = true,
        )
        CheckLine(draft.confirmed, { onChange(draft.copy(confirmed = it)) }, "I checked this medicine, dose and directions against the original prescription and packaging.")
    }
}

private fun manualMedicine() = OrganizedMedicine(
    medicineName = "", strength = "", form = "", quantity = "", frequency = "", suggestedTimes = emptyList(),
    mealTiming = MedicineMealTiming.NOT_STATED, instructions = "", durationDays = null, prescriptionItemNumber = null,
    medicineImageIndex = -1, prescriptionPageIndexes = emptyList(), confidence = null, uncertainties = emptyList(),
    matchQuality = MedicineMatchQuality.MANUAL,
)

private fun matchLabel(medicine: OrganizedMedicine): String = when (medicine.matchQuality) {
    MedicineMatchQuality.MATCHED -> "Matched to prescription and packaging${medicine.confidence?.let { " · ${(it * 100).toInt()}%" }.orEmpty()}"
    MedicineMatchQuality.POSSIBLE -> "Possible packaging match · check carefully"
    MedicineMatchQuality.PRESCRIPTION_ONLY -> "Found on prescription · no safe package match"
    MedicineMatchQuality.MANUAL -> "Entered manually"
}

private fun mealTimingLabel(value: MedicineMealTiming): String = when (value) {
    MedicineMealTiming.BEFORE_FOOD -> "Before food"
    MedicineMealTiming.WITH_FOOD -> "With food"
    MedicineMealTiming.AFTER_FOOD -> "After food"
    MedicineMealTiming.NOT_STATED -> "Not stated"
}

private fun imageName(context: Context, uri: Uri, index: Int): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull().orEmpty().ifBlank { "Photo ${index + 1}" }

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().also { values ->
    if (from in values.indices && to in values.indices) values.add(to, values.removeAt(from))
}
