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
        if (merged.size > maximum) vm.message.value = t("Use up to $maximum $label. The extra photos weren't added.", "ज़्यादा से ज़्यादा $maximum $label जोड़ें। बाक़ी फ़ोटो नहीं जोड़ी गईं।")
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
            vm.message.value = if (kind == "prescription") t("Use up to 5 prescription pages.", "पर्चे के ज़्यादा से ज़्यादा 5 पेज जोड़ें।") else t("Use up to 10 medicine photos.", "दवा की ज़्यादा से ज़्यादा 10 फ़ोटो जोड़ें।")
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
            vm.message.value = t("No camera app is available. Choose a photo instead.", "कैमरा ऐप नहीं मिला। फ़ोटो चुनें।")
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
            item { ScannerHeader(t("Scan prescription", "पर्चा स्कैन करें"), "1 / 2") { cleanAndClose() } }
            item { Text(t("Add a clear prescription. Read and check the words before saving. Medicine packaging is optional for your own comparison.", "साफ़ पर्चा जोड़ें। सहेजने से पहले शब्द पढ़कर जाँचें। मिलान के लिए दवा के पैकेट की फ़ोटो चाहें तो जोड़ें।")) }
            item {
                PhotoSelectionCard(
                    title = t("Prescription pages", "पर्चे के पेज"),
                    subtitle = t("Add in page order · up to 5", "पेज के क्रम से जोड़ें · ज़्यादा से ज़्यादा 5"),
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
                    subtitle = t("One clear photo per medicine · up to 10", "हर दवा की एक साफ़ फ़ोटो · ज़्यादा से ज़्यादा 10"),
                    tint = Color(0xFFFFEDD3),
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
            scanError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) } }
            item {
                Text(t("Your phone reads the page. It can read printed English letters. You may need to type handwriting yourself. The app does not decide any dose or treatment.", "पढ़ाई आपके फ़ोन पर ही होती है। छपे अंग्रेज़ी अक्षर पढ़े जाते हैं। हाथ की लिखाई आपको ख़ुद लिखनी पड़ सकती है। ऐप ख़ुराक या इलाज तय नहीं करता।"), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
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
                                warnings = listOf(t("Compare every copied line with the paper. The app has not matched brands, chosen doses or confirmed treatment.", "हर कॉपी की गई पंक्ति को असली पेज से मिलाएँ। ऐप ने ब्रांड नहीं मिलाए, ख़ुराक नहीं चुनी और इलाज पक्का नहीं किया।")) + if (medicines.isEmpty()) listOf(t("No medicine lines could be read clearly. Look at the prescription and type the medicines yourself.", "दवा की कोई पंक्ति ठीक से नहीं पढ़ी जा सकी। असली पर्चा देखकर दवाएँ ख़ुद लिखें।")) else emptyList()
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
                    shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Default.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text(t("Read prescription", "पर्चा पढ़ें")) }
                TextButton(onClick = {
                    drafts = listOf(MedicineDraft(newId(), manualMedicine(), false)); warnings = emptyList(); unmatched = emptyList(); unmatchedReviewed = true; phase = MedicineScannerPhase.Review
                }, modifier = Modifier.fillMaxWidth()) { Text(t("Add one manually", "एक दवा ख़ुद लिखें")) }
                TextButton(onClick = { cleanAndClose() }, modifier = Modifier.fillMaxWidth()) { Text(t("Cancel", "रद्द करें")) }
            }
        }

        MedicineScannerPhase.Scanning -> Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CircularProgressIndicator(color = Rust)
                Heading(t("Reading prescription", "पर्चा पढ़ रहे हैं"), 26)
                val value = progress
                if (value?.stage == MedicineScanStage.PREPARING && value.total > 0) {
                    Text(t("${value.completed.coerceAtMost(value.total)} of ${value.total}", "${value.completed.coerceAtMost(value.total)} / ${value.total}"))
                    if (value.fileName.isNotBlank()) Text(value.fileName, style = MaterialTheme.typography.bodySmall)
                } else Text(t("Reading the selected pages…", "चुने गए पेज पढ़ रहे हैं…"), style = MaterialTheme.typography.bodyMedium)
                Text(t("Keep this screen open until review appears. You can cancel without changing saved medicines.", "जाँच की सूची आने तक यह स्क्रीन खुली रखें। रोकने से सहेजी दवाएँ नहीं बदलेंगी।"), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { returnToImages() }) { Text(t("Cancel", "रद्द करें")) }
            }
        }

        MedicineScannerPhase.Review -> LazyColumn(
            Modifier.fillMaxSize().imePadding().testTag("medicine-scanner-review"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ScannerHeader(t("Review medicine list", "दवा की सूची जाँचें"), "2 / 2") { returnToImages() } }
            item { SurfaceCard(Sage) { Text(t("Check before saving", "सहेजने से पहले जाँचें"), fontWeight = FontWeight.Medium, fontFamily = SaharaTitleFont); Text(t("The phone can read words wrongly. Check each medicine with the prescription and the packet. Sahara does not diagnose, prescribe or suggest treatment.", "पढ़ाई में ग़लती हो सकती है। हर दवा को पर्चे और पैकेट से मिलाएँ। सहारा बीमारी नहीं बताता, दवा नहीं लिखता और इलाज की सलाह नहीं देता।")) } }
            if (prescriptionUris.isNotEmpty()) item { SurfaceCard {
                Text(t("Original pages", "असली पेज"), fontWeight = FontWeight.SemiBold)
                prescriptionUris.forEachIndexed { index, uri -> TextButton(onClick = { runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { scanError = t("No image viewer is available. Open this photo in Files to compare it.", "फ़ोटो देखने का ऐप नहीं मिला। मिलाने के लिए यह फ़ोटो Files में खोलें।") } }) { Text(t("View prescription page ${index + 1}", "पर्चे का पेज ${index + 1} देखें")) } }
            } }
            warnings.forEach { warning -> item(warning) { SurfaceCard(Peach) { Text(warning) } } }
            items(drafts.size, key = { drafts[it].id }) { index ->
                MedicineReviewCard(
                    draft = drafts[index],
                    storage = vm.storageNamespace,
                    personId = personAtOpen,
                    onChange = { changed -> drafts = drafts.toMutableList().also { it[index] = changed } },
                    onRemove = { drafts = drafts.toMutableList().also { it.removeAt(index) } },
                )
            }
            if (unmatched.isNotEmpty()) item {
                SurfaceCard(Peach) {
                    Text(t("${unmatched.size} medicine ${if (unmatched.size == 1) "photo was" else "photos were"} not matched", "दवा की ${unmatched.size} फ़ोटो नहीं मिलीं"), fontWeight = FontWeight.Bold)
                    Text(t("Review ${unmatched.joinToString { "photo ${it + 1}" }}. Add any missing medicine manually or leave it out only after checking the original prescription.", "${unmatched.joinToString { "फ़ोटो ${it + 1}" }} जाँचें। छूटी दवा ख़ुद लिखें, या असली पर्चा देखने के बाद ही उसे छोड़ें।"))
                    unmatched.filter { it in medicineUris.indices }.forEach { index -> TextButton(onClick = { runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(medicineUris[index]), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    }.onFailure { scanError = t("No image viewer is available. Open this photo in Files to compare it.", "फ़ोटो देखने वाला ऐप नहीं मिला। मिलान के लिए यह फ़ोटो Files में खोलें।") } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(t("View medicine photo ${index + 1}", "दवा की फ़ोटो ${index + 1} देखें")) } }
                    Spacer(Modifier.height(8.dp)); CheckLine(unmatchedReviewed, { unmatchedReviewed = it }, t("I reviewed the unmatched medicine photos.", "मैंने बिना मिली दवा की फ़ोटो जाँच ली हैं।"))
                }
            }
            item {
                OutlinedButton(onClick = { drafts = drafts + MedicineDraft(newId(), manualMedicine(), false) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(t("Add another manually", "एक और दवा ख़ुद लिखें"))
                }
            }
            scanError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.announce()) } }
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
                            require(vm.selected.value == personAtOpen) { t("The selected person changed. Open this prescription for the right person.", "चुना गया व्यक्ति बदल गया है। सही व्यक्ति के लिए यह पर्चा खोलें।") }
                            if(onReviewedMedicines != null) {
                                require(stagingAllowed()) { t("This prescription's account or edit access changed. Nothing was saved.", "इस पर्चे का खाता या बदलाव की अनुमति बदल गई है। कुछ सहेजा नहीं गया।") }
                                onReviewedMedicines(linked)
                                releaseSelectedPhotos()
                            } else vm.saveMedicineList(linked) {
                                releaseSelectedPhotos()
                                File(context.cacheDir, "medicine-scan").deleteRecursively()
                                onClose()
                            }
                        } catch (failure: Exception) { if (failure is CancellationException) throw failure; scanError = failure.message ?: t("Could not keep the original pages. Your medicine list has not been saved.", "असली पेज सँभाले नहीं जा सके। आपकी दवा की सूची सहेजी नहीं गई।") }
                        finally { savingSources = false }
                    }
                }, enabled = valid && !busy && !savingSources, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (busy || savingSources) t("Saving…", "सहेज रहे हैं…") else if(onReviewedMedicines != null) t("Use ${drafts.size} reviewed medicines", "जाँची गई ${drafts.size} दवाएँ लें") else t("Save ${drafts.size} ${if (drafts.size == 1) "medicine" else "medicines"}", "${drafts.size} दवा सहेजें"))
                }
                if (!valid) Text(t("Before saving, add a medicine name and dose, check photos that did not match, and confirm every medicine.", "सहेजने से पहले दवा का नाम और ख़ुराक लिखें, बिना मिली फ़ोटो देखें और हर दवा की पुष्टि करें।"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { returnToImages() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(t("Back to photos", "फ़ोटो पर वापस")) }
                TextButton(onClick = { cleanAndClose() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(t("Cancel", "रद्द करें")) }
            }
        }
    }
}

