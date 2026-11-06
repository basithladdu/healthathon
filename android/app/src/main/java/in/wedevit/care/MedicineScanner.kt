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
fun MedicineScanner(vm: CareModel, onClose: () -> Unit, onAccount: () -> Unit, onReviewedMedicines: ((List<MedicineDraft>) -> Unit)? = null) {
    val context = LocalContext.current; val t = careText()
    val scope = rememberCoroutineScope()
    val documentReader = remember(context) { CareDocumentReader(context.applicationContext) }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val selectedPerson by vm.selected.collectAsStateWithLifecycle()
    val personAtOpen = rememberSaveable { vm.selected.value }
    val accountAtOpen = rememberSaveable { vm.accountNamespace }
    fun stagingAllowed() = vm.selected.value == personAtOpen && vm.accountNamespace == accountAtOpen && vm.canEdit.value &&
        (vm.cloud == null || vm.cloud.session.value?.userId == accountAtOpen)
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
    // Photo limits and a missing camera app are said beside the photo cards, not in a toast.
    var pickNote by remember { mutableStateOf("") }

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
        pickNote = if (merged.size > maximum) t("Use up to $maximum $label.", "ज़्यादा से ज़्यादा $maximum $label जोड़ें।") else ""
        return merged.take(maximum)
    }

    val prescriptionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        prescriptionUris = addSelected(prescriptionUris, selected, 5, t("prescription pages", "पर्चे के पेज"))
    }
    val medicinePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { selected ->
        medicineUris = addSelected(medicineUris, selected, 10, t("medicine photos", "दवा की फ़ोटो"))
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved && pendingUri.isNotBlank()) {
            if (pendingKind == "prescription") prescriptionUris = addSelected(prescriptionUris, listOf(Uri.parse(pendingUri)), 5, t("prescription pages", "पर्चे के पेज"))
            else medicineUris = addSelected(medicineUris, listOf(Uri.parse(pendingUri)), 10, t("medicine photos", "दवा की फ़ोटो"))
        } else if (pendingPath.isNotBlank()) File(pendingPath).delete()
        pendingKind = ""; pendingUri = ""; pendingPath = ""
    }

    fun photograph(kind: String) {
        val maximumReached = if (kind == "prescription") prescriptionUris.size >= 5 else medicineUris.size >= 10
        if (maximumReached) {
            pickNote = if (kind == "prescription") t("Use up to 5 prescription pages.", "पर्चे के ज़्यादा से ज़्यादा 5 पेज जोड़ें।") else t("Use up to 10 medicine photos.", "दवा की ज़्यादा से ज़्यादा 10 फ़ोटो जोड़ें।")
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
            pickNote = t("No camera app is available. Choose a photo instead.", "कैमरा ऐप नहीं मिला। फ़ोटो चुनें।")
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

    // Photos → reading → review slide forward and back like the rest of the app.
    SaharaScreenHost(phase, Modifier.fillMaxSize(), label = "Medicine scanner", direction = { from, to -> if (to.ordinal > from.ordinal) SaharaMotion.Direction.Forward else SaharaMotion.Direction.Back }) { shown -> when (shown) {
        MedicineScannerPhase.Select -> LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("medicine-scanner-select"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScannerHeader(t("Scan prescription", "पर्चा स्कैन करें"), "1 / 2") { cleanAndClose() } }
            item {
                PhotoSelectionCard(
                    title = t("Prescription pages", "पर्चे के पेज"),
                    subtitle = "",
                    tint = Mint,
                    uris = prescriptionUris,
                    chooseLabel = t("Choose pages", "पेज चुनें"),
                    photographLabel = t("Take photo", "फ़ोटो लें"),
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
                    title = t("Medicine strips or boxes", "दवा के पत्ते या डिब्बे"),
                    subtitle = "",
                    tint = SaharaApricot,
                    uris = medicineUris,
                    chooseLabel = t("Choose photos", "फ़ोटो चुनें"),
                    photographLabel = t("Take photo", "फ़ोटो लें"),
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
            if (pickNote.isNotBlank()) item { Text(pickNote, color = RustText, fontSize = 15.sp, modifier = Modifier.animateItem().announce()) }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scanError = null; phase = MedicineScannerPhase.Scanning
                        scanJob = scope.launch {
                            try {
                                val pages = documentReader.prescription(prescriptionUris.map(Uri::parse)) { done, total ->
                                    progress = MedicineScanProgress(MedicineScanStage.PREPARING, done, total, t("Prescription page ${done.coerceAtLeast(1)}", "पर्चे का पेज ${done.coerceAtLeast(1)}"))
                                }
                                val medicines = copiedPrescriptionMedicines(pages)
                                drafts = medicines.mapIndexed { index, medicine -> MedicineDraft(stableMedicineDraftId(scanId, medicine, index), medicine) }
                                warnings = if (medicines.isEmpty()) listOf(t("No medicines found", "कोई दवा नहीं मिली")) else emptyList()
                                unmatched = medicineUris.indices.toList()
                                unmatchedReviewed = unmatched.isEmpty()
                                phase = MedicineScannerPhase.Review
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                scanError = error.message ?: t("Could not make the medicine list. Try again.", "दवा की सूची पूरी नहीं बन सकी। फिर कोशिश करें।")
                                phase = MedicineScannerPhase.Select
                            } finally { scanJob = null }
                        }
                    },
                    enabled = prescriptionUris.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text(t("Read prescription", "पर्चा पढ़ें"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    drafts = listOf(MedicineDraft(newId(), manualMedicine(), false)); warnings = emptyList(); unmatched = emptyList(); unmatchedReviewed = true; phase = MedicineScannerPhase.Review
                }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add one manually", "एक दवा ख़ुद लिखें"), fontSize = 15.sp) }
            }
        }

        MedicineScannerPhase.Scanning -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Rust)
                Heading(t("Reading prescription", "पर्चा पढ़ रहे हैं"), 26)
                val value = progress
                if (value?.stage == MedicineScanStage.PREPARING && value.total > 0) {
                    // Progress as a badge.
                    Surface(shape = RoundedCornerShape(50), color = Mint) { Text(t("${value.completed.coerceAtMost(value.total)} of ${value.total}", "${value.completed.coerceAtMost(value.total)} / ${value.total}"), Modifier.padding(horizontal = 14.dp, vertical = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DoctorCardInk) }
                    if (value.fileName.isNotBlank()) Text(value.fileName, fontSize = 14.sp, color = DoctorCardSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Text(t("Reading the selected pages…", "चुने गए पेज पढ़ रहे हैं…"), fontSize = 16.sp, color = DoctorCardInk)
                OutlinedButton(onClick = { returnToImages() }, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), fontSize = 15.sp) }
            }
        }

        MedicineScannerPhase.Review -> LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("medicine-scanner-review"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScannerHeader(t("Review medicine list", "दवा की सूची जाँचें"), "2 / 2") { returnToImages() } }
            if (prescriptionUris.isNotEmpty()) item { SurfaceCard {
                Text(t("Original pages", "असली पेज"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                prescriptionUris.forEachIndexed { index, uri -> TextButton(onClick = { runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { scanError = t("No image viewer available.", "फ़ोटो देखने वाला ऐप नहीं मिला।") } }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("View prescription page ${index + 1}", "पर्चे का पेज ${index + 1} देखें"), fontSize = 15.sp) } }
            } }
            warnings.forEach { warning -> item(warning) { SurfaceCard(Peach) { Row(verticalAlignment = Alignment.Top) { Icon(Icons.Default.WarningAmber, null, Modifier.size(20.dp), tint = RustText); Spacer(Modifier.width(12.dp)); Text(warning, fontSize = 15.sp, color = DoctorCardInk) } } } }
            items(drafts.size, key = { drafts[it].id }) { index -> Box(Modifier.animateItem()) {
                MedicineReviewCard(
                    draft = drafts[index],
                    storage = vm.storageNamespace,
                    personId = personAtOpen,
                    onChange = { changed -> drafts = drafts.toMutableList().also { it[index] = changed } },
                    onRemove = { drafts = drafts.toMutableList().also { it.removeAt(index) } },
                )
            } }
            if (unmatched.isNotEmpty()) item {
                SurfaceCard(Peach) {
                    Text(run { val n = unmatched.size; if (n == 1) t("1 medicine photo was not matched", "दवा की 1 फ़ोटो नहीं मिली") else t("$n medicine photos were not matched", "दवा की $n फ़ोटो नहीं मिलीं") }, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk)
                    unmatched.filter { it in medicineUris.indices }.forEach { index -> TextButton(onClick = { runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(medicineUris[index]), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { scanError = t("No image viewer available.", "फ़ोटो देखने वाला ऐप नहीं मिला।") } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("View medicine photo ${index + 1}", "दवा की फ़ोटो ${index + 1} देखें")) } }
                    Spacer(Modifier.height(8.dp)); CheckLine(unmatchedReviewed, { unmatchedReviewed = it }, t("I reviewed the unmatched medicine photos.", "मैंने बिना मिली दवा की फ़ोटो जाँच ली हैं।"))
                }
            }
            item {
                OutlinedButton(onClick = { drafts = drafts + MedicineDraft(newId(), manualMedicine(), false) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Add another manually", "एक और दवा ख़ुद लिखें"), fontSize = 15.sp)
                }
            }
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
                            if(onReviewedMedicines != null) require(stagingAllowed()) { t("This prescription's care profile or edit access changed.", "इस पर्चे की देखभाल प्रोफ़ाइल या बदलाव की अनुमति बदल गई है।") }
                            val sourceStore = MedicineSourceStore(context, vm.accountNamespace)
                            val originals = sourceStore.save(personAtOpen, scanId, prescriptionUris.map(Uri::parse), vm.reportFiles)
                            val references = withContext(Dispatchers.IO) { sourceStore.references(originals, vm.reportFiles) }
                            val linked = drafts.map { draft -> if (references.isBlank()) draft else draft.copy(medicine = draft.medicine.copy(instructions = draft.medicine.instructions + "\nOriginal prescription references:\n" + references)) }
                            require(vm.selected.value == personAtOpen) { t("The selected person changed.", "चुना गया व्यक्ति बदल गया है।") }
                            if(onReviewedMedicines != null) {
                                require(stagingAllowed()) { t("Access changed. Nothing was saved.", "पहुँच बदल गई। कुछ सहेजा नहीं गया।") }
                                onReviewedMedicines(linked)
                                releaseSelectedPhotos()
                            } else vm.saveMedicineList(linked) {
                                releaseSelectedPhotos()
                                File(context.cacheDir, "medicine-scan").deleteRecursively()
                                onClose()
                            }
                        } catch (failure: Exception) { if (failure is CancellationException) throw failure; scanError = failure.message ?: t("Could not keep the original pages.", "असली पेज सँभाले नहीं जा सके।") }
                        finally { savingSources = false }
                    }
                }, enabled = valid && !busy && !savingSources, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, text = if (busy || savingSources) t("Saving…", "सहेज रहे हैं…") else if(onReviewedMedicines != null) t("Use ${drafts.size} reviewed medicines", "जाँची गई ${drafts.size} दवाएँ लें") else run { val n = drafts.size; if (n == 1) t("Save 1 medicine", "1 दवा सहेजें") else t("Save $n medicines", "$n दवा सहेजें") })
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { cleanAndClose() }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(t("Cancel", "रद्द करें"), fontSize = 15.sp) }
            }
        }
    } }
    scanError?.let { error -> AlertDialog(onDismissRequest = { scanError = null }, title = { Text(t("Not done", "पूरा नहीं हुआ")) }, text = { Text(error) },
        confirmButton = { TextButton(onClick = { scanError = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("OK", "ठीक है")) } }) }
}