@Composable
private fun ScannerHeader(title: String, step: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, careText()("Back", "वापस")) }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontFamily = SaharaTitleFont,
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
    val context = LocalContext.current; val t = careText(); val hindi = careHindi()
    SurfaceCard(tint) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(subtitle, style = MaterialTheme.typography.bodySmall) }
            Surface(color = Paper, shape = RoundedCornerShape(12.dp)) { Text(uris.size.toString(), Modifier.padding(horizontal = 11.dp, vertical = 6.dp), fontWeight = FontWeight.Bold) }
        }
        uris.forEachIndexed { index, value ->
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(imageName(context, Uri.parse(value), index, hindi), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onMove != null && index > 0) IconButton(onClick = { onMove(index, index - 1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowUpward, t("Move earlier", "ऊपर करें")) }
                if (onMove != null && index < uris.lastIndex) IconButton(onClick = { onMove(index, index + 1) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.ArrowDownward, t("Move later", "नीचे करें")) }
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, t("Remove ${index + 1}", "${index + 1} हटाएँ")) }
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
private fun MedicineReviewCard(draft: MedicineDraft, storage: String, personId: String, onChange: (MedicineDraft) -> Unit, onRemove: () -> Unit) {
    val medicine = draft.medicine; val t = careText(); val hindi = careHindi()
    fun change(next: OrganizedMedicine) = onChange(draft.copy(medicine = next, confirmed = false))
    SurfaceCard(Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Medication, null, tint = Rust); Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(if (medicine.medicineName.isBlank()) t("Manual medicine", "ख़ुद लिखी दवा") else medicine.medicineName, fontWeight = FontWeight.Bold)
                Text(matchLabel(medicine, hindi), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, t("Remove medicine", "दवा हटाएँ")) }
        }
        medicine.uncertainties.forEach { Text("• $it", color = Rust, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        CareField(medicine.medicineName, { change(medicine.copy(medicineName = it.take(120))) }, t("Medicine name", "दवा का नाम"), singleLine = true,
            supporting = t("Strip cut or name missing? Type it, or pick from the list as you type.", "पत्ता कटा है या नाम नहीं दिखता? नाम लिखें, या लिखते समय सूची से चुनें।"))
        MedicineNameSuggestions(medicine.medicineName) { change(medicine.copy(medicineName = it.take(120))) }
        CareField(medicine.strength, { change(medicine.copy(strength = it.take(80))) }, t("Strength", "ताक़त"), singleLine = true, placeholder = t("e.g. 500 mg, 5 mg/5 ml", "जैसे 500 mg, 5 mg/5 ml"))
        CareField(medicine.form, { change(medicine.copy(form = it.take(60))) }, t("Form", "रूप"), singleLine = true, placeholder = t("e.g. tablet, capsule, syrup, injection, patch", "जैसे गोली, कैप्सूल, सिरप, इंजेक्शन, पैच"))
        Text(t("Type the rest by hand from the prescription. A photo never fills these.", "बाक़ी पर्चे से देखकर ख़ुद लिखें। फ़ोटो से ये कभी नहीं भरे जाते।"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        CareField(medicine.quantity, { change(medicine.copy(quantity = it.take(80))) }, t("How much each time", "हर बार कितनी"), singleLine = true, placeholder = t("e.g. 1 tablet, 1½ tablets, 5 ml", "जैसे 1 गोली, 1½ गोली, 5 ml"))
        CareField(medicine.frequency, { change(medicine.copy(frequency = it.take(120))) }, t("How often", "कितनी बार"), singleLine = true, placeholder = t("e.g. twice a day, only when needed", "जैसे दिन में दो बार, ज़रूरत होने पर ही"))
        CareField(medicine.instructions, { change(medicine.copy(instructions = it.take(500))) }, t("Directions", "तरीक़ा"), minLines = 2, placeholder = t("e.g. as written: dissolve in water", "जैसे पर्चे में लिखा: पानी में घोलकर"))
        Text(t("Food timing", "खाने के हिसाब से समय"), fontWeight = FontWeight.SemiBold)
        if (medicine.sourceQuote.isNotBlank()) {
            Text(t("Original reading · ${medicine.prescriptionPageIndexes.joinToString { "page ${it + 1}" }}", "असली पढ़ाई · ${medicine.prescriptionPageIndexes.joinToString { "पेज ${it + 1}" }}"), style = MaterialTheme.typography.labelMedium)
            Text(medicine.sourceQuote, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            MedicineMealTiming.entries.forEach { timing ->
                FilterChip(selected = medicine.mealTiming == timing, onClick = { change(medicine.copy(mealTiming = timing)) }, label = { Text(mealTimingLabel(timing, hindi), fontSize = 11.sp) })
            }
        }
        DoseTimesField(medicine.suggestedTimes, { change(medicine.copy(suggestedTimes = it)) })
        CareField(
            medicine.durationDays?.toString().orEmpty(),
            { value -> if (value.isBlank()) change(medicine.copy(durationDays = null)) else value.toIntOrNull()?.takeIf { it in 1..365 }?.let { change(medicine.copy(durationDays = it)) } },
            t("For how many days", "कितने दिन"), singleLine = true, placeholder = t("e.g. 5 · leave empty if ongoing", "जैसे 5 · चलती रहे तो ख़ाली छोड़ें"),
        )
        StripPhotoRow(storage, personId, draft.id) { reading -> change(MedicinePhotoFields.fillBlanks(medicine, reading)) }
        CheckLine(draft.confirmed, { onChange(draft.copy(confirmed = it)) }, t("I checked this medicine, dose and directions against the original prescription and packaging.", "मैंने यह दवा, ख़ुराक और तरीक़ा असली पर्चे और पैकेट से मिला लिया है।"))
    }
}

private fun manualMedicine() = OrganizedMedicine(
    medicineName = "", strength = "", form = "", quantity = "", frequency = "", suggestedTimes = emptyList(),
    mealTiming = MedicineMealTiming.NOT_STATED, instructions = "", durationDays = null, prescriptionItemNumber = null,
    medicineImageIndex = -1, prescriptionPageIndexes = emptyList(), confidence = null, uncertainties = emptyList(),
    matchQuality = MedicineMatchQuality.MANUAL,
)

private fun matchLabel(medicine: OrganizedMedicine, hindi: Boolean): String = when (medicine.matchQuality) {
    MedicineMatchQuality.MATCHED -> (if (hindi) "पर्चे और पैकेट से मिली" else "Matched to prescription and packaging") + medicine.confidence?.let { " · ${(it * 100).toInt()}%" }.orEmpty()
    MedicineMatchQuality.POSSIBLE -> if (hindi) "पैकेट से शायद मिली · ध्यान से जाँचें" else "Possible packaging match · check carefully"
    MedicineMatchQuality.PRESCRIPTION_ONLY -> if (hindi) "पर्चे में मिली · पैकेट से पक्का मिलान नहीं" else "Found on prescription · no safe package match"
    MedicineMatchQuality.MANUAL -> if (hindi) "ख़ुद लिखी गई" else "Entered manually"
}

private fun mealTimingLabel(value: MedicineMealTiming, hindi: Boolean): String = when (value) {
    MedicineMealTiming.BEFORE_FOOD -> if (hindi) "खाने से पहले" else "Before food"
    MedicineMealTiming.WITH_FOOD -> if (hindi) "खाने के साथ" else "With food"
    MedicineMealTiming.AFTER_FOOD -> if (hindi) "खाने के बाद" else "After food"
    MedicineMealTiming.NOT_STATED -> if (hindi) "लिखा नहीं" else "Not stated"
}

private fun imageName(context: Context, uri: Uri, index: Int, hindi: Boolean): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull().orEmpty().ifBlank { if (hindi) "फ़ोटो ${index + 1}" else "Photo ${index + 1}" }

private fun <T> List<T>.moved(from: Int, to: Int): List<T> = toMutableList().also { values ->
    if (from in values.indices && to in values.indices) values.add(to, values.removeAt(from))
}