@Composable
private fun ScannerHeader(title: String, step: String, info: String? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, careText()("Back", "वापस")) }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontFamily = SaharaTitleFont,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.7).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = DoctorCardInk,
        )
        if (info != null) MedicineInfoButton(title, info, "medicine-scanner-info")
        CareLanguageMenu(compact = true)
        Spacer(Modifier.width(8.dp))
        Surface(
            color = Mint,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = step,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = DoctorCardInk,
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
    val context = LocalContext.current; val t = careText(); val hindi = careHindi()
    SurfaceCard(tint) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk); if (subtitle.isNotBlank()) Text(subtitle, fontSize = 14.sp, color = DoctorCardSub) }
            // Count as a round badge, green once something is added.
            Surface(color = if (uris.isEmpty()) Paper else DoctorStatusGoodBg, shape = RoundedCornerShape(50)) { Text(uris.size.toString(), Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (uris.isEmpty()) DoctorCardSub else DoctorStatusGood) }
        }
        uris.forEachIndexed { index, value ->
            if (index == 0) Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, null, Modifier.size(20.dp), tint = DoctorCardSub); Spacer(Modifier.width(8.dp))
                Text(imageName(context, Uri.parse(value), index, hindi), Modifier.weight(1f), fontSize = 15.sp, color = DoctorCardInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onMove != null && index > 0) IconButton(onClick = { onMove(index, index - 1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowUpward, t("Move earlier", "ऊपर करें")) }
                if (onMove != null && index < uris.lastIndex) IconButton(onClick = { onMove(index, index + 1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowDownward, t("Move later", "नीचे करें")) }
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Remove ${index + 1}", "${index + 1} हटाएँ")) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChoose, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(chooseLabel, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2)
            }
            OutlinedButton(onClick = onPhotograph, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(photographLabel, fontSize = 15.sp, lineHeight = 18.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun MedicineReviewCard(draft: MedicineDraft, storage: String, personId: String, onChange: (MedicineDraft) -> Unit, onRemove: () -> Unit) {
    val medicine = draft.medicine; val t = careText(); val hindi = careHindi()
    fun change(next: OrganizedMedicine) = onChange(draft.copy(medicine = next, confirmed = false))
    SurfaceCard(Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Medication, null, tint = RustText); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (medicine.medicineName.isBlank()) t("Manual medicine", "ख़ुद लिखी दवा") else medicine.medicineName, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = DoctorCardInk)
                // How well it matched, as a status chip.
                val (fg, bg) = when (medicine.matchQuality) { MedicineMatchQuality.MATCHED -> DoctorStatusGood to DoctorStatusGoodBg; MedicineMatchQuality.MANUAL -> DoctorCardSub to Cream; else -> DoctorStatusWarn to DoctorStatusWarnBg }
                Surface(shape = RoundedCornerShape(50), color = bg) { Text(matchLabel(medicine, hindi), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = fg) }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.DeleteOutline, t("Remove medicine", "दवा हटाएँ"), tint = RustText) }
        }
        medicine.uncertainties.forEach { Text("• $it", color = RustText, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
        // The prescription's own words sit above the fields they fill, not between a label and its choices.
        if (medicine.sourceQuote.isNotBlank()) {
            Text(run { val pages = medicine.prescriptionPageIndexes.joinToString { val k = it + 1; t("page $k", "पेज $k") }; t("Original reading · $pages", "असली पढ़ाई · $pages") }, Modifier.padding(top = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DoctorCardSub)
            Text(medicine.sourceQuote, fontSize = 14.sp, color = DoctorCardInk)
        }
        Spacer(Modifier.height(12.dp))
        CareField(medicine.medicineName, { change(medicine.copy(medicineName = it.take(120))) }, t("Medicine name", "दवा का नाम"), singleLine = true)
        MedicineNameSuggestions(medicine.medicineName) { change(medicine.copy(medicineName = it.take(120))) }
        CareField(medicine.strength, { change(medicine.copy(strength = it.take(80))) }, t("Strength", "ताक़त"), singleLine = true, placeholder = t("e.g. 500 mg, 5 mg/5 ml", "जैसे 500 mg, 5 mg/5 ml"))
        CareField(medicine.form, { change(medicine.copy(form = it.take(60))) }, t("Form", "रूप"), singleLine = true, placeholder = t("e.g. tablet, capsule, syrup, injection, patch", "जैसे गोली, कैप्सूल, सिरप, इंजेक्शन, पैच"))
        CareField(medicine.quantity, { change(medicine.copy(quantity = it.take(80))) }, t("How much each time", "हर बार कितनी"), singleLine = true, placeholder = t("e.g. 1 tablet, 1½ tablets, 5 ml", "जैसे 1 गोली, 1½ गोली, 5 ml"))
        CareField(medicine.frequency, { change(medicine.copy(frequency = it.take(120))) }, t("How often", "कितनी बार"), singleLine = true, placeholder = t("e.g. twice a day, only when needed", "जैसे दिन में दो बार, ज़रूरत होने पर ही"))
        CareField(medicine.instructions, { change(medicine.copy(instructions = it.take(500))) }, t("Directions", "तरीक़ा"), minLines = 2, placeholder = t("e.g. as written: dissolve in water", "जैसे पर्चे में लिखा: पानी में घोलकर"))
        Text(t("Food timing", "खाने के हिसाब से समय"), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = DoctorCardInk, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            MedicineMealTiming.entries.forEach { timing ->
                FilterChip(selected = medicine.mealTiming == timing, onClick = { change(medicine.copy(mealTiming = timing)) }, label = { Text(mealTimingLabel(timing, hindi), fontSize = 14.sp) }, shape = RoundedCornerShape(50), modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        DoseTimesField(medicine.suggestedTimes, { change(medicine.copy(suggestedTimes = it)) })
        CareField(
            medicine.durationDays?.toString().orEmpty(),
            { value -> if (value.isBlank()) change(medicine.copy(durationDays = null)) else value.toIntOrNull()?.takeIf { it in 1..365 }?.let { change(medicine.copy(durationDays = it)) } },
            t("For how many days", "कितने दिन"), singleLine = true, placeholder = t("e.g. 5 · leave empty if ongoing", "जैसे 5 · चलती रहे तो ख़ाली छोड़ें"),
        )
        StripPhotoRow(storage, personId, draft.id) { reading -> change(MedicinePhotoFields.fillBlanks(medicine, reading)) }
        CheckLine(draft.confirmed, { onChange(draft.copy(confirmed = it)) }, t("I checked this with the prescription", "मैंने इसे पर्चे से मिला लिया है"))
    }
}

private fun manualMedicine() = OrganizedMedicine(
    medicineName = "", strength = "", form = "", quantity = "", frequency = "", suggestedTimes = emptyList(),
    mealTiming = MedicineMealTiming.NOT_STATED, instructions = "", durationDays = null, prescriptionItemNumber = null,
    medicineImageIndex = -1, prescriptionPageIndexes = emptyList(), confidence = null, uncertainties = emptyList(),
    matchQuality = MedicineMatchQuality.MANUAL,
)

private fun matchLabel(medicine: OrganizedMedicine, hindi: Boolean): String = when (medicine.matchQuality) {
    MedicineMatchQuality.MATCHED -> careTr(hindi, "Matched", "मिली") + medicine.confidence?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    MedicineMatchQuality.POSSIBLE -> careTr(hindi, "Possible match", "शायद मिली")
    MedicineMatchQuality.PRESCRIPTION_ONLY -> careTr(hindi, "Prescription only", "सिर्फ़ पर्चे में")
    MedicineMatchQuality.MANUAL -> careTr(hindi, "Entered manually", "ख़ुद लिखी गई")
}

private fun mealTimingLabel(value: MedicineMealTiming, hindi: Boolean): String = when (value) {
    MedicineMealTiming.BEFORE_FOOD -> careTr(hindi, "Before food", "खाने से पहले")
    MedicineMealTiming.WITH_FOOD -> careTr(hindi, "With food", "खाने के साथ")
    MedicineMealTiming.AFTER_FOOD -> careTr(hindi, "After food", "खाने के बाद")
    MedicineMealTiming.NOT_STATED -> careTr(hindi, "Not stated", "लिखा नहीं")
}

private fun imageName(context: Context, uri: Uri, index: Int, hindi: Boolean): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull().orEmpty().ifBlank { careTr(hindi, "Photo ${index + 1}", "फ़ोटो ${index + 1}") }

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().also { values ->
    if (from in values.indices && to in values.indices) values.add(to, values.removeAt(from))
}
